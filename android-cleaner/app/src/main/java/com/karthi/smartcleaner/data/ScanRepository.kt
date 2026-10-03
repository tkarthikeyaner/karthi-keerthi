package com.karthi.smartcleaner.data

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.karthi.smartcleaner.scanner.DuplicateDetector
import com.karthi.smartcleaner.scanner.FileScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ScanPhase {
    data object Idle : ScanPhase
    data class Walking(val filesFound: Int) : ScanPhase
    data object FindingDuplicates : ScanPhase
}

data class ScanState(
    val phase: ScanPhase = ScanPhase.Idle,
    val result: ScanResult? = null,
    val error: String? = null,
) {
    val isScanning: Boolean get() = phase != ScanPhase.Idle
}

/**
 * Single source of truth for scan results, shared by the UI and [com.karthi.smartcleaner.work.ScanWorker].
 * A mutex guarantees only one scan runs at a time even if the scheduled job and a manual scan overlap.
 */
class ScanRepository(
    private val context: Context,
    private val settings: SettingsRepository,
    private val detector: DuplicateDetector,
) {
    private val scanMutex = Mutex()
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    suspend fun scan(): ScanResult = scanMutex.withLock {
        val started = System.currentTimeMillis()
        val config = settings.current()
        _state.update { it.copy(phase = ScanPhase.Walking(0), error = null) }
        try {
            val root = Environment.getExternalStorageDirectory()
            val files = FileScanner(root).scan { found ->
                _state.update { it.copy(phase = ScanPhase.Walking(found)) }
            }
            _state.update { it.copy(phase = ScanPhase.FindingDuplicates) }
            val duplicates = detector.find(files)
            val result = ScanResult(
                files = files,
                largeFiles = files.filter { it.size >= config.thresholdBytes }.sortedByDescending { it.size },
                duplicateGroups = duplicates,
                newFilesCount = if (config.lastScanAt == 0L) files.size else files.count { it.lastModified > config.lastScanAt },
                thresholdBytes = config.thresholdBytes,
                scannedAt = started,
                durationMs = System.currentTimeMillis() - started,
            )
            settings.setLastScanAt(started)
            _state.update { ScanState(phase = ScanPhase.Idle, result = result) }
            result
        } catch (e: CancellationException) {
            _state.update { it.copy(phase = ScanPhase.Idle) }
            throw e
        } catch (t: Throwable) {
            _state.update { it.copy(phase = ScanPhase.Idle, error = t.message ?: t.javaClass.simpleName) }
            throw t
        }
    }

    /** Re-applies a new size threshold to the cached result without rescanning storage. */
    fun applyThreshold(thresholdBytes: Long) {
        _state.update { state ->
            val result = state.result ?: return@update state
            state.copy(
                result = result.copy(
                    largeFiles = result.files.filter { it.size >= thresholdBytes }.sortedByDescending { it.size },
                    thresholdBytes = thresholdBytes,
                ),
            )
        }
    }

    suspend fun delete(targets: Collection<ScannedFile>): DeleteOutcome = withContext(Dispatchers.IO) {
        val deleted = HashSet<String>()
        val failed = mutableListOf<String>()
        var freed = 0L
        for (file in targets) {
            val f = File(file.path)
            val ok = try {
                !f.exists() || f.delete()
            } catch (_: SecurityException) {
                false
            }
            if (ok) {
                deleted += file.path
                freed += file.size
            } else {
                failed += file.name
            }
        }
        if (deleted.isNotEmpty()) {
            // Let MediaStore drop the removed entries so gallery apps stop showing them.
            MediaScannerConnection.scanFile(context, deleted.toTypedArray(), null, null)
            _state.update { s -> s.copy(result = s.result?.without(deleted)) }
        }
        DeleteOutcome(deleted, failed, freed)
    }
}
