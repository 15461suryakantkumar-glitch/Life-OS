package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.CalendarEventEntity
import com.example.data.local.RecurrencePattern
import com.example.data.local.TaskCategory
import com.example.ui.components.CategoryBadge
import com.example.ui.components.ObsidianCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.ObsidianBase
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.LifeOsUiState
import com.example.util.DateTimeUtils
import java.util.Calendar

@Composable
fun CalendarScreen(
    state: LifeOsUiState,
    onSaveEvent: (String, String, Long, Int, Int, TaskCategory, RecurrencePattern, Int, Boolean) -> Unit,
    onDeleteEvent: (CalendarEventEntity) -> Unit
) {
    val today = state.todayEpochDay
    var selectedEpochDay by remember { mutableLongStateOf(today) }
    var calendarView by remember { mutableStateOf("MONTH") } // MONTH | WEEK | DAY
    var showCreateDialog by remember { mutableStateOf(false) }

    val initialCal = remember { Calendar.getInstance() }
    var displayedYear by remember { mutableIntStateOf(initialCal.get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableIntStateOf(initialCal.get(Calendar.MONTH)) }

    val countdownEvents = remember(state.events, today) {
        state.events.filter { it.isCountdown && it.dateEpochDay >= today }
    }

    val selectedDayEvents = remember(state.events, selectedEpochDay) {
        state.events.filter {
            it.dateEpochDay == selectedEpochDay ||
                (it.recurrence == RecurrencePattern.DAILY && it.dateEpochDay <= selectedEpochDay)
        }
    }

    val selectedDayTasks = remember(state.tasks, selectedEpochDay) {
        state.tasks.filter { it.deadlineEpochDay == selectedEpochDay }
    }

    Scaffold(
        containerColor = ObsidianVoid,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_event_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create Event or Countdown")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("calendar_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Smart Calendar", style = MaterialTheme.typography.headlineLarge, color = InkPrimary)
                Text("Schedule blocks, recurring events, task deadlines & countdowns", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            }

            // View mode switch: Monthly / Weekly / Daily
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("MONTH" to "Monthly", "WEEK" to "Weekly", "DAY" to "Daily").forEach { (key, lbl) ->
                        FilterChip(
                            selected = calendarView == key,
                            onClick = { calendarView = key },
                            label = { Text(lbl) }
                        )
                    }
                }
            }

            // Monthly Grid or Weekly Strip
            if (calendarView == "MONTH") {
                item {
                    MonthlyCalendarGrid(
                        year = displayedYear,
                        monthZeroBased = displayedMonth,
                        todayEpochDay = today,
                        selectedEpochDay = selectedEpochDay,
                        eventDays = state.events.map { it.dateEpochDay }.toSet(),
                        taskDays = state.tasks.map { it.deadlineEpochDay }.toSet(),
                        onPrevMonth = {
                            if (displayedMonth == 0) {
                                displayedMonth = 11
                                displayedYear -= 1
                            } else {
                                displayedMonth -= 1
                            }
                        },
                        onNextMonth = {
                            if (displayedMonth == 11) {
                                displayedMonth = 0
                                displayedYear += 1
                            } else {
                                displayedMonth += 1
                            }
                        },
                        onSelectDay = { selectedEpochDay = it }
                    )
                }
            } else if (calendarView == "WEEK") {
                item {
                    WeeklyCalendarStrip(
                        startDay = today,
                        selectedEpochDay = selectedEpochDay,
                        eventDays = state.events.map { it.dateEpochDay }.toSet(),
                        onSelectDay = { selectedEpochDay = it }
                    )
                }
            }

            // Important Date Countdowns
            if (countdownEvents.isNotEmpty()) {
                item {
                    SectionHeader(title = "Important Countdowns", subtitle = "Days remaining tracker")
                }
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        countdownEvents.forEach { cd ->
                            val daysLeft = DateTimeUtils.daysBetween(today, cd.dateEpochDay).coerceAtLeast(0)
                            ObsidianCard(
                                modifier = Modifier.width(190.dp),
                                elevated = true
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusPill(text = "${daysLeft} DAYS LEFT", color = AmberWarning, icon = Icons.Filled.Flag)
                                    IconButton(
                                        onClick = { onDeleteEvent(cd) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete countdown", tint = InkMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(cd.title, style = MaterialTheme.typography.titleMedium, color = InkPrimary, maxLines = 2)
                                Spacer(Modifier.height(4.dp))
                                Text(DateTimeUtils.formatFullDate(cd.dateEpochDay), style = MaterialTheme.typography.labelSmall, color = InkSecondary)
                            }
                        }
                    }
                }
            }

            // Selected Day Agenda
            item {
                SectionHeader(
                    title = "Agenda · ${DateTimeUtils.formatFullDate(selectedEpochDay)}",
                    subtitle = "${selectedDayEvents.size} events · ${selectedDayTasks.size} task deadlines"
                )
            }

            if (selectedDayEvents.isEmpty() && selectedDayTasks.isEmpty()) {
                item {
                    ObsidianCard {
                        Text("No events or deadlines on this date.", style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
                    }
                }
            } else {
                items(selectedDayEvents, key = { "ev_${it.id}" }) { event ->
                    ObsidianCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.width(68.dp)) {
                                Text(
                                    text = DateTimeUtils.formatTimeOfDay(event.startMinuteOfDay),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "${event.durationMinutes}m",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkMuted
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(event.title, style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                                if (event.notes.isNotBlank()) {
                                    Text(event.notes, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CategoryBadge(event.category)
                                    if (event.reminderMinutesBefore > 0) {
                                        StatusPill(
                                            text = "${event.reminderMinutesBefore}m alert",
                                            color = MaterialTheme.colorScheme.primary,
                                            icon = Icons.Filled.NotificationsActive
                                        )
                                    }
                                    if (event.recurrence != RecurrencePattern.NONE) {
                                        StatusPill(
                                            text = event.recurrence.label,
                                            color = MaterialTheme.colorScheme.secondary,
                                            icon = Icons.Filled.Repeat
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { onDeleteEvent(event) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete event", tint = RoseAlert)
                            }
                        }
                    }
                }

                items(selectedDayTasks, key = { "task_${it.id}" }) { task ->
                    ObsidianCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Task Deadline", style = MaterialTheme.typography.labelSmall, color = AmberWarning)
                                Text(task.title, style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                            }
                            CategoryBadge(task.category)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showCreateDialog) {
        CreateEventDialog(
            initialEpochDay = selectedEpochDay,
            onDismiss = { showCreateDialog = false },
            onSave = { title, notes, hour, duration, cat, rec, reminder, isCountdown ->
                onSaveEvent(
                    title,
                    notes,
                    selectedEpochDay,
                    hour * 60,
                    duration,
                    cat,
                    rec,
                    reminder,
                    isCountdown
                )
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun MonthlyCalendarGrid(
    year: Int,
    monthZeroBased: Int,
    todayEpochDay: Long,
    selectedEpochDay: Long,
    eventDays: Set<Long>,
    taskDays: Set<Long>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (Long) -> Unit
) {
    val firstCal = remember(year, monthZeroBased) {
        Calendar.getInstance().apply { set(year, monthZeroBased, 1) }
    }
    val daysInMonth = firstCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeekOffset = (firstCal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY).coerceIn(0, 6)

    ObsidianCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                text = DateTimeUtils.formatMonthYear(year, monthZeroBased),
                style = MaterialTheme.typography.titleLarge,
                color = InkPrimary
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { dow ->
                Text(
                    text = dow,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val totalCells = firstDayOfWeekOffset + daysInMonth
        val rows = (totalCells + 6) / 7
        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val cellIdx = r * 7 + c
                    val dayNum = cellIdx - firstDayOfWeekOffset + 1
                    if (dayNum in 1..daysInMonth) {
                        val epoch = DateTimeUtils.epochDayFromYearMonthDay(year, monthZeroBased, dayNum)
                        val isSelected = epoch == selectedEpochDay
                        val isToday = epoch == todayEpochDay
                        val hasMarker = eventDays.contains(epoch) || taskDays.contains(epoch)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isToday -> CharcoalElevated
                                        else -> Color.Transparent
                                    }
                                )
                                .then(
                                    if (isToday && !isSelected) {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(10.dp))
                                    } else Modifier
                                )
                                .clickable { onSelectDay(epoch) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = dayNum.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) Color.White else InkPrimary,
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                )
                                if (hasMarker) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color.White else MaterialTheme.colorScheme.secondary)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f).height(42.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun WeeklyCalendarStrip(
    startDay: Long,
    selectedEpochDay: Long,
    eventDays: Set<Long>,
    onSelectDay: (Long) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        (0..6).forEach { offset ->
            val day = startDay + offset
            val isSelected = day == selectedEpochDay
            val label = DateTimeUtils.formatFullDate(day)
            val shortDay = label.substringBefore(",")
            val num = label.substringAfterLast(" ")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else CharcoalElevated)
                    .clickable { onSelectDay(day) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(shortDay, style = MaterialTheme.typography.labelSmall, color = if (isSelected) Color.White else InkSecondary)
                Spacer(Modifier.height(4.dp))
                Text(num, style = MaterialTheme.typography.titleMedium, color = if (isSelected) Color.White else InkPrimary)
                Spacer(Modifier.height(4.dp))
                if (eventDays.contains(day)) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color.White else MaterialTheme.colorScheme.secondary)
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateEventDialog(
    initialEpochDay: Long,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Int, TaskCategory, RecurrencePattern, Int, Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var startHour by remember { mutableIntStateOf(9) }
    var duration by remember { mutableIntStateOf(60) }
    var category by remember { mutableStateOf(TaskCategory.STUDY) }
    var recurrence by remember { mutableStateOf(RecurrencePattern.NONE) }
    var reminder by remember { mutableIntStateOf(15) }
    var isCountdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianBase,
        title = { Text("Schedule · ${DateTimeUtils.formatFullDate(initialEpochDay)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event or milestone title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("event_title_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Location / Notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Start Time", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(8, 9, 11, 14, 17, 20).forEach { hr ->
                        FilterChip(
                            selected = startHour == hr,
                            onClick = { startHour = hr },
                            label = { Text(DateTimeUtils.formatTimeOfDay(hr * 60)) }
                        )
                    }
                }
                Text("Duration & Alert", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(30, 60, 90, 120).forEach { dur ->
                        FilterChip(
                            selected = duration == dur,
                            onClick = { duration = dur },
                            label = { Text("${dur}m") }
                        )
                    }
                    listOf(10, 15, 30).forEach { rem ->
                        FilterChip(
                            selected = reminder == rem,
                            onClick = { reminder = rem },
                            label = { Text("${rem}m alert") }
                        )
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskCategory.entries.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isCountdown, onCheckedChange = { isCountdown = it })
                    Text("Pin as Important Countdown Milestone", style = MaterialTheme.typography.bodySmall, color = InkPrimary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, notes, startHour, duration, category, recurrence, reminder, isCountdown) },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_event_confirm_btn")
            ) {
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
