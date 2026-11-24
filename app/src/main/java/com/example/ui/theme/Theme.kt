package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
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
    MIDNIGHT_AURORA(
        displayName = "Midnight Aurora",
        subtitle = "Dark · Luminous glass & sleek edges",
        isDark = true,
        previewPrimary = AuroraPrimary,
        previewSecondary = AuroraSecondary,
        previewSurface = AuroraCard
    ),
    ALABASTER_CLOUD(
        displayName = "Alabaster Cloud",
        subtitle = "Light · Airy soft-pill elevation",
        isDark = false,
        previewPrimary = AlabasterPrimary,
        previewSecondary = AlabasterSecondary,
        previewSurface = AlabasterCard
    ),
    MATCHA_MIST(
        displayName = "Matcha Mist",
        subtitle = "Light · Crisp botanical studio",
        isDark = false,
        previewPrimary = MatchaPrimary,
        previewSecondary = MatchaSecondary,
        previewSurface = MatchaCard
    ),
    WARM_LINEN(
        displayName = "Warm Linen",
        subtitle = "Light · Classic serif & warm paper",
        isDark = false,
        previewPrimary = LinenPrimary,
        previewSecondary = LinenSecondary,
        previewSurface = LinenCard
    );

    companion object {
        fun fromStored(raw: String?): LifeOsThemeMode {
            if (raw.isNullOrBlank()) return MIDNIGHT_AURORA
            entries.firstOrNull { it.name == raw }?.let { return it }
            return when (raw) {
                "VIOLET_CYAN", "CYBER_INDIGO", "AURORA_PULSE" -> MIDNIGHT_AURORA
                else -> MIDNIGHT_AURORA
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
    val secondary: Color,
    val cardRadius: Dp,
    val heroRadius: Dp,
    val smallRadius: Dp,
    val pillRadius: Dp,
    val cardBorderWidth: Dp,
    val cardElevation: Dp,
    val typographyLabel: String
)

private val MidnightAuroraTokens = LifeOsThemeTokens(
    mode = LifeOsThemeMode.MIDNIGHT_AURORA,
    isDark = true,
    background = AuroraVoid,
    surfaceBase = AuroraBase,
    surfaceCard = AuroraCard,
    surfaceElevated = AuroraElevated,
    surfaceHighlight = AuroraHighlight,
    borderSubtle = AuroraBorder,
    borderAccent = AuroraBorderGlow,
    inkPrimary = AuroraInkPrimary,
    inkSecondary = AuroraInkSecondary,
    inkMuted = AuroraInkMuted,
    primary = AuroraPrimary,
    secondary = AuroraSecondary,
    cardRadius = 16.dp,
    heroRadius = 20.dp,
    smallRadius = 10.dp,
    pillRadius = 10.dp,
    cardBorderWidth = 1.dp,
    cardElevation = 0.dp,
    typographyLabel = "Geometric Tech"
)

private val AlabasterCloudTokens = LifeOsThemeTokens(
    mode = LifeOsThemeMode.ALABASTER_CLOUD,
    isDark = false,
    background = AlabasterBg,
    surfaceBase = AlabasterBase,
    surfaceCard = AlabasterCard,
    surfaceElevated = AlabasterElevated,
    surfaceHighlight = AlabasterHighlight,
    borderSubtle = AlabasterBorder,
    borderAccent = AlabasterBorderGlow,
    inkPrimary = AlabasterInkPrimary,
    inkSecondary = AlabasterInkSecondary,
    inkMuted = AlabasterInkMuted,
    primary = AlabasterPrimary,
    secondary = AlabasterSecondary,
    cardRadius = 24.dp,
    heroRadius = 28.dp,
    smallRadius = 14.dp,
    pillRadius = 50.dp,
    cardBorderWidth = 1.dp,
    cardElevation = 3.dp,
    typographyLabel = "Airy Minimalist"
)

private val MatchaMistTokens = LifeOsThemeTokens(
    mode = LifeOsThemeMode.MATCHA_MIST,
    isDark = false,
    background = MatchaBg,
    surfaceBase = MatchaBase,
    surfaceCard = MatchaCard,
    surfaceElevated = MatchaElevated,
    surfaceHighlight = MatchaHighlight,
    borderSubtle = MatchaBorder,
    borderAccent = MatchaBorderGlow,
    inkPrimary = MatchaInkPrimary,
    inkSecondary = MatchaInkSecondary,
    inkMuted = MatchaInkMuted,
    primary = MatchaPrimary,
    secondary = MatchaSecondary,
    cardRadius = 12.dp,
    heroRadius = 16.dp,
    smallRadius = 8.dp,
    pillRadius = 8.dp,
    cardBorderWidth = 1.2.dp,
    cardElevation = 1.dp,
    typographyLabel = "Botanical Studio"
)

private val WarmLinenTokens = LifeOsThemeTokens(
    mode = LifeOsThemeMode.WARM_LINEN,
    isDark = false,
    background = LinenBg,
    surfaceBase = LinenBase,
    surfaceCard = LinenCard,
    surfaceElevated = LinenElevated,
    surfaceHighlight = LinenHighlight,
    borderSubtle = LinenBorder,
    borderAccent = LinenBorderGlow,
    inkPrimary = LinenInkPrimary,
    inkSecondary = LinenInkSecondary,
    inkMuted = LinenInkMuted,
    primary = LinenPrimary,
    secondary = LinenSecondary,
    cardRadius = 18.dp,
    heroRadius = 22.dp,
    smallRadius = 12.dp,
    pillRadius = 14.dp,
    cardBorderWidth = 1.dp,
    cardElevation = 2.dp,
    typographyLabel = "Editorial Serif"
)

val LocalLifeOsTokens = staticCompositionLocalOf { MidnightAuroraTokens }

object LifeOsThemeTokensProvider {
    val current: LifeOsThemeTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalLifeOsTokens.current
}

fun tokensForMode(mode: LifeOsThemeMode): LifeOsThemeTokens = when (mode) {
    LifeOsThemeMode.MIDNIGHT_AURORA -> MidnightAuroraTokens
    LifeOsThemeMode.ALABASTER_CLOUD -> AlabasterCloudTokens
    LifeOsThemeMode.MATCHA_MIST -> MatchaMistTokens
    LifeOsThemeMode.WARM_LINEN -> WarmLinenTokens
}

private fun typographyForMode(mode: LifeOsThemeMode): Typography = when (mode) {
    LifeOsThemeMode.MIDNIGHT_AURORA -> MidnightAuroraTypography
    LifeOsThemeMode.ALABASTER_CLOUD -> AlabasterCloudTypography
    LifeOsThemeMode.MATCHA_MIST -> MatchaMistTypography
    LifeOsThemeMode.WARM_LINEN -> WarmLinenTypography
}

@Composable
fun LifeOsTheme(
    themeMode: LifeOsThemeMode = LifeOsThemeMode.MIDNIGHT_AURORA,
    content: @Composable () -> Unit
) {
    val tokens = tokensForMode(themeMode)
    val typography = typographyForMode(themeMode)
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
            onPrimary = Color.White,
            primaryContainer = tokens.primary.copy(alpha = 0.22f),
            onPrimaryContainer = tokens.inkPrimary,
            secondary = tokens.secondary,
            onSecondary = tokens.background,
            secondaryContainer = tokens.secondary.copy(alpha = 0.18f),
            onSecondaryContainer = tokens.inkPrimary,
            tertiary = EmeraldSuccess,
            onTertiary = tokens.background,
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
            primary = tokens.primary,
            onPrimary = Color.White,
            primaryContainer = tokens.primary.copy(alpha = 0.14f),
            onPrimaryContainer = tokens.primary,
            secondary = tokens.secondary,
            onSecondary = Color.White,
            secondaryContainer = tokens.secondary.copy(alpha = 0.14f),
            onSecondaryContainer = tokens.inkPrimary,
            tertiary = EmeraldSuccess,
            onTertiary = Color.White,
            background = tokens.background,
            onBackground = tokens.inkPrimary,
            surface = tokens.surfaceBase,
            onSurface = tokens.inkPrimary,
            surfaceVariant = tokens.surfaceElevated,
            onSurfaceVariant = tokens.inkSecondary,
            surfaceContainerLowest = tokens.surfaceBase,
            surfaceContainerLow = tokens.background,
            surfaceContainer = tokens.surfaceCard,
            surfaceContainerHigh = tokens.surfaceElevated,
            surfaceContainerHighest = tokens.surfaceHighlight,
            outline = tokens.borderSubtle,
            outlineVariant = tokens.borderAccent,
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
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}
