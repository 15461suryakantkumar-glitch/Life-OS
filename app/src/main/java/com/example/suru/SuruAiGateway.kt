package com.example.suru

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.AiProviderUsageEntity
import com.example.data.local.LifeOsDatabase
import com.example.data.local.UserPreferencesState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SuruAiProvider(
    val displayName: String,
    val shortName: String,
    val models: List<String>,
    val supportsVision: Boolean
) {
    OPENAI(
        displayName = "OpenAI (GPT)",
        shortName = "GPT",
        models = listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "o4-mini"),
        supportsVision = true
    ),
    ANTHROPIC(
        displayName = "Anthropic (Claude)",
        shortName = "Claude",
        models = listOf("claude-3-5-haiku-latest", "claude-sonnet-4-20250514", "claude-3-7-sonnet-latest"),
        supportsVision = true
    ),
    GEMINI(
        displayName = "Google (Gemini)",
        shortName = "Gemini",
        models = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon"),
        supportsVision = true
    ),
    XAI(
        displayName = "xAI (Grok)",
        shortName = "Grok",
        models = listOf("grok-3-mini", "grok-3"),
        supportsVision = false
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        shortName = "DeepSeek",
        models = listOf("deepseek-chat", "deepseek-reasoner"),
        supportsVision = false
    );

    companion object {
        fun fromStored(raw: String?): SuruAiProvider {
            return entries.firstOrNull { it.name == raw } ?: GEMINI
        }
    }
}

enum class ProviderConnectionStatus(val label: String, val isReady: Boolean) {
    CONFIGURED_DIRECT("Configured", true),
    CONFIGURED_FIREBASE("Configured", true),
    CONFIGURED_PROXY("Configured", true),
    CONFIGURATION_REQUIRED("Unavailable", false)
}

enum class SuruRoutingTaskKind {
    CHAT,
    PLANNING_REASONING,
    STUDY_HELP,
    VISION_MULTIMODAL
}

data class ProviderDiagnostic(
    val provider: SuruAiProvider,
    val status: ProviderConnectionStatus,
    val detail: String
)

data class GatewayCompletionResult(
    val rawText: String,
    val providerUsed: SuruAiProvider,
    val modelUsed: String,
    val secondaryReviewedBy: String? = null,
    val fallbackNote: String? = null
)

/**
 * Zero-Supabase SURU AI gateway delegating strictly to OpenRouter first -> Gemini second.
 * Never surfaces backend/provider details, fallback notices, or raw error strings to the UI.
 */
class SuruAiGateway(private val appContext: Context) {

    fun getProviderStatus(provider: SuruAiProvider): ProviderConnectionStatus {
        return when (provider) {
            SuruAiProvider.OPENAI -> if (configuredKey(BuildConfig.OPENAI_API_KEY) != null) {
                ProviderConnectionStatus.CONFIGURED_DIRECT
            } else ProviderConnectionStatus.CONFIGURATION_REQUIRED
            SuruAiProvider.ANTHROPIC -> if (configuredKey(BuildConfig.ANTHROPIC_API_KEY) != null) {
                ProviderConnectionStatus.CONFIGURED_DIRECT
            } else ProviderConnectionStatus.CONFIGURATION_REQUIRED
            SuruAiProvider.GEMINI -> if (SuruAiRouter.currentGeminiKey() != null) {
                ProviderConnectionStatus.CONFIGURED_DIRECT
            } else ProviderConnectionStatus.CONFIGURATION_REQUIRED
            SuruAiProvider.XAI -> if (configuredKey(BuildConfig.XAI_API_KEY) != null) {
                ProviderConnectionStatus.CONFIGURED_DIRECT
            } else ProviderConnectionStatus.CONFIGURATION_REQUIRED
            SuruAiProvider.DEEPSEEK -> if (configuredKey(BuildConfig.DEEPSEEK_API_KEY) != null) {
                ProviderConnectionStatus.CONFIGURED_DIRECT
            } else ProviderConnectionStatus.CONFIGURATION_REQUIRED
        }
    }

    fun getAllDiagnostics(): List<ProviderDiagnostic> {
        return SuruAiProvider.entries.map { provider ->
            val st = getProviderStatus(provider)
            val detail = if (st.isReady) "Ready" else "Optional"
            ProviderDiagnostic(provider, st, detail)
        }
    }

    fun hasAnyConfiguredProvider(): Boolean {
        return SuruAiRouter.hasAnyRouteConfigured(
            openRouterKey = SuruAiRouter.currentOpenRouterKey(),
            geminiKey = SuruAiRouter.currentGeminiKey()
        )
    }

    suspend fun generateCompletion(
        prefs: UserPreferencesState,
        taskKind: SuruRoutingTaskKind,
        systemInstruction: String,
        historyTurns: List<Pair<String, String>>,
        userPrompt: String,
        imageAttachment: SuruImageAttachment? = null,
        expectJson: Boolean = true
    ): GatewayCompletionResult {
        val bounded = SuruBoundedMemory.fromHistory(
            turns = historyTurns.map { (role, text) ->
                SuruTurn(isFromUser = role == "user", text = text)
            }
        )
        return generateCompletionBounded(
            systemInstruction = systemInstruction,
            memoryState = bounded,
            userPrompt = userPrompt,
            imageAttachment = imageAttachment,
            expectJson = expectJson
        )
    }

    suspend fun generateCompletionBounded(
        systemInstruction: String,
        memoryState: SuruBoundedMemoryState,
        userPrompt: String,
        imageAttachment: SuruImageAttachment? = null,
        expectJson: Boolean = true
    ): GatewayCompletionResult {
        val startMs = System.currentTimeMillis()
        try {
            when (
                val outcome = SuruAiRouter.completeWithFallback(
                    systemInstruction = systemInstruction,
                    memoryState = memoryState,
                    userPrompt = userPrompt,
                    imageAttachment = imageAttachment,
                    expectJson = expectJson
                )
            ) {
                is SuruRouteOutcome.Success -> {
                    val latency = System.currentTimeMillis() - startMs
                    logUsageAsync(
                        promptChars = systemInstruction.length + userPrompt.length,
                        responseChars = outcome.text.length,
                        success = true,
                        latencyMs = latency
                    )
                    return GatewayCompletionResult(
                        rawText = outcome.text,
                        providerUsed = SuruAiProvider.GEMINI,
                        modelUsed = SuruAiRouter.GEMINI_MODEL_ID,
                        secondaryReviewedBy = null,
                        fallbackNote = null
                    )
                }
                is SuruRouteOutcome.Unavailable -> {
                    val latency = System.currentTimeMillis() - startMs
                    logUsageAsync(
                        promptChars = systemInstruction.length + userPrompt.length,
                        responseChars = 0,
                        success = false,
                        latencyMs = latency
                    )
                    throw IllegalStateException(SuruAiRouter.CALM_UNAVAILABLE_MESSAGE)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e("SuruAiGateway", "SURU completion unavailable", t)
            throw IllegalStateException(SuruAiRouter.CALM_UNAVAILABLE_MESSAGE)
        }
    }

    private suspend fun logUsageAsync(
        promptChars: Int,
        responseChars: Int,
        success: Boolean,
        latencyMs: Long
    ) {
        withContext(Dispatchers.IO) {
            runCatching {
                LifeOsDatabase.getInstance(appContext).aiProviderUsageDao().insert(
                    AiProviderUsageEntity(
                        providerName = "SURU",
                        modelId = "default",
                        promptTokensEst = (promptChars / 4).coerceAtLeast(1),
                        responseTokensEst = (responseChars / 4).coerceAtLeast(0),
                        success = success,
                        latencyMs = latencyMs
                    )
                )
            }
        }
    }

    companion object {
        fun configuredKey(rawValue: String?): String? = SuruAiRouter.configuredSecret(rawValue)

        fun configuredProxyUrl(): String? = null
    }
}
