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

enum class LifeOsSoundCue(val rawResourceName: String) {
    TASK_COMPLETE("sfx_task_complete"),
    STREAK_ACTIVATE("sfx_streak_activate"),
    LEVEL_UP("sfx_level_up"),
    FOCUS_COMPLETE("sfx_focus_complete")
}

/**
 * Lightweight, 100% offline SoundPool helper for LIFE OS tactile audio feedback.
 *
 * - Prefers local res/raw resources (`sfx_task_complete`, `sfx_streak_activate`,
 *   `sfx_level_up`, `sfx_focus_complete`) if provided.
 * - Falls back safely to cached procedural PCM .wav chimes in `cacheDir/lifeos_sfx`
 *   when raw audio files are not bundled, and no-ops gracefully if muted or on
 *   headless/test environments.
 */
class LifeOsSoundManager(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var soundPool: SoundPool? = null
    private val sampleIds = LinkedHashMap<LifeOsSoundCue, Int>()
    private val loadedSampleIds = HashSet<Int>()
    @Volatile
    private var isInitialized = false
    @Volatile
    private var isReleased = false

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
                    .setMaxStreams(3)
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

    fun playTaskComplete(enabled: Boolean) = playCue(LifeOsSoundCue.TASK_COMPLETE, enabled, volume = 0.55f)

    fun playStreakActivate(enabled: Boolean) = playCue(LifeOsSoundCue.STREAK_ACTIVATE, enabled, volume = 0.65f)

    fun playLevelUp(enabled: Boolean) = playCue(LifeOsSoundCue.LEVEL_UP, enabled, volume = 0.72f)

    fun playFocusComplete(enabled: Boolean) = playCue(LifeOsSoundCue.FOCUS_COMPLETE, enabled, volume = 0.65f)

    private fun playCue(cue: LifeOsSoundCue, enabled: Boolean, volume: Float) {
        if (!enabled || isReleased) return
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
                pool.play(sampleId, volume, volume, 1, 0, 1.0f)
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
            val file = File(dir, "${cue.rawResourceName}_v1.wav")
            if (!file.exists() || file.length() < 44L) {
                val bytes = buildWavForCue(cue)
                FileOutputStream(file).use { it.write(bytes) }
            }
            file
        }.getOrNull()
    }

    companion object {
        private const val SAMPLE_RATE = 22050

        fun buildWavForCue(cue: LifeOsSoundCue): ByteArray {
            val tones = when (cue) {
                LifeOsSoundCue.TASK_COMPLETE -> listOf(
                    ToneNote(freqHz = 659.25, durationMs = 70, gain = 0.32),
                    ToneNote(freqHz = 987.77, durationMs = 130, gain = 0.28)
                )
                LifeOsSoundCue.STREAK_ACTIVATE -> listOf(
                    ToneNote(freqHz = 523.25, durationMs = 65, gain = 0.30),
                    ToneNote(freqHz = 659.25, durationMs = 65, gain = 0.32),
                    ToneNote(freqHz = 1046.50, durationMs = 160, gain = 0.34)
                )
                LifeOsSoundCue.LEVEL_UP -> listOf(
                    ToneNote(freqHz = 523.25, durationMs = 75, gain = 0.30),
                    ToneNote(freqHz = 659.25, durationMs = 75, gain = 0.32),
                    ToneNote(freqHz = 783.99, durationMs = 80, gain = 0.34),
                    ToneNote(freqHz = 1046.50, durationMs = 210, gain = 0.38)
                )
                LifeOsSoundCue.FOCUS_COMPLETE -> listOf(
                    ToneNote(freqHz = 587.33, durationMs = 110, gain = 0.30),
                    ToneNote(freqHz = 880.00, durationMs = 180, gain = 0.32)
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
                val attackSamples = (count * 0.12).coerceAtLeast(1.0)
                for (i in 0 until count) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val attack = (i / attackSamples).coerceAtMost(1.0)
                    val decay = exp(-4.2 * (i.toDouble() / count.coerceAtLeast(1)))
                    val fundamental = sin(2.0 * PI * note.freqHz * t)
                    val shimmer = 0.22 * sin(2.0 * PI * (note.freqHz * 2.0) * t)
                    val value = ((fundamental + shimmer) * attack * decay * note.gain).coerceIn(-0.98, 0.98)
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

            // RIFF header
            wav[0] = 'R'.code.toByte(); wav[1] = 'I'.code.toByte()
            wav[2] = 'F'.code.toByte(); wav[3] = 'F'.code.toByte()
            writeIntLe(wav, 4, chunkSize)
            wav[8] = 'W'.code.toByte(); wav[9] = 'A'.code.toByte()
            wav[10] = 'V'.code.toByte(); wav[11] = 'E'.code.toByte()

            // fmt subchunk
            wav[12] = 'f'.code.toByte(); wav[13] = 'm'.code.toByte()
            wav[14] = 't'.code.toByte(); wav[15] = ' '.code.toByte()
            writeIntLe(wav, 16, 16) // PCM subchunk size
            writeShortLe(wav, 20, 1) // AudioFormat = PCM (1)
            writeShortLe(wav, 22, 1) // Mono
            writeIntLe(wav, 24, SAMPLE_RATE)
            writeIntLe(wav, 28, SAMPLE_RATE * 2) // ByteRate
            writeShortLe(wav, 32, 2) // BlockAlign
            writeShortLe(wav, 34, 16) // BitsPerSample

            // data subchunk
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
