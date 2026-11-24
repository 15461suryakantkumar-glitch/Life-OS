package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.kutta.KuttaEmotion
import com.example.kutta.KuttaEventHandler
import com.example.ui.theme.LifeOsThemeMode
import com.example.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import kotlin.math.absoluteValue

private val Context.lifeOsDataStore by preferencesDataStore(name = "life_os_prefs")

const val KUTTA_NAME = "KUTTA"

enum class PetAccessory(val label: String, val requiredLevel: Int, val bonusLabel: String) {
    NONE("No Accessory", 1, "Signature Burgundy Hoodie"),
    HOLO_VISOR("Study Glasses", 2, "+Focus Vibe"),
    CYBER_SCARF("Cozy Muffler", 3, "+Warmth"),
    ASTRAL_CROWN("Star Crown", 5, "+Champion Glow"),
    ORBITAL_HALO("Lucky Clover", 7, "+Mastery Charm")
}

enum class PetGrowthStage(val title: String, val minLevel: Int, val description: String) {
    SPARK("Playful Pup", 1, "KUTTA just joined your desk and is cheering you on, Malkin!"),
    PRISM("Study Buddy", 3, "KUTTA matches your study sprints and daily hydration rhythm."),
    SENTINEL("Loyal Guardian", 5, "Steady, caring, and proud of your growing daily streaks."),
    ASTRAL("Star Teammate", 7, "Always right by your side through every challenge and milestone."),
    SOVEREIGN("Forever Companion", 10, "Your highest bond with KUTTA earned through lasting consistency.")
}

enum class CompanionMood(val label: String, val nudgeHint: String) {
    JOYFUL("Happy", "Areyyy Malkin! Aaj toh mood ekdum mast hai!"),
    FOCUSED("Focused", "Shhh Malkin, ab full focus mode on!"),
    CHILL("Happy", "Hey Malkin! KUTTA yahin hai aapke saath."),
    SLEEPY("Sleepy", "Bas Malkin, thoda aaram kar lo..."),
    WORRIED("Worried", "Koi baat nahi Malkin, KUTTA hai na saath.")
}

object CompanionMoodResolver {
    fun resolve(
        isTimerRunning: Boolean,
        petHappiness: Int,
        petEnergy: Int,
        hasRecentPositiveAction: Boolean,
        pendingTasks: List<TaskEntity>,
        distractingMinutesToday: Int,
        dailyScreenLimitMinutes: Int,
        nowMillis: Long = System.currentTimeMillis(),
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): CompanionMood {
        val emotion = KuttaEventHandler.resolveBaselineEmotion(
            isTimerRunning = isTimerRunning,
            pendingTasks = pendingTasks,
            distractingMinutesToday = distractingMinutesToday,
            dailyScreenLimitMinutes = dailyScreenLimitMinutes,
            recentPositiveScore = hasRecentPositiveAction || petHappiness >= 90,
            lastCheckInMoodScore = null,
            nowMillis = nowMillis,
            hourOfDay = hourOfDay
        )
        return when (emotion) {
            KuttaEmotion.FOCUSED -> CompanionMood.FOCUSED
            KuttaEmotion.PROUD -> CompanionMood.JOYFUL
            KuttaEmotion.WORRIED -> CompanionMood.WORRIED
            KuttaEmotion.SLEEPY -> CompanionMood.SLEEPY
            KuttaEmotion.HAPPY -> CompanionMood.JOYFUL
        }
    }
}

enum class UserAvatarPreset(val label: String) {
    SUNNY_CLOUD("Sunny Cloud"),
    FOREST_DEER("Forest Deer"),
    OCEAN_WHALE("Ocean Whale"),
    BERRY_CAT("Berry Cat"),
    STAR_BUNNY("BUNNI · Star Bunny"),
    AMBER_FOX("Amber Fox");

    companion object {
        fun fromStored(raw: String?): UserAvatarPreset {
            return entries.firstOrNull { it.name == raw } ?: STAR_BUNNY
        }
    }
}

data class UserPreferencesState(
    val isSignedIn: Boolean = false,
    val userName: String = "",
    val userHandle: String = "",
    val userEmail: String = "",
    val friendCode: String = "",
    val avatarPreset: UserAvatarPreset = UserAvatarPreset.STAR_BUNNY,
    val dailyFocusGoalMinutes: Int = 120,
    val dailyScreenLimitMinutes: Int = 180,
    val focusMinutesLoggedToday: Int = 0,
    val waterIntakeMlToday: Int = 0,
    val dailyWaterGoalMl: Int = 2000,
    val waterRemindersEnabled: Boolean = true,
    val cycleCareEnabled: Boolean = true,
    val averageCycleLengthDays: Int = 28,
    val averagePeriodLengthDays: Int = 5,
    val reducedMotionEnabled: Boolean = false,
    val themeMode: LifeOsThemeMode = LifeOsThemeMode.FEELORA_DARK,
    val totalXp: Int = 0,
    val dailyStreak: Int = 0,
    val weeklyStreak: Int = 0,
    val lastActiveEpochDay: Long = -1L,
    val loginEpochDays: Set<Long> = emptySet(),
    val petName: String = KUTTA_NAME,
    val petHappiness: Int = 75,
    val petEnergy: Int = 80,
    val equippedAccessory: PetAccessory = PetAccessory.NONE,
    val unlockedBadges: Set<String> = emptySet(),
    val shareXpWithFriends: Boolean = true,
    val shareStreaksWithFriends: Boolean = true,
    val shareFocusWithFriends: Boolean = false,
    val shareChallengesWithFriends: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val taskRemindersEnabled: Boolean = true,
    val wellnessAlertsEnabled: Boolean = true,
    val quietHoursEnabled: Boolean = true,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 7,
    val reminderFrequencyHours: Int = 2,
    val maxRemindersPerTaskPerDay: Int = 3,
    val soundEffectsEnabled: Boolean = true,
    val soundVolume: Float = 0.72f,
    val hapticsEnabled: Boolean = true,
    val soundNavEnabled: Boolean = true,
    val soundInteractionEnabled: Boolean = true,
    val soundTaskEnabled: Boolean = true,
    val soundCalendarEnabled: Boolean = true,
    val soundSuruEnabled: Boolean = true,
    val soundGamificationEnabled: Boolean = true,
    val soundCompanionEnabled: Boolean = true,
    val hasCompletedPermissionSetup: Boolean = false,
    val suruPersonalizationEnabled: Boolean = true,
    val suruVoiceMuted: Boolean = false,
    val suruSpeechSpeed: Float = 1.0f,
    val suruVoiceLanguage: String = "HINGLISH",
    val suruMorningBriefingEnabled: Boolean = true,
    val suruEveningReflectionEnabled: Boolean = true,
    val suruMorningHour: Int = 8,
    val suruEveningHour: Int = 20
) {
    val level: Int
        get() = (totalXp / 250) + 1

    val xpIntoCurrentLevel: Int
        get() = totalXp % 250

    val xpPerLevel: Int
        get() = 250

    val petStage: PetGrowthStage
        get() = PetGrowthStage.entries.lastOrNull { level >= it.minLevel } ?: PetGrowthStage.SPARK

    val unlockedAccessories: List<PetAccessory>
        get() = PetAccessory.entries.filter { level >= it.requiredLevel }
}

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val IS_SIGNED_IN = booleanPreferencesKey("is_signed_in")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_HANDLE = stringPreferencesKey("user_handle")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val FRIEND_CODE = stringPreferencesKey("friend_code")
        val AVATAR_PRESET = stringPreferencesKey("avatar_preset")
        val FOCUS_GOAL = intPreferencesKey("focus_goal_mins")
        val SCREEN_LIMIT = intPreferencesKey("screen_limit_mins")
        val FOCUS_TODAY = intPreferencesKey("focus_logged_today")
        val FOCUS_TODAY_DAY = longPreferencesKey("focus_today_epoch_day")
        val WATER_TODAY_ML = intPreferencesKey("water_today_ml")
        val WATER_TODAY_DAY = longPreferencesKey("water_today_epoch_day")
        val WATER_GOAL_ML = intPreferencesKey("water_goal_ml")
        val WATER_REMINDERS = booleanPreferencesKey("water_reminders_enabled")
        val CYCLE_CARE_ENABLED = booleanPreferencesKey("cycle_care_enabled")
        val CYCLE_LENGTH_DAYS = intPreferencesKey("average_cycle_length_days")
        val PERIOD_LENGTH_DAYS = intPreferencesKey("average_period_length_days")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion_enabled")
        val THEME_MODE = stringPreferencesKey("accent_preset")
        val TOTAL_XP = intPreferencesKey("total_xp")
        val DAILY_STREAK = intPreferencesKey("daily_streak")
        val WEEKLY_STREAK = intPreferencesKey("weekly_streak")
        val LAST_ACTIVE_DAY = longPreferencesKey("last_active_day")
        val LOGIN_DAYS = stringPreferencesKey("login_epoch_days")
        val PET_HAPPINESS = intPreferencesKey("pet_happiness")
        val PET_ENERGY = intPreferencesKey("pet_energy")
        val PET_ACCESSORY = stringPreferencesKey("pet_accessory")
        val UNLOCKED_BADGES = stringPreferencesKey("unlocked_badges")
        val SHARE_XP = booleanPreferencesKey("share_xp")
        val SHARE_STREAKS = booleanPreferencesKey("share_streaks")
        val SHARE_FOCUS = booleanPreferencesKey("share_focus")
        val SHARE_CHALLENGES = booleanPreferencesKey("share_challenges")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val TASK_REMINDERS = booleanPreferencesKey("task_reminders")
        val WELLNESS_ALERTS = booleanPreferencesKey("wellness_alerts")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_START_HOUR = intPreferencesKey("quiet_start_hour")
        val QUIET_END_HOUR = intPreferencesKey("quiet_end_hour")
        val REMINDER_FREQUENCY_HOURS = intPreferencesKey("reminder_frequency_hours")
        val MAX_REMINDERS_PER_TASK = intPreferencesKey("max_reminders_per_task_day")
        val SOUND_EFFECTS = booleanPreferencesKey("sound_effects_enabled")
        val SOUND_VOLUME_X100 = intPreferencesKey("sound_volume_x100")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val SOUND_NAV = booleanPreferencesKey("sound_nav_enabled")
        val SOUND_INTERACTION = booleanPreferencesKey("sound_interaction_enabled")
        val SOUND_TASK = booleanPreferencesKey("sound_task_enabled")
        val SOUND_CALENDAR = booleanPreferencesKey("sound_calendar_enabled")
        val SOUND_SURU = booleanPreferencesKey("sound_suru_enabled")
        val SOUND_GAMIFICATION = booleanPreferencesKey("sound_gamification_enabled")
        val SOUND_COMPANION = booleanPreferencesKey("sound_companion_enabled")
        val HAS_COMPLETED_PERMISSION_SETUP = booleanPreferencesKey("has_completed_permission_setup")
        val SURU_PERSONALIZATION = booleanPreferencesKey("suru_personalization_enabled")
        val SURU_VOICE_MUTED = booleanPreferencesKey("suru_voice_muted")
        val SURU_SPEECH_SPEED_X100 = intPreferencesKey("suru_speech_speed_x100")
        val SURU_VOICE_LANG = stringPreferencesKey("suru_voice_lang")
        val SURU_MORNING_BRIEFING = booleanPreferencesKey("suru_morning_briefing")
        val SURU_EVENING_REFLECTION = booleanPreferencesKey("suru_evening_reflection")
        val SURU_MORNING_HOUR = intPreferencesKey("suru_morning_hour")
        val SURU_EVENING_HOUR = intPreferencesKey("suru_evening_hour")
    }

    val preferencesFlow: Flow<UserPreferencesState> = context.lifeOsDataStore.data.map { prefs ->
        val theme = LifeOsThemeMode.fromStored(prefs[Keys.THEME_MODE])
        val accessory = PetAccessory.entries.firstOrNull {
            it.name == prefs[Keys.PET_ACCESSORY]
        } ?: PetAccessory.NONE
        val avatar = UserAvatarPreset.fromStored(prefs[Keys.AVATAR_PRESET])

        val badgesRaw = prefs[Keys.UNLOCKED_BADGES] ?: ""
        val badges = badgesRaw.split(",").filter { it.isNotBlank() }.toSet()

        val loginDaysRaw = prefs[Keys.LOGIN_DAYS] ?: ""
        val loginDays = loginDaysRaw.split(",").mapNotNull { it.toLongOrNull() }.toSet()

        val savedHandle = prefs[Keys.USER_HANDLE] ?: ""
        val savedName = prefs[Keys.USER_NAME] ?: ""
        val savedCode = prefs[Keys.FRIEND_CODE]?.takeIf { it.isNotBlank() }
            ?: if (savedName.isNotBlank()) generateFriendCode(savedHandle, savedName) else ""

        val today = DateTimeUtils.todayEpochDay()
        val storedFocusDay = prefs[Keys.FOCUS_TODAY_DAY] ?: today
        val focusToday = if (storedFocusDay == today) (prefs[Keys.FOCUS_TODAY] ?: 0) else 0
        val storedWaterDay = prefs[Keys.WATER_TODAY_DAY] ?: today
        val waterToday = if (storedWaterDay == today) (prefs[Keys.WATER_TODAY_ML] ?: 0) else 0

        UserPreferencesState(
            isSignedIn = prefs[Keys.IS_SIGNED_IN] ?: false,
            userName = savedName,
            userHandle = savedHandle,
            userEmail = prefs[Keys.USER_EMAIL] ?: "",
            friendCode = savedCode,
            avatarPreset = avatar,
            dailyFocusGoalMinutes = prefs[Keys.FOCUS_GOAL] ?: 120,
            dailyScreenLimitMinutes = prefs[Keys.SCREEN_LIMIT] ?: 180,
            focusMinutesLoggedToday = focusToday,
            waterIntakeMlToday = waterToday,
            dailyWaterGoalMl = prefs[Keys.WATER_GOAL_ML] ?: 2000,
            waterRemindersEnabled = prefs[Keys.WATER_REMINDERS] ?: true,
            cycleCareEnabled = prefs[Keys.CYCLE_CARE_ENABLED] ?: true,
            averageCycleLengthDays = prefs[Keys.CYCLE_LENGTH_DAYS] ?: 28,
            averagePeriodLengthDays = prefs[Keys.PERIOD_LENGTH_DAYS] ?: 5,
            reducedMotionEnabled = prefs[Keys.REDUCED_MOTION] ?: false,
            themeMode = theme,
            totalXp = prefs[Keys.TOTAL_XP] ?: 0,
            dailyStreak = prefs[Keys.DAILY_STREAK] ?: 0,
            weeklyStreak = prefs[Keys.WEEKLY_STREAK] ?: 0,
            lastActiveEpochDay = prefs[Keys.LAST_ACTIVE_DAY] ?: -1L,
            loginEpochDays = loginDays,
            petName = KUTTA_NAME,
            petHappiness = prefs[Keys.PET_HAPPINESS] ?: 75,
            petEnergy = prefs[Keys.PET_ENERGY] ?: 80,
            equippedAccessory = accessory,
            unlockedBadges = badges,
            shareXpWithFriends = prefs[Keys.SHARE_XP] ?: true,
            shareStreaksWithFriends = prefs[Keys.SHARE_STREAKS] ?: true,
            shareFocusWithFriends = prefs[Keys.SHARE_FOCUS] ?: false,
            shareChallengesWithFriends = prefs[Keys.SHARE_CHALLENGES] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            taskRemindersEnabled = prefs[Keys.TASK_REMINDERS] ?: true,
            wellnessAlertsEnabled = prefs[Keys.WELLNESS_ALERTS] ?: true,
            quietHoursEnabled = prefs[Keys.QUIET_HOURS_ENABLED] ?: true,
            quietStartHour = prefs[Keys.QUIET_START_HOUR] ?: 22,
            quietEndHour = prefs[Keys.QUIET_END_HOUR] ?: 7,
            reminderFrequencyHours = (prefs[Keys.REMINDER_FREQUENCY_HOURS] ?: 2).coerceIn(1, 6),
            maxRemindersPerTaskPerDay = (prefs[Keys.MAX_REMINDERS_PER_TASK] ?: 3).coerceIn(1, 10),
            soundEffectsEnabled = prefs[Keys.SOUND_EFFECTS] ?: true,
            soundVolume = ((prefs[Keys.SOUND_VOLUME_X100] ?: 72) / 100f).coerceIn(0.1f, 1.0f),
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            soundNavEnabled = prefs[Keys.SOUND_NAV] ?: true,
            soundInteractionEnabled = prefs[Keys.SOUND_INTERACTION] ?: true,
            soundTaskEnabled = prefs[Keys.SOUND_TASK] ?: true,
            soundCalendarEnabled = prefs[Keys.SOUND_CALENDAR] ?: true,
            soundSuruEnabled = prefs[Keys.SOUND_SURU] ?: true,
            soundGamificationEnabled = prefs[Keys.SOUND_GAMIFICATION] ?: true,
            soundCompanionEnabled = prefs[Keys.SOUND_COMPANION] ?: true,
            hasCompletedPermissionSetup = prefs[Keys.HAS_COMPLETED_PERMISSION_SETUP] ?: false,
            suruPersonalizationEnabled = prefs[Keys.SURU_PERSONALIZATION] ?: true,
            suruVoiceMuted = prefs[Keys.SURU_VOICE_MUTED] ?: false,
            suruSpeechSpeed = ((prefs[Keys.SURU_SPEECH_SPEED_X100] ?: 100) / 100f).coerceIn(0.75f, 1.5f),
            suruVoiceLanguage = prefs[Keys.SURU_VOICE_LANG] ?: "HINGLISH",
            suruMorningBriefingEnabled = prefs[Keys.SURU_MORNING_BRIEFING] ?: true,
            suruEveningReflectionEnabled = prefs[Keys.SURU_EVENING_REFLECTION] ?: true,
            suruMorningHour = prefs[Keys.SURU_MORNING_HOUR] ?: 8,
            suruEveningHour = prefs[Keys.SURU_EVENING_HOUR] ?: 20
        )
    }

    suspend fun recordLoginDay(epochDay: Long = DateTimeUtils.todayEpochDay()) {
        context.lifeOsDataStore.edit { prefs ->
            val existing = (prefs[Keys.LOGIN_DAYS] ?: "")
                .split(",")
                .mapNotNull { it.toLongOrNull() }
                .filter { it >= epochDay - 60 }
                .toMutableSet()
            if (existing.add(epochDay)) {
                prefs[Keys.LOGIN_DAYS] = existing.sorted().joinToString(",")
            }
        }
    }

    suspend fun awardXpAndBoostPet(xpDelta: Int, happinessDelta: Int, energyDelta: Int) {
        val today = DateTimeUtils.todayEpochDay()
        context.lifeOsDataStore.edit { prefs ->
            val currentXp = prefs[Keys.TOTAL_XP] ?: 0
            prefs[Keys.TOTAL_XP] = (currentXp + xpDelta).coerceAtLeast(0)

            val currentHap = prefs[Keys.PET_HAPPINESS] ?: 75
            val currentEnergy = prefs[Keys.PET_ENERGY] ?: 80
            prefs[Keys.PET_HAPPINESS] = (currentHap + happinessDelta).coerceIn(10, 100)
            prefs[Keys.PET_ENERGY] = (currentEnergy + energyDelta).coerceIn(10, 100)

            val lastDay = prefs[Keys.LAST_ACTIVE_DAY] ?: -1L
            val currentDaily = prefs[Keys.DAILY_STREAK] ?: 0
            val nextDaily = when {
                lastDay == today -> currentDaily.coerceAtLeast(1)
                lastDay == today - 1L -> currentDaily + 1
                else -> 1
            }
            prefs[Keys.DAILY_STREAK] = nextDaily
            prefs[Keys.WEEKLY_STREAK] = ((nextDaily - 1) / 7) + 1
            prefs[Keys.LAST_ACTIVE_DAY] = today
        }
    }

    suspend fun adjustPetMoodAndEnergy(happinessDelta: Int, energyDelta: Int) {
        context.lifeOsDataStore.edit { prefs ->
            val currentHap = prefs[Keys.PET_HAPPINESS] ?: 75
            val currentEnergy = prefs[Keys.PET_ENERGY] ?: 80
            prefs[Keys.PET_HAPPINESS] = (currentHap + happinessDelta).coerceIn(10, 100)
            prefs[Keys.PET_ENERGY] = (currentEnergy + energyDelta).coerceIn(10, 100)
        }
    }

    suspend fun addFocusMinutes(minutes: Int) {
        val today = DateTimeUtils.todayEpochDay()
        context.lifeOsDataStore.edit { prefs ->
            val storedDay = prefs[Keys.FOCUS_TODAY_DAY] ?: today
            val current = if (storedDay == today) (prefs[Keys.FOCUS_TODAY] ?: 0) else 0
            prefs[Keys.FOCUS_TODAY_DAY] = today
            prefs[Keys.FOCUS_TODAY] = (current + minutes).coerceAtLeast(0)
        }
    }

    suspend fun logWaterMl(deltaMl: Int): Int {
        val today = DateTimeUtils.todayEpochDay()
        var updatedTotal = 0
        context.lifeOsDataStore.edit { prefs ->
            val storedDay = prefs[Keys.WATER_TODAY_DAY] ?: today
            val current = if (storedDay == today) (prefs[Keys.WATER_TODAY_ML] ?: 0) else 0
            updatedTotal = (current + deltaMl).coerceIn(0, 6000)
            prefs[Keys.WATER_TODAY_DAY] = today
            prefs[Keys.WATER_TODAY_ML] = updatedTotal
        }
        return updatedTotal
    }

    suspend fun updateWaterSettings(goalMl: Int, remindersEnabled: Boolean) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.WATER_GOAL_ML] = goalMl.coerceIn(750, 5000)
            prefs[Keys.WATER_REMINDERS] = remindersEnabled
        }
    }

    suspend fun updateCycleSettings(enabled: Boolean, cycleLengthDays: Int, periodLengthDays: Int) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.CYCLE_CARE_ENABLED] = enabled
            prefs[Keys.CYCLE_LENGTH_DAYS] = cycleLengthDays.coerceIn(21, 45)
            prefs[Keys.PERIOD_LENGTH_DAYS] = periodLengthDays.coerceIn(2, 10)
        }
    }

    suspend fun updateReducedMotion(enabled: Boolean) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.REDUCED_MOTION] = enabled
        }
    }

    suspend fun unlockBadge(badgeId: String) {
        context.lifeOsDataStore.edit { prefs ->
            val existing = (prefs[Keys.UNLOCKED_BADGES] ?: "")
                .split(",").filter { it.isNotBlank() }.toMutableSet()
            existing.add(badgeId)
            prefs[Keys.UNLOCKED_BADGES] = existing.joinToString(",")
        }
    }

    suspend fun completeOnboardingSignIn(
        name: String,
        identityOrEmail: String,
        focusGoal: Int,
        themeMode: LifeOsThemeMode
    ) {
        val today = DateTimeUtils.todayEpochDay()
        val cleanName = name.trim().ifEmpty { "Malkin" }
        val cleanIdentity = identityOrEmail.trim()
        val derivedEmail = if (cleanIdentity.contains("@") && cleanIdentity.contains(".")) cleanIdentity else ""
        val derivedHandle = when {
            derivedEmail.isNotEmpty() -> {
                val prefix = derivedEmail.substringBefore("@")
                    .lowercase()
                    .replace(Regex("[^a-z0-9._]"), "")
                    .ifEmpty { "user" }
                "@$prefix"
            }
            cleanIdentity.isNotEmpty() -> {
                val stripped = cleanIdentity.removePrefix("@")
                    .lowercase()
                    .replace(Regex("[^a-z0-9._]"), "")
                    .ifEmpty { "user" }
                "@$stripped"
            }
            else -> {
                val slug = cleanName.lowercase()
                    .replace(Regex("[^a-z0-9]"), "")
                    .take(14)
                    .ifEmpty { "malkin" }
                "@$slug"
            }
        }

        context.lifeOsDataStore.edit { prefs ->
            val existingCode = prefs[Keys.FRIEND_CODE]?.takeIf { it.isNotBlank() }
                ?: generateFriendCode(derivedHandle, cleanName)
            val loginDays = (prefs[Keys.LOGIN_DAYS] ?: "")
                .split(",").mapNotNull { it.toLongOrNull() }.toMutableSet()
            loginDays.add(today)

            prefs[Keys.IS_SIGNED_IN] = true
            prefs[Keys.USER_NAME] = cleanName
            prefs[Keys.USER_HANDLE] = derivedHandle
            prefs[Keys.USER_EMAIL] = derivedEmail
            prefs[Keys.FRIEND_CODE] = existingCode
            prefs[Keys.FOCUS_GOAL] = focusGoal.coerceIn(15, 600)
            prefs[Keys.THEME_MODE] = themeMode.name
            prefs[Keys.LOGIN_DAYS] = loginDays.sorted().joinToString(",")
        }
    }

    suspend fun signOutSession() {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.IS_SIGNED_IN] = false
        }
    }

    suspend fun updateProfile(
        name: String,
        handle: String,
        focusGoal: Int,
        screenLimit: Int,
        avatarPreset: UserAvatarPreset? = null
    ) {
        context.lifeOsDataStore.edit { prefs ->
            val cleanName = name.trim().ifEmpty { "Malkin" }
            val cleanHandle = handle.trim().ifEmpty { "@malkin" }.let {
                if (it.startsWith("@")) it else "@$it"
            }
            prefs[Keys.USER_NAME] = cleanName
            prefs[Keys.USER_HANDLE] = cleanHandle
            if (prefs[Keys.FRIEND_CODE].isNullOrBlank()) {
                prefs[Keys.FRIEND_CODE] = generateFriendCode(cleanHandle, cleanName)
            }
            if (avatarPreset != null) {
                prefs[Keys.AVATAR_PRESET] = avatarPreset.name
            }
            prefs[Keys.FOCUS_GOAL] = focusGoal.coerceIn(15, 600)
            prefs[Keys.SCREEN_LIMIT] = screenLimit.coerceIn(30, 720)
        }
    }

    suspend fun updateAvatarPreset(avatarPreset: UserAvatarPreset) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.AVATAR_PRESET] = avatarPreset.name
        }
    }

    suspend fun equipAccessory(accessory: PetAccessory) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.PET_ACCESSORY] = accessory.name
        }
    }

    suspend fun updateThemeMode(mode: LifeOsThemeMode) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun updatePrivacy(
        shareXp: Boolean,
        shareStreaks: Boolean,
        shareFocus: Boolean,
        shareChallenges: Boolean
    ) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.SHARE_XP] = shareXp
            prefs[Keys.SHARE_STREAKS] = shareStreaks
            prefs[Keys.SHARE_FOCUS] = shareFocus
            prefs[Keys.SHARE_CHALLENGES] = shareChallenges
        }
    }

    suspend fun updateNotifications(
        enabled: Boolean,
        reminders: Boolean,
        wellnessAlerts: Boolean,
        quietHoursEnabled: Boolean = true,
        quietStartHour: Int = 22,
        quietEndHour: Int = 7
    ) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS] = enabled
            prefs[Keys.TASK_REMINDERS] = reminders
            prefs[Keys.WELLNESS_ALERTS] = wellnessAlerts
            prefs[Keys.QUIET_HOURS_ENABLED] = quietHoursEnabled
            prefs[Keys.QUIET_START_HOUR] = quietStartHour.coerceIn(0, 23)
            prefs[Keys.QUIET_END_HOUR] = quietEndHour.coerceIn(0, 23)
        }
    }

    suspend fun updateSoundEffects(enabled: Boolean) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.SOUND_EFFECTS] = enabled
        }
    }

    suspend fun updateSoundConfiguration(
        masterEnabled: Boolean,
        volume: Float,
        hapticsEnabled: Boolean,
        navEnabled: Boolean,
        interactionEnabled: Boolean,
        taskEnabled: Boolean,
        calendarEnabled: Boolean,
        suruEnabled: Boolean,
        gamificationEnabled: Boolean,
        companionEnabled: Boolean
    ) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.SOUND_EFFECTS] = masterEnabled
            prefs[Keys.SOUND_VOLUME_X100] = (volume.coerceIn(0.1f, 1.0f) * 100).toInt()
            prefs[Keys.HAPTICS_ENABLED] = hapticsEnabled
            prefs[Keys.SOUND_NAV] = navEnabled
            prefs[Keys.SOUND_INTERACTION] = interactionEnabled
            prefs[Keys.SOUND_TASK] = taskEnabled
            prefs[Keys.SOUND_CALENDAR] = calendarEnabled
            prefs[Keys.SOUND_SURU] = suruEnabled
            prefs[Keys.SOUND_GAMIFICATION] = gamificationEnabled
            prefs[Keys.SOUND_COMPANION] = companionEnabled
        }
    }

    suspend fun updateSmartReminderPolicy(frequencyHours: Int, maxPerTaskPerDay: Int) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.REMINDER_FREQUENCY_HOURS] = frequencyHours.coerceIn(1, 6)
            prefs[Keys.MAX_REMINDERS_PER_TASK] = maxPerTaskPerDay.coerceIn(1, 10)
        }
    }

    suspend fun markPermissionSetupCompleted(completed: Boolean = true) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.HAS_COMPLETED_PERMISSION_SETUP] = completed
        }
    }

    suspend fun updateSuruVoiceSettings(
        muted: Boolean,
        speechSpeed: Float,
        languageName: String
    ) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.SURU_VOICE_MUTED] = muted
            prefs[Keys.SURU_SPEECH_SPEED_X100] = (speechSpeed.coerceIn(0.75f, 1.5f) * 100).toInt()
            prefs[Keys.SURU_VOICE_LANG] = languageName
        }
    }

    suspend fun updateSuruMemoryPersonalization(enabled: Boolean) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.SURU_PERSONALIZATION] = enabled
        }
    }

    suspend fun updateSuruBriefingSettings(
        morningEnabled: Boolean,
        eveningEnabled: Boolean,
        morningHour: Int = 8,
        eveningHour: Int = 20
    ) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.SURU_MORNING_BRIEFING] = morningEnabled
            prefs[Keys.SURU_EVENING_REFLECTION] = eveningEnabled
            prefs[Keys.SURU_MORNING_HOUR] = morningHour.coerceIn(5, 12)
            prefs[Keys.SURU_EVENING_HOUR] = eveningHour.coerceIn(17, 23)
        }
    }

    suspend fun resetDefaults() {
        context.lifeOsDataStore.edit { it.clear() }
    }

    companion object {
        fun generateFriendCode(handle: String, name: String): String {
            val seed = "${handle.lowercase()}:${name.lowercase()}".hashCode().absoluteValue
            val prefix = handle.removePrefix("@")
                .uppercase()
                .replace(Regex("[^A-Z0-9]"), "")
                .take(4)
                .padEnd(4, 'X')
            val suffix = (seed % 9000 + 1000).toString()
            return "LIFE-$prefix-$suffix"
        }
    }
}
