package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val PlusJakartaSansFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val CormorantGaramondFamily = FontFamily(
    Font(R.font.cormorant_garamond, FontWeight.Normal),
    Font(R.font.cormorant_garamond, FontWeight.Medium),
    Font(R.font.cormorant_garamond, FontWeight.SemiBold),
    Font(R.font.cormorant_garamond, FontWeight.Bold)
)

val SpaceGroteskFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val ManropeFamily = FontFamily(
    Font(R.font.manrope, FontWeight.Normal),
    Font(R.font.manrope, FontWeight.Medium),
    Font(R.font.manrope, FontWeight.SemiBold),
    Font(R.font.manrope, FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

private fun buildFeeloraTypography(
    heroFamily: FontFamily,
    displayFamily: FontFamily,
    bodyFamily: FontFamily,
    monoFamily: FontFamily,
    displayTrackingScale: Float = 1f,
    labelTrackingBoost: Float = 0f
): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = heroFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 33.sp,
        lineHeight = 39.sp,
        letterSpacing = (-0.5 * displayTrackingScale).sp
    ),
    displayMedium = TextStyle(
        fontFamily = heroFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 27.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.3 * displayTrackingScale).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2 * displayTrackingScale).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1 * displayTrackingScale).sp
    ),
    titleLarge = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.5.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.15.sp
    ),
    bodySmall = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
        letterSpacing = (0.25 + labelTrackingBoost).sp
    ),
    labelMedium = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        letterSpacing = (0.25 + labelTrackingBoost).sp
    ),
    labelSmall = TextStyle(
        fontFamily = monoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
        letterSpacing = (0.3 + labelTrackingBoost).sp
    )
)

val HybridLavenderTypography = buildFeeloraTypography(
    heroFamily = CormorantGaramondFamily,
    displayFamily = PlusJakartaSansFamily,
    bodyFamily = PlusJakartaSansFamily,
    monoFamily = JetBrainsMonoFamily,
    displayTrackingScale = 0.75f,
    labelTrackingBoost = 0.1f
)

val MidnightAuroraTypography = buildFeeloraTypography(
    heroFamily = SpaceGroteskFamily,
    displayFamily = SpaceGroteskFamily,
    bodyFamily = ManropeFamily,
    monoFamily = JetBrainsMonoFamily,
    displayTrackingScale = 1.05f,
    labelTrackingBoost = 0.05f
)

val MatchaMistTypography = buildFeeloraTypography(
    heroFamily = PlusJakartaSansFamily,
    displayFamily = SpaceGroteskFamily,
    bodyFamily = PlusJakartaSansFamily,
    monoFamily = JetBrainsMonoFamily,
    displayTrackingScale = 0.8f,
    labelTrackingBoost = 0.15f
)

val LifeOsTypography = HybridLavenderTypography
