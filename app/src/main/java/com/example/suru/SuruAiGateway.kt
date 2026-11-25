package com.example.suru

import android.content.Context
import com.example.data.local.AiProviderUsageEntity
import com.example.data.local.LifeOsDatabase
import com.example.data.local.UserPreferencesState
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SuruAiGateway(
    private val appContext: Context,
    private val httpClient: OkHttpClient = defaultOkHttpClient(),
    private val credentialProvider: AiCredentialProvider = BuildConfigAiCredentialProvider(),
    private val endpointConfig: AiProviderEndpointConfig = AiProviderEndpointConfig(),
    private val modelRegistry: AiModelRegistry = AiModelRegistry(),
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val baseRetryDelayMs: Long = 350L,
    private val maxRetryAttempts: Int = 2
) {

    private data class ProviderRuntimeRecord(
        val liveVerifiedConnected: Boolean = false,
        val isTesting: Boolean = false,
        val lastConnectedAtMillis: Long? = null,
        val lastErrorMessage: String? = null,
        val lastErrorCategory: AiErrorCategory = AiErrorCategory.NONE
    )

    private val runtimeRecords = MutableStateFlow(
        SuruAiProvider.entries.associateWith { ProviderRuntimeRecord() }
    )
    private var lastSyncedPrefs: UserPreferencesState = UserPreferencesState()

    private val _diagnosticsFlow = MutableStateFlow(buildDiagnosticsSnapshot())
    val diagnosticsFlow: StateFlow<List<ProviderDiagnostic>> = _diagnosticsFlow.asStateFlow()

    fun syncPreferences(prefs: UserPreferencesState) {
        if (lastSyncedPrefs != prefs) {
            lastSyncedPrefs = prefs
            refreshDiagnosticsSnapshot()
        }
    }

    fun getModelRegistry(): AiModelRegistry = modelRegistry

    fun getProviderConfigState(provider: SuruAiProvider): ProviderConfigState {
        if (credentialProvider.getProxyUrl() != null) {
            return ProviderConfigState.PROXY_CONFIGURED
        }
        if (credentialProvider.getApiKey(provider) != null) {
            return ProviderConfigState.API_KEY_DETECTED
        }
        if (provider == SuruAiProvider.GEMINI && isFirebaseAvailable()) {
            return ProviderConfigState.FIREBASE_FALLBACK_ONLY
        }
        return ProviderConfigState.NOT_CONFIGURED
    }

    fun getProviderStatus(provider: SuruAiProvider): ProviderConnectionStatus {
        val configState = getProviderConfigState(provider)
        if (configState == ProviderConfigState.NOT_CONFIGURED) {
            return ProviderConnectionStatus.NOT_CONFIGURED
        }
        val runtime = runtimeRecords.value[provider] ?: ProviderRuntimeRecord()
        return when {
            runtime.isTesting -> ProviderConnectionStatus.TESTING
            runtime.liveVerifiedConnected -> ProviderConnectionStatus.CONNECTED
            runtime.lastErrorMessage != null -> ProviderConnectionStatus.CONNECTION_FAILED
            else -> ProviderConnectionStatus.CONFIGURED_UNTESTED
        }
    }

    fun getAllDiagnostics(): List<ProviderDiagnostic> = buildDiagnosticsSnapshot()

    fun hasAnyConfiguredProvider(): Boolean {
        return SuruAiProvider.entries.any { getProviderConfigState(it) != ProviderConfigState.NOT_CONFIGURED }
    }

    suspend fun testConnection(
        provider: SuruAiProvider,
        modelOverride: String? = null,
        supabaseAccessToken: String = lastSyncedPrefs.supabaseAccessToken
    ): ProviderDiagnostic {
        val model = modelOverride?.takeIf { it.isNotBlank() } ?: resolveSelectedModel(provider, lastSyncedPrefs)
        val configState = getProviderConfigState(provider)
        if (configState == ProviderConfigState.NOT_CONFIGURED) {
            val detail = "Missing ${provider.envVarName}. Set it in AI Studio Secrets (or configure SURU_AI_PROXY_URL)."
            updateRuntimeRecord(provider) {
                it.copy(
                    liveVerifiedConnected = false,
                    isTesting = false,
                    lastErrorMessage = detail,
                    lastErrorCategory = AiErrorCategory.NOT_CONFIGURED
                )
            }
            return getAllDiagnostics().first { it.provider == provider }
        }

        updateRuntimeRecord(provider) {
            it.copy(isTesting = true, lastErrorMessage = null, lastErrorCategory = AiErrorCategory.NONE)
        }

        val startMs = clockMillis()
        try {
            val raw = invokeWithRetry(provider) {
                invokeSingleProvider(
                    provider = provider,
                    model = model,
                    systemInstruction = "Respond concisely with OK.",
                    historyTurns = emptyList(),
                    userPrompt = "Connection check: reply with OK.",
                    imageAttachment = null,
                    expectJson = false,
                    supabaseAccessToken = supabaseAccessToken,
                    maxTokens = 24
                )
            }
            if (raw.isBlank()) {
                throw AiGatewayException(
                    provider = provider,
                    category = AiErrorCategory.INVALID_RESPONSE,
                    safeDetail = "${provider.displayName} returned an empty response during connection test."
                )
            }
            val latency = (clockMillis() - startMs).coerceAtLeast(1L)
            markProviderSuccess(provider)
            logUsageAsync(provider, model, 28, raw.length, success = true, latencyMs = latency)
            // Best-effort live model catalog refresh after verified connection
            runCatching { refreshModels(provider) }
        } catch (e: CancellationException) {
            updateRuntimeRecord(provider) { it.copy(isTesting = false) }
            throw e
        } catch (t: Throwable) {
            val classified = AiProviderAdapters.classifyThrowable(provider, t, allActiveSecrets())
            val latency = (clockMillis() - startMs).coerceAtLeast(1L)
            markProviderFailure(provider, classified)
            logUsageAsync(provider, model, 28, 0, success = false, latencyMs = latency)
        }
        return getAllDiagnostics().first { it.provider == provider }
    }

    suspend fun refreshModels(provider: SuruAiProvider): List<String> {
        val apiKey = credentialProvider.getApiKey(provider) ?: return modelRegistry.getModels(provider)
        val req = AiProviderAdapters.buildListModelsRequest(
            provider = provider,
            baseUrl = endpointConfig.baseUrlFor(provider),
            apiKey = apiKey
        )
        val fetched = withContext(Dispatchers.IO) {
            runCatching {
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    val body = resp.body?.string().orEmpty()
                    AiProviderAdapters.parseModelsResponse(provider, body)
                }
            }.getOrDefault(emptyList())
        }
        if (fetched.isNotEmpty()) {
            modelRegistry.updateDiscoveredModels(provider, fetched)
            refreshDiagnosticsSnapshot()
        }
        return modelRegistry.getModels(provider)
    }

    fun selectExecutionChain(
        prefs: UserPreferencesState,
        taskKind: SuruRoutingTaskKind
    ): List<Pair<SuruAiProvider, String>> {
        val preferred = SuruAiProvider.fromStored(prefs.suruDefaultProvider)
        val preferredModel = resolveSelectedModel(preferred, prefs)

        val configured = SuruAiProvider.entries.filter { getProviderConfigState(it) != ProviderConfigState.NOT_CONFIGURED }
        if (configured.isEmpty()) return emptyList()

        val chain = LinkedHashSet<Pair<SuruAiProvider, String>>()

        if (!prefs.suruAutoRoutingEnabled) {
            if (preferred in configured) {
                chain.add(preferred to preferredModel)
            }
        } else {
            val smartOrder = when (taskKind) {
                SuruRoutingTaskKind.VISION_MULTIMODAL -> listOf(
                    preferred.takeIf { it.supportsVision },
                    SuruAiProvider.GEMINI,
                    SuruAiProvider.OPENAI,
                    SuruAiProvider.ANTHROPIC
                ).filterNotNull()

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
                val model = resolveSelectedModel(prov, prefs)
                if (taskKind != SuruRoutingTaskKind.VISION_MULTIMODAL || prov.supportsVision) {
                    chain.add(prov to model)
                }
            }
        }

        if (prefs.suruAutoFallbackEnabled) {
            configured.forEach { prov ->
                if (taskKind != SuruRoutingTaskKind.VISION_MULTIMODAL || prov.supportsVision) {
                    chain.add(prov to resolveSelectedModel(prov, prefs))
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
        syncPreferences(prefs)
        val preferred = SuruAiProvider.fromStored(prefs.suruDefaultProvider)
        val chain = selectExecutionChain(prefs, taskKind)
        if (chain.isEmpty()) {
            throw AiGatewayException(
                provider = preferred,
                category = AiErrorCategory.NOT_CONFIGURED,
                safeDetail = "No AI provider is configured. Add API keys (GEMINI_API_KEY, OPENAI_API_KEY, ANTHROPIC_API_KEY, XAI_API_KEY, DEEPSEEK_API_KEY) or SURU_AI_PROXY_URL in AI Studio Secrets."
            )
        }

        var lastError: AiGatewayException? = null
        val failedReasons = mutableListOf<String>()

        for ((index, candidate) in chain.withIndex()) {
            val (provider, model) = candidate
            val startMs = clockMillis()
            try {
                val raw = invokeWithRetry(provider) {
                    invokeSingleProvider(
                        provider = provider,
                        model = model,
                        systemInstruction = systemInstruction,
                        historyTurns = historyTurns,
                        userPrompt = userPrompt,
                        imageAttachment = imageAttachment,
                        expectJson = expectJson,
                        supabaseAccessToken = prefs.supabaseAccessToken
                    )
                }
                val latency = (clockMillis() - startMs).coerceAtLeast(1L)
                markProviderSuccess(provider)
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
                if (prefs.suruSecondaryReviewEnabled && taskKind == SuruRoutingTaskKind.STUDY_HELP) {
                    val secondaryProv = SuruAiProvider.fromStored(prefs.suruSecondaryProvider)
                    val secondaryModel = prefs.suruSecondaryModel
                        .takeIf { it in modelRegistry.getModels(secondaryProv) }
                        ?: resolveSelectedModel(secondaryProv, prefs)
                    if (secondaryProv != provider && getProviderConfigState(secondaryProv) != ProviderConfigState.NOT_CONFIGURED) {
                        runCatching {
                            val reviewPrompt = "Verify factual accuracy and clarity of this draft JSON response, keeping the exact same JSON schema and improving weak spots if needed:\n\n$raw"
                            val reviewed = invokeWithRetry(secondaryProv) {
                                invokeSingleProvider(
                                    provider = secondaryProv,
                                    model = secondaryModel,
                                    systemInstruction = "Return valid JSON matching the exact schema provided.",
                                    historyTurns = emptyList(),
                                    userPrompt = reviewPrompt,
                                    imageAttachment = null,
                                    expectJson = expectJson,
                                    supabaseAccessToken = prefs.supabaseAccessToken
                                )
                            }
                            if (reviewed.isNotBlank()) {
                                markProviderSuccess(secondaryProv)
                                finalOutput = reviewed
                                reviewedBy = "${secondaryProv.shortName} ($secondaryModel)"
                            }
                        }
                    }
                }

                val fallbackNote = buildFallbackNotice(provider, model, index, failedReasons)
                val routingNote = buildRoutingNotice(preferred, provider, taskKind, index)
                return GatewayCompletionResult(
                    rawText = finalOutput,
                    providerUsed = provider,
                    modelUsed = model,
                    secondaryReviewedBy = reviewedBy,
                    fallbackNote = fallbackNote,
                    routingNote = routingNote
                )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                val classified = AiProviderAdapters.classifyThrowable(provider, t, allActiveSecrets())
                lastError = classified
                failedReasons.add("${provider.shortName} (${classified.category.label})")
                markProviderFailure(provider, classified)
                val latency = (clockMillis() - startMs).coerceAtLeast(1L)
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

        throw lastError ?: AiGatewayException(
            provider = preferred,
            category = AiErrorCategory.SERVER_ERROR,
            safeDetail = "All configured AI providers failed."
        )
    }

    fun streamCompletion(
        prefs: UserPreferencesState,
        taskKind: SuruRoutingTaskKind,
        systemInstruction: String,
        historyTurns: List<Pair<String, String>>,
        userPrompt: String,
        imageAttachment: SuruImageAttachment? = null,
        expectJson: Boolean = true
    ): Flow<AiGatewayStreamEvent> = flow {
        syncPreferences(prefs)
        val preferred = SuruAiProvider.fromStored(prefs.suruDefaultProvider)
        val chain = selectExecutionChain(prefs, taskKind)
        if (chain.isEmpty()) {
            throw AiGatewayException(
                provider = preferred,
                category = AiErrorCategory.NOT_CONFIGURED,
                safeDetail = "No AI provider is configured. Add API keys or SURU_AI_PROXY_URL in AI Studio Secrets."
            )
        }

        var lastError: AiGatewayException? = null
        val failedReasons = mutableListOf<String>()

        for ((index, candidate) in chain.withIndex()) {
            val (provider, model) = candidate
            val startMs = clockMillis()
            val accumulated = StringBuilder()
            try {
                val fallbackNote = buildFallbackNotice(provider, model, index, failedReasons)
                val routingNote = buildRoutingNotice(preferred, provider, taskKind, index)

                val apiKey = credentialProvider.getApiKey(provider)
                val proxyUrl = credentialProvider.getProxyUrl()
                if (proxyUrl == null && apiKey != null && provider.supportsStreaming) {
                    streamDirectProvider(
                        provider = provider,
                        apiKey = apiKey,
                        model = model,
                        systemInstruction = systemInstruction,
                        historyTurns = historyTurns,
                        userPrompt = userPrompt,
                        imageAttachment = imageAttachment
                    ) { chunk ->
                        accumulated.append(chunk)
                        emit(
                            AiGatewayStreamEvent.Delta(
                                provider = provider,
                                model = model,
                                chunk = chunk,
                                accumulatedText = accumulated.toString(),
                                fallbackNote = fallbackNote,
                                routingNote = routingNote
                            )
                        )
                    }
                    val finalRaw = accumulated.toString().trim()
                    if (finalRaw.isBlank()) {
                        throw AiGatewayException(
                            provider = provider,
                            category = AiErrorCategory.INVALID_RESPONSE,
                            safeDetail = "${provider.displayName} stream ended with empty output."
                        )
                    }
                    markProviderSuccess(provider)
                    logUsageAsync(
                        provider = provider,
                        model = model,
                        promptChars = systemInstruction.length + userPrompt.length,
                        responseChars = finalRaw.length,
                        success = true,
                        latencyMs = (clockMillis() - startMs).coerceAtLeast(1L)
                    )
                    emit(
                        AiGatewayStreamEvent.Completed(
                            GatewayCompletionResult(
                                rawText = finalRaw,
                                providerUsed = provider,
                                modelUsed = model,
                                fallbackNote = fallbackNote,
                                routingNote = routingNote
                            )
                        )
                    )
                    return@flow
                } else {
                    val result = invokeWithRetry(provider) {
                        invokeSingleProvider(
                            provider = provider,
                            model = model,
                            systemInstruction = systemInstruction,
                            historyTurns = historyTurns,
                            userPrompt = userPrompt,
                            imageAttachment = imageAttachment,
                            expectJson = expectJson,
                            supabaseAccessToken = prefs.supabaseAccessToken
                        )
                    }
                    markProviderSuccess(provider)
                    logUsageAsync(
                        provider = provider,
                        model = model,
                        promptChars = systemInstruction.length + userPrompt.length,
                        responseChars = result.length,
                        success = true,
                        latencyMs = (clockMillis() - startMs).coerceAtLeast(1L)
                    )
                    emit(
                        AiGatewayStreamEvent.Completed(
                            GatewayCompletionResult(
                                rawText = result,
                                providerUsed = provider,
                                modelUsed = model,
                                fallbackNote = fallbackNote,
                                routingNote = routingNote
                            )
                        )
                    )
                    return@flow
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                val classified = AiProviderAdapters.classifyThrowable(provider, t, allActiveSecrets())
                lastError = classified
                failedReasons.add("${provider.shortName} (${classified.category.label})")
                markProviderFailure(provider, classified)
                logUsageAsync(
                    provider = provider,
                    model = model,
                    promptChars = systemInstruction.length + userPrompt.length,
                    responseChars = 0,
                    success = false,
                    latencyMs = (clockMillis() - startMs).coerceAtLeast(1L)
                )
                // If partial chunks were already streamed from this provider, don't mid-stream splice another provider
                if (accumulated.isNotEmpty() || !prefs.suruAutoFallbackEnabled) {
                    throw classified
                }
            }
        }

        throw lastError ?: AiGatewayException(
            provider = preferred,
            category = AiErrorCategory.SERVER_ERROR,
            safeDetail = "All configured AI providers failed."
        )
    }.flowOn(Dispatchers.IO)

    private suspend fun streamDirectProvider(
        provider: SuruAiProvider,
        apiKey: String,
        model: String,
        systemInstruction: String,
        historyTurns: List<Pair<String, String>>,
        userPrompt: String,
        imageAttachment: SuruImageAttachment?,
        onDelta: suspend (String) -> Unit
    ) {
        val req = AiProviderAdapters.buildChatRequest(
            provider = provider,
            baseUrl = endpointConfig.baseUrlFor(provider),
            apiKey = apiKey,
            model = model,
            systemInstruction = systemInstruction,
            historyTurns = historyTurns,
            userPrompt = userPrompt,
            imageAttachment = imageAttachment,
            expectJson = false,
            stream = true
        )
        invokeWithRetry(provider) {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errBody = resp.body?.string().orEmpty()
                    throw AiProviderAdapters.classifyHttpFailure(provider, model, resp.code, errBody, allActiveSecrets())
                }
                val source = resp.body?.source() ?: throw AiGatewayException(
                    provider = provider,
                    category = AiErrorCategory.INVALID_RESPONSE,
                    safeDetail = "${provider.displayName} returned an empty stream body."
                )
                val contentType = resp.header("Content-Type").orEmpty().lowercase()
                if (contentType.contains("application/json") && !contentType.contains("event-stream")) {
                    val bodyStr = source.readUtf8()
                    val text = AiProviderAdapters.parseCompletionResponse(provider, bodyStr)
                    onDelta(text)
                    return@use
                }
                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
                    if (trimmed.startsWith("data:")) {
                        val dataPayload = trimmed.removePrefix("data:").trim()
                        if (dataPayload == "[DONE]") break
                        val delta = AiProviderAdapters.parseSseDataLine(provider, dataPayload)
                        if (!delta.isNullOrEmpty()) {
                            onDelta(delta)
                        }
                    }
                }
            }
        }
    }

    private suspend fun <T> invokeWithRetry(
        provider: SuruAiProvider,
        block: suspend () -> T
    ): T {
        var attempt = 0
        var lastError: AiGatewayException? = null
        while (attempt < maxRetryAttempts) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                val classified = AiProviderAdapters.classifyThrowable(provider, t, allActiveSecrets())
                lastError = classified
                attempt++
                if (!classified.retryable || attempt >= maxRetryAttempts) {
                    throw classified
                }
                val backoffMs = baseRetryDelayMs * (1L shl (attempt - 1))
                delay(backoffMs)
            }
        }
        throw lastError ?: AiGatewayException(
            provider = provider,
            category = AiErrorCategory.SERVER_ERROR,
            safeDetail = "${provider.displayName} failed after retries."
        )
    }

    private suspend fun invokeSingleProvider(
        provider: SuruAiProvider,
        model: String,
        systemInstruction: String,
        historyTurns: List<Pair<String, String>>,
        userPrompt: String,
        imageAttachment: SuruImageAttachment?,
        expectJson: Boolean,
        supabaseAccessToken: String,
        maxTokens: Int = 1600
    ): String {
        val proxyUrl = credentialProvider.getProxyUrl()
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

        val apiKey = credentialProvider.getApiKey(provider)
        if (apiKey == null) {
            if (provider == SuruAiProvider.GEMINI && isFirebaseAvailable()) {
                return callFirebaseGemini(model, systemInstruction, historyTurns, userPrompt, imageAttachment)
            }
            throw AiGatewayException(
                provider = provider,
                category = AiErrorCategory.NOT_CONFIGURED,
                safeDetail = "${provider.envVarName} is not configured in AI Studio Secrets."
            )
        }

        val req = AiProviderAdapters.buildChatRequest(
            provider = provider,
            baseUrl = endpointConfig.baseUrlFor(provider),
            apiKey = apiKey,
            model = model,
            systemInstruction = systemInstruction,
            historyTurns = historyTurns,
            userPrompt = userPrompt,
            imageAttachment = imageAttachment,
            expectJson = expectJson,
            stream = false,
            maxTokens = maxTokens
        )
        return withContext(Dispatchers.IO) {
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw AiProviderAdapters.classifyHttpFailure(provider, model, resp.code, body, allActiveSecrets())
                }
                AiProviderAdapters.parseCompletionResponse(provider, body)
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
                throw AiProviderAdapters.classifyHttpFailure(provider, model, resp.code, raw, allActiveSecrets())
            }
            val parsed = runCatching { JSONObject(raw) }.getOrNull()
                ?: throw AiGatewayException(provider, AiErrorCategory.INVALID_RESPONSE, safeDetail = "Invalid proxy JSON.")
            parsed.optString("text").ifBlank { parsed.optString("output") }
                .ifBlank { throw AiGatewayException(provider, AiErrorCategory.INVALID_RESPONSE, safeDetail = "Empty proxy response.") }
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
            throw AiGatewayException(
                provider = SuruAiProvider.GEMINI,
                category = AiErrorCategory.INVALID_RESPONSE,
                safeDetail = "Firebase AI returned an empty response."
            )
        }
    }

    private fun resolveSelectedModel(provider: SuruAiProvider, prefs: UserPreferencesState): String {
        val perProvider = prefs.parsedProviderModels().mapNotNull { (k, v) ->
            SuruAiProvider.entries.firstOrNull { it.name == k }?.let { it to v }
        }.toMap()
        val isPrimary = SuruAiProvider.fromStored(prefs.suruDefaultProvider) == provider
        return modelRegistry.resolveSelectedModel(
            provider = provider,
            perProviderSelections = perProvider,
            fallbackDefaultModel = prefs.suruDefaultModel,
            isPrimaryProvider = isPrimary
        )
    }

    private fun buildDiagnosticsSnapshot(): List<ProviderDiagnostic> {
        val runtimes = runtimeRecords.value
        return SuruAiProvider.entries.map { provider ->
            val configState = getProviderConfigState(provider)
            val runtime = runtimes[provider] ?: ProviderRuntimeRecord()
            val status = getProviderStatus(provider)
            val selectedModel = resolveSelectedModel(provider, lastSyncedPrefs)
            val models = modelRegistry.getModels(provider)

            val apiConfigLabel = when (configState) {
                ProviderConfigState.API_KEY_DETECTED -> "${provider.envVarName} detected via Secrets"
                ProviderConfigState.PROXY_CONFIGURED -> "SURU_AI_PROXY_URL configured"
                ProviderConfigState.FIREBASE_FALLBACK_ONLY -> "GEMINI_API_KEY missing · Firebase fallback ready"
                ProviderConfigState.NOT_CONFIGURED -> "Not Configured — add ${provider.envVarName} in Secrets"
            }

            val detail = when (status) {
                ProviderConnectionStatus.CONNECTED -> "Live API request verified ($selectedModel)."
                ProviderConnectionStatus.TESTING -> "Sending live verification request to ${provider.shortName}..."
                ProviderConnectionStatus.CONNECTION_FAILED -> runtime.lastErrorMessage ?: "Connection failed."
                ProviderConnectionStatus.CONFIGURED_UNTESTED -> when (configState) {
                    ProviderConfigState.API_KEY_DETECTED -> "Key configured. Tap Test Connection to verify live access."
                    ProviderConfigState.PROXY_CONFIGURED -> "Proxy configured. Tap Test Connection to verify live access."
                    ProviderConfigState.FIREBASE_FALLBACK_ONLY -> "Firebase fallback available. Tap Test Connection or set GEMINI_API_KEY."
                    ProviderConfigState.NOT_CONFIGURED -> "Add ${provider.envVarName} or SURU_AI_PROXY_URL in AI Studio Secrets."
                }
                ProviderConnectionStatus.NOT_CONFIGURED -> "Add ${provider.envVarName} or SURU_AI_PROXY_URL in AI Studio Secrets."
            }

            ProviderDiagnostic(
                provider = provider,
                status = status,
                configState = configState,
                apiConfigLabel = apiConfigLabel,
                selectedModel = selectedModel,
                availableModels = models,
                detail = detail,
                lastConnectedAtMillis = runtime.lastConnectedAtMillis,
                lastErrorMessage = runtime.lastErrorMessage,
                lastErrorCategory = runtime.lastErrorCategory,
                isTesting = runtime.isTesting
            )
        }
    }

    private fun updateRuntimeRecord(
        provider: SuruAiProvider,
        transform: (ProviderRuntimeRecord) -> ProviderRuntimeRecord
    ) {
        runtimeRecords.update { current ->
            val prev = current[provider] ?: ProviderRuntimeRecord()
            current + (provider to transform(prev))
        }
        refreshDiagnosticsSnapshot()
    }

    private fun markProviderSuccess(provider: SuruAiProvider) {
        val now = clockMillis()
        updateRuntimeRecord(provider) {
            it.copy(
                liveVerifiedConnected = true,
                isTesting = false,
                lastConnectedAtMillis = now,
                lastErrorMessage = null,
                lastErrorCategory = AiErrorCategory.NONE
            )
        }
    }

    private fun markProviderFailure(provider: SuruAiProvider, error: AiGatewayException) {
        updateRuntimeRecord(provider) {
            it.copy(
                liveVerifiedConnected = false,
                isTesting = false,
                lastErrorMessage = error.safeDetail,
                lastErrorCategory = error.category
            )
        }
    }

    private fun refreshDiagnosticsSnapshot() {
        _diagnosticsFlow.value = buildDiagnosticsSnapshot()
    }

    private fun allActiveSecrets(): List<String> {
        return SuruAiProvider.entries.mapNotNull { credentialProvider.getApiKey(it) }
    }

    private fun buildFallbackNotice(
        providerUsed: SuruAiProvider,
        modelUsed: String,
        index: Int,
        failedReasons: List<String>
    ): String? {
        if (index <= 0 || failedReasons.isEmpty()) return null
        return "Switched to ${providerUsed.displayName} ($modelUsed) via automatic fallback after ${failedReasons.joinToString(", ")} failed."
    }

    private fun buildRoutingNotice(
        preferred: SuruAiProvider,
        providerUsed: SuruAiProvider,
        taskKind: SuruRoutingTaskKind,
        index: Int
    ): String? {
        if (index != 0 || preferred == providerUsed) return null
        val reason = when (taskKind) {
            SuruRoutingTaskKind.VISION_MULTIMODAL -> "vision input requires multimodal support"
            SuruRoutingTaskKind.PLANNING_REASONING -> "planning/reasoning task routing"
            SuruRoutingTaskKind.STUDY_HELP -> "study synthesis task routing"
            SuruRoutingTaskKind.CHAT -> "primary provider unavailable"
        }
        return "Auto-routed from ${preferred.shortName} to ${providerUsed.shortName} ($reason)."
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
        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        fun configuredKey(rawValue: String?): String? =
            BuildConfigAiCredentialProvider.normalizeValidCredential(rawValue)

        fun configuredProxyUrl(): String? =
            BuildConfigAiCredentialProvider().getProxyUrl()
    }
}
