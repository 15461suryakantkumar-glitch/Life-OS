package com.example.suru

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class SuruRouteTarget {
    OPENROUTER,
    GEMINI_API_KEY
}

sealed interface SuruRouteOutcome {
    data class Success(val text: String) : SuruRouteOutcome
    data class Unavailable(val diagnosticSummary: String = "") : SuruRouteOutcome
}

data class SuruSecretProbe(
    val value: String?,
    val source: String,
    val status: String,
    val errorCode: String? = null
)

private class SuruHttpFailure(
    val code: Int,
    val detail: String
) : IllegalStateException("HTTP $code: $detail")

/**
 * Zero-Supabase SURU AI two-step direct router:
 * 1) OpenRouter direct POST (`openrouter/free`) using `OPENROUTER_API_KEY`.
 * 2) Gemini direct POST (`gemini-2.5-flash:generateContent?key=...`) using `GEMINI_API_KEY`.
 *
 * Never logs or surfaces raw API keys, secret-bearing URLs, or user health/check-in data.
 */
object SuruAiRouter {

    private const val TAG = "SuruAiRouter"
    private const val OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    private const val OPENROUTER_FREE_MODEL = "openrouter/free"
    const val GEMINI_MODEL_ID = "gemini-2.5-flash"
    private const val DIRECT_TIMEOUT_MS = 20_000L

    const val CALM_UNAVAILABLE_MESSAGE =
        "I'm having a little trouble connecting right now. Please try again in a moment."

    private val relaxedJson = Json { ignoreUnknownKeys = true }

    private val ipv4FirstDns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return Dns.SYSTEM.lookup(hostname).sortedBy { if (it is Inet4Address) 0 else 1 }
        }
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(ipv4FirstDns)
            .retryOnConnectionFailure(true)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private suspend fun awaitHttpCall(request: Request): Pair<Int, String> =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use { res ->
                            val body = res.body?.string().orEmpty()
                            if (continuation.isActive) {
                                continuation.resume(res.code to body)
                            }
                        }
                    } catch (t: Throwable) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(t)
                        }
                    }
                }
            })
        }

    private fun safeLog(level: String, msg: String) {
        runCatching {
            when (level) {
                "i" -> Log.i(TAG, msg)
                "w" -> Log.w(TAG, msg)
                else -> Log.e(TAG, msg)
            }
        }
    }

    fun configuredSecret(rawValue: String?): String? {
        if (rawValue == null) return null
        val cleaned = stripWrappingQuotes(rawValue.trim())
        if (classifyInvalidSecret(cleaned) != null) return null
        return cleaned
    }

    private fun stripWrappingQuotes(trimmed: String): String {
        return if (trimmed.length >= 2 &&
            ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                (trimmed.startsWith("'") && trimmed.endsWith("'")))
        ) {
            trimmed.substring(1, trimmed.length - 1).trim()
        } else {
            trimmed
        }
    }

    private fun classifyInvalidSecret(cleaned: String): String? {
        return when {
            cleaned.isBlank() -> "unset"
            cleaned.equals("null", ignoreCase = true) ||
                cleaned.equals("undefined", ignoreCase = true) ||
                cleaned.equals("none", ignoreCase = true) -> "unset"
            cleaned.equals("your_api_key_here", ignoreCase = true) ||
                cleaned.startsWith("YOUR_", ignoreCase = true) ||
                (cleaned.startsWith("<") && cleaned.endsWith(">")) ||
                (cleaned.startsWith("[") && cleaned.endsWith("]")) -> "placeholder"
            cleaned.contains("...") ||
                cleaned.contains("***") ||
                cleaned.contains("•••") -> "masked"
            else -> null
        }
    }

    internal fun safeKeyKind(cleanedKey: String): String {
        return when {
            cleanedKey.startsWith("AQ.") -> "AQ."
            cleanedKey.startsWith("AIza") -> "AIza"
            cleanedKey.startsWith("sk-or-") -> "sk-or"
            else -> "custom"
        }
    }

    internal fun probeSecret(
        primaryLabel: String = "BuildConfig",
        primaryValue: () -> String?,
        envLookup: (String) -> String? = System::getenv,
        propLookup: (String) -> String? = System::getProperty,
        keyPrefixForCode: String = "SECRET",
        vararg keys: String
    ): SuruSecretProbe {
        var firstFailure: Pair<String, String>? = null

        fun checkCandidate(source: String, raw: String?): SuruSecretProbe? {
            if (raw == null) return null
            val cleaned = stripWrappingQuotes(raw.trim())
            val invalidReason = classifyInvalidSecret(cleaned)
            if (invalidReason == null) {
                return SuruSecretProbe(
                    value = cleaned,
                    source = source,
                    status = "ready($source:${safeKeyKind(cleaned)},len=${cleaned.length})",
                    errorCode = null
                )
            }
            if (invalidReason != "unset" && firstFailure == null) {
                firstFailure = source to invalidReason
            }
            return null
        }

        runCatching { primaryValue() }.getOrNull()?.let { raw ->
            checkCandidate(primaryLabel, raw)?.let { return it }
        }
        for (key in keys) {
            runCatching { envLookup(key) }.getOrNull()?.let { raw ->
                checkCandidate("env:$key", raw)?.let { return it }
            }
            runCatching { propLookup(key) }.getOrNull()?.let { raw ->
                checkCandidate("prop:$key", raw)?.let { return it }
            }
        }
        val failure = firstFailure
        return if (failure != null) {
            val code = if (failure.second == "masked") {
                "${keyPrefixForCode}_KEY_MASKED"
            } else {
                "${keyPrefixForCode}_KEY_EMPTY"
            }
            SuruSecretProbe(
                value = null,
                source = failure.first,
                status = "${failure.second}(${failure.first})",
                errorCode = code
            )
        } else {
            SuruSecretProbe(
                value = null,
                source = "none",
                status = "unset",
                errorCode = "${keyPrefixForCode}_KEY_EMPTY"
            )
        }
    }

    internal fun resolveSecret(
        primaryValue: () -> String?,
        envLookup: (String) -> String? = System::getenv,
        propLookup: (String) -> String? = System::getProperty,
        vararg keys: String
    ): String? {
        return probeSecret(
            primaryValue = primaryValue,
            envLookup = envLookup,
            propLookup = propLookup,
            keys = keys
        ).value
    }

    internal fun geminiRestEndpoint(
        apiKey: String,
        modelId: String = GEMINI_MODEL_ID
    ): String {
        val cleanKey = apiKey.trim()
        val encodedKey = URLEncoder.encode(cleanKey, "UTF-8")
        return "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$encodedKey"
    }

    internal fun normalizeConversationTurns(turns: List<SuruTurn>): List<SuruTurn> {
        val normalized = mutableListOf<SuruTurn>()
        for (turn in turns) {
            val cleanText = turn.text.trim()
            if (cleanText.isEmpty()) continue
            if (normalized.isEmpty() && !turn.isFromUser) continue
            val prev = normalized.lastOrNull()
            if (prev != null && prev.isFromUser == turn.isFromUser) {
                normalized[normalized.lastIndex] = prev.copy(text = "${prev.text}\n$cleanText")
            } else {
                normalized.add(turn.copy(text = cleanText))
            }
        }
        return normalized
    }

    internal fun sanitizeDiagnostic(
        raw: String?,
        secretsToRedact: List<String> = emptyList()
    ): String {
        if (raw.isNullOrBlank()) return ""
        var s = raw.replace(Regex("\\s+"), " ").trim()
        for (secret in secretsToRedact) {
            if (secret.length >= 6) {
                s = s.replace(secret, "[REDACTED]")
                runCatching { URLEncoder.encode(secret, "UTF-8") }.getOrNull()?.let { encoded ->
                    if (encoded.length >= 6) s = s.replace(encoded, "[REDACTED]")
                }
            }
        }
        s = s.replace(Regex("([?&]key=)[^&\\s]+", RegexOption.IGNORE_CASE), "$1[REDACTED]")
        s = s.replace(Regex("(AIza|AQ\\.|sk-or-v1-|sk-)[A-Za-z0-9_.-]{6,}"), "[REDACTED_KEY]")
        s = s.replace(Regex("Bearer\\s+\\S+", RegexOption.IGNORE_CASE), "Bearer [REDACTED]")
        return if (s.length > 140) s.take(137) + "..." else s
    }

    private fun extractErrorSummary(body: String): String {
        if (body.isBlank()) return "empty error body"
        return runCatching {
            val root = relaxedJson.parseToJsonElement(body).jsonObject
            val errObj = root["error"]?.takeIf { it !is JsonNull }?.jsonObject
            val msg = errObj?.get("message")?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
                ?: root["message"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
                ?: body
            sanitizeDiagnostic(msg)
        }.getOrElse { sanitizeDiagnostic(body) }
    }

    internal fun extractOpenRouterContent(responseBody: String): String {
        val root = relaxedJson.parseToJsonElement(responseBody).jsonObject
        val err = root["error"]
        if (err != null && err !is JsonNull) {
            val errMsg = (err as? JsonObject)
                ?.get("message")?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
                ?: "error payload"
            throw IllegalStateException("OpenRouter error: ${sanitizeDiagnostic(errMsg)}")
        }
        val firstChoice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw IllegalStateException("OpenRouter returned no choices")
        val messageObj = firstChoice["message"]?.takeIf { it !is JsonNull }?.jsonObject
        val rawContent = messageObj?.get("content")
        val extracted = when (rawContent) {
            is JsonPrimitive -> rawContent.contentOrNull?.trim().orEmpty()
            is JsonArray -> buildString {
                for (item in rawContent) {
                    val partText = when (item) {
                        is JsonObject -> item["text"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
                        is JsonPrimitive -> item.contentOrNull
                        else -> null
                    }
                    if (!partText.isNullOrBlank()) {
                        if (isNotEmpty()) append('\n')
                        append(partText.trim())
                    }
                }
            }.trim()
            else -> ""
        }
        val fallbackChoiceText = firstChoice["text"]
            ?.takeIf { it !is JsonNull }
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            .orEmpty()
        return extracted.ifBlank { fallbackChoiceText }.ifBlank {
            throw IllegalStateException("OpenRouter returned empty content")
        }
    }

    internal fun extractGeminiRestContent(responseBody: String): String {
        val root = relaxedJson.parseToJsonElement(responseBody).jsonObject
        val err = root["error"]
        if (err != null && err !is JsonNull) {
            val errMsg = (err as? JsonObject)
                ?.get("message")?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
                ?: "error payload"
            throw IllegalStateException("Gemini error: ${sanitizeDiagnostic(errMsg)}")
        }
        val firstCandidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        if (firstCandidate == null) {
            val blockReason = root["promptFeedback"]?.takeIf { it !is JsonNull }?.jsonObject
                ?.get("blockReason")?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
            throw IllegalStateException(
                if (blockReason != null) "Gemini blocked: $blockReason" else "Gemini returned no candidates"
            )
        }
        val parts = firstCandidate["content"]?.takeIf { it !is JsonNull }?.jsonObject
            ?.get("parts")?.takeIf { it !is JsonNull }?.jsonArray
        if (parts == null || parts.isEmpty()) {
            val finishReason = firstCandidate["finishReason"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
            throw IllegalStateException(
                if (finishReason != null) "Gemini empty parts ($finishReason)" else "Gemini returned no parts"
            )
        }
        val out = StringBuilder()
        for (part in parts) {
            val text = (part as? JsonObject)?.get("text")
                ?.takeIf { it !is JsonNull }
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()
            out.append(text)
        }
        return out.toString().trim().ifBlank {
            throw IllegalStateException("Gemini returned empty text")
        }
    }

    fun buildRoutePlan(
        openRouterKey: String?,
        geminiKey: String?
    ): List<SuruRouteTarget> {
        val plan = mutableListOf<SuruRouteTarget>()
        if (configuredSecret(openRouterKey) != null) {
            plan.add(SuruRouteTarget.OPENROUTER)
        }
        if (configuredSecret(geminiKey) != null) {
            plan.add(SuruRouteTarget.GEMINI_API_KEY)
        }
        return plan
    }

    fun hasAnyRouteConfigured(
        openRouterKey: String? = currentOpenRouterKey(),
        geminiKey: String? = currentGeminiKey()
    ): Boolean {
        return buildRoutePlan(openRouterKey, geminiKey).isNotEmpty()
    }

    private fun failureDiagnosticCode(prefix: String, throwable: Throwable): String {
        return when (throwable) {
            is SuruHttpFailure -> "${prefix}_HTTP_${throwable.code}"
            is TimeoutCancellationException, is SocketTimeoutException -> "${prefix}_TIMEOUT"
            is IOException -> "${prefix}_NET_ERR"
            else -> "${prefix}_BAD_RESP"
        }
    }

    suspend fun completeWithFallback(
        systemInstruction: String,
        memoryState: SuruBoundedMemoryState,
        userPrompt: String,
        imageAttachment: SuruImageAttachment? = null,
        expectJson: Boolean = false
    ): SuruRouteOutcome {
        val openRouterProbe = probeOpenRouterKey()
        val geminiProbe = probeGeminiKey()
        val openRouterKey = openRouterProbe.value
        val geminiKey = geminiProbe.value
        val plan = buildRoutePlan(openRouterKey, geminiKey)

        val diagnosticCodes = mutableListOf<String>()
        if (openRouterKey == null) {
            diagnosticCodes.add(openRouterProbe.errorCode ?: "OPENROUTER_KEY_EMPTY")
        }

        safeLog(
            "i",
            "SURU route plan=${plan.map { it.name }} or=${openRouterProbe.status} gem=${geminiProbe.status}"
        )

        val effectiveSystem = buildString {
            append(systemInstruction.trim())
            if (memoryState.rollingSummary.isNotBlank()) {
                appendLine()
                appendLine()
                append("Earlier conversation context: ")
                append(memoryState.rollingSummary)
            }
        }
        val cleanTurns = normalizeConversationTurns(memoryState.recentTurns)
        val secretValues = listOfNotNull(openRouterKey, geminiKey)

        // Step 1: Direct POST to OpenRouter (openrouter/free)
        if (openRouterKey != null) {
            try {
                val reply = withTimeout(DIRECT_TIMEOUT_MS) {
                    callOpenRouter(
                        apiKey = openRouterKey,
                        systemInstruction = effectiveSystem,
                        recentTurns = cleanTurns,
                        userPrompt = userPrompt,
                        imageAttachment = imageAttachment
                    )
                }.trim()
                if (reply.isNotEmpty()) {
                    return SuruRouteOutcome.Success(reply)
                }
                diagnosticCodes.add("OR_BAD_RESP")
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    diagnosticCodes.add("OR_TIMEOUT")
                    safeLog("w", "OpenRouter request timed out")
                } else {
                    throw e
                }
            } catch (t: Throwable) {
                val code = failureDiagnosticCode("OR", t)
                diagnosticCodes.add(code)
                safeLog("w", "OpenRouter failed ($code): ${sanitizeDiagnostic(t.message, secretValues)}")
            }
        }

        // Step 2: Direct POST to Gemini (gemini-2.5-flash:generateContent?key=...)
        if (geminiKey == null) {
            diagnosticCodes.add(geminiProbe.errorCode ?: "GEMINI_KEY_EMPTY")
        } else {
            try {
                val reply = withTimeout(DIRECT_TIMEOUT_MS) {
                    callGeminiRest(
                        apiKey = geminiKey,
                        systemInstruction = effectiveSystem,
                        recentTurns = cleanTurns,
                        userPrompt = userPrompt,
                        imageAttachment = imageAttachment,
                        expectJson = expectJson
                    )
                }.trim()
                if (reply.isNotEmpty()) {
                    return SuruRouteOutcome.Success(reply)
                }
                diagnosticCodes.add("GEMINI_BAD_RESP")
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    diagnosticCodes.add("GEMINI_TIMEOUT")
                    safeLog("w", "Gemini direct REST request timed out")
                } else {
                    throw e
                }
            } catch (t: Throwable) {
                val code = failureDiagnosticCode("GEMINI", t)
                diagnosticCodes.add(code)
                safeLog("w", "Gemini direct REST failed ($code): ${sanitizeDiagnostic(t.message, secretValues)}")
            }
        }

        val summary = diagnosticCodes.joinToString(" | ").ifBlank { "UNAVAILABLE" }
        safeLog("w", "SURU unavailable: $summary")
        return SuruRouteOutcome.Unavailable(summary)
    }

    internal suspend fun callOpenRouter(
        apiKey: String,
        systemInstruction: String,
        recentTurns: List<SuruTurn>,
        userPrompt: String,
        imageAttachment: SuruImageAttachment?
    ): String {
        val cleanKey = apiKey.trim()
        val messages = JSONArray()
        if (systemInstruction.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemInstruction))
        }
        recentTurns.forEach { turn ->
            val role = if (turn.isFromUser) "user" else "assistant"
            messages.put(JSONObject().put("role", role).put("content", turn.text))
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
            put("model", OPENROUTER_FREE_MODEL)
            put("messages", messages)
            put("temperature", 0.4)
            put("max_tokens", 2048)
        }

        val request = Request.Builder()
            .url(OPENROUTER_ENDPOINT)
            .header("Authorization", "Bearer $cleanKey")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("HTTP-Referer", "https://feelora.app")
            .header("X-Title", "FEELORA")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val (code, body) = awaitHttpCall(request)
        if (code !in 200..299) {
            val detail = extractErrorSummary(body)
            safeLog("w", "OpenRouter HTTP $code: $detail")
            throw SuruHttpFailure(code, detail)
        }
        return extractOpenRouterContent(body)
    }

    internal suspend fun callGeminiRest(
        apiKey: String,
        systemInstruction: String,
        recentTurns: List<SuruTurn>,
        userPrompt: String,
        imageAttachment: SuruImageAttachment?,
        expectJson: Boolean,
        endpointUrl: String = geminiRestEndpoint(apiKey)
    ): String {
        val contentsArray = JSONArray()
        recentTurns.forEach { turn ->
            contentsArray.put(
                JSONObject()
                    .put("role", if (turn.isFromUser) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", turn.text)))
            )
        }
        val currentParts = JSONArray().put(
            JSONObject().put("text", userPrompt.ifBlank { "Analyze this image carefully." })
        )
        if (imageAttachment != null) {
            currentParts.put(
                JSONObject().put(
                    "inlineData",
                    JSONObject()
                        .put("mimeType", "image/jpeg")
                        .put("data", imageAttachment.base64Jpeg)
                )
            )
        }
        contentsArray.put(JSONObject().put("role", "user").put("parts", currentParts))

        val payload = JSONObject().apply {
            if (systemInstruction.isNotBlank()) {
                put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
                )
            }
            put("contents", contentsArray)
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.4)
                    put("maxOutputTokens", 1400)
                    if (expectJson) put("responseMimeType", "application/json")
                }
            )
        }

        val request = Request.Builder()
            .url(endpointUrl)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val (code, body) = awaitHttpCall(request)
        if (code !in 200..299) {
            val detail = extractErrorSummary(body)
            safeLog("w", "Gemini REST HTTP $code: $detail")
            throw SuruHttpFailure(code, detail)
        }
        return extractGeminiRestContent(body)
    }

    fun probeOpenRouterKey(): SuruSecretProbe {
        return probeSecret(
            primaryValue = { BuildConfig.OPENROUTER_API_KEY },
            keyPrefixForCode = "OPENROUTER",
            keys = arrayOf("OPENROUTER_API_KEY")
        )
    }

    fun probeGeminiKey(): SuruSecretProbe {
        return probeSecret(
            primaryValue = { BuildConfig.GEMINI_API_KEY },
            keyPrefixForCode = "GEMINI",
            keys = arrayOf("GEMINI_API_KEY", "GOOGLE_API_KEY")
        )
    }

    fun currentOpenRouterKey(): String? = probeOpenRouterKey().value

    fun currentGeminiKey(): String? = probeGeminiKey().value
}
