package com.example.data.social

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.local.FriendEntity
import com.example.data.local.LifeOsDatabase
import com.example.data.local.TogetherOutboxEntity
import com.example.data.local.UserAvatarPreset
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserPreferencesState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val TOGETHER_TAG = "TogetherRepository"

enum class TogetherCompetitionMode(
    val title: String,
    val description: String,
    val metricUnit: String
) {
    XP_SPRINT("XP Sprint", "Race to earn the most verified XP together", "XP"),
    TASK_FINISH("Task Finish Challenge", "Complete planned daily tasks consistently", "tasks"),
    STUDY_FOCUS_RACE("Study Focus Race", "Log distraction-free deep focus minutes", "min"),
    STREAK_CHALLENGE("Streak Challenge", "Maintain active daily study & habit momentum", "days"),
    CONSISTENCY_DUEL("Consistency Duel", "Friendly overall daily execution score duel", "pts")
}

enum class TogetherReaction(val label: String, val badgeText: String) {
    CHEER("Cheer", "Cheered"),
    PROUD("Proud", "Proud"),
    KEEP_GOING("Keep going", "Keep going"),
    HIGH_FIVE("High five", "High five")
}

data class TogetherFriendProfile(
    val userId: String,
    val displayName: String,
    val handle: String,
    val bio: String = "",
    val friendCode: String = "",
    val avatarPreset: UserAvatarPreset = UserAvatarPreset.STAR_BUNNY,
    val avatarUrl: String = "",
    val shareXp: Boolean = true,
    val shareStreaks: Boolean = true,
    val shareFocus: Boolean = false,
    val shareChallenges: Boolean = true,
    val level: Int = 1,
    val xp: Int = 0,
    val dailyStreak: Int = 0,
    val focusMinutesToday: Int = 0,
    val tasksCompletedToday: Int = 0,
    val consistencyScore: Int = 0,
    val challengesCompleted: Int = 0,
    val unreadCount: Int = 0,
    val lastMessagePreview: String = "",
    val lastMessageAtMillis: Long = 0L,
    val presenceStatus: String = "",
    val isFocusingNow: Boolean = false,
    val lastActiveNote: String = ""
)

sealed interface TogetherConnectStatus {
    data object Idle : TogetherConnectStatus
    data object Loading : TogetherConnectStatus
    data class Success(val message: String, val friendName: String = "") : TogetherConnectStatus
    data class Error(val message: String) : TogetherConnectStatus
}

data class TogetherFriendRequest(
    val id: String,
    val requesterId: String,
    val addresseeId: String,
    val requesterName: String,
    val requesterHandle: String,
    val status: String, // PENDING | ACCEPTED | DECLINED | BLOCKED
    val isIncoming: Boolean,
    val createdAtMillis: Long
)

data class TogetherMessage(
    val id: String,
    val clientMessageId: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val content: String,
    val mediaUrl: String? = null,
    val deliveryStatus: String = "SENT", // QUEUED | SENT | DELIVERED | READ
    val createdAtMillis: Long
)

data class TogetherSharedGoal(
    val id: String,
    val creatorId: String,
    val partnerId: String?,
    val partnerName: String?,
    val title: String,
    val description: String,
    val targetValue: Int,
    val currentValue: Int,
    val unitLabel: String,
    val isCompleted: Boolean,
    val updatedAtMillis: Long
)

data class TogetherCompetition(
    val id: String,
    val creatorId: String,
    val partnerId: String?,
    val partnerName: String?,
    val title: String,
    val mode: TogetherCompetitionMode,
    val creatorScore: Int,
    val partnerScore: Int,
    val daysRemaining: Int,
    val isActive: Boolean,
    val createdAtMillis: Long
)

data class TogetherActivityItem(
    val id: String,
    val userId: String,
    val authorName: String,
    val summary: String,
    val createdAtMillis: Long,
    val reactionCounts: Map<TogetherReaction, Int> = emptyMap(),
    val myReaction: TogetherReaction? = null
)

data class TogetherState(
    val isCloudConfigured: Boolean = false,
    val isSyncing: Boolean = false,
    val isRealtimeConnected: Boolean = false,
    val statusNote: String = "",
    val friends: List<TogetherFriendProfile> = emptyList(),
    val incomingRequests: List<TogetherFriendRequest> = emptyList(),
    val outgoingRequests: List<TogetherFriendRequest> = emptyList(),
    val blockedUserIds: Set<String> = emptySet(),
    val messagesByConversation: Map<String, List<TogetherMessage>> = emptyMap(),
    val outboxCount: Int = 0,
    val sharedGoals: List<TogetherSharedGoal> = emptyList(),
    val competitions: List<TogetherCompetition> = emptyList(),
    val activities: List<TogetherActivityItem> = emptyList(),
    val togetherStreakDays: Int = 0,
    val myPresenceStatus: String = "Ready to study",
    val myQuickNote: String = "",
    val pendingConnectCode: String = "",
    val quickConnectStatus: TogetherConnectStatus = TogetherConnectStatus.Idle
)

/**
 * Enforces hard zero-leakage privacy guarantees before any profile snapshot leaves the device.
 * Private local data (Cycle Care, Quick Notes / Journals, Daily Check-In notes, SURU chat/memories)
 * is strictly excluded by construction and never serialized into Together payloads.
 */
object TogetherPrivacyFilter {

    fun buildSafeProfilePayload(
        userId: String,
        prefs: UserPreferencesState,
        tasksCompletedToday: Int,
        consistencyScore: Int,
        challengesCompleted: Int
    ): JSONObject {
        val code = prefs.friendCode.ifBlank {
            UserPreferencesRepository.generateFriendCode(prefs.userHandle, prefs.userName)
        }
        return JSONObject().apply {
            put("id", userId)
            put("display_name", prefs.userName.ifBlank { "FEELORA Friend" })
            put("handle", prefs.userHandle.ifBlank { "@feelora" })
            put("bio", prefs.userBio.take(160))
            put("preferred_language", prefs.preferredLanguage)
            put("theme_preference", prefs.themeMode.name)
            put("friend_code", code)
            put("avatar_preset", prefs.avatarPreset.name)
            put("avatar_url", prefs.avatarPhotoUri.takeIf { it.startsWith("http") }.orEmpty())
            put("share_xp", prefs.shareXpWithFriends)
            put("share_streaks", prefs.shareStreaksWithFriends)
            put("share_focus", prefs.shareFocusWithFriends)
            put("share_challenges", prefs.shareChallengesWithFriends)
            put("level", if (prefs.shareXpWithFriends) prefs.level else 1)
            put("xp", if (prefs.shareXpWithFriends) prefs.totalXp else 0)
            put("daily_streak", if (prefs.shareStreaksWithFriends) prefs.dailyStreak else 0)
            put("focus_minutes_today", if (prefs.shareFocusWithFriends) prefs.focusMinutesLoggedToday else 0)
            put("tasks_completed_today", if (prefs.shareChallengesWithFriends) tasksCompletedToday else 0)
            put("consistency_score", if (prefs.shareChallengesWithFriends) consistencyScore else 0)
            put("challenges_completed", if (prefs.shareChallengesWithFriends) challengesCompleted else 0)
            put("updated_at", System.currentTimeMillis())
        }
    }

    fun conversationIdFor(userA: String, userB: String): String {
        return if (userA <= userB) "${userA}_$userB" else "${userB}_$userA"
    }
}

class TogetherRepository(
    private val appContext: Context,
    private val supabase: SupabaseAuthAndSyncClient
) {
    private val db = LifeOsDatabase.getInstance(appContext)
    private val _state = MutableStateFlow(TogetherState(isCloudConfigured = supabase.isConfigured()))
    val state: StateFlow<TogetherState> = _state.asStateFlow()

    private val localFallbackMessages = LinkedHashMap<String, MutableList<TogetherMessage>>()
    private var syncJob: Job? = null
    private var realtimeSocket: WebSocket? = null
    private var currentUserId: String = ""
    private var currentAccessToken: String = ""

    fun refreshConfigurationState() {
        _state.update {
            it.copy(
                isCloudConfigured = supabase.isConfigured(),
                statusNote = ""
            )
        }
    }

    fun setPendingConnectCode(code: String) {
        val clean = FriendSyncCodec.extractCodeForInputField(code)
        _state.update {
            it.copy(
                pendingConnectCode = clean,
                quickConnectStatus = TogetherConnectStatus.Idle
            )
        }
    }

    fun clearQuickConnectStatus() {
        _state.update { it.copy(quickConnectStatus = TogetherConnectStatus.Idle) }
    }

    fun startRealtimeSync(scope: CoroutineScope, userId: String, accessToken: String) {
        currentUserId = userId
        currentAccessToken = accessToken
        _state.update { it.copy(isCloudConfigured = supabase.isConfigured()) }
        if (!supabase.isConfigured() || userId.isBlank() || accessToken.isBlank()) {
            return
        }

        syncJob?.cancel()
        connectRealtimeSocket(userId, accessToken)

        syncJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching {
                    flushOutboxInternal()
                    refreshAllInternal()
                }.onFailure { err ->
                    if (err is CancellationException) throw err
                    Log.w(TOGETHER_TAG, "Sync tick issue: ${err.message}")
                }
                delay(12_000L)
            }
        }
    }

    fun stopRealtimeSync() {
        syncJob?.cancel()
        syncJob = null
        runCatching { realtimeSocket?.close(1000, "Stopped") }
        realtimeSocket = null
        _state.update { it.copy(isRealtimeConnected = false) }
    }

    private fun connectRealtimeSocket(userId: String, accessToken: String) {
        val baseUrl = supabase.getConfiguredUrl() ?: return
        val anonKey = supabase.getConfiguredAnonKey() ?: return
        val wsBase = baseUrl.replace("https://", "wss://").replace("http://", "ws://")
        val wsUrl = "$wsBase/realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0"
        runCatching { realtimeSocket?.close(1000, "Reconnect") }

        val req = Request.Builder().url(wsUrl).build()
        realtimeSocket = supabase.httpClient.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val joinMsg = JSONObject().apply {
                    put("topic", "realtime:public:together_messages")
                    put("event", "phx_join")
                    put(
                        "payload",
                        JSONObject().put(
                            "config",
                            JSONObject().put("postgres_changes", JSONArray().put(
                                JSONObject()
                                    .put("event", "*")
                                    .put("schema", "public")
                                    .put("table", "together_messages")
                            ))
                        ).put("access_token", accessToken)
                    )
                    put("ref", "1")
                }
                webSocket.send(joinMsg.toString())
                _state.update { it.copy(isRealtimeConnected = true) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("together_messages") || text.contains("INSERT") || text.contains("UPDATE")) {
                    syncJob?.let {
                        // Immediate pull will occur cleanly; also trigger one-off background fetch
                        CoroutineScope(Dispatchers.IO).launch {
                            runCatching { refreshMessagesAndCounts(userId, accessToken) }
                        }
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _state.update { it.copy(isRealtimeConnected = false) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _state.update { it.copy(isRealtimeConnected = false) }
            }
        })
    }

    suspend fun syncMySafeProfile(
        userId: String,
        accessToken: String,
        prefs: UserPreferencesState,
        tasksCompletedToday: Int,
        consistencyScore: Int,
        challengesCompleted: Int
    ): SupabaseResult<Unit> {
        if (!supabase.isConfigured() || userId.isBlank() || accessToken.isBlank()) {
            return SupabaseResult.Failure(supabase.configurationStatusMessage(), isConfigMissing = true)
        }
        val payload = TogetherPrivacyFilter.buildSafeProfilePayload(
            userId = userId,
            prefs = prefs,
            tasksCompletedToday = tasksCompletedToday,
            consistencyScore = consistencyScore,
            challengesCompleted = challengesCompleted
        )
        return when (val res = supabase.restPost("profiles", payload.toString(), accessToken, upsert = true)) {
            is SupabaseResult.Success -> SupabaseResult.Success(Unit)
            is SupabaseResult.Failure -> res
        }
    }

    suspend fun refreshAll(): SupabaseResult<Unit> = refreshAllInternal()

    private suspend fun refreshAllInternal(): SupabaseResult<Unit> {
        val uid = currentUserId
        val token = currentAccessToken
        if (!supabase.isConfigured() || uid.isBlank() || token.isBlank()) {
            return SupabaseResult.Failure(supabase.configurationStatusMessage(), isConfigMissing = true)
        }

        _state.update { it.copy(isSyncing = true) }
        try {
            // 1. Connections from together_connections (with graceful fallback to friendships)
            val acceptedIds = LinkedHashSet<String>()
            val blockedIds = HashSet<String>()
            val incoming = mutableListOf<TogetherFriendRequest>()
            val outgoing = mutableListOf<TogetherFriendRequest>()

            val connRes = fetchConnectionsRows(uid, token)
            if (connRes is SupabaseResult.Failure) {
                _state.update { it.copy(isSyncing = false) }
                return connRes
            }
            val shipsArr = JSONArray((connRes as SupabaseResult.Success).value)
            for (i in 0 until shipsArr.length()) {
                val obj = shipsArr.getJSONObject(i)
                val reqId = obj.optString("user_id").ifBlank { obj.optString("requester_id") }
                val addId = obj.optString("friend_id").ifBlank { obj.optString("addressee_id") }
                val status = obj.optString("status", "ACCEPTED").uppercase()
                val otherId = if (reqId == uid) addId else reqId
                val isIncoming = addId == uid
                when (status) {
                    "ACCEPTED", "CONNECTED" -> if (otherId.isNotBlank()) acceptedIds.add(otherId)
                    "BLOCKED" -> if (otherId.isNotBlank()) blockedIds.add(otherId)
                    "PENDING" -> {
                        val reqItem = TogetherFriendRequest(
                            id = obj.optString("id"),
                            requesterId = reqId,
                            addresseeId = addId,
                            requesterName = obj.optString("requester_name").ifBlank { "Friend" },
                            requesterHandle = obj.optString("requester_handle"),
                            status = status,
                            isIncoming = isIncoming,
                            createdAtMillis = obj.optLong("created_at")
                        )
                        if (isIncoming) incoming.add(reqItem) else outgoing.add(reqItem)
                    }
                }
            }

            // 2. Profiles + study_presence of accepted friends
            val friends = mutableListOf<TogetherFriendProfile>()
            if (acceptedIds.isNotEmpty()) {
                val inClause = acceptedIds.joinToString(",")
                val profRes = supabase.restGet("profiles?id=in.($inClause)", token)
                val presenceByUser = fetchStudyPresenceMap(inClause, token)
                if (profRes is SupabaseResult.Success) {
                    val arr = JSONArray(profRes.value)
                    for (i in 0 until arr.length()) {
                        val p = arr.getJSONObject(i)
                        val base = parseFriendProfile(p)
                        if (base.userId !in blockedIds) {
                            val pres = presenceByUser[base.userId]
                            friends.add(
                                if (pres != null) {
                                    base.copy(
                                        presenceStatus = pres.status,
                                        isFocusingNow = pres.isFocusing,
                                        lastActiveNote = pres.note
                                    )
                                } else base
                            )
                        }
                    }
                }
            }

            // Preserve any locally-connected friends in Room/memory that aren't in cloud yet
            _state.value.friends.forEach { existingLocal ->
                val dup = friends.any {
                    it.userId == existingLocal.userId ||
                        (existingLocal.friendCode.isNotBlank() && it.friendCode.equals(existingLocal.friendCode, ignoreCase = true))
                }
                if (!dup && existingLocal.userId.startsWith("local_")) {
                    friends.add(existingLocal)
                }
            }

            // 3. Messages + unread counts + outbox merge
            refreshMessagesAndCounts(uid, token, friends)

            // 4. Shared Goals
            val goalsRes = supabase.restGet(
                "shared_goals?or=(creator_id.eq.$uid,partner_id.eq.$uid)&order=updated_at.desc",
                token
            )
            val goals = if (goalsRes is SupabaseResult.Success) parseGoals(JSONArray(goalsRes.value)) else emptyList()

            // 5. Competitions
            val compRes = supabase.restGet(
                "competitions?or=(creator_id.eq.$uid,partner_id.eq.$uid)&order=created_at.desc",
                token
            )
            val comps = if (compRes is SupabaseResult.Success) parseCompetitions(JSONArray(compRes.value)) else emptyList()

            // 6. Activities & reactions
            val actRes = supabase.restGet("together_activities?order=created_at.desc&limit=30", token)
            val acts = if (actRes is SupabaseResult.Success) parseActivities(JSONArray(actRes.value)) else emptyList()

            // Compute friendly Together Streak from connected friends & active streaks
            val streakVal = if (friends.isEmpty()) 0 else {
                val sharedActive = friends.count { it.dailyStreak > 0 }
                if (sharedActive > 0) (friends.maxOfOrNull { it.dailyStreak } ?: 1).coerceAtMost(365) else 1
            }

            val outboxList = db.togetherOutboxDao().getAllOnce()
            _state.update {
                it.copy(
                    isSyncing = false,
                    statusNote = "Synced",
                    friends = friends,
                    incomingRequests = incoming,
                    outgoingRequests = outgoing,
                    blockedUserIds = blockedIds,
                    sharedGoals = goals,
                    competitions = comps,
                    activities = acts,
                    outboxCount = outboxList.size,
                    togetherStreakDays = streakVal
                )
            }

            // Mirror accepted friends cleanly into local Room friends cache
            friends.forEach { f ->
                val existing = db.friendDao().findByCodeOrHandle(f.friendCode, f.handle)
                db.friendDao().insertFriend(
                    FriendEntity(
                        id = existing?.id ?: 0L,
                        displayName = f.displayName,
                        handle = f.handle,
                        friendCode = f.friendCode,
                        level = f.level,
                        xp = f.xp,
                        streakDays = f.dailyStreak,
                        focusMinutesToday = f.focusMinutesToday,
                        challengesCompleted = f.challengesCompleted,
                        challengesTotal = 4,
                        petStageName = "FEELORA Together",
                        sharesStats = f.shareXp || f.shareStreaks || f.shareFocus || f.shareChallenges,
                        shareXp = f.shareXp,
                        shareStreaks = f.shareStreaks,
                        shareFocus = f.shareFocus,
                        shareChallenges = f.shareChallenges,
                        statusText = f.presenceStatus.ifBlank { "Connected online" },
                        lastSyncedAtMillis = System.currentTimeMillis()
                    )
                )
            }

            return SupabaseResult.Success(Unit)
        } finally {
            _state.update { it.copy(isSyncing = false) }
        }
    }

    private data class ParsedPresence(
        val status: String,
        val isFocusing: Boolean,
        val note: String
    )

    private suspend fun fetchConnectionsRows(uid: String, token: String): SupabaseResult<String> {
        val primary = supabase.restGet(
            "together_connections?or=(user_id.eq.$uid,friend_id.eq.$uid)&order=created_at.desc",
            token
        )
        if (primary is SupabaseResult.Success) return primary

        val altCols = supabase.restGet(
            "together_connections?or=(requester_id.eq.$uid,addressee_id.eq.$uid)&order=created_at.desc",
            token
        )
        if (altCols is SupabaseResult.Success) return altCols

        val legacy = supabase.restGet(
            "friendships?or=(requester_id.eq.$uid,addressee_id.eq.$uid)&order=created_at.desc",
            token
        )
        return if (legacy is SupabaseResult.Success) legacy else primary
    }

    private suspend fun fetchStudyPresenceMap(inClause: String, token: String): Map<String, ParsedPresence> {
        if (inClause.isBlank() || !supabase.isConfigured() || token.isBlank()) return emptyMap()
        val res = supabase.restGet("study_presence?user_id=in.($inClause)", token)
        if (res !is SupabaseResult.Success) return emptyMap()
        return runCatching {
            val arr = JSONArray(res.value)
            val map = HashMap<String, ParsedPresence>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val uid = o.optString("user_id")
                if (uid.isNotBlank()) {
                    val focusing = o.optBoolean("is_studying", o.optBoolean("is_focusing", false))
                    val status = o.optString("status")
                        .ifBlank { if (focusing) "Focusing now" else "Online" }
                    val note = o.optString("note").ifBlank { o.optString("message") }
                    map[uid] = ParsedPresence(status = status, isFocusing = focusing, note = note)
                }
            }
            map
        }.getOrDefault(emptyMap())
    }

    private suspend fun refreshMessagesAndCounts(
        uid: String,
        token: String,
        friendsToUpdate: MutableList<TogetherFriendProfile>? = null
    ) {
        val msgRes = if (supabase.isConfigured() && uid.isNotBlank() && token.isNotBlank()) {
            supabase.restGet(
                "together_messages?or=(sender_id.eq.$uid,recipient_id.eq.$uid)&order=created_at.asc&limit=300",
                token
            )
        } else null
        val outbox = db.togetherOutboxDao().getAllOnce()
        val grouped = LinkedHashMap<String, MutableList<TogetherMessage>>()

        if (msgRes is SupabaseResult.Success) {
            val arr = JSONArray(msgRes.value)
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val msg = TogetherMessage(
                    id = m.optString("id"),
                    clientMessageId = m.optString("client_message_id"),
                    conversationId = m.optString("conversation_id"),
                    senderId = m.optString("sender_id"),
                    recipientId = m.optString("recipient_id"),
                    content = m.optString("content"),
                    mediaUrl = m.optString("media_url").takeIf { it.isNotBlank() && it != "null" },
                    deliveryStatus = m.optString("delivery_status", "SENT"),
                    createdAtMillis = m.optLong("created_at")
                )
                grouped.getOrPut(msg.conversationId) { mutableListOf() }.add(msg)
            }
        }

        // Merge local in-memory messages so offline/preview chats & cheers show immediately
        synchronized(localFallbackMessages) {
            localFallbackMessages.forEach { (convId, list) ->
                val target = grouped.getOrPut(convId) { mutableListOf() }
                list.forEach { localMsg ->
                    if (target.none { it.clientMessageId == localMsg.clientMessageId }) {
                        target.add(localMsg)
                    }
                }
            }
        }

        // Append queued offline messages cleanly
        outbox.forEach { queued ->
            val list = grouped.getOrPut(queued.conversationId) { mutableListOf() }
            if (list.none { it.clientMessageId == queued.clientMessageId }) {
                list.add(
                    TogetherMessage(
                        id = "outbox_${queued.id}",
                        clientMessageId = queued.clientMessageId,
                        conversationId = queued.conversationId,
                        senderId = uid.ifBlank { "local_self" },
                        recipientId = queued.recipientUserId,
                        content = queued.content,
                        mediaUrl = queued.mediaLocalUri,
                        deliveryStatus = "QUEUED",
                        createdAtMillis = queued.createdAtMillis
                    )
                )
            }
        }

        grouped.values.forEach { list -> list.sortBy { it.createdAtMillis } }

        val targetFriends = friendsToUpdate ?: _state.value.friends.toMutableList()
        for (idx in targetFriends.indices) {
            val f = targetFriends[idx]
            val convId = TogetherPrivacyFilter.conversationIdFor(uid.ifBlank { "local_self" }, f.userId)
            val thread = grouped[convId].orEmpty()
            val unread = thread.count { it.senderId == f.userId && it.deliveryStatus != "READ" }
            val last = thread.lastOrNull()
            targetFriends[idx] = f.copy(
                unreadCount = unread,
                lastMessagePreview = last?.content?.ifBlank { if (last.mediaUrl != null) "Shared photo" else "" }.orEmpty(),
                lastMessageAtMillis = last?.createdAtMillis ?: 0L
            )
        }

        _state.update {
            it.copy(
                friends = targetFriends,
                messagesByConversation = grouped,
                outboxCount = outbox.size
            )
        }
    }

    suspend fun lookupProfileByFriendCode(
        rawCode: String,
        accessToken: String
    ): SupabaseResult<TogetherFriendProfile> {
        if (!supabase.isConfigured()) {
            return SupabaseResult.Failure(supabase.configurationStatusMessage(), isConfigMissing = true)
        }
        val cleanCode = FriendSyncCodec.extractCodeForInputField(rawCode).trim().uppercase()
        if (cleanCode.isBlank()) {
            return SupabaseResult.Failure("Enter a valid Friend Code.")
        }

        // 1. Try lookup_profile_by_friend_code RPC with common parameter names
        val rpcCandidates = listOf(
            JSONObject().put("friend_code", cleanCode).toString(),
            JSONObject().put("code", cleanCode).toString(),
            JSONObject().put("p_friend_code", cleanCode).toString()
        )
        for (body in rpcCandidates) {
            when (val rpcRes = supabase.restRpc("lookup_profile_by_friend_code", body, accessToken)) {
                is SupabaseResult.Success -> {
                    parseProfileFromRpcOrArray(rpcRes.value)?.let { profile ->
                        return SupabaseResult.Success(profile)
                    }
                }
                is SupabaseResult.Failure -> Unit
            }
        }

        // 2. Fallback direct query against profiles table when authenticated
        if (accessToken.isNotBlank()) {
            val encodedHandle = Uri.encode(SupabaseAuthAndSyncClient.normalizeHandle(rawCode))
            val encodedCode = Uri.encode(cleanCode)
            val queryRes = supabase.restGet(
                "profiles?or=(friend_code.eq.$encodedCode,handle.eq.$encodedHandle)&limit=1",
                accessToken
            )
            if (queryRes is SupabaseResult.Success) {
                parseProfileFromRpcOrArray(queryRes.value)?.let { profile ->
                    return SupabaseResult.Success(profile)
                }
            }
        }

        return SupabaseResult.Failure("No cloud profile found for \"$cleanCode\".")
    }

    private fun parseProfileFromRpcOrArray(rawJson: String): TogetherFriendProfile? {
        val trimmed = rawJson.trim()
        if (trimmed.isBlank() || trimmed == "null" || trimmed == "[]") return null
        return runCatching {
            val obj = when {
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    if (arr.length() == 0) return null
                    arr.getJSONObject(0)
                }
                trimmed.startsWith("{") -> JSONObject(trimmed)
                else -> return null
            }
            val id = obj.optString("id").ifBlank { obj.optString("user_id") }
            if (id.isBlank()) null else parseFriendProfile(obj.put("id", id))
        }.getOrNull()
    }

    suspend fun connectByFriendCode(
        rawInput: String,
        myUserId: String,
        myFriendCode: String,
        myName: String,
        myHandle: String,
        accessToken: String
    ): SupabaseResult<TogetherFriendProfile> {
        val cleanInput = rawInput.trim()
        if (cleanInput.isBlank()) {
            val err = "Enter a Friend Code or paste an invite link."
            _state.update { it.copy(quickConnectStatus = TogetherConnectStatus.Error(err)) }
            return SupabaseResult.Failure(err)
        }

        val extractedCode = FriendSyncCodec.extractCodeForInputField(cleanInput)
        if (myFriendCode.isNotBlank() && extractedCode.equals(myFriendCode.trim(), ignoreCase = true)) {
            val err = "That's your own Friend Code — share it with a friend to connect!"
            _state.update { it.copy(quickConnectStatus = TogetherConnectStatus.Error(err)) }
            return SupabaseResult.Failure(err)
        }

        _state.update { it.copy(quickConnectStatus = TogetherConnectStatus.Loading) }

        // Try live Supabase lookup + together_connections upsert first when configured
        if (supabase.isConfigured()) {
            val lookupRes = lookupProfileByFriendCode(cleanInput, accessToken)
            if (lookupRes is SupabaseResult.Success) {
                val target = lookupRes.value
                if (myUserId.isNotBlank() && target.userId == myUserId) {
                    val err = "That's your own FEELORA profile!"
                    _state.update { it.copy(quickConnectStatus = TogetherConnectStatus.Error(err)) }
                    return SupabaseResult.Failure(err)
                }
                if (myUserId.isNotBlank() && accessToken.isNotBlank()) {
                    val connSaved = insertTogetherConnection(
                        myUserId = myUserId,
                        friendId = target.userId,
                        myName = myName,
                        myHandle = myHandle,
                        accessToken = accessToken
                    )
                    if (connSaved is SupabaseResult.Success) {
                        refreshAllInternal()
                        val msg = "Connected with ${target.displayName}!"
                        _state.update {
                            it.copy(
                                pendingConnectCode = "",
                                quickConnectStatus = TogetherConnectStatus.Success(msg, target.displayName)
                            )
                        }
                        return SupabaseResult.Success(target)
                    }
                }
            }
        }

        // Smooth local / Room / in-memory fallback when offline, unconfigured, or lookup isn't reachable
        val parsed = FriendSyncCodec.parseInput(cleanInput)
        if (parsed == null) {
            val err = "Enter a valid Friend Code (like FLR-ARIA-4821), @handle, or invite link."
            _state.update { it.copy(quickConnectStatus = TogetherConnectStatus.Error(err)) }
            return SupabaseResult.Failure(err)
        }

        val existingRoom = db.friendDao().findByCodeOrHandle(parsed.friendCode, parsed.handle)
        val savedEntity = parsed.toFriendEntity(
            existingId = existingRoom?.id ?: 0L,
            statusNote = "Ready to study"
        )
        db.friendDao().insertFriend(savedEntity)

        val localProfile = TogetherFriendProfile(
            userId = "local_${parsed.friendCode.ifBlank { parsed.handle }}",
            displayName = parsed.displayName,
            handle = parsed.handle,
            friendCode = parsed.friendCode,
            level = parsed.level,
            xp = parsed.xp,
            dailyStreak = parsed.streakDays,
            focusMinutesToday = parsed.focusMinutesToday,
            challengesCompleted = parsed.challengesCompleted,
            shareXp = parsed.shareXp,
            shareStreaks = parsed.shareStreaks,
            shareFocus = parsed.shareFocus,
            shareChallenges = parsed.shareChallenges,
            presenceStatus = "Connected · Ready",
            isFocusingNow = false,
            lastActiveNote = "Connected via code ${parsed.friendCode}"
        )

        _state.update { st ->
            val filtered = st.friends.filterNot {
                it.userId == localProfile.userId ||
                    (localProfile.friendCode.isNotBlank() && it.friendCode.equals(localProfile.friendCode, ignoreCase = true))
            }
            val updatedFriends = listOf(localProfile) + filtered
            st.copy(
                friends = updatedFriends,
                togetherStreakDays = st.togetherStreakDays.coerceAtLeast(1),
                pendingConnectCode = "",
                quickConnectStatus = TogetherConnectStatus.Success(
                    message = "Connected with ${localProfile.displayName} (${localProfile.friendCode})",
                    friendName = localProfile.displayName
                )
            )
        }
        return SupabaseResult.Success(localProfile)
    }

    private suspend fun insertTogetherConnection(
        myUserId: String,
        friendId: String,
        myName: String,
        myHandle: String,
        accessToken: String
    ): SupabaseResult<Unit> {
        val now = System.currentTimeMillis()
        val primaryPayload = JSONObject().apply {
            put("user_id", myUserId)
            put("friend_id", friendId)
            put("status", "ACCEPTED")
            put("created_at", now)
        }
        val res1 = supabase.restPost("together_connections", primaryPayload.toString(), accessToken, upsert = true)
        if (res1 is SupabaseResult.Success) return SupabaseResult.Success(Unit)

        val altPayload = JSONObject().apply {
            put("requester_id", myUserId)
            put("addressee_id", friendId)
            put("requester_name", myName.ifBlank { "Friend" })
            put("requester_handle", myHandle.ifBlank { "@feelora" })
            put("status", "ACCEPTED")
            put("created_at", now)
        }
        val res2 = supabase.restPost("together_connections", altPayload.toString(), accessToken, upsert = true)
        if (res2 is SupabaseResult.Success) return SupabaseResult.Success(Unit)

        val res3 = supabase.restPost("friendships", altPayload.toString(), accessToken, upsert = true)
        return when (res3) {
            is SupabaseResult.Success -> SupabaseResult.Success(Unit)
            is SupabaseResult.Failure -> res3
        }
    }

    suspend fun publishStudyPresence(
        myUserId: String,
        status: String,
        isFocusing: Boolean,
        quickNote: String,
        accessToken: String
    ) {
        val cleanStatus = status.trim().ifBlank { "Ready to study" }
        val cleanNote = quickNote.trim()
        _state.update {
            it.copy(
                myPresenceStatus = cleanStatus,
                myQuickNote = cleanNote
            )
        }
        if (!supabase.isConfigured() || myUserId.isBlank() || accessToken.isBlank()) return
        val payload = JSONObject().apply {
            put("user_id", myUserId)
            put("status", cleanStatus)
            put("is_studying", isFocusing)
            put("note", cleanNote)
            put("updated_at", System.currentTimeMillis())
        }
        supabase.restPost("study_presence", payload.toString(), accessToken, upsert = true)
    }

    suspend fun sendCheer(
        myUserId: String,
        myName: String,
        friend: TogetherFriendProfile,
        accessToken: String
    ): SupabaseResult<String> {
        val senderName = myName.ifBlank { "A friend" }
        val cheerContent = "✨ $senderName sent you a cheer! Keep going!"
        val msgRes = sendMessage(
            myUserId = myUserId,
            friendUserId = friend.userId,
            content = cheerContent,
            mediaLocalUri = null,
            accessToken = accessToken
        )
        _state.update { st ->
            st.copy(
                friends = st.friends.map { f ->
                    if (f.userId == friend.userId) {
                        f.copy(
                            lastMessagePreview = "You sent a cheer ✨",
                            lastMessageAtMillis = System.currentTimeMillis()
                        )
                    } else f
                },
                activities = listOf(
                    TogetherActivityItem(
                        id = "cheer_${System.currentTimeMillis()}",
                        userId = myUserId.ifBlank { "local_self" },
                        authorName = senderName,
                        summary = "Sent a cheer to ${friend.displayName} ✨",
                        createdAtMillis = System.currentTimeMillis(),
                        reactionCounts = mapOf(TogetherReaction.CHEER to 1),
                        myReaction = TogetherReaction.CHEER
                    )
                ) + st.activities.take(24)
            )
        }
        return when (msgRes) {
            is SupabaseResult.Success -> SupabaseResult.Success("Cheer sent to ${friend.displayName}!")
            is SupabaseResult.Failure -> SupabaseResult.Success("Cheer saved for ${friend.displayName}!")
        }
    }

    suspend fun sendQuickNoteToCircle(
        myUserId: String,
        myName: String,
        note: String,
        targetFriend: TogetherFriendProfile?,
        accessToken: String
    ): SupabaseResult<String> {
        val cleanNote = note.trim()
        if (cleanNote.isBlank()) return SupabaseResult.Failure("Write a short check-in note first.")

        publishStudyPresence(
            myUserId = myUserId,
            status = cleanNote.take(40),
            isFocusing = cleanNote.contains("focus", ignoreCase = true) || cleanNote.contains("study", ignoreCase = true),
            quickNote = cleanNote,
            accessToken = accessToken
        )

        val recipients = if (targetFriend != null) listOf(targetFriend) else _state.value.friends
        recipients.forEach { friend ->
            sendMessage(
                myUserId = myUserId,
                friendUserId = friend.userId,
                content = cleanNote,
                mediaLocalUri = null,
                accessToken = accessToken
            )
        }

        _state.update { st ->
            st.copy(
                activities = listOf(
                    TogetherActivityItem(
                        id = "note_${System.currentTimeMillis()}",
                        userId = myUserId.ifBlank { "local_self" },
                        authorName = myName.ifBlank { "You" },
                        summary = cleanNote,
                        createdAtMillis = System.currentTimeMillis()
                    )
                ) + st.activities.take(24)
            )
        }
        val label = targetFriend?.displayName ?: "your circle"
        return SupabaseResult.Success("Note shared with $label!")
    }

    suspend fun sendFriendRequest(
        query: String,
        myUserId: String,
        myName: String,
        myHandle: String,
        accessToken: String
    ): SupabaseResult<String> {
        if (!supabase.isConfigured() || myUserId.isBlank() || accessToken.isBlank()) {
            return SupabaseResult.Failure(supabase.configurationStatusMessage(), isConfigMissing = true)
        }
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return SupabaseResult.Failure("Enter a username or Friend Code.")

        val lookup = lookupProfileByFriendCode(cleanQuery, accessToken)
        if (lookup is SupabaseResult.Failure) return lookup
        val target = (lookup as SupabaseResult.Success).value
        if (target.userId == myUserId) {
            return SupabaseResult.Failure("That's your own FEELORA profile!")
        }

        return when (val ins = insertTogetherConnection(myUserId, target.userId, myName, myHandle, accessToken)) {
            is SupabaseResult.Success -> {
                refreshAllInternal()
                SupabaseResult.Success("Connected with ${target.displayName}!")
            }
            is SupabaseResult.Failure -> ins
        }
    }

    suspend fun respondToFriendRequest(
        requestId: String,
        status: String, // ACCEPTED | DECLINED | BLOCKED
        accessToken: String
    ): SupabaseResult<Unit> {
        if (!supabase.isConfigured() || accessToken.isBlank()) {
            return SupabaseResult.Failure(supabase.configurationStatusMessage(), isConfigMissing = true)
        }
        val body = JSONObject().put("status", status).toString()
        val res = supabase.restPatch("friendships?id=eq.$requestId", body, accessToken)
        return when (res) {
            is SupabaseResult.Success -> {
                refreshAllInternal()
                SupabaseResult.Success(Unit)
            }
            is SupabaseResult.Failure -> res
        }
    }

    suspend fun blockOrRemoveFriend(
        myUserId: String,
        friendUserId: String,
        block: Boolean,
        accessToken: String
    ): SupabaseResult<Unit> {
        if (!supabase.isConfigured() || accessToken.isBlank()) {
            return SupabaseResult.Failure(supabase.configurationStatusMessage(), isConfigMissing = true)
        }
        val query = "friendships?or=(and(requester_id.eq.$myUserId,addressee_id.eq.$friendUserId),and(requester_id.eq.$friendUserId,addressee_id.eq.$myUserId))"
        val res = if (block) {
            supabase.restPatch(query, JSONObject().put("status", "BLOCKED").toString(), accessToken)
        } else {
            supabase.restDelete(query, accessToken)
        }
        return when (res) {
            is SupabaseResult.Success -> {
                refreshAllInternal()
                SupabaseResult.Success(Unit)
            }
            is SupabaseResult.Failure -> res
        }
    }

    suspend fun reportUser(
        myUserId: String,
        reportedUserId: String,
        reason: String,
        accessToken: String
    ): SupabaseResult<String> {
        if (!supabase.isConfigured() || accessToken.isBlank()) {
            return SupabaseResult.Failure(supabase.configurationStatusMessage(), isConfigMissing = true)
        }
        val body = JSONObject().apply {
            put("reporter_id", myUserId)
            put("reported_user_id", reportedUserId)
            put("reason", reason.trim().ifBlank { "Inappropriate behavior" })
            put("created_at", System.currentTimeMillis())
        }
        return when (val res = supabase.restPost("together_reports", body.toString(), accessToken)) {
            is SupabaseResult.Success -> SupabaseResult.Success("Report submitted. Thank you for keeping Together respectful.")
            is SupabaseResult.Failure -> res
        }
    }

    suspend fun sendMessage(
        myUserId: String,
        friendUserId: String,
        content: String,
        mediaLocalUri: Uri?,
        accessToken: String
    ): SupabaseResult<Unit> {
        val cleanText = content.trim()
        if (cleanText.isBlank() && mediaLocalUri == null) {
            return SupabaseResult.Success(Unit)
        }
        val effectiveUid = myUserId.ifBlank { "local_self" }
        val convId = TogetherPrivacyFilter.conversationIdFor(effectiveUid, friendUserId)
        val clientMsgId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Upload image via Photo Picker URI if online & configured
        var uploadedMediaUrl: String? = null
        if (mediaLocalUri != null && supabase.isConfigured() && accessToken.isNotBlank()) {
            val objectPath = "$effectiveUid/$clientMsgId.jpg"
            when (val uploadRes = supabase.uploadImageToStorage("together-media", objectPath, mediaLocalUri, accessToken)) {
                is SupabaseResult.Success -> uploadedMediaUrl = uploadRes.value
                is SupabaseResult.Failure -> uploadedMediaUrl = null
            }
        }

        val localMsg = TogetherMessage(
            id = "local_$clientMsgId",
            clientMessageId = clientMsgId,
            conversationId = convId,
            senderId = effectiveUid,
            recipientId = friendUserId,
            content = cleanText,
            mediaUrl = uploadedMediaUrl ?: mediaLocalUri?.toString(),
            deliveryStatus = if (supabase.isConfigured() && myUserId.isNotBlank() && accessToken.isNotBlank() && !friendUserId.startsWith("local_")) "QUEUED" else "SENT",
            createdAtMillis = now
        )
        synchronized(localFallbackMessages) {
            localFallbackMessages.getOrPut(convId) { mutableListOf() }.add(localMsg)
        }

        if (!supabase.isConfigured() || myUserId.isBlank() || accessToken.isBlank() || friendUserId.startsWith("local_")) {
            refreshMessagesAndCounts(effectiveUid, accessToken)
            return SupabaseResult.Success(Unit)
        }

        // Queue in Room outbox first for reliability when online sync is active
        db.togetherOutboxDao().upsert(
            TogetherOutboxEntity(
                conversationId = convId,
                recipientUserId = friendUserId,
                clientMessageId = clientMsgId,
                content = cleanText,
                mediaLocalUri = uploadedMediaUrl ?: mediaLocalUri?.toString(),
                status = "PENDING",
                createdAtMillis = now
            )
        )

        val payload = JSONObject().apply {
            put("client_message_id", clientMsgId)
            put("conversation_id", convId)
            put("sender_id", myUserId)
            put("recipient_id", friendUserId)
            put("content", cleanText)
            if (uploadedMediaUrl != null) put("media_url", uploadedMediaUrl)
            put("delivery_status", "DELIVERED")
            put("created_at", now)
        }

        return when (val postRes = supabase.restPost("together_messages", payload.toString(), accessToken)) {
            is SupabaseResult.Success -> {
                db.togetherOutboxDao().deleteByClientId(clientMsgId)
                refreshMessagesAndCounts(myUserId, accessToken)
                SupabaseResult.Success(Unit)
            }
            is SupabaseResult.Failure -> {
                refreshMessagesAndCounts(myUserId, accessToken)
                postRes
            }
        }
    }

    suspend fun markConversationRead(myUserId: String, friendUserId: String, accessToken: String) {
        if (!supabase.isConfigured() || myUserId.isBlank() || accessToken.isBlank()) return
        val convId = TogetherPrivacyFilter.conversationIdFor(myUserId, friendUserId)
        val query = "together_messages?conversation_id=eq.$convId&recipient_id=eq.$myUserId&delivery_status=neq.READ"
        supabase.restPatch(query, JSONObject().put("delivery_status", "READ").toString(), accessToken)
        refreshMessagesAndCounts(myUserId, accessToken)
    }

    private suspend fun flushOutboxInternal() {
        val uid = currentUserId
        val token = currentAccessToken
        if (!supabase.isConfigured() || uid.isBlank() || token.isBlank()) return
        val queued = db.togetherOutboxDao().getAllOnce()
        queued.forEach { item ->
            val payload = JSONObject().apply {
                put("client_message_id", item.clientMessageId)
                put("conversation_id", item.conversationId)
                put("sender_id", uid)
                put("recipient_id", item.recipientUserId)
                put("content", item.content)
                if (!item.mediaLocalUri.isNullOrBlank() && item.mediaLocalUri.startsWith("http")) {
                    put("media_url", item.mediaLocalUri)
                }
                put("delivery_status", "DELIVERED")
                put("created_at", item.createdAtMillis)
            }
            val res = supabase.restPost("together_messages", payload.toString(), token, upsert = true)
            if (res is SupabaseResult.Success) {
                db.togetherOutboxDao().deleteByClientId(item.clientMessageId)
            }
        }
    }

    suspend fun createSharedGoal(
        myUserId: String,
        partnerId: String?,
        partnerName: String?,
        title: String,
        description: String,
        targetValue: Int,
        unitLabel: String,
        accessToken: String
    ): SupabaseResult<Unit> {
        if (!supabase.isConfigured() || accessToken.isBlank()) {
            return SupabaseResult.Failure(supabase.configurationStatusMessage(), isConfigMissing = true)
        }
        val payload = JSONObject().apply {
            put("creator_id", myUserId)
            if (!partnerId.isNullOrBlank()) put("partner_id", partnerId)
            if (!partnerName.isNullOrBlank()) put("partner_name", partnerName)
            put("title", title.trim())
            put("description", description.trim())
            put("target_value", targetValue.coerceAtLeast(1))
            put("current_value", 0)
            put("unit_label", unitLabel.trim().ifBlank { "units" })
            put("is_completed", false)
            put("updated_at", System.currentTimeMillis())
        }
        return when (val res = supabase.restPost("shared_goals", payload.toString(), accessToken)) {
            is SupabaseResult.Success -> {
                refreshAllInternal()
                SupabaseResult.Success(Unit)
            }
            is SupabaseResult.Failure -> res
        }
    }

    suspend fun incrementSharedGoal(
        goal: TogetherSharedGoal,
        delta: Int,
        myName: String,
        myUserId: String,
        accessToken: String
    ): SupabaseResult<Unit> {
        if (!supabase.isConfigured() || accessToken.isBlank()) {
            return SupabaseResult.Failure(supabase.configurationStatusMessage(), isConfigMissing = true)
        }
        val nextVal = (goal.currentValue + delta).coerceIn(0, goal.targetValue)
        val completed = nextVal >= goal.targetValue
        val payload = JSONObject().apply {
            put("current_value", nextVal)
            put("is_completed", completed)
            put("updated_at", System.currentTimeMillis())
        }
        return when (val res = supabase.restPatch("shared_goals?id=eq.${goal.id}", payload.toString(), accessToken)) {
            is SupabaseResult.Success -> {
                if (completed) {
                    postActivity(myUserId, myName, "Completed shared goal \"${goal.title}\"!", accessToken)
                }
                refreshAllInternal()
                SupabaseResult.Success(Unit)
            }
            is SupabaseResult.Failure -> res
        }
    }

    suspend fun createCompetition(
        myUserId: String,
        partnerId: String?,
        partnerName: String?,
        title: String,
        mode: TogetherCompetitionMode,
        myInitialScore: Int,
        accessToken: String
    ): SupabaseResult<Unit> {
        if (!supabase.isConfigured() || accessToken.isBlank()) {
            return SupabaseResult.Failure(supabase.configurationStatusMessage(), isConfigMissing = true)
        }
        val payload = JSONObject().apply {
            put("creator_id", myUserId)
            if (!partnerId.isNullOrBlank()) put("partner_id", partnerId)
            if (!partnerName.isNullOrBlank()) put("partner_name", partnerName)
            put("title", title.trim().ifBlank { mode.title })
            put("mode", mode.name)
            put("creator_score", myInitialScore)
            put("partner_score", 0)
            put("days_remaining", 7)
            put("is_active", true)
            put("created_at", System.currentTimeMillis())
        }
        return when (val res = supabase.restPost("competitions", payload.toString(), accessToken)) {
            is SupabaseResult.Success -> {
                refreshAllInternal()
                SupabaseResult.Success(Unit)
            }
            is SupabaseResult.Failure -> res
        }
    }

    suspend fun postActivity(
        userId: String,
        authorName: String,
        summary: String,
        accessToken: String
    ) {
        if (!supabase.isConfigured() || userId.isBlank() || accessToken.isBlank()) return
        val body = JSONObject().apply {
            put("user_id", userId)
            put("author_name", authorName.ifBlank { "Friend" })
            put("summary", summary)
            put("created_at", System.currentTimeMillis())
        }
        supabase.restPost("together_activities", body.toString(), accessToken)
    }

    suspend fun reactToActivity(
        activityId: String,
        myUserId: String,
        reaction: TogetherReaction,
        accessToken: String
    ): SupabaseResult<Unit> {
        if (!supabase.isConfigured() || accessToken.isBlank()) {
            return SupabaseResult.Failure(supabase.configurationStatusMessage(), isConfigMissing = true)
        }
        val body = JSONObject().apply {
            put("activity_id", activityId)
            put("user_id", myUserId)
            put("reaction", reaction.name)
        }
        return when (val res = supabase.restPost("together_reactions", body.toString(), accessToken, upsert = true)) {
            is SupabaseResult.Success -> {
                _state.update { st ->
                    st.copy(
                        activities = st.activities.map { item ->
                            if (item.id == activityId) {
                                val nextMap = item.reactionCounts.toMutableMap()
                                nextMap[reaction] = (nextMap[reaction] ?: 0) + 1
                                item.copy(reactionCounts = nextMap, myReaction = reaction)
                            } else item
                        }
                    )
                }
                SupabaseResult.Success(Unit)
            }
            is SupabaseResult.Failure -> res
        }
    }

    private fun parseFriendProfile(p: JSONObject): TogetherFriendProfile {
        return TogetherFriendProfile(
            userId = p.optString("id"),
            displayName = p.optString("display_name", "Friend"),
            handle = p.optString("handle", "@friend"),
            bio = p.optString("bio", ""),
            friendCode = p.optString("friend_code", ""),
            avatarPreset = UserAvatarPreset.fromStored(p.optString("avatar_preset")),
            avatarUrl = p.optString("avatar_url", ""),
            shareXp = p.optBoolean("share_xp", true),
            shareStreaks = p.optBoolean("share_streaks", true),
            shareFocus = p.optBoolean("share_focus", false),
            shareChallenges = p.optBoolean("share_challenges", true),
            level = p.optInt("level", 1),
            xp = p.optInt("xp", 0),
            dailyStreak = p.optInt("daily_streak", 0),
            focusMinutesToday = p.optInt("focus_minutes_today", 0),
            tasksCompletedToday = p.optInt("tasks_completed_today", 0),
            consistencyScore = p.optInt("consistency_score", 0),
            challengesCompleted = p.optInt("challenges_completed", 0)
        )
    }

    private fun parseGoals(arr: JSONArray): List<TogetherSharedGoal> {
        val out = mutableListOf<TogetherSharedGoal>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                TogetherSharedGoal(
                    id = o.optString("id"),
                    creatorId = o.optString("creator_id"),
                    partnerId = o.optString("partner_id").takeIf { it.isNotBlank() && it != "null" },
                    partnerName = o.optString("partner_name").takeIf { it.isNotBlank() && it != "null" },
                    title = o.optString("title"),
                    description = o.optString("description"),
                    targetValue = o.optInt("target_value", 1),
                    currentValue = o.optInt("current_value", 0),
                    unitLabel = o.optString("unit_label", "units"),
                    isCompleted = o.optBoolean("is_completed", false),
                    updatedAtMillis = o.optLong("updated_at")
                )
            )
        }
        return out
    }

    private fun parseCompetitions(arr: JSONArray): List<TogetherCompetition> {
        val out = mutableListOf<TogetherCompetition>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val mode = TogetherCompetitionMode.entries.firstOrNull { it.name == o.optString("mode") }
                ?: TogetherCompetitionMode.XP_SPRINT
            out.add(
                TogetherCompetition(
                    id = o.optString("id"),
                    creatorId = o.optString("creator_id"),
                    partnerId = o.optString("partner_id").takeIf { it.isNotBlank() && it != "null" },
                    partnerName = o.optString("partner_name").takeIf { it.isNotBlank() && it != "null" },
                    title = o.optString("title"),
                    mode = mode,
                    creatorScore = o.optInt("creator_score", 0),
                    partnerScore = o.optInt("partner_score", 0),
                    daysRemaining = o.optInt("days_remaining", 7),
                    isActive = o.optBoolean("is_active", true),
                    createdAtMillis = o.optLong("created_at")
                )
            )
        }
        return out
    }

    private fun parseActivities(arr: JSONArray): List<TogetherActivityItem> {
        val out = mutableListOf<TogetherActivityItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                TogetherActivityItem(
                    id = o.optString("id"),
                    userId = o.optString("user_id"),
                    authorName = o.optString("author_name", "Friend"),
                    summary = o.optString("summary"),
                    createdAtMillis = o.optLong("created_at")
                )
            )
        }
        return out
    }
}
