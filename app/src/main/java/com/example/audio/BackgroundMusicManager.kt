package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * BackgroundMusicManager:
 * Synthesizes and streams soothing, peaceful toddler lullabies and melodies in real-time.
 * 100% self-contained, lightweight, zero-latency and requires no external MP3/audio files.
 */
class BackgroundMusicManager {

    data class MelodyTrack(
        val id: Int,
        val title: String,
        val emoji: String,
        val bpm: Int,
        val notes: List<Pair<Float, Float>> // Pair(Frequency in Hz, Duration in beats)
    )

    /**
     * ВАЖНО: AudioTrack создаётся, пишется, ставится на паузу и освобождается ТОЛЬКО на этом одном потоке.
     * Раньше stop()/release()/pause() вызывались из главного потока, пока корутина висела в блокирующем
     * track.write() — это приводило к нативному SIGSEGV в libaudioclient (AudioTrack::releaseBuffer).
     * Остальные потоки лишь меняют флаги, а сам плеер-цикл на своём потоке реагирует на них.
     */
    private val audioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "bg-music").apply { isDaemon = true }
    }
    private val scope = CoroutineScope(SupervisorJob() + audioExecutor.asCoroutineDispatcher())
    private var loopJob: Job? = null

    @Volatile private var released = false
    @Volatile private var restartRequested = false

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow(0)
    val currentTrackIndex: StateFlow<Int> = _currentTrackIndex.asStateFlow()

    private val _currentTrackTitle = MutableStateFlow("")
    val currentTrackTitle: StateFlow<String> = _currentTrackTitle.asStateFlow()

    @Volatile var musicVolume: Float = 0.45f
    @Volatile var isEnabled: Boolean = true
        set(value) {
            field = value
            if (value && _isPlaying.value) ensureLoop()
        }

    // Frequencies (Hz) for standard notes
    companion object {
        const val C4 = 261.63f
        const val D4 = 293.66f
        const val E4 = 329.63f
        const val F4 = 349.23f
        const val G4 = 392.00f
        const val A4 = 440.00f
        const val B4 = 493.88f
        const val C5 = 523.25f
        const val D5 = 587.33f
        const val E5 = 659.25f
        const val F5 = 698.46f
        const val G5 = 783.99f
        const val A5 = 880.00f
        const val REST = 0f
        private const val TAG = "BackgroundMusicManager"
    }

    // Soothing Toddler Playlists
    val playlist: List<MelodyTrack> = listOf(
        // Track 0: Twinkle Twinkle Little Star / Колыбельная звёздочек
        MelodyTrack(
            id = 0,
            title = "Колыбельная звёздочек",
            emoji = "⭐",
            bpm = 76,
            notes = listOf(
                Pair(C5, 1f), Pair(C5, 1f), Pair(G5, 1f), Pair(G5, 1f),
                Pair(A5, 1f), Pair(A5, 1f), Pair(G5, 2f),
                Pair(F5, 1f), Pair(F5, 1f), Pair(E5, 1f), Pair(E5, 1f),
                Pair(D5, 1f), Pair(D5, 1f), Pair(C5, 2f),
                Pair(G5, 1f), Pair(G5, 1f), Pair(F5, 1f), Pair(F5, 1f),
                Pair(E5, 1f), Pair(E5, 1f), Pair(D5, 2f),
                Pair(G5, 1f), Pair(G5, 1f), Pair(F5, 1f), Pair(F5, 1f),
                Pair(E5, 1f), Pair(E5, 1f), Pair(D5, 2f),
                Pair(C5, 1f), Pair(C5, 1f), Pair(G5, 1f), Pair(G5, 1f),
                Pair(A5, 1f), Pair(A5, 1f), Pair(G5, 2f),
                Pair(F5, 1f), Pair(F5, 1f), Pair(E5, 1f), Pair(E5, 1f),
                Pair(D5, 1f), Pair(D5, 1f), Pair(C5, 2f),
                Pair(REST, 1f)
            )
        ),
        // Track 1: Brahms' Lullaby / Сладкие сны
        MelodyTrack(
            id = 1,
            title = "Сладкие сны",
            emoji = "🌙",
            bpm = 68,
            notes = listOf(
                Pair(E4, 1f), Pair(E4, 1f), Pair(G4, 2f),
                Pair(E4, 1f), Pair(E4, 1f), Pair(G4, 2f),
                Pair(E4, 1f), Pair(G4, 1f), Pair(C5, 2f), Pair(B4, 1f),
                Pair(A4, 1f), Pair(A4, 1f), Pair(G4, 2f),
                Pair(D4, 1f), Pair(E4, 1f), Pair(F4, 2f),
                Pair(D4, 1f), Pair(E4, 1f), Pair(F4, 2f),
                Pair(D4, 1f), Pair(F4, 1f), Pair(B4, 1.5f), Pair(A4, 0.5f),
                Pair(G4, 1f), Pair(B4, 1f), Pair(C5, 2.5f),
                Pair(REST, 1f)
            )
        ),
        // Track 2: Sunny Playground / Солнечная полянка
        MelodyTrack(
            id = 2,
            title = "Солнечная полянка",
            emoji = "🌻",
            bpm = 88,
            notes = listOf(
                Pair(C5, 1f), Pair(E5, 1f), Pair(G5, 1f), Pair(E5, 1f),
                Pair(A5, 1.5f), Pair(G5, 0.5f), Pair(E5, 1f), Pair(C5, 1f),
                Pair(D5, 1f), Pair(F5, 1f), Pair(E5, 1f), Pair(D5, 1f),
                Pair(C5, 2f), Pair(E5, 1f), Pair(G5, 1f),
                Pair(A5, 1f), Pair(C5, 1f), Pair(G5, 2f),
                Pair(F5, 1f), Pair(E5, 1f), Pair(D5, 1f), Pair(G4, 1f),
                Pair(C5, 3f), Pair(REST, 1f)
            )
        ),
        // Track 3: Magic Music Box / Волшебная шкатулка
        MelodyTrack(
            id = 3,
            title = "Волшебная шкатулка",
            emoji = "🎶",
            bpm = 80,
            notes = listOf(
                Pair(G5, 0.75f), Pair(E5, 0.75f), Pair(C5, 1.5f),
                Pair(A5, 0.75f), Pair(F5, 0.75f), Pair(D5, 1.5f),
                Pair(B4, 0.75f), Pair(D5, 0.75f), Pair(F5, 0.75f), Pair(A5, 0.75f),
                Pair(G5, 2.5f),
                Pair(E5, 0.75f), Pair(G5, 0.75f), Pair(C5, 1.5f),
                Pair(D5, 0.75f), Pair(F5, 0.75f), Pair(B4, 1.5f),
                Pair(C5, 3f),
                Pair(REST, 1f)
            )
        )
    )

    init {
        _currentTrackTitle.value = playlist[0].title
        ensureLoop()
    }

    private fun keepPlaying(): Boolean =
        isEnabled && _isPlaying.value && !released && !restartRequested

    private fun ensureLoop() {
        if (released) return
        if (loopJob?.isActive == true) return
        loopJob = scope.launch { runLoop() }
    }

    /** Включает/выключает фоновую музыку целиком (настройка родителя). */
    fun setMusicEnabled(on: Boolean) {
        isEnabled = on
        if (on) resumeMusic() else pauseMusic()
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pauseMusic() else resumeMusic()
    }

    /** Только ставит флаг: паузу выполнит сам плеер-цикл на своём потоке. */
    fun pauseMusic() {
        _isPlaying.value = false
    }

    fun resumeMusic() {
        if (!isEnabled) return
        _isPlaying.value = true
        ensureLoop()
    }

    fun nextTrack() {
        selectTrack((_currentTrackIndex.value + 1) % playlist.size)
    }

    fun previousTrack() {
        val prev = if (_currentTrackIndex.value - 1 < 0) playlist.size - 1 else _currentTrackIndex.value - 1
        selectTrack(prev)
    }

    fun selectTrack(index: Int) {
        val safeIndex = index.coerceIn(0, playlist.size - 1)
        _currentTrackIndex.value = safeIndex
        _currentTrackTitle.value = playlist[safeIndex].title
        restartRequested = true // цикл заметит и начнёт новую мелодию
        if (_isPlaying.value && isEnabled) ensureLoop()
    }

    /** Окончательная остановка: цикл сам освободит AudioTrack и завершит поток. */
    fun release() {
        released = true
    }

    private fun createTrack(sampleRate: Int): AudioTrack? {
        return try {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return null
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
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
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                Log.w(TAG, "AudioTrack not initialized")
                try { track.release() } catch (_: Exception) {}
                null
            } else {
                track
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot create AudioTrack: ${e.message}")
            null
        }
    }

    private fun releaseTrack(track: AudioTrack?) {
        if (track == null) return
        try { track.stop() } catch (_: Exception) {}
        try { track.release() } catch (_: Exception) {}
    }

    /**
     * Пишет сэмплы небольшими кусками (≈46 мс), чтобы быстро реагировать на паузу/смену мелодии.
     * @return false, если трек сломался (нужно пересоздать), true — иначе (в т.ч. при штатной остановке).
     */
    private fun writeChunks(track: AudioTrack, samples: ShortArray): Boolean {
        var offset = 0
        while (offset < samples.size) {
            if (!keepPlaying()) return true
            val n = min(1024, samples.size - offset)
            val written = try {
                track.write(samples, offset, n)
            } catch (e: Exception) {
                -1
            }
            if (written <= 0) return false
            offset += written
        }
        return true
    }

    private suspend fun CoroutineScope.runLoop() {
        val sampleRate = 22050
        var track: AudioTrack? = null
        try {
            delay(500L) // не нагружаем старт приложения
            while (isActive && !released) {
                if (!(isEnabled && _isPlaying.value)) {
                    // Пауза выполняется здесь, на потоке плеера
                    val t = track
                    if (t != null) {
                        try {
                            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                                t.pause()
                                t.flush()
                            }
                        } catch (_: Exception) {}
                    }
                    delay(120L)
                    continue
                }

                var current = track
                if (current == null) {
                    current = createTrack(sampleRate)
                    if (current == null) {
                        delay(2000L)
                        continue
                    }
                    track = current
                }
                try {
                    if (current.playState != AudioTrack.PLAYSTATE_PLAYING) current.play()
                } catch (e: Exception) {
                    releaseTrack(current)
                    track = null
                    delay(1000L)
                    continue
                }

                restartRequested = false
                val melody = playlist[_currentTrackIndex.value]
                _currentTrackTitle.value = melody.title
                val beatDurationMs = 60000 / melody.bpm
                var broken = false

                for (note in melody.notes) {
                    if (!keepPlaying()) break
                    val durationMs = (note.second * beatDurationMs).toInt()
                    val samples = if (note.first > 0f) {
                        synthNote(note.first, durationMs, sampleRate)
                    } else {
                        ShortArray(((durationMs.toLong() * sampleRate) / 1000).toInt())
                    }
                    if (samples.isNotEmpty() && !writeChunks(current, samples)) {
                        broken = true
                        break
                    }
                }

                if (broken) {
                    releaseTrack(current)
                    track = null
                    delay(1000L)
                    continue
                }
                // Короткая пауза между повторами (400 мс тишины)
                if (keepPlaying()) {
                    if (!writeChunks(current, ShortArray((400 * sampleRate) / 1000))) {
                        releaseTrack(current)
                        track = null
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Music loop error: ${e.message}")
        } finally {
            releaseTrack(track)
            if (released) audioExecutor.shutdown()
        }
    }

    /** Мягкий «музыкальная шкатулка / челеста» тон: быстрая атака, плавное затухание. */
    private fun synthNote(freq: Float, durationMs: Int, sampleRate: Int): ShortArray {
        val numSamples = (durationMs * sampleRate) / 1000
        if (numSamples <= 0) return ShortArray(0)
        val buffer = ShortArray(numSamples)
        val baseVol = (musicVolume * 16000f).coerceIn(0f, 28000f)
        val attackSamples = (0.015f * sampleRate).toInt().coerceAtLeast(1)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / numSamples
            val envelope = if (i < attackSamples) {
                i.toFloat() / attackSamples
            } else {
                exp(-3.5f * progress)
            }
            val fundamental = sin(2.0 * PI * freq * t).toFloat() * 0.70f
            val secondHarmonic = sin(2.0 * PI * (freq * 2f) * t).toFloat() * 0.20f
            val thirdHarmonic = sin(2.0 * PI * (freq * 3f) * t).toFloat() * 0.10f
            val sampleVal = (fundamental + secondHarmonic + thirdHarmonic) * envelope * baseVol
            buffer[i] = sampleVal.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }
}
