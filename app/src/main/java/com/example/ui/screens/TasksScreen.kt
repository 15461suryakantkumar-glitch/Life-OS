package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.RecurrencePattern
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import com.example.data.local.TaskSectionEntity
import com.example.kutta.KuttaDialogueBubble
import com.example.kutta.KuttaEmotion
import com.example.kutta.KuttaEvent
import com.example.kutta.KuttaEventHandler
import com.example.kutta.KuttaMascot
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.NeonProgressBar
import com.example.ui.components.ObsidianCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.LifeOsUiState

@Composable
fun TasksScreen(
    state: LifeOsUiState,
    openCreateDialogImmediately: Boolean,
    onConsumeCreateDialogFlag: () -> Unit,
    onToggleTask: (TaskEntity) -> Unit,
    onSaveTask: (Long?, Long, String, String, TaskPriority, TaskCategory, Long, RecurrencePattern) -> Unit,
    onLogTaskDistraction: (TaskEntity, Int) -> Unit,
    onDeleteTask: (TaskEntity) -> Unit,
    onAddSection: (String) -> Unit,
    onRenameSection: (TaskSectionEntity, String) -> Unit,
    onDeleteSection: (TaskSectionEntity) -> Unit,
    onQuickAddTaskInSection: (Long, String) -> Unit,
    onTapKutta: () -> Unit = {}
) {
    val tokens = LifeOsThemeTokensProvider.current
    var viewMode by remember { mutableStateOf("ALL") } // ALL | DAILY | WEEKLY
    var showSectionCreator by remember { mutableStateOf(false) }
    var newSectionName by remember { mutableStateOf("") }
    var activeInlineSectionId by remember { mutableStateOf<Long?>(null) }

    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var renamingSection by remember { mutableStateOf<TaskSectionEntity?>(null) }
    var deletingSectionConfirm by remember { mutableStateOf<Pair<TaskSectionEntity, Int>?>(null) }

    val sectionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(openCreateDialogImmediately, state.taskSections) {
        if (openCreateDialogImmediately) {
            if (state.taskSections.isEmpty()) {
                showSectionCreator = true
            } else {
                activeInlineSectionId = state.taskSections.first().id
            }
            onConsumeCreateDialogFlag()
        }
    }

    LaunchedEffect(showSectionCreator) {
        if (showSectionCreator) {
            sectionFocusRequester.requestFocus()
        }
    }

    val today = state.todayEpochDay
    val filteredTasks = remember(state.tasks, viewMode, today) {
        state.tasks.filter { task ->
            when (viewMode) {
                "DAILY" -> task.deadlineEpochDay <= today
                "WEEKLY" -> task.deadlineEpochDay <= today + 7
                else -> true
            }
        }
    }

    val sectionIds = remember(state.taskSections) { state.taskSections.map { it.id }.toSet() }
    val ungroupedTasks = remember(filteredTasks, sectionIds) {
        filteredTasks.filter { it.sectionId == 0L || it.sectionId !in sectionIds }
    }

    val doneCount = filteredTasks.count { it.isCompleted }
    val progress = if (filteredTasks.isEmpty()) 0f else doneCount.toFloat() / filteredTasks.size

    fun submitNewSection() {
        val clean = newSectionName.trim()
        if (clean.isEmpty()) return
        onAddSection(clean)
        newSectionName = ""
        showSectionCreator = false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
            .testTag("tasks_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + Inline New Section trigger
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tasks", style = MaterialTheme.typography.headlineLarge, color = tokens.inkPrimary)
                    Text(
                        text = "Organize sections & add tasks inline",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkSecondary
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = { showSectionCreator = !showSectionCreator },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(38.dp).testTag("new_section_btn")
                ) {
                    Icon(
                        imageVector = if (showSectionCreator) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (showSectionCreator) "Close" else "New Section",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Inline Section Creator Bar
        item {
            AnimatedVisibility(
                visible = showSectionCreator,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ObsidianCard(
                    borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                    elevated = true,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Text(
                        text = "New Section",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newSectionName,
                            onValueChange = { newSectionName = it },
                            placeholder = { Text("e.g. Physics, Chemistry, Bio, Tests", maxLines = 1) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { submitNewSection() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                unfocusedBorderColor = tokens.borderSubtle,
                                focusedTextColor = tokens.inkPrimary,
                                unfocusedTextColor = tokens.inkPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(sectionFocusRequester)
                                .testTag("new_section_input")
                        )
                        FilledIconButton(
                            onClick = { submitNewSection() },
                            enabled = newSectionName.isNotBlank(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.size(48.dp).testTag("save_section_btn")
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = "Save section")
                        }
                    }
                }
            }
        }

        // Compact KUTTA Study Companion Strip
        item {
            val studyVisual = remember(state.kuttaState, state.tasks) {
                if (state.kuttaState.isTransientOverride) {
                    state.kuttaState
                } else if (state.pendingTasksCount > 0) {
                    KuttaEventHandler.stateForBaseline(
                        emotion = KuttaEmotion.FOCUSED,
                        dialogueIndex = state.completedTasksCount
                    )
                } else {
                    state.kuttaState
                }
            }
            ObsidianCard(elevated = true, contentPadding = PaddingValues(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KuttaMascot(
                        visualState = studyVisual,
                        size = 64.dp,
                        onTap = onTapKutta
                    )
                    Spacer(Modifier.width(10.dp))
                    KuttaDialogueBubble(
                        message = studyVisual.dialogue,
                        emotion = studyVisual.emotion,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Compact Progress Strip
        item {
            ObsidianCard(elevated = true, contentPadding = PaddingValues(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Task Progress", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                        Text(
                            text = if (filteredTasks.isEmpty()) "No tasks yet" else "$doneCount of ${filteredTasks.size} completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.inkSecondary
                        )
                    }
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                NeonProgressBar(progress = progress, heightDp = 6)
            }
        }

        // View Mode Filter Chips
        if (state.taskSections.isNotEmpty() || state.tasks.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL" to "All", "DAILY" to "Today", "WEEKLY" to "7 Days").forEach { (key, label) ->
                        FilterChip(
                            selected = viewMode == key,
                            onClick = { viewMode = key },
                            label = { Text(label, maxLines = 1, softWrap = false) }
                        )
                    }
                }
            }
        }

        // Empty Slate vs Permanent Sections
        if (state.taskSections.isEmpty() && ungroupedTasks.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.FolderOpen,
                    title = "No sections yet",
                    subtitle = "Chalo Malkin! Create subject or project sections (like Physics, Chemistry, Bio, or Tests) and type tasks right inside each card.",
                    actionLabel = "Create First Section",
                    onAction = { showSectionCreator = true },
                    modifier = Modifier.testTag("tasks_empty_state")
                )
            }
        } else {
            items(state.taskSections, key = { "sec_${it.id}" }) { section ->
                val sectionTasks = filteredTasks.filter { it.sectionId == section.id }
                TaskSectionCard(
                    title = section.name,
                    tasks = sectionTasks,
                    isInlineAdding = activeInlineSectionId == section.id,
                    onStartInlineAdd = { activeInlineSectionId = section.id },
                    onCloseInlineAdd = { activeInlineSectionId = null },
                    onQuickAddTask = { title -> onQuickAddTaskInSection(section.id, title) },
                    onToggleTask = onToggleTask,
                    onEditTask = { editingTask = it },
                    onDeleteTask = onDeleteTask,
                    onRenameSection = { renamingSection = section },
                    onDeleteSection = {
                        val totalInSection = state.tasks.count { it.sectionId == section.id }
                        if (totalInSection == 0) {
                            onDeleteSection(section)
                        } else {
                            deletingSectionConfirm = section to totalInSection
                        }
                    },
                    sectionTag = "section_card_${section.id}"
                )
            }

            if (ungroupedTasks.isNotEmpty()) {
                item(key = "sec_general") {
                    TaskSectionCard(
                        title = "General",
                        tasks = ungroupedTasks,
                        isInlineAdding = activeInlineSectionId == 0L,
                        onStartInlineAdd = { activeInlineSectionId = 0L },
                        onCloseInlineAdd = { activeInlineSectionId = null },
                        onQuickAddTask = { title -> onQuickAddTaskInSection(0L, title) },
                        onToggleTask = onToggleTask,
                        onEditTask = { editingTask = it },
                        onDeleteTask = onDeleteTask,
                        onRenameSection = null,
                        onDeleteSection = null,
                        sectionTag = "section_card_general"
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    editingTask?.let { task ->
        TaskEditorDialog(
            existing = task,
            todayEpochDay = today,
            onDismiss = { editingTask = null },
            onLogDistraction = { mins ->
                onLogTaskDistraction(task, mins)
                editingTask = null
            },
            onConfirm = { title, desc, priority, category, deadlineOffset, recurrence ->
                onSaveTask(
                    task.id,
                    task.sectionId,
                    title,
                    desc,
                    priority,
                    category,
                    today + deadlineOffset,
                    recurrence
                )
                editingTask = null
            }
        )
    }

    renamingSection?.let { section ->
        var updatedName by remember(section.id) { mutableStateOf(section.name) }
        AlertDialog(
            onDismissRequest = { renamingSection = null },
            containerColor = tokens.surfaceBase,
            title = { Text("Rename Section", style = MaterialTheme.typography.titleLarge, color = tokens.inkPrimary) },
            text = {
                OutlinedTextField(
                    value = updatedName,
                    onValueChange = { updatedName = it },
                    label = { Text("Section name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameSection(section, updatedName)
                        renamingSection = null
                    },
                    enabled = updatedName.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingSection = null }) { Text("Cancel") }
            }
        )
    }

    deletingSectionConfirm?.let { (section, taskCount) ->
        AlertDialog(
            onDismissRequest = { deletingSectionConfirm = null },
            containerColor = tokens.surfaceBase,
            title = { Text("Delete \"${section.name}\"?", color = tokens.inkPrimary) },
            text = { Text("This removes the section and its $taskCount task${if (taskCount == 1) "" else "s"}.", color = tokens.inkSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSection(section)
                        deletingSectionConfirm = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingSectionConfirm = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TaskSectionCard(
    title: String,
    tasks: List<TaskEntity>,
    isInlineAdding: Boolean,
    onStartInlineAdd: () -> Unit,
    onCloseInlineAdd: () -> Unit,
    onQuickAddTask: (String) -> Unit,
    onToggleTask: (TaskEntity) -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    onDeleteTask: (TaskEntity) -> Unit,
    onRenameSection: (() -> Unit)?,
    onDeleteSection: (() -> Unit)?,
    sectionTag: String
) {
    val tokens = LifeOsThemeTokensProvider.current
    var inlineDraft by remember { mutableStateOf("") }
    val inlineFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isInlineAdding) {
        if (isInlineAdding) {
            inlineFocusRequester.requestFocus()
        }
    }

    fun submitInline() {
        val clean = inlineDraft.trim()
        if (clean.isEmpty()) return
        onQuickAddTask(clean)
        inlineDraft = ""
        inlineFocusRequester.requestFocus()
    }

    val doneCount = tasks.count { it.isCompleted }

    ObsidianCard(
        modifier = Modifier.testTag(sectionTag),
        borderColor = if (isInlineAdding) MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f) else tokens.borderSubtle,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            StatusPill(
                text = "$doneCount/${tasks.size}",
                color = if (tasks.isNotEmpty() && doneCount == tasks.size) EmeraldSuccess else MaterialTheme.colorScheme.primary
            )
            if (onRenameSection != null) {
                IconButton(onClick = onRenameSection) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Rename section",
                        tint = tokens.inkSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (onDeleteSection != null) {
                IconButton(onClick = onDeleteSection) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Delete section",
                        tint = RoseAlert,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (tasks.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tasks.forEach { task ->
                    SectionTaskRow(
                        task = task,
                        onToggle = { onToggleTask(task) },
                        onEdit = { onEditTask(task) },
                        onDelete = { onDeleteTask(task) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (!isInlineAdding) {
            Surface(
                onClick = onStartInlineAdd,
                shape = RoundedCornerShape(tokens.smallRadius),
                color = tokens.surfaceElevated.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, tokens.borderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("${sectionTag}_add_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Add task",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = inlineDraft,
                    onValueChange = { inlineDraft = it },
                    placeholder = { Text("Add task... (press Done)", maxLines = 1) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitInline() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                        unfocusedBorderColor = tokens.borderSubtle,
                        focusedTextColor = tokens.inkPrimary,
                        unfocusedTextColor = tokens.inkPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(inlineFocusRequester)
                        .testTag("${sectionTag}_inline_input")
                )
                FilledIconButton(
                    onClick = { submitInline() },
                    enabled = inlineDraft.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(48.dp).testTag("${sectionTag}_inline_submit")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add task")
                }
                IconButton(
                    onClick = onCloseInlineAdd,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close inline entry", tint = tokens.inkMuted)
                }
            }
        }
    }
}

@Composable
private fun SectionTaskRow(
    task: TaskEntity,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val rowBg by animateColorAsState(
        targetValue = if (task.isCompleted) EmeraldSuccess.copy(alpha = 0.09f) else tokens.surfaceElevated,
        animationSpec = tween(220),
        label = "sec_task_bg"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (task.isCompleted) 1.14f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "sec_task_scale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (task.isCompleted) 0.68f else 1f,
        animationSpec = tween(220),
        label = "sec_task_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.smallRadius))
            .background(rowBg)
            .clickable(onClick = onToggle)
            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .graphicsLayer { alpha = contentAlpha }
            .testTag("task_card_${task.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (task.isCompleted) "Completed" else "Pending",
            tint = if (task.isCompleted) EmeraldSuccess else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (task.isCompleted) tokens.inkMuted else tokens.inkPrimary,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (task.description.isNotBlank() || task.distractionMinutes > 0 || task.focusMinutesSpent > 0) {
                val meta = buildList {
                    if (task.description.isNotBlank()) add(task.description)
                    if (task.focusMinutesSpent > 0) add("${task.focusMinutesSpent}m focused")
                    if (task.distractionMinutes > 0) add("${task.distractionMinutes}m distracted")
                }.joinToString(" · ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (task.distractionMinutes > 0) RoseAlert else tokens.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (task.xpAwarded) "Claimed" else "+${task.xpReward}XP",
            style = MaterialTheme.typography.labelSmall,
            color = if (task.xpAwarded) tokens.inkMuted else EmeraldSuccess,
            maxLines = 1,
            softWrap = false
        )
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit task", tint = tokens.inkSecondary, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete task", tint = RoseAlert, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TaskEditorDialog(
    existing: TaskEntity,
    todayEpochDay: Long,
    onDismiss: () -> Unit,
    onLogDistraction: (Int) -> Unit,
    onConfirm: (String, String, TaskPriority, TaskCategory, Int, RecurrencePattern) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    var title by remember { mutableStateOf(existing.title) }
    var description by remember { mutableStateOf(existing.description) }
    var priority by remember { mutableStateOf(existing.priority) }
    var category by remember { mutableStateOf(existing.category) }
    var recurrence by remember { mutableStateOf(existing.recurrence) }
    var dayOffset by remember {
        mutableIntStateOf((existing.deadlineEpochDay - todayEpochDay).toInt().coerceIn(0, 30))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceBase,
        title = {
            Text("Edit Task", style = MaterialTheme.typography.titleLarge, color = tokens.inkPrimary)
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
                    label = { Text("Notes (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Priority", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskPriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text("${p.label} (+${p.xpValue}XP)", maxLines = 1, softWrap = false) }
                        )
                    }
                }
                Text("Category", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskCategory.entries.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c.label, maxLines = 1, softWrap = false) }
                        )
                    }
                }
                Text("Deadline", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0 to "Today", 1 to "Tomorrow", 3 to "+3 Days", 7 to "Next Week").forEach { (off, lbl) ->
                        FilterChip(
                            selected = dayOffset == off,
                            onClick = { dayOffset = off },
                            label = { Text(lbl, maxLines = 1, softWrap = false) }
                        )
                    }
                }
                Text("Repeat", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RecurrencePattern.entries.forEach { rec ->
                        FilterChip(
                            selected = recurrence == rec,
                            onClick = { recurrence = rec },
                            label = { Text(rec.label, maxLines = 1, softWrap = false) }
                        )
                    }
                }
                // Log Distraction quick actions for this task
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onLogDistraction(10) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = RoseAlert, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("+10m Distracted", style = MaterialTheme.typography.labelMedium, color = RoseAlert, maxLines = 1, softWrap = false)
                    }
                    if (existing.distractionMinutes > 0) {
                        Text(
                            text = "${existing.distractionMinutes}m total",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.inkSecondary
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
