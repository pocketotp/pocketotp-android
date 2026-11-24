package com.pocketotp.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Fixed brand palette by default. Wallpaper/dynamic color is deliberately not enabled.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6B5C), onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F2E0), onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4B635B), secondaryContainer = Color(0xFFCEE9DD),
    background = Color(0xFFF5FAF8), onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF5FAF8), onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDCE5E0), onSurfaceVariant = Color(0xFF3F4946),
    surfaceContainer = Color(0xFFE9EFEC), surfaceContainerLow = Color(0xFFEFF5F2),
    outline = Color(0xFF6F7975), outlineVariant = Color(0xFFBEC9C5),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD5C4), onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005046), onPrimaryContainer = Color(0xFFA7F2E0),
    secondary = Color(0xFFB2CCC1), secondaryContainer = Color(0xFF344C43),
    background = Color(0xFF0E1513), onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0E1513), onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF3F4946), onSurfaceVariant = Color(0xFFBFC9C5),
    surfaceContainer = Color(0xFF1E2624), surfaceContainerLow = Color(0xFF161D1B),
    outline = Color(0xFF89938F), outlineVariant = Color(0xFF3F4946),
)

@Composable
fun PocketOtpTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
