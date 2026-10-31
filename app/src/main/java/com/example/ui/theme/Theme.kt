package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HyperCleanDarkColorScheme = darkColorScheme(
    primary = NeonCyanPrimary,
    onPrimary = OnNeonCyan,
    primaryContainer = NeonCyanContainer,
    onPrimaryContainer = NeonCyanPrimary,
    secondary = ElectricIndigoSecondary,
    onSecondary = OnElectricIndigo,
    secondaryContainer = ElectricIndigoContainer,
    onSecondaryContainer = Color(0xFFC7D2FE),
    tertiary = EmeraldSafe,
    onTertiary = Color(0xFF002116),
    tertiaryContainer = EmeraldSafeContainer,
    onTertiaryContainer = Color(0xFF6EE7B7),
    error = CoralDanger,
    onError = Color.White,
    errorContainer = CoralDangerContainer,
    onErrorContainer = Color(0xFFFECDD3),
    background = ObsidianBackground,
    onBackground = TextPrimaryLight,
    surface = GraphiteSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = GraphiteSurfaceVariant,
    onSurfaceVariant = TextSecondaryMuted,
    outline = OutlineSubtle
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HyperCleanDarkColorScheme,
        typography = Typography,
        content = content
    )
}
