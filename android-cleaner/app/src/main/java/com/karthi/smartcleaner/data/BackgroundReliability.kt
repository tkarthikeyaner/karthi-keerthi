package com.karthi.smartcleaner.data

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Xiaomi (MIUI / HyperOS) and some other OEMs kill background work unless the app is exempt from
 * battery optimisation and allowed to autostart. Without this the daily scan may never fire.
 */
object BackgroundReliability {

    private val XIAOMI_BRANDS = setOf("xiaomi", "redmi", "poco")

    val isXiaomi: Boolean
        get() = Build.MANUFACTURER.lowercase() in XIAOMI_BRANDS || Build.BRAND.lowercase() in XIAOMI_BRANDS

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** Direct system prompt; sideloaded app, so the Play policy restriction on this intent doesn't apply. */
    @SuppressLint("BatteryLife")
    fun batteryExemptionIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    /** Candidate screens for MIUI/HyperOS Autostart, most specific first; ends with app details. */
    fun autostartIntents(context: Context): List<Intent> = listOf(
        Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ),
        Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )

    /** Launches the first autostart screen that resolves; returns false if none could be opened. */
    fun openAutostartSettings(context: Context): Boolean {
        for (intent in autostartIntents(context)) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
                // ActivityNotFoundException or SecurityException on non-MIUI builds; try the next one.
            }
        }
        return false
    }
}
