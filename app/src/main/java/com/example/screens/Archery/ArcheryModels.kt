package com.example.screens.Archery

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.shared.ColorGreen
import com.example.shared.ColorInk
import com.example.shared.ColorOrange
import com.example.shared.ColorPink
import com.example.shared.ColorYellow

/**
 * 🎯 Arrow types as specified in TZ Step 5:
 * - NORMAL: straight wooden arrow, leaves stuck arrow, sound arrow_hit
 * - CARROT: funny carrot with green leafy top, leaves stuck carrot, sound carrot_hit
 * - STAR: yellow star on a stick, leaves star sticker, sound star_hit
 */
enum class ArrowType(
    val iconEmoji: String,
    val soundHit: String,
    val voiceSound: String,
    val particleColor: Color,
    val shaftColor: Color
) {
    NORMAL(
        iconEmoji = "➶",
        soundHit = "arrow_hit",
        voiceSound = "voice_arrow_normal",
        particleColor = Color(0xFFD7CCC8), // Soft wood beige
        shaftColor = Color(0xFF8D6E63)
    ),
    CARROT(
        iconEmoji = "🥕",
        soundHit = "carrot_hit",
        voiceSound = "voice_arrow_carrot",
        particleColor = ColorOrange,
        shaftColor = ColorOrange
    ),
    STAR(
        iconEmoji = "⭐",
        soundHit = "star_hit",
        voiceSound = "voice_arrow_star",
        particleColor = ColorYellow,
        shaftColor = ColorYellow
    )
}

enum class HitZone {
    HEAD,
    BODY,
    LEFT_ARM,
    RIGHT_ARM
}

/**
 * Arrow currently in flight along a parabolic trajectory.
 */
data class FlyingArrow(
    val id: Long,
    val type: ArrowType,
    var position: Offset,
    var velocity: Offset,
    var angleDeg: Float,
    var ageSec: Float = 0f,
    var isAssisted: Boolean = false
)

/**
 * Arrow stuck into the scarecrow or in the ground/fence.
 * Local position is relative to the scarecrow center so it wobbles with it!
 */
data class StuckArrow(
    val id: Long,
    val type: ArrowType,
    var localOffset: Offset,
    val angleDeg: Float,
    val hitZone: HitZone?,
    val timestamp: Long = System.currentTimeMillis(),
    var alpha: Float = 1.0f,
    var blowAwayVx: Float = 0f,
    var blowAwayVy: Float = 0f,
    var isBlowingAway: Boolean = false
)

/**
 * Soft plasticine particle for impact celebrations.
 */
data class ArcheryParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    var radius: Float,
    var alpha: Float = 1.0f,
    var lifeSec: Float = 0.5f,
    var ageSec: Float = 0f
)

/**
 * Confetti particle for the 7-hit celebration.
 */
data class ArcheryConfetti(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float,
    var vRot: Float,
    val color: Color,
    val size: Float,
    var alpha: Float = 1.0f,
    var ageSec: Float = 0f
)
