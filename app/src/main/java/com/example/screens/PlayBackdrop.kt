package com.example.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import com.example.shared.ColorBg
import com.example.shared.ColorBgSoft
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Общий фон меню: тёплый градиент, мягкое свечение цвета мира и медленно плывущие
 * пузыри, звёздочки и облачка. Рисуется одним Canvas, поэтому почти ничего не стоит.
 */
@Composable
fun PlayBackdrop(modifier: Modifier = Modifier, tint: Color = Color(0xFFFFD35C)) {
    val t by rememberInfiniteTransition(label = "backdrop").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing), RepeatMode.Restart),
        label = "backdropT"
    )
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val d = density
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFFBF1), ColorBg, ColorBgSoft)))
            // свечение цвета мира у верхнего края
            drawCircle(
                Brush.radialGradient(listOf(lerp(tint, Color.White, 0.55f).copy(alpha = 0.55f), Color.Transparent), Offset(w * 0.5f, 0f), w * 0.9f),
                w * 0.9f, Offset(w * 0.5f, 0f)
            )
            // плывущие пузыри
            for (i in 0 until 9) {
                val sp = 0.6f + (i % 3) * 0.25f
                val ph = i * 0.37f
                val x = ((i * 0.131f + 0.07f + sin((t * 2f * PI.toFloat() * sp) + ph) * 0.03f) % 1f) * w
                val y = (1.1f - ((t * sp + ph) % 1f) * 1.2f) * h
                val r = (14f + (i % 4) * 9f) * d
                val c = lerp(tint, Color.White, 0.6f).copy(alpha = 0.16f)
                drawCircle(c, r, Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.22f), r * 0.22f, Offset(x - r * 0.35f, y - r * 0.35f))
            }
            // звёздочки: мерцают
            for (i in 0 until 7) {
                val x = ((i * 0.173f + 0.09f) % 1f) * w
                val y = ((i * 0.291f + 0.05f) % 0.9f) * h
                val tw = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() * 6f + i * 1.7f)
                val r = (5f + 4f * tw) * d
                val path = Path().apply {
                    for (k in 0 until 8) {
                        val a = k / 8f * 2f * PI.toFloat() - PI.toFloat() / 2f
                        val rr = if (k % 2 == 0) r else r * 0.38f
                        val px = x + cos(a) * rr
                        val py = y + sin(a) * rr
                        if (k == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(path, Color.White.copy(alpha = 0.35f + 0.45f * tw))
            }
            // облачка у нижнего края
            for (i in 0 until 2) {
                val cx = (((i * 0.55f + t * 0.5f) % 1.3f) - 0.15f) * w
                val cy = h * (0.93f - i * 0.02f)
                val c = Color.White.copy(alpha = 0.5f)
                drawCircle(c, 22f * d, Offset(cx - 30f * d, cy + 6f * d))
                drawCircle(c, 30f * d, Offset(cx, cy - 4f * d))
                drawCircle(c, 24f * d, Offset(cx + 30f * d, cy + 4f * d))
            }
        }
    }
}
