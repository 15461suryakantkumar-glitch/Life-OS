package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE friends ADD COLUMN friendCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE friends ADD COLUMN challengesCompleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE friends ADD COLUMN challengesTotal INTEGER NOT NULL DEFAULT 4")
                db.execSQL("ALTER TABLE friends ADD COLUMN petStageName TEXT NOT NULL DEFAULT 'Spark Wisp'")
                db.execSQL("ALTER TABLE friends ADD COLUMN shareXp INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE friends ADD COLUMN shareStreaks INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE friends ADD COLUMN shareFocus INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE friends ADD COLUMN shareChallenges INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE friends ADD COLUMN lastSyncedAtMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): LifeOsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LifeOsDatabase::class.java,
                    "life_os_database.db"
                )
                    .addMigrations(MIGRATION_1_2)
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
     * No pre-filled tasks, schedules, habits, wellness logs, friends, or fake XP.
     */
    suspend fun seedInitialDataIfNeeded(db: LifeOsDatabase) {
        if (db.widgetConfigDao().getWidgetsOnce().isNotEmpty()) return

        val defaultWidgets = DashboardWidgetType.entries.mapIndexed { index, type ->
            WidgetConfigEntity(
                widgetTypeName = type.name,
                sortOrder = index,
                isVisible = true
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
                description = "Reach 250 XP from real completions",
                targetCount = 250,
                currentProgress = 0,
                xpReward = 200,
                isWeekly = true
            )
        )
        challenges.forEach { db.challengeDao().insertChallenge(it) }
    }
}
