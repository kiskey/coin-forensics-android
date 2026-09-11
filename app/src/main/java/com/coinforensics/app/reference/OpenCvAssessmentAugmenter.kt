package com.coinforensics.app.reference

import com.coinforensics.app.imaging.OpenCvDecisionPolicy
import com.coinforensics.app.imaging.OpenCvForensicEngine
import com.coinforensics.app.imaging.OpenCvPairAnalysis
import com.coinforensics.app.model.CoinSide
import com.coinforensics.app.model.DiagnosticCheck
import com.coinforensics.app.model.DiagnosticStatus
import com.coinforensics.app.model.ImageSlot
import com.coinforensics.app.model.LoadedImage
import com.coinforensics.app.model.ReferenceAssessment
import com.coinforensics.app.model.ReferencePack
import com.coinforensics.app.model.ReferenceSampleImage
import com.coinforensics.app.model.ReferenceSourceKind
import kotlin.math.min

/**
 * Adds OpenCV evidence to the existing reference assessment without replacing the original engine.
 * Genuine and counterfeit controls remain completely separate evidence pools.
 */
object OpenCvAssessmentAugmenter {
    fun augment(
        base: ReferenceAssessment,
        pack: ReferencePack,
        images: Map<ImageSlot, LoadedImage>,
        referenceSamples: List<ReferenceSampleImage>
    ): ReferenceAssessment {
        if (referenceSamples.isEmpty()) return base
        val sourceById = pack.sources.associateBy { it.id }

        fun target(side: CoinSide): LoadedImage? = when (side) {
            CoinSide.OBVERSE -> images[ImageSlot.OBVERSE]
            CoinSide.REVERSE -> images[ImageSlot.REVERSE]
            CoinSide.EDGE -> images[ImageSlot.EDGE]
        }

        val genuineKinds = setOf(
            ReferenceSourceKind.CERTIFIED_GENUINE,
            ReferenceSourceKind.GENUINE_AUCTION,
            ReferenceSourceKind.CATALOG_REFERENCE
        )
        val genuine = referenceSamples.filter { sourceById[it.provenanceSourceId]?.kind in genuineKinds }
        val counterfeit = referenceSamples.filter {
            sourceById[it.provenanceSourceId]?.kind in setOf(
                ReferenceSourceKind.COUNTERFEIT_DIAGNOSTIC,
                ReferenceSourceKind.COMMUNITY_LEAD
            )
        }

        val extraChecks = mutableListOf<DiagnosticCheck>()
        val usedSources = base.sourceIdsUsed.toMutableSet()
        val genuineResultsBySide = mutableMapOf<CoinSide, MutableList<Pair<String, OpenCvPairAnalysis>>>()

        genuine.forEach { sample ->
            val coin = target(sample.side) ?: return@forEach
            if (sample.side == CoinSide.EDGE) return@forEach
            val result = OpenCvForensicEngine.compare(
                coin.bitmap,
                sample.bitmap,
                pack.regions.filter { it.side == sample.side }
            )
            if (result.available) {
                genuineResultsBySide.getOrPut(sample.side) { mutableListOf() }
                    .add(sample.provenanceSourceId to result)
                usedSources += sample.provenanceSourceId
            }
        }

        val genuineConsensus = genuineResultsBySide.mapValues { (_, pairs) ->
            OpenCvForensicEngine.consensus(pairs.map { it.second })
        }.filterValues { it != null }.mapValues { it.value!! }

        genuineConsensus.forEach { (side, consensus) ->
            val sourceIds = genuineResultsBySide[side].orEmpty().map { it.first }.distinct()
            val status = OpenCvDecisionPolicy.genuineStatus(consensus.overallScore, consensus.registrationReliability)
            val weakest = consensus.regionScores.entries.sortedBy { it.value }.take(3)
                .joinToString { "${it.key} ${"%.1f".format(it.value)}" }
                .ifBlank { "no pack-specific regions" }
            extraChecks += DiagnosticCheck(
                markerId = "opencv-genuine-consensus-${side.name.lowercase()}",
                title = "OpenCV multi-control forensic consensus — ${side.label}",
                status = status,
                score = if (status == DiagnosticStatus.MANUAL_REVIEW) null else consensus.overallScore,
                detail = buildString {
                    append("${consensus.controlCount} genuine control(s); combined ${"%.1f".format(consensus.overallScore)}/100. ")
                    append("ORB/RANSAC geometry ${"%.1f".format(consensus.geometryScore)}, ")
                    append("Canny edge overlap ${"%.1f".format(consensus.edgeScore)}, ")
                    append("Laplacian microtexture ${"%.1f".format(consensus.textureScore)}, ")
                    append("normalized luminance ${"%.1f".format(consensus.luminanceScore)}. ")
                    append("Median ${consensus.goodMatches} good ORB matches / ${consensus.inliers} RANSAC inliers; ")
                    append("registration reliability ${"%.2f".format(consensus.registrationReliability)}. ")
                    append("Weakest mapped regions: $weakest. This is image-consistency evidence, not an authenticity probability.")
                },
                sourceIds = sourceIds
            )
        }

        var authoritativeCounterfeitCaution = false
        counterfeit.forEach { sample ->
            val source = sourceById[sample.provenanceSourceId] ?: return@forEach
            val coin = target(sample.side) ?: return@forEach
            if (sample.side == CoinSide.EDGE) return@forEach
            val linkedRegionIds = pack.markers
                .filter { sample.provenanceSourceId in it.sourceIds && it.side == sample.side }
                .mapNotNull { it.regionId }
                .distinct()
            val regions = if (linkedRegionIds.isEmpty()) {
                emptyList()
            } else {
                pack.regions.filter { it.side == sample.side && it.id in linkedRegionIds }
            }
            if (regions.isEmpty()) return@forEach

            val result = OpenCvForensicEngine.compare(coin.bitmap, sample.bitmap, regions)
            if (!result.available) return@forEach
            val genuineScore = genuineConsensus[sample.side]?.overallScore
            val authoritative = source.kind == ReferenceSourceKind.COUNTERFEIT_DIAGNOSTIC
            val caution = OpenCvDecisionPolicy.counterfeitCaution(
                counterfeitScore = result.overallScore,
                genuineConsensusScore = genuineScore,
                registrationReliability = result.registrationReliability,
                authoritative = authoritative
            )
            authoritativeCounterfeitCaution = authoritativeCounterfeitCaution || caution
            usedSources += source.id

            val deltaText = genuineScore?.let { " delta vs genuine ${"%+.1f".format(result.overallScore - it)}" }
                ?: " no genuine OpenCV baseline"
            extraChecks += DiagnosticCheck(
                markerId = "opencv-counterfeit-proximity-${sample.id}",
                title = "OpenCV counterfeit-control proximity — ${source.authority.label}",
                status = if (caution) DiagnosticStatus.CAUTION else DiagnosticStatus.MANUAL_REVIEW,
                score = if (caution) result.overallScore else null,
                detail = buildString {
                    append("Counterfeit-control similarity ${"%.1f".format(result.overallScore)}/100;$deltaText. ")
                    append("Geometry ${"%.1f".format(result.geometryScore)}, edges ${"%.1f".format(result.edgeScore)}, ")
                    append("microtexture ${"%.1f".format(result.textureScore)}, luminance ${"%.1f".format(result.luminanceScore)}, ")
                    append("registration reliability ${"%.2f".format(result.registrationReliability)}. ")
                    if (authoritative) {
                        append(if (caution) {
                            "The submitted image is materially closer to this professionally documented counterfeit control than to the current genuine-control consensus. Treat as a serious forensic caution, not a standalone counterfeit verdict."
                        } else {
                            "No OpenCV counterfeit-proximity threshold was crossed. This does not exclude other fake dies or manufacturing methods."
                        })
                    } else {
                        append("Community counterfeit imagery is displayed for manual corroboration only and cannot trigger an automated counterfeit verdict.")
                    }
                },
                sourceIds = listOf(source.id)
            )
        }

        if (genuineConsensus.isEmpty() && OpenCvForensicEngine.isAvailable()) {
            extraChecks += DiagnosticCheck(
                markerId = "opencv-no-genuine-controls",
                title = "OpenCV forensic engine",
                status = DiagnosticStatus.NOT_AVAILABLE,
                detail = "OpenCV is available, but no target face has a usable documented genuine image control. Add genuine obverse/reverse controls before OpenCV can contribute to the verdict."
            )
        }

        val cvScores = genuineConsensus.values
            .filter { it.registrationReliability >= 0.18 }
            .map { it.overallScore }
        val cvEvidence = cvScores.takeIf { it.isNotEmpty() }?.average()
        var enhancedScore = when {
            base.evidenceConsistencyScore != null && cvEvidence != null ->
                (base.evidenceConsistencyScore * 0.72 + cvEvidence * 0.28).coerceIn(0.0, 100.0)
            base.evidenceConsistencyScore != null -> base.evidenceConsistencyScore
            cvEvidence != null -> cvEvidence
            else -> null
        }
        if (authoritativeCounterfeitCaution && enhancedScore != null) {
            // A known-fake proximity hit is a caution signal, not a counterfeit probability.
            enhancedScore = min(enhancedScore, 74.0)
        }

        val interpretation = when {
            authoritativeCounterfeitCaution ->
                "OpenCV ORB/RANSAC registration plus edge and microtexture analysis found elevated similarity to a professionally documented counterfeit control relative to the genuine-control consensus. Treat this as a serious forensic caution and verify edge, weight, dimensions/composition and in-hand authentication. ${base.interpretation}"
            cvEvidence != null ->
                "OpenCV-enhanced image evidence was combined with the deterministic reference, physical and provenance checks. OpenCV genuine-control consensus ${"%.1f".format(cvEvidence)}/100; this remains an evidence-consistency assessment, not an authenticity probability. ${base.interpretation}"
            else -> base.interpretation
        }

        return base.copy(
            evidenceConsistencyScore = enhancedScore,
            checks = base.checks + extraChecks,
            sourceIdsUsed = usedSources,
            interpretation = interpretation
        )
    }
}
