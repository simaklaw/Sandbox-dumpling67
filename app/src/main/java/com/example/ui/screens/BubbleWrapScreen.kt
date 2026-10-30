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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private class BwSpark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val c: Color, val r: Float)

private class BwTheme(val name: String, val bg1: Color, val bg2: Color, val film: Color, val rim: Color)

private val BW_THEMES = listOf(
    BwTheme("Классика", Color(0xFFB3E5FC), Color(0xFF4FC3F7), Color(0xFFE1F5FE), Color(0xFF81D4FA)),
    BwTheme("Розовый зефир", Color(0xFFFFE0EC), Color(0xFFF48FB1), Color(0xFFFFF1F6), Color(0xFFF8BBD0)),
    BwTheme("Мята", Color(0xFFD7F5E3), Color(0xFF66BB6A), Color(0xFFF1FFF6), Color(0xFFA5D6A7)),
    BwTheme("Ночь", Color(0xFF1A1A3E), Color(0xFF5E35B1), Color(0xFFB39DDB), Color(0xFF7E57C2))
)

@Composable
fun BubbleWrapScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val sm = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    LaunchedEffect(Unit) {
        sm.preloadRaw((1..6).map { "bw_pop_$it" } + "bw_rustle")
    }

    val cols = 7
    val rows = 11
    val total = cols * rows
    var themeIdx by remember { mutableStateOf(0) }
    val theme = BW_THEMES[themeIdx]
    // 0 = целый, 1 = лопнул
    val popped = remember { BooleanArray(total) }
    // анимация лопания: 0..1 (1 — полностью сдулся); swell — «вздувание» при нажатии
    val anim = remember { FloatArray(total) }
    val sparks = remember { mutableListOf<BwSpark>() }
    val ripples = remember { mutableListOf<FloatArray>() } // x, y, t
    var frame by remember { mutableStateOf(0) }
    var poppedCount by remember { mutableStateOf(0) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var lastSound by remember { mutableStateOf(0L) }
    var popAllRunning by remember { mutableStateOf(false) }
    val popAllQueue = remember { ArrayDeque<Int>() }

    fun layoutOf(sz: Size): Triple<Float, Float, Float> {
        val pad = 18f
        val cell = minOf((sz.width - pad * 2) / cols, (sz.height - pad * 2) / rows)
        val ox = (sz.width - cell * cols) / 2f
        val oy = (sz.height - cell * rows) / 2f
        return Triple(cell, ox, oy)
    }

    fun popBubble(i: Int, quiet: Boolean = false) {
        if (popped[i]) return
        popped[i] = true
        poppedCount++
        val (cell, ox, oy) = layoutOf(canvasSize)
        val cx = ox + (i % cols + 0.5f) * cell
        val cy = oy + (i / cols + 0.5f) * cell
        ripples.add(floatArrayOf(cx, cy, 0f))
        val palette = listOf(Color.White, theme.rim, Color(0xFFFFF59D))
        for (k in 0 until 7) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val sp = Random.nextFloat() * cell * 0.09f + cell * 0.03f
            sparks.add(BwSpark(cx, cy, cos(a) * sp, sin(a) * sp, 1f, palette.random(), cell * (0.02f + Random.nextFloat() * 0.03f)))
        }
        val now = System.currentTimeMillis()
        if (!quiet || now - lastSound > 35) {
            lastSound = now
            sm.play("bw_pop_${Random.nextInt(1, 7)}", volume = 1f, rate = 0.94f + Random.nextFloat() * 0.14f)
        }
        if (!quiet) haptics.vibratePop() else if (i % 3 == 0) haptics.vibrate(8L)
    }

    fun newSheet() {
        for (i in 0 until total) { popped[i] = false; anim[i] = 0f }
        poppedCount = 0
        popAllQueue.clear()
        sm.play("bw_rustle", volume = 0.9f)
        haptics.vibrateSquish()
    }

    // кадровый цикл
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            for (i in 0 until total) {
                val target = if (popped[i]) 1f else 0f
                anim[i] += (target - anim[i]) * 0.32f
                if (kotlin.math.abs(target - anim[i]) < 0.002f) anim[i] = target
            }
            val it = sparks.iterator()
            while (it.hasNext()) {
                val s = it.next()
                s.x += s.vx; s.y += s.vy; s.vy += 0.25f; s.vx *= 0.97f; s.life -= 0.045f
                if (s.life <= 0f) it.remove()
            }
            val rit = ripples.iterator()
            while (rit.hasNext()) { val r = rit.next(); r[2] += 0.07f; if (r[2] >= 1f) rit.remove() }
            // «лопнуть всё» — волной
            if (popAllQueue.isNotEmpty()) {
                repeat(2) { if (popAllQueue.isNotEmpty()) popBubble(popAllQueue.removeFirst(), quiet = true) }
            }
            frame++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(theme.bg1, theme.bg2)))
            .testTag("bubble_wrap_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 88.dp, end = 12.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val dark = themeIdx == 3
                Column {
                    Text("Пузырчатая плёнка", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (dark) Color.White else Color(0xFF17324D))
                    Text("Лопнуто: $poppedCount / $total", fontSize = 13.sp, color = if (dark) Color(0xFFD1C4E9) else Color(0xFF37597A))
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.clickable { themeIdx = (themeIdx + 1) % BW_THEMES.size; sm.play("bw_pop_1", volume = 0.5f, rate = 1.3f) }.testTag("theme_button")
                ) {
                    Text("🎨 " + theme.name, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 14.sp, color = Color(0xFF17324D))
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                fun hit(p: Offset) {
                                    val (cell, ox, oy) = layoutOf(canvasSize)
                                    val c = ((p.x - ox) / cell).toInt()
                                    val r = ((p.y - oy) / cell).toInt()
                                    if (c !in 0 until cols || r !in 0 until rows) return
                                    val i = r * cols + c
                                    val cx = ox + (c + 0.5f) * cell
                                    val cy = oy + (r + 0.5f) * cell
                                    if (hypot(p.x - cx, p.y - cy) < cell * 0.5f) popBubble(i)
                                }
                                hit(down.position)
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    if (ev.type == PointerEventType.Release) break
                                    ev.changes.forEach { ch -> if (ch.pressed) { hit(ch.position); ch.consume() } }
                                    if (ev.changes.none { it.pressed }) break
                                }
                            }
                        }
                ) {
                    canvasSize = size
                    val f = frame
                    drawSheet(theme, cols, rows, popped, anim, sparks, ripples, layoutOf(size), f)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f).clickable { newSheet() }.testTag("reset_sheet_btn")
                ) {
                    Box(Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        Text("🔄 Новый лист", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF17324D))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(24.dp), color = Color(0xFFFF7043),
                    modifier = Modifier.weight(1f).clickable {
                        if (popAllQueue.isEmpty()) {
                            val idx = (0 until total).filter { !popped[it] }.sortedBy { (it / cols) * 10 + (it % cols) }
                            popAllQueue.addAll(idx)
                        }
                    }.testTag("pop_all_btn")
                ) {
                    Box(Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        Text("💥 Лопнуть всё", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSheet(
    th: BwTheme, cols: Int, rows: Int,
    popped: BooleanArray, anim: FloatArray,
    sparks: List<BwSpark>, ripples: List<FloatArray>,
    lay: Triple<Float, Float, Float>, frame: Int
) {
    val (cell, ox, oy) = lay
    // плёнка-подложка
    drawRoundRect(
        brush = Brush.linearGradient(listOf(th.film.copy(alpha = 0.55f), th.film.copy(alpha = 0.25f)), Offset.Zero, Offset(size.width, size.height)),
        topLeft = Offset(ox - 10f, oy - 10f), size = Size(cell * cols + 20f, cell * rows + 20f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(34f)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.5f), topLeft = Offset(ox - 10f, oy - 10f),
        size = Size(cell * cols + 20f, cell * rows + 20f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(34f), style = Stroke(2.5f)
    )
    for (i in 0 until cols * rows) {
        val c = i % cols; val r = i / cols
        val cx = ox + (c + 0.5f) * cell
        val cy = oy + (r + 0.5f) * cell
        val R = cell * 0.43f
        val a = anim[i]
        val ctr = Offset(cx, cy)
        // тень/ячейка плёнки
        drawCircle(Color.Black.copy(alpha = 0.10f), R * 1.04f, Offset(cx + R * 0.06f, cy + R * 0.10f))
        if (a < 0.5f) {
            // целый пузырь: «хлопок» — быстрый кратковременный рост при лопании
            val k = 1f + (if (a > 0f) a * 0.35f else 0f)
            val rr = R * k * (1f - a * 1.2f).coerceAtLeast(0.1f)
            drawCircle(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.75f),
                    0.45f to th.film.copy(alpha = 0.35f),
                    0.85f to th.rim.copy(alpha = 0.55f),
                    1f to th.rim.copy(alpha = 0.9f),
                    center = Offset(cx - R * 0.25f, cy - R * 0.3f), radius = R * 1.35f
                ), rr, ctr
            )
            drawCircle(Color.White.copy(alpha = 0.7f), rr, ctr, style = Stroke(cell * 0.025f))
            // блик-полумесяц
            drawArc(
                Color.White.copy(alpha = 0.9f), startAngle = 200f, sweepAngle = 70f, useCenter = false,
                topLeft = Offset(cx - rr * 0.68f, cy - rr * 0.68f), size = Size(rr * 1.36f, rr * 1.36f),
                style = Stroke(cell * 0.05f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            drawCircle(Color.White, rr * 0.11f, Offset(cx - rr * 0.38f, cy - rr * 0.42f))
            drawCircle(Color.White.copy(alpha = 0.35f), rr * 0.06f, Offset(cx + rr * 0.42f, cy + rr * 0.45f))
        } else {
            // сдувшаяся плоская плёнка с морщинками
            drawCircle(th.film.copy(alpha = 0.35f), R * 0.95f, ctr)
            drawCircle(Color.Black.copy(alpha = 0.18f), R * 0.95f, ctr, style = Stroke(cell * 0.03f))
            val seed = i * 7919
            for (w in 0 until 4) {
                val ang = ((seed + w * 97) % 360) * PI.toFloat() / 180f
                val len = R * (0.35f + ((seed + w * 31) % 40) / 100f)
                val s = Offset(cx - cos(ang) * len * 0.5f, cy - sin(ang) * len * 0.5f)
                val e = Offset(cx + cos(ang) * len * 0.5f, cy + sin(ang) * len * 0.5f)
                drawLine(Color.White.copy(alpha = 0.45f), s, e, cell * 0.02f)
                drawLine(Color.Black.copy(alpha = 0.15f), s + Offset(2f, 2f), e + Offset(2f, 2f), cell * 0.018f)
            }
        }
    }
    for (rp in ripples) {
        drawCircle(Color.White.copy(alpha = (1f - rp[2]) * 0.8f), cell * (0.3f + rp[2] * 0.7f), Offset(rp[0], rp[1]), style = Stroke(cell * 0.04f * (1f - rp[2])))
    }
    for (s in sparks) {
        drawCircle(s.c.copy(alpha = s.life.coerceIn(0f, 1f)), s.r * s.life.coerceIn(0.2f, 1f), Offset(s.x, s.y))
    }
}
