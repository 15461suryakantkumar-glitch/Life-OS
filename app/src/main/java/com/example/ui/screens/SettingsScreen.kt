package com.example.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.WidgetConfigEntity
import com.example.ui.components.ObsidianCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.WidgetCustomizerList
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.ObsidianBase
import com.example.widgets.CalendarWidgetReceiver
import com.example.widgets.CountdownWidgetReceiver
import com.example.widgets.DigitalPetWidgetReceiver
import com.example.widgets.StreakWidgetReceiver
import com.example.widgets.TasksWidgetReceiver
import com.example.widgets.XpLevelWidgetReceiver
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.ThemeAccentPreset
import com.example.ui.viewmodel.LifeOsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: LifeOsUiState,
    onUpdateProfile: (String, String, Int, Int) -> Unit,
    onUpdatePetName: (String) -> Unit,
    onUpdateAccent: (ThemeAccentPreset) -> Unit,
    onUpdatePrivacy: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onUpdateNotifications: (Boolean, Boolean, Boolean) -> Unit,
    onToggleWidgetVisibility: (WidgetConfigEntity) -> Unit,
    onMoveWidget: (WidgetConfigEntity, Int) -> Unit,
    onSignOut: () -> Unit,
    onResetData: () -> Unit
) {
    val prefs = state.prefs
    var name by remember(prefs.userName) { mutableStateOf(prefs.userName) }
    var handle by remember(prefs.userHandle) { mutableStateOf(prefs.userHandle) }
    var petName by remember(prefs.petName) { mutableStateOf(prefs.petName) }
    var focusGoal by remember(prefs.dailyFocusGoalMinutes) { mutableStateOf(prefs.dailyFocusGoalMinutes.toString()) }
    var screenLimit by remember(prefs.dailyScreenLimitMinutes) { mutableStateOf(prefs.dailyScreenLimitMinutes.toString()) }

    var showWidgetSheet by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("System Settings", style = MaterialTheme.typography.headlineLarge, color = InkPrimary)
            Text("LIFE OS · Take Control. Level Up. Live Better.", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
        }

        // Personal Profile & Companion
        item {
            ObsidianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Personal Profile & Companion", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                }
                Spacer(Modifier.height(12.dp))
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
                            screenLimit.toIntOrNull() ?: 180
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_profile_btn")
                ) { Text("Save Profile") }

                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = petName,
                        onValueChange = { petName = it },
                        label = { Text("Digital Pet Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { onUpdatePetName(petName) }) {
                        Text("Rename")
                    }
                }
            }
        }

        // Theme Settings
        item {
            ObsidianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Theme Accent", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                }
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeAccentPreset.entries.forEach { preset ->
                        val selected = prefs.accentPreset == preset
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) preset.primary.copy(alpha = 0.16f) else CharcoalElevated)
                                .border(
                                    1.dp,
                                    if (selected) preset.primary else BorderHairline,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onUpdateAccent(preset) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(preset.label, style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(16.dp).clip(CircleShape).background(preset.primary))
                                Box(Modifier.size(16.dp).clip(CircleShape).background(preset.secondary))
                            }
                        }
                    }
                }
            }
        }

        // Widget Customization Shortcut
        item {
            ObsidianCard(onClick = { showWidgetSheet = true }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DashboardCustomize, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Dashboard Widget Studio", style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                            Text("${state.widgets.count { it.isVisible }}/${state.widgets.size} widgets active on Home", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                        }
                    }
                    TextButton(onClick = { showWidgetSheet = true }) { Text("Edit") }
                }
            }
        }

        // Android Home-Screen Glance Widgets Pinning
        item {
            val context = LocalContext.current
            val pinTargets = remember {
                listOf(
                    "Tasks" to TasksWidgetReceiver::class.java,
                    "Calendar" to CalendarWidgetReceiver::class.java,
                    "Countdown" to CountdownWidgetReceiver::class.java,
                    "XP & Level" to XpLevelWidgetReceiver::class.java,
                    "Streaks" to StreakWidgetReceiver::class.java,
                    "Digital Pet" to DigitalPetWidgetReceiver::class.java
                )
            }
            ObsidianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Android Home-Screen Widgets", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pin interactive Jetpack Glance widgets to your Android launcher or add them via your home screen widget picker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pinTargets.forEach { (label, receiverClass) ->
                        AssistChip(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val manager = AppWidgetManager.getInstance(context)
                                    if (manager.isRequestPinAppWidgetSupported) {
                                        manager.requestPinAppWidget(ComponentName(context, receiverClass), null, null)
                                    } else {
                                        Toast.makeText(context, "Long-press Home Screen -> Widgets -> Life OS", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Long-press Home Screen -> Widgets -> Life OS", Toast.LENGTH_SHORT).show()
                                }
                            },
                            label = { Text("Pin $label") }
                        )
                    }
                }
            }
        }

        // Notification Preferences
        item {
            ObsidianCard {
                SectionHeader(title = "Notification Preferences", subtitle = "Smart reminders & wellness nudges")
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    label = "Enable LIFE OS Alerts",
                    checked = prefs.notificationsEnabled,
                    onCheckedChange = { onUpdateNotifications(it, prefs.taskRemindersEnabled, prefs.wellnessAlertsEnabled) }
                )
                ToggleRow(
                    label = "Task & Calendar Reminders",
                    checked = prefs.taskRemindersEnabled,
                    onCheckedChange = { onUpdateNotifications(prefs.notificationsEnabled, it, prefs.wellnessAlertsEnabled) }
                )
                ToggleRow(
                    label = "Digital Wellness Budget Alerts",
                    checked = prefs.wellnessAlertsEnabled,
                    onCheckedChange = { onUpdateNotifications(prefs.notificationsEnabled, prefs.taskRemindersEnabled, it) }
                )
            }
        }

        // Privacy Settings
        item {
            ObsidianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Privacy & Social Sharing", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
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

        // Account & Data Management
        item {
            ObsidianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null, tint = InkSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Account & Architecture", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        append("Active Session: ${prefs.userName.ifBlank { "Operator" }} (${prefs.userHandle})")
                        if (prefs.friendCode.isNotBlank()) append(" · ${prefs.friendCode}")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Local Room + DataStore persistence active. Offline LIFE AI engine enabled by default with optional Firebase AI and peer Wi-Fi/invite sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth().testTag("sign_out_btn")
                ) {
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

    if (showWidgetSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWidgetSheet = false },
            containerColor = ObsidianBase
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text("Customize Home Widgets", style = MaterialTheme.typography.headlineMedium, color = InkPrimary)
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
            containerColor = ObsidianBase,
            title = { Text("Reset Local Data?") },
            text = { Text("This clears all tasks, events, habits, friends, and XP and returns LIFE OS to a clean empty slate.") },
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = InkPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
