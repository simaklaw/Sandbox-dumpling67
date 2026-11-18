package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Фоновая музыка меню: одна мягкая мелодия `res/raw/menu_music.ogg` (её делает `generate_menu_music.py`),
 * играет MediaPlayer по кругу. Раньше музыка синтезировалась на лету через AudioTrack и звучала «роботом».
 *
 * Все вызовы идут с главного потока. Без [Context] (тесты) менеджер молчит: состояние есть, звука нет.
 * Звук best-effort: сбой плеера не должен ронять игру.
 */
class BackgroundMusicManager(context: Context? = null) {

    private val appContext: Context? = context?.applicationContext
    private var player: MediaPlayer? = null

    @Volatile private var released = false

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTrackTitle = MutableStateFlow(TRACK_TITLE)
    val currentTrackTitle: StateFlow<String> = _currentTrackTitle.asStateFlow()

    /** Громкость мелодии 0..1 (сама мелодия записана тихо, около -20 дБ RMS). */
    @Volatile var musicVolume: Float = 0.6f
        set(value) {
            field = value.coerceIn(0f, 1f)
            applyVolume()
        }

    @Volatile var isEnabled: Boolean = true
        set(value) {
            field = value
            syncPlayback()
        }

    /** Пока идёт заставка, музыка не стартует (иначе она накладывается на ноты заставки). */
    @Volatile var held: Boolean = true

    /** Включает/выключает фоновую музыку целиком (настройка родителя). */
    fun setMusicEnabled(on: Boolean) {
        isEnabled = on
        if (on) resumeMusic() else pauseMusic()
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pauseMusic() else resumeMusic()
    }

    fun pauseMusic() {
        _isPlaying.value = false
        syncPlayback()
    }

    fun resumeMusic() {
        if (!isEnabled || held || released) return
        _isPlaying.value = true
        syncPlayback()
    }

    /** Окончательная остановка: освобождает плеер. */
    fun release() {
        released = true
        try {
            player?.release()
        } catch (e: Exception) {
            Log.w(TAG, "release failed: ${e.message}")
        }
        player = null
    }

    private fun shouldPlay(): Boolean = isEnabled && _isPlaying.value && !held && !released

    private fun syncPlayback() {
        val ctx = appContext ?: return
        try {
            if (shouldPlay()) {
                val p = player ?: createPlayer(ctx) ?: return
                p.setVolume(musicVolume, musicVolume)
                if (!p.isPlaying) p.start()
            } else {
                val p = player
                if (p != null && p.isPlaying) p.pause()
            }
        } catch (e: Exception) {
            Log.w(TAG, "music playback failed: ${e.message}")
            try {
                player?.release()
            } catch (e2: Exception) {
                Log.w(TAG, "release after failure: ${e2.message}")
            }
            player = null
        }
    }

    private fun applyVolume() {
        try {
            player?.setVolume(musicVolume, musicVolume)
        } catch (e: Exception) {
            Log.w(TAG, "set volume failed: ${e.message}")
        }
    }

    private fun createPlayer(ctx: Context): MediaPlayer? {
        val p = MediaPlayer.create(
            ctx,
            R.raw.menu_music,
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
        ) ?: return null
        p.isLooping = true
        p.setOnErrorListener { _, what, extra ->
            Log.w(TAG, "MediaPlayer error $what/$extra")
            player = null
            true
        }
        player = p
        return p
    }

    private companion object {
        const val TAG = "BackgroundMusicManager"
        const val TRACK_TITLE = "Колыбельная пельмешка"
    }
}
