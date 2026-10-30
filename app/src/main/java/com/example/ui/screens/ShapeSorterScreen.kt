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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.runtime.withFrameNanos
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
    LaunchedEffect(Unit) { soundManager.preloadRaw(listOf("fx_popper", "fx_rocket", "fx_boom")) }

    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    var round by remember { mutableIntStateOf(0) }
    var interacted by remember { mutableStateOf(false) }
    var activity by remember { mutableIntStateOf(0) }
    var celebrating by remember { mutableStateOf(false) }

    // Ступенчатая сложность: чем выше уровень, тем больше фигурок; последний уровень — все одного цвета (только форма)
    val counts = if (age.isToddler2) intArrayOf(2, 3, 3, 4) else intArrayOf(3, 4, 5, 6, 6)
    val maxLevel = counts.size - 1
    val prefs = remember { context.getSharedPreferences("playground_prefs", android.content.Context.MODE_PRIVATE) }
    val levelKey = "sorter_level_${age.id}"
    var level by remember(age.id) { mutableIntStateOf(try { prefs.getInt(levelKey, 0).coerceIn(0, maxLevel) } catch (e: Exception) { 0 }) }
    var levelUpBanner by remember { mutableStateOf(false) }
    val neutral = !age.isToddler2 && level >= 4
    val fx = remember { SorterFx() }
    var fxTick by remember { mutableIntStateOf(0) }

    val kinds = remember(round, level, age.id) {
        SorterShapeKind.values().toList().shuffled().take(counts[level])
    }
    val layout = remember(kinds, sizePx, round, level) {
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
                    haptics.vibratePop()
                    scope.launch {
                        // хлопушки с двух сторон
                        soundManager.play("fx_popper", volume = 1f)
                        fx.popper(sizePx.width.toFloat(), sizePx.height.toFloat(), left = true)
                        fx.popper(sizePx.width.toFloat(), sizePx.height.toFloat(), left = false)
                        haptics.vibratePop()
                        delay(450)
                        // три залпа салюта
                        for (k in 0 until 3) {
                            soundManager.play("fx_rocket", volume = 0.8f)
                            val bx = sizePx.width * (0.2f + 0.3f * k + (Math.random().toFloat() - 0.5f) * 0.1f)
                            val by = sizePx.height * (0.22f + Math.random().toFloat() * 0.16f)
                            fx.rocket(bx, sizePx.height.toFloat(), by)
                            delay(520)
                            soundManager.play("fx_boom", volume = 0.9f)
                            fx.burst(bx, by)
                            haptics.vibratePop()
                            delay(380)
                        }
                        delay(900)
                        celebrating = false
                        if (level < maxLevel) {
                            level++
                            try { prefs.edit().putInt(levelKey, level).apply() } catch (e: Exception) { }
                            levelUpBanner = true
                            delay(1600)
                            levelUpBanner = false
                        }
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
                        drawPath(path, if (neutral) Color(0xFF90A4AE) else p.kind.color)
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

            // Частицы салюта и хлопушек
            LaunchedEffect(celebrating) {
                var last = 0L
                while (celebrating || fx.alive()) {
                    withFrameNanos { now ->
                        val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                        last = now
                        fx.step(dt)
                        fxTick++
                    }
                }
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val t = fxTick
                fx.draw(this)
            }

            // Плашка уровня
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp, end = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.9f),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Уровень ${level + 1}", fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, color = Color(0xFF5D3A1A))
                        for (i in 0..maxLevel) {
                            Box(
                                Modifier
                                    .size(if (i == level) 14.dp else 10.dp)
                                    .background(if (i <= level) Color(0xFFFFB300) else Color(0xFFD7CCC8), androidx.compose.foundation.shape.CircleShape)
                            )
                        }
                    }
                }
            }
            if (neutral && !celebrating) {
                Text(
                    "Все фигурки одного цвета — смотри на форму!",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                    fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color(0xFF5D3A1A)
                )
            }
            if (levelUpBanner) {
                val pulse = rememberInfiniteTransition(label = "sorter_lvl")
                val sc by pulse.animateFloat(0.92f, 1.1f, infiniteRepeatable(tween(350), RepeatMode.Reverse), label = "lvlScale")
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Уровень ${level + 1}! 🌟",
                        fontSize = 40.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                        color = Color(0xFFFF6F00),
                        modifier = Modifier.graphicsLayer { scaleX = sc; scaleY = sc }
                    )
                }
            }
        }
    }
}

/** Частицы: конфетти из хлопушек, ракеты и взрывы салюта. */
private class SorterFx {
    private class P(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Color, var life: Float, val size: Float, val strip: Boolean, val g: Float)
    private class R(var x: Float, var y: Float, val topY: Float, val vy: Float)
    private val ps = ArrayList<P>()
    private val rockets = ArrayList<R>()
    private val cols = listOf(Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF40C4FF), Color(0xFF69F0AE), Color(0xFFE040FB), Color(0xFFFF9100), Color.White)
    fun alive() = ps.isNotEmpty() || rockets.isNotEmpty()

    fun popper(w: Float, h: Float, left: Boolean) {
        val ox = if (left) w * 0.04f else w * 0.96f
        val oy = h * 0.96f
        val dir = if (left) 1f else -1f
        repeat(70) {
            val a = (-60f + Math.random().toFloat() * 45f) * Math.PI.toFloat() / 180f
            val sp = (500f + Math.random().toFloat() * 900f)
            ps.add(P(ox, oy, dir * kotlin.math.cos(a) * sp * 0.8f, kotlin.math.sin(a) * sp * 1.25f,
                cols.random(), 1.9f + Math.random().toFloat(), 8f + Math.random().toFloat() * 10f, Math.random() < 0.5, 700f))
        }
    }
    fun rocket(x: Float, fromY: Float, topY: Float) { rockets.add(R(x, fromY, topY, -(fromY - topY) / 0.52f)) }
    fun burst(x: Float, y: Float) {
        val c1 = cols.random(); val c2 = cols.random()
        repeat(80) {
            val a = it / 80f * 6.2832f
            val sp = 220f + Math.random().toFloat() * 360f
            ps.add(P(x, y, kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp, if (it % 2 == 0) c1 else c2, 1.3f + Math.random().toFloat() * 0.5f, 7f, false, 260f))
        }
        repeat(25) {
            val a = Math.random().toFloat() * 6.2832f
            val sp = Math.random().toFloat() * 140f
            ps.add(P(x, y, kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp, Color.White, 1f, 5f, false, 200f))
        }
    }
    fun step(dt: Float) {
        val ri = rockets.iterator()
        while (ri.hasNext()) {
            val r = ri.next(); r.y += r.vy * dt
            ps.add(P(r.x, r.y, (Math.random().toFloat() - 0.5f) * 40f, 60f, Color(0xFFFFE082), 0.35f, 5f, false, 0f))
            if (r.y <= r.topY) ri.remove()
        }
        val it = ps.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.vy += p.g * dt; p.vx *= (1f - 0.9f * dt); p.x += p.vx * dt; p.y += p.vy * dt
            p.life -= dt
            if (p.life <= 0f) it.remove()
        }
    }
    fun draw(d: androidx.compose.ui.graphics.drawscope.DrawScope) {
        for (p in ps) {
            val a = p.life.coerceIn(0f, 1f)
            if (p.strip) {
                d.drawLine(p.color.copy(alpha = a), Offset(p.x, p.y), Offset(p.x - p.vx * 0.03f, p.y - p.vy * 0.03f), p.size * 0.5f)
            } else {
                d.drawCircle(p.color.copy(alpha = a), p.size * (0.5f + 0.5f * a), Offset(p.x, p.y))
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
