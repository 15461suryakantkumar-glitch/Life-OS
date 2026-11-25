package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CormorantGaramondFamily
import com.example.ui.theme.DeepLavender
import com.example.ui.theme.FeeloraGlowLavender
import com.example.ui.theme.FeeloraHeadingViolet
import com.example.ui.theme.FeeloraLavenderMistBottom
import com.example.ui.theme.FeeloraLavenderMistTop
import com.example.ui.theme.FeeloraMidnightMid
import com.example.ui.theme.FeeloraMidnightTop
import com.example.ui.theme.FeeloraRoyalViolet
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PrimaryLavender
import com.example.ui.theme.SoftPeach
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom Compose Canvas glowing lavender-white smiling heart emblem matching the Feelora mockup.
 */
@Composable
fun FeeloraHeartEmblem(
    size: Dp = 84.dp,
    modifier: Modifier = Modifier,
    glowIntensity: Float = 1f
) {
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Feelora glowing heart emblem" }
    ) {
        val w = this.size.width
        val h = this.size.height
        val cx = w * 0.5f
        val cy = h * 0.5f

        // Ambient outer glow halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    FeeloraGlowLavender.copy(alpha = 0.42f * glowIntensity),
                    Color(0xFF8C62F2).copy(alpha = 0.16f * glowIntensity),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = w * 0.62f
            ),
            radius = w * 0.62f,
            center = Offset(cx, cy)
        )

        // Open-ribbon dynamic heart contour matching the Feelora mockup
        val outerHeart = Path().apply {
            moveTo(w * 0.495f, h * 0.305f)
            // Left lobe arch
            cubicTo(
                w * 0.43f, h * 0.17f,
                w * 0.17f, h * 0.16f,
                w * 0.165f, h * 0.415f
            )
            // Left sweep down to heart tip
            cubicTo(
                w * 0.16f, h * 0.635f,
                w * 0.365f, h * 0.775f,
                w * 0.50f, h * 0.865f
            )
            // Right sweep up to right lobe
            cubicTo(
                w * 0.655f, h * 0.775f,
                w * 0.84f, h * 0.625f,
                w * 0.835f, h * 0.415f
            )
            // Right lobe arch stopping cleanly before center cleft
            cubicTo(
                w * 0.83f, h * 0.21f,
                w * 0.635f, h * 0.19f,
                w * 0.555f, h * 0.28f
            )
        }

        // Soft outer stroke aura
        drawPath(
            path = outerHeart,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.35f * glowIntensity),
                    FeeloraGlowLavender.copy(alpha = 0.30f * glowIntensity)
                ),
                start = Offset(w * 0.2f, h * 0.2f),
                end = Offset(w * 0.8f, h * 0.85f)
            ),
            style = Stroke(
                width = w * 0.155f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Main glossy lavender-white heart ribbon stroke
        drawPath(
            path = outerHeart,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFFFFF),
                    Color(0xFFF0E7FF),
                    Color(0xFFCBB1FF),
                    Color(0xFFA980FB)
                ),
                start = Offset(w * 0.18f, h * 0.22f),
                end = Offset(w * 0.82f, h * 0.84f)
            ),
            style = Stroke(
                width = w * 0.105f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Happy closed-eye arcs inside the heart (^ ^)
        val eyeStroke = w * 0.052f
        val leftEye = Path().apply {
            moveTo(w * 0.34f, h * 0.455f)
            quadraticTo(w * 0.39f, h * 0.385f, w * 0.44f, h * 0.455f)
        }
        val rightEye = Path().apply {
            moveTo(w * 0.56f, h * 0.455f)
            quadraticTo(w * 0.61f, h * 0.385f, w * 0.66f, h * 0.455f)
        }
        val smile = Path().apply {
            moveTo(w * 0.435f, h * 0.545f)
            quadraticTo(w * 0.50f, h * 0.615f, w * 0.565f, h * 0.545f)
        }

        val faceBrush = Brush.linearGradient(
            colors = listOf(Color(0xFFF6F1FF), Color(0xFFD1BAFF)),
            start = Offset(w * 0.35f, h * 0.40f),
            end = Offset(w * 0.65f, h * 0.60f)
        )

        drawPath(
            path = leftEye,
            brush = faceBrush,
            style = Stroke(width = eyeStroke, cap = StrokeCap.Round)
        )
        drawPath(
            path = rightEye,
            brush = faceBrush,
            style = Stroke(width = eyeStroke, cap = StrokeCap.Round)
        )
        drawPath(
            path = smile,
            brush = faceBrush,
            style = Stroke(width = eyeStroke * 0.95f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Full Feelora brand lockup matching the attached mockup:
 * Glowing smiling heart emblem + bold "Feelora" Outfit wordmark + "Feel · Heal · Grow" subtitle.
 */
@Composable
fun FeeloraLogo(
    modifier: Modifier = Modifier,
    emblemSize: Dp = 76.dp,
    wordmarkSize: TextUnit = 38.sp,
    showSubtitle: Boolean = true,
    lightOnDark: Boolean = true
) {
    val wordColor = if (lightOnDark) Color.White else FeeloraHeadingViolet
    val subColor = if (lightOnDark) Color(0xFFE3D8FF) else Color(0xFF5A5270)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FeeloraHeartEmblem(size = emblemSize)
        Text(
            text = "Feelora",
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = wordmarkSize,
            letterSpacing = (-0.5).sp,
            color = wordColor,
            textAlign = TextAlign.Center
        )
        if (showSubtitle) {
            Text(
                text = "Feel  ·  Heal  ·  Grow",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                letterSpacing = 1.4.sp,
                color = subColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Signature Feelora midnight-violet to soft-lavender-mist backdrop with floating star sparkles.
 */
@Composable
fun FeeloraSparkleBackdrop(
    splitRatio: Float = 0.50f,
    sparklePhase: Float = 0f,
    fullDarkMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        if (fullDarkMode) {
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to FeeloraMidnightTop,
                        0.38f to FeeloraMidnightMid,
                        0.68f to FeeloraRoyalViolet,
                        0.88f to Color(0xFF6B47A9),
                        1.0f to Color(0xFFB398E8)
                    )
                )
            )
        } else {
            val s = splitRatio.coerceIn(0.30f, 0.72f)
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to FeeloraMidnightTop,
                        (s * 0.45f) to FeeloraMidnightMid,
                        (s * 0.85f) to FeeloraRoyalViolet,
                        (s + 0.08f).coerceAtMost(0.92f) to FeeloraLavenderMistTop,
                        1.0f to FeeloraLavenderMistBottom
                    )
                )
            )
        }

        // Soft upper violet radial bloom
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF835AE8).copy(alpha = 0.34f),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, h * 0.25f),
                radius = w * 0.68f
            ),
            radius = w * 0.68f,
            center = Offset(w * 0.5f, h * 0.25f)
        )

        // Subtle floating 4-point sparkles & soft bokeh dots in upper midnight region
        val sparkles = listOf(
            Triple(0.14f, 0.11f, 8f),
            Triple(0.84f, 0.14f, 10f),
            Triple(0.24f, 0.25f, 6f),
            Triple(0.76f, 0.28f, 7f),
            Triple(0.10f, 0.36f, 5.5f),
            Triple(0.89f, 0.37f, 6.5f),
            Triple(0.38f, 0.15f, 4.5f),
            Triple(0.65f, 0.10f, 5.5f)
        )
        sparkles.forEachIndexed { idx, (fx, fy, radius) ->
            val twinkle = 0.45f + 0.55f * ((sin(sparklePhase * 2f * PI + idx * 1.15).toFloat() + 1f) * 0.5f)
            val driftY = cos(sparklePhase * 2f * PI + idx * 0.8).toFloat() * 4f
            drawFourPointStar(
                center = Offset(w * fx, h * fy + driftY),
                outerRadius = radius * (0.85f + 0.25f * twinkle),
                color = Color.White.copy(alpha = 0.55f * twinkle)
            )
        }

        val dots = listOf(
            Triple(0.18f, 0.19f, 3.5f),
            Triple(0.81f, 0.22f, 3f),
            Triple(0.46f, 0.08f, 2.8f),
            Triple(0.58f, 0.21f, 3.2f),
            Triple(0.16f, 0.42f, 2.5f),
            Triple(0.85f, 0.43f, 2.8f)
        )
        dots.forEachIndexed { idx, (fx, fy, r) ->
            val alpha = 0.22f + 0.35f * ((cos(sparklePhase * 2f * PI + idx * 1.4).toFloat() + 1f) * 0.5f)
            drawCircle(
                color = FeeloraGlowLavender.copy(alpha = alpha),
                radius = r,
                center = Offset(w * fx, h * fy)
            )
        }
    }
}

fun DrawScope.drawFourPointStar(
    center: Offset,
    outerRadius: Float,
    color: Color
) {
    val inner = outerRadius * 0.26f
    val path = Path().apply {
        moveTo(center.x, center.y - outerRadius)
        quadraticTo(center.x + inner * 0.4f, center.y - inner * 0.4f, center.x + outerRadius, center.y)
        quadraticTo(center.x + inner * 0.4f, center.y + inner * 0.4f, center.x, center.y + outerRadius)
        quadraticTo(center.x - inner * 0.4f, center.y + inner * 0.4f, center.x - outerRadius, center.y)
        quadraticTo(center.x - inner * 0.4f, center.y - inner * 0.4f, center.x, center.y - outerRadius)
        close()
    }
    drawPath(path = path, color = color)
}

/**
 * Sleeping golden KUTTA puppy mascot drawn with layered 3D-shaded Compose Canvas geometry
 * matching the mockup's sleeping golden puppy resting on top of the frosted auth card.
 */
@Composable
fun FeeloraSleepingKuttaMascot(
    modifier: Modifier = Modifier,
    width: Dp = 156.dp,
    height: Dp = 104.dp,
    breathingOffset: Float = 0f
) {
    Canvas(
        modifier = modifier
            .size(width, height)
            .semantics { contentDescription = "Sleeping golden KUTTA puppy mascot" }
    ) {
        val w = size.width
        val h = size.height
        val breath = breathingOffset * h * 0.015f

        // Soft contact shadow directly underneath paws/body
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x4423124A), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.89f),
                radius = w * 0.44f
            ),
            topLeft = Offset(w * 0.12f, h * 0.80f),
            size = Size(w * 0.76f, h * 0.18f)
        )

        // Curled golden body mound behind head
        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFDFAA), Color(0xFFE7AE68))
            ),
            topLeft = Offset(w * 0.18f, h * 0.44f + breath),
            size = Size(w * 0.64f, h * 0.42f)
        )

        // Tail tucked gently on the right
        drawOval(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFF6C682), Color(0xFFD8974C))
            ),
            topLeft = Offset(w * 0.68f, h * 0.58f + breath),
            size = Size(w * 0.22f, h * 0.24f)
        )

        // Left & right floppy golden-amber ears
        val leftEar = Path().apply {
            moveTo(w * 0.30f, h * 0.26f)
            cubicTo(w * 0.15f, h * 0.28f, w * 0.12f, h * 0.54f, w * 0.21f, h * 0.66f)
            cubicTo(w * 0.26f, h * 0.69f, w * 0.32f, h * 0.60f, w * 0.33f, h * 0.44f)
            close()
        }
        val rightEar = Path().apply {
            moveTo(w * 0.70f, h * 0.26f)
            cubicTo(w * 0.85f, h * 0.28f, w * 0.88f, h * 0.54f, w * 0.79f, h * 0.66f)
            cubicTo(w * 0.74f, h * 0.69f, w * 0.68f, h * 0.60f, w * 0.67f, h * 0.44f)
            close()
        }
        val earBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFFE7A95F), Color(0xFFC47F38))
        )
        drawPath(leftEar, brush = earBrush)
        drawPath(rightEar, brush = earBrush)

        // Fluffy rounded puppy head
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFECC9), Color(0xFFF9CFA0), Color(0xFFE7AF69)),
                center = Offset(w * 0.5f, h * 0.36f),
                radius = w * 0.32f
            ),
            topLeft = Offset(w * 0.26f, h * 0.10f + breath * 0.5f),
            size = Size(w * 0.48f, h * 0.58f)
        )

        // Soft cream muzzle patch
        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFF5E2), Color(0xFFFBE1BA))
            ),
            topLeft = Offset(w * 0.37f, h * 0.41f + breath * 0.5f),
            size = Size(w * 0.26f, h * 0.21f)
        )

        // Rosy sleepy cheeks
        drawOval(
            color = Color(0x66F29098),
            topLeft = Offset(w * 0.31f, h * 0.43f),
            size = Size(w * 0.08f, h * 0.055f)
        )
        drawOval(
            color = Color(0x66F29098),
            topLeft = Offset(w * 0.61f, h * 0.43f),
            size = Size(w * 0.08f, h * 0.055f)
        )

        // Peaceful curved closed eyes
        val eyeColor = Color(0xFF422918)
        drawArc(
            color = eyeColor,
            startAngle = 15f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(w * 0.355f, h * 0.315f),
            size = Size(w * 0.095f, h * 0.075f),
            style = Stroke(width = w * 0.022f, cap = StrokeCap.Round)
        )
        drawArc(
            color = eyeColor,
            startAngle = 15f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(w * 0.55f, h * 0.315f),
            size = Size(w * 0.095f, h * 0.075f),
            style = Stroke(width = w * 0.022f, cap = StrokeCap.Round)
        )

        // Cute button nose + gentle smile
        drawOval(
            color = Color(0xFF3E261B),
            topLeft = Offset(w * 0.468f, h * 0.435f),
            size = Size(w * 0.064f, h * 0.052f)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.55f),
            radius = w * 0.009f,
            center = Offset(w * 0.488f, h * 0.45f)
        )
        val mouthPath = Path().apply {
            moveTo(w * 0.455f, h * 0.515f)
            quadraticTo(w * 0.482f, h * 0.545f, w * 0.50f, h * 0.515f)
            quadraticTo(w * 0.518f, h * 0.545f, w * 0.545f, h * 0.515f)
        }
        drawPath(
            path = mouthPath,
            color = Color(0xFF5D3A28),
            style = Stroke(width = w * 0.015f, cap = StrokeCap.Round)
        )

        // Two front paws resting on the card top edge
        val pawBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFEBD0), Color(0xFFEAC088))
        )
        drawRoundRect(
            brush = pawBrush,
            topLeft = Offset(w * 0.30f, h * 0.72f),
            size = Size(w * 0.145f, h * 0.145f),
            cornerRadius = CornerRadius(w * 0.07f, w * 0.07f)
        )
        drawRoundRect(
            brush = pawBrush,
            topLeft = Offset(w * 0.555f, h * 0.72f),
            size = Size(w * 0.145f, h * 0.145f),
            cornerRadius = CornerRadius(w * 0.07f, w * 0.07f)
        )
        // Tiny toe lines on paws
        listOf(0.348f, 0.395f, 0.603f, 0.650f).forEach { tx ->
            drawLine(
                color = Color(0xFFD09D64),
                start = Offset(w * tx, h * 0.80f),
                end = Offset(w * tx, h * 0.855f),
                strokeWidth = w * 0.011f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun FeeloraBrandMark(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
    withSoftFrame: Boolean = false,
    showGlow: Boolean = false
) {
    val tokens = LifeOsThemeTokensProvider.current
    val emblemModifier = if (withSoftFrame) {
        val frameShape = RoundedCornerShape(size * 0.30f)
        modifier
            .size(size)
            .clip(frameShape)
            .background(
                Brush.linearGradient(
                    colors = if (tokens.isDark) {
                        listOf(tokens.surfaceElevated, tokens.surfaceCard)
                    } else {
                        listOf(Color(0xFFFAF7F0), tokens.surfaceHighlight)
                    }
                )
            )
            .border(1.dp, tokens.borderAccent.copy(alpha = 0.55f), frameShape)
            .padding(size * 0.12f)
    } else if (showGlow) {
        modifier
            .size(size)
            .background(
                Brush.radialGradient(
                    colors = listOf(tokens.primary.copy(alpha = 0.24f), Color.Transparent)
                )
            )
    } else {
        modifier.size(size)
    }
    Box(
        modifier = emblemModifier.semantics { contentDescription = "FEELORA emblem" },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_feelora_emblem),
            contentDescription = null,
            modifier = Modifier.size(size * if (withSoftFrame) 0.78f else 1f)
        )
    }
}

@Composable
fun FeeloraBrandLockup(
    modifier: Modifier = Modifier,
    emblemSize: Dp = 40.dp,
    showTagline: Boolean = false,
    centered: Boolean = false
) {
    val tokens = LifeOsThemeTokensProvider.current
    if (centered) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FeeloraBrandMark(size = emblemSize, withSoftFrame = true)
            Text(
                text = "FEELORA",
                fontFamily = CormorantGaramondFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                letterSpacing = 3.2.sp,
                color = tokens.inkPrimary
            )
            if (showTagline) {
                Text(
                    text = "Feel · Heal · Grow",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.inkSecondary
                )
            }
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FeeloraBrandMark(size = emblemSize, withSoftFrame = true)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "FEELORA",
                    fontFamily = CormorantGaramondFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = 2.2.sp,
                    color = tokens.inkPrimary
                )
                if (showTagline) {
                    Text(
                        text = "Feel · Heal · Grow",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.inkSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun FeeloraEditorialAccentBar(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(DeepLavender, PrimaryLavender, SoftPeach)
                )
            )
    )
}
