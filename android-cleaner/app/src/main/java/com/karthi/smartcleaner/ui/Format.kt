package com.karthi.smartcleaner.ui

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceAtMost(4)
    val value = bytes / 1024.0.pow(exp)
    val unit = "KMGT"[exp - 1]
    return String.format(Locale.getDefault(), if (value >= 100) "%.0f %sB" else "%.1f %sB", value, unit)
}

fun formatDate(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))

fun formatTime(hour: Int, minute: Int): String = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
