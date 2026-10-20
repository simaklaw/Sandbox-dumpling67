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
import com.example.ui.theme.PlaygroundEnvironmentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    val isMusicPlaying: StateFlow<Boolean> = musicManager.isPlaying
    val currentTrackTitle: StateFlow<String> = musicManager.currentTrackTitle

    val tiltState: StateFlow<DeviceTilt> = tiltSensorManager.tiltState

    private val _environmentType = MutableStateFlow(PlaygroundEnvironmentType.AUTO_TIME)
    val environmentType: StateFlow<PlaygroundEnvironmentType> = _environmentType.asStateFlow()

    private val _atmosphereWeather = MutableStateFlow(AtmosphereWeatherType.PETAL_BREEZE)
    val atmosphereWeather: StateFlow<AtmosphereWeatherType> = _atmosphereWeather.asStateFlow()

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

    fun setEnvironment(type: PlaygroundEnvironmentType) {
        _environmentType.value = type
        soundPlayer.triggerLightTapPulse()
        soundPlayer.playSparkleChime()

        // Match atmosphere to environment
        _atmosphereWeather.value = when (type) {
            PlaygroundEnvironmentType.STARRY_NIGHT -> AtmosphereWeatherType.GOLDEN_STARDUST
            PlaygroundEnvironmentType.COSMIC_SPACE -> AtmosphereWeatherType.GOLDEN_STARDUST
            PlaygroundEnvironmentType.CARNIVAL_PARTY -> AtmosphereWeatherType.CELEBRATION_CONFETTI
            PlaygroundEnvironmentType.GOLDEN_SUNSET -> AtmosphereWeatherType.AUTUMN_LEAVES
            else -> AtmosphereWeatherType.PETAL_BREEZE
        }
    }

    fun cycleNextEnvironment() {
        val all = PlaygroundEnvironmentType.values()
        val nextIdx = (_environmentType.value.ordinal + 1) % all.size
        setEnvironment(all[nextIdx])
    }

    fun setWeather(weather: AtmosphereWeatherType) {
        _atmosphereWeather.value = weather
        soundPlayer.triggerLightTapPulse()
    }

    fun triggerCelebration() {
        _atmosphereWeather.value = AtmosphereWeatherType.CELEBRATION_CONFETTI
        soundPlayer.playFanfare()
        viewModelScope.launch {
            delay(8000)
            if (_atmosphereWeather.value == AtmosphereWeatherType.CELEBRATION_CONFETTI) {
                _atmosphereWeather.value = AtmosphereWeatherType.PETAL_BREEZE
            }
        }
    }

    fun onZoneChanged(pageIndex: Int) {
        soundPlayer.playZoneTransition()
        when (pageIndex) {
            0 -> soundPlayer.playDogBark()
            1 -> soundPlayer.playEatCrunch()
            2 -> soundPlayer.playSquishElastic()
            3 -> soundPlayer.playSqueak()
            4 -> soundPlayer.playBowShot()
            5 -> soundPlayer.playBabyGiggle()
            6 -> soundPlayer.playPouringGrain("rice")
            7 -> soundPlayer.playBubblePop()
            8 -> soundPlayer.playTeaWhistle()
            9 -> soundPlayer.playSplash()
            10 -> soundPlayer.playLeverClunk()
            11 -> soundPlayer.playSparkleChime()
        }
    }

    fun toggleMusic() {
        musicManager.togglePlayPause()
        soundPlayer.triggerLightTapPulse()
    }

    fun nextMusicTrack() {
        musicManager.nextTrack()
        soundPlayer.triggerLightTapPulse()
    }

    fun toggleSoundEnabled(enabled: Boolean) {
        repository.toggleSound(enabled)
        soundPlayer.soundEnabled = enabled
        soundPlayer.triggerLightTapPulse()
    }

    fun toggleHapticsEnabled(enabled: Boolean) {
        repository.toggleHaptics(enabled)
        soundPlayer.hapticsEnabled = enabled
        soundPlayer.triggerLightTapPulse()
    }

    fun togglePalmRejection(enabled: Boolean) {
        repository.togglePalmRejection(enabled)
        soundPlayer.triggerLightTapPulse()
    }

    fun setPalmRejectionMargin(marginDp: Float) {
        repository.setPalmRejectionMargin(marginDp)
    }

    fun addBonusStars(amount: Int) {
        repository.addCoins(amount)
        triggerCelebration()
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
