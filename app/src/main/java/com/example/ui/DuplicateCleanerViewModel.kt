package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.CleanupHistoryEntity
import com.example.data.local.HyperCleanDatabase
import com.example.data.local.LinkedFolderEntity
import com.example.data.repository.StorageScannerRepository
import com.example.domain.engine.ProgressiveHashEngine
import com.example.domain.model.ByteFormatter
import com.example.domain.model.DuplicateGroup
import com.example.domain.model.FileCategory
import com.example.domain.model.ScanPhase
import com.example.domain.model.ScanTelemetry
import com.example.domain.model.SelectionRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DuplicateCleanerUiState(
    val isScanning: Boolean = false,
    val telemetry: ScanTelemetry = ScanTelemetry(),
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val selectedCategory: FileCategory = FileCategory.ALL,
    val selectionRule: SelectionRule = SelectionRule.ORIGINAL_BY_DATE_AND_FOLDER,
    val searchQuery: String = "",
    val statusBannerMessage: String? = null
) {
    val filteredGroups: List<DuplicateGroup>
        get() = duplicateGroups.filter { group ->
            val matchesCategory = selectedCategory == FileCategory.ALL || group.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() || group.files.any {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.folderPath.contains(searchQuery, ignoreCase = true)
            }
            matchesCategory && matchesQuery
        }

    val totalSelectedFilesCount: Int
        get() = duplicateGroups.sumOf { g -> g.files.count { it.isSelectedForDeletion } }

    val totalSelectedReclaimableBytes: Long
        get() = duplicateGroups.sumOf { g ->
            g.files.count { it.isSelectedForDeletion } * g.fileSizeBytes
        }
}

class DuplicateCleanerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = HyperCleanDatabase.getInstance(application)
    private val dao = database.dao()
    private val repository = StorageScannerRepository(application, dao)
    private val progressiveEngine = ProgressiveHashEngine(
        dao = dao,
        streamProvider = { item -> repository.openFileInputStream(item) },
        fastReaderProvider = { item -> repository.openFastReader(item) }
    )

    private val _uiState = MutableStateFlow(DuplicateCleanerUiState())
    val uiState: StateFlow<DuplicateCleanerUiState> = _uiState.asStateFlow()

    val cachedSignaturesCount: StateFlow<Int> = dao.observeCachedSignatureCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val cleanupHistory: StateFlow<List<CleanupHistoryEntity>> = dao.observeCleanupHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalBytesFreedAllTime: StateFlow<Long> = dao.observeTotalBytesFreedAllTime()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val linkedFolders: StateFlow<List<LinkedFolderEntity>> = dao.observeLinkedFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var activeScanJob: Job? = null

    init {
        startUltraFastScan()
    }

    fun startUltraFastScan() {
        activeScanJob?.cancel()
        activeScanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    statusBannerMessage = null,
                    telemetry = ScanTelemetry(
                        phase = ScanPhase.DISCOVERING,
                        progressFraction = 0.08f,
                        currentStatusText = "Indicizzazione file su MediaStore e cartelle dispositivo collegate..."
                    )
                )
            }

            try {
                val allFiles = repository.discoverAllFiles()
                val currentRule = _uiState.value.selectionRule

                val output = progressiveEngine.executeProgressiveScan(
                    discoveredFiles = allFiles,
                    selectionRule = currentRule,
                    onTelemetryUpdate = { liveTelemetry ->
                        _uiState.update { state ->
                            state.copy(telemetry = liveTelemetry)
                        }
                    }
                )

                _uiState.update { state ->
                    state.copy(
                        isScanning = false,
                        duplicateGroups = output.duplicateGroups,
                        telemetry = output.telemetry
                    )
                }
            } catch (ce: CancellationException) {
                _uiState.update { state ->
                    state.copy(
                        isScanning = false,
                        telemetry = state.telemetry.copy(
                            phase = ScanPhase.IDLE,
                            currentStatusText = "Scansione interrotta dall'utente"
                        )
                    )
                }
                throw ce
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isScanning = false,
                        statusBannerMessage = "Errore durante la scansione: ${e.localizedMessage ?: "Errore imprevisto"}"
                    )
                }
            }
        }
    }

    fun cancelScan() {
        activeScanJob?.cancel()
        activeScanJob = null
    }

    fun setCategoryFilter(category: FileCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun applySelectionRule(rule: SelectionRule) {
        _uiState.update { state ->
            val updatedGroups = state.duplicateGroups.map { group ->
                val updatedFiles = ProgressiveHashEngine.applySelectionRuleToGroup(
                    files = group.files,
                    rule = rule
                )
                group.copy(files = updatedFiles)
            }
            state.copy(
                selectionRule = rule,
                duplicateGroups = updatedGroups,
                statusBannerMessage = "Applicato criterio: ${rule.titleIt}"
            )
        }
    }

    fun toggleFileSelection(groupId: String, fileId: String) {
        _uiState.update { state ->
            val updatedGroups = state.duplicateGroups.map { group ->
                if (group.groupId != groupId) {
                    group
                } else {
                    val toggledFiles = group.files.map { f ->
                        if (f.id == fileId) {
                            f.copy(isSelectedForDeletion = !f.isSelectedForDeletion)
                        } else {
                            f
                        }
                    }
                    group.copy(files = toggledFiles)
                }
            }
            state.copy(duplicateGroups = updatedGroups)
        }
    }

    fun deleteSelectedDuplicates() {
        val currentState = _uiState.value
        val itemsToDelete = currentState.duplicateGroups
            .flatMap { it.files }
            .filter { it.isSelectedForDeletion }

        if (itemsToDelete.isEmpty()) return

        viewModelScope.launch {
            val (deletedCount, freedBytes) = repository.deleteSelectedFiles(itemsToDelete)

            if (deletedCount > 0) {
                dao.insertCleanupRecord(
                    CleanupHistoryEntity(
                        filesDeletedCount = deletedCount,
                        bytesFreed = freedBytes,
                        scanDurationMs = currentState.telemetry.elapsedTimeMs,
                        ioSavedPercent = currentState.telemetry.ioSavedPercent,
                        summaryNote = "Eliminate $deletedCount copie ridondanti (${ByteFormatter.formatBytes(freedBytes)} liberati)"
                    )
                )
            }

            val deletedIds = itemsToDelete.map { it.id }.toSet()
            _uiState.update { state ->
                val remainingGroups = state.duplicateGroups.mapNotNull { group ->
                    val surviving = group.files.filterNot { it.id in deletedIds }
                    if (surviving.size >= 2) {
                        group.copy(files = surviving)
                    } else {
                        null
                    }
                }
                state.copy(
                    duplicateGroups = remainingGroups,
                    statusBannerMessage = "Eliminati $deletedCount file duplicati • Liberati ${ByteFormatter.formatBytes(freedBytes)}!"
                )
            }
        }
    }

    fun clearHashCache() {
        viewModelScope.launch {
            dao.clearAllSignatures()
            _uiState.update {
                it.copy(statusBannerMessage = "Cache firme 1KB/4KB azzerata con successo")
            }
        }
    }

    fun addSafFolder(treeUri: Uri, folderName: String) {
        viewModelScope.launch {
            repository.addLinkedSafFolder(treeUri, folderName)
            _uiState.update {
                it.copy(statusBannerMessage = "Cartella '$folderName' collegata. Avvio scansione...")
            }
            startUltraFastScan()
        }
    }

    fun removeSafFolder(uriString: String) {
        viewModelScope.launch {
            repository.removeLinkedSafFolder(uriString)
            startUltraFastScan()
        }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application)
                DuplicateCleanerViewModel(app)
            }
        }
    }
}
