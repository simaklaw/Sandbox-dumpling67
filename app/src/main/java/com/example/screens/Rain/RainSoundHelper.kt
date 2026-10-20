package com.example.screens.Rain

import android.content.Context
import com.example.audio.SoundEffectPlayer
import com.example.shared.SoundManager
import kotlin.random.Random

/**
 * 🎵 Audio helper for Step 14: Rain Game ("Дождик").
 */
class RainSoundHelper(
    private val context: Context,
    private val soundEffectPlayer: SoundEffectPlayer,
    private val soundManager: SoundManager
) {
    private var lastDropTimestamp: Long = 0L
    private val minDropIntervalMs = 80L // max 12 drops/sec

    fun playDropPlip() {
        val now = System.currentTimeMillis()
        if (now - lastDropTimestamp < minDropIntervalMs) return
        lastDropTimestamp = now

        val pitchVariation = 0.95f + Random.nextFloat() * 0.10f
        val played = soundManager.play("drop_plip", volume = 0.8f, rate = pitchVariation)
        if (!played) soundEffectPlayer.playPop(pitchVariation)
    }

    fun playSplashSoft() {
        val played = soundManager.play("splash_soft")
        if (!played) soundEffectPlayer.playSplash()
    }

    fun playRainbowChime() {
        val played = soundManager.play("rainbow_chime")
        if (!played) soundEffectPlayer.playChime(1.2f)
    }

    fun playFrog() {
        val played = soundManager.play("animal_frog")
        if (!played) soundEffectPlayer.playAnimalSound("frog")
    }

    fun playDuck() {
        val played = soundManager.play("animal_duck")
        if (!played) soundEffectPlayer.playAnimalSound("duck")
    }

    fun playButterfly() {
        val played = soundManager.play("decor_butterfly")
        if (!played) soundEffectPlayer.playChime()
    }

    fun playSun() {
        val played = soundManager.play("decor_sun")
        if (!played) soundEffectPlayer.playChime()
    }

    fun playNight() {
        val played = soundManager.play("decor_star")
        if (!played) soundEffectPlayer.playChime(0.8f)
    }
}
