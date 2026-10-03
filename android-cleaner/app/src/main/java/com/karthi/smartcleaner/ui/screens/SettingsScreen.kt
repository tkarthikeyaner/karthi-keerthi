package com.karthi.smartcleaner.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karthi.smartcleaner.BuildConfig
import com.karthi.smartcleaner.data.BackgroundReliability
import com.karthi.smartcleaner.data.ThemeMode
import com.karthi.smartcleaner.ui.MainViewModel
import com.karthi.smartcleaner.ui.formatBytes
import com.karthi.smartcleaner.ui.formatTime
import kotlin.math.roundToInt

private val THRESHOLD_STEPS_MB = listOf(5, 10, 25, 50, 100, 250, 500, 1000, 2000, 4000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    notificationsGranted: Boolean,
    onRequestNotifications: () -> Unit,
    onBack: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard("Large file threshold") {
                val initialIndex = THRESHOLD_STEPS_MB.indexOfFirst { it >= s.thresholdMb }.takeIf { it >= 0 }
                    ?: THRESHOLD_STEPS_MB.lastIndex
                var index by remember(s.thresholdMb) { mutableFloatStateOf(initialIndex.toFloat()) }
                val mb = THRESHOLD_STEPS_MB[index.roundToInt()]
                Text("Flag files ≥ ${formatBytes(mb * 1024L * 1024L)}", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = index,
                    onValueChange = { index = it },
                    onValueChangeFinished = { vm.setThresholdMb(THRESHOLD_STEPS_MB[index.roundToInt()]) },
                    valueRange = 0f..THRESHOLD_STEPS_MB.lastIndex.toFloat(),
                    steps = THRESHOLD_STEPS_MB.size - 2,
                )
            }

            SettingsCard("Daily scan") {
                ToggleRow(
                    title = "Scheduled scan",
                    subtitle = "Scan storage every day and send a summary notification",
                    checked = s.scheduleEnabled,
                    onChecked = { vm.setSchedule(it, s.scanHour, s.scanMinute) },
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = s.scheduleEnabled) { showTimePicker = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Scan time", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        formatTime(s.scanHour, s.scanMinute),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (s.scheduleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
                if (!notificationsGranted) {
                    TextButton(onClick = onRequestNotifications) { Text("Allow notifications for scan reports") }
                }
            }

            if (s.scheduleEnabled) BackgroundReliabilityCard()

            SettingsCard("Appearance") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = s.themeMode == mode,
                            onClick = { vm.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ToggleRow(
                        title = "Dynamic color",
                        subtitle = "Match your wallpaper (Material You)",
                        checked = s.dynamicColor,
                        onChecked = vm::setDynamicColor,
                    )
                }
            }

            Text(
                "Smart Cleaner ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(initialHour = s.scanHour, initialMinute = s.scanMinute)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Daily scan time") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    vm.setSchedule(true, pickerState.hour, pickerState.minute)
                    showTimePicker = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BackgroundReliabilityCard() {
    val context = LocalContext.current
    var exempt by remember { mutableStateOf(BackgroundReliability.isIgnoringBatteryOptimizations(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        exempt = BackgroundReliability.isIgnoringBatteryOptimizations(context)
    }
    if (exempt && !BackgroundReliability.isXiaomi) return

    SettingsCard("Background reliability") {
        Text(
            if (BackgroundReliability.isXiaomi) {
                "Xiaomi phones stop background apps aggressively. Allow both settings below or the daily scan may not run."
            } else {
                "Allow background running so the daily scan isn't delayed or skipped."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (exempt) "Battery optimisation: off ✓" else "Battery optimisation: on",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!exempt) {
                TextButton(onClick = {
                    try {
                        context.startActivity(BackgroundReliability.batteryExemptionIntent(context))
                    } catch (_: ActivityNotFoundException) {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }) { Text("Allow") }
            }
        }
        if (BackgroundReliability.isXiaomi) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Autostart (MIUI / HyperOS)", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { BackgroundReliability.openAutostartSettings(context) }) { Text("Open") }
            }
            Text(
                "Also: Settings › Apps › Smart Cleaner › Battery saver › No restrictions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
