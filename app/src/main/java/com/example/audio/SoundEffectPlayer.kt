package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import com.example.shared.SoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Toddler-friendly Sound and Haptics Player for "Детская площадка".
 * Uses procedural synthesis for instant zero-latency audio without relying on external media files.
 */
class SoundEffectPlayer(private val context: Context) {

    private val vibrator: Vibrator? = try {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (v?.hasVibrator() == true) v else null
    } catch (_: Exception) {
        null
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile
    private var isSettingSoundEnabled = false

    val soundManager: SoundManager = SoundManager.getInstance(context, null).also {
        it.attachSoundEffectPlayer(this)
    }

    var soundEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (!isSettingSoundEnabled) {
                isSettingSoundEnabled = true
                try {
                    val sm = try { soundManager } catch (_: Exception) { null }
                    if (sm != null && sm.isSoundEnabled != value) {
                        sm.isSoundEnabled = value
                    }
                } catch (_: Exception) {
                    // Safeguard during initialization
                } finally {
                    isSettingSoundEnabled = false
                }
            }
        }
    var hapticsEnabled: Boolean = true

    private val fxPreloadNames = listOf(
        "fx_animal_chorus",
        "fx_arcade_button",
        "fx_arrow_carrot",
        "fx_arrow_carrot_bullseye",
        "fx_arrow_sucker",
        "fx_arrow_sucker_bullseye",
        "fx_arrow_vibration_buzz",
        "fx_arrow_wooden",
        "fx_arrow_wooden_bullseye",
        "fx_baby_giggle",
        "fx_bear_growl",
        "fx_belly_inhale_pop",
        "fx_bicycle_bell",
        "fx_boing",
        "fx_bow_shot",
        "fx_bowstring_tension",
        "fx_box_pop_open",
        "fx_box_rattle",
        "fx_box_tension_hum",
        "fx_bumper_hit",
        "fx_bunny_hop",
        "fx_butt_fall_laugh",
        "fx_cartoon_yelp",
        "fx_cat_meow",
        "fx_cat_purr",
        "fx_catapult_crash_land",
        "fx_catapult_flying_scream",
        "fx_catapult_launch",
        "fx_catapult_stretch",
        "fx_chime",
        "fx_clean_sponge",
        "fx_coin_shower",
        "fx_comical_fall_wobble",
        "fx_cow_moo",
        "fx_crow_caw",
        "fx_cute_character",
        "fx_dial_0",
        "fx_dial_1",
        "fx_dial_2",
        "fx_dial_3",
        "fx_dial_4",
        "fx_dial_5",
        "fx_dial_6",
        "fx_dial_7",
        "fx_dial_8",
        "fx_dial_9",
        "fx_dog_bark",
        "fx_dog_woof",
        "fx_door_open",
        "fx_eat_crunch",
        "fx_fanfare",
        "fx_flipper_snap",
        "fx_freeze_ice",
        "fx_frog_ribbit",
        "fx_gear_ratchet",
        "fx_goose_honk",
        "fx_grain_pour",
        "fx_grain_rustle",
        "fx_gulp",
        "fx_head_dizzy_spin",
        "fx_hiccup_series",
        "fx_horse_neigh",
        "fx_idle_chuckle",
        "fx_jackpot_fanfare",
        "fx_jar_glass_clink",
        "fx_kiss",
        "fx_legendary_fanfare",
        "fx_lever_down",
        "fx_lever_up",
        "fx_lock_off",
        "fx_lock_on",
        "fx_magic_sparkle",
        "fx_mold_stamp",
        "fx_munch_crunch",
        "fx_munch_yucky",
        "fx_patter_l",
        "fx_patter_r",
        "fx_pelmen_giggle_run",
        "fx_pelmen_tag_caught",
        "fx_phone_hangup",
        "fx_phone_ringtone",
        "fx_pig_oink",
        "fx_pinball_plunger",
        "fx_pinwheel_tick",
        "fx_rooster_crow",
        "fx_sand_crunch",
        "fx_scarecrow_grunt",
        "fx_scarecrow_recoil_spring",
        "fx_sheep_baa",
        "fx_shield_chime",
        "fx_sneeze",
        "fx_sparkle_chime",
        "fx_speed_dash",
        "fx_splash",
        "fx_squeak",
        "fx_squish",
        "fx_squish_elastic",
        "fx_stir",
        "fx_switch_off",
        "fx_switch_on",
        "fx_tea_whistle",
        "fx_tickle_laughter",
        "fx_tongue_bleee",
        "fx_vortex_swirl",
        "fx_xylo_0",
        "fx_xylo_1",
        "fx_xylo_2",
        "fx_xylo_3",
        "fx_xylo_4",
        "fx_xylo_5",
        "fx_xylo_6",
        "fx_xylo_7",
        "fx_xyloc_0",
        "fx_xyloc_1",
        "fx_xyloc_2",
        "fx_xyloc_3",
        "fx_xyloc_4",
        "fx_xyloc_5",
        "fx_xyloc_6",
        "fx_xyloc_7",
        "fx_zone_transition"
    )

    init {
        scope.launch { try { soundManager.preloadRaw(fxPreloadNames) } catch (_: Throwable) {} }
    }

    // ==========================================
    // 🎈 BALLOON & BUBBLE POP SOUNDS
    // ==========================================

    fun playPop(pitchMultiplier: Float = 1.0f) {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.play("pop", rate = pitchMultiplier)) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 70
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)
            val baseFreq = 550f * pitchMultiplier.coerceIn(0.6f, 2.0f)

            for (i in 0 until numSamples) {
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress) * (1.0f - progress)
                val freq = baseFreq - progress * 260f
                val sample = (sin(2.0 * PI * freq * (i.toFloat() / sampleRate)) * env * 27000).toInt().toShort()
                buffer[i] = sample
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playBubblePop(pitchMultiplier: Float = 0.75f + kotlin.random.Random.nextFloat() * 0.5f) {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.play("bubble", rate = pitchMultiplier)) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 90
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)
            val effectivePitch = pitchMultiplier.coerceIn(0.5f, 2.0f)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val freq = (400f + 500f * progress) * effectivePitch
                val env = sin(progress * PI.toFloat())
                val wave = sin(2.0 * PI * freq * t) * 0.85f + sin(2.0 * PI * (freq * 1.5f) * t) * 0.15f
                buffer[i] = (wave * env * 24000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    // ==========================================
    // 🥟 SQUISH & BOING SOUNDS
    // ==========================================

    fun playSquish(pitchMultiplier: Float = 1.0f) {
        if (!soundEnabled) return
        vibrateSquish()
        if (soundManager.playFxRaw("fx_squish")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 130
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)
            val baseFreq = 220f * pitchMultiplier.coerceIn(0.7f, 1.8f)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val freq = baseFreq + 90f * sin(2.0 * PI * 16.0 * t).toFloat()
                val env = (1.0f - (i.toFloat() / numSamples))
                buffer[i] = (sin(2.0 * PI * freq * t) * env * 24000).toInt().toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playBoing(pitchMultiplier: Float = 1.0f) {
        if (!soundEnabled) return
        vibrateMedium()
        if (soundManager.playFxRaw("fx_boing")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 280
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val base = (240f + 140f * progress) * pitchMultiplier.coerceIn(0.6f, 1.8f)
                val freq = base + sin(2.0 * PI * 30.0 * t).toFloat() * 60f
                val env = (1.0f - progress * 0.85f)
                val sample = (sin(2.0 * PI * freq * t) * env * 24000).toInt().toShort()
                buffer[i] = sample
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playSqueak() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw("fx_squeak")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 120
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val freq = 800f + 600f * progress
                val env = sin(progress * PI.toFloat())
                buffer[i] = (sin(2.0 * PI * freq * t) * env * 22000).toInt().toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    // ==========================================
    // 🍵 TEA & KITCHEN SOUNDS
    // ==========================================

    fun playTeaWhistle() {
        if (!soundEnabled) return
        vibrateSeries()
        if (soundManager.playFxRaw("fx_tea_whistle")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 450
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                // Kettle whistle rising pitch
                val freq = 1200f + 600f * progress + sin(2.0 * PI * 12.0 * t).toFloat() * 50f
                val env = sin(progress * PI.toFloat())
                val harmonic = sin(2.0 * PI * freq * t).toFloat() * 0.75f + sin(2.0 * PI * (freq * 2.0) * t).toFloat() * 0.25f
                buffer[i] = (harmonic * env * 25000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playSplash() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw("fx_splash")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 120
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress) * (1.0f - progress)
                val bubble = sin(2.0 * PI * (280.0 + progress * 700.0) * t).toFloat() * 14000f
                val noise = ((Math.random() * 2.0 - 1.0) * 12000).toFloat()
                buffer[i] = ((bubble + noise) * env).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playStir() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw("fx_stir")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 260
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val swirl = sin(2.0 * PI * 8.0 * t).toFloat()
                val freq = 320f + swirl * 120f
                val env = sin(progress * PI.toFloat())
                val noise = ((Math.random() * 2.0 - 1.0) * 6000).toFloat()
                val tone = sin(2.0 * PI * freq * t).toFloat() * 16000f
                buffer[i] = ((tone + noise) * env).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playGulpDrink() {
        if (!soundEnabled) return
        vibrateSeries()
        if (soundManager.playFxRaw("fx_gulp")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 380
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            val stepTime = numSamples / 3
            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val stepIdx = i % stepTime
                val stepProg = stepIdx.toFloat() / stepTime
                val env = sin(stepProg * PI.toFloat())
                val freq = 220f + 160f * (1.0f - stepProg)
                val wave = sin(2.0 * PI * freq * t).toFloat() * 24000f
                buffer[i] = (wave * env).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    // ==========================================
    // 🎨 DRAWING & MAGIC SOUNDS
    // ==========================================

    fun playChime(pitchMultiplier: Float = 1.0f) {
        if (!soundEnabled) return
        if (soundManager.playFxRaw("fx_chime")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 160
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)
            val freq = 1046.5f * pitchMultiplier.coerceIn(0.5f, 2.5f)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val env = (1.0f - (i.toFloat() / numSamples))
                val wave = sin(2.0 * PI * freq * t) * 0.75f + sin(2.0 * PI * (freq * 2f) * t) * 0.25f
                buffer[i] = (wave * env * 22000).toInt().toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playMagicSparkle() {
        if (!soundEnabled) return
        if (soundManager.playFxRaw("fx_magic_sparkle")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 280
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)
            val notes = floatArrayOf(880f, 1046.5f, 1318.5f, 1567.98f, 2093f)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val noteIdx = ((progress * notes.size).toInt()).coerceIn(0, notes.size - 1)
                val freq = notes[noteIdx]
                val subProg = (progress * notes.size) - noteIdx
                val env = (1.0f - subProg) * (1.0f - progress * 0.4f)
                val wave = sin(2.0 * PI * freq * t).toFloat() * 24000f
                buffer[i] = (wave * env).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playCleanSponge() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw("fx_clean_sponge")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 180
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val progress = i.toFloat() / numSamples
                val env = sin(progress * PI.toFloat())
                val noise = ((Math.random() * 2.0 - 1.0) * 16000).toFloat()
                buffer[i] = (noise * env).toInt().toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    // ==========================================
    // 🎛️ BUSYBOARD SOUNDS (Switches, Xylophone, Gears, Doors, Levers)
    // ==========================================

    fun playLeverClunk(isDown: Boolean = true) {
        if (!soundEnabled) return
        vibrateMedium()
        if (soundManager.playFxRaw((if (isDown) "fx_lever_down" else "fx_lever_up"))) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 90
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)
            val baseFreq = if (isDown) 180f else 320f

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress) * (1.0f - progress)
                val clunk = sin(2.0 * PI * (baseFreq + 60f * (1f - progress)) * t).toFloat() * 20000f
                val metal = ((Math.random() * 2.0 - 1.0) * 10000).toFloat()
                buffer[i] = ((clunk + metal) * env).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playArcadeButton() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw("fx_arcade_button")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 110
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress)
                val freq = 520f + 280f * sin(progress * PI.toFloat())
                val tone = sin(2.0 * PI * freq * t).toFloat() * 22000f
                buffer[i] = (tone * env).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playSwitchClick(isOn: Boolean = true) {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw((if (isOn) "fx_switch_on" else "fx_switch_off"))) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 45
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)
            val baseFreq = if (isOn) 980f else 620f

            for (i in 0 until numSamples) {
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress) * (1.0f - progress)
                val tone = sin(2.0 * PI * baseFreq * (i.toFloat() / sampleRate)).toFloat() * 18000f
                val snap = ((Math.random() * 2.0 - 1.0) * 12000).toFloat()
                buffer[i] = ((tone + snap) * env).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playDoorOpen() {
        if (!soundEnabled) return
        vibrateMedium()
        if (soundManager.playFxRaw("fx_door_open")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 180
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val freq = 340f + 220f * progress + sin(2.0 * PI * 24.0 * t).toFloat() * 40f
                val env = sin(progress * PI.toFloat())
                buffer[i] = (sin(2.0 * PI * freq * t) * env * 22000).toInt().toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playBicycleBell() {
        if (!soundEnabled) return
        vibrateSeries()
        if (soundManager.playFxRaw("fx_bicycle_bell")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 240
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val ring = sin(2.0 * PI * 18.0 * t).toFloat()
                val env = (1.0f - progress * 0.7f)
                val freq = 1760.0f + ring * 120f
                val wave = sin(2.0 * PI * freq * t) * 0.7f + sin(2.0 * PI * (freq * 2.2f) * t) * 0.3f
                buffer[i] = (wave * env * 25000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    // ==========================================
    // 🐾 10 CUTE ANIMAL SOUNDS (Dog, Cat, Cow, Horse, Pig, Sheep, Goose, Rooster, Frog, Crow)
    // ==========================================

    fun playAnimalSound(animal: String) {
        if (!soundEnabled) return
        vibrateMedium()
        scope.launch {
            val sampleRate = 22050
            when (animal.lowercase().trim()) {
                "dog", "собачка", "щенок", "песик", "dog_woof" -> playDogWoof(sampleRate)
                "cat", "котик", "кошечка", "кот", "cat_meow" -> playCatMeow(sampleRate)
                "cow", "корова", "коровка", "cow_moo" -> playCowMoo(sampleRate)
                "horse", "лошадка", "конь", "horse_neigh" -> playHorseNeigh(sampleRate)
                "pig", "свинья", "поросенок", "хрюшка", "pig_oink" -> playPigOink(sampleRate)
                "sheep", "барашек", "овечка", "баран", "sheep_baa" -> playSheepBaa(sampleRate)
                "goose", "гусь", "гусик", "goose_honk" -> playGooseHonk(sampleRate)
                "rooster", "петух", "петушок", "rooster_crow" -> playRoosterCrow(sampleRate)
                "frog", "лягушка", "лягушонок", "frog_ribbit" -> playFrogRibbit(sampleRate)
                "crow", "ворона", "ворон", "crow_caw" -> playCrowCaw(sampleRate)
                "bunny", "зайка" -> playBunnyHop()
                "bear", "мишка" -> playBearGrowl()
                else -> playDogWoof(sampleRate)
            }
        }
    }

    fun playDogWoof(sampleRate: Int = 22050) {
        if (soundManager.playFxRaw("fx_dog_woof")) return
        val durationMs = 230
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / numSamples
            val freq = 310f - 140f * progress
            val env = (1.0f - progress) * (1.0f - progress)
            val noise = ((Math.random() * 2.0 - 1.0) * 0.3f).toFloat()
            val tone = sin(2.0 * PI * freq * t).toFloat() * 0.7f
            buffer[i] = ((tone + noise) * env * 27000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcm(buffer, sampleRate)
    }

    fun playCatMeow(sampleRate: Int = 22050) {
        if (soundManager.playFxRaw("fx_cat_meow")) return
        val durationMs = 380
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / numSamples
            val freq = if (progress < 0.45f) {
                480f + (progress / 0.45f) * 240f
            } else {
                720f - ((progress - 0.45f) / 0.55f) * 180f
            }
            val env = sin(progress * PI.toFloat())
            val harmonic1 = sin(2.0 * PI * freq * t).toFloat() * 0.6f
            val harmonic2 = sin(2.0 * PI * freq * 2f * t).toFloat() * 0.3f
            val harmonic3 = sin(2.0 * PI * freq * 3f * t).toFloat() * 0.1f
            buffer[i] = ((harmonic1 + harmonic2 + harmonic3) * env * 25000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcm(buffer, sampleRate)
    }

    fun playCowMoo(sampleRate: Int = 22050) {
        if (soundManager.playFxRaw("fx_cow_moo")) return
        val durationMs = 520
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / numSamples
            val freq = 135f - progress * 20f + sin(2.0 * PI * 5.0 * t).toFloat() * 8f
            val env = sin(progress * PI.toFloat())
            val wave = sin(2.0 * PI * freq * t).toFloat() * 0.65f + sin(2.0 * PI * freq * 2f * t).toFloat() * 0.25f + sin(2.0 * PI * freq * 3f * t).toFloat() * 0.1f
            buffer[i] = (wave * env * 26500f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcm(buffer, sampleRate)
    }

    fun playHorseNeigh(sampleRate: Int = 22050) {
        if (soundManager.playFxRaw("fx_horse_neigh")) return
        val durationMs = 480
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / numSamples
            // High whinny with fast vibrato descending into warm snort
            val vibrato = sin(2.0 * PI * 18.0 * t).toFloat() * 80f
            val baseFreq = if (progress < 0.6f) {
                750f + (progress / 0.6f) * 200f
            } else {
                950f - ((progress - 0.6f) / 0.4f) * 550f
            }
            val freq = (baseFreq + vibrato).coerceAtLeast(180f)
            val env = sin(progress * PI.toFloat())
            val tone = sin(2.0 * PI * freq * t).toFloat() * 0.7f + sin(2.0 * PI * freq * 1.5f * t).toFloat() * 0.2f
            val breath = ((Math.random() * 2.0 - 1.0) * 0.15f * progress).toFloat()
            buffer[i] = ((tone + breath) * env * 26000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcm(buffer, sampleRate)
    }

    fun playPigOink(sampleRate: Int = 22050) {
        if (soundManager.playFxRaw("fx_pig_oink")) return
        val durationMs = 260
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / numSamples
            val grunt = sin(2.0 * PI * 32.0 * t).toFloat()
            val freq = 210f + grunt * 70f - progress * 40f
            val env = sin(progress * PI.toFloat())
            val wave = sin(2.0 * PI * freq * t).toFloat() * 0.6f + sin(2.0 * PI * (freq * 2.3f) * t).toFloat() * 0.3f
            val noise = ((Math.random() * 2.0 - 1.0) * 0.2f).toFloat()
            buffer[i] = ((wave + noise) * env * 27000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcm(buffer, sampleRate)
    }

    fun playSheepBaa(sampleRate: Int = 22050) {
        if (soundManager.playFxRaw("fx_sheep_baa")) return
        val durationMs = 450
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / numSamples
            // Bleating tremolo (13Hz amplitude flutter)
            val tremolo = 0.65f + 0.35f * sin(2.0 * PI * 13.0 * t).toFloat()
            val freq = 290f - progress * 40f
            val env = sin(progress * PI.toFloat()) * tremolo
            val wave = sin(2.0 * PI * freq * t).toFloat() * 0.55f +
                    sin(2.0 * PI * freq * 2f * t).toFloat() * 0.3f +
                    sin(2.0 * PI * freq * 3f * t).toFloat() * 0.15f
            buffer[i] = (wave * env * 26000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcm(buffer, sampleRate)
    }

    fun playGooseHonk(sampleRate: Int = 22050) {
        if (soundManager.playFxRaw("fx_goose_honk")) return
        val durationMs = 280
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / numSamples
            val freq1 = 430f - progress * 60f
            val freq2 = 860f - progress * 120f
            val env = sin(progress * PI.toFloat())
            val tone = sin(2.0 * PI * freq1 * t).toFloat() * 0.5f +
                    sin(2.0 * PI * freq2 * t).toFloat() * 0.4f +
                    sin(2.0 * PI * (freq1 * 3f) * t).toFloat() * 0.1f
            buffer[i] = (tone * env * 27000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcm(buffer, sampleRate)
    }

    fun playRoosterCrow(sampleRate: Int = 22050) {
        if (soundManager.playFxRaw("fx_rooster_crow")) return
        val durationMs = 620
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        // 4-step crow: Ku - ka - re - kuuu!
        val notes = floatArrayOf(440f, 554.37f, 659.25f, 587.33f)
        val noteBoundaries = floatArrayOf(0.18f, 0.36f, 0.58f, 1.0f)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / numSamples
            var currentNote = notes[0]
            var subProg = progress / 0.18f
            for (k in noteBoundaries.indices) {
                val prev = if (k == 0) 0f else noteBoundaries[k - 1]
                val next = noteBoundaries[k]
                if (progress in prev..next) {
                    currentNote = notes[k]
                    subProg = (progress - prev) / (next - prev)
                    break
                }
            }
            val env = sin(subProg.coerceIn(0f, 1f) * PI.toFloat()) * (if (progress > 0.58f) 1.0f - (progress - 0.58f) * 0.4f else 1.0f)
            val wave = sin(2.0 * PI * currentNote * t).toFloat() * 0.6f +
                    sin(2.0 * PI * (currentNote * 2f) * t).toFloat() * 0.3f +
                    sin(2.0 * PI * (currentNote * 3f) * t).toFloat() * 0.1f
            buffer[i] = (wave * env * 26000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcm(buffer, sampleRate)
    }

    fun playFrogRibbit(sampleRate: Int = 22050) {
        if (soundManager.playFxRaw("fx_frog_ribbit")) return
        val durationMs = 280
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / numSamples
            val flutter = sin(2.0 * PI * 40.0 * t).toFloat()
            val freq = 170f + flutter * 80f
            val env = sin(progress * PI.toFloat())
            val wave = sin(2.0 * PI * freq * t).toFloat() * 0.8f + sin(2.0 * PI * (freq * 2f) * t).toFloat() * 0.2f
            buffer[i] = (wave * env * 26000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcm(buffer, sampleRate)
    }

    fun playCrowCaw(sampleRate: Int = 22050) {
        if (soundManager.playFxRaw("fx_crow_caw")) return
        val durationMs = 340
        val numSamples = (durationMs * sampleRate) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / numSamples
            val baseFreq = 520f - progress * 140f
            val rasp = sin(2.0 * PI * 65.0 * t).toFloat() * 70f
            val freq = (baseFreq + rasp).coerceAtLeast(150f)
            val env = sin(progress * PI.toFloat())
            val noise = ((Math.random() * 2.0 - 1.0) * 0.25f).toFloat()
            val tone = sin(2.0 * PI * freq * t).toFloat() * 0.75f
            buffer[i] = ((tone + noise) * env * 26000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playPcm(buffer, sampleRate)
    }

    // ==========================================
    // 🎠 ZONE TRANSITION & PET REACTION SOUNDS
    // ==========================================

    /**
     * Joyful whoosh + glockenspiel chord when switching between zones in "Детская площадка".
     */
    fun playZoneTransition() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw("fx_zone_transition")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 260
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)
            val chordNotes = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.50f)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress * 0.7f) * (1.0f - progress)
                var sumWave = 0f
                for (k in chordNotes.indices) {
                    val noteFreq = chordNotes[k]
                    val delayFactor = (k * 0.04f)
                    if (progress >= delayFactor) {
                        val subProg = (progress - delayFactor) / (1f - delayFactor)
                        val subEnv = (1f - subProg) * sin(subProg * PI.toFloat())
                        sumWave += sin(2.0 * PI * noteFreq * t).toFloat() * subEnv * 0.25f
                    }
                }
                // Subtle whoosh noise
                val noise = ((Math.random() * 2.0 - 1.0) * 0.15f * (1f - progress)).toFloat()
                buffer[i] = ((sumWave + noise) * env * 27000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playBunnyHop() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw("fx_bunny_hop")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 150
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val freq = 450f + 350f * progress
                val env = sin(progress * PI.toFloat())
                val wave = sin(2.0 * PI * freq * t).toFloat()
                buffer[i] = (wave * env * 23000f).toInt().toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playBearGrowl() {
        if (!soundEnabled) return
        vibrateMedium()
        if (soundManager.playFxRaw("fx_bear_growl")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 320
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val rumble = sin(2.0 * PI * 16.0 * t).toFloat()
                val freq = 120f + rumble * 30f
                val env = sin(progress * PI.toFloat())
                val wave = sin(2.0 * PI * freq * t).toFloat() * 0.8f
                val noise = ((Math.random() * 2.0 - 1.0) * 0.2f).toFloat()
                buffer[i] = ((wave + noise) * env * 26000f).toInt().toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playTickleLaughter() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw("fx_tickle_laughter")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 300
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            val giggleSteps = 3
            val stepSize = numSamples / giggleSteps
            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val stepIdx = i % stepSize
                val stepProg = stepIdx.toFloat() / stepSize
                val env = sin(stepProg * PI.toFloat())
                val freq = 700f + (i / stepSize) * 110f + sin(2.0 * PI * 22.0 * t).toFloat() * 50f
                buffer[i] = (sin(2.0 * PI * freq * t) * env * 23000).toInt().toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playEatCrunch() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw("fx_eat_crunch")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 140
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress) * (1.0f - progress)
                val noise = ((Math.random() * 2.0 - 1.0) * 22000).toFloat()
                buffer[i] = (noise * env).toInt().toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    // ==========================================
    // 🎁 BLIND BOX & LOOT BOX SOUND EFFECTS
    // ==========================================

    fun playSquishElastic(pitchMultiplier: Float = 1.0f) {
        if (!soundEnabled) return
        vibrateSquishDeform()
        if (soundManager.playFxRaw("fx_squish_elastic")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 150
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)
            val baseFreq = 160f * pitchMultiplier.coerceIn(0.7f, 1.6f)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val wobble = sin(2.0 * PI * 22.0 * t).toFloat() * 40f
                val freq = baseFreq + wobble - progress * 45f
                val env = sin(progress * PI.toFloat())
                val tone = sin(2.0 * PI * freq * t).toFloat() * 0.7f + sin(2.0 * PI * (freq * 2.5f) * t).toFloat() * 0.2f
                val squelch = ((Math.random() * 2.0 - 1.0) * 0.2f).toFloat()
                buffer[i] = ((tone + squelch) * env * 27000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playMunchCrunchSound() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw("fx_munch_crunch")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 380
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                // Crunchy munch rhythm pulses
                val crunchPulse = sin(2.0 * PI * 18.0 * t).toFloat()
                val noise = ((Math.random() * 2.0 - 1.0) * 0.65f).toFloat()
                val tone = sin(2.0 * PI * (320f + crunchPulse * 80f) * t).toFloat() * 0.35f
                val env = (1.0f - progress) * (if (crunchPulse > 0) 1.0f else 0.3f)
                buffer[i] = ((tone + noise) * env * 25000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playSparkleChime() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw("fx_sparkle_chime")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 380
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)
            val chordFreqs = floatArrayOf(880f, 1174.66f, 1396.91f, 1760f, 2093f)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                var sumWave = 0f
                for (f in chordFreqs) {
                    sumWave += sin(2.0 * PI * f * t).toFloat() * 0.2f
                }
                val shimmer = sin(2.0 * PI * 24.0 * t).toFloat() * 0.15f
                val env = (1.0f - progress) * (1.0f - progress)
                buffer[i] = ((sumWave + shimmer) * env * 26000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playLegendaryFanfare() {
        if (!soundEnabled) return
        vibrateSeries()
        if (soundManager.playFxRaw("fx_legendary_fanfare")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 680
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)
            // Grand arpeggio: C5 -> E5 -> G5 -> C6 -> E6 -> G6 -> C7
            val notes = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.50f, 1318.51f, 1567.98f, 2093.00f)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val noteIdx = (progress * notes.size).toInt().coerceIn(0, notes.size - 1)
                val freq = notes[noteIdx]
                val noteProg = (progress * notes.size) - noteIdx
                val env = (1f - noteProg * 0.4f) * (1f - progress * 0.2f)
                val wave = sin(2.0 * PI * freq * t).toFloat() * 0.7f + sin(2.0 * PI * freq * 2f * t).toFloat() * 0.25f
                buffer[i] = (wave * env * 27000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playCuteCharacterSound(pitchMultiplier: Float = 1.0f) {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw("fx_cute_character", rate = pitchMultiplier)) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 240
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)
            val baseFreq = 520f * pitchMultiplier.coerceIn(0.5f, 2.5f)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val freq = baseFreq + sin(2.0 * PI * 14.0 * t).toFloat() * 60f + (progress * 150f)
                val env = sin(progress * PI.toFloat())
                val wave = sin(2.0 * PI * freq * t).toFloat() * 0.75f + sin(2.0 * PI * freq * 1.5f * t).toFloat() * 0.2f
                buffer[i] = (wave * env * 25000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    // ==========================================
    // 🎉 CELEBRATION & FANFARE
    // ==========================================

    fun playFanfare() {
        if (!soundEnabled) return
        vibrateSeries()
        if (soundManager.playFxRaw("fx_fanfare")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 460
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)
            val notes = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.50f)

            for (i in 0 until numSamples) {
                val noteIdx = ((i.toFloat() / numSamples) * notes.size).toInt().coerceIn(0, notes.size - 1)
                val freq = notes[noteIdx]
                val t = i.toFloat() / sampleRate
                val env = (1.0f - (i.toFloat() / numSamples) * 0.25f)
                buffer[i] = (sin(2.0 * PI * freq * t) * env * 25000).toInt().toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playBabyGiggle() {
        if (!soundEnabled) return
        if (soundManager.playFxRaw("fx_baby_giggle")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 360
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            val giggleSteps = 4
            val stepSize = numSamples / giggleSteps
            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val stepIdx = i % stepSize
                val stepProg = stepIdx.toFloat() / stepSize
                val env = sin(stepProg * PI.toFloat())
                val freq = 650f + (i / stepSize) * 80f + sin(2.0 * PI * 18.0 * t).toFloat() * 40f
                buffer[i] = (sin(2.0 * PI * freq * t) * env * 22000).toInt().toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    // ==========================================
    // 📳 HAPTIC FEEDBACK PATTERNS (HapticFeedbackConstants)
    // ==========================================

    /**
     * Performs subtle, responsive haptic feedback using Android's HapticFeedbackConstants.
     */
    fun performHapticFeedback(constant: Int, view: View? = null) {
        if (!hapticsEnabled) return
        if (view != null) {
            try {
                view.performHapticFeedback(constant)
            } catch (_: Exception) {}
            return
        }
        if (vibrator == null) return
        // Fallback directly to Vibrator / VibrationEffect when View is not attached
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effectId = when (constant) {
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.VIRTUAL_KEY -> VibrationEffect.EFFECT_CLICK
                    HapticFeedbackConstants.CLOCK_TICK -> VibrationEffect.EFFECT_TICK
                    HapticFeedbackConstants.CONFIRM -> VibrationEffect.EFFECT_HEAVY_CLICK
                    HapticFeedbackConstants.REJECT -> VibrationEffect.EFFECT_DOUBLE_CLICK
                    else -> VibrationEffect.EFFECT_CLICK
                }
                vibrator?.vibrate(VibrationEffect.createPredefined(effectId))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val duration = when (constant) {
                    HapticFeedbackConstants.CLOCK_TICK -> 8L
                    HapticFeedbackConstants.KEYBOARD_TAP -> 14L
                    HapticFeedbackConstants.CONFIRM -> 28L
                    else -> 18L
                }
                vibrator?.vibrate(VibrationEffect.createOneShot(duration, 180))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(14L)
            }
        } catch (_: Exception) {}
    }

    /**
     * Triggers subtle squish device vibration using HapticFeedbackConstants.
     */
    fun performSquishHaptic(intensity: Float = 1.0f, view: View? = null) {
        if (!hapticsEnabled) return
        val constant = when {
            intensity > 0.7f -> HapticFeedbackConstants.CONFIRM
            intensity > 0.35f -> HapticFeedbackConstants.KEYBOARD_TAP
            else -> HapticFeedbackConstants.CLOCK_TICK
        }
        performHapticFeedback(constant, view)
    }

    /**
     * Triggers UI touch feedback using HapticFeedbackConstants.KEYBOARD_TAP.
     */
    fun performUiClickHaptic(view: View? = null) {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, view)
    }

    /**
     * Triggers obstacle collision haptic feedback using HapticFeedbackConstants.VIRTUAL_KEY.
     */
    fun performObstacleHitHaptic(view: View? = null) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, view)
    }

    /**
     * Triggers item collection haptic feedback using HapticFeedbackConstants.CONFIRM.
     */
    fun performItemCollectHaptic(view: View? = null) {
        performHapticFeedback(HapticFeedbackConstants.CONFIRM, view)
    }

    // ==========================================
    // 🎮 PELMESH-SIX SOUNDPOOL SOUND EFFECT MANAGER
    // ==========================================

    /**
     * Triggers audio feedback via SoundPool when Pelmesh-Six hits obstacles or screen boundaries.
     */
    fun playPelmeshHitObstacle(intensity: Float = 1.0f) {
        performObstacleHitHaptic()
        soundManager.playPelmeshHitObstacle(intensity)
    }

    /**
     * Triggers audio feedback via SoundPool when Pelmesh-Six collects items (coins, stars, bubbles).
     */
    fun playPelmeshCollectItem(itemType: String = "COIN", pitchMultiplier: Float = 1.0f) {
        performItemCollectHaptic()
        soundManager.playPelmeshCollectItem(itemType, pitchMultiplier)
    }

    fun triggerLightTapPulse() {
        performUiClickHaptic()
    }

    fun vibrateSquish() {
        performSquishHaptic(0.5f)
    }

    fun vibrateSquishDeform() {
        if (!hapticsEnabled || vibrator == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 20, 15, 35)
                val amplitudes = intArrayOf(0, 120, 0, 210)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 20, 15, 35), -1)
            }
        } catch (_: Exception) {}
    }

    fun vibrateBoingRelease() {
        if (!hapticsEnabled || vibrator == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 25, 20, 15)
                val amplitudes = intArrayOf(0, 230, 0, 150)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 25, 20, 15), -1)
            }
        } catch (_: Exception) {}
    }

    fun vibrateDragTick() {
        if (!hapticsEnabled || vibrator == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(8, 100))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(8)
            }
        } catch (_: Exception) {}
    }

    fun vibrateMedium() {
        if (!hapticsEnabled || vibrator == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 35, 20, 45)
                val amplitudes = intArrayOf(0, 160, 0, 200)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 35, 20, 45), -1)
            }
        } catch (_: Exception) {}
    }

    fun vibrateSeries() {
        if (!hapticsEnabled || vibrator == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 30, 35, 30, 35, 50)
                val amplitudes = intArrayOf(0, 150, 0, 190, 0, 240)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 30, 35, 30, 35, 50), -1)
            }
        } catch (_: Exception) {}
    }

    // ==========================================
    // 🌾 GRAIN & POURING SOUNDS
    // ==========================================

    fun playPouringGrain(grainTypeId: String = "rice") {
        if (soundManager.playFxRaw("fx_grain_pour")) return
        playGrainRustle(grainTypeId = grainTypeId, intensity = 1.0f)
    }

    /**
     * Реалистичный звуковой эффект шороха крупы при перемещении или пересыпании.
     * Использует генерацию гранулярного шума с уникальным акустическим профилем
     * для каждого типа крупы (рис, гречка, посыпка, бусины).
     */
    fun playGrainRustle(grainTypeId: String = "rice", intensity: Float = 1.0f) {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw("fx_grain_rustle")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = (180 + (intensity * 120)).toInt()
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples

                val env = sin(progress * PI.toFloat()) * (1.0f - progress * 0.2f)
                val noise = (Math.random() * 2.0 - 1.0).toFloat()

                val sample = when (grainTypeId) {
                    "rice" -> {
                        // Рис: частый мелкий хрустящий шорох зёрен
                        val click = if (Math.random() > 0.82) (Math.random() * 2.0 - 1.0).toFloat() * 1.5f else 0f
                        val freq = 2200f + 600f * sin(2.0 * PI * 60.0 * t).toFloat()
                        (noise * 0.5f + click * 0.35f + sin(2.0 * PI * freq * t) * 0.15f) * env * 22000f
                    }
                    "buckwheat" -> {
                        // Гречка: более плотный, тёплый и глубокий глухой шорох
                        val freq = 850f + 300f * sin(2.0 * PI * 35.0 * t).toFloat()
                        val subNoise = sin(2.0 * PI * (freq * 0.5f) * t).toFloat()
                        (noise * 0.75f + subNoise * 0.25f) * env * 24000f
                    }
                    "sprinkles" -> {
                        // Посыпка: лёгкий звонкий сладкий шорох
                        val freq = 3200f + 800f * sin(2.0 * PI * 80.0 * t).toFloat()
                        val chime = sin(2.0 * PI * (3800f) * t).toFloat() * 0.2f
                        (noise * 0.45f + chime + sin(2.0 * PI * freq * t) * 0.35f) * env * 20000f
                    }
                    "magic_beads" -> {
                        // Бусины: стеклянно-кристаллический перезвон
                        val freq1 = 1800f + (Math.random() * 400).toFloat()
                        val freq2 = 3600f + (Math.random() * 800).toFloat()
                        val ring = (sin(2.0 * PI * freq1 * t) + sin(2.0 * PI * freq2 * t)) * 0.4f
                        (noise * 0.3f + ring * 0.7f) * env * 23000f
                    }
                    else -> {
                        val freq = 1400f + 400f * sin(2.0 * PI * 40.0 * t).toFloat()
                        (noise * 0.65f + sin(2.0 * PI * freq * t) * 0.35f) * env * 20000f
                    }
                }

                buffer[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    /**
     * Легкий стук/звон стеклянной емкости при перемещении или касании.
     */
    fun playJarGlassClink() {
        if (!soundEnabled) return
        if (soundManager.playFxRaw("fx_jar_glass_clink")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 110
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress) * (1.0f - progress)
                val glassFreq = 2800f + sin(2.0 * PI * 120.0 * t).toFloat() * 200f
                val sample = sin(2.0 * PI * glassFreq * t) * env * 22000f
                buffer[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    // ==========================================
    // 🏃‍♂️ COMICAL RUNNING & CARTOON SLAP SOUNDS
    // ==========================================

    fun playCartoonYelp(pitchMultiplier: Float = 1.0f) {
        if (!soundEnabled) return
        vibrateMedium()
        if (soundManager.playFxRaw("fx_cartoon_yelp")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 190
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = sin(progress * PI.toFloat()).coerceAtLeast(0f)
                val pitchCurve = if (progress < 0.35f) {
                    650f + (progress / 0.35f) * 600f
                } else {
                    1250f - ((progress - 0.35f) / 0.65f) * 750f
                }
                val vibrato = sin(2.0 * PI * 24.0 * t).toFloat() * 55f
                val freq = (pitchCurve + vibrato) * pitchMultiplier.coerceIn(0.7f, 1.6f)
                val harmonic = sin(2.0 * PI * freq * t).toFloat() * 0.75f + sin(2.0 * PI * (freq * 2f) * t).toFloat() * 0.25f
                buffer[i] = (harmonic * env * 24000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playComicalFallWobble() {
        if (!soundEnabled) return
        vibrateSeries()
        if (soundManager.playFxRaw("fx_comical_fall_wobble")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 380
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress * 0.7f)
                val slideFreq = 880f - progress * 720f
                val wobble = sin(2.0 * PI * 18.0 * t).toFloat() * 65f * (1.0f - progress)
                val freq = (slideFreq + wobble).coerceAtLeast(60f)
                val wave = sin(2.0 * PI * freq * t).toFloat() * 0.8f + sin(2.0 * PI * (freq * 0.5f) * t).toFloat() * 0.2f
                val thudAtEnd = if (progress > 0.8f) {
                    val thudProg = (progress - 0.8f) / 0.2f
                    sin(2.0 * PI * 75.0 * t).toFloat() * (1.0f - thudProg) * 16000f
                } else 0f
                val total = (wave * env * 20000f + thudAtEnd).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                buffer[i] = total
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playCatapultStretch() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw("fx_catapult_stretch")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 130
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = sin(progress * PI.toFloat()).coerceAtLeast(0f)
                val freq = 240f + progress * 420f + sin(2.0 * PI * 28.0 * t).toFloat() * 35f
                val sample = sin(2.0 * PI * freq * t).toFloat() * env * 22000f
                buffer[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playCatapultLaunch() {
        if (!soundEnabled) return
        vibrateMedium()
        if (soundManager.playFxRaw("fx_catapult_launch")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 240
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress) * (1.0f - progress)
                // Sharp twang snap + whoosh
                val snapFreq = 1400f * (1.0f - progress * 0.75f)
                val twang = sin(2.0 * PI * snapFreq * t).toFloat() * 0.7f
                val whoosh = ((Math.random() * 2.0 - 1.0) * sin(progress * PI).coerceAtLeast(0.0) * 0.35f).toFloat()
                val total = ((twang + whoosh) * env * 25000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                buffer[i] = total
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playCatapultCrashLand() {
        if (!soundEnabled) return
        vibrateSeries()
        if (soundManager.playFxRaw("fx_catapult_crash_land")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 320
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress)
                val bass = sin(2.0 * PI * 95.0 * t).toFloat() * (1.0f - progress * 1.5f).coerceAtLeast(0f) * 0.6f
                val boingFreq = 320f + 160f * sin(2.0 * PI * 18.0 * t).toFloat()
                val boing = sin(2.0 * PI * boingFreq * t).toFloat() * 0.4f
                val total = ((bass + boing) * env * 25000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                buffer[i] = total
            }
            playPcm(buffer, sampleRate)
        }
    }

    // ==========================================
    // 🎯 SCARECROW ARCHERY & DULL IMPACT SOUNDS
    // ==========================================

    /**
     * Realistic wooden bow creak and string straining sound before release.
     * Lets the child feel the preparation and tactile anticipation of drawing the bow.
     */
    fun playBowstringTension() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw("fx_bowstring_tension")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 230
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = sin(progress * PI.toFloat() * 0.95f).coerceAtLeast(0f)

                // 1. Rising string tension pitch (210Hz gliding up to 460Hz)
                val tensionFreq = 210f + progress * 250f + sin(2.0 * PI * 18.0 * t).toFloat() * 20f
                val stringTone = sin(2.0 * PI * tensionFreq * t).toFloat() * 0.65f

                // 2. Wood fiber strain creak (random tiny impulse crackles like bending wood)
                val woodCreak = if (i % 80 in 0..4) {
                    ((Math.random() * 2.0 - 1.0) * 0.45f).toFloat()
                } else 0f

                // 3. Overtone harmonic giving taut bow fiber texture
                val harmonic = sin(2.0 * PI * (tensionFreq * 2.2f) * t).toFloat() * 0.25f * progress

                val sample = ((stringTone + woodCreak + harmonic) * env * 23000f).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playBowShot() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw("fx_bow_shot")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 150
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress) * (1.0f - progress)
                val twangFreq = 960f - progress * 620f
                val twang = sin(2.0 * PI * twangFreq * t).toFloat() * 0.7f
                val whoosh = ((Math.random() * 2.0 - 1.0) * sin(progress * PI).coerceAtLeast(0.0) * 0.35f).toFloat()
                buffer[i] = ((twang + whoosh) * env * 24000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    /**
     * Deep, heavy dull thud impact when an arrow pierces the scarecrow.
     * Features low-frequency sack/wood resonance, snappy straw crunch, and arrow-specific acoustics.
     */
    fun playDullHeavyArrowImpact(arrowType: String = "WOODEN", isBullseye: Boolean = false) {
        if (!soundEnabled) return
        vibrateMedium()
        if (soundManager.playFxRaw("fx_arrow_${arrowType.lowercase()}" + (if (isBullseye) "_bullseye" else ""))) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = if (isBullseye) 360 else 260
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress) * (1.0f - progress)

                // 1. Deep dull thud punch (125Hz gliding to 65Hz)
                val bassFreq = 125f - progress * 60f
                val bassPunch = sin(2.0 * PI * bassFreq * t).toFloat() * 0.75f * (1.0f - progress * 1.8f).coerceAtLeast(0f)

                // 2. Straw sack body resonance (mid frequency 185Hz)
                val bodyResonance = sin(2.0 * PI * 185.0 * t).toFloat() * 0.35f * (1.0f - progress * 2.5f).coerceAtLeast(0f)

                // 3. Transient crunch at the very moment of piercing (first 25ms)
                val crunch = if (progress < 0.15f) {
                    val cProg = progress / 0.15f
                    ((Math.random() * 2.0 - 1.0) * (1.0f - cProg) * 0.55f).toFloat()
                } else 0f

                // 4. Arrow-specific sound nuance
                val arrowNuance = when (arrowType) {
                    "CARROT" -> {
                        // Juicy vegetable crunch
                        if (progress < 0.25f) sin(2.0 * PI * 340.0 * t).toFloat() * 0.3f * (1.0f - progress * 4f) else 0f
                    }
                    "SUCKER" -> {
                        // Rubbery suction pop (chpok)
                        if (progress < 0.12f) sin(2.0 * PI * 650.0 * (1.0f - progress * 5f) * t).toFloat() * 0.4f else 0f
                    }
                    "GOLDEN_STAR" -> {
                        // Bright golden shimmer
                        sin(2.0 * PI * 1800.0 * t).toFloat() * 0.25f * (1.0f - progress)
                    }
                    else -> 0f
                }

                // 5. Bullseye fanfare chime
                val bullseyeChime = if (isBullseye && progress > 0.12f) {
                    val bProg = (progress - 0.12f) / 0.88f
                    (sin(2.0 * PI * 1760.0 * t).toFloat() * 0.35f + sin(2.0 * PI * 2640.0 * t).toFloat() * 0.25f) * (1.0f - bProg)
                } else 0f

                val totalSample = (bassPunch + bodyResonance + crunch + arrowNuance + bullseyeChime) * env * 27000f
                buffer[i] = totalSample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    /**
     * Comical recoil and rebound sound when the scarecrow's wooden post sways back and forth.
     */
    fun playScarecrowRecoilSpring(intensity: Float = 1.0f) {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw("fx_scarecrow_recoil_spring")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 310
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress)

                // Spring wobble modulation (wobbles at 14Hz, descending pitch 280Hz -> 150Hz)
                val baseFreq = 260f - progress * 110f
                val springWobble = sin(2.0 * PI * 15.0 * t).toFloat() * 55f * (1.0f - progress * 0.8f)
                val freq = (baseFreq + springWobble) * intensity.coerceIn(0.7f, 1.4f)

                val wave = sin(2.0 * PI * freq * t).toFloat() * 0.75f + sin(2.0 * PI * (freq * 1.5f) * t).toFloat() * 0.25f
                val total = (wave * env * 22000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                buffer[i] = total
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playScarecrowWobble() {
        playScarecrowRecoilSpring(1.0f)
    }

    // ==========================================
    // 🔒 TODDLER SAFETY & LOCK SOUNDS
    // ==========================================

    // ==========================================
    // 🔮 PINBALL & MARBLE RUN SOUND EFFECTS
    // ==========================================

    /**
     * Spring plunger launch: ascending whoosh and mechanical release boing!
     */
    fun playPinballPlungerRelease() {
        if (!soundEnabled) return
        vibrateMedium()
        if (soundManager.playFxRaw("fx_pinball_plunger")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 280
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = sin(progress * PI.toFloat()).coerceAtLeast(0f)
                // Rising launch whoosh + spring release tone
                val freq = 180f + progress * 750f + sin(2.0 * PI * 24.0 * t).toFloat() * 45f
                val wave = sin(2.0 * PI * freq * t).toFloat() * 0.7f
                val noise = ((Math.random() * 2.0 - 1.0) * 0.25f * (1.0f - progress)).toFloat()
                buffer[i] = ((wave + noise) * env * 25000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    /**
     * Punchy glowing bumper hit with harmonic metallic chime and spring bounce.
     */
    fun playBumperHit(pitchMult: Float = 1.0f) {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw("fx_bumper_hit")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 190
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress) * (1.0f - progress)
                val baseFreq = 520f * pitchMult
                val harmonic = sin(2.0 * PI * baseFreq * t).toFloat() * 0.65f +
                        sin(2.0 * PI * (baseFreq * 2.02f) * t).toFloat() * 0.35f +
                        sin(2.0 * PI * (baseFreq * 3.1f) * t).toFloat() * 0.15f
                val thud = if (progress < 0.15f) sin(2.0 * PI * 110.0 * t).toFloat() * 0.5f else 0f
                buffer[i] = ((harmonic + thud) * env * 26000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    /**
     * Flipper paddle snap kick.
     */
    fun playFlipperSnap() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        if (soundManager.playFxRaw("fx_flipper_snap")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 120
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress)
                val click = sin(2.0 * PI * (340.0 - progress * 160.0) * t).toFloat() * 0.8f
                buffer[i] = (click * env * 22000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    // ==========================================
    // 🤪 СМЕШИНКИ (FUNNY TODDLER SOUNDS)
    // ==========================================

    fun playHeadDizzySpin() {
        if (!soundEnabled) return
        if (soundManager.playFxRaw("fx_head_dizzy_spin")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 600
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                // Ascending twirl + warble
                val warble = sin(2.0 * PI * 18.0 * t).toFloat() * 120f
                val freq = 350f + progress * 550f + warble
                val env = sin(progress * PI.toFloat())
                val sample = (sin(2.0 * PI * freq * t).toFloat() * env * 22000f).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playIdleChuckle() {
        if (!soundEnabled) return
        if (soundManager.playFxRaw("fx_idle_chuckle")) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 180
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val freq = 320f + 60f * sin(2.0 * PI * 14.0 * t).toFloat()
                val env = sin(progress * PI.toFloat())
                val sample = (sin(2.0 * PI * freq * t).toFloat() * env * 14000f).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    // ==========================================
    // 🏖️ КИНЕТИЧЕСКИЙ ПЕСОК (KINETIC SAND ASMR)
    // ==========================================

    // ==========================================
    // 📱 ДЕТСКИЙ ТЕЛЕФОНЧИК (BABY PHONE AUDIO)
    // ==========================================

    // ==========================================
    // ⚙️ PCM RAW AUDIO SINK (STREAMING AUDIOTRACK)
    // ==========================================

    // ВАЖНО: весь доступ к pcmTrack (создание, write, stop, release) — на ОДНОМ потоке.
    // Параллельные write() из разных корутин и release() с главного потока при живом write()
    // дают нативный SIGSEGV в libaudioclient (AudioTrack::releaseBuffer).
    private val pcmExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "pcm-sink").apply { isDaemon = true }
    }
    private var pcmTrack: AudioTrack? = null

    private fun playPcm(buffer: ShortArray, sampleRate: Int) {
        if (!soundEnabled) return
        try {
            pcmExecutor.execute { writePcmOnSinkThread(buffer, sampleRate) }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            // плеер уже освобождён
        }
    }

    private fun writePcmOnSinkThread(buffer: ShortArray, sampleRate: Int) {
        try {
            var track = pcmTrack
            if (track == null) {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuf <= 0) return
                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuf * 2, 8192))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                if (t.state != AudioTrack.STATE_INITIALIZED) {
                    try { t.release() } catch (_: Exception) {}
                    return
                }
                t.play()
                pcmTrack = t
                track = t
            }
            val written = track.write(buffer, 0, buffer.size)
            if (written < 0) {
                // трек «умер» (например, смена аудиоустройства) — пересоздадим при следующем звуке
                try { track.stop() } catch (_: Exception) {}
                try { track.release() } catch (_: Exception) {}
                pcmTrack = null
            }
        } catch (_: Exception) {}
    }

    fun release() {
        soundManager.release("all")
        try {
            // stop/release — на том же потоке, что и write; уже поставленные звуки доиграют
            pcmExecutor.execute {
                try { pcmTrack?.stop() } catch (_: Exception) {}
                try { pcmTrack?.release() } catch (_: Exception) {}
                pcmTrack = null
            }
            pcmExecutor.shutdown()
        } catch (_: java.util.concurrent.RejectedExecutionException) {}
    }
}
