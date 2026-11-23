package com.example

import com.example.data.ai.LifeContextSnapshot
import com.example.data.ai.LocalLifeAiEngine
import com.example.data.audio.LifeOsSoundCue
import com.example.data.audio.LifeOsSoundManager
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskSectionEntity
import com.example.data.local.TaskSectionMapper
import com.example.data.local.UserPreferencesState
import com.example.data.social.FriendSyncCodec
import com.example.util.ProductivityCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for LIFE OS core offline intelligence, productivity scoring,
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
        // Focus sharing was disabled -> masked to 0
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
}
