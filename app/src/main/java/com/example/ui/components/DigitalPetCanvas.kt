package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.local.PetAccessory
import com.example.data.local.PetGrowthStage
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CharcoalHighlight
import com.example.ui.theme.ObsidianBase

/**
 * Geometric, sleek futuristic cyber-companion rendered procedurally on Canvas.
 * Evolves with PetGrowthStage, displays unlocked PetAccessory items, and reacts to mood/energy.
 */
@Composable
fun DigitalPetCanvas(
    petName: String,
    stage: PetGrowthStage,
    happiness: Int,
    energy: Int,
    accessory: PetAccessory,
    size: Dp = 148.dp,
    onTap: (() -> Unit)? = null
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val infiniteTransition = rememberInfiniteTransition(label = "pet_pulse")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pet_float"
    )
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pet_orbit"
    )
    val eyePulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pet_eye"
    )

    val auraIntensity = ((happiness + energy) / 200f).coerceIn(0.25f, 1f)

    Canvas(
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = "$petName digital companion, stage ${stage.title}" }
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
    ) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f + floatOffset

        // Outer astral energy halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.34f * auraIntensity),
                    secondary.copy(alpha = 0.14f * auraIntensity),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = w * 0.48f
            ),
            radius = w * 0.48f,
            center = Offset(cx, cy)
        )

        // Stage 3+ Rotating Orbital Ring
        if (stage.ordinal >= PetGrowthStage.PRISM.ordinal) {
            rotate(degrees = orbitAngle, pivot = Offset(cx, cy)) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(primary, secondary, Color.Transparent, primary)
                    ),
                    startAngle = 0f,
                    sweepAngle = 290f,
                    useCenter = false,
                    topLeft = Offset(cx - w * 0.38f, cy - w * 0.38f),
                    size = Size(w * 0.76f, w * 0.76f),
                    style = Stroke(width = w * 0.018f, cap = StrokeCap.Round)
                )
            }
        }

        // Stage 4+ Secondary Counter-Rotating Ring or Orbital Halo Accessory
        if (stage.ordinal >= PetGrowthStage.SENTINEL.ordinal || accessory == PetAccessory.ORBITAL_HALO) {
            rotate(degrees = -orbitAngle * 1.3f, pivot = Offset(cx, cy)) {
                drawCircle(
                    color = secondary.copy(alpha = 0.45f),
                    radius = w * 0.43f,
                    center = Offset(cx, cy),
                    style = Stroke(width = w * 0.012f)
                )
                drawCircle(
                    color = secondary,
                    radius = w * 0.025f,
                    center = Offset(cx + w * 0.43f, cy)
                )
                drawCircle(
                    color = primary,
                    radius = w * 0.025f,
                    center = Offset(cx - w * 0.43f, cy)
                )
            }
        }

        // Geometric Ears / Horns (evolve with stage)
        val earSpread = w * (0.16f + stage.ordinal * 0.015f)
        val earHeight = h * (0.22f + stage.ordinal * 0.015f)
        val leftEar = Path().apply {
            moveTo(cx - earSpread * 0.35f, cy - h * 0.14f)
            lineTo(cx - earSpread, cy - h * 0.14f - earHeight)
            lineTo(cx - earSpread * 1.1f, cy - h * 0.02f)
            close()
        }
        val rightEar = Path().apply {
            moveTo(cx + earSpread * 0.35f, cy - h * 0.14f)
            lineTo(cx + earSpread, cy - h * 0.14f - earHeight)
            lineTo(cx + earSpread * 1.1f, cy - h * 0.02f)
            close()
        }
        drawPath(leftEar, color = CharcoalHighlight)
        drawPath(rightEar, color = CharcoalHighlight)
        drawPath(leftEar, color = primary.copy(alpha = 0.7f), style = Stroke(width = w * 0.014f))
        drawPath(rightEar, color = secondary.copy(alpha = 0.7f), style = Stroke(width = w * 0.014f))

        // Main Obsidian Faceted Head / Core Body
        val bodyRadius = w * (0.23f + stage.ordinal * 0.01f)
        val headPath = Path().apply {
            moveTo(cx, cy - bodyRadius * 0.95f)
            lineTo(cx + bodyRadius * 0.95f, cy - bodyRadius * 0.25f)
            lineTo(cx + bodyRadius * 0.72f, cy + bodyRadius * 0.72f)
            lineTo(cx, cy + bodyRadius * 1.02f)
            lineTo(cx - bodyRadius * 0.72f, cy + bodyRadius * 0.72f)
            lineTo(cx - bodyRadius * 0.95f, cy - bodyRadius * 0.25f)
            close()
        }
        drawPath(
            path = headPath,
            brush = Brush.verticalGradient(
                colors = listOf(CharcoalElevated, ObsidianBase)
            )
        )
        drawPath(
            path = headPath,
            brush = Brush.linearGradient(listOf(primary, secondary)),
            style = Stroke(width = w * 0.02f)
        )

        // Cyber Scarf accessory
        if (accessory == PetAccessory.CYBER_SCARF) {
            val scarfPath = Path().apply {
                moveTo(cx - bodyRadius * 0.7f, cy + bodyRadius * 0.74f)
                lineTo(cx + bodyRadius * 0.7f, cy + bodyRadius * 0.74f)
                lineTo(cx + bodyRadius * 0.45f, cy + bodyRadius * 1.15f)
                lineTo(cx - bodyRadius * 0.45f, cy + bodyRadius * 1.15f)
                close()
            }
            drawPath(scarfPath, brush = Brush.horizontalGradient(listOf(secondary, primary)))
        }

        // Luminous Eyes (happy = bright cyan arc/diamond, low energy = calm slit)
        val eyeY = cy - bodyRadius * 0.05f
        val eyeOffsetX = bodyRadius * 0.36f
        val eyeColor = if (happiness >= 60) secondary else primary
        val eyeRadiusY = if (energy >= 35) bodyRadius * 0.18f else bodyRadius * 0.06f

        // Left & Right glowing eyes
        drawOval(
            color = eyeColor.copy(alpha = eyePulse),
            topLeft = Offset(cx - eyeOffsetX - bodyRadius * 0.16f, eyeY - eyeRadiusY),
            size = Size(bodyRadius * 0.32f, eyeRadiusY * 2f)
        )
        drawOval(
            color = eyeColor.copy(alpha = eyePulse),
            topLeft = Offset(cx + eyeOffsetX - bodyRadius * 0.16f, eyeY - eyeRadiusY),
            size = Size(bodyRadius * 0.32f, eyeRadiusY * 2f)
        )

        // Holo Visor accessory
        if (accessory == PetAccessory.HOLO_VISOR) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(primary.copy(alpha = 0.45f), secondary.copy(alpha = 0.55f))
                ),
                topLeft = Offset(cx - bodyRadius * 0.78f, eyeY - bodyRadius * 0.24f),
                size = Size(bodyRadius * 1.56f, bodyRadius * 0.46f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
            )
            drawRoundRect(
                color = secondary.copy(alpha = 0.85f),
                topLeft = Offset(cx - bodyRadius * 0.78f, eyeY - bodyRadius * 0.24f),
                size = Size(bodyRadius * 1.56f, bodyRadius * 0.46f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
                style = Stroke(width = w * 0.012f)
            )
        }

        // Astral Crown accessory
        if (accessory == PetAccessory.ASTRAL_CROWN) {
            val crownY = cy - bodyRadius * 1.08f
            val crownPath = Path().apply {
                moveTo(cx - bodyRadius * 0.45f, crownY + bodyRadius * 0.15f)
                lineTo(cx - bodyRadius * 0.35f, crownY - bodyRadius * 0.28f)
                lineTo(cx, crownY - bodyRadius * 0.05f)
                lineTo(cx + bodyRadius * 0.35f, crownY - bodyRadius * 0.28f)
                lineTo(cx + bodyRadius * 0.45f, crownY + bodyRadius * 0.15f)
                close()
            }
            drawPath(
                crownPath,
                brush = Brush.horizontalGradient(listOf(AmberWarning, secondary))
            )
        }

        // Forehead Core Gem (changes color with stage)
        val gemColor = when (stage) {
            PetGrowthStage.SPARK -> primary
            PetGrowthStage.PRISM -> secondary
            PetGrowthStage.SENTINEL -> AmberWarning
            PetGrowthStage.ASTRAL -> Color.White
            PetGrowthStage.SOVEREIGN -> secondary
        }
        drawCircle(
            color = gemColor,
            radius = bodyRadius * 0.11f,
            center = Offset(cx, cy - bodyRadius * 0.48f)
        )
    }
}
