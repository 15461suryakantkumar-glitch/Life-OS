package com.example.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.R
import com.example.data.local.CalendarEventEntity
import com.example.data.local.LifeOsDatabase
import com.example.data.local.LifeOsSeeder
import com.example.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownGlanceWidget()
}

class CountdownGlanceWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = DateTimeUtils.todayEpochDay()
        val countdowns = withContext(Dispatchers.IO) {
            val db = LifeOsDatabase.getInstance(context)
            LifeOsSeeder.seedInitialDataIfNeeded(db)
            db.calendarEventDao().getAllEventsOnce()
                .filter { it.isCountdown && it.dateEpochDay >= today }
        }

        provideContent {
            val prefs = currentState<Preferences>()
            val index = prefs[CycleCountdownAction.KEY_COUNTDOWN_INDEX] ?: 0
            GlanceTheme {
                CountdownWidgetContent(countdowns = countdowns, today = today, index = index)
            }
        }
    }

    @Composable
    private fun CountdownWidgetContent(
        countdowns: List<CalendarEventEntity>,
        today: Long,
        index: Int
    ) {
        val size = LocalSize.current
        val isWide = size.width >= 210.dp && countdowns.size > 1

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(LifeOsGlanceColors.BgObsidian)
                .cornerRadius(20.dp)
                .padding(12.dp)
        ) {
            GlanceWidgetHeader(
                title = "Countdowns",
                badgeText = if (countdowns.isNotEmpty()) "${countdowns.size} active" else null,
                iconRes = R.drawable.ic_widget_flag,
                headerAction = openLifeOsRouteAction("calendar"),
                trailingIconRes = if (countdowns.size > 1) R.drawable.ic_widget_refresh else null,
                trailingAction = if (countdowns.size > 1) actionRunCallback<CycleCountdownAction>() else null
            )

            Spacer(GlanceModifier.height(8.dp))

            if (countdowns.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(LifeOsGlanceColors.SurfaceCharcoal)
                        .cornerRadius(14.dp)
                        .clickable(openLifeOsRouteAction("calendar"))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No milestones · Add in Calendar",
                        style = TextStyle(color = LifeOsGlanceColors.InkSecondary, fontSize = 11.sp)
                    )
                }
            } else if (isWide) {
                Row(modifier = GlanceModifier.fillMaxSize()) {
                    val primary = countdowns[index % countdowns.size]
                    val secondary = countdowns[(index + 1) % countdowns.size]
                    CountdownTile(item = primary, today = today, modifier = GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(8.dp))
                    CountdownTile(item = secondary, today = today, modifier = GlanceModifier.defaultWeight())
                }
            } else {
                val featured = countdowns[index % countdowns.size]
                CountdownTile(item = featured, today = today, modifier = GlanceModifier.fillMaxSize())
            }
        }
    }

    @Composable
    private fun CountdownTile(
        item: CalendarEventEntity,
        today: Long,
        modifier: GlanceModifier
    ) {
        val daysLeft = DateTimeUtils.daysBetween(today, item.dateEpochDay).coerceAtLeast(0)
        Column(
            modifier = modifier
                .background(LifeOsGlanceColors.SurfaceCharcoal)
                .cornerRadius(14.dp)
                .clickable(openLifeOsRouteAction("calendar"))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${daysLeft}d",
                style = TextStyle(
                    color = LifeOsGlanceColors.Cyan,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = item.title,
                style = TextStyle(
                    color = LifeOsGlanceColors.InkPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 2
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = DateTimeUtils.formatFullDate(item.dateEpochDay),
                style = TextStyle(
                    color = LifeOsGlanceColors.Amber,
                    fontSize = 10.sp
                ),
                maxLines = 1
            )
        }
    }

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable
    ) {
        val fallback = RemoteViews(context.packageName, R.layout.widget_loading)
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, fallback)
    }
}
