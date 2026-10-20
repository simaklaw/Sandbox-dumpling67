package com.example.shared

import androidx.compose.ui.geometry.Offset

data class HintConfig(
    val id: String,
    val description: String,
    val mode: String, // "tap" | "drag"
    val fromOffset: Offset,
    val toOffset: Offset = Offset.Zero
)

object HintsCatalog {
    val homeScreenFeedHint = HintConfig(
        id = "home_feed_hint",
        description = "Покажи касание на карточку Кормление",
        mode = "tap",
        fromOffset = Offset(100f, 150f)
    )

    val gameFeedingDragHint = HintConfig(
        id = "game_feeding_drag",
        description = "Перетащи еду в ротик зверя",
        mode = "drag",
        fromOffset = Offset(200f, 600f),
        toOffset = Offset(200f, 300f)
    )
}
