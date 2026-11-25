package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.example.kutta.LocalReducedMotion
import com.example.ui.components.FeeloraBannerTone
import com.example.ui.components.FeeloraLogo
import com.example.ui.components.FeeloraSleepingKuttaMascot
import com.example.ui.components.FeeloraSparkleBackdrop
import com.example.ui.components.FeeloraStatusBanner
import com.example.ui.theme.FeeloraHeadingViolet
import com.example.ui.theme.FeeloraPrimaryCta
import com.example.ui.theme.FeeloraSlateBody
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.viewmodel.AuthUiState
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

object FeeloraAuthValidator {
    fun validateEmail(rawEmail: String): String? {
        val email = rawEmail.trim()
        if (email.isEmpty()) return "Please enter your email address."
        val atIdx = email.indexOf('@')
        val dotIdx = email.lastIndexOf('.')
        if (atIdx <= 0 || dotIdx <= atIdx + 1 || dotIdx >= email.length - 1) {
            return "Please enter a valid email address."
        }
        return null
    }

    fun validatePassword(password: String, isCreateAccount: Boolean): String? {
        if (password.isBlank()) return "Please enter your password."
        if (isCreateAccount && password.length < 6) {
            return "Password must be at least 6 characters."
        }
        return null
    }
}

/**
 * Matching Feelora Login Screen:
 * - Same midnight-violet (#170F2E -> #4B2E83) to soft-lavender (#EAE4F8 -> #F7F5FC) gradient
 * - Glowing Feelora heart logo
 * - Sleeping KUTTA golden puppy resting right on top of the frosted-lavender auth card
 * - ONLY two login options:
 *   1) "Continue with Google"
 *   2) "Continue with Email" (email + password form with Sign In / Create Account switch)
 */
@Composable
fun AuthScreen(
    initialEmail: String = "",
    authUiState: AuthUiState = AuthUiState(),
    onBack: () -> Unit = {},
    onClearAuthFeedback: () -> Unit = {},
    onGoogleSignIn: (email: String, displayName: String, idToken: String) -> Unit,
    onEmailAuthSubmit: (email: String, password: String, isCreateAccount: Boolean) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current

    var showEmailForm by rememberSaveable { mutableStateOf(false) }
    var isCreateAccount by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var googleBusy by remember { mutableStateOf(false) }

    BackHandler {
        if (showEmailForm) {
            showEmailForm = false
            localError = null
            onClearAuthFeedback()
        } else {
            onBack()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "kutta_sleep_breath")
    val breathAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "kutta_breath"
    )
    val breath = if (reducedMotion) 0f else breathAnim

    fun triggerGoogleSignIn() {
        localError = null
        onClearAuthFeedback()
        val webClientId = runCatching { BuildConfig.GOOGLE_WEB_CLIENT_ID.trim() }.getOrDefault("")
        if (webClientId.isBlank()) {
            localError = "Google Sign-In requires GOOGLE_WEB_CLIENT_ID in AI Studio Secrets. Use Continue with Email below."
            return
        }
        scope.launch {
            googleBusy = true
            runCatching {
                val credentialManager = CredentialManager.create(context)
                val option = GetSignInWithGoogleOption.Builder(webClientId).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(option)
                    .build()
                val response = credentialManager.getCredential(
                    context = context,
                    request = request
                )
                val cred = response.credential
                if (cred is CustomCredential &&
                    cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                    onGoogleSignIn(
                        googleCred.id,
                        googleCred.displayName.orEmpty(),
                        googleCred.idToken
                    )
                } else {
                    localError = "Unable to read Google account credential. Please try Continue with Email."
                }
            }.onFailure { err ->
                localError = when (err) {
                    is GetCredentialCancellationException -> null
                    is NoCredentialException -> "No Google account found on this device. Please use Continue with Email below."
                    else -> err.message?.takeIf { it.isNotBlank() }
                        ?: "Google Sign-In is unavailable on this device. Please use Continue with Email."
                }
            }
            googleBusy = false
        }
    }

    fun submitEmailForm() {
        focusManager.clearFocus()
        onClearAuthFeedback()
        val emailErr = FeeloraAuthValidator.validateEmail(email)
        if (emailErr != null) {
            localError = emailErr
            return
        }
        val passErr = FeeloraAuthValidator.validatePassword(password, isCreateAccount = isCreateAccount)
        if (passErr != null) {
            localError = passErr
            return
        }
        localError = null
        onEmailAuthSubmit(email.trim(), password, isCreateAccount)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("auth_screen")
    ) {
        FeeloraSparkleBackdrop(splitRatio = 0.44f, sparklePhase = 0.18f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .widthIn(max = 520.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar with clean back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .testTag("auth_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Glowing Feelora Heart Logo + Wordmark + Subtitle
            FeeloraLogo(
                emblemSize = 72.dp,
                wordmarkSize = 36.sp,
                showSubtitle = true,
                lightOnDark = true
            )

            Spacer(Modifier.height(14.dp))

            // Overlapping Sleeping KUTTA Puppy perched right on top of the frosted-lavender auth card
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = Color(0xFFF6F3FD).copy(alpha = 0.94f),
                    border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.85f)),
                    shadowElevation = 14.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 66.dp)
                        .shadow(
                            elevation = 18.dp,
                            shape = RoundedCornerShape(30.dp),
                            ambientColor = Color(0xFF4B2E83),
                            spotColor = Color(0xFF4B2E83)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 22.dp, end = 22.dp, top = 46.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Welcome to Feelora",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 23.sp,
                            color = FeeloraHeadingViolet,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Sign in to continue your calm space.",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp,
                            color = FeeloraSlateBody,
                            textAlign = TextAlign.Center
                        )

                        val displayError = localError ?: authUiState.errorMessage
                        if (!displayError.isNullOrBlank()) {
                            FeeloraStatusBanner(
                                message = displayError,
                                tone = FeeloraBannerTone.ERROR,
                                onDismiss = {
                                    localError = null
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

                        Spacer(Modifier.height(2.dp))

                        // Option 1: Continue with Google
                        OutlinedButton(
                            enabled = !authUiState.isLoading && !googleBusy,
                            onClick = ::triggerGoogleSignIn,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, Color(0xFFD9CFF2)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = FeeloraHeadingViolet
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("auth_google_btn")
                        ) {
                            if (googleBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = FeeloraPrimaryCta
                                )
                                Spacer(Modifier.width(10.dp))
                            } else {
                                GoogleBadgeMark()
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(
                                text = "Continue with Google",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.5.sp
                            )
                        }

                        // Option 2: Continue with Email
                        Button(
                            enabled = !authUiState.isLoading && !googleBusy,
                            onClick = {
                                showEmailForm = !showEmailForm
                                localError = null
                                onClearAuthFeedback()
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (showEmailForm) Color(0xFFE7DFFA) else FeeloraPrimaryCta,
                                contentColor = if (showEmailForm) FeeloraHeadingViolet else Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = if (showEmailForm) 0.dp else 4.dp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("auth_email_toggle_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MailOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Continue with Email",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.5.sp
                            )
                        }

                        // Expandable Email + Password Form with Sign In / Create Account Switch
                        AnimatedVisibility(
                            visible = showEmailForm,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            val fieldColors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FeeloraPrimaryCta,
                                unfocusedBorderColor = Color(0xFFD5CAE9),
                                focusedLabelColor = FeeloraPrimaryCta,
                                unfocusedLabelColor = FeeloraSlateBody,
                                focusedTextColor = FeeloraHeadingViolet,
                                unfocusedTextColor = FeeloraHeadingViolet,
                                focusedContainerColor = Color.White.copy(alpha = 0.78f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.65f)
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Sign In / Create Account segmented toggle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFFEAE3FB))
                                        .border(1.dp, Color(0xFFDAD0F4), RoundedCornerShape(50))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(false to "Sign In", true to "Create Account").forEach { (createMode, label) ->
                                        val selected = isCreateAccount == createMode
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .minimumInteractiveComponentSize()
                                                .clip(RoundedCornerShape(50))
                                                .background(if (selected) FeeloraPrimaryCta else Color.Transparent)
                                                .clickable {
                                                    isCreateAccount = createMode
                                                    localError = null
                                                    onClearAuthFeedback()
                                                }
                                                .padding(vertical = 10.dp)
                                                .testTag(if (createMode) "auth_mode_signup" else "auth_mode_signin"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.5.sp,
                                                color = if (selected) Color.White else FeeloraSlateBody
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = email,
                                    onValueChange = {
                                        email = it
                                        localError = null
                                    },
                                    label = { Text("Email address") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.MailOutline,
                                            contentDescription = null,
                                            tint = FeeloraSlateBody
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Next
                                    ),
                                    colors = fieldColors,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_email_input")
                                )

                                OutlinedTextField(
                                    value = password,
                                    onValueChange = {
                                        password = it
                                        localError = null
                                    },
                                    label = { Text("Password") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Lock,
                                            contentDescription = null,
                                            tint = FeeloraSlateBody
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                                tint = FeeloraSlateBody
                                            )
                                        }
                                    },
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { submitEmailForm() }
                                    ),
                                    colors = fieldColors,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_password_input")
                                )

                                Button(
                                    enabled = !authUiState.isLoading && !googleBusy,
                                    onClick = ::submitEmailForm,
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = FeeloraPrimaryCta,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("auth_email_submit_btn")
                                ) {
                                    if (authUiState.isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = if (isCreateAccount) "Create Account" else "Sign In",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.5.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Sleeping KUTTA Puppy sitting right on top of the frosted card edge
                FeeloraSleepingKuttaMascot(
                    width = 148.dp,
                    height = 98.dp,
                    breathingOffset = breath,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(2f)
                        .testTag("auth_sleeping_kutta")
                )
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun GoogleBadgeMark() {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF4285F4), Color(0xFF34A853))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "G",
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            color = Color.White
        )
    }
}
