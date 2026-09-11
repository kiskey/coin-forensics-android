package com.coinforensics.app.model

import android.graphics.Bitmap

enum class AppTab(val label: String) {
    CASE("Case"), FORENSICS("Forensics"), REFERENCES("Refs"), COMPARE("Compare"), REPORT("Report")
}

enum class ImageSlot(val label: String, val shortLabel: String) {
    OBVERSE("Coin obverse", "Obverse"),
    REVERSE("Coin reverse", "Reverse"),
    EDGE("Edge / reeding", "Edge"),
    REFERENCE_OBVERSE("Known-genuine obverse", "Ref O"),
    REFERENCE_REVERSE("Known-genuine reverse", "Ref R"),
    UV("Real UV capture", "UV"),
    IR("Real IR capture", "IR")
}

enum class ForensicMode(val label: String, val description: String) {
    ORIGINAL("Original", "Unmodified source pixels"),
    GRAYSCALE("Luminance", "Perceptual grayscale luminance"),
    RED("Red channel", "Red-channel intensity only"),
    GREEN("Green channel", "Green-channel intensity only"),
    BLUE("Blue channel", "Blue-channel intensity only"),
    LOCAL_CONTRAST("Local contrast", "Enhances local relief and field variation"),
    HIGH_PASS("High pass", "Suppresses broad lighting and emphasizes fine detail"),
    SOBEL("Edges", "Gradient magnitude map for device and lettering boundaries"),
    LAPLACIAN("Microtexture", "Second-derivative map for fine surface texture"),
    TEXTURE_VARIANCE("Texture variance", "Maps local surface variance"),
    SPECULAR("Specular", "Highlights likely glare / reflective hotspots"),
    RELIEF("Pseudo relief", "Gradient-derived pseudo topographic shading"),
    FALSE_COLOR("False color", "False-color luminance visualization")
}

data class CoinMetadata(
    val country: String = "",
    val denomination: String = "",
    val year: String = "",
    val mint: String = "",
    val weightGrams: String = "",
    val diameterMm: String = "",
    val thicknessMm: String = "",
    val notes: String = ""
)

data class ImageQualityMetrics(
    val width: Int,
    val height: Int,
    val resolutionScore: Double,
    val sharpnessScore: Double,
    val contrastScore: Double,
    val highlightClipPercent: Double,
    val shadowClipPercent: Double,
    val glarePercent: Double,
    val overallScore: Double
)

data class SurfaceMetrics(
    val microtextureEnergy: Double,
    val localVariance: Double,
    val edgeDensityPercent: Double,
    val rimCircularityScore: Double?,
    val rimPatternRegularity: Double?
)

data class EvidenceFinding(
    val title: String,
    val detail: String,
    val severity: Severity
)

enum class Severity { INFO, GOOD, CAUTION, WARNING }

data class ForensicAnalysis(
    val quality: ImageQualityMetrics,
    val surface: SurfaceMetrics,
    val findings: List<EvidenceFinding>
)

data class ComparisonResult(
    val consistencyScore: Double,
    val luminanceCorrelation: Double,
    val edgeCorrelation: Double,
    val bestRotationDegrees: Float,
    val bestScale: Float,
    val bestOffsetX: Int,
    val bestOffsetY: Int,
    val differenceBitmap: Bitmap,
    val interpretation: String
)

data class LoadedImage(
    val slot: ImageSlot,
    val bitmap: Bitmap,
    val sourceLabel: String,
    val provenanceSourceId: String? = null
)

data class ReferenceSampleImage(
    val id: String,
    val side: CoinSide,
    val bitmap: Bitmap,
    val sourceLabel: String,
    val provenanceSourceId: String,
    val autoAcquired: Boolean = false,
    val licenseSummary: String? = null
)

enum class ReferenceSourceKind {
    CERTIFIED_GENUINE,
    GENUINE_AUCTION,
    COUNTERFEIT_DIAGNOSTIC,
    AUTHENTICATION_GUIDANCE,
    SPECIFICATION,
    COMMUNITY_LEAD,
    CATALOG_REFERENCE
}

enum class EvidenceAuthority(val label: String) {
    PCGS("PCGS"),
    NGC("NGC"),
    STACKS_BOWERS("Stack's Bowers"),
    ANA("ANA"),
    NUMISTA("Numista"),
    GREATCOLLECTIONS("GreatCollections"),
    APMEX("APMEX"),
    WIKIMEDIA_COMMONS("Wikimedia Commons"),
    REMOTE_CATALOG("Curated remote catalog"),
    OTHER("Other")
}

enum class DiagnosticScope(val label: String) {
    EXACT_DATE_VARIETY("Exact date / variety"),
    SAME_TYPE_OTHER_DATE("Same type, other date"),
    SERIES_WIDE("Series-wide"),
    GENERAL_AUTHENTICATION("General authentication")
}

enum class CoinSide(val label: String) { OBVERSE("Obverse"), REVERSE("Reverse"), EDGE("Edge") }

enum class RegionShape { RECT, ANNULUS, FULL_COIN }

data class NormalizedRegion(
    val id: String,
    val label: String,
    val side: CoinSide,
    val shape: RegionShape,
    val left: Double = 0.0,
    val top: Double = 0.0,
    val right: Double = 1.0,
    val bottom: Double = 1.0,
    val innerRadius: Double = 0.0,
    val outerRadius: Double = 1.0,
    val description: String = ""
)

data class ReferenceSource(
    val id: String,
    val title: String,
    val authority: EvidenceAuthority,
    val kind: ReferenceSourceKind,
    val scope: DiagnosticScope,
    val url: String,
    val certificationNumber: String? = null,
    val gradeOrStatus: String? = null,
    val notes: String = "",
    val imageUsageNote: String = "External source; images are not bundled."
)

enum class DiagnosticCategory {
    WEIGHT,
    DATE_GEOMETRY,
    LETTERING,
    RIM_DENTICLES,
    RELIEF_DETAIL,
    SURFACE_TEXTURE,
    TRANSFER_DIE,
    LUSTER_COLOR,
    EDGE,
    VARIETY
}

enum class AutomatedTest { REGION_REFERENCE_MATCH, WEIGHT_TOLERANCE, DIAMETER_TOLERANCE, SURFACE_REFERENCE_DELTA, MANUAL_ONLY }

data class DiagnosticMarker(
    val id: String,
    val title: String,
    val category: DiagnosticCategory,
    val side: CoinSide? = null,
    val regionId: String? = null,
    val scope: DiagnosticScope,
    val automatedTest: AutomatedTest,
    val description: String,
    val sourceIds: List<String>
)

data class PhysicalSpecification(
    val expectedWeightGrams: Double? = null,
    val weightToleranceGrams: Double? = null,
    val expectedDiameterMm: Double? = null,
    val diameterToleranceMm: Double? = null,
    val expectedThicknessMm: Double? = null,
    val thicknessToleranceMm: Double? = null,
    val compositionNote: String? = null,
    val sourceIds: List<String> = emptyList()
)

data class ReferencePack(
    val id: String,
    val displayName: String,
    val country: String,
    val denomination: String,
    val year: String,
    val mintOrVariety: String,
    val catalogReferences: List<String>,
    val physical: PhysicalSpecification,
    val regions: List<NormalizedRegion>,
    val markers: List<DiagnosticMarker>,
    val sources: List<ReferenceSource>,
    val notes: String
)

enum class DiagnosticStatus { CONSISTENT, CAUTION, INCONSISTENT, NOT_AVAILABLE, MANUAL_REVIEW }

data class RegionComparison(
    val regionId: String,
    val label: String,
    val side: CoinSide,
    val consistencyScore: Double,
    val luminanceScore: Double,
    val edgeScore: Double,
    val interpretation: String
)

data class DiagnosticCheck(
    val markerId: String,
    val title: String,
    val status: DiagnosticStatus,
    val score: Double? = null,
    val detail: String,
    val sourceIds: List<String> = emptyList()
)

data class CounterfeitRegionComparison(
    val sourceId: String,
    val sourceTitle: String,
    val sourceScope: DiagnosticScope,
    val regionId: String,
    val regionLabel: String,
    val side: CoinSide,
    val counterfeitSimilarityScore: Double,
    val genuineConsensusScore: Double?,
    val deltaVsGenuine: Double?,
    val interpretation: String
)

data class ReferenceAssessment(
    val packId: String,
    val packName: String,
    val evidenceConsistencyScore: Double?,
    val regionComparisons: List<RegionComparison>,
    val counterfeitComparisons: List<CounterfeitRegionComparison> = emptyList(),
    val checks: List<DiagnosticCheck>,
    val sourceIdsUsed: Set<String>,
    val interpretation: String,
    val provenanceStrengthScore: Double? = null,
    val provenanceSummary: String = ""
)

data class AppUiState(
    val tab: AppTab = AppTab.CASE,
    val metadata: CoinMetadata = CoinMetadata(),
    val images: Map<ImageSlot, LoadedImage> = emptyMap(),
    val selectedForensicSlot: ImageSlot = ImageSlot.OBVERSE,
    val selectedCompareSlot: ImageSlot = ImageSlot.OBVERSE,
    val forensicMode: ForensicMode = ForensicMode.ORIGINAL,
    val filterIntensity: Float = 1.0f,
    val processedBitmap: Bitmap? = null,
    val analysisBySlot: Map<ImageSlot, ForensicAnalysis> = emptyMap(),
    val comparisonBySlot: Map<ImageSlot, ComparisonResult> = emptyMap(),
    val referenceSamples: List<ReferenceSampleImage> = emptyList(),
    val referencePacks: List<ReferencePack> = emptyList(),
    val selectedReferencePackId: String? = null,
    val referenceAssessment: ReferenceAssessment? = null,
    val onlineReferenceConfig: OnlineReferenceConfig = OnlineReferenceConfig(),
    val discoveryResult: ReferenceDiscoveryResult? = null,
    val selectedIdentityCandidateId: String? = null,
    val isWorking: Boolean = false,
    val message: String? = null
)
