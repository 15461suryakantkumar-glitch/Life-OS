package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.AppUsageCategory
import com.example.data.local.AppUsageLogEntity
import com.example.data.local.CycleLogEntity
import com.example.data.local.TaskEntity
import com.example.kutta.KuttaDialogueBubble
import com.example.kutta.KuttaEvent
import com.example.kutta.KuttaEventHandler
import com.example.kutta.KuttaMascot
import com.example.ui.components.GradientGlowCard
import com.example.ui.components.MetricTile
import com.example.ui.components.NeonProgressBar
import com.example.ui.components.ObsidianCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.LifeOsUiState
import com.example.util.CycleCareAnalyzer
import com.example.util.DateTimeUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WellnessScreen(
    state: LifeOsUiState,
    onAddManualUsage: (String, AppUsageCategory, Int, Boolean, Long?) -> Unit,
    onDeleteUsage: (AppUsageLogEntity) -> Unit,
    onBuildUsageAccessIntent: () -> Intent,
    onRefreshUsageStats: () -> Unit,
    onLogWater: (Int) -> Unit = {},
    onTriggerWaterReminder: () -> Unit = {},
    onSubmitCheckIn: (Int, Int, String) -> Unit = { _, _, _ -> },
    onLogCycleStart: (Long, String, String) -> Unit = { _, _, _ -> },
    onLogCycleEnd: (Long) -> Unit = {},
    onToggleCycleSymptom: (String) -> Unit = {},
    onDeleteCycleLog: (CycleLogEntity) -> Unit = {}
) {
    val tokens = LifeOsThemeTokensProvider.current
    val context = LocalContext.current
    var showLogDialog by remember { mutableStateOf(false) }

    val today = state.todayEpochDay
    val todayLogs = state.todayUsageLogs
    val totalToday = state.totalScreenMinutesToday
    val distractingToday = state.distractingMinutesToday
    val limit = state.prefs.dailyScreenLimitMinutes.coerceAtLeast(60)
    val usageRatio = (totalToday.toFloat() / limit).coerceIn(0f, 1.5f)

    val weeklyTrend = remember(state.usageLogs, today) {
        (6 downTo 0).map { daysAgo ->
            val day = today - daysAgo
            val total = state.usageLogs.filter { it.dateEpochDay == day }.sumOf { it.minutesUsed }
            DateTimeUtils.formatFullDate(day).substringBefore(",") to total
        }
    }

    val perTaskDistractions = remember(state.tasks) {
        state.tasks.filter { it.distractionMinutes > 0 }.sortedByDescending { it.distractionMinutes }
    }

    val feedbackMessage = remember(totalToday, distractingToday, limit) {
        when {
            totalToday > limit -> "Daily screen budget exceeded by ${totalToday - limit}m. Try a 45m Focus Sprint to reset."
            distractingToday > 90 -> "Distracting apps took ${distractingToday}m today. Trim social breaks during study hours."
            else -> "Healthy screen balance today (${DateTimeUtils.formatMinutesCompact(totalToday)} logged vs ${DateTimeUtils.formatMinutesCompact(limit)} budget)."
        }
    }

    Scaffold(
        containerColor = tokens.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showLogDialog = true },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_usage_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Log App Usage")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("wellness_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Wellness OS", style = MaterialTheme.typography.headlineLarge, color = tokens.inkPrimary)
                Text("Water hydration, daily check-ins, private Cycle Care & screen wellbeing", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
            }

            // 1. Water Tracker & KUTTA Reminder Card
            item {
                val waterMl = state.prefs.waterIntakeMlToday
                val waterGoal = state.prefs.dailyWaterGoalMl.coerceAtLeast(500)
                val waterRatio = (waterMl.toFloat() / waterGoal).coerceIn(0f, 1f)
                val waterVisual = remember(state.kuttaState, waterMl, waterGoal) {
                    if (state.kuttaState.isWaterHighlight) state.kuttaState
                    else if (waterMl >= waterGoal) KuttaEventHandler.stateForEvent(KuttaEvent.WaterLogged(waterMl, waterGoal), dialogueIndex = 0)
                    else KuttaEventHandler.stateForEvent(KuttaEvent.WaterReminder, dialogueIndex = 0)
                }
                ObsidianCard(
                    modifier = Modifier.testTag("wellness_water_card"),
                    borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Water Tracker", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                                Text("$waterMl / $waterGoal ml today", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                            }
                        }
                        StatusPill(
                            text = "${(waterRatio * 100).toInt()}%",
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    NeonProgressBar(progress = waterRatio, heightDp = 7)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KuttaMascot(visualState = waterVisual, size = 72.dp)
                        Spacer(Modifier.width(8.dp))
                        KuttaDialogueBubble(
                            message = waterVisual.dialogue,
                            emotion = waterVisual.emotion,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onLogWater(250) },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.weight(1f).height(40.dp).testTag("water_add_250_btn")
                        ) {
                            Text("+250ml", maxLines = 1, softWrap = false)
                        }
                        FilledTonalButton(
                            onClick = { onLogWater(500) },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.weight(1f).height(40.dp).testTag("water_add_500_btn")
                        ) {
                            Text("+500ml", maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(
                            onClick = onTriggerWaterReminder,
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(40.dp).testTag("water_remind_btn")
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Nudge", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            // 2. Daily Check-In Card
            item {
                val existingCheckIn = state.todayCheckIn
                var selectedMood by remember(existingCheckIn?.moodScore) {
                    mutableIntStateOf(existingCheckIn?.moodScore ?: 4)
                }
                var selectedEnergy by remember(existingCheckIn?.energyScore) {
                    mutableIntStateOf(existingCheckIn?.energyScore ?: 3)
                }
                var checkInNote by remember(existingCheckIn?.note) {
                    mutableStateOf(existingCheckIn?.note ?: "")
                }
                ObsidianCard(modifier = Modifier.testTag("wellness_checkin_card"), elevated = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Daily Check-In", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                            Text("KUTTA adapts gently to how you feel today, Malkin.", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                        }
                        if (existingCheckIn != null) {
                            StatusPill(text = "Logged", color = EmeraldSuccess)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Mood", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            1 to "Low",
                            2 to "Tired",
                            3 to "Okay",
                            4 to "Good",
                            5 to "Great"
                        ).forEach { (score, label) ->
                            FilterChip(
                                selected = selectedMood == score,
                                onClick = { selectedMood = score },
                                label = { Text(label, maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Energy (1–5)", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..5).forEach { lvl ->
                            FilterChip(
                                selected = selectedEnergy == lvl,
                                onClick = { selectedEnergy = lvl },
                                label = { Text("$lvl", maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = checkInNote,
                        onValueChange = { checkInNote = it },
                        label = { Text("Quick reflection (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onSubmitCheckIn(selectedMood, selectedEnergy, checkInNote) },
                        modifier = Modifier.fillMaxWidth().height(42.dp).testTag("checkin_submit_btn")
                    ) {
                        Text(if (existingCheckIn == null) "Save Daily Check-In" else "Update Check-In", maxLines = 1, softWrap = false)
                    }
                }
            }

            // 3. Strictly Local Private Cycle Care Card
            if (state.prefs.cycleCareEnabled) {
                item {
                    val summary = state.cycleSummary
                    var selectedFlow by remember { mutableStateOf(summary.latestCycleLog?.flowLevel ?: "Medium") }
                    val cycleKuttaVisual = remember(summary, state.kuttaState) {
                        when {
                            state.kuttaState.activeEvent is KuttaEvent.CyclePeriodStarted ||
                                state.kuttaState.activeEvent is KuttaEvent.CycleSymptomsLogged ||
                                state.kuttaState.activeEvent is KuttaEvent.CyclePeriodEnded ||
                                state.kuttaState.activeEvent is KuttaEvent.CyclePeriodApproaching -> state.kuttaState
                            summary.isCurrentlyOnPeriod -> KuttaEventHandler.stateForEvent(KuttaEvent.CyclePeriodStarted, dialogueIndex = 0)
                            summary.isPeriodApproaching -> KuttaEventHandler.stateForEvent(KuttaEvent.CyclePeriodApproaching(summary.daysUntilNextPeriod), dialogueIndex = 0)
                            else -> KuttaEventHandler.stateForEvent(KuttaEvent.CycleCareRest, dialogueIndex = 1)
                        }
                    }
                    ObsidianCard(
                        modifier = Modifier.testTag("wellness_cycle_care_card"),
                        borderColor = Color(0xFFF472B6).copy(alpha = 0.50f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFF472B6))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Cycle Care", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                                    Text("Gentle care, prediction & symptom log", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                                }
                            }
                            StatusPill(
                                text = "Private · Device Only",
                                color = Color(0xFFF472B6),
                                icon = Icons.Filled.Lock
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(tokens.smallRadius),
                                color = tokens.surfaceElevated,
                                border = BorderStroke(1.dp, tokens.borderSubtle)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Cycle Day", style = MaterialTheme.typography.labelSmall, color = tokens.inkSecondary)
                                    Text(
                                        text = if (summary.hasLogs) "Day ${summary.currentCycleDay}" else "Not logged",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = tokens.inkPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(tokens.smallRadius),
                                color = tokens.surfaceElevated,
                                border = BorderStroke(1.dp, tokens.borderSubtle)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Next Period (Est.)", style = MaterialTheme.typography.labelSmall, color = tokens.inkSecondary)
                                    Text(
                                        text = when {
                                            !summary.hasLogs -> "Log start date"
                                            summary.isCurrentlyOnPeriod -> "Active now"
                                            summary.daysUntilNextPeriod <= 0 -> "Due now"
                                            else -> "~ ${summary.daysUntilNextPeriod} days"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFFF472B6),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            KuttaMascot(visualState = cycleKuttaVisual, size = 76.dp)
                            Spacer(Modifier.width(8.dp))
                            KuttaDialogueBubble(
                                message = cycleKuttaVisual.dialogue,
                                emotion = cycleKuttaVisual.emotion,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Text("Flow Level", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Light", "Medium", "Heavy").forEach { flow ->
                                FilterChip(
                                    selected = selectedFlow == flow,
                                    onClick = { selectedFlow = flow },
                                    label = { Text(flow, maxLines = 1, softWrap = false) }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onLogCycleStart(today, selectedFlow, "") },
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.weight(1f).height(40.dp).testTag("cycle_log_start_btn")
                            ) {
                                Text("Log Period Start", maxLines = 1, softWrap = false)
                            }
                            OutlinedButton(
                                onClick = { onLogCycleEnd(today) },
                                enabled = summary.hasLogs && summary.isCurrentlyOnPeriod,
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.weight(1f).height(40.dp).testTag("cycle_log_end_btn")
                            ) {
                                Text("Log Period End", maxLines = 1, softWrap = false)
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text("Symptoms & Comfort", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                        Spacer(Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CycleCareAnalyzer.commonSymptoms.forEach { symptom ->
                                val active = summary.latestSymptoms.contains(symptom)
                                FilterChip(
                                    selected = active,
                                    onClick = { onToggleCycleSymptom(symptom) },
                                    label = { Text(symptom, maxLines = 1, softWrap = false) }
                                )
                            }
                        }

                        if (state.cycleLogs.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text("Recent Cycle Entries", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                            Spacer(Modifier.height(4.dp))
                            state.cycleLogs.take(3).forEach { log ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = buildString {
                                            append(DateTimeUtils.formatFullDate(log.startEpochDay))
                                            log.endEpochDay?.let { append(" – ${DateTimeUtils.formatFullDate(it)}") }
                                            append(" · ${log.flowLevel}")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.inkSecondary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(onClick = { onDeleteCycleLog(log) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete cycle log", tint = tokens.inkMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // OS UsageStats Permission & Sync Banner
            item {
                ObsidianCard(
                    borderColor = if (state.hasUsageStatsPermission) EmeraldSuccess.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (state.hasUsageStatsPermission) Icons.Filled.Shield else Icons.Filled.Info,
                            contentDescription = null,
                            tint = if (state.hasUsageStatsPermission) EmeraldSuccess else MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(state.usageStatsStatusLabel, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                            Text(
                                text = if (state.hasUsageStatsPermission) {
                                    "Android Usage Access is active. Pull real app usage anytime."
                                } else {
                                    "Grant Usage Access in Android Settings for automatic screen-time sync, or log apps manually anytime."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkSecondary
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!state.hasUsageStatsPermission) {
                            Button(
                                onClick = {
                                    runCatching { context.startActivity(onBuildUsageAccessIntent()) }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(38.dp).testTag("grant_usage_access_btn")
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Grant Usage Access", maxLines = 1, softWrap = false)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onRefreshUsageStats,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(38.dp).testTag("sync_usage_stats_btn")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sync OS Stats", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }

            // Daily Screen Time Summary
            item {
                GradientGlowCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Today's Screen Time", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                            Text(
                                text = DateTimeUtils.formatMinutesCompact(totalToday),
                                style = MaterialTheme.typography.displayMedium,
                                color = tokens.inkPrimary
                            )
                        }
                        StatusPill(
                            text = if (totalToday > limit) "OVER BUDGET" else "WITHIN BUDGET",
                            color = if (totalToday > limit) RoseAlert else EmeraldSuccess
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    NeonProgressBar(progress = usageRatio.coerceAtMost(1f))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Daily budget: ${DateTimeUtils.formatMinutesCompact(limit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkSecondary
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricTile(
                        label = "Productive",
                        value = DateTimeUtils.formatMinutesCompact((totalToday - distractingToday).coerceAtLeast(0)),
                        accent = EmeraldSuccess,
                        icon = Icons.Filled.Shield,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "Distracting",
                        value = DateTimeUtils.formatMinutesCompact(distractingToday),
                        accent = RoseAlert,
                        icon = Icons.Filled.WarningAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                ObsidianCard(elevated = true) {
                    Text("Wellbeing Summary", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(feedbackMessage, style = MaterialTheme.typography.bodyMedium, color = tokens.inkSecondary)
                }
            }

            // Per-Task Distraction Summary
            if (perTaskDistractions.isNotEmpty()) {
                item {
                    ObsidianCard {
                        SectionHeader(
                            title = "Per-Task Distraction",
                            subtitle = "Distraction minutes linked to specific tasks"
                        )
                        Spacer(Modifier.height(8.dp))
                        val maxDist = perTaskDistractions.maxOf { it.distractionMinutes }.coerceAtLeast(15).toFloat()
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            perTaskDistractions.take(5).forEach { task ->
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
                                    NeonProgressBar(progress = task.distractionMinutes / maxDist, heightDp = 5)
                                }
                            }
                        }
                    }
                }
            }

            // 7-Day Usage Trend Chart
            item {
                ObsidianCard {
                    SectionHeader(title = "7-Day Screen Trend", subtitle = "Daily total minutes")
                    Spacer(Modifier.height(12.dp))
                    val primary = MaterialTheme.colorScheme.primary
                    val secondary = MaterialTheme.colorScheme.secondary
                    val maxVal = (weeklyTrend.maxOfOrNull { it.second } ?: 180).coerceAtLeast(limit).toFloat()

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        val barCount = weeklyTrend.size
                        val slotWidth = size.width / barCount
                        val barWidth = slotWidth * 0.48f
                        weeklyTrend.forEachIndexed { idx, (_, mins) ->
                            val ratio = if (mins <= 0) 0.03f else (mins / maxVal).coerceIn(0.06f, 1f)
                            val barHeight = size.height * ratio
                            val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                            val top = size.height - barHeight
                            val isOver = mins > limit
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    when {
                                        mins <= 0 -> listOf(tokens.borderSubtle, tokens.borderSubtle)
                                        isOver -> listOf(RoseAlert, AmberWarning)
                                        else -> listOf(secondary, primary)
                                    }
                                ),
                                topLeft = Offset(left, top),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(14f, 14f)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        weeklyTrend.forEach { (dayLabel, mins) ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(dayLabel, style = MaterialTheme.typography.labelSmall, color = tokens.inkSecondary)
                                Text("${mins}m", style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(title = "Today's App Breakdown", subtitle = if (state.hasUsageStatsPermission) "OS + manual entries" else "Manual entries")
            }

            if (todayLogs.isEmpty()) {
                item {
                    ObsidianCard {
                        Text("No app sessions logged today. Tap + to record usage or grant Usage Access above.", style = MaterialTheme.typography.bodyMedium, color = tokens.inkSecondary)
                    }
                }
            } else {
                items(todayLogs, key = { it.id }) { log ->
                    ObsidianCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(tokens.smallRadius))
                                    .background(if (log.isDistracting) RoseAlert.copy(alpha = 0.16f) else tokens.surfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.PhoneAndroid,
                                    contentDescription = null,
                                    tint = if (log.isDistracting) RoseAlert else MaterialTheme.colorScheme.secondary
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(log.appName, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(log.category.label, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                                    if (log.isDistracting) {
                                        Text("· Distracting", style = MaterialTheme.typography.bodySmall, color = RoseAlert)
                                    }
                                }
                            }
                            Text(
                                text = DateTimeUtils.formatMinutesCompact(log.minutesUsed),
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.inkPrimary
                            )
                            IconButton(onClick = { onDeleteUsage(log) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove entry", tint = tokens.inkMuted)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showLogDialog) {
        LogUsageDialog(
            pendingTasks = state.tasks.filter { !it.isCompleted },
            onDismiss = { showLogDialog = false },
            onSave = { name, cat, mins, distracting, linkedTaskId ->
                onAddManualUsage(name, cat, mins, distracting, linkedTaskId)
                showLogDialog = false
            }
        )
    }
}

@Composable
private fun LogUsageDialog(
    pendingTasks: List<TaskEntity>,
    onDismiss: () -> Unit,
    onSave: (String, AppUsageCategory, Int, Boolean, Long?) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    var appName by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("25") }
    var category by remember { mutableStateOf(AppUsageCategory.PRODUCTIVE) }
    var isDistracting by remember { mutableStateOf(false) }
    var linkedTaskId by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceBase,
        title = { Text("Log App Screen Time", color = tokens.inkPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("App name (e.g. YouTube, Notion)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("usage_app_name_input")
                )
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Minutes used today") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppUsageCategory.entries.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = {
                                category = c
                                isDistracting = c == AppUsageCategory.SOCIAL || c == AppUsageCategory.ENTERTAINMENT
                            },
                            label = { Text(c.label, maxLines = 1, softWrap = false) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isDistracting, onCheckedChange = { isDistracting = it })
                    Text("Mark as distracting app", style = MaterialTheme.typography.bodySmall, color = tokens.inkPrimary)
                }
                if (isDistracting && pendingTasks.isNotEmpty()) {
                    Text("Link distraction to task (optional)", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = linkedTaskId == null,
                            onClick = { linkedTaskId = null },
                            label = { Text("None", maxLines = 1, softWrap = false) }
                        )
                        pendingTasks.take(5).forEach { task ->
                            FilterChip(
                                selected = linkedTaskId == task.id,
                                onClick = { linkedTaskId = task.id },
                                label = {
                                    Text(
                                        text = task.title,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val mins = minutesText.toIntOrNull() ?: 0
            Button(
                onClick = { onSave(appName, category, mins, isDistracting, linkedTaskId) },
                enabled = appName.isNotBlank() && mins > 0
            ) { Text("Log") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
