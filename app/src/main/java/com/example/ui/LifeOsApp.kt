package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.audio.LifeOsSoundCue
import com.example.kutta.LocalReducedMotion
import com.example.ui.components.AnimatedStreakPill
import com.example.ui.components.StatusPill
import com.example.ui.components.UserAvatarBadge
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.GrowthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LifeAiScreen
import com.example.ui.screens.NotificationsHistoryScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PermissionSetupScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SuruAiSettingsSheet
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.TogetherChatScreen
import com.example.ui.screens.TogetherScreen
import com.example.ui.screens.WellnessScreen
import com.example.ui.screens.conversationMessagesFor
import com.example.ui.theme.LifeOsTheme
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.viewmodel.LifeOsViewModel
import kotlinx.coroutines.launch

enum class LifeOsSection(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val isPrimaryTab: Boolean = false
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home, isPrimaryTab = true),
    TASKS("tasks", "Tasks", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle, isPrimaryTab = true),
    CALENDAR("calendar", "Calendar", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, isPrimaryTab = true),
    LIFE_AI("life_ai", "SURU", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, isPrimaryTab = true),
    WELLNESS("wellness", "Wellness", Icons.Filled.HealthAndSafety, Icons.Outlined.HealthAndSafety, isPrimaryTab = true),
    TOGETHER("together", "Together", Icons.Filled.Groups, Icons.Outlined.Groups),
    TOGETHER_CHAT("together_chat", "Chat", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
    GROWTH("growth", "KUTTA Corner", Icons.Filled.Pets, Icons.Outlined.Pets),
    NOTIFICATIONS("notifications", "Notifications", Icons.Filled.Notifications, Icons.Outlined.Notifications),
    PROFILE("profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
    PERMISSIONS("permissions", "Permissions", Icons.Filled.Shield, Icons.Outlined.Shield)
}

@Composable
fun LifeOsApp(
    initialRoute: String? = null,
    onConsumeInitialRoute: () -> Unit = {},
    incomingFriendPayload: String? = null,
    onConsumeIncomingFriendPayload: () -> Unit = {},
    viewModel: LifeOsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var currentSection by remember { mutableStateOf(LifeOsSection.HOME) }
    var openCreateTaskImmediately by remember { mutableStateOf(false) }
    var showSuruAiSettingsSheet by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            viewModel.showMessage("Enable notification permission in Android Settings for smart reminders.")
        }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (status != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun openDrawerMenu() {
        drawerScope.launch { drawerState.open() }
    }

    fun navigateToSection(target: LifeOsSection) {
        currentSection = target
        when (target) {
            LifeOsSection.TOGETHER -> viewModel.refreshTogetherData()
            LifeOsSection.PERMISSIONS -> viewModel.refreshSystemPermissions()
            else -> Unit
        }
    }

    val navigateToRoute: (String) -> Unit = { route ->
        val normalized = when (route) {
            "friends" -> "together"
            else -> route
        }
        LifeOsSection.entries.firstOrNull { it.route == normalized }?.let {
            navigateToSection(it)
        }
    }

    // Track foreground state, re-sync UsageStats/Permissions, & trigger KUTTA greeting on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.setAppInForeground(true)
                    viewModel.refreshUsageStatsStatusAndSync(showToast = false)
                    viewModel.refreshSystemPermissions()
                    viewModel.onAppForegrounded()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.setAppInForeground(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(initialRoute) {
        if (!initialRoute.isNullOrBlank()) {
            when (initialRoute) {
                "create_task" -> {
                    currentSection = LifeOsSection.TASKS
                    openCreateTaskImmediately = true
                }
                "friends", "together" -> {
                    navigateToSection(LifeOsSection.TOGETHER)
                }
                else -> {
                    LifeOsSection.entries.firstOrNull { it.route == initialRoute }?.let {
                        navigateToSection(it)
                    }
                }
            }
            onConsumeInitialRoute()
        }
    }

    LaunchedEffect(incomingFriendPayload, state.prefs.isSignedIn) {
        val payload = incomingFriendPayload
        if (!payload.isNullOrBlank() && state.prefs.isSignedIn) {
            viewModel.importFriendFromInput(payload)
            navigateToSection(LifeOsSection.TOGETHER)
            onConsumeIncomingFriendPayload()
        }
    }

    var hasRenderedInitialSection by remember { mutableStateOf(false) }
    LaunchedEffect(currentSection) {
        if (currentSection != LifeOsSection.TOGETHER) {
            viewModel.stopNearbyPeerRadar()
        }
        if (hasRenderedInitialSection) {
            viewModel.playUiCue(LifeOsSoundCue.NAV_SWITCH)
        } else {
            hasRenderedInitialSection = true
        }
    }

    LaunchedEffect(state.statusSnackbar) {
        val msg = state.statusSnackbar
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(state.petReactionBanner) {
        if (state.petReactionBanner != null) {
            kotlinx.coroutines.delay(4000L)
            viewModel.clearPetReaction()
        }
    }

    LaunchedEffect(state.levelUpBanner) {
        if (state.levelUpBanner != null) {
            kotlinx.coroutines.delay(4500L)
            viewModel.clearLevelUpBanner()
        }
    }

    val isMainAppReady = state.prefs.isSignedIn && state.prefs.hasCompletedPermissionSetup

    BackHandler(enabled = isMainAppReady && (drawerState.isOpen || currentSection != LifeOsSection.HOME)) {
        if (drawerState.isOpen) {
            drawerScope.launch { drawerState.close() }
        } else {
            when (currentSection) {
                LifeOsSection.TOGETHER_CHAT -> {
                    viewModel.openTogetherChat(null)
                    currentSection = LifeOsSection.TOGETHER
                }
                LifeOsSection.PERMISSIONS -> currentSection = LifeOsSection.SETTINGS
                else -> currentSection = LifeOsSection.HOME
            }
        }
    }

    CompositionLocalProvider(LocalReducedMotion provides state.prefs.reducedMotionEnabled) {
        LifeOsTheme(themeMode = state.prefs.themeMode) {
            val tokens = LifeOsThemeTokensProvider.current
            val gateStage = when {
                !state.isPrefsLoaded -> 0
                !state.prefs.isSignedIn -> 1
                !state.prefs.hasCompletedPermissionSetup -> 2
                else -> 3
            }

            Crossfade(targetState = gateStage, label = "life_os_auth_gate") { stage ->
                when (stage) {
                    0 -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(tokens.background)
                        )
                    }
                    1 -> {
                        OnboardingScreen(
                            existingPrefs = state.prefs,
                            authUiState = state.authUiState,
                            inviteLink = viewModel.buildMyInviteLink(),
                            onPreviewThemeMode = viewModel::updateThemeMode,
                            onSignUp = viewModel::signUpWithSupabase,
                            onSignIn = viewModel::signInWithSupabase,
                            onSendPasswordReset = viewModel::sendPasswordReset,
                            onResendVerification = viewModel::resendVerificationEmail,
                            onCheckUsername = viewModel::checkUsernameAvailability,
                            onClearAuthFeedback = viewModel::clearAuthFeedback,
                            onConnectFriendInput = viewModel::importFriendFromInput,
                            onComplete7Step = { name, handleOrEmail, bio, preferredLanguage, themeMode, avatarPreset, avatarPhotoUri, focusGoalMinutes ->
                                currentSection = LifeOsSection.HOME
                                viewModel.complete7StepOnboarding(
                                    name = name,
                                    handleOrEmail = handleOrEmail,
                                    bio = bio,
                                    preferredLanguage = preferredLanguage,
                                    themeMode = themeMode,
                                    avatarPreset = avatarPreset,
                                    avatarPhotoUri = avatarPhotoUri,
                                    focusGoalMinutes = focusGoalMinutes
                                )
                            }
                        )
                    }
                    2 -> {
                        PermissionSetupScreen(
                            permissions = state.appPermissions,
                            onRefreshPermissions = viewModel::refreshSystemPermissions,
                            onContinue = viewModel::completePermissionSetup
                        )
                    }
                    else -> {
                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            gesturesEnabled = currentSection != LifeOsSection.TOGETHER_CHAT,
                            drawerContent = {
                                FeeloraNavigationDrawerSheet(
                                    currentSection = currentSection,
                                    userName = state.prefs.userName,
                                    userHandle = state.prefs.userHandle,
                                    level = state.prefs.level,
                                    streakDays = state.prefs.dailyStreak,
                                    avatarPreset = state.prefs.avatarPreset,
                                    avatarPhotoUri = state.prefs.avatarPhotoUri,
                                    unreadTogether = state.unreadTogetherMessagesCount,
                                    unreadNotifications = state.unreadNotificationsCount,
                                    onSelectSection = { target ->
                                        drawerScope.launch { drawerState.close() }
                                        navigateToSection(target)
                                    }
                                )
                            }
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(tokens.background)
                            ) {
                                val isWideScreen = maxWidth >= 600.dp
                                val showNavigationChrome = currentSection != LifeOsSection.TOGETHER_CHAT

                                Scaffold(
                                    containerColor = tokens.background,
                                    contentWindowInsets = WindowInsets.safeDrawing,
                                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                                    bottomBar = {
                                        if (!isWideScreen && showNavigationChrome) {
                                            NavigationBar(
                                                containerColor = tokens.surfaceBase,
                                                contentColor = tokens.inkPrimary,
                                                windowInsets = WindowInsets.navigationBars
                                            ) {
                                                val primaryTabs = LifeOsSection.entries.filter { it.isPrimaryTab }
                                                primaryTabs.forEach { sec ->
                                                    val selected = currentSection == sec
                                                    NavigationBarItem(
                                                        selected = selected,
                                                        onClick = { navigateToSection(sec) },
                                                        icon = {
                                                            Icon(
                                                                if (selected) sec.selectedIcon else sec.unselectedIcon,
                                                                contentDescription = sec.label
                                                            )
                                                        },
                                                        label = { Text(sec.label, maxLines = 1, softWrap = false) },
                                                        colors = NavigationBarItemDefaults.colors(
                                                            selectedIconColor = MaterialTheme.colorScheme.secondary,
                                                            selectedTextColor = tokens.inkPrimary,
                                                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                                            unselectedIconColor = tokens.inkMuted,
                                                            unselectedTextColor = tokens.inkMuted
                                                        ),
                                                        modifier = Modifier.testTag("nav_${sec.route}")
                                                    )
                                                }
                                            }
                                        }
                                    }
                                ) { innerPadding ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding)
                                    ) {
                                        if (isWideScreen && showNavigationChrome) {
                                            val railSections = listOf(
                                                LifeOsSection.HOME,
                                                LifeOsSection.TASKS,
                                                LifeOsSection.CALENDAR,
                                                LifeOsSection.LIFE_AI,
                                                LifeOsSection.WELLNESS,
                                                LifeOsSection.TOGETHER,
                                                LifeOsSection.GROWTH,
                                                LifeOsSection.NOTIFICATIONS,
                                                LifeOsSection.PROFILE,
                                                LifeOsSection.SETTINGS
                                            )
                                            NavigationRail(
                                                containerColor = tokens.surfaceBase,
                                                contentColor = tokens.inkPrimary,
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                Spacer(Modifier.height(12.dp))
                                                Text(
                                                    text = "FEELORA",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(vertical = 8.dp)
                                                )
                                                railSections.forEach { sec ->
                                                    val selected = currentSection == sec
                                                    val badgeCount = when (sec) {
                                                        LifeOsSection.TOGETHER -> state.unreadTogetherMessagesCount
                                                        LifeOsSection.NOTIFICATIONS -> state.unreadNotificationsCount
                                                        else -> 0
                                                    }
                                                    NavigationRailItem(
                                                        selected = selected,
                                                        onClick = { navigateToSection(sec) },
                                                        icon = {
                                                            if (badgeCount > 0) {
                                                                BadgedBox(
                                                                    badge = {
                                                                        Badge {
                                                                            Text(if (badgeCount > 9) "9+" else "$badgeCount")
                                                                        }
                                                                    }
                                                                ) {
                                                                    Icon(
                                                                        if (selected) sec.selectedIcon else sec.unselectedIcon,
                                                                        contentDescription = sec.label
                                                                    )
                                                                }
                                                            } else {
                                                                Icon(
                                                                    if (selected) sec.selectedIcon else sec.unselectedIcon,
                                                                    contentDescription = sec.label
                                                                )
                                                            }
                                                        },
                                                        label = { Text(sec.label, maxLines = 1, softWrap = false) },
                                                        colors = NavigationRailItemDefaults.colors(
                                                            selectedIconColor = MaterialTheme.colorScheme.secondary,
                                                            selectedTextColor = tokens.inkPrimary,
                                                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                                            unselectedIconColor = tokens.inkMuted,
                                                            unselectedTextColor = tokens.inkMuted
                                                        ),
                                                        modifier = Modifier.testTag("rail_${sec.route}")
                                                    )
                                                }
                                            }
                                        }

                                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                            AnimatedContent(
                                                targetState = currentSection,
                                                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                                                label = "life_os_section_transition"
                                            ) { section ->
                                                when (section) {
                                                    LifeOsSection.HOME -> HomeScreen(
                                                        state = state,
                                                        onToggleTask = viewModel::toggleTaskCompleted,
                                                        onToggleHabit = viewModel::toggleHabitToday,
                                                        onSelectTimerPreset = viewModel::selectTimerDuration,
                                                        onSelectTimerTask = viewModel::selectFocusLinkedTask,
                                                        onToggleTimer = viewModel::toggleStudyTimer,
                                                        onResetTimer = viewModel::resetStudyTimer,
                                                        onFinishTimerEarly = viewModel::finishStudySessionEarly,
                                                        onUpdateNote = viewModel::updateQuickNote,
                                                        onInteractPet = viewModel::interactWithPet,
                                                        onTapKutta = viewModel::triggerKuttaTap,
                                                        onLogWaterQuick = viewModel::logWaterIntake,
                                                        onToggleWidgetVisibility = viewModel::toggleWidgetVisibility,
                                                        onMoveWidget = viewModel::moveWidget,
                                                        onNavigateToSection = navigateToRoute,
                                                        onQuickAddTaskClick = {
                                                            openCreateTaskImmediately = true
                                                            currentSection = LifeOsSection.TASKS
                                                        },
                                                        onOpenSuruWithVoice = {
                                                            currentSection = LifeOsSection.LIFE_AI
                                                            viewModel.startSuruVoiceListening()
                                                        },
                                                        onDismissLevelUp = viewModel::clearLevelUpBanner,
                                                        onOpenDrawer = ::openDrawerMenu,
                                                        onOpenNotifications = { navigateToSection(LifeOsSection.NOTIFICATIONS) },
                                                        onShowMessage = viewModel::showMessage
                                                    )
                                                    LifeOsSection.TASKS -> TasksScreen(
                                                        state = state,
                                                        openCreateDialogImmediately = openCreateTaskImmediately,
                                                        onConsumeCreateDialogFlag = { openCreateTaskImmediately = false },
                                                        onToggleTask = viewModel::toggleTaskCompleted,
                                                        onSaveTask = viewModel::saveTask,
                                                        onLogTaskDistraction = viewModel::logTaskDistraction,
                                                        onDeleteTask = viewModel::deleteTask,
                                                        onAddSection = viewModel::addTaskSection,
                                                        onRenameSection = viewModel::renameTaskSection,
                                                        onDeleteSection = viewModel::deleteTaskSection,
                                                        onQuickAddTaskInSection = viewModel::addInlineTask,
                                                        onTapKutta = viewModel::triggerKuttaTap,
                                                        onAskSuruBacklog = {
                                                            viewModel.triggerBacklogRecoveryPreview()
                                                            currentSection = LifeOsSection.LIFE_AI
                                                        },
                                                        onOpenDrawer = ::openDrawerMenu,
                                                        onOpenNotifications = { navigateToSection(LifeOsSection.NOTIFICATIONS) },
                                                        onOpenProfile = { navigateToSection(LifeOsSection.PROFILE) }
                                                    )
                                                    LifeOsSection.CALENDAR -> CalendarScreen(
                                                        state = state,
                                                        onSaveEvent = viewModel::saveCalendarEvent,
                                                        onDeleteEvent = viewModel::deleteCalendarEvent,
                                                        onAskSuru = { currentSection = LifeOsSection.LIFE_AI },
                                                        onShowMessage = viewModel::showMessage,
                                                        onOpenDrawer = ::openDrawerMenu,
                                                        onOpenNotifications = { navigateToSection(LifeOsSection.NOTIFICATIONS) },
                                                        onOpenProfile = { navigateToSection(LifeOsSection.PROFILE) }
                                                    )
                                                    LifeOsSection.WELLNESS -> WellnessScreen(
                                                        state = state,
                                                        onAddManualUsage = viewModel::addManualUsageLog,
                                                        onDeleteUsage = viewModel::deleteUsageLog,
                                                        onBuildUsageAccessIntent = viewModel::buildUsageAccessIntent,
                                                        onRefreshUsageStats = {
                                                            viewModel.refreshUsageStatsStatusAndSync(showToast = true)
                                                        },
                                                        onSelectWellbeingRange = viewModel::selectWellbeingRange,
                                                        onLogWater = viewModel::logWaterIntake,
                                                        onTriggerWaterReminder = viewModel::triggerWaterReminder,
                                                        onSubmitCheckIn = viewModel::submitDailyCheckIn,
                                                        onLogCycleStart = viewModel::logCyclePeriodStart,
                                                        onLogCycleEnd = viewModel::logCyclePeriodEnd,
                                                        onToggleCycleSymptom = viewModel::toggleCycleSymptom,
                                                        onDeleteCycleLog = viewModel::deleteCycleLog,
                                                        onAskSuru = { currentSection = LifeOsSection.LIFE_AI },
                                                        onOpenDrawer = ::openDrawerMenu,
                                                        onOpenNotifications = { navigateToSection(LifeOsSection.NOTIFICATIONS) },
                                                        onOpenProfile = { navigateToSection(LifeOsSection.PROFILE) }
                                                    )
                                                    LifeOsSection.GROWTH -> GrowthScreen(
                                                        state = state,
                                                        onBack = { currentSection = LifeOsSection.HOME },
                                                        onInteractPet = viewModel::interactWithPet,
                                                        onEquipAccessory = viewModel::equipPetAccessory,
                                                        onClaimChallenge = viewModel::claimChallenge,
                                                        onToggleHabit = viewModel::toggleHabitToday,
                                                        onAddHabit = viewModel::addHabit
                                                    )
                                                    LifeOsSection.TOGETHER -> TogetherScreen(
                                                        state = state,
                                                        myInviteLink = viewModel.buildMyInviteLink(),
                                                        onBack = { currentSection = LifeOsSection.HOME },
                                                        onRefresh = viewModel::refreshTogetherData,
                                                        onOpenChat = { friend ->
                                                            viewModel.openTogetherChat(friend.userId)
                                                            currentSection = LifeOsSection.TOGETHER_CHAT
                                                        },
                                                        onSendFriendRequest = viewModel::sendTogetherFriendRequest,
                                                        onRespondToRequest = viewModel::respondToTogetherRequest,
                                                        onCreateSharedGoal = viewModel::createTogetherSharedGoal,
                                                        onIncrementGoal = viewModel::incrementTogetherSharedGoal,
                                                        onCreateCompetition = viewModel::createTogetherCompetition,
                                                        onReactToActivity = viewModel::reactToTogetherActivity,
                                                        onUpdatePrivacy = viewModel::updatePrivacy,
                                                        onImportLocalFriend = viewModel::importFriendFromInput,
                                                        onToggleNearbyRadar = viewModel::toggleNearbyPeerRadar,
                                                        onSyncNearbyPeer = viewModel::syncWithNearbyPeer,
                                                        onShowMessage = viewModel::showMessage
                                                    )
                                                    LifeOsSection.TOGETHER_CHAT -> {
                                                        val myId = state.prefs.supabaseUserId.ifBlank {
                                                            state.prefs.friendCode.ifBlank { "local_self" }
                                                        }
                                                        val friend = state.togetherState.friends.firstOrNull {
                                                            it.userId == state.activeChatFriendId
                                                        }
                                                        if (friend != null) {
                                                            TogetherChatScreen(
                                                                myUserId = myId,
                                                                friend = friend,
                                                                messages = conversationMessagesFor(
                                                                    myUserId = myId,
                                                                    friendUserId = friend.userId,
                                                                    map = state.togetherState.messagesByConversation
                                                                ),
                                                                onBack = {
                                                                    viewModel.openTogetherChat(null)
                                                                    currentSection = LifeOsSection.TOGETHER
                                                                },
                                                                onSendMessage = { content, mediaUri ->
                                                                    viewModel.sendTogetherChatMessage(friend.userId, content, mediaUri)
                                                                },
                                                                onBlockFriend = { block ->
                                                                    viewModel.blockOrRemoveTogetherFriend(friend.userId, block)
                                                                    if (block) {
                                                                        viewModel.openTogetherChat(null)
                                                                        currentSection = LifeOsSection.TOGETHER
                                                                    }
                                                                },
                                                                onReportUser = { reason ->
                                                                    viewModel.reportTogetherUser(friend.userId, reason)
                                                                }
                                                            )
                                                        } else {
                                                            TogetherScreen(
                                                                state = state,
                                                                myInviteLink = viewModel.buildMyInviteLink(),
                                                                onBack = { currentSection = LifeOsSection.HOME },
                                                                onRefresh = viewModel::refreshTogetherData,
                                                                onOpenChat = { selectedFriend ->
                                                                    viewModel.openTogetherChat(selectedFriend.userId)
                                                                    currentSection = LifeOsSection.TOGETHER_CHAT
                                                                },
                                                                onSendFriendRequest = viewModel::sendTogetherFriendRequest,
                                                                onRespondToRequest = viewModel::respondToTogetherRequest,
                                                                onCreateSharedGoal = viewModel::createTogetherSharedGoal,
                                                                onIncrementGoal = viewModel::incrementTogetherSharedGoal,
                                                                onCreateCompetition = viewModel::createTogetherCompetition,
                                                                onReactToActivity = viewModel::reactToTogetherActivity,
                                                                onUpdatePrivacy = viewModel::updatePrivacy,
                                                                onImportLocalFriend = viewModel::importFriendFromInput,
                                                                onToggleNearbyRadar = viewModel::toggleNearbyPeerRadar,
                                                                onSyncNearbyPeer = viewModel::syncWithNearbyPeer,
                                                                onShowMessage = viewModel::showMessage
                                                            )
                                                        }
                                                    }
                                                    LifeOsSection.NOTIFICATIONS -> NotificationsHistoryScreen(
                                                        history = state.notificationHistory,
                                                        onBack = { currentSection = LifeOsSection.HOME },
                                                        onOpenRoute = navigateToRoute,
                                                        onMarkRead = viewModel::markNotificationRead,
                                                        onMarkAllRead = viewModel::markAllNotificationsRead,
                                                        onClearAll = viewModel::clearNotificationHistory
                                                    )
                                                    LifeOsSection.LIFE_AI -> LifeAiScreen(
                                                        state = state,
                                                        onSendPrompt = { prompt -> viewModel.sendAiPrompt(prompt) },
                                                        onDismissError = viewModel::clearAiError,
                                                        onStartVoice = viewModel::startSuruVoiceListening,
                                                        onStopVoice = viewModel::stopSuruVoiceListening,
                                                        onSpeakText = viewModel::speakSuruText,
                                                        onStopSpeaking = viewModel::stopSuruSpeaking,
                                                        onClearVoiceError = viewModel::clearSuruVoiceError,
                                                        onUpdateVoiceSettings = viewModel::updateSuruVoiceSettings,
                                                        onAdjustStudyPlanMinutes = viewModel::adjustPendingStudyPlanWorkload,
                                                        onConfirmPendingAction = viewModel::confirmPendingSuruAction,
                                                        onDismissPendingAction = viewModel::dismissPendingSuruConfirmation,
                                                        onUndoLastAction = viewModel::undoLastSuruAction,
                                                        onGenerateStudyHelp = viewModel::generateSuruStudyHelp,
                                                        onTriggerBacklogRecovery = viewModel::triggerBacklogRecoveryPreview,
                                                        onUpdateStudyPlanStatus = viewModel::updateStudyPlanStatus,
                                                        onTogglePersonalization = viewModel::updateSuruMemoryPersonalization,
                                                        onAddMemory = viewModel::upsertSuruMemory,
                                                        onToggleMemory = viewModel::toggleSuruMemory,
                                                        onDeleteMemory = viewModel::deleteSuruMemory,
                                                        onAttachImageUri = viewModel::attachSuruImageFromUri,
                                                        onClearImage = viewModel::clearPendingSuruImage,
                                                        onPlayCue = viewModel::playUiCue,
                                                        onOpenDrawer = ::openDrawerMenu,
                                                        onOpenAiSettings = { showSuruAiSettingsSheet = true }
                                                    )
                                                    LifeOsSection.PROFILE -> ProfileScreen(
                                                        state = state,
                                                        myShareToken = viewModel.buildMyShareToken(),
                                                        myInviteLink = viewModel.buildMyInviteLink(),
                                                        onBack = { currentSection = LifeOsSection.HOME },
                                                        onUpdateProfile = { name, handle, focusGoal, screenLimit, preset, bio, lang, photoUri ->
                                                            viewModel.updateProfile(
                                                                name = name,
                                                                handle = handle,
                                                                focusGoal = focusGoal,
                                                                screenLimit = screenLimit,
                                                                avatarPreset = preset,
                                                                bio = bio,
                                                                preferredLanguage = lang,
                                                                avatarPhotoUri = photoUri
                                                            )
                                                        },
                                                        onSelectAvatar = viewModel::updateAvatarPreset,
                                                        onSelectPhotoUri = viewModel::updateAvatarPhotoUri,
                                                        onShowMessage = viewModel::showMessage
                                                    )
                                                    LifeOsSection.SETTINGS -> SettingsScreen(
                                                        state = state,
                                                        onBack = { currentSection = LifeOsSection.HOME },
                                                        onOpenProfile = { currentSection = LifeOsSection.PROFILE },
                                                        onUpdateProfile = { name, handle, focusGoal, screenLimit, preset ->
                                                            viewModel.updateProfile(
                                                                name = name,
                                                                handle = handle,
                                                                focusGoal = focusGoal,
                                                                screenLimit = screenLimit,
                                                                avatarPreset = preset
                                                            )
                                                        },
                                                        onUpdateThemeMode = viewModel::updateThemeMode,
                                                        onUpdatePrivacy = viewModel::updatePrivacy,
                                                        onUpdateNotifications = viewModel::updateNotifications,
                                                        onRequestNotificationPermission = ::requestNotificationPermissionIfNeeded,
                                                        onOpenNotificationsHistory = { currentSection = LifeOsSection.NOTIFICATIONS },
                                                        onUpdateSoundEffects = viewModel::updateSoundEffects,
                                                        onPreviewSoundCategory = viewModel::previewSoundCategory,
                                                        onTestSoundEffect = viewModel::previewSoundEffect,
                                                        onUpdateWaterSettings = viewModel::updateWaterSettings,
                                                        onUpdateCycleSettings = viewModel::updateCycleSettings,
                                                        onUpdateReducedMotion = viewModel::updateReducedMotion,
                                                        onUpdateSuruVoiceSettings = viewModel::updateSuruVoiceSettings,
                                                        onUpdateSuruBriefingSettings = viewModel::updateSuruBriefingSettings,
                                                        onUpdateSuruPersonalization = viewModel::updateSuruMemoryPersonalization,
                                                        onOpenSuru = { currentSection = LifeOsSection.LIFE_AI },
                                                        onOpenSuruAiSettings = { showSuruAiSettingsSheet = true },
                                                        onToggleWidgetVisibility = viewModel::toggleWidgetVisibility,
                                                        onMoveWidget = viewModel::moveWidget,
                                                        onSignOut = viewModel::signOutLocalSession,
                                                        onResetData = viewModel::resetAllLocalData,
                                                        onOpenPermissions = {
                                                            viewModel.refreshSystemPermissions()
                                                            currentSection = LifeOsSection.PERMISSIONS
                                                        },
                                                        onUpdateSoundConfig = viewModel::updateSoundConfiguration,
                                                        onUpdateSmartReminderPolicy = viewModel::updateSmartReminderPolicy
                                                    )
                                                    LifeOsSection.PERMISSIONS -> PermissionsScreen(
                                                        permissions = state.appPermissions,
                                                        onBack = { currentSection = LifeOsSection.SETTINGS },
                                                        onRefresh = viewModel::refreshSystemPermissions,
                                                        onShowMessage = viewModel::showMessage
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showSuruAiSettingsSheet) {
                            SuruAiSettingsSheet(
                                state = state,
                                onDismiss = { showSuruAiSettingsSheet = false },
                                onUpdateGateway = viewModel::updateSuruAiGatewaySettings,
                                onUpdateVoice = viewModel::updateSuruVoiceSettings
                            )
                        }

                        if (state.authUiState.sessionExpiredPrompt) {
                            AlertDialog(
                                onDismissRequest = viewModel::dismissSessionExpiredPrompt,
                                containerColor = tokens.surfaceBase,
                                title = {
                                    Text(
                                        "Cloud Session Expired",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = tokens.inkPrimary
                                    )
                                },
                                text = {
                                    Text(
                                        "Your Supabase cloud session expired. All your local tasks, quick notes, Cycle Care, and SURU memories stay safe on this device. Sign in again to resume Together sync, or stay offline.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = tokens.inkSecondary
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            viewModel.dismissSessionExpiredPrompt()
                                            viewModel.signOutLocalSession()
                                        }
                                    ) {
                                        Text("Sign In Again")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = viewModel::dismissSessionExpiredPrompt) {
                                        Text("Stay Offline")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeeloraNavigationDrawerSheet(
    currentSection: LifeOsSection,
    userName: String,
    userHandle: String,
    level: Int,
    streakDays: Int,
    avatarPreset: com.example.data.local.UserAvatarPreset,
    avatarPhotoUri: String,
    unreadTogether: Int,
    unreadNotifications: Int,
    onSelectSection: (LifeOsSection) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    ModalDrawerSheet(
        drawerContainerColor = tokens.surfaceBase,
        drawerContentColor = tokens.inkPrimary,
        modifier = Modifier.width(304.dp).testTag("navigation_drawer_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Drawer Profile Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.cardRadius))
                    .background(tokens.surfaceElevated)
                    .border(1.dp, tokens.borderSubtle, RoundedCornerShape(tokens.cardRadius))
                    .clickable { onSelectSection(LifeOsSection.PROFILE) }
                    .padding(12.dp)
                    .testTag("drawer_profile_header"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatarBadge(
                    preset = avatarPreset,
                    photoUri = avatarPhotoUri,
                    displayName = userName,
                    size = 50.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userName.ifBlank { "Explorer" },
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = userHandle.ifBlank { "@explorer" },
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(text = "Lv.$level", color = MaterialTheme.colorScheme.primary)
                        AnimatedStreakPill(streakDays = streakDays)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "WORKSPACE",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.inkMuted,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
            )
            listOf(
                LifeOsSection.HOME,
                LifeOsSection.TASKS,
                LifeOsSection.CALENDAR,
                LifeOsSection.LIFE_AI,
                LifeOsSection.WELLNESS
            ).forEach { sec ->
                DrawerNavRow(
                    section = sec,
                    selected = currentSection == sec,
                    badgeCount = 0,
                    onClick = { onSelectSection(sec) }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = tokens.borderSubtle
            )

            Text(
                text = "SOCIAL, KUTTA & SYSTEM",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.inkMuted,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
            )
            listOf(
                LifeOsSection.TOGETHER,
                LifeOsSection.GROWTH,
                LifeOsSection.NOTIFICATIONS,
                LifeOsSection.PROFILE,
                LifeOsSection.SETTINGS,
                LifeOsSection.PERMISSIONS
            ).forEach { sec ->
                val badge = when (sec) {
                    LifeOsSection.TOGETHER -> unreadTogether
                    LifeOsSection.NOTIFICATIONS -> unreadNotifications
                    else -> 0
                }
                DrawerNavRow(
                    section = sec,
                    selected = currentSection == sec,
                    badgeCount = badge,
                    onClick = { onSelectSection(sec) }
                )
            }
        }
    }
}

@Composable
private fun DrawerNavRow(
    section: LifeOsSection,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    NavigationDrawerItem(
        label = {
            Text(
                text = section.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = if (selected) section.selectedIcon else section.unselectedIcon,
                contentDescription = section.label
            )
        },
        badge = if (badgeCount > 0) {
            {
                StatusPill(
                    text = if (badgeCount > 99) "99+" else "$badgeCount",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        } else null,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            unselectedContainerColor = Color.Transparent,
            selectedIconColor = MaterialTheme.colorScheme.secondary,
            unselectedIconColor = tokens.inkSecondary,
            selectedTextColor = tokens.inkPrimary,
            unselectedTextColor = tokens.inkSecondary
        ),
        modifier = Modifier.testTag("drawer_${section.route}")
    )
}
