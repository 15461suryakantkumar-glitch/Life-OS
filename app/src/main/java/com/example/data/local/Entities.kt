package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskPriority(val label: String, val xpValue: Int) {
    LOW("Low", 20),
    MEDIUM("Medium", 35),
    HIGH("High", 55),
    CRITICAL("Critical", 80)
}

enum class TaskCategory(val label: String) {
    STUDY("Study"),
    WORK("Work"),
    HEALTH("Health"),
    CODE("Build"),
    PERSONAL("Personal")
}

enum class RecurrencePattern(val label: String) {
    NONE("One-time"),
    DAILY("Daily"),
    WEEKDAYS("Weekdays"),
    WEEKLY("Weekly")
}

enum class DashboardWidgetType(val title: String, val subtitle: String) {
    TASKS("Today's Tasks", "Pending missions & quick checkoff"),
    CALENDAR("Upcoming Events", "Timeline & next scheduled blocks"),
    COUNTDOWN("Countdown", "Important milestones & days left"),
    HABIT_TRACKER("Habit Tracker", "Daily consistency & streaks"),
    STUDY_TIMER("Focus Timer", "Deep-work Pomodoro countdown"),
    PRODUCTIVITY_SCORE("Productivity Score", "Live daily execution rating"),
    XP_TRACKER("XP & Level", "Level progress & next milestone"),
    DIGITAL_PET("Digital Companion", "Mood, energy & stage status"),
    QUICK_NOTES("Quick Notes", "Instant scratchpad synced locally"),
    DAILY_STATS("Daily Statistics", "Completed tasks, focus & screen time")
}

enum class AppUsageCategory(val label: String) {
    PRODUCTIVE("Productive"),
    SOCIAL("Social"),
    ENTERTAINMENT("Entertainment"),
    UTILITY("Utility")
}

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val priorityName: String = TaskPriority.MEDIUM.name,
    val categoryName: String = TaskCategory.WORK.name,
    val deadlineEpochDay: Long,
    val recurrenceName: String = RecurrencePattern.NONE.name,
    val isCompleted: Boolean = false,
    val completedAtMillis: Long? = null,
    val xpReward: Int = 35,
    val xpAwarded: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    val priority: TaskPriority
        get() = TaskPriority.entries.firstOrNull { it.name == priorityName } ?: TaskPriority.MEDIUM

    val category: TaskCategory
        get() = TaskCategory.entries.firstOrNull { it.name == categoryName } ?: TaskCategory.WORK

    val recurrence: RecurrencePattern
        get() = RecurrencePattern.entries.firstOrNull { it.name == recurrenceName } ?: RecurrencePattern.NONE
}

@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val dateEpochDay: Long,
    val startMinuteOfDay: Int = 540,
    val durationMinutes: Int = 60,
    val categoryName: String = TaskCategory.STUDY.name,
    val recurrenceName: String = RecurrencePattern.NONE.name,
    val reminderMinutesBefore: Int = 15,
    val isCountdown: Boolean = false
) {
    val category: TaskCategory
        get() = TaskCategory.entries.firstOrNull { it.name == categoryName } ?: TaskCategory.STUDY

    val recurrence: RecurrencePattern
        get() = RecurrencePattern.entries.firstOrNull { it.name == recurrenceName } ?: RecurrencePattern.NONE
}

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val categoryName: String = TaskCategory.HEALTH.name,
    val targetPerWeek: Int = 5,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastCompletedEpochDay: Long = -1L,
    val xpAwardedEpochDay: Long = -1L,
    val xpReward: Int = 25
) {
    fun isCompletedOn(epochDay: Long): Boolean = lastCompletedEpochDay == epochDay
}

@Entity(tableName = "widget_configs")
data class WidgetConfigEntity(
    @PrimaryKey val widgetTypeName: String,
    val sortOrder: Int,
    val isVisible: Boolean = true
) {
    val type: DashboardWidgetType?
        get() = DashboardWidgetType.entries.firstOrNull { it.name == widgetTypeName }
}

@Entity(tableName = "quick_notes")
data class QuickNoteEntity(
    @PrimaryKey val id: Int = 1,
    val content: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_usage_logs")
data class AppUsageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appName: String,
    val categoryName: String = AppUsageCategory.UTILITY.name,
    val minutesUsed: Int,
    val isDistracting: Boolean = false,
    val dateEpochDay: Long
) {
    val category: AppUsageCategory
        get() = AppUsageCategory.entries.firstOrNull { it.name == categoryName } ?: AppUsageCategory.UTILITY
}

@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val targetCount: Int,
    val currentProgress: Int,
    val xpReward: Int,
    val isWeekly: Boolean = false,
    val isClaimed: Boolean = false
)

@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val handle: String,
    val friendCode: String = "",
    val level: Int = 1,
    val xp: Int = 0,
    val streakDays: Int = 0,
    val focusMinutesToday: Int = 0,
    val challengesCompleted: Int = 0,
    val challengesTotal: Int = 4,
    val petStageName: String = "Spark Wisp",
    val sharesStats: Boolean = true,
    val shareXp: Boolean = true,
    val shareStreaks: Boolean = true,
    val shareFocus: Boolean = true,
    val shareChallenges: Boolean = true,
    val statusText: String = "Connected",
    val lastSyncedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_messages")
data class AiMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isFromUser: Boolean,
    val text: String,
    val actionBadge: String? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)
