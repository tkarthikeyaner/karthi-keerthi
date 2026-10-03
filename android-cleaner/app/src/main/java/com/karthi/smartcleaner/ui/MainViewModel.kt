package com.karthi.smartcleaner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.karthi.smartcleaner.CleanerApp
import com.karthi.smartcleaner.data.AppSettings
import com.karthi.smartcleaner.data.DuplicateGroup
import com.karthi.smartcleaner.data.ScanRepository
import com.karthi.smartcleaner.data.ScanState
import com.karthi.smartcleaner.data.ScannedFile
import com.karthi.smartcleaner.data.SettingsRepository
import com.karthi.smartcleaner.data.StoragePermission
import com.karthi.smartcleaner.data.ThemeMode
import com.karthi.smartcleaner.work.ScanScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val scanRepository: ScanRepository,
) : AndroidViewModel(application) {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val scanState: StateFlow<ScanState> = scanRepository.state

    private val _hasStorageAccess = MutableStateFlow(StoragePermission.isGranted(application))
    val hasStorageAccess: StateFlow<Boolean> = _hasStorageAccess.asStateFlow()

    /** Paths selected for deletion on the file list screens. */
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    /** Paths marked "delete" on the duplicates screen; everything else in a group is kept. */
    private val _duplicateDeletions = MutableStateFlow<Set<String>>(emptySet())
    val duplicateDeletions: StateFlow<Set<String>> = _duplicateDeletions.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            val current = settingsRepository.current()
            if (current.scheduleEnabled) {
                ScanScheduler.ensureScheduled(application, current.scanHour, current.scanMinute)
            }
            if (_hasStorageAccess.value && scanState.value.result == null) scan()
        }
    }

    fun refreshPermission() {
        val granted = StoragePermission.isGranted(getApplication())
        val newlyGranted = granted && !_hasStorageAccess.value
        _hasStorageAccess.value = granted
        if (newlyGranted && scanState.value.result == null) scan()
    }

    fun scan() {
        if (scanState.value.isScanning || !_hasStorageAccess.value) return
        viewModelScope.launch {
            try {
                scanRepository.scan()
                _selection.value = emptySet()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send("Scan failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun delete(files: List<ScannedFile>) {
        if (files.isEmpty()) return
        viewModelScope.launch {
            val outcome = scanRepository.delete(files)
            _selection.update { it - outcome.deleted }
            _duplicateDeletions.update { it - outcome.deleted }
            val msg = buildString {
                append("Deleted ${outcome.deleted.size} files · freed ${formatBytes(outcome.freedBytes)}")
                if (outcome.failed.isNotEmpty()) append(" · ${outcome.failed.size} could not be deleted")
            }
            _messages.send(msg)
        }
    }

    // --- Selection (file lists) ---

    fun toggleSelection(path: String) = _selection.update { if (path in it) it - path else it + path }

    fun setSelection(paths: Set<String>) {
        _selection.value = paths
    }

    // --- Keep/delete decisions (duplicates) ---

    fun resetDuplicateDecisions(groups: List<DuplicateGroup>) {
        _duplicateDeletions.value = groups.flatMapTo(HashSet()) { g ->
            g.files.filter { it.path != g.suggestedKeep }.map { it.path }
        }
    }

    /** Flips keep/delete for one file, refusing to mark the last kept copy of a group for deletion. */
    fun toggleDuplicateDecision(group: DuplicateGroup, path: String) {
        val current = _duplicateDeletions.value
        if (path in current) {
            _duplicateDeletions.value = current - path
            return
        }
        val keptInGroup = group.files.count { it.path !in current }
        if (keptInGroup <= 1) {
            _messages.trySend("Keep at least one copy in each group")
            return
        }
        _duplicateDeletions.value = current + path
    }

    // --- Settings ---

    fun setThresholdMb(mb: Int) = viewModelScope.launch {
        settingsRepository.setThresholdMb(mb)
        scanRepository.applyThreshold(mb * 1024L * 1024L)
    }

    fun setSchedule(enabled: Boolean, hour: Int, minute: Int) = viewModelScope.launch {
        settingsRepository.setSchedule(enabled, hour, minute)
        val app = getApplication<Application>()
        if (enabled) ScanScheduler.schedule(app, hour, minute) else ScanScheduler.cancel(app)
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CleanerApp
                MainViewModel(app, app.container.settingsRepository, app.container.scanRepository)
            }
        }
    }
}
