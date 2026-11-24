package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.LifeContextSnapshot
import com.example.data.audio.LifeOsSoundCue
import com.example.data.audio.LifeOsSoundManager
import com.example.data.local.CalendarEventEntity
import com.example.data.local.LifeOsDatabase
import com.example.data.local.TaskCategory
import com.example.data.local.TaskEntity
import com.example.data.local.TaskPriority
import com.example.data.local.UserPreferencesState
import com.example.notifications.AppPermissionId
import com.example.notifications.AppPermissionStatus
import com.example.notifications.PermissionCenterHelper
import com.example.notifications.TaskReminderPolicy
import com.example.suru.SuruActionEngine
import com.example.suru.SuruImageLoadResult
import com.example.suru.SuruImageProcessor
import com.example.suru.SuruIntelligenceService
import com.example.suru.SuruIntent
import com.example.suru.SuruNluEngine
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PermissionSetupScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.theme.LifeOsTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FeeloraUpgradeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun baseSnapshot() = LifeContextSnapshot(
        userName = "Malkin",
        level = 3,
        totalXp = 480,
        dailyStreak = 4,
        pendingTasksSummary = "Electrostatics Sheet (High)",
        pendingTaskTitles = listOf("Electrostatics Sheet"),
        completedTasksCount = 4,
        upcomingEventsSummary = "Physics Lecture at 9:00 AM",
        screenTimeTodayMinutes = 55,
        focusMinutesToday = 45,
        dailyFocusGoalMinutes = 120,
        habitsCompletedToday = 1,
        habitsTotal = 2,
        productivityScore = 76
    )

    @Test
    fun roomMigration6To7_preservesExistingTasksAndAddsSmartReminderAndAttachmentColumns() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_migration_6_7_${System.currentTimeMillis()}.db"
        val factory = FrameworkSQLiteOpenHelperFactory()
        val v6Callback = object : SupportSQLiteOpenHelper.Callback(6) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sectionId` INTEGER NOT NULL DEFAULT 0, `title` TEXT NOT NULL, `description` TEXT NOT NULL DEFAULT '', `priorityName` TEXT NOT NULL DEFAULT 'MEDIUM', `categoryName` TEXT NOT NULL DEFAULT 'STUDY', `deadlineEpochDay` INTEGER NOT NULL DEFAULT 0, `recurrenceName` TEXT NOT NULL DEFAULT 'NONE', `isCompleted` INTEGER NOT NULL DEFAULT 0, `xpReward` INTEGER NOT NULL DEFAULT 25, `distractionMinutes` INTEGER NOT NULL DEFAULT 0, `focusMinutesSpent` INTEGER NOT NULL DEFAULT 0, `createdAtMillis` INTEGER NOT NULL DEFAULT 0, `completedAtMillis` INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `isFromUser` INTEGER NOT NULL, `text` TEXT NOT NULL, `actionBadge` TEXT, `createdAtMillis` INTEGER NOT NULL DEFAULT 0)"
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }
        val helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(v6Callback)
                .build()
        )
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO `tasks` (`title`, `deadlineEpochDay`) VALUES ('Preserved v6 Task', 20510)")
        db.execSQL("INSERT INTO `ai_messages` (`isFromUser`, `text`) VALUES (1, 'Hello SURU')")

        LifeOsDatabase.MIGRATION_6_7.migrate(db)

        db.query("SELECT title, snoozedUntilMillis, reminderCountToday, remindersDismissed FROM `tasks`").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Preserved v6 Task", c.getString(0))
            assertEquals(0L, c.getLong(1))
            assertEquals(0, c.getInt(2))
            assertEquals(0, c.getInt(3))
        }
        db.query("SELECT text, attachmentSummary FROM `ai_messages`").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Hello SURU", c.getString(0))
            assertNull(c.getString(1))
        }
        helper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun taskReminderPolicy_respectsActiveCalendarEventsSnoozeDismissalAndCaps() {
        val today = 20_500L
        val now = 1_700_000_000_000L
        val busyEvent = CalendarEventEntity(
            id = 1L,
            title = "Deep Focus Lab",
            dateEpochDay = today,
            startMinuteOfDay = 10 * 60,
            durationMinutes = 60
        )
        val overdueTask = TaskEntity(
            id = 1L,
            title = "Overdue Lab Report",
            priorityName = TaskPriority.MEDIUM.name,
            deadlineEpochDay = today - 1
        )
        val highTask = TaskEntity(
            id = 2L,
            title = "High Priority Math",
            priorityName = TaskPriority.HIGH.name,
            deadlineEpochDay = today
        )
        val snoozedTask = TaskEntity(
            id = 3L,
            title = "Snoozed Task",
            deadlineEpochDay = today,
            snoozedUntilMillis = now + 3_600_000L
        )
        val dismissedTask = TaskEntity(
            id = 4L,
            title = "Dismissed Task",
            deadlineEpochDay = today,
            remindersDismissed = true
        )
        val cappedTask = TaskEntity(
            id = 5L,
            title = "Capped Task",
            deadlineEpochDay = today,
            lastReminderEpochDay = today,
            reminderCountToday = 3
        )
        val tooRecentTask = TaskEntity(
            id = 6L,
            title = "Recently Reminded",
            deadlineEpochDay = today,
            lastRemindedAtMillis = now - 30 * 60_000L
        )

        // During an active calendar event -> no reminders fired
        val duringEvent = TaskReminderPolicy.selectEligibleTasks(
            tasks = listOf(overdueTask, highTask),
            events = listOf(busyEvent),
            todayEpochDay = today,
            minuteOfDay = 10 * 60 + 15,
            nowMillis = now,
            frequencyHours = 2,
            maxRemindersPerTaskPerDay = 3
        )
        assertTrue(duringEvent.isEmpty())

        // Outside event -> excludes snoozed, dismissed, capped, too-recent, and sorts overdue first
        val eligible = TaskReminderPolicy.selectEligibleTasks(
            tasks = listOf(highTask, overdueTask, snoozedTask, dismissedTask, cappedTask, tooRecentTask),
            events = listOf(busyEvent),
            todayEpochDay = today,
            minuteOfDay = 12 * 60,
            nowMillis = now,
            frequencyHours = 2,
            maxRemindersPerTaskPerDay = 3
        )
        assertEquals(listOf(1L, 2L), eligible.map { it.id })
    }

    @Test
    fun suruJsonParserAndNlu_handleActionIntentsAndPrivacyIsolation() = runTest {
        val today = 20_500L
        val tasks = listOf(
            TaskEntity(id = 11L, title = "Organic Chemistry", categoryName = TaskCategory.STUDY.name, deadlineEpochDay = today),
            TaskEntity(id = 12L, title = "Physics Numericals", categoryName = TaskCategory.STUDY.name, deadlineEpochDay = today - 1)
        )

        val sysPrompt = SuruIntelligenceService.buildSystemInstruction(
            context = baseSnapshot(),
            tasks = tasks,
            events = emptyList(),
            studyPlans = emptyList(),
            enabledMemories = emptyList(),
            pendingContext = null,
            hasImage = true,
            todayEpochDay = today
        )
        assertTrue(sysPrompt.contains("Electrostatics") || sysPrompt.contains("Organic Chemistry"))
        assertFalse(sysPrompt.lowercase().contains("cycle_logs"))
        assertFalse(sysPrompt.lowercase().contains("period flow"))

        val parsedMove = SuruIntelligenceService.parseStructuredJsonToAction(
            rawJson = """{"intent":"RESCHEDULE_TASK","reply":"Moved today's tasks to tomorrow.","dateOffsetDays":1,"rescheduleAllToday":true}""",
            context = baseSnapshot(),
            tasks = tasks,
            events = emptyList(),
            todayEpochDay = today
        )
        assertNotNull(parsedMove)
        assertEquals(SuruIntent.RESCHEDULE_TASK, parsedMove!!.intent)
        assertTrue(parsedMove.rescheduleAllToday)

        val parsedDelete = SuruIntelligenceService.parseStructuredJsonToAction(
            rawJson = """{"intent":"DELETE_TASK","reply":"Confirm deleting Organic Chemistry?","targetTaskId":11}""",
            context = baseSnapshot(),
            tasks = tasks,
            events = emptyList(),
            todayEpochDay = today
        )
        assertNotNull(parsedDelete)
        assertTrue(parsedDelete!!.requiresConfirmation)

        // Verify NLU Part 2 intents
        assertEquals(
            SuruIntent.PLAN_WEEK,
            SuruNluEngine.parseAndPlan("Plan my entire week", baseSnapshot(), tasks, emptyList(), emptyList(), null, null, today)?.intent
        )
        assertEquals(
            SuruIntent.PROGRESS_SUMMARY,
            SuruNluEngine.parseAndPlan("Summarize my progress this month", baseSnapshot(), tasks, emptyList(), emptyList(), null, null, today)?.intent
        )
        assertEquals(
            SuruIntent.OVERDUE_CHECK,
            SuruNluEngine.parseAndPlan("What should I focus on today?", baseSnapshot(), tasks, emptyList(), emptyList(), null, null, today)?.intent
        )
        assertEquals(
            SuruIntent.FIND_FREE_SLOTS,
            SuruNluEngine.parseAndPlan("Find available study time today", baseSnapshot(), tasks, emptyList(), emptyList(), null, null, today)?.intent
        )
        val logged = SuruNluEngine.parseAndPlan(
            "I completed two hours of Chemistry",
            baseSnapshot(),
            tasks,
            emptyList(),
            emptyList(),
            null,
            null,
            today
        )
        assertEquals(SuruIntent.LOG_STUDY_PROGRESS, logged?.intent)
        assertEquals(120, logged?.loggedMinutes)

        // ActionEngine executes bulk move today->tomorrow and undo restores cleanly
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LifeOsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val insertedId = db.taskDao().insertTask(
                TaskEntity(title = "Today Physics Drill", deadlineEpochDay = today)
            )
            val engine = SuruActionEngine(db)
            val outcome = engine.executeStructuredAction(parsedMove, today)
            assertEquals(today + 1, db.taskDao().getAllTasksOnce().first { it.id == insertedId }.deadlineEpochDay)
            engine.undo(outcome.undoRecord!!)
            assertEquals(today, db.taskDao().getAllTasksOnce().first { it.id == insertedId }.deadlineEpochDay)
        } finally {
            db.close()
        }
    }

    @Test
    fun imageProcessor_scalesAndCompressesWithinUploadLimit() {
        assertEquals(1, SuruImageProcessor.computeInSampleSize(800, 600, 1280))
        assertTrue(SuruImageProcessor.computeInSampleSize(4000, 3000, 1280) >= 2)

        val largeBitmap = Bitmap.createBitmap(2400, 1600, Bitmap.Config.ARGB_8888)
        val result = SuruImageProcessor.encodeBitmapForUpload(largeBitmap, "Handwritten Notes")
        assertTrue(result is SuruImageLoadResult.Success)
        val attachment = (result as SuruImageLoadResult.Success).attachment
        assertTrue(attachment.width <= 1280)
        assertTrue(attachment.height <= 1280)
        assertTrue(attachment.jpegBytes.size <= 3 * 1024 * 1024)
        assertTrue(attachment.base64Jpeg.isNotBlank())
    }

    @Test
    fun soundManager_categoryAndMasterGatingWorksCorrectly() {
        val allOn = UserPreferencesState()
        assertTrue(LifeOsSoundManager.shouldPlayForPrefs(LifeOsSoundCue.NAV_SWITCH, allOn))
        assertTrue(LifeOsSoundManager.shouldPlayForPrefs(LifeOsSoundCue.TASK_COMPLETE, allOn))
        assertTrue(LifeOsSoundManager.shouldPlayForPrefs(LifeOsSoundCue.SURU_SUCCESS, allOn))

        val masterOff = allOn.copy(soundEffectsEnabled = false)
        assertFalse(LifeOsSoundManager.shouldPlayForPrefs(LifeOsSoundCue.LEVEL_UP, masterOff))

        val navOff = allOn.copy(soundNavEnabled = false)
        assertFalse(LifeOsSoundManager.shouldPlayForPrefs(LifeOsSoundCue.NAV_SWITCH, navOff))
        assertTrue(LifeOsSoundManager.shouldPlayForPrefs(LifeOsSoundCue.TASK_COMPLETE, navOff))

        val companionOff = allOn.copy(soundCompanionEnabled = false)
        assertFalse(LifeOsSoundManager.shouldPlayForPrefs(LifeOsSoundCue.COMPANION_INTERACT, companionOff))
    }

    @Test
    fun permissionCenterHelper_reportsHonestStatuses() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val all = PermissionCenterHelper.collectAllPermissions(context)
        assertEquals(7, all.size)
        assertEquals(
            AppPermissionStatus.NOT_REQUIRED,
            all.first { it.id == AppPermissionId.PHOTOS }.status
        )
        assertEquals(
            AppPermissionStatus.NOT_REQUIRED,
            all.first { it.id == AppPermissionId.ALARMS_REMINDERS }.status
        )
        assertEquals(
            AppPermissionStatus.GRANTED,
            all.first { it.id == AppPermissionId.INTERNET }.status
        )
    }

    @Test
    fun onboardingScreen_rendersCleanlyWithoutKutta() {
        composeRule.setContent {
            LifeOsTheme {
                OnboardingScreen(
                    existingPrefs = UserPreferencesState(),
                    onCompleteSignIn = { _, _, _, _ -> }
                )
            }
        }
        composeRule.onNodeWithText("Welcome to FEELORA").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_name_input").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("KUTTA", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
    }

    @Test
    fun permissionSetupScreen_rendersSkippableSetupWithoutKutta() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val perms = PermissionCenterHelper.collectAllPermissions(context)
        composeRule.setContent {
            LifeOsTheme {
                PermissionSetupScreen(
                    permissions = perms,
                    onRefreshPermissions = {},
                    onContinue = {}
                )
            }
        }
        composeRule.onNodeWithText("Optional Permissions").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_enter_app_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_skip_permissions_btn").performScrollTo().assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("KUTTA", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
    }

    @Test
    fun permissionsScreen_rendersLivePermissionCards() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val perms = PermissionCenterHelper.collectAllPermissions(context)
        composeRule.setContent {
            LifeOsTheme {
                PermissionsScreen(
                    permissions = perms,
                    onBack = {},
                    onRefresh = {}
                )
            }
        }
        composeRule.onNodeWithText("Permissions & Privacy").assertIsDisplayed()
        composeRule.onNodeWithTag("permission_card_NOTIFICATIONS").assertIsDisplayed()
    }
}
