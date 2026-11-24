package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.UserAvatarPreset
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.InfoSky
import com.example.ui.theme.LifeOsThemeTokensProvider
import com.example.ui.theme.RoseAlert

@Composable
fun FeeloraScreenBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val backdropBrush = Brush.verticalGradient(
        colors = if (tokens.isDark) {
            listOf(
                tokens.background,
                tokens.surfaceBase.copy(alpha = 0.92f),
                tokens.background
            )
        } else {
            listOf(
                tokens.background,
                tokens.surfaceElevated.copy(alpha = 0.35f),
                tokens.background
            )
        }
    )
    Box(
        modifier = modifier
            .background(backdropBrush)
    ) {
        content()
    }
}

@Composable
fun FeeloraTopHeader(
    title: String,
    subtitle: String? = null,
    userName: String = "",
    avatarPreset: UserAvatarPreset = UserAvatarPreset.STAR_BUNNY,
    avatarUri: String? = null,
    unreadNotifications: Int = 0,
    onOpenDrawer: (() -> Unit)? = null,
    onOpenNotifications: (() -> Unit)? = null,
    onOpenProfile: (() -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null
) {
    val tokens = LifeOsThemeTokensProvider.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onOpenDrawer != null) {
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("top_bar_menu_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Open navigation menu",
                    tint = tokens.inkPrimary
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = if (onOpenDrawer == null) 4.dp else 2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = tokens.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailingAction != null) {
            trailingAction()
        }
        if (onOpenNotifications != null) {
            Box(contentAlignment = Alignment.TopEnd) {
                IconButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("top_bar_notifications_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsNone,
                        contentDescription = "Notifications",
                        tint = tokens.inkPrimary
                    )
                }
                if (unreadNotifications > 0) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp, end = 8.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(tokens.deepPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (unreadNotifications > 9) "9+" else "$unreadNotifications",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        if (onOpenProfile != null) {
            IconButton(
                onClick = onOpenProfile,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("top_bar_profile_btn")
            ) {
                UserAvatarBadge(
                    preset = avatarPreset,
                    photoUri = avatarUri,
                    displayName = userName,
                    size = 34.dp
                )
            }
        }
    }
}

@Composable
fun FeeloraSubpageHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    val tokens = LifeOsThemeTokensProvider.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .testTag("subpage_back_btn")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Navigate back",
                tint = tokens.inkPrimary
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = tokens.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

enum class FeeloraBannerTone { INFO, SUCCESS, WARNING, ERROR }

@Composable
fun FeeloraStatusBanner(
    message: String,
    tone: FeeloraBannerTone = FeeloraBannerTone.INFO,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tokens = LifeOsThemeTokensProvider.current
    val (accent, icon) = when (tone) {
        FeeloraBannerTone.INFO -> InfoSky to Icons.Filled.Info
        FeeloraBannerTone.SUCCESS -> EmeraldSuccess to Icons.Filled.Check
        FeeloraBannerTone.WARNING -> AmberWarning to Icons.Filled.Info
        FeeloraBannerTone.ERROR -> RoseAlert to Icons.Filled.ErrorOutline
    }
    val shape = RoundedCornerShape(tokens.smallRadius)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = accent.copy(alpha = if (tokens.isDark) 0.14f else 0.11f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.42f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.inkPrimary,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(actionLabel, color = accent, fontWeight = FontWeight.SemiBold)
                }
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss banner",
                        tint = tokens.inkSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun <T> FeeloraSegmentedRow(
    items: List<T>,
    selectedItem: T,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    iconFor: ((T) -> ImageVector?)? = null,
    tagPrefix: String = "segment"
) {
    val tokens = LifeOsThemeTokensProvider.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            val selected = item == selectedItem
            val label = labelFor(item)
            val icon = iconFor?.invoke(item)
            val pillShape = RoundedCornerShape(tokens.pillRadius)
            Row(
                modifier = Modifier
                    .clip(pillShape)
                    .background(
                        when {
                            selected && !tokens.isDark -> tokens.surfaceHighlight
                            selected -> tokens.primary.copy(alpha = 0.28f)
                            else -> tokens.surfaceCard
                        }
                    )
                    .border(
                        1.dp,
                        if (selected) tokens.primary else tokens.borderSubtle,
                        pillShape
                    )
                    .clickable { onSelect(item) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
                    .testTag("${tagPrefix}_${label.lowercase().replace(" ", "_")}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) tokens.deepPrimary else tokens.inkSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) tokens.inkPrimary else tokens.inkSecondary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun FeeloraToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    testTag: String? = null
) {
    val tokens = LifeOsThemeTokensProvider.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.smallRadius))
            .background(tokens.surfaceElevated.copy(alpha = 0.72f))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) tokens.inkPrimary else tokens.inkMuted
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.inkSecondary
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
            colors = SwitchDefaults.colors(
                checkedThumbColor = tokens.background,
                checkedTrackColor = accent
            )
        )
    }
}

@Composable
fun FeeloraLoadingCard(
    title: String,
    subtitle: String = "Syncing with Feelora...",
    modifier: Modifier = Modifier
) {
    val tokens = LifeOsThemeTokensProvider.current
    ObsidianCard(modifier = modifier, elevated = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = tokens.inkPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.inkSecondary)
            }
        }
    }
}

@Composable
fun FeeloraConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceCard,
        titleContentColor = tokens.inkPrimary,
        textContentColor = tokens.inkSecondary,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) RoseAlert else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
