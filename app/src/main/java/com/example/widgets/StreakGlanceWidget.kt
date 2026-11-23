package com.example.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.R
import com.example.data.local.HabitEntity
import com.example.data.local.LifeOsDatabase
import com.example.data.local.LifeOsSeeder
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserPreferencesState
import com.example.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakGlanceWidget()
}

class StreakGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (prefs, habits) = withContext(Dispatchers.IO) {
            val db = LifeOsDatabase.getInstance(context)
            LifeOsSeeder.seedInitialDataIfNeeded(db)
            val p = UserPreferencesRepository(context).preferencesFlow.first()
            val h = db.habitDao().getAllHabitsOnce()
            p to h
        }

        provideContent {
            GlanceTheme {
                StreakWidgetContent(prefs = prefs, habits = habits)
            }
        }
    }

    @Composable
    private fun StreakWidgetContent(prefs: UserPreferencesState, habits: List<HabitEntity>) {
        val size = LocalSize.current
        val isTall = size.height >= 145.dp
        val today = DateTimeUtils.todayEpochDay()
        val habitsDoneToday = habits.count { it.isCompletedOn(today) }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(LifeOsGlanceColors.BgObsidian)
                .cornerRadius(20.dp)
                .padding(12.dp)
        ) {
            GlanceWidgetHeader(
                title = "Streaks",
                badgeText = "$habitsDoneToday/${habits.size} today",
                iconRes = R.drawable.ic_widget_flame,
                headerAction = openLifeOsRouteAction("growth")
            )

            Spacer(GlanceModifier.height(8.dp))

            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(LifeOsGlanceColors.SurfaceCharcoal)
                    .cornerRadius(14.dp)
                    .clickable(openLifeOsRouteAction("growth"))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "${prefs.dailyStreak}d",
                        style = TextStyle(
                            color = LifeOsGlanceColors.Amber,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Daily Streak",
                        style = TextStyle(color = LifeOsGlanceColors.InkSecondary, fontSize = 10.sp)
                    )
                }
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "${prefs.weeklyStreak}w",
                        style = TextStyle(
                            color = LifeOsGlanceColors.Emerald,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Weekly Streak",
                        style = TextStyle(color = LifeOsGlanceColors.InkSecondary, fontSize = 10.sp)
                    )
                }
            }

            if (isTall && habits.isNotEmpty()) {
                Spacer(GlanceModifier.height(8.dp))
                habits.take(3).forEach { habit ->
                    val done = habit.isCompletedOn(today)
                    val toggleHabit = actionRunCallback<ToggleHabitWidgetAction>(
                        actionParametersOf(ToggleHabitWidgetAction.KEY_HABIT_ID to habit.id)
                    )
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(LifeOsGlanceColors.SurfaceElevated)
                            .cornerRadius(10.dp)
                            .clickable(toggleHabit)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(
                                if (done) R.drawable.ic_widget_check else R.drawable.ic_widget_uncheck
                            ),
                            contentDescription = "Toggle habit",
                            modifier = GlanceModifier.size(16.dp)
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        Text(
                            text = habit.title,
                            style = TextStyle(color = LifeOsGlanceColors.InkPrimary, fontSize = 11.sp),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Text(
                            text = "${habit.currentStreak}d",
                            style = TextStyle(color = LifeOsGlanceColors.Amber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(GlanceModifier.height(4.dp))
                }
            }
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
