package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeAccentPreset
import com.example.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.absoluteValue

private val Context.lifeOsDataStore by preferencesDataStore(name = "life_os_prefs")

enum class PetAccessory(val label: String, val requiredLevel: Int, val bonusLabel: String) {
    NONE("No Accessory", 1, "Pure astral form"),
    HOLO_VISOR("Holo Visor", 2, "+Focus Aura"),
    CYBER_SCARF("Cyan Scarf", 3, "+Energy Flow"),
    ASTRAL_CROWN("Astral Crown", 5, "+Sovereign Glow"),
    ORBITAL_HALO("Orbital Halo", 7, "+Mastery Rings")
}

enum class PetGrowthStage(val title: String, val minLevel: Int, val description: String) {
    SPARK("Spark Wisp", 1, "A newborn luminous core learning your daily rhythm."),
    PRISM("Prism Fox", 3, "Sharp geometric facets fueled by consistent streaks."),
    SENTINEL("Cyber Sentinel", 5, "Armored in violet light with synchronized orbital rings."),
    ASTRAL("Astral Guardian", 7, "Radiates calm dual-core energy across your command center."),
    SOVEREIGN("Sovereign Nova", 10, "Peak digital evolution achieved through relentless mastery.")
}

data class UserPreferencesState(
    val isSignedIn: Boolean = false,
    val userName: String = "",
    val userHandle: String = "",
    val userEmail: String = "",
    val friendCode: String = "",
    val dailyFocusGoalMinutes: Int = 120,
    val dailyScreenLimitMinutes: Int = 180,
    val focusMinutesLoggedToday: Int = 0,
    val accentPreset: ThemeAccentPreset = ThemeAccentPreset.VIOLET_CYAN,
    val totalXp: Int = 0,
    val dailyStreak: Int = 0,
    val weeklyStreak: Int = 0,
    val lastActiveEpochDay: Long = -1L,
    val petName: String = "NOVA",
    val petHappiness: Int = 50,
    val petEnergy: Int = 50,
    val equippedAccessory: PetAccessory = PetAccessory.NONE,
    val unlockedBadges: Set<String> = emptySet(),
    val shareXpWithFriends: Boolean = true,
    val shareStreaksWithFriends: Boolean = true,
    val shareFocusWithFriends: Boolean = false,
    val shareChallengesWithFriends: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val taskRemindersEnabled: Boolean = true,
    val wellnessAlertsEnabled: Boolean = true,
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
        val FOCUS_GOAL = intPreferencesKey("focus_goal_mins")
        val SCREEN_LIMIT = intPreferencesKey("screen_limit_mins")
        val FOCUS_TODAY = intPreferencesKey("focus_logged_today")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val TOTAL_XP = intPreferencesKey("total_xp")
        val DAILY_STREAK = intPreferencesKey("daily_streak")
        val WEEKLY_STREAK = intPreferencesKey("weekly_streak")
        val LAST_ACTIVE_DAY = longPreferencesKey("last_active_day")
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
        val SOUND_EFFECTS = booleanPreferencesKey("sound_effects_enabled")
    }

    val preferencesFlow: Flow<UserPreferencesState> = context.lifeOsDataStore.data.map { prefs ->
        val accent = ThemeAccentPreset.entries.firstOrNull {
            it.name == prefs[Keys.ACCENT_PRESET]
        } ?: ThemeAccentPreset.VIOLET_CYAN

        val accessory = PetAccessory.entries.firstOrNull {
            it.name == prefs[Keys.PET_ACCESSORY]
        } ?: PetAccessory.NONE

        val badgesRaw = prefs[Keys.UNLOCKED_BADGES] ?: ""
        val badges = badgesRaw.split(",").filter { it.isNotBlank() }.toSet()

        val savedHandle = prefs[Keys.USER_HANDLE] ?: ""
        val savedName = prefs[Keys.USER_NAME] ?: ""
        val savedCode = prefs[Keys.FRIEND_CODE]?.takeIf { it.isNotBlank() }
            ?: if (savedName.isNotBlank()) generateFriendCode(savedHandle, savedName) else ""

        UserPreferencesState(
            isSignedIn = prefs[Keys.IS_SIGNED_IN] ?: false,
            userName = savedName,
            userHandle = savedHandle,
            userEmail = prefs[Keys.USER_EMAIL] ?: "",
            friendCode = savedCode,
            dailyFocusGoalMinutes = prefs[Keys.FOCUS_GOAL] ?: 120,
            dailyScreenLimitMinutes = prefs[Keys.SCREEN_LIMIT] ?: 180,
            focusMinutesLoggedToday = prefs[Keys.FOCUS_TODAY] ?: 0,
            accentPreset = accent,
            totalXp = prefs[Keys.TOTAL_XP] ?: 0,
            dailyStreak = prefs[Keys.DAILY_STREAK] ?: 0,
            weeklyStreak = prefs[Keys.WEEKLY_STREAK] ?: 0,
            lastActiveEpochDay = prefs[Keys.LAST_ACTIVE_DAY] ?: -1L,
            petName = prefs[Keys.PET_NAME] ?: "NOVA",
            petHappiness = prefs[Keys.PET_HAPPINESS] ?: 50,
            petEnergy = prefs[Keys.PET_ENERGY] ?: 50,
            equippedAccessory = accessory,
            unlockedBadges = badges,
            shareXpWithFriends = prefs[Keys.SHARE_XP] ?: true,
            shareStreaksWithFriends = prefs[Keys.SHARE_STREAKS] ?: true,
            shareFocusWithFriends = prefs[Keys.SHARE_FOCUS] ?: false,
            shareChallengesWithFriends = prefs[Keys.SHARE_CHALLENGES] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            taskRemindersEnabled = prefs[Keys.TASK_REMINDERS] ?: true,
            wellnessAlertsEnabled = prefs[Keys.WELLNESS_ALERTS] ?: true,
            soundEffectsEnabled = prefs[Keys.SOUND_EFFECTS] ?: true
        )
    }

    suspend fun awardXpAndBoostPet(xpDelta: Int, happinessDelta: Int, energyDelta: Int) {
        val today = DateTimeUtils.todayEpochDay()
        context.lifeOsDataStore.edit { prefs ->
            val currentXp = prefs[Keys.TOTAL_XP] ?: 0
            prefs[Keys.TOTAL_XP] = (currentXp + xpDelta).coerceAtLeast(0)

            val currentHap = prefs[Keys.PET_HAPPINESS] ?: 50
            val currentEnergy = prefs[Keys.PET_ENERGY] ?: 50
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
            val currentHap = prefs[Keys.PET_HAPPINESS] ?: 50
            val currentEnergy = prefs[Keys.PET_ENERGY] ?: 50
            prefs[Keys.PET_HAPPINESS] = (currentHap + happinessDelta).coerceIn(10, 100)
            prefs[Keys.PET_ENERGY] = (currentEnergy + energyDelta).coerceIn(10, 100)
        }
    }

    suspend fun addFocusMinutes(minutes: Int) {
        context.lifeOsDataStore.edit { prefs ->
            val current = prefs[Keys.FOCUS_TODAY] ?: 0
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
        focusGoal: Int,
        accentPreset: ThemeAccentPreset
    ) {
        val cleanName = name.trim().ifEmpty { "Operator" }
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
                    .ifEmpty { "operator" }
                "@$slug.os"
            }
        }
        val cleanPet = petName.trim().ifEmpty { "NOVA" }.uppercase()

        context.lifeOsDataStore.edit { prefs ->
            val existingCode = prefs[Keys.FRIEND_CODE]?.takeIf { it.isNotBlank() }
                ?: generateFriendCode(derivedHandle, cleanName)
            prefs[Keys.IS_SIGNED_IN] = true
            prefs[Keys.USER_NAME] = cleanName
            prefs[Keys.USER_HANDLE] = derivedHandle
            prefs[Keys.USER_EMAIL] = derivedEmail
            prefs[Keys.FRIEND_CODE] = existingCode
            prefs[Keys.PET_NAME] = cleanPet
            prefs[Keys.FOCUS_GOAL] = focusGoal.coerceIn(15, 600)
            prefs[Keys.ACCENT_PRESET] = accentPreset.name
        }
    }

    suspend fun signOutSession() {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.IS_SIGNED_IN] = false
        }
    }

    suspend fun updateProfile(name: String, handle: String, focusGoal: Int, screenLimit: Int) {
        context.lifeOsDataStore.edit { prefs ->
            val cleanName = name.trim().ifEmpty { "Operator" }
            val cleanHandle = handle.trim().ifEmpty { "@operator.os" }.let {
                if (it.startsWith("@")) it else "@$it"
            }
            prefs[Keys.USER_NAME] = cleanName
            prefs[Keys.USER_HANDLE] = cleanHandle
            if (prefs[Keys.FRIEND_CODE].isNullOrBlank()) {
                prefs[Keys.FRIEND_CODE] = generateFriendCode(cleanHandle, cleanName)
            }
            prefs[Keys.FOCUS_GOAL] = focusGoal.coerceIn(15, 600)
            prefs[Keys.SCREEN_LIMIT] = screenLimit.coerceIn(30, 720)
        }
    }

    suspend fun updatePetName(name: String) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.PET_NAME] = name.trim().ifEmpty { "NOVA" }.uppercase()
        }
    }

    suspend fun equipAccessory(accessory: PetAccessory) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.PET_ACCESSORY] = accessory.name
        }
    }

    suspend fun updateAccentPreset(preset: ThemeAccentPreset) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.ACCENT_PRESET] = preset.name
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

    suspend fun updateNotifications(enabled: Boolean, reminders: Boolean, wellnessAlerts: Boolean) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS] = enabled
            prefs[Keys.TASK_REMINDERS] = reminders
            prefs[Keys.WELLNESS_ALERTS] = wellnessAlerts
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
