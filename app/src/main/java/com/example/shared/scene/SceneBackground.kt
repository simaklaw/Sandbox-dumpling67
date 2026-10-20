package com.example.shared.scene

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.shared.ColorInk
import com.example.shared.LocalAgeProfile
import kotlin.math.sin

/**
 * 🌄 3-Layer Vector Scene Background Renderer:
 * Layer 1: Sky / Wall background gradient & patterns
 * Layer 2: Ground / Floor / Distant Hills / Shelf / Barn
 * Layer 3: Animated elements (drifting clouds, gently swaying grass/hills, sun glow)
 */
@Composable
fun SceneBackground(
    theme: SceneTheme,
    modifier: Modifier = Modifier
) {
    val ageProfile = LocalAgeProfile.current

    // Infinite animation for subtle cloud drift & hill sway
    val infiniteTransition = rememberInfiniteTransition(label = "scene_bg_anim")
    val cloudDrift by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (ageProfile.isToddler2) 7200 else 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cloudDrift"
    )

    val sunRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (ageProfile.isToddler2) 40000 else 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sunRotation"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val width = size.width
                val height = size.height
                val inkStroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

                onDrawBehind {
                    when (theme) {
                        SceneTheme.MEADOW -> drawMeadowScene(width, height, cloudDrift, sunRotation, inkStroke)
                        SceneTheme.FARM -> drawFarmScene(width, height, cloudDrift, sunRotation, inkStroke)
                        SceneTheme.KITCHEN -> drawKitchenScene(width, height, inkStroke)
                        SceneTheme.SKY -> drawSkyScene(width, height, cloudDrift, sunRotation, inkStroke)
                        SceneTheme.ROOM -> drawRoomScene(width, height, inkStroke)
                        SceneTheme.NIGHT -> drawNightScene(width, height, cloudDrift, inkStroke)
                    }
                }
            }
    ) { }
}

/**
 * 🌿 MEADOW Scene (Main Screen)
 */
private fun DrawScope.drawMeadowScene(
    width: Float,
    height: Float,
    cloudDrift: Float,
    sunRotation: Float,
    inkStroke: Stroke
) {
    // Layer 1: Sky Gradient
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(SceneTheme.MEADOW.skyTopColor, SceneTheme.MEADOW.skyBottomColor)
        ),
        size = Size(width, height)
    )

    // Sun in Top Right
    val sunCenter = Offset(width * 0.82f, height * 0.14f)
    val sunRadius = 42.dp.toPx()
    drawCircle(color = Color(0xFFFFD23F), radius = sunRadius, center = sunCenter)
    drawCircle(color = ColorInk, radius = sunRadius, center = sunCenter, style = inkStroke)

    // Rotating Sun Rays
    rotate(degrees = sunRotation, pivot = sunCenter) {
        for (i in 0 until 8) {
            val angle = i * 45f
            rotate(degrees = angle, pivot = sunCenter) {
                drawLine(
                    color = Color(0xFFFFD23F),
                    start = Offset(sunCenter.x, sunCenter.y - sunRadius - 8.dp.toPx()),
                    end = Offset(sunCenter.x, sunCenter.y - sunRadius - 20.dp.toPx()),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }

    // Drifting Fluffy Clouds
    drawVectorCloud(Offset(width * 0.20f + cloudDrift, height * 0.12f), scale = 1.0f)
    drawVectorCloud(Offset(width * 0.65f - cloudDrift * 0.8f, height * 0.22f), scale = 0.8f)

    // Layer 2: Back Rolling Hill (Darker Green)
    val backHillY = height * 0.62f
    val backHillPath = Path().apply {
        moveTo(0f, backHillY)
        quadraticTo(width * 0.35f, backHillY - 70.dp.toPx(), width * 0.75f, backHillY - 20.dp.toPx())
        quadraticTo(width * 0.90f, backHillY, width, backHillY - 40.dp.toPx())
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }
    drawPath(backHillPath, color = SceneTheme.MEADOW.groundSecondaryColor)
    drawPath(backHillPath, color = ColorInk, style = inkStroke)

    // Front Main Hill (Lighter Green)
    val frontHillY = height * 0.68f
    val frontHillPath = Path().apply {
        moveTo(0f, frontHillY)
        quadraticTo(width * 0.45f, frontHillY - 90.dp.toPx(), width, frontHillY - 10.dp.toPx())
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }
    drawPath(frontHillPath, color = SceneTheme.MEADOW.groundColor)
    drawPath(frontHillPath, color = ColorInk, style = inkStroke)

    // Layer 3: Grass Tufts at Bottom
    for (i in 0..6) {
        val gx = i * (width / 6f) + 20.dp.toPx()
        val gy = height - 18.dp.toPx()
        drawGrassTuft(Offset(gx, gy))
    }
}

/**
 * 🚜 FARM Scene (Animal Room)
 */
private fun DrawScope.drawFarmScene(
    width: Float,
    height: Float,
    cloudDrift: Float,
    sunRotation: Float,
    inkStroke: Stroke
) {
    // Sky
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(SceneTheme.FARM.skyTopColor, SceneTheme.FARM.skyBottomColor)
        ),
        size = Size(width, height)
    )

    // Sun & Clouds
    val sunCenter = Offset(width * 0.18f, height * 0.12f)
    drawCircle(color = Color(0xFFFFD23F), radius = 38.dp.toPx(), center = sunCenter)
    drawCircle(color = ColorInk, radius = 38.dp.toPx(), center = sunCenter, style = inkStroke)
    drawVectorCloud(Offset(width * 0.60f + cloudDrift, height * 0.15f), scale = 0.9f)

    // Distant Red Barn Silhouette
    val barnW = 100.dp.toPx()
    val barnH = 80.dp.toPx()
    val barnTopLeft = Offset(width * 0.68f, height * 0.52f - barnH)
    drawRect(color = Color(0xFFFF6F61), topLeft = barnTopLeft, size = Size(barnW, barnH))
    drawRect(color = ColorInk, topLeft = barnTopLeft, size = Size(barnW, barnH), style = inkStroke)

    // Barn Roof
    val roofPath = Path().apply {
        moveTo(barnTopLeft.x - 10.dp.toPx(), barnTopLeft.y)
        lineTo(barnTopLeft.x + barnW / 2f, barnTopLeft.y - 35.dp.toPx())
        lineTo(barnTopLeft.x + barnW + 10.dp.toPx(), barnTopLeft.y)
        close()
    }
    drawPath(roofPath, color = Color(0xFFFF9A3C))
    drawPath(roofPath, color = ColorInk, style = inkStroke)

    // Green Field
    val fieldY = height * 0.52f
    drawRect(
        color = SceneTheme.FARM.groundColor,
        topLeft = Offset(0f, fieldY),
        size = Size(width, height - fieldY)
    )
    drawLine(color = ColorInk, start = Offset(0f, fieldY), end = Offset(width, fieldY), strokeWidth = 3.5.dp.toPx())

    // Wooden Fence
    val fencePostW = 14.dp.toPx()
    val fencePostH = 40.dp.toPx()
    val postsCount = 6
    for (i in 0 until postsCount) {
        val px = i * (width / (postsCount - 1)) + 8.dp.toPx()
        drawRoundRect(color = Color(0xFFFFD23F), topLeft = Offset(px, fieldY - fencePostH), size = Size(fencePostW, fencePostH), cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()))
        drawRoundRect(color = ColorInk, topLeft = Offset(px, fieldY - fencePostH), size = Size(fencePostW, fencePostH), cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()), style = inkStroke)
    }
    drawRect(color = Color(0xFFFF9A3C), topLeft = Offset(0f, fieldY - fencePostH * 0.6f), size = Size(width, 10.dp.toPx()))
    drawRect(color = ColorInk, topLeft = Offset(0f, fieldY - fencePostH * 0.6f), size = Size(width, 10.dp.toPx()), style = inkStroke)
}

/**
 * 🍳 KITCHEN Scene (Kitchen Folder)
 */
private fun DrawScope.drawKitchenScene(
    width: Float,
    height: Float,
    inkStroke: Stroke
) {
    // Warm Wall Background
    drawRect(color = Color(0xFFFFC86B), size = Size(width, height))

    // Polka Dots Wall Pattern
    val dotR = 10.dp.toPx()
    for (row in 0..8) {
        for (col in 0..6) {
            val dx = col * 70.dp.toPx() + (if (row % 2 == 1) 35.dp.toPx() else 0f)
            val dy = row * 60.dp.toPx()
            drawCircle(color = Color.White.copy(alpha = 0.20f), radius = dotR, center = Offset(dx, dy))
        }
    }

    // Shelf with Teacups in Upper Section
    val shelfY = height * 0.18f
    val shelfH = 14.dp.toPx()
    drawRoundRect(color = Color(0xFFFF9A3C), topLeft = Offset(0f, shelfY), size = Size(width, shelfH), cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()))
    drawLine(color = ColorInk, start = Offset(0f, shelfY), end = Offset(width, shelfY), strokeWidth = 3.5.dp.toPx())
    drawLine(color = ColorInk, start = Offset(0f, shelfY + shelfH), end = Offset(width, shelfY + shelfH), strokeWidth = 3.5.dp.toPx())

    // Teacups on Shelf
    drawTeacupGraphic(Offset(width * 0.25f, shelfY - 18.dp.toPx()), color = Color(0xFFFF7AB6))
    drawTeacupGraphic(Offset(width * 0.70f, shelfY - 18.dp.toPx()), color = Color(0xFF2EC4E6))

    // Checkered Tablecloth Floor in Lower Section
    val floorY = height * 0.70f
    drawRect(color = Color(0xFFFF6F61), topLeft = Offset(0f, floorY), size = Size(width, height - floorY))
    drawLine(color = ColorInk, start = Offset(0f, floorY), end = Offset(width, floorY), strokeWidth = 4.dp.toPx())

    // White Checkered Grid Lines
    val checkW = 35.dp.toPx()
    for (i in 0..12) {
        val cx = i * checkW
        drawRect(color = Color.White.copy(alpha = 0.28f), topLeft = Offset(cx, floorY), size = Size(checkW / 2f, height - floorY))
    }
}

/**
 * ☁️ SKY Scene (Games Folder)
 */
private fun DrawScope.drawSkyScene(
    width: Float,
    height: Float,
    cloudDrift: Float,
    sunRotation: Float,
    inkStroke: Stroke
) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(SceneTheme.SKY.skyTopColor, SceneTheme.SKY.skyBottomColor)
        ),
        size = Size(width, height)
    )

    // Sun & Clouds
    val sunCenter = Offset(width * 0.85f, height * 0.10f)
    drawCircle(color = Color(0xFFFFD23F), radius = 45.dp.toPx(), center = sunCenter)
    drawCircle(color = ColorInk, radius = 45.dp.toPx(), center = sunCenter, style = inkStroke)

    drawVectorCloud(Offset(width * 0.15f + cloudDrift, height * 0.18f), scale = 1.1f)
    drawVectorCloud(Offset(width * 0.68f - cloudDrift * 0.7f, height * 0.32f), scale = 0.9f)
    drawVectorCloud(Offset(width * 0.35f + cloudDrift * 0.5f, height * 0.65f), scale = 1.0f)
}

/**
 * 🏠 ROOM Scene (Interior)
 */
private fun DrawScope.drawRoomScene(
    width: Float,
    height: Float,
    inkStroke: Stroke
) {
    // Purple Wall
    drawRect(color = Color(0xFFC9B3FF), size = Size(width, height))

    // Vertical Wall Stripes
    val stripeW = 28.dp.toPx()
    for (i in 0..20) {
        if (i % 2 == 0) {
            drawRect(color = Color.White.copy(alpha = 0.15f), topLeft = Offset(i * stripeW, 0f), size = Size(stripeW, height))
        }
    }

    // Wooden Floor
    val floorY = height * 0.72f
    drawRect(color = Color(0xFFE0A96D), topLeft = Offset(0f, floorY), size = Size(width, height - floorY))
    drawLine(color = ColorInk, start = Offset(0f, floorY), end = Offset(width, floorY), strokeWidth = 4.dp.toPx())
}

/**
 * 🌙 NIGHT Scene (Sleep/Rest Mode)
 */
private fun DrawScope.drawNightScene(
    width: Float,
    height: Float,
    cloudDrift: Float,
    inkStroke: Stroke
) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(SceneTheme.NIGHT.skyTopColor, SceneTheme.NIGHT.skyBottomColor)
        ),
        size = Size(width, height)
    )

    // Crescent Moon
    val moonCenter = Offset(width * 0.80f, height * 0.12f)
    val moonR = 36.dp.toPx()
    drawCircle(color = Color(0xFFFFD23F), radius = moonR, center = moonCenter)
    drawCircle(color = SceneTheme.NIGHT.skyTopColor, radius = moonR * 0.85f, center = Offset(moonCenter.x - 10.dp.toPx(), moonCenter.y - 6.dp.toPx()))

    // Dark Hill
    val hillY = height * 0.70f
    drawRect(color = SceneTheme.NIGHT.groundColor, topLeft = Offset(0f, hillY), size = Size(width, height - hillY))
    drawLine(color = ColorInk, start = Offset(0f, hillY), end = Offset(width, hillY), strokeWidth = 3.5.dp.toPx())
}

/**
 * Helper: Vector Cloud Drawing
 */
private fun DrawScope.drawVectorCloud(center: Offset, scale: Float) {
    val r = 24.dp.toPx() * scale
    val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    drawCircle(color = Color.White, radius = r * 1.2f, center = center)
    drawCircle(color = Color.White, radius = r * 0.9f, center = Offset(center.x - r * 1.1f, center.y + r * 0.2f))
    drawCircle(color = Color.White, radius = r * 0.9f, center = Offset(center.x + r * 1.1f, center.y + r * 0.2f))

    // Contour
    drawCircle(color = ColorInk, radius = r * 1.2f, center = center, style = inkStroke)
    drawCircle(color = ColorInk, radius = r * 0.9f, center = Offset(center.x - r * 1.1f, center.y + r * 0.2f), style = inkStroke)
    drawCircle(color = ColorInk, radius = r * 0.9f, center = Offset(center.x + r * 1.1f, center.y + r * 0.2f), style = inkStroke)
}

/**
 * Helper: Grass Tuft
 */
private fun DrawScope.drawGrassTuft(offset: Offset) {
    val path = Path().apply {
        moveTo(offset.x, offset.y)
        quadraticTo(offset.x - 8.dp.toPx(), offset.y - 18.dp.toPx(), offset.x - 14.dp.toPx(), offset.y - 24.dp.toPx())
        moveTo(offset.x, offset.y)
        quadraticTo(offset.x, offset.y - 22.dp.toPx(), offset.x, offset.y - 30.dp.toPx())
        moveTo(offset.x, offset.y)
        quadraticTo(offset.x + 8.dp.toPx(), offset.y - 18.dp.toPx(), offset.x + 14.dp.toPx(), offset.y - 24.dp.toPx())
    }
    drawPath(path, color = Color(0xFF2FB35B), style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
}

/**
 * Helper: Teacup Graphic
 */
private fun DrawScope.drawTeacupGraphic(center: Offset, color: Color) {
    val w = 24.dp.toPx()
    val h = 18.dp.toPx()
    drawRoundRect(color = color, topLeft = Offset(center.x - w / 2, center.y - h / 2), size = Size(w, h), cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()))
    drawRoundRect(color = ColorInk, topLeft = Offset(center.x - w / 2, center.y - h / 2), size = Size(w, h), cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
}
