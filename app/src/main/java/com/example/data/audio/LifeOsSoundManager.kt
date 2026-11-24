package com.example.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.data.local.UserPreferencesState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class SoundCategory(val label: String, val description: String) {
    NAVIGATION("Navigation", "Switching screens, drawer & tabs"),
    BUTTON_INTERACTION("Button interaction", "Primary buttons, chips & toggles"),
    TASK_COMPLETION("Task completion", "Completing or adding tasks"),
    ACHIEVEMENT("Achievement", "Level ups & badge milestones"),
    STREAK_COMPLETION("Streak completion", "Daily habit & streak celebrations"),
    SURU_INTERACTION("SURU interaction", "Opening SURU, listening & plan confirmation"),
    KUTTA_INTERACTION("KUTTA interaction", "Tapping, feeding or patting KUTTA"),
    TOGETHER_MESSAGES("Together messages", "Incoming & sent Together chat messages"),
    REMINDERS("Reminders", "Gentle timer & reminder chimes"),
    NOTIFICATIONS("Notifications", "In-app notification alerts")
}

enum class LifeOsSoundCue(
    val rawResourceName: String,
    val category: SoundCategory,
    val baseGain: Float = 0.65f
) {
    NAV_SWITCH("sfx_nav_switch", SoundCategory.NAVIGATION, 0.36f),
    UI_TAP("sfx_ui_tap", SoundCategory.BUTTON_INTERACTION, 0.32f),
    UI_TOGGLE("sfx_ui_toggle", SoundCategory.BUTTON_INTERACTION, 0.40f),
    UI_CONFIRM("sfx_ui_confirm", SoundCategory.BUTTON_INTERACTION, 0.48f),
    UI_UNDO("sfx_ui_undo", SoundCategory.BUTTON_INTERACTION, 0.42f),
    TASK_CREATE("sfx_task_create", SoundCategory.TASK_COMPLETION, 0.50f),
    TASK_COMPLETE("sfx_task_complete", SoundCategory.TASK_COMPLETION, 0.62f),
    TASK_RESCHEDULE("sfx_task_reschedule", SoundCategory.REMINDERS, 0.48f),
    CALENDAR_CREATE("sfx_calendar_create", SoundCategory.REMINDERS, 0.50f),
    SCHEDULE_SAVED("sfx_schedule_saved", SoundCategory.REMINDERS, 0.55f),
    SURU_ACTIVATE("sfx_suru_activate", SoundCategory.SURU_INTERACTION, 0.52f),
    SURU_LISTENING("sfx_suru_listen", SoundCategory.SURU_INTERACTION, 0.48f),
    SURU_SUCCESS("sfx_suru_success", SoundCategory.SURU_INTERACTION, 0.58f),
    SURU_ERROR("sfx_suru_error", SoundCategory.SURU_INTERACTION, 0.46f),
    SURU_PLAN_COMPLETE("sfx_suru_plan", SoundCategory.SURU_INTERACTION, 0.64f),
    STREAK_ACTIVATE("sfx_streak_activate", SoundCategory.STREAK_COMPLETION, 0.66f),
    LEVEL_UP("sfx_level_up", SoundCategory.ACHIEVEMENT, 0.72f),
    ACHIEVEMENT_UNLOCK("sfx_achievement_unlock", SoundCategory.ACHIEVEMENT, 0.70f),
    FOCUS_COMPLETE("sfx_focus_complete", SoundCategory.REMINDERS, 0.66f),
    COMPANION_INTERACT("sfx_companion_tap", SoundCategory.KUTTA_INTERACTION, 0.52f),
    TOGETHER_MESSAGE("sfx_together_msg", SoundCategory.TOGETHER_MESSAGES, 0.54f),
    NOTIFICATION_PING("sfx_notif_ping", SoundCategory.NOTIFICATIONS, 0.52f),
    APP_OPEN_LOGO("sfx_app_open_logo", SoundCategory.NAVIGATION, 0.42f),
    STUDY_BREAK("sfx_study_break", SoundCategory.REMINDERS, 0.52f),
    CHAPTER_DONE("sfx_chapter_done", SoundCategory.ACHIEVEMENT, 0.60f)
}

/**
 * Soft, lavender-inspired acoustic sound & haptic manager for FEELORA 2.0.
 *
 * - Supports all 10 SoundCategory toggles independently plus master volume & haptics.
 * - Guards against playing during active SURU speech or while app is backgrounded.
 * - Respects system silent/vibrate ringer mode cleanly.
 */
class LifeOsSoundManager(
    private val appContext: Context,
    private val isSuruSpeakingProvider: () -> Boolean = { false }
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var soundPool: SoundPool? = null
    private val sampleIds = LinkedHashMap<LifeOsSoundCue, Int>()
    private val loadedSampleIds = HashSet<Int>()
    private val lastPlayedAt = HashMap<LifeOsSoundCue, Long>()
    @Volatile
    private var isInitialized = false
    @Volatile
    private var isReleased = false
    @Volatile
    private var isAppInForeground = true

    fun setAppInForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
    }

    fun preload() {
        if (isInitialized || isReleased) return
        isInitialized = true
        scope.launch {
            runCatching {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val pool = SoundPool.Builder()
                    .setMaxStreams(4)
                    .setAudioAttributes(attrs)
                    .build()
                pool.setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0) {
                        synchronized(loadedSampleIds) {
                            loadedSampleIds.add(sampleId)
                        }
                    }
                }
                soundPool = pool

                LifeOsSoundCue.entries.forEach { cue ->
                    val rawId = resolveRawResourceId(cue.rawResourceName)
                    val loadedId = if (rawId != 0) {
                        pool.load(appContext, rawId, 1)
                    } else {
                        val wavFile = ensureFallbackWavFile(cue)
                        if (wavFile != null && wavFile.exists()) {
                            pool.load(wavFile.absolutePath, 1)
                        } else {
                            0
                        }
                    }
                    if (loadedId != 0) {
                        sampleIds[cue] = loadedId
                    }
                }
            }
        }
    }

    fun previewCategory(category: SoundCategory, prefs: UserPreferencesState): Boolean {
        val cue = when (category) {
            SoundCategory.NAVIGATION -> LifeOsSoundCue.NAV_SWITCH
            SoundCategory.BUTTON_INTERACTION -> LifeOsSoundCue.UI_CONFIRM
            SoundCategory.TASK_COMPLETION -> LifeOsSoundCue.TASK_COMPLETE
            SoundCategory.ACHIEVEMENT -> LifeOsSoundCue.ACHIEVEMENT_UNLOCK
            SoundCategory.STREAK_COMPLETION -> LifeOsSoundCue.STREAK_ACTIVATE
            SoundCategory.SURU_INTERACTION -> LifeOsSoundCue.SURU_SUCCESS
            SoundCategory.KUTTA_INTERACTION -> LifeOsSoundCue.COMPANION_INTERACT
            SoundCategory.TOGETHER_MESSAGES -> LifeOsSoundCue.TOGETHER_MESSAGE
            SoundCategory.REMINDERS -> LifeOsSoundCue.FOCUS_COMPLETE
            SoundCategory.NOTIFICATIONS -> LifeOsSoundCue.NOTIFICATION_PING
        }
        return playCue(cue, prefs, bypassSpeechGuard = true)
    }

    fun playCue(
        cue: LifeOsSoundCue,
        prefs: UserPreferencesState,
        bypassSpeechGuard: Boolean = false
    ): Boolean {
        if (!isAppInForeground && cue.category != SoundCategory.REMINDERS && cue.category != SoundCategory.NOTIFICATIONS) {
            return false
        }
        if (!shouldPlayForPrefs(cue, prefs)) return false
        if (!bypassSpeechGuard && isSuruSpeakingProvider()) return false

        if (prefs.hapticsEnabled && cue.category != SoundCategory.NAVIGATION) {
            triggerSubtleHaptic()
        }

        val now = System.currentTimeMillis()
        val cooldown = if (cue.category == SoundCategory.NAVIGATION) 110L else 45L
        synchronized(lastPlayedAt) {
            val prev = lastPlayedAt[cue] ?: 0L
            if (now - prev < cooldown) return false
            lastPlayedAt[cue] = now
        }

        if (!isInitialized) preload()

        return runCatching {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null && audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                return@runCatching false
            }
            val pool = soundPool ?: return@runCatching false
            val sampleId = sampleIds[cue] ?: return@runCatching false
            val ready = synchronized(loadedSampleIds) { loadedSampleIds.contains(sampleId) }
            if (!ready) return@runCatching false
            val vol = (cue.baseGain * prefs.soundVolume).coerceIn(0.05f, 1.0f)
            pool.play(sampleId, vol, vol, 1, 0, 1.0f)
            true
        }.getOrDefault(false)
    }

    fun playTaskComplete(enabled: Boolean, volume: Float = 0.72f) =
        playLegacy(LifeOsSoundCue.TASK_COMPLETE, enabled, volume)

    fun playStreakActivate(enabled: Boolean, volume: Float = 0.72f) =
        playLegacy(LifeOsSoundCue.STREAK_ACTIVATE, enabled, volume)

    fun playLevelUp(enabled: Boolean, volume: Float = 0.72f) =
        playLegacy(LifeOsSoundCue.LEVEL_UP, enabled, volume)

    fun playFocusComplete(enabled: Boolean, volume: Float = 0.72f) =
        playLegacy(LifeOsSoundCue.FOCUS_COMPLETE, enabled, volume)

    private fun playLegacy(cue: LifeOsSoundCue, enabled: Boolean, volume: Float) {
        if (!enabled || isReleased || isSuruSpeakingProvider()) return
        if (!isInitialized) preload()
        runCatching {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null && audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                return
            }
            val pool = soundPool ?: return
            val sampleId = sampleIds[cue] ?: return
            val ready = synchronized(loadedSampleIds) { loadedSampleIds.contains(sampleId) }
            if (ready) {
                val vol = (cue.baseGain * volume).coerceIn(0.05f, 1.0f)
                pool.play(sampleId, vol, vol, 1, 0, 1.0f)
            }
        }
    }

    private fun triggerSubtleHaptic() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vib = vm?.defaultVibrator
                if (vib?.hasVibrator() == true) {
                    vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                }
            } else {
                @Suppress("DEPRECATION")
                val vib = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (vib?.hasVibrator() == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(12L, 60))
                }
            }
        }
    }

    fun release() {
        isReleased = true
        scope.cancel()
        runCatching {
            soundPool?.release()
        }
        soundPool = null
        sampleIds.clear()
        synchronized(loadedSampleIds) {
            loadedSampleIds.clear()
        }
    }

    @Suppress("DiscouragedApi")
    private fun resolveRawResourceId(rawName: String): Int {
        return runCatching {
            appContext.resources.getIdentifier(rawName, "raw", appContext.packageName)
        }.getOrDefault(0)
    }

    private fun ensureFallbackWavFile(cue: LifeOsSoundCue): File? {
        return runCatching {
            val dir = File(appContext.cacheDir, "lifeos_sfx")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "${cue.rawResourceName}_v3.wav")
            if (!file.exists() || file.length() < 44L) {
                val bytes = buildWavForCue(cue)
                FileOutputStream(file).use { it.write(bytes) }
            }
            file
        }.getOrNull()
    }

    companion object {
        private const val SAMPLE_RATE = 22050

        fun shouldPlayForPrefs(cue: LifeOsSoundCue, prefs: UserPreferencesState): Boolean {
            if (!prefs.soundEffectsEnabled) return false
            if (cue == LifeOsSoundCue.APP_OPEN_LOGO) {
                return prefs.splashAudioLogoEnabled && prefs.soundNavEnabled
            }
            return when (cue.category) {
                SoundCategory.NAVIGATION -> prefs.soundNavEnabled
                SoundCategory.BUTTON_INTERACTION -> prefs.soundInteractionEnabled
                SoundCategory.TASK_COMPLETION -> prefs.soundTaskEnabled
                SoundCategory.ACHIEVEMENT -> prefs.soundAchievementEnabled
                SoundCategory.STREAK_COMPLETION -> prefs.soundStreakEnabled
                SoundCategory.SURU_INTERACTION -> prefs.soundSuruEnabled
                SoundCategory.KUTTA_INTERACTION -> prefs.soundCompanionEnabled
                SoundCategory.TOGETHER_MESSAGES -> prefs.soundTogetherEnabled
                SoundCategory.REMINDERS -> prefs.soundReminderEnabled
                SoundCategory.NOTIFICATIONS -> prefs.soundNotificationEnabled
            }
        }

        fun buildWavForCue(cue: LifeOsSoundCue): ByteArray {
            val tones = when (cue) {
                LifeOsSoundCue.NAV_SWITCH -> listOf(
                    ToneNote(freqHz = 440.0, durationMs = 36, gain = 0.14)
                )
                LifeOsSoundCue.UI_TAP -> listOf(
                    ToneNote(freqHz = 523.25, durationMs = 30, gain = 0.14)
                )
                LifeOsSoundCue.UI_TOGGLE -> listOf(
                    ToneNote(freqHz = 587.33, durationMs = 42, gain = 0.18),
                    ToneNote(freqHz = 783.99, durationMs = 52, gain = 0.16)
                )
                LifeOsSoundCue.UI_CONFIRM -> listOf(
                    ToneNote(freqHz = 523.25, durationMs = 55, gain = 0.22),
                    ToneNote(freqHz = 659.25, durationMs = 70, gain = 0.24),
                    ToneNote(freqHz = 783.99, durationMs = 110, gain = 0.22)
                )
                LifeOsSoundCue.UI_UNDO -> listOf(
                    ToneNote(freqHz = 659.25, durationMs = 55, gain = 0.20),
                    ToneNote(freqHz = 493.88, durationMs = 95, gain = 0.20)
                )
                LifeOsSoundCue.TASK_CREATE -> listOf(
                    ToneNote(freqHz = 587.33, durationMs = 55, gain = 0.22),
                    ToneNote(freqHz = 880.00, durationMs = 95, gain = 0.22)
                )
                LifeOsSoundCue.TASK_COMPLETE -> listOf(
                    ToneNote(freqHz = 659.25, durationMs = 70, gain = 0.28),
                    ToneNote(freqHz = 987.77, durationMs = 130, gain = 0.26)
                )
                LifeOsSoundCue.TASK_RESCHEDULE -> listOf(
                    ToneNote(freqHz = 523.25, durationMs = 60, gain = 0.20),
                    ToneNote(freqHz = 659.25, durationMs = 100, gain = 0.22)
                )
                LifeOsSoundCue.CALENDAR_CREATE -> listOf(
                    ToneNote(freqHz = 493.88, durationMs = 65, gain = 0.22),
                    ToneNote(freqHz = 739.99, durationMs = 115, gain = 0.24)
                )
                LifeOsSoundCue.SCHEDULE_SAVED -> listOf(
                    ToneNote(freqHz = 523.25, durationMs = 65, gain = 0.24),
                    ToneNote(freqHz = 659.25, durationMs = 75, gain = 0.24),
                    ToneNote(freqHz = 987.77, durationMs = 140, gain = 0.26)
                )
                LifeOsSoundCue.SURU_ACTIVATE -> listOf(
                    ToneNote(freqHz = 587.33, durationMs = 60, gain = 0.22),
                    ToneNote(freqHz = 880.00, durationMs = 110, gain = 0.24)
                )
                LifeOsSoundCue.SURU_LISTENING -> listOf(
                    ToneNote(freqHz = 659.25, durationMs = 75, gain = 0.22)
                )
                LifeOsSoundCue.SURU_SUCCESS -> listOf(
                    ToneNote(freqHz = 587.33, durationMs = 60, gain = 0.24),
                    ToneNote(freqHz = 783.99, durationMs = 70, gain = 0.26),
                    ToneNote(freqHz = 1046.50, durationMs = 140, gain = 0.26)
                )
                LifeOsSoundCue.SURU_ERROR -> listOf(
                    ToneNote(freqHz = 392.00, durationMs = 80, gain = 0.22),
                    ToneNote(freqHz = 329.63, durationMs = 120, gain = 0.20)
                )
                LifeOsSoundCue.SURU_PLAN_COMPLETE -> listOf(
                    ToneNote(freqHz = 523.25, durationMs = 70, gain = 0.26),
                    ToneNote(freqHz = 659.25, durationMs = 70, gain = 0.26),
                    ToneNote(freqHz = 783.99, durationMs = 80, gain = 0.28),
                    ToneNote(freqHz = 1046.50, durationMs = 180, gain = 0.30)
                )
                LifeOsSoundCue.STREAK_ACTIVATE -> listOf(
                    ToneNote(freqHz = 523.25, durationMs = 65, gain = 0.28),
                    ToneNote(freqHz = 659.25, durationMs = 65, gain = 0.30),
                    ToneNote(freqHz = 1046.50, durationMs = 160, gain = 0.32)
                )
                LifeOsSoundCue.LEVEL_UP,
                LifeOsSoundCue.ACHIEVEMENT_UNLOCK -> listOf(
                    ToneNote(freqHz = 523.25, durationMs = 75, gain = 0.28),
                    ToneNote(freqHz = 659.25, durationMs = 75, gain = 0.30),
                    ToneNote(freqHz = 783.99, durationMs = 80, gain = 0.32),
                    ToneNote(freqHz = 1046.50, durationMs = 210, gain = 0.35)
                )
                LifeOsSoundCue.FOCUS_COMPLETE -> listOf(
                    ToneNote(freqHz = 587.33, durationMs = 110, gain = 0.28),
                    ToneNote(freqHz = 880.00, durationMs = 180, gain = 0.30)
                )
                LifeOsSoundCue.COMPANION_INTERACT -> listOf(
                    ToneNote(freqHz = 659.25, durationMs = 55, gain = 0.22),
                    ToneNote(freqHz = 783.99, durationMs = 95, gain = 0.22)
                )
                LifeOsSoundCue.TOGETHER_MESSAGE -> listOf(
                    ToneNote(freqHz = 659.25, durationMs = 50, gain = 0.20),
                    ToneNote(freqHz = 987.77, durationMs = 85, gain = 0.22)
                )
                LifeOsSoundCue.NOTIFICATION_PING -> listOf(
                    ToneNote(freqHz = 587.33, durationMs = 55, gain = 0.20),
                    ToneNote(freqHz = 880.00, durationMs = 110, gain = 0.22)
                )
                LifeOsSoundCue.APP_OPEN_LOGO -> listOf(
                    ToneNote(freqHz = 523.25, durationMs = 80, gain = 0.16),
                    ToneNote(freqHz = 659.25, durationMs = 95, gain = 0.18),
                    ToneNote(freqHz = 783.99, durationMs = 170, gain = 0.20)
                )
                LifeOsSoundCue.STUDY_BREAK -> listOf(
                    ToneNote(freqHz = 523.25, durationMs = 95, gain = 0.20),
                    ToneNote(freqHz = 659.25, durationMs = 150, gain = 0.22)
                )
                LifeOsSoundCue.CHAPTER_DONE -> listOf(
                    ToneNote(freqHz = 587.33, durationMs = 65, gain = 0.22),
                    ToneNote(freqHz = 783.99, durationMs = 75, gain = 0.24),
                    ToneNote(freqHz = 1046.50, durationMs = 150, gain = 0.26)
                )
            }
            return buildToneWavBytes(tones)
        }

        data class ToneNote(val freqHz: Double, val durationMs: Int, val gain: Double)

        fun buildToneWavBytes(notes: List<ToneNote>): ByteArray {
            val totalSamples = notes.sumOf { (SAMPLE_RATE * it.durationMs) / 1000 }
            val pcmData = ByteArray(totalSamples * 2)
            var sampleOffset = 0

            notes.forEach { note ->
                val count = (SAMPLE_RATE * note.durationMs) / 1000
                val attackSamples = (count * 0.16).coerceAtLeast(1.0)
                for (i in 0 until count) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val attack = (i / attackSamples).coerceAtMost(1.0)
                    val decay = exp(-4.5 * (i.toDouble() / count.coerceAtLeast(1)))
                    val fundamental = sin(2.0 * PI * note.freqHz * t)
                    val warmOctave = 0.18 * sin(2.0 * PI * (note.freqHz * 2.0) * t)
                    val value = ((fundamental + warmOctave) * attack * decay * note.gain).coerceIn(-0.98, 0.98)
                    val pcm16 = (value * Short.MAX_VALUE).toInt().toShort()
                    val byteIdx = (sampleOffset + i) * 2
                    pcmData[byteIdx] = (pcm16.toInt() and 0xFF).toByte()
                    pcmData[byteIdx + 1] = ((pcm16.toInt() ushr 8) and 0xFF).toByte()
                }
                sampleOffset += count
            }

            val dataSize = pcmData.size
            val chunkSize = 36 + dataSize
            val wav = ByteArray(44 + dataSize)

            wav[0] = 'R'.code.toByte(); wav[1] = 'I'.code.toByte()
            wav[2] = 'F'.code.toByte(); wav[3] = 'F'.code.toByte()
            writeIntLe(wav, 4, chunkSize)
            wav[8] = 'W'.code.toByte(); wav[9] = 'A'.code.toByte()
            wav[10] = 'V'.code.toByte(); wav[11] = 'E'.code.toByte()

            wav[12] = 'f'.code.toByte(); wav[13] = 'm'.code.toByte()
            wav[14] = 't'.code.toByte(); wav[15] = ' '.code.toByte()
            writeIntLe(wav, 16, 16)
            writeShortLe(wav, 20, 1)
            writeShortLe(wav, 22, 1)
            writeIntLe(wav, 24, SAMPLE_RATE)
            writeIntLe(wav, 28, SAMPLE_RATE * 2)
            writeShortLe(wav, 32, 2)
            writeShortLe(wav, 34, 16)

            wav[36] = 'd'.code.toByte(); wav[37] = 'a'.code.toByte()
            wav[38] = 't'.code.toByte(); wav[39] = 'a'.code.toByte()
            writeIntLe(wav, 40, dataSize)
            System.arraycopy(pcmData, 0, wav, 44, dataSize)
            return wav
        }

        private fun writeIntLe(buf: ByteArray, offset: Int, value: Int) {
            buf[offset] = (value and 0xFF).toByte()
            buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
            buf[offset + 2] = ((value ushr 16) and 0xFF).toByte()
            buf[offset + 3] = ((value ushr 24) and 0xFF).toByte()
        }

        private fun writeShortLe(buf: ByteArray, offset: Int, value: Int) {
            buf[offset] = (value and 0xFF).toByte()
            buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        }
    }
}
