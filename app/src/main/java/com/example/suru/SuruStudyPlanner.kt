package com.example.suru

import com.example.data.local.CalendarEventEntity
import com.example.data.local.RecurrencePattern
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import com.example.util.DateTimeUtils
import kotlin.math.roundToInt

object CalendarIntelligence {

    fun findOverlaps(
        dateEpochDay: Long,
        startMinuteOfDay: Int,
        durationMinutes: Int,
        existingEvents: List<CalendarEventEntity>
    ): List<CalendarEventEntity> {
        val proposedEnd = startMinuteOfDay + durationMinutes
        return existingEvents.filter { ev ->
            val occursOnDay = ev.dateEpochDay == dateEpochDay ||
                (ev.recurrence == RecurrencePattern.DAILY && ev.dateEpochDay <= dateEpochDay)
            if (!occursOnDay || ev.isCountdown) return@filter false
            val evStart = ev.startMinuteOfDay
            val evEnd = ev.startMinuteOfDay + ev.durationMinutes
            startMinuteOfDay < evEnd && proposedEnd > evStart
        }
    }

    fun findConflictFreeStartMinute(
        dateEpochDay: Int,
        todayEpochDay: Long,
        preferredStartMinute: Int,
        durationMinutes: Int,
        existingEvents: List<CalendarEventEntity>,
        additionalBooked: List<Pair<Int, Int>> = emptyList()
    ): Pair<Int, CalendarEventEntity?> {
        val targetDay = todayEpochDay + dateEpochDay
        val candidates = listOf(
            preferredStartMinute,
            preferredStartMinute + 30,
            preferredStartMinute + 60,
            preferredStartMinute - 60,
            540,  // 9:00 AM
            660,  // 11:00 AM
            840,  // 2:00 PM
            1020, // 5:00 PM
            1110, // 6:30 PM
            1200  // 8:00 PM
        ).map { it.coerceIn(360, 1380 - durationMinutes.coerceAtMost(180)) }.distinct()

        for (candidate in candidates) {
            val overlapsEvent = findOverlaps(targetDay, candidate, durationMinutes, existingEvents)
            val overlapsAdditional = additionalBooked.any { (bStart, bDur) ->
                val bEnd = bStart + bDur
                candidate < bEnd && (candidate + durationMinutes) > bStart
            }
            if (overlapsEvent.isEmpty() && !overlapsAdditional) {
                return candidate to null
            }
        }
        val firstOverlap = findOverlaps(targetDay, preferredStartMinute, durationMinutes, existingEvents).firstOrNull()
        return preferredStartMinute to firstOverlap
    }

    fun suggestFreeSlots(
        dateEpochDay: Long,
        durationMinutes: Int,
        existingEvents: List<CalendarEventEntity>
    ): List<Int> {
        val windows = listOf(480, 570, 690, 870, 1020, 1110, 1200)
        return windows.filter { start ->
            findOverlaps(dateEpochDay, start, durationMinutes, existingEvents).isEmpty()
        }.take(3)
    }
}

object TaskIntelligence {
    fun findDuplicate(title: String, existingTasks: List<TaskEntity>): TaskEntity? {
        val normalized = normalize(title)
        if (normalized.isBlank()) return null
        return existingTasks.firstOrNull { task ->
            val existingNorm = normalize(task.title)
            existingNorm == normalized ||
                (existingNorm.length > 5 && normalized.length > 5 &&
                    (existingNorm.contains(normalized) || normalized.contains(existingNorm)))
        }
    }

    fun buildSubtasks(taskTitle: String): List<String> {
        val clean = taskTitle.trim().ifBlank { "Study Topic" }
        val lower = clean.lowercase()
        return when {
            listOf("chapter", "physics", "chem", "bio", "math", "ncert", "study", "revision").any { lower.contains(it) } ->
                listOf(
                    "$clean — Core Concepts & Key Formulas",
                    "$clean — Solved Examples & Practice Set",
                    "$clean — Quick Summary & Self-Test"
                )
            listOf("code", "app", "bug", "feature", "build", "api").any { lower.contains(it) } ->
                listOf(
                    "$clean — Scope & Edge Cases",
                    "$clean — Core Implementation",
                    "$clean — Verification & Cleanup"
                )
            else ->
                listOf(
                    "$clean — Step 1: Outline & Setup",
                    "$clean — Step 2: Focused Execution",
                    "$clean — Step 3: Final Review"
                )
        }
    }

    private fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

object SuruStudyPlanner {

    fun estimateChapterWeight(chapterName: String): Pair<Float, String> {
        val lower = chapterName.lowercase()
        return when {
            listOf(
                "electro", "magnet", "thermo", "quantum", "calculus", "integr",
                "organic", "mechan", "optic", "equilibrium", "genetics", "differen"
            ).any { lower.contains(it) } -> 1.35f to "High Depth"
            listOf("intro", "basic", "overview", "summary", "unit", "dimension", "glossary")
                .any { lower.contains(it) } -> 0.75f to "Light"
            else -> 1.0f to "Standard"
        }
    }

    fun buildStudyPlanDraft(
        subject: String,
        chapters: List<String>,
        deadlineDays: Int,
        dailyAvailableMinutes: Int,
        preferredWindow: SuruTimeWindow,
        startHour: Int,
        startMinute: Int,
        existingEvents: List<CalendarEventEntity>,
        todayEpochDay: Long = DateTimeUtils.todayEpochDay(),
        customWeights: Map<String, Float>? = null
    ): SuruStudyPlanDraft {
        val cleanSubject = subject.trim().ifBlank { "Study" }
        val cleanChapters = chapters.map { it.trim() }.filter { it.isNotBlank() }
            .ifEmpty { listOf("$cleanSubject Module 1", "$cleanSubject Module 2") }
        val safeDays = deadlineDays.coerceIn(1, 60)
        val safeDailyMins = dailyAvailableMinutes.coerceIn(30, 360)
        val preferredStart = (startHour.coerceIn(6, 22) * 60 + startMinute.coerceIn(0, 59))

        val weightMap = linkedMapOf<String, Float>()
        val weightLabels = linkedMapOf<String, String>()
        cleanChapters.forEach { ch ->
            val (estW, lbl) = estimateChapterWeight(ch)
            val w = customWeights?.get(ch)?.coerceIn(0.5f, 2.0f) ?: estW
            weightMap[ch] = w
            weightLabels[ch] = when {
                w >= 1.25f -> "High Depth"
                w <= 0.85f -> "Light"
                else -> lbl
            }
        }

        val totalWeight = weightMap.values.sum().coerceAtLeast(1f)
        val studyDaysCount = if (safeDays >= 3) safeDays - 1 else safeDays
        val totalStudyCapacity = studyDaysCount * safeDailyMins

        // Allocate chapters proportionally based on weights so heavier chapters get more time
        val chapterMinutes = cleanChapters.associateWith { ch ->
            val share = (weightMap[ch] ?: 1f) / totalWeight
            (totalStudyCapacity * share).roundToInt().coerceAtLeast(30)
        }

        val sessions = mutableListOf<SuruPlanSessionPreview>()
        val chapterQueue = mutableListOf<Triple<String, String, Int>>() // chapter, phase, mins

        cleanChapters.forEach { ch ->
            val alloc = chapterMinutes[ch] ?: 60
            val conceptMins = (alloc * 0.50f).roundToInt().coerceIn(20, safeDailyMins)
            val practiceMins = (alloc * 0.30f).roundToInt().coerceIn(15, safeDailyMins)
            val revisionMins = (alloc - conceptMins - practiceMins).coerceAtLeast(15)
            chapterQueue.add(Triple(ch, "Concept", conceptMins))
            chapterQueue.add(Triple(ch, "Practice", practiceMins))
            chapterQueue.add(Triple(ch, "Revision", revisionMins))
        }

        val dayRemainingBudget = IntArray(safeDays) { safeDailyMins }
        val dayBookedSlots = Array(safeDays) { mutableListOf<Pair<Int, Int>>() }
        var dayCursor = 0

        for ((chapter, phase, rawMins) in chapterQueue) {
            var remainingPart = rawMins
            while (remainingPart > 0) {
                while (dayCursor < studyDaysCount - 1 && dayRemainingBudget[dayCursor] < 15) {
                    dayCursor++
                }
                val budgetToday = dayRemainingBudget[dayCursor]
                val chunk = minOf(remainingPart, budgetToday).coerceAtLeast(15)
                val dayPreferred = if (dayBookedSlots[dayCursor].isEmpty()) {
                    preferredStart
                } else {
                    val lastEnd = dayBookedSlots[dayCursor].maxOf { it.first + it.second }
                    lastEnd + 10 // 10-min rest break between study blocks
                }

                val (resolvedStart, overlap) = CalendarIntelligence.findConflictFreeStartMinute(
                    dateEpochDay = dayCursor,
                    todayEpochDay = todayEpochDay,
                    preferredStartMinute = dayPreferred,
                    durationMinutes = chunk,
                    existingEvents = existingEvents,
                    additionalBooked = dayBookedSlots[dayCursor]
                )

                val wLabel = weightLabels[chapter] ?: "Standard"
                val title = "$cleanSubject · $chapter ($phase)"
                sessions.add(
                    SuruPlanSessionPreview(
                        dayOffset = dayCursor,
                        title = title,
                        chapterName = chapter,
                        phase = phase,
                        weightLabel = wLabel,
                        startMinuteOfDay = resolvedStart,
                        durationMinutes = chunk,
                        hasConflict = overlap != null,
                        conflictReason = overlap?.let {
                            "Overlaps \"${it.title}\" at ${DateTimeUtils.formatTimeOfDay(it.startMinuteOfDay)}"
                        }
                    )
                )
                dayBookedSlots[dayCursor].add(resolvedStart to chunk)
                dayRemainingBudget[dayCursor] = (dayRemainingBudget[dayCursor] - chunk).coerceAtLeast(0)
                remainingPart -= chunk

                if (dayRemainingBudget[dayCursor] < 15 && dayCursor < studyDaysCount - 1) {
                    dayCursor++
                } else if (remainingPart > 0 && dayCursor == studyDaysCount - 1) {
                    break
                }
            }
        }

        // Dedicated final cumulative revision day if horizon >= 3 days
        if (safeDays >= 3) {
            val revDay = safeDays - 1
            val revDuration = minOf(safeDailyMins, 90).coerceAtLeast(30)
            val (revStart, revOverlap) = CalendarIntelligence.findConflictFreeStartMinute(
                dateEpochDay = revDay,
                todayEpochDay = todayEpochDay,
                preferredStartMinute = preferredStart,
                durationMinutes = revDuration,
                existingEvents = existingEvents,
                additionalBooked = dayBookedSlots[revDay]
            )
            sessions.add(
                SuruPlanSessionPreview(
                    dayOffset = revDay,
                    title = "$cleanSubject · Full Mock & Revision (${cleanChapters.size} chapters)",
                    chapterName = cleanChapters.joinToString(", "),
                    phase = "Revision",
                    weightLabel = "Cumulative",
                    startMinuteOfDay = revStart,
                    durationMinutes = revDuration,
                    hasConflict = revOverlap != null,
                    conflictReason = revOverlap?.let {
                        "Overlaps \"${it.title}\" at ${DateTimeUtils.formatTimeOfDay(it.startMinuteOfDay)}"
                    }
                )
            )
        }

        val conflictCount = sessions.count { it.hasConflict }
        val summaryNote = buildString {
            append("${cleanChapters.size} chapters weighted by depth across $safeDays days · ")
            append("Daily cap ${DateTimeUtils.formatMinutesCompact(safeDailyMins)} (${preferredWindow.label}). ")
            append("Includes concept learning, practice & revision.")
            if (conflictCount > 0) {
                append(" $conflictCount block(s) have schedule conflicts you can adjust.")
            }
        }

        return SuruStudyPlanDraft(
            subject = cleanSubject,
            chapters = cleanChapters,
            chapterWeights = weightMap,
            deadlineDays = safeDays,
            dailyAvailableMinutes = safeDailyMins,
            preferredWindow = preferredWindow,
            startHour = startHour,
            startMinute = startMinute,
            sessions = sessions.sortedWith(compareBy({ it.dayOffset }, { it.startMinuteOfDay })),
            summaryNote = summaryNote
        )
    }

    fun buildBacklogRecoveryPlan(
        tasks: List<TaskEntity>,
        existingEvents: List<CalendarEventEntity>,
        dailyFocusGoalMinutes: Int = 120,
        todayEpochDay: Long = DateTimeUtils.todayEpochDay()
    ): SuruBacklogPlan {
        val pending = tasks
            .filter { !it.isCompleted }
            .sortedWith(
                compareByDescending<TaskEntity> { it.priority.ordinal }
                    .thenBy { it.deadlineEpochDay }
                    .thenBy { it.createdAtMillis }
            )

        if (pending.isEmpty()) {
            return SuruBacklogPlan(
                items = emptyList(),
                summaryNote = "No pending backlog tasks right now — your queue is clear!"
            )
        }

        val maxTasksPerDay = if (dailyFocusGoalMinutes >= 150) 3 else 2
        val items = mutableListOf<SuruBacklogItem>()
        val dayBooked = Array(5) { mutableListOf<Pair<Int, Int>>() }
        val dayCounts = IntArray(5)

        pending.take(10).forEach { task ->
            var placedDay = 0
            while (placedDay < 4 && dayCounts[placedDay] >= maxTasksPerDay) {
                placedDay++
            }
            val duration = when (task.priority) {
                TaskPriority.CRITICAL -> 60
                TaskPriority.HIGH -> 50
                TaskPriority.MEDIUM -> 40
                TaskPriority.LOW -> 30
            }
            val baseStart = 570 + dayCounts[placedDay] * 120 // 9:30 AM, 11:30 AM, 1:30 PM
            val (slotStart, overlap) = CalendarIntelligence.findConflictFreeStartMinute(
                dateEpochDay = placedDay,
                todayEpochDay = todayEpochDay,
                preferredStartMinute = baseStart,
                durationMinutes = duration,
                existingEvents = existingEvents,
                additionalBooked = dayBooked[placedDay]
            )
            items.add(
                SuruBacklogItem(
                    taskId = task.id,
                    title = task.title,
                    priority = task.priority,
                    plannedDayOffset = placedDay,
                    startMinuteOfDay = slotStart,
                    durationMinutes = duration,
                    hasConflict = overlap != null
                )
            )
            dayBooked[placedDay].add(slotStart to duration)
            dayCounts[placedDay]++
        }

        val overdueCount = pending.count { it.deadlineEpochDay < todayEpochDay }
        val daysUsed = items.maxOfOrNull { it.plannedDayOffset + 1 } ?: 1
        val summary = buildString {
            append("Prioritized ${items.size} open task(s)")
            if (overdueCount > 0) append(" ($overdueCount overdue)")
            append(" evenly across $daysUsed day(s) without crowding your calendar or altering completed work.")
        }

        return SuruBacklogPlan(
            items = items,
            summaryNote = summary
        )
    }
}
