package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.local.CompanionMood
import com.example.kutta.KuttaEmotion
import com.example.kutta.KuttaEventHandler
import com.example.kutta.KuttaMascot

/**
 * Legacy-compatible entrypoint now permanently bound to KUTTA.
 */
@Composable
fun DigitalPetCanvas(
    mood: CompanionMood = CompanionMood.CHILL,
    size: Dp = 132.dp,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null
) {
    val emotion = when (mood) {
        CompanionMood.JOYFUL, CompanionMood.CHILL -> KuttaEmotion.HAPPY
        CompanionMood.FOCUSED -> KuttaEmotion.FOCUSED
        CompanionMood.SLEEPY -> KuttaEmotion.SLEEPY
        CompanionMood.WORRIED -> KuttaEmotion.WORRIED
    }
    KuttaMascot(
        visualState = KuttaEventHandler.stateForBaseline(emotion),
        size = size,
        modifier = modifier,
        onTap = onTap
    )
}
