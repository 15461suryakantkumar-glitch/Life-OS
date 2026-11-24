package com.example.kutta

import androidx.annotation.DrawableRes
import com.example.R
import com.example.data.local.TaskEntity
import java.util.Calendar

/**
 * Five core emotional states of KUTTA — LIFE OS's single permanent companion.
 */
enum class KuttaEmotion(
    val label: String,
    val badgeText: String,
    val defaultPose: KuttaPose
) {
    HAPPY("Happy", "Mast Mood", KuttaPose.HAPPY),
    FOCUSED("Focused", "Study Mode", KuttaPose.FOCUSED),
    SLEEPY("Sleepy", "Rest Time", KuttaPose.SLEEPY),
    WORRIED("Worried", "Caring Nudge", KuttaPose.WORRIED),
    PROUD("Proud", "Champion", KuttaPose.PROUD)
}

/**
 * Nine reusable local KUTTA mascot drawable poses.
 */
enum class KuttaPose(@DrawableRes val drawableRes: Int, val contentDescription: String) {
    HAPPY(R.drawable.kutta_happy, "KUTTA smiling joyfully with paws raised"),
    FOCUSED(R.drawable.kutta_focused, "KUTTA wearing study glasses reading a book"),
    SLEEPY(R.drawable.kutta_sleepy, "KUTTA looking drowsy and sleepy"),
    WORRIED(R.drawable.kutta_worried, "KUTTA looking softly concerned and supportive"),
    PROUD(R.drawable.kutta_proud, "KUTTA smiling proudly with chest lifted"),
    MORNING(R.drawable.kutta_morning, "KUTTA waving hello in a cheerful morning stretch"),
    SLEEPING(R.drawable.kutta_sleeping, "KUTTA curled up sleeping peacefully"),
    CELEBRATING(R.drawable.kutta_celebrating, "KUTTA jumping mid-air in celebration"),
    IDLE(R.drawable.kutta_idle, "KUTTA relaxing calmly with a friendly smile")
}

/**
 * All user and system events that trigger KUTTA pose transitions & Hinglish responses.
 */
sealed interface KuttaEvent {
    data object AppOpenedMorning : KuttaEvent
    data object NightRest : KuttaEvent
    data class TaskCompleted(val taskTitle: String = "") : KuttaEvent
    data object TaskPendingNudge : KuttaEvent
    data object FocusStarted : KuttaEvent
    data class FocusCompleted(val minutes: Int = 25) : KuttaEvent
    data object WaterReminder : KuttaEvent
    data class WaterLogged(val currentMl: Int, val goalMl: Int) : KuttaEvent
    data class StreakMaintained(val streakDays: Int) : KuttaEvent
    data object PetTapped : KuttaEvent
    data class CheckInLogged(val moodScore: Int) : KuttaEvent
    data object CyclePeriodLogged : KuttaEvent
    data class CyclePeriodApproaching(val daysUntil: Int) : KuttaEvent
    data object CyclePeriodStarted : KuttaEvent
    data object CyclePeriodEnded : KuttaEvent
    data object CycleSymptomsLogged : KuttaEvent
    data object CycleCareRest : KuttaEvent
}

data class KuttaVisualState(
    val emotion: KuttaEmotion = KuttaEmotion.HAPPY,
    val pose: KuttaPose = KuttaPose.IDLE,
    val dialogue: String = "Hey, Malkin! Main hoon KUTTA — aaj saath mein mast kaam karte hain!",
    val activeEvent: KuttaEvent? = null,
    val isWaterHighlight: Boolean = false,
    val showHearts: Boolean = false,
    val showSparkles: Boolean = false,
    val showSleepZzz: Boolean = false,
    val isTransientOverride: Boolean = false
)

/**
 * Natural Hinglish dialogue engine in Roman script.
 * KUTTA always refers to himself as "KUTTA" and the user as "Malkin".
 * Rotates naturally so repeated events do not feel robotic.
 */
object KuttaDialogue {
    private val lastLineByBucket = HashMap<String, String>()

    val happyLines = listOf(
        "Areyyy Malkin! Aaj toh mood ekdum mast hai!",
        "KUTTA super happy hai kyunki Malkin ne itna achha kiya!",
        "Dekho KUTTA kaise nach raha hai, Malkin!"
    )

    val focusedLines = listOf(
        "Shhh Malkin, ab full focus mode on!",
        "Main bhi book khol ke baith gaya hoon, Malkin!",
        "Chalo Malkin, saath mein complete karte hain!"
    )

    val sleepyLines = listOf(
        "Bas Malkin, thoda aaram kar lo...",
        "Neend aa rahi hai Malkin, par aapke saath hoon.",
        "Aankhein band ho rahi hain, goodnight Malkin..."
    )

    val worriedLines = listOf(
        "Koi baat nahi Malkin, KUTTA hai na saath.",
        "Sab theek ho jayega, ek step lo bas.",
        "Malkin thak gayi ho kya? Thoda paani pee lo."
    )

    val proudLines = listOf(
        "OYYYY! Malkin ne kar dikhaya!",
        "Mujhe pata tha meri Malkin best hai!",
        "Champion level kaam kiya, Malkin!"
    )

    val morningLines = listOf(
        "Good morning Malkin! Naya din, naya start!",
        "Uth gayi Malkin? Chalo aaj ka plan dekhte hain.",
        "Good morning Malkin! KUTTA ready hai — aaj kuch bada karte hain!"
    )

    val taskCompletedLines = listOf(
        "Waah Malkin! Ek aur kaam khatam!",
        "Shabash Malkin! Aage badhte raho!",
        "Mast speed hai Malkin! KUTTA khush ho gaya!"
    )

    val waterReminderLines = listOf(
        "Malkin, paani peene ka time ho gaya! Jaldi ek sip lo!",
        "Hydration check, Malkin! KUTTA yaad dila raha hai!",
        "Malkin, bottle paas mein rakho — ek glass paani pee lo abhi!"
    )

    val waterLoggedLines = listOf(
        "Yay Malkin! Hydration level up — KUTTA proud hai!",
        "Good job Malkin! Paani peete raho, energy high rahegi!",
        "Ek aur sip done, Malkin! Health bhi aur focus bhi set!"
    )

    val nightLines = listOf(
        "Raat ho gayi Malkin, ab rest zaroori hai.",
        "KUTTA so raha hai, aap bhi aaram karo.",
        "Shubh ratri Malkin... KUTTA yahin paas so raha hai."
    )

    val petTappedLines = listOf(
        "Hehe Malkin, gulguli ho rahi hai! KUTTA hamesha aapki team mein hai!",
        "Arre Malkin! Ek aur head pat milega kya? KUTTA khush ho gaya!",
        "Bow-wow Malkin! KUTTA full charge ho gaya aapke pyaar se!",
        "KUTTA + Malkin = sabse best team forever!"
    )

    val streakLines = listOf(
        "OYYYY Malkin! Streak chal rahi hai — KUTTA naach raha hai!",
        "Kya consistency hai Malkin! KUTTA ko aap par garv hai!",
        "Champion streak, Malkin! Aise hi roz aage badho!"
    )

    val cycleApproachingLines = listOf(
        "Malkin, date paas aa rahi hai. Thoda rest aur hydration zaroor rakhna.",
        "Malkin, aane wale dino ke liye thoda extra aaram plan kar lena. KUTTA saath hai."
    )

    val cycleStartedOrLoggedLines = listOf(
        "Koi baat nahi Malkin, KUTTA yahin hai. Aaj slow chalenge.",
        "Malkin, aaj gentle mode on karte hain. Take care."
    )

    val cycleSymptomsLines = listOf(
        "Malkin, dard ya thakan ho toh khud ko push mat karo. Garam paani lo.",
        "Aaj pressure mat lo Malkin — KUTTA ke saath bas comfortable raho."
    )

    val cycleEndedLines = listOf(
        "Yay Malkin! Fresh energy ke saath wapas!",
        "Great Malkin! Ab aaram se apna pace wapas pakadte hain!"
    )

    fun forEmotion(emotion: KuttaEmotion, pickIndex: Int? = null): String {
        val list = when (emotion) {
            KuttaEmotion.HAPPY -> happyLines
            KuttaEmotion.FOCUSED -> focusedLines
            KuttaEmotion.SLEEPY -> sleepyLines
            KuttaEmotion.WORRIED -> worriedLines
            KuttaEmotion.PROUD -> proudLines
        }
        return pickRotating("emotion_${emotion.name}", list, pickIndex)
    }

    fun forEvent(event: KuttaEvent, pickIndex: Int? = null): String {
        val (bucket, list) = when (event) {
            KuttaEvent.AppOpenedMorning -> "event_morning" to morningLines
            KuttaEvent.NightRest -> "event_night" to nightLines
            is KuttaEvent.TaskCompleted -> "event_task_done" to taskCompletedLines
            KuttaEvent.TaskPendingNudge -> "event_worried" to worriedLines
            KuttaEvent.FocusStarted -> "event_focused" to focusedLines
            is KuttaEvent.FocusCompleted -> "event_proud" to proudLines
            KuttaEvent.WaterReminder -> "event_water_rem" to waterReminderLines
            is KuttaEvent.WaterLogged -> "event_water_ok" to waterLoggedLines
            is KuttaEvent.StreakMaintained -> "event_streak" to streakLines
            KuttaEvent.PetTapped -> "event_tapped" to petTappedLines
            is KuttaEvent.CheckInLogged -> if (event.moodScore <= 2) {
                "event_worried" to worriedLines
            } else {
                "event_happy" to happyLines
            }
            KuttaEvent.CyclePeriodLogged -> "cycle_logged" to cycleStartedOrLoggedLines
            is KuttaEvent.CyclePeriodApproaching -> "cycle_approach" to cycleApproachingLines
            KuttaEvent.CyclePeriodStarted -> "cycle_started" to cycleStartedOrLoggedLines
            KuttaEvent.CyclePeriodEnded -> "cycle_ended" to cycleEndedLines
            KuttaEvent.CycleSymptomsLogged -> "cycle_symptoms" to cycleSymptomsLines
            KuttaEvent.CycleCareRest -> "cycle_logged" to cycleStartedOrLoggedLines
        }
        return pickRotating(bucket, list, pickIndex)
    }

    private fun pickRotating(bucket: String, options: List<String>, explicitIndex: Int?): String {
        if (options.isEmpty()) return "Hey Malkin, KUTTA yahin hai!"
        if (explicitIndex != null) {
            return options[((explicitIndex % options.size) + options.size) % options.size]
        }
        val previous = lastLineByBucket[bucket]
        val candidates = if (options.size > 1 && previous != null) {
            options.filterNot { it == previous }
        } else {
            options
        }
        val chosen = candidates.random()
        lastLineByBucket[bucket] = chosen
        return chosen
    }

    fun resetRotationForTest() {
        lastLineByBucket.clear()
    }
}

/**
 * Pure deterministic resolver mapping app telemetry + active transient KUTTA events
 * into the exact KuttaVisualState.
 */
object KuttaEventHandler {
    fun resolveBaselineEmotion(
        isTimerRunning: Boolean,
        pendingTasks: List<TaskEntity>,
        distractingMinutesToday: Int,
        dailyScreenLimitMinutes: Int,
        recentPositiveScore: Boolean,
        lastCheckInMoodScore: Int?,
        nowMillis: Long = System.currentTimeMillis(),
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): KuttaEmotion {
        if (isTimerRunning) return KuttaEmotion.FOCUSED

        val isNight = hourOfDay >= 22 || hourOfDay < 6
        if (isNight) return KuttaEmotion.SLEEPY

        if (recentPositiveScore) return KuttaEmotion.PROUD

        val twoHoursMillis = 2L * 60L * 60L * 1000L
        val hasStalePendingTask = pendingTasks.any { (nowMillis - it.createdAtMillis) >= twoHoursMillis }
        val highDistraction = distractingMinutesToday > (dailyScreenLimitMinutes / 2).coerceAtLeast(45)
        val difficultCheckIn = lastCheckInMoodScore != null && lastCheckInMoodScore <= 2

        if (hasStalePendingTask || highDistraction || difficultCheckIn) {
            return KuttaEmotion.WORRIED
        }

        return KuttaEmotion.HAPPY
    }

    fun stateForBaseline(
        emotion: KuttaEmotion,
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        dialogueIndex: Int? = null
    ): KuttaVisualState {
        val isNight = hourOfDay >= 22 || hourOfDay < 6
        val pose = when {
            emotion == KuttaEmotion.SLEEPY && isNight -> KuttaPose.SLEEPING
            emotion == KuttaEmotion.HAPPY -> KuttaPose.IDLE
            else -> emotion.defaultPose
        }
        val text = if (emotion == KuttaEmotion.SLEEPY && isNight) {
            KuttaDialogue.forEvent(KuttaEvent.NightRest, dialogueIndex)
        } else {
            KuttaDialogue.forEmotion(emotion, dialogueIndex)
        }
        return KuttaVisualState(
            emotion = emotion,
            pose = pose,
            dialogue = text,
            activeEvent = null,
            showHearts = emotion == KuttaEmotion.HAPPY,
            showSparkles = emotion == KuttaEmotion.PROUD,
            showSleepZzz = emotion == KuttaEmotion.SLEEPY,
            isTransientOverride = false
        )
    }

    fun stateForEvent(event: KuttaEvent, dialogueIndex: Int? = null): KuttaVisualState {
        val dialogue = KuttaDialogue.forEvent(event, dialogueIndex)
        return when (event) {
            KuttaEvent.AppOpenedMorning -> KuttaVisualState(
                emotion = KuttaEmotion.HAPPY,
                pose = KuttaPose.MORNING,
                dialogue = dialogue,
                activeEvent = event,
                showHearts = true,
                isTransientOverride = true
            )
            KuttaEvent.NightRest -> KuttaVisualState(
                emotion = KuttaEmotion.SLEEPY,
                pose = KuttaPose.SLEEPING,
                dialogue = dialogue,
                activeEvent = event,
                showSleepZzz = true,
                isTransientOverride = false
            )
            is KuttaEvent.TaskCompleted -> KuttaVisualState(
                emotion = KuttaEmotion.HAPPY,
                pose = KuttaPose.HAPPY,
                dialogue = dialogue,
                activeEvent = event,
                showHearts = true,
                showSparkles = true,
                isTransientOverride = true
            )
            KuttaEvent.TaskPendingNudge -> KuttaVisualState(
                emotion = KuttaEmotion.WORRIED,
                pose = KuttaPose.WORRIED,
                dialogue = dialogue,
                activeEvent = event,
                isTransientOverride = true
            )
            KuttaEvent.FocusStarted -> KuttaVisualState(
                emotion = KuttaEmotion.FOCUSED,
                pose = KuttaPose.FOCUSED,
                dialogue = dialogue,
                activeEvent = event,
                isTransientOverride = true
            )
            is KuttaEvent.FocusCompleted -> KuttaVisualState(
                emotion = KuttaEmotion.PROUD,
                pose = KuttaPose.CELEBRATING,
                dialogue = dialogue,
                activeEvent = event,
                showSparkles = true,
                isTransientOverride = true
            )
            KuttaEvent.WaterReminder -> KuttaVisualState(
                emotion = KuttaEmotion.HAPPY,
                pose = KuttaPose.MORNING,
                dialogue = dialogue,
                activeEvent = event,
                isWaterHighlight = true,
                isTransientOverride = true
            )
            is KuttaEvent.WaterLogged -> KuttaVisualState(
                emotion = KuttaEmotion.PROUD,
                pose = KuttaPose.HAPPY,
                dialogue = dialogue,
                activeEvent = event,
                isWaterHighlight = true,
                showHearts = true,
                isTransientOverride = true
            )
            is KuttaEvent.StreakMaintained -> KuttaVisualState(
                emotion = KuttaEmotion.PROUD,
                pose = KuttaPose.CELEBRATING,
                dialogue = dialogue,
                activeEvent = event,
                showSparkles = true,
                isTransientOverride = true
            )
            KuttaEvent.PetTapped -> KuttaVisualState(
                emotion = KuttaEmotion.HAPPY,
                pose = KuttaPose.HAPPY,
                dialogue = dialogue,
                activeEvent = event,
                showHearts = true,
                isTransientOverride = true
            )
            is KuttaEvent.CheckInLogged -> KuttaVisualState(
                emotion = if (event.moodScore <= 2) KuttaEmotion.WORRIED else KuttaEmotion.HAPPY,
                pose = if (event.moodScore <= 2) KuttaPose.WORRIED else KuttaPose.HAPPY,
                dialogue = dialogue,
                activeEvent = event,
                showHearts = event.moodScore >= 3,
                isTransientOverride = true
            )
            KuttaEvent.CyclePeriodLogged,
            KuttaEvent.CyclePeriodStarted,
            KuttaEvent.CycleCareRest -> KuttaVisualState(
                emotion = KuttaEmotion.SLEEPY,
                pose = KuttaPose.SLEEPING,
                dialogue = dialogue,
                activeEvent = event,
                showHearts = true,
                isTransientOverride = true
            )
            is KuttaEvent.CyclePeriodApproaching,
            KuttaEvent.CycleSymptomsLogged -> KuttaVisualState(
                emotion = KuttaEmotion.WORRIED,
                pose = KuttaPose.WORRIED,
                dialogue = dialogue,
                activeEvent = event,
                showHearts = true,
                isTransientOverride = true
            )
            KuttaEvent.CyclePeriodEnded -> KuttaVisualState(
                emotion = KuttaEmotion.PROUD,
                pose = KuttaPose.PROUD,
                dialogue = dialogue,
                activeEvent = event,
                showSparkles = true,
                isTransientOverride = true
            )
        }
    }

    fun durationMillisFor(event: KuttaEvent): Long = when (event) {
        KuttaEvent.AppOpenedMorning -> 5000L
        is KuttaEvent.FocusCompleted, is KuttaEvent.StreakMaintained -> 4600L
        KuttaEvent.WaterReminder, is KuttaEvent.TaskCompleted -> 4000L
        KuttaEvent.CyclePeriodLogged,
        KuttaEvent.CyclePeriodStarted,
        KuttaEvent.CycleSymptomsLogged,
        is KuttaEvent.CyclePeriodApproaching,
        KuttaEvent.CyclePeriodEnded -> 5000L
        else -> 3200L
    }
}
