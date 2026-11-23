package com.example.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.FriendsScreen
import com.example.ui.screens.GrowthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LifeAiScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.WellnessScreen
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.LifeOsTheme
import com.example.ui.theme.ObsidianBase
import com.example.ui.theme.ObsidianVoid
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

    // Ensure system Back returns to Home from secondary sections
    BackHandler(enabled = state.prefs.isSignedIn && currentSection != LifeOsSection.HOME) {
        currentSection = LifeOsSection.HOME
    }

    val navigateToRoute: (String) -> Unit = { route ->
        val found = LifeOsSection.entries.firstOrNull { it.route == route }
        if (found != null) currentSection = found
    }

    LifeOsTheme(accentPreset = state.prefs.accentPreset) {
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
                            .background(ObsidianVoid)
                    )
                }
                1 -> {
                    OnboardingScreen(
                        existingPrefs = state.prefs,
                        onCompleteSignIn = { name, identity, petName, focusGoal, accent ->
                            currentSection = LifeOsSection.HOME
                            viewModel.completeOnboarding(
                                name = name,
                                identityOrEmail = identity,
                                petName = petName,
                                focusGoalMinutes = focusGoal,
                                accentPreset = accent
                            )
                        }
                    )
                }
                else -> {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ObsidianVoid)
                    ) {
            val isWideScreen = maxWidth >= 600.dp

            Scaffold(
                containerColor = ObsidianVoid,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!isWideScreen) {
                        NavigationBar(
                            containerColor = ObsidianBase,
                            contentColor = InkPrimary,
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
                                        selectedTextColor = InkPrimary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                        unselectedIconColor = InkMuted,
                                        unselectedTextColor = InkMuted
                                    ),
                                    modifier = Modifier.testTag("nav_${sec.route}")
                                )
                            }

                            val isSecondaryActive = currentSection in listOf(
                                LifeOsSection.WELLNESS,
                                LifeOsSection.GROWTH,
                                LifeOsSection.FRIENDS,
                                LifeOsSection.SETTINGS
                            )
                            NavigationBarItem(
                                selected = isSecondaryActive,
                                onClick = { showModuleDrawer = true },
                                icon = {
                                    Icon(Icons.Filled.Apps, contentDescription = "More Modules")
                                },
                                label = { Text("More", maxLines = 1, softWrap = false) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.secondary,
                                    selectedTextColor = InkPrimary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                    unselectedIconColor = InkMuted,
                                    unselectedTextColor = InkMuted
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
                            containerColor = ObsidianBase,
                            contentColor = InkPrimary,
                            modifier = Modifier
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "LIFE OS",
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
                                    label = { Text(sec.label, maxLines = 1) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.secondary,
                                        selectedTextColor = InkPrimary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                        unselectedIconColor = InkMuted,
                                        unselectedTextColor = InkMuted
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
                                    onDeleteUsage = viewModel::deleteUsageLog
                                )
                                LifeOsSection.GROWTH -> GrowthScreen(
                                    state = state,
                                    onInteractPet = viewModel::interactWithPet,
                                    onEquipAccessory = viewModel::equipPetAccessory,
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
                                LifeOsSection.SETTINGS -> SettingsScreen(
                                    state = state,
                                    onUpdateProfile = viewModel::updateProfile,
                                    onUpdatePetName = viewModel::updatePetName,
                                    onUpdateAccent = viewModel::updateAccentPreset,
                                    onUpdatePrivacy = viewModel::updatePrivacy,
                                    onUpdateNotifications = viewModel::updateNotifications,
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

            // Mobile Command Dock Sheet for all 8 sections
            if (showModuleDrawer) {
                ModalBottomSheet(
                    onDismissRequest = { showModuleDrawer = false },
                    containerColor = ObsidianBase
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("LIFE OS Modules", style = MaterialTheme.typography.headlineMedium, color = InkPrimary)
                        Text("Open additional LIFE OS tools", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                        Spacer(Modifier.height(4.dp))
                        val modules = listOf(
                            LifeOsSection.WELLNESS to "Screen time, distracting apps & 7-day trends",
                            LifeOsSection.GROWTH to "Digital Pet ${state.prefs.petName}, XP, streaks & badges",
                            LifeOsSection.FRIENDS to "Leaderboard, invites & privacy",
                            LifeOsSection.SETTINGS to "Profile, themes, widgets & audio"
                        )
                        modules.forEach { (section, subtitle) ->
                            val active = currentSection == section
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        currentSection = section
                                        showModuleDrawer = false
                                    }
                                    .testTag("drawer_${section.route}"),
                                color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else CharcoalElevated,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        section.selectedIcon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(section.label, style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
                }
            }
        }
    }
}
