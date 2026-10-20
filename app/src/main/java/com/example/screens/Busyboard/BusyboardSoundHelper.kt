package com.example.screens.Busyboard

import android.content.Context
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.audio.SoundEffectPlayer
import com.example.shared.SoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 🎛️ Sound Helper for Step 8: BusyBoard.
 */
class BusyboardSoundHelper(
    private val context: Context,
    private val soundEffectPlayer: SoundEffectPlayer,
    private val soundManager: SoundManager
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun playLeverClack() {
        try {
            soundEffectPlayer.playLeverClunk()
        } catch (_: Exception) {
            soundManager.play("pop")
        }
    }

    fun playLeverHum() {
        // Soft click pulse
        try {
            soundEffectPlayer.playBubblePop()
        } catch (_: Exception) {}
    }

    fun playConfettiRain() {
        try {
            soundEffectPlayer.playMagicSparkle()
        } catch (_: Exception) {
            soundManager.play("chime")
        }
    }

    fun playTrainToot() {
        scope.launch {
            try {
                // Harmonic dual-tone train horn: 440Hz + 554Hz
                playHarmonicHorn(freq1 = 440f, freq2 = 554f, durationMs = 450)
            } catch (_: Exception) {
                soundManager.play("chime")
            }
        }
    }

    fun playStarBurst() {
        try {
            soundEffectPlayer.playChime(1.4f)
        } catch (_: Exception) {
            soundManager.play("chime")
        }
    }

    fun playQuack() {
        scope.launch {
            try {
                // Cartoon quack (descending nasal pulse)
                val base = 320f + Random.nextInt(3) * 35f
                playQuackTone(baseFreq = base, durationMs = 280)
            } catch (_: Exception) {
                soundManager.play("pop")
            }
        }
    }

    fun playFrogBoing() {
        try {
            soundEffectPlayer.playBoing()
        } catch (_: Exception) {
            soundManager.play("boing")
        }
    }

    fun playFirework() {
        try {
            soundEffectPlayer.playChime(1.1f)
            soundManager.play("success")
        } catch (_: Exception) {
            soundManager.play("pop")
        }
    }

    fun playSwitchOn() {
        try {
            soundEffectPlayer.playLeverClunk()
        } catch (_: Exception) {
            soundManager.play("tap")
        }
    }

    fun playSwitchOff() {
        try {
            soundEffectPlayer.playBubblePop()
        } catch (_: Exception) {
            soundManager.play("tap")
        }
    }

    fun playJingleLights() {
        scope.launch {
            try {
                // Joyful 4-note ascending chime
                val melody = floatArrayOf(523f, 659f, 784f, 1046f)
                playMelodySequence(melody, noteDurationMs = 120)
            } catch (_: Exception) {
                soundManager.play("chime")
            }
        }
    }

    fun playKnobTick() {
        try {
            soundEffectPlayer.playBubblePop()
        } catch (_: Exception) {
            soundManager.play("tap")
        }
    }

    fun playDoorCreak() {
        try {
            soundEffectPlayer.playSqueak()
        } catch (_: Exception) {
            soundManager.play("tap")
        }
    }

    fun playDoorClose() {
        try {
            soundEffectPlayer.playLeverClunk()
        } catch (_: Exception) {
            soundManager.play("tap")
        }
    }

    fun playPeekabooVoice(animalName: String) {
        val played = soundManager.play("peekaboo")
        scope.launch {
            if (!played) {
                try {
                    // Warm sing-song "Ku-ku!" tone (G4 -> E4)
                    playMelodySequence(floatArrayOf(784f, 659f), noteDurationMs = 180)
                } catch (_: Exception) {}
            }
            delay(400)
            soundManager.play("animal_$animalName")
        }
    }

    fun playBellDing() {
        try {
            val played = soundManager.play("bell_ding")
            if (!played) soundEffectPlayer.playBicycleBell()
        } catch (_: Exception) {
            soundManager.play("chime")
        }
    }

    fun playWhooshSoft() {
        soundManager.play("whoosh_soft")
    }

    fun playPraise() {
        val played = soundManager.play("praise")
        if (!played) {
            try {
                soundEffectPlayer.playChime(1.5f)
                soundManager.play("chime")
            } catch (_: Exception) {
                soundManager.play("chime")
            }
        }
    }

    fun playPop() {
        soundManager.play("pop")
    }

    private fun playHarmonicHorn(freq1: Float, freq2: Float, durationMs: Int) {
        val sampleRate = 22050
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / numSamples
            val env = sin(progress * PI.toFloat()).coerceAtLeast(0f)
            val sample = (sin(2.0 * PI * freq1 * t) * 0.5f + sin(2.0 * PI * freq2 * t) * 0.5f) * env * 22000f
            buffer[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcm(buffer, sampleRate, durationMs)
    }

    private fun playQuackTone(baseFreq: Float, durationMs: Int) {
        val sampleRate = 22050
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / numSamples
            val freq = baseFreq - progress * 90f
            val env = sin(progress * PI.toFloat()).coerceAtLeast(0f)
            val wave = sin(2.0 * PI * freq * t).toFloat() + sin(2.0 * PI * freq * 2.8 * t).toFloat() * 0.35f
            val sample = wave * env * 20000f
            buffer[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcm(buffer, sampleRate, durationMs)
    }

    private fun playMelodySequence(notes: FloatArray, noteDurationMs: Int) {
        val sampleRate = 22050
        val totalMs = notes.size * noteDurationMs
        val numSamples = (totalMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)
        val samplesPerNote = numSamples / notes.size

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val noteIdx = (i / samplesPerNote).coerceIn(0, notes.size - 1)
            val noteProg = (i % samplesPerNote).toFloat() / samplesPerNote
            val env = sin(noteProg * PI.toFloat()).coerceAtLeast(0f)
            val freq = notes[noteIdx]
            val sample = sin(2.0 * PI * freq * t) * env * 22000f
            buffer[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcm(buffer, sampleRate, totalMs)
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
