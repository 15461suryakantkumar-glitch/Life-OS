package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.TaskEntity
import com.example.kutta.KuttaMascot
import com.example.ui.components.AnimatedStreakPill
import com.example.ui.components.FeeloraScreenBackdrop
import com.example.ui.components.FeeloraSubpageHeader
import com.example.ui.components.GradientGlowCard
import com.example.ui.components.NeonProgressBar
import com.example.ui.components.ObsidianCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FeeloraTypeHelpers
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.BadgeCatalog
import com.example.ui.viewmodel.LifeOsUiState
import com.example.util.DateTimeUtils

@Composable
fun CommandCenterScreen(
    state: LifeOsUiState,
    onBackToProfile: () -> Unit,
    onNavigateToSection: (String) -> Unit,
    onToggleTask: (TaskEntity) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    var cornerMenuExpanded by remember { mutableStateOf(false) }

    BackHandler(onBack = onBackToProfile)

    val today = state.todayEpochDay
    val countdowns = remember(state.events, today) {
        state.events.filter { it.isCountdown && it.dateEpochDay >= today }.sortedBy { it.dateEpochDay }
    }
    val priorityTasks = remember(state.tasks, today) {
        state.tasks.filter { !it.isCompleted }
            .sortedWith(
                compareByDescending<TaskEntity> { it.deadlineEpochDay < today }
                    .thenByDescending { it.priority.ordinal }
                    .thenBy { it.deadlineEpochDay }
            )
            .take(5)
    }

    FeeloraScreenBackdrop(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("command_center_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FeeloraSubpageHeader(
                    title = "Personal Command Center",
                    subtitle = "Deadlines, study mastery, streak & track record",
                    onBack = onBackToProfile,
                    trailing = {
                        Box {
                            IconButton(
                                onClick = { cornerMenuExpanded = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("command_center_corner_menu_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = "Command Center menu",
                                    tint = tokens.inkPrimary
                                )
                            }
                            DropdownMenu(
                                expanded = cornerMenuExpanded,
                                onDismissRequest = { cornerMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Study") },
                                    leadingIcon = { Icon(Icons.Filled.MenuBook, contentDescription = null) },
                                    onClick = {
                                        cornerMenuExpanded = false
                                        onNavigateToSection("study")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Tasks") },
                                    leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                                    onClick = {
                                        cornerMenuExpanded = false
                                        onNavigateToSection("tasks")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Calendar") },
                                    leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                                    onClick = {
                                        cornerMenuExpanded = false
                                        onNavigateToSection("calendar")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Wellness") },
                                    leadingIcon = { Icon(Icons.Filled.HealthAndSafety, contentDescription = null) },
                                    onClick = {
                                        cornerMenuExpanded = false
                                        onNavigateToSection("wellness")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("SURU AI") },
                                    leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                                    onClick = {
                                        cornerMenuExpanded = false
                                        onNavigateToSection("life_ai")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("KUTTA Sanctuary") },
                                    leadingIcon = { Icon(Icons.Filled.Pets, contentDescription = null) },
                                    onClick = {
                                        cornerMenuExpanded = false
                                        onNavigateToSection("growth")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Notifications") },
                                    leadingIcon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                                    onClick = {
                                        cornerMenuExpanded = false
                                        onNavigateToSection("notifications")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                    onClick = {
                                        cornerMenuExpanded = false
                                        onNavigateToSection("settings")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Back to Profile") },
                                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                    onClick = {
                                        cornerMenuExpanded = false
                                        onBackToProfile()
                                    }
                                )
                            }
                        }
                    }
                )
            }

            // 1. Streak, XP & Level Hero (18894.png)
            item {
                val prefs = state.prefs
                val xpRatio = (prefs.xpIntoCurrentLevel.toFloat() / prefs.xpPerLevel.coerceAtLeast(1)).coerceIn(0f, 1f)
                val studyMasteryPct = if (state.studyChapters.isEmpty()) {
                    state.productivityScore
                } else {
                    ((state.completedChaptersCount * 100f) / state.studyChapters.size).toInt().coerceIn(0, 100)
                }
                GradientGlowCard(contentPadding = PaddingValues(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("YOU'RE ON FIRE!", style = FeeloraTypeHelpers.editorialTag, color = tokens.primary)
                            Spacer(Modifier.height(4.dp))
                            Text("Level ${prefs.level}", style = FeeloraTypeHelpers.statNumber, color = tokens.inkPrimary)
                            Text(
                                "${prefs.dailyStreak} day streak · ${prefs.totalXp} XP total",
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                color = tokens.inkSecondary
                            )
                        }
                        AnimatedStreakPill(streakDays = prefs.dailyStreak)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TrackStatPill("Study", "$studyMasteryPct%")
                        TrackStatPill("Tasks", "${state.completedTasksCount}/${state.tasks.size}")
                        TrackStatPill("XP", "${prefs.totalXp}")
                    }
                    Spacer(Modifier.height(10.dp))
                    NeonProgressBar(progress = xpRatio, heightDp = 8)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${prefs.xpIntoCurrentLevel}/${prefs.xpPerLevel} XP to Level ${prefs.level + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.inkSecondary
                    )
                }
            }

            // 2. Urgent Deadlines & Countdowns
            item {
                SectionHeader(title = "Urgent Deadlines & Countdowns", subtitle = "Upcoming milestones")
            }
            if (countdowns.isEmpty()) {
                item {
                    ObsidianCard {
                        Text(
                            "No countdown deadlines scheduled. Add milestone countdowns in Calendar to track exam or project days remaining.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.inkSecondary
                        )
                    }
                }
            } else {
                items(countdowns.take(4), key = { "cc_cd_${it.id}" }) { event ->
                    val daysLeft = (event.dateEpochDay - today).coerceAtLeast(0L)
                    ObsidianCard(onClick = { onNavigateToSection("calendar") }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(event.title, style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                                Text(DateTimeUtils.formatFullDate(event.dateEpochDay), style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                            }
                            StatusPill(
                                text = if (daysLeft == 0L) "Today" else "${daysLeft}d left",
                                color = if (daysLeft <= 2L) RoseAlert else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 3. Remaining Priority Tasks
            item {
                SectionHeader(title = "Priority Tasks", subtitle = "${state.pendingTasksCount} open · ${state.completedTasksCount} done")
            }
            if (priorityTasks.isEmpty()) {
                item {
                    ObsidianCard {
                        Text("All caught up — zero remaining tasks right now.", style = MaterialTheme.typography.bodyMedium, color = EmeraldSuccess)
                    }
                }
            } else {
                items(priorityTasks, key = { "cc_task_${it.id}" }) { task ->
                    ObsidianCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onToggleTask(task) }, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = "Complete ${task.title}",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(task.title, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${task.priority.label} · ${DateTimeUtils.formatRelativeDeadline(task.deadlineEpochDay)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (task.deadlineEpochDay < today) RoseAlert else tokens.inkSecondary
                                )
                            }
                            StatusPill(text = "+${task.xpReward} XP", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }

            // 4. Study Progress Overview
            item {
                SectionHeader(title = "Study Progress", subtitle = "${state.completedChaptersCount}/${state.studyChapters.size} chapters completed")
            }
            item {
                ObsidianCard(onClick = { onNavigateToSection("study") }) {
                    if (state.studySubjects.isEmpty()) {
                        Text(
                            "No subjects tracked yet. Open Study from the corner menu to organize subjects & chapters.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.inkSecondary
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.studySubjects.forEach { subj ->
                                val pct = (state.progressForSubject(subj.id) * 100).toInt()
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(subj.name, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                        Text("$pct%", style = MaterialTheme.typography.labelMedium, color = tokens.deepPrimary)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    NeonProgressBar(progress = state.progressForSubject(subj.id), heightDp = 6)
                                }
                            }
                        }
                    }
                }
            }

            // 5. KUTTA Companion Sanctuary Card
            item {
                ObsidianCard(onClick = { onNavigateToSection("growth") }, elevated = true) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        KuttaMascot(visualState = state.kuttaState, size = 72.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("KUTTA Sanctuary", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary, fontWeight = FontWeight.Bold)
                                StatusPill(text = state.kuttaState.emotion.label, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(state.kuttaState.dialogue, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Mood ${state.prefs.petHappiness}% · Energy ${state.prefs.petEnergy}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.inkMuted
                            )
                        }
                    }
                }
            }

            // 6. Track Record & Achievement Badges
            item {
                val weekly = state.weeklyAnalysis
                ObsidianCard {
                    SectionHeader(title = "Personal Track Record", subtitle = "7-day cumulative rhythm")
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TrackStatPill("7d Focus", DateTimeUtils.formatMinutesCompact(weekly.weeklyFocusMinutes))
                        TrackStatPill("Tasks Done", "${weekly.weeklyTasksCompleted}")
                        TrackStatPill("Active Days", "${weekly.activeLoginDaysCount}/7")
                    }
                }
            }

            item {
                ObsidianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = AmberWarning)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Achievement Badges (${state.prefs.unlockedBadges.size}/${BadgeCatalog.all.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.inkPrimary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BadgeCatalog.all.forEach { badge ->
                            val unlocked = state.prefs.unlockedBadges.contains(badge.id)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (unlocked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (unlocked) EmeraldSuccess else tokens.inkMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(badge.title, style = MaterialTheme.typography.titleSmall, color = if (unlocked) tokens.inkPrimary else tokens.inkMuted)
                                    Text(badge.description, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                                }
                                if (unlocked) {
                                    StatusPill(text = "Unlocked", color = EmeraldSuccess)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun TrackStatPill(label: String, value: String) {
    val tokens = LifeOsThemeTokensProvider.current
    Column(horizontalAlignment = Alignment.Start) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.inkSecondary)
    }
}
