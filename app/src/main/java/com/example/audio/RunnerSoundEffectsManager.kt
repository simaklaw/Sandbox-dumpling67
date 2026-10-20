package com.example.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.physics.runner.RunnerEntityType
import com.example.shared.SoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Dedicated toddler-friendly Sound Effects Manager for "RunnerGame" / "Пельмень-Сикс Раннер".
 *
 * Designed to provide:
 * 1. Ultra-low latency SoundPool audio feedback (USAGE_GAME, CONTENT_TYPE_SONIFICATION).
 * 2. Playful cartoon sound variations (boings, squishes, chimes, giggles, comic impacts).
 * 3. Non-repetitive pitch jitter (0.95f - 1.15f) so repetitive actions (jumps, steps) stay fun.
 * 4. Ascending musical arpeggio pitch progression during item collect combo streaks.
 * 5. Coordinated haptic vibrations for physical tactile engagement.
 * 6. Graceful fallbacks and lifecycle-safe cleanup on screen disposal.
 */
class RunnerSoundEffectsManager(
    private val context: Context,
    private val soundPlayer: SoundEffectPlayer? = null,
    private val soundManager: SoundManager? = null
) {
    companion object {
        private const val TAG = "RunnerSoundManager"
        private const val MAX_STREAMS = 12

        // List of all raw OGG sound assets utilized by RunnerGame
        val RUNNER_AUDIO_ASSETS = listOf(
            // Jumps & Acrobatic
            "release_boing",
            "frog_boing",
            "bounce",
            "bubble",
            "pop",
            "whoosh",
            "whoosh_soft",

            // Collisions & Impacts
            "thud",
            "wall",
            "carrot_hit",
            "squish_1",
            "squish_2",
            "squish_3",
            "dizzy",
            "soft_no",

            // Trampolines & Bounces
            "bumper",
            "cloud_bounce",
            "land_bounce",
            "wobble",
            "frog_boing",
            "rainbow_slide",

            // Collectibles & Rewards
            "star_collect",
            "pickup",
            "chime",
            "star_burst",
            "rainbow_chime",
            "yum",
            "yum_voice",
            "giggle",
            "balloon_pop_1",
            "confetti_rain",

            // Speed & Milestones
            "fanfare_short",
            "firework",
            "rainbow_salute",

            // Footsteps & UI
            "tap",
            "drop_plip_1",
            "drop_plip_2",
            "switch_on",
            "switch_off",
            "spawn"
        )
    }

    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val effectiveSoundManager: SoundManager = soundManager ?: SoundManager.getInstance(context, soundPlayer)

    // Vibrator access for synchronized haptic feedback
    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    private val lastPlayTimeMap = ConcurrentHashMap<String, Long>()

    // Master settings
    private var _localEnabled: Boolean = true

    var isEnabled: Boolean
        get() = _localEnabled && effectiveSoundManager.isSoundEnabled && (soundPlayer?.soundEnabled ?: true)
        set(value) {
            _localEnabled = value
            effectiveSoundManager.isSoundEnabled = value
            soundPlayer?.soundEnabled = value
        }

    val isSoundCurrentlyEnabled: Boolean get() = isEnabled

    /**
     * Toggles sound effect playback on/off and syncs with central sound management.
     * Returns true if sound is now enabled, false if muted.
     */
    fun toggleSound(): Boolean {
        val newState = !isEnabled
        isEnabled = newState
        if (newState) {
            playUiTap()
        }
        return newState
    }

    var isHapticsEnabled: Boolean = true
        get() = field && (soundPlayer?.hapticsEnabled ?: true)

    var volume: Float = 1.0f

    // =========================================================================
    // 🔊 CORE PLAYBACK ENGINE WITH JITTER & CENTRAL SOUNDMANAGER
    // =========================================================================

    /**
     * Internal play method with non-repeating variations, pitch jitter and debounce.
     * Delegates playback to shared SoundManager to conserve audio hardware resources.
     */
    fun playRaw(
        fileName: String,
        baseVolume: Float = 1.0f,
        rate: Float = 1.0f,
        pitchJitterAmount: Float = 0.05f,
        minIntervalMs: Long = 50L
    ): Boolean {
        if (!isEnabled) return false

        val now = System.currentTimeMillis()
        val lastTime = lastPlayTimeMap[fileName] ?: 0L
        if (now - lastTime < minIntervalMs) {
            return false // Debounce rapid spam
        }
        lastPlayTimeMap[fileName] = now

        val jitter = if (pitchJitterAmount > 0f) {
            1.0f + (Random.nextFloat() - 0.5f) * pitchJitterAmount * 2f
        } else {
            1.0f
        }
        val effectiveRate = (rate * jitter).coerceIn(0.5f, 2.0f)
        val effectiveVolume = (baseVolume * volume).coerceIn(0f, 1f)

        return effectiveSoundManager.play(fileName, effectiveVolume, effectiveRate)
    }

    /**
     * Plays a random sound from a list of funny alternatives.
     */
    fun playRandom(
        options: List<String>,
        baseVolume: Float = 1.0f,
        rate: Float = 1.0f,
        pitchJitterAmount: Float = 0.06f
    ) {
        if (options.isEmpty()) return
        val pick = options[Random.nextInt(options.size)]
        playRaw(pick, baseVolume, rate, pitchJitterAmount)
    }

    // =========================================================================
    // 🦘 RUNNER GAME AUDIO EVENTS
    // =========================================================================

    /**
     * Plays cheerful jump sound with cute cartoon spring boings.
     * Randomly adjusts playback pitch by +/- 10% to create a dynamic, non-repetitive auditory experience.
     * Alternates between "release_boing", "frog_boing", and "bounce".
     * If double jump ("Сикс-кульбит"), delegates to dedicated playDoubleJumpSpin().
     */
    fun playJump(isDoubleJump: Boolean = false) {
        // Random pitch adjustment of +/- 10% (0.90f to 1.10f)
        val pitchMultiplier = 1.0f + (Random.nextFloat() * 0.20f - 0.10f)

        if (isDoubleJump) {
            playDoubleJumpSpin()
        } else {
            // Regular primary jump: bouncy boings with +/- 10% dynamic pitch
            val jumpSounds = listOf("release_boing", "frog_boing", "bounce")
            val effectiveRate = (1.02f * pitchMultiplier).coerceIn(0.5f, 2.0f)
            playRandom(jumpSounds, baseVolume = 0.95f, rate = effectiveRate, pitchJitterAmount = 0.10f)
            vibrateBoing()
        }
    }

    /**
     * Dedicated double jump "Сикс-кульбит" sound with playful acrobatic whoosh, bubble pop,
     * and random +/- 10% pitch variation.
     */
    fun playDoubleJumpSpin() {
        val pitchMultiplier = 1.0f + (Random.nextFloat() * 0.20f - 0.10f) // +/- 10%
        val bubbleRate = (1.28f * pitchMultiplier).coerceIn(0.5f, 2.0f)
        val whooshRate = (1.38f * pitchMultiplier).coerceIn(0.5f, 2.0f)
        playRaw("bubble", baseVolume = 0.95f, rate = bubbleRate, pitchJitterAmount = 0.10f)
        playRaw("whoosh_soft", baseVolume = 0.80f, rate = whooshRate, pitchJitterAmount = 0.10f)
        vibrateLightPulse()
    }

    /**
     * Plays comic landing impact when hero touches down on a platform or ground after high fall.
     * Randomly adjusts playback pitch by +/- 10%.
     */
    fun playLand(impactSpeed: Float = 1.0f) {
        val pitchMultiplier = 1.0f + (Random.nextFloat() * 0.20f - 0.10f) // +/- 10%
        val vol = (impactSpeed * 0.7f).coerceIn(0.4f, 0.9f)
        val landRate = (1.05f * pitchMultiplier).coerceIn(0.5f, 2.0f)
        playRaw("land_bounce", baseVolume = vol, rate = landRate, pitchJitterAmount = 0.10f, minIntervalMs = 120L)
    }

    /**
     * Plays springy trampoline or pinball bumper bounces.
     * Randomly adjusts playback pitch by +/- 10% to create a dynamic and less repetitive auditory experience.
     */
    fun playBounce(type: RunnerEntityType, impactSpeed: Float = 1.0f) {
        // Random pitch adjustment of +/- 10% (0.90f to 1.10f)
        val pitchMultiplier = 1.0f + (Random.nextFloat() * 0.20f - 0.10f)

        when (type) {
            RunnerEntityType.TRAMPOLINE -> {
                // High vertical mushroom launch with +/- 10% pitch variation
                val trampolineSounds = listOf("cloud_bounce", "frog_boing", "bumper")
                val baseRate = (1.15f * pitchMultiplier).coerceIn(0.5f, 2.0f)
                playRandom(trampolineSounds, baseVolume = 1.0f, rate = baseRate, pitchJitterAmount = 0.10f)
                vibrateBoingHigh()
            }
            RunnerEntityType.BUMPER -> {
                // Pinball rebound with +/- 10% pitch variation
                val bumperRate = (1.12f * pitchMultiplier).coerceIn(0.5f, 2.0f)
                val wobbleRate = (1.20f * pitchMultiplier).coerceIn(0.5f, 2.0f)
                playRaw("bumper", baseVolume = 1.0f, rate = bumperRate, pitchJitterAmount = 0.10f)
                playRaw("wobble", baseVolume = 0.6f, rate = wobbleRate, pitchJitterAmount = 0.10f)
                vibrateLightPulse()
            }
            else -> {
                // General bouncy surface with +/- 10% pitch variation
                val baseRate = (1.0f * pitchMultiplier).coerceIn(0.5f, 2.0f)
                playRaw("bounce", baseVolume = 0.85f, rate = baseRate, pitchJitterAmount = 0.10f)
                vibrateLightPulse()
            }
        }
    }

    /**
     * Plays funny squish and comical thuds when Pelmen-Six bumps into obstacles.
     */
    fun playObstacleHit(
        type: RunnerEntityType = RunnerEntityType.OBSTACLE_BUTTER,
        intensity: Float = 1.0f
    ) {
        // Hilarious squish dough sound
        val squishSounds = listOf("squish_1", "squish_2", "squish_3")
        playRandom(squishSounds, baseVolume = 0.95f, rate = 1.05f, pitchJitterAmount = 0.08f)

        when (type) {
            RunnerEntityType.OBSTACLE_HAZARD -> {
                // Crunchy obstacle hit (pepper / fork)
                playRaw("carrot_hit", baseVolume = 0.9f, rate = 1.1f)
                playRaw("thud", baseVolume = 0.8f, rate = 1.0f)
            }
            RunnerEntityType.OBSTACLE_BUTTER -> {
                // Soft creamy slip / bump
                playRaw("thud", baseVolume = 0.85f, rate = 1.15f)
            }
            else -> {
                playRaw("thud", baseVolume = 0.8f, rate = 1.0f)
            }
        }

        // Add cute toddler cartoon vocal reaction on hard hits
        if (intensity > 0.6f) {
            scope.launch {
                delay(120L)
                val reactions = listOf("soft_no", "dizzy")
                playRandom(reactions, baseVolume = 0.8f, rate = 1.1f)
            }
        }

        vibrateObstacleHit()
    }

    /**
     * Specific 'collect' sound trigger whenever Pelmen-Six collides with an item.
     * Uses a distinct, high-pitched bubbly sound effect from assets ("bubble") with
     * lively pop acoustics and ascending combo progression.
     */
    fun playBubblyItemCollect(comboStreak: Int = 0) {
        val pitchStep = (comboStreak % 8) * 0.05f
        val pitchJitter = (Random.nextFloat() * 0.16f - 0.08f) // +/- 8% organic bubbly variation
        val highPitchRate = (1.36f + pitchStep + pitchJitter).coerceIn(1.15f, 1.85f)
        playRaw("bubble", baseVolume = 1.0f, rate = highPitchRate, pitchJitterAmount = 0.05f)
        vibrateCollectTap()
    }

    /**
     * Plays rewarding item collection sound.
     * Integrates the distinct high-pitched bubbly trigger alongside ascending musical pentatonic arpeggio.
     */
    fun playCollectCoin(comboStreak: Int = 0) {
        // Distinct high-pitched bubbly collect trigger on item collision
        playBubblyItemCollect(comboStreak)

        // Pentatonic-like progressive pitch step (C, D, E, G, A, C...)
        val pitchStep = (comboStreak % 8) * 0.065f
        val comboRate = (1.0f + pitchStep).coerceIn(1.0f, 1.62f)

        val coinSounds = listOf("star_collect", "pickup", "chime")
        playRandom(coinSounds, baseVolume = 0.85f, rate = comboRate, pitchJitterAmount = 0.04f)

        // Celebratory mini-fanfare on combo milestones (every 5 coins)
        if (comboStreak > 0 && comboStreak % 5 == 0) {
            scope.launch {
                delay(80L)
                playRaw("star_burst", baseVolume = 0.75f, rate = 1.25f)
            }
        }
    }

    /**
     * Plays cartoon smash sound when an obstacle or butter block is obliterated in turbo mode.
     */
    fun playObstacleSmash() {
        playRaw("carrot_hit", baseVolume = 1.0f, rate = 1.15f)
        playRaw("squish_3", baseVolume = 0.9f, rate = 1.1f)
        vibrateObstacleHit()
    }

    /**
     * Plays extra celebratory sound for Golden Pelmen (Dumpling) collect.
     * Combines distinct bubbly pop with golden fanfare and sweet toddler reaction.
     */
    fun playCollectDumpling(comboStreak: Int = 0) {
        // Distinct high-pitched bubbly collect trigger on item collision
        playBubblyItemCollect(comboStreak)

        playRaw("star_burst", baseVolume = 1.0f, rate = 1.1f)
        playRaw("rainbow_chime", baseVolume = 0.85f, rate = 1.15f)

        // Funny delicious toddler "Yum!" reaction
        scope.launch {
            delay(100L)
            playRaw("yum_voice", baseVolume = 0.9f, rate = 1.05f)
        }

        vibrateRewardBurst()
    }

    /**
     * Unified item collect dispatcher.
     */
    fun playCollectItem(type: RunnerEntityType, comboStreak: Int = 0) {
        when (type) {
            RunnerEntityType.COLLECTIBLE_COIN -> playCollectCoin(comboStreak)
            RunnerEntityType.COLLECTIBLE_DUMPLING -> playCollectDumpling(comboStreak)
            RunnerEntityType.POWERUP_SHIELD -> playPowerUpShield()
            RunnerEntityType.POWERUP_MAGNET -> playPowerUpMagnet()
            else -> playBubblyItemCollect(comboStreak)
        }
    }

    /**
     * Explicit collect sound trigger alias for general item collisions.
     */
    fun playCollect(type: RunnerEntityType = RunnerEntityType.COLLECTIBLE_COIN, comboStreak: Int = 0) {
        playCollectItem(type, comboStreak)
    }

    /**
     * Plays magical activation sound when Bubble Shield is collected.
     */
    fun playPowerUpShield() {
        playRaw("rainbow_chime", baseVolume = 1.0f, rate = 1.25f)
        playRaw("bubble", baseVolume = 0.95f, rate = 1.35f)
        vibrateRewardBurst()
    }

    /**
     * Plays crisp popping sound when Bubble Shield breaks and absorbs an obstacle collision.
     */
    fun playShieldBreak() {
        playRaw("balloon_pop_1", baseVolume = 1.0f, rate = 1.2f)
        playRaw("bubble", baseVolume = 0.95f, rate = 0.9f)
        vibrateObstacleHit()
    }

    /**
     * Plays electromagnetic whoosh and chime when Coin Magnet is collected.
     */
    fun playPowerUpMagnet() {
        playRaw("chime", baseVolume = 1.0f, rate = 1.3f)
        playRaw("whoosh", baseVolume = 0.85f, rate = 1.35f)
        vibrateRewardBurst()
    }

    /**
     * Plays high-speed turbo booster gate sound (Speed 67 Turbo).
     */
    fun playSpeedBoost() {
        playRaw("whoosh", baseVolume = 1.0f, rate = 1.25f)
        playRaw("fanfare_short", baseVolume = 0.9f, rate = 1.15f)
        playRaw("firework", baseVolume = 0.8f, rate = 1.2f)
        vibrateSpeedBoostRumble()
    }

    /**
     * Plays downward dive / slam sound.
     */
    fun playDive() {
        playRaw("whoosh_soft", baseVolume = 0.85f, rate = 0.9f)
        vibrateLightPulse()
    }

    /**
     * Plays running footstep patter. Alternates left and right foot with subtle pitch variation.
     */
    fun playFootstep(isLeftFoot: Boolean) {
        val sound = if (isLeftFoot) "drop_plip_1" else "drop_plip_2"
        val rate = if (isLeftFoot) 1.08f else 0.96f
        playRaw(sound, baseVolume = 0.35f, rate = rate, pitchJitterAmount = 0.03f, minIntervalMs = 80L)
    }

    /**
     * Plays UI tap sound for buttons (pause, debug, reset).
     */
    fun playUiTap() {
        playRaw("tap", baseVolume = 0.85f, rate = 1.1f)
        vibrateLightPulse()
    }

    /**
     * Plays pause / unpause feedback.
     */
    fun playPauseToggle(isPaused: Boolean) {
        val sound = if (isPaused) "switch_off" else "switch_on"
        playRaw(sound, baseVolume = 0.85f, rate = 1.1f)
        vibrateLightPulse()
    }

    /**
     * Plays game reset / respawn sound.
     */
    fun playReset() {
        playRaw("spawn", baseVolume = 0.9f, rate = 1.1f)
        playRaw("release_boing", baseVolume = 0.85f, rate = 1.15f)
        vibrateLightPulse()
    }

    /**
     * Plays game over sound.
     */
    fun playGameOver() {
        playRaw("dizzy", baseVolume = 0.9f, rate = 0.95f)
        playRaw("soft_no", baseVolume = 0.85f, rate = 1.0f)
        vibrateObstacleHit()
    }

    /**
     * Plays milestone celebration when reaching high distances (e.g. 500m, 1000m).
     */
    fun playMilestone(distanceMeters: Int) {
        playRaw("rainbow_salute", baseVolume = 1.0f, rate = 1.05f)
        playRaw("fanfare_short", baseVolume = 0.95f, rate = 1.1f)
        vibrateRewardBurst()
    }

    /**
     * 🧊 Plays crisp slippery skate slide sound when dashing across ice platforms.
     */
    fun playIceSlide() {
        playRaw("rainbow_slide", baseVolume = 0.9f, rate = 1.25f, pitchJitterAmount = 0.08f, minIntervalMs = 250L)
        playRaw("whoosh_soft", baseVolume = 0.7f, rate = 1.3f, pitchJitterAmount = 0.05f, minIntervalMs = 250L)
        vibrateLightPulse()
    }

    /**
     * 🍮 Plays ultra-squishy, comical jelly marmalade bounce ("boing-squish").
     */
    fun playJellyBounce() {
        playRaw("frog_boing", baseVolume = 1.0f, rate = 1.15f, pitchJitterAmount = 0.1f)
        playRaw("squish_1", baseVolume = 0.9f, rate = 1.3f)
        vibrateBoing()
    }

    /**
     * ☁️ Plays warm steam geyser whoosh and bubbling lift.
     */
    fun playSteamWhoosh() {
        playRaw("whoosh", baseVolume = 0.95f, rate = 1.1f, pitchJitterAmount = 0.06f, minIntervalMs = 200L)
        playRaw("bubble", baseVolume = 0.85f, rate = 1.4f, minIntervalMs = 200L)
        vibrateLightPulse()
    }

    /**
     * 🌟 Plays joyful sticker discovery celebration fanfare with confetti sounds.
     */
    fun playStickerUnlockCelebration() {
        playRaw("confetti_rain", baseVolume = 1.0f, rate = 1.1f)
        playRaw("fanfare_short", baseVolume = 0.95f, rate = 1.2f)
        playRaw("rainbow_salute", baseVolume = 0.9f, rate = 1.1f)
        vibrateRewardBurst()
    }

    // =========================================================================
    // 📳 SYNCHRONIZED HAPTIC FEEDBACK HELPERS
    // =========================================================================

    private fun vibrateLightPulse() {
        if (!isHapticsEnabled) return
        soundPlayer?.triggerLightTapPulse() ?: run {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(12, 100))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(12)
                }
            } catch (_: Exception) {}
        }
    }

    private fun vibrateBoing() {
        if (!isHapticsEnabled) return
        soundPlayer?.vibrateBoingRelease() ?: run {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 20, 15, 25)
                    val amplitudes = intArrayOf(0, 160, 0, 220)
                    vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(40)
                }
            } catch (_: Exception) {}
        }
    }

    private fun vibrateBoingHigh() {
        if (!isHapticsEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 25, 15, 35)
                val amplitudes = intArrayOf(0, 220, 0, 255)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(60)
            }
        } catch (_: Exception) {}
    }

    private fun vibrateObstacleHit() {
        if (!isHapticsEnabled) return
        soundPlayer?.vibrateSquish() ?: run {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 45, 20, 30)
                    val amplitudes = intArrayOf(0, 240, 0, 160)
                    vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(75)
                }
            } catch (_: Exception) {}
        }
    }

    private fun vibrateCollectTap() {
        if (!isHapticsEnabled) return
        soundPlayer?.triggerLightTapPulse() ?: run {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(14, 150))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(14)
                }
            } catch (_: Exception) {}
        }
    }

    private fun vibrateRewardBurst() {
        if (!isHapticsEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 30, 20, 30, 20, 45)
                val amplitudes = intArrayOf(0, 180, 0, 220, 0, 255)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120)
            }
        } catch (_: Exception) {}
    }

    private fun vibrateSpeedBoostRumble() {
        if (!isHapticsEnabled) return
        soundPlayer?.vibrateSquishDeform() ?: run {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 30, 15, 30, 15, 40)
                    val amplitudes = intArrayOf(0, 150, 0, 200, 0, 240)
                    vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(100)
                }
            } catch (_: Exception) {}
        }
    }

    // =========================================================================
    // 🧹 LIFECYCLE & CLEANUP
    // =========================================================================

    fun pause() {
        // Handled centrally by SoundManager
    }

    fun resume() {
        // Handled centrally by SoundManager
    }

    fun release() {
        try {
            scope.cancel()
            lastPlayTimeMap.clear()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing RunnerSoundEffectsManager", e)
        }
    }
}

/**
 * Convenient aliases requested for seamless developer experience.
 */
typealias RunnerGameSoundManager = RunnerSoundEffectsManager
typealias RunnerSoundManager = RunnerSoundEffectsManager

/**
 * Composable helper to remember and automatically lifecycle-manage RunnerSoundEffectsManager.
 */
@Composable
fun rememberRunnerSoundEffectsManager(
    context: Context = LocalContext.current,
    soundPlayer: SoundEffectPlayer? = null,
    soundManager: SoundManager? = null
): RunnerSoundEffectsManager {
    val manager = remember(context, soundPlayer, soundManager) {
        RunnerSoundEffectsManager(context, soundPlayer, soundManager)
    }

    DisposableEffect(manager) {
        onDispose {
            manager.release()
        }
    }

    return manager
}
