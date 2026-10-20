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

    fun playBowPull() {
        try {
            soundEffectPlayer.playBowstringTension()
        } catch (_: Exception) {
            soundManager.play("tap")
        }
    }

    fun playShoot() {
        try {
            soundEffectPlayer.playBowShot()
        } catch (_: Exception) {
            soundManager.play("whoosh")
        }
    }

    fun playHit(type: ArrowType) {
        try {
            when (type) {
                ArrowType.NORMAL -> {
                    soundEffectPlayer.playDullHeavyArrowImpact("WOODEN")
                    soundEffectPlayer.playArrowVibrationBuzz()
                }
                ArrowType.CARROT -> {
                    soundEffectPlayer.playDullHeavyArrowImpact("CARROT")
                }
                ArrowType.STAR -> {
                    soundEffectPlayer.playDullHeavyArrowImpact("STAR")
                    soundEffectPlayer.playMagicSparkle()
                }
            }
        } catch (_: Exception) {
            soundManager.play("pop")
        }
    }

    fun playScarecrowGiggle() {
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
        try {
            soundEffectPlayer.playScarecrowRecoilSpring(1.0f)
        } catch (_: Exception) {
            soundManager.play("boing")
        }
    }

    fun playThud() {
        try {
            soundEffectPlayer.playDullHeavyArrowImpact("WOODEN", isBullseye = false)
        } catch (_: Exception) {
            soundManager.play("tap")
        }
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
