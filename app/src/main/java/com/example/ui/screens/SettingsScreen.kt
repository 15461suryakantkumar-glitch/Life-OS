package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CompanionSpecies
import com.example.data.local.UserAvatarPreset
import com.example.data.local.WidgetConfigEntity
import com.example.ui.components.GlanceWidgetPinningSection
import com.example.ui.components.ObsidianCard
import com.example.ui.components.RetakePersonalityQuizDialog
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.UserAvatarBadge
import com.example.ui.components.WidgetCustomizerList
import com.example.ui.theme.LifeOsThemeMode
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.LifeOsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: LifeOsUiState,
    onOpenProfile: () -> Unit,
    onReappointCompanion: (CompanionSpecies, String?) -> Unit,
    onUpdateProfile: (String, String, Int, Int, UserAvatarPreset) -> Unit,
    onUpdatePetName: (String) -> Unit,
    onUpdateThemeMode: (LifeOsThemeMode) -> Unit,
    onUpdatePrivacy: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onUpdateNotifications: (Boolean, Boolean, Boolean, Boolean, Int, Int) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onUpdateSoundEffects: (Boolean) -> Unit = {},
    onTestSoundEffect: () -> Unit = {},
    onToggleWidgetVisibility: (WidgetConfigEntity) -> Unit,
    onMoveWidget: (WidgetConfigEntity, Int) -> Unit,
    onSignOut: () -> Unit,
    onResetData: () -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val prefs = state.prefs
    var name by remember(prefs.userName) { mutableStateOf(prefs.userName) }
    var handle by remember(prefs.userHandle) { mutableStateOf(prefs.userHandle) }
    var petName by remember(prefs.petName) { mutableStateOf(prefs.petName) }
    var focusGoal by remember(prefs.dailyFocusGoalMinutes) { mutableStateOf(prefs.dailyFocusGoalMinutes.toString()) }
    var screenLimit by remember(prefs.dailyScreenLimitMinutes) { mutableStateOf(prefs.dailyScreenLimitMinutes.toString()) }

    var showWidgetSheet by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showRetakeQuiz by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineLarge, color = tokens.inkPrimary)
            Text(
                "Themes, profile, 2-hour task reminders, widgets, and audio",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.inkSecondary
            )
        }

        // Top Profile & Companion Shortcut Card
        item {
            ObsidianCard(elevated = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAvatarBadge(preset = prefs.avatarPreset, size = 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = prefs.userName.ifBlank { "Member" },
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.inkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${prefs.userHandle.ifBlank { "@member" }} · ${prefs.friendCode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(onClick = onOpenProfile, modifier = Modifier.testTag("settings_open_profile_btn")) {
                        Text("Profile", maxLines = 1, softWrap = false)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Companion: ${prefs.petName} (${prefs.companionSpecies.displayTitle})",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkSecondary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = { showRetakeQuiz = true }) {
                        Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retake Match", maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        // 4 Distinct Aesthetic Themes (1 Dark + 3 Light Modes)
        item {
            ObsidianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Aesthetic Theme", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                        Text("1 dark mode + 3 curated light studio themes", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LifeOsThemeMode.entries.forEach { mode ->
                        val selected = prefs.themeMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(tokens.smallRadius))
                                .background(
                                    if (selected) mode.previewPrimary.copy(alpha = 0.14f)
                                    else tokens.surfaceElevated
                                )
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) mode.previewPrimary else tokens.borderSubtle,
                                    shape = RoundedCornerShape(tokens.smallRadius)
                                )
                                .clickable { onUpdateThemeMode(mode) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("theme_mode_${mode.name}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = mode.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = tokens.inkPrimary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    StatusPill(
                                        text = if (mode.isDark) "Dark" else "Light",
                                        color = mode.previewPrimary
                                    )
                                }
                                Text(mode.subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(16.dp).clip(CircleShape).background(mode.previewPrimary))
                                Box(Modifier.size(16.dp).clip(CircleShape).background(mode.previewSecondary))
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(mode.previewSurface)
                                        .border(1.dp, tokens.borderSubtle, CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Personal Targets & Companion Name
        item {
            ObsidianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Account & Daily Targets", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = handle,
                    onValueChange = { handle = it },
                    label = { Text("Handle") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = focusGoal,
                        onValueChange = { focusGoal = it.filter { c -> c.isDigit() } },
                        label = { Text("Focus goal (m)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = screenLimit,
                        onValueChange = { screenLimit = it.filter { c -> c.isDigit() } },
                        label = { Text("Screen limit (m)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        onUpdateProfile(
                            name,
                            handle,
                            focusGoal.toIntOrNull() ?: 120,
                            screenLimit.toIntOrNull() ?: 180,
                            prefs.avatarPreset
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_profile_btn")
                ) { Text("Save Details", maxLines = 1, softWrap = false) }

                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = petName,
                        onValueChange = { petName = it },
                        label = { Text("Companion Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { onUpdatePetName(petName) }) {
                        Text("Rename", maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        // Dashboard Cards Customization
        item {
            ObsidianCard(onClick = { showWidgetSheet = true }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.DashboardCustomize, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Dashboard Cards", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                            Text(
                                "${state.widgets.count { it.isVisible }}/${state.widgets.size} cards visible on Home",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkSecondary
                            )
                        }
                    }
                    TextButton(onClick = { showWidgetSheet = true }) { Text("Edit") }
                }
            }
        }

        // Direct Android Home-Screen Glance Widgets Pinning
        item {
            ObsidianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Home-Screen Widgets", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pin interactive Life OS widgets directly to your Android launcher.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.inkSecondary
                )
                Spacer(Modifier.height(10.dp))
                GlanceWidgetPinningSection(petName = prefs.petName)
            }
        }

        // Sound, 2-Hour Task Reminders & Quiet Hours
        item {
            ObsidianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Notifications & 2h Task Nudges", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                        Text("Sound effects, 2-hour pending task repeats & quiet hours", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    label = "Sound Effects (Tasks, Streaks & Level-Up)",
                    checked = prefs.soundEffectsEnabled,
                    onCheckedChange = onUpdateSoundEffects
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onTestSoundEffect,
                        enabled = prefs.soundEffectsEnabled,
                        modifier = Modifier.testTag("test_sfx_btn")
                    ) {
                        Text("Preview Chime", maxLines = 1, softWrap = false)
                    }
                }
                ToggleRow(
                    label = "Master Notifications",
                    checked = prefs.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) onRequestNotificationPermission()
                        onUpdateNotifications(
                            enabled,
                            prefs.taskRemindersEnabled,
                            prefs.wellnessAlertsEnabled,
                            prefs.quietHoursEnabled,
                            prefs.quietStartHour,
                            prefs.quietEndHour
                        )
                    }
                )
                ToggleRow(
                    label = "2-Hour Pending Task Reminders (repeats every 2h until completed)",
                    checked = prefs.taskRemindersEnabled,
                    onCheckedChange = { reminders ->
                        if (reminders) onRequestNotificationPermission()
                        onUpdateNotifications(
                            prefs.notificationsEnabled || reminders,
                            reminders,
                            prefs.wellnessAlertsEnabled,
                            prefs.quietHoursEnabled,
                            prefs.quietStartHour,
                            prefs.quietEndHour
                        )
                    }
                )
                ToggleRow(
                    label = "Digital Wellbeing Budget Alerts",
                    checked = prefs.wellnessAlertsEnabled,
                    onCheckedChange = { wellness ->
                        onUpdateNotifications(
                            prefs.notificationsEnabled,
                            prefs.taskRemindersEnabled,
                            wellness,
                            prefs.quietHoursEnabled,
                            prefs.quietStartHour,
                            prefs.quietEndHour
                        )
                    }
                )
                ToggleRow(
                    label = "Quiet Hours (pause reminders overnight)",
                    checked = prefs.quietHoursEnabled,
                    onCheckedChange = { quiet ->
                        onUpdateNotifications(
                            prefs.notificationsEnabled,
                            prefs.taskRemindersEnabled,
                            prefs.wellnessAlertsEnabled,
                            quiet,
                            prefs.quietStartHour,
                            prefs.quietEndHour
                        )
                    }
                )
                if (prefs.quietHoursEnabled) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Quiet Window (${prefs.quietStartHour}:00 – ${prefs.quietEndHour}:00)",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.inkSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple(22, 7, "10 PM – 7 AM"),
                            Triple(23, 7, "11 PM – 7 AM"),
                            Triple(21, 6, "9 PM – 6 AM")
                        ).forEach { (start, end, label) ->
                            FilterChip(
                                selected = prefs.quietStartHour == start && prefs.quietEndHour == end,
                                onClick = {
                                    onUpdateNotifications(
                                        prefs.notificationsEnabled,
                                        prefs.taskRemindersEnabled,
                                        prefs.wellnessAlertsEnabled,
                                        true,
                                        start,
                                        end
                                    )
                                },
                                label = { Text(label, maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                }
            }
        }

        // Privacy Settings
        item {
            ObsidianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Privacy & Social Sharing", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    label = "Share Total XP on Leaderboard",
                    checked = prefs.shareXpWithFriends,
                    onCheckedChange = {
                        onUpdatePrivacy(it, prefs.shareStreaksWithFriends, prefs.shareFocusWithFriends, prefs.shareChallengesWithFriends)
                    }
                )
                ToggleRow(
                    label = "Share Daily Streaks",
                    checked = prefs.shareStreaksWithFriends,
                    onCheckedChange = {
                        onUpdatePrivacy(prefs.shareXpWithFriends, it, prefs.shareFocusWithFriends, prefs.shareChallengesWithFriends)
                    }
                )
                ToggleRow(
                    label = "Share Focus Minutes",
                    checked = prefs.shareFocusWithFriends,
                    onCheckedChange = {
                        onUpdatePrivacy(prefs.shareXpWithFriends, prefs.shareStreaksWithFriends, it, prefs.shareChallengesWithFriends)
                    }
                )
                ToggleRow(
                    label = "Share Challenge Progress",
                    checked = prefs.shareChallengesWithFriends,
                    onCheckedChange = {
                        onUpdatePrivacy(prefs.shareXpWithFriends, prefs.shareStreaksWithFriends, prefs.shareFocusWithFriends, it)
                    }
                )
            }
        }

        // Account & Storage Management
        item {
            ObsidianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null, tint = tokens.inkSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Account & Storage", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        append("Signed in as ${prefs.userName.ifBlank { "Member" }} (${prefs.userHandle})")
                        if (prefs.friendCode.isNotBlank()) append(" · ${prefs.friendCode}")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Local Room + DataStore persistence is active. Offline LIFE AI works without cloud keys, and Friend sync runs at ₹0 cost via share links and local Wi-Fi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.inkSecondary
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth().testTag("sign_out_btn")
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Switch Account / Sign Out", maxLines = 1, softWrap = false)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseAlert),
                    modifier = Modifier.fillMaxWidth().testTag("reset_data_btn")
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset Local Workspace", maxLines = 1, softWrap = false)
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showWidgetSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWidgetSheet = false },
            containerColor = tokens.surfaceBase
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text("Customize Dashboard Cards", style = MaterialTheme.typography.headlineMedium, color = tokens.inkPrimary)
                Spacer(Modifier.height(12.dp))
                WidgetCustomizerList(
                    widgets = state.widgets,
                    onToggleVisible = onToggleWidgetVisibility,
                    onMoveUp = { onMoveWidget(it, -1) },
                    onMoveDown = { onMoveWidget(it, 1) }
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = tokens.surfaceBase,
            title = { Text("Reset Local Workspace?", color = tokens.inkPrimary) },
            text = {
                Text(
                    "This clears all tasks, events, habits, friends, and XP, returning Life OS to a clean zero baseline.",
                    color = tokens.inkSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetData()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseAlert)
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showRetakeQuiz) {
        RetakePersonalityQuizDialog(
            onDismiss = { showRetakeQuiz = false },
            onConfirmCompanion = { newSpecies, newName ->
                onReappointCompanion(newSpecies, newName)
                showRetakeQuiz = false
            }
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = tokens.inkPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
