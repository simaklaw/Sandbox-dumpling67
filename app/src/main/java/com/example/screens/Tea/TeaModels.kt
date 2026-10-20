package com.example.screens.Tea

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * 🍓 Tea Ingredient Types for Step 7A:
 * - Strawberry (#FF7FA3)
 * - Mint (#7FE0A0)
 * - Lemon (#FFE066)
 * - Blueberry (#8A6BFF)
 */
enum class TeaIngredientType(
    val id: String,
    val title: String,
    val emoji: String,
    val color: Color
) {
    STRAWBERRY(
        id = "strawberry",
        title = "Клубника",
        emoji = "🍓",
        color = Color(0xFFFF7FA3)
    ),
    MINT(
        id = "mint",
        title = "Мята",
        emoji = "🌿",
        color = Color(0xFF7FE0A0)
    ),
    LEMON(
        id = "lemon",
        title = "Лимон",
        emoji = "🍋",
        color = Color(0xFFFFE066)
    ),
    BLUEBERRY(
        id = "blueberry",
        title = "Черника",
        emoji = "🫐",
        color = Color(0xFF8A6BFF)
    )
}

/**
 * Stage of Tea Preparation:
 * - ADDING_INGREDIENTS (0..3 ingredients, Brew button visible if >= 1)
 * - BREWING (stirring spoon, swirling waves, rising steam, 2.5s)
 * - READY_TO_DRINK (Drink button visible, cup steaming gently)
 * - DRINKING (water level lowering, gulp sounds, smiling cup, 1.6s)
 */
enum class TeaBrewState {
    ADDING_INGREDIENTS,
    BREWING,
    READY_TO_DRINK,
    DRINKING
}

/**
 * Ingredient floating or sinking inside the tea cup.
 */
data class CupIngredientItem(
    val id: Long,
    val type: TeaIngredientType,
    var x: Float,
    var y: Float,
    var targetY: Float,
    var rotation: Float = 0f,
    var sinkProgress: Float = 0f, // 0..1 over 3 seconds
    var spawnTime: Long = System.currentTimeMillis()
)

/**
 * Steam cloud particle rising from hot brewed tea.
 */
data class SteamParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var scale: Float,
    var alpha: Float = 0.8f,
    var lifeSec: Float = 1.8f,
    var ageSec: Float = 0f
)

/**
 * Sparkle / Lemonade Fizz Bubble particle.
 */
data class TeaBubbleParticle(
    var x: Float,
    var y: Float,
    var vy: Float,
    var radius: Float,
    val color: Color,
    var alpha: Float = 0.9f,
    var isSparkle: Boolean = false,
    var ageSec: Float = 0f,
    var lifeSec: Float = 1.2f
)
