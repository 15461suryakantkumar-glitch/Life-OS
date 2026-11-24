package com.example.kutta

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.components.GradientGlowCard
import com.example.ui.components.NeonProgressBar
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.RoseAlert
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val LocalReducedMotion = compositionLocalOf { false }

/**
 * Resolves whether animations should be minimized based on in-app Reduced Motion toggle
 * or Android system animator duration scale.
 */
@Composable
fun rememberShouldReduceMotion(): Boolean {
    val appReducedMotion = LocalReducedMotion.current
    val context = LocalContext.current
    val systemScaleZero = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
    return appReducedMotion || systemScaleZero
}

/**
 * Reusable KUTTA animated mascot composable powered by the 9 transparent KUTTA drawable illustrations
 * plus lightweight Compose motion & particle cues (hearts, sparkles, Zzz, water nudge).
 * Automatically pauses infinite transitions when backgrounded and honors Reduced Motion.
 */
@Composable
fun KuttaMascot(
    visualState: KuttaVisualState,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    onTap: (() -> Unit)? = null
) {
    val tokens = LifeOsThemeTokensProvider.current
    val reduceMotion = rememberShouldReduceMotion()
    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleActive by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME, Lifecycle.Event.ON_START -> isLifecycleActive = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isLifecycleActive = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var tapPulse by remember { mutableStateOf(false) }
    val tapScale by animateFloatAsState(
        targetValue = if (tapPulse) 0.90f else 1f,
        animationSpec = if (reduceMotion) tween(90) else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        finishedListener = { tapPulse = false },
        label = "kutta_tap_scale"
    )

    val runMotion = !reduceMotion && isLifecycleActive

    val infiniteTransition = rememberInfiniteTransition(label = "kutta_motion_loop")
    val phase by if (runMotion) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "kutta_phase"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    val auraPulse by if (runMotion) {
        infiniteTransition.animateFloat(
            initialValue = 0.88f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "kutta_aura"
        )
    } else {
        remember { mutableStateOf(0.96f) }
    }

    val translationYOffset = if (!runMotion) {
        0f
    } else {
        when (visualState.pose) {
            KuttaPose.CELEBRATING -> -kotlin.math.abs(sin(phase * 2f)) * 16f
            KuttaPose.HAPPY, KuttaPose.MORNING -> -kotlin.math.abs(sin(phase * 1.5f)) * 10f
            KuttaPose.PROUD -> sin(phase) * -4f - 3f
            KuttaPose.SLEEPY, KuttaPose.SLEEPING -> sin(phase * 0.7f) * 3f
            KuttaPose.WORRIED -> sin(phase) * 2.5f
            KuttaPose.FOCUSED, KuttaPose.IDLE -> sin(phase) * 3.5f
        }
    }

    val rotationTilt = if (!runMotion) {
        0f
    } else {
        when (visualState.pose) {
            KuttaPose.HAPPY, KuttaPose.MORNING -> sin(phase * 2f) * 3.8f
            KuttaPose.CELEBRATING -> sin(phase * 2.5f) * 5.0f
            KuttaPose.WORRIED -> -3.5f + sin(phase * 0.9f) * 2.2f
            KuttaPose.PROUD -> sin(phase) * 1.6f
            KuttaPose.FOCUSED -> sin(phase * 0.8f) * 1.2f
            KuttaPose.SLEEPY, KuttaPose.SLEEPING -> sin(phase * 0.6f) * 1.5f
            KuttaPose.IDLE -> sin(phase) * 2.0f
        }
    }

    val breathScale = if (!runMotion) {
        1f
    } else {
        when (visualState.pose) {
            KuttaPose.SLEEPING, KuttaPose.SLEEPY -> 1f + sin(phase * 0.8f) * 0.025f
            KuttaPose.PROUD -> 1.03f + sin(phase) * 0.015f
            else -> 1f + sin(phase) * 0.014f
        }
    }

    val auraColor = when (visualState.emotion) {
        KuttaEmotion.HAPPY -> MaterialTheme.colorScheme.secondary
        KuttaEmotion.FOCUSED -> MaterialTheme.colorScheme.primary
        KuttaEmotion.SLEEPY -> Color(0xFFA78BFA)
        KuttaEmotion.WORRIED -> AmberWarning
        KuttaEmotion.PROUD -> EmeraldSuccess
    }

    val clickableModifier = if (onTap != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            tapPulse = true
            onTap()
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .then(clickableModifier),
        contentAlignment = Alignment.Center
    ) {
        // Soft studio glow behind KUTTA
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        auraColor.copy(alpha = if (tokens.isDark) 0.34f else 0.24f),
                        auraColor.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(this.size.width / 2f, this.size.height * 0.56f),
                    radius = this.size.minDimension * 0.48f * auraPulse
                )
            )
        }

        // Crossfade smoothly between KUTTA poses
        AnimatedContent(
            targetState = visualState.pose,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                } else {
                    (fadeIn(tween(240)) + scaleIn(initialScale = 0.92f, animationSpec = tween(240))) togetherWith fadeOut(tween(180))
                }
            },
            label = "kutta_pose_crossfade"
        ) { pose ->
            Image(
                painter = painterResource(id = pose.drawableRes),
                contentDescription = pose.contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size * 0.90f)
                    .graphicsLayer {
                        translationY = translationYOffset
                        rotationZ = rotationTilt
                        scaleX = tapScale * breathScale
                        scaleY = tapScale * breathScale
                    }
            )
        }

        // Lightweight decorative ambient effects (hearts, sparkles, Zzz)
        if (runMotion && (visualState.showHearts || visualState.showSparkles || visualState.showSleepZzz)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = this.size.width
                val h = this.size.height

                if (visualState.showHearts) {
                    val heartRise = (phase / (2f * PI.toFloat()))
                    val hx = w * 0.80f + sin(phase * 2f) * (w * 0.03f)
                    val hy = h * (0.34f - heartRise * 0.18f)
                    drawMiniHeart(
                        center = Offset(hx, hy),
                        radius = w * 0.045f,
                        color = RoseAlert.copy(alpha = (1f - heartRise).coerceIn(0.25f, 0.9f))
                    )
                }

                if (visualState.showSparkles) {
                    for (i in 0..2) {
                        val angle = phase + i * 2.1f
                        val sx = w * (0.18f + i * 0.31f) + cos(angle) * (w * 0.03f)
                        val sy = h * (0.20f + (i % 2) * 0.10f) + sin(angle) * (h * 0.025f)
                        val alpha = (0.45f + 0.5f * kotlin.math.abs(sin(angle))).coerceIn(0f, 1f)
                        drawMiniSparkle(
                            center = Offset(sx, sy),
                            radius = w * 0.042f,
                            color = AmberWarning.copy(alpha = alpha)
                        )
                    }
                }

                if (visualState.showSleepZzz) {
                    val drift = (phase / (2f * PI.toFloat()))
                    val zCenter = Offset(w * 0.78f, h * (0.28f - drift * 0.12f))
                    drawCircle(
                        color = Color(0xFFC4B5FD).copy(alpha = (0.85f - drift * 0.6f).coerceIn(0.2f, 0.9f)),
                        radius = w * 0.024f,
                        center = zCenter
                    )
                    drawCircle(
                        color = Color(0xFFC4B5FD).copy(alpha = (0.7f - drift * 0.5f).coerceIn(0.15f, 0.8f)),
                        radius = w * 0.017f,
                        center = Offset(zCenter.x - w * 0.05f, zCenter.y + h * 0.045f)
                    )
                }
            }
        }

        // Water nudge visual pill when water reminder triggers
        AnimatedVisibility(
            visible = visualState.isWaterHighlight,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary,
                shadowElevation = 4.dp
            ) {
                Icon(
                    imageVector = Icons.Filled.WaterDrop,
                    contentDescription = "Drink water reminder",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(6.dp)
                        .size(16.dp)
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMiniHeart(
    center: Offset,
    radius: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(center.x, center.y + radius * 0.7f)
        cubicTo(
            center.x - radius * 1.4f, center.y - radius * 0.1f,
            center.x - radius * 0.6f, center.y - radius * 1.2f,
            center.x, center.y - radius * 0.35f
        )
        cubicTo(
            center.x + radius * 0.6f, center.y - radius * 1.2f,
            center.x + radius * 1.4f, center.y - radius * 0.1f,
            center.x, center.y + radius * 0.7f
        )
        close()
    }
    drawPath(path, color = color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMiniSparkle(
    center: Offset,
    radius: Float,
    color: Color
) {
    val inner = radius * 0.28f
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + inner, center.y - inner)
        lineTo(center.x + radius, center.y)
        lineTo(center.x + inner, center.y + inner)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - inner, center.y + inner)
        lineTo(center.x - radius, center.y)
        lineTo(center.x - inner, center.y - inner)
        close()
    }
    drawPath(path, color = color)
}

/**
 * Reusable Hinglish speech bubble for KUTTA.
 */
@Composable
fun KuttaDialogueBubble(
    message: String,
    emotion: KuttaEmotion,
    modifier: Modifier = Modifier
) {
    val tokens = LifeOsThemeTokensProvider.current
    val accent = when (emotion) {
        KuttaEmotion.HAPPY -> MaterialTheme.colorScheme.secondary
        KuttaEmotion.FOCUSED -> MaterialTheme.colorScheme.primary
        KuttaEmotion.SLEEPY -> Color(0xFF8B5CF6)
        KuttaEmotion.WORRIED -> AmberWarning
        KuttaEmotion.PROUD -> EmeraldSuccess
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(tokens.cardRadius * 0.85f),
        color = accent.copy(alpha = if (tokens.isDark) 0.14f else 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.38f))
    ) {
        AnimatedContent(
            targetState = message,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "kutta_dialogue_swap"
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.inkPrimary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
            )
        }
    }
}

/**
 * Prominent Dashboard Companion Card for KUTTA showing expression, Hinglish message,
 * Level / XP, Mood badge, and Pet / Interact action (never awards XP).
 */
@Composable
fun KuttaDashboardCard(
    visualState: KuttaVisualState,
    level: Int,
    xpIntoCurrentLevel: Int,
    xpPerLevel: Int,
    onTapKutta: () -> Unit,
    onOpenSanctuary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LifeOsThemeTokensProvider.current
    GradientGlowCard(
        modifier = modifier.testTag("kutta_dashboard_card"),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KuttaMascot(
                visualState = visualState,
                size = 112.dp,
                onTap = onTapKutta,
                modifier = Modifier.testTag("kutta_hero_mascot")
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "KUTTA",
                            style = MaterialTheme.typography.titleLarge,
                            color = tokens.inkPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        StatusPill(
                            text = visualState.emotion.label,
                            color = when (visualState.emotion) {
                                KuttaEmotion.HAPPY -> MaterialTheme.colorScheme.secondary
                                KuttaEmotion.FOCUSED -> MaterialTheme.colorScheme.primary
                                KuttaEmotion.SLEEPY -> Color(0xFF8B5CF6)
                                KuttaEmotion.WORRIED -> AmberWarning
                                KuttaEmotion.PROUD -> EmeraldSuccess
                            }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                KuttaDialogueBubble(
                    message = visualState.dialogue,
                    emotion = visualState.emotion,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lv.$level · $xpIntoCurrentLevel / $xpPerLevel XP",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.inkSecondary
            )
            Text(
                text = "Tap KUTTA to interact",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.inkMuted
            )
        }
        Spacer(Modifier.height(5.dp))
        NeonProgressBar(
            progress = (xpIntoCurrentLevel.toFloat() / xpPerLevel.coerceAtLeast(1)).coerceIn(0f, 1f),
            heightDp = 6
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onTapKutta,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("pat_kutta_btn")
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = RoseAlert, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pat KUTTA", maxLines = 1, softWrap = false, fontSize = 13.sp)
            }
            Surface(
                onClick = onOpenSanctuary,
                shape = RoundedCornerShape(tokens.smallRadius),
                color = tokens.surfaceElevated,
                border = BorderStroke(1.dp, tokens.borderSubtle),
                modifier = Modifier.height(40.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Pets, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "KUTTA Corner",
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.inkPrimary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
