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
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.R
import com.example.data.local.LifeOsDatabase
import com.example.data.local.LifeOsSeeder
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserPreferencesState
import com.example.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class XpLevelWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = XpLevelGlanceWidget()
}

class XpLevelGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (prefs, prodScore) = withContext(Dispatchers.IO) {
            val db = LifeOsDatabase.getInstance(context)
            LifeOsSeeder.seedInitialDataIfNeeded(db)
            val p = UserPreferencesRepository(context).preferencesFlow.first()
            val tasks = db.taskDao().getAllTasksOnce()
            val habits = db.habitDao().getAllHabitsOnce()
            val today = DateTimeUtils.todayEpochDay()
            val screenMinutes = db.appUsageDao().observeAllLogs().first()
                .filter { it.dateEpochDay == today }
                .sumOf { it.minutesUsed }
            val score = com.example.util.ProductivityCalculator.computeScore(
                totalTasks = tasks.size,
                completedTasks = tasks.count { it.isCompleted },
                totalHabits = habits.size,
                completedHabitsToday = habits.count { it.isCompletedOn(today) },
                focusMinutesToday = p.focusMinutesLoggedToday,
                dailyFocusGoalMinutes = p.dailyFocusGoalMinutes,
                screenMinutesToday = screenMinutes,
                dailyScreenLimitMinutes = p.dailyScreenLimitMinutes
            )
            p to score
        }

        provideContent {
            GlanceTheme {
                XpWidgetContent(prefs = prefs, productivityScore = prodScore)
            }
        }
    }

    @Composable
    private fun XpWidgetContent(prefs: UserPreferencesState, productivityScore: Int) {
        val size = LocalSize.current
        val isTall = size.height >= 130.dp
        val progress = prefs.xpIntoCurrentLevel.toFloat() / prefs.xpPerLevel.coerceAtLeast(1)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(LifeOsGlanceColors.BgObsidian)
                .cornerRadius(20.dp)
                .clickable(openLifeOsRouteAction("growth"))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlanceWidgetHeader(
                title = "Level ${prefs.level}",
                badgeText = "${prefs.totalXp} XP",
                iconRes = R.drawable.ic_widget_bolt,
                headerAction = openLifeOsRouteAction("growth"),
                trailingIconRes = R.drawable.ic_widget_refresh,
                trailingAction = actionRunCallback<RefreshAllWidgetsAction>()
            )

            Spacer(GlanceModifier.height(10.dp))

            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(LifeOsGlanceColors.SurfaceCharcoal)
                    .cornerRadius(14.dp)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${prefs.xpIntoCurrentLevel} / ${prefs.xpPerLevel} XP",
                        style = TextStyle(
                            color = LifeOsGlanceColors.InkPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Text(
                        text = "Lv.${prefs.level + 1}",
                        style = TextStyle(color = LifeOsGlanceColors.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
                    color = LifeOsGlanceColors.Violet,
                    backgroundColor = LifeOsGlanceColors.ProgressTrack
                )
                if (isTall) {
                    Spacer(GlanceModifier.height(8.dp))
                    Text(
                        text = "Productivity Score: ${productivityScore}% · ${prefs.petStage.title}",
                        style = TextStyle(color = LifeOsGlanceColors.InkSecondary, fontSize = 10.sp),
                        maxLines = 1
                    )
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
