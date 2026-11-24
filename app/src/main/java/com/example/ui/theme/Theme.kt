package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val BitaxeDarkColorScheme = darkColorScheme(
    primary = NeonEmerald,
    onPrimary = Color(0xFF002110),
    primaryContainer = NeonEmeraldContainer,
    onPrimaryContainer = NeonEmerald,
    secondary = ElectricCyan,
    onSecondary = Color(0xFF001F2E),
    secondaryContainer = ElectricCyanContainer,
    onSecondaryContainer = ElectricCyan,
    tertiary = AmberCaution,
    onTertiary = Color(0xFF281800),
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberCaution,
    error = ThermalRed,
    onError = Color(0xFFFFFFFF),
    errorContainer = ThermalRedContainer,
    onErrorContainer = ThermalRed,
    background = ObsidianBlack,
    onBackground = SlateTextPrimary,
    surface = CharcoalBase,
    onSurface = SlateTextPrimary,
    surfaceVariant = CharcoalSurface,
    onSurfaceVariant = SlateTextSecondary,
    surfaceContainerLowest = ObsidianBlack,
    surfaceContainerLow = CharcoalBase,
    surfaceContainer = CharcoalSurface,
    surfaceContainerHigh = CharcoalElevated,
    surfaceContainerHighest = Color(0xFF222C3D),
    outline = CharcoalBorder,
    outlineVariant = Color(0xFF1D2636)
)

val BitaxeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun BitaxeAiTheme(
    isArabic: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BitaxeDarkColorScheme,
        typography = buildAppTypography(isArabic = isArabic),
        shapes = BitaxeShapes,
        content = content
    )
}
