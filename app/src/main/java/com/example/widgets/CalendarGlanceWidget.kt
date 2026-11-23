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
import androidx.glance.action.actionParametersOf
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
import com.example.data.local.RecurrencePattern
import com.example.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarGlanceWidget()
}

class CalendarGlanceWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val allEvents = withContext(Dispatchers.IO) {
            val db = LifeOsDatabase.getInstance(context)
            LifeOsSeeder.seedInitialDataIfNeeded(db)
            db.calendarEventDao().getAllEventsOnce().filter { !it.isCountdown }
        }

        provideContent {
            val prefs = currentState<Preferences>()
            val dayOffset = prefs[SwitchCalendarDayAction.KEY_CALENDAR_OFFSET] ?: 0
            GlanceTheme {
                CalendarWidgetContent(allEvents = allEvents, dayOffset = dayOffset)
            }
        }
    }

    @Composable
    private fun CalendarWidgetContent(allEvents: List<CalendarEventEntity>, dayOffset: Int) {
        val size = LocalSize.current
        val isTall = size.height >= 165.dp
        val isWide = size.width >= 210.dp
        val targetDay = DateTimeUtils.todayEpochDay() + dayOffset
        val dayEvents = allEvents.filter {
            it.dateEpochDay == targetDay ||
                (it.recurrence == RecurrencePattern.DAILY && it.dateEpochDay <= targetDay)
        }
        val maxItems = if (isTall) 4 else 2

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(LifeOsGlanceColors.BgObsidian)
                .cornerRadius(20.dp)
                .padding(12.dp)
        ) {
            GlanceWidgetHeader(
                title = DateTimeUtils.formatFullDate(targetDay),
                badgeText = when (dayOffset) {
                    0 -> "TODAY"
                    1 -> "TOMORROW"
                    else -> "+${dayOffset}D"
                },
                iconRes = R.drawable.ic_widget_calendar,
                headerAction = openLifeOsRouteAction("calendar")
            )

            Spacer(GlanceModifier.height(6.dp))

            // Interactive day switcher strip
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                DayStepPill(
                    label = if (dayOffset > 0) "Prev" else "Today",
                    isActive = dayOffset == 0,
                    delta = if (dayOffset > 0) -1 else 0
                )
                Spacer(GlanceModifier.width(6.dp))
                DayStepPill(
                    label = "Next Day ›",
                    isActive = dayOffset > 0,
                    delta = 1
                )
            }

            Spacer(GlanceModifier.height(8.dp))

            if (dayEvents.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(LifeOsGlanceColors.SurfaceCharcoal)
                        .cornerRadius(12.dp)
                        .clickable(openLifeOsRouteAction("calendar"))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No events scheduled · Tap to plan",
                        style = TextStyle(color = LifeOsGlanceColors.InkSecondary, fontSize = 11.sp)
                    )
                }
            } else {
                dayEvents.take(maxItems).forEach { ev ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(LifeOsGlanceColors.SurfaceCharcoal)
                            .cornerRadius(12.dp)
                            .clickable(openLifeOsRouteAction("calendar"))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .width(4.dp)
                                .height(26.dp)
                                .cornerRadius(2.dp)
                                .background(LifeOsGlanceColors.Cyan)
                        ) {}
                        Spacer(GlanceModifier.width(8.dp))
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = ev.title,
                                style = TextStyle(
                                    color = LifeOsGlanceColors.InkPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = "${DateTimeUtils.formatTimeOfDay(ev.startMinuteOfDay)} · ${ev.durationMinutes}m",
                                style = TextStyle(
                                    color = LifeOsGlanceColors.InkSecondary,
                                    fontSize = 10.sp
                                ),
                                maxLines = 1
                            )
                        }
                        if (isWide) {
                            Spacer(GlanceModifier.width(6.dp))
                            Text(
                                text = ev.category.label,
                                style = TextStyle(
                                    color = LifeOsGlanceColors.Violet,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                    Spacer(GlanceModifier.height(5.dp))
                }
            }
        }
    }

    @Composable
    private fun DayStepPill(label: String, isActive: Boolean, delta: Int) {
        Box(
            modifier = GlanceModifier
                .background(if (isActive) LifeOsGlanceColors.Violet else LifeOsGlanceColors.SurfaceElevated)
                .cornerRadius(8.dp)
                .clickable(
                    actionRunCallback<SwitchCalendarDayAction>(
                        actionParametersOf(SwitchCalendarDayAction.KEY_DAY_DELTA to delta)
                    )
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = LifeOsGlanceColors.InkPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
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
