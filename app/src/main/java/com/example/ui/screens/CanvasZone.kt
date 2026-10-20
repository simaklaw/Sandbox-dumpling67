package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository

/**
 * CanvasZone Composable:
 * Multi-touch finger painting playground with rainbow, bubble, and sparkle brushes for toddlers.
 */
@Composable
fun CanvasZoneComposable(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    modifier: Modifier = Modifier
) {
    CanvasZone(repository = repository, soundPlayer = soundPlayer, modifier = modifier)
}
