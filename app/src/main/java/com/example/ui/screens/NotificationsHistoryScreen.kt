package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.NotificationHistoryEntity
import com.example.notifications.FeeloraNotificationCategory
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FeeloraScreenBackdrop
import com.example.ui.components.FeeloraSegmentedRow
import com.example.ui.components.FeeloraSubpageHeader
import com.example.ui.components.ObsidianCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.util.DateTimeUtils

@Composable
fun NotificationsHistoryScreen(
    history: List<NotificationHistoryEntity>,
    onBack: () -> Unit,
    onOpenRoute: (String) -> Unit,
    onMarkRead: (Long) -> Unit,
    onMarkAllRead: () -> Unit,
    onClearAll: () -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    var selectedFilter by remember { mutableStateOf<FeeloraNotificationCategory?>(null) }

    BackHandler(onBack = onBack)

    val filtered = remember(history, selectedFilter) {
        if (selectedFilter == null) history else history.filter { it.category == selectedFilter?.name }
    }

    FeeloraScreenBackdrop(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("notifications_history_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                FeeloraSubpageHeader(
                    title = "Notifications",
                    subtitle = "${history.count { !it.isRead }} unread · 5 categories",
                    onBack = onBack,
                    trailing = {
                        Row {
                            IconButton(onClick = onMarkAllRead, enabled = history.any { !it.isRead }) {
                                Icon(Icons.Filled.DoneAll, contentDescription = "Mark all read", tint = MaterialTheme.colorScheme.secondary)
                            }
                            IconButton(onClick = onClearAll, enabled = history.isNotEmpty()) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear notification history", tint = tokens.inkSecondary)
                            }
                        }
                    }
                )
            }

            item {
                val filterOptions: List<FeeloraNotificationCategory?> = listOf(null) + FeeloraNotificationCategory.entries
                FeeloraSegmentedRow(
                    items = filterOptions,
                    selectedItem = selectedFilter,
                    labelFor = { cat ->
                        when (cat) {
                            null -> "All"
                            FeeloraNotificationCategory.TASK -> "Tasks"
                            FeeloraNotificationCategory.PENDING_TASK -> "Pending"
                            FeeloraNotificationCategory.COMPANION -> "KUTTA"
                            FeeloraNotificationCategory.ACHIEVEMENT -> "Rewards"
                            FeeloraNotificationCategory.TOGETHER -> "Together"
                        }
                    },
                    onSelect = { selectedFilter = it },
                    tagPrefix = "notif_filter"
                )
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.NotificationsNone,
                        title = "You're all caught up",
                        subtitle = "Task reminders, KUTTA nudges, achievements, and Together alerts will appear here."
                    )
                }
            } else {
                items(filtered, key = { it.id }) { item ->
                    val category = FeeloraNotificationCategory.entries.firstOrNull { it.name == item.category }
                        ?: FeeloraNotificationCategory.TASK
                    val (icon, accent) = notificationStyleFor(category)
                    ObsidianCard(
                        onClick = {
                            if (!item.isRead) onMarkRead(item.id)
                            onOpenRoute(item.targetRoute.ifBlank { category.defaultRoute })
                        },
                        borderColor = if (!item.isRead) accent.copy(alpha = 0.65f) else tokens.borderSubtle,
                        elevated = !item.isRead
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(icon, contentDescription = null, tint = accent)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary, modifier = Modifier.weight(1f))
                                    if (!item.isRead) {
                                        StatusPill(text = "New", color = accent)
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(item.body, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "${category.channelTitle.removePrefix("Feelora · ")} · ${DateTimeUtils.formatLiveTime(item.createdAtMillis)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.inkMuted
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun notificationStyleFor(category: FeeloraNotificationCategory): Pair<ImageVector, Color> = when (category) {
    FeeloraNotificationCategory.TASK -> Icons.Filled.TaskAlt to MaterialTheme.colorScheme.primary
    FeeloraNotificationCategory.PENDING_TASK -> Icons.Filled.NotificationsActive to AmberWarning
    FeeloraNotificationCategory.COMPANION -> Icons.Filled.Favorite to MaterialTheme.colorScheme.secondary
    FeeloraNotificationCategory.ACHIEVEMENT -> Icons.Filled.EmojiEvents to EmeraldSuccess
    FeeloraNotificationCategory.TOGETHER -> Icons.Filled.Groups to MaterialTheme.colorScheme.primary
}
