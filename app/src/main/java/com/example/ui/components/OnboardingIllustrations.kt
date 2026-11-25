package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kutta.LocalReducedMotion
import com.example.ui.theme.FeeloraGlowLavender
import com.example.ui.theme.FeeloraPrimaryCta

/**
 * Custom 3D-styled Canvas illustrations standing on soft glowing pedestals for each of the
 * 7 Feelora onboarding slides, matching the attached Feelora design mockup.
 */
@Composable
fun OnboardingSlidePedestalIllustration(
    pageIndex: Int,
    contentDescriptionText: String,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val infiniteTransition = rememberInfiniteTransition(label = "onboarding_float_$pageIndex")
    val floatFactor by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_factor"
    )
    val bob = if (reducedMotion) 0f else floatFactor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .semantics { contentDescription = contentDescriptionText },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(250.dp)) {
            val w = size.width
            val h = size.height
            val floatY = bob * h * 0.018f

            // Soft spotlight radial halo behind subject
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFA983FF).copy(alpha = 0.42f),
                        Color(0xFF6C3CE9).copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, h * 0.44f),
                    radius = w * 0.48f
                ),
                radius = w * 0.48f,
                center = Offset(w * 0.5f, h * 0.44f)
            )

            // Soft glowing lavender-white elliptical pedestal at bottom
            drawGlowingPedestal(w, h)

            when (pageIndex) {
                0 -> drawTasksClipboard3D(w, h, floatY)
                1 -> drawStudyBooksAndCap3D(w, h, floatY)
                2 -> drawScheduleCalendarAndClock3D(w, h, floatY)
                3 -> drawWellnessWaterAndHeart3D(w, h, floatY)
                4 -> drawSuruRobot3D(w, h, floatY)
                5 -> drawKuttaPuppyWithHearts3D(w, h, floatY)
                else -> drawTogetherOneCompanions3D(w, h, floatY)
            }
        }
    }
}

private fun DrawScope.drawGlowingPedestal(w: Float, h: Float) {
    // Outer soft aura
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFFFFF).copy(alpha = 0.48f),
                Color(0xFFD6C4FF).copy(alpha = 0.25f),
                Color.Transparent
            ),
            center = Offset(w * 0.5f, h * 0.84f),
            radius = w * 0.44f
        ),
        topLeft = Offset(w * 0.08f, h * 0.72f),
        size = Size(w * 0.84f, h * 0.24f)
    )
    // Solid pedestal disc
    drawOval(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFDFBFF),
                Color(0xFFDCCEFF)
            )
        ),
        topLeft = Offset(w * 0.18f, h * 0.78f),
        size = Size(w * 0.64f, h * 0.12f)
    )
    // Subtle object contact shadow on pedestal
    drawOval(
        color = Color(0x3347298A),
        topLeft = Offset(w * 0.28f, h * 0.805f),
        size = Size(w * 0.44f, h * 0.065f)
    )
}

// Slide 1/7: Tilted 3D clipboard with checklist + pencil
private fun DrawScope.drawTasksClipboard3D(w: Float, h: Float, floatY: Float) {
    rotate(degrees = -7f, pivot = Offset(w * 0.48f, h * 0.46f + floatY)) {
        // Clipboard shadow edge (3D thickness)
        drawRoundRect(
            color = Color(0xFF5632AC),
            topLeft = Offset(w * 0.26f, h * 0.18f + floatY),
            size = Size(w * 0.44f, h * 0.60f),
            cornerRadius = CornerRadius(24f, 24f)
        )
        // Main clipboard body
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFA984FF), Color(0xFF7B50EB))
            ),
            topLeft = Offset(w * 0.245f, h * 0.165f + floatY),
            size = Size(w * 0.44f, h * 0.59f),
            cornerRadius = CornerRadius(24f, 24f)
        )
        // Crisp white paper sheet
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White, Color(0xFFF1ECFF))
            ),
            topLeft = Offset(w * 0.285f, h * 0.23f + floatY),
            size = Size(w * 0.36f, h * 0.48f),
            cornerRadius = CornerRadius(14f, 14f)
        )
        // Metallic lavender top clip
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFE7DEFF), Color(0xFF9F85E6))
            ),
            topLeft = Offset(w * 0.39f, h * 0.135f + floatY),
            size = Size(w * 0.16f, h * 0.085f),
            cornerRadius = CornerRadius(12f, 12f)
        )
        drawCircle(
            color = Color(0xFF5A37B5),
            radius = w * 0.018f,
            center = Offset(w * 0.47f, h * 0.16f + floatY)
        )

        // 4 Checklist rows with violet checkmarks
        listOf(0.32f, 0.42f, 0.52f, 0.62f).forEachIndexed { idx, rowY ->
            val cy = h * rowY + floatY
            val checkPath = Path().apply {
                moveTo(w * 0.33f, cy)
                lineTo(w * 0.352f, cy + h * 0.022f)
                lineTo(w * 0.388f, cy - h * 0.024f)
            }
            drawPath(
                path = checkPath,
                color = if (idx < 3) FeeloraPrimaryCta else Color(0xFFB5A3EC),
                style = Stroke(width = 5.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawRoundRect(
                color = Color(0xFFDAD0F8),
                topLeft = Offset(w * 0.415f, cy - h * 0.01f),
                size = Size(w * (if (idx % 2 == 0) 0.17f else 0.13f), h * 0.022f),
                cornerRadius = CornerRadius(8f, 8f)
            )
        }
    }

    // Floating 3D lavender-silver pencil on right
    rotate(degrees = 26f, pivot = Offset(w * 0.74f, h * 0.46f - floatY * 0.7f)) {
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFFEDE6FF), Color(0xFFA88AF8), Color(0xFF7B54E6))
            ),
            topLeft = Offset(w * 0.71f, h * 0.28f - floatY * 0.7f),
            size = Size(w * 0.07f, h * 0.32f),
            cornerRadius = CornerRadius(12f, 12f)
        )
        val tipPath = Path().apply {
            moveTo(w * 0.71f, h * 0.595f - floatY * 0.7f)
            lineTo(w * 0.78f, h * 0.595f - floatY * 0.7f)
            lineTo(w * 0.745f, h * 0.665f - floatY * 0.7f)
            close()
        }
        drawPath(path = tipPath, color = Color(0xFFFFD8BA))
        drawCircle(
            color = FeeloraPrimaryCta,
            radius = w * 0.012f,
            center = Offset(w * 0.745f, h * 0.655f - floatY * 0.7f)
        )
    }
}

// Slide 2/7: Stacked 3D pastel/violet textbooks + graduation cap with golden tassel
private fun DrawScope.drawStudyBooksAndCap3D(w: Float, h: Float, floatY: Float) {
    // Bottom large warm-peach & lavender book
    drawRoundRect(
        color = Color(0xFFD8887E),
        topLeft = Offset(w * 0.21f, h * 0.61f + floatY * 0.3f),
        size = Size(w * 0.58f, h * 0.15f),
        cornerRadius = CornerRadius(18f, 18f)
    )
    drawRoundRect(
        brush = Brush.verticalGradient(colors = listOf(Color(0xFFFFE1D6), Color(0xFFF3B3A6))),
        topLeft = Offset(w * 0.22f, h * 0.59f + floatY * 0.3f),
        size = Size(w * 0.56f, h * 0.14f),
        cornerRadius = CornerRadius(16f, 16f)
    )
    // Cream pages edge
    drawRoundRect(
        color = Color(0xFFFFFBF7),
        topLeft = Offset(w * 0.26f, h * 0.615f + floatY * 0.3f),
        size = Size(w * 0.49f, h * 0.09f),
        cornerRadius = CornerRadius(10f, 10f)
    )

    // Middle lavender-indigo book
    drawRoundRect(
        color = Color(0xFF5634AE),
        topLeft = Offset(w * 0.24f, h * 0.47f + floatY * 0.5f),
        size = Size(w * 0.52f, h * 0.13f),
        cornerRadius = CornerRadius(16f, 16f)
    )
    drawRoundRect(
        brush = Brush.verticalGradient(colors = listOf(Color(0xFFB799FF), Color(0xFF7D55E8))),
        topLeft = Offset(w * 0.25f, h * 0.45f + floatY * 0.5f),
        size = Size(w * 0.50f, h * 0.12f),
        cornerRadius = CornerRadius(14f, 14f)
    )
    drawRoundRect(
        color = Color(0xFFF7F4FF),
        topLeft = Offset(w * 0.29f, h * 0.472f + floatY * 0.5f),
        size = Size(w * 0.43f, h * 0.075f),
        cornerRadius = CornerRadius(8f, 8f)
    )

    // 3D Dark-violet graduation mortarboard cap perched on top
    val capY = h * 0.26f + floatY
    // Cap skull base
    drawRoundRect(
        brush = Brush.verticalGradient(colors = listOf(Color(0xFF453673), Color(0xFF281D4D))),
        topLeft = Offset(w * 0.36f, capY + h * 0.08f),
        size = Size(w * 0.28f, h * 0.12f),
        cornerRadius = CornerRadius(24f, 24f)
    )
    // Diamond top board
    val board = Path().apply {
        moveTo(w * 0.50f, capY - h * 0.04f)
        lineTo(w * 0.77f, capY + h * 0.07f)
        lineTo(w * 0.50f, capY + h * 0.18f)
        lineTo(w * 0.23f, capY + h * 0.07f)
        close()
    }
    drawPath(
        path = board,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF6B53AE), Color(0xFF33245E))
        )
    )
    // Golden tassel cord
    drawLine(
        color = Color(0xFFFFD07B),
        start = Offset(w * 0.50f, capY + h * 0.07f),
        end = Offset(w * 0.69f, capY + h * 0.16f),
        strokeWidth = 4.5f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color(0xFFFFD07B),
        start = Offset(w * 0.69f, capY + h * 0.16f),
        end = Offset(w * 0.69f, capY + h * 0.28f),
        strokeWidth = 5.5f,
        cap = StrokeCap.Round
    )
    drawCircle(
        color = Color(0xFFFFE09E),
        radius = w * 0.022f,
        center = Offset(w * 0.50f, capY + h * 0.07f)
    )
}

// Slide 3/7: 3D Calendar board + overlapping circular analog clock
private fun DrawScope.drawScheduleCalendarAndClock3D(w: Float, h: Float, floatY: Float) {
    // 3D Calendar Card
    val calTop = h * 0.20f + floatY * 0.7f
    drawRoundRect(
        color = Color(0xFF7B55DF),
        topLeft = Offset(w * 0.18f, calTop + h * 0.02f),
        size = Size(w * 0.52f, h * 0.50f),
        cornerRadius = CornerRadius(26f, 26f)
    )
    drawRoundRect(
        brush = Brush.verticalGradient(colors = listOf(Color.White, Color(0xFFEBE4FF))),
        topLeft = Offset(w * 0.19f, calTop),
        size = Size(w * 0.50f, h * 0.49f),
        cornerRadius = CornerRadius(24f, 24f)
    )
    // Purple calendar header
    drawRoundRect(
        brush = Brush.horizontalGradient(colors = listOf(Color(0xFF8C63F6), FeeloraPrimaryCta)),
        topLeft = Offset(w * 0.19f, calTop),
        size = Size(w * 0.50f, h * 0.135f),
        cornerRadius = CornerRadius(24f, 24f)
    )
    // Spiral binder rings
    listOf(0.30f, 0.44f, 0.58f).forEach { rx ->
        drawRoundRect(
            color = Color(0xFFEDE6FF),
            topLeft = Offset(w * rx, calTop - h * 0.045f),
            size = Size(w * 0.042f, h * 0.085f),
            cornerRadius = CornerRadius(10f, 10f)
        )
    }
    // Date grid tiles
    for (r in 0..2) {
        for (c in 0..3) {
            val active = (r == 1 && c == 1) || (r == 2 && c == 2)
            drawRoundRect(
                color = if (active) FeeloraPrimaryCta else Color(0xFFD5C8F8),
                topLeft = Offset(w * (0.24f + c * 0.105f), calTop + h * (0.18f + r * 0.092f)),
                size = Size(w * 0.072f, h * 0.062f),
                cornerRadius = CornerRadius(8f, 8f)
            )
        }
    }

    // Overlapping 3D Analog Clock on lower-right
    val clockCenter = Offset(w * 0.68f, h * 0.58f - floatY * 0.5f)
    val clockR = w * 0.175f
    drawCircle(
        color = Color(0xFF4F2CA8),
        radius = clockR + 6f,
        center = clockCenter.copy(y = clockCenter.y + 5f)
    )
    drawCircle(
        brush = Brush.linearGradient(colors = listOf(Color(0xFF9C76FF), FeeloraPrimaryCta)),
        radius = clockR,
        center = clockCenter
    )
    drawCircle(
        brush = Brush.radialGradient(colors = listOf(Color.White, Color(0xFFF0EAFF))),
        radius = clockR * 0.80f,
        center = clockCenter
    )
    // Clock hands (10:10 serene layout)
    drawLine(
        color = Color(0xFF28194E),
        start = clockCenter,
        end = Offset(clockCenter.x - clockR * 0.36f, clockCenter.y - clockR * 0.34f),
        strokeWidth = 6f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = FeeloraPrimaryCta,
        start = clockCenter,
        end = Offset(clockCenter.x + clockR * 0.44f, clockCenter.y - clockR * 0.24f),
        strokeWidth = 5f,
        cap = StrokeCap.Round
    )
    drawCircle(color = Color(0xFF28194E), radius = 5.5f, center = clockCenter)
}

// Slide 4/7: 3D Water glass with luminous sky-blue water + glowing pink-lavender heart & droplet
private fun DrawScope.drawWellnessWaterAndHeart3D(w: Float, h: Float, floatY: Float) {
    // Glass body path (tapered cylinder)
    val gTop = h * 0.20f + floatY * 0.6f
    val gBottom = h * 0.74f + floatY * 0.6f
    val glassOuter = Path().apply {
        moveTo(w * 0.32f, gTop)
        lineTo(w * 0.68f, gTop)
        lineTo(w * 0.625f, gBottom)
        quadraticTo(w * 0.50f, gBottom + h * 0.045f, w * 0.375f, gBottom)
        close()
    }
    drawPath(
        path = glassOuter,
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0x66FFFFFF),
                Color(0x38DCE4FF),
                Color(0x70FFFFFF)
            )
        )
    )
    drawPath(
        path = glassOuter,
        color = Color.White.copy(alpha = 0.65f),
        style = Stroke(width = 3.5f, join = StrokeJoin.Round)
    )

    // Luminous blue water fill inside glass
    val wTop = h * 0.36f + floatY * 0.6f
    val waterBody = Path().apply {
        moveTo(w * 0.342f, wTop)
        lineTo(w * 0.658f, wTop)
        lineTo(w * 0.616f, gBottom - h * 0.012f)
        quadraticTo(w * 0.50f, gBottom + h * 0.032f, w * 0.384f, gBottom - h * 0.012f)
        close()
    }
    drawPath(
        path = waterBody,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF72C6FF), Color(0xFF4788F5), Color(0xFF5D59EA))
        )
    )
    // Water surface ellipse
    drawOval(
        color = Color(0xFFB3E3FF),
        topLeft = Offset(w * 0.342f, wTop - h * 0.025f),
        size = Size(w * 0.316f, h * 0.052f)
    )
    // Glass rim highlight
    drawOval(
        color = Color.White.copy(alpha = 0.72f),
        topLeft = Offset(w * 0.32f, gTop - h * 0.028f),
        size = Size(w * 0.36f, h * 0.056f),
        style = Stroke(width = 3.5f)
    )

    // Floating 3D soft water droplet on left
    val dropPath = Path().apply {
        moveTo(w * 0.23f, h * 0.50f - floatY * 0.8f)
        cubicTo(
            w * 0.16f, h * 0.61f - floatY * 0.8f,
            w * 0.18f, h * 0.71f - floatY * 0.8f,
            w * 0.23f, h * 0.71f - floatY * 0.8f
        )
        cubicTo(
            w * 0.28f, h * 0.71f - floatY * 0.8f,
            w * 0.30f, h * 0.61f - floatY * 0.8f,
            w * 0.23f, h * 0.50f - floatY * 0.8f
        )
        close()
    }
    drawPath(
        path = dropPath,
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFE0F4FF), Color(0xFF83BFFF)),
            center = Offset(w * 0.22f, h * 0.62f - floatY * 0.8f),
            radius = w * 0.10f
        )
    )

    // Floating 3D pink-lavender heart on right
    drawFloating3DHeart(
        center = Offset(w * 0.74f, h * 0.60f + floatY * 0.9f),
        size = w * 0.19f,
        topColor = Color(0xFFFFB2DC),
        bottomColor = Color(0xFFDF6FB6)
    )
}

// Slide 5/7: Cute 3D white-and-lavender SURU companion robot
private fun DrawScope.drawSuruRobot3D(w: Float, h: Float, floatY: Float) {
    val cy = floatY

    // Tiny glowing antennas
    drawLine(
        color = Color(0xFFD9CAFF),
        start = Offset(w * 0.42f, h * 0.22f + cy),
        end = Offset(w * 0.39f, h * 0.13f + cy),
        strokeWidth = 4.5f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color(0xFFD9CAFF),
        start = Offset(w * 0.58f, h * 0.22f + cy),
        end = Offset(w * 0.61f, h * 0.13f + cy),
        strokeWidth = 4.5f,
        cap = StrokeCap.Round
    )
    drawCircle(color = Color(0xFF9BE7FF), radius = 7f, center = Offset(w * 0.39f, h * 0.125f + cy))
    drawCircle(color = Color(0xFF9BE7FF), radius = 7f, center = Offset(w * 0.61f, h * 0.125f + cy))

    // Lavender glowing ear-pods
    drawOval(
        brush = Brush.verticalGradient(colors = listOf(Color(0xFFCBB1FF), Color(0xFF7B50EB))),
        topLeft = Offset(w * 0.23f, h * 0.29f + cy),
        size = Size(w * 0.08f, h * 0.16f)
    )
    drawOval(
        brush = Brush.verticalGradient(colors = listOf(Color(0xFFCBB1FF), Color(0xFF7B50EB))),
        topLeft = Offset(w * 0.69f, h * 0.29f + cy),
        size = Size(w * 0.08f, h * 0.16f)
    )

    // Rounded glossy 3D white robot body
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, Color(0xFFE7DFFF), Color(0xFFC4B0F6)),
            center = Offset(w * 0.46f, h * 0.56f + cy),
            radius = w * 0.24f
        ),
        topLeft = Offset(w * 0.36f, h * 0.52f + cy),
        size = Size(w * 0.28f, h * 0.24f),
        cornerRadius = CornerRadius(48f, 48f)
    )
    // Cute waving arms
    drawOval(
        brush = Brush.verticalGradient(colors = listOf(Color.White, Color(0xFFD2C0FC))),
        topLeft = Offset(w * 0.27f, h * 0.54f + cy),
        size = Size(w * 0.10f, h * 0.14f)
    )
    drawOval(
        brush = Brush.verticalGradient(colors = listOf(Color.White, Color(0xFFD2C0FC))),
        topLeft = Offset(w * 0.63f, h * 0.54f + cy),
        size = Size(w * 0.10f, h * 0.14f)
    )
    // Glowing chest heart/orb
    drawCircle(
        brush = Brush.radialGradient(colors = listOf(Color(0xFFB3F0FF), FeeloraPrimaryCta)),
        radius = w * 0.036f,
        center = Offset(w * 0.50f, h * 0.64f + cy)
    )

    // Glossy rounded white robot head
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, Color(0xFFF0EAFF), Color(0xFFCBB7FA)),
            center = Offset(w * 0.45f, h * 0.28f + cy),
            radius = w * 0.30f
        ),
        topLeft = Offset(w * 0.275f, h * 0.19f + cy),
        size = Size(w * 0.45f, h * 0.35f),
        cornerRadius = CornerRadius(64f, 64f)
    )

    // Dark glossy visor face screen
    drawRoundRect(
        brush = Brush.verticalGradient(colors = listOf(Color(0xFF1F163D), Color(0xFF0E0921))),
        topLeft = Offset(w * 0.32f, h * 0.245f + cy),
        size = Size(w * 0.36f, h * 0.23f),
        cornerRadius = CornerRadius(38f, 38f)
    )

    // Glowing cyan happy curved eyes & gentle smile on visor
    val eyeGlow = Color(0xFF7BE8FF)
    drawArc(
        color = eyeGlow,
        startAngle = 195f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(w * 0.375f, h * 0.31f + cy),
        size = Size(w * 0.08f, h * 0.07f),
        style = Stroke(width = 6.5f, cap = StrokeCap.Round)
    )
    drawArc(
        color = eyeGlow,
        startAngle = 195f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(w * 0.545f, h * 0.31f + cy),
        size = Size(w * 0.08f, h * 0.07f),
        style = Stroke(width = 6.5f, cap = StrokeCap.Round)
    )
    drawArc(
        color = Color(0xFFC5B3FF),
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(w * 0.445f, h * 0.375f + cy),
        size = Size(w * 0.11f, h * 0.06f),
        style = Stroke(width = 5f, cap = StrokeCap.Round)
    )
}

// Slide 6/7: Golden puppy KUTTA resting peacefully surrounded by floating pink-lavender hearts
private fun DrawScope.drawKuttaPuppyWithHearts3D(w: Float, h: Float, floatY: Float) {
    val cy = floatY * 0.5f

    // 5 Floating hearts around KUTTA
    drawFloating3DHeart(Offset(w * 0.21f, h * 0.36f - floatY * 0.9f), w * 0.095f, Color(0xFFFFB2DC), Color(0xFFD670B9))
    drawFloating3DHeart(Offset(w * 0.31f, h * 0.22f + floatY * 0.6f), w * 0.075f, Color(0xFFE7B8FF), Color(0xFF9F68E8))
    drawFloating3DHeart(Offset(w * 0.51f, h * 0.16f - floatY * 0.7f), w * 0.11f, Color(0xFFFFBBE2), Color(0xFFE16DB2))
    drawFloating3DHeart(Offset(w * 0.70f, h * 0.22f + floatY * 0.8f), w * 0.08f, Color(0xFFE7B8FF), Color(0xFF9F68E8))
    drawFloating3DHeart(Offset(w * 0.80f, h * 0.38f - floatY * 0.6f), w * 0.095f, Color(0xFFFFB2DC), Color(0xFFD670B9))

    // Golden puppy sitting/resting center-front
    // Body
    drawOval(
        brush = Brush.verticalGradient(colors = listOf(Color(0xFFFFE5B8), Color(0xFFE5A960))),
        topLeft = Offset(w * 0.28f, h * 0.49f + cy),
        size = Size(w * 0.44f, h * 0.31f)
    )
    // Floppy ears
    val earBrush = Brush.verticalGradient(colors = listOf(Color(0xFFE7A95E), Color(0xFFC17C35)))
    drawOval(brush = earBrush, topLeft = Offset(w * 0.215f, h * 0.34f + cy), size = Size(w * 0.14f, h * 0.26f))
    drawOval(brush = earBrush, topLeft = Offset(w * 0.645f, h * 0.34f + cy), size = Size(w * 0.14f, h * 0.26f))

    // Head
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFF0D1), Color(0xFFF9CFA0), Color(0xFFE6AC66)),
            center = Offset(w * 0.50f, h * 0.41f + cy),
            radius = w * 0.22f
        ),
        radius = w * 0.195f,
        center = Offset(w * 0.50f, h * 0.435f + cy)
    )
    // Cream muzzle
    drawOval(
        color = Color(0xFFFFF5E2),
        topLeft = Offset(w * 0.405f, h * 0.455f + cy),
        size = Size(w * 0.19f, h * 0.14f)
    )
    // Happy peaceful closed eyes
    drawArc(
        color = Color(0xFF422918),
        startAngle = 15f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(w * 0.385f, h * 0.39f + cy),
        size = Size(w * 0.075f, h * 0.055f),
        style = Stroke(width = 5f, cap = StrokeCap.Round)
    )
    drawArc(
        color = Color(0xFF422918),
        startAngle = 15f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(w * 0.54f, h * 0.39f + cy),
        size = Size(w * 0.075f, h * 0.055f),
        style = Stroke(width = 5f, cap = StrokeCap.Round)
    )
    // Nose & smile
    drawOval(color = Color(0xFF3E261B), topLeft = Offset(w * 0.475f, h * 0.472f + cy), size = Size(w * 0.05f, h * 0.04f))
    drawArc(
        color = Color(0xFF5D3A28),
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(w * 0.46f, h * 0.505f + cy),
        size = Size(w * 0.08f, h * 0.045f),
        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
    )
    // Cute paws on pedestal
    drawRoundRect(
        color = Color(0xFFFFEBD0),
        topLeft = Offset(w * 0.34f, h * 0.71f + cy),
        size = Size(w * 0.11f, h * 0.10f),
        cornerRadius = CornerRadius(18f, 18f)
    )
    drawRoundRect(
        color = Color(0xFFFFEBD0),
        topLeft = Offset(w * 0.55f, h * 0.71f + cy),
        size = Size(w * 0.11f, h * 0.10f),
        cornerRadius = CornerRadius(18f, 18f)
    )
}

// Slide 7/7: Together One — Two companions facing each other beneath a glowing lavender heart
private fun DrawScope.drawTogetherOneCompanions3D(w: Float, h: Float, floatY: Float) {
    // Glowing floating lavender-white heart between them
    drawFloating3DHeart(
        center = Offset(w * 0.50f, h * 0.25f + floatY),
        size = w * 0.17f,
        topColor = Color.White,
        bottomColor = FeeloraGlowLavender
    )

    // Left companion (girl with flowing hair in soft lavender sweater)
    val girlBody = Path().apply {
        moveTo(w * 0.22f, h * 0.81f)
        cubicTo(w * 0.22f, h * 0.56f, w * 0.26f, h * 0.48f, w * 0.345f, h * 0.48f)
        cubicTo(w * 0.43f, h * 0.48f, w * 0.46f, h * 0.58f, w * 0.45f, h * 0.81f)
        close()
    }
    drawPath(
        path = girlBody,
        brush = Brush.verticalGradient(colors = listOf(Color(0xFFD1BDFB), Color(0xFF8C67D6)))
    )
    drawCircle(
        color = Color(0xFFF4CEBA),
        radius = w * 0.07f,
        center = Offset(w * 0.355f, h * 0.39f)
    )
    val girlHair = Path().apply {
        moveTo(w * 0.285f, h * 0.39f)
        cubicTo(w * 0.28f, h * 0.27f, w * 0.42f, h * 0.27f, w * 0.41f, h * 0.38f)
        cubicTo(w * 0.36f, h * 0.37f, w * 0.34f, h * 0.48f, w * 0.33f, h * 0.65f)
        cubicTo(w * 0.26f, h * 0.62f, w * 0.25f, h * 0.50f, w * 0.285f, h * 0.39f)
        close()
    }
    drawPath(path = girlHair, color = Color(0xFF28193D))

    // Right companion (boy in deep periwinkle-indigo sweater)
    val boyBody = Path().apply {
        moveTo(w * 0.55f, h * 0.81f)
        cubicTo(w * 0.54f, h * 0.56f, w * 0.57f, h * 0.46f, w * 0.655f, h * 0.46f)
        cubicTo(w * 0.74f, h * 0.46f, w * 0.78f, h * 0.56f, w * 0.78f, h * 0.81f)
        close()
    }
    drawPath(
        path = boyBody,
        brush = Brush.verticalGradient(colors = listOf(Color(0xFF7B85DF), Color(0xFF47499E)))
    )
    drawCircle(
        color = Color(0xFFF2C8B2),
        radius = w * 0.072f,
        center = Offset(w * 0.65f, h * 0.375f)
    )
    val boyHair = Path().apply {
        moveTo(w * 0.58f, h * 0.36f)
        cubicTo(w * 0.58f, h * 0.26f, w * 0.73f, h * 0.26f, w * 0.725f, h * 0.39f)
        cubicTo(w * 0.72f, h * 0.44f, w * 0.68f, h * 0.43f, w * 0.67f, h * 0.36f)
        close()
    }
    drawPath(path = boyHair, color = Color(0xFF251A3B))
}

private fun DrawScope.drawFloating3DHeart(
    center: Offset,
    size: Float,
    topColor: Color,
    bottomColor: Color
) {
    val half = size * 0.5f
    val path = Path().apply {
        moveTo(center.x, center.y + half * 0.72f)
        cubicTo(
            center.x - size * 0.62f, center.y + half * 0.25f,
            center.x - size * 0.62f, center.y - half * 0.68f,
            center.x, center.y - half * 0.22f
        )
        cubicTo(
            center.x + size * 0.62f, center.y - half * 0.68f,
            center.x + size * 0.62f, center.y + half * 0.25f,
            center.x, center.y + half * 0.72f
        )
        close()
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(topColor, bottomColor),
            startY = center.y - half,
            endY = center.y + half
        )
    )
}
