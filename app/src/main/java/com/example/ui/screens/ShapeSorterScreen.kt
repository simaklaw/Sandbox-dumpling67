package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import com.example.shared.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.screens.Shapes.SorterPiece
import com.example.screens.Shapes.SorterShapeKind
import com.example.screens.Shapes.SorterWindowHole
import com.example.shared.ColorInk
import com.example.shared.HintHand
import com.example.shared.LocalAgeProfile
import com.example.shared.SoundManager
import com.example.shared.rememberHaptics
import com.example.shared.scene.SceneBackground
import com.example.shared.scene.SceneTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ============================================================================
// 🔷 ФИГУРКИ-ОКОШКИ (Шаг 14): сортер. Перетащи фигурку в окошко своей формы.
// 2 года: 3 фигурки, подсвеченное окошко, тап по фигурке сам «отправляет» её (tapDuplicateDrag).
// 3–5 лет: 6 фигурок, без подсветки; чужое окошко мягко «не пускает».
// Нет проигрыша. Когда все на местах — салют, и раунд начинается заново с новым набором.
// ============================================================================

private class SorterLayout(
    val pieceSize: Float,                       // px
    val windows: SnapshotStateList<SorterWindowHole>,
    val pieces: SnapshotStateList<SorterPiece>,
    val panelTop: Float,
    val panelBottom: Float,
    val shelfTop: Float
)

private fun buildSorterLayout(
    kinds: List<SorterShapeKind>,
    size: IntSize,
    dens: Float
): SorterLayout {
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val rows = (kinds.size + 2) / 3
    val topPad = 96f * dens        // место под кнопку «Домой»
    val bottomPad = 20f * dens
    val available = h - topPad - bottomPad
    val ps = min(w / 3.6f, available / (rows * 2 * 1.3f + 0.7f))
    val rowH = ps * 1.3f

    val windows = mutableStateListOf<SorterWindowHole>()
    val windowKinds = kinds.shuffled()
    windowKinds.forEachIndexed { i, kind ->
        val col = i % 3
        val row = i / 3
        val inRow = min(3, kinds.size - row * 3)
        val cx = w * (col + 0.5f + (3 - inRow) / 2f) / 3f
        val cy = topPad + ps * 0.65f + row * rowH
        windows.add(SorterWindowHole(id = i, kind = kind, centerPos = Offset(cx, cy)))
    }

    val pieces = mutableStateListOf<SorterPiece>()
    val shelfKinds = kinds.shuffled()
    shelfKinds.forEachIndexed { i, kind ->
        val col = i % 3
        val row = i / 3
        val inRow = min(3, kinds.size - row * 3)
        val cx = w * (col + 0.5f + (3 - inRow) / 2f) / 3f
        val cy = h - bottomPad - ps * 0.65f - (rows - 1 - row) * rowH
        val home = Offset(cx, cy)
        pieces.add(SorterPiece(id = i, kind = kind, currentPos = home, homeShelfPos = home))
    }

    return SorterLayout(
        pieceSize = ps,
        windows = windows,
        pieces = pieces,
        panelTop = topPad - 8f * dens,
        panelBottom = topPad + ps * 0.65f + (rows - 1) * rowH + ps * 0.75f,
        shelfTop = h - bottomPad - ps * 0.65f - (rows - 1) * rowH - ps * 0.75f
    )
}

@Composable
fun ShapeSorterScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val dens = LocalDensity.current.density
    val age = LocalAgeProfile.current
    val haptics = rememberHaptics()
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val scope = rememberCoroutineScope()

    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    var round by remember { mutableIntStateOf(0) }
    var interacted by remember { mutableStateOf(false) }
    var activity by remember { mutableIntStateOf(0) }
    var celebrating by remember { mutableStateOf(false) }

    // В 2 года берём 3 случайные формы из 6, в 3–5 — все шесть
    val kinds = remember(round, age.id) {
        val all = SorterShapeKind.values().toList().shuffled()
        if (age.isToddler2) all.take(3) else all
    }
    val layout = remember(kinds, sizePx, round) {
        if (sizePx.width == 0 || sizePx.height == 0) null else buildSorterLayout(kinds, sizePx, dens)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { sizePx = it }
    ) {
        SceneBackground(theme = SceneTheme.ROOM)

        if (layout != null) {
            val ps = layout.pieceSize
            val pieces = layout.pieces
            val windows = layout.windows
            val draggingKind = pieces.firstOrNull { it.isDragging }?.kind

            fun indexOfPiece(id: Int) = pieces.indexOfFirst { it.id == id }

            suspend fun slide(id: Int, to: Offset, ms: Int) {
                val i0 = indexOfPiece(id)
                if (i0 < 0) return
                val from = pieces[i0].currentPos
                animate(0f, 1f, animationSpec = tween(ms)) { v, _ ->
                    val k = indexOfPiece(id)
                    if (k >= 0) pieces[k] = pieces[k].copy(currentPos = lerp(from, to, v))
                }
            }

            fun checkComplete() {
                if (!celebrating && pieces.all { it.isSnapped }) {
                    celebrating = true
                    soundManager.play("rainbow_chime")
                    haptics.vibratePop()
                    scope.launch {
                        delay(2600)
                        celebrating = false
                        round++ // новый набор фигурок
                    }
                }
            }

            fun settle(pieceId: Int, window: SorterWindowHole) {
                scope.launch {
                    val i0 = indexOfPiece(pieceId)
                    if (i0 < 0) return@launch
                    pieces[i0] = pieces[i0].copy(isDragging = false, isAnimatingToWindow = true)
                    slide(pieceId, window.centerPos, 170)
                    val i = indexOfPiece(pieceId)
                    val wi = windows.indexOfFirst { it.id == window.id }
                    if (i >= 0) pieces[i] = pieces[i].copy(isSnapped = true, isAnimatingToWindow = false)
                    if (wi >= 0) windows[wi] = windows[wi].copy(isFilled = true)
                    soundManager.play("snap_in")
                    soundManager.play(window.kind.voiceSoundKey) // голос: «Круг!»
                    haptics.vibratePop()
                    checkComplete()
                }
            }

            fun goHome(pieceId: Int) {
                scope.launch {
                    val i0 = indexOfPiece(pieceId)
                    if (i0 < 0) return@launch
                    val home = pieces[i0].homeShelfPos
                    pieces[i0] = pieces[i0].copy(isDragging = false, isAnimatingToWindow = true)
                    slide(pieceId, home, 260)
                    val i = indexOfPiece(pieceId)
                    if (i >= 0) pieces[i] = pieces[i].copy(isAnimatingToWindow = false)
                }
            }

            fun release(pieceId: Int) {
                val i = indexOfPiece(pieceId)
                if (i < 0) return
                val p = pieces[i]
                val reach = ps * 0.8f * age.hitSlopMultiplier
                val target = windows.firstOrNull {
                    it.kind == p.kind && !it.isFilled && hypot(p.currentPos.x - it.centerPos.x, p.currentPos.y - it.centerPos.y) < reach
                }
                if (target != null) {
                    settle(pieceId, target)
                } else {
                    // Чужое окошко: мягкое «не-а» только для 3–5 лет
                    val overWrong = windows.any {
                        it.kind != p.kind && hypot(p.currentPos.x - it.centerPos.x, p.currentPos.y - it.centerPos.y) < ps * 0.6f
                    }
                    if (overWrong && age.allowRefusal) soundManager.play("soft_no")
                    goHome(pieceId)
                }
            }

            fun flyToOwnWindow(pieceId: Int) {
                val i = indexOfPiece(pieceId)
                if (i < 0) return
                val p = pieces[i]
                if (p.isSnapped || p.isAnimatingToWindow) return
                val target = windows.firstOrNull { it.kind == p.kind && !it.isFilled } ?: return
                interacted = true
                activity++
                settle(pieceId, target)
            }

            // Авто-демо (2 года): 10 с тишины — первая фигурка сама «залетает» в своё окошко
            LaunchedEffect(layout, activity, age.id) {
                val secs = age.autoDemoIdleDelaySeconds
                if (secs > 0) {
                    while (true) {
                        delay(secs * 1000L)
                        val p = pieces.firstOrNull { !it.isSnapped && !it.isAnimatingToWindow && !it.isDragging }
                        val w = p?.let { q -> windows.firstOrNull { it.kind == q.kind && !it.isFilled } }
                        if (p != null && w != null) settle(p.id, w)
                    }
                }
            }

            // Панель-«крышка» с окошками и полка с фигурками
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val panelRect = RoundRect(
                    Rect(w * 0.04f, layout.panelTop, w * 0.96f, layout.panelBottom),
                    CornerRadius(28.dp.toPx())
                )
                val panel = Path().apply { addRoundRect(panelRect) }
                drawPath(panel, Color(0xFFFFE0A8))
                drawPath(panel, ColorInk, style = Stroke(width = 4.dp.toPx()))

                val shelfRect = RoundRect(
                    Rect(w * 0.04f, layout.shelfTop, w * 0.96f, size.height - 8.dp.toPx()),
                    CornerRadius(28.dp.toPx())
                )
                val shelf = Path().apply { addRoundRect(shelfRect) }
                drawPath(shelf, Color(0xFFC78C4E).copy(alpha = 0.75f))
                drawPath(shelf, ColorInk, style = Stroke(width = 3.dp.toPx()))

                for (win in windows) {
                    // Тёмное «отверстие» в форме фигурки
                    val hole = sorterShapePath(win.kind, win.centerPos.x, win.centerPos.y, ps * 0.56f)
                    drawPath(hole, Color(0xFF4A3A55))
                    // 2 года: контур цвета фигурки (подсказка). 3–5: «тень» — окошко без цветной подсказки
                    val outline = if (age.isToddler2) win.kind.color.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.22f)
                    drawPath(hole, outline, style = Stroke(width = 3.dp.toPx()))
                    // 2 года: подсвечиваем окошко, пока фигурку тащат
                    if (age.isToddler2 && !win.isFilled && draggingKind == win.kind) {
                        drawPath(
                            sorterShapePath(win.kind, win.centerPos.x, win.centerPos.y, ps * 0.64f),
                            Color.White, style = Stroke(width = 6.dp.toPx())
                        )
                    }
                }
            }

            // Фигурки
            for (p in pieces) {
                key(p.id) {
                    Canvas(
                        modifier = Modifier
                            .offset { IntOffset((p.currentPos.x - ps / 2f).roundToInt(), (p.currentPos.y - ps / 2f).roundToInt()) }
                            .size((ps / dens).dp)
                            .zIndex(if (p.isDragging || p.isAnimatingToWindow) 10f else 1f)
                            .graphicsLayer {
                                val s = if (p.isDragging) 1.12f else 1f
                                scaleX = s
                                scaleY = s
                            }
                            .pointerInput(p.id, p.isSnapped, age.id) {
                                if (!p.isSnapped && age.tapDuplicateDrag) {
                                    detectTapGestures(onTap = { flyToOwnWindow(p.id) })
                                }
                            }
                            .pointerInput(p.id, p.isSnapped) {
                                if (!p.isSnapped) {
                                    detectDragGestures(
                                        onDragStart = {
                                            val i = indexOfPiece(p.id)
                                            if (i >= 0 && !pieces[i].isAnimatingToWindow) {
                                                interacted = true
                                                activity++
                                                pieces[i] = pieces[i].copy(isDragging = true)
                                                soundManager.play("tap")
                                            }
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            val i = indexOfPiece(p.id)
                                            if (i >= 0 && pieces[i].isDragging) {
                                                val cur = pieces[i].currentPos
                                                pieces[i] = pieces[i].copy(
                                                    currentPos = Offset(
                                                        (cur.x + amount.x).coerceIn(ps / 2f, sizePx.width - ps / 2f),
                                                        (cur.y + amount.y).coerceIn(ps / 2f, sizePx.height - ps / 2f)
                                                    )
                                                )
                                            }
                                        },
                                        onDragEnd = {
                                            val i = indexOfPiece(p.id)
                                            if (i >= 0 && pieces[i].isDragging) release(p.id)
                                        },
                                        onDragCancel = {
                                            val i = indexOfPiece(p.id)
                                            if (i >= 0 && pieces[i].isDragging) goHome(p.id)
                                        }
                                    )
                                }
                            }
                    ) {
                        val path = sorterShapePath(p.kind, size.width / 2f, size.height / 2f, size.width * 0.44f)
                        drawPath(path, p.kind.color)
                        drawPath(path, ColorInk, style = Stroke(width = 4.dp.toPx()))
                        // блик
                        drawCircle(
                            Color.White.copy(alpha = 0.45f),
                            radius = size.width * 0.07f,
                            center = Offset(size.width * 0.38f, size.height * 0.36f)
                        )
                    }
                }
            }

            // 👆 Подсказка до первого касания: тащим первую фигурку в её окошко
            if (!interacted) {
                val first = pieces.firstOrNull { !it.isSnapped }
                val win = first?.let { f -> windows.firstOrNull { it.kind == f.kind } }
                if (first != null && win != null) {
                    HintHand(
                        from = Offset(first.currentPos.x / dens - 20f, first.currentPos.y / dens - 10f),
                        to = Offset(win.centerPos.x / dens - 20f, win.centerPos.y / dens - 10f),
                        mode = "drag",
                        delayMs = age.hintIdleDelaySeconds * 1000L
                    )
                }
            }

            // 🎉 Салют
            if (celebrating) {
                val pulse = rememberInfiniteTransition(label = "sorter_party")
                val s by pulse.animateFloat(
                    initialValue = 0.9f, targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse),
                    label = "party_scale"
                )
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "🎉⭐🎈",
                        fontSize = 72.sp,
                        modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s }
                    )
                }
            }
        }
    }
}

/** Контур фигурки: круг / овал / квадрат / треугольник / звезда / сердце. `s` — «полуразмер». */
private fun sorterShapePath(kind: SorterShapeKind, cx: Float, cy: Float, s: Float): Path {
    val path = Path()
    when (kind) {
        SorterShapeKind.CIRCLE -> path.addOval(Rect(cx - s, cy - s, cx + s, cy + s))
        SorterShapeKind.OVAL -> path.addOval(Rect(cx - s, cy - s * 0.62f, cx + s, cy + s * 0.62f))
        SorterShapeKind.SQUARE -> {
            val q = s * 0.9f
            path.addRoundRect(RoundRect(Rect(cx - q, cy - q, cx + q, cy + q), CornerRadius(q * 0.18f)))
        }
        SorterShapeKind.TRIANGLE -> {
            path.moveTo(cx, cy - s)
            path.lineTo(cx + s * 1.02f, cy + s * 0.82f)
            path.lineTo(cx - s * 1.02f, cy + s * 0.82f)
            path.close()
        }
        SorterShapeKind.STAR -> {
            val inner = s * 0.45f
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) s else inner
                val a = (-PI / 2.0 + i * PI / 5.0).toFloat()
                val x = cx + r * cos(a)
                val y = cy + r * sin(a)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
        SorterShapeKind.HEART -> {
            path.moveTo(cx, cy + s * 0.9f)
            path.cubicTo(cx - s * 1.4f, cy - s * 0.1f, cx - s * 0.7f, cy - s * 1.1f, cx, cy - s * 0.35f)
            path.cubicTo(cx + s * 0.7f, cy - s * 1.1f, cx + s * 1.4f, cy - s * 0.1f, cx, cy + s * 0.9f)
            path.close()
        }
    }
    return path
}
