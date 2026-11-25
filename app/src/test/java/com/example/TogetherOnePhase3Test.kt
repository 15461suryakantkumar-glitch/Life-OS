package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LifeOsDatabase
import com.example.data.local.UserAvatarPreset
import com.example.data.local.UserPreferencesState
import com.example.data.social.FriendSyncCodec
import com.example.data.social.SupabaseAuthAndSyncClient
import com.example.data.social.SupabaseResult
import com.example.data.social.TogetherFriendProfile
import com.example.data.social.TogetherPrivacyFilter
import com.example.data.social.TogetherRepository
import com.example.data.social.TogetherState
import com.example.data.social.TogetherStudyPresence
import com.example.ui.screens.TogetherOneScreen
import com.example.ui.theme.LifeOsTheme
import com.example.ui.viewmodel.TogetherOneUiState
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TogetherOnePhase3Test {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun roomMigration9To10_preservesFriendsAndAddsTogetherOneColumns() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_migration_9_10_${System.currentTimeMillis()}.db"
        val factory = FrameworkSQLiteOpenHelperFactory()
        val v9Callback = object : SupportSQLiteOpenHelper.Callback(9) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `friends` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `handle` TEXT NOT NULL,
                        `friendCode` TEXT NOT NULL DEFAULT '',
                        `level` INTEGER NOT NULL DEFAULT 1,
                        `xp` INTEGER NOT NULL DEFAULT 0,
                        `streakDays` INTEGER NOT NULL DEFAULT 0,
                        `focusMinutesToday` INTEGER NOT NULL DEFAULT 0,
                        `challengesCompleted` INTEGER NOT NULL DEFAULT 0,
                        `challengesTotal` INTEGER NOT NULL DEFAULT 4,
                        `petStageName` TEXT NOT NULL DEFAULT 'Playful Pup',
                        `sharesStats` INTEGER NOT NULL DEFAULT 1,
                        `shareXp` INTEGER NOT NULL DEFAULT 1,
                        `shareStreaks` INTEGER NOT NULL DEFAULT 1,
                        `shareFocus` INTEGER NOT NULL DEFAULT 1,
                        `shareChallenges` INTEGER NOT NULL DEFAULT 1,
                        `statusText` TEXT NOT NULL DEFAULT 'Connected',
                        `lastSyncedAtMillis` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }

        val helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(v9Callback)
                .build()
        )
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO `friends` (`displayName`, `handle`, `friendCode`, `level`, `xp`) VALUES ('Aria', '@aria', 'FLR-ARIA-4821', 4, 620)"
        )

        LifeOsDatabase.MIGRATION_9_10.migrate(db)

        db.query("SELECT displayName, remoteUserId, friendshipId, isTogetherOne FROM `friends`").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Aria", c.getString(0))
            assertEquals("", c.getString(1))
            assertEquals("", c.getString(2))
            assertEquals(0, c.getInt(3))
        }
        helper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun friendSyncCodec_extractsInviteLinksDeepLinksAndHandlesCleanly() {
        assertEquals(
            "FLR-ARIA-4821",
            FriendSyncCodec.extractInviteCode("lifeos://friend?code=flr-aria-4821")
        )
        assertEquals(
            "FLR-ARIA-4821",
            FriendSyncCodec.extractInviteCode("https://feelora.app/invite?code=FLR-ARIA-4821&ref=share")
        )
        assertEquals(
            "FLR-ARIA-4821",
            FriendSyncCodec.extractInviteCode("feelora://invite?code=FLR-ARIA-4821")
        )
        assertEquals(
            "FLR-ARIA-4821",
            FriendSyncCodec.extractInviteCode("  flr-aria-4821  ")
        )
        assertEquals(
            "@aria.study",
            FriendSyncCodec.extractInviteCode("@Aria.Study")
        )
        assertEquals(
            "@aria",
            FriendSyncCodec.extractInviteCode("aria")
        )
        assertNull(FriendSyncCodec.extractInviteCode(""))
        assertNull(FriendSyncCodec.extractInviteCode("   "))
        assertNull(FriendSyncCodec.extractInviteCode("bad!!input"))
    }

    @Test
    fun togetherPrivacyFilter_buildsPhase3PayloadAndExcludesPrivateLocalData() {
        val prefs = UserPreferencesState(
            userName = "Malkin",
            userHandle = "@malkin",
            friendCode = "FLR-MALK-1010",
            totalXp = 920,
            dailyStreak = 9,
            focusMinutesLoggedToday = 75,
            shareXpWithFriends = true,
            shareStreaksWithFriends = false,
            shareFocusWithFriends = false,
            shareChallengesWithFriends = true
        )
        val payload = TogetherPrivacyFilter.buildSafeProfilePayload(
            userId = "uid-1",
            prefs = prefs,
            tasksCompletedToday = 5,
            consistencyScore = 92,
            challengesCompleted = 3,
            challengesTotal = 4
        )
        assertEquals(920, payload.getInt("total_xp"))
        assertEquals(920, payload.getInt("xp"))
        assertEquals(0, payload.getInt("daily_streak"))
        assertEquals(0, payload.getInt("focus_minutes_today"))
        assertEquals(0L, payload.getLong("focus_date_epoch_day"))
        assertEquals(3, payload.getInt("challenges_completed"))
        assertEquals(4, payload.getInt("challenges_total"))
        assertTrue(payload.getString("pet_stage_name").isNotBlank())

        val serialized = payload.toString().lowercase()
        assertFalse(serialized.contains("cycle"))
        assertFalse(serialized.contains("daily_check_in"))
        assertFalse(serialized.contains("quick_note"))
        assertFalse(serialized.contains("suru_memor"))
    }

    @Test
    fun supabaseAuthAndTogetherRepository_onlineFlowsAndFailedMessageRetryWork() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LifeOsDatabase.getInstance(context).togetherOutboxDao().clearAll()

        var failMessagePost = true
        var lastPatchedBody = ""
        var lastPresenceBody = ""

        val mockHttp = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                val method = req.method

                val (code, bodyText) = when {
                    url.endsWith("/auth/v1/signup") -> {
                        200 to JSONObject()
                            .put("access_token", "anon-jwt-123")
                            .put("refresh_token", "refresh-123")
                            .put("expires_in", 3600)
                            .put(
                                "user",
                                JSONObject()
                                    .put("id", "user-self-1")
                                    .put("is_anonymous", true)
                            )
                            .toString()
                    }
                    url.contains("/rest/v1/rpc/lookup_profile_by_friend_code") -> {
                        200 to JSONArray().put(
                            JSONObject()
                                .put("id", "user-partner-2")
                                .put("display_name", "Aria")
                                .put("handle", "@aria")
                                .put("friend_code", "FLR-ARIA-4821")
                                .put("level", 4)
                                .put("total_xp", 650)
                                .put("daily_streak", 6)
                                .put("share_xp", true)
                                .put("share_streaks", true)
                                .put("share_focus", false)
                                .put("share_challenges", true)
                        ).toString()
                    }
                    url.contains("/rest/v1/friendships") && method == "POST" -> {
                        201 to JSONArray().put(JSONObject().put("id", "ship-1")).toString()
                    }
                    url.contains("/rest/v1/friendships") && method == "PATCH" -> {
                        val buf = okio.Buffer()
                        req.body?.writeTo(buf)
                        lastPatchedBody = buf.readUtf8()
                        200 to "[]"
                    }
                    url.contains("/rest/v1/friendships") && method == "GET" -> {
                        200 to JSONArray().put(
                            JSONObject()
                                .put("id", "ship-1")
                                .put("requester_id", "user-self-1")
                                .put("addressee_id", "user-partner-2")
                                .put("requester_name", "Malkin")
                                .put("requester_handle", "@malkin")
                                .put("status", "ACCEPTED")
                                .put("is_together_one", true)
                                .put("created_at", 1_700_000_000_000L)
                        ).toString()
                    }
                    url.contains("/rest/v1/profiles") && method == "GET" -> {
                        200 to JSONArray().put(
                            JSONObject()
                                .put("id", "user-partner-2")
                                .put("display_name", "Aria")
                                .put("handle", "@aria")
                                .put("friend_code", "FLR-ARIA-4821")
                                .put("level", 4)
                                .put("total_xp", 650)
                                .put("daily_streak", 6)
                                .put("focus_minutes_today", 90)
                                .put("share_xp", true)
                                .put("share_streaks", true)
                                .put("share_focus", false)
                                .put("share_challenges", true)
                        ).toString()
                    }
                    url.contains("/rest/v1/study_presence") && method == "POST" -> {
                        val buf = okio.Buffer()
                        req.body?.writeTo(buf)
                        lastPresenceBody = buf.readUtf8()
                        201 to "[]"
                    }
                    url.contains("/rest/v1/study_presence") && method == "GET" -> {
                        200 to JSONArray().toString()
                    }
                    url.contains("/rest/v1/together_messages") && method == "POST" -> {
                        if (failMessagePost) {
                            500 to """{"message":"Simulated network blip"}"""
                        } else {
                            201 to "[]"
                        }
                    }
                    url.contains("/rest/v1/together_messages") && method == "GET" -> {
                        200 to "[]"
                    }
                    url.contains("/rest/v1/shared_goals") ||
                        url.contains("/rest/v1/competitions") ||
                        url.contains("/rest/v1/together_activities") -> {
                        200 to "[]"
                    }
                    else -> 200 to "[]"
                }

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code < 400) "OK" else "Error")
                    .body(bodyText.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val client = SupabaseAuthAndSyncClient(
            appContext = context,
            baseUrlOverride = "https://mock-project.supabase.co",
            anonKeyOverride = "mock-anon-key-123456",
            httpClientOverride = mockHttp
        )

        val sessionRes = client.ensureSession(UserPreferencesState())
        assertTrue(sessionRes is SupabaseResult.Success)
        val session = (sessionRes as SupabaseResult.Success).value
        assertEquals("user-self-1", session.userId)
        assertTrue(session.isAnonymous)

        val repo = TogetherRepository(context, client)
        val reqRes = repo.sendFriendRequest(
            query = "https://feelora.app/invite?code=FLR-ARIA-4821",
            myUserId = session.userId,
            myName = "Malkin",
            myHandle = "@malkin",
            accessToken = session.accessToken
        )
        assertTrue(reqRes is SupabaseResult.Success)
        val partner = repo.state.value.activePartner
        assertEquals("Aria", partner?.displayName)
        assertEquals(0, partner?.focusMinutesToday) // share_focus = false masked cleanly

        // Toggle Together One partner
        val pinRes = repo.setTogetherOnePartner(session.userId, "user-partner-2", true, session.accessToken)
        assertTrue(pinRes is SupabaseResult.Success)
        assertTrue(lastPatchedBody.contains("\"is_together_one\":true"))

        // Live study presence
        val presRes = repo.upsertStudyPresence(
            userId = session.userId,
            displayName = "Malkin",
            isStudying = true,
            sessionTitle = "Organic Chemistry",
            targetMinutes = 45,
            startedAtMillis = 1000L,
            endsAtMillis = 2701000L,
            kuttaEmotion = "FOCUSED",
            cheerNote = "Let's lock in!",
            accessToken = session.accessToken
        )
        assertTrue(presRes is SupabaseResult.Success)
        assertTrue(lastPresenceBody.contains("Organic Chemistry"))

        // Message fails -> marks FAILED outbox -> retry succeeds
        val sendFail = repo.sendMessage(session.userId, "user-partner-2", "Ready to co-study?", null, session.accessToken)
        assertTrue(sendFail is SupabaseResult.Failure)
        assertEquals(1, repo.state.value.failedOutboxCount)
        assertTrue(repo.state.value.lastMessageError != null)

        failMessagePost = false
        val retryRes = repo.retryFailedMessages(session.userId, session.accessToken)
        assertTrue(retryRes is SupabaseResult.Success)
        assertEquals(1, (retryRes as SupabaseResult.Success).value)
        assertEquals(0, repo.state.value.failedOutboxCount)
        assertNull(repo.state.value.lastMessageError)
    }

    @Test
    fun togetherOneScreen_displaysInviteComparisonCoStudyAndRetryBanner() {
        val partner = TogetherFriendProfile(
            userId = "user-partner-2",
            isTogetherOne = true,
            displayName = "Aria Vance",
            handle = "@aria",
            avatarPreset = UserAvatarPreset.BERRY_CAT,
            shareXp = true,
            shareStreaks = true,
            shareFocus = false,
            shareChallenges = true,
            level = 4,
            xp = 650,
            dailyStreak = 6,
            focusMinutesToday = 95,
            challengesCompleted = 3,
            challengesTotal = 4
        )
        val uiState = TogetherOneUiState(
            prefs = UserPreferencesState(
                userName = "Malkin",
                userHandle = "@malkin",
                friendCode = "FLR-MALK-1010",
                totalXp = 920,
                dailyStreak = 9,
                focusMinutesLoggedToday = 50
            ),
            togetherState = TogetherState(
                isCloudConfigured = true,
                isRealtimeConnected = true,
                lastMessageError = "Offline — queued 1 message",
                failedOutboxCount = 1,
                friends = listOf(partner),
                togetherStreakDays = 6
            ),
            isOnline = true,
            myFriendCode = "FLR-MALK-1010",
            myInviteLink = "https://feelora.app/invite?code=FLR-MALK-1010",
            myDeepLink = "lifeos://friend?code=FLR-MALK-1010",
            myChallengesCompleted = 2,
            myChallengesTotal = 4,
            activePartner = partner,
            partnerPresence = TogetherStudyPresence(
                userId = "user-partner-2",
                displayName = "Aria Vance",
                isStudying = true,
                sessionTitle = "Calculus Sprint",
                targetMinutes = 45,
                startedAtMillis = System.currentTimeMillis() - 60_000L,
                endsAtMillis = System.currentTimeMillis() + 44 * 60_000L,
                kuttaEmotion = "FOCUSED"
            )
        )

        composeRule.setContent {
            LifeOsTheme {
                TogetherOneScreen(
                    state = uiState,
                    onBack = {},
                    onRefresh = {},
                    onSendInviteOrRequest = { _, _ -> },
                    onRespondToRequest = { _, _ -> },
                    onCancelOutgoingRequest = {},
                    onToggleTogetherOnePartner = { _, _ -> },
                    onRemoveOrBlockFriend = { _, _ -> },
                    onOpenChat = {},
                    onStartCoStudy = { _, _, _, _ -> },
                    onStopCoStudy = {},
                    onSendCheer = { _, _ -> },
                    onCreateSharedGoal = { _, _, _, _, _, _ -> },
                    onIncrementGoal = { _, _ -> },
                    onCreateCompetition = { _, _, _, _ -> },
                    onReactToActivity = { _, _ -> },
                    onUpdatePrivacy = { _, _, _, _ -> },
                    onRetryFailedMessages = {},
                    onClearFeedback = {},
                    onShowMessage = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("together_one_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("together_retry_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("together_one_screen").performScrollToNode(hasTestTag("together_invite_card"))
        composeRule.onNodeWithTag("together_invite_card").assertIsDisplayed()
        composeRule.onNodeWithText("@malkin · FLR-MALK-1010").assertIsDisplayed()
        composeRule.onNodeWithTag("together_one_screen").performScrollToNode(hasTestTag("together_partner_spotlight"))
        composeRule.onNodeWithTag("together_partner_spotlight").assertIsDisplayed()
        composeRule.onNodeWithText("Aria Vance").assertIsDisplayed()
        composeRule.onNodeWithText("Aria: Private").assertIsDisplayed()
        composeRule.onNodeWithTag("together_one_screen").performScrollToNode(hasTestTag("together_costudy_card"))
        composeRule.onNodeWithTag("together_costudy_card").assertIsDisplayed()
        composeRule.onNodeWithText("Aria Vance is studying now").assertIsDisplayed()
        composeRule.onNodeWithText("Calculus Sprint", substring = true).assertIsDisplayed()
    }
}
