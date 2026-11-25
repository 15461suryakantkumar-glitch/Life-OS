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
    TASKS("Today's Tasks", "Pending tasks and quick check-off"),
    CALENDAR("Upcoming Schedule", "Timeline and next scheduled blocks"),
    COUNTDOWN("Countdowns", "Upcoming milestones and days left"),
    HABIT_TRACKER("Habit Tracker", "Daily consistency and streaks"),
    STUDY_TIMER("Focus Timer", "Deep-work timer countdown"),
    PRODUCTIVITY_SCORE("Productivity Score", "Live daily execution score"),
    XP_TRACKER("XP & Level", "Level progress and next milestone"),
    DIGITAL_PET("KUTTA Sanctuary", "Mood, energy & study check-in"),
    QUICK_NOTES("Quick Notes", "Instant scratchpad saved locally"),
    DAILY_STATS("Daily Summary", "Tasks completed, focus, and screen time")
}

enum class AppUsageCategory(val label: String) {
    PRODUCTIVE("Productive"),
    SOCIAL("Social"),
    ENTERTAINMENT("Entertainment"),
    UTILITY("Utility")
}

@Entity(tableName = "task_sections")
data class TaskSectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis()
)

object TaskSectionMapper {
    fun inferCategory(sectionName: String): TaskCategory {
        val lower = sectionName.lowercase()
        return when {
            listOf("phys", "chem", "bio", "math", "study", "exam", "test", "mock", "quiz", "read", "note", "revision", "ncert", "lecture")
                .any { lower.contains(it) } -> TaskCategory.STUDY
            listOf("code", "dev", "build", "app", "api", "bug", "proj", "tech", "algo")
                .any { lower.contains(it) } -> TaskCategory.CODE
            listOf("health", "gym", "run", "fit", "medit", "walk", "sleep", "diet", "water", "workout")
                .any { lower.contains(it) } -> TaskCategory.HEALTH
            listOf("home", "life", "errand", "chore", "personal", "shop", "family")
                .any { lower.contains(it) } -> TaskCategory.PERSONAL
            else -> TaskCategory.WORK
        }
    }
}

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sectionId: Long = 0L,
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
    val createdAtMillis: Long = System.currentTimeMillis(),
    val distractionMinutes: Int = 0,
    val focusMinutesSpent: Int = 0,
    val lastRemindedAtMillis: Long = 0L,
    val snoozedUntilMillis: Long = 0L,
    val reminderCountToday: Int = 0,
    val lastReminderEpochDay: Long = -1L,
    val remindersDismissed: Boolean = false
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
    val packageName: String = "",
    val categoryName: String = AppUsageCategory.UTILITY.name,
    val minutesUsed: Int,
    val isDistracting: Boolean = false,
    val dateEpochDay: Long
) {
    val category: AppUsageCategory
        get() = AppUsageCategory.entries.firstOrNull { it.name == categoryName } ?: AppUsageCategory.UTILITY
}

@Entity(tableName = "daily_progress_logs")
data class DailyProgressLogEntity(
    @PrimaryKey val epochDay: Long,
    val focusMinutes: Int = 0,
    val xpEarned: Int = 0,
    val tasksCompleted: Int = 0,
    val distractingMinutes: Int = 0
)

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
    val remoteUserId: String = "",
    val friendshipId: String = "",
    val isTogetherOne: Boolean = false,
    val displayName: String,
    val handle: String,
    val friendCode: String = "",
    val level: Int = 1,
    val xp: Int = 0,
    val streakDays: Int = 0,
    val focusMinutesToday: Int = 0,
    val challengesCompleted: Int = 0,
    val challengesTotal: Int = 4,
    val petStageName: String = "Playful Pup",
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
    val attachmentSummary: String? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "cycle_logs")
data class CycleLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    val flowLevel: String = "Medium",
    val symptomsCsv: String = "",
    val notes: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_check_ins")
data class DailyCheckInEntity(
    @PrimaryKey val epochDay: Long,
    val moodScore: Int = 4, // 1 = Very Low .. 5 = Great
    val energyScore: Int = 3, // 1..5
    val note: String = "",
    val timestampMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "suru_memories")
data class SuruMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // STUDY_PREF | TIME_PREF | GOAL | WEAK_SUBJECT | WELLNESS_PREF
    val keyLabel: String,
    val valueText: String,
    val isEnabled: Boolean = true,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "suru_study_plans")
data class SuruStudyPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val chaptersCsv: String,
    val startEpochDay: Long,
    val deadlineDays: Int,
    val dailyMinutesBudget: Int,
    val preferredWindow: String,
    val status: String = "ACTIVE", // ACTIVE | COMPLETED | ARCHIVED
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "notification_history")
data class NotificationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // TASK | PENDING_TASK | COMPANION | ACHIEVEMENT | TOGETHER
    val title: String,
    val body: String,
    val targetRoute: String = "home",
    val targetId: Long = -1L,
    val isRead: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_provider_usage")
data class AiProviderUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerName: String,
    val modelId: String,
    val promptTokensEst: Int = 0,
    val responseTokensEst: Int = 0,
    val success: Boolean = true,
    val latencyMs: Long = 0L,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "together_outbox")
data class TogetherOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val recipientUserId: String,
    val clientMessageId: String,
    val content: String,
    val mediaLocalUri: String? = null,
    val status: String = "PENDING", // PENDING | FAILED
    val retryCount: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis()
)

enum class StudyChapterStatus(val label: String) {
    PENDING("Pending"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed")
}

enum class StudySessionMode(val label: String) {
    POMODORO("Pomodoro"),
    CUSTOM("Custom Focus")
}

enum class StudyResourceType(val label: String) {
    NOTE("Note"),
    SUMMARY("Summary"),
    LINK("Reference Link")
}

@Entity(tableName = "study_subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String = "#9274D8",
    val iconName: String = "MenuBook",
    val targetMinutesPerDay: Int = 60,
    val weeklyGoalMinutes: Int = 300,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_chapters")
data class StudyChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val title: String,
    val notes: String = "",
    val status: String = StudyChapterStatus.PENDING.name,
    val estimatedMinutes: Int = 45,
    val sortOrder: Int = 0,
    val completedAtMillis: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    val chapterStatus: StudyChapterStatus
        get() = StudyChapterStatus.entries.firstOrNull { it.name == status } ?: StudyChapterStatus.PENDING

    val isCompleted: Boolean
        get() = chapterStatus == StudyChapterStatus.COMPLETED
}

@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long? = null,
    val chapterId: Long? = null,
    val plannedMinutes: Int = 25,
    val actualMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val sessionMode: String = StudySessionMode.POMODORO.name,
    val completed: Boolean = true,
    val startedAtMillis: Long = System.currentTimeMillis(),
    val endedAtMillis: Long = System.currentTimeMillis(),
    val dateEpochDay: Long
) {
    val mode: StudySessionMode
        get() = StudySessionMode.entries.firstOrNull { it.name == sessionMode } ?: StudySessionMode.POMODORO
}

@Entity(tableName = "study_resources")
data class StudyResourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val chapterId: Long? = null,
    val title: String,
    val content: String,
    val resourceType: String = StudyResourceType.NOTE.name,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val type: StudyResourceType
        get() = StudyResourceType.entries.firstOrNull { it.name == resourceType } ?: StudyResourceType.NOTE
}
