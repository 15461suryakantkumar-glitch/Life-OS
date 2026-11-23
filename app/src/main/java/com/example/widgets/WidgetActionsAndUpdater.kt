package com.example.widgets

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.example.data.local.LifeOsDatabase
import com.example.data.local.LifeOsSeeder
import com.example.data.local.UserPreferencesRepository
import com.example.util.DateTimeUtils

private const val TAG = "LifeOsWidgetSync"

object LifeOsWidgetUpdater {
    suspend fun updateAllWidgets(context: Context) {
        try {
            TasksGlanceWidget().updateAll(context)
            CalendarGlanceWidget().updateAll(context)
            CountdownGlanceWidget().updateAll(context)
            XpLevelGlanceWidget().updateAll(context)
            StreakGlanceWidget().updateAll(context)
            DigitalPetGlanceWidget().updateAll(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh one or more Glance widgets", e)
        }
    }
}

class ToggleTaskWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[KEY_TASK_ID] ?: return
        val db = LifeOsDatabase.getInstance(context)
        LifeOsSeeder.seedInitialDataIfNeeded(db)
        val task = db.taskDao().getAllTasksOnce().firstOrNull { it.id == taskId } ?: return
        val nowCompleted = !task.isCompleted
        val shouldAwardXp = nowCompleted && !task.xpAwarded
        db.taskDao().updateTask(
            task.copy(
                isCompleted = nowCompleted,
                completedAtMillis = if (nowCompleted) System.currentTimeMillis() else null,
                xpAwarded = task.xpAwarded || shouldAwardXp
            )
        )
        if (shouldAwardXp) {
            UserPreferencesRepository(context).awardXpAndBoostPet(
                xpDelta = task.xpReward,
                happinessDelta = 7,
                energyDelta = 3
            )
        }
        LifeOsWidgetUpdater.updateAllWidgets(context)
    }

    companion object {
        val KEY_TASK_ID = ActionParameters.Key<Long>("task_id")
    }
}

class ToggleHabitWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val habitId = parameters[KEY_HABIT_ID] ?: return
        val db = LifeOsDatabase.getInstance(context)
        LifeOsSeeder.seedInitialDataIfNeeded(db)
        val today = DateTimeUtils.todayEpochDay()
        val habit = db.habitDao().getAllHabitsOnce().firstOrNull { it.id == habitId } ?: return

        val alreadyDone = habit.isCompletedOn(today)
        if (alreadyDone) {
            db.habitDao().updateHabit(
                habit.copy(
                    lastCompletedEpochDay = today - 1,
                    currentStreak = (habit.currentStreak - 1).coerceAtLeast(0)
                )
            )
        } else {
            val newStreak = if (habit.lastCompletedEpochDay == today - 1) habit.currentStreak + 1 else 1
            val shouldAwardXp = habit.xpAwardedEpochDay != today
            db.habitDao().updateHabit(
                habit.copy(
                    lastCompletedEpochDay = today,
                    xpAwardedEpochDay = if (shouldAwardXp) today else habit.xpAwardedEpochDay,
                    currentStreak = newStreak,
                    bestStreak = maxOf(habit.bestStreak, newStreak)
                )
            )
            if (shouldAwardXp) {
                UserPreferencesRepository(context).awardXpAndBoostPet(
                    xpDelta = habit.xpReward,
                    happinessDelta = 5,
                    energyDelta = 6
                )
            }
        }
        LifeOsWidgetUpdater.updateAllWidgets(context)
    }

    companion object {
        val KEY_HABIT_ID = ActionParameters.Key<Long>("habit_id")
    }
}

class PetInteractWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val action = parameters[KEY_PET_ACTION] ?: "FEED"
        val prefsRepo = UserPreferencesRepository(context)
        when (action) {
            "FEED" -> prefsRepo.awardXpAndBoostPet(xpDelta = 5, happinessDelta = 6, energyDelta = 12)
            "PLAY" -> prefsRepo.awardXpAndBoostPet(xpDelta = 10, happinessDelta = 12, energyDelta = -5)
            else -> prefsRepo.awardXpAndBoostPet(xpDelta = 0, happinessDelta = 4, energyDelta = 15)
        }
        LifeOsWidgetUpdater.updateAllWidgets(context)
    }

    companion object {
        val KEY_PET_ACTION = ActionParameters.Key<String>("pet_action")
    }
}

class SwitchCalendarDayAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val delta = parameters[KEY_DAY_DELTA] ?: 0
        updateAppWidgetState(context, glanceId) { prefs ->
            val current = prefs[KEY_CALENDAR_OFFSET] ?: 0
            prefs[KEY_CALENDAR_OFFSET] = if (delta == 0) 0 else (current + delta).coerceIn(0, 14)
        }
        CalendarGlanceWidget().update(context, glanceId)
    }

    companion object {
        val KEY_DAY_DELTA = ActionParameters.Key<Int>("day_delta")
        val KEY_CALENDAR_OFFSET = intPreferencesKey("calendar_day_offset")
    }
}

class CycleCountdownAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, glanceId) { prefs ->
            val current = prefs[KEY_COUNTDOWN_INDEX] ?: 0
            prefs[KEY_COUNTDOWN_INDEX] = current + 1
        }
        CountdownGlanceWidget().update(context, glanceId)
    }

    companion object {
        val KEY_COUNTDOWN_INDEX = intPreferencesKey("countdown_index")
    }
}

class RefreshAllWidgetsAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        LifeOsWidgetUpdater.updateAllWidgets(context)
    }
}
