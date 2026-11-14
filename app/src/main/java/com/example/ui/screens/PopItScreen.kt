package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.SoundManager
import com.example.shared.i18n.Text
import com.example.shared.rememberHaptics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** style: 0 — обычные цвета, 1 — перламутр (переливается), 2 — «хамелеон»: каждая ячейка плавно меняет весь спектр. */
private class PiBoard(val name: String, val cols: Int, val rows: Int, val mask: (Int, Int) -> Boolean, val rainbowByRow: Boolean, val style: Int = 0)

private val PI_RAINBOW = listOf(
    Color(0xFFEF5350), Color(0xFFFF9800), Color(0xFFFFD600), Color(0xFF66BB6A),
    Color(0xFF29B6F6), Color(0xFF5C6BC0), Color(0xFFAB47BC), Color(0xFFEC407A)
)
private val PI_CANDY = listOf(
    Color(0xFFFF5C8A), Color(0xFF40C4FF), Color(0xFFFFD740), Color(0xFF69F0AE), Color(0xFFB388FF), Color(0xFFFF9E40)
)

private val PI_BOARDS = listOf(
    PiBoard("Радуга", 6, 8, { _, _ -> true }, true),
    PiBoard("Перламутр", 7, 7, { c, r -> hypot(c - 3f, r - 3f) <= 3.2f }, false, 1),
    PiBoard("Сердце", 7, 6, { c, r ->
        val x = (c - 3f) / 3.2f; val y = (2.2f - r) / 3f
        val a = x * x + y * y - 1f
        a * a * a - x * x * y * y * y <= 0.02f
    }, false),
    PiBoard("Конфетки", 5, 5, { _, _ -> true }, false),
    PiBoard("Хамелеон", 6, 6, { _, _ -> true }, false, 2)
)

/** Цвет ячейки; для переливающихся стилей зависит от кадра. */
private fun piColor(boardIdx: Int, board: PiBoard, c: Int, r: Int, frame: Int): Color = when {
    board.style == 1 -> Color.hsv(((c * 31 + r * 23 + frame * 0.9f) % 360f + 360f) % 360f, 0.38f, 1f)
    board.style == 2 -> Color.hsv(((c * 47 + r * 29 + frame * 1.6f) % 360f + 360f) % 360f, 0.85f, 1f)
    board.rainbowByRow -> PI_RAINBOW[r % PI_RAINBOW.size]
    boardIdx == 2 -> if ((c + r) % 2 == 0) Color(0xFFFF5C8A) else Color(0xFFFF8FB1)
    else -> PI_CANDY[(c * 2 + r * 3) % PI_CANDY.size]
}

private fun piRand(k: Int): Float {
    val v = sin(k * 12.9898f) * 43758.547f
    return v - kotlin.math.floor(v)
}

private class PiSpark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val c: Color)

@Composable
fun PopItScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val sm = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    LaunchedEffect(Unit) {
        sm.preloadRaw((1..6).map { "pi_in_$it" } + (1..6).map { "pi_out_$it" })
    }

    var boardIdx by remember { mutableStateOf(0) }
    val board = PI_BOARDS[boardIdx]
    val maxCells = 64
    // true = вдавлен
    val pushed = remember { BooleanArray(maxCells) }
    val anim = remember { FloatArray(maxCells) }
    val vel = remember { FloatArray(maxCells) }
    val sparks = remember { mutableListOf<PiSpark>() }
    var frame by remember { mutableStateOf(0) }
    var count by remember { mutableStateOf(0) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var celebrate by remember { mutableStateOf(0f) }
    var lastSound by remember { mutableStateOf(0L) }

    fun total(): Int {
        var n = 0
        for (r in 0 until board.rows) for (c in 0 until board.cols) if (board.mask(c, r)) n++
        return n
    }
    fun recount() { var n = 0; for (r in 0 until board.rows) for (c in 0 until board.cols) if (board.mask(c, r) && pushed[r * board.cols + c]) n++; count = n }

    fun layout(sz: Size): Triple<Float, Float, Float> {
        val pad = 34f
        val cell = minOf((sz.width - pad * 2) / board.cols, (sz.height - pad * 2) / board.rows)
        return Triple(cell, (sz.width - cell * board.cols) / 2f, (sz.height - cell * board.rows) / 2f)
    }

    fun colorOf(c: Int, r: Int): Color = piColor(boardIdx, board, c, r, frame)

    fun toggle(i: Int) {
        val c = i % board.cols; val r = i / board.cols
        pushed[i] = !pushed[i]
        val (cell, ox, oy) = layout(canvasSize)
        val cx = ox + (c + 0.5f) * cell; val cy = oy + (r + 0.5f) * cell
        val idx = 1 + ((c + r) % 6)
        val now = System.currentTimeMillis()
        if (now - lastSound > 22) {
            lastSound = now
            sm.play(if (pushed[i]) "pi_in_$idx" else "pi_out_$idx", volume = 1f, rate = 0.98f + Random.nextFloat() * 0.05f)
        }
        if (pushed[i]) haptics.vibrate(14L) else haptics.vibrate(8L)
        val col = colorOf(c, r)
        for (k in 0 until 5) {
            val a = Random.nextFloat() * 2f * PI.toFloat(); val s = cell * (0.02f + Random.nextFloat() * 0.04f)
            sparks.add(PiSpark(cx, cy, cos(a) * s, sin(a) * s, 1f, col.copy(alpha = 0.9f)))
        }
        recount()
        if (count == total() && pushed[i]) { celebrate = 1f; soundPlayer.playSparkleChime(); haptics.vibrateSquish() }
    }

    fun flipBoard() {
        // переворот: что было вдавлено, стало выпуклым и наоборот — как у настоящего поп-ита
        for (i in 0 until maxCells) pushed[i] = !pushed[i]
        sm.play("pi_out_3", volume = 0.9f, rate = 0.8f)
        haptics.vibrateSquish()
        recount()
    }

    fun reset() {
        for (i in 0 until maxCells) pushed[i] = false
        count = 0; celebrate = 0f
    }

    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { } // физика «на кадр» вне лямбды: прореживать нельзя, скорость сменилась бы
            for (i in 0 until maxCells) {
                val t = if (pushed[i]) 1f else 0f
                // пружинка: щёлкает и чуть «пружинит»
                vel[i] = vel[i] * 0.66f + (t - anim[i]) * 0.30f
                anim[i] += vel[i]
                if (abs(t - anim[i]) < 0.003f && abs(vel[i]) < 0.003f) { anim[i] = t; vel[i] = 0f }
            }
            val it = sparks.iterator()
            while (it.hasNext()) { val s = it.next(); s.x += s.vx; s.y += s.vy; s.vy += 0.12f; s.life -= 0.05f; if (s.life <= 0f) it.remove() }
            if (celebrate > 0f) celebrate = (celebrate - 0.012f).coerceAtLeast(0f)
            frame++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A2468), Color(0xFF14123A))))
            .testTag("pop_it_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Поп-ит", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Вдавлено: $count / ${total()}", color = Color(0xFFFFD54F), fontSize = 13.sp)
                }
                Surface(
                    shape = RoundedCornerShape(20.dp), color = Color(0xFF4338CA),
                    modifier = Modifier.clickable { boardIdx = (boardIdx + 1) % PI_BOARDS.size; reset() }
                ) {
                    Text("🔷 " + board.name, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Canvas(
                    modifier = Modifier.fillMaxSize().pointerInput(boardIdx) {
                        awaitEachGesture {
                            fun cellAt(p: Offset): Int {
                                val (cell, ox, oy) = layout(canvasSize)
                                val c = ((p.x - ox) / cell).toInt(); val r = ((p.y - oy) / cell).toInt()
                                if (c !in 0 until board.cols || r !in 0 until board.rows || !board.mask(c, r)) return -1
                                val cx = ox + (c + 0.5f) * cell; val cy = oy + (r + 0.5f) * cell
                                return if (hypot(p.x - cx, p.y - cy) < cell * 0.52f) r * board.cols + c else -1
                            }
                            // одно нажатие = одна ячейка; провести пальцем нельзя — нужно отпустить и нажать снова
                            val down = awaitFirstDown()
                            down.consume()
                            val first = cellAt(down.position)
                            if (first >= 0) toggle(first)
                            while (true) {
                                val ev = awaitPointerEvent()
                                ev.changes.forEach { ch ->
                                    if (ch.changedToDown()) { val i = cellAt(ch.position); if (i >= 0) toggle(i) } // второй палец — отдельное нажатие
                                    ch.consume()
                                }
                                if (ev.changes.none { it.pressed }) break
                            }
                        }
                    }
                ) {
                    canvasSize = size
                    val f = frame
                    drawBoard(board, boardIdx, anim, sparks, layout(size), celebrate, f) { c, r -> piColor(boardIdx, board, c, r, f) }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF4338CA), modifier = Modifier.weight(1f).clickable { flipBoard() }) {
                    Box(Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        Text("🔃 Перевернуть", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF6D28D9), modifier = Modifier.weight(1f).clickable { reset() }) {
                    Box(Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        Text("🔄 Сброс", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawBoard(
    board: PiBoard, boardIdx: Int, anim: FloatArray, sparks: List<PiSpark>,
    lay: Triple<Float, Float, Float>, celebrate: Float, frame: Int,
    colorOf: (Int, Int) -> Color
) {
    val (cell, ox, oy) = lay
    val w = cell * board.cols; val h = cell * board.rows
    // силиконовая основа
    val base = Color(0xFF7C3AED)
    if (boardIdx == 1) {
        drawCircle(Color.Black.copy(alpha = 0.3f), w / 2f + 26f, Offset(size.width / 2f + 4f, size.height / 2f + 10f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFA78BFA), base, Color(0xFF5B21B6)), Offset(size.width / 2f, size.height / 2f), w / 2f + 26f), w / 2f + 24f, Offset(size.width / 2f, size.height / 2f))
    } else if (boardIdx == 2) {
        // сердце: большая подложка из кругов
        for (r in 0 until board.rows) for (c in 0 until board.cols) if (board.mask(c, r)) {
            drawCircle(Color.Black.copy(alpha = 0.2f), cell * 0.72f, Offset(ox + (c + 0.5f) * cell + 3f, oy + (r + 0.5f) * cell + 7f))
        }
        for (r in 0 until board.rows) for (c in 0 until board.cols) if (board.mask(c, r)) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFFC4B5FD), base), Offset(ox + (c + 0.5f) * cell, oy + (r + 0.5f) * cell), cell * 0.8f), cell * 0.7f, Offset(ox + (c + 0.5f) * cell, oy + (r + 0.5f) * cell))
        }
    } else {
        drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(ox - 22f + 4f, oy - 22f + 10f), Size(w + 44f, h + 44f), CornerRadius(60f))
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFFA78BFA), base, Color(0xFF5B21B6)), Offset(ox, oy), Offset(ox + w, oy + h)), Offset(ox - 22f, oy - 22f), Size(w + 44f, h + 44f), CornerRadius(60f))
        drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(ox - 22f, oy - 22f), Size(w + 44f, h + 44f), CornerRadius(60f), style = Stroke(3f))
    }
    // фактура силикона: мелкая «зернистость» листа
    for (k in 0 until 160) {
        val x = ox - 20f + piRand(k * 3 + 1) * (w + 40f); val y = oy - 20f + piRand(k * 3 + 2) * (h + 40f)
        drawCircle((if (k % 2 == 0) Color.White else Color.Black).copy(alpha = 0.05f), 1.2f + piRand(k * 3 + 3) * 1.8f, Offset(x, y))
    }
    for (r in 0 until board.rows) for (c in 0 until board.cols) {
        if (!board.mask(c, r)) continue
        val i = r * board.cols + c
        drawPopCell(Offset(ox + (c + 0.5f) * cell, oy + (r + 0.5f) * cell), cell * 0.40f, colorOf(c, r), anim[i], i, board.style)
    }
    for (s in sparks) drawCircle(s.c.copy(alpha = s.life.coerceIn(0f, 1f)), 5f * s.life.coerceIn(0.3f, 1f), Offset(s.x, s.y))
    if (celebrate > 0f) {
        val t = 1f - celebrate
        for (k in 0 until 24) {
            val a = k * (2f * PI.toFloat() / 24f)
            val d = t * size.minDimension * 0.55f
            drawCircle(PI_RAINBOW[k % PI_RAINBOW.size].copy(alpha = celebrate), 9f * celebrate + 3f, Offset(size.width / 2f + cos(a) * d, size.height / 2f + sin(a) * d))
        }
    }
}

/** Одна ячейка: выпуклый глянцевый купол или вдавленная чашка с рельефом. a: 0 — выпуклая, 1 — вдавлена. */
private fun DrawScope.drawPopCell(ctr: Offset, R: Float, col: Color, aRaw: Float, seed: Int, style: Int) {
    val a = aRaw.coerceIn(0f, 1f)
    val cx = ctr.x; val cy = ctr.y
    val dark = colorLerp(col, Color.Black, 0.5f)
    val deep = colorLerp(col, Color.Black, 0.68f)
    val light = colorLerp(col, Color.White, 0.6f)
    // гнездо в листе: фаска, тень, полость
    drawCircle(Color.Black.copy(alpha = 0.30f), R * 1.22f, Offset(cx + R * 0.03f, cy + R * 0.07f))
    drawCircle(
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.42f), Color.White.copy(alpha = 0.26f)), cy - R * 1.16f, cy + R * 1.16f),
        R * 1.16f, ctr
    )
    drawCircle(Color(0xFF241250).copy(alpha = 0.62f), R * 1.04f, ctr)
    if (a < 0.5f) {
        // ───── выпуклый купол ─────
        val rr = R * (1f - 0.12f * a)
        val lc = Offset(cx - rr * 0.32f, cy - rr * 0.40f)
        drawCircle(
            Brush.radialGradient(
                0f to light, 0.30f to colorLerp(col, Color.White, 0.22f), 0.68f to col, 1f to dark,
                center = lc, radius = rr * 1.5f
            ), rr, ctr
        )
        // подповерхностное свечение и переотражённый свет у нижнего края
        drawCircle(colorLerp(col, Color.White, 0.4f).copy(alpha = 0.28f), rr * 0.5f, Offset(cx + rr * 0.26f, cy + rr * 0.32f))
        drawArc(light.copy(alpha = 0.5f), 25f, 130f, false, Offset(cx - rr * 0.84f, cy - rr * 0.84f), Size(rr * 1.68f, rr * 1.68f), style = Stroke(rr * 0.10f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        if (style != 0) { // перламутровая плёнка: второй оттенок с другой стороны
            val h2 = Color.hsv(((col.red * 360f + col.green * 200f + 120f) % 360f), 0.55f, 1f)
            drawCircle(h2.copy(alpha = 0.32f), rr * 0.62f, Offset(cx + rr * 0.3f, cy + rr * 0.3f))
            drawCircle(Color.White.copy(alpha = 0.12f), rr * 0.9f, Offset(cx - rr * 0.1f, cy - rr * 0.1f), style = Stroke(rr * 0.07f))
        }
        // фактура: микро-точки
        for (k in 0 until 8) {
            val ang = piRand(seed * 17 + k) * 6.283f; val d = piRand(seed * 29 + k) * rr * 0.8f
            drawCircle((if (k % 2 == 0) Color.White else Color.Black).copy(alpha = 0.07f), rr * 0.025f + 0.8f, Offset(cx + cos(ang) * d, cy + sin(ang) * d))
        }
        drawCircle(dark.copy(alpha = 0.6f), rr, ctr, style = Stroke(R * 0.05f))
        // блики: окно-отражение и точка
        rotate(-28f, Offset(cx - rr * 0.25f, cy - rr * 0.5f)) {
            drawOval(Color.White.copy(alpha = 0.42f), Offset(cx - rr * 0.65f, cy - rr * 0.76f), Size(rr * 0.80f, rr * 0.40f))
        }
        drawCircle(Color.White.copy(alpha = 0.95f), rr * 0.09f, Offset(cx - rr * 0.42f, cy - rr * 0.52f))
        drawCircle(Color.White.copy(alpha = 0.6f), rr * 0.045f, Offset(cx - rr * 0.28f, cy - rr * 0.6f))
    } else {
        // ───── вдавленная чашка ─────
        val rr = R * 0.93f
        val lc = Offset(cx + rr * 0.30f, cy + rr * 0.36f) // свет попадает внутрь снизу-справа
        drawCircle(
            Brush.radialGradient(
                0f to colorLerp(col, Color.White, 0.30f), 0.42f to colorLerp(col, Color.Black, 0.12f), 0.8f to dark, 1f to deep,
                center = lc, radius = rr * 1.45f
            ), rr, ctr
        )
        // глубокая тень от верхне-левой стенки
        drawArc(Color.Black.copy(alpha = 0.50f), 190f, 120f, false, Offset(cx - rr * 0.86f, cy - rr * 0.86f), Size(rr * 1.72f, rr * 1.72f), style = Stroke(rr * 0.18f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        // блик на противоположной стенке
        drawArc(Color.White.copy(alpha = 0.50f), 15f, 95f, false, Offset(cx - rr * 0.84f, cy - rr * 0.84f), Size(rr * 1.68f, rr * 1.68f), style = Stroke(rr * 0.08f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        // складки продавленного силикона
        for (k in 0 until 3) {
            drawArc(Color.White.copy(alpha = 0.13f), 40f + k * 95f, 36f, false, Offset(cx - rr * 0.52f, cy - rr * 0.52f), Size(rr * 1.04f, rr * 1.04f), style = Stroke(rr * 0.03f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        if (style != 0) drawCircle(Color.White.copy(alpha = 0.10f), rr * 0.45f, Offset(cx + rr * 0.2f, cy + rr * 0.22f))
        drawCircle(deep.copy(alpha = 0.75f), rr, ctr, style = Stroke(R * 0.06f))
        drawCircle(Color.White.copy(alpha = 0.22f), rr * 1.07f, Offset(cx, cy + R * 0.03f), style = Stroke(1.6f))
    }
}
