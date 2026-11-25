package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateTimeUtils {
    fun todayEpochDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val localOffset = cal.timeZone.getOffset(cal.timeInMillis)
        return TimeUnit.MILLISECONDS.toDays(cal.timeInMillis + localOffset)
    }

    fun epochDayFromMillis(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val localOffset = cal.timeZone.getOffset(millis)
        return TimeUnit.MILLISECONDS.toDays(millis + localOffset)
    }

    fun millisFromEpochDay(epochDay: Long, hour: Int = 9, minute: Int = 0): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = TimeUnit.DAYS.toMillis(epochDay)
        // Adjust back from UTC midnight so calendar fields match local date
        val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = TimeUnit.DAYS.toMillis(epochDay)
        }
        cal.set(
            utcCal.get(Calendar.YEAR),
            utcCal.get(Calendar.MONTH),
            utcCal.get(Calendar.DAY_OF_MONTH),
            hour,
            minute,
            0
        )
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun calendarForEpochDay(epochDay: Long): Calendar {
        val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = TimeUnit.DAYS.toMillis(epochDay)
        }
        return Calendar.getInstance().apply {
            set(
                utcCal.get(Calendar.YEAR),
                utcCal.get(Calendar.MONTH),
                utcCal.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0
            )
            set(Calendar.MILLISECOND, 0)
        }
    }

    fun epochDayFromYearMonthDay(year: Int, monthZeroBased: Int, dayOfMonth: Int): Long {
        val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            set(year, monthZeroBased, dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return TimeUnit.MILLISECONDS.toDays(utcCal.timeInMillis)
    }

    fun formatGreeting(hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String {
        return when (hourOfDay) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Late night focus"
        }
    }

    fun formatFullDate(epochDay: Long): String {
        val cal = calendarForEpochDay(epochDay)
        val fmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        return fmt.format(cal.time)
    }

    fun formatMonthYear(year: Int, monthZeroBased: Int): String {
        val cal = Calendar.getInstance().apply { set(year, monthZeroBased, 1) }
        val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return fmt.format(cal.time)
    }

    fun formatTimeOfDay(minuteOfDay: Int): String {
        val h = (minuteOfDay / 60).coerceIn(0, 23)
        val m = (minuteOfDay % 60).coerceIn(0, 59)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
        }
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        return fmt.format(cal.time)
    }

    fun formatLiveTime(millis: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        return fmt.format(Date(millis))
    }

    fun formatShortDate(millis: Long): String {
        val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
        return fmt.format(Date(millis))
    }

    fun formatRelativeDeadline(epochDay: Long): String {
        val diff = epochDay - todayEpochDay()
        return when {
            diff < -1L -> "${-diff}d overdue"
            diff == -1L -> "Yesterday"
            diff == 0L -> "Today"
            diff == 1L -> "Tomorrow"
            diff in 2L..6L -> "In ${diff}d"
            else -> formatFullDate(epochDay)
        }
    }

    fun daysBetween(fromEpochDay: Long, toEpochDay: Long): Long = toEpochDay - fromEpochDay

    fun formatMinutesCompact(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }
}

object ProductivityCalculator {
    fun computeScore(
        totalTasks: Int,
        completedTasks: Int,
        totalHabits: Int,
        completedHabitsToday: Int,
        focusMinutesToday: Int,
        dailyFocusGoalMinutes: Int,
        screenMinutesToday: Int,
        dailyScreenLimitMinutes: Int
    ): Int {
        if (totalTasks == 0 && totalHabits == 0 && focusMinutesToday == 0 && screenMinutesToday == 0) {
            return 0
        }
        val taskRatio = if (totalTasks > 0) (completedTasks.toFloat() / totalTasks) * 40f else 0f
        val habitRatio = if (totalHabits > 0) (completedHabitsToday.toFloat() / totalHabits) * 30f else 0f
        val focusRatio = (focusMinutesToday.toFloat() / dailyFocusGoalMinutes.coerceAtLeast(30)).coerceAtMost(1f) * 20f
        val hasPositiveActivity = completedTasks > 0 || completedHabitsToday > 0 || focusMinutesToday > 0
        val wellnessBonus = if (hasPositiveActivity && screenMinutesToday <= dailyScreenLimitMinutes) 10f else 0f
        return (taskRatio + habitRatio + focusRatio + wellnessBonus).toInt().coerceIn(0, 100)
    }
}

