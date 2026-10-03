package com.karthi.smartcleaner.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Exact-time daily scheduling: a one-time request delayed until the next HH:MM, which the worker
 * re-arms after each run. Unlike a 24h PeriodicWorkRequest this never drifts from the chosen time.
 */
object ScanScheduler {
    private const val UNIQUE_NAME = "daily_storage_scan"

    /** Reschedules after the user changes the time. */
    fun schedule(context: Context, hour: Int, minute: Int, fromWorker: Boolean = false) {
        // From inside the running worker, REPLACE would cancel that worker; append the next run instead.
        val policy = if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        enqueue(context, hour, minute, policy)
    }

    /** Called on app start: arms the schedule if missing without disturbing a pending or running scan. */
    fun ensureScheduled(context: Context, hour: Int, minute: Int) =
        enqueue(context, hour, minute, ExistingWorkPolicy.KEEP)

    private fun enqueue(context: Context, hour: Int, minute: Int, policy: ExistingWorkPolicy) {
        val delay = delayUntilNext(LocalDateTime.now(), hour, minute)
        val request = OneTimeWorkRequestBuilder<ScanWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, policy, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }

    fun delayUntilNext(now: LocalDateTime, hour: Int, minute: Int): Duration {
        var next = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
