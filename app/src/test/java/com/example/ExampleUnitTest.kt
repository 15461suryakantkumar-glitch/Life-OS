package com.example

import com.example.suru.LifeContextSnapshot
import com.example.suru.SuruIntent
import com.example.suru.SuruNluEngine
import com.example.data.audio.LifeOsSoundCue
import com.example.data.audio.LifeOsSoundManager
import com.example.data.local.AppUsageCategory
import com.example.data.local.AppUsageLogEntity
import com.example.data.local.CompanionMood
import com.example.data.local.CompanionMoodResolver
import com.example.data.local.CycleLogEntity
import com.example.data.local.DailyProgressLogEntity
import com.example.data.local.KUTTA_NAME
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import com.example.data.local.TaskSectionEntity
import com.example.data.local.TaskSectionMapper
import com.example.data.local.UserPreferencesState
import com.example.data.social.FriendSyncCodec
import com.example.kutta.KuttaDialogue
import com.example.kutta.KuttaEmotion
import com.example.kutta.KuttaEvent
import com.example.kutta.KuttaEventHandler
import com.example.kutta.KuttaPose
import com.example.notifications.TaskReminderScheduler
import com.example.ui.theme.LifeOsThemeMode
import com.example.util.CycleCareAnalyzer
import com.example.util.ProductivityAnalyzer
import com.example.util.ProductivityCalculator
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for LIFE OS offline intelligence, productivity scoring,
 * personality companion matching, mood resolution, analyzer insights, quiet hours,
 * and privacy-preserving friend sync codec.
 */
class ExampleUnitTest {

    private fun sampleSnapshot() = LifeContextSnapshot(
        userName = "Suryakant",
        level = 4,
        totalXp = 820,
        dailyStreak = 6,
        pendingTasksSummary = "Finish HP (High); Organic Chem (Medium)",
        pendingTaskTitles = listOf("Finish HP", "Organic Chem"),
        completedTasksCount = 3,
        upcomingEventsSummary = "Physics at 9:00 AM",
        screenTimeTodayMinutes = 95,
        focusMinutesToday = 60,
        dailyFocusGoalMinutes = 120,
        habitsCompletedToday = 2,
        habitsTotal = 4,
        productivityScore = 78
    )

    private fun cleanSlateSnapshot() = LifeContextSnapshot(
        userName = "NovaUser",
        level = 1,
        totalXp = 0,
        dailyStreak = 0,
        pendingTasksSummary = "",
        pendingTaskTitles = emptyList(),
        completedTasksCount = 0,
        upcomingEventsSummary = "",
        screenTimeTodayMinutes = 0,
        focusMinutesToday = 0,
        dailyFocusGoalMinutes = 120,
        habitsCompletedToday = 0,
        habitsTotal = 0,
        productivityScore = 0
    )

    @Test
    fun productivityCalculator_returnsZeroOnCleanSlate_andPositiveWithProgress() {
        val cleanScore = ProductivityCalculator.computeScore(
            totalTasks = 0,
            completedTasks = 0,
            totalHabits = 0,
            completedHabitsToday = 0,
            focusMinutesToday = 0,
            dailyFocusGoalMinutes = 120,
            screenMinutesToday = 0,
            dailyScreenLimitMinutes = 180
        )
        assertEquals(0, cleanScore)

        val activeScore = ProductivityCalculator.computeScore(
            totalTasks = 4,
            completedTasks = 2,
            totalHabits = 2,
            completedHabitsToday = 1,
            focusMinutesToday = 60,
            dailyFocusGoalMinutes = 120,
            screenMinutesToday = 90,
            dailyScreenLimitMinutes = 180
        )
        assertTrue(activeScore in 40..90)
    }

    @Test
    fun offlineSuruNlu_parsesScheduleEventAndTaskCommands() {
        val eventRes = SuruNluEngine.parseAndPlan(
            userPrompt = "Schedule Physics tomorrow at 8 AM.",
            context = sampleSnapshot(),
            tasks = emptyList(),
            events = emptyList(),
            memories = emptyList(),
            pendingContext = null,
            lastStudyDraft = null
        )
        assertNotNull(eventRes)
        assertEquals(SuruIntent.CREATE_EVENT, eventRes!!.intent)
        assertEquals("Physics", eventRes.title)
        assertEquals(1, eventRes.dateOffsetDays)
        assertEquals(8, eventRes.startHour)
        assertEquals(0, eventRes.startMinute)
        assertEquals(TaskCategory.STUDY, eventRes.category)

        val taskRes = SuruNluEngine.parseAndPlan(
            userPrompt = "Add Chemical Equilibrium to my tasks.",
            context = sampleSnapshot(),
            tasks = emptyList(),
            events = emptyList(),
            memories = emptyList(),
            pendingContext = null,
            lastStudyDraft = null
        )
        assertNotNull(taskRes)
        assertEquals(SuruIntent.CREATE_TASK, taskRes!!.intent)
        assertEquals("Chemical Equilibrium", taskRes.title)
        assertEquals(TaskCategory.STUDY, taskRes.category)
    }

    @Test
    fun offlineSuruNlu_plansWeekAndSummarizesProgress() {
        val plan = SuruNluEngine.parseAndPlan(
            userPrompt = "Plan my entire week",
            context = sampleSnapshot(),
            tasks = emptyList(),
            events = emptyList(),
            memories = emptyList(),
            pendingContext = null,
            lastStudyDraft = null
        )
        assertEquals(SuruIntent.PLAN_WEEK, plan?.intent)

        val report = SuruNluEngine.parseAndPlan(
            userPrompt = "Summarize my progress this month",
            context = sampleSnapshot(),
            tasks = emptyList(),
            events = emptyList(),
            memories = emptyList(),
            pendingContext = null,
            lastStudyDraft = null
        )
        assertEquals(SuruIntent.PROGRESS_SUMMARY, report?.intent)

        val focusNext = SuruNluEngine.parseAndPlan(
            userPrompt = "What should I focus on today?",
            context = cleanSlateSnapshot(),
            tasks = emptyList(),
            events = emptyList(),
            memories = emptyList(),
            pendingContext = null,
            lastStudyDraft = null
        )
        assertEquals(SuruIntent.OVERDUE_CHECK, focusNext?.intent)
        assertTrue(focusNext?.reply?.contains("clear", ignoreCase = true) == true)
    }

    @Test
    fun friendSyncCodec_roundTripAndPrivacyFiltering() {
        val prefs = UserPreferencesState(
            userName = "Aria",
            userHandle = "@aria.os",
            friendCode = "LIFE-ARIA-4821",
            totalXp = 950,
            dailyStreak = 9,
            focusMinutesLoggedToday = 75,
            shareXpWithFriends = true,
            shareStreaksWithFriends = true,
            shareFocusWithFriends = false,
            shareChallengesWithFriends = true
        )

        val inviteLink = FriendSyncCodec.buildInviteLink(
            prefs = prefs,
            challengesCompleted = 2,
            challengesTotal = 4
        )
        assertTrue(inviteLink.startsWith("lifeos://friend?data=LIFEOS1:"))

        val parsed = FriendSyncCodec.parseInput(inviteLink)
        assertNotNull(parsed)
        assertEquals("Aria", parsed!!.displayName)
        assertEquals("@aria.os", parsed.handle)
        assertEquals("LIFE-ARIA-4821", parsed.friendCode)
        assertEquals(950, parsed.xp)
        assertEquals(9, parsed.streakDays)
        assertFalse(parsed.shareFocus)
        assertEquals(0, parsed.focusMinutesToday)
        assertEquals(2, parsed.challengesCompleted)
        assertEquals(4, parsed.challengesTotal)

        val shortParsed = FriendSyncCodec.parseInput("LIFE-KAI-1024")
        assertNotNull(shortParsed)
        assertEquals("LIFE-KAI-1024", shortParsed!!.friendCode)
    }

    @Test
    fun soundManager_synthesizesValidWavHeaders_andPreferencesDefaultSoundOn() {
        assertTrue(UserPreferencesState().soundEffectsEnabled)

        LifeOsSoundCue.entries.forEach { cue ->
            val wav = LifeOsSoundManager.buildWavForCue(cue)
            assertTrue(wav.size > 44)
            assertEquals('R'.code.toByte(), wav[0])
            assertEquals('I'.code.toByte(), wav[1])
            assertEquals('F'.code.toByte(), wav[2])
            assertEquals('F'.code.toByte(), wav[3])
            assertEquals('W'.code.toByte(), wav[8])
            assertEquals('A'.code.toByte(), wav[9])
            assertEquals('V'.code.toByte(), wav[10])
            assertEquals('E'.code.toByte(), wav[11])
        }
    }

    @Test
    fun taskSectionMapper_infersCategoriesAndPreservesDefaults() {
        assertEquals(TaskCategory.STUDY, TaskSectionMapper.inferCategory("Physics"))
        assertEquals(TaskCategory.STUDY, TaskSectionMapper.inferCategory("Chemistry"))
        assertEquals(TaskCategory.STUDY, TaskSectionMapper.inferCategory("Bio"))
        assertEquals(TaskCategory.STUDY, TaskSectionMapper.inferCategory("Tests"))
        assertEquals(TaskCategory.CODE, TaskSectionMapper.inferCategory("Android Build"))
        assertEquals(TaskCategory.HEALTH, TaskSectionMapper.inferCategory("Gym Routine"))
        assertEquals(TaskCategory.WORK, TaskSectionMapper.inferCategory("Inbox"))

        val section = TaskSectionEntity(id = 4L, name = "Physics")
        val task = TaskEntity(sectionId = section.id, title = "Solve Electrostatics", deadlineEpochDay = 20_000L)
        assertEquals(4L, task.sectionId)
    }

    @Test
    fun kuttaIdentity_isPermanentSingleCompanionAndBaselineStartsFresh() {
        val defaults = UserPreferencesState()
        assertEquals("KUTTA", KUTTA_NAME)
        assertEquals("KUTTA", defaults.petName)
        assertEquals(0, defaults.totalXp)
        assertEquals(1, defaults.level)
        assertEquals(0, defaults.waterIntakeMlToday)
        assertEquals(2000, defaults.dailyWaterGoalMl)
    }

    @Test
    fun companionMoodResolver_reactsToTimerPendingAgeDistractionAndNightHours() {
        val now = 1_700_000_000_000L
        val threeHoursMs = 3L * 60L * 60L * 1000L
        val staleTask = TaskEntity(
            title = "Pending Mock Paper",
            deadlineEpochDay = 20_000L,
            createdAtMillis = now - threeHoursMs
        )

        assertEquals(
            CompanionMood.FOCUSED,
            CompanionMoodResolver.resolve(
                isTimerRunning = true,
                petHappiness = 70,
                petEnergy = 80,
                hasRecentPositiveAction = false,
                pendingTasks = listOf(staleTask),
                distractingMinutesToday = 100,
                dailyScreenLimitMinutes = 180,
                nowMillis = now,
                hourOfDay = 14
            )
        )

        assertEquals(
            CompanionMood.WORRIED,
            CompanionMoodResolver.resolve(
                isTimerRunning = false,
                petHappiness = 75,
                petEnergy = 80,
                hasRecentPositiveAction = false,
                pendingTasks = listOf(staleTask),
                distractingMinutesToday = 10,
                dailyScreenLimitMinutes = 180,
                nowMillis = now,
                hourOfDay = 14
            )
        )

        assertEquals(
            CompanionMood.SLEEPY,
            CompanionMoodResolver.resolve(
                isTimerRunning = false,
                petHappiness = 75,
                petEnergy = 80,
                hasRecentPositiveAction = false,
                pendingTasks = emptyList(),
                distractingMinutesToday = 0,
                dailyScreenLimitMinutes = 180,
                nowMillis = now,
                hourOfDay = 23
            )
        )

        assertEquals(
            CompanionMood.JOYFUL,
            CompanionMoodResolver.resolve(
                isTimerRunning = false,
                petHappiness = 75,
                petEnergy = 80,
                hasRecentPositiveAction = false,
                pendingTasks = emptyList(),
                distractingMinutesToday = 10,
                dailyScreenLimitMinutes = 180,
                nowMillis = now,
                hourOfDay = 14
            )
        )
    }

    @Test
    fun kuttaEventHandler_resolvesFiveEmotionsAndEventTransitions() {
        val now = 1_700_000_000_000L
        val staleTask = TaskEntity(
            title = "Revise Organic Chemistry",
            deadlineEpochDay = 20_000L,
            createdAtMillis = now - (3L * 3600L * 1000L)
        )

        assertEquals(
            KuttaEmotion.FOCUSED,
            KuttaEventHandler.resolveBaselineEmotion(
                isTimerRunning = true,
                pendingTasks = listOf(staleTask),
                distractingMinutesToday = 90,
                dailyScreenLimitMinutes = 180,
                recentPositiveScore = false,
                lastCheckInMoodScore = null,
                nowMillis = now,
                hourOfDay = 14
            )
        )
        assertEquals(
            KuttaEmotion.SLEEPY,
            KuttaEventHandler.resolveBaselineEmotion(
                isTimerRunning = false,
                pendingTasks = emptyList(),
                distractingMinutesToday = 0,
                dailyScreenLimitMinutes = 180,
                recentPositiveScore = false,
                lastCheckInMoodScore = null,
                nowMillis = now,
                hourOfDay = 23
            )
        )
        assertEquals(
            KuttaEmotion.PROUD,
            KuttaEventHandler.resolveBaselineEmotion(
                isTimerRunning = false,
                pendingTasks = emptyList(),
                distractingMinutesToday = 0,
                dailyScreenLimitMinutes = 180,
                recentPositiveScore = true,
                lastCheckInMoodScore = null,
                nowMillis = now,
                hourOfDay = 15
            )
        )
        assertEquals(
            KuttaEmotion.WORRIED,
            KuttaEventHandler.resolveBaselineEmotion(
                isTimerRunning = false,
                pendingTasks = listOf(staleTask),
                distractingMinutesToday = 10,
                dailyScreenLimitMinutes = 180,
                recentPositiveScore = false,
                lastCheckInMoodScore = null,
                nowMillis = now,
                hourOfDay = 15
            )
        )
        assertEquals(
            KuttaEmotion.HAPPY,
            KuttaEventHandler.resolveBaselineEmotion(
                isTimerRunning = false,
                pendingTasks = emptyList(),
                distractingMinutesToday = 10,
                dailyScreenLimitMinutes = 180,
                recentPositiveScore = false,
                lastCheckInMoodScore = 4,
                nowMillis = now,
                hourOfDay = 15
            )
        )

        val morning = KuttaEventHandler.stateForEvent(KuttaEvent.AppOpenedMorning)
        assertEquals(KuttaPose.MORNING, morning.pose)

        val focusDone = KuttaEventHandler.stateForEvent(KuttaEvent.FocusCompleted(25))
        assertEquals(KuttaEmotion.PROUD, focusDone.emotion)
        assertEquals(KuttaPose.CELEBRATING, focusDone.pose)

        val waterRem = KuttaEventHandler.stateForEvent(KuttaEvent.WaterReminder)
        assertTrue(waterRem.isWaterHighlight)
        assertEquals(KuttaPose.MORNING, waterRem.pose)

        val periodStart = KuttaEventHandler.stateForEvent(KuttaEvent.CyclePeriodStarted)
        assertEquals(KuttaEmotion.SLEEPY, periodStart.emotion)
        assertEquals(KuttaPose.SLEEPING, periodStart.pose)
        assertTrue(periodStart.showHearts)

        val periodEnd = KuttaEventHandler.stateForEvent(KuttaEvent.CyclePeriodEnded)
        assertEquals(KuttaEmotion.PROUD, periodEnd.emotion)
        assertEquals(KuttaPose.PROUD, periodEnd.pose)
    }

    @Test
    fun kuttaDialogue_usesHinglishWithMalkinOrKuttaAndRotatesWithoutImmediateRepeats() {
        KuttaDialogue.resetRotationForTest()
        KuttaEmotion.entries.forEach { emotion ->
            val line = KuttaDialogue.forEmotion(emotion)
            assertTrue("Expected Malkin or KUTTA in: $line", line.contains("Malkin") || line.contains("KUTTA"))
        }

        val events = listOf(
            KuttaEvent.AppOpenedMorning,
            KuttaEvent.NightRest,
            KuttaEvent.TaskCompleted("Test"),
            KuttaEvent.FocusStarted,
            KuttaEvent.FocusCompleted(25),
            KuttaEvent.WaterReminder,
            KuttaEvent.WaterLogged(500, 2000),
            KuttaEvent.StreakMaintained(3),
            KuttaEvent.PetTapped,
            KuttaEvent.CyclePeriodStarted,
            KuttaEvent.CyclePeriodEnded,
            KuttaEvent.CycleSymptomsLogged
        )
        events.forEach { ev ->
            val line = KuttaDialogue.forEvent(ev)
            assertTrue("Expected Malkin or KUTTA in: $line", line.contains("Malkin") || line.contains("KUTTA"))
        }

        // Rotation avoids immediate repetition for multi-line buckets
        KuttaDialogue.resetRotationForTest()
        val first = KuttaDialogue.forEvent(KuttaEvent.PetTapped)
        val second = KuttaDialogue.forEvent(KuttaEvent.PetTapped)
        assertTrue(first != second)
    }

    @Test
    fun cycleCareAnalyzer_computesCycleDayPredictionsAndApproachingStatus() {
        val empty = CycleCareAnalyzer.summarize(
            logs = emptyList(),
            configuredCycleLength = 28,
            configuredPeriodLength = 5,
            todayEpochDay = 20_000L
        )
        assertFalse(empty.hasLogs)
        assertFalse(empty.isCurrentlyOnPeriod)
        assertEquals(0, empty.currentCycleDay)

        val activeLogs = listOf(
            CycleLogEntity(
                id = 1L,
                startEpochDay = 19_972L,
                endEpochDay = 19_976L,
                flowLevel = "Medium",
                symptomsCsv = "Cramps"
            ),
            CycleLogEntity(
                id = 2L,
                startEpochDay = 20_000L,
                endEpochDay = null,
                flowLevel = "Heavy",
                symptomsCsv = "Cramps, Fatigue"
            )
        )
        val activeSummary = CycleCareAnalyzer.summarize(
            logs = activeLogs,
            configuredCycleLength = 30,
            configuredPeriodLength = 5,
            todayEpochDay = 20_001L
        )
        assertTrue(activeSummary.hasLogs)
        assertTrue(activeSummary.isCurrentlyOnPeriod)
        assertEquals(2L, activeSummary.activePeriodLogId)
        assertEquals(2, activeSummary.currentCycleDay)
        assertEquals(28, activeSummary.averageCycleLengthDays)
        assertEquals(listOf("Cramps", "Fatigue"), activeSummary.latestSymptoms)

        val approaching = CycleCareAnalyzer.summarize(
            logs = listOf(
                CycleLogEntity(
                    id = 3L,
                    startEpochDay = 20_000L,
                    endEpochDay = 20_004L
                )
            ),
            configuredCycleLength = 28,
            configuredPeriodLength = 5,
            todayEpochDay = 20_026L
        )
        assertFalse(approaching.isCurrentlyOnPeriod)
        assertEquals(27, approaching.currentCycleDay)
        assertEquals(2, approaching.daysUntilNextPeriod)
        assertTrue(approaching.isPeriodApproaching)
    }

    @Test
    fun friendSyncPayload_strictlyExcludesPrivateCycleCareData() {
        val prefs = UserPreferencesState(
            userName = "Malkin",
            userHandle = "@malkin.os",
            friendCode = "LIFE-MALK-1234",
            cycleCareEnabled = true,
            averageCycleLengthDays = 29,
            averagePeriodLengthDays = 5
        )
        val token = FriendSyncCodec.buildShareToken(prefs, challengesCompleted = 1, challengesTotal = 4)
        val encoded = token.removePrefix("LIFEOS1:")
        val json = String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8).lowercase()
        assertFalse(json.contains("cycle"))
        assertFalse(json.contains("period"))
        assertFalse(json.contains("symptom"))
        assertFalse(json.contains("flow"))
    }

    @Test
    fun productivityAnalyzer_producesDailyAndWeeklyActionableInsights() {
        val today = 20_000L
        val now = 1_700_000_000_000L
        val prefs = UserPreferencesState(
            isSignedIn = true,
            dailyFocusGoalMinutes = 120,
            dailyScreenLimitMinutes = 180,
            focusMinutesLoggedToday = 45,
            loginEpochDays = setOf(today - 2, today - 1, today)
        )
        val tasks = listOf(
            TaskEntity(
                title = "Organic Chemistry",
                priorityName = TaskPriority.HIGH.name,
                deadlineEpochDay = today,
                createdAtMillis = now - (3L * 3600L * 1000L),
                distractionMinutes = 25
            )
        )
        val usage = listOf(
            AppUsageLogEntity(
                appName = "Instagram",
                categoryName = AppUsageCategory.SOCIAL.name,
                minutesUsed = 50,
                isDistracting = true,
                dateEpochDay = today
            )
        )
        val progressLogs = listOf(
            DailyProgressLogEntity(epochDay = today - 1, focusMinutes = 90, xpEarned = 180, tasksCompleted = 3, distractingMinutes = 20),
            DailyProgressLogEntity(epochDay = today, focusMinutes = 45, xpEarned = 60, tasksCompleted = 0, distractingMinutes = 50)
        )

        val daily = ProductivityAnalyzer.analyzeDaily(prefs, tasks, emptyList(), usage, todayEpochDay = today, nowMillis = now)
        assertEquals(1, daily.agedPendingCount)
        assertEquals("Organic Chemistry", daily.focusTaskSuggestion)
        assertTrue(daily.recommendations.any { it.contains("Organic Chemistry") })

        val weekly = ProductivityAnalyzer.analyzeWeekly(prefs, tasks, usage, progressLogs, todayEpochDay = today)
        assertEquals(3, weekly.activeLoginDaysCount)
        assertEquals(135, weekly.weeklyFocusMinutes)
        assertEquals(70, weekly.weeklyDistractingMinutes)
    }

    @Test
    fun taskReminderScheduler_evaluatesQuietHoursCorrectly() {
        assertTrue(TaskReminderScheduler.isWithinQuietHours(currentHour = 23, quietHoursEnabled = true, startHour = 22, endHour = 7))
        assertTrue(TaskReminderScheduler.isWithinQuietHours(currentHour = 5, quietHoursEnabled = true, startHour = 22, endHour = 7))
        assertFalse(TaskReminderScheduler.isWithinQuietHours(currentHour = 14, quietHoursEnabled = true, startHour = 22, endHour = 7))
        assertFalse(TaskReminderScheduler.isWithinQuietHours(currentHour = 23, quietHoursEnabled = false, startHour = 22, endHour = 7))
    }

    @Test
    fun themeModes_includeThreeDistinctFeeloraThemes_withHybridLavenderDefault() {
        assertEquals(3, LifeOsThemeMode.entries.size)
        assertEquals(2, LifeOsThemeMode.entries.count { it.isDark })
        assertEquals(1, LifeOsThemeMode.entries.count { !it.isDark })
        assertEquals(LifeOsThemeMode.HYBRID_LAVENDER, UserPreferencesState().themeMode)
        assertTrue(LifeOsThemeMode.HYBRID_LAVENDER in LifeOsThemeMode.entries)
        assertTrue(LifeOsThemeMode.MIDNIGHT_AURORA in LifeOsThemeMode.entries)
        assertTrue(LifeOsThemeMode.MATCHA_MIST in LifeOsThemeMode.entries)
    }
}
