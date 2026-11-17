package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MaxMotionDarkScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = OledBlack,
    primaryContainer = NeonCyanDim,
    onPrimaryContainer = TextPrimary,
    secondary = ElectricPurple,
    onSecondary = TextPrimary,
    secondaryContainer = StudioElevated,
    onSecondaryContainer = ElectricPurpleLight,
    tertiary = CacheEmerald,
    onTertiary = OledBlack,
    background = OledBlack,
    onBackground = TextPrimary,
    surface = StudioPanel,
    onSurface = TextPrimary,
    surfaceVariant = StudioElevated,
    onSurfaceVariant = TextSecondary,
    outline = StudioBorder,
    outlineVariant = StudioBorder,
    error = AlertRed,
    onError = TextPrimary
)

val MaxMotionShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun MaxMotionTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MaxMotionDarkScheme,
        typography = MaxMotionTypography,
        shapes = MaxMotionShapes,
        content = content
    )
}
