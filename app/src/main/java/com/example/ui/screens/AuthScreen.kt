package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.kutta.LocalReducedMotion
import com.example.ui.components.FeeloraBannerTone
import com.example.ui.components.FeeloraLogo
import com.example.ui.components.FeeloraSleepingKuttaMascot
import com.example.ui.components.FeeloraSparkleBackdrop
import com.example.ui.components.FeeloraStatusBanner
import com.example.ui.theme.FeeloraHeadingViolet
import com.example.ui.theme.FeeloraPrimaryCta
import com.example.ui.theme.FeeloraSlateBody
import com.example.ui.theme.FeeloraSoftLavenderPill
import com.example.ui.viewmodel.AuthUiState

object FeeloraAuthValidator {
    fun validateDisplayName(rawName: String): String? {
        val trimmed = rawName.trim()
        if (trimmed.length > 32) {
            return "Display name must be 32 characters or fewer."
        }
        if (trimmed.any { it == '\n' || it == '\r' || it.isISOControl() }) {
            return "Please avoid line breaks or control characters."
        }
        return null
    }

    fun resolveDisplayName(rawName: String, fallback: String = "Friend"): String {
        val cleaned = rawName.trim().take(32)
        return cleaned.ifBlank { fallback.trim().ifBlank { "Friend" } }
    }
}

/**
 * Strictly offline local profile entry screen aligned with the Feelora design board (18894.png):
 * - Midnight-violet (#170F2E -> #4B2E83) to soft lavender-mist (#EFEAF9 -> #F7F5FC) backdrop
 * - Glowing Feelora smiling heart emblem + wordmark
 * - Sleeping golden KUTTA puppy resting on top of the frosted-lavender card
 * - Fast offline local profile entry (no Google Sign-In, no email/password, no internet required)
 */
@Composable
fun AuthScreen(
    initialDisplayName: String = "",
    authUiState: AuthUiState = AuthUiState(),
    onBack: () -> Unit = {},
    onClearAuthFeedback: () -> Unit = {},
    onContinueOffline: (displayName: String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val reducedMotion = LocalReducedMotion.current

    var displayName by rememberSaveable {
        mutableStateOf(initialDisplayName.trim().ifBlank { "Friend" })
    }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler {
        localError = null
        onClearAuthFeedback()
        onBack()
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

    fun submitOfflineEntry() {
        focusManager.clearFocus()
        onClearAuthFeedback()
        val validationError = FeeloraAuthValidator.validateDisplayName(displayName)
        if (validationError != null) {
            localError = validationError
            return
        }
        localError = null
        onContinueOffline(FeeloraAuthValidator.resolveDisplayName(displayName))
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

            FeeloraLogo(
                emblemSize = 72.dp,
                wordmarkSize = 36.sp,
                showSubtitle = true,
                lightOnDark = true
            )

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = Color(0xFFF6F3FD).copy(alpha = 0.96f),
                    border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.88f)),
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
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = FeeloraSoftLavenderPill
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = FeeloraPrimaryCta,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Offline Local Profile · Stored on device",
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp,
                                    color = FeeloraPrimaryCta
                                )
                            }
                        }

                        Text(
                            text = "Welcome to Feelora",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 23.sp,
                            color = FeeloraHeadingViolet,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Choose a local display name to enter your calm space immediately. No account or internet needed.",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
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

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = {
                                displayName = it.take(32)
                                localError = null
                            },
                            label = { Text("Display name") },
                            placeholder = { Text("Friend") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.PersonOutline,
                                    contentDescription = null,
                                    tint = FeeloraSlateBody
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { submitOfflineEntry() }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FeeloraPrimaryCta,
                                unfocusedBorderColor = Color(0xFFD5CAE9),
                                focusedLabelColor = FeeloraPrimaryCta,
                                unfocusedLabelColor = FeeloraSlateBody,
                                focusedTextColor = FeeloraHeadingViolet,
                                unfocusedTextColor = FeeloraHeadingViolet,
                                focusedContainerColor = Color.White.copy(alpha = 0.85f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.72f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_name_input")
                        )

                        // Quick local name chips for 1-tap entry
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Friend", "Explorer", "Student", "Scholar").forEach { preset ->
                                val selected = displayName.trim().equals(preset, ignoreCase = true)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        displayName = preset
                                        localError = null
                                    },
                                    label = {
                                        Text(
                                            text = preset,
                                            fontFamily = FontFamily.SansSerif,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = FeeloraSoftLavenderPill,
                                        selectedLabelColor = FeeloraPrimaryCta,
                                        containerColor = Color.White.copy(alpha = 0.75f),
                                        labelColor = FeeloraSlateBody
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(2.dp))

                        Button(
                            enabled = !authUiState.isLoading,
                            onClick = ::submitOfflineEntry,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FeeloraPrimaryCta,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("auth_enter_btn")
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
                                text = "Enter Feelora  →",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

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
