package com.example.suru

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.ai.LifeContextSnapshot
import com.example.data.local.CalendarEventEntity
import com.example.data.local.SuruMemoryEntity
import com.example.data.local.TaskEntity
import com.example.data.local.UserPreferencesState
import com.example.util.DateTimeUtils
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

private const val SURU_AI_TAG = "SuruIntelligence"

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
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = studyHelpSchema
            }
        )
    }

    private val chatModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    fun isCloudReady(): Boolean {
        return try {
            if (FirebaseApp.getApps(appContext).isEmpty()) return false
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun generateConversationalReply(
        userPrompt: String,
        context: LifeContextSnapshot,
        enabledMemories: List<SuruMemoryEntity>
    ): Pair<String, Boolean> {
        if (isCloudReady()) {
            try {
                // Privacy boundary: NEVER include Cycle Care logs or private Check-In / Quick Notes.
                val memoryBlock = if (enabledMemories.isEmpty()) "None" else {
                    enabledMemories.take(6).joinToString("; ") { "${it.keyLabel}: ${it.valueText}" }
                }
                val prompt = buildString {
                    appendLine("You are SURU, the personal AI digital assistant inside FEELORA.")
                    appendLine("Personality: calm, supportive, concise, practical, supports English, Hindi, and Hinglish naturally.")
                    appendLine("User: ${context.userName.ifBlank { "Friend" }} · Lv.${context.level} (${context.totalXp} XP) · Score ${context.productivityScore}%")
                    appendLine("Focus today: ${context.focusMinutesToday}m/${context.dailyFocusGoalMinutes}m · Pending tasks: ${context.pendingTasksSummary.ifBlank { "None" }}")
                    appendLine("Approved user preferences: $memoryBlock")
                    appendLine("Do not invent facts or textbook page citations. Reply helpfully in 2-4 crisp sentences.")
                    appendLine("User message: \"$userPrompt\"")
                }
                val response = withTimeout(30_000L) { chatModel.generateContent(prompt) }
                val text = response.text?.trim()
                if (!text.isNullOrBlank()) {
                    return text to false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(SURU_AI_TAG, "Cloud chat fallback (${t.message})")
            }
        }
        return buildLocalConversationalFallback(userPrompt, context, enabledMemories) to true
    }

    suspend fun generateStudyHelp(
        topic: String,
        mode: SuruStudyHelpMode,
        userNotes: String
    ): SuruStudyHelpResult {
        val cleanTopic = topic.trim().ifBlank { "Study Topic" }
        val cleanNotes = userNotes.trim()
        val hasNotes = cleanNotes.isNotBlank()

        if (isCloudReady()) {
            try {
                val prompt = buildString {
                    appendLine("Create study assistance for topic: \"$cleanTopic\" in mode: ${mode.label} (${mode.subtitle}).")
                    if (hasNotes) {
                        appendLine("Prioritize ONLY the user's pasted notes below when summarizing:")
                        appendLine(cleanNotes.take(3500))
                    } else {
                        appendLine("No textbook excerpts were pasted. Give general conceptual study help and do NOT fabricate specific textbook chapters or page numbers.")
                    }
                }
                val response = withTimeout(30_000L) { studyModel.generateContent(prompt) }
                val rawJson = response.text
                if (!rawJson.isNullOrBlank()) {
                    val json = JSONObject(rawJson)
                    val explanation = json.optString("conceptExplanation").trim()
                    if (explanation.isNotBlank()) {
                        val fronts = jsonArrayToList(json.optJSONArray("flashcardFronts"))
                        val backs = jsonArrayToList(json.optJSONArray("flashcardBacks"))
                        val cards = fronts.zip(backs).ifEmpty { buildLocalFlashcards(cleanTopic, cleanNotes) }
                        return SuruStudyHelpResult(
                            topic = cleanTopic,
                            mode = mode,
                            isBasedOnUserNotes = hasNotes,
                            sourceDisclaimer = if (hasNotes) {
                                "Grounded in your pasted study notes + AI study synthesis."
                            } else {
                                "AI-generated conceptual study guide (not cited from a specific textbook)."
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
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(SURU_AI_TAG, "Cloud study help fallback (${t.message})")
            }
        }

        return buildLocalStudyHelp(cleanTopic, mode, cleanNotes)
    }

    companion object {
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
                gentleClosing = "Start small, stay steady — KUTTA and I are right here with you."
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

        private fun buildLocalConversationalFallback(
            userPrompt: String,
            context: LifeContextSnapshot,
            memories: List<SuruMemoryEntity>
        ): String {
            val name = context.userName.ifBlank { "Friend" }
            val prefNote = memories.firstOrNull { it.isEnabled }?.let {
                " (Noted preference: ${it.valueText})"
            }.orEmpty()
            return "SURU is running in offline mode for $name$prefNote. You have ${context.pendingTaskTitles.size} pending task(s) and ${context.productivityScore}% productivity score.\n\nTry asking:\n• \"Suru, mujhe agle 5 din mein Physics ke 3 chapters complete karne hain\"\n• \"Add Revise Organic Chem tomorrow\"\n• \"Schedule Mock Test at 6 PM\"\n• \"Clear my backlog\" or \"Morning briefing\""
        }

        private fun jsonArrayToList(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).mapNotNull { idx ->
                arr.optString(idx).trim().takeIf { it.isNotBlank() }
            }
        }
    }
}
