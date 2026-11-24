package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.example.suru.SuruAiProvider
import com.example.suru.SuruVoiceLanguage
import com.example.ui.components.FeeloraToggleRow
import com.example.ui.components.ObsidianCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.viewmodel.LifeOsUiState

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
        autoFallbackEnabled: Boolean
    ) -> Unit,
    onUpdateVoice: (muted: Boolean, speed: Float, lang: SuruVoiceLanguage) -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val prefs = state.prefs
    var defaultProvider by remember(prefs.suruDefaultProvider) { mutableStateOf(SuruAiProvider.fromStored(prefs.suruDefaultProvider)) }
    var defaultModel by remember(prefs.suruDefaultModel) {
        mutableStateOf(prefs.suruDefaultModel.takeIf { it in defaultProvider.models } ?: defaultProvider.models.first())
    }
    var autoRouting by remember(prefs.suruAutoRoutingEnabled) { mutableStateOf(prefs.suruAutoRoutingEnabled) }
    var secondaryReview by remember(prefs.suruSecondaryReviewEnabled) { mutableStateOf(prefs.suruSecondaryReviewEnabled) }
    var secondaryProvider by remember(prefs.suruSecondaryProvider) { mutableStateOf(SuruAiProvider.fromStored(prefs.suruSecondaryProvider)) }
    var secondaryModel by remember(prefs.suruSecondaryModel) {
        mutableStateOf(prefs.suruSecondaryModel.takeIf { it in secondaryProvider.models } ?: secondaryProvider.models.first())
    }
    var autoFallback by remember(prefs.suruAutoFallbackEnabled) { mutableStateOf(prefs.suruAutoFallbackEnabled) }
    val currentLang = remember(prefs.suruVoiceLanguage) { SuruVoiceLanguage.fromStored(prefs.suruVoiceLanguage) }

    fun pushUpdate() {
        onUpdateGateway(
            defaultProvider,
            defaultModel,
            autoRouting,
            secondaryReview,
            secondaryProvider,
            secondaryModel,
            autoFallback
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceBase
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("suru_ai_settings_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("SURU Multi-Provider AI Gateway", style = MaterialTheme.typography.headlineMedium, color = tokens.inkPrimary)
            Text(
                "Providers marked Configuration Required need their key or SURU_AI_PROXY_URL set in AI Studio Secrets.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.inkSecondary
            )

            // Provider Diagnostic List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.aiProviderDiagnostics.forEach { diag ->
                    val selected = defaultProvider == diag.provider
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(tokens.smallRadius))
                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else tokens.surfaceElevated)
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else tokens.borderSubtle,
                                shape = RoundedCornerShape(tokens.smallRadius)
                            )
                            .clickable {
                                defaultProvider = diag.provider
                                defaultModel = diag.provider.models.first()
                                pushUpdate()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(diag.provider.displayName, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary, fontWeight = FontWeight.SemiBold)
                            Text(diag.detail, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                        }
                        Spacer(Modifier.width(8.dp))
                        StatusPill(
                            text = diag.status.label,
                            color = if (diag.status.isReady) EmeraldSuccess else AmberWarning
                        )
                    }
                }
            }

            // Primary Model Selector
            ObsidianCard {
                Text("Primary Model (${defaultProvider.shortName})", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    defaultProvider.models.forEach { model ->
                        FilterChip(
                            selected = defaultModel == model,
                            onClick = {
                                defaultModel = model
                                pushUpdate()
                            },
                            label = { Text(model, maxLines = 1, softWrap = false) }
                        )
                    }
                }
            }

            // Smart Routing & Fallback
            FeeloraToggleRow(
                title = "Automatic Task Routing",
                subtitle = "Routes vision to multimodal models and deep planning to reasoning models",
                checked = autoRouting,
                onCheckedChange = {
                    autoRouting = it
                    pushUpdate()
                }
            )
            FeeloraToggleRow(
                title = "Automatic Provider Fallback",
                subtitle = "Retries next configured provider if primary times out or errors",
                checked = autoFallback,
                onCheckedChange = {
                    autoFallback = it
                    pushUpdate()
                }
            )
            FeeloraToggleRow(
                title = "Secondary Model Verification",
                subtitle = "Optionally cross-checks Study Lab outputs with a second configured model",
                checked = secondaryReview,
                onCheckedChange = {
                    secondaryReview = it
                    pushUpdate()
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
                                    secondaryProvider = prov
                                    secondaryModel = prov.models.first()
                                    pushUpdate()
                                },
                                label = { Text(prov.shortName, maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        secondaryProvider.models.forEach { m ->
                            FilterChip(
                                selected = secondaryModel == m,
                                onClick = {
                                    secondaryModel = m
                                    pushUpdate()
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
                            text = "${log.providerName} · ${log.modelId} · ${log.latencyMs}ms · ${if (log.success) "OK" else "Fallback"}",
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
