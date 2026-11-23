package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.util.DateTimeUtils

@Database(
    entities = [
        TaskEntity::class,
        CalendarEventEntity::class,
        HabitEntity::class,
        WidgetConfigEntity::class,
        QuickNoteEntity::class,
        AppUsageLogEntity::class,
        ChallengeEntity::class,
        FriendEntity::class,
        AiMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LifeOsDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun habitDao(): HabitDao
    abstract fun widgetConfigDao(): WidgetConfigDao
    abstract fun quickNoteDao(): QuickNoteDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun friendDao(): FriendDao
    abstract fun aiMessageDao(): AiMessageDao

    companion object {
        @Volatile
        private var INSTANCE: LifeOsDatabase? = null

        fun getInstance(context: Context): LifeOsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LifeOsDatabase::class.java,
                    "life_os_database.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

object LifeOsSeeder {
    suspend fun seedInitialDataIfNeeded(db: LifeOsDatabase) {
        if (db.widgetConfigDao().getWidgetsOnce().isNotEmpty()) return
        val today = DateTimeUtils.todayEpochDay()

        // Default movable widget layout
        val defaultWidgets = DashboardWidgetType.entries.mapIndexed { index, type ->
            WidgetConfigEntity(
                widgetTypeName = type.name,
                sortOrder = index,
                isVisible = true
            )
        }
        db.widgetConfigDao().upsertAll(defaultWidgets)

        // Starter Tasks
        val starterTasks = listOf(
            TaskEntity(
                title = "Complete Organic Chemistry Problem Set",
                description = "Chapters 4 & 5 reaction mechanisms + practice sheet",
                priorityName = TaskPriority.HIGH.name,
                categoryName = TaskCategory.STUDY.name,
                deadlineEpochDay = today,
                recurrenceName = RecurrencePattern.NONE.name,
                xpReward = 55
            ),
            TaskEntity(
                title = "Ship LIFE OS Dashboard Polish",
                description = "Review sprint deliverables and run integration check",
                priorityName = TaskPriority.CRITICAL.name,
                categoryName = TaskCategory.CODE.name,
                deadlineEpochDay = today,
                recurrenceName = RecurrencePattern.NONE.name,
                xpReward = 80
            ),
            TaskEntity(
                title = "45-Min Strength & Mobility Session",
                description = "Zone 2 warmup + compound lifts",
                priorityName = TaskPriority.MEDIUM.name,
                categoryName = TaskCategory.HEALTH.name,
                deadlineEpochDay = today,
                recurrenceName = RecurrencePattern.DAILY.name,
                isCompleted = true,
                completedAtMillis = System.currentTimeMillis() - 3600_000L,
                xpReward = 35,
                xpAwarded = true
            ),
            TaskEntity(
                title = "Prepare Physics Optics Lab Notes",
                description = "Summarize interference and diffraction derivations",
                priorityName = TaskPriority.HIGH.name,
                categoryName = TaskCategory.STUDY.name,
                deadlineEpochDay = today + 1,
                recurrenceName = RecurrencePattern.NONE.name,
                xpReward = 55
            ),
            TaskEntity(
                title = "Weekly Inbox Zero & Plan Review",
                description = "Archive resolved threads and set top 3 goals",
                priorityName = TaskPriority.LOW.name,
                categoryName = TaskCategory.PERSONAL.name,
                deadlineEpochDay = today + 2,
                recurrenceName = RecurrencePattern.WEEKLY.name,
                xpReward = 20
            )
        )
        starterTasks.forEach { db.taskDao().insertTask(it) }

        // Starter Events & Important Countdowns
        val starterEvents = listOf(
            CalendarEventEntity(
                title = "Deep Focus Block: Systems Architecture",
                notes = "Phone on DND · 90m sprint",
                dateEpochDay = today,
                startMinuteOfDay = 540, // 9:00 AM
                durationMinutes = 90,
                categoryName = TaskCategory.WORK.name,
                reminderMinutesBefore = 15
            ),
            CalendarEventEntity(
                title = "Physics Seminar & Problem Review",
                notes = "Hall B · Bring lab notebook",
                dateEpochDay = today,
                startMinuteOfDay = 840, // 2:00 PM
                durationMinutes = 60,
                categoryName = TaskCategory.STUDY.name,
                reminderMinutesBefore = 30
            ),
            CalendarEventEntity(
                title = "Evening Recovery Walk",
                notes = "Zero screens for 30 minutes",
                dateEpochDay = today,
                startMinuteOfDay = 1110, // 6:30 PM
                durationMinutes = 35,
                categoryName = TaskCategory.HEALTH.name,
                recurrenceName = RecurrencePattern.DAILY.name,
                reminderMinutesBefore = 10
            ),
            CalendarEventEntity(
                title = "Quantum Mechanics Midterm",
                notes = "Major exam milestone",
                dateEpochDay = today + 11,
                startMinuteOfDay = 540,
                durationMinutes = 120,
                categoryName = TaskCategory.STUDY.name,
                isCountdown = true
            ),
            CalendarEventEntity(
                title = "Product Launch Demo Day",
                notes = "Final pitch deck & live build",
                dateEpochDay = today + 24,
                startMinuteOfDay = 600,
                durationMinutes = 90,
                categoryName = TaskCategory.WORK.name,
                isCountdown = true
            )
        )
        starterEvents.forEach { db.calendarEventDao().insertEvent(it) }

        // Starter Habits
        val starterHabits = listOf(
            HabitEntity(
                title = "Morning Deep Work (50m)",
                categoryName = TaskCategory.WORK.name,
                targetPerWeek = 6,
                currentStreak = 6,
                bestStreak = 14,
                lastCompletedEpochDay = today,
                xpAwardedEpochDay = today,
                xpReward = 30
            ),
            HabitEntity(
                title = "Hydrate 2.5L Water",
                categoryName = TaskCategory.HEALTH.name,
                targetPerWeek = 7,
                currentStreak = 4,
                bestStreak = 21,
                lastCompletedEpochDay = today - 1,
                xpReward = 20
            ),
            HabitEntity(
                title = "Read 20 Pages Non-Fiction",
                categoryName = TaskCategory.STUDY.name,
                targetPerWeek = 5,
                currentStreak = 3,
                bestStreak = 9,
                lastCompletedEpochDay = today - 1,
                xpReward = 25
            ),
            HabitEntity(
                title = "Screen-Free Wind Down",
                categoryName = TaskCategory.PERSONAL.name,
                targetPerWeek = 7,
                currentStreak = 5,
                bestStreak = 11,
                lastCompletedEpochDay = today - 1,
                xpReward = 25
            )
        )
        starterHabits.forEach { db.habitDao().insertHabit(it) }

        // Quick Note
        db.quickNoteDao().saveNote(
            QuickNoteEntity(
                id = 1,
                content = "Top priority today: finish high-impact deep work before checking social feeds."
            )
        )

        // Digital Wellness Sample Logs (7-day trend + today's apps)
        val wellnessLogs = listOf(
            AppUsageLogEntity(appName = "Notion & Docs", categoryName = AppUsageCategory.PRODUCTIVE.name, minutesUsed = 54, isDistracting = false, dateEpochDay = today),
            AppUsageLogEntity(appName = "Anki Flashcards", categoryName = AppUsageCategory.PRODUCTIVE.name, minutesUsed = 38, isDistracting = false, dateEpochDay = today),
            AppUsageLogEntity(appName = "Social Reels", categoryName = AppUsageCategory.SOCIAL.name, minutesUsed = 46, isDistracting = true, dateEpochDay = today),
            AppUsageLogEntity(appName = "Video Stream", categoryName = AppUsageCategory.ENTERTAINMENT.name, minutesUsed = 34, isDistracting = true, dateEpochDay = today),
            AppUsageLogEntity(appName = "Messages", categoryName = AppUsageCategory.UTILITY.name, minutesUsed = 22, isDistracting = false, dateEpochDay = today),
            // Past 6 days sample totals for trend chart
            AppUsageLogEntity(appName = "Daily Aggregate", categoryName = AppUsageCategory.UTILITY.name, minutesUsed = 245, isDistracting = true, dateEpochDay = today - 1),
            AppUsageLogEntity(appName = "Daily Aggregate", categoryName = AppUsageCategory.UTILITY.name, minutesUsed = 210, isDistracting = false, dateEpochDay = today - 2),
            AppUsageLogEntity(appName = "Daily Aggregate", categoryName = AppUsageCategory.UTILITY.name, minutesUsed = 280, isDistracting = true, dateEpochDay = today - 3),
            AppUsageLogEntity(appName = "Daily Aggregate", categoryName = AppUsageCategory.UTILITY.name, minutesUsed = 190, isDistracting = false, dateEpochDay = today - 4),
            AppUsageLogEntity(appName = "Daily Aggregate", categoryName = AppUsageCategory.UTILITY.name, minutesUsed = 175, isDistracting = false, dateEpochDay = today - 5),
            AppUsageLogEntity(appName = "Daily Aggregate", categoryName = AppUsageCategory.UTILITY.name, minutesUsed = 230, isDistracting = true, dateEpochDay = today - 6)
        )
        wellnessLogs.forEach { db.appUsageDao().insertLog(it) }

        // Daily & Weekly Challenges
        val challenges = listOf(
            ChallengeEntity(
                title = "Daily Momentum",
                description = "Complete 3 tasks today",
                targetCount = 3,
                currentProgress = 1,
                xpReward = 90,
                isWeekly = false
            ),
            ChallengeEntity(
                title = "Habit Lock-In",
                description = "Check off 2 daily habits",
                targetCount = 2,
                currentProgress = 1,
                xpReward = 70,
                isWeekly = false
            ),
            ChallengeEntity(
                title = "Deep Focus Sprint",
                description = "Log 25+ minutes on the Study Timer",
                targetCount = 25,
                currentProgress = 25,
                xpReward = 110,
                isWeekly = false
            ),
            ChallengeEntity(
                title = "Weekly Velocity",
                description = "Reach 500 XP this week",
                targetCount = 500,
                currentProgress = 340,
                xpReward = 250,
                isWeekly = true
            ),
            ChallengeEntity(
                title = "Digital Discipline",
                description = "Keep distracting apps under 90m/day",
                targetCount = 1,
                currentProgress = 1,
                xpReward = 180,
                isWeekly = true
            )
        )
        challenges.forEach { db.challengeDao().insertChallenge(it) }

        // Local Demo Friends Leaderboard
        val friends = listOf(
            FriendEntity(
                displayName = "Aria Chen",
                handle = "@aria.builds",
                level = 8,
                xp = 1940,
                streakDays = 14,
                focusMinutesToday = 110,
                sharesStats = true,
                statusText = "Shipping ML pipeline"
            ),
            FriendEntity(
                displayName = "Marcus Vance",
                handle = "@mvance",
                level = 6,
                xp = 1320,
                streakDays = 9,
                focusMinutesToday = 75,
                sharesStats = true,
                statusText = "Prepping Organic Chem"
            ),
            FriendEntity(
                displayName = "Elena Rostova",
                handle = "@elena_r",
                level = 5,
                xp = 980,
                streakDays = 5,
                focusMinutesToday = 45,
                sharesStats = true,
                statusText = "Reading systems notes"
            ),
            FriendEntity(
                displayName = "Devon Brooks",
                handle = "@devon.b",
                level = 4,
                xp = 740,
                streakDays = 3,
                focusMinutesToday = 25,
                sharesStats = false,
                statusText = "Private mode enabled"
            )
        )
        friends.forEach { db.friendDao().insertFriend(it) }

        // Initial Welcome Message in LIFE AI
        db.aiMessageDao().insertMessage(
            AiMessageEntity(
                isFromUser = false,
                text = "LIFE AI online. Tell me to schedule an event, add a task, analyze your productivity, or organize tomorrow's plan."
            )
        )
    }
}
