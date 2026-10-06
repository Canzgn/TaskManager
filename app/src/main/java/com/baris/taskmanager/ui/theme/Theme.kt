package com.baris.taskmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Lavender,
    onPrimaryContainer = DeepIndigo,
    secondary = Mint,
    onSecondary = Color.White,
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0F1F8),
    onSurfaceVariant = Muted,
    outline = Color(0xFFD4D6E3)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8B9FF),
    onPrimary = Color(0xFF23256B),
    primaryContainer = DeepIndigo,
    secondary = Color(0xFF68D9B4),
    background = Color(0xFF111321),
    surface = Color(0xFF1E2030),
    surfaceVariant = Color(0xFF2A2D40)
)

@Composable
fun TaskManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
