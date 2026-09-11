package com.coinforensics.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.coinforensics.app.imaging.ImageIo
import com.coinforensics.app.model.AppTab
import com.coinforensics.app.model.AppUiState
import com.coinforensics.app.model.DiagnosticStatus
import com.coinforensics.app.model.CoinMetadata
import com.coinforensics.app.model.CoinSide
import com.coinforensics.app.model.ForensicAnalysis
import com.coinforensics.app.model.ForensicMode
import com.coinforensics.app.model.ImageSlot
import com.coinforensics.app.model.OnlineReferenceConfig
import com.coinforensics.app.model.ReferenceSource
import com.coinforensics.app.model.ReferenceSourceKind
import com.coinforensics.app.model.Severity
import com.coinforensics.app.report.ReportExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinForensicsApp(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var pendingImportSlot by remember { mutableStateOf<ImageSlot?>(null) }
    var pendingProvenanceSourceId by remember { mutableStateOf<String?>(null) }
    var pendingReferenceSide by remember { mutableStateOf<CoinSide?>(null) }
    var pendingCaptureSlot by remember { mutableStateOf<ImageSlot?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val slot = pendingImportSlot
        val refSide = pendingReferenceSide
        val sourceId = pendingProvenanceSourceId
        if (uri != null && refSide != null && sourceId != null) {
            vm.importReferenceSample(refSide, uri, sourceId)
        } else if (uri != null && slot != null) {
            vm.importImage(slot, uri, sourceId)
        }
        pendingImportSlot = null
        pendingReferenceSide = null
        pendingProvenanceSourceId = null
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val slot = pendingCaptureSlot
        val uri = pendingCaptureUri
        if (success && slot != null && uri != null) vm.importImage(slot, uri)
        pendingCaptureSlot = null
        pendingCaptureUri = null
    }

    val onImport: (ImageSlot) -> Unit = { slot ->
        pendingImportSlot = slot
        pendingReferenceSide = null
        pendingProvenanceSourceId = null
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val onReferenceImport: (CoinSide, String) -> Unit = { side, sourceId ->
        pendingImportSlot = null
        pendingReferenceSide = side
        pendingProvenanceSourceId = sourceId
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val onCapture: (ImageSlot) -> Unit = { slot ->
        val uri = ImageIo.createCaptureUri(context)
        pendingCaptureSlot = slot
        pendingCaptureUri = uri
        camera.launch(uri)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Coin Forensics", fontWeight = FontWeight.SemiBold)
                        Text("Local forensic engine + provenance research", style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.tab == tab,
                        onClick = { vm.setTab(tab) },
                        icon = { Text(tab.label.take(1), fontWeight = FontWeight.Bold) },
                        label = { Text(tab.label) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.tab) {
                AppTab.CASE -> CaseScreen(state, vm::updateMetadata, onImport, onCapture)
                AppTab.FORENSICS -> ForensicsScreen(state, vm)
                AppTab.REFERENCES -> ReferenceScreen(state, vm, onReferenceImport)
                AppTab.COMPARE -> CompareScreen(state, vm)
                AppTab.REPORT -> ReportScreen(state)
            }
            if (state.isWorking) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun CaseScreen(
    state: AppUiState,
    onMetadata: (CoinMetadata) -> Unit,
    onImport: (ImageSlot) -> Unit,
    onCapture: (ImageSlot) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("Coin case")
        Text(
            "Record the physical measurements separately from the image evidence. Weight and dimensions are not inferred from a photograph.",
            style = MaterialTheme.typography.bodySmall
        )

        MetadataFields(state.metadata, onMetadata)
        HorizontalDivider()
        SectionTitle("Evidence images")
        Text(
            "Best results: coin centered, camera square to the surface, high resolution, neutral background, and multiple lighting directions.",
            style = MaterialTheme.typography.bodySmall
        )

        ImageSlot.entries.forEach { slot ->
            EvidenceImageCard(
                slot = slot,
                bitmap = state.images[slot]?.bitmap,
                source = state.images[slot]?.sourceLabel,
                onImport = { onImport(slot) },
                onCapture = { onCapture(slot) }
            )
        }

        InfoCard(
            "UV / IR integrity",
            "The UV and IR slots are only for photographs actually captured with the corresponding illumination/sensor. The app never converts a visible RGB image into fake invisible-light evidence."
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MetadataFields(metadata: CoinMetadata, onChange: (CoinMetadata) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = metadata.country,
                onValueChange = { onChange(metadata.copy(country = it)) },
                label = { Text("Country / issuer") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = metadata.denomination,
                onValueChange = { onChange(metadata.copy(denomination = it)) },
                label = { Text("Denomination") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = metadata.year,
                onValueChange = { onChange(metadata.copy(year = it)) },
                label = { Text("Year") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = metadata.mint,
                onValueChange = { onChange(metadata.copy(mint = it)) },
                label = { Text("Mint / variety") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = metadata.weightGrams,
                onValueChange = { onChange(metadata.copy(weightGrams = it)) },
                label = { Text("Weight g") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = metadata.diameterMm,
                onValueChange = { onChange(metadata.copy(diameterMm = it)) },
                label = { Text("Diameter mm") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = metadata.thicknessMm,
                onValueChange = { onChange(metadata.copy(thicknessMm = it)) },
                label = { Text("Thickness mm") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        OutlinedTextField(
            value = metadata.notes,
            onValueChange = { onChange(metadata.copy(notes = it)) },
            label = { Text("Case notes") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
    }
}

@Composable
private fun EvidenceImageCard(
    slot: ImageSlot,
    bitmap: Bitmap?,
    source: String?,
    onImport: () -> Unit,
    onCapture: () -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(slot.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        source ?: if (slot == ImageSlot.UV || slot == ImageSlot.IR) "Use only a real ${slot.shortLabel} capture" else "No image loaded",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = slot.label,
                        modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImport) { Text("Import") }
                OutlinedButton(onClick = onCapture) { Text("Camera") }
            }
        }
    }
}

@Composable
private fun ForensicsScreen(state: AppUiState, vm: AppViewModel) {
    val usable = listOf(ImageSlot.OBVERSE, ImageSlot.REVERSE, ImageSlot.EDGE, ImageSlot.UV, ImageSlot.IR)
        .filter { state.images.containsKey(it) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("Forensic viewer")
        if (usable.isEmpty()) {
            InfoCard("No evidence image", "Load an obverse, reverse, edge, UV, or IR image in the Case tab first.")
            return@Column
        }

        Text("Evidence source", style = MaterialTheme.typography.labelLarge)
        ScrollableChips {
            usable.forEach { slot ->
                FilterChip(
                    selected = state.selectedForensicSlot == slot,
                    onClick = { vm.setForensicSlot(slot) },
                    label = { Text(slot.shortLabel) }
                )
            }
        }

        Text("View", style = MaterialTheme.typography.labelLarge)
        ScrollableChips {
            ForensicMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.forensicMode == mode,
                    onClick = { vm.setModeAndProcess(mode) },
                    label = { Text(mode.label) }
                )
            }
        }
        Text(state.forensicMode.description, style = MaterialTheme.typography.bodySmall)

        ZoomableImage(state.processedBitmap ?: state.images[state.selectedForensicSlot]?.bitmap)

        if (state.forensicMode !in listOf(ForensicMode.ORIGINAL, ForensicMode.GRAYSCALE, ForensicMode.RED, ForensicMode.GREEN, ForensicMode.BLUE)) {
            Text("Enhancement intensity: ${"%.2f".format(Locale.US, state.filterIntensity)}×", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = state.filterIntensity,
                onValueChange = vm::setFilterIntensity,
                onValueChangeFinished = vm::processSelected,
                valueRange = 0.5f..2.5f
            )
        }

        Button(onClick = vm::analyzeSelected, enabled = !state.isWorking) { Text("Analyze evidence") }

        state.analysisBySlot[state.selectedForensicSlot]?.let { AnalysisCard(it) }

        InfoCard(
            "What this scan can and cannot say",
            "These transforms expose pixel evidence and measurable geometry. They can flag areas for inspection, but they cannot establish metal composition, recover detail absent from the photo, or substitute for an in-hand certification."
        )
    }
}

@Composable
private fun AnalysisCard(analysis: ForensicAnalysis) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Measurement summary", fontWeight = FontWeight.Bold)
            Metric("Image quality", analysis.quality.overallScore, "/100")
            Metric("Sharpness", analysis.quality.sharpnessScore, "/100")
            Metric("Contrast", analysis.quality.contrastScore, "/100")
            Metric("Glare", analysis.quality.glarePercent, "%")
            Metric("Edge density", analysis.surface.edgeDensityPercent, "%")
            analysis.surface.rimCircularityScore?.let { Metric("Rim circularity", it, "/100") }
            analysis.surface.rimPatternRegularity?.let { Metric("Rim pattern", it, "/100") }
            HorizontalDivider()
            analysis.findings.forEach { finding ->
                val marker = when (finding.severity) {
                    Severity.GOOD -> "✓"
                    Severity.CAUTION -> "△"
                    Severity.WARNING -> "!"
                    Severity.INFO -> "•"
                }
                Text("$marker ${finding.title}", fontWeight = FontWeight.SemiBold)
                Text(finding.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ReferenceScreen(
    state: AppUiState,
    vm: AppViewModel,
    onReferenceImport: (CoinSide, String) -> Unit
) {
    val context = LocalContext.current
    val pack = state.referencePacks.firstOrNull { it.id == state.selectedReferencePackId }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("Exact coin / die references")
        Text(
            "Reference packs separate exact-date controls from same-type counterfeit warnings. The app never promotes a diagnostic from another date into an exact die marker unless the source documents it.",
            style = MaterialTheme.typography.bodySmall
        )

        OnlineDiscoverySection(state, vm)

        if (state.referencePacks.isEmpty()) {
            InfoCard("No packs installed", "Install or build a reference pack before running die-level checks.")
            return@Column
        }

        Text("Reference pack", style = MaterialTheme.typography.labelLarge)
        ScrollableChips {
            state.referencePacks.forEach { item ->
                FilterChip(
                    selected = item.id == state.selectedReferencePackId,
                    onClick = { vm.selectReferencePack(item.id) },
                    label = { Text(item.year + " " + item.denomination) }
                )
            }
        }

        if (pack == null) return@Column

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(pack.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(pack.mintOrVariety, style = MaterialTheme.typography.bodySmall)
                Text(pack.catalogReferences.joinToString(" • "), style = MaterialTheme.typography.labelSmall)
                pack.physical.expectedWeightGrams?.let {
                    Text("Documented weight: ${"%.2f".format(Locale.US, it)} g", style = MaterialTheme.typography.bodySmall)
                }
                pack.physical.expectedDiameterMm?.let {
                    Text("Documented diameter: ${"%.2f".format(Locale.US, it)} mm", style = MaterialTheme.typography.bodySmall)
                }
                pack.physical.compositionNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(pack.notes, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = vm::applyReferencePackMetadata) { Text("Use pack metadata for case") }
            }
        }

        SectionTitle("Genuine / catalogue controls")
        Text(
            "Certified controls are preferred. Catalogue/open-license references can supplement them, but the report gives those lower provenance strength. Restricted-source images stay manual unless reuse permission is explicit.",
            style = MaterialTheme.typography.bodySmall
        )

        val genuineSources = pack.sources.filter {
            it.kind == ReferenceSourceKind.CERTIFIED_GENUINE ||
                it.kind == ReferenceSourceKind.GENUINE_AUCTION ||
                it.kind == ReferenceSourceKind.CATALOG_REFERENCE
        }
        genuineSources.forEach { source ->
            ReferenceSourceCard(
                source = source,
                onOpen = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url))) },
                onAttachObverse = { onReferenceImport(CoinSide.OBVERSE, source.id) },
                onAttachReverse = { onReferenceImport(CoinSide.REVERSE, source.id) }
            )
        }

        if (state.referenceSamples.isNotEmpty()) {
            fun sampleKind(sourceId: String) = pack.sources.firstOrNull { it.id == sourceId }?.kind
            val genuineSamples = state.referenceSamples.filter {
                sampleKind(it.provenanceSourceId) == ReferenceSourceKind.CERTIFIED_GENUINE ||
                    sampleKind(it.provenanceSourceId) == ReferenceSourceKind.GENUINE_AUCTION ||
                    sampleKind(it.provenanceSourceId) == ReferenceSourceKind.CATALOG_REFERENCE
            }
            val counterfeitSamples = state.referenceSamples.filter {
                sampleKind(it.provenanceSourceId) == ReferenceSourceKind.COUNTERFEIT_DIAGNOSTIC ||
                    sampleKind(it.provenanceSourceId) == ReferenceSourceKind.COMMUNITY_LEAD
            }
            val genuineObverse = genuineSamples.count { it.side == CoinSide.OBVERSE }
            val genuineReverse = genuineSamples.count { it.side == CoinSide.REVERSE }
            val provenanceLines = state.referenceSamples
                .groupBy { it.provenanceSourceId }
                .map { (sourceId, samples) ->
                    "${samples.size}× ${samples.first().side.label}: ${sourceTitle(pack.sources, sourceId)}"
                }
            InfoCard(
                "Attached reference library",
                "Genuine controls — O: $genuineObverse • R: $genuineReverse\n" +
                    "Counterfeit/community controls: ${counterfeitSamples.size}\n" + provenanceLines.joinToString("\n")
            )
        } else {
            val refO = state.images[ImageSlot.REFERENCE_OBVERSE]
            val refR = state.images[ImageSlot.REFERENCE_REVERSE]
            if (refO != null || refR != null) {
                InfoCard(
                    "Attached provenance",
                    listOfNotNull(
                        refO?.let { "Obverse: ${sourceTitle(pack.sources, it.provenanceSourceId)}" },
                        refR?.let { "Reverse: ${sourceTitle(pack.sources, it.provenanceSourceId)}" }
                    ).joinToString("\n")
                )
            }
        }

        SectionTitle("Documented counterfeit controls")
        Text(
            "Attach a counterfeit image only to its documented source. It is compared in a separate evidence pool and never becomes a genuine baseline. Same-type/different-date and community examples are explicitly down-scoped.",
            style = MaterialTheme.typography.bodySmall
        )
        pack.sources.filter {
            it.kind == ReferenceSourceKind.COUNTERFEIT_DIAGNOSTIC || it.kind == ReferenceSourceKind.COMMUNITY_LEAD
        }.forEach { source ->
            ReferenceSourceCard(
                source = source,
                onOpen = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url))) },
                onAttachObverse = { onReferenceImport(CoinSide.OBVERSE, source.id) },
                onAttachReverse = { onReferenceImport(CoinSide.REVERSE, source.id) }
            )
            if (source.kind == ReferenceSourceKind.COMMUNITY_LEAD) {
                Text(
                    "Community image comparisons remain manual-only and cannot produce an automated counterfeit verdict.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        SectionTitle("Authentication methodology")
        pack.sources.filter { it.kind == ReferenceSourceKind.AUTHENTICATION_GUIDANCE }.forEach { source ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(source.title, fontWeight = FontWeight.SemiBold)
                    Text("${source.authority.label} • ${source.scope.label}", style = MaterialTheme.typography.labelSmall)
                    Text(source.notes, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url))) }) {
                        Text("Open methodology source")
                    }
                }
            }
        }

        SectionTitle("Diagnostic map")
        pack.markers.forEach { marker ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(marker.title, fontWeight = FontWeight.SemiBold)
                    Text("${marker.scope.label} • ${marker.category.name.replace('_', ' ')}", style = MaterialTheme.typography.labelSmall)
                    Text(marker.description, style = MaterialTheme.typography.bodySmall)
                    Text("Sources: ${marker.sourceIds.joinToString()}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Button(onClick = vm::runReferenceAssessment, enabled = !state.isWorking) {
            Text("Run exact reference assessment")
        }

        state.referenceAssessment?.takeIf { it.packId == pack.id }?.let { assessment ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reference-pack evidence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    assessment.evidenceConsistencyScore?.let {
                        Text("${f1(it)}/100 evidence consistency", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    assessment.provenanceStrengthScore?.let {
                        Text("Reference provenance strength: ${f1(it)}/100", fontWeight = FontWeight.Medium)
                    }
                    if (assessment.provenanceSummary.isNotBlank()) {
                        Text(assessment.provenanceSummary, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(assessment.interpretation, style = MaterialTheme.typography.bodySmall)

                    if (assessment.regionComparisons.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Region-by-region geometry", fontWeight = FontWeight.SemiBold)
                        assessment.regionComparisons.forEach { region ->
                            Text("${region.side.label} — ${region.label}: ${f1(region.consistencyScore)}/100", fontWeight = FontWeight.Medium)
                            Text("Edges ${f1(region.edgeScore)} • luminance ${f1(region.luminanceScore)}", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (assessment.counterfeitComparisons.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Counterfeit-reference proximity", fontWeight = FontWeight.SemiBold)
                        Text(
                            "These are source-scoped visual comparisons, not counterfeit probabilities. A low match cannot prove genuine and a high match requires corroboration.",
                            style = MaterialTheme.typography.labelSmall
                        )
                        assessment.counterfeitComparisons.forEach { comparison ->
                            val baseline = comparison.genuineConsensusScore?.let { " • genuine ${f1(it)}" } ?: " • no genuine baseline"
                            val delta = comparison.deltaVsGenuine?.let { " • Δ ${if (it >= 0) "+" else ""}${f1(it)}" } ?: ""
                            Text(
                                "${comparison.side.label} — ${comparison.regionLabel}: fake ${f1(comparison.counterfeitSimilarityScore)}$baseline$delta",
                                fontWeight = FontWeight.Medium
                            )
                            Text(comparison.interpretation, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (assessment.checks.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Diagnostic checks", fontWeight = FontWeight.SemiBold)
                        assessment.checks.forEach { check ->
                            Text("${diagnosticMark(check.status)} ${check.title}", fontWeight = FontWeight.Medium)
                            Text(check.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        InfoCard(
            "Why multiple genuine controls matter",
            "A single genuine coin can differ because of grade, toning, die state, clashed dies, cleaning, photography or circulation. The analyzer takes a median across multiple certified genuine controls and keeps documented counterfeit samples in a separate comparison pool."
        )
    }
}

@Composable
private fun OnlineDiscoverySection(state: AppUiState, vm: AppViewModel) {
    val context = LocalContext.current
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Online reference research", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Uses coin metadata to find catalogue identity and legally reusable reference images. Restricted grading-service pages are not scraped; they remain source-linked/manual unless an official permitted feed is configured.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = state.onlineReferenceConfig.numistaApiKey,
                onValueChange = { vm.updateOnlineReferenceConfig(state.onlineReferenceConfig.copy(numistaApiKey = it)) },
                label = { Text("Numista API key (optional)") },
                supportingText = { Text("Kept in memory for this app session; not exported in reports.") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.onlineReferenceConfig.manifestUrl,
                onValueChange = { vm.updateOnlineReferenceConfig(state.onlineReferenceConfig.copy(manifestUrl = it)) },
                label = { Text("Curated reference manifest URL (optional)") },
                supportingText = { Text("HTTPS JSON feed for exact die packs, diagnostics, provenance and licensed reference assets.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::discoverOnlineReferences, enabled = !state.isWorking) { Text("Research this coin") }
                OutlinedButton(
                    onClick = vm::acquireBestAndAuthenticate,
                    enabled = !state.isWorking && state.discoveryResult != null
                ) { Text("Acquire + authenticate") }
            }

            state.discoveryResult?.let { result ->
                Text("Query: ${result.query}", style = MaterialTheme.typography.labelSmall)
                Text(
                    "Providers: ${result.providersUsed.joinToString { it.label }.ifBlank { "none" }} • identities ${result.identityCandidates.size} • references ${result.references.size}",
                    style = MaterialTheme.typography.labelSmall
                )
                result.identityCandidates.take(4).forEach { identity ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(identity.title, fontWeight = FontWeight.SemiBold)
                            Text("${identity.provider.label} • ${identity.catalogReferences.joinToString()}", style = MaterialTheme.typography.labelSmall)
                            val physical = listOfNotNull(
                                identity.weightGrams?.let { "${f1(it)} g" },
                                identity.diameterMm?.let { "${f1(it)} mm" },
                                identity.thicknessMm?.let { "${f1(it)} mm thick" }
                            ).joinToString(" • ")
                            if (physical.isNotBlank()) Text(physical, style = MaterialTheme.typography.bodySmall)
                            Text(identity.matchNote, style = MaterialTheme.typography.bodySmall)
                            val identityKey = "${identity.provider.name}:${identity.id}"
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (state.selectedIdentityCandidateId == identityKey) {
                                    AssistChip(onClick = {}, label = { Text("Selected") })
                                } else {
                                    OutlinedButton(onClick = { vm.selectIdentityCandidate(identity) }) { Text("Use identity") }
                                }
                                if (identity.sourceUrl.startsWith("https://")) {
                                    OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(identity.sourceUrl))) }) {
                                        Text("Open identity")
                                    }
                                }
                            }
                        }
                    }
                }

                if (result.references.isNotEmpty()) {
                    Text("Discovered image/source candidates", fontWeight = FontWeight.SemiBold)
                    result.references.take(12).forEach { ref ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(ref.title, fontWeight = FontWeight.Medium)
                                Text(
                                    "${ref.provider.label} • ${ref.trust.label} • ${ref.side?.label ?: "side unknown"}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    if (ref.canAutoDownload) "Auto-download permitted: ${ref.licenseName ?: ref.rightsBasis ?: "authorized reusable asset"}" else "Source only: image reuse permission not established",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                ref.rightsBasis?.let { Text("Rights: $it", style = MaterialTheme.typography.labelSmall) }
                                ref.sha256?.let { Text("Integrity: SHA-256 pinned by manifest", style = MaterialTheme.typography.labelSmall) }
                                if (ref.notes.isNotBlank()) Text(ref.notes, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (ref.sourcePageUrl.startsWith("https://")) {
                                        OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ref.sourcePageUrl))) }) { Text("Open") }
                                    }
                                    if (ref.canAutoDownload && ref.imageUrl != null) {
                                        when (ref.side) {
                                            CoinSide.OBVERSE -> OutlinedButton(onClick = { vm.acquireDiscoveredReference(ref.id) }) { Text("Acquire O") }
                                            CoinSide.REVERSE -> OutlinedButton(onClick = { vm.acquireDiscoveredReference(ref.id) }) { Text("Acquire R") }
                                            CoinSide.EDGE -> OutlinedButton(onClick = { vm.acquireDiscoveredReference(ref.id) }) { Text("Acquire edge") }
                                            null -> {
                                                OutlinedButton(onClick = { vm.acquireDiscoveredReference(ref.id, CoinSide.OBVERSE) }) { Text("Use as O") }
                                                OutlinedButton(onClick = { vm.acquireDiscoveredReference(ref.id, CoinSide.REVERSE) }) { Text("Use as R") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                result.warnings.take(5).forEach { Text("△ $it", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun ReferenceSourceCard(
    source: ReferenceSource,
    onOpen: () -> Unit,
    onAttachObverse: () -> Unit,
    onAttachReverse: () -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(source.title, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(source.authority.label, source.gradeOrStatus, source.certificationNumber?.let { "Cert $it" }).joinToString(" • "),
                style = MaterialTheme.typography.labelSmall
            )
            Text(source.notes, style = MaterialTheme.typography.bodySmall)
            Text(source.imageUsageNote, style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onOpen) { Text("Open") }
                OutlinedButton(onClick = onAttachObverse) { Text("Add O") }
                OutlinedButton(onClick = onAttachReverse) { Text("Add R") }
            }
        }
    }
}

private fun sourceTitle(sources: List<ReferenceSource>, id: String?): String {
    if (id == null) return "unverified local image"
    return sources.firstOrNull { it.id == id }?.let { "${it.authority.label} — ${it.title}" } ?: "unknown source id: $id"
}

private fun diagnosticMark(status: DiagnosticStatus): String = when (status) {
    DiagnosticStatus.CONSISTENT -> "✓"
    DiagnosticStatus.CAUTION -> "△"
    DiagnosticStatus.INCONSISTENT -> "!"
    DiagnosticStatus.NOT_AVAILABLE -> "—"
    DiagnosticStatus.MANUAL_REVIEW -> "◉"
}

@Composable
private fun CompareScreen(state: AppUiState, vm: AppViewModel) {
    val availableTargets = listOf(ImageSlot.OBVERSE, ImageSlot.REVERSE).filter { state.images.containsKey(it) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("Reference comparison")
        Text(
            "Use a documented genuine specimen of the same type/date/die family when possible. The score measures visual consistency after a small local alignment search; it is not an authenticity probability.",
            style = MaterialTheme.typography.bodySmall
        )
        if (availableTargets.isEmpty()) {
            InfoCard("No target", "Load an obverse or reverse image in the Case tab.")
            return@Column
        }

        ScrollableChips {
            availableTargets.forEach { slot ->
                FilterChip(
                    selected = state.selectedCompareSlot == slot,
                    onClick = { vm.setCompareSlot(slot) },
                    label = { Text(slot.shortLabel) }
                )
            }
        }

        val refSlot = if (state.selectedCompareSlot == ImageSlot.REVERSE) ImageSlot.REFERENCE_REVERSE else ImageSlot.REFERENCE_OBVERSE
        val target = state.images[state.selectedCompareSlot]?.bitmap
        val reference = state.images[refSlot]?.bitmap

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniImageCard("Your coin", target, Modifier.weight(1f))
            MiniImageCard("Genuine ref", reference, Modifier.weight(1f))
        }

        if (reference == null) {
            InfoCard("Reference missing", "Load ${refSlot.label.lowercase()} in the Case tab before comparing.")
        }

        Button(onClick = vm::compareSelected, enabled = reference != null && target != null && !state.isWorking) {
            Text("Align and compare locally")
        }

        state.comparisonBySlot[state.selectedCompareSlot]?.let { result ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Consistency ${f1(result.consistencyScore)}/100", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(result.interpretation)
                    Metric("Luminance consistency", result.luminanceCorrelation, "/100")
                    Metric("Edge / device consistency", result.edgeCorrelation, "/100")
                    Text(
                        "Best alignment: ${f1(result.bestRotationDegrees.toDouble())}° rotation; ${f1(result.bestScale.toDouble())}× scale; offset ${result.bestOffsetX}, ${result.bestOffsetY} px.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("Normalized difference map", fontWeight = FontWeight.SemiBold)
                    Image(
                        bitmap = result.differenceBitmap.asImageBitmap(),
                        contentDescription = "Difference map",
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        "Red regions deserve inspection, but they can be caused by lighting, wear, die state/variety, centering, lens distortion, compression, or genuine design differences.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportScreen(state: AppUiState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("Evidence report")
        Text(
            "The report preserves the separation between captured evidence, deterministic transforms, reference consistency, and physical measurements.",
            style = MaterialTheme.typography.bodySmall
        )

        SummaryLine("Images loaded", state.images.size.toString())
        SummaryLine("Evidence scans", state.analysisBySlot.size.toString())
        SummaryLine("Reference comparisons", state.comparisonBySlot.size.toString())
        SummaryLine("Reference assessment", if (state.referenceAssessment != null) "complete" else "not run")

        state.referenceAssessment?.let { assessment ->
            InfoCard(
                "${assessment.packName}",
                assessment.evidenceConsistencyScore?.let { "${f1(it)}/100 evidence consistency. ${assessment.interpretation}" }
                    ?: assessment.interpretation
            )
        }

        state.comparisonBySlot.forEach { (slot, result) ->
            InfoCard("${slot.shortLabel} comparison", "${f1(result.consistencyScore)}/100 consistency. ${result.interpretation}")
        }

        Button(
            enabled = !exporting,
            onClick = {
                exporting = true
                scope.launch {
                    val file = withContext(Dispatchers.IO) { ReportExporter.createPdf(context, state) }
                    exporting = false
                    ReportExporter.sharePdf(context, file)
                }
            }
        ) { Text(if (exporting) "Creating PDF…" else "Export / share PDF report") }

        InfoCard(
            "Authentication boundary",
            "A high visual-consistency score is supportive evidence only. Final authentication should also consider exact specifications, edge diagnostics, provenance, metal/composition tests where appropriate, known die diagnostics, and expert in-hand examination."
        )

        SectionTitle("Designed next extensions")
        Text("• additional exact-date reference packs and multi-control consensus scoring")
        Text("• licensed/public-domain reference-image ingestion where source terms permit")
        Text("• named counterfeit-marker overlays with documented coordinates")
        Text("• multi-light image registration to distinguish relief from stains/shadows")
        Text("• specific-gravity / scale measurement entry and tolerance rules")
        Text("• optional local TFLite/ONNX classifier as an independent vote, never the sole verdict")
        Text("• OpenCV feature registration for more difficult perspective/alignment cases")
    }
}

@Composable
private fun ZoomableImage(bitmap: Bitmap?) {
    if (bitmap == null) {
        Box(Modifier.fillMaxWidth().height(260.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text("No image")
        }
        return
    }
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 560.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Forensic image",
                modifier = Modifier.fillMaxSize()
                    .pointerInput(bitmap) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 8f)
                            offset += pan
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Pinch to zoom • drag to inspect", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            AssistChip(onClick = { scale = 1f; offset = Offset.Zero }, label = { Text("Reset view") })
        }
    }
}

@Composable
private fun MiniImageCard(title: String, bitmap: Bitmap?, modifier: Modifier = Modifier) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            if (bitmap == null) {
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { Text("Missing") }
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun ScrollableChips(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun Metric(label: String, value: Double, suffix: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text("${f1(value)}$suffix", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun f1(v: Double): String = "%.1f".format(Locale.US, v)
