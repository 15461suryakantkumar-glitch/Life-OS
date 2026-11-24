package com.example.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
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
import com.example.data.local.TaskPriority
import java.util.Calendar

object LifeOsGlanceColors {
    val BgObsidian = ColorProvider(Color(0xFF161124))
    val SurfaceCharcoal = ColorProvider(Color(0xFF261E40))
    val SurfaceElevated = ColorProvider(Color(0xFF312652))
    val Violet = ColorProvider(Color(0xFFA78BFA))
    val Cyan = ColorProvider(Color(0xFFF3C6DC))
    val Emerald = ColorProvider(Color(0xFF10B981))
    val Amber = ColorProvider(Color(0xFFD97706))
    val Rose = ColorProvider(Color(0xFFE11D48))
    val InkPrimary = ColorProvider(Color(0xFFFAF7FF))
    val InkSecondary = ColorProvider(Color(0xFFD6CCEE))
    val InkMuted = ColorProvider(Color(0xFF9E90C2))
    val ProgressTrack = ColorProvider(Color(0xFF46386B))
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
        TaskPriority.MEDIUM -> LifeOsGlanceColors.Violet
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
                    modifier = ModifierSize16()
                )
            }
        }
    }
}

private fun ModifierSize16() = GlanceModifier.size(16.dp)

fun decodeKuttaWidgetBitmap(
    context: Context,
    happiness: Int,
    energy: Int
): Bitmap {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    @DrawableRes val resId = when {
        hour >= 22 || hour < 6 || energy < 35 -> R.drawable.kutta_sleeping
        happiness >= 85 -> R.drawable.kutta_happy
        happiness < 45 -> R.drawable.kutta_worried
        hour in 6..10 -> R.drawable.kutta_morning
        else -> R.drawable.kutta_idle
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
    val decoded = BitmapFactory.decodeResource(context.resources, resId, opts)
        ?: BitmapFactory.decodeResource(context.resources, R.drawable.kutta_idle)
    return Bitmap.createScaledBitmap(decoded, 140, 140, true)
}
