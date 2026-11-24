package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.local.DashboardWidgetType
import com.example.data.local.HabitEntity
import com.example.data.local.TaskEntity
import com.example.data.local.WidgetConfigEntity
import com.example.kutta.KuttaDashboardCard
import com.example.suru.SuruMascot
import com.example.ui.components.AnimatedStreakPill
import com.example.ui.components.DashboardWidgetCard
import com.example.ui.components.FeeloraScreenBackdrop
import com.example.ui.components.FeeloraTopHeader
import com.example.ui.components.GlanceWidgetPinningSection
import com.example.ui.components.LevelUpBanner
import com.example.ui.components.StatusPill
import com.example.ui.components.WidgetCustomizerList
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.LifeOsThemeTokensProvider
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
    onSelectTimerTask: (Long?) -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onFinishTimerEarly: () -> Unit,
    onUpdateNote: (String) -> Unit,
    onInteractPet: (String) -> Unit,
    onTapKutta: () -> Unit = { onInteractPet("TAP") },
    onLogWaterQuick: (Int) -> Unit = {},
    onToggleWidgetVisibility: (WidgetConfigEntity) -> Unit,
    onMoveWidget: (WidgetConfigEntity, Int) -> Unit,
    onNavigateToSection: (String) -> Unit,
    onQuickAddTaskClick: () -> Unit,
    onOpenSuruWithVoice: () -> Unit = { onNavigateToSection("life_ai") },
    onDismissLevelUp: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onOpenNotifications: () -> Unit = { onNavigateToSection("notifications") },
    onShowMessage: (String) -> Unit = {}
) {
    val tokens = LifeOsThemeTokensProvider.current
    val context = LocalContext.current
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showWidgetsSheet by remember { mutableStateOf(false) }
    var widgetsSheetTab by remember { mutableIntStateOf(0) } // 0 = Home Widgets, 1 = Dashboard Cards

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onOpenSuruWithVoice()
        } else {
            onNavigateToSection("life_ai")
            onShowMessage("Enable microphone permission to speak directly with SURU.")
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    val visibleWidgets = remember(state.widgets) {
        state.widgets
            .filter { it.isVisible && it.widgetTypeName != DashboardWidgetType.DIGITAL_PET.name }
            .sortedBy { it.sortOrder }
    }

    val togetherCount = state.togetherState.friends.size + state.friends.size

    FeeloraScreenBackdrop(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_screen"),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Unified FEELORA 2.0 Top Header (Hamburger Drawer + Notification Center + Profile)
            item {
                FeeloraTopHeader(
                    title = "FEELORA",
                    subtitle = "${DateTimeUtils.formatFullDate(state.todayEpochDay)} · ${DateTimeUtils.formatLiveTime(currentTimeMillis)}",
                    userName = state.prefs.userName,
                    avatarPreset = state.prefs.avatarPreset,
                    avatarUri = state.prefs.avatarPhotoUri,
                    unreadNotifications = state.unreadNotificationsCount,
                    onOpenDrawer = onOpenDrawer,
                    onOpenNotifications = onOpenNotifications,
                    onOpenProfile = { onNavigateToSection("profile") },
                    trailingAction = {
                        IconButton(
                            onClick = {
                                widgetsSheetTab = 1
                                showWidgetsSheet = true
                            },
                            modifier = Modifier.size(48.dp).testTag("customize_widgets_btn")
                        ) {
                            Icon(
                                Icons.Filled.DashboardCustomize,
                                contentDescription = "Customize Cards",
                                tint = tokens.inkPrimary
                            )
                        }
                    }
                )
            }

            // Atmospheric FEELORA Hero Command Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(tokens.heroRadius))
                        .border(tokens.cardBorderWidth, tokens.borderAccent.copy(alpha = 0.45f), RoundedCornerShape(tokens.heroRadius))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_feelora_hero_1795511874544),
                        contentDescription = "FEELORA studio atmosphere",
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        tokens.surfaceBase.copy(alpha = if (tokens.isDark) 0.62f else 0.78f),
                                        tokens.surfaceBase.copy(alpha = if (tokens.isDark) 0.90f else 0.94f)
                                    )
                                )
                            )
                    )

                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${DateTimeUtils.formatGreeting()}, ${state.prefs.userName.ifBlank { "Explorer" }}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = tokens.inkPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.prefs.userHandle.isNotBlank()) {
                            Text(
                                text = state.prefs.userHandle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(
                                text = "Lv.${state.prefs.level} · ${state.prefs.totalXp} XP",
                                color = MaterialTheme.colorScheme.primary
                            )
                            AnimatedStreakPill(streakDays = state.prefs.dailyStreak)
                            StatusPill(
                                text = "${state.productivityScore}% Flow",
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onQuickAddTaskClick,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(42.dp).testTag("home_quick_task_btn")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Task", style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                            }
                            FilledTonalButton(
                                onClick = { onNavigateToSection("life_ai") },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(42.dp).testTag("home_life_ai_btn")
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("SURU", style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                            }
                            OutlinedButton(
                                onClick = {
                                    widgetsSheetTab = 0
                                    showWidgetsSheet = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(42.dp).testTag("home_widgets_btn")
                            ) {
                                Icon(Icons.Filled.Widgets, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Widgets", style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }

            // KUTTA Sanctuary Card
            item {
                KuttaDashboardCard(
                    visualState = state.kuttaState,
                    level = state.prefs.level,
                    xpIntoCurrentLevel = state.prefs.xpIntoCurrentLevel,
                    xpPerLevel = state.prefs.xpPerLevel,
                    onTapKutta = onTapKutta,
                    onOpenSanctuary = { onNavigateToSection("growth") }
                )
            }

            // SURU Quick Voice & Planning Strip
            item {
                Surface(
                    onClick = { onNavigateToSection("life_ai") },
                    shape = RoundedCornerShape(tokens.cardRadius),
                    color = tokens.surfaceCard,
                    shadowElevation = tokens.cardElevation,
                    border = BorderStroke(tokens.cardBorderWidth, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_suru_quick_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SuruMascot(state = state.suruMascotState, size = 48.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("SURU", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary, fontWeight = FontWeight.Bold)
                                StatusPill(text = state.cloudAiStatus.badgeLabel, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = state.suruMorningBriefing.headline.ifBlank {
                                    "Tap to plan study, backlog, or talk in English / Hinglish"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) onOpenSuruWithVoice()
                                else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = tokens.background
                            ),
                            modifier = Modifier.size(48.dp).testTag("home_suru_mic_btn")
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = "Speak to SURU")
                        }
                    }
                }
            }

            // Water & Private Cycle Care Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = { onLogWaterQuick(250) },
                        modifier = Modifier.weight(1f).testTag("home_quick_water_btn"),
                        shape = RoundedCornerShape(tokens.cardRadius * 0.85f),
                        color = tokens.surfaceCard,
                        shadowElevation = tokens.cardElevation,
                        border = BorderStroke(tokens.cardBorderWidth, tokens.borderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.WaterDrop, contentDescription = "Log water", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text("Hydration", style = MaterialTheme.typography.labelMedium, color = tokens.inkPrimary, maxLines = 1, softWrap = false)
                                    Text(
                                        text = "${state.prefs.waterIntakeMlToday}/${state.prefs.dailyWaterGoalMl} ml",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.inkSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text("+250ml", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (state.prefs.cycleCareEnabled) {
                        val cycleSub = if (state.cycleSummary.hasLogs) {
                            if (state.cycleSummary.isCurrentlyOnPeriod) "Gentle Mode" else "Day ${state.cycleSummary.currentCycleDay}"
                        } else "Private Log"
                        QuickModuleChip(
                            label = "Cycle Care",
                            sub = cycleSub,
                            icon = Icons.Filled.Favorite,
                            accent = Color(0xFFF472B6),
                            onClick = { onNavigateToSection("wellness") },
                            modifier = Modifier.weight(0.85f)
                        )
                    }
                }
            }

            // Quick Module Dock (Wellness, KUTTA Corner, Together)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickModuleChip(
                        label = "Wellness",
                        sub = if (state.hasUsageStatsPermission) DateTimeUtils.formatMinutesCompact(state.totalScreenMinutesToday) else "Setup",
                        icon = Icons.Filled.HealthAndSafety,
                        accent = MaterialTheme.colorScheme.secondary,
                        onClick = { onNavigateToSection("wellness") },
                        modifier = Modifier.weight(1f)
                    )
                    QuickModuleChip(
                        label = "KUTTA",
                        sub = state.kuttaState.emotion.label,
                        icon = Icons.Filled.Pets,
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigateToSection("growth") },
                        modifier = Modifier.weight(1f)
                    )
                    QuickModuleChip(
                        label = "Together",
                        sub = if (state.unreadTogetherMessagesCount > 0) "${state.unreadTogetherMessagesCount} new" else "$togetherCount circle",
                        icon = Icons.Filled.Groups,
                        accent = AmberWarning,
                        onClick = { onNavigateToSection("together") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Level Up & Pet Reaction Banners
            item {
                LevelUpBanner(
                    level = state.levelUpBanner,
                    petName = state.prefs.petName,
                    onDismiss = onDismissLevelUp
                )
            }

            item {
                AnimatedVisibility(
                    visible = state.petReactionBanner != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        shape = RoundedCornerShape(tokens.smallRadius),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(state.petReactionBanner.orEmpty(), style = MaterialTheme.typography.bodySmall, color = tokens.inkPrimary)
                        }
                    }
                }
            }

            // Customizable Dashboard Cards
            if (visibleWidgets.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(tokens.cardRadius))
                            .background(tokens.surfaceElevated)
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No cards active on Home.", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = {
                            widgetsSheetTab = 1
                            showWidgetsSheet = true
                        }) {
                            Text("Customize Cards", maxLines = 1, softWrap = false)
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
                            onSelectTimerTask = onSelectTimerTask,
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
                        border = BorderStroke(1.dp, tokens.borderSubtle),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Icon(Icons.Filled.DashboardCustomize, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Customize Cards (${visibleWidgets.size}/${state.widgets.size})", maxLines = 1, softWrap = false)
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showWidgetsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWidgetsSheet = false },
            containerColor = tokens.surfaceBase
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
                    color = tokens.inkPrimary
                )
                Text(
                    text = if (widgetsSheetTab == 0) {
                        "Pin live Android Glance widgets directly to your home screen."
                    } else {
                        "Choose which cards appear on your Home tab."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.inkSecondary
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = widgetsSheetTab == 0,
                        onClick = { widgetsSheetTab = 0 },
                        label = { Text("Home Widgets", maxLines = 1, softWrap = false) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            selectedLabelColor = tokens.inkPrimary
                        )
                    )
                    FilterChip(
                        selected = widgetsSheetTab == 1,
                        onClick = { widgetsSheetTab = 1 },
                        label = { Text("Dashboard Cards", maxLines = 1, softWrap = false) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            selectedLabelColor = tokens.inkPrimary
                        )
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (widgetsSheetTab == 0) {
                    GlanceWidgetPinningSection(petName = state.prefs.petName, onNotice = onShowMessage)
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
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LifeOsThemeTokensProvider.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(tokens.cardRadius * 0.85f),
        color = tokens.surfaceCard,
        shadowElevation = tokens.cardElevation,
        border = BorderStroke(tokens.cardBorderWidth, tokens.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.inkPrimary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.inkSecondary,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
