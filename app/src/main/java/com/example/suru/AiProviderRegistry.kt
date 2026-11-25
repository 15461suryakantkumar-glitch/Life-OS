package com.example.suru

import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SuruAiProvider(
    val displayName: String,
    val shortName: String,
    val envVarName: String,
    val defaultBaseUrl: String,
    val models: List<String>,
    val supportsVision: Boolean,
    val supportsStreaming: Boolean = true
) {
    OPENAI(
        displayName = "OpenAI (GPT)",
        shortName = "GPT",
        envVarName = "OPENAI_API_KEY",
        defaultBaseUrl = "https://api.openai.com/v1",
        models = listOf("gpt-4.1-mini", "gpt-4.1", "gpt-4o-mini", "gpt-4o", "o4-mini"),
        supportsVision = true
    ),
    ANTHROPIC(
        displayName = "Anthropic (Claude)",
        shortName = "Claude",
        envVarName = "ANTHROPIC_API_KEY",
        defaultBaseUrl = "https://api.anthropic.com",
        models = listOf("claude-sonnet-4-20250514", "claude-3-7-sonnet-latest", "claude-3-5-haiku-latest"),
        supportsVision = true
    ),
    GEMINI(
        displayName = "Google (Gemini)",
        shortName = "Gemini",
        envVarName = "GEMINI_API_KEY",
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
        models = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash"),
        supportsVision = true
    ),
    XAI(
        displayName = "xAI (Grok)",
        shortName = "Grok",
        envVarName = "XAI_API_KEY",
        defaultBaseUrl = "https://api.x.ai/v1",
        models = listOf("grok-3-mini", "grok-3", "grok-3-fast"),
        supportsVision = false
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        shortName = "DeepSeek",
        envVarName = "DEEPSEEK_API_KEY",
        defaultBaseUrl = "https://api.deepseek.com",
        models = listOf("deepseek-chat", "deepseek-reasoner"),
        supportsVision = false
    );

    companion object {
        fun fromStored(raw: String?): SuruAiProvider {
            return entries.firstOrNull { it.name == raw } ?: GEMINI
        }
    }
}

enum class AiErrorCategory(val label: String, val isRetryableByDefault: Boolean) {
    NONE("None", false),
    NOT_CONFIGURED("Not Configured", false),
    AUTH_ERROR("Authentication Error", false),
    RATE_LIMIT("Rate Limited", true),
    INSUFFICIENT_CREDITS("Insufficient Credits", false),
    MODEL_UNAVAILABLE("Model Unavailable", false),
    NETWORK_TIMEOUT("Network Timeout", true),
    SERVER_ERROR("Provider Server Error", true),
    INVALID_RESPONSE("Invalid Response", false)
}

class AiGatewayException(
    val provider: SuruAiProvider,
    val category: AiErrorCategory,
    val httpCode: Int? = null,
    val retryable: Boolean = category.isRetryableByDefault,
    val safeDetail: String,
    cause: Throwable? = null
) : IllegalStateException(safeDetail, cause)

enum class ProviderConfigState(val label: String) {
    API_KEY_DETECTED("API Key Configured"),
    PROXY_CONFIGURED("Proxy Configured"),
    FIREBASE_FALLBACK_ONLY("Firebase Fallback (API Key Not Set)"),
    NOT_CONFIGURED("Not Configured")
}

enum class ProviderConnectionStatus(
    val label: String,
    val isReady: Boolean,
    val isLiveVerified: Boolean
) {
    CONNECTED("Connected", isReady = true, isLiveVerified = true),
    CONFIGURED_UNTESTED("Configured · Untested", isReady = true, isLiveVerified = false),
    TESTING("Testing...", isReady = true, isLiveVerified = false),
    CONNECTION_FAILED("Connection Failed", isReady = true, isLiveVerified = false),
    NOT_CONFIGURED("Not Configured", isReady = false, isLiveVerified = false)
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
    val configState: ProviderConfigState = if (status.isReady) ProviderConfigState.API_KEY_DETECTED else ProviderConfigState.NOT_CONFIGURED,
    val apiConfigLabel: String = configState.label,
    val selectedModel: String = provider.models.first(),
    val availableModels: List<String> = provider.models,
    val detail: String,
    val lastConnectedAtMillis: Long? = null,
    val lastErrorMessage: String? = null,
    val lastErrorCategory: AiErrorCategory = AiErrorCategory.NONE,
    val isTesting: Boolean = false
)

data class GatewayCompletionResult(
    val rawText: String,
    val providerUsed: SuruAiProvider,
    val modelUsed: String,
    val secondaryReviewedBy: String? = null,
    val fallbackNote: String? = null,
    val routingNote: String? = null
)

sealed class AiGatewayStreamEvent {
    data class Delta(
        val provider: SuruAiProvider,
        val model: String,
        val chunk: String,
        val accumulatedText: String,
        val fallbackNote: String? = null,
        val routingNote: String? = null
    ) : AiGatewayStreamEvent()

    data class Completed(val result: GatewayCompletionResult) : AiGatewayStreamEvent()
}

data class AiProviderEndpointConfig(
    val openAiBaseUrl: String = SuruAiProvider.OPENAI.defaultBaseUrl,
    val anthropicBaseUrl: String = SuruAiProvider.ANTHROPIC.defaultBaseUrl,
    val geminiBaseUrl: String = SuruAiProvider.GEMINI.defaultBaseUrl,
    val xaiBaseUrl: String = SuruAiProvider.XAI.defaultBaseUrl,
    val deepSeekBaseUrl: String = SuruAiProvider.DEEPSEEK.defaultBaseUrl
) {
    fun baseUrlFor(provider: SuruAiProvider): String {
        return when (provider) {
            SuruAiProvider.OPENAI -> openAiBaseUrl
            SuruAiProvider.ANTHROPIC -> anthropicBaseUrl
            SuruAiProvider.GEMINI -> geminiBaseUrl
            SuruAiProvider.XAI -> xaiBaseUrl
            SuruAiProvider.DEEPSEEK -> deepSeekBaseUrl
        }.trimEnd('/')
    }
}

interface AiCredentialProvider {
    fun getApiKey(provider: SuruAiProvider): String?
    fun getProxyUrl(): String?
}

class BuildConfigAiCredentialProvider : AiCredentialProvider {
    override fun getApiKey(provider: SuruAiProvider): String? {
        val raw = when (provider) {
            SuruAiProvider.OPENAI -> runCatching { BuildConfig.OPENAI_API_KEY }.getOrDefault("")
            SuruAiProvider.ANTHROPIC -> runCatching { BuildConfig.ANTHROPIC_API_KEY }.getOrDefault("")
            SuruAiProvider.GEMINI -> runCatching { BuildConfig.GEMINI_API_KEY }.getOrDefault("")
            SuruAiProvider.XAI -> runCatching { BuildConfig.XAI_API_KEY }.getOrDefault("")
            SuruAiProvider.DEEPSEEK -> runCatching { BuildConfig.DEEPSEEK_API_KEY }.getOrDefault("")
        }
        return normalizeValidCredential(raw)
    }

    override fun getProxyUrl(): String? {
        val raw = runCatching { BuildConfig.SURU_AI_PROXY_URL.trim() }.getOrDefault("")
        return raw.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    companion object {
        fun normalizeValidCredential(rawValue: String?): String? {
            val raw = rawValue?.trim().orEmpty()
            if (raw.isBlank()) return null
            val lower = raw.lowercase()
            if (lower == "your_api_key_here" ||
                lower.startsWith("your_") ||
                lower.contains("placeholder") ||
                lower == "changeme" ||
                lower == "replace_me"
            ) {
                return null
            }
            return raw
        }
    }
}

class AiModelRegistry {
    private val _modelsByProvider = MutableStateFlow(
        SuruAiProvider.entries.associateWith { it.models }
    )
    val modelsByProvider: StateFlow<Map<SuruAiProvider, List<String>>> = _modelsByProvider.asStateFlow()

    fun getModels(provider: SuruAiProvider): List<String> {
        return _modelsByProvider.value[provider] ?: provider.models
    }

    fun updateDiscoveredModels(provider: SuruAiProvider, discoveredIds: List<String>) {
        val sanitized = discoveredIds
            .map { it.trim() }
            .filter { it.isNotBlank() && isSupportedModelCandidate(provider, it) }
            .distinct()
        if (sanitized.isEmpty()) return

        // Keep curated defaults prioritized, appending newly discovered supported models
        val merged = (provider.models + sanitized).distinct().take(20)
        _modelsByProvider.update { current ->
            current + (provider to merged)
        }
    }

    fun resolveSelectedModel(
        provider: SuruAiProvider,
        perProviderSelections: Map<SuruAiProvider, String>,
        fallbackDefaultModel: String? = null,
        isPrimaryProvider: Boolean = false
    ): String {
        val known = getModels(provider)
        val storedForProvider = perProviderSelections[provider]?.trim()?.takeIf { it.isNotBlank() }
        if (storedForProvider != null) return storedForProvider
        if (isPrimaryProvider && !fallbackDefaultModel.isNullOrBlank() && fallbackDefaultModel in known) {
            return fallbackDefaultModel
        }
        return known.firstOrNull() ?: provider.models.first()
    }

    companion object {
        fun isSupportedModelCandidate(provider: SuruAiProvider, modelId: String): Boolean {
            val lower = modelId.lowercase()
            return when (provider) {
                SuruAiProvider.OPENAI ->
                    (lower.startsWith("gpt-4") || lower.startsWith("gpt-5") || lower.startsWith("o3") || lower.startsWith("o4")) &&
                        !lower.contains("audio") &&
                        !lower.contains("realtime") &&
                        !lower.contains("tts") &&
                        !lower.contains("transcribe") &&
                        !lower.contains("image")
                SuruAiProvider.ANTHROPIC -> lower.startsWith("claude-")
                SuruAiProvider.GEMINI ->
                    lower.startsWith("gemini-") &&
                        !lower.contains("embedding") &&
                        !lower.contains("aqa") &&
                        !lower.contains("gemini-1.0")
                SuruAiProvider.XAI -> lower.startsWith("grok-") && !lower.contains("image")
                SuruAiProvider.DEEPSEEK -> lower.startsWith("deepseek-")
            }
        }
    }
}

object AiSecretSanitizer {
    private val keyPattern = Regex("""(sk-[A-Za-z0-9_\-]{8,}|AIza[0-9A-Za-z\-_]{12,}|xai-[A-Za-z0-9_\-]{8,}|Bearer\s+[A-Za-z0-9._\-]{8,}|[?&]key=[A-Za-z0-9_\-]{8,})""")

    fun sanitize(raw: String?, knownSecrets: Collection<String> = emptyList()): String {
        var clean = raw.orEmpty().trim()
        knownSecrets.filter { it.length >= 6 }.forEach { secret ->
            clean = clean.replace(secret, "[REDACTED]")
        }
        clean = keyPattern.replace(clean) { match ->
            val v = match.value
            when {
                v.startsWith("Bearer ", ignoreCase = true) -> "Bearer [REDACTED]"
                v.contains("key=") -> "?key=[REDACTED]"
                else -> "[REDACTED]"
            }
        }
        return clean
    }
}
