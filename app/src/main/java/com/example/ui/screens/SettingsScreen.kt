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
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.audio.SoundCategory
import com.example.data.local.UserAvatarPreset
import com.example.data.local.WidgetConfigEntity
import com.example.notifications.AppPermissionStatus
import com.example.suru.SuruAiProvider
import com.example.suru.SuruVoiceLanguage
import com.example.ui.components.FeeloraConfirmDialog
import com.example.ui.components.FeeloraScreenBackdrop
import com.example.ui.components.FeeloraSubpageHeader
import com.example.ui.components.FeeloraToggleRow
import com.example.ui.components.GlanceWidgetPinningSection
import com.example.ui.components.ObsidianCard
import com.example.ui.components.StatusPill
import com.example.ui.components.UserAvatarBadge
import com.example.ui.components.WidgetCustomizerList
import com.example.ui.theme.LifeOsThemeMode
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.LifeOsUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: LifeOsUiState,
    onBack: () -> Unit = {},
    onOpenProfile: () -> Unit,
    onUpdateProfile: (String, String, Int, Int, UserAvatarPreset) -> Unit,
    onUpdateThemeMode: (LifeOsThemeMode) -> Unit,
    onUpdatePrivacy: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onUpdateNotifications: (
        enabled: Boolean,
        reminders: Boolean,
        wellnessAlerts: Boolean,
        quietHoursEnabled: Boolean,
        quietStartHour: Int,
        quietEndHour: Int,
        pendingTaskFollowUp: Boolean,
        companionAlerts: Boolean,
        achievementAlerts: Boolean,
        togetherAlerts: Boolean
    ) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationsHistory: () -> Unit = {},
    onUpdateSoundEffects: (Boolean) -> Unit = {},
    onPreviewSoundCategory: (SoundCategory) -> Unit = {},
    onTestSoundEffect: () -> Unit = {},
    onUpdateWaterSettings: (Int, Boolean) -> Unit = { _, _ -> },
    onUpdateCycleSettings: (Boolean, Int, Int) -> Unit = { _, _, _ -> },
    onUpdateReducedMotion: (Boolean) -> Unit = {},
    onUpdateSuruVoiceSettings: (Boolean, Float, SuruVoiceLanguage) -> Unit = { _, _, _ -> },
    onUpdateSuruBriefingSettings: (Boolean, Boolean, Int, Int) -> Unit = { _, _, _, _ -> },
    onUpdateSuruPersonalization: (Boolean) -> Unit = {},
    onOpenSuru: () -> Unit = {},
    onOpenSuruAiSettings: () -> Unit = {},
    onToggleWidgetVisibility: (WidgetConfigEntity) -> Unit,
    onMoveWidget: (WidgetConfigEntity, Int) -> Unit,
    onSignOut: () -> Unit,
    onResetData: () -> Unit,
    onOpenPermissions: () -> Unit = {},
    onUpdateSoundConfig: (
        masterEnabled: Boolean,
        volume: Float,
        hapticsEnabled: Boolean,
        navEnabled: Boolean,
        interactionEnabled: Boolean,
        taskEnabled: Boolean,
        achievementEnabled: Boolean,
        streakEnabled: Boolean,
        suruEnabled: Boolean,
        companionEnabled: Boolean,
        togetherEnabled: Boolean,
        reminderEnabled: Boolean,
        notificationEnabled: Boolean
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onUpdateSmartReminderPolicy: (Int, Int) -> Unit = { _, _ -> }
) {
    val tokens = LifeOsThemeTokensProvider.current
    val prefs = state.prefs
    val currentVoiceLang = remember(prefs.suruVoiceLanguage) {
        SuruVoiceLanguage.fromStored(prefs.suruVoiceLanguage)
    }
    var name by remember(prefs.userName) { mutableStateOf(prefs.userName) }
    var handle by remember(prefs.userHandle) { mutableStateOf(prefs.userHandle) }
    var focusGoal by remember(prefs.dailyFocusGoalMinutes) { mutableStateOf(prefs.dailyFocusGoalMinutes.toString()) }
    var screenLimit by remember(prefs.dailyScreenLimitMinutes) { mutableStateOf(prefs.dailyScreenLimitMinutes.toString()) }

    var showWidgetSheet by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    fun pushSoundUpdate(
        master: Boolean = prefs.soundEffectsEnabled,
        volume: Float = prefs.soundVolume,
        haptics: Boolean = prefs.hapticsEnabled,
        nav: Boolean = prefs.soundNavEnabled,
        interaction: Boolean = prefs.soundInteractionEnabled,
        task: Boolean = prefs.soundTaskEnabled,
        achievement: Boolean = prefs.soundAchievementEnabled,
        streak: Boolean = prefs.soundStreakEnabled,
        suru: Boolean = prefs.soundSuruEnabled,
        companion: Boolean = prefs.soundCompanionEnabled,
        together: Boolean = prefs.soundTogetherEnabled,
        reminder: Boolean = prefs.soundReminderEnabled,
        notif: Boolean = prefs.soundNotificationEnabled
    ) {
        onUpdateSoundConfig(master, volume, haptics, nav, interaction, task, achievement, streak, suru, companion, together, reminder, notif)
    }

    FeeloraScreenBackdrop(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FeeloraSubpageHeader(
                    title = "Settings",
                    subtitle = "3 Themes, 10 Soundscape Channels, Notifications & Privacy",
                    onBack = onBack
                )
            }

            // Profile Shortcut
            item {
                ObsidianCard(elevated = true) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        UserAvatarBadge(preset = prefs.avatarPreset, photoUri = prefs.avatarPhotoUri, displayName = prefs.userName, size = 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(prefs.userName.ifBlank { "Member" }, style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${prefs.userHandle.ifBlank { "@member" }} · ${prefs.friendCode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(onClick = onOpenProfile, modifier = Modifier.testTag("settings_open_profile_btn")) {
                            Text("Profile", maxLines = 1, softWrap = false)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Companion: KUTTA (${prefs.petStage.title})", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                        StatusPill(text = "Lv.${prefs.level}", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            // Permissions Center
            item {
                val readyCount = state.appPermissions.count { it.status == AppPermissionStatus.GRANTED || it.status == AppPermissionStatus.NOT_REQUIRED }
                val totalCount = state.appPermissions.size
                ObsidianCard(onClick = onOpenPermissions, borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Permissions & Privacy Center", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                Text(
                                    text = if (totalCount > 0) "$readyCount of $totalCount access checks ready" else "Inspect Android permissions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.inkSecondary
                                )
                            }
                        }
                        OutlinedButton(onClick = onOpenPermissions, modifier = Modifier.testTag("settings_open_permissions_btn")) {
                            Text("Manage", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            // 3 Curated Aesthetic Themes (Hybrid Lavender, Midnight Aurora, Matcha Mist)
            item {
                ObsidianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Aesthetic Theme", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                            Text("Three crafted atmospheres with distinct typography", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
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
                                    .background(if (selected) mode.previewPrimary.copy(alpha = 0.16f) else tokens.surfaceElevated)
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
                                        Text(mode.displayName, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                        StatusPill(text = if (mode.isDark) "Dark" else "Light", color = mode.previewPrimary)
                                    }
                                    Text(mode.subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.size(16.dp).clip(CircleShape).background(mode.previewPrimary))
                                    Box(Modifier.size(16.dp).clip(CircleShape).background(mode.previewSecondary))
                                    Box(Modifier.size(16.dp).clip(CircleShape).background(mode.previewSurface).border(1.dp, tokens.borderSubtle, CircleShape))
                                }
                            }
                        }
                    }
                }
            }

            // 10-Category Acoustic Soundscape & Haptics
            item {
                ObsidianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("10-Category Sound System", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                            Text("Soft chimes with automatic SURU voice & background suppression", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    FeeloraToggleRow(
                        title = "Master Sound Effects",
                        checked = prefs.soundEffectsEnabled,
                        onCheckedChange = onUpdateSoundEffects
                    )
                    Spacer(Modifier.height(6.dp))
                    FeeloraToggleRow(
                        title = "Subtle Haptic Touch Feedback",
                        checked = prefs.hapticsEnabled,
                        onCheckedChange = { pushSoundUpdate(haptics = it) }
                    )
                    if (prefs.soundEffectsEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Volume (${(prefs.soundVolume * 100).roundToInt()}%)", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                            TextButton(onClick = onTestSoundEffect, modifier = Modifier.testTag("test_sfx_btn")) {
                                Text("Test Chime", maxLines = 1, softWrap = false)
                            }
                        }
                        Slider(
                            value = prefs.soundVolume,
                            onValueChange = { pushSoundUpdate(volume = it) },
                            valueRange = 0.1f..1.0f,
                            modifier = Modifier.testTag("sound_volume_slider")
                        )
                        Spacer(Modifier.height(6.dp))
                        val categoryBindings = listOf(
                            Triple(SoundCategory.NAVIGATION, prefs.soundNavEnabled) { v: Boolean -> pushSoundUpdate(nav = v) },
                            Triple(SoundCategory.BUTTON_INTERACTION, prefs.soundInteractionEnabled) { v: Boolean -> pushSoundUpdate(interaction = v) },
                            Triple(SoundCategory.TASK_COMPLETION, prefs.soundTaskEnabled) { v: Boolean -> pushSoundUpdate(task = v) },
                            Triple(SoundCategory.ACHIEVEMENT, prefs.soundAchievementEnabled) { v: Boolean -> pushSoundUpdate(achievement = v) },
                            Triple(SoundCategory.STREAK_COMPLETION, prefs.soundStreakEnabled) { v: Boolean -> pushSoundUpdate(streak = v) },
                            Triple(SoundCategory.SURU_INTERACTION, prefs.soundSuruEnabled) { v: Boolean -> pushSoundUpdate(suru = v) },
                            Triple(SoundCategory.KUTTA_INTERACTION, prefs.soundCompanionEnabled) { v: Boolean -> pushSoundUpdate(companion = v) },
                            Triple(SoundCategory.TOGETHER_MESSAGES, prefs.soundTogetherEnabled) { v: Boolean -> pushSoundUpdate(together = v) },
                            Triple(SoundCategory.REMINDERS, prefs.soundReminderEnabled) { v: Boolean -> pushSoundUpdate(reminder = v) },
                            Triple(SoundCategory.NOTIFICATIONS, prefs.soundNotificationEnabled) { v: Boolean -> pushSoundUpdate(notif = v) }
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            categoryBindings.forEach { (cat, enabled, onToggle) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(tokens.smallRadius))
                                        .background(tokens.surfaceElevated.copy(alpha = 0.72f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cat.label, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                        Text(cat.description, style = MaterialTheme.typography.labelSmall, color = tokens.inkSecondary)
                                    }
                                    IconButton(
                                        onClick = { onPreviewSoundCategory(cat) },
                                        modifier = Modifier.size(40.dp).testTag("preview_sound_${cat.name}")
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = "Preview ${cat.label}", tint = MaterialTheme.colorScheme.secondary)
                                    }
                                    Switch(checked = enabled, onCheckedChange = onToggle)
                                }
                            }
                        }
                    }
                }
            }

            // 5-Category Notification Center & Smart Reminders
            item {
                ObsidianCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Notifications & Reminders", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                                Text("5 smart channels + quiet hours + action buttons", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                            }
                        }
                        TextButton(onClick = onOpenNotificationsHistory) {
                            Text("History (${state.unreadNotificationsCount})", maxLines = 1, softWrap = false)
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    fun pushNotifUpdate(
                        enabled: Boolean = prefs.notificationsEnabled,
                        taskRem: Boolean = prefs.taskRemindersEnabled,
                        wellness: Boolean = prefs.wellnessAlertsEnabled,
                        quiet: Boolean = prefs.quietHoursEnabled,
                        qStart: Int = prefs.quietStartHour,
                        qEnd: Int = prefs.quietEndHour,
                        pending: Boolean = prefs.pendingTaskFollowUpEnabled,
                        companion: Boolean = prefs.companionAlertsEnabled,
                        achievement: Boolean = prefs.achievementAlertsEnabled,
                        together: Boolean = prefs.togetherAlertsEnabled
                    ) {
                        onUpdateNotifications(enabled, taskRem, wellness, quiet, qStart, qEnd, pending, companion, achievement, together)
                    }

                    ToggleRow("Master Notifications", prefs.notificationsEnabled) {
                        if (it) onRequestNotificationPermission()
                        pushNotifUpdate(enabled = it)
                    }
                    ToggleRow("1. Task Reminders", prefs.taskRemindersEnabled) {
                        if (it) onRequestNotificationPermission()
                        pushNotifUpdate(enabled = prefs.notificationsEnabled || it, taskRem = it)
                    }
                    ToggleRow("2. Pending Task 2h Follow-Ups (Done / Snooze / Tomorrow)", prefs.pendingTaskFollowUpEnabled) {
                        pushNotifUpdate(pending = it)
                    }
                    ToggleRow("3. KUTTA Companion Hydration & Care", prefs.companionAlertsEnabled) {
                        pushNotifUpdate(companion = it)
                    }
                    ToggleRow("4. Achievements & Streak Alerts", prefs.achievementAlertsEnabled) {
                        pushNotifUpdate(achievement = it)
                    }
                    ToggleRow("5. Together Messages & Requests", prefs.togetherAlertsEnabled) {
                        pushNotifUpdate(together = it)
                    }
                    ToggleRow("Digital Wellbeing Budget Alerts", prefs.wellnessAlertsEnabled) {
                        pushNotifUpdate(wellness = it)
                    }
                    if (prefs.taskRemindersEnabled) {
                        Spacer(Modifier.height(4.dp))
                        Text("Reminder Frequency (Every ${prefs.reminderFrequencyHours}h)", style = MaterialTheme.typography.labelSmall, color = tokens.inkSecondary)
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 3, 4).forEach { hrs ->
                                FilterChip(
                                    selected = prefs.reminderFrequencyHours == hrs,
                                    onClick = { onUpdateSmartReminderPolicy(hrs, prefs.maxRemindersPerTaskPerDay) },
                                    label = { Text("${hrs}h") },
                                    modifier = Modifier.testTag("reminder_freq_${hrs}h")
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Daily Cap per Task (${prefs.maxRemindersPerTaskPerDay}/day)", style = MaterialTheme.typography.labelSmall, color = tokens.inkSecondary)
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2, 3, 5).forEach { cap ->
                                FilterChip(
                                    selected = prefs.maxRemindersPerTaskPerDay == cap,
                                    onClick = { onUpdateSmartReminderPolicy(prefs.reminderFrequencyHours, cap) },
                                    label = { Text("${cap}/day") },
                                    modifier = Modifier.testTag("reminder_cap_${cap}")
                                )
                            }
                        }
                    }
                    ToggleRow("Quiet Hours (${prefs.quietStartHour}:00 – ${prefs.quietEndHour}:00)", prefs.quietHoursEnabled) {
                        pushNotifUpdate(quiet = it)
                    }
                    if (prefs.quietHoursEnabled) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(Triple(22, 7, "10 PM – 7 AM"), Triple(23, 7, "11 PM – 7 AM"), Triple(21, 6, "9 PM – 6 AM")).forEach { (s, e, lbl) ->
                                FilterChip(
                                    selected = prefs.quietStartHour == s && prefs.quietEndHour == e,
                                    onClick = { pushNotifUpdate(quiet = true, qStart = s, qEnd = e) },
                                    label = { Text(lbl, maxLines = 1, softWrap = false) }
                                )
                            }
                        }
                    }
                }
            }

            // SURU Multi-Provider AI Gateway & Voice
            item {
                val activeProvider = SuruAiProvider.fromStored(prefs.suruDefaultProvider)
                ObsidianCard(borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("SURU AI Gateway & Voice", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                                Text("${activeProvider.displayName} · ${prefs.suruDefaultModel}", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(onClick = onOpenSuruAiSettings) { Text("Models", maxLines = 1, softWrap = false) }
                            OutlinedButton(onClick = onOpenSuru, modifier = Modifier.testTag("settings_open_suru_btn")) { Text("Chat", maxLines = 1, softWrap = false) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ToggleRow("Mute SURU Voice Responses", prefs.suruVoiceMuted) {
                        onUpdateSuruVoiceSettings(it, prefs.suruSpeechSpeed, currentVoiceLang)
                    }
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuruVoiceLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = currentVoiceLang == lang,
                                onClick = { onUpdateSuruVoiceSettings(prefs.suruVoiceMuted, prefs.suruSpeechSpeed, lang) },
                                label = { Text(lang.label, maxLines = 1, softWrap = false) },
                                modifier = Modifier.testTag("settings_suru_lang_${lang.name}")
                            )
                        }
                    }
                    ToggleRow("Morning Briefing Notification", prefs.suruMorningBriefingEnabled) {
                        if (it) onRequestNotificationPermission()
                        onUpdateSuruBriefingSettings(it, prefs.suruEveningReflectionEnabled, prefs.suruMorningHour, prefs.suruEveningHour)
                    }
                    ToggleRow("Evening Reflection Notification", prefs.suruEveningReflectionEnabled) {
                        if (it) onRequestNotificationPermission()
                        onUpdateSuruBriefingSettings(prefs.suruMorningBriefingEnabled, it, prefs.suruMorningHour, prefs.suruEveningHour)
                    }
                    ToggleRow("SURU Personalization Memory", prefs.suruPersonalizationEnabled) {
                        onUpdateSuruPersonalization(it)
                    }
                }
            }

            // Account & Daily Targets
            item {
                ObsidianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Account & Daily Targets", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = handle, onValueChange = { handle = it }, label = { Text("Handle") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = focusGoal, onValueChange = { focusGoal = it.filter(Char::isDigit) }, label = { Text("Focus goal (m)") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = screenLimit, onValueChange = { screenLimit = it.filter(Char::isDigit) }, label = { Text("Screen limit (m)") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onUpdateProfile(name, handle, focusGoal.toIntOrNull() ?: 120, screenLimit.toIntOrNull() ?: 180, prefs.avatarPreset)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_profile_btn")
                    ) { Text("Save Details") }
                }
            }

            // Water & Cycle Care Settings
            item {
                ObsidianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Water & Private Cycle Care", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Daily Water Goal (${prefs.dailyWaterGoalMl} ml)", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1500, 2000, 2500, 3000).forEach { goal ->
                            FilterChip(selected = prefs.dailyWaterGoalMl == goal, onClick = { onUpdateWaterSettings(goal, prefs.waterRemindersEnabled) }, label = { Text("${goal}ml") })
                        }
                    }
                    ToggleRow("KUTTA Water Nudges", prefs.waterRemindersEnabled) {
                        onUpdateWaterSettings(prefs.dailyWaterGoalMl, it)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFF472B6), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cycle Care (100% On-Device)", style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary)
                    }
                    ToggleRow("Show Cycle Care in Wellness & Home", prefs.cycleCareEnabled) {
                        onUpdateCycleSettings(it, prefs.averageCycleLengthDays, prefs.averagePeriodLengthDays)
                    }
                    if (prefs.cycleCareEnabled) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(25, 26, 28, 30, 32, 35).forEach { len ->
                                FilterChip(selected = prefs.averageCycleLengthDays == len, onClick = { onUpdateCycleSettings(true, len, prefs.averagePeriodLengthDays) }, label = { Text("${len}d") })
                            }
                        }
                    }
                }
            }

            // Accessibility, Dashboard Cards & Home-Screen Widgets
            item {
                ObsidianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AccessibilityNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Motion & Widgets", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                    }
                    ToggleRow("Reduced Motion", prefs.reducedMotionEnabled, onUpdateReducedMotion)
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { showWidgetSheet = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.DashboardCustomize, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Customize Dashboard Cards (${state.widgets.count { it.isVisible }}/${state.widgets.size})")
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Home-Screen Glance Widgets", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                    }
                    Spacer(Modifier.height(6.dp))
                    GlanceWidgetPinningSection(petName = prefs.petName)
                }
            }

            // Privacy & Together Sharing
            item {
                ObsidianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Together Privacy & Sharing", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                    }
                    Spacer(Modifier.height(8.dp))
                    ToggleRow("Share Total XP & Level", prefs.shareXpWithFriends) {
                        onUpdatePrivacy(it, prefs.shareStreaksWithFriends, prefs.shareFocusWithFriends, prefs.shareChallengesWithFriends)
                    }
                    ToggleRow("Share Daily Streaks", prefs.shareStreaksWithFriends) {
                        onUpdatePrivacy(prefs.shareXpWithFriends, it, prefs.shareFocusWithFriends, prefs.shareChallengesWithFriends)
                    }
                    ToggleRow("Share Focus Minutes", prefs.shareFocusWithFriends) {
                        onUpdatePrivacy(prefs.shareXpWithFriends, prefs.shareStreaksWithFriends, it, prefs.shareChallengesWithFriends)
                    }
                    ToggleRow("Share Challenges & Consistency", prefs.shareChallengesWithFriends) {
                        onUpdatePrivacy(prefs.shareXpWithFriends, prefs.shareStreaksWithFriends, prefs.shareFocusWithFriends, it)
                    }
                }
            }

            // Account & Reset
            item {
                ObsidianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudOff, contentDescription = null, tint = tokens.inkSecondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Account & Storage", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Signed in as ${prefs.userName.ifBlank { "Member" }} (${prefs.userHandle})" + if (prefs.hasValidRemoteSession) " · Cloud Synced" else " · Device Local",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Signing out keeps all tasks, XP, habits, Cycle Care, and SURU memories preserved on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().testTag("sign_out_btn")) {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Switch Account / Sign Out")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showResetConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseAlert),
                        modifier = Modifier.fillMaxWidth().testTag("reset_data_btn")
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset Local Workspace")
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showWidgetSheet) {
        ModalBottomSheet(onDismissRequest = { showWidgetSheet = false }, containerColor = tokens.surfaceBase) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
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
        FeeloraConfirmDialog(
            title = "Reset Local Workspace?",
            message = "This clears all tasks, events, habits, SURU plans/memories, friends, and XP, returning Feelora to a clean baseline.",
            confirmLabel = "Reset",
            isDestructive = true,
            onConfirm = {
                onResetData()
                showResetConfirm = false
            },
            onDismiss = { showResetConfirm = false }
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = tokens.inkPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
