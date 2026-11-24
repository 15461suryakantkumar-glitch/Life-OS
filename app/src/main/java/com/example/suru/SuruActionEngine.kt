package com.example.suru

import com.example.data.local.CalendarEventEntity
import com.example.data.local.HabitEntity
import com.example.data.local.LifeOsDatabase
import com.example.data.local.SuruMemoryEntity
import com.example.data.local.SuruStudyPlanEntity
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import com.example.data.local.TaskSectionEntity
import com.example.util.DateTimeUtils

data class SuruExecutionOutcome(
    val actionBadge: String?,
    val undoRecord: SuruUndoRecord?,
    val feedbackMessage: String
)

class SuruActionEngine(private val db: LifeOsDatabase) {

    suspend fun executeStructuredAction(
        action: SuruStructuredAction,
        todayEpochDay: Long = DateTimeUtils.todayEpochDay()
    ): SuruExecutionOutcome {
        return when (action.intent) {
            SuruIntent.CREATE_TASK -> executeCreateTask(action, todayEpochDay)
            SuruIntent.COMPLETE_TASK -> executeCompleteTask(action)
            SuruIntent.DELETE_TASK -> executeDeleteTask(action)
            SuruIntent.BREAKDOWN_TASK -> executeBreakdownTask(action, todayEpochDay)
            SuruIntent.CREATE_EVENT -> executeCreateEvent(action, todayEpochDay)
            SuruIntent.RESCHEDULE_EVENT -> executeRescheduleEvent(action, todayEpochDay)
            SuruIntent.CREATE_HABIT -> executeCreateHabit(action)
            SuruIntent.ORGANIZE_TOMORROW -> executeOrganizeTomorrow(todayEpochDay)
            SuruIntent.SAVE_MEMORY -> executeSaveMemory(action)
            SuruIntent.CREATE_STUDY_PLAN -> {
                val draft = action.studyPlanDraft
                if (draft != null) executeStudyPlan(draft, todayEpochDay)
                else SuruExecutionOutcome(null, null, "No study plan draft to apply.")
            }
            SuruIntent.BACKLOG_RECOVERY -> {
                val plan = action.backlogPlan
                if (plan != null) executeBacklogPlan(plan, todayEpochDay)
                else SuruExecutionOutcome(null, null, "No backlog plan to apply.")
            }
            else -> SuruExecutionOutcome(null, null, "")
        }
    }

    suspend fun executeStudyPlan(
        draft: SuruStudyPlanDraft,
        todayEpochDay: Long = DateTimeUtils.todayEpochDay()
    ): SuruExecutionOutcome {
        val insertedSectionIds = mutableListOf<Long>()
        val insertedTaskIds = mutableListOf<Long>()
        val insertedEventIds = mutableListOf<Long>()
        val insertedPlanIds = mutableListOf<Long>()

        val existingSections = db.taskSectionDao().getAllSectionsOnce()
        val matchedSection = existingSections.firstOrNull {
            it.name.equals(draft.subject, ignoreCase = true)
        }
        val sectionId = if (matchedSection != null) {
            matchedSection.id
        } else {
            val nextOrder = (existingSections.maxOfOrNull { it.sortOrder } ?: -1) + 1
            val id = db.taskSectionDao().insertSection(
                TaskSectionEntity(name = draft.subject, sortOrder = nextOrder)
            )
            insertedSectionIds.add(id)
            id
        }

        // Create chapter tasks in the unified Task manager
        draft.chapters.forEachIndexed { idx, chapter ->
            val weight = draft.chapterWeights[chapter] ?: 1f
            val priority = if (weight >= 1.25f) TaskPriority.HIGH else TaskPriority.MEDIUM
            val targetDayOffset = (((idx + 1).toFloat() / draft.chapters.size.coerceAtLeast(1)) * (draft.deadlineDays - 1)).toInt()
                .coerceIn(0, (draft.deadlineDays - 1).coerceAtLeast(0))
            val taskId = db.taskDao().insertTask(
                TaskEntity(
                    sectionId = sectionId,
                    title = "${draft.subject}: $chapter",
                    description = "SURU Study Plan · Weight ${"%.1f".format(weight)}x (Concept + Practice + Revision)",
                    priorityName = priority.name,
                    categoryName = TaskCategory.STUDY.name,
                    deadlineEpochDay = todayEpochDay + targetDayOffset,
                    xpReward = priority.xpValue
                )
            )
            insertedTaskIds.add(taskId)
        }

        // Schedule study blocks into the unified Calendar with reminders
        draft.sessions.forEach { session ->
            val eventId = db.calendarEventDao().insertEvent(
                CalendarEventEntity(
                    title = session.title,
                    notes = "SURU ${session.phase} · ${session.weightLabel}",
                    dateEpochDay = todayEpochDay + session.dayOffset,
                    startMinuteOfDay = session.startMinuteOfDay,
                    durationMinutes = session.durationMinutes,
                    categoryName = TaskCategory.STUDY.name,
                    reminderMinutesBefore = 15
                )
            )
            insertedEventIds.add(eventId)
        }

        val planId = db.suruStudyPlanDao().insertPlan(
            SuruStudyPlanEntity(
                subject = draft.subject,
                chaptersCsv = draft.chapters.joinToString(", "),
                startEpochDay = todayEpochDay,
                deadlineDays = draft.deadlineDays,
                dailyMinutesBudget = draft.dailyAvailableMinutes,
                preferredWindow = draft.preferredWindow.label
            )
        )
        insertedPlanIds.add(planId)

        val badge = "Study Plan · ${draft.chapters.size} chapters · ${draft.sessions.size} blocks"
        val undo = SuruUndoRecord(
            label = "Undo ${draft.subject} study plan",
            insertedTaskIds = insertedTaskIds,
            insertedSectionIds = insertedSectionIds,
            insertedEventIds = insertedEventIds,
            insertedPlanIds = insertedPlanIds
        )
        return SuruExecutionOutcome(
            actionBadge = badge,
            undoRecord = undo,
            feedbackMessage = "Added ${draft.chapters.size} tasks & ${draft.sessions.size} calendar sessions for ${draft.subject}"
        )
    }

    suspend fun executeBacklogPlan(
        plan: SuruBacklogPlan,
        todayEpochDay: Long = DateTimeUtils.todayEpochDay()
    ): SuruExecutionOutcome {
        val prevTasks = mutableListOf<TaskEntity>()
        val insertedEventIds = mutableListOf<Long>()
        val allTasks = db.taskDao().getAllTasksOnce()

        plan.items.forEach { item ->
            val task = allTasks.firstOrNull { it.id == item.taskId }
            if (task != null && !task.isCompleted) {
                prevTasks.add(task)
                db.taskDao().updateTask(
                    task.copy(deadlineEpochDay = todayEpochDay + item.plannedDayOffset)
                )
                val evId = db.calendarEventDao().insertEvent(
                    CalendarEventEntity(
                        title = "Catch-up: ${task.title}",
                        notes = "SURU Backlog Recovery (${item.priority.label})",
                        dateEpochDay = todayEpochDay + item.plannedDayOffset,
                        startMinuteOfDay = item.startMinuteOfDay,
                        durationMinutes = item.durationMinutes,
                        categoryName = task.categoryName,
                        reminderMinutesBefore = 15
                    )
                )
                insertedEventIds.add(evId)
            }
        }

        return SuruExecutionOutcome(
            actionBadge = "Backlog Plan · ${plan.items.size} tasks scheduled",
            undoRecord = SuruUndoRecord(
                label = "Undo backlog schedule",
                insertedEventIds = insertedEventIds,
                previousTasks = prevTasks
            ),
            feedbackMessage = "Rescheduled ${plan.items.size} pending task(s) across upcoming days"
        )
    }

    private suspend fun executeCreateTask(action: SuruStructuredAction, todayEpochDay: Long): SuruExecutionOutcome {
        val title = action.title?.trim()?.takeIf { it.isNotBlank() }
            ?: return SuruExecutionOutcome(null, null, "Task title missing.")
        val sections = db.taskSectionDao().getAllSectionsOnce()
        val matchedSection = sections.firstOrNull { sec ->
            title.contains(sec.name, ignoreCase = true) ||
                (action.subject != null && sec.name.equals(action.subject, ignoreCase = true))
        }
        val taskId = db.taskDao().insertTask(
            TaskEntity(
                sectionId = matchedSection?.id ?: 0L,
                title = title,
                priorityName = action.priority.name,
                categoryName = action.category.name,
                deadlineEpochDay = todayEpochDay + action.dateOffsetDays,
                xpReward = action.priority.xpValue
            )
        )
        return SuruExecutionOutcome(
            actionBadge = "Task Added · $title",
            undoRecord = SuruUndoRecord(label = "Undo add \"$title\"", insertedTaskIds = listOf(taskId)),
            feedbackMessage = "Added task \"$title\""
        )
    }

    private suspend fun executeCompleteTask(action: SuruStructuredAction): SuruExecutionOutcome {
        val allTasks = db.taskDao().getAllTasksOnce()
        val target = action.targetTaskId?.let { id -> allTasks.firstOrNull { it.id == id } }
            ?: allTasks.firstOrNull { !it.isCompleted && it.title.equals(action.title, ignoreCase = true) }
            ?: return SuruExecutionOutcome(null, null, "Matching pending task not found.")

        val updated = target.copy(
            isCompleted = true,
            completedAtMillis = System.currentTimeMillis(),
            xpAwarded = true
        )
        db.taskDao().updateTask(updated)
        return SuruExecutionOutcome(
            actionBadge = "Completed · ${target.title}",
            undoRecord = SuruUndoRecord(label = "Undo complete \"${target.title}\"", previousTasks = listOf(target)),
            feedbackMessage = "Completed \"${target.title}\""
        )
    }

    private suspend fun executeDeleteTask(action: SuruStructuredAction): SuruExecutionOutcome {
        val allTasks = db.taskDao().getAllTasksOnce()
        val target = action.targetTaskId?.let { id -> allTasks.firstOrNull { it.id == id } }
            ?: return SuruExecutionOutcome(null, null, "Task not found.")
        db.taskDao().deleteTask(target)
        return SuruExecutionOutcome(
            actionBadge = "Deleted · ${target.title}",
            undoRecord = SuruUndoRecord(label = "Undo delete \"${target.title}\"", previousTasks = listOf(target)),
            feedbackMessage = "Removed task \"${target.title}\""
        )
    }

    private suspend fun executeBreakdownTask(action: SuruStructuredAction, todayEpochDay: Long): SuruExecutionOutcome {
        val allTasks = db.taskDao().getAllTasksOnce()
        val parent = action.targetTaskId?.let { id -> allTasks.firstOrNull { it.id == id } }
        val sectionId = parent?.sectionId ?: 0L
        val deadline = parent?.deadlineEpochDay ?: todayEpochDay
        val insertedIds = mutableListOf<Long>()
        val subtasks = action.subtasks.ifEmpty { TaskIntelligence.buildSubtasks(action.title ?: "Study Topic") }
        subtasks.forEach { sub ->
            val id = db.taskDao().insertTask(
                TaskEntity(
                    sectionId = sectionId,
                    title = sub,
                    description = "Subtask created by SURU",
                    priorityName = action.priority.name,
                    categoryName = action.category.name,
                    deadlineEpochDay = deadline,
                    xpReward = TaskPriority.LOW.xpValue
                )
            )
            insertedIds.add(id)
        }
        return SuruExecutionOutcome(
            actionBadge = "Subtasks · ${subtasks.size} added",
            undoRecord = SuruUndoRecord(label = "Undo subtasks", insertedTaskIds = insertedIds),
            feedbackMessage = "Created ${subtasks.size} subtasks"
        )
    }

    private suspend fun executeCreateEvent(action: SuruStructuredAction, todayEpochDay: Long): SuruExecutionOutcome {
        val title = action.title?.trim()?.takeIf { it.isNotBlank() }
            ?: return SuruExecutionOutcome(null, null, "Event title missing.")
        val startMin = (action.startHour.coerceIn(0, 23) * 60 + action.startMinute.coerceIn(0, 59))
        val duration = action.durationMinutes.coerceIn(15, 360)
        val eventId = db.calendarEventDao().insertEvent(
            CalendarEventEntity(
                title = title,
                notes = "Scheduled by SURU",
                dateEpochDay = todayEpochDay + action.dateOffsetDays,
                startMinuteOfDay = startMin,
                durationMinutes = duration,
                categoryName = action.category.name,
                reminderMinutesBefore = 15
            )
        )
        val label = "${DateTimeUtils.formatFullDate(todayEpochDay + action.dateOffsetDays)} ${DateTimeUtils.formatTimeOfDay(startMin)}"
        return SuruExecutionOutcome(
            actionBadge = "Scheduled · $label",
            undoRecord = SuruUndoRecord(label = "Undo \"$title\" event", insertedEventIds = listOf(eventId)),
            feedbackMessage = "Scheduled \"$title\" at $label"
        )
    }

    private suspend fun executeRescheduleEvent(action: SuruStructuredAction, todayEpochDay: Long): SuruExecutionOutcome {
        val allEvents = db.calendarEventDao().getAllEventsOnce()
        val target = action.targetEventId?.let { id -> allEvents.firstOrNull { it.id == id } }
            ?: return SuruExecutionOutcome(null, null, "Event not found.")
        val startMin = (action.startHour.coerceIn(0, 23) * 60 + action.startMinute.coerceIn(0, 59))
        db.calendarEventDao().updateEvent(
            target.copy(
                dateEpochDay = todayEpochDay + action.dateOffsetDays,
                startMinuteOfDay = startMin
            )
        )
        return SuruExecutionOutcome(
            actionBadge = "Rescheduled · ${target.title}",
            undoRecord = SuruUndoRecord(label = "Undo move \"${target.title}\"", previousEvents = listOf(target)),
            feedbackMessage = "Moved \"${target.title}\""
        )
    }

    private suspend fun executeCreateHabit(action: SuruStructuredAction): SuruExecutionOutcome {
        val title = action.title?.trim()?.takeIf { it.isNotBlank() }
            ?: return SuruExecutionOutcome(null, null, "Habit title missing.")
        val id = db.habitDao().insertHabit(
            HabitEntity(
                title = title,
                categoryName = action.category.name,
                targetPerWeek = 7,
                xpReward = 25
            )
        )
        return SuruExecutionOutcome(
            actionBadge = "Habit Added · $title",
            undoRecord = SuruUndoRecord(label = "Undo habit \"$title\"", insertedHabitIds = listOf(id)),
            feedbackMessage = "Added habit \"$title\""
        )
    }

    private suspend fun executeOrganizeTomorrow(todayEpochDay: Long): SuruExecutionOutcome {
        val existingEvents = db.calendarEventDao().getAllEventsOnce()
        val pending = db.taskDao().getAllTasksOnce().filter { !it.isCompleted }.take(3)
        if (pending.isEmpty()) {
            return SuruExecutionOutcome(null, null, "No pending tasks to organize for tomorrow.")
        }
        val insertedIds = mutableListOf<Long>()
        val booked = mutableListOf<Pair<Int, Int>>()
        pending.forEachIndexed { idx, task ->
            val (slot, _) = CalendarIntelligence.findConflictFreeStartMinute(
                dateEpochDay = 1,
                todayEpochDay = todayEpochDay,
                preferredStartMinute = 540 + idx * 120,
                durationMinutes = 60,
                existingEvents = existingEvents,
                additionalBooked = booked
            )
            booked.add(slot to 60)
            val id = db.calendarEventDao().insertEvent(
                CalendarEventEntity(
                    title = task.title,
                    notes = "Organized by SURU",
                    dateEpochDay = todayEpochDay + 1,
                    startMinuteOfDay = slot,
                    durationMinutes = 60,
                    categoryName = task.categoryName,
                    reminderMinutesBefore = 15
                )
            )
            insertedIds.add(id)
        }
        return SuruExecutionOutcome(
            actionBadge = "Tomorrow Planned · ${insertedIds.size} Blocks",
            undoRecord = SuruUndoRecord(label = "Undo tomorrow's plan", insertedEventIds = insertedIds),
            feedbackMessage = "Planned ${insertedIds.size} conflict-aware blocks for tomorrow"
        )
    }

    private suspend fun executeSaveMemory(action: SuruStructuredAction): SuruExecutionOutcome {
        val key = action.memoryKey?.trim().orEmpty().ifBlank { "Preference" }
        val value = action.memoryValue?.trim().orEmpty()
        if (value.isBlank()) return SuruExecutionOutcome(null, null, "")
        val existing = db.suruMemoryDao().findByKey(key)
        db.suruMemoryDao().upsertMemory(
            SuruMemoryEntity(
                id = existing?.id ?: 0L,
                category = action.memoryCategory,
                keyLabel = key,
                valueText = value,
                isEnabled = true,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
        return SuruExecutionOutcome(
            actionBadge = "Saved to Memory · $key",
            undoRecord = null,
            feedbackMessage = "Saved preference to SURU Memory"
        )
    }

    suspend fun undo(record: SuruUndoRecord) {
        val allTasks = db.taskDao().getAllTasksOnce()
        val allEvents = db.calendarEventDao().getAllEventsOnce()
        val allHabits = db.habitDao().getAllHabitsOnce()
        val allSections = db.taskSectionDao().getAllSectionsOnce()
        val allPlans = db.suruStudyPlanDao().getAllPlansOnce()

        record.insertedTaskIds.forEach { id ->
            allTasks.firstOrNull { it.id == id }?.let { db.taskDao().deleteTask(it) }
        }
        record.insertedEventIds.forEach { id ->
            allEvents.firstOrNull { it.id == id }?.let { db.calendarEventDao().deleteEvent(it) }
        }
        record.insertedHabitIds.forEach { id ->
            allHabits.firstOrNull { it.id == id }?.let { db.habitDao().deleteHabit(it) }
        }
        record.insertedSectionIds.forEach { id ->
            allSections.firstOrNull { it.id == id }?.let { db.taskSectionDao().deleteSection(it) }
        }
        record.insertedPlanIds.forEach { id ->
            allPlans.firstOrNull { it.id == id }?.let { db.suruStudyPlanDao().deletePlan(it) }
        }
        record.previousTasks.forEach { prev ->
            db.taskDao().insertTask(prev)
        }
        record.previousEvents.forEach { prev ->
            db.calendarEventDao().insertEvent(prev)
        }
        record.previousHabits.forEach { prev ->
            db.habitDao().insertHabit(prev)
        }
    }
}
