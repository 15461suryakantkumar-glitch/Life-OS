package com.example.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
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

/**
 * Self-contained gentle ambient chime player for the Feelora Opening Screen.
 * Uses Android SoundPool with synthesized warm lavender-bell WAV chords and
 * fails silently when muted, on silent ringer, or if audio hardware is unavailable.
 */
class FeeloraOpeningChimePlayer(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var soundPool: SoundPool? = null
    private var launchSampleId: Int = 0
    private var tapSampleId: Int = 0
    private val loadedIds = HashSet<Int>()

    @Volatile
    private var pendingPlayOnLoad = false

    @Volatile
    private var isReleased = false

    @Volatile
    var isMuted: Boolean = false

    fun prepare(autoPlayWhenReady: Boolean = false) {
        if (isReleased) return
        if (autoPlayWhenReady && !isMuted) {
            pendingPlayOnLoad = true
        }
        if (soundPool != null) {
            if (autoPlayWhenReady && !isMuted) {
                playLaunchChime()
            }
            return
        }
        scope.launch {
            runCatching {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val pool = SoundPool.Builder()
                    .setMaxStreams(3)
                    .setAudioAttributes(attrs)
                    .build()
                pool.setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0 && !isReleased) {
                        synchronized(loadedIds) { loadedIds.add(sampleId) }
                        if (sampleId == launchSampleId && pendingPlayOnLoad && !isMuted) {
                            pendingPlayOnLoad = false
                            playLoadedSample(pool, sampleId, gain = 0.42f)
                        }
                    }
                }
                soundPool = pool

                val launchFile = ensureWavFile(
                    fileName = "feelora_opening_chime_v1.wav",
                    bytes = buildAmbientChimeWav()
                )
                val tapFile = ensureWavFile(
                    fileName = "feelora_sparkle_tap_v1.wav",
                    bytes = buildSparkleTapWav()
                )
                if (launchFile != null && launchFile.exists()) {
                    launchSampleId = pool.load(launchFile.absolutePath, 1)
                }
                if (tapFile != null && tapFile.exists()) {
                    tapSampleId = pool.load(tapFile.absolutePath, 1)
                }
            }
        }
    }

    fun playLaunchChime() {
        if (isMuted || isReleased) return
        val pool = soundPool ?: return
        if (launchSampleId != 0 && isSampleLoaded(launchSampleId)) {
            pendingPlayOnLoad = false
            playLoadedSample(pool, launchSampleId, gain = 0.42f)
        } else {
            pendingPlayOnLoad = true
        }
    }

    fun playTapSparkle() {
        if (isMuted || isReleased) return
        val pool = soundPool ?: return
        val targetId = when {
            tapSampleId != 0 && isSampleLoaded(tapSampleId) -> tapSampleId
            launchSampleId != 0 && isSampleLoaded(launchSampleId) -> launchSampleId
            else -> 0
        }
        if (targetId != 0) {
            playLoadedSample(pool, targetId, gain = 0.34f)
        }
    }

    fun release() {
        isReleased = true
        pendingPlayOnLoad = false
        scope.cancel()
        runCatching { soundPool?.release() }
        soundPool = null
        synchronized(loadedIds) { loadedIds.clear() }
    }

    private fun isSampleLoaded(sampleId: Int): Boolean =
        synchronized(loadedIds) { loadedIds.contains(sampleId) }

    private fun playLoadedSample(pool: SoundPool, sampleId: Int, gain: Float) {
        runCatching {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null && audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                return
            }
            pool.play(sampleId, gain, gain, 1, 0, 1.0f)
        }
    }

    private fun ensureWavFile(fileName: String, bytes: ByteArray): File? {
        return runCatching {
            val dir = File(appContext.cacheDir, "lifeos_sfx")
            if (!dir.exists()) dir.mkdirs()
            val out = File(dir, fileName)
            if (!out.exists() || out.length() < 44L) {
                FileOutputStream(out).use { it.write(bytes) }
            }
            out
        }.getOrNull()
    }

    companion object {
        fun buildAmbientChimeWav(): ByteArray {
            // Soft lavender harp/celesta arpeggio: C5 -> E5 -> G5 -> B5 -> D6
            val notes = listOf(
                LifeOsSoundManager.Companion.ToneNote(freqHz = 523.25, durationMs = 130, gain = 0.16),
                LifeOsSoundManager.Companion.ToneNote(freqHz = 659.25, durationMs = 140, gain = 0.18),
                LifeOsSoundManager.Companion.ToneNote(freqHz = 783.99, durationMs = 150, gain = 0.20),
                LifeOsSoundManager.Companion.ToneNote(freqHz = 987.77, durationMs = 170, gain = 0.19),
                LifeOsSoundManager.Companion.ToneNote(freqHz = 1174.66, durationMs = 340, gain = 0.21)
            )
            return LifeOsSoundManager.buildToneWavBytes(notes)
        }

        fun buildSparkleTapWav(): ByteArray {
            val notes = listOf(
                LifeOsSoundManager.Companion.ToneNote(freqHz = 783.99, durationMs = 70, gain = 0.16),
                LifeOsSoundManager.Companion.ToneNote(freqHz = 1046.50, durationMs = 140, gain = 0.18)
            )
            return LifeOsSoundManager.buildToneWavBytes(notes)
        }
    }
}
