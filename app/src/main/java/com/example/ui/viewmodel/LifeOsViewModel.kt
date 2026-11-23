package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiExecutionResult
import com.example.data.ai.LifeAiRepository
import com.example.data.ai.LifeContextSnapshot
import com.example.data.local.AiMessageEntity
import com.example.data.local.AndroidUsageStatsBridge
import com.example.data.local.AppUsageCategory
import com.example.data.local.AppUsageLogEntity
import com.example.data.local.CalendarEventEntity
import com.example.data.local.ChallengeEntity
import com.example.data.local.DashboardWidgetType
import com.example.data.local.FriendEntity
import com.example.data.local.HabitEntity
import com.example.data.local.LifeOsDatabase
import com.example.data.local.LifeOsSeeder
import com.example.data.local.LocalDemoSocialBridge
import com.example.data.local.PetAccessory
import com.example.data.local.QuickNoteEntity
import com.example.data.local.RecurrencePattern
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserPreferencesState
import com.example.data.local.WidgetConfigEntity
import com.example.ui.theme.ThemeAccentPreset
import com.example.util.DateTimeUtils
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
        AchievementBadge("FIRST_LAUNCH", "Command Online", "Initialized your LIFE OS command center", 50),
        AchievementBadge("STREAK_3", "3-Day Ignition", "Maintained a 3-day active streak", 75),
        AchievementBadge("TASK_MASTER_5", "Execution Engine", "Completed 5 or more tasks", 100),
        AchievementBadge("DEEP_FOCUS", "Flow Architect", "Logged 60+ minutes of deep focus", 120),
        AchievementBadge("LEVEL_5", "Cyber Sentinel", "Reached Level 5 & evolved NOVA", 200),
        AchievementBadge("AI_COMMANDER", "Neural Sync", "Executed a command via LIFE AI", 80)
    )
}

data class StudyTimerState(
    val selectedMinutes: Int = 25,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false
)

data class LifeOsUiState(
    val prefs: UserPreferencesState = UserPreferencesState(),
    val tasks: List<TaskEntity> = emptyList(),
    val events: List<CalendarEventEntity> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val widgets: List<WidgetConfigEntity> = emptyList(),
    val quickNote: String = "",
    val usageLogs: List<AppUsageLogEntity> = emptyList(),
    val challenges: List<ChallengeEntity> = emptyList(),
    val friends: List<FriendEntity> = emptyList(),
    val aiMessages: List<AiMessageEntity> = emptyList(),
    val studyTimer: StudyTimerState = StudyTimerState(),
    val isAiLoading: Boolean = false,
    val aiErrorMessage: String? = null,
    val petReactionBanner: String? = null,
    val statusSnackbar: String? = null,
    val usageStatsStatusLabel: String = "Manual & Sample Mode (OS UsageStats Disabled)",
    val cloudSyncStatusLabel: String = "Local-First Mode · Firebase Auth & Firestore Adapter Ready"
) {
    val todayEpochDay: Long get() = DateTimeUtils.todayEpochDay()

    val todayTasks: List<TaskEntity>
        get() = tasks.filter { it.deadlineEpochDay <= todayEpochDay || !it.isCompleted }.take(8)

    val completedTasksCount: Int
        get() = tasks.count { it.isCompleted }

    val todayUsageLogs: List<AppUsageLogEntity>
        get() = usageLogs.filter { it.dateEpochDay == todayEpochDay }

    val totalScreenMinutesToday: Int
        get() = todayUsageLogs.sumOf { it.minutesUsed }

    val distractingMinutesToday: Int
        get() = todayUsageLogs.filter { it.isDistracting }.sumOf { it.minutesUsed }

    val productivityScore: Int
        get() {
            val totalTasks = tasks.size.coerceAtLeast(1)
            val taskRatio = (tasks.count { it.isCompleted }.toFloat() / totalTasks) * 40f
            val habitTotal = habits.size.coerceAtLeast(1)
            val habitRatio = (habits.count { it.isCompletedOn(todayEpochDay) }.toFloat() / habitTotal) * 30f
            val focusRatio = (prefs.focusMinutesLoggedToday.toFloat() / prefs.dailyFocusGoalMinutes.coerceAtLeast(30)).coerceAtMost(1f) * 20f
            val wellnessPenalty = if (totalScreenMinutesToday > prefs.dailyScreenLimitMinutes) 0f else 10f
            return (taskRatio + habitRatio + focusRatio + wellnessPenalty).toInt().coerceIn(12, 100)
        }
}

class LifeOsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = LifeOsDatabase.getInstance(application)
    private val prefsRepo = UserPreferencesRepository(application)
    private val aiRepo = LifeAiRepository()
    private val usageBridge = AndroidUsageStatsBridge(application)
    private val socialBridge = LocalDemoSocialBridge()

    private val studyTimerState = MutableStateFlow(StudyTimerState())
    private val aiLoadingState = MutableStateFlow(false)
    private val aiErrorState = MutableStateFlow<String?>(null)
    private val petReactionState = MutableStateFlow<String?>(null)
    private val snackbarState = MutableStateFlow<String?>(null)

    private var timerJob: Job? = null

    private data class CoreFlows(
        val prefs: UserPreferencesState,
        val tasks: List<TaskEntity>,
        val events: List<CalendarEventEntity>,
        val habits: List<HabitEntity>,
        val widgets: List<WidgetConfigEntity>
    )

    private data class SecondaryFlows(
        val note: QuickNoteEntity?,
        val usage: List<AppUsageLogEntity>,
        val challenges: List<ChallengeEntity>,
        val friends: List<FriendEntity>,
        val aiMessages: List<AiMessageEntity>
    )

    private data class TransientFlows(
        val timer: StudyTimerState,
        val aiLoading: Boolean,
        val aiError: String?,
        val petReaction: String?,
        val snackbar: String?
    )

    private val coreFlow = combine(
        prefsRepo.preferencesFlow,
        db.taskDao().observeAllTasks(),
        db.calendarEventDao().observeAllEvents(),
        db.habitDao().observeAllHabits(),
        db.widgetConfigDao().observeWidgets()
    ) { prefs, tasks, events, habits, widgets ->
        CoreFlows(prefs, tasks, events, habits, widgets)
    }

    private val secondaryFlow = combine(
        db.quickNoteDao().observeNote(),
        db.appUsageDao().observeAllLogs(),
        db.challengeDao().observeAllChallenges(),
        db.friendDao().observeAllFriends(),
        db.aiMessageDao().observeMessages()
    ) { note, usage, challenges, friends, aiMessages ->
        SecondaryFlows(note, usage, challenges, friends, aiMessages)
    }

    private val transientFlow = combine(
        studyTimerState,
        aiLoadingState,
        aiErrorState,
        petReactionState,
        snackbarState
    ) { timer, loading, err, petReact, snack ->
        TransientFlows(timer, loading, err, petReact, snack)
    }

    val uiState: StateFlow<LifeOsUiState> = combine(
        coreFlow,
        secondaryFlow,
        transientFlow
    ) { core, sec, trans ->
        LifeOsUiState(
            prefs = core.prefs,
            tasks = core.tasks,
            events = core.events,
            habits = core.habits,
            widgets = core.widgets,
            quickNote = sec.note?.content ?: "",
            usageLogs = sec.usage,
            challenges = sec.challenges,
            friends = sec.friends,
            aiMessages = sec.aiMessages,
            studyTimer = trans.timer,
            isAiLoading = trans.aiLoading,
            aiErrorMessage = trans.aiError,
            petReactionBanner = trans.petReaction,
            statusSnackbar = trans.snackbar,
            usageStatsStatusLabel = usageBridge.getSourceStatusLabel(),
            cloudSyncStatusLabel = socialBridge.syncModeLabel
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LifeOsUiState()
    )

    init {
        viewModelScope.launch {
            LifeOsSeeder.seedInitialDataIfNeeded(db)
            LifeOsWidgetUpdater.updateAllWidgets(getApplication())
        }
    }

    private fun syncHomeWidgets() {
        viewModelScope.launch {
            LifeOsWidgetUpdater.updateAllWidgets(getApplication())
        }
    }

    fun clearSnackbar() {
        snackbarState.value = null
    }

    fun clearPetReaction() {
        petReactionState.value = null
    }

    fun clearAiError() {
        aiErrorState.value = null
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
                prefsRepo.awardXpAndBoostPet(task.xpReward, happinessDelta = 7, energyDelta = 3)
                petReactionState.value = "${uiState.value.prefs.petName} gained +${task.xpReward} XP from \"${task.title}\"!"
                snackbarState.value = "+${task.xpReward} XP earned"
                updateChallengeProgress("Daily Momentum", 1)
                updateChallengeProgress("Weekly Velocity", task.xpReward)
                evaluateBadges()
            } else if (nowCompleted) {
                snackbarState.value = "Task completed (XP already claimed)"
            }
            syncHomeWidgets()
        }
    }

    fun saveTask(
        existingId: Long? = null,
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
                title = title.trim(),
                description = description.trim(),
                priorityName = priority.name,
                categoryName = category.name,
                deadlineEpochDay = deadlineEpochDay,
                recurrenceName = recurrence.name,
                xpReward = priority.xpValue
            ) ?: TaskEntity(
                title = title.trim(),
                description = description.trim(),
                priorityName = priority.name,
                categoryName = category.name,
                deadlineEpochDay = deadlineEpochDay,
                recurrenceName = recurrence.name,
                xpReward = priority.xpValue
            )
            db.taskDao().insertTask(entity)
            snackbarState.value = if (existing == null) "Task created" else "Task updated"
            syncHomeWidgets()
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            db.taskDao().deleteTask(task)
            snackbarState.value = "Task removed"
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
                    prefsRepo.awardXpAndBoostPet(habit.xpReward, happinessDelta = 5, energyDelta = 6)
                    petReactionState.value = "${uiState.value.prefs.petName} loved your habit streak! +${habit.xpReward} XP"
                    snackbarState.value = "+${habit.xpReward} XP · ${newStreak}d streak"
                    updateChallengeProgress("Habit Lock-In", 1)
                    updateChallengeProgress("Weekly Velocity", habit.xpReward)
                    evaluateBadges()
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
        studyTimerState.value = StudyTimerState(
            selectedMinutes = minutes,
            remainingSeconds = minutes * 60,
            isRunning = false
        )
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
                        completeStudySession(latest.selectedMinutes)
                        break
                    }
                }
            }
        }
    }

    fun resetStudyTimer() {
        timerJob?.cancel()
        val mins = studyTimerState.value.selectedMinutes
        studyTimerState.value = StudyTimerState(
            selectedMinutes = mins,
            remainingSeconds = mins * 60,
            isRunning = false
        )
    }

    fun finishStudySessionEarly() {
        val state = studyTimerState.value
        val elapsedMins = ((state.selectedMinutes * 60) - state.remainingSeconds) / 60
        if (elapsedMins < 1) {
            snackbarState.value = "Run timer at least 1 minute to log focus"
            return
        }
        timerJob?.cancel()
        viewModelScope.launch {
            completeStudySession(elapsedMins)
        }
    }

    private suspend fun completeStudySession(minutes: Int) {
        val xp = (minutes * 2).coerceIn(10, 120)
        prefsRepo.addFocusMinutes(minutes)
        prefsRepo.awardXpAndBoostPet(xp, happinessDelta = 8, energyDelta = -4)
        updateChallengeProgress("Deep Focus Sprint", minutes)
        updateChallengeProgress("Weekly Velocity", xp)
        evaluateBadges()
        petReactionState.value = "Focus sprint logged (${minutes}m)! ${uiState.value.prefs.petName} gained +$xp XP"
        snackbarState.value = "Logged ${minutes}m focus · +$xp XP"
        val resetMins = studyTimerState.value.selectedMinutes
        studyTimerState.value = StudyTimerState(resetMins, resetMins * 60, false)
    }

    // --- DIGITAL WELLNESS ---
    fun addManualUsageLog(appName: String, category: AppUsageCategory, minutes: Int, isDistracting: Boolean) {
        if (appName.isBlank() || minutes <= 0) return
        viewModelScope.launch {
            db.appUsageDao().insertLog(
                AppUsageLogEntity(
                    appName = appName.trim(),
                    categoryName = category.name,
                    minutesUsed = minutes.coerceIn(1, 720),
                    isDistracting = isDistracting,
                    dateEpochDay = DateTimeUtils.todayEpochDay()
                )
            )
            snackbarState.value = "Logged ${minutes}m for ${appName.trim()}"
        }
    }

    fun deleteUsageLog(log: AppUsageLogEntity) {
        viewModelScope.launch {
            db.appUsageDao().deleteLog(log)
        }
    }

    // --- DIGITAL PET & CHALLENGES ---
    fun interactWithPet(action: String) {
        viewModelScope.launch {
            when (action) {
                "FEED" -> {
                    prefsRepo.awardXpAndBoostPet(xpDelta = 5, happinessDelta = 6, energyDelta = 12)
                    petReactionState.value = "${uiState.value.prefs.petName} absorbed energy core! (+12 Energy)"
                }
                "PLAY" -> {
                    prefsRepo.awardXpAndBoostPet(xpDelta = 10, happinessDelta = 12, energyDelta = -5)
                    petReactionState.value = "${uiState.value.prefs.petName} is hyped! (+12 Happiness, +10 XP)"
                }
                "REST" -> {
                    prefsRepo.awardXpAndBoostPet(xpDelta = 0, happinessDelta = 3, energyDelta = 18)
                    petReactionState.value = "${uiState.value.prefs.petName} entered recharge mode (+18 Energy)"
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
                snackbarState.value = "Unlock at Level ${accessory.requiredLevel}"
            }
        }
    }

    fun claimChallenge(challenge: ChallengeEntity) {
        if (challenge.isClaimed || challenge.currentProgress < challenge.targetCount) return
        viewModelScope.launch {
            db.challengeDao().updateChallenge(challenge.copy(isClaimed = true))
            prefsRepo.awardXpAndBoostPet(challenge.xpReward, happinessDelta = 10, energyDelta = 8)
            petReactionState.value = "Challenge cleared! +${challenge.xpReward} XP"
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
        if (state.prefs.level >= 5) {
            prefsRepo.unlockBadge("LEVEL_5")
        }
    }

    // --- FRIENDS ---
    fun addDemoFriend(name: String, handle: String, xp: Int, streak: Int) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val cleanHandle = handle.trim().ifEmpty { "@${name.trim().lowercase().replace(" ", "")}" }
            val computedLevel = (xp.coerceAtLeast(100) / 250) + 1
            db.friendDao().insertFriend(
                FriendEntity(
                    displayName = name.trim(),
                    handle = if (cleanHandle.startsWith("@")) cleanHandle else "@$cleanHandle",
                    level = computedLevel,
                    xp = xp.coerceIn(100, 9999),
                    streakDays = streak.coerceIn(0, 365),
                    focusMinutesToday = 35,
                    sharesStats = true,
                    statusText = "Joined via local invite"
                )
            )
            snackbarState.value = "${name.trim()} added to local leaderboard"
        }
    }

    fun removeFriend(friend: FriendEntity) {
        viewModelScope.launch {
            db.friendDao().deleteFriend(friend)
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
            val pendingSummary = current.tasks
                .filter { !it.isCompleted }
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
                completedTasksCount = current.completedTasksCount,
                upcomingEventsSummary = eventsSummary,
                screenTimeTodayMinutes = current.totalScreenMinutesToday,
                focusMinutesToday = current.prefs.focusMinutesLoggedToday
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
                            db.taskDao().insertTask(
                                TaskEntity(
                                    title = title,
                                    priorityName = result.priority.name,
                                    categoryName = result.category.name,
                                    deadlineEpochDay = today + result.dateOffsetDays,
                                    xpReward = result.priority.xpValue
                                )
                            )
                            badge = "Task Created · $title"
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
                                val startMin = 540 + idx * 120 // 9 AM, 11 AM, 1 PM
                                db.calendarEventDao().insertEvent(
                                    CalendarEventEntity(
                                        title = task.title,
                                        notes = "Auto-blocked by LIFE AI",
                                        dateEpochDay = today + 1,
                                        startMinuteOfDay = startMin,
                                        durationMinutes = 60,
                                        categoryName = task.categoryName
                                    )
                                )
                            }
                            if (pending.isNotEmpty()) {
                                badge = "Tomorrow Organized · ${pending.size} Focus Blocks"
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

    // --- SETTINGS ---
    fun updateProfile(name: String, handle: String, focusGoal: Int, screenLimit: Int) {
        viewModelScope.launch {
            prefsRepo.updateProfile(name, handle, focusGoal, screenLimit)
            snackbarState.value = "Profile saved"
        }
    }

    fun updatePetName(name: String) {
        viewModelScope.launch {
            prefsRepo.updatePetName(name)
            snackbarState.value = "Companion renamed"
            syncHomeWidgets()
        }
    }

    fun updateAccentPreset(preset: ThemeAccentPreset) {
        viewModelScope.launch {
            prefsRepo.updateAccentPreset(preset)
        }
    }

    fun updatePrivacy(shareXp: Boolean, shareStreaks: Boolean, shareFocus: Boolean) {
        viewModelScope.launch {
            prefsRepo.updatePrivacy(shareXp, shareStreaks, shareFocus)
        }
    }

    fun updateNotifications(enabled: Boolean, reminders: Boolean, wellnessAlerts: Boolean) {
        viewModelScope.launch {
            prefsRepo.updateNotifications(enabled, reminders, wellnessAlerts)
        }
    }

    fun resetAllLocalData() {
        viewModelScope.launch {
            db.taskDao().clearAll()
            db.calendarEventDao().clearAll()
            db.habitDao().clearAll()
            db.appUsageDao().clearAll()
            db.challengeDao().clearAll()
            db.friendDao().clearAll()
            db.aiMessageDao().clearAll()
            prefsRepo.resetDefaults()
            // Re-seed clean starter workspace
            db.widgetConfigDao().upsertAll(
                DashboardWidgetType.entries.mapIndexed { idx, t ->
                    WidgetConfigEntity(t.name, idx, true)
                }
            )
            LifeOsSeeder.seedInitialDataIfNeeded(db)
            snackbarState.value = "Workspace reset to fresh defaults"
            syncHomeWidgets()
        }
    }
}
