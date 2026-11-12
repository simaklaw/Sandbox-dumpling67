package com.example.ui.screens

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Правила комнаты «Ракетный робот» (индекс 50): чистая логика без Compose, чтобы её проверяли юнит-тесты (JetBotRulesTest).
// Идея: пещера едет справа налево, робот с ракетным ранцем: палец на экране — тяга вверх, отпустил — падает.
// Стены пещеры безопасны (робот мягко отскакивает), опасны только препятствия. Ударов можно выдержать три, потом «ещё раз».
// Все размеры в «логических единицах»: ширина мира всегда JET_LW, высота зависит от экрана.

const val JET_LW = 360f

object JetCfg {
    const val PLAYER = 40f
    const val PLAYER_X = 0.27f          // доля ширины
    const val BASE_SPEED = 120f         // единиц в секунду
    const val MAX_SPEED = 175f
    const val ACCEL = 0.35f             // прибавка скорости в секунду
    const val GRAVITY = 520f
    const val THRUST = 1050f
    const val TERMINAL = 360f
    const val SEG = 90f                 // ширина звена пещеры
    const val WANDER = 26f
    const val LIVES = 3
    const val INVULN = 1.7f
    const val BOOST_TIME = 5f
    const val BOOST_MUL = 1.45f
    const val POWER_EVERY = 1250f
    const val SPIKE_EVERY = 560f
}

enum class JetKind { GEAR, WOBBLE, DASH, ZIGZAG, SPIKE, BAR, FLAG }

enum class JetPower { SHIELD, BOOST }

class JetLevel(val name: String, val caveH: Float, val distance: Float, val spacing: Float, val kinds: List<JetKind>)

val JET_LEVELS = listOf(
    JetLevel("Старт", 470f, 2200f, 330f, listOf(JetKind.GEAR, JetKind.WOBBLE, JetKind.DASH, JetKind.ZIGZAG)),
    JetLevel("Вертушки", 400f, 1700f, 600f, listOf(JetKind.BAR)),
    JetLevel("Колючки", 350f, 1500f, 360f, listOf(JetKind.GEAR, JetKind.WOBBLE, JetKind.ZIGZAG, JetKind.SPIKE))
)

val JET_TOTAL = JET_LEVELS.sumOf { it.distance.toDouble() }.toFloat()

class JetCavePt(var x: Float, val ceil: Float, val floor: Float)

class JetObs(
    val kind: JetKind, var x: Float, var y: Float, val w: Float, val h: Float,
    val startY: Float, var phase: Float, var vSpeed: Float, val amp: Float, val ceiling: Boolean
)

class JetPow(val kind: JetPower, var x: Float, var y: Float, val size: Float)

/** Событие мира для экрана: звук, вспышка, вибрация. */
class JetEvent(val type: Int, val x: Float, val y: Float) {
    companion object {
        const val HIT = 0       // потерял сердечко
        const val SHIELD_BREAK = 1
        const val POWER_SHIELD = 2
        const val POWER_BOOST = 3
        const val WIN = 4
        const val LOST = 5
        const val LEVEL = 6
    }
}

const val JET_PLAY = 0
const val JET_WON = 1
const val JET_LOST = 2

class JetWorld(val lh: Float, val rnd: Random = Random.Default, val god: Boolean = false) {
    val lw = JET_LW
    var dist = 0f
    var level = 0
    var speed = JetCfg.BASE_SPEED
    var playerY = lh / 2f - JetCfg.PLAYER / 2f
    var vy = 0f
    var hold = 0f
    var shield = false
    var boostLeft = 0f
    var invuln = 0f
    var lives = JetCfg.LIVES
    var state = JET_PLAY
    var clock = 0f
    val cave = ArrayList<JetCavePt>()
    val obs = ArrayList<JetObs>()
    val pows = ArrayList<JetPow>()
    val events = ArrayList<JetEvent>()

    private var targetY = lh / 2f
    private var caveH = JET_LEVELS[0].caveH + 60f
    private var nextObs = 420f
    private var nextPow = 700f
    private var nextSpike = 500f
    private var flagSpawned = false

    val playerX get() = lw * JetCfg.PLAYER_X - JetCfg.PLAYER / 2f
    val boosting get() = boostLeft > 0f

    init {
        val n = (lw / JetCfg.SEG).toInt() + 3
        for (i in 0 until n) cave.add(JetCavePt(i * JetCfg.SEG, targetY - caveH / 2f, targetY + caveH / 2f))
    }

    private fun nextCavePt(): JetCavePt {
        val last = cave.last()
        val pad = 40f + caveH / 2f
        targetY = (targetY + (rnd.nextFloat() - 0.5f) * 2f * JetCfg.WANDER).coerceIn(pad, max(pad, lh - pad))
        caveH += (JET_LEVELS[level].caveH - caveH) * 0.05f
        return JetCavePt(last.x + JetCfg.SEG, targetY - caveH / 2f, targetY + caveH / 2f)
    }

    /** Потолок и пол пещеры на горизонтали [x] (линейная интерполяция между звеньями). */
    fun caveAt(x: Float): Pair<Float, Float> {
        for (i in 0 until cave.size - 1) {
            val a = cave[i]; val b = cave[i + 1]
            if (x >= a.x && x <= b.x) {
                val t = (x - a.x) / (b.x - a.x)
                return Pair(a.ceil + (b.ceil - a.ceil) * t, a.floor + (b.floor - a.floor) * t)
            }
        }
        val e = if (x > cave.last().x) cave.last() else cave.first()
        return Pair(e.ceil, e.floor)
    }

    private fun spawnObstacle() {
        val lv = JET_LEVELS[level]
        val kind = lv.kinds[rnd.nextInt(lv.kinds.size)]
        val (cy, fy) = caveAt(lw)
        val mid = (cy + fy) / 2f
        val range = (fy - cy) * 0.55f
        var y = mid + (rnd.nextFloat() - 0.5f) * range
        var w = 34f; var h = 34f
        var vs = 0f; var amp = 0f; var ceiling = false
        when (kind) {
            JetKind.WOBBLE -> { amp = 55f + rnd.nextFloat() * 30f; vs = 1.4f + rnd.nextFloat() * 1.2f }
            JetKind.DASH -> { w = 46f; h = 30f }
            JetKind.ZIGZAG -> { vs = 70f + rnd.nextFloat() * 50f }
            JetKind.SPIKE -> { w = 30f; h = 42f; ceiling = rnd.nextBoolean() }
            JetKind.BAR -> { w = 14f; h = 230f }
            else -> {}
        }
        var x = lw + 20f
        if (kind == JetKind.SPIKE) {
            val p = cave.last(); x = p.x
            y = if (ceiling) p.ceil - 6f else p.floor - h + 6f
        }
        obs.add(JetObs(kind, x, y, w, h, y, rnd.nextFloat() * 6.28f, vs, amp, ceiling))
    }

    private fun spawnFloorSpike() {
        val p = cave.last(); val h = 28f + rnd.nextFloat() * 14f
        obs.add(JetObs(JetKind.SPIKE, p.x, p.floor - h + 6f, 30f, h, p.floor - h + 6f, 0f, 0f, 0f, false))
    }

    private fun spawnPower() {
        val (cy, fy) = caveAt(lw)
        val mid = (cy + fy) / 2f
        val kind = if (rnd.nextBoolean()) JetPower.SHIELD else JetPower.BOOST
        pows.add(JetPow(kind, lw + 20f, mid + (rnd.nextFloat() - 0.5f) * (fy - cy) * 0.5f, 44f))
    }

    private fun spawnFlag() {
        val (cy, fy) = caveAt(lw + 160f)
        obs.add(JetObs(JetKind.FLAG, lw + 160f, (cy + fy) / 2f - 60f, 50f, 120f, 0f, 0f, 0f, 0f, false))
        flagSpawned = true
    }

    /** Один шаг мира. [touching] — палец на экране (тяга). */
    fun step(dt: Float, touching: Boolean) {
        if (state != JET_PLAY) return
        clock += dt
        // тяга нарастает и спадает плавно, чтобы полёт был мягким
        hold = if (touching) min(1f, hold + 7f * dt) else max(0f, hold - 9f * dt)
        if (boostLeft > 0f) boostLeft = max(0f, boostLeft - dt)
        if (invuln > 0f) invuln = max(0f, invuln - dt)
        speed = min(JetCfg.MAX_SPEED, speed + JetCfg.ACCEL * dt)
        val eff = speed * (if (boosting) JetCfg.BOOST_MUL else 1f)
        val dd = eff * dt
        dist += dd

        if (level < JET_LEVELS.size - 1) {
            var th = 0f
            for (i in 0..level) th += JET_LEVELS[i].distance
            if (dist >= th) { level++; events.add(JetEvent(JetEvent.LEVEL, playerX, playerY)) }
        }
        val goal = dist >= JET_TOTAL
        if (goal && !flagSpawned) spawnFlag()

        for (p in cave) p.x -= dd
        if (cave.first().x < -JetCfg.SEG) cave.removeAt(0)
        while (cave.last().x < lw + JetCfg.SEG) cave.add(nextCavePt())

        vy += (JetCfg.GRAVITY - JetCfg.THRUST * hold) * dt
        vy = vy.coerceIn(-JetCfg.TERMINAL, JetCfg.TERMINAL)
        playerY += vy * dt
        val (cy, fy) = caveAt(playerX + JetCfg.PLAYER / 2f)
        val pad = 4f
        if (playerY < cy + pad) { playerY = cy + pad; if (vy < 0f) vy *= -0.4f }
        else if (playerY + JetCfg.PLAYER > fy - pad) { playerY = fy - JetCfg.PLAYER - pad; if (vy > 0f) vy *= -0.4f }

        if (!goal) {
            if (dist >= nextPow) { spawnPower(); nextPow = dist + JetCfg.POWER_EVERY; nextObs = max(nextObs, dist + 150f) }
            if (dist >= nextObs) { spawnObstacle(); nextObs = dist + JET_LEVELS[level].spacing }
            if (dist >= nextSpike && level != 1) { spawnFloorSpike(); nextSpike = dist + JetCfg.SPIKE_EVERY }
        }

        updatePowers(dd)
        updateObstacles(dd, dt)
    }

    private fun updatePowers(dd: Float) {
        val it = pows.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x -= dd
            val (cy, fy) = caveAt(p.x + p.size / 2f)
            p.y = p.y.coerceIn(cy + 8f, max(cy + 8f, fy - p.size - 8f))
            val hit = playerX - 6f < p.x + p.size && playerX + JetCfg.PLAYER + 6f > p.x &&
                playerY - 6f < p.y + p.size && playerY + JetCfg.PLAYER + 6f > p.y
            if (hit) {
                if (p.kind == JetPower.SHIELD) { shield = true; events.add(JetEvent(JetEvent.POWER_SHIELD, p.x, p.y)) }
                else { boostLeft = JetCfg.BOOST_TIME; events.add(JetEvent(JetEvent.POWER_BOOST, p.x, p.y)) }
                it.remove()
            } else if (p.x + p.size < 0f) it.remove()
        }
    }

    private fun updateObstacles(dd: Float, dt: Float) {
        val it = obs.iterator()
        while (it.hasNext()) {
            val o = it.next()
            o.x -= dd * (if (o.kind == JetKind.DASH) 1.5f else 1f)
            val (cy, fy) = caveAt(o.x + o.w / 2f)
            when (o.kind) {
                JetKind.WOBBLE -> {
                    o.phase += o.vSpeed * dt
                    o.y = (o.startY + sin(o.phase) * o.amp).coerceIn(cy, max(cy, fy - o.h))
                }
                JetKind.ZIGZAG -> {
                    o.y += o.vSpeed * dt
                    if (o.y <= cy || o.y + o.h >= fy) { o.vSpeed = -o.vSpeed; o.y = o.y.coerceIn(cy, max(cy, fy - o.h)) }
                }
                JetKind.BAR -> {
                    o.phase += (0.35f + hold * 2.4f) * dt
                    o.y = o.y.coerceIn(cy + 50f, max(cy + 50f, fy - 50f))
                }
                JetKind.SPIKE -> o.y = if (o.ceiling) cy - 6f else fy - o.h + 6f
                JetKind.FLAG -> {}
                else -> o.y = o.y.coerceIn(cy, max(cy, fy - o.h))
            }
            if (o.kind == JetKind.FLAG) {
                if (playerX + JetCfg.PLAYER >= o.x) { state = JET_WON; events.add(JetEvent(JetEvent.WIN, o.x, o.y)); return }
                continue
            }
            if (collides(o) && invuln <= 0f) {
                val ex = o.x + o.w / 2f; val ey = o.y + o.h / 2f
                it.remove()
                if (shield || god) {
                    shield = false
                    events.add(JetEvent(JetEvent.SHIELD_BREAK, ex, ey))
                } else {
                    lives--
                    invuln = JetCfg.INVULN
                    vy = -120f
                    events.add(JetEvent(JetEvent.HIT, ex, ey))
                    if (lives <= 0) { state = JET_LOST; events.add(JetEvent(JetEvent.LOST, playerX, playerY)); return }
                }
            } else if (o.x + max(o.w, o.h) < -40f) it.remove()
        }
    }

    fun collides(o: JetObs): Boolean {
        val s = JetCfg.PLAYER
        if (o.kind == JetKind.BAR) {
            val px = playerX + s / 2f; val py = playerY + s / 2f
            val dx = px - o.x; val dy = py - (o.y)
            val c = cos(-o.phase); val sn = sin(-o.phase)
            val rx = dx * c - dy * sn; val ry = dx * sn + dy * c
            return abs(rx) < o.w / 2f + 12f && abs(ry) < o.h / 2f + 12f
        }
        val m = 9f
        return playerX + m < o.x + o.w && playerX + s - m > o.x && playerY + m < o.y + o.h && playerY + s - m > o.y
    }

    fun progress(): Float = (dist / JET_TOTAL).coerceIn(0f, 1f)
}
