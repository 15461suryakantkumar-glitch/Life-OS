package com.example.data.social

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserPreferencesState
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Online invite-link & Friend Code codec for Together One.
 * Replaces legacy static base64 snapshot blobs with lightweight shareable invite links
 * (`lifeos://friend?code=...` and `https://feelora.app/invite?code=...`) backed by Supabase lookup.
 */
object FriendSyncCodec {

    private val VALID_CODE_OR_HANDLE = Regex("""^[A-Za-z0-9@._-]{3,64}$""")

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun urlDecode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)

    fun resolveFriendCode(prefs: UserPreferencesState): String {
        return prefs.friendCode.trim().uppercase().ifBlank {
            UserPreferencesRepository.generateFriendCode(prefs.userHandle, prefs.userName)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun buildShareToken(
        prefs: UserPreferencesState,
        challengesCompleted: Int = 0,
        challengesTotal: Int = 4
    ): String = resolveFriendCode(prefs)

    fun buildInviteLink(friendCode: String): String {
        val clean = friendCode.trim().uppercase()
        return "https://feelora.app/invite?code=${urlEncode(clean)}"
    }

    fun buildDeepLink(friendCode: String): String {
        val clean = friendCode.trim().uppercase()
        return "lifeos://friend?code=${urlEncode(clean)}"
    }

    @Suppress("UNUSED_PARAMETER")
    fun buildInviteLink(
        prefs: UserPreferencesState,
        challengesCompleted: Int = 0,
        challengesTotal: Int = 4
    ): String = buildInviteLink(resolveFriendCode(prefs))

    /**
     * Extracts a sanitized friend code or @handle from:
     * - `lifeos://friend?code=FLR-XXXX-1234`
     * - `https://feelora.app/invite?code=FLR-XXXX-1234`
     * - `feelora://invite?code=FLR-XXXX-1234`
     * - Raw `FLR-XXXX-1234` / `LIFE-XXXX-1234` / `@handle`
     */
    fun extractInviteCode(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        val candidateFromUri = if (trimmed.contains("://") || trimmed.contains("code=")) {
            val rawQuery = runCatching { URI(trimmed).rawQuery }.getOrNull()
                ?: trimmed.substringAfter("?", "")
            val params = rawQuery.split("&")
                .mapNotNull { part ->
                    val eq = part.indexOf('=')
                    if (eq <= 0) null
                    else urlDecode(part.substring(0, eq)) to urlDecode(part.substring(eq + 1))
                }
                .toMap()
            (params["code"] ?: params["friend_code"] ?: params["handle"])?.trim()
                ?: if (trimmed.contains("code=")) {
                    trimmed.substringAfter("code=").substringBefore("&").substringBefore("#").trim()
                } else null
        } else {
            null
        }

        val candidate = (candidateFromUri ?: trimmed)
            .lineSequence()
            .firstOrNull()
            ?.trim()
            ?.substringBefore(" ")
            .orEmpty()

        if (!VALID_CODE_OR_HANDLE.matches(candidate)) return null
        return if (candidate.startsWith("@")) {
            SupabaseAuthAndSyncClient.normalizeHandle(candidate).takeIf { it.length >= 3 }
        } else if (candidate.uppercase().startsWith("FLR-") || candidate.uppercase().startsWith("LIFE-")) {
            candidate.uppercase()
        } else if (candidate.contains("-")) {
            candidate.uppercase()
        } else {
            // Treat bare username lookup cleanly as @handle
            SupabaseAuthAndSyncClient.normalizeHandle(candidate).takeIf { it.length >= 3 }
        }
    }
}

class FriendSyncManager(private val appContext: Context) {

    fun observeNetworkOnline(): Flow<Boolean> = callbackFlow {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        fun checkCurrent(): Boolean {
            return try {
                val active = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(active) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } catch (_: Exception) {
                false
            }
        }

        trySend(checkCurrent())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(checkCurrent())
            }

            override fun onLost(network: Network) {
                trySend(checkCurrent())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        val registered = try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
            true
        } catch (_: Exception) {
            false
        }

        awaitClose {
            if (registered) {
                try {
                    cm.unregisterNetworkCallback(callback)
                } catch (_: Exception) {
                }
            }
        }
    }.distinctUntilChanged()

    fun isFirebaseConfigured(): Boolean {
        return try {
            FirebaseApp.getApps(appContext).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }
}
