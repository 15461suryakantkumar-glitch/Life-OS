package com.example.suru

import android.content.Context
import com.example.BuildConfig
import com.example.data.local.AiProviderUsageEntity
import com.example.data.local.LifeOsDatabase
import com.example.data.local.UserPreferencesState
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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
        models = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash"),
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
    CONFIGURED_FIREBASE("Configured (Firebase)", true),
    CONFIGURED_PROXY("Configured (Proxy)", true),
    CONFIGURATION_REQUIRED("Configuration Required", false)
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

class SuruAiGateway(private val appContext: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    fun getProviderStatus(provider: SuruAiProvider): ProviderConnectionStatus {
        if (configuredProxyUrl() != null) {
            return ProviderConnectionStatus.CONFIGURED_PROXY
        }
        return when (provider) {
            SuruAiProvider.OPENAI -> if (configuredKey(BuildConfig.OPENAI_API_KEY) != null) {
                ProviderConnectionStatus.CONFIGURED_DIRECT
            } else ProviderConnectionStatus.CONFIGURATION_REQUIRED
            SuruAiProvider.ANTHROPIC -> if (configuredKey(BuildConfig.ANTHROPIC_API_KEY) != null) {
                ProviderConnectionStatus.CONFIGURED_DIRECT
            } else ProviderConnectionStatus.CONFIGURATION_REQUIRED
            SuruAiProvider.GEMINI -> when {
                configuredKey(BuildConfig.GEMINI_API_KEY) != null -> ProviderConnectionStatus.CONFIGURED_DIRECT
                isFirebaseAvailable() -> ProviderConnectionStatus.CONFIGURED_FIREBASE
                else -> ProviderConnectionStatus.CONFIGURATION_REQUIRED
            }
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
            val detail = when (st) {
                ProviderConnectionStatus.CONFIGURED_DIRECT -> "API key detected via BuildConfig / Secrets."
                ProviderConnectionStatus.CONFIGURED_FIREBASE -> "Connected via Firebase AI Logic."
                ProviderConnectionStatus.CONFIGURED_PROXY -> "Routed through configured SURU backend proxy."
                ProviderConnectionStatus.CONFIGURATION_REQUIRED -> "Add ${provider.name}_API_KEY or SURU_AI_PROXY_URL in AI Studio Secrets."
            }
            ProviderDiagnostic(provider, st, detail)
        }
    }

    fun hasAnyConfiguredProvider(): Boolean {
        return SuruAiProvider.entries.any { getProviderStatus(it).isReady }
    }

    fun selectExecutionChain(
        prefs: UserPreferencesState,
        taskKind: SuruRoutingTaskKind
    ): List<Pair<SuruAiProvider, String>> {
        val preferred = SuruAiProvider.fromStored(prefs.suruDefaultProvider)
        val preferredModel = prefs.suruDefaultModel.takeIf { it in preferred.models }
            ?: preferred.models.first()

        val configured = SuruAiProvider.entries.filter { getProviderStatus(it).isReady }
        if (configured.isEmpty()) return emptyList()

        val chain = LinkedHashSet<Pair<SuruAiProvider, String>>()

        if (!prefs.suruAutoRoutingEnabled) {
            if (getProviderStatus(preferred).isReady) {
                chain.add(preferred to preferredModel)
            }
        } else {
            val smartOrder = when (taskKind) {
                SuruRoutingTaskKind.VISION_MULTIMODAL -> listOf(
                    SuruAiProvider.GEMINI,
                    SuruAiProvider.OPENAI,
                    SuruAiProvider.ANTHROPIC
                )
                SuruRoutingTaskKind.PLANNING_REASONING,
                SuruRoutingTaskKind.STUDY_HELP -> listOf(
                    preferred,
                    SuruAiProvider.DEEPSEEK,
                    SuruAiProvider.ANTHROPIC,
                    SuruAiProvider.OPENAI,
                    SuruAiProvider.GEMINI
                )
                SuruRoutingTaskKind.CHAT -> listOf(
                    preferred,
                    SuruAiProvider.GEMINI,
                    SuruAiProvider.OPENAI,
                    SuruAiProvider.ANTHROPIC,
                    SuruAiProvider.XAI,
                    SuruAiProvider.DEEPSEEK
                )
            }
            smartOrder.filter { it in configured }.forEach { prov ->
                val model = if (prov == preferred) preferredModel else prov.models.first()
                if (taskKind != SuruRoutingTaskKind.VISION_MULTIMODAL || prov.supportsVision) {
                    chain.add(prov to model)
                }
            }
        }

        if (prefs.suruAutoFallbackEnabled) {
            configured.forEach { prov ->
                if (taskKind != SuruRoutingTaskKind.VISION_MULTIMODAL || prov.supportsVision) {
                    val model = if (prov == preferred) preferredModel else prov.models.first()
                    chain.add(prov to model)
                }
            }
        }
        return chain.toList()
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
        val chain = selectExecutionChain(prefs, taskKind)
        if (chain.isEmpty()) {
            throw IllegalStateException("No AI provider is configured. Add an API key or SURU_AI_PROXY_URL in AI Studio Secrets.")
        }

        var lastError: Throwable? = null
        val attemptedNames = mutableListOf<String>()

        for ((index, candidate) in chain.withIndex()) {
            val (provider, model) = candidate
            val startMs = System.currentTimeMillis()
            try {
                val raw = invokeSingleProvider(
                    provider = provider,
                    model = model,
                    systemInstruction = systemInstruction,
                    historyTurns = historyTurns,
                    userPrompt = userPrompt,
                    imageAttachment = imageAttachment,
                    expectJson = expectJson,
                    supabaseAccessToken = prefs.supabaseAccessToken
                )
                val latency = System.currentTimeMillis() - startMs
                logUsageAsync(
                    provider = provider,
                    model = model,
                    promptChars = systemInstruction.length + userPrompt.length,
                    responseChars = raw.length,
                    success = true,
                    latencyMs = latency
                )

                var reviewedBy: String? = null
                var finalOutput = raw
                if (prefs.suruSecondaryReviewEnabled &&
                    taskKind == SuruRoutingTaskKind.STUDY_HELP
                ) {
                    val secondaryProv = SuruAiProvider.fromStored(prefs.suruSecondaryProvider)
                    val secondaryModel = prefs.suruSecondaryModel.takeIf { it in secondaryProv.models }
                        ?: secondaryProv.models.first()
                    if (secondaryProv != provider && getProviderStatus(secondaryProv).isReady) {
                        runCatching {
                            val reviewPrompt = "Verify factual accuracy and clarity of this draft JSON response, keeping the exact same JSON schema and improving weak spots if needed:\n\n$raw"
                            val reviewed = invokeSingleProvider(
                                provider = secondaryProv,
                                model = secondaryModel,
                                systemInstruction = "Return valid JSON matching the exact schema provided.",
                                historyTurns = emptyList(),
                                userPrompt = reviewPrompt,
                                imageAttachment = null,
                                expectJson = expectJson,
                                supabaseAccessToken = prefs.supabaseAccessToken
                            )
                            if (reviewed.isNotBlank()) {
                                finalOutput = reviewed
                                reviewedBy = "${secondaryProv.shortName} ($secondaryModel)"
                            }
                        }
                    }
                }

                val fallbackNote = if (index > 0 && attemptedNames.isNotEmpty()) {
                    "Auto-fallback to ${provider.shortName} after ${attemptedNames.last()} was unavailable."
                } else null

                return GatewayCompletionResult(
                    rawText = finalOutput,
                    providerUsed = provider,
                    modelUsed = model,
                    secondaryReviewedBy = reviewedBy,
                    fallbackNote = fallbackNote
                )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                lastError = t
                attemptedNames.add(provider.shortName)
                val latency = System.currentTimeMillis() - startMs
                logUsageAsync(
                    provider = provider,
                    model = model,
                    promptChars = systemInstruction.length + userPrompt.length,
                    responseChars = 0,
                    success = false,
                    latencyMs = latency
                )
                if (!prefs.suruAutoFallbackEnabled) break
            }
        }

        throw lastError ?: IllegalStateException("All configured AI providers failed.")
    }

    private suspend fun invokeSingleProvider(
        provider: SuruAiProvider,
        model: String,
        systemInstruction: String,
        historyTurns: List<Pair<String, String>>,
        userPrompt: String,
        imageAttachment: SuruImageAttachment?,
        expectJson: Boolean,
        supabaseAccessToken: String
    ): String {
        val proxyUrl = configuredProxyUrl()
        if (proxyUrl != null) {
            return callProxyEndpoint(
                proxyUrl = proxyUrl,
                provider = provider,
                model = model,
                systemInstruction = systemInstruction,
                historyTurns = historyTurns,
                userPrompt = userPrompt,
                imageAttachment = imageAttachment,
                expectJson = expectJson,
                bearerToken = supabaseAccessToken
            )
        }

        return when (provider) {
            SuruAiProvider.GEMINI -> {
                val apiKey = configuredKey(BuildConfig.GEMINI_API_KEY)
                if (apiKey != null) {
                    callGeminiRest(apiKey, model, systemInstruction, historyTurns, userPrompt, imageAttachment, expectJson)
                } else {
                    callFirebaseGemini(model, systemInstruction, historyTurns, userPrompt, imageAttachment)
                }
            }
            SuruAiProvider.OPENAI -> {
                val key = configuredKey(BuildConfig.OPENAI_API_KEY)
                    ?: throw IllegalStateException("OPENAI_API_KEY is not configured.")
                callOpenAiCompatible(
                    endpoint = "https://api.openai.com/v1/chat/completions",
                    apiKey = key,
                    model = model,
                    systemInstruction = systemInstruction,
                    historyTurns = historyTurns,
                    userPrompt = userPrompt,
                    imageAttachment = imageAttachment,
                    expectJson = expectJson
                )
            }
            SuruAiProvider.XAI -> {
                val key = configuredKey(BuildConfig.XAI_API_KEY)
                    ?: throw IllegalStateException("XAI_API_KEY is not configured.")
                callOpenAiCompatible(
                    endpoint = "https://api.x.ai/v1/chat/completions",
                    apiKey = key,
                    model = model,
                    systemInstruction = systemInstruction,
                    historyTurns = historyTurns,
                    userPrompt = userPrompt,
                    imageAttachment = null,
                    expectJson = expectJson
                )
            }
            SuruAiProvider.DEEPSEEK -> {
                val key = configuredKey(BuildConfig.DEEPSEEK_API_KEY)
                    ?: throw IllegalStateException("DEEPSEEK_API_KEY is not configured.")
                callOpenAiCompatible(
                    endpoint = "https://api.deepseek.com/chat/completions",
                    apiKey = key,
                    model = model,
                    systemInstruction = systemInstruction,
                    historyTurns = historyTurns,
                    userPrompt = userPrompt,
                    imageAttachment = null,
                    expectJson = expectJson
                )
            }
            SuruAiProvider.ANTHROPIC -> {
                val key = configuredKey(BuildConfig.ANTHROPIC_API_KEY)
                    ?: throw IllegalStateException("ANTHROPIC_API_KEY is not configured.")
                callAnthropicMessages(key, model, systemInstruction, historyTurns, userPrompt, imageAttachment)
            }
        }
    }

    private suspend fun callProxyEndpoint(
        proxyUrl: String,
        provider: SuruAiProvider,
        model: String,
        systemInstruction: String,
        historyTurns: List<Pair<String, String>>,
        userPrompt: String,
        imageAttachment: SuruImageAttachment?,
        expectJson: Boolean,
        bearerToken: String
    ): String = withContext(Dispatchers.IO) {
        val bodyJson = JSONObject().apply {
            put("provider", provider.name)
            put("model", model)
            put("systemInstruction", systemInstruction)
            put("userPrompt", userPrompt)
            put("expectJson", expectJson)
            put("history", JSONArray().apply {
                historyTurns.forEach { (role, text) ->
                    put(JSONObject().put("role", role).put("text", text))
                }
            })
            if (imageAttachment != null) {
                put("imageBase64Jpeg", imageAttachment.base64Jpeg)
            }
        }
        val reqBuilder = Request.Builder()
            .url(proxyUrl)
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
        if (bearerToken.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer $bearerToken")
        }
        httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("AI proxy error HTTP ${resp.code}: ${raw.take(180)}")
            }
            val parsed = JSONObject(raw)
            parsed.optString("text").ifBlank { parsed.optString("output") }
                .ifBlank { throw IllegalStateException("Empty AI proxy response.") }
        }
    }

    private suspend fun callGeminiRest(
        apiKey: String,
        model: String,
        systemInstruction: String,
        historyTurns: List<Pair<String, String>>,
        userPrompt: String,
        imageAttachment: SuruImageAttachment?,
        expectJson: Boolean
    ): String = withContext(Dispatchers.IO) {
        val contentsArray = JSONArray()
        historyTurns.forEach { (role, text) ->
            contentsArray.put(
                JSONObject().put("role", if (role == "model") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", text)))
            )
        }
        val currentParts = JSONArray().put(
            JSONObject().put("text", userPrompt.ifBlank { "Analyze the attached image carefully." })
        )
        if (imageAttachment != null) {
            currentParts.put(
                JSONObject().put(
                    "inlineData",
                    JSONObject().put("mimeType", "image/jpeg").put("data", imageAttachment.base64Jpeg)
                )
            )
        }
        contentsArray.put(JSONObject().put("role", "user").put("parts", currentParts))

        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.35)
                put("maxOutputTokens", 1600)
                if (expectJson) put("responseMimeType", "application/json")
            })
        }

        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException(msg?.takeIf { it.isNotBlank() } ?: "Gemini HTTP ${resp.code}")
            }
            val root = JSONObject(body)
            val parts = root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: throw IllegalStateException("Gemini returned no candidates.")
            val out = StringBuilder()
            for (i in 0 until parts.length()) {
                out.append(parts.optJSONObject(i)?.optString("text").orEmpty())
            }
            out.toString().trim().ifBlank { throw IllegalStateException("Empty Gemini response.") }
        }
    }

    private suspend fun callFirebaseGemini(
        model: String,
        systemInstruction: String,
        historyTurns: List<Pair<String, String>>,
        userPrompt: String,
        imageAttachment: SuruImageAttachment?
    ): String {
        val generativeModel = Firebase.ai.generativeModel(modelName = model)
        val combinedPrompt = buildString {
            appendLine(systemInstruction)
            if (historyTurns.isNotEmpty()) {
                appendLine("\nRecent conversation:")
                historyTurns.forEach { (r, t) -> appendLine("$r: $t") }
            }
            appendLine("\nCurrent user message: ${userPrompt.ifBlank { "Analyze this attached image." }}")
        }
        val response = withTimeout(32_000L) {
            if (imageAttachment != null) {
                generativeModel.generateContent(
                    content {
                        image(imageAttachment.previewBitmap)
                        text(combinedPrompt)
                    }
                )
            } else {
                generativeModel.generateContent(combinedPrompt)
            }
        }
        return response.text?.trim().orEmpty().ifBlank {
            throw IllegalStateException("Firebase AI returned an empty response.")
        }
    }

    private suspend fun callOpenAiCompatible(
        endpoint: String,
        apiKey: String,
        model: String,
        systemInstruction: String,
        historyTurns: List<Pair<String, String>>,
        userPrompt: String,
        imageAttachment: SuruImageAttachment?,
        expectJson: Boolean
    ): String = withContext(Dispatchers.IO) {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemInstruction))
        historyTurns.forEach { (role, text) ->
            val mapped = if (role == "model" || role == "assistant") "assistant" else "user"
            messages.put(JSONObject().put("role", mapped).put("content", text))
        }
        if (imageAttachment != null) {
            val contentArray = JSONArray()
                .put(JSONObject().put("type", "text").put("text", userPrompt.ifBlank { "Analyze this image." }))
                .put(
                    JSONObject().put("type", "image_url").put(
                        "image_url",
                        JSONObject().put("url", "data:image/jpeg;base64,${imageAttachment.base64Jpeg}")
                    )
                )
            messages.put(JSONObject().put("role", "user").put("content", contentArray))
        } else {
            messages.put(JSONObject().put("role", "user").put("content", userPrompt))
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            if (!model.startsWith("o") && !model.contains("reasoner")) {
                put("temperature", 0.35)
            }
            if (expectJson && endpoint.contains("openai.com")) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
        }

        val req = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException(msg?.takeIf { it.isNotBlank() } ?: "Provider HTTP ${resp.code}")
            }
            val root = JSONObject(body)
            val content = root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
            content?.ifBlank { null } ?: throw IllegalStateException("Provider returned empty message.")
        }
    }

    private suspend fun callAnthropicMessages(
        apiKey: String,
        model: String,
        systemInstruction: String,
        historyTurns: List<Pair<String, String>>,
        userPrompt: String,
        imageAttachment: SuruImageAttachment?
    ): String = withContext(Dispatchers.IO) {
        val messages = JSONArray()
        historyTurns.forEach { (role, text) ->
            val mapped = if (role == "model" || role == "assistant") "assistant" else "user"
            messages.put(JSONObject().put("role", mapped).put("content", text))
        }
        val currentContent = if (imageAttachment != null) {
            JSONArray()
                .put(
                    JSONObject().put("type", "image").put(
                        "source",
                        JSONObject()
                            .put("type", "base64")
                            .put("media_type", "image/jpeg")
                            .put("data", imageAttachment.base64Jpeg)
                    )
                )
                .put(JSONObject().put("type", "text").put("text", userPrompt.ifBlank { "Analyze this image." }))
        } else {
            userPrompt
        }
        messages.put(JSONObject().put("role", "user").put("content", currentContent))

        val payload = JSONObject().apply {
            put("model", model)
            put("max_tokens", 1600)
            put("system", systemInstruction)
            put("messages", messages)
        }

        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException(msg?.takeIf { it.isNotBlank() } ?: "Anthropic HTTP ${resp.code}")
            }
            val root = JSONObject(body)
            val contentArr = root.optJSONArray("content") ?: throw IllegalStateException("Anthropic returned empty content.")
            val out = StringBuilder()
            for (i in 0 until contentArr.length()) {
                val block = contentArr.optJSONObject(i) ?: continue
                if (block.optString("type") == "text") {
                    out.append(block.optString("text"))
                }
            }
            out.toString().trim().ifBlank { throw IllegalStateException("Empty Anthropic response.") }
        }
    }

    private suspend fun logUsageAsync(
        provider: SuruAiProvider,
        model: String,
        promptChars: Int,
        responseChars: Int,
        success: Boolean,
        latencyMs: Long
    ) {
        withContext(Dispatchers.IO) {
            runCatching {
                LifeOsDatabase.getInstance(appContext).aiProviderUsageDao().insert(
                    AiProviderUsageEntity(
                        providerName = provider.name,
                        modelId = model,
                        promptTokensEst = (promptChars / 4).coerceAtLeast(1),
                        responseTokensEst = (responseChars / 4).coerceAtLeast(0),
                        success = success,
                        latencyMs = latencyMs
                    )
                )
            }
        }
    }

    private fun isFirebaseAvailable(): Boolean {
        return runCatching { FirebaseApp.getApps(appContext).isNotEmpty() }.getOrDefault(false)
    }

    companion object {
        fun configuredKey(rawValue: String?): String? {
            val raw = rawValue?.trim().orEmpty()
            if (raw.isBlank() || raw == "your_api_key_here" || raw.startsWith("YOUR_")) {
                return null
            }
            return raw
        }

        fun configuredProxyUrl(): String? {
            val raw = runCatching { BuildConfig.SURU_AI_PROXY_URL.trim() }.getOrDefault("")
            return raw.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        }
    }
}
