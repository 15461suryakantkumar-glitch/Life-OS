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
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WaterDrop
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
import com.example.data.audio.LifeOsSoundCue
import com.example.data.local.UserAvatarPreset
import com.example.data.local.WidgetConfigEntity
import com.example.notifications.AppPermissionStatus
import com.example.suru.SuruVoiceLanguage
import com.example.ui.components.GlanceWidgetPinningSection
import com.example.ui.components.ObsidianCard
import com.example.ui.components.SectionHeader
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
    onOpenProfile: () -> Unit,
    onUpdateProfile: (String, String, Int, Int, UserAvatarPreset) -> Unit,
    onUpdateThemeMode: (LifeOsThemeMode) -> Unit,
    onUpdatePrivacy: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onUpdateNotifications: (Boolean, Boolean, Boolean, Boolean, Int, Int) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onUpdateSoundEffects: (Boolean) -> Unit = {},
    onTestSoundEffect: () -> Unit = {},
    onUpdateWaterSettings: (Int, Boolean) -> Unit = { _, _ -> },
    onUpdateCycleSettings: (Boolean, Int, Int) -> Unit = { _, _, _ -> },
    onUpdateReducedMotion: (Boolean) -> Unit = {},
    onUpdateSuruVoiceSettings: (Boolean, Float, SuruVoiceLanguage) -> Unit = { _, _, _ -> },
    onUpdateSuruBriefingSettings: (Boolean, Boolean, Int, Int) -> Unit = { _, _, _, _ -> },
    onUpdateSuruPersonalization: (Boolean) -> Unit = {},
    onOpenSuru: () -> Unit = {},
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
        calendarEnabled: Boolean,
        suruEnabled: Boolean,
        gamificationEnabled: Boolean,
        companionEnabled: Boolean
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> },
    onPreviewSoundCue: (LifeOsSoundCue) -> Unit = {},
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
                "Themes, permissions, smart reminders, widgets, and audio",
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
                        text = "Permanent Companion: KUTTA (${prefs.petStage.title})",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkSecondary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    StatusPill(text = "Lv.${prefs.level}", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        // Dedicated Permissions & Privacy Center Card
        item {
            val readyCount = state.appPermissions.count {
                it.status == AppPermissionStatus.GRANTED || it.status == AppPermissionStatus.NOT_REQUIRED
            }
            val totalCount = state.appPermissions.size
            ObsidianCard(
                onClick = onOpenPermissions,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Permissions & Privacy Center", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                            Text(
                                text = if (totalCount > 0) {
                                    "$readyCount of $totalCount access checks ready · manage Android permissions"
                                } else {
                                    "Inspect notifications, camera, mic, photos & usage access"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkSecondary
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenPermissions,
                        modifier = Modifier.testTag("settings_open_permissions_btn")
                    ) {
                        Text("Manage", maxLines = 1, softWrap = false)
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
                        Text("2 dark modes + 4 curated light studio themes (FEELORA Lavender default)", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
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

        // SURU Personal AI Voice, Daily Briefings & Memory
        item {
            ObsidianCard(borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("SURU Assistant & Voice", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                            Text("Voice language, speech speed, daily briefings & memory", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                        }
                    }
                    OutlinedButton(onClick = onOpenSuru, modifier = Modifier.testTag("settings_open_suru_btn")) {
                        Text("Open", maxLines = 1, softWrap = false)
                    }
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    label = "Mute SURU Voice Responses",
                    checked = prefs.suruVoiceMuted,
                    onCheckedChange = { muted ->
                        onUpdateSuruVoiceSettings(muted, prefs.suruSpeechSpeed, currentVoiceLang)
                    }
                )
                Text("Voice Recognition & Speech Language", style = MaterialTheme.typography.labelSmall, color = tokens.inkSecondary)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuruVoiceLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = currentVoiceLang == lang,
                            onClick = { onUpdateSuruVoiceSettings(prefs.suruVoiceMuted, prefs.suruSpeechSpeed, lang) },
                            label = { Text(lang.label, maxLines = 1, softWrap = false) },
                            modifier = Modifier.testTag("settings_suru_lang_${lang.name}")
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Speech Rate (${prefs.suruSpeechSpeed}x)", style = MaterialTheme.typography.labelSmall, color = tokens.inkSecondary)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.85f to "0.85x", 1.0f to "1.0x", 1.25f to "1.25x").forEach { (rate, label) ->
                        FilterChip(
                            selected = kotlin.math.abs(prefs.suruSpeechSpeed - rate) < 0.05f,
                            onClick = { onUpdateSuruVoiceSettings(prefs.suruVoiceMuted, rate, currentVoiceLang) },
                            label = { Text(label, maxLines = 1, softWrap = false) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    label = "Morning Briefing Notification",
                    checked = prefs.suruMorningBriefingEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) onRequestNotificationPermission()
                        onUpdateSuruBriefingSettings(
                            enabled,
                            prefs.suruEveningReflectionEnabled,
                            prefs.suruMorningHour,
                            prefs.suruEveningHour
                        )
                    }
                )
                ToggleRow(
                    label = "Evening Reflection Notification",
                    checked = prefs.suruEveningReflectionEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) onRequestNotificationPermission()
                        onUpdateSuruBriefingSettings(
                            prefs.suruMorningBriefingEnabled,
                            enabled,
                            prefs.suruMorningHour,
                            prefs.suruEveningHour
                        )
                    }
                )
                ToggleRow(
                    label = "SURU Personalization Memory (editable in SURU -> Memory)",
                    checked = prefs.suruPersonalizationEnabled,
                    onCheckedChange = onUpdateSuruPersonalization
                )
                Text(
                    text = "SURU never sends audio silently, respects quiet hours, and never accesses private Cycle Care or journal notes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.inkMuted
                )
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
            }
        }

        // Hydration & Private Cycle Care Settings
        item {
            ObsidianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Water & Private Cycle Care", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Daily Water Goal (${prefs.dailyWaterGoalMl} ml)",
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.inkSecondary
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1500, 2000, 2500, 3000).forEach { goal ->
                        FilterChip(
                            selected = prefs.dailyWaterGoalMl == goal,
                            onClick = { onUpdateWaterSettings(goal, prefs.waterRemindersEnabled) },
                            label = { Text("${goal}ml", maxLines = 1, softWrap = false) }
                        )
                    }
                }
                ToggleRow(
                    label = "KUTTA Water Nudges",
                    checked = prefs.waterRemindersEnabled,
                    onCheckedChange = { onUpdateWaterSettings(prefs.dailyWaterGoalMl, it) }
                )

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFF472B6), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cycle Care (100% On-Device)", style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary)
                }
                ToggleRow(
                    label = "Show Cycle Care in Wellness OS & Home",
                    checked = prefs.cycleCareEnabled,
                    onCheckedChange = { onUpdateCycleSettings(it, prefs.averageCycleLengthDays, prefs.averagePeriodLengthDays) }
                )
                if (prefs.cycleCareEnabled) {
                    Text(
                        text = "Average Cycle Length: ${prefs.averageCycleLengthDays} days",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.inkSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(25, 26, 28, 30, 32, 35).forEach { len ->
                            FilterChip(
                                selected = prefs.averageCycleLengthDays == len,
                                onClick = { onUpdateCycleSettings(true, len, prefs.averagePeriodLengthDays) },
                                label = { Text("${len}d", maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                }
                Text(
                    text = "Cycle Care data never syncs with friends and never appears on home-screen widgets.",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.inkMuted
                )
            }
        }

        // Accessibility & Motion
        item {
            ObsidianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AccessibilityNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Accessibility & Motion", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                }
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    label = "Reduced Motion (simplify KUTTA animations & particles)",
                    checked = prefs.reducedMotionEnabled,
                    onCheckedChange = onUpdateReducedMotion
                )
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
                    "Pin interactive Feelora widgets directly to your Android launcher.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.inkSecondary
                )
                Spacer(Modifier.height(10.dp))
                GlanceWidgetPinningSection(petName = prefs.petName)
            }
        }

        // Acoustic Sound Effects, Volume, Haptics & Category Toggles
        item {
            ObsidianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Acoustic Soundscape & Haptics", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                        Text("Lavender-soft chimes with SURU speech protection", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    label = "Master Sound Effects",
                    checked = prefs.soundEffectsEnabled,
                    onCheckedChange = onUpdateSoundEffects
                )
                ToggleRow(
                    label = "Subtle Haptic Touch Feedback",
                    checked = prefs.hapticsEnabled,
                    onCheckedChange = { haptics ->
                        onUpdateSoundConfig(
                            prefs.soundEffectsEnabled,
                            prefs.soundVolume,
                            haptics,
                            prefs.soundNavEnabled,
                            prefs.soundInteractionEnabled,
                            prefs.soundTaskEnabled,
                            prefs.soundCalendarEnabled,
                            prefs.soundSuruEnabled,
                            prefs.soundGamificationEnabled,
                            prefs.soundCompanionEnabled
                        )
                    }
                )
                if (prefs.soundEffectsEnabled) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Volume (${(prefs.soundVolume * 100).roundToInt()}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.inkSecondary
                    )
                    Slider(
                        value = prefs.soundVolume,
                        onValueChange = { vol ->
                            onUpdateSoundConfig(
                                prefs.soundEffectsEnabled,
                                vol,
                                prefs.hapticsEnabled,
                                prefs.soundNavEnabled,
                                prefs.soundInteractionEnabled,
                                prefs.soundTaskEnabled,
                                prefs.soundCalendarEnabled,
                                prefs.soundSuruEnabled,
                                prefs.soundGamificationEnabled,
                                prefs.soundCompanionEnabled
                            )
                        },
                        valueRange = 0.1f..1.0f,
                        modifier = Modifier.testTag("sound_volume_slider")
                    )
                    Text("Sound Categories", style = MaterialTheme.typography.labelSmall, color = tokens.inkSecondary)
                    ToggleRow(
                        label = "Navigation Switches",
                        checked = prefs.soundNavEnabled,
                        onCheckedChange = { v ->
                            onUpdateSoundConfig(
                                prefs.soundEffectsEnabled, prefs.soundVolume, prefs.hapticsEnabled,
                                v, prefs.soundInteractionEnabled, prefs.soundTaskEnabled,
                                prefs.soundCalendarEnabled, prefs.soundSuruEnabled, prefs.soundGamificationEnabled, prefs.soundCompanionEnabled
                            )
                        }
                    )
                    ToggleRow(
                        label = "UI Interactions & Undo",
                        checked = prefs.soundInteractionEnabled,
                        onCheckedChange = { v ->
                            onUpdateSoundConfig(
                                prefs.soundEffectsEnabled, prefs.soundVolume, prefs.hapticsEnabled,
                                prefs.soundNavEnabled, v, prefs.soundTaskEnabled,
                                prefs.soundCalendarEnabled, prefs.soundSuruEnabled, prefs.soundGamificationEnabled, prefs.soundCompanionEnabled
                            )
                        }
                    )
                    ToggleRow(
                        label = "Tasks (Create, Complete & Reschedule)",
                        checked = prefs.soundTaskEnabled,
                        onCheckedChange = { v ->
                            onUpdateSoundConfig(
                                prefs.soundEffectsEnabled, prefs.soundVolume, prefs.hapticsEnabled,
                                prefs.soundNavEnabled, prefs.soundInteractionEnabled, v,
                                prefs.soundCalendarEnabled, prefs.soundSuruEnabled, prefs.soundGamificationEnabled, prefs.soundCompanionEnabled
                            )
                        }
                    )
                    ToggleRow(
                        label = "Calendar & Schedule Saves",
                        checked = prefs.soundCalendarEnabled,
                        onCheckedChange = { v ->
                            onUpdateSoundConfig(
                                prefs.soundEffectsEnabled, prefs.soundVolume, prefs.hapticsEnabled,
                                prefs.soundNavEnabled, prefs.soundInteractionEnabled, prefs.soundTaskEnabled,
                                v, prefs.soundSuruEnabled, prefs.soundGamificationEnabled, prefs.soundCompanionEnabled
                            )
                        }
                    )
                    ToggleRow(
                        label = "SURU Chimes",
                        checked = prefs.soundSuruEnabled,
                        onCheckedChange = { v ->
                            onUpdateSoundConfig(
                                prefs.soundEffectsEnabled, prefs.soundVolume, prefs.hapticsEnabled,
                                prefs.soundNavEnabled, prefs.soundInteractionEnabled, prefs.soundTaskEnabled,
                                prefs.soundCalendarEnabled, v, prefs.soundGamificationEnabled, prefs.soundCompanionEnabled
                            )
                        }
                    )
                    ToggleRow(
                        label = "XP, Streaks & Level-Up",
                        checked = prefs.soundGamificationEnabled,
                        onCheckedChange = { v ->
                            onUpdateSoundConfig(
                                prefs.soundEffectsEnabled, prefs.soundVolume, prefs.hapticsEnabled,
                                prefs.soundNavEnabled, prefs.soundInteractionEnabled, prefs.soundTaskEnabled,
                                prefs.soundCalendarEnabled, prefs.soundSuruEnabled, v, prefs.soundCompanionEnabled
                            )
                        }
                    )
                    ToggleRow(
                        label = "KUTTA Companion Taps",
                        checked = prefs.soundCompanionEnabled,
                        onCheckedChange = { v ->
                            onUpdateSoundConfig(
                                prefs.soundEffectsEnabled, prefs.soundVolume, prefs.hapticsEnabled,
                                prefs.soundNavEnabled, prefs.soundInteractionEnabled, prefs.soundTaskEnabled,
                                prefs.soundCalendarEnabled, prefs.soundSuruEnabled, prefs.soundGamificationEnabled, v
                            )
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Preview Chimes", style = MaterialTheme.typography.labelSmall, color = tokens.inkSecondary)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            LifeOsSoundCue.TASK_COMPLETE to "Task Done",
                            LifeOsSoundCue.SURU_SUCCESS to "SURU",
                            LifeOsSoundCue.STREAK_ACTIVATE to "Streak",
                            LifeOsSoundCue.LEVEL_UP to "Level Up"
                        ).forEach { (cue, label) ->
                            FilterChip(
                                selected = false,
                                onClick = { onPreviewSoundCue(cue) },
                                label = { Text(label, maxLines = 1, softWrap = false) }
                            )
                        }
                        TextButton(
                            onClick = onTestSoundEffect,
                            modifier = Modifier.testTag("test_sfx_btn")
                        ) {
                            Text("Preview", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }

        // Smart 2-Hour Task Reminders & Quiet Hours
        item {
            ObsidianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Smart Task Reminders & Quiet Hours", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                        Text("Skips calendar blocks, completed tasks & quiet hours", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                    }
                }
                Spacer(Modifier.height(8.dp))
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
                    label = "Smart Pending Task Reminders (Complete / Later / Tomorrow actions)",
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
                if (prefs.taskRemindersEnabled) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Reminder Frequency (Every ${prefs.reminderFrequencyHours}h)",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.inkSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 2, 3, 4).forEach { hrs ->
                            FilterChip(
                                selected = prefs.reminderFrequencyHours == hrs,
                                onClick = { onUpdateSmartReminderPolicy(hrs, prefs.maxRemindersPerTaskPerDay) },
                                label = { Text("${hrs}h", maxLines = 1, softWrap = false) },
                                modifier = Modifier.testTag("reminder_freq_${hrs}h")
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Daily Cap per Task (Max ${prefs.maxRemindersPerTaskPerDay} / day)",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.inkSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2, 3, 5).forEach { cap ->
                            FilterChip(
                                selected = prefs.maxRemindersPerTaskPerDay == cap,
                                onClick = { onUpdateSmartReminderPolicy(prefs.reminderFrequencyHours, cap) },
                                label = { Text("${cap}/day", maxLines = 1, softWrap = false) },
                                modifier = Modifier.testTag("reminder_cap_${cap}")
                            )
                        }
                    }
                }
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
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Background checks run via Android WorkManager; system Doze mode may delay exact timing when asleep.",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.inkMuted
                )
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
                    "Local Room + DataStore persistence is active. SURU NLU, study planner, and voice work on-device (when Android Speech service supports it); optional cloud AI uses Firebase AI Logic.",
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
                    "This clears all tasks, events, habits, SURU plans/memories, friends, and XP, returning Feelora to a clean zero baseline.",
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
