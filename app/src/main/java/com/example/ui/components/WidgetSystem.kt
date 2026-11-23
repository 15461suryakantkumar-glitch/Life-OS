package com.example.ui.components

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.MainActivity
import com.example.data.local.DashboardWidgetType
import com.example.data.local.HabitEntity
import com.example.data.local.TaskEntity
import com.example.data.local.WidgetConfigEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.viewmodel.LifeOsUiState
import com.example.util.DateTimeUtils
import com.example.widgets.CalendarWidgetReceiver
import com.example.widgets.CountdownWidgetReceiver
import com.example.widgets.DigitalPetWidgetReceiver
import com.example.widgets.StreakWidgetReceiver
import com.example.widgets.TasksWidgetReceiver
import com.example.widgets.XpLevelWidgetReceiver
import java.util.Locale

@Composable
fun DashboardWidgetCard(
    type: DashboardWidgetType,
    state: LifeOsUiState,
    onToggleTask: (TaskEntity) -> Unit,
    onToggleHabit: (HabitEntity) -> Unit,
    onSelectTimerPreset: (Int) -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onFinishTimerEarly: () -> Unit,
    onUpdateNote: (String) -> Unit,
    onInteractPet: (String) -> Unit,
    onNavigateToSection: (String) -> Unit
) {
    when (type) {
        DashboardWidgetType.TASKS -> TasksWidget(state, onToggleTask) { onNavigateToSection("tasks") }
        DashboardWidgetType.CALENDAR -> CalendarWidget(state) { onNavigateToSection("calendar") }
        DashboardWidgetType.COUNTDOWN -> CountdownWidget(state) { onNavigateToSection("calendar") }
        DashboardWidgetType.HABIT_TRACKER -> HabitWidget(state, onToggleHabit) { onNavigateToSection("growth") }
        DashboardWidgetType.STUDY_TIMER -> StudyTimerWidget(
            state = state,
            onSelectPreset = onSelectTimerPreset,
            onToggle = onToggleTimer,
            onReset = onResetTimer,
            onFinishEarly = onFinishTimerEarly
        )
        DashboardWidgetType.PRODUCTIVITY_SCORE -> ProductivityScoreWidget(state)
        DashboardWidgetType.XP_TRACKER -> XpTrackerWidget(state) { onNavigateToSection("growth") }
        DashboardWidgetType.DIGITAL_PET -> DigitalPetWidget(state, onInteractPet) { onNavigateToSection("growth") }
        DashboardWidgetType.QUICK_NOTES -> QuickNotesWidget(state.quickNote, onUpdateNote)
        DashboardWidgetType.DAILY_STATS -> DailyStatsWidget(state)
    }
}

@Composable
private fun TasksWidget(
    state: LifeOsUiState,
    onToggleTask: (TaskEntity) -> Unit,
    onOpenTasks: () -> Unit
) {
    val visibleTasks = state.todayTasks.take(4)
    val pendingCount = state.tasks.count { !it.isCompleted }
    val sectionById = remember(state.taskSections) { state.taskSections.associateBy { it.id } }
    ObsidianCard(modifier = Modifier.testTag("widget_tasks")) {
        SectionHeader(
            title = "Today's Tasks",
            subtitle = if (state.tasks.isEmpty()) "No tasks yet" else "$pendingCount pending · Tap to complete",
            trailing = {
                TextButton(
                    onClick = onOpenTasks,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("widget_tasks_all_btn")
                ) {
                    Text(if (state.tasks.isEmpty()) "Open" else "All", maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(15.dp))
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        if (visibleTasks.isEmpty()) {
            Text(
                "Clean slate. Create a section in Tasks or ask LIFE AI to add a task.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                visibleTasks.forEach { task ->
                    val rowBg by animateColorAsState(
                        targetValue = if (task.isCompleted) EmeraldSuccess.copy(alpha = 0.08f) else CharcoalElevated.copy(alpha = 0.55f),
                        animationSpec = tween(240),
                        label = "widget_task_bg"
                    )
                    val checkScale by animateFloatAsState(
                        targetValue = if (task.isCompleted) 1.12f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "widget_task_scale"
                    )
                    val contentAlpha by animateFloatAsState(
                        targetValue = if (task.isCompleted) 0.65f else 1f,
                        animationSpec = tween(240),
                        label = "widget_task_alpha"
                    )
                    val secName = sectionById[task.sectionId]?.name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(rowBg)
                            .clickable { onToggleTask(task) }
                            .padding(vertical = 8.dp, horizontal = 10.dp)
                            .graphicsLayer { alpha = contentAlpha },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = if (task.isCompleted) "Completed" else "Pending",
                            tint = if (task.isCompleted) EmeraldSuccess else MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer {
                                    scaleX = checkScale
                                    scaleY = checkScale
                                }
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (task.isCompleted) InkMuted else InkPrimary,
                                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(secName ?: task.category.label)
                                    append(" · +${task.xpReward} XP")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        PriorityBadge(task.priority)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarWidget(
    state: LifeOsUiState,
    onOpenCalendar: () -> Unit
) {
    val today = state.todayEpochDay
    val upcoming = state.events
        .filter { !it.isCountdown && it.dateEpochDay >= today }
        .take(3)
    ObsidianCard(modifier = Modifier.testTag("widget_calendar")) {
        SectionHeader(
            title = "Upcoming Schedule",
            subtitle = DateTimeUtils.formatFullDate(today),
            trailing = {
                TextButton(onClick = onOpenCalendar) {
                    Text("Calendar")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        )
        Spacer(Modifier.height(10.dp))
        if (upcoming.isEmpty()) {
            Text("No upcoming blocks scheduled.", style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
        } else {
            upcoming.forEach { ev ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ev.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = InkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${DateTimeUtils.formatFullDate(ev.dateEpochDay)} · ${DateTimeUtils.formatTimeOfDay(ev.startMinuteOfDay)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSecondary
                        )
                    }
                    StatusPill(text = "${ev.durationMinutes}m", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun CountdownWidget(
    state: LifeOsUiState,
    onOpenCalendar: () -> Unit
) {
    val today = state.todayEpochDay
    val countdowns = state.events.filter { it.isCountdown && it.dateEpochDay >= today }.take(3)
    ObsidianCard(modifier = Modifier.testTag("widget_countdown")) {
        SectionHeader(
            title = "Milestone Countdowns",
            subtitle = "Important upcoming dates",
            trailing = {
                IconButton(onClick = onOpenCalendar) {
                    Icon(Icons.Filled.Flag, contentDescription = "Open countdowns", tint = AmberWarning)
                }
            }
        )
        Spacer(Modifier.height(10.dp))
        if (countdowns.isEmpty()) {
            Text("Add an important countdown in Calendar.", style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                countdowns.forEach { item ->
                    val daysLeft = DateTimeUtils.daysBetween(today, item.dateEpochDay).coerceAtLeast(0)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CharcoalElevated)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "${daysLeft}d",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = InkPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitWidget(
    state: LifeOsUiState,
    onToggleHabit: (HabitEntity) -> Unit,
    onOpenGrowth: () -> Unit
) {
    val today = state.todayEpochDay
    val completedCount = state.habits.count { it.isCompletedOn(today) }
    val total = state.habits.size.coerceAtLeast(1)
    ObsidianCard(modifier = Modifier.testTag("widget_habits")) {
        SectionHeader(
            title = "Habit Pulse",
            subtitle = "$completedCount / ${state.habits.size} locked in today",
            trailing = {
                TextButton(onClick = onOpenGrowth) { Text("Streaks") }
            }
        )
        Spacer(Modifier.height(8.dp))
        NeonProgressBar(progress = completedCount.toFloat() / total)
        Spacer(Modifier.height(12.dp))
        if (state.habits.isEmpty()) {
            Text(
                text = "No habits added yet. Tap Streaks to create your first daily habit.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.habits.take(4).forEach { habit ->
                    val done = habit.isCompletedOn(today)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (done) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else CharcoalElevated)
                            .clickable { onToggleHabit(habit) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (done) EmeraldSuccess else InkSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = habit.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        StatusPill(
                            text = "${habit.currentStreak}d",
                            color = AmberWarning,
                            icon = Icons.Outlined.LocalFireDepartment
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyTimerWidget(
    state: LifeOsUiState,
    onSelectPreset: (Int) -> Unit,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onFinishEarly: () -> Unit
) {
    val timer = state.studyTimer
    val mins = timer.remainingSeconds / 60
    val secs = timer.remainingSeconds % 60
    val formatted = String.format(Locale.US, "%02d:%02d", mins, secs)
    val totalSecs = (timer.selectedMinutes * 60).coerceAtLeast(1)
    val progress = 1f - (timer.remainingSeconds.toFloat() / totalSecs)

    GradientGlowCard(
        modifier = Modifier.testTag("widget_study_timer"),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Focus Timer", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = formatted,
                    style = MaterialTheme.typography.headlineMedium,
                    color = InkPrimary,
                    fontWeight = FontWeight.Bold
                )
                StatusPill(
                    text = if (timer.isRunning) "ACTIVE" else "READY",
                    color = if (timer.isRunning) EmeraldSuccess else MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        NeonProgressBar(progress = progress, heightDp = 5)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(15, 25, 45, 60).forEach { preset ->
                FilterChip(
                    selected = timer.selectedMinutes == preset,
                    onClick = { onSelectPreset(preset) },
                    label = { Text("${preset}m", maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                        selectedLabelColor = InkPrimary
                    )
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onToggle,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f).height(40.dp).testTag("study_timer_toggle_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = if (timer.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (timer.isRunning) "Pause" else "Start Focus", maxLines = 1)
            }
            OutlinedButton(
                onClick = onFinishEarly,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(40.dp).testTag("study_timer_log_btn")
            ) {
                Text("Log", maxLines = 1)
            }
            FilledTonalIconButton(
                onClick = onReset,
                modifier = Modifier.size(40.dp).testTag("study_timer_reset_btn")
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reset timer", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ProductivityScoreWidget(state: LifeOsUiState) {
    val score by animateIntAsState(
        targetValue = state.productivityScore,
        animationSpec = tween(450),
        label = "widget_prod_score"
    )
    val rating = when {
        score >= 85 -> "Peak Command"
        score >= 65 -> "Strong Momentum"
        score >= 45 -> "Steady Progress"
        score > 0 -> "Warming Up"
        else -> "Standby"
    }
    ObsidianCard(modifier = Modifier.testTag("widget_productivity_score"), elevated = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Productivity Score", style = MaterialTheme.typography.titleSmall, color = InkSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$score / 100",
                    style = MaterialTheme.typography.headlineLarge,
                    color = InkPrimary
                )
                Spacer(Modifier.height(4.dp))
                StatusPill(text = rating, color = MaterialTheme.colorScheme.secondary, icon = Icons.Filled.Bolt)
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${score}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        NeonProgressBar(progress = score / 100f, heightDp = 6)
    }
}

@Composable
private fun XpTrackerWidget(state: LifeOsUiState, onOpenGrowth: () -> Unit) {
    val prefs = state.prefs
    val animatedXp by animateIntAsState(
        targetValue = prefs.totalXp,
        animationSpec = tween(420),
        label = "widget_xp_counter"
    )
    ObsidianCard(
        modifier = Modifier.testTag("widget_xp_tracker"),
        onClick = onOpenGrowth
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Level ${prefs.level} Operator", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                Text("$animatedXp Total XP · ${prefs.xpPerLevel - prefs.xpIntoCurrentLevel} XP to Lv.${prefs.level + 1}", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            }
            Spacer(Modifier.width(8.dp))
            AnimatedStreakPill(streakDays = prefs.dailyStreak)
        }
        Spacer(Modifier.height(10.dp))
        NeonProgressBar(progress = prefs.xpIntoCurrentLevel.toFloat() / prefs.xpPerLevel, heightDp = 6)
    }
}

@Composable
private fun DigitalPetWidget(
    state: LifeOsUiState,
    onInteractPet: (String) -> Unit,
    onOpenGrowth: () -> Unit
) {
    val prefs = state.prefs
    val animatedHappiness by animateIntAsState(
        targetValue = prefs.petHappiness,
        animationSpec = tween(350),
        label = "pet_widget_happiness"
    )
    val animatedEnergy by animateIntAsState(
        targetValue = prefs.petEnergy,
        animationSpec = tween(350),
        label = "pet_widget_energy"
    )
    ObsidianCard(
        modifier = Modifier.testTag("widget_digital_pet"),
        elevated = true,
        onClick = onOpenGrowth
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DigitalPetCanvas(
                petName = prefs.petName,
                stage = prefs.petStage,
                happiness = prefs.petHappiness,
                energy = prefs.petEnergy,
                accessory = prefs.equippedAccessory,
                size = 76.dp,
                onTap = { onInteractPet("TAP") }
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(prefs.petName, style = MaterialTheme.typography.titleMedium, color = InkPrimary, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    StatusPill(text = prefs.petStage.title, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(4.dp))
                Text("Mood $animatedHappiness% · Energy $animatedEnergy%", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onInteractPet("FEED") },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Charge", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onInteractPet("PLAY") },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cheer", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickNotesWidget(
    note: String,
    onUpdateNote: (String) -> Unit
) {
    var draft by remember(note) { mutableStateOf(note) }
    ObsidianCard(modifier = Modifier.testTag("widget_quick_notes")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(8.dp))
            Text("Quick Notes", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = {
                draft = it
                onUpdateNote(it)
            },
            modifier = Modifier.fillMaxWidth().testTag("quick_notes_input"),
            placeholder = { Text("Capture a thought, formula, or reminder...") },
            minLines = 2,
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = BorderHairline,
                focusedTextColor = InkPrimary,
                unfocusedTextColor = InkPrimary
            )
        )
    }
}

@Composable
private fun DailyStatsWidget(state: LifeOsUiState) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("widget_daily_stats"),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricTile(
            label = "Tasks",
            value = "${state.completedTasksCount}/${state.tasks.size}",
            accent = EmeraldSuccess,
            icon = Icons.Filled.CheckCircle,
            modifier = Modifier.weight(1f)
        )
        MetricTile(
            label = "Focus",
            value = DateTimeUtils.formatMinutesCompact(state.prefs.focusMinutesLoggedToday),
            accent = MaterialTheme.colorScheme.secondary,
            icon = Icons.Filled.Timer,
            modifier = Modifier.weight(1f)
        )
        MetricTile(
            label = "Screen",
            value = DateTimeUtils.formatMinutesCompact(state.totalScreenMinutesToday),
            accent = MaterialTheme.colorScheme.primary,
            icon = Icons.Filled.AutoAwesome,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun WidgetCustomizerList(
    widgets: List<WidgetConfigEntity>,
    onToggleVisible: (WidgetConfigEntity) -> Unit,
    onMoveUp: (WidgetConfigEntity) -> Unit,
    onMoveDown: (WidgetConfigEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        widgets.sortedBy { it.sortOrder }.forEachIndexed { index, config ->
            val meta = config.type ?: return@forEachIndexed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalElevated)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(meta.title, style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                    Text(meta.subtitle, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                }
                IconButton(
                    onClick = { onMoveUp(config) },
                    enabled = index > 0
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(
                    onClick = { onMoveDown(config) },
                    enabled = index < widgets.lastIndex
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                }
                Switch(
                    checked = config.isVisible,
                    onCheckedChange = { onToggleVisible(config) }
                )
            }
        }
    }
}

private data class GlancePinTarget(
    val key: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accent: Color,
    val receiverClass: Class<*>
)

@Composable
fun GlanceWidgetPinningSection(
    petName: String,
    onNotice: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var inlineFeedback by remember { mutableStateOf<String?>(null) }

    val pinTargets = remember(petName) {
        listOf(
            GlancePinTarget(
                key = "tasks",
                title = "Tasks",
                description = "Interactive task checklist & XP sync",
                icon = Icons.Filled.CheckCircle,
                accent = EmeraldSuccess,
                receiverClass = TasksWidgetReceiver::class.java
            ),
            GlancePinTarget(
                key = "countdown",
                title = "Countdown",
                description = "Upcoming milestone days remaining",
                icon = Icons.Filled.Flag,
                accent = AmberWarning,
                receiverClass = CountdownWidgetReceiver::class.java
            ),
            GlancePinTarget(
                key = "xp",
                title = "XP & Level",
                description = "Total XP, level progress & score",
                icon = Icons.Filled.Bolt,
                accent = Color(0xFF8B5CF6),
                receiverClass = XpLevelWidgetReceiver::class.java
            ),
            GlancePinTarget(
                key = "streak",
                title = "Streak",
                description = "Daily streak & habit pulse",
                icon = Icons.Outlined.LocalFireDepartment,
                accent = AmberWarning,
                receiverClass = StreakWidgetReceiver::class.java
            ),
            GlancePinTarget(
                key = "pet",
                title = "${petName.ifBlank { "NOVA" }} Digital Pet",
                description = "Interactive companion mood & energy",
                icon = Icons.Filled.Pets,
                accent = Color(0xFF06B6D4),
                receiverClass = DigitalPetWidgetReceiver::class.java
            ),
            GlancePinTarget(
                key = "calendar",
                title = "Calendar",
                description = "Daily schedule & time blocks",
                icon = Icons.Filled.CalendarMonth,
                accent = Color(0xFF38BDF8),
                receiverClass = CalendarWidgetReceiver::class.java
            )
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pinTargets.forEach { target ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalElevated)
                    .border(1.dp, BorderHairline, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(target.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = target.icon,
                        contentDescription = null,
                        tint = target.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = target.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = InkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = target.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = {
                        val msg = requestPinGlanceWidget(context, target.title, target.receiverClass)
                        inlineFeedback = msg
                        onNotice(msg)
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp).testTag("pin_widget_${target.key}")
                ) {
                    Text("Pin to Home", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = CharcoalElevated.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, BorderHairline)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = inlineFeedback
                        ?: "If your launcher restricts direct pinning, long-press your Android home screen -> Widgets -> Life OS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
            }
        }
    }
}

private fun requestPinGlanceWidget(
    context: Context,
    title: String,
    receiverClass: Class<*>
): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = AppWidgetManager.getInstance(context)
        if (manager.isRequestPinAppWidgetSupported) {
            val callback = PendingIntent.getActivity(
                context,
                receiverClass.name.hashCode(),
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val launched = manager.requestPinAppWidget(
                ComponentName(context, receiverClass),
                null,
                callback
            )
            if (launched) {
                "Pin prompt sent for $title. Confirm on your home screen, or add manually via Home screen -> Widgets -> Life OS."
            } else {
                "Direct pinning was declined or blocked by your launcher. Long-press your home screen -> Widgets -> Life OS."
            }
        } else {
            "This launcher does not support direct widget pinning. Long-press your home screen -> Widgets -> Life OS."
        }
    } else {
        "Direct widget pinning requires Android 8.0+. Long-press your home screen -> Widgets -> Life OS."
    }
}
