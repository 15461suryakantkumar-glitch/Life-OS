package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.suru.ProviderConnectionStatus
import com.example.suru.ProviderDiagnostic
import com.example.suru.SuruAiProvider
import com.example.suru.SuruVoiceLanguage
import com.example.ui.components.FeeloraToggleRow
import com.example.ui.components.ObsidianCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.RoseAlert
import com.example.ui.viewmodel.LifeOsUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuruAiSettingsSheet(
    state: LifeOsUiState,
    onDismiss: () -> Unit,
    onUpdateGateway: (
        defaultProvider: SuruAiProvider,
        defaultModel: String,
        autoRoutingEnabled: Boolean,
        secondaryReviewEnabled: Boolean,
        secondaryProvider: SuruAiProvider,
        secondaryModel: String,
        autoFallbackEnabled: Boolean,
        streamingEnabled: Boolean
    ) -> Unit,
    onSelectProviderModel: (SuruAiProvider, String) -> Unit = { _, _ -> },
    onTestConnection: (SuruAiProvider) -> Unit = {},
    onRefreshModels: (SuruAiProvider) -> Unit = {},
    onUpdateVoice: (muted: Boolean, speed: Float, lang: SuruVoiceLanguage) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val prefs = state.prefs
    var defaultProvider by remember(prefs.suruDefaultProvider) {
        mutableStateOf(SuruAiProvider.fromStored(prefs.suruDefaultProvider))
    }
    var defaultModel by remember(prefs.suruDefaultModel, prefs.suruProviderModelsRaw) {
        val perProvider = prefs.parsedProviderModels()[defaultProvider.name]
        mutableStateOf(perProvider ?: prefs.suruDefaultModel.takeIf { it.isNotBlank() } ?: defaultProvider.models.first())
    }
    var autoRouting by remember(prefs.suruAutoRoutingEnabled) { mutableStateOf(prefs.suruAutoRoutingEnabled) }
    var streamingEnabled by remember(prefs.suruStreamingEnabled) { mutableStateOf(prefs.suruStreamingEnabled) }
    var secondaryReview by remember(prefs.suruSecondaryReviewEnabled) { mutableStateOf(prefs.suruSecondaryReviewEnabled) }
    var secondaryProvider by remember(prefs.suruSecondaryProvider) {
        mutableStateOf(SuruAiProvider.fromStored(prefs.suruSecondaryProvider))
    }
    var secondaryModel by remember(prefs.suruSecondaryModel) {
        mutableStateOf(prefs.suruSecondaryModel.takeIf { it.isNotBlank() } ?: secondaryProvider.models.first())
    }
    var autoFallback by remember(prefs.suruAutoFallbackEnabled) { mutableStateOf(prefs.suruAutoFallbackEnabled) }
    val currentLang = remember(prefs.suruVoiceLanguage) { SuruVoiceLanguage.fromStored(prefs.suruVoiceLanguage) }

    val diagnostics = remember(state.aiProviderDiagnostics) {
        state.aiProviderDiagnostics.ifEmpty {
            SuruAiProvider.entries.map { provider ->
                ProviderDiagnostic(
                    provider = provider,
                    status = ProviderConnectionStatus.NOT_CONFIGURED,
                    detail = "Add ${provider.envVarName} or SURU_AI_PROXY_URL in AI Studio Secrets."
                )
            }
        }
    }

    fun pushUpdate(
        targetProvider: SuruAiProvider = defaultProvider,
        targetModel: String = defaultModel,
        targetRouting: Boolean = autoRouting,
        targetSecondaryReview: Boolean = secondaryReview,
        targetSecondaryProvider: SuruAiProvider = secondaryProvider,
        targetSecondaryModel: String = secondaryModel,
        targetFallback: Boolean = autoFallback,
        targetStreaming: Boolean = streamingEnabled
    ) {
        onUpdateGateway(
            targetProvider,
            targetModel,
            targetRouting,
            targetSecondaryReview,
            targetSecondaryProvider,
            targetSecondaryModel,
            targetFallback,
            targetStreaming
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.surfaceBase
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("suru_ai_settings_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "SURU Multi-Provider AI Gateway",
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.inkPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Google Gemini starts as primary. Switching primary providers keeps all conversation history intact. \"Connected\" only appears after a live API request succeeds.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.inkSecondary
            )

            // 5 Independent Provider Cards
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                diagnostics.forEach { diag ->
                    val isPrimary = defaultProvider == diag.provider
                    val statusColor = when (diag.status) {
                        ProviderConnectionStatus.CONNECTED -> EmeraldSuccess
                        ProviderConnectionStatus.CONNECTION_FAILED -> RoseAlert
                        ProviderConnectionStatus.TESTING -> MaterialTheme.colorScheme.primary
                        ProviderConnectionStatus.CONFIGURED_UNTESTED,
                        ProviderConnectionStatus.NOT_CONFIGURED -> AmberWarning
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(tokens.cardRadius))
                            .background(
                                if (isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else tokens.surfaceElevated
                            )
                            .border(
                                width = if (isPrimary) 1.5.dp else 1.dp,
                                color = if (isPrimary) MaterialTheme.colorScheme.primary else tokens.borderSubtle,
                                shape = RoundedCornerShape(tokens.cardRadius)
                            )
                            .padding(14.dp)
                            .testTag("provider_card_${diag.provider.name}"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Top row: Provider Name + Primary badge + Connection Status badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = diag.provider.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = tokens.inkPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isPrimary) {
                                        StatusPill(
                                            text = "Primary",
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VpnKey,
                                        contentDescription = null,
                                        tint = if (diag.status.isReady) EmeraldSuccess else AmberWarning,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = diag.apiConfigLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.inkSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            StatusPill(
                                text = diag.status.label,
                                color = statusColor,
                                icon = if (diag.status == ProviderConnectionStatus.CONNECTED) Icons.Filled.CheckCircle else null
                            )
                        }

                        // Selected Model + Dynamic Model Picker
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Selected model: ${diag.selectedModel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.inkPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (diag.status.isReady) {
                                IconButton(
                                    onClick = { onRefreshModels(diag.provider) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("refresh_models_${diag.provider.name}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = "Refresh ${diag.provider.shortName} models",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            diag.availableModels.forEach { modelId ->
                                FilterChip(
                                    selected = diag.selectedModel == modelId,
                                    onClick = {
                                        onSelectProviderModel(diag.provider, modelId)
                                        if (isPrimary) {
                                            defaultModel = modelId
                                        }
                                    },
                                    label = { Text(modelId, maxLines = 1, softWrap = false) }
                                )
                            }
                        }

                        // Last Successful Connection Timestamp (when available)
                        diag.lastConnectedAtMillis?.let { ts ->
                            Text(
                                text = "Last connected: ${formatTimestamp(ts)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess,
                                modifier = Modifier.testTag("last_connected_${diag.provider.name}")
                            )
                        }

                        // Error Details Box (when applicable)
                        if (diag.lastErrorMessage != null && diag.status == ProviderConnectionStatus.CONNECTION_FAILED) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(tokens.smallRadius))
                                    .background(RoseAlert.copy(alpha = 0.12f))
                                    .border(1.dp, RoseAlert.copy(alpha = 0.45f), RoundedCornerShape(tokens.smallRadius))
                                    .padding(10.dp)
                                    .testTag("error_detail_${diag.provider.name}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = "Connection error",
                                    tint = RoseAlert,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = diag.lastErrorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.inkPrimary
                                )
                            }
                        } else if (diag.status == ProviderConnectionStatus.NOT_CONFIGURED) {
                            Text(
                                text = diag.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkSecondary
                            )
                        }

                        // Actions Row: Set Primary & Test Connection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    defaultProvider = diag.provider
                                    defaultModel = diag.selectedModel
                                    pushUpdate(
                                        targetProvider = diag.provider,
                                        targetModel = diag.selectedModel
                                    )
                                },
                                enabled = !isPrimary,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("select_primary_${diag.provider.name}")
                            ) {
                                Text(if (isPrimary) "Active Primary" else "Use as Primary", maxLines = 1, softWrap = false)
                            }
                            OutlinedButton(
                                onClick = { onTestConnection(diag.provider) },
                                enabled = !diag.isTesting,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("test_connection_${diag.provider.name}")
                            ) {
                                if (diag.isTesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Testing...", maxLines = 1, softWrap = false)
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.NetworkCheck,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Test Connection", maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }

            // Streaming, Smart Routing & Fallback
            FeeloraToggleRow(
                title = "Streaming Responses",
                subtitle = "Streams live tokens into SURU chat as they arrive",
                checked = streamingEnabled,
                onCheckedChange = {
                    streamingEnabled = it
                    pushUpdate(targetStreaming = it)
                }
            )
            FeeloraToggleRow(
                title = "Automatic Task Routing",
                subtitle = "Routes vision to multimodal models and deep planning to reasoning models",
                checked = autoRouting,
                onCheckedChange = {
                    autoRouting = it
                    pushUpdate(targetRouting = it)
                }
            )
            FeeloraToggleRow(
                title = "Automatic Provider Fallback",
                subtitle = "Switches to next configured provider (with notice) if primary fails",
                checked = autoFallback,
                onCheckedChange = {
                    autoFallback = it
                    pushUpdate(targetFallback = it)
                }
            )
            FeeloraToggleRow(
                title = "Secondary Model Verification",
                subtitle = "Optionally cross-checks Study Lab outputs with a second configured model",
                checked = secondaryReview,
                onCheckedChange = {
                    secondaryReview = it
                    pushUpdate(targetSecondaryReview = it)
                }
            )

            if (secondaryReview) {
                ObsidianCard {
                    Text("Verification Provider & Model", style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuruAiProvider.entries.forEach { prov ->
                            FilterChip(
                                selected = secondaryProvider == prov,
                                onClick = {
                                    val firstModel = diagnostics.firstOrNull { it.provider == prov }?.availableModels?.firstOrNull()
                                        ?: prov.models.first()
                                    secondaryProvider = prov
                                    secondaryModel = firstModel
                                    pushUpdate(
                                        targetSecondaryProvider = prov,
                                        targetSecondaryModel = firstModel
                                    )
                                },
                                label = { Text(prov.shortName, maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    val secModels = diagnostics.firstOrNull { it.provider == secondaryProvider }?.availableModels
                        ?: secondaryProvider.models
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        secModels.forEach { m ->
                            FilterChip(
                                selected = secondaryModel == m,
                                onClick = {
                                    secondaryModel = m
                                    pushUpdate(targetSecondaryModel = m)
                                },
                                label = { Text(m, maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                }
            }

            // Voice Quick Controls
            ObsidianCard {
                Text("SURU Voice Preferences", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuruVoiceLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = currentLang == lang,
                            onClick = { onUpdateVoice(prefs.suruVoiceMuted, prefs.suruSpeechSpeed, lang) },
                            label = { Text(lang.label, maxLines = 1, softWrap = false) }
                        )
                    }
                }
            }

            if (state.aiProviderUsageLogs.isNotEmpty()) {
                ObsidianCard {
                    Text("Recent Provider Activity", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                    Spacer(Modifier.height(6.dp))
                    state.aiProviderUsageLogs.take(5).forEach { log ->
                        Text(
                            text = "${log.providerName} · ${log.modelId} · ${log.latencyMs}ms · ${if (log.success) "OK" else "Failed"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (log.success) EmeraldSuccess else AmberWarning
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    return runCatching {
        SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(millis))
    }.getOrDefault(millis.toString())
}
