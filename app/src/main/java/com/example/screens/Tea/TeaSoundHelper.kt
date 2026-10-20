package com.example.screens.Tea

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
 * 🍵 Sound Helper for Step 7A: Magic Tea Party.
 */
class TeaSoundHelper(
    private val context: Context,
    private val soundEffectPlayer: SoundEffectPlayer,
    private val soundManager: SoundManager
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun playSplashSmall() {
        try {
            soundEffectPlayer.playSplash()
        } catch (_: Exception) {
            soundManager.play("pop")
        }
    }

    fun playPop() {
        soundManager.play("pop")
    }

    fun playPickup() {
        try {
            soundEffectPlayer.playBubblePop()
        } catch (_: Exception) {
            soundManager.play("tap")
        }
    }

    fun playStir() {
        scope.launch {
            try {
                // Gentle swirling water slosh sound (sine water modulation)
                playSwirlTone(durationMs = 2400)
            } catch (_: Exception) {
                soundManager.play("whoosh")
            }
        }
    }

    fun playSteam() {
        scope.launch {
            try {
                // Soft airy hiss of hot aromatic steam
                playHissTone(durationMs = 1800)
            } catch (_: Exception) {
                soundManager.play("sparkle")
            }
        }
    }

    fun playChime() {
        try {
            soundEffectPlayer.playChime(1.3f)
        } catch (_: Exception) {
            soundManager.play("success")
        }
    }

    fun playGlug() {
        scope.launch {
            try {
                // Liquid drinking gulps (descending bubble gulps)
                playGulpsTone(durationMs = 1500)
            } catch (_: Exception) {
                soundManager.play("pop")
            }
        }
    }

    fun playYum() {
        try {
            soundEffectPlayer.playChime(1.5f)
            soundManager.play("magic")
        } catch (_: Exception) {
            soundManager.play("success")
        }
    }

    fun playClink() {
        try {
            soundEffectPlayer.playBicycleBell()
        } catch (_: Exception) {
            soundManager.play("tap")
        }
    }

    fun playGiggle() {
        scope.launch {
            try {
                playCuteGiggle()
            } catch (_: Exception) {
                soundManager.play("chime")
            }
        }
    }

    private fun playSwirlTone(durationMs: Int) {
        val sampleRate = 22050
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / numSamples
            val env = sin(progress * PI.toFloat()).coerceAtLeast(0f)
            val swirlFreq = 220f + sin(2.0 * PI * 4.0 * t).toFloat() * 60f
            val sample = (sin(2.0 * PI * swirlFreq * t) * env * 18000f).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcm(buffer, sampleRate, durationMs)
    }

    private fun playHissTone(durationMs: Int) {
        val sampleRate = 22050
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val env = sin(progress * PI.toFloat()).coerceAtLeast(0f)
            val noise = (Math.random() * 2.0 - 1.0).toFloat()
            val sample = (noise * env * 12000f).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcm(buffer, sampleRate, durationMs)
    }

    private fun playGulpsTone(durationMs: Int) {
        val sampleRate = 22050
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)
        val gulpsCount = 5
        val samplesPerGulp = numSamples / gulpsCount

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val gulpProg = (i % samplesPerGulp).toFloat() / samplesPerGulp
            val env = sin(gulpProg * PI.toFloat()).coerceAtLeast(0f)
            val freq = 340f - gulpProg * 140f
            val sample = (sin(2.0 * PI * freq * t) * env * 22000f).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcm(buffer, sampleRate, durationMs)
    }

    private fun playCuteGiggle() {
        val sampleRate = 22050
        val durationMs = 350
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)
        val freqs = floatArrayOf(620f, 700f, 780f, 700f, 760f)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val noteIdx = ((i.toFloat() / numSamples) * freqs.size).toInt().coerceIn(0, freqs.size - 1)
            val freq = freqs[noteIdx]
            val progress = i.toFloat() / numSamples
            val env = sin(progress * PI.toFloat()).coerceAtLeast(0f)
            val sample = (sin(2.0 * PI * freq * t) * env * 21000f).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcm(buffer, sampleRate, durationMs)
    }

    private fun playPcm(buffer: ShortArray, sampleRate: Int, durationMs: Int) {
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
