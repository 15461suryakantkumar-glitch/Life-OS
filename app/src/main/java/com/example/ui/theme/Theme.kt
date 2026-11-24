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
    HYBRID_LAVENDER(
        displayName = "Hybrid Lavender",
        subtitle = "Default · Velvet plum, muted lavender & warm cream",
        isDark = true,
        previewPrimary = HybridLavenderPrimary,
        previewSecondary = HybridLavenderSecondary,
        previewSurface = HybridLavenderCard
    ),
    MIDNIGHT_AURORA(
        displayName = "Midnight Aurora",
        subtitle = "Dark · Deep midnight purple, violet & cyan glass",
        isDark = true,
        previewPrimary = AuroraPrimary,
        previewSecondary = AuroraSecondary,
        previewSurface = AuroraCard
    ),
    MATCHA_MIST(
        displayName = "Matcha Mist",
        subtitle = "Light · Botanical sage green & soft cream",
        isDark = false,
        previewPrimary = MatchaPrimary,
        previewSecondary = MatchaSecondary,
        previewSurface = MatchaCard
    );

    companion object {
        fun fromStored(raw: String?): LifeOsThemeMode {
            if (raw.isNullOrBlank()) return HYBRID_LAVENDER
            entries.firstOrNull { it.name == raw }?.let { return it }
            return when (raw) {
                "MIDNIGHT_AURORA", "VIOLET_CYAN", "CYBER_INDIGO", "AURORA_PULSE" -> MIDNIGHT_AURORA
                "MATCHA_MIST", "ALABASTER_CLOUD", "WARM_LINEN" -> MATCHA_MIST
                else -> HYBRID_LAVENDER
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
    val tertiary: Color,
    val cardRadius: Dp,
    val heroRadius: Dp,
    val smallRadius: Dp,
    val pillRadius: Dp,
    val cardBorderWidth: Dp,
    val cardElevation: Dp,
    val typographyLabel: String
)

private val HybridLavenderTokens = LifeOsThemeTokens(
    mode = LifeOsThemeMode.HYBRID_LAVENDER,
    isDark = true,
    background = HybridLavenderBg,
    surfaceBase = HybridLavenderBase,
    surfaceCard = HybridLavenderCard,
    surfaceElevated = HybridLavenderElevated,
    surfaceHighlight = HybridLavenderHighlight,
    borderSubtle = HybridLavenderBorder,
    borderAccent = HybridLavenderBorderGlow,
    inkPrimary = HybridLavenderInkPrimary,
    inkSecondary = HybridLavenderInkSecondary,
    inkMuted = HybridLavenderInkMuted,
    primary = HybridLavenderPrimary,
    secondary = HybridLavenderSecondary,
    tertiary = HybridLavenderTertiary,
    cardRadius = 22.dp,
    heroRadius = 26.dp,
    smallRadius = 14.dp,
    pillRadius = 50.dp,
    cardBorderWidth = 1.dp,
    cardElevation = 0.dp,
    typographyLabel = "Editorial Lavender"
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
    tertiary = AuroraTertiary,
    cardRadius = 18.dp,
    heroRadius = 22.dp,
    smallRadius = 12.dp,
    pillRadius = 50.dp,
    cardBorderWidth = 1.dp,
    cardElevation = 0.dp,
    typographyLabel = "Midnight Glass"
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
    tertiary = MatchaTertiary,
    cardRadius = 20.dp,
    heroRadius = 24.dp,
    smallRadius = 12.dp,
    pillRadius = 50.dp,
    cardBorderWidth = 1.dp,
    cardElevation = 1.dp,
    typographyLabel = "Botanical Studio"
)

val LocalLifeOsTokens = staticCompositionLocalOf { HybridLavenderTokens }

object LifeOsThemeTokensProvider {
    val current: LifeOsThemeTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalLifeOsTokens.current
}

fun tokensForMode(mode: LifeOsThemeMode): LifeOsThemeTokens = when (mode) {
    LifeOsThemeMode.HYBRID_LAVENDER -> HybridLavenderTokens
    LifeOsThemeMode.MIDNIGHT_AURORA -> MidnightAuroraTokens
    LifeOsThemeMode.MATCHA_MIST -> MatchaMistTokens
}

private fun typographyForMode(mode: LifeOsThemeMode): Typography = when (mode) {
    LifeOsThemeMode.HYBRID_LAVENDER -> HybridLavenderTypography
    LifeOsThemeMode.MIDNIGHT_AURORA -> MidnightAuroraTypography
    LifeOsThemeMode.MATCHA_MIST -> MatchaMistTypography
}

@Composable
fun LifeOsTheme(
    themeMode: LifeOsThemeMode = LifeOsThemeMode.HYBRID_LAVENDER,
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
        val isHybrid = themeMode == LifeOsThemeMode.HYBRID_LAVENDER
        darkColorScheme(
            primary = tokens.primary,
            onPrimary = if (isHybrid) HybridLavenderBg else Color.White,
            primaryContainer = if (isHybrid) HybridLavenderHighlight else tokens.primary.copy(alpha = 0.24f),
            onPrimaryContainer = tokens.inkPrimary,
            secondary = tokens.secondary,
            onSecondary = tokens.background,
            secondaryContainer = tokens.secondary.copy(alpha = 0.18f),
            onSecondaryContainer = tokens.inkPrimary,
            tertiary = tokens.tertiary,
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
            primaryContainer = tokens.surfaceElevated,
            onPrimaryContainer = tokens.inkPrimary,
            secondary = tokens.secondary,
            onSecondary = Color.White,
            secondaryContainer = tokens.secondary.copy(alpha = 0.14f),
            onSecondaryContainer = tokens.inkPrimary,
            tertiary = tokens.tertiary,
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
