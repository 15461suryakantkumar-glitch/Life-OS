package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiExecutionResult
import com.example.data.ai.LifeAiRepository
import com.example.data.ai.LifeContextSnapshot
import com.example.data.audio.LifeOsSoundManager
import com.example.data.local.AiMessageEntity
import com.example.data.local.AndroidUsageStatsBridge
import com.example.data.local.AppUsageCategory
import com.example.data.local.AppUsageLogEntity
import com.example.data.local.CalendarEventEntity
import com.example.data.local.ChallengeEntity
import com.example.data.local.CompanionMood
import com.example.data.local.CompanionMoodResolver
import com.example.data.local.CompanionSpecies
import com.example.data.local.DailyProgressLogEntity
import com.example.data.local.FriendEntity
import com.example.data.local.HabitEntity
import com.example.data.local.LifeOsDatabase
import com.example.data.local.LifeOsSeeder
import com.example.data.local.PetAccessory
import com.example.data.local.QuickNoteEntity
import com.example.data.local.RecurrencePattern
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import com.example.data.local.TaskSectionEntity
import com.example.data.local.TaskSectionMapper
import com.example.data.local.UserAvatarPreset
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserPreferencesState
import com.example.data.local.WidgetConfigEntity
import com.example.data.social.FriendSyncCodec
import com.example.data.social.FriendSyncManager
import com.example.data.social.NearbyFriendPeer
import com.example.notifications.TaskReminderScheduler
import com.example.ui.theme.LifeOsThemeMode
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AchievementBadge(
    val id: String,
    val title: String,
    val description: String,
    val xpBonus: Int
)

object BadgeCatalog {
    val all = listOf(
        AchievementBadge("FIRST_LAUNCH", "Ready to Go", "Set up your Life OS workspace", 0),
        AchievementBadge("STREAK_3", "3-Day Streak", "Stayed active for 3 days in a row", 75),
        AchievementBadge("TASK_MASTER_5", "Task Finisher", "Completed 5 or more tasks", 100),
        AchievementBadge("DEEP_FOCUS", "Deep Focus", "Logged 60+ minutes of focus time", 120),
        AchievementBadge("LEVEL_5", "Steady Explorer", "Reached Level 5 with your companion", 200),
        AchievementBadge("AI_COMMANDER", "AI Planner", "Ran a command with LIFE AI", 0)
    )
}

data class StudyTimerState(
    val selectedMinutes: Int = 25,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val linkedTaskId: Long? = null
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
    val dailyProgressLogs: List<DailyProgressLogEntity> = emptyList(),
    val challenges: List<ChallengeEntity> = emptyList(),
    val friends: List<FriendEntity> = emptyList(),
    val aiMessages: List<AiMessageEntity> = emptyList(),
    val studyTimer: StudyTimerState = StudyTimerState(),
    val isAiLoading: Boolean = false,
    val aiErrorMessage: String? = null,
    val petReactionBanner: String? = null,
    val levelUpBanner: Int? = null,
    val statusSnackbar: String? = null,
    val hasUsageStatsPermission: Boolean = false,
    val usageStatsStatusLabel: String = "Manual Tracking (Offline Safe)",
    val isOnline: Boolean = false,
    val isCloudAiAvailable: Boolean = false,
    val isPeerRadarActive: Boolean = false,
    val nearbyPeers: List<NearbyFriendPeer> = emptyList()
) {
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
    private val aiRepo = LifeAiRepository(application)
    private val usageBridge = AndroidUsageStatsBridge(application)
    private val friendSyncManager = FriendSyncManager(application)
    private val soundManager = LifeOsSoundManager(application)

    private val studyTimerState = MutableStateFlow(StudyTimerState())
    private val aiLoadingState = MutableStateFlow(false)
    private val aiErrorState = MutableStateFlow<String?>(null)
    private val petReactionState = MutableStateFlow<String?>(null)
    private val levelUpBannerState = MutableStateFlow<Int?>(null)
    private val snackbarState = MutableStateFlow<String?>(null)
    private val usagePermissionTick = MutableStateFlow(0)

    private var timerJob: Job? = null

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
        val aiMessages: List<AiMessageEntity>
    )

    private data class TransientFlows(
        val timer: StudyTimerState,
        val aiLoading: Boolean,
        val aiError: String?,
        val petReaction: String?,
        val levelUpBanner: Int?,
        val snackbar: String?
    )

    private data class SocialNetworkFlows(
        val isOnline: Boolean,
        val isPeerRadarActive: Boolean,
        val nearbyPeers: List<NearbyFriendPeer>,
        val usageTick: Int
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

    private val secondaryFlow = combine(
        db.quickNoteDao().observeNote(),
        usageAndProgressFlow,
        db.challengeDao().observeAllChallenges(),
        db.friendDao().observeAllFriends(),
        db.aiMessageDao().observeMessages()
    ) { note, usagePair, challenges, friends, aiMessages ->
        SecondaryFlows(note, usagePair.first, usagePair.second, challenges, friends, aiMessages)
    }

    private val feedbackBannersFlow = combine(
        petReactionState,
        levelUpBannerState
    ) { petReact, lvlBanner ->
        petReact to lvlBanner
    }

    private val transientFlow = combine(
        studyTimerState,
        aiLoadingState,
        aiErrorState,
        feedbackBannersFlow,
        snackbarState
    ) { timer, loading, err, banners, snack ->
        TransientFlows(timer, loading, err, banners.first, banners.second, snack)
    }

    private val socialNetworkFlow = combine(
        friendSyncManager.observeNetworkOnline(),
        friendSyncManager.isPeerRadarActive,
        friendSyncManager.nearbyPeers,
        usagePermissionTick
    ) { online, radar, peers, tick ->
        SocialNetworkFlows(online, radar, peers, tick)
    }

    val uiState: StateFlow<LifeOsUiState> = combine(
        coreFlow,
        secondaryFlow,
        transientFlow,
        socialNetworkFlow
    ) { core, sec, trans, social ->
        val completedChal = sec.challenges.count { it.isClaimed || it.currentProgress >= it.targetCount }
        val totalChal = sec.challenges.size.coerceAtLeast(1)
        friendSyncManager.updateOutgoingToken(
            FriendSyncCodec.buildShareToken(core.prefs, completedChal, totalChal)
        )
        val hasUsagePerm = usageBridge.hasSystemUsagePermission()
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
            dailyProgressLogs = sec.dailyProgress,
            challenges = sec.challenges,
            friends = sec.friends,
            aiMessages = sec.aiMessages,
            studyTimer = trans.timer,
            isAiLoading = trans.aiLoading,
            aiErrorMessage = trans.aiError,
            petReactionBanner = trans.petReaction,
            levelUpBanner = trans.levelUpBanner,
            statusSnackbar = trans.snackbar,
            hasUsageStatsPermission = hasUsagePerm,
            usageStatsStatusLabel = usageBridge.getSourceStatusLabel(),
            isOnline = social.isOnline,
            isCloudAiAvailable = social.isOnline && aiRepo.isCloudAiAvailable(),
            isPeerRadarActive = social.isPeerRadarActive,
            nearbyPeers = social.nearbyPeers
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
            LifeOsWidgetUpdater.updateAllWidgets(getApplication())
            val p = uiState.value.prefs
            TaskReminderScheduler.syncSchedule(
                context = getApplication(),
                notificationsEnabled = p.notificationsEnabled,
                taskRemindersEnabled = p.taskRemindersEnabled
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        friendSyncManager.stopNearbyPeerSync()
        soundManager.release()
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

    // --- ONBOARDING & COMPANION MATCH ---
    fun completeOnboarding(
        name: String,
        identityOrEmail: String,
        petName: String,
        companionSpecies: CompanionSpecies,
        focusGoalMinutes: Int,
        themeMode: LifeOsThemeMode
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            prefsRepo.completeOnboardingSignIn(
                name = name,
                identityOrEmail = identityOrEmail,
                petName = petName,
                companionSpecies = companionSpecies,
                focusGoal = focusGoalMinutes,
                themeMode = themeMode
            )
            prefsRepo.unlockBadge("FIRST_LAUNCH")
            TaskReminderScheduler.syncSchedule(
                context = getApplication(),
                notificationsEnabled = true,
                taskRemindersEnabled = true
            )
            snackbarState.value = "Welcome to Life OS, ${name.trim()}!"
            syncHomeWidgets()
        }
    }

    fun reappointCompanion(species: CompanionSpecies, customName: String?) {
        viewModelScope.launch {
            prefsRepo.appointCompanionFromQuiz(species, customName)
            val resolvedName = customName?.trim()?.takeIf { it.isNotBlank() } ?: species.defaultName
            petReactionState.value = "$resolvedName (${species.displayTitle}) is now your companion!"
            snackbarState.value = "Companion updated to $resolvedName"
            syncHomeWidgets()
        }
    }

    fun signOutLocalSession() {
        viewModelScope.launch {
            friendSyncManager.stopNearbyPeerSync()
            prefsRepo.signOutSession()
            snackbarState.value = "Signed out of local session"
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

        val sfxEnabled = currentPrefs.soundEffectsEnabled
        when {
            expectedNewLevel > prevLevel -> {
                levelUpBannerState.value = expectedNewLevel
                soundManager.playLevelUp(sfxEnabled)
            }
            expectedNewStreak > prevStreak -> {
                soundManager.playStreakActivate(sfxEnabled)
            }
            isFocusCompletion -> {
                soundManager.playFocusComplete(sfxEnabled)
            }
            isTaskCompletion || isHabitCompletion -> {
                soundManager.playTaskComplete(sfxEnabled)
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
                petReactionState.value = "${uiState.value.prefs.petName} is cheering! +${task.xpReward} XP from \"${task.title}\""
                snackbarState.value = "+${task.xpReward} XP earned"
                updateChallengeProgress("Daily Momentum", 1)
                updateChallengeProgress("Weekly Velocity", task.xpReward)
                evaluateBadges()
            } else if (nowCompleted) {
                soundManager.playTaskComplete(uiState.value.prefs.soundEffectsEnabled)
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
                xpReward = priority.xpValue
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
            snackbarState.value = if (existing == null) "Task added" else "Task updated"
            syncHomeWidgets()
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            db.taskDao().deleteTask(task)
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

    // --- STUDY TIMER ---
    fun selectTimerDuration(minutes: Int) {
        timerJob?.cancel()
        val current = studyTimerState.value
        studyTimerState.value = StudyTimerState(
            selectedMinutes = minutes,
            remainingSeconds = minutes * 60,
            isRunning = false,
            linkedTaskId = current.linkedTaskId
        )
    }

    fun selectFocusLinkedTask(taskId: Long?) {
        studyTimerState.value = studyTimerState.value.copy(linkedTaskId = taskId)
    }

    fun toggleStudyTimer() {
        val current = studyTimerState.value
        if (current.isRunning) {
            timerJob?.cancel()
            studyTimerState.value = current.copy(isRunning = false)
        } else {
            studyTimerState.value = current.copy(isRunning = true)
            timerJob?.cancel()
            timerJob = viewModelScope.launch {
                while (studyTimerState.value.remainingSeconds > 0 && studyTimerState.value.isRunning) {
                    delay(1000L)
                    val latest = studyTimerState.value
                    if (!latest.isRunning) break
                    val nextSec = (latest.remainingSeconds - 1).coerceAtLeast(0)
                    studyTimerState.value = latest.copy(remainingSeconds = nextSec)
                    if (nextSec == 0) {
                        completeStudySession(latest.selectedMinutes, latest.linkedTaskId)
                        break
                    }
                }
            }
        }
    }

    fun resetStudyTimer() {
        timerJob?.cancel()
        val current = studyTimerState.value
        studyTimerState.value = StudyTimerState(
            selectedMinutes = current.selectedMinutes,
            remainingSeconds = current.selectedMinutes * 60,
            isRunning = false,
            linkedTaskId = current.linkedTaskId
        )
    }

    fun finishStudySessionEarly() {
        val state = studyTimerState.value
        val elapsedMins = ((state.selectedMinutes * 60) - state.remainingSeconds) / 60
        if (elapsedMins < 1) {
            snackbarState.value = "Run the timer for at least 1 minute to save focus"
            return
        }
        timerJob?.cancel()
        viewModelScope.launch {
            completeStudySession(elapsedMins, state.linkedTaskId)
        }
    }

    private suspend fun completeStudySession(minutes: Int, linkedTaskId: Long?) {
        val xp = (minutes * 2).coerceIn(10, 120)
        prefsRepo.addFocusMinutes(minutes)
        recordDailyProgressDelta(focusDelta = minutes)
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
        updateChallengeProgress("Deep Focus Sprint", minutes)
        updateChallengeProgress("Weekly Velocity", xp)
        evaluateBadges()
        petReactionState.value = "Focus session saved (${minutes}m)! ${uiState.value.prefs.petName} gained +$xp XP"
        snackbarState.value = "Saved ${minutes}m focus · +$xp XP"
        val current = studyTimerState.value
        studyTimerState.value = StudyTimerState(
            selectedMinutes = current.selectedMinutes,
            remainingSeconds = current.selectedMinutes * 60,
            isRunning = false,
            linkedTaskId = current.linkedTaskId
        )
        syncHomeWidgets()
    }

    // --- DIGITAL WELLNESS & OS USAGE STATS ---
    fun buildUsageAccessIntent(): Intent = usageBridge.openUsageAccessSettingsIntent()

    fun refreshUsageStatsStatusAndSync(showToast: Boolean = true) {
        usagePermissionTick.value += 1
        viewModelScope.launch {
            syncSystemUsageStatsIfGranted(showToast = showToast)
        }
    }

    private suspend fun syncSystemUsageStatsIfGranted(showToast: Boolean) {
        if (!usageBridge.hasSystemUsagePermission()) {
            if (showToast) {
                snackbarState.value = "Grant Usage Access in Android Settings to auto-sync app screen time."
            }
            return
        }
        val systemEntries = usageBridge.queryTodayUsageStats()
        if (systemEntries.isEmpty()) {
            if (showToast) {
                snackbarState.value = "Usage Access active — no foreground app sessions over 1m today yet."
            }
            return
        }

        val today = DateTimeUtils.todayEpochDay()
        val existingToday = db.appUsageDao().getLogsForDay(today)
        val existingByName = existingToday.associateBy { it.appName.lowercase() }
        var updatedCount = 0

        systemEntries.forEach { entry ->
            val match = existingByName[entry.appName.lowercase()]
            if (match == null) {
                db.appUsageDao().insertLog(
                    AppUsageLogEntity(
                        appName = entry.appName,
                        categoryName = entry.category.name,
                        minutesUsed = entry.minutesUsed,
                        isDistracting = entry.isDistracting,
                        dateEpochDay = today
                    )
                )
                updatedCount++
            } else if (entry.minutesUsed > match.minutesUsed) {
                db.appUsageDao().insertLog(
                    match.copy(
                        minutesUsed = entry.minutesUsed,
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

    // --- DIGITAL COMPANION & CHALLENGES ---
    fun interactWithPet(action: String) {
        viewModelScope.launch {
            val currentPrefs = uiState.value.prefs
            when (action) {
                "TAP" -> {
                    val mood = uiState.value.companionMood
                    petReactionState.value = "${currentPrefs.petName} feels ${mood.label.lowercase()} — ${mood.nudgeHint}"
                    return@launch
                }
                "FEED" -> {
                    prefsRepo.adjustPetMoodAndEnergy(happinessDelta = 4, energyDelta = 12)
                    petReactionState.value = "${currentPrefs.petName} enjoyed a snack (+12% Energy)"
                }
                "PLAY" -> {
                    prefsRepo.adjustPetMoodAndEnergy(happinessDelta = 10, energyDelta = -4)
                    petReactionState.value = "${currentPrefs.petName} cheered up (+10% Mood)"
                }
                "REST" -> {
                    prefsRepo.adjustPetMoodAndEnergy(happinessDelta = 2, energyDelta = 18)
                    petReactionState.value = "${currentPrefs.petName} took a cozy rest (+18% Energy)"
                }
            }
            syncHomeWidgets()
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

    // --- LIFE AI ASSISTANT ---
    fun sendAiPrompt(prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty() || aiLoadingState.value) return
        aiErrorState.value = null

        viewModelScope.launch {
            db.aiMessageDao().insertMessage(AiMessageEntity(isFromUser = true, text = clean))
            aiLoadingState.value = true

            val current = uiState.value
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

            when (val result = aiRepo.processPrompt(clean, snapshot)) {
                is AiExecutionResult.Error -> {
                    aiErrorState.value = result.message
                }
                is AiExecutionResult.Success -> {
                    var badge: String? = null
                    when (result.actionType) {
                        "CREATE_TASK" -> {
                            val title = result.title ?: clean.take(40)
                            val matchedSection = current.taskSections.firstOrNull { sec ->
                                title.contains(sec.name, ignoreCase = true) || clean.contains(sec.name, ignoreCase = true)
                            }
                            db.taskDao().insertTask(
                                TaskEntity(
                                    sectionId = matchedSection?.id ?: 0L,
                                    title = title,
                                    priorityName = result.priority.name,
                                    categoryName = result.category.name,
                                    deadlineEpochDay = today + result.dateOffsetDays,
                                    xpReward = result.priority.xpValue
                                )
                            )
                            badge = "Task Added · $title"
                        }
                        "CREATE_HABIT" -> {
                            val title = result.title ?: clean.take(40)
                            db.habitDao().insertHabit(
                                HabitEntity(
                                    title = title,
                                    categoryName = result.category.name,
                                    targetPerWeek = 7,
                                    xpReward = 25
                                )
                            )
                            badge = "Habit Created · $title"
                        }
                        "CREATE_EVENT" -> {
                            val title = result.title ?: clean.take(40)
                            val startMin = result.startHour * 60 + result.startMinute
                            db.calendarEventDao().insertEvent(
                                CalendarEventEntity(
                                    title = title,
                                    notes = "Scheduled by LIFE AI",
                                    dateEpochDay = today + result.dateOffsetDays,
                                    startMinuteOfDay = startMin,
                                    durationMinutes = result.durationMinutes,
                                    categoryName = result.category.name
                                )
                            )
                            badge = "Scheduled · ${DateTimeUtils.formatFullDate(today + result.dateOffsetDays)} ${DateTimeUtils.formatTimeOfDay(startMin)}"
                        }
                        "ORGANIZE_TOMORROW" -> {
                            val pending = current.tasks.filter { !it.isCompleted }.take(3)
                            pending.forEachIndexed { idx, task ->
                                val startMin = 540 + idx * 120
                                db.calendarEventDao().insertEvent(
                                    CalendarEventEntity(
                                        title = task.title,
                                        notes = "Scheduled by LIFE AI",
                                        dateEpochDay = today + 1,
                                        startMinuteOfDay = startMin,
                                        durationMinutes = 60,
                                        categoryName = task.categoryName
                                    )
                                )
                            }
                            if (pending.isNotEmpty()) {
                                badge = "Tomorrow Planned · ${pending.size} Focus Blocks"
                            }
                        }
                    }
                    prefsRepo.unlockBadge("AI_COMMANDER")
                    db.aiMessageDao().insertMessage(
                        AiMessageEntity(
                            isFromUser = false,
                            text = result.reply,
                            actionBadge = badge
                        )
                    )
                    syncHomeWidgets()
                }
            }
            aiLoadingState.value = false
        }
    }

    // --- PROFILE, THEMES & SETTINGS ---
    fun updateProfile(
        name: String,
        handle: String,
        focusGoal: Int,
        screenLimit: Int,
        avatarPreset: UserAvatarPreset? = null
    ) {
        viewModelScope.launch {
            prefsRepo.updateProfile(name, handle, focusGoal, screenLimit, avatarPreset)
            snackbarState.value = "Profile saved"
        }
    }

    fun updateAvatarPreset(avatarPreset: UserAvatarPreset) {
        viewModelScope.launch {
            prefsRepo.updateAvatarPreset(avatarPreset)
            snackbarState.value = "Avatar updated to ${avatarPreset.label}"
        }
    }

    fun updatePetName(name: String) {
        viewModelScope.launch {
            prefsRepo.updatePetName(name)
            snackbarState.value = "Companion renamed"
            syncHomeWidgets()
        }
    }

    fun updateThemeMode(mode: LifeOsThemeMode) {
        viewModelScope.launch {
            prefsRepo.updateThemeMode(mode)
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
        }
    }

    fun updateNotifications(
        enabled: Boolean,
        reminders: Boolean,
        wellnessAlerts: Boolean,
        quietHoursEnabled: Boolean = uiState.value.prefs.quietHoursEnabled,
        quietStartHour: Int = uiState.value.prefs.quietStartHour,
        quietEndHour: Int = uiState.value.prefs.quietEndHour
    ) {
        viewModelScope.launch {
            prefsRepo.updateNotifications(
                enabled = enabled,
                reminders = reminders,
                wellnessAlerts = wellnessAlerts,
                quietHoursEnabled = quietHoursEnabled,
                quietStartHour = quietStartHour,
                quietEndHour = quietEndHour
            )
            TaskReminderScheduler.syncSchedule(
                context = getApplication(),
                notificationsEnabled = enabled,
                taskRemindersEnabled = reminders
            )
        }
    }

    fun updateSoundEffects(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateSoundEffects(enabled)
            if (enabled) {
                soundManager.playTaskComplete(enabled = true)
            }
            snackbarState.value = if (enabled) "Sound effects enabled" else "Sound effects muted"
        }
    }

    fun previewSoundEffect() {
        if (uiState.value.prefs.soundEffectsEnabled) {
            soundManager.playStreakActivate(enabled = true)
        } else {
            snackbarState.value = "Enable Sound Effects first to preview the chime"
        }
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
            db.quickNoteDao().saveNote(QuickNoteEntity(id = 1, content = ""))
            prefsRepo.resetDefaults()
            LifeOsSeeder.seedInitialDataIfNeeded(db)
            TaskReminderScheduler.syncSchedule(getApplication(), notificationsEnabled = false, taskRemindersEnabled = false)
            snackbarState.value = "Workspace reset to a clean slate"
            syncHomeWidgets()
        }
    }
}
