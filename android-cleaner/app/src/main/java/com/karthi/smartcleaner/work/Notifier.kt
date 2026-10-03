package com.karthi.smartcleaner.work

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.karthi.smartcleaner.MainActivity
import com.karthi.smartcleaner.R
import com.karthi.smartcleaner.data.ScanResult
import com.karthi.smartcleaner.data.StoragePermission
import com.karthi.smartcleaner.ui.formatBytes

object Notifier {
    private const val CHANNEL_ID = "scan_reports"
    private const val SUMMARY_ID = 1001
    private const val PERMISSION_ID = 1002

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_scan_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_scan_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showScanSummary(context: Context, result: ScanResult) {
        val title = "Scan complete · ${formatBytes(result.totalBytes)} in ${result.files.size} files"
        val lines = listOf(
            "${result.newFilesCount} new files since last scan",
            "${result.largeFiles.size} large files (${formatBytes(result.largeFiles.sumOf { it.size })})",
            "${result.duplicateFileCount} duplicates · ${formatBytes(result.reclaimableDuplicateBytes)} reclaimable",
        )
        post(
            context,
            SUMMARY_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(lines.last())
                .setStyle(NotificationCompat.InboxStyle().also { style -> lines.forEach(style::addLine) }),
        )
    }

    fun showPermissionNeeded(context: Context) {
        post(
            context,
            PERMISSION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Daily scan skipped")
                .setContentText("Storage access was revoked. Tap to grant it again."),
        )
    }

    @SuppressLint("MissingPermission") // Checked via StoragePermission.canPostNotifications.
    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!StoragePermission.canPostNotifications(context)) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = builder
            .setSmallIcon(R.drawable.ic_stat_clean)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
