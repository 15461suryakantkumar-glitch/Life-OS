package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.FriendEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.social.FriendSyncCodec
import com.example.data.social.NearbyFriendPeer
import com.example.data.social.TogetherConnectStatus
import com.example.data.social.TogetherFriendProfile
import com.example.data.social.TogetherReaction
import com.example.ui.components.AnimatedStreakPill
import com.example.ui.components.FeeloraBannerTone
import com.example.ui.components.FeeloraScreenBackdrop
import com.example.ui.components.FeeloraStatusBanner
import com.example.ui.components.FeeloraSubpageHeader
import com.example.ui.components.FeeloraToggleRow
import com.example.ui.components.GradientGlowCard
import com.example.ui.components.ObsidianCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.UserAvatarBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FeeloraTypeHelpers
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.LifeOsUiState

@Composable
fun TogetherOneScreen(
    state: LifeOsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onConnectCode: (String) -> Unit,
    onClearConnectFeedback: () -> Unit,
    onSendCheer: (TogetherFriendProfile) -> Unit,
    onSendQuickNote: (note: String, targetFriend: TogetherFriendProfile?) -> Unit,
    onOpenChat: (TogetherFriendProfile) -> Unit,
    onRespondToRequest: (String, Boolean) -> Unit,
    onReactToActivity: (String, TogetherReaction) -> Unit,
    onUpdatePrivacy: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onToggleNearbyRadar: () -> Unit,
    onSyncNearbyPeer: (NearbyFriendPeer) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    val together = state.togetherState
    val prefs = state.prefs
    val myCode = remember(prefs.friendCode, prefs.userHandle, prefs.userName) {
        prefs.friendCode.ifBlank {
            UserPreferencesRepository.generateFriendCode(prefs.userHandle, prefs.userName)
        }
    }
    val myDeepLink = remember(myCode) {
        FriendSyncCodec.buildFriendCodeLink(myCode)
    }

    var codeInput by rememberSaveable { mutableStateOf(together.pendingConnectCode) }
    LaunchedEffect(together.pendingConnectCode, together.quickConnectStatus) {
        if (together.pendingConnectCode.isNotBlank()) {
            if (together.pendingConnectCode != codeInput) {
                codeInput = together.pendingConnectCode
            }
        } else if (together.quickConnectStatus is TogetherConnectStatus.Success) {
            codeInput = ""
        }
    }

    val mergedFriends = remember(together.friends, state.friends) {
        mergeConnectedFriends(together.friends, state.friends)
    }

    var quickNoteText by rememberSaveable { mutableStateOf("") }
    var selectedTargetUserId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedTargetFriend = remember(selectedTargetUserId, mergedFriends) {
        mergedFriends.firstOrNull { it.userId == selectedTargetUserId }
    }

    fun shareMyFriendCode() {
        val shareBody = "Connect with me on Feelora Together One!\nFriend Code: $myCode\nTap to connect: $myDeepLink"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareBody)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Share Feelora Friend Code"))
        }.onFailure {
            @Suppress("DEPRECATION")
            clipboard.setText(AnnotatedString(myDeepLink))
            onShowMessage("Share link copied to clipboard")
        }
    }

    FeeloraScreenBackdrop(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("together_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                FeeloraSubpageHeader(
                    title = "Together One",
                    subtitle = "Stay close, share calm momentum & cheer together",
                    onBack = onBack,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(
                                text = if (together.isRealtimeConnected) {
                                    "Live"
                                } else if (together.isCloudConfigured && prefs.hasValidRemoteSession) {
                                    "Synced"
                                } else {
                                    "Ready"
                                },
                                color = if (together.isRealtimeConnected || together.isCloudConfigured) {
                                    EmeraldSuccess
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                icon = Icons.Filled.CloudDone
                            )
                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("together_refresh_btn")
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

            // 1. Top Card: My Friend Code with One-Tap Copy & Share
            item {
                GradientGlowCard(modifier = Modifier.testTag("together_my_code_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatarBadge(
                            preset = prefs.avatarPreset,
                            photoUri = prefs.avatarPhotoUri,
                            displayName = prefs.userName,
                            size = 48.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = prefs.userName.ifBlank { "Feelora Friend" },
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.inkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = prefs.userHandle.ifBlank { "@feelora" },
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        AnimatedStreakPill(
                            streakDays = together.togetherStreakDays,
                            label = "${together.togetherStreakDays}d Circle"
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(tokens.smallRadius),
                        color = tokens.surfaceCard.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, tokens.borderAccent)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "YOUR FRIEND CODE",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.inkMuted
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = myCode,
                                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("together_my_friend_code")
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                @Suppress("DEPRECATION")
                                clipboard.setText(AnnotatedString(myCode))
                                onShowMessage("Copied Friend Code $myCode")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("together_copy_code_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy friend code",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Copy")
                        }
                        Button(
                            onClick = ::shareMyFriendCode,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("together_share_code_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share friend code",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }
            }

            // 2. Quick Connect Card
            item {
                val isConnecting = together.quickConnectStatus is TogetherConnectStatus.Loading
                ObsidianCard(
                    elevated = true,
                    modifier = Modifier.testTag("together_quick_connect_card")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PersonAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Quick Connect",
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.inkPrimary
                            )
                            Text(
                                text = "Enter a friend code, @handle, or invite link",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkSecondary
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { next ->
                            codeInput = next
                            if (together.quickConnectStatus !is TogetherConnectStatus.Idle) {
                                onClearConnectFeedback()
                            }
                        },
                        label = { Text("Friend code (e.g. FLR-ARIA-4821)") },
                        singleLine = true,
                        enabled = !isConnecting,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (codeInput.isNotBlank() && !isConnecting) {
                                    onConnectCode(codeInput)
                                }
                            }
                        ),
                        trailingIcon = {
                            if (codeInput.isNotEmpty() && !isConnecting) {
                                IconButton(onClick = {
                                    codeInput = ""
                                    onClearConnectFeedback()
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear friend code")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("together_quick_connect_input")
                    )

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = { onConnectCode(codeInput) },
                        enabled = codeInput.isNotBlank() && !isConnecting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("together_quick_connect_btn")
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Connecting...")
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Connect")
                        }
                    }

                    AnimatedVisibility(visible = together.quickConnectStatus !is TogetherConnectStatus.Idle) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            when (val status = together.quickConnectStatus) {
                                is TogetherConnectStatus.Loading -> {
                                    FeeloraStatusBanner(
                                        message = "Looking up friend code...",
                                        tone = FeeloraBannerTone.INFO,
                                        modifier = Modifier.testTag("together_quick_connect_feedback")
                                    )
                                }
                                is TogetherConnectStatus.Success -> {
                                    FeeloraStatusBanner(
                                        message = status.message,
                                        tone = FeeloraBannerTone.SUCCESS,
                                        onDismiss = onClearConnectFeedback,
                                        modifier = Modifier.testTag("together_quick_connect_feedback")
                                    )
                                }
                                is TogetherConnectStatus.Error -> {
                                    FeeloraStatusBanner(
                                        message = status.message,
                                        tone = FeeloraBannerTone.ERROR,
                                        onDismiss = onClearConnectFeedback,
                                        modifier = Modifier.testTag("together_quick_connect_feedback")
                                    )
                                }
                                TogetherConnectStatus.Idle -> Unit
                            }
                        }
                    }
                }
            }

            // Incoming Requests (if any)
            if (together.incomingRequests.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Pending Invites",
                        subtitle = "${together.incomingRequests.size} waiting"
                    )
                }
                items(together.incomingRequests, key = { "req_${it.id}" }) { req ->
                    ObsidianCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(req.requesterName, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                Text(req.requesterHandle, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                            }
                            IconButton(
                                onClick = { onRespondToRequest(req.id, false) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Decline request", tint = RoseAlert)
                            }
                            IconButton(
                                onClick = { onRespondToRequest(req.id, true) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = "Accept request", tint = EmeraldSuccess)
                            }
                        }
                    }
                }
            }

            // 3. Connected Friends & Quick Sync Section
            item {
                SectionHeader(
                    title = "Connected Friends",
                    subtitle = if (mergedFriends.isEmpty()) {
                        "Connect above using a friend's code"
                    } else {
                        "${mergedFriends.size} connected · tap a card to open chat"
                    }
                )
            }

            if (mergedFriends.isEmpty()) {
                item {
                    ObsidianCard(modifier = Modifier.testTag("together_empty_friends_card")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Groups,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "No friends connected yet",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = tokens.inkPrimary
                                )
                                Text(
                                    text = "Share your code $myCode or enter a friend's code above to start syncing cheers and study notes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.inkSecondary
                                )
                            }
                        }
                    }
                }
            } else {
                val featuredFriend = mergedFriends.first()
                item {
                    val myProgressPct = state.productivityScore.coerceIn(10, 100)
                    val friendProgressPct = ((featuredFriend.consistencyScore.takeIf { it > 0 } ?: (featuredFriend.dailyStreak * 12 + 36))).coerceIn(12, 100)
                    GradientGlowCard(
                        modifier = Modifier.testTag("together_shared_progress_card")
                    ) {
                        Surface(
                            shape = RoundedCornerShape(tokens.smallRadius),
                            color = tokens.surfaceCard.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, tokens.borderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Connected with ${featuredFriend.displayName} ♡",
                                style = MaterialTheme.typography.titleSmall,
                                color = tokens.inkPrimary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Shared Progress",
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.inkPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SharedProgressRing(
                                label = "You",
                                percent = myProgressPct,
                                ringColor = MaterialTheme.colorScheme.primary,
                                trackColor = tokens.borderSubtle
                            )
                            SharedProgressRing(
                                label = featuredFriend.displayName,
                                percent = friendProgressPct,
                                ringColor = MaterialTheme.colorScheme.secondary,
                                trackColor = tokens.borderSubtle
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { onOpenChat(featuredFriend) },
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("together_featured_chat_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Chat Together", maxLines = 1, softWrap = false)
                        }
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(tokens.smallRadius),
                            color = tokens.surfaceCard.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, tokens.borderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(
                                    text = "Shared Goals",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = tokens.inkPrimary
                                )
                                Text(
                                    text = "Study 5 hours this week · ${(state.prefs.focusMinutesLoggedToday / 60)}h ${state.prefs.focusMinutesLoggedToday % 60}m logged today",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                    color = tokens.inkSecondary
                                )
                            }
                        }
                    }
                }
                items(mergedFriends, key = { it.userId }) { friend ->
                    ConnectedFriendLiveCard(
                        friend = friend,
                        onSendCheer = { onSendCheer(friend) },
                        onOpenChat = { onOpenChat(friend) }
                    )
                }
            }

            // Quick Note / Check-In Composer
            item {
                ObsidianCard(
                    elevated = true,
                    modifier = Modifier.testTag("together_quick_note_card")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Quick Check-In Note",
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.inkPrimary
                            )
                            Text(
                                text = "Share your study vibe or a gentle check-in",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkSecondary
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    val quickPrompts = listOf(
                        "Focusing now 🎯",
                        "Water break 💧",
                        "Proud of you! ✨",
                        "25m sprint?"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickPrompts.forEach { preset ->
                            FilterChip(
                                selected = quickNoteText == preset,
                                onClick = { quickNoteText = preset },
                                label = { Text(preset) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }

                    if (mergedFriends.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedTargetUserId == null,
                                onClick = { selectedTargetUserId = null },
                                label = { Text("All Circle") },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                            mergedFriends.forEach { friend ->
                                FilterChip(
                                    selected = selectedTargetUserId == friend.userId,
                                    onClick = { selectedTargetUserId = friend.userId },
                                    label = { Text(friend.displayName) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = quickNoteText,
                        onValueChange = { quickNoteText = it },
                        label = {
                            Text(
                                if (selectedTargetFriend != null) {
                                    "Send note to ${selectedTargetFriend.displayName}"
                                } else {
                                    "Share a quick check-in note"
                                }
                            )
                        },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("together_quick_note_input")
                    )

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val note = quickNoteText.trim()
                            if (note.isNotBlank()) {
                                onSendQuickNote(note, selectedTargetFriend)
                                quickNoteText = ""
                            }
                        },
                        enabled = quickNoteText.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("together_quick_note_send_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (selectedTargetFriend != null) {
                                "Send Note to ${selectedTargetFriend.displayName}"
                            } else {
                                "Send Note"
                            }
                        )
                    }
                }
            }

            // Recent Circle Updates (when any cheers/notes/activities exist)
            if (together.activities.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Recent Circle Pulse",
                        subtitle = "Cheers and check-ins from your circle"
                    )
                }
                items(together.activities.take(6), key = { it.id }) { act ->
                    ObsidianCard {
                        Text(act.authorName, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                        Spacer(Modifier.height(2.dp))
                        Text(act.summary, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TogetherReaction.entries.forEach { reaction ->
                                val count = act.reactionCounts[reaction] ?: 0
                                val picked = act.myReaction == reaction
                                FilterChip(
                                    selected = picked,
                                    onClick = { onReactToActivity(act.id, reaction) },
                                    label = {
                                        Text(if (count > 0) "${reaction.label} ($count)" else reaction.label)
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Nearby Wi-Fi Sync & Privacy Controls
            item {
                ObsidianCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WifiTethering,
                            contentDescription = null,
                            tint = if (state.isPeerRadarActive) EmeraldSuccess else MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Nearby Wi-Fi Sync", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                            Text(
                                text = if (state.isPeerRadarActive) "Scanning local Wi-Fi..." else "Find Feelora friends on the same Wi-Fi",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkSecondary
                            )
                        }
                        OutlinedButton(
                            onClick = onToggleNearbyRadar,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(if (state.isPeerRadarActive) "Stop" else "Scan")
                        }
                    }

                    state.nearbyPeers.forEach { peer ->
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(tokens.smallRadius))
                                .background(tokens.surfaceElevated)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(peer.serviceName, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary, modifier = Modifier.weight(1f))
                            Button(
                                onClick = { onSyncNearbyPeer(peer) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Sync")
                            }
                        }
                    }
                }
            }

            item {
                ObsidianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sharing Preferences", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Cycle Care, personal check-ins, and SURU memories stay strictly private on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FeeloraToggleRow(
                            title = "Share XP & Level",
                            checked = prefs.shareXpWithFriends,
                            onCheckedChange = {
                                onUpdatePrivacy(it, prefs.shareStreaksWithFriends, prefs.shareFocusWithFriends, prefs.shareChallengesWithFriends)
                            }
                        )
                        FeeloraToggleRow(
                            title = "Share Streaks",
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
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedFriendLiveCard(
    friend: TogetherFriendProfile,
    onSendCheer: () -> Unit,
    onOpenChat: () -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val activityLine = remember(friend) {
        buildString {
            if (friend.presenceStatus.isNotBlank()) {
                append(friend.presenceStatus)
            } else if (friend.isFocusingNow) {
                append("Focusing now")
            } else {
                append("Lv.${friend.level} · ${friend.dailyStreak}d streak")
            }
            if (friend.shareFocus && friend.focusMinutesToday > 0) {
                append(" · ${friend.focusMinutesToday}m focus")
            }
        }
    }

    ObsidianCard(
        onClick = onOpenChat,
        modifier = Modifier.testTag("together_friend_card_${friend.userId}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatarBadge(
                preset = friend.avatarPreset,
                photoUri = friend.avatarUrl,
                displayName = friend.displayName,
                size = 46.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = friend.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (friend.isFocusingNow) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(EmeraldSuccess)
                        )
                    }
                }
                Text(
                    text = activityLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (friend.isFocusingNow) EmeraldSuccess else tokens.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val preview = friend.lastMessagePreview.ifBlank {
                    friend.lastActiveNote.ifBlank {
                        friend.friendCode.ifBlank { friend.handle }
                    }
                }
                Text(
                    text = preview,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (friend.unreadCount > 0) {
                Spacer(Modifier.width(6.dp))
                StatusPill(
                    text = "${friend.unreadCount}",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSendCheer,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("together_cheer_btn_${friend.userId}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Send cheer to ${friend.displayName}",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Send Cheer", maxLines = 1, softWrap = false)
            }
            OutlinedButton(
                onClick = onOpenChat,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("together_chat_btn_${friend.userId}")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Chat with ${friend.displayName}",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Chat", maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun SharedProgressRing(
    label: String,
    percent: Int,
    ringColor: Color,
    trackColor: Color
) {
    val tokens = LifeOsThemeTokensProvider.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(96.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(88.dp)) {
                val stroke = 9.dp.toPx()
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = (percent.coerceIn(0, 100) / 100f) * 360f,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Text(
                text = "$percent%",
                style = FeeloraTypeHelpers.statNumber.copy(fontFeatureSettings = "tnum"),
                color = tokens.inkPrimary
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tokens.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun mergeConnectedFriends(
    cloudFriends: List<TogetherFriendProfile>,
    localFriends: List<FriendEntity>
): List<TogetherFriendProfile> {
    if (localFriends.isEmpty()) return cloudFriends
    val result = cloudFriends.toMutableList()
    val seenCodes = cloudFriends.mapNotNull { it.friendCode.trim().uppercase().takeIf(String::isNotEmpty) }.toMutableSet()
    val seenHandles = cloudFriends.mapNotNull { it.handle.trim().lowercase().takeIf(String::isNotEmpty) }.toMutableSet()

    localFriends.forEach { entity ->
        val normCode = entity.friendCode.trim().uppercase()
        val normHandle = entity.handle.trim().lowercase()
        val alreadyListed = (normCode.isNotEmpty() && normCode in seenCodes) ||
            (normHandle.isNotEmpty() && normHandle in seenHandles)
        if (!alreadyListed) {
            if (normCode.isNotEmpty()) seenCodes.add(normCode)
            if (normHandle.isNotEmpty()) seenHandles.add(normHandle)
            result.add(
                TogetherFriendProfile(
                    userId = "local_${entity.friendCode.ifBlank { entity.handle.ifBlank { entity.id.toString() } }}",
                    displayName = entity.displayName,
                    handle = entity.handle,
                    friendCode = entity.friendCode,
                    shareXp = entity.shareXp,
                    shareStreaks = entity.shareStreaks,
                    shareFocus = entity.shareFocus,
                    shareChallenges = entity.shareChallenges,
                    level = entity.level,
                    xp = entity.xp,
                    dailyStreak = entity.streakDays,
                    focusMinutesToday = entity.focusMinutesToday,
                    challengesCompleted = entity.challengesCompleted,
                    presenceStatus = entity.statusText.ifBlank { "Connected · Ready" },
                    lastActiveNote = entity.statusText
                )
            )
        }
    }
    return result
}
