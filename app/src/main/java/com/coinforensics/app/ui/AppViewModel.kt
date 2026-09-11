package com.coinforensics.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coinforensics.app.imaging.ForensicEngine
import com.coinforensics.app.imaging.ImageIo
import com.coinforensics.app.imaging.ReferenceComparator
import com.coinforensics.app.model.AppTab
import com.coinforensics.app.model.AppUiState
import com.coinforensics.app.model.CoinIdentityCandidate
import com.coinforensics.app.model.CoinMetadata
import com.coinforensics.app.model.CoinSide
import com.coinforensics.app.model.DiscoveredReference
import com.coinforensics.app.model.DiscoveryProvider
import com.coinforensics.app.model.ForensicMode
import com.coinforensics.app.model.ImageSlot
import com.coinforensics.app.model.LoadedImage
import com.coinforensics.app.model.OnlineReferenceConfig
import com.coinforensics.app.model.ReferencePack
import com.coinforensics.app.model.ReferenceSampleImage
import com.coinforensics.app.model.ReferenceSourceKind
import com.coinforensics.app.reference.DieReferenceAnalyzer
import com.coinforensics.app.reference.GenericReferencePackFactory
import com.coinforensics.app.reference.OnlineReferenceService
import com.coinforensics.app.reference.ReferencePackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val builtInPacks = ReferencePackRepository.builtIns()
    private val _state = MutableStateFlow(
        AppUiState(
            referencePacks = builtInPacks,
            selectedReferencePackId = null
        )
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    fun setTab(tab: AppTab) = _state.update { it.copy(tab = tab) }

    fun updateMetadata(metadata: CoinMetadata) = _state.update { current ->
        val identityChanged = current.metadata.country != metadata.country ||
            current.metadata.denomination != metadata.denomination ||
            current.metadata.year != metadata.year ||
            current.metadata.mint != metadata.mint
        if (!identityChanged) {
            current.copy(metadata = metadata, referenceAssessment = null)
        } else {
            val builtInMatch = findBestBuiltInMatch(metadata)
            current.copy(
                metadata = metadata,
                images = current.images.filterKeys { it != ImageSlot.REFERENCE_OBVERSE && it != ImageSlot.REFERENCE_REVERSE },
                comparisonBySlot = emptyMap(),
                referenceSamples = emptyList(),
                referencePacks = builtInPacks,
                selectedReferencePackId = builtInMatch?.id,
                discoveryResult = null,
                selectedIdentityCandidateId = null,
                referenceAssessment = null
            )
        }
    }

    fun updateOnlineReferenceConfig(config: OnlineReferenceConfig) = _state.update {
        it.copy(onlineReferenceConfig = config)
    }

    fun importImage(slot: ImageSlot, uri: Uri, provenanceSourceId: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, message = null) }
            runCatching {
                withContext(Dispatchers.IO) { ImageIo.decode(getApplication(), uri) }
            }.onSuccess { bitmap ->
                _state.update { s ->
                    val loaded = LoadedImage(slot, bitmap, uri.lastPathSegment ?: "image", provenanceSourceId)
                    val images = s.images + (slot to loaded)
                    val nextForensic = when {
                        slot in listOf(ImageSlot.OBVERSE, ImageSlot.REVERSE, ImageSlot.EDGE, ImageSlot.UV, ImageSlot.IR) -> slot
                        s.images[s.selectedForensicSlot] == null -> ImageSlot.OBVERSE
                        else -> s.selectedForensicSlot
                    }
                    s.copy(
                        images = images,
                        selectedForensicSlot = nextForensic,
                        processedBitmap = if (slot == nextForensic) bitmap else s.processedBitmap,
                        referenceAssessment = null,
                        isWorking = false,
                        message = if (provenanceSourceId == null) "${slot.label} loaded" else "${slot.label} loaded with documented provenance"
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isWorking = false, message = "Could not decode image: ${error.message}") }
            }
        }
    }

    fun importReferenceSample(side: CoinSide, uri: Uri, provenanceSourceId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, message = null) }
            runCatching {
                withContext(Dispatchers.IO) { ImageIo.decode(getApplication(), uri) }
            }.onSuccess { bitmap ->
                _state.update { s ->
                    addReferenceBitmap(
                        state = s,
                        side = side,
                        bitmap = bitmap,
                        provenanceSourceId = provenanceSourceId,
                        sourceLabel = uri.lastPathSegment ?: "reference",
                        autoAcquired = false,
                        licenseSummary = null
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isWorking = false, message = "Could not decode reference image: ${error.message}") }
            }
        }
    }

    fun discoverOnlineReferences() {
        val snapshot = _state.value
        val metadata = snapshot.metadata
        if (metadata.country.isBlank() && metadata.denomination.isBlank() && metadata.year.isBlank()) {
            _state.update { it.copy(message = "Enter at least issuer/country, denomination, or year before online reference research.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, message = "Researching reusable references and catalogue identity…") }
            runCatching {
                withContext(Dispatchers.IO) {
                    OnlineReferenceService.discover(metadata, snapshot.onlineReferenceConfig)
                }
            }.onSuccess { result ->
                _state.update { current ->
                    val builtInMatch = findBestBuiltInMatch(metadata)
                    val curatedMatch = result.curatedPacks.maxByOrNull { matchScore(it, metadata) }
                    val curatedIdentities = result.identityCandidates.filter { it.provider == DiscoveryProvider.CURATED_MANIFEST }
                    val selectedIdentity = curatedIdentities.singleOrNull()
                        ?: result.identityCandidates.singleOrNull()
                    val basePack = curatedMatch ?: builtInMatch ?: GenericReferencePackFactory.create(
                        metadata = metadata,
                        identity = selectedIdentity,
                        discovered = result.references
                    )
                    val chosen = if (curatedMatch != null) {
                        GenericReferencePackFactory.mergeDiscoveredSources(curatedMatch, result.references)
                    } else if (builtInMatch != null) {
                        GenericReferencePackFactory.mergeDiscoveredSources(builtInMatch, result.references)
                    } else basePack

                    val packs = (builtInPacks + result.curatedPacks + chosen)
                        .associateBy { it.id }
                        .toMutableMap()
                        .apply { put(chosen.id, chosen) }
                        .values
                        .toList()
                    val changedPack = current.selectedReferencePackId != chosen.id
                    current.copy(
                        referencePacks = packs,
                        selectedReferencePackId = chosen.id,
                        discoveryResult = result,
                        selectedIdentityCandidateId = selectedIdentity?.let(::identityKey),
                        referenceSamples = if (changedPack) emptyList() else current.referenceSamples,
                        referenceAssessment = null,
                        isWorking = false,
                        message = if (selectedIdentity == null && result.identityCandidates.size > 1) {
                            "Reference research complete: ${result.identityCandidates.size} identity candidates found. Select the exact coin identity before physical-spec scoring; ${result.references.size} image/source candidate(s) found."
                        } else {
                            "Reference research complete: ${result.identityCandidates.size} identity candidate(s), ${result.references.size} image/source candidate(s)."
                        }
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isWorking = false, message = "Reference research failed: ${error.message}") }
            }
        }
    }

    fun selectIdentityCandidate(identity: CoinIdentityCandidate) {
        _state.update { current ->
            val discovery = current.discoveryResult ?: return@update current.copy(message = "Run reference research first.")
            if (discovery.identityCandidates.none { identityKey(it) == identityKey(identity) }) {
                return@update current.copy(message = "That identity candidate is no longer available.")
            }
            val builtInMatch = findBestBuiltInMatch(current.metadata)
            val curatedMatch = discovery.curatedPacks.maxByOrNull { matchScore(it, current.metadata) }
            val base = curatedMatch ?: builtInMatch ?: GenericReferencePackFactory.create(
                metadata = current.metadata,
                identity = identity,
                discovered = discovery.references
            )
            val chosen = if (curatedMatch != null || builtInMatch != null) {
                GenericReferencePackFactory.mergeDiscoveredSources(base, discovery.references)
            } else base
            val changedPack = current.selectedReferencePackId != chosen.id
            val packs = (builtInPacks + discovery.curatedPacks + chosen)
                .associateBy { it.id }
                .toMutableMap()
                .apply { put(chosen.id, chosen) }
                .values.toList()
            current.copy(
                referencePacks = packs,
                selectedReferencePackId = chosen.id,
                selectedIdentityCandidateId = identityKey(identity),
                referenceSamples = if (changedPack) emptyList() else current.referenceSamples,
                referenceAssessment = null,
                message = "Selected identity: ${identity.title}."
            )
        }
    }

    fun acquireDiscoveredReference(referenceId: String, sideOverride: CoinSide? = null) {
        val snapshot = _state.value
        val ref = snapshot.discoveryResult?.references?.firstOrNull { it.id == referenceId }
        if (ref == null) {
            _state.update { it.copy(message = "Reference candidate not found.") }
            return
        }
        val side = sideOverride ?: ref.side
        if (side == null) {
            _state.update { it.copy(message = "Choose whether this image is obverse or reverse before adding it.") }
            return
        }
        if (!ref.canAutoDownload) {
            _state.update { it.copy(message = "Automatic download is disabled for this image because reusable image rights were not established. Open the source and import manually if permitted.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, message = "Downloading licensed reference image…") }
            runCatching {
                withContext(Dispatchers.IO) { OnlineReferenceService.downloadReferenceBitmap(ref) }
            }.onSuccess { bitmap ->
                _state.update { s ->
                    val withSource = ensureDiscoveredSourceInSelectedPack(s, ref)
                    addReferenceBitmap(
                        state = withSource,
                        side = side,
                        bitmap = bitmap,
                        provenanceSourceId = ref.id,
                        sourceLabel = ref.title,
                        autoAcquired = true,
                        licenseSummary = referenceRightsSummary(ref)
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isWorking = false, message = "Reference download failed: ${error.message}") }
            }
        }
    }

    /** Downloads a conservative set of legally reusable controls and then runs the local analyzer. */
    fun acquireBestAndAuthenticate() {
        val snapshot = _state.value
        val discovery = snapshot.discoveryResult
        val pack = snapshot.referencePacks.firstOrNull { it.id == snapshot.selectedReferencePackId }
        if (discovery == null || pack == null) {
            _state.update { it.copy(message = "Run online reference research first.") }
            return
        }
        if (snapshot.images[ImageSlot.OBVERSE] == null && snapshot.images[ImageSlot.REVERSE] == null) {
            _state.update { it.copy(message = "Load at least one high-resolution coin face before authentication.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, message = "Acquiring the strongest permitted reference set…") }
            val candidates = selectBestAutoCandidates(discovery.references)
            val downloaded = withContext(Dispatchers.IO) {
                candidates.mapNotNull { ref ->
                    val side = ref.side ?: return@mapNotNull null
                    runCatching { Triple(ref, side, OnlineReferenceService.downloadReferenceBitmap(ref)) }.getOrNull()
                }
            }
            _state.update { s ->
                var next = s
                downloaded.forEach { (ref, side, bitmap) ->
                    next = ensureDiscoveredSourceInSelectedPack(next, ref)
                    if (next.referenceSamples.none { it.provenanceSourceId == ref.id && it.side == side }) {
                        next = addReferenceBitmap(
                            state = next,
                            side = side,
                            bitmap = bitmap,
                            provenanceSourceId = ref.id,
                            sourceLabel = ref.title,
                            autoAcquired = true,
                            licenseSummary = referenceRightsSummary(ref),
                            finalWorking = true
                        )
                    }
                }
                next
            }

            val afterDownloads = _state.value
            val selectedPack = afterDownloads.referencePacks.firstOrNull { it.id == afterDownloads.selectedReferencePackId }
            if (selectedPack == null) {
                _state.update { it.copy(isWorking = false, message = "Reference pack disappeared during acquisition.") }
                return@launch
            }
            val analyses = withContext(Dispatchers.Default) {
                afterDownloads.images.filterKeys { it == ImageSlot.OBVERSE || it == ImageSlot.REVERSE }
                    .mapValues { (_, loaded) -> ForensicEngine.analyze(loaded.bitmap) }
            }
            val assessment = withContext(Dispatchers.Default) {
                DieReferenceAnalyzer.assess(selectedPack, afterDownloads.metadata, afterDownloads.images, afterDownloads.referenceSamples)
            }
            _state.update {
                it.copy(
                    analysisBySlot = it.analysisBySlot + analyses,
                    referenceAssessment = assessment,
                    isWorking = false,
                    message = "Best-evidence authentication complete using ${downloaded.size} newly acquired licensed control image(s)."
                )
            }
        }
    }

    fun setForensicSlot(slot: ImageSlot) {
        val source = _state.value.images[slot]?.bitmap
        _state.update { it.copy(selectedForensicSlot = slot, processedBitmap = source, forensicMode = ForensicMode.ORIGINAL) }
    }

    fun setCompareSlot(slot: ImageSlot) = _state.update { it.copy(selectedCompareSlot = slot) }

    fun setFilterIntensity(value: Float) = _state.update { it.copy(filterIntensity = value) }

    fun setModeAndProcess(mode: ForensicMode) {
        _state.update { it.copy(forensicMode = mode) }
        processSelected()
    }

    fun processSelected() {
        val snapshot = _state.value
        val source = snapshot.images[snapshot.selectedForensicSlot]?.bitmap ?: return
        val mode = snapshot.forensicMode
        val intensity = snapshot.filterIntensity
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, message = null) }
            val processed = withContext(Dispatchers.Default) { ForensicEngine.process(source, mode, intensity) }
            _state.update { it.copy(processedBitmap = processed, isWorking = false) }
        }
    }

    fun analyzeSelected() {
        val snapshot = _state.value
        val slot = snapshot.selectedForensicSlot
        val source = snapshot.images[slot]?.bitmap ?: return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, message = null) }
            val analysis = withContext(Dispatchers.Default) { ForensicEngine.analyze(source) }
            _state.update {
                it.copy(
                    analysisBySlot = it.analysisBySlot + (slot to analysis),
                    isWorking = false,
                    message = "Evidence scan complete for ${slot.shortLabel}"
                )
            }
        }
    }

    fun compareSelected() {
        val snapshot = _state.value
        val slot = snapshot.selectedCompareSlot
        val target = snapshot.images[slot]?.bitmap
        val referenceSlot = when (slot) {
            ImageSlot.OBVERSE -> ImageSlot.REFERENCE_OBVERSE
            ImageSlot.REVERSE -> ImageSlot.REFERENCE_REVERSE
            else -> null
        }
        val reference = referenceSlot?.let { snapshot.images[it]?.bitmap }
        if (target == null || reference == null) {
            _state.update { it.copy(message = "Load both the coin image and its matching genuine reference first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, message = "Aligning images locally…") }
            val result = withContext(Dispatchers.Default) { ReferenceComparator.compare(target, reference) }
            _state.update {
                it.copy(
                    comparisonBySlot = it.comparisonBySlot + (slot to result),
                    isWorking = false,
                    message = "Reference comparison complete"
                )
            }
        }
    }

    fun selectReferencePack(packId: String) {
        val pack = _state.value.referencePacks.firstOrNull { it.id == packId } ?: return
        _state.update { state ->
            state.copy(
                selectedReferencePackId = packId,
                referenceSamples = if (state.selectedReferencePackId == packId) state.referenceSamples else emptyList(),
                referenceAssessment = null,
                message = "Reference pack selected: ${pack.displayName}"
            )
        }
    }

    fun applyReferencePackMetadata() {
        val state = _state.value
        val pack = state.referencePacks.firstOrNull { it.id == state.selectedReferencePackId } ?: return
        _state.update {
            it.copy(
                metadata = it.metadata.copy(
                    country = pack.country,
                    denomination = pack.denomination,
                    year = pack.year,
                    mint = pack.mintOrVariety
                ),
                message = "Case metadata filled from ${pack.displayName}"
            )
        }
    }

    fun runReferenceAssessment() {
        val snapshot = _state.value
        val pack = snapshot.referencePacks.firstOrNull { it.id == snapshot.selectedReferencePackId }
        if (pack == null) {
            _state.update { it.copy(message = "Select a reference pack first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, message = "Running high-resolution reference evidence checks…") }
            val assessment = withContext(Dispatchers.Default) {
                DieReferenceAnalyzer.assess(pack, snapshot.metadata, snapshot.images, snapshot.referenceSamples)
            }
            _state.update {
                it.copy(
                    referenceAssessment = assessment,
                    isWorking = false,
                    message = "Reference-pack assessment complete"
                )
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun findBestBuiltInMatch(metadata: CoinMetadata): ReferencePack? = builtInPacks
        .map { pack -> pack to matchScore(pack, metadata) }
        .filter { it.second >= 5 }
        .maxByOrNull { it.second }
        ?.first

    private fun identityKey(identity: CoinIdentityCandidate): String = "${identity.provider.name}:${identity.id}"

    private fun referenceRightsSummary(ref: DiscoveredReference): String? = listOfNotNull(
        ref.licenseName,
        ref.creator,
        ref.rightsBasis,
        ref.sha256?.let { "SHA-256 pinned" }
    ).joinToString(" • ").takeIf { it.isNotBlank() }

    private fun matchScore(pack: ReferencePack, metadata: CoinMetadata): Int {
        var score = 0
        if (metadata.year.isNotBlank() && pack.year.equals(metadata.year.trim(), true)) score += 4
        if (metadata.country.isNotBlank() && (pack.country.contains(metadata.country.trim(), true) || metadata.country.contains(pack.country, true))) score += 2
        if (metadata.denomination.isNotBlank() && (pack.denomination.contains(metadata.denomination.trim(), true) || metadata.denomination.contains(pack.denomination, true))) score += 2
        if (metadata.mint.isNotBlank() && (pack.mintOrVariety.contains(metadata.mint.trim(), true) || metadata.mint.contains(pack.mintOrVariety, true))) score += 1
        return score
    }

    private fun selectBestAutoCandidates(all: List<DiscoveredReference>): List<DiscoveredReference> {
        val eligible = all.filter { it.canAutoDownload && it.imageUrl != null && it.side != null }
        val genuine = eligible.filter {
            it.kind == ReferenceSourceKind.CERTIFIED_GENUINE ||
                it.kind == ReferenceSourceKind.GENUINE_AUCTION ||
                it.kind == ReferenceSourceKind.CATALOG_REFERENCE
        }
        val fake = eligible.filter {
            it.kind == ReferenceSourceKind.COUNTERFEIT_DIAGNOSTIC || it.kind == ReferenceSourceKind.COMMUNITY_LEAD
        }
        return buildList {
            listOf(CoinSide.OBVERSE, CoinSide.REVERSE).forEach { side ->
                addAll(genuine.filter { it.side == side }.sortedBy { it.trust.ordinal }.take(3))
                addAll(fake.filter { it.side == side }.sortedBy { it.trust.ordinal }.take(1))
            }
        }.distinctBy { it.id }
    }

    private fun ensureDiscoveredSourceInSelectedPack(state: AppUiState, ref: DiscoveredReference): AppUiState {
        val selectedId = state.selectedReferencePackId ?: return state
        val pack = state.referencePacks.firstOrNull { it.id == selectedId } ?: return state
        if (pack.sources.any { it.id == ref.id }) return state
        val merged = GenericReferencePackFactory.mergeDiscoveredSources(pack, listOf(ref))
        return state.copy(referencePacks = state.referencePacks.map { if (it.id == selectedId) merged else it })
    }

    private fun addReferenceBitmap(
        state: AppUiState,
        side: CoinSide,
        bitmap: android.graphics.Bitmap,
        provenanceSourceId: String,
        sourceLabel: String,
        autoAcquired: Boolean,
        licenseSummary: String?,
        finalWorking: Boolean = false
    ): AppUiState {
        val sample = ReferenceSampleImage(
            id = "${provenanceSourceId}-${side.name}-${System.nanoTime()}",
            side = side,
            bitmap = bitmap,
            sourceLabel = sourceLabel,
            provenanceSourceId = provenanceSourceId,
            autoAcquired = autoAcquired,
            licenseSummary = licenseSummary
        )
        val source = state.referencePacks
            .firstOrNull { it.id == state.selectedReferencePackId }
            ?.sources
            ?.firstOrNull { it.id == provenanceSourceId }
        val isGenuine = source?.kind in setOf(
            ReferenceSourceKind.CERTIFIED_GENUINE,
            ReferenceSourceKind.GENUINE_AUCTION,
            ReferenceSourceKind.CATALOG_REFERENCE
        )
        val slot = if (side == CoinSide.REVERSE) ImageSlot.REFERENCE_REVERSE else ImageSlot.REFERENCE_OBVERSE
        val latest = LoadedImage(slot, bitmap, sample.sourceLabel, provenanceSourceId)
        val images = if (isGenuine && side != CoinSide.EDGE) state.images + (slot to latest) else state.images
        val role = when (source?.kind) {
            ReferenceSourceKind.COUNTERFEIT_DIAGNOSTIC -> "documented counterfeit control"
            ReferenceSourceKind.COMMUNITY_LEAD -> "community counterfeit lead"
            ReferenceSourceKind.CATALOG_REFERENCE -> "catalogue/open-reference control"
            else -> "genuine control"
        }
        return state.copy(
            referenceSamples = state.referenceSamples + sample,
            images = images,
            referenceAssessment = null,
            isWorking = finalWorking,
            message = if (finalWorking) state.message else "${side.label} $role added (${state.referenceSamples.count { it.side == side } + 1} total reference images)"
        )
    }
}
