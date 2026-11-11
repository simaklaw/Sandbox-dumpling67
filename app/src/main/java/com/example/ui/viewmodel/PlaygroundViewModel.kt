package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.BackgroundMusicManager
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.model.GameState
import com.example.sensor.DeviceTilt
import com.example.sensor.TiltSensorManager
import com.example.ui.components.AtmosphereWeatherType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Central ViewModel managing the Playground state, tilt sensor, atmospheric particles, and audio toggles.
 */
class PlaygroundViewModel(
    private val repository: GameRepository,
    val soundPlayer: SoundEffectPlayer,
    val musicManager: BackgroundMusicManager,
    private val tiltSensorManager: TiltSensorManager
) : ViewModel() {

    val gameState: StateFlow<GameState> = repository.gameState

    val currentTrackTitle: StateFlow<String> = musicManager.currentTrackTitle

    val tiltState: StateFlow<DeviceTilt> = tiltSensorManager.tiltState

    private val _atmosphereWeather = MutableStateFlow(AtmosphereWeatherType.PETAL_BREEZE)

    init {
        // Sync initial sound/haptic state
        val initial = repository.gameState.value
        soundPlayer.soundEnabled = initial.soundEnabled
        soundPlayer.hapticsEnabled = initial.hapticsEnabled

        // Start listening to tilt sensor
        tiltSensorManager.startListening()

        // Periodic magical atmosphere shift loop
        viewModelScope.launch {
            val weathers = listOf(
                AtmosphereWeatherType.PETAL_BREEZE,
                AtmosphereWeatherType.GOLDEN_STARDUST,
                AtmosphereWeatherType.SNOW_FLURRY,
                AtmosphereWeatherType.AUTUMN_LEAVES
            )
            var idx = 0
            while (true) {
                delay(35000) // gentle shift every 35 seconds
                if (_atmosphereWeather.value != AtmosphereWeatherType.CELEBRATION_CONFETTI) {
                    idx = (idx + 1) % weathers.size
                    _atmosphereWeather.value = weathers[idx]
                }
            }
        }
    }

    fun setWeather(weather: AtmosphereWeatherType) {
        _atmosphereWeather.value = weather
        soundPlayer.triggerLightTapPulse()
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

    override fun onCleared() {
        super.onCleared()
        tiltSensorManager.stopListening()
    }
}
