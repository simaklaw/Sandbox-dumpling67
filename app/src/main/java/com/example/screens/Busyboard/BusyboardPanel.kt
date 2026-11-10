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
 * Комната 3 «Электрощиток» — идея «рычаг → короткое замыкание → навёл порядок → звезда электрика»,
 * доработанная так, чтобы было и интересно, и не страшно:
 *   • три автомата, у каждого своя лампа-«мордочка»; рубильником пускаешь ток;
 *   • две лампы горят спокойно, а ВСЕ ТРИ сразу — перегрузка: лампы выпучивают глаза и мигают (мягко, ≤ 4 раз в секунду),
 *     стрелка прибора уходит в красное, щиток сыплет звёздными искорками;
 *   • порядок наводится рубильником вверх или любым автоматом — «Фух!» и звезда электрика;
 *   • если ребёнок растерялся, автомат сам выбивает рубильник (4 с у малышей, 7 с у остальных) — хаос никогда не вечен;
 *   • звук хаоса — один «дзынь-бульк» и не больше 3 искорок в секунду, и только первые 2,5 секунды.
 */

private val PANEL_LAMPS = listOf(Color(0xFFEF476F), Color(0xFFFFD166), Color(0xFF06D6A0))
private val PANEL_LAMP_RATE = floatArrayOf(1.0f, 1.26f, 1.498f)
private val PANEL_BODY = Color(0xFF39424E)
private val PANEL_BUS = Color(0xFF4CC9F0)

private class PanelGeo {
    val lampX = FloatArray(3)
    val brkX = FloatArray(3)
    var lampY = 0f
    var lampR = 0f
    var brkW = 0f
    var brkTop = 0f
    var brkBot = 0f
    var busY = 0f
    var pivotX = 0f
    var pivotY = 0f
    var travelR = 0f
    var knobR = 0f
    var gaugeX = 0f
    var gaugeY = 0f
    var gaugeR = 0f

    fun layout(w: Float, h: Float, d: Float) {
        lampY = h * 0.14f
        lampR = min(w * 0.085f, h * 0.07f)
        brkX[0] = w * 0.22f; brkX[1] = w * 0.50f; brkX[2] = w * 0.78f
        lampX[0] = brkX[0]; lampX[1] = brkX[1]; lampX[2] = brkX[2]
        brkW = w * 0.20f
        brkTop = h * 0.30f
        brkBot = h * 0.46f
        busY = h * 0.55f
        pivotX = w * 0.72f
        pivotY = h * 0.77f
        travelR = min(w * 0.19f, h * 0.13f)
        knobR = max(15f * d, travelR * 0.24f)
        gaugeX = w * 0.27f
        gaugeY = h * 0.82f
        gaugeR = min(w * 0.20f, h * 0.13f)
    }

    fun knobAt(t: Float): Offset {
        val a = Math.toRadians((5f + t * 170f).toDouble())
        return Offset(pivotX + travelR * sin(a).toFloat(), pivotY - travelR * cos(a).toFloat())
    }
}

private class PanelState(toddler: Boolean) {
    val panel = BbPanel(3, if (toddler) 4f else 7f)
    val lever = BbSpring(0f, 0f, 260f, 17f)
    val brk = Array(3) { BbSpring(0f, 0f, 380f, 24f) }
    val needle = BbSpring(0f, 0f, 140f, 11f)
    var grab = 0          // 0 — мимо, 1 — рубильник, 2 — автомат
    var crossedMid = false
    var arrivePending = false
    var pulse = 0f
    var chaosT = 0f
    var sparkT = 0f
    var sparkSndT = 0f
    var happy = 0f
    var time = 0f
    var w = 1f
    var h = 1f
    val g = PanelGeo()
    val particles = BbParticles(160)
}

private fun DrawScope.lampFace(c: Offset, r: Float, mood: Int, look: Offset, d: Float) {
    val ex = r * 0.36f
    val ey = c.y - r * 0.10f
    when (mood) {
        0 -> { // спит
            for (s in listOf(-1f, 1f)) {
                drawLine(ColorInk, Offset(c.x + s * ex - r * 0.16f, ey), Offset(c.x + s * ex + r * 0.16f, ey), strokeWidth = 2.5f * d, cap = StrokeCap.Round)
            }
            drawLine(ColorInk, Offset(c.x - r * 0.14f, c.y + r * 0.34f), Offset(c.x + r * 0.14f, c.y + r * 0.34f), strokeWidth = 2.5f * d, cap = StrokeCap.Round)
        }
        1 -> { // довольна
            for (s in listOf(-1f, 1f)) {
                drawCircle(Color.White, r * 0.20f, Offset(c.x + s * ex, ey))
                drawCircle(ColorInk, r * 0.10f, Offset(c.x + s * ex + look.x * r * 0.05f, ey + look.y * r * 0.05f))
            }
            drawArc(ColorInk, 20f, 140f, false, Offset(c.x - r * 0.28f, c.y + r * 0.02f), Size(r * 0.56f, r * 0.42f), style = Stroke(2.5f * d, cap = StrokeCap.Round))
        }
        else -> { // перегрузка: глаза навыкате, рот «о»
            for (s in listOf(-1f, 1f)) {
                drawCircle(Color.White, r * 0.27f, Offset(c.x + s * ex, ey))
                drawCircle(ColorInk, r * 0.11f, Offset(c.x + s * ex + look.x * r * 0.12f, ey + look.y * r * 0.12f))
            }
            drawCircle(ColorInk, r * 0.14f, Offset(c.x, c.y + r * 0.36f))
        }
    }
}

/** Электрощиток: рубильник, три автомата, три лампы-мордочки и прибор. Перегрузка → хаос → «Фух!» → звезда электрика. */
@Composable
internal fun BbPanelBoard(ctx: BbCtx, modifier: Modifier = Modifier) {
    val st = remember { PanelState(ctx.toddler) }
    val dens = LocalDensity.current.density

    fun handle(notes: List<BbPanelNote>) {
        val g = st.g
        for (n in notes) when (n.event) {
            BbPanelEvent.LAMP_ON -> {
                ctx.audio.play(BbSound.LAMP, PANEL_LAMP_RATE[n.lamp.coerceIn(0, 2)])
                st.particles.burst(g.lampX[n.lamp.coerceIn(0, 2)], g.lampY, ctx.n(6), 170f * dens, 0.7f, 6f * dens, listOf(PANEL_LAMPS[n.lamp.coerceIn(0, 2)], Color.White), BB_STAR)
            }
            BbPanelEvent.LAMP_OFF -> Unit
            BbPanelEvent.OVERLOAD -> {
                ctx.audio.play(BbSound.ZAP)
                ctx.thump()
                st.chaosT = 0f
                st.sparkT = 0f
                st.sparkSndT = 0f
            }
            BbPanelEvent.AUTO_TRIP -> {
                st.lever.target = 0f
                st.arrivePending = true
                ctx.audio.play(BbSound.THUNK)
            }
            BbPanelEvent.RESTORED -> {
                ctx.audio.play(BbSound.RELIEF)
                ctx.thump()
                st.happy = 2.5f
                ctx.reward(BbElement.PANEL)
                for (i in 0 until 3) st.particles.burst(g.lampX[i], g.lampY, ctx.n(8), 230f * dens, 1.0f, 7f * dens, BB_CONFETTI, BB_STAR)
            }
        }
    }

    fun toggleBreaker(i: Int) {
        val notes = st.panel.toggleBreaker(i)
        st.brk[i].target = if (st.panel.breakers[i]) 1f else 0f
        ctx.audio.play(BbSound.CLICK, if (st.panel.breakers[i]) 1f else 0.85f)
        ctx.buzz()
        handle(notes)
    }

    val tick = rememberBbTick { dt ->
        st.time += dt
        val p = st.panel
        st.lever.step(dt)
        for (b in st.brk) b.step(dt)

        if (st.arrivePending && abs(st.lever.x - st.lever.target) < 0.04f) {
            st.arrivePending = false
            ctx.audio.play(BbSound.THUNK)
            ctx.thump()
        }
        handle(p.tick(dt))

        if (p.overloaded) {
            st.chaosT += dt
            st.sparkT -= dt
            st.sparkSndT -= dt
            if (st.sparkT <= 0f) {
                st.sparkT = 0.12f + Random.nextFloat() * 0.12f
                val g = st.g
                val sx: Float
                val sy: Float
                when (Random.nextInt(4)) {
                    0 -> { sx = g.lampX[Random.nextInt(3)]; sy = g.lampY }
                    1 -> { sx = g.brkX[Random.nextInt(3)]; sy = g.brkTop }
                    2 -> { sx = g.pivotX; sy = g.pivotY }
                    else -> { sx = st.w * (0.2f + Random.nextFloat() * 0.6f); sy = g.busY }
                }
                st.particles.burst(sx, sy, ctx.n(5), 210f * dens, 0.45f, 2.2f * dens, listOf(Color.White, Color(0xFFFFE066), Color(0xFF66D9FF)), BB_SPARK)
                if (st.chaosT < 2.5f && st.sparkSndT <= 0f) {
                    ctx.audio.play(BbSound.SPARK, 0.9f + Random.nextFloat() * 0.5f)
                    st.sparkSndT = 0.34f
                }
            }
        }
        if (p.power) st.pulse += dt * (if (p.overloaded) 1.7f else 0.75f)
        if (st.happy > 0f) st.happy = max(0f, st.happy - dt)

        st.needle.target = when {
            !p.power -> 0f
            p.overloaded -> 1f
            else -> p.lit / 3f * 0.72f
        }
        st.needle.step(dt)

        val busy = p.power || !st.lever.settled || !st.needle.settled || st.brk.any { !it.settled } || st.happy > 0f
        val parts = st.particles.update(dt, 360f * dens)
        busy || parts
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                st.w = it.width.toFloat(); st.h = it.height.toFloat()
                st.g.layout(st.w, st.h, dens)
            }
            .bbGesture(
                onDown = { pt ->
                    ctx.touch()
                    val g = st.g
                    val slop = if (ctx.toddler) 1.4f else 1.0f
                    val knob = g.knobAt(st.lever.x)
                    if ((pt - knob).getDistance() < g.knobR * 2.4f * slop) {
                        st.grab = 1
                        st.crossedMid = st.lever.x > 0.5f
                        ctx.buzz()
                    } else {
                        val bi = (0 until 3).firstOrNull { i ->
                            abs(pt.x - g.brkX[i]) < g.brkW * 0.7f * slop && pt.y > g.brkTop - 14f * dens && pt.y < g.brkBot + 14f * dens
                        }
                        if (bi != null) {
                            st.grab = 2
                            toggleBreaker(bi)
                            tick.bump()
                        } else st.grab = 0
                    }
                },
                onMove = { pt, _ ->
                    if (st.grab == 1) {
                        val g = st.g
                        var ang = Math.toDegrees(atan2((pt.x - g.pivotX).toDouble(), (g.pivotY - pt.y).toDouble())).toFloat()
                        if (ang < 0f) ang += 360f
                        val a2 = if (ang > 270f) ang - 360f else ang
                        val t = ((a2 - 5f) / 170f).coerceIn(0f, 1f)
                        val mid = t > 0.5f
                        if (mid != st.crossedMid) {
                            st.crossedMid = mid
                            ctx.audio.play(BbSound.CLICK, 0.9f)
                            ctx.buzz()
                        }
                        st.lever.snap(t)
                        tick.bump()
                    }
                },
                onUp = { _, wasTap ->
                    if (st.grab == 1) {
                        val target = if (wasTap) (if (st.lever.x > 0.5f) 0f else 1f) else if (st.lever.x > 0.5f) 1f else 0f
                        st.lever.target = target
                        st.arrivePending = true
                        handle(st.panel.setPower(target > 0.5f))
                        tick.bump()
                    }
                    st.grab = 0
                }
            )
    ) {
        tick.value
        val d = dens
        val w = size.width
        val h = size.height
        val g = st.g
        val p = st.panel
        val overload = p.overloaded
        val t = st.time

        // корпус
        drawRoundRect(PANEL_BODY, Offset.Zero, Size(w, h), CornerRadius(20 * d, 20 * d))
        drawRoundRect(Color(0xFF20242C), Offset(6 * d, 6 * d), Size(w - 12 * d, h - 12 * d), CornerRadius(16 * d, 16 * d), style = Stroke(3 * d))
        for (rx in listOf(18 * d, w - 18 * d)) for (ry in listOf(18 * d, h - 18 * d)) {
            drawCircle(Color(0xFF9AA0AC), 4.5f * d, Offset(rx, ry))
            drawCircle(Color(0xFF20242C), 4.5f * d, Offset(rx, ry), style = Stroke(1.5f * d))
        }

        // провода: шина + короткие отводы к автоматам и лампам
        val busL = g.brkX[0]
        val busR = g.brkX[2]
        val terminal = Offset(g.pivotX, g.pivotY - g.travelR - 14 * d)
        fun wire(x0: Float, y0: Float, x1: Float, y1: Float, core: Color) {
            drawLine(Color(0xFF14171D), Offset(x0, y0), Offset(x1, y1), strokeWidth = 8 * d, cap = StrokeCap.Round)
            drawLine(core.copy(alpha = 0.9f), Offset(x0, y0), Offset(x1, y1), strokeWidth = 4 * d, cap = StrokeCap.Round)
        }
        wire(busL, g.busY, busR, g.busY, PANEL_BUS)
        wire(terminal.x, terminal.y, terminal.x, g.busY, PANEL_BUS)
        wire(g.pivotX, g.busY, busR, g.busY, PANEL_BUS)
        for (i in 0 until 3) {
            wire(g.brkX[i], g.busY, g.brkX[i], g.brkBot, PANEL_BUS)
            wire(g.brkX[i], g.brkTop, g.brkX[i], g.lampY + g.lampR, PANEL_LAMPS[i])
        }

        // бегущие импульсы тока
        if (p.power) {
            fun pulses(x0: Float, y0: Float, x1: Float, y1: Float) {
                for (k in 0 until 2) {
                    val u = ((st.pulse + k * 0.5f) % 1f)
                    val c = Offset(x0 + (x1 - x0) * u, y0 + (y1 - y0) * u)
                    drawCircle(Color(0xFFFFE066).copy(alpha = 0.45f), 7 * d, c)
                    drawCircle(Color.White, 3 * d, c)
                }
            }
            pulses(terminal.x, terminal.y, terminal.x, g.busY)
            pulses(g.pivotX, g.busY, busL, g.busY)
            pulses(g.pivotX, g.busY, busR, g.busY)
            for (i in 0 until 3) if (p.breakers[i]) {
                pulses(g.brkX[i], g.busY, g.brkX[i], g.brkBot)
                pulses(g.brkX[i], g.brkTop, g.brkX[i], g.lampY + g.lampR)
            }
        }

        // автоматы
        for (i in 0 until 3) {
            val on = st.brk[i].x.coerceIn(0f, 1f)
            val x0 = g.brkX[i] - g.brkW / 2f
            drawRoundRect(Color(0xFF2B313A), Offset(x0, g.brkTop), Size(g.brkW, g.brkBot - g.brkTop), CornerRadius(8 * d, 8 * d))
            drawRoundRect(ColorInk, Offset(x0, g.brkTop), Size(g.brkW, g.brkBot - g.brkTop), CornerRadius(8 * d, 8 * d), style = Stroke(2.5f * d))
            drawRoundRect(PANEL_LAMPS[i], Offset(x0 + 5 * d, g.brkTop + 5 * d), Size(g.brkW - 10 * d, 6 * d), CornerRadius(3 * d, 3 * d))
            val slotTop = g.brkTop + 16 * d
            val slotBot = g.brkBot - 8 * d
            val hy = slotBot - (slotBot - slotTop - 16 * d) * on - 8 * d
            drawRoundRect(Color(0xFF14171D), Offset(g.brkX[i] - 7 * d, slotTop), Size(14 * d, slotBot - slotTop), CornerRadius(7 * d, 7 * d))
            drawRoundRect(if (on > 0.5f) PANEL_LAMPS[i] else Color(0xFFB8C0C8), Offset(g.brkX[i] - 11 * d, hy - 8 * d), Size(22 * d, 16 * d), CornerRadius(6 * d, 6 * d))
            drawRoundRect(ColorInk, Offset(g.brkX[i] - 11 * d, hy - 8 * d), Size(22 * d, 16 * d), CornerRadius(6 * d, 6 * d), style = Stroke(2 * d))
        }

        // лампы-мордочки
        for (i in 0 until 3) {
            var shake = Offset.Zero
            var bright = 1f
            val lit = p.lampOn(i)
            if (overload && lit && !ctx.toddler) { // малышам — без дрожания и мерцания, только «большие глаза»
                shake = Offset(sin(t * 38f + i * 2f) * 2.2f * d, cos(t * 33f + i) * 1.6f * d)
                bright = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * 2f * PI.toFloat() * 3.5f + i * 2.1f)) // ≈3,5 Гц, не стробоскоп
            }
            val c = Offset(g.lampX[i] + shake.x, g.lampY + shake.y)
            val r = g.lampR
            if (lit) {
                drawCircle(Brush.radialGradient(listOf(PANEL_LAMPS[i].copy(alpha = 0.75f * bright), Color.Transparent), center = c, radius = r * 2.6f), r * 2.6f, c)
            }
            drawRoundRect(Color(0xFF20242C), Offset(c.x - r * 0.55f, c.y + r * 0.78f), Size(r * 1.1f, r * 0.55f), CornerRadius(4 * d, 4 * d))
            val glass = if (lit) PANEL_LAMPS[i].copy(alpha = 0.55f + 0.45f * bright) else PANEL_LAMPS[i].copy(alpha = 0.22f)
            drawCircle(Color(0xFFDDE3E8).copy(alpha = 0.35f), r, c)
            drawCircle(glass, r, c)
            drawCircle(ColorInk, r, c, style = Stroke(3 * d))
            drawCircle(Color.White.copy(alpha = if (lit) 0.55f else 0.25f), r * 0.16f, Offset(c.x - r * 0.45f, c.y - r * 0.5f))
            val mood = when {
                overload && lit -> 2
                lit || st.happy > 0f -> 1
                else -> 0
            }
            lampFace(c, r, mood, Offset(sin(t * 9f + i), cos(t * 7f + i * 2f)), d)
        }

        // прибор: стрелка нагрузки
        run {
            val c = Offset(g.gaugeX, g.gaugeY)
            val r = g.gaugeR
            drawCircle(Color(0xFFF4F1EA), r + 6 * d, c)
            drawCircle(ColorInk, r + 6 * d, c, style = Stroke(3 * d))
            fun zone(v0: Float, v1: Float, col: Color) {
                drawArc(col, 180f + 180f * v0, 180f * (v1 - v0), false, Offset(c.x - r * 0.82f, c.y - r * 0.82f), Size(r * 1.64f, r * 1.64f), style = Stroke(7 * d))
            }
            zone(0f, 0.5f, Color(0xFF06D6A0)); zone(0.5f, 0.78f, Color(0xFFFFD166)); zone(0.78f, 1f, Color(0xFFEF476F))
            val v = st.needle.x.coerceIn(0f, 1f) + (if (overload) sin(t * 45f) * 0.015f else 0f)
            val a = ((180f + 180f * v.coerceIn(0f, 1f)) * PI / 180.0).toFloat()
            drawLine(ColorInk, c, Offset(c.x + cos(a) * r * 0.86f, c.y + sin(a) * r * 0.86f), strokeWidth = 3.5f * d, cap = StrokeCap.Round)
            drawCircle(ColorInk, 5 * d, c)
        }

        // рубильник
        run {
            val arcTL = Offset(g.pivotX - g.travelR, g.pivotY - g.travelR)
            val arcSize = Size(g.travelR * 2, g.travelR * 2)
            drawArc(Color(0xFF14171D), -85f, 170f, false, arcTL, arcSize, style = Stroke(9 * d, cap = StrokeCap.Round))
            val zr = g.travelR + 12 * d
            val aUp = Math.toRadians(5.0)
            val aDn = Math.toRadians(175.0)
            drawCircle(Color(0xFF06D6A0), 4 * d, Offset(g.pivotX + zr * sin(aUp).toFloat(), g.pivotY - zr * cos(aUp).toFloat()))
            drawCircle(Color(0xFFEF476F), 4 * d, Offset(g.pivotX + zr * sin(aDn).toFloat(), g.pivotY - zr * cos(aDn).toFloat()))
            drawRoundRect(Color(0xFF4A505C), Offset(g.pivotX - 16 * d, g.pivotY - 10 * d), Size(32 * d, 46 * d), CornerRadius(5 * d, 5 * d))
            drawRoundRect(Color(0xFF14171D), Offset(g.pivotX - 16 * d, g.pivotY - 10 * d), Size(32 * d, 46 * d), CornerRadius(5 * d, 5 * d), style = Stroke(2 * d))
            val k = g.knobAt(st.lever.x.coerceIn(-0.05f, 1.05f))
            drawLine(Color(0xFF14171D), Offset(g.pivotX, g.pivotY), k, strokeWidth = 14 * d, cap = StrokeCap.Round)
            drawLine(Color(0xFF8A94A6), Offset(g.pivotX, g.pivotY), k, strokeWidth = 8 * d, cap = StrokeCap.Round)
            if (overload) drawCircle(Color(0xFFFF9E45).copy(alpha = 0.35f + 0.2f * sin(t * 12f)), g.knobR * 1.7f, k)
            drawCircle(Color(0xFFEF476F), g.knobR, k)
            drawCircle(Color(0xFF7A1F33), g.knobR, k, style = Stroke(2.5f * d))
            drawCircle(Color.White.copy(alpha = 0.45f), g.knobR * 0.28f, Offset(k.x - g.knobR * 0.35f, k.y - g.knobR * 0.35f))
            drawCircle(Color(0xFF14171D), 6 * d, Offset(g.pivotX, g.pivotY))
        }

        // предупреждающая полоса
        run {
            val sh = 14 * d
            val y0 = h - 12 * d - sh
            drawRoundRect(Color(0xFFFFD166), Offset(12 * d, y0), Size(w - 24 * d, sh), CornerRadius(4 * d, 4 * d))
            var x = 12 * d
            val step = 20 * d
            while (x < w - 12 * d - step * 0.5f) {
                val stripe = Path().apply {
                    moveTo(x, y0); lineTo(x + step * 0.5f, y0)
                    lineTo(x + step * 0.5f - sh * 0.7f, y0 + sh); lineTo(x - sh * 0.7f, y0 + sh); close()
                }
                drawPath(stripe, Color(0xFF20242C))
                x += step
            }
        }

        st.particles.draw(this)
    }
}
