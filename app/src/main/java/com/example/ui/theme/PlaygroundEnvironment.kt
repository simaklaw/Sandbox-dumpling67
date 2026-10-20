package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import java.util.Calendar

enum class PlaygroundEnvironmentType(
    val title: String,
    val emoji: String,
    val description: String,
    val skyColors: List<Color>,
    val groundColor: Color,
    val isNight: Boolean,
    val requiredCharactersUnlocked: Int = 0
) {
    AUTO_TIME(
        title = "Авто (по часам)",
        emoji = "⏰",
        description = "Меняется утром, днем, на закате и ночью",
        skyColors = listOf(Color(0xFF81D4FA), Color(0xFFFFECB3), Color(0xFFFFE082)),
        groundColor = Color(0xFFFFCC80),
        isNight = false
    ),
    SUNNY_DAY(
        title = "Солнечный день",
        emoji = "☀️",
        description = "Ясное небо, бабочки и пушистые облака",
        skyColors = listOf(Color(0xFF81D4FA), Color(0xFFB3E5FC), Color(0xFFFFF9C4)),
        groundColor = Color(0xFFFFE082),
        isNight = false
    ),
    GOLDEN_SUNSET(
        title = "Золотой закат",
        emoji = "🌅",
        description = "Тёплый вечер, розовые облака и светлячки",
        skyColors = listOf(Color(0xFFFF8A65), Color(0xFFFFAB91), Color(0xFFFFE082)),
        groundColor = Color(0xFFFFB74D),
        isNight = false
    ),
    STARRY_NIGHT(
        title = "Звёздная ночь",
        emoji = "🌙",
        description = "Светящаяся луна, созвездия и мерцающие светлячки",
        skyColors = listOf(Color(0xFF0D1B2A), Color(0xFF1B263B), Color(0xFF415A77)),
        groundColor = Color(0xFF1B263B),
        isNight = true
    ),
    COSMIC_SPACE(
        title = "Космос",
        emoji = "🌌",
        description = "Звёздная невесомость, туманности и кометы",
        skyColors = listOf(Color(0xFF12002B), Color(0xFF280659), Color(0xFF4A0E4E)),
        groundColor = Color(0xFF240046),
        isNight = true,
        requiredCharactersUnlocked = 3
    ),
    CARNIVAL_PARTY(
        title = "Карнавал",
        emoji = "🎉",
        description = "Праздничные флажки, конфетти и шарики",
        skyColors = listOf(Color(0xFFFF80AB), Color(0xFFFFE082), Color(0xFF80D8FF)),
        groundColor = Color(0xFFFFD54F),
        isNight = false,
        requiredCharactersUnlocked = 6
    );

    fun resolveEffectiveTheme(): PlaygroundEnvironmentType {
        if (this != AUTO_TIME) return this
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 6..10 -> SUNNY_DAY
            in 11..17 -> SUNNY_DAY
            in 18..20 -> GOLDEN_SUNSET
            else -> STARRY_NIGHT
        }
    }
}
