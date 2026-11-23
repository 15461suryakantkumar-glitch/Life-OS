package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeAccentPreset(val label: String, val primary: Color, val secondary: Color) {
    VIOLET_CYAN("Electric Violet & Cyan", ElectricViolet, ElectricCyan),
    CYBER_INDIGO("Cyber Indigo & Teal", CyberIndigo, AuroraTeal),
    AURORA_PULSE("Aurora Pink & Violet", AuroraPink, ElectricViolet)
}

val LifeOsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun LifeOsTheme(
    accentPreset: ThemeAccentPreset = ThemeAccentPreset.VIOLET_CYAN,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = accentPreset.primary,
        onPrimary = Color.White,
        primaryContainer = accentPreset.primary.copy(alpha = 0.22f),
        onPrimaryContainer = ElectricVioletSoft,
        secondary = accentPreset.secondary,
        onSecondary = ObsidianVoid,
        secondaryContainer = accentPreset.secondary.copy(alpha = 0.18f),
        onSecondaryContainer = ElectricCyanSoft,
        tertiary = EmeraldSuccess,
        onTertiary = ObsidianVoid,
        background = ObsidianVoid,
        onBackground = InkPrimary,
        surface = ObsidianBase,
        onSurface = InkPrimary,
        surfaceVariant = CharcoalSurface,
        onSurfaceVariant = InkSecondary,
        surfaceContainerLowest = ObsidianVoid,
        surfaceContainerLow = ObsidianBase,
        surfaceContainer = CharcoalSurface,
        surfaceContainerHigh = CharcoalElevated,
        surfaceContainerHighest = CharcoalHighlight,
        outline = BorderHairline,
        outlineVariant = BorderGlow,
        error = RoseAlert,
        onError = Color.White
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LifeOsTypography,
        shapes = LifeOsShapes,
        content = content
    )
}
