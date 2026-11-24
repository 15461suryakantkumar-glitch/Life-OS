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
import com.example.data.local.LifeOsDatabase
import com.example.data.local.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

object TaskReminderScheduler {
    const val WORK_NAME = "lifeos_pending_task_reminder_2h"
    const val CHANNEL_ID = "lifeos_task_reminders"
    const val NOTIFICATION_ID = 4201

    fun syncSchedule(
        context: Context,
        notificationsEnabled: Boolean,
        taskRemindersEnabled: Boolean
    ) {
        val wm = WorkManager.getInstance(context)
        if (!notificationsEnabled || !taskRemindersEnabled) {
            wm.cancelUniqueWork(WORK_NAME)
            clearReminderNotification(context)
            return
        }
        val request = PeriodicWorkRequestBuilder<PendingTaskReminderWorker>(2, TimeUnit.HOURS)
            .setInitialDelay(2, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun clearReminderNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
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
                "Pending Task Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Gentle reminders every 2 hours while you have incomplete tasks."
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun postPendingTasksNotification(
        context: Context,
        pendingCount: Int,
        topTaskTitle: String,
        companionName: String
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
            "1 task still waiting"
        } else {
            "$pendingCount pending tasks waiting"
        }
        val body = if (pendingCount == 1) {
            "$companionName suggests tackling \"$topTaskTitle\" when you're ready."
        } else {
            "Next up: \"$topTaskTitle\" (+${pendingCount - 1} more). Tap to view Tasks."
        }

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
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    const val BRIEFING_WORK_NAME = "feelora_suru_daily_briefing"
    const val BRIEFING_NOTIFICATION_ID = 4202
    private const val BRIEFING_PREFS = "feelora_briefing_guard"

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
            NotificationManagerCompat.from(context).notify(BRIEFING_NOTIFICATION_ID + if (isMorning) 0 else 1, notification)
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

        val db = LifeOsDatabase.getInstance(applicationContext)
        val pendingTasks = db.taskDao().getAllTasksOnce().filter { !it.isCompleted }
        if (pendingTasks.isEmpty()) {
            TaskReminderScheduler.clearReminderNotification(applicationContext)
            return Result.success()
        }

        val topTask = pendingTasks.first()
        TaskReminderScheduler.postPendingTasksNotification(
            context = applicationContext,
            pendingCount = pendingTasks.size,
            topTaskTitle = topTask.title,
            companionName = prefs.petName
        )
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

        val today = com.example.util.DateTimeUtils.todayEpochDay()
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

