package com.example.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

private fun DrawScope.drawHeart(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + size * 0.7f)
        cubicTo(
            center.x - size * 1.2f, center.y - size * 0.3f,
            center.x - size * 0.8f, center.y - size * 1.2f,
            center.x, center.y - size * 0.4f
        )
        cubicTo(
            center.x + size * 0.8f, center.y - size * 1.2f,
            center.x + size * 1.2f, center.y - size * 0.3f,
            center.x, center.y + size * 0.7f
        )
        close()
    }
    drawPath(path, color = color)
}
