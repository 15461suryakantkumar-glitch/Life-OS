package com.example.suru

import com.example.data.local.CalendarEventEntity
import com.example.data.local.HabitEntity
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority

enum class SuruMascotState(val label: String) {
    IDLE("Ready"),
    LISTENING("Listening"),
    THINKING("Thinking"),
    SPEAKING("Speaking"),
    WORKING("Planning"),
    SUCCESS("Done"),
    ERROR("Needs Check"),
    SLEEPING("Resting")
}

enum class SuruVoiceState(val label: String) {
    READY("Ready"),
    LISTENING("Listening..."),
    PROCESSING("Processing..."),
    SPEAKING("Speaking"),
    COMPLETED("Completed"),
    FAILED("Voice Unavailable")
}

enum class SuruVoiceLanguage(
    val label: String,
    val sttLocale: String,
    val ttsLanguageTag: String
) {
    ENGLISH("English", "en-IN", "en-IN"),
    HINDI("हिन्दी", "hi-IN", "hi-IN"),
    HINGLISH("Hinglish", "en-IN", "en-IN");

    companion object {
        fun fromStored(raw: String?): SuruVoiceLanguage {
            return entries.firstOrNull { it.name == raw } ?: HINGLISH
        }
    }
}

enum class SuruIntent {
    CHAT,
    ASK_CLARIFICATION,
    CREATE_TASK,
    UPDATE_TASK,
    RESCHEDULE_TASK,
    COMPLETE_TASK,
    DELETE_TASK,
    BREAKDOWN_TASK,
    CREATE_EVENT,
    RESCHEDULE_EVENT,
    CREATE_HABIT,
    CREATE_STUDY_PLAN,
    BACKLOG_RECOVERY,
    LOG_STUDY_PROGRESS,
    STUDY_HELP,
    WELLNESS_CHECK,
    MORNING_BRIEFING,
    EVENING_REFLECTION,
    ORGANIZE_TOMORROW,
    PLAN_WEEK,
    FIND_FREE_SLOTS,
    PROGRESS_SUMMARY,
    OVERDUE_CHECK,
    INSIGHTS,
    SAVE_MEMORY
}

enum class SuruTimeWindow(val label: String, val defaultHour: Int, val defaultMinute: Int) {
    MORNING("Morning", 8, 30),
    AFTERNOON("Afternoon", 14, 0),
    EVENING("Evening", 18, 30),
    NIGHT("Night", 20, 30),
    CUSTOM("Custom", 9, 0)
}

enum class SuruStudyHelpMode(val label: String, val subtitle: String) {
    BEGINNER("Beginner", "Simple intuition & everyday analogies"),
    DETAILED("Detailed", "Step-by-step mechanisms & depth"),
    QUICK_REVISION("Quick Revision", "Rapid key points & recall hooks"),
    QUESTION_PRACTICE("Question Practice", "Active recall prompts & self-check"),
    EXAM_ORIENTED("Exam-Oriented", "High-yield definitions, traps & marking tips")
}

data class SuruPlanSessionPreview(
    val dayOffset: Int,
    val title: String,
    val chapterName: String,
    val phase: String, // Concept | Practice | Revision | Break
    val weightLabel: String,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val hasConflict: Boolean = false,
    val conflictReason: String? = null
)

data class SuruStudyPlanDraft(
    val subject: String,
    val chapters: List<String>,
    val chapterWeights: Map<String, Float>,
    val deadlineDays: Int,
    val dailyAvailableMinutes: Int,
    val preferredWindow: SuruTimeWindow,
    val startHour: Int,
    val startMinute: Int,
    val sessions: List<SuruPlanSessionPreview>,
    val summaryNote: String
)

data class SuruBacklogItem(
    val taskId: Long,
    val title: String,
    val priority: TaskPriority,
    val plannedDayOffset: Int,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val hasConflict: Boolean = false
)

data class SuruBacklogPlan(
    val items: List<SuruBacklogItem>,
    val summaryNote: String
)

data class SuruPendingContext(
    val intent: SuruIntent,
    val subject: String = "",
    val title: String = "",
    val chapters: List<String> = emptyList(),
    val deadlineDays: Int? = null,
    val dailyAvailableMinutes: Int? = null,
    val dateOffsetDays: Int? = null,
    val preferredWindow: SuruTimeWindow? = null,
    val startHour: Int? = null,
    val startMinute: Int? = null,
    val durationMinutes: Int? = null,
    val category: TaskCategory = TaskCategory.STUDY,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val targetTaskId: Long? = null,
    val targetEventId: Long? = null,
    val missingSlot: String? = null
)

data class SuruStructuredAction(
    val intent: SuruIntent,
    val reply: String,
    val title: String? = null,
    val newTitle: String? = null,
    val subject: String? = null,
    val dateOffsetDays: Int = 0,
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val durationMinutes: Int = 60,
    val loggedMinutes: Int = 0,
    val deadlineDays: Int = 5,
    val dailyMinutesBudget: Int = 90,
    val rescheduleAllToday: Boolean = false,
    val category: TaskCategory = TaskCategory.STUDY,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val chapters: List<String> = emptyList(),
    val subtasks: List<String> = emptyList(),
    val studyHelpTopic: String? = null,
    val studyHelpMode: SuruStudyHelpMode = SuruStudyHelpMode.BEGINNER,
    val memoryKey: String? = null,
    val memoryValue: String? = null,
    val memoryCategory: String = "STUDY_PREF",
    val targetTaskId: Long? = null,
    val targetEventId: Long? = null,
    val studyPlanDraft: SuruStudyPlanDraft? = null,
    val backlogPlan: SuruBacklogPlan? = null,
    val updatedPendingContext: SuruPendingContext? = null,
    val requiresConfirmation: Boolean = false,
    val confirmationTitle: String? = null,
    val confirmationMessage: String? = null,
    val isOfflineEngine: Boolean = true
)

sealed class SuruConfirmationRequest {
    data class StudyPlanConfirmation(
        val draft: SuruStudyPlanDraft,
        val sourcePrompt: String
    ) : SuruConfirmationRequest()

    data class BacklogConfirmation(
        val plan: SuruBacklogPlan,
        val sourcePrompt: String
    ) : SuruConfirmationRequest()

    data class ActionConfirmation(
        val title: String,
        val message: String,
        val warningDetail: String? = null,
        val action: SuruStructuredAction
    ) : SuruConfirmationRequest()
}

data class SuruUndoRecord(
    val label: String,
    val insertedTaskIds: List<Long> = emptyList(),
    val insertedSectionIds: List<Long> = emptyList(),
    val insertedEventIds: List<Long> = emptyList(),
    val insertedHabitIds: List<Long> = emptyList(),
    val insertedPlanIds: List<Long> = emptyList(),
    val previousTasks: List<TaskEntity> = emptyList(),
    val previousEvents: List<CalendarEventEntity> = emptyList(),
    val previousHabits: List<HabitEntity> = emptyList()
)

data class SuruStudyHelpResult(
    val topic: String,
    val mode: SuruStudyHelpMode,
    val isBasedOnUserNotes: Boolean,
    val sourceDisclaimer: String,
    val conceptExplanation: String,
    val summaryBullets: List<String>,
    val practiceQuestions: List<String>,
    val flashcards: List<Pair<String, String>>,
    val revisionChecklist: List<String>,
    val mistakeTips: List<String>
)

data class SuruDailyBriefing(
    val isMorning: Boolean,
    val headline: String,
    val greeting: String,
    val focusSummary: String,
    val priorityTasks: List<String>,
    val scheduleBlocks: List<String>,
    val wellnessNote: String,
    val gentleClosing: String
)

data class LifeContextSnapshot(
    val userName: String,
    val level: Int,
    val totalXp: Int,
    val dailyStreak: Int,
    val pendingTasksSummary: String,
    val pendingTaskTitles: List<String> = emptyList(),
    val completedTasksCount: Int,
    val upcomingEventsSummary: String,
    val screenTimeTodayMinutes: Int,
    val focusMinutesToday: Int,
    val dailyFocusGoalMinutes: Int = 120,
    val habitsCompletedToday: Int = 0,
    val habitsTotal: Int = 0,
    val productivityScore: Int = 0
)

