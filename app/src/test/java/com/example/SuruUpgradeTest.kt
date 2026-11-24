package com.example

import android.content.Context
import android.speech.SpeechRecognizer
import androidx.compose.ui.graphics.Color
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.suru.LifeContextSnapshot
import com.example.data.local.CalendarEventEntity
import com.example.data.local.KUTTA_NAME
import com.example.data.local.LifeOsDatabase
import com.example.data.local.SuruMemoryEntity
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import com.example.data.local.UserAvatarPreset
import com.example.data.local.UserPreferencesState
import com.example.notifications.TaskReminderScheduler
import com.example.suru.CalendarIntelligence
import com.example.suru.SuruActionEngine
import com.example.suru.SuruIntelligenceService
import com.example.suru.SuruIntent
import com.example.suru.SuruNluEngine
import com.example.suru.SuruStudyHelpMode
import com.example.suru.SuruStudyPlanner
import com.example.suru.SuruTimeWindow
import com.example.suru.SuruVoiceLanguage
import com.example.suru.SuruVoiceManager
import com.example.suru.SuruVoiceState
import com.example.suru.TaskIntelligence
import com.example.ui.theme.AuroraPrimary
import com.example.ui.theme.AuroraVoid
import com.example.ui.theme.HybridLavenderBg
import com.example.ui.theme.HybridLavenderPrimary
import com.example.ui.theme.MatchaBg
import com.example.ui.theme.MatchaPrimary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SuruUpgradeTest {

    private fun baseSnapshot() = LifeContextSnapshot(
        userName = "Malkin",
        level = 3,
        totalXp = 480,
        dailyStreak = 4,
        pendingTasksSummary = "Revise Waves (High)",
        pendingTaskTitles = listOf("Revise Waves"),
        completedTasksCount = 2,
        upcomingEventsSummary = "Math Class at 9:00 AM",
        screenTimeTodayMinutes = 60,
        focusMinutesToday = 45,
        dailyFocusGoalMinutes = 120,
        habitsCompletedToday = 1,
        habitsTotal = 2,
        productivityScore = 72
    )

    @Test
    fun hinglishMultiTurnStudyPlanner_collectsMissingSlotsAndBuildsWeightedPlan() {
        val today = 20_500L

        // Turn 1: Hinglish goal with days and subject, missing chapters & daily time
        val turn1 = SuruNluEngine.parseAndPlan(
            userPrompt = "Suru, mujhe agle 5 din mein Physics ke 3 chapters complete karne hain.",
            context = baseSnapshot(),
            tasks = emptyList(),
            events = emptyList(),
            memories = emptyList(),
            pendingContext = null,
            lastStudyDraft = null,
            todayEpochDay = today
        )
        assertNotNull(turn1)
        assertEquals(SuruIntent.ASK_CLARIFICATION, turn1!!.intent)
        assertEquals("CHAPTERS", turn1.updatedPendingContext?.missingSlot)
        assertEquals("Physics", turn1.updatedPendingContext?.subject)
        assertEquals(5, turn1.updatedPendingContext?.deadlineDays)

        // Turn 2: Chapters provided, asks for daily available time
        val turn2 = SuruNluEngine.parseAndPlan(
            userPrompt = "Electrostatics, Current Electricity aur Magnetism.",
            context = baseSnapshot(),
            tasks = emptyList(),
            events = emptyList(),
            memories = emptyList(),
            pendingContext = turn1.updatedPendingContext,
            lastStudyDraft = null,
            todayEpochDay = today
        )
        assertNotNull(turn2)
        assertEquals(SuruIntent.ASK_CLARIFICATION, turn2!!.intent)
        assertEquals("DAILY_TIME", turn2.updatedPendingContext?.missingSlot)
        assertEquals(3, turn2.updatedPendingContext?.chapters?.size)

        // Turn 3: Daily time & window in Hinglish -> full confirmation draft
        val turn3 = SuruNluEngine.parseAndPlan(
            userPrompt = "2 ghante shaam ko",
            context = baseSnapshot(),
            tasks = emptyList(),
            events = emptyList(),
            memories = emptyList(),
            pendingContext = turn2.updatedPendingContext,
            lastStudyDraft = null,
            todayEpochDay = today
        )
        assertNotNull(turn3)
        assertEquals(SuruIntent.CREATE_STUDY_PLAN, turn3!!.intent)
        assertTrue(turn3.requiresConfirmation)
        val draft = turn3.studyPlanDraft
        assertNotNull(draft)
        assertEquals(5, draft!!.deadlineDays)
        assertEquals(120, draft.dailyAvailableMinutes)
        assertEquals(SuruTimeWindow.EVENING, draft.preferredWindow)
        assertTrue(draft.sessions.isNotEmpty())
        // Heavy chapter weight should be >= light chapter weight
        assertTrue((draft.chapterWeights["Electrostatics"] ?: 1f) >= 1.25f)

        // Conversational follow-up correction on proposed plan
        val corrected = SuruNluEngine.parseAndPlan(
            userPrompt = "Actually, make it night and 90 mins",
            context = baseSnapshot(),
            tasks = emptyList(),
            events = emptyList(),
            memories = emptyList(),
            pendingContext = null,
            lastStudyDraft = draft,
            todayEpochDay = today
        )
        val correctedDraft = corrected?.studyPlanDraft
        assertNotNull(correctedDraft)
        assertEquals(SuruTimeWindow.NIGHT, correctedDraft!!.preferredWindow)
        assertEquals(90, correctedDraft.dailyAvailableMinutes)
    }

    @Test
    fun calendarAndTaskIntelligence_detectDuplicatesOverlapsAndBacklogPriority() {
        val today = 20_500L
        val existingEvent = CalendarEventEntity(
            id = 10L,
            title = "Physics Lecture",
            dateEpochDay = today + 1,
            startMinuteOfDay = 18 * 60 + 30,
            durationMinutes = 60
        )

        val overlaps = CalendarIntelligence.findOverlaps(
            dateEpochDay = today + 1,
            startMinuteOfDay = 18 * 60 + 45,
            durationMinutes = 60,
            existingEvents = listOf(existingEvent)
        )
        assertEquals(1, overlaps.size)

        val (resolvedMinute, _) = CalendarIntelligence.findConflictFreeStartMinute(
            dateEpochDay = 1,
            todayEpochDay = today,
            preferredStartMinute = 18 * 60 + 30,
            durationMinutes = 60,
            existingEvents = listOf(existingEvent)
        )
        assertTrue(resolvedMinute != 18 * 60 + 30)

        val existingTask = TaskEntity(
            id = 1L,
            title = "Solve Electrostatics Sheet",
            priorityName = TaskPriority.MEDIUM.name,
            deadlineEpochDay = today - 2,
            isCompleted = false
        )
        val doneTask = TaskEntity(
            id = 2L,
            title = "Completed Notes",
            priorityName = TaskPriority.HIGH.name,
            deadlineEpochDay = today - 1,
            isCompleted = true
        )
        val criticalTask = TaskEntity(
            id = 3L,
            title = "Mock Test Error Analysis",
            priorityName = TaskPriority.CRITICAL.name,
            deadlineEpochDay = today,
            isCompleted = false
        )

        assertNotNull(TaskIntelligence.findDuplicate("solve electrostatics sheet", listOf(existingTask)))
        val subtasks = TaskIntelligence.buildSubtasks("Physics Chapter 2")
        assertEquals(3, subtasks.size)

        val backlog = SuruStudyPlanner.buildBacklogRecoveryPlan(
            tasks = listOf(existingTask, doneTask, criticalTask),
            existingEvents = listOf(existingEvent),
            dailyFocusGoalMinutes = 120,
            todayEpochDay = today
        )
        // Completed task is never touched; critical & overdue items are scheduled
        assertEquals(2, backlog.items.size)
        assertFalse(backlog.items.any { it.taskId == doneTask.id })
        assertEquals(criticalTask.id, backlog.items.first().taskId)
    }

    @Test
    fun actionEngine_executesStudyPlanTaskDeleteAndUndoCleanly() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LifeOsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val engine = SuruActionEngine(db)
            val today = 20_500L
            val draft = SuruStudyPlanner.buildStudyPlanDraft(
                subject = "Physics",
                chapters = listOf("Electrostatics", "Magnetism"),
                deadlineDays = 3,
                dailyAvailableMinutes = 90,
                preferredWindow = SuruTimeWindow.EVENING,
                startHour = 18,
                startMinute = 30,
                existingEvents = emptyList(),
                todayEpochDay = today
            )

            val planOutcome = engine.executeStudyPlan(draft, today)
            assertNotNull(planOutcome.undoRecord)
            assertEquals(2, db.taskDao().getAllTasksOnce().size)
            assertTrue(db.calendarEventDao().getAllEventsOnce().isNotEmpty())
            assertEquals(1, db.suruStudyPlanDao().getAllPlansOnce().size)

            // Undo removes everything inserted by that study plan
            engine.undo(planOutcome.undoRecord!!)
            assertEquals(0, db.taskDao().getAllTasksOnce().size)
            assertEquals(0, db.calendarEventDao().getAllEventsOnce().size)
            assertEquals(0, db.suruStudyPlanDao().getAllPlansOnce().size)

            // Delete task confirmation & undo restores deleted task
            val seededId = db.taskDao().insertTask(
                TaskEntity(
                    title = "Organic Chemistry Notes",
                    categoryName = TaskCategory.STUDY.name,
                    deadlineEpochDay = today
                )
            )
            val seededTask = db.taskDao().getAllTasksOnce().first { it.id == seededId }
            val deleteAction = SuruNluEngine.parseAndPlan(
                userPrompt = "Delete task Organic Chemistry Notes",
                context = baseSnapshot(),
                tasks = listOf(seededTask),
                events = emptyList(),
                memories = emptyList(),
                pendingContext = null,
                lastStudyDraft = null,
                todayEpochDay = today
            )
            assertNotNull(deleteAction)
            assertEquals(SuruIntent.DELETE_TASK, deleteAction!!.intent)
            assertTrue(deleteAction.requiresConfirmation)

            val deleteOutcome = engine.executeStructuredAction(deleteAction, today)
            assertEquals(0, db.taskDao().getAllTasksOnce().size)
            engine.undo(deleteOutcome.undoRecord!!)
            assertEquals(1, db.taskDao().getAllTasksOnce().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun roomMigration5To6_preservesPreExistingDataAndCreatesSuruTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_migration_5_6_${System.currentTimeMillis()}.db"
        val factory = FrameworkSQLiteOpenHelperFactory()
        val v5Callback = object : SupportSQLiteOpenHelper.Callback(5) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sectionId` INTEGER NOT NULL DEFAULT 0, `title` TEXT NOT NULL, `description` TEXT NOT NULL DEFAULT '', `priorityName` TEXT NOT NULL DEFAULT 'MEDIUM', `categoryName` TEXT NOT NULL DEFAULT 'STUDY', `deadlineEpochDay` INTEGER NOT NULL DEFAULT 0, `recurrenceName` TEXT NOT NULL DEFAULT 'NONE', `isCompleted` INTEGER NOT NULL DEFAULT 0, `xpReward` INTEGER NOT NULL DEFAULT 25, `distractionMinutes` INTEGER NOT NULL DEFAULT 0, `focusMinutesSpent` INTEGER NOT NULL DEFAULT 0, `createdAtMillis` INTEGER NOT NULL DEFAULT 0, `completedAtMillis` INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cycle_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startEpochDay` INTEGER NOT NULL, `endEpochDay` INTEGER, `flowLevel` TEXT NOT NULL DEFAULT 'Medium', `symptomsCsv` TEXT NOT NULL DEFAULT '', `notes` TEXT NOT NULL DEFAULT '', `createdAtMillis` INTEGER NOT NULL DEFAULT 0)"
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }
        val helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(v5Callback)
                .build()
        )
        val writableV5 = helper.writableDatabase
        writableV5.execSQL("INSERT INTO `tasks` (`title`, `deadlineEpochDay`) VALUES ('Preserved Task', 20500)")
        writableV5.execSQL("INSERT INTO `cycle_logs` (`startEpochDay`, `flowLevel`) VALUES (20495, 'Medium')")

        // Apply MIGRATION_5_6 cleanly
        LifeOsDatabase.MIGRATION_5_6.migrate(writableV5)

        writableV5.query("SELECT title FROM `tasks`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Preserved Task", cursor.getString(0))
        }
        writableV5.query("SELECT flowLevel FROM `cycle_logs`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Medium", cursor.getString(0))
        }

        // Verify new v6 SURU tables are present and usable
        writableV5.execSQL(
            "INSERT INTO `suru_memories` (`category`, `keyLabel`, `valueText`) VALUES ('STUDY_PREF', 'Peak Hour', 'Evening 6:30 PM')"
        )
        writableV5.execSQL(
            "INSERT INTO `suru_study_plans` (`subject`, `chaptersCsv`, `startEpochDay`, `deadlineDays`, `dailyMinutesBudget`, `preferredWindow`) VALUES ('Physics', 'Optics', 20500, 5, 90, 'Evening')"
        )
        writableV5.query("SELECT COUNT(*) FROM `suru_memories`").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        writableV5.query("SELECT COUNT(*) FROM `suru_study_plans`").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        helper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun studyHelpAndBriefings_preserveNotesDistinctionAndPrivacyIsolation() {
        val withNotes = SuruIntelligenceService.buildLocalStudyHelp(
            topic = "Gauss's Law",
            mode = SuruStudyHelpMode.EXAM_ORIENTED,
            userNotes = "Electric flux through a closed surface equals enclosed charge divided by epsilon zero. Gaussian surfaces simplify symmetric fields."
        )
        assertTrue(withNotes.isBasedOnUserNotes)
        assertTrue(withNotes.sourceDisclaimer.contains("notes", ignoreCase = true))
        assertEquals(2, withNotes.flashcards.size)

        val conceptual = SuruIntelligenceService.buildLocalStudyHelp(
            topic = "Thermodynamics",
            mode = SuruStudyHelpMode.BEGINNER,
            userNotes = ""
        )
        assertFalse(conceptual.isBasedOnUserNotes)
        assertTrue(conceptual.sourceDisclaimer.contains("conceptual study framework", ignoreCase = true))

        val prefs = UserPreferencesState(
            userName = "Suryakant",
            dailyFocusGoalMinutes = 120,
            waterIntakeMlToday = 1000,
            dailyWaterGoalMl = 2000,
            cycleCareEnabled = true
        )
        val morning = SuruIntelligenceService.buildMorningBriefing(
            prefs = prefs,
            tasks = listOf(TaskEntity(title = "Finish Magnetism", deadlineEpochDay = 20_500L)),
            events = emptyList(),
            todayEpochDay = 20_500L
        )
        val combinedText = (morning.headline + morning.focusSummary + morning.wellnessNote + morning.priorityTasks.joinToString()).lowercase()
        assertTrue(combinedText.contains("finish magnetism"))
        assertFalse(combinedText.contains("period"))
        assertFalse(combinedText.contains("cycle"))
    }

    @Test
    fun voiceManagerBriefingWindowAndFeeloraThemeTokens_behaveCorrectly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val voiceManager = SuruVoiceManager(context)
        try {
            // Without RECORD_AUDIO runtime grant, startListening moves safely to FAILED with clear message
            voiceManager.startListening(SuruVoiceLanguage.HINGLISH) {}
            assertEquals(SuruVoiceState.FAILED, voiceManager.voiceState.value)
            assertNotNull(voiceManager.voiceError.value)

            voiceManager.clearError()
            assertEquals(SuruVoiceState.READY, voiceManager.voiceState.value)
            assertNull(voiceManager.voiceError.value)

            assertTrue(
                SuruVoiceManager.mapRecognitionError(SpeechRecognizer.ERROR_NETWORK)
                    .contains("connectivity", ignoreCase = true)
            )
        } finally {
            voiceManager.release()
        }

        // Briefing window firing guard
        assertTrue(TaskReminderScheduler.shouldFireBriefingForWindow(currentHour = 8, targetHour = 8, lastFiredDay = 100L, todayEpochDay = 101L))
        assertFalse(TaskReminderScheduler.shouldFireBriefingForWindow(currentHour = 8, targetHour = 8, lastFiredDay = 101L, todayEpochDay = 101L))
        assertFalse(TaskReminderScheduler.shouldFireBriefingForWindow(currentHour = 9, targetHour = 8, lastFiredDay = 100L, todayEpochDay = 101L))

        // FEELORA 2.0 3-theme palette and distinct identities
        assertEquals(Color(0xFF161124), HybridLavenderBg)
        assertEquals(Color(0xFFA78BFA), HybridLavenderPrimary)
        assertEquals(Color(0xFF090B14), AuroraVoid)
        assertEquals(Color(0xFF8B5CF6), AuroraPrimary)
        assertEquals(Color(0xFFF3F6EE), MatchaBg)
        assertEquals(Color(0xFF2F6B4F), MatchaPrimary)
        assertEquals("KUTTA", KUTTA_NAME)
        assertEquals(UserAvatarPreset.STAR_BUNNY, UserPreferencesState().avatarPreset)
        assertTrue(UserPreferencesState().avatarPreset.label.contains("BUNNI"))
    }
}
