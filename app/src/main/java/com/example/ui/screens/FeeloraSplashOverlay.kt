package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kutta.LocalReducedMotion
import com.example.ui.components.FeeloraHeartEmblem
import com.example.ui.components.FeeloraSparkleBackdrop
import com.example.ui.theme.CaveatFontFamily
import com.example.ui.theme.OutfitFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Brief ~2.2s FEELORA opening brand splash overlay for returning signed-in launches.
 * Uses the same midnight-violet sparkle canvas, glowing smiling heart emblem,
 * Outfit "Feelora" wordmark, and "Feel · Heal · Grow".
 */
@Composable
fun FeeloraSplashOverlay(
    audioLogoEnabled: Boolean = true,
    onPlayAudioLogo: () -> Unit,
    onFinished: () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current

    val emblemScale = remember { Animatable(if (reducedMotion) 1f else 0.78f) }
    val emblemAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val wordmarkAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val taglineAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val containerAlpha = remember { Animatable(1f) }

    LaunchedEffect(reducedMotion, audioLogoEnabled) {
        if (audioLogoEnabled) {
            onPlayAudioLogo()
        }
        if (reducedMotion) {
            delay(650L)
            containerAlpha.animateTo(0f, tween(220))
            onFinished()
            return@LaunchedEffect
        }

        launch {
            delay(140L)
            emblemAlpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        }
        launch {
            delay(140L)
            emblemScale.animateTo(1f, tween(760, easing = FastOutSlowInEasing))
        }
        launch {
            delay(520L)
            wordmarkAlpha.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
        }
        launch {
            delay(860L)
            taglineAlpha.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }

        delay(2050L)
        containerAlpha.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(containerAlpha.value)
            .testTag("feelora_splash_overlay")
    ) {
        FeeloraSparkleBackdrop(fullDarkMode = true, sparklePhase = 0.25f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                FeeloraHeartEmblem(
                    size = 104.dp,
                    modifier = Modifier.graphicsLayer {
                        alpha = emblemAlpha.value
                        scaleX = emblemScale.value
                        scaleY = emblemScale.value
                    }
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Feelora",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 42.sp,
                    letterSpacing = (-0.5).sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(wordmarkAlpha.value)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Feel  ·  Heal  ·  Grow",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    letterSpacing = 1.5.sp,
                    color = Color(0xFFE7DFFF),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(wordmarkAlpha.value)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Your safe space for a happier mind ♡",
                    fontFamily = CaveatFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 23.sp,
                    color = Color(0xFFF3EBFF),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(taglineAlpha.value)
                )
            }

            Text(
                text = "Skip",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = Color(0xFFE3D8FF),
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
}
