package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.FriendEntity
import com.example.data.social.TogetherCompetitionMode
import com.example.data.social.TogetherFriendProfile
import com.example.data.social.TogetherReaction
import com.example.data.social.TogetherSharedGoal
import com.example.ui.components.AnimatedStreakPill
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FeeloraBannerTone
import com.example.ui.components.FeeloraConfirmDialog
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
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.TogetherOneUiState

private enum class TogetherOneTab(val label: String) {
    OVERVIEW("Overview"),
    CIRCLE("Circle"),
    GOALS("Goals & Duels"),
    PRIVACY("Privacy & Feed")
}

@Composable
fun TogetherOneScreen(
    state: TogetherOneUiState,
    localCachedFriends: List<FriendEntity> = emptyList(),
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSendInviteOrRequest: (String, (Boolean, String) -> Unit) -> Unit,
    onRespondToRequest: (String, Boolean) -> Unit,
    onCancelOutgoingRequest: (String) -> Unit,
    onToggleTogetherOnePartner: (String, Boolean) -> Unit,
    onRemoveOrBlockFriend: (String, Boolean) -> Unit,
    onOpenChat: (TogetherFriendProfile) -> Unit,
    onStartCoStudy: (title: String, targetMinutes: Int, kuttaEmotion: String, cheerNote: String) -> Unit,
    onStopCoStudy: () -> Unit,
    onSendCheer: (friendUserId: String, cheerMessage: String) -> Unit,
    onCreateSharedGoal: (partnerId: String?, partnerName: String?, title: String, description: String, target: Int, unit: String) -> Unit,
    onIncrementGoal: (TogetherSharedGoal, Int) -> Unit,
    onCreateCompetition: (partnerId: String?, partnerName: String?, title: String, mode: TogetherCompetitionMode) -> Unit,
    onReactToActivity: (String, TogetherReaction) -> Unit,
    onUpdatePrivacy: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onRetryFailedMessages: () -> Unit,
    onClearFeedback: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    var selectedTab by remember { mutableStateOf(TogetherOneTab.OVERVIEW) }
    var showConnectDialog by remember { mutableStateOf(false) }
    var showCreateGoalDialog by remember { mutableStateOf(false) }
    var showCreateCompeteDialog by remember { mutableStateOf(false) }
    var pendingRemoveFriend by remember { mutableStateOf<Pair<TogetherFriendProfile, Boolean>?>(null) }

    val together = state.togetherState
    val prefs = state.prefs
    val partner = state.activePartner

    fun shareInvite() {
        val shareText = buildString {
            append("Join me on FEELORA Together One!")
            append("\nFriend Code: ${state.myFriendCode} (${prefs.userHandle.ifBlank { "@member" }})")
            if (state.myInviteLink.isNotBlank()) append("\nInvite Link: ${state.myInviteLink}")
            if (state.myDeepLink.isNotBlank()) append("\nApp Link: ${state.myDeepLink}")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Invite to FEELORA Together One")) }
    }

    FeeloraScreenBackdrop(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        when (selectedTab) {
                            TogetherOneTab.GOALS -> showCreateGoalDialog = true
                            else -> showConnectDialog = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = tokens.background,
                    modifier = Modifier.testTag("together_one_fab")
                ) {
                    Icon(
                        imageVector = if (selectedTab == TogetherOneTab.GOALS) Icons.Filled.Add else Icons.Filled.PersonAdd,
                        contentDescription = if (selectedTab == TogetherOneTab.GOALS) "New shared goal" else "Connect by code or link"
                    )
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("together_one_screen"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                item {
                    FeeloraSubpageHeader(
                        title = "Together One",
                        subtitle = if (together.isCloudConfigured) {
                            "Shared accountability, live co-study & chats"
                        } else {
                            "Configure Supabase Secrets for cloud sync"
                        },
                        onBack = onBack,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(
                                    text = when {
                                        !together.isCloudConfigured -> "Offline"
                                        together.isRealtimeConnected -> "Live"
                                        prefs.hasValidRemoteSession -> "Synced"
                                        else -> "Ready"
                                    },
                                    color = when {
                                        together.isRealtimeConnected -> EmeraldSuccess
                                        together.isCloudConfigured -> MaterialTheme.colorScheme.secondary
                                        else -> AmberWarning
                                    },
                                    icon = if (together.isCloudConfigured && state.isOnline) Icons.Filled.CloudDone else Icons.Filled.CloudOff
                                )
                                IconButton(
                                    onClick = onRefresh,
                                    modifier = Modifier.size(48.dp).testTag("together_refresh_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = "Refresh Together One",
                                        tint = tokens.inkPrimary
                                    )
                                }
                            }
                        }
                    )
                }

                if (state.isBusy || together.isSyncing) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                // Error / retry / feedback banners
                if (!together.isCloudConfigured) {
                    item {
                        FeeloraStatusBanner(
                            message = "Add SUPABASE_URL & SUPABASE_ANON_KEY in AI Studio Secrets to enable online partner sync, invite lookup, and real-time chat.",
                            tone = FeeloraBannerTone.INFO
                        )
                    }
                }

                if (together.lastMessageError != null || together.failedOutboxCount > 0) {
                    item {
                        val failedCount = together.failedOutboxCount
                        val msg = together.lastMessageError ?: "$failedCount queued message(s) waiting to send."
                        FeeloraStatusBanner(
                            message = msg,
                            tone = FeeloraBannerTone.ERROR,
                            actionLabel = "Retry",
                            onAction = onRetryFailedMessages,
                            modifier = Modifier.testTag("together_retry_banner")
                        )
                    }
                }

                state.errorMessage?.let { err ->
                    item {
                        FeeloraStatusBanner(
                            message = err,
                            tone = FeeloraBannerTone.ERROR,
                            onDismiss = onClearFeedback
                        )
                    }
                }

                state.feedbackMessage?.let { msg ->
                    item {
                        FeeloraStatusBanner(
                            message = msg,
                            tone = FeeloraBannerTone.SUCCESS,
                            onDismiss = onClearFeedback
                        )
                    }
                }

                // My Invite Strip
                item {
                    GradientGlowCard(modifier = Modifier.testTag("together_invite_card")) {
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
                                Text(
                                    text = prefs.userName.ifBlank { "Member" },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = tokens.inkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${prefs.userHandle.ifBlank { "@member" }} · ${state.myFriendCode}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                                    clipboard.setText(AnnotatedString(state.myFriendCode))
                                    onShowMessage("Friend Code copied: ${state.myFriendCode}")
                                },
                                modifier = Modifier.weight(1f).testTag("together_copy_code_btn")
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Code", maxLines = 1, softWrap = false)
                            }
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(state.myInviteLink))
                                    onShowMessage("Invite link copied")
                                },
                                modifier = Modifier.weight(1f).testTag("together_copy_link_btn")
                            ) {
                                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Link", maxLines = 1, softWrap = false)
                            }
                            Button(
                                onClick = { shareInvite() },
                                modifier = Modifier.weight(1f).testTag("together_share_invite_btn")
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                // Navigation Tabs
                item {
                    FeeloraSegmentedRow(
                        items = TogetherOneTab.entries,
                        selectedItem = selectedTab,
                        labelFor = { it.label },
                        onSelect = { selectedTab = it },
                        tagPrefix = "together_tab"
                    )
                }

                when (selectedTab) {
                    TogetherOneTab.OVERVIEW -> {
                        if (partner == null) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.Favorite,
                                    title = "Choose your Together One partner",
                                    subtitle = "Connect with a study buddy or close friend using a Friend Code or invite link to share live focus sessions, gentle accountability, and private chat.",
                                    actionLabel = "Connect by Code or Link",
                                    onAction = { showConnectDialog = true }
                                )
                            }
                        } else {
                            // Partner Spotlight & Side-by-Side Comparison
                            item {
                                TogetherPartnerComparisonCard(
                                    state = state,
                                    partner = partner,
                                    onOpenChat = { onOpenChat(partner) },
                                    onSendCheer = { cheer -> onSendCheer(partner.userId, cheer) }
                                )
                            }
                        }

                        // Live Co-Study Presence Card
                        item {
                            CoStudyPresenceCard(
                                state = state,
                                partner = partner,
                                onStartCoStudy = onStartCoStudy,
                                onStopCoStudy = onStopCoStudy,
                                onSendCheer = onSendCheer
                            )
                        }

                        // Active Shared Goals Preview
                        if (together.sharedGoals.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Shared Goals",
                                    subtitle = "Tap +1 when you make progress",
                                    trailing = {
                                        TextButton(onClick = { selectedTab = TogetherOneTab.GOALS }) {
                                            Text("View All", maxLines = 1, softWrap = false)
                                        }
                                    }
                                )
                            }
                            items(together.sharedGoals.take(2), key = { "ov_goal_${it.id}" }) { goal ->
                                SharedGoalCard(goal = goal, onIncrement = { onIncrementGoal(goal, 1) })
                            }
                        }
                    }

                    TogetherOneTab.CIRCLE -> {
                        item {
                            ObsidianCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Add by Friend Code or Link", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                        Text(
                                            "Supports FLR-XXXX-1234, @username, lifeos://friend, or https://feelora.app/invite",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.inkSecondary
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = { showConnectDialog = true },
                                        modifier = Modifier.testTag("circle_connect_btn")
                                    ) {
                                        Text("Connect", maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }

                        if (together.incomingRequests.isNotEmpty()) {
                            item { SectionHeader(title = "Incoming Requests", subtitle = "Approve or decline") }
                            items(together.incomingRequests, key = { "in_req_${it.id}" }) { req ->
                                ObsidianCard(borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(req.requesterName.ifBlank { "FEELORA Member" }, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                            Text(req.requesterHandle, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                                        }
                                        IconButton(
                                            onClick = { onRespondToRequest(req.id, true) },
                                            modifier = Modifier.size(48.dp).testTag("accept_req_${req.id}")
                                        ) {
                                            Icon(Icons.Filled.Check, contentDescription = "Accept request", tint = EmeraldSuccess)
                                        }
                                        IconButton(
                                            onClick = { onRespondToRequest(req.id, false) },
                                            modifier = Modifier.size(48.dp).testTag("decline_req_${req.id}")
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = "Decline request", tint = tokens.inkMuted)
                                        }
                                    }
                                }
                            }
                        }

                        if (together.outgoingRequests.isNotEmpty()) {
                            item { SectionHeader(title = "Sent Invites", subtitle = "Waiting for confirmation") }
                            items(together.outgoingRequests, key = { "out_req_${it.id}" }) { req ->
                                ObsidianCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                req.addresseeName.ifBlank { "Pending Invite" },
                                                style = MaterialTheme.typography.titleSmall,
                                                color = tokens.inkPrimary
                                            )
                                            Text(
                                                req.addresseeHandle.ifBlank { "Awaiting acceptance" },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = tokens.inkSecondary
                                            )
                                        }
                                        TextButton(
                                            onClick = { onCancelOutgoingRequest(req.id) },
                                            modifier = Modifier.testTag("cancel_req_${req.id}")
                                        ) {
                                            Text("Cancel", color = RoseAlert, maxLines = 1, softWrap = false)
                                        }
                                    }
                                }
                            }
                        }

                        if (together.friends.isEmpty() && localCachedFriends.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.Groups,
                                    title = "No connected friends yet",
                                    subtitle = "Share your Friend Code or paste your partner's invite link to start.",
                                    actionLabel = "Connect Now",
                                    onAction = { showConnectDialog = true }
                                )
                            }
                        } else {
                            if (together.friends.isNotEmpty()) {
                                item { SectionHeader(title = "Your Circle", subtitle = "Star one partner for Together One spotlight") }
                                items(together.friends, key = { "friend_${it.userId}" }) { friend ->
                                    ObsidianCard(
                                        onClick = { onOpenChat(friend) },
                                        borderColor = if (friend.isTogetherOne) MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f) else null
                                    ) {
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
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        friend.displayName,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = tokens.inkPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (friend.isTogetherOne) {
                                                        StatusPill(text = "Together One", color = MaterialTheme.colorScheme.secondary)
                                                    }
                                                }
                                                val meta = buildString {
                                                    append(friend.handle)
                                                    if (friend.shareXp) append(" · Lv.${friend.level} (${friend.xp} XP)")
                                                    else append(" · XP Private")
                                                    if (friend.shareFocus) append(" · ${friend.focusMinutesToday}m focus")
                                                }
                                                Text(meta, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                if (friend.lastMessagePreview.isNotBlank()) {
                                                    Text(
                                                        friend.lastMessagePreview,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = tokens.inkMuted,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            if (friend.unreadCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.secondary),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("${friend.unreadCount}", style = MaterialTheme.typography.labelSmall, color = tokens.background, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            IconButton(
                                                onClick = { onToggleTogetherOnePartner(friend.userId, !friend.isTogetherOne) },
                                                modifier = Modifier.size(48.dp).testTag("star_partner_${friend.userId}")
                                            ) {
                                                Icon(
                                                    imageVector = if (friend.isTogetherOne) Icons.Filled.Star else Icons.Filled.StarOutline,
                                                    contentDescription = if (friend.isTogetherOne) "Unpin Together One partner" else "Pin as Together One partner",
                                                    tint = if (friend.isTogetherOne) AmberWarning else tokens.inkMuted
                                                )
                                            }
                                            IconButton(
                                                onClick = { pendingRemoveFriend = friend to false },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove connection", tint = tokens.inkMuted)
                                            }
                                        }
                                    }
                                }
                            } else if (localCachedFriends.isNotEmpty()) {
                                item { SectionHeader(title = "Cached Connections", subtitle = "Tap Refresh when back online") }
                                items(localCachedFriends, key = { "cached_${it.id}" }) { lf ->
                                    ObsidianCard {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            UserAvatarBadge(preset = prefs.avatarPreset, displayName = lf.displayName, size = 40.dp)
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(lf.displayName, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                                Text(
                                                    "${lf.handle} · ${if (lf.shareXp) "${lf.xp} XP" else "Private"}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = tokens.inkSecondary
                                                )
                                            }
                                            if (lf.shareStreaks) {
                                                StatusPill(text = "${lf.streakDays}d", color = AmberWarning)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    TogetherOneTab.GOALS -> {
                        item {
                            SectionHeader(
                                title = "Shared Goals",
                                subtitle = "Build momentum together",
                                trailing = {
                                    TextButton(onClick = { showCreateGoalDialog = true }) {
                                        Text("+ New Goal", maxLines = 1, softWrap = false)
                                    }
                                }
                            )
                        }
                        if (together.sharedGoals.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.Flag,
                                    title = "No shared goals yet",
                                    subtitle = "Set a joint milestone like finishing 20 study blocks or 10 practice sets together.",
                                    actionLabel = "Create Shared Goal",
                                    onAction = { showCreateGoalDialog = true }
                                )
                            }
                        } else {
                            items(together.sharedGoals, key = { "goal_${it.id}" }) { goal ->
                                SharedGoalCard(goal = goal, onIncrement = { onIncrementGoal(goal, 1) })
                            }
                        }

                        item {
                            Spacer(Modifier.height(4.dp))
                            ObsidianCard {
                                SectionHeader(
                                    title = "Friendly Duels",
                                    subtitle = "Healthy accountability — 5 challenge modes",
                                    trailing = {
                                        TextButton(onClick = { showCreateCompeteDialog = true }) {
                                            Text("+ Duel", maxLines = 1, softWrap = false)
                                        }
                                    }
                                )
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
                                    title = "No active friendly duels",
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
                                        Text("${comp.partnerName ?: "Partner"}: ${comp.partnerScore} ${comp.mode.metricUnit}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }
                        }
                    }

                    TogetherOneTab.PRIVACY -> {
                        item {
                            ObsidianCard {
                                SectionHeader(
                                    title = "Mutual Sharing Controls",
                                    subtitle = "Cycle Care, Daily Check-Ins, Quick Notes & SURU Memories never leave this device"
                                )
                                Spacer(Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FeeloraToggleRow(
                                        title = "Share XP & Level",
                                        checked = prefs.shareXpWithFriends,
                                        onCheckedChange = {
                                            onUpdatePrivacy(it, prefs.shareStreaksWithFriends, prefs.shareFocusWithFriends, prefs.shareChallengesWithFriends)
                                        },
                                        icon = Icons.Filled.AutoAwesome,
                                        testTag = "privacy_toggle_xp"
                                    )
                                    FeeloraToggleRow(
                                        title = "Share Daily Streaks",
                                        checked = prefs.shareStreaksWithFriends,
                                        onCheckedChange = {
                                            onUpdatePrivacy(prefs.shareXpWithFriends, it, prefs.shareFocusWithFriends, prefs.shareChallengesWithFriends)
                                        },
                                        icon = Icons.Filled.Favorite,
                                        testTag = "privacy_toggle_streaks"
                                    )
                                    FeeloraToggleRow(
                                        title = "Share Focus Minutes",
                                        checked = prefs.shareFocusWithFriends,
                                        onCheckedChange = {
                                            onUpdatePrivacy(prefs.shareXpWithFriends, prefs.shareStreaksWithFriends, it, prefs.shareChallengesWithFriends)
                                        },
                                        icon = Icons.Filled.Timer,
                                        testTag = "privacy_toggle_focus"
                                    )
                                    FeeloraToggleRow(
                                        title = "Share Challenges & Goals",
                                        checked = prefs.shareChallengesWithFriends,
                                        onCheckedChange = {
                                            onUpdatePrivacy(prefs.shareXpWithFriends, prefs.shareStreaksWithFriends, prefs.shareFocusWithFriends, it)
                                        },
                                        icon = Icons.Filled.Flag,
                                        testTag = "privacy_toggle_challenges"
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Private Cycle Care logs and Daily Check-Ins remain 100% local-only.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.inkSecondary
                                    )
                                }
                            }
                        }

                        if (together.activities.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.Timeline,
                                    title = "No circle milestones yet",
                                    subtitle = "Completed shared goals and KUTTA cheers from your circle show up here."
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
                                                label = {
                                                    Text(
                                                        if (count > 0) "${reaction.label} ($count)" else reaction.label,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
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

    if (showConnectDialog) {
        var query by remember { mutableStateOf("") }
        var feedback by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showConnectDialog = false },
            containerColor = tokens.surfaceCard,
            title = { Text("Connect in Together One", color = tokens.inkPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter a Friend Code (FLR-XXXX-1234), @username, or paste a feelora.app / lifeos:// invite link.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkSecondary
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; feedback = null },
                        label = { Text("Friend Code, @username or Invite Link") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("together_connect_input")
                    )
                    feedback?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = RoseAlert)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = query.isNotBlank(),
                    onClick = {
                        onSendInviteOrRequest(query.trim()) { ok, msg ->
                            if (ok) showConnectDialog = false else feedback = msg
                        }
                    },
                    modifier = Modifier.testTag("together_connect_confirm_btn")
                ) { Text("Send Request", maxLines = 1, softWrap = false) }
            },
            dismissButton = {
                TextButton(onClick = { showConnectDialog = false }) { Text("Cancel") }
            }
        )
    }

    pendingRemoveFriend?.let { (target, isBlock) ->
        FeeloraConfirmDialog(
            title = if (isBlock) "Block ${target.displayName}?" else "Remove ${target.displayName}?",
            message = if (isBlock) {
                "They will no longer be able to send requests or messages."
            } else {
                "Remove ${target.displayName} from your Together circle? You can always reconnect later with a Friend Code."
            },
            confirmLabel = if (isBlock) "Block" else "Remove",
            isDestructive = true,
            onConfirm = {
                onRemoveOrBlockFriend(target.userId, isBlock)
                pendingRemoveFriend = null
            },
            onDismiss = { pendingRemoveFriend = null }
        )
    }

    if (showCreateGoalDialog) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var target by remember { mutableStateOf("10") }
        var unit by remember { mutableStateOf("sessions") }
        var selectedPartner by remember { mutableStateOf(partner ?: together.friends.firstOrNull()) }
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
                                FilterChip(
                                    selected = selectedPartner?.userId == f.userId,
                                    onClick = { selectedPartner = f },
                                    label = { Text(f.displayName, maxLines = 1, softWrap = false) }
                                )
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
        var selectedPartner by remember { mutableStateOf(partner ?: together.friends.firstOrNull()) }
        AlertDialog(
            onDismissRequest = { showCreateCompeteDialog = false },
            containerColor = tokens.surfaceCard,
            title = { Text("Start Friendly Challenge", color = tokens.inkPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Challenge title") },
                        placeholder = { Text("Weekend Study Sprint") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TogetherCompetitionMode.entries.forEach { mode ->
                            FilterChip(
                                selected = selectedMode == mode,
                                onClick = { selectedMode = mode },
                                label = { Text(mode.title, maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                    Text(selectedMode.description, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                    if (together.friends.isNotEmpty()) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            together.friends.forEach { f ->
                                FilterChip(
                                    selected = selectedPartner?.userId == f.userId,
                                    onClick = { selectedPartner = f },
                                    label = { Text(f.displayName, maxLines = 1, softWrap = false) }
                                )
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

@Composable
private fun TogetherPartnerComparisonCard(
    state: TogetherOneUiState,
    partner: TogetherFriendProfile,
    onOpenChat: () -> Unit,
    onSendCheer: (String) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val prefs = state.prefs

    ObsidianCard(
        elevated = true,
        borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
        modifier = Modifier.testTag("together_partner_spotlight")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatarBadge(
                preset = partner.avatarPreset,
                photoUri = partner.avatarUrl,
                displayName = partner.displayName,
                size = 46.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = partner.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    StatusPill(text = "Together One", color = MaterialTheme.colorScheme.secondary)
                }
                Text(
                    text = "${partner.handle} · ${partner.petStageName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onOpenChat,
                modifier = Modifier.testTag("together_open_chat_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Chat", maxLines = 1, softWrap = false)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Side-by-side comparison grid respecting partner sharing preferences
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComparisonStatBox(
                    label = "Level & XP",
                    myValue = "Lv.${prefs.level} · ${prefs.totalXp} XP",
                    partnerName = partner.displayName,
                    partnerValue = if (partner.shareXp) "Lv.${partner.level} · ${partner.xp} XP" else "Private",
                    isPartnerPrivate = !partner.shareXp,
                    modifier = Modifier.weight(1f)
                )
                ComparisonStatBox(
                    label = "Daily Streak",
                    myValue = "${prefs.dailyStreak}d",
                    partnerName = partner.displayName,
                    partnerValue = if (partner.shareStreaks) "${partner.dailyStreak}d" else "Private",
                    isPartnerPrivate = !partner.shareStreaks,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComparisonStatBox(
                    label = "Focus Today",
                    myValue = "${prefs.focusMinutesLoggedToday}m",
                    partnerName = partner.displayName,
                    partnerValue = if (partner.shareFocus) "${partner.focusMinutesToday}m" else "Private",
                    isPartnerPrivate = !partner.shareFocus,
                    modifier = Modifier.weight(1f)
                )
                ComparisonStatBox(
                    label = "Challenges",
                    myValue = "${state.myChallengesCompleted}/${state.myChallengesTotal}",
                    partnerName = partner.displayName,
                    partnerValue = if (partner.shareChallenges) "${partner.challengesCompleted}/${partner.challengesTotal}" else "Private",
                    isPartnerPrivate = !partner.shareChallenges,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Proud of you!", "Keep going!", "Co-study sprint?").forEach { cheer ->
                FilterChip(
                    selected = false,
                    onClick = { onSendCheer(cheer) },
                    label = { Text(cheer, maxLines = 1, softWrap = false) }
                )
            }
        }
    }
}

@Composable
private fun ComparisonStatBox(
    label: String,
    myValue: String,
    partnerName: String,
    partnerValue: String,
    isPartnerPrivate: Boolean,
    modifier: Modifier = Modifier
) {
    val tokens = LifeOsThemeTokensProvider.current
    val shape = RoundedCornerShape(tokens.smallRadius)
    Surface(
        modifier = modifier,
        shape = shape,
        color = tokens.surfaceElevated.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, tokens.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted, maxLines = 1, softWrap = false)
            Text("You: $myValue", style = MaterialTheme.typography.labelMedium, color = tokens.inkPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = "${partnerName.substringBefore(" ")}: $partnerValue",
                style = MaterialTheme.typography.labelMedium,
                color = if (isPartnerPrivate) tokens.inkMuted else MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CoStudyPresenceCard(
    state: TogetherOneUiState,
    partner: TogetherFriendProfile?,
    onStartCoStudy: (title: String, targetMinutes: Int, kuttaEmotion: String, cheerNote: String) -> Unit,
    onStopCoStudy: () -> Unit,
    onSendCheer: (friendUserId: String, cheerMessage: String) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val myPresence = state.togetherState.myPresence
    val partnerPresence = state.partnerPresence
    val isMyStudying = myPresence?.isStudying == true

    var sessionTitle by remember { mutableStateOf("Deep Focus Co-Study") }
    var selectedDuration by remember { mutableIntStateOf(25) }

    ObsidianCard(modifier = Modifier.testTag("together_costudy_card")) {
        SectionHeader(
            title = "Live Co-Study Room",
            subtitle = if (isMyStudying) {
                "Your live session: ${myPresence?.sessionTitle.orEmpty()} (${myPresence?.remainingMinutes() ?: selectedDuration}m left)"
            } else {
                "Broadcast your active study status & KUTTA mood"
            },
            trailing = {
                StatusPill(
                    text = if (isMyStudying) "Studying" else "Idle",
                    color = if (isMyStudying) EmeraldSuccess else tokens.inkMuted
                )
            }
        )

        if (partner != null) {
            Spacer(Modifier.height(10.dp))
            val partnerActive = partnerPresence?.isStudying == true
            Surface(
                shape = RoundedCornerShape(tokens.smallRadius),
                color = if (partnerActive) EmeraldSuccess.copy(alpha = 0.10f) else tokens.surfaceElevated,
                border = BorderStroke(1.dp, if (partnerActive) EmeraldSuccess.copy(alpha = 0.45f) else tokens.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (partnerActive) {
                                "${partner.displayName} is studying now"
                            } else {
                                "${partner.displayName} is currently idle"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.inkPrimary
                        )
                        val detail = if (partnerActive) {
                            "${partnerPresence?.sessionTitle.orEmpty()} · ${partnerPresence?.remainingMinutes() ?: 0}m remaining · KUTTA: ${partnerPresence?.kuttaEmotion ?: "FOCUSED"}"
                        } else {
                            "Send a gentle invite to start a co-study sprint together."
                        }
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                    }
                    if (!partnerActive) {
                        TextButton(onClick = { onSendCheer(partner.userId, "Want to start a ${selectedDuration}m co-study sprint?") }) {
                            Text("Nudge", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        AnimatedVisibility(visible = !isMyStudying) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = sessionTitle,
                    onValueChange = { sessionTitle = it },
                    label = { Text("Session topic") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("costudy_title_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 45, 60).forEach { mins ->
                        FilterChip(
                            selected = selectedDuration == mins,
                            onClick = { selectedDuration = mins },
                            label = { Text("${mins}m", maxLines = 1, softWrap = false) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (isMyStudying) {
                    onStopCoStudy()
                } else {
                    onStartCoStudy(
                        sessionTitle.ifBlank { "Deep Focus Co-Study" },
                        selectedDuration,
                        "FOCUSED",
                        "Studying alongside my Together One circle!"
                    )
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isMyStudying) RoseAlert else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth().testTag("together_costudy_toggle_btn")
        ) {
            Icon(
                imageVector = if (isMyStudying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isMyStudying) "End Co-Study Presence" else "Start Co-Study (${selectedDuration}m)",
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun SharedGoalCard(
    goal: TogetherSharedGoal,
    onIncrement: () -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
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
                onClick = onIncrement,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("+1 ${goal.unitLabel}", maxLines = 1, softWrap = false)
            }
        }
    }
}
