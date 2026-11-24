package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.LifeOsThemeMode
import com.example.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import kotlin.math.absoluteValue

private val Context.lifeOsDataStore by preferencesDataStore(name = "life_os_prefs")

enum class PetAccessory(val label: String, val requiredLevel: Int, val bonusLabel: String) {
    NONE("No Accessory", 1, "Natural look"),
    HOLO_VISOR("Sun Shades", 2, "+Focus Style"),
    CYBER_SCARF("Cozy Scarf", 3, "+Warmth"),
    ASTRAL_CROWN("Star Crown", 5, "+Milestone Glow"),
    ORBITAL_HALO("Leaf Sprout", 7, "+Mastery Charm")
}

enum class PetGrowthStage(val title: String, val minLevel: Int, val description: String) {
    SPARK("Little Spark", 1, "Just joined your desk and learning your daily rhythm."),
    PRISM("Cozy Sprout", 3, "Bright-eyed and energized by your steady habits."),
    SENTINEL("Steady Explorer", 5, "Confident and upbeat from consistent daily progress."),
    ASTRAL("Starry Guardian", 7, "Calm, focused, and proud of your long streaks."),
    SOVEREIGN("Golden Luminary", 10, "Your highest companion bond earned through lasting consistency.")
}

enum class CompanionSpecies(
    val displayTitle: String,
    val defaultName: String,
    val archetypeTag: String,
    val personalityBio: String
) {
    MOCHI_BEAR(
        displayTitle = "Mochi Bear",
        defaultName = "Mochi",
        archetypeTag = "Calm & Steady",
        personalityBio = "Warm, grounding, and patient. Built for steady daily plans and calm progress."
    ),
    PEBBLE_FOX(
        displayTitle = "Pebble Fox",
        defaultName = "Pebble",
        archetypeTag = "Quick & Curious",
        personalityBio = "Playful, sharp, and creative. Thrives on fast focus sprints and fresh ideas."
    ),
    BOBA_OTTER(
        displayTitle = "Boba Otter",
        defaultName = "Boba",
        archetypeTag = "Balanced & Social",
        personalityBio = "Cheerful, resilient, and encouraging. Loves streaks, habits, and shared wins."
    ),
    PIP_OWL(
        displayTitle = "Pip Owl",
        defaultName = "Pip",
        archetypeTag = "Deep & Thoughtful",
        personalityBio = "Reflective, analytical, and composed. Ideal for late-evening study and deep work."
    );

    companion object {
        fun fromStored(raw: String?): CompanionSpecies {
            return entries.firstOrNull { it.name == raw } ?: MOCHI_BEAR
        }
    }
}

enum class CompanionMood(val label: String, val nudgeHint: String) {
    JOYFUL("Joyful", "Proud of your recent progress!"),
    FOCUSED("Focused", "Locked into deep focus with you."),
    CHILL("Chill", "Relaxed and ready when you are."),
    SLEEPY("Sleepy", "Quiet hours — time to wind down gently."),
    WORRIED("Gentle Nudge", "A task or screen budget needs a quick check.")
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
        if (isTimerRunning) return CompanionMood.FOCUSED
        if (hasRecentPositiveAction || petHappiness >= 88) return CompanionMood.JOYFUL

        val twoHoursMillis = 2L * 60L * 60L * 1000L
        val hasStalePendingTask = pendingTasks.any { (nowMillis - it.createdAtMillis) >= twoHoursMillis }
        val highDistraction = distractingMinutesToday > (dailyScreenLimitMinutes / 2).coerceAtLeast(45)
        if (hasStalePendingTask || highDistraction) return CompanionMood.WORRIED

        val isQuietNight = hourOfDay >= 22 || hourOfDay < 6
        if (isQuietNight || petEnergy < 35) return CompanionMood.SLEEPY

        return CompanionMood.CHILL
    }
}

object PersonalityQuizEvaluator {
    /**
     * Maps 3 quick personality answers (each 0..3) to one appointed CompanionSpecies.
     */
    fun appointCompanion(q1: Int, q2: Int, q3: Int): CompanionSpecies {
        val scores = mutableMapOf(
            CompanionSpecies.MOCHI_BEAR to 0,
            CompanionSpecies.PEBBLE_FOX to 0,
            CompanionSpecies.BOBA_OTTER to 0,
            CompanionSpecies.PIP_OWL to 0
        )
        when (q1) {
            0 -> scores[CompanionSpecies.MOCHI_BEAR] = scores.getValue(CompanionSpecies.MOCHI_BEAR) + 2
            1 -> scores[CompanionSpecies.PEBBLE_FOX] = scores.getValue(CompanionSpecies.PEBBLE_FOX) + 2
            2 -> scores[CompanionSpecies.PIP_OWL] = scores.getValue(CompanionSpecies.PIP_OWL) + 2
            else -> scores[CompanionSpecies.BOBA_OTTER] = scores.getValue(CompanionSpecies.BOBA_OTTER) + 2
        }
        when (q2) {
            0 -> scores[CompanionSpecies.MOCHI_BEAR] = scores.getValue(CompanionSpecies.MOCHI_BEAR) + 2
            1 -> scores[CompanionSpecies.PIP_OWL] = scores.getValue(CompanionSpecies.PIP_OWL) + 2
            2 -> scores[CompanionSpecies.PEBBLE_FOX] = scores.getValue(CompanionSpecies.PEBBLE_FOX) + 2
            else -> scores[CompanionSpecies.BOBA_OTTER] = scores.getValue(CompanionSpecies.BOBA_OTTER) + 2
        }
        when (q3) {
            0 -> scores[CompanionSpecies.MOCHI_BEAR] = scores.getValue(CompanionSpecies.MOCHI_BEAR) + 2
            1 -> scores[CompanionSpecies.PEBBLE_FOX] = scores.getValue(CompanionSpecies.PEBBLE_FOX) + 2
            2 -> scores[CompanionSpecies.BOBA_OTTER] = scores.getValue(CompanionSpecies.BOBA_OTTER) + 2
            else -> scores[CompanionSpecies.PIP_OWL] = scores.getValue(CompanionSpecies.PIP_OWL) + 2
        }
        return scores.maxByOrNull { it.value }?.key ?: CompanionSpecies.MOCHI_BEAR
    }
}

enum class UserAvatarPreset(val label: String) {
    SUNNY_CLOUD("Sunny Cloud"),
    FOREST_DEER("Forest Deer"),
    OCEAN_WHALE("Ocean Whale"),
    BERRY_CAT("Berry Cat"),
    STAR_BUNNY("Star Bunny"),
    AMBER_FOX("Amber Fox");

    companion object {
        fun fromStored(raw: String?): UserAvatarPreset {
            return entries.firstOrNull { it.name == raw } ?: SUNNY_CLOUD
        }
    }
}

data class UserPreferencesState(
    val isSignedIn: Boolean = false,
    val userName: String = "",
    val userHandle: String = "",
    val userEmail: String = "",
    val friendCode: String = "",
    val avatarPreset: UserAvatarPreset = UserAvatarPreset.SUNNY_CLOUD,
    val dailyFocusGoalMinutes: Int = 120,
    val dailyScreenLimitMinutes: Int = 180,
    val focusMinutesLoggedToday: Int = 0,
    val themeMode: LifeOsThemeMode = LifeOsThemeMode.MIDNIGHT_AURORA,
    val totalXp: Int = 0,
    val dailyStreak: Int = 0,
    val weeklyStreak: Int = 0,
    val lastActiveEpochDay: Long = -1L,
    val loginEpochDays: Set<Long> = emptySet(),
    val companionSpecies: CompanionSpecies = CompanionSpecies.MOCHI_BEAR,
    val personalityQuizCompleted: Boolean = false,
    val petName: String = "Mochi",
    val petHappiness: Int = 65,
    val petEnergy: Int = 75,
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
    val soundEffectsEnabled: Boolean = true
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
        val THEME_MODE = stringPreferencesKey("accent_preset")
        val TOTAL_XP = intPreferencesKey("total_xp")
        val DAILY_STREAK = intPreferencesKey("daily_streak")
        val WEEKLY_STREAK = intPreferencesKey("weekly_streak")
        val LAST_ACTIVE_DAY = longPreferencesKey("last_active_day")
        val LOGIN_DAYS = stringPreferencesKey("login_epoch_days")
        val COMPANION_SPECIES = stringPreferencesKey("companion_species")
        val QUIZ_COMPLETED = booleanPreferencesKey("personality_quiz_completed")
        val PET_NAME = stringPreferencesKey("pet_name")
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
        val SOUND_EFFECTS = booleanPreferencesKey("sound_effects_enabled")
    }

    val preferencesFlow: Flow<UserPreferencesState> = context.lifeOsDataStore.data.map { prefs ->
        val theme = LifeOsThemeMode.fromStored(prefs[Keys.THEME_MODE])
        val accessory = PetAccessory.entries.firstOrNull {
            it.name == prefs[Keys.PET_ACCESSORY]
        } ?: PetAccessory.NONE
        val species = CompanionSpecies.fromStored(prefs[Keys.COMPANION_SPECIES])
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
            themeMode = theme,
            totalXp = prefs[Keys.TOTAL_XP] ?: 0,
            dailyStreak = prefs[Keys.DAILY_STREAK] ?: 0,
            weeklyStreak = prefs[Keys.WEEKLY_STREAK] ?: 0,
            lastActiveEpochDay = prefs[Keys.LAST_ACTIVE_DAY] ?: -1L,
            loginEpochDays = loginDays,
            companionSpecies = species,
            personalityQuizCompleted = prefs[Keys.QUIZ_COMPLETED] ?: false,
            petName = prefs[Keys.PET_NAME] ?: species.defaultName,
            petHappiness = prefs[Keys.PET_HAPPINESS] ?: 65,
            petEnergy = prefs[Keys.PET_ENERGY] ?: 75,
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
            soundEffectsEnabled = prefs[Keys.SOUND_EFFECTS] ?: true
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

            val currentHap = prefs[Keys.PET_HAPPINESS] ?: 65
            val currentEnergy = prefs[Keys.PET_ENERGY] ?: 75
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
            val currentHap = prefs[Keys.PET_HAPPINESS] ?: 65
            val currentEnergy = prefs[Keys.PET_ENERGY] ?: 75
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
        petName: String,
        companionSpecies: CompanionSpecies,
        focusGoal: Int,
        themeMode: LifeOsThemeMode
    ) {
        val today = DateTimeUtils.todayEpochDay()
        val cleanName = name.trim().ifEmpty { "Explorer" }
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
                    .ifEmpty { "explorer" }
                "@$slug"
            }
        }
        val cleanPet = petName.trim().ifEmpty { companionSpecies.defaultName }

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
            prefs[Keys.COMPANION_SPECIES] = companionSpecies.name
            prefs[Keys.QUIZ_COMPLETED] = true
            prefs[Keys.PET_NAME] = cleanPet
            prefs[Keys.FOCUS_GOAL] = focusGoal.coerceIn(15, 600)
            prefs[Keys.THEME_MODE] = themeMode.name
            prefs[Keys.LOGIN_DAYS] = loginDays.sorted().joinToString(",")
        }
    }

    suspend fun appointCompanionFromQuiz(species: CompanionSpecies, customName: String?) {
        val cleanName = customName?.trim()?.takeIf { it.isNotBlank() } ?: species.defaultName
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.COMPANION_SPECIES] = species.name
            prefs[Keys.PET_NAME] = cleanName
            prefs[Keys.QUIZ_COMPLETED] = true
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
            val cleanName = name.trim().ifEmpty { "Explorer" }
            val cleanHandle = handle.trim().ifEmpty { "@explorer" }.let {
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

    suspend fun updatePetName(name: String) {
        context.lifeOsDataStore.edit { prefs ->
            val fallback = CompanionSpecies.fromStored(prefs[Keys.COMPANION_SPECIES]).defaultName
            prefs[Keys.PET_NAME] = name.trim().ifEmpty { fallback }
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
