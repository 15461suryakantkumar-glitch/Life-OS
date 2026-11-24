package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
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
import com.example.data.local.AndroidUsageStatsBridge
import com.example.data.local.AppUsageCategory
import com.example.data.local.AppUsageLogEntity
import com.example.data.local.CycleLogEntity
import com.example.data.local.TaskEntity
import com.example.data.local.WellbeingRange
import com.example.data.local.WellbeingTelemetryState
import com.example.kutta.KuttaDialogueBubble
import com.example.kutta.KuttaEvent
import com.example.kutta.KuttaEventHandler
import com.example.kutta.KuttaMascot
import com.example.ui.components.FeeloraScreenBackdrop
import com.example.ui.components.FeeloraSegmentedRow
import com.example.ui.components.FeeloraTopHeader
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
    onSelectWellbeingRange: (WellbeingRange) -> Unit = {},
    onLogWater: (Int) -> Unit = {},
    onTriggerWaterReminder: () -> Unit = {},
    onSubmitCheckIn: (Int, Int, String) -> Unit = { _, _, _ -> },
    onLogCycleStart: (Long, String, String) -> Unit = { _, _, _ -> },
    onLogCycleEnd: (Long) -> Unit = {},
    onToggleCycleSymptom: (String) -> Unit = {},
    onDeleteCycleLog: (CycleLogEntity) -> Unit = {},
    onAskSuru: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val tokens = LifeOsThemeTokensProvider.current
    val context = LocalContext.current
    var showLogDialog by remember { mutableStateOf(false) }

    val today = state.todayEpochDay
    val todayLogs = state.todayUsageLogs
    val report = state.wellbeingReport
    val isDaily = state.wellbeingRange == WellbeingRange.DAILY
    val displayTotal = if (isDaily) maxOf(state.totalScreenMinutesToday, report.totalMinutes) else report.totalMinutes
    val displayDistracting = if (isDaily) maxOf(state.distractingMinutesToday, report.distractingMinutes) else report.distractingMinutes
    val limit = state.prefs.dailyScreenLimitMinutes.coerceAtLeast(60)
    val scaledBudget = limit * state.wellbeingRange.days
    val usageRatio = (displayTotal.toFloat() / scaledBudget).coerceIn(0f, 1.5f)

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

    FeeloraScreenBackdrop(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showLogDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = tokens.background,
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
                    FeeloraTopHeader(
                        title = "Wellness OS",
                        subtitle = "Hydration, Check-Ins, Digital Wellbeing & Cycle Care",
                        userName = state.prefs.userName,
                        avatarPreset = state.prefs.avatarPreset,
                        avatarUri = state.prefs.avatarPhotoUri,
                        unreadNotifications = state.unreadNotificationsCount,
                        onOpenDrawer = onOpenDrawer,
                        onOpenNotifications = onOpenNotifications,
                        onOpenProfile = onOpenProfile,
                        trailingAction = {
                            OutlinedButton(
                                onClick = onAskSuru,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp).testTag("wellness_ask_suru_btn")
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("SURU", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                            }
                        }
                    )
                }

                // 1. Water Tracker
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
                            StatusPill(text = "${(waterRatio * 100).toInt()}%", color = MaterialTheme.colorScheme.secondary)
                        }
                        Spacer(Modifier.height(8.dp))
                        NeonProgressBar(progress = waterRatio, heightDp = 7)
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            KuttaMascot(visualState = waterVisual, size = 70.dp)
                            Spacer(Modifier.width(8.dp))
                            KuttaDialogueBubble(message = waterVisual.dialogue, emotion = waterVisual.emotion, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { onLogWater(250) },
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.weight(1f).height(40.dp).testTag("water_add_250_btn")
                            ) { Text("+250ml", maxLines = 1, softWrap = false) }
                            FilledTonalButton(
                                onClick = { onLogWater(500) },
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.weight(1f).height(40.dp).testTag("water_add_500_btn")
                            ) { Text("+500ml", maxLines = 1, softWrap = false) }
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

                // 2. Daily Check-In
                item {
                    val existingCheckIn = state.todayCheckIn
                    var selectedMood by remember(existingCheckIn?.moodScore) { mutableIntStateOf(existingCheckIn?.moodScore ?: 4) }
                    var selectedEnergy by remember(existingCheckIn?.energyScore) { mutableIntStateOf(existingCheckIn?.energyScore ?: 3) }
                    var checkInNote by remember(existingCheckIn?.note) { mutableStateOf(existingCheckIn?.note ?: "") }
                    ObsidianCard(modifier = Modifier.testTag("wellness_checkin_card"), elevated = true) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Daily Check-In", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                                Text("Private mood & energy check-in (device only)", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                            }
                            if (existingCheckIn != null) StatusPill(text = "Logged", color = EmeraldSuccess)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Mood", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1 to "Low", 2 to "Tired", 3 to "Okay", 4 to "Good", 5 to "Great").forEach { (score, label) ->
                                FilterChip(selected = selectedMood == score, onClick = { selectedMood = score }, label = { Text(label, maxLines = 1, softWrap = false) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Energy (1–5)", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..5).forEach { lvl ->
                                FilterChip(selected = selectedEnergy == lvl, onClick = { selectedEnergy = lvl }, label = { Text("$lvl", maxLines = 1, softWrap = false) })
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
                                StatusPill(text = "Private · Device Only", color = Color(0xFFF472B6), icon = Icons.Filled.Lock)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                KuttaMascot(visualState = cycleKuttaVisual, size = 76.dp)
                                Spacer(Modifier.width(8.dp))
                                KuttaDialogueBubble(message = cycleKuttaVisual.dialogue, emotion = cycleKuttaVisual.emotion, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Flow Level", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Light", "Medium", "Heavy").forEach { flow ->
                                    FilterChip(selected = selectedFlow == flow, onClick = { selectedFlow = flow }, label = { Text(flow, maxLines = 1, softWrap = false) })
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onLogCycleStart(today, selectedFlow, "") },
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.weight(1f).height(40.dp).testTag("cycle_log_start_btn")
                                ) { Text("Log Period Start", maxLines = 1, softWrap = false) }
                                OutlinedButton(
                                    onClick = { onLogCycleEnd(today) },
                                    enabled = summary.hasLogs && summary.isCurrentlyOnPeriod,
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.weight(1f).height(40.dp).testTag("cycle_log_end_btn")
                                ) { Text("Log Period End", maxLines = 1, softWrap = false) }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Symptoms & Comfort", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                            Spacer(Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                CycleCareAnalyzer.commonSymptoms.forEach { symptom ->
                                    FilterChip(
                                        selected = summary.latestSymptoms.contains(symptom),
                                        onClick = { onToggleCycleSymptom(symptom) },
                                        label = { Text(symptom, maxLines = 1, softWrap = false) }
                                    )
                                }
                            }
                            if (state.cycleLogs.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
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

                // 4. Authentic Digital Wellbeing Section (Range Switcher + 4 Explicit Telemetry States)
                item {
                    SectionHeader(title = "Digital Wellbeing", subtitle = "Authentic UsageStatsManager telemetry")
                }

                item {
                    FeeloraSegmentedRow(
                        items = WellbeingRange.entries,
                        selectedItem = state.wellbeingRange,
                        labelFor = { it.label },
                        onSelect = onSelectWellbeingRange,
                        tagPrefix = "wellbeing_range"
                    )
                }

                item {
                    val stateColor = when (report.state) {
                        WellbeingTelemetryState.RECORDED_USAGE -> EmeraldSuccess
                        WellbeingTelemetryState.NO_USAGE_RECORDED -> MaterialTheme.colorScheme.secondary
                        WellbeingTelemetryState.PERMISSION_NOT_GRANTED -> AmberWarning
                        WellbeingTelemetryState.USAGE_UNAVAILABLE -> RoseAlert
                    }
                    ObsidianCard(borderColor = stateColor.copy(alpha = 0.5f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (state.hasUsageStatsPermission) Icons.Filled.Shield else Icons.Filled.Info,
                                contentDescription = null,
                                tint = stateColor
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(report.state.label, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                Text(report.state.description, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!state.hasUsageStatsPermission) {
                                Button(
                                    onClick = { runCatching { context.startActivity(onBuildUsageAccessIntent()) } },
                                    modifier = Modifier.height(38.dp).testTag("grant_usage_access_btn")
                                ) {
                                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Grant Usage Access", maxLines = 1, softWrap = false)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onRefreshUsageStats,
                                    modifier = Modifier.height(38.dp).testTag("sync_usage_stats_btn")
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Refresh Telemetry", maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }

                item {
                    GradientGlowCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${state.wellbeingRange.label} Screen Time", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                                Text(
                                    text = DateTimeUtils.formatMinutesCompact(displayTotal),
                                    style = MaterialTheme.typography.displayMedium,
                                    color = tokens.inkPrimary
                                )
                            }
                            StatusPill(
                                text = if (displayTotal > scaledBudget) "OVER BUDGET" else "BALANCED",
                                color = if (displayTotal > scaledBudget) RoseAlert else EmeraldSuccess
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        NeonProgressBar(progress = usageRatio.coerceAtMost(1f))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Budget target: ${DateTimeUtils.formatMinutesCompact(scaledBudget)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.inkSecondary
                        )
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile(
                            label = "Productive",
                            value = DateTimeUtils.formatMinutesCompact((displayTotal - displayDistracting).coerceAtLeast(0)),
                            accent = EmeraldSuccess,
                            icon = Icons.Filled.Shield,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "Distracting",
                            value = DateTimeUtils.formatMinutesCompact(displayDistracting),
                            accent = RoseAlert,
                            icon = Icons.Filled.WarningAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (perTaskDistractions.isNotEmpty()) {
                    item {
                        ObsidianCard {
                            SectionHeader(title = "Per-Task Distraction", subtitle = "Distraction minutes linked to specific tasks")
                            Spacer(Modifier.height(8.dp))
                            val maxDist = perTaskDistractions.maxOf { it.distractionMinutes }.coerceAtLeast(15).toFloat()
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                perTaskDistractions.take(5).forEach { task ->
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(task.title, style = MaterialTheme.typography.bodySmall, color = tokens.inkPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                            Text("${task.distractionMinutes}m", style = MaterialTheme.typography.labelMedium, color = RoseAlert)
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        NeonProgressBar(progress = task.distractionMinutes / maxDist, heightDp = 5)
                                    }
                                }
                            }
                        }
                    }
                }

                // 7-Day Trend
                item {
                    ObsidianCard {
                        SectionHeader(title = "7-Day Screen Trend", subtitle = "Daily total minutes")
                        Spacer(Modifier.height(12.dp))
                        val primary = MaterialTheme.colorScheme.primary
                        val secondary = MaterialTheme.colorScheme.secondary
                        val maxVal = (weeklyTrend.maxOfOrNull { it.second } ?: 180).coerceAtLeast(limit).toFloat()
                        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                            val slotWidth = size.width / weeklyTrend.size
                            val barWidth = slotWidth * 0.48f
                            weeklyTrend.forEachIndexed { idx, (_, mins) ->
                                val ratio = if (mins <= 0) 0.03f else (mins / maxVal).coerceIn(0.06f, 1f)
                                val barHeight = size.height * ratio
                                val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        when {
                                            mins <= 0 -> listOf(tokens.borderSubtle, tokens.borderSubtle)
                                            mins > limit -> listOf(RoseAlert, AmberWarning)
                                            else -> listOf(secondary, primary)
                                        }
                                    ),
                                    topLeft = Offset(left, size.height - barHeight),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(14f, 14f)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            weeklyTrend.forEach { (dayLabel, mins) ->
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(dayLabel, style = MaterialTheme.typography.labelSmall, color = tokens.inkSecondary)
                                    Text("${mins}m", style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
                                }
                            }
                        }
                    }
                }

                // App Breakdown (Authentic PackageManager icons + single Apps fallback)
                item {
                    SectionHeader(
                        title = "${state.wellbeingRange.label} App Breakdown",
                        subtitle = if (state.hasUsageStatsPermission) "Real app icons & UsageStatsManager totals" else "Manual entries"
                    )
                }

                if (!isDaily && report.apps.isNotEmpty()) {
                    items(report.apps, key = { "rep_${it.packageName}" }) { app ->
                        val iconBitmap = remember(app.packageName) {
                            AndroidUsageStatsBridge.loadAppIconBitmap(context, app.packageName)
                        }
                        ObsidianCard {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                AppIconBadge(iconBitmap = iconBitmap, appName = app.appName, isDistracting = app.isDistracting)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.appName, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                    Text(
                                        text = if (app.isDistracting) "${app.category.label} · Distracting" else app.category.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (app.isDistracting) RoseAlert else tokens.inkSecondary
                                    )
                                }
                                Text(DateTimeUtils.formatMinutesCompact(app.minutesUsed), style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                            }
                        }
                    }
                } else if (todayLogs.isEmpty()) {
                    item {
                        ObsidianCard {
                            Text(
                                text = when (report.state) {
                                    WellbeingTelemetryState.PERMISSION_NOT_GRANTED -> "Grant Usage Access above or tap + to log sessions manually."
                                    WellbeingTelemetryState.NO_USAGE_RECORDED -> "No qualifying app usage recorded in this window yet."
                                    WellbeingTelemetryState.USAGE_UNAVAILABLE -> "Usage information unavailable from system telemetry."
                                    WellbeingTelemetryState.RECORDED_USAGE -> "No app sessions logged today yet."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.inkSecondary
                            )
                        }
                    }
                } else {
                    items(todayLogs, key = { it.id }) { log ->
                        val iconBitmap = remember(log.packageName) {
                            AndroidUsageStatsBridge.loadAppIconBitmap(context, log.packageName)
                        }
                        ObsidianCard {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                AppIconBadge(iconBitmap = iconBitmap, appName = log.appName, isDistracting = log.isDistracting)
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
                                Text(DateTimeUtils.formatMinutesCompact(log.minutesUsed), style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
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
private fun AppIconBadge(
    iconBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    appName: String,
    isDistracting: Boolean
) {
    val tokens = LifeOsThemeTokensProvider.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(tokens.smallRadius))
            .background(if (isDistracting) RoseAlert.copy(alpha = 0.14f) else tokens.surfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = "$appName icon",
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Apps,
                contentDescription = "$appName app",
                tint = if (isDistracting) RoseAlert else MaterialTheme.colorScheme.secondary
            )
        }
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
        containerColor = tokens.surfaceCard,
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
                        FilterChip(selected = linkedTaskId == null, onClick = { linkedTaskId = null }, label = { Text("None", maxLines = 1, softWrap = false) })
                        pendingTasks.take(5).forEach { task ->
                            FilterChip(
                                selected = linkedTaskId == task.id,
                                onClick = { linkedTaskId = task.id },
                                label = { Text(task.title, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
