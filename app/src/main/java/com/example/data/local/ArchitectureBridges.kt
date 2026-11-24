package com.example.data.local

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class WellbeingTelemetryState(val label: String, val description: String) {
    PERMISSION_NOT_GRANTED(
        label = "Permission not granted",
        description = "Grant Usage Access to display authentic screen time across installed apps."
    ),
    NO_USAGE_RECORDED(
        label = "No usage recorded",
        description = "Usage Access is active, and no qualifying foreground app usage was recorded in this period yet."
    ),
    USAGE_UNAVAILABLE(
        label = "Usage information unavailable",
        description = "Android UsageStatsManager could not provide telemetry for this window."
    ),
    RECORDED_USAGE(
        label = "Actual recorded usage",
        description = "Live foreground telemetry verified via Android UsageStatsManager."
    )
}

enum class WellbeingRange(val label: String, val days: Int) {
    DAILY("Today", 1),
    WEEKLY("Weekly", 7),
    MONTHLY("Monthly", 30)
}

data class SystemAppUsageSummary(
    val packageName: String,
    val appName: String,
    val category: AppUsageCategory,
    val minutesUsed: Int,
    val isDistracting: Boolean
)

data class UsageTelemetryReport(
    val state: WellbeingTelemetryState,
    val range: WellbeingRange,
    val totalMinutes: Int,
    val distractingMinutes: Int,
    val apps: List<SystemAppUsageSummary>
)

interface WellnessTelemetrySource {
    fun hasSystemUsagePermission(): Boolean
    fun getSourceStatusLabel(): String
    fun queryTodayUsageStats(): List<SystemAppUsageSummary>
    fun queryUsageReport(range: WellbeingRange): UsageTelemetryReport
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
            "Permission Required"
        }
    }

    override fun queryTodayUsageStats(): List<SystemAppUsageSummary> {
        return queryUsageReport(WellbeingRange.DAILY).apps
    }

    override fun queryUsageReport(range: WellbeingRange): UsageTelemetryReport {
        if (!hasSystemUsagePermission()) {
            return UsageTelemetryReport(
                state = WellbeingTelemetryState.PERMISSION_NOT_GRANTED,
                range = range,
                totalMinutes = 0,
                distractingMinutes = 0,
                apps = emptyList()
            )
        }
        val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return UsageTelemetryReport(
                state = WellbeingTelemetryState.USAGE_UNAVAILABLE,
                range = range,
                totalMinutes = 0,
                distractingMinutes = 0,
                apps = emptyList()
            )

        return try {
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (range.days > 1) {
                    add(Calendar.DAY_OF_YEAR, -(range.days - 1))
                }
            }
            val startMillis = cal.timeInMillis

            val aggregatedMillis = if (range == WellbeingRange.DAILY) {
                computeForegroundMillisFromEvents(usageManager, startMillis, now)
                    .ifEmpty { computeForegroundMillisFromAggregate(usageManager, startMillis, now) }
            } else {
                computeForegroundMillisFromAggregate(usageManager, startMillis, now)
            }

            val pm = context.packageManager
            val apps = aggregatedMillis.entries
                .mapNotNull { (pkg, millis) ->
                    if (pkg == context.packageName || isSystemNoisePackage(pkg)) return@mapNotNull null
                    val mins = TimeUnit.MILLISECONDS.toMinutes(millis).toInt()
                    if (mins <= 0) return@mapNotNull null
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
                .take(25)

            if (apps.isEmpty()) {
                UsageTelemetryReport(
                    state = WellbeingTelemetryState.NO_USAGE_RECORDED,
                    range = range,
                    totalMinutes = 0,
                    distractingMinutes = 0,
                    apps = emptyList()
                )
            } else {
                UsageTelemetryReport(
                    state = WellbeingTelemetryState.RECORDED_USAGE,
                    range = range,
                    totalMinutes = apps.sumOf { it.minutesUsed },
                    distractingMinutes = apps.filter { it.isDistracting }.sumOf { it.minutesUsed },
                    apps = apps
                )
            }
        } catch (_: Exception) {
            UsageTelemetryReport(
                state = WellbeingTelemetryState.USAGE_UNAVAILABLE,
                range = range,
                totalMinutes = 0,
                distractingMinutes = 0,
                apps = emptyList()
            )
        }
    }

    private fun computeForegroundMillisFromEvents(
        usageManager: UsageStatsManager,
        startMillis: Long,
        endMillis: Long
    ): Map<String, Long> {
        val events = usageManager.queryEvents(startMillis, endMillis) ?: return emptyMap()
        val totals = LinkedHashMap<String, Long>()
        val activeStarts = HashMap<String, Long>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val ts = event.timeStamp
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                @Suppress("DEPRECATION")
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    activeStarts[pkg] = ts
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                @Suppress("DEPRECATION")
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val started = activeStarts.remove(pkg)
                    if (started != null && ts > started) {
                        totals[pkg] = (totals[pkg] ?: 0L) + (ts - started)
                    }
                }
            }
        }
        activeStarts.forEach { (pkg, started) ->
            if (endMillis > started) {
                totals[pkg] = (totals[pkg] ?: 0L) + (endMillis - started)
            }
        }
        return totals
    }

    private fun computeForegroundMillisFromAggregate(
        usageManager: UsageStatsManager,
        startMillis: Long,
        endMillis: Long
    ): Map<String, Long> {
        val statsMap = usageManager.queryAndAggregateUsageStats(startMillis, endMillis) ?: return emptyMap()
        val totals = LinkedHashMap<String, Long>()
        statsMap.forEach { (pkg, stat) ->
            val fgMillis = stat.totalTimeInForeground
            if (fgMillis >= 60_000L) {
                totals[pkg] = fgMillis
            }
        }
        return totals
    }

    fun openUsageAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    companion object {
        private val iconCache = object : LruCache<String, ImageBitmap?>(64) {}

        fun resolveAppLabel(pm: PackageManager, packageName: String): String {
            if (packageName.isBlank()) return "App"
            return try {
                val info = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(info).toString().ifBlank { fallbackLabel(packageName) }
            } catch (_: Exception) {
                fallbackLabel(packageName)
            }
        }

        fun loadAppIconBitmap(context: Context, packageName: String): ImageBitmap? {
            if (packageName.isBlank()) return null
            iconCache.get(packageName)?.let { return it }
            val loaded = runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                drawableToBitmap(drawable)?.asImageBitmap()
            }.getOrNull()
            if (loaded != null) {
                iconCache.put(packageName, loaded)
            }
            return loaded
        }

        private fun drawableToBitmap(drawable: Drawable, sizePx: Int = 96): Bitmap? {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                return drawable.bitmap
            }
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }

        private fun fallbackLabel(packageName: String): String {
            return packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }

        fun isSystemNoisePackage(pkg: String): Boolean {
            val lower = pkg.lowercase()
            return lower.contains("launcher") ||
                lower == "com.android.systemui" ||
                lower == "com.android.settings" ||
                lower == "android"
        }

        fun classifyPackage(pkg: String, label: String): AppUsageCategory {
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
}
