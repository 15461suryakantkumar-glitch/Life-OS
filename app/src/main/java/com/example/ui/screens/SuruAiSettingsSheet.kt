package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.suru.SuruAiProvider
import com.example.suru.SuruVoiceLanguage
import com.example.ui.components.FeeloraToggleRow
import com.example.ui.components.ObsidianCard
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
    val currentLang = remember(prefs.suruVoiceLanguage) { SuruVoiceLanguage.fromStored(prefs.suruVoiceLanguage) }

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
            Text("SURU Voice Preferences", style = MaterialTheme.typography.headlineMedium, color = tokens.inkPrimary)
            Text(
                "Customize how SURU speaks and listens during your conversations.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.inkSecondary
            )

            FeeloraToggleRow(
                title = "Mute SURU Voice Responses",
                subtitle = "Keep SURU replies text-only unless you tap read aloud",
                checked = prefs.suruVoiceMuted,
                onCheckedChange = { muted ->
                    onUpdateVoice(muted, prefs.suruSpeechSpeed, currentLang)
                }
            )

            ObsidianCard {
                Text("Voice Language", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                Spacer(Modifier.height(8.dp))
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

            Spacer(Modifier.height(20.dp))
        }
    }
}
