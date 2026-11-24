package com.example

import com.example.data.ai.LifeContextSnapshot
import com.example.data.ai.LocalLifeAiEngine
import com.example.data.audio.LifeOsSoundCue
import com.example.data.audio.LifeOsSoundManager
import com.example.data.local.AppUsageCategory
import com.example.data.local.AppUsageLogEntity
import com.example.data.local.CompanionMood
import com.example.data.local.CompanionMoodResolver
import com.example.data.local.CompanionSpecies
import com.example.data.local.DailyProgressLogEntity
import com.example.data.local.PersonalityQuizEvaluator
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import com.example.data.local.TaskSectionEntity
import com.example.data.local.TaskSectionMapper
import com.example.data.local.UserPreferencesState
import com.example.data.social.FriendSyncCodec
import com.example.notifications.TaskReminderScheduler
import com.example.ui.theme.LifeOsThemeMode
import com.example.util.ProductivityAnalyzer
import com.example.util.ProductivityCalculator
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
        val eventRes = LocalLifeAiEngine.process("Schedule Physics tomorrow at 8 AM.", sampleSnapshot())
        assertEquals("CREATE_EVENT", eventRes.actionType)
        assertEquals("Physics", eventRes.title)
        assertEquals(1, eventRes.dateOffsetDays)
        assertEquals(8, eventRes.startHour)
        assertEquals(0, eventRes.startMinute)
        assertEquals(TaskCategory.STUDY, eventRes.category)

        val taskRes = LocalLifeAiEngine.process("Add Chemical Equilibrium to my tasks.", sampleSnapshot())
        assertEquals("CREATE_TASK", taskRes.actionType)
        assertEquals("Chemical Equilibrium", taskRes.title)
        assertEquals(TaskCategory.STUDY, taskRes.category)

        val habitRes = LocalLifeAiEngine.process("Add habit Read 20 pages", cleanSlateSnapshot())
        assertEquals("CREATE_HABIT", habitRes.actionType)
        assertEquals("Read 20 pages", habitRes.title)
    }

    @Test
    fun offlineAi_organizesTomorrowAndReportsInsightsIncludingCleanSlate() {
        val plan = LocalLifeAiEngine.process("Organize tomorrow based on my pending tasks.", sampleSnapshot())
        assertEquals("ORGANIZE_TOMORROW", plan.actionType)
        assertTrue(plan.reply.contains("Finish HP"))

        val report = LocalLifeAiEngine.process("Show my weekly productivity.", sampleSnapshot())
        assertEquals("NONE", report.actionType)
        assertTrue(report.reply.contains("78%"))
        assertTrue(report.reply.contains("Suryakant"))

        val cleanInsight = LocalLifeAiEngine.process("Show my productivity insights", cleanSlateSnapshot())
        assertEquals("NONE", cleanInsight.actionType)
        assertTrue(cleanInsight.reply.contains("Clean slate active"))
        assertTrue(cleanInsight.reply.contains("NovaUser"))
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
    fun personalityQuiz_appointsDeterministicCompanionSpecies() {
        assertEquals(CompanionSpecies.MOCHI_BEAR, PersonalityQuizEvaluator.appointCompanion(0, 0, 0))
        assertEquals(CompanionSpecies.PEBBLE_FOX, PersonalityQuizEvaluator.appointCompanion(1, 2, 1))
        assertEquals(CompanionSpecies.PIP_OWL, PersonalityQuizEvaluator.appointCompanion(2, 1, 3))
        assertEquals(CompanionSpecies.BOBA_OTTER, PersonalityQuizEvaluator.appointCompanion(3, 3, 2))
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
            CompanionMood.CHILL,
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
    fun themeModes_includeOneDarkAndThreeLightModes() {
        assertEquals(4, LifeOsThemeMode.entries.size)
        assertEquals(1, LifeOsThemeMode.entries.count { it.isDark })
        assertEquals(3, LifeOsThemeMode.entries.count { !it.isDark })
    }
}
