package com.coinforensics.app.reference

import com.coinforensics.app.model.AutomatedTest
import com.coinforensics.app.model.CoinIdentityCandidate
import com.coinforensics.app.model.CoinMetadata
import com.coinforensics.app.model.CoinSide
import com.coinforensics.app.model.DiagnosticCategory
import com.coinforensics.app.model.DiagnosticMarker
import com.coinforensics.app.model.DiagnosticScope
import com.coinforensics.app.model.DiscoveredReference
import com.coinforensics.app.model.DiscoveryProvider
import com.coinforensics.app.model.EvidenceAuthority
import com.coinforensics.app.model.NormalizedRegion
import com.coinforensics.app.model.PhysicalSpecification
import com.coinforensics.app.model.ReferencePack
import com.coinforensics.app.model.ReferenceSource
import com.coinforensics.app.model.ReferenceSourceKind
import com.coinforensics.app.model.ReferenceTrust
import com.coinforensics.app.model.RegionShape
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser for a portable reference-pack manifest. This is the scalable path for exact die packs:
 * a maintained catalog can publish provenance, exact diagnostic regions, known counterfeit markers,
 * physical specifications and legally reusable image assets without updating the APK.
 */
object ReferenceManifestParser {
    data class Result(
        val packs: List<ReferencePack>,
        val references: List<DiscoveredReference>,
        val identities: List<CoinIdentityCandidate>,
        val warnings: List<String>
    )

    fun parse(json: String, metadata: CoinMetadata): Result {
        val root = JSONObject(json)
        val packObjects = when {
            root.has("packs") -> root.optJSONArray("packs") ?: JSONArray()
            root.has("id") -> JSONArray().put(root)
            else -> JSONArray()
        }
        val packs = mutableListOf<ReferencePack>()
        val refs = mutableListOf<DiscoveredReference>()
        val identities = mutableListOf<CoinIdentityCandidate>()
        val warnings = mutableListOf<String>()

        for (i in 0 until packObjects.length()) {
            val obj = packObjects.optJSONObject(i) ?: continue
            runCatching {
                val pack = parsePack(obj)
                val images = parseImages(obj, pack)
                pack to images
            }.onSuccess { (pack, images) ->
                if (matches(pack, metadata)) {
                    packs += pack
                    identities += CoinIdentityCandidate(
                        id = pack.id,
                        provider = DiscoveryProvider.CURATED_MANIFEST,
                        title = pack.displayName,
                        sourceUrl = obj.optString("source_url").ifBlank { pack.sources.firstOrNull()?.url.orEmpty() },
                        catalogReferences = pack.catalogReferences,
                        weightGrams = pack.physical.expectedWeightGrams,
                        diameterMm = pack.physical.expectedDiameterMm,
                        thicknessMm = pack.physical.expectedThicknessMm,
                        matchNote = "Exact/curated manifest pack"
                    )
                    refs += images
                }
            }.onFailure { warnings += "Manifest pack ${obj.optString("id", "#$i")} could not be parsed: ${it.message}" }
        }
        return Result(packs, refs, identities, warnings)
    }

    private fun parsePack(obj: JSONObject): ReferencePack {
        val physicalObj = obj.optJSONObject("physical") ?: JSONObject()
        val sources = parseSources(obj.optJSONArray("sources") ?: JSONArray())
        val regions = parseRegions(obj.optJSONArray("regions") ?: JSONArray())
        val markers = parseMarkers(obj.optJSONArray("markers") ?: JSONArray())
        val pack = ReferencePack(
            id = obj.requireText("id"),
            displayName = obj.optString("display_name").ifBlank { obj.requireText("id") },
            country = obj.optString("country"),
            denomination = obj.optString("denomination"),
            year = obj.optString("year"),
            mintOrVariety = obj.optString("mint_or_variety"),
            catalogReferences = obj.optJSONArray("catalog_references").strings(),
            physical = PhysicalSpecification(
                expectedWeightGrams = physicalObj.optDoubleOrNull("expected_weight_g"),
                weightToleranceGrams = physicalObj.optDoubleOrNull("weight_tolerance_g"),
                expectedDiameterMm = physicalObj.optDoubleOrNull("expected_diameter_mm"),
                diameterToleranceMm = physicalObj.optDoubleOrNull("diameter_tolerance_mm"),
                expectedThicknessMm = physicalObj.optDoubleOrNull("expected_thickness_mm"),
                thicknessToleranceMm = physicalObj.optDoubleOrNull("thickness_tolerance_mm"),
                compositionNote = physicalObj.optString("composition_note").takeIf { it.isNotBlank() },
                sourceIds = physicalObj.optJSONArray("source_ids").strings()
            ),
            regions = regions,
            markers = markers,
            sources = sources,
            notes = obj.optString("notes", "Curated remote reference pack")
        )
        validatePack(pack)
        return pack
    }

    private fun parseSources(array: JSONArray): List<ReferenceSource> = buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            add(
                ReferenceSource(
                    id = o.requireText("id"),
                    title = o.optString("title", o.requireText("id")),
                    authority = enumOr(o.optString("authority"), EvidenceAuthority.OTHER),
                    kind = enumOr(o.optString("kind"), ReferenceSourceKind.SPECIFICATION),
                    scope = enumOr(o.optString("scope"), DiagnosticScope.EXACT_DATE_VARIETY),
                    url = o.optString("url"),
                    certificationNumber = o.optString("certification_number").takeIf { it.isNotBlank() },
                    gradeOrStatus = o.optString("grade_or_status").takeIf { it.isNotBlank() },
                    notes = o.optString("notes"),
                    imageUsageNote = o.optString("image_usage_note", "External source; image permission is evaluated per asset.")
                )
            )
        }
    }

    private fun parseRegions(array: JSONArray): List<NormalizedRegion> = buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            add(
                NormalizedRegion(
                    id = o.requireText("id"),
                    label = o.optString("label", o.requireText("id")),
                    side = enumOr(o.optString("side"), CoinSide.OBVERSE),
                    shape = enumOr(o.optString("shape"), RegionShape.RECT),
                    left = o.optDouble("left", 0.0),
                    top = o.optDouble("top", 0.0),
                    right = o.optDouble("right", 1.0),
                    bottom = o.optDouble("bottom", 1.0),
                    innerRadius = o.optDouble("inner_radius", 0.0),
                    outerRadius = o.optDouble("outer_radius", 1.0),
                    description = o.optString("description")
                )
            )
        }
    }

    private fun parseMarkers(array: JSONArray): List<DiagnosticMarker> = buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            add(
                DiagnosticMarker(
                    id = o.requireText("id"),
                    title = o.optString("title", o.requireText("id")),
                    category = enumOr(o.optString("category"), DiagnosticCategory.RELIEF_DETAIL),
                    side = o.optString("side").takeIf { it.isNotBlank() }?.let { enumOr(it, CoinSide.OBVERSE) },
                    regionId = o.optString("region_id").takeIf { it.isNotBlank() },
                    scope = enumOr(o.optString("scope"), DiagnosticScope.EXACT_DATE_VARIETY),
                    automatedTest = enumOr(o.optString("automated_test"), AutomatedTest.MANUAL_ONLY),
                    description = o.optString("description"),
                    sourceIds = o.optJSONArray("source_ids").strings()
                )
            )
        }
    }

    private fun parseImages(obj: JSONObject, pack: ReferencePack): List<DiscoveredReference> {
        val sources = pack.sources.associateBy { it.id }
        val array = obj.optJSONArray("images") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val sourceId = o.requireText("source_id")
                val source = requireNotNull(sources[sourceId]) { "Image '${o.optString("id", "#$i")}' references unknown source '$sourceId'." }
                val license = o.optString("license_name").takeIf { it.isNotBlank() }
                add(
                    DiscoveredReference(
                        id = o.optString("id").ifBlank { "$sourceId-image-$i" },
                        provider = DiscoveryProvider.CURATED_MANIFEST,
                        title = o.optString("title").ifBlank { source.title },
                        sourcePageUrl = o.optString("source_page_url").ifBlank { source.url },
                        imageUrl = o.optString("image_url").takeIf { it.isNotBlank() },
                        thumbnailUrl = o.optString("thumbnail_url").takeIf { it.isNotBlank() },
                        side = o.optString("side").takeIf { it.isNotBlank() }?.let { enumOr(it, CoinSide.OBVERSE) },
                        kind = source.kind,
                        scope = source.scope,
                        authority = source.authority,
                        trust = trustFor(source.kind),
                        licenseName = license,
                        licenseUrl = o.optString("license_url").takeIf { it.isNotBlank() },
                        creator = o.optString("creator").takeIf { it.isNotBlank() },
                        rightsBasis = o.optString("permission_basis").takeIf { it.isNotBlank() },
                        sha256 = o.optString("sha256").takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) },
                        canAutoDownload = o.optBoolean("can_auto_download", false) &&
                            (isOpenLicense(license) || o.optString("permission_basis").equals("authorized", ignoreCase = true)),
                        notes = o.optString("notes")
                    )
                )
            }
        }
    }

    private fun validatePack(pack: ReferencePack) {
        fun requireUnique(values: List<String>, label: String) {
            require(values.size == values.toSet().size) { "Duplicate $label id in pack '${pack.id}'." }
        }
        val sourceIds = pack.sources.map { it.id }
        val regionIds = pack.regions.map { it.id }
        val markerIds = pack.markers.map { it.id }
        requireUnique(sourceIds, "source")
        requireUnique(regionIds, "region")
        requireUnique(markerIds, "marker")
        val sourceSet = sourceIds.toSet()
        val regions = pack.regions.associateBy { it.id }
        require(pack.physical.sourceIds.all { it in sourceSet }) { "Physical specification contains an unknown source id." }
        pack.regions.forEach { region ->
            require(region.left in 0.0..1.0 && region.top in 0.0..1.0 && region.right in 0.0..1.0 && region.bottom in 0.0..1.0) {
                "Region '${region.id}' has coordinates outside 0..1."
            }
            if (region.shape == RegionShape.RECT) require(region.left < region.right && region.top < region.bottom) {
                "Region '${region.id}' has an invalid rectangle."
            }
            if (region.shape == RegionShape.ANNULUS) require(region.innerRadius in 0.0..1.0 && region.outerRadius in 0.0..1.0 && region.innerRadius < region.outerRadius) {
                "Region '${region.id}' has an invalid annulus."
            }
        }
        pack.markers.forEach { marker ->
            require(marker.sourceIds.all { it in sourceSet }) { "Marker '${marker.id}' contains an unknown source id." }
            marker.regionId?.let { regionId ->
                val region = requireNotNull(regions[regionId]) { "Marker '${marker.id}' references unknown region '$regionId'." }
                marker.side?.let { require(it == region.side) { "Marker '${marker.id}' side does not match region '$regionId'." } }
            }
            if (marker.automatedTest == AutomatedTest.REGION_REFERENCE_MATCH) {
                require(marker.regionId != null) { "Automated region marker '${marker.id}' must specify region_id." }
            }
        }
        require((pack.physical.weightToleranceGrams ?: 0.0) >= 0.0) { "Negative weight tolerance." }
        require((pack.physical.diameterToleranceMm ?: 0.0) >= 0.0) { "Negative diameter tolerance." }
        require((pack.physical.thicknessToleranceMm ?: 0.0) >= 0.0) { "Negative thickness tolerance." }
    }

    private fun matches(pack: ReferencePack, metadata: CoinMetadata): Boolean {
        fun compatible(a: String, b: String): Boolean = a.isBlank() || b.isBlank() ||
            a.contains(b, ignoreCase = true) || b.contains(a, ignoreCase = true)
        return compatible(pack.country, metadata.country) &&
            compatible(pack.denomination, metadata.denomination) &&
            compatible(pack.year, metadata.year) &&
            compatible(pack.mintOrVariety, metadata.mint)
    }

    private fun trustFor(kind: ReferenceSourceKind): ReferenceTrust = when (kind) {
        ReferenceSourceKind.CERTIFIED_GENUINE, ReferenceSourceKind.GENUINE_AUCTION,
        ReferenceSourceKind.COUNTERFEIT_DIAGNOSTIC -> ReferenceTrust.CERTIFIED
        ReferenceSourceKind.CATALOG_REFERENCE -> ReferenceTrust.CATALOG
        ReferenceSourceKind.COMMUNITY_LEAD -> ReferenceTrust.COMMUNITY
        else -> ReferenceTrust.CURATED
    }

    private fun isOpenLicense(value: String?): Boolean {
        val v = value.orEmpty().lowercase()
        if (v.contains("noncommercial") || v.contains("no derivatives") || v.contains("no-derivatives") ||
            v.contains("-nc") || v.contains("-nd") || v.contains(" nc ") || v.contains(" nd ")) return false
        return v.contains("cc0") || v.contains("public domain") ||
            v.contains("cc by-sa") || v.contains("cc-by-sa") ||
            v.contains("cc by ") || v == "cc by" || v.contains("cc-by ") || v == "cc-by" ||
            v.contains("creative commons attribution")
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name.equals(value.trim().replace('-', '_').replace(' ', '_'), true) } ?: fallback

    private fun JSONObject.requireText(key: String): String = optString(key).takeIf { it.isNotBlank() }
        ?: error("Missing required field '$key'")

    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

    private fun JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        return buildList { for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let(::add) }
    }
}
