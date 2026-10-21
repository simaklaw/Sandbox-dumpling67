package com.example.shared

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.util.Log
import com.example.audio.SoundCategory
import com.example.audio.SoundCatalog
import com.example.audio.SoundEntry
import com.example.audio.SoundEffectPlayer
import com.example.audio.SoundResourceResolver
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Handle to an actively running audio loop.
 */
class LoopHandle(
    val name: String,
    val soundId: Int,
    var streamId: Int,
    var targetVolume: Float,
    var currentVolume: Float,
    var isFadingOut: Boolean = false
)

/**
 * Next-Generation Unified SoundManager for "Детская площадка" (Step 9).
 *
 * Implements:
 * - Low-latency SoundPool (USAGE_GAME, CONTENT_TYPE_SONIFICATION, maxStreams = 10)
 * - Safe missing-file handling (zero crashes on missing res/raw files)
 * - Priority-based mixing & voice ducking (40% reduction of SFX during VOICE)
 * - Group-based lazy preloading (background coroutines)
 * - Non-repeating variant randomization
 * - Pitch jitter for SFX diversity
 * - Loop stream management with 150ms smooth fade-out
 * - 3-category parent control (SFX, VOICE, ANIMAL) + Master Volume
 * - Audio focus management
 */
class SoundManager(
    private val context: Context,
    private var soundEffectPlayer: SoundEffectPlayer? = null
) {
    companion object {
        private const val TAG = "SoundManager"
        private const val PREFS_NAME = "playground_prefs"
        private const val KEY_SFX_ENABLED = "pref_sound_sfx_enabled"
        private const val KEY_VOICE_ENABLED = "pref_sound_voice_enabled"
        private const val KEY_ANIMAL_ENABLED = "pref_sound_animal_enabled"
        private const val KEY_MASTER_VOLUME = "pref_sound_master_volume"
        private const val KEY_MUTED = "pref_sound_muted"

        @Volatile
        private var instance: SoundManager? = null

        fun getInstance(context: Context, soundEffectPlayer: SoundEffectPlayer? = null): SoundManager {
            return instance?.also {
                if (soundEffectPlayer != null && it.soundEffectPlayer == null) {
                    it.attachSoundEffectPlayer(soundEffectPlayer)
                }
            } ?: synchronized(this) {
                instance?.also {
                    if (soundEffectPlayer != null && it.soundEffectPlayer == null) {
                        it.attachSoundEffectPlayer(soundEffectPlayer)
                    }
                } ?: run {
                    val sm = SoundManager(context.applicationContext, soundEffectPlayer)
                    instance = sm
                    sm
                }
            }
        }
    }

    fun attachSoundEffectPlayer(player: SoundEffectPlayer) {
        if (this.soundEffectPlayer != player) {
            this.soundEffectPlayer = player
            applyMuteState()
        }
    }

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // SoundPool engine
    private var soundPool: SoundPool? = null
    private val soundIdMap = ConcurrentHashMap<String, Int>() // fileName -> soundId
    private val loadedSoundIds = ConcurrentHashMap.newKeySet<Int>()
    private val missingFilesLogged = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var isSoundPoolSupported = true
    private var consecutiveLoadFailures = 0

    // Concurrency and rate limiting
    private val lastPlayTimeMap = ConcurrentHashMap<String, Long>()
    private val lastVariantIndexMap = ConcurrentHashMap<String, Int>()
    private val activeStreams = ConcurrentHashMap<Int, StreamInfo>() // streamId -> info

    // Active Loops
    // ВАЖНО: ConcurrentHashMap не принимает null-значения — activeLoops[handle] = null вызывал NPE
    // при любом startLoop() (вылет игр с зацикленными звуками). Используем синхронизированную HashMap;
    // LoopHandle — обычный класс (сравнение по ссылке), т.к. его поля меняются и хеш не должен «плыть».
    private val activeLoops: MutableMap<LoopHandle, Job?> = java.util.Collections.synchronizedMap(HashMap())

    // Ducking state
    private var activeVoiceCount = 0
    private var isDucked = false

    // Category enable flags & Master Volume
    var isSfxEnabled: Boolean = prefs.getBoolean(KEY_SFX_ENABLED, true)
        private set
    var isSoundEnabled: Boolean
        get() = isSfxEnabled
        set(value) {
            isSfxEnabled = value
        }

    fun toggleSound(): Boolean {
        val newState = !isSoundEnabled
        isSoundEnabled = newState
        return newState
    }
    var isVoiceEnabled: Boolean = prefs.getBoolean(KEY_VOICE_ENABLED, true)
        private set
    var isAnimalEnabled: Boolean = prefs.getBoolean(KEY_ANIMAL_ENABLED, true)
        private set
    var masterVolume: Float = prefs.getFloat(KEY_MASTER_VOLUME, 0.7f).coerceIn(0f, 1f)
        private set
    private var _isMuted: Boolean = prefs.getBoolean(KEY_MUTED, false)
    val isMuted: Boolean get() = _isMuted

    fun setMuted(muted: Boolean) {
        _isMuted = muted
        prefs.edit().putBoolean(KEY_MUTED, muted).apply()
        applyMuteState()
        if (muted) {
            stopAll()
        }
    }

    data class StreamInfo(
        val name: String,
        val category: SoundCategory,
        val baseVolume: Float,
        val startTime: Long
    )

    init {
        initSoundPool()
        if (soundEffectPlayer != null) {
            try {
                soundEffectPlayer?.soundEnabled = !isMuted && isSfxEnabled
            } catch (e: Exception) {
                Log.w(TAG, "Error initializing soundEnabled on soundEffectPlayer", e)
            }
        }
    }

    private fun initSoundPool() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(audioAttributes)
                .build().apply {
                    setOnLoadCompleteListener { _, sampleId, status ->
                        if (status == 0) {
                            loadedSoundIds.add(sampleId)
                            consecutiveLoadFailures = 0
                        } else {
                            consecutiveLoadFailures++
                            Log.w(TAG, "Sound sample $sampleId failed to load (status: $status)")
                            if (consecutiveLoadFailures >= 2) {
                                isSoundPoolSupported = false
                                Log.i(TAG, "SoundPool codec decoding unavailable in this environment, using pure procedural audio engine.")
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "SoundPool not available, using procedural audio fallback", e)
            isSoundPoolSupported = false
        }
    }

    // ==========================================
    // 🎵 PRELOADING & LIFECYCLE
    // ==========================================

    /**
     * Preloads all sound files for a specific screen group asynchronously with safe sequential pacing.
     */
    fun preload(group: String) {
        if (!isSoundPoolSupported) return
        managerScope.launch(Dispatchers.IO) {
            val entriesToLoad = SoundCatalog.entries.filter { it.group == group || group == "all" }
            for (entry in entriesToLoad) {
                if (!isSoundPoolSupported) break
                for (fileName in entry.files) {
                    if (!isSoundPoolSupported) break
                    if (soundIdMap.containsKey(fileName)) continue
                    loadRawSoundFile(fileName)
                    delay(200L) // Safe pacing prevents Codec2 decoder exhaustion
                }
            }
        }
    }

    fun playPelmeshHitObstacle(intensity: Float = 1.0f) {
        play("thud", volume = intensity)
    }

    fun playPelmeshCollectItem(itemType: String = "star", pitchMultiplier: Float = 1.0f) {
        play("star_collect", rate = pitchMultiplier)
    }

    /**
     * Unloads sound files for a specific screen group to reclaim memory.
     */
    fun release(group: String) {
        if (group == "shared") return // keep shared in memory
        managerScope.launch(Dispatchers.IO) {
            val entriesToUnload = SoundCatalog.entries.filter { it.group == group }
            for (entry in entriesToUnload) {
                for (fileName in entry.files) {
                    val soundId = soundIdMap.remove(fileName)
                    if (soundId != null) {
                        loadedSoundIds.remove(soundId)
                        soundPool?.unload(soundId)
                    }
                }
            }
        }
    }

    private fun loadRawSoundFile(fileName: String): Int? {
        if (!isSoundPoolSupported) return null

        val existing = soundIdMap[fileName]
        if (existing != null) return existing

        val resId = SoundResourceResolver.getRawResId(appContext, fileName)
        if (resId == 0) {
            if (missingFilesLogged.add(fileName)) {
                Log.d(TAG, "Audio asset 'res/raw/$fileName.ogg' not found. Playback will use procedural fallback.")
            }
            return null
        }

        return try {
            val pool = soundPool ?: return null
            val soundId = pool.load(appContext, resId, 1)
            soundIdMap[fileName] = soundId
            soundId
        } catch (e: Exception) {
            consecutiveLoadFailures++
            if (consecutiveLoadFailures >= 2) {
                isSoundPoolSupported = false
            }
            Log.w(TAG, "Error loading sound file $fileName", e)
            null
        }
    }

    // ==========================================
    // 🔊 PUBLIC PLAYBACK API
    // ==========================================

    /**
     * Plays a sound by its logical catalog name.
     * Respects categories, volume, priorities, pitch jitter, and concurrent limits.
     *
     * @return true if playback was initiated, false if ignored/skipped.
     */
    fun play(name: String, volume: Float = 1.0f, rate: Float = 1.0f): Boolean {
        if (isMuted || masterVolume <= 0.001f) return false

        val entry = SoundCatalog.find(name)
        val category = entry?.category ?: SoundCategory.SFX

        // 1. Check category enablement
        if (!isCategoryEnabled(category)) return false

        val now = System.currentTimeMillis()

        // 2. Check minimum interval
        if (entry != null && entry.minIntervalMs > 0) {
            val lastTime = lastPlayTimeMap[entry.name] ?: 0L
            if (now - lastTime < entry.minIntervalMs) {
                return false
            }
        }

        // 3. Check concurrent stream limits
        if (entry != null && entry.maxConcurrent > 0) {
            val count = activeStreams.values.count { it.name == entry.name }
            if (count >= entry.maxConcurrent) {
                return false
            }
        }

        // 4. Variant Selection (non-repeating)
        val fileName = if (entry != null && entry.files.isNotEmpty()) {
            val files = entry.files
            if (files.size == 1) {
                files[0]
            } else {
                val lastIdx = lastVariantIndexMap[entry.name] ?: -1
                val availableIndices = files.indices.filter { it != lastIdx }
                val selectedIdx = if (availableIndices.isNotEmpty()) {
                    availableIndices[Random.nextInt(availableIndices.size)]
                } else {
                    Random.nextInt(files.size)
                }
                lastVariantIndexMap[entry.name] = selectedIdx
                files[selectedIdx]
            }
        } else {
            name
        }

        // Update rate limiting timestamp
        if (entry != null) {
            lastPlayTimeMap[entry.name] = now
        }

        // 5. Calculate pitch / rate
        val calculatedRate = if (category == SoundCategory.SFX && entry != null && entry.pitchJitter > 0f) {
            val jitter = (Random.nextFloat() * 2f - 1f) * entry.pitchJitter
            (rate * (1.0f + jitter)).coerceIn(0.5f, 2.0f)
        } else {
            rate.coerceIn(0.5f, 2.0f)
        }

        // 6. Calculate Effective Volume
        val baseEntryVolume = entry?.volume ?: 1.0f
        val duckingFactor = if (isDucked && category == SoundCategory.SFX) 0.6f else 1.0f
        val finalVol = (volume * baseEntryVolume * masterVolume * duckingFactor).coerceIn(0f, 1f)

        // 7. Try playing from SoundPool
        var soundId = soundIdMap[fileName]
        if (soundId == null) {
            soundId = loadRawSoundFile(fileName)
        }

        if (soundId != null && loadedSoundIds.contains(soundId)) {
            requestAudioFocus()
            val priorityInt = when (category) {
                SoundCategory.VOICE -> 3
                SoundCategory.ANIMAL -> 2
                SoundCategory.SFX -> 1
            }

            val streamId = soundPool?.play(
                soundId,
                finalVol,
                finalVol,
                priorityInt,
                0, // 0 = no loop
                calculatedRate
            ) ?: 0

            if (streamId > 0) {
                activeStreams[streamId] = StreamInfo(
                    name = entry?.name ?: name,
                    category = category,
                    baseVolume = volume * baseEntryVolume * masterVolume,
                    startTime = now
                )

                // Voice ducking trigger
                if (category == SoundCategory.VOICE) {
                    onVoiceStart()
                    managerScope.launch {
                        delay(1200) // approximate voice length
                        onVoiceEnd()
                    }
                }
                return true
            }
        }

        // Procedural synthesis fallback (if soundEffectPlayer exists and file was missing)
        if (soundEffectPlayer != null && (soundId == null || !loadedSoundIds.contains(soundId))) {
            playProceduralFallback(name)
            return true
        }

        return false
    }

    private fun playProceduralFallback(name: String) {
        val player = soundEffectPlayer ?: return
        try {
            when (name.lowercase().trim()) {
                "tap", "click" -> player.playBubblePop()
                "pop" -> player.playPop()
                "bubble" -> player.playBubblePop()
                "success", "chime" -> player.playChime(1.2f)
                "sparkle", "magic", "star_burst" -> player.playMagicSparkle()
                "soft_no", "refuse" -> player.playSquish(0.8f)
                "whoosh", "whoosh_soft", "transition" -> player.playZoneTransition()
                "boing", "release_boing", "frog_boing" -> player.playBoing()
                "squeak", "poke" -> player.playSqueak()
                "clunk", "lever_clack" -> player.playLeverClunk()
                "switch_on", "switch_off" -> player.playSwitchClick()
                "bell", "bell_ding" -> player.playBicycleBell()
                "splash", "splash_small" -> player.playSplash()
                "squish" -> player.playSquish()
                "shoot" -> player.playBowShot()
                "arrow_hit", "thud", "wall" -> player.playPop(0.8f)
                "carrot_hit" -> player.playDullHeavyArrowImpact("CARROT")
                "star_hit", "star_collect" -> player.playSparkleChime()
                "fanfare_short", "fanfare", "rainbow_salute" -> player.playFanfare()
                "animal_dog", "animal_dog_1", "animal_dog_2", "animal_dog_happy" -> player.playAnimalSound("dog")
                "animal_cat", "animal_cat_1", "animal_cat_2", "animal_cat_happy" -> player.playAnimalSound("cat")
                "animal_cow", "animal_cow_1", "animal_cow_2" -> player.playAnimalSound("cow")
                "animal_horse", "animal_horse_1", "animal_horse_2" -> player.playAnimalSound("horse")
                "animal_pig", "animal_pig_1", "animal_pig_2" -> player.playAnimalSound("pig")
                "animal_sheep", "animal_sheep_1", "animal_sheep_2" -> player.playAnimalSound("sheep")
                "animal_goose", "animal_goose_1", "animal_goose_2" -> player.playAnimalSound("goose")
                "animal_rooster", "animal_rooster_1", "animal_rooster_2" -> player.playAnimalSound("rooster")
                "animal_frog", "animal_frog_1", "animal_frog_2" -> player.playAnimalSound("frog")
                "animal_crow", "animal_crow_1", "animal_crow_2" -> player.playAnimalSound("crow")
                "animal_duck", "animal_duck_1", "animal_duck_2" -> player.playAnimalSound("goose")
                "animal_bunny", "animal_bunny_1", "animal_bunny_2", "animal_bunny_happy" -> player.playBunnyHop()
                "balloon_pop", "balloon_pop_1", "balloon_pop_2", "balloon_pop_3" -> player.playPop()
                "praise", "praise_1", "praise_2", "praise_3" -> player.playBabyGiggle()
                "peekaboo", "peekaboo_1", "peekaboo_2" -> player.playCuteCharacterSound()
                "yum", "yum_voice" -> player.playMunchCrunchSound()
                "eat_in", "chew", "chew_1", "chew_2" -> player.playEatCrunch()
                "pickup", "open" -> player.playBubblePop()
                "stir" -> player.playStir()
                "glug" -> player.playGulpDrink()
                "tea_whistle", "steam" -> player.playTeaWhistle()
                "clink" -> player.playJarGlassClink()
                "confetti_rain", "firework" -> player.playLegendaryFanfare()
                "train_toot" -> player.playBicycleBell()
                "jingle_lights" -> player.playSparkleChime()
                "knob_tick" -> player.playArcadeButton()
                "door_creak", "door_close" -> player.playDoorOpen()
                "stretch" -> player.playCatapultStretch()
                "wear", "gift" -> player.playSquishElastic()
                "giggle", "scarecrow_giggle" -> player.playTickleLaughter()
                "dizzy" -> player.playHeadDizzySpin()
                "yawn", "lullaby_short" -> player.playIdleChuckle()
                "bow_pull" -> player.playBowstringTension()
                "wobble" -> player.playScarecrowWobble()
                "bumper" -> player.playBumperHit()
                "sling" -> player.playCatapultStretch()
                "flip" -> player.playFlipperSnap()
                "draw_loop", "stamp" -> player.playCleanSponge()
                "grain_pour", "grain_scoop" -> player.playPouringGrain()
                "voice_hint_tap", "voice_hint_drag", "voice_hint_look" -> player.playChime(1.1f)
                "voice_arrow_normal", "voice_arrow_carrot", "voice_arrow_star" -> player.playChime(1.3f)
                "voice_name_cat", "voice_name_cow", "voice_name_dog", "voice_name_duck", "voice_name_frog", "voice_name_pig" -> player.playCuteCharacterSound()
                else -> {
                    val key = name.lowercase().trim()
                    when {
                        key.contains("pop") -> player.playPop()
                        key.contains("bubble") || key.contains("tap") || key.contains("click") -> player.playBubblePop()
                        key.contains("squish") -> player.playSquish()
                        key.contains("boing") -> player.playBoing()
                        key.contains("chime") || key.contains("star") -> player.playChime()
                        key.contains("animal") -> player.playAnimalSound(key)
                        key.contains("whoosh") -> player.playZoneTransition()
                        key.contains("bell") -> player.playBicycleBell()
                        else -> {
                            player.playBubblePop()
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // ==========================================
    // 🔄 LOOP ENGINE
    // ==========================================

    /**
     * Starts an audio loop (e.g., bow_pull, grain_pour, draw_loop, lever_hum).
     */
    fun startLoop(name: String): LoopHandle? {
        if (isMuted || !isSfxEnabled || masterVolume <= 0.001f) return null

        val entry = SoundCatalog.find(name)
        val fileName = entry?.files?.firstOrNull() ?: name

        var soundId = soundIdMap[fileName]
        if (soundId == null) {
            soundId = loadRawSoundFile(fileName)
        }

        val baseVol = (entry?.volume ?: 1.0f) * masterVolume
        val handle = LoopHandle(
            name = name,
            soundId = soundId ?: 0,
            streamId = 0,
            targetVolume = baseVol,
            currentVolume = baseVol
        )

        if (soundId != null && loadedSoundIds.contains(soundId)) {
            requestAudioFocus()
            val streamId = soundPool?.play(
                soundId,
                baseVol,
                baseVol,
                1,
                -1, // Loop indefinitely
                1.0f
            ) ?: 0
            handle.streamId = streamId
        }

        activeLoops[handle] = null
        return handle
    }

    /**
     * Adjusts the volume of an active loop smoothly (e.g. based on pull force or speed).
     */
    fun setLoopVolume(handle: LoopHandle, volume: Float) {
        if (handle.isFadingOut) return
        val entry = SoundCatalog.find(handle.name)
        val baseVol = (entry?.volume ?: 1.0f) * masterVolume
        val target = (volume * baseVol).coerceIn(0f, 1f)
        handle.targetVolume = target
        handle.currentVolume = target

        if (handle.streamId > 0 && soundPool != null) {
            val duckingFactor = if (isDucked) 0.6f else 1.0f
            val effVol = target * duckingFactor
            soundPool?.setVolume(handle.streamId, effVol, effVol)
        }
    }

    /**
     * Stops an active loop with a smooth fade-out (default 150ms).
     */
    fun stopLoop(handle: LoopHandle, fadeMs: Int = 150) {
        if (handle.isFadingOut) return
        handle.isFadingOut = true

        val job = managerScope.launch {
            if (handle.streamId > 0 && soundPool != null && fadeMs > 0) {
                val steps = 8
                val stepTime = (fadeMs / steps).toLong()
                val startVol = handle.currentVolume
                for (i in 1..steps) {
                    delay(stepTime)
                    val v = startVol * (1f - (i.toFloat() / steps))
                    soundPool?.setVolume(handle.streamId, v, v)
                }
                soundPool?.stop(handle.streamId)
            } else if (handle.streamId > 0) {
                soundPool?.stop(handle.streamId)
            }
            activeLoops.remove(handle)
        }
        activeLoops[handle] = job
    }

    /**
     * Stops all sounds, streams, and loops immediately.
     */
    fun stopAll() {
        try {
            val snapshot = synchronized(activeLoops) { activeLoops.entries.map { it.key to it.value } }
            for ((handle, job) in snapshot) {
                job?.cancel()
                if (handle.streamId > 0) {
                    soundPool?.stop(handle.streamId)
                }
            }
            activeLoops.clear()

            soundPool?.autoPause()
            activeStreams.clear()
            isDucked = false
            activeVoiceCount = 0
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping all audio", e)
        }
    }

    // ==========================================
    // 🦆 VOICE DUCKING SYSTEM
    // ==========================================

    private fun onVoiceStart() {
        activeVoiceCount++
        if (!isDucked) {
            isDucked = true
            applyDucking(0.6f) // Duck SFX by 40%
        }
    }

    private fun onVoiceEnd() {
        activeVoiceCount = max(0, activeVoiceCount - 1)
        if (activeVoiceCount == 0 && isDucked) {
            isDucked = false
            applyDucking(1.0f) // Restore SFX volume
        }
    }

    private fun applyDucking(factor: Float) {
        managerScope.launch {
            // Smoothly adjust SFX streams and loops over 80ms
            for (handle in synchronized(activeLoops) { activeLoops.keys.toList() }) {
                if (handle.streamId > 0 && !handle.isFadingOut) {
                    val v = handle.currentVolume * factor
                    soundPool?.setVolume(handle.streamId, v, v)
                }
            }
        }
    }

    // ==========================================
    // ⚙️ CATEGORY & MASTER SETTINGS
    // ==========================================

    fun setCategoryEnabled(category: SoundCategory, enabled: Boolean) {
        when (category) {
            SoundCategory.SFX -> {
                isSfxEnabled = enabled
                prefs.edit().putBoolean(KEY_SFX_ENABLED, enabled).apply()
                if (!enabled) {
                    stopAllSfx()
                }
            }
            SoundCategory.VOICE -> {
                isVoiceEnabled = enabled
                prefs.edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply()
            }
            SoundCategory.ANIMAL -> {
                isAnimalEnabled = enabled
                prefs.edit().putBoolean(KEY_ANIMAL_ENABLED, enabled).apply()
            }
        }
        applyMuteState()
    }

    fun isCategoryEnabled(category: SoundCategory): Boolean {
        if (isMuted) return false
        return when (category) {
            SoundCategory.SFX -> isSfxEnabled
            SoundCategory.VOICE -> isVoiceEnabled
            SoundCategory.ANIMAL -> isAnimalEnabled
        }
    }

    fun setMasterVolume(v: Float) {
        masterVolume = v.coerceIn(0f, 1f)
        prefs.edit().putFloat(KEY_MASTER_VOLUME, masterVolume).apply()
        // Update active loops
        for (handle in synchronized(activeLoops) { activeLoops.keys.toList() }) {
            setLoopVolume(handle, handle.targetVolume)
        }
    }

    // Backward-compatible volume wrappers
    fun setVolume(vol: Float) = setMasterVolume(vol)
    fun getVolume(): Float = masterVolume

    @Volatile
    private var isSyncingSoundEnabled = false

    private fun applyMuteState() {
        if (!isSyncingSoundEnabled) {
            isSyncingSoundEnabled = true
            try {
                soundEffectPlayer?.soundEnabled = !isMuted && isSfxEnabled
            } catch (e: Exception) {
                Log.w(TAG, "Error syncing soundEnabled to soundEffectPlayer", e)
            } finally {
                isSyncingSoundEnabled = false
            }
        }
    }

    private fun stopAllSfx() {
        for ((handle, job) in synchronized(activeLoops) { activeLoops.entries.map { it.key to it.value } }) {
            job?.cancel()
            if (handle.streamId > 0) {
                soundPool?.stop(handle.streamId)
            }
        }
        activeLoops.clear()
    }

    // ==========================================
    // 📻 AUDIO FOCUS & LIFECYCLE
    // ==========================================

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                        ) {
                            stopAll()
                        }
                    }
                    .build()
                audioManager?.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                        ) {
                            stopAll()
                        }
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (_: Exception) {}
    }

    fun onAppPause() {
        stopAll()
    }

    fun onAppResume() {
        // Do not auto-resume loops or sounds per section 2.7
    }

    fun releaseAll() {
        stopAll()
        soundPool?.release()
        soundPool = null
        loadedSoundIds.clear()
        soundIdMap.clear()
        managerScope.cancel()
    }
}
