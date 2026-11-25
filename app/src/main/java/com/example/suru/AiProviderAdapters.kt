package com.example.suru

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException

internal object AiProviderAdapters {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    const val ANTHROPIC_VERSION = "2023-06-01"

    fun buildChatRequest(
        provider: SuruAiProvider,
        baseUrl: String,
        apiKey: String,
        model: String,
        systemInstruction: String,
        historyTurns: List<Pair<String, String>>,
        userPrompt: String,
        imageAttachment: SuruImageAttachment?,
        expectJson: Boolean,
        stream: Boolean,
        maxTokens: Int = 1600
    ): Request {
        val cleanBase = baseUrl.trimEnd('/')
        return when (provider) {
            SuruAiProvider.OPENAI,
            SuruAiProvider.XAI,
            SuruAiProvider.DEEPSEEK -> {
                val url = "$cleanBase/chat/completions"
                val payload = buildOpenAiCompatiblePayload(
                    provider = provider,
                    model = model,
                    systemInstruction = systemInstruction,
                    historyTurns = historyTurns,
                    userPrompt = userPrompt,
                    imageAttachment = imageAttachment,
                    expectJson = expectJson,
                    stream = stream,
                    maxTokens = maxTokens
                )
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Accept", if (stream) "text/event-stream" else "application/json")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()
            }

            SuruAiProvider.ANTHROPIC -> {
                val url = "$cleanBase/v1/messages"
                val payload = buildAnthropicPayload(
                    model = model,
                    systemInstruction = systemInstruction,
                    historyTurns = historyTurns,
                    userPrompt = userPrompt,
                    imageAttachment = imageAttachment,
                    stream = stream,
                    maxTokens = maxTokens
                )
                Request.Builder()
                    .url(url)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .header("Accept", if (stream) "text/event-stream" else "application/json")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()
            }

            SuruAiProvider.GEMINI -> {
                val action = if (stream) "streamGenerateContent?alt=sse" else "generateContent"
                val url = "$cleanBase/v1beta/models/$model:$action"
                val payload = buildGeminiPayload(
                    systemInstruction = systemInstruction,
                    historyTurns = historyTurns,
                    userPrompt = userPrompt,
                    imageAttachment = imageAttachment,
                    expectJson = expectJson && !stream,
                    maxTokens = maxTokens
                )
                Request.Builder()
                    .url(url)
                    .header("x-goog-api-key", apiKey)
                    .header("Accept", if (stream) "text/event-stream" else "application/json")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()
            }
        }
    }

    fun buildListModelsRequest(
        provider: SuruAiProvider,
        baseUrl: String,
        apiKey: String
    ): Request {
        val cleanBase = baseUrl.trimEnd('/')
        return when (provider) {
            SuruAiProvider.OPENAI,
            SuruAiProvider.XAI,
            SuruAiProvider.DEEPSEEK -> Request.Builder()
                .url("$cleanBase/models")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            SuruAiProvider.ANTHROPIC -> Request.Builder()
                .url("$cleanBase/v1/models")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .get()
                .build()

            SuruAiProvider.GEMINI -> Request.Builder()
                .url("$cleanBase/v1beta/models")
                .header("x-goog-api-key", apiKey)
                .get()
                .build()
        }
    }

    fun parseModelsResponse(provider: SuruAiProvider, rawBody: String): List<String> {
        return runCatching {
            val root = JSONObject(rawBody)
            when (provider) {
                SuruAiProvider.GEMINI -> {
                    val arr = root.optJSONArray("models") ?: return@runCatching emptyList()
                    buildList {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val name = obj.optString("name").removePrefix("models/").trim()
                            val methods = obj.optJSONArray("supportedGenerationMethods")
                            val supportsGen = methods == null || (0 until methods.length()).any {
                                methods.optString(it) == "generateContent"
                            }
                            if (name.isNotBlank() && supportsGen) add(name)
                        }
                    }
                }
                SuruAiProvider.OPENAI,
                SuruAiProvider.ANTHROPIC,
                SuruAiProvider.XAI,
                SuruAiProvider.DEEPSEEK -> {
                    val arr = root.optJSONArray("data") ?: return@runCatching emptyList()
                    buildList {
                        for (i in 0 until arr.length()) {
                            val id = arr.optJSONObject(i)?.optString("id")?.trim().orEmpty()
                            if (id.isNotBlank()) add(id)
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun parseCompletionResponse(provider: SuruAiProvider, rawBody: String): String {
        val root = runCatching { JSONObject(rawBody) }.getOrElse {
            throw AiGatewayException(
                provider = provider,
                category = AiErrorCategory.INVALID_RESPONSE,
                safeDetail = "${provider.displayName} returned malformed JSON."
            )
        }
        return when (provider) {
            SuruAiProvider.OPENAI,
            SuruAiProvider.XAI,
            SuruAiProvider.DEEPSEEK -> {
                val msg = root.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                val text = msg?.optString("content")?.trim().orEmpty()
                text.ifBlank {
                    throw AiGatewayException(
                        provider = provider,
                        category = AiErrorCategory.INVALID_RESPONSE,
                        safeDetail = "${provider.displayName} returned an empty response."
                    )
                }
            }

            SuruAiProvider.ANTHROPIC -> {
                val arr = root.optJSONArray("content") ?: throw AiGatewayException(
                    provider = provider,
                    category = AiErrorCategory.INVALID_RESPONSE,
                    safeDetail = "Anthropic returned empty content blocks."
                )
                val sb = StringBuilder()
                for (i in 0 until arr.length()) {
                    val block = arr.optJSONObject(i) ?: continue
                    if (block.optString("type") == "text") {
                        sb.append(block.optString("text"))
                    }
                }
                sb.toString().trim().ifBlank {
                    throw AiGatewayException(
                        provider = provider,
                        category = AiErrorCategory.INVALID_RESPONSE,
                        safeDetail = "Anthropic returned an empty text message."
                    )
                }
            }

            SuruAiProvider.GEMINI -> {
                val parts = root.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?: throw AiGatewayException(
                        provider = provider,
                        category = AiErrorCategory.INVALID_RESPONSE,
                        safeDetail = "Gemini returned no candidates."
                    )
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    sb.append(parts.optJSONObject(i)?.optString("text").orEmpty())
                }
                sb.toString().trim().ifBlank {
                    throw AiGatewayException(
                        provider = provider,
                        category = AiErrorCategory.INVALID_RESPONSE,
                        safeDetail = "Gemini returned an empty response."
                    )
                }
            }
        }
    }

    fun parseSseDataLine(provider: SuruAiProvider, dataJson: String): String? {
        val trimmed = dataJson.trim()
        if (trimmed.isBlank() || trimmed == "[DONE]") return null
        val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        // Check inline SSE error events
        obj.optJSONObject("error")?.let { errObj ->
            val msg = errObj.optString("message").ifBlank { "Streaming error" }
            throw AiGatewayException(
                provider = provider,
                category = classifyMessageCategory(null, msg),
                safeDetail = "${provider.displayName}: ${AiSecretSanitizer.sanitize(msg)}"
            )
        }
        return when (provider) {
            SuruAiProvider.OPENAI,
            SuruAiProvider.XAI,
            SuruAiProvider.DEEPSEEK -> {
                val delta = obj.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("delta")
                delta?.optString("content")?.takeIf { it.isNotEmpty() }
            }

            SuruAiProvider.ANTHROPIC -> {
                when (obj.optString("type")) {
                    "content_block_delta" -> {
                        val delta = obj.optJSONObject("delta")
                        if (delta?.optString("type") == "text_delta") {
                            delta.optString("text").takeIf { it.isNotEmpty() }
                        } else null
                    }
                    else -> null
                }
            }

            SuruAiProvider.GEMINI -> {
                val parts = obj.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts") ?: return null
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    sb.append(parts.optJSONObject(i)?.optString("text").orEmpty())
                }
                sb.toString().takeIf { it.isNotEmpty() }
            }
        }
    }

    fun classifyHttpFailure(
        provider: SuruAiProvider,
        model: String,
        httpCode: Int,
        rawBody: String,
        knownSecrets: Collection<String> = emptyList()
    ): AiGatewayException {
        val rawMsg = runCatching {
            val root = JSONObject(rawBody)
            root.optJSONObject("error")?.optString("message")
                ?.ifBlank { null }
                ?: root.optString("message").ifBlank { null }
        }.getOrNull().orEmpty()

        val cleanMsg = AiSecretSanitizer.sanitize(rawMsg, knownSecrets)
        val category = classifyMessageCategory(httpCode, cleanMsg.ifBlank { rawBody })
        val userDetail = when (category) {
            AiErrorCategory.AUTH_ERROR ->
                "${provider.displayName} authentication failed (HTTP $httpCode). Check ${provider.envVarName} in AI Studio Secrets."
            AiErrorCategory.INSUFFICIENT_CREDITS ->
                "${provider.displayName} has insufficient credits or exhausted billing quota (HTTP $httpCode)."
            AiErrorCategory.RATE_LIMIT ->
                "${provider.displayName} rate limit reached (HTTP $httpCode). ${cleanMsg.take(120)}".trim()
            AiErrorCategory.MODEL_UNAVAILABLE ->
                "Model '$model' is not available on ${provider.displayName} (HTTP $httpCode)."
            AiErrorCategory.NETWORK_TIMEOUT ->
                "${provider.displayName} timed out (HTTP $httpCode)."
            AiErrorCategory.SERVER_ERROR ->
                "${provider.displayName} server error (HTTP $httpCode). ${cleanMsg.take(120)}".trim()
            else ->
                "${provider.displayName} error (HTTP $httpCode)${if (cleanMsg.isNotBlank()) ": ${cleanMsg.take(160)}" else ""}"
        }
        return AiGatewayException(
            provider = provider,
            category = category,
            httpCode = httpCode,
            retryable = category.isRetryableByDefault,
            safeDetail = AiSecretSanitizer.sanitize(userDetail, knownSecrets)
        )
    }

    fun classifyThrowable(
        provider: SuruAiProvider,
        t: Throwable,
        knownSecrets: Collection<String> = emptyList()
    ): AiGatewayException {
        if (t is AiGatewayException) return t
        val clean = AiSecretSanitizer.sanitize(t.message, knownSecrets)
        val category = when (t) {
            is SocketTimeoutException -> AiErrorCategory.NETWORK_TIMEOUT
            is IOException -> AiErrorCategory.NETWORK_TIMEOUT
            else -> classifyMessageCategory(null, clean)
        }
        val detail = clean.ifBlank { "${provider.displayName} connection failed." }
        return AiGatewayException(
            provider = provider,
            category = category,
            httpCode = null,
            retryable = category.isRetryableByDefault,
            safeDetail = detail,
            cause = t
        )
    }

    fun classifyMessageCategory(httpCode: Int?, message: String): AiErrorCategory {
        val lower = message.lowercase()
        val billingKeywords = listOf(
            "insufficient_quota",
            "insufficient quota",
            "credit balance",
            "insufficient balance",
            "balance is insufficient",
            "exceeded your current quota",
            "billing",
            "payment required",
            "out of credits"
        )
        if (httpCode == 402 || billingKeywords.any { lower.contains(it) }) {
            return AiErrorCategory.INSUFFICIENT_CREDITS
        }
        if (httpCode == 401 || httpCode == 403 ||
            lower.contains("invalid api key") ||
            lower.contains("incorrect api key") ||
            lower.contains("api key not valid") ||
            lower.contains("authentication_error") ||
            lower.contains("unauthorized")
        ) {
            return AiErrorCategory.AUTH_ERROR
        }
        if (httpCode == 429 || lower.contains("rate limit") || lower.contains("too many requests")) {
            return AiErrorCategory.RATE_LIMIT
        }
        if (httpCode == 404 || lower.contains("model_not_found") || lower.contains("not found for api version")) {
            return AiErrorCategory.MODEL_UNAVAILABLE
        }
        if (httpCode == 408 || httpCode == 504 || lower.contains("timeout") || lower.contains("timed out")) {
            return AiErrorCategory.NETWORK_TIMEOUT
        }
        if (httpCode != null && httpCode >= 500) {
            return AiErrorCategory.SERVER_ERROR
        }
        return AiErrorCategory.INVALID_RESPONSE
    }

    /**
     * Extracts readable streamed reply text while a structured JSON response (`{"intent":...,"reply":"..."}`)
     * is still arriving chunk-by-chunk, or falls back to clean raw text for plain-text streams.
     */
    fun extractPartialReplyForUi(accumulatedRaw: String): String {
        val trimmed = accumulatedRaw.trimStart()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("```")) {
            return accumulatedRaw
        }
        val marker = "\"reply\""
        val idx = accumulatedRaw.indexOf(marker)
        if (idx < 0) return ""
        val colonIdx = accumulatedRaw.indexOf(':', idx + marker.length)
        if (colonIdx < 0) return ""
        val openQuoteIdx = accumulatedRaw.indexOf('"', colonIdx + 1)
        if (openQuoteIdx < 0) return ""

        val out = StringBuilder()
        var i = openQuoteIdx + 1
        while (i < accumulatedRaw.length) {
            val ch = accumulatedRaw[i]
            if (ch == '\\' && i + 1 < accumulatedRaw.length) {
                when (val esc = accumulatedRaw[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    '"' -> out.append('"')
                    '\\' -> out.append('\\')
                    '/' -> out.append('/')
                    else -> out.append(esc)
                }
                i += 2
            } else if (ch == '"') {
                break
            } else {
                out.append(ch)
                i++
            }
        }
        return out.toString()
    }

    private fun buildOpenAiCompatiblePayload(
        provider: SuruAiProvider,
        model: String,
        systemInstruction: String,
        historyTurns: List<Pair<String, String>>,
        userPrompt: String,
        imageAttachment: SuruImageAttachment?,
        expectJson: Boolean,
        stream: Boolean,
        maxTokens: Int
    ): JSONObject {
        val messages = JSONArray()
        if (systemInstruction.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemInstruction))
        }
        historyTurns.forEach { (role, text) ->
            if (text.isNotBlank()) {
                val mapped = if (role == "model" || role == "assistant") "assistant" else "user"
                messages.put(JSONObject().put("role", mapped).put("content", text))
            }
        }
        val effectivePrompt = userPrompt.ifBlank { "Analyze this image." }
        if (imageAttachment != null && provider.supportsVision) {
            val contentArray = JSONArray()
                .put(JSONObject().put("type", "text").put("text", effectivePrompt))
                .put(
                    JSONObject().put("type", "image_url").put(
                        "image_url",
                        JSONObject().put("url", "data:image/jpeg;base64,${imageAttachment.base64Jpeg}")
                    )
                )
            messages.put(JSONObject().put("role", "user").put("content", contentArray))
        } else {
            messages.put(JSONObject().put("role", "user").put("content", effectivePrompt))
        }

        val isReasoningModel = model.startsWith("o") || model.contains("reasoner")
        return JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("stream", stream)
            if (maxTokens > 0) {
                if (model.startsWith("o")) put("max_completion_tokens", maxTokens) else put("max_tokens", maxTokens)
            }
            if (!isReasoningModel) {
                put("temperature", 0.35)
            }
            if (expectJson && !stream && !isReasoningModel &&
                (provider == SuruAiProvider.OPENAI || provider == SuruAiProvider.DEEPSEEK)
            ) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
        }
    }

    private fun buildAnthropicPayload(
        model: String,
        systemInstruction: String,
        historyTurns: List<Pair<String, String>>,
        userPrompt: String,
        imageAttachment: SuruImageAttachment?,
        stream: Boolean,
        maxTokens: Int
    ): JSONObject {
        val normalizedTurns = mutableListOf<Pair<String, String>>()
        for ((role, text) in historyTurns) {
            if (text.isBlank()) continue
            val mapped = if (role == "model" || role == "assistant") "assistant" else "user"
            if (normalizedTurns.isEmpty() && mapped != "user") continue
            if (normalizedTurns.isNotEmpty() && normalizedTurns.last().first == mapped) {
                val prev = normalizedTurns.removeAt(normalizedTurns.lastIndex)
                normalizedTurns.add(mapped to "${prev.second}\n$text")
            } else {
                normalizedTurns.add(mapped to text)
            }
        }
        // If history ends with "user", merge or separate cleanly before current user turn
        val messages = JSONArray()
        normalizedTurns.forEach { (role, text) ->
            messages.put(JSONObject().put("role", role).put("content", text))
        }
        val effectivePrompt = userPrompt.ifBlank { "Analyze this image." }
        if (messages.length() > 0 && messages.getJSONObject(messages.length() - 1).optString("role") == "user") {
            messages.put(JSONObject().put("role", "assistant").put("content", "Understood."))
        }
        val currentContent: Any = if (imageAttachment != null) {
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
                .put(JSONObject().put("type", "text").put("text", effectivePrompt))
        } else {
            effectivePrompt
        }
        messages.put(JSONObject().put("role", "user").put("content", currentContent))

        return JSONObject().apply {
            put("model", model)
            put("max_tokens", maxTokens.coerceAtLeast(16))
            put("stream", stream)
            if (systemInstruction.isNotBlank()) {
                put("system", systemInstruction)
            }
            put("messages", messages)
        }
    }

    private fun buildGeminiPayload(
        systemInstruction: String,
        historyTurns: List<Pair<String, String>>,
        userPrompt: String,
        imageAttachment: SuruImageAttachment?,
        expectJson: Boolean,
        maxTokens: Int
    ): JSONObject {
        val contentsArray = JSONArray()
        historyTurns.forEach { (role, text) ->
            if (text.isNotBlank()) {
                val mapped = if (role == "model" || role == "assistant") "model" else "user"
                contentsArray.put(
                    JSONObject().put("role", mapped)
                        .put("parts", JSONArray().put(JSONObject().put("text", text)))
                )
            }
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

        return JSONObject().apply {
            if (systemInstruction.isNotBlank()) {
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
            }
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.35)
                put("maxOutputTokens", maxTokens)
                if (expectJson) put("responseMimeType", "application/json")
            })
        }
    }
}
