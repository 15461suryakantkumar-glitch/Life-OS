package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class LifeOsThemeMode(
    val displayName: String,
    val subtitle: String,
    val isDark: Boolean,
    val previewPrimary: Color,
    val previewSecondary: Color,
    val previewSurface: Color
) {
    LIGHT(
        displayName = "Lavender Mist",
        subtitle = "Soft lavender mist, bright white cards & vibrant violet",
        isDark = false,
        previewPrimary = VioletPrimaryCta,
        previewSecondary = VioletBrightAccent,
        previewSurface = LavenderMistBg
    ),
    DARK(
        displayName = "Velvet Plum",
        subtitle = "Deep evening plum, luminous lavender & peach",
        isDark = true,
        previewPrimary = VelvetLavenderPrimary,
        previewSecondary = VelvetSoftPeach,
        previewSurface = VelvetPlumCard
    ),
    SYSTEM(
        displayName = "System",
        subtitle = "Follows your Android Light & Dark appearance",
        isDark = false,
        previewPrimary = VioletPrimaryCta,
        previewSecondary = DeepLavender,
        previewSurface = LavenderMistBase
    );

    fun resolvesToDark(systemDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemDark
    }

    companion object {
        fun fromStored(raw: String?): LifeOsThemeMode {
            if (raw.isNullOrBlank()) return LIGHT
            entries.firstOrNull { it.name == raw }?.let { return it }
            return when (raw) {
                "DARK", "MIDNIGHT_AURORA", "VIOLET_CYAN", "CYBER_INDIGO", "AURORA_PULSE" -> DARK
                "SYSTEM" -> SYSTEM
                "LIGHT", "MATCHA_MIST", "ALABASTER_CLOUD", "WARM_LINEN", "HYBRID_LAVENDER" -> LIGHT
                else -> LIGHT
            }
        }
    }
}

@Immutable
data class LifeOsThemeTokens(
    val mode: LifeOsThemeMode,
    val isDark: Boolean,
    val background: Color,
    val surfaceBase: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val surfaceHighlight: Color,
    val borderSubtle: Color,
    val borderAccent: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkMuted: Color,
    val primary: Color,
    val deepPrimary: Color,
    val secondary: Color,
    val tertiary: Color,
    val peach: Color,
    val rose: Color,
    val cardRadius: Dp,
    val heroRadius: Dp,
    val smallRadius: Dp,
    val pillRadius: Dp,
    val cardBorderWidth: Dp,
    val cardElevation: Dp,
    val typographyLabel: String
)

private val LavenderLightTokens = LifeOsThemeTokens(
    mode = LifeOsThemeMode.LIGHT,
    isDark = false,
    background = LavenderMistBg,
    surfaceBase = LavenderMistBase,
    surfaceCard = PureWhiteCard,
    surfaceElevated = LavenderCardElevated,
    surfaceHighlight = LavenderCardHighlight,
    borderSubtle = LavenderBorderSubtle,
    borderAccent = LavenderBorderAccent,
    inkPrimary = LavenderHeadingPlum,
    inkSecondary = LavenderSlateSecondary,
    inkMuted = LavenderSlateMuted,
    primary = VioletPrimaryCta,
    deepPrimary = FeeloraPrimaryCtaDark,
    secondary = VioletBrightAccent,
    tertiary = MutedRose,
    peach = SoftPeach,
    rose = MutedRose,
    cardRadius = 22.dp,
    heroRadius = 28.dp,
    smallRadius = 14.dp,
    pillRadius = 50.dp,
    cardBorderWidth = 1.dp,
    cardElevation = 0.dp,
    typographyLabel = "Lavender Mist"
)

private val VelvetDarkTokens = LifeOsThemeTokens(
    mode = LifeOsThemeMode.DARK,
    isDark = true,
    background = VelvetPlumBg,
    surfaceBase = VelvetPlumBase,
    surfaceCard = VelvetPlumCard,
    surfaceElevated = VelvetPlumElevated,
    surfaceHighlight = VelvetPlumHighlight,
    borderSubtle = VelvetPlumBorderSubtle,
    borderAccent = VelvetPlumBorderAccent,
    inkPrimary = VelvetInkPrimary,
    inkSecondary = VelvetInkSecondary,
    inkMuted = VelvetInkMuted,
    primary = VelvetLavenderPrimary,
    deepPrimary = VelvetDeepLavender,
    secondary = VelvetSoftPeach,
    tertiary = VelvetMutedRose,
    peach = VelvetSoftPeach,
    rose = VelvetMutedRose,
    cardRadius = 22.dp,
    heroRadius = 28.dp,
    smallRadius = 14.dp,
    pillRadius = 50.dp,
    cardBorderWidth = 1.dp,
    cardElevation = 0.dp,
    typographyLabel = "Velvet Plum"
)

val LocalLifeOsTokens = staticCompositionLocalOf { LavenderLightTokens }

object LifeOsThemeTokensProvider {
    val current: LifeOsThemeTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalLifeOsTokens.current
}

fun tokensForMode(
    mode: LifeOsThemeMode,
    systemDark: Boolean = false
): LifeOsThemeTokens = if (mode.resolvesToDark(systemDark)) {
    VelvetDarkTokens.copy(mode = mode)
} else {
    LavenderLightTokens.copy(mode = mode)
}

@Composable
fun LifeOsTheme(
    themeMode: LifeOsThemeMode = LifeOsThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val tokens = tokensForMode(themeMode, systemDark)
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(tokens.smallRadius * 0.7f),
        small = RoundedCornerShape(tokens.smallRadius),
        medium = RoundedCornerShape(tokens.cardRadius),
        large = RoundedCornerShape(tokens.heroRadius),
        extraLarge = RoundedCornerShape(tokens.heroRadius + 6.dp)
    )

    val colorScheme = if (tokens.isDark) {
        darkColorScheme(
            primary = tokens.primary,
            onPrimary = VelvetPlumBg,
            primaryContainer = tokens.surfaceHighlight,
            onPrimaryContainer = tokens.inkPrimary,
            secondary = tokens.secondary,
            onSecondary = VelvetPlumBg,
            secondaryContainer = tokens.peach.copy(alpha = 0.16f),
            onSecondaryContainer = tokens.inkPrimary,
            tertiary = tokens.tertiary,
            onTertiary = VelvetPlumBg,
            background = tokens.background,
            onBackground = tokens.inkPrimary,
            surface = tokens.surfaceBase,
            onSurface = tokens.inkPrimary,
            surfaceVariant = tokens.surfaceCard,
            onSurfaceVariant = tokens.inkSecondary,
            surfaceContainerLowest = tokens.background,
            surfaceContainerLow = tokens.surfaceBase,
            surfaceContainer = tokens.surfaceCard,
            surfaceContainerHigh = tokens.surfaceElevated,
            surfaceContainerHighest = tokens.surfaceHighlight,
            outline = tokens.borderSubtle,
            outlineVariant = tokens.borderAccent,
            error = RoseAlert,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = VioletPrimaryCta,
            onPrimary = Color.White,
            primaryContainer = LavenderCardHighlight,
            onPrimaryContainer = LavenderHeadingPlum,
            secondary = VioletBrightAccent,
            onSecondary = Color.White,
            secondaryContainer = LavenderCardElevated,
            onSecondaryContainer = LavenderHeadingPlum,
            tertiary = MutedRose,
            onTertiary = LavenderHeadingPlum,
            background = LavenderMistBg,
            onBackground = LavenderHeadingPlum,
            surface = LavenderMistBase,
            onSurface = LavenderHeadingPlum,
            surfaceVariant = LavenderCardElevated,
            onSurfaceVariant = LavenderSlateSecondary,
            surfaceContainerLowest = LavenderMistBg,
            surfaceContainerLow = LavenderMistBase,
            surfaceContainer = PureWhiteCard,
            surfaceContainerHigh = LavenderCardElevated,
            surfaceContainerHighest = LavenderCardHighlight,
            outline = LavenderBorderSubtle,
            outlineVariant = LavenderBorderAccent,
            error = RoseAlert,
            onError = Color.White
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !tokens.isDark
                controller.isAppearanceLightNavigationBars = !tokens.isDark
            }
        }
    }

    CompositionLocalProvider(LocalLifeOsTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FeeloraTypography,
            shapes = shapes,
            content = content
        )
    }
}
