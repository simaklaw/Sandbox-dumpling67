package com.example.screens.Busyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

enum class BusyboardPage(val title: String, val iconEmoji: String) {
    BUTTONS_LEVERS("Кнопки и рычаги", "🔘"),
    LIGHTS_MECHANICS("Свет и механика", "💡"),
    PEEKABOO("Ку-ку", "🏠"),
    MUSIC("Музыка", "🎹"),
    TOYS("Игрушки", "🎡")
}

data class PeekabooAnimal(
    val id: String,
    val name: String,
    val emoji: String,
    val soundAnimal: String
)

object BusyboardCatalog {
    val PEEKABOO_ANIMALS = listOf(
        PeekabooAnimal("cat", "Котик", "🐱", "cat"),
        PeekabooAnimal("dog", "Собачка", "🐶", "dog"),
        PeekabooAnimal("duck", "Уточка", "🦆", "duck"),
        PeekabooAnimal("cow", "Коровка", "🐮", "cow"),
        PeekabooAnimal("frog", "Лягушонок", "🐸", "frog"),
        PeekabooAnimal("horse", "Лошадка", "🐴", "horse")
    )
}

data class BusyParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val isStar: Boolean = false,
    var size: Float = 10f,
    var alpha: Float = 1.0f,
    var lifeSec: Float = 0.8f,
    var ageSec: Float = 0f
)
