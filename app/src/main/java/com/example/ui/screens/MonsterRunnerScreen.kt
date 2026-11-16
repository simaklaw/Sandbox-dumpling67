package com.example.ui.screens

import com.example.shared.withFrameNanosEco
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.shared.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * «Забег монстрика» — боковой раннер. Бежит монстрик, собранный в Фабрике.
 * Тап — прыжок (в воздухе — ещё один). Собирай монетки, перепрыгивай бочки и ящики.
 * Локации меняются по ходу забега: Поля → Лес → Замок → снова Поля…
 */

private const val RUN_LOC_LEN = 3600f      // dp на одну локацию
private const val RUN_GRAVITY = 2300f
private const val RUN_JUMP_V = 800f
private const val RUN_JUMP2_V = 660f
private const val RUN_K = 2.6f             // масштаб пиксель-арт предметов

private class RunObs(val x: Float, val kind: Int, val w: Float, val h: Float)
private class RunCoin(val x: Float, val y: Float) { var taken = false }
private class RunFire(var x: Float, val y: Float, var t: Float = 0f)
private class RunFx(val x: Float, val y: Float, val n: Int)

/** Выстрел злодеев. kind: 0 стрела, 1 звезда, 2 порыв духа, 3 наземная волна босса (её не сбить огнём — только перепрыгнуть). */
private class RunShot(var x: Float, val y: Float, val vx: Float, val kind: Int, var t: Float = 0f)

/**
 * Злодей-скелет. kind: 0 пехотинец (меч), 1 прыгун, 2 щитоносец (3 жизни), 3 лучник (стоит и стреляет),
 * 4 злой дух (парит и бросает порывы). Лучник и дух привязаны к экрану (sx), остальные идут по трассе.
 */
private class RunFoe(var x: Float, val kind: Int, val loc: Int, var sx: Float = 0f) {
    val w = when (kind) { 2 -> 70f; 4 -> 66f; else -> 52f }
    val h = when (kind) { 2 -> 104f; 4 -> 74f; 1 -> 86f; else -> 90f }
    var hp = when (kind) { 2 -> 3; 4 -> 2; else -> 1 }
    val maxHp = hp
    var y = if (kind == 4) 120f else 0f
    var vy = 0f
    var hopT = 0.6f
    var flash = 0f
    var dead = false
    var cd = 1.4f + Random.nextFloat() * 1.2f
    var aim = 0f          // 0..1: замах/прицеливание перед выстрелом
    var aimY = 40f
    var age = Random.nextFloat() * 6f
    val screenBound get() = kind >= 3
    val style: SkelStyle = skelStyle(
        weapon = when (kind) { 0 -> 0; 1 -> 6; 2 -> 1; else -> 4 },
        hat = (kind + loc) % 4,
        biome = loc,
        shield = kind == 2
    )
}

private val RUN_BOSS_NAMES = listOf("СКЕЛЕТ-ВОЖАК", "ЛЕСНОЙ ЛИХ", "КОРОЛЬ СКЕЛЕТОВ", "ВУЛКАННЫЙ ПОВЕЛИТЕЛЬ")
private val RUN_BOSS_COL = longArrayOf(0xFFFFB74D, 0xFF9CCC65, 0xFFB388FF, 0xFFFF7043)
private val RUN_BOSS_ICON = listOf("💀", "🌲", "👑", "🌋")

private class RunWorld(val toddler: Boolean) {
    var dist = 0f
    var speed = 0f
    var hy = 0f
    var vy = 0f
    var airJumps = 0
    var lives = 3
    var coins = 0
    var invuln = 0f
    var slow = 0f
    var over = false
    var paused = false
    var time = 0f
    var nextX = 700f
    var loc = 0
    var squash = 0f
    var wasAir = false
    var kills = 0
    var bossesDown = 0
    var fireCd = 0f
    var muzzle = 0f
    var hxw = 0f
    var vw = 800f
    var shake = 0f
    // босс: 0 нет, 1 выход, 2 бой, 3 гибель
    var bossState = 0
    var bossHp = 0
    var bossMax = 1
    var bossPhase = 0
    var bossT = 0f
    var bossFlash = 0f
    var bossBlock = 0f
    var bossLap = -1
    var bossX = 0f            // положение на экране, dp
    var bossMode = 0          // 0 ходит под щитом, 1 готовит удар, 2 бьёт, 3 открыт для огня
    var bossModeT = 0f
    var bossAtk = 0
    var victory = false
    val foes = ArrayList<RunFoe>()
    val fires = ArrayList<RunFire>()
    val shots = ArrayList<RunShot>()
    val fx = ArrayList<RunFx>()
    val obs = ArrayList<RunObs>()
    val items = ArrayList<RunCoin>()
    var onEvent: (String) -> Unit = {}

    val fireDelay get() = if (toddler) 0.6f else 0.42f
    val bossShielded get() = bossMode != 3
    val cycleLen get() = BIOME_COUNT * RUN_LOC_LEN
    /** Доля пути до босса в текущем круге (для полоски прогресса). */
    fun progress(): Float = if (bossState != 0) 1f else ((dist % cycleLen) / cycleLen).coerceIn(0f, 1f)

    fun start(startLoc: Int, visibleW: Float) {
        dist = startLoc * RUN_LOC_LEN
        loc = startLoc
        hy = 0f; vy = 0f; airJumps = 0
        lives = 3; coins = 0; invuln = 0f; slow = 0f; over = false; paused = false; time = 0f
        obs.clear(); items.clear(); foes.clear(); fires.clear(); fx.clear(); shots.clear()
        bossState = 0; bossLap = startLoc / BIOME_COUNT - 1; bossFlash = 0f; victory = false
        kills = 0; bossesDown = 0; fireCd = 0f; muzzle = 0f; shake = 0f
        nextX = dist + visibleW + 300f
    }

    fun jump() {
        if (over || paused) return
        if (hy <= 0.5f) {
            vy = RUN_JUMP_V; airJumps = 1; squash = -0.4f; onEvent("jump")
        } else if (airJumps > 0) {
            vy = RUN_JUMP2_V; airJumps = 0; squash = -0.3f; onEvent("jump2")
        }
    }

    private fun baseSpeed(): Float {
        val t = if (toddler) 150f else 230f
        val cap = if (toddler) 230f else 400f
        return min(cap, t + dist / 60f)
    }

    /** Какого злодея выпустить: чем дальше забег, тем разнообразнее и злее. */
    private fun pickFoeKind(): Int {
        val d = dist % cycleLen
        val pool = ArrayList<Int>()
        pool.add(0); pool.add(0); pool.add(1)
        if (dist > 1200f) pool.add(3)
        if (dist > 2400f) pool.add(2)
        if (d > RUN_LOC_LEN * 1.0f || dist > cycleLen) pool.add(4)
        if (toddler) return pool.filter { it != 4 }.random()
        return pool.random()
    }

    private fun spawnFoe(x: Float, visibleW: Float) {
        val k = pickFoeKind()
        val f = RunFoe(x, k, loc)
        if (f.screenBound) {
            f.sx = visibleW + 80f
            f.x = dist + f.sx
        }
        foes.add(f)
    }

    private fun spawn(visibleW: Float) {
        while (nextX < dist + visibleW + 400f) {
            val r = Random.nextFloat()
            val kind = Random.nextInt(4)
            val w: Float
            val h: Float
            when (kind) {
                0 -> { w = 23f * RUN_K; h = 26f * RUN_K }   // бочка
                1 -> { w = 26f * RUN_K; h = 18f * RUN_K }   // ящик
                2 -> { w = 18f * RUN_K; h = 22f * RUN_K }   // ваза
                else -> { w = 23f * RUN_K; h = 15f * RUN_K } // сундук
            }
            val sp = baseSpeed()
            if (r < 0.30f) {
                obs.add(RunObs(nextX, kind, w, h))
                for (k in 0..4) {
                    val a = k / 4f * PI.toFloat()
                    items.add(RunCoin(nextX + w / 2f - 120f + k * 60f, h + 40f + sin(a) * 80f))
                }
            } else if (r < 0.40f && !toddler) {
                obs.add(RunObs(nextX, kind, w, h))
                obs.add(RunObs(nextX + w + 190f, (kind + 1) % 4, w, h))
                for (k in 0..6) {
                    val a = k / 6f * PI.toFloat()
                    items.add(RunCoin(nextX + w / 2f - 40f + k * 55f, h + 40f + sin(a) * 95f))
                }
                nextX += w + 190f
            } else if (r < 0.88f) {
                spawnFoe(nextX, visibleW)
                if (!toddler && Random.nextFloat() < 0.35f) {
                    val k2 = Random.nextInt(2)
                    foes.add(RunFoe(nextX + 190f, k2, loc))
                }
            } else {
                for (k in 0..5) items.add(RunCoin(nextX + k * 62f, 36f + (if (k % 2 == 0) 0f else 20f)))
            }
            nextX += (sp * (if (toddler) 1.5f else 1.0f) + 250f) * (0.9f + Random.nextFloat() * 0.6f)
        }
    }

    private fun hurt() {
        invuln = 1.4f
        slow = 0.7f
        vy = 380f
        if (hy <= 0f) hy = 1f
        shake = max(shake, 0.35f)
        if (!toddler) {
            lives--
            if (lives <= 0) { over = true; onEvent("over") }
        }
        onEvent("hit")
    }

    fun fire() {
        if (over || paused || fireCd > 0f) return
        fireCd = fireDelay
        muzzle = 0.18f
        fires.add(RunFire(hxw + 50f, hy + 64f))
        onEvent("fire")
    }

    fun bossAbs(): Float = dist + bossX
    fun bossH(): Float = 250f

    // ---------- босс ----------
    private fun bossDur(base: Float): Float = base * (1f - 0.15f * bossPhase)

    private fun bossBegin(m: Int) {
        bossMode = m
        bossModeT = 0f
        when (m) {
            1 -> { bossAtk = pickBossAttack(); onEvent("bossAtk") }
            2 -> bossStrike()
            3 -> onEvent("bossOpen")
        }
    }

    private fun pickBossAttack(): Int = when (bossPhase) {
        0 -> Random.nextInt(2)
        1 -> Random.nextInt(3).coerceAtMost(1) + (if (Random.nextFloat() < 0.3f) 2 else 0)   // 0, 1 или комбо (2)
        else -> Random.nextInt(4)
    }

    /** 0 волна по земле, 1 залп звёзд, 2 комбо волна+залп, 3 призыв скелетов. */
    private fun bossStrike() {
        val ax = bossAbs() - 50f
        val v = 300f + 40f * bossPhase
        if (bossAtk == 0 || bossAtk == 2) shots.add(RunShot(ax, 0f, -v, 3))
        if (bossAtk == 1 || bossAtk == 2) {
            val n = 2 + bossPhase
            for (i in 0 until n) {
                val hi = if (i % 2 == 0) 40f else 135f
                shots.add(RunShot(ax + 20f + i * 150f, hi, -(v + 40f), 1))
            }
        }
        if (bossAtk == 3) {
            for (i in 0..1) foes.add(RunFoe(bossAbs() - 90f - i * 90f, 0, loc))
        }
        shake = max(shake, 0.25f)
        onEvent("bossStrike")
    }

    private fun updateBoss(dt: Float, visibleW: Float, hx: Float) {
        bossFlash = max(0f, bossFlash - dt)
        bossBlock = max(0f, bossBlock - dt)
        val lap = (dist / cycleLen).toInt()
        // старт: конец последней локации круга
        if (bossState == 0 && lap != bossLap && dist - lap * cycleLen >= cycleLen - RUN_LOC_LEN * 0.30f) {
            bossLap = lap
            bossState = 1
            bossT = 0f
            bossMax = if (toddler) 10 else 18
            bossHp = bossMax
            bossPhase = 0
            bossX = visibleW + 260f
            nextX = 1e9f
            foes.clear(); shots.clear()
            obs.removeAll { it.x > dist + visibleW * 0.6f }
            shake = 1f
            onEvent("bossStart")
        }
        if (bossState == 0) return
        val rest = visibleW - 190f
        when (bossState) {
            1 -> { // выход на арену: неуязвим
                bossT += dt
                bossX += (rest - bossX) * min(1f, dt * 2.2f)
                if (bossT > 2.2f) { bossState = 2; bossBegin(0) }
            }
            2 -> {
                bossModeT += dt
                bossX = rest + if (bossMode == 0) sin(time * 1.6f) * 22f else 0f
                when (bossMode) {
                    0 -> if (bossModeT >= bossDur(1.7f)) bossBegin(1)
                    1 -> if (bossModeT >= bossDur(0.95f)) bossBegin(2)
                    2 -> if (bossModeT >= 0.55f) bossBegin(3)
                    3 -> if (bossModeT >= bossDur(3.4f)) bossBegin(0)
                }
                for (p in fires) {
                    if (abs(p.x - bossAbs()) < 95f && p.y > 0f && p.y < bossH()) {
                        p.x = 1e9f
                        if (bossMode == 3) {
                            bossHp--
                            bossFlash = 0.15f
                            fx.add(RunFx(bossAbs() - 40f, p.y, 6))
                            if (bossHp <= 0) {
                                bossState = 3; bossT = 0f; shots.clear()
                                break
                            }
                            val np = if (bossHp <= bossMax / 3) 2 else if (bossHp <= bossMax * 2 / 3) 1 else 0
                            if (np != bossPhase) {
                                bossPhase = np
                                shake = 0.8f
                                onEvent("bossPhase")
                                bossBegin(0)
                            } else onEvent("bossHit")
                        } else {
                            bossBlock = 0.25f
                            fx.add(RunFx(bossAbs() - 60f, p.y, 3))
                            onEvent("bossBlock")
                        }
                        break
                    }
                }
            }
            3 -> {
                bossT += dt
                shake = max(shake, 0.2f)
                if (((bossT * 6f).toInt()) != (((bossT - dt) * 6f).toInt())) {
                    fx.add(RunFx(bossAbs() + (Random.nextFloat() - 0.5f) * 140f, 40f + Random.nextFloat() * 160f, 16))
                }
                if (bossT >= 2.5f && !victory) {
                    victory = true
                    paused = true
                    bossesDown++
                    kills += 5
                    coins += 40
                    onEvent("bossWin")
                }
            }
        }
    }

    /** Продолжить бег после победы над боссом: следующий круг, всё сложнее. */
    fun continueAfterBoss(visibleW: Float) {
        bossState = 0
        victory = false
        paused = false
        for (k in 0..11) items.add(RunCoin(dist + visibleW * 0.55f + k * 52f, 40f + (k % 3) * 40f))
        nextX = dist + visibleW + 500f
        invuln = 1.2f
    }

    private fun killFoe(f: RunFoe) {
        f.dead = true
        kills++
        coins += 2
        for (k in 0..2) items.add(RunCoin(f.x - 40f + k * 40f, 50f + f.y + (if (k == 1) 30f else 0f)))
        fx.add(RunFx(f.x, f.y + f.h / 2f, 16))
        onEvent("foeDie")
    }

    private fun damageFoe(f: RunFoe, d: Int) {
        f.hp -= d
        f.flash = 0.18f
        fx.add(RunFx(f.x, f.y + f.h * 0.6f, 6))
        if (f.hp <= 0) killFoe(f) else onEvent("foeHit")
    }

    private fun updateFoes(dt: Float, visibleW: Float, hx: Float) {
        hxw = hx
        fireCd = max(0f, fireCd - dt)
        muzzle = max(0f, muzzle - dt)
        if (toddler && fireCd <= 0f && (bossState == 2 || foes.any { !it.dead && it.x > hx + 100f && it.x < hx + visibleW * 0.7f })) fire()
        for (f in foes.toList()) {
            if (f.dead) continue
            f.flash = max(0f, f.flash - dt)
            f.age += dt
            when (f.kind) {
                0, 2 -> f.x -= (if (f.kind == 2) 45f else (if (toddler) 30f else 70f)) * dt
                1 -> {
                    f.x -= (if (toddler) 30f else 60f) * dt
                    f.hopT -= dt
                    if (f.hopT <= 0f && f.y <= 0f) { f.vy = 560f; f.hopT = 1.1f + Random.nextFloat() * 0.5f }
                    if (f.y > 0f || f.vy > 0f) {
                        f.y += f.vy * dt; f.vy -= 1900f * dt
                        if (f.y <= 0f) { f.y = 0f; f.vy = 0f }
                    }
                }
                3, 4 -> {
                    // выезжают на экран и держатся справа, медленно сдвигаясь к герою
                    val target = visibleW * (if (f.kind == 3) 0.74f else 0.80f)
                    f.sx += if (f.sx > target) -90f * dt else -8f * dt
                    f.x = dist + f.sx
                    if (f.kind == 4) f.y = 125f + sin(f.age * 2.2f) * 28f
                    if (f.sx < target + 40f && bossState != 3) {
                        f.cd -= dt
                        if (f.cd <= 0.7f && f.aim <= 0f) { f.aim = 0.01f; f.aimY = if (f.kind == 3) (if (Random.nextBoolean()) 38f else 135f) else (hy + 55f).coerceIn(30f, 170f) }
                        if (f.aim > 0f) f.aim = min(1f, f.aim + dt / 0.7f)
                        if (f.cd <= 0f) {
                            shots.add(RunShot(f.x - 40f, if (f.kind == 3) f.aimY else f.y, -(if (f.kind == 3) 430f else 380f), if (f.kind == 3) 0 else 2))
                            f.cd = (if (toddler) 3.4f else 2.3f) + Random.nextFloat() * 1.2f
                            f.aim = 0f
                            onEvent("foeShot")
                        }
                    }
                }
            }
            val near = abs(f.x - hx) < f.w / 2f + 14f
            val overlapY = hy < f.y + f.h - 16f && hy + 100f > f.y
            if (near && overlapY) {
                // прыжок на голову (кроме духа): злодей получает урон, монстрик отскакивает
                if (f.kind != 4 && vy < -120f && hy > f.y + f.h * 0.5f - 12f) {
                    damageFoe(f, 1)
                    vy = 640f; airJumps = 1; squash = -0.35f
                    onEvent("stomp")
                } else if (invuln <= 0f) { hurt() }
            }
        }
        for (p in fires) {
            p.x += 780f * dt
            p.t += dt
            for (f in foes) {
                if (f.dead) continue
                if (abs(p.x - f.x) < f.w / 2f + 14f && p.y > f.y - 12f && p.y < f.y + f.h + 12f) {
                    p.x = 1e9f
                    damageFoe(f, 1)
                    break
                }
            }
        }
        fires.removeAll { it.x > dist + visibleW + 120f }
        foes.removeAll { it.dead || it.x < dist - 300f }
    }

    private fun updateShots(dt: Float, hx: Float) {
        for (s in shots) {
            s.x += s.vx * dt
            s.t += dt
            val halfH = when (s.kind) { 0 -> 12f; 1 -> 18f; 2 -> 24f; else -> 46f }
            val halfW = if (s.kind == 3) 34f else 22f
            if (invuln <= 0f && abs(s.x - hx) < halfW + 12f) {
                val lo = if (s.kind == 3) 0f else s.y - halfH
                val hi = s.y + halfH
                if (hy < hi && hy + 100f > lo) { hurt(); s.x = -1e9f }
            }
        }
        for (p in fires) for (s in shots) {
            if (s.kind != 3 && abs(p.x - s.x) < 34f && abs(p.y - s.y) < 40f) { p.x = 1e9f; fx.add(RunFx(s.x, s.y, 6)); s.x = -1e9f }
        }
        shots.removeAll { it.x < dist - 200f }
    }

    fun step(dt: Float, visibleW: Float, heroX: Float) {
        if (over || paused) return
        time += dt
        shake = max(0f, shake - dt * 1.4f)
        slow = max(0f, slow - dt)
        invuln = max(0f, invuln - dt)
        speed = baseSpeed() * (if (slow > 0f) 0.55f else 1f)
        if (bossState != 0) {
            val capD = ((dist / cycleLen).toInt() + 1) * cycleLen - 2f
            dist = min(dist + speed * 0.4f * dt, capD)
        } else dist += speed * dt
        if (hy > 0f || vy > 0f) {
            hy += vy * dt
            vy -= RUN_GRAVITY * dt
            if (hy <= 0f) { hy = 0f; vy = 0f; squash = 0.45f; if (wasAir) onEvent("land") }
            wasAir = true
        } else wasAir = false
        squash *= (1f - min(1f, dt * 9f))
        spawn(visibleW)
        val nl = (dist / RUN_LOC_LEN).toInt() % BIOME_COUNT
        if (nl != loc) { loc = nl; onEvent("loc") }
        val hx = dist + heroX
        val pad = if (toddler) 16f else 10f
        for (o in obs) {
            if (invuln <= 0f && hx + 14f > o.x + pad && hx - 14f < o.x + o.w - pad && hy < o.h - pad) {
                hurt()
                break
            }
        }
        vw = visibleW
        updateFoes(dt, visibleW, hx)
        updateBoss(dt, visibleW, hx)
        updateShots(dt, hx)
        for (c in items) {
            if (c.taken) continue
            if (abs(c.x - hx) < 52f && abs(c.y - (hy + 55f)) < 68f) {
                c.taken = true; coins++; onEvent("coin")
            }
        }
        obs.removeAll { it.x < dist - 300f }
        items.removeAll { it.x < dist - 300f }
    }
}

private enum class RunScreen { MENU, PLAY, PAUSE, WIN, OVER }

private fun runPrefs(ctx: Context) = ctx.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE)

@Composable
private fun RunChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
}

@Composable
private fun RunStat(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 20.sp)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF6A1B4D))
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8E4A74))
    }
}

/** Круглая кнопка поверх игры: ловит касание сама, поэтому тап по ней не считается прыжком. */
@Composable
private fun RunRoundButton(
    modifier: Modifier, size: androidx.compose.ui.unit.Dp, color: Color, onDown: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .size(size)
            .background(color, androidx.compose.foundation.shape.CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val d = awaitFirstDown()
                    d.consume()
                    onDown()
                }
            },
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
internal fun MonsterRunnerScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    /** Монстрик, с которым открыт забег (из общей комнаты); иначе берётся из «Моего дома». */
    initialSpec: MonSpec? = null,
    /** «Изменить монстрика»: вернуться в фабрику с текущим героем. */
    onEdit: ((MonSpec) -> Unit)? = null
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val density = LocalDensity.current.density
    val toddler = LocalAgeProfile.current.isToddler2

    val houseList = remember { loadHouse(context) }
    var heroIdx by remember {
        mutableIntStateOf(try { runPrefs(context).getInt("mr_hero", 0) } catch (e: Exception) { 0 })
    }
    if (heroIdx >= houseList.size) heroIdx = 0
    var heroSpecState by remember { mutableStateOf(initialSpec ?: houseList.getOrNull(heroIdx) ?: MonSpec()) }
    val heroSpec = heroSpecState
    val heroImgs = rememberMonImgs(heroSpec)
    val heroAnim = remember { MonAnim().also { it.danceUntil = 1e9f } }
    val boomFrames = remember { loadBoomFrames(context) }
    val booms = remember { ArrayList<FloatArray>() }
    val biomeGfx = rememberZBiomeGfx()

    var screen by remember { mutableStateOf(RunScreen.MENU) }
    var startLoc by remember { mutableIntStateOf(0) }
    val world = remember(toddler) { RunWorld(toddler) }
    var tick by remember { mutableIntStateOf(0) }
    var clock by remember { mutableFloatStateOf(0f) }
    var banner by remember { mutableStateOf("") }
    var best by remember { mutableIntStateOf(try { runPrefs(context).getInt("mr_best", 0) } catch (e: Exception) { 0 }) }
    var runsCount by remember { mutableIntStateOf(try { runPrefs(context).getInt("mr_runs", 0) } catch (e: Exception) { 0 }) }
    var newBest by remember { mutableStateOf(false) }
    var resultDist by remember { mutableIntStateOf(0) }
    var resultCoins by remember { mutableIntStateOf(0) }
    var resultKills by remember { mutableIntStateOf(0) }
    var resultBosses by remember { mutableIntStateOf(0) }
    var hudCoins by remember { mutableIntStateOf(0) }
    var hudLives by remember { mutableIntStateOf(3) }
    var hudLoc by remember { mutableIntStateOf(0) }
    var hudDist by remember { mutableIntStateOf(0) }
    var hudKills by remember { mutableIntStateOf(0) }
    var hudProg by remember { mutableFloatStateOf(0f) }
    var hudTime by remember { mutableFloatStateOf(0f) }
    var hudFire by remember { mutableFloatStateOf(0f) }
    var hudBossState by remember { mutableIntStateOf(0) }
    var hudBossHp by remember { mutableFloatStateOf(1f) }
    var hudBossMode by remember { mutableIntStateOf(0) }
    var hudBossPhase by remember { mutableIntStateOf(0) }
    var hudBossIdx by remember { mutableIntStateOf(0) }
    var sizeW by remember { mutableFloatStateOf(0f) }
    var sizeH by remember { mutableFloatStateOf(0f) }
    val sparks = remember { mutableListOf<FloatArray>() }
    var lottieTick by remember { mutableIntStateOf(0) }
    var banked by remember { mutableIntStateOf(0) }
    fun bank() {
        val d = world.coins - banked
        if (d > 0) { addWallet(context, d); banked += d }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { bank() } }
    val walletNow = remember(screen) { loadWallet(context) }

    /** Итоги забега: пишем рекорд (в том числе при выходе в меню, чтобы дистанция не пропадала). */
    fun finishStats() {
        bank()
        resultDist = (world.dist / 10f).toInt()
        resultCoins = world.coins
        resultKills = world.kills
        resultBosses = world.bossesDown
        newBest = resultDist > best
        if (newBest) {
            best = resultDist
            try { runPrefs(context).edit().putInt("mr_best", best).apply() } catch (e: Exception) { }
        }
    }

    fun confetti(x: Float, y: Float, n: Int) {
        repeat(n) {
            val a = Random.nextFloat() * 6.28f
            val s = (120f + Random.nextFloat() * 320f) * density
            sparks.add(floatArrayOf(x, y, cos(a) * s, sin(a) * s - 180f * density, 1f, Random.nextInt(5).toFloat()))
        }
    }

    LaunchedEffect(Unit) {
        soundManager.preloadRaw(listOf("zb_jump", "zb_jump2", "zb_land", "zb_coin", "zb_ouch", "zb_hurt", "zb_rocket", "zb_down", "sw_boom", "sw_dash", "sw_rage", "sw_warn", "sw_hit"))
    }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
        if (screen == RunScreen.PLAY) screen = RunScreen.PAUSE
    }
    world.onEvent = { ev ->
        when (ev) {
            "jump" -> soundManager.play("zb_jump", volume = 0.7f)
            "jump2" -> soundManager.play("zb_jump2", volume = 0.7f)
            "land" -> soundManager.play("zb_land", volume = 0.5f)
            "coin" -> soundManager.play("zb_coin", volume = 0.7f, rate = 0.9f + (world.coins % 8) * 0.05f)
            "hit" -> { soundManager.play("zb_ouch", volume = 0.9f); soundPlayer.vibrateSquishDeform() }
            "stomp" -> { soundManager.play("zb_land", volume = 0.8f, rate = 1.3f); soundManager.play("zb_hurt", volume = 0.6f, rate = 1.3f) }
            "loc" -> {
                soundPlayer.playFanfare()
                banner = BIOME_NAMES[world.loc] + "!"
                confetti(sizeW / 2f, sizeH * 0.35f, 40)
                lottieTick++
            }
            "bossStart" -> {
                soundManager.play("sw_rage", volume = 1f, rate = 0.8f)
                banner = "⚠️ БОСС: " + RUN_BOSS_NAMES[world.bossLap.coerceAtLeast(0) % 4] + "!"
            }
            "bossAtk" -> {
                soundManager.play("sw_warn", volume = 0.7f, rate = 0.9f)
                banner = when (world.bossAtk) {
                    0 -> "ПРЫГАЙ! 🌊"
                    1 -> "СБЕЙ ЗВЁЗДЫ! 🔥"
                    2 -> "ПРЫГАЙ И ЖГИ!"
                    else -> "СКЕЛЕТЫ ИДУТ! 👾"
                }
            }
            "bossStrike" -> soundManager.play("sw_boom", volume = 0.7f, rate = 0.6f)
            "bossOpen" -> { soundManager.play("zb_coin", volume = 0.8f, rate = 1.7f); banner = "🔥 ЩИТ СПАЛ — БЕЙ!" }
            "bossBlock" -> soundManager.play("sw_hit", volume = 0.7f, rate = 1.6f)
            "bossPhase" -> { soundManager.play("sw_rage", volume = 1f, rate = 1.0f); banner = "БОСС В ЯРОСТИ!" }
            "bossHit" -> { soundManager.play("zb_hurt", volume = 0.7f, rate = 0.7f); soundManager.play("fx_it_drp_${13 + Random.nextInt(4)}", volume = 0.6f) }
            "bossWin" -> {
                finishStats()
                soundPlayer.playFanfare()
                soundManager.play("sw_boom", volume = 1f, rate = 0.7f)
                confetti(sizeW / 2f, sizeH * 0.35f, 70)
                lottieTick++
                banner = ""
                screen = RunScreen.WIN
            }
            "fire" -> soundManager.play("zb_rocket", volume = 0.6f, rate = 1.25f)
            "foeShot" -> soundManager.play("zb_rocket", volume = 0.35f, rate = 0.8f)
            "foeHit" -> { soundManager.play("zb_hurt", volume = 0.8f, rate = 1.2f); soundManager.play("fx_it_drp_${1 + Random.nextInt(5)}", volume = 0.6f) }
            "foeDie" -> {
                soundManager.play("sw_boom", volume = 0.55f, rate = 1.3f)
                soundManager.play("zb_down", volume = 0.5f, rate = 1.5f)
                soundManager.play("zb_coin", volume = 0.6f, rate = 1.3f)
                soundManager.play("fx_it_dst_${1 + Random.nextInt(4)}", volume = 0.6f)
            }
            "over" -> {
                finishStats()
                if (newBest) {
                    soundPlayer.playLegendaryFanfare()
                    lottieTick++
                } else soundPlayer.playComicalFallWobble()
                screen = RunScreen.OVER
            }
        }
    }

    fun begin() {
        bank()
        banked = 0
        world.start(startLoc, sizeW / density)
        banner = BIOME_NAMES[startLoc]
        runsCount++
        try { runPrefs(context).edit().putInt("mr_runs", runsCount).apply() } catch (e: Exception) { }
        hudTime = 0f
        screen = RunScreen.PLAY
    }

    fun toMenu() {
        if (screen != RunScreen.MENU && screen != RunScreen.OVER) finishStats()
        banner = ""
        screen = RunScreen.MENU
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF4A6FA5))
            .testTag("monster_runner_root")
            .pointerInput(screen) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (screen == RunScreen.PLAY) world.jump()
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val redraw = tick
            val wpx = this.size.width
            val hpx = this.size.height
            sizeW = wpx; sizeH = hpx
            val z = density
            val gy = hpx * 0.78f / z
            val running = screen != RunScreen.MENU
            val camX = if (running) world.dist else clock * 60f + startLoc * RUN_LOC_LEN
            val bi = if (running) world.loc else startLoc
            val shk = if (running) world.shake else 0f
            val shx = sin(clock * 83f) * 9f * shk * z
            val shy = cos(clock * 71f) * 7f * shk * z
            withTransform({ translate(shx, shy) }) {
                drawBiomeBackdrop(biomeGfx, bi, camX, 0f, z, gy, (clock * 60f).toInt())
                withTransform({
                    translate(-camX * z, 0f)
                    scale(z, z, Offset.Zero)
                }) {
                    drawBiomeGround(biomeGfx, bi, camX - 40f, camX + wpx / z + 40f, gy, 400f)
                    if (running) {
                        for (o in world.obs) {
                            if (o.x > camX + wpx / z + 100f || o.x + o.w < camX - 100f) continue
                            val im = when (o.kind) { 0 -> biomeGfx.barrel; 1 -> biomeGfx.crate; 2 -> biomeGfx.vase; else -> biomeGfx.chest }
                            pxImg(im, o.x, gy - o.h + 3f, o.w, o.h)
                        }
                        for (c in world.items) {
                            if (c.taken || c.x > camX + wpx / z + 60f || c.x < camX - 60f) continue
                            val sq = abs(cos(clock * 5f + c.x * 0.02f))
                            val cy = gy - c.y
                            drawOval(Color(0xFFE65100), Offset(c.x - 17f * sq - 3f, cy - 22f), Size(34f * sq + 6f, 44f))
                            drawOval(Color(0xFFFFC107), Offset(c.x - 16f * sq - 1f, cy - 20f), Size(32f * sq + 2f, 40f))
                            drawOval(Color(0xFFFFF176), Offset(c.x - 8f * sq, cy - 13f), Size(14f * sq + 2f, 24f))
                        }
                        val tk60 = (clock * 60f).toInt()
                        for (f in world.foes) {
                            if (f.dead || f.x > camX + wpx / z + 120f || f.x < camX - 120f) continue
                            val base = gy - f.y
                            val flash = f.flash > 0f
                            // предупреждение о выстреле: красная полоса от стрелка до героя
                            if (f.aim > 0f && f.kind >= 3) {
                                val ly = gy - f.aimY
                                drawRect(Color(0xFFFF1744).copy(alpha = 0.10f + 0.28f * f.aim), Offset(camX - 20f, ly - 5f), Size(f.x - camX + 20f, 10f))
                            }
                            if (f.kind != 4) drawOval(Color.Black.copy(alpha = 0.22f), Offset(f.x - f.w * 0.45f, gy - 6f), Size(f.w * 0.9f, 12f))
                            // злой красный ореол: «это враг»
                            drawCircle(Color(0xFFFF1744).copy(alpha = 0.10f + 0.04f * sin(clock * 6f + f.x)), f.h * 0.75f, Offset(f.x, base - f.h * 0.5f))
                            when (f.kind) {
                                4 -> drawSpirit(f.x, base - f.h * 0.5f, f.w * 1.5f, f.h * 1.5f, tk60, flash, f.aim)
                                else -> {
                                    val air = f.y > 1f
                                    val moving = f.kind != 3
                                    drawBoneFighter(
                                        f.x, base, f.h * 1.15f, -1f, f.style,
                                        walk = clock * 7f + f.x * 0.03f,
                                        stride = if (air || !moving) 0f else (if (f.kind == 2) 0.5f else 0.9f),
                                        arm = if (f.kind == 3) 0f else sin(clock * 6f + f.x) * 0.15f,
                                        tuck = if (air) 1f else 0f,
                                        flash = flash,
                                        bowPull = if (f.kind == 3) f.aim else 0f
                                    )
                                }
                            }
                            if (f.maxHp > 1) {
                                val bw = 44f
                                val top = base - f.h * 1.15f - 18f
                                drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(f.x - bw / 2f, top), Size(bw, 8f), androidx.compose.ui.geometry.CornerRadius(4f, 4f))
                                drawRoundRect(Color(0xFFFF7043), Offset(f.x - bw / 2f, top), Size(bw * f.hp / f.maxHp, 8f), androidx.compose.ui.geometry.CornerRadius(4f, 4f))
                            }
                        }
                        for (s in world.shots) {
                            if (s.x > camX + wpx / z + 100f) continue
                            if (s.kind == 3) {
                                val pulse = 1f + 0.12f * sin(s.t * 30f)
                                drawOval(Color(0xFFB71C1C).copy(alpha = 0.85f), Offset(s.x - 38f, gy - 98f * pulse), Size(76f, 98f * pulse + 4f))
                                drawOval(Color(0xFFFF7043), Offset(s.x - 28f, gy - 82f * pulse), Size(56f, 82f * pulse))
                                drawOval(Color(0xFFFFE082), Offset(s.x - 14f, gy - 56f * pulse), Size(28f, 56f * pulse))
                            } else {
                                drawFoeShot(s.kind, s.x, gy - s.y, PI.toFloat(), (s.t * 60f).toInt(), false)
                            }
                        }
                        for (p in world.fires) {
                            val cy = gy - p.y
                            val fl = sin(p.t * 40f) * 3f
                            drawCircle(Color(0xFFFF5722).copy(alpha = 0.25f), 34f, Offset(p.x - 10f, cy))
                            for (k in 1..4) drawCircle(Color(0xFFFF7043).copy(alpha = 0.5f - k * 0.1f), 20f - k * 3f, Offset(p.x - k * 20f, cy + fl * k * 0.3f))
                            drawCircle(Color(0xFFFF6F00), 22f + fl, Offset(p.x, cy))
                            drawCircle(Color(0xFFFFCA28), 14f, Offset(p.x + 2f, cy))
                            drawCircle(Color(0xFFFFF9C4), 7f, Offset(p.x + 4f, cy))
                        }
                    }
                }
                val heroXdp = wpx / z * 0.24f
                val flick = running && world.invuln > 0f && ((world.invuln * 14f).toInt() % 2 == 0)
                if (!flick) {
                    heroAnim.hop = 0f
                    heroAnim.squash = if (running) world.squash.coerceIn(-0.5f, 0.6f) else 0f
                    val lift = if (running) world.hy else 0f
                    val lay = monLayout(heroImgs, heroXdp * z, (gy - lift) * z + 2f, 96f * z, 118f * z)
                    drawMonster(heroImgs, heroSpec, lay, heroAnim, clock * (if (running) 1.4f else 0.6f), z, 0.5f)
                }
                if (running && world.muzzle > 0f) {
                    val mx = (heroXdp + 46f) * z
                    val my = (gy - world.hy - 64f) * z
                    val k = world.muzzle / 0.18f
                    drawCircle(Color(0xFFFF6F00).copy(alpha = 0.5f * k), 44f * z * (1.2f - k * 0.4f), Offset(mx, my))
                    drawCircle(Color(0xFFFFEB3B).copy(alpha = 0.9f * k), 22f * z, Offset(mx, my))
                }

                // ---------- босс ----------
                if (running && world.bossState != 0) {
                    val bIdx = world.bossLap.coerceAtLeast(0) % 4
                    val bst = BOSS_STYLES[bIdx]
                    val bossCol = Color(RUN_BOSS_COL[bIdx])
                    val bxPx = world.bossX * z
                    val feet = gy * z
                    val hBoss = world.bossH() * z
                    val windup = weaponWindup(20)
                    val teleDur = 0.95f * (1f - 0.15f * world.bossPhase)
                    var arm = 0f
                    var stride = 0.6f
                    var jaw = 0f
                    var lean = 0f
                    when {
                        world.bossState == 1 -> stride = 1f
                        world.bossState == 3 -> { stride = 0f; lean = min(1.3f, world.bossT * 0.7f); jaw = 1f }
                        world.bossMode == 1 -> {
                            stride = 0f
                            arm = atkPose(((world.bossModeT / teleDur) * windup).toInt() + 1, windup)
                        }
                        world.bossMode == 2 -> {
                            stride = 0f
                            arm = atkPose(windup + (world.bossModeT / 0.55f * 12f).toInt(), windup)
                        }
                        world.bossMode == 3 -> { stride = 0f; jaw = 1f; lean = 0.12f }
                    }
                    val dyingFlash = world.bossState == 3 && ((clock * 12f).toInt() % 2 == 0)
                    drawCircle(
                        Brush.radialGradient(listOf(bossCol.copy(alpha = 0.30f), Color.Transparent), Offset(bxPx, feet - hBoss * 0.5f), hBoss * 0.8f),
                        hBoss * 0.8f, Offset(bxPx, feet - hBoss * 0.5f)
                    )
                    drawOval(Color.Black.copy(alpha = 0.28f), Offset(bxPx - 90f * z, feet - 8f * z), Size(180f * z, 18f * z))
                    drawBoneFighter(
                        bxPx, feet, hBoss, -1f, bst,
                        walk = clock * 4f, stride = stride, arm = arm, lean = lean,
                        flash = world.bossFlash > 0f || dyingFlash, jaw = jaw
                    )
                    val cyBody = feet - hBoss * 0.5f
                    val shielded = world.bossState == 1 || (world.bossState == 2 && world.bossMode != 3)
                    if (shielded) {
                        val a = 0.12f + 0.05f * sin(clock * 5f) + (if (world.bossBlock > 0f) 0.3f else 0f)
                        drawCircle(bossCol.copy(alpha = a), hBoss * 0.62f, Offset(bxPx - 6f * z, cyBody))
                        drawCircle(
                            (if (world.bossBlock > 0f) Color.White else bossCol).copy(alpha = 0.7f), hBoss * 0.62f, Offset(bxPx - 6f * z, cyBody),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = (if (world.bossBlock > 0f) 6f else 3f) * z)
                        )
                    } else if (world.bossState == 2) {
                        val pr = 38f * z * (1f + 0.15f * sin(clock * 14f))
                        drawCircle(Color(0xFFFF1744).copy(alpha = 0.9f), pr, Offset(bxPx - 14f * z, cyBody - 6f * z), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f * z))
                        drawCircle(Color(0xFFFF1744).copy(alpha = 0.9f), pr * 0.5f, Offset(bxPx - 14f * z, cyBody - 6f * z), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f * z))
                    }
                }
                if (running && world.invuln > 1.0f) {
                    drawRect(Color(0xFFFF1744).copy(alpha = ((world.invuln - 1.0f) * 1.0f).coerceIn(0f, 0.4f)), Offset.Zero, Size(wpx, hpx))
                }
                booms.removeAll { clock - it[2] >= BOOM_TIME }
                for (b in booms) drawBoom(boomFrames, b[0], b[1], 150f * z, clock - b[2])
                val pal = listOf(Color(0xFFFFD54F), Color(0xFFFF8A65), Color(0xFF4FC3F7), Color(0xFFAED581), Color(0xFFF06292))
                for (s in sparks) drawCircle(pal[s[5].toInt() % 5].copy(alpha = s[4].coerceIn(0f, 1f)), 4f * z, Offset(s[0], s[1]))
            }
        }

        if (screen == RunScreen.PLAY || screen == RunScreen.PAUSE) {
            Column(
                Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 8.dp, start = 12.dp, end = 72.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RunChip("🪙 $hudCoins")
                    RunChip("📏 $hudDist м")
                    RunChip("👾 $hudKills")
                    if (!toddler) RunChip("❤️".repeat(max(0, hudLives)))
                }
                // дорога до босса: где ты, какая локация, где босс
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RunChip(BIOME_NAMES[hudLoc])
                    Spacer(Modifier.size(6.dp))
                    Box(
                        Modifier.size(width = 120.dp, height = 12.dp)
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    ) {
                        Box(Modifier.size(width = (120f * hudProg.coerceIn(0.03f, 1f)).dp, height = 12.dp).background(Color(0xFF66BB6A), RoundedCornerShape(6.dp)))
                    }
                    Text(RUN_BOSS_ICON[hudBossIdx], fontSize = 18.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
            RunRoundButton(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 12.dp),
                48.dp, Color.Black.copy(alpha = 0.45f),
                { if (screen == RunScreen.PLAY) { soundPlayer.playPop(1.1f); screen = RunScreen.PAUSE } }
            ) { Text("⏸", fontSize = 22.sp) }

            if (screen == RunScreen.PLAY) {
                // кнопка огня с кольцом перезарядки
                Box(
                    Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val ready = hudFire <= 0f
                    RunRoundButton(
                        Modifier, 100.dp, (if (ready) Color(0xFFFF6F00) else Color(0xFF8D4A1A)).copy(alpha = 0.9f),
                        { if (screen == RunScreen.PLAY) world.fire() }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔥", fontSize = 40.sp)
                            Text("ОГОНЬ", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                    Canvas(Modifier.size(108.dp)) {
                        drawArc(
                            Color.White.copy(alpha = if (ready) 0.0f else 0.85f), -90f, 360f * (1f - hudFire.coerceIn(0f, 1f)), false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx())
                        )
                    }
                }
                // подсказки первых забегов
                if (runsCount <= 3 && hudTime < 12f && hudBossState == 0) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RunChip("👆 Тап по экрану — прыжок (в воздухе — ещё раз)")
                        Spacer(Modifier.height(4.dp))
                        RunChip("🔥 Огонь или прыжок на голову — побеждай злодеев")
                    }
                }
            }
            // полоса босса: имя, здоровье по фазам, щит / «бей!»
            if (hudBossState != 0) {
                val bCol = Color(RUN_BOSS_COL[hudBossIdx])
                Column(
                    Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 78.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        RUN_BOSS_ICON[hudBossIdx] + " " + RUN_BOSS_NAMES[hudBossIdx], fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier.size(width = 220.dp, height = 16.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    ) {
                        Box(Modifier.size(width = (220f * hudBossHp.coerceIn(0f, 1f)).dp, height = 16.dp).background(bCol, RoundedCornerShape(8.dp)))
                    }
                    Spacer(Modifier.height(3.dp))
                    val st = when {
                        hudBossState == 1 -> "⚠️ Приближается…"
                        hudBossState == 3 -> "💥 Побеждён!"
                        hudBossMode == 3 -> "🔥 ЩИТ СПАЛ — БЕЙ!"
                        hudBossMode == 1 -> "⚠️ Готовит удар"
                        else -> "🛡 Щит — жди, пока спадёт"
                    }
                    RunChip(st + "  " + "●".repeat(3 - hudBossPhase) + "○".repeat(hudBossPhase))
                }
            }
            if (banner.isNotEmpty() && screen == RunScreen.PLAY) {
                Text(
                    banner, fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFEB3B), textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(bottom = 90.dp, start = 16.dp, end = 16.dp)
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }
        }

        if (screen == RunScreen.PAUSE) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                KitWindow(width = 300.dp, title = "Пауза") {
                    Text("$hudDist м   🪙 $hudCoins   👾 $hudKills", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF6A1B4D))
                    Spacer(Modifier.height(6.dp))
                    KitLongButton(R.drawable.ui_btn_long_green, "Продолжить ▶", 180.dp, 15.sp) { soundPlayer.playPop(1.2f); screen = RunScreen.PLAY }
                    KitLongButton(R.drawable.ui_btn_long_blue, "Заново ↻", 150.dp, 13.sp) { soundPlayer.playPop(1.0f); begin() }
                    KitLongButton(R.drawable.ui_btn_long_red, "В меню", 150.dp, 13.sp) { soundPlayer.playPop(0.9f); toMenu() }
                }
            }
        }

        if (screen == RunScreen.MENU) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                KitWindow(width = 330.dp, title = "Забег") {
                    Text(
                        if (toddler) "👆 Тапай — монстрик прыгает и сам плюётся огнём!\n💀 Бей злодеев и босса, собирай монетки."
                        else "👆 Тап — прыжок (в воздухе — двойной)\n🔥 Огонь или прыжок на голову бьёт злодеев\n💀 В конце круга — БОСС: бей, когда щит спадёт",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6A1B4D), textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("С какого места бежать:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8E4A74))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (i in 0 until BIOME_COUNT) {
                            KitLongButton(
                                if (i == 0) R.drawable.ui_btn_long_green else if (i == 1) R.drawable.ui_btn_long_blue else R.drawable.ui_btn_long_red,
                                BIOME_NAMES[i].substringAfter(' '), 64.dp, 10.sp, selected = startLoc == i
                            ) { startLoc = i; soundPlayer.playPop(1.2f) }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    KitLongButton(
                        R.drawable.ui_btn_long_blue,
                        if (houseList.isEmpty()) "Дом пуст" else "Из дома ${heroIdx + 1}/${houseList.size}",
                        105.dp, 10.sp
                    ) {
                        if (houseList.isNotEmpty()) {
                            heroIdx = (heroIdx + 1) % houseList.size
                            heroSpecState = houseList[heroIdx]
                            heroAnim.squash = 0.5f
                            soundPlayer.playPop(1.3f)
                            try { runPrefs(context).edit().putInt("mr_hero", heroIdx).apply() } catch (e: Exception) { }
                            try { runPrefs(context).edit().putString("mw_last", heroSpecState.encode()).apply() } catch (e: Exception) { }
                        }
                    }
                    KitLongButton(R.drawable.ui_btn_long_blue, "🎲 Случайный", 105.dp, 10.sp) {
                        heroSpecState = randomFree(loadUnlocked(context))
                        heroAnim.squash = 0.8f
                        soundPlayer.playMagicSparkle()
                        try { runPrefs(context).edit().putString("mw_last", heroSpecState.encode()).apply() } catch (e: Exception) { }
                    }
                    if (onEdit != null) {
                        KitLongButton(R.drawable.ui_btn_long_red, "🛠 Изменить", 105.dp, 10.sp) {
                            soundPlayer.playPop(1.3f); onEdit(heroSpecState)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                KitLongButton(R.drawable.ui_btn_long_green, "БЕЖАТЬ ▶", 220.dp, 22.sp) { begin() }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RunChip("🪙 $walletNow")
                    if (best > 0) RunChip("🏆 рекорд: $best м")
                }
            }
        }

        if (screen == RunScreen.WIN) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                KitWindow(width = 330.dp, title = "Босс побеждён!") {
                    Text(
                        RUN_BOSS_ICON[hudBossIdx] + " " + RUN_BOSS_NAMES[hudBossIdx] + " повержен!   +40 🪙",
                        fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF6A1B4D), textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        RunStat("📏", "$resultDist", "метров")
                        RunStat("🪙", "$resultCoins", "монет")
                        RunStat("👾", "$resultKills", "злодеев")
                        RunStat("💀", "$resultBosses", "боссов")
                    }
                    Spacer(Modifier.height(4.dp))
                    KitLongButton(R.drawable.ui_btn_long_green, "Бежать дальше ▶", 190.dp, 15.sp) {
                        soundPlayer.playPop(1.2f)
                        world.continueAfterBoss(sizeW / density)
                        banner = BIOME_NAMES[world.loc] + " — новый круг!"
                        screen = RunScreen.PLAY
                    }
                    KitLongButton(R.drawable.ui_btn_long_blue, "В меню", 130.dp, 12.sp) { soundPlayer.playPop(0.9f); toMenu() }
                }
            }
        }

        if (screen == RunScreen.OVER) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                KitWindow(width = 330.dp, title = if (newBest) "Рекорд!" else "Конец забега") {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        RunStat("📏", "$resultDist", "метров")
                        RunStat("🪙", "$resultCoins", "монет")
                        RunStat("👾", "$resultKills", "злодеев")
                        RunStat("💀", "$resultBosses", "боссов")
                    }
                    if (!newBest && best > 0) Text("🏆 рекорд: $best м", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8E4A74))
                    Spacer(Modifier.height(4.dp))
                    KitLongButton(R.drawable.ui_btn_long_green, "Ещё раз ↻", 170.dp, 15.sp) { begin() }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        KitLongButton(R.drawable.ui_btn_long_blue, "В меню", 100.dp, 11.sp) { screen = RunScreen.MENU }
                        if (onEdit != null) KitLongButton(R.drawable.ui_btn_long_red, "🛠 Изменить", 100.dp, 11.sp) { onEdit(heroSpecState) }
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LottieConfetti(lottieTick, Modifier.fillMaxSize())
        if (screen == RunScreen.MENU) LottieLoop(R.raw.lottie_mascot, Modifier.align(Alignment.TopEnd).statusBarsPadding().size(96.dp))
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanosEco { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.033f)
                last = now
                clock += dt
                if (screen == RunScreen.PLAY && sizeW > 0f) {
                    world.step(dt, sizeW / density, sizeW / density * 0.24f)
                    hudCoins = world.coins; hudLives = world.lives; hudLoc = world.loc; hudDist = (world.dist / 10f).toInt()
                    hudKills = world.kills
                    hudTime = world.time
                    hudProg = world.progress()
                    hudFire = if (world.fireCd > 0f) world.fireCd / world.fireDelay else 0f
                    hudBossState = world.bossState
                    hudBossHp = world.bossHp.toFloat() / world.bossMax
                    hudBossMode = world.bossMode
                    hudBossPhase = world.bossPhase
                    hudBossIdx = world.bossLap.coerceAtLeast(((world.dist / world.cycleLen).toInt())).coerceAtLeast(0) % 4
                }
                if (world.fx.isNotEmpty()) {
                    val gyPx = sizeH * 0.78f
                    for (e in world.fx) {
                        confetti((e.x - world.dist) * density, gyPx - e.y * density, e.n)
                        if (e.n >= 16) booms.add(floatArrayOf((e.x - world.dist) * density, gyPx - e.y * density, clock))
                    }
                    world.fx.clear()
                }
                val it = sparks.iterator()
                while (it.hasNext()) {
                    val s = it.next()
                    s[0] += s[2] * dt; s[1] += s[3] * dt
                    s[3] += 700f * density * dt
                    s[4] -= dt * 1.1f
                    if (s[4] <= 0f) it.remove()
                }
                tick++
            }
        }
    }
    LaunchedEffect(banner) {
        if (banner.isNotEmpty()) { kotlinx.coroutines.delay(1800); banner = "" }
    }
}
