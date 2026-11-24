package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.LifeOsDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.util.DateTimeUtils
import com.example.widgets.LifeOsWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val taskId = intent.getLongExtra(TaskReminderScheduler.EXTRA_TASK_ID, -1L)
        val notificationId = intent.getIntExtra(
            TaskReminderScheduler.EXTRA_NOTIFICATION_ID,
            TaskReminderScheduler.NOTIFICATION_ID
        )
        if (taskId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = LifeOsDatabase.getInstance(context)
                val prefsRepo = UserPreferencesRepository(context)
                val now = System.currentTimeMillis()
                val today = DateTimeUtils.todayEpochDay()
                val task = db.taskDao().getAllTasksOnce().firstOrNull { it.id == taskId }

                if (task != null) {
                    when (action) {
                        TaskReminderScheduler.ACTION_MARK_COMPLETE -> {
                            if (!task.isCompleted) {
                                val shouldAwardXp = !task.xpAwarded
                                db.taskDao().updateTask(
                                    task.copy(
                                        isCompleted = true,
                                        completedAtMillis = now,
                                        xpAwarded = true
                                    )
                                )
                                if (shouldAwardXp) {
                                    prefsRepo.awardXpAndBoostPet(
                                        xpDelta = task.xpReward,
                                        happinessDelta = 8,
                                        energyDelta = 3
                                    )
                                }
                            }
                        }
                        TaskReminderScheduler.ACTION_SNOOZE -> {
                            db.taskDao().updateTask(
                                task.copy(
                                    snoozedUntilMillis = now + 60L * 60L * 1000L,
                                    remindersDismissed = false
                                )
                            )
                        }
                        TaskReminderScheduler.ACTION_RESCHEDULE -> {
                            val nextDeadline = TaskReminderPolicy.recalculateDeadlineForTomorrowReschedule(
                                currentDeadlineEpochDay = task.deadlineEpochDay,
                                todayEpochDay = today
                            )
                            db.taskDao().updateTask(
                                task.copy(
                                    deadlineEpochDay = nextDeadline,
                                    snoozedUntilMillis = 0L,
                                    lastRemindedAtMillis = 0L,
                                    reminderCountToday = 0,
                                    lastReminderEpochDay = today,
                                    remindersDismissed = false
                                )
                            )
                        }
                        TaskReminderScheduler.ACTION_DISMISS -> {
                            db.taskDao().updateTask(
                                task.copy(remindersDismissed = true)
                            )
                        }
                    }
                }
                TaskReminderScheduler.clearReminderNotification(context, notificationId)
                LifeOsWidgetUpdater.updateAllWidgets(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
