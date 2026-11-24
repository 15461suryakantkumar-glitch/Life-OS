package com.example.suru

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.SuruMemoryEntity
import com.example.data.local.SuruStudyPlanEntity
import com.example.ui.components.GradientGlowCard
import com.example.ui.components.ObsidianCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.LifeOsUiState
import com.example.util.DateTimeUtils

@Composable
fun SuruConfirmationCard(
    request: SuruConfirmationRequest,
    onAdjustDailyMinutes: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    ObsidianCard(
        elevated = true,
        borderColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("suru_confirmation_card")
    ) {
        when (request) {
            is SuruConfirmationRequest.StudyPlanConfirmation -> {
                val draft = request.draft
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${draft.subject} Study Plan Preview",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.inkPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    StatusPill(
                        text = "${draft.deadlineDays}d · ${draft.preferredWindow.label}",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(draft.summaryNote, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)

                Spacer(Modifier.height(10.dp))
                // Manual Daily Workload Adjuster
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(tokens.smallRadius))
                        .background(tokens.surfaceBase)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Daily Workload: ${DateTimeUtils.formatMinutesCompact(draft.dailyAvailableMinutes)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.inkPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onAdjustDailyMinutes((draft.dailyAvailableMinutes - 30).coerceAtLeast(30)) },
                            modifier = Modifier.size(36.dp).testTag("study_plan_decrease_time")
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Decrease daily study time")
                        }
                        IconButton(
                            onClick = { onAdjustDailyMinutes((draft.dailyAvailableMinutes + 30).coerceAtMost(360)) },
                            modifier = Modifier.size(36.dp).testTag("study_plan_increase_time")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Increase daily study time")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    draft.sessions.take(5).forEach { s ->
                        Surface(
                            shape = RoundedCornerShape(tokens.smallRadius),
                            color = tokens.surfaceBase,
                            border = BorderStroke(
                                1.dp,
                                if (s.hasConflict) AmberWarning else tokens.borderSubtle
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Day ${s.dayOffset + 1} · ${s.title}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = tokens.inkPrimary
                                    )
                                    Text(
                                        text = "${DateTimeUtils.formatTimeOfDay(s.startMinuteOfDay)} (${s.durationMinutes}m · ${s.weightLabel})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.inkSecondary
                                    )
                                    if (s.conflictReason != null) {
                                        Text(
                                            text = s.conflictReason,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AmberWarning
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (draft.sessions.size > 5) {
                        Text(
                            text = "+${draft.sessions.size - 5} more study & revision blocks included",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.inkMuted
                        )
                    }
                }
            }

            is SuruConfirmationRequest.BacklogConfirmation -> {
                val plan = request.plan
                Text("Adaptive Backlog Plan", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                Spacer(Modifier.height(4.dp))
                Text(plan.summaryNote, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                Spacer(Modifier.height(8.dp))
                plan.items.take(4).forEach { item ->
                    Text(
                        text = "• Day ${item.plannedDayOffset + 1} ${DateTimeUtils.formatTimeOfDay(item.startMinuteOfDay)} — ${item.title} (${item.priority.label})",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkPrimary
                    )
                }
            }

            is SuruConfirmationRequest.ActionConfirmation -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = AmberWarning)
                    Spacer(Modifier.width(8.dp))
                    Text(request.title, style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                }
                Spacer(Modifier.height(6.dp))
                Text(request.message, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).testTag("suru_confirm_cancel_btn")
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).testTag("suru_confirm_apply_btn")
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Confirm & Apply")
            }
        }
    }
}

@Composable
fun SuruStudyLabPanel(
    activeHelp: SuruStudyHelpResult?,
    isLoading: Boolean,
    onGenerateHelp: (String, SuruStudyHelpMode, String) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    var topic by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(SuruStudyHelpMode.BEGINNER) }
    var revealedFlashcards by remember { mutableStateOf(setOf<Int>()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("suru_study_lab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ObsidianCard(elevated = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("SURU Study Intelligence", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                        Text("Explanations, summaries, flashcards & practice modes", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic or Chapter") },
                    placeholder = { Text("e.g., Electrostatics, Gauss Law, Organic Mechanisms") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("study_lab_topic_input")
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Paste Study Notes (Optional)") },
                    placeholder = { Text("Paste syllabus notes here to ground summaries strictly in your material...") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("study_lab_notes_input")
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuruStudyHelpMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.label, maxLines = 1, softWrap = false) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        revealedFlashcards = emptySet()
                        onGenerateHelp(topic, selectedMode, notes)
                    },
                    enabled = !isLoading && (topic.isNotBlank() || notes.isNotBlank()),
                    modifier = Modifier.fillMaxWidth().testTag("study_lab_generate_btn")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isLoading) "Generating..." else "Generate ${selectedMode.label} Guide")
                }
            }
        }

        if (activeHelp != null) {
            item {
                GradientGlowCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(activeHelp.topic, style = MaterialTheme.typography.titleLarge, color = tokens.inkPrimary)
                        StatusPill(
                            text = if (activeHelp.isBasedOnUserNotes) "From Your Notes" else "Conceptual AI",
                            color = if (activeHelp.isBasedOnUserNotes) EmeraldSuccess else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(activeHelp.sourceDisclaimer, style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
                    Spacer(Modifier.height(8.dp))
                    Text(activeHelp.conceptExplanation, style = MaterialTheme.typography.bodyMedium, color = tokens.inkPrimary)
                }
            }

            item {
                ObsidianCard {
                    Text("Summary & Key Points", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                    Spacer(Modifier.height(6.dp))
                    activeHelp.summaryBullets.forEach { b ->
                        Text("• $b", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }

            item {
                ObsidianCard {
                    Text("Active Recall Flashcards (Tap to reveal)", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeHelp.flashcards.forEachIndexed { idx, (front, back) ->
                            val open = idx in revealedFlashcards
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(tokens.smallRadius))
                                    .clickable {
                                        revealedFlashcards = if (open) revealedFlashcards - idx else revealedFlashcards + idx
                                    },
                                color = tokens.surfaceElevated,
                                border = BorderStroke(1.dp, tokens.borderSubtle),
                                shape = RoundedCornerShape(tokens.smallRadius)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(front, style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary)
                                    if (open) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(back, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                    } else {
                                        Text("Tap to reveal answer", style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                ObsidianCard {
                    Text("Practice Questions & Common Mistakes", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                    Spacer(Modifier.height(6.dp))
                    activeHelp.practiceQuestions.forEach { q ->
                        Text(q, style = MaterialTheme.typography.bodySmall, color = tokens.inkPrimary, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    activeHelp.mistakeTips.forEach { tip ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tip, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Revision Checklist", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    activeHelp.revisionChecklist.forEach { step ->
                        Text("☐ $step", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun SuruBriefingsPanel(
    state: LifeOsUiState,
    onTriggerBacklogRecovery: () -> Unit,
    onUpdatePlanStatus: (SuruStudyPlanEntity, String) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val morning = state.suruMorningBriefing
    val evening = state.suruEveningReflection

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("suru_briefings_panel"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientGlowCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WbSunny, contentDescription = null, tint = AmberWarning)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(morning.headline, style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                        Text(morning.greeting, style = MaterialTheme.typography.labelSmall, color = tokens.inkSecondary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(morning.focusSummary, style = MaterialTheme.typography.bodyMedium, color = tokens.inkPrimary)
                if (morning.priorityTasks.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    morning.priorityTasks.forEach { t ->
                        Text("• $t", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(morning.wellnessNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }

        item {
            ObsidianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.NightsStay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(evening.headline, style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                        Text(evening.greeting, style = MaterialTheme.typography.labelSmall, color = tokens.inkSecondary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(evening.focusSummary, style = MaterialTheme.typography.bodyMedium, color = tokens.inkPrimary)
                Spacer(Modifier.height(6.dp))
                Text(evening.wellnessNote, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                Spacer(Modifier.height(4.dp))
                Text(evening.gentleClosing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        item {
            ObsidianCard(elevated = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Adaptive Backlog Manager", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                        Text(
                            "${state.pendingTasksCount} open task(s) in queue",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.inkSecondary
                        )
                    }
                    OutlinedButton(
                        onClick = onTriggerBacklogRecovery,
                        enabled = state.pendingTasksCount > 0,
                        modifier = Modifier.testTag("suru_backlog_btn")
                    ) {
                        Icon(Icons.Filled.EventRepeat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Re-plan")
                    }
                }
            }
        }

        if (state.suruStudyPlans.isNotEmpty()) {
            item {
                Text("Saved Study Plans", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
            }
            items(state.suruStudyPlans, key = { it.id }) { plan ->
                ObsidianCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(plan.subject, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                            Text(plan.chaptersCsv, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                            Text(
                                "${plan.deadlineDays}d · ${DateTimeUtils.formatMinutesCompact(plan.dailyMinutesBudget)}/day · ${plan.preferredWindow}",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.inkMuted
                            )
                        }
                        StatusPill(
                            text = plan.status,
                            color = if (plan.status == "COMPLETED") EmeraldSuccess else MaterialTheme.colorScheme.primary
                        )
                    }
                    if (plan.status == "ACTIVE") {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onUpdatePlanStatus(plan, "COMPLETED") }) {
                                Text("Mark Complete")
                            }
                            TextButton(onClick = { onUpdatePlanStatus(plan, "ARCHIVED") }) {
                                Text("Archive", color = RoseAlert)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuruMemoryPanel(
    personalizationEnabled: Boolean,
    memories: List<SuruMemoryEntity>,
    onTogglePersonalization: (Boolean) -> Unit,
    onAddMemory: (String, String, String) -> Unit,
    onToggleMemory: (SuruMemoryEntity) -> Unit,
    onDeleteMemory: (SuruMemoryEntity) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("suru_memory_panel"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ObsidianCard(elevated = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Personalization Memory", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                            Text("Cycle Care & private journal notes stay excluded", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                        }
                    }
                    Switch(checked = personalizationEnabled, onCheckedChange = onTogglePersonalization)
                }
            }
        }

        item {
            ObsidianCard {
                Text("Add Study or Schedule Preference", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    label = { Text("Preference Label") },
                    placeholder = { Text("e.g., Best Focus Window, Weak Subject") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("memory_key_input")
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    label = { Text("Details") },
                    placeholder = { Text("e.g., Evening 6-8 PM works best; Optics needs extra time") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("memory_value_input")
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        onAddMemory("STUDY_PREF", newKey, newValue)
                        newKey = ""
                        newValue = ""
                    },
                    enabled = newKey.isNotBlank() && newValue.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("memory_save_btn")
                ) {
                    Text("Save Preference")
                }
            }
        }

        if (memories.isEmpty()) {
            item {
                ObsidianCard {
                    Text(
                        "No stored preferences yet. You can tell SURU \"Remember that I prefer evening study\" or add one above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkSecondary
                    )
                }
            }
        } else {
            items(memories, key = { it.id }) { mem ->
                ObsidianCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mem.keyLabel, style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary)
                            Text(mem.valueText, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                        }
                        Switch(checked = mem.isEnabled, onCheckedChange = { onToggleMemory(mem) })
                        IconButton(onClick = { onDeleteMemory(mem) }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete memory", tint = RoseAlert)
                        }
                    }
                }
            }
        }
    }
}
