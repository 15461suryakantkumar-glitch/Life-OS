package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.AppUsageCategory
import com.example.data.local.AppUsageLogEntity
import com.example.ui.components.GradientGlowCard
import com.example.ui.components.MetricTile
import com.example.ui.components.NeonProgressBar
import com.example.ui.components.ObsidianCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.ObsidianBase
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.LifeOsUiState
import com.example.util.DateTimeUtils

@Composable
fun WellnessScreen(
    state: LifeOsUiState,
    onAddManualUsage: (String, AppUsageCategory, Int, Boolean) -> Unit,
    onDeleteUsage: (AppUsageLogEntity) -> Unit
) {
    var showLogDialog by remember { mutableStateOf(false) }

    val today = state.todayEpochDay
    val todayLogs = state.todayUsageLogs
    val totalToday = state.totalScreenMinutesToday
    val distractingToday = state.distractingMinutesToday
    val limit = state.prefs.dailyScreenLimitMinutes.coerceAtLeast(60)
    val usageRatio = (totalToday.toFloat() / limit).coerceIn(0f, 1.5f)

    // Build 7-day trend points
    val weeklyTrend = remember(state.usageLogs, today) {
        (6 downTo 0).map { daysAgo ->
            val day = today - daysAgo
            val total = state.usageLogs.filter { it.dateEpochDay == day }.sumOf { it.minutesUsed }
            DateTimeUtils.formatFullDate(day).substringBefore(",") to total
        }
    }

    val feedbackMessage = remember(totalToday, distractingToday, limit) {
        when {
            totalToday > limit -> "Screen budget exceeded by ${totalToday - limit}m. Enable a 45m Focus Sprint to reset attention."
            distractingToday > 90 -> "Distracting apps account for ${distractingToday}m today. Trim social blocks before evening study."
            else -> "Healthy digital balance today (${DateTimeUtils.formatMinutesCompact(totalToday)} logged vs ${DateTimeUtils.formatMinutesCompact(limit)} budget)."
        }
    }

    Scaffold(
        containerColor = ObsidianVoid,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showLogDialog = true },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = ObsidianVoid,
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Digital Wellness", style = MaterialTheme.typography.headlineLarge, color = InkPrimary)
                Text("Screen time telemetry, distraction audits & weekly trends", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            }

            // Architecture transparency banner (Manual/Sample vs OS UsageStats)
            item {
                ObsidianCard(borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(state.usageStatsStatusLabel, style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                            Text(
                                "Using manual logs & sample telemetry. Architecture is wired to AndroidUsageStatsBridge for future PACKAGE_USAGE_STATS opt-in.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
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
                            Text("Today's Screen Time", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                            Text(
                                text = DateTimeUtils.formatMinutesCompact(totalToday),
                                style = MaterialTheme.typography.displayMedium,
                                color = InkPrimary
                            )
                        }
                        StatusPill(
                            text = if (totalToday > limit) "OVER LIMIT" else "WITHIN BUDGET",
                            color = if (totalToday > limit) RoseAlert else EmeraldSuccess
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    NeonProgressBar(progress = usageRatio.coerceAtMost(1f))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Daily budget: ${DateTimeUtils.formatMinutesCompact(limit)} · Productive vs Distracting split",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
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

            // Wellness Feedback
            item {
                ObsidianCard(elevated = true) {
                    Text("Wellness Feedback", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(feedbackMessage, style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
                }
            }

            // 7-Day Usage Trend Chart
            item {
                ObsidianCard {
                    SectionHeader(title = "7-Day Usage Trend", subtitle = "Daily total minutes (tap + to log)")
                    Spacer(Modifier.height(14.dp))
                    val primary = MaterialTheme.colorScheme.primary
                    val secondary = MaterialTheme.colorScheme.secondary
                    val maxVal = (weeklyTrend.maxOfOrNull { it.second } ?: 180).coerceAtLeast(limit).toFloat()

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    ) {
                        val barCount = weeklyTrend.size
                        val slotWidth = size.width / barCount
                        val barWidth = slotWidth * 0.48f
                        weeklyTrend.forEachIndexed { idx, (_, mins) ->
                            val ratio = (mins / maxVal).coerceIn(0.06f, 1f)
                            val barHeight = size.height * ratio
                            val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                            val top = size.height - barHeight
                            val isOver = mins > limit
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    if (isOver) listOf(RoseAlert, AmberWarning)
                                    else listOf(secondary, primary)
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
                                Text(dayLabel, style = MaterialTheme.typography.labelSmall, color = InkSecondary)
                                Text("${mins}m", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                            }
                        }
                    }
                }
            }

            // Most-Used & Distracting Apps Breakdown
            item {
                SectionHeader(title = "Today's Application Breakdown", subtitle = "Manual entries & sample logs")
            }

            if (todayLogs.isEmpty()) {
                item {
                    ObsidianCard {
                        Text("No app sessions logged today. Tap + to record screen time.", style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
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
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (log.isDistracting) RoseAlert.copy(alpha = 0.18f) else CharcoalElevated),
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
                                Text(log.appName, style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(log.category.label, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                                    if (log.isDistracting) {
                                        Text("· Distracting", style = MaterialTheme.typography.bodySmall, color = RoseAlert)
                                    }
                                }
                            }
                            Text(
                                text = DateTimeUtils.formatMinutesCompact(log.minutesUsed),
                                style = MaterialTheme.typography.titleMedium,
                                color = InkPrimary
                            )
                            IconButton(onClick = { onDeleteUsage(log) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove log", tint = InkMuted)
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
            onDismiss = { showLogDialog = false },
            onSave = { name, cat, mins, distracting ->
                onAddManualUsage(name, cat, mins, distracting)
                showLogDialog = false
            }
        )
    }
}

@Composable
private fun LogUsageDialog(
    onDismiss: () -> Unit,
    onSave: (String, AppUsageCategory, Int, Boolean) -> Unit
) {
    var appName by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("30") }
    var category by remember { mutableStateOf(AppUsageCategory.PRODUCTIVE) }
    var isDistracting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianBase,
        title = { Text("Log App Screen Time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("App name (e.g. Obsidian, YouTube)") },
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
                            label = { Text(c.label) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isDistracting, onCheckedChange = { isDistracting = it })
                    Text("Mark as distracting app", style = MaterialTheme.typography.bodySmall, color = InkPrimary)
                }
            }
        },
        confirmButton = {
            val mins = minutesText.toIntOrNull() ?: 0
            Button(
                onClick = { onSave(appName, category, mins, isDistracting) },
                enabled = appName.isNotBlank() && mins > 0
            ) { Text("Log") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
