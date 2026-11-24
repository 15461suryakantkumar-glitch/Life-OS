package com.example.suru

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.kutta.LocalReducedMotion
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.HybridLavenderPrimary
import com.example.ui.theme.HybridLavenderSecondary
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.RoseAlert
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * SURU: Creator digital character mascot inside FEELORA.
 * Consistent across all 8 states (IDLE, LISTENING, THINKING, SPEAKING, WORKING, SUCCESS, ERROR, SLEEPING),
 * using lightweight vector Canvas rendering and honoring ReducedMotion & Lifecycle state.
 */
@Composable
fun SuruMascot(
    state: SuruMascotState,
    size: Dp = 112.dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val tokens = LifeOsThemeTokensProvider.current
    val reducedMotion = LocalReducedMotion.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isForeground by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isForeground = true
            else if (event == Lifecycle.Event.ON_PAUSE) isForeground = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val animateActive = !reducedMotion && isForeground
    val transition = rememberInfiniteTransition(label = "suru_character_anim")

    val breathPhase by if (animateActive) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = when (state) {
                        SuruMascotState.SLEEPING -> 3800
                        SuruMascotState.SUCCESS -> 1300
                        SuruMascotState.LISTENING -> 1600
                        else -> 2500
                    },
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "suru_breath"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    val fastPhase by if (animateActive) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "suru_fast"
        )
    } else {
        remember { mutableStateOf(0.5f) }
    }

    val blinkPhase by if (animateActive) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "suru_blink"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    val auraColor by animateColorAsState(
        targetValue = when (state) {
            SuruMascotState.LISTENING -> HybridLavenderSecondary
            SuruMascotState.THINKING -> HybridLavenderPrimary
            SuruMascotState.SPEAKING -> tokens.primary
            SuruMascotState.WORKING -> tokens.secondary
            SuruMascotState.SUCCESS -> EmeraldSuccess
            SuruMascotState.ERROR -> AmberWarning
            SuruMascotState.SLEEPING -> tokens.inkMuted
            SuruMascotState.IDLE -> tokens.primary
        },
        animationSpec = tween(320),
        label = "suru_aura"
    )

    val containerModifier = modifier
        .size(size)
        .clip(CircleShape)
        .background(Color.Transparent)
        .let { base ->
            if (onClick != null) base.clickable(onClick = onClick) else base
        }
        .semantics {
            contentDescription = "SURU assistant mascot in ${state.label} state"
        }
        .testTag("suru_mascot_${state.name.lowercase()}")

    Box(
        modifier = containerModifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f
            val r = minOf(w, h) * 0.44f

            val bobOffset = when (state) {
                SuruMascotState.SUCCESS -> -kotlin.math.abs(sin(breathPhase)) * r * 0.09f
                SuruMascotState.SLEEPING -> sin(breathPhase) * r * 0.025f
                else -> sin(breathPhase) * r * 0.04f
            }

            // Soft backdrop glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(auraColor.copy(alpha = 0.28f), auraColor.copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r * 1.15f
                ),
                radius = r * 1.15f,
                center = Offset(cx, cy)
            )

            // Listening sound-wave rings
            if (state == SuruMascotState.LISTENING) {
                val pulse1 = ((fastPhase / (2f * PI.toFloat())) % 1f)
                drawCircle(
                    color = auraColor.copy(alpha = (1f - pulse1) * 0.55f),
                    radius = r * (0.90f + pulse1 * 0.22f),
                    center = Offset(cx, cy),
                    style = Stroke(width = w * 0.025f)
                )
            }

            // Working progress arc
            if (state == SuruMascotState.WORKING) {
                val angle = (fastPhase * 180f / PI.toFloat()) % 360f
                drawArc(
                    color = auraColor.copy(alpha = 0.78f),
                    startAngle = angle,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(cx - r * 1.02f, cy - r * 1.02f),
                    size = Size(r * 2.04f, r * 2.04f),
                    style = Stroke(width = w * 0.03f, cap = StrokeCap.Round)
                )
            }

            // Character portrait background disc
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2F244A),
                        Color(0xFF1F1734)
                    )
                ),
                radius = r * 0.88f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = auraColor.copy(alpha = 0.65f),
                radius = r * 0.88f,
                center = Offset(cx, cy),
                style = Stroke(width = w * 0.022f)
            )

            val headY = cy - r * 0.06f + bobOffset
            val headTilt = when (state) {
                SuruMascotState.LISTENING -> -6f
                SuruMascotState.ERROR -> 7f
                else -> 0f
            }

            // Hoodie shoulders (consistent soft lavender hoodie)
            val hoodiePath = Path().apply {
                moveTo(cx - r * 0.56f, cy + r * 0.78f)
                quadraticTo(cx - r * 0.50f, cy + r * 0.34f + bobOffset * 0.5f, cx - r * 0.16f, cy + r * 0.30f + bobOffset * 0.5f)
                lineTo(cx + r * 0.16f, cy + r * 0.30f + bobOffset * 0.5f)
                quadraticTo(cx + r * 0.50f, cy + r * 0.34f + bobOffset * 0.5f, cx + r * 0.56f, cy + r * 0.78f)
                close()
            }
            drawPath(
                path = hoodiePath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFA78BFA), Color(0xFF7C5CE0))
                )
            )

            // Cream hoodie collar & drawstrings
            drawArc(
                color = Color(0xFFE9DFFF),
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(cx - r * 0.24f, cy + r * 0.24f + bobOffset * 0.5f),
                size = Size(r * 0.48f, r * 0.22f),
                style = Stroke(width = r * 0.06f, cap = StrokeCap.Round)
            )
            drawLine(
                color = Color(0xFFF5F0FF),
                start = Offset(cx - r * 0.11f, cy + r * 0.42f + bobOffset * 0.5f),
                end = Offset(cx - r * 0.12f, cy + r * 0.62f + bobOffset * 0.5f),
                strokeWidth = r * 0.032f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFF5F0FF),
                start = Offset(cx + r * 0.11f, cy + r * 0.42f + bobOffset * 0.5f),
                end = Offset(cx + r * 0.12f, cy + r * 0.62f + bobOffset * 0.5f),
                strokeWidth = r * 0.032f,
                cap = StrokeCap.Round
            )

            // Little lavender star emblem on hoodie chest
            drawCircle(
                color = Color(0xFFF2C9E8),
                radius = r * 0.06f,
                center = Offset(cx + r * 0.25f, cy + r * 0.55f + bobOffset * 0.5f)
            )

            rotate(degrees = headTilt, pivot = Offset(cx, headY)) {
                // Neck
                drawRoundRect(
                    color = Color(0xFFEBC3A6),
                    topLeft = Offset(cx - r * 0.10f, headY + r * 0.24f),
                    size = Size(r * 0.20f, r * 0.16f),
                    cornerRadius = CornerRadius(r * 0.06f, r * 0.06f)
                )

                // Back hair silhouette
                drawCircle(
                    color = Color(0xFF241B38),
                    radius = r * 0.43f,
                    center = Offset(cx, headY - r * 0.08f)
                )

                // Ears & sleek creator ear-mic headset
                drawCircle(
                    color = Color(0xFFF4CEB4),
                    radius = r * 0.085f,
                    center = Offset(cx - r * 0.36f, headY + r * 0.02f)
                )
                drawCircle(
                    color = Color(0xFFF4CEB4),
                    radius = r * 0.085f,
                    center = Offset(cx + r * 0.36f, headY + r * 0.02f)
                )
                // Ear-mic accent on right ear
                drawCircle(
                    color = auraColor,
                    radius = r * 0.065f,
                    center = Offset(cx + r * 0.39f, headY + r * 0.02f)
                )
                if (state == SuruMascotState.LISTENING || state == SuruMascotState.SPEAKING) {
                    drawLine(
                        color = auraColor,
                        start = Offset(cx + r * 0.39f, headY + r * 0.06f),
                        end = Offset(cx + r * 0.14f, headY + r * 0.18f),
                        strokeWidth = r * 0.028f,
                        cap = StrokeCap.Round
                    )
                }

                // Face
                drawCircle(
                    color = Color(0xFFFAD8C0),
                    radius = r * 0.355f,
                    center = Offset(cx, headY)
                )

                // Soft blush cheeks
                drawOval(
                    color = Color(0xFFF2A7C6).copy(alpha = 0.42f),
                    topLeft = Offset(cx - r * 0.26f, headY + r * 0.06f),
                    size = Size(r * 0.12f, r * 0.065f)
                )
                drawOval(
                    color = Color(0xFFF2A7C6).copy(alpha = 0.42f),
                    topLeft = Offset(cx + r * 0.14f, headY + r * 0.06f),
                    size = Size(r * 0.12f, r * 0.065f)
                )

                // Creator styled front hair bangs + lavender signature streak
                val bangsPath = Path().apply {
                    moveTo(cx - r * 0.38f, headY - r * 0.06f)
                    quadraticTo(cx - r * 0.34f, headY - r * 0.44f, cx, headY - r * 0.44f)
                    quadraticTo(cx + r * 0.36f, headY - r * 0.44f, cx + r * 0.38f, headY - r * 0.06f)
                    quadraticTo(cx + r * 0.20f, headY - r * 0.22f, cx + r * 0.04f, headY - r * 0.13f)
                    quadraticTo(cx - r * 0.12f, headY - r * 0.24f, cx - r * 0.38f, headY - r * 0.06f)
                    close()
                }
                drawPath(bangsPath, color = Color(0xFF241B38))

                // Subtle lavender highlight lock on left fringe
                val highlightPath = Path().apply {
                    moveTo(cx - r * 0.22f, headY - r * 0.34f)
                    quadraticTo(cx - r * 0.10f, headY - r * 0.26f, cx - r * 0.06f, headY - r * 0.16f)
                    quadraticTo(cx - r * 0.18f, headY - r * 0.20f, cx - r * 0.22f, headY - r * 0.34f)
                    close()
                }
                drawPath(highlightPath, color = Color(0xFFB99AFF).copy(alpha = 0.85f))

                // Eyes
                val eyeY = headY - r * 0.01f
                val eyeOffsetX = r * 0.14f
                val isBlinking = animateActive && blinkPhase > 0.93f

                when {
                    state == SuruMascotState.SLEEPING || isBlinking -> {
                        drawArc(
                            color = Color(0xFF281D3D),
                            startAngle = 15f,
                            sweepAngle = 150f,
                            useCenter = false,
                            topLeft = Offset(cx - eyeOffsetX - r * 0.06f, eyeY - r * 0.02f),
                            size = Size(r * 0.12f, r * 0.06f),
                            style = Stroke(width = r * 0.032f, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = Color(0xFF281D3D),
                            startAngle = 15f,
                            sweepAngle = 150f,
                            useCenter = false,
                            topLeft = Offset(cx + eyeOffsetX - r * 0.06f, eyeY - r * 0.02f),
                            size = Size(r * 0.12f, r * 0.06f),
                            style = Stroke(width = r * 0.032f, cap = StrokeCap.Round)
                        )
                    }
                    state == SuruMascotState.SUCCESS -> {
                        // Happy crescent eyes
                        drawArc(
                            color = Color(0xFF281D3D),
                            startAngle = 195f,
                            sweepAngle = 150f,
                            useCenter = false,
                            topLeft = Offset(cx - eyeOffsetX - r * 0.06f, eyeY - r * 0.03f),
                            size = Size(r * 0.12f, r * 0.08f),
                            style = Stroke(width = r * 0.036f, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = Color(0xFF281D3D),
                            startAngle = 195f,
                            sweepAngle = 150f,
                            useCenter = false,
                            topLeft = Offset(cx + eyeOffsetX - r * 0.06f, eyeY - r * 0.03f),
                            size = Size(r * 0.12f, r * 0.08f),
                            style = Stroke(width = r * 0.036f, cap = StrokeCap.Round)
                        )
                    }
                    else -> {
                        val lookDx = if (state == SuruMascotState.THINKING) r * 0.02f else 0f
                        val lookDy = if (state == SuruMascotState.THINKING) -r * 0.02f else 0f
                        listOf(-eyeOffsetX, eyeOffsetX).forEach { ex ->
                            drawCircle(
                                color = Color(0xFF281D3D),
                                radius = r * 0.052f,
                                center = Offset(cx + ex + lookDx, eyeY + lookDy)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = r * 0.019f,
                                center = Offset(cx + ex + lookDx - r * 0.016f, eyeY + lookDy - r * 0.018f)
                            )
                        }
                    }
                }

                // Sleek round glasses (signature consistent detail)
                val glassColor = if (state == SuruMascotState.WORKING) auraColor else Color(0xFF4C3A70)
                drawCircle(
                    color = glassColor,
                    radius = r * 0.115f,
                    center = Offset(cx - eyeOffsetX, eyeY),
                    style = Stroke(width = r * 0.025f)
                )
                drawCircle(
                    color = glassColor,
                    radius = r * 0.115f,
                    center = Offset(cx + eyeOffsetX, eyeY),
                    style = Stroke(width = r * 0.025f)
                )
                drawLine(
                    color = glassColor,
                    start = Offset(cx - eyeOffsetX + r * 0.115f, eyeY),
                    end = Offset(cx + eyeOffsetX - r * 0.115f, eyeY),
                    strokeWidth = r * 0.022f
                )

                // Eyebrows for expression
                if (state == SuruMascotState.ERROR) {
                    drawLine(
                        color = Color(0xFF281D3D),
                        start = Offset(cx - eyeOffsetX - r * 0.06f, eyeY - r * 0.15f),
                        end = Offset(cx - eyeOffsetX + r * 0.06f, eyeY - r * 0.12f),
                        strokeWidth = r * 0.024f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFF281D3D),
                        start = Offset(cx + eyeOffsetX - r * 0.06f, eyeY - r * 0.16f),
                        end = Offset(cx + eyeOffsetX + r * 0.06f, eyeY - r * 0.16f),
                        strokeWidth = r * 0.024f,
                        cap = StrokeCap.Round
                    )
                }

                // Mouth expression
                val mouthY = headY + r * 0.17f
                when (state) {
                    SuruMascotState.SPEAKING -> {
                        val openRatio = (0.35f + 0.65f * kotlin.math.abs(sin(fastPhase)))
                        drawOval(
                            color = Color(0xFF8C3B58),
                            topLeft = Offset(cx - r * 0.065f, mouthY - r * 0.02f),
                            size = Size(r * 0.13f, r * (0.04f + 0.06f * openRatio))
                        )
                    }
                    SuruMascotState.SUCCESS -> {
                        drawArc(
                            color = Color(0xFF8C3B58),
                            startAngle = 15f,
                            sweepAngle = 150f,
                            useCenter = true,
                            topLeft = Offset(cx - r * 0.09f, mouthY - r * 0.04f),
                            size = Size(r * 0.18f, r * 0.11f)
                        )
                    }
                    SuruMascotState.ERROR -> {
                        drawArc(
                            color = Color(0xFF5D3A58),
                            startAngle = 200f,
                            sweepAngle = 140f,
                            useCenter = false,
                            topLeft = Offset(cx - r * 0.06f, mouthY - r * 0.01f),
                            size = Size(r * 0.12f, r * 0.06f),
                            style = Stroke(width = r * 0.026f, cap = StrokeCap.Round)
                        )
                    }
                    SuruMascotState.THINKING -> {
                        drawCircle(
                            color = Color(0xFF6B3E5A),
                            radius = r * 0.032f,
                            center = Offset(cx + r * 0.03f, mouthY)
                        )
                    }
                    SuruMascotState.SLEEPING -> {
                        drawLine(
                            color = Color(0xFF6B3E5A),
                            start = Offset(cx - r * 0.04f, mouthY),
                            end = Offset(cx + r * 0.04f, mouthY),
                            strokeWidth = r * 0.024f,
                            cap = StrokeCap.Round
                        )
                    }
                    else -> {
                        // Gentle warm smile
                        drawArc(
                            color = Color(0xFF6B3E5A),
                            startAngle = 18f,
                            sweepAngle = 144f,
                            useCenter = false,
                            topLeft = Offset(cx - r * 0.08f, mouthY - r * 0.05f),
                            size = Size(r * 0.16f, r * 0.085f),
                            style = Stroke(width = r * 0.028f, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            // State-specific ambient badges / particles
            when (state) {
                SuruMascotState.THINKING -> {
                    for (i in 0..2) {
                        val theta = fastPhase + i * 0.9f
                        val dotX = cx + r * 0.56f + cos(theta) * r * 0.08f
                        val dotY = cy - r * 0.55f + sin(theta) * r * 0.06f
                        drawCircle(
                            color = HybridLavenderPrimary.copy(alpha = 0.85f),
                            radius = r * (0.035f + i * 0.008f),
                            center = Offset(dotX, dotY)
                        )
                    }
                }
                SuruMascotState.SUCCESS -> {
                    val sparkOffsets = listOf(
                        Offset(cx - r * 0.65f, cy - r * 0.45f),
                        Offset(cx + r * 0.68f, cy - r * 0.42f),
                        Offset(cx + r * 0.58f, cy + r * 0.25f)
                    )
                    sparkOffsets.forEachIndexed { idx, pt ->
                        val scale = 0.7f + 0.3f * kotlin.math.abs(sin(breathPhase + idx))
                        drawCircle(
                            color = EmeraldSuccess.copy(alpha = 0.85f),
                            radius = r * 0.048f * scale,
                            center = pt
                        )
                    }
                }
                SuruMascotState.SLEEPING -> {
                    val drift = sin(breathPhase) * r * 0.05f
                    drawCircle(
                        color = HybridLavenderPrimary.copy(alpha = 0.55f),
                        radius = r * 0.035f,
                        center = Offset(cx + r * 0.45f, cy - r * 0.36f + drift)
                    )
                    drawCircle(
                        color = HybridLavenderPrimary.copy(alpha = 0.75f),
                        radius = r * 0.052f,
                        center = Offset(cx + r * 0.58f, cy - r * 0.52f + drift)
                    )
                }
                SuruMascotState.ERROR -> {
                    drawCircle(
                        color = AmberWarning,
                        radius = r * 0.08f,
                        center = Offset(cx + r * 0.62f, cy - r * 0.52f)
                    )
                }
                else -> Unit
            }
        }
    }
}
