package com.example.screens.Busyboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.example.shared.ColorInk
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * Комната 1 «Замочки и свет»: задвижка, выключатель с лампой, молния с сюрпризом, замок и ключ.
 * Каждая деталь — Canvas + крошечный автомат состояний; звуки идут только от дискретных событий.
 */

private val WoodDark = Color(0xFF8D5A2B)
private val WoodLight = Color(0xFFE8B070)
private val Metal = Color(0xFFC9D2DB)
private val MetalDark = Color(0xFF7B8794)
private val Gold = Color(0xFFFFC93C)

internal fun DrawScope.bbPlate(topLeft: Offset, size: Size, radius: Float, fill: Color, strokeW: Float) {
    drawRoundRect(fill, topLeft, size, CornerRadius(radius, radius))
    drawRoundRect(ColorInk, topLeft, size, CornerRadius(radius, radius), style = Stroke(strokeW))
}

private fun Color.mix(other: Color, t: Float): Color = lerp(this, other, t.coerceIn(0f, 1f))

// ═════════════════════════════ 1. ЗАДВИЖКА ═════════════════════════════

private class LatchState {
    val p = BbSpring(0f, 0f, 200f, 18f)
    var dragging = false
    var open = false
    var pop = 0f
    var prevX = 0f
    var w = 1f
    var h = 1f
    val particles = BbParticles(48)
}

/** Задвижка: двигаешь засов — дверка распахивается, за ней зверёк. Тап по доске тоже переключает. */
@Composable
internal fun BbLatch(ctx: BbCtx, modifier: Modifier = Modifier) {
    val st = remember { LatchState() }
    val paint = remember { bbEmojiPaint() }
    val dens = LocalDensity.current.density

    val afterMove = remember {
        {
            val x = st.p.x
            val n = bbCrossings(st.prevX, x, 0.125f)
            if (n > 0) ctx.audio.play(BbSound.TICK, 0.85f + 0.4f * x.coerceIn(0f, 1f))
            if (!st.open && x >= 0.96f) {
                st.open = true
                ctx.audio.play(BbSound.CLACK)
                ctx.thump()
                ctx.reward(BbElement.LATCH)
                st.particles.burst(st.w * 0.5f, st.h * 0.28f, ctx.n(14), 260f * dens, 0.9f, 8f * dens, BB_CONFETTI)
            } else if (st.open && x <= 0.4f) {
                st.open = false
                ctx.audio.play(BbSound.CLACK, 0.9f)
            }
            st.prevX = x
        }
    }

    val tick = rememberBbTick { dt ->
        var redraw = false
        if (!st.dragging && !st.p.settled) {
            st.p.step(dt)
            afterMove()
            redraw = true
        }
        val target = if (st.open) 1f else 0f
        if (abs(st.pop - target) > 0.002f) {
            st.pop += (target - st.pop) * min(1f, dt * 9f)
            redraw = true
        }
        if (st.particles.update(dt, 520f * dens)) redraw = true
        redraw
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { st.w = it.width.toFloat(); st.h = it.height.toFloat() }
            .bbGesture(
                onDown = {
                    ctx.touch()
                    st.dragging = true
                    ctx.buzz()
                },
                onMove = { _, d ->
                    val travel = max(1f, st.w * 0.55f)
                    st.p.snap((st.p.x + d.x / travel).coerceIn(0f, 1f))
                    afterMove()
                    tick.bump()
                },
                onUp = { _, wasTap ->
                    st.dragging = false
                    st.p.target = if (wasTap) (if (st.p.x > 0.5f) 0f else 1f) else if (st.p.x > 0.5f) 1f else 0f
                }
            )
    ) {
        tick.value
        val w = size.width
        val h = size.height
        val x = st.p.x.coerceIn(0f, 1f)
        val d = dens

        // дверка с зверьком за ней
        val fx0 = w * 0.16f
        val fx1 = w * 0.84f
        val fy0 = h * 0.04f
        val fy1 = h * 0.55f
        bbPlate(Offset(fx0 - 5 * d, fy0 - 5 * d), Size(fx1 - fx0 + 10 * d, fy1 - fy0 + 10 * d), 14 * d, WoodDark, 3 * d)
        drawRoundRect(Color(0xFF3B2A4A), Offset(fx0, fy0), Size(fx1 - fx0, fy1 - fy0), CornerRadius(10 * d, 10 * d))
        val ecx = (fx0 + fx1) / 2f
        val ecy = (fy0 + fy1) / 2f + (1f - st.pop) * h * 0.05f
        bbEmoji("🐥", ecx, ecy, min(fx1 - fx0, fy1 - fy0) * 0.7f * (0.4f + 0.6f * st.pop), paint, st.pop)
        val leafW = (fx1 - fx0) * (1f - 0.88f * x)
        drawRoundRect(WoodLight, Offset(fx0, fy0), Size(leafW, fy1 - fy0), CornerRadius(10 * d, 10 * d))
        drawRoundRect(ColorInk, Offset(fx0, fy0), Size(leafW, fy1 - fy0), CornerRadius(10 * d, 10 * d), style = Stroke(3 * d))
        if (leafW > 24 * d) {
            drawRoundRect(WoodDark.copy(alpha = 0.35f), Offset(fx0 + 10 * d, fy0 + 10 * d), Size(leafW - 20 * d, (fy1 - fy0) * 0.38f), CornerRadius(8 * d, 8 * d))
            drawCircle(Gold, 7 * d, Offset(fx0 + leafW - 12 * d, (fy0 + fy1) / 2f))
            drawCircle(ColorInk, 7 * d, Offset(fx0 + leafW - 12 * d, (fy0 + fy1) / 2f), style = Stroke(2 * d))
        }

        // планка с засовом
        val py0 = h * 0.64f
        val py1 = h * 0.93f
        bbPlate(Offset(w * 0.07f, py0), Size(w * 0.86f, py1 - py0), 14 * d, Metal, 3 * d)
        for (rx in listOf(w * 0.07f + 12 * d, w * 0.93f - 12 * d)) for (ry in listOf(py0 + 12 * d, py1 - 12 * d)) {
            drawCircle(MetalDark, 3.5f * d, Offset(rx, ry))
        }
        val sx0 = w * 0.22f
        val sx1 = w * 0.76f
        val sy = (py0 + py1) / 2f
        drawLine(Color(0xFF4A5560), Offset(sx0, sy), Offset(sx1, sy), strokeWidth = 13 * d, cap = StrokeCap.Round)
        drawRoundRect(MetalDark, Offset(w * 0.79f, sy - 15 * d), Size(11 * d, 30 * d), CornerRadius(4 * d, 4 * d))
        val kx = sx0 + (sx1 - sx0) * x
        drawLine(Color(0xFF9AA5B1), Offset(sx0 - 6 * d, sy), Offset(kx, sy), strokeWidth = 9 * d, cap = StrokeCap.Round)
        drawCircle(Color(0xFFFF6F61), 17 * d, Offset(kx, sy))
        drawCircle(ColorInk, 17 * d, Offset(kx, sy), style = Stroke(3 * d))
        drawCircle(Color.White.copy(alpha = 0.55f), 5 * d, Offset(kx - 5 * d, sy - 5 * d))

        st.particles.draw(this)
    }
}

// ═════════════════════════════ 2. ВЫКЛЮЧАТЕЛЬ С ЛАМПОЙ ═════════════════════════════

private val LAMP_COLORS = listOf(
    Color(0xFFFFE066), Color(0xFFFF8FB1), Color(0xFF8EDC9D), Color(0xFF7FC8FF), Color(0xFFFFB36B)
)
private val LAMP_RATES = floatArrayOf(1.0f, 1.122f, 1.26f, 1.498f, 1.682f)

private class SwitchState {
    var on = false
    val lever = BbSpring(0f, 0f, 320f, 22f)
    var glow = 0f
    var colorIdx = 0
    var acc = 0f
    var decided = false
    var w = 1f
    var h = 1f
    val particles = BbParticles(40)
}

/** Выключатель: тап или смахивание вверх/вниз — лампочка загорается мягким светом и «пинькает». */
@Composable
internal fun BbSwitch(ctx: BbCtx, modifier: Modifier = Modifier) {
    val st = remember { SwitchState() }
    val dens = LocalDensity.current.density

    fun set(on: Boolean) {
        if (st.on == on) return
        st.on = on
        st.lever.target = if (on) 1f else 0f
        ctx.audio.play(BbSound.CLICK, if (on) 1f else 0.85f)
        ctx.buzz()
        if (on) {
            ctx.audio.play(BbSound.LAMP, LAMP_RATES[st.colorIdx % LAMP_RATES.size])
            ctx.reward(BbElement.SWITCH)
            val c = LAMP_COLORS[st.colorIdx % LAMP_COLORS.size]
            st.particles.burst(st.w * 0.5f, st.h * 0.24f, ctx.n(10), 200f * dens, 0.8f, 7f * dens, listOf(c, Color.White), BB_STAR)
        } else {
            st.colorIdx++
        }
    }

    val tick = rememberBbTick { dt ->
        var redraw = false
        if (!st.lever.settled) {
            st.lever.step(dt)
            redraw = true
        }
        val target = if (st.on) 1f else 0f
        if (abs(st.glow - target) > 0.003f) {
            st.glow += (target - st.glow) * min(1f, dt * 10f)
            redraw = true
        }
        if (st.particles.update(dt, 300f * dens)) redraw = true
        redraw
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { st.w = it.width.toFloat(); st.h = it.height.toFloat() }
            .bbGesture(
                onDown = {
                    ctx.touch()
                    st.acc = 0f
                    st.decided = false
                },
                onMove = { _, d ->
                    if (!st.decided) {
                        st.acc += d.y
                        val th = 14f * dens
                        if (st.acc < -th) { st.decided = true; set(true) }
                        else if (st.acc > th) { st.decided = true; set(false) }
                    }
                },
                onUp = { _, wasTap -> if (wasTap || !st.decided) set(!st.on) } // «смазанный» тап тоже переключает
            )
    ) {
        tick.value
        val w = size.width
        val h = size.height
        val d = dens
        val g = st.glow
        val lamp = LAMP_COLORS[st.colorIdx % LAMP_COLORS.size]
        val bulb = Offset(w * 0.5f, h * 0.26f)

        // световой конус и ореол
        if (g > 0.01f) {
            val cone = Path().apply {
                moveTo(bulb.x - 12 * d, bulb.y + 14 * d)
                lineTo(bulb.x + 12 * d, bulb.y + 14 * d)
                lineTo(w * 0.98f, h * 0.60f)
                lineTo(w * 0.02f, h * 0.60f)
                close()
            }
            drawPath(cone, Brush.verticalGradient(listOf(lamp.copy(alpha = 0.38f * g), Color.Transparent), startY = bulb.y, endY = h * 0.60f))
            drawCircle(Brush.radialGradient(listOf(lamp.copy(alpha = 0.85f * g), Color.Transparent), center = bulb, radius = w * 0.46f), w * 0.46f, bulb)
        }

        // шнур и патрон
        drawLine(ColorInk, Offset(bulb.x, 0f), Offset(bulb.x, bulb.y - 30 * d), strokeWidth = 3 * d)
        bbPlate(Offset(bulb.x - 12 * d, bulb.y - 32 * d), Size(24 * d, 18 * d), 5 * d, MetalDark, 2.5f * d)
        // колба
        val r = 26 * d
        drawCircle(Color(0xFFDDE3E8).mix(lamp, g), r, bulb)
        drawCircle(ColorInk, r, bulb, style = Stroke(3 * d))
        drawCircle(Color.White.copy(alpha = 0.6f), 7 * d, Offset(bulb.x - 9 * d, bulb.y - 9 * d))
        // нить накала
        val fil = Path().apply {
            moveTo(bulb.x - 8 * d, bulb.y + 10 * d)
            lineTo(bulb.x - 4 * d, bulb.y - 2 * d)
            lineTo(bulb.x, bulb.y + 6 * d)
            lineTo(bulb.x + 4 * d, bulb.y - 2 * d)
            lineTo(bulb.x + 8 * d, bulb.y + 10 * d)
        }
        drawPath(fil, if (g > 0.5f) Color(0xFFFF9F1C) else Color(0xFF9AA5B1), style = Stroke(2.5f * d, cap = StrokeCap.Round))

        // настенная плита и клавиша
        val px0 = w * 0.30f
        val px1 = w * 0.70f
        val py0 = h * 0.62f
        val py1 = h * 0.94f
        bbPlate(Offset(px0, py0), Size(px1 - px0, py1 - py0), 16 * d, Color(0xFFF4F1EA), 3 * d)
        val rx0 = px0 + 10 * d
        val rx1 = px1 - 10 * d
        val ry0 = py0 + 10 * d
        val ry1 = py1 - 10 * d
        val lv = st.lever.x.coerceIn(0f, 1f)
        val split = (ry0 + ry1) / 2f + (0.5f - lv) * (ry1 - ry0) * 0.45f
        drawRoundRect(Color(0xFFE9573F).mix(Color(0xFFF7A08F), lv), Offset(rx0, ry0), Size(rx1 - rx0, split - ry0), CornerRadius(10 * d, 10 * d))
        drawRoundRect(Color(0xFFB8C0C8).mix(Color(0xFF8B96A1), lv), Offset(rx0, split), Size(rx1 - rx0, ry1 - split), CornerRadius(10 * d, 10 * d))
        drawRoundRect(ColorInk, Offset(rx0, ry0), Size(rx1 - rx0, ry1 - ry0), CornerRadius(10 * d, 10 * d), style = Stroke(2.5f * d))
        drawLine(ColorInk, Offset(rx0, split), Offset(rx1, split), strokeWidth = 2.5f * d)
        drawCircle(if (st.on) Color(0xFF5CCB6E) else Color(0xFF9AA5B1), 4 * d, Offset((px0 + px1) / 2f, py0 - 8 * d))

        st.particles.draw(this)
    }
}

// ═════════════════════════════ 3. МОЛНИЯ С СЮРПРИЗОМ ═════════════════════════════

private val ZIP_TOYS = listOf("🧸", "🦆", "🐙", "🚗", "🦄", "🐶")

private class ZipperState {
    var p = 0f
    var open = false
    var opens = 0
    var toyPop = 0f
    var dragging = false
    var w = 1f
    var h = 1f
    val particles = BbParticles(48)
}

/** Молния: тянешь замочек вниз — зубчики щёлкают по одному, а в раскрытой щели прячется игрушка. */
@Composable
internal fun BbZipper(ctx: BbCtx, modifier: Modifier = Modifier) {
    val st = remember { ZipperState() }
    val paint = remember { bbEmojiPaint() }
    val dens = LocalDensity.current.density

    val tick = rememberBbTick { dt ->
        var redraw = false
        val target = if (st.open) 1f else 0f
        if (abs(st.toyPop - target) > 0.002f) {
            st.toyPop += (target - st.toyPop) * min(1f, dt * 8f)
            redraw = true
        }
        if (st.particles.update(dt, 480f * dens)) redraw = true
        redraw
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { st.w = it.width.toFloat(); st.h = it.height.toFloat() }
            .bbGesture(
                onDown = { ctx.touch(); st.dragging = true; ctx.buzz() },
                onMove = { _, d ->
                    val travel = max(1f, st.h * 0.62f)
                    val old = st.p
                    st.p = (old + d.y / travel).coerceIn(0f, 1f)
                    val n = bbCrossings(old, st.p, 1f / 20f)
                    if (n > 0) ctx.audio.play(BbSound.ZIP, 0.92f + 0.16f * st.p)
                    if (!st.open && st.p >= 0.93f) {
                        st.open = true
                        st.opens++
                        ctx.audio.play(BbSound.CLACK, 0.8f)
                        ctx.thump()
                        ctx.reward(BbElement.ZIPPER)
                        st.particles.burst(st.w * 0.5f, st.h * 0.42f, ctx.n(14), 260f * dens, 0.9f, 8f * dens, BB_CONFETTI)
                    } else if (st.open && st.p <= 0.35f) {
                        st.open = false
                    }
                    tick.bump()
                },
                onUp = { _, wasTap ->
                    st.dragging = false
                    if (wasTap) {
                        // тап: молния сама «доезжает» до ближайшего края
                        val to = if (st.p > 0.5f) 0f else 1f
                        st.p = to
                        st.open = to > 0.5f
                        if (st.open) {
                            st.opens++
                            ctx.reward(BbElement.ZIPPER)
                            ctx.audio.play(BbSound.ZIP)
                            ctx.audio.play(BbSound.CLACK, 0.8f)
                            st.particles.burst(st.w * 0.5f, st.h * 0.42f, ctx.n(12), 240f * dens, 0.9f, 8f * dens, BB_CONFETTI)
                        } else {
                            ctx.audio.play(BbSound.ZIP, 0.9f)
                        }
                        tick.bump()
                    }
                }
            )
    ) {
        tick.value
        val w = size.width
        val h = size.height
        val d = dens
        val cx = w / 2f
        val y0 = h * 0.06f
        val yEnd = h * 0.84f
        val ys = y0 + st.p * (yEnd - y0)
        val gmax = w * 0.30f

        // ткань
        drawRect(Color(0xFF4F8FE0))
        for (i in 0..6) drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, h * (0.1f + i * 0.14f)), Offset(w, h * (0.1f + i * 0.14f) - 18 * d), strokeWidth = 5 * d)

        // раскрытая щель: тёмный карман с игрушкой
        val gap = Path().apply {
            moveTo(cx, ys)
            lineTo(cx - gmax, y0 - 8 * d)
            lineTo(cx + gmax, y0 - 8 * d)
            close()
        }
        clipPath(gap) {
            drawRect(Color(0xFF3B2A4A))
            val toyY = ys - (ys - y0) * 0.45f - (1f - st.toyPop) * 14 * d
            val toySize = min(gmax * 1.5f, (ys - y0) * 0.9f) * (0.3f + 0.7f * st.toyPop)
            if (st.toyPop > 0.02f) bbEmoji(ZIP_TOYS[(max(st.opens, 1) - 1) % ZIP_TOYS.size], cx, toyY, toySize, paint, st.toyPop)
        }

        // зубчики: раздвинуты над замочком, сцеплены под ним
        val pitch = 13 * d
        val tw = 10 * d
        val th = 7 * d
        var yy = y0
        var i = 0
        while (yy < ys) {
            val half = gmax * (1f - (yy - y0) / max(1f, ys - y0))
            val c = if (i % 2 == 0) Gold else Color(0xFFE8ECEF)
            drawRoundRect(c, Offset(cx - half - tw, yy), Size(tw, th), CornerRadius(2 * d, 2 * d))
            drawRoundRect(ColorInk.copy(alpha = 0.55f), Offset(cx - half - tw, yy), Size(tw, th), CornerRadius(2 * d, 2 * d), style = Stroke(1 * d))
            drawRoundRect(c, Offset(cx + half, yy + th * 0.4f), Size(tw, th), CornerRadius(2 * d, 2 * d))
            drawRoundRect(ColorInk.copy(alpha = 0.55f), Offset(cx + half, yy + th * 0.4f), Size(tw, th), CornerRadius(2 * d, 2 * d), style = Stroke(1 * d))
            yy += pitch
            i++
        }
        yy = ys
        while (yy < yEnd + 20 * d) {
            val left = i % 2 == 0
            val c = if (left) Gold else Color(0xFFE8ECEF)
            val x0 = if (left) cx - tw else cx
            drawRoundRect(c, Offset(x0, yy), Size(tw, th), CornerRadius(2 * d, 2 * d))
            drawRoundRect(ColorInk.copy(alpha = 0.55f), Offset(x0, yy), Size(tw, th), CornerRadius(2 * d, 2 * d), style = Stroke(1 * d))
            yy += pitch * 0.6f
            i++
        }

        // замочек и язычок
        bbPlate(Offset(cx - 20 * d, ys - 6 * d), Size(40 * d, 46 * d), 10 * d, Gold, 3 * d)
        bbPlate(Offset(cx - 9 * d, ys + 38 * d), Size(18 * d, 34 * d), 9 * d, Color(0xFFFF6F61), 3 * d)
        drawCircle(Color.White.copy(alpha = 0.6f), 4 * d, Offset(cx - 8 * d, ys + 4 * d))

        st.particles.draw(this)
    }
}

// ═════════════════════════════ 4. ЗАМОК И КЛЮЧ ═════════════════════════════

private val PRIZES = listOf("🍬", "🌟", "🎁", "🍓", "🎈")

private class KeyLockState {
    var phase = 0              // 0 — ключ свободен, 1 — поворачивается, 2 — открыто
    var kx = 0f                // центр ключа (кольцо)
    var ky = 0f
    var grabDx = 0f
    var grabDy = 0f
    var dragging = false
    var turn = 0f              // 0..1 поворот ключа и дужки
    var lid = 0f               // 0..1 крышка сундучка
    var opens = 0
    var w = 1f
    var h = 1f
    var inited = false
    val particles = BbParticles(48)
}

/** Замок и ключ: перетащи ключ к замочной скважине — он сам повернётся, сундучок откроется и покажет сюрприз. */
@Composable
internal fun BbKeyLock(ctx: BbCtx, modifier: Modifier = Modifier) {
    val st = remember { KeyLockState() }
    val paint = remember { bbEmojiPaint() }
    val dens = LocalDensity.current.density

    fun homeX() = st.w * 0.22f
    fun homeY() = st.h * 0.90f
    fun holeX() = st.w * 0.5f
    fun holeY() = st.h * 0.60f
    fun keyLen() = 52f * dens

    val tick = rememberBbTick { dt ->
        var redraw = false
        if (!st.inited && st.w > 2f) {
            st.kx = homeX(); st.ky = homeY(); st.inited = true; redraw = true
        }
        when (st.phase) {
            0 -> if (!st.dragging && st.inited) {
                val k = min(1f, dt * 10f)
                val nx = st.kx + (homeX() - st.kx) * k
                val ny = st.ky + (homeY() - st.ky) * k
                if (abs(nx - st.kx) + abs(ny - st.ky) > 0.2f) { st.kx = nx; st.ky = ny; redraw = true }
            }
            1 -> {
                st.turn = min(1f, st.turn + dt / 0.38f)
                redraw = true
                if (st.turn >= 1f) {
                    st.phase = 2
                    st.opens++
                    ctx.thump()
                    ctx.reward(BbElement.KEYLOCK)
                    st.particles.burst(holeX(), st.h * 0.34f, ctx.n(16), 280f * dens, 1.0f, 8f * dens, BB_CONFETTI)
                }
            }
            2 -> if (st.lid < 1f) { st.lid = min(1f, st.lid + dt / 0.45f); redraw = true }
        }
        if (st.particles.update(dt, 500f * dens)) redraw = true
        redraw
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { st.w = it.width.toFloat(); st.h = it.height.toFloat() }
            .bbGesture(
                onDown = { p ->
                    ctx.touch()
                    if (st.phase == 2) return@bbGesture
                    val slop = if (ctx.toddler) 1.5f else 1.2f
                    val cxk = st.kx
                    val cyk = st.ky - keyLen() / 2f
                    if (st.phase == 0 && (p - Offset(cxk, cyk)).getDistance() < 56f * dens * slop) {
                        st.dragging = true
                        st.grabDx = st.kx - p.x
                        st.grabDy = st.ky - p.y
                        ctx.buzz()
                    }
                },
                onMove = { p, _ ->
                    if (st.dragging && st.phase == 0) {
                        st.kx = p.x + st.grabDx
                        st.ky = p.y + st.grabDy
                        val tipX = st.kx
                        val tipY = st.ky - keyLen()
                        val slop = if (ctx.toddler) 1.6f else 1.2f
                        if ((Offset(tipX, tipY) - Offset(holeX(), holeY())).getDistance() < 30f * dens * slop) {
                            st.dragging = false
                            st.phase = 1
                            st.turn = 0f
                            st.kx = holeX()
                            st.ky = holeY() + keyLen() - 6f * dens
                            ctx.audio.play(BbSound.KEYTURN)
                            ctx.buzz()
                        }
                        tick.bump()
                    }
                },
                onUp = { _, wasTap ->
                    st.dragging = false
                    if (st.phase == 2 && wasTap && st.lid >= 0.99f) {
                        // тап по открытому сундучку — закрыть и начать заново
                        st.phase = 0
                        st.turn = 0f
                        st.lid = 0f
                        st.kx = holeX()
                        st.ky = holeY() + keyLen()
                        ctx.audio.play(BbSound.CLACK, 0.85f)
                        tick.bump()
                    }
                }
            )
    ) {
        tick.value
        val w = size.width
        val h = size.height
        val d = dens
        val cx = w / 2f

        // сундучок
        val bx0 = w * 0.12f
        val bx1 = w * 0.88f
        val by0 = h * 0.36f
        val by1 = h * 0.74f
        // приз за крышкой
        val pop = st.lid
        if (pop > 0.05f) {
            val py = by0 - (h * 0.04f + pop * h * 0.10f)
            bbEmoji(PRIZES[(max(st.opens, 1) - 1) % PRIZES.size], cx, py, min(w, h) * 0.30f * (0.4f + 0.6f * pop), paint, pop)
        }
        bbPlate(Offset(bx0, by0), Size(bx1 - bx0, by1 - by0), 12 * d, WoodLight, 3 * d)
        for (i in 1..3) drawLine(WoodDark.copy(alpha = 0.5f), Offset(bx0 + (bx1 - bx0) * i / 4f, by0 + 6 * d), Offset(bx0 + (bx1 - bx0) * i / 4f, by1 - 6 * d), strokeWidth = 3 * d)
        drawRect(Color(0xFF5E6B78), Offset(bx0, by0 + (by1 - by0) * 0.42f), Size(bx1 - bx0, 9 * d))

        // крышка: раскрывается, как книжка, вокруг левой кромки
        val ly0 = h * 0.22f
        val lh = by0 - ly0
        rotate(degrees = -st.lid * 78f, pivot = Offset(bx0, by0)) {
            bbPlate(Offset(bx0, ly0), Size(bx1 - bx0, lh), 14 * d, WoodDark, 3 * d)
            drawRect(Color(0xFF5E6B78), Offset(bx0, ly0 + lh * 0.5f), Size(bx1 - bx0, 8 * d))
        }

        // замок: дужка приподнимается при повороте ключа
        val lx = holeX()
        val ly = holeY()
        val lift = st.turn * 14 * d
        val shackle = Path().apply {
            moveTo(lx - 15 * d, ly - 18 * d)
            lineTo(lx - 15 * d, ly - 36 * d - lift)
            cubicTo(lx - 15 * d, ly - 58 * d - lift, lx + 15 * d, ly - 58 * d - lift, lx + 15 * d, ly - 36 * d - lift)
            lineTo(lx + 15 * d, ly - 18 * d - lift * 0.4f)
        }
        drawPath(shackle, Color(0xFF9AA5B1), style = Stroke(8 * d, cap = StrokeCap.Round))
        bbPlate(Offset(lx - 28 * d, ly - 22 * d), Size(56 * d, 46 * d), 10 * d, Gold, 3 * d)
        drawCircle(ColorInk, 6 * d, Offset(lx, ly - 6 * d))
        drawRoundRect(ColorInk, Offset(lx - 2.5f * d, ly - 6 * d), Size(5 * d, 14 * d), CornerRadius(2 * d, 2 * d))

        // ключ: кольцо внизу, стержень вверх, бородка сбоку; в замке поворачивается на 90°
        val kl = keyLen()
        val angle = if (st.phase == 0) 0f else st.turn * 90f
        val pivot = if (st.phase == 0) Offset(st.kx, st.ky - kl) else Offset(lx, ly)
        rotate(degrees = angle, pivot = pivot) {
            val kx = st.kx
            val ky = st.ky
            drawLine(Color(0xFFD9DEE3), Offset(kx, ky), Offset(kx, ky - kl), strokeWidth = 8 * d, cap = StrokeCap.Round)
            drawRoundRect(Color(0xFFD9DEE3), Offset(kx, ky - kl + 3 * d), Size(14 * d, 8 * d), CornerRadius(2 * d, 2 * d))
            drawRoundRect(Color(0xFFD9DEE3), Offset(kx, ky - kl + 17 * d), Size(10 * d, 8 * d), CornerRadius(2 * d, 2 * d))
            drawCircle(Gold, 15 * d, Offset(kx, ky + 8 * d))
            drawCircle(ColorInk, 15 * d, Offset(kx, ky + 8 * d), style = Stroke(3 * d))
            drawCircle(Color(0xFFFFF3C4), 6 * d, Offset(kx, ky + 8 * d))
        }

        st.particles.draw(this)
    }
}
