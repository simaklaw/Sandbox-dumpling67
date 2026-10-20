package com.example.screens.Rain

import androidx.compose.ui.graphics.Color
import kotlin.random.Random

/**
 * 🌧️ Falling raindrop in "Дождик" game.
 */
data class RainDrop(
    val id: Long,
    var x: Float,           // dp
    var y: Float,           // dp (from cloud down to ground)
    var vy: Float = 1100f,  // dp/sec
    val size: Float = 14f,
    var isSplashed: Boolean = false
)

/**
 * 💧 Ground water puddle.
 */
data class RainPuddle(
    val id: Int,
    val centerXDp: Float,
    val widthDp: Float,
    val heightDp: Float = 26f,
    var dropCount: Int = 0,
    var hasFrog: Boolean = false,
    var hasDuck: Boolean = false,
    var ripplePhase: Float = 0f
)

/**
 * 🌸 Growing flower on the meadow.
 */
data class GrowingFlower(
    val id: Long,
    val xDp: Float,
    val yDp: Float,
    val color: Color,
    val iconEmoji: String,
    var progress: Float = 0f, // 0..1 (grows over 2 seconds)
    var isFullyGrown: Boolean = false
)

/**
 * 🐸 Meadow resident (Frog or Duckling)
 */
data class MeadowAnimal(
    val id: Long,
    val emoji: String,
    val soundKey: String,
    var xDp: Float,
    var yDp: Float,
    var jumpProgress: Float = 0f,
    var isJumping: Boolean = false,
    var rotation: Float = 0f
)

/**
 * Splash particle when drop hits ground or puddle
 */
data class RainSplashParticle(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var color: Color = Color(0xFF90CAF9),
    var size: Float = 8f,
    var alpha: Float = 1.0f,
    var ageSec: Float = 0f,
    val lifeSec: Float = 0.35f
)
