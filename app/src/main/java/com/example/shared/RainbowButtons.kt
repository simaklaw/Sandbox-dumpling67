package com.example.shared

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

private fun Color.darker(): Color {
    return Color(
        red = (red * 0.78f).coerceIn(0f, 1f),
        green = (green * 0.78f).coerceIn(0f, 1f),
        blue = (blue * 0.78f).coerceIn(0f, 1f),
        alpha = alpha
    )
}
