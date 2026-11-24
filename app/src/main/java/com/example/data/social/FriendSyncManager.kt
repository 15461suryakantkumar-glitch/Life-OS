package com.example.data.social

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.example.data.local.FriendEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserPreferencesState
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

private const val TAG = "FriendSyncManager"
private const val NSD_SERVICE_TYPE = "_lifeos._tcp."
private const val TOKEN_PREFIX = "LIFEOS1:"

data class NearbyFriendPeer(
    val serviceName: String,
    val host: InetAddress,
    val port: Int
)

data class ParsedFriendInvite(
    val displayName: String,
    val handle: String,
    val friendCode: String,
    val level: Int,
    val xp: Int,
    val streakDays: Int,
    val focusMinutesToday: Int,
    val challengesCompleted: Int,
    val challengesTotal: Int,
    val petStageName: String,
    val shareXp: Boolean,
    val shareStreaks: Boolean,
    val shareFocus: Boolean,
    val shareChallenges: Boolean,
    val syncedAtMillis: Long,
    val isSnapshotVerified: Boolean
) {
    fun toFriendEntity(existingId: Long = 0L, statusNote: String? = null): FriendEntity {
        val sharesAny = shareXp || shareStreaks || shareFocus || shareChallenges
        return FriendEntity(
            id = existingId,
            displayName = displayName,
            handle = handle,
            friendCode = friendCode,
            level = level,
            xp = xp,
            streakDays = streakDays,
            focusMinutesToday = focusMinutesToday,
            challengesCompleted = challengesCompleted,
            challengesTotal = challengesTotal,
            petStageName = petStageName,
            sharesStats = sharesAny,
            shareXp = shareXp,
            shareStreaks = shareStreaks,
            shareFocus = shareFocus,
            shareChallenges = shareChallenges,
            statusText = statusNote ?: if (isSnapshotVerified) "Synced snapshot" else "Added via invite code",
            lastSyncedAtMillis = syncedAtMillis
        )
    }
}

object FriendSyncCodec {

    fun buildShareToken(
        prefs: UserPreferencesState,
        challengesCompleted: Int,
        challengesTotal: Int
    ): String {
        val code = prefs.friendCode.ifBlank {
            UserPreferencesRepository.generateFriendCode(prefs.userHandle, prefs.userName)
        }
        val safeName = escapeJson(prefs.userName.ifBlank { "Operator" })
        val safeHandle = escapeJson(prefs.userHandle.ifBlank { "@operator.os" })
        val safeCode = escapeJson(code)
        val safePetStage = escapeJson(prefs.petStage.title)
        val xpVal = if (prefs.shareXpWithFriends) prefs.totalXp else 0
        val lvVal = if (prefs.shareXpWithFriends) prefs.level else 1
        val stVal = if (prefs.shareStreaksWithFriends) prefs.dailyStreak else 0
        val fcVal = if (prefs.shareFocusWithFriends) prefs.focusMinutesLoggedToday else 0
        val ccVal = if (prefs.shareChallengesWithFriends) challengesCompleted else 0
        val ctVal = challengesTotal.coerceAtLeast(1)
        val tsVal = System.currentTimeMillis()

        val json = buildString {
            append('{')
            append("\"v\":1,")
            append("\"n\":\"").append(safeName).append("\",")
            append("\"h\":\"").append(safeHandle).append("\",")
            append("\"c\":\"").append(safeCode).append("\",")
            append("\"lv\":").append(lvVal).append(',')
            append("\"xp\":").append(xpVal).append(',')
            append("\"st\":").append(stVal).append(',')
            append("\"fc\":").append(fcVal).append(',')
            append("\"cc\":").append(ccVal).append(',')
            append("\"ct\":").append(ctVal).append(',')
            append("\"pt\":\"").append(safePetStage).append("\",")
            append("\"sXp\":").append(prefs.shareXpWithFriends).append(',')
            append("\"sSt\":").append(prefs.shareStreaksWithFriends).append(',')
            append("\"sFc\":").append(prefs.shareFocusWithFriends).append(',')
            append("\"sCh\":").append(prefs.shareChallengesWithFriends).append(',')
            append("\"ts\":").append(tsVal)
            append('}')
        }
        return TOKEN_PREFIX + base64UrlEncode(json.toByteArray(Charsets.UTF_8))
    }

    fun buildInviteLink(
        prefs: UserPreferencesState,
        challengesCompleted: Int,
        challengesTotal: Int
    ): String {
        val token = buildShareToken(prefs, challengesCompleted, challengesTotal)
        return "lifeos://friend?data=$token"
    }

    fun extractTokenFromRaw(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains("data=")) {
            val afterData = trimmed.substringAfter("data=").substringBefore("&").trim()
            if (afterData.startsWith(TOKEN_PREFIX)) return afterData
            return runCatching { Uri.parse(trimmed).getQueryParameter("data") }.getOrNull()
        }
        val idx = trimmed.indexOf(TOKEN_PREFIX)
        if (idx >= 0) {
            return trimmed.substring(idx).lineSequence().firstOrNull()?.trim()?.substringBefore(" ")
        }
        return null
    }

    fun parseInput(rawInput: String): ParsedFriendInvite? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        val extractedToken = extractTokenFromRaw(trimmed)
        if (extractedToken != null && extractedToken.startsWith(TOKEN_PREFIX)) {
            decodePayload(extractedToken.removePrefix(TOKEN_PREFIX))?.let { return it }
        }

        // Check raw JSON paste
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            decodeJson(trimmed)?.let { return it }
        }

        // Check short Friend Code format e.g. LIFE-ARIA-4821
        val codeRegex = Regex("""(?i)^LIFE-[A-Z0-9]{2,8}(?:-\d{3,6})?$""")
        if (codeRegex.matches(trimmed)) {
            val upper = trimmed.uppercase()
            val shortName = upper.removePrefix("LIFE-").substringBefore("-")
                .lowercase()
                .replaceFirstChar { it.uppercase() }
                .ifBlank { "Friend" }
            return ParsedFriendInvite(
                displayName = shortName,
                handle = "@${shortName.lowercase()}.os",
                friendCode = upper,
                level = 1,
                xp = 0,
                streakDays = 0,
                focusMinutesToday = 0,
                challengesCompleted = 0,
                challengesTotal = 4,
                petStageName = "KUTTA · Spark",
                shareXp = true,
                shareStreaks = true,
                shareFocus = false,
                shareChallenges = true,
                syncedAtMillis = System.currentTimeMillis(),
                isSnapshotVerified = false
            )
        }

        return null
    }

    private fun decodePayload(encoded: String): ParsedFriendInvite? {
        return try {
            val decodedBytes = base64UrlDecode(encoded)
            val jsonStr = String(decodedBytes, Charsets.UTF_8)
            decodeJson(jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeJson(jsonStr: String): ParsedFriendInvite? {
        return try {
            val map = parseFlatJsonObject(jsonStr) ?: return null
            val name = map["n"]?.trim().orEmpty().ifBlank { return null }
            val rawHandle = map["h"]?.trim().orEmpty().ifBlank { "@${name.lowercase().replace(" ", "")}" }
            val handle = if (rawHandle.startsWith("@")) rawHandle else "@$rawHandle"
            val code = map["c"]?.trim().orEmpty().ifBlank {
                UserPreferencesRepository.generateFriendCode(handle, name)
            }
            ParsedFriendInvite(
                displayName = name,
                handle = handle,
                friendCode = code,
                level = (map["lv"]?.toIntOrNull() ?: 1).coerceAtLeast(1),
                xp = (map["xp"]?.toIntOrNull() ?: 0).coerceAtLeast(0),
                streakDays = (map["st"]?.toIntOrNull() ?: 0).coerceAtLeast(0),
                focusMinutesToday = (map["fc"]?.toIntOrNull() ?: 0).coerceAtLeast(0),
                challengesCompleted = (map["cc"]?.toIntOrNull() ?: 0).coerceAtLeast(0),
                challengesTotal = (map["ct"]?.toIntOrNull() ?: 4).coerceAtLeast(1),
                petStageName = map["pt"]?.ifBlank { "KUTTA" } ?: "KUTTA",
                shareXp = map["sXp"]?.toBooleanStrictOrNull() ?: true,
                shareStreaks = map["sSt"]?.toBooleanStrictOrNull() ?: true,
                shareFocus = map["sFc"]?.toBooleanStrictOrNull() ?: false,
                shareChallenges = map["sCh"]?.toBooleanStrictOrNull() ?: true,
                syncedAtMillis = map["ts"]?.toLongOrNull() ?: System.currentTimeMillis(),
                isSnapshotVerified = true
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun escapeJson(value: String): String {
        val sb = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun parseFlatJsonObject(json: String): Map<String, String>? {
        val s = json.trim()
        if (!s.startsWith("{") || !s.endsWith("}")) return null
        val result = LinkedHashMap<String, String>()
        var i = 1
        val end = s.length - 1

        fun skipWs() {
            while (i < end && s[i].isWhitespace()) i++
        }

        fun readQuoted(): String? {
            if (i >= end || s[i] != '"') return null
            i++
            val sb = StringBuilder()
            while (i < end) {
                val c = s[i++]
                if (c == '"') return sb.toString()
                if (c == '\\' && i < end) {
                    val esc = s[i++]
                    when (esc) {
                        '"', '\\', '/' -> sb.append(esc)
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(c)
                }
            }
            return null
        }

        while (i < end) {
            skipWs()
            if (i >= end) break
            if (s[i] == ',') {
                i++
                continue
            }
            val key = readQuoted() ?: return null
            skipWs()
            if (i >= end || s[i] != ':') return null
            i++
            skipWs()
            if (i >= end) return null
            val value = if (s[i] == '"') {
                readQuoted() ?: return null
            } else {
                val start = i
                while (i < end && s[i] != ',' && !s[i].isWhitespace()) i++
                s.substring(start, i)
            }
            result[key] = value
        }
        return result
    }

    // Pure-Kotlin RFC 4648 Base64URL (works across all Android API levels & JVM unit tests)
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private fun base64UrlEncode(data: ByteArray): String {
        val out = StringBuilder((data.size * 4 + 2) / 3)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val hasB1 = i + 1 < data.size
            val hasB2 = i + 2 < data.size
            val b1 = if (hasB1) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (hasB2) data[i + 2].toInt() and 0xFF else 0

            out.append(ALPHABET[b0 ushr 2])
            out.append(ALPHABET[((b0 and 0x03) shl 4) or (b1 ushr 4)])
            if (hasB1) {
                out.append(ALPHABET[((b1 and 0x0F) shl 2) or (b2 ushr 6)])
            }
            if (hasB2) {
                out.append(ALPHABET[b2 and 0x3F])
            }
            i += 3
        }
        return out.toString()
    }

    private fun base64UrlDecode(text: String): ByteArray {
        val clean = text.trim().trimEnd('=')
        val rev = IntArray(128) { -1 }
        ALPHABET.forEachIndexed { idx, ch -> rev[ch.code] = idx }
        rev['+'.code] = 62
        rev['/'.code] = 63

        val bytes = ArrayList<Byte>((clean.length * 3) / 4)
        var buffer = 0
        var bitsCollected = 0
        for (ch in clean) {
            val code = ch.code
            val val6 = if (code in 0..127) rev[code] else -1
            if (val6 < 0) continue
            buffer = (buffer shl 6) or val6
            bitsCollected += 6
            if (bitsCollected >= 8) {
                bitsCollected -= 8
                bytes.add(((buffer ushr bitsCollected) and 0xFF).toByte())
            }
        }
        return bytes.toByteArray()
    }
}

class FriendSyncManager(private val appContext: Context) {

    private val _nearbyPeers = MutableStateFlow<List<NearbyFriendPeer>>(emptyList())
    val nearbyPeers: StateFlow<List<NearbyFriendPeer>> = _nearbyPeers.asStateFlow()

    private val _isPeerRadarActive = MutableStateFlow(false)
    val isPeerRadarActive: StateFlow<Boolean> = _isPeerRadarActive.asStateFlow()

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var latestOutgoingToken: String = ""

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

    fun updateOutgoingToken(token: String) {
        latestOutgoingToken = token
    }

    fun startNearbyPeerSync(scope: CoroutineScope, serviceLabel: String, outgoingToken: String) {
        latestOutgoingToken = outgoingToken
        if (_isPeerRadarActive.value) return

        val manager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = manager
        _isPeerRadarActive.value = true
        _nearbyPeers.value = emptyList()

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val ss = ServerSocket(0)
                serverSocket = ss
                val localPort = ss.localPort

                val info = NsdServiceInfo().apply {
                    serviceName = "LIFEOS_${serviceLabel.replace(Regex("[^A-Za-z0-9]"), "").take(12).ifBlank { "Node" }}"
                    serviceType = NSD_SERVICE_TYPE
                    port = localPort
                }

                val regListener = object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
                    override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
                    override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                }
                registrationListener = regListener
                manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, regListener)

                val discListener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(regType: String) {}
                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        if (!serviceInfo.serviceType.contains("_lifeos")) return
                        if (serviceInfo.serviceName == info.serviceName) return
                        try {
                            @Suppress("DEPRECATION")
                            manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {}
                                override fun onServiceResolved(resolved: NsdServiceInfo) {
                                    @Suppress("DEPRECATION")
                                    val host = resolved.host ?: return
                                    val peer = NearbyFriendPeer(
                                        serviceName = resolved.serviceName,
                                        host = host,
                                        port = resolved.port
                                    )
                                    val current = _nearbyPeers.value.toMutableList()
                                    if (current.none { it.serviceName == peer.serviceName }) {
                                        current.add(peer)
                                        _nearbyPeers.value = current
                                    }
                                }
                            })
                        } catch (_: Exception) {
                        }
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        _nearbyPeers.value = _nearbyPeers.value.filterNot { it.serviceName == serviceInfo.serviceName }
                    }

                    override fun onDiscoveryStopped(serviceType: String) {}
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                }
                discoveryListener = discListener
                manager.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discListener)

                while (!ss.isClosed) {
                    val client = ss.accept()
                    launch(Dispatchers.IO) {
                        client.use { sock ->
                            sock.soTimeout = 3000
                            val writer = PrintWriter(sock.getOutputStream(), true)
                            writer.println(latestOutgoingToken)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Nearby Wi-Fi peer radar stopped: ${e.message}")
            }
        }
    }

    suspend fun fetchFromNearbyPeer(peer: NearbyFriendPeer): ParsedFriendInvite? = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(peer.host, peer.port), 3000)
                socket.soTimeout = 3000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val line = reader.readLine()?.trim().orEmpty()
                FriendSyncCodec.parseInput(line)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not sync peer ${peer.serviceName}: ${e.message}")
            null
        }
    }

    fun stopNearbyPeerSync() {
        _isPeerRadarActive.value = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null

        val manager = nsdManager
        if (manager != null) {
            registrationListener?.let {
                try {
                    manager.unregisterService(it)
                } catch (_: Exception) {
                }
            }
            discoveryListener?.let {
                try {
                    manager.stopServiceDiscovery(it)
                } catch (_: Exception) {
                }
            }
        }
        registrationListener = null
        discoveryListener = null
    }
}
