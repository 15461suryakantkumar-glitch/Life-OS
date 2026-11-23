package com.example.data.local

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Architectural contract for Digital Wellness telemetry.
 * Initially backed by Local Manual + Sample entries; ready for Android UsageStatsManager
 * once the user explicitly grants PACKAGE_USAGE_STATS special app access.
 */
interface WellnessTelemetrySource {
    fun hasSystemUsagePermission(): Boolean
    fun getSourceStatusLabel(): String
}

class AndroidUsageStatsBridge(private val context: Context) : WellnessTelemetrySource {
    override fun hasSystemUsagePermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    override fun getSourceStatusLabel(): String {
        return if (hasSystemUsagePermission()) {
            "UsageStats Permission Granted (Adapter Ready)"
        } else {
            "Manual & Sample Mode (OS UsageStats Disabled)"
        }
    }
}

/**
 * Architectural contract for future Firebase Auth & Firestore Social Sync.
 * Current build runs purely on local Room storage without requiring login.
 */
interface SocialCloudSyncBridge {
    val isCloudConnected: Boolean
    val syncModeLabel: String
}

class LocalDemoSocialBridge : SocialCloudSyncBridge {
    override val isCloudConnected: Boolean = false
    override val syncModeLabel: String = "Local-First Mode · Firebase Auth & Firestore Adapter Ready"
}
