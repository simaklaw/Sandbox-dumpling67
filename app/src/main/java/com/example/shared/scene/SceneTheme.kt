package com.example.shared.scene

import androidx.compose.ui.graphics.Color

/**
 * 🌄 6 Core Vector Scene Background Themes for Step 12A:
 * - MEADOW: Main Screen (Blue sky, 2 green rolling hills, sun, clouds, grass, flowers)
 * - FARM: Animal Room (Bright sky, green field, fence, distant red barn)
 * - KITCHEN: Kitchen Folder (Warm orange wall with polka dots, checkered floor, shelf, cups)
 * - SKY: Games Folder (Blue sky gradient, fluffy clouds, birds, stars)
 * - ROOM: Interior (Purple striped wall, wooden floor, window, rug)
 * - NIGHT: Sleep/Rest Mode (Dark night sky, moon, stars, dark hills)
 */
enum class SceneTheme(
    val title: String,
    val skyTopColor: Color,
    val skyBottomColor: Color,
    val groundColor: Color,
    val groundSecondaryColor: Color
) {
    MEADOW(
        title = "Лужайка",
        skyTopColor = Color(0xFF8FD8FF),
        skyBottomColor = Color(0xFFD9F4FF),
        groundColor = Color(0xFF5BDB82),
        groundSecondaryColor = Color(0xFF2FB35B)
    ),
    FARM(
        title = "Ферма",
        skyTopColor = Color(0xFF9ADFFF),
        skyBottomColor = Color(0xFFFFF1B8),
        groundColor = Color(0xFF5BDB82),
        groundSecondaryColor = Color(0xFF4CD97B)
    ),
    KITCHEN(
        title = "Кухня",
        skyTopColor = Color(0xFFFFC86B),
        skyBottomColor = Color(0xFFFFE082),
        groundColor = Color(0xFFFF6F61),
        groundSecondaryColor = Color(0xFFFFF3D6)
    ),
    SKY(
        title = "Небо",
        skyTopColor = Color(0xFF4F7BFF),
        skyBottomColor = Color(0xFF9ADFFF),
        groundColor = Color(0xFF6EC6FF),
        groundSecondaryColor = Color(0xFF2EC4E6)
    ),
    ROOM(
        title = "Комната",
        skyTopColor = Color(0xFFC9B3FF),
        skyBottomColor = Color(0xFFE8DDFF),
        groundColor = Color(0xFFE0A96D),
        groundSecondaryColor = Color(0xFFC78C4E)
    ),
    NIGHT(
        title = "Ночь",
        skyTopColor = Color(0xFF1E2A6B),
        skyBottomColor = Color(0xFF4F5DB8),
        groundColor = Color(0xFF2C397E),
        groundSecondaryColor = Color(0xFF1A2353)
    )
}
