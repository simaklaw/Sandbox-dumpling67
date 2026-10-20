package com.example.screens.Grains

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * 🌾 Grain Types:
 * - WHITE_RICE: #F5EFE0, fine speckles
 * - BUCKWHEAT: #8B5E3C, medium speckles
 * - GREEN_PEAS: #7BC47F, large rounded speckles
 */
enum class GrainKind(
    val id: String,
    val title: String,
    val emoji: String,
    val color: Color,
    val darkColor: Color,
    val speckleColor: Color,
    val soundProfile: String
) {
    WHITE_RICE(
        id = "rice",
        title = "Белый рис",
        emoji = "🍚",
        color = Color(0xFFF5EFE0),
        darkColor = Color(0xFFDCD2BE),
        speckleColor = Color(0xFFC4B89F),
        soundProfile = "rice"
    ),
    BUCKWHEAT(
        id = "buckwheat",
        title = "Гречка",
        emoji = "🌰",
        color = Color(0xFF8B5E3C),
        darkColor = Color(0xFF6D4428),
        speckleColor = Color(0xFF503019),
        soundProfile = "buckwheat"
    ),
    GREEN_PEAS(
        id = "peas",
        title = "Горошек",
        emoji = "🟢",
        color = Color(0xFF7BC47F),
        darkColor = Color(0xFF5CA360),
        speckleColor = Color(0xFF3F8243),
        soundProfile = "sprinkles"
    )
}

/**
 * Stamp / Mold shapes: Star, Heart, Cat.
 */
enum class StampKind(val emoji: String) {
    STAR("⭐"),
    HEART("💖"),
    CAT("🐱")
}

/**
 * Stamp Impression indentation left on the grain table.
 */
data class StampImpression(
    val id: Long,
    val x: Float,
    val y: Float,
    val kind: StampKind,
    val timestamp: Long = System.currentTimeMillis(),
    var alpha: Float = 1.0f
)

/**
 * Fine particle for pouring stream or flung handful.
 */
data class GrainParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    var size: Float = 5f,
    var alpha: Float = 1.0f,
    var lifeSec: Float = 0.6f,
    var ageSec: Float = 0f
)

/**
 * Glass Jar State.
 */
data class GrainJar(
    val id: Int, // 0 = Left, 1 = Right
    var level: Float, // 0..100 units
    var center: Offset = Offset.Zero,
    var tiltAngleDeg: Float = 0f,
    var isGrabbed: Boolean = false,
    var isAutoPouring: Boolean = false
)
