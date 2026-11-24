package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.notifications.AppPermissionId
import com.example.notifications.AppPermissionInfo
import com.example.notifications.AppPermissionStatus
import com.example.notifications.PermissionCenterHelper
import com.example.ui.components.ObsidianCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LifeOsThemeTokensProvider

@Composable
fun PermissionsScreen(
    permissions: List<AppPermissionInfo>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onShowMessage: (String) -> Unit = {}
) {
    val tokens = LifeOsThemeTokensProvider.current
    val context = LocalContext.current

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onRefresh()
        if (!granted) {
            onShowMessage("Permission denied — you can grant it anytime from Android App Settings.")
        }
    }

    fun launchSettingsSafely(intent: Intent) {
        val launched = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (!launched) {
            runCatching {
                context.startActivity(PermissionCenterHelper.buildAppDetailsSettingsIntent(context))
            }.onFailure {
                onShowMessage("Could not open Android system settings on this device.")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
            .testTag("permissions_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).testTag("permissions_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Settings",
                    tint = tokens.inkPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Permissions & Privacy",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.inkPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Live Android access status & transparency",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.inkSecondary
                )
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(48.dp).testTag("permissions_refresh_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh permissions",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ObsidianCard(borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "FEELORA Privacy Standard",
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.inkPrimary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "• Camera & Microphone activate only when you explicitly tap them.\n" +
                            "• Photo selection uses the zero-permission Android Photo Picker.\n" +
                            "• Private Cycle Care logs and personal journal entries never sync or go to AI prompts.\n" +
                            "• API credentials stay outside app source code via AI Studio Secrets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkSecondary
                    )
                }
            }

            items(permissions, key = { it.id.name }) { info ->
                PermissionRowCard(
                    info = info,
                    icon = iconForPermission(info.id),
                    onRequestRuntime = { perm ->
                        runtimePermissionLauncher.launch(perm)
                    },
                    onOpenSettings = {
                        val intent = when (info.id) {
                            AppPermissionId.NOTIFICATIONS -> PermissionCenterHelper.buildNotificationSettingsIntent(context)
                            AppPermissionId.USAGE_ACCESS -> PermissionCenterHelper.buildUsageAccessSettingsIntent()
                            AppPermissionId.ALARMS_REMINDERS -> PermissionCenterHelper.buildExactAlarmSettingsIntent(context)
                            else -> PermissionCenterHelper.buildAppDetailsSettingsIntent(context)
                        }
                        launchSettingsSafely(intent)
                    }
                )
            }

            item {
                OutlinedButton(
                    onClick = {
                        launchSettingsSafely(PermissionCenterHelper.buildAppDetailsSettingsIntent(context))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("permissions_app_details_btn")
                ) {
                    Text("Open Android App Info Settings", maxLines = 1, softWrap = false)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PermissionRowCard(
    info: AppPermissionInfo,
    icon: ImageVector,
    onRequestRuntime: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val tokens = LifeOsThemeTokensProvider.current
    val pillColor: Color = when (info.status) {
        AppPermissionStatus.GRANTED -> EmeraldSuccess
        AppPermissionStatus.NOT_GRANTED -> AmberWarning
        AppPermissionStatus.NOT_REQUIRED -> MaterialTheme.colorScheme.primary
        AppPermissionStatus.UNAVAILABLE -> tokens.inkMuted
    }

    ObsidianCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permission_card_${info.id.name}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(icon, contentDescription = null, tint = pillColor, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.inkPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(text = info.status.label, color = pillColor)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = info.purpose,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.inkSecondary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = info.statusDetail,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.inkMuted
        )

        if (info.status != AppPermissionStatus.GRANTED &&
            info.status != AppPermissionStatus.UNAVAILABLE &&
            (info.isRuntimeRequestable || info.opensSystemSettings)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (info.isRuntimeRequestable && info.runtimePermission != null) {
                    Button(
                        onClick = { onRequestRuntime(info.runtimePermission) },
                        modifier = Modifier.testTag("permission_grant_${info.id.name}")
                    ) {
                        Text("Allow", maxLines = 1, softWrap = false)
                    }
                    if (info.opensSystemSettings) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onOpenSettings) {
                            Text("Settings", maxLines = 1, softWrap = false)
                        }
                    }
                } else if (info.opensSystemSettings) {
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("permission_settings_${info.id.name}")
                    ) {
                        Text("Open Settings", maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

private fun iconForPermission(id: AppPermissionId): ImageVector = when (id) {
    AppPermissionId.NOTIFICATIONS -> Icons.Filled.NotificationsActive
    AppPermissionId.USAGE_ACCESS -> Icons.Filled.PhoneAndroid
    AppPermissionId.ALARMS_REMINDERS -> Icons.Filled.Schedule
    AppPermissionId.CAMERA -> Icons.Filled.CameraAlt
    AppPermissionId.MICROPHONE -> Icons.Filled.Mic
    AppPermissionId.PHOTOS -> Icons.Filled.PhotoLibrary
    AppPermissionId.INTERNET -> Icons.Filled.CloudQueue
}
