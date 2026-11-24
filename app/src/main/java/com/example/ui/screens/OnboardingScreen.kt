package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.local.UserAvatarPreset
import com.example.data.local.UserPreferencesState
import com.example.notifications.AppPermissionId
import com.example.notifications.AppPermissionInfo
import com.example.notifications.AppPermissionStatus
import com.example.notifications.PermissionCenterHelper
import com.example.ui.components.FeeloraBannerTone
import com.example.ui.components.FeeloraStatusBanner
import com.example.ui.components.ObsidianCard
import com.example.ui.components.StatusPill
import com.example.ui.components.UserAvatarBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LifeOsThemeMode
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.tokensForMode
import com.example.ui.viewmodel.AuthUiState

/**
 * 7-Screen FEELORA First-Launch Onboarding Experience:
 * 1. Welcome
 * 2. Discover FEELORA (Tasks, Study, Calendar, Wellness, SURU AI, Companion, Together)
 * 3. Together Introduction (Optional social features)
 * 4. Authentication (Create Account, Login, Forgot Password, Email verification, or local studio)
 * 5. Profile Creation (Display name, @username, photo/avatar, bio, language, 3-theme preference)
 * 6. Optional Connection (Friend code / invite link / share / Skip)
 * 7. Finish (Launch celebration)
 *
 * Strictly excludes companion pet visuals and mascot selection on all 7 onboarding screens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    existingPrefs: UserPreferencesState,
    authUiState: AuthUiState = AuthUiState(),
    inviteLink: String = "",
    onPreviewThemeMode: (LifeOsThemeMode) -> Unit = {},
    onSignUp: (email: String, password: String, displayName: String, onSuccess: () -> Unit) -> Unit = { _, _, _, onSuccess -> onSuccess() },
    onSignIn: (email: String, password: String, onSuccess: () -> Unit) -> Unit = { _, _, onSuccess -> onSuccess() },
    onSendPasswordReset: (String) -> Unit = {},
    onResendVerification: (String) -> Unit = {},
    onCheckUsername: (String) -> Unit = {},
    onClearAuthFeedback: () -> Unit = {},
    onConnectFriendInput: (String, (Boolean, String) -> Unit) -> Unit = { _, cb -> cb(true, "Connected") },
    onComplete7Step: (
        name: String,
        handleOrEmail: String,
        bio: String,
        preferredLanguage: String,
        themeMode: LifeOsThemeMode,
        avatarPreset: UserAvatarPreset,
        avatarPhotoUri: String,
        focusGoalMinutes: Int
    ) -> Unit
) {
    var step by rememberSaveable { mutableIntStateOf(if (existingPrefs.hasCompletedOnboarding && !existingPrefs.isSignedIn) 3 else 0) }
    var authTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Create Account, 1 = Sign In, 2 = Reset

    // Auth fields
    var email by rememberSaveable { mutableStateOf(existingPrefs.userEmail) }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var localAuthError by rememberSaveable { mutableStateOf<String?>(null) }

    // Profile fields
    var displayName by rememberSaveable { mutableStateOf(existingPrefs.userName) }
    var usernameHandle by rememberSaveable { mutableStateOf(existingPrefs.userHandle) }
    var bio by rememberSaveable { mutableStateOf(existingPrefs.userBio) }
    var preferredLanguage by rememberSaveable { mutableStateOf(existingPrefs.preferredLanguage.ifBlank { "English" }) }
    var selectedTheme by rememberSaveable { mutableStateOf(existingPrefs.themeMode) }
    var selectedAvatar by rememberSaveable { mutableStateOf(existingPrefs.avatarPreset) }
    var selectedPhotoUri by rememberSaveable { mutableStateOf(existingPrefs.avatarPhotoUri) }
    var focusGoalMinutes by rememberSaveable { mutableIntStateOf(existingPrefs.dailyFocusGoalMinutes) }
    var nameError by rememberSaveable { mutableStateOf(false) }

    // Optional friend connection step
    var friendCodeInput by rememberSaveable { mutableStateOf("") }
    var friendConnectMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var friendConnectOk by rememberSaveable { mutableStateOf(false) }

    val tokens = tokensForMode(selectedTheme)
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri.toString()
        }
    }

    BackHandler(enabled = step > 0) {
        step -= 1
    }

    fun finishAll() {
        val cleanName = displayName.trim().ifBlank {
            email.substringBefore("@").replaceFirstChar { it.uppercase() }.ifBlank { "Explorer" }
        }
        val cleanHandle = usernameHandle.trim().ifBlank {
            "@${cleanName.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "member" }}"
        }
        onComplete7Step(
            cleanName,
            cleanHandle,
            bio.trim(),
            preferredLanguage,
            selectedTheme,
            selectedAvatar,
            selectedPhotoUri,
            focusGoalMinutes
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
                        radius = size.maxDimension * 0.55f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tokens.secondary.copy(alpha = if (tokens.isDark) 0.16f else 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.80f),
                        radius = size.maxDimension * 0.50f
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
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top step indicator & Back arrow (Steps 2..7)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (step > 0) {
                    IconButton(
                        onClick = { step -= 1 },
                        modifier = Modifier.size(48.dp).testTag("onboarding_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous step",
                            tint = tokens.inkPrimary
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }

                // 7-step pill dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(7) { idx ->
                        val active = idx == step
                        val passed = idx < step
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (active) 22.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        active -> tokens.primary
                                        passed -> tokens.secondary
                                        else -> tokens.borderSubtle
                                    }
                                )
                        )
                    }
                }

                Text(
                    text = "${step + 1}/7",
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.inkSecondary,
                    modifier = Modifier.padding(end = 10.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboarding_step_anim"
            ) { currentStep ->
                when (currentStep) {
                    // SCREEN 1 — WELCOME
                    0 -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .clip(RoundedCornerShape(tokens.heroRadius))
                                    .border(1.dp, tokens.borderAccent.copy(alpha = 0.45f), RoundedCornerShape(tokens.heroRadius))
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_feelora_welcome_1795511874594),
                                    contentDescription = "FEELORA calm studio illustration",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, tokens.background.copy(alpha = 0.68f))
                                            )
                                        )
                                )
                            }

                            Spacer(Modifier.height(26.dp))

                            Text(
                                text = "FEELORA",
                                style = MaterialTheme.typography.displayLarge,
                                color = tokens.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Calm clarity for your study rhythm, tasks, calendar, wellness, and meaningful focus — every single day.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = tokens.inkSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(30.dp))

                            Button(
                                onClick = { step = 1 },
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.primary, contentColor = tokens.background),
                                shape = RoundedCornerShape(tokens.smallRadius),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("onboarding_get_started_btn")
                            ) {
                                Text("Get Started", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // SCREEN 2 — DISCOVER FEELORA
                    1 -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Discover FEELORA",
                                style = MaterialTheme.typography.headlineLarge,
                                color = tokens.inkPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Seven calm modules built into one cohesive personal workspace.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.inkSecondary
                            )
                            Spacer(Modifier.height(4.dp))

                            val features = listOf(
                                Triple(Icons.Filled.TaskAlt, "Tasks", "Sectioned lists, quick inline add, and smart gentle reminders."),
                                Triple(Icons.Filled.School, "Study", "Pomodoro focus timer, weighted chapter plans, and study aids."),
                                Triple(Icons.Filled.CalendarMonth, "Calendar", "Month, week, and day agenda with milestone countdowns."),
                                Triple(Icons.Filled.HealthAndSafety, "Wellness", "Hydration, daily mood check-in, screen balance & private Cycle Care."),
                                Triple(Icons.Filled.AutoAwesome, "SURU AI", "Voice, camera notes, briefings, and multi-provider intelligence."),
                                Triple(Icons.Filled.Favorite, "Companion", "An expressive companion unlocked inside your workspace after setup."),
                                Triple(Icons.Filled.Groups, "Together", "Optional accountability chats, shared goals, and friendly progress.")
                            )
                            features.forEach { (icon, title, desc) ->
                                ObsidianCard(contentPadding = PaddingValues(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(tokens.primary.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(icon, contentDescription = null, tint = tokens.primary, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(title, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary, fontWeight = FontWeight.SemiBold)
                                            Text(desc, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            Button(
                                onClick = { step = 2 },
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.primary, contentColor = tokens.background),
                                shape = RoundedCornerShape(tokens.smallRadius),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("onboarding_next_btn")
                            ) {
                                Text("Continue", fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // SCREEN 3 — TOGETHER INTRODUCTION
                    2 -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatusPill(
                                text = "OPTIONAL SOCIAL CIRCLE",
                                color = tokens.secondary,
                                icon = Icons.Filled.Groups
                            )
                            Text(
                                text = "Meet Together",
                                style = MaterialTheme.typography.headlineLarge,
                                color = tokens.inkPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Grow alongside close study friends without social noise. Connecting with friends is completely optional — you decide what to share.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.inkSecondary
                            )

                            val highlights = listOf(
                                "Private 1-to-1 chat & photo sharing with trusted friends",
                                "Celebrate achievements & shared daily study streaks",
                                "Create collaborative Shared Goals and stay accountable",
                                "Friendly XP, Task, Focus & Streak challenges",
                                "Compare only the stats you explicitly opt in to share"
                            )
                            ObsidianCard(elevated = true) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    highlights.forEach { line ->
                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = EmeraldSuccess,
                                                modifier = Modifier.size(18.dp).padding(top = 2.dp)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(line, style = MaterialTheme.typography.bodyMedium, color = tokens.inkPrimary)
                                        }
                                    }
                                }
                            }

                            ObsidianCard(borderColor = tokens.secondary.copy(alpha = 0.45f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, tint = tokens.secondary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Cycle Care, private notes, daily reflections, and SURU conversations stay 100% private on your device.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.inkSecondary
                                    )
                                }
                            }

                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { step = 3 },
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.primary, contentColor = tokens.background),
                                shape = RoundedCornerShape(tokens.smallRadius),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("onboarding_together_next_btn")
                            ) {
                                Text("Continue to Account", fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // SCREEN 4 — AUTHENTICATION
                    3 -> {
                        val fieldColors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = tokens.primary,
                            unfocusedBorderColor = tokens.borderSubtle,
                            focusedLabelColor = tokens.primary,
                            unfocusedLabelColor = tokens.inkSecondary,
                            focusedTextColor = tokens.inkPrimary,
                            unfocusedTextColor = tokens.inkPrimary
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Your Account",
                                style = MaterialTheme.typography.headlineLarge,
                                color = tokens.inkPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Sign in or create an account to enable cloud Together sync across devices, or continue locally anytime.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkSecondary
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0 to "Create Account", 1 to "Sign In", 2 to "Reset Password").forEach { (idx, label) ->
                                    FilterChip(
                                        selected = authTab == idx,
                                        onClick = {
                                            authTab = idx
                                            localAuthError = null
                                            onClearAuthFeedback()
                                        },
                                        label = { Text(label, maxLines = 1, softWrap = false) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = tokens.primary.copy(alpha = 0.22f),
                                            selectedLabelColor = tokens.inkPrimary
                                        ),
                                        modifier = Modifier.testTag("auth_tab_$idx")
                                    )
                                }
                            }

                            if (!authUiState.isCloudConfigured) {
                                FeeloraStatusBanner(
                                    message = "Cloud Together keys are not set in Secrets. You can still use FEELORA locally with full offline privacy.",
                                    tone = FeeloraBannerTone.INFO
                                )
                            }

                            val displayErr = localAuthError ?: authUiState.errorMessage
                            if (!displayErr.isNullOrBlank()) {
                                FeeloraStatusBanner(
                                    message = displayErr,
                                    tone = FeeloraBannerTone.ERROR,
                                    onDismiss = {
                                        localAuthError = null
                                        onClearAuthFeedback()
                                    }
                                )
                            }

                            if (!authUiState.infoMessage.isNullOrBlank()) {
                                FeeloraStatusBanner(
                                    message = authUiState.infoMessage,
                                    tone = FeeloraBannerTone.SUCCESS
                                )
                            }

                            if (!authUiState.verificationEmailPending.isNullOrBlank()) {
                                ObsidianCard(borderColor = AmberWarning.copy(alpha = 0.5f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.MarkEmailRead, contentDescription = null, tint = AmberWarning)
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Verify your email", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                            Text(
                                                "Check ${authUiState.verificationEmailPending} and confirm your link.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = tokens.inkSecondary
                                            )
                                        }
                                        TextButton(onClick = { onResendVerification(authUiState.verificationEmailPending) }) {
                                            Text("Resend")
                                        }
                                    }
                                }
                            }

                            ObsidianCard(elevated = true) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (authTab == 0) {
                                        OutlinedTextField(
                                            value = displayName,
                                            onValueChange = { displayName = it },
                                            label = { Text("Full Name") },
                                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = tokens.primary) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                                            colors = fieldColors,
                                            modifier = Modifier.fillMaxWidth().testTag("auth_name_input")
                                        )
                                    }

                                    OutlinedTextField(
                                        value = email,
                                        onValueChange = {
                                            email = it
                                            localAuthError = null
                                        },
                                        label = { Text("Email address") },
                                        leadingIcon = { Icon(Icons.Filled.MailOutline, contentDescription = null, tint = tokens.inkSecondary) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                        colors = fieldColors,
                                        modifier = Modifier.fillMaxWidth().testTag("auth_email_input")
                                    )

                                    if (authTab != 2) {
                                        OutlinedTextField(
                                            value = password,
                                            onValueChange = {
                                                password = it
                                                localAuthError = null
                                            },
                                            label = { Text("Password") },
                                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = tokens.inkSecondary) },
                                            trailingIcon = {
                                                IconButton(onClick = { showPassword = !showPassword }) {
                                                    Icon(
                                                        imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                        contentDescription = if (showPassword) "Hide password" else "Show password"
                                                    )
                                                }
                                            },
                                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                            colors = fieldColors,
                                            modifier = Modifier.fillMaxWidth().testTag("auth_password_input")
                                        )
                                    }

                                    if (authTab == 0) {
                                        OutlinedTextField(
                                            value = confirmPassword,
                                            onValueChange = {
                                                confirmPassword = it
                                                localAuthError = null
                                            },
                                            label = { Text("Confirm Password") },
                                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = tokens.inkSecondary) },
                                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                            colors = fieldColors,
                                            modifier = Modifier.fillMaxWidth().testTag("auth_confirm_password_input")
                                        )
                                    }

                                    Spacer(Modifier.height(4.dp))

                                    Button(
                                        enabled = !authUiState.isLoading,
                                        onClick = {
                                            focusManager.clearFocus()
                                            val trimmedEmail = email.trim()
                                            if (!trimmedEmail.contains("@") || !trimmedEmail.contains(".")) {
                                                localAuthError = "Please enter a valid email address."
                                                return@Button
                                            }
                                            when (authTab) {
                                                0 -> {
                                                    if (displayName.isBlank()) {
                                                        localAuthError = "Please enter your name."
                                                        return@Button
                                                    }
                                                    if (password.length < 6) {
                                                        localAuthError = "Password must be at least 6 characters."
                                                        return@Button
                                                    }
                                                    if (password != confirmPassword) {
                                                        localAuthError = "Passwords do not match."
                                                        return@Button
                                                    }
                                                    onSignUp(trimmedEmail, password, displayName.trim()) {
                                                        step = 4
                                                    }
                                                }
                                                1 -> {
                                                    if (password.isBlank()) {
                                                        localAuthError = "Please enter your password."
                                                        return@Button
                                                    }
                                                    onSignIn(trimmedEmail, password) {
                                                        step = 4
                                                    }
                                                }
                                                else -> onSendPasswordReset(trimmedEmail)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = tokens.primary, contentColor = tokens.background),
                                        shape = RoundedCornerShape(tokens.smallRadius),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("auth_primary_btn")
                                    ) {
                                        if (authUiState.isLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = tokens.background)
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text(
                                            text = when (authTab) {
                                                0 -> "Create Account"
                                                1 -> "Sign In"
                                                else -> "Send Reset Email"
                                            },
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { step = 4 },
                                border = BorderStroke(1.dp, tokens.borderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("auth_continue_local_btn")
                            ) {
                                Text("Continue Offline / Set Up Locally")
                            }
                        }
                    }

                    // SCREEN 5 — PROFILE CREATION
                    4 -> {
                        val fieldColors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = tokens.primary,
                            unfocusedBorderColor = tokens.borderSubtle,
                            focusedLabelColor = tokens.primary,
                            unfocusedLabelColor = tokens.inkSecondary,
                            focusedTextColor = tokens.inkPrimary,
                            unfocusedTextColor = tokens.inkPrimary
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Create Your Profile",
                                style = MaterialTheme.typography.headlineLarge,
                                color = tokens.inkPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Personalize your look. Private journals, Cycle Care, and SURU memories stay private by default.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkSecondary
                            )

                            ObsidianCard(elevated = true) {
                                // Avatar preview + Photo Picker
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatarBadge(
                                        preset = selectedAvatar,
                                        photoUri = selectedPhotoUri,
                                        displayName = displayName,
                                        size = 64.dp
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Profile Picture", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                                        Text(
                                            text = if (selectedPhotoUri.isNotBlank()) "Custom photo selected" else "Choose an avatar or upload a photo",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.inkSecondary
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        },
                                        modifier = Modifier.testTag("onboarding_pick_photo_btn")
                                    ) {
                                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Photo", maxLines = 1, softWrap = false)
                                    }
                                }

                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    UserAvatarPreset.entries.forEach { preset ->
                                        val active = selectedAvatar == preset && selectedPhotoUri.isBlank()
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .border(
                                                    width = if (active) 2.dp else 1.dp,
                                                    color = if (active) tokens.primary else tokens.borderSubtle,
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    selectedAvatar = preset
                                                    selectedPhotoUri = ""
                                                }
                                                .padding(3.dp)
                                        ) {
                                            UserAvatarBadge(preset = preset, size = 38.dp)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = displayName,
                                    onValueChange = {
                                        displayName = it
                                        if (nameError && it.isNotBlank()) nameError = false
                                    },
                                    label = { Text("Display Name") },
                                    placeholder = { Text("Your name") },
                                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = tokens.primary) },
                                    isError = nameError,
                                    supportingText = if (nameError) {
                                        { Text("Please enter your display name.", color = RoseAlert) }
                                    } else null,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth().testTag("onboarding_name_input")
                                )

                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = usernameHandle,
                                    onValueChange = {
                                        usernameHandle = it
                                        onCheckUsername(it)
                                    },
                                    label = { Text("Unique Username") },
                                    placeholder = { Text("@yourhandle") },
                                    leadingIcon = { Icon(Icons.Filled.AlternateEmail, contentDescription = null, tint = tokens.inkSecondary) },
                                    supportingText = {
                                        when {
                                            authUiState.usernameCheckBusy -> Text("Checking availability...", color = tokens.inkSecondary)
                                            authUiState.usernameAvailable == true -> Text("Username available", color = EmeraldSuccess)
                                            authUiState.usernameAvailable == false -> Text("Username already taken", color = RoseAlert)
                                            else -> Text("Letters & numbers recommended", color = tokens.inkMuted)
                                        }
                                    },
                                    singleLine = true,
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth().testTag("onboarding_identity_input")
                                )

                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = bio,
                                    onValueChange = { bio = it.take(160) },
                                    label = { Text("Bio (Optional)") },
                                    placeholder = { Text("What are you working toward?") },
                                    maxLines = 2,
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth().testTag("onboarding_bio_input")
                                )

                                Spacer(Modifier.height(10.dp))
                                Text("Preferred Language", style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary)
                                Spacer(Modifier.height(6.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("English", "Hinglish", "Hindi").forEach { lang ->
                                        FilterChip(
                                            selected = preferredLanguage == lang,
                                            onClick = { preferredLanguage = lang },
                                            label = { Text(lang, maxLines = 1, softWrap = false) }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))
                                Text("Daily Focus Goal", style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary)
                                Spacer(Modifier.height(6.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(60, 90, 120, 180).forEach { mins ->
                                        FilterChip(
                                            selected = focusGoalMinutes == mins,
                                            onClick = { focusGoalMinutes = mins },
                                            label = { Text("${mins}m", maxLines = 1, softWrap = false) }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))
                                Text("Theme Preference", style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary)
                                Spacer(Modifier.height(6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    LifeOsThemeMode.entries.forEach { mode ->
                                        val active = selectedTheme == mode
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
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
                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                                .testTag("onboarding_theme_${mode.name}"),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(mode.displayName, style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary, fontWeight = FontWeight.SemiBold)
                                                Text(mode.subtitle, style = MaterialTheme.typography.labelSmall, color = tokens.inkSecondary)
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Box(Modifier.size(14.dp).clip(CircleShape).background(mode.previewPrimary))
                                                Box(Modifier.size(14.dp).clip(CircleShape).background(mode.previewSecondary))
                                            }
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    if (displayName.isBlank()) {
                                        nameError = true
                                    } else {
                                        focusManager.clearFocus()
                                        step = 5
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.primary, contentColor = tokens.background),
                                shape = RoundedCornerShape(tokens.smallRadius),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("onboarding_profile_next_btn")
                            ) {
                                Text("Continue", fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // SCREEN 6 — OPTIONAL FRIEND CONNECTION
                    5 -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Connect with a Friend",
                                style = MaterialTheme.typography.headlineLarge,
                                color = tokens.inkPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Have a Friend Code or invite link? Paste it below to connect right away, or invite someone to join Together.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkSecondary
                            )

                            ObsidianCard(elevated = true) {
                                OutlinedTextField(
                                    value = friendCodeInput,
                                    onValueChange = { friendCodeInput = it },
                                    label = { Text("Friend Code or Invite Link") },
                                    placeholder = { Text("FLR-NAME-1234 or feelora://invite?...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("onboarding_friend_code_input")
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            onConnectFriendInput(friendCodeInput) { ok, msg ->
                                                friendConnectOk = ok
                                                friendConnectMessage = msg
                                            }
                                        },
                                        enabled = friendCodeInput.isNotBlank(),
                                        modifier = Modifier.weight(1f).testTag("onboarding_connect_friend_btn")
                                    ) {
                                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Connect", maxLines = 1, softWrap = false)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "Join me on FEELORA Together! ${inviteLink.ifBlank { "feelora://invite" }}"
                                                )
                                            }
                                            runCatching {
                                                context.startActivity(Intent.createChooser(shareIntent, "Invite to FEELORA"))
                                            }
                                        },
                                        modifier = Modifier.weight(1f).testTag("onboarding_share_invite_btn")
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Share Link", maxLines = 1, softWrap = false)
                                    }
                                }

                                friendConnectMessage?.let { msg ->
                                    Spacer(Modifier.height(10.dp))
                                    FeeloraStatusBanner(
                                        message = msg,
                                        tone = if (friendConnectOk) FeeloraBannerTone.SUCCESS else FeeloraBannerTone.WARNING
                                    )
                                }
                            }

                            Button(
                                onClick = { step = 6 },
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.primary, contentColor = tokens.background),
                                shape = RoundedCornerShape(tokens.smallRadius),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("onboarding_connection_next_btn")
                            ) {
                                Text("Continue", fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(
                                onClick = { step = 6 },
                                modifier = Modifier.align(Alignment.CenterHorizontally).testTag("onboarding_skip_connection_btn")
                            ) {
                                Text("Skip for now")
                            }
                        }
                    }

                    // SCREEN 7 — FINISH
                    else -> {
                        val safeName = displayName.trim().ifBlank { "Explorer" }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Spacer(Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .size(92.dp)
                                    .clip(CircleShape)
                                    .background(tokens.primary.copy(alpha = 0.16f))
                                    .border(2.dp, tokens.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.EmojiEvents,
                                    contentDescription = "Setup complete",
                                    tint = tokens.secondary,
                                    modifier = Modifier.size(44.dp)
                                )
                            }

                            Text(
                                text = "You're All Set, $safeName",
                                style = MaterialTheme.typography.headlineLarge,
                                color = tokens.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Your personal FEELORA studio is ready with ${selectedTheme.displayName} theme, ${focusGoalMinutes}m daily focus target, and SURU standing by.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.inkSecondary,
                                textAlign = TextAlign.Center
                            )

                            ObsidianCard(elevated = true) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    UserAvatarBadge(
                                        preset = selectedAvatar,
                                        photoUri = selectedPhotoUri,
                                        displayName = safeName,
                                        size = 48.dp
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(safeName, style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                                        Text(
                                            text = "${usernameHandle.ifBlank { "@member" }} · $preferredLanguage · ${selectedTheme.displayName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.inkSecondary
                                        )
                                    }
                                    StatusPill(text = "Ready", color = EmeraldSuccess)
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { finishAll() },
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.primary, contentColor = tokens.background),
                                shape = RoundedCornerShape(tokens.smallRadius),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("onboarding_submit_btn")
                            ) {
                                Text("Enter FEELORA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
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
                text = "Allow what helps you today, or skip now and adjust anytime in Settings → Permissions.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.inkSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(18.dp))

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
                            Text("Digital Wellbeing Sync (Optional)", style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                            Text(
                                "Reads on-device app usage totals for Wellness. Never shared externally.",
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
                text = "Camera & Microphone are only requested when you tap Photo Camera or Voice inside SURU.",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.inkMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.primary,
                    contentColor = tokens.background
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
