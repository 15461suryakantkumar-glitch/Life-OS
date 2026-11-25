package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.StudyChapterEntity
import com.example.data.local.StudyChapterStatus
import com.example.data.local.StudyResourceEntity
import com.example.data.local.StudyResourceType
import com.example.data.local.StudySessionMode
import com.example.data.local.SubjectEntity
import com.example.data.local.SuruStudyPlanEntity
import com.example.ui.components.FeeloraScreenBackdrop
import com.example.ui.components.FeeloraSegmentedRow
import com.example.ui.components.FeeloraTopHeader
import com.example.ui.components.GradientGlowCard
import com.example.ui.components.NeonProgressBar
import com.example.ui.components.ObsidianCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FeeloraTypeHelpers
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.LifeOsUiState
import com.example.util.DateTimeUtils

private enum class StudySubTab(val label: String) {
    OVERVIEW("Overview"),
    SUBJECTS("Subjects"),
    PLANNER("Planner"),
    FOCUS("Focus"),
    NOTES("Notes")
}

private val subjectColorSwatches = listOf(
    "#9274D8" to "Lavender",
    "#7054B5" to "Plum",
    "#E3977C" to "Peach",
    "#C8829C" to "Rose",
    "#3B82F6" to "Sky",
    "#10B981" to "Sage"
)

@Composable
fun StudyScreen(
    state: LifeOsUiState,
    onSaveSubject: (Long?, String, String, String, Int, Int) -> Unit,
    onDeleteSubject: (SubjectEntity) -> Unit,
    onSaveChapter: (Long?, Long, String, String, Int, StudyChapterStatus) -> Unit,
    onToggleChapterCompleted: (StudyChapterEntity) -> Unit,
    onDeleteChapter: (StudyChapterEntity) -> Unit,
    onSaveResource: (Long?, Long, Long?, String, String, StudyResourceType) -> Unit,
    onDeleteResource: (StudyResourceEntity) -> Unit,
    onConfigureTimer: (Int, Int, StudySessionMode) -> Unit,
    onSelectFocusSubjectAndChapter: (Long?, Long?) -> Unit,
    onToggleBreakPhase: (Boolean) -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onFinishTimerEarly: () -> Unit,
    onUpdateStudyPlanStatus: (SuruStudyPlanEntity, String) -> Unit,
    onOpenSuru: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    var activeTab by remember { mutableStateOf(StudySubTab.OVERVIEW) }
    var selectedSubjectId by remember(state.studySubjects) {
        mutableLongStateOf(state.studySubjects.firstOrNull()?.id ?: -1L)
    }

    var showSubjectDialog by remember { mutableStateOf(false) }
    var editingSubject by remember { mutableStateOf<SubjectEntity?>(null) }

    var showChapterDialog by remember { mutableStateOf(false) }
    var chapterSubjectTargetId by remember { mutableLongStateOf(-1L) }
    var editingChapter by remember { mutableStateOf<StudyChapterEntity?>(null) }

    var showResourceDialog by remember { mutableStateOf(false) }
    var editingResource by remember { mutableStateOf<StudyResourceEntity?>(null) }

    var showCustomTimerDialog by remember { mutableStateOf(false) }

    val selectedSubject = state.studySubjects.firstOrNull { it.id == selectedSubjectId }
        ?: state.studySubjects.firstOrNull()

    FeeloraScreenBackdrop(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("study_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FeeloraTopHeader(
                    title = "Study",
                    subtitle = "Subjects, chapters, deep focus & quiet notes",
                    userName = state.prefs.userName,
                    avatarPreset = state.prefs.avatarPreset,
                    avatarUri = state.prefs.avatarPhotoUri,
                    unreadNotifications = state.unreadNotificationsCount,
                    streakDays = state.prefs.dailyStreak,
                    onOpenDrawer = onOpenDrawer,
                    onOpenNotifications = onOpenNotifications,
                    onOpenProfile = onOpenProfile,
                    trailingAction = {
                        IconButton(
                            onClick = {
                                editingSubject = null
                                showSubjectDialog = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("study_add_subject_icon_btn")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Subject", tint = tokens.inkPrimary)
                        }
                    }
                )
            }

            item {
                FeeloraSegmentedRow(
                    items = StudySubTab.entries,
                    selectedItem = activeTab,
                    labelFor = { it.label },
                    onSelect = { activeTab = it },
                    tagPrefix = "study_tab"
                )
            }

            when (activeTab) {
                StudySubTab.OVERVIEW -> {
                    item {
                        GradientGlowCard(contentPadding = PaddingValues(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "TODAY'S STUDY FLOW",
                                        style = FeeloraTypeHelpers.editorialTag,
                                        color = tokens.deepPrimary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = DateTimeUtils.formatMinutesCompact(state.prefs.focusMinutesLoggedToday),
                                        style = FeeloraTypeHelpers.statNumber.copy(fontFeatureSettings = "tnum"),
                                        color = tokens.inkPrimary
                                    )
                                    Text(
                                        text = "of ${DateTimeUtils.formatMinutesCompact(state.prefs.dailyFocusGoalMinutes)} daily goal",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                        color = tokens.inkSecondary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatusPill(
                                        text = "${state.studySubjects.size} Subjects",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    StatusPill(
                                        text = "${state.completedChaptersCount}/${state.studyChapters.size} Chapters",
                                        color = EmeraldSuccess
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            val goalProgress = (state.prefs.focusMinutesLoggedToday.toFloat() /
                                state.prefs.dailyFocusGoalMinutes.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                            NeonProgressBar(progress = goalProgress, heightDp = 8)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { activeTab = StudySubTab.FOCUS },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("study_start_focus_btn")
                                ) {
                                    Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Focus Timer", maxLines = 1, softWrap = false)
                                }
                                OutlinedButton(
                                    onClick = onOpenSuru,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("study_plan_suru_btn")
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Ask SURU", maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }

                    item {
                        SectionHeader(
                            title = "Your Subjects",
                            subtitle = if (state.studySubjects.isEmpty()) "Create your first study subject" else "Track chapter mastery"
                        )
                    }

                    if (state.studySubjects.isEmpty()) {
                        item {
                            StudyEmptyCard(
                                icon = Icons.Filled.LibraryBooks,
                                title = "No subjects added yet",
                                body = "Add subjects like Physics, Literature, or Coding to organize chapters, track completion, and link Pomodoro sessions.",
                                actionLabel = "Add First Subject",
                                testTag = "study_empty_add_subject_btn",
                                onAction = {
                                    editingSubject = null
                                    showSubjectDialog = true
                                }
                            )
                        }
                    } else {
                        items(state.studySubjects, key = { it.id }) { subject ->
                            SubjectOverviewCard(
                                subject = subject,
                                chapters = state.studyChapters.filter { it.subjectId == subject.id },
                                progress = state.progressForSubject(subject.id),
                                onOpenSubject = {
                                    selectedSubjectId = subject.id
                                    activeTab = StudySubTab.SUBJECTS
                                },
                                onStartFocus = {
                                    onSelectFocusSubjectAndChapter(subject.id, null)
                                    activeTab = StudySubTab.FOCUS
                                }
                            )
                        }
                    }

                    val pendingChapters = state.studyChapters.filter { !it.isCompleted }.take(5)
                    if (pendingChapters.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Next Chapters", subtitle = "Tap check to earn +40 XP")
                        }
                        items(pendingChapters, key = { "ov_${it.id}" }) { chapter ->
                            val subjName = state.studySubjects.firstOrNull { it.id == chapter.subjectId }?.name ?: "Study"
                            ChapterRowCard(
                                chapter = chapter,
                                subjectLabel = subjName,
                                onToggleComplete = { onToggleChapterCompleted(chapter) },
                                onEdit = {
                                    editingChapter = chapter
                                    chapterSubjectTargetId = chapter.subjectId
                                    showChapterDialog = true
                                },
                                onDelete = { onDeleteChapter(chapter) }
                            )
                        }
                    }

                    item {
                        QuickFocusTimerCard(
                            remainingSeconds = state.studyTimer.remainingSeconds,
                            isRunning = state.studyTimer.isRunning,
                            isBreakPhase = state.studyTimer.isBreakPhase,
                            onToggleTimer = onToggleTimer,
                            onOpenFocusTab = { activeTab = StudySubTab.FOCUS }
                        )
                    }
                }

                StudySubTab.SUBJECTS -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader(title = "Subjects & Chapters", subtitle = "Structured curriculum tracker")
                            OutlinedButton(
                                onClick = {
                                    editingSubject = null
                                    showSubjectDialog = true
                                },
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .testTag("study_new_subject_btn")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Subject", maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    if (state.studySubjects.isEmpty()) {
                        item {
                            StudyEmptyCard(
                                icon = Icons.Filled.MenuBook,
                                title = "Build your study syllabus",
                                body = "Organize chapters under subjects to visualize completion percentages and launch focused sprints.",
                                actionLabel = "Create Subject",
                                testTag = "study_subjects_empty_btn",
                                onAction = {
                                    editingSubject = null
                                    showSubjectDialog = true
                                }
                            )
                        }
                    } else {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                state.studySubjects.forEach { subj ->
                                    val selected = subj.id == selectedSubject?.id
                                    val accent = parseHexColor(subj.colorHex, MaterialTheme.colorScheme.primary)
                                    FilterChip(
                                        selected = selected,
                                        onClick = { selectedSubjectId = subj.id },
                                        label = { Text(subj.name, maxLines = 1, softWrap = false) },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(accent)
                                            )
                                        },
                                        modifier = Modifier.testTag("study_subject_chip_${subj.id}")
                                    )
                                }
                            }
                        }

                        selectedSubject?.let { subject ->
                            val chapters = state.studyChapters.filter { it.subjectId == subject.id }
                            val progress = state.progressForSubject(subject.id)
                            val accent = parseHexColor(subject.colorHex, tokens.primary)
                            item {
                                ObsidianCard(elevated = true, borderColor = accent.copy(alpha = 0.45f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(accent.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Filled.MenuBook, contentDescription = null, tint = accent)
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(subject.name, style = MaterialTheme.typography.titleLarge, color = tokens.inkPrimary)
                                            Text(
                                                "${chapters.count { it.isCompleted }} of ${chapters.size} chapters · ${subject.targetMinutesPerDay}m/day target",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = tokens.inkSecondary
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                editingSubject = subject
                                                showSubjectDialog = true
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit Subject", tint = tokens.inkSecondary)
                                        }
                                        IconButton(
                                            onClick = { onDeleteSubject(subject) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete Subject", tint = RoseAlert)
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    NeonProgressBar(progress = progress, heightDp = 8)
                                    Spacer(Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            editingChapter = null
                                            chapterSubjectTargetId = subject.id
                                            showChapterDialog = true
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("study_add_chapter_btn")
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Add Chapter to ${subject.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }

                            if (chapters.isEmpty()) {
                                item {
                                    ObsidianCard {
                                        Text(
                                            "No chapters under ${subject.name} yet. Tap \"Add Chapter\" above to break down your syllabus.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = tokens.inkSecondary
                                        )
                                    }
                                }
                            } else {
                                items(chapters, key = { "ch_${it.id}" }) { chapter ->
                                    ChapterRowCard(
                                        chapter = chapter,
                                        subjectLabel = null,
                                        onToggleComplete = { onToggleChapterCompleted(chapter) },
                                        onEdit = {
                                            editingChapter = chapter
                                            chapterSubjectTargetId = subject.id
                                            showChapterDialog = true
                                        },
                                        onDelete = { onDeleteChapter(chapter) }
                                    )
                                }
                            }
                        }

                        item {
                            QuickFocusTimerCard(
                                remainingSeconds = state.studyTimer.remainingSeconds,
                                isRunning = state.studyTimer.isRunning,
                                isBreakPhase = state.studyTimer.isBreakPhase,
                                onToggleTimer = onToggleTimer,
                                onOpenFocusTab = { activeTab = StudySubTab.FOCUS }
                            )
                        }
                    }
                }

                StudySubTab.PLANNER -> {
                    item {
                        ObsidianCard(borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("SURU Study Planner", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                                    Text(
                                        "Tell SURU your chapters & available hours to schedule conflict-free study blocks.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.inkSecondary
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = onOpenSuru,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("study_planner_open_suru_btn")
                            ) {
                                Text("Plan with SURU AI")
                            }
                        }
                    }

                    if (state.suruStudyPlans.isEmpty()) {
                        item {
                            StudyEmptyCard(
                                icon = Icons.Filled.Schedule,
                                title = "No study plans saved yet",
                                body = "Ask SURU: \"I have 3 chapters to complete and 4 hours available\" to build an adaptive plan synced with Tasks & Calendar.",
                                actionLabel = "Open SURU Planner",
                                testTag = "study_planner_empty_btn",
                                onAction = onOpenSuru
                            )
                        }
                    } else {
                        items(state.suruStudyPlans, key = { it.id }) { plan ->
                            ObsidianCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(plan.subject, style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                                        Text(
                                            "${DateTimeUtils.formatMinutesCompact(plan.dailyMinutesBudget)}/day · ${plan.deadlineDays}d · ${plan.preferredWindow}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    StatusPill(
                                        text = plan.status,
                                        color = if (plan.status == "COMPLETED") EmeraldSuccess else MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (plan.chaptersCsv.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Chapters: ${plan.chaptersCsv}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.inkSecondary
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val nextStatus = if (plan.status == "COMPLETED") "ACTIVE" else "COMPLETED"
                                    OutlinedButton(
                                        onClick = { onUpdateStudyPlanStatus(plan, nextStatus) },
                                        modifier = Modifier.heightIn(min = 42.dp)
                                    ) {
                                        Text(if (plan.status == "COMPLETED") "Reopen Plan" else "Mark Complete")
                                    }
                                }
                            }
                        }
                    }
                }

                StudySubTab.FOCUS -> {
                    item {
                        val timer = state.studyTimer
                        val totalSecs = (if (timer.isBreakPhase) timer.breakMinutes else timer.selectedMinutes).coerceAtLeast(1) * 60
                        val progress = 1f - (timer.remainingSeconds.toFloat() / totalSecs.toFloat()).coerceIn(0f, 1f)
                        val trackColor = tokens.borderSubtle
                        val ringColor = if (timer.isBreakPhase) EmeraldSuccess else MaterialTheme.colorScheme.primary

                        GradientGlowCard(contentPadding = PaddingValues(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (timer.isBreakPhase) "GENTLE BREAK" else timer.sessionMode.label.uppercase(),
                                        style = FeeloraTypeHelpers.editorialTag,
                                        color = ringColor
                                    )
                                    Text(
                                        text = if (timer.pomodoroCyclesCompleted > 0) "${timer.pomodoroCyclesCompleted} cycle(s) logged" else "Deep Focus Session",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.inkSecondary
                                    )
                                }
                                OutlinedButton(
                                    onClick = { showCustomTimerDialog = true },
                                    modifier = Modifier.heightIn(min = 40.dp).testTag("study_custom_timer_btn")
                                ) {
                                    Text("Custom", maxLines = 1, softWrap = false)
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(176.dp)) {
                                    val strokeWidth = 11.dp.toPx()
                                    drawArc(
                                        color = trackColor,
                                        startAngle = -90f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = ringColor,
                                        startAngle = -90f,
                                        sweepAngle = progress * 360f,
                                        useCenter = false,
                                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val mins = timer.remainingSeconds / 60
                                    val secs = timer.remainingSeconds % 60
                                    Text(
                                        text = "%02d:%02d".format(mins, secs),
                                        style = FeeloraTypeHelpers.timerDisplay.copy(fontFeatureSettings = "tnum"),
                                        color = tokens.inkPrimary
                                    )
                                    Text(
                                        text = if (timer.isBreakPhase) "Rest your eyes" else "Stay present",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = tokens.inkSecondary
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // Preset modes
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    Triple(25, 5, "Pomodoro 25/5"),
                                    Triple(50, 10, "Deep 50/10"),
                                    Triple(15, 3, "Sprint 15/3")
                                ).forEach { (w, b, lbl) ->
                                    val selected = !timer.isBreakPhase && timer.selectedMinutes == w && timer.breakMinutes == b
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onConfigureTimer(w, b, StudySessionMode.POMODORO) },
                                        label = { Text(lbl, maxLines = 1, softWrap = false) }
                                    )
                                }
                            }

                            // Subject Linker
                            if (state.studySubjects.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Focus Subject", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = timer.activeSubjectId == null,
                                        onClick = { onSelectFocusSubjectAndChapter(null, null) },
                                        label = { Text("General") }
                                    )
                                    state.studySubjects.forEach { subj ->
                                        FilterChip(
                                            selected = timer.activeSubjectId == subj.id,
                                            onClick = { onSelectFocusSubjectAndChapter(subj.id, null) },
                                            label = { Text(subj.name, maxLines = 1, softWrap = false) }
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onToggleTimer,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("study_timer_play_pause_btn")
                                ) {
                                    Icon(
                                        imageVector = if (timer.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (timer.isRunning) "Pause" else "Start", maxLines = 1, softWrap = false)
                                }
                                OutlinedButton(
                                    onClick = { onToggleBreakPhase(!timer.isBreakPhase) },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("study_timer_break_btn")
                                ) {
                                    Icon(Icons.Filled.Coffee, contentDescription = "Toggle Break", modifier = Modifier.size(18.dp))
                                }
                                OutlinedButton(
                                    onClick = onResetTimer,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("study_timer_reset_btn")
                                ) {
                                    Icon(Icons.Filled.Replay, contentDescription = "Reset Timer", modifier = Modifier.size(18.dp))
                                }
                                OutlinedButton(
                                    onClick = onFinishTimerEarly,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("study_timer_finish_btn")
                                ) {
                                    Icon(Icons.Filled.Stop, contentDescription = "Finish & Save Session", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    item {
                        SectionHeader(title = "Recent Focus Sessions", subtitle = "Logged locally in Feelora Study")
                    }
                    if (state.studySessions.isEmpty()) {
                        item {
                            ObsidianCard {
                                Text(
                                    "Complete a Pomodoro or Focus Timer session to see your session log here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = tokens.inkSecondary
                                )
                            }
                        }
                    } else {
                        items(state.studySessions.take(10), key = { it.id }) { session ->
                            val subjName = session.subjectId?.let { id ->
                                state.studySubjects.firstOrNull { it.id == id }?.name
                            } ?: "Focused Study"
                            ObsidianCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(subjName, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                        Text(
                                            "${session.mode.label} · ${DateTimeUtils.formatFullDate(session.dateEpochDay)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.inkSecondary
                                        )
                                    }
                                    StatusPill(
                                        text = "${session.actualMinutes}m",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                StudySubTab.NOTES -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader(title = "Study Notes & Resources", subtitle = "Formulas, summaries & links")
                            OutlinedButton(
                                onClick = {
                                    if (state.studySubjects.isEmpty()) {
                                        editingSubject = null
                                        showSubjectDialog = true
                                    } else {
                                        editingResource = null
                                        showResourceDialog = true
                                    }
                                },
                                modifier = Modifier.heightIn(min = 44.dp).testTag("study_add_note_btn")
                            ) {
                                Icon(Icons.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Note", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    if (state.studyResources.isEmpty()) {
                        item {
                            StudyEmptyCard(
                                icon = Icons.Filled.NoteAdd,
                                title = "Quiet space for study notes",
                                body = "Save concise key concepts, chapter summaries, or reference links neatly grouped by subject.",
                                actionLabel = if (state.studySubjects.isEmpty()) "Create Subject First" else "New Study Note",
                                testTag = "study_notes_empty_btn",
                                onAction = {
                                    if (state.studySubjects.isEmpty()) {
                                        editingSubject = null
                                        showSubjectDialog = true
                                    } else {
                                        editingResource = null
                                        showResourceDialog = true
                                    }
                                }
                            )
                        }
                    } else {
                        items(state.studyResources, key = { it.id }) { res ->
                            val subjName = state.studySubjects.firstOrNull { it.id == res.subjectId }?.name ?: "Subject"
                            ObsidianCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            StatusPill(text = res.type.label, color = MaterialTheme.colorScheme.secondary)
                                            Text(subjName, style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(res.title, style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                                    }
                                    IconButton(
                                        onClick = {
                                            editingResource = res
                                            showResourceDialog = true
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit Note", tint = tokens.inkSecondary)
                                    }
                                    IconButton(
                                        onClick = { onDeleteResource(res) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete Note", tint = RoseAlert)
                                    }
                                }
                                if (res.content.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(res.content, style = MaterialTheme.typography.bodyMedium, color = tokens.inkSecondary)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showSubjectDialog) {
        SubjectEditorDialog(
            initial = editingSubject,
            onDismiss = { showSubjectDialog = false },
            onSave = { name, colorHex, dailyMins, weeklyMins ->
                onSaveSubject(editingSubject?.id, name, colorHex, "MenuBook", dailyMins, weeklyMins)
                showSubjectDialog = false
            }
        )
    }

    if (showChapterDialog && chapterSubjectTargetId > 0L) {
        ChapterEditorDialog(
            initial = editingChapter,
            onDismiss = { showChapterDialog = false },
            onSave = { title, notes, estMins ->
                onSaveChapter(
                    editingChapter?.id,
                    chapterSubjectTargetId,
                    title,
                    notes,
                    estMins,
                    editingChapter?.chapterStatus ?: StudyChapterStatus.PENDING
                )
                showChapterDialog = false
            }
        )
    }

    if (showResourceDialog && state.studySubjects.isNotEmpty()) {
        ResourceEditorDialog(
            initial = editingResource,
            subjects = state.studySubjects,
            defaultSubjectId = selectedSubject?.id ?: state.studySubjects.first().id,
            onDismiss = { showResourceDialog = false },
            onSave = { subjId, title, content, type ->
                onSaveResource(editingResource?.id, subjId, null, title, content, type)
                showResourceDialog = false
            }
        )
    }

    if (showCustomTimerDialog) {
        CustomFocusDialog(
            initialWork = state.studyTimer.selectedMinutes,
            initialBreak = state.studyTimer.breakMinutes,
            onDismiss = { showCustomTimerDialog = false },
            onConfirm = { work, brk ->
                onConfigureTimer(work, brk, StudySessionMode.CUSTOM)
                showCustomTimerDialog = false
            }
        )
    }
}

@Composable
private fun QuickFocusTimerCard(
    remainingSeconds: Int,
    isRunning: Boolean,
    isBreakPhase: Boolean,
    onToggleTimer: () -> Unit,
    onOpenFocusTab: () -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val mins = remainingSeconds / 60
    val secs = remainingSeconds % 60
    ObsidianCard(
        onClick = onOpenFocusTab,
        elevated = true,
        borderColor = tokens.borderAccent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isBreakPhase) "Break Timer" else "Focus Timer",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.inkPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "%02d:%02d".format(mins, secs),
                    style = FeeloraTypeHelpers.timerDisplay.copy(fontFeatureSettings = "tnum"),
                    color = tokens.inkPrimary
                )
            }
            Surface(
                onClick = onToggleTimer,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier
                    .size(54.dp)
                    .testTag("study_quick_timer_play_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isRunning) "Pause timer" else "Start timer",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectOverviewCard(
    subject: SubjectEntity,
    chapters: List<StudyChapterEntity>,
    progress: Float,
    onOpenSubject: () -> Unit,
    onStartFocus: () -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val accent = parseHexColor(subject.colorHex, MaterialTheme.colorScheme.primary)
    ObsidianCard(onClick = onOpenSubject) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(subject.name, style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                Text(
                    "${(progress * 100).toInt()}% · ${chapters.size} Chapters",
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = tokens.inkSecondary
                )
            }
            OutlinedButton(
                onClick = onStartFocus,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.heightIn(min = 40.dp)
            ) {
                Text("Focus", maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = tokens.inkMuted
            )
        }
        Spacer(Modifier.height(10.dp))
        NeonProgressBar(progress = progress, heightDp = 7)
    }
}

@Composable
private fun ChapterRowCard(
    chapter: StudyChapterEntity,
    subjectLabel: String?,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    ObsidianCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleComplete,
                modifier = Modifier.size(48.dp).testTag("study_chapter_toggle_${chapter.id}")
            ) {
                Icon(
                    imageVector = if (chapter.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (chapter.isCompleted) "Mark chapter incomplete" else "Complete chapter",
                    tint = if (chapter.isCompleted) EmeraldSuccess else MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                if (!subjectLabel.isNullOrBlank()) {
                    Text(subjectLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        textDecoration = if (chapter.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (chapter.isCompleted) tokens.inkMuted else tokens.inkPrimary
                )
                Text(
                    text = "${chapter.estimatedMinutes}m est.${if (chapter.notes.isNotBlank()) " · ${chapter.notes}" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit Chapter", tint = tokens.inkSecondary)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete Chapter", tint = RoseAlert)
            }
        }
    }
}

@Composable
private fun StudyEmptyCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    testTag: String,
    onAction: () -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    ObsidianCard(elevated = true) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(testTag)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun SubjectEditorDialog(
    initial: SubjectEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Int) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var colorHex by remember(initial) { mutableStateOf(initial?.colorHex ?: "#9274D8") }
    var dailyMins by remember(initial) { mutableStateOf((initial?.targetMinutesPerDay ?: 60).toString()) }
    var weeklyMins by remember(initial) { mutableStateOf((initial?.weeklyGoalMinutes ?: 300).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceCard,
        title = { Text(if (initial == null) "New Subject" else "Edit Subject") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subject Name (e.g. Physics)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("subject_name_input")
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subjectColorSwatches.forEach { (hex, label) ->
                        val c = parseHexColor(hex, MaterialTheme.colorScheme.primary)
                        val selected = colorHex == hex
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) tokens.inkPrimary else tokens.borderSubtle,
                                    shape = CircleShape
                                )
                                .clickable { colorHex = hex }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dailyMins,
                        onValueChange = { dailyMins = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Daily min") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weeklyMins,
                        onValueChange = { weeklyMins = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Weekly min") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            name,
                            colorHex,
                            dailyMins.toIntOrNull() ?: 60,
                            weeklyMins.toIntOrNull() ?: 300
                        )
                    }
                },
                modifier = Modifier.testTag("save_subject_dialog_btn")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ChapterEditorDialog(
    initial: StudyChapterEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    var title by remember(initial) { mutableStateOf(initial?.title ?: "") }
    var notes by remember(initial) { mutableStateOf(initial?.notes ?: "") }
    var estMins by remember(initial) { mutableStateOf((initial?.estimatedMinutes ?: 45).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceCard,
        title = { Text(if (initial == null) "Add Chapter" else "Edit Chapter") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Chapter Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("chapter_title_input")
                )
                OutlinedTextField(
                    value = estMins,
                    onValueChange = { estMins = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Estimated Minutes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Focus Notes (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title, notes, estMins.toIntOrNull() ?: 45)
                    }
                },
                modifier = Modifier.testTag("save_chapter_dialog_btn")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ResourceEditorDialog(
    initial: StudyResourceEntity?,
    subjects: List<SubjectEntity>,
    defaultSubjectId: Long,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, StudyResourceType) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    var subjectId by remember(initial) { mutableLongStateOf(initial?.subjectId ?: defaultSubjectId) }
    var title by remember(initial) { mutableStateOf(initial?.title ?: "") }
    var content by remember(initial) { mutableStateOf(initial?.content ?: "") }
    var type by remember(initial) { mutableStateOf(initial?.type ?: StudyResourceType.NOTE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceCard,
        title = { Text(if (initial == null) "New Study Note" else "Edit Study Note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    subjects.forEach { subj ->
                        FilterChip(
                            selected = subjectId == subj.id,
                            onClick = { subjectId = subj.id },
                            label = { Text(subj.name, maxLines = 1, softWrap = false) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StudyResourceType.entries.forEach { rt ->
                        FilterChip(
                            selected = type == rt,
                            onClick = { type = rt },
                            label = { Text(rt.label, maxLines = 1, softWrap = false) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("resource_title_input")
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content or Summary") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() || content.isNotBlank()) {
                        onSave(subjectId, title, content, type)
                    }
                },
                modifier = Modifier.testTag("save_resource_dialog_btn")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CustomFocusDialog(
    initialWork: Int,
    initialBreak: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    var workMins by remember { mutableStateOf(initialWork.toString()) }
    var breakMins by remember { mutableStateOf(initialBreak.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceCard,
        title = { Text("Custom Focus Cycle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = workMins,
                    onValueChange = { workMins = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Focus duration (minutes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = breakMins,
                    onValueChange = { breakMins = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Break duration (minutes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    workMins.toIntOrNull() ?: 25,
                    breakMins.toIntOrNull() ?: 5
                )
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun parseHexColor(hex: String, fallback: Color): Color {
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { fallback }
}
