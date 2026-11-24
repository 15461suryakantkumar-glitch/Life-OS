package com.example.util

import com.example.data.local.CycleLogEntity

data class CycleCareSummary(
    val hasLogs: Boolean,
    val isCurrentlyOnPeriod: Boolean,
    val activePeriodLogId: Long?,
    val currentCycleDay: Int,
    val averageCycleLengthDays: Int,
    val averagePeriodLengthDays: Int,
    val daysUntilNextPeriod: Int,
    val estimatedNextPeriodEpochDay: Long?,
    val isPeriodApproaching: Boolean,
    val latestSymptoms: List<String>,
    val latestCycleLog: CycleLogEntity?
)

/**
 * Pure local Cycle Care predictor and analyzer.
 * Strictly on-device; never serialized into widgets or friend sync payloads.
 */
object CycleCareAnalyzer {
    val commonSymptoms = listOf(
        "Cramps",
        "Fatigue",
        "Headache",
        "Mood Swings",
        "Bloating",
        "Backache",
        "Calm"
    )

    fun summarize(
        logs: List<CycleLogEntity>,
        configuredCycleLength: Int,
        configuredPeriodLength: Int,
        todayEpochDay: Long = DateTimeUtils.todayEpochDay()
    ): CycleCareSummary {
        val sortedDesc = logs.sortedByDescending { it.startEpochDay }
        val latest = sortedDesc.firstOrNull()
        val fallbackCycle = configuredCycleLength.coerceIn(21, 45)
        val fallbackPeriod = configuredPeriodLength.coerceIn(2, 10)

        if (latest == null) {
            return CycleCareSummary(
                hasLogs = false,
                isCurrentlyOnPeriod = false,
                activePeriodLogId = null,
                currentCycleDay = 0,
                averageCycleLengthDays = fallbackCycle,
                averagePeriodLengthDays = fallbackPeriod,
                daysUntilNextPeriod = 0,
                estimatedNextPeriodEpochDay = null,
                isPeriodApproaching = false,
                latestSymptoms = emptyList(),
                latestCycleLog = null
            )
        }

        // Refine average cycle length if user logged >= 2 period start dates
        val startAsc = sortedDesc.map { it.startEpochDay }.distinct().sorted()
        val computedAvgCycle = if (startAsc.size >= 2) {
            val diffs = startAsc.zipWithNext { a, b -> (b - a).toInt() }
                .filter { it in 18..55 }
                .takeLast(5)
            if (diffs.isNotEmpty()) diffs.average().toInt().coerceIn(21, 45) else fallbackCycle
        } else {
            fallbackCycle
        }

        val isOpenPeriod = latest.endEpochDay == null && (todayEpochDay - latest.startEpochDay) in 0L..10L
        val isWithinRange = latest.endEpochDay != null && todayEpochDay in latest.startEpochDay..latest.endEpochDay
        val onPeriod = isOpenPeriod || isWithinRange

        val cycleDay = (todayEpochDay - latest.startEpochDay + 1L).toInt().coerceAtLeast(1)
        val estimatedNext = latest.startEpochDay + computedAvgCycle
        val daysUntil = (estimatedNext - todayEpochDay).toInt()

        val symptoms = latest.symptomsCsv
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return CycleCareSummary(
            hasLogs = true,
            isCurrentlyOnPeriod = onPeriod,
            activePeriodLogId = if (isOpenPeriod) latest.id else null,
            currentCycleDay = cycleDay,
            averageCycleLengthDays = computedAvgCycle,
            averagePeriodLengthDays = fallbackPeriod,
            daysUntilNextPeriod = daysUntil,
            estimatedNextPeriodEpochDay = estimatedNext,
            isPeriodApproaching = !onPeriod && daysUntil in 0..3,
            latestSymptoms = symptoms,
            latestCycleLog = latest
        )
    }
}
