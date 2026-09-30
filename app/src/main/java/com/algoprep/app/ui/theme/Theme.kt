package com.algoprep.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = VioletDeep,
    onPrimary = Color.White,
    primaryContainer = PaperHigh,
    onPrimaryContainer = VioletDeep,
    secondary = LimeDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9FFB0),
    onSecondaryContainer = Color(0xFF233600),
    tertiary = PinkDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E8),
    onTertiaryContainer = Color(0xFF52002B),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperHigh,
    onSurfaceVariant = Color(0xFF555070),
    surfaceContainerLowest = PaperRaised,
    surfaceContainerLow = PaperRaised,
    surfaceContainer = PaperHigh,
    surfaceContainerHigh = PaperHigh,
    surfaceContainerHighest = Color(0xFFE2DEFF),
    outline = Color(0xFF8B86A8),
    outlineVariant = Color(0xFFD6D1F2),
)

private val DarkColors = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A1F6B),
    onPrimaryContainer = VioletSoft,
    secondary = Lime,
    onSecondary = Color(0xFF1B2900),
    secondaryContainer = Color(0xFF2E4300),
    onSecondaryContainer = Lime,
    tertiary = Pink,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF5B1438),
    onTertiaryContainer = Color(0xFFFFB3D2),
    background = Ink,
    onBackground = Color(0xFFF1F0FF),
    surface = Ink,
    onSurface = Color(0xFFF1F0FF),
    surfaceVariant = InkHigh,
    onSurfaceVariant = Color(0xFFB4B0CC),
    surfaceContainerLowest = Ink,
    surfaceContainerLow = InkRaised,
    surfaceContainer = InkRaised,
    surfaceContainerHigh = InkHigh,
    surfaceContainerHighest = Color(0xFF2A2A3A),
    outline = Color(0xFF7C7896),
    outlineVariant = InkOutline,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Signature gradient used by hero cards and the primary call to action. */
val HeroBrush: Brush = Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFFB344FF), Color(0xFFFF4D9D)))

@Composable
fun AlgoPrepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
