package com.coinforensics.app.reference

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.coinforensics.app.model.CoinIdentityCandidate
import com.coinforensics.app.model.CoinMetadata
import com.coinforensics.app.model.CoinSide
import com.coinforensics.app.model.DiagnosticScope
import com.coinforensics.app.model.DiscoveredReference
import com.coinforensics.app.model.DiscoveryProvider
import com.coinforensics.app.model.EvidenceAuthority
import com.coinforensics.app.model.OnlineReferenceConfig
import com.coinforensics.app.model.ReferenceDiscoveryResult
import com.coinforensics.app.model.ReferenceSourceKind
import com.coinforensics.app.model.ReferenceTrust
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.max

/**
 * Best-effort online research layer.
 *
 * Design goals:
 * 1) local analysis remains functional if every network provider fails;
 * 2) automatic image downloads occur only when reuse rights are explicitly compatible;
 * 3) source-only records remain useful for provenance/manual import;
 * 4) restricted grading-service sites are not scraped here.
 */
object OnlineReferenceService {
    private const val USER_AGENT = "CoinForensics/0.3 (personal numismatic research; Android)"

    fun discover(metadata: CoinMetadata, config: OnlineReferenceConfig): ReferenceDiscoveryResult {
        val query = queryFor(metadata)
        val identities = mutableListOf<CoinIdentityCandidate>()
        val references = mutableListOf<DiscoveredReference>()
        val packs = mutableListOf<com.coinforensics.app.model.ReferencePack>()
        val warnings = mutableListOf<String>()
        val providers = linkedSetOf<DiscoveryProvider>()

        runCatching { discoverCommons(metadata) }
            .onSuccess {
                providers += DiscoveryProvider.WIKIMEDIA_COMMONS
                references += it
            }
            .onFailure { warnings += "Wikimedia Commons discovery failed: ${it.message}" }

        if (config.numistaApiKey.isNotBlank()) {
            runCatching { discoverNumista(metadata, config.numistaApiKey.trim()) }
                .onSuccess { result ->
                    providers += DiscoveryProvider.NUMISTA
                    identities += result.first
                    references += result.second
                }
                .onFailure { warnings += "Numista API discovery failed: ${it.message}" }
        } else {
            warnings += "Numista API key not supplied; catalogue identity/specification lookup was skipped."
        }

        if (config.manifestUrl.isNotBlank()) {
            runCatching {
                val url = expandManifestUrl(config.manifestUrl.trim(), metadata)
                require(url.startsWith("https://")) { "Curated manifest URL must use HTTPS." }
                ReferenceManifestParser.parse(httpGet(url), metadata)
            }.onSuccess { result ->
                providers += DiscoveryProvider.CURATED_MANIFEST
                identities += result.identities
                references += result.references
                packs += result.packs
                warnings += result.warnings
            }.onFailure { warnings += "Curated manifest discovery failed: ${it.message}" }
        }

        return ReferenceDiscoveryResult(
            query = query,
            identityCandidates = identities.distinctBy { it.provider to it.id },
            references = references.distinctBy { it.id },
            curatedPacks = packs.distinctBy { it.id },
            warnings = warnings.distinct(),
            providersUsed = providers
        )
    }

    fun downloadReferenceBitmap(reference: DiscoveredReference, maxDimension: Int = 3000): Bitmap {
        require(reference.canAutoDownload) { "This source is not marked as automatically reusable." }
        val imageUrl = reference.imageUrl ?: error("No downloadable image URL is available.")
        require(imageUrl.startsWith("https://")) { "Reference images must use HTTPS." }
        val connection = open(imageUrl)
        connection.connectTimeout = 15_000
        connection.readTimeout = 25_000
        connection.connect()
        try {
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            require(connection.url.protocol.equals("https", ignoreCase = true)) { "Reference image redirected to a non-HTTPS URL." }
            val maximumBytes = 25 * 1024 * 1024
            val declaredBytes = connection.contentLengthLong
            require(declaredBytes <= 0 || declaredBytes <= maximumBytes) { "Reference image exceeds 25 MB safety limit." }
            val bytes = connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= maximumBytes) { "Reference image exceeds 25 MB safety limit." }
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            reference.sha256?.let { expected ->
                val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                require(actual.equals(expected, ignoreCase = true)) { "Reference image SHA-256 does not match the curated manifest." }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Downloaded file is not a valid raster image." }
            var sample = 1
            var largest = max(bounds.outWidth, bounds.outHeight)
            while (largest / sample > maxDimension * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ?: error("Downloaded file is not a decodable bitmap")
            val currentLargest = max(decoded.width, decoded.height)
            if (currentLargest <= maxDimension) return decoded
            val scale = maxDimension.toDouble() / currentLargest
            return Bitmap.createScaledBitmap(
                decoded,
                maxOf(1, (decoded.width * scale).toInt()),
                maxOf(1, (decoded.height * scale).toInt()),
                true
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun discoverCommons(metadata: CoinMetadata): List<DiscoveredReference> {
        val base = queryFor(metadata)
        val queries = listOf(
            base,
            "$base coin obverse reverse",
            "$base counterfeit fake replica"
        )
        val out = mutableListOf<DiscoveredReference>()
        queries.forEach { q ->
            val params = linkedMapOf(
                "action" to "query",
                "generator" to "search",
                "gsrsearch" to q,
                "gsrnamespace" to "6",
                "gsrlimit" to "8",
                "prop" to "imageinfo",
                "iiprop" to "url|extmetadata",
                "iiurlwidth" to "1600",
                "iiextmetadatafilter" to "LicenseShortName|LicenseUrl|Artist|Credit|ImageDescription|AttributionRequired|Copyrighted",
                "format" to "json",
                "formatversion" to "2",
                "origin" to "*"
            )
            val url = "https://commons.wikimedia.org/w/api.php?" + params.entries.joinToString("&") {
                encode(it.key) + "=" + encode(it.value)
            }
            val root = JSONObject(httpGet(url))
            val pages = root.optJSONObject("query")?.optJSONArray("pages") ?: JSONArray()
            for (i in 0 until pages.length()) {
                val page = pages.optJSONObject(i) ?: continue
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val meta = info.optJSONObject("extmetadata")
                val title = page.optString("title").removePrefix("File:")
                val description = htmlToText(metaValue(meta, "ImageDescription"))
                val combined = "$title $description"
                val license = htmlToText(metaValue(meta, "LicenseShortName")).takeIf { it.isNotBlank() }
                val licenseUrl = metaValue(meta, "LicenseUrl").takeIf { it.isNotBlank() }
                val artist = htmlToText(metaValue(meta, "Artist")).takeIf { it.isNotBlank() }
                val sourcePage = page.optString("canonicalurl").ifBlank {
                    "https://commons.wikimedia.org/wiki/" + encodePath(page.optString("title"))
                }
                val isCounterfeit = looksCounterfeit(combined)
                val kind = if (isCounterfeit) ReferenceSourceKind.COMMUNITY_LEAD else ReferenceSourceKind.CATALOG_REFERENCE
                val scope = if (metadata.year.isNotBlank() && combined.contains(metadata.year, ignoreCase = true)) {
                    DiagnosticScope.EXACT_DATE_VARIETY
                } else DiagnosticScope.SERIES_WIDE
                out += DiscoveredReference(
                    id = "commons-${page.optLong("pageid", i.toLong())}",
                    provider = DiscoveryProvider.WIKIMEDIA_COMMONS,
                    title = title,
                    sourcePageUrl = sourcePage,
                    imageUrl = info.optString("url").takeIf { it.startsWith("https://") },
                    thumbnailUrl = info.optString("thumburl").takeIf { it.startsWith("https://") },
                    side = inferSide(combined),
                    kind = kind,
                    scope = scope,
                    authority = EvidenceAuthority.WIKIMEDIA_COMMONS,
                    trust = if (isCounterfeit) ReferenceTrust.COMMUNITY else ReferenceTrust.CATALOG,
                    licenseName = license,
                    licenseUrl = licenseUrl,
                    creator = artist,
                    rightsBasis = license?.let { "Wikimedia Commons file license: $it" },
                    canAutoDownload = isOpenLicense(license),
                    notes = buildString {
                        append(if (isCounterfeit) "Open-media counterfeit/search lead; verify the file description before treating it as a documented fake. " else "Open-media catalogue/reference candidate; not equivalent to third-party certification. ")
                        if (description.isNotBlank()) append(description.take(280))
                    }.trim()
                )
            }
        }
        return out.distinctBy { it.id }
            .sortedWith(compareByDescending<DiscoveredReference> { it.canAutoDownload }.thenBy { it.kind.ordinal })
            .take(24)
    }

    private fun discoverNumista(metadata: CoinMetadata, apiKey: String): Pair<List<CoinIdentityCandidate>, List<DiscoveredReference>> {
        val searchParams = mutableListOf("q=${encode(queryFor(metadata))}")
        metadata.year.trim().toIntOrNull()?.let { searchParams += "year=$it" }
        searchParams += "lang=en"
        val searchUrl = "https://api.numista.com/v3/types?" + searchParams.joinToString("&")
        val search = JSONObject(httpGet(searchUrl, mapOf("Numista-API-Key" to apiKey)))
        val types = search.optJSONArray("types") ?: JSONArray()
        val identities = mutableListOf<CoinIdentityCandidate>()
        val refs = mutableListOf<DiscoveredReference>()

        val maxTypes = minOf(4, types.length())
        for (i in 0 until maxTypes) {
            val summary = types.optJSONObject(i) ?: continue
            val id = summary.optLong("id", -1L).takeIf { it > 0 } ?: continue
            val detail = JSONObject(httpGet("https://api.numista.com/v3/types/$id?lang=en", mapOf("Numista-API-Key" to apiKey)))
            val issues = runCatching {
                JSONArray(httpGet("https://api.numista.com/v3/types/$id/issues?lang=en", mapOf("Numista-API-Key" to apiKey)))
            }.getOrDefault(JSONArray())
            val exactIssue = findIssue(issues, metadata)
            if (metadata.year.isNotBlank() && issues.length() > 0 && exactIssue == null) continue

            val title = detail.optString("title").ifBlank { summary.optString("title", "Numista N#$id") }
            val typeUrl = detail.optString("url").ifBlank { "https://en.numista.com/catalogue/pieces$id.html" }
            val issueId = exactIssue?.optLong("id", -1L)?.takeIf { it > 0 }
            identities += CoinIdentityCandidate(
                id = if (issueId != null) "$id:$issueId" else id.toString(),
                provider = DiscoveryProvider.NUMISTA,
                title = title,
                sourceUrl = typeUrl,
                catalogReferences = listOf("N#$id") + collectCatalogueRefs(detail),
                weightGrams = detail.optDoubleOrNull("weight"),
                diameterMm = detail.optDoubleOrNull("size") ?: detail.optDoubleOrNull("diameter"),
                thicknessMm = detail.optDoubleOrNull("thickness"),
                matchNote = if (exactIssue != null) {
                    "Numista type N#$id contains requested issue ${metadata.year}${metadata.mint.takeIf { it.isNotBlank() }?.let { " / $it" }.orEmpty()}."
                } else "Numista type N#$id matched the metadata search."
            )

            listOf(
                "obverse" to CoinSide.OBVERSE,
                "reverse" to CoinSide.REVERSE,
                "edge" to CoinSide.EDGE
            ).forEach { (key, side) ->
                val sideObj = detail.optJSONObject(key) ?: return@forEach
                val image = findImageCandidate(sideObj) ?: return@forEach
                val license = findTextByKey(sideObj, setOf("license", "licence", "license_name", "licence_name"))
                val creator = findTextByKey(sideObj, setOf("author", "artist", "creator", "copyright", "credit"))
                refs += DiscoveredReference(
                    id = "numista-$id-${side.name.lowercase()}",
                    provider = DiscoveryProvider.NUMISTA,
                    title = "$title — ${side.label}",
                    sourcePageUrl = typeUrl,
                    imageUrl = image,
                    side = side,
                    kind = ReferenceSourceKind.CATALOG_REFERENCE,
                    scope = DiagnosticScope.SERIES_WIDE,
                    authority = EvidenceAuthority.NUMISTA,
                    trust = ReferenceTrust.CATALOG,
                    licenseName = license,
                    creator = creator,
                    rightsBasis = license?.let { "Numista image license metadata: $it" },
                    canAutoDownload = isOpenLicense(license),
                    notes = if (isOpenLicense(license)) {
                        "Numista type-level catalogue image with an API-exposed open/reusable license. It is supporting series/type evidence, not an exact-date certified control unless a curated issue manifest separately establishes that provenance."
                    } else {
                        "Numista catalogue image found, but automatic reuse is disabled because an open image license was not confidently identified. Open the source and import manually only when permitted."
                    }
                )
            }
        }
        return identities to refs
    }

    private fun findIssue(issues: JSONArray, metadata: CoinMetadata): JSONObject? {
        val year = metadata.year.trim().toIntOrNull() ?: return issues.optJSONObject(0)
        val mint = metadata.mint.trim()
        for (i in 0 until issues.length()) {
            val o = issues.optJSONObject(i) ?: continue
            val issueYear = o.optInt("gregorian_year", o.optInt("year", Int.MIN_VALUE))
            if (issueYear != year) continue
            if (mint.isBlank()) return o
            val text = o.toString()
            if (text.contains(mint, ignoreCase = true)) return o
        }
        return null
    }

    private fun collectCatalogueRefs(detail: JSONObject): List<String> {
        val out = mutableListOf<String>()
        val arr = detail.optJSONArray("references") ?: detail.optJSONArray("catalogues") ?: return emptyList()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val code = o.optString("code")
            val number = o.optString("number")
            val text = listOf(code, number).filter { it.isNotBlank() }.joinToString(" ")
            if (text.isNotBlank()) out += text
        }
        return out.distinct()
    }

    private fun findImageCandidate(root: JSONObject): String? {
        fun walk(value: Any?, keyHint: String = "", depth: Int = 0): String? {
            if (depth > 7 || value == null || value == JSONObject.NULL) return null
            return when (value) {
                is JSONObject -> {
                    val preferred = listOf("image_url", "picture_url", "url", "image", "picture")
                    preferred.forEach { key ->
                        if (value.has(key)) walk(value.opt(key), key, depth + 1)?.let { return it }
                    }
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        walk(value.opt(k), k, depth + 1)?.let { return it }
                    }
                    null
                }
                is JSONArray -> {
                    for (i in 0 until value.length()) walk(value.opt(i), keyHint, depth + 1)?.let { return it }
                    null
                }
                is String -> if (looksImageUrl(value) && (keyHint.contains("image", true) || keyHint.contains("picture", true) || keyHint.contains("url", true))) value else null
                else -> null
            }
        }
        return walk(root)
    }

    private fun findTextByKey(root: JSONObject, keysWanted: Set<String>): String? {
        fun walk(value: Any?, depth: Int = 0): String? {
            if (depth > 6 || value == null || value == JSONObject.NULL) return null
            return when (value) {
                is JSONObject -> {
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val child = value.opt(key)
                        if (keysWanted.any { it.equals(key, true) }) {
                            when (child) {
                                is String -> if (child.isNotBlank()) return htmlToText(child)
                                is JSONObject -> child.optString("name").takeIf { it.isNotBlank() }?.let { return htmlToText(it) }
                            }
                        }
                        walk(child, depth + 1)?.let { return it }
                    }
                    null
                }
                is JSONArray -> {
                    for (i in 0 until value.length()) walk(value.opt(i), depth + 1)?.let { return it }
                    null
                }
                else -> null
            }
        }
        return walk(root)
    }

    private fun queryFor(metadata: CoinMetadata): String = listOf(
        metadata.country,
        metadata.year,
        metadata.denomination,
        metadata.mint
    ).map { it.trim() }.filter { it.isNotBlank() }.joinToString(" ").ifBlank { "coin" }

    private fun inferSide(text: String): CoinSide? = when {
        Regex("\\b(obverse|avers|front)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> CoinSide.OBVERSE
        Regex("\\b(reverse|revers|back)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> CoinSide.REVERSE
        Regex("\\b(edge|reed|reeding|milling)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> CoinSide.EDGE
        else -> null
    }

    private fun looksCounterfeit(text: String): Boolean = Regex(
        "\\b(counterfeit|fake|forgery|forged|replica|reproduction|cast copy)\\b",
        RegexOption.IGNORE_CASE
    ).containsMatchIn(text)

    private fun looksImageUrl(value: String): Boolean {
        val v = value.lowercase()
        return v.startsWith("https://") && (
            v.contains(".jpg") || v.contains(".jpeg") || v.contains(".png") || v.contains(".webp") ||
                v.contains("image") || v.contains("picture") || v.contains("photo")
            )
    }

    private fun isOpenLicense(value: String?): Boolean {
        val v = value.orEmpty().lowercase().replace('_', '-').replace(Regex("\\s+"), " ").trim()
        if (v.contains("noncommercial") || v.contains("no derivatives") || v.contains("no-derivatives") ||
            v.contains("-nc") || v.contains("-nd") || v.contains(" nc ") || v.contains(" nd ")) return false
        return v.contains("cc0") || v.contains("public domain") ||
            v.contains("cc by-sa") || v.contains("cc-by-sa") ||
            v.contains("cc by ") || v == "cc by" || v.contains("cc-by ") || v == "cc-by" ||
            v.contains("creative commons attribution")
    }

    private fun metaValue(meta: JSONObject?, key: String): String = meta?.optJSONObject(key)?.optString("value").orEmpty()

    private fun htmlToText(input: String): String = input
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace(Regex("\\s+"), " ")
        .trim()


    private fun expandManifestUrl(template: String, metadata: CoinMetadata): String {
        val query = listOf(metadata.country, metadata.denomination, metadata.year, metadata.mint)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val values = mapOf(
            "{country}" to metadata.country,
            "{denomination}" to metadata.denomination,
            "{year}" to metadata.year,
            "{mint}" to metadata.mint,
            "{query}" to query
        )
        return values.entries.fold(template) { url, (token, value) ->
            url.replace(token, encode(value))
        }
    }

    private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String {
        val c = open(url)
        c.connectTimeout = 12_000
        c.readTimeout = 20_000
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.connect()
        try {
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.use { input ->
                val reader = input.bufferedReader(StandardCharsets.UTF_8)
                val out = StringBuilder()
                val chars = CharArray(16 * 1024)
                val maxChars = 5 * 1024 * 1024
                while (true) {
                    val count = reader.read(chars)
                    if (count < 0) break
                    require(out.length + count <= maxChars) { "HTTP response exceeds 5 MiB safety limit." }
                    out.append(chars, 0, count)
                }
                out.toString()
            }.orEmpty()
            require(code in 200..299) { "HTTP $code ${body.take(180)}" }
            return body
        } finally {
            c.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", USER_AGENT)
        setRequestProperty("Accept", "application/json,image/*;q=0.8,*/*;q=0.5")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    private fun encodePath(value: String): String = value.split('/').joinToString("/") { encode(it).replace("+", "_") }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null
}
