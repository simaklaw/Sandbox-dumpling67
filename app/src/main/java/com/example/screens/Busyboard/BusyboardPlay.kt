package com.example.screens.Busyboard

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.example.shared.ColorInk
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * Комната 2 «Звуки и игры»: шестерёнки, счёты, ксилофон, колесо.
 * Звук здесь — только «по событию»: щелчок зубца, стук косточки, нота, тик сектора.
 */

private val PENTA = floatArrayOf(1.0f, 1.122f, 1.26f, 1.498f, 1.682f)

private fun angleDiff(a: Float, b: Float): Float {
    var d = b - a
    while (d > 180f) d -= 360f
    while (d < -180f) d += 360f
    return d
}

// ═════════════════════════════ 5. ШЕСТЕРЁНКИ ═════════════════════════════

private val GEAR_BODY = listOf(Color(0xFFFF8A65), Color(0xFF4FC3F7), Color(0xFF81C784))
private val GEAR_DONE = Color(0xFFFFD54F)

private class GearsState {
    val train = BbGearTrain(intArrayOf(12, 8, 6))
    var dragging = false
    var lastAng = 0f
    var lastMs = 0L
    var vel = 0f
    var done = false
    var glow = 0f
    var w = 1f
    var h = 1f
    val particles = BbParticles(48)
}

private fun DrawScope.bbGear(c: Offset, r: Float, teeth: Int, angleDeg: Float, body: Color, d: Float) {
    val pitch = 360f / teeth
    val inner = r * 0.82f
    val path = Path()
    var first = true
    for (i in 0 until teeth) {
        val a = angleDeg + i * pitch
        val pts = floatArrayOf(a - 0.30f * pitch, inner, a - 0.16f * pitch, r, a + 0.16f * pitch, r, a + 0.30f * pitch, inner)
        var k = 0
        while (k < pts.size) {
            val rad = ((pts[k] - 90f) * PI / 180.0).toFloat()
            val px = c.x + cos(rad) * pts[k + 1]
            val py = c.y + sin(rad) * pts[k + 1]
            if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
            k += 2
        }
    }
    path.close()
    drawPath(path, body)
    drawPath(path, ColorInk, style = Stroke(3 * d))
    // отверстия-«спицы» и втулка вращаются вместе с шестернёй
    for (i in 0 until 4) {
        val rad = ((angleDeg + i * 90f - 90f) * PI / 180.0).toFloat()
        drawCircle(Color.Black.copy(alpha = 0.18f), r * 0.11f, Offset(c.x + cos(rad) * r * 0.55f, c.y + sin(rad) * r * 0.55f))
    }
    drawCircle(Color.White.copy(alpha = 0.85f), r * 0.24f, c)
    drawCircle(ColorInk, r * 0.24f, c, style = Stroke(2.5f * d))
    val rad0 = ((angleDeg - 90f) * PI / 180.0).toFloat()
    drawLine(ColorInk, c, Offset(c.x + cos(rad0) * r * 0.2f, c.y + sin(rad0) * r * 0.2f), strokeWidth = 2.5f * d, cap = StrokeCap.Round)
}

/** Шестерёнки: крутишь большую пальцем (или тапаешь — она раскрутится сама); маленькие вращаются навстречу и щёлкают. */
@Composable
internal fun BbGears(ctx: BbCtx, modifier: Modifier = Modifier) {
    val st = remember { GearsState() }
    val dens = LocalDensity.current.density
    val goalTurns = if (ctx.toddler) 2f else 3f

    fun center() = Offset(st.w * 0.5f, st.h * 0.34f)

    fun checkDone() {
        if (!st.done && st.train.progress(goalTurns) >= 1f) {
            st.done = true
            ctx.reward(BbElement.GEARS)
            val c = center()
            st.particles.burst(c.x, c.y, ctx.n(16), 280f * dens, 1.0f, 8f * dens, BB_CONFETTI)
        }
    }

    val tick = rememberBbTick { dt ->
        var redraw = false
        if (!st.dragging && st.train.omega != 0f) {
            val clicks = st.train.coast(dt)
            if (clicks > 0) ctx.audio.play(BbSound.TICK, 0.9f + Random.nextFloat() * 0.2f)
            checkDone()
            redraw = true
        }
        val g = if (st.done) 1f else 0f
        if (abs(st.glow - g) > 0.01f) { st.glow += (g - st.glow) * min(1f, dt * 5f); redraw = true }
        if (st.particles.update(dt, 480f * dens)) redraw = true
        redraw
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { st.w = it.width.toFloat(); st.h = it.height.toFloat() }
            .bbGesture(
                onDown = { p ->
                    ctx.touch()
                    st.dragging = true
                    st.train.omega = 0f
                    st.vel = 0f
                    val c = center()
                    st.lastAng = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat()
                    st.lastMs = SystemClock.uptimeMillis()
                },
                onMove = { p, _ ->
                    val c = center()
                    val ang = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat()
                    if ((p - c).getDistance() > 12f * dens) {
                        // скачок угла у самой втулки не должен «прокручивать» шестерёнку
                        val dA = angleDiff(st.lastAng, ang).coerceIn(-60f, 60f)
                        st.lastAng = ang
                        val now = SystemClock.uptimeMillis()
                        val dtMs = max(1L, now - st.lastMs)
                        st.lastMs = now
                        st.vel = st.vel * 0.6f + (dA / (dtMs / 1000f)) * 0.4f
                        val clicks = st.train.advance(dA)
                        if (clicks > 0) {
                            ctx.audio.play(BbSound.TICK, 0.95f + Random.nextFloat() * 0.12f)
                            ctx.buzz()
                        }
                        checkDone()
                        tick.bump()
                    } else {
                        st.lastAng = ang // палец у втулки: запоминаем угол, чтобы на выходе не было скачка
                    }
                },
                onUp = { _, wasTap ->
                    st.dragging = false
                    if (wasTap) {
                        st.train.omega = if (ctx.toddler) 420f else 520f
                    } else if (SystemClock.uptimeMillis() - st.lastMs < 120L) {
                        st.train.omega = st.vel.coerceIn(-900f, 900f)
                    }
                    tick.bump()
                }
            )
    ) {
        tick.value
        val w = size.width
        val h = size.height
        val d = dens
        val c0 = center()
        val r0 = min(w * 0.24f, h * 0.19f)
        val r1 = r0 * st.train.teethOf(1) / st.train.teethOf(0)
        val r2 = r0 * st.train.teethOf(2) / st.train.teethOf(0)
        val c1 = Offset(c0.x - 0.78f * (r0 + r1) * 0.97f, c0.y + 0.63f * (r0 + r1) * 0.97f)
        val c2 = Offset(c0.x + 0.80f * (r0 + r2) * 0.97f, c0.y + 0.60f * (r0 + r2) * 0.97f)

        // ободок прогресса вокруг большой шестерни
        val ringR = r0 + 11 * d
        drawCircle(Color.Black.copy(alpha = 0.10f), ringR, c0, style = Stroke(7 * d))
        drawArc(
            color = GEAR_DONE, startAngle = -90f, sweepAngle = 360f * st.train.progress(goalTurns), useCenter = false,
            topLeft = Offset(c0.x - ringR, c0.y - ringR), size = Size(ringR * 2, ringR * 2),
            style = Stroke(7 * d, cap = StrokeCap.Round)
        )

        val tint = { base: Color -> if (st.glow > 0.01f) androidx.compose.ui.graphics.lerp(base, GEAR_DONE, 0.55f * st.glow) else base }
        bbGear(c1, r1, st.train.teethOf(1), st.train.angleOf(1), tint(GEAR_BODY[1]), d)
        bbGear(c2, r2, st.train.teethOf(2), st.train.angleOf(2), tint(GEAR_BODY[2]), d)
        bbGear(c0, r0, st.train.teethOf(0), st.train.angleOf(0), tint(GEAR_BODY[0]), d)

        // шкала оборотов внизу
        val bx0 = w * 0.14f
        val bx1 = w * 0.86f
        val by = h * 0.91f
        drawLine(Color.Black.copy(alpha = 0.15f), Offset(bx0, by), Offset(bx1, by), strokeWidth = 9 * d, cap = StrokeCap.Round)
        val pr = st.train.progress(goalTurns)
        if (pr > 0.01f) drawLine(GEAR_DONE, Offset(bx0, by), Offset(bx0 + (bx1 - bx0) * pr, by), strokeWidth = 9 * d, cap = StrokeCap.Round)

        st.particles.draw(this)
    }
}

// ═════════════════════════════ 6. СЧЁТЫ ═════════════════════════════

private val ABACUS_COLORS = listOf(Color(0xFFEF5350), Color(0xFFFFCA28), Color(0xFF66BB6A), Color(0xFF42A5F5))

private class AbacusState(val rods: Int, val beads: Int) {
    val gap: Float
    val pos: Array<FloatArray>
    var grabRod = -1
    var grabBead = -1
    var grabDy = 0f
    var lastPushed = 0
    var atEnd = false
    var done = false
    var w = 1f
    var h = 1f
    val particles = BbParticles(48)

    init {
        // косточки (диаметр D) вплотную: шаг между центрами = D / длина хода
        gap = 1f / (beads + 1.6f)
        pos = Array(rods) { FloatArray(beads) { i -> i * gap } }
    }
}

/** Счёты: вертикальные прутья, косточки скользят и расталкивают соседей; «тук» разной высоты. Тап по косточке — она едет сама. */
@Composable
internal fun BbAbacus(ctx: BbCtx, modifier: Modifier = Modifier) {
    val st = remember { if (ctx.toddler) AbacusState(3, 4) else AbacusState(4, 5) }
    val dens = LocalDensity.current.density

    // геометрия: x центров прутьев, y начала хода, длина хода, диаметр косточки
    fun colW() = st.w * 0.84f / st.rods
    fun rodX(r: Int) = st.w * 0.08f + colW() * (r + 0.5f)
    fun diam() = min(colW() * 0.82f, st.h * 0.74f / (st.beads + 1.6f))
    fun trackY0() = st.h * 0.10f + diam() / 2f
    fun trackLen() = st.h * 0.80f - diam() - 2f

    fun beadY(r: Int, i: Int) = trackY0() + st.pos[r][i] * trackLen()

    fun checkDone() {
        if (st.done) return
        if ((0 until st.rods).all { bbAbacusRowRight(st.pos[it], st.gap) }) {
            st.done = true
            ctx.reward(BbElement.ABACUS)
            st.particles.burst(st.w * 0.5f, st.h * 0.45f, ctx.n(18), 300f * dens, 1.1f, 8f * dens, BB_CONFETTI)
        }
    }

    fun knock(r: Int, i: Int) {
        ctx.audio.play(BbSound.BEAD, PENTA[(r * 2 + i) % PENTA.size])
        ctx.buzz()
    }

    val tick = rememberBbTick { dt -> st.particles.update(dt, 480f * dens) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { st.w = it.width.toFloat(); st.h = it.height.toFloat() }
            .bbGesture(
                onDown = { p ->
                    ctx.touch()
                    st.grabRod = -1
                    st.grabBead = -1
                    st.lastPushed = 0
                    st.atEnd = false
                    var best = Float.MAX_VALUE
                    val slop = if (ctx.toddler) 1.25f else 1.0f
                    for (r in 0 until st.rods) {
                        if (abs(p.x - rodX(r)) > colW() * 0.55f) continue
                        for (i in 0 until st.beads) {
                            val dist = abs(p.y - beadY(r, i))
                            if (dist < diam() * 0.75f * slop && dist < best) {
                                best = dist; st.grabRod = r; st.grabBead = i; st.grabDy = beadY(r, i) - p.y
                            }
                        }
                    }
                },
                onMove = { p, _ ->
                    val r = st.grabRod
                    val i = st.grabBead
                    if (r >= 0) {
                        val target = (p.y + st.grabDy - trackY0()) / trackLen()
                        val pushed = bbAbacusDrag(st.pos[r], i, target, st.gap)
                        val lo = i * st.gap
                        val hi = 1f - (st.beads - 1 - i) * st.gap
                        val end = st.pos[r][i] <= lo + 0.001f || st.pos[r][i] >= hi - 0.001f
                        if ((pushed > 0 && st.lastPushed == 0) || (end && !st.atEnd)) knock(r, i)
                        st.lastPushed = pushed
                        st.atEnd = end
                        checkDone()
                        tick.bump()
                    }
                },
                onUp = { _, wasTap ->
                    val r = st.grabRod
                    val i = st.grabBead
                    if (wasTap && r >= 0) {
                        // тап: косточка (и те, что мешают) уезжают к ближнему концу
                        bbAbacusDrag(st.pos[r], i, if (st.pos[r][i] > 0.5f) 0f else 1f, st.gap)
                        knock(r, i)
                        checkDone()
                        tick.bump()
                    }
                    st.grabRod = -1
                    st.grabBead = -1
                }
            )
    ) {
        tick.value
        val d = dens
        val w = size.width
        val h = size.height
        val dm = diam()
        val y0 = h * 0.10f
        val y1 = h * 0.90f

        // деревянная рамка
        drawRoundRect(Color(0xFFC98B4F), Offset(w * 0.03f, y0 - 6 * d), Size(w * 0.94f, y1 - y0 + 12 * d), CornerRadius(14 * d, 14 * d))
        drawRoundRect(ColorInk, Offset(w * 0.03f, y0 - 6 * d), Size(w * 0.94f, y1 - y0 + 12 * d), CornerRadius(14 * d, 14 * d), style = Stroke(3 * d))
        drawRoundRect(Color(0xFFF3E1BD), Offset(w * 0.07f, y0), Size(w * 0.86f, y1 - y0), CornerRadius(8 * d, 8 * d))

        for (r in 0 until st.rods) {
            val x = rodX(r)
            drawLine(Color(0xFF8D6E63), Offset(x, y0 + 4 * d), Offset(x, y1 - 4 * d), strokeWidth = 5 * d, cap = StrokeCap.Round)
            val col = ABACUS_COLORS[r % ABACUS_COLORS.size]
            for (i in 0 until st.beads) {
                val c = Offset(x, beadY(r, i))
                drawCircle(col, dm / 2f, c)
                drawCircle(ColorInk, dm / 2f, c, style = Stroke(2.5f * d))
                drawCircle(Color.White.copy(alpha = 0.55f), dm * 0.12f, Offset(c.x - dm * 0.16f, c.y - dm * 0.16f))
            }
        }
        st.particles.draw(this)
    }
}

// ═════════════════════════════ 7. КСИЛОФОН ═════════════════════════════

private val XYLO_COLORS = listOf(
    Color(0xFFEF5350), Color(0xFFFF8A65), Color(0xFFFFCA28), Color(0xFF9CCC65),
    Color(0xFF26C6DA), Color(0xFF42A5F5), Color(0xFF7E57C2), Color(0xFFEC407A)
)

private class XyloState {
    val flash = FloatArray(8)
    var played = 0
    var w = 1f
    var h = 1f
    val particles = BbParticles(60)
}

/** Ксилофон: 8 пластинок до-мажора (малышам 5 крупных); можно играть сразу несколькими пальцами и водить пальцем по пластинкам (глиссандо). */
@Composable
internal fun BbXylophone(ctx: BbCtx, modifier: Modifier = Modifier) {
    val st = remember { XyloState() }
    val dens = LocalDensity.current.density

    // малышам — 5 крупных пластинок (до, ми, соль, ля, до): попасть проще, и любая комбинация звучит красиво
    val bars = if (ctx.toddler) 5 else 8
    val notes = if (ctx.toddler) intArrayOf(0, 2, 4, 5, 7) else intArrayOf(0, 1, 2, 3, 4, 5, 6, 7)

    // верхняя пластинка (row = 0) — самая короткая и высокая нота, нижняя — длинная и низкая
    fun rowAt(y: Float): Int {
        if (y < st.h * 0.04f || y > st.h * 0.96f) return -1
        return ((y - st.h * 0.04f) / (st.h * 0.92f / bars)).toInt().coerceIn(0, bars - 1)
    }
    fun noteOf(row: Int) = notes[bars - 1 - row]
    fun spread(row: Int) = if (bars > 1) row / (bars - 1f) else 0f

    val tick = rememberBbTick { dt ->
        var redraw = false
        for (i in 0 until 8) if (st.flash[i] > 0f) { st.flash[i] = max(0f, st.flash[i] - dt * 4f); redraw = true }
        if (st.particles.update(dt, 420f * dens)) redraw = true
        redraw
    }

    fun hit(row: Int) {
        val note = noteOf(row)
        ctx.audio.xylo(note)
        ctx.buzz()
        st.flash[row] = 1f
        st.played = st.played or (1 shl note)
        val rowH = st.h * 0.92f / bars
        val width = st.w * (0.62f + 0.30f * spread(row))
        val y = st.h * 0.04f + rowH * (row + 0.5f)
        st.particles.burst(st.w * 0.5f + (Random.nextFloat() - 0.5f) * width * 0.8f, y, ctx.n(5), 180f * dens, 0.6f, 6f * dens, listOf(XYLO_COLORS[(spread(row) * 7f + 0.5f).toInt()], Color.White), BB_STAR)
        if (bbXyloDone(st.played)) ctx.reward(BbElement.XYLOPHONE)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { st.w = it.width.toFloat(); st.h = it.height.toFloat() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val last = HashMap<Long, Int>()
                    val first = awaitFirstDown(requireUnconsumed = false)
                    ctx.touch()
                    val row0 = rowAt(first.position.y)
                    last[first.id.value] = row0
                    if (row0 >= 0) hit(row0)
                    tick.bump()
                    do {
                        val ev = awaitPointerEvent()
                        for (ch in ev.changes) {
                            val id = ch.id.value
                            if (ch.pressed) {
                                val row = rowAt(ch.position.y)
                                if (last[id] != row) {
                                    last[id] = row
                                    if (row >= 0) hit(row)
                                }
                                ch.consume()
                            } else {
                                last.remove(id)
                                ch.consume()
                            }
                        }
                        tick.bump()
                    } while (ev.changes.any { it.pressed })
                }
            }
    ) {
        tick.value
        val d = dens
        val w = size.width
        val h = size.height
        val rowH = h * 0.92f / bars
        // рейки, на которых лежат пластинки
        drawLine(Color(0xFF8D5A2B), Offset(w * 0.30f, h * 0.02f), Offset(w * 0.30f, h * 0.98f), strokeWidth = 7 * d, cap = StrokeCap.Round)
        drawLine(Color(0xFF8D5A2B), Offset(w * 0.70f, h * 0.02f), Offset(w * 0.70f, h * 0.98f), strokeWidth = 7 * d, cap = StrokeCap.Round)
        for (row in 0 until bars) {
            val bw = w * (0.62f + 0.30f * spread(row))
            val f = st.flash[row]
            val top = h * 0.04f + rowH * row + 3 * d + f * 3 * d
            val x0 = (w - bw) / 2f
            val base = XYLO_COLORS[(spread(row) * 7f + 0.5f).toInt()]
            drawRoundRect(base, Offset(x0, top), Size(bw, rowH - 6 * d), CornerRadius(10 * d, 10 * d))
            drawRoundRect(Color.White.copy(alpha = 0.30f + 0.5f * f), Offset(x0 + 6 * d, top + 3 * d), Size(bw - 12 * d, (rowH - 6 * d) * 0.28f), CornerRadius(6 * d, 6 * d))
            drawRoundRect(ColorInk, Offset(x0, top), Size(bw, rowH - 6 * d), CornerRadius(10 * d, 10 * d), style = Stroke(2.5f * d))
            drawCircle(ColorInk.copy(alpha = 0.55f), 3 * d, Offset(x0 + 12 * d, top + (rowH - 6 * d) / 2f))
            drawCircle(ColorInk.copy(alpha = 0.55f), 3 * d, Offset(x0 + bw - 12 * d, top + (rowH - 6 * d) / 2f))
        }
        st.particles.draw(this)
    }
}

// ═════════════════════════════ 8. КОЛЕСО ═════════════════════════════

private val WHEEL_EMOJI = listOf("🐶", "🍎", "⭐", "🚗", "🌈", "🐱")
private val WHEEL_COLORS = listOf(
    Color(0xFFFF8A65), Color(0xFFEF5350), Color(0xFFFFEE58), Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFCE93D8)
)

private class WheelState {
    var rot = 0f
    var omega = 0f
    var dragging = false
    var pending = false
    var lastAng = 0f
    var lastMs = 0L
    var vel = 0f
    var spins = 0
    var flap = 0f
    var shine = 0f
    var spun = 0f
    var w = 1f
    var h = 1f
    val particles = BbParticles(48)
}

/** Колесо: крутанул — трещотка тикает по секторам, колесо честно тормозит и «выбирает» зверька или сюрприз. */
@Composable
internal fun BbWheel(ctx: BbCtx, modifier: Modifier = Modifier) {
    val st = remember { WheelState() }
    val paint = remember { bbEmojiPaint() }
    val dens = LocalDensity.current.density
    val needed = bbSpinsNeeded(ctx.toddler)

    fun center() = Offset(st.w * 0.5f, st.h * 0.55f)
    fun radius() = min(st.w * 0.44f, st.h * 0.40f)

    fun land() {
        st.pending = false
        val big = st.spun >= 120f // лёгкое «подталкивание» — ещё не вращение
        st.spun = 0f
        if (!big) return
        val idx = bbWheelIndex(st.rot, 6)
        st.spins++
        st.shine = 1f
        when (idx) {
            0 -> ctx.audio.animal("dog")
            1 -> ctx.audio.play(BbSound.POP)
            2 -> ctx.audio.play(BbSound.LAMP, 1.26f)
            3 -> ctx.audio.play(BbSound.WHOOSH)
            4 -> ctx.audio.play(BbSound.LAMP, 1.5f)
            else -> ctx.audio.animal("cat")
        }
        ctx.thump()
        val c = center()
        st.particles.burst(c.x, c.y - radius() * 0.9f, ctx.n(12), 240f * dens, 0.9f, 8f * dens, BB_CONFETTI)
        if (st.spins >= needed) ctx.reward(BbElement.WHEEL)
    }

    val tick = rememberBbTick { dt ->
        var redraw = false
        if (!st.dragging && st.omega != 0f) {
            val prev = st.rot
            st.omega = bbWheelDecay(st.omega, dt)
            st.rot += st.omega * dt
            st.spun += abs(st.omega * dt)
            if (bbCrossings(prev, st.rot, 60f) > 0) {
                ctx.audio.play(BbSound.TICK, 0.7f + min(0.5f, abs(st.omega) / 1500f))
                st.flap = 1f
            }
            if (st.omega == 0f && st.pending) land()
            redraw = true
        }
        if (st.flap > 0f) { st.flap = max(0f, st.flap - dt * 6f); redraw = true }
        if (st.shine > 0f) { st.shine = max(0f, st.shine - dt * 0.8f); redraw = true }
        if (st.particles.update(dt, 480f * dens)) redraw = true
        redraw
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { st.w = it.width.toFloat(); st.h = it.height.toFloat() }
            .bbGesture(
                onDown = { p ->
                    ctx.touch()
                    st.dragging = true
                    st.omega = 0f
                    st.vel = 0f
                    val c = center()
                    st.lastAng = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat()
                    st.lastMs = SystemClock.uptimeMillis()
                },
                onMove = { p, _ ->
                    val c = center()
                    val ang = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat()
                    if ((p - c).getDistance() > 14f * dens) {
                        val dA = angleDiff(st.lastAng, ang).coerceIn(-60f, 60f)
                        st.lastAng = ang
                        val now = SystemClock.uptimeMillis()
                        val dtMs = max(1L, now - st.lastMs)
                        st.lastMs = now
                        st.vel = st.vel * 0.6f + (dA / (dtMs / 1000f)) * 0.4f
                        val prev = st.rot
                        st.rot += dA
                        st.spun += abs(dA)
                        st.pending = true
                        if (bbCrossings(prev, st.rot, 60f) > 0) {
                            ctx.audio.play(BbSound.TICK, 0.75f)
                            st.flap = 1f
                        }
                        tick.bump()
                    } else {
                        st.lastAng = ang
                    }
                },
                onUp = { _, wasTap ->
                    st.dragging = false
                    if (wasTap) {
                        st.omega = (if (ctx.toddler) 520f else 640f) + Random.nextFloat() * 520f
                        st.pending = true
                        ctx.audio.play(BbSound.WHOOSH, 1.1f)
                    } else if (SystemClock.uptimeMillis() - st.lastMs < 120L) {
                        st.omega = st.vel.coerceIn(-1400f, 1400f)
                        if (abs(st.omega) < 40f) st.omega = 0f
                    }
                    if (st.omega == 0f && st.pending) land()
                    tick.bump()
                }
            )
    ) {
        tick.value
        val d = dens
        val c = center()
        val r = radius()

        // подставка
        drawRoundRect(Color(0xFF8D5A2B), Offset(c.x - r * 0.35f, c.y + r * 0.82f), Size(r * 0.7f, r * 0.42f), CornerRadius(8 * d, 8 * d))
        drawCircle(ColorInk, r + 7 * d, c)

        rotate(degrees = st.rot, pivot = c) {
            for (k in 0 until 6) {
                drawArc(
                    color = WHEEL_COLORS[k], startAngle = -90f + k * 60f, sweepAngle = 60f, useCenter = true,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2)
                )
                drawArc(
                    color = ColorInk, startAngle = -90f + k * 60f, sweepAngle = 60f, useCenter = true,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(2.5f * d)
                )
            }
        }
        // эмодзи сектора всегда «головой вверх»
        for (k in 0 until 6) {
            val a = ((st.rot + (k + 0.5f) * 60f) * PI / 180.0).toFloat()
            bbEmoji(WHEEL_EMOJI[k], c.x + sin(a) * r * 0.64f, c.y - cos(a) * r * 0.64f, r * 0.42f, paint)
        }
        // «сияние» выбранного сектора сразу после остановки
        if (st.shine > 0f) {
            drawCircle(Color.White.copy(alpha = 0.55f * st.shine), r * 1.02f, c, style = Stroke(6 * d))
        }
        // втулка
        drawCircle(Color.White, r * 0.17f, c)
        drawCircle(ColorInk, r * 0.17f, c, style = Stroke(3 * d))
        // стрелка-трещотка сверху покачивается при тиках
        rotate(degrees = -st.flap * 20f, pivot = Offset(c.x, c.y - r - 4 * d)) {
            val tri = Path().apply {
                moveTo(c.x - 11 * d, c.y - r - 16 * d)
                lineTo(c.x + 11 * d, c.y - r - 16 * d)
                lineTo(c.x, c.y - r + 8 * d)
                close()
            }
            drawPath(tri, Color(0xFFE53935))
            drawPath(tri, ColorInk, style = Stroke(3 * d))
        }
        st.particles.draw(this)
    }
}
