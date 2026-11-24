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
import com.example.data.local.NotificationHistoryEntity
import com.example.data.local.RecurrencePattern
import com.example.data.local.TaskEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserPreferencesState
import com.example.util.DateTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class FeeloraNotificationCategory(
    val channelId: String,
    val channelTitle: String,
    val channelDescription: String,
    val defaultRoute: String
) {
    TASK(
        channelId = "feelora_tasks",
        channelTitle = "Feelora · Tasks",
        channelDescription = "Scheduled task reminders and daily study briefings.",
        defaultRoute = "tasks"
    ),
    PENDING_TASK(
        channelId = "feelora_pending_tasks",
        channelTitle = "Feelora · Pending Tasks",
        channelDescription = "Gentle 2-hour follow-up reminders with quick Complete, Snooze & Reschedule actions.",
        defaultRoute = "tasks"
    ),
    COMPANION(
        channelId = "feelora_companion",
        channelTitle = "Feelora · KUTTA Companion",
        channelDescription = "Warm hydration & care nudges from KUTTA.",
        defaultRoute = "growth"
    ),
    ACHIEVEMENT(
        channelId = "feelora_achievements",
        channelTitle = "Feelora · Achievements",
        channelDescription = "Level ups, streaks, and milestone celebrations.",
        defaultRoute = "growth"
    ),
    TOGETHER(
        channelId = "feelora_together",
        channelTitle = "Feelora · Together",
        channelDescription = "Privacy-safe friend invites, messages, and shared goals.",
        defaultRoute = "together"
    )
}

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

    fun recalculateDeadlineForTomorrowReschedule(currentDeadlineEpochDay: Long, todayEpochDay: Long): Long {
        return maxOf(currentDeadlineEpochDay, todayEpochDay) + 1L
    }
}

object TaskReminderScheduler {
    const val WORK_NAME = "lifeos_pending_task_reminder_2h"
    const val CHANNEL_ID = "feelora_pending_tasks"
    const val NOTIFICATION_ID = 4201
    const val BRIEFING_WORK_NAME = "feelora_suru_daily_briefing"
    const val BRIEFING_NOTIFICATION_ID = 4202

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

    fun isCategoryAllowed(category: FeeloraNotificationCategory, prefs: UserPreferencesState): Boolean {
        if (!prefs.notificationsEnabled) return false
        return when (category) {
            FeeloraNotificationCategory.TASK -> prefs.taskRemindersEnabled
            FeeloraNotificationCategory.PENDING_TASK -> prefs.taskRemindersEnabled && prefs.pendingTaskFollowUpEnabled
            FeeloraNotificationCategory.COMPANION -> prefs.companionAlertsEnabled
            FeeloraNotificationCategory.ACHIEVEMENT -> prefs.achievementAlertsEnabled
            FeeloraNotificationCategory.TOGETHER -> prefs.togetherAlertsEnabled
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
            FeeloraNotificationCategory.entries.forEach { cat ->
                val importance = when (cat) {
                    FeeloraNotificationCategory.TOGETHER -> NotificationManager.IMPORTANCE_HIGH
                    else -> NotificationManager.IMPORTANCE_DEFAULT
                }
                val channel = NotificationChannel(
                    cat.channelId,
                    cat.channelTitle,
                    importance
                ).apply {
                    description = cat.channelDescription
                }
                manager.createNotificationChannel(channel)
            }
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
            "Still waiting: $topTaskTitle"
        } else {
            "$pendingCount tasks ready · Next: $topTaskTitle"
        }
        val body = if (pendingCount == 1) {
            "$companionName is cheering you on. Mark complete, snooze 1h, or move to tomorrow."
        } else {
            "\"$topTaskTitle\" (+${pendingCount - 1} more). Use quick actions below without opening the app."
        }

        val builder = NotificationCompat.Builder(context, FeeloraNotificationCategory.PENDING_TASK.channelId)
            .setSmallIcon(R.drawable.ic_notif_feelora)
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
                    "Snooze 1h",
                    buildActionPendingIntent(context, ACTION_SNOOZE, topTaskId, 12)
                )
                .addAction(
                    R.drawable.ic_widget_calendar,
                    "Tomorrow",
                    buildActionPendingIntent(context, ACTION_RESCHEDULE, topTaskId, 13)
                )
                .setDeleteIntent(buildActionPendingIntent(context, ACTION_DISMISS, topTaskId, 14))
        }

        val posted = try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
        if (posted) {
            recordNotificationHistoryAsync(
                context = context,
                category = FeeloraNotificationCategory.PENDING_TASK,
                title = title,
                body = body,
                targetRoute = "tasks",
                targetId = topTaskId
            )
        }
        return posted
    }

    fun postContextualNotification(
        context: Context,
        category: FeeloraNotificationCategory,
        notificationId: Int,
        title: String,
        body: String,
        targetRoute: String = category.defaultRoute,
        targetId: Long = -1L
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", targetRoute)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, category.channelId)
            .setSmallIcon(R.drawable.ic_widget_check)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()

        val posted = try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
        if (posted) {
            recordNotificationHistoryAsync(
                context = context,
                category = category,
                title = title,
                body = body,
                targetRoute = targetRoute,
                targetId = targetId
            )
        }
        return posted
    }

    fun recordNotificationHistoryAsync(
        context: Context,
        category: FeeloraNotificationCategory,
        title: String,
        body: String,
        targetRoute: String = category.defaultRoute,
        targetId: Long = -1L
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                LifeOsDatabase.getInstance(context).notificationHistoryDao().insert(
                    NotificationHistoryEntity(
                        category = category.name,
                        title = title,
                        body = body,
                        targetRoute = targetRoute,
                        targetId = targetId,
                        isRead = false,
                        createdAtMillis = System.currentTimeMillis()
                    )
                )
            }
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
        return postContextualNotification(
            context = context,
            category = FeeloraNotificationCategory.TASK,
            notificationId = BRIEFING_NOTIFICATION_ID + if (isMorning) 0 else 1,
            title = title,
            body = body,
            targetRoute = "life_ai"
        )
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
        if (!prefs.isSignedIn ||
            !TaskReminderScheduler.isCategoryAllowed(FeeloraNotificationCategory.PENDING_TASK, prefs)
        ) {
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
        if (!prefs.isSignedIn ||
            !TaskReminderScheduler.isCategoryAllowed(FeeloraNotificationCategory.TASK, prefs)
        ) {
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
