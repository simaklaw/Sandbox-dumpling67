package com.example.shared.scene

import androidx.compose.ui.geometry.Rect

/**
 * 🦋 Decor Kinds
 */
enum class DecorKind(val defaultEmoji: String, val defaultSound: String) {
    SUN("☀️", "decor_sun"),
    CLOUD("☁️", "decor_cloud"),
    BUTTERFLY("🦋", "decor_butterfly"),
    BIRD("🐦", "decor_bird"),
    BEE("🐝", "decor_bee"),
    FLOWER("🌸", "decor_flower"),
    TREE("🌳", "decor_tree"),
    MUSHROOM("🍄", "decor_mushroom"),
    SNAIL("🐌", "decor_snail"),
    STAR("⭐", "decor_star"),
    FISH("🐟", "decor_fish"),
    BALLOON("🎈", "pop"),
    BALL("⚽", "decor_ball"),
    YARN("🧶", "decor_yarn"),
    KITE("🪁", "decor_kite"),
    RAINBOW("🌈", "decor_rainbow"),
    HOT_AIR_BALLOON("🎈", "decor_balloon_fly"),
    PLANT("🪴", "decor_plant"),
    PUMPKIN("🎃", "decor_pumpkin"),
    SUNFLOWER("🌻", "decor_sunflower"),
    GEAR("⚙️", "decor_gear"),
    GUEST("🧸", "decor_guest"),
    TEAPOT("🫖", "decor_teapot"),
    CAKE("🎂", "decor_cake"),
    MOUSE("🐭", "decor_mouse"),
    BULB("💡", "decor_bulb"),
    UFO("🛸", "ufo_hello"),
    MOON_FACE("🌝", "chime"),
    PLANET("🪐", "star_collect")
}

/**
 * ⚡ Interactive Reactions on Touch or Events
 */
enum class DecorReaction {
    WIGGLE,
    JUMP,
    SMILE,
    FLY_AWAY_AND_RETURN,
    SPIN,
    GROW_POP,
    BOUNCE_THREE_TIMES,
    ROLL_UNWIND,
    FLY_UP_RETURN,
    SHIMMER_SPARKLE,
    PLANT_FLOWER,
    PUMPKIN_JUMP,
    SUNFLOWER_NOD,
    GEAR_SPEED_UP,
    CLAP_HANDS,
    TEAPOT_STEAM,
    CANDLE_DANCE,
    MOUSE_HIDE,
    BULB_FLASH
}

/**
 * 📋 Spec for placing a decorative item on screen
 */
data class DecorSpec(
    val kind: DecorKind,
    val xFraction: Float,      // 0..1 relative screen width
    val yFraction: Float,      // 0..1 relative screen height
    val sizeDp: Float = 72f,
    val reaction: DecorReaction? = null,
    val soundName: String? = null,
    val interactive: Boolean = true,  // false: doesn't catch touches (for pinball)
    val id: String? = null            // for triggering from game events
)

/**
 * 🎮 Controller for game-driven decor events
 */
class DecorController {
    private val listeners = mutableListOf<(id: String, reaction: DecorReaction) -> Unit>()
    private var lastTriggerTime = 0L

    fun addListener(listener: (id: String, reaction: DecorReaction) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (id: String, reaction: DecorReaction) -> Unit) {
        listeners.remove(listener)
    }

    fun trigger(id: String, reaction: DecorReaction) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < 330L) return // Max 3 reactions per second screen-wide
        lastTriggerTime = now
        listeners.forEach { it(id, reaction) }
    }
}
