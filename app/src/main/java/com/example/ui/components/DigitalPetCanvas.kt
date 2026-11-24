package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.local.CompanionMood
import com.example.data.local.CompanionSpecies
import com.example.data.local.PetAccessory
import com.example.data.local.PetGrowthStage
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseAlert
import kotlinx.coroutines.delay

/**
 * Cute/cool, non-futuristic soft-3D studio companion rendered procedurally on Canvas.
 * Visually adapts to the appointed CompanionSpecies, CompanionMood (5 live moods),
 * PetGrowthStage evolution halo/aura, and equipped PetAccessory.
 */
@Composable
fun DigitalPetCanvas(
    petName: String,
    stage: PetGrowthStage,
    happiness: Int,
    energy: Int,
    accessory: PetAccessory,
    species: CompanionSpecies = CompanionSpecies.MOCHI_BEAR,
    mood: CompanionMood = CompanionMood.CHILL,
    size: Dp = 148.dp,
    onTap: (() -> Unit)? = null
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val infiniteTransition = rememberInfiniteTransition(label = "pet_motion")
    val floatAmplitude = when (mood) {
        CompanionMood.JOYFUL -> 7f
        CompanionMood.SLEEPY -> 2.5f
        CompanionMood.WORRIED -> 3.5f
        else -> 5f
    }
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -floatAmplitude,
        targetValue = floatAmplitude,
        animationSpec = infiniteRepeatable(
            animation = tween(if (mood == CompanionMood.SLEEPY) 2800 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pet_float"
    )
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pet_breathe"
    )

    var isBouncing by remember { mutableStateOf(false) }
    var lastScoreSum by remember { mutableIntStateOf(happiness + energy + stage.ordinal * 100) }
    val tapScale by animateFloatAsState(
        targetValue = if (isBouncing) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pet_tap_scale"
    )
    LaunchedEffect(happiness, energy, stage) {
        val currentSum = happiness + energy + stage.ordinal * 100
        if (currentSum > lastScoreSum) {
            isBouncing = true
        }
        lastScoreSum = currentSum
    }
    LaunchedEffect(isBouncing) {
        if (isBouncing) {
            delay(240L)
            isBouncing = false
        }
    }

    Canvas(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = tapScale * breatheScale
                scaleY = tapScale * breatheScale
            }
            .semantics {
                contentDescription = "$petName (${species.displayTitle}), mood ${mood.label}, stage ${stage.title}"
            }
            .then(
                if (onTap != null) {
                    Modifier.clickable {
                        isBouncing = true
                        onTap()
                    }
                } else Modifier
            )
    ) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h * 0.50f + floatOffset

        val palette = speciesPalette(species)
        val moodHaloColor = when (mood) {
            CompanionMood.JOYFUL -> EmeraldSuccess
            CompanionMood.FOCUSED -> primary
            CompanionMood.CHILL -> secondary
            CompanionMood.SLEEPY -> Color(0xFF818CF8)
            CompanionMood.WORRIED -> AmberWarning
        }

        // Soft studio ambient backdrop glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    moodHaloColor.copy(alpha = 0.24f),
                    palette.bodyBase.copy(alpha = 0.09f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = w * 0.46f
            ),
            radius = w * 0.46f,
            center = Offset(cx, cy)
        )

        // Soft 3D contact shadow on floor
        val shadowScale = (1f - (floatOffset / 28f)).coerceIn(0.84f, 1.1f)
        drawOval(
            color = Color.Black.copy(alpha = 0.16f),
            topLeft = Offset(cx - w * 0.24f * shadowScale, h * 0.84f),
            size = Size(w * 0.48f * shadowScale, h * 0.08f)
        )

        // Chubby 3D lower body torso with gradient
        val bodyW = w * 0.46f
        val bodyH = h * 0.30f
        val bodyTop = cy + h * 0.08f
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(palette.bodyHighlight, palette.bodyBase, palette.bodyShadow),
                center = Offset(cx - bodyW * 0.18f, bodyTop + bodyH * 0.25f),
                radius = bodyW * 0.85f
            ),
            topLeft = Offset(cx - bodyW / 2f, bodyTop),
            size = Size(bodyW, bodyH)
        )
        // Belly cream patch
        drawOval(
            color = palette.bellyCream,
            topLeft = Offset(cx - bodyW * 0.30f, bodyTop + bodyH * 0.18f),
            size = Size(bodyW * 0.60f, bodyH * 0.68f)
        )

        // Cute paws resting on front
        drawCircle(
            brush = Brush.radialGradient(listOf(palette.bodyHighlight, palette.bodyBase)),
            radius = w * 0.068f,
            center = Offset(cx - w * 0.14f, bodyTop + bodyH * 0.72f)
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(palette.bodyHighlight, palette.bodyBase)),
            radius = w * 0.068f,
            center = Offset(cx + w * 0.14f, bodyTop + bodyH * 0.72f)
        )

        // Ears / tufts per species (behind head)
        drawSpeciesEars(species = species, palette = palette, cx = cx, cy = cy, w = w, h = h)

        // Cozy Scarf accessory around neck
        if (accessory == PetAccessory.CYBER_SCARF) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(RoseAlert, AmberWarning, secondary)),
                topLeft = Offset(cx - w * 0.22f, cy + h * 0.11f),
                size = Size(w * 0.44f, h * 0.09f),
                cornerRadius = CornerRadius(24f, 24f)
            )
            // Scarf tail
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(AmberWarning, RoseAlert)),
                topLeft = Offset(cx + w * 0.07f, cy + h * 0.16f),
                size = Size(w * 0.09f, h * 0.17f),
                cornerRadius = CornerRadius(14f, 14f)
            )
        }

        // Plump 3D Sphere Head
        val headR = w * 0.255f
        val headCenter = Offset(cx, cy - h * 0.03f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.bodyHighlight, palette.bodyBase, palette.bodyShadow),
                center = Offset(headCenter.x - headR * 0.32f, headCenter.y - headR * 0.35f),
                radius = headR * 1.65f
            ),
            radius = headR,
            center = headCenter
        )

        // Species face patch (fox cheeks / owl disc / bear snout / otter muzzle)
        drawSpeciesFacePatch(species = species, palette = palette, headCenter = headCenter, headR = headR)

        // Soft top-left 3D specular highlight on head
        drawOval(
            color = Color.White.copy(alpha = 0.24f),
            topLeft = Offset(headCenter.x - headR * 0.52f, headCenter.y - headR * 0.78f),
            size = Size(headR * 0.56f, headR * 0.28f)
        )

        // Rosy blush cheeks
        val blushAlpha = if (mood == CompanionMood.JOYFUL) 0.72f else 0.44f
        drawOval(
            color = Color(0xFFFB7185).copy(alpha = blushAlpha),
            topLeft = Offset(headCenter.x - headR * 0.64f, headCenter.y + headR * 0.14f),
            size = Size(headR * 0.28f, headR * 0.16f)
        )
        drawOval(
            color = Color(0xFFFB7185).copy(alpha = blushAlpha),
            topLeft = Offset(headCenter.x + headR * 0.36f, headCenter.y + headR * 0.14f),
            size = Size(headR * 0.28f, headR * 0.16f)
        )

        // Mood-reactive eyes & brows
        drawMoodFaceDetails(
            species = species,
            mood = mood,
            headCenter = headCenter,
            headR = headR,
            accentColor = moodHaloColor
        )

        // Accessories on head
        when (accessory) {
            PetAccessory.HOLO_VISOR -> {
                // Cool soft-3D rounded sunglasses
                val glassY = headCenter.y - headR * 0.20f
                val lensW = headR * 0.44f
                val lensH = headR * 0.28f
                drawRoundRect(
                    color = Color(0xFF1E293B),
                    topLeft = Offset(headCenter.x - headR * 0.58f, glassY),
                    size = Size(lensW, lensH),
                    cornerRadius = CornerRadius(14f, 14f)
                )
                drawRoundRect(
                    color = Color(0xFF1E293B),
                    topLeft = Offset(headCenter.x + headR * 0.14f, glassY),
                    size = Size(lensW, lensH),
                    cornerRadius = CornerRadius(14f, 14f)
                )
                // Glossy tint + bridge
                drawLine(
                    color = Color(0xFF0F172A),
                    start = Offset(headCenter.x - headR * 0.14f, glassY + lensH * 0.35f),
                    end = Offset(headCenter.x + headR * 0.14f, glassY + lensH * 0.35f),
                    strokeWidth = headR * 0.08f,
                    cap = StrokeCap.Round
                )
                drawRoundRect(
                    color = secondary.copy(alpha = 0.65f),
                    topLeft = Offset(headCenter.x - headR * 0.58f, glassY),
                    size = Size(lensW, lensH),
                    cornerRadius = CornerRadius(14f, 14f),
                    style = Stroke(width = headR * 0.06f)
                )
                drawRoundRect(
                    color = secondary.copy(alpha = 0.65f),
                    topLeft = Offset(headCenter.x + headR * 0.14f, glassY),
                    size = Size(lensW, lensH),
                    cornerRadius = CornerRadius(14f, 14f),
                    style = Stroke(width = headR * 0.06f)
                )
            }
            PetAccessory.ASTRAL_CROWN -> {
                val crownY = headCenter.y - headR * 0.96f
                val crownPath = Path().apply {
                    moveTo(headCenter.x - headR * 0.38f, crownY + headR * 0.14f)
                    lineTo(headCenter.x - headR * 0.30f, crownY - headR * 0.26f)
                    lineTo(headCenter.x - headR * 0.10f, crownY - headR * 0.04f)
                    lineTo(headCenter.x, crownY - headR * 0.34f)
                    lineTo(headCenter.x + headR * 0.10f, crownY - headR * 0.04f)
                    lineTo(headCenter.x + headR * 0.30f, crownY - headR * 0.26f)
                    lineTo(headCenter.x + headR * 0.38f, crownY + headR * 0.14f)
                    close()
                }
                drawPath(crownPath, brush = Brush.horizontalGradient(listOf(AmberWarning, Color(0xFFFDE047))))
            }
            PetAccessory.ORBITAL_HALO -> {
                // Cute Leaf Sprout on top of head
                val topX = headCenter.x
                val topY = headCenter.y - headR * 0.94f
                drawLine(
                    color = Color(0xFF15803D),
                    start = Offset(topX, topY + headR * 0.06f),
                    end = Offset(topX, topY - headR * 0.22f),
                    strokeWidth = headR * 0.07f,
                    cap = StrokeCap.Round
                )
                drawOval(
                    color = EmeraldSuccess,
                    topLeft = Offset(topX - headR * 0.34f, topY - headR * 0.34f),
                    size = Size(headR * 0.34f, headR * 0.18f)
                )
                drawOval(
                    color = Color(0xFF34D399),
                    topLeft = Offset(topX, topY - headR * 0.36f),
                    size = Size(headR * 0.34f, headR * 0.18f)
                )
            }
            else -> Unit
        }

        // Mood visual cues (sparkles for JOYFUL, gentle amber ring/droplet for WORRIED, zzz dot for SLEEPY)
        when (mood) {
            CompanionMood.JOYFUL -> {
                drawCircle(color = AmberWarning, radius = w * 0.022f, center = Offset(cx - w * 0.32f, cy - h * 0.20f))
                drawCircle(color = EmeraldSuccess, radius = w * 0.018f, center = Offset(cx + w * 0.33f, cy - h * 0.24f))
                drawCircle(color = Color(0xFFF472B6), radius = w * 0.016f, center = Offset(cx + w * 0.29f, cy + h * 0.04f))
            }
            CompanionMood.FOCUSED -> {
                // Subtle focus aura badge on chest
                drawCircle(
                    color = primary.copy(alpha = 0.85f),
                    radius = w * 0.026f,
                    center = Offset(cx + w * 0.28f, cy - h * 0.20f)
                )
            }
            CompanionMood.WORRIED -> {
                // Soft amber sweatdrop nudge cue beside temple
                val dropX = headCenter.x + headR * 0.92f
                val dropY = headCenter.y - headR * 0.34f
                val drop = Path().apply {
                    moveTo(dropX, dropY - headR * 0.18f)
                    lineTo(dropX + headR * 0.11f, dropY + headR * 0.04f)
                    quadraticTo(dropX, dropY + headR * 0.18f, dropX - headR * 0.11f, dropY + headR * 0.04f)
                    close()
                }
                drawPath(drop, color = AmberWarning)
            }
            CompanionMood.SLEEPY -> {
                // Quiet sleepy bubble floating up right
                drawCircle(color = Color(0xFFA5B4FC).copy(alpha = 0.7f), radius = w * 0.016f, center = Offset(cx + w * 0.24f, cy - h * 0.24f))
                drawCircle(color = Color(0xFF818CF8).copy(alpha = 0.85f), radius = w * 0.024f, center = Offset(cx + w * 0.31f, cy - h * 0.31f))
            }
            CompanionMood.CHILL -> Unit
        }
    }
}

private data class CompanionPalette(
    val bodyHighlight: Color,
    val bodyBase: Color,
    val bodyShadow: Color,
    val earInner: Color,
    val bellyCream: Color
)

private fun speciesPalette(species: CompanionSpecies): CompanionPalette = when (species) {
    CompanionSpecies.MOCHI_BEAR -> CompanionPalette(
        bodyHighlight = Color(0xFFFFF5EB),
        bodyBase = Color(0xFFE7CBB3),
        bodyShadow = Color(0xFFC09B7C),
        earInner = Color(0xFFFCA5A5),
        bellyCream = Color(0xFFFFFBF7)
    )
    CompanionSpecies.PEBBLE_FOX -> CompanionPalette(
        bodyHighlight = Color(0xFFFDBA74),
        bodyBase = Color(0xFFEA580C),
        bodyShadow = Color(0xFF9A3412),
        earInner = Color(0xFFFFEDD5),
        bellyCream = Color(0xFFFFF7ED)
    )
    CompanionSpecies.BOBA_OTTER -> CompanionPalette(
        bodyHighlight = Color(0xFFD6B59C),
        bodyBase = Color(0xFF9C7254),
        bodyShadow = Color(0xFF6B4830),
        earInner = Color(0xFFFCE7D6),
        bellyCream = Color(0xFFF7E5D5)
    )
    CompanionSpecies.PIP_OWL -> CompanionPalette(
        bodyHighlight = Color(0xFFA7F3D0),
        bodyBase = Color(0xFF4F8A7B),
        bodyShadow = Color(0xFF28594E),
        earInner = Color(0xFFD1FAE5),
        bellyCream = Color(0xFFECFDF5)
    )
}

private fun DrawScope.drawSpeciesEars(
    species: CompanionSpecies,
    palette: CompanionPalette,
    cx: Float,
    cy: Float,
    w: Float,
    h: Float
) {
    when (species) {
        CompanionSpecies.MOCHI_BEAR -> {
            val earR = w * 0.095f
            val left = Offset(cx - w * 0.19f, cy - h * 0.22f)
            val right = Offset(cx + w * 0.19f, cy - h * 0.22f)
            drawCircle(palette.bodyBase, radius = earR, center = left)
            drawCircle(palette.bodyBase, radius = earR, center = right)
            drawCircle(palette.earInner, radius = earR * 0.56f, center = left)
            drawCircle(palette.earInner, radius = earR * 0.56f, center = right)
        }
        CompanionSpecies.PEBBLE_FOX -> {
            val leftEar = Path().apply {
                moveTo(cx - w * 0.06f, cy - h * 0.20f)
                lineTo(cx - w * 0.24f, cy - h * 0.36f)
                lineTo(cx - w * 0.24f, cy - h * 0.08f)
                close()
            }
            val rightEar = Path().apply {
                moveTo(cx + w * 0.06f, cy - h * 0.20f)
                lineTo(cx + w * 0.24f, cy - h * 0.36f)
                lineTo(cx + w * 0.24f, cy - h * 0.08f)
                close()
            }
            drawPath(leftEar, color = palette.bodyBase)
            drawPath(rightEar, color = palette.bodyBase)
            val leftInner = Path().apply {
                moveTo(cx - w * 0.09f, cy - h * 0.18f)
                lineTo(cx - w * 0.20f, cy - h * 0.30f)
                lineTo(cx - w * 0.20f, cy - h * 0.12f)
                close()
            }
            val rightInner = Path().apply {
                moveTo(cx + w * 0.09f, cy - h * 0.18f)
                lineTo(cx + w * 0.20f, cy - h * 0.30f)
                lineTo(cx + w * 0.20f, cy - h * 0.12f)
                close()
            }
            drawPath(leftInner, color = palette.earInner)
            drawPath(rightInner, color = palette.earInner)
        }
        CompanionSpecies.BOBA_OTTER -> {
            val earR = w * 0.072f
            val left = Offset(cx - w * 0.22f, cy - h * 0.16f)
            val right = Offset(cx + w * 0.22f, cy - h * 0.16f)
            drawCircle(palette.bodyShadow, radius = earR, center = left)
            drawCircle(palette.bodyShadow, radius = earR, center = right)
            drawCircle(palette.earInner, radius = earR * 0.52f, center = left)
            drawCircle(palette.earInner, radius = earR * 0.52f, center = right)
        }
        CompanionSpecies.PIP_OWL -> {
            // Soft feather ear tufts
            val leftTuft = Path().apply {
                moveTo(cx - w * 0.10f, cy - h * 0.22f)
                lineTo(cx - w * 0.24f, cy - h * 0.31f)
                lineTo(cx - w * 0.19f, cy - h * 0.14f)
                close()
            }
            val rightTuft = Path().apply {
                moveTo(cx + w * 0.10f, cy - h * 0.22f)
                lineTo(cx + w * 0.24f, cy - h * 0.31f)
                lineTo(cx + w * 0.19f, cy - h * 0.14f)
                close()
            }
            drawPath(leftTuft, color = palette.bodyShadow)
            drawPath(rightTuft, color = palette.bodyShadow)
        }
    }
}

private fun DrawScope.drawSpeciesFacePatch(
    species: CompanionSpecies,
    palette: CompanionPalette,
    headCenter: Offset,
    headR: Float
) {
    when (species) {
        CompanionSpecies.MOCHI_BEAR, CompanionSpecies.BOBA_OTTER -> {
            drawOval(
                color = palette.bellyCream,
                topLeft = Offset(headCenter.x - headR * 0.38f, headCenter.y + headR * 0.06f),
                size = Size(headR * 0.76f, headR * 0.52f)
            )
        }
        CompanionSpecies.PEBBLE_FOX -> {
            drawOval(
                color = palette.bellyCream,
                topLeft = Offset(headCenter.x - headR * 0.76f, headCenter.y + headR * 0.08f),
                size = Size(headR * 1.52f, headR * 0.62f)
            )
        }
        CompanionSpecies.PIP_OWL -> {
            drawCircle(
                color = palette.bellyCream,
                radius = headR * 0.42f,
                center = Offset(headCenter.x - headR * 0.32f, headCenter.y - headR * 0.02f)
            )
            drawCircle(
                color = palette.bellyCream,
                radius = headR * 0.42f,
                center = Offset(headCenter.x + headR * 0.32f, headCenter.y - headR * 0.02f)
            )
        }
    }
}

private fun DrawScope.drawMoodFaceDetails(
    species: CompanionSpecies,
    mood: CompanionMood,
    headCenter: Offset,
    headR: Float,
    accentColor: Color
) {
    val eyeY = headCenter.y - headR * 0.06f
    val eyeOff = headR * 0.36f
    val ink = Color(0xFF1E293B)

    when (mood) {
        CompanionMood.JOYFUL -> {
            // Happy "^ ^" arcs
            val leftArc = Path().apply {
                moveTo(headCenter.x - eyeOff - headR * 0.14f, eyeY + headR * 0.04f)
                quadraticTo(headCenter.x - eyeOff, eyeY - headR * 0.16f, headCenter.x - eyeOff + headR * 0.14f, eyeY + headR * 0.04f)
            }
            val rightArc = Path().apply {
                moveTo(headCenter.x + eyeOff - headR * 0.14f, eyeY + headR * 0.04f)
                quadraticTo(headCenter.x + eyeOff, eyeY - headR * 0.16f, headCenter.x + eyeOff + headR * 0.14f, eyeY + headR * 0.04f)
            }
            drawPath(leftArc, color = ink, style = Stroke(width = headR * 0.09f, cap = StrokeCap.Round))
            drawPath(rightArc, color = ink, style = Stroke(width = headR * 0.09f, cap = StrokeCap.Round))
        }
        CompanionMood.SLEEPY -> {
            // Gentle downward closed eyelids
            val leftSleep = Path().apply {
                moveTo(headCenter.x - eyeOff - headR * 0.14f, eyeY)
                quadraticTo(headCenter.x - eyeOff, eyeY + headR * 0.10f, headCenter.x - eyeOff + headR * 0.14f, eyeY)
            }
            val rightSleep = Path().apply {
                moveTo(headCenter.x + eyeOff - headR * 0.14f, eyeY)
                quadraticTo(headCenter.x + eyeOff, eyeY + headR * 0.10f, headCenter.x + eyeOff + headR * 0.14f, eyeY)
            }
            drawPath(leftSleep, color = ink, style = Stroke(width = headR * 0.08f, cap = StrokeCap.Round))
            drawPath(rightSleep, color = ink, style = Stroke(width = headR * 0.08f, cap = StrokeCap.Round))
        }
        CompanionMood.WORRIED -> {
            // Glossy eyes + concerned eyebrows
            drawCircle(ink, radius = headR * 0.13f, center = Offset(headCenter.x - eyeOff, eyeY + headR * 0.02f))
            drawCircle(ink, radius = headR * 0.13f, center = Offset(headCenter.x + eyeOff, eyeY + headR * 0.02f))
            drawCircle(Color.White, radius = headR * 0.045f, center = Offset(headCenter.x - eyeOff - headR * 0.04f, eyeY - headR * 0.02f))
            drawCircle(Color.White, radius = headR * 0.045f, center = Offset(headCenter.x + eyeOff - headR * 0.04f, eyeY - headR * 0.02f))
            // Worried brows tilted up toward center
            drawLine(
                color = ink,
                start = Offset(headCenter.x - eyeOff - headR * 0.16f, eyeY - headR * 0.16f),
                end = Offset(headCenter.x - eyeOff + headR * 0.12f, eyeY - headR * 0.24f),
                strokeWidth = headR * 0.07f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = ink,
                start = Offset(headCenter.x + eyeOff - headR * 0.12f, eyeY - headR * 0.24f),
                end = Offset(headCenter.x + eyeOff + headR * 0.16f, eyeY - headR * 0.16f),
                strokeWidth = headR * 0.07f,
                cap = StrokeCap.Round
            )
        }
        CompanionMood.FOCUSED -> {
            // Crisp determined glossy eyes + slight brow lock
            drawCircle(ink, radius = headR * 0.14f, center = Offset(headCenter.x - eyeOff, eyeY))
            drawCircle(ink, radius = headR * 0.14f, center = Offset(headCenter.x + eyeOff, eyeY))
            drawCircle(accentColor, radius = headR * 0.048f, center = Offset(headCenter.x - eyeOff + headR * 0.04f, eyeY + headR * 0.04f))
            drawCircle(accentColor, radius = headR * 0.048f, center = Offset(headCenter.x + eyeOff + headR * 0.04f, eyeY + headR * 0.04f))
            drawCircle(Color.White, radius = headR * 0.05f, center = Offset(headCenter.x - eyeOff - headR * 0.04f, eyeY - headR * 0.05f))
            drawCircle(Color.White, radius = headR * 0.05f, center = Offset(headCenter.x + eyeOff - headR * 0.04f, eyeY - headR * 0.05f))
            drawLine(
                color = ink,
                start = Offset(headCenter.x - eyeOff - headR * 0.14f, eyeY - headR * 0.24f),
                end = Offset(headCenter.x - eyeOff + headR * 0.14f, eyeY - headR * 0.17f),
                strokeWidth = headR * 0.065f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = ink,
                start = Offset(headCenter.x + eyeOff - headR * 0.14f, eyeY - headR * 0.17f),
                end = Offset(headCenter.x + eyeOff + headR * 0.14f, eyeY - headR * 0.24f),
                strokeWidth = headR * 0.065f,
                cap = StrokeCap.Round
            )
        }
        CompanionMood.CHILL -> {
            // Bright soft-3D eyes with big sparkle catchlights
            drawCircle(ink, radius = headR * 0.145f, center = Offset(headCenter.x - eyeOff, eyeY))
            drawCircle(ink, radius = headR * 0.145f, center = Offset(headCenter.x + eyeOff, eyeY))
            drawCircle(Color.White, radius = headR * 0.055f, center = Offset(headCenter.x - eyeOff - headR * 0.045f, eyeY - headR * 0.045f))
            drawCircle(Color.White, radius = headR * 0.055f, center = Offset(headCenter.x + eyeOff - headR * 0.045f, eyeY - headR * 0.045f))
            drawCircle(Color.White.copy(alpha = 0.7f), radius = headR * 0.025f, center = Offset(headCenter.x - eyeOff + headR * 0.05f, eyeY + headR * 0.05f))
            drawCircle(Color.White.copy(alpha = 0.7f), radius = headR * 0.025f, center = Offset(headCenter.x + eyeOff + headR * 0.05f, eyeY + headR * 0.05f))
        }
    }

    // Nose or Beak + Mouth expression
    if (species == CompanionSpecies.PIP_OWL) {
        val beak = Path().apply {
            moveTo(headCenter.x - headR * 0.11f, headCenter.y + headR * 0.08f)
            lineTo(headCenter.x + headR * 0.11f, headCenter.y + headR * 0.08f)
            lineTo(headCenter.x, headCenter.y + headR * 0.25f)
            close()
        }
        drawPath(beak, color = AmberWarning)
    } else {
        drawOval(
            color = ink,
            topLeft = Offset(headCenter.x - headR * 0.09f, headCenter.y + headR * 0.10f),
            size = Size(headR * 0.18f, headR * 0.12f)
        )
        val mouthPath = Path().apply {
            if (mood == CompanionMood.WORRIED) {
                moveTo(headCenter.x - headR * 0.12f, headCenter.y + headR * 0.34f)
                quadraticTo(headCenter.x, headCenter.y + headR * 0.25f, headCenter.x + headR * 0.12f, headCenter.y + headR * 0.34f)
            } else {
                moveTo(headCenter.x - headR * 0.13f, headCenter.y + headR * 0.27f)
                quadraticTo(headCenter.x, headCenter.y + headR * 0.39f, headCenter.x + headR * 0.13f, headCenter.y + headR * 0.27f)
            }
        }
        drawPath(mouthPath, color = ink, style = Stroke(width = headR * 0.06f, cap = StrokeCap.Round))
    }
}
