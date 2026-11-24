package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.audio.LifeOsSoundCue
import com.example.data.audio.LifeOsSoundManager
import com.example.data.audio.SoundCategory
import com.example.data.local.AiMessageEntity
import com.example.data.local.AiProviderUsageEntity
import com.example.data.local.AndroidUsageStatsBridge
import com.example.data.local.AppUsageCategory
import com.example.data.local.AppUsageLogEntity
import com.example.data.local.CalendarEventEntity
import com.example.data.local.ChallengeEntity
import com.example.data.local.CompanionMood
import com.example.data.local.CompanionMoodResolver
import com.example.data.local.CycleLogEntity
import com.example.data.local.DailyCheckInEntity
import com.example.data.local.DailyProgressLogEntity
import com.example.data.local.FriendEntity
import com.example.data.local.HabitEntity
import com.example.data.local.LifeOsDatabase
import com.example.data.local.LifeOsSeeder
import com.example.data.local.NotificationHistoryEntity
import com.example.data.local.PetAccessory
import com.example.data.local.QuickNoteEntity
import com.example.data.local.RecurrencePattern
import com.example.data.local.StudyChapterEntity
import com.example.data.local.StudyChapterStatus
import com.example.data.local.StudyResourceEntity
import com.example.data.local.StudyResourceType
import com.example.data.local.StudySessionEntity
import com.example.data.local.StudySessionMode
import com.example.data.local.SubjectEntity
import com.example.data.local.SuruMemoryEntity
import com.example.data.local.SuruStudyPlanEntity
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import com.example.data.local.TaskSectionEntity
import com.example.data.local.TaskSectionMapper
import com.example.data.local.UsageTelemetryReport
import com.example.data.local.UserAvatarPreset
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserPreferencesState
import com.example.data.local.WellbeingRange
import com.example.data.local.WellbeingTelemetryState
import com.example.data.local.WidgetConfigEntity
import com.example.data.social.FriendSyncCodec
import com.example.data.social.FriendSyncManager
import com.example.data.social.NearbyFriendPeer
import com.example.data.social.SupabaseAuthAndSyncClient
import com.example.data.social.SupabaseResult
import com.example.data.social.TogetherCompetitionMode
import com.example.data.social.TogetherReaction
import com.example.data.social.TogetherRepository
import com.example.data.social.TogetherSharedGoal
import com.example.data.social.TogetherState
import com.example.kutta.KuttaEvent
import com.example.kutta.KuttaEventHandler
import com.example.kutta.KuttaVisualState
import com.example.notifications.AppPermissionInfo
import com.example.notifications.FeeloraNotificationCategory
import com.example.notifications.PermissionCenterHelper
import com.example.notifications.TaskReminderScheduler
import com.example.suru.CalendarIntelligence
import com.example.suru.LifeContextSnapshot
import com.example.suru.ProviderDiagnostic
import com.example.suru.SuruActionEngine
import com.example.suru.SuruAiProvider
import com.example.suru.SuruAiResponse
import com.example.suru.SuruCloudAiStatus
import com.example.suru.SuruConfirmationRequest
import com.example.suru.SuruDailyBriefing
import com.example.suru.SuruImageAttachment
import com.example.suru.SuruImageLoadResult
import com.example.suru.SuruImageProcessor
import com.example.suru.SuruIntent
import com.example.suru.SuruIntelligenceService
import com.example.suru.SuruMascotState
import com.example.suru.SuruNluEngine
import com.example.suru.SuruPendingContext
import com.example.suru.SuruStructuredAction
import com.example.suru.SuruStudyHelpMode
import com.example.suru.SuruStudyHelpResult
import com.example.suru.SuruStudyPlanDraft
import com.example.suru.SuruStudyPlanner
import com.example.suru.SuruUndoRecord
import com.example.suru.SuruVoiceLanguage
import com.example.suru.SuruVoiceManager
import com.example.suru.SuruVoiceState
import com.example.ui.theme.LifeOsThemeMode
import com.example.util.CycleCareAnalyzer
import com.example.util.CycleCareSummary
import com.example.util.DailyAnalysisReport
import com.example.util.DateTimeUtils
import com.example.util.ProductivityAnalyzer
import com.example.util.ProductivityCalculator
import com.example.util.WeeklyAnalysisReport
import com.example.widgets.LifeOsWidgetUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class AchievementBadge(
    val id: String,
    val title: String,
    val description: String,
    val xpBonus: Int
)

object BadgeCatalog {
    val all = listOf(
        AchievementBadge("FIRST_LAUNCH", "Ready to Go", "Set up your Feelora workspace", 0),
        AchievementBadge("STREAK_3", "3-Day Streak", "Stayed active for 3 days in a row", 75),
        AchievementBadge("TASK_MASTER_5", "Task Finisher", "Completed 5 or more tasks", 100),
        AchievementBadge("DEEP_FOCUS", "Deep Focus", "Logged 60+ minutes of focus time", 120),
        AchievementBadge("LEVEL_5", "Steady Explorer", "Reached Level 5 with KUTTA", 200),
        AchievementBadge("AI_COMMANDER", "SURU Strategist", "Planned a mission with SURU", 0)
    )
}

data class StudyTimerState(
    val selectedMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val isBreakPhase: Boolean = false,
    val sessionMode: StudySessionMode = StudySessionMode.POMODORO,
    val pomodoroCyclesCompleted: Int = 0,
    val linkedTaskId: Long? = null,
    val activeSubjectId: Long? = null,
    val activeChapterId: Long? = null
)

data class AuthUiState(
    val isLoading: Boolean = false,
    val isCloudConfigured: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val verificationEmailPending: String? = null,
    val isResetSent: Boolean = false,
    val usernameAvailable: Boolean? = null,
    val usernameCheckBusy: Boolean = false,
    val sessionExpiredPrompt: Boolean = false
)

data class LifeOsUiState(
    val isPrefsLoaded: Boolean = false,
    val prefs: UserPreferencesState = UserPreferencesState(),
    val taskSections: List<TaskSectionEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val events: List<CalendarEventEntity> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val widgets: List<WidgetConfigEntity> = emptyList(),
    val quickNote: String = "",
    val usageLogs: List<AppUsageLogEntity> = emptyList(),
    val wellbeingRange: WellbeingRange = WellbeingRange.DAILY,
    val wellbeingReport: UsageTelemetryReport = UsageTelemetryReport(
        state = WellbeingTelemetryState.PERMISSION_NOT_GRANTED,
        range = WellbeingRange.DAILY,
        totalMinutes = 0,
        distractingMinutes = 0,
        apps = emptyList()
    ),
    val dailyProgressLogs: List<DailyProgressLogEntity> = emptyList(),
    val challenges: List<ChallengeEntity> = emptyList(),
    val friends: List<FriendEntity> = emptyList(),
    val aiMessages: List<AiMessageEntity> = emptyList(),
    val cycleLogs: List<CycleLogEntity> = emptyList(),
    val dailyCheckIns: List<DailyCheckInEntity> = emptyList(),
    val kuttaEventOverride: KuttaVisualState? = null,
    val studyTimer: StudyTimerState = StudyTimerState(),
    val isAiLoading: Boolean = false,
    val aiErrorMessage: String? = null,
    val petReactionBanner: String? = null,
    val levelUpBanner: Int? = null,
    val statusSnackbar: String? = null,
    val hasUsageStatsPermission: Boolean = false,
    val usageStatsStatusLabel: String = "Permission Required",
    val isOnline: Boolean = false,
    val isCloudAiAvailable: Boolean = false,
    val cloudAiStatus: SuruCloudAiStatus = SuruCloudAiStatus.MISSING_API_KEY,
    val aiProviderDiagnostics: List<ProviderDiagnostic> = emptyList(),
    val aiProviderUsageLogs: List<AiProviderUsageEntity> = emptyList(),
    val notificationHistory: List<NotificationHistoryEntity> = emptyList(),
    val isPeerRadarActive: Boolean = false,
    val nearbyPeers: List<NearbyFriendPeer> = emptyList(),
    val suruMemories: List<SuruMemoryEntity> = emptyList(),
    val suruStudyPlans: List<SuruStudyPlanEntity> = emptyList(),
    val suruVoiceState: SuruVoiceState = SuruVoiceState.READY,
    val suruPartialTranscript: String = "",
    val suruVoiceError: String? = null,
    val suruMascotState: SuruMascotState = SuruMascotState.IDLE,
    val suruPendingConfirmation: SuruConfirmationRequest? = null,
    val suruLastUndo: SuruUndoRecord? = null,
    val suruActiveStudyHelp: SuruStudyHelpResult? = null,
    val pendingSuruImage: SuruImageAttachment? = null,
    val appPermissions: List<AppPermissionInfo> = emptyList(),
    val authUiState: AuthUiState = AuthUiState(),
    val togetherState: TogetherState = TogetherState(),
    val activeChatFriendId: String? = null,
    val studySubjects: List<SubjectEntity> = emptyList(),
    val studyChapters: List<StudyChapterEntity> = emptyList(),
    val studySessions: List<StudySessionEntity> = emptyList(),
    val studyResources: List<StudyResourceEntity> = emptyList()
) {
    fun progressForSubject(subjectId: Long): Float {
        val chapters = studyChapters.filter { it.subjectId == subjectId }
        if (chapters.isEmpty()) return 0f
        val done = chapters.count { it.isCompleted }
        return done.toFloat() / chapters.size.toFloat()
    }

    val totalStudySessionMinutesToday: Int
        get() = studySessions.filter { it.dateEpochDay == todayEpochDay }.sumOf { it.actualMinutes }

    val completedChaptersCount: Int
        get() = studyChapters.count { it.isCompleted }

    val unreadNotificationsCount: Int
        get() = notificationHistory.count { !it.isRead }

    val unreadTogetherMessagesCount: Int
        get() = togetherState.friends.sumOf { it.unreadCount } + togetherState.incomingRequests.size
    val suruMorningBriefing: SuruDailyBriefing
        get() = SuruIntelligenceService.buildMorningBriefing(prefs, tasks, events, todayEpochDay)

    val suruEveningReflection: SuruDailyBriefing
        get() = SuruIntelligenceService.buildEveningReflection(prefs, tasks, todayEpochDay)

    val todayEpochDay: Long get() = DateTimeUtils.todayEpochDay()

    val todayTasks: List<TaskEntity>
        get() = tasks.filter { it.deadlineEpochDay <= todayEpochDay || !it.isCompleted }.take(8)

    val completedTasksCount: Int
        get() = tasks.count { it.isCompleted }

    val pendingTasksCount: Int
        get() = tasks.count { !it.isCompleted }

    val todayUsageLogs: List<AppUsageLogEntity>
        get() = usageLogs.filter { it.dateEpochDay == todayEpochDay }

    val totalScreenMinutesToday: Int
        get() = todayUsageLogs.sumOf { it.minutesUsed }

    val distractingMinutesToday: Int
        get() = todayUsageLogs.filter { it.isDistracting }.sumOf { it.minutesUsed }

    val completedChallengesCount: Int
        get() = challenges.count { it.isClaimed || it.currentProgress >= it.targetCount }

    val todayCheckIn: DailyCheckInEntity?
        get() = dailyCheckIns.firstOrNull { it.epochDay == todayEpochDay }

    val cycleSummary: CycleCareSummary
        get() = CycleCareAnalyzer.summarize(
            logs = cycleLogs,
            configuredCycleLength = prefs.averageCycleLengthDays,
            configuredPeriodLength = prefs.averagePeriodLengthDays,
            todayEpochDay = todayEpochDay
        )

    val productivityScore: Int
        get() = ProductivityCalculator.computeScore(
            totalTasks = tasks.size,
            completedTasks = completedTasksCount,
            totalHabits = habits.size,
            completedHabitsToday = habits.count { it.isCompletedOn(todayEpochDay) },
            focusMinutesToday = prefs.focusMinutesLoggedToday,
            dailyFocusGoalMinutes = prefs.dailyFocusGoalMinutes,
            screenMinutesToday = totalScreenMinutesToday,
            dailyScreenLimitMinutes = prefs.dailyScreenLimitMinutes
        )

    val companionMood: CompanionMood
        get() = CompanionMoodResolver.resolve(
            isTimerRunning = studyTimer.isRunning,
            petHappiness = prefs.petHappiness,
            petEnergy = prefs.petEnergy,
            hasRecentPositiveAction = petReactionBanner != null,
            pendingTasks = tasks.filter { !it.isCompleted },
            distractingMinutesToday = distractingMinutesToday,
            dailyScreenLimitMinutes = prefs.dailyScreenLimitMinutes
        )

    val kuttaState: KuttaVisualState
        get() {
            kuttaEventOverride?.let { return it }
            val baselineEmotion = KuttaEventHandler.resolveBaselineEmotion(
                isTimerRunning = studyTimer.isRunning,
                pendingTasks = tasks.filter { !it.isCompleted },
                distractingMinutesToday = distractingMinutesToday,
                dailyScreenLimitMinutes = prefs.dailyScreenLimitMinutes,
                recentPositiveScore = petReactionBanner != null || prefs.petHappiness >= 90,
                lastCheckInMoodScore = todayCheckIn?.moodScore
            )
            return KuttaEventHandler.stateForBaseline(
                emotion = baselineEmotion,
                dialogueIndex = (todayEpochDay + prefs.dailyStreak + completedTasksCount).toInt()
            )
        }

    val dailyAnalysis: DailyAnalysisReport
        get() = ProductivityAnalyzer.analyzeDaily(
            prefs = prefs,
            tasks = tasks,
            habits = habits,
            todayUsageLogs = todayUsageLogs,
            todayEpochDay = todayEpochDay
        )

    val weeklyAnalysis: WeeklyAnalysisReport
        get() = ProductivityAnalyzer.analyzeWeekly(
            prefs = prefs,
            tasks = tasks,
            usageLogs = usageLogs,
            dailyProgressLogs = dailyProgressLogs,
            todayEpochDay = todayEpochDay
        )
}

class LifeOsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = LifeOsDatabase.getInstance(application)
    private val prefsRepo = UserPreferencesRepository(application)
    private val suruService = SuruIntelligenceService(application)
    private val suruActionEngine = SuruActionEngine(db)
    private val suruVoiceManager = SuruVoiceManager(application)
    private val usageBridge = AndroidUsageStatsBridge(application)
    private val friendSyncManager = FriendSyncManager(application)
    private val supabaseClient = SupabaseAuthAndSyncClient(application)
    private val togetherRepository = TogetherRepository(application, supabaseClient)
    private val soundManager = LifeOsSoundManager(application) {
        suruVoiceManager.voiceState.value == SuruVoiceState.SPEAKING
    }

    private val studyTimerState = MutableStateFlow(StudyTimerState())
    private val aiLoadingState = MutableStateFlow(false)
    private val aiErrorState = MutableStateFlow<String?>(null)
    private val petReactionState = MutableStateFlow<String?>(null)
    private val levelUpBannerState = MutableStateFlow<Int?>(null)
    private val snackbarState = MutableStateFlow<String?>(null)
    private val usagePermissionTick = MutableStateFlow(0)
    private val wellbeingRangeState = MutableStateFlow(WellbeingRange.DAILY)
    private val wellbeingReportState = MutableStateFlow(
        UsageTelemetryReport(
            state = WellbeingTelemetryState.PERMISSION_NOT_GRANTED,
            range = WellbeingRange.DAILY,
            totalMinutes = 0,
            distractingMinutes = 0,
            apps = emptyList()
        )
    )
    private val kuttaEventState = MutableStateFlow<KuttaVisualState?>(null)
    private val suruSuccessOverride = MutableStateFlow(false)
    private val suruPendingConfirmationState = MutableStateFlow<SuruConfirmationRequest?>(null)
    private val suruLastUndoState = MutableStateFlow<SuruUndoRecord?>(null)
    private val suruStudyHelpState = MutableStateFlow<SuruStudyHelpResult?>(null)
    private val pendingSuruImageState = MutableStateFlow<SuruImageAttachment?>(null)
    private val appPermissionsState = MutableStateFlow(
        PermissionCenterHelper.collectAllPermissions(application)
    )
    private val authUiStateFlow = MutableStateFlow(
        AuthUiState(isCloudConfigured = supabaseClient.isConfigured())
    )
    private val activeChatFriendIdState = MutableStateFlow<String?>(null)

    private var pendingSuruContext: SuruPendingContext? = null
    private var lastProposedStudyDraft: SuruStudyPlanDraft? = null

    private var timerJob: Job? = null
    private var kuttaEventJob: Job? = null
    private var suruSuccessJob: Job? = null
    private var hasTriggeredMorningToday = false
    private var hasTriggeredCycleApproachToday = false

    private data class CoreFlows(
        val prefs: UserPreferencesState,
        val taskSections: List<TaskSectionEntity>,
        val tasks: List<TaskEntity>,
        val events: List<CalendarEventEntity>,
        val habits: List<HabitEntity>,
        val widgets: List<WidgetConfigEntity>
    )

    private data class SecondaryFlows(
        val note: QuickNoteEntity?,
        val usage: List<AppUsageLogEntity>,
        val dailyProgress: List<DailyProgressLogEntity>,
        val challenges: List<ChallengeEntity>,
        val friends: List<FriendEntity>,
        val aiMessages: List<AiMessageEntity>,
        val cycleLogs: List<CycleLogEntity>,
        val dailyCheckIns: List<DailyCheckInEntity>,
        val suruMemories: List<SuruMemoryEntity>,
        val suruStudyPlans: List<SuruStudyPlanEntity>
    )

    private data class TransientFlows(
        val timer: StudyTimerState,
        val aiLoading: Boolean,
        val aiError: String?,
        val petReaction: String?,
        val levelUpBanner: Int?,
        val snackbar: String?,
        val kuttaOverride: KuttaVisualState?
    )

    private data class SuruUiTransientFlows(
        val voiceState: SuruVoiceState,
        val partialTranscript: String,
        val voiceError: String?,
        val successOverride: Boolean,
        val confirmation: SuruConfirmationRequest?,
        val undoRecord: SuruUndoRecord?,
        val studyHelp: SuruStudyHelpResult?,
        val pendingImage: SuruImageAttachment?
    )

    private data class SocialNetworkFlows(
        val isOnline: Boolean,
        val isPeerRadarActive: Boolean,
        val nearbyPeers: List<NearbyFriendPeer>,
        val usageTick: Int,
        val permissions: List<AppPermissionInfo>,
        val togetherState: TogetherState,
        val authUiState: AuthUiState,
        val activeChatFriendId: String?,
        val wellbeingRange: WellbeingRange,
        val wellbeingReport: UsageTelemetryReport,
        val notificationHistory: List<NotificationHistoryEntity>,
        val aiUsageLogs: List<AiProviderUsageEntity>
    )

    private val tasksAndSectionsFlow = combine(
        db.taskSectionDao().observeAllSections(),
        db.taskDao().observeAllTasks()
    ) { sections, tasks ->
        sections to tasks
    }

    private val coreFlow = combine(
        prefsRepo.preferencesFlow,
        tasksAndSectionsFlow,
        db.calendarEventDao().observeAllEvents(),
        db.habitDao().observeAllHabits(),
        db.widgetConfigDao().observeWidgets()
    ) { prefs, tasksAndSections, events, habits, widgets ->
        CoreFlows(prefs, tasksAndSections.first, tasksAndSections.second, events, habits, widgets)
    }

    private val usageAndProgressFlow = combine(
        db.appUsageDao().observeAllLogs(),
        db.dailyProgressDao().observeAllLogs()
    ) { usage, dailyProgress ->
        usage to dailyProgress
    }

    private val wellnessAndAiFlow = combine(
        db.aiMessageDao().observeMessages(),
        db.cycleLogDao().observeAllLogs(),
        db.dailyCheckInDao().observeAllCheckIns(),
        db.suruMemoryDao().observeAllMemories(),
        db.suruStudyPlanDao().observeAllPlans()
    ) { aiMessages, cycleLogs, checkIns, memories, plans ->
        listOf(aiMessages, cycleLogs, checkIns, memories, plans)
    }

    private val studyDataFlow = combine(
        db.subjectDao().observeAll(),
        db.studyChapterDao().observeAll(),
        db.studySessionDao().observeAll(),
        db.studyResourceDao().observeAll()
    ) { subjects, chapters, sessions, resources ->
        listOf(subjects, chapters, sessions, resources)
    }

    private data class StudyBundle(
        val subjects: List<SubjectEntity>,
        val chapters: List<StudyChapterEntity>,
        val sessions: List<StudySessionEntity>,
        val resources: List<StudyResourceEntity>
    )

    private val notificationsAiStudyFlow = combine(
        db.notificationHistoryDao().observeAll(),
        db.aiProviderUsageDao().observeAll(),
        studyDataFlow
    ) { notifHist, aiUsage, studyPacked ->
        @Suppress("UNCHECKED_CAST")
        Triple(
            notifHist,
            aiUsage,
            StudyBundle(
                subjects = studyPacked[0] as List<SubjectEntity>,
                chapters = studyPacked[1] as List<StudyChapterEntity>,
                sessions = studyPacked[2] as List<StudySessionEntity>,
                resources = studyPacked[3] as List<StudyResourceEntity>
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private val secondaryFlow = combine(
        db.quickNoteDao().observeNote(),
        usageAndProgressFlow,
        db.challengeDao().observeAllChallenges(),
        db.friendDao().observeAllFriends(),
        wellnessAndAiFlow
    ) { note, usagePair, challenges, friends, packed ->
        SecondaryFlows(
            note = note,
            usage = usagePair.first,
            dailyProgress = usagePair.second,
            challenges = challenges,
            friends = friends,
            aiMessages = packed[0] as List<AiMessageEntity>,
            cycleLogs = packed[1] as List<CycleLogEntity>,
            dailyCheckIns = packed[2] as List<DailyCheckInEntity>,
            suruMemories = packed[3] as List<SuruMemoryEntity>,
            suruStudyPlans = packed[4] as List<SuruStudyPlanEntity>
        )
    }

    private val feedbackBannersFlow = combine(
        petReactionState,
        levelUpBannerState,
        kuttaEventState
    ) { petReact, lvlBanner, kuttaOverride ->
        Triple(petReact, lvlBanner, kuttaOverride)
    }

    private val transientFlow = combine(
        studyTimerState,
        aiLoadingState,
        aiErrorState,
        feedbackBannersFlow,
        snackbarState
    ) { timer, loading, err, banners, snack ->
        TransientFlows(timer, loading, err, banners.first, banners.second, snack, banners.third)
    }

    private val suruVoiceFlow = combine(
        suruVoiceManager.voiceState,
        suruVoiceManager.partialTranscript,
        suruVoiceManager.voiceError
    ) { vState, partial, vErr ->
        Triple(vState, partial, vErr)
    }

    private val suruExtrasFlow = combine(
        suruLastUndoState,
        suruStudyHelpState,
        pendingSuruImageState
    ) { undo, help, pendingImg ->
        Triple(undo, help, pendingImg)
    }

    private val suruFlow = combine(
        suruVoiceFlow,
        suruSuccessOverride,
        suruPendingConfirmationState,
        suruExtrasFlow
    ) { voiceTriple, success, confirm, extras ->
        SuruUiTransientFlows(
            voiceState = voiceTriple.first,
            partialTranscript = voiceTriple.second,
            voiceError = voiceTriple.third,
            successOverride = success,
            confirmation = confirm,
            undoRecord = extras.first,
            studyHelp = extras.second,
            pendingImage = extras.third
        )
    }

    private val networkBasicFlow = combine(
        friendSyncManager.observeNetworkOnline(),
        friendSyncManager.isPeerRadarActive,
        friendSyncManager.nearbyPeers,
        usagePermissionTick,
        appPermissionsState
    ) { online, radar, peers, tick, perms ->
        listOf(online, radar, peers, tick, perms)
    }

    private val togetherExtrasFlow = combine(
        togetherRepository.state,
        authUiStateFlow,
        activeChatFriendIdState,
        wellbeingRangeState,
        wellbeingReportState
    ) { tState, authUi, chatFriendId, wRange, wReport ->
        listOf(tState, authUi, chatFriendId, wRange, wReport)
    }

    private val socialNetworkFlow = combine(
        networkBasicFlow,
        togetherExtrasFlow,
        notificationsAiStudyFlow
    ) { netBasic, tExtras, histAiStudy ->
        @Suppress("UNCHECKED_CAST")
        SocialNetworkFlows(
            isOnline = netBasic[0] as Boolean,
            isPeerRadarActive = netBasic[1] as Boolean,
            nearbyPeers = netBasic[2] as List<NearbyFriendPeer>,
            usageTick = netBasic[3] as Int,
            permissions = netBasic[4] as List<AppPermissionInfo>,
            togetherState = tExtras[0] as TogetherState,
            authUiState = tExtras[1] as AuthUiState,
            activeChatFriendId = tExtras[2] as String?,
            wellbeingRange = tExtras[3] as WellbeingRange,
            wellbeingReport = tExtras[4] as UsageTelemetryReport,
            notificationHistory = histAiStudy.first,
            aiUsageLogs = histAiStudy.second
        ) to histAiStudy.third
    }

    val uiState: StateFlow<LifeOsUiState> = combine(
        coreFlow,
        secondaryFlow,
        transientFlow,
        socialNetworkFlow,
        suruFlow
    ) { core, sec, trans, socialPair, suru ->
        val social = socialPair.first
        val studyBundle = socialPair.second
        val completedChal = sec.challenges.count { it.isClaimed || it.currentProgress >= it.targetCount }
        val totalChal = sec.challenges.size.coerceAtLeast(1)
        friendSyncManager.updateOutgoingToken(
            FriendSyncCodec.buildShareToken(core.prefs, completedChal, totalChal)
        )
        suruVoiceManager.updateSettings(
            muted = core.prefs.suruVoiceMuted,
            speechRate = core.prefs.suruSpeechSpeed,
            language = SuruVoiceLanguage.fromStored(core.prefs.suruVoiceLanguage)
        )
        val hasUsagePerm = usageBridge.hasSystemUsagePermission()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val mascotState = when {
            suru.voiceState == SuruVoiceState.LISTENING -> SuruMascotState.LISTENING
            suru.voiceState == SuruVoiceState.PROCESSING -> SuruMascotState.THINKING
            suru.voiceState == SuruVoiceState.SPEAKING -> SuruMascotState.SPEAKING
            trans.aiLoading -> if (suru.confirmation != null) SuruMascotState.WORKING else SuruMascotState.THINKING
            trans.aiError != null || suru.voiceState == SuruVoiceState.FAILED -> SuruMascotState.ERROR
            suru.successOverride -> SuruMascotState.SUCCESS
            suru.confirmation != null -> SuruMascotState.WORKING
            hour >= 23 || hour < 5 -> SuruMascotState.SLEEPING
            else -> SuruMascotState.IDLE
        }

        val cloudStatus = suruService.getCloudAiStatus()
        LifeOsUiState(
            isPrefsLoaded = true,
            prefs = core.prefs,
            taskSections = core.taskSections,
            tasks = core.tasks,
            events = core.events,
            habits = core.habits,
            widgets = core.widgets,
            quickNote = sec.note?.content ?: "",
            usageLogs = sec.usage,
            wellbeingRange = social.wellbeingRange,
            wellbeingReport = social.wellbeingReport,
            dailyProgressLogs = sec.dailyProgress,
            challenges = sec.challenges,
            friends = sec.friends,
            aiMessages = sec.aiMessages,
            cycleLogs = sec.cycleLogs,
            dailyCheckIns = sec.dailyCheckIns,
            kuttaEventOverride = trans.kuttaOverride,
            studyTimer = trans.timer,
            isAiLoading = trans.aiLoading,
            aiErrorMessage = trans.aiError,
            petReactionBanner = trans.petReaction,
            levelUpBanner = trans.levelUpBanner,
            statusSnackbar = trans.snackbar,
            hasUsageStatsPermission = hasUsagePerm,
            usageStatsStatusLabel = usageBridge.getSourceStatusLabel(),
            isOnline = social.isOnline,
            isCloudAiAvailable = cloudStatus.isCloudConfigured,
            cloudAiStatus = cloudStatus,
            aiProviderDiagnostics = suruService.getGateway().getAllDiagnostics(),
            aiProviderUsageLogs = social.aiUsageLogs,
            notificationHistory = social.notificationHistory,
            isPeerRadarActive = social.isPeerRadarActive,
            nearbyPeers = social.nearbyPeers,
            suruMemories = sec.suruMemories,
            suruStudyPlans = sec.suruStudyPlans,
            suruVoiceState = suru.voiceState,
            suruPartialTranscript = suru.partialTranscript,
            suruVoiceError = suru.voiceError,
            suruMascotState = mascotState,
            suruPendingConfirmation = suru.confirmation,
            suruLastUndo = suru.undoRecord,
            suruActiveStudyHelp = suru.studyHelp,
            pendingSuruImage = suru.pendingImage,
            appPermissions = social.permissions,
            authUiState = social.authUiState,
            togetherState = social.togetherState,
            activeChatFriendId = social.activeChatFriendId,
            studySubjects = studyBundle.subjects,
            studyChapters = studyBundle.chapters,
            studySessions = studyBundle.sessions,
            studyResources = studyBundle.resources
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LifeOsUiState()
    )

    init {
        soundManager.preload()
        viewModelScope.launch {
            LifeOsSeeder.seedInitialDataIfNeeded(db)
            prefsRepo.recordLoginDay()
            syncSystemUsageStatsIfGranted(showToast = false)
            refreshSystemPermissions()
            LifeOsWidgetUpdater.updateAllWidgets(getApplication())
            val p = prefsRepo.preferencesFlow.first()
            TaskReminderScheduler.syncSchedule(
                context = getApplication(),
                notificationsEnabled = p.notificationsEnabled,
                taskRemindersEnabled = p.taskRemindersEnabled,
                frequencyHours = p.reminderFrequencyHours
            )
            TaskReminderScheduler.syncDailyBriefings(
                context = getApplication(),
                notificationsEnabled = p.notificationsEnabled,
                morningEnabled = p.suruMorningBriefingEnabled,
                eveningEnabled = p.suruEveningReflectionEnabled
            )
            tryAutoRefreshSupabaseSession()
            onAppForegrounded()
        }
    }

    override fun onCleared() {
        super.onCleared()
        friendSyncManager.stopNearbyPeerSync()
        togetherRepository.stopRealtimeSync()
        soundManager.release()
        suruVoiceManager.release()
    }

    fun setAppInForeground(inForeground: Boolean) {
        soundManager.setAppInForeground(inForeground)
        if (inForeground) {
            onAppForegrounded()
            refreshSystemPermissions()
        }
    }

    private fun emitKuttaEvent(event: KuttaEvent) {
        val nextVisual = KuttaEventHandler.stateForEvent(event)
        kuttaEventState.value = nextVisual
        kuttaEventJob?.cancel()
        if (nextVisual.isTransientOverride) {
            kuttaEventJob = viewModelScope.launch {
                delay(KuttaEventHandler.durationMillisFor(event))
                kuttaEventState.value = null
            }
        }
    }

    fun onAppForegrounded() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val current = uiState.value
        when {
            hour in 5..11 && !hasTriggeredMorningToday -> {
                hasTriggeredMorningToday = true
                emitKuttaEvent(KuttaEvent.AppOpenedMorning)
            }
            hour >= 22 || hour < 5 -> {
                emitKuttaEvent(KuttaEvent.NightRest)
            }
            current.prefs.cycleCareEnabled && current.cycleSummary.isPeriodApproaching && !hasTriggeredCycleApproachToday -> {
                hasTriggeredCycleApproachToday = true
                emitKuttaEvent(KuttaEvent.CyclePeriodApproaching(current.cycleSummary.daysUntilNextPeriod))
            }
        }
    }

    private fun syncHomeWidgets() {
        viewModelScope.launch {
            LifeOsWidgetUpdater.updateAllWidgets(getApplication())
        }
    }

    private suspend fun recordDailyProgressDelta(
        focusDelta: Int = 0,
        xpDelta: Int = 0,
        tasksCompletedDelta: Int = 0,
        distractingDelta: Int = 0
    ) {
        val today = DateTimeUtils.todayEpochDay()
        val existing = db.dailyProgressDao().getForDay(today) ?: DailyProgressLogEntity(epochDay = today)
        db.dailyProgressDao().upsert(
            existing.copy(
                focusMinutes = (existing.focusMinutes + focusDelta).coerceAtLeast(0),
                xpEarned = (existing.xpEarned + xpDelta).coerceAtLeast(0),
                tasksCompleted = (existing.tasksCompleted + tasksCompletedDelta).coerceAtLeast(0),
                distractingMinutes = (existing.distractingMinutes + distractingDelta).coerceAtLeast(0)
            )
        )
    }

    fun clearSnackbar() {
        snackbarState.value = null
    }

    fun clearPetReaction() {
        petReactionState.value = null
    }

    fun clearLevelUpBanner() {
        levelUpBannerState.value = null
    }

    fun clearAiError() {
        aiErrorState.value = null
    }

    fun showMessage(message: String) {
        snackbarState.value = message
    }

    // --- ONBOARDING, SUPABASE AUTH & SESSION ---
    fun clearAuthFeedback() {
        authUiStateFlow.update { it.copy(errorMessage = null, infoMessage = null, isResetSent = false) }
    }

    fun dismissSessionExpiredPrompt() {
        authUiStateFlow.update { it.copy(sessionExpiredPrompt = false) }
    }

    private suspend fun tryAutoRefreshSupabaseSession() {
        val prefs = prefsRepo.preferencesFlow.first()
        if (!prefs.isSignedIn) return
        if (!supabaseClient.isConfigured()) {
            togetherRepository.refreshConfigurationState()
            return
        }
        if (prefs.supabaseRefreshToken.isBlank() && prefs.supabaseAccessToken.isBlank()) return

        val now = System.currentTimeMillis()
        if (prefs.supabaseSessionExpiresAt > now + 120_000L && prefs.supabaseAccessToken.isNotBlank()) {
            when (val userRes = supabaseClient.fetchCurrentUser(prefs.supabaseAccessToken)) {
                is SupabaseResult.Success -> {
                    prefsRepo.saveSupabaseSession(
                        userId = userRes.value.userId,
                        email = userRes.value.email,
                        accessToken = prefs.supabaseAccessToken,
                        refreshToken = prefs.supabaseRefreshToken,
                        expiresAtMillis = prefs.supabaseSessionExpiresAt,
                        emailVerified = userRes.value.emailVerified
                    )
                    togetherRepository.startRealtimeSync(viewModelScope, userRes.value.userId, prefs.supabaseAccessToken)
                    syncSafeProfileToTogether()
                    return
                }
                is SupabaseResult.Failure -> if (!userRes.isUnauthorized) return
            }
        }

        if (prefs.supabaseRefreshToken.isNotBlank()) {
            when (val refreshed = supabaseClient.refreshSession(prefs.supabaseRefreshToken)) {
                is SupabaseResult.Success -> {
                    val s = refreshed.value
                    prefsRepo.saveSupabaseSession(
                        userId = s.userId,
                        email = s.email,
                        accessToken = s.accessToken,
                        refreshToken = s.refreshToken.ifBlank { prefs.supabaseRefreshToken },
                        expiresAtMillis = s.expiresAtMillis,
                        emailVerified = s.emailVerified
                    )
                    togetherRepository.startRealtimeSync(viewModelScope, s.userId, s.accessToken)
                    syncSafeProfileToTogether()
                }
                is SupabaseResult.Failure -> {
                    if (refreshed.isUnauthorized) {
                        authUiStateFlow.update { it.copy(sessionExpiredPrompt = true) }
                    }
                }
            }
        }
    }

    fun signUpWithSupabase(email: String, password: String, displayName: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            authUiStateFlow.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            when (val res = supabaseClient.signUp(email, password, displayName)) {
                is SupabaseResult.Success -> {
                    val s = res.value
                    if (s.accessToken.isNotBlank()) {
                        prefsRepo.saveSupabaseSession(
                            userId = s.userId,
                            email = s.email,
                            accessToken = s.accessToken,
                            refreshToken = s.refreshToken,
                            expiresAtMillis = s.expiresAtMillis,
                            emailVerified = s.emailVerified
                        )
                        authUiStateFlow.update {
                            it.copy(
                                isLoading = false,
                                verificationEmailPending = if (!s.emailVerified) s.email else null,
                                infoMessage = if (s.emailVerified) "Account created!" else "Account created! Check ${s.email} for verification."
                            )
                        }
                        onSuccess()
                    } else {
                        authUiStateFlow.update {
                            it.copy(
                                isLoading = false,
                                verificationEmailPending = email.trim(),
                                infoMessage = "Verification link sent to ${email.trim()}. Confirm your email, then sign in or continue setup."
                            )
                        }
                    }
                }
                is SupabaseResult.Failure -> {
                    authUiStateFlow.update { it.copy(isLoading = false, errorMessage = res.message) }
                }
            }
        }
    }

    fun signInWithSupabase(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            authUiStateFlow.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            when (val res = supabaseClient.signIn(email, password)) {
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
                    if (s.displayName.isNotBlank() && uiState.value.prefs.userName.isBlank()) {
                        prefsRepo.completeOnboardingSignIn(
                            name = s.displayName,
                            identityOrEmail = s.email,
                            focusGoal = uiState.value.prefs.dailyFocusGoalMinutes,
                            themeMode = uiState.value.prefs.themeMode
                        )
                    }
                    togetherRepository.startRealtimeSync(viewModelScope, s.userId, s.accessToken)
                    syncSafeProfileToTogether()
                    authUiStateFlow.update {
                        it.copy(
                            isLoading = false,
                            sessionExpiredPrompt = false,
                            verificationEmailPending = if (!s.emailVerified) s.email else null,
                            infoMessage = "Signed in as ${s.email}"
                        )
                    }
                    onSuccess()
                }
                is SupabaseResult.Failure -> {
                    authUiStateFlow.update { it.copy(isLoading = false, errorMessage = res.message) }
                }
            }
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            authUiStateFlow.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null, isResetSent = false) }
            when (val res = supabaseClient.sendPasswordReset(email)) {
                is SupabaseResult.Success -> authUiStateFlow.update {
                    it.copy(isLoading = false, isResetSent = true, infoMessage = res.value)
                }
                is SupabaseResult.Failure -> authUiStateFlow.update {
                    it.copy(isLoading = false, errorMessage = res.message)
                }
            }
        }
    }

    fun resendVerificationEmail(email: String) {
        viewModelScope.launch {
            authUiStateFlow.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = supabaseClient.resendVerification(email)) {
                is SupabaseResult.Success -> authUiStateFlow.update {
                    it.copy(isLoading = false, infoMessage = res.value)
                }
                is SupabaseResult.Failure -> authUiStateFlow.update {
                    it.copy(isLoading = false, errorMessage = res.message)
                }
            }
        }
    }

    fun checkUsernameAvailability(username: String) {
        val clean = SupabaseAuthAndSyncClient.normalizeHandle(username)
        if (clean.length < 3) {
            authUiStateFlow.update { it.copy(usernameAvailable = null, usernameCheckBusy = false) }
            return
        }
        viewModelScope.launch {
            authUiStateFlow.update { it.copy(usernameCheckBusy = true) }
            val token = uiState.value.prefs.supabaseAccessToken
            when (val res = supabaseClient.checkUsernameAvailable(clean, token)) {
                is SupabaseResult.Success -> authUiStateFlow.update {
                    it.copy(usernameAvailable = res.value, usernameCheckBusy = false)
                }
                is SupabaseResult.Failure -> authUiStateFlow.update {
                    it.copy(usernameAvailable = null, usernameCheckBusy = false)
                }
            }
        }
    }

    fun complete7StepOnboarding(
        name: String,
        handleOrEmail: String,
        bio: String,
        preferredLanguage: String,
        themeMode: LifeOsThemeMode,
        avatarPreset: UserAvatarPreset,
        avatarPhotoUri: String,
        focusGoalMinutes: Int = 120
    ) {
        viewModelScope.launch {
            var resolvedAvatarUri = avatarPhotoUri
            val prefsNow = uiState.value.prefs
            if (avatarPhotoUri.startsWith("content://") && prefsNow.hasValidRemoteSession && supabaseClient.isConfigured()) {
                val parsedUri = runCatching { Uri.parse(avatarPhotoUri) }.getOrNull()
                if (parsedUri != null) {
                    val upload = supabaseClient.uploadImageToStorage(
                        bucket = "avatars",
                        objectPath = "${prefsNow.supabaseUserId}/avatar.jpg",
                        localUri = parsedUri,
                        accessToken = prefsNow.supabaseAccessToken
                    )
                    if (upload is SupabaseResult.Success) {
                        resolvedAvatarUri = upload.value
                    }
                }
            }
            prefsRepo.completeOnboardingSignIn(
                name = name,
                identityOrEmail = handleOrEmail,
                focusGoal = focusGoalMinutes,
                themeMode = themeMode,
                bio = bio,
                preferredLanguage = preferredLanguage,
                avatarPreset = avatarPreset,
                avatarPhotoUri = resolvedAvatarUri
            )
            prefsRepo.unlockBadge("FIRST_LAUNCH")
            val updated = prefsRepo.preferencesFlow.first()
            TaskReminderScheduler.syncSchedule(
                context = getApplication(),
                notificationsEnabled = updated.notificationsEnabled,
                taskRemindersEnabled = updated.taskRemindersEnabled,
                frequencyHours = updated.reminderFrequencyHours
            )
            if (updated.hasValidRemoteSession && supabaseClient.isConfigured()) {
                togetherRepository.startRealtimeSync(viewModelScope, updated.supabaseUserId, updated.supabaseAccessToken)
                syncSafeProfileToTogether()
            }
            emitKuttaEvent(KuttaEvent.AppOpenedMorning)
            snackbarState.value = "Welcome to FEELORA, ${updated.userName.ifBlank { name }}!"
            syncHomeWidgets()
        }
    }

    fun completeOnboarding(
        name: String,
        identityOrEmail: String,
        focusGoalMinutes: Int,
        themeMode: LifeOsThemeMode
    ) {
        if (name.isBlank()) return
        complete7StepOnboarding(
            name = name,
            handleOrEmail = identityOrEmail,
            bio = "",
            preferredLanguage = "English",
            themeMode = themeMode,
            avatarPreset = UserAvatarPreset.STAR_BUNNY,
            avatarPhotoUri = "",
            focusGoalMinutes = focusGoalMinutes
        )
    }

    fun signOutLocalSession() {
        viewModelScope.launch {
            friendSyncManager.stopNearbyPeerSync()
            togetherRepository.stopRealtimeSync()
            // Preserves all local Room user data, XP, tasks, events, habits, Cycle Care & memories
            prefsRepo.signOutSession()
            authUiStateFlow.update { AuthUiState(isCloudConfigured = supabaseClient.isConfigured()) }
            snackbarState.value = "Signed out safely. Local data & progress are preserved."
        }
    }

    private suspend fun applyXpAndPlaySound(
        xpDelta: Int,
        happinessDelta: Int,
        energyDelta: Int,
        isTaskCompletion: Boolean = false,
        isHabitCompletion: Boolean = false,
        isFocusCompletion: Boolean = false
    ) {
        val currentPrefs = uiState.value.prefs
        val prevLevel = currentPrefs.level
        val prevStreak = currentPrefs.dailyStreak
        val today = DateTimeUtils.todayEpochDay()
        val expectedNewLevel = (((currentPrefs.totalXp + xpDelta).coerceAtLeast(0)) / currentPrefs.xpPerLevel) + 1
        val expectedNewStreak = when {
            currentPrefs.lastActiveEpochDay == today -> prevStreak.coerceAtLeast(1)
            currentPrefs.lastActiveEpochDay == today - 1L -> prevStreak + 1
            else -> 1
        }

        prefsRepo.awardXpAndBoostPet(xpDelta, happinessDelta, energyDelta)
        recordDailyProgressDelta(xpDelta = xpDelta)

        when {
            expectedNewLevel > prevLevel -> {
                levelUpBannerState.value = expectedNewLevel
                soundManager.playCue(LifeOsSoundCue.LEVEL_UP, currentPrefs)
            }
            expectedNewStreak > prevStreak -> {
                soundManager.playCue(LifeOsSoundCue.STREAK_ACTIVATE, currentPrefs)
                if (expectedNewStreak >= 2) {
                    emitKuttaEvent(KuttaEvent.StreakMaintained(expectedNewStreak))
                }
            }
            isFocusCompletion -> {
                soundManager.playCue(LifeOsSoundCue.FOCUS_COMPLETE, currentPrefs)
            }
            isTaskCompletion || isHabitCompletion -> {
                soundManager.playCue(LifeOsSoundCue.TASK_COMPLETE, currentPrefs)
            }
        }
    }

    // --- TASKS ---
    fun toggleTaskCompleted(task: TaskEntity) {
        viewModelScope.launch {
            val nowCompleted = !task.isCompleted
            val shouldAwardXp = nowCompleted && !task.xpAwarded
            val updated = task.copy(
                isCompleted = nowCompleted,
                completedAtMillis = if (nowCompleted) System.currentTimeMillis() else null,
                xpAwarded = task.xpAwarded || shouldAwardXp
            )
            db.taskDao().updateTask(updated)

            if (shouldAwardXp) {
                recordDailyProgressDelta(tasksCompletedDelta = 1)
                applyXpAndPlaySound(
                    xpDelta = task.xpReward,
                    happinessDelta = 8,
                    energyDelta = 3,
                    isTaskCompletion = true
                )
                emitKuttaEvent(KuttaEvent.TaskCompleted(task.title))
                petReactionState.value = "Waah Malkin! +${task.xpReward} XP earned from \"${task.title}\"!"
                snackbarState.value = "+${task.xpReward} XP earned"
                updateChallengeProgress("Daily Momentum", 1)
                updateChallengeProgress("Weekly Velocity", task.xpReward)
                evaluateBadges()
            } else if (nowCompleted) {
                soundManager.playTaskComplete(uiState.value.prefs.soundEffectsEnabled)
                emitKuttaEvent(KuttaEvent.TaskCompleted(task.title))
                snackbarState.value = "Task completed"
            }

            // Clear reminder notification automatically if zero pending tasks remain
            val remainingPending = db.taskDao().getAllTasksOnce().count { !it.isCompleted }
            if (remainingPending == 0) {
                TaskReminderScheduler.clearReminderNotification(getApplication())
            }
            syncHomeWidgets()
        }
    }

    fun logTaskDistraction(task: TaskEntity, minutes: Int) {
        if (minutes <= 0) return
        viewModelScope.launch {
            val added = minutes.coerceIn(1, 240)
            db.taskDao().updateTask(task.copy(distractionMinutes = task.distractionMinutes + added))
            recordDailyProgressDelta(distractingDelta = added)
            prefsRepo.adjustPetMoodAndEnergy(happinessDelta = -4, energyDelta = -2)
            snackbarState.value = "Logged ${added}m distraction on \"${task.title}\""
            syncHomeWidgets()
        }
    }

    // --- TASK SECTIONS & INLINE TASKS ---
    fun addTaskSection(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val nextOrder = (uiState.value.taskSections.maxOfOrNull { it.sortOrder } ?: -1) + 1
            db.taskSectionDao().insertSection(
                TaskSectionEntity(
                    name = clean,
                    sortOrder = nextOrder
                )
            )
            snackbarState.value = "Section \"$clean\" added"
        }
    }

    fun renameTaskSection(section: TaskSectionEntity, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            db.taskSectionDao().updateSection(section.copy(name = clean))
            snackbarState.value = "Section renamed to \"$clean\""
        }
    }

    fun deleteTaskSection(section: TaskSectionEntity) {
        viewModelScope.launch {
            db.taskDao().deleteTasksBySection(section.id)
            db.taskSectionDao().deleteSection(section)
            snackbarState.value = "Section \"${section.name}\" removed"
            syncHomeWidgets()
        }
    }

    fun addInlineTask(sectionId: Long, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val section = uiState.value.taskSections.firstOrNull { it.id == sectionId }
            val inferredCategory = section?.let { TaskSectionMapper.inferCategory(it.name) } ?: TaskCategory.WORK
            db.taskDao().insertTask(
                TaskEntity(
                    sectionId = sectionId,
                    title = clean,
                    description = "",
                    priorityName = TaskPriority.MEDIUM.name,
                    categoryName = inferredCategory.name,
                    deadlineEpochDay = DateTimeUtils.todayEpochDay(),
                    recurrenceName = RecurrencePattern.NONE.name,
                    xpReward = TaskPriority.MEDIUM.xpValue
                )
            )
            soundManager.playCue(LifeOsSoundCue.TASK_CREATE, uiState.value.prefs)
            syncHomeWidgets()
        }
    }

    fun saveTask(
        existingId: Long? = null,
        sectionId: Long = 0L,
        title: String,
        description: String,
        priority: TaskPriority,
        category: TaskCategory,
        deadlineEpochDay: Long,
        recurrence: RecurrencePattern
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val existing = existingId?.let { id -> uiState.value.tasks.firstOrNull { it.id == id } }
            val entity = existing?.copy(
                sectionId = if (sectionId != 0L) sectionId else existing.sectionId,
                title = title.trim(),
                description = description.trim(),
                priorityName = priority.name,
                categoryName = category.name,
                deadlineEpochDay = deadlineEpochDay,
                recurrenceName = recurrence.name,
                xpReward = priority.xpValue,
                snoozedUntilMillis = if (existing.deadlineEpochDay != deadlineEpochDay) 0L else existing.snoozedUntilMillis,
                reminderCountToday = if (existing.deadlineEpochDay != deadlineEpochDay) 0 else existing.reminderCountToday,
                remindersDismissed = if (existing.deadlineEpochDay != deadlineEpochDay) false else existing.remindersDismissed
            ) ?: TaskEntity(
                sectionId = sectionId,
                title = title.trim(),
                description = description.trim(),
                priorityName = priority.name,
                categoryName = category.name,
                deadlineEpochDay = deadlineEpochDay,
                recurrenceName = recurrence.name,
                xpReward = priority.xpValue
            )
            db.taskDao().insertTask(entity)
            soundManager.playCue(
                if (existing == null) LifeOsSoundCue.TASK_CREATE else LifeOsSoundCue.TASK_RESCHEDULE,
                uiState.value.prefs
            )
            snackbarState.value = if (existing == null) "Task added" else "Task updated"
            syncHomeWidgets()
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            db.taskDao().deleteTask(task)
            soundManager.playCue(LifeOsSoundCue.UI_TOGGLE, uiState.value.prefs)
            snackbarState.value = "Task removed"
            val remainingPending = db.taskDao().getAllTasksOnce().count { !it.isCompleted }
            if (remainingPending == 0) {
                TaskReminderScheduler.clearReminderNotification(getApplication())
            }
            syncHomeWidgets()
        }
    }

    // --- CALENDAR ---
    fun saveCalendarEvent(
        title: String,
        notes: String,
        dateEpochDay: Long,
        startMinuteOfDay: Int,
        durationMinutes: Int,
        category: TaskCategory,
        recurrence: RecurrencePattern,
        reminderMinutesBefore: Int,
        isCountdown: Boolean
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            db.calendarEventDao().insertEvent(
                CalendarEventEntity(
                    title = title.trim(),
                    notes = notes.trim(),
                    dateEpochDay = dateEpochDay,
                    startMinuteOfDay = startMinuteOfDay,
                    durationMinutes = durationMinutes,
                    categoryName = category.name,
                    recurrenceName = recurrence.name,
                    reminderMinutesBefore = reminderMinutesBefore,
                    isCountdown = isCountdown
                )
            )
            soundManager.playCue(LifeOsSoundCue.CALENDAR_CREATE, uiState.value.prefs)
            snackbarState.value = if (isCountdown) "Countdown milestone added" else "Event scheduled"
            syncHomeWidgets()
        }
    }

    fun deleteCalendarEvent(event: CalendarEventEntity) {
        viewModelScope.launch {
            db.calendarEventDao().deleteEvent(event)
            snackbarState.value = "Event removed"
            syncHomeWidgets()
        }
    }

    // --- HABITS ---
    fun toggleHabitToday(habit: HabitEntity) {
        viewModelScope.launch {
            val today = DateTimeUtils.todayEpochDay()
            val alreadyDoneToday = habit.isCompletedOn(today)
            if (alreadyDoneToday) {
                db.habitDao().updateHabit(
                    habit.copy(
                        lastCompletedEpochDay = today - 1,
                        currentStreak = (habit.currentStreak - 1).coerceAtLeast(0)
                    )
                )
                snackbarState.value = "Habit unchecked"
            } else {
                val newStreak = if (habit.lastCompletedEpochDay == today - 1) habit.currentStreak + 1 else 1
                val shouldAwardXp = habit.xpAwardedEpochDay != today
                db.habitDao().updateHabit(
                    habit.copy(
                        lastCompletedEpochDay = today,
                        xpAwardedEpochDay = if (shouldAwardXp) today else habit.xpAwardedEpochDay,
                        currentStreak = newStreak,
                        bestStreak = maxOf(habit.bestStreak, newStreak)
                    )
                )
                if (shouldAwardXp) {
                    applyXpAndPlaySound(
                        xpDelta = habit.xpReward,
                        happinessDelta = 6,
                        energyDelta = 5,
                        isHabitCompletion = true
                    )
                    petReactionState.value = "${uiState.value.prefs.petName} loved your habit streak! +${habit.xpReward} XP"
                    snackbarState.value = "+${habit.xpReward} XP · ${newStreak}d streak"
                    updateChallengeProgress("Habit Lock-In", 1)
                    updateChallengeProgress("Weekly Velocity", habit.xpReward)
                    evaluateBadges()
                } else {
                    soundManager.playTaskComplete(uiState.value.prefs.soundEffectsEnabled)
                }
            }
            syncHomeWidgets()
        }
    }

    fun addHabit(title: String, category: TaskCategory, targetPerWeek: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            db.habitDao().insertHabit(
                HabitEntity(
                    title = title.trim(),
                    categoryName = category.name,
                    targetPerWeek = targetPerWeek.coerceIn(1, 7),
                    xpReward = 25
                )
            )
            snackbarState.value = "Habit added"
            syncHomeWidgets()
        }
    }

    // --- WIDGET CUSTOMIZATION ---
    fun toggleWidgetVisibility(widget: WidgetConfigEntity) {
        viewModelScope.launch {
            db.widgetConfigDao().updateWidget(widget.copy(isVisible = !widget.isVisible))
        }
    }

    fun moveWidget(widget: WidgetConfigEntity, delta: Int) {
        viewModelScope.launch {
            val currentList = uiState.value.widgets.sortedBy { it.sortOrder }.toMutableList()
            val index = currentList.indexOfFirst { it.widgetTypeName == widget.widgetTypeName }
            val targetIndex = index + delta
            if (index == -1 || targetIndex !in currentList.indices) return@launch
            val swapped = currentList[targetIndex]
            currentList[targetIndex] = currentList[index].copy(sortOrder = targetIndex)
            currentList[index] = swapped.copy(sortOrder = index)
            val normalized = currentList.mapIndexed { idx, item -> item.copy(sortOrder = idx) }
            db.widgetConfigDao().upsertAll(normalized)
        }
    }

    // --- QUICK NOTES ---
    fun updateQuickNote(content: String) {
        viewModelScope.launch {
            db.quickNoteDao().saveNote(
                QuickNoteEntity(id = 1, content = content, updatedAtMillis = System.currentTimeMillis())
            )
        }
    }

    // --- STUDY SUBJECTS, CHAPTERS, RESOURCES & POMODORO/CUSTOM TIMER ---
    fun saveSubject(
        existingId: Long? = null,
        name: String,
        colorHex: String = "#9274D8",
        iconName: String = "MenuBook",
        targetMinutesPerDay: Int = 60,
        weeklyGoalMinutes: Int = 300
    ) {
        val clean = name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val existing = existingId?.let { id -> uiState.value.studySubjects.firstOrNull { it.id == id } }
            val entity = existing?.copy(
                name = clean,
                colorHex = colorHex,
                iconName = iconName,
                targetMinutesPerDay = targetMinutesPerDay.coerceIn(15, 480),
                weeklyGoalMinutes = weeklyGoalMinutes.coerceIn(30, 3000)
            ) ?: SubjectEntity(
                name = clean,
                colorHex = colorHex,
                iconName = iconName,
                targetMinutesPerDay = targetMinutesPerDay.coerceIn(15, 480),
                weeklyGoalMinutes = weeklyGoalMinutes.coerceIn(30, 3000)
            )
            db.subjectDao().upsert(entity)
            soundManager.playCue(LifeOsSoundCue.TASK_CREATE, uiState.value.prefs)
            snackbarState.value = if (existing == null) "Subject \"$clean\" created" else "Updated \"$clean\""
        }
    }

    fun deleteSubject(subject: SubjectEntity) {
        viewModelScope.launch {
            db.studyChapterDao().deleteBySubjectId(subject.id)
            db.studyResourceDao().deleteBySubjectId(subject.id)
            db.subjectDao().delete(subject)
            if (studyTimerState.value.activeSubjectId == subject.id) {
                studyTimerState.value = studyTimerState.value.copy(activeSubjectId = null, activeChapterId = null)
            }
            snackbarState.value = "Subject \"${subject.name}\" removed"
        }
    }

    fun saveStudyChapter(
        existingId: Long? = null,
        subjectId: Long,
        title: String,
        notes: String = "",
        estimatedMinutes: Int = 45,
        status: StudyChapterStatus = StudyChapterStatus.PENDING
    ) {
        val clean = title.trim()
        if (clean.isBlank() || subjectId <= 0L) return
        viewModelScope.launch {
            val existing = existingId?.let { id -> uiState.value.studyChapters.firstOrNull { it.id == id } }
            val nextOrder = existing?.sortOrder ?: (uiState.value.studyChapters.count { it.subjectId == subjectId })
            val entity = existing?.copy(
                title = clean,
                notes = notes.trim(),
                estimatedMinutes = estimatedMinutes.coerceIn(10, 360),
                status = status.name,
                completedAtMillis = if (status == StudyChapterStatus.COMPLETED) existing.completedAtMillis ?: System.currentTimeMillis() else null
            ) ?: StudyChapterEntity(
                subjectId = subjectId,
                title = clean,
                notes = notes.trim(),
                estimatedMinutes = estimatedMinutes.coerceIn(10, 360),
                status = status.name,
                sortOrder = nextOrder,
                completedAtMillis = if (status == StudyChapterStatus.COMPLETED) System.currentTimeMillis() else null
            )
            db.studyChapterDao().upsert(entity)
            soundManager.playCue(LifeOsSoundCue.TASK_CREATE, uiState.value.prefs)
            snackbarState.value = if (existing == null) "Chapter \"$clean\" added" else "Chapter updated"
        }
    }

    fun toggleChapterCompleted(chapter: StudyChapterEntity) {
        viewModelScope.launch {
            val nowCompleted = !chapter.isCompleted
            val nextStatus = if (nowCompleted) StudyChapterStatus.COMPLETED else StudyChapterStatus.IN_PROGRESS
            db.studyChapterDao().update(
                chapter.copy(
                    status = nextStatus.name,
                    completedAtMillis = if (nowCompleted) System.currentTimeMillis() else null
                )
            )
            if (nowCompleted) {
                applyXpAndPlaySound(
                    xpDelta = 40,
                    happinessDelta = 8,
                    energyDelta = 2,
                    isTaskCompletion = true
                )
                soundManager.playCue(LifeOsSoundCue.CHAPTER_DONE, uiState.value.prefs)
                emitKuttaEvent(KuttaEvent.TaskCompleted(chapter.title))
                petReactionState.value = "Chapter complete, Malkin! +40 XP for \"${chapter.title}\""
                snackbarState.value = "Completed \"${chapter.title}\" · +40 XP"
                evaluateBadges()
            } else {
                soundManager.playCue(LifeOsSoundCue.UI_TOGGLE, uiState.value.prefs)
                snackbarState.value = "Marked \"${chapter.title}\" in progress"
            }
        }
    }

    fun deleteStudyChapter(chapter: StudyChapterEntity) {
        viewModelScope.launch {
            db.studyChapterDao().delete(chapter)
            if (studyTimerState.value.activeChapterId == chapter.id) {
                studyTimerState.value = studyTimerState.value.copy(activeChapterId = null)
            }
            snackbarState.value = "Chapter removed"
        }
    }

    fun saveStudyResource(
        existingId: Long? = null,
        subjectId: Long,
        chapterId: Long? = null,
        title: String,
        content: String,
        resourceType: StudyResourceType = StudyResourceType.NOTE
    ) {
        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        if (cleanTitle.isBlank() && cleanContent.isBlank()) return
        viewModelScope.launch {
            val entity = StudyResourceEntity(
                id = existingId ?: 0L,
                subjectId = subjectId,
                chapterId = chapterId,
                title = cleanTitle.ifBlank { "Study Note" },
                content = cleanContent,
                resourceType = resourceType.name,
                updatedAtMillis = System.currentTimeMillis()
            )
            db.studyResourceDao().upsert(entity)
            soundManager.playCue(LifeOsSoundCue.UI_CONFIRM, uiState.value.prefs)
            snackbarState.value = "Saved study note"
        }
    }

    fun deleteStudyResource(resource: StudyResourceEntity) {
        viewModelScope.launch {
            db.studyResourceDao().delete(resource)
            snackbarState.value = "Study note deleted"
        }
    }

    fun selectTimerDuration(minutes: Int) {
        configureStudyTimer(
            workMinutes = minutes,
            breakMinutes = studyTimerState.value.breakMinutes,
            mode = if (minutes == 25 || minutes == 50) StudySessionMode.POMODORO else StudySessionMode.CUSTOM
        )
    }

    fun configureStudyTimer(
        workMinutes: Int,
        breakMinutes: Int = 5,
        mode: StudySessionMode = StudySessionMode.POMODORO
    ) {
        timerJob?.cancel()
        val current = studyTimerState.value
        val safeWork = workMinutes.coerceIn(5, 180)
        val safeBreak = breakMinutes.coerceIn(1, 30)
        studyTimerState.value = current.copy(
            selectedMinutes = safeWork,
            breakMinutes = safeBreak,
            remainingSeconds = safeWork * 60,
            isRunning = false,
            isBreakPhase = false,
            sessionMode = mode
        )
    }

    fun selectFocusLinkedTask(taskId: Long?) {
        studyTimerState.value = studyTimerState.value.copy(linkedTaskId = taskId)
    }

    fun selectFocusSubjectAndChapter(subjectId: Long?, chapterId: Long?) {
        studyTimerState.value = studyTimerState.value.copy(
            activeSubjectId = subjectId,
            activeChapterId = chapterId
        )
    }

    fun toggleStudyBreakPhase(startBreak: Boolean) {
        timerJob?.cancel()
        val current = studyTimerState.value
        val targetMins = if (startBreak) current.breakMinutes else current.selectedMinutes
        studyTimerState.value = current.copy(
            isBreakPhase = startBreak,
            remainingSeconds = targetMins * 60,
            isRunning = false
        )
        if (startBreak) {
            soundManager.playCue(LifeOsSoundCue.STUDY_BREAK, uiState.value.prefs)
        }
    }

    fun toggleStudyTimer() {
        val current = studyTimerState.value
        if (current.isRunning) {
            timerJob?.cancel()
            studyTimerState.value = current.copy(isRunning = false)
        } else {
            studyTimerState.value = current.copy(isRunning = true)
            if (!current.isBreakPhase) {
                emitKuttaEvent(KuttaEvent.FocusStarted)
            }
            timerJob?.cancel()
            timerJob = viewModelScope.launch {
                while (studyTimerState.value.remainingSeconds > 0 && studyTimerState.value.isRunning) {
                    delay(1000L)
                    val latest = studyTimerState.value
                    if (!latest.isRunning) break
                    val nextSec = (latest.remainingSeconds - 1).coerceAtLeast(0)
                    studyTimerState.value = latest.copy(remainingSeconds = nextSec)
                    if (nextSec == 0) {
                        if (latest.isBreakPhase) {
                            soundManager.playCue(LifeOsSoundCue.STUDY_BREAK, uiState.value.prefs)
                            studyTimerState.value = latest.copy(
                                isBreakPhase = false,
                                isRunning = false,
                                remainingSeconds = latest.selectedMinutes * 60
                            )
                            snackbarState.value = "Break finished — ready for your next focus sprint!"
                        } else {
                            completeStudySession(
                                minutes = latest.selectedMinutes,
                                linkedTaskId = latest.linkedTaskId,
                                subjectId = latest.activeSubjectId,
                                chapterId = latest.activeChapterId
                            )
                        }
                        break
                    }
                }
            }
        }
    }

    fun resetStudyTimer() {
        timerJob?.cancel()
        val current = studyTimerState.value
        val targetMins = if (current.isBreakPhase) current.breakMinutes else current.selectedMinutes
        studyTimerState.value = current.copy(
            remainingSeconds = targetMins * 60,
            isRunning = false
        )
    }

    fun finishStudySessionEarly() {
        val state = studyTimerState.value
        if (state.isBreakPhase) {
            toggleStudyBreakPhase(false)
            return
        }
        val elapsedMins = ((state.selectedMinutes * 60) - state.remainingSeconds) / 60
        if (elapsedMins < 1) {
            snackbarState.value = "Run the timer for at least 1 minute to save focus"
            return
        }
        timerJob?.cancel()
        viewModelScope.launch {
            completeStudySession(
                minutes = elapsedMins,
                linkedTaskId = state.linkedTaskId,
                subjectId = state.activeSubjectId,
                chapterId = state.activeChapterId
            )
        }
    }

    fun logManualStudySession(subjectId: Long?, chapterId: Long?, minutes: Int) {
        val safeMins = minutes.coerceIn(5, 360)
        viewModelScope.launch {
            completeStudySession(
                minutes = safeMins,
                linkedTaskId = null,
                subjectId = subjectId,
                chapterId = chapterId
            )
        }
    }

    private suspend fun completeStudySession(
        minutes: Int,
        linkedTaskId: Long?,
        subjectId: Long? = null,
        chapterId: Long? = null
    ) {
        val xp = (minutes * 2).coerceIn(10, 120)
        val now = System.currentTimeMillis()
        val timerSnapshot = studyTimerState.value
        prefsRepo.addFocusMinutes(minutes)
        recordDailyProgressDelta(focusDelta = minutes)
        db.studySessionDao().insert(
            StudySessionEntity(
                subjectId = subjectId,
                chapterId = chapterId,
                plannedMinutes = timerSnapshot.selectedMinutes,
                actualMinutes = minutes,
                breakMinutes = timerSnapshot.breakMinutes,
                sessionMode = timerSnapshot.sessionMode.name,
                completed = true,
                startedAtMillis = now - (minutes * 60_000L),
                endedAtMillis = now,
                dateEpochDay = DateTimeUtils.todayEpochDay()
            )
        )
        if (linkedTaskId != null) {
            val task = db.taskDao().getAllTasksOnce().firstOrNull { it.id == linkedTaskId }
            if (task != null) {
                db.taskDao().updateTask(task.copy(focusMinutesSpent = task.focusMinutesSpent + minutes))
            }
        }
        applyXpAndPlaySound(
            xpDelta = xp,
            happinessDelta = 8,
            energyDelta = -4,
            isFocusCompletion = true
        )
        emitKuttaEvent(KuttaEvent.FocusCompleted(minutes))
        updateChallengeProgress("Deep Focus Sprint", minutes)
        updateChallengeProgress("Weekly Velocity", xp)
        evaluateBadges()
        petReactionState.value = "OYYYY Malkin! ${minutes}m focus done · +$xp XP!"
        snackbarState.value = "Saved ${minutes}m focus · +$xp XP"
        val current = studyTimerState.value
        studyTimerState.value = current.copy(
            remainingSeconds = current.selectedMinutes * 60,
            isRunning = false,
            isBreakPhase = false,
            pomodoroCyclesCompleted = current.pomodoroCyclesCompleted + 1
        )
        syncHomeWidgets()
    }

    // --- DIGITAL WELLNESS & OS USAGE STATS ---
    fun buildUsageAccessIntent(): Intent = usageBridge.openUsageAccessSettingsIntent()

    fun selectWellbeingRange(range: WellbeingRange) {
        wellbeingRangeState.value = range
        viewModelScope.launch {
            syncSystemUsageStatsIfGranted(showToast = false, range = range)
        }
    }

    fun refreshUsageStatsStatusAndSync(showToast: Boolean = true) {
        usagePermissionTick.value += 1
        viewModelScope.launch {
            syncSystemUsageStatsIfGranted(showToast = showToast, range = wellbeingRangeState.value)
        }
    }

    private suspend fun syncSystemUsageStatsIfGranted(
        showToast: Boolean,
        range: WellbeingRange = wellbeingRangeState.value
    ) {
        val report = usageBridge.queryUsageReport(range)
        wellbeingReportState.value = report

        when (report.state) {
            WellbeingTelemetryState.PERMISSION_NOT_GRANTED -> {
                if (showToast) {
                    snackbarState.value = "Grant Usage Access in Android Settings to view authentic screen time."
                }
                return
            }
            WellbeingTelemetryState.USAGE_UNAVAILABLE -> {
                if (showToast) {
                    snackbarState.value = "Usage information unavailable on this device right now."
                }
                return
            }
            WellbeingTelemetryState.NO_USAGE_RECORDED -> {
                if (showToast) {
                    snackbarState.value = "Usage Access active — no app usage recorded in this window yet."
                }
                return
            }
            WellbeingTelemetryState.RECORDED_USAGE -> Unit
        }

        if (range != WellbeingRange.DAILY) {
            if (showToast) {
                snackbarState.value = "${range.label} screen time synced (${report.apps.size} apps)"
            }
            return
        }

        val today = DateTimeUtils.todayEpochDay()
        val existingToday = db.appUsageDao().getLogsForDay(today)
        val existingByPkgOrName = existingToday.associateBy {
            it.packageName.ifBlank { it.appName }.lowercase()
        }
        var updatedCount = 0

        report.apps.forEach { entry ->
            val key = entry.packageName.ifBlank { entry.appName }.lowercase()
            val match = existingByPkgOrName[key]
                ?: existingToday.firstOrNull { it.appName.equals(entry.appName, ignoreCase = true) }
            if (match == null) {
                db.appUsageDao().insertLog(
                    AppUsageLogEntity(
                        appName = entry.appName,
                        packageName = entry.packageName,
                        categoryName = entry.category.name,
                        minutesUsed = entry.minutesUsed,
                        isDistracting = entry.isDistracting,
                        dateEpochDay = today
                    )
                )
                updatedCount++
            } else if (entry.minutesUsed != match.minutesUsed || match.packageName != entry.packageName) {
                db.appUsageDao().insertLog(
                    match.copy(
                        packageName = entry.packageName,
                        minutesUsed = maxOf(match.minutesUsed, entry.minutesUsed),
                        categoryName = entry.category.name,
                        isDistracting = entry.isDistracting
                    )
                )
                updatedCount++
            }
        }
        val todayLogs = db.appUsageDao().getLogsForDay(today)
        val totalDistractingToday = todayLogs.filter { it.isDistracting }.sumOf { it.minutesUsed }
        val existingDaily = db.dailyProgressDao().getForDay(today) ?: DailyProgressLogEntity(epochDay = today)
        db.dailyProgressDao().upsert(
            existingDaily.copy(distractingMinutes = maxOf(existingDaily.distractingMinutes, totalDistractingToday))
        )

        if (showToast) {
            snackbarState.value = if (updatedCount > 0) {
                "Synced $updatedCount app usage ${if (updatedCount == 1) "entry" else "entries"} from Android"
            } else {
                "Screen time is up to date"
            }
        }
    }

    fun addManualUsageLog(
        appName: String,
        category: AppUsageCategory,
        minutes: Int,
        isDistracting: Boolean,
        linkedTaskId: Long? = null
    ) {
        if (appName.isBlank() || minutes <= 0) return
        viewModelScope.launch {
            val clamped = minutes.coerceIn(1, 720)
            db.appUsageDao().insertLog(
                AppUsageLogEntity(
                    appName = appName.trim(),
                    categoryName = category.name,
                    minutesUsed = clamped,
                    isDistracting = isDistracting,
                    dateEpochDay = DateTimeUtils.todayEpochDay()
                )
            )
            if (isDistracting) {
                recordDailyProgressDelta(distractingDelta = clamped)
                if (linkedTaskId != null) {
                    val task = db.taskDao().getAllTasksOnce().firstOrNull { it.id == linkedTaskId }
                    if (task != null) {
                        db.taskDao().updateTask(task.copy(distractionMinutes = task.distractionMinutes + clamped))
                    }
                }
            }
            snackbarState.value = "Logged ${clamped}m for ${appName.trim()}"
        }
    }

    fun deleteUsageLog(log: AppUsageLogEntity) {
        viewModelScope.launch {
            db.appUsageDao().deleteLog(log)
        }
    }

    // --- KUTTA INTERACTIONS, WATER, CHECK-IN & CYCLE CARE ---
    fun triggerKuttaTap() {
        soundManager.playCue(LifeOsSoundCue.COMPANION_INTERACT, uiState.value.prefs)
        emitKuttaEvent(KuttaEvent.PetTapped)
    }

    fun interactWithPet(action: String) {
        viewModelScope.launch {
            soundManager.playCue(LifeOsSoundCue.COMPANION_INTERACT, uiState.value.prefs)
            when (action) {
                "TAP" -> {
                    emitKuttaEvent(KuttaEvent.PetTapped)
                    return@launch
                }
                "FEED" -> {
                    prefsRepo.adjustPetMoodAndEnergy(happinessDelta = 4, energyDelta = 12)
                    emitKuttaEvent(KuttaEvent.PetTapped)
                    petReactionState.value = "KUTTA ne snack khaya, Malkin! (+12% Energy · 0 XP)"
                }
                "PLAY" -> {
                    prefsRepo.adjustPetMoodAndEnergy(happinessDelta = 10, energyDelta = -4)
                    emitKuttaEvent(KuttaEvent.PetTapped)
                    petReactionState.value = "KUTTA super happy hai, Malkin! (+10% Mood · 0 XP)"
                }
                "REST" -> {
                    prefsRepo.adjustPetMoodAndEnergy(happinessDelta = 2, energyDelta = 18)
                    emitKuttaEvent(KuttaEvent.NightRest)
                    petReactionState.value = "KUTTA thoda rest kar raha hai, Malkin (+18% Energy)"
                }
            }
            syncHomeWidgets()
        }
    }

    fun logWaterIntake(deltaMl: Int = 250) {
        viewModelScope.launch {
            val updated = prefsRepo.logWaterMl(deltaMl)
            val goal = uiState.value.prefs.dailyWaterGoalMl
            soundManager.playCue(LifeOsSoundCue.UI_TOGGLE, uiState.value.prefs)
            emitKuttaEvent(KuttaEvent.WaterLogged(updated, goal))
            snackbarState.value = "+${deltaMl}ml logged · $updated / ${goal}ml"
            syncHomeWidgets()
        }
    }

    fun triggerWaterReminder() {
        emitKuttaEvent(KuttaEvent.WaterReminder)
        snackbarState.value = "KUTTA: Malkin, paani pee lo!"
    }

    fun updateWaterSettings(goalMl: Int, remindersEnabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateWaterSettings(goalMl, remindersEnabled)
            snackbarState.value = "Water target updated"
            syncHomeWidgets()
        }
    }

    fun submitDailyCheckIn(moodScore: Int, energyScore: Int, note: String) {
        viewModelScope.launch {
            val today = DateTimeUtils.todayEpochDay()
            db.dailyCheckInDao().upsert(
                DailyCheckInEntity(
                    epochDay = today,
                    moodScore = moodScore.coerceIn(1, 5),
                    energyScore = energyScore.coerceIn(1, 5),
                    note = note.trim()
                )
            )
            emitKuttaEvent(KuttaEvent.CheckInLogged(moodScore))
            snackbarState.value = "Daily check-in saved"
        }
    }

    fun logCyclePeriodStart(
        epochDay: Long = DateTimeUtils.todayEpochDay(),
        flowLevel: String = "Medium",
        notes: String = ""
    ) {
        viewModelScope.launch {
            db.cycleLogDao().insertLog(
                CycleLogEntity(
                    startEpochDay = epochDay,
                    flowLevel = flowLevel,
                    notes = notes.trim()
                )
            )
            emitKuttaEvent(KuttaEvent.CyclePeriodStarted)
            snackbarState.value = "Cycle start saved privately on device"
        }
    }

    fun logCyclePeriodEnd(epochDay: Long = DateTimeUtils.todayEpochDay()) {
        viewModelScope.launch {
            val latest = db.cycleLogDao().getAllLogsOnce().firstOrNull()
            if (latest == null) {
                snackbarState.value = "Log a period start date first"
                return@launch
            }
            db.cycleLogDao().updateLog(latest.copy(endEpochDay = maxOf(epochDay, latest.startEpochDay)))
            emitKuttaEvent(KuttaEvent.CyclePeriodEnded)
            snackbarState.value = "Cycle end date saved"
        }
    }

    fun toggleCycleSymptom(symptom: String) {
        viewModelScope.launch {
            val today = DateTimeUtils.todayEpochDay()
            val latest = db.cycleLogDao().getAllLogsOnce().firstOrNull()
            if (latest == null) {
                db.cycleLogDao().insertLog(
                    CycleLogEntity(
                        startEpochDay = today,
                        symptomsCsv = symptom
                    )
                )
            } else {
                val existing = latest.symptomsCsv
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toMutableSet()
                if (!existing.add(symptom)) {
                    existing.remove(symptom)
                }
                db.cycleLogDao().updateLog(latest.copy(symptomsCsv = existing.joinToString(",")))
            }
            emitKuttaEvent(KuttaEvent.CycleSymptomsLogged)
        }
    }

    fun deleteCycleLog(log: CycleLogEntity) {
        viewModelScope.launch {
            db.cycleLogDao().deleteLog(log)
            snackbarState.value = "Cycle entry removed"
        }
    }

    fun updateCycleSettings(enabled: Boolean, cycleLengthDays: Int, periodLengthDays: Int) {
        viewModelScope.launch {
            prefsRepo.updateCycleSettings(enabled, cycleLengthDays, periodLengthDays)
            snackbarState.value = "Cycle Care preferences saved"
        }
    }

    fun updateReducedMotion(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateReducedMotion(enabled)
            snackbarState.value = if (enabled) "Reduced Motion enabled" else "Full animations enabled"
        }
    }

    fun equipPetAccessory(accessory: PetAccessory) {
        viewModelScope.launch {
            if (uiState.value.prefs.level >= accessory.requiredLevel) {
                prefsRepo.equipAccessory(accessory)
                snackbarState.value = "Equipped ${accessory.label}"
                syncHomeWidgets()
            } else {
                snackbarState.value = "Unlocks at Level ${accessory.requiredLevel}"
            }
        }
    }

    fun claimChallenge(challenge: ChallengeEntity) {
        if (challenge.isClaimed || challenge.currentProgress < challenge.targetCount) return
        viewModelScope.launch {
            db.challengeDao().updateChallenge(challenge.copy(isClaimed = true))
            applyXpAndPlaySound(
                xpDelta = challenge.xpReward,
                happinessDelta = 10,
                energyDelta = 8,
                isTaskCompletion = true
            )
            petReactionState.value = "Challenge completed! +${challenge.xpReward} XP"
            snackbarState.value = "Claimed +${challenge.xpReward} XP!"
            evaluateBadges()
            syncHomeWidgets()
        }
    }

    private suspend fun updateChallengeProgress(challengeTitle: String, delta: Int) {
        val target = uiState.value.challenges.firstOrNull { it.title == challengeTitle } ?: return
        if (target.isClaimed) return
        val updatedProgress = (target.currentProgress + delta).coerceAtMost(target.targetCount)
        db.challengeDao().updateChallenge(target.copy(currentProgress = updatedProgress))
    }

    private suspend fun evaluateBadges() {
        val state = uiState.value
        if (state.completedTasksCount >= 5) {
            prefsRepo.unlockBadge("TASK_MASTER_5")
        }
        if (state.prefs.focusMinutesLoggedToday >= 60) {
            prefsRepo.unlockBadge("DEEP_FOCUS")
        }
        if (state.prefs.dailyStreak >= 3) {
            prefsRepo.unlockBadge("STREAK_3")
        }
        if (state.prefs.level >= 5) {
            prefsRepo.unlockBadge("LEVEL_5")
        }
    }

    // --- FRIENDS & ONLINE / PEER SYNC ---
    fun buildMyShareToken(): String {
        val state = uiState.value
        return FriendSyncCodec.buildShareToken(
            prefs = state.prefs,
            challengesCompleted = state.completedChallengesCount,
            challengesTotal = state.challenges.size.coerceAtLeast(1)
        )
    }

    fun buildMyInviteLink(): String {
        val state = uiState.value
        return FriendSyncCodec.buildInviteLink(
            prefs = state.prefs,
            challengesCompleted = state.completedChallengesCount,
            challengesTotal = state.challenges.size.coerceAtLeast(1)
        )
    }

    fun addFriendByUsername(name: String, handle: String, friendCode: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            val cleanName = name.trim()
            val rawHandle = handle.trim().ifEmpty { "@${cleanName.lowercase().replace(" ", "")}" }
            val cleanHandle = if (rawHandle.startsWith("@")) rawHandle else "@$rawHandle"
            val code = friendCode.trim().uppercase().ifBlank {
                UserPreferencesRepository.generateFriendCode(cleanHandle, cleanName)
            }
            val existing = db.friendDao().findByCodeOrHandle(code, cleanHandle)
            val entity = FriendEntity(
                id = existing?.id ?: 0L,
                displayName = cleanName,
                handle = cleanHandle,
                friendCode = code,
                level = existing?.level ?: 1,
                xp = existing?.xp ?: 0,
                streakDays = existing?.streakDays ?: 0,
                focusMinutesToday = existing?.focusMinutesToday ?: 0,
                challengesCompleted = existing?.challengesCompleted ?: 0,
                challengesTotal = existing?.challengesTotal ?: 4,
                petStageName = existing?.petStageName ?: "Little Spark",
                sharesStats = true,
                statusText = if (existing == null) "Awaiting progress sync" else existing.statusText
            )
            db.friendDao().insertFriend(entity)
            snackbarState.value = "$cleanName added"
        }
    }

    fun importFriendFromInput(rawInput: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val parsed = FriendSyncCodec.parseInput(rawInput)
        if (parsed == null) {
            snackbarState.value = "Invalid invite link or friend ID"
            onResult(false, "Paste a valid LIFEOS1 sync token, invite link, or LIFE-XXXX Friend ID.")
            return
        }
        viewModelScope.launch {
            val existing = db.friendDao().findByCodeOrHandle(parsed.friendCode, parsed.handle)
            val entity = parsed.toFriendEntity(existingId = existing?.id ?: 0L)
            db.friendDao().insertFriend(entity)
            val msg = if (existing != null) {
                "Updated ${entity.displayName}'s progress"
            } else {
                "Connected ${entity.displayName} (${entity.handle})"
            }
            snackbarState.value = msg
            onResult(true, msg)
        }
    }

    fun toggleNearbyPeerRadar() {
        val state = uiState.value
        if (state.isPeerRadarActive) {
            friendSyncManager.stopNearbyPeerSync()
            snackbarState.value = "Nearby Wi-Fi sync paused"
        } else if (!state.isOnline) {
            snackbarState.value = "Connect to Wi-Fi to discover nearby devices"
        } else {
            val label = state.prefs.userHandle.ifBlank { state.prefs.userName.ifBlank { "Explorer" } }
            friendSyncManager.startNearbyPeerSync(
                scope = viewModelScope,
                serviceLabel = label,
                outgoingToken = buildMyShareToken()
            )
            snackbarState.value = "Scanning Wi-Fi for nearby Life OS users..."
        }
    }

    fun stopNearbyPeerRadar() {
        friendSyncManager.stopNearbyPeerSync()
    }

    fun syncWithNearbyPeer(peer: NearbyFriendPeer) {
        viewModelScope.launch {
            val parsed = friendSyncManager.fetchFromNearbyPeer(peer)
            if (parsed == null) {
                snackbarState.value = "Could not fetch progress from ${peer.serviceName}"
                return@launch
            }
            val existing = db.friendDao().findByCodeOrHandle(parsed.friendCode, parsed.handle)
            val entity = parsed.toFriendEntity(
                existingId = existing?.id ?: 0L,
                statusNote = "Synced over Wi-Fi"
            )
            db.friendDao().insertFriend(entity)
            snackbarState.value = "Synced ${entity.displayName} (Lv.${entity.level} · ${entity.xp} XP)"
        }
    }

    fun removeFriend(friend: FriendEntity) {
        viewModelScope.launch {
            db.friendDao().deleteFriend(friend)
            snackbarState.value = "Removed ${friend.displayName}"
        }
    }

    // --- TOGETHER CLOUD SYNC, REQUESTS, CHATS, SHARED GOALS & COMPETE ---
    fun refreshTogetherData() {
        viewModelScope.launch {
            val prefs = uiState.value.prefs
            if (prefs.hasValidRemoteSession && supabaseClient.isConfigured()) {
                syncSafeProfileToTogether()
                togetherRepository.refreshAll()
            } else {
                togetherRepository.refreshConfigurationState()
            }
        }
    }

    fun syncSafeProfileToTogether() {
        viewModelScope.launch {
            val state = uiState.value
            val p = state.prefs
            if (!p.hasValidRemoteSession || !supabaseClient.isConfigured()) return@launch
            val today = DateTimeUtils.todayEpochDay()
            val doneToday = state.tasks.count {
                it.isCompleted && it.completedAtMillis?.let { ts -> DateTimeUtils.epochDayFromMillis(ts) == today } == true
            }
            togetherRepository.syncMySafeProfile(
                userId = p.supabaseUserId,
                accessToken = p.supabaseAccessToken,
                prefs = p,
                tasksCompletedToday = doneToday,
                consistencyScore = state.productivityScore,
                challengesCompleted = state.completedChallengesCount
            )
        }
    }

    fun sendTogetherFriendRequest(query: String) {
        viewModelScope.launch {
            val p = uiState.value.prefs
            when (val res = togetherRepository.sendFriendRequest(
                query = query,
                myUserId = p.supabaseUserId,
                myName = p.userName,
                myHandle = p.userHandle,
                accessToken = p.supabaseAccessToken
            )) {
                is SupabaseResult.Success -> {
                    soundManager.playCue(LifeOsSoundCue.TOGETHER_MESSAGE, p)
                    snackbarState.value = res.value
                }
                is SupabaseResult.Failure -> {
                    snackbarState.value = res.message
                }
            }
        }
    }

    fun respondToTogetherRequest(requestId: String, accept: Boolean) {
        viewModelScope.launch {
            val p = uiState.value.prefs
            val status = if (accept) "ACCEPTED" else "DECLINED"
            when (val res = togetherRepository.respondToFriendRequest(requestId, status, p.supabaseAccessToken)) {
                is SupabaseResult.Success -> {
                    soundManager.playCue(LifeOsSoundCue.UI_CONFIRM, p)
                    snackbarState.value = if (accept) "Friend request accepted!" else "Request declined"
                }
                is SupabaseResult.Failure -> snackbarState.value = res.message
            }
        }
    }

    fun blockOrRemoveTogetherFriend(friendUserId: String, block: Boolean) {
        viewModelScope.launch {
            val p = uiState.value.prefs
            when (val res = togetherRepository.blockOrRemoveFriend(p.supabaseUserId, friendUserId, block, p.supabaseAccessToken)) {
                is SupabaseResult.Success -> {
                    if (activeChatFriendIdState.value == friendUserId) {
                        activeChatFriendIdState.value = null
                    }
                    snackbarState.value = if (block) "User blocked" else "Friend removed"
                }
                is SupabaseResult.Failure -> snackbarState.value = res.message
            }
        }
    }

    fun reportTogetherUser(reportedUserId: String, reason: String) {
        viewModelScope.launch {
            val p = uiState.value.prefs
            when (val res = togetherRepository.reportUser(p.supabaseUserId, reportedUserId, reason, p.supabaseAccessToken)) {
                is SupabaseResult.Success -> snackbarState.value = res.value
                is SupabaseResult.Failure -> snackbarState.value = res.message
            }
        }
    }

    fun openTogetherChat(friendUserId: String?) {
        activeChatFriendIdState.value = friendUserId
        if (!friendUserId.isNullOrBlank()) {
            viewModelScope.launch {
                val p = uiState.value.prefs
                togetherRepository.markConversationRead(p.supabaseUserId, friendUserId, p.supabaseAccessToken)
            }
        }
    }

    fun sendTogetherChatMessage(friendUserId: String, content: String, mediaUri: Uri? = null) {
        viewModelScope.launch {
            val p = uiState.value.prefs
            soundManager.playCue(LifeOsSoundCue.TOGETHER_MESSAGE, p)
            when (val res = togetherRepository.sendMessage(
                myUserId = p.supabaseUserId,
                friendUserId = friendUserId,
                content = content,
                mediaLocalUri = mediaUri,
                accessToken = p.supabaseAccessToken
            )) {
                is SupabaseResult.Success -> Unit
                is SupabaseResult.Failure -> {
                    snackbarState.value = if (res.isConfigMissing) {
                        "Queued locally (cloud Together backend not configured)."
                    } else {
                        "Saved to offline outbox: ${res.message}"
                    }
                }
            }
        }
    }

    fun createTogetherSharedGoal(
        partnerId: String?,
        partnerName: String?,
        title: String,
        description: String,
        targetValue: Int,
        unitLabel: String
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val p = uiState.value.prefs
            when (val res = togetherRepository.createSharedGoal(
                myUserId = p.supabaseUserId,
                partnerId = partnerId,
                partnerName = partnerName,
                title = title,
                description = description,
                targetValue = targetValue,
                unitLabel = unitLabel,
                accessToken = p.supabaseAccessToken
            )) {
                is SupabaseResult.Success -> {
                    soundManager.playCue(LifeOsSoundCue.UI_CONFIRM, p)
                    snackbarState.value = "Shared goal created"
                }
                is SupabaseResult.Failure -> snackbarState.value = res.message
            }
        }
    }

    fun incrementTogetherSharedGoal(goal: TogetherSharedGoal, delta: Int = 1) {
        viewModelScope.launch {
            val p = uiState.value.prefs
            when (val res = togetherRepository.incrementSharedGoal(goal, delta, p.userName, p.supabaseUserId, p.supabaseAccessToken)) {
                is SupabaseResult.Success -> {
                    soundManager.playCue(LifeOsSoundCue.TASK_COMPLETE, p)
                }
                is SupabaseResult.Failure -> snackbarState.value = res.message
            }
        }
    }

    fun createTogetherCompetition(
        partnerId: String?,
        partnerName: String?,
        title: String,
        mode: TogetherCompetitionMode
    ) {
        viewModelScope.launch {
            val state = uiState.value
            val p = state.prefs
            val myInitial = when (mode) {
                TogetherCompetitionMode.XP_SPRINT -> p.totalXp
                TogetherCompetitionMode.TASK_FINISH -> state.completedTasksCount
                TogetherCompetitionMode.STUDY_FOCUS_RACE -> p.focusMinutesLoggedToday
                TogetherCompetitionMode.STREAK_CHALLENGE -> p.dailyStreak
                TogetherCompetitionMode.CONSISTENCY_DUEL -> state.productivityScore
            }
            when (val res = togetherRepository.createCompetition(
                myUserId = p.supabaseUserId,
                partnerId = partnerId,
                partnerName = partnerName,
                title = title,
                mode = mode,
                myInitialScore = myInitial,
                accessToken = p.supabaseAccessToken
            )) {
                is SupabaseResult.Success -> {
                    soundManager.playCue(LifeOsSoundCue.UI_CONFIRM, p)
                    snackbarState.value = "${mode.title} challenge started!"
                }
                is SupabaseResult.Failure -> snackbarState.value = res.message
            }
        }
    }

    fun reactToTogetherActivity(activityId: String, reaction: TogetherReaction) {
        viewModelScope.launch {
            val p = uiState.value.prefs
            soundManager.playCue(LifeOsSoundCue.UI_TAP, p)
            togetherRepository.reactToActivity(activityId, p.supabaseUserId, reaction, p.supabaseAccessToken)
        }
    }

    // --- SURU VOICE, ASSISTANT, PLANNER, STUDY LAB, MEMORY & UNDO ---
    private fun triggerSuruSuccessPulse() {
        suruSuccessOverride.value = true
        suruSuccessJob?.cancel()
        suruSuccessJob = viewModelScope.launch {
            delay(2800L)
            suruSuccessOverride.value = false
        }
    }

    fun refreshSystemPermissions() {
        appPermissionsState.value = PermissionCenterHelper.collectAllPermissions(getApplication())
        usagePermissionTick.value += 1
    }

    fun completePermissionSetup() {
        viewModelScope.launch {
            prefsRepo.markPermissionSetupCompleted(true)
            refreshSystemPermissions()
            soundManager.playCue(LifeOsSoundCue.UI_CONFIRM, uiState.value.prefs)
        }
    }

    fun attachSuruImageFromUri(uri: Uri, sourceLabel: String = "Photo") {
        viewModelScope.launch {
            when (val res = SuruImageProcessor.loadFromUri(getApplication(), uri, sourceLabel)) {
                is SuruImageLoadResult.Success -> {
                    pendingSuruImageState.value = res.attachment
                    soundManager.playCue(LifeOsSoundCue.UI_TOGGLE, uiState.value.prefs)
                }
                is SuruImageLoadResult.Error -> {
                    aiErrorState.value = res.message
                    soundManager.playCue(LifeOsSoundCue.SURU_ERROR, uiState.value.prefs)
                }
            }
        }
    }

    fun clearPendingSuruImage() {
        pendingSuruImageState.value = null
    }

    fun playUiCue(cue: LifeOsSoundCue) {
        soundManager.playCue(cue, uiState.value.prefs)
    }

    fun startSuruVoiceListening() {
        soundManager.playCue(LifeOsSoundCue.SURU_LISTENING, uiState.value.prefs, bypassSpeechGuard = true)
        val currentLang = SuruVoiceLanguage.fromStored(uiState.value.prefs.suruVoiceLanguage)
        suruVoiceManager.startListening(language = currentLang) { transcript ->
            sendAiPrompt(prompt = transcript, speakReply = true)
        }
    }

    fun stopSuruVoiceListening() {
        suruVoiceManager.stopListening()
    }

    fun speakSuruText(text: String) {
        suruVoiceManager.speak(text)
    }

    fun stopSuruSpeaking() {
        suruVoiceManager.stopSpeaking()
    }

    fun clearSuruVoiceError() {
        suruVoiceManager.clearError()
    }

    fun updateSuruVoiceSettings(muted: Boolean, speechSpeed: Float, language: SuruVoiceLanguage) {
        viewModelScope.launch {
            prefsRepo.updateSuruVoiceSettings(muted, speechSpeed, language.name)
            suruVoiceManager.updateSettings(muted, speechSpeed, language)
            snackbarState.value = if (muted) "SURU voice muted" else "SURU voice: ${language.label}"
        }
    }

    fun updateSuruMemoryPersonalization(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateSuruMemoryPersonalization(enabled)
            snackbarState.value = if (enabled) "SURU personalization enabled" else "SURU personalization paused"
        }
    }

    fun upsertSuruMemory(category: String, keyLabel: String, valueText: String) {
        if (keyLabel.isBlank() || valueText.isBlank()) return
        viewModelScope.launch {
            db.suruMemoryDao().upsertMemory(
                SuruMemoryEntity(
                    category = category,
                    keyLabel = keyLabel.trim(),
                    valueText = valueText.trim(),
                    isEnabled = true
                )
            )
            soundManager.playCue(LifeOsSoundCue.UI_CONFIRM, uiState.value.prefs)
            snackbarState.value = "Saved to SURU Memory"
        }
    }

    fun toggleSuruMemory(memory: SuruMemoryEntity) {
        viewModelScope.launch {
            db.suruMemoryDao().updateMemory(memory.copy(isEnabled = !memory.isEnabled))
            soundManager.playCue(LifeOsSoundCue.UI_TOGGLE, uiState.value.prefs)
        }
    }

    fun deleteSuruMemory(memory: SuruMemoryEntity) {
        viewModelScope.launch {
            db.suruMemoryDao().deleteMemory(memory)
            snackbarState.value = "Removed memory \"${memory.keyLabel}\""
        }
    }

    fun updateSuruBriefingSettings(
        morningEnabled: Boolean,
        eveningEnabled: Boolean,
        morningHour: Int = uiState.value.prefs.suruMorningHour,
        eveningHour: Int = uiState.value.prefs.suruEveningHour
    ) {
        viewModelScope.launch {
            prefsRepo.updateSuruBriefingSettings(morningEnabled, eveningEnabled, morningHour, eveningHour)
            TaskReminderScheduler.syncDailyBriefings(
                context = getApplication(),
                notificationsEnabled = uiState.value.prefs.notificationsEnabled,
                morningEnabled = morningEnabled,
                eveningEnabled = eveningEnabled
            )
            snackbarState.value = "Daily briefing preferences saved"
        }
    }

    fun sendAiPrompt(prompt: String, speakReply: Boolean = false) {
        val attachedImage = pendingSuruImageState.value
        val rawClean = prompt.trim()
        if ((rawClean.isEmpty() && attachedImage == null) || aiLoadingState.value) return
        val effectivePrompt = rawClean.ifBlank { "Analyze this attached image for my studies or tasks." }

        pendingSuruImageState.value = null
        aiErrorState.value = null
        suruVoiceManager.clearError()

        viewModelScope.launch {
            val current = uiState.value
            soundManager.playCue(LifeOsSoundCue.SURU_ACTIVATE, current.prefs)
            db.aiMessageDao().insertMessage(
                AiMessageEntity(
                    isFromUser = true,
                    text = effectivePrompt,
                    attachmentSummary = attachedImage?.summaryLabel
                )
            )
            aiLoadingState.value = true

            val today = DateTimeUtils.todayEpochDay()
            val pendingTasks = current.tasks.filter { !it.isCompleted }
            val pendingSummary = pendingTasks
                .take(8)
                .joinToString("; ") { "${it.title} (${it.priority.label})" }
            val eventsSummary = current.events
                .filter { it.dateEpochDay >= today }
                .take(6)
                .joinToString("; ") { "${it.title} at ${DateTimeUtils.formatTimeOfDay(it.startMinuteOfDay)}" }

            val snapshot = LifeContextSnapshot(
                userName = current.prefs.userName,
                level = current.prefs.level,
                totalXp = current.prefs.totalXp,
                dailyStreak = current.prefs.dailyStreak,
                pendingTasksSummary = pendingSummary,
                pendingTaskTitles = pendingTasks.map { it.title },
                completedTasksCount = current.completedTasksCount,
                upcomingEventsSummary = eventsSummary,
                screenTimeTodayMinutes = current.totalScreenMinutesToday,
                focusMinutesToday = current.prefs.focusMinutesLoggedToday,
                dailyFocusGoalMinutes = current.prefs.dailyFocusGoalMinutes,
                habitsCompletedToday = current.habits.count { it.isCompletedOn(today) },
                habitsTotal = current.habits.size,
                productivityScore = current.productivityScore
            )

            val enabledMemories = if (current.prefs.suruPersonalizationEnabled) {
                current.suruMemories.filter { it.isEnabled }
            } else {
                emptyList()
            }

            // If user explicitly cancels an active draft, reset pendingContext locally immediately
            if (pendingSuruContext != null && effectivePrompt.lowercase() in listOf("cancel", "never mind", "nevermind", "chhodo", "rehne do")) {
                pendingSuruContext = null
                val msg = "Cancelled that draft. What would you like to work on next?"
                db.aiMessageDao().insertMessage(AiMessageEntity(isFromUser = false, text = msg))
                aiLoadingState.value = false
                return@launch
            }

            // 1. Multi-Provider Cloud AI Gateway first whenever configured & online
            when (
                val cloudResponse = suruService.processUnifiedPrompt(
                    userPrompt = effectivePrompt,
                    conversationHistory = current.aiMessages,
                    imageAttachment = attachedImage,
                    context = snapshot,
                    tasks = current.tasks,
                    events = current.events,
                    studyPlans = current.suruStudyPlans,
                    enabledMemories = enabledMemories,
                    pendingContext = pendingSuruContext,
                    todayEpochDay = today,
                    prefs = current.prefs
                )
            ) {
                is SuruAiResponse.Success -> {
                    executeSuruActionAndRespond(
                        suruAction = cloudResponse.action,
                        sourcePrompt = effectivePrompt,
                        current = current,
                        today = today,
                        speakReply = speakReply
                    )
                    aiLoadingState.value = false
                    return@launch
                }
                is SuruAiResponse.Error -> {
                    soundManager.playCue(LifeOsSoundCue.SURU_ERROR, current.prefs)
                    aiErrorState.value = cloudResponse.message
                    db.aiMessageDao().insertMessage(
                        AiMessageEntity(
                            isFromUser = false,
                            text = cloudResponse.message,
                            actionBadge = cloudResponse.status.badgeLabel
                        )
                    )
                    aiLoadingState.value = false
                    return@launch
                }
                null -> Unit // Cloud is offline or missing key — proceed to deterministic offline SURU tools
            }

            // 2. Deterministic offline NLU & study planner when offline or API key unconfigured
            val offlineAction = SuruNluEngine.parseAndPlan(
                userPrompt = effectivePrompt,
                context = snapshot,
                tasks = current.tasks,
                events = current.events,
                memories = enabledMemories,
                pendingContext = pendingSuruContext,
                lastStudyDraft = lastProposedStudyDraft,
                todayEpochDay = today
            )
            if (offlineAction != null) {
                executeSuruActionAndRespond(
                    suruAction = offlineAction,
                    sourcePrompt = effectivePrompt,
                    current = current,
                    today = today,
                    speakReply = speakReply
                )
            } else {
                val honestFallback = SuruIntelligenceService.buildLocalConversationalFallback(
                    status = current.cloudAiStatus,
                    context = snapshot,
                    memories = enabledMemories
                )
                db.aiMessageDao().insertMessage(
                    AiMessageEntity(
                        isFromUser = false,
                        text = honestFallback,
                        actionBadge = current.cloudAiStatus.badgeLabel
                    )
                )
            }
            aiLoadingState.value = false
        }
    }

    private suspend fun executeSuruActionAndRespond(
        suruAction: SuruStructuredAction,
        sourcePrompt: String,
        current: LifeOsUiState,
        today: Long,
        speakReply: Boolean
    ) {
        pendingSuruContext = suruAction.updatedPendingContext
        if (suruAction.studyPlanDraft != null) {
            lastProposedStudyDraft = suruAction.studyPlanDraft
        }

        var finalReply = suruAction.reply
        var badge: String? = null

        when {
            suruAction.intent == SuruIntent.STUDY_HELP -> {
                val help = suruService.generateStudyHelp(
                    topic = suruAction.studyHelpTopic ?: sourcePrompt,
                    mode = suruAction.studyHelpMode,
                    userNotes = "",
                    prefs = current.prefs
                )
                suruStudyHelpState.value = help
                finalReply = "${help.conceptExplanation}\n\nKey Takeaways:\n" +
                    help.summaryBullets.joinToString("\n") { "• $it" }
                badge = "Study Help · ${help.mode.label}"
                soundManager.playCue(LifeOsSoundCue.SURU_SUCCESS, current.prefs)
            }

            suruAction.intent == SuruIntent.LOG_STUDY_PROGRESS -> {
                val mins = suruAction.loggedMinutes.coerceIn(10, 480)
                val xp = (mins / 2).coerceIn(10, 120)
                prefsRepo.addFocusMinutes(mins)
                recordDailyProgressDelta(focusDelta = mins)
                val query = (suruAction.subject ?: suruAction.title).orEmpty()
                if (query.isNotBlank()) {
                    val matchedTask = current.tasks.firstOrNull { !it.isCompleted && it.title.contains(query, ignoreCase = true) }
                    if (matchedTask != null) {
                        db.taskDao().updateTask(matchedTask.copy(focusMinutesSpent = matchedTask.focusMinutesSpent + mins))
                    }
                }
                applyXpAndPlaySound(
                    xpDelta = xp,
                    happinessDelta = 6,
                    energyDelta = -2,
                    isFocusCompletion = true
                )
                badge = "Logged ${DateTimeUtils.formatMinutesCompact(mins)} · +$xp XP"
                triggerSuruSuccessPulse()
            }

            suruAction.intent == SuruIntent.FIND_FREE_SLOTS -> {
                val todaySlots = CalendarIntelligence.suggestFreeSlots(today, 60, current.events)
                    .joinToString(", ") { DateTimeUtils.formatTimeOfDay(it) }
                val tomorrowSlots = CalendarIntelligence.suggestFreeSlots(today + 1, 60, current.events)
                    .joinToString(", ") { DateTimeUtils.formatTimeOfDay(it) }
                finalReply = buildString {
                    appendLine("Open 60-minute study windows on your Calendar:")
                    appendLine("• Today: ${todaySlots.ifBlank { "Tightly booked — try a 25m sprint between blocks." }}")
                    append("• Tomorrow: ${tomorrowSlots.ifBlank { "Open morning & evening slots available." }}")
                }
                badge = "Available Slots"
                soundManager.playCue(LifeOsSoundCue.SURU_SUCCESS, current.prefs)
            }

            suruAction.intent == SuruIntent.PROGRESS_SUMMARY -> {
                val w = current.weeklyAnalysis
                finalReply = buildString {
                    appendLine("Progress Summary (${current.prefs.userName.ifBlank { "You" }} · Lv.${current.prefs.level}):")
                    appendLine("• Productivity Score: ${current.productivityScore}% · Streak: ${current.prefs.dailyStreak}d")
                    appendLine("• Tasks: ${current.completedTasksCount} completed, ${current.pendingTasksCount} open")
                    appendLine("• Focus today: ${DateTimeUtils.formatMinutesCompact(current.prefs.focusMinutesLoggedToday)} | This week: ${DateTimeUtils.formatMinutesCompact(w.weeklyFocusMinutes)}")
                    append("• Most consistent habit streak & top wins are tracking smoothly.")
                }
                badge = "Progress Summary"
                soundManager.playCue(LifeOsSoundCue.SURU_SUCCESS, current.prefs)
            }

            suruAction.intent == SuruIntent.OVERDUE_CHECK -> {
                val overdue = current.tasks.filter { !it.isCompleted && it.deadlineEpochDay < today }
                if (suruAction.reply.length < 40) {
                    finalReply = if (overdue.isEmpty()) {
                        "No overdue tasks! Your top pending item is: " +
                            (current.tasks.firstOrNull { !it.isCompleted }?.title ?: "None — you're caught up!")
                    } else {
                        "You have ${overdue.size} overdue task(s): " +
                            overdue.take(4).joinToString(", ") { it.title } +
                            ". Say \"Clear my backlog\" to spread them calmly."
                    }
                }
                badge = if (overdue.isEmpty()) "On Track" else "${overdue.size} Overdue"
                soundManager.playCue(LifeOsSoundCue.SURU_SUCCESS, current.prefs)
            }

            suruAction.intent == SuruIntent.MORNING_BRIEFING -> {
                val b = current.suruMorningBriefing
                finalReply = buildString {
                    appendLine("${b.headline} — ${b.focusSummary}")
                    if (b.priorityTasks.isNotEmpty()) {
                        appendLine("Top tasks: ${b.priorityTasks.joinToString(", ")}")
                    }
                    append(b.wellnessNote)
                }
                badge = "Morning Briefing"
                soundManager.playCue(LifeOsSoundCue.SURU_SUCCESS, current.prefs)
            }

            suruAction.intent == SuruIntent.EVENING_REFLECTION -> {
                val r = current.suruEveningReflection
                finalReply = buildString {
                    appendLine("${r.headline} — ${r.focusSummary}")
                    append(r.wellnessNote)
                }
                badge = "Evening Reflection"
                soundManager.playCue(LifeOsSoundCue.SURU_SUCCESS, current.prefs)
            }

            suruAction.requiresConfirmation -> {
                when {
                    suruAction.studyPlanDraft != null -> {
                        suruPendingConfirmationState.value = SuruConfirmationRequest.StudyPlanConfirmation(
                            draft = suruAction.studyPlanDraft,
                            sourcePrompt = sourcePrompt
                        )
                    }
                    suruAction.backlogPlan != null -> {
                        suruPendingConfirmationState.value = SuruConfirmationRequest.BacklogConfirmation(
                            plan = suruAction.backlogPlan,
                            sourcePrompt = sourcePrompt
                        )
                    }
                    else -> {
                        suruPendingConfirmationState.value = SuruConfirmationRequest.ActionConfirmation(
                            title = suruAction.confirmationTitle ?: "Confirm Action",
                            message = suruAction.confirmationMessage ?: suruAction.reply,
                            action = suruAction
                        )
                    }
                }
            }

            suruAction.intent !in setOf(SuruIntent.CHAT, SuruIntent.ASK_CLARIFICATION, SuruIntent.WELLNESS_CHECK, SuruIntent.INSIGHTS) -> {
                val outcome = suruActionEngine.executeStructuredAction(suruAction, today)
                badge = outcome.actionBadge
                if (outcome.undoRecord != null) {
                    suruLastUndoState.value = outcome.undoRecord
                }
                val cue = when (suruAction.intent) {
                    SuruIntent.CREATE_TASK -> LifeOsSoundCue.TASK_CREATE
                    SuruIntent.RESCHEDULE_TASK, SuruIntent.UPDATE_TASK -> LifeOsSoundCue.TASK_RESCHEDULE
                    SuruIntent.COMPLETE_TASK -> LifeOsSoundCue.TASK_COMPLETE
                    SuruIntent.CREATE_EVENT, SuruIntent.RESCHEDULE_EVENT -> LifeOsSoundCue.CALENDAR_CREATE
                    SuruIntent.PLAN_WEEK, SuruIntent.ORGANIZE_TOMORROW -> LifeOsSoundCue.SCHEDULE_SAVED
                    else -> LifeOsSoundCue.SURU_SUCCESS
                }
                soundManager.playCue(cue, current.prefs)
                if (suruAction.intent == SuruIntent.COMPLETE_TASK) {
                    emitKuttaEvent(KuttaEvent.TaskCompleted(suruAction.title ?: "Task"))
                }
                triggerSuruSuccessPulse()
            }

            else -> {
                soundManager.playCue(LifeOsSoundCue.SURU_SUCCESS, current.prefs)
            }
        }

        prefsRepo.unlockBadge("AI_COMMANDER")
        db.aiMessageDao().insertMessage(
            AiMessageEntity(isFromUser = false, text = finalReply, actionBadge = badge)
        )
        val remainingPending = db.taskDao().getAllTasksOnce().count { !it.isCompleted }
        if (remainingPending == 0) {
            TaskReminderScheduler.clearReminderNotification(getApplication())
        }
        if (speakReply && !current.prefs.suruVoiceMuted) {
            suruVoiceManager.speak(finalReply)
        }
        syncHomeWidgets()
    }

    fun adjustPendingStudyPlanWorkload(newDailyMinutes: Int) {
        val current = suruPendingConfirmationState.value as? SuruConfirmationRequest.StudyPlanConfirmation ?: return
        val updatedDraft = SuruStudyPlanner.buildStudyPlanDraft(
            subject = current.draft.subject,
            chapters = current.draft.chapters,
            deadlineDays = current.draft.deadlineDays,
            dailyAvailableMinutes = newDailyMinutes.coerceIn(30, 360),
            preferredWindow = current.draft.preferredWindow,
            startHour = current.draft.startHour,
            startMinute = current.draft.startMinute,
            existingEvents = uiState.value.events,
            todayEpochDay = DateTimeUtils.todayEpochDay(),
            customWeights = current.draft.chapterWeights
        )
        lastProposedStudyDraft = updatedDraft
        suruPendingConfirmationState.value = current.copy(draft = updatedDraft)
        soundManager.playCue(LifeOsSoundCue.UI_TAP, uiState.value.prefs)
    }

    fun confirmPendingSuruAction() {
        val pending = suruPendingConfirmationState.value ?: return
        viewModelScope.launch {
            val today = DateTimeUtils.todayEpochDay()
            val outcome = when (pending) {
                is SuruConfirmationRequest.StudyPlanConfirmation ->
                    suruActionEngine.executeStudyPlan(pending.draft, today)
                is SuruConfirmationRequest.BacklogConfirmation ->
                    suruActionEngine.executeBacklogPlan(pending.plan, today)
                is SuruConfirmationRequest.ActionConfirmation ->
                    suruActionEngine.executeStructuredAction(pending.action, today)
            }
            suruPendingConfirmationState.value = null
            lastProposedStudyDraft = null
            if (outcome.undoRecord != null) {
                suruLastUndoState.value = outcome.undoRecord
            }
            val cue = when (pending) {
                is SuruConfirmationRequest.StudyPlanConfirmation -> LifeOsSoundCue.SURU_PLAN_COMPLETE
                is SuruConfirmationRequest.BacklogConfirmation -> LifeOsSoundCue.SCHEDULE_SAVED
                is SuruConfirmationRequest.ActionConfirmation -> LifeOsSoundCue.UI_CONFIRM
            }
            soundManager.playCue(cue, uiState.value.prefs)
            triggerSuruSuccessPulse()
            db.aiMessageDao().insertMessage(
                AiMessageEntity(
                    isFromUser = false,
                    text = outcome.feedbackMessage.ifBlank { "Confirmed and applied!" },
                    actionBadge = outcome.actionBadge
                )
            )
            snackbarState.value = outcome.feedbackMessage.ifBlank { "Changes applied" }
            syncHomeWidgets()
        }
    }

    fun dismissPendingSuruConfirmation() {
        suruPendingConfirmationState.value = null
        soundManager.playCue(LifeOsSoundCue.UI_TAP, uiState.value.prefs)
        snackbarState.value = "Draft dismissed"
    }

    fun undoLastSuruAction() {
        val record = suruLastUndoState.value ?: return
        viewModelScope.launch {
            suruActionEngine.undo(record)
            suruLastUndoState.value = null
            soundManager.playCue(LifeOsSoundCue.UI_UNDO, uiState.value.prefs)
            snackbarState.value = "Undid: ${record.label}"
            db.aiMessageDao().insertMessage(
                AiMessageEntity(
                    isFromUser = false,
                    text = "Reverted (${record.label}). Your data is back to how it was before.",
                    actionBadge = "Undone"
                )
            )
            syncHomeWidgets()
        }
    }

    fun triggerBacklogRecoveryPreview() {
        val current = uiState.value
        val plan = SuruStudyPlanner.buildBacklogRecoveryPlan(
            tasks = current.tasks,
            existingEvents = current.events,
            dailyFocusGoalMinutes = current.prefs.dailyFocusGoalMinutes,
            todayEpochDay = current.todayEpochDay
        )
        if (plan.items.isEmpty()) {
            snackbarState.value = "No pending tasks in backlog!"
            return
        }
        soundManager.playCue(LifeOsSoundCue.SURU_ACTIVATE, current.prefs)
        suruPendingConfirmationState.value = SuruConfirmationRequest.BacklogConfirmation(
            plan = plan,
            sourcePrompt = "Backlog Recovery"
        )
    }

    fun generateSuruStudyHelp(topic: String, mode: SuruStudyHelpMode, userNotes: String) {
        if (topic.isBlank() && userNotes.isBlank()) return
        viewModelScope.launch {
            aiLoadingState.value = true
            val currentPrefs = uiState.value.prefs
            val result = suruService.generateStudyHelp(
                topic = topic.ifBlank { "Study Notes" },
                mode = mode,
                userNotes = userNotes,
                prefs = currentPrefs
            )
            suruStudyHelpState.value = result
            soundManager.playCue(LifeOsSoundCue.SURU_SUCCESS, currentPrefs)
            aiLoadingState.value = false
        }
    }

    fun updateStudyPlanStatus(plan: SuruStudyPlanEntity, status: String) {
        viewModelScope.launch {
            db.suruStudyPlanDao().updatePlan(plan.copy(status = status))
            snackbarState.value = "Study plan marked $status"
        }
    }

    fun updateSuruAiGatewaySettings(
        defaultProvider: SuruAiProvider,
        defaultModel: String,
        autoRoutingEnabled: Boolean,
        secondaryReviewEnabled: Boolean,
        secondaryProvider: SuruAiProvider,
        secondaryModel: String,
        autoFallbackEnabled: Boolean
    ) {
        viewModelScope.launch {
            prefsRepo.updateSuruAiGatewaySettings(
                defaultProvider = defaultProvider.name,
                defaultModel = defaultModel,
                autoRoutingEnabled = autoRoutingEnabled,
                secondaryReviewEnabled = secondaryReviewEnabled,
                secondaryProvider = secondaryProvider.name,
                secondaryModel = secondaryModel,
                autoFallbackEnabled = autoFallbackEnabled
            )
            soundManager.playCue(LifeOsSoundCue.UI_CONFIRM, uiState.value.prefs)
            snackbarState.value = "SURU AI routing updated (${defaultProvider.shortName})"
        }
    }

    // --- PROFILE, THEMES & SETTINGS ---
    fun updateProfile(
        name: String,
        handle: String,
        focusGoal: Int,
        screenLimit: Int,
        avatarPreset: UserAvatarPreset? = null,
        bio: String? = null,
        preferredLanguage: String? = null,
        avatarPhotoUri: String? = null
    ) {
        viewModelScope.launch {
            var resolvedPhoto = avatarPhotoUri
            val prefsNow = uiState.value.prefs
            if (avatarPhotoUri != null && avatarPhotoUri.startsWith("content://") && prefsNow.hasValidRemoteSession && supabaseClient.isConfigured()) {
                val uri = runCatching { Uri.parse(avatarPhotoUri) }.getOrNull()
                if (uri != null) {
                    val upload = supabaseClient.uploadImageToStorage(
                        bucket = "avatars",
                        objectPath = "${prefsNow.supabaseUserId}/avatar.jpg",
                        localUri = uri,
                        accessToken = prefsNow.supabaseAccessToken
                    )
                    if (upload is SupabaseResult.Success) {
                        resolvedPhoto = upload.value
                    }
                }
            }
            prefsRepo.updateProfile(
                name = name,
                handle = handle,
                focusGoal = focusGoal,
                screenLimit = screenLimit,
                avatarPreset = avatarPreset,
                bio = bio,
                preferredLanguage = preferredLanguage,
                avatarPhotoUri = resolvedPhoto
            )
            syncSafeProfileToTogether()
            soundManager.playCue(LifeOsSoundCue.UI_CONFIRM, uiState.value.prefs)
            snackbarState.value = "Profile saved"
        }
    }

    fun updateAvatarPreset(avatarPreset: UserAvatarPreset) {
        viewModelScope.launch {
            prefsRepo.updateAvatarPreset(avatarPreset)
            syncSafeProfileToTogether()
            soundManager.playCue(LifeOsSoundCue.UI_TOGGLE, uiState.value.prefs)
            snackbarState.value = "Avatar updated to ${avatarPreset.label}"
        }
    }

    fun updateAvatarPhotoUri(uri: Uri) {
        viewModelScope.launch {
            var finalUri = uri.toString()
            val prefsNow = uiState.value.prefs
            if (prefsNow.hasValidRemoteSession && supabaseClient.isConfigured()) {
                val upload = supabaseClient.uploadImageToStorage(
                    bucket = "avatars",
                    objectPath = "${prefsNow.supabaseUserId}/avatar.jpg",
                    localUri = uri,
                    accessToken = prefsNow.supabaseAccessToken
                )
                if (upload is SupabaseResult.Success) {
                    finalUri = upload.value
                }
            }
            prefsRepo.updateAvatarPhotoUri(finalUri)
            syncSafeProfileToTogether()
            soundManager.playCue(LifeOsSoundCue.UI_CONFIRM, uiState.value.prefs)
            snackbarState.value = "Profile photo updated"
        }
    }

    fun updateThemeMode(mode: LifeOsThemeMode) {
        viewModelScope.launch {
            prefsRepo.updateThemeMode(mode)
            syncSafeProfileToTogether()
            soundManager.playCue(LifeOsSoundCue.UI_TOGGLE, uiState.value.prefs)
            snackbarState.value = "Theme switched to ${mode.displayName}"
        }
    }

    fun updatePrivacy(
        shareXp: Boolean,
        shareStreaks: Boolean,
        shareFocus: Boolean,
        shareChallenges: Boolean
    ) {
        viewModelScope.launch {
            prefsRepo.updatePrivacy(shareXp, shareStreaks, shareFocus, shareChallenges)
            syncSafeProfileToTogether()
        }
    }

    fun updateNotifications(
        enabled: Boolean,
        reminders: Boolean,
        wellnessAlerts: Boolean,
        quietHoursEnabled: Boolean = uiState.value.prefs.quietHoursEnabled,
        quietStartHour: Int = uiState.value.prefs.quietStartHour,
        quietEndHour: Int = uiState.value.prefs.quietEndHour,
        pendingTaskFollowUp: Boolean = uiState.value.prefs.pendingTaskFollowUpEnabled,
        companionAlerts: Boolean = uiState.value.prefs.companionAlertsEnabled,
        achievementAlerts: Boolean = uiState.value.prefs.achievementAlertsEnabled,
        togetherAlerts: Boolean = uiState.value.prefs.togetherAlertsEnabled
    ) {
        viewModelScope.launch {
            val freq = uiState.value.prefs.reminderFrequencyHours
            prefsRepo.updateNotifications(
                enabled = enabled,
                reminders = reminders,
                wellnessAlerts = wellnessAlerts,
                quietHoursEnabled = quietHoursEnabled,
                quietStartHour = quietStartHour,
                quietEndHour = quietEndHour,
                pendingTaskFollowUp = pendingTaskFollowUp,
                companionAlerts = companionAlerts,
                achievementAlerts = achievementAlerts,
                togetherAlerts = togetherAlerts
            )
            TaskReminderScheduler.syncSchedule(
                context = getApplication(),
                notificationsEnabled = enabled,
                taskRemindersEnabled = reminders && pendingTaskFollowUp,
                frequencyHours = freq
            )
            soundManager.playCue(LifeOsSoundCue.UI_TOGGLE, uiState.value.prefs)
        }
    }

    fun markNotificationRead(id: Long) {
        viewModelScope.launch {
            db.notificationHistoryDao().markRead(id)
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            db.notificationHistoryDao().markAllRead()
            soundManager.playCue(LifeOsSoundCue.UI_TAP, uiState.value.prefs)
        }
    }

    fun clearNotificationHistory() {
        viewModelScope.launch {
            db.notificationHistoryDao().clearAll()
            snackbarState.value = "Notification history cleared"
        }
    }

    fun updateSmartReminderPolicy(frequencyHours: Int, maxPerDay: Int) {
        viewModelScope.launch {
            prefsRepo.updateSmartReminderPolicy(frequencyHours, maxPerDay)
            val p = uiState.value.prefs
            TaskReminderScheduler.syncSchedule(
                context = getApplication(),
                notificationsEnabled = p.notificationsEnabled,
                taskRemindersEnabled = p.taskRemindersEnabled && p.pendingTaskFollowUpEnabled,
                frequencyHours = frequencyHours
            )
            soundManager.playCue(LifeOsSoundCue.UI_TOGGLE, p)
            snackbarState.value = "Smart reminders updated (every ${frequencyHours}h, max ${maxPerDay}/day)"
        }
    }

    fun updateSoundEffects(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateSoundEffects(enabled)
            if (enabled) {
                soundManager.playTaskComplete(enabled = true, volume = uiState.value.prefs.soundVolume)
            }
            snackbarState.value = if (enabled) "Sound effects enabled" else "Sound effects muted"
        }
    }

    fun updateSoundConfiguration(
        masterEnabled: Boolean = uiState.value.prefs.soundEffectsEnabled,
        volume: Float = uiState.value.prefs.soundVolume,
        hapticsEnabled: Boolean = uiState.value.prefs.hapticsEnabled,
        navEnabled: Boolean = uiState.value.prefs.soundNavEnabled,
        interactionEnabled: Boolean = uiState.value.prefs.soundInteractionEnabled,
        taskEnabled: Boolean = uiState.value.prefs.soundTaskEnabled,
        achievementEnabled: Boolean = uiState.value.prefs.soundAchievementEnabled,
        streakEnabled: Boolean = uiState.value.prefs.soundStreakEnabled,
        suruEnabled: Boolean = uiState.value.prefs.soundSuruEnabled,
        companionEnabled: Boolean = uiState.value.prefs.soundCompanionEnabled,
        togetherEnabled: Boolean = uiState.value.prefs.soundTogetherEnabled,
        reminderEnabled: Boolean = uiState.value.prefs.soundReminderEnabled,
        notificationEnabled: Boolean = uiState.value.prefs.soundNotificationEnabled
    ) {
        viewModelScope.launch {
            prefsRepo.updateSoundConfiguration(
                masterEnabled = masterEnabled,
                volume = volume,
                hapticsEnabled = hapticsEnabled,
                navEnabled = navEnabled,
                interactionEnabled = interactionEnabled,
                taskEnabled = taskEnabled,
                calendarEnabled = reminderEnabled,
                suruEnabled = suruEnabled,
                gamificationEnabled = achievementEnabled || streakEnabled,
                companionEnabled = companionEnabled,
                achievementEnabled = achievementEnabled,
                streakEnabled = streakEnabled,
                togetherEnabled = togetherEnabled,
                reminderEnabled = reminderEnabled,
                notificationEnabled = notificationEnabled
            )
        }
    }

    fun previewSoundEffect() {
        previewSoundCategory(SoundCategory.STREAK_COMPLETION)
    }

    fun previewSoundCategory(category: SoundCategory) {
        val prefs = uiState.value.prefs
        if (!prefs.soundEffectsEnabled) {
            snackbarState.value = "Enable Sound Effects first to preview chimes"
            return
        }
        val unlockedPrefs = when (category) {
            SoundCategory.NAVIGATION -> prefs.copy(soundNavEnabled = true)
            SoundCategory.BUTTON_INTERACTION -> prefs.copy(soundInteractionEnabled = true)
            SoundCategory.TASK_COMPLETION -> prefs.copy(soundTaskEnabled = true)
            SoundCategory.ACHIEVEMENT -> prefs.copy(soundAchievementEnabled = true)
            SoundCategory.STREAK_COMPLETION -> prefs.copy(soundStreakEnabled = true)
            SoundCategory.SURU_INTERACTION -> prefs.copy(soundSuruEnabled = true)
            SoundCategory.KUTTA_INTERACTION -> prefs.copy(soundCompanionEnabled = true)
            SoundCategory.TOGETHER_MESSAGES -> prefs.copy(soundTogetherEnabled = true)
            SoundCategory.REMINDERS -> prefs.copy(soundReminderEnabled = true)
            SoundCategory.NOTIFICATIONS -> prefs.copy(soundNotificationEnabled = true)
        }
        soundManager.previewCategory(category, unlockedPrefs)
    }

    fun previewSoundCue(cue: LifeOsSoundCue) {
        previewSoundCategory(cue.category)
    }

    fun updateSplashAudioLogo(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateSplashAudioLogo(enabled)
            snackbarState.value = if (enabled) "Opening chime enabled" else "Opening chime muted"
        }
    }

    fun playAppOpenLogoCue() {
        soundManager.playCue(LifeOsSoundCue.APP_OPEN_LOGO, uiState.value.prefs, bypassSpeechGuard = true)
    }

    fun resetAllLocalData() {
        viewModelScope.launch {
            friendSyncManager.stopNearbyPeerSync()
            db.taskSectionDao().clearAll()
            db.taskDao().clearAll()
            db.calendarEventDao().clearAll()
            db.habitDao().clearAll()
            db.appUsageDao().clearAll()
            db.dailyProgressDao().clearAll()
            db.challengeDao().clearAll()
            db.friendDao().clearAll()
            db.aiMessageDao().clearAll()
            db.cycleLogDao().clearAll()
            db.dailyCheckInDao().clearAll()
            db.suruMemoryDao().clearAll()
            db.suruStudyPlanDao().clearAll()
            db.subjectDao().clearAll()
            db.studyChapterDao().clearAll()
            db.studySessionDao().clearAll()
            db.studyResourceDao().clearAll()
            db.quickNoteDao().saveNote(QuickNoteEntity(id = 1, content = ""))
            prefsRepo.resetDefaults()
            LifeOsSeeder.seedInitialDataIfNeeded(db)
            TaskReminderScheduler.syncSchedule(getApplication(), notificationsEnabled = false, taskRemindersEnabled = false)
            TaskReminderScheduler.syncDailyBriefings(getApplication(), notificationsEnabled = false, morningEnabled = false, eveningEnabled = false)
            suruPendingConfirmationState.value = null
            suruLastUndoState.value = null
            suruStudyHelpState.value = null
            pendingSuruImageState.value = null
            pendingSuruContext = null
            lastProposedStudyDraft = null
            snackbarState.value = "Workspace reset to a clean slate"
            syncHomeWidgets()
        }
    }
}
