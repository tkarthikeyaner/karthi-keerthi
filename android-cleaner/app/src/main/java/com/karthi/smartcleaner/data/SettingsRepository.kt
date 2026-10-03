package com.karthi.smartcleaner.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val thresholdMb: Int = 100,
    val scheduleEnabled: Boolean = true,
    val scanHour: Int = 21,
    val scanMinute: Int = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val lastScanAt: Long = 0L,
) {
    val thresholdBytes: Long get() = thresholdMb * 1024L * 1024L
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            val defaults = AppSettings()
            AppSettings(
                thresholdMb = p[THRESHOLD_MB] ?: defaults.thresholdMb,
                scheduleEnabled = p[SCHEDULE_ENABLED] ?: defaults.scheduleEnabled,
                scanHour = p[SCAN_HOUR] ?: defaults.scanHour,
                scanMinute = p[SCAN_MINUTE] ?: defaults.scanMinute,
                themeMode = p[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: defaults.themeMode,
                dynamicColor = p[DYNAMIC_COLOR] ?: defaults.dynamicColor,
                lastScanAt = p[LAST_SCAN_AT] ?: defaults.lastScanAt,
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThresholdMb(value: Int) = store.edit { it[THRESHOLD_MB] = value.coerceIn(1, 100_000) }

    suspend fun setSchedule(enabled: Boolean, hour: Int, minute: Int) = store.edit {
        it[SCHEDULE_ENABLED] = enabled
        it[SCAN_HOUR] = hour.coerceIn(0, 23)
        it[SCAN_MINUTE] = minute.coerceIn(0, 59)
    }

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[THEME_MODE] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[DYNAMIC_COLOR] = enabled }

    suspend fun setLastScanAt(timestamp: Long) = store.edit { it[LAST_SCAN_AT] = timestamp }

    private companion object {
        val THRESHOLD_MB = intPreferencesKey("threshold_mb")
        val SCHEDULE_ENABLED = booleanPreferencesKey("schedule_enabled")
        val SCAN_HOUR = intPreferencesKey("scan_hour")
        val SCAN_MINUTE = intPreferencesKey("scan_minute")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LAST_SCAN_AT = longPreferencesKey("last_scan_at")
    }
}
