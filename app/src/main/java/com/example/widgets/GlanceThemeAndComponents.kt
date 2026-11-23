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
 * Renders a compact 120x120px geometric Digital Companion bitmap before provideContent()
 * so RemoteViews memory stays tiny (~57KB) while preserving stage & accessory visual polish.
 */
fun renderDigitalPetWidgetBitmap(
    stage: PetGrowthStage,
    happiness: Int,
    energy: Int,
    accessory: PetAccessory
): Bitmap {
    val sizePx = 120
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = sizePx / 2f
    val cy = sizePx / 2f + 4f

    val violet = 0xFF8B5CF6.toInt()
    val cyan = 0xFF22D3EE.toInt()
    val amber = 0xFFF59E0B.toInt()

    // Aura glow
    val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            cx, cy, sizePx * 0.48f,
            intArrayOf(0x558B5CF6, 0x2222D3EE, 0x00000000),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(cx, cy, sizePx * 0.48f, auraPaint)

    // Stage 2+ orbital ring
    if (stage.ordinal >= PetGrowthStage.PRISM.ordinal || accessory == PetAccessory.ORBITAL_HALO) {
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = 0x9922D3EE.toInt()
        }
        canvas.drawCircle(cx, cy, sizePx * 0.40f, ringPaint)
    }

    // Ears
    val earSpread = sizePx * 0.19f
    val earHeight = sizePx * 0.22f
    val earFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF1F2435.toInt()
    }
    val earStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = violet
    }
    val leftEar = Path().apply {
        moveTo(cx - earSpread * 0.35f, cy - sizePx * 0.14f)
        lineTo(cx - earSpread, cy - sizePx * 0.14f - earHeight)
        lineTo(cx - earSpread * 1.1f, cy - sizePx * 0.02f)
        close()
    }
    val rightEar = Path().apply {
        moveTo(cx + earSpread * 0.35f, cy - sizePx * 0.14f)
        lineTo(cx + earSpread, cy - sizePx * 0.14f - earHeight)
        lineTo(cx + earSpread * 1.1f, cy - sizePx * 0.02f)
        close()
    }
    canvas.drawPath(leftEar, earFill)
    canvas.drawPath(rightEar, earFill)
    canvas.drawPath(leftEar, earStroke)
    earStroke.color = cyan
    canvas.drawPath(rightEar, earStroke)

    // Faceted Obsidian Head
    val bodyR = sizePx * 0.24f
    val headPath = Path().apply {
        moveTo(cx, cy - bodyR * 0.95f)
        lineTo(cx + bodyR * 0.95f, cy - bodyR * 0.25f)
        lineTo(cx + bodyR * 0.72f, cy + bodyR * 0.72f)
        lineTo(cx, cy + bodyR * 1.02f)
        lineTo(cx - bodyR * 0.72f, cy + bodyR * 0.72f)
        lineTo(cx - bodyR * 0.95f, cy - bodyR * 0.25f)
        close()
    }
    val headFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = LinearGradient(cx, cy - bodyR, cx, cy + bodyR, 0xFF171B28.toInt(), 0xFF0A0C12.toInt(), Shader.TileMode.CLAMP)
    }
    val headStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.6f
        shader = LinearGradient(cx - bodyR, cy, cx + bodyR, cy, violet, cyan, Shader.TileMode.CLAMP)
    }
    canvas.drawPath(headPath, headFill)
    canvas.drawPath(headPath, headStroke)

    // Cyber Scarf
    if (accessory == PetAccessory.CYBER_SCARF) {
        val scarfPath = Path().apply {
            moveTo(cx - bodyR * 0.7f, cy + bodyR * 0.74f)
            lineTo(cx + bodyR * 0.7f, cy + bodyR * 0.74f)
            lineTo(cx + bodyR * 0.45f, cy + bodyR * 1.12f)
            lineTo(cx - bodyR * 0.45f, cy + bodyR * 1.12f)
            close()
        }
        val scarfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(cx - bodyR, cy, cx + bodyR, cy, cyan, violet, Shader.TileMode.CLAMP)
        }
        canvas.drawPath(scarfPath, scarfPaint)
    }

    // Glowing Eyes
    val eyeY = cy - bodyR * 0.05f
    val eyeOff = bodyR * 0.36f
    val eyeRy = if (energy >= 35) bodyR * 0.18f else bodyR * 0.07f
    val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (happiness >= 60) cyan else violet
    }
    canvas.drawOval(RectF(cx - eyeOff - bodyR * 0.16f, eyeY - eyeRy, cx - eyeOff + bodyR * 0.16f, eyeY + eyeRy), eyePaint)
    canvas.drawOval(RectF(cx + eyeOff - bodyR * 0.16f, eyeY - eyeRy, cx + eyeOff + bodyR * 0.16f, eyeY + eyeRy), eyePaint)

    // Holo Visor
    if (accessory == PetAccessory.HOLO_VISOR) {
        val visorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x6622D3EE
        }
        val visorRect = RectF(cx - bodyR * 0.78f, eyeY - bodyR * 0.24f, cx + bodyR * 0.78f, eyeY + bodyR * 0.22f)
        canvas.drawRoundRect(visorRect, 6f, 6f, visorPaint)
    }

    // Astral Crown
    if (accessory == PetAccessory.ASTRAL_CROWN) {
        val crownY = cy - bodyR * 1.08f
        val crown = Path().apply {
            moveTo(cx - bodyR * 0.45f, crownY + bodyR * 0.15f)
            lineTo(cx - bodyR * 0.35f, crownY - bodyR * 0.28f)
            lineTo(cx, crownY - bodyR * 0.05f)
            lineTo(cx + bodyR * 0.35f, crownY - bodyR * 0.28f)
            lineTo(cx + bodyR * 0.45f, crownY + bodyR * 0.15f)
            close()
        }
        val crownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = amber }
        canvas.drawPath(crown, crownPaint)
    }

    // Core gem
    val gemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (stage.ordinal >= PetGrowthStage.SENTINEL.ordinal) amber else cyan
    }
    canvas.drawCircle(cx, cy - bodyR * 0.48f, bodyR * 0.11f, gemPaint)

    return bitmap
}
