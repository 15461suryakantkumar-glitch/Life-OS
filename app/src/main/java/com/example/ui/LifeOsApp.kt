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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.FriendsScreen
import com.example.ui.screens.GrowthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LifeAiScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.WellnessScreen
import com.example.ui.theme.LifeOsTheme
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.viewmodel.LifeOsViewModel

enum class LifeOsSection(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    TASKS("tasks", "Tasks", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
    CALENDAR("calendar", "Calendar", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    WELLNESS("wellness", "Wellness", Icons.Filled.HealthAndSafety, Icons.Outlined.HealthAndSafety),
    GROWTH("growth", "Growth", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents),
    FRIENDS("friends", "Friends", Icons.Filled.Groups, Icons.Outlined.Groups),
    LIFE_AI("life_ai", "LIFE AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    PROFILE("profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var showModuleDrawer by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            viewModel.showMessage("Enable notification permission in Android Settings for 2-hour task reminders.")
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

    // Re-sync Android UsageStats automatically when returning from Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshUsageStatsStatusAndSync(showToast = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(initialRoute) {
        if (!initialRoute.isNullOrBlank()) {
            if (initialRoute == "create_task") {
                currentSection = LifeOsSection.TASKS
                openCreateTaskImmediately = true
            } else {
                LifeOsSection.entries.firstOrNull { it.route == initialRoute }?.let {
                    currentSection = it
                }
            }
            onConsumeInitialRoute()
        }
    }

    LaunchedEffect(incomingFriendPayload, state.prefs.isSignedIn) {
        val payload = incomingFriendPayload
        if (!payload.isNullOrBlank() && state.prefs.isSignedIn) {
            viewModel.importFriendFromInput(payload)
            currentSection = LifeOsSection.FRIENDS
            onConsumeIncomingFriendPayload()
        }
    }

    LaunchedEffect(currentSection) {
        if (currentSection != LifeOsSection.FRIENDS) {
            viewModel.stopNearbyPeerRadar()
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

    BackHandler(enabled = state.prefs.isSignedIn && currentSection != LifeOsSection.HOME) {
        currentSection = LifeOsSection.HOME
    }

    val navigateToRoute: (String) -> Unit = { route ->
        LifeOsSection.entries.firstOrNull { it.route == route }?.let {
            currentSection = it
        }
    }

    LifeOsTheme(themeMode = state.prefs.themeMode) {
        val tokens = LifeOsThemeTokensProvider.current
        val gateStage = when {
            !state.isPrefsLoaded -> 0
            !state.prefs.isSignedIn -> 1
            else -> 2
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
                        onPreviewThemeMode = viewModel::updateThemeMode,
                        onCompleteSignIn = { name, identity, petName, species, focusGoal, mode ->
                            currentSection = LifeOsSection.HOME
                            viewModel.completeOnboarding(
                                name = name,
                                identityOrEmail = identity,
                                petName = petName,
                                companionSpecies = species,
                                focusGoalMinutes = focusGoal,
                                themeMode = mode
                            )
                            requestNotificationPermissionIfNeeded()
                        }
                    )
                }
                else -> {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(tokens.background)
                    ) {
                        val isWideScreen = maxWidth >= 600.dp

                        Scaffold(
                            containerColor = tokens.background,
                            contentWindowInsets = WindowInsets.safeDrawing,
                            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                            bottomBar = {
                                if (!isWideScreen) {
                                    NavigationBar(
                                        containerColor = tokens.surfaceBase,
                                        contentColor = tokens.inkPrimary,
                                        windowInsets = WindowInsets.navigationBars
                                    ) {
                                        val primaryTabs = listOf(
                                            LifeOsSection.HOME,
                                            LifeOsSection.TASKS,
                                            LifeOsSection.CALENDAR,
                                            LifeOsSection.LIFE_AI
                                        )
                                        primaryTabs.forEach { sec ->
                                            val selected = currentSection == sec
                                            NavigationBarItem(
                                                selected = selected,
                                                onClick = { currentSection = sec },
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

                                        val isSecondaryActive = currentSection in listOf(
                                            LifeOsSection.WELLNESS,
                                            LifeOsSection.GROWTH,
                                            LifeOsSection.FRIENDS,
                                            LifeOsSection.PROFILE,
                                            LifeOsSection.SETTINGS
                                        )
                                        NavigationBarItem(
                                            selected = isSecondaryActive,
                                            onClick = { showModuleDrawer = true },
                                            icon = {
                                                Icon(Icons.Filled.Apps, contentDescription = "More modules")
                                            },
                                            label = { Text("More", maxLines = 1, softWrap = false) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.secondary,
                                                selectedTextColor = tokens.inkPrimary,
                                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                                unselectedIconColor = tokens.inkMuted,
                                                unselectedTextColor = tokens.inkMuted
                                            ),
                                            modifier = Modifier.testTag("nav_modules")
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                if (isWideScreen) {
                                    NavigationRail(
                                        containerColor = tokens.surfaceBase,
                                        contentColor = tokens.inkPrimary,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            text = "Life OS",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                        LifeOsSection.entries.forEach { sec ->
                                            val selected = currentSection == sec
                                            NavigationRailItem(
                                                selected = selected,
                                                onClick = { currentSection = sec },
                                                icon = {
                                                    Icon(
                                                        if (selected) sec.selectedIcon else sec.unselectedIcon,
                                                        contentDescription = sec.label
                                                    )
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
                                                onToggleWidgetVisibility = viewModel::toggleWidgetVisibility,
                                                onMoveWidget = viewModel::moveWidget,
                                                onNavigateToSection = navigateToRoute,
                                                onQuickAddTaskClick = {
                                                    openCreateTaskImmediately = true
                                                    currentSection = LifeOsSection.TASKS
                                                },
                                                onDismissLevelUp = viewModel::clearLevelUpBanner,
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
                                                onQuickAddTaskInSection = viewModel::addInlineTask
                                            )
                                            LifeOsSection.CALENDAR -> CalendarScreen(
                                                state = state,
                                                onSaveEvent = viewModel::saveCalendarEvent,
                                                onDeleteEvent = viewModel::deleteCalendarEvent
                                            )
                                            LifeOsSection.WELLNESS -> WellnessScreen(
                                                state = state,
                                                onAddManualUsage = viewModel::addManualUsageLog,
                                                onDeleteUsage = viewModel::deleteUsageLog,
                                                onBuildUsageAccessIntent = viewModel::buildUsageAccessIntent,
                                                onRefreshUsageStats = {
                                                    viewModel.refreshUsageStatsStatusAndSync(showToast = true)
                                                }
                                            )
                                            LifeOsSection.GROWTH -> GrowthScreen(
                                                state = state,
                                                onInteractPet = viewModel::interactWithPet,
                                                onEquipAccessory = viewModel::equipPetAccessory,
                                                onReappointCompanion = viewModel::reappointCompanion,
                                                onClaimChallenge = viewModel::claimChallenge,
                                                onToggleHabit = viewModel::toggleHabitToday,
                                                onAddHabit = viewModel::addHabit
                                            )
                                            LifeOsSection.FRIENDS -> FriendsScreen(
                                                state = state,
                                                myShareToken = viewModel.buildMyShareToken(),
                                                myInviteLink = viewModel.buildMyInviteLink(),
                                                onImportInvite = viewModel::importFriendFromInput,
                                                onAddFriendByUsername = viewModel::addFriendByUsername,
                                                onToggleNearbyRadar = viewModel::toggleNearbyPeerRadar,
                                                onSyncNearbyPeer = viewModel::syncWithNearbyPeer,
                                                onRemoveFriend = viewModel::removeFriend,
                                                onUpdatePrivacy = viewModel::updatePrivacy,
                                                onShowMessage = viewModel::showMessage
                                            )
                                            LifeOsSection.LIFE_AI -> LifeAiScreen(
                                                state = state,
                                                onSendPrompt = viewModel::sendAiPrompt,
                                                onDismissError = viewModel::clearAiError
                                            )
                                            LifeOsSection.PROFILE -> ProfileScreen(
                                                state = state,
                                                myShareToken = viewModel.buildMyShareToken(),
                                                myInviteLink = viewModel.buildMyInviteLink(),
                                                onBack = { currentSection = LifeOsSection.HOME },
                                                onUpdateProfile = viewModel::updateProfile,
                                                onSelectAvatar = viewModel::updateAvatarPreset,
                                                onReappointCompanion = viewModel::reappointCompanion,
                                                onShowMessage = viewModel::showMessage
                                            )
                                            LifeOsSection.SETTINGS -> SettingsScreen(
                                                state = state,
                                                onOpenProfile = { currentSection = LifeOsSection.PROFILE },
                                                onReappointCompanion = viewModel::reappointCompanion,
                                                onUpdateProfile = viewModel::updateProfile,
                                                onUpdatePetName = viewModel::updatePetName,
                                                onUpdateThemeMode = viewModel::updateThemeMode,
                                                onUpdatePrivacy = viewModel::updatePrivacy,
                                                onUpdateNotifications = viewModel::updateNotifications,
                                                onRequestNotificationPermission = ::requestNotificationPermissionIfNeeded,
                                                onUpdateSoundEffects = viewModel::updateSoundEffects,
                                                onTestSoundEffect = viewModel::previewSoundEffect,
                                                onToggleWidgetVisibility = viewModel::toggleWidgetVisibility,
                                                onMoveWidget = viewModel::moveWidget,
                                                onSignOut = viewModel::signOutLocalSession,
                                                onResetData = viewModel::resetAllLocalData
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Mobile Command Dock Sheet for secondary modules
                        if (showModuleDrawer) {
                            ModalBottomSheet(
                                onDismissRequest = { showModuleDrawer = false },
                                containerColor = tokens.surfaceBase
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .windowInsetsPadding(WindowInsets.navigationBars)
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Life OS Workspace", style = MaterialTheme.typography.headlineMedium, color = tokens.inkPrimary)
                                    Text("Jump to analytics, companion, social & preferences", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                                    Spacer(Modifier.height(4.dp))
                                    val modules = listOf(
                                        LifeOsSection.PROFILE to "Avatar, graphs, Friend ID & Daily/Weekly analyzer",
                                        LifeOsSection.WELLNESS to "Screen time, Usage Access & per-task distraction",
                                        LifeOsSection.GROWTH to "${state.prefs.petName} (${state.prefs.companionSpecies.displayTitle}), habits & badges",
                                        LifeOsSection.FRIENDS to "Leaderboard, invites & Wi-Fi sync",
                                        LifeOsSection.SETTINGS to "4 themes, 2h task reminders, widgets & audio"
                                    )
                                    modules.forEach { (section, subtitle) ->
                                        val active = currentSection == section
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(tokens.cardRadius))
                                                .clickable {
                                                    currentSection = section
                                                    showModuleDrawer = false
                                                }
                                                .testTag("drawer_${section.route}"),
                                            color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else tokens.surfaceElevated,
                                            shape = RoundedCornerShape(tokens.cardRadius)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    section.selectedIcon,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Column {
                                                    Text(section.label, style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                                                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
