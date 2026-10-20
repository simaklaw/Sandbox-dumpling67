package com.example.screens.Catapult

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.screens.Squish.HeroExpression
import com.example.screens.Squish.HeroSkin
import com.example.shared.scene.DecorKind

/**
 * 🚀 World Zones along the vertical height in Catapult ("Запусти героя в небо")
 */
enum class CatapultZone(
    val minHeightScreens: Float,
    val maxHeightScreens: Float,
    val skyTopColor: Color,
    val skyBottomColor: Color
) {
    MEADOW(0f, 1f, Color(0xFF8FD8FF), Color(0xFFD9F4FF)),
    CLOUDS(1f, 3f, Color(0xFF6EC6FF), Color(0xFF9ADFFF)),
    HIGH_SKY(3f, 5f, Color(0xFF4F7BFF), Color(0xFF6EC6FF)),
    SPACE(5f, 10f, Color(0xFF1E2A6B), Color(0xFF4F5DB8));

    companion object {
        fun forHeight(screens: Float): CatapultZone {
            return when {
                screens < 1f -> MEADOW
                screens < 3f -> CLOUDS
                screens < 5f -> HIGH_SKY
                else -> SPACE
            }
        }
    }
}

/**
 * 🎯 Interactive flight bonus / obstacle kinds
 */
enum class CatapultBonusKind(
    val defaultEmoji: String,
    val soundName: String,
    val radiusDp: Float
) {
    STAR("⭐", "star_collect", 26f),
    CLOUD_TRAMPOLINE("☁️", "cloud_bounce", 48f),
    BALLOON_BUNDLE("🎈", "balloon_boost", 40f),
    BIRD("🐦", "bird_tweet", 32f),
    KITE("🪁", "kite_swoosh", 42f),
    RAINBOW("🌈", "rainbow_slide", 58f),
    UFO("🛸", "ufo_hello", 44f),
    MOON_FACE("🌝", "chime", 50f)
}

/**
 * Procedurally placed bonus / obstacle in the vertical sky
 */
data class CatapultBonusItem(
    val id: Long,
    val kind: CatapultBonusKind,
    val xDp: Float,         // 0..screenWidthDp
    val yDp: Float,         // In total vertical world coordinates (0 = ground, positive up)
    var isCollected: Boolean = false,
    var bounceScale: Float = 1.0f,
    var activeTimerSec: Float = 0f
)

/**
 * Particles emitted during launch, bouncing, stars, gifts
 */
data class CatapultParticle(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var color: Color,
    val isStar: Boolean = true,
    var size: Float = 12f,
    var alpha: Float = 1.0f,
    var ageSec: Float = 0f,
    val lifeSec: Float = 0.8f
)

/**
 * Falling reward gift box with parachute
 */
data class CatapultGiftBox(
    var xDp: Float,
    var yDp: Float,
    var isLanded: Boolean = false,
    var isOpened: Boolean = false,
    var parachuteSway: Float = 0f,
    var ageSec: Float = 0f
)
