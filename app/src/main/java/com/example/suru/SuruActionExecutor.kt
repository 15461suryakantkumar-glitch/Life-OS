package com.example.suru

import android.content.Context
import com.example.data.local.CalendarEventEntity
import com.example.data.local.HabitEntity
import com.example.data.local.LifeOsDatabase
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import com.example.notifications.TaskReminderScheduler
import com.example.util.DateTimeUtils
import com.example.widgets.LifeOsWidgetUpdater
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.util.Calendar
import java.util.Locale

data class SuruWorkspaceSnapshot(
    val todayEpochDay: Long,
    val currentDateFormatted: String,
    val currentTimeFormatted: String,
    val currentMinuteOfDay: Int,
    val tasks: List<TaskEntity>,
    val events: List<CalendarEventEntity>,
    val habits: List<HabitEntity>
)

data class SuruScheduleBlockSpec(
    val title: String,
    val startTime: String,
    val endTime: String? = null,
    val durationMinutes: Int = 45,
    val category: TaskCategory = TaskCategory.STUDY
)

sealed interface SuruChatActionCommand {
    data class CreateTask(
        val title: String,
        val category: TaskCategory = TaskCategory.WORK,
        val priority: TaskPriority = TaskPriority.MEDIUM,
        val dueDateEpochDay: Long? = null,
        val scheduledTime: String? = null,
        val durationMinutes: Int = 30
    ) : SuruChatActionCommand

    data class UpdateTask(
        val taskId: Long? = null,
        val titleMatch: String? = null,
        val newTitle: String? = null,
        val category: TaskCategory? = null,
        val priority: TaskPriority? = null,
        val dueDateEpochDay: Long? = null,
        val scheduledTime: String? = null,
        val durationMinutes: Int? = null
    ) : SuruChatActionCommand

    data class CompleteTask(
        val taskId: Long? = null,
        val titleMatch: String? = null
    ) : SuruChatActionCommand

    data class DeleteTask(
        val taskId: Long? = null,
        val titleMatch: String? = null
    ) : SuruChatActionCommand

    data class SetupSchedule(
        val dateEpochDay: Long? = null,
        val clearExistingForDate: Boolean = false,
        val blocks: List<SuruScheduleBlockSpec>
    ) : SuruChatActionCommand

    data class CreateHabit(
        val title: String,
        val category: TaskCategory = TaskCategory.HEALTH,
        val targetPerWeek: Int = 7
    ) : SuruChatActionCommand

    data class CompleteHabit(
        val habitId: Long? = null,
        val titleMatch: String? = null
    ) : SuruChatActionCommand

    data class DeleteHabit(
        val habitId: Long? = null,
        val titleMatch: String? = null
    ) : SuruChatActionCommand
}

data class SuruActionParseResult(
    val visibleReply: String,
    val commands: List<SuruChatActionCommand>
)

data class SuruActionBatchResult(
    val executedCount: Int,
    val actionSummary: String?,
    val undoRecord: SuruUndoRecord?
)

object SuruActionParser {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val actionsTagRegex = Regex("(?is)<suru_actions>([\\s\\S]*?)</suru_actions>")
    private val unclosedActionsTagRegex = Regex("(?is)<suru_actions>[\\s\\S]*$")

    fun extractReplyAndActions(rawText: String): SuruActionParseResult {
        val commands = mutableListOf<SuruChatActionCommand>()
        actionsTagRegex.findAll(rawText).forEach { match ->
            val inner = match.groupValues.getOrNull(1).orEmpty()
            commands += parseActionsPayload(inner)
        }
        val stripped = rawText
            .replace(actionsTagRegex, "")
            .replace(unclosedActionsTagRegex, "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        return SuruActionParseResult(
            visibleReply = stripped,
            commands = commands
        )
    }

    internal fun parseActionsPayload(rawPayload: String): List<SuruChatActionCommand> {
        var cleaned = rawPayload.trim()
        if (cleaned.startsWith("```")) {
            cleaned = cleaned
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .trim()
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.removeSuffix("```").trim()
            }
        }
        if (cleaned.isBlank()) return emptyList()

        return runCatching {
            val jsonSlice = when {
                cleaned.startsWith("[") || cleaned.startsWith("{") -> cleaned
                else -> {
                    val startArr = cleaned.indexOf('[')
                    val endArr = cleaned.lastIndexOf(']')
                    if (startArr >= 0 && endArr > startArr) {
                        cleaned.substring(startArr, endArr + 1)
                    } else {
                        return@runCatching emptyList()
                    }
                }
            }
            val element = jsonParser.parseToJsonElement(jsonSlice)
            val objects: List<JsonObject> = when (element) {
                is JsonArray -> element.mapNotNull { it as? JsonObject }
                is JsonObject -> listOf(element)
                else -> emptyList()
            }
            objects.mapNotNull { parseActionObject(it) }
        }.getOrElse { emptyList() }
    }

    private fun parseActionObject(obj: JsonObject): SuruChatActionCommand? {
        val type = optCleanString(obj, "type")?.lowercase(Locale.US) ?: return null
        return when (type) {
            "create_task", "add_task" -> {
                val title = optCleanString(obj, "title") ?: return null
                SuruChatActionCommand.CreateTask(
                    title = title,
                    category = parseCategory(optCleanString(obj, "category"), TaskCategory.WORK),
                    priority = parsePriority(optCleanString(obj, "priority"), TaskPriority.MEDIUM),
                    dueDateEpochDay = optNullableLong(obj, "dueDateEpochDay"),
                    scheduledTime = optCleanString(obj, "scheduledTime"),
                    durationMinutes = (optNullableInt(obj, "durationMinutes") ?: 30).coerceIn(10, 360)
                )
            }

            "update_task", "reschedule_task", "edit_task" -> {
                val taskId = optNullableLong(obj, "taskId")
                val titleMatch = optCleanString(obj, "titleMatch") ?: optCleanString(obj, "title")
                val newTitle = optCleanString(obj, "newTitle")
                val category = optCleanString(obj, "category")?.let { parseCategory(it, TaskCategory.WORK) }
                val priority = optCleanString(obj, "priority")?.let { parsePriority(it, TaskPriority.MEDIUM) }
                val due = optNullableLong(obj, "dueDateEpochDay")
                val time = optCleanString(obj, "scheduledTime")
                val duration = optNullableInt(obj, "durationMinutes")?.coerceIn(10, 360)
                if (taskId == null && titleMatch == null) return null
                SuruChatActionCommand.UpdateTask(
                    taskId = taskId,
                    titleMatch = titleMatch,
                    newTitle = newTitle,
                    category = category,
                    priority = priority,
                    dueDateEpochDay = due,
                    scheduledTime = time,
                    durationMinutes = duration
                )
            }

            "complete_task", "finish_task" -> {
                val taskId = optNullableLong(obj, "taskId")
                val titleMatch = optCleanString(obj, "titleMatch") ?: optCleanString(obj, "title")
                if (taskId == null && titleMatch == null) return null
                SuruChatActionCommand.CompleteTask(taskId = taskId, titleMatch = titleMatch)
            }

            "delete_task", "remove_task" -> {
                val taskId = optNullableLong(obj, "taskId")
                val titleMatch = optCleanString(obj, "titleMatch") ?: optCleanString(obj, "title")
                if (taskId == null && titleMatch == null) return null
                SuruChatActionCommand.DeleteTask(taskId = taskId, titleMatch = titleMatch)
            }

            "setup_schedule", "schedule_blocks", "create_schedule" -> {
                val blocksJson = obj["blocks"] as? JsonArray ?: return null
                val blocks = mutableListOf<SuruScheduleBlockSpec>()
                for (blockEl in blocksJson) {
                    val b = blockEl as? JsonObject ?: continue
                    val title = optCleanString(b, "title") ?: continue
                    val start = optCleanString(b, "startTime") ?: continue
                    val end = optCleanString(b, "endTime")
                    val dur = (optNullableInt(b, "durationMinutes") ?: 45).coerceIn(10, 360)
                    val cat = parseCategory(optCleanString(b, "category"), TaskCategory.STUDY)
                    blocks.add(
                        SuruScheduleBlockSpec(
                            title = title,
                            startTime = start,
                            endTime = end,
                            durationMinutes = dur,
                            category = cat
                        )
                    )
                }
                if (blocks.isEmpty()) return null
                val dateEpoch = optNullableLong(obj, "dateEpochDay") ?: optNullableLong(obj, "dueDateEpochDay")
                val clearExisting = optBoolean(obj, "clearExistingForDate", false)
                SuruChatActionCommand.SetupSchedule(
                    dateEpochDay = dateEpoch,
                    clearExistingForDate = clearExisting,
                    blocks = blocks
                )
            }

            "create_habit", "add_habit" -> {
                val title = optCleanString(obj, "title") ?: return null
                SuruChatActionCommand.CreateHabit(
                    title = title,
                    category = parseCategory(optCleanString(obj, "category"), TaskCategory.HEALTH),
                    targetPerWeek = (optNullableInt(obj, "targetPerWeek") ?: 7).coerceIn(1, 7)
                )
            }

            "complete_habit", "toggle_habit", "check_habit" -> {
                val habitId = optNullableLong(obj, "habitId")
                val titleMatch = optCleanString(obj, "titleMatch") ?: optCleanString(obj, "title")
                if (habitId == null && titleMatch == null) return null
                SuruChatActionCommand.CompleteHabit(habitId = habitId, titleMatch = titleMatch)
            }

            "delete_habit", "remove_habit" -> {
                val habitId = optNullableLong(obj, "habitId")
                val titleMatch = optCleanString(obj, "titleMatch") ?: optCleanString(obj, "title")
                if (habitId == null && titleMatch == null) return null
                SuruChatActionCommand.DeleteHabit(habitId = habitId, titleMatch = titleMatch)
            }

            else -> null
        }
    }

    internal fun parseTimeToMinuteOfDay(raw: String?): Int? {
        val s = raw?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() } ?: return null
        val amPmMatch = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*(AM|PM)$").find(s)
        if (amPmMatch != null) {
            var hour = amPmMatch.groupValues[1].toIntOrNull() ?: return null
            val minute = amPmMatch.groupValues[2].toIntOrNull() ?: 0
            val amPm = amPmMatch.groupValues[3]
            if (hour !in 1..12 || minute !in 0..59) return null
            if (amPm == "PM" && hour != 12) hour += 12
            if (amPm == "AM" && hour == 12) hour = 0
            return hour * 60 + minute
        }
        val hhmmMatch = Regex("^(\\d{1,2}):(\\d{2})$").find(s) ?: return null
        val hour = hhmmMatch.groupValues[1].toIntOrNull() ?: return null
        val minute = hhmmMatch.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    internal fun formatHHmm(minuteOfDay: Int): String {
        val clamped = minuteOfDay.coerceIn(0, 23 * 60 + 59)
        return String.format(Locale.US, "%02d:%02d", clamped / 60, clamped % 60)
    }

    internal fun computeFreeTimeSlots(
        eventsForDay: List<CalendarEventEntity>,
        minStartMinute: Int,
        dayEndMinute: Int = 23 * 60,
        minGapMinutes: Int = 30
    ): List<Pair<Int, Int>> {
        val windowStart = minStartMinute.coerceIn(6 * 60, dayEndMinute)
        val busy = eventsForDay
            .filter { !it.isCountdown }
            .map { ev ->
                val s = ev.startMinuteOfDay.coerceIn(0, 24 * 60)
                val e = (ev.startMinuteOfDay + ev.durationMinutes).coerceIn(s, 24 * 60)
                s to e
            }
            .filter { it.second > windowStart && it.first < dayEndMinute }
            .sortedBy { it.first }

        val merged = mutableListOf<Pair<Int, Int>>()
        for (interval in busy) {
            val last = merged.lastOrNull()
            if (last == null || interval.first > last.second) {
                merged.add(interval)
            } else {
                merged[merged.lastIndex] = last.first to maxOf(last.second, interval.second)
            }
        }

        val free = mutableListOf<Pair<Int, Int>>()
        var cursor = windowStart
        for ((bStart, bEnd) in merged) {
            if (bStart - cursor >= minGapMinutes) {
                free.add(cursor to bStart)
            }
            cursor = maxOf(cursor, bEnd)
        }
        if (dayEndMinute - cursor >= minGapMinutes) {
            free.add(cursor to dayEndMinute)
        }
        return free
    }

    fun buildContextPromptSection(snapshot: SuruWorkspaceSnapshot): String {
        val today = snapshot.todayEpochDay
        val openTasks = snapshot.tasks.filter { !it.isCompleted }
            .sortedWith(compareBy<TaskEntity> { it.deadlineEpochDay }.thenByDescending { it.priority.ordinal })
        val overdueTasks = openTasks.filter { it.deadlineEpochDay < today }
        val completedToday = snapshot.tasks.filter {
            it.isCompleted && it.completedAtMillis?.let { ts -> DateTimeUtils.epochDayFromMillis(ts) == today } == true
        }
        val todayBlocks = snapshot.events
            .filter { it.dateEpochDay == today && !it.isCountdown }
            .sortedBy { it.startMinuteOfDay }
        val upcomingBlocks = snapshot.events
            .filter { it.dateEpochDay > today && it.dateEpochDay <= today + 3 && !it.isCountdown }
            .sortedWith(compareBy({ it.dateEpochDay }, { it.startMinuteOfDay }))
        val freeSlots = computeFreeTimeSlots(todayBlocks, minStartMinute = snapshot.currentMinuteOfDay)

        return buildString {
            appendLine("WORKSPACE SNAPSHOT (read-only live context):")
            appendLine(
                "- Current date/time: ${snapshot.currentDateFormatted} · ${snapshot.currentTimeFormatted} " +
                    "(todayEpochDay=$today, tomorrowEpochDay=${today + 1})"
            )

            appendLine("- Open tasks (${openTasks.size} open, ${overdueTasks.size} overdue, ${completedToday.size} done today):")
            if (openTasks.isEmpty()) {
                appendLine("  (none)")
            } else {
                openTasks.take(16).forEach { task ->
                    if (!SuruBoundedMemory.isSensitiveText(task.title)) {
                        val dueTag = when {
                            task.deadlineEpochDay < today -> "OVERDUE(${task.deadlineEpochDay})"
                            task.deadlineEpochDay == today -> "TODAY($today)"
                            task.deadlineEpochDay == today + 1 -> "TOMORROW(${today + 1})"
                            else -> "epochDay=${task.deadlineEpochDay}"
                        }
                        appendLine("  * id=${task.id} | \"${task.title}\" | ${task.priority.name} | ${task.category.name} | due=$dueTag")
                    }
                }
            }

            appendLine("- Today's scheduled blocks (${todayBlocks.size}):")
            if (todayBlocks.isEmpty()) {
                appendLine("  (none scheduled yet)")
            } else {
                todayBlocks.forEach { ev ->
                    if (!SuruBoundedMemory.isSensitiveText(ev.title)) {
                        val startStr = formatHHmm(ev.startMinuteOfDay)
                        val endStr = formatHHmm(ev.startMinuteOfDay + ev.durationMinutes)
                        appendLine("  * id=${ev.id} | $startStr-$endStr | \"${ev.title}\" | ${ev.category.name}")
                    }
                }
            }

            val slotLabels = if (freeSlots.isEmpty()) "None left before 23:00" else {
                freeSlots.joinToString(", ") { (s, e) -> "${formatHHmm(s)}-${formatHHmm(e)}" }
            }
            appendLine("- Free time slots today: $slotLabels")

            if (upcomingBlocks.isNotEmpty()) {
                val upText = upcomingBlocks.take(6).joinToString("; ") { ev ->
                    "epochDay=${ev.dateEpochDay} ${formatHHmm(ev.startMinuteOfDay)} \"${ev.title}\""
                }
                appendLine("- Upcoming scheduled blocks: $upText")
            }

            appendLine("- Habits (${snapshot.habits.size}):")
            if (snapshot.habits.isEmpty()) {
                appendLine("  (none)")
            } else {
                snapshot.habits.take(12).forEach { h ->
                    if (!SuruBoundedMemory.isSensitiveText(h.title)) {
                        val done = if (h.isCompletedOn(today)) "done today" else "pending today"
                        appendLine("  * id=${h.id} | \"${h.title}\" | $done | streak=${h.currentStreak}d")
                    }
                }
            }
        }.trimEnd()
    }

    fun buildActionsInstruction(todayEpochDay: Long): String {
        return """
ACTION EXECUTION PROTOCOL:
Whenever the user asks to create, edit, reschedule, prioritize, complete, or delete tasks, habits, or schedule blocks (or asks to plan their day/evening), write a helpful conversational reply AND append a single fenced <suru_actions>[...]</suru_actions> JSON array at the very end of your message.
If the user is just chatting or asking a question without requesting workspace changes, do NOT include <suru_actions>.
Supported action objects inside <suru_actions>[ ... ]</suru_actions>:
- create_task: {"type":"create_task","title":"...","category":"STUDY|WORK|HEALTH|CODE|PERSONAL","priority":"HIGH|MEDIUM|LOW","dueDateEpochDay":$todayEpochDay,"scheduledTime":"HH:mm","durationMinutes":30}
- update_task: {"type":"update_task","taskId":"...","titleMatch":"...","newTitle":"...","scheduledTime":"HH:mm","dueDateEpochDay":$todayEpochDay,"priority":"HIGH|MEDIUM|LOW"}
- complete_task: {"type":"complete_task","taskId":"...","titleMatch":"..."}
- delete_task: {"type":"delete_task","taskId":"...","titleMatch":"..."}
- setup_schedule: {"type":"setup_schedule","dateEpochDay":$todayEpochDay,"clearExistingForDate":false,"blocks":[{"title":"...","startTime":"18:00","endTime":"18:45","category":"STUDY|WORK|HEALTH|CODE|PERSONAL"}]}
- create_habit: {"type":"create_habit","title":"...","category":"HEALTH|STUDY|WORK|CODE|PERSONAL","targetPerWeek":7}
- complete_habit: {"type":"complete_habit","habitId":"...","titleMatch":"..."}
Rules: Use exact taskId/habitId from the workspace snapshot when updating/completing/deleting, use 24-hour "HH:mm" times, use epochDay integers (today=$todayEpochDay, tomorrow=${todayEpochDay + 1}), and never output raw JSON outside <suru_actions>...</suru_actions>.
        """.trim()
    }

    private fun optCleanString(obj: JsonObject, key: String): String? {
        val el = obj[key] ?: return null
        if (el is JsonNull) return null
        val prim = el as? JsonPrimitive ?: return null
        val s = prim.contentOrNull?.trim() ?: return null
        return if (s.isEmpty() || s.equals("null", ignoreCase = true)) null else s
    }

    private fun optNullableLong(obj: JsonObject, key: String): Long? {
        val el = obj[key] ?: return null
        if (el is JsonNull) return null
        val prim = el as? JsonPrimitive ?: return null
        return prim.longOrNull ?: prim.contentOrNull?.trim()?.toLongOrNull()
    }

    private fun optNullableInt(obj: JsonObject, key: String): Int? {
        val el = obj[key] ?: return null
        if (el is JsonNull) return null
        val prim = el as? JsonPrimitive ?: return null
        return prim.intOrNull ?: prim.contentOrNull?.trim()?.toIntOrNull()
    }

    private fun optBoolean(obj: JsonObject, key: String, default: Boolean = false): Boolean {
        val el = obj[key] ?: return default
        if (el is JsonNull) return default
        val prim = el as? JsonPrimitive ?: return default
        return prim.booleanOrNull
            ?: prim.contentOrNull?.trim()?.lowercase(Locale.US)?.let {
                when (it) {
                    "true", "1", "yes" -> true
                    "false", "0", "no" -> false
                    else -> null
                }
            }
            ?: default
    }

    private fun parseCategory(raw: String?, default: TaskCategory): TaskCategory {
        val clean = raw?.trim()?.uppercase(Locale.US) ?: return default
        return when (clean) {
            "BUILD", "CODING" -> TaskCategory.CODE
            else -> TaskCategory.entries.firstOrNull { it.name == clean } ?: default
        }
    }

    private fun parsePriority(raw: String?, default: TaskPriority): TaskPriority {
        val clean = raw?.trim()?.uppercase(Locale.US) ?: return default
        return TaskPriority.entries.firstOrNull { it.name == clean } ?: default
    }
}

class SuruActionExecutor(
    private val db: LifeOsDatabase,
    private val appContext: Context? = null
) {

    suspend fun captureWorkspaceSnapshot(): SuruWorkspaceSnapshot {
        val today = DateTimeUtils.todayEpochDay()
        val cal = Calendar.getInstance()
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val tasks = db.taskDao().getAllTasksOnce()
        val events = db.calendarEventDao().getAllEventsOnce()
        val habits = db.habitDao().getAllHabitsOnce()
        return SuruWorkspaceSnapshot(
            todayEpochDay = today,
            currentDateFormatted = DateTimeUtils.formatFullDate(today),
            currentTimeFormatted = "${DateTimeUtils.formatLiveTime()} (${SuruActionParser.formatHHmm(minuteOfDay)})",
            currentMinuteOfDay = minuteOfDay,
            tasks = tasks,
            events = events,
            habits = habits
        )
    }

    suspend fun executeActions(
        commands: List<SuruChatActionCommand>,
        todayEpochDay: Long = DateTimeUtils.todayEpochDay()
    ): SuruActionBatchResult {
        if (commands.isEmpty()) {
            return SuruActionBatchResult(executedCount = 0, actionSummary = null, undoRecord = null)
        }

        val insertedTaskIds = mutableListOf<Long>()
        val insertedEventIds = mutableListOf<Long>()
        val insertedHabitIds = mutableListOf<Long>()
        val previousTasks = mutableListOf<TaskEntity>()
        val previousEvents = mutableListOf<CalendarEventEntity>()
        val previousHabits = mutableListOf<HabitEntity>()

        var taskMutationCount = 0
        var scheduleMutationCount = 0
        var habitMutationCount = 0

        for (cmd in commands) {
            when (cmd) {
                is SuruChatActionCommand.CreateTask -> {
                    val targetDay = (cmd.dueDateEpochDay ?: todayEpochDay).coerceAtLeast(todayEpochDay - 365)
                    val sections = db.taskSectionDao().getAllSectionsOnce()
                    val matchedSection = sections.firstOrNull { cmd.title.contains(it.name, ignoreCase = true) }
                    val newId = db.taskDao().insertTask(
                        TaskEntity(
                            sectionId = matchedSection?.id ?: 0L,
                            title = cmd.title,
                            priorityName = cmd.priority.name,
                            categoryName = cmd.category.name,
                            deadlineEpochDay = targetDay,
                            xpReward = cmd.priority.xpValue
                        )
                    )
                    insertedTaskIds.add(newId)
                    taskMutationCount++

                    val startMin = SuruActionParser.parseTimeToMinuteOfDay(cmd.scheduledTime)
                    if (startMin != null) {
                        val eventId = db.calendarEventDao().insertEvent(
                            CalendarEventEntity(
                                title = cmd.title,
                                notes = "Scheduled by SURU",
                                dateEpochDay = targetDay,
                                startMinuteOfDay = startMin,
                                durationMinutes = cmd.durationMinutes.coerceIn(10, 360),
                                categoryName = cmd.category.name,
                                reminderMinutesBefore = 15
                            )
                        )
                        insertedEventIds.add(eventId)
                        scheduleMutationCount++
                    }
                }

                is SuruChatActionCommand.UpdateTask -> {
                    val allTasks = db.taskDao().getAllTasksOnce()
                    val target = findMatchingTask(cmd.taskId, cmd.titleMatch, allTasks) ?: continue
                    if (previousTasks.none { it.id == target.id }) {
                        previousTasks.add(target)
                    }
                    val nextPriority = cmd.priority ?: target.priority
                    val nextCategory = cmd.category ?: target.category
                    val nextTitle = cmd.newTitle?.takeIf { it.isNotBlank() } ?: target.title
                    val nextDeadline = cmd.dueDateEpochDay ?: target.deadlineEpochDay
                    db.taskDao().updateTask(
                        target.copy(
                            title = nextTitle,
                            priorityName = nextPriority.name,
                            categoryName = nextCategory.name,
                            deadlineEpochDay = nextDeadline,
                            xpReward = nextPriority.xpValue,
                            snoozedUntilMillis = if (nextDeadline != target.deadlineEpochDay) 0L else target.snoozedUntilMillis,
                            reminderCountToday = if (nextDeadline != target.deadlineEpochDay) 0 else target.reminderCountToday,
                            remindersDismissed = if (nextDeadline != target.deadlineEpochDay) false else target.remindersDismissed
                        )
                    )
                    taskMutationCount++

                    val startMin = SuruActionParser.parseTimeToMinuteOfDay(cmd.scheduledTime)
                    if (startMin != null) {
                        val allEvents = db.calendarEventDao().getAllEventsOnce()
                        val existingEvent = allEvents.firstOrNull { ev ->
                            !ev.isCountdown && (ev.title.equals(target.title, ignoreCase = true) ||
                                ev.title.equals(nextTitle, ignoreCase = true))
                        }
                        if (existingEvent != null) {
                            if (previousEvents.none { it.id == existingEvent.id }) {
                                previousEvents.add(existingEvent)
                            }
                            db.calendarEventDao().updateEvent(
                                existingEvent.copy(
                                    title = nextTitle,
                                    dateEpochDay = nextDeadline,
                                    startMinuteOfDay = startMin,
                                    durationMinutes = cmd.durationMinutes ?: existingEvent.durationMinutes,
                                    categoryName = nextCategory.name
                                )
                            )
                        } else {
                            val eventId = db.calendarEventDao().insertEvent(
                                CalendarEventEntity(
                                    title = nextTitle,
                                    notes = "Rescheduled by SURU",
                                    dateEpochDay = nextDeadline,
                                    startMinuteOfDay = startMin,
                                    durationMinutes = cmd.durationMinutes ?: 30,
                                    categoryName = nextCategory.name,
                                    reminderMinutesBefore = 15
                                )
                            )
                            insertedEventIds.add(eventId)
                        }
                        scheduleMutationCount++
                    }
                }

                is SuruChatActionCommand.CompleteTask -> {
                    val allTasks = db.taskDao().getAllTasksOnce()
                    val openFirst = allTasks.filter { !it.isCompleted }
                    val target = findMatchingTask(cmd.taskId, cmd.titleMatch, openFirst)
                        ?: findMatchingTask(cmd.taskId, cmd.titleMatch, allTasks)
                        ?: continue
                    if (previousTasks.none { it.id == target.id }) {
                        previousTasks.add(target)
                    }
                    db.taskDao().updateTask(
                        target.copy(
                            isCompleted = true,
                            completedAtMillis = System.currentTimeMillis(),
                            xpAwarded = true
                        )
                    )
                    taskMutationCount++
                }

                is SuruChatActionCommand.DeleteTask -> {
                    val allTasks = db.taskDao().getAllTasksOnce()
                    val target = findMatchingTask(cmd.taskId, cmd.titleMatch, allTasks) ?: continue
                    if (previousTasks.none { it.id == target.id }) {
                        previousTasks.add(target)
                    }
                    db.taskDao().deleteTask(target)
                    taskMutationCount++
                }

                is SuruChatActionCommand.SetupSchedule -> {
                    val targetDate = cmd.dateEpochDay ?: todayEpochDay
                    if (cmd.clearExistingForDate) {
                        val existingForDate = db.calendarEventDao().getAllEventsOnce()
                            .filter { it.dateEpochDay == targetDate && !it.isCountdown }
                        existingForDate.forEach { ev ->
                            if (previousEvents.none { it.id == ev.id }) {
                                previousEvents.add(ev)
                            }
                            db.calendarEventDao().deleteEvent(ev)
                        }
                    }
                    for (block in cmd.blocks) {
                        val startMin = SuruActionParser.parseTimeToMinuteOfDay(block.startTime) ?: continue
                        val endMin = SuruActionParser.parseTimeToMinuteOfDay(block.endTime)
                        val duration = if (endMin != null && endMin > startMin) {
                            (endMin - startMin).coerceIn(10, 360)
                        } else {
                            block.durationMinutes.coerceIn(10, 360)
                        }
                        val eventId = db.calendarEventDao().insertEvent(
                            CalendarEventEntity(
                                title = block.title,
                                notes = "Planned by SURU",
                                dateEpochDay = targetDate,
                                startMinuteOfDay = startMin,
                                durationMinutes = duration,
                                categoryName = block.category.name,
                                reminderMinutesBefore = 15
                            )
                        )
                        insertedEventIds.add(eventId)
                        scheduleMutationCount++
                    }
                }

                is SuruChatActionCommand.CreateHabit -> {
                    val habitId = db.habitDao().insertHabit(
                        HabitEntity(
                            title = cmd.title,
                            categoryName = cmd.category.name,
                            targetPerWeek = cmd.targetPerWeek,
                            xpReward = 25
                        )
                    )
                    insertedHabitIds.add(habitId)
                    habitMutationCount++
                }

                is SuruChatActionCommand.CompleteHabit -> {
                    val allHabits = db.habitDao().getAllHabitsOnce()
                    val target = findMatchingHabit(cmd.habitId, cmd.titleMatch, allHabits) ?: continue
                    if (target.isCompletedOn(todayEpochDay)) continue
                    if (previousHabits.none { it.id == target.id }) {
                        previousHabits.add(target)
                    }
                    val nextStreak = if (target.lastCompletedEpochDay == todayEpochDay - 1) {
                        target.currentStreak + 1
                    } else {
                        1
                    }
                    db.habitDao().updateHabit(
                        target.copy(
                            lastCompletedEpochDay = todayEpochDay,
                            xpAwardedEpochDay = todayEpochDay,
                            currentStreak = nextStreak,
                            bestStreak = maxOf(target.bestStreak, nextStreak)
                        )
                    )
                    habitMutationCount++
                }

                is SuruChatActionCommand.DeleteHabit -> {
                    val allHabits = db.habitDao().getAllHabitsOnce()
                    val target = findMatchingHabit(cmd.habitId, cmd.titleMatch, allHabits) ?: continue
                    if (previousHabits.none { it.id == target.id }) {
                        previousHabits.add(target)
                    }
                    db.habitDao().deleteHabit(target)
                    habitMutationCount++
                }
            }
        }

        val total = taskMutationCount + scheduleMutationCount + habitMutationCount
        if (total == 0) {
            return SuruActionBatchResult(executedCount = 0, actionSummary = null, undoRecord = null)
        }

        appContext?.let { ctx ->
            runCatching {
                if (db.taskDao().getAllTasksOnce().none { !it.isCompleted }) {
                    TaskReminderScheduler.clearReminderNotification(ctx)
                }
                LifeOsWidgetUpdater.updateAllWidgets(ctx)
            }
        }

        val summary = buildSummaryLabel(taskMutationCount, scheduleMutationCount, habitMutationCount)
        val undoRecord = SuruUndoRecord(
            label = summary,
            insertedTaskIds = insertedTaskIds,
            insertedEventIds = insertedEventIds,
            insertedHabitIds = insertedHabitIds,
            previousTasks = previousTasks,
            previousEvents = previousEvents,
            previousHabits = previousHabits
        )
        return SuruActionBatchResult(
            executedCount = total,
            actionSummary = summary,
            undoRecord = undoRecord
        )
    }

    suspend fun undo(record: SuruUndoRecord) {
        SuruActionEngine(db).undo(record)
        appContext?.let { ctx ->
            runCatching { LifeOsWidgetUpdater.updateAllWidgets(ctx) }
        }
    }

    private fun buildSummaryLabel(tasks: Int, schedule: Int, habits: Int): String {
        val parts = mutableListOf<String>()
        if (tasks > 0) parts.add("$tasks task${if (tasks == 1) "" else "s"}")
        if (schedule > 0) parts.add("$schedule schedule item${if (schedule == 1) "" else "s"}")
        if (habits > 0) parts.add("$habits habit${if (habits == 1) "" else "s"}")
        return "✓ Updated ${parts.joinToString(" · ")}"
    }

    private fun findMatchingTask(id: Long?, query: String?, candidates: List<TaskEntity>): TaskEntity? {
        if (id != null) {
            candidates.firstOrNull { it.id == id }?.let { return it }
        }
        val q = query?.trim()?.lowercase(Locale.US).orEmpty()
        if (q.isBlank()) return null
        return candidates.firstOrNull { it.title.equals(q, ignoreCase = true) }
            ?: candidates.firstOrNull {
                val lower = it.title.lowercase(Locale.US)
                lower.contains(q) || q.contains(lower)
            }
    }

    private fun findMatchingHabit(id: Long?, query: String?, candidates: List<HabitEntity>): HabitEntity? {
        if (id != null) {
            candidates.firstOrNull { it.id == id }?.let { return it }
        }
        val q = query?.trim()?.lowercase(Locale.US).orEmpty()
        if (q.isBlank()) return null
        return candidates.firstOrNull { it.title.equals(q, ignoreCase = true) }
            ?: candidates.firstOrNull {
                val lower = it.title.lowercase(Locale.US)
                lower.contains(q) || q.contains(lower)
            }
    }
}
