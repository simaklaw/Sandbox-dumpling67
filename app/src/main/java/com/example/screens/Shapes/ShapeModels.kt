package com.example.screens.Shapes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * 🔷 Sorter Shape Kind and color associations.
 */
enum class SorterShapeKind(
    val title: String,
    val emoji: String,
    val color: Color,
    val voiceSoundKey: String
) {
    CIRCLE("Круг", "🔴", Color(0xFFFF6F61), "voice_shape_circle"),
    SQUARE("Квадрат", "🟦", Color(0xFF4F7BFF), "voice_shape_square"),
    TRIANGLE("Треугольник", "🔺", Color(0xFFFFD23F), "voice_shape_triangle"),
    STAR("Звезда", "⭐", Color(0xFF4CD97B), "voice_shape_star"),
    HEART("Сердце", "💖", Color(0xFFFF7AB6), "voice_shape_heart"),
    OVAL("Овал", "🟣", Color(0xFFA66BFF), "voice_shape_oval")
}

/**
 * Single piece in the sorter.
 */
data class SorterPiece(
    val id: Int,
    val kind: SorterShapeKind,
    var currentPos: Offset,     // screen dp or canvas px
    var homeShelfPos: Offset,
    var isSnapped: Boolean = false,
    var isDragging: Boolean = false,
    var isAnimatingToWindow: Boolean = false
)

/**
 * Window hole on the sorter box lid.
 */
data class SorterWindowHole(
    val id: Int,
    val kind: SorterShapeKind,
    var centerPos: Offset,
    var isFilled: Boolean = false
)
