package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val HyperCleanColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = PaperCanvas,
    primaryContainer = SignalLime,
    onPrimaryContainer = Ink,
    inversePrimary = SignalLime,

    secondary = ProtectedGreen,
    onSecondary = Color.White,
    secondaryContainer = ProtectedSoft,
    onSecondaryContainer = ProtectedGreen,

    tertiary = VioletNote,
    onTertiary = Color.White,
    tertiaryContainer = VioletSoft,
    onTertiaryContainer = VioletNote,

    error = AlertCoral,
    onError = Color.White,
    errorContainer = AlertCoralSoft,
    onErrorContainer = AlertCoralDeep,

    background = PaperCanvas,
    onBackground = Ink,
    surface = PaperSurface,
    onSurface = Ink,
    surfaceVariant = PaperTint,
    onSurfaceVariant = InkMuted,
    surfaceTint = SignalLime,
    surfaceContainer = PaperTint,
    surfaceContainerHigh = PaperSurface,
    surfaceContainerLow = PaperTint,

    outline = HairlineStrong,
    outlineVariant = Hairline,

    inverseSurface = ConsoleInk,
    inverseOnSurface = ConsoleText,
    scrim = Ink
)

val HyperShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HyperCleanColorScheme,
        typography = HyperTypography,
        shapes = HyperShapes,
        content = content
    )
}
