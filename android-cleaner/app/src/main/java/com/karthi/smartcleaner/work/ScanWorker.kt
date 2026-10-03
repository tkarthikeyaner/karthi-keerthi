package com.karthi.smartcleaner.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.karthi.smartcleaner.CleanerApp
import com.karthi.smartcleaner.data.StoragePermission
import kotlinx.coroutines.CancellationException

class ScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CleanerApp).container
        val settings = container.settingsRepository.current()
        try {
            if (!StoragePermission.isGranted(applicationContext)) {
                Notifier.showPermissionNeeded(applicationContext)
            } else {
                val result = container.scanRepository.scan()
                Notifier.showScanSummary(applicationContext, result)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed scan must not break the daily chain; log and try again tomorrow.
            Log.e(TAG, "Scheduled scan failed", e)
        } finally {
            if (settings.scheduleEnabled && !isStopped) {
                ScanScheduler.schedule(applicationContext, settings.scanHour, settings.scanMinute, fromWorker = true)
            }
        }
        return Result.success()
    }

    private companion object {
        const val TAG = "ScanWorker"
    }
}
