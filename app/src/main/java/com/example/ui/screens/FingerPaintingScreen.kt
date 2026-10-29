package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.example.shared.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.*
import com.example.shared.scene.*
import com.example.ui.components.CanvasBrushMode
import com.example.ui.components.MultitouchRainbowCanvas
import com.example.ui.components.MultitouchStroke
import com.example.ui.components.StampElement
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

val PAPER_COLORS = listOf(
    Color(0xFFFFFFFF), // White
    Color(0xFFFFF3B0), // Yellow
    Color(0xFFD6F0FF), // Light Blue
    Color(0xFFFFE0EE)  // Pink
)

val PALETTE_COLORS_8 = listOf(
    Color(0xFFFF1744), // Red
    Color(0xFFFF9100), // Orange
    Color(0xFFFFD600), // Yellow
    Color(0xFF00E676), // Green
    Color(0xFF00E5FF), // Cyan
    Color(0xFF2979FF), // Blue
    Color(0xFFD500F9), // Purple
    Color(0xFFFF4081)  // Pink
)

val TODDLER_PALETTE_COLORS_4 = listOf(
    Color(0xFFFF1744), // Red
    Color(0xFFFFD600), // Yellow
    Color(0xFF2979FF), // Blue
    Color(0xFF00E676)  // Green
)

val FUN_STAMPS = listOf("🐾", "⭐", "🌸", "🦆", "🥟", "🐱")

data class SpongeBubble(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    val color: Color
)

@Composable
fun CanvasZone(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    modifier: Modifier = Modifier
) {
    FingerPaintingScreen(repository = repository, soundPlayer = soundPlayer, modifier = modifier)
}

@Composable
fun FingerPaintingScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val ageProfile = LocalAgeProfile.current
    val strokes = remember { mutableStateListOf<MultitouchStroke>() }
    val stamps = remember { mutableStateListOf<StampElement>() }
    val bubbles = remember { mutableStateListOf<SpongeBubble>() }
    val gameState by repository.gameState.collectAsState()

    val colors = if (ageProfile.isToddler2) TODDLER_PALETTE_COLORS_4 else PALETTE_COLORS_8

    var selectedColor by remember { mutableStateOf(colors[0]) }
    // OFF by default: Normal solid color line mode
    var isRainbowActive by remember { mutableStateOf(false) }
    var selectedBrushMode by remember { mutableStateOf(CanvasBrushMode.PASTEL_COLOR) }
    var selectedStamp by remember { mutableStateOf(FUN_STAMPS[0]) }
    var strokeWidth by remember { mutableFloatStateOf(if (ageProfile.isToddler2) 32f else 24f) }
    var lastPaintTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showIdleHint by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val dens = LocalDensity.current.density

    LaunchedEffect(lastPaintTime, ageProfile.isToddler2) {
        if (ageProfile.isToddler2 && ageProfile.autoDemoIdleDelaySeconds > 0) {
            showIdleHint = false
            delay(ageProfile.autoDemoIdleDelaySeconds * 1000L)
            showIdleHint = true
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val clearBounce = remember { Animatable(1f) }

    // Cleaning bubble particles loop
    LaunchedEffect(Unit) {
        while (isActive) {
            if (bubbles.isNotEmpty()) {
                for (i in bubbles.indices.reversed()) {
                    val b = bubbles[i]
                    b.x += b.vx
                    b.y += b.vy
                    b.size -= 0.6f
                    if (b.size <= 0f) {
                        bubbles.removeAt(i)
                    }
                }
                delay(16)
            } else {
                delay(150)
            }
        }
    }

    fun triggerClearCanvas() {
        soundManager.play("whoosh")
        soundManager.play("chime")
        haptics.vibrate(30)

        strokes.clear()
        stamps.clear()

        repeat(28) {
            bubbles.add(
                SpongeBubble(
                    id = System.nanoTime() + it,
                    x = Random.nextFloat() * 800f + 40f,
                    y = Random.nextFloat() * 1000f + 100f,
                    vx = (Random.nextFloat() - 0.5f) * 14f,
                    vy = -(Random.nextFloat() * 10f + 4f),
                    size = Random.nextFloat() * 24f + 16f,
                    color = PALETTE_COLORS_8.random().copy(alpha = 0.7f)
                )
            )
        }

        coroutineScope.launch {
            clearBounce.snapTo(0.92f)
            clearBounce.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
        }
    }

    fun undoLastAction() {
        if (stamps.isNotEmpty() && (strokes.isEmpty() || stamps.last().id > strokes.last().id)) {
            stamps.removeAt(stamps.lastIndex)
        } else if (strokes.isNotEmpty()) {
            strokes.removeAt(strokes.lastIndex)
        }
        soundManager.play("pop")
        haptics.vibrateShort()
    }

    // Determine actual brush mode to pass to canvas
    val activeCanvasBrushMode = if (isRainbowActive) {
        CanvasBrushMode.RAINBOW_TRAIL
    } else {
        selectedBrushMode
    }

    var paperColorIndex by remember { mutableIntStateOf(0) }
    val currentPaperColor = PAPER_COLORS[paperColorIndex]

    val roomDecors = remember {
        listOf(
            DecorSpec(DecorKind.PLANT, 0.08f, 0.45f, 76f),
            DecorSpec(DecorKind.SUN, 0.90f, 0.12f, 72f, reaction = DecorReaction.SMILE),
            DecorSpec(DecorKind.STAR, 0.12f, 0.18f, 56f, reaction = DecorReaction.SPIN),
            DecorSpec(DecorKind.FLOWER, 0.92f, 0.45f, 60f),
            DecorSpec(DecorKind.BIRD, 0.90f, 0.28f, 58f),
            DecorSpec(DecorKind.FISH, 0.08f, 0.30f, 60f)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("finger_painting_screen")
    ) {
        // 1. Vector 3-Layer Scene Background: ROOM
        SceneBackground(theme = SceneTheme.ROOM)

        // 2. Harmless room decor layer
        DecorLayer(items = roomDecors)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (expanded) 2.dp else 6.dp, vertical = if (expanded) 2.dp else 3.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TOP BAR: Home space, Paper Color Toggle, Clear Sponge & Undo Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Home button space (80 dp)
                    Spacer(modifier = Modifier.size(80.dp))

                    // 📄 Paper color toggle button (72 dp touch target)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(50.dp)
                            .border(BorderStroke(3.dp, ColorInk), ShapeButton)
                            .background(currentPaperColor, ShapeButton)
                            .clip(ShapeButton)
                            .plasticineClickable(haptics = haptics) {
                                paperColorIndex = (paperColorIndex + 1) % PAPER_COLORS.size
                                soundManager.play("pop")
                                haptics.vibrateShort()
                            }
                            .testTag("paper_color_button")
                    ) {
                        Text("📄", fontSize = 24.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!ageProfile.isToddler2) {
                        // Undo button (72 dp target)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(50.dp)
                                .border(BorderStroke(3.dp, ColorInk), ShapeButton)
                                .background(ColorWhite, ShapeButton)
                                .clip(ShapeButton)
                                .plasticineClickable(haptics = haptics) {
                                    undoLastAction()
                                }
                        ) {
                            Text("↩️", fontSize = 24.sp)
                        }
                    }

                    // Увеличить / уменьшить окно рисования
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(50.dp)
                            .border(BorderStroke(3.dp, ColorInk), ShapeButton)
                            .background(if (expanded) ColorYellow else ColorWhite, ShapeButton)
                            .clip(ShapeButton)
                            .plasticineClickable(haptics = haptics) {
                                expanded = !expanded
                                soundManager.play("pop")
                            }
                            .testTag("expand_canvas_button")
                    ) {
                        Text(if (expanded) "🗗" else "⛶", fontSize = 26.sp)
                    }

                    // Clear Sponge button (72 dp target)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(50.dp)
                            .border(BorderStroke(3.dp, ColorInk), ShapeButton)
                            .background(ColorYellow, ShapeButton)
                            .clip(ShapeButton)
                            .plasticineClickable(haptics = haptics) {
                                triggerClearCanvas()
                            }
                            .testTag("clear_canvas_button")
                    ) {
                        Text("🧽", fontSize = 28.sp)
                    }
                }
            }

            // CENTER: Canvas as Sheet of Paper with Yellow Plasticine Frame and 1° lively tilt
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer { rotationZ = if (expanded) 0f else 1f }
                    .scale(clearBounce.value)
                    .clip(RoundedCornerShape(20.dp))
                    .background(currentPaperColor)
                    .border(4.dp, ColorYellow, RoundedCornerShape(20.dp))
                    .border(2.dp, ColorInk, RoundedCornerShape(20.dp))
            ) {
                MultitouchRainbowCanvas(
                    strokes = strokes,
                    stamps = stamps,
                    activeBrushMode = activeCanvasBrushMode,
                    selectedColor = selectedColor,
                    selectedStamp = selectedStamp,
                    strokeWidth = strokeWidth,
                    soundPlayer = soundPlayer,
                    onStrokeAdded = { repository.addCoins(1) },
                    palmRejectionEnabled = gameState.palmRejectionEnabled,
                    edgeMarginDp = gameState.palmRejectionMarginDp
                )

                // Render Stamps
                stamps.forEach { stamp ->
                    val stampSp = (stamp.size * 1.7f).coerceAtLeast(40f)
                    val halfDp = stampSp * 0.62f
                    Text(
                        text = stamp.emoji,
                        fontSize = stampSp.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = (stamp.x / dens - halfDp).coerceAtLeast(0f).dp,
                                top = (stamp.y / dens - halfDp).coerceAtLeast(0f).dp
                            )
                            .graphicsLayer { rotationZ = stamp.rotation }
                    )
                }

                // Render Clear Sponge Bubbles
                Canvas(modifier = Modifier.fillMaxSize()) {
                    bubbles.forEach { b ->
                        drawCircle(
                            color = b.color,
                            radius = b.size,
                            center = Offset(b.x, b.y)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.85f),
                            radius = b.size * 0.32f,
                            center = Offset(b.x - b.size * 0.3f, b.y - b.size * 0.3f)
                        )
                    }
                }
            }

            // BOTTOM CONTROL PANELS (Effects, Palette with Rainbow, Size Selector)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (expanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colors.forEach { color ->
                            val isSel = selectedColor == color && !isRainbowActive
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .border(BorderStroke(if (isSel) 4.dp else 2.dp, if (isSel) ColorInk else Color.White), CircleShape)
                                    .background(color, CircleShape)
                                    .clip(CircleShape)
                                    .plasticineClickable(haptics = haptics) {
                                        selectedColor = color
                                        isRainbowActive = false
                                        if (selectedBrushMode == CanvasBrushMode.ANIMAL_STAMP) selectedBrushMode = CanvasBrushMode.PASTEL_COLOR
                                        soundManager.play("pop")
                                    }
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .border(BorderStroke(if (isRainbowActive) 4.dp else 2.dp, if (isRainbowActive) ColorPurple else ColorInk), CircleShape)
                                .background(if (isRainbowActive) ColorWhite else ColorBgSoft, CircleShape)
                                .clip(CircleShape)
                                .plasticineClickable(haptics = haptics) {
                                    isRainbowActive = !isRainbowActive
                                    soundManager.play("magic")
                                }
                        ) { Text("🌈", fontSize = 22.sp) }
                    }
                } else {
                // Компактная панель: ряд 1 — кисти (или штампы) и размеры, ряд 2 — цвета и радуга
                val small = !ageProfile.isToddler2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedBrushMode == CanvasBrushMode.ANIMAL_STAMP) {
                        FUN_STAMPS.forEach { stamp ->
                            val isSelected = selectedStamp == stamp
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .border(BorderStroke(if (isSelected) 3.5.dp else 2.dp, if (isSelected) ColorPink else ColorInk), CircleShape)
                                    .background(if (isSelected) ColorWhite else ColorBgSoft, CircleShape)
                                    .clip(CircleShape)
                                    .plasticineClickable(haptics = haptics) {
                                        selectedStamp = stamp
                                        soundManager.play("pop")
                                    }
                            ) { Text(stamp, fontSize = 24.sp) }
                        }
                    } else {
                        val availableEffects = if (ageProfile.isToddler2) {
                            listOf(
                                CanvasBrushMode.PASTEL_COLOR to "🖌️",
                                CanvasBrushMode.NEON_GLOW to "✨",
                                CanvasBrushMode.MAGIC_BUBBLES to "🫧"
                            )
                        } else {
                            listOf(
                                CanvasBrushMode.PASTEL_COLOR to "🖌️",
                                CanvasBrushMode.NEON_GLOW to "✨",
                                CanvasBrushMode.MAGIC_BUBBLES to "🫧",
                                CanvasBrushMode.ANIMAL_STAMP to "🐾"
                            )
                        }
                        availableEffects.forEach { (mode, emoji) ->
                            val isSelected = selectedBrushMode == mode && !isRainbowActive
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(if (small) 48.dp else 58.dp)
                                    .border(BorderStroke(if (isSelected) 3.5.dp else 2.dp, if (isSelected) ColorPurple else ColorInk), ShapeButton)
                                    .background(if (isSelected) ColorWhite else ColorBgSoft, ShapeButton)
                                    .clip(ShapeButton)
                                    .plasticineClickable(haptics = haptics) {
                                        selectedBrushMode = mode
                                        isRainbowActive = false
                                        soundManager.play("chime")
                                    }
                            ) { Text(emoji, fontSize = if (small) 26.sp else 30.sp) }
                        }
                        if (small) {
                            listOf(14f to 10.dp, 28f to 18.dp, 48f to 28.dp).forEach { (size, dotR) ->
                                val isSelected = strokeWidth == size
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .border(BorderStroke(if (isSelected) 3.5.dp else 1.5.dp, if (isSelected) ColorInk else ColorInk.copy(alpha = 0.3f)), CircleShape)
                                        .background(if (isSelected) ColorYellow else ColorWhite, CircleShape)
                                        .clip(CircleShape)
                                        .plasticineClickable(haptics = haptics) {
                                            strokeWidth = size
                                            soundManager.play("pop")
                                        }
                                ) { Box(Modifier.size(dotR).clip(CircleShape).background(ColorInk)) }
                            }
                        }
                    }
                }

                val swatch = if (small) 34.dp else 52.dp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colors.forEach { color ->
                        val isSelected = selectedColor == color && !isRainbowActive
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(swatch)
                                .border(BorderStroke(if (isSelected) 4.dp else 2.dp, if (isSelected) ColorInk else Color.White), CircleShape)
                                .background(color, CircleShape)
                                .clip(CircleShape)
                                .plasticineClickable(haptics = haptics) {
                                    selectedColor = color
                                    isRainbowActive = false
                                    if (selectedBrushMode == CanvasBrushMode.ANIMAL_STAMP) selectedBrushMode = CanvasBrushMode.PASTEL_COLOR
                                    soundManager.play("pop")
                                }
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(swatch)
                            .border(BorderStroke(if (isRainbowActive) 4.dp else 2.dp, if (isRainbowActive) ColorPurple else ColorInk), CircleShape)
                            .background(if (isRainbowActive) ColorWhite else ColorBgSoft, CircleShape)
                            .clip(CircleShape)
                            .plasticineClickable(haptics = haptics) {
                                isRainbowActive = !isRainbowActive
                                soundManager.play("magic")
                            }
                    ) { Text("🌈", fontSize = if (small) 20.sp else 30.sp) }
                }
                }
            }
        }
    }
}
