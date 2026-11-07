package com.example.screens.Pinball

import kotlin.math.*
import kotlin.random.Random

enum class SpeedMode(val gravity: Float, val flipperSpeedMultiplier: Float) {
    TURTLE(6f, 0.85f),
    BUNNY(10f, 1.0f),
    ROCKET(14f, 1.15f)
}

enum class EntityType { WALL, BUMPER, SLINGSHOT, STAR, DRAIN, FLIPPER_LEFT, FLIPPER_RIGHT, BALL }

class PinVec(val x: Float, val y: Float)

/** Шарик. Координаты — в «мировых» единицах поля 9×18, ось Y вниз. */
class PinBall(var px: Float, var py: Float, var vx: Float, var vy: Float) {
    val position: PinVec get() = PinVec(px, py)
    internal var anchorX = px
    internal var anchorY = py
    internal var anchorT = 0f
    /** Оставшееся время «плена» в воронке (0 — шарик свободен). */
    var captured = 0f
        internal set
    /** Поворот для рисования блика (катится по полю). */
    var roll = 0f
        internal set
    internal var restT = 0f
}

/**
 * Собственная физика пинбола (круг против «толстых» отрезков и кругов), без внешних библиотек.
 *
 * — Шаг 1/480 с, шарик не может проскочить сквозь стенку или лапку.
 * — Лапки вращаются и передают шарику скорость поверхности (настоящий удар, «пушечный» бросок у кончика).
 * — Рогатки, 4 бамперa, 6 кристаллов-мишеней, 3 ролловера, спиннер, воронка-«чёрная дыра», множитель и мультибол.
 * — Все зазоры на поле шире шарика минимум в 1.5 раза, а антизастревание срабатывает только как страховка.
 */
class PinballPhysicsEngine(
    val onScoreAdded: (Int) -> Unit,
    val onSoundTriggered: (String) -> Unit,
    val onBumperHit: (Int) -> Unit,
    val onStarHit: (Int) -> Unit,
    val onAllStarsLit: () -> Unit,
    /** Источник случайности (в тестах — с фиксированным зерном, чтобы симуляция была воспроизводимой). */
    private val rng: Random = Random.Default
) {
    val worldWidth = 9.0f
    val worldHeight = 18.0f

    // дополнительные события (необязательные)
    var onGemHit: (Int) -> Unit = {}
    var onAllGemsLit: () -> Unit = {}
    var onSaucer: (Boolean) -> Unit = {}   // true — захват, false — выстрел
    var onMultiplier: (Int) -> Unit = {}

    private class Seg(
        val ax: Float, val ay: Float, val bx: Float, val by: Float,
        val half: Float, val rest: Float, val kind: EntityType, val id: Int = 0
    )

    // ---------- устройство поля ----------
    val ballR = 0.36f
    val bumperR = 0.62f
    val bumperPositions = listOf(
        PinVec(3.1f, 5.4f), PinVec(5.9f, 5.4f), PinVec(4.5f, 7.3f), PinVec(4.5f, 3.3f)
    )
    val starPositions = listOf(PinVec(3.2f, 1.9f), PinVec(4.5f, 1.2f), PinVec(5.8f, 1.9f))
    // Центр кристалла не дальше от стенки, чем радиус шарика (0.36): иначе между стенкой и
    // кристаллом получается «клин», где шарик стоит в равновесии и не скатывается.
    val gemR = 0.30f
    val gemPositions = listOf(
        PinVec(0.30f, 7.2f), PinVec(0.30f, 8.3f), PinVec(0.30f, 9.4f),
        PinVec(8.70f, 7.2f), PinVec(8.70f, 8.3f), PinVec(8.70f, 9.4f)
    )
    val saucerPos = PinVec(4.5f, 10.6f)
    val saucerR = 0.42f
    val spinnerPos = PinVec(7.3f, 8.5f)
    val spinnerR = 0.55f

    val starLitStates = booleanArrayOf(false, false, false)
    val gemLit = BooleanArray(6)
    var multiplier = 1
        private set
    var spinAngle = 0f
        private set
    private var spinVel = 0f
    private var spinScoreT = 0f

    /** Вспышки для отрисовки: 1 → 0. */
    val bumperFlash = FloatArray(4)
    val slingFlash = FloatArray(2)
    val gemFlash = FloatArray(6)
    var saucerFlash = 0f
        private set

    private val segs = ArrayList<Seg>()
    private val balls = ArrayList<PinBall>()

    var currentSpeedMode: SpeedMode = SpeedMode.BUNNY
    var autoPawsEnabled: Boolean = true

    // ---- лапки ----
    val leftPivot = PinVec(2.4f, 16.1f)
    val rightPivot = PinVec(6.6f, 16.1f)
    val flipLen = 1.95f
    val flipHalf = 0.21f
    private val restAngle = 0.46f
    private val upAngle = -0.55f
    private var leftA = restAngle
    private var rightA = -restAngle
    private var leftW = 0f
    private var rightW = 0f
    @Volatile private var manualLeft = false
    @Volatile private var manualRight = false
    private var autoLeftUntil = 0f
    private var autoRightUntil = 0f
    private var leftOn = false
    private var rightOn = false

    // ---- служебное ----
    private var simT = 0f
    private val coolBumper = FloatArray(4)
    private val coolSling = FloatArray(2)
    private var coolWall = 0f
    private var lastUnstuck = 0f
    private var saucerCool = 0f
    private var bumperHits = 0
    private var stuckRepeat = 0

    var unstuckLowSpeedCount = 0
    var unstuckNoDisplacementCount = 0
    var unstuckOnFlipperCount = 0
    var unstuckOutOfBoundsCount = 0
    var unstuckNaNCount = 0
    var maxRecordedSpeed = 0f

    init {
        buildField()
        spawnBall()
    }

    private fun addSeg(ax: Float, ay: Float, bx: Float, by: Float, half: Float, rest: Float, kind: EntityType = EntityType.WALL, id: Int = 0) {
        segs.add(Seg(ax, ay, bx, by, half, rest, kind, id))
    }

    private fun buildField() {
        val t = 0.2f
        // боковые стенки
        addSeg(-t, 4.5f, -t, 13.0f, t, 0.35f)
        addSeg(9f + t, 4.5f, 9f + t, 13.0f, t, 0.35f)
        // верхняя арка (внутренний радиус 4.5, центр (4.5, 4.5))
        val n = 32
        val rc = 4.5f + t
        for (i in 0 until n) {
            val a1 = Math.PI - i * Math.PI / n
            val a2 = Math.PI - (i + 1) * Math.PI / n
            addSeg(
                (4.5 + rc * cos(a1)).toFloat(), (4.5 - rc * sin(a1)).toFloat(),
                (4.5 + rc * cos(a2)).toFloat(), (4.5 - rc * sin(a2)).toFloat(),
                t, 0.4f
            )
        }
        // скаты к лапкам
        addSeg(0f, 13.0f, leftPivot.x, leftPivot.y, t, 0.3f)
        addSeg(9f, 13.0f, rightPivot.x, rightPivot.y, t, 0.3f)
        // рогатки: наклонная грань «бьёт», остальные — стенки
        addSeg(1.15f, 10.6f, 2.25f, 13.35f, 0.07f, 0.55f, EntityType.SLINGSHOT, 0)
        addSeg(2.25f, 13.35f, 1.15f, 12.6f, 0.07f, 0.4f)
        addSeg(1.15f, 12.6f, 1.15f, 10.6f, 0.07f, 0.4f)
        addSeg(7.85f, 10.6f, 6.75f, 13.35f, 0.07f, 0.55f, EntityType.SLINGSHOT, 1)
        addSeg(6.75f, 13.35f, 7.85f, 12.6f, 0.07f, 0.4f)
        addSeg(7.85f, 12.6f, 7.85f, 10.6f, 0.07f, 0.4f)
    }

    fun getBalls(): List<PinBall> = balls.toList()
    fun getFlipperAngles(): Pair<Float, Float> = Pair(leftA, rightA)

    // ---------------- управление ----------------
    fun holdLeft(pressed: Boolean) { manualLeft = pressed }
    fun holdRight(pressed: Boolean) { manualRight = pressed }

    /** Кратковременный взмах (для автолапок и тестов): держится ~0.25 с. */
    fun setLeftFlipperEngaged(engaged: Boolean) { autoLeftUntil = if (engaged) simT + 0.25f else 0f }
    fun setRightFlipperEngaged(engaged: Boolean) { autoRightUntil = if (engaged) simT + 0.25f else 0f }

    private fun startPos(): PinVec = PinVec(if (rng.nextBoolean()) 2.7f else 6.3f, 2.6f)

    fun spawnBall(initialPos: PinVec = startPos()) {
        if (balls.size >= 3) balls.removeAt(0)
        val b = PinBall(initialPos.x, initialPos.y, (rng.nextFloat() - 0.5f) * 2f, 0.5f + rng.nextFloat())
        b.anchorT = simT
        balls.add(b)
        onSoundTriggered("spawn")
    }

    fun clearAllAndReset() {
        balls.clear()
        starLitStates.fill(false)
        gemLit.fill(false)
        multiplier = 1
        bumperHits = 0
        spawnBall()
    }

    private fun respawn(b: PinBall) {
        val p = startPos()
        b.px = p.x; b.py = p.y
        b.vx = (rng.nextFloat() - 0.5f) * 2f; b.vy = 1f
        b.captured = 0f
        b.anchorX = b.px; b.anchorY = b.py; b.anchorT = simT
    }

    private fun addScore(base: Int) { onScoreAdded(base * multiplier) }

    // ---------------- шаг симуляции ----------------
    private val subDt = 1f / 480f
    private var acc = 0f
    private var lastNanos = 0L

    fun step(currentTimeNanos: Long) {
        if (lastNanos == 0L) { lastNanos = currentTimeNanos; return }
        val frame = (currentTimeNanos - lastNanos) / 1_000_000_000.0f
        lastNanos = currentTimeNanos
        acc += min(frame, 0.05f)
        var guard = 0
        while (acc >= subDt && guard < 26) {
            substep(subDt)
            acc -= subDt
            guard++
        }
        if (guard >= 26) acc = 0f
    }

    private fun substep(dt: Float) {
        simT += dt
        updateFlippers(dt)
        val g = currentSpeedMode.gravity
        for (b in balls.toList()) {
            if (b.captured > 0f) {
                b.captured -= dt
                b.px += (saucerPos.x - b.px) * 0.2f; b.py += (saucerPos.y - b.py) * 0.2f
                b.vx = 0f; b.vy = 0f
                if (b.captured <= 0f) ejectFromSaucer(b)
                continue
            }
            b.vy += g * dt
            val drag = 1f - 0.05f * dt
            b.vx *= drag; b.vy *= drag
            b.px += b.vx * dt
            b.py += b.vy * dt
            collide(b)
            collide(b)
            val sp = hypot(b.vx, b.vy)
            if (sp.isNaN() || b.px.isNaN() || b.py.isNaN()) {
                unstuckNaNCount++; respawn(b); continue
            }
            if (sp > 15f) { b.vx *= 15f / sp; b.vy *= 15f / sp }
            if (sp > maxRecordedSpeed) maxRecordedSpeed = min(sp, 15f)
            b.roll += (b.vx * dt) / ballR
            sensors(b, sp)
            if (b.py > 17.7f) { onSoundTriggered("blub"); respawn(b) }
        }
        ballBall()
        for (i in coolBumper.indices) coolBumper[i] -= dt
        for (i in coolSling.indices) coolSling[i] -= dt
        for (i in bumperFlash.indices) bumperFlash[i] = max(0f, bumperFlash[i] - dt * 4f)
        for (i in slingFlash.indices) slingFlash[i] = max(0f, slingFlash[i] - dt * 5f)
        for (i in gemFlash.indices) gemFlash[i] = max(0f, gemFlash[i] - dt * 3f)
        saucerFlash = max(0f, saucerFlash - dt * 2f)
        coolWall -= dt
        saucerCool -= dt
        // спиннер крутится по инерции
        spinAngle += spinVel * dt
        spinVel *= (1f - 1.6f * dt)
        spinScoreT -= dt
        if (autoPawsEnabled) autoPaws()
        if (simT - lastUnstuck >= 0.1f) { lastUnstuck = simT; antiStuck() }
    }

    private fun updateFlippers(dt: Float) {
        val m = currentSpeedMode.flipperSpeedMultiplier
        val wantL = manualLeft || simT < autoLeftUntil
        val wantR = manualRight || simT < autoRightUntil
        if (wantL && !leftOn) onSoundTriggered("flip")
        if (wantR && !rightOn) onSoundTriggered("flip")
        leftOn = wantL; rightOn = wantR
        val up = 15f * m
        val down = 8f * m
        val prevL = leftA
        val tgtL = if (wantL) upAngle else restAngle
        val stepL = (if (wantL) up else down) * dt
        leftA = if (leftA < tgtL) min(tgtL, leftA + stepL) else max(tgtL, leftA - stepL)
        leftW = (leftA - prevL) / dt
        val prevR = rightA
        val tgtR = if (wantR) -upAngle else -restAngle
        val stepR = (if (wantR) up else down) * dt
        rightA = if (rightA < tgtR) min(tgtR, rightA + stepR) else max(tgtR, rightA - stepR)
        rightW = (rightA - prevR) / dt
    }

    private fun flipperTip(left: Boolean): PinVec =
        if (left) PinVec(leftPivot.x + flipLen * cos(leftA), leftPivot.y + flipLen * sin(leftA))
        else PinVec(rightPivot.x - flipLen * cos(rightA), rightPivot.y - flipLen * sin(rightA))

    private fun distToSeg(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val l2 = dx * dx + dy * dy
        val t = if (l2 > 0f) (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0f, 1f) else 0f
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    private fun autoPaws() {
        val lt = flipperTip(true); val rt = flipperTip(false)
        for (b in balls) {
            if (b.captured > 0f || b.vy < -1.0f) { b.restT = 0f; continue }
            if (b.py < 13.8f) { b.restT = 0f; continue }
            val dl = distToSeg(b.px, b.py, leftPivot.x, leftPivot.y, lt.x, lt.y)
            val dr = distToSeg(b.px, b.py, rightPivot.x, rightPivot.y, rt.x, rt.y)
            if (b.px < 4.5f && dl < 1.0f) autoLeftUntil = max(autoLeftUntil, simT + 0.22f)
            if (b.px >= 4.5f && dr < 1.0f) autoRightUntil = max(autoRightUntil, simT + 0.22f)
            // лежит на лапке без движения — подкинуть
            if (hypot(b.vx, b.vy) < 1.0f) {
                b.restT += subDt
                if (b.restT > 0.5f) {
                    if (b.px < 4.5f) autoLeftUntil = simT + 0.25f else autoRightUntil = simT + 0.25f
                    b.restT = 0f
                }
            } else b.restT = 0f
        }
    }

    // ---------------- столкновения ----------------
    private fun collide(b: PinBall) {
        for (s in segs) hitSeg(b, s.ax, s.ay, s.bx, s.by, s.half, s.rest, s, 0f, 0f, 0f)
        val lTip = flipperTip(true)
        hitSeg(b, leftPivot.x, leftPivot.y, lTip.x, lTip.y, flipHalf, 0.25f, null, leftW, leftPivot.x, leftPivot.y)
        val rTip = flipperTip(false)
        hitSeg(b, rightPivot.x, rightPivot.y, rTip.x, rTip.y, flipHalf, 0.25f, null, rightW, rightPivot.x, rightPivot.y)
        // отбойники
        for (i in bumperPositions.indices) {
            val c = bumperPositions[i]
            var nx = b.px - c.x; var ny = b.py - c.y
            val d = hypot(nx, ny)
            val R = bumperR + ballR
            if (d < R) {
                if (d < 1e-4f) { nx = 0f; ny = -1f } else { nx /= d; ny /= d }
                val j = (rng.nextFloat() - 0.5f) * 0.4f
                val cj = cos(j); val sj = sin(j)
                val kx = nx * cj - ny * sj; val ky = nx * sj + ny * cj
                b.px = c.x + nx * R; b.py = c.y + ny * R
                val out = max(7.5f, b.vx * kx + b.vy * ky)
                b.vx = kx * out; b.vy = ky * out
                if (coolBumper[i] <= 0f) {
                    coolBumper[i] = 0.12f
                    bumperFlash[i] = 1f
                    addScore(10); onSoundTriggered("bumper"); onBumperHit(i)
                    bumperHits++
                    if (bumperHits % 8 == 0 && multiplier < 5) { multiplier++; onMultiplier(multiplier) }
                }
            }
        }
        // кристаллы-мишени на стенках
        for (i in gemPositions.indices) {
            val c = gemPositions[i]
            var nx = b.px - c.x; var ny = b.py - c.y
            val d = hypot(nx, ny)
            val R = gemR + ballR
            if (d < R) {
                if (d < 1e-4f) { nx = 1f; ny = 0f } else { nx /= d; ny /= d }
                b.px = c.x + nx * R; b.py = c.y + ny * R
                val vn = b.vx * nx + b.vy * ny
                if (vn < 0f) { b.vx -= 1.5f * vn * nx; b.vy -= 1.5f * vn * ny }
                if (!gemLit[i]) {
                    gemLit[i] = true
                    gemFlash[i] = 1f
                    addScore(15); onSoundTriggered("bell_ding"); onGemHit(i)
                    if (gemLit.all { it }) {
                        gemLit.fill(false)
                        addScore(100)
                        onSoundTriggered("fanfare_short"); onAllGemsLit()
                        if (balls.size < 3) spawnBall() // мультибол!
                    }
                } else if (vn < -1f) { gemFlash[i] = 0.6f; onSoundTriggered("tap") }
            }
        }
    }

    /** Круг (шарик) против «толстого» отрезка. w — угловая скорость, если отрезок — лапка с осью (pvx, pvy). */
    private fun hitSeg(b: PinBall, ax: Float, ay: Float, bx: Float, by: Float, half: Float, e: Float, s: Seg?, w: Float, pvx: Float, pvy: Float) {
        val dx = bx - ax; val dy = by - ay
        val l2 = dx * dx + dy * dy
        val t = if (l2 > 0f) (((b.px - ax) * dx + (b.py - ay) * dy) / l2).coerceIn(0f, 1f) else 0f
        val cx = ax + t * dx; val cy = ay + t * dy
        var nx = b.px - cx; var ny = b.py - cy
        val d = hypot(nx, ny)
        val R = ballR + half
        if (d >= R) return
        if (d < 1e-5f) {
            val ln = sqrt(l2)
            nx = -dy / ln; ny = dx / ln
        } else { nx /= d; ny /= d }
        b.px = cx + nx * R; b.py = cy + ny * R
        // скорость поверхности в точке касания (лапка вращается)
        val vsx = -w * (cy - pvy)
        val vsy = w * (cx - pvx)
        val rvx = b.vx - vsx; val rvy = b.vy - vsy
        val vn = rvx * nx + rvy * ny
        if (vn >= 0f) return
        val bounce = if (abs(vn) < 0.9f) 0f else e   // тихо лежащий шарик не дрожит
        b.vx -= (1f + bounce) * vn * nx
        b.vy -= (1f + bounce) * vn * ny
        if (s != null && s.kind == EntityType.SLINGSHOT) {
            val id = s.id
            if (coolSling[id] <= 0f) {
                coolSling[id] = 0.15f
                slingFlash[id] = 1f
                val kick = 7.0f
                val on = b.vx * nx + b.vy * ny
                if (on < kick) { b.vx += nx * (kick - on); b.vy += ny * (kick - on) }
                addScore(5); onSoundTriggered("sling")
            }
        } else if (s != null && coolWall <= 0f && abs(vn) > 1.8f) {
            coolWall = 0.1f
            onSoundTriggered("wall")
        }
    }

    /** Простое упругое столкновение шариков между собой (мультибол). */
    private fun ballBall() {
        if (balls.size < 2) return
        for (i in 0 until balls.size) for (j in i + 1 until balls.size) {
            val a = balls[i]; val c = balls[j]
            if (a.captured > 0f || c.captured > 0f) continue
            var nx = c.px - a.px; var ny = c.py - a.py
            val d = hypot(nx, ny)
            val R = ballR * 2f
            if (d >= R) continue
            if (d < 1e-5f) { nx = 1f; ny = 0f } else { nx /= d; ny /= d }
            val push = (R - d) / 2f
            a.px -= nx * push; a.py -= ny * push; c.px += nx * push; c.py += ny * push
            val rv = (c.vx - a.vx) * nx + (c.vy - a.vy) * ny
            if (rv < 0f) {
                val imp = -rv * 0.95f
                a.vx -= imp * nx; a.vy -= imp * ny; c.vx += imp * nx; c.vy += imp * ny
            }
        }
    }

    // ---------------- датчики: ролловеры, спиннер, воронка ----------------
    private fun sensors(b: PinBall, sp: Float) {
        for (i in starPositions.indices) {
            if (!starLitStates[i] && hypot(b.px - starPositions[i].x, b.py - starPositions[i].y) < 0.8f) {
                starLitStates[i] = true
                addScore(20); onSoundTriggered("chime"); onStarHit(i)
                if (starLitStates.all { it }) {
                    onSoundTriggered("fanfare_short"); onAllStarsLit(); starLitStates.fill(false)
                    if (multiplier < 5) { multiplier++; onMultiplier(multiplier) }
                }
            }
        }
        if (hypot(b.px - spinnerPos.x, b.py - spinnerPos.y) < spinnerR) {
            spinVel = min(40f, max(spinVel, sp * 4f))
            if (spinScoreT <= 0f) { spinScoreT = 0.12f; addScore(2); onSoundTriggered("tap") }
        }
        if (saucerCool <= 0f && sp < 10f && hypot(b.px - saucerPos.x, b.py - saucerPos.y) < saucerR) {
            b.captured = 0.9f
            saucerFlash = 1f
            addScore(50); onSoundTriggered("whoosh"); onSaucer(true)
        }
    }

    private fun ejectFromSaucer(b: PinBall) {
        val a = (-Math.PI / 2.0 + (rng.nextFloat() - 0.5f) * 0.9).toFloat()
        b.vx = cos(a) * 12.5f; b.vy = sin(a) * 12.5f
        b.px = saucerPos.x; b.py = saucerPos.y - saucerR
        saucerCool = 1.2f
        saucerFlash = 1f
        b.anchorX = b.px; b.anchorY = b.py; b.anchorT = simT
        onSoundTriggered("boing"); onSaucer(false)
    }

    // ---------------- защита от застревания (страховка) ----------------
    private fun antiStuck() {
        for (b in balls.toList()) {
            if (b.captured > 0f) { b.anchorX = b.px; b.anchorY = b.py; b.anchorT = simT; continue }
            if (b.px < -1f || b.px > worldWidth + 1f || b.py < -1.5f || b.py > worldHeight + 1.5f) {
                unstuckOutOfBoundsCount++; respawn(b); continue
            }
            val moved = hypot(b.px - b.anchorX, b.py - b.anchorY)
            if (moved > 0.35f) { b.anchorX = b.px; b.anchorY = b.py; b.anchorT = simT; if (moved > 1.2f) stuckRepeat = 0; continue }
            val still = simT - b.anchorT
            val sp = hypot(b.vx, b.vy)
            if (b.py < 14.0f) {
                if (still > 0.6f) {
                    if (sp < 0.6f) unstuckLowSpeedCount++ else unstuckNoDisplacementCount++
                    stuckRepeat++
                    val kick = min(3, stuckRepeat)
                    b.vx += (if (b.px < 4.5f) 1f else -1f) * (2.5f + kick) + (rng.nextFloat() - 0.5f)
                    b.vy = -(4f + 2f * kick)
                    // шарик прижат к краю — чуть сдвинуть к центру, чтобы не вернулся в тот же угол
                    b.px += (4.5f - b.px) * 0.05f
                    b.anchorX = b.px; b.anchorY = b.py; b.anchorT = simT
                }
            } else if (still > 1.0f) {
                unstuckOnFlipperCount++
                if (b.px < 4.5f) autoLeftUntil = simT + 0.3f else autoRightUntil = simT + 0.3f
                b.anchorX = b.px; b.anchorY = b.py; b.anchorT = simT
            }
        }
    }
}
