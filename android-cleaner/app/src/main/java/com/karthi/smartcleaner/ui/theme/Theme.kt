package com.karthi.smartcleaner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.karthi.smartcleaner.data.FileCategory
import com.karthi.smartcleaner.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6EF3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001A43),
    secondary = Color(0xFF575E71),
    secondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFF00897B),
    tertiaryContainer = Color(0xFFB2DFDB),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF8FAFF),
    surface = Color(0xFFF8FAFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB0C6FF),
    onPrimary = Color(0xFF002D6C),
    primaryContainer = Color(0xFF004398),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFBFC6DC),
    secondaryContainer = Color(0xFF3F4759),
    tertiary = Color(0xFF80CBC4),
    tertiaryContainer = Color(0xFF00504A),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
)

/** Fixed accent per category so cards stay recognizable under dynamic color. */
fun FileCategory.accent(dark: Boolean): Color = when (this) {
    FileCategory.PHOTOS -> if (dark) Color(0xFFFFB86C) else Color(0xFFE67E22)
    FileCategory.VIDEOS -> if (dark) Color(0xFFFF8A9A) else Color(0xFFD63655)
    FileCategory.AUDIO -> if (dark) Color(0xFFCE93D8) else Color(0xFF8E44AD)
    FileCategory.DOCUMENTS -> if (dark) Color(0xFF82B1FF) else Color(0xFF2962FF)
    FileCategory.OTHER -> if (dark) Color(0xFFB0BEC5) else Color(0xFF607D8B)
}

@Composable
fun isAppInDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun SmartCleanerTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = isAppInDarkTheme(themeMode)
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
