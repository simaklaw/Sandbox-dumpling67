package com.example.model

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Visual themes and tactile materials for the Squishy Pelmen-Six antistress toy.
 */
enum class SquishyMaterialType(
    val id: String,
    val title: String,
    val emoji: String,
    val subtitle: String,
    val bodyColors: List<Color>,
    val rimColor: Color,
    val blushColor: Color,
    val highlightColor: Color,
    val emblemBgColors: List<Color>,
    val emblemTextColor: Color,
    val backgroundColors: List<Color>,
    val particleEmojis: List<String>,
    val springStiffness: Float = 150f,
    val damping: Float = 9.8f,
    val internalPressure: Float = 0.75f,
    val bounciness: Float = 1.0f,
    val squishResistance: Float = 1.0f
) {
    GOLDEN_DUMPLING(
        id = "golden_dumpling",
        title = "Золотой Пельмешек",
        emoji = "🥟",
        subtitle = "Классическое нежное тесто",
        bodyColors = listOf(Color(0xFFFFFDE7), Color(0xFFFFE082), Color(0xFFFFB300)),
        rimColor = Color(0xFFE65100),
        blushColor = Color(0xFFFF8A80),
        highlightColor = Color(0x99FFFFFF),
        emblemBgColors = listOf(Color(0xFFFFECB3), Color(0xFFFFD54F)),
        emblemTextColor = Color(0xFFD84315),
        backgroundColors = listOf(Color(0xFFFFF8E1), Color(0xFFFFECB3), Color(0xFFFFE082)),
        particleEmojis = listOf("✨", "🥟", "⭐", "💖", "🌟"),
        springStiffness = 145f,
        damping = 9.2f,
        internalPressure = 0.78f,
        bounciness = 1.1f,
        squishResistance = 1.0f
    ),

    STRAWBERRY_ZEPHYR(
        id = "strawberry_zephyr",
        title = "Клубничный Зефир",
        emoji = "🍓",
        subtitle = "Сладкая розовая пастила",
        bodyColors = listOf(Color(0xFFFFF0F5), Color(0xFFFFB6C1), Color(0xFFFF69B4)),
        rimColor = Color(0xFFC2185B),
        blushColor = Color(0xFFFF4081),
        highlightColor = Color(0xB3FFFFFF),
        emblemBgColors = listOf(Color(0xFFFFE4E1), Color(0xFFFFC0CB)),
        emblemTextColor = Color(0xFF880E4F),
        backgroundColors = listOf(Color(0xFFFCE4EC), Color(0xFFF8BBD0), Color(0xFFF48FB1)),
        particleEmojis = listOf("🍓", "💖", "🌸", "✨", "🎀", "🍭"),
        springStiffness = 115f,
        damping = 13.5f, // Slow recovery memory-foam feeling
        internalPressure = 0.65f,
        bounciness = 0.65f,
        squishResistance = 0.8f
    ),

    COSMIC_GALAXY(
        id = "cosmic_galaxy",
        title = "Космический Слайм",
        emoji = "🌌",
        subtitle = "Звёздная антистресс-материя",
        bodyColors = listOf(Color(0xFF80DEEA), Color(0xFF7E57C2), Color(0xFF311B92)),
        rimColor = Color(0xFF00E5FF),
        blushColor = Color(0xFFEA80FC),
        highlightColor = Color(0xCCB388FF),
        emblemBgColors = listOf(Color(0xFFB388FF), Color(0xFF7C4DFF)),
        emblemTextColor = Color(0xFFFFFFFF),
        backgroundColors = listOf(Color(0xFFEDE7F6), Color(0xFFD1C4E9), Color(0xFFB39DDB)),
        particleEmojis = listOf("🌌", "⭐", "🚀", "🪐", "✨", "💜"),
        springStiffness = 175f,
        damping = 7.5f, // Very elastic cosmic slime
        internalPressure = 0.90f,
        bounciness = 1.4f,
        squishResistance = 1.15f
    ),

    RAINBOW_CANDY(
        id = "rainbow_candy",
        title = "Радужный Мармелад",
        emoji = "🌈",
        subtitle = "Сочный тянущийся мармелад",
        bodyColors = listOf(Color(0xFFFFF9C4), Color(0xFF80CBC4), Color(0xFFFF80AB)),
        rimColor = Color(0xFFFF4081),
        blushColor = Color(0xFFFF5252),
        highlightColor = Color(0xCCFFFFFF),
        emblemBgColors = listOf(Color(0xFFFFF59D), Color(0xFFFFCC80)),
        emblemTextColor = Color(0xFFE91E63),
        backgroundColors = listOf(Color(0xFFE8F5E9), Color(0xFFFFFDE7), Color(0xFFFCE4EC)),
        particleEmojis = listOf("🌈", "🍬", "🎉", "✨", "🦄", "🍭"),
        springStiffness = 160f,
        damping = 8.0f,
        internalPressure = 0.82f,
        bounciness = 1.25f,
        squishResistance = 0.95f
    ),

    CHOCO_CREAM(
        id = "choco_cream",
        title = "Шоко-Мялка",
        emoji = "🍫",
        subtitle = "Молочный шоколадный крем",
        bodyColors = listOf(Color(0xFFD7CCC8), Color(0xFFA1887F), Color(0xFF5D4037)),
        rimColor = Color(0xFF3E2723),
        blushColor = Color(0xFFFF8A80),
        highlightColor = Color(0x80FFFFFF),
        emblemBgColors = listOf(Color(0xFFFFECB3), Color(0xFFFFCC80)),
        emblemTextColor = Color(0xFF3E2723),
        backgroundColors = listOf(Color(0xFFEFEBE9), Color(0xFFD7CCC8), Color(0xFFBCAAA4)),
        particleEmojis = listOf("🍫", "🍪", "🍩", "✨", "☕", "🤎"),
        springStiffness = 130f,
        damping = 11.2f, // Dense cream dough
        internalPressure = 0.70f,
        bounciness = 0.85f,
        squishResistance = 1.2f
    ),

    CRYSTAL_AQUA(
        id = "crystal_aqua",
        title = "Кристальное Желе",
        emoji = "🫧",
        subtitle = "Прозрачное аква-желе",
        bodyColors = listOf(Color(0xFFE0F7FA), Color(0xFF80DEEA), Color(0xFF00ACC1)),
        rimColor = Color(0xFF006064),
        blushColor = Color(0xFF80CBC4),
        highlightColor = Color(0xE6FFFFFF),
        emblemBgColors = listOf(Color(0xFFB2EBF2), Color(0xFF4DD0E1)),
        emblemTextColor = Color(0xFF004D40),
        backgroundColors = listOf(Color(0xFFE0F2F1), Color(0xFFB2DFDB), Color(0xFF80CBC4)),
        particleEmojis = listOf("🫧", "💧", "🌊", "✨", "🐬", "💎"),
        springStiffness = 210f,
        damping = 6.2f, // Ultra jiggly jelly
        internalPressure = 0.95f,
        bounciness = 1.55f,
        squishResistance = 1.1f
    )
}
