package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.screens.Shapes.*
import com.example.screens.Squish.SquishParticle
import com.example.shared.*
import com.example.shared.scene.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

/**
 * 🔷 Игра 3: «Фигурки-окошки» (ШАГ 14)
 * - Сортер: большой ящик с лицом и окошками разных форм (2 шт в 2yo, 3-6 в 3-5yo).
 * - Нижняя полка: фигурки (≥96 dp в 3-5yo, 130 dp в 2yo).
 * - Магнит: если ближе 90 dp (140 dp в 2yo) к своему окошку -> snap_in с радостью ящика.
 * - Тап по фигурке: она сама летит к своему окошку (помощь малышу).
 * - Неправильное окошко: мягкий отскок на полку, soft_no, ящик качает головой.
 * - При заполнении всех окошек: праздник (fanfare_short, салют) и переход на следующий уровень.
 */
@Composable
fun FaceAntistressScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val ageProfile = LocalAgeProfile.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // SharedPreferences for level progression (Hidden from toddler, no numbers)
    val prefs = remember { context.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE) }
    var currentLevel by remember {
        val saved = prefs.getInt("shapes_level", 1)
        mutableIntStateOf(saved)
    }

    // Shapes in current level
    val shapesForLevel = remember(currentLevel, ageProfile.isToddler2) {
        if (ageProfile.isToddler2) {
            if (currentLevel >= 3) {
                listOf(SorterShapeKind.CIRCLE, SorterShapeKind.SQUARE, SorterShapeKind.TRIANGLE)
            } else {
                listOf(SorterShapeKind.CIRCLE, SorterShapeKind.SQUARE)
            }
        } else {
            when (currentLevel) {
                1 -> listOf(SorterShapeKind.CIRCLE, SorterShapeKind.SQUARE, SorterShapeKind.TRIANGLE)
                2 -> listOf(SorterShapeKind.CIRCLE, SorterShapeKind.SQUARE, SorterShapeKind.TRIANGLE, SorterShapeKind.STAR)
                3 -> listOf(SorterShapeKind.CIRCLE, SorterShapeKind.SQUARE, SorterShapeKind.TRIANGLE, SorterShapeKind.STAR, SorterShapeKind.HEART)
                else -> SorterShapeKind.values().toList()
            }
        }
    }

    // Pieces and Window Holes
    val pieces = remember { mutableStateListOf<SorterPiece>() }
    val holes = remember { mutableStateListOf<SorterWindowHole>() }

    // Sorter Box Happy / Sad Face State
    var boxMood by remember { mutableStateOf("happy") }
    var boxJumpOffset by remember { mutableFloatStateOf(0f) }

    // Celebration & particles
    val particles = remember { mutableStateListOf<SquishParticle>() }
    var isCelebrationActive by remember { mutableStateOf(false) }

    // Magnet snapping threshold (90 dp in 3-5yo, 140 dp in 2yo)
    val magnetThresholdDp = if (ageProfile.isToddler2) 140f else 90f

    // Decor for Room theme
    val roomDecors = remember {
        listOf(
            DecorSpec(DecorKind.BALL, 0.12f, 0.15f, 64f),
            DecorSpec(DecorKind.YARN, 0.88f, 0.18f, 60f)
        )
    }

    // Initialize level layout
    fun setupLevel() {
        pieces.clear()
        holes.clear()

        val count = shapesForLevel.size
        shapesForLevel.forEachIndexed { idx, kind ->
            holes.add(
                SorterWindowHole(
                    id = idx,
                    kind = kind,
                    centerPos = Offset.Zero // will be set in Canvas
                )
            )

            pieces.add(
                SorterPiece(
                    id = idx,
                    kind = kind,
                    currentPos = Offset.Zero,
                    homeShelfPos = Offset.Zero
                )
            )
        }
    }

    LaunchedEffect(currentLevel, ageProfile.isToddler2) {
        setupLevel()
    }

    // Check level completion
    fun checkCompletion() {
        if (holes.all { it.isFilled } && !isCelebrationActive) {
            isCelebrationActive = true
            soundManager.play("fanfare_short")
            haptics.vibrate(40)

            coroutineScope.launch {
                // Celebration animation
                boxMood = "celebrate"
                delay(1800)

                // Advance level
                currentLevel++
                prefs.edit().putInt("shapes_level", currentLevel).apply()
                isCelebrationActive = false
                boxMood = "happy"
                setupLevel()
            }
        }
    }

    // Snap piece to window
    fun snapPiece(piece: SorterPiece, hole: SorterWindowHole) {
        piece.isSnapped = true
        hole.isFilled = true
        piece.currentPos = hole.centerPos
        soundManager.play("snap_in")
        soundManager.play(piece.kind.voiceSoundKey)
        haptics.vibrateShort()

        // Box joy
        boxMood = "joy"
        coroutineScope.launch {
            delay(600)
            boxMood = "happy"
        }

        checkCompletion()
    }

    // Reject piece (soft_no)
    fun rejectPiece(piece: SorterPiece) {
        soundManager.play("soft_no")
        haptics.vibrate(15)
        boxMood = "reject"

        coroutineScope.launch {
            // Animate back to shelf
            val startPos = piece.currentPos
            val anim = Animatable(0f)
            anim.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) {
                piece.currentPos = Offset(
                    startPos.x + (piece.homeShelfPos.x - startPos.x) * value,
                    startPos.y + (piece.homeShelfPos.y - startPos.y) * value
                )
            }
            piece.currentPos = piece.homeShelfPos
            boxMood = "happy"
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val screenWidthDp = maxWidth.value
        val screenHeightDp = maxHeight.value

        // 1. Vector Room Background
        SceneBackground(theme = SceneTheme.ROOM)

        // 2. Harmless background decor
        DecorLayer(items = roomDecors)

        // 3. Main Sorter Canvas Layer
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pieces.size) {
                    detectTapGestures(
                        onTap = { tapPos ->
                            // Tap on piece -> Auto fly to its hole
                            for (piece in pieces) {
                                if (!piece.isSnapped && !piece.isAnimatingToWindow) {
                                    if ((tapPos - piece.currentPos).getDistance() <= 60.dp.toPx()) {
                                        val targetHole = holes.firstOrNull { it.kind == piece.kind && !it.isFilled }
                                        targetHole?.let { hole ->
                                            piece.isAnimatingToWindow = true
                                            coroutineScope.launch {
                                                soundManager.play("pickup")
                                                val startPos = piece.currentPos
                                                val anim = Animatable(0f)
                                                anim.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) {
                                                    piece.currentPos = Offset(
                                                        startPos.x + (hole.centerPos.x - startPos.x) * value,
                                                        startPos.y + (hole.centerPos.y - startPos.y) * value
                                                    )
                                                }
                                                piece.isAnimatingToWindow = false
                                                snapPiece(piece, hole)
                                            }
                                        }
                                        return@detectTapGestures
                                    }
                                }
                            }
                        }
                    )
                }
                .pointerInput(pieces.size) {
                    var draggedPiece: SorterPiece? = null

                    detectDragGestures(
                        onDragStart = { startPos ->
                            for (piece in pieces) {
                                if (!piece.isSnapped && !piece.isAnimatingToWindow) {
                                    if ((startPos - piece.currentPos).getDistance() <= 60.dp.toPx()) {
                                        draggedPiece = piece
                                        piece.isDragging = true
                                        soundManager.play("pickup")
                                        haptics.vibrate(15)
                                        break
                                    }
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            draggedPiece?.let { piece ->
                                piece.currentPos += dragAmount

                                // Check Magnet Auto-Snap immediately while dragging
                                for (hole in holes) {
                                    if (!hole.isFilled) {
                                        val dist = (piece.currentPos - hole.centerPos).getDistance()
                                        if (dist <= magnetThresholdDp.dp.toPx()) {
                                            if (hole.kind == piece.kind) {
                                                piece.isDragging = false
                                                draggedPiece = null
                                                snapPiece(piece, hole)
                                                return@detectDragGestures
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            draggedPiece?.let { piece ->
                                piece.isDragging = false

                                // Check if dropped near wrong hole
                                var hitWrongHole = false
                                for (hole in holes) {
                                    if (!hole.isFilled && (piece.currentPos - hole.centerPos).getDistance() <= magnetThresholdDp.dp.toPx()) {
                                        if (hole.kind != piece.kind) {
                                            hitWrongHole = true
                                            break
                                        }
                                    }
                                }

                                if (hitWrongHole) {
                                    rejectPiece(piece)
                                } else {
                                    // Return to shelf smoothly
                                    coroutineScope.launch {
                                        val startPos = piece.currentPos
                                        val anim = Animatable(0f)
                                        anim.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) {
                                            piece.currentPos = Offset(
                                                startPos.x + (piece.homeShelfPos.x - startPos.x) * value,
                                                startPos.y + (piece.homeShelfPos.y - startPos.y) * value
                                            )
                                        }
                                        piece.currentPos = piece.homeShelfPos
                                    }
                                }
                                draggedPiece = null
                            }
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

            // A. BIG WOODEN SORTER BOX (Upper half)
            val boxW = w * 0.82f
            val boxH = h * 0.38f
            val boxTopLeft = Offset((w - boxW) / 2f, h * 0.16f + boxJumpOffset)

            // Box Body
            drawRoundRect(
                color = Color(0xFFFFD180),
                topLeft = boxTopLeft,
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
            )
            drawRoundRect(
                color = ColorInk,
                topLeft = boxTopLeft,
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                style = inkStroke
            )

            // Box Face (Eyes & Mouth on front)
            val boxFaceCenter = Offset(w / 2f, boxTopLeft.y + boxH * 0.80f)
            drawBoxFace(boxFaceCenter, mood = boxMood)

            // B. HOLES ON THE BOX LID
            val holeRadius = if (ageProfile.isToddler2) 38.dp.toPx() else 30.dp.toPx()
            val cols = if (holes.size <= 3) holes.size else (holes.size + 1) / 2
            val rows = if (holes.size <= 3) 1 else 2

            holes.forEachIndexed { index, hole ->
                val r = index / cols
                val c = index % cols
                val hX = boxTopLeft.x + (boxW / (cols + 1)) * (c + 1)
                val hY = boxTopLeft.y + (boxH * 0.55f / (rows + 1)) * (r + 1)
                hole.centerPos = Offset(hX, hY)

                // Draw Hole Slot (Dark recessed socket with color border)
                drawShapeSlot(hole.kind, hole.centerPos, holeRadius, hole.isFilled)
            }

            // C. BOTTOM WOODEN SHELF
            val shelfY = h * 0.72f
            drawLine(color = Color(0xFFBCAAA4), start = Offset(w * 0.08f, shelfY), end = Offset(w * 0.92f, shelfY), strokeWidth = 12.dp.toPx(), cap = StrokeCap.Round)
            drawLine(color = ColorInk, start = Offset(w * 0.08f, shelfY), end = Offset(w * 0.92f, shelfY), strokeWidth = 14.dp.toPx(), cap = StrokeCap.Round, blendMode = BlendMode.DstOver)

            // D. PIECES ON SHELF OR DRAGGING
            val pieceRadius = if (ageProfile.isToddler2) 42.dp.toPx() else 34.dp.toPx()
            pieces.forEachIndexed { index, piece ->
                val shelfSpacing = (w * 0.84f) / (pieces.size + 1)
                piece.homeShelfPos = Offset(w * 0.08f + shelfSpacing * (index + 1), shelfY + 44.dp.toPx())

                if (piece.currentPos == Offset.Zero) {
                    piece.currentPos = piece.homeShelfPos
                }

                if (!piece.isSnapped || isCelebrationActive) {
                    val scale = if (piece.isDragging) 1.15f else 1.0f
                    drawSorterPiece(piece.kind, piece.currentPos, pieceRadius * scale)
                }
            }
        }
    }
}

/**
 * Draws sorter socket hole.
 */
private fun DrawScope.drawShapeSlot(kind: SorterShapeKind, center: Offset, radius: Float, isFilled: Boolean) {
    val inkStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val slotColor = if (isFilled) kind.color.copy(alpha = 0.4f) else Color(0xFF5D4037).copy(alpha = 0.25f)

    when (kind) {
        SorterShapeKind.CIRCLE -> {
            drawCircle(color = slotColor, radius = radius, center = center)
            drawCircle(color = kind.color, radius = radius, center = center, style = inkStroke)
        }
        SorterShapeKind.SQUARE -> {
            drawRoundRect(color = slotColor, topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2f, radius * 2f), cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()))
            drawRoundRect(color = kind.color, topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2f, radius * 2f), cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()), style = inkStroke)
        }
        SorterShapeKind.TRIANGLE -> {
            val triPath = Path().apply {
                moveTo(center.x, center.y - radius)
                lineTo(center.x + radius, center.y + radius)
                lineTo(center.x - radius, center.y + radius)
                close()
            }
            drawPath(triPath, color = slotColor)
            drawPath(triPath, color = kind.color, style = inkStroke)
        }
        SorterShapeKind.STAR -> {
            drawStarSlot(center, radius, slotColor, kind.color)
        }
        SorterShapeKind.HEART -> {
            drawHeartSlot(center, radius, slotColor, kind.color)
        }
        SorterShapeKind.OVAL -> {
            drawOval(color = slotColor, topLeft = Offset(center.x - radius * 1.2f, center.y - radius * 0.8f), size = Size(radius * 2.4f, radius * 1.6f))
            drawOval(color = kind.color, topLeft = Offset(center.x - radius * 1.2f, center.y - radius * 0.8f), size = Size(radius * 2.4f, radius * 1.6f), style = inkStroke)
        }
    }
}

/**
 * Draws glossy sorter piece.
 */
private fun DrawScope.drawSorterPiece(kind: SorterShapeKind, center: Offset, radius: Float) {
    val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Shadow
    drawCircle(color = ColorInk.copy(alpha = 0.15f), radius = radius * 1.05f, center = Offset(center.x, center.y + 4.dp.toPx()))

    when (kind) {
        SorterShapeKind.CIRCLE -> {
            drawCircle(color = kind.color, radius = radius, center = center)
            drawCircle(color = ColorInk, radius = radius, center = center, style = inkStroke)
        }
        SorterShapeKind.SQUARE -> {
            drawRoundRect(color = kind.color, topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2f, radius * 2f), cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()))
            drawRoundRect(color = ColorInk, topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2f, radius * 2f), cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()), style = inkStroke)
        }
        SorterShapeKind.TRIANGLE -> {
            val triPath = Path().apply {
                moveTo(center.x, center.y - radius)
                lineTo(center.x + radius, center.y + radius)
                lineTo(center.x - radius, center.y + radius)
                close()
            }
            drawPath(triPath, color = kind.color)
            drawPath(triPath, color = ColorInk, style = inkStroke)
        }
        SorterShapeKind.STAR -> {
            drawStarSlot(center, radius, kind.color, ColorInk)
        }
        SorterShapeKind.HEART -> {
            drawHeartSlot(center, radius, kind.color, ColorInk)
        }
        SorterShapeKind.OVAL -> {
            drawOval(color = kind.color, topLeft = Offset(center.x - radius * 1.2f, center.y - radius * 0.8f), size = Size(radius * 2.4f, radius * 1.6f))
            drawOval(color = ColorInk, topLeft = Offset(center.x - radius * 1.2f, center.y - radius * 0.8f), size = Size(radius * 2.4f, radius * 1.6f), style = inkStroke)
        }
    }

    // Specular Catchlight
    drawCircle(color = ColorWhite.copy(alpha = 0.45f), radius = radius * 0.35f, center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f))
}

private fun DrawScope.drawStarSlot(center: Offset, radius: Float, fillColor: Color, strokeColor: Color) {
    val starPath = Path()
    val points = 5
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius else radius * 0.48f
        val angle = (i * PI / points).toFloat() - (PI / 2f).toFloat()
        val px = center.x + cos(angle) * r
        val py = center.y + sin(angle) * r
        if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
    }
    starPath.close()
    drawPath(starPath, color = fillColor)
    drawPath(starPath, color = strokeColor, style = Stroke(width = 2.5.dp.toPx()))
}

private fun DrawScope.drawHeartSlot(center: Offset, radius: Float, fillColor: Color, strokeColor: Color) {
    val heartPath = Path().apply {
        moveTo(center.x, center.y + radius * 0.7f)
        cubicTo(center.x - radius * 1.2f, center.y - radius * 0.3f, center.x - radius * 0.6f, center.y - radius, center.x, center.y - radius * 0.4f)
        cubicTo(center.x + radius * 0.6f, center.y - radius, center.x + radius * 1.2f, center.y - radius * 0.3f, center.x, center.y + radius * 0.7f)
        close()
    }
    drawPath(heartPath, color = fillColor)
    drawPath(heartPath, color = strokeColor, style = Stroke(width = 2.5.dp.toPx()))
}

private fun DrawScope.drawBoxFace(center: Offset, mood: String) {
    val eyeSpacing = 28.dp.toPx()
    val leftEye = Offset(center.x - eyeSpacing, center.y)
    val rightEye = Offset(center.x + eyeSpacing, center.y)

    // Cheeks
    drawCircle(color = ColorPink.copy(alpha = 0.6f), radius = 8.dp.toPx(), center = Offset(leftEye.x - 10.dp.toPx(), leftEye.y + 10.dp.toPx()))
    drawCircle(color = ColorPink.copy(alpha = 0.6f), radius = 8.dp.toPx(), center = Offset(rightEye.x + 10.dp.toPx(), rightEye.y + 10.dp.toPx()))

    if (mood == "joy" || mood == "celebrate") {
        // Happy squint arcs
        val lArc = Path().apply {
            moveTo(leftEye.x - 8.dp.toPx(), leftEye.y)
            quadraticBezierTo(leftEye.x, leftEye.y - 8.dp.toPx(), leftEye.x + 8.dp.toPx(), leftEye.y)
        }
        val rArc = Path().apply {
            moveTo(rightEye.x - 8.dp.toPx(), rightEye.y)
            quadraticBezierTo(rightEye.x, rightEye.y - 8.dp.toPx(), rightEye.x + 8.dp.toPx(), rightEye.y)
        }
        drawPath(lArc, color = ColorInk, style = Stroke(width = 2.5.dp.toPx()))
        drawPath(rArc, color = ColorInk, style = Stroke(width = 2.5.dp.toPx()))
    } else {
        drawCircle(color = ColorInk, radius = 5.dp.toPx(), center = leftEye)
        drawCircle(color = ColorInk, radius = 5.dp.toPx(), center = rightEye)
    }

    // Smile
    val smilePath = Path().apply {
        moveTo(center.x - 14.dp.toPx(), center.y + 10.dp.toPx())
        quadraticBezierTo(center.x, center.y + 20.dp.toPx(), center.x + 14.dp.toPx(), center.y + 10.dp.toPx())
    }
    drawPath(smilePath, color = ColorInk, style = Stroke(width = 2.5.dp.toPx()))
}
