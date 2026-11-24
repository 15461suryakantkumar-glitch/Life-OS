package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
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
    onDismissError: () -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val quickPrompts = listOf(
        "Add Review Physics Notes tomorrow",
        "Schedule Deep Work at 9 AM for 60m",
        "Add habit Hydrate 2L water",
        "Suggest my daily plan",
        "Show my productivity insights"
    )

    LaunchedEffect(state.aiMessages.size) {
        if (state.aiMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.aiMessages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
            .testTag("life_ai_screen")
    ) {
        // AI Assistant Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.surfaceBase)
                .border(0.5.dp, tokens.borderSubtle)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_ai_core_1795431261833),
                contentDescription = "LIFE AI Assistant",
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.secondary, CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("LIFE AI", style = MaterialTheme.typography.titleLarge, color = tokens.inkPrimary)
                Text(
                    text = if (state.isCloudAiAvailable) "On-device + Cloud AI ready" else "On-device engine active (offline ₹0)",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusPill(
                text = if (state.isCloudAiAvailable) "Hybrid" else "Local Ready",
                color = EmeraldSuccess,
                icon = Icons.Filled.AutoAwesome
            )
        }

        // Quick Prompt Suggestions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickPrompts.forEach { sample ->
                AssistChip(
                    onClick = { onSendPrompt(sample) },
                    label = { Text(sample, maxLines = 1, softWrap = false) },
                    enabled = !state.isAiLoading
                )
            }
        }

        // Real Error Banner if AI call fails
        if (state.aiErrorMessage != null) {
            ObsidianCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                borderColor = RoseAlert
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = RoseAlert)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = state.aiErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismissError) { Text("Dismiss") }
                }
            }
        }

        // Chat Timeline or Clean Welcome Hero
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.aiMessages.isEmpty()) {
                item {
                    GradientGlowCard(modifier = Modifier.testTag("ai_empty_hero")) {
                        Text(
                            text = "Ask LIFE AI, ${state.prefs.userName.ifBlank { "Friend" }}",
                            style = MaterialTheme.typography.titleLarge,
                            color = tokens.inkPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Instant offline task creation, habit setup, calendar scheduling, daily planning, and productivity insights — ready with zero extra setup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.inkSecondary
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            quickPrompts.take(3).forEach { cmd ->
                                OutlinedButton(
                                    onClick = { onSendPrompt(cmd) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
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
                                .background(
                                    if (msg.isFromUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    else tokens.surfaceCard
                                )
                                .border(
                                    1.dp,
                                    if (msg.isFromUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else tokens.borderSubtle,
                                    bubbleShape
                                )
                                .padding(14.dp)
                        ) {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.inkPrimary
                            )
                            if (!msg.actionBadge.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                StatusPill(
                                    text = msg.actionBadge,
                                    color = EmeraldSuccess,
                                    icon = Icons.Filled.CheckCircle
                                )
                            }
                        }
                    }
                }
            }

            if (state.isAiLoading) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("LIFE AI is thinking...", style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                    }
                }
            }
        }

        // Message Composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.surfaceBase)
                .border(0.5.dp, tokens.borderSubtle)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f).testTag("ai_prompt_input"),
                placeholder = { Text("Ask LIFE AI to add tasks, schedule, or analyze...") },
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = tokens.surfaceCard,
                    unfocusedContainerColor = tokens.surfaceCard,
                    focusedBorderColor = MaterialTheme.colorScheme.secondary,
                    unfocusedBorderColor = tokens.borderSubtle,
                    focusedTextColor = tokens.inkPrimary,
                    unfocusedTextColor = tokens.inkPrimary
                )
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    val toSend = input.trim()
                    if (toSend.isNotEmpty()) {
                        input = ""
                        onSendPrompt(toSend)
                    }
                },
                enabled = input.isNotBlank() && !state.isAiLoading,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                modifier = Modifier.size(48.dp).testTag("ai_send_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send prompt")
            }
        }
    }
}
