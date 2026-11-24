package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CompanionSpecies
import com.example.data.local.UserAvatarPreset
import com.example.data.local.UserPreferencesRepository
import com.example.ui.components.AnimatedStreakPill
import com.example.ui.components.DigitalPetCanvas
import com.example.ui.components.GradientGlowCard
import com.example.ui.components.NeonProgressBar
import com.example.ui.components.ObsidianCard
import com.example.ui.components.RetakePersonalityQuizDialog
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.UserAvatarBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.LifeOsUiState
import com.example.util.DateTimeUtils

@Composable
fun ProfileScreen(
    state: LifeOsUiState,
    myShareToken: String,
    myInviteLink: String,
    onBack: () -> Unit,
    onUpdateProfile: (String, String, Int, Int, UserAvatarPreset) -> Unit,
    onSelectAvatar: (UserAvatarPreset) -> Unit,
    onReappointCompanion: (CompanionSpecies, String?) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val prefs = state.prefs

    var name by remember(prefs.userName) { mutableStateOf(prefs.userName) }
    var handle by remember(prefs.userHandle) { mutableStateOf(prefs.userHandle) }
    var focusGoal by remember(prefs.dailyFocusGoalMinutes) { mutableStateOf(prefs.dailyFocusGoalMinutes.toString()) }
    var screenLimit by remember(prefs.dailyScreenLimitMinutes) { mutableStateOf(prefs.dailyScreenLimitMinutes.toString()) }
    var analyzerTab by remember { mutableIntStateOf(0) } // 0 = Daily, 1 = Weekly
    var showRetakeQuiz by remember { mutableStateOf(false) }

    val friendId = remember(prefs.friendCode, prefs.userHandle, prefs.userName) {
        prefs.friendCode.ifBlank {
            UserPreferencesRepository.generateFriendCode(prefs.userHandle, prefs.userName)
        }
    }

    val dailyReport = state.dailyAnalysis
    val weeklyReport = state.weeklyAnalysis

    fun shareFriendId() {
        val summary = buildString {
            appendLine("Add me on LIFE OS!")
            appendLine("Friend ID: $friendId")
            appendLine("Name: ${prefs.userName.ifBlank { "Explorer" }} (${prefs.userHandle})")
            appendLine()
            append("Invite link: $myInviteLink")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "LIFE OS Friend ID · $friendId")
            putExtra(Intent.EXTRA_TEXT, summary)
        }
        context.startActivity(Intent.createChooser(intent, "Share Friend ID"))
    }

    // Prepare 7-day progress series
    val today = state.todayEpochDay
    val progressByDay = remember(state.dailyProgressLogs) {
        state.dailyProgressLogs.associateBy { it.epochDay }
    }
    val weeklySeries = remember(today, progressByDay, state.tasks, state.usageLogs, prefs.focusMinutesLoggedToday) {
        (6 downTo 0).map { offset ->
            val day = today - offset
            val log = progressByDay[day]
            val dayDist = state.usageLogs
                .filter { it.dateEpochDay == day && it.isDistracting }
                .sumOf { it.minutesUsed }
            val xp = log?.xpEarned ?: 0
            val focus = if (day == today) maxOf(log?.focusMinutes ?: 0, prefs.focusMinutesLoggedToday) else (log?.focusMinutes ?: 0)
            val dist = maxOf(log?.distractingMinutes ?: 0, dayDist)
            Triple(DateTimeUtils.formatFullDate(day).substringBefore(","), xp + focus, dist)
        }
    }
    val loginWindow14 = remember(today, prefs.loginEpochDays) {
        (13 downTo 0).map { offset ->
            val day = today - offset
            day to (day == today || prefs.loginEpochDays.contains(day))
        }
    }
    val distractingTasks = remember(state.tasks) {
        state.tasks.filter { it.distractionMinutes > 0 }.sortedByDescending { it.distractionMinutes }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
            .testTag("profile_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("profile_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = tokens.inkPrimary
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Profile & Analytics", style = MaterialTheme.typography.headlineMedium, color = tokens.inkPrimary)
                    Text("Identity, companion match, insights & charts", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                }
                AnimatedStreakPill(streakDays = prefs.dailyStreak)
            }
        }

        // Identity + Friend ID + 3D Avatar Card
        item {
            GradientGlowCard(contentPadding = PaddingValues(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAvatarBadge(preset = prefs.avatarPreset, size = 60.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = prefs.userName.ifBlank { "Explorer" },
                            style = MaterialTheme.typography.headlineMedium,
                            color = tokens.inkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${prefs.userHandle.ifBlank { "@explorer" }} · Level ${prefs.level} (${prefs.totalXp} XP)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Proofread copyable / shareable Friend ID
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(tokens.smallRadius))
                        .background(tokens.surfaceBase.copy(alpha = 0.72f))
                        .border(1.dp, tokens.borderSubtle, RoundedCornerShape(tokens.smallRadius))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Friend ID", style = MaterialTheme.typography.labelSmall, color = tokens.inkSecondary)
                        Text(
                            text = friendId,
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.inkPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(friendId))
                            onShowMessage("Friend ID copied: $friendId")
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp).testTag("copy_friend_id_btn")
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy ID", maxLines = 1, softWrap = false)
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = { shareFriendId() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share Friend ID", tint = MaterialTheme.colorScheme.secondary)
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text("Choose Avatar", style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UserAvatarPreset.entries.forEach { preset ->
                        val selected = prefs.avatarPreset == preset
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(tokens.smallRadius))
                                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else tokens.borderSubtle,
                                    shape = RoundedCornerShape(tokens.smallRadius)
                                )
                                .clickable { onSelectAvatar(preset) }
                                .padding(8.dp)
                        ) {
                            UserAvatarBadge(preset = preset, size = 44.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = preset.label.substringBefore(" "),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.primary else tokens.inkSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Appointed Companion Card + Retake Match (shows ONLY appointed companion)
        item {
            ObsidianCard(elevated = true) {
                SectionHeader(
                    title = "Your Companion",
                    subtitle = "${prefs.companionSpecies.displayTitle} · ${prefs.companionSpecies.archetypeTag}",
                    trailing = {
                        OutlinedButton(
                            onClick = { showRetakeQuiz = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp).testTag("retake_quiz_btn")
                        ) {
                            Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retake Match", maxLines = 1, softWrap = false)
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
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
                        species = prefs.companionSpecies,
                        mood = state.companionMood,
                        size = 86.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(prefs.petName, style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                            StatusPill(text = state.companionMood.label, color = MaterialTheme.colorScheme.secondary)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(state.companionMood.nudgeHint, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                    }
                }
            }
        }

        // Automated Daily & Weekly Analyzer
        item {
            ObsidianCard(borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Daily & Weekly Analyzer", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = analyzerTab == 0,
                        onClick = { analyzerTab = 0 },
                        label = { Text("Daily Review", maxLines = 1, softWrap = false) }
                    )
                    FilterChip(
                        selected = analyzerTab == 1,
                        onClick = { analyzerTab = 1 },
                        label = { Text("Weekly Insights", maxLines = 1, softWrap = false) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (analyzerTab == 0) {
                    StatusPill(text = dailyReport.headline, color = MaterialTheme.colorScheme.primary, icon = Icons.Filled.AutoAwesome)
                    Spacer(Modifier.height(6.dp))
                    Text(dailyReport.summary, style = MaterialTheme.typography.bodyMedium, color = tokens.inkPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Tasks: ${state.completedTasksCount}/${state.tasks.size} · Focus: ${prefs.focusMinutesLoggedToday}m · Distraction: ${dailyReport.distractionRatioPercent}% · Over 2h pending: ${dailyReport.agedPendingCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.inkSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    dailyReport.recommendations.forEach { rec ->
                        Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(rec, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                        }
                    }
                } else {
                    StatusPill(text = weeklyReport.headline, color = MaterialTheme.colorScheme.secondary, icon = Icons.Filled.AutoAwesome)
                    Spacer(Modifier.height(6.dp))
                    Text(weeklyReport.summary, style = MaterialTheme.typography.bodyMedium, color = tokens.inkPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "7d Logins: ${weeklyReport.activeLoginDaysCount}/7 · Done: ${weeklyReport.weeklyTasksCompleted} · Focus: ${weeklyReport.weeklyFocusMinutes}m · Distracting: ${weeklyReport.weeklyDistractingMinutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.inkSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    weeklyReport.recommendations.forEach { rec ->
                        Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(rec, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                        }
                    }
                }
            }
        }

        // Graph 1: 7-Day Overall Progress Canvas Graph
        item {
            val primary = MaterialTheme.colorScheme.primary
            val secondary = MaterialTheme.colorScheme.secondary
            val maxProgress = (weeklySeries.maxOfOrNull { it.second } ?: 60).coerceAtLeast(60).toFloat()

            ObsidianCard {
                SectionHeader(title = "Overall Progress (7 Days)", subtitle = "Combined XP + focus momentum per day")
                Spacer(Modifier.height(12.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    val slotW = size.width / weeklySeries.size
                    val barW = slotW * 0.48f
                    weeklySeries.forEachIndexed { index, (_, value, _) ->
                        val ratio = if (value <= 0) 0.04f else (value / maxProgress).coerceIn(0.08f, 1f)
                        val barH = size.height * ratio
                        val left = index * slotW + (slotW - barW) / 2f
                        drawRoundRect(
                            brush = if (value <= 0) {
                                Brush.verticalGradient(listOf(tokens.borderSubtle, tokens.borderSubtle))
                            } else {
                                Brush.verticalGradient(listOf(secondary, primary))
                            },
                            topLeft = Offset(left, size.height - barH),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(12f, 12f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    weeklySeries.forEach { (label, valScore, _) ->
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.inkSecondary)
                            Text("$valScore", style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
                        }
                    }
                }
            }
        }

        // Graph 2: 14-Day Login Days Consistency Canvas Graph
        item {
            val activeCount = loginWindow14.count { it.second }
            ObsidianCard {
                SectionHeader(title = "Login Days (14-Day Activity)", subtitle = "$activeCount of 14 days active")
                Spacer(Modifier.height(12.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    val slotW = size.width / loginWindow14.size
                    val cellW = slotW * 0.68f
                    loginWindow14.forEachIndexed { idx, (_, active) ->
                        val left = idx * slotW + (slotW - cellW) / 2f
                        val barH = if (active) size.height * 0.86f else size.height * 0.30f
                        drawRoundRect(
                            color = if (active) EmeraldSuccess else tokens.borderSubtle,
                            topLeft = Offset(left, size.height - barH),
                            size = Size(cellW, barH),
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("14d ago", style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
                    Text("Today", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess)
                }
            }
        }

        // Graph 3: Distraction Graph (7-Day + Per-Task Breakdown)
        item {
            val maxDist = (weeklySeries.maxOfOrNull { it.third } ?: 45).coerceAtLeast(45).toFloat()
            ObsidianCard {
                SectionHeader(title = "Distraction Graph", subtitle = "7-day distracting minutes & per-task log")
                Spacer(Modifier.height(12.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                ) {
                    val slotW = size.width / weeklySeries.size
                    val barW = slotW * 0.46f
                    weeklySeries.forEachIndexed { index, (_, _, dist) ->
                        val ratio = if (dist <= 0) 0.04f else (dist / maxDist).coerceIn(0.08f, 1f)
                        val barH = size.height * ratio
                        val left = index * slotW + (slotW - barW) / 2f
                        drawRoundRect(
                            brush = if (dist <= 0) {
                                Brush.verticalGradient(listOf(tokens.borderSubtle, tokens.borderSubtle))
                            } else {
                                Brush.verticalGradient(listOf(RoseAlert, AmberWarning))
                            },
                            topLeft = Offset(left, size.height - barH),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(12f, 12f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    weeklySeries.forEach { (label, _, dist) ->
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.inkSecondary)
                            Text("${dist}m", style = MaterialTheme.typography.labelSmall, color = if (dist > 0) RoseAlert else tokens.inkMuted)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Per-Task Distraction", style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary)
                Spacer(Modifier.height(6.dp))
                if (distractingTasks.isEmpty()) {
                    Text(
                        text = "No distractions logged on individual tasks yet. Log one in Tasks or Digital Wellness.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkSecondary
                    )
                } else {
                    val maxTaskDist = distractingTasks.maxOf { it.distractionMinutes }.coerceAtLeast(15).toFloat()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        distractingTasks.take(5).forEach { task ->
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.inkPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${task.distractionMinutes}m",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = RoseAlert
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                NeonProgressBar(progress = task.distractionMinutes / maxTaskDist, heightDp = 5)
                            }
                        }
                    }
                }
            }
        }

        // Editable Profile Details
        item {
            ObsidianCard {
                Text("Edit Profile", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
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
                    modifier = Modifier.fillMaxWidth().testTag("profile_save_btn")
                ) {
                    Text("Save Profile", maxLines = 1, softWrap = false)
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showRetakeQuiz) {
        RetakePersonalityQuizDialog(
            onDismiss = { showRetakeQuiz = false },
            onConfirmCompanion = { species, customName ->
                onReappointCompanion(species, customName)
                showRetakeQuiz = false
            }
        )
    }
}
