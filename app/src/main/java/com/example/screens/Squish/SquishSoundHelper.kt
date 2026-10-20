package com.example.screens.Squish

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
import kotlin.random.Random

/**
 * 🎵 Audio helper for Step 6: Squishy ("Мялка").
 * Provides rate-limited playback (<= 6/sec) of procedural squish sounds.
 */
class SquishSoundHelper(
    private val context: Context,
    private val soundEffectPlayer: SoundEffectPlayer,
    private val soundManager: SoundManager
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var lastSoundTimestamp: Long = 0L
    private val minSoundIntervalMs = 150L // max ~6.6 sounds/sec

    private fun canPlaySound(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastSoundTimestamp < minSoundIntervalMs) return false
        lastSoundTimestamp = now
        return true
    }

    fun playSquish() {
        if (!canPlaySound()) return
        try {
            val variant = Random.nextInt(1, 4)
            val pitch = 0.85f + Random.nextFloat() * 0.35f
            soundEffectPlayer.playSquish(pitch)
        } catch (_: Exception) {
            soundManager.play("pop")
        }
    }

    fun playStretch() {
        if (!canPlaySound()) return
        try {
            soundEffectPlayer.playSquish(1.4f)
        } catch (_: Exception) {
            soundManager.play("pop")
        }
    }

    fun playReleaseBoing() {
        if (!canPlaySound()) return
        try {
            soundEffectPlayer.playBoing()
        } catch (_: Exception) {
            soundManager.play("boing")
        }
    }

    fun playFly() {
        if (!canPlaySound()) return
        scope.launch {
            try {
                // Cheerful ascending "wheee!" cartoon slide
                playMelodicSlide(startFreq = 420f, endFreq = 780f, durationMs = 380)
            } catch (_: Exception) {
                soundManager.play("chime")
            }
        }
    }

    fun playBounce() {
        if (!canPlaySound()) return
        try {
            soundEffectPlayer.playBoing()
        } catch (_: Exception) {
            soundManager.play("boing")
        }
    }

    fun playPoke() {
        if (!canPlaySound()) return
        try {
            soundEffectPlayer.playBubblePop()
        } catch (_: Exception) {
            soundManager.play("pop")
        }
    }

    fun playGiggle() {
        if (!canPlaySound()) return
        scope.launch {
            try {
                playMelodicGiggle()
            } catch (_: Exception) {
                soundManager.play("chime")
            }
        }
    }

    fun playDizzy() {
        if (!canPlaySound()) return
        scope.launch {
            try {
                playWobblySpiralTone()
            } catch (_: Exception) {
                soundManager.play("boing")
            }
        }
    }

    fun playYawn() {
        if (!canPlaySound()) return
        scope.launch {
            try {
                playMelodicSlide(startFreq = 360f, endFreq = 220f, durationMs = 450)
            } catch (_: Exception) {
                soundManager.play("pop")
            }
        }
    }

    fun playWear() {
        soundManager.play("magic")
    }

    fun playGift() {
        soundManager.play("success")
    }

    private fun playMelodicSlide(startFreq: Float, endFreq: Float, durationMs: Int) {
        val sampleRate = 22050
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / numSamples
            val freq = startFreq + (endFreq - startFreq) * progress
            val env = sin(progress * PI.toFloat()).coerceAtLeast(0f)
            val sample = (sin(2.0 * PI * freq * t) * env * 22000f).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcmTrack(buffer, sampleRate, durationMs)
    }

    private fun playMelodicGiggle() {
        val sampleRate = 22050
        val durationMs = 320
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)
        val freqs = floatArrayOf(580f, 660f, 740f, 660f, 720f)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / numSamples
            val noteIdx = ((i.toFloat() / numSamples) * freqs.size).toInt().coerceIn(0, freqs.size - 1)
            val freq = freqs[noteIdx]
            val env = sin(progress * PI.toFloat()).coerceAtLeast(0f)
            val sample = (sin(2.0 * PI * freq * t) * env * 20000f).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcmTrack(buffer, sampleRate, durationMs)
    }

    private fun playWobblySpiralTone() {
        val sampleRate = 22050
        val durationMs = 420
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / numSamples
            val wobble = sin(2.0 * PI * 16.0 * t).toFloat() * 60f
            val freq = 440f - progress * 120f + wobble
            val env = (1.0f - progress * 0.8f)
            val sample = (sin(2.0 * PI * freq * t) * env * 20000f).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcmTrack(buffer, sampleRate, durationMs)
    }

    private fun playPcmTrack(buffer: ShortArray, sampleRate: Int, durationMs: Int) {
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
