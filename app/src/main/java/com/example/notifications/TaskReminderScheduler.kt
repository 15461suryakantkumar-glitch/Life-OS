package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.local.CalendarEventEntity
import com.example.data.local.LifeOsDatabase
import com.example.data.local.RecurrencePattern
import com.example.data.local.TaskEntity
import com.example.data.local.UserPreferencesRepository
import com.example.util.DateTimeUtils
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

object TaskReminderPolicy {

    fun isUserCurrentlyBusyInEvent(
        events: List<CalendarEventEntity>,
        todayEpochDay: Long,
        minuteOfDay: Int
    ): CalendarEventEntity? {
        return events.firstOrNull { ev ->
            if (ev.isCountdown) return@firstOrNull false
            val occursToday = ev.dateEpochDay == todayEpochDay ||
                (ev.recurrence == RecurrencePattern.DAILY && ev.dateEpochDay <= todayEpochDay)
            if (!occursToday) return@firstOrNull false
            val end = ev.startMinuteOfDay + ev.durationMinutes
            minuteOfDay in ev.startMinuteOfDay until end
        }
    }

    fun selectEligibleTasks(
        tasks: List<TaskEntity>,
        events: List<CalendarEventEntity>,
        todayEpochDay: Long,
        minuteOfDay: Int,
        nowMillis: Long,
        frequencyHours: Int,
        maxRemindersPerTaskPerDay: Int
    ): List<TaskEntity> {
        if (isUserCurrentlyBusyInEvent(events, todayEpochDay, minuteOfDay) != null) {
            return emptyList()
        }

        val minIntervalMillis = frequencyHours.coerceIn(1, 6) * 60L * 60L * 1000L
        return tasks
            .filter { task ->
                if (task.isCompleted || task.remindersDismissed) return@filter false
                if (task.deadlineEpochDay > todayEpochDay + 1L) return@filter false
                if (task.snoozedUntilMillis > nowMillis) return@filter false
                if (task.lastRemindedAtMillis > 0L && (nowMillis - task.lastRemindedAtMillis) < minIntervalMillis) {
                    return@filter false
                }
                val countToday = if (task.lastReminderEpochDay == todayEpochDay) task.reminderCountToday else 0
                if (countToday >= maxRemindersPerTaskPerDay.coerceIn(1, 10)) {
                    return@filter false
                }
                true
            }
            .sortedWith(
                compareByDescending<TaskEntity> { it.deadlineEpochDay < todayEpochDay }
                    .thenByDescending { it.priority.ordinal }
                    .thenBy { it.deadlineEpochDay }
                    .thenBy { if (it.lastReminderEpochDay == todayEpochDay) it.reminderCountToday else 0 }
            )
    }
}

object TaskReminderScheduler {
    const val WORK_NAME = "lifeos_pending_task_reminder_2h"
    const val CHANNEL_ID = "lifeos_task_reminders"
    const val NOTIFICATION_ID = 4201

    const val ACTION_MARK_COMPLETE = "com.example.notifications.ACTION_MARK_COMPLETE"
    const val ACTION_SNOOZE = "com.example.notifications.ACTION_SNOOZE"
    const val ACTION_RESCHEDULE = "com.example.notifications.ACTION_RESCHEDULE"
    const val ACTION_DISMISS = "com.example.notifications.ACTION_DISMISS"
    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    fun syncSchedule(
        context: Context,
        notificationsEnabled: Boolean,
        taskRemindersEnabled: Boolean,
        frequencyHours: Int = 2
    ) {
        val wm = WorkManager.getInstance(context)
        if (!notificationsEnabled || !taskRemindersEnabled) {
            wm.cancelUniqueWork(WORK_NAME)
            clearReminderNotification(context)
            return
        }
        val safeHours = frequencyHours.coerceIn(1, 6).toLong()
        val request = PeriodicWorkRequestBuilder<PendingTaskReminderWorker>(safeHours, TimeUnit.HOURS)
            .setInitialDelay(safeHours, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun clearReminderNotification(context: Context, notificationId: Int = NOTIFICATION_ID) {
        try {
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (_: Exception) {
        }
    }

    fun isWithinQuietHours(
        currentHour: Int,
        quietHoursEnabled: Boolean,
        startHour: Int,
        endHour: Int
    ): Boolean {
        if (!quietHoursEnabled) return false
        val h = currentHour.coerceIn(0, 23)
        val s = startHour.coerceIn(0, 23)
        val e = endHour.coerceIn(0, 23)
        if (s == e) return false
        return if (s < e) {
            h in s until e
        } else {
            h >= s || h < e
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Smart Task Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Gentle reminders for pending tasks with quick Complete, Snooze & Reschedule actions."
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun postPendingTasksNotification(
        context: Context,
        pendingCount: Int,
        topTaskTitle: String,
        companionName: String,
        topTaskId: Long = -1L
    ): Boolean {
        if (pendingCount <= 0) {
            clearReminderNotification(context)
            return false
        }
        if (!hasNotificationPermission(context)) return false
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "tasks")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (pendingCount == 1) {
            "Task still waiting: $topTaskTitle"
        } else {
            "$pendingCount tasks waiting · Next: $topTaskTitle"
        }
        val body = if (pendingCount == 1) {
            "Tap to open Tasks or mark complete directly below."
        } else {
            "Top priority: \"$topTaskTitle\" (+${pendingCount - 1} more). Tap an action below."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_check)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)

        if (topTaskId > 0L) {
            builder
                .addAction(
                    R.drawable.ic_widget_check,
                    "Complete",
                    buildActionPendingIntent(context, ACTION_MARK_COMPLETE, topTaskId, 11)
                )
                .addAction(
                    R.drawable.ic_widget_refresh,
                    "Later (1h)",
                    buildActionPendingIntent(context, ACTION_SNOOZE, topTaskId, 12)
                )
                .addAction(
                    R.drawable.ic_widget_calendar,
                    "Tomorrow",
                    buildActionPendingIntent(context, ACTION_RESCHEDULE, topTaskId, 13)
                )
                .setDeleteIntent(buildActionPendingIntent(context, ACTION_DISMISS, topTaskId, 14))
        }

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun buildActionPendingIntent(
        context: Context,
        action: String,
        taskId: Long,
        requestOffset: Int
    ): PendingIntent {
        val actionIntent = Intent(context, TaskReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
        }
        return PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID + requestOffset,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    const val BRIEFING_WORK_NAME = "feelora_suru_daily_briefing"
    const val BRIEFING_NOTIFICATION_ID = 4202

    fun syncDailyBriefings(
        context: Context,
        notificationsEnabled: Boolean,
        morningEnabled: Boolean,
        eveningEnabled: Boolean
    ) {
        val wm = WorkManager.getInstance(context)
        if (!notificationsEnabled || (!morningEnabled && !eveningEnabled)) {
            wm.cancelUniqueWork(BRIEFING_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<SuruDailyBriefingWorker>(1, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(
            BRIEFING_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun postSuruBriefingNotification(
        context: Context,
        isMorning: Boolean,
        title: String,
        body: String
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "life_ai")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            BRIEFING_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_check)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(
                BRIEFING_NOTIFICATION_ID + if (isMorning) 0 else 1,
                notification
            )
            true
        } catch (_: SecurityException) {
            false
        }
    }

    internal fun shouldFireBriefingForWindow(
        currentHour: Int,
        targetHour: Int,
        lastFiredDay: Long,
        todayEpochDay: Long
    ): Boolean {
        return lastFiredDay != todayEpochDay && currentHour == targetHour
    }
}

class PendingTaskReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = UserPreferencesRepository(applicationContext).preferencesFlow.first()
        if (!prefs.isSignedIn || !prefs.notificationsEnabled || !prefs.taskRemindersEnabled) {
            TaskReminderScheduler.clearReminderNotification(applicationContext)
            return Result.success()
        }

        val cal = Calendar.getInstance()
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val minuteOfDay = currentHour * 60 + cal.get(Calendar.MINUTE)
        if (TaskReminderScheduler.isWithinQuietHours(
                currentHour = currentHour,
                quietHoursEnabled = prefs.quietHoursEnabled,
                startHour = prefs.quietStartHour,
                endHour = prefs.quietEndHour
            )
        ) {
            return Result.success()
        }

        val db = LifeOsDatabase.getInstance(applicationContext)
        val allTasks = db.taskDao().getAllTasksOnce()
        val allEvents = db.calendarEventDao().getAllEventsOnce()
        val today = DateTimeUtils.todayEpochDay()
        val now = System.currentTimeMillis()

        val eligible = TaskReminderPolicy.selectEligibleTasks(
            tasks = allTasks,
            events = allEvents,
            todayEpochDay = today,
            minuteOfDay = minuteOfDay,
            nowMillis = now,
            frequencyHours = prefs.reminderFrequencyHours,
            maxRemindersPerTaskPerDay = prefs.maxRemindersPerTaskPerDay
        )

        if (eligible.isEmpty()) {
            if (allTasks.none { !it.isCompleted }) {
                TaskReminderScheduler.clearReminderNotification(applicationContext)
            }
            return Result.success()
        }

        val topTask = eligible.first()
        val posted = TaskReminderScheduler.postPendingTasksNotification(
            context = applicationContext,
            pendingCount = eligible.size,
            topTaskTitle = topTask.title,
            companionName = prefs.petName,
            topTaskId = topTask.id
        )
        if (posted) {
            val nextCount = if (topTask.lastReminderEpochDay == today) topTask.reminderCountToday + 1 else 1
            db.taskDao().updateTask(
                topTask.copy(
                    lastRemindedAtMillis = now,
                    lastReminderEpochDay = today,
                    reminderCountToday = nextCount
                )
            )
        }
        return Result.success()
    }
}

class SuruDailyBriefingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = UserPreferencesRepository(applicationContext).preferencesFlow.first()
        if (!prefs.isSignedIn || !prefs.notificationsEnabled) {
            return Result.success()
        }
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (TaskReminderScheduler.isWithinQuietHours(
                currentHour = currentHour,
                quietHoursEnabled = prefs.quietHoursEnabled,
                startHour = prefs.quietStartHour,
                endHour = prefs.quietEndHour
            )
        ) {
            return Result.success()
        }

        val today = DateTimeUtils.todayEpochDay()
        val guard = applicationContext.getSharedPreferences("feelora_briefing_guard", Context.MODE_PRIVATE)
        val db = LifeOsDatabase.getInstance(applicationContext)
        val tasks = db.taskDao().getAllTasksOnce()
        val events = db.calendarEventDao().getAllEventsOnce()

        if (prefs.suruMorningBriefingEnabled &&
            TaskReminderScheduler.shouldFireBriefingForWindow(
                currentHour = currentHour,
                targetHour = prefs.suruMorningHour,
                lastFiredDay = guard.getLong("last_morning_day", -1L),
                todayEpochDay = today
            )
        ) {
            val briefing = com.example.suru.SuruIntelligenceService.buildMorningBriefing(prefs, tasks, events, today)
            if (TaskReminderScheduler.postSuruBriefingNotification(applicationContext, true, briefing.headline, briefing.focusSummary)) {
                guard.edit().putLong("last_morning_day", today).apply()
            }
        }

        if (prefs.suruEveningReflectionEnabled &&
            TaskReminderScheduler.shouldFireBriefingForWindow(
                currentHour = currentHour,
                targetHour = prefs.suruEveningHour,
                lastFiredDay = guard.getLong("last_evening_day", -1L),
                todayEpochDay = today
            )
        ) {
            val reflection = com.example.suru.SuruIntelligenceService.buildEveningReflection(prefs, tasks, today)
            if (TaskReminderScheduler.postSuruBriefingNotification(applicationContext, false, reflection.headline, reflection.focusSummary)) {
                guard.edit().putLong("last_evening_day", today).apply()
            }
        }
        return Result.success()
    }
}
