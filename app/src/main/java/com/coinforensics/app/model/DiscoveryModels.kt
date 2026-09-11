package com.coinforensics.app.model

/**
 * Online reference discovery is deliberately separated from the forensic engine.
 * Discovery can fail or be unavailable without disabling local analysis.
 */
data class OnlineReferenceConfig(
    val numistaApiKey: String = "",
    val manifestUrl: String = ""
)

enum class DiscoveryProvider(val label: String) {
    WIKIMEDIA_COMMONS("Wikimedia Commons"),
    NUMISTA("Numista API"),
    CURATED_MANIFEST("Curated manifest")
}

enum class ReferenceTrust(val label: String) {
    CERTIFIED("Certified / professionally authenticated"),
    CURATED("Curated specialist source"),
    CATALOG("Catalogue / open-reference image"),
    COMMUNITY("Community diagnostic lead"),
    UNKNOWN("Unverified")
}

data class CoinIdentityCandidate(
    val id: String,
    val provider: DiscoveryProvider,
    val title: String,
    val sourceUrl: String,
    val catalogReferences: List<String> = emptyList(),
    val weightGrams: Double? = null,
    val diameterMm: Double? = null,
    val thicknessMm: Double? = null,
    val matchNote: String = ""
)

data class DiscoveredReference(
    val id: String,
    val provider: DiscoveryProvider,
    val title: String,
    val sourcePageUrl: String,
    val imageUrl: String? = null,
    val thumbnailUrl: String? = null,
    val side: CoinSide? = null,
    val kind: ReferenceSourceKind,
    val scope: DiagnosticScope,
    val authority: EvidenceAuthority,
    val trust: ReferenceTrust,
    val licenseName: String? = null,
    val licenseUrl: String? = null,
    val creator: String? = null,
    val rightsBasis: String? = null,
    val sha256: String? = null,
    val canAutoDownload: Boolean = false,
    val notes: String = ""
)

data class ReferenceDiscoveryResult(
    val query: String,
    val identityCandidates: List<CoinIdentityCandidate> = emptyList(),
    val references: List<DiscoveredReference> = emptyList(),
    val curatedPacks: List<ReferencePack> = emptyList(),
    val warnings: List<String> = emptyList(),
    val providersUsed: Set<DiscoveryProvider> = emptySet()
)
