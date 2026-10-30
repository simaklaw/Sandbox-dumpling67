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

/** Шарик. Координаты — в «мировых» единицах поля 9×16, ось Y вниз. */
class PinBall(var px: Float, var py: Float, var vx: Float, var vy: Float) {
    val position: PinVec get() = PinVec(px, py)
    internal var anchorX = px
    internal var anchorY = py
    internal var anchorT = 0f
}

/**
 * Собственная лёгкая физика пинбола (круг против отрезков и кругов) вместо jbox2d.
 * Лапки — вращающиеся «капсулы», которые передают шарику свою скорость, поэтому удар получается
 * настоящим. Шаг 1/240 с исключает проскакивание сквозь стенки, а все «липкие» места исключены
 * активными отбойниками и защитой от застревания.
 */
class PinballPhysicsEngine(
    val onScoreAdded: (Int) -> Unit,
    val onSoundTriggered: (String) -> Unit,
    val onBumperHit: (Int) -> Unit,
    val onStarHit: (Int) -> Unit,
    val onAllStarsLit: () -> Unit
) {
    val worldWidth = 9.0f
    val worldHeight = 16.0f

    private class Seg(
        val ax: Float, val ay: Float, val bx: Float, val by: Float,
        val half: Float, val rest: Float, val kind: EntityType, val id: Int = 0
    )

    val bumperPositions = listOf(
        PinVec(2.8f, 5.0f), PinVec(6.2f, 5.0f), PinVec(4.5f, 6.8f), PinVec(4.5f, 8.9f)
    )
    private val bumperR = 0.7f
    private val starPositions = listOf(PinVec(3.0f, 2.0f), PinVec(4.5f, 1.2f), PinVec(6.0f, 2.0f))
    private val ballR = 0.4f

    private val segs = ArrayList<Seg>()
    private val balls = ArrayList<PinBall>()

    var currentSpeedMode: SpeedMode = SpeedMode.BUNNY
    var autoPawsEnabled: Boolean = true
    val starLitStates = booleanArrayOf(false, false, false)

    // ---- лапки ----
    private val leftPivot = PinVec(2.2f, 14.0f)
    private val rightPivot = PinVec(6.8f, 14.0f)
    private val flipLen = 2.0f
    private val flipHalf = 0.23f
    private val restAngle = 0.52f   // конец лапки смотрит вниз к центру
    private val upAngle = -0.50f    // поднятая лапка
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
        // боковые стенки (чуть за пределами поля, чтобы шарик не выпал)
        addSeg(-t, 2.0f, -t, 12.0f, t, 0.35f)
        addSeg(9f + t, 2.0f, 9f + t, 12.0f, t, 0.35f)
        // верхняя арка: полуокружность радиуса 4.5 с центром (4.5, 3.0)
        val n = 24
        for (i in 0 until n) {
            val a1 = Math.PI - i * Math.PI / n
            val a2 = Math.PI - (i + 1) * Math.PI / n
            addSeg(
                (4.5 + 4.5 * cos(a1)).toFloat(), (3.0 - 4.5 * sin(a1)).toFloat(),
                (4.5 + 4.5 * cos(a2)).toFloat(), (3.0 - 4.5 * sin(a2)).toFloat(),
                t, 0.4f
            )
        }
        // скаты к лапкам
        addSeg(-t, 12.0f, 2.2f, 14.0f, t, 0.3f)
        addSeg(9f + t, 12.0f, 6.8f, 14.0f, t, 0.3f)
        // рогатки (треугольники над лапками) — активно подбрасывают шарик
        addSeg(1.1f, 9.6f, 2.0f, 11.2f, 0.06f, 0.6f, EntityType.SLINGSHOT, 0)
        addSeg(2.0f, 11.2f, 1.1f, 11.4f, 0.06f, 0.6f, EntityType.SLINGSHOT, 0)
        addSeg(1.1f, 11.4f, 1.1f, 9.6f, 0.06f, 0.6f, EntityType.WALL)
        addSeg(7.9f, 9.6f, 7.0f, 11.2f, 0.06f, 0.6f, EntityType.SLINGSHOT, 1)
        addSeg(7.0f, 11.2f, 7.9f, 11.4f, 0.06f, 0.6f, EntityType.SLINGSHOT, 1)
        addSeg(7.9f, 11.4f, 7.9f, 9.6f, 0.06f, 0.6f, EntityType.WALL)
    }

    fun getBalls(): List<PinBall> = balls.toList()
    fun getFlipperAngles(): Pair<Float, Float> = Pair(leftA, rightA)

    // ---------------- управление ----------------
    fun holdLeft(pressed: Boolean) { manualLeft = pressed }
    fun holdRight(pressed: Boolean) { manualRight = pressed }

    /** Кратковременный взмах (для автолапок и тестов): держится ~0.25 с. */
    fun setLeftFlipperEngaged(engaged: Boolean) { autoLeftUntil = if (engaged) simT + 0.25f else 0f }
    fun setRightFlipperEngaged(engaged: Boolean) { autoRightUntil = if (engaged) simT + 0.25f else 0f }

    fun spawnBall(initialPos: PinVec = PinVec(4.5f + (Random.nextFloat() - 0.5f) * 3f, 3.2f)) {
        if (balls.size >= 3) balls.removeAt(0)
        val b = PinBall(initialPos.x, initialPos.y, (Random.nextFloat() - 0.5f) * 3f, 0.5f + Random.nextFloat())
        b.anchorT = simT
        balls.add(b)
        onSoundTriggered("spawn")
    }

    fun clearAllAndReset() {
        balls.clear()
        starLitStates.fill(false)
        spawnBall()
    }

    private fun respawn(b: PinBall) {
        b.px = 4.5f + (Random.nextFloat() - 0.5f) * 3f; b.py = 3.2f
        b.vx = (Random.nextFloat() - 0.5f) * 3f; b.vy = 1f
        b.anchorX = b.px; b.anchorY = b.py; b.anchorT = simT
    }

    // ---------------- шаг симуляции ----------------
    private val subDt = 1f / 240f
    private var acc = 0f
    private var lastNanos = 0L

    fun step(currentTimeNanos: Long) {
        if (lastNanos == 0L) { lastNanos = currentTimeNanos; return }
        val frame = (currentTimeNanos - lastNanos) / 1_000_000_000.0f
        lastNanos = currentTimeNanos
        acc += min(frame, 0.05f)
        var guard = 0
        while (acc >= subDt && guard < 14) {
            substep(subDt)
            acc -= subDt
            guard++
        }
    }

    private fun substep(dt: Float) {
        simT += dt
        updateFlippers(dt)
        val g = currentSpeedMode.gravity
        for (b in balls.toList()) {
            b.vy += g * dt
            b.px += b.vx * dt
            b.py += b.vy * dt
            collide(b)
            val sp = hypot(b.vx, b.vy)
            if (sp.isNaN() || b.px.isNaN() || b.py.isNaN()) {
                unstuckNaNCount++; respawn(b); continue
            }
            if (sp > 15f) { b.vx *= 15f / sp; b.vy *= 15f / sp }
            if (sp > maxRecordedSpeed) maxRecordedSpeed = min(sp, 15f)
            // звёзды
            for (i in starPositions.indices) {
                if (!starLitStates[i] && hypot(b.px - starPositions[i].x, b.py - starPositions[i].y) < 0.8f) {
                    starLitStates[i] = true
                    onScoreAdded(20); onSoundTriggered("chime"); onStarHit(i)
                    if (starLitStates.all { it }) {
                        onSoundTriggered("fanfare_short"); onAllStarsLit(); starLitStates.fill(false)
                    }
                }
            }
            // слив
            if (b.py > 15.6f) { onSoundTriggered("blub"); respawn(b) }
        }
        for (i in coolBumper.indices) coolBumper[i] -= dt
        for (i in coolSling.indices) coolSling[i] -= dt
        coolWall -= dt
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
        val up = 17f * m
        val down = 9f * m
        // лева: угол уменьшается при подъёме; права: увеличивается
        val prevL = leftA
        val tgtL = if (wantL) upAngle else restAngle
        val stepL = (if (wantL) up else down) * dt
        leftA = when {
            leftA < tgtL -> min(tgtL, leftA + stepL)
            else -> max(tgtL, leftA - stepL)
        }
        leftW = (leftA - prevL) / dt
        val prevR = rightA
        val tgtR = if (wantR) -upAngle else -restAngle
        val stepR = (if (wantR) up else down) * dt
        rightA = when {
            rightA < tgtR -> min(tgtR, rightA + stepR)
            else -> max(tgtR, rightA - stepR)
        }
        rightW = (rightA - prevR) / dt
    }

    private fun autoPaws() {
        for (b in balls) {
            if (b.vy > 0.3f && b.py in 12.2f..14.9f) {
                if (b.px in 1.4f..4.5f) autoLeftUntil = max(autoLeftUntil, simT + 0.22f)
                if (b.px in 4.5f..7.6f) autoRightUntil = max(autoRightUntil, simT + 0.22f)
            }
        }
    }

    // ---------------- столкновения ----------------
    private fun collide(b: PinBall) {
        for (s in segs) hitSeg(b, s.ax, s.ay, s.bx, s.by, s.half, s.rest, s, 0f, 0f, 0f)
        // лапки
        val lTip = PinVec(leftPivot.x + flipLen * cos(leftA), leftPivot.y + flipLen * sin(leftA))
        hitSeg(b, leftPivot.x, leftPivot.y, lTip.x, lTip.y, flipHalf, 0.3f, null, leftW, leftPivot.x, leftPivot.y)
        val rTip = PinVec(rightPivot.x - flipLen * cos(rightA), rightPivot.y - flipLen * sin(rightA))
        hitSeg(b, rightPivot.x, rightPivot.y, rTip.x, rTip.y, flipHalf, 0.3f, null, rightW, rightPivot.x, rightPivot.y)
        // отбойники
        for (i in bumperPositions.indices) {
            val c = bumperPositions[i]
            var nx = b.px - c.x; var ny = b.py - c.y
            val d = hypot(nx, ny)
            val R = bumperR + ballR
            if (d < R) {
                if (d < 1e-4f) { nx = 0f; ny = -1f } else { nx /= d; ny /= d }
                // небольшой случайный разворот нормали — шарик не может «усидеть» точно на макушке
                val j = (Random.nextFloat() - 0.5f) * 0.5f
                val cj = cos(j); val sj = sin(j)
                val kx = nx * cj - ny * sj; val ky = nx * sj + ny * cj
                b.px = c.x + nx * R; b.py = c.y + ny * R
                val out = max(7.0f, b.vx * kx + b.vy * ky)
                b.vx = kx * out; b.vy = ky * out
                if (coolBumper[i] <= 0f) {
                    coolBumper[i] = 0.12f
                    onScoreAdded(10); onSoundTriggered("bumper"); onBumperHit(i)
                }
            }
        }
    }

    /** Круг (шарик) против «толстого» отрезка. w — угловая скорость, если отрезок — лапка с осью (pxv, pyv). */
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
        val bounce = if (abs(vn) < 0.8f) 0f else e   // тихо лежащий шарик не дрожит
        b.vx -= (1f + bounce) * vn * nx
        b.vy -= (1f + bounce) * vn * ny
        // лёгкое трение вдоль поверхности
        if (s != null && s.kind == EntityType.SLINGSHOT) {
            val id = s.id
            if (coolSling[id] <= 0f) {
                coolSling[id] = 0.15f
                val kick = 6.5f
                val on = b.vx * nx + b.vy * ny
                if (on < kick) { b.vx += nx * (kick - on); b.vy += ny * (kick - on) }
                onScoreAdded(5); onSoundTriggered("sling")
            }
        } else if (s != null && coolWall <= 0f && abs(vn) > 1.5f) {
            coolWall = 0.1f
            onSoundTriggered("wall")
        }
    }

    // ---------------- защита от застревания ----------------
    private fun antiStuck() {
        for (b in balls.toList()) {
            if (b.px < -1f || b.px > worldWidth + 1f || b.py < -1.5f || b.py > worldHeight + 1.5f) {
                unstuckOutOfBoundsCount++; respawn(b); continue
            }
            val moved = hypot(b.px - b.anchorX, b.py - b.anchorY)
            if (moved > 0.35f) { b.anchorX = b.px; b.anchorY = b.py; b.anchorT = simT; continue }
            val still = simT - b.anchorT
            val sp = hypot(b.vx, b.vy)
            if (b.py < 13.0f) {
                if (still > 0.9f) {
                    if (sp < 0.6f) unstuckLowSpeedCount++ else unstuckNoDisplacementCount++
                    b.vx += if (Random.nextBoolean()) 2.5f else -2.5f
                    b.vy = -3.5f
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
