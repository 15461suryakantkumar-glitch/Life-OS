package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.local.UserPreferencesState
import com.example.notifications.AppPermissionId
import com.example.notifications.AppPermissionInfo
import com.example.notifications.AppPermissionStatus
import com.example.notifications.PermissionCenterHelper
import com.example.ui.components.ObsidianCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LifeOsThemeMode
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
        focusGoalMinutes: Int,
        themeMode: LifeOsThemeMode
    ) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(existingPrefs.userName) }
    var identity by rememberSaveable {
        mutableStateOf(existingPrefs.userEmail.ifBlank { existingPrefs.userHandle })
    }
    var focusGoalMinutes by rememberSaveable { mutableIntStateOf(existingPrefs.dailyFocusGoalMinutes) }
    var selectedTheme by rememberSaveable { mutableStateOf(existingPrefs.themeMode) }
    var showNameError by rememberSaveable { mutableStateOf(false) }

    val tokens = tokensForMode(selectedTheme)
    val focusManager = LocalFocusManager.current

    fun submitProfile() {
        if (name.isBlank()) {
            showNameError = true
            return
        }
        focusManager.clearFocus()
        onCompleteSignIn(
            name.trim(),
            identity.trim(),
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
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = tokens.surfaceElevated,
                border = BorderStroke(1.dp, tokens.borderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = tokens.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "FEELORA · PERSONAL STUDIO",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.inkSecondary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Welcome to FEELORA",
                style = MaterialTheme.typography.headlineLarge,
                color = tokens.inkPrimary,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your calm, intelligent space for study planning, tasks, calendar clarity, and personal growth with SURU.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.inkSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(22.dp))

            ObsidianCard(modifier = Modifier.fillMaxWidth()) {
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
                            tint = if (showNameError) RoseAlert else tokens.primary
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
                        focusedBorderColor = tokens.primary,
                        unfocusedBorderColor = tokens.borderSubtle,
                        focusedLabelColor = tokens.primary,
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
                    listOf(60, 90, 120, 180).forEach { mins ->
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

                Text("Studio Look", style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(LifeOsThemeMode.FEELORA_LIGHT, LifeOsThemeMode.FEELORA_DARK).forEach { mode ->
                        val active = selectedTheme == mode
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(tokens.smallRadius))
                                .background(if (active) mode.previewPrimary.copy(alpha = 0.16f) else tokens.surfaceElevated)
                                .border(
                                    width = if (active) 1.5.dp else 1.dp,
                                    color = if (active) mode.previewPrimary else tokens.borderSubtle,
                                    shape = RoundedCornerShape(tokens.smallRadius)
                                )
                                .clickable {
                                    selectedTheme = mode
                                    onPreviewThemeMode(mode)
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (mode.isDark) "Dark" else "Lavender Light",
                                style = MaterialTheme.typography.labelLarge,
                                color = tokens.inkPrimary,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(mode.previewPrimary)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = { submitProfile() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(tokens.smallRadius),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("onboarding_submit_btn")
                ) {
                    Text("Continue", maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "All tasks, Cycle Care, and personal memories stay private under your control.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.inkMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PermissionSetupScreen(
    permissions: List<AppPermissionInfo>,
    onRefreshPermissions: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val tokens = com.example.ui.theme.LifeOsThemeTokensProvider.current

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshPermissions()
    }

    val notifInfo = permissions.firstOrNull { it.id == AppPermissionId.NOTIFICATIONS }
    val usageInfo = permissions.firstOrNull { it.id == AppPermissionId.USAGE_ACCESS }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("permission_setup_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = tokens.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = tokens.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Optional Permissions",
                style = MaterialTheme.typography.headlineMedium,
                color = tokens.inkPrimary,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Allow what helps you today, or skip now and change anytime in Settings → Permissions.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.inkSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(18.dp))

            // 1. Notifications
            ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = tokens.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Smart Task Reminders", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                            Text(
                                "Gentle 2-hour pending task nudges & morning briefings (skips calendar blocks & quiet hours).",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkSecondary
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (notifInfo?.status == AppPermissionStatus.GRANTED) {
                        StatusPill(text = "Allowed", color = EmeraldSuccess, icon = Icons.Filled.CheckCircle)
                    } else {
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    runCatching {
                                        context.startActivity(PermissionCenterHelper.buildNotificationSettingsIntent(context))
                                    }
                                }
                            },
                            modifier = Modifier.testTag("onboarding_allow_notif_btn")
                        ) { Text("Allow", maxLines = 1, softWrap = false) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 2. Digital Wellbeing Usage Access (Optional)
            ObsidianCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = tokens.secondary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Screen-Time Sync (Optional)", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                            Text(
                                "Reads on-device app usage totals for Wellness OS. Never shared externally.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkSecondary
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (usageInfo?.status == AppPermissionStatus.GRANTED) {
                        StatusPill(text = "Granted", color = EmeraldSuccess, icon = Icons.Filled.CheckCircle)
                    } else {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        PermissionCenterHelper.buildUsageAccessSettingsIntent()
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            modifier = Modifier.testTag("onboarding_usage_btn")
                        ) { Text("Settings", maxLines = 1, softWrap = false) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Camera & Microphone are only requested if you choose to tap Photo Camera or Voice inside SURU.",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.inkMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.primary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(tokens.smallRadius),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("onboarding_enter_app_btn")
            ) {
                Text("Enter FEELORA", maxLines = 1, softWrap = false)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.height(6.dp))

            TextButton(
                onClick = onContinue,
                modifier = Modifier.testTag("onboarding_skip_permissions_btn")
            ) {
                Text("Skip for now")
            }
        }
    }
}
