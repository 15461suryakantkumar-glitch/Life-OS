package com.example.data.social

import android.content.Context
import android.net.Uri
import com.example.BuildConfig
import com.example.data.local.UserPreferencesState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SupabaseSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val emailVerified: Boolean,
    val displayName: String = "",
    val isAnonymous: Boolean = false
)

sealed class SupabaseResult<out T> {
    data class Success<T>(val value: T) : SupabaseResult<T>()
    data class Failure(
        val message: String,
        val isConfigMissing: Boolean = false,
        val isUnauthorized: Boolean = false
    ) : SupabaseResult<Nothing>()
}

object FeeloraSupabase {
    @Volatile
    private var cachedClient: SupabaseAuthAndSyncClient? = null

    fun client(context: Context): SupabaseAuthAndSyncClient {
        return cachedClient ?: synchronized(this) {
            cachedClient ?: SupabaseAuthAndSyncClient(context.applicationContext).also {
                cachedClient = it
            }
        }
    }
}

class SupabaseAuthAndSyncClient(
    private val appContext: Context,
    private val baseUrlOverride: String? = null,
    private val anonKeyOverride: String? = null,
    httpClientOverride: OkHttpClient? = null
) {

    val httpClient: OkHttpClient = httpClientOverride ?: OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    fun getConfiguredUrl(): String? {
        val raw = (baseUrlOverride ?: runCatching { BuildConfig.SUPABASE_URL }.getOrDefault(""))
            .trim()
            .trimEnd('/')
        return raw.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }

    fun getConfiguredAnonKey(): String? {
        val raw = (anonKeyOverride ?: runCatching { BuildConfig.SUPABASE_ANON_KEY }.getOrDefault("")).trim()
        return raw.takeIf { it.isNotBlank() && !it.startsWith("YOUR_") && it != "your_api_key_here" }
    }

    fun isConfigured(): Boolean = getConfiguredUrl() != null && getConfiguredAnonKey() != null

    fun configurationStatusMessage(): String {
        return if (isConfigured()) {
            "Connected to FEELORA Cloud"
        } else {
            "Cloud Together sync requires SUPABASE_URL & SUPABASE_ANON_KEY in AI Studio Secrets."
        }
    }

    suspend fun signInAnonymously(): SupabaseResult<SupabaseSession> = withContext(Dispatchers.IO) {
        val baseUrl = getConfiguredUrl() ?: return@withContext notConfiguredError()
        val anonKey = getConfiguredAnonKey() ?: return@withContext notConfiguredError()

        val req = Request.Builder()
            .url("$baseUrl/auth/v1/signup")
            .header("apikey", anonKey)
            .header("Content-Type", "application/json")
            .post("{}".toRequestBody(JSON_MEDIA))
            .build()

        executeAuthRequest(req, fallbackEmail = "", fallbackName = "", isAnonymousFallback = true)
    }

    suspend fun ensureSession(currentPrefs: UserPreferencesState): SupabaseResult<SupabaseSession> {
        if (!isConfigured()) return notConfiguredError()
        val now = System.currentTimeMillis()
        if (currentPrefs.hasValidRemoteSession && currentPrefs.supabaseSessionExpiresAt > now + 60_000L) {
            return SupabaseResult.Success(
                SupabaseSession(
                    userId = currentPrefs.supabaseUserId,
                    email = "",
                    accessToken = currentPrefs.supabaseAccessToken,
                    refreshToken = currentPrefs.supabaseRefreshToken,
                    expiresAtMillis = currentPrefs.supabaseSessionExpiresAt,
                    emailVerified = false,
                    displayName = currentPrefs.userName,
                    isAnonymous = true
                )
            )
        }
        if (currentPrefs.supabaseRefreshToken.isNotBlank()) {
            when (val refreshed = refreshSession(currentPrefs.supabaseRefreshToken)) {
                is SupabaseResult.Success -> return refreshed
                is SupabaseResult.Failure -> if (!refreshed.isUnauthorized) return refreshed
            }
        }
        return signInAnonymously()
    }

    suspend fun refreshSession(refreshToken: String): SupabaseResult<SupabaseSession> = withContext(Dispatchers.IO) {
        val baseUrl = getConfiguredUrl() ?: return@withContext notConfiguredError()
        val anonKey = getConfiguredAnonKey() ?: return@withContext notConfiguredError()
        if (refreshToken.isBlank()) {
            return@withContext SupabaseResult.Failure("Your Together sync token expired. Reconnecting anonymously.", isUnauthorized = true)
        }

        val payload = JSONObject().put("refresh_token", refreshToken)
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
            .header("apikey", anonKey)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        executeAuthRequest(req, fallbackEmail = "", fallbackName = "")
    }

    suspend fun fetchCurrentUser(accessToken: String): SupabaseResult<SupabaseSession> = withContext(Dispatchers.IO) {
        val baseUrl = getConfiguredUrl() ?: return@withContext notConfiguredError()
        val anonKey = getConfiguredAnonKey() ?: return@withContext notConfiguredError()
        if (accessToken.isBlank()) {
            return@withContext SupabaseResult.Failure("Not signed in.", isUnauthorized = true)
        }
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/user")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use SupabaseResult.Failure(
                        message = parseSupabaseError(raw, "Session expired"),
                        isUnauthorized = resp.code == 401 || resp.code == 403
                    )
                }
                val user = JSONObject(raw)
                val userId = user.optString("id")
                val email = user.optString("email")
                val verified = user.optString("email_confirmed_at").isNotBlank() ||
                    user.optString("confirmed_at").isNotBlank()
                val name = user.optJSONObject("user_metadata")?.optString("display_name").orEmpty()
                val anon = user.optBoolean("is_anonymous", email.isBlank())
                SupabaseResult.Success(
                    SupabaseSession(
                        userId = userId,
                        email = email,
                        accessToken = accessToken,
                        refreshToken = "",
                        expiresAtMillis = System.currentTimeMillis() + 3600_000L,
                        emailVerified = verified,
                        displayName = name,
                        isAnonymous = anon
                    )
                )
            }
        }.getOrElse { SupabaseResult.Failure(it.message ?: "Could not verify session.") }
    }

    suspend fun checkUsernameAvailable(
        username: String,
        accessToken: String
    ): SupabaseResult<Boolean> = withContext(Dispatchers.IO) {
        val baseUrl = getConfiguredUrl() ?: return@withContext notConfiguredError()
        val anonKey = getConfiguredAnonKey() ?: return@withContext notConfiguredError()
        val cleanHandle = normalizeHandle(username)
        if (cleanHandle.length < 3) {
            return@withContext SupabaseResult.Failure("Username must be at least 2 characters.")
        }
        val encoded = Uri.encode(cleanHandle)
        val reqBuilder = Request.Builder()
            .url("$baseUrl/rest/v1/profiles?select=id&handle=eq.$encoded&limit=1")
            .header("apikey", anonKey)
        if (accessToken.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer $accessToken")
        } else {
            reqBuilder.header("Authorization", "Bearer $anonKey")
        }

        runCatching {
            httpClient.newCall(reqBuilder.get().build()).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use SupabaseResult.Failure(parseSupabaseError(raw, "Unable to check username availability."))
                }
                val arr = JSONArray(raw)
                SupabaseResult.Success(arr.length() == 0)
            }
        }.getOrElse { SupabaseResult.Failure(it.message ?: "Network error checking username.") }
    }

    suspend fun uploadImageToStorage(
        bucket: String,
        objectPath: String,
        localUri: Uri,
        accessToken: String
    ): SupabaseResult<String> = withContext(Dispatchers.IO) {
        val baseUrl = getConfiguredUrl() ?: return@withContext notConfiguredError()
        val anonKey = getConfiguredAnonKey() ?: return@withContext notConfiguredError()
        if (accessToken.isBlank()) {
            return@withContext SupabaseResult.Failure("Sign in required to upload media.", isUnauthorized = true)
        }
        val bytes = runCatching {
            appContext.contentResolver.openInputStream(localUri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext SupabaseResult.Failure("Could not read selected image.")

        if (bytes.size > 5 * 1024 * 1024) {
            return@withContext SupabaseResult.Failure("Image is larger than 5 MB. Please choose a smaller photo.")
        }

        val mime = appContext.contentResolver.getType(localUri) ?: "image/jpeg"
        val req = Request.Builder()
            .url("$baseUrl/storage/v1/object/$bucket/$objectPath")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $accessToken")
            .header("x-upsert", "true")
            .post(bytes.toRequestBody(mime.toMediaType()))
            .build()

        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use SupabaseResult.Failure(parseSupabaseError(raw, "Image upload failed (HTTP ${resp.code})."))
                }
                SupabaseResult.Success("$baseUrl/storage/v1/object/public/$bucket/$objectPath")
            }
        }.getOrElse { SupabaseResult.Failure(it.message ?: "Upload failed.") }
    }

    suspend fun restGet(pathWithQuery: String, accessToken: String): SupabaseResult<String> =
        executeRest("GET", pathWithQuery, null, accessToken)

    suspend fun restPost(pathWithQuery: String, bodyJson: String, accessToken: String, upsert: Boolean = false): SupabaseResult<String> =
        executeRest("POST", pathWithQuery, bodyJson, accessToken, upsert)

    suspend fun restPatch(pathWithQuery: String, bodyJson: String, accessToken: String): SupabaseResult<String> =
        executeRest("PATCH", pathWithQuery, bodyJson, accessToken)

    suspend fun restDelete(pathWithQuery: String, accessToken: String): SupabaseResult<String> =
        executeRest("DELETE", pathWithQuery, null, accessToken)

    suspend fun restRpc(
        functionName: String,
        bodyJson: String,
        accessToken: String = ""
    ): SupabaseResult<String> = withContext(Dispatchers.IO) {
        val baseUrl = getConfiguredUrl() ?: return@withContext notConfiguredError()
        val anonKey = getConfiguredAnonKey() ?: return@withContext notConfiguredError()
        val bearer = accessToken.ifBlank { anonKey }
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/$functionName")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $bearer")
            .header("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()

        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use SupabaseResult.Failure(
                        message = parseSupabaseError(raw, "RPC failed (HTTP ${resp.code})."),
                        isUnauthorized = resp.code == 401 || resp.code == 403
                    )
                }
                SupabaseResult.Success(raw)
            }
        }.getOrElse { SupabaseResult.Failure(it.message ?: "Network connection unavailable.") }
    }

    private suspend fun executeRest(
        method: String,
        pathWithQuery: String,
        bodyJson: String?,
        accessToken: String,
        upsert: Boolean = false
    ): SupabaseResult<String> = withContext(Dispatchers.IO) {
        val baseUrl = getConfiguredUrl() ?: return@withContext notConfiguredError()
        val anonKey = getConfiguredAnonKey() ?: return@withContext notConfiguredError()
        if (accessToken.isBlank()) {
            return@withContext SupabaseResult.Failure("Sign in required for cloud sync.", isUnauthorized = true)
        }
        val builder = Request.Builder()
            .url("$baseUrl/rest/v1/$pathWithQuery")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")

        if (method == "POST") {
            builder.header(
                "Prefer",
                if (upsert) "resolution=merge-duplicates,return=representation" else "return=representation"
            )
        } else if (method == "PATCH") {
            builder.header("Prefer", "return=representation")
        }

        val reqBody = bodyJson?.toRequestBody(JSON_MEDIA)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(reqBody ?: "{}".toRequestBody(JSON_MEDIA))
            "PATCH" -> builder.patch(reqBody ?: "{}".toRequestBody(JSON_MEDIA))
            "DELETE" -> builder.delete()
        }

        runCatching {
            httpClient.newCall(builder.build()).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use SupabaseResult.Failure(
                        message = parseSupabaseError(raw, "Cloud request failed (HTTP ${resp.code})."),
                        isUnauthorized = resp.code == 401 || resp.code == 403
                    )
                }
                SupabaseResult.Success(raw)
            }
        }.getOrElse { SupabaseResult.Failure(it.message ?: "Network connection unavailable.") }
    }

    private fun executeAuthRequest(
        req: Request,
        fallbackEmail: String,
        fallbackName: String,
        isAnonymousFallback: Boolean = false
    ): SupabaseResult<SupabaseSession> {
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use SupabaseResult.Failure(
                        message = parseSupabaseError(raw, "Authentication failed (HTTP ${resp.code})."),
                        isUnauthorized = resp.code == 401
                    )
                }
                val root = JSONObject(raw)
                val accessToken = root.optString("access_token")
                val refreshToken = root.optString("refresh_token")
                val expiresIn = root.optLong("expires_in", 3600L)
                val userObj = root.optJSONObject("user") ?: root
                val userId = userObj.optString("id")
                val email = userObj.optString("email").ifBlank { fallbackEmail }
                val verified = userObj.optString("email_confirmed_at").isNotBlank() ||
                    userObj.optString("confirmed_at").isNotBlank()
                val name = userObj.optJSONObject("user_metadata")?.optString("display_name")
                    ?.takeIf { it.isNotBlank() } ?: fallbackName
                val isAnon = userObj.optBoolean("is_anonymous", isAnonymousFallback && email.isBlank())

                if (userId.isBlank()) {
                    return@use SupabaseResult.Failure("Unexpected server response during sign in.")
                }

                SupabaseResult.Success(
                    SupabaseSession(
                        userId = userId,
                        email = email,
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresAtMillis = System.currentTimeMillis() + (expiresIn * 1000L),
                        emailVerified = verified,
                        displayName = name,
                        isAnonymous = isAnon
                    )
                )
            }
        }.getOrElse { SupabaseResult.Failure(it.message ?: "Unable to connect to authentication service.") }
    }

    private fun notConfiguredError() = SupabaseResult.Failure(
        message = "FEELORA cloud backend is not configured yet. Add SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets.",
        isConfigMissing = true
    )

    private fun parseSupabaseError(raw: String?, fallback: String): String {
        if (raw.isNullOrBlank()) return fallback
        return runCatching {
            val obj = JSONObject(raw)
            obj.optString("error_description")
                .ifBlank { obj.optString("msg") }
                .ifBlank { obj.optString("message") }
                .ifBlank { obj.optString("error") }
                .ifBlank { fallback }
        }.getOrDefault(fallback)
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun normalizeHandle(raw: String): String {
            val stripped = raw.trim().removePrefix("@")
                .lowercase()
                .replace(Regex("[^a-z0-9._]"), "")
            return if (stripped.isBlank()) "" else "@$stripped"
        }
    }
}
