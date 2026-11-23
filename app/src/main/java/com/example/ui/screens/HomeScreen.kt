package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.HabitEntity
import com.example.data.local.TaskEntity
import com.example.data.local.WidgetConfigEntity
import com.example.ui.components.AnimatedStreakPill
import com.example.ui.components.DashboardWidgetCard
import com.example.ui.components.GlanceWidgetPinningSection
import com.example.ui.components.LevelUpBanner
import com.example.ui.components.StatusPill
import com.example.ui.components.WidgetCustomizerList
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.ObsidianBase
import com.example.ui.viewmodel.LifeOsUiState
import com.example.util.DateTimeUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: LifeOsUiState,
    onToggleTask: (TaskEntity) -> Unit,
    onToggleHabit: (HabitEntity) -> Unit,
    onSelectTimerPreset: (Int) -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onFinishTimerEarly: () -> Unit,
    onUpdateNote: (String) -> Unit,
    onInteractPet: (String) -> Unit,
    onToggleWidgetVisibility: (WidgetConfigEntity) -> Unit,
    onMoveWidget: (WidgetConfigEntity, Int) -> Unit,
    onNavigateToSection: (String) -> Unit,
    onQuickAddTaskClick: () -> Unit,
    onDismissLevelUp: () -> Unit = {},
    onShowMessage: (String) -> Unit = {}
) {
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showWidgetsSheet by remember { mutableStateOf(false) }
    var widgetsSheetTab by remember { mutableIntStateOf(0) } // 0 = Home-Screen Widgets (Glance), 1 = Dashboard Cards

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    val visibleWidgets = remember(state.widgets) {
        state.widgets.filter { it.isVisible }.sortedBy { it.sortOrder }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("home_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Compact Hero Command Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_command_1795431263383),
                    contentDescription = "LIFE OS Command Banner",
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    ObsidianBase.copy(alpha = 0.58f),
                                    ObsidianBase.copy(alpha = 0.90f)
                                )
                            )
                        )
                )
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = buildString {
                                    append("LIFE OS")
                                    if (state.prefs.userHandle.isNotBlank()) append(" · ${state.prefs.userHandle}")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${DateTimeUtils.formatGreeting()}, ${state.prefs.userName.ifBlank { "Operator" }}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = InkPrimary,
                                maxLines = 2
                            )
                            Text(
                                text = "${DateTimeUtils.formatFullDate(state.todayEpochDay)} · ${DateTimeUtils.formatLiveTime(currentTimeMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary,
                                maxLines = 1
                            )
                        }
                        Row {
                            IconButton(
                                onClick = {
                                    widgetsSheetTab = 1
                                    showWidgetsSheet = true
                                },
                                modifier = Modifier.testTag("customize_widgets_btn")
                            ) {
                                Icon(
                                    Icons.Filled.DashboardCustomize,
                                    contentDescription = "Customize Dashboard Cards",
                                    tint = InkPrimary
                                )
                            }
                            IconButton(
                                onClick = { onNavigateToSection("settings") },
                                modifier = Modifier.testTag("home_settings_btn")
                            ) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = "Settings",
                                    tint = InkPrimary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            text = "Lv.${state.prefs.level} · ${state.prefs.totalXp} XP",
                            color = MaterialTheme.colorScheme.primary
                        )
                        AnimatedStreakPill(streakDays = state.prefs.dailyStreak)
                        StatusPill(
                            text = "Score ${state.productivityScore}%",
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Quick Command Actions Row (zero truncation on narrow 360dp screens)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onQuickAddTaskClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).height(40.dp).testTag("home_quick_task_btn")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Tasks",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        FilledTonalButton(
                            onClick = { onNavigateToSection("life_ai") },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).height(40.dp).testTag("home_life_ai_btn")
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "LIFE AI",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                widgetsSheetTab = 0
                                showWidgetsSheet = true
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).height(40.dp).testTag("home_widgets_btn")
                        ) {
                            Icon(Icons.Filled.Widgets, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Widgets",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Quick Module Dock (direct 1-tap access to Wellness, Growth, Friends)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickModuleChip(
                    label = "Wellness",
                    sub = DateTimeUtils.formatMinutesCompact(state.totalScreenMinutesToday),
                    icon = Icons.Filled.HealthAndSafety,
                    accent = MaterialTheme.colorScheme.secondary,
                    onClick = { onNavigateToSection("wellness") },
                    modifier = Modifier.weight(1f)
                )
                QuickModuleChip(
                    label = "Pet & XP",
                    sub = state.prefs.petName,
                    icon = Icons.Filled.AutoAwesome,
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = { onNavigateToSection("growth") },
                    modifier = Modifier.weight(1f)
                )
                QuickModuleChip(
                    label = "Friends",
                    sub = if (state.friends.isEmpty()) "0 linked" else "${state.friends.size} linked",
                    icon = Icons.Filled.Groups,
                    accent = AmberWarning,
                    onClick = { onNavigateToSection("friends") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Level-Up Celebration Banner (animated entrance when leveling up)
        item {
            LevelUpBanner(
                level = state.levelUpBanner,
                petName = state.prefs.petName,
                onDismiss = onDismissLevelUp
            )
        }

        // Pet Reaction Toast Banner if active
        item {
            AnimatedVisibility(
                visible = state.petReactionBanner != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = state.petReactionBanner.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Movable & Customizable Widgets
        if (visibleWidgets.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(CharcoalElevated)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No cards active on your dashboard.", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        widgetsSheetTab = 1
                        showWidgetsSheet = true
                    }) {
                        Text("Customize Cards")
                    }
                }
            }
        } else {
            items(visibleWidgets, key = { it.widgetTypeName }) { config ->
                val type = config.type
                if (type != null) {
                    DashboardWidgetCard(
                        type = type,
                        state = state,
                        onToggleTask = onToggleTask,
                        onToggleHabit = onToggleHabit,
                        onSelectTimerPreset = onSelectTimerPreset,
                        onToggleTimer = onToggleTimer,
                        onResetTimer = onResetTimer,
                        onFinishTimerEarly = onFinishTimerEarly,
                        onUpdateNote = onUpdateNote,
                        onInteractPet = onInteractPet,
                        onNavigateToSection = onNavigateToSection
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        widgetsSheetTab = 1
                        showWidgetsSheet = true
                    },
                    border = BorderStroke(1.dp, BorderHairline),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Filled.DashboardCustomize, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Customize Cards (${visibleWidgets.size}/${state.widgets.size})", maxLines = 1)
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }

    if (showWidgetsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWidgetsSheet = false },
            containerColor = ObsidianBase
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (widgetsSheetTab == 0) "Home-Screen Widgets" else "Dashboard Cards",
                    style = MaterialTheme.typography.headlineMedium,
                    color = InkPrimary
                )
                Text(
                    text = if (widgetsSheetTab == 0) {
                        "Pin live Android Jetpack Glance widgets directly to your home screen."
                    } else {
                        "Choose which compact cards appear on your Home tab."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = widgetsSheetTab == 0,
                        onClick = { widgetsSheetTab = 0 },
                        label = { Text("Home Widgets", maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            selectedLabelColor = InkPrimary
                        )
                    )
                    FilterChip(
                        selected = widgetsSheetTab == 1,
                        onClick = { widgetsSheetTab = 1 },
                        label = { Text("Dashboard Cards", maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            selectedLabelColor = InkPrimary
                        )
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (widgetsSheetTab == 0) {
                    GlanceWidgetPinningSection(
                        petName = state.prefs.petName,
                        onNotice = onShowMessage
                    )
                } else {
                    WidgetCustomizerList(
                        widgets = state.widgets,
                        onToggleVisible = onToggleWidgetVisibility,
                        onMoveUp = { onMoveWidget(it, -1) },
                        onMoveDown = { onMoveWidget(it, 1) }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun QuickModuleChip(
    label: String,
    sub: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = CharcoalElevated,
        border = BorderStroke(1.dp, BorderHairline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = InkPrimary,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = InkSecondary,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
