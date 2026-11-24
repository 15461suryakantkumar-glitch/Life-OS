package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.local.TaskCategory
import com.example.data.local.TaskPriority
import com.example.data.local.UserAvatarPreset
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.RoseAlert
import kotlinx.coroutines.delay

@Composable
fun ObsidianCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color? = null,
    elevated: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(13.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val resolvedBorder = borderColor ?: tokens.borderSubtle
    val shape = RoundedCornerShape(tokens.cardRadius)
    val bgBrush = Brush.linearGradient(
        colors = if (elevated) {
            listOf(tokens.surfaceElevated, tokens.surfaceCard)
        } else {
            listOf(tokens.surfaceCard, tokens.surfaceBase)
        }
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = Color.Transparent,
        shadowElevation = tokens.cardElevation,
        border = BorderStroke(tokens.cardBorderWidth, resolvedBorder)
    ) {
        Column(
            modifier = Modifier
                .background(bgBrush)
                .padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun GradientGlowCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val shape = RoundedCornerShape(tokens.heroRadius)
    val bgBrush = Brush.linearGradient(
        colors = listOf(
            primary.copy(alpha = if (tokens.isDark) 0.22f else 0.12f),
            tokens.surfaceElevated,
            secondary.copy(alpha = if (tokens.isDark) 0.14f else 0.08f)
        )
    )
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            primary.copy(alpha = if (tokens.isDark) 0.55f else 0.42f),
            secondary.copy(alpha = if (tokens.isDark) 0.45f else 0.35f)
        )
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(tokens.cardBorderWidth, borderBrush, shape)
            .then(if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = Color.Transparent,
        shadowElevation = tokens.cardElevation
    ) {
        Column(
            modifier = Modifier
                .background(bgBrush)
                .padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val tokens = LifeOsThemeTokensProvider.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = tokens.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.inkSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun NeonProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    heightDp: Int = 8
) {
    val tokens = LifeOsThemeTokensProvider.current
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 450),
        label = "neon_progress"
    )
    val gradient = Brush.horizontalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary
        )
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(CircleShape)
            .background(tokens.borderSubtle.copy(alpha = 0.7f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(heightDp.dp)
                .clip(CircleShape)
                .background(gradient)
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val tokens = LifeOsThemeTokensProvider.current
    val pillShape = RoundedCornerShape(tokens.pillRadius)
    val bgAlpha = if (tokens.isDark) 0.16f else 0.14f
    val borderAlpha = if (tokens.isDark) 0.35f else 0.42f
    Row(
        modifier = modifier
            .clip(pillShape)
            .background(color.copy(alpha = bgAlpha))
            .border(1.dp, color.copy(alpha = borderAlpha), pillShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PriorityBadge(priority: TaskPriority) {
    val tokens = LifeOsThemeTokensProvider.current
    val color = when (priority) {
        TaskPriority.LOW -> tokens.inkSecondary
        TaskPriority.MEDIUM -> MaterialTheme.colorScheme.secondary
        TaskPriority.HIGH -> AmberWarning
        TaskPriority.CRITICAL -> RoseAlert
    }
    StatusPill(text = priority.label.uppercase(), color = color)
}

@Composable
fun CategoryBadge(category: TaskCategory) {
    val tokens = LifeOsThemeTokensProvider.current
    val color = when (category) {
        TaskCategory.STUDY -> MaterialTheme.colorScheme.primary
        TaskCategory.WORK -> MaterialTheme.colorScheme.secondary
        TaskCategory.HEALTH -> EmeraldSuccess
        TaskCategory.CODE -> AmberWarning
        TaskCategory.PERSONAL -> tokens.inkSecondary
    }
    StatusPill(text = category.label, color = color)
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    accent: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val tokens = LifeOsThemeTokensProvider.current
    ObsidianCard(
        modifier = modifier,
        borderColor = tokens.borderAccent.copy(alpha = 0.5f),
        elevated = true,
        contentPadding = PaddingValues(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tokens.inkSecondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = tokens.inkPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color = MaterialTheme.colorScheme.secondary,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tokens = LifeOsThemeTokensProvider.current
    ObsidianCard(
        modifier = modifier,
        borderColor = accent.copy(alpha = 0.30f),
        elevated = true
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(tokens.smallRadius))
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(tokens.smallRadius)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.inkPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.inkSecondary
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onAction,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
            ) {
                Text(actionLabel, color = tokens.inkPrimary, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
fun AnimatedStreakPill(
    streakDays: Int,
    label: String = "${streakDays}d Streak",
    modifier: Modifier = Modifier
) {
    val tokens = LifeOsThemeTokensProvider.current
    val active = streakDays > 0
    var previousStreak by remember { mutableIntStateOf(streakDays) }
    var burst by remember { mutableStateOf(false) }

    LaunchedEffect(streakDays) {
        if (streakDays > previousStreak) {
            burst = true
            delay(450L)
            burst = false
        }
        previousStreak = streakDays
    }

    val tint by animateColorAsState(
        targetValue = if (active) AmberWarning else tokens.inkMuted,
        animationSpec = tween(320),
        label = "streak_tint"
    )
    val bgColor by animateColorAsState(
        targetValue = if (active) AmberWarning.copy(alpha = 0.16f) else tokens.surfaceElevated,
        animationSpec = tween(320),
        label = "streak_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (active) AmberWarning.copy(alpha = 0.50f) else tokens.borderSubtle,
        animationSpec = tween(320),
        label = "streak_border"
    )
    val burstScale by animateFloatAsState(
        targetValue = if (burst) 1.14f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "streak_burst"
    )

    val flameGlow = if (active) {
        val transition = rememberInfiniteTransition(label = "streak_flame_glow")
        val alpha by transition.animateFloat(
            initialValue = 0.72f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "streak_flame_alpha"
        )
        alpha
    } else {
        0.7f
    }

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = burstScale
                scaleY = burstScale
            }
            .clip(RoundedCornerShape(tokens.pillRadius))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(tokens.pillRadius))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.LocalFireDepartment,
            contentDescription = null,
            tint = tint.copy(alpha = flameGlow),
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun LevelUpBanner(
    level: Int?,
    petName: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LifeOsThemeTokensProvider.current
    AnimatedVisibility(
        visible = level != null,
        enter = fadeIn(tween(260)) + expandVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
        exit = fadeOut(tween(180)) + shrinkVertically(),
        modifier = modifier
    ) {
        val reached = level ?: 1
        GradientGlowCard(onClick = onDismiss) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LEVEL UP · LEVEL $reached",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "$petName's bond deepened! Keep your daily rhythm going.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkPrimary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = tokens.inkSecondary)
                }
            }
        }
    }
}

/**
 * Cute, tactile soft-3D-styled avatar badge for the top-corner Profile trigger and Profile picker.
 */
@Composable
fun UserAvatarBadge(
    preset: UserAvatarPreset,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val (bgTop, bgBottom, faceBase, earAccent) = when (preset) {
        UserAvatarPreset.SUNNY_CLOUD -> listOf(
            Color(0xFFBAE6FD), Color(0xFF38BDF8), Color(0xFFFFFFFF), Color(0xFFFDE047)
        )
        UserAvatarPreset.FOREST_DEER -> listOf(
            Color(0xFFD9F99D), Color(0xFF4D7C0F), Color(0xFFD9A066), Color(0xFF8B5A2B)
        )
        UserAvatarPreset.OCEAN_WHALE -> listOf(
            Color(0xFFA5F3FC), Color(0xFF0284C7), Color(0xFF38BDF8), Color(0xFFE0F2FE)
        )
        UserAvatarPreset.BERRY_CAT -> listOf(
            Color(0xFFFBCFE8), Color(0xFFEC4899), Color(0xFFFFF1F2), Color(0xFFF472B6)
        )
        UserAvatarPreset.STAR_BUNNY -> listOf(
            Color(0xFFDDD6FE), Color(0xFF7C3AED), Color(0xFFFDF4FF), Color(0xFFFBBF24)
        )
        UserAvatarPreset.AMBER_FOX -> listOf(
            Color(0xFFFED7AA), Color(0xFFEA580C), Color(0xFFF97316), Color(0xFFFFF7ED)
        )
    }

    Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .semantics { contentDescription = "${preset.label} avatar" }
    ) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f

        // 3D sphere backdrop
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(bgTop, bgBottom),
                center = Offset(w * 0.34f, h * 0.28f),
                radius = w * 0.85f
            )
        )

        // Character silhouette + cute 3D shading
        when (preset) {
            UserAvatarPreset.SUNNY_CLOUD -> {
                // Warm sun peeking behind cloud
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0xFFFEF08A), earAccent)),
                    radius = w * 0.22f,
                    center = Offset(cx + w * 0.18f, cy - h * 0.14f)
                )
                // Fluffy cloud puffs
                drawCircle(color = faceBase, radius = w * 0.20f, center = Offset(cx - w * 0.14f, cy + h * 0.08f))
                drawCircle(color = faceBase, radius = w * 0.22f, center = Offset(cx + w * 0.12f, cy + h * 0.08f))
                drawCircle(color = faceBase, radius = w * 0.24f, center = Offset(cx, cy - h * 0.02f))
            }
            UserAvatarPreset.OCEAN_WHALE -> {
                // Rounded whale body
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF7DD3FC), faceBase),
                        center = Offset(cx - w * 0.1f, cy - h * 0.1f)
                    ),
                    topLeft = Offset(cx - w * 0.32f, cy - h * 0.22f),
                    size = Size(w * 0.64f, h * 0.48f)
                )
                // Cream belly wave
                drawArc(
                    color = earAccent,
                    startAngle = 15f,
                    sweepAngle = 150f,
                    useCenter = true,
                    topLeft = Offset(cx - w * 0.28f, cy - h * 0.08f),
                    size = Size(w * 0.56f, h * 0.34f)
                )
            }
            else -> {
                // Ears
                val earR = if (preset == UserAvatarPreset.STAR_BUNNY) w * 0.12f else w * 0.14f
                val earH = if (preset == UserAvatarPreset.STAR_BUNNY) h * 0.28f else h * 0.18f
                drawOval(
                    color = earAccent,
                    topLeft = Offset(cx - w * 0.28f, cy - h * 0.38f),
                    size = Size(earR * 1.4f, earH)
                )
                drawOval(
                    color = earAccent,
                    topLeft = Offset(cx + w * 0.28f - earR * 1.4f, cy - h * 0.38f),
                    size = Size(earR * 1.4f, earH)
                )
                // Plump 3D head
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.45f), faceBase),
                        center = Offset(cx - w * 0.10f, cy - h * 0.10f),
                        radius = w * 0.40f
                    ),
                    radius = w * 0.30f,
                    center = Offset(cx, cy + h * 0.06f)
                )
            }
        }

        // Cute eyes & rosy cheeks
        val eyeY = cy + h * 0.04f
        drawCircle(color = Color(0xFF1E293B), radius = w * 0.036f, center = Offset(cx - w * 0.11f, eyeY))
        drawCircle(color = Color(0xFF1E293B), radius = w * 0.036f, center = Offset(cx + w * 0.11f, eyeY))
        drawCircle(color = Color.White, radius = w * 0.013f, center = Offset(cx - w * 0.12f, eyeY - h * 0.01f))
        drawCircle(color = Color.White, radius = w * 0.013f, center = Offset(cx + w * 0.10f, eyeY - h * 0.01f))
        drawCircle(color = Color(0xFFFDA4AF).copy(alpha = 0.65f), radius = w * 0.048f, center = Offset(cx - w * 0.18f, eyeY + h * 0.07f))
        drawCircle(color = Color(0xFFFDA4AF).copy(alpha = 0.65f), radius = w * 0.048f, center = Offset(cx + w * 0.18f, eyeY + h * 0.07f))

        // Tiny smile curve
        val smile = Path().apply {
            moveTo(cx - w * 0.055f, eyeY + h * 0.08f)
            quadraticTo(cx, eyeY + h * 0.14f, cx + w * 0.055f, eyeY + h * 0.08f)
        }
        drawPath(smile, color = Color(0xFF1E293B), style = Stroke(width = w * 0.025f, cap = StrokeCap.Round))

        // Top gloss rim highlight
        drawCircle(
            color = Color.White.copy(alpha = 0.28f),
            radius = w * 0.16f,
            center = Offset(w * 0.28f, h * 0.22f)
        )
    }
}
