package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskSectionEntity::class,
        TaskEntity::class,
        CalendarEventEntity::class,
        HabitEntity::class,
        WidgetConfigEntity::class,
        QuickNoteEntity::class,
        AppUsageLogEntity::class,
        DailyProgressLogEntity::class,
        ChallengeEntity::class,
        FriendEntity::class,
        AiMessageEntity::class,
        CycleLogEntity::class,
        DailyCheckInEntity::class,
        SuruMemoryEntity::class,
        SuruStudyPlanEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class LifeOsDatabase : RoomDatabase() {
    abstract fun taskSectionDao(): TaskSectionDao
    abstract fun taskDao(): TaskDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun habitDao(): HabitDao
    abstract fun widgetConfigDao(): WidgetConfigDao
    abstract fun quickNoteDao(): QuickNoteDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun dailyProgressDao(): DailyProgressDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun friendDao(): FriendDao
    abstract fun aiMessageDao(): AiMessageDao
    abstract fun cycleLogDao(): CycleLogDao
    abstract fun dailyCheckInDao(): DailyCheckInDao
    abstract fun suruMemoryDao(): SuruMemoryDao
    abstract fun suruStudyPlanDao(): SuruStudyPlanDao

    companion object {
        @Volatile
        private var INSTANCE: LifeOsDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE friends ADD COLUMN friendCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE friends ADD COLUMN challengesCompleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE friends ADD COLUMN challengesTotal INTEGER NOT NULL DEFAULT 4")
                db.execSQL("ALTER TABLE friends ADD COLUMN petStageName TEXT NOT NULL DEFAULT 'Little Spark'")
                db.execSQL("ALTER TABLE friends ADD COLUMN shareXp INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE friends ADD COLUMN shareStreaks INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE friends ADD COLUMN shareFocus INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE friends ADD COLUMN shareChallenges INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE friends ADD COLUMN lastSyncedAtMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `task_sections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL DEFAULT 0, `createdAtMillis` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `sectionId` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `distractionMinutes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `focusMinutesSpent` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_progress_logs` (`epochDay` INTEGER NOT NULL, `focusMinutes` INTEGER NOT NULL DEFAULT 0, `xpEarned` INTEGER NOT NULL DEFAULT 0, `tasksCompleted` INTEGER NOT NULL DEFAULT 0, `distractingMinutes` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`epochDay`))"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cycle_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startEpochDay` INTEGER NOT NULL, `endEpochDay` INTEGER, `flowLevel` TEXT NOT NULL DEFAULT 'Medium', `symptomsCsv` TEXT NOT NULL DEFAULT '', `notes` TEXT NOT NULL DEFAULT '', `createdAtMillis` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_check_ins` (`epochDay` INTEGER NOT NULL, `moodScore` INTEGER NOT NULL DEFAULT 4, `energyScore` INTEGER NOT NULL DEFAULT 3, `note` TEXT NOT NULL DEFAULT '', `timestampMillis` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`epochDay`))"
                )
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `suru_memories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT NOT NULL, `keyLabel` TEXT NOT NULL, `valueText` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL DEFAULT 1, `updatedAtMillis` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `suru_study_plans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `subject` TEXT NOT NULL, `chaptersCsv` TEXT NOT NULL, `startEpochDay` INTEGER NOT NULL, `deadlineDays` INTEGER NOT NULL, `dailyMinutesBudget` INTEGER NOT NULL, `preferredWindow` TEXT NOT NULL, `status` TEXT NOT NULL DEFAULT 'ACTIVE', `createdAtMillis` INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        fun getInstance(context: Context): LifeOsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LifeOsDatabase::class.java,
                    "life_os_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

object LifeOsSeeder {
    /**
     * Clean zero-data initialization:
     * Only registers default dashboard widget slots and zero-progress challenge definitions.
     * No pre-filled tasks, task sections, schedules, habits, wellness logs, friends, or fake XP.
     */
    suspend fun seedInitialDataIfNeeded(db: LifeOsDatabase) {
        if (db.widgetConfigDao().getWidgetsOnce().isNotEmpty()) return

        // Home already shows the dedicated prominent KUTTA card, so keep default cards concise & non-duplicate
        val initialVisibleTypes = setOf(
            DashboardWidgetType.TASKS,
            DashboardWidgetType.STUDY_TIMER
        )
        val orderedTypes = listOf(
            DashboardWidgetType.TASKS,
            DashboardWidgetType.STUDY_TIMER,
            DashboardWidgetType.DIGITAL_PET,
            DashboardWidgetType.CALENDAR,
            DashboardWidgetType.HABIT_TRACKER,
            DashboardWidgetType.COUNTDOWN,
            DashboardWidgetType.PRODUCTIVITY_SCORE,
            DashboardWidgetType.XP_TRACKER,
            DashboardWidgetType.QUICK_NOTES,
            DashboardWidgetType.DAILY_STATS
        )
        val defaultWidgets = orderedTypes.mapIndexed { index, type ->
            WidgetConfigEntity(
                widgetTypeName = type.name,
                sortOrder = index,
                isVisible = type in initialVisibleTypes
            )
        }
        db.widgetConfigDao().upsertAll(defaultWidgets)

        val challenges = listOf(
            ChallengeEntity(
                title = "Daily Momentum",
                description = "Complete 3 tasks today",
                targetCount = 3,
                currentProgress = 0,
                xpReward = 90,
                isWeekly = false
            ),
            ChallengeEntity(
                title = "Habit Lock-In",
                description = "Check off 2 daily habits",
                targetCount = 2,
                currentProgress = 0,
                xpReward = 70,
                isWeekly = false
            ),
            ChallengeEntity(
                title = "Deep Focus Sprint",
                description = "Log 25+ minutes on the Focus Timer",
                targetCount = 25,
                currentProgress = 0,
                xpReward = 110,
                isWeekly = false
            ),
            ChallengeEntity(
                title = "Weekly Velocity",
                description = "Earn 250 XP from real completions",
                targetCount = 250,
                currentProgress = 0,
                xpReward = 200,
                isWeekly = true
            )
        )
        challenges.forEach { db.challengeDao().insertChallenge(it) }
    }
}
