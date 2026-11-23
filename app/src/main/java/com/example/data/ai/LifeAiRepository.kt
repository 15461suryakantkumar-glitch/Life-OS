package com.example.data.ai

import android.util.Log
import com.example.data.local.TaskCategory
import com.example.data.local.TaskPriority
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

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
        val priority: TaskPriority = TaskPriority.MEDIUM
    ) : AiExecutionResult()

    data class Error(val message: String) : AiExecutionResult()
}

data class LifeContextSnapshot(
    val userName: String,
    val level: Int,
    val totalXp: Int,
    val dailyStreak: Int,
    val pendingTasksSummary: String,
    val completedTasksCount: Int,
    val upcomingEventsSummary: String,
    val screenTimeTodayMinutes: Int,
    val focusMinutesToday: Int
)

class LifeAiRepository {

    private val actionSchema = Schema.obj(
        properties = mapOf(
            "reply" to Schema.string(
                description = "Concise, helpful personal command center response for the user."
            ),
            "actionType" to Schema.enumeration(
                values = listOf("NONE", "CREATE_TASK", "CREATE_EVENT", "ORGANIZE_TOMORROW"),
                description = "Use CREATE_TASK to add a task, CREATE_EVENT to schedule a calendar block, ORGANIZE_TOMORROW to schedule top pending tasks for tomorrow, or NONE for analysis/advice."
            ),
            "title" to Schema.string(
                description = "Short title of the task or calendar event if actionType is CREATE_TASK or CREATE_EVENT.",
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
                description = "Task or event category."
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

    suspend fun processPrompt(
        userPrompt: String,
        context: LifeContextSnapshot
    ): AiExecutionResult {
        val systemPrompt = buildString {
            appendLine("You are LIFE AI, the integrated personal operating system assistant inside LIFE OS.")
            appendLine("Tone: crisp, strategic, encouraging, zero fluff.")
            appendLine("User telemetry snapshot:")
            appendLine("- Name: ${context.userName} | Level ${context.level} (${context.totalXp} XP) | ${context.dailyStreak}-day streak")
            appendLine("- Completed tasks: ${context.completedTasksCount} | Focus today: ${context.focusMinutesToday}m | Screen time today: ${context.screenTimeTodayMinutes}m")
            appendLine("- Pending tasks: ${context.pendingTasksSummary.ifBlank { "None" }}")
            appendLine("- Upcoming events: ${context.upcomingEventsSummary.ifBlank { "None" }}")
            appendLine()
            appendLine("User message: \"$userPrompt\"")
            appendLine("Respond with structured JSON. If the user asks to schedule something (e.g. 'Schedule Physics tomorrow at 8 AM'), choose CREATE_EVENT with dateOffsetDays=1, startHour=8, startMinute=0. If they ask to add a task, choose CREATE_TASK. If they ask to organize tomorrow based on pending tasks, choose ORGANIZE_TOMORROW and summarize the proposed time-blocked plan in 'reply'. If they ask for weekly productivity or advice, choose NONE and provide specific numbers from the snapshot.")
        }

        return try {
            val response = withTimeout(60_000L) {
                structuredModel.generateContent(systemPrompt)
            }
            val rawJson = response.text
            if (rawJson.isNullOrBlank()) {
                return AiExecutionResult.Error("LIFE AI returned an empty response. Please try again.")
            }

            val json = JSONObject(rawJson)
            val reply = json.optString("reply", "Command processed.")
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
                priority = priority
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "LIFE AI request timed out", e)
            AiExecutionResult.Error("LIFE AI timed out after 60 seconds. Check your connection and retry.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "LIFE AI generation failed", e)
            AiExecutionResult.Error(e.localizedMessage ?: "Unable to reach LIFE AI right now.")
        }
    }
}
