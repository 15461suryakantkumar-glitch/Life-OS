package com.example.data.local

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class SystemAppUsageSummary(
    val packageName: String,
    val appName: String,
    val category: AppUsageCategory,
    val minutesUsed: Int,
    val isDistracting: Boolean
)

interface WellnessTelemetrySource {
    fun hasSystemUsagePermission(): Boolean
    fun getSourceStatusLabel(): String
    fun queryTodayUsageStats(): List<SystemAppUsageSummary>
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
            "Usage Access Active"
        } else {
            "Manual Tracking (Offline Safe)"
        }
    }

    override fun queryTodayUsageStats(): List<SystemAppUsageSummary> {
        if (!hasSystemUsagePermission()) return emptyList()
        return try {
            val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyList()
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = cal.timeInMillis
            val stats = usageManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
                ?: return emptyList()

            val pm = context.packageManager
            val aggregated = LinkedHashMap<String, Long>()
            stats.forEach { stat ->
                val pkg = stat.packageName ?: return@forEach
                if (pkg == context.packageName) return@forEach
                val fgMillis = stat.totalTimeInForeground
                if (fgMillis >= 60_000L) {
                    aggregated[pkg] = (aggregated[pkg] ?: 0L) + fgMillis
                }
            }

            aggregated.entries
                .mapNotNull { (pkg, millis) ->
                    val mins = TimeUnit.MILLISECONDS.toMinutes(millis).toInt()
                    if (mins <= 0 || isSystemNoisePackage(pkg)) return@mapNotNull null
                    val label = resolveAppLabel(pm, pkg)
                    val category = classifyPackage(pkg, label)
                    val distracting = category == AppUsageCategory.SOCIAL || category == AppUsageCategory.ENTERTAINMENT
                    SystemAppUsageSummary(
                        packageName = pkg,
                        appName = label,
                        category = category,
                        minutesUsed = mins,
                        isDistracting = distracting
                    )
                }
                .sortedByDescending { it.minutesUsed }
                .take(15)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun openUsageAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun resolveAppLabel(pm: PackageManager, packageName: String): String {
        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString().ifBlank { packageName.substringAfterLast('.') }
        } catch (_: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    private fun isSystemNoisePackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("launcher") ||
            lower == "com.android.systemui" ||
            lower == "com.android.settings" ||
            lower == "android"
    }

    private fun classifyPackage(pkg: String, label: String): AppUsageCategory {
        val combined = "${pkg.lowercase()} ${label.lowercase()}"
        return when {
            listOf("instagram", "tiktok", "musically", "snapchat", "twitter", "x.com", "reddit", "facebook", "whatsapp", "discord", "telegram", "threads")
                .any { combined.contains(it) } -> AppUsageCategory.SOCIAL
            listOf("youtube", "netflix", "twitch", "spotify", "primevideo", "hotstar", "game", "roblox", "pubg")
                .any { combined.contains(it) } -> AppUsageCategory.ENTERTAINMENT
            listOf("docs", "notion", "obsidian", "anki", "khan", "duolingo", "coursera", "classroom", "notes", "calendar", "drive", "pdf", "reader")
                .any { combined.contains(it) } -> AppUsageCategory.PRODUCTIVE
            else -> AppUsageCategory.UTILITY
        }
    }
}
