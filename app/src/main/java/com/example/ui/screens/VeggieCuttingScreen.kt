package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.SoundManager
import com.example.shared.i18n.Text
import com.example.shared.rememberHaptics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private class VcType(
    val name: String, val emoji: String, val skin: Color, val skinDark: Color, val inner: Color, val detail: Color,
    val round: Boolean, val tapered: Boolean, val sound: String
)

private val VC_TYPES = listOf(
    VcType("Морковка", "🥕", Color(0xFFFF8F00), Color(0xFFE65100), Color(0xFFFFB74D), Color(0xFFFFE0B2), false, true, "carrot"),
    VcType("Огурец", "🥒", Color(0xFF43A047), Color(0xFF1B5E20), Color(0xFFDCEDC8), Color(0xFFAED581), false, false, "cucumber"),
    VcType("Помидор", "🍅", Color(0xFFE53935), Color(0xFFB71C1C), Color(0xFFFF8A80), Color(0xFFFFCDD2), true, false, "tomato"),
    VcType("Редиска", "🌸", Color(0xFFD81B60), Color(0xFF880E4F), Color(0xFFFFF3F8), Color(0xFFF8BBD0), true, false, "hard"),
    VcType("Картошка", "🥔", Color(0xFFBCAAA4), Color(0xFF6D4C41), Color(0xFFFFF9C4), Color(0xFFFFF176), true, false, "hard")
)

private class VcSlice(var x: Float, var y: Float, val tx: Float, val ty: Float, var vx: Float, var vy: Float, val d: Float, val type: Int, var rot: Float, val vrot: Float, var landed: Boolean = false)
private class VcDrop(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val c: Color, val r: Float)
private class VcBowlItem(val type: Int, val ox: Float, val oy: Float, val rot: Float)

@Composable
fun VeggieCuttingScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val sm = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    LaunchedEffect(Unit) {
        sm.preloadRaw(listOf("carrot", "cucumber", "tomato", "hard").flatMap { listOf("kit_chop_${it}_1", "kit_chop_${it}_2") } + listOf("kit_plop", "kit_done"))
    }

    var size by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableIntStateOf(0) }
    var cuts by remember { mutableIntStateOf(0) }
    var salads by remember { mutableIntStateOf(0) }
    var vegName by remember { mutableStateOf("") }
    var banner by remember { mutableStateOf("") }

    // изменяемая модель (без Compose-состояния — перерисовка по frame)
    val slices = remember { ArrayList<VcSlice>() }
    val drops = remember { ArrayList<VcDrop>() }
    val bowl = remember { ArrayList<VcBowlItem>() }
    class Model {
        // a0 — верхний край овоща (экранная y), len — оставшаяся длина вдоль вертикальной оси
        var type = 0; var len = 0f; var total = 0f; var a0 = 0f; var thick = 0f; var st = 0f
        var knifeY = 0f; var knifeDown = 0f; var slideIn = 0f; var nextAt = -1f
        var bowlClearAt = -1f; var bannerUntil = 0f; var clock = 0f; var inited = false; var lastPlop = 0f
    }
    val m = remember { Model() }

    fun W() = size.width.toFloat()
    fun H() = size.height.toFloat()
    fun cy() = H() * VC_BOARD_CY   // центр разделочной доски
    fun cx() = W() / 2f

    fun initVeg(t: Int) {
        val w = W(); val h = H()
        m.type = t
        val vt = VC_TYPES[t]
        if (vt.round) {
            m.thick = w * when (t) { 2 -> 0.42f; 3 -> 0.32f; else -> 0.40f }
            m.total = m.thick
            m.st = m.thick / 7f
        } else {
            // длинные овощи лежат вдоль доски (сверху вниз), нож идёт поперёк
            m.thick = w * if (t == 0) 0.19f else 0.17f
            m.total = h * 0.34f
            m.st = m.total / (if (t == 0) 12f else 10f)
        }
        m.a0 = cy() - m.total / 2f
        m.len = m.total
        m.knifeY = m.a0 + m.len - m.st
        m.slideIn = w
        m.nextAt = -1f
        vegName = vt.name
    }

    fun thickAt(pos: Float): Float {
        val vt = VC_TYPES[m.type]
        if (vt.round) {
            val r = m.thick / 2f
            val dx = (pos - (m.a0 + r)).coerceIn(-r, r)
            return 2f * kotlin.math.sqrt(max(0f, r * r - dx * dx))
        }
        return if (vt.tapered) m.thick * (0.45f + 0.55f * ((pos - m.a0) / m.total).coerceIn(0f, 1f)) else m.thick
    }

    fun cut() {
        if (!m.inited || m.nextAt >= 0f || m.len <= 0.5f) return
        val vt = VC_TYPES[m.type]
        val take = if (m.len - m.st < m.st * 0.55f) m.len else m.st
        val faceY = m.a0 + m.len
        val lineY = faceY - take
        val d = thickAt(lineY).coerceAtLeast(m.st)
        m.len -= take
        m.knifeDown = 1f
        cuts++
        sm.play("kit_chop_${vt.sound}_${Random.nextInt(1, 3)}", volume = 1f, rate = 0.96f + Random.nextFloat() * 0.1f)
        haptics.vibrate(12L)
        // отрезанный кусочек падает в миску под доской
        val bw = W() * 0.62f
        val tx = W() / 2f + (Random.nextFloat() - 0.5f) * bw * 0.5f
        val ty = H() * 0.76f + H() * 0.012f
        val sx = cx(); val sy = lineY + take / 2f
        val tf = 0.55f; val g = H() * 3.2f
        slices.add(VcSlice(sx, sy, tx, ty, (tx - sx) / tf, (ty - sy) / tf - 0.5f * g * tf, min(d, W() * 0.2f), m.type, 0f, (Random.nextFloat() - 0.5f) * 14f))
        // брызги сока
        repeat(6) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val sp = W() * (0.15f + Random.nextFloat() * 0.3f)
            drops.add(VcDrop(cx() + (Random.nextFloat() - 0.5f) * d * 0.6f, lineY, cos(a) * sp, sin(a) * sp - H() * 0.1f, 1f, vt.skin.copy(alpha = 0.9f), W() * 0.008f))
        }
        if (m.len <= 0.5f) {
            m.nextAt = m.clock + 0.45f
            sm.play("kit_done", volume = 0.6f, rate = 1.3f)
            repeat(14) {
                val a = Random.nextFloat() * 2f * PI.toFloat(); val sp = W() * (0.2f + Random.nextFloat() * 0.4f)
                drops.add(VcDrop(W() / 2f, cy(), cos(a) * sp, sin(a) * sp - H() * 0.1f, 1f, listOf(Color(0xFFFFD54F), Color(0xFFEF5350), Color(0xFF66BB6A)).random(), W() * 0.009f))
            }
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                m.clock += dt
                if (size.width > 0) {
                    if (!m.inited) { initVeg(0); m.inited = true }
                    m.slideIn += (0f - m.slideIn) * min(1f, dt * 12f)
                    if (m.slideIn < 0.5f) m.slideIn = 0f
                    val target = m.a0 + m.len - m.st
                    m.knifeY += (target - m.knifeY) * min(1f, dt * 22f)
                    m.knifeDown = max(0f, m.knifeDown - dt * 7f)
                    if (m.nextAt in 0f..m.clock) { initVeg((m.type + 1 + Random.nextInt(VC_TYPES.size - 1)) % VC_TYPES.size); sm.play("kit_plop", volume = 0.8f, rate = 1.25f) }
                    val g = H() * 3.2f
                    val it = slices.iterator()
                    while (it.hasNext()) {
                        val s = it.next()
                        s.x += s.vx * dt; s.y += s.vy * dt; s.vy += g * dt; s.rot += s.vrot * dt
                        if (s.vy > 0 && s.y >= s.ty) {
                            it.remove()
                            val bw = W() * 0.62f
                            bowl.add(VcBowlItem(s.type, (s.x - W() / 2f).coerceIn(-bw * 0.35f, bw * 0.35f), (Random.nextFloat() - 0.5f) * H() * 0.02f, s.rot))
                            if (m.clock - m.lastPlop > 0.05f) { sm.play("kit_plop", volume = 0.7f, rate = 0.9f + Random.nextFloat() * 0.3f); m.lastPlop = m.clock }
                            if (bowl.size >= 26 && m.bowlClearAt < 0f) {
                                salads++
                                sm.play("kit_done", volume = 1f)
                                haptics.vibrateSquish()
                                banner = "Салат готов! 🥗"
                                m.bannerUntil = m.clock + 2f
                                m.bowlClearAt = m.clock + 0.9f
                                repeat(30) {
                                    val a = Random.nextFloat() * 2f * PI.toFloat(); val sp = W() * (0.3f + Random.nextFloat() * 0.6f)
                                    drops.add(VcDrop(W() / 2f, H() * 0.72f, cos(a) * sp, sin(a) * sp - H() * 0.3f, 1.2f, listOf(Color(0xFFFFD54F), Color(0xFFEF5350), Color(0xFF66BB6A), Color(0xFF42A5F5), Color(0xFFFF8A65)).random(), W() * 0.012f))
                                }
                            }
                        }
                    }
                    if (m.bowlClearAt in 0f..m.clock) { bowl.clear(); m.bowlClearAt = -1f }
                    if (banner.isNotEmpty() && m.clock > m.bannerUntil) banner = ""
                    val di = drops.iterator()
                    while (di.hasNext()) {
                        val d = di.next()
                        d.x += d.vx * dt; d.y += d.vy * dt; d.vy += H() * 2.2f * dt; d.life -= dt * 1.8f
                        if (d.life <= 0f) di.remove()
                    }
                }
                frame++
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        e.changes.forEach { if (it.changedToDown()) { cut(); it.consume() } }
                    }
                }
            }
            .testTag("veggie_cutting_screen")
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val f = frame
            if (size.width > 0f && m.inited) drawKitchen(m.type, cx() - m.slideIn, m.a0, m.thick, m.total, m.len, m.knifeY, m.knifeDown, m.clock, cy(), slices, drops, bowl)
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
            Text("Нарезка овощей", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF4E342E))
            Text("$vegName · кусочков: $cuts · салатов: $salads", fontSize = 13.sp, color = Color(0xFF6D4C41))
        }
        if (cuts == 0) {
            Text(
                "Тапай по экрану — нож режет! 🔪", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4E342E),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)
                    .background(Color.White.copy(alpha = 0.85f), androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        if (banner.isNotEmpty()) {
            Text(banner, fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color(0xFFE65100), modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** Центр доски по высоте экрана и её высота (доля экрана). */
private const val VC_BOARD_CY = 0.36f
private const val VC_BOARD_H = 0.44f

/** Детерминированный «шум» 0..1: текстуры не должны мерцать от кадра к кадру. */
private fun vcH(i: Int): Float {
    val v = sin(i * 12.9898f) * 43758.547f
    return v - floor(v)
}

private fun vcLerpPt(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/**
 * Срез (кусочек, торец, кусочки в миске): кожура, мякоть и внутреннее строение каждого овоща.
 * Без кистей и аллокаций на каждый кадр: только слои кругов с прозрачностью.
 */
private fun DrawScope.drawDisc(cx: Float, cy: Float, d: Float, t: Int, rot: Float) {
    val vt = VC_TYPES[t]
    val r = d / 2f
    val c = Offset(cx, cy)
    drawCircle(vt.skinDark, r, c)
    drawCircle(vt.skin, r * 0.95f, c)
    when (t) {
        0 -> { // морковь: оранжевая мякоть, годовые кольца, светлая сердцевина с лучиками
            drawCircle(Color(0xFFFFA726), r * 0.88f, c)
            drawCircle(Color(0xFFFFB74D), r * 0.66f, c)
            for (i in 1..3) drawCircle(Color(0xFFE65100).copy(alpha = 0.22f), r * (0.28f + 0.17f * i), c, style = Stroke(r * 0.03f))
            drawCircle(Color(0xFFFFD699), r * 0.34f, c)
            drawCircle(Color(0xFFFFE9C6), r * 0.2f, c)
            for (k in 0 until 12) {
                val a = rot + k * (2f * PI.toFloat() / 12f)
                drawLine(Color(0xFFFFF3E0).copy(alpha = 0.55f), c + Offset(cos(a) * r * 0.2f, sin(a) * r * 0.2f), c + Offset(cos(a) * r * 0.62f, sin(a) * r * 0.62f), r * 0.025f)
            }
        }
        1 -> { // огурец: бледная мякоть, зелёное кольцо у кожуры, три камеры с семенами
            drawCircle(Color(0xFF9CCC65), r * 0.88f, c)
            drawCircle(Color(0xFFE3F0C8), r * 0.8f, c)
            drawCircle(Color(0xFFD2E6B0), r * 0.55f, c, style = Stroke(r * 0.05f))
            for (k in 0 until 3) {
                val a = rot + k * 2.0944f
                val lc = c + Offset(cos(a) * r * 0.3f, sin(a) * r * 0.3f)
                drawCircle(Color(0xFFF4F9E4), r * 0.24f, lc)
                for (q in 0 until 3) {
                    val sa = a + (q - 1) * 0.7f
                    drawOval(Color(0xFFFFF9C4), lc + Offset(cos(sa) * r * 0.12f - r * 0.03f, sin(sa) * r * 0.12f - r * 0.05f), Size(r * 0.07f, r * 0.1f))
                }
            }
            drawCircle(Color(0xFFC5E1A5), r * 0.1f, c)
        }
        2 -> { // помидор: красная мякоть, четыре камеры с желе и семенами, белые перегородки
            drawCircle(Color(0xFFE53935), r * 0.9f, c)
            drawCircle(Color(0xFFEF5350), r * 0.78f, c)
            for (k in 0 until 4) {
                val a = rot + k * (PI.toFloat() / 2f) + PI.toFloat() / 4f
                val lc = c + Offset(cos(a) * r * 0.42f, sin(a) * r * 0.42f)
                drawCircle(Color(0xFFFFAB91), r * 0.27f, lc)
                drawCircle(Color(0xFFFFCCBC).copy(alpha = 0.7f), r * 0.18f, lc + Offset(-r * 0.03f, -r * 0.03f))
                for (q in 0 until 3) {
                    val sa = a + (q - 1) * 0.9f + 0.4f
                    drawOval(Color(0xFFFFF59D), lc + Offset(cos(sa) * r * 0.13f - r * 0.025f, sin(sa) * r * 0.13f - r * 0.04f), Size(r * 0.06f, r * 0.09f))
                }
                val pa = a + PI.toFloat() / 4f
                drawLine(Color(0xFFFFCDD2), c, c + Offset(cos(pa) * r * 0.8f, sin(pa) * r * 0.8f), r * 0.05f)
            }
            drawCircle(Color(0xFFFFE0DC), r * 0.14f, c)
        }
        3 -> { // редис: белая мякоть, розовое кольцо у кожуры, тонкие прожилки
            drawCircle(Color(0xFFF48FB1), r * 0.88f, c)
            drawCircle(Color(0xFFFFFDF8), r * 0.8f, c)
            drawCircle(Color(0xFFF8BBD0).copy(alpha = 0.5f), r * 0.72f, c, style = Stroke(r * 0.07f))
            for (k in 0 until 14) {
                val a = rot + k * (2f * PI.toFloat() / 14f)
                drawLine(Color(0xFFF3D5DE).copy(alpha = 0.7f), c + Offset(cos(a) * r * 0.1f, sin(a) * r * 0.1f), c + Offset(cos(a) * r * 0.62f, sin(a) * r * 0.62f), r * 0.02f)
            }
            drawCircle(Color(0xFFFCE4EC), r * 0.12f, c)
        }
        else -> { // картошка: светлая крахмалистая мякоть, сосудистое кольцо, крапинки
            drawCircle(Color(0xFFE0C79A), r * 0.9f, c)
            drawCircle(Color(0xFFFFF4B0), r * 0.84f, c)
            drawCircle(Color(0xFFE9D98F).copy(alpha = 0.7f), r * 0.68f, c, style = Stroke(r * 0.06f))
            for (k in 0 until 16) {
                val a = rot + k * 1.7f
                val rr = r * (0.15f + 0.5f * vcH(k + 3))
                drawCircle(Color(0xFFFFFBD6), r * 0.035f, c + Offset(cos(a) * rr, sin(a) * rr))
            }
        }
    }
}

// ---------- Овощи целиком ----------
// Рисуются в локальной системе: ось овоща — x (от верхнего конца к срезу), толщина — y, начало в центре верхнего
// конца. Свет слева-сверху: в этой системе «верх» (−y) переходит на экране влево.

private fun DrawScope.drawCucumber(T: Float, total: Float) {
    val r = T * 0.42f
    val top = -T / 2f
    drawRoundRect(Color.Black.copy(alpha = 0.2f), Offset(T * 0.02f, top + T * 0.1f), Size(total, T), CornerRadius(r))
    drawRoundRect(
        Brush.verticalGradient(
            0f to Color(0xFF9CCC65), 0.18f to Color(0xFF66BB6A), 0.5f to Color(0xFF388E3C), 0.85f to Color(0xFF1B5E20), 1f to Color(0xFF144A19),
            startY = top, endY = -top
        ),
        Offset(0f, top), Size(total, T), CornerRadius(r)
    )
    // светлые продольные полосы
    for (i in 0 until 6) {
        val y0 = top + T * (0.16f + 0.13f * i)
        val path = Path()
        path.moveTo(T * 0.3f, y0)
        for (sg in 1..9) path.lineTo(T * 0.3f + (total - T * 0.6f) * sg / 9f, y0 + sin(sg * 1.1f + i * 2f) * T * 0.018f)
        drawPath(path, Color(0xFFDCEDC8).copy(alpha = 0.28f), style = Stroke(T * 0.022f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    // пупырышки
    for (k in 0 until 46) {
        val bx = T * 0.25f + (total - T * 0.5f) * vcH(k)
        val by = top + T * (0.1f + 0.8f * vcH(k + 101))
        drawCircle(Color(0xFF1B5E20).copy(alpha = 0.35f), T * 0.022f, Offset(bx + T * 0.008f, by + T * 0.01f))
        drawCircle(Color(0xFFC5E1A5).copy(alpha = 0.55f), T * 0.016f, Offset(bx, by))
    }
    drawLine(Color.White.copy(alpha = 0.28f), Offset(r, top + T * 0.2f), Offset(total - r, top + T * 0.2f), T * 0.09f, StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.16f), Offset(r, top + T * 0.3f), Offset(total * 0.6f, top + T * 0.3f), T * 0.03f, StrokeCap.Round)
    // плодоножка
    drawRoundRect(Color(0xFF8D9A2E), Offset(-T * 0.07f, -T * 0.11f), Size(T * 0.16f, T * 0.22f), CornerRadius(T * 0.05f))
    drawCircle(Color(0xFF5D6B1A), T * 0.04f, Offset(0f, 0f))
}

private fun DrawScope.drawCarrot(T: Float, total: Float) {
    fun half(x: Float) = T * (0.45f + 0.55f * (x / total).coerceIn(0f, 1f)) / 2f
    val h0 = half(0f)
    val xr = h0 * 1.2f
    val body = Path().apply {
        moveTo(0f, -h0 * 0.35f)
        cubicTo(0f, -h0 * 0.9f, h0 * 0.5f, -h0, xr, -half(xr))
        lineTo(total, -half(total)); lineTo(total, half(total)); lineTo(xr, half(xr))
        cubicTo(h0 * 0.5f, h0, 0f, h0 * 0.9f, 0f, h0 * 0.35f)
        close()
    }
    withTransform({ translate(T * 0.02f, T * 0.09f) }) { drawPath(body, Color.Black.copy(alpha = 0.2f)) }
    drawPath(
        body,
        Brush.verticalGradient(
            0f to Color(0xFFFFC773), 0.3f to Color(0xFFFF9A1F), 0.7f to Color(0xFFE65100), 1f to Color(0xFFBF360C),
            startY = -T / 2f, endY = T / 2f
        )
    )
    // поперечные морщинки: короткие дуги, не доходящие до краёв
    for (i in 0 until 11) {
        val x = total * (0.07f + 0.085f * i) + total * 0.02f * vcH(i + 40)
        val hx = half(x)
        val ya = -hx * (0.35f + 0.55f * vcH(i + 60))
        val yb = hx * (0.35f + 0.55f * vcH(i + 80))
        val p = Path().apply { moveTo(x, ya); cubicTo(x + T * 0.06f, ya * 0.4f, x + T * 0.06f, yb * 0.4f, x, yb) }
        drawPath(p, Color(0xFFA33A0A).copy(alpha = 0.4f), style = Stroke(T * 0.02f, cap = StrokeCap.Round))
    }
    for (k in 0 until 26) {
        val x = xr + (total - xr) * vcH(k + 7)
        val y = (vcH(k + 21) - 0.5f) * half(x) * 1.6f
        drawCircle(Color(0xFFFFE0B2).copy(alpha = 0.4f), T * 0.012f, Offset(x, y))
    }
    drawLine(Color.White.copy(alpha = 0.26f), Offset(xr, -half(xr) * 0.55f), Offset(total * 0.97f, -half(total) * 0.55f), T * 0.07f, StrokeCap.Round)
}

private fun DrawScope.drawTomato(T: Float) {
    val r = T / 2f
    val c = Offset(r, 0f)
    drawCircle(Color.Black.copy(alpha = 0.2f), r, Offset(r + r * 0.02f, r * 0.1f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFF7A5C), Color(0xFFE53935), Color(0xFFA81414)), Offset(r * 0.7f, -r * 0.35f), r * 1.35f), r, c)
    // дольки
    for (sgn in listOf(-1f, 1f)) {
        val p = Path().apply { moveTo(r * 0.3f, 0f); cubicTo(r * 0.7f, sgn * r * 0.7f, r * 1.4f, sgn * r * 0.75f, r * 1.85f, sgn * r * 0.12f) }
        drawPath(p, Color.Black.copy(alpha = 0.1f), style = Stroke(r * 0.04f, cap = StrokeCap.Round))
    }
    for (k in 0 until 24) {
        val a = vcH(k) * 6.2832f
        val rr = r * 0.9f * kotlin.math.sqrt(vcH(k + 50))
        drawCircle(Color(0xFF7F1010).copy(alpha = 0.18f), r * 0.012f, c + Offset(cos(a) * rr, sin(a) * rr))
    }
    // чашелистики и плодоножка (сверху)
    val q = Offset(r * 0.2f, 0f)
    for (i in 0 until 5) {
        val a = i * 1.2566f + 0.35f
        val tip = q + Offset(cos(a) * r * 0.36f, sin(a) * r * 0.36f)
        val l = q + Offset(cos(a - 0.3f) * r * 0.1f, sin(a - 0.3f) * r * 0.1f)
        val rr = q + Offset(cos(a + 0.3f) * r * 0.1f, sin(a + 0.3f) * r * 0.1f)
        val leaf = Path().apply { moveTo(l.x, l.y); cubicTo(vcLerpPt(l, tip, 0.5f).x, vcLerpPt(l, tip, 0.5f).y - r * 0.05f, tip.x, tip.y, tip.x, tip.y); lineTo(rr.x, rr.y); close() }
        drawPath(leaf, Color(0xFF2E7D32))
        drawPath(leaf, Color(0xFF1B5E20), style = Stroke(r * 0.015f))
    }
    drawCircle(Color(0xFF558B2F), r * 0.07f, q)
    drawRoundRect(Color(0xFF6B8E23), Offset(-r * 0.1f, -r * 0.04f), Size(r * 0.26f, r * 0.08f), CornerRadius(r * 0.04f))
    // глянцевые блики
    drawOval(Color.White.copy(alpha = 0.42f), Offset(r * 0.3f, -r * 0.64f), Size(r * 0.36f, r * 0.2f))
    drawCircle(Color.White.copy(alpha = 0.5f), r * 0.04f, Offset(r * 0.75f, -r * 0.5f))
}

private fun DrawScope.drawRadish(T: Float) {
    val r = T / 2f
    val c = Offset(r, 0f)
    drawCircle(Color.Black.copy(alpha = 0.2f), r, Offset(r + r * 0.02f, r * 0.1f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFF6B97), Color(0xFFD81B60), Color(0xFF8A0E4F)), Offset(r * 0.7f, -r * 0.35f), r * 1.35f), r, c)
    // белый «носик» корнеплода к срезу
    drawCircle(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFFCE4EC).copy(alpha = 0.85f)), startX = r * 1.2f, endX = r * 2f), r, c)
    // тонкие продольные прожилки
    for (i in 0 until 7) {
        val y = (i - 3f) * r * 0.24f
        val p = Path().apply { moveTo(r * 0.4f, y * 0.6f); cubicTo(r * 0.9f, y, r * 1.3f, y, r * 1.8f, y * 0.7f) }
        drawPath(p, Color(0xFF6A0B3A).copy(alpha = 0.16f), style = Stroke(r * 0.015f, cap = StrokeCap.Round))
    }
    // листья сверху
    for ((i, ang) in listOf(-0.5f, 0f, 0.5f).withIndex()) {
        val leaf = Path().apply {
            moveTo(r * 0.2f, 0f)
            cubicTo(-r * 0.05f, ang * r * 0.6f - r * 0.25f, -r * 0.45f, ang * r * 1.1f - r * 0.2f, -r * 0.6f, ang * r * 1.1f)
            cubicTo(-r * 0.4f, ang * r * 1.1f + r * 0.2f, -r * 0.05f, ang * r * 0.6f + r * 0.25f, r * 0.2f, 0f)
            close()
        }
        drawPath(leaf, if (i == 1) Color(0xFF43A047) else Color(0xFF2E7D32))
        drawLine(Color(0xFF1B5E20), Offset(r * 0.2f, 0f), Offset(-r * 0.5f, ang * r * 1.1f), r * 0.012f)
    }
    drawOval(Color.White.copy(alpha = 0.4f), Offset(r * 0.32f, -r * 0.62f), Size(r * 0.3f, r * 0.17f))
}

private fun DrawScope.drawPotato(T: Float) {
    val r = T / 2f
    val c = Offset(r, 0f)
    // неровный контур: 20 точек с лёгким разбросом радиуса, сглаженных кривыми
    val n = 20
    val pts = List(n) { i ->
        val a = i * 2f * PI.toFloat() / n
        val rr = r * (0.9f + 0.1f * vcH(i + 7))
        Offset(c.x + cos(a) * rr, c.y + sin(a) * rr)
    }
    val blob = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 0 until n) {
            val p0 = pts[(i - 1 + n) % n]; val p1 = pts[i]; val p2 = pts[(i + 1) % n]; val p3 = pts[(i + 2) % n]
            cubicTo(
                p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                p2.x, p2.y
            )
        }
        close()
    }
    withTransform({ translate(r * 0.02f, r * 0.1f) }) { drawPath(blob, Color.Black.copy(alpha = 0.2f)) }
    drawPath(blob, Brush.radialGradient(listOf(Color(0xFFE6CBA0), Color(0xFFC49A6C), Color(0xFF8D6240)), Offset(r * 0.7f, -r * 0.35f), r * 1.4f))
    drawPath(blob, Color(0xFF6D4C41).copy(alpha = 0.5f), style = Stroke(r * 0.015f))
    // глазки: тёмная вмятина с светлым ободком
    for (k in 0 until 6) {
        val a = vcH(k + 30) * 6.2832f
        val rr = r * (0.2f + 0.55f * vcH(k + 33))
        val e = c + Offset(cos(a) * rr, sin(a) * rr)
        drawOval(Color(0xFFF1DDB9).copy(alpha = 0.6f), e + Offset(-r * 0.02f, -r * 0.02f), Size(r * 0.15f, r * 0.1f))
        drawOval(Color(0xFF5D3A22), e, Size(r * 0.1f, r * 0.065f))
    }
    // крапинки и светлые пятна
    for (k in 0 until 60) {
        val a = vcH(k + 70) * 6.2832f
        val rr = r * 0.92f * kotlin.math.sqrt(vcH(k + 130))
        val dark = k % 3 != 0
        drawCircle(if (dark) Color(0xFF6D4C41).copy(alpha = 0.35f) else Color(0xFFF3E3C3).copy(alpha = 0.4f), r * (0.012f + 0.014f * vcH(k + 9)), c + Offset(cos(a) * rr, sin(a) * rr))
    }
    drawOval(Color.White.copy(alpha = 0.22f), Offset(r * 0.32f, -r * 0.6f), Size(r * 0.3f, r * 0.16f))
}

private fun DrawScope.drawKitchen(
    type: Int, cxVeg: Float, a0: Float, thick: Float, total: Float, len: Float, knifeY: Float, knifeDown: Float, clock: Float, cy: Float,
    slices: List<VcSlice>, drops: List<VcDrop>, bowl: List<VcBowlItem>
) {
    val w = size.width; val h = size.height
    // стена с плиткой
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFF8E1), Color(0xFFFFECB3))), size = Size(w, h * 0.62f))
    val tile = w / 6f
    for (i in 0..6) for (j in 0..5) {
        drawRect(Color(0xFFFFE082).copy(alpha = 0.35f), Offset(i * tile + 2f, j * tile + 2f), Size(tile - 4f, tile - 4f))
    }
    // столешница
    drawRect(Brush.verticalGradient(listOf(Color(0xFF8D6E63), Color(0xFF5D4037))), Offset(0f, h * 0.62f), Size(w, h * 0.38f))
    // разделочная доска: овощ лежит на ней целиком, нож режет поперёк
    val bh = h * VC_BOARD_H
    val bt = cy - bh / 2f
    drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(w * 0.04f + 6f, bt + 10f), Size(w * 0.92f, bh), CornerRadius(h * 0.03f))
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFCC80), Color(0xFFFFB74D))), Offset(w * 0.04f, bt), Size(w * 0.92f, bh), CornerRadius(h * 0.03f))
    for (k in 1..4) drawLine(Color(0xFFFFA726).copy(alpha = 0.5f), Offset(w * 0.07f, bt + bh * k / 5f), Offset(w * 0.93f, bt + bh * k / 5f), 3f)
    drawCircle(Color(0xFF8D6E63), h * 0.012f, Offset(w * 0.9f, bt + bh * 0.5f))

    // овощ
    val vt = VC_TYPES[type]
    if (len > 0.5f) {
        withTransform({ translate(cxVeg, a0); rotate(90f, Offset.Zero); scale(1f, -1f, Offset.Zero) }) {
            clipRect(left = -thick * 2f, top = -thick * 2f, right = len, bottom = thick * 2f) {
                when (type) {
                    0 -> drawCarrot(thick, total)
                    1 -> drawCucumber(thick, total)
                    2 -> drawTomato(thick)
                    3 -> drawRadish(thick)
                    else -> drawPotato(thick)
                }
            }
            // свежий срез: овал с внутренним строением (половина заходит на тело, как у цилиндра под углом)
            val tFace = when {
                vt.round -> { val r = thick / 2f; val dx = (len - r).coerceIn(-r, r); 2f * kotlin.math.sqrt(max(0f, r * r - dx * dx)) }
                vt.tapered -> thick * (0.45f + 0.55f * (len / total).coerceIn(0f, 1f))
                else -> thick
            }.coerceAtLeast(2f)
            val rx = max(4f, tFace * 0.13f)
            withTransform({ translate(len, 0f); scale(rx / (tFace / 2f), 1f, Offset.Zero) }) {
                drawDisc(0f, 0f, tFace, type, 0.6f)
            }
        }
    }

    // миска: задняя стенка и внутренность, затем кусочки, затем передняя стенка (она закрывает низ кусочков,
    // так что они лежат ВНУТРИ миски, а не поверх неё)
    val bx = w / 2f; val bw = w * 0.62f; val btop = h * 0.76f; val bd = h * 0.13f
    val brx = bw / 2f; val bry = bd * 0.28f
    drawOval(Color.Black.copy(alpha = 0.2f), Offset(bx - brx + 6f, btop + bd * 1.05f), Size(bw, h * 0.03f))
    val bodyPath = Path().apply {
        moveTo(bx - brx, btop)
        cubicTo(bx - brx, btop + bd * 1.35f, bx - brx * 0.45f, btop + bd * 1.45f, bx, btop + bd * 1.45f)
        cubicTo(bx + brx * 0.45f, btop + bd * 1.45f, bx + brx, btop + bd * 1.35f, bx + brx, btop)
        close()
    }
    // внешняя стенка (видна под передним краем) и внутренность
    drawOval(Color(0xFFCFD8DC), Offset(bx - brx, btop - bry), Size(bw, bry * 2f))
    drawOval(Color(0xFF90A4AE), Offset(bx - brx + 8f, btop - bry + 5f), Size(bw - 16f, bry * 2f - 8f))
    drawOval(Color(0xFFB0BEC5), Offset(bx - brx + 14f, btop - bry + 9f), Size(bw - 28f, bry * 1.2f))
    for (it in bowl) drawDisc(bx + it.ox, btop + it.oy, w * 0.085f, it.type, it.rot)
    // летящие кусочки: пока они выше края, их видно; ниже края их закрывает передняя стенка
    for (s in slices) drawDisc(s.x, s.y, s.d, s.type, s.rot)
    // передняя стенка миски
    drawPath(bodyPath, Brush.verticalGradient(listOf(Color(0xFFF5F8FA), Color(0xFFFFFFFF), Color(0xFFD7E3EA)), btop, btop + bd * 1.45f))
    drawPath(bodyPath, Color(0xFFB3E5FC), style = Stroke(h * 0.006f, join = StrokeJoin.Round))
    // кромка: передняя дуга обода поверх стенки
    drawArc(Color(0xFFECEFF1), 0f, 180f, false, Offset(bx - brx, btop - bry), Size(bw, bry * 2f), style = Stroke(h * 0.012f, cap = StrokeCap.Round))
    drawArc(Color(0xFFB3E5FC), 0f, 180f, false, Offset(bx - brx, btop - bry), Size(bw, bry * 2f), style = Stroke(h * 0.004f))
    drawOval(Color.White.copy(alpha = 0.55f), Offset(bx - brx * 0.7f, btop + bd * 0.35f), Size(brx * 0.25f, bd * 0.45f))
    // декоративный полосатый поясок
    drawLine(Color(0xFF81D4FA).copy(alpha = 0.55f), Offset(bx - brx * 0.82f, btop + bd * 0.55f), Offset(bx + brx * 0.82f, btop + bd * 0.55f), h * 0.008f)

    for (d in drops) drawCircle(d.c.copy(alpha = d.life.coerceIn(0f, 1f)), d.r, Offset(d.x, d.y))

    // нож: поварской, острие вправо, висит над местом разреза и по тапу рубит вниз
    val bl = w * 0.30f            // длина лезвия
    val kbh = w * 0.078f           // высота лезвия у пятки
    val knifeX = cxVeg + bl * 0.1f   // лезвие поперёк овоща, над местом разреза
    val hoverY = knifeY - h * 0.075f - sin(clock * 3f) * h * 0.004f
    val downY = knifeY
    val k = knifeDown
    val edgeY = hoverY + (downY - hoverY) * k
    val pivot = Offset(knifeX, edgeY)
    rotate(-13f * (1f - k), pivot) {
        withTransform({ translate(knifeX, edgeY) }) {
            // тень от ножа на разделочной доске
            drawOval(Color.Black.copy(alpha = 0.12f * (1f - k)), Offset(-bl * 0.55f, downY - edgeY + kbh * 0.1f), Size(bl * 1.0f, kbh * 0.22f))
            val heel = -bl * 0.60f
            val tipX = bl * 0.40f
            val blade = Path().apply {
                moveTo(heel, -kbh)                                        // пятка, спинка
                lineTo(bl * 0.12f, -kbh * 0.98f)
                cubicTo(bl * 0.28f, -kbh * 0.96f, bl * 0.38f, -kbh * 0.55f, tipX, -kbh * 0.10f) // скос к острию
                cubicTo(bl * 0.34f, -kbh * 0.02f, bl * 0.22f, 0f, bl * 0.06f, 0f)             // брюшко
                lineTo(heel + bl * 0.05f, 0f)                            // режущая кромка
                lineTo(heel, -kbh * 0.08f)
                close()
            }
            drawPath(blade, Brush.verticalGradient(listOf(Color(0xFF90A4AE), Color(0xFFECEFF1), Color(0xFFFFFFFF)), -kbh, 0f))
            // фаска вдоль кромки: светлая и тёмная полоса
            val bevel = Path().apply {
                moveTo(heel + bl * 0.04f, -kbh * 0.30f); lineTo(bl * 0.05f, -kbh * 0.27f)
                cubicTo(bl * 0.22f, -kbh * 0.25f, bl * 0.32f, -kbh * 0.18f, tipX - bl * 0.01f, -kbh * 0.11f)
                lineTo(bl * 0.30f, -kbh * 0.03f); lineTo(bl * 0.06f, -kbh * 0.015f); lineTo(heel + bl * 0.05f, -kbh * 0.015f); close()
            }
            drawPath(bevel, Color(0xFFFFFFFF).copy(alpha = 0.85f))
            drawLine(Color(0xFF78909C), Offset(heel + bl * 0.04f, -kbh * 0.30f), Offset(bl * 0.05f, -kbh * 0.27f), 2.5f)
            // блик и тёмная спинка
            drawLine(Color.White.copy(alpha = 0.9f), Offset(heel + bl * 0.08f, -kbh * 0.78f), Offset(bl * 0.18f, -kbh * 0.76f), kbh * 0.07f, StrokeCap.Round)
            drawLine(Color(0xFF546E7A), Offset(heel, -kbh), Offset(bl * 0.12f, -kbh * 0.98f), 4f, StrokeCap.Round)
            drawPath(blade, Color(0xFF607D8B), style = Stroke(2.5f, join = StrokeJoin.Round))
            // накладка-болстер на пятке
            drawRoundRect(Color(0xFFB0BEC5), Offset(heel - bl * 0.025f, -kbh * 1.02f), Size(bl * 0.05f, kbh * 1.04f), CornerRadius(bl * 0.01f))
            drawRoundRect(Color(0xFF607D8B), Offset(heel - bl * 0.025f, -kbh * 1.02f), Size(bl * 0.05f, kbh * 1.04f), CornerRadius(bl * 0.01f), style = Stroke(2.5f))
            // рукоятка: дерево с заклёпками
            val hl = bl * 0.42f
            val hx = heel - bl * 0.025f - hl
            val handle = Path().apply {
                moveTo(hx + hl * 0.06f, -kbh * 1.00f)
                lineTo(heel - bl * 0.025f, -kbh * 1.00f)
                lineTo(heel - bl * 0.025f, -kbh * 0.18f)
                cubicTo(heel - bl * 0.10f, -kbh * 0.08f, hx + hl * 0.25f, -kbh * 0.12f, hx + hl * 0.04f, -kbh * 0.20f)
                cubicTo(hx - hl * 0.02f, -kbh * 0.30f, hx - hl * 0.02f, -kbh * 0.82f, hx + hl * 0.06f, -kbh * 1.00f)
                close()
            }
            drawPath(handle, Brush.verticalGradient(listOf(Color(0xFFA1704A), Color(0xFF6D4C41), Color(0xFF4E342E)), -kbh, -kbh * 0.1f))
            drawPath(handle, Color(0xFF3E2723), style = Stroke(3f, join = StrokeJoin.Round))
            drawLine(Color.White.copy(alpha = 0.25f), Offset(hx + hl * 0.12f, -kbh * 0.88f), Offset(heel - bl * 0.06f, -kbh * 0.88f), kbh * 0.06f, StrokeCap.Round)
            for (q in 0..2) {
                val rx = heel - bl * 0.025f - hl * (0.2f + q * 0.28f)
                drawCircle(Color(0xFFFFCC80), kbh * 0.07f, Offset(rx, -kbh * 0.56f))
                drawCircle(Color(0xFF8D6E63), kbh * 0.07f, Offset(rx, -kbh * 0.56f), style = Stroke(1.5f))
            }
        }
    }
}
