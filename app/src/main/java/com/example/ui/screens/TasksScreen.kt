package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.RecurrencePattern
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import com.example.ui.components.CategoryBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.NeonProgressBar
import com.example.ui.components.ObsidianCard
import com.example.ui.components.PriorityBadge
import com.example.ui.components.StatusPill
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
fun TasksScreen(
    state: LifeOsUiState,
    openCreateDialogImmediately: Boolean,
    onConsumeCreateDialogFlag: () -> Unit,
    onToggleTask: (TaskEntity) -> Unit,
    onSaveTask: (Long?, String, String, TaskPriority, TaskCategory, Long, RecurrencePattern) -> Unit,
    onDeleteTask: (TaskEntity) -> Unit
) {
    var viewMode by remember { mutableStateOf("DAILY") } // DAILY | WEEKLY | ALL
    var selectedCategory by remember { mutableStateOf<TaskCategory?>(null) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var showTaskDialog by remember { mutableStateOf(false) }

    LaunchedEffect(openCreateDialogImmediately) {
        if (openCreateDialogImmediately) {
            editingTask = null
            showTaskDialog = true
            onConsumeCreateDialogFlag()
        }
    }

    val today = state.todayEpochDay
    val filteredTasks = remember(state.tasks, viewMode, selectedCategory, today) {
        state.tasks.filter { task ->
            val matchesView = when (viewMode) {
                "DAILY" -> task.deadlineEpochDay <= today
                "WEEKLY" -> task.deadlineEpochDay <= today + 7
                else -> true
            }
            val matchesCat = selectedCategory == null || task.category == selectedCategory
            matchesView && matchesCat
        }
    }

    val doneCount = filteredTasks.count { it.isCompleted }
    val progress = if (filteredTasks.isEmpty()) 0f else doneCount.toFloat() / filteredTasks.size

    Scaffold(
        containerColor = ObsidianVoid,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTask = null
                    showTaskDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_task_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create Task")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("tasks_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Task Command", style = MaterialTheme.typography.headlineLarge, color = InkPrimary)
                Text("Manage missions, recurring routines, priorities & deadlines", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            }

            item {
                ObsidianCard(elevated = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Completion Velocity", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                            Text("$doneCount of ${filteredTasks.size} missions completed", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                        }
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(Modifier.height(10.dp))
                    NeonProgressBar(progress = progress)
                }
            }

            // View Mode Tabs (Daily / Weekly / All)
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("DAILY" to "Today", "WEEKLY" to "7 Days", "ALL" to "All Tasks").forEach { (key, label) ->
                        FilterChip(
                            selected = viewMode == key,
                            onClick = { viewMode = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // Category Filter Chips
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All Categories") }
                    )
                    TaskCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
            }

            if (filteredTasks.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.CheckCircle,
                        title = if (state.tasks.isEmpty()) "Zero Tasks Queued" else "No Matching Tasks",
                        subtitle = if (state.tasks.isEmpty()) {
                            "Your command queue is clean. Tap + below to add a mission or tell LIFE AI to create one."
                        } else {
                            "No tasks match the active date or category filter."
                        }
                    )
                }
            } else {
                items(filteredTasks, key = { it.id }) { task ->
                    ObsidianCard(
                        onClick = { onToggleTask(task) },
                        modifier = Modifier.testTag("task_card_${task.id}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = "Toggle completion",
                                tint = if (task.isCompleted) EmeraldSuccess else MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(24.dp)
                                    .clickable { onToggleTask(task) }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (task.isCompleted) InkMuted else InkPrimary,
                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                                )
                                if (task.description.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = task.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PriorityBadge(task.priority)
                                    CategoryBadge(task.category)
                                    StatusPill(
                                        text = DateTimeUtils.formatFullDate(task.deadlineEpochDay),
                                        color = if (task.deadlineEpochDay < today && !task.isCompleted) RoseAlert else InkSecondary
                                    )
                                    if (task.recurrence != RecurrencePattern.NONE) {
                                        StatusPill(
                                            text = task.recurrence.label,
                                            color = MaterialTheme.colorScheme.secondary,
                                            icon = Icons.Filled.Repeat
                                        )
                                    }
                                    StatusPill(
                                        text = if (task.xpAwarded) "XP Claimed" else "+${task.xpReward} XP",
                                        color = if (task.xpAwarded) InkMuted else EmeraldSuccess
                                    )
                                }
                            }
                            Column {
                                IconButton(
                                    onClick = {
                                        editingTask = task
                                        showTaskDialog = true
                                    }
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit task", tint = InkSecondary)
                                }
                                IconButton(onClick = { onDeleteTask(task) }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete task", tint = RoseAlert)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showTaskDialog) {
        TaskEditorDialog(
            existing = editingTask,
            todayEpochDay = today,
            onDismiss = { showTaskDialog = false },
            onConfirm = { title, desc, priority, category, deadlineOffset, recurrence ->
                onSaveTask(
                    editingTask?.id,
                    title,
                    desc,
                    priority,
                    category,
                    today + deadlineOffset,
                    recurrence
                )
                showTaskDialog = false
            }
        )
    }
}

@Composable
private fun TaskEditorDialog(
    existing: TaskEntity?,
    todayEpochDay: Long,
    onDismiss: () -> Unit,
    onConfirm: (String, String, TaskPriority, TaskCategory, Int, RecurrencePattern) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var priority by remember { mutableStateOf(existing?.priority ?: TaskPriority.MEDIUM) }
    var category by remember { mutableStateOf(existing?.category ?: TaskCategory.WORK) }
    var recurrence by remember { mutableStateOf(existing?.recurrence ?: RecurrencePattern.NONE) }
    var dayOffset by remember {
        mutableIntStateOf(
            if (existing != null) (existing.deadlineEpochDay - todayEpochDay).toInt().coerceIn(0, 30) else 0
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianBase,
        title = {
            Text(if (existing == null) "New Mission" else "Edit Mission", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("task_title_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Details (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Priority", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskPriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text("${p.label} (+${p.xpValue}XP)") }
                        )
                    }
                }
                Text("Category", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskCategory.entries.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c.label) }
                        )
                    }
                }
                Text("Deadline", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Today", 1 to "Tomorrow", 3 to "+3 Days", 7 to "Next Week").forEach { (off, lbl) ->
                        FilterChip(
                            selected = dayOffset == off,
                            onClick = { dayOffset = off },
                            label = { Text(lbl) }
                        )
                    }
                }
                Text("Recurrence", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RecurrencePattern.entries.forEach { rec ->
                        FilterChip(
                            selected = recurrence == rec,
                            onClick = { recurrence = rec },
                            label = { Text(rec.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, description, priority, category, dayOffset, recurrence) },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_task_confirm_btn")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
