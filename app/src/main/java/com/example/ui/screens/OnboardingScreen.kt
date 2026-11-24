package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CompanionSpecies
import com.example.data.local.PersonalityQuizEvaluator
import com.example.data.local.UserPreferencesState
import com.example.ui.components.AppointedCompanionRevealCard
import com.example.ui.components.ObsidianCard
import com.example.ui.components.PersonalityQuizQuestionCard
import com.example.ui.theme.LifeOsThemeMode
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.tokensForMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    existingPrefs: UserPreferencesState,
    onPreviewThemeMode: (LifeOsThemeMode) -> Unit = {},
    onCompleteSignIn: (
        name: String,
        identityOrEmail: String,
        petName: String,
        companionSpecies: CompanionSpecies,
        focusGoalMinutes: Int,
        themeMode: LifeOsThemeMode
    ) -> Unit
) {
    var step by rememberSaveable { mutableIntStateOf(0) } // 0 = Profile & Theme, 1 = Personality Quiz, 2 = Appointed Companion Reveal
    var name by rememberSaveable { mutableStateOf(existingPrefs.userName) }
    var identity by rememberSaveable {
        mutableStateOf(existingPrefs.userEmail.ifBlank { existingPrefs.userHandle })
    }
    var focusGoalMinutes by rememberSaveable { mutableIntStateOf(existingPrefs.dailyFocusGoalMinutes) }
    var selectedTheme by rememberSaveable { mutableStateOf(existingPrefs.themeMode) }
    var showNameError by rememberSaveable { mutableStateOf(false) }

    var q1 by rememberSaveable { mutableIntStateOf(0) }
    var q2 by rememberSaveable { mutableIntStateOf(0) }
    var q3 by rememberSaveable { mutableIntStateOf(0) }

    val appointedSpecies = remember(q1, q2, q3) {
        PersonalityQuizEvaluator.appointCompanion(q1, q2, q3)
    }
    var customPetName by rememberSaveable(appointedSpecies) { mutableStateOf(appointedSpecies.defaultName) }

    val tokens = tokensForMode(selectedTheme)
    val focusManager = LocalFocusManager.current

    fun submitAll() {
        if (name.isBlank()) {
            showNameError = true
            step = 0
            return
        }
        focusManager.clearFocus()
        onCompleteSignIn(
            name.trim(),
            identity.trim(),
            customPetName.trim().ifBlank { appointedSpecies.defaultName },
            appointedSpecies,
            focusGoalMinutes,
            selectedTheme
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tokens.primary.copy(alpha = if (tokens.isDark) 0.24f else 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.18f, size.height * 0.12f),
                        radius = size.maxDimension * 0.52f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tokens.secondary.copy(alpha = if (tokens.isDark) 0.16f else 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.78f),
                        radius = size.maxDimension * 0.48f
                    )
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .testTag("onboarding_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 540.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(listOf(tokens.primary, tokens.secondary)),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .background(tokens.surfaceBase),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.lifeos_neon_icon_1795447784366),
                    contentDescription = "LIFE OS Emblem",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = tokens.surfaceElevated,
                border = BorderStroke(1.dp, tokens.borderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = tokens.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when (step) {
                            0 -> "STEP 1 OF 2 · PROFILE & THEME"
                            1 -> "STEP 2 OF 2 · PERSONALITY MATCH"
                            else -> "YOUR APPOINTED COMPANION"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.inkSecondary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = when (step) {
                    0 -> "Welcome to Life OS"
                    1 -> "Meet Your Study Partner"
                    else -> "Say Hi to ${appointedSpecies.displayTitle}"
                },
                style = MaterialTheme.typography.displayMedium,
                color = tokens.inkPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (step) {
                    0 -> "Set your name, daily focus goal, and preferred look."
                    1 -> "Answer 3 quick questions so Life OS can match your ideal companion."
                    else -> "Appointed exclusively for your style at Level 1 · 0 XP."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.inkSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(18.dp))

            ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                when (step) {
                    0 -> {
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                if (showNameError && it.isNotBlank()) showNameError = false
                            },
                            label = { Text("Your Name") },
                            placeholder = { Text("Enter your name") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = if (showNameError) RoseAlert else tokens.secondary
                                )
                            },
                            isError = showNameError,
                            supportingText = if (showNameError) {
                                { Text("Please enter your name to continue.", color = RoseAlert) }
                            } else null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = tokens.secondary,
                                unfocusedBorderColor = tokens.borderSubtle,
                                focusedLabelColor = tokens.secondary,
                                unfocusedLabelColor = tokens.inkSecondary,
                                focusedTextColor = tokens.inkPrimary,
                                unfocusedTextColor = tokens.inkPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_name_input")
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = identity,
                            onValueChange = { identity = it },
                            label = { Text("Email or Handle (Optional)") },
                            placeholder = { Text("you@domain.com or @handle") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.AlternateEmail,
                                    contentDescription = null,
                                    tint = tokens.inkSecondary
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = tokens.primary,
                                unfocusedBorderColor = tokens.borderSubtle,
                                focusedLabelColor = tokens.primary,
                                unfocusedLabelColor = tokens.inkSecondary,
                                focusedTextColor = tokens.inkPrimary,
                                unfocusedTextColor = tokens.inkPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_identity_input")
                        )

                        Spacer(Modifier.height(14.dp))

                        Text("Daily Focus Target", style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(60, 90, 120, 180, 240).forEach { mins ->
                                val selected = focusGoalMinutes == mins
                                FilterChip(
                                    selected = selected,
                                    onClick = { focusGoalMinutes = mins },
                                    label = { Text("${mins}m / day", maxLines = 1, softWrap = false) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = tokens.primary.copy(alpha = 0.22f),
                                        selectedLabelColor = tokens.inkPrimary,
                                        containerColor = tokens.surfaceElevated,
                                        labelColor = tokens.inkSecondary
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Text("Workspace Theme", style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary)
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LifeOsThemeMode.entries.forEach { mode ->
                                val active = selectedTheme == mode
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(tokens.smallRadius))
                                        .background(if (active) mode.previewPrimary.copy(alpha = 0.16f) else tokens.surfaceElevated)
                                        .border(
                                            width = 1.dp,
                                            color = if (active) mode.previewPrimary else tokens.borderSubtle,
                                            shape = RoundedCornerShape(tokens.smallRadius)
                                        )
                                        .clickable {
                                            selectedTheme = mode
                                            onPreviewThemeMode(mode)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(mode.displayName, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                        Text(mode.subtitle, style = MaterialTheme.typography.labelSmall, color = tokens.inkSecondary)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Box(Modifier.size(14.dp).clip(CircleShape).background(mode.previewSurface).border(1.dp, tokens.borderSubtle, CircleShape))
                                        Box(Modifier.size(14.dp).clip(CircleShape).background(mode.previewPrimary))
                                        Box(Modifier.size(14.dp).clip(CircleShape).background(mode.previewSecondary))
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Button(
                            onClick = {
                                if (name.isBlank()) {
                                    showNameError = true
                                } else {
                                    focusManager.clearFocus()
                                    step = 1
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = tokens.primary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(tokens.smallRadius),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("onboarding_next_btn")
                        ) {
                            Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Continue to Personality Match", maxLines = 1, softWrap = false)
                        }
                    }

                    1 -> {
                        PersonalityQuizQuestionCard(0, q1) { q1 = it }
                        Spacer(Modifier.height(14.dp))
                        PersonalityQuizQuestionCard(1, q2) { q2 = it }
                        Spacer(Modifier.height(14.dp))
                        PersonalityQuizQuestionCard(2, q3) { q3 = it }
                        Spacer(Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { step = 0 },
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Back", maxLines = 1, softWrap = false)
                            }
                            Button(
                                onClick = {
                                    customPetName = appointedSpecies.defaultName
                                    step = 2
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = tokens.primary,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("onboarding_reveal_btn")
                            ) {
                                Text("Find My Companion", maxLines = 1, softWrap = false)
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    else -> {
                        AppointedCompanionRevealCard(
                            species = appointedSpecies,
                            petName = customPetName,
                            onPetNameChange = { customPetName = it }
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { step = 1 },
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.height(50.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Retake", maxLines = 1, softWrap = false)
                            }
                            Button(
                                onClick = { submitAll() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = tokens.primary,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("onboarding_submit_btn")
                            ) {
                                Text("Launch Life OS", maxLines = 1, softWrap = false)
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Your profile, tasks, and habits stay safely saved on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.inkMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}
