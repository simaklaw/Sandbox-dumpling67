package com.example.ui.components

import androidx.compose.runtime.getValue

enum class AtmosphereWeatherType(
    val title: String,
    val emoji: String,
    val baseCount: Int
) {
    PETAL_BREEZE("Лепестки цветов", "🌸", 24),
    SNOW_FLURRY("Пушистый снег", "❄️", 32),
    GOLDEN_STARDUST("Звёздная пыль", "✨", 28),
    AUTUMN_LEAVES("Осенние листья", "🍂", 20),
    CELEBRATION_CONFETTI("Праздничный салют", "🎉", 45),
    CLEAR_SKY("Чистое небо", "☀️", 0)
}
