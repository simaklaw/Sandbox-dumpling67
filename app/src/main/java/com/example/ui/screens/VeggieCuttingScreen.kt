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
        var type = 0; var len = 0f; var total = 0f; var x0 = 0f; var thick = 0f; var st = 0f
        var knifeX = 0f; var knifeDown = 0f; var slideIn = 0f; var nextAt = -1f
        var bowlClearAt = -1f; var bannerUntil = 0f; var clock = 0f; var inited = false; var lastPlop = 0f
    }
    val m = remember { Model() }

    fun W() = size.width.toFloat()
    fun H() = size.height.toFloat()
    fun cy() = H() * 0.40f

    fun initVeg(t: Int) {
        val w = W(); val h = H()
        m.type = t
        val vt = VC_TYPES[t]
        if (vt.round) {
            m.thick = w * when (t) { 2 -> 0.42f; 3 -> 0.32f; else -> 0.40f }
            m.total = m.thick
            m.x0 = (w - m.thick) / 2f
            m.st = m.thick / 7f
        } else {
            m.thick = h * if (t == 0) 0.125f else 0.11f
            m.total = w * 0.76f
            m.x0 = w * 0.12f
            m.st = m.total / (if (t == 0) 12f else 10f)
        }
        m.len = m.total
        m.knifeX = m.x0 + m.len - m.st
        m.slideIn = w
        m.nextAt = -1f
        vegName = vt.name
    }

    fun thickAt(x: Float): Float {
        val vt = VC_TYPES[m.type]
        if (vt.round) {
            val r = m.thick / 2f
            val dx = (x - (m.x0 + r)).coerceIn(-r, r)
            return 2f * kotlin.math.sqrt(max(0f, r * r - dx * dx))
        }
        return if (vt.tapered) m.thick * (0.45f + 0.55f * ((x - m.x0) / m.total).coerceIn(0f, 1f)) else m.thick
    }

    fun cut() {
        if (!m.inited || m.nextAt >= 0f || m.len <= 0.5f) return
        val vt = VC_TYPES[m.type]
        val take = if (m.len - m.st < m.st * 0.55f) m.len else m.st
        val faceX = m.x0 + m.len
        val lineX = faceX - take
        val d = thickAt(lineX).coerceAtLeast(m.st)
        m.len -= take
        m.knifeDown = 1f
        cuts++
        sm.play("kit_chop_${vt.sound}_${Random.nextInt(1, 3)}", volume = 1f, rate = 0.96f + Random.nextFloat() * 0.1f)
        haptics.vibrate(12L)
        // летящий кусочек
        val bw = W() * 0.62f
        val tx = W() / 2f + (Random.nextFloat() - 0.5f) * bw * 0.5f
        val ty = H() * 0.80f
        val sx = lineX + take / 2f; val sy = cy()
        val tf = 0.55f; val g = H() * 3.2f
        slices.add(VcSlice(sx, sy, tx, ty, (tx - sx) / tf, (ty - sy) / tf - 0.5f * g * tf, min(d, W() * 0.2f), m.type, 0f, (Random.nextFloat() - 0.5f) * 14f))
        // брызги сока
        repeat(6) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val sp = W() * (0.15f + Random.nextFloat() * 0.3f)
            drops.add(VcDrop(lineX, cy() + (Random.nextFloat() - 0.5f) * d * 0.6f, cos(a) * sp, sin(a) * sp - H() * 0.1f, 1f, vt.skin.copy(alpha = 0.9f), W() * 0.008f))
        }
        if (m.len <= 0.5f) { m.nextAt = m.clock + 0.35f }
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
                    val target = m.x0 + m.len - m.st
                    m.knifeX += (target - m.knifeX) * min(1f, dt * 22f)
                    m.knifeDown = max(0f, m.knifeDown - dt * 7f)
                    if (m.nextAt in 0f..m.clock) { initVeg((m.type + 1 + Random.nextInt(VC_TYPES.size - 1)) % VC_TYPES.size) }
                    val g = H() * 3.2f
                    val it = slices.iterator()
                    while (it.hasNext()) {
                        val s = it.next()
                        s.x += s.vx * dt; s.y += s.vy * dt; s.vy += g * dt; s.rot += s.vrot * dt
                        if (s.vy > 0 && s.y >= s.ty) {
                            it.remove()
                            val bw = W() * 0.62f
                            bowl.add(VcBowlItem(s.type, (s.x - W() / 2f).coerceIn(-bw * 0.35f, bw * 0.35f), (Random.nextFloat() - 0.5f) * H() * 0.03f, s.rot))
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
            if (size.width > 0f && m.inited) drawKitchen(m.type, m.x0 + m.slideIn * -1f, m.thick, m.total, m.len, m.knifeX - m.slideIn, m.knifeDown, m.clock, cy(), m.st, slices, drops, bowl, bowl.size)
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 88.dp, end = 12.dp, top = 10.dp)) {
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

private fun DrawScope.drawDisc(cx: Float, cy: Float, d: Float, t: Int, rot: Float) {
    val vt = VC_TYPES[t]
    val r = d / 2f
    drawCircle(vt.skinDark, r, Offset(cx, cy))
    drawCircle(vt.skin, r * 0.93f, Offset(cx, cy))
    drawCircle(vt.inner, r * 0.82f, Offset(cx, cy))
    when (t) {
        0 -> { drawCircle(vt.skin.copy(alpha = 0.6f), r * 0.45f, Offset(cx, cy), style = Stroke(r * 0.07f)); drawCircle(vt.detail, r * 0.28f, Offset(cx, cy)) }
        1 -> { for (k in 0 until 6) { val a = rot + k * PI.toFloat() / 3f; drawCircle(Color(0xFFF9FBE7), r * 0.07f, Offset(cx + cos(a) * r * 0.45f, cy + sin(a) * r * 0.45f)) }; drawCircle(vt.detail, r * 0.18f, Offset(cx, cy), style = Stroke(r * 0.05f)) }
        2 -> { for (k in 0 until 4) { val a = rot + k * PI.toFloat() / 2f; drawCircle(vt.detail, r * 0.24f, Offset(cx + cos(a) * r * 0.4f, cy + sin(a) * r * 0.4f)) }; drawCircle(Color(0xFFFFEE58), r * 0.07f, Offset(cx + cos(rot) * r * 0.4f, cy + sin(rot) * r * 0.4f)) }
        3 -> { drawCircle(vt.detail, r * 0.5f, Offset(cx, cy), style = Stroke(r * 0.08f)) }
        else -> { for (k in 0 until 5) { val a = rot + k * 1.26f; drawCircle(vt.detail, r * 0.06f, Offset(cx + cos(a) * r * 0.5f, cy + sin(a) * r * 0.5f)) } }
    }
}

private fun DrawScope.drawKitchen(
    type: Int, x0: Float, thick: Float, total: Float, len: Float, knifeX: Float, knifeDown: Float, clock: Float, cy: Float, st: Float,
    slices: List<VcSlice>, drops: List<VcDrop>, bowl: List<VcBowlItem>, bowlCount: Int
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
    // разделочная доска
    val bt = cy - thick * 0.9f - h * 0.04f
    val bh = thick * 1.8f + h * 0.08f
    drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(w * 0.04f + 6f, bt + 10f), Size(w * 0.92f, bh), CornerRadius(h * 0.03f))
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFCC80), Color(0xFFFFB74D))), Offset(w * 0.04f, bt), Size(w * 0.92f, bh), CornerRadius(h * 0.03f))
    for (k in 1..4) drawLine(Color(0xFFFFA726).copy(alpha = 0.5f), Offset(w * 0.07f, bt + bh * k / 5f), Offset(w * 0.93f, bt + bh * k / 5f), 3f)
    drawCircle(Color(0xFF8D6E63), h * 0.012f, Offset(w * 0.9f, bt + bh * 0.5f))

    // овощ
    val vt = VC_TYPES[type]
    val faceX = x0 + len
    drawOval(Color.Black.copy(alpha = 0.18f), Offset(x0, cy + thick * 0.35f), Size(max(len, 1f), thick * 0.35f))
    if (len > 0.5f) clipRect(left = 0f, top = 0f, right = faceX, bottom = h) {
        when {
            vt.round -> {
                val r = thick / 2f
                val c = Offset(x0 + r, cy)
                drawCircle(Brush.radialGradient(listOf(vt.skin, vt.skinDark), Offset(c.x - r * 0.3f, c.y - r * 0.3f), r * 1.4f), r, c)
                drawCircle(Color.White.copy(alpha = 0.35f), r * 0.14f, Offset(c.x - r * 0.45f, c.y - r * 0.5f))
                if (type == 2) { drawCircle(Color(0xFF2E7D32), r * 0.16f, Offset(c.x - r * 0.0f, c.y - r * 0.92f)) }
            }
            vt.tapered -> {
                val p = Path().apply {
                    moveTo(x0, cy - thick * 0.22f); lineTo(x0 + total, cy - thick / 2f); lineTo(x0 + total, cy + thick / 2f); lineTo(x0, cy + thick * 0.22f); close()
                }
                drawPath(p, Brush.verticalGradient(listOf(vt.inner, vt.skin, vt.skinDark), cy - thick / 2f, cy + thick / 2f))
                drawCircle(vt.skin, thick * 0.22f, Offset(x0 + 2f, cy))
                for (k in 1..5) { val xx = x0 + total * k / 6f; drawLine(vt.skinDark.copy(alpha = 0.5f), Offset(xx, cy - thick * 0.22f), Offset(xx + total * 0.02f, cy - thick * 0.05f), 4f) }
            }
            else -> {
                drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f).compositeOver(vt.skin), vt.skin, vt.skinDark), cy - thick / 2f, cy + thick / 2f), Offset(x0, cy - thick / 2f), Size(total, thick), CornerRadius(thick * 0.45f))
                for (k in 0 until 14) drawCircle(vt.skinDark.copy(alpha = 0.45f), thick * 0.035f, Offset(x0 + total * (k + 0.5f) / 14f, cy - thick * (0.18f + 0.1f * ((k * 7) % 5) / 5f) + if (k % 2 == 0) thick * 0.25f else 0f))
            }
        }
    }
    // срез (торец) — овал
    if (len > 0.5f) {
        val tFace = when {
            vt.round -> { val r = thick / 2f; val dx = (faceX - (x0 + r)).coerceIn(-r, r); 2f * kotlin.math.sqrt(max(0f, r * r - dx * dx)) }
            vt.tapered -> thick * (0.45f + 0.55f * ((faceX - x0) / total).coerceIn(0f, 1f))
            else -> thick
        }
        val rx = max(4f, tFace * 0.13f)
        drawOval(vt.inner, Offset(faceX - rx, cy - tFace / 2f), Size(rx * 2f, tFace))
        drawOval(vt.skinDark, Offset(faceX - rx, cy - tFace / 2f), Size(rx * 2f, tFace), style = Stroke(3f))
    }

    // миска (задняя часть), кусочки и передний край
    val bx = w / 2f; val bw = w * 0.62f; val btop = h * 0.76f; val bd = h * 0.13f
    drawOval(Color.Black.copy(alpha = 0.2f), Offset(bx - bw / 2f + 6f, btop + bd * 0.85f), Size(bw, h * 0.03f))
    drawArc(Color(0xFFECEFF1), 0f, 180f, true, Offset(bx - bw / 2f, btop - bd * 0.1f), Size(bw, bd * 1.9f))
    drawOval(Color(0xFFCFD8DC), Offset(bx - bw / 2f, btop - bd * 0.2f), Size(bw, bd * 0.5f))
    drawOval(Color(0xFFB0BEC5), Offset(bx - bw / 2f + 6f, btop - bd * 0.16f), Size(bw - 12f, bd * 0.42f))
    for (it in bowl) drawDisc(bx + it.ox, btop + bd * 0.05f + it.oy, w * 0.085f, it.type, it.rot)
    drawArc(Color(0xFFFFFFFF).copy(alpha = 0.95f), 0f, 180f, true, Offset(bx - bw / 2f, btop + bd * 0.1f), Size(bw, bd * 1.5f))
    drawArc(Color(0xFFB3E5FC), 0f, 180f, false, Offset(bx - bw / 2f, btop + bd * 0.1f), Size(bw, bd * 1.5f), style = Stroke(h * 0.006f))

    // летящие кусочки
    for (s in slices) drawDisc(s.x, s.y, s.d, s.type, s.rot)
    for (d in drops) drawCircle(d.c.copy(alpha = d.life.coerceIn(0f, 1f)), d.r, Offset(d.x, d.y))

    // нож: висит над местом разреза, по тапу резко опускается
    val bladeW = w * 0.085f
    val bladeH = h * 0.17f
    val hoverY = cy - thick * 0.5f - h * 0.035f - sin(clock * 3f) * h * 0.004f
    val downY = cy + thick * 0.5f + h * 0.005f
    val k = knifeDown
    val edgeY = hoverY + (downY - hoverY) * k
    rotate(-6f * (1f - k), Offset(knifeX, edgeY)) {
        val blade = Path().apply {
            moveTo(knifeX - bladeW / 2f, edgeY); lineTo(knifeX + bladeW / 2f, edgeY)
            lineTo(knifeX + bladeW * 0.42f, edgeY - bladeH); lineTo(knifeX - bladeW * 0.42f, edgeY - bladeH); close()
        }
        drawPath(blade, Brush.horizontalGradient(listOf(Color(0xFFCFD8DC), Color(0xFFFFFFFF), Color(0xFF90A4AE)), knifeX - bladeW / 2f, knifeX + bladeW / 2f))
        drawLine(Color.White, Offset(knifeX - bladeW / 2f, edgeY), Offset(knifeX + bladeW / 2f, edgeY), 4f)
        drawPath(blade, Color(0xFF607D8B), style = Stroke(3f))
        drawRoundRect(Color(0xFF6D4C41), Offset(knifeX - bladeW * 0.3f, edgeY - bladeH - h * 0.1f), Size(bladeW * 0.6f, h * 0.105f), CornerRadius(bladeW * 0.2f))
        drawRoundRect(Color(0xFF3E2723), Offset(knifeX - bladeW * 0.3f, edgeY - bladeH - h * 0.1f), Size(bladeW * 0.6f, h * 0.105f), CornerRadius(bladeW * 0.2f), style = Stroke(3f))
        drawCircle(Color(0xFFFFCC80), bladeW * 0.06f, Offset(knifeX, edgeY - bladeH - h * 0.06f))
    }
}
