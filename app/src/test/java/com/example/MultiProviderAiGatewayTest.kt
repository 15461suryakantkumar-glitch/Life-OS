package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.UserPreferencesState
import com.example.suru.AiCredentialProvider
import com.example.suru.AiErrorCategory
import com.example.suru.AiGatewayException
import com.example.suru.AiGatewayStreamEvent
import com.example.suru.AiProviderAdapters
import com.example.suru.BuildConfigAiCredentialProvider
import com.example.suru.ProviderConfigState
import com.example.suru.ProviderConnectionStatus
import com.example.suru.ProviderDiagnostic
import com.example.suru.SuruAiGateway
import com.example.suru.SuruAiProvider
import com.example.suru.SuruRoutingTaskKind
import com.example.ui.screens.SuruAiSettingsSheet
import com.example.ui.theme.LifeOsTheme
import com.example.ui.viewmodel.LifeOsUiState
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.SocketTimeoutException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiProviderAiGatewayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeCredentialProvider(
        private val keys: Map<SuruAiProvider, String?> = emptyMap(),
        private val proxy: String? = null
    ) : AiCredentialProvider {
        override fun getApiKey(provider: SuruAiProvider): String? =
            BuildConfigAiCredentialProvider.normalizeValidCredential(keys[provider])

        override fun getProxyUrl(): String? = proxy
    }

    private fun buildMockClient(handler: (Request, String) -> Response): OkHttpClient {
        val interceptor = Interceptor { chain ->
            val req = chain.request()
            val bodyBuffer = Buffer()
            req.body?.writeTo(bodyBuffer)
            handler(req, bodyBuffer.readUtf8())
        }
        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    private fun jsonResponse(req: Request, code: Int, jsonBody: String): Response {
        return Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .header("Content-Type", "application/json")
            .body(jsonBody.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun sseResponse(req: Request, sseBody: String): Response {
        return Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Content-Type", "text/event-stream")
            .body(sseBody.toResponseBody("text/event-stream".toMediaType()))
            .build()
    }

    @Test
    fun credentialDetection_rejectsPlaceholdersAndNeverFakesConnectedOnStartup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val creds = FakeCredentialProvider(
            keys = mapOf(
                SuruAiProvider.OPENAI to "YOUR_OPENAI_API_KEY",
                SuruAiProvider.ANTHROPIC to "your_api_key_here",
                SuruAiProvider.GEMINI to "   ",
                SuruAiProvider.XAI to "replace_me",
                SuruAiProvider.DEEPSEEK to "sk-deepseek-real-123456"
            )
        )
        val gateway = SuruAiGateway(
            appContext = context,
            credentialProvider = creds
        )

        val diagnostics = gateway.getAllDiagnostics()
        assertEquals(5, diagnostics.size)

        val openai = diagnostics.first { it.provider == SuruAiProvider.OPENAI }
        val anthropic = diagnostics.first { it.provider == SuruAiProvider.ANTHROPIC }
        val xai = diagnostics.first { it.provider == SuruAiProvider.XAI }
        val deepseek = diagnostics.first { it.provider == SuruAiProvider.DEEPSEEK }

        assertEquals(ProviderConnectionStatus.NOT_CONFIGURED, openai.status)
        assertEquals(ProviderConnectionStatus.NOT_CONFIGURED, anthropic.status)
        assertEquals(ProviderConnectionStatus.NOT_CONFIGURED, xai.status)

        // Key is present for DeepSeek, so configState is API_KEY_DETECTED,
        // but live connection status MUST NOT say Connected before an actual request succeeds!
        assertEquals(ProviderConfigState.API_KEY_DETECTED, deepseek.configState)
        assertEquals(ProviderConnectionStatus.CONFIGURED_UNTESTED, deepseek.status)
        assertFalse(deepseek.status.isLiveVerified)
        assertNull(deepseek.lastConnectedAtMillis)
    }

    @Test
    fun testConnection_verifiesOfficialEndpointsHeadersAndTransitionsToConnected() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val seenRequests = mutableListOf<Pair<String, Map<String, String>>>()

        val client = buildMockClient { req, _ ->
            val headers = mapOf(
                "Authorization" to req.header("Authorization").orEmpty(),
                "x-api-key" to req.header("x-api-key").orEmpty(),
                "anthropic-version" to req.header("anthropic-version").orEmpty(),
                "x-goog-api-key" to req.header("x-goog-api-key").orEmpty()
            )
            val url = req.url.toString()
            seenRequests.add(url to headers)

            when {
                url.endsWith("/models") && url.contains("openai.com") ->
                    jsonResponse(req, 200, """{"data":[{"id":"gpt-4.1-mini"},{"id":"gpt-4.1"}]}""")
                url.endsWith("/v1/models") && url.contains("anthropic.com") ->
                    jsonResponse(req, 200, """{"data":[{"id":"claude-sonnet-4-20250514"}]}""")
                url.endsWith("/v1beta/models") && url.contains("googleapis.com") ->
                    jsonResponse(req, 200, """{"models":[{"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]}]}""")
                url.endsWith("/models") ->
                    jsonResponse(req, 200, """{"data":[]}""")
                url.contains("api.openai.com/v1/chat/completions") ->
                    jsonResponse(req, 200, """{"choices":[{"message":{"content":"OK"}}]}""")
                url.contains("api.anthropic.com/v1/messages") ->
                    jsonResponse(req, 200, """{"content":[{"type":"text","text":"OK"}]}""")
                url.contains("generativelanguage.googleapis.com/v1beta/models/") ->
                    jsonResponse(req, 200, """{"candidates":[{"content":{"parts":[{"text":"OK"}]}}]}""")
                url.contains("api.x.ai/v1/chat/completions") ->
                    jsonResponse(req, 200, """{"choices":[{"message":{"content":"OK"}}]}""")
                url.contains("api.deepseek.com/chat/completions") ->
                    jsonResponse(req, 200, """{"choices":[{"message":{"content":"OK"}}]}""")
                else -> jsonResponse(req, 404, """{"error":{"message":"unexpected url $url"}}""")
            }
        }

        val creds = FakeCredentialProvider(
            keys = mapOf(
                SuruAiProvider.OPENAI to "sk-openai-test-1234",
                SuruAiProvider.ANTHROPIC to "sk-ant-test-1234",
                SuruAiProvider.GEMINI to "AIzaGeminiTest1234567",
                SuruAiProvider.XAI to "xai-test-1234",
                SuruAiProvider.DEEPSEEK to "sk-deepseek-test-1234"
            )
        )

        var fakeClock = 1_750_000_000_000L
        val gateway = SuruAiGateway(
            appContext = context,
            httpClient = client,
            credentialProvider = creds,
            clockMillis = { fakeClock++ },
            baseRetryDelayMs = 1L
        )

        for (provider in SuruAiProvider.entries) {
            val diag = gateway.testConnection(provider)
            assertEquals("Expected CONNECTED for $provider: ${diag.lastErrorMessage}", ProviderConnectionStatus.CONNECTED, diag.status)
            assertTrue(diag.status.isLiveVerified)
            assertNotNull(diag.lastConnectedAtMillis)
            assertNull(diag.lastErrorMessage)
        }

        val openaiReq = seenRequests.first { it.first.contains("api.openai.com/v1/chat/completions") }
        assertEquals("Bearer sk-openai-test-1234", openaiReq.second["Authorization"])

        val anthropicReq = seenRequests.first { it.first.contains("api.anthropic.com/v1/messages") }
        assertEquals("sk-ant-test-1234", anthropicReq.second["x-api-key"])
        assertEquals("2023-06-01", anthropicReq.second["anthropic-version"])

        val geminiReq = seenRequests.first { it.first.contains("generativelanguage.googleapis.com") && it.first.contains(":generateContent") }
        assertEquals("AIzaGeminiTest1234567", geminiReq.second["x-goog-api-key"])
        assertFalse("Gemini key must not be exposed in URL", geminiReq.first.contains("AIzaGeminiTest1234567"))

        val xaiReq = seenRequests.first { it.first.contains("api.x.ai/v1/chat/completions") }
        assertEquals("Bearer xai-test-1234", xaiReq.second["Authorization"])

        val dsReq = seenRequests.first { it.first.contains("api.deepseek.com/chat/completions") }
        assertEquals("Bearer sk-deepseek-test-1234", dsReq.second["Authorization"])
    }

    @Test
    fun providerSwitching_preservesConversationHistoryAcrossAllFiveAdapters() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val capturedBodies = mutableMapOf<SuruAiProvider, JSONObject>()

        val client = buildMockClient { req, rawBody ->
            val url = req.url.toString()
            val json = JSONObject(rawBody)
            when {
                url.contains("generativelanguage.googleapis.com") -> {
                    capturedBodies[SuruAiProvider.GEMINI] = json
                    jsonResponse(req, 200, """{"candidates":[{"content":{"parts":[{"text":"Gemini reply"}]}}]}""")
                }
                url.contains("api.openai.com") -> {
                    capturedBodies[SuruAiProvider.OPENAI] = json
                    jsonResponse(req, 200, """{"choices":[{"message":{"content":"OpenAI reply"}}]}""")
                }
                url.contains("api.anthropic.com") -> {
                    capturedBodies[SuruAiProvider.ANTHROPIC] = json
                    jsonResponse(req, 200, """{"content":[{"type":"text","text":"Claude reply"}]}""")
                }
                url.contains("api.x.ai") -> {
                    capturedBodies[SuruAiProvider.XAI] = json
                    jsonResponse(req, 200, """{"choices":[{"message":{"content":"Grok reply"}}]}""")
                }
                url.contains("api.deepseek.com") -> {
                    capturedBodies[SuruAiProvider.DEEPSEEK] = json
                    jsonResponse(req, 200, """{"choices":[{"message":{"content":"DeepSeek reply"}}]}""")
                }
                else -> jsonResponse(req, 404, "{}")
            }
        }

        val creds = FakeCredentialProvider(
            keys = SuruAiProvider.entries.associateWith { "sk-test-${it.name.lowercase()}-key" }
        )
        val gateway = SuruAiGateway(
            appContext = context,
            httpClient = client,
            credentialProvider = creds,
            baseRetryDelayMs = 1L
        )

        val history = listOf(
            "user" to "Remember my Physics test is Friday.",
            "model" to "Got it, Physics test on Friday."
        )

        // Gemini is default initially
        assertEquals("GEMINI", UserPreferencesState().suruDefaultProvider)

        for (provider in SuruAiProvider.entries) {
            val prefs = UserPreferencesState(
                suruDefaultProvider = provider.name,
                suruDefaultModel = provider.models.first(),
                suruAutoRoutingEnabled = false,
                suruAutoFallbackEnabled = false
            )
            val result = gateway.generateCompletion(
                prefs = prefs,
                taskKind = SuruRoutingTaskKind.CHAT,
                systemInstruction = "Be concise.",
                historyTurns = history,
                userPrompt = "When is my Physics test?",
                expectJson = false
            )
            assertEquals(provider, result.providerUsed)
            val payload = capturedBodies[provider]!!
            val payloadStr = payload.toString()
            assertTrue("$provider must receive prior user turn", payloadStr.contains("Physics test is Friday"))
            assertTrue("$provider must receive prior assistant turn", payloadStr.contains("Got it, Physics test on Friday"))
        }
    }

    @Test
    fun sseStreaming_parsesDeltasAcrossAllProvidersAndExtractsJsonReply() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = buildMockClient { req, _ ->
            val url = req.url.toString()
            when {
                url.contains("api.openai.com") || url.contains("api.x.ai") || url.contains("api.deepseek.com") -> {
                    sseResponse(
                        req,
                        """
                        data: {"choices":[{"delta":{"content":"Hello "}}]}

                        data: {"choices":[{"delta":{"content":"world!"}}]}

                        data: [DONE]
                        """.trimIndent()
                    )
                }
                url.contains("api.anthropic.com") -> {
                    sseResponse(
                        req,
                        """
                        event: content_block_delta
                        data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"Claude "}}

                        event: content_block_delta
                        data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"streams!"}}
                        """.trimIndent()
                    )
                }
                url.contains("generativelanguage.googleapis.com") -> {
                    sseResponse(
                        req,
                        """
                        data: {"candidates":[{"content":{"parts":[{"text":"Gemini "}]}}]}

                        data: {"candidates":[{"content":{"parts":[{"text":"live!"}]}}]}
                        """.trimIndent()
                    )
                }
                else -> jsonResponse(req, 404, "{}")
            }
        }

        val creds = FakeCredentialProvider(
            keys = SuruAiProvider.entries.associateWith { "sk-stream-${it.name.lowercase()}" }
        )
        val gateway = SuruAiGateway(
            appContext = context,
            httpClient = client,
            credentialProvider = creds,
            baseRetryDelayMs = 1L
        )

        val expectedOutputs = mapOf(
            SuruAiProvider.OPENAI to "Hello world!",
            SuruAiProvider.XAI to "Hello world!",
            SuruAiProvider.DEEPSEEK to "Hello world!",
            SuruAiProvider.ANTHROPIC to "Claude streams!",
            SuruAiProvider.GEMINI to "Gemini live!"
        )

        for ((provider, expected) in expectedOutputs) {
            val prefs = UserPreferencesState(
                suruDefaultProvider = provider.name,
                suruDefaultModel = provider.models.first(),
                suruAutoRoutingEnabled = false,
                suruAutoFallbackEnabled = false
            )
            val events = gateway.streamCompletion(
                prefs = prefs,
                taskKind = SuruRoutingTaskKind.CHAT,
                systemInstruction = "Help user.",
                historyTurns = emptyList(),
                userPrompt = "Hi",
                expectJson = false
            ).toList()

            val deltas = events.filterIsInstance<AiGatewayStreamEvent.Delta>()
            val completed = events.filterIsInstance<AiGatewayStreamEvent.Completed>().single()
            assertEquals(2, deltas.size)
            assertEquals(expected, completed.result.rawText)
        }

        // Incremental JSON reply extractor check
        val partialJson = """{"intent":"CHAT","reply":"Line 1\nLine 2"""
        assertEquals("Line 1\nLine 2", AiProviderAdapters.extractPartialReplyForUi(partialJson))
    }

    @Test
    fun errorHandling_classifiesAuthCreditsRateLimitRetriesAndRedactsSecrets() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val secretKey = "sk-super-secret-openai-998877"
        var xaiChatAttempts = 0

        val client = buildMockClient { req, _ ->
            val url = req.url.toString()
            when {
                url.contains("api.openai.com") -> {
                    jsonResponse(req, 401, """{"error":{"message":"Incorrect API key provided: $secretKey"}}""")
                }
                url.contains("api.anthropic.com") -> {
                    jsonResponse(req, 402, """{"error":{"message":"Your credit balance is too low to access the Anthropic API."}}""")
                }
                url.contains("api.x.ai/v1/chat/completions") -> {
                    xaiChatAttempts++
                    if (xaiChatAttempts == 1) {
                        jsonResponse(req, 429, """{"error":{"message":"Rate limit reached"}}""")
                    } else {
                        jsonResponse(req, 200, """{"choices":[{"message":{"content":"Recovered after 429 retry"}}]}""")
                    }
                }
                url.contains("api.x.ai/v1/models") -> {
                    jsonResponse(req, 200, """{"data":[{"id":"grok-3-mini"}]}""")
                }
                url.contains("api.deepseek.com") -> {
                    throw SocketTimeoutException("Read timed out")
                }
                else -> jsonResponse(req, 500, "{}")
            }
        }

        val creds = FakeCredentialProvider(
            keys = mapOf(
                SuruAiProvider.OPENAI to secretKey,
                SuruAiProvider.ANTHROPIC to "sk-ant-valid-123",
                SuruAiProvider.XAI to "xai-valid-123",
                SuruAiProvider.DEEPSEEK to "sk-ds-valid-123"
            )
        )
        val gateway = SuruAiGateway(
            appContext = context,
            httpClient = client,
            credentialProvider = creds,
            baseRetryDelayMs = 1L,
            maxRetryAttempts = 2
        )

        // 1. 401 Auth Error -> Connection Failed + API key NEVER leaked
        val openaiDiag = gateway.testConnection(SuruAiProvider.OPENAI)
        assertEquals(ProviderConnectionStatus.CONNECTION_FAILED, openaiDiag.status)
        assertEquals(AiErrorCategory.AUTH_ERROR, openaiDiag.lastErrorCategory)
        assertFalse(openaiDiag.lastErrorMessage.orEmpty().contains(secretKey))

        // 2. 402 Insufficient Credits
        val claudeDiag = gateway.testConnection(SuruAiProvider.ANTHROPIC)
        assertEquals(ProviderConnectionStatus.CONNECTION_FAILED, claudeDiag.status)
        assertEquals(AiErrorCategory.INSUFFICIENT_CREDITS, claudeDiag.lastErrorCategory)

        // 3. 429 Transient Rate Limit -> Retries automatically and connects
        val xaiDiag = gateway.testConnection(SuruAiProvider.XAI)
        assertEquals(ProviderConnectionStatus.CONNECTED, xaiDiag.status)
        assertEquals(2, xaiChatAttempts)

        // 4. SocketTimeoutException -> Retries twice then Connection Failed
        val dsDiag = gateway.testConnection(SuruAiProvider.DEEPSEEK)
        assertEquals(ProviderConnectionStatus.CONNECTION_FAILED, dsDiag.status)
        assertEquals(AiErrorCategory.NETWORK_TIMEOUT, dsDiag.lastErrorCategory)
    }

    @Test
    fun fallbackBehavior_alwaysNotifiesUserAndRespectsDisabledSetting() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = buildMockClient { req, _ ->
            val url = req.url.toString()
            when {
                url.contains("generativelanguage.googleapis.com") ->
                    jsonResponse(req, 503, """{"error":{"message":"Service temporarily unavailable"}}""")
                url.contains("api.openai.com") ->
                    jsonResponse(req, 200, """{"choices":[{"message":{"content":"Handled by OpenAI"}}]}""")
                else -> jsonResponse(req, 404, "{}")
            }
        }
        val creds = FakeCredentialProvider(
            keys = mapOf(
                SuruAiProvider.GEMINI to "AIzaGeminiTest1234567",
                SuruAiProvider.OPENAI to "sk-openai-test-1234"
            )
        )
        val gateway = SuruAiGateway(
            appContext = context,
            httpClient = client,
            credentialProvider = creds,
            baseRetryDelayMs = 1L,
            maxRetryAttempts = 1
        )

        val withFallback = UserPreferencesState(
            suruDefaultProvider = SuruAiProvider.GEMINI.name,
            suruAutoRoutingEnabled = false,
            suruAutoFallbackEnabled = true
        )
        val res = gateway.generateCompletion(
            prefs = withFallback,
            taskKind = SuruRoutingTaskKind.CHAT,
            systemInstruction = "Help",
            historyTurns = emptyList(),
            userPrompt = "Hello",
            expectJson = false
        )
        assertEquals(SuruAiProvider.OPENAI, res.providerUsed)
        val fallbackNote = requireNotNull(res.fallbackNote)
        assertTrue(fallbackNote.contains("OpenAI"))
        assertTrue(fallbackNote.contains("Gemini"))

        // When fallback is disabled, it MUST fail instead of silently switching
        val withoutFallback = withFallback.copy(suruAutoFallbackEnabled = false)
        var thrown: AiGatewayException? = null
        try {
            gateway.generateCompletion(
                prefs = withoutFallback,
                taskKind = SuruRoutingTaskKind.CHAT,
                systemInstruction = "Help",
                historyTurns = emptyList(),
                userPrompt = "Hello",
                expectJson = false
            )
        } catch (e: AiGatewayException) {
            thrown = e
        }
        assertNotNull(thrown)
        assertEquals(SuruAiProvider.GEMINI, thrown!!.provider)
    }

    @Test
    fun providerDashboardUi_rendersAllFiveProvidersHonestStatusesTimestampsAndErrors() {
        var testedProvider: SuruAiProvider? = null
        val diagnostics = listOf(
            ProviderDiagnostic(
                provider = SuruAiProvider.OPENAI,
                status = ProviderConnectionStatus.CONNECTED,
                configState = ProviderConfigState.API_KEY_DETECTED,
                apiConfigLabel = "OPENAI_API_KEY detected via Secrets",
                selectedModel = "gpt-4.1-mini",
                availableModels = listOf("gpt-4.1-mini", "gpt-4.1"),
                detail = "Live API request verified (gpt-4.1-mini).",
                lastConnectedAtMillis = 1_750_000_000_000L
            ),
            ProviderDiagnostic(
                provider = SuruAiProvider.ANTHROPIC,
                status = ProviderConnectionStatus.CONNECTION_FAILED,
                configState = ProviderConfigState.API_KEY_DETECTED,
                apiConfigLabel = "ANTHROPIC_API_KEY detected via Secrets",
                selectedModel = "claude-sonnet-4-20250514",
                availableModels = listOf("claude-sonnet-4-20250514"),
                detail = "Authentication failed",
                lastErrorMessage = "Anthropic (Claude) authentication failed (HTTP 401).",
                lastErrorCategory = AiErrorCategory.AUTH_ERROR
            ),
            ProviderDiagnostic(
                provider = SuruAiProvider.GEMINI,
                status = ProviderConnectionStatus.NOT_CONFIGURED,
                configState = ProviderConfigState.NOT_CONFIGURED,
                apiConfigLabel = "Not Configured — add GEMINI_API_KEY in Secrets",
                selectedModel = "gemini-2.5-flash",
                availableModels = listOf("gemini-2.5-flash"),
                detail = "Add GEMINI_API_KEY or SURU_AI_PROXY_URL in AI Studio Secrets."
            ),
            ProviderDiagnostic(
                provider = SuruAiProvider.XAI,
                status = ProviderConnectionStatus.NOT_CONFIGURED,
                configState = ProviderConfigState.NOT_CONFIGURED,
                apiConfigLabel = "Not Configured — add XAI_API_KEY in Secrets",
                selectedModel = "grok-3-mini",
                availableModels = listOf("grok-3-mini"),
                detail = "Add XAI_API_KEY or SURU_AI_PROXY_URL in AI Studio Secrets."
            ),
            ProviderDiagnostic(
                provider = SuruAiProvider.DEEPSEEK,
                status = ProviderConnectionStatus.CONFIGURED_UNTESTED,
                configState = ProviderConfigState.API_KEY_DETECTED,
                apiConfigLabel = "DEEPSEEK_API_KEY detected via Secrets",
                selectedModel = "deepseek-chat",
                availableModels = listOf("deepseek-chat", "deepseek-reasoner"),
                detail = "Key configured. Tap Test Connection to verify live access."
            )
        )

        composeRule.setContent {
            LifeOsTheme {
                SuruAiSettingsSheet(
                    state = LifeOsUiState(
                        prefs = UserPreferencesState(),
                        aiProviderDiagnostics = diagnostics
                    ),
                    onDismiss = {},
                    onUpdateGateway = { _, _, _, _, _, _, _, _ -> },
                    onTestConnection = { testedProvider = it },
                    onUpdateVoice = { _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("OpenAI (GPT)").assertIsDisplayed()
        composeRule.onNodeWithText("Connected").assertIsDisplayed()
        composeRule.onNodeWithTag("last_connected_OPENAI").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("error_detail_ANTHROPIC").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("provider_card_GEMINI").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("provider_card_XAI").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("provider_card_DEEPSEEK").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("test_connection_OPENAI").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(SuruAiProvider.OPENAI, testedProvider)
    }
}
