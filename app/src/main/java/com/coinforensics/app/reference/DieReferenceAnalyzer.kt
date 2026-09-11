package com.coinforensics.app.reference

import android.graphics.Bitmap
import com.coinforensics.app.imaging.ForensicEngine
import com.coinforensics.app.imaging.ReferenceComparator
import com.coinforensics.app.imaging.ScoreMath
import com.coinforensics.app.model.AutomatedTest
import com.coinforensics.app.model.CoinMetadata
import com.coinforensics.app.model.CoinSide
import com.coinforensics.app.model.CounterfeitRegionComparison
import com.coinforensics.app.model.DiagnosticCheck
import com.coinforensics.app.model.DiagnosticStatus
import com.coinforensics.app.model.ImageSlot
import com.coinforensics.app.model.LoadedImage
import com.coinforensics.app.model.ReferenceAssessment
import com.coinforensics.app.model.ReferencePack
import com.coinforensics.app.model.ReferenceSampleImage
import com.coinforensics.app.model.ReferenceSourceKind
import com.coinforensics.app.model.RegionComparison
import kotlin.math.abs

/**
 * Explainable reference-pack analyzer.
 *
 * Genuine controls and documented counterfeit samples are intentionally kept in separate pools.
 * Counterfeit-image proximity may raise a caution, but failure to match a known fake never proves
 * genuineness. Community-sourced fake examples remain manual evidence only.
 */
object DieReferenceAnalyzer {
    fun assess(
        pack: ReferencePack,
        metadata: CoinMetadata,
        images: Map<ImageSlot, LoadedImage>,
        referenceSamples: List<ReferenceSampleImage> = emptyList()
    ): ReferenceAssessment {
        val allGenuineRegionComparisons = mutableListOf<RegionComparison>()
        val checks = mutableListOf<DiagnosticCheck>()
        val usedSources = linkedSetOf<String>()
        val sourceById = pack.sources.associateBy { it.id }

        val obverse = images[ImageSlot.OBVERSE]
        val reverse = images[ImageSlot.REVERSE]
        val legacyRefObverse = images[ImageSlot.REFERENCE_OBVERSE]
        val legacyRefReverse = images[ImageSlot.REFERENCE_REVERSE]

        fun sourceKind(sample: ReferenceSampleImage): ReferenceSourceKind? =
            sourceById[sample.provenanceSourceId]?.kind

        fun isGenuine(sample: ReferenceSampleImage): Boolean = sourceKind(sample) in setOf(
            ReferenceSourceKind.CERTIFIED_GENUINE,
            ReferenceSourceKind.GENUINE_AUCTION,
            ReferenceSourceKind.CATALOG_REFERENCE
        )

        fun isCounterfeitReference(sample: ReferenceSampleImage): Boolean = sourceKind(sample) in setOf(
            ReferenceSourceKind.COUNTERFEIT_DIAGNOSTIC,
            ReferenceSourceKind.COMMUNITY_LEAD
        )

        val genuineSamples = referenceSamples.filter(::isGenuine)
        val counterfeitSamples = referenceSamples.filter(::isCounterfeitReference)
        val obverseGenuine = genuineSamples.filter { it.side == CoinSide.OBVERSE }
        val reverseGenuine = genuineSamples.filter { it.side == CoinSide.REVERSE }

        fun compareAgainstGenuineControls(
            side: CoinSide,
            target: LoadedImage?,
            samples: List<ReferenceSampleImage>,
            legacy: LoadedImage?
        ) {
            if (target == null) return
            val sideRegions = pack.regions.filter { it.side == side }
            if (samples.isNotEmpty()) {
                samples.forEach { sample ->
                    val overall = ReferenceComparator.compare(target.bitmap, sample.bitmap)
                    allGenuineRegionComparisons += ReferenceComparator.compareRegions(
                        target.bitmap,
                        sample.bitmap,
                        overall,
                        sideRegions
                    )
                    usedSources += sample.provenanceSourceId
                }
            } else if (legacy != null) {
                val source = legacy.provenanceSourceId?.let(sourceById::get)
                val credible = source?.kind in setOf(
                    ReferenceSourceKind.CERTIFIED_GENUINE,
                    ReferenceSourceKind.GENUINE_AUCTION,
                    ReferenceSourceKind.CATALOG_REFERENCE
                )
                if (credible || legacy.provenanceSourceId == null) {
                    val overall = ReferenceComparator.compare(target.bitmap, legacy.bitmap)
                    allGenuineRegionComparisons += ReferenceComparator.compareRegions(
                        target.bitmap,
                        legacy.bitmap,
                        overall,
                        sideRegions
                    )
                    legacy.provenanceSourceId?.let(usedSources::add)
                }
            }
        }

        compareAgainstGenuineControls(CoinSide.OBVERSE, obverse, obverseGenuine, legacyRefObverse)
        compareAgainstGenuineControls(CoinSide.REVERSE, reverse, reverseGenuine, legacyRefReverse)

        val genuineRegionConsensus = consensusRegions(allGenuineRegionComparisons)

        // Preserve provenance and make the evidence role explicit in the report.
        if (referenceSamples.isNotEmpty()) {
            referenceSamples.forEach { sample ->
                val source = sourceById[sample.provenanceSourceId]
                val (status, role) = when (source?.kind) {
                    ReferenceSourceKind.CERTIFIED_GENUINE,
                    ReferenceSourceKind.GENUINE_AUCTION -> DiagnosticStatus.CONSISTENT to "certified/documented genuine control"
                    ReferenceSourceKind.CATALOG_REFERENCE -> DiagnosticStatus.CAUTION to "catalogue/open-reference control"

                    ReferenceSourceKind.COUNTERFEIT_DIAGNOSTIC -> DiagnosticStatus.MANUAL_REVIEW to "documented counterfeit control"
                    ReferenceSourceKind.COMMUNITY_LEAD -> DiagnosticStatus.MANUAL_REVIEW to "community counterfeit lead"
                    else -> DiagnosticStatus.CAUTION to "unverified reference"
                }
                checks += DiagnosticCheck(
                    markerId = "reference-provenance-${sample.id}",
                    title = "Reference provenance — ${sample.side.label}",
                    status = status,
                    detail = if (source != null) {
                        "Attached as $role and bound to ${source.authority.label}: ${source.title}."
                    } else {
                        "Reference points to a source id that is not present in this pack."
                    },
                    sourceIds = listOf(sample.provenanceSourceId)
                )
            }

            genuineSamples.groupBy { it.side }.forEach { (side, samples) ->
                if (samples.size >= 2) {
                    checks += DiagnosticCheck(
                        markerId = "multi-control-${side.name.lowercase()}",
                        title = "Multi-control genuine consensus — ${side.label}",
                        status = DiagnosticStatus.CONSISTENT,
                        detail = "${samples.size} documented genuine controls contributed to ${side.label.lowercase()} region medians. This reduces dependence on one coin's grade, wear, toning, die state or photography.",
                        sourceIds = samples.map { it.provenanceSourceId }.distinct()
                    )
                }
            }
        } else {
            listOf(legacyRefObverse, legacyRefReverse).filterNotNull().forEach { ref ->
                val sourceId = ref.provenanceSourceId
                if (sourceId == null) {
                    checks += DiagnosticCheck(
                        markerId = "reference-provenance-${ref.slot.name.lowercase()}",
                        title = "Reference provenance",
                        status = DiagnosticStatus.CAUTION,
                        detail = "${ref.slot.shortLabel} has no documented source attached. Geometry comparison can run, but evidentiary weight should be reduced."
                    )
                } else {
                    val source = sourceById[sourceId]
                    val credible = source?.kind in setOf(
                        ReferenceSourceKind.CERTIFIED_GENUINE,
                        ReferenceSourceKind.GENUINE_AUCTION,
                        ReferenceSourceKind.CATALOG_REFERENCE
                    )
                    checks += DiagnosticCheck(
                        markerId = "reference-provenance-${ref.slot.name.lowercase()}",
                        title = "Reference provenance",
                        status = if (credible) DiagnosticStatus.CONSISTENT else DiagnosticStatus.CAUTION,
                        detail = if (source != null) {
                            "${ref.slot.shortLabel} is bound to ${source.authority.label}: ${source.title}."
                        } else {
                            "${ref.slot.shortLabel} points to a source id that is not present in this pack."
                        },
                        sourceIds = listOf(sourceId)
                    )
                }
            }
        }

        // Compare separately against documented fake samples. Only source-linked diagnostic regions
        // are used; e.g. a 1909-B fake is not compared against the 1911 date region.
        val counterfeitComparisons = mutableListOf<CounterfeitRegionComparison>()
        counterfeitSamples.forEach { sample ->
            val source = sourceById[sample.provenanceSourceId] ?: return@forEach
            val target = when (sample.side) {
                CoinSide.OBVERSE -> obverse
                CoinSide.REVERSE -> reverse
                CoinSide.EDGE -> images[ImageSlot.EDGE]
            } ?: return@forEach

            val linkedRegionIds = pack.markers
                .filter { sample.provenanceSourceId in it.sourceIds && it.regionId != null && it.side == sample.side }
                .mapNotNull { it.regionId }
                .distinct()
            val regions = pack.regions.filter { it.side == sample.side && it.id in linkedRegionIds }
            if (regions.isEmpty()) return@forEach

            val overall = ReferenceComparator.compare(target.bitmap, sample.bitmap)
            val results = ReferenceComparator.compareRegions(target.bitmap, sample.bitmap, overall, regions)
            results.forEach { result ->
                val genuine = genuineRegionConsensus.firstOrNull { it.regionId == result.regionId }
                val delta = genuine?.let { result.consistencyScore - it.consistencyScore }
                val closerToFake = result.consistencyScore >= 80.0 && (delta ?: 0.0) >= 6.0
                val authorityText = if (source.kind == ReferenceSourceKind.COUNTERFEIT_DIAGNOSTIC) {
                    "documented counterfeit source"
                } else {
                    "community comparison lead"
                }
                val interpretation = when {
                    source.kind == ReferenceSourceKind.COMMUNITY_LEAD && closerToFake ->
                        "Visually closer to this $authorityText than to the attached genuine consensus in this region. Manual corroboration only; community evidence cannot establish counterfeit status."

                    source.kind == ReferenceSourceKind.COUNTERFEIT_DIAGNOSTIC && closerToFake ->
                        "Visually closer to this $authorityText than to the attached genuine consensus in this source-linked diagnostic region. Treat as a caution requiring physical and expert corroboration."

                    genuine == null ->
                        "Similarity to this $authorityText is shown without a genuine-control baseline; do not interpret the raw similarity as counterfeit evidence."

                    else ->
                        "This region is not materially closer to the attached counterfeit control than to the genuine-control consensus. That does not prove authenticity or exclude other counterfeit families."
                }
                counterfeitComparisons += CounterfeitRegionComparison(
                    sourceId = source.id,
                    sourceTitle = source.title,
                    sourceScope = source.scope,
                    regionId = result.regionId,
                    regionLabel = result.label,
                    side = result.side,
                    counterfeitSimilarityScore = result.consistencyScore,
                    genuineConsensusScore = genuine?.consistencyScore,
                    deltaVsGenuine = delta,
                    interpretation = interpretation
                )
            }

            val sourceComparisons = counterfeitComparisons.filter { it.sourceId == source.id && it.side == sample.side }
            val suspicious = sourceComparisons.any {
                it.counterfeitSimilarityScore >= 80.0 && (it.deltaVsGenuine ?: 0.0) >= 6.0
            }
            val automatedAuthority = source.kind == ReferenceSourceKind.COUNTERFEIT_DIAGNOSTIC
            checks += DiagnosticCheck(
                markerId = "counterfeit-proximity-${sample.id}",
                title = "Counterfeit-reference proximity — ${source.authority.label}",
                status = if (automatedAuthority && suspicious) DiagnosticStatus.CAUTION else DiagnosticStatus.MANUAL_REVIEW,
                detail = when {
                    sourceComparisons.isEmpty() -> "No source-linked image region is defined for this counterfeit reference."
                    automatedAuthority && suspicious -> "At least one source-linked region is visually closer to the documented counterfeit control than to the current genuine-control consensus. This is a caution, not a counterfeit verdict."
                    automatedAuthority -> "No tested source-linked region is materially closer to this known counterfeit control. This cannot exclude other counterfeit dies or manufacturing methods."
                    else -> "Community counterfeit image attached. Similarity results are displayed for manual study only and never alter the automated authenticity evidence score."
                },
                sourceIds = listOf(source.id)
            )
            usedSources += source.id
        }

        // Surface statistics use genuine controls only. A fake image is never allowed to become the
        // baseline simply because it was attached most recently.
        val surfacePairs = buildList<Pair<LoadedImage, Bitmap>> {
            if (obverse != null) {
                if (obverseGenuine.isNotEmpty()) obverseGenuine.forEach { add(obverse to it.bitmap) }
                else if (legacyIsCredibleGenuine(legacyRefObverse, sourceById)) legacyRefObverse?.let { add(obverse to it.bitmap) }
            }
            if (reverse != null) {
                if (reverseGenuine.isNotEmpty()) reverseGenuine.forEach { add(reverse to it.bitmap) }
                else if (legacyIsCredibleGenuine(legacyRefReverse, sourceById)) legacyRefReverse?.let { add(reverse to it.bitmap) }
            }
        }

        pack.markers.forEach { marker ->
            val check = when (marker.automatedTest) {
                AutomatedTest.REGION_REFERENCE_MATCH -> {
                    val region = marker.regionId?.let { id -> genuineRegionConsensus.firstOrNull { it.regionId == id } }
                    if (region == null) {
                        DiagnosticCheck(
                            markerId = marker.id,
                            title = marker.title,
                            status = DiagnosticStatus.NOT_AVAILABLE,
                            detail = "A matching target and documented genuine reference image are required for this region.",
                            sourceIds = marker.sourceIds
                        )
                    } else {
                        val status = when {
                            region.consistencyScore >= 82.0 -> DiagnosticStatus.CONSISTENT
                            region.consistencyScore >= 66.0 -> DiagnosticStatus.CAUTION
                            else -> DiagnosticStatus.INCONSISTENT
                        }
                        DiagnosticCheck(
                            markerId = marker.id,
                            title = marker.title,
                            status = status,
                            score = region.consistencyScore,
                            detail = "${region.label}: ${"%.1f".format(region.consistencyScore)}/100 genuine-consensus visual consistency; edge ${"%.1f".format(region.edgeScore)}, luminance ${"%.1f".format(region.luminanceScore)}. ${marker.description}",
                            sourceIds = marker.sourceIds
                        )
                    }
                }

                AutomatedTest.WEIGHT_TOLERANCE -> weightCheck(
                    marker.id,
                    marker.title,
                    marker.description,
                    marker.sourceIds,
                    metadata,
                    pack
                )

                AutomatedTest.DIAMETER_TOLERANCE -> diameterCheck(
                    marker.id,
                    marker.title,
                    marker.description,
                    marker.sourceIds,
                    metadata,
                    pack
                )

                AutomatedTest.SURFACE_REFERENCE_DELTA -> surfaceDeltaCheck(
                    marker.id,
                    marker.title,
                    marker.description,
                    marker.sourceIds,
                    surfacePairs
                )

                AutomatedTest.MANUAL_ONLY -> DiagnosticCheck(
                    markerId = marker.id,
                    title = marker.title,
                    status = DiagnosticStatus.MANUAL_REVIEW,
                    detail = marker.description,
                    sourceIds = marker.sourceIds
                )
            }
            checks += check
            if (check.status != DiagnosticStatus.NOT_AVAILABLE) usedSources += marker.sourceIds
        }

        val metadataMismatch = metadataMismatch(pack, metadata)
        if (metadataMismatch.isNotEmpty()) {
            checks.add(
                0,
                DiagnosticCheck(
                    markerId = "metadata-pack-match",
                    title = "Selected reference pack vs case metadata",
                    status = DiagnosticStatus.CAUTION,
                    detail = metadataMismatch
                )
            )
        }

        // Community comparisons never enter this score. An authoritative counterfeit reference may
        // contribute only through a CAUTION check; not matching one known fake never boosts the score.
        val nonGeometryAutomatedMarkerIds = pack.markers
            .filter {
                it.automatedTest == AutomatedTest.WEIGHT_TOLERANCE ||
                    it.automatedTest == AutomatedTest.DIAMETER_TOLERANCE ||
                    it.automatedTest == AutomatedTest.SURFACE_REFERENCE_DELTA
            }
            .map { it.id }
            .toSet()
        val scoredChecks = checks
            .filter { check ->
                check.markerId in nonGeometryAutomatedMarkerIds ||
                    check.markerId == "metadata-pack-match" ||
                    (check.markerId.startsWith("counterfeit-proximity-") && check.status == DiagnosticStatus.CAUTION)
            }
            .mapNotNull { check ->
                when (check.status) {
                    DiagnosticStatus.CONSISTENT -> 92.0
                    DiagnosticStatus.CAUTION -> 68.0
                    DiagnosticStatus.INCONSISTENT -> 35.0
                    else -> null
                }
            }
        val regionScores = genuineRegionConsensus.map { it.consistencyScore }
        val combinedInputs = regionScores + scoredChecks
        val evidenceScore = combinedInputs.takeIf { it.isNotEmpty() }?.average()?.coerceIn(0.0, 100.0)

        val attachedSourceKinds = referenceSamples.mapNotNull { sourceById[it.provenanceSourceId]?.kind }
        val certifiedCount = attachedSourceKinds.count {
            it == ReferenceSourceKind.CERTIFIED_GENUINE || it == ReferenceSourceKind.GENUINE_AUCTION
        }
        val catalogCount = attachedSourceKinds.count { it == ReferenceSourceKind.CATALOG_REFERENCE }
        val provenanceStrength = when {
            certifiedCount >= 4 -> 100.0
            certifiedCount >= 2 -> 94.0
            certifiedCount == 1 -> 86.0
            catalogCount >= 4 -> 76.0
            catalogCount >= 2 -> 70.0
            catalogCount == 1 -> 62.0
            else -> if (genuineRegionConsensus.isNotEmpty()) 48.0 else 30.0
        }
        val provenanceSummary = when {
            certifiedCount > 0 -> "$certifiedCount certified/documented genuine image control(s) and $catalogCount catalogue/open-reference control(s) contributed."
            catalogCount > 0 -> "No certified control is attached; $catalogCount catalogue/open-reference image control(s) contributed, so provenance confidence is capped."
            genuineRegionConsensus.isNotEmpty() -> "Visual comparison ran against a legacy/unverified reference; provenance is weak."
            else -> "No usable genuine-image provenance is attached."
        }

        val hasCounterfeitCaution = checks.any {
            it.markerId.startsWith("counterfeit-proximity-") && it.status == DiagnosticStatus.CAUTION
        }
        val interpretation = when {
            evidenceScore == null -> "Insufficient evidence. Attach target images and preferably several certified genuine reference controls with provenance."
            hasCounterfeitCaution -> "A source-linked region shows elevated proximity to a documented counterfeit control. Treat this as a forensic caution, then verify weight, diameter, edge, composition where possible, and professional in-hand authentication."
            checks.any { it.status == DiagnosticStatus.INCONSISTENT } -> "One or more measured checks are materially inconsistent with the attached genuine consensus or documented physical specification. Recheck measurement accuracy, identity/variety, wear, image registration and source provenance before treating this as counterfeit evidence."
            genuineRegionConsensus.isEmpty() -> "The available physical/specification evidence can be summarized, but no die-level image comparison against a documented genuine control is available yet. Add obverse/reverse genuine controls before interpreting the score as visual consistency."
            certifiedCount == 0 && catalogCount > 0 && evidenceScore >= 70.0 -> "The image is visually consistent with the available catalogue/open-reference controls, but no certified or professionally documented genuine image control is attached. Treat this as type/series consistency, not strong die-level authentication."
            evidenceScore >= 84.0 -> "The available image/physical evidence is strongly consistent with the documented genuine control set. This is not an authenticity probability and cannot exclude a sophisticated transfer-die counterfeit."
            evidenceScore >= 70.0 -> "The available evidence is broadly consistent but contains cautions. Review low-scoring regions and documented counterfeit diagnostics manually."
            else -> "The available evidence has substantial disagreement or weak provenance. Obtain better images, additional certified genuine controls, edge/weight evidence and professional authentication before purchase."
        }

        return ReferenceAssessment(
            packId = pack.id,
            packName = pack.displayName,
            evidenceConsistencyScore = evidenceScore,
            regionComparisons = genuineRegionConsensus,
            counterfeitComparisons = counterfeitComparisons,
            checks = checks,
            sourceIdsUsed = usedSources,
            interpretation = interpretation,
            provenanceStrengthScore = provenanceStrength,
            provenanceSummary = provenanceSummary
        )
    }

    private fun legacyIsCredibleGenuine(
        legacy: LoadedImage?,
        sources: Map<String, com.coinforensics.app.model.ReferenceSource>
    ): Boolean {
        if (legacy == null) return false
        val sourceId = legacy.provenanceSourceId ?: return true
        return sources[sourceId]?.kind in setOf(
            ReferenceSourceKind.CERTIFIED_GENUINE,
            ReferenceSourceKind.GENUINE_AUCTION,
            ReferenceSourceKind.CATALOG_REFERENCE
        )
    }

    private fun consensusRegions(all: List<RegionComparison>): List<RegionComparison> {
        if (all.isEmpty()) return emptyList()
        return all.groupBy { it.regionId }.values.map { group ->
            val first = group.first()
            val score = median(group.map { it.consistencyScore })
            val lum = median(group.map { it.luminanceScore })
            val edge = median(group.map { it.edgeScore })
            RegionComparison(
                regionId = first.regionId,
                label = first.label,
                side = first.side,
                consistencyScore = score,
                luminanceScore = lum,
                edgeScore = edge,
                interpretation = ScoreMath.interpretation(score)
            )
        }.sortedWith(compareBy({ it.side.ordinal }, { it.label }))
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    private fun weightCheck(
        markerId: String,
        title: String,
        description: String,
        sourceIds: List<String>,
        metadata: CoinMetadata,
        pack: ReferencePack
    ): DiagnosticCheck {
        val measured = metadata.weightGrams.trim().toDoubleOrNull()
        val expected = pack.physical.expectedWeightGrams
        if (measured == null || expected == null) {
            return DiagnosticCheck(markerId, title, DiagnosticStatus.NOT_AVAILABLE, detail = "Enter a measured weight in grams. $description", sourceIds = sourceIds)
        }
        val delta = abs(measured - expected)
        val tolerance = pack.physical.weightToleranceGrams ?: 0.35
        val status = when {
            delta <= tolerance -> DiagnosticStatus.CONSISTENT
            delta <= tolerance * 2.0 -> DiagnosticStatus.CAUTION
            else -> DiagnosticStatus.INCONSISTENT
        }
        val score = (100.0 - delta / (tolerance * 2.0) * 65.0).coerceIn(0.0, 100.0)
        return DiagnosticCheck(
            markerId = markerId,
            title = title,
            status = status,
            score = score,
            detail = "Measured ${"%.2f".format(measured)} g vs documented ${"%.2f".format(expected)} g; difference ${"%.2f".format(delta)} g. $description",
            sourceIds = sourceIds
        )
    }

    private fun diameterCheck(
        markerId: String,
        title: String,
        description: String,
        sourceIds: List<String>,
        metadata: CoinMetadata,
        pack: ReferencePack
    ): DiagnosticCheck {
        val measured = metadata.diameterMm.trim().toDoubleOrNull()
        val expected = pack.physical.expectedDiameterMm
        if (measured == null || expected == null) {
            return DiagnosticCheck(markerId, title, DiagnosticStatus.NOT_AVAILABLE, detail = "Enter a caliper-measured diameter in millimeters. $description", sourceIds = sourceIds)
        }
        val delta = abs(measured - expected)
        val tolerance = pack.physical.diameterToleranceMm ?: 0.35
        val status = when {
            delta <= tolerance -> DiagnosticStatus.CONSISTENT
            delta <= tolerance * 2.0 -> DiagnosticStatus.CAUTION
            else -> DiagnosticStatus.INCONSISTENT
        }
        val score = (100.0 - delta / (tolerance * 2.0) * 65.0).coerceIn(0.0, 100.0)
        return DiagnosticCheck(
            markerId = markerId,
            title = title,
            status = status,
            score = score,
            detail = "Measured ${"%.2f".format(measured)} mm vs documented ${"%.2f".format(expected)} mm; difference ${"%.2f".format(delta)} mm. $description",
            sourceIds = sourceIds
        )
    }

    private fun surfaceDeltaCheck(
        markerId: String,
        title: String,
        description: String,
        sourceIds: List<String>,
        pairs: List<Pair<LoadedImage, Bitmap>>
    ): DiagnosticCheck {
        if (pairs.isEmpty()) {
            return DiagnosticCheck(markerId, title, DiagnosticStatus.NOT_AVAILABLE, detail = "A target/genuine-reference image pair is required. $description", sourceIds = sourceIds)
        }
        val divergences = pairs.map { (target, referenceBitmap) ->
            val t = ForensicEngine.analyze(target.bitmap)
            val r = ForensicEngine.analyze(referenceBitmap)
            val ratio = t.surface.microtextureEnergy / r.surface.microtextureEnergy.coerceAtLeast(0.002)
            if (ratio >= 1.0) ratio else 1.0 / ratio.coerceAtLeast(0.05)
        }
        val consensusDivergence = median(divergences)
        val status = when {
            consensusDivergence < 1.45 -> DiagnosticStatus.CONSISTENT
            else -> DiagnosticStatus.CAUTION
        }
        val score = (100.0 - (consensusDivergence - 1.0) * 35.0).coerceIn(25.0, 100.0)
        return DiagnosticCheck(
            markerId = markerId,
            title = title,
            status = status,
            score = score,
            detail = "Median target/genuine-control microtexture divergence: ${"%.2f".format(consensusDivergence)}× across ${pairs.size} comparison(s). $description",
            sourceIds = sourceIds
        )
    }

    private fun metadataMismatch(pack: ReferencePack, metadata: CoinMetadata): String {
        val issues = mutableListOf<String>()
        if (metadata.year.isNotBlank() && metadata.year.trim() != pack.year) issues += "case year '${metadata.year}' differs from pack year '${pack.year}'"
        if (metadata.country.isNotBlank() && !metadata.country.contains("Brit", ignoreCase = true) && !pack.country.contains(metadata.country, ignoreCase = true)) {
            issues += "case issuer '${metadata.country}' does not obviously match '${pack.country}'"
        }
        return if (issues.isEmpty()) "" else issues.joinToString(prefix = "Check reference selection: ", separator = "; ", postfix = ".")
    }
}
