package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kutta.LocalReducedMotion
import com.example.ui.components.FeeloraBrandMark
import com.example.ui.theme.CormorantGaramondFamily
import com.example.ui.theme.LifeOsThemeTokensProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Calm ~2.3s FEELORA opening sequence:
 * Warm Cream / Velvet Plum -> Soft Lavender + Peach radial bloom -> Botanical F emblem
 * -> Cormorant Garamond wordmark -> "Your space to grow." -> smooth exit.
 * Non-blocking, skippable anytime, and reduced-motion aware.
 */
@Composable
fun FeeloraSplashOverlay(
    onPlayAudioLogo: () -> Unit,
    onFinished: () -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val reducedMotion = LocalReducedMotion.current

    val glowAlpha = remember { Animatable(if (reducedMotion) 0.7f else 0f) }
    val emblemScale = remember { Animatable(if (reducedMotion) 1f else 0.78f) }
    val emblemAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val wordmarkAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val taglineAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val containerAlpha = remember { Animatable(1f) }

    LaunchedEffect(reducedMotion) {
        onPlayAudioLogo()
        if (reducedMotion) {
            delay(650L)
            containerAlpha.animateTo(0f, tween(220))
            onFinished()
            return@LaunchedEffect
        }

        launch {
            glowAlpha.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
        launch {
            delay(180L)
            emblemAlpha.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }
        launch {
            delay(180L)
            emblemScale.animateTo(1f, tween(780, easing = FastOutSlowInEasing))
        }
        launch {
            delay(620L)
            wordmarkAlpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        }
        launch {
            delay(980L)
            taglineAlpha.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
        }

        delay(2150L)
        containerAlpha.animateTo(0f, tween(340, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(containerAlpha.value)
            .background(tokens.background)
            .drawBehind {
                val g = glowAlpha.value
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tokens.primary.copy(alpha = (if (tokens.isDark) 0.28f else 0.20f) * g),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.42f),
                        radius = size.maxDimension * 0.48f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tokens.peach.copy(alpha = (if (tokens.isDark) 0.16f else 0.24f) * g),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.68f, size.height * 0.56f),
                        radius = size.maxDimension * 0.36f
                    )
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("feelora_splash_overlay")
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FeeloraBrandMark(
                size = 96.dp,
                withSoftFrame = true,
                modifier = Modifier.graphicsLayer {
                    alpha = emblemAlpha.value
                    scaleX = emblemScale.value
                    scaleY = emblemScale.value
                }
            )
            Spacer(Modifier.height(22.dp))
            Text(
                text = "FEELORA",
                fontFamily = CormorantGaramondFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 38.sp,
                letterSpacing = 5.5.sp,
                color = tokens.inkPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(wordmarkAlpha.value)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your space to grow.",
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.inkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(taglineAlpha.value)
            )
        }

        Text(
            text = "Skip",
            style = MaterialTheme.typography.labelLarge,
            color = tokens.inkSecondary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onFinished)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("splash_skip_btn")
        )
    }
}
