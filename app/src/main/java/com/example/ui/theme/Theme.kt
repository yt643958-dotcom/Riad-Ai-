package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val RiadAiColorScheme = darkColorScheme(
    primary = MatrixGreen,
    onPrimary = VoidBackground,
    primaryContainer = MatrixGreenSubtle,
    onPrimaryContainer = MatrixGreen,
    secondary = MatrixGreenDim,
    onSecondary = VoidBackground,
    secondaryContainer = SurfaceVariantDark,
    onSecondaryContainer = TextPrimary,
    tertiary = SeverityLow,
    onTertiary = VoidBackground,
    background = VoidBackground,
    onBackground = TextPrimary,
    surface = ElevatedSurface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextMuted,
    outline = SubtleBorder,
    outlineVariant = SubtleBorder,
    error = AlertRed,
    onError = VoidBackground,
    errorContainer = AlertRed.copy(alpha = 0.14f),
    onErrorContainer = AlertRed
)

private val RiadAiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun RiadAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RiadAiColorScheme,
        typography = RiadAiTypography,
        shapes = RiadAiShapes,
        content = content
    )
}
