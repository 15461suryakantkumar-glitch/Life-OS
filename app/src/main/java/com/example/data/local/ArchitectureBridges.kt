package com.example.data.local

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

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
            "OS UsageStats Connected"
        } else {
            "Manual Tracking Active"
        }
    }
}
