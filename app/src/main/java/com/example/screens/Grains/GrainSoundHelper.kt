package com.example.screens.Grains

import android.content.Context
import com.example.audio.SoundEffectPlayer
import com.example.shared.SoundManager

/**
 * 🌾 Sound Helper for Step 7B: Grain Sensory Sandbox.
 */
class GrainSoundHelper(
    private val context: Context,
    private val soundEffectPlayer: SoundEffectPlayer,
    private val soundManager: SoundManager
) {
    private var lastPourSoundTime: Long = 0L
    private var lastScoopSoundTime: Long = 0L

    fun playPour(grain: GrainKind, intensity: Float) {
        val now = System.currentTimeMillis()
        if (now - lastPourSoundTime < 160L) return
        lastPourSoundTime = now
        try {
            soundEffectPlayer.playGrainRustle(grain.soundProfile, intensity.coerceIn(0.4f, 1.3f))
        } catch (_: Exception) {
            soundManager.play("pop")
        }
    }

    fun playScoop(grain: GrainKind, speedIntensity: Float) {
        val now = System.currentTimeMillis()
        if (now - lastScoopSoundTime < 220L) return
        lastScoopSoundTime = now
        try {
            soundEffectPlayer.playGrainRustle(grain.soundProfile, (speedIntensity * 0.8f).coerceIn(0.5f, 1.4f))
        } catch (_: Exception) {
            soundManager.play("pop")
        }
    }

    fun playStamp() {
        try {
            soundEffectPlayer.playLeverClunk()
            soundManager.play("magic")
        } catch (_: Exception) {
            soundManager.play("pop")
        }
    }

    fun playWhoosh() {
        soundManager.play("whoosh")
    }

    fun playPop() {
        soundManager.play("pop")
    }

    fun playClink() {
        try {
            soundEffectPlayer.playBicycleBell()
        } catch (_: Exception) {
            soundManager.play("tap")
        }
    }
}
