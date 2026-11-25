package com.example

import com.example.suru.LifeContextSnapshot
import com.example.suru.SuruActionParser
import com.example.suru.SuruAiProvider
import com.example.suru.SuruAiRouter
import com.example.suru.SuruBoundedMemory
import com.example.suru.SuruBoundedMemoryState
import com.example.suru.SuruChatActionCommand
import com.example.suru.SuruIntent
import com.example.suru.SuruNluEngine
import com.example.suru.SuruRouteTarget
import com.example.suru.SuruTurn
import com.example.suru.SuruWorkspaceSnapshot
import com.example.ui.SuruAiViewModel
import com.example.ui.SuruChatUiState
import com.example.data.audio.FeeloraOpeningChimePlayer
import com.example.data.audio.LifeOsSoundCue
import com.example.data.audio.LifeOsSoundManager
import com.example.data.local.CalendarEventEntity
import com.example.data.local.HabitEntity
import com.example.data.local.SuruMemoryEntity
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
import com.example.ui.screens.FeeloraAuthValidator
import com.example.ui.screens.FeeloraOnboardingSlides
import com.example.ui.theme.LifeOsThemeMode
import com.example.util.CycleCareAnalyzer
import com.example.util.ProductivityAnalyzer
import com.example.util.ProductivityCalculator
import java.util.Base64
import kotlinx.serialization.json.Json
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
    fun offlineAi_parsesScheduleEventTaskAndHabitCommands() {
        val eventRes = SuruNluEngine.parseAndPlan("Schedule Physics tomorrow at 8 AM.", sampleSnapshot(), emptyList(), emptyList(), emptyList(), null, null)
        assertNotNull(eventRes)
        assertEquals(SuruIntent.CREATE_EVENT, eventRes!!.intent)
        assertEquals("Physics", eventRes.title)
        assertEquals(1, eventRes.dateOffsetDays)
        assertEquals(8, eventRes.startHour)
        assertEquals(0, eventRes.startMinute)
        assertEquals(TaskCategory.STUDY, eventRes.category)

        val taskRes = SuruNluEngine.parseAndPlan("Add Chemical Equilibrium to my tasks.", sampleSnapshot(), emptyList(), emptyList(), emptyList(), null, null)
        assertNotNull(taskRes)
        assertEquals(SuruIntent.CREATE_TASK, taskRes!!.intent)
        assertEquals("Chemical Equilibrium", taskRes.title)
        assertEquals(TaskCategory.STUDY, taskRes.category)

        val notesTaskRes = SuruNluEngine.parseAndPlan("Add task Read 20 pages", cleanSlateSnapshot(), emptyList(), emptyList(), emptyList(), null, null)
        assertNotNull(notesTaskRes)
        assertEquals(SuruIntent.CREATE_TASK, notesTaskRes!!.intent)
        assertEquals("Read 20 pages", notesTaskRes.title)
    }

    @Test
    fun offlineAi_organizesTomorrowAndReportsInsightsIncludingCleanSlate() {
        val sampleTasks = listOf(TaskEntity(title = "Finish HP", priorityName = TaskPriority.HIGH.name, deadlineEpochDay = 20_000L))
        val plan = SuruNluEngine.parseAndPlan("Move today's tasks to tomorrow", sampleSnapshot(), sampleTasks, emptyList(), emptyList(), null, null, 20_000L)
        assertNotNull(plan)
        assertEquals(SuruIntent.RESCHEDULE_TASK, plan!!.intent)
        assertTrue(plan.rescheduleAllToday)

        val focusCheck = SuruNluEngine.parseAndPlan("What should I focus on today?", sampleSnapshot(), sampleTasks, emptyList(), emptyList(), null, null, 20_000L)
        assertNotNull(focusCheck)
        assertEquals(SuruIntent.OVERDUE_CHECK, focusCheck!!.intent)
        assertTrue(focusCheck.reply.contains("Finish HP"))

        val report = SuruNluEngine.parseAndPlan("Summarize my progress this month", sampleSnapshot(), sampleTasks, emptyList(), emptyList(), null, null, 20_000L)
        assertNotNull(report)
        assertEquals(SuruIntent.PROGRESS_SUMMARY, report!!.intent)
        assertTrue(report.reply.contains("progress summary", ignoreCase = true))

        val cleanInsight = SuruNluEngine.parseAndPlan("Summarize my progress", cleanSlateSnapshot(), emptyList(), emptyList(), emptyList(), null, null, 20_000L)
        assertNotNull(cleanInsight)
        assertEquals(SuruIntent.PROGRESS_SUMMARY, cleanInsight!!.intent)
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

        val codeLink = FriendSyncCodec.buildFriendCodeLink("flr-maya-7788")
        assertEquals("lifeos://friend?code=FLR-MAYA-7788", codeLink)
        assertEquals("FLR-MAYA-7788", FriendSyncCodec.extractCodeForInputField(codeLink))

        val deepLinked = FriendSyncCodec.parseInput(codeLink)
        assertNotNull(deepLinked)
        assertEquals("FLR-MAYA-7788", deepLinked!!.friendCode)
        assertEquals("Maya", deepLinked.displayName)

        val feeloraInvite = FriendSyncCodec.parseInput("feelora://invite?code=LIFE-NOAH-2048")
        assertNotNull(feeloraInvite)
        assertEquals("LIFE-NOAH-2048", feeloraInvite!!.friendCode)
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
    fun themeModes_includeLightDarkAndSystem() {
        assertEquals(3, LifeOsThemeMode.entries.size)
        assertEquals(1, LifeOsThemeMode.entries.count { it.isDark })
        assertEquals(2, LifeOsThemeMode.entries.count { !it.isDark })
    }

    @Test
    fun suruRouter_prioritizesOpenRouterFirstAndFallsBackOnceToGemini() {
        assertEquals(
            listOf(SuruRouteTarget.OPENROUTER, SuruRouteTarget.GEMINI_API_KEY),
            SuruAiRouter.buildRoutePlan(
                openRouterKey = "or-live-key",
                geminiKey = "gem-live-key"
            )
        )
        assertEquals(
            listOf(SuruRouteTarget.OPENROUTER),
            SuruAiRouter.buildRoutePlan(
                openRouterKey = "or-live-key",
                geminiKey = null
            )
        )
        assertEquals(
            listOf(SuruRouteTarget.GEMINI_API_KEY),
            SuruAiRouter.buildRoutePlan(
                openRouterKey = null,
                geminiKey = "gem-live-key"
            )
        )
        assertTrue(
            SuruAiRouter.buildRoutePlan(
                openRouterKey = "YOUR_OPENROUTER_API_KEY",
                geminiKey = "your_api_key_here"
            ).isEmpty()
        )
        assertEquals(
            listOf(SuruRouteTarget.GEMINI_API_KEY),
            SuruAiRouter.buildRoutePlan(
                openRouterKey = "sk-or-v1-ca9...e6a",
                geminiKey = "AQ.Ab8TestKey"
            )
        )

        assertEquals("trimmed-key", SuruAiRouter.configuredSecret("  \"trimmed-key\"  "))
        assertEquals("trimmed-single", SuruAiRouter.configuredSecret(" 'trimmed-single' "))
        assertEquals(null, SuruAiRouter.configuredSecret("sk-or-v1-ca9...e6a"))
        assertEquals(null, SuruAiRouter.configuredSecret("   "))
        assertEquals(null, SuruAiRouter.configuredSecret(null))

        val envResolved = SuruAiRouter.resolveSecret(
            primaryValue = { "YOUR_OPENROUTER_API_KEY" },
            envLookup = { key -> if (key == "OPENROUTER_API_KEY") "  sk-or-v1-live123  " else null },
            propLookup = { null },
            "OPENROUTER_API_KEY"
        )
        assertEquals("sk-or-v1-live123", envResolved)

        val propResolved = SuruAiRouter.resolveSecret(
            primaryValue = { throw IllegalStateException("missing") },
            envLookup = { null },
            propLookup = { key -> if (key == "GEMINI_API_KEY") "AIzaTestKey123" else null },
            "GEMINI_API_KEY"
        )
        assertEquals("AIzaTestKey123", propResolved)

        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=AIzaTestKey123",
            SuruAiRouter.geminiRestEndpoint("AIzaTestKey123")
        )
        assertTrue(
            SuruAiRouter.geminiRestEndpoint("AQ.Ab8TestKey")
                .startsWith("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=")
        )

        val normalized = SuruAiRouter.normalizeConversationTurns(
            listOf(
                SuruTurn(isFromUser = false, text = "Welcome"),
                SuruTurn(isFromUser = true, text = "Hello"),
                SuruTurn(isFromUser = true, text = "Retry"),
                SuruTurn(isFromUser = false, text = "   "),
                SuruTurn(isFromUser = false, text = "Hi there")
            )
        )
        assertEquals(2, normalized.size)
        assertTrue(normalized[0].isFromUser)
        assertEquals("Hello\nRetry", normalized[0].text)
        assertFalse(normalized[1].isFromUser)
        assertEquals("Hi there", normalized[1].text)

        assertEquals(
            "Take one slow breath first.",
            SuruAiRouter.extractOpenRouterContent(
                """{"choices":[{"message":{"role":"assistant","content":"  Take one slow breath first.  "}}]}"""
            )
        )
        assertEquals(
            "Step 1\nStep 2",
            SuruAiRouter.extractOpenRouterContent(
                """{"choices":[{"message":{"role":"assistant","content":[{"type":"text","text":"Step 1"},{"type":"text","text":"Step 2"}]}}]}"""
            )
        )

        val maskedProbe = SuruAiRouter.probeSecret(
            primaryValue = { "sk-or-v1-ca9...e6a" },
            envLookup = { null },
            propLookup = { null },
            keyPrefixForCode = "OPENROUTER",
            keys = arrayOf("OPENROUTER_API_KEY")
        )
        assertEquals(null, maskedProbe.value)
        assertEquals("masked(BuildConfig)", maskedProbe.status)
        assertEquals("OPENROUTER_KEY_MASKED", maskedProbe.errorCode)

        val emptyProbe = SuruAiRouter.probeSecret(
            primaryValue = { "" },
            envLookup = { null },
            propLookup = { null },
            keyPrefixForCode = "GEMINI",
            keys = arrayOf("GEMINI_API_KEY")
        )
        assertEquals(null, emptyProbe.value)
        assertEquals("GEMINI_KEY_EMPTY", emptyProbe.errorCode)

        val readyProbe = SuruAiRouter.probeSecret(
            primaryValue = { "AQ.Ab8SampleKey123456" },
            envLookup = { null },
            propLookup = { null },
            keyPrefixForCode = "GEMINI",
            keys = arrayOf("GEMINI_API_KEY")
        )
        assertEquals("AQ.Ab8SampleKey123456", readyProbe.value)
        assertEquals(null, readyProbe.errorCode)
        assertTrue(readyProbe.status.contains("AQ."))

        assertEquals(
            "Take it one step at a time.",
            SuruAiRouter.extractGeminiRestContent(
                """{"candidates":[{"content":{"parts":[{"text":"Take it one step "},{"text":"at a time."}]}}]}"""
            )
        )

        val redacted = SuruAiRouter.sanitizeDiagnostic(
            "HTTP 401 Bearer sk-or-v1-secret123456 failed at ?key=SecretQuery999 with AQ.Ab8SecretToken999",
            listOf("sk-or-v1-secret123456", "AQ.Ab8SecretToken999", "SecretQuery999")
        )
        assertFalse(redacted.contains("secret123456"))
        assertFalse(redacted.contains("SecretToken999"))
        assertFalse(redacted.contains("SecretQuery999"))

        val calmLower = SuruAiRouter.CALM_UNAVAILABLE_MESSAGE.lowercase()
        assertFalse(calmLower.contains("http"))
        assertFalse(calmLower.contains("openrouter"))
        assertFalse(calmLower.contains("gemini"))
        assertFalse(calmLower.contains("supabase"))
        assertFalse(calmLower.contains("api_key"))
    }

    @Test
    fun suruBoundedMemory_compactsOlderTurnsAndGuardsCycleAndCheckInPrivacy() {
        var memory = SuruBoundedMemoryState()
        for (i in 1..14) {
            memory = SuruBoundedMemory.appendTurn(
                memory,
                SuruTurn(isFromUser = i % 2 == 1, text = "Study goal turn $i for Organic Chemistry")
            )
        }
        assertEquals(SuruBoundedMemory.MAX_RECENT_TURNS, memory.recentTurns.size)
        assertTrue(memory.rollingSummary.isNotBlank())
        assertTrue(memory.rollingSummary.length <= SuruBoundedMemory.MAX_SUMMARY_CHARS)
        assertEquals("Study goal turn 14 for Organic Chemistry", memory.recentTurns.last().text)

        // Sensitive cycle_logs / daily_check_ins phrases are never retained in AI memory
        val afterSensitive = SuruBoundedMemory.appendTurn(
            memory,
            SuruTurn(isFromUser = true, text = "cycle_logs period flow heavy daily_check_ins note")
        )
        assertEquals(memory, afterSensitive)

        val memories = listOf(
            SuruMemoryEntity(id = 1, category = "STUDY_PREF", keyLabel = "Weak Subject", valueText = "Electrostatics numericals", updatedAtMillis = 100L),
            SuruMemoryEntity(id = 2, category = "TIME_PREF", keyLabel = "Best Time", valueText = "Evening 6-8 PM", updatedAtMillis = 200L),
            SuruMemoryEntity(id = 3, category = "WELLNESS_PREF", keyLabel = "cycle_logs", valueText = "Private flow info", updatedAtMillis = 300L),
            SuruMemoryEntity(id = 4, category = "GOAL", keyLabel = "Exam Target", valueText = "NEET Physics 160+", updatedAtMillis = 400L)
        )
        val filtered = SuruBoundedMemory.filterRelevantMemories("Help me revise Electrostatics", memories, maxItems = 5)
        assertFalse(filtered.any { it.keyLabel.contains("cycle", ignoreCase = true) })
        assertEquals("Weak Subject", filtered.first().keyLabel)
    }

    @Test
    fun suruAiViewModel_exposesCleanOutputOnlyStateWithoutBackendDetails() {
        assertEquals("gemini-3.8-flash", UserPreferencesState().suruDefaultModel)
        assertEquals("gemini-3.8-flash", SuruAiProvider.GEMINI.models.first())
        assertFalse(SuruAiProvider.GEMINI.models.any { it.startsWith("gemini-2.") || it.startsWith("gemini-1.5") })

        val viewModel = SuruAiViewModel()
        assertEquals(SuruChatUiState.Idle, viewModel.uiState.value.generationState)
        assertEquals("Calm", viewModel.uiState.value.selectedMood)
        assertEquals(SuruBoundedMemoryState(), viewModel.uiState.value.memoryState)
        val welcomeLower = viewModel.uiState.value.messages.first().text.lowercase()
        assertFalse(welcomeLower.contains("gemini"))
        assertFalse(welcomeLower.contains("openrouter"))
        assertFalse(welcomeLower.contains("supabase"))

        viewModel.selectMood("Focused")
        assertEquals("Focused", viewModel.uiState.value.selectedMood)
        viewModel.resetChat()
        assertEquals(1, viewModel.uiState.value.messages.size)
        assertEquals(SuruBoundedMemoryState(), viewModel.uiState.value.memoryState)
    }

    @Test
    fun suruActionParser_parsesActionsStripsTagsAndBuildsSafeContext() {
        val rawAssistantReply = """
            Got it! I added your Physics task and organized your evening schedule.

            <suru_actions>
            [
              {"type":"create_task","title":"Physics Ch.4 Numericals","category":"STUDY","priority":"HIGH","dueDateEpochDay":20100,"scheduledTime":"18:00","durationMinutes":45},
              {"type":"update_task","taskId":"7","titleMatch":"Chem Notes","newTitle":"Organic Chem Notes","scheduledTime":"19:00","dueDateEpochDay":20100,"priority":"MEDIUM"},
              {"type":"complete_task","titleMatch":"Morning Walk"},
              {"type":"delete_task","taskId":"9"},
              {"type":"setup_schedule","clearExistingForDate":true,"blocks":[{"title":"Deep Focus","startTime":"20:00","endTime":"20:45","category":"STUDY"}]},
              {"type":"create_habit","title":"Drink 2L Water","category":"HEALTH","targetPerWeek":7},
              {"type":"complete_habit","habitId":"3"}
            ]
            </suru_actions>
        """.trimIndent()

        val parsed = SuruActionParser.extractReplyAndActions(rawAssistantReply)
        assertEquals("Got it! I added your Physics task and organized your evening schedule.", parsed.visibleReply)
        assertFalse(parsed.visibleReply.contains("<suru_actions>"))
        assertEquals(7, parsed.commands.size)

        val createCmd = parsed.commands[0] as SuruChatActionCommand.CreateTask
        assertEquals("Physics Ch.4 Numericals", createCmd.title)
        assertEquals(TaskCategory.STUDY, createCmd.category)
        assertEquals(TaskPriority.HIGH, createCmd.priority)
        assertEquals(20100L, createCmd.dueDateEpochDay)
        assertEquals("18:00", createCmd.scheduledTime)
        assertEquals(45, createCmd.durationMinutes)

        val updateCmd = parsed.commands[1] as SuruChatActionCommand.UpdateTask
        assertEquals(7L, updateCmd.taskId)
        assertEquals("Organic Chem Notes", updateCmd.newTitle)
        assertEquals("19:00", updateCmd.scheduledTime)

        val completeCmd = parsed.commands[2] as SuruChatActionCommand.CompleteTask
        assertEquals("Morning Walk", completeCmd.titleMatch)

        val deleteCmd = parsed.commands[3] as SuruChatActionCommand.DeleteTask
        assertEquals(9L, deleteCmd.taskId)

        val scheduleCmd = parsed.commands[4] as SuruChatActionCommand.SetupSchedule
        assertTrue(scheduleCmd.clearExistingForDate)
        assertEquals(1, scheduleCmd.blocks.size)
        assertEquals("20:00", scheduleCmd.blocks[0].startTime)
        assertEquals("20:45", scheduleCmd.blocks[0].endTime)

        assertTrue(parsed.commands[5] is SuruChatActionCommand.CreateHabit)
        assertTrue(parsed.commands[6] is SuruChatActionCommand.CompleteHabit)

        // Malformed JSON inside <suru_actions> must still strip tag and never crash
        val malformed = SuruActionParser.extractReplyAndActions(
            "Here is your plan.\n<suru_actions>{not valid json</suru_actions>"
        )
        assertEquals("Here is your plan.", malformed.visibleReply)
        assertTrue(malformed.commands.isEmpty())

        assertEquals(18 * 60 + 30, SuruActionParser.parseTimeToMinuteOfDay("18:30"))
        assertEquals(18 * 60 + 30, SuruActionParser.parseTimeToMinuteOfDay("6:30 PM"))

        val snapshot = SuruWorkspaceSnapshot(
            todayEpochDay = 20100L,
            currentDateFormatted = "Mon, Jan 12",
            currentTimeFormatted = "2:00 PM (14:00)",
            currentMinuteOfDay = 14 * 60,
            tasks = listOf(
                TaskEntity(id = 1, title = "Revise Kinematics", priorityName = TaskPriority.HIGH.name, categoryName = TaskCategory.STUDY.name, deadlineEpochDay = 20099L),
                TaskEntity(id = 2, title = "cycle_logs private note", deadlineEpochDay = 20100L)
            ),
            events = listOf(
                CalendarEventEntity(id = 10, title = "Math Lecture", dateEpochDay = 20100L, startMinuteOfDay = 15 * 60, durationMinutes = 60, categoryName = TaskCategory.STUDY.name)
            ),
            habits = listOf(
                HabitEntity(id = 5, title = "Evening Stretch", currentStreak = 4, lastCompletedEpochDay = 20100L)
            )
        )
        val contextSection = SuruActionParser.buildContextPromptSection(snapshot)
        assertTrue(contextSection.contains("Revise Kinematics"))
        assertTrue(contextSection.contains("OVERDUE(20099)"))
        assertTrue(contextSection.contains("Math Lecture"))
        assertTrue(contextSection.contains("14:00-15:00"))
        assertTrue(contextSection.contains("16:00-23:00"))
        assertTrue(contextSection.contains("Evening Stretch"))
        assertFalse(contextSection.contains("cycle_logs"))
        assertFalse(contextSection.contains("daily_check_ins"))
    }

    @Test
    fun feeloraOnboardingSlides_haveExact7SlidesWithCleanCorrectedCopy() {
        assertEquals(7, FeeloraOnboardingSlides.size)
        assertEquals(
            listOf(
                "Manage Your Tasks",
                "Plan Your Study",
                "Stay on Schedule",
                "Take Care of Yourself",
                "Meet SURU",
                "Your Companion KUTTA",
                "Together One"
            ),
            FeeloraOnboardingSlides.map { it.title }
        )
        assertEquals(
            listOf(
                "Stay organized and never miss anything.",
                "Track subjects, chapters, and study goals.",
                "Manage deadlines, reminders, and more.",
                "Track water, mood, and wellness.",
                "Your intelligent AI assistant.",
                "Always by your side.",
                "Grow together, achieve more."
            ),
            FeeloraOnboardingSlides.map { it.description }
        )
    }

    @Test
    fun feeloraAuthValidator_andOpeningChimeWav_workCleanly() {
        assertNotNull(FeeloraAuthValidator.validateEmail(""))
        assertNotNull(FeeloraAuthValidator.validateEmail("invalid-email"))
        assertEquals(null, FeeloraAuthValidator.validateEmail("aria@feelora.app"))

        assertNotNull(FeeloraAuthValidator.validatePassword("", isCreateAccount = false))
        assertNotNull(FeeloraAuthValidator.validatePassword("12345", isCreateAccount = true))
        assertEquals(null, FeeloraAuthValidator.validatePassword("123456", isCreateAccount = true))
        assertEquals(null, FeeloraAuthValidator.validatePassword("pass", isCreateAccount = false))

        val chime = FeeloraOpeningChimePlayer.buildAmbientChimeWav()
        val sparkle = FeeloraOpeningChimePlayer.buildSparkleTapWav()
        assertTrue(chime.size > 44)
        assertTrue(sparkle.size > 44)
        assertEquals('R'.code.toByte(), chime[0])
        assertEquals('W'.code.toByte(), chime[8])
        assertEquals('R'.code.toByte(), sparkle[0])
        assertEquals('W'.code.toByte(), sparkle[8])
    }
}
