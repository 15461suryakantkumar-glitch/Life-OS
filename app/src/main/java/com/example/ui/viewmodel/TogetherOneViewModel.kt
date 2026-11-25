package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.audio.LifeOsSoundCue
import com.example.data.audio.LifeOsSoundManager
import com.example.data.local.ChallengeEntity
import com.example.data.local.LifeOsDatabase
import com.example.data.local.StudySessionEntity
import com.example.data.local.TaskEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserPreferencesState
import com.example.data.social.FeeloraSupabase
import com.example.data.social.FriendSyncCodec
import com.example.data.social.FriendSyncManager
import com.example.data.social.SupabaseResult
import com.example.data.social.TogetherCompetitionMode
import com.example.data.social.TogetherFriendProfile
import com.example.data.social.TogetherReaction
import com.example.data.social.TogetherServiceLocator
import com.example.data.social.TogetherSharedGoal
import com.example.data.social.TogetherState
import com.example.data.social.TogetherStudyPresence
import com.example.util.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TogetherOneUiState(
    val prefs: UserPreferencesState = UserPreferencesState(),
    val togetherState: TogetherState = TogetherState(),
    val isOnline: Boolean = false,
    val isBusy: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null,
    val myFriendCode: String = "",
    val myInviteLink: String = "",
    val myDeepLink: String = "",
    val myWeeklyStudyMinutes: Int = 0,
    val myTasksDoneToday: Int = 0,
    val myChallengesCompleted: Int = 0,
    val myChallengesTotal: Int = 4,
    val activePartner: TogetherFriendProfile? = null,
    val partnerPresence: TogetherStudyPresence? = null
)

class TogetherOneViewModel(application: Application) : AndroidViewModel(application) {
    private val db = LifeOsDatabase.getInstance(application)
    private val prefsRepo = UserPreferencesRepository(application)
    private val supabaseClient = FeeloraSupabase.client(application)
    private val togetherRepository = TogetherServiceLocator.getRepository(application)
    private val friendSyncManager = FriendSyncManager(application)
    private val soundManager = LifeOsSoundManager(application) { false }

    private val busyState = MutableStateFlow(false)
    private val feedbackState = MutableStateFlow<String?>(null)
    private val errorState = MutableStateFlow<String?>(null)

    private data class LocalStats(
        val tasks: List<TaskEntity>,
        val sessions: List<StudySessionEntity>,
        val challenges: List<ChallengeEntity>
    )

    private val localStatsFlow = combine(
        db.taskDao().observeAllTasks(),
        db.studySessionDao().observeAll(),
        db.challengeDao().observeAllChallenges()
    ) { tasks, sessions, challenges ->
        LocalStats(tasks = tasks, sessions = sessions, challenges = challenges)
    }

    private val uiFeedbackFlow = combine(
        busyState,
        feedbackState,
        errorState
    ) { busy, feedback, err ->
        Triple(busy, feedback, err)
    }

    val uiState: StateFlow<TogetherOneUiState> = combine(
        prefsRepo.preferencesFlow,
        togetherRepository.state,
        friendSyncManager.observeNetworkOnline(),
        localStatsFlow,
        uiFeedbackFlow
    ) { prefs, tState, online, stats, uiFeedback ->
        val today = DateTimeUtils.todayEpochDay()
        val weekStart = today - 6L
        val weeklyMins = stats.sessions
            .filter { it.dateEpochDay in weekStart..today }
            .sumOf { it.actualMinutes }
            .coerceAtLeast(prefs.focusMinutesLoggedToday)
        val tasksDoneToday = stats.tasks.count {
            it.isCompleted && it.completedAtMillis?.let { ts -> DateTimeUtils.epochDayFromMillis(ts) == today } == true
        }
        val challengesDone = stats.challenges.count { it.isClaimed || it.currentProgress >= it.targetCount }
        val challengesTotal = stats.challenges.size.coerceAtLeast(1)

        val code = FriendSyncCodec.resolveFriendCode(prefs)
        val partner = tState.activePartner
        val partnerPresence = partner?.let { tState.presenceByUserId[it.userId] ?: it.presence }

        TogetherOneUiState(
            prefs = prefs,
            togetherState = tState,
            isOnline = online,
            isBusy = uiFeedback.first,
            feedbackMessage = uiFeedback.second,
            errorMessage = uiFeedback.third,
            myFriendCode = code,
            myInviteLink = FriendSyncCodec.buildInviteLink(code),
            myDeepLink = FriendSyncCodec.buildDeepLink(code),
            myWeeklyStudyMinutes = weeklyMins,
            myTasksDoneToday = tasksDoneToday,
            myChallengesCompleted = challengesDone,
            myChallengesTotal = challengesTotal,
            activePartner = partner,
            partnerPresence = partnerPresence
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TogetherOneUiState()
    )

    fun clearFeedback() {
        feedbackState.value = null
        errorState.value = null
        togetherRepository.clearMessageError()
    }

    private suspend fun ensureSession(): UserPreferencesState? {
        val current = prefsRepo.preferencesFlow.first()
        if (!supabaseClient.isConfigured()) {
            togetherRepository.refreshConfigurationState()
            return null
        }
        return when (val res = supabaseClient.ensureSession(current)) {
            is SupabaseResult.Success -> {
                val s = res.value
                prefsRepo.saveSupabaseSession(
                    userId = s.userId,
                    email = s.email,
                    accessToken = s.accessToken,
                    refreshToken = s.refreshToken,
                    expiresAtMillis = s.expiresAtMillis,
                    emailVerified = s.emailVerified
                )
                val updated = prefsRepo.preferencesFlow.first()
                togetherRepository.startRealtimeSync(viewModelScope, updated.supabaseUserId, updated.supabaseAccessToken)
                syncProfileInternal(updated)
                updated
            }
            is SupabaseResult.Failure -> {
                errorState.value = res.message
                null
            }
        }
    }

    private suspend fun syncProfileInternal(prefs: UserPreferencesState) {
        if (!prefs.hasValidRemoteSession || !supabaseClient.isConfigured()) return
        val snapshot = uiState.value
        togetherRepository.syncMySafeProfile(
            userId = prefs.supabaseUserId,
            accessToken = prefs.supabaseAccessToken,
            prefs = prefs,
            tasksCompletedToday = snapshot.myTasksDoneToday,
            consistencyScore = 0,
            challengesCompleted = snapshot.myChallengesCompleted,
            challengesTotal = snapshot.myChallengesTotal
        )
    }

    fun refresh() {
        viewModelScope.launch {
            busyState.value = true
            errorState.value = null
            val active = ensureSession()
            if (active != null && active.hasValidRemoteSession) {
                when (val res = togetherRepository.refreshAll()) {
                    is SupabaseResult.Failure -> errorState.value = res.message
                    is SupabaseResult.Success -> Unit
                }
            }
            busyState.value = false
        }
    }

    fun sendInviteOrRequest(rawInput: String, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val codeOrHandle = FriendSyncCodec.extractInviteCode(rawInput)
        if (codeOrHandle == null) {
            val msg = "Enter a Friend Code (FLR-XXXX-1234), @username, or invite link."
            errorState.value = msg
            onComplete(false, msg)
            return
        }
        viewModelScope.launch {
            busyState.value = true
            errorState.value = null
            val active = ensureSession()
            if (active == null || !active.hasValidRemoteSession) {
                val msg = errorState.value ?: supabaseClient.configurationStatusMessage()
                busyState.value = false
                onComplete(false, msg)
                return@launch
            }
            when (
                val res = togetherRepository.sendFriendRequest(
                    query = codeOrHandle,
                    myUserId = active.supabaseUserId,
                    myName = active.userName,
                    myHandle = active.userHandle,
                    accessToken = active.supabaseAccessToken
                )
            ) {
                is SupabaseResult.Success -> {
                    soundManager.playCue(LifeOsSoundCue.TOGETHER_MESSAGE, active)
                    feedbackState.value = res.value
                    onComplete(true, res.value)
                }
                is SupabaseResult.Failure -> {
                    errorState.value = res.message
                    onComplete(false, res.message)
                }
            }
            busyState.value = false
        }
    }

    fun respondToRequest(requestId: String, accept: Boolean) {
        viewModelScope.launch {
            busyState.value = true
            val active = ensureSession()
            if (active != null) {
                when (val res = togetherRepository.respondToFriendRequest(requestId, accept, active.supabaseAccessToken)) {
                    is SupabaseResult.Success -> {
                        soundManager.playCue(LifeOsSoundCue.UI_CONFIRM, active)
                        feedbackState.value = if (accept) "Connected in Together One!" else "Request declined"
                    }
                    is SupabaseResult.Failure -> errorState.value = res.message
                }
            }
            busyState.value = false
        }
    }

    fun cancelOutgoingRequest(requestId: String) {
        viewModelScope.launch {
            busyState.value = true
            val active = ensureSession()
            if (active != null) {
                when (val res = togetherRepository.cancelFriendRequest(requestId, active.supabaseAccessToken)) {
                    is SupabaseResult.Success -> feedbackState.value = "Invite cancelled"
                    is SupabaseResult.Failure -> errorState.value = res.message
                }
            }
            busyState.value = false
        }
    }

    fun toggleTogetherOnePartner(friendUserId: String, makePrimary: Boolean) {
        viewModelScope.launch {
            busyState.value = true
            val active = ensureSession()
            if (active != null) {
                when (
                    val res = togetherRepository.setTogetherOnePartner(
                        myUserId = active.supabaseUserId,
                        friendUserId = friendUserId,
                        isTogetherOne = makePrimary,
                        accessToken = active.supabaseAccessToken
                    )
                ) {
                    is SupabaseResult.Success -> {
                        soundManager.playCue(LifeOsSoundCue.UI_CONFIRM, active)
                        feedbackState.value = if (makePrimary) "Set as your Together One partner" else "Removed Together One spotlight"
                    }
                    is SupabaseResult.Failure -> errorState.value = res.message
                }
            }
            busyState.value = false
        }
    }

    fun removeOrBlockFriend(friendUserId: String, block: Boolean) {
        viewModelScope.launch {
            busyState.value = true
            val active = ensureSession()
            if (active != null) {
                when (
                    val res = togetherRepository.blockOrRemoveFriend(
                        myUserId = active.supabaseUserId,
                        friendUserId = friendUserId,
                        block = block,
                        accessToken = active.supabaseAccessToken
                    )
                ) {
                    is SupabaseResult.Success -> {
                        feedbackState.value = if (block) "User blocked" else "Connection removed"
                    }
                    is SupabaseResult.Failure -> errorState.value = res.message
                }
            }
            busyState.value = false
        }
    }

    fun startCoStudySession(
        sessionTitle: String,
        targetMinutes: Int,
        kuttaEmotion: String = "FOCUSED",
        cheerNote: String = ""
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val safeMins = targetMinutes.coerceIn(5, 240)
            val endsAt = now + safeMins * 60_000L
            val currentPrefs = prefsRepo.preferencesFlow.first()
            val active = ensureSession() ?: currentPrefs
            when (
                val res = togetherRepository.upsertStudyPresence(
                    userId = active.supabaseUserId,
                    displayName = active.userName.ifBlank { "Member" },
                    isStudying = true,
                    sessionTitle = sessionTitle.ifBlank { "Deep Focus Co-Study" },
                    targetMinutes = safeMins,
                    startedAtMillis = now,
                    endsAtMillis = endsAt,
                    kuttaEmotion = kuttaEmotion,
                    cheerNote = cheerNote,
                    accessToken = active.supabaseAccessToken
                )
            ) {
                is SupabaseResult.Success -> {
                    soundManager.playCue(LifeOsSoundCue.UI_CONFIRM, active)
                    feedbackState.value = "Live Co-Study started (${safeMins}m)"
                }
                is SupabaseResult.Failure -> {
                    feedbackState.value = if (res.isConfigMissing) {
                        "Co-Study timer active locally (configure Supabase Secrets for live partner presence)."
                    } else {
                        res.message
                    }
                }
            }
        }
    }

    fun stopCoStudySession() {
        viewModelScope.launch {
            val currentPrefs = prefsRepo.preferencesFlow.first()
            val active = ensureSession() ?: currentPrefs
            togetherRepository.upsertStudyPresence(
                userId = active.supabaseUserId,
                displayName = active.userName.ifBlank { "Member" },
                isStudying = false,
                sessionTitle = "",
                targetMinutes = 25,
                startedAtMillis = 0L,
                endsAtMillis = 0L,
                kuttaEmotion = "HAPPY",
                cheerNote = "",
                accessToken = active.supabaseAccessToken
            )
            feedbackState.value = "Co-Study session paused"
        }
    }

    fun sendCheer(friendUserId: String, cheerMessage: String) {
        viewModelScope.launch {
            val active = ensureSession()
            if (active == null || !active.hasValidRemoteSession) {
                errorState.value = errorState.value ?: supabaseClient.configurationStatusMessage()
                return@launch
            }
            soundManager.playCue(LifeOsSoundCue.TOGETHER_MESSAGE, active)
            when (
                val res = togetherRepository.sendQuickCheer(
                    myUserId = active.supabaseUserId,
                    myName = active.userName,
                    friendUserId = friendUserId,
                    cheerMessage = cheerMessage,
                    accessToken = active.supabaseAccessToken
                )
            ) {
                is SupabaseResult.Success -> feedbackState.value = "Cheer sent!"
                is SupabaseResult.Failure -> errorState.value = res.message
            }
        }
    }

    fun updatePrivacy(shareXp: Boolean, shareStreaks: Boolean, shareFocus: Boolean, shareChallenges: Boolean) {
        viewModelScope.launch {
            prefsRepo.updatePrivacy(shareXp, shareStreaks, shareFocus, shareChallenges)
            val updated = prefsRepo.preferencesFlow.first()
            val active = if (updated.hasValidRemoteSession) updated else ensureSession()
            if (active != null) {
                syncProfileInternal(updated.copy(supabaseUserId = active.supabaseUserId, supabaseAccessToken = active.supabaseAccessToken))
            }
            feedbackState.value = "Privacy sharing preferences saved"
        }
    }

    fun createSharedGoal(
        partnerId: String?,
        partnerName: String?,
        title: String,
        description: String,
        targetValue: Int,
        unitLabel: String
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val active = ensureSession() ?: return@launch
            when (
                val res = togetherRepository.createSharedGoal(
                    myUserId = active.supabaseUserId,
                    partnerId = partnerId,
                    partnerName = partnerName,
                    title = title,
                    description = description,
                    targetValue = targetValue,
                    unitLabel = unitLabel,
                    accessToken = active.supabaseAccessToken
                )
            ) {
                is SupabaseResult.Success -> {
                    soundManager.playCue(LifeOsSoundCue.UI_CONFIRM, active)
                    feedbackState.value = "Shared goal created"
                }
                is SupabaseResult.Failure -> errorState.value = res.message
            }
        }
    }

    fun incrementSharedGoal(goal: TogetherSharedGoal, delta: Int = 1) {
        viewModelScope.launch {
            val active = ensureSession() ?: return@launch
            when (
                val res = togetherRepository.incrementSharedGoal(
                    goal = goal,
                    delta = delta,
                    myName = active.userName,
                    myUserId = active.supabaseUserId,
                    accessToken = active.supabaseAccessToken
                )
            ) {
                is SupabaseResult.Success -> soundManager.playCue(LifeOsSoundCue.TASK_COMPLETE, active)
                is SupabaseResult.Failure -> errorState.value = res.message
            }
        }
    }

    fun createCompetition(
        partnerId: String?,
        partnerName: String?,
        title: String,
        mode: TogetherCompetitionMode
    ) {
        viewModelScope.launch {
            val snapshot = uiState.value
            val active = ensureSession() ?: return@launch
            val initialScore = when (mode) {
                TogetherCompetitionMode.XP_SPRINT -> active.totalXp
                TogetherCompetitionMode.TASK_FINISH -> snapshot.myTasksDoneToday
                TogetherCompetitionMode.STUDY_FOCUS_RACE -> active.focusMinutesLoggedToday
                TogetherCompetitionMode.STREAK_CHALLENGE -> active.dailyStreak
                TogetherCompetitionMode.CONSISTENCY_DUEL -> snapshot.myChallengesCompleted
            }
            when (
                val res = togetherRepository.createCompetition(
                    myUserId = active.supabaseUserId,
                    partnerId = partnerId,
                    partnerName = partnerName,
                    title = title,
                    mode = mode,
                    myInitialScore = initialScore,
                    accessToken = active.supabaseAccessToken
                )
            ) {
                is SupabaseResult.Success -> {
                    soundManager.playCue(LifeOsSoundCue.UI_CONFIRM, active)
                    feedbackState.value = "${mode.title} started!"
                }
                is SupabaseResult.Failure -> errorState.value = res.message
            }
        }
    }

    fun reactToActivity(activityId: String, reaction: TogetherReaction) {
        viewModelScope.launch {
            val active = ensureSession() ?: return@launch
            soundManager.playCue(LifeOsSoundCue.UI_TAP, active)
            togetherRepository.reactToActivity(activityId, active.supabaseUserId, reaction, active.supabaseAccessToken)
        }
    }

    fun retryFailedMessages() {
        viewModelScope.launch {
            val active = ensureSession() ?: return@launch
            when (val res = togetherRepository.retryFailedMessages(active.supabaseUserId, active.supabaseAccessToken)) {
                is SupabaseResult.Success -> {
                    feedbackState.value = if (res.value > 0) "Sent ${res.value} queued message(s)" else "All messages up to date"
                }
                is SupabaseResult.Failure -> errorState.value = res.message
            }
        }
    }

    fun sendChatMessage(friendUserId: String, content: String, mediaUri: Uri? = null) {
        viewModelScope.launch {
            val active = ensureSession() ?: prefsRepo.preferencesFlow.first()
            soundManager.playCue(LifeOsSoundCue.TOGETHER_MESSAGE, active)
            when (
                val res = togetherRepository.sendMessage(
                    myUserId = active.supabaseUserId,
                    friendUserId = friendUserId,
                    content = content,
                    mediaLocalUri = mediaUri,
                    accessToken = active.supabaseAccessToken
                )
            ) {
                is SupabaseResult.Success -> Unit
                is SupabaseResult.Failure -> errorState.value = res.message
            }
        }
    }
}
