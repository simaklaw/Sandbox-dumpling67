package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import com.example.shared.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.HintHand
import com.example.shared.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class KineticTool(val title: String, val icon: String) {
    MOLD_STAR("Звезда", "⭐"),
    MOLD_CASTLE("Замок", "🏰"),
    MOLD_HEART("Сердце", "💖"),
    MOLD_MUSHROOM("Гриб", "🍄"),
    KNIFE("Ножик", "🔪"),
    PAW("Лапка", "🐾"),
    SQUISH("Ручка", "🖐️")
}

data class SandStamp(
    val id: Int,
    val x: Float,
    val y: Float,
    val tool: KineticTool,
    val color: Color,
    val scale: Float = 1f,
    val rotation: Float = 0f
)

data class CutLine(
    val start: Offset,
    val end: Offset,
    val color: Color
)

data class SandGrain(
    val id: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val size: Float,
    var alpha: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KineticSandScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    onBackClick: () -> Unit = {}
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    // 5 Vibrant kinetic sand colors
    val sandColors = listOf(
        Pair("Бирюзовый", Color(0xFF00BCD4)),
        Pair("Розовый", Color(0xFFFF4081)),
        Pair("Солнечный", Color(0xFFFFCA28)),
        Pair("Фиолетовый", Color(0xFFAB47BC)),
        Pair("Мятный", Color(0xFF26A69A))
    )
    var selectedColorIndex by remember { mutableStateOf(0) }
    val activeSandColor = sandColors[selectedColorIndex].second

    var activeTool by remember { mutableStateOf(KineticTool.MOLD_STAR) }

    val stamps = remember { mutableStateListOf<SandStamp>() }
    val cutLines = remember { mutableStateListOf<CutLine>() }
    val grains = remember { mutableStateListOf<SandGrain>() }

    var showHint by remember { mutableStateOf(true) }

    // Spawn sand grains for tactile ASMR visual burst
    fun spawnGrains(x: Float, y: Float, color: Color, count: Int = 10) {
        for (i in 0 until count) {
            val ang = Random.nextFloat() * 2f * PI.toFloat()
            val spd = Random.nextFloat() * 160f + 60f
            grains.add(
                SandGrain(
                    id = Random.nextInt(),
                    x = x,
                    y = y,
                    vx = cos(ang) * spd,
                    vy = sin(ang) * spd - 30f,
                    color = color.copy(alpha = Random.nextFloat() * 0.4f + 0.6f),
                    size = Random.nextFloat() * 6f + 3f,
                    alpha = 1f
                )
            )
        }
    }

    // Grains update loop
    LaunchedEffect(grains.size) {
        if (grains.isNotEmpty()) {
            while (grains.isNotEmpty()) {
                withFrameNanos { _ ->
                    val it = grains.iterator()
                    while (it.hasNext()) {
                        val g = it.next()
                        g.x += g.vx * 0.016f
                        g.y += g.vy * 0.016f
                        g.vy += 320f * 0.016f
                        g.alpha -= 0.03f
                        if (g.alpha <= 0f) {
                            it.remove()
                        }
                    }
                }
            }
        }
    }

    // Smooth Sand Tray smoothing animation
    val waveSmoothing = remember { Animatable(0f) }

    fun clearTray() {
        soundPlayer.playZoneTransition()
        haptics.vibrateSquish()
        scope.launch {
            waveSmoothing.snapTo(0f)
            waveSmoothing.animateTo(1f, tween(600, easing = EaseOutCubic))
            stamps.clear()
            cutLines.clear()
            grains.clear()
            waveSmoothing.snapTo(0f)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Кинетический песок 🏖️", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(sandColors[selectedColorIndex].first, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                // Общая кнопка «Домой» (80 dp) рисуется поверх верхнего левого угла — оставляем под неё место
                navigationIcon = { androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(88.dp)) },
                actions = {
                    IconButton(
                        onClick = {
                            selectedColorIndex = (selectedColorIndex + 1) % sandColors.size
                            soundPlayer.playBubblePop()
                            haptics.vibratePop()
                        },
                        modifier = Modifier.testTag("sand_color_btn")
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = "Цвет песка", tint = activeSandColor)
                    }
                    IconButton(
                        onClick = { clearTray() },
                        modifier = Modifier.testTag("sand_refresh_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Разгладить песок")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = activeSandColor.copy(alpha = 0.2f)
                )
            )
        },
        bottomBar = {
            // Tool selector bar
            Surface(
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KineticTool.values().forEach { tool ->
                        val isSelected = activeTool == tool
                        FilledTonalIconButton(
                            onClick = {
                                activeTool = tool
                                soundPlayer.playBubblePop()
                                haptics.vibratePop()
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (isSelected) activeSandColor else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("tool_${tool.name}")
                        ) {
                            Text(tool.icon, fontSize = 22.sp)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF3ECE0)),
            contentAlignment = Alignment.Center
        ) {
            // Main Sandbox Canvas Area
            var lastDragPoint by remember { mutableStateOf<Offset?>(null) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(activeSandColor.copy(alpha = 0.85f), activeSandColor)
                        )
                    )
                    .border(6.dp, Color(0xFFD7CCC8), RoundedCornerShape(32.dp))
                    .pointerInput(activeTool, activeSandColor) {
                        detectTapGestures { offset ->
                            showHint = false
                            when (activeTool) {
                                KineticTool.KNIFE -> {
                                    // Knife tap: small slice mark
                                    soundPlayer.playSandCrunch()
                                    haptics.vibratePop()
                                    cutLines.add(
                                        CutLine(
                                            start = Offset(offset.x - 35f, offset.y - 35f),
                                            end = Offset(offset.x + 35f, offset.y + 35f),
                                            color = Color.Black.copy(alpha = 0.25f)
                                        )
                                    )
                                    spawnGrains(offset.x, offset.y, activeSandColor, 12)
                                }
                                KineticTool.SQUISH -> {
                                    // Squish thumb imprint
                                    soundPlayer.playSquishElastic()
                                    haptics.vibrateSquish()
                                    stamps.add(
                                        SandStamp(
                                            id = Random.nextInt(),
                                            x = offset.x,
                                            y = offset.y,
                                            tool = KineticTool.SQUISH,
                                            color = activeSandColor,
                                            scale = Random.nextFloat() * 0.3f + 0.9f
                                        )
                                    )
                                    spawnGrains(offset.x, offset.y, activeSandColor, 8)
                                }
                                else -> {
                                    // Stamping a mold (Star, Castle, Heart, Mushroom, Paw)
                                    soundPlayer.playMoldStamp()
                                    haptics.vibrateSquish()
                                    stamps.add(
                                        SandStamp(
                                            id = Random.nextInt(),
                                            x = offset.x,
                                            y = offset.y,
                                            tool = activeTool,
                                            color = activeSandColor,
                                            scale = 1f,
                                            rotation = Random.nextFloat() * 20f - 10f
                                        )
                                    )
                                    spawnGrains(offset.x, offset.y, activeSandColor, 14)
                                }
                            }
                        }
                    }
                    .pointerInput(activeTool, activeSandColor) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                showHint = false
                                lastDragPoint = offset
                                if (activeTool == KineticTool.KNIFE) {
                                    soundPlayer.playSandCrunch()
                                    haptics.vibrate(12)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val current = change.position
                                val prev = lastDragPoint
                                if (prev != null) {
                                    if (activeTool == KineticTool.KNIFE) {
                                        cutLines.add(
                                            CutLine(
                                                start = prev,
                                                end = current,
                                                color = Color.Black.copy(alpha = 0.22f)
                                            )
                                        )
                                        if (Random.nextFloat() < 0.35f) {
                                            soundPlayer.playSandCrunch()
                                            haptics.vibrate(10)
                                            spawnGrains(current.x, current.y, activeSandColor, 4)
                                        }
                                    } else if (activeTool == KineticTool.SQUISH) {
                                        if (Random.nextFloat() < 0.25f) {
                                            stamps.add(
                                                SandStamp(
                                                    id = Random.nextInt(),
                                                    x = current.x,
                                                    y = current.y,
                                                    tool = KineticTool.SQUISH,
                                                    color = activeSandColor,
                                                    scale = 0.7f
                                                )
                                            )
                                            spawnGrains(current.x, current.y, activeSandColor, 3)
                                        }
                                    }
                                }
                                lastDragPoint = current
                            },
                            onDragEnd = {
                                lastDragPoint = null
                            },
                            onDragCancel = {
                                lastDragPoint = null
                            }
                        )
                    }
            ) {
                // Canvas layer for sand surface, stamps, cuts and grains
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // 1. Subtle tactile sand grain texture
                    for (i in 0 until 180) {
                        val gx = (i * 73f) % w
                        val gy = (i * 127f) % h
                        drawCircle(
                            color = Color.White.copy(alpha = 0.18f),
                            radius = 2.dp.toPx(),
                            center = Offset(gx, gy)
                        )
                    }

                    // 2. Render all stamps (3D relief effect with shadow & highlight)
                    for (stamp in stamps) {
                        drawSandStamp(stamp)
                    }

                    // 3. Render all cut lines
                    for (cut in cutLines) {
                        drawLine(
                            color = cut.color,
                            start = cut.start,
                            end = cut.end,
                            strokeWidth = 7.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.4f),
                            start = cut.start - Offset(2f, 2f),
                            end = cut.end - Offset(2f, 2f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // 4. Render flying sand grains
                    for (g in grains) {
                        drawCircle(
                            color = g.color.copy(alpha = g.alpha.coerceIn(0f, 1f)),
                            radius = g.size,
                            center = Offset(g.x, g.y)
                        )
                    }

                    // 5. Wave smoothing animation if tray is being cleared
                    if (waveSmoothing.value > 0f) {
                        val waveX = w * waveSmoothing.value
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.6f), Color.Transparent),
                                startX = waveX - 100f,
                                endX = waveX + 100f
                            ),
                            topLeft = Offset.Zero,
                            size = size
                        )
                    }
                }

                // First Launch Hint for Toddlers
                if (showHint && stamps.isEmpty() && cutLines.isEmpty()) {
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        HintHand(
                            from = Offset(0f, 0f),
                            mode = "tap",
                            delayMs = 300
                        )
                    }
                }
            }
        }
    }
}

// Draw realistic clay/sand 3D embossed stamp
private fun DrawScope.drawSandStamp(stamp: SandStamp) {
    val center = Offset(stamp.x, stamp.y)
    val baseR = 36.dp.toPx() * stamp.scale

    rotate(stamp.rotation, pivot = center) {
        // Drop shadow for depth
        drawCircle(
            color = Color.Black.copy(alpha = 0.2f),
            radius = baseR + 4f,
            center = center + Offset(4f, 6f)
        )

        // Mold Body
        val moldColor = stamp.color.copy(alpha = 0.95f)
        drawCircle(
            color = moldColor,
            radius = baseR,
            center = center
        )

        // Light specular emboss ring
        drawCircle(
            color = Color.White.copy(alpha = 0.45f),
            radius = baseR - 4f,
            center = center - Offset(2f, 2f),
            style = Stroke(width = 3.dp.toPx())
        )

        // Center Icon embossed shape
        when (stamp.tool) {
            KineticTool.MOLD_STAR -> {
                drawStarRelief(center, baseR * 0.6f)
            }
            KineticTool.MOLD_HEART -> {
                drawHeartRelief(center, baseR * 0.55f)
            }
            KineticTool.MOLD_CASTLE -> {
                // Castle crenellations
                drawRect(
                    color = Color.Black.copy(alpha = 0.25f),
                    topLeft = center - Offset(baseR * 0.5f, baseR * 0.4f),
                    size = Size(baseR, baseR * 0.7f)
                )
            }
            KineticTool.MOLD_MUSHROOM -> {
                drawCircle(color = Color.Black.copy(alpha = 0.25f), radius = baseR * 0.45f, center = center - Offset(0f, 6f))
                drawRect(color = Color.Black.copy(alpha = 0.2f), topLeft = center - Offset(8f, 0f), size = Size(16f, baseR * 0.5f))
            }
            KineticTool.PAW -> {
                // Main pad
                drawCircle(color = Color.Black.copy(alpha = 0.25f), radius = baseR * 0.35f, center = center + Offset(0f, 6f))
                // 3 Toes
                drawCircle(color = Color.Black.copy(alpha = 0.25f), radius = baseR * 0.14f, center = center + Offset(-baseR * 0.28f, -baseR * 0.25f))
                drawCircle(color = Color.Black.copy(alpha = 0.25f), radius = baseR * 0.16f, center = center + Offset(0f, -baseR * 0.35f))
                drawCircle(color = Color.Black.copy(alpha = 0.25f), radius = baseR * 0.14f, center = center + Offset(baseR * 0.28f, -baseR * 0.25f))
            }
            KineticTool.SQUISH -> {
                // Soft thumb indent
                drawCircle(color = Color.Black.copy(alpha = 0.15f), radius = baseR * 0.7f, center = center)
            }
            else -> {}
        }
    }
}

private fun DrawScope.drawStarRelief(center: Offset, size: Float) {
    val path = Path()
    val points = 5
    val outerR = size
    val innerR = size * 0.45f
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outerR else innerR
        val angle = (i * PI / points - PI / 2).toFloat()
        val x = center.x + cos(angle) * r
        val y = center.y + sin(angle) * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = Color.Black.copy(alpha = 0.25f))
    drawPath(path = path, color = Color.White.copy(alpha = 0.35f), style = Stroke(width = 2.dp.toPx()))
}

private fun DrawScope.drawHeartRelief(center: Offset, size: Float) {
    val path = Path().apply {
        moveTo(center.x, center.y + size * 0.6f)
        cubicTo(
            center.x - size * 0.9f, center.y - size * 0.1f,
            center.x - size * 0.6f, center.y - size * 0.8f,
            center.x, center.y - size * 0.35f
        )
        cubicTo(
            center.x + size * 0.6f, center.y - size * 0.8f,
            center.x + size * 0.9f, center.y - size * 0.1f,
            center.x, center.y + size * 0.6f
        )
        close()
    }
    drawPath(path = path, color = Color.Black.copy(alpha = 0.25f))
    drawPath(path = path, color = Color.White.copy(alpha = 0.35f), style = Stroke(width = 2.dp.toPx()))
}
