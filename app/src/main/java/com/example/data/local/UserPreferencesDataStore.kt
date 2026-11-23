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
    val userName: String = "Alex Mercer",
    val userHandle: String = "@alex.os",
    val userEmail: String = "",
    val dailyFocusGoalMinutes: Int = 120,
    val dailyScreenLimitMinutes: Int = 180,
    val focusMinutesLoggedToday: Int = 45,
    val accentPreset: ThemeAccentPreset = ThemeAccentPreset.VIOLET_CYAN,
    val totalXp: Int = 620,
    val dailyStreak: Int = 6,
    val weeklyStreak: Int = 3,
    val lastActiveEpochDay: Long = DateTimeUtils.todayEpochDay(),
    val petName: String = "NOVA",
    val petHappiness: Int = 84,
    val petEnergy: Int = 78,
    val equippedAccessory: PetAccessory = PetAccessory.HOLO_VISOR,
    val unlockedBadges: Set<String> = setOf("FIRST_LAUNCH", "STREAK_3"),
    val shareXpWithFriends: Boolean = true,
    val shareStreaksWithFriends: Boolean = true,
    val shareFocusWithFriends: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val taskRemindersEnabled: Boolean = true,
    val wellnessAlertsEnabled: Boolean = true
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
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val TASK_REMINDERS = booleanPreferencesKey("task_reminders")
        val WELLNESS_ALERTS = booleanPreferencesKey("wellness_alerts")
    }

    val preferencesFlow: Flow<UserPreferencesState> = context.lifeOsDataStore.data.map { prefs ->
        val accent = ThemeAccentPreset.entries.firstOrNull {
            it.name == prefs[Keys.ACCENT_PRESET]
        } ?: ThemeAccentPreset.VIOLET_CYAN

        val accessory = PetAccessory.entries.firstOrNull {
            it.name == prefs[Keys.PET_ACCESSORY]
        } ?: PetAccessory.HOLO_VISOR

        val badgesRaw = prefs[Keys.UNLOCKED_BADGES] ?: "FIRST_LAUNCH,STREAK_3"
        val badges = badgesRaw.split(",").filter { it.isNotBlank() }.toSet()

        UserPreferencesState(
            isSignedIn = prefs[Keys.IS_SIGNED_IN] ?: false,
            userName = prefs[Keys.USER_NAME] ?: "Alex Mercer",
            userHandle = prefs[Keys.USER_HANDLE] ?: "@alex.os",
            userEmail = prefs[Keys.USER_EMAIL] ?: "",
            dailyFocusGoalMinutes = prefs[Keys.FOCUS_GOAL] ?: 120,
            dailyScreenLimitMinutes = prefs[Keys.SCREEN_LIMIT] ?: 180,
            focusMinutesLoggedToday = prefs[Keys.FOCUS_TODAY] ?: 45,
            accentPreset = accent,
            totalXp = prefs[Keys.TOTAL_XP] ?: 620,
            dailyStreak = prefs[Keys.DAILY_STREAK] ?: 6,
            weeklyStreak = prefs[Keys.WEEKLY_STREAK] ?: 3,
            lastActiveEpochDay = prefs[Keys.LAST_ACTIVE_DAY] ?: DateTimeUtils.todayEpochDay(),
            petName = prefs[Keys.PET_NAME] ?: "NOVA",
            petHappiness = prefs[Keys.PET_HAPPINESS] ?: 84,
            petEnergy = prefs[Keys.PET_ENERGY] ?: 78,
            equippedAccessory = accessory,
            unlockedBadges = badges,
            shareXpWithFriends = prefs[Keys.SHARE_XP] ?: true,
            shareStreaksWithFriends = prefs[Keys.SHARE_STREAKS] ?: true,
            shareFocusWithFriends = prefs[Keys.SHARE_FOCUS] ?: false,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            taskRemindersEnabled = prefs[Keys.TASK_REMINDERS] ?: true,
            wellnessAlertsEnabled = prefs[Keys.WELLNESS_ALERTS] ?: true
        )
    }

    suspend fun awardXpAndBoostPet(xpDelta: Int, happinessDelta: Int, energyDelta: Int) {
        context.lifeOsDataStore.edit { prefs ->
            val currentXp = prefs[Keys.TOTAL_XP] ?: 620
            prefs[Keys.TOTAL_XP] = (currentXp + xpDelta).coerceAtLeast(0)
            val currentHap = prefs[Keys.PET_HAPPINESS] ?: 84
            val currentEnergy = prefs[Keys.PET_ENERGY] ?: 78
            prefs[Keys.PET_HAPPINESS] = (currentHap + happinessDelta).coerceIn(10, 100)
            prefs[Keys.PET_ENERGY] = (currentEnergy + energyDelta).coerceIn(10, 100)
            prefs[Keys.LAST_ACTIVE_DAY] = DateTimeUtils.todayEpochDay()
        }
    }

    suspend fun addFocusMinutes(minutes: Int) {
        context.lifeOsDataStore.edit { prefs ->
            val current = prefs[Keys.FOCUS_TODAY] ?: 45
            prefs[Keys.FOCUS_TODAY] = current + minutes
        }
    }

    suspend fun unlockBadge(badgeId: String) {
        context.lifeOsDataStore.edit { prefs ->
            val existing = (prefs[Keys.UNLOCKED_BADGES] ?: "FIRST_LAUNCH,STREAK_3")
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
        val cleanName = name.trim().ifEmpty { "Commander" }
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
                    .ifEmpty { "commander" }
                "@$slug.os"
            }
        }
        val cleanPet = petName.trim().ifEmpty { "NOVA" }.uppercase()

        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.IS_SIGNED_IN] = true
            prefs[Keys.USER_NAME] = cleanName
            prefs[Keys.USER_HANDLE] = derivedHandle
            prefs[Keys.USER_EMAIL] = derivedEmail
            prefs[Keys.PET_NAME] = cleanPet
            prefs[Keys.FOCUS_GOAL] = focusGoal.coerceIn(15, 600)
            prefs[Keys.ACCENT_PRESET] = accentPreset.name
            prefs[Keys.LAST_ACTIVE_DAY] = DateTimeUtils.todayEpochDay()
        }
    }

    suspend fun signOutSession() {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.IS_SIGNED_IN] = false
        }
    }

    suspend fun updateProfile(name: String, handle: String, focusGoal: Int, screenLimit: Int) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.USER_NAME] = name.trim().ifEmpty { "Alex Mercer" }
            prefs[Keys.USER_HANDLE] = handle.trim().ifEmpty { "@alex.os" }
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

    suspend fun updatePrivacy(shareXp: Boolean, shareStreaks: Boolean, shareFocus: Boolean) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.SHARE_XP] = shareXp
            prefs[Keys.SHARE_STREAKS] = shareStreaks
            prefs[Keys.SHARE_FOCUS] = shareFocus
        }
    }

    suspend fun updateNotifications(enabled: Boolean, reminders: Boolean, wellnessAlerts: Boolean) {
        context.lifeOsDataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS] = enabled
            prefs[Keys.TASK_REMINDERS] = reminders
            prefs[Keys.WELLNESS_ALERTS] = wellnessAlerts
        }
    }

    suspend fun resetDefaults() {
        context.lifeOsDataStore.edit { it.clear() }
    }
}
