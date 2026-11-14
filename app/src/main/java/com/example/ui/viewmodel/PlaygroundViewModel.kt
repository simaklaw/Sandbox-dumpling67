package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.audio.BackgroundMusicManager
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.model.GameState
import kotlinx.coroutines.flow.StateFlow

/**
 * Центральная ViewModel: состояние площадки, музыка и звуковые переключатели.
 */
class PlaygroundViewModel(
    private val repository: GameRepository,
    val soundPlayer: SoundEffectPlayer,
    val musicManager: BackgroundMusicManager
) : ViewModel() {

    val gameState: StateFlow<GameState> = repository.gameState

    val currentTrackTitle: StateFlow<String> = musicManager.currentTrackTitle

    init {
        // Sync initial sound/haptic state
        val initial = repository.gameState.value
        soundPlayer.soundEnabled = initial.soundEnabled
        soundPlayer.hapticsEnabled = initial.hapticsEnabled
    }

    fun toggleMusic() {
        musicManager.togglePlayPause()
        soundPlayer.triggerLightTapPulse()
    }

    fun togglePalmRejection(enabled: Boolean) {
        repository.togglePalmRejection(enabled)
        soundPlayer.triggerLightTapPulse()
    }

    fun setPalmRejectionMargin(marginDp: Float) {
        repository.setPalmRejectionMargin(marginDp)
    }

    fun resetProgress() {
        repository.resetProgress()
        soundPlayer.playBoing()
    }
}
