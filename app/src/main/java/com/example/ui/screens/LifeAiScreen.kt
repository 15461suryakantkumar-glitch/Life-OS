package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.audio.LifeOsSoundCue
import com.example.data.local.SuruMemoryEntity
import com.example.data.local.SuruStudyPlanEntity
import com.example.suru.SuruBriefingsPanel
import com.example.suru.SuruConfirmationCard
import com.example.suru.SuruImageProcessor
import com.example.suru.SuruMascot
import com.example.suru.SuruMemoryPanel
import com.example.suru.SuruStudyHelpMode
import com.example.suru.SuruStudyLabPanel
import com.example.suru.SuruVoiceLanguage
import com.example.suru.SuruVoiceState
import com.example.ui.components.FeeloraScreenBackdrop
import com.example.ui.components.GradientGlowCard
import com.example.ui.components.ObsidianCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.LifeOsUiState

@Composable
fun LifeAiScreen(
    state: LifeOsUiState,
    onSendPrompt: (String) -> Unit,
    onDismissError: () -> Unit,
    onStartVoice: () -> Unit = {},
    onStopVoice: () -> Unit = {},
    onSpeakText: (String) -> Unit = {},
    onStopSpeaking: () -> Unit = {},
    onClearVoiceError: () -> Unit = {},
    onUpdateVoiceSettings: (Boolean, Float, SuruVoiceLanguage) -> Unit = { _, _, _ -> },
    onAdjustStudyPlanMinutes: (Int) -> Unit = {},
    onConfirmPendingAction: () -> Unit = {},
    onDismissPendingAction: () -> Unit = {},
    onUndoLastAction: () -> Unit = {},
    onGenerateStudyHelp: (String, SuruStudyHelpMode, String) -> Unit = { _, _, _ -> },
    onTriggerBacklogRecovery: () -> Unit = {},
    onUpdateStudyPlanStatus: (SuruStudyPlanEntity, String) -> Unit = { _, _ -> },
    onTogglePersonalization: (Boolean) -> Unit = {},
    onAddMemory: (String, String, String) -> Unit = { _, _, _ -> },
    onToggleMemory: (SuruMemoryEntity) -> Unit = {},
    onDeleteMemory: (SuruMemoryEntity) -> Unit = {},
    onAttachImageUri: (Uri, String) -> Unit = { _, _ -> },
    onClearImage: () -> Unit = {},
    onPlayCue: (LifeOsSoundCue) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onOpenAiSettings: () -> Unit = {}
) {
    val tokens = LifeOsThemeTokensProvider.current
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var activeTab by remember { mutableIntStateOf(0) } // 0=Assistant, 1=Study Lab, 2=Briefings, 3=Memory
    var showAttachMenu by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val listState = rememberLazyListState()
    val voiceLanguage = remember(state.prefs.suruVoiceLanguage) {
        SuruVoiceLanguage.fromStored(state.prefs.suruVoiceLanguage)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onAttachImageUri(uri, "Photo")
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val captureUri = pendingCameraUri
        pendingCameraUri = null
        if (saved && captureUri != null) onAttachImageUri(captureUri, "Camera Photo")
    }

    fun launchCameraCapture() {
        runCatching {
            val uri = SuruImageProcessor.createCameraCaptureUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }.onFailure {
            pendingCameraUri = null
            onPlayCue(LifeOsSoundCue.SURU_ERROR)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCameraCapture() else onPlayCue(LifeOsSoundCue.SURU_ERROR)
    }

    fun handleCameraTap() {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            onPlayCue(LifeOsSoundCue.SURU_ERROR)
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) launchCameraCapture() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartVoice() else onClearVoiceError()
    }

    fun handleMicTap() {
        when (state.suruVoiceState) {
            SuruVoiceState.LISTENING -> onStopVoice()
            SuruVoiceState.SPEAKING -> onStopSpeaking()
            else -> {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) onStartVoice() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val quickPrompts = listOf(
        "Suru, mujhe agle 5 din mein Physics ke 3 chapters complete karne hain",
        "Plan my entire week",
        "Move today's tasks to tomorrow",
        "Summarize my progress this month",
        "What should I focus on today?"
    )

    LaunchedEffect(state.aiMessages.size) {
        if (activeTab == 0 && state.aiMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.aiMessages.lastIndex)
        }
    }

    FeeloraScreenBackdrop(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("life_ai_screen")
        ) {
            // SURU Character + Gateway + Voice Control Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.surfaceBase)
                    .border(0.5.dp, tokens.borderSubtle)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = tokens.inkPrimary)
                    }
                    SuruMascot(
                        state = state.suruMascotState,
                        size = 56.dp,
                        onClick = { if (state.suruVoiceState == SuruVoiceState.SPEAKING) onStopSpeaking() }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("SURU", style = MaterialTheme.typography.titleLarge, color = tokens.inkPrimary, fontWeight = FontWeight.Bold)
                            if (state.suruVoiceState != SuruVoiceState.READY && state.suruVoiceState != SuruVoiceState.FAILED) {
                                StatusPill(
                                    text = state.suruVoiceState.label,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = "FEELORA Companion",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onOpenAiSettings,
                        modifier = Modifier.size(44.dp).testTag("suru_gateway_settings_btn")
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = "SURU Voice Preferences", tint = MaterialTheme.colorScheme.secondary)
                    }

                    IconButton(
                        onClick = {
                            if (state.suruVoiceState == SuruVoiceState.SPEAKING) onStopSpeaking()
                            else onUpdateVoiceSettings(!state.prefs.suruVoiceMuted, state.prefs.suruSpeechSpeed, voiceLanguage)
                        },
                        modifier = Modifier.size(44.dp).testTag("suru_mute_btn")
                    ) {
                        Icon(
                            imageVector = if (state.prefs.suruVoiceMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (state.prefs.suruVoiceMuted) "Unmute SURU voice" else "Mute SURU voice",
                            tint = if (state.prefs.suruVoiceMuted) tokens.inkMuted else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        0 to "Assistant",
                        1 to "Study Lab",
                        2 to "Briefings",
                        3 to "Memory (${state.suruMemories.size})"
                    ).forEach { (idx, label) ->
                        FilterChip(
                            selected = activeTab == idx,
                            onClick = { activeTab = idx },
                            label = { Text(label, maxLines = 1, softWrap = false) },
                            modifier = Modifier.testTag("suru_tab_$idx")
                        )
                    }
                }
            }

            // Undo Bar
            state.suruLastUndo?.let { undo ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(tokens.smallRadius),
                    color = tokens.surfaceElevated
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(undo.label, style = MaterialTheme.typography.bodySmall, color = tokens.inkPrimary, modifier = Modifier.weight(1f))
                        TextButton(onClick = onUndoLastAction, modifier = Modifier.testTag("suru_undo_btn")) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Undo")
                        }
                    }
                }
            }

            when (activeTab) {
                1 -> Box(Modifier.weight(1f)) {
                    SuruStudyLabPanel(activeHelp = state.suruActiveStudyHelp, isLoading = state.isAiLoading, onGenerateHelp = onGenerateStudyHelp)
                }
                2 -> Box(Modifier.weight(1f)) {
                    SuruBriefingsPanel(state = state, onTriggerBacklogRecovery = { onTriggerBacklogRecovery(); activeTab = 0 }, onUpdatePlanStatus = onUpdateStudyPlanStatus)
                }
                3 -> Box(Modifier.weight(1f)) {
                    SuruMemoryPanel(
                        personalizationEnabled = state.prefs.suruPersonalizationEnabled,
                        memories = state.suruMemories,
                        onTogglePersonalization = onTogglePersonalization,
                        onAddMemory = onAddMemory,
                        onToggleMemory = onToggleMemory,
                        onDeleteMemory = onDeleteMemory
                    )
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickPrompts.forEach { sample ->
                            AssistChip(onClick = { onSendPrompt(sample) }, label = { Text(sample, maxLines = 1, softWrap = false) }, enabled = !state.isAiLoading)
                        }
                    }

                    if (state.suruPartialTranscript.isNotBlank() && state.suruVoiceState == SuruVoiceState.LISTENING) {
                        ObsidianCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), borderColor = MaterialTheme.colorScheme.primary) {
                            Text("Hearing: \"${state.suruPartialTranscript}\"", style = MaterialTheme.typography.bodySmall, color = tokens.inkPrimary)
                        }
                    }

                    val activeVoiceError = state.suruVoiceError
                    if (activeVoiceError != null) {
                        ObsidianCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), borderColor = RoseAlert) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = RoseAlert)
                                Spacer(Modifier.width(8.dp))
                                Text(activeVoiceError, style = MaterialTheme.typography.bodySmall, color = tokens.inkPrimary, modifier = Modifier.weight(1f))
                                TextButton(onClick = { onClearVoiceError(); onDismissError() }) { Text("Dismiss") }
                            }
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.aiMessages.isEmpty()) {
                            item {
                                GradientGlowCard(modifier = Modifier.testTag("ai_empty_hero")) {
                                    Text("Hi ${state.prefs.userName.ifBlank { "Friend" }}, I'm SURU", style = MaterialTheme.typography.titleLarge, color = tokens.inkPrimary)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Speak or type naturally in English, Hindi, or Hinglish to plan your day, organize tasks, or study calmly.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.inkSecondary
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        quickPrompts.take(3).forEach { cmd ->
                                            OutlinedButton(onClick = { onSendPrompt(cmd) }, modifier = Modifier.fillMaxWidth()) {
                                                Text(cmd, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            items(state.aiMessages, key = { it.id }) { msg ->
                                val align = if (msg.isFromUser) Alignment.CenterEnd else Alignment.CenterStart
                                val bubbleShape = RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = if (msg.isFromUser) 18.dp else 4.dp,
                                    bottomEnd = if (msg.isFromUser) 4.dp else 18.dp
                                )
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = align) {
                                    Column(
                                        modifier = Modifier
                                            .widthIn(max = 320.dp)
                                            .clip(bubbleShape)
                                            .background(if (msg.isFromUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else tokens.surfaceCard)
                                            .border(1.dp, if (msg.isFromUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else tokens.borderSubtle, bubbleShape)
                                            .padding(14.dp)
                                    ) {
                                        if (!msg.attachmentSummary.isNullOrBlank()) {
                                            StatusPill(text = msg.attachmentSummary, color = MaterialTheme.colorScheme.secondary, icon = Icons.Filled.PhotoLibrary)
                                            Spacer(Modifier.height(6.dp))
                                        }
                                        Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = tokens.inkPrimary)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            if (!msg.actionBadge.isNullOrBlank()) {
                                                StatusPill(text = msg.actionBadge, color = EmeraldSuccess, icon = Icons.Filled.CheckCircle)
                                            } else Spacer(Modifier.width(1.dp))
                                            if (!msg.isFromUser) {
                                                IconButton(onClick = { onSpeakText(msg.text) }, modifier = Modifier.size(32.dp)) {
                                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        state.suruPendingConfirmation?.let { pendingReq ->
                            item {
                                SuruConfirmationCard(
                                    request = pendingReq,
                                    onAdjustDailyMinutes = onAdjustStudyPlanMinutes,
                                    onConfirm = onConfirmPendingAction,
                                    onDismiss = onDismissPendingAction
                                )
                            }
                        }

                        if (state.isAiLoading) {
                            val streamingText = state.aiStreamingReply
                            if (!streamingText.isNullOrBlank()) {
                                item {
                                    val streamShape = RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = 4.dp,
                                        bottomEnd = 18.dp
                                    )
                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                        Column(
                                            modifier = Modifier
                                                .widthIn(max = 320.dp)
                                                .clip(streamShape)
                                                .background(tokens.surfaceCard)
                                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), streamShape)
                                                .padding(14.dp)
                                                .testTag("ai_streaming_bubble")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                state.aiStreamingProviderLabel?.let { label ->
                                                    StatusPill(text = label, color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                            Spacer(Modifier.height(6.dp))
                                            Text(streamingText, style = MaterialTheme.typography.bodyMedium, color = tokens.inkPrimary)
                                        }
                                    }
                                }
                            } else {
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(10.dp))
                                        Text("SURU is thinking...", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                                    }
                                }
                            }
                        }
                    }

                    state.pendingSuruImage?.let { img ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(tokens.surfaceElevated)
                                .border(0.5.dp, tokens.borderSubtle)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("suru_image_preview_strip")
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    bitmap = img.toComposeImageBitmap(),
                                    contentDescription = "Attached study photo preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Photo ready for SURU", style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary)
                                    Text(img.summaryLabel, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = onClearImage, modifier = Modifier.size(48.dp).testTag("suru_remove_image_btn")) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove attached photo", tint = RoseAlert)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Solve step-by-step", "Summarize notes", "Make study tasks from this").forEach { chipPrompt ->
                                    AssistChip(onClick = { onSendPrompt(chipPrompt) }, label = { Text(chipPrompt, maxLines = 1, softWrap = false) }, enabled = !state.isAiLoading)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(tokens.surfaceBase)
                            .border(0.5.dp, tokens.borderSubtle)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isListening = state.suruVoiceState == SuruVoiceState.LISTENING
                        FilledIconButton(
                            onClick = { handleMicTap() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isListening) RoseAlert else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (isListening) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.size(48.dp).testTag("suru_mic_btn")
                        ) {
                            Icon(if (isListening) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = if (isListening) "Stop listening" else "Speak to SURU")
                        }
                        Spacer(Modifier.width(6.dp))
                        Box {
                            FilledIconButton(
                                onClick = { showAttachMenu = true },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (state.pendingSuruImage != null) MaterialTheme.colorScheme.secondaryContainer else tokens.surfaceElevated,
                                    contentColor = if (state.pendingSuruImage != null) MaterialTheme.colorScheme.onSecondaryContainer else tokens.inkPrimary
                                ),
                                modifier = Modifier.size(48.dp).testTag("suru_attach_btn")
                            ) {
                                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Attach photo or camera image")
                            }
                            DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Choose from Photos") },
                                    leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                                    onClick = {
                                        showAttachMenu = false
                                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    modifier = Modifier.testTag("suru_pick_photo_item")
                                )
                                DropdownMenuItem(
                                    text = { Text("Take Photo") },
                                    leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                                    onClick = {
                                        showAttachMenu = false
                                        handleCameraTap()
                                    },
                                    modifier = Modifier.testTag("suru_take_photo_item")
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f).testTag("ai_prompt_input"),
                            placeholder = { Text(if (state.pendingSuruImage != null) "Ask about this photo..." else "Ask SURU (English / Hinglish)...") },
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = tokens.surfaceCard,
                                unfocusedContainerColor = tokens.surfaceCard,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = tokens.borderSubtle,
                                focusedTextColor = tokens.inkPrimary,
                                unfocusedTextColor = tokens.inkPrimary
                            )
                        )
                        Spacer(Modifier.width(6.dp))
                        FilledIconButton(
                            onClick = {
                                val toSend = input.trim()
                                if (toSend.isNotEmpty() || state.pendingSuruImage != null) {
                                    input = ""
                                    onSendPrompt(toSend)
                                }
                            },
                            enabled = (input.isNotBlank() || state.pendingSuruImage != null) && !state.isAiLoading,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.size(48.dp).testTag("ai_send_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send prompt")
                        }
                    }
                }
            }
        }
    }
}
