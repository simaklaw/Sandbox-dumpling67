package com.example.screens.Archery

import android.content.Context
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.audio.SoundEffectPlayer
import com.example.shared.SoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * 🎵 Specialized Audio Helper for Step 5: Archery & Scarecrow.
 * Bridges SoundManager and procedural synthesis from SoundEffectPlayer.
 */
class ArcherySoundHelper(
    private val context: Context,
    private val soundEffectPlayer: SoundEffectPlayer,
    private val soundManager: SoundManager
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun playPop() {
        soundManager.play("pop")
    }

    /**
     * Voice stub for arrow selection: «Стрела», «Морковка», «Звёздочка».
     * Generates a warm, cheerful cartoon vowel tone while voice assets are not yet bundled.
     */
    fun playArrowVoice(type: ArrowType) {
        val soundKey = when (type) {
            ArrowType.NORMAL -> "voice_arrow_normal"
            ArrowType.CARROT -> "voice_arrow_carrot"
            ArrowType.STAR   -> "voice_arrow_star"
        }
        val played = soundManager.play(soundKey)
        if (!played) {
            scope.launch {
                try {
                    when (type) {
                        ArrowType.NORMAL -> playGentleTone(baseFreq = 340f, durationMs = 280, melody = floatArrayOf(340f, 420f))
                        ArrowType.CARROT -> playGentleTone(baseFreq = 400f, durationMs = 320, melody = floatArrayOf(400f, 480f, 520f))
                        ArrowType.STAR   -> playGentleTone(baseFreq = 520f, durationMs = 300, melody = floatArrayOf(520f, 650f, 780f))
                    }
                } catch (e: Exception) {
                    Log.w("ArcherySoundHelper", "Voice stub playback skipped", e)
                }
            }
        }
    }

    private var lastDraw = 0L
    private var lastShot = 0L
    private var lastHit = 0L
    private var lastWobble = 0L
    private var lastGiggle = 0L

    init {
        try {
            soundManager.preloadRaw(listOf("arch_draw", "arch_release", "arch_hit_straw", "arch_hit_carrot", "arch_wobble"))
        } catch (_: Exception) {}
    }

    // Все звуки — короткие готовые ogg через SoundPool: стартуют мгновенно и не копятся в очереди.
    fun playBowPull() {
        val now = System.currentTimeMillis()
        if (now - lastDraw < 150) return
        lastDraw = now
        soundManager.play("arch_draw", volume = 0.6f)
    }

    fun playShoot() {
        val now = System.currentTimeMillis()
        if (now - lastShot < 90) return
        lastShot = now
        soundManager.play("arch_release", volume = 1f, rate = 0.97f + kotlin.random.Random.nextFloat() * 0.07f)
    }

    fun playHit(type: ArrowType) {
        val now = System.currentTimeMillis()
        if (now - lastHit < 60) return
        lastHit = now
        when (type) {
            ArrowType.NORMAL -> soundManager.play("arch_hit_straw", volume = 1f, rate = 0.95f + kotlin.random.Random.nextFloat() * 0.1f)
            ArrowType.CARROT -> soundManager.play("arch_hit_carrot", volume = 1f, rate = 0.95f + kotlin.random.Random.nextFloat() * 0.1f)
            ArrowType.STAR -> {
                soundManager.play("arch_hit_straw", volume = 0.8f, rate = 1.25f)
                try { soundEffectPlayer.playMagicSparkle() } catch (_: Exception) {}
            }
        }
    }

    fun playScarecrowGiggle() {
        val nowG = System.currentTimeMillis()
        if (nowG - lastGiggle < 700) return
        lastGiggle = nowG
        scope.launch {
            try {
                // Friendly high-pitched giggling chirp
                playGentleTone(baseFreq = 580f, durationMs = 320, melody = floatArrayOf(580f, 640f, 720f, 640f, 700f))
            } catch (_: Exception) {
                soundManager.play("boing")
            }
        }
    }

    fun playWobble() {
        val now = System.currentTimeMillis()
        if (now - lastWobble < 200) return
        lastWobble = now
        soundManager.play("arch_wobble", volume = 0.7f)
    }

    fun playThud() {
        playHit(ArrowType.NORMAL)
    }

    fun playWhoosh() {
        soundManager.play("whoosh")
    }

    fun playFanfare() {
        try {
            soundEffectPlayer.playChime(1.2f)
            soundManager.play("success")
        } catch (_: Exception) {
            soundManager.play("success")
        }
    }

    private fun playGentleTone(baseFreq: Float, durationMs: Int, melody: FloatArray) {
        val sampleRate = 22050
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)
        val noteCount = melody.size
        val samplesPerNote = numSamples / noteCount

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / numSamples
            val noteIndex = (i / samplesPerNote).coerceIn(0, noteCount - 1)
            val freq = melody[noteIndex]
            val env = sin(progress * PI.toFloat()).coerceAtLeast(0f)

            // Warm rounded flute/bell overtone
            val sampleVal = (sin(2.0 * PI * freq * t) * 0.75f + sin(4.0 * PI * freq * t) * 0.25f) * env * 22000f
            buffer[i] = sampleVal.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_GAME)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, buffer.size)
            track.play()
            scope.launch {
                kotlinx.coroutines.delay(durationMs + 60L)
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
}
