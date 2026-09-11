package com.coinforensics.app.reference

import com.coinforensics.app.model.AutomatedTest
import com.coinforensics.app.model.CoinIdentityCandidate
import com.coinforensics.app.model.CoinMetadata
import com.coinforensics.app.model.CoinSide
import com.coinforensics.app.model.DiagnosticCategory
import com.coinforensics.app.model.DiagnosticMarker
import com.coinforensics.app.model.DiagnosticScope
import com.coinforensics.app.model.DiscoveredReference
import com.coinforensics.app.model.NormalizedRegion
import com.coinforensics.app.model.PhysicalSpecification
import com.coinforensics.app.model.ReferencePack
import com.coinforensics.app.model.ReferenceSource
import com.coinforensics.app.model.ReferenceSourceKind
import com.coinforensics.app.model.RegionShape

/**
 * Builds a safe generic comparison pack when no hand-curated exact die pack exists yet.
 * It intentionally avoids pretending to know where a date, mintmark, or letter sits on an
 * arbitrary coin. Instead it compares full designs, rim zones and stable spatial sectors.
 */
object GenericReferencePackFactory {
    fun create(
        metadata: CoinMetadata,
        identity: CoinIdentityCandidate?,
        discovered: List<DiscoveredReference>
    ): ReferencePack {
        val country = metadata.country.ifBlank { "Unknown issuer" }
        val denomination = metadata.denomination.ifBlank { identity?.title ?: "Unknown denomination" }
        val year = metadata.year.ifBlank { "Unknown year" }
        val mint = metadata.mint.ifBlank { "Unspecified mint / variety" }
        val id = "dynamic-${slug(country)}-${slug(denomination)}-${slug(year)}-${slug(mint)}"

        val regions = genericRegions()
        val sources = discovered.map { it.toSource() }.distinctBy { it.id } + identitySource(identity)
        val cleanSources = sources.filterNotNull().distinctBy { it.id }

        val markers = mutableListOf<DiagnosticMarker>()
        for (side in listOf(CoinSide.OBVERSE, CoinSide.REVERSE)) {
            val sideName = side.name.lowercase()
            val relevantSourceIds = cleanSources.filter {
                it.kind == ReferenceSourceKind.CERTIFIED_GENUINE ||
                    it.kind == ReferenceSourceKind.GENUINE_AUCTION ||
                    it.kind == ReferenceSourceKind.CATALOG_REFERENCE
            }.map { it.id }
            for (region in regions.filter { it.side == side }) {
                markers += DiagnosticMarker(
                    id = "generic-${sideName}-${region.id}",
                    title = "${side.label}: ${region.label} geometry",
                    category = if (region.shape == RegionShape.ANNULUS) DiagnosticCategory.RIM_DENTICLES else DiagnosticCategory.RELIEF_DETAIL,
                    side = side,
                    regionId = region.id,
                    scope = DiagnosticScope.SERIES_WIDE,
                    automatedTest = AutomatedTest.REGION_REFERENCE_MATCH,
                    description = "Generic high-resolution geometry comparison. This region is spatial rather than die-semantic because no curated marker map is installed for this issue yet.",
                    sourceIds = relevantSourceIds
                )
            }
        }

        identity?.weightGrams?.let {
            markers += DiagnosticMarker(
                id = "generic-weight",
                title = "Measured weight vs catalogue specification",
                category = DiagnosticCategory.WEIGHT,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                automatedTest = AutomatedTest.WEIGHT_TOLERANCE,
                description = "A physical corroboration check only; circulation, clipping, damage and catalogue uncertainty must be considered.",
                sourceIds = listOfNotNull(identitySource(identity)?.id)
            )
        }
        identity?.diameterMm?.let {
            markers += DiagnosticMarker(
                id = "generic-diameter",
                title = "Measured diameter vs catalogue specification",
                category = DiagnosticCategory.EDGE,
                scope = DiagnosticScope.EXACT_DATE_VARIETY,
                automatedTest = AutomatedTest.DIAMETER_TOLERANCE,
                description = "Uses a real caliper measurement, never a photo-derived physical scale.",
                sourceIds = listOfNotNull(identitySource(identity)?.id)
            )
        }

        markers += DiagnosticMarker(
            id = "generic-surface-delta",
            title = "Surface microtexture divergence vs genuine controls",
            category = DiagnosticCategory.SURFACE_TEXTURE,
            scope = DiagnosticScope.GENERAL_AUTHENTICATION,
            automatedTest = AutomatedTest.SURFACE_REFERENCE_DELTA,
            description = "Flags strong texture divergence only. Lighting, cleaning, corrosion, wear and compression can create false positives.",
            sourceIds = cleanSources.filter { it.kind != ReferenceSourceKind.COMMUNITY_LEAD }.map { it.id }
        )
        markers += DiagnosticMarker(
            id = "generic-edge-manual",
            title = "Edge / reeding requires direct comparison",
            category = DiagnosticCategory.EDGE,
            side = CoinSide.EDGE,
            scope = DiagnosticScope.EXACT_DATE_VARIETY,
            automatedTest = AutomatedTest.MANUAL_ONLY,
            description = "Capture the complete edge where possible. Generic packs do not infer an edge pattern unless a curated exact-issue manifest supplies one.",
            sourceIds = emptyList()
        )

        // Link every discovered counterfeit/control lead to conservative broad regions so the
        // analyzer can show proximity without allowing a low fake match to boost authenticity.
        discovered.filter {
            it.kind == ReferenceSourceKind.COUNTERFEIT_DIAGNOSTIC || it.kind == ReferenceSourceKind.COMMUNITY_LEAD
        }.forEach { ref ->
            val sides = ref.side?.let(::listOf) ?: listOf(CoinSide.OBVERSE, CoinSide.REVERSE)
            sides.forEach { side ->
                val full = regions.first { it.id == "${side.name.lowercase()}-full" }
                markers += DiagnosticMarker(
                    id = "counterfeit-link-${slug(ref.id)}-${side.name.lowercase()}",
                    title = "Compare against ${ref.title}",
                    category = DiagnosticCategory.TRANSFER_DIE,
                    side = side,
                    regionId = full.id,
                    scope = ref.scope,
                    automatedTest = AutomatedTest.MANUAL_ONLY,
                    description = "Counterfeit proximity is an independent caution channel. Matching a known fake can be significant; not matching it never proves genuine.",
                    sourceIds = listOf(ref.id)
                )
            }
        }

        val refs = buildList {
            identity?.let { addAll(it.catalogReferences) }
            cleanSources.mapNotNullTo(this) { s -> s.certificationNumber?.let { "Cert $it" } }
        }.distinct()

        return ReferencePack(
            id = id,
            displayName = listOf(country, year, denomination, mint).filter { it.isNotBlank() }.joinToString(" "),
            country = country,
            denomination = denomination,
            year = year,
            mintOrVariety = mint,
            catalogReferences = refs.ifEmpty { listOf("Dynamic online research pack") },
            physical = PhysicalSpecification(
                expectedWeightGrams = identity?.weightGrams,
                weightToleranceGrams = identity?.weightGrams?.let { maxOf(0.20, it * 0.015) },
                expectedDiameterMm = identity?.diameterMm,
                diameterToleranceMm = identity?.diameterMm?.let { maxOf(0.20, it * 0.010) },
                expectedThicknessMm = identity?.thicknessMm,
                compositionNote = "Physical specifications are imported from the selected catalogue identity when available; verify the exact year/mint/variety before treating them as authoritative.",
                sourceIds = listOfNotNull(identitySource(identity)?.id)
            ),
            regions = regions,
            markers = markers.distinctBy { it.id },
            sources = cleanSources,
            notes = "Dynamic generic pack. It can authenticate visual consistency against documented controls, but it does not claim exact die-marker knowledge until a curated issue manifest is available. Certified controls outrank catalogue/open-reference images; community counterfeit leads remain manual evidence."
        )
    }

    fun mergeDiscoveredSources(base: ReferencePack, discovered: List<DiscoveredReference>): ReferencePack {
        if (discovered.isEmpty()) return base
        val newSources = (base.sources + discovered.map { it.toSource() }).distinctBy { it.id }
        val newMarkers = base.markers.toMutableList()
        val regionBySide = base.regions.groupBy { it.side }
        discovered.filter {
            it.kind == ReferenceSourceKind.COUNTERFEIT_DIAGNOSTIC || it.kind == ReferenceSourceKind.COMMUNITY_LEAD
        }.forEach { ref ->
            val sides = ref.side?.let(::listOf) ?: listOf(CoinSide.OBVERSE, CoinSide.REVERSE)
            sides.forEach sideLoop@ { side ->
                val full = regionBySide[side]?.firstOrNull { it.shape == RegionShape.FULL_COIN }
                    ?: regionBySide[side]?.firstOrNull()
                    ?: return@sideLoop
                val markerId = "online-counterfeit-${slug(ref.id)}-${side.name.lowercase()}"
                if (newMarkers.none { it.id == markerId }) {
                    newMarkers += DiagnosticMarker(
                        id = markerId,
                        title = "Online counterfeit/reference proximity — ${ref.title}",
                        category = DiagnosticCategory.TRANSFER_DIE,
                        side = side,
                        regionId = full.id,
                        scope = ref.scope,
                        automatedTest = AutomatedTest.MANUAL_ONLY,
                        description = "Source-linked online reference. Similarity is reported separately from genuine consistency and cannot by itself establish authenticity.",
                        sourceIds = listOf(ref.id)
                    )
                }
            }
        }
        return base.copy(sources = newSources, markers = newMarkers)
    }

    private fun identitySource(identity: CoinIdentityCandidate?): ReferenceSource? = identity?.let {
        ReferenceSource(
            id = "identity-${slug(it.provider.name)}-${slug(it.id)}",
            title = it.title,
            authority = when (it.provider.name) {
                "NUMISTA" -> com.coinforensics.app.model.EvidenceAuthority.NUMISTA
                "CURATED_MANIFEST" -> com.coinforensics.app.model.EvidenceAuthority.REMOTE_CATALOG
                else -> com.coinforensics.app.model.EvidenceAuthority.OTHER
            },
            kind = ReferenceSourceKind.SPECIFICATION,
            scope = DiagnosticScope.EXACT_DATE_VARIETY,
            url = it.sourceUrl,
            notes = it.matchNote,
            imageUsageNote = "Identity/specification record; image rights are evaluated separately per image asset."
        )
    }

    private fun DiscoveredReference.toSource() = ReferenceSource(
        id = id,
        title = title,
        authority = authority,
        kind = kind,
        scope = scope,
        url = sourcePageUrl,
        notes = notes,
        imageUsageNote = buildString {
            if (canAutoDownload) append("Automatic image reuse permitted by recorded license")
            else append("Source-only until image permission/license is confirmed")
            licenseName?.let { append(" • $it") }
            creator?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
            rightsBasis?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
            sha256?.let { append(" • SHA-256 pinned") }
        }
    )

    private fun genericRegions(): List<NormalizedRegion> = buildList {
        for (side in listOf(CoinSide.OBVERSE, CoinSide.REVERSE)) {
            val p = side.name.lowercase()
            add(NormalizedRegion("$p-full", "Full design", side, RegionShape.FULL_COIN, description = "Whole-face geometry"))
            add(NormalizedRegion("$p-rim", "Rim / denticle zone", side, RegionShape.ANNULUS, innerRadius = 0.78, outerRadius = 0.98, description = "Outer rim and border devices"))
            add(NormalizedRegion("$p-center", "Central relief", side, RegionShape.RECT, 0.24, 0.24, 0.76, 0.76, description = "Central device geometry"))
            add(NormalizedRegion("$p-upper", "Upper field / legend", side, RegionShape.RECT, 0.12, 0.08, 0.88, 0.38, description = "Upper design sector"))
            add(NormalizedRegion("$p-lower", "Lower field / legend", side, RegionShape.RECT, 0.12, 0.62, 0.88, 0.92, description = "Lower design sector"))
            add(NormalizedRegion("$p-left", "Left design sector", side, RegionShape.RECT, 0.08, 0.20, 0.42, 0.80, description = "Left-side geometry"))
            add(NormalizedRegion("$p-right", "Right design sector", side, RegionShape.RECT, 0.58, 0.20, 0.92, 0.80, description = "Right-side geometry"))
        }
    }

    private fun slug(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(64)
}
