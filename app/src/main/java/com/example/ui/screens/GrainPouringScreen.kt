package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.PI
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.LocalAgeProfile
import com.example.shared.SoundManager
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * «Сыпучести» — пересыпай крупу и песок из одной банки в другую.
 *
 * Две банки: одна полная, другая пустая. Нажми на полную — она приподнимется и наклонится
 * к соседке. Нажми на пустую — всё пересыплется. Нажми на поднятую банку — она опустится.
 * Сверху — выбор: песок (банки поменьше), рис, гречка, горох, чечевица.
 */

private class GrainType(val name: String, val emoji: String, val color: Color, val dark: Color, val small: Boolean)

private val GRAIN_TYPES = listOf(
    GrainType("Песок", "🏖️", Color(0xFFE8C77A), Color(0xFFB98F3A), true),
    GrainType("Рис", "🍚", Color(0xFFFFFDF2), Color(0xFFCFC6A0), false),
    GrainType("Гречка", "🌰", Color(0xFF8D5B34), Color(0xFF5A3418), false),
    GrainType("Горох", "🟢", Color(0xFF8BC34A), Color(0xFF4E8A1F), false),
    GrainType("Чечевица", "🔴", Color(0xFFE2694A), Color(0xFF9E3520), false)
)

private class SJar(var level: Float) { var tilt = 0f }
private class SPart(var x: Float, var y: Float, var vx: Float, var vy: Float)

private class PourWorld(val dp: Float) {
    var w = 0f
    var h = 0f
    var jw = 0f
    var jh = 0f
    var floorY = 0f
    var typeIdx = 0
    val jars = arrayOf(SJar(1f), SJar(0f))
    var lifted = -1
    var pouring = false
    val parts = ArrayList<SPart>()
    var emitAcc = 0f
    var time = 0f
    var shake = 0f
    var shakeJar = -1
    var onEvent: (String) -> Unit = {}

    fun layout(width: Float, height: Float) {
        w = width; h = height
        val small = GRAIN_TYPES[typeIdx].small
        jw = min(min(w * 0.26f, h * 0.27f), 170f * dp) * (if (small) 0.78f else 1f)
        jh = jw * 1.4f
        floorY = h * 0.82f
    }

    fun reset(type: Int) {
        typeIdx = type
        jars[0].level = 1f; jars[1].level = 0f
        jars[0].tilt = 0f; jars[1].tilt = 0f
        lifted = -1; pouring = false; parts.clear()
        layout(w, h)
    }

    fun baseCx(i: Int) = if (i == 0) w * 0.27f else w * 0.73f
    fun baseCy() = floorY - jh / 2f
    private fun sgn(i: Int) = if (i == 0) 1f else -1f
    private val maxTiltRad = (68.0 * PI / 180.0).toFloat()

    fun angleRad(i: Int) = sgn(i) * maxTiltRad * jars[i].tilt

    /** Смещение центра поднятой банки (dx, dy) в пикселях. */
    fun offset(i: Int): Offset {
        val t = jars[i].tilt
        if (t <= 0f) return Offset.Zero
        val s = sgn(i)
        val th = s * maxTiltRad
        val lx = s * jw / 2f; val ly = -jh / 2f
        val lipX = lx * cos(th) - ly * sin(th)
        val lipY = lx * sin(th) + ly * cos(th)
        val targetX = baseCx(1 - i) - s * 0.2f * jw
        val dxFull = targetX - (baseCx(i) + lipX)
        val targetLipY = floorY - jh - 34f * dp
        val dyFull = (targetLipY - lipY) - baseCy()
        return Offset(dxFull * t, dyFull * t)
    }

    fun center(i: Int): Offset { val o = offset(i); return Offset(baseCx(i) + o.x, baseCy() + o.y) }

    fun lipPos(i: Int): Offset {
        val s = sgn(i)
        val th = angleRad(i)
        val lx = s * jw / 2f; val ly = -jh / 2f
        val c = center(i)
        return Offset(c.x + lx * cos(th) - ly * sin(th), c.y + lx * sin(th) + ly * cos(th))
    }

    private fun hitBase(i: Int, p: Offset): Boolean {
        val cx = baseCx(i)
        return p.x in (cx - jw * 0.7f)..(cx + jw * 0.7f) && p.y in (floorY - jh * 1.25f)..(floorY + 30f * dp)
    }

    private fun hitLifted(i: Int, p: Offset): Boolean {
        val c = center(i)
        return abs(p.x - c.x) < jh * 0.75f && abs(p.y - c.y) < jh * 0.75f
    }

    fun tap(p: Offset) {
        if (lifted == -1) {
            for (i in 0..1) {
                if (hitBase(i, p)) {
                    if (jars[i].level > 0.02f) { lifted = i; onEvent("lift") }
                    else { shake = 0.5f; shakeJar = i; onEvent("empty") }
                    return
                }
            }
        } else {
            val i = lifted
            val o = 1 - i
            if (hitLifted(i, p)) {
                pouring = false; lifted = -1; onEvent("lower")
            } else if (hitBase(o, p) && jars[i].tilt > 0.85f && !pouring) {
                pouring = true; emitAcc = 0f; onEvent("pourStart")
            }
        }
    }

    fun step(dtRaw: Float) {
        if (w <= 0f) return
        val dt = dtRaw.coerceIn(0.001f, 0.033f)
        time += dt
        if (shake > 0f) shake = max(0f, shake - dt)
        for (i in 0..1) {
            val target = if (lifted == i) 1f else 0f
            val j = jars[i]
            j.tilt += (target - j.tilt) * min(1f, dt * 6f)
            if (abs(target - j.tilt) < 0.004f) j.tilt = target
        }
        if (pouring && lifted >= 0) {
            val src = jars[lifted]; val dst = jars[1 - lifted]
            val amt = min(0.30f * dt, min(src.level, 1f - dst.level))
            src.level -= amt; dst.level += amt
            emitAcc += amt * 900f
            val lip = lipPos(lifted)
            val s = sgn(lifted)
            while (emitAcc >= 1f) {
                emitAcc -= 1f
                parts.add(SPart(lip.x + (Random.nextFloat() - 0.5f) * 8f * dp, lip.y,
                    s * (40f + Random.nextFloat() * 50f) * dp, (Random.nextFloat() * 60f) * dp))
            }
            if (src.level <= 0.003f || dst.level >= 0.997f) {
                if (src.level <= 0.003f) src.level = 0f
                if (dst.level >= 0.997f) dst.level = 1f
                pouring = false; lifted = -1; onEvent("done")
            }
        }
        val it = parts.iterator()
        val dstIdx = if (lifted >= 0) 1 - lifted else -1
        while (it.hasNext()) {
            val g = it.next()
            g.vy += 1700f * dp * dt
            g.x += g.vx * dt; g.y += g.vy * dt
            val ground = floorY - (if (dstIdx >= 0) jars[dstIdx].level else 0.5f) * jh
            if (g.y >= ground || g.y > h + 20f) it.remove()
        }
        // если банку опустили — оставшиеся зёрнышки просто падают в пол
    }
}

@Composable
fun GrainPouringScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val dp = LocalDensity.current.density
    val world = remember { PourWorld(dp) }
    var stars by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var typeIdx by remember { mutableIntStateOf(0) }
    var everLifted by remember { mutableIntStateOf(0) }
    var loopHandle by remember { androidx.compose.runtime.mutableStateOf<com.example.shared.LoopHandle?>(null) }

    fun stopPour() {
        loopHandle?.let { soundManager.stopLoop(it) }
        loopHandle = null
    }

    world.onEvent = { ev ->
        when (ev) {
            "lift" -> { everLifted++; soundPlayer.playPop(1.1f) }
            "lower" -> { stopPour(); soundPlayer.playPop(0.8f) }
            "empty" -> soundManager.play("boing", volume = 0.5f)
            "pourStart" -> { stopPour(); loopHandle = soundManager.startLoop("grain_pour") }
            "done" -> { stopPour(); stars++; soundPlayer.playChime(1.2f) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            world.pouring = false
            loopHandle?.let { soundManager.stopLoop(it, 0) }
            loopHandle = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF8E1), Color(0xFFFFE0B2))))
            .testTag("grain_pouring_root")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { world.tap(it) }
                }
        ) {
            if (world.w != size.width || world.h != size.height) world.layout(size.width, size.height)
            @Suppress("UNUSED_VARIABLE") val redraw = tick
            val type = GRAIN_TYPES[world.typeIdx]
            val floor = world.floorY
            val jw = world.jw
            val jh = world.jh

            // Стол.
            drawRect(Color(0xFFA1887F), Offset(0f, floor), Size(size.width, size.height - floor))
            drawRect(Color(0xFF8D6E63), Offset(0f, floor), Size(size.width, 6f * dp))

            // Порядок: сначала неподнятые, потом поднятая.
            val order = if (world.lifted == 0) listOf(1, 0) else listOf(0, 1)
            for (i in order) {
                val jar = world.jars[i]
                val bc = Offset(world.baseCx(i), world.baseCy())
                val off = world.offset(i)
                val deg = world.angleRad(i) * 57.29578f
                // тень
                drawOval(Color.Black.copy(alpha = 0.15f), Offset(bc.x - jw * 0.55f, floor - 6f * dp), Size(jw * 1.1f, 14f * dp))
                var shx = 0f
                if (world.shake > 0f && world.shakeJar == i) shx = sin(world.time * 50f) * 6f * dp * world.shake * 2f
                withTransform({
                    translate(off.x + shx, off.y)
                    rotate(deg, pivot = bc)
                }) {
                    val jarRect = androidx.compose.ui.geometry.Rect(bc.x - jw / 2f, bc.y - jh / 2f, bc.x + jw / 2f, bc.y + jh / 2f)
                    val jarPath = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(jarRect, CornerRadius(14f * dp))) }
                    drawPath(jarPath, Color.White.copy(alpha = 0.35f))
                    if (jar.level > 0.003f) {
                        clipPath(jarPath) {
                            rotate(-deg, pivot = bc) {
                                // нижняя точка и «губа» банки в мировых осях
                                val th = deg * 0.0174533f
                                val cs = cos(th); val sn = sin(th)
                                fun ry(lx: Float, ly: Float) = lx * sn + ly * cs
                                val lowest = max(ry(-jw / 2f, jh / 2f), ry(jw / 2f, jh / 2f))
                                val lipLow = max(ry(-jw / 2f, -jh / 2f), ry(jw / 2f, -jh / 2f))
                                val surface = lowest - jar.level * (lowest - lipLow)
                                val sy = bc.y + surface
                                drawRect(type.color, Offset(bc.x - jw, sy), Size(jw * 2f, (lowest - surface) + 40f * dp))
                                // горка под струёй
                                if (world.pouring && world.lifted >= 0 && world.lifted != i) {
                                    drawOval(type.color, Offset(bc.x - jw * 0.28f, sy - 9f * dp), Size(jw * 0.56f, 20f * dp))
                                }
                                // зёрнышки
                                val n = if (type.small) 130 else 80
                                for (k in 0 until n) {
                                    val hx = ((k * 7919 + 13) % 1000) / 1000f
                                    val hy = ((k * 104729 + 7) % 1000) / 1000f
                                    val gx = bc.x - jw / 2f + hx * jw
                                    val gy = sy + hy * (lowest - surface)
                                    drawCircle(type.dark.copy(alpha = 0.45f), (if (type.small) 1.6f else 3.2f) * dp, Offset(gx, gy))
                                }
                                drawRect(Color.White.copy(alpha = 0.25f), Offset(bc.x - jw, sy), Size(jw * 2f, 3f * dp))
                            }
                        }
                    }
                    // стекло и горлышко
                    val edge = Color(0xFF64B5F6)
                    drawPath(jarPath, edge, style = Stroke(width = 4f * dp))
                    drawRoundRect(Color(0xFF90CAF9), Offset(bc.x - jw * 0.58f, bc.y - jh / 2f - 7f * dp), Size(jw * 1.16f, 12f * dp), CornerRadius(6f * dp))
                    drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(bc.x - jw * 0.36f, bc.y - jh * 0.38f), Size(6f * dp, jh * 0.6f), CornerRadius(3f * dp))
                }
            }

            // Падающие зёрна.
            for (g in world.parts) drawCircle(type.color, 2.6f * dp, Offset(g.x, g.y))
            for (g in world.parts) drawCircle(type.dark.copy(alpha = 0.5f), 1.4f * dp, Offset(g.x + 1f, g.y + 1f))

            // Подсказка без слов: прыгающая стрелка.
            if (!world.pouring) {
                val bounce = sin(world.time * 5f) * 8f * dp
                var ax = 0f; var ay = 0f; var show = false
                if (world.lifted == -1) {
                    val full = if (world.jars[0].level >= world.jars[1].level) 0 else 1
                    if (world.jars[full].level > 0.02f && (everLifted < 3 || world.time % 12f > 8f)) {
                        ax = world.baseCx(full); ay = floor - jh - 26f * dp; show = true
                    }
                } else if (world.jars[world.lifted].tilt > 0.85f) {
                    val o = 1 - world.lifted
                    ax = world.baseCx(o); ay = floor - jh - 80f * dp; show = true
                }
                if (show) {
                    val ar = Path().apply {
                        moveTo(ax, ay + bounce + 26f * dp)
                        lineTo(ax - 18f * dp, ay + bounce)
                        lineTo(ax + 18f * dp, ay + bounce)
                        close()
                    }
                    drawPath(ar, Color(0xFFFF7043))
                    drawPath(ar, Color(0xFFBF360C), style = Stroke(3f * dp))
                }
            }
        }

        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(88.dp))
            for ((idx, t) in GRAIN_TYPES.withIndex()) {
                val selected = idx == typeIdx
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(t.color)
                        .border(if (selected) 4.dp else 2.dp, if (selected) Color(0xFFFF7043) else Color.White, CircleShape)
                        .clickable {
                            typeIdx = idx
                            stopPour()
                            world.reset(idx)
                            soundPlayer.playPop(1.2f)
                        },
                    contentAlignment = Alignment.Center
                ) { Text(t.emoji, fontSize = 22.sp) }
            }
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.85f),
                shadowElevation = 4.dp
            ) {
                Text(
                    "⭐ $stars",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5D4037)
                )
            }
        }
    }

    LaunchedEffect(world) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) world.step((now - last) / 1_000_000_000f)
                last = now
                tick++
            }
        }
    }
}
