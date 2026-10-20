package com.example.model

import androidx.compose.ui.graphics.Color

/**
 * Shared particle for celebratory pops, squishes, and wardrobe dressing effects.
 */
data class SparkleParticle(
    val id: Long,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color = Color(0xFFFFD54F),
    val size: Float = 14f,
    val emoji: String = "✨"
)
