package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.data.local.AndroidUsageStatsBridge

enum class AppPermissionId {
    NOTIFICATIONS,
    USAGE_ACCESS,
    ALARMS_REMINDERS,
    CAMERA,
    MICROPHONE,
    PHOTOS,
    INTERNET
}

enum class AppPermissionStatus(val label: String) {
    GRANTED("Granted"),
    NOT_GRANTED("Not granted"),
    NOT_REQUIRED("Not required"),
    UNAVAILABLE("Unavailable")
}

data class AppPermissionInfo(
    val id: AppPermissionId,
    val title: String,
    val purpose: String,
    val status: AppPermissionStatus,
    val statusDetail: String,
    val isRuntimeRequestable: Boolean = false,
    val runtimePermission: String? = null,
    val opensSystemSettings: Boolean = false
)

object PermissionCenterHelper {

    fun collectAllPermissions(context: Context): List<AppPermissionInfo> {
        return listOf(
            checkNotifications(context),
            checkUsageAccess(context),
            checkAlarmsAndReminders(context),
            checkCamera(context),
            checkMicrophone(context),
            checkPhotos(),
            checkInternet(context)
        )
    }

    fun checkNotifications(context: Context): AppPermissionInfo {
        val runtimeGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val channelEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val granted = runtimeGranted && channelEnabled
        val status = when {
            granted -> AppPermissionStatus.GRANTED
            else -> AppPermissionStatus.NOT_GRANTED
        }
        val detail = when {
            granted -> "Enabled for 2-hour smart task nudges and briefings."
            !runtimeGranted -> "Allow notifications for task reminders and study alerts."
            else -> "Notifications are turned off in Android system settings."
        }
        return AppPermissionInfo(
            id = AppPermissionId.NOTIFICATIONS,
            title = "Notifications",
            purpose = "2-hour task reminders, morning briefings, and gentle study alerts.",
            status = status,
            statusDetail = detail,
            isRuntimeRequestable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !runtimeGranted,
            runtimePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.POST_NOTIFICATIONS
            } else {
                null
            },
            opensSystemSettings = !channelEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        )
    }

    fun checkUsageAccess(context: Context): AppPermissionInfo {
        val bridge = AndroidUsageStatsBridge(context)
        val granted = bridge.hasSystemUsagePermission()
        val hasSettingsTarget = resolveIntentAvailable(context, buildUsageAccessSettingsIntent())
        val status = when {
            granted -> AppPermissionStatus.GRANTED
            !hasSettingsTarget -> AppPermissionStatus.UNAVAILABLE
            else -> AppPermissionStatus.NOT_GRANTED
        }
        val detail = when (status) {
            AppPermissionStatus.GRANTED -> "System screen time sync is active on-device."
            AppPermissionStatus.UNAVAILABLE -> "Usage Access screen is unavailable on this Android build; manual logging works."
            AppPermissionStatus.NOT_GRANTED -> "Optional — enables automatic app screen-time tracking in Wellness OS."
            AppPermissionStatus.NOT_REQUIRED -> ""
        }
        return AppPermissionInfo(
            id = AppPermissionId.USAGE_ACCESS,
            title = "Digital Wellbeing Usage Access",
            purpose = "Reads daily app usage totals privately on your device for Wellness insights.",
            status = status,
            statusDetail = detail,
            opensSystemSettings = hasSettingsTarget
        )
    }

    fun checkAlarmsAndReminders(context: Context): AppPermissionInfo {
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                am?.canScheduleExactAlarms() == true
            }.getOrDefault(false)
        } else {
            true
        }
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canExact) {
            "Not required — FEELORA uses WorkManager for flexible 2-hour task reminders without draining battery."
        } else {
            "Active via WorkManager (flexible battery-friendly background reminders)."
        }
        return AppPermissionInfo(
            id = AppPermissionId.ALARMS_REMINDERS,
            title = "Alarms & Background Reminders",
            purpose = "2-hour task follow-ups and briefings. Note: Android Doze mode may delay checks while sleeping.",
            status = AppPermissionStatus.NOT_REQUIRED,
            statusDetail = detail,
            opensSystemSettings = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        )
    }

    fun checkCamera(context: Context): AppPermissionInfo {
        val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        if (!hasCamera) {
            return AppPermissionInfo(
                id = AppPermissionId.CAMERA,
                title = "Camera",
                purpose = "Capture handwritten notes or textbook pages directly for SURU vision.",
                status = AppPermissionStatus.UNAVAILABLE,
                statusDetail = "No camera hardware detected on this device."
            )
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        return AppPermissionInfo(
            id = AppPermissionId.CAMERA,
            title = "Camera",
            purpose = "Only used when you tap Camera in SURU to snap notes or questions.",
            status = if (granted) AppPermissionStatus.GRANTED else AppPermissionStatus.NOT_GRANTED,
            statusDetail = if (granted) {
                "Granted for on-demand camera capture in SURU."
            } else {
                "Requested only if you choose Take Photo inside SURU."
            },
            isRuntimeRequestable = !granted,
            runtimePermission = Manifest.permission.CAMERA,
            opensSystemSettings = true
        )
    }

    fun checkMicrophone(context: Context): AppPermissionInfo {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        return AppPermissionInfo(
            id = AppPermissionId.MICROPHONE,
            title = "Microphone",
            purpose = "Voice commands to SURU (only activates when you tap the mic button).",
            status = if (granted) AppPermissionStatus.GRANTED else AppPermissionStatus.NOT_GRANTED,
            statusDetail = if (granted) {
                "Granted for tap-to-speak voice commands."
            } else {
                "Not granted — you can grant anytime or type to SURU."
            },
            isRuntimeRequestable = !granted,
            runtimePermission = Manifest.permission.RECORD_AUDIO,
            opensSystemSettings = true
        )
    }

    fun checkPhotos(): AppPermissionInfo {
        return AppPermissionInfo(
            id = AppPermissionId.PHOTOS,
            title = "Photo & Gallery Access",
            purpose = "Share study photos or screenshots with SURU via Android Photo Picker.",
            status = AppPermissionStatus.NOT_REQUIRED,
            statusDetail = "Zero storage permission required — you only share photos you pick."
        )
    }

    fun checkInternet(context: Context): AppPermissionInfo {
        val selfGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.INTERNET
        ) == PackageManager.PERMISSION_GRANTED
        val declaredInManifest = runCatching {
            @Suppress("DEPRECATION")
            val pkg = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
            pkg.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
        }.getOrDefault(false)
        val granted = selfGranted || declaredInManifest
        return AppPermissionInfo(
            id = AppPermissionId.INTERNET,
            title = "Internet",
            purpose = "Connects SURU to Google Gemini when configured and online. Local features work offline.",
            status = if (granted) AppPermissionStatus.GRANTED else AppPermissionStatus.NOT_GRANTED,
            statusDetail = if (granted) {
                "Manifest-declared; used only when sending prompts or images to AI."
            } else {
                "Not declared in manifest."
            }
        )
    }

    fun buildNotificationSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            buildAppDetailsSettingsIntent(context)
        }
    }

    fun buildUsageAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildExactAlarmSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            buildAppDetailsSettingsIntent(context)
        }
    }

    fun buildAppDetailsSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun resolveIntentAvailable(context: Context, intent: Intent): Boolean {
        return runCatching {
            intent.resolveActivity(context.packageManager) != null
        }.getOrDefault(false)
    }
}
