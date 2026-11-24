package com.example.suru

import com.example.data.local.CalendarEventEntity
import com.example.data.local.SuruMemoryEntity
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import com.example.util.DateTimeUtils
import java.util.Locale

object SuruNluEngine {

    private val numberWords = mapOf(
        "one" to 1, "ek" to 1, "एक" to 1,
        "two" to 2, "do" to 2, "दो" to 2,
        "three" to 3, "teen" to 3, "तीन" to 3,
        "four" to 4, "char" to 4, "chaar" to 4, "चार" to 4,
        "five" to 5, "paanch" to 5, "panch" to 5, "पांच" to 5,
        "six" to 6, "chhah" to 6, "che" to 6, "छह" to 6,
        "seven" to 7, "saat" to 7, "सात" to 7,
        "eight" to 8, "aath" to 8, "आठ" to 8,
        "ten" to 10, "das" to 10, "दस" to 10,
        "half" to 30
    )

    fun parseAndPlan(
        userPrompt: String,
        context: LifeContextSnapshot,
        tasks: List<TaskEntity>,
        events: List<CalendarEventEntity>,
        memories: List<SuruMemoryEntity>,
        pendingContext: SuruPendingContext?,
        lastStudyDraft: SuruStudyPlanDraft?,
        todayEpochDay: Long = DateTimeUtils.todayEpochDay()
    ): SuruStructuredAction? {
        val raw = stripWakeWords(userPrompt.trim())
        if (raw.isBlank()) return null
        val lower = raw.lowercase(Locale.US)

        // 1. Handle active conversational correction or slot-filling follow-up first
        if (pendingContext != null) {
            handlePendingFollowUp(
                raw = raw,
                lower = lower,
                pending = pendingContext,
                events = events,
                memories = memories,
                todayEpochDay = todayEpochDay
            )?.let { return it }
        }

        // 2. Handle conversational correction on a recently proposed Study Plan ("Actually, make it evening" / "make it 90 mins")
        if (lastStudyDraft != null && looksLikeCorrection(lower)) {
            val newWindow = extractPreferredWindow(lower) ?: lastStudyDraft.preferredWindow
            val parsedTime = extractTimeOfDay(lower)
            val newStartHour = parsedTime?.first ?: newWindow.defaultHour
            val newStartMinute = parsedTime?.second ?: newWindow.defaultMinute
            val newDailyMins = extractDailyAvailableMinutes(lower) ?: lastStudyDraft.dailyAvailableMinutes
            val newDays = extractDeadlineDays(lower) ?: lastStudyDraft.deadlineDays

            val updatedDraft = SuruStudyPlanner.buildStudyPlanDraft(
                subject = lastStudyDraft.subject,
                chapters = lastStudyDraft.chapters,
                deadlineDays = newDays,
                dailyAvailableMinutes = newDailyMins,
                preferredWindow = newWindow,
                startHour = newStartHour,
                startMinute = newStartMinute,
                existingEvents = events,
                todayEpochDay = todayEpochDay,
                customWeights = lastStudyDraft.chapterWeights
            )
            return SuruStructuredAction(
                intent = SuruIntent.CREATE_STUDY_PLAN,
                reply = "Updated your ${updatedDraft.subject} plan to ${updatedDraft.preferredWindow.label} (${DateTimeUtils.formatMinutesCompact(updatedDraft.dailyAvailableMinutes)}/day across ${updatedDraft.deadlineDays} days). Preview is ready to confirm!",
                subject = updatedDraft.subject,
                chapters = updatedDraft.chapters,
                studyPlanDraft = updatedDraft,
                requiresConfirmation = true,
                confirmationTitle = "Confirm Updated Study Plan",
                confirmationMessage = updatedDraft.summaryNote,
                isOfflineEngine = true
            )
        }

        // 3. Morning Briefing / Evening Reflection
        if (lower.contains("morning briefing") || lower.contains("brief me") || lower.contains("aaj ka plan") || lower.contains("good morning suru")) {
            return SuruStructuredAction(
                intent = SuruIntent.MORNING_BRIEFING,
                reply = "Here is your Morning Briefing for today.",
                isOfflineEngine = true
            )
        }
        if (lower.contains("evening reflection") || lower.contains("day review") || lower.contains("aaj kaisa raha") || lower.contains("good night suru")) {
            return SuruStructuredAction(
                intent = SuruIntent.EVENING_REFLECTION,
                reply = "Here is your Evening Reflection.",
                isOfflineEngine = true
            )
        }

        // 4. Explicit Personal Memory Saving ("remember that I prefer evening study")
        if (looksLikeMemorySave(lower)) {
            val cleanedVal = raw
                .replace(Regex("(?i)^(?:please\\s+)?(?:remember\\s+(?:that)?|note\\s+that|yaad\\s+rakhna\\s+(?:ki)?)\\s*"), "")
                .trim()
                .ifBlank { raw }
            val category = when {
                lower.contains("morning") || lower.contains("evening") || lower.contains("night") || lower.contains("shaam") || lower.contains("subah") -> "TIME_PREF"
                lower.contains("weak") || lower.contains("hard") || lower.contains("physics") || lower.contains("math") || lower.contains("chem") -> "STUDY_PREF"
                else -> "GOAL"
            }
            val keyLabel = when (category) {
                "TIME_PREF" -> "Preferred Focus Window"
                "STUDY_PREF" -> "Study Preference"
                else -> "Personal Goal"
            }
            return SuruStructuredAction(
                intent = SuruIntent.SAVE_MEMORY,
                reply = "Got it! I'll remember: \"$cleanedVal\". You can view, edit, or delete this anytime in SURU Memory.",
                memoryKey = keyLabel,
                memoryValue = cleanedVal,
                memoryCategory = category,
                isOfflineEngine = true
            )
        }

        // 5. Backlog Recovery / Overdue Task Rescheduling
        if (looksLikeBacklogIntent(lower)) {
            val backlogPlan = SuruStudyPlanner.buildBacklogRecoveryPlan(
                tasks = tasks,
                existingEvents = events,
                dailyFocusGoalMinutes = context.dailyFocusGoalMinutes,
                todayEpochDay = todayEpochDay
            )
            if (backlogPlan.items.isEmpty()) {
                return SuruStructuredAction(
                    intent = SuruIntent.CHAT,
                    reply = "You have zero pending backlog right now — all caught up! Want to add a new focus goal or study plan?",
                    isOfflineEngine = true
                )
            }
            return SuruStructuredAction(
                intent = SuruIntent.BACKLOG_RECOVERY,
                reply = "I analyzed your ${backlogPlan.items.size} pending task(s) and prepared a balanced recovery plan. Review and confirm when ready!",
                backlogPlan = backlogPlan,
                requiresConfirmation = true,
                confirmationTitle = "Confirm Backlog Recovery Plan",
                confirmationMessage = backlogPlan.summaryNote,
                isOfflineEngine = true
            )
        }

        // 6. Automatic Study Planner (English, Hindi & Hinglish)
        if (looksLikeStudyPlanIntent(lower)) {
            val subject = extractSubject(raw, lower) ?: inferSubjectFromMemories(memories) ?: "Study"
            val chapters = extractChaptersList(raw)
            val deadlineDays = extractDeadlineDays(lower)
            val dailyMins = extractDailyAvailableMinutes(lower)
            val windowFromMemory = memories.firstOrNull { it.isEnabled && it.category == "TIME_PREF" }?.valueText
            val window = extractPreferredWindow(lower) ?: extractPreferredWindow(windowFromMemory?.lowercase().orEmpty())

            // Check critical missing info
            if (chapters.isEmpty()) {
                val expectedCount = extractExpectedChapterCount(lower)
                val promptQuestion = if (expectedCount != null) {
                    if (isHindiOrHinglish(lower)) {
                        "Sure! Kaunse $expectedCount chapters complete karne hain?"
                    } else {
                        "Sure! Which $expectedCount chapters would you like me to plan?"
                    }
                } else {
                    if (isHindiOrHinglish(lower)) {
                        "Sure! Kaun-kaunse chapters ya topics cover karne hain?"
                    } else {
                        "Sure! Which chapters or topics should I include in this study plan?"
                    }
                }
                val parsedTime = extractTimeOfDay(lower)
                return SuruStructuredAction(
                    intent = SuruIntent.ASK_CLARIFICATION,
                    reply = promptQuestion,
                    updatedPendingContext = SuruPendingContext(
                        intent = SuruIntent.CREATE_STUDY_PLAN,
                        subject = subject,
                        chapters = emptyList(),
                        deadlineDays = deadlineDays ?: 7,
                        dailyAvailableMinutes = dailyMins,
                        preferredWindow = window,
                        startHour = parsedTime?.first ?: window?.defaultHour,
                        startMinute = parsedTime?.second ?: window?.defaultMinute,
                        missingSlot = "CHAPTERS"
                    ),
                    isOfflineEngine = true
                )
            }

            if (dailyMins == null) {
                val parsedTime = extractTimeOfDay(lower)
                val question = if (isHindiOrHinglish(lower)) {
                    "Got it (${chapters.joinToString(", ")}). Daily kitna time available hai?"
                } else {
                    "Got it (${chapters.joinToString(", ")}). How much time can you study daily?"
                }
                return SuruStructuredAction(
                    intent = SuruIntent.ASK_CLARIFICATION,
                    reply = question,
                    updatedPendingContext = SuruPendingContext(
                        intent = SuruIntent.CREATE_STUDY_PLAN,
                        subject = subject,
                        chapters = chapters,
                        deadlineDays = deadlineDays ?: 5,
                        dailyAvailableMinutes = null,
                        preferredWindow = window,
                        startHour = parsedTime?.first ?: window?.defaultHour,
                        startMinute = parsedTime?.second ?: window?.defaultMinute,
                        missingSlot = "DAILY_TIME"
                    ),
                    isOfflineEngine = true
                )
            }

            val resolvedWindow = window ?: SuruTimeWindow.EVENING
            val parsedTime = extractTimeOfDay(lower)
            val draft = SuruStudyPlanner.buildStudyPlanDraft(
                subject = subject,
                chapters = chapters,
                deadlineDays = deadlineDays ?: 5,
                dailyAvailableMinutes = dailyMins,
                preferredWindow = resolvedWindow,
                startHour = parsedTime?.first ?: resolvedWindow.defaultHour,
                startMinute = parsedTime?.second ?: resolvedWindow.defaultMinute,
                existingEvents = events,
                todayEpochDay = todayEpochDay
            )
            return SuruStructuredAction(
                intent = SuruIntent.CREATE_STUDY_PLAN,
                reply = "Here's your ${draft.deadlineDays}-day ${draft.subject} study plan for ${draft.chapters.joinToString(", ")}. You can adjust daily workload or confirm to add it to Tasks & Calendar.",
                subject = draft.subject,
                chapters = draft.chapters,
                studyPlanDraft = draft,
                requiresConfirmation = true,
                confirmationTitle = "Confirm ${draft.subject} Study Plan",
                confirmationMessage = draft.summaryNote,
                isOfflineEngine = true
            )
        }

        // 7. Weekly Planning & Exam Next Week ("Plan my entire week" / "I have an examination next week")
        if (lower.contains("plan my week") || lower.contains("plan my entire week") || lower.contains("weekly plan") || lower.contains("plan this week")) {
            return SuruStructuredAction(
                intent = SuruIntent.PLAN_WEEK,
                reply = "Planned your priority tasks into conflict-free study blocks across the upcoming week.",
                isOfflineEngine = true
            )
        }
        if ((lower.contains("exam") || lower.contains("examination") || lower.contains("test")) &&
            (lower.contains("next week") || lower.contains("in a week") || lower.contains("agle hafte"))
        ) {
            val subject = extractSubject(raw, lower) ?: inferSubjectFromMemories(memories) ?: "Exam Prep"
            val chapters = extractChaptersList(raw)
            if (chapters.isEmpty()) {
                return SuruStructuredAction(
                    intent = SuruIntent.ASK_CLARIFICATION,
                    reply = "Let's get you ready for your $subject exam next week! Which chapters or topics are on the syllabus?",
                    updatedPendingContext = SuruPendingContext(
                        intent = SuruIntent.CREATE_STUDY_PLAN,
                        subject = subject,
                        chapters = emptyList(),
                        deadlineDays = 7,
                        dailyAvailableMinutes = extractDailyAvailableMinutes(lower),
                        preferredWindow = extractPreferredWindow(lower),
                        missingSlot = "CHAPTERS"
                    ),
                    isOfflineEngine = true
                )
            }
        }

        // 8. Log Completed Study Progress ("I completed two hours of Chemistry")
        extractLoggedStudyProgress(raw, lower)?.let { (subjectLabel, mins) ->
            return SuruStructuredAction(
                intent = SuruIntent.LOG_STUDY_PROGRESS,
                reply = "Logged ${DateTimeUtils.formatMinutesCompact(mins)} of focused $subjectLabel progress! (+${(mins / 2).coerceIn(10, 120)} XP)",
                subject = subjectLabel,
                title = subjectLabel,
                loggedMinutes = mins,
                isOfflineEngine = true
            )
        }

        // 9. Bulk Move Today's Tasks to Tomorrow ("Move today's tasks to tomorrow")
        if ((lower.contains("move") || lower.contains("shift") || lower.contains("push")) &&
            (lower.contains("today's task") || lower.contains("todays task") || lower.contains("all today") || lower.contains("aaj ke task")) &&
            (lower.contains("tomorrow") || lower.contains("kal") || lower.contains("next day"))
        ) {
            return SuruStructuredAction(
                intent = SuruIntent.RESCHEDULE_TASK,
                reply = "Moved today's open tasks to tomorrow and refreshed their reminder schedule.",
                dateOffsetDays = 1,
                rescheduleAllToday = true,
                isOfflineEngine = true
            )
        }

        // 10. Free Study Time / What to focus on today / Progress Summary / Overdue Check
        if (lower.contains("free time") || lower.contains("free slot") || lower.contains("available study time") || lower.contains("when can i study")) {
            return SuruStructuredAction(
                intent = SuruIntent.FIND_FREE_SLOTS,
                reply = "Checked your calendar for open study windows.",
                isOfflineEngine = true
            )
        }
        if (lower.contains("what should i focus") || lower.contains("focus on today") || lower.contains("what next")) {
            val overdueCount = tasks.count { !it.isCompleted && it.deadlineEpochDay < todayEpochDay }
            val top = tasks.filter { !it.isCompleted }
                .sortedWith(compareByDescending<TaskEntity> { it.deadlineEpochDay < todayEpochDay }.thenByDescending { it.priority.ordinal }.thenBy { it.deadlineEpochDay })
                .take(3)
            val replyText = if (top.isEmpty()) {
                "Your task queue is completely clear today! Start a 25-minute deep reading sprint or review your goals."
            } else {
                buildString {
                    append("Top focus right now")
                    if (overdueCount > 0) append(" ($overdueCount overdue)")
                    appendLine(":")
                    top.forEachIndexed { idx, t ->
                        val tag = if (t.deadlineEpochDay < todayEpochDay) "Overdue" else t.priority.label
                        appendLine("${idx + 1}. ${t.title} [$tag]")
                    }
                    append("Start with #1 for a focused 25-minute block!")
                }
            }
            return SuruStructuredAction(
                intent = SuruIntent.OVERDUE_CHECK,
                reply = replyText,
                isOfflineEngine = true
            )
        }
        if (lower.contains("summarize my progress") || lower.contains("progress this month") || lower.contains("my progress") || lower.contains("weekly report")) {
            return SuruStructuredAction(
                intent = SuruIntent.PROGRESS_SUMMARY,
                reply = "Prepared your progress summary.",
                isOfflineEngine = true
            )
        }

        // 11. Rename / Edit Task ("rename task X to Y" / "edit task X to Y")
        extractEditTaskIntent(raw, lower, tasks)?.let { return it }

        // 12. Task Completion ("mark Physics done" / "complete ... " / "ho gaya")
        if (looksLikeCompleteTask(lower)) {
            val matched = findMatchingTask(raw, lower, tasks.filter { !it.isCompleted })
            if (matched != null) {
                return SuruStructuredAction(
                    intent = SuruIntent.COMPLETE_TASK,
                    reply = "Marked \"${matched.title}\" as completed! (+${matched.xpReward} XP)",
                    title = matched.title,
                    targetTaskId = matched.id,
                    isOfflineEngine = true
                )
            }
        }

        // 13. Task Deletion (requires explicit confirmation!)
        if (looksLikeDeleteTask(lower)) {
            val matched = findMatchingTask(raw, lower, tasks)
            if (matched != null) {
                return SuruStructuredAction(
                    intent = SuruIntent.DELETE_TASK,
                    reply = "Should I remove the task \"${matched.title}\"? Please confirm below.",
                    title = matched.title,
                    targetTaskId = matched.id,
                    requiresConfirmation = true,
                    confirmationTitle = "Delete Task?",
                    confirmationMessage = "Remove \"${matched.title}\" from your Tasks?",
                    isOfflineEngine = true
                )
            }
        }

        // 14. Break down task into subtasks
        if (lower.contains("break down") || lower.contains("subtask") || lower.contains("split task")) {
            val matched = findMatchingTask(raw, lower, tasks.filter { !it.isCompleted })
            val topic = matched?.title ?: extractCleanActionTitle(raw)
            val subtasks = TaskIntelligence.buildSubtasks(topic)
            return SuruStructuredAction(
                intent = SuruIntent.BREAKDOWN_TASK,
                reply = "Broke down \"$topic\" into ${subtasks.size} actionable subtasks in your Tasks.",
                title = topic,
                targetTaskId = matched?.id,
                subtasks = subtasks,
                category = matched?.category ?: inferCategory(lower),
                priority = matched?.priority ?: TaskPriority.MEDIUM,
                isOfflineEngine = true
            )
        }

        // 15. Event OR Task Rescheduling ("move ... to 6 PM", "reschedule ... to tomorrow")
        if (looksLikeRescheduleEvent(lower)) {
            val matchedEvent = findMatchingEvent(raw, lower, events)
            val newOffset = extractDateOffset(lower)
            val parsedTime = extractTimeOfDay(lower)
            if (matchedEvent != null && parsedTime != null) {
                val startMin = parsedTime.first * 60 + parsedTime.second
                val targetDay = todayEpochDay + newOffset
                val overlaps = CalendarIntelligence.findOverlaps(targetDay, startMin, matchedEvent.durationMinutes, events.filter { it.id != matchedEvent.id })
                return SuruStructuredAction(
                    intent = SuruIntent.RESCHEDULE_EVENT,
                    reply = if (overlaps.isEmpty()) {
                        "Moved \"${matchedEvent.title}\" to ${formatDateLabel(newOffset, todayEpochDay)} at ${DateTimeUtils.formatTimeOfDay(startMin)}."
                    } else {
                        "\"${matchedEvent.title}\" at ${DateTimeUtils.formatTimeOfDay(startMin)} overlaps \"${overlaps.first().title}\". Confirm if you still want this slot."
                    },
                    title = matchedEvent.title,
                    targetEventId = matchedEvent.id,
                    dateOffsetDays = newOffset,
                    startHour = parsedTime.first,
                    startMinute = parsedTime.second,
                    durationMinutes = matchedEvent.durationMinutes,
                    requiresConfirmation = overlaps.isNotEmpty(),
                    confirmationTitle = if (overlaps.isNotEmpty()) "Schedule Conflict Detected" else null,
                    confirmationMessage = overlaps.firstOrNull()?.let { "Overlaps with \"${it.title}\"" },
                    isOfflineEngine = true
                )
            }
            val matchedTask = findMatchingTask(raw, lower, tasks.filter { !it.isCompleted })
            if (matchedTask != null && newOffset > 0) {
                return SuruStructuredAction(
                    intent = SuruIntent.RESCHEDULE_TASK,
                    reply = "Moved \"${matchedTask.title}\" to ${formatDateLabel(newOffset, todayEpochDay)}.",
                    title = matchedTask.title,
                    targetTaskId = matchedTask.id,
                    dateOffsetDays = newOffset,
                    isOfflineEngine = true
                )
            }
        }

        // 16. Wellness Check / Support (Stress, Fatigue, Sleep, Water, Mood)
        if (looksLikeWellnessIntent(lower)) {
            val wellnessReply = buildWellnessResponse(lower, context)
            return SuruStructuredAction(
                intent = SuruIntent.WELLNESS_CHECK,
                reply = wellnessReply,
                isOfflineEngine = true
            )
        }

        // 12. Study Help ("explain ...", "summarize ...", "quiz / practice questions ...", "flashcards ...")
        if (looksLikeStudyHelpIntent(lower)) {
            val topic = extractStudyTopic(raw)
            val mode = when {
                lower.contains("exam") -> SuruStudyHelpMode.EXAM_ORIENTED
                lower.contains("question") || lower.contains("quiz") || lower.contains("mcq") || lower.contains("practice") -> SuruStudyHelpMode.QUESTION_PRACTICE
                lower.contains("quick") || lower.contains("revise") || lower.contains("summary") || lower.contains("flashcard") -> SuruStudyHelpMode.QUICK_REVISION
                lower.contains("detail") || lower.contains("deep") -> SuruStudyHelpMode.DETAILED
                else -> SuruStudyHelpMode.BEGINNER
            }
            return SuruStructuredAction(
                intent = SuruIntent.STUDY_HELP,
                reply = "Here's a ${mode.label} study guide on $topic (open Study Lab for flashcards & checklists too).",
                studyHelpTopic = topic,
                studyHelpMode = mode,
                isOfflineEngine = true
            )
        }

        // 13. Hinglish / Hindi Task or Calendar Event commands ("kal shaam 6 baje Physics schedule kar do", "task add karo ...")
        val dateOffset = extractDateOffset(lower)
        val parsedTime = extractTimeOfDay(lower)
        val duration = extractDurationMinutes(lower)
        val inferredCategory = inferCategory(lower)
        val inferredPriority = inferPriority(lower)

        val looksLikeSchedule = listOf(
            "schedule", "calendar", "meeting", "class", "appointment", "kar do", "fix kar", "rakh do"
        ).any { lower.contains(it) } || parsedTime != null

        val looksLikeTaskVerb = listOf(
            "add task", "create task", "new task", "task:", "todo", "to my tasks", "task add", "remind me to"
        ).any { lower.contains(it) }

        if (looksLikeSchedule && !looksLikeTaskVerb && !lower.startsWith("add ")) {
            val title = extractCleanActionTitle(raw)
            if (parsedTime == null) {
                return SuruStructuredAction(
                    intent = SuruIntent.ASK_CLARIFICATION,
                    reply = "What time on ${formatDateLabel(dateOffset, todayEpochDay)} should I schedule \"$title\"?",
                    updatedPendingContext = SuruPendingContext(
                        intent = SuruIntent.CREATE_EVENT,
                        title = title,
                        dateOffsetDays = dateOffset,
                        durationMinutes = duration,
                        category = inferredCategory,
                        priority = inferredPriority,
                        missingSlot = "EVENT_TIME"
                    ),
                    isOfflineEngine = true
                )
            }
            val startMin = parsedTime.first * 60 + parsedTime.second
            val targetDay = todayEpochDay + dateOffset
            val overlaps = CalendarIntelligence.findOverlaps(targetDay, startMin, duration, events)
            if (overlaps.isNotEmpty()) {
                val alternatives = CalendarIntelligence.suggestFreeSlots(targetDay, duration, events)
                    .joinToString(", ") { DateTimeUtils.formatTimeOfDay(it) }
                return SuruStructuredAction(
                    intent = SuruIntent.CREATE_EVENT,
                    reply = "\"$title\" at ${DateTimeUtils.formatTimeOfDay(startMin)} overlaps with \"${overlaps.first().title}\". Confirm anyway or pick another slot" +
                        if (alternatives.isNotBlank()) " (free: $alternatives)." else ".",
                    title = title,
                    dateOffsetDays = dateOffset,
                    startHour = parsedTime.first,
                    startMinute = parsedTime.second,
                    durationMinutes = duration,
                    category = inferredCategory,
                    priority = inferredPriority,
                    requiresConfirmation = true,
                    confirmationTitle = "Calendar Conflict Detected",
                    confirmationMessage = "Overlaps with \"${overlaps.first().title}\" at ${DateTimeUtils.formatTimeOfDay(overlaps.first().startMinuteOfDay)}.",
                    isOfflineEngine = true
                )
            }
            return SuruStructuredAction(
                intent = SuruIntent.CREATE_EVENT,
                reply = "Scheduled \"$title\" for ${formatDateLabel(dateOffset, todayEpochDay)} at ${DateTimeUtils.formatTimeOfDay(startMin)} (${duration}m).",
                title = title,
                dateOffsetDays = dateOffset,
                startHour = parsedTime.first,
                startMinute = parsedTime.second,
                durationMinutes = duration,
                category = inferredCategory,
                priority = inferredPriority,
                isOfflineEngine = true
            )
        }

        if (looksLikeTaskVerb || listOf("add ", "karna hai", "complete ", "finish ", "write ", "revise ").any { lower.contains(it) }) {
            val title = extractCleanActionTitle(raw)
            val duplicate = TaskIntelligence.findDuplicate(title, tasks.filter { !it.isCompleted })
            if (duplicate != null) {
                return SuruStructuredAction(
                    intent = SuruIntent.CREATE_TASK,
                    reply = "You already have a similar pending task: \"${duplicate.title}\". Confirm if you still want to add \"$title\".",
                    title = title,
                    dateOffsetDays = dateOffset,
                    category = inferredCategory,
                    priority = inferredPriority,
                    requiresConfirmation = true,
                    confirmationTitle = "Possible Duplicate Task",
                    confirmationMessage = "\"${duplicate.title}\" is already in your pending tasks.",
                    isOfflineEngine = true
                )
            }
            return SuruStructuredAction(
                intent = SuruIntent.CREATE_TASK,
                reply = "Added \"$title\" to Tasks (${inferredPriority.label} · due ${formatDateLabel(dateOffset, todayEpochDay)}).",
                title = title,
                dateOffsetDays = dateOffset,
                category = inferredCategory,
                priority = inferredPriority,
                isOfflineEngine = true
            )
        }

        return null
    }

    private fun handlePendingFollowUp(
        raw: String,
        lower: String,
        pending: SuruPendingContext,
        events: List<CalendarEventEntity>,
        memories: List<SuruMemoryEntity>,
        todayEpochDay: Long
    ): SuruStructuredAction? {
        if (lower in listOf("cancel", "never mind", "nevermind", "chhodo", "rehne do", "stop")) {
            return SuruStructuredAction(
                intent = SuruIntent.CHAT,
                reply = "No problem, cancelled that draft. Let me know what you'd like to do next!",
                updatedPendingContext = null,
                isOfflineEngine = true
            )
        }

        return when (pending.intent) {
            SuruIntent.CREATE_STUDY_PLAN -> {
                var next = pending
                // Update time/window if user corrected ("actually make it evening")
                extractPreferredWindow(lower)?.let { w ->
                    next = next.copy(preferredWindow = w, startHour = w.defaultHour, startMinute = w.defaultMinute)
                }
                extractTimeOfDay(lower)?.let { (h, m) ->
                    next = next.copy(startHour = h, startMinute = m)
                }
                extractDeadlineDays(lower)?.let { d ->
                    next = next.copy(deadlineDays = d)
                }
                extractDailyAvailableMinutes(lower)?.let { mins ->
                    next = next.copy(dailyAvailableMinutes = mins)
                }
                if (next.chapters.isEmpty()) {
                    val chapters = extractChaptersList(raw).ifEmpty {
                        splitRawChapterReply(raw)
                    }
                    if (chapters.isNotEmpty()) {
                        next = next.copy(chapters = chapters)
                    }
                }

                if (next.chapters.isEmpty()) {
                    return SuruStructuredAction(
                        intent = SuruIntent.ASK_CLARIFICATION,
                        reply = if (isHindiOrHinglish(lower)) "Kaun-kaunse chapters include karne hain?" else "Which chapters should I include?",
                        updatedPendingContext = next.copy(missingSlot = "CHAPTERS"),
                        isOfflineEngine = true
                    )
                }
                if (next.dailyAvailableMinutes == null) {
                    return SuruStructuredAction(
                        intent = SuruIntent.ASK_CLARIFICATION,
                        reply = if (isHindiOrHinglish(lower)) "Got it. Daily kitna time available hai?" else "Got it. How much daily time do you have available?",
                        updatedPendingContext = next.copy(missingSlot = "DAILY_TIME"),
                        isOfflineEngine = true
                    )
                }

                val window = next.preferredWindow ?: extractPreferredWindow(
                    memories.firstOrNull { it.isEnabled && it.category == "TIME_PREF" }?.valueText?.lowercase().orEmpty()
                ) ?: SuruTimeWindow.EVENING

                val draft = SuruStudyPlanner.buildStudyPlanDraft(
                    subject = next.subject.ifBlank { "Study" },
                    chapters = next.chapters,
                    deadlineDays = next.deadlineDays ?: 5,
                    dailyAvailableMinutes = next.dailyAvailableMinutes,
                    preferredWindow = window,
                    startHour = next.startHour ?: window.defaultHour,
                    startMinute = next.startMinute ?: window.defaultMinute,
                    existingEvents = events,
                    todayEpochDay = todayEpochDay
                )
                SuruStructuredAction(
                    intent = SuruIntent.CREATE_STUDY_PLAN,
                    reply = "Ready! Built a ${draft.deadlineDays}-day plan for ${draft.chapters.joinToString(", ")} (${DateTimeUtils.formatMinutesCompact(draft.dailyAvailableMinutes)}/day in the ${draft.preferredWindow.label.lowercase()}). Review & confirm below.",
                    subject = draft.subject,
                    chapters = draft.chapters,
                    studyPlanDraft = draft,
                    updatedPendingContext = null,
                    requiresConfirmation = true,
                    confirmationTitle = "Confirm ${draft.subject} Study Plan",
                    confirmationMessage = draft.summaryNote,
                    isOfflineEngine = true
                )
            }

            SuruIntent.CREATE_EVENT -> {
                val parsedTime = extractTimeOfDay(lower) ?: extractPreferredWindow(lower)?.let { it.defaultHour to it.defaultMinute }
                val dateOffset = if (looksLikeDateReference(lower)) extractDateOffset(lower) else (pending.dateOffsetDays ?: 0)
                if (parsedTime == null) return null

                val startMin = parsedTime.first * 60 + parsedTime.second
                val dur = extractDurationMinutesOrNull(lower) ?: (pending.durationMinutes ?: 60)
                val targetDay = todayEpochDay + dateOffset
                val overlaps = CalendarIntelligence.findOverlaps(targetDay, startMin, dur, events)

                SuruStructuredAction(
                    intent = SuruIntent.CREATE_EVENT,
                    reply = if (overlaps.isEmpty()) {
                        "Scheduled \"${pending.title}\" for ${formatDateLabel(dateOffset, todayEpochDay)} at ${DateTimeUtils.formatTimeOfDay(startMin)}."
                    } else {
                        "\"${pending.title}\" at ${DateTimeUtils.formatTimeOfDay(startMin)} conflicts with \"${overlaps.first().title}\". Confirm if you still want to add it."
                    },
                    title = pending.title,
                    dateOffsetDays = dateOffset,
                    startHour = parsedTime.first,
                    startMinute = parsedTime.second,
                    durationMinutes = dur,
                    category = pending.category,
                    priority = pending.priority,
                    updatedPendingContext = null,
                    requiresConfirmation = overlaps.isNotEmpty(),
                    confirmationTitle = if (overlaps.isNotEmpty()) "Calendar Conflict" else null,
                    confirmationMessage = overlaps.firstOrNull()?.let { "Overlaps with \"${it.title}\"" },
                    isOfflineEngine = true
                )
            }

            else -> null
        }
    }

    private fun stripWakeWords(input: String): String {
        return input
            .replace(Regex("^(?i)(?:hey|hi|hello|arey|oye)?\\s*suru\\s*[,:\\-]?\\s*"), "")
            .trim()
    }

    private fun looksLikeCorrection(lower: String): Boolean {
        return listOf("actually", "make it", "change it", "shift to", "nahi ", "instead", "badal").any { lower.contains(it) }
    }

    private fun looksLikeMemorySave(lower: String): Boolean {
        return listOf("remember that", "remember i ", "yaad rakh", "save preference", "note that i ").any { lower.contains(it) }
    }

    private fun looksLikeBacklogIntent(lower: String): Boolean {
        return listOf("backlog", "overdue", "pending tasks", "catch up", "reschedule missed", "clear pending").any { lower.contains(it) }
    }

    private fun looksLikeStudyPlanIntent(lower: String): Boolean {
        val hasStudyUnit = listOf("chapter", "chapters", "syllabus", "unit", "units", "study plan", "revision plan", "exam plan")
            .any { lower.contains(it) }
        val hasDurationOrPlan = listOf("complete", "finish", "cover", "karne", "padhna", "plan", "days", "din", "week")
            .any { lower.contains(it) }
        return hasStudyUnit && hasDurationOrPlan
    }

    private fun looksLikeCompleteTask(lower: String): Boolean {
        return listOf("mark ", "complete ", "finished ", "done with", "ho gaya", "check off").any { lower.contains(it) } &&
            !looksLikeStudyPlanIntent(lower)
    }

    private fun looksLikeDeleteTask(lower: String): Boolean {
        return listOf("delete ", "remove ", "hatao", "hata do").any { lower.contains(it) }
    }

    private fun looksLikeRescheduleEvent(lower: String): Boolean {
        return listOf("reschedule ", "move ", "shift ", "postpone ").any { lower.contains(it) } &&
            !lower.contains("backlog")
    }

    private fun looksLikeWellnessIntent(lower: String): Boolean {
        return listOf(
            "stress", "anxious", "tired", "exhausted", "overwhelmed", "sleep", "hydrate",
            "water", "paani", "thak", "break", "breathe", "mood", "burnout", "tension"
        ).any { lower.contains(it) } && !lower.startsWith("add habit") && !lower.startsWith("schedule ")
    }

    private fun looksLikeStudyHelpIntent(lower: String): Boolean {
        return listOf(
            "explain ", "summarize ", "summary of", "practice question", "flashcard",
            "quiz me", "revision checklist", "mistake", "samjhao", "exam tip"
        ).any { lower.contains(it) }
    }

    private fun isHindiOrHinglish(lower: String): Boolean {
        return listOf("mujhe", "kaunse", "kitna", "din", "aur", "karne", "hai", "hain", "shaam", "subah", "kal", "padhna", "ghante", "chahiye", "karo")
            .any { lower.contains(it) }
    }

    private fun extractSubject(raw: String, lower: String): String? {
        val knownSubjects = listOf(
            "Physics", "Chemistry", "Biology", "Mathematics", "Maths", "Math",
            "History", "Geography", "Economics", "English", "Computer Science", "Coding", "DSA"
        )
        knownSubjects.firstOrNull { lower.contains(it.lowercase()) }?.let {
            return if (it == "Maths") "Math" else it
        }
        Regex("""(?i)(?:of|for|mein)\s+([A-Za-z][A-Za-z ]{2,20})\s+(?:chapters?|ke|syllabus)""")
            .find(raw)?.groupValues?.getOrNull(1)?.trim()?.let { return it }
        return null
    }

    private fun inferSubjectFromMemories(memories: List<SuruMemoryEntity>): String? {
        return memories.firstOrNull { it.isEnabled && it.category == "STUDY_PREF" }?.valueText?.take(24)
    }

    private fun extractExpectedChapterCount(lower: String): Int? {
        Regex("""(\d+)\s+(?:chapters?|units?)""").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            return it.coerceIn(1, 20)
        }
        numberWords.forEach { (word, num) ->
            if (num <= 10 && Regex("""\b$word\s+(?:chapters?|units?)\b""").containsMatchIn(lower)) {
                return num
            }
        }
        return null
    }

    private fun extractChaptersList(raw: String): List<String> {
        val colonMatch = Regex("""(?i)chapters?\s*[:\-]\s*(.+)$""").find(raw)
        if (colonMatch != null) {
            return splitRawChapterReply(colonMatch.groupValues[1])
        }
        val namedMatch = Regex("""(?i)(?:like|namely|including|hain|are)\s+(.+?)(?:\s+in\s+\d+|\s+for\s+\d+|$)""").find(raw)
        if (namedMatch != null) {
            return splitRawChapterReply(namedMatch.groupValues[1])
        }
        return emptyList()
    }

    private fun splitRawChapterReply(raw: String): List<String> {
        val cleaned = raw
            .replace(Regex("(?i)^(?:chapters?\\s*(?:are|:)?\\s*)"), "")
            .trimEnd('.', '!', '?')
        return cleaned
            .split(Regex("""\s*(?:,|\baur\b|\band\b|&|;)\s*""", RegexOption.IGNORE_CASE))
            .map { it.trim().trim('"', '\'') }
            .filter { it.length >= 2 && !it.lowercase().matches(Regex("^(?:complete|karne|hain|please)$")) }
            .map { part -> part.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.US) else c.toString() } }
    }

    private fun extractDeadlineDays(lower: String): Int? {
        Regex("""\b(\d{1,2})\s*(?:days?|din|dino)\b""").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            return it.coerceIn(1, 60)
        }
        numberWords.forEach { (word, num) ->
            if (num in 1..14 && Regex("""\b$word\s+(?:days?|din|dino)\b""").containsMatchIn(lower)) {
                return num
            }
        }
        if (lower.contains("next week") || lower.contains("ek week") || lower.contains("one week")) return 7
        if (lower.contains("two weeks") || lower.contains("2 weeks")) return 14
        return null
    }

    private fun extractDailyAvailableMinutes(lower: String): Int? {
        Regex("""\b(\d+(?:\.\d+)?)\s*(?:hr|hrs|hour|hours|h|ghante|ghanta)\b""").find(lower)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let { hrs ->
            return (hrs * 60).toInt().coerceIn(20, 480)
        }
        Regex("""\b(\d{2,3})\s*(?:min|mins|minute|minutes|m)\b""").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { mins ->
            return mins.coerceIn(15, 480)
        }
        numberWords.forEach { (word, num) ->
            if (Regex("""\b$word\s+(?:hr|hrs|hour|hours|ghante|ghanta)\b""").containsMatchIn(lower)) {
                return (if (num == 30) 30 else num * 60).coerceIn(20, 480)
            }
        }
        return null
    }

    private fun extractPreferredWindow(lower: String): SuruTimeWindow? {
        return when {
            lower.contains("morning") || lower.contains("subah") -> SuruTimeWindow.MORNING
            lower.contains("afternoon") || lower.contains("dopahar") -> SuruTimeWindow.AFTERNOON
            lower.contains("evening") || lower.contains("shaam") || lower.contains("sham") -> SuruTimeWindow.EVENING
            lower.contains("night") || lower.contains("raat") -> SuruTimeWindow.NIGHT
            else -> null
        }
    }

    private fun looksLikeDateReference(lower: String): Boolean {
        return listOf("today", "aaj", "tomorrow", "tmrw", "kal", "parso", "day after", "next week").any { lower.contains(it) } ||
            Regex("""\bin\s+\d+\s+days?\b""").containsMatchIn(lower)
    }

    private fun extractDateOffset(lower: String): Int {
        return when {
            lower.contains("day after tomorrow") || lower.contains("parso") -> 2
            lower.contains("tomorrow") || lower.contains("tmrw") || Regex("""\bkal\b""").containsMatchIn(lower) -> 1
            lower.contains("next week") -> 7
            Regex("""\bin\s+(\d+)\s+days?\b""").find(lower) != null -> {
                (Regex("""\bin\s+(\d+)\s+days?\b""").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 365)
            }
            else -> 0
        }
    }

    private fun extractTimeOfDay(lower: String): Pair<Int, Int>? {
        Regex("""\b(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""").find(lower)?.let { match ->
            var hour = match.groupValues[1].toIntOrNull() ?: return@let
            val minute = (match.groupValues[2].toIntOrNull() ?: 0).coerceIn(0, 59)
            val mer = match.groupValues[3]
            if (hour in 1..12) {
                if (mer == "pm" && hour != 12) hour += 12
                if (mer == "am" && hour == 12) hour = 0
                return hour to minute
            }
        }
        Regex("""\b(?:at\s+)?([01]?\d|2[0-3]):([0-5]\d)\b""").find(lower)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@let
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            return hour to minute
        }
        Regex("""\b(\d{1,2})\s*baje\b""").find(lower)?.let { match ->
            var hour = match.groupValues[1].toIntOrNull() ?: return@let
            if (hour in 1..11 && (lower.contains("shaam") || lower.contains("sham") || lower.contains("evening") || lower.contains("raat") || lower.contains("pm"))) {
                hour += 12
            }
            return hour.coerceIn(0, 23) to 0
        }
        return extractPreferredWindow(lower)?.let { it.defaultHour to it.defaultMinute }
    }

    private fun extractDurationMinutes(lower: String): Int = extractDurationMinutesOrNull(lower) ?: 60

    private fun extractDurationMinutesOrNull(lower: String): Int? {
        Regex("""\b(\d{1,3})\s*(?:min|mins|minute|minutes|m)\b""").find(lower)?.let {
            return (it.groupValues[1].toIntOrNull() ?: 60).coerceIn(15, 240)
        }
        Regex("""\b(\d)\s*(?:hr|hrs|hour|hours|h|ghante|ghanta)\b""").find(lower)?.let {
            return ((it.groupValues[1].toIntOrNull() ?: 1) * 60).coerceIn(15, 240)
        }
        return null
    }

    private fun inferCategory(lower: String): TaskCategory {
        return when {
            listOf("physics", "chem", "bio", "math", "study", "exam", "read", "chapter", "notes", "revision").any { lower.contains(it) } -> TaskCategory.STUDY
            listOf("code", "build", "app", "bug", "kotlin", "api", "debug").any { lower.contains(it) } -> TaskCategory.CODE
            listOf("gym", "run", "walk", "workout", "yoga", "water", "sleep", "health", "meditate").any { lower.contains(it) } -> TaskCategory.HEALTH
            listOf("buy", "clean", "call", "family", "home", "personal").any { lower.contains(it) } -> TaskCategory.PERSONAL
            else -> TaskCategory.WORK
        }
    }

    private fun inferPriority(lower: String): TaskPriority {
        return when {
            listOf("critical", "urgent", "asap", "immediately").any { lower.contains(it) } -> TaskPriority.CRITICAL
            listOf("high", "important", "exam", "zaroori").any { lower.contains(it) } -> TaskPriority.HIGH
            listOf("low", "whenever", "someday").any { lower.contains(it) } -> TaskPriority.LOW
            else -> TaskPriority.MEDIUM
        }
    }

    private fun findMatchingTask(raw: String, lower: String, candidates: List<TaskEntity>): TaskEntity? {
        if (candidates.isEmpty()) return null
        val direct = candidates.firstOrNull { lower.contains(it.title.lowercase()) }
        if (direct != null) return direct
        val query = extractCleanActionTitle(raw).lowercase()
        return candidates.firstOrNull { t ->
            t.title.lowercase().contains(query) || query.contains(t.title.lowercase())
        }
    }

    private fun findMatchingEvent(raw: String, lower: String, events: List<CalendarEventEntity>): CalendarEventEntity? {
        if (events.isEmpty()) return null
        val direct = events.firstOrNull { lower.contains(it.title.lowercase()) }
        if (direct != null) return direct
        val query = extractCleanActionTitle(raw).lowercase()
        return events.firstOrNull { e ->
            e.title.lowercase().contains(query) || query.contains(e.title.lowercase())
        }
    }

    private fun extractStudyTopic(raw: String): String {
        val cleaned = raw
            .replace(Regex("(?i)^(?:please\\s+)?(?:explain|summarize|give\\s+(?:me\\s+)?(?:a\\s+)?(?:summary\\s+of|practice\\s+questions\\s+(?:on|for)|flashcards\\s+(?:on|for)|exam\\s+tips\\s+(?:on|for))|quiz\\s+me\\s+on|samjhao)\\s*"), "")
            .replace(Regex("(?i)\\s+(?:in\\s+(?:beginner|simple|detailed|exam|quick)\\s+mode|easily|step\\s+by\\s+step)\\s*$"), "")
            .trim()
            .trimEnd('.', '?', '!')
        return cleaned.ifBlank { "Core Study Concept" }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    }

    private fun extractCleanActionTitle(raw: String): String {
        var cleaned = raw.trim().trimEnd('.', '!', '?')
        listOf(
            Regex("""^(?i)(?:please\s+)?(?:add|create|make|schedule|book|plan|set|mark|complete|finish|delete|remove|move|reschedule|shift|break\s+down)\s+(?:a\s+)?(?:new\s+)?(?:task|habit|event|meeting|session|reminder)?\s*(?:called|named|:)?\s*"""),
            Regex("""^(?i)(?:remind\s+me\s+to|i\s+need\s+to|i\s+have\s+to)\s+""")
        ).forEach { cleaned = cleaned.replace(it, "") }

        listOf(
            Regex("""(?i)\s+(?:to\s+my\s+tasks?|into\s+subtasks?|as\s+done|as\s+completed|kar\s+do|karo|rakh\s+do|hata\s+do)\s*$"""),
            Regex("""(?i)\s+(?:at\s+)?\d{1,2}(?::\d{2})?\s*(?:am|pm|baje)?\s*$"""),
            Regex("""(?i)\s+(?:day\s+after\s+tomorrow|tomorrow|tmrw|today|kal|aaj|parso|morning|afternoon|evening|night|shaam|subah)\s*$""")
        ).forEach { cleaned = cleaned.replace(it, "") }

        val finalTitle = cleaned.trim().trim('"', '\'', ',', '.', '-', ':')
        return if (finalTitle.isNotBlank()) {
            finalTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        } else {
            raw.trim().take(40)
        }
    }

    private fun formatDateLabel(dateOffset: Int, todayEpochDay: Long): String = when (dateOffset) {
        0 -> "today"
        1 -> "tomorrow"
        else -> DateTimeUtils.formatFullDate(todayEpochDay + dateOffset)
    }

    private fun extractLoggedStudyProgress(raw: String, lower: String): Pair<String, Int>? {
        val verbs = listOf("completed", "studied", "logged", "did", "finished", "padha", "padhai")
        if (verbs.none { lower.contains(it) }) return null
        val mins = extractDailyAvailableMinutes(lower) ?: extractDurationMinutesOrNull(lower) ?: return null
        val subjectMatch = Regex("""(?i)\b(?:of|on|for|mein)\s+([A-Za-z][A-Za-z0-9 &]{1,28})""").find(raw)
        val subj = subjectMatch?.groupValues?.getOrNull(1)?.trim()?.trimEnd('.', '!', '?')
            ?: extractSubject(raw, lower)
            ?: "Study"
        return subj.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() } to mins
    }

    private fun extractEditTaskIntent(
        raw: String,
        lower: String,
        tasks: List<TaskEntity>
    ): SuruStructuredAction? {
        val renameMatch = Regex("""(?i)^(?:please\s+)?(?:rename|edit|update|change)\s+(?:the\s+)?task\s+["']?(.+?)["']?\s+to\s+["']?(.+?)["']?\s*$""").find(raw)
            ?: return null
        val oldQuery = renameMatch.groupValues[1].trim()
        val newTitle = renameMatch.groupValues[2].trim().trimEnd('.', '!')
        if (oldQuery.isBlank() || newTitle.isBlank()) return null
        val matched = tasks.firstOrNull {
            it.title.equals(oldQuery, ignoreCase = true) ||
                it.title.contains(oldQuery, ignoreCase = true)
        } ?: return null
        return SuruStructuredAction(
            intent = SuruIntent.UPDATE_TASK,
            reply = "Updated \"${matched.title}\" to \"$newTitle\".",
            title = matched.title,
            newTitle = newTitle,
            targetTaskId = matched.id,
            category = matched.category,
            priority = matched.priority,
            isOfflineEngine = true
        )
    }

    private fun buildWellnessResponse(lower: String, context: LifeContextSnapshot): String {
        return when {
            lower.contains("water") || lower.contains("hydrate") || lower.contains("paani") ->
                "Hydration check! Small sips every 45 minutes keep focus steady without feeling heavy. Tap +250ml on Home or Wellness anytime."
            lower.contains("sleep") || lower.contains("raat") ->
                "A calm wind-down routine (dimming screens 20–30 minutes before bed and noting tomorrow's #1 task) helps your mind rest easier."
            lower.contains("stress") || lower.contains("anxious") || lower.contains("overwhelmed") || lower.contains("tired") || lower.contains("thak") ->
                "Take a slow 4-second breath in and 6-second breath out. You don't have to finish everything at once — pick just one gentle 15-minute step or pause for water and rest."
            else ->
                "Wellness check for ${context.userName.ifBlank { "you" }}: Focus today is ${DateTimeUtils.formatMinutesCompact(context.focusMinutesToday)}. Pace yourself with short 10-minute breaks between deep sessions."
        }
    }
}
