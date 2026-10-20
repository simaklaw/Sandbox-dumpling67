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

    fun playGearRatchet() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        scope.launch {
            val sampleRate = 22050
            val durationMs = 35
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress)
                val woodClick = sin(2.0 * PI * 750.0 * (i.toFloat() / sampleRate)).toFloat() * 22000f
                buffer[i] = (woodClick * env).toInt().toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playDoorOpen() {
        if (!soundEnabled) return
        vibrateMedium()
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

    /**
     * Musical Xylophone note: noteIndex 0..7 (Do, Re, Mi, Fa, Sol, La, Ti, Do)
     */
    fun playXylophoneNote(noteIndex: Int) {
        if (!soundEnabled) return
        triggerLightTapPulse()
        val frequencies = floatArrayOf(
            523.25f, // C5 (До)
            587.33f, // D5 (Ре)
            659.25f, // E5 (Ми)
            698.46f, // F5 (Фа)
            783.99f, // G5 (Соль)
            880.00f, // A5 (Ля)
            987.77f, // B5 (Си)
            1046.50f // C6 (До)
        )
        val note = frequencies[noteIndex.coerceIn(0, 7)]
        scope.launch {
            val sampleRate = 22050
            val durationMs = 240
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress) * (1.0f - progress * 0.5f)
                val fundamental = sin(2.0 * PI * note * t).toFloat() * 0.75f
                val harmonic = sin(2.0 * PI * (note * 3f) * t).toFloat() * 0.25f
                buffer[i] = ((fundamental + harmonic) * env * 26000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
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

    fun playAnimalChorus() {
        if (!soundEnabled) return
        vibrateSeries()
        scope.launch {
            playDogWoof()
            kotlinx.coroutines.delay(160)
            playCatMeow()
            kotlinx.coroutines.delay(180)
            playRoosterCrow()
            kotlinx.coroutines.delay(200)
            playCowMoo()
        }
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

    fun playCatPurr() {
        if (!soundEnabled) return
        vibrateMedium()
        scope.launch {
            val sampleRate = 22050
            val durationMs = 380
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val flutter = sin(2.0 * PI * 28.0 * t).toFloat()
                val freq = 90f + flutter * 25f
                val env = sin(progress * PI.toFloat())
                val purr = sin(2.0 * PI * freq * t).toFloat() * 0.7f + sin(2.0 * PI * (freq * 2f) * t).toFloat() * 0.3f
                buffer[i] = (purr * env * 24000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playDogBark() {
        if (!soundEnabled) return
        vibrateMedium()
        scope.launch {
            val sampleRate = 22050
            val durationMs = 200
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val freq = 360f - 160f * progress
                val env = (1.0f - progress) * (1.0f - progress)
                val noise = ((Math.random() * 2.0 - 1.0) * 0.4f).toFloat()
                val wave = sin(2.0 * PI * freq * t).toFloat() * 0.6f + noise
                buffer[i] = (wave * env * 26000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playBunnyHop() {
        if (!soundEnabled) return
        triggerLightTapPulse()
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

    fun playKissSound() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        scope.launch {
            val sampleRate = 22050
            val durationMs = 120
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val progress = i.toFloat() / numSamples
                val env = sin(progress * PI.toFloat())
                val freq = 900f + 600f * (1f - progress)
                val tone = sin(2.0 * PI * freq * (i.toFloat() / sampleRate)).toFloat() * 0.6f
                val smack = ((Math.random() * 2.0 - 1.0) * 0.4f).toFloat()
                buffer[i] = ((tone + smack) * env * 25000f).toInt().toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playEatCrunch() {
        if (!soundEnabled) return
        triggerLightTapPulse()
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

    fun playBoxTensionHum() {
        if (!soundEnabled) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 320
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                // Rising frequency and swelling amplitude
                val freq = 120f + progress * 240f + sin(2.0 * PI * 18.0 * t).toFloat() * 15f
                val env = progress * (1.0f - progress * 0.15f)
                val tone = sin(2.0 * PI * freq * t).toFloat() * 0.8f
                buffer[i] = (tone * env * 24000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playMunchCrunchSound() {
        if (!soundEnabled) return
        triggerLightTapPulse()
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

    fun playMunchYuckySound() {
        if (!soundEnabled) return
        vibrateSquishDeform()
        scope.launch {
            val sampleRate = 22050
            val durationMs = 280
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                // Downward sliding yucky tone
                val freq = 280f - progress * 160f
                val env = (1.0f - progress)
                val tone = sin(2.0 * PI * freq * t).toFloat() * 0.7f
                val squelch = ((Math.random() * 2.0 - 1.0) * 0.3f).toFloat()
                buffer[i] = ((tone + squelch) * env * 24000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playCoinRewardShower() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        scope.launch {
            val sampleRate = 22050
            val durationMs = 450
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)
            val coinNotes = floatArrayOf(1318.51f, 1567.98f, 2093.00f, 2637.02f)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val noteIdx = ((progress * 8).toInt()) % coinNotes.size
                val freq = coinNotes[noteIdx]
                val subProg = (progress * 8) - (progress * 8).toInt()
                val env = (1.0f - subProg) * (1.0f - progress * 0.3f)
                val wave = sin(2.0 * PI * freq * t).toFloat() * 0.8f
                buffer[i] = (wave * env * 23000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playBoxRattle(tapCount: Int = 1) {
        if (!soundEnabled) return
        vibrateSquish()
        scope.launch {
            val sampleRate = 22050
            val durationMs = 120
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)
            val baseFreq = 180f + tapCount * 45f

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress) * (1.0f - progress)
                val rattleNoise = ((Math.random() * 2.0 - 1.0) * 0.45f).toFloat()
                val thudTone = sin(2.0 * PI * (baseFreq - progress * 60f) * t).toFloat() * 0.55f
                buffer[i] = ((thudTone + rattleNoise) * env * 27000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playBoxPopOpen() {
        if (!soundEnabled) return
        vibrateMedium()
        scope.launch {
            val sampleRate = 22050
            val durationMs = 280
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                // Low pop followed by quick upward chime sweep
                val popFreq = if (progress < 0.25f) 350f - progress * 400f else 600f + progress * 800f
                val env = if (progress < 0.25f) (1f - progress / 0.25f) else (1f - (progress - 0.25f) / 0.75f)
                val wave = sin(2.0 * PI * popFreq * t).toFloat() * 0.7f + sin(2.0 * PI * popFreq * 2f * t).toFloat() * 0.3f
                buffer[i] = (wave * env * 27000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playSparkleChime() {
        if (!soundEnabled) return
        triggerLightTapPulse()
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

    fun vibrateBoxTap(tapCount: Int) {
        if (!hapticsEnabled || vibrator == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                when (tapCount) {
                    1 -> vibrator.vibrate(VibrationEffect.createOneShot(28, 160))
                    2 -> {
                        val timings = longArrayOf(0, 30, 25, 45)
                        val amplitudes = intArrayOf(0, 180, 0, 230)
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    }
                    else -> {
                        val timings = longArrayOf(0, 35, 20, 45, 20, 60)
                        val amplitudes = intArrayOf(0, 190, 0, 230, 0, 255)
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(35L * tapCount)
            }
        } catch (_: Exception) {}
    }

    fun vibrateBoxExplosion() {
        if (!hapticsEnabled || vibrator == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Energetic burst waveform: Initial strong crack followed by sparkling aftershocks
                val timings = longArrayOf(0, 70, 30, 45, 30, 35, 40, 25)
                val amplitudes = intArrayOf(0, 255, 0, 210, 0, 170, 0, 120)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 70, 30, 45, 30, 35, 40, 25), -1)
            }
        } catch (_: Exception) {}
    }

    fun vibratePurr() {
        if (!hapticsEnabled || vibrator == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 45, 45, 55, 45, 45)
                val amplitudes = intArrayOf(0, 110, 0, 130, 0, 100)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 45, 45, 55, 45, 45), -1)
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

    fun playPatterStep(isLeft: Boolean = true) {
        if (!soundEnabled) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 45
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)
            val baseFreq = if (isLeft) 440f else 520f

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress) * (1.0f - progress)
                val freq = baseFreq - progress * 160f
                val pop = sin(2.0 * PI * freq * t).toFloat() * 18000f
                val clickNoise = ((Math.random() * 2.0 - 1.0) * 4500f * (1.0f - progress * 2f).coerceAtLeast(0f)).toFloat()
                buffer[i] = ((pop + clickNoise) * env).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playCartoonYelp(pitchMultiplier: Float = 1.0f) {
        if (!soundEnabled) return
        vibrateMedium()
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

    fun playCatapultFlyingScream() {
        if (!soundEnabled) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 520
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress * 0.8f) * sin(progress * PI.toFloat()).coerceAtLeast(0.1f)
                val screamPitch = if (progress < 0.25f) {
                    800f + (progress / 0.25f) * 850f
                } else {
                    1650f - ((progress - 0.25f) / 0.75f) * 1150f
                }
                val wobble = sin(2.0 * PI * 32.0 * t).toFloat() * 70f
                val freq = screamPitch + wobble
                val wave = sin(2.0 * PI * freq * t).toFloat() * 0.75f + sin(2.0 * PI * (freq * 1.5f) * t).toFloat() * 0.25f
                buffer[i] = (wave * env * 24000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playCatapultCrashLand() {
        if (!soundEnabled) return
        vibrateSeries()
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

    /**
     * Fluttering spring buzz of the arrow shaft vibrating in the wood right after piercing.
     */
    fun playArrowVibrationBuzz() {
        if (!soundEnabled) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 180
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress * 0.9f)
                val flutter = sin(2.0 * PI * 32.0 * t).toFloat() * 40f
                val freq = 360f + flutter
                val wave = sin(2.0 * PI * freq * t).toFloat() * 0.8f
                val sample = (wave * env * 18000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                buffer[i] = sample
            }
            playPcm(buffer, sampleRate)
        }
    }

    /**
     * Funny cartoon grunt / recoil vocal reaction from the scarecrow.
     */
    fun playScarecrowGruntReaction() {
        if (!soundEnabled) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 160
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = sin(progress * PI.toFloat()).coerceAtLeast(0f)
                // Downward cartoon grunt: 420Hz down to 180Hz
                val freq = 420f - progress * 240f + sin(2.0 * PI * 20.0 * t).toFloat() * 25f
                val harmonic = sin(2.0 * PI * freq * t).toFloat() * 0.7f + sin(2.0 * PI * (freq * 2f) * t).toFloat() * 0.3f
                buffer[i] = (harmonic * env * 23000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playArrowPierce(isBullseye: Boolean = false) {
        playDullHeavyArrowImpact("WOODEN", isBullseye)
    }

    fun playScarecrowWobble() {
        playScarecrowRecoilSpring(1.0f)
    }

    // ==========================================
    // 🔒 TODDLER SAFETY & LOCK SOUNDS
    // ==========================================

    fun playLockClick(isLocked: Boolean) {
        if (!soundEnabled) return
        triggerLightTapPulse()
        scope.launch {
            val sampleRate = 22050
            val durationMs = 110
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)
            val freq = if (isLocked) 880f else 660f

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress).coerceAtLeast(0f)
                val sample = (sin(2.0 * PI * freq * t) * env * 22000).toInt().toShort()
                buffer[i] = sample
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playShieldChime() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        scope.launch {
            val sampleRate = 22050
            val durationMs = 240
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress * 0.7f)
                val freq = 523.25f + progress * 260f
                val sample = (sin(2.0 * PI * freq * t) * env * 20000).toInt().toShort()
                buffer[i] = sample
            }
            playPcm(buffer, sampleRate)
        }
    }

    // ==========================================
    // 🔮 PINBALL & MARBLE RUN SOUND EFFECTS
    // ==========================================

    /**
     * Spring plunger launch: ascending whoosh and mechanical release boing!
     */
    fun playPinballPlungerRelease() {
        if (!soundEnabled) return
        vibrateMedium()
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
     * Rapid spinning pinwheel gear clacking sound.
     */
    fun playPinwheelTick() {
        if (!soundEnabled) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 60
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress)
                val freq = 880f + (Math.random() * 200.0 - 100.0).toFloat()
                val wave = sin(2.0 * PI * freq * t).toFloat() * 0.7f
                buffer[i] = (wave * env * 18000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    /**
     * Musical xylophone note on stair ramps (C, D, E, F, G, A, B, High C).
     */
    fun playXylophoneChime(noteIndex: Int) {
        if (!soundEnabled) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 220
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)
            val scaleFreqs = floatArrayOf(523.25f, 587.33f, 659.25f, 698.46f, 783.99f, 880.00f, 987.77f, 1046.50f)
            val freq = scaleFreqs[noteIndex.coerceIn(0, scaleFreqs.size - 1)]

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = (1.0f - progress * 0.85f) * (1.0f - progress)
                val tone = sin(2.0 * PI * freq * t).toFloat() * 0.75f + sin(2.0 * PI * (freq * 2.76f) * t).toFloat() * 0.25f
                buffer[i] = (tone * env * 24000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
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

    /**
     * Vortex swirling pipe slide sound.
     */
    fun playVortexSwirl() {
        if (!soundEnabled) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 300
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = sin(progress * PI.toFloat()).coerceAtLeast(0f)
                val freq = 680f - progress * 420f + sin(2.0 * PI * 18.0 * t).toFloat() * 60f
                val tone = sin(2.0 * PI * freq * t).toFloat() * 0.7f
                buffer[i] = (tone * env * 21000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    /**
     * Big Jackpot celebration when ball reaches the golden bucket or trigger jackpot.
     */
    fun playJackpotFanfare() {
        if (!soundEnabled) return
        vibrateMedium()
        scope.launch {
            val sampleRate = 22050
            val notes = listOf(523.25f, 659.25f, 783.99f, 1046.50f)
            for (f in notes) {
                val numSamples = (90 * sampleRate) / 1000
                val buffer = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / numSamples
                    val env = (1.0f - progress)
                    val tone = sin(2.0 * PI * f * t).toFloat() * 0.8f
                    buffer[i] = (tone * env * 24000f).toInt().toShort()
                }
                playPcm(buffer, sampleRate)
                kotlinx.coroutines.delay(70)
            }
        }
    }

    /**
     * Pelmen caught in tag game - squish-pop with musical chime.
     */
    fun playPelmenTagCaught(pitch: Float = 1.0f) {
        if (!soundEnabled) return
        vibrateSquishDeform()
        scope.launch {
            val sampleRate = 22050
            val durationMs = 180
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)
            val baseFreq = 520f * pitch.coerceIn(0.7f, 1.8f)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val freq = baseFreq + 400f * sin(progress * PI.toFloat()).toFloat()
                val env = (1.0f - progress * 0.8f)
                val sample = (sin(2.0 * PI * freq * t) * env * 24000).toInt().toShort()
                buffer[i] = sample
            }
            playPcm(buffer, sampleRate)
        }
    }

    /**
     * Funny rapid giggle and cheer sound when Pelmen runs and escapes.
     */
    fun playPelmenGiggleRun() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        scope.launch {
            val sampleRate = 22050
            val notes = listOf(580f, 720f, 650f, 880f)
            for (freq in notes) {
                val numSamples = (45 * sampleRate) / 1000
                val buffer = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / sampleRate
                    val env = sin((i.toFloat() / numSamples) * PI.toFloat())
                    val sample = (sin(2.0 * PI * freq * t) * env * 20000).toInt().toShort()
                    buffer[i] = sample
                }
                playPcm(buffer, sampleRate)
                kotlinx.coroutines.delay(35)
            }
        }
    }

    /**
     * Power-up freeze ice crystal sound.
     */
    fun playFreezeIceCrystal() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        scope.launch {
            val sampleRate = 22050
            val durationMs = 300
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val freq = 1200f + sin(2.0 * PI * 28.0 * t).toFloat() * 250f + progress * 600f
                val env = (1.0f - progress)
                val sample = (sin(2.0 * PI * freq * t) * env * 22000).toInt().toShort()
                buffer[i] = sample
            }
            playPcm(buffer, sampleRate)
        }
    }

    /**
     * Cartoon speed dash whoosh.
     */
    fun playSpeedDashWhoosh() {
        if (!soundEnabled) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 160
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val freq = 200f + progress * 700f
                val noise = (kotlin.random.Random.nextFloat() * 2f - 1f) * 0.25f
                val tone = sin(2.0 * PI * freq * t).toFloat() * 0.75f
                val env = sin(progress * PI.toFloat())
                buffer[i] = ((tone + noise) * env * 23000f).toInt().toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    // ==========================================
    // 🤪 СМЕШИНКИ (FUNNY TODDLER SOUNDS)
    // ==========================================

    fun playSneezeApchhi() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        scope.launch {
            val sampleRate = 22050
            // Inhale then explosive sneeze
            val durationMs = 500
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val sample = if (progress < 0.35f) {
                    // Inhale: rising whistling hiss
                    val p = progress / 0.35f
                    val freq = 400f + p * 800f
                    val noise = (kotlin.random.Random.nextFloat() * 2f - 1f) * 0.4f
                    val tone = sin(2.0 * PI * freq * t).toFloat() * 0.6f
                    ((tone + noise) * p * 16000f).toInt()
                } else {
                    // Explosive ACHOO + decay
                    val p = (progress - 0.35f) / 0.65f
                    val freq = 750f - p * 450f
                    val noise = (kotlin.random.Random.nextFloat() * 2f - 1f) * 0.6f
                    val tone = sin(2.0 * PI * freq * t).toFloat() * 0.4f
                    val env = (1.0f - p) * (1.0f - p)
                    ((tone + noise) * env * 28000f).toInt()
                }
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playBellyInhalePop() {
        if (!soundEnabled) return
        scope.launch {
            val sampleRate = 22050
            val durationMs = 650
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val sample = if (progress < 0.75f) {
                    // Long puff expansion
                    val p = progress / 0.75f
                    val freq = 120f + p * 180f
                    val wave = sin(2.0 * PI * freq * t).toFloat()
                    (wave * p * 18000f).toInt()
                } else {
                    // Sharp pop / release
                    val p = (progress - 0.75f) / 0.25f
                    val freq = 600f - p * 350f
                    val env = (1.0f - p) * (1.0f - p)
                    (sin(2.0 * PI * freq * t).toFloat() * env * 26000f).toInt()
                }
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playTongueBleee() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        scope.launch {
            val sampleRate = 22050
            val durationMs = 450
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                // Springy tongue raspberry with rapid amplitude flutter
                val flutter = sin(2.0 * PI * 28.0 * t).toFloat()
                val freq = 220f + 50f * sin(2.0 * PI * 12.0 * t).toFloat()
                val noise = (kotlin.random.Random.nextFloat() * 2f - 1f) * 0.35f
                val tone = sin(2.0 * PI * freq * t).toFloat()
                val env = sin(progress * PI.toFloat()) * (0.6f + 0.4f * flutter)
                val sample = ((tone + noise) * env * 24000f).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playHeadDizzySpin() {
        if (!soundEnabled) return
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

    fun playButtFallLaugh() {
        if (!soundEnabled) return
        vibrateMedium()
        scope.launch {
            val sampleRate = 22050
            // Heavy soft thud
            val durationMs = 280
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val freq = 140f - progress * 90f
                val env = (1.0f - progress) * (1.0f - progress)
                val noise = (kotlin.random.Random.nextFloat() * 2f - 1f) * 0.2f
                val sample = ((sin(2.0 * PI * freq * t).toFloat() + noise) * env * 27000f).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
            kotlinx.coroutines.delay(200)
            playBabyGiggle()
        }
    }

    fun playHiccupSeries() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        scope.launch {
            for (step in 0 until 4) {
                val sampleRate = 22050
                val durationMs = 80
                val numSamples = (durationMs * sampleRate) / 1000
                val buffer = ShortArray(numSamples)
                val baseFreq = 480f + step * 40f

                for (i in 0 until numSamples) {
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / numSamples
                    val freq = baseFreq + progress * 260f
                    val env = (1.0f - progress) * (1.0f - progress)
                    val sample = (sin(2.0 * PI * freq * t).toFloat() * env * 25000f).toInt()
                    buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                playPcm(buffer, sampleRate)
                kotlinx.coroutines.delay(220)
            }
        }
    }

    fun playIdleChuckle() {
        if (!soundEnabled) return
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

    fun playSandCrunch() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        scope.launch {
            val sampleRate = 22050
            val durationMs = 120
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                // Soft ASMR grain texture crunch
                val noise = (kotlin.random.Random.nextFloat() * 2f - 1f) * 0.7f
                val tone = sin(2.0 * PI * 180.0 * t).toFloat() * 0.3f
                val env = (1.0f - progress) * (1.0f - progress)
                val sample = ((noise + tone) * env * 22000f).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playMoldStamp() {
        if (!soundEnabled) return
        vibrateMedium()
        scope.launch {
            val sampleRate = 22050
            val durationMs = 160
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val freq = 220f - progress * 100f
                val noise = (kotlin.random.Random.nextFloat() * 2f - 1f) * 0.3f
                val tone = sin(2.0 * PI * freq * t).toFloat() * 0.7f
                val env = sin(progress * PI.toFloat())
                val sample = ((tone + noise) * env * 25000f).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    // ==========================================
    // 📱 ДЕТСКИЙ ТЕЛЕФОНЧИК (BABY PHONE AUDIO)
    // ==========================================

    fun playPhoneDialTone(digit: Int) {
        if (!soundEnabled) return
        triggerLightTapPulse()
        scope.launch {
            val sampleRate = 22050
            val durationMs = 110
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            // Musical dial tone frequencies for toddler keys
            val lowFreq = 697f + (digit % 3) * 80f
            val highFreq = 1209f + (digit / 3) * 110f

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val env = sin(progress * PI.toFloat())
                val wave = sin(2.0 * PI * lowFreq * t).toFloat() * 0.5f +
                           sin(2.0 * PI * highFreq * t).toFloat() * 0.5f
                val sample = (wave * env * 24000f).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playPhoneRingtone() {
        if (!soundEnabled) return
        vibrateMedium()
        scope.launch {
            val sampleRate = 22050
            val durationMs = 350
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                // Joyful telephone bell oscillation
                val bellFreq = if (progress < 0.5f) 850f else 1050f
                val env = sin(progress * PI.toFloat())
                val sample = (sin(2.0 * PI * bellFreq * t).toFloat() * env * 24000f).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)
        }
    }

    fun playPhoneHangup() {
        if (!soundEnabled) return
        triggerLightTapPulse()
        scope.launch {
            val sampleRate = 22050
            for (step in 0 until 3) {
                val durationMs = 80
                val numSamples = (durationMs * sampleRate) / 1000
                val buffer = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / numSamples
                    val env = sin(progress * PI.toFloat())
                    val sample = (sin(2.0 * PI * 425.0 * t).toFloat() * env * 20000f).toInt()
                    buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                playPcm(buffer, sampleRate)
                kotlinx.coroutines.delay(120)
            }
        }
    }

    fun playAlloGreeting(animal: String) {
        if (!soundEnabled) return
        scope.launch {
            // First: cute high-pitched "Allo-allo!" cheerful chime tone
            val sampleRate = 22050
            val durationMs = 240
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples
                val freq = 520f + sin(progress * PI.toFloat() * 2f) * 160f
                val env = sin(progress * PI.toFloat())
                val sample = (sin(2.0 * PI * freq * t).toFloat() * env * 22000f).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(buffer, sampleRate)

            kotlinx.coroutines.delay(260)

            // Then: corresponding animal sound
            when (animal.lowercase()) {
                "dog", "собачка", "щенок" -> playDogBark()
                "cat", "котик", "котёнок" -> playCatMeow()
                "cow", "коровка" -> playCowMoo()
                "frog", "лягушка" -> playFrogRibbit()
                "duck", "утёнок", "утка" -> playGooseHonk()
                "bear", "мишка" -> playBearGrowl()
                else -> playTickleLaughter()
            }

            kotlinx.coroutines.delay(350)
            playBabyGiggle()
        }
    }

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
