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
import com.example.util.DateTimeUtils
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

data class TogetherStudyPresence(
    val userId: String,
    val displayName: String,
    val isStudying: Boolean = false,
    val sessionTitle: String = "",
    val targetMinutes: Int = 25,
    val startedAtMillis: Long = 0L,
    val endsAtMillis: Long = 0L,
    val kuttaEmotion: String = "HAPPY",
    val cheerNote: String = "",
    val updatedAtMillis: Long = 0L
) {
    fun remainingMinutes(nowMillis: Long = System.currentTimeMillis()): Int {
        if (!isStudying || endsAtMillis <= nowMillis) return 0
        return (((endsAtMillis - nowMillis) / 60_000L) + 1L).toInt().coerceAtLeast(1)
    }
}

data class TogetherFriendProfile(
    val userId: String,
    val friendshipId: String = "",
    val isTogetherOne: Boolean = false,
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
    val focusDateEpochDay: Long = 0L,
    val tasksCompletedToday: Int = 0,
    val consistencyScore: Int = 0,
    val challengesCompleted: Int = 0,
    val challengesTotal: Int = 4,
    val petStageName: String = "Playful Pup",
    val unreadCount: Int = 0,
    val lastMessagePreview: String = "",
    val lastMessageAtMillis: Long = 0L,
    val presence: TogetherStudyPresence? = null
)

data class TogetherFriendRequest(
    val id: String,
    val requesterId: String,
    val addresseeId: String,
    val requesterName: String,
    val requesterHandle: String,
    val addresseeName: String = "",
    val addresseeHandle: String = "",
    val status: String, // PENDING | ACCEPTED | DECLINED | BLOCKED
    val isTogetherOne: Boolean = false,
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
    val deliveryStatus: String = "SENT", // QUEUED | FAILED | SENT | DELIVERED | READ
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
    val lastMessageError: String? = null,
    val friends: List<TogetherFriendProfile> = emptyList(),
    val incomingRequests: List<TogetherFriendRequest> = emptyList(),
    val outgoingRequests: List<TogetherFriendRequest> = emptyList(),
    val blockedUserIds: Set<String> = emptySet(),
    val messagesByConversation: Map<String, List<TogetherMessage>> = emptyMap(),
    val outboxCount: Int = 0,
    val failedOutboxCount: Int = 0,
    val sharedGoals: List<TogetherSharedGoal> = emptyList(),
    val competitions: List<TogetherCompetition> = emptyList(),
    val activities: List<TogetherActivityItem> = emptyList(),
    val togetherStreakDays: Int = 0,
    val myPresence: TogetherStudyPresence? = null,
    val presenceByUserId: Map<String, TogetherStudyPresence> = emptyMap()
) {
    val activePartner: TogetherFriendProfile?
        get() = friends.firstOrNull { it.isTogetherOne } ?: friends.firstOrNull()
}

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
        challengesCompleted: Int,
        challengesTotal: Int = 4
    ): JSONObject {
        val code = prefs.friendCode.ifBlank {
            UserPreferencesRepository.generateFriendCode(prefs.userHandle, prefs.userName)
        }
        val gatedXp = if (prefs.shareXpWithFriends) prefs.totalXp else 0
        val gatedLevel = if (prefs.shareXpWithFriends) prefs.level else 1
        val gatedFocus = if (prefs.shareFocusWithFriends) prefs.focusMinutesLoggedToday else 0
        val gatedChallengesDone = if (prefs.shareChallengesWithFriends) challengesCompleted else 0

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
            put("level", gatedLevel)
            put("xp", gatedXp)
            put("total_xp", gatedXp)
            put("daily_streak", if (prefs.shareStreaksWithFriends) prefs.dailyStreak else 0)
            put("focus_minutes_today", gatedFocus)
            put("focus_date_epoch_day", if (prefs.shareFocusWithFriends) DateTimeUtils.todayEpochDay() else 0L)
            put("tasks_completed_today", if (prefs.shareChallengesWithFriends) tasksCompletedToday else 0)
            put("consistency_score", if (prefs.shareChallengesWithFriends) consistencyScore else 0)
            put("challenges_completed", gatedChallengesDone)
            put("challenges_total", challengesTotal.coerceAtLeast(1))
            put("pet_stage_name", prefs.petStage.title)
            put("updated_at", System.currentTimeMillis())
        }
    }

    fun conversationIdFor(userA: String, userB: String): String {
        return if (userA <= userB) "${userA}_$userB" else "${userB}_$userA"
    }
}

object TogetherServiceLocator {
    @Volatile
    private var repository: TogetherRepository? = null

    fun getRepository(context: Context): TogetherRepository {
        return repository ?: synchronized(this) {
            repository ?: TogetherRepository(
                appContext = context.applicationContext,
                supabase = FeeloraSupabase.client(context)
            ).also { repository = it }
        }
    }
}

class TogetherRepository(
    private val appContext: Context,
    private val supabase: SupabaseAuthAndSyncClient
) {
    private val db = LifeOsDatabase.getInstance(appContext)
    private val _state = MutableStateFlow(TogetherState(isCloudConfigured = supabase.isConfigured()))
    val state: StateFlow<TogetherState> = _state.asStateFlow()

    private var syncJob: Job? = null
    private var realtimeSocket: WebSocket? = null
    private var currentUserId: String = ""
    private var currentAccessToken: String = ""
    private var realtimeScope: CoroutineScope? = null

    fun refreshConfigurationState() {
        _state.update {
            it.copy(
                isCloudConfigured = supabase.isConfigured(),
                statusNote = if (supabase.isConfigured()) it.statusNote else supabase.configurationStatusMessage()
            )
        }
    }

    fun clearMessageError() {
        _state.update { it.copy(lastMessageError = null) }
    }

    fun startRealtimeSync(scope: CoroutineScope, userId: String, accessToken: String) {
        currentUserId = userId
        currentAccessToken = accessToken
        realtimeScope = scope
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
        realtimeScope = null
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
                val tables = listOf("together_messages", "friendships", "study_presence", "shared_goals")
                tables.forEachIndexed { idx, table ->
                    val joinMsg = JSONObject().apply {
                        put("topic", "realtime:public:$table")
                        put("event", "phx_join")
                        put(
                            "payload",
                            JSONObject().put(
                                "config",
                                JSONObject().put(
                                    "postgres_changes",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("event", "*")
                                            .put("schema", "public")
                                            .put("table", table)
                                    )
                                )
                            ).put("access_token", accessToken)
                        )
                        put("ref", "${idx + 1}")
                    }
                    webSocket.send(joinMsg.toString())
                }
                _state.update { it.copy(isRealtimeConnected = true) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val isChangeEvent = text.contains("INSERT") ||
                    text.contains("UPDATE") ||
                    text.contains("DELETE") ||
                    text.contains("postgres_changes")
                if (!isChangeEvent) return
                val scope = realtimeScope ?: CoroutineScope(Dispatchers.IO)
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        when {
                            text.contains("together_messages") -> refreshMessagesAndCounts(userId, accessToken)
                            else -> refreshAllInternal()
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
        challengesCompleted: Int,
        challengesTotal: Int = 4
    ): SupabaseResult<Unit> {
        if (!supabase.isConfigured() || userId.isBlank() || accessToken.isBlank()) {
            return SupabaseResult.Failure(supabase.configurationStatusMessage(), isConfigMissing = true)
        }
        val payload = TogetherPrivacyFilter.buildSafeProfilePayload(
            userId = userId,
            prefs = prefs,
            tasksCompletedToday = tasksCompletedToday,
            consistencyScore = consistencyScore,
            challengesCompleted = challengesCompleted,
            challengesTotal = challengesTotal
        )
        return when (val res = supabase.restPost("profiles", payload.toString(), accessToken, upsert = true)) {
            is SupabaseResult.Success -> SupabaseResult.Success(Unit)
            is SupabaseResult.Failure -> res
        }
    }

    suspend fun lookupProfileByFriendCode(
        rawQuery: String,
        accessToken: String
    ): SupabaseResult<TogetherFriendProfile?> {
        if (!supabase.isConfigured() || accessToken.isBlank()) {
            return SupabaseResult.Failure(supabase.configurationStatusMessage(), isConfigMissing = true)
        }
        val extracted = FriendSyncCodec.extractInviteCode(rawQuery) ?: rawQuery.trim()
        if (extracted.isBlank()) return SupabaseResult.Success(null)

        val rpcParams = JSONObject().put("p_code", extracted).toString()
        val rpcRes = supabase.restRpc("lookup_profile_by_friend_code", rpcParams, accessToken)
        if (rpcRes is SupabaseResult.Success) {
            val arr = runCatching { JSONArray(rpcRes.value) }.getOrNull()
            if (arr != null && arr.length() > 0) {
                return SupabaseResult.Success(parseFriendProfile(arr.getJSONObject(0)))
            }
            if (arr != null) return SupabaseResult.Success(null)
        }

        // Fallback query if RPC is not deployed on the remote Postgres instance yet
        val encodedHandle = Uri.encode(SupabaseAuthAndSyncClient.normalizeHandle(extracted))
        val encodedCode = Uri.encode(extracted.uppercase())
        val fallback = supabase.restGet(
            "profiles?or=(handle.eq.$encodedHandle,friend_code.eq.$encodedCode)&limit=1",
            accessToken
        )
        return when (fallback) {
            is SupabaseResult.Success -> {
                val arr = JSONArray(fallback.value)
                if (arr.length() == 0) SupabaseResult.Success(null)
                else SupabaseResult.Success(parseFriendProfile(arr.getJSONObject(0)))
            }
            is SupabaseResult.Failure -> fallback
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
            // 1. Friendships involving current user
            val shipsRes = supabase.restGet(
                "friendships?or=(requester_id.eq.$uid,addressee_id.eq.$uid)&order=created_at.desc",
                token
            )
            if (shipsRes is SupabaseResult.Failure) {
                _state.update { it.copy(isSyncing = false, statusNote = shipsRes.message) }
                return shipsRes
            }
            val shipsArr = JSONArray((shipsRes as SupabaseResult.Success).value)
            val acceptedIds = LinkedHashSet<String>()
            val friendshipIdByUser = HashMap<String, String>()
            val togetherOneByUser = HashMap<String, Boolean>()
            val blockedIds = HashSet<String>()
            val incoming = mutableListOf<TogetherFriendRequest>()
            val outgoing = mutableListOf<TogetherFriendRequest>()

            for (i in 0 until shipsArr.length()) {
                val obj = shipsArr.getJSONObject(i)
                val shipId = obj.optString("id")
                val reqId = obj.optString("requester_id")
                val addId = obj.optString("addressee_id")
                val status = obj.optString("status")
                val isTogetherOne = obj.optBoolean("is_together_one", false)
                val otherId = if (reqId == uid) addId else reqId
                val isIncoming = addId == uid
                when (status) {
                    "ACCEPTED" -> if (otherId.isNotBlank()) {
                        acceptedIds.add(otherId)
                        friendshipIdByUser[otherId] = shipId
                        togetherOneByUser[otherId] = isTogetherOne
                    }
                    "BLOCKED" -> if (otherId.isNotBlank()) blockedIds.add(otherId)
                    "PENDING" -> {
                        val reqItem = TogetherFriendRequest(
                            id = shipId,
                            requesterId = reqId,
                            addresseeId = addId,
                            requesterName = obj.optString("requester_name"),
                            requesterHandle = obj.optString("requester_handle"),
                            status = status,
                            isTogetherOne = isTogetherOne,
                            isIncoming = isIncoming,
                            createdAtMillis = obj.optLong("created_at")
                        )
                        if (isIncoming) incoming.add(reqItem) else outgoing.add(reqItem)
                    }
                }
            }

            // 2. Profiles of accepted friends + study_presence
            val profileLookupIds = (acceptedIds + uid + outgoing.map { it.addresseeId }).filter { it.isNotBlank() }.distinct()
            val profilesById = HashMap<String, TogetherFriendProfile>()
            if (profileLookupIds.isNotEmpty()) {
                val inClause = profileLookupIds.joinToString(",")
                val profRes = supabase.restGet("profiles?id=in.($inClause)", token)
                if (profRes is SupabaseResult.Success) {
                    val arr = JSONArray(profRes.value)
                    for (i in 0 until arr.length()) {
                        val p = arr.getJSONObject(i)
                        val prof = parseFriendProfile(p)
                        profilesById[prof.userId] = prof
                    }
                }
            }

            // 2b. Enrich outgoing requests with addressee names
            for (idx in outgoing.indices) {
                val reqItem = outgoing[idx]
                profilesById[reqItem.addresseeId]?.let { target ->
                    outgoing[idx] = reqItem.copy(
                        addresseeName = target.displayName,
                        addresseeHandle = target.handle
                    )
                }
            }

            // 2c. Live Co-Study Presence
            val presenceMap = HashMap<String, TogetherStudyPresence>()
            if (profileLookupIds.isNotEmpty()) {
                val inClause = profileLookupIds.joinToString(",")
                val presRes = supabase.restGet("study_presence?user_id=in.($inClause)", token)
                if (presRes is SupabaseResult.Success) {
                    val arr = JSONArray(presRes.value)
                    for (i in 0 until arr.length()) {
                        val pres = parsePresence(arr.getJSONObject(i))
                        presenceMap[pres.userId] = pres
                    }
                }
            }

            val friends = acceptedIds
                .filter { it !in blockedIds }
                .mapNotNull { id ->
                    profilesById[id]?.copy(
                        friendshipId = friendshipIdByUser[id].orEmpty(),
                        isTogetherOne = togetherOneByUser[id] == true,
                        presence = presenceMap[id]
                    )
                }
                .sortedByDescending { it.isTogetherOne }
                .toMutableList()

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

            val streakVal = if (friends.isEmpty()) 0 else {
                val sharedActive = friends.count { it.dailyStreak > 0 }
                if (sharedActive > 0) (friends.maxOfOrNull { it.dailyStreak } ?: 1).coerceAtMost(365) else 1
            }

            val outboxList = db.togetherOutboxDao().getAllOnce()
            val failedCount = outboxList.count { it.status == "FAILED" }
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
                    failedOutboxCount = failedCount,
                    togetherStreakDays = streakVal,
                    myPresence = presenceMap[uid] ?: it.myPresence,
                    presenceByUserId = presenceMap
                )
            }

            // Mirror accepted friends cleanly into local Room friends cache & prune disconnected ones
            val existingLocal = db.friendDao().getAllFriendsOnce()
            val remoteIds = friends.mapTo(HashSet()) { it.userId }
            existingLocal.filter { it.remoteUserId.isNotBlank() && it.remoteUserId !in remoteIds }.forEach { stale ->
                db.friendDao().deleteFriend(stale)
            }
            friends.forEach { f ->
                val existing = db.friendDao().findByRemoteOrCodeOrHandle(f.userId, f.friendCode, f.handle)
                db.friendDao().insertFriend(
                    FriendEntity(
                        id = existing?.id ?: 0L,
                        remoteUserId = f.userId,
                        friendshipId = f.friendshipId,
                        isTogetherOne = f.isTogetherOne,
                        displayName = f.displayName,
                        handle = f.handle,
                        friendCode = f.friendCode,
                        level = f.level,
                        xp = f.xp,
                        streakDays = f.dailyStreak,
                        focusMinutesToday = f.focusMinutesToday,
                        challengesCompleted = f.challengesCompleted,
                        challengesTotal = f.challengesTotal.coerceAtLeast(1),
                        petStageName = f.petStageName.ifBlank { "Playful Pup" },
                        sharesStats = f.shareXp || f.shareStreaks || f.shareFocus || f.shareChallenges,
                        shareXp = f.shareXp,
                        shareStreaks = f.shareStreaks,
                        shareFocus = f.shareFocus,
                        shareChallenges = f.shareChallenges,
                        statusText = if (f.isTogetherOne) "Together One Partner" else "Connected online",
                        lastSyncedAtMillis = System.currentTimeMillis()
                    )
                )
            }

            return SupabaseResult.Success(Unit)
        } finally {
            _state.update { it.copy(isSyncing = false) }
        }
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

        // Append queued/failed offline messages cleanly
        outbox.forEach { queued ->
            val list = grouped.getOrPut(queued.conversationId) { mutableListOf() }
            if (list.none { it.clientMessageId == queued.clientMessageId }) {
                list.add(
                    TogetherMessage(
                        id = "outbox_${queued.id}",
                        clientMessageId = queued.clientMessageId,
                        conversationId = queued.conversationId,
                        senderId = uid,
                        recipientId = queued.recipientUserId,
                        content = queued.content,
                        mediaUrl = queued.mediaLocalUri,
                        deliveryStatus = if (queued.status == "FAILED") "FAILED" else "QUEUED",
                        createdAtMillis = queued.createdAtMillis
                    )
                )
            }
        }

        grouped.values.forEach { list -> list.sortBy { it.createdAtMillis } }

        val targetFriends = friendsToUpdate ?: _state.value.friends.toMutableList()
        for (idx in targetFriends.indices) {
            val f = targetFriends[idx]
            val convId = TogetherPrivacyFilter.conversationIdFor(uid, f.userId)
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
                outboxCount = outbox.size,
                failedOutboxCount = outbox.count { q -> q.status == "FAILED" }
            )
        }
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
        currentUserId = myUserId
        currentAccessToken = accessToken
        val cleanQuery = FriendSyncCodec.extractInviteCode(query) ?: query.trim()
        if (cleanQuery.isBlank()) return SupabaseResult.Failure("Enter a Friend Code, @username, or invite link.")

        val lookupRes = lookupProfileByFriendCode(cleanQuery, accessToken)
        if (lookupRes is SupabaseResult.Failure) return lookupRes
        val target = (lookupRes as SupabaseResult.Success).value
            ?: return SupabaseResult.Failure("No FEELORA user found matching \"$cleanQuery\".")

        if (target.userId == myUserId) {
            return SupabaseResult.Failure("That's your own FEELORA Friend Code!")
        }
        if (_state.value.friends.any { it.userId == target.userId }) {
            return SupabaseResult.Success("You're already connected with ${target.displayName}!")
        }

        val payload = JSONObject().apply {
            put("requester_id", myUserId)
            put("addressee_id", target.userId)
            put("requester_name", myName.ifBlank { "FEELORA Friend" })
            put("requester_handle", myHandle.ifBlank { "@feelora" })
            put("status", "PENDING")
            put("is_together_one", _state.value.friends.isEmpty())
            put("created_at", System.currentTimeMillis())
        }
        return when (val ins = supabase.restPost("friendships", payload.toString(), accessToken, upsert = true)) {
            is SupabaseResult.Success -> {
                refreshAllInternal()
                SupabaseResult.Success("Friend request sent to ${target.displayName} (${target.friendCode}).")
            }
            is SupabaseResult.Failure -> ins
        }
    }

    suspend fun respondToFriendRequest(
        requestId: String,
        accept: Boolean,
        accessToken: String
    ): SupabaseResult<Unit> = respondToFriendRequest(
        requestId = requestId,
        status = if (accept) "ACCEPTED" else "DECLINED",
        accessToken = accessToken
    )

    suspend fun respondToFriendRequest(
        requestId: String,
        status: String, // ACCEPTED | DECLINED | BLOCKED
        accessToken: String
    ): SupabaseResult<Unit> {
        if (!supabase.isConfigured() || accessToken.isBlank()) {
            return SupabaseResult.Failure(supabase.configurationStatusMessage(), isConfigMissing = true)
        }
        val body = JSONObject().apply {
            put("status", status)
            if (status == "ACCEPTED" && _state.value.friends.none { it.isTogetherOne }) {
                put("is_together_one", true)
            }
        }.toString()
        val res = supabase.restPatch("friendships?id=eq.$requestId", body, accessToken)
        return when (res) {
            is SupabaseResult.Success -> {
                refreshAllInternal()
                SupabaseResult.Success(Unit)
            }
            is SupabaseResult.Failure -> res
        }
    }

    suspend fun cancelFriendRequest(
        requestId: String,
        accessToken: String
    ): SupabaseResult<Unit> {
        if (!supabase.isConfigured() || accessToken.isBlank()) {
            return SupabaseResult.Failure(supabase.configurationStatusMessage(), isConfigMissing = true)
        }
        return when (val res = supabase.restDelete("friendships?id=eq.$requestId", accessToken)) {
            is SupabaseResult.Success -> {
                refreshAllInternal()
                SupabaseResult.Success(Unit)
            }
            is SupabaseResult.Failure -> res
        }
    }

    suspend fun setTogetherOnePartner(
        myUserId: String,
        friendUserId: String,
        isTogetherOne: Boolean,
        accessToken: String
    ): SupabaseResult<Unit> {
        if (!supabase.isConfigured() || myUserId.isBlank() || accessToken.isBlank()) {
            return SupabaseResult.Failure(supabase.configurationStatusMessage(), isConfigMissing = true)
        }
        currentUserId = myUserId
        currentAccessToken = accessToken
        if (isTogetherOne) {
            // Reset other active Together One markers for current user's friendships
            supabase.restPatch(
                "friendships?or=(requester_id.eq.$myUserId,addressee_id.eq.$myUserId)&is_together_one=eq.true",
                JSONObject().put("is_together_one", false).toString(),
                accessToken
            )
            db.friendDao().clearAllTogetherOne()
        }
        val targetQuery = "friendships?or=(and(requester_id.eq.$myUserId,addressee_id.eq.$friendUserId),and(requester_id.eq.$friendUserId,addressee_id.eq.$myUserId))"
        return when (val res = supabase.restPatch(targetQuery, JSONObject().put("is_together_one", isTogetherOne).toString(), accessToken)) {
            is SupabaseResult.Success -> {
                db.friendDao().updateTogetherOneFlag(friendUserId, isTogetherOne)
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
        currentUserId = myUserId
        currentAccessToken = accessToken
        val query = "friendships?or=(and(requester_id.eq.$myUserId,addressee_id.eq.$friendUserId),and(requester_id.eq.$friendUserId,addressee_id.eq.$myUserId))"
        val res = if (block) {
            supabase.restPatch(query, JSONObject().put("status", "BLOCKED").put("is_together_one", false).toString(), accessToken)
        } else {
            supabase.restDelete(query, accessToken)
        }
        return when (res) {
            is SupabaseResult.Success -> {
                db.friendDao().deleteByRemoteUserId(friendUserId)
                refreshAllInternal()
                SupabaseResult.Success(Unit)
            }
            is SupabaseResult.Failure -> res
        }
    }

    suspend fun upsertStudyPresence(
        userId: String,
        displayName: String,
        isStudying: Boolean,
        sessionTitle: String,
        targetMinutes: Int,
        startedAtMillis: Long,
        endsAtMillis: Long,
        kuttaEmotion: String,
        cheerNote: String,
        accessToken: String
    ): SupabaseResult<TogetherStudyPresence> {
        val now = System.currentTimeMillis()
        val localPresence = TogetherStudyPresence(
            userId = userId.ifBlank { "local_self" },
            displayName = displayName.ifBlank { "You" },
            isStudying = isStudying,
            sessionTitle = sessionTitle.trim(),
            targetMinutes = targetMinutes.coerceAtLeast(1),
            startedAtMillis = startedAtMillis,
            endsAtMillis = endsAtMillis,
            kuttaEmotion = kuttaEmotion.ifBlank { "HAPPY" },
            cheerNote = cheerNote.trim().take(120),
            updatedAtMillis = now
        )
        _state.update { it.copy(myPresence = localPresence) }

        if (!supabase.isConfigured() || userId.isBlank() || accessToken.isBlank()) {
            return SupabaseResult.Failure(supabase.configurationStatusMessage(), isConfigMissing = true)
        }

        val payload = JSONObject().apply {
            put("user_id", userId)
            put("display_name", localPresence.displayName)
            put("is_studying", localPresence.isStudying)
            put("session_title", localPresence.sessionTitle)
            put("target_minutes", localPresence.targetMinutes)
            put("started_at_millis", localPresence.startedAtMillis)
            put("ends_at_millis", localPresence.endsAtMillis)
            put("kutta_emotion", localPresence.kuttaEmotion)
            put("cheer_note", localPresence.cheerNote)
            put("updated_at", now)
        }

        return when (val res = supabase.restPost("study_presence", payload.toString(), accessToken, upsert = true)) {
            is SupabaseResult.Success -> SupabaseResult.Success(localPresence)
            is SupabaseResult.Failure -> res
        }
    }

    suspend fun sendQuickCheer(
        myUserId: String,
        myName: String,
        friendUserId: String,
        cheerMessage: String,
        accessToken: String
    ): SupabaseResult<Unit> {
        val msgRes = sendMessage(
            myUserId = myUserId,
            friendUserId = friendUserId,
            content = cheerMessage,
            mediaLocalUri = null,
            accessToken = accessToken
        )
        if (msgRes is SupabaseResult.Success) {
            postActivity(myUserId, myName, "Sent a KUTTA cheer: \"$cheerMessage\"", accessToken)
        }
        return msgRes
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

        var uploadedMediaUrl: String? = null
        if (mediaLocalUri != null && supabase.isConfigured() && accessToken.isNotBlank()) {
            val objectPath = "$myUserId/$clientMsgId.jpg"
            when (val uploadRes = supabase.uploadImageToStorage("together-media", objectPath, mediaLocalUri, accessToken)) {
                is SupabaseResult.Success -> uploadedMediaUrl = uploadRes.value
                is SupabaseResult.Failure -> uploadedMediaUrl = null
            }
        }

        val initialEntity = TogetherOutboxEntity(
            conversationId = convId,
            recipientUserId = friendUserId,
            clientMessageId = clientMsgId,
            content = cleanText,
            mediaLocalUri = uploadedMediaUrl ?: mediaLocalUri?.toString(),
            status = "PENDING",
            createdAtMillis = now
        )
        db.togetherOutboxDao().upsert(initialEntity)

        if (!supabase.isConfigured() || myUserId.isBlank() || accessToken.isBlank()) {
            val errMsg = supabase.configurationStatusMessage()
            db.togetherOutboxDao().upsert(initialEntity.copy(status = "FAILED", retryCount = 1))
            _state.update { it.copy(lastMessageError = errMsg) }
            refreshMessagesAndCounts(effectiveUid, accessToken)
            return SupabaseResult.Failure(errMsg, isConfigMissing = true)
        }

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
                _state.update { it.copy(lastMessageError = null) }
                refreshMessagesAndCounts(myUserId, accessToken)
                SupabaseResult.Success(Unit)
            }
            is SupabaseResult.Failure -> {
                val persisted = db.togetherOutboxDao().getAllOnce().firstOrNull { it.clientMessageId == clientMsgId }
                if (persisted != null) {
                    db.togetherOutboxDao().upsert(
                        persisted.copy(status = "FAILED", retryCount = persisted.retryCount + 1)
                    )
                }
                _state.update { it.copy(lastMessageError = postRes.message) }
                refreshMessagesAndCounts(myUserId, accessToken)
                postRes
            }
        }
    }

    suspend fun retryFailedMessages(myUserId: String, accessToken: String): SupabaseResult<Int> {
        if (!supabase.isConfigured() || myUserId.isBlank() || accessToken.isBlank()) {
            val msg = supabase.configurationStatusMessage()
            _state.update { it.copy(lastMessageError = msg) }
            return SupabaseResult.Failure(msg, isConfigMissing = true)
        }
        currentUserId = myUserId
        currentAccessToken = accessToken
        val sentCount = flushOutboxInternal()
        refreshMessagesAndCounts(myUserId, accessToken)
        return SupabaseResult.Success(sentCount)
    }

    suspend fun markConversationRead(myUserId: String, friendUserId: String, accessToken: String) {
        if (!supabase.isConfigured() || myUserId.isBlank() || accessToken.isBlank()) return
        val convId = TogetherPrivacyFilter.conversationIdFor(myUserId, friendUserId)
        val query = "together_messages?conversation_id=eq.$convId&recipient_id=eq.$myUserId&delivery_status=neq.READ"
        supabase.restPatch(query, JSONObject().put("delivery_status", "READ").toString(), accessToken)
        refreshMessagesAndCounts(myUserId, accessToken)
    }

    private suspend fun flushOutboxInternal(): Int {
        val uid = currentUserId
        val token = currentAccessToken
        if (!supabase.isConfigured() || uid.isBlank() || token.isBlank()) return 0
        val queued = db.togetherOutboxDao().getAllOnce()
        var flushed = 0
        queued.forEach { item ->
            val convId = TogetherPrivacyFilter.conversationIdFor(uid, item.recipientUserId)
            val payload = JSONObject().apply {
                put("client_message_id", item.clientMessageId)
                put("conversation_id", convId)
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
                flushed++
            } else if (res is SupabaseResult.Failure) {
                db.togetherOutboxDao().upsert(item.copy(status = "FAILED", retryCount = item.retryCount + 1))
                _state.update { it.copy(lastMessageError = res.message) }
            }
        }
        if (flushed > 0 && db.togetherOutboxDao().getAllOnce().isEmpty()) {
            _state.update { it.copy(lastMessageError = null) }
        }
        return flushed
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
        currentUserId = myUserId
        currentAccessToken = accessToken
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
        currentUserId = myUserId
        currentAccessToken = accessToken
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
        currentUserId = myUserId
        currentAccessToken = accessToken
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
        val shareXp = p.optBoolean("share_xp", true)
        val shareStreaks = p.optBoolean("share_streaks", true)
        val shareFocus = p.optBoolean("share_focus", false)
        val shareChallenges = p.optBoolean("share_challenges", true)
        val rawXp = maxOf(p.optInt("total_xp", 0), p.optInt("xp", 0))
        return TogetherFriendProfile(
            userId = p.optString("id"),
            displayName = p.optString("display_name", "Friend"),
            handle = p.optString("handle", "@friend"),
            bio = p.optString("bio", ""),
            friendCode = p.optString("friend_code", ""),
            avatarPreset = UserAvatarPreset.fromStored(p.optString("avatar_preset")),
            avatarUrl = p.optString("avatar_url", ""),
            shareXp = shareXp,
            shareStreaks = shareStreaks,
            shareFocus = shareFocus,
            shareChallenges = shareChallenges,
            level = if (shareXp) p.optInt("level", 1) else 1,
            xp = if (shareXp) rawXp else 0,
            dailyStreak = if (shareStreaks) p.optInt("daily_streak", 0) else 0,
            focusMinutesToday = if (shareFocus) p.optInt("focus_minutes_today", 0) else 0,
            focusDateEpochDay = if (shareFocus) p.optLong("focus_date_epoch_day", 0L) else 0L,
            tasksCompletedToday = if (shareChallenges) p.optInt("tasks_completed_today", 0) else 0,
            consistencyScore = if (shareChallenges) p.optInt("consistency_score", 0) else 0,
            challengesCompleted = if (shareChallenges) p.optInt("challenges_completed", 0) else 0,
            challengesTotal = if (shareChallenges) p.optInt("challenges_total", 4).coerceAtLeast(1) else 4,
            petStageName = p.optString("pet_stage_name", "Playful Pup").ifBlank { "Playful Pup" }
        )
    }

    private fun parsePresence(o: JSONObject): TogetherStudyPresence {
        return TogetherStudyPresence(
            userId = o.optString("user_id"),
            displayName = o.optString("display_name", "Friend"),
            isStudying = o.optBoolean("is_studying", false),
            sessionTitle = o.optString("session_title", ""),
            targetMinutes = o.optInt("target_minutes", 25).coerceAtLeast(1),
            startedAtMillis = o.optLong("started_at_millis", 0L),
            endsAtMillis = o.optLong("ends_at_millis", 0L),
            kuttaEmotion = o.optString("kutta_emotion", "HAPPY"),
            cheerNote = o.optString("cheer_note", ""),
            updatedAtMillis = o.optLong("updated_at", 0L)
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
