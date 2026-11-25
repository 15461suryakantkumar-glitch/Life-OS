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
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.ui.components.FeeloraBrandMark
import com.example.ui.components.FeeloraStatusBanner
import com.example.ui.components.ObsidianCard
import com.example.ui.components.StatusPill
import com.example.ui.components.UserAvatarBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FeeloraTypeHelpers
import com.example.ui.theme.LifeOsThemeMode
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.tokensForMode
import com.example.ui.viewmodel.AuthUiState

private data class OnboardingStoryPage(
    val eyebrow: String,
    val title: String,
    val headline: String,
    val description: String,
    val bullets: List<String>,
    val icon: ImageVector
)

private val FeeloraStoryPages = listOf(
    OnboardingStoryPage(
        eyebrow = "01 · TASKS",
        title = "Tasks",
        headline = "Calm priority checklists without the clutter.",
        description = "Organize study and daily actions into clean sections, capture tasks in a single tap, and receive gentle, respectful reminders.",
        bullets = listOf(
            "Group work into Overdue, Today, and Upcoming sections",
            "Track streaks and earn XP as every task clicks complete"
        ),
        icon = Icons.Filled.TaskAlt
    ),
    OnboardingStoryPage(
        eyebrow = "02 · STUDY",
        title = "Study",
        headline = "Structured subjects, chapters & focused sprints.",
        description = "Map each subject and chapter, balance daily or weekly study hours, and enter deep focus with Pomodoro or custom timers.",
        bullets = listOf(
            "Track subject progress automatically as chapters finish",
            "Review session logs, notes & quick resources in one home"
        ),
        icon = Icons.Filled.MenuBook
    ),
    OnboardingStoryPage(
        eyebrow = "03 · CALENDAR",
        title = "Calendar",
        headline = "Deadlines and days ahead in peaceful view.",
        description = "Switch between Month, Week, and Day horizons, watch exam countdowns, and export key blocks straight to your device calendar.",
        bullets = listOf(
            "Automatic conflict checks keep study blocks realistic",
            "Pinned milestone countdowns highlight what matters most"
        ),
        icon = Icons.Filled.CalendarMonth
    ),
    OnboardingStoryPage(
        eyebrow = "04 · WELLNESS",
        title = "Wellness",
        headline = "Gentle care for hydration, rhythm & screen balance.",
        description = "Check in with your mood and energy, hit daily hydration goals, monitor screen balance honestly, and track Cycle Care in complete device privacy.",
        bullets = listOf(
            "One-tap hydration & quick daily emotional check-ins",
            "Cycle Care & private reflections never leave your device"
        ),
        icon = Icons.Filled.Spa
    ),
    OnboardingStoryPage(
        eyebrow = "05 · SURU AI",
        title = "SURU AI",
        headline = "Thoughtful study planning that asks before acting.",
        description = "Ask SURU to divide chapters across your available hours, summarize notes, or reschedule overdue tasks with clear confirmation cards.",
        bullets = listOf(
            "Built-in study balancer splits chapters with smart breaks",
            "Supports voice, handwritten note photos & multi-provider AI"
        ),
        icon = Icons.Filled.AutoAwesome
    ),
    OnboardingStoryPage(
        eyebrow = "06 · COMPANION",
        title = "KUTTA Companion",
        headline = "Meet KUTTA — cheering every small step forward.",
        description = "Your loyal desk companion reacts to focus sessions, hydration, streaks, and tasks with warm encouragement and unlockable keepsakes.",
        bullets = listOf(
            "Reflects your live momentum across calm animated states",
            "Grows alongside your XP, streaks & finished study chapters"
        ),
        icon = Icons.Filled.Pets
    ),
    OnboardingStoryPage(
        eyebrow = "07 · TOGETHER ONE",
        title = "Together One",
        headline = "Quiet accountability with people you trust.",
        description = "Connecting with friends is completely optional. Share mutual study streaks, cheer progress, or work toward shared goals on your own terms.",
        bullets = listOf(
            "Invite via personal FLR friend code or deep link",
            "Granular privacy controls — private logs stay 100% yours"
        ),
        icon = Icons.Filled.Groups
    )
)

/**
 * FEELORA Onboarding & Account Setup:
 * - Story Stage (Pages 0..6): 7 illustrated Lavender Editorial feature pages (Tasks, Study, Calendar, Wellness, SURU AI, KUTTA Companion, Together One) with 7-step progress indicator, Back, Continue, and Skip.
 * - Setup Stage (Steps 7..9):
 *   7 -> Account (Supabase Cloud Create Account / Sign In / Reset Password or Offline Local)
 *   8 -> Profile Creation (Display Name, @username, Photo/Avatar, Bio, Language, Theme Light/Dark/System, Focus Goal)
 *   9 -> Optional Friend Code Connection & Launch
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    existingPrefs: UserPreferencesState,
    authUiState: AuthUiState = AuthUiState(),
    inviteLink: String = "",
    onPreviewThemeMode: (LifeOsThemeMode) -> Unit = {},
    onSignUp: (email: String, password: String, displayName: String, onSuccess: () -> Unit) -> Unit = { _, _, _, onSuccess -> onSuccess() },
    onSignIn: (email: String, password: String, onSuccess: () -> Unit) -> Unit = { _, _, _, -> },
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
    // Pages 0..6 = 7 Illustrated Story Pages; Steps 7, 8, 9 = Account, Profile, Connection & Launch
    var step by rememberSaveable { mutableIntStateOf(if (existingPrefs.hasCompletedOnboarding && !existingPrefs.isSignedIn) 7 else 0) }
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
                            tokens.primary.copy(alpha = if (tokens.isDark) 0.22f else 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.16f, size.height * 0.10f),
                        radius = size.maxDimension * 0.55f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tokens.peach.copy(alpha = if (tokens.isDark) 0.12f else 0.24f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.82f),
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
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header Row: Back / Brand + Progress + Skip
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
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        FeeloraBrandMark(size = 34.dp, withSoftFrame = true)
                    }
                }

                if (step in 0..6) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(7) { idx ->
                            val active = idx == step
                            val passed = idx < step
                            Box(
                                modifier = Modifier
                                    .height(6.dp)
                                    .width(if (active) 24.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            active -> tokens.deepPrimary
                                            passed -> tokens.primary
                                            else -> tokens.borderSubtle
                                        }
                                    )
                            )
                        }
                    }
                    TextButton(
                        onClick = { step = 7 },
                        modifier = Modifier.testTag("onboarding_skip_btn")
                    ) {
                        Text(
                            text = "Skip",
                            style = MaterialTheme.typography.labelLarge,
                            color = tokens.inkSecondary
                        )
                    }
                } else {
                    Text(
                        text = "SETUP · ${step - 6}/3",
                        style = FeeloraTypeHelpers.editorialTag,
                        color = tokens.deepPrimary
                    )
                    Spacer(Modifier.size(48.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboarding_step_anim"
            ) { currentStep ->
                when (currentStep) {
                    in 0..6 -> {
                        val page = FeeloraStoryPages[currentStep]
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_page_$currentStep"),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (currentStep == 0) {
                                // Opening Editorial Welcome Banner on Page 1
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(190.dp)
                                        .clip(RoundedCornerShape(tokens.heroRadius))
                                        .border(1.dp, tokens.borderAccent.copy(alpha = 0.55f), RoundedCornerShape(tokens.heroRadius))
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_feelora_welcome_1795511874594),
                                        contentDescription = "FEELORA editorial botanical journal artwork",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, tokens.background.copy(alpha = 0.72f))
                                                )
                                            )
                                    )
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FeeloraBrandMark(size = 36.dp, withSoftFrame = true)
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "FEELORA",
                                                style = MaterialTheme.typography.headlineLarge,
                                                color = tokens.inkPrimary
                                            )
                                            Text(
                                                text = "Calm clarity · Personal growth",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = tokens.inkSecondary
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Distinct illustrated stage for Pages 2..7
                                OnboardingFeatureIllustrationCard(
                                    pageIndex = currentStep,
                                    tokens = tokens
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = page.eyebrow,
                                    style = FeeloraTypeHelpers.editorialTag,
                                    color = tokens.deepPrimary
                                )
                                Text(
                                    text = page.title,
                                    style = MaterialTheme.typography.displayMedium,
                                    color = tokens.inkPrimary
                                )
                                Text(
                                    text = page.headline,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = tokens.deepPrimary
                                )
                                Text(
                                    text = page.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = tokens.inkSecondary
                                )
                            }

                            ObsidianCard(elevated = false) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    page.bullets.forEach { bullet ->
                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = tokens.primary,
                                                modifier = Modifier.size(18.dp).padding(top = 2.dp)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = bullet,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = tokens.inkPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { step = currentStep + 1 },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = tokens.deepPrimary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(tokens.smallRadius),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag(
                                        when (currentStep) {
                                            0 -> "onboarding_get_started_btn"
                                            6 -> "onboarding_together_next_btn"
                                            else -> "onboarding_next_btn"
                                        }
                                    )
                            ) {
                                Text(
                                    text = if (currentStep == 6) "Set Up Your Space" else "Continue",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // SETUP STEP 1 (index 7) — AUTHENTICATION
                    7 -> {
                        val fieldColors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = tokens.primary,
                            unfocusedBorderColor = tokens.borderSubtle,
                            focusedLabelColor = tokens.deepPrimary,
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
                                style = MaterialTheme.typography.displayMedium,
                                color = tokens.inkPrimary
                            )
                            Text(
                                text = "Sign in or create an account to enable Together One cloud sync across devices, or continue privately on this device.",
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
                                            selectedContainerColor = tokens.surfaceHighlight,
                                            selectedLabelColor = tokens.inkPrimary
                                        ),
                                        modifier = Modifier.testTag("auth_tab_$idx")
                                    )
                                }
                            }

                            if (!authUiState.isCloudConfigured) {
                                FeeloraStatusBanner(
                                    message = "Cloud Together keys are not configured in Secrets yet. You can still enjoy FEELORA locally with full offline privacy.",
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
                                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = tokens.deepPrimary) },
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
                                                        step = 8
                                                    }
                                                }
                                                1 -> {
                                                    if (password.isBlank()) {
                                                        localAuthError = "Please enter your password."
                                                        return@Button
                                                    }
                                                    onSignIn(trimmedEmail, password) {
                                                        step = 8
                                                    }
                                                }
                                                else -> onSendPasswordReset(trimmedEmail)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = tokens.deepPrimary, contentColor = Color.White),
                                        shape = RoundedCornerShape(tokens.smallRadius),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("auth_primary_btn")
                                    ) {
                                        if (authUiState.isLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
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
                                onClick = { step = 8 },
                                border = BorderStroke(1.dp, tokens.borderAccent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("auth_continue_local_btn")
                            ) {
                                Text("Continue Offline / Set Up Locally", color = tokens.inkPrimary)
                            }
                        }
                    }

                    // SETUP STEP 2 (index 8) — PROFILE & THEME PREFERENCE
                    8 -> {
                        val fieldColors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = tokens.primary,
                            unfocusedBorderColor = tokens.borderSubtle,
                            focusedLabelColor = tokens.deepPrimary,
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
                                style = MaterialTheme.typography.displayMedium,
                                color = tokens.inkPrimary
                            )
                            Text(
                                text = "Personalize your look. Private journals, Cycle Care, and SURU memories stay strictly on your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkSecondary
                            )

                            ObsidianCard(elevated = false) {
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
                                                    color = if (active) tokens.deepPrimary else tokens.borderSubtle,
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
                                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = tokens.deepPrimary) },
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
                                                .background(if (active) tokens.surfaceHighlight else tokens.surfaceBase)
                                                .border(
                                                    width = if (active) 1.5.dp else 1.dp,
                                                    color = if (active) tokens.deepPrimary else tokens.borderSubtle,
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
                                                Text(mode.subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Box(Modifier.size(14.dp).clip(CircleShape).background(mode.previewSurface))
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
                                        step = 9
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.deepPrimary, contentColor = Color.White),
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

                    // SETUP STEP 3 (index 9) — OPTIONAL FRIEND CONNECTION & READY TO ENTER
                    else -> {
                        val safeName = displayName.trim().ifBlank { "Explorer" }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Together One & Launch",
                                style = MaterialTheme.typography.displayMedium,
                                color = tokens.inkPrimary
                            )
                            Text(
                                text = "Optional: paste a Friend Code or invite link to connect now, or jump straight into FEELORA.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkSecondary
                            )

                            ObsidianCard(elevated = false) {
                                OutlinedTextField(
                                    value = friendCodeInput,
                                    onValueChange = { friendCodeInput = it },
                                    label = { Text("Friend Code or Invite Link (Optional)") },
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
                                                    "Join me on FEELORA Together One! ${inviteLink.ifBlank { "feelora://invite" }}"
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
                                        Text("Ready, $safeName", style = MaterialTheme.typography.titleMedium, color = tokens.inkPrimary)
                                        Text(
                                            text = "${usernameHandle.ifBlank { "@member" }} · ${focusGoalMinutes}m goal · ${selectedTheme.displayName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.inkSecondary
                                        )
                                    }
                                    StatusPill(text = "Ready", color = EmeraldSuccess, icon = Icons.Filled.EmojiEvents)
                                }
                            }

                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { finishAll() },
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.deepPrimary, contentColor = Color.White),
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
private fun OnboardingFeatureIllustrationCard(
    pageIndex: Int,
    tokens: com.example.ui.theme.LifeOsThemeTokens
) {
    val shape = RoundedCornerShape(tokens.heroRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        tokens.surfaceElevated,
                        tokens.surfaceCard,
                        tokens.peach.copy(alpha = if (tokens.isDark) 0.16f else 0.32f)
                    )
                )
            )
            .border(1.dp, tokens.borderAccent.copy(alpha = 0.6f), shape),
        contentAlignment = Alignment.Center
    ) {
        when (pageIndex) {
            // Page 2 — Study (Subject progress rings & session pills)
            1 -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    drawCircle(
                        color = tokens.primary.copy(alpha = 0.14f),
                        radius = h * 0.34f,
                        center = Offset(w * 0.28f, h * 0.50f)
                    )
                    drawArc(
                        color = tokens.deepPrimary,
                        startAngle = -90f,
                        sweepAngle = 250f,
                        useCenter = false,
                        topLeft = Offset(w * 0.28f - h * 0.26f, h * 0.50f - h * 0.26f),
                        size = Size(h * 0.52f, h * 0.52f),
                        style = Stroke(width = 14f, cap = StrokeCap.Round)
                    )
                    drawRoundRect(
                        color = tokens.surfaceCard,
                        topLeft = Offset(w * 0.52f, h * 0.24f),
                        size = Size(w * 0.38f, h * 0.22f),
                        cornerRadius = CornerRadius(24f, 24f)
                    )
                    drawRoundRect(
                        color = tokens.peach.copy(alpha = 0.65f),
                        topLeft = Offset(w * 0.52f, h * 0.54f),
                        size = Size(w * 0.34f, h * 0.22f),
                        cornerRadius = CornerRadius(24f, 24f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.padding(start = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("72%", style = FeeloraTypeHelpers.statNumber, color = tokens.deepPrimary)
                        Text("Mastery", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(end = 16.dp)) {
                        Text("3 Chapters · 25m Focus", style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary)
                        Text("5m Mindful Break", style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary)
                    }
                }
            }
            // Page 3 — Calendar (Editorial Grid + Countdown)
            2 -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cols = 5
                    val cell = 24f
                    val gap = 14f
                    val startX = w * 0.14f
                    val startY = h * 0.28f
                    for (r in 0..2) {
                        for (c in 0 until cols) {
                            val active = (r == 1 && c == 2) || (r == 2 && c == 4)
                            drawCircle(
                                color = if (active) tokens.deepPrimary else tokens.primary.copy(alpha = 0.20f),
                                radius = cell * 0.52f,
                                center = Offset(startX + c * (cell + gap), startY + r * (cell + gap))
                            )
                        }
                    }
                    drawRoundRect(
                        color = tokens.peach.copy(alpha = 0.55f),
                        topLeft = Offset(w * 0.58f, h * 0.28f),
                        size = Size(w * 0.32f, h * 0.44f),
                        cornerRadius = CornerRadius(32f, 32f)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("4 DAYS", style = FeeloraTypeHelpers.statNumber, color = tokens.inkPrimary)
                        Text("Next Milestone", style = MaterialTheme.typography.labelMedium, color = tokens.inkSecondary)
                    }
                }
            }
            // Page 4 — Wellness (Water ripples & Calm Mood Wave)
            3 -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = Offset(size.width * 0.5f, size.height * 0.5f)
                    listOf(0.42f, 0.30f, 0.18f).forEachIndexed { i, factor ->
                        drawCircle(
                            color = if (i == 2) tokens.peach.copy(alpha = 0.55f) else tokens.primary.copy(alpha = 0.14f + i * 0.08f),
                            radius = size.minDimension * factor,
                            center = c
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Spa, contentDescription = null, tint = tokens.deepPrimary, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Hydration · Mood · Private Cycle Care", style = MaterialTheme.typography.labelLarge, color = tokens.inkPrimary)
                }
            }
            // Page 5 — SURU AI (Luminous botanical intelligence halo)
            4 -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(tokens.primary.copy(alpha = 0.36f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.44f),
                            radius = size.minDimension * 0.46f
                        )
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = tokens.deepPrimary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    StatusPill(text = "\"3 chapters · 4h balanced with breaks\"", color = tokens.deepPrimary)
                }
            }
            // Page 6 — KUTTA Companion (Official companion ONLY here!)
            5 -> {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.kutta_happy),
                        contentDescription = "KUTTA companion illustration",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(154.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(text = "KUTTA", color = tokens.deepPrimary, icon = Icons.Filled.Favorite)
                        Text(
                            text = "\"Malkin, har chhota step celebrate karenge!\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.inkPrimary
                        )
                    }
                }
            }
            // Page 7 — Together One (Overlapping Lavender & Peach circles)
            else -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cy = size.height * 0.48f
                    drawCircle(
                        color = tokens.primary.copy(alpha = 0.24f),
                        radius = size.minDimension * 0.30f,
                        center = Offset(size.width * 0.41f, cy)
                    )
                    drawCircle(
                        color = tokens.peach.copy(alpha = 0.52f),
                        radius = size.minDimension * 0.30f,
                        center = Offset(size.width * 0.59f, cy)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Groups, contentDescription = null, tint = tokens.deepPrimary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(6.dp))
                    StatusPill(text = "TOGETHER ONE · OPTIONAL", color = tokens.deepPrimary)
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
                color = tokens.surfaceHighlight,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = tokens.deepPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Optional Permissions",
                style = MaterialTheme.typography.headlineLarge,
                color = tokens.inkPrimary,
                textAlign = TextAlign.Center
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
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = tokens.deepPrimary)
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
                        Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = tokens.primary)
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
                    containerColor = tokens.deepPrimary,
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
