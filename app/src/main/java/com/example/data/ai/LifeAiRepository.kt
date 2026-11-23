package com.example.data.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.local.TaskCategory
import com.example.data.local.TaskPriority
import com.example.util.DateTimeUtils
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

private const val TAG = "LifeAiRepository"

sealed class AiExecutionResult {
    data class Success(
        val reply: String,
        val actionType: String,
        val title: String? = null,
        val dateOffsetDays: Int = 0,
        val startHour: Int = 9,
        val startMinute: Int = 0,
        val durationMinutes: Int = 60,
        val category: TaskCategory = TaskCategory.STUDY,
        val priority: TaskPriority = TaskPriority.MEDIUM,
        val isOfflineEngine: Boolean = true
    ) : AiExecutionResult()

    data class Error(val message: String) : AiExecutionResult()
}

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

class LifeAiRepository(private val appContext: Context) {

    private val actionSchema = Schema.obj(
        properties = mapOf(
            "reply" to Schema.string(
                description = "Concise, helpful personal command center response for the user."
            ),
            "actionType" to Schema.enumeration(
                values = listOf("NONE", "CREATE_TASK", "CREATE_EVENT", "CREATE_HABIT", "ORGANIZE_TOMORROW"),
                description = "Use CREATE_TASK to add a task, CREATE_EVENT to schedule a calendar block, CREATE_HABIT to add a daily habit, ORGANIZE_TOMORROW to schedule top pending tasks for tomorrow, or NONE for analysis/advice."
            ),
            "title" to Schema.string(
                description = "Short title of the task, habit, or calendar event if creating one.",
                nullable = true
            ),
            "dateOffsetDays" to Schema.integer(
                description = "0 for today, 1 for tomorrow, 2 for two days out, etc."
            ),
            "startHour" to Schema.integer(
                description = "24-hour start hour (0-23) for calendar events."
            ),
            "startMinute" to Schema.integer(
                description = "Start minute (0-59) for calendar events."
            ),
            "durationMinutes" to Schema.integer(
                description = "Duration in minutes (15-240) for calendar events."
            ),
            "category" to Schema.enumeration(
                values = TaskCategory.entries.map { it.name },
                description = "Task, habit, or event category."
            ),
            "priority" to Schema.enumeration(
                values = TaskPriority.entries.map { it.name },
                description = "Task priority level."
            )
        ),
        optionalProperties = listOf("title")
    )

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = actionSchema
            }
        )
    }

    /**
     * Local-first execution:
     * 1) Deterministic local/on-device LIFE AI handles task creation, habit creation,
     *    calendar scheduling, daily planning, and productivity insights instantly at ₹0 cost.
     * 2) Only if the prompt is open-ended (unmatched locally) AND Firebase AI + network
     *    are configured/online, it optionally queries cloud Gemini with graceful local fallback.
     */
    suspend fun processPrompt(
        userPrompt: String,
        context: LifeContextSnapshot
    ): AiExecutionResult {
        val localMatch = LocalLifeAiEngine.tryMatchIntent(userPrompt, context)
        if (localMatch != null) {
            return localMatch
        }

        if (canAttemptCloudAi()) {
            val cloudResult = tryCloudGemini(userPrompt, context)
            if (cloudResult != null) return cloudResult
        }

        return LocalLifeAiEngine.process(userPrompt, context)
    }

    fun isCloudAiAvailable(): Boolean = canAttemptCloudAi()

    private fun canAttemptCloudAi(): Boolean {
        return try {
            if (FirebaseApp.getApps(appContext).isEmpty()) return false
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun tryCloudGemini(
        userPrompt: String,
        context: LifeContextSnapshot
    ): AiExecutionResult.Success? {
        val systemPrompt = buildString {
            appendLine("You are LIFE AI, the integrated personal operating system assistant inside LIFE OS.")
            appendLine("Tone: crisp, strategic, encouraging, zero fluff.")
            appendLine("User telemetry snapshot:")
            appendLine("- Name: ${context.userName} | Level ${context.level} (${context.totalXp} XP) | ${context.dailyStreak}-day streak")
            appendLine("- Score: ${context.productivityScore}% | Completed tasks: ${context.completedTasksCount} | Focus today: ${context.focusMinutesToday}m | Screen time today: ${context.screenTimeTodayMinutes}m")
            appendLine("- Pending tasks: ${context.pendingTasksSummary.ifBlank { "None" }}")
            appendLine("- Upcoming events: ${context.upcomingEventsSummary.ifBlank { "None" }}")
            appendLine()
            appendLine("User message: \"$userPrompt\"")
            appendLine("Respond with structured JSON. If the user asks to schedule something, choose CREATE_EVENT. If they ask to add a task, choose CREATE_TASK. If they ask to add a habit, choose CREATE_HABIT. If they ask to organize tomorrow based on pending tasks, choose ORGANIZE_TOMORROW. If they ask for productivity feedback or advice, choose NONE and cite concrete numbers from the snapshot.")
        }

        return try {
            val response = withTimeout(6_000L) {
                structuredModel.generateContent(systemPrompt)
            }
            val rawJson = response.text
            if (rawJson.isNullOrBlank()) return null

            val json = JSONObject(rawJson)
            val reply = json.optString("reply", "").ifBlank { return null }
            val actionType = json.optString("actionType", "NONE")
            val title = if (json.isNull("title")) null else json.optString("title").takeIf { it.isNotBlank() }
            val dateOffset = json.optInt("dateOffsetDays", 0).coerceIn(0, 365)
            val startHour = json.optInt("startHour", 9).coerceIn(0, 23)
            val startMinute = json.optInt("startMinute", 0).coerceIn(0, 59)
            val duration = json.optInt("durationMinutes", 60).coerceIn(15, 240)
            val category = TaskCategory.entries.firstOrNull {
                it.name == json.optString("category")
            } ?: TaskCategory.STUDY
            val priority = TaskPriority.entries.firstOrNull {
                it.name == json.optString("priority")
            } ?: TaskPriority.MEDIUM

            AiExecutionResult.Success(
                reply = reply,
                actionType = actionType,
                title = title,
                dateOffsetDays = dateOffset,
                startHour = startHour,
                startMinute = startMinute,
                durationMinutes = duration,
                category = category,
                priority = priority,
                isOfflineEngine = false
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "Cloud AI unavailable (${t.message}); using local LIFE AI engine.")
            null
        }
    }

    internal fun processLocally(
        userPrompt: String,
        context: LifeContextSnapshot
    ): AiExecutionResult.Success = LocalLifeAiEngine.process(userPrompt, context)
}

internal object LocalLifeAiEngine {

    /**
     * Deterministic, zero-cost, 100% offline natural-language parser & planner.
     * Supports task creation, habit creation, calendar scheduling, daily planning, and productivity insights.
     */
    fun process(
        userPrompt: String,
        context: LifeContextSnapshot
    ): AiExecutionResult.Success {
        return tryMatchIntent(userPrompt, context) ?: buildGuidanceFallback(context)
    }

    fun tryMatchIntent(
        userPrompt: String,
        context: LifeContextSnapshot
    ): AiExecutionResult.Success? {
        val raw = userPrompt.trim()
        val lower = raw.lowercase(Locale.US)
        val displayName = context.userName.ifBlank { "Operator" }

        // 1. Organize Tomorrow / Suggest Daily Schedule
        val wantsScheduleSuggestion = (lower.contains("organize") || lower.contains("plan") ||
            lower.contains("auto") || lower.contains("suggest") || lower.contains("build")) &&
            (lower.contains("tomorrow") || lower.contains("pending") ||
                lower.contains("my day") || lower.contains("today") || lower.contains("schedule") || lower.contains("routine"))

        if (wantsScheduleSuggestion && !hasExplicitEventTime(lower)) {
            val isTomorrow = lower.contains("tomorrow") || lower.contains("tmrw")
            val topPending = context.pendingTaskTitles.take(3)
            if (isTomorrow && topPending.isNotEmpty()) {
                val reply = buildString {
                    appendLine("Time-blocked plan for tomorrow, $displayName:")
                    topPending.forEachIndexed { index, taskTitle ->
                        val slot = DateTimeUtils.formatTimeOfDay(540 + index * 120)
                        appendLine("• $slot — $taskTitle (60m focus block)")
                    }
                    append("Added ${topPending.size} focus blocks directly to your Calendar.")
                }
                return AiExecutionResult.Success(
                    reply = reply,
                    actionType = "ORGANIZE_TOMORROW",
                    dateOffsetDays = 1,
                    isOfflineEngine = true
                )
            } else {
                val goal = context.dailyFocusGoalMinutes
                val reply = if (topPending.isEmpty()) {
                    buildString {
                        appendLine("Suggested focus blueprint for $displayName (Goal: ${DateTimeUtils.formatMinutesCompact(goal)}):")
                        appendLine("• 9:00 AM — Deep Work Sprint #1 (50m) + 10m break")
                        appendLine("• 11:30 AM — Skill Study / Priority Mission (45m)")
                        appendLine("• 5:30 PM — Daily Habit Check-in & Digital Wellness Review")
                        append("Your queue is currently empty — say \"Add <task> to my tasks\" or \"Schedule <event> at 9 AM\" to populate this blueprint.")
                    }
                } else {
                    buildString {
                        appendLine("Recommended ${if (isTomorrow) "tomorrow" else "today"} focus sequence for $displayName:")
                        topPending.forEachIndexed { index, title ->
                            val slot = DateTimeUtils.formatTimeOfDay(540 + index * 120)
                            appendLine("• $slot — $title (60m deep focus)")
                        }
                        append("Say \"Organize tomorrow based on my pending tasks\" to auto-block these onto your Calendar.")
                    }
                }
                return AiExecutionResult.Success(
                    reply = reply,
                    actionType = "NONE",
                    isOfflineEngine = true
                )
            }
        }

        // 2. Productivity Insights, Weekly Report, Focus Advice
        val wantsAnalysis = listOf(
            "productivity", "weekly", "report", "feedback", "analyze", "analysis",
            "insight", "insights", "summary", "progress", "how am i", "unfinished",
            "pending tasks", "score", "streak", "screen time", "wellness", "stats",
            "what should i focus"
        ).any { lower.contains(it) } &&
            !lower.startsWith("add ") &&
            !lower.startsWith("create ") &&
            !lower.startsWith("schedule ")

        if (wantsAnalysis) {
            val pendingCount = context.pendingTaskTitles.size
            val nextMission = context.pendingTaskTitles.firstOrNull()
            val focusCompact = DateTimeUtils.formatMinutesCompact(context.focusMinutesToday)
            val goalCompact = DateTimeUtils.formatMinutesCompact(context.dailyFocusGoalMinutes)
            val screenCompact = DateTimeUtils.formatMinutesCompact(context.screenTimeTodayMinutes)
            val hasAnyActivity = pendingCount > 0 || context.completedTasksCount > 0 ||
                context.focusMinutesToday > 0 || context.habitsTotal > 0 || context.screenTimeTodayMinutes > 0

            val reply = if (!hasAnyActivity) {
                buildString {
                    appendLine("$displayName's Command Insights (Lv.${context.level} · ${context.totalXp} XP):")
                    appendLine("• Clean slate active — Score: ${context.productivityScore}% | Streak: ${context.dailyStreak}d")
                    appendLine("• Focus Progress: 0m / $goalCompact target")
                    append("Quick start: Add your first mission (e.g., \"Add Finish Physics Notes tomorrow\") or start a 25m Focus Timer on Home to earn your first XP.")
                }
            } else {
                val wellnessTip = when {
                    context.screenTimeTodayMinutes == 0 -> "No screen usage logged yet today."
                    context.screenTimeTodayMinutes > 180 -> "Screen time ($screenCompact) is running high — run a 25m DND sprint."
                    else -> "Screen time ($screenCompact) is balanced."
                }
                buildString {
                    appendLine("$displayName's Command Insights (Lv.${context.level} · ${context.totalXp} XP):")
                    appendLine("• Productivity Score: ${context.productivityScore}% | Streak: ${context.dailyStreak}d")
                    appendLine("• Tasks: ${context.completedTasksCount} completed, $pendingCount pending")
                    appendLine("• Habits Today: ${context.habitsCompletedToday}/${context.habitsTotal} | Focus: $focusCompact / $goalCompact")
                    appendLine("• Wellness: $wellnessTip")
                    if (nextMission != null) {
                        append("Next target: \"$nextMission\". Completing it advances XP, streaks, and companion evolution.")
                    } else {
                        append("All queued tasks completed — great momentum!")
                    }
                }
            }
            return AiExecutionResult.Success(
                reply = reply,
                actionType = "NONE",
                isOfflineEngine = true
            )
        }

        // 3. Habit Creation Intent
        val looksLikeHabit = listOf(
            "add habit", "create habit", "new habit", "track habit", "daily habit"
        ).any { lower.contains(it) }

        if (looksLikeHabit) {
            val title = extractCleanTitle(raw, isEvent = false)
                .replace(Regex("(?i)^habit\\s*:?\\s*"), "")
                .trim()
                .ifBlank { "Daily Focus Habit" }
            val inferredCategory = inferCategory(lower)
            return AiExecutionResult.Success(
                reply = "Added daily habit \"$title\" (${inferredCategory.label} · +25 XP on completion).",
                actionType = "CREATE_HABIT",
                title = title,
                category = inferredCategory,
                isOfflineEngine = true
            )
        }

        // 4. Calendar Scheduling Intent
        val dateOffset = extractDateOffset(lower)
        val parsedTime = extractTimeOfDay(lower)
        val duration = extractDurationMinutes(lower)
        val inferredCategory = inferCategory(lower)
        val inferredPriority = inferPriority(lower)

        val looksLikeEvent = listOf(
            "schedule ", "book ", "calendar", "meeting", "appointment", "seminar", "class", "session"
        ).any { lower.contains(it) } || parsedTime != null

        val explicitTaskVerb = listOf(
            "add ", "create task", "new task", "todo", "to-do", "to my tasks", "remind me to", "finish ", "task:"
        ).any { lower.contains(it) }

        if (looksLikeEvent && !explicitTaskVerb) {
            val hour = parsedTime?.first ?: 9
            val minute = parsedTime?.second ?: 0
            val title = extractCleanTitle(raw, isEvent = true)
            val dateLabel = when (dateOffset) {
                0 -> "today"
                1 -> "tomorrow"
                else -> DateTimeUtils.formatFullDate(DateTimeUtils.todayEpochDay() + dateOffset)
            }
            val timeLabel = DateTimeUtils.formatTimeOfDay(hour * 60 + minute)
            return AiExecutionResult.Success(
                reply = "Scheduled \"$title\" for $dateLabel at $timeLabel (${duration}m · ${inferredCategory.label}).",
                actionType = "CREATE_EVENT",
                title = title,
                dateOffsetDays = dateOffset,
                startHour = hour,
                startMinute = minute,
                durationMinutes = duration,
                category = inferredCategory,
                priority = inferredPriority,
                isOfflineEngine = true
            )
        }

        // 5. Task Creation Intent
        val looksLikeTask = explicitTaskVerb || listOf(
            "need to ", "have to ", "must ", "study ", "prepare ", "complete ", "write ", "fix ", "review ", "buy "
        ).any { lower.startsWith(it) || lower.contains(" $it") }

        if (looksLikeTask) {
            val title = extractCleanTitle(raw, isEvent = false)
            val deadlineLabel = when (dateOffset) {
                0 -> "today"
                1 -> "tomorrow"
                else -> "in $dateOffset days"
            }
            return AiExecutionResult.Success(
                reply = "Added \"$title\" to Tasks (${inferredPriority.label} priority · +${inferredPriority.xpValue} XP, due $deadlineLabel).",
                actionType = "CREATE_TASK",
                title = title,
                dateOffsetDays = dateOffset,
                category = inferredCategory,
                priority = inferredPriority,
                isOfflineEngine = true
            )
        }

        return null
    }

    private fun buildGuidanceFallback(context: LifeContextSnapshot): AiExecutionResult.Success {
        val displayName = context.userName.ifBlank { "Operator" }
        val pendingPreview = context.pendingTaskTitles.take(2).joinToString(", ")
        val suggestion = if (pendingPreview.isNotBlank()) {
            "You currently have ${context.pendingTaskTitles.size} pending tasks (up next: $pendingPreview)."
        } else {
            "Your workspace is clean and ready."
        }
        return AiExecutionResult.Success(
            reply = "LIFE AI active offline ($displayName · Lv.${context.level}, Score ${context.productivityScore}%). $suggestion\n\nTry commands like:\n• \"Add Chemical Equilibrium to my tasks\"\n• \"Schedule Physics tomorrow at 8 AM\"\n• \"Add habit Read 20 pages\"\n• \"Suggest my daily plan\"\n• \"Show my productivity insights\"",
            actionType = "NONE",
            isOfflineEngine = true
        )
    }

    private fun hasExplicitEventTime(lower: String): Boolean = extractTimeOfDay(lower) != null

    private fun extractDateOffset(lower: String): Int {
        return when {
            lower.contains("day after tomorrow") -> 2
            lower.contains("tomorrow") || lower.contains("tmrw") -> 1
            lower.contains("next week") -> 7
            Regex("""\bin\s+(\d+)\s+days?\b""").find(lower) != null -> {
                val match = Regex("""\bin\s+(\d+)\s+days?\b""").find(lower)
                (match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 365)
            }
            else -> 0
        }
    }

    private fun extractTimeOfDay(lower: String): Pair<Int, Int>? {
        val amPmRegex = Regex("""\b(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""")
        amPmRegex.find(lower)?.let { match ->
            var hour = match.groupValues[1].toIntOrNull() ?: return@let
            val minute = (match.groupValues[2].toIntOrNull() ?: 0).coerceIn(0, 59)
            val meridiem = match.groupValues[3]
            if (hour in 1..12) {
                if (meridiem == "pm" && hour != 12) hour += 12
                if (meridiem == "am" && hour == 12) hour = 0
                return hour to minute
            }
        }

        val h24Regex = Regex("""\b(?:at\s+)?([01]?\d|2[0-3]):([0-5]\d)\b""")
        h24Regex.find(lower)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@let
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            return hour to minute
        }

        return when {
            lower.contains("morning") -> 9 to 0
            lower.contains("afternoon") -> 14 to 0
            lower.contains("evening") -> 18 to 30
            lower.contains("night") -> 20 to 30
            else -> null
        }
    }

    private fun extractDurationMinutes(lower: String): Int {
        Regex("""\b(\d{1,3})\s*(?:min|mins|minute|minutes|m)\b""").find(lower)?.let { match ->
            val mins = match.groupValues[1].toIntOrNull() ?: 60
            return mins.coerceIn(15, 240)
        }
        Regex("""\b(\d)\s*(?:hr|hrs|hour|hours|h)\b""").find(lower)?.let { match ->
            val hours = match.groupValues[1].toIntOrNull() ?: 1
            return (hours * 60).coerceIn(15, 240)
        }
        return 60
    }

    private fun inferCategory(lower: String): TaskCategory {
        return when {
            listOf("physics", "chem", "math", "study", "exam", "read", "chapter", "lab", "notes", "course", "revision").any { lower.contains(it) } -> TaskCategory.STUDY
            listOf("code", "build", "app", "bug", "git", "kotlin", "deploy", "api", "debug").any { lower.contains(it) } -> TaskCategory.CODE
            listOf("gym", "run", "walk", "workout", "yoga", "water", "sleep", "health", "meditate").any { lower.contains(it) } -> TaskCategory.HEALTH
            listOf("buy", "clean", "call", "family", "home", "personal", "errand").any { lower.contains(it) } -> TaskCategory.PERSONAL
            else -> TaskCategory.WORK
        }
    }

    private fun inferPriority(lower: String): TaskPriority {
        return when {
            listOf("critical", "urgent", "asap", "immediately").any { lower.contains(it) } -> TaskPriority.CRITICAL
            listOf("high priority", "high", "important", "exam").any { lower.contains(it) } -> TaskPriority.HIGH
            listOf("low priority", "low", "whenever", "someday").any { lower.contains(it) } -> TaskPriority.LOW
            else -> TaskPriority.MEDIUM
        }
    }

    private fun extractCleanTitle(raw: String, isEvent: Boolean): String {
        var cleaned = raw.trim().trimEnd('.', '!', '?')
        val leadingPatterns = listOf(
            Regex("""^(?i)(?:please\s+)?(?:add|create|make|schedule|book|plan|set|track)\s+(?:a\s+)?(?:new\s+)?(?:daily\s+)?(?:task|habit|event|meeting|session|reminder)?\s*(?:called|named|titled|:)?\s*"""),
            Regex("""^(?i)(?:remind\s+me\s+to|i\s+need\s+to|i\s+have\s+to)\s+"""),
            Regex("""^(?i)(?:todo|to-do|task|habit)\s*:\s*""")
        )
        leadingPatterns.forEach { regex ->
            cleaned = cleaned.replace(regex, "")
        }

        val trailingPatterns = mutableListOf(
            Regex("""(?i)\s+to\s+my\s+(?:tasks?|habits?|todo\s+list|calendar|schedule)\s*$"""),
            Regex("""(?i)\s+(?:high|medium|low|critical)\s+priority\s*$""")
        )
        if (isEvent) {
            trailingPatterns.add(Regex("""(?i)\s+for\s+\d{1,3}\s*(?:min|mins|minute|minutes|hr|hrs|hour|hours|m|h)\s*$"""))
            trailingPatterns.add(Regex("""(?i)\s+(?:at\s+)?\d{1,2}(?::\d{2})?\s*(?:am|pm)?\s*$"""))
            trailingPatterns.add(Regex("""(?i)\s+(?:day\s+after\s+tomorrow|tomorrow|tmrw|today|next\s+week|in\s+\d+\s+days?|morning|afternoon|evening|night)\s*$"""))
            trailingPatterns.add(Regex("""(?i)\s+(?:at\s+)?\d{1,2}(?::\d{2})?\s*(?:am|pm)?\s*$"""))
            trailingPatterns.add(Regex("""(?i)\s+(?:day\s+after\s+tomorrow|tomorrow|tmrw|today|next\s+week|in\s+\d+\s+days?)\s*$"""))
        } else {
            trailingPatterns.add(Regex("""(?i)\s+(?:due\s+)?(?:by\s+)?(?:day\s+after\s+tomorrow|tomorrow|tmrw|today|next\s+week|in\s+\d+\s+days?)\s*$"""))
        }

        repeat(2) {
            trailingPatterns.forEach { regex ->
                cleaned = cleaned.replace(regex, "")
            }
        }

        val finalTitle = cleaned.trim().trim('"', '\'', ',', '.', '-', ':')
        return if (finalTitle.isNotBlank()) {
            finalTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        } else {
            raw.trim().take(40)
        }
    }
}
