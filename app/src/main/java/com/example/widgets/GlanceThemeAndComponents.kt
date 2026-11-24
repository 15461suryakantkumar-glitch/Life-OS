package com.example.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.data.local.CompanionSpecies
import com.example.data.local.PetAccessory
import com.example.data.local.PetGrowthStage
import com.example.data.local.TaskPriority

object LifeOsGlanceColors {
    val BgObsidian = ColorProvider(Color(0xFF0A0C12))
    val SurfaceCharcoal = ColorProvider(Color(0xFF131724))
    val SurfaceElevated = ColorProvider(Color(0xFF1B2032))
    val Violet = ColorProvider(Color(0xFF8B5CF6))
    val Cyan = ColorProvider(Color(0xFF22D3EE))
    val Emerald = ColorProvider(Color(0xFF10B981))
    val Amber = ColorProvider(Color(0xFFF59E0B))
    val Rose = ColorProvider(Color(0xFFF43F5E))
    val InkPrimary = ColorProvider(Color(0xFFF8FAFC))
    val InkSecondary = ColorProvider(Color(0xFF94A3B8))
    val InkMuted = ColorProvider(Color(0xFF64748B))
    val ProgressTrack = ColorProvider(Color(0xFF262C40))
}

val NAVIGATE_TO_PARAM = ActionParameters.Key<String>("navigate_to")

fun openLifeOsRouteAction(route: String): Action {
    return actionStartActivity<MainActivity>(
        parameters = actionParametersOf(NAVIGATE_TO_PARAM to route)
    )
}

fun priorityColorProvider(priority: TaskPriority): ColorProvider {
    return when (priority) {
        TaskPriority.LOW -> LifeOsGlanceColors.InkSecondary
        TaskPriority.MEDIUM -> LifeOsGlanceColors.Cyan
        TaskPriority.HIGH -> LifeOsGlanceColors.Amber
        TaskPriority.CRITICAL -> LifeOsGlanceColors.Rose
    }
}

@Composable
fun GlanceWidgetHeader(
    title: String,
    badgeText: String? = null,
    iconRes: Int? = null,
    headerAction: Action,
    trailingIconRes: Int? = null,
    trailingAction: Action? = null
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(headerAction),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = title,
                modifier = GlanceModifier.size(18.dp)
            )
            Spacer(GlanceModifier.width(6.dp))
        }
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = title,
                style = TextStyle(
                    color = LifeOsGlanceColors.InkPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
        if (!badgeText.isNullOrBlank()) {
            Box(
                modifier = GlanceModifier
                    .background(LifeOsGlanceColors.SurfaceElevated)
                    .cornerRadius(8.dp)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeText,
                    style = TextStyle(
                        color = LifeOsGlanceColors.Cyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }
        if (trailingIconRes != null && trailingAction != null) {
            Spacer(GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier
                    .size(28.dp)
                    .cornerRadius(8.dp)
                    .background(LifeOsGlanceColors.SurfaceElevated)
                    .clickable(trailingAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(trailingIconRes),
                    contentDescription = "Action",
                    modifier = GlanceModifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Renders a compact 120x120px soft-3D appointed Companion bitmap before provideContent()
 * so RemoteViews memory stays tiny while preserving species, mood & accessory visual polish.
 */
fun renderDigitalPetWidgetBitmap(
    stage: PetGrowthStage,
    happiness: Int,
    energy: Int,
    accessory: PetAccessory,
    species: CompanionSpecies = CompanionSpecies.MOCHI_BEAR
): Bitmap {
    val sizePx = 120
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = sizePx / 2f
    val cy = sizePx * 0.54f

    val (highlightCol, baseCol, shadowCol, earInnerCol, creamCol) = when (species) {
        CompanionSpecies.MOCHI_BEAR -> intArrayOf(0xFFFFF5EB.toInt(), 0xFFE7CBB3.toInt(), 0xFFC09B7C.toInt(), 0xFFFCA5A5.toInt(), 0xFFFFFBF7.toInt())
        CompanionSpecies.PEBBLE_FOX -> intArrayOf(0xFFFDBA74.toInt(), 0xFFEA580C.toInt(), 0xFF9A3412.toInt(), 0xFFFFEDD5.toInt(), 0xFFFFF7ED.toInt())
        CompanionSpecies.BOBA_OTTER -> intArrayOf(0xFFD6B59C.toInt(), 0xFF9C7254.toInt(), 0xFF6B4830.toInt(), 0xFFFCE7D6.toInt(), 0xFFF7E5D5.toInt())
        CompanionSpecies.PIP_OWL -> intArrayOf(0xFFA7F3D0.toInt(), 0xFF4F8A7B.toInt(), 0xFF28594E.toInt(), 0xFFD1FAE5.toInt(), 0xFFECFDF5.toInt())
    }

    // Soft studio glow behind companion
    val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            cx, cy, sizePx * 0.48f,
            intArrayOf(0x4410B981, 0x2238BDF8, 0x00000000),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(cx, cy, sizePx * 0.48f, auraPaint)

    // Ears behind head
    val earPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = baseCol }
    val innerEarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = earInnerCol }
    when (species) {
        CompanionSpecies.PEBBLE_FOX -> {
            val lEar = Path().apply {
                moveTo(cx - 8f, cy - 22f)
                lineTo(cx - 30f, cy - 44f)
                lineTo(cx - 30f, cy - 10f)
                close()
            }
            val rEar = Path().apply {
                moveTo(cx + 8f, cy - 22f)
                lineTo(cx + 30f, cy - 44f)
                lineTo(cx + 30f, cy - 10f)
                close()
            }
            canvas.drawPath(lEar, earPaint)
            canvas.drawPath(rEar, earPaint)
        }
        CompanionSpecies.PIP_OWL -> {
            earPaint.color = shadowCol
            val lTuft = Path().apply {
                moveTo(cx - 12f, cy - 24f)
                lineTo(cx - 28f, cy - 38f)
                lineTo(cx - 22f, cy - 14f)
                close()
            }
            val rTuft = Path().apply {
                moveTo(cx + 12f, cy - 24f)
                lineTo(cx + 28f, cy - 38f)
                lineTo(cx + 22f, cy - 14f)
                close()
            }
            canvas.drawPath(lTuft, earPaint)
            canvas.drawPath(rTuft, earPaint)
        }
        else -> {
            val rEar = sizePx * 0.11f
            canvas.drawCircle(cx - 24f, cy - 26f, rEar, earPaint)
            canvas.drawCircle(cx + 24f, cy - 26f, rEar, earPaint)
            canvas.drawCircle(cx - 24f, cy - 26f, rEar * 0.55f, innerEarPaint)
            canvas.drawCircle(cx + 24f, cy - 26f, rEar * 0.55f, innerEarPaint)
        }
    }

    // Soft-3D round head
    val headR = sizePx * 0.30f
    val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            cx - headR * 0.3f, cy - headR * 0.35f, headR * 1.6f,
            intArrayOf(highlightCol, baseCol, shadowCol),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(cx, cy, headR, headPaint)

    // Muzzle / facial patch
    val creamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = creamCol }
    if (species == CompanionSpecies.PIP_OWL) {
        canvas.drawCircle(cx - headR * 0.32f, cy - 2f, headR * 0.40f, creamPaint)
        canvas.drawCircle(cx + headR * 0.32f, cy - 2f, headR * 0.40f, creamPaint)
    } else {
        canvas.drawOval(
            RectF(cx - headR * 0.42f, cy + headR * 0.04f, cx + headR * 0.42f, cy + headR * 0.60f),
            creamPaint
        )
    }

    // Eyes & cheeks
    val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E293B.toInt() }
    val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FB7185.toInt() }
    val eyeY = cy - headR * 0.08f
    val eyeOff = headR * 0.36f
    canvas.drawOval(RectF(cx - headR * 0.66f, cy + headR * 0.14f, cx - headR * 0.36f, cy + headR * 0.32f), blushPaint)
    canvas.drawOval(RectF(cx + headR * 0.36f, cy + headR * 0.14f, cx + headR * 0.66f, cy + headR * 0.32f), blushPaint)

    if (energy < 35) {
        val sleepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1E293B.toInt()
            strokeWidth = 3f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx - eyeOff - 5f, eyeY, cx - eyeOff + 5f, eyeY, sleepPaint)
        canvas.drawLine(cx + eyeOff - 5f, eyeY, cx + eyeOff + 5f, eyeY, sleepPaint)
    } else {
        canvas.drawCircle(cx - eyeOff, eyeY, headR * 0.14f, inkPaint)
        canvas.drawCircle(cx + eyeOff, eyeY, headR * 0.14f, inkPaint)
        val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        canvas.drawCircle(cx - eyeOff - 2f, eyeY - 2f, headR * 0.05f, shinePaint)
        canvas.drawCircle(cx + eyeOff - 2f, eyeY - 2f, headR * 0.05f, shinePaint)
    }

    // Nose or beak
    if (species == CompanionSpecies.PIP_OWL) {
        val beakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF59E0B.toInt() }
        val beak = Path().apply {
            moveTo(cx - 4f, cy + 3f)
            lineTo(cx + 4f, cy + 3f)
            lineTo(cx, cy + 10f)
            close()
        }
        canvas.drawPath(beak, beakPaint)
    } else {
        canvas.drawOval(RectF(cx - 4f, cy + 3f, cx + 4f, cy + 8f), inkPaint)
    }

    // Accessories
    when (accessory) {
        PetAccessory.HOLO_VISOR -> {
            val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEE1E293B.toInt() }
            canvas.drawRoundRect(RectF(cx - headR * 0.62f, eyeY - 6f, cx - 2f, eyeY + 6f), 5f, 5f, shadePaint)
            canvas.drawRoundRect(RectF(cx + 2f, eyeY - 6f, cx + headR * 0.62f, eyeY + 6f), 5f, 5f, shadePaint)
        }
        PetAccessory.ASTRAL_CROWN -> {
            val crownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(cx - 14f, cy - headR, cx + 14f, cy - headR, 0xFFF59E0B.toInt(), 0xFFFDE047.toInt(), Shader.TileMode.CLAMP)
            }
            val crownY = cy - headR - 1f
            val crown = Path().apply {
                moveTo(cx - 14f, crownY + 4f)
                lineTo(cx - 10f, crownY - 9f)
                lineTo(cx, crownY - 2f)
                lineTo(cx + 10f, crownY - 9f)
                lineTo(cx + 14f, crownY + 4f)
                close()
            }
            canvas.drawPath(crown, crownPaint)
        }
        PetAccessory.ORBITAL_HALO -> {
            val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF10B981.toInt() }
            canvas.drawOval(RectF(cx - 12f, cy - headR - 10f, cx, cy - headR - 2f), leafPaint)
            canvas.drawOval(RectF(cx, cy - headR - 11f, cx + 12f, cy - headR - 3f), leafPaint)
        }
        else -> Unit
    }

    return bitmap
}
