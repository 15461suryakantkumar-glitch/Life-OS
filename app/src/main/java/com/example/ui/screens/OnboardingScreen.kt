package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.notifications.AppPermissionId
import com.example.notifications.AppPermissionInfo
import com.example.notifications.AppPermissionStatus
import com.example.notifications.PermissionCenterHelper
import com.example.ui.components.FeeloraSparkleBackdrop
import com.example.ui.components.ObsidianCard
import com.example.ui.components.OnboardingSlidePedestalIllustration
import com.example.ui.components.StatusPill
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FeeloraHeadingViolet
import com.example.ui.theme.FeeloraPrimaryCta
import com.example.ui.theme.FeeloraSlateBody
import com.example.ui.theme.FeeloraSoftLavenderPill
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.OutfitFontFamily
import kotlinx.coroutines.launch

data class OnboardingSlideSpec(
    val pageNumber: Int,
    val title: String,
    val description: String,
    val illustrationDescription: String
)

val FeeloraOnboardingSlides: List<OnboardingSlideSpec> = listOf(
    OnboardingSlideSpec(
        pageNumber = 1,
        title = "Manage Your Tasks",
        description = "Stay organized and never miss anything.",
        illustrationDescription = "3D lavender clipboard with checklist and pencil"
    ),
    OnboardingSlideSpec(
        pageNumber = 2,
        title = "Plan Your Study",
        description = "Track subjects, chapters, and study goals.",
        illustrationDescription = "3D stacked study books topped with a graduation cap"
    ),
    OnboardingSlideSpec(
        pageNumber = 3,
        title = "Stay on Schedule",
        description = "Manage deadlines, reminders, and more.",
        illustrationDescription = "3D purple calendar paired with an analog clock"
    ),
    OnboardingSlideSpec(
        pageNumber = 4,
        title = "Take Care of Yourself",
        description = "Track water, mood, and wellness.",
        illustrationDescription = "3D clear water glass with droplet and glowing heart"
    ),
    OnboardingSlideSpec(
        pageNumber = 5,
        title = "Meet SURU",
        description = "Your intelligent AI assistant.",
        illustrationDescription = "3D glossy white and lavender SURU companion robot"
    ),
    OnboardingSlideSpec(
        pageNumber = 6,
        title = "Your Companion KUTTA",
        description = "Always by your side.",
        illustrationDescription = "Sleeping golden KUTTA puppy surrounded by floating hearts"
    ),
    OnboardingSlideSpec(
        pageNumber = 7,
        title = "Together One",
        description = "Grow together, achieve more.",
        illustrationDescription = "Two companions facing each other beneath a glowing heart"
    )
)

/**
 * Feelora 7-Slide Onboarding Pager (1/7 to 7/7) matching the attached Feelora design mockup:
 * - Midnight-violet top half with twinkling sparkles + glowing pedestal 3D-styled illustrations
 * - Smooth transition to soft lavender-mist lower canvas
 * - Dark violet headings (#1F1638), muted slate descriptions (#5A5270), 7 progress dots
 * - Slide 1/7: Skip | Next ->
 * - Slides 2/7..6/7: <- Back | Next ->
 * - Slide 7/7: <- Back | Get Started (#6C3CE9 solid pill)
 */
@Composable
fun OnboardingScreen(
    initialPage: Int = 0,
    onBackToSplash: () -> Unit = {},
    onSkip: () -> Unit,
    onComplete: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, FeeloraOnboardingSlides.lastIndex),
        pageCount = { FeeloraOnboardingSlides.size }
    )
    val scope = rememberCoroutineScope()

    BackHandler {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        } else {
            onBackToSplash()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("onboarding_screen")
    ) {
        FeeloraSparkleBackdrop(splitRatio = 0.50f, sparklePhase = pagerState.currentPage * 0.14f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 540.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIdx ->
                val slide = FeeloraOnboardingSlides[pageIdx]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .testTag("onboarding_page_$pageIdx"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.weight(0.28f))

                    OnboardingSlidePedestalIllustration(
                        pageIndex = pageIdx,
                        contentDescriptionText = slide.illustrationDescription,
                        modifier = Modifier.weight(2.15f)
                    )

                    Spacer(Modifier.weight(0.25f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = slide.title,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 27.sp,
                            lineHeight = 32.sp,
                            color = FeeloraHeadingViolet,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = slide.description,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.5.sp,
                            lineHeight = 22.sp,
                            color = FeeloraSlateBody,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 290.dp)
                        )
                    }

                    Spacer(Modifier.weight(0.42f))
                }
            }

            // 7 Progress Dots
            Row(
                modifier = Modifier
                    .padding(bottom = 22.dp)
                    .testTag("onboarding_progress_dots"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(FeeloraOnboardingSlides.size) { idx ->
                    val isCurrent = idx == pagerState.currentPage
                    val dotWidth by animateDpAsState(
                        targetValue = if (isCurrent) 20.dp else 8.dp,
                        label = "dot_width_$idx"
                    )
                    val dotColor by animateColorAsState(
                        targetValue = if (isCurrent) FeeloraPrimaryCta else Color(0xFFCBBFE8),
                        label = "dot_color_$idx"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(dotColor)
                            .clickable {
                                scope.launch { pagerState.animateScrollToPage(idx) }
                            }
                    )
                }
            }

            // Bottom Action Row matching mockup
            val currentPage = pagerState.currentPage
            val isFirst = currentPage == 0
            val isLast = currentPage == FeeloraOnboardingSlides.lastIndex

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (isFirst) {
                            onSkip()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
                        }
                    },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Color(0xFFD9CFF2)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White.copy(alpha = 0.72f),
                        contentColor = Color(0xFF423663)
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(0.88f)
                        .height(50.dp)
                        .testTag(if (isFirst) "onboarding_skip_btn" else "onboarding_back_btn")
                ) {
                    Text(
                        text = if (isFirst) "Skip" else "←  Back",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Button(
                    onClick = {
                        if (isLast) {
                            onComplete()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                        }
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLast) FeeloraPrimaryCta else FeeloraSoftLavenderPill,
                        contentColor = if (isLast) Color.White else FeeloraHeadingViolet
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = if (isLast) 6.dp else 1.dp
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1.12f)
                        .height(50.dp)
                        .testTag(if (isLast) "onboarding_get_started_btn" else "onboarding_next_btn")
                ) {
                    Text(
                        text = if (isLast) "Get Started" else "Next  →",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.5.sp,
                        maxLines = 1,
                        softWrap = false
                    )
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
    val tokens = LifeOsThemeTokensProvider.current

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
