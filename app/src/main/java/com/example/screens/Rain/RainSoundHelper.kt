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
    private var lastSplashTimestamp: Long = 0L
    private var dropNote = 0

    init {
        // Грузим заранее: первый тап не должен упасть в запасной «поп».
        soundManager.preloadRaw((1..4).map { "rain_drop$it" } + "rain_splash")
    }

    /**
     * Капелька при поливе: редкая (≈3 в секунду) и тихая, ноты идут по кругу вверх-вниз
     * в одной тональности — дождь звучит как тихая музыкальная шкатулка, а не как пулемёт.
     */
    fun playDropPlip() {
        val now = System.currentTimeMillis()
        if (now - lastDropTimestamp < 300L) return
        lastDropTimestamp = now
        dropNote = (dropNote + 1 + Random.nextInt(2)) % 4
        soundManager.playFxRaw("rain_drop${dropNote + 1}", volume = 0.32f, rate = 0.98f + Random.nextFloat() * 0.04f)
    }

    fun playSplashSoft() {
        val now = System.currentTimeMillis()
        if (now - lastSplashTimestamp < 420L) return
        lastSplashTimestamp = now
        soundManager.playFxRaw("rain_splash", volume = 0.4f, rate = 0.96f + Random.nextFloat() * 0.08f)
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
