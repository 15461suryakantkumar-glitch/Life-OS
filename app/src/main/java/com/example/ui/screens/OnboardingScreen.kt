package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.example.data.local.UserPreferencesState
import com.example.ui.components.ObsidianCard
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.ObsidianBase
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.ThemeAccentPreset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    existingPrefs: UserPreferencesState,
    onCompleteSignIn: (
        name: String,
        identityOrEmail: String,
        petName: String,
        focusGoalMinutes: Int,
        accentPreset: ThemeAccentPreset
    ) -> Unit
) {
    val initialName = if (existingPrefs.userName == "Alex Mercer" && !existingPrefs.isSignedIn) {
        ""
    } else {
        existingPrefs.userName
    }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var identity by rememberSaveable {
        mutableStateOf(
            existingPrefs.userEmail.ifBlank {
                if (existingPrefs.userHandle == "@alex.os" && !existingPrefs.isSignedIn) "" else existingPrefs.userHandle
            }
        )
    }
    var petName by rememberSaveable { mutableStateOf(existingPrefs.petName.ifBlank { "NOVA" }) }
    var focusGoalMinutes by rememberSaveable { mutableIntStateOf(existingPrefs.dailyFocusGoalMinutes) }
    var selectedPreset by rememberSaveable { mutableStateOf(existingPrefs.accentPreset) }
    var showNameError by rememberSaveable { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    fun submitForm() {
        if (name.isBlank()) {
            showNameError = true
            return
        }
        focusManager.clearFocus()
        onCompleteSignIn(
            name.trim(),
            identity.trim(),
            petName.trim(),
            focusGoalMinutes,
            selectedPreset
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            selectedPreset.primary.copy(alpha = 0.24f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.18f, size.height * 0.12f),
                        radius = size.maxDimension * 0.52f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            selectedPreset.secondary.copy(alpha = 0.16f),
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
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Neon Emblem Header
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(listOf(ElectricCyan, ElectricViolet)),
                        shape = RoundedCornerShape(26.dp)
                    )
                    .background(ObsidianBase),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.lifeos_neon_icon_1795447784366),
                    contentDescription = "LIFE OS Emblem",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = CharcoalElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = selectedPreset.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "LOCAL-FIRST · OFFLINE-READY COMMAND CENTER",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSecondary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Initialize LIFE OS",
                style = MaterialTheme.typography.displayMedium,
                color = InkPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Sign in locally and personalize your workspace, companion, and daily focus profile.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(22.dp))

            // Sign-In & Name Entry Card
            ObsidianCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LockOpen,
                        contentDescription = null,
                        tint = selectedPreset.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Operator Sign-In",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkPrimary
                    )
                }

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (showNameError && it.isNotBlank()) showNameError = false
                    },
                    label = { Text("Your Name *") },
                    placeholder = { Text("Enter your name (e.g., Suryakant)") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = if (showNameError) RoseAlert else selectedPreset.secondary
                        )
                    },
                    isError = showNameError,
                    supportingText = if (showNameError) {
                        { Text("Please enter your name to launch LIFE OS.", color = RoseAlert) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = selectedPreset.secondary,
                        unfocusedBorderColor = BorderHairline,
                        focusedLabelColor = selectedPreset.secondary,
                        unfocusedLabelColor = InkSecondary,
                        focusedTextColor = InkPrimary,
                        unfocusedTextColor = InkPrimary
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
                            tint = InkSecondary
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = selectedPreset.primary,
                        unfocusedBorderColor = BorderHairline,
                        focusedLabelColor = selectedPreset.primary,
                        unfocusedLabelColor = InkSecondary,
                        focusedTextColor = InkPrimary,
                        unfocusedTextColor = InkPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_identity_input")
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = petName,
                    onValueChange = { petName = it },
                    label = { Text("Digital Companion Name") },
                    placeholder = { Text("NOVA") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = selectedPreset.primary
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitForm() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = selectedPreset.primary,
                        unfocusedBorderColor = BorderHairline,
                        focusedLabelColor = selectedPreset.primary,
                        unfocusedLabelColor = InkSecondary,
                        focusedTextColor = InkPrimary,
                        unfocusedTextColor = InkPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_pet_input")
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Daily Deep-Focus Target",
                    style = MaterialTheme.typography.labelLarge,
                    color = InkPrimary
                )
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
                            label = { Text("${mins}m / day") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = selectedPreset.primary.copy(alpha = 0.24f),
                                selectedLabelColor = InkPrimary,
                                containerColor = CharcoalElevated,
                                labelColor = InkSecondary
                            )
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Command Accent",
                    style = MaterialTheme.typography.labelLarge,
                    color = InkPrimary
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeAccentPreset.entries.forEach { preset ->
                        val active = selectedPreset == preset
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) preset.primary.copy(alpha = 0.18f) else CharcoalElevated)
                                .border(
                                    width = 1.dp,
                                    color = if (active) preset.secondary else BorderHairline,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedPreset = preset }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(preset.primary)
                            )
                            Spacer(Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(preset.secondary)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { submitForm() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = selectedPreset.primary,
                        contentColor = InkPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("onboarding_submit_btn")
                ) {
                    Text(
                        text = "Sign In & Launch LIFE OS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Your profile, tasks, habits, and widgets are saved locally on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}
