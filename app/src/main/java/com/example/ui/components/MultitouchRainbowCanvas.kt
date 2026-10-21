package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.audio.SoundEffectPlayer
import com.example.ui.util.ToddlerPalmRejection
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class CanvasBrushMode(val title: String, val emoji: String) {
    RAINBOW_TRAIL("Радужный след", "🌈"),
    NEON_GLOW("Неон", "✨"),
    MAGIC_BUBBLES("Пузыри", "🫧"),
    PASTEL_COLOR("Пастель", "🖌️"),
    ANIMAL_STAMP("Штампы", "🐾")
}

data class RainbowPathPoint(
    val offset: Offset,
    val hue: Float, // 0..360 for animated rainbow color along trail
    val timestamp: Long = System.currentTimeMillis()
)

data class TrailSparkle(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    var alpha: Float,
    val color: Color
)

data class MultitouchStroke(
    val id: Long = System.nanoTime(),
    val points: List<RainbowPathPoint>,
    val color: Color,
    val strokeWidth: Float,
    val brushMode: CanvasBrushMode
)

data class StampElement(
    val id: Long = System.nanoTime(),
    val emoji: String,
    val x: Float,
    val y: Float,
    val size: Float,
    val rotation: Float
)

val RAINBOW_SPECTRUM = listOf(
    Color(0xFFFF1744), // Red
    Color(0xFFFF9100), // Orange
    Color(0xFFFFEA00), // Yellow
    Color(0xFF00E676), // Green
    Color(0xFF00E5FF), // Cyan
    Color(0xFF2979FF), // Blue
    Color(0xFFD500F9), // Purple
    Color(0xFFFF4081)  // Pink
)

/**
 * Reusable Multitouch Canvas with dynamic rainbow trails, particle sparkles, and smooth Bezier strokes.
 */
@Composable
fun MultitouchRainbowCanvas(
    strokes: MutableList<MultitouchStroke>,
    stamps: MutableList<StampElement>,
    activeBrushMode: CanvasBrushMode,
    selectedColor: Color,
    selectedStamp: String,
    strokeWidth: Float,
    soundPlayer: SoundEffectPlayer,
    onStrokeAdded: () -> Unit,
    palmRejectionEnabled: Boolean = true,
    edgeMarginDp: Float = 22f,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val edgeMarginPx = remember(edgeMarginDp, density) {
        with(density) { edgeMarginDp.dp.toPx() }
    }

    // Map of active pointers: PointerId -> List<RainbowPathPoint>
    val activePointers = remember { mutableStateMapOf<Long, MutableList<RainbowPathPoint>>() }
    val activePointersHue = remember { mutableStateMapOf<Long, Float>() }
    val trailingSparkles = remember { mutableStateListOf<TrailSparkle>() }
    var liveTick by remember { mutableIntStateOf(0) }

    // Ambient shimmering phase for rainbow wave effect
    val infiniteTransition = rememberInfiniteTransition(label = "rainbow_wave")
    val rainbowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Sparkle particle physics updater
    LaunchedEffect(trailingSparkles.size) {
        while (trailingSparkles.isNotEmpty()) {
            delay(16)
            for (i in trailingSparkles.indices.reversed()) {
                val s = trailingSparkles[i]
                s.x += s.vx
                s.y += s.vy
                s.size -= 0.35f
                s.alpha -= 0.025f
                if (s.size <= 0f || s.alpha <= 0f) {
                    trailingSparkles.removeAt(i)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(activeBrushMode, selectedColor, selectedStamp, strokeWidth, palmRejectionEnabled, edgeMarginPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id.value
                    val canvasSize = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())

                    val isInitialAccidental = palmRejectionEnabled &&
                            ToddlerPalmRejection.isNearEdge(down.position, canvasSize, edgeMarginPx)

                    if (!isInitialAccidental) {
                        if (activeBrushMode == CanvasBrushMode.ANIMAL_STAMP) {
                            soundPlayer.playChime(Random.nextFloat() * 0.4f + 0.9f)
                            soundPlayer.vibrateSquishDeform()
                            stamps.add(
                                StampElement(
                                    emoji = selectedStamp,
                                    x = down.position.x,
                                    y = down.position.y,
                                    size = strokeWidth * 1.8f,
                                    rotation = (Random.nextFloat() - 0.5f) * 36f
                                )
                            )
                            onStrokeAdded()
                        } else {
                            soundPlayer.vibrateDragTick()
                            val startHue = (pointerId * 77f + rainbowPhase) % 360f
                            activePointersHue[pointerId] = startHue
                            activePointers[pointerId] = mutableListOf(
                                RainbowPathPoint(offset = down.position, hue = startHue)
                            )
                        }
                    }

                    // Track all simultaneous fingers with palm rejection filtering
                    do {
                        val event = awaitPointerEvent()
                        val filteredChanges = ToddlerPalmRejection.filterIntentionalChanges(
                            changes = event.changes,
                            containerSize = canvasSize,
                            edgeMarginPx = edgeMarginPx,
                            enabled = palmRejectionEnabled
                        )

                        for (change in event.changes) {
                            val id = change.id.value
                            val isAllowed = filteredChanges.contains(change) || activePointers.containsKey(id)

                            if (change.pressed && isAllowed) {
                                if (activeBrushMode == CanvasBrushMode.ANIMAL_STAMP) {
                                    if (change.previousPressed.not() && !ToddlerPalmRejection.isNearEdge(change.position, canvasSize, edgeMarginPx)) {
                                        stamps.add(
                                            StampElement(
                                                emoji = selectedStamp,
                                                x = change.position.x,
                                                y = change.position.y,
                                                size = strokeWidth * 1.8f,
                                                rotation = (Random.nextFloat() - 0.5f) * 36f
                                            )
                                        )
                                        soundPlayer.playChime(Random.nextFloat() * 0.4f + 0.9f)
                                        soundPlayer.vibrateSquishDeform()
                                        onStrokeAdded()
                                    }
                                } else {
                                    var pointList = activePointers[id]
                                    var currentHue = activePointersHue[id] ?: 0f
                                    currentHue = (currentHue + 7f) % 360f
                                    activePointersHue[id] = currentHue

                                    val newPoint = RainbowPathPoint(
                                        offset = change.position,
                                        hue = currentHue
                                    )

                                    if (pointList == null) {
                                        if (!ToddlerPalmRejection.isNearEdge(change.position, canvasSize, edgeMarginPx) || !palmRejectionEnabled) {
                                            pointList = mutableListOf(newPoint)
                                            activePointers[id] = pointList
                                        }
                                    } else {
                                        pointList.add(newPoint)
                                    }
                                    liveTick++

                                    // Spawn trailing fairy sparkle particles behind finger
                                    if (activeBrushMode == CanvasBrushMode.RAINBOW_TRAIL || activeBrushMode == CanvasBrushMode.NEON_GLOW) {
                                        val color = if (activeBrushMode == CanvasBrushMode.RAINBOW_TRAIL) {
                                            Color.hsl(currentHue, 0.95f, 0.55f)
                                        } else {
                                            selectedColor
                                        }
                                        if (Random.nextFloat() < 0.65f) {
                                            trailingSparkles.add(
                                                TrailSparkle(
                                                    id = System.nanoTime() + id,
                                                    x = change.position.x + (Random.nextFloat() - 0.5f) * 16f,
                                                    y = change.position.y + (Random.nextFloat() - 0.5f) * 16f,
                                                    vx = (Random.nextFloat() - 0.5f) * 6f,
                                                    vy = (Random.nextFloat() - 0.5f) * 6f,
                                                    size = Random.nextFloat() * 8f + 6f,
                                                    alpha = 0.9f,
                                                    color = color
                                                )
                                            )
                                        }
                                    }
                                }
                                change.consume()
                            } else {
                                // Finger lifted: commit stroke
                                val finishedPoints = activePointers.remove(id)
                                activePointersHue.remove(id)
                                if (finishedPoints != null && finishedPoints.isNotEmpty()) {
                                    strokes.add(
                                        MultitouchStroke(
                                            points = finishedPoints.toList(),
                                            color = selectedColor,
                                            strokeWidth = strokeWidth,
                                            brushMode = activeBrushMode
                                        )
                                    )
                                    soundPlayer.vibrateDragTick()
                                    onStrokeAdded()
                                }
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // Commit any remaining strokes
                    activePointers.forEach { (_, pts) ->
                        if (pts.isNotEmpty()) {
                            strokes.add(
                                MultitouchStroke(
                                    points = pts.toList(),
                                    color = selectedColor,
                                    strokeWidth = strokeWidth,
                                    brushMode = activeBrushMode
                                )
                            )
                            onStrokeAdded()
                        }
                    }
                    activePointers.clear()
                    activePointersHue.clear()
                }
            }
            .testTag("multitouch_rainbow_canvas")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val redrawTrigger = liveTick
            // 1. Draw committed strokes
            strokes.forEach { stroke ->
                drawRenderedStroke(stroke)
            }

            // 2. Draw active live strokes under each finger simultaneously
            activePointers.forEach { (_, pts) ->
                if (pts.isNotEmpty()) {
                    drawRenderedStroke(
                        MultitouchStroke(
                            points = pts,
                            color = selectedColor,
                            strokeWidth = strokeWidth,
                            brushMode = activeBrushMode
                        )
                    )

                    // Draw glowing pulsating touch indicator under finger tip
                    val tip = pts.last().offset
                    val tipHue = pts.last().hue
                    val indicatorColor = if (activeBrushMode == CanvasBrushMode.RAINBOW_TRAIL) {
                        Color.hsl(tipHue, 0.95f, 0.55f)
                    } else {
                        selectedColor
                    }

                    drawCircle(
                        color = indicatorColor.copy(alpha = 0.35f),
                        radius = strokeWidth * 1.1f,
                        center = tip
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = strokeWidth * 0.45f,
                        center = tip
                    )
                }
            }

            // 3. Draw trailing glowing sparkles
            trailingSparkles.forEach { s ->
                drawCircle(
                    color = s.color.copy(alpha = s.alpha.coerceIn(0f, 1f)),
                    radius = s.size,
                    center = Offset(s.x, s.y)
                )
                drawCircle(
                    color = Color.White.copy(alpha = (s.alpha * 0.7f).coerceIn(0f, 1f)),
                    radius = s.size * 0.45f,
                    center = Offset(s.x, s.y)
                )
            }
        }
    }
}

/**
 * High-performance, silky smooth Bezier path rendering with rainbow gradients and glow.
 */
private fun DrawScope.drawRenderedStroke(stroke: MultitouchStroke) {
    if (stroke.points.isEmpty()) return

    if (stroke.points.size == 1) {
        val pt = stroke.points[0]
        val circleColor = if (stroke.brushMode == CanvasBrushMode.RAINBOW_TRAIL) {
            Color.hsl(pt.hue, 0.95f, 0.55f)
        } else {
            stroke.color
        }
        drawCircle(
            color = circleColor,
            radius = stroke.strokeWidth / 2f,
            center = pt.offset
        )
        return
    }

    when (stroke.brushMode) {
        CanvasBrushMode.RAINBOW_TRAIL -> {
            // Draw multi-colored rainbow ribbon with smooth segmented Bezier curves
            for (i in 1 until stroke.points.size) {
                val p0 = stroke.points[i - 1]
                val p1 = stroke.points[i]
                val segmentColor = Color.hsl(p1.hue, 0.95f, 0.55f)
                val glowColor = Color.hsl(p1.hue, 0.95f, 0.75f).copy(alpha = 0.4f)

                // Soft outer glow
                drawLine(
                    color = glowColor,
                    start = p0.offset,
                    end = p1.offset,
                    strokeWidth = stroke.strokeWidth * 1.6f,
                    cap = StrokeCap.Round
                )
                // Core vibrant rainbow line
                drawLine(
                    color = segmentColor,
                    start = p0.offset,
                    end = p1.offset,
                    strokeWidth = stroke.strokeWidth,
                    cap = StrokeCap.Round
                )
                // Inner highlight core
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = p0.offset,
                    end = p1.offset,
                    strokeWidth = stroke.strokeWidth * 0.35f,
                    cap = StrokeCap.Round
                )
            }
        }
        CanvasBrushMode.NEON_GLOW -> {
            val path = createSmoothBezierPath(stroke.points.map { it.offset })
            // Outer neon aura
            drawPath(
                path = path,
                color = stroke.color.copy(alpha = 0.35f),
                style = Stroke(
                    width = stroke.strokeWidth * 1.8f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            // Core color
            drawPath(
                path = path,
                color = stroke.color,
                style = Stroke(
                    width = stroke.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            // White core shine
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.65f),
                style = Stroke(
                    width = stroke.strokeWidth * 0.35f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
        CanvasBrushMode.MAGIC_BUBBLES -> {
            for (i in 0 until stroke.points.size step 2) {
                val pt = stroke.points[i].offset
                val bSize = stroke.strokeWidth * (0.8f + (i % 3) * 0.25f)
                drawCircle(
                    color = stroke.color.copy(alpha = 0.4f),
                    radius = bSize,
                    center = pt
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f),
                    radius = bSize * 0.3f,
                    center = Offset(pt.x - bSize * 0.3f, pt.y - bSize * 0.3f)
                )
            }
        }
        else -> {
            val path = createSmoothBezierPath(stroke.points.map { it.offset })
            drawPath(
                path = path,
                color = stroke.color,
                style = Stroke(
                    width = stroke.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

private fun createSmoothBezierPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path

    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val p0 = points[i - 1]
        val p1 = points[i]
        val midX = (p0.x + p1.x) / 2f
        val midY = (p0.y + p1.y) / 2f
        path.quadraticBezierTo(p0.x, p0.y, midX, midY)
    }
    path.lineTo(points.last().x, points.last().y)
    return path
}
