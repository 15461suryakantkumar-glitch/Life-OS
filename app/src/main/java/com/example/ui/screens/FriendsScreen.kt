package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.FriendEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.social.NearbyFriendPeer
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GradientGlowCard
import com.example.ui.components.NeonProgressBar
import com.example.ui.components.ObsidianCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.UserAvatarBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.LifeOsUiState
import com.example.util.DateTimeUtils

private data class LeaderboardRow(
    val name: String,
    val handle: String,
    val level: Int,
    val xp: Int,
    val streak: Int,
    val focusToday: Int,
    val challengesDone: Int,
    val challengesTotal: Int,
    val isCurrentUser: Boolean,
    val isPrivate: Boolean,
    val sourceFriend: FriendEntity? = null
)

@Composable
fun FriendsScreen(
    state: LifeOsUiState,
    myShareToken: String,
    myInviteLink: String,
    onImportInvite: (String, (Boolean, String) -> Unit) -> Unit,
    onAddFriendByUsername: (String, String, String) -> Unit,
    onToggleNearbyRadar: () -> Unit,
    onSyncNearbyPeer: (NearbyFriendPeer) -> Unit,
    onRemoveFriend: (FriendEntity) -> Unit,
    onUpdatePrivacy: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedFriend by remember { mutableStateOf<FriendEntity?>(null) }
    var showPrivacyPanel by remember { mutableStateOf(false) }

    val prefs = state.prefs
    val myCode = remember(prefs.friendCode, prefs.userHandle, prefs.userName) {
        prefs.friendCode.ifBlank {
            UserPreferencesRepository.generateFriendCode(prefs.userHandle, prefs.userName)
        }
    }

    val leaderboard = remember(state.friends, prefs, state.completedChallengesCount, state.challenges.size) {
        if (state.friends.isEmpty()) {
            emptyList()
        } else {
            val self = LeaderboardRow(
                name = "${prefs.userName.ifBlank { "You" }} (You)",
                handle = prefs.userHandle.ifBlank { "@you" },
                level = prefs.level,
                xp = prefs.totalXp,
                streak = prefs.dailyStreak,
                focusToday = prefs.focusMinutesLoggedToday,
                challengesDone = state.completedChallengesCount,
                challengesTotal = state.challenges.size.coerceAtLeast(1),
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
                    challengesDone = it.challengesCompleted,
                    challengesTotal = it.challengesTotal.coerceAtLeast(1),
                    isCurrentUser = false,
                    isPrivate = !it.sharesStats,
                    sourceFriend = it
                )
            }
            (others + self).sortedByDescending { if (it.sourceFriend?.shareXp == false) -1 else it.xp }
        }
    }

    fun launchSystemShare() {
        val summary = buildString {
            appendLine("Connect with ${prefs.userName.ifBlank { "me" }} on Life OS!")
            appendLine("Friend ID: $myCode")
            if (prefs.shareXpWithFriends) appendLine("Level ${prefs.level} · ${prefs.totalXp} XP")
            if (prefs.shareStreaksWithFriends) appendLine("Daily Streak: ${prefs.dailyStreak}d")
            appendLine()
            appendLine("Open Life OS -> Friends -> Add Friend and paste this link or Friend ID:")
            append(myInviteLink)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Life OS Friend Invite · $myCode")
            putExtra(Intent.EXTRA_TEXT, summary)
        }
        context.startActivity(Intent.createChooser(intent, "Share Life OS Progress"))
    }

    Scaffold(
        containerColor = tokens.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_friend_fab")
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = "Add Friend")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("friends_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Friends & Sync", style = MaterialTheme.typography.headlineLarge, color = tokens.inkPrimary)
                        Text(
                            "Connect via Friend ID, invite link, or local Wi-Fi at ₹0 cost",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.inkSecondary
                        )
                    }
                    StatusPill(
                        text = if (state.isOnline) "Online" else "Offline",
                        color = if (state.isOnline) EmeraldSuccess else AmberWarning,
                        icon = if (state.isOnline) Icons.Filled.CloudDone else Icons.Filled.CloudOff
                    )
                }
            }

            // Shareable Profile & Live Sync Card
            item {
                GradientGlowCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatarBadge(preset = prefs.avatarPreset, size = 44.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = prefs.userName.ifBlank { "Member" },
                                style = MaterialTheme.typography.titleLarge,
                                color = tokens.inkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${prefs.userHandle.ifBlank { "@member" }} · ID: $myCode",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(myShareToken))
                                onShowMessage("Copied live sync token to clipboard")
                            }
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy sync token", tint = tokens.inkPrimary)
                        }
                        IconButton(onClick = { launchSystemShare() }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share invite link", tint = MaterialTheme.colorScheme.secondary)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                clipboard.setText(AnnotatedString(myCode))
                                onShowMessage("Copied Friend ID: $myCode")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy ID", maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(
                            onClick = onToggleNearbyRadar,
                            modifier = Modifier.weight(1f).testTag("wifi_radar_btn")
                        ) {
                            Icon(
                                Icons.Filled.WifiTethering,
                                contentDescription = null,
                                tint = if (state.isPeerRadarActive) EmeraldSuccess else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.isPeerRadarActive) "Scanning" else "Wi-Fi Sync", maxLines = 1, softWrap = false)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Privacy Header Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(tokens.smallRadius))
                            .clickable { showPrivacyPanel = !showPrivacyPanel }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = tokens.inkSecondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Privacy & Sharing Controls", style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary)
                        }
                        Icon(
                            imageVector = if (showPrivacyPanel) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = "Toggle privacy controls",
                            tint = tokens.inkSecondary
                        )
                    }

                    AnimatedVisibility(visible = showPrivacyPanel) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PrivacyToggleRow(
                                label = "Share Total XP & Level",
                                checked = prefs.shareXpWithFriends,
                                onCheckedChange = {
                                    onUpdatePrivacy(it, prefs.shareStreaksWithFriends, prefs.shareFocusWithFriends, prefs.shareChallengesWithFriends)
                                }
                            )
                            PrivacyToggleRow(
                                label = "Share Daily Streaks",
                                checked = prefs.shareStreaksWithFriends,
                                onCheckedChange = {
                                    onUpdatePrivacy(prefs.shareXpWithFriends, it, prefs.shareFocusWithFriends, prefs.shareChallengesWithFriends)
                                }
                            )
                            PrivacyToggleRow(
                                label = "Share Focus Minutes",
                                checked = prefs.shareFocusWithFriends,
                                onCheckedChange = {
                                    onUpdatePrivacy(prefs.shareXpWithFriends, prefs.shareStreaksWithFriends, it, prefs.shareChallengesWithFriends)
                                }
                            )
                            PrivacyToggleRow(
                                label = "Share Challenge Progress",
                                checked = prefs.shareChallengesWithFriends,
                                onCheckedChange = {
                                    onUpdatePrivacy(prefs.shareXpWithFriends, prefs.shareStreaksWithFriends, prefs.shareFocusWithFriends, it)
                                }
                            )
                        }
                    }
                }
            }

            // Nearby Wi-Fi Peers when Radar is active
            if (state.isPeerRadarActive) {
                item {
                    ObsidianCard(borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.WifiTethering, contentDescription = null, tint = EmeraldSuccess)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Nearby Wi-Fi Peers", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                Text(
                                    text = if (state.nearbyPeers.isEmpty()) "Listening on local Wi-Fi for nearby Life OS users..." else "${state.nearbyPeers.size} peer(s) discovered",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.inkSecondary
                                )
                            }
                        }
                        if (state.nearbyPeers.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            state.nearbyPeers.forEach { peer ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(tokens.smallRadius))
                                        .background(tokens.surfaceElevated)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(peer.serviceName, style = MaterialTheme.typography.bodyMedium, color = tokens.inkPrimary)
                                    OutlinedButton(onClick = { onSyncNearbyPeer(peer) }) {
                                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Sync", maxLines = 1, softWrap = false)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }

            // Empty State vs Leaderboard
            if (state.friends.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.Groups,
                        title = "No friends connected yet",
                        subtitle = "Connect with a Friend ID, invite link, username, or local Wi-Fi sync to compare XP and streaks.",
                        actionLabel = "Add First Friend",
                        onAction = { showAddDialog = true },
                        modifier = Modifier.testTag("friends_empty_card")
                    )
                }
            } else {
                // Weekly Comparison Card (only with real connected friends)
                item {
                    val topFriend = state.friends.filter { it.shareXp }.maxByOrNull { it.xp }
                    if (topFriend != null) {
                        val goalXp = maxOf(topFriend.xp, prefs.totalXp, 100)
                        val userRatio = (prefs.totalXp.toFloat() / goalXp).coerceIn(0f, 1f)
                        ObsidianCard(elevated = true) {
                            SectionHeader(
                                title = "XP Comparison",
                                subtitle = "You (${prefs.totalXp} XP) vs ${topFriend.displayName} (${topFriend.xp} XP)"
                            )
                            Spacer(Modifier.height(8.dp))
                            NeonProgressBar(progress = userRatio)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (prefs.totalXp >= topFriend.xp) {
                                    "You lead your circle by ${prefs.totalXp - topFriend.xp} XP."
                                } else {
                                    "${topFriend.xp - prefs.totalXp} XP behind ${topFriend.displayName}."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkSecondary
                            )
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Circle Leaderboard",
                        subtitle = "Tap any friend to compare XP, streaks, and challenges"
                    )
                }

                itemsIndexed(leaderboard, key = { idx, row -> "${row.handle}_$idx" }) { index, row ->
                    val friend = row.sourceFriend
                    ObsidianCard(
                        onClick = { if (friend != null) selectedFriend = friend },
                        borderColor = if (row.isCurrentUser) MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f) else tokens.borderSubtle
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (index == 0) AmberWarning.copy(alpha = 0.22f) else tokens.surfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "#${index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (index == 0) AmberWarning else tokens.inkPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(row.name, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                val subtitle = when {
                                    row.isPrivate -> "Private profile"
                                    friend != null && !friend.shareChallenges -> "Lv.${row.level} · ${friend.statusText}"
                                    else -> "Lv.${row.level} · ${row.challengesDone}/${row.challengesTotal} challenges"
                                }
                                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (row.isPrivate || friend?.shareXp == false) "Private" else "${row.xp} XP",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                if (!row.isPrivate && (friend == null || friend.shareStreaks)) {
                                    StatusPill(
                                        text = "${row.streak}d",
                                        color = AmberWarning,
                                        icon = Icons.Outlined.LocalFireDepartment
                                    )
                                }
                            }
                            if (friend != null) {
                                IconButton(onClick = { onRemoveFriend(friend) }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove friend", tint = tokens.inkMuted)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showAddDialog) {
        AddFriendDialog(
            onDismiss = { showAddDialog = false },
            onImportInvite = { raw, onDone ->
                onImportInvite(raw) { ok, msg ->
                    if (ok) showAddDialog = false
                    onDone(ok, msg)
                }
            },
            onAddByUsername = { name, handle, code ->
                onAddFriendByUsername(name, handle, code)
                showAddDialog = false
            }
        )
    }

    selectedFriend?.let { friend ->
        FriendComparisonDialog(
            state = state,
            friend = friend,
            onDismiss = { selectedFriend = null }
        )
    }
}

@Composable
private fun PrivacyToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = tokens.inkPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AddFriendDialog(
    onDismiss: () -> Unit,
    onImportInvite: (String, (Boolean, String) -> Unit) -> Unit,
    onAddByUsername: (String, String, String) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    var mode by remember { mutableStateOf("CODE") } // CODE | USERNAME
    var inviteInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var name by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceBase,
        title = { Text("Add Friend", style = MaterialTheme.typography.titleLarge, color = tokens.inkPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == "CODE",
                        onClick = { mode = "CODE"; errorMsg = null },
                        label = { Text("Friend ID / Link", maxLines = 1, softWrap = false) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        )
                    )
                    FilterChip(
                        selected = mode == "USERNAME",
                        onClick = { mode = "USERNAME"; errorMsg = null },
                        label = { Text("Username", maxLines = 1, softWrap = false) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        )
                    )
                }

                if (mode == "CODE") {
                    OutlinedTextField(
                        value = inviteInput,
                        onValueChange = { inviteInput = it; errorMsg = null },
                        label = { Text("Sync token, invite link, or Friend ID") },
                        placeholder = { Text("lifeos://friend?data=LIFEOS1:... or LIFE-ARIA-4821") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("friend_invite_input")
                    )
                    if (errorMsg != null) {
                        Text(errorMsg!!, style = MaterialTheme.typography.bodySmall, color = RoseAlert)
                    }
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Display name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("friend_name_input")
                    )
                    OutlinedTextField(
                        value = handle,
                        onValueChange = { handle = it },
                        label = { Text("Username (e.g. @aria)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("friend_handle_input")
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Friend ID (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (mode == "CODE") {
                Button(
                    onClick = {
                        onImportInvite(inviteInput) { ok, msg ->
                            if (!ok) errorMsg = msg
                        }
                    },
                    enabled = inviteInput.isNotBlank(),
                    modifier = Modifier.testTag("confirm_add_friend_btn")
                ) { Text("Connect", maxLines = 1, softWrap = false) }
            } else {
                Button(
                    onClick = { onAddByUsername(name, handle, code) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.testTag("confirm_add_friend_btn")
                ) { Text("Add", maxLines = 1, softWrap = false) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FriendComparisonDialog(
    state: LifeOsUiState,
    friend: FriendEntity,
    onDismiss: () -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val prefs = state.prefs
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceBase,
        title = {
            Column {
                Text(friend.displayName, style = MaterialTheme.typography.titleLarge, color = tokens.inkPrimary)
                Text("${friend.handle} · ${friend.petStageName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ComparisonRow(
                    metric = "XP & Level",
                    yours = if (prefs.shareXpWithFriends) "Lv.${prefs.level} (${prefs.totalXp})" else "Hidden",
                    theirs = if (friend.shareXp) "Lv.${friend.level} (${friend.xp})" else "Private"
                )
                ComparisonRow(
                    metric = "Daily Streak",
                    yours = if (prefs.shareStreaksWithFriends) "${prefs.dailyStreak}d" else "Hidden",
                    theirs = if (friend.shareStreaks) "${friend.streakDays}d" else "Private"
                )
                ComparisonRow(
                    metric = "Focus Today",
                    yours = if (prefs.shareFocusWithFriends) DateTimeUtils.formatMinutesCompact(prefs.focusMinutesLoggedToday) else "Hidden",
                    theirs = if (friend.shareFocus) DateTimeUtils.formatMinutesCompact(friend.focusMinutesToday) else "Private"
                )
                ComparisonRow(
                    metric = "Challenges",
                    yours = if (prefs.shareChallengesWithFriends) "${state.completedChallengesCount}/${state.challenges.size}" else "Hidden",
                    theirs = if (friend.shareChallenges) "${friend.challengesCompleted}/${friend.challengesTotal}" else "Private"
                )
                if (friend.lastSyncedAtMillis > 0L) {
                    Text(
                        text = "Last synced ${DateTimeUtils.formatShortDate(friend.lastSyncedAtMillis)} · ${friend.statusText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.inkMuted
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun ComparisonRow(metric: String, yours: String, theirs: String) {
    val tokens = LifeOsThemeTokensProvider.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.smallRadius))
            .background(tokens.surfaceElevated)
            .border(1.dp, tokens.borderSubtle, RoundedCornerShape(tokens.smallRadius))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(metric, style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary, modifier = Modifier.weight(1f))
        Text(yours, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(" vs ", style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
        Text(theirs, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
    }
}
