package com.example.suru

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.AiMessageEntity
import com.example.data.local.CalendarEventEntity
import com.example.data.local.SuruMemoryEntity
import com.example.data.local.SuruStudyPlanEntity
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import com.example.data.local.UserPreferencesState
import com.example.util.DateTimeUtils
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private const val SURU_AI_TAG = "SuruIntelligence"
private const val GEMINI_FLASH_MODEL = "gemini-2.5-flash"

enum class SuruCloudAiStatus(val badgeLabel: String, val isCloudConfigured: Boolean) {
    CLOUD_GEMINI_KEY("Gemini Cloud Ready", true),
    CLOUD_FIREBASE("Firebase Gemini Ready", true),
    MISSING_API_KEY("Local NLU Only (No API Key)", false),
    OFFLINE("Offline Mode", false)
}

sealed class SuruAiResponse {
    data class Success(
        val action: SuruStructuredAction,
        val usedCloud: Boolean
    ) : SuruAiResponse()

    data class Error(
        val message: String,
        val status: SuruCloudAiStatus
    ) : SuruAiResponse()
}

class SuruIntelligenceService(private val appContext: Context) {

    private val studyHelpSchema = Schema.obj(
        properties = mapOf(
            "conceptExplanation" to Schema.string("Clear explanation tailored to the requested mode."),
            "summaryBullets" to Schema.array(Schema.string(), "3 concise takeaway points."),
            "practiceQuestions" to Schema.array(Schema.string(), "3 active recall or practice questions."),
            "flashcardFronts" to Schema.array(Schema.string(), "3 flashcard prompts."),
            "flashcardBacks" to Schema.array(Schema.string(), "3 concise flashcard answers."),
            "revisionChecklist" to Schema.array(Schema.string(), "3 revision steps."),
            "mistakeTips" to Schema.array(Schema.string(), "2 common mistake warnings.")
        )
    )

    private val studyModel by lazy {
        Firebase.ai.generativeModel(
            modelName = GEMINI_FLASH_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = studyHelpSchema
            }
        )
    }

    private val chatModel by lazy {
        Firebase.ai.generativeModel(modelName = GEMINI_FLASH_MODEL)
    }

    private val gateway = SuruAiGateway(appContext)

    fun getGateway(): SuruAiGateway = gateway

    fun getCloudAiStatus(): SuruCloudAiStatus {
        val online = isDeviceOnline()
        val hasFirebase = runCatching { FirebaseApp.getApps(appContext).isNotEmpty() }.getOrDefault(false)
        if (!online) {
            return if (gateway.hasAnyConfiguredProvider()) SuruCloudAiStatus.OFFLINE else SuruCloudAiStatus.MISSING_API_KEY
        }
        val key = configuredGeminiKey()
        if (key != null || gateway.hasAnyConfiguredProvider()) {
            return if (hasFirebase && key == null && configuredKey(BuildConfig.OPENAI_API_KEY) == null && configuredKey(BuildConfig.ANTHROPIC_API_KEY) == null) {
                SuruCloudAiStatus.CLOUD_FIREBASE
            } else {
                SuruCloudAiStatus.CLOUD_GEMINI_KEY
            }
        }
        if (hasFirebase) return SuruCloudAiStatus.CLOUD_FIREBASE
        return SuruCloudAiStatus.MISSING_API_KEY
    }

    fun isCloudReady(): Boolean {
        val st = getCloudAiStatus()
        return st == SuruCloudAiStatus.CLOUD_GEMINI_KEY || st == SuruCloudAiStatus.CLOUD_FIREBASE
    }

    private fun configuredKey(rawValue: String?): String? = SuruAiGateway.configuredKey(rawValue)

    private fun configuredGeminiKey(): String? {
        val raw = runCatching { BuildConfig.GEMINI_API_KEY.trim() }.getOrDefault("")
        if (raw.isBlank() || raw == "your_api_key_here" || raw.startsWith("YOUR_")) {
            return null
        }
        return raw
    }

    private fun isDeviceOnline(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun processUnifiedPrompt(
        userPrompt: String,
        conversationHistory: List<AiMessageEntity>,
        imageAttachment: SuruImageAttachment?,
        context: LifeContextSnapshot,
        tasks: List<TaskEntity>,
        events: List<CalendarEventEntity>,
        studyPlans: List<SuruStudyPlanEntity>,
        enabledMemories: List<SuruMemoryEntity>,
        pendingContext: SuruPendingContext?,
        todayEpochDay: Long = DateTimeUtils.todayEpochDay(),
        prefs: UserPreferencesState = UserPreferencesState()
    ): SuruAiResponse? {
        val status = getCloudAiStatus()
        if (!status.isCloudConfigured || status == SuruCloudAiStatus.OFFLINE) {
            if (imageAttachment != null) {
                val reason = if (status == SuruCloudAiStatus.OFFLINE) {
                    "You are offline right now. Connect to the internet so SURU can analyze your photo."
                } else {
                    "Image analysis requires a configured multimodal AI provider (Gemini, OpenAI, or Claude) in AI Studio Secrets."
                }
                return SuruAiResponse.Error(reason, status)
            }
            return null
        }

        val systemInstruction = buildSystemInstruction(
            context = context,
            tasks = tasks,
            events = events,
            studyPlans = studyPlans,
            enabledMemories = enabledMemories,
            pendingContext = pendingContext,
            hasImage = imageAttachment != null,
            todayEpochDay = todayEpochDay
        )

        val recentTurns = conversationHistory.takeLast(8).map { msg ->
            (if (msg.isFromUser) "user" else "model") to buildString {
                if (!msg.attachmentSummary.isNullOrBlank()) {
                    append("[Image: ${msg.attachmentSummary}] ")
                }
                append(msg.text.take(600))
            }
        }

        val taskKind = when {
            imageAttachment != null -> SuruRoutingTaskKind.VISION_MULTIMODAL
            userPrompt.contains("plan", ignoreCase = true) ||
                userPrompt.contains("schedule", ignoreCase = true) ||
                userPrompt.contains("backlog", ignoreCase = true) -> SuruRoutingTaskKind.PLANNING_REASONING
            else -> SuruRoutingTaskKind.CHAT
        }

        return try {
            val result = gateway.generateCompletion(
                prefs = prefs,
                taskKind = taskKind,
                systemInstruction = systemInstruction,
                historyTurns = recentTurns,
                userPrompt = userPrompt,
                imageAttachment = imageAttachment,
                expectJson = true
            )
            val rawJson = result.rawText

            val parsed = parseStructuredJsonToAction(
                rawJson = rawJson,
                context = context,
                tasks = tasks,
                events = events,
                todayEpochDay = todayEpochDay
            )
            if (parsed != null) {
                val providerTag = "${result.providerUsed.shortName} · ${result.modelUsed}"
                val decoratedReply = if (!result.fallbackNote.isNullOrBlank()) {
                    "${parsed.reply}\n\n(${result.fallbackNote})"
                } else parsed.reply
                SuruAiResponse.Success(
                    action = parsed.copy(reply = decoratedReply, isOfflineEngine = false),
                    usedCloud = true
                ).also { Log.d(SURU_AI_TAG, "SURU responded via $providerTag") }
            } else {
                val trimmed = rawJson.trim()
                if (trimmed.isNotBlank() && !trimmed.startsWith("{")) {
                    SuruAiResponse.Success(
                        action = SuruStructuredAction(
                            intent = SuruIntent.CHAT,
                            reply = trimmed,
                            isOfflineEngine = false
                        ),
                        usedCloud = true
                    )
                } else {
                    SuruAiResponse.Error(
                        "SURU received an unreadable response format from ${result.providerUsed.shortName}. Please rephrase your request.",
                        status
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(SURU_AI_TAG, "Cloud AI gateway failed: ${t.message}")
            SuruAiResponse.Error(
                "AI request failed: ${t.message ?: "Network or API error"}",
                status
            )
        }
    }

    private suspend fun callGeminiRestJson(
        apiKey: String,
        systemInstruction: String,
        historyTurns: List<Pair<String, String>>,
        userPrompt: String,
        imageAttachment: SuruImageAttachment?
    ): String? = withContext(Dispatchers.IO) {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_FLASH_MODEL:generateContent?key=$apiKey")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 35_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

        val contentsArray = JSONArray()
        historyTurns.forEach { (role, text) ->
            contentsArray.put(
                JSONObject().apply {
                    put("role", role)
                    put("parts", JSONArray().put(JSONObject().put("text", text)))
                }
            )
        }

        val currentParts = JSONArray()
        currentParts.put(
            JSONObject().put(
                "text",
                userPrompt.ifBlank { "Please analyze this attached image carefully and help me with my studies or tasks." }
            )
        )
        if (imageAttachment != null) {
            currentParts.put(
                JSONObject().put(
                    "inlineData",
                    JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", imageAttachment.base64Jpeg)
                    }
                )
            )
        }
        contentsArray.put(
            JSONObject().apply {
                put("role", "user")
                put("parts", currentParts)
            }
        )

        val payload = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            )
            put("contents", contentsArray)
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.4)
                    put("maxOutputTokens", 1400)
                    put("responseMimeType", "application/json")
                }
            )
        }

        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { s ->
                BufferedReader(InputStreamReader(s, Charsets.UTF_8)).readText()
            }.orEmpty()

            if (code !in 200..299) {
                val errMsg = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty().ifBlank { "HTTP $code" }
                throw IllegalStateException(errMsg)
            }

            val root = JSONObject(body)
            val candidates = root.optJSONArray("candidates") ?: return@withContext null
            if (candidates.length() == 0) return@withContext null
            val parts = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts")
                ?: return@withContext null
            val textBuilder = StringBuilder()
            for (i in 0 until parts.length()) {
                val t = parts.optJSONObject(i)?.optString("text").orEmpty()
                if (t.isNotBlank()) textBuilder.append(t)
            }
            textBuilder.toString().trim().takeIf { it.isNotBlank() }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun callFirebaseAiJson(
        systemInstruction: String,
        historyTurns: List<Pair<String, String>>,
        userPrompt: String,
        imageAttachment: SuruImageAttachment?
    ): String? {
        val combinedPrompt = buildString {
            appendLine(systemInstruction)
            if (historyTurns.isNotEmpty()) {
                appendLine("\nRecent conversation:")
                historyTurns.forEach { (r, t) -> appendLine("$r: $t") }
            }
            appendLine("\nCurrent user message: ${userPrompt.ifBlank { "Analyze this attached image." }}")
        }
        val response = withTimeout(30_000L) {
            if (imageAttachment != null) {
                chatModel.generateContent(
                    content {
                        image(imageAttachment.previewBitmap)
                        text(combinedPrompt)
                    }
                )
            } else {
                chatModel.generateContent(combinedPrompt)
            }
        }
        return response.text?.trim()
    }

    suspend fun generateStudyHelp(
        topic: String,
        mode: SuruStudyHelpMode,
        userNotes: String,
        prefs: UserPreferencesState = UserPreferencesState()
    ): SuruStudyHelpResult {
        val cleanTopic = topic.trim().ifBlank { "Study Topic" }
        val cleanNotes = userNotes.trim()
        val hasNotes = cleanNotes.isNotBlank()
        val status = getCloudAiStatus()

        if (status.isCloudConfigured && status != SuruCloudAiStatus.OFFLINE) {
            try {
                val prompt = buildString {
                    appendLine("Create structured JSON study help for topic: \"$cleanTopic\" in mode: ${mode.label} (${mode.subtitle}).")
                    appendLine("JSON fields required: conceptExplanation (string), summaryBullets (3 strings), practiceQuestions (3 strings), flashcardFronts (3 strings), flashcardBacks (3 strings), revisionChecklist (3 strings), mistakeTips (2 strings).")
                    if (hasNotes) {
                        appendLine("Ground ONLY in the user's pasted notes below:")
                        appendLine(cleanNotes.take(3500))
                    } else {
                        appendLine("Give accurate conceptual clarity without inventing page numbers or textbook citations.")
                    }
                }
                val result = gateway.generateCompletion(
                    prefs = prefs,
                    taskKind = SuruRoutingTaskKind.STUDY_HELP,
                    systemInstruction = "Return strictly valid JSON with keys: conceptExplanation, summaryBullets, practiceQuestions, flashcardFronts, flashcardBacks, revisionChecklist, mistakeTips.",
                    historyTurns = emptyList(),
                    userPrompt = prompt,
                    imageAttachment = null,
                    expectJson = true
                )
                parseStudyHelpJson(result.rawText, cleanTopic, mode, cleanNotes)?.let { parsed ->
                    val suffix = buildString {
                        append("Powered by ${result.providerUsed.shortName}")
                        if (!result.secondaryReviewedBy.isNullOrBlank()) {
                            append(" · Verified by ${result.secondaryReviewedBy}")
                        }
                    }
                    return parsed.copy(sourceDisclaimer = "${parsed.sourceDisclaimer} ($suffix)")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(SURU_AI_TAG, "Study help gateway fallback (${t.message})")
            }
        }

        return buildLocalStudyHelp(cleanTopic, mode, cleanNotes)
    }

    private fun parseStudyHelpJson(
        rawJson: String,
        cleanTopic: String,
        mode: SuruStudyHelpMode,
        cleanNotes: String
    ): SuruStudyHelpResult? {
        return runCatching {
            val sanitized = stripMarkdownJsonFence(rawJson)
            val json = JSONObject(sanitized)
            val explanation = json.optString("conceptExplanation").trim()
            if (explanation.isBlank()) return@runCatching null
            val hasNotes = cleanNotes.isNotBlank()
            val fronts = jsonArrayToList(json.optJSONArray("flashcardFronts"))
            val backs = jsonArrayToList(json.optJSONArray("flashcardBacks"))
            val cards = fronts.zip(backs).ifEmpty { buildLocalFlashcards(cleanTopic, cleanNotes) }
            SuruStudyHelpResult(
                topic = cleanTopic,
                mode = mode,
                isBasedOnUserNotes = hasNotes,
                sourceDisclaimer = if (hasNotes) {
                    "Grounded in your pasted study notes + Gemini synthesis."
                } else {
                    "Gemini conceptual study guide (not cited from a specific textbook)."
                },
                conceptExplanation = explanation,
                summaryBullets = jsonArrayToList(json.optJSONArray("summaryBullets")).ifEmpty {
                    buildLocalSummaryBullets(cleanTopic, cleanNotes)
                },
                practiceQuestions = jsonArrayToList(json.optJSONArray("practiceQuestions")).ifEmpty {
                    buildLocalPracticeQuestions(cleanTopic)
                },
                flashcards = cards,
                revisionChecklist = jsonArrayToList(json.optJSONArray("revisionChecklist")).ifEmpty {
                    buildLocalRevisionChecklist(cleanTopic)
                },
                mistakeTips = jsonArrayToList(json.optJSONArray("mistakeTips")).ifEmpty {
                    buildLocalMistakeTips(cleanTopic)
                }
            )
        }.getOrNull()
    }

    companion object {

        fun buildSystemInstruction(
            context: LifeContextSnapshot,
            tasks: List<TaskEntity>,
            events: List<CalendarEventEntity>,
            studyPlans: List<SuruStudyPlanEntity>,
            enabledMemories: List<SuruMemoryEntity>,
            pendingContext: SuruPendingContext?,
            hasImage: Boolean,
            todayEpochDay: Long
        ): String {
            // Privacy guard: NEVER include CycleLogEntity, DailyCheckInEntity.note, or QuickNoteEntity here.
            val pending = tasks.filter { !it.isCompleted }
            val overdue = pending.filter { it.deadlineEpochDay < todayEpochDay }
            val dueToday = pending.filter { it.deadlineEpochDay == todayEpochDay }
            val todayEvents = events.filter { it.dateEpochDay == todayEpochDay && !it.isCountdown }
                .sortedBy { it.startMinuteOfDay }
            val upcomingEvents = events.filter { it.dateEpochDay > todayEpochDay && it.dateEpochDay <= todayEpochDay + 7 && !it.isCountdown }
                .sortedWith(compareBy({ it.dateEpochDay }, { it.startMinuteOfDay }))

            val memoryLines = if (enabledMemories.isEmpty()) "None" else {
                enabledMemories.take(8).joinToString("; ") { "${it.keyLabel}: ${it.valueText}" }
            }
            val planLines = if (studyPlans.isEmpty()) "None" else {
                studyPlans.take(4).joinToString("; ") { "${it.subject} [${it.chaptersCsv}] (${it.status})" }
            }

            return buildString {
                appendLine("You are SURU, the personal AI assistant inside FEELORA.")
                appendLine("Personality: intelligent, warm, calm, practical, concise, slightly playful, supportive. Understand and reply naturally in English, Hindi, or Hinglish matching the user.")
                appendLine("Today: ${DateTimeUtils.formatFullDate(todayEpochDay)} (epochDay=$todayEpochDay).")
                appendLine("User: ${context.userName.ifBlank { "Friend" }} · Lv.${context.level} (${context.totalXp} XP) · Streak ${context.dailyStreak}d · Productivity ${context.productivityScore}%")
                appendLine("Focus today: ${context.focusMinutesToday}m / ${context.dailyFocusGoalMinutes}m goal · Completed tasks total: ${context.completedTasksCount} · Habits today: ${context.habitsCompletedToday}/${context.habitsTotal}")
                appendLine("Open tasks (${pending.size}, ${overdue.size} overdue, ${dueToday.size} due today):")
                pending.take(15).forEach { t ->
                    val statusTag = when {
                        t.deadlineEpochDay < todayEpochDay -> "OVERDUE"
                        t.deadlineEpochDay == todayEpochDay -> "TODAY"
                        else -> "in ${t.deadlineEpochDay - todayEpochDay}d"
                    }
                    appendLine("  - id=${t.id}: \"${t.title}\" [${t.priority.label}, ${t.category.label}, $statusTag]")
                }
                appendLine("Today's calendar events (${todayEvents.size}):")
                todayEvents.forEach { e ->
                    appendLine("  - id=${e.id}: ${DateTimeUtils.formatTimeOfDay(e.startMinuteOfDay)} (${e.durationMinutes}m) \"${e.title}\"")
                }
                if (upcomingEvents.isNotEmpty()) {
                    appendLine("Upcoming events: " + upcomingEvents.take(8).joinToString("; ") {
                        "d+${it.dateEpochDay - todayEpochDay} ${DateTimeUtils.formatTimeOfDay(it.startMinuteOfDay)} \"${it.title}\""
                    })
                }
                appendLine("Active study plans: $planLines")
                appendLine("Approved user preferences (SURU Memory): $memoryLines")
                if (pendingContext != null) {
                    appendLine("Pending follow-up context: intent=${pendingContext.intent.name}, subject=${pendingContext.subject}, chapters=${pendingContext.chapters}, missing=${pendingContext.missingSlot}")
                }
                if (hasImage) {
                    appendLine("An image is attached! Examine it carefully (handwritten notes, diagrams, textbook problems, schedules). Summarize accurately, answer the question, or offer tasks/study plan items from its visible content. If unreadable or blurry, say so honestly.")
                }
                appendLine()
                appendLine("OUTPUT FORMAT: Return ONLY a single valid JSON object (no markdown fences) with these fields:")
                appendLine("- intent (string): one of CHAT, ASK_CLARIFICATION, CREATE_TASK, UPDATE_TASK, RESCHEDULE_TASK, COMPLETE_TASK, DELETE_TASK, BREAKDOWN_TASK, CREATE_EVENT, RESCHEDULE_EVENT, CREATE_HABIT, CREATE_STUDY_PLAN, BACKLOG_RECOVERY, LOG_STUDY_PROGRESS, PLAN_WEEK, FIND_FREE_SLOTS, PROGRESS_SUMMARY, OVERDUE_CHECK, STUDY_HELP, WELLNESS_CHECK, MORNING_BRIEFING, EVENING_REFLECTION, ORGANIZE_TOMORROW, SAVE_MEMORY")
                appendLine("- reply (string): natural, helpful response shown directly to user (2-5 clear sentences or concise bullet lines). Never fabricate user stats.")
                appendLine("- title (string|null): task/event/habit title or target task title to update/complete/delete/reschedule.")
                appendLine("- newTitle (string|null): updated title when intent=UPDATE_TASK.")
                appendLine("- targetTaskId (long|null): exact task id from the open tasks list above when modifying/completing/deleting/rescheduling an existing task.")
                appendLine("- targetEventId (long|null): exact event id when rescheduling an event.")
                appendLine("- subject (string|null): study subject when intent=CREATE_STUDY_PLAN.")
                appendLine("- chapters (array of strings): explicit chapters when creating a study plan.")
                appendLine("- deadlineDays (int): 1..60 days for study plans.")
                appendLine("- dailyMinutesBudget (int): 30..360 daily study minutes.")
                appendLine("- preferredWindow (string): MORNING | AFTERNOON | EVENING | NIGHT | CUSTOM.")
                appendLine("- dateOffsetDays (int): 0=today, 1=tomorrow, etc.")
                appendLine("- rescheduleAllToday (boolean): true when moving all today's open tasks to tomorrow/another day.")
                appendLine("- startHour (int 0-23), startMinute (int 0-59), durationMinutes (int 15-240)")
                appendLine("- loggedMinutes (int): minutes completed when intent=LOG_STUDY_PROGRESS (e.g. '2 hours of Chemistry' -> 120).")
                appendLine("- category (string): WORK | STUDY | CODE | HEALTH | PERSONAL.")
                appendLine("- priority (string): LOW | MEDIUM | HIGH | CRITICAL.")
                appendLine("- studyHelpTopic (string|null), studyHelpMode (string): BEGINNER | DETAILED | QUICK_REVISION | QUESTION_PRACTICE | EXAM_ORIENTED.")
                appendLine("- memoryKey (string|null), memoryValue (string|null), memoryCategory (string): TIME_PREF | STUDY_PREF | GOAL.")
                appendLine("- subtasks (array of strings): for BREAKDOWN_TASK.")
                appendLine("IMPORTANT: Ask brief clarification (intent=ASK_CLARIFICATION) only if truly missing critical info for a study plan (like which chapters or daily time budget). Otherwise act directly!")
            }
        }

        fun parseStructuredJsonToAction(
            rawJson: String,
            context: LifeContextSnapshot,
            tasks: List<TaskEntity>,
            events: List<CalendarEventEntity>,
            todayEpochDay: Long
        ): SuruStructuredAction? {
            return runCatching {
                val cleaned = stripMarkdownJsonFence(rawJson)
                val json = JSONObject(cleaned)
                val intentStr = json.optString("intent", "CHAT").trim().uppercase(Locale.US)
                val intent = SuruIntent.entries.firstOrNull { it.name == intentStr } ?: SuruIntent.CHAT
                val reply = json.optString("reply").trim().ifBlank { return@runCatching null }
                val title = optNullableString(json, "title")
                val newTitle = optNullableString(json, "newTitle")
                val subject = optNullableString(json, "subject")
                val targetTaskId = if (json.has("targetTaskId") && !json.isNull("targetTaskId")) {
                    json.optLong("targetTaskId", -1L).takeIf { it > 0L }
                } else null
                val targetEventId = if (json.has("targetEventId") && !json.isNull("targetEventId")) {
                    json.optLong("targetEventId", -1L).takeIf { it > 0L }
                } else null

                val dateOffset = json.optInt("dateOffsetDays", 0).coerceIn(0, 365)
                val startHour = json.optInt("startHour", 9).coerceIn(0, 23)
                val startMinute = json.optInt("startMinute", 0).coerceIn(0, 59)
                val durationMinutes = json.optInt("durationMinutes", 60).coerceIn(15, 240)
                val loggedMinutes = json.optInt("loggedMinutes", 0).coerceIn(0, 600)
                val deadlineDays = json.optInt("deadlineDays", 5).coerceIn(1, 60)
                val dailyMinutesBudget = json.optInt("dailyMinutesBudget", 90).coerceIn(30, 360)
                val rescheduleAllToday = json.optBoolean("rescheduleAllToday", false)

                val category = TaskCategory.entries.firstOrNull {
                    it.name == json.optString("category").trim().uppercase(Locale.US)
                } ?: TaskCategory.STUDY
                val priority = TaskPriority.entries.firstOrNull {
                    it.name == json.optString("priority").trim().uppercase(Locale.US)
                } ?: TaskPriority.MEDIUM
                val window = SuruTimeWindow.entries.firstOrNull {
                    it.name == json.optString("preferredWindow").trim().uppercase(Locale.US)
                } ?: SuruTimeWindow.EVENING
                val studyHelpMode = SuruStudyHelpMode.entries.firstOrNull {
                    it.name == json.optString("studyHelpMode").trim().uppercase(Locale.US)
                } ?: SuruStudyHelpMode.BEGINNER

                val chapters = jsonArrayToList(json.optJSONArray("chapters"))
                val subtasks = jsonArrayToList(json.optJSONArray("subtasks"))
                val studyHelpTopic = optNullableString(json, "studyHelpTopic")
                val memoryKey = optNullableString(json, "memoryKey")
                val memoryValue = optNullableString(json, "memoryValue")
                val memoryCategory = optNullableString(json, "memoryCategory") ?: "STUDY_PREF"

                var studyDraft: SuruStudyPlanDraft? = null
                var backlogPlan: SuruBacklogPlan? = null
                var requiresConfirmation = false
                var confirmTitle: String? = null
                var confirmMessage: String? = null

                when (intent) {
                    SuruIntent.CREATE_STUDY_PLAN -> {
                        val effectiveChapters = chapters.ifEmpty {
                            listOf("${subject ?: title ?: "Core"} Chapter 1", "${subject ?: title ?: "Core"} Chapter 2")
                        }
                        studyDraft = SuruStudyPlanner.buildStudyPlanDraft(
                            subject = subject ?: title ?: "Study",
                            chapters = effectiveChapters,
                            deadlineDays = deadlineDays,
                            dailyAvailableMinutes = dailyMinutesBudget,
                            preferredWindow = window,
                            startHour = if (json.has("startHour")) startHour else window.defaultHour,
                            startMinute = if (json.has("startMinute")) startMinute else window.defaultMinute,
                            existingEvents = events,
                            todayEpochDay = todayEpochDay
                        )
                        requiresConfirmation = true
                        confirmTitle = "Confirm ${studyDraft.subject} Study Plan"
                        confirmMessage = studyDraft.summaryNote
                    }
                    SuruIntent.BACKLOG_RECOVERY -> {
                        backlogPlan = SuruStudyPlanner.buildBacklogRecoveryPlan(
                            tasks = tasks,
                            existingEvents = events,
                            dailyFocusGoalMinutes = context.dailyFocusGoalMinutes,
                            todayEpochDay = todayEpochDay
                        )
                        if (backlogPlan.items.isNotEmpty()) {
                            requiresConfirmation = true
                            confirmTitle = "Confirm Backlog Recovery Plan"
                            confirmMessage = backlogPlan.summaryNote
                        }
                    }
                    SuruIntent.DELETE_TASK -> {
                        val target = targetTaskId?.let { id -> tasks.firstOrNull { it.id == id } }
                            ?: title?.let { q -> tasks.firstOrNull { it.title.contains(q, ignoreCase = true) } }
                        if (target != null) {
                            requiresConfirmation = true
                            confirmTitle = "Delete Task?"
                            confirmMessage = "Remove \"${target.title}\" from your Tasks?"
                        }
                    }
                    SuruIntent.CREATE_TASK -> {
                        if (!title.isNullOrBlank()) {
                            val dup = TaskIntelligence.findDuplicate(title, tasks.filter { !it.isCompleted })
                            if (dup != null) {
                                requiresConfirmation = true
                                confirmTitle = "Possible Duplicate Task"
                                confirmMessage = "\"${dup.title}\" is already in your pending tasks."
                            }
                        }
                    }
                    SuruIntent.CREATE_EVENT, SuruIntent.RESCHEDULE_EVENT -> {
                        val targetDay = todayEpochDay + dateOffset
                        val startMin = startHour * 60 + startMinute
                        val candidateEvents = if (targetEventId != null) events.filter { it.id != targetEventId } else events
                        val overlaps = CalendarIntelligence.findOverlaps(targetDay, startMin, durationMinutes, candidateEvents)
                        if (overlaps.isNotEmpty()) {
                            requiresConfirmation = true
                            confirmTitle = "Schedule Conflict Detected"
                            confirmMessage = "Overlaps with \"${overlaps.first().title}\" at ${DateTimeUtils.formatTimeOfDay(overlaps.first().startMinuteOfDay)}."
                        }
                    }
                    else -> Unit
                }

                SuruStructuredAction(
                    intent = intent,
                    reply = reply,
                    title = title,
                    newTitle = newTitle,
                    subject = subject,
                    dateOffsetDays = dateOffset,
                    startHour = startHour,
                    startMinute = startMinute,
                    durationMinutes = durationMinutes,
                    loggedMinutes = loggedMinutes,
                    deadlineDays = deadlineDays,
                    dailyMinutesBudget = dailyMinutesBudget,
                    rescheduleAllToday = rescheduleAllToday,
                    category = category,
                    priority = priority,
                    chapters = chapters,
                    subtasks = subtasks,
                    studyHelpTopic = studyHelpTopic,
                    studyHelpMode = studyHelpMode,
                    memoryKey = memoryKey,
                    memoryValue = memoryValue,
                    memoryCategory = memoryCategory,
                    targetTaskId = targetTaskId,
                    targetEventId = targetEventId,
                    studyPlanDraft = studyDraft,
                    backlogPlan = backlogPlan,
                    requiresConfirmation = requiresConfirmation,
                    confirmationTitle = confirmTitle,
                    confirmationMessage = confirmMessage,
                    isOfflineEngine = false
                )
            }.getOrNull()
        }

        private fun optNullableString(json: JSONObject, key: String): String? {
            if (!json.has(key) || json.isNull(key)) return null
            return json.optString(key).trim().takeIf { it.isNotBlank() && it != "null" }
        }

        internal fun stripMarkdownJsonFence(raw: String): String {
            var s = raw.trim()
            if (s.startsWith("```")) {
                s = s.removePrefix("```json").removePrefix("```").trim()
                if (s.endsWith("```")) {
                    s = s.removeSuffix("```").trim()
                }
            }
            val start = s.indexOf('{')
            val end = s.lastIndexOf('}')
            return if (start >= 0 && end > start) s.substring(start, end + 1) else s
        }

        fun buildMorningBriefing(
            prefs: UserPreferencesState,
            tasks: List<TaskEntity>,
            events: List<CalendarEventEntity>,
            todayEpochDay: Long = DateTimeUtils.todayEpochDay()
        ): SuruDailyBriefing {
            val name = prefs.userName.ifBlank { "Friend" }
            val pending = tasks.filter { !it.isCompleted }
                .sortedWith(compareByDescending<TaskEntity> { it.priority.ordinal }.thenBy { it.deadlineEpochDay })
            val todayEvents = events
                .filter { it.dateEpochDay == todayEpochDay && !it.isCountdown }
                .sortedBy { it.startMinuteOfDay }
            val waterRemaining = (prefs.dailyWaterGoalMl - prefs.waterIntakeMlToday).coerceAtLeast(0)

            val priorityLines = pending.take(3).map { task ->
                val tag = if (task.deadlineEpochDay < todayEpochDay) "Overdue" else task.priority.label
                "${task.title} ($tag)"
            }
            val eventLines = todayEvents.take(4).map { ev ->
                "${DateTimeUtils.formatTimeOfDay(ev.startMinuteOfDay)} · ${ev.title} (${ev.durationMinutes}m)"
            }

            val focusSummary = when {
                pending.isEmpty() && todayEvents.isEmpty() ->
                    "Your slate is open today — pick one 25-minute focus priority to start calmly."
                else ->
                    "${pending.size} open task(s) and ${todayEvents.size} scheduled block(s) today. Focus target: ${DateTimeUtils.formatMinutesCompact(prefs.dailyFocusGoalMinutes)}."
            }

            val wellnessNote = "Hydration goal: ${DateTimeUtils.formatMinutesCompact(prefs.dailyFocusGoalMinutes)} focus · ${waterRemaining}ml water left today. Take short 10m breaks between study blocks."

            return SuruDailyBriefing(
                isMorning = true,
                headline = "Good Morning, $name",
                greeting = "SURU Morning Briefing · ${DateTimeUtils.formatFullDate(todayEpochDay)}",
                focusSummary = focusSummary,
                priorityTasks = priorityLines,
                scheduleBlocks = eventLines,
                wellnessNote = wellnessNote,
                gentleClosing = "Start small, stay steady — I'm right here whenever you want to adjust your day."
            )
        }

        fun buildEveningReflection(
            prefs: UserPreferencesState,
            tasks: List<TaskEntity>,
            todayEpochDay: Long = DateTimeUtils.todayEpochDay()
        ): SuruDailyBriefing {
            val name = prefs.userName.ifBlank { "Friend" }
            val completedToday = tasks.filter {
                it.isCompleted && it.completedAtMillis?.let { ts ->
                    DateTimeUtils.epochDayFromMillis(ts) == todayEpochDay
                } == true
            }
            val openTasks = tasks.filter { !it.isCompleted }.take(3)
            val focusCompact = DateTimeUtils.formatMinutesCompact(prefs.focusMinutesLoggedToday)

            val summary = buildString {
                append("Today you logged $focusCompact of deep focus")
                if (completedToday.isNotEmpty()) {
                    append(" and completed ${completedToday.size} task(s)")
                }
                append(". Streak: ${prefs.dailyStreak}d · Level ${prefs.level}.")
            }

            val wellness = if (prefs.waterIntakeMlToday >= prefs.dailyWaterGoalMl * 0.75f) {
                "Great hydration today (${prefs.waterIntakeMlToday}/${prefs.dailyWaterGoalMl} ml). Wind down screens before sleep."
            } else {
                "Have a glass of water before winding down (${prefs.waterIntakeMlToday}/${prefs.dailyWaterGoalMl} ml logged). Rest well tonight."
            }

            return SuruDailyBriefing(
                isMorning = false,
                headline = "Evening Reflection · $name",
                greeting = "SURU Day Wrap-Up · ${DateTimeUtils.formatFullDate(todayEpochDay)}",
                focusSummary = summary,
                priorityTasks = openTasks.map { "Carry forward: ${it.title} (${it.priority.label})" },
                scheduleBlocks = completedToday.take(4).map { "Done: ${it.title}" },
                wellnessNote = wellness,
                gentleClosing = "Whatever you finished today counts. Tomorrow starts fresh."
            )
        }

        fun buildLocalStudyHelp(
            topic: String,
            mode: SuruStudyHelpMode,
            userNotes: String
        ): SuruStudyHelpResult {
            val hasNotes = userNotes.isNotBlank()
            val explanation = if (hasNotes) {
                val sentences = userNotes.split(Regex("[.!?\\n]+")).map { it.trim() }.filter { it.length > 8 }
                val preview = sentences.take(3).joinToString(". ")
                when (mode) {
                    SuruStudyHelpMode.BEGINNER ->
                        "Simple breakdown from your notes on $topic: $preview. Focus first on the core cause-and-effect idea before memorizing terms."
                    SuruStudyHelpMode.DETAILED ->
                        "Detailed synthesis of your $topic notes: $preview. Trace how each definition links into problem-solving steps."
                    SuruStudyHelpMode.QUICK_REVISION ->
                        "60-second revision of $topic from your notes: $preview."
                    SuruStudyHelpMode.QUESTION_PRACTICE ->
                        "Practice framing for $topic based on your notes: turn each core point ($preview) into a 'Why?' and 'How?' check."
                    SuruStudyHelpMode.EXAM_ORIENTED ->
                        "Exam-focused lens on your $topic notes: lock down exact definitions, units/conditions, and exceptions highlighted in: $preview."
                }
            } else {
                when (mode) {
                    SuruStudyHelpMode.BEGINNER ->
                        "Think of $topic in 3 layers: (1) What problem or phenomenon it describes, (2) the core relationship or rule governing it, and (3) one concrete everyday example. Paste your own notes above anytime if you want a summary strictly grounded in your syllabus."
                    SuruStudyHelpMode.DETAILED ->
                        "To master $topic in depth: start from first-principles definitions, write down assumptions/boundary conditions, derive or trace the key mechanism step-by-step, and verify with 2–3 varied numerical or conceptual edge cases."
                    SuruStudyHelpMode.QUICK_REVISION ->
                        "Rapid Recall for $topic: 1) Core definition in one line, 2) Key formula / rule & units, 3) Common exception or special case."
                    SuruStudyHelpMode.QUESTION_PRACTICE ->
                        "Active Recall Workout for $topic: Close your notes, explain the core idea aloud in 30 seconds, solve one direct application, and test one edge-case twist."
                    SuruStudyHelpMode.EXAM_ORIENTED ->
                        "Exam Strategy for $topic: Examiners commonly test boundary conditions, sign conventions/units, and contrasting pairs. Highlight keywords and check assumptions before answering."
                }
            }

            return SuruStudyHelpResult(
                topic = topic,
                mode = mode,
                isBasedOnUserNotes = hasNotes,
                sourceDisclaimer = if (hasNotes) {
                    "Generated locally from your pasted study notes (offline mode)."
                } else {
                    "Offline conceptual study framework (no textbook citations fabricated — paste your notes above for note-grounded summaries)."
                },
                conceptExplanation = explanation,
                summaryBullets = buildLocalSummaryBullets(topic, userNotes),
                practiceQuestions = buildLocalPracticeQuestions(topic),
                flashcards = buildLocalFlashcards(topic, userNotes),
                revisionChecklist = buildLocalRevisionChecklist(topic),
                mistakeTips = buildLocalMistakeTips(topic)
            )
        }

        private fun buildLocalSummaryBullets(topic: String, userNotes: String): List<String> {
            if (userNotes.isNotBlank()) {
                val lines = userNotes.split(Regex("[.\\n]+")).map { it.trim() }.filter { it.length > 6 }
                if (lines.isNotEmpty()) return lines.take(4)
            }
            return listOf(
                "Core idea: Define $topic clearly in your own words without looking at notes.",
                "Key relations: Map out formulas, conditions, or cause-effect links in $topic.",
                "Application: Connect $topic to 2 solved examples and 1 edge case."
            )
        }

        private fun buildLocalPracticeQuestions(topic: String): List<String> = listOf(
            "1. In 2 sentences, how would you explain $topic to a beginner?",
            "2. What assumptions or conditions must hold true when applying $topic?",
            "3. Describe one classic mistake students make in $topic and how to catch it."
        )

        private fun buildLocalFlashcards(topic: String, userNotes: String): List<Pair<String, String>> {
            if (userNotes.isNotBlank()) {
                val lines = userNotes.split(Regex("[.\\n]+")).map { it.trim() }.filter { it.length > 8 }.take(3)
                if (lines.isNotEmpty()) {
                    return lines.mapIndexed { idx, line ->
                        "Note Recall #${idx + 1} ($topic)" to line
                    }
                }
            }
            return listOf(
                "What is the central goal of $topic?" to "Write your 1-sentence core definition + units/context.",
                "When does $topic break down or need a special rule?" to "Check boundary conditions, signs, and exceptions.",
                "Fast self-check before an exam on $topic" to "Recall 1 formula/framework + 1 worked example from memory."
            )
        }

        private fun buildLocalRevisionChecklist(topic: String): List<String> = listOf(
            "Explain $topic aloud in under 60 seconds",
            "Write key terms / equations on a blank page from memory",
            "Solve 3 practice questions without peeking at hints",
            "Flag 1 tricky sub-topic to revisit tomorrow"
        )

        private fun buildLocalMistakeTips(topic: String): List<String> = listOf(
            "Rushing past definitions & units in $topic — always verify units/assumptions first.",
            "Passive re-reading instead of active recall — test yourself before reviewing answers."
        )

        fun buildLocalConversationalFallback(
            status: SuruCloudAiStatus,
            context: LifeContextSnapshot,
            memories: List<SuruMemoryEntity>
        ): String {
            val name = context.userName.ifBlank { "Friend" }
            val prefNote = memories.firstOrNull { it.isEnabled }?.let {
                " (Noted preference: ${it.valueText})"
            }.orEmpty()
            val modeNotice = when (status) {
                SuruCloudAiStatus.MISSING_API_KEY ->
                    "Cloud Gemini AI is not configured yet (add GEMINI_API_KEY in AI Studio Secrets for full open-ended Q&A and photo understanding)."
                SuruCloudAiStatus.OFFLINE ->
                    "You're offline right now, so SURU is running with on-device planning and task tools."
                else ->
                    "SURU is running in local mode."
            }
            return "$modeNotice Hi $name$prefNote — you currently have ${context.pendingTaskTitles.size} pending task(s) and ${context.productivityScore}% productivity.\n\nLocal commands ready anytime:\n• \"Suru, mujhe agle 5 din mein Physics ke 3 chapters complete karne hain\"\n• \"Move today's tasks to tomorrow\"\n• \"Plan my entire week\"\n• \"I completed 2 hours of Chemistry\""
        }

        private fun jsonArrayToList(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).mapNotNull { idx ->
                arr.optString(idx).trim().takeIf { it.isNotBlank() }
            }
        }
    }
}
