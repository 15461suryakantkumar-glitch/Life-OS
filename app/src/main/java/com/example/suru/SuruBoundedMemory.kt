package com.example.suru

import com.example.data.local.SuruMemoryEntity
import java.util.Locale

data class SuruTurn(
    val isFromUser: Boolean,
    val text: String
)

data class SuruBoundedMemoryState(
    val rollingSummary: String = "",
    val recentTurns: List<SuruTurn> = emptyList()
)

/**
 * Compact, privacy-guarded bounded memory system for SURU AI.
 *
 * Maintains:
 * 1) A concise rolling summary of older turns beyond the active window (capped in length).
 * 2) The most recent 8-10 conversation turns (trimmed per turn so token usage stays fast and bounded).
 * 3) Strictly non-sensitive, prompt-relevant user preferences (never cycle_logs or daily_check_ins).
 */
object SuruBoundedMemory {

    const val MIN_RECENT_TURNS: Int = 8
    const val MAX_RECENT_TURNS: Int = 10
    const val MAX_TURN_CHARS: Int = 480
    const val MAX_SUMMARY_CHARS: Int = 640
    private const val MAX_SUMMARY_SNIPPET_CHARS: Int = 90
    private const val MAX_SUMMARY_ITEMS: Int = 7

    private val sensitiveKeywords = listOf(
        "cycle_logs",
        "daily_check_ins",
        "cycle care",
        "cycle_care",
        "period flow",
        "flowlevel",
        "menstrual",
        "ovulation",
        "last period",
        "next period",
        "period start",
        "period end",
        "symptomscssv",
        "daily check-in note",
        "quick_notes",
        "supabase",
        "openrouter_api_key",
        "gemini_api_key",
        "bearer ",
        "sk-or-"
    )

    private val boilerplateReplies = setOf(
        "I'm having a little trouble connecting right now. Please try again in a moment.",
        "I'm having trouble reaching my thoughts right now. Please try again in a moment."
    )

    fun isSensitiveText(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val lower = raw.lowercase(Locale.US)
        return sensitiveKeywords.any { lower.contains(it) }
    }

    fun sanitizeTurnText(raw: String): String {
        val compact = raw.replace(Regex("\\s+"), " ").trim()
        if (compact.isEmpty() || isSensitiveText(compact)) return ""
        return if (compact.length > MAX_TURN_CHARS) {
            compact.take(MAX_TURN_CHARS - 1).trimEnd() + "…"
        } else {
            compact
        }
    }

    fun appendTurn(
        state: SuruBoundedMemoryState,
        turn: SuruTurn
    ): SuruBoundedMemoryState {
        val cleanText = sanitizeTurnText(turn.text)
        if (cleanText.isEmpty() || cleanText in boilerplateReplies) {
            return state
        }
        val normalized = SuruTurn(isFromUser = turn.isFromUser, text = cleanText)
        val combined = state.recentTurns + normalized
        if (combined.size <= MAX_RECENT_TURNS) {
            return state.copy(recentTurns = combined)
        }
        val overflowCount = combined.size - MAX_RECENT_TURNS
        val evicted = combined.take(overflowCount)
        val retained = combined.drop(overflowCount)
        val updatedSummary = foldIntoRollingSummary(state.rollingSummary, evicted)
        return SuruBoundedMemoryState(
            rollingSummary = updatedSummary,
            recentTurns = retained
        )
    }

    fun fromHistory(
        turns: List<SuruTurn>,
        maxRecentTurns: Int = MAX_RECENT_TURNS
    ): SuruBoundedMemoryState {
        val safeLimit = maxRecentTurns.coerceIn(MIN_RECENT_TURNS, MAX_RECENT_TURNS)
        val cleaned = turns.mapNotNull { turn ->
            val sanitized = sanitizeTurnText(turn.text)
            if (sanitized.isEmpty() || sanitized in boilerplateReplies) null
            else SuruTurn(isFromUser = turn.isFromUser, text = sanitized)
        }
        if (cleaned.size <= safeLimit) {
            return SuruBoundedMemoryState(rollingSummary = "", recentTurns = cleaned)
        }
        val older = cleaned.dropLast(safeLimit)
        val recent = cleaned.takeLast(safeLimit)
        return SuruBoundedMemoryState(
            rollingSummary = foldIntoRollingSummary("", older),
            recentTurns = recent
        )
    }

    fun foldIntoRollingSummary(
        existingSummary: String,
        evictedTurns: List<SuruTurn>
    ): String {
        if (evictedTurns.isEmpty()) return existingSummary.take(MAX_SUMMARY_CHARS)

        val existingItems = existingSummary
            .split(" | ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()

        evictedTurns.forEach { turn ->
            val clean = sanitizeTurnText(turn.text)
            if (clean.isNotEmpty() && !isLowValueFiller(clean)) {
                val prefix = if (turn.isFromUser) "User: " else "SURU: "
                val snippet = compressSnippet(clean, MAX_SUMMARY_SNIPPET_CHARS - prefix.length)
                val entry = "$prefix$snippet"
                if (existingItems.none { it.equals(entry, ignoreCase = true) }) {
                    existingItems.add(entry)
                }
            }
        }

        while (existingItems.size > MAX_SUMMARY_ITEMS) {
            existingItems.removeAt(0)
        }

        var joined = existingItems.joinToString(" | ")
        while (joined.length > MAX_SUMMARY_CHARS && existingItems.size > 1) {
            existingItems.removeAt(0)
            joined = existingItems.joinToString(" | ")
        }
        return if (joined.length > MAX_SUMMARY_CHARS) {
            joined.takeLast(MAX_SUMMARY_CHARS).trimStart()
        } else {
            joined
        }
    }

    fun filterRelevantMemories(
        prompt: String,
        memories: List<SuruMemoryEntity>,
        maxItems: Int = 5
    ): List<SuruMemoryEntity> {
        val safeMemories = memories.filter {
            it.isEnabled &&
                it.keyLabel.isNotBlank() &&
                it.valueText.isNotBlank() &&
                !isSensitiveText(it.keyLabel) &&
                !isSensitiveText(it.valueText)
        }
        if (safeMemories.size <= maxItems) return safeMemories

        val tokens = prompt.lowercase(Locale.US)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
            .toSet()

        return safeMemories
            .sortedWith(
                compareByDescending<SuruMemoryEntity> { mem ->
                    val hay = "${mem.keyLabel} ${mem.valueText} ${mem.category}".lowercase(Locale.US)
                    val keywordHits = tokens.count { hay.contains(it) } * 10
                    val categoryBonus = when (mem.category) {
                        "GOAL", "TIME_PREF" -> 3
                        "STUDY_PREF", "WEAK_SUBJECT" -> 2
                        else -> 0
                    }
                    keywordHits + categoryBonus
                }.thenByDescending { it.updatedAtMillis }
            )
            .take(maxItems)
    }

    fun buildContextBlock(
        memoryState: SuruBoundedMemoryState,
        mood: String? = null,
        relevantMemories: List<SuruMemoryEntity> = emptyList()
    ): String {
        val parts = mutableListOf<String>()
        val safeMood = mood?.trim()?.takeIf { it.isNotEmpty() && !isSensitiveText(it) }
        if (safeMood != null) {
            parts.add("Current user mood: $safeMood")
        }
        if (memoryState.rollingSummary.isNotBlank()) {
            parts.add("Earlier conversation summary: ${memoryState.rollingSummary}")
        }
        if (relevantMemories.isNotEmpty()) {
            val prefsText = relevantMemories.joinToString("; ") { "${it.keyLabel}: ${it.valueText}" }
            parts.add("Relevant user preferences: $prefsText")
        }
        return parts.joinToString("\n")
    }

    private fun isLowValueFiller(text: String): Boolean {
        val lower = text.lowercase(Locale.US).trim('.', '!', '?')
        return lower in setOf("hi", "hello", "hey", "ok", "okay", "thanks", "thank you", "hmm")
    }

    private fun compressSnippet(text: String, maxChars: Int): String {
        val firstSentence = text.split(Regex("(?<=[.!?])\\s+")).firstOrNull()?.trim().orEmpty()
        val chosen = if (firstSentence.length in 12..maxChars) firstSentence else text
        return if (chosen.length > maxChars) {
            chosen.take((maxChars - 1).coerceAtLeast(12)).trimEnd() + "…"
        } else {
            chosen
        }
    }
}
