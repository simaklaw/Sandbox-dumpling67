package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
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

    private val scope = CoroutineScope(Dispatchers.Default)
    private var musicJob: Job? = null

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow(0)
    val currentTrackIndex: StateFlow<Int> = _currentTrackIndex.asStateFlow()

    private val _currentTrackTitle = MutableStateFlow("")
    val currentTrackTitle: StateFlow<String> = _currentTrackTitle.asStateFlow()

    var musicVolume: Float = 0.45f
    var isEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                stopMusic()
            } else if (_isPlaying.value) {
                startMusic()
            }
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
        scope.launch {
            delay(500L)
            if (isEnabled && _isPlaying.value) {
                startMusic()
            }
        }
    }

    private var streamingTrack: AudioTrack? = null

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pauseMusic()
        } else {
            resumeMusic()
        }
    }

    fun pauseMusic() {
        _isPlaying.value = false
        musicJob?.cancel()
        musicJob = null
        try {
            streamingTrack?.pause()
            streamingTrack?.flush()
        } catch (_: Exception) {}
    }

    fun resumeMusic() {
        if (!isEnabled) return
        _isPlaying.value = true
        startMusic()
    }

    fun nextTrack() {
        val nextIdx = (_currentTrackIndex.value + 1) % playlist.size
        selectTrack(nextIdx)
    }

    fun previousTrack() {
        val prevIdx = if (_currentTrackIndex.value - 1 < 0) playlist.size - 1 else _currentTrackIndex.value - 1
        selectTrack(prevIdx)
    }

    fun selectTrack(index: Int) {
        val safeIndex = index.coerceIn(0, playlist.size - 1)
        _currentTrackIndex.value = safeIndex
        _currentTrackTitle.value = playlist[safeIndex].title
        if (_isPlaying.value && isEnabled) {
            startMusic()
        }
    }

    fun stopMusic() {
        musicJob?.cancel()
        musicJob = null
        try {
            streamingTrack?.stop()
            streamingTrack?.release()
        } catch (_: Exception) {}
        streamingTrack = null
    }

    private fun getOrCreateStreamingTrack(sampleRate: Int): AudioTrack? {
        streamingTrack?.let { return it }
        return try {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
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
            track.play()
            streamingTrack = track
            track
        } catch (_: Exception) {
            null
        }
    }

    private fun startMusic() {
        musicJob?.cancel()
        if (!isEnabled || !_isPlaying.value) return

        musicJob = scope.launch(Dispatchers.Default) {
            try {
                val sampleRate = 22050
                val track = getOrCreateStreamingTrack(sampleRate) ?: return@launch
                if (track.state != AudioTrack.STATE_INITIALIZED) {
                    Log.w("BackgroundMusicManager", "AudioTrack not initialized, skipping music loop")
                    return@launch
                }
                try {
                    track.play()
                } catch (_: Exception) {}

                while (isActive && isEnabled && _isPlaying.value) {
                    val currentMelody = playlist[_currentTrackIndex.value]
                    _currentTrackTitle.value = currentMelody.title
                    val beatDurationMs = (60000 / currentMelody.bpm)

                    for (note in currentMelody.notes) {
                        if (!isActive || !isEnabled || !_isPlaying.value) break

                        val freq = note.first
                        val durationBeats = note.second
                        val noteDurationMs = (durationBeats * beatDurationMs).toInt()

                        if (freq > 0f) {
                            writeGentleMusicBoxNote(track, freq, noteDurationMs, sampleRate)
                        } else {
                            val restSamples = (noteDurationMs * sampleRate) / 1000
                            if (restSamples > 0) {
                                val written = try {
                                    track.write(ShortArray(restSamples), 0, restSamples)
                                } catch (_: Exception) { -1 }
                                if (written <= 0) {
                                    delay(noteDurationMs.toLong().coerceAtLeast(20L))
                                }
                            } else {
                                delay(20L)
                            }
                        }
                    }
                    // Tiny breather between loops (write 400ms silence)
                    val pauseSamples = (400 * sampleRate) / 1000
                    val written = try {
                        track.write(ShortArray(pauseSamples), 0, pauseSamples)
                    } catch (_: Exception) { -1 }
                    if (written <= 0) {
                        delay(400L)
                    }
                }
            } catch (_: CancellationException) {
                // Normal job cancellation
            } catch (e: Exception) {
                Log.w("BackgroundMusicManager", "Music loop error: ${e.message}")
            }
        }
    }

    /**
     * Synthesizes and writes a soft, warm music box / celesta tone directly to the stream.
     */
    private suspend fun writeGentleMusicBoxNote(track: AudioTrack, freq: Float, durationMs: Int, sampleRate: Int) {
        try {
            val numSamples = (durationMs * sampleRate) / 1000
            if (numSamples <= 0) return
            val buffer = ShortArray(numSamples)
            val baseVol = (musicVolume * 16000f).coerceIn(0f, 28000f)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / numSamples

                // Bell ADSR Envelope: Fast sweet attack (15ms), long exponential decay
                val attackSamples = (0.015f * sampleRate).toInt().coerceAtLeast(1)
                val envelope = if (i < attackSamples) {
                    i.toFloat() / attackSamples
                } else {
                    // Soft exponential decay
                    Math.exp(-3.5 * progress).toFloat()
                }

                // Soft warm harmonics
                val fundamental = sin(2.0 * PI * freq * t).toFloat() * 0.70f
                val secondHarmonic = sin(2.0 * PI * (freq * 2f) * t).toFloat() * 0.20f
                val thirdHarmonic = sin(2.0 * PI * (freq * 3f) * t).toFloat() * 0.10f
                val sampleVal = (fundamental + secondHarmonic + thirdHarmonic) * envelope * baseVol

                buffer[i] = sampleVal.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            val written = try {
                track.write(buffer, 0, buffer.size)
            } catch (_: Exception) { -1 }
            if (written <= 0) {
                delay(durationMs.toLong().coerceAtLeast(20L))
            }
        } catch (_: Exception) {}
    }

    fun release() {
        stopMusic()
    }
}
