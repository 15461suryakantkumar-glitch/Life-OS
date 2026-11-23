package com.example.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.FriendEntity
import com.example.ui.components.GradientGlowCard
import com.example.ui.components.NeonProgressBar
import com.example.ui.components.ObsidianCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.ObsidianBase
import com.example.ui.theme.ObsidianVoid
import com.example.ui.viewmodel.LifeOsUiState

private data class LeaderboardRow(
    val name: String,
    val handle: String,
    val level: Int,
    val xp: Int,
    val streak: Int,
    val focusToday: Int,
    val isCurrentUser: Boolean,
    val isPrivate: Boolean,
    val sourceFriend: FriendEntity? = null
)

@Composable
fun FriendsScreen(
    state: LifeOsUiState,
    onAddFriend: (String, String, Int, Int) -> Unit,
    onRemoveFriend: (FriendEntity) -> Unit,
    onOpenPrivacySettings: () -> Unit
) {
    var showInviteDialog by remember { mutableStateOf(false) }
    var selectedFriend by remember { mutableStateOf<FriendEntity?>(null) }
    var showSharePreview by remember { mutableStateOf(false) }

    val prefs = state.prefs
    val leaderboard = remember(state.friends, prefs) {
        val self = LeaderboardRow(
            name = "${prefs.userName} (You)",
            handle = prefs.userHandle,
            level = prefs.level,
            xp = prefs.totalXp,
            streak = prefs.dailyStreak,
            focusToday = prefs.focusMinutesLoggedToday,
            isCurrentUser = true,
            isPrivate = false
        )
        val others = state.friends.map {
            LeaderboardRow(
                name = it.displayName,
                handle = it.handle,
                level = it.level,
                xp = it.xp,
                streak = it.streakDays,
                focusToday = it.focusMinutesToday,
                isCurrentUser = false,
                isPrivate = !it.sharesStats,
                sourceFriend = it
            )
        }
        (others + self).sortedByDescending { it.xp }
    }

    Scaffold(
        containerColor = ObsidianVoid,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showInviteDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_friend_fab")
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = "Invite Friend")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("friends_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Friends & Arena", style = MaterialTheme.typography.headlineLarge, color = InkPrimary)
                Text("Weekly competitions, XP comparisons & privacy-first sharing", style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            }

            // Local Demo + Firebase Ready Banner
            item {
                ObsidianCard(borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Local Social Demonstration", style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                            Text(state.cloudSyncStatusLabel, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
                        }
                    }
                }
            }

            // Shareable Profile & Privacy Card
            item {
                GradientGlowCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(prefs.userName, style = MaterialTheme.typography.titleLarge, color = InkPrimary)
                            Text("${prefs.userHandle} · Invite Code: LIFE-${prefs.level}X9", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        IconButton(onClick = { showSharePreview = true }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share achievements", tint = InkPrimary)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(
                            text = if (prefs.shareXpWithFriends) "XP Public" else "XP Private",
                            color = if (prefs.shareXpWithFriends) EmeraldSuccess else InkMuted
                        )
                        StatusPill(
                            text = if (prefs.shareStreaksWithFriends) "Streaks Public" else "Streaks Private",
                            color = if (prefs.shareStreaksWithFriends) AmberWarning else InkMuted
                        )
                        StatusPill(
                            text = if (prefs.shareFocusWithFriends) "Focus Public" else "Focus Private",
                            color = if (prefs.shareFocusWithFriends) MaterialTheme.colorScheme.secondary else InkMuted
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onOpenPrivacySettings) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Configure Privacy Controls")
                    }
                }
            }

            // Weekly Shared Competition Card
            item {
                val topRival = state.friends.maxByOrNull { it.xp }
                val rivalXp = topRival?.xp ?: 1200
                val userRatio = (prefs.totalXp.toFloat() / rivalXp.coerceAtLeast(1)).coerceIn(0f, 1f)
                ObsidianCard(elevated = true) {
                    SectionHeader(
                        title = "Weekly XP Sprint",
                        subtitle = "You (${prefs.totalXp} XP) vs ${topRival?.displayName ?: "Top Rival"} ($rivalXp XP)"
                    )
                    Spacer(Modifier.height(10.dp))
                    NeonProgressBar(progress = userRatio)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (prefs.totalXp >= rivalXp) "You lead the arena! Keep your streak alive." else "${rivalXp - prefs.totalXp} XP behind #1 — complete 2 high-priority tasks to close the gap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                }
            }

            // Leaderboard
            item {
                SectionHeader(title = "Leaderboard", subtitle = "Ranked by total XP")
            }

            itemsIndexed(leaderboard, key = { idx, row -> "${row.handle}_$idx" }) { index, row ->
                ObsidianCard(
                    onClick = { if (row.sourceFriend != null) selectedFriend = row.sourceFriend },
                    borderColor = if (row.isCurrentUser) MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (index == 0) AmberWarning.copy(alpha = 0.22f) else CharcoalElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (index == 0) AmberWarning else InkPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.name, style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                            Text(
                                text = if (row.isPrivate) "Stats hidden by user privacy" else "Lv.${row.level} · ${row.focusToday}m focus today",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (row.isPrivate) "Private" else "${row.xp} XP",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (!row.isPrivate) {
                                StatusPill(
                                    text = "${row.streak}d",
                                    color = AmberWarning,
                                    icon = Icons.Outlined.LocalFireDepartment
                                )
                            }
                        }
                        if (row.sourceFriend != null) {
                            IconButton(onClick = { onRemoveFriend(row.sourceFriend) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove friend", tint = InkMuted)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showInviteDialog) {
        var name by remember { mutableStateOf("") }
        var handle by remember { mutableStateOf("") }
        var xpText by remember { mutableStateOf("850") }
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            containerColor = ObsidianBase,
            title = { Text("Add Local Friend / Rival") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Display name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = handle,
                        onValueChange = { handle = it },
                        label = { Text("Handle (e.g. @kai)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = xpText,
                        onValueChange = { xpText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Starting XP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddFriend(name, handle, xpText.toIntOrNull() ?: 850, 4)
                        showInviteDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }) { Text("Cancel") }
            }
        )
    }

    selectedFriend?.let { friend ->
        AlertDialog(
            onDismissRequest = { selectedFriend = null },
            containerColor = ObsidianBase,
            title = { Text(friend.displayName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(friend.handle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    Text("Status: ${friend.statusText}", style = MaterialTheme.typography.bodyMedium, color = InkPrimary)
                    if (friend.sharesStats) {
                        Text("Level ${friend.level} · ${friend.xp} XP", style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
                        Text("Active Streak: ${friend.streakDays} days", style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
                        Text("Focus Today: ${friend.focusMinutesToday} min", style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
                    } else {
                        Text("This friend keeps detailed telemetry private.", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedFriend = null }) { Text("Close") }
            }
        )
    }

    if (showSharePreview) {
        AlertDialog(
            onDismissRequest = { showSharePreview = false },
            containerColor = ObsidianBase,
            title = { Text("Shareable Achievement Card") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${prefs.userName} (${prefs.userHandle})", style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                    Text("Level ${prefs.level} · Companion: ${prefs.petName} (${prefs.petStage.title})", style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
                    if (prefs.shareXpWithFriends) Text("Total XP: ${prefs.totalXp}", color = MaterialTheme.colorScheme.secondary)
                    if (prefs.shareStreaksWithFriends) Text("Streak: ${prefs.dailyStreak} days", color = AmberWarning)
                    if (prefs.shareFocusWithFriends) Text("Focus Today: ${prefs.focusMinutesLoggedToday}m", color = EmeraldSuccess)
                    Text("Unlocked Badges: ${prefs.unlockedBadges.size}", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                }
            },
            confirmButton = {
                TextButton(onClick = { showSharePreview = false }) { Text("Done") }
            }
        )
    }
}
