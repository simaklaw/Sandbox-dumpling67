package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.LocalAgeProfile
import com.example.shared.SoundManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * «Жадная акула» — подводный раннер. Жадная акула гонится за твоим монстриком.
 * Тяни пальцем вверх-вниз — плыви; 🚀 — рывок. Препятствия замедляют, а каждое замедление —
 * шаг к пасти. Вблизи акула кусает: успей уйти по глубине. Акула сжирает всё на пути —
 * заманивай её в стайки рыбок, лови золотые кольца и жемчуг.
 */

private const val SK_GAP_MAX = 340f
private const val SK_SURFACE = 58f

private class SkObs(val kind: Int, val x: Float, val y: Float, val w: Float, val h: Float) {
    val phase = Random.nextFloat() * 6.28f
    var smashed = false
    var zap = 0f
}
private class SkKit(val x: Float, val y: Float) { var taken = false }
private class SkBoom(var x: Float, var y: Float, val size: Float) { var age = 0f }
private class SkImgs(val boom: List<ImageBitmap>, val mine: ImageBitmap?, val kit: ImageBitmap?)
private class SkPearl(val x: Float, val y: Float) { var taken = false }
private class SkRing(val x: Float, val y: Float) { var taken = false }
private class SkSchool(val x: Float, val y: Float, val n: Int) { var eaten = false; val ph = Random.nextFloat() * 6.28f }
private class SkPart(var x: Float, var y: Float, var vx: Float, var vy: Float, val kind: Int, val color: Color, val size: Float, val gravity: Float) {
    var life = 1f
    var decay = 1f
    var rot = Random.nextFloat() * 6.28f
}
private class SkWord(val text: String, var x: Float, var y: Float, val color: Color) { var t = 0f }

private class SkWorld(val toddler: Boolean) {
    var w = 800f
    var h = 400f
    var time = 0f
    var dist = 0f
    var hy = 200f
    var targetY = 200f
    var tilt = 0f
    var dashT = 0f
    var ringT = 0f
    var stun = 0f
    var invuln = 0f
    var energy = 100f
    var gap = SK_GAP_MAX * 0.8f
    var sharkY = 200f
    var state = 0            // 0 погоня, 1 замах пасти, 2 промах, 3 съела
    var telT = 0f
    var stunT = 0f
    var biteY = 0f
    var snapCd = 2f
    var rageT = 0f
    var warnT = 0f
    var rageCd = 9f
    var chewT = 0f
    var eatT = 0f
    var snapped = false
    var over = false
    var pearls = 0
    var combo = 0
    var comboT = 0f
    var heartT = 0f
    var crunchCd = 0f
    var nextX = 700f
    var running = false
    var shake = 0f
    var flash = 0f
    var flashColor = Color.White
    val obs = ArrayList<SkObs>()
    val pearlsL = ArrayList<SkPearl>()
    val rings = ArrayList<SkRing>()
    val kits = ArrayList<SkKit>()
    val booms = ArrayList<SkBoom>()
    var shield = 0f
    val schools = ArrayList<SkSchool>()
    val parts = ArrayList<SkPart>()
    val words = ArrayList<SkWord>()
    var onEvent: (String, Float) -> Unit = { _, _ -> }

    val heroX get() = w * 0.40f
    val floorY get() = h - 48f
    val minY get() = SK_SURFACE + 36f
    val maxY get() = floorY - 36f
    val kvis get() = (heroX * 0.92f) / SK_GAP_MAX

    fun setSize(width: Float, height: Float) {
        w = width; h = height
        if (!running) { hy = h * 0.5f; targetY = hy; sharkY = hy }
    }

    fun reset() {
        dist = 0f; time = 0f
        hy = h * 0.5f; targetY = hy; sharkY = hy; tilt = 0f
        dashT = 0f; ringT = 0f; stun = 0f; invuln = 1.2f; energy = 100f
        gap = SK_GAP_MAX * 0.8f
        state = 0; telT = 0f; stunT = 0f; snapCd = 2.5f
        rageT = 0f; warnT = 0f; rageCd = 10f; chewT = 0f; eatT = 0f; snapped = false
        over = false; pearls = 0; combo = 0; comboT = 0f
        obs.clear(); pearlsL.clear(); rings.clear(); kits.clear(); booms.clear(); shield = 0f; schools.clear(); parts.clear(); words.clear()
        nextX = 650f
        running = true
        onEvent("start", 0f)
    }

    fun dash() {
        if (!running || over || state == 3) return
        if (energy >= 35f && dashT <= 0.15f) {
            energy -= 35f
            dashT = 0.9f
            onEvent("dash", 0f)
            for (i in 0 until 12) parts.add(SkPart(heroX - 20f, hy + (Random.nextFloat() - 0.5f) * 40f, -120f - Random.nextFloat() * 180f, (Random.nextFloat() - 0.5f) * 60f, 0, Color.White, 3f + Random.nextFloat() * 4f, -40f))
        }
    }

    private fun word(text: String, x: Float, y: Float, c: Color) {
        words.add(SkWord(text, x, y, c))
        if (words.size > 5) words.removeAt(0)
    }

    private fun boom(x: Float, y: Float, size: Float) {
        booms.add(SkBoom(x, y, size))
        if (booms.size > 8) booms.removeAt(0)
    }

    private fun hitBy(o: SkObs?) {
        if (invuln > 0f) return
        if (shield > 0f) {
            shield = 0f; invuln = 1.2f
            word("ЩИТ!", heroX, hy - 56f, Color(0xFF80DEEA))
            flash = 0.3f; flashColor = Color(0xFF80DEEA)
            onEvent("ring", 0f)
            if (o != null && o.kind == 4) { o.smashed = true; boom(heroX + 30f, hy, 110f); onEvent("boom", 0f) }
            return
        }
        if (o != null && o.kind == 4) { o.smashed = true; boom(heroX, hy, 170f); onEvent("boom", 0f) }
        stun = 1.1f; invuln = 2.0f; combo = 0
        o?.zap = 0.6f
        shake = 0.8f
        flash = 0.4f; flashColor = Color(0xFFFFEB3B)
        word("АЙ!", heroX, hy - 50f, Color(0xFFFFEB3B))
        for (i in 0 until 10) parts.add(SkPart(heroX, hy, (Random.nextFloat() - 0.5f) * 300f, (Random.nextFloat() - 0.5f) * 300f, 2, Color(0xFFFFF176), 4f, 0f).also { it.decay = 2.2f })
        onEvent("hit", 0f)
    }

    private fun eaten() {
        if (state == 3) return
        state = 3; eatT = 0f; snapped = false
        onEvent("rage", 0f)
    }

    private fun circleRect(cx: Float, cy: Float, r: Float, l: Float, t: Float, rr: Float, b: Float): Boolean {
        val px = cx.coerceIn(l, rr)
        val py = cy.coerceIn(t, b)
        return hypot(cx - px, cy - py) < r
    }

    private fun spawn() {
        while (nextX < dist + w + 400f) {
            val p = Random.nextInt(if (toddler) 5 else 10)
            val lo = minY + 10f
            val hi = maxY - 10f
            when (p) {
                0 -> {
                    val hh = 90f + Random.nextFloat() * (floorY - SK_SURFACE - 240f)
                    val ww = 70f + Random.nextFloat() * 40f
                    obs.add(SkObs(0, nextX, 0f, ww, hh))
                    for (k in 0..4) pearlsL.add(SkPearl(nextX + ww / 2f - 100f + k * 50f, max(lo, floorY - hh - 50f - sin(k / 4f * PI.toFloat()) * 40f)))
                }
                1 -> {
                    val len = 120f + Random.nextFloat() * (floorY - SK_SURFACE - 330f)
                    obs.add(SkObs(1, nextX, 0f, 60f, len))
                    for (k in 0..4) pearlsL.add(SkPearl(nextX + 30f - 100f + k * 50f, min(hi, SK_SURFACE + len + 70f + sin(k / 4f * PI.toFloat()) * 40f)))
                }
                2 -> {
                    val y1 = lo + Random.nextFloat() * (hi - lo)
                    val y2 = lo + Random.nextFloat() * (hi - lo)
                    obs.add(SkObs(2, nextX, y1, 74f, 74f))
                    obs.add(SkObs(2, nextX + 230f, y2, 74f, 74f))
                    for (k in 0..3) pearlsL.add(SkPearl(nextX + 80f + k * 45f, (y1 + y2) / 2f))
                }
                3 -> {
                    val y = lo + Random.nextFloat() * (hi - lo)
                    for (k in 0..6) pearlsL.add(SkPearl(nextX + k * 48f, y + sin(k * 0.9f) * 22f))
                    schools.add(SkSchool(nextX + 120f, lo + Random.nextFloat() * (hi - lo), 6))
                }
                4 -> {
                    val y = lo + 40f + Random.nextFloat() * (hi - lo - 80f)
                    rings.add(SkRing(nextX + 200f, y))
                    for (k in 0..3) pearlsL.add(SkPearl(nextX + k * 45f, y))
                }
                5 -> {
                    val cnt = 2 + Random.nextInt(2)
                    for (k in 0 until cnt) obs.add(SkObs(3, nextX + k * 150f, lo + Random.nextFloat() * (hi - lo), 56f, 56f))
                    schools.add(SkSchool(nextX + 60f, lo + Random.nextFloat() * (hi - lo), 5))
                }
                6 -> {
                    val y2 = SK_SURFACE + 80f + Random.nextFloat() * max(10f, floorY - SK_SURFACE - 400f)
                    val hs = floorY - (y2 + 170f + Random.nextFloat() * 40f)
                    obs.add(SkObs(1, nextX, 0f, 60f, y2 - SK_SURFACE))
                    obs.add(SkObs(0, nextX + 20f, 0f, 90f, max(60f, hs)))
                    for (k in 0..3) pearlsL.add(SkPearl(nextX + 40f, y2 + 60f + k * 0f - 0f).also { })
                }
                8 -> {
                    val y1 = lo + Random.nextFloat() * (hi - lo)
                    obs.add(SkObs(4, nextX, y1, 64f, 64f))
                    obs.add(SkObs(4, nextX + 190f, lo + Random.nextFloat() * (hi - lo), 64f, 64f))
                    for (k in 0..3) pearlsL.add(SkPearl(nextX + 70f + k * 30f, (lo + hi) / 2f))
                }
                9 -> {
                    val y = lo + 30f + Random.nextFloat() * (hi - lo - 60f)
                    kits.add(SkKit(nextX + 120f, y))
                    obs.add(SkObs(4, nextX + 330f, lo + Random.nextFloat() * (hi - lo), 64f, 64f))
                }
                else -> {
                    val y = lo + Random.nextFloat() * (hi - lo)
                    rings.add(SkRing(nextX + 160f, y))
                    obs.add(SkObs(2, nextX + 360f, lo + Random.nextFloat() * (hi - lo), 74f, 74f))
                }
            }
            nextX += 500f + Random.nextFloat() * 260f + (if (toddler) 160f else 0f)
        }
    }

    fun step(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0f, 0.04f)
        time += dt
        shake = max(0f, shake - dt * 3f)
        flash = max(0f, flash - dt * 2.4f)
        stepParticles(dt)
        for (b in booms) { b.age += dt; b.x -= 170f * dt }
        booms.removeAll { it.age >= BOOM_TIME }
        if (!running) { dist += 150f * dt; return }
        if (state == 3) {
            eatT += dt
            sharkY += (hy - sharkY) * min(1f, dt * 8f)
            if (!snapped && eatT >= 0.28f) {
                snapped = true
                onEvent("chomp", 0f)
                shake = 1f; flash = 0.6f; flashColor = Color(0xFFFF5252)
                boom(heroX + 20f, hy, 230f); onEvent("boom", 0f)
                word("ХРУСЬ!", heroX, hy - 60f, Color(0xFFFFEB3B))
                for (i in 0 until 26) parts.add(SkPart(heroX, hy, (Random.nextFloat() - 0.5f) * 520f, (Random.nextFloat() - 0.5f) * 520f, if (i % 2 == 0) 0 else 2, if (i % 3 == 0) Color(0xFFFFEB3B) else Color.White, 4f + Random.nextFloat() * 6f, -30f).also { it.decay = 0.9f })
            }
            if (eatT > 1.7f && !over) { over = true; onEvent("over", 0f) }
            return
        }
        if (over) return

        // — управление глубиной
        val prevY = hy
        hy += (targetY - hy) * min(1f, dt * 8f)
        hy = hy.coerceIn(minY, maxY)
        tilt += (((hy - prevY) / max(dt, 0.001f)) * 0.0016f - tilt) * min(1f, dt * 10f)
        tilt = tilt.coerceIn(-0.5f, 0.5f)

        // — скорости
        stun = max(0f, stun - dt)
        invuln = max(0f, invuln - dt)
        dashT = max(0f, dashT - dt)
        ringT = max(0f, ringT - dt)
        chewT = max(0f, chewT - dt)
        energy = min(100f, energy + 13f * dt)
        var vh = 240f
        if (dashT > 0f) vh += 170f
        if (ringT > 0f) vh += 200f
        if (stun > 0f) vh *= 0.35f
        t_ramp = min(80f, time * 1.2f)
        var vs = 252f + t_ramp
        if (rageT > 0f) vs *= 1.45f
        if (chewT > 0f) vs *= 0.5f
        if (state == 2) vs *= 0.3f
        if (toddler) vs *= 0.72f
        gap += (vh - vs) * dt
        gap = min(gap, SK_GAP_MAX)
        dist += vh * dt

        // — бешенство
        if (warnT > 0f) {
            warnT -= dt
            if (warnT <= 0f) { rageT = 2.6f; flash = 0.4f; flashColor = Color(0xFFFF1744) }
        } else if (rageT > 0f) {
            rageT -= dt
        } else {
            rageCd -= dt
            if (rageCd <= 0f && !toddler) { warnT = 1.3f; rageCd = 12f + Random.nextFloat() * 5f; onEvent("rageWarn", 0f) }
        }
        sharkY += (hy - sharkY) * min(1f, dt * (if (rageT > 0f) 3.2f else 2f))

        // — укус
        when (state) {
            0 -> {
                snapCd -= dt
                if (gap < 150f && snapCd <= 0f) {
                    state = 1; telT = 0.45f; biteY = hy
                    onEvent("warn", 0f)
                }
            }
            1 -> {
                telT -= dt
                sharkY += (biteY - sharkY) * min(1f, dt * 14f)
                if (telT <= 0f) {
                    if (abs(hy - biteY) < 58f && invuln <= 0f || (abs(hy - biteY) < 58f && gap < 40f)) {
                        if (toddler) { state = 2; stunT = 0.7f; gap += 160f; snapCd = 1.5f; onEvent("miss", 0f); word("ПРОМАХ!", heroX - 80f, hy - 50f, Color.White) }
                        else if (shield > 0f) {
                            shield = 0f; invuln = 1.2f; state = 2; stunT = 0.7f; gap += 120f; snapCd = 1.4f
                            onEvent("ring", 0f); word("ЩИТ!", heroX, hy - 56f, Color(0xFF80DEEA))
                        } else eaten()
                    } else {
                        state = 2; stunT = 0.7f; gap += 90f; snapCd = 1.1f
                        onEvent("miss", 0f)
                        word("ПРОМАХ!", heroX - 80f, hy - 50f, Color.White)
                    }
                }
            }
            2 -> {
                stunT -= dt
                if (stunT <= 0f) state = 0
            }
        }
        if (gap <= 0f && state != 3) {
            if (toddler) { gap = 200f; state = 2; stunT = 0.8f; onEvent("miss", 0f) } else eaten()
        }
        if (state == 3) return

        // — сердцебиение рядом с акулой
        heartT -= dt
        if (gap < 150f && heartT <= 0f) { heartT = 0.55f + gap / 400f; onEvent("heart", 1f - gap / 150f) }

        // — столкновения
        val hx = dist + heroX
        val hr = 25f
        for (o in obs) {
            if (o.smashed) continue
            o.zap = max(0f, o.zap - dt)
            var hit = false
            when (o.kind) {
                0 -> hit = circleRect(hx, hy, hr, o.x + o.w * 0.18f, floorY - o.h * 0.92f, o.x + o.w * 0.82f, floorY)
                1 -> hit = circleRect(hx, hy, hr, o.x + o.w / 2f - 6f, SK_SURFACE, o.x + o.w / 2f + 6f, SK_SURFACE + o.h - 18f) ||
                    hypot(hx - (o.x + o.w / 2f), hy - (SK_SURFACE + o.h)) < hr + 28f
                2 -> {
                    val cy = o.y + sin(time * 2f + o.phase) * 40f
                    hit = hypot(hx - o.x, hy - cy) < hr + o.w * 0.42f || hypot(hx - o.x, hy - (cy + o.w * 0.5f)) < hr + o.w * 0.28f
                }
                4 -> {
                    val cy = o.y + sin(time * 1.3f + o.phase) * 10f
                    hit = hypot(hx - o.x, hy - cy) < hr + o.w * 0.46f
                }
                else -> {
                    val cy = o.y + sin(time * 1.6f + o.phase) * 14f
                    hit = hypot(hx - o.x, hy - cy) < hr + o.w * 0.5f
                }
            }
            if (hit) hitBy(o)
        }
        // — жемчуг и кольца
        comboT = max(0f, comboT - dt)
        if (comboT <= 0f) combo = 0
        for (p in pearlsL) {
            if (p.taken) continue
            if (abs(p.x - hx) < 34f && abs(p.y - hy) < 40f) {
                p.taken = true; pearls++; combo++; comboT = 1.2f
                onEvent("pearl", min(combo, 9).toFloat())
                for (i in 0 until 4) parts.add(SkPart(p.x - dist, p.y, (Random.nextFloat() - 0.5f) * 160f, -Random.nextFloat() * 120f, 2, Color.White, 3f, 0f).also { it.decay = 2f })
            }
        }
        for (r in rings) {
            if (r.taken) continue
            if (abs(r.x - hx) < 22f && abs(r.y - hy) < 52f) {
                r.taken = true; ringT = 1.7f
                flash = 0.3f; flashColor = Color(0xFFFFD54F)
                word("ТУРБО!", heroX, hy - 60f, Color(0xFFFFD54F))
                onEvent("ring", 0f)
            }
        }
        for (k in kits) {
            if (k.taken) continue
            if (hypot(k.x - hx, k.y - hy) < 46f) {
                k.taken = true; shield = 1f
                word("ЩИТ!", heroX, hy - 56f, Color(0xFF80DEEA))
                flash = 0.3f; flashColor = Color(0xFF80DEEA)
                onEvent("ring", 0f)
            }
        }
        // — акула сжирает всё на пути
        val sharkWX = dist + heroX - gap
        crunchCd = max(0f, crunchCd - dt)
        for (s in schools) {
            if (s.eaten) continue
            if (s.x < sharkWX + 40f) {
                s.eaten = true
                chewT = 0.8f
                onEvent("gulp", 0f)
                word("ЧАВК!", s.x - dist + 40f, s.y - 30f, Color(0xFFFFAB40))
                for (i in 0 until 12) parts.add(SkPart(s.x - dist, s.y, (Random.nextFloat() - 0.5f) * 260f, (Random.nextFloat() - 0.5f) * 200f, 0, Color(0xFFFFFFFF), 3f + Random.nextFloat() * 4f, -30f))
            }
        }
        for (o in obs) {
            if (o.smashed) continue
            if (o.x + o.w * 0.5f < sharkWX + 20f) {
                o.smashed = true
                if (o.kind == 4) {
                    boom(o.x - dist + o.w / 2f, o.y, 150f); onEvent("boom", 0f)
                    if (state == 0 || state == 1) { state = 2; stunT = 0.9f; gap += 70f; snapCd = 1.2f; word("БАХ!", o.x - dist, o.y - 40f, Color(0xFFFFEB3B)) }
                } else if (crunchCd <= 0f) { crunchCd = 0.12f; onEvent("crunch", 0f); boom(o.x - dist + o.w / 2f, if (o.kind == 0) floorY - o.h / 2f else if (o.kind == 1) SK_SURFACE + o.h / 2f else o.y, 70f) }
                val ox = o.x + o.w / 2f - dist
                val oy = if (o.kind == 0) floorY - o.h / 2f else if (o.kind == 1) SK_SURFACE + o.h / 2f else o.y
                for (i in 0 until 10) parts.add(SkPart(ox, oy, (Random.nextFloat() - 0.2f) * 360f, (Random.nextFloat() - 0.5f) * 300f, 1, Color(0xFF90A4AE), 5f + Random.nextFloat() * 6f, 500f).also { it.decay = 1.1f })
            }
        }
        obs.removeAll { it.smashed || it.x < dist - 500f }
        pearlsL.removeAll { it.taken || it.x < dist - 400f }
        rings.removeAll { it.taken || it.x < dist - 400f }
        kits.removeAll { it.taken || it.x < dist - 400f }
        schools.removeAll { it.eaten || it.x < dist - 500f }
        spawn()

        // пузырьки за героем
        if (Random.nextFloat() < dt * (if (dashT > 0f || ringT > 0f) 50f else 8f)) {
            parts.add(SkPart(heroX - 24f, hy + (Random.nextFloat() - 0.5f) * 30f, -40f - Random.nextFloat() * 60f, -20f - Random.nextFloat() * 30f, 0, Color.White, 2.5f + Random.nextFloat() * 3f, -30f))
        }
    }

    private var t_ramp = 0f

    private fun stepParticles(dt: Float) {
        // фоновые пузырьки
        if (Random.nextFloat() < dt * 3f) parts.add(SkPart(Random.nextFloat() * w, h, (Random.nextFloat() - 0.5f) * 14f, -40f - Random.nextFloat() * 60f, 0, Color.White, 2f + Random.nextFloat() * 5f, 0f).also { it.decay = 0.12f })
        val it = parts.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.vy += p.gravity * dt
            p.x += p.vx * dt - (if (p.kind == 0 && running) 0f else 0f)
            p.y += p.vy * dt
            p.vx *= (1f - dt * 1.2f)
            p.rot += dt * 6f
            p.life -= dt * p.decay
            if (p.life <= 0f || p.y < SK_SURFACE - 4f && p.kind == 0) it.remove()
        }
        for (wd in words) { wd.t += dt * 1.2f; wd.y -= 30f * dt }
        words.removeAll { it.t >= 1f }
    }

    /** x пасти на экране. */
    fun mouthX(): Float {
        if (state == 3) {
            val k = min(1f, eatT / 0.28f)
            val e = 1f - (1f - k) * (1f - k)
            val base = heroX - gap * kvis
            val x0 = base + (heroX + 36f - base) * e
            return if (eatT > 0.28f) heroX + 36f + (eatT - 0.28f) * 520f else x0
        }
        return heroX - gap * kvis
    }

    fun mouthOpen(): Float {
        if (state == 3) return if (eatT < 0.28f) 1f else max(0f, 0.25f - (eatT - 0.28f) * 3f)
        if (state == 1) return 0.5f + 0.5f * (1f - telT / 0.45f)
        val base = 0.16f + 0.10f * sin(time * 6f)
        return if (rageT > 0f || warnT > 0f) base + 0.3f else base
    }
}

private enum class SkScreen { MENU, PLAY, OVER }

private fun skPrefs(ctx: Context) = ctx.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE)

@Composable
private fun SkChip(text: String) {
    Box(
        Modifier
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
}

@Composable
fun SharkChaseScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val z = LocalDensity.current.density
    val toddler = LocalAgeProfile.current.isToddler2

    val houseList = remember { loadHouse(context) }
    var heroIdx by remember { mutableIntStateOf(try { skPrefs(context).getInt("sk_hero", 0) } catch (e: Exception) { 0 }) }
    if (heroIdx >= houseList.size) heroIdx = 0
    val heroSpec = houseList.getOrNull(heroIdx) ?: MonSpec()
    val heroImgs = rememberMonImgs(heroSpec)
    val heroAnim = remember { MonAnim().also { it.danceUntil = 1e9f } }

    val sharkFrames = remember {
        listOf(R.drawable.shark_f00,R.drawable.shark_f01,R.drawable.shark_f02,R.drawable.shark_f03,R.drawable.shark_f04,R.drawable.shark_f05,R.drawable.shark_f06,R.drawable.shark_f07,R.drawable.shark_f08,R.drawable.shark_f09,R.drawable.shark_f10,R.drawable.shark_f11,R.drawable.shark_f12,R.drawable.shark_f13,R.drawable.shark_f14,R.drawable.shark_f15,R.drawable.shark_f16,R.drawable.shark_f17,R.drawable.shark_f18,R.drawable.shark_f19,R.drawable.shark_f20,R.drawable.shark_f21,R.drawable.shark_f22,R.drawable.shark_f23,R.drawable.shark_f24).mapNotNull { id ->
            try { android.graphics.BitmapFactory.decodeResource(context.resources, id)?.asImageBitmap() } catch (e: Throwable) { null }
        }
    }
    val imgs = remember {
        fun ld(id: Int) = try { android.graphics.BitmapFactory.decodeResource(context.resources, id)?.asImageBitmap() } catch (e: Throwable) { null }
        SkImgs(loadBoomFrames(context), ld(R.drawable.sw_mine), ld(R.drawable.sw_kitten))
    }
    val world = remember(toddler) { SkWorld(toddler) }
    var screen by remember { mutableStateOf(SkScreen.MENU) }
    var tick by remember { mutableIntStateOf(0) }
    var clock by remember { mutableFloatStateOf(0f) }
    var best by remember { mutableIntStateOf(try { skPrefs(context).getInt("sk_best", 0) } catch (e: Exception) { 0 }) }
    var newBest by remember { mutableStateOf(false) }
    var resultM by remember { mutableIntStateOf(0) }
    var resultP by remember { mutableIntStateOf(0) }
    var hudP by remember { mutableIntStateOf(0) }
    var hudM by remember { mutableIntStateOf(0) }
    var hudGap by remember { mutableFloatStateOf(1f) }
    var hudEnergy by remember { mutableFloatStateOf(1f) }
    var hudWarn by remember { mutableStateOf(false) }
    var lottieTick by remember { mutableIntStateOf(0) }
    var banked by remember { mutableIntStateOf(0) }
    fun bank() {
        val d = world.pearls - banked
        if (d > 0) { addWallet(context, d); banked += d }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { bank() } }
    val walletNow = remember(screen) { loadWallet(context) }

    LaunchedEffect(Unit) {
        soundManager.preloadRaw(listOf("sw_dash", "sw_pearl", "sw_ring", "sw_hit", "sw_rage", "sw_warn", "sw_miss", "sw_chomp", "sw_gulp", "sw_start", "sw_over", "sw_heart", "sw_crunch", "sw_boom"))
    }
    world.onEvent = { ev, arg ->
        when (ev) {
            "start" -> soundManager.play("sw_start", volume = 0.8f)
            "dash" -> soundManager.play("sw_dash", volume = 0.8f)
            "pearl" -> soundManager.play("sw_pearl", volume = 0.6f, rate = 0.9f + arg * 0.07f)
            "ring" -> soundManager.play("sw_ring", volume = 0.9f)
            "hit" -> { soundManager.play("sw_hit", volume = 0.9f); soundPlayer.vibrateSquishDeform() }
            "rageWarn" -> soundManager.play("sw_rage", volume = 0.9f)
            "warn" -> soundManager.play("sw_warn", volume = 0.8f)
            "miss" -> soundManager.play("sw_miss", volume = 0.9f)
            "gulp" -> soundManager.play("sw_gulp", volume = 0.7f)
            "crunch" -> soundManager.play("sw_crunch", volume = 0.45f, rate = 0.9f + Random.nextFloat() * 0.3f)
            "heart" -> soundManager.play("sw_heart", volume = 0.4f + arg * 0.5f)
            "boom" -> soundManager.play("sw_boom", volume = 0.8f, rate = 0.9f + Random.nextFloat() * 0.2f)
            "chomp" -> { soundManager.play("sw_chomp", volume = 1f); soundPlayer.vibrateSquishDeform() }
            "over" -> {
                soundManager.play("sw_over", volume = 0.8f)
                bank()
                resultM = (world.dist / 10f).toInt()
                resultP = world.pearls
                newBest = resultM > best
                if (newBest) {
                    best = resultM
                    try { skPrefs(context).edit().putInt("sk_best", best).apply() } catch (e: Exception) { }
                    lottieTick++
                }
                screen = SkScreen.OVER
            }
        }
    }

    fun begin() {
        bank(); banked = 0
        world.reset()
        screen = SkScreen.PLAY
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0277BD))
            .testTag("shark_chase_root")
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (screen == SkScreen.PLAY) world.targetY = (down.position.y / z)
                    do {
                        val e = awaitPointerEvent()
                        val c = e.changes.firstOrNull()
                        if (c != null && screen == SkScreen.PLAY) world.targetY = (c.position.y / z)
                    } while (e.changes.any { it.pressed })
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val redraw = tick
            world.setSize(size.width / z, size.height / z)
            val sx = if (world.shake > 0f) sin(clock * 90f) * 7f * world.shake else 0f
            val sy = if (world.shake > 0f) cos(clock * 80f) * 7f * world.shake else 0f
            withTransform({ translate(sx * z, sy * z); scale(z, z, Offset.Zero) }) {
                skDrawSea(world)
                skDrawObjects(world, imgs)
            }
            // герой
            val show = screen == SkScreen.PLAY && world.state != 3 || (screen == SkScreen.PLAY && world.state == 3 && world.eatT < 0.28f)
            val blink = world.invuln > 0f && ((world.invuln * 12f).toInt() % 2 == 0) && world.state != 3
            if (screen != SkScreen.MENU && show && !blink) {
                heroAnim.squash = (sin(clock * 5f) * 0.05f)
                val hx = world.heroX * z
                val cyPx = world.hy * z
                val lay = monLayout(heroImgs, hx, cyPx + 59f * z, 92f * z, 114f * z)
                withTransform({ rotate(Math.toDegrees(world.tilt.toDouble()).toFloat() + 6f, Offset(hx, cyPx)) }) {
                    drawMonster(heroImgs, heroSpec, lay, heroAnim, clock * 1.3f, z, 0.5f)
                }
            }
            withTransform({ translate(sx * z, sy * z); scale(z, z, Offset.Zero) }) {
                if (screen != SkScreen.OVER || world.state == 3) skDrawShark(world, sharkFrames)
                skDrawFront(world, imgs)
            }
        }

        if (screen == SkScreen.PLAY) {
            Row(
                Modifier.statusBarsPadding().padding(top = 8.dp, start = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkChip("🐚 $hudP")
                SkChip("📏 $hudM м")
            }
            // дистанция до акулы
            Row(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 50.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🦈", fontSize = 22.sp)
                Box(
                    Modifier.padding(horizontal = 6.dp).size(width = 150.dp, height = 12.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                ) {
                    Box(
                        Modifier.fillMaxSize().then(Modifier)
                            .padding(end = (150f * hudGap).dp.coerceAtLeast(0.dp))
                            .background(Color(0xFFFF5252), RoundedCornerShape(6.dp))
                    )
                }
                Text("👾", fontSize = 22.sp)
            }
            if (hudWarn) {
                Text(
                    "⚠️ БЕШЕНАЯ АКУЛА!", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFEB3B),
                    modifier = Modifier.align(Alignment.Center).padding(bottom = 90.dp)
                )
            }
            // рывок
            Column(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(width = 86.dp, height = 10.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(5.dp))
                ) {
                    Box(
                        Modifier.size(width = (86f * hudEnergy).dp, height = 10.dp)
                            .background(if (hudEnergy >= 0.35f) Color(0xFF69F0AE) else Color(0xFFFFAB40), RoundedCornerShape(5.dp))
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.size(92.dp)
                        .background(if (hudEnergy >= 0.35f) Color(0xFF00C853).copy(alpha = 0.9f) else Color(0xFF607D8B).copy(alpha = 0.8f), CircleShape)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val d = awaitFirstDown()
                                d.consume()
                                if (screen == SkScreen.PLAY) world.dash()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Text("🚀", fontSize = 42.sp) }
            }
        }

        if (screen == SkScreen.MENU) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                KitWindow(width = 330.dp, title = "Жадная акула") {
                    Text(
                        "Жадная акула хочет тебя съесть — не дай себя поймать!\nТяни пальцем вверх-вниз — плыви.\n🚀 — рывок. Лови 🐚, ныряй в золотые кольца.\nЗаманивай акулу в стайки рыбок и на мины 💣!\nКотик 🐱 в пузыре — защитный щит.",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6A1B4D), textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(6.dp))
                KitLongButton(
                    R.drawable.ui_btn_long_blue,
                    if (houseList.isEmpty()) "Нужен монстрик 👾" else "Другой монстрик (${heroIdx + 1}/${houseList.size})",
                    230.dp, 12.sp
                ) {
                    if (houseList.isNotEmpty()) {
                        heroIdx = (heroIdx + 1) % houseList.size
                        soundPlayer.playPop(1.3f)
                        try { skPrefs(context).edit().putInt("sk_hero", heroIdx).apply() } catch (e: Exception) { }
                    }
                }
                Spacer(Modifier.height(6.dp))
                KitLongButton(R.drawable.ui_btn_long_green, "ПЛЫТЬ ▶", 210.dp, 22.sp) { begin() }
                SkChip("🪙 в кошельке: $walletNow")
                if (best > 0) {
                    Spacer(Modifier.height(4.dp))
                    SkChip("🏆 рекорд: $best м")
                }
            }
        }

        if (screen == SkScreen.OVER) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                KitWindow(width = 330.dp, title = if (newBest) "Рекорд!" else "Жадина съела!") {
                    Text("📏 $resultM м   🐚 $resultP", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF6A1B4D))
                    Spacer(Modifier.height(8.dp))
                    KitLongButton(R.drawable.ui_btn_long_green, "Ещё раз ↻", 170.dp, 15.sp) { begin() }
                    KitLongButton(R.drawable.ui_btn_long_blue, "В меню", 170.dp, 15.sp) { screen = SkScreen.MENU }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LottieConfetti(lottieTick, Modifier.fillMaxSize())
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.033f)
                last = now
                clock += dt
                if (screen == SkScreen.PLAY) {
                    world.step(dt)
                    hudP = world.pearls
                    hudM = (world.dist / 10f).toInt()
                    hudGap = (world.gap / SK_GAP_MAX).coerceIn(0f, 1f)
                    hudEnergy = (world.energy / 100f).coerceIn(0f, 1f)
                    hudWarn = world.warnT > 0f || world.rageT > 0f
                } else if (screen == SkScreen.MENU) {
                    world.running = false
                    world.step(dt)
                } else {
                    world.step(dt)
                }
                tick++
            }
        }
    }
}

// ───────────────────────── Рисование моря ─────────────────────────

private val SK_TOP = listOf(Color(0xFF4DD0E1), Color(0xFF1E88E5), Color(0xFF283593))
private val SK_BOT = listOf(Color(0xFF0277BD), Color(0xFF0D47A1), Color(0xFF070B2E))

private fun skBiome(dist: Float): Triple<Color, Color, Float> {
    val f = (dist / 3500f)
    val i = floor(f).toInt()
    val u = f - i
    val k = ((u - 0.75f) / 0.25f).coerceIn(0f, 1f)
    val s = k * k * (3f - 2f * k)
    val a = i % 3
    val b = (i + 1) % 3
    return Triple(lerp(SK_TOP[a], SK_TOP[b], s), lerp(SK_BOT[a], SK_BOT[b], s), (a + s) / 2f)
}

private fun DrawScope.skDrawSea(w: SkWorld) {
    val sw = w.w
    val sh = w.h
    val camX = w.dist
    val t = w.time
    val (top, bot, depth) = skBiome(w.dist)
    // небо над водой
    drawRect(Brush.verticalGradient(listOf(Color(0xFFB3E5FC), Color(0xFFE1F5FE)), startY = 0f, endY = SK_SURFACE), Offset.Zero, Size(sw, SK_SURFACE))
    drawCircle(Color(0xFFFFF59D), 18f, Offset(sw * 0.82f, 22f))
    // вода
    drawRect(Brush.verticalGradient(listOf(top, bot), startY = SK_SURFACE, endY = sh), Offset(0f, SK_SURFACE), Size(sw, sh - SK_SURFACE))
    // лучи
    for (i in 0 until 6) {
        val x = (((i * 190f - camX * 0.12f) % (sw + 300f)) + (sw + 300f)) % (sw + 300f) - 150f
        val sway = sin(t * 0.5f + i) * 26f
        val path = Path().apply {
            moveTo(x - 24f, SK_SURFACE); lineTo(x + 24f, SK_SURFACE)
            lineTo(x + 130f + sway, sh); lineTo(x - 60f + sway, sh); close()
        }
        drawPath(path, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f * (1f - depth * 0.85f)), Color.Transparent), startY = SK_SURFACE, endY = sh * 0.9f))
    }
    // дальний риф
    val far = Path()
    far.moveTo(0f, sh)
    var x = 0f
    while (x <= sw + 30f) {
        val wx = x + camX * 0.15f
        far.lineTo(x, w.floorY - 40f - 36f * sin(wx * 0.011f) - 22f * sin(wx * 0.027f + 1f))
        x += 24f
    }
    far.lineTo(sw, sh); far.close()
    drawPath(far, Color(0xFF01285A).copy(alpha = 0.35f))
    // водоросли
    val kelpSpacing = 150f
    val first = floor((camX * 0.55f) / kelpSpacing).toInt() - 1
    for (i in first..first + (sw / kelpSpacing).toInt() + 3) {
        val kx = i * kelpSpacing - camX * 0.55f + (i * 37 % 60)
        val hgt = 70f + ((i * 53) % 70)
        val p = Path()
        p.moveTo(kx, w.floorY + 6f)
        for (s in 1..6) {
            val u = s / 6f
            p.lineTo(kx + sin(t * 1.5f + i + u * 3f) * 12f * u, w.floorY + 6f - hgt * u)
        }
        drawPath(p, Color(0xFF2E7D32).copy(alpha = 0.75f), style = Stroke(width = 7f, cap = StrokeCap.Round))
    }
    // дно
    drawRect(Brush.verticalGradient(listOf(Color(0xFFE6CE96), Color(0xFFB89A5E)), startY = w.floorY, endY = sh), Offset(0f, w.floorY), Size(sw, sh - w.floorY))
    drawRect(Color(0xFFF3E1AE), Offset(0f, w.floorY), Size(sw, 5f))
    var px = -(camX % 70f)
    var pi = floor(camX / 70f).toInt()
    while (px < sw + 70f) {
        val k = pi * 7919 % 100
        drawOval(Color(0xFF8D6E63).copy(alpha = 0.55f), Offset(px + k % 30, w.floorY + 12f + k % 20), Size(10f + k % 9, 5f + k % 4))
        if (k % 9 == 0) drawCircle(Color(0xFFFF8A80), 5f, Offset(px + 20f, w.floorY + 8f))
        px += 70f; pi++
    }
    // волны на поверхности
    val wave = Path()
    wave.moveTo(0f, SK_SURFACE + 8f)
    var wx = 0f
    while (wx <= sw + 20f) {
        wave.lineTo(wx, SK_SURFACE + sin(wx * 0.04f + t * 2f - camX * 0.02f) * 4f)
        wx += 12f
    }
    wave.lineTo(sw, SK_SURFACE + 14f); wave.lineTo(0f, SK_SURFACE + 14f); wave.close()
    drawPath(wave, Color.White.copy(alpha = 0.35f))
}

private fun DrawScope.skDrawObjects(w: SkWorld, imgs: SkImgs) {
    val camX = w.dist
    val t = w.time
    val sw = w.w
    for (o in w.obs) {
        if (o.x - camX > sw + 120f || o.x - camX < -200f) continue
        val sx = o.x - camX
        when (o.kind) {
            0 -> { // скала снизу
                val p = Path().apply {
                    moveTo(sx, w.floorY + 4f); lineTo(sx + o.w * 0.2f, w.floorY - o.h * 0.6f)
                    lineTo(sx + o.w * 0.5f, w.floorY - o.h); lineTo(sx + o.w * 0.78f, w.floorY - o.h * 0.55f)
                    lineTo(sx + o.w, w.floorY + 4f); close()
                }
                drawPath(p, Brush.verticalGradient(listOf(Color(0xFF90A4AE), Color(0xFF455A64)), startY = w.floorY - o.h, endY = w.floorY))
                drawPath(p, Color(0xFF263238), style = Stroke(3f))
                drawLine(Color.White.copy(alpha = 0.35f), Offset(sx + o.w * 0.45f, w.floorY - o.h * 0.9f), Offset(sx + o.w * 0.25f, w.floorY - o.h * 0.45f), 3f, StrokeCap.Round)
                drawCircle(Color(0xFFEC407A), 6f, Offset(sx + o.w * 0.3f, w.floorY - o.h * 0.3f))
            }
            1 -> { // якорь на цепи
                val cx = sx + o.w / 2f
                val end = SK_SURFACE + o.h
                var yy = SK_SURFACE
                var k = 0
                while (yy < end - 14f) {
                    drawRoundRect(Color(0xFF607D8B), Offset(cx - 5f + (if (k % 2 == 0) 0f else 2f), yy), Size(10f, 16f), CornerRadius(5f), style = Stroke(3f))
                    yy += 13f; k++
                }
                val ay = end
                drawCircle(Color(0xFF37474F), 6f, Offset(cx, ay - 12f), style = Stroke(3f))
                drawLine(Color(0xFF37474F), Offset(cx, ay - 6f), Offset(cx, ay + 30f), 7f, StrokeCap.Round)
                drawLine(Color(0xFF37474F), Offset(cx - 22f, ay + 8f), Offset(cx + 22f, ay + 8f), 6f, StrokeCap.Round)
                val arc = Path().apply { moveTo(cx - 28f, ay + 18f); quadraticBezierTo(cx, ay + 50f, cx + 28f, ay + 18f) }
                drawPath(arc, Color(0xFF37474F), style = Stroke(7f, cap = StrokeCap.Round))
            }
            2 -> { // медуза
                val cy = o.y + sin(t * 2f + o.phase) * 40f
                val r = o.w * 0.5f
                val glow = if (o.zap > 0f) 0.6f else 0.25f
                drawCircle(Color(0xFFE040FB).copy(alpha = glow), r * 1.5f, Offset(sx, cy))
                for (k in -2..2) {
                    val p = Path().apply {
                        moveTo(sx + k * r * 0.3f, cy + r * 0.2f)
                        for (s in 1..5) quadraticBezierTo(sx + k * r * 0.3f + sin(t * 4f + s + k) * 7f, cy + r * (0.2f + s * 0.28f), sx + k * r * 0.3f + sin(t * 4f + s + k + 1f) * 5f, cy + r * (0.2f + s * 0.3f))
                    }
                    drawPath(p, Color(0xFFF8BBD0).copy(alpha = 0.85f), style = Stroke(3f, cap = StrokeCap.Round))
                }
                val dome = Path().apply { moveTo(sx - r, cy + r * 0.25f); quadraticBezierTo(sx - r, cy - r * 1.15f, sx, cy - r * 1.15f); quadraticBezierTo(sx + r, cy - r * 1.15f, sx + r, cy + r * 0.25f); close() }
                drawPath(dome, Brush.verticalGradient(listOf(Color(0xFFF48FB1), Color(0xFFBA68C8)), startY = cy - r, endY = cy + r * 0.3f))
                drawPath(dome, Color.White.copy(alpha = 0.5f), style = Stroke(2f))
                drawCircle(Color.White.copy(alpha = 0.6f), r * 0.13f, Offset(sx - r * 0.4f, cy - r * 0.55f))
                drawCircle(Color(0xFF4A148C), 3.5f, Offset(sx - r * 0.3f, cy - r * 0.2f)); drawCircle(Color(0xFF4A148C), 3.5f, Offset(sx + r * 0.3f, cy - r * 0.2f))
            }
            4 -> { // морская мина
                val cy = o.y + sin(t * 1.3f + o.phase) * 10f
                val im = imgs.mine
                val blink = 0.5f + 0.5f * sin(t * 8f + o.phase)
                if (im != null) {
                    val k = (o.w * 1.55f) / 192f
                    withTransform({ translate(sx - o.w * 0.775f, cy - o.w * 0.775f); scale(k, k, Offset.Zero) }) { drawImage(im, Offset.Zero) }
                } else drawCircle(Color(0xFF455A64), o.w * 0.5f, Offset(sx, cy))
                drawCircle(Color(0xFFFF1744).copy(alpha = 0.35f + 0.5f * blink), 5f, Offset(sx, cy - o.w * 0.1f))
            }
            else -> { // морской ёж
                val cy = o.y + sin(t * 1.6f + o.phase) * 14f
                val r = o.w * 0.36f
                for (k in 0 until 14) {
                    val a = k / 14f * 6.2832f + t * 0.3f
                    drawLine(Color(0xFF4A148C), Offset(sx + cos(a) * r * 0.8f, cy + sin(a) * r * 0.8f), Offset(sx + cos(a) * r * 1.55f, cy + sin(a) * r * 1.55f), 4f, StrokeCap.Round)
                }
                drawCircle(Brush.radialGradient(listOf(Color(0xFF8E24AA), Color(0xFF311B92)), center = Offset(sx - r * 0.3f, cy - r * 0.3f), radius = r * 1.4f), r, Offset(sx, cy))
                drawCircle(Color(0xFFFF5252), 4f, Offset(sx - r * 0.3f, cy - r * 0.1f)); drawCircle(Color(0xFFFF5252), 4f, Offset(sx + r * 0.3f, cy - r * 0.1f))
            }
        }
    }
    for (kt in w.kits) {
        val sx = kt.x - camX
        if (sx > sw + 60f || sx < -60f) continue
        val ky = kt.y + sin(t * 2.2f + kt.x * 0.01f) * 6f
        drawCircle(Color(0xFFB2EBF2).copy(alpha = 0.35f), 40f, Offset(sx, ky))
        imgs.kit?.let { im ->
            val k = 54f / 200f
            withTransform({ translate(sx - 27f, ky - 27f); scale(k, k, Offset.Zero) }) { drawImage(im, Offset.Zero) }
        }
        drawCircle(Color.White.copy(alpha = 0.7f), 40f, Offset(sx, ky), style = Stroke(2.5f))
        drawCircle(Color.White.copy(alpha = 0.8f), 5f, Offset(sx - 18f, ky - 20f))
    }
    for (r in w.rings) {
        val sx = r.x - camX
        if (sx > sw + 60f || sx < -60f) continue
        val pulse = 1f + 0.06f * sin(t * 6f)
        drawOval(Color(0xFFFFD54F).copy(alpha = 0.35f), Offset(sx - 20f, r.y - 60f * pulse), Size(40f, 120f * pulse))
        drawOval(Color(0xFFFFC107), Offset(sx - 16f, r.y - 52f * pulse), Size(32f, 104f * pulse), style = Stroke(9f))
        drawOval(Color(0xFFFFF59D), Offset(sx - 16f, r.y - 52f * pulse), Size(32f, 104f * pulse), style = Stroke(3f))
        drawCircle(Color.White, 3f, Offset(sx + sin(t * 5f) * 14f, r.y - 40f * pulse))
    }
    for (p in w.pearlsL) {
        val sx = p.x - camX
        if (sx > sw + 30f || sx < -30f) continue
        val py = p.y + sin(t * 3f + p.x * 0.05f) * 3f
        drawCircle(Color.White.copy(alpha = 0.25f), 13f, Offset(sx, py))
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFF8BBD0)), center = Offset(sx - 3f, py - 3f), radius = 11f), 9f, Offset(sx, py))
        drawCircle(Color.White, 2.5f, Offset(sx - 3f, py - 3f))
    }
    for (s in w.schools) {
        val sx = s.x - camX
        if (sx > sw + 120f || sx < -120f) continue
        for (k in 0 until s.n) {
            val fx = sx + (k % 3) * 24f + sin(t * 2f + s.ph + k) * 8f
            val fy = s.y + (k / 3) * 22f - 11f + sin(t * 3f + k * 1.7f) * 6f
            skFish(fx, fy, if (k % 2 == 0) Color(0xFFFFB74D) else Color(0xFFFFEE58), 1f, t + k)
        }
    }
}

private fun DrawScope.skFish(x: Float, y: Float, c: Color, dir: Float, t: Float) {
    val tail = sin(t * 12f) * 3f
    val tp = Path().apply { moveTo(x - 9f * dir, y); lineTo(x - 17f * dir, y - 7f + tail); lineTo(x - 17f * dir, y + 7f + tail); close() }
    drawPath(tp, c)
    drawOval(c, Offset(x - 10f, y - 6.5f), Size(20f, 13f))
    drawOval(Color.White.copy(alpha = 0.4f), Offset(x - 5f, y - 4.5f), Size(9f, 4f))
    drawCircle(Color.White, 2.6f, Offset(x + 5f * dir, y - 1.5f))
    drawCircle(Color(0xFF212121), 1.3f, Offset(x + 5.6f * dir, y - 1.5f))
}

private fun DrawScope.skDrawShark(w: SkWorld, frames: List<ImageBitmap>) {
    val mx = w.mouthX()
    val my = w.sharkY
    val rage = w.rageT > 0f || w.warnT > 0f || w.state == 1 || w.state == 3
    val open = w.mouthOpen()
    val t = w.time
    // красное зарево при приближении
    if (w.running && w.gap < 200f && w.state != 3) {
        val a = ((1f - w.gap / 200f) * 0.45f) * (0.75f + 0.25f * sin(t * 10f))
        drawRect(Brush.horizontalGradient(listOf(Color(0xFFFF1744).copy(alpha = a), Color.Transparent), startX = 0f, endX = w.w * 0.4f), Offset.Zero, Size(w.w, w.h))
    }
    if (frames.isEmpty()) return
    val k = 250f / 300f
    val lunge = if (w.state == 1) (1f - w.telT / 0.45f) * 40f else 0f
    val pulse = if (w.state == 1) 1f + 0.12f * (1f - w.telT / 0.45f) else 1f
    val spd = if (rage) 22f else 14f
    val img = frames[((t * spd).toInt() % frames.size + frames.size) % frames.size]
    val tint = if (rage) ColorFilter.tint(Color(0x66FF1744), BlendMode.SrcAtop) else null
    val ox = if (w.state == 1) sin(t * 60f) * 2f else 0f
    withTransform({
        translate(mx + lunge + ox, my)
        rotate(sin(t * 3f) * 3f, Offset.Zero)
        scale(pulse, pulse, Offset.Zero)
        translate(-300f * k * 0.989f, -287f * k * 0.23f)
        scale(k, k, Offset.Zero)
    }) {
        drawImage(img, Offset.Zero, colorFilter = tint)
    }
}

private fun shade(c: Color, k: Float): Color = Color((c.red * k).coerceIn(0f, 1f), (c.green * k).coerceIn(0f, 1f), (c.blue * k).coerceIn(0f, 1f), 1f)

private val skWordFill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = android.graphics.Paint.Align.CENTER
    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
}
private val skWordStroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = android.graphics.Paint.Align.CENTER
    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    style = android.graphics.Paint.Style.STROKE
    color = 0xFF08224F.toInt()
}

private fun DrawScope.skDrawFront(w: SkWorld, imgs: SkImgs) {
    for (p in w.parts) {
        val a = p.life.coerceIn(0f, 1f)
        when (p.kind) {
            0 -> {
                drawCircle(p.color.copy(alpha = a * 0.75f), p.size, Offset(p.x, p.y), style = Stroke(1.6f))
                drawCircle(Color.White.copy(alpha = a * 0.7f), p.size * 0.22f, Offset(p.x - p.size * 0.35f, p.y - p.size * 0.35f))
            }
            1 -> withTransform({ rotate(p.rot * 57.3f, Offset(p.x, p.y)) }) {
                drawRect(p.color.copy(alpha = a), Offset(p.x - p.size / 2f, p.y - p.size / 2f), Size(p.size, p.size * 0.8f))
            }
            else -> drawCircle(p.color.copy(alpha = a), p.size * (0.5f + 0.5f * a), Offset(p.x, p.y))
        }
    }
    for (b in w.booms) drawBoom(imgs.boom, b.x, b.y, b.size, b.age)
    if (w.shield > 0f && w.running && w.state != 3) {
        val pr = 46f + 2f * sin(w.time * 7f)
        drawCircle(Color(0xFFB2EBF2).copy(alpha = 0.28f), pr, Offset(w.heroX, w.hy))
        drawCircle(Color.White.copy(alpha = 0.85f), pr, Offset(w.heroX, w.hy), style = Stroke(3f))
        drawCircle(Color.White.copy(alpha = 0.8f), 6f, Offset(w.heroX - pr * 0.45f, w.hy - pr * 0.5f))
    }
    val cv = drawContext.canvas.nativeCanvas
    for (wd in w.words) {
        val k = wd.t
        val pop = if (k < 0.15f) 0.6f + k / 0.15f * 0.6f else 1.2f - (k - 0.15f) * 0.2f
        val a = ((1f - k * k) * 255).toInt().coerceIn(0, 255)
        val sz = 30f * pop * w.density()
        skWordStroke.textSize = sz; skWordStroke.strokeWidth = sz * 0.2f; skWordStroke.alpha = a
        skWordFill.textSize = sz; skWordFill.alpha = a
        skWordFill.color = ((255 shl 24) or ((wd.color.red * 255).toInt() shl 16) or ((wd.color.green * 255).toInt() shl 8) or (wd.color.blue * 255).toInt())
        cv.save()
        cv.scale(1f / w.density(), 1f / w.density(), 0f, 0f)
        cv.drawText(wd.text, wd.x * w.density(), wd.y * w.density(), skWordStroke)
        cv.drawText(wd.text, wd.x * w.density(), wd.y * w.density(), skWordFill)
        cv.restore()
    }
    if (w.flash > 0f) drawRect(w.flashColor.copy(alpha = (w.flash * 0.3f).coerceIn(0f, 0.35f)), Offset.Zero, Size(w.w, w.h))
    // затемнение глубины
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x55001133)), center = Offset(w.w / 2f, w.h / 2f), radius = max(w.w, w.h) * 0.75f), Offset.Zero, Size(w.w, w.h))
}

private fun SkWorld.density(): Float = 1f
