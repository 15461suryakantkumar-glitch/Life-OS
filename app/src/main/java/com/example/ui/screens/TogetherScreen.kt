package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.UserPreferencesRepository
import com.example.data.social.NearbyFriendPeer
import com.example.data.social.TogetherCompetitionMode
import com.example.data.social.TogetherFriendProfile
import com.example.data.social.TogetherReaction
import com.example.data.social.TogetherSharedGoal
import com.example.ui.components.AnimatedStreakPill
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FeeloraBannerTone
import com.example.ui.components.FeeloraScreenBackdrop
import com.example.ui.components.FeeloraSegmentedRow
import com.example.ui.components.FeeloraStatusBanner
import com.example.ui.components.FeeloraSubpageHeader
import com.example.ui.components.FeeloraToggleRow
import com.example.ui.components.GradientGlowCard
import com.example.ui.components.NeonProgressBar
import com.example.ui.components.ObsidianCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.UserAvatarBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.viewmodel.LifeOsUiState

private enum class TogetherTab(val label: String) {
    CHATS("Chats"),
    FRIENDS("Friends"),
    GOALS("Shared Goals"),
    COMPETE("Compete"),
    PROGRESS("Progress")
}

@Composable
fun TogetherScreen(
    state: LifeOsUiState,
    myInviteLink: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenChat: (TogetherFriendProfile) -> Unit,
    onSendFriendRequest: (String) -> Unit,
    onRespondToRequest: (String, Boolean) -> Unit,
    onCreateSharedGoal: (partnerId: String?, partnerName: String?, title: String, description: String, target: Int, unit: String) -> Unit,
    onIncrementGoal: (TogetherSharedGoal, Int) -> Unit,
    onCreateCompetition: (partnerId: String?, partnerName: String?, title: String, mode: TogetherCompetitionMode) -> Unit,
    onReactToActivity: (String, TogetherReaction) -> Unit,
    onUpdatePrivacy: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onImportLocalFriend: (String, (Boolean, String) -> Unit) -> Unit,
    onToggleNearbyRadar: () -> Unit,
    onSyncNearbyPeer: (NearbyFriendPeer) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    var selectedTab by remember { mutableStateOf(TogetherTab.CHATS) }
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var showCreateGoalDialog by remember { mutableStateOf(false) }
    var showCreateCompeteDialog by remember { mutableStateOf(false) }

    val together = state.togetherState
    val prefs = state.prefs
    val myCode = remember(prefs.friendCode, prefs.userHandle, prefs.userName) {
        prefs.friendCode.ifBlank {
            UserPreferencesRepository.generateFriendCode(prefs.userHandle, prefs.userName)
        }
    }

    fun shareInvite() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Join me on FEELORA Together! Friend Code: $myCode (${prefs.userHandle})\n$myInviteLink"
            )
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Invite to FEELORA Together")) }
    }

    FeeloraScreenBackdrop(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        when (selectedTab) {
                            TogetherTab.GOALS -> showCreateGoalDialog = true
                            TogetherTab.COMPETE -> showCreateCompeteDialog = true
                            else -> showAddFriendDialog = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = tokens.background,
                    modifier = Modifier.testTag("together_fab")
                ) {
                    Icon(
                        imageVector = if (selectedTab == TogetherTab.GOALS || selectedTab == TogetherTab.COMPETE) Icons.Filled.Add else Icons.Filled.PersonAdd,
                        contentDescription = "New Together action"
                    )
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("together_screen"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    FeeloraSubpageHeader(
                        title = "Together",
                        subtitle = if (together.isCloudConfigured) "Private social accountability circle" else "Cloud Together sync requires Supabase Secrets",
                        onBack = onBack,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(
                                    text = if (together.isCloudConfigured && prefs.hasValidRemoteSession) {
                                        if (together.isRealtimeConnected) "Live" else "Synced"
                                    } else {
                                        "Local"
                                    },
                                    color = if (together.isRealtimeConnected) EmeraldSuccess else AmberWarning,
                                    icon = if (together.isCloudConfigured && prefs.hasValidRemoteSession) Icons.Filled.CloudDone else Icons.Filled.CloudOff
                                )
                                IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh Together", tint = tokens.inkPrimary)
                                }
                            }
                        }
                    )
                }

                if (!together.isCloudConfigured || !prefs.hasValidRemoteSession) {
                    item {
                        FeeloraStatusBanner(
                            message = if (!together.isCloudConfigured) {
                                "Cloud Together sync requires SUPABASE_URL & SUPABASE_ANON_KEY in AI Studio Secrets. Nearby Wi-Fi & invite links remain available."
                            } else {
                                "Sign in with a cloud account in Onboarding / Settings to unlock live multi-device Together sync."
                            },
                            tone = FeeloraBannerTone.INFO
                        )
                    }
                }

                if (together.statusNote.isNotBlank()) {
                    item {
                        FeeloraStatusBanner(message = together.statusNote, tone = FeeloraBannerTone.INFO)
                    }
                }

                // My Invite Strip + Together Streak
                item {
                    GradientGlowCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatarBadge(
                                preset = prefs.avatarPreset,
                                photoUri = prefs.avatarPhotoUri,
                                displayName = prefs.userName,
                                size = 46.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prefs.userName.ifBlank { "Member" }, style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                                Text("${prefs.userHandle.ifBlank { "@member" }} · $myCode", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            }
                            AnimatedStreakPill(
                                streakDays = together.togetherStreakDays,
                                label = "${together.togetherStreakDays}d Circle"
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(myCode))
                                    onShowMessage("Friend Code copied: $myCode")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy Code", maxLines = 1, softWrap = false)
                            }
                            Button(
                                onClick = { shareInvite() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Invite", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                // 5 Tabs
                item {
                    FeeloraSegmentedRow(
                        items = TogetherTab.entries,
                        selectedItem = selectedTab,
                        labelFor = { it.label },
                        onSelect = { selectedTab = it },
                        tagPrefix = "together_tab"
                    )
                }

                when (selectedTab) {
                    TogetherTab.CHATS -> {
                        if (together.friends.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.AutoMirrored.Filled.Chat,
                                    title = "No active friend chats yet",
                                    subtitle = "Add a friend via username, Friend Code, or invite link to start private accountability messaging.",
                                    actionLabel = "Find Friend",
                                    onAction = { showAddFriendDialog = true }
                                )
                            }
                        } else {
                            items(together.friends, key = { "chat_${it.userId}" }) { friend ->
                                ObsidianCard(onClick = { onOpenChat(friend) }) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        UserAvatarBadge(
                                            preset = friend.avatarPreset,
                                            photoUri = friend.avatarUrl,
                                            displayName = friend.displayName,
                                            size = 44.dp
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(friend.displayName, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                            Text(
                                                text = friend.lastMessagePreview.ifBlank { "${friend.handle} · Tap to message" },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = tokens.inkSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (friend.unreadCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.secondary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${friend.unreadCount}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = tokens.background,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    TogetherTab.FRIENDS -> {
                        if (together.incomingRequests.isNotEmpty()) {
                            item { SectionHeader(title = "Incoming Requests", subtitle = "Approve or decline") }
                            items(together.incomingRequests, key = { "req_${it.id}" }) { req ->
                                ObsidianCard(borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(req.requesterName, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                            Text(req.requesterHandle, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                                        }
                                        IconButton(onClick = { onRespondToRequest(req.id, true) }) {
                                            Icon(Icons.Filled.Check, contentDescription = "Accept request", tint = EmeraldSuccess)
                                        }
                                        IconButton(onClick = { onRespondToRequest(req.id, false) }) {
                                            Icon(Icons.Filled.Close, contentDescription = "Decline request", tint = tokens.inkMuted)
                                        }
                                    }
                                }
                            }
                        }

                        // Nearby Wi-Fi Peer fallback
                        item {
                            ObsidianCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Filled.WifiTethering, contentDescription = null, tint = if (state.isPeerRadarActive) EmeraldSuccess else MaterialTheme.colorScheme.secondary)
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text("Nearby Wi-Fi Sync", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                            Text("Exchange safe stat snapshots locally on same Wi-Fi", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                                        }
                                    }
                                    OutlinedButton(onClick = onToggleNearbyRadar) {
                                        Text(if (state.isPeerRadarActive) "Stop" else "Scan", maxLines = 1, softWrap = false)
                                    }
                                }
                                if (state.isPeerRadarActive && state.nearbyPeers.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    state.nearbyPeers.forEach { peer ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(peer.serviceName, style = MaterialTheme.typography.bodyMedium, color = tokens.inkPrimary)
                                            Button(onClick = { onSyncNearbyPeer(peer) }) { Text("Sync") }
                                        }
                                    }
                                }
                            }
                        }

                        if (together.friends.isEmpty() && state.friends.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.Groups,
                                    title = "No friends connected yet",
                                    subtitle = "Tap + to send a friend request or import a Friend Code.",
                                    actionLabel = "Add Friend",
                                    onAction = { showAddFriendDialog = true }
                                )
                            }
                        } else {
                            items(together.friends, key = { "tf_${it.userId}" }) { f ->
                                ObsidianCard(onClick = { onOpenChat(f) }) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        UserAvatarBadge(preset = f.avatarPreset, photoUri = f.avatarUrl, displayName = f.displayName, size = 42.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(f.displayName, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                            Text(
                                                text = "${f.handle} · Lv.${f.level}" + if (f.shareXp) " · ${f.xp} XP" else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = tokens.inkSecondary
                                            )
                                        }
                                        if (f.shareStreaks) {
                                            AnimatedStreakPill(streakDays = f.dailyStreak, label = "${f.dailyStreak}d")
                                        }
                                    }
                                }
                            }

                            items(state.friends, key = { "lf_${it.id}" }) { lf ->
                                ObsidianCard {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        UserAvatarBadge(preset = prefs.avatarPreset, displayName = lf.displayName, size = 40.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(lf.displayName, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                            Text("${lf.handle} · ${if (lf.shareXp) "${lf.xp} XP" else "Private"}", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                                        }
                                        StatusPill(text = "${lf.streakDays}d", color = AmberWarning)
                                    }
                                }
                            }
                        }
                    }

                    TogetherTab.GOALS -> {
                        if (together.sharedGoals.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.Flag,
                                    title = "No shared goals yet",
                                    subtitle = "Create a Shared Goal with a friend (like completing 20 practice chapters together).",
                                    actionLabel = "Create Shared Goal",
                                    onAction = { showCreateGoalDialog = true }
                                )
                            }
                        } else {
                            items(together.sharedGoals, key = { "goal_${it.id}" }) { goal ->
                                val ratio = (goal.currentValue.toFloat() / goal.targetValue.coerceAtLeast(1)).coerceIn(0f, 1f)
                                ObsidianCard(elevated = true) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(goal.title, style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                                            if (goal.description.isNotBlank()) {
                                                Text(goal.description, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                                            }
                                            goal.partnerName?.let {
                                                Text("With $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                            }
                                        }
                                        StatusPill(
                                            text = "${goal.currentValue}/${goal.targetValue} ${goal.unitLabel}",
                                            color = if (goal.isCompleted) EmeraldSuccess else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    NeonProgressBar(progress = ratio, heightDp = 7)
                                    if (!goal.isCompleted) {
                                        Spacer(Modifier.height(8.dp))
                                        Button(
                                            onClick = { onIncrementGoal(goal, 1) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("+1 ${goal.unitLabel}", maxLines = 1, softWrap = false)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    TogetherTab.COMPETE -> {
                        item {
                            ObsidianCard {
                                SectionHeader(title = "5 Friendly Challenge Modes", subtitle = "Healthy motivation — zero toxic comparison")
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TogetherCompetitionMode.entries.forEach { mode ->
                                        FilterChip(
                                            selected = false,
                                            onClick = { showCreateCompeteDialog = true },
                                            label = { Text(mode.title, maxLines = 1, softWrap = false) }
                                        )
                                    }
                                }
                            }
                        }
                        if (together.competitions.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.EmojiEvents,
                                    title = "No active competitions",
                                    subtitle = "Start an XP Sprint, Task Finish Challenge, Study Focus Race, Streak Challenge, or Consistency Duel.",
                                    actionLabel = "Start Challenge",
                                    onAction = { showCreateCompeteDialog = true }
                                )
                            }
                        } else {
                            items(together.competitions, key = { "comp_${it.id}" }) { comp ->
                                ObsidianCard(elevated = true) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(comp.title, style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                                            Text("${comp.mode.title} · ${comp.daysRemaining}d left", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                        }
                                        StatusPill(text = comp.mode.metricUnit, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("You: ${comp.creatorScore} ${comp.mode.metricUnit}", style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary)
                                        Text("${comp.partnerName ?: "Friend"}: ${comp.partnerScore} ${comp.mode.metricUnit}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }
                        }
                    }

                    TogetherTab.PROGRESS -> {
                        item {
                            ObsidianCard {
                                SectionHeader(title = "Mutual Sharing Controls", subtitle = "Private journals, Cycle Care & SURU never sync")
                                Spacer(Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FeeloraToggleRow(
                                        title = "Share XP & Level",
                                        checked = prefs.shareXpWithFriends,
                                        onCheckedChange = {
                                            onUpdatePrivacy(it, prefs.shareStreaksWithFriends, prefs.shareFocusWithFriends, prefs.shareChallengesWithFriends)
                                        }
                                    )
                                    FeeloraToggleRow(
                                        title = "Share Daily Streaks",
                                        checked = prefs.shareStreaksWithFriends,
                                        onCheckedChange = {
                                            onUpdatePrivacy(prefs.shareXpWithFriends, it, prefs.shareFocusWithFriends, prefs.shareChallengesWithFriends)
                                        }
                                    )
                                    FeeloraToggleRow(
                                        title = "Share Focus Minutes",
                                        checked = prefs.shareFocusWithFriends,
                                        onCheckedChange = {
                                            onUpdatePrivacy(prefs.shareXpWithFriends, prefs.shareStreaksWithFriends, it, prefs.shareChallengesWithFriends)
                                        }
                                    )
                                    FeeloraToggleRow(
                                        title = "Share Challenges & Consistency",
                                        checked = prefs.shareChallengesWithFriends,
                                        onCheckedChange = {
                                            onUpdatePrivacy(prefs.shareXpWithFriends, prefs.shareStreaksWithFriends, prefs.shareFocusWithFriends, it)
                                        }
                                    )
                                }
                            }
                        }

                        if (together.activities.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.Timeline,
                                    title = "No circle milestones yet",
                                    subtitle = "Achievements shared by you or your friends appear here with encouraging reactions."
                                )
                            }
                        } else {
                            items(together.activities, key = { "act_${it.id}" }) { item ->
                                ObsidianCard {
                                    Text(item.authorName, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                    Spacer(Modifier.height(4.dp))
                                    Text(item.summary, style = MaterialTheme.typography.bodyMedium, color = tokens.inkSecondary)
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        TogetherReaction.entries.forEach { reaction ->
                                            val count = item.reactionCounts[reaction] ?: 0
                                            FilterChip(
                                                selected = item.myReaction == reaction,
                                                onClick = { onReactToActivity(item.id, reaction) },
                                                label = { Text(if (count > 0) "${reaction.label} ($count)" else reaction.label, maxLines = 1, softWrap = false) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showAddFriendDialog) {
        var query by remember { mutableStateOf("") }
        var feedback by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAddFriendDialog = false },
            containerColor = tokens.surfaceCard,
            title = { Text("Connect with Friend", color = tokens.inkPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter a @username, FLR-XXXX code, or paste a feelora://invite link.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkSecondary
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; feedback = null },
                        label = { Text("Username, Friend Code or Invite Link") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("together_add_friend_input")
                    )
                    feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = EmeraldSuccess) }
                }
            },
            confirmButton = {
                Button(
                    enabled = query.isNotBlank(),
                    onClick = {
                        val trimmed = query.trim()
                        if (trimmed.contains("://") || trimmed.startsWith("LIFE") || trimmed.startsWith("FLR")) {
                            onImportLocalFriend(trimmed) { ok, msg ->
                                if (ok) showAddFriendDialog = false else feedback = msg
                            }
                        } else {
                            onSendFriendRequest(trimmed)
                            showAddFriendDialog = false
                        }
                    }
                ) { Text("Connect") }
            },
            dismissButton = { TextButton(onClick = { showAddFriendDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCreateGoalDialog) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var target by remember { mutableStateOf("10") }
        var unit by remember { mutableStateOf("sessions") }
        var selectedPartner by remember { mutableStateOf(together.friends.firstOrNull()) }
        AlertDialog(
            onDismissRequest = { showCreateGoalDialog = false },
            containerColor = tokens.surfaceCard,
            title = { Text("New Shared Goal", color = tokens.inkPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Goal title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Notes (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = target, onValueChange = { target = it.filter(Char::isDigit) }, label = { Text("Target") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    if (together.friends.isNotEmpty()) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            together.friends.forEach { f ->
                                FilterChip(selected = selectedPartner?.userId == f.userId, onClick = { selectedPartner = f }, label = { Text(f.displayName) })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = title.isNotBlank(),
                    onClick = {
                        onCreateSharedGoal(
                            selectedPartner?.userId,
                            selectedPartner?.displayName,
                            title.trim(),
                            desc.trim(),
                            target.toIntOrNull()?.coerceAtLeast(1) ?: 10,
                            unit.ifBlank { "steps" }
                        )
                        showCreateGoalDialog = false
                    }
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateGoalDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCreateCompeteDialog) {
        var title by remember { mutableStateOf("") }
        var selectedMode by remember { mutableStateOf(TogetherCompetitionMode.XP_SPRINT) }
        var selectedPartner by remember { mutableStateOf(together.friends.firstOrNull()) }
        AlertDialog(
            onDismissRequest = { showCreateCompeteDialog = false },
            containerColor = tokens.surfaceCard,
            title = { Text("Start Friendly Challenge", color = tokens.inkPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Challenge title") }, placeholder = { Text("Weekend Study Sprint") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TogetherCompetitionMode.entries.forEach { mode ->
                            FilterChip(selected = selectedMode == mode, onClick = { selectedMode = mode }, label = { Text(mode.title, maxLines = 1, softWrap = false) })
                        }
                    }
                    Text(selectedMode.description, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                    if (together.friends.isNotEmpty()) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            together.friends.forEach { f ->
                                FilterChip(selected = selectedPartner?.userId == f.userId, onClick = { selectedPartner = f }, label = { Text(f.displayName) })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateCompetition(
                            selectedPartner?.userId,
                            selectedPartner?.displayName,
                            title.ifBlank { selectedMode.title },
                            selectedMode
                        )
                        showCreateCompeteDialog = false
                    }
                ) { Text("Start") }
            },
            dismissButton = { TextButton(onClick = { showCreateCompeteDialog = false }) { Text("Cancel") } }
        )
    }
}
