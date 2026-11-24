package com.example.util

import com.example.data.local.AppUsageLogEntity
import com.example.data.local.DailyProgressLogEntity
import com.example.data.local.HabitEntity
import com.example.data.local.TaskEntity
import com.example.data.local.UserPreferencesState

data class DailyAnalysisReport(
    val headline: String,
    val summary: String,
    val recommendations: List<String>,
    val focusTaskSuggestion: String?,
    val agedPendingCount: Int,
    val distractionRatioPercent: Int
)

data class WeeklyAnalysisReport(
    val headline: String,
    val summary: String,
    val recommendations: List<String>,
    val activeLoginDaysCount: Int,
    val weeklyTasksCompleted: Int,
    val weeklyFocusMinutes: Int,
    val weeklyDistractingMinutes: Int
)

object ProductivityAnalyzer {

    fun analyzeDaily(
        prefs: UserPreferencesState,
        tasks: List<TaskEntity>,
        habits: List<HabitEntity>,
        todayUsageLogs: List<AppUsageLogEntity>,
        todayEpochDay: Long = DateTimeUtils.todayEpochDay(),
        nowMillis: Long = System.currentTimeMillis()
    ): DailyAnalysisReport {
        val pendingTasks = tasks.filter { !it.isCompleted }
        val completedToday = tasks.count {
            it.isCompleted && it.completedAtMillis?.let { ts ->
                DateTimeUtils.epochDayFromMillis(ts) == todayEpochDay
            } == true
        }
        val twoHoursMillis = 2L * 60L * 60L * 1000L
        val agedPending = pendingTasks.filter { (nowMillis - it.createdAtMillis) >= twoHoursMillis }
        val totalScreen = todayUsageLogs.sumOf { it.minutesUsed }
        val distracting = todayUsageLogs.filter { it.isDistracting }.sumOf { it.minutesUsed }
        val distractionRatio = if (totalScreen > 0) ((distracting * 100f) / totalScreen).toInt().coerceIn(0, 100) else 0
        val habitsDone = habits.count { it.isCompletedOn(todayEpochDay) }
        val focusToday = prefs.focusMinutesLoggedToday
        val focusGoal = prefs.dailyFocusGoalMinutes.coerceAtLeast(30)

        val highestPriorityPending = pendingTasks
            .sortedWith(compareByDescending<TaskEntity> { it.priority.ordinal }.thenBy { it.createdAtMillis })
            .firstOrNull()
        val mostDistractedTask = tasks
            .filter { it.distractionMinutes > 0 }
            .maxByOrNull { it.distractionMinutes }

        val headline = when {
            tasks.isEmpty() && habits.isEmpty() && focusToday == 0 && totalScreen == 0 ->
                "Clean Slate Ready"
            completedToday >= 3 && focusToday >= focusGoal / 2 && distractionRatio <= 30 ->
                "Strong Daily Momentum"
            agedPending.isNotEmpty() ->
                "${agedPending.size} Pending ${if (agedPending.size == 1) "Task" else "Tasks"} Need Attention"
            distracting > prefs.dailyScreenLimitMinutes / 2 ->
                "High Distraction Detected"
            else ->
                "Steady Daily Progress"
        }

        val summary = if (tasks.isEmpty() && habits.isEmpty() && focusToday == 0 && totalScreen == 0) {
            "No activity has been logged today yet. Add one priority task and start a short 15 or 25 minute focus block to build momentum."
        } else {
            buildString {
                append("Today: $completedToday tasks completed, $habitsDone/${habits.size} habits done, ")
                append("${DateTimeUtils.formatMinutesCompact(focusToday)}/${DateTimeUtils.formatMinutesCompact(focusGoal)} focused")
                if (totalScreen > 0) {
                    append(", and ${DateTimeUtils.formatMinutesCompact(distracting)} distracting screen time ($distractionRatio%).")
                } else {
                    append(".")
                }
            }
        }

        val recs = mutableListOf<String>()
        if (highestPriorityPending != null) {
            recs.add("Start with \"${highestPriorityPending.title}\" (${highestPriorityPending.priority.label}) using a 25m focus timer.")
        } else {
            recs.add("Add 1–3 concrete tasks for today so your focus blocks stay targeted.")
        }
        if (mostDistractedTask != null) {
            recs.add("Watch out during \"${mostDistractedTask.title}\" (${mostDistractedTask.distractionMinutes}m distraction logged) — silence social apps first.")
        } else if (distracting > 45) {
            recs.add("Distracting screen time reached ${DateTimeUtils.formatMinutesCompact(distracting)} — keep your phone face-down during the next sprint.")
        }
        if (focusToday < focusGoal) {
            val remaining = (focusGoal - focusToday).coerceAtLeast(15)
            recs.add("You need ${DateTimeUtils.formatMinutesCompact(remaining)} more focus time to hit today's ${DateTimeUtils.formatMinutesCompact(focusGoal)} goal.")
        } else {
            recs.add("Daily focus goal reached! Protect your energy and review tomorrow's calendar.")
        }

        return DailyAnalysisReport(
            headline = headline,
            summary = summary,
            recommendations = recs.take(3),
            focusTaskSuggestion = highestPriorityPending?.title,
            agedPendingCount = agedPending.size,
            distractionRatioPercent = distractionRatio
        )
    }

    fun analyzeWeekly(
        prefs: UserPreferencesState,
        tasks: List<TaskEntity>,
        usageLogs: List<AppUsageLogEntity>,
        dailyProgressLogs: List<DailyProgressLogEntity>,
        todayEpochDay: Long = DateTimeUtils.todayEpochDay()
    ): WeeklyAnalysisReport {
        val windowDays = (todayEpochDay - 6L..todayEpochDay).toSet()
        val loginDays7 = prefs.loginEpochDays.count { it in windowDays }.coerceAtLeast(if (prefs.isSignedIn) 1 else 0)

        val progressByDay = dailyProgressLogs.associateBy { it.epochDay }
        val completedFromTasks = tasks.count {
            it.isCompleted && it.completedAtMillis?.let { ts ->
                DateTimeUtils.epochDayFromMillis(ts) in windowDays
            } == true
        }
        val completedFromLogs = windowDays.sumOf { progressByDay[it]?.tasksCompleted ?: 0 }
        val weeklyCompleted = maxOf(completedFromTasks, completedFromLogs)

        val weeklyFocus = windowDays.sumOf { day ->
            val logged = progressByDay[day]?.focusMinutes ?: 0
            if (day == todayEpochDay) maxOf(logged, prefs.focusMinutesLoggedToday) else logged
        }

        val weeklyDistracting = windowDays.sumOf { day ->
            val fromUsage = usageLogs.filter { it.dateEpochDay == day && it.isDistracting }.sumOf { it.minutesUsed }
            val fromProgress = progressByDay[day]?.distractingMinutes ?: 0
            maxOf(fromUsage, fromProgress)
        }

        val bestFocusDay = windowDays.maxByOrNull { day ->
            val logged = progressByDay[day]?.focusMinutes ?: 0
            if (day == todayEpochDay) maxOf(logged, prefs.focusMinutesLoggedToday) else logged
        }
        val bestFocusMins = bestFocusDay?.let { day ->
            val logged = progressByDay[day]?.focusMinutes ?: 0
            if (day == todayEpochDay) maxOf(logged, prefs.focusMinutesLoggedToday) else logged
        } ?: 0

        val headline = when {
            weeklyCompleted == 0 && weeklyFocus == 0 && weeklyDistracting == 0 ->
                "Week 1 Baseline"
            loginDays7 >= 5 && weeklyFocus >= prefs.dailyFocusGoalMinutes * 3 ->
                "Consistent Weekly Rhythm"
            weeklyDistracting > weeklyFocus ->
                "Reclaim Focus From Distractions"
            else ->
                "Building Weekly Consistency"
        }

        val summary = if (weeklyCompleted == 0 && weeklyFocus == 0 && weeklyDistracting == 0) {
            "Active $loginDays7 of 7 days this week. Complete tasks and log focus sprints to unlock your weekly trend insights."
        } else {
            buildString {
                append("Past 7 days: active $loginDays7/7 days, $weeklyCompleted tasks finished, ")
                append("${DateTimeUtils.formatMinutesCompact(weeklyFocus)} deep focus")
                if (bestFocusMins > 0 && bestFocusDay != null) {
                    append(" (peak on ${DateTimeUtils.formatFullDate(bestFocusDay)})")
                }
                append(".")
            }
        }

        val recs = mutableListOf<String>()
        if (loginDays7 < 5) {
            recs.add("Check in daily — even completing 1 small task keeps your streak and companion mood high.")
        } else {
            recs.add("Strong $loginDays7/7 day check-in cadence! Keep your morning routine anchored.")
        }
        if (weeklyDistracting > weeklyFocus && weeklyDistracting > 0) {
            recs.add("Weekly distractions (${DateTimeUtils.formatMinutesCompact(weeklyDistracting)}) exceeded focus time. Try blocking one 25m task sprint before social apps.")
        } else if (weeklyFocus > 0) {
            recs.add("Your focus-to-distraction balance is positive this week — protect your peak focus hours.")
        }
        val pendingCount = tasks.count { !it.isCompleted }
        if (pendingCount > 4) {
            recs.add("You have $pendingCount open tasks. Ask SURU to \"Organize tomorrow\" to time-block your top 3.")
        } else {
            recs.add("Task backlog is manageable ($pendingCount open). Keep new sections concise and actionable.")
        }

        return WeeklyAnalysisReport(
            headline = headline,
            summary = summary,
            recommendations = recs.take(3),
            activeLoginDaysCount = loginDays7,
            weeklyTasksCompleted = weeklyCompleted,
            weeklyFocusMinutes = weeklyFocus,
            weeklyDistractingMinutes = weeklyDistracting
        )
    }
}
