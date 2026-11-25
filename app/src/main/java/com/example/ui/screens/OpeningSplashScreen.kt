package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.audio.FeeloraOpeningChimePlayer
import com.example.kutta.LocalReducedMotion
import com.example.ui.components.FeeloraHeartEmblem
import com.example.ui.components.FeeloraSparkleBackdrop
import com.example.ui.components.drawFourPointStar
import com.example.ui.theme.CaveatFontFamily
import com.example.ui.theme.FeeloraGlowLavender
import com.example.ui.theme.FeeloraHeadingViolet
import com.example.ui.theme.FeeloraWarmSunsetGold
import com.example.ui.theme.FeeloraWarmSunsetPeach
import com.example.ui.theme.OutfitFontFamily
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Interactive Feelora Opening / Splash Screen matching the attached design mockup:
 * - Midnight-violet gradient with twinkling stars & soft glow
 * - Interactive circling/orbital motion graphics around FeeloraHeartEmblem (reacts to touch/drag/tap)
 * - Gentle ambient chime sound on launch/tap with mute toggle & safe SoundPool fallback
 * - "Feelora" bold wordmark, "Feel · Heal · Grow", handwritten "Your safe space for a happier mind ♡"
 * - Sunset girl-and-puppy hero illustration card
 * - White pill "Get Started →" button + subtle "Sign in" button
 */
@Composable
fun OpeningSplashScreen(
    initialMuted: Boolean = false,
    onToggleAudioMute: (Boolean) -> Unit = {},
    onGetStarted: () -> Unit,
    onSignIn: () -> Unit
) {
    val context = LocalContext.current
    val reducedMotion = LocalReducedMotion.current
    val coroutineScope = rememberCoroutineScope()

    var isMuted by remember(initialMuted) { mutableStateOf(initialMuted) }
    val chimePlayer = remember(context) { FeeloraOpeningChimePlayer(context.applicationContext) }

    LaunchedEffect(isMuted) {
        chimePlayer.isMuted = isMuted
    }

    LaunchedEffect(Unit) {
        chimePlayer.isMuted = isMuted
        chimePlayer.prepare(autoPlayWhenReady = !isMuted)
    }

    DisposableEffect(chimePlayer) {
        onDispose { chimePlayer.release() }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splash_orbital_motion")
    val orbitProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (reducedMotion) 24000 else 11000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_progress"
    )
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    // Interactive touch/drag/tap motion state around the Feelora emblem
    val dragTiltX = remember { Animatable(0f) }
    val dragTiltY = remember { Animatable(0f) }
    val tapBounce = remember { Animatable(1f) }
    val tapRipple = remember { Animatable(0f) }
    var interactiveOrbitBoost by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("opening_splash_screen")
    ) {
        FeeloraSparkleBackdrop(
            fullDarkMode = true,
            sparklePhase = if (reducedMotion) 0.1f else orbitProgress
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 520.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar with subtle audio mute/unmute pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    IconButton(
                        onClick = {
                            val nextMuted = !isMuted
                            isMuted = nextMuted
                            chimePlayer.isMuted = nextMuted
                            onToggleAudioMute(nextMuted)
                            if (!nextMuted) {
                                chimePlayer.playTapSparkle()
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("splash_mute_btn")
                    ) {
                        Icon(
                            imageVector = if (isMuted) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = if (isMuted) "Unmute opening chime" else "Mute opening chime",
                            tint = Color(0xFFEDE5FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Interactive Orbital Feelora Logo Zone
            Box(
                modifier = Modifier
                    .size(164.dp)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            if (!isMuted) {
                                chimePlayer.playTapSparkle()
                            }
                            interactiveOrbitBoost += 0.22f
                            coroutineScope.launch {
                                tapBounce.snapTo(0.92f)
                                tapBounce.animateTo(
                                    targetValue = 1.15f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                                tapBounce.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                                )
                            }
                            coroutineScope.launch {
                                tapRipple.snapTo(0.01f)
                                tapRipple.animateTo(1f, tween(760, easing = FastOutSlowInEasing))
                                tapRipple.snapTo(0f)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    dragTiltX.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 220f))
                                }
                                coroutineScope.launch {
                                    dragTiltY.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 220f))
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch { dragTiltX.animateTo(0f, spring()) }
                                coroutineScope.launch { dragTiltY.animateTo(0f, spring()) }
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            interactiveOrbitBoost += (dragAmount.x + dragAmount.y) * 0.0022f
                            coroutineScope.launch {
                                dragTiltX.snapTo((dragTiltX.value + dragAmount.x * 0.35f).coerceIn(-28f, 28f))
                                dragTiltY.snapTo((dragTiltY.value + dragAmount.y * 0.35f).coerceIn(-28f, 28f))
                            }
                        }
                    }
                    .testTag("splash_interactive_logo"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width * 0.5f + dragTiltX.value * 0.45f
                    val cy = size.height * 0.5f + dragTiltY.value * 0.45f
                    val baseAngle = ((orbitProgress + interactiveOrbitBoost) * 2f * PI).toFloat()

                    // Soft radial backdrop glow behind logo
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFB58CFF).copy(alpha = 0.36f * pulseGlow),
                                Color(0xFF7B4BE0).copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = size.minDimension * 0.52f
                        ),
                        radius = size.minDimension * 0.52f,
                        center = Offset(cx, cy)
                    )

                    // Tap ripple ring
                    val ripple = tapRipple.value
                    if (ripple > 0f) {
                        drawCircle(
                            color = FeeloraGlowLavender.copy(alpha = (1f - ripple) * 0.58f),
                            radius = size.minDimension * (0.28f + ripple * 0.24f),
                            center = Offset(cx, cy),
                            style = Stroke(width = 3.5f * (1f - ripple * 0.5f))
                        )
                    }

                    // Two tilted elliptical orbital paths
                    val ringRadiusX = size.width * 0.43f
                    val ringRadiusY = size.height * 0.23f
                    val tiltDegA = -22f + dragTiltY.value * 0.45f
                    val tiltDegB = 26f - dragTiltX.value * 0.45f

                    rotate(degrees = tiltDegA, pivot = Offset(cx, cy)) {
                        drawOval(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.05f),
                                    FeeloraGlowLavender.copy(alpha = 0.42f),
                                    Color.White.copy(alpha = 0.55f),
                                    Color.White.copy(alpha = 0.05f)
                                ),
                                center = Offset(cx, cy)
                            ),
                            topLeft = Offset(cx - ringRadiusX, cy - ringRadiusY),
                            size = Size(ringRadiusX * 2f, ringRadiusY * 2f),
                            style = Stroke(width = 2.2f)
                        )
                    }

                    rotate(degrees = tiltDegB, pivot = Offset(cx, cy)) {
                        drawOval(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.04f),
                                    FeeloraWarmSunsetGold.copy(alpha = 0.38f),
                                    FeeloraGlowLavender.copy(alpha = 0.35f),
                                    Color.White.copy(alpha = 0.04f)
                                ),
                                center = Offset(cx, cy)
                            ),
                            topLeft = Offset(cx - ringRadiusX * 0.92f, cy - ringRadiusY * 0.94f),
                            size = Size(ringRadiusX * 1.84f, ringRadiusY * 1.88f),
                            style = Stroke(width = 1.8f)
                        )
                    }

                    // Orbiting luminous particles on both orbits
                    val particlesA = listOf(0f, 2.1f, 4.2f)
                    val radA = tiltDegA * (PI.toFloat() / 180f)
                    particlesA.forEachIndexed { idx, offset ->
                        val theta = baseAngle + offset
                        val lx = ringRadiusX * cos(theta)
                        val ly = ringRadiusY * sin(theta)
                        val px = cx + lx * cos(radA) - ly * sin(radA)
                        val py = cy + lx * sin(radA) + ly * cos(radA)
                        if (idx == 0) {
                            drawFourPointStar(
                                center = Offset(px, py),
                                outerRadius = 7.5f,
                                color = Color.White.copy(alpha = 0.92f)
                            )
                        } else {
                            drawCircle(
                                color = if (idx == 1) FeeloraGlowLavender else FeeloraWarmSunsetGold,
                                radius = if (idx == 1) 4.5f else 3.6f,
                                center = Offset(px, py)
                            )
                        }
                    }

                    val particlesB = listOf(1.1f, 3.3f, 5.2f)
                    val radB = tiltDegB * (PI.toFloat() / 180f)
                    particlesB.forEachIndexed { idx, offset ->
                        val theta = -baseAngle * 0.85f + offset
                        val lx = ringRadiusX * 0.92f * cos(theta)
                        val ly = ringRadiusY * 0.94f * sin(theta)
                        val px = cx + lx * cos(radB) - ly * sin(radB)
                        val py = cy + lx * sin(radB) + ly * cos(radB)
                        drawCircle(
                            color = if (idx % 2 == 0) Color.White.copy(alpha = 0.85f) else Color(0xFFD0B6FF),
                            radius = if (idx == 0) 4.8f else 3.4f,
                            center = Offset(px, py)
                        )
                    }
                }

                FeeloraHeartEmblem(
                    size = 96.dp,
                    glowIntensity = pulseGlow,
                    modifier = Modifier.graphicsLayer {
                        scaleX = tapBounce.value
                        scaleY = tapBounce.value
                        translationX = dragTiltX.value * 0.25f
                        translationY = dragTiltY.value * 0.25f
                    }
                )
            }

            // Bold "Feelora" wordmark + "Feel · Heal · Grow"
            Text(
                text = "Feelora",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 42.sp,
                letterSpacing = (-0.6).sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Feel  ·  Heal  ·  Grow",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                letterSpacing = 1.6.sp,
                color = Color(0xFFE7DFFF),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(14.dp))

            // Handwritten-style tagline with subtle curved underline
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Your safe space\nfor a happier mind ♡",
                    fontFamily = CaveatFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 25.sp,
                    lineHeight = 28.sp,
                    color = Color(0xFFF6F1FF),
                    textAlign = TextAlign.Center
                )
                Canvas(
                    modifier = Modifier
                        .width(156.dp)
                        .height(10.dp)
                ) {
                    val path = Path().apply {
                        moveTo(size.width * 0.08f, size.height * 0.72f)
                        quadraticTo(
                            size.width * 0.50f, size.height * 0.10f,
                            size.width * 0.94f, size.height * 0.48f
                        )
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFFDCC9FF).copy(alpha = 0.72f),
                        style = Stroke(width = 3f, cap = StrokeCap.Round)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Sunset Girl-and-Puppy Hero Illustration Card
            SunsetGirlAndPuppyHeroCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(198.dp)
            )

            Spacer(Modifier.weight(1f, fill = true))
            Spacer(Modifier.height(20.dp))

            // White pill CTA "Get Started →"
            Button(
                onClick = {
                    if (!isMuted) chimePlayer.playTapSparkle()
                    onGetStarted()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = FeeloraHeadingViolet
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .height(54.dp)
                    .testTag("splash_get_started_btn")
            ) {
                Text(
                    text = "Get Started  →",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = FeeloraHeadingViolet
                )
            }

            Spacer(Modifier.height(8.dp))

            // Subtle "Local Profile" button below
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        if (!isMuted) chimePlayer.playTapSparkle()
                        onSignIn()
                    }
                    .padding(horizontal = 24.dp, vertical = 10.dp)
                    .testTag("splash_sign_in_btn"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Local Profile",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = Color(0xFFF6F2FF)
                )
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SunsetGirlAndPuppyHeroCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF392368))
            .semantics { contentDescription = "Warm sunset illustration of a girl sitting with her golden puppy" }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Sunset twilight sky gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF472A7B),
                        0.34f to Color(0xFF7B4A9E),
                        0.62f to Color(0xFFD67FA2),
                        0.84f to FeeloraWarmSunsetPeach,
                        1.0f to FeeloraWarmSunsetGold
                    )
                )
            )

            // Glowing setting sun orb on horizon
            val sunCenter = Offset(w * 0.62f, h * 0.72f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFF5D4),
                        FeeloraWarmSunsetGold.copy(alpha = 0.80f),
                        FeeloraWarmSunsetPeach.copy(alpha = 0.25f),
                        Color.Transparent
                    ),
                    center = sunCenter,
                    radius = w * 0.42f
                ),
                radius = w * 0.42f,
                center = sunCenter
            )

            // Soft clouds tinted lavender-gold
            drawOval(
                color = Color(0x44FFE5C8),
                topLeft = Offset(w * 0.08f, h * 0.16f),
                size = Size(w * 0.36f, h * 0.11f)
            )
            drawOval(
                color = Color(0x38FFD0C4),
                topLeft = Offset(w * 0.60f, h * 0.22f),
                size = Size(w * 0.34f, h * 0.10f)
            )

            // Distant purple mountain ridge
            val farHill = Path().apply {
                moveTo(0f, h * 0.76f)
                quadraticTo(w * 0.22f, h * 0.62f, w * 0.48f, h * 0.73f)
                quadraticTo(w * 0.76f, h * 0.82f, w, h * 0.67f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = farHill,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF63408C), Color(0xFF41256A))
                )
            )

            // Foreground meadow hill
            val meadow = Path().apply {
                moveTo(0f, h * 0.82f)
                quadraticTo(w * 0.35f, h * 0.75f, w * 0.70f, h * 0.81f)
                quadraticTo(w * 0.88f, h * 0.84f, w, h * 0.79f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = meadow,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF3A225C), Color(0xFF25143E))
                )
            )

            // Girl seated on the left gazing toward sunset
            // Cozy lavender sweater torso
            val sweaterPath = Path().apply {
                moveTo(w * 0.26f, h * 0.96f)
                cubicTo(w * 0.26f, h * 0.69f, w * 0.31f, h * 0.56f, w * 0.40f, h * 0.56f)
                cubicTo(w * 0.49f, h * 0.56f, w * 0.52f, h * 0.72f, w * 0.51f, h * 0.96f)
                close()
            }
            drawPath(
                path = sweaterPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFCBB6F8), Color(0xFF8C6CC7)),
                    start = Offset(w * 0.30f, h * 0.56f),
                    end = Offset(w * 0.50f, h * 0.94f)
                )
            )

            // Girl's head & flowing dark violet-brown hair (back view)
            drawCircle(
                color = Color(0xFFF3CBB4),
                radius = w * 0.068f,
                center = Offset(w * 0.405f, h * 0.455f)
            )
            val hairPath = Path().apply {
                moveTo(w * 0.335f, h * 0.45f)
                cubicTo(w * 0.33f, h * 0.30f, w * 0.48f, h * 0.30f, w * 0.478f, h * 0.46f)
                cubicTo(w * 0.485f, h * 0.62f, w * 0.455f, h * 0.76f, w * 0.395f, h * 0.80f)
                cubicTo(w * 0.325f, h * 0.78f, w * 0.305f, h * 0.63f, w * 0.335f, h * 0.45f)
                close()
            }
            drawPath(
                path = hairPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF28193E), Color(0xFF422959))
                )
            )

            // Golden puppy (KUTTA) sitting closely on the girl's right
            // Puppy body
            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFDFB0), Color(0xFFE1A25A))
                ),
                topLeft = Offset(w * 0.525f, h * 0.64f),
                size = Size(w * 0.155f, h * 0.28f)
            )
            // Puppy tail curving outward
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFAD199), Color(0xFFD59148))
                ),
                topLeft = Offset(w * 0.645f, h * 0.79f),
                size = Size(w * 0.085f, h * 0.075f)
            )
            // Puppy head & floppy golden ears (gazing softly toward sunset)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFEBC7), Color(0xFFECBA76)),
                    center = Offset(w * 0.60f, h * 0.55f),
                    radius = w * 0.08f
                ),
                radius = w * 0.074f,
                center = Offset(w * 0.602f, h * 0.575f)
            )
            drawOval(
                color = Color(0xFFD7924A),
                topLeft = Offset(w * 0.522f, h * 0.535f),
                size = Size(w * 0.048f, h * 0.115f)
            )
            drawOval(
                color = Color(0xFFD7924A),
                topLeft = Offset(w * 0.635f, h * 0.535f),
                size = Size(w * 0.048f, h * 0.115f)
            )

            // Girl's arm gently resting on KUTTA's shoulder
            drawLine(
                color = Color(0xFFB59AE8),
                start = Offset(w * 0.45f, h * 0.68f),
                end = Offset(w * 0.555f, h * 0.70f),
                strokeWidth = w * 0.042f,
                cap = StrokeCap.Round
            )

            // Tiny foreground meadow wildflowers & soft sparkles
            listOf(
                Offset(w * 0.12f, h * 0.90f) to Color(0xFFFFD6DF),
                Offset(w * 0.18f, h * 0.93f) to Color(0xFFE6D4FF),
                Offset(w * 0.78f, h * 0.91f) to Color(0xFFFFE7B0),
                Offset(w * 0.86f, h * 0.88f) to Color(0xFFE6D4FF)
            ).forEach { (pt, col) ->
                drawCircle(color = col.copy(alpha = 0.85f), radius = 4.2f, center = pt)
            }
            drawFourPointStar(Offset(w * 0.22f, h * 0.25f), 5.5f, Color.White.copy(alpha = 0.65f))
            drawFourPointStar(Offset(w * 0.82f, h * 0.18f), 6.5f, Color.White.copy(alpha = 0.75f))
        }

        // Subtle lavender mist edge vignette at bottom of illustration
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.72f to Color.Transparent,
                            1.0f to Color(0x44321D5C)
                        )
                    )
                )
        )
    }
}
