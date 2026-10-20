package com.example.screens.Boxes

import androidx.compose.ui.graphics.Color

/**
 * 📦 Surprise Item in "Кто в коробке?"
 */
data class BoxSurprise(
    val id: String,
    val name: String,
    val emoji: String,
    val soundKey: String
)

/**
 * All 10 surprises for the game.
 */
object BoxSurpriseCatalog {
    val ALL_SURPRISES = listOf(
        BoxSurprise("duck", "Утёнок", "🦆", "animal_duck"),
        BoxSurprise("dog", "Щенок", "🐶", "animal_dog"),
        BoxSurprise("cat", "Котёнок", "🐱", "animal_cat"),
        BoxSurprise("bear", "Мишка", "🧸", "decor_guest"),
        BoxSurprise("bunny", "Зайка", "🐰", "animal_bunny"),
        BoxSurprise("frog", "Лягушонок", "🐸", "animal_frog"),
        BoxSurprise("chick", "Цыплёнок", "🐥", "chick_peep"),
        BoxSurprise("elephant", "Слонёнок", "🐘", "elephant_toot"),
        BoxSurprise("robot", "Робот", "🤖", "robot_beep"),
        BoxSurprise("dino", "Динозаврик", "🦖", "dino_soft")
    )
}

/**
 * State for a single interactive box.
 */
data class GiftBoxItem(
    val id: Int,
    val baseColor: Color,
    val ribbonColor: Color,
    var shakeAngle: Float = 0f,
    var isShaking: Boolean = false,
    var isLidOpen: Boolean = false,
    var currentSurprise: BoxSurprise? = null,
    var surpriseProgress: Float = 0f, // 0..1 jumping out
    var lastActionTime: Long = 0L
)
