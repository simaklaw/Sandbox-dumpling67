package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.sensor.DeviceTilt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

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

data class AtmosphereParticle(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float,
    var vRot: Float,
    var size: Float,
    var alpha: Float,
    val color: Color,
    val type: AtmosphereWeatherType
)

@Composable
fun GlobalAtmosphereParticleLayer(
    weatherType: AtmosphereWeatherType,
    tilt: DeviceTilt = DeviceTilt(),
    tiltProvider: (() -> DeviceTilt)? = null,
    modifier: Modifier = Modifier
) {
    if (weatherType == AtmosphereWeatherType.CLEAR_SKY) return

    val currentTiltProvider by androidx.compose.runtime.rememberUpdatedState(
        tiltProvider ?: { tilt }
    )
    val particles = remember { mutableStateListOf<AtmosphereParticle>() }

    // Initialize or regenerate particles on weather type change
    LaunchedEffect(weatherType) {
        particles.clear()
        if (weatherType == AtmosphereWeatherType.CLEAR_SKY) return@LaunchedEffect

        repeat(weatherType.baseCount) { i ->
            particles.add(
                createRandomAtmosphereParticle(
                    weatherType = weatherType,
                    startY = Random.nextFloat() * 1200f,
                    id = System.nanoTime() + i
                )
            )
        }
    }

    // Physics loop reacting to wind and accelerometer tilt smoothly without restarting coroutines
    LaunchedEffect(weatherType) {
        if (weatherType == AtmosphereWeatherType.CLEAR_SKY) return@LaunchedEffect

        while (isActive) {
            delay(16)
            val currentTilt = currentTiltProvider()
            val tiltWindX = currentTilt.roll * 4.5f // Wind influenced by tilting phone left/right
            val tiltGravityY = currentTilt.pitch * 1.5f

            for (i in particles.indices) {
                val p = particles[i]
                p.x += p.vx + tiltWindX
                p.y += p.vy + tiltGravityY
                p.rotation += p.vRot

                // Wrap boundaries
                if (p.y > 1500f) {
                    p.y = -40f
                    p.x = Random.nextFloat() * 1000f
                } else if (p.y < -50f) {
                    p.y = 1450f
                }

                if (p.x > 1100f) {
                    p.x = -50f
                } else if (p.x < -60f) {
                    p.x = 1050f
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        particles.forEach { p ->
            when (p.type) {
                AtmosphereWeatherType.PETAL_BREEZE -> {
                    drawPetal(p)
                }
                AtmosphereWeatherType.SNOW_FLURRY -> {
                    drawSnowflake(p)
                }
                AtmosphereWeatherType.GOLDEN_STARDUST -> {
                    drawStardust(p)
                }
                AtmosphereWeatherType.AUTUMN_LEAVES -> {
                    drawLeaf(p)
                }
                AtmosphereWeatherType.CELEBRATION_CONFETTI -> {
                    drawConfettiPiece(p)
                }
                else -> {}
            }
        }
    }
}

private fun createRandomAtmosphereParticle(
    weatherType: AtmosphereWeatherType,
    startY: Float,
    id: Long
): AtmosphereParticle {
    val colors = when (weatherType) {
        AtmosphereWeatherType.PETAL_BREEZE -> listOf(
            Color(0xFFFF80AB), Color(0xFFFF4081), Color(0xFFF8BBD0), Color(0xFFFFCDD2)
        )
        AtmosphereWeatherType.SNOW_FLURRY -> listOf(
            Color.White, Color(0xFFE1F5FE), Color(0xFFB3E5FC)
        )
        AtmosphereWeatherType.GOLDEN_STARDUST -> listOf(
            Color(0xFFFFD54F), Color(0xFFFFEE58), Color(0xFFFFCA28), Color(0xFFFFF9C4)
        )
        AtmosphereWeatherType.AUTUMN_LEAVES -> listOf(
            Color(0xFFFF6F00), Color(0xFFF57C00), Color(0xFFFFB300), Color(0xFFD84315)
        )
        AtmosphereWeatherType.CELEBRATION_CONFETTI -> listOf(
            Color(0xFFFF1744), Color(0xFF00E676), Color(0xFF00E5FF), Color(0xFFFFD600), Color(0xFFD500F9)
        )
        AtmosphereWeatherType.CLEAR_SKY -> listOf(Color.Transparent)
    }

    val speedY = when (weatherType) {
        AtmosphereWeatherType.SNOW_FLURRY -> Random.nextFloat() * 1.5f + 1.2f
        AtmosphereWeatherType.PETAL_BREEZE -> Random.nextFloat() * 1.8f + 1.4f
        AtmosphereWeatherType.GOLDEN_STARDUST -> Random.nextFloat() * 1.2f + 0.8f
        AtmosphereWeatherType.AUTUMN_LEAVES -> Random.nextFloat() * 2.2f + 1.5f
        AtmosphereWeatherType.CELEBRATION_CONFETTI -> Random.nextFloat() * 3.5f + 2.5f
        AtmosphereWeatherType.CLEAR_SKY -> 0f
    }

    return AtmosphereParticle(
        id = id,
        x = Random.nextFloat() * 1000f,
        y = startY,
        vx = (Random.nextFloat() - 0.5f) * 1.2f + (if (weatherType == AtmosphereWeatherType.PETAL_BREEZE) 0.8f else 0f),
        vy = speedY,
        rotation = Random.nextFloat() * 360f,
        vRot = (Random.nextFloat() - 0.5f) * 4f,
        size = Random.nextFloat() * 8f + 8f,
        alpha = Random.nextFloat() * 0.4f + 0.6f,
        color = colors.random(),
        type = weatherType
    )
}

private fun DrawScope.drawPetal(p: AtmosphereParticle) {
    rotate(p.rotation, pivot = Offset(p.x, p.y)) {
        val path = Path().apply {
            moveTo(p.x, p.y - p.size)
            cubicTo(
                p.x + p.size * 0.7f, p.y - p.size * 0.5f,
                p.x + p.size * 0.7f, p.y + p.size * 0.5f,
                p.x, p.y + p.size
            )
            cubicTo(
                p.x - p.size * 0.7f, p.y + p.size * 0.5f,
                p.x - p.size * 0.7f, p.y - p.size * 0.5f,
                p.x, p.y - p.size
            )
            close()
        }
        drawPath(path = path, color = p.color.copy(alpha = p.alpha))
    }
}

private fun DrawScope.drawSnowflake(p: AtmosphereParticle) {
    drawCircle(
        color = p.color.copy(alpha = p.alpha),
        radius = p.size * 0.45f,
        center = Offset(p.x, p.y)
    )
    drawCircle(
        color = Color.White.copy(alpha = (p.alpha * 0.8f).coerceIn(0f, 1f)),
        radius = p.size * 0.22f,
        center = Offset(p.x, p.y)
    )
}

private fun DrawScope.drawStardust(p: AtmosphereParticle) {
    rotate(p.rotation, pivot = Offset(p.x, p.y)) {
        val s = p.size * 0.5f
        drawCircle(
            color = p.color.copy(alpha = p.alpha),
            radius = s,
            center = Offset(p.x, p.y)
        )
        // 4-point sparkle cross
        drawLine(
            color = Color.White.copy(alpha = p.alpha),
            start = Offset(p.x - s * 1.5f, p.y),
            end = Offset(p.x + s * 1.5f, p.y),
            strokeWidth = 2f
        )
        drawLine(
            color = Color.White.copy(alpha = p.alpha),
            start = Offset(p.x, p.y - s * 1.5f),
            end = Offset(p.x, p.y + s * 1.5f),
            strokeWidth = 2f
        )
    }
}

private fun DrawScope.drawLeaf(p: AtmosphereParticle) {
    rotate(p.rotation, pivot = Offset(p.x, p.y)) {
        val path = Path().apply {
            moveTo(p.x, p.y - p.size * 1.2f)
            lineTo(p.x + p.size * 0.6f, p.y)
            lineTo(p.x, p.y + p.size * 1.2f)
            lineTo(p.x - p.size * 0.6f, p.y)
            close()
        }
        drawPath(path = path, color = p.color.copy(alpha = p.alpha))
    }
}

private fun DrawScope.drawConfettiPiece(p: AtmosphereParticle) {
    rotate(p.rotation, pivot = Offset(p.x, p.y)) {
        drawRect(
            color = p.color.copy(alpha = p.alpha),
            topLeft = Offset(p.x - p.size * 0.5f, p.y - p.size * 0.25f),
            size = androidx.compose.ui.geometry.Size(p.size, p.size * 0.5f)
        )
    }
}
