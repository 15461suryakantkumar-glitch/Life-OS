package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.social.TogetherFriendProfile
import com.example.data.social.TogetherMessage
import com.example.data.social.TogetherPrivacyFilter
import com.example.ui.components.FeeloraConfirmDialog
import com.example.ui.components.FeeloraScreenBackdrop
import com.example.ui.components.StatusPill
import com.example.ui.components.UserAvatarBadge
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.RoseAlert
import com.example.util.DateTimeUtils

@Composable
fun TogetherChatScreen(
    myUserId: String,
    friend: TogetherFriendProfile,
    messages: List<TogetherMessage>,
    lastMessageError: String? = null,
    failedOutboxCount: Int = 0,
    onRetryFailedMessages: () -> Unit = {},
    onBack: () -> Unit,
    onSendMessage: (content: String, mediaUri: Uri?) -> Unit,
    onBlockFriend: (block: Boolean) -> Unit,
    onReportUser: (reason: String) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    var draft by remember { mutableStateOf("") }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showOverflow by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    BackHandler(onBack = onBack)

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) attachedImageUri = uri
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    FeeloraScreenBackdrop(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("together_chat_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Chat Top Bar
            Surface(color = tokens.surfaceBase, tonalElevation = 1.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Together", tint = tokens.inkPrimary)
                    }
                    UserAvatarBadge(
                        preset = friend.avatarPreset,
                        photoUri = friend.avatarUrl,
                        displayName = friend.displayName,
                        size = 40.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(friend.displayName, style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${friend.handle} · Lv.${friend.level}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    Box {
                        IconButton(onClick = { showOverflow = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Chat options", tint = tokens.inkPrimary)
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text("Report user") },
                                leadingIcon = { Icon(Icons.Filled.ReportProblem, contentDescription = null, tint = RoseAlert) },
                                onClick = {
                                    showOverflow = false
                                    showReportDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Block user") },
                                leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null, tint = RoseAlert) },
                                onClick = {
                                    showOverflow = false
                                    confirmBlock = true
                                }
                            )
                        }
                    }
                }
            }

            if (lastMessageError != null || failedOutboxCount > 0) {
                com.example.ui.components.FeeloraStatusBanner(
                    message = lastMessageError ?: "$failedOutboxCount message(s) failed to send.",
                    tone = com.example.ui.components.FeeloraBannerTone.ERROR,
                    actionLabel = "Retry",
                    onAction = onRetryFailedMessages,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("together_chat_retry_banner")
                )
            }

            // Message Thread
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            text = "Start a private conversation with ${friend.displayName}. Messages sync in real time and queue safely if offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.inkSecondary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                items(messages, key = { it.clientMessageId.ifBlank { it.id } }) { msg ->
                    val isMine = msg.senderId == myUserId || msg.senderId.isBlank() || msg.senderId == "local_self"
                    val isFailed = msg.deliveryStatus == "FAILED"
                    val shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isMine) 18.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 18.dp
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .clip(shape)
                                .background(
                                    if (isMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                    else tokens.surfaceCard
                                )
                                .border(
                                    1.dp,
                                    when {
                                        isFailed -> RoseAlert.copy(alpha = 0.7f)
                                        isMine -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                        else -> tokens.borderSubtle
                                    },
                                    shape
                                )
                                .padding(12.dp)
                        ) {
                            if (!msg.mediaUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = msg.mediaUrl,
                                    contentDescription = "Shared image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(170.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                if (msg.content.isNotBlank()) Spacer(Modifier.height(6.dp))
                            }
                            if (msg.content.isNotBlank()) {
                                Text(msg.content, style = MaterialTheme.typography.bodyMedium, color = tokens.inkPrimary)
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = DateTimeUtils.formatLiveTime(msg.createdAtMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.inkMuted
                                )
                                if (isMine) {
                                    if (isFailed) {
                                        StatusPill(
                                            text = "Failed · Retry",
                                            color = RoseAlert,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { onRetryFailedMessages() }
                                                .testTag("chat_failed_retry_pill")
                                        )
                                    } else {
                                        StatusPill(
                                            text = msg.deliveryStatus.lowercase().replaceFirstChar { it.uppercase() },
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Attached image preview
            attachedImageUri?.let { uri ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tokens.surfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Selected photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Photo ready to send", style = MaterialTheme.typography.bodySmall, color = tokens.inkPrimary, modifier = Modifier.weight(1f))
                    IconButton(onClick = { attachedImageUri = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = RoseAlert)
                    }
                }
            }

            // Composer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.surfaceBase)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Attach photo", tint = MaterialTheme.colorScheme.secondary)
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Message ${friend.displayName}...") },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = tokens.borderSubtle,
                        focusedTextColor = tokens.inkPrimary,
                        unfocusedTextColor = tokens.inkPrimary
                    ),
                    modifier = Modifier.weight(1f).testTag("together_chat_input")
                )
                Spacer(Modifier.width(6.dp))
                FilledIconButton(
                    onClick = {
                        val trimmed = draft.trim()
                        if (trimmed.isNotEmpty() || attachedImageUri != null) {
                            onSendMessage(trimmed, attachedImageUri)
                            draft = ""
                            attachedImageUri = null
                        }
                    },
                    enabled = draft.isNotBlank() || attachedImageUri != null,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = tokens.background
                    ),
                    modifier = Modifier.size(48.dp).testTag("together_chat_send_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
                }
            }
        }
    }

    if (confirmBlock) {
        FeeloraConfirmDialog(
            title = "Block ${friend.displayName}?",
            message = "They will no longer be able to send you friend requests or messages.",
            confirmLabel = "Block",
            isDestructive = true,
            onConfirm = {
                confirmBlock = false
                onBlockFriend(true)
            },
            onDismiss = { confirmBlock = false }
        )
    }

    if (showReportDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            containerColor = tokens.surfaceCard,
            title = { Text("Report ${friend.displayName}", color = tokens.inkPrimary) },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = reason.isNotBlank(),
                    onClick = {
                        onReportUser(reason.trim())
                        showReportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseAlert)
                ) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = { showReportDialog = false }) { Text("Cancel") } }
        )
    }
}

fun conversationMessagesFor(
    myUserId: String,
    friendUserId: String,
    map: Map<String, List<TogetherMessage>>
): List<TogetherMessage> {
    val convId = TogetherPrivacyFilter.conversationIdFor(myUserId, friendUserId)
    return map[convId].orEmpty()
}
