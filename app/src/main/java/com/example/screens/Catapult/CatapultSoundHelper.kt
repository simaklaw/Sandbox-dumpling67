package com.example.screens.Catapult

import android.content.Context
import com.example.audio.SoundEffectPlayer
import com.example.shared.SoundManager

/**
 * 🎵 Audio helper for Step 13: Catapult ("Запусти героя в небо").
 * Handles sound effects and loops with strict rate-limiting (<= 6 sounds/sec).
 */
class CatapultSoundHelper(
    private val context: Context,
    private val soundEffectPlayer: SoundEffectPlayer,
    private val soundManager: SoundManager
) {
    private var lastSoundTimestamp: Long = 0L
    private val minSoundIntervalMs = 150L // max ~6.6 sounds/sec

    private fun canPlaySound(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastSoundTimestamp < minSoundIntervalMs) return false
        lastSoundTimestamp = now
        return true
    }

    fun playSlingFire() {
        if (!canPlaySound()) return
        val played = soundManager.play("sling_fire")
        if (!played) soundEffectPlayer.playBoing()
    }

    fun playParachuteOpen() {
        if (!canPlaySound()) return
        val played = soundManager.play("parachute_open")
        if (!played) soundEffectPlayer.playZoneTransition()
    }

    fun playLandBounce() {
        if (!canPlaySound()) return
        val played = soundManager.play("land_bounce")
        if (!played) soundEffectPlayer.playBoing(0.8f)
    }

    fun playStarCollect() {
        if (!canPlaySound()) return
        val played = soundManager.play("star_collect")
        if (!played) soundEffectPlayer.playChime()
    }

    fun playCloudBounce() {
        if (!canPlaySound()) return
        val played = soundManager.play("cloud_bounce")
        if (!played) soundEffectPlayer.playBoing(1.3f)
    }

    fun playBalloonBoost() {
        if (!canPlaySound()) return
        val played = soundManager.play("balloon_boost")
        if (!played) soundEffectPlayer.playPop()
    }

    fun playBirdTweet() {
        if (!canPlaySound()) return
        val played = soundManager.play("bird_tweet")
        if (!played) soundManager.play("decor_bird")
    }

    fun playKiteSwoosh() {
        if (!canPlaySound()) return
        val played = soundManager.play("kite_swoosh")
        if (!played) soundEffectPlayer.playZoneTransition()
    }

    fun playRainbowSlide() {
        if (!canPlaySound()) return
        val played = soundManager.play("rainbow_slide")
        if (!played) soundEffectPlayer.playChime()
    }

    fun playUfoHello() {
        if (!canPlaySound()) return
        val played = soundManager.play("ufo_hello")
        if (!played) soundEffectPlayer.playChime()
    }

    fun playFly() {
        if (!canPlaySound()) return
        val played = soundManager.play("fly")
        if (!played) soundEffectPlayer.playBoing()
    }

    fun playGift() {
        if (!canPlaySound()) return
        val played = soundManager.play("gift")
        if (!played) soundEffectPlayer.playChime()
    }

    fun playPop() {
        if (!canPlaySound()) return
        soundManager.play("pop")
    }
}
