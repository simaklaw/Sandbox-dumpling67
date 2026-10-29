package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.example.shared.i18n.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * «Приключения Зелика» — карта испытаний. Управление прежнее: нажми, куда идти Зелику —
 * пойдёт, а далеко — побежит; нажми на самого Зелика — подпрыгнет, три раза быстро — захихикает.
 * Зелик сам перепрыгивает ямы и ступеньки. Врагов нет — только испытания: кочки через реку,
 * лесенки, грибы-батуты, плоты, кнопки с мостами и лифты. Собери все кристаллы 💎, заправь
 * ракету и лети дальше! Три спрятанные звезды ⭐ в каждом уровне — для самых ловких.
 */

private class AlienFrames(
    val idle: List<ImageBitmap>,
    val walk: List<ImageBitmap>,
    val run: List<ImageBitmap>,
    val jump: List<ImageBitmap>,
    val hurt: List<ImageBitmap>
)

@Composable
private fun rememberAlienFrames(): AlienFrames {
    val idle = listOf(
        ImageBitmap.imageResource(R.drawable.alien_idle_1),
        ImageBitmap.imageResource(R.drawable.alien_idle_2),
        ImageBitmap.imageResource(R.drawable.alien_idle_3)
    )
    val walk = listOf(
        ImageBitmap.imageResource(R.drawable.alien_walk_1),
        ImageBitmap.imageResource(R.drawable.alien_walk_2),
        ImageBitmap.imageResource(R.drawable.alien_walk_3),
        ImageBitmap.imageResource(R.drawable.alien_walk_4),
        ImageBitmap.imageResource(R.drawable.alien_walk_5),
        ImageBitmap.imageResource(R.drawable.alien_walk_6)
    )
    val run = listOf(
        ImageBitmap.imageResource(R.drawable.alien_run_1),
        ImageBitmap.imageResource(R.drawable.alien_run_2),
        ImageBitmap.imageResource(R.drawable.alien_run_3),
        ImageBitmap.imageResource(R.drawable.alien_run_4),
        ImageBitmap.imageResource(R.drawable.alien_run_5),
        ImageBitmap.imageResource(R.drawable.alien_run_6)
    )
    val jump = listOf(
        ImageBitmap.imageResource(R.drawable.alien_jump_1),
        ImageBitmap.imageResource(R.drawable.alien_jump_2),
        ImageBitmap.imageResource(R.drawable.alien_jump_3),
        ImageBitmap.imageResource(R.drawable.alien_jump_4)
    )
    val hurt = listOf(
        ImageBitmap.imageResource(R.drawable.alien_hurt_1),
        ImageBitmap.imageResource(R.drawable.alien_hurt_2),
        ImageBitmap.imageResource(R.drawable.alien_hurt_3),
        ImageBitmap.imageResource(R.drawable.alien_hurt_4)
    )
    return remember { AlienFrames(idle, walk, run, jump, hurt) }
}

private val CRYSTAL_COLORS = listOf(
    Color(0xFF4DD0E1), Color(0xFFF48FB1), Color(0xFFFFD54F), Color(0xFFB39DDB)
)

// ───────────────────────────── Уровни ─────────────────────────────

private const val ZR_G = 1500f
private const val ZR_K_GROUND = 0
private const val ZR_K_LEDGE = 1
private const val ZR_K_RAFT = 2
private const val ZR_K_LIFT = 3
private const val ZR_K_BRIDGE = 4

private class ZrPlat(val x0: Float, val x1: Float, val top: Float, val kind: Int) {
    var dx = 0f
    var curTop = top
    var prevTop = top
    var ext = 1f
    var btn = -1
    var amp = 0f
    var phase = 0f
    var speed = 1f
    var low = 0f
    var high = 0f
    fun left() = x0 + dx
    fun right() = x0 + dx + (x1 - x0) * ext
}

private class ZrItem(val x: Float, val y: Float, val star: Boolean) { var taken = false; val ph = Random.nextFloat() * 6.28f }
private class ZrSpring(val x: Float, val top: Float) { var squash = 0f }
private class ZrButton(val id: Int, val x: Float) { var pressed = false }
private class ZrSpark(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Color, var life: Float, val size: Float)

private class ZrLevel(val index: Int, val name: String, val emoji: String, val theme: Int) {
    val plats = ArrayList<ZrPlat>()
    val items = ArrayList<ZrItem>()
    val springs = ArrayList<ZrSpring>()
    val buttons = ArrayList<ZrButton>()
    var len = 0f
    var crystals = 0
    var stars = 0
    var nextBtn = 0
}

private val LEVEL_DEFS = listOf(
    Triple("Лужайка", "🌼", 0),
    Triple("Кочки у реки", "🪨", 1),
    Triple("Грибная роща", "🍄", 2),
    Triple("Плоты", "🛶", 1),
    Triple("Пещера кнопок", "🔘", 3),
    Triple("Ледяные лифты", "🧊", 4),
    Triple("Звёздный мост", "🌉", 5),
    Triple("Космопорт", "🚀", 5)
)

private fun ZrLevel.crystal(x: Float, y: Float) { items.add(ZrItem(x, y, false)); crystals++ }
private fun ZrLevel.star(x: Float, y: Float) { if (stars < 3) { items.add(ZrItem(x, y, true)); stars++ } }

/** Ровный участок. */
private fun ZrLevel.flat(x0: Float, n: Int, withStar: Boolean = false): Float {
    val len = 120f + n * 70f
    plats.add(ZrPlat(x0, x0 + len, 0f, ZR_K_GROUND))
    for (i in 0 until n) crystal(x0 + 90f + i * 70f, 42f + (if (i == n - 1 && n >= 3) 78f else 0f))
    if (withStar) star(x0 + len * 0.5f, 140f)
    return x0 + len
}

/** Ямы с водой: прыгает сам. */
private fun ZrLevel.hops(x0: Float, count: Int, gap: Float = 90f): Float {
    var x = x0
    plats.add(ZrPlat(x, x + 200f, 0f, ZR_K_GROUND)); x += 200f
    for (i in 0 until count) {
        crystal(x + gap / 2f, 112f)
        x += gap
        plats.add(ZrPlat(x, x + 200f, 0f, ZR_K_GROUND)); x += 200f
        if (i == count - 1) star(x - 100f, 150f)
    }
    return x
}

/** Кочки через реку. */
private fun ZrLevel.stones(x0: Float): Float {
    var x = x0
    plats.add(ZrPlat(x, x + 150f, 0f, ZR_K_GROUND)); x += 150f
    for (i in 0 until 3) {
        x += 66f
        plats.add(ZrPlat(x, x + 72f, 0f, ZR_K_LEDGE))
        crystal(x + 36f, 44f)
        x += 72f
    }
    x += 66f
    plats.add(ZrPlat(x, x + 150f, 0f, ZR_K_GROUND))
    star(x - 30f, 160f)
    return x + 150f
}

/** Лесенка вверх и вниз. */
private fun ZrLevel.stairs(x0: Float): Float {
    val x = x0
    plats.add(ZrPlat(x, x + 100f, 0f, ZR_K_GROUND))
    plats.add(ZrPlat(x + 100f, x + 180f, 50f, ZR_K_LEDGE))
    plats.add(ZrPlat(x + 180f, x + 260f, 100f, ZR_K_LEDGE))
    plats.add(ZrPlat(x + 260f, x + 340f, 150f, ZR_K_LEDGE))
    plats.add(ZrPlat(x + 340f, x + 500f, 150f, ZR_K_LEDGE))
    plats.add(ZrPlat(x + 500f, x + 580f, 100f, ZR_K_LEDGE))
    plats.add(ZrPlat(x + 580f, x + 660f, 50f, ZR_K_LEDGE))
    plats.add(ZrPlat(x + 660f, x + 760f, 0f, ZR_K_GROUND))
    plats.add(ZrPlat(x, x + 760f, 0f, ZR_K_GROUND))
    crystal(x + 140f, 90f); crystal(x + 220f, 140f)
    crystal(x + 380f, 192f); crystal(x + 460f, 192f)
    crystal(x + 620f, 92f)
    star(x + 420f, 290f)
    return x + 760f
}

/** Гриб-батут и высокий выступ. */
private fun ZrLevel.mushroom(x0: Float): Float {
    plats.add(ZrPlat(x0, x0 + 700f, 0f, ZR_K_GROUND))
    springs.add(ZrSpring(x0 + 150f, 0f))
    plats.add(ZrPlat(x0 + 215f, x0 + 460f, 178f, ZR_K_LEDGE))
    crystal(x0 + 270f, 220f); crystal(x0 + 340f, 220f); crystal(x0 + 410f, 220f)
    crystal(x0 + 560f, 44f)
    star(x0 + 340f, 330f)
    return x0 + 700f
}

/** Плоты на воде. */
private fun ZrLevel.rafts(x0: Float, toddler: Boolean): Float {
    for (k in 0 until 4) {
        val p = ZrPlat(x0 + 70f + k * 160f, x0 + 170f + k * 160f, 0f, ZR_K_RAFT)
        p.amp = if (toddler) 14f else 24f
        p.phase = k * PI.toFloat()
        p.speed = 1.1f
        plats.add(p)
        crystal(x0 + 120f + k * 160f, 70f)
    }
    plats.add(ZrPlat(x0 + 740f, x0 + 900f, 0f, ZR_K_GROUND))
    star(x0 + 440f, 160f)
    return x0 + 900f
}

/** Кнопка раскладывает мост через пропасть. */
private fun ZrLevel.bridge(x0: Float): Float {
    val id = nextBtn++
    plats.add(ZrPlat(x0, x0 + 260f, 0f, ZR_K_GROUND))
    buttons.add(ZrButton(id, x0 + 190f))
    val b = ZrPlat(x0 + 260f, x0 + 540f, 0f, ZR_K_BRIDGE)
    b.btn = id; b.ext = 0f
    plats.add(b)
    plats.add(ZrPlat(x0 + 540f, x0 + 800f, 0f, ZR_K_GROUND))
    crystal(x0 + 400f, 44f); crystal(x0 + 480f, 44f)
    star(x0 + 270f, 150f)
    return x0 + 800f
}

/** Лифт на высокую площадку. */
private fun ZrLevel.lift(x0: Float): Float {
    plats.add(ZrPlat(x0, x0 + 120f, 0f, ZR_K_GROUND))
    val e = ZrPlat(x0 + 135f, x0 + 245f, 0f, ZR_K_LIFT)
    e.low = 0f; e.high = 150f; e.speed = 0.85f
    plats.add(e)
    plats.add(ZrPlat(x0 + 262f, x0 + 620f, 150f, ZR_K_LEDGE))
    plats.add(ZrPlat(x0 + 620f, x0 + 690f, 75f, ZR_K_LEDGE))
    plats.add(ZrPlat(x0 + 690f, x0 + 800f, 0f, ZR_K_GROUND))
    crystal(x0 + 340f, 192f); crystal(x0 + 430f, 192f); crystal(x0 + 520f, 192f)
    star(x0 + 440f, 300f)
    return x0 + 800f
}

private fun buildLevel(index: Int, toddler: Boolean): ZrLevel {
    val (name, emoji, theme) = LEVEL_DEFS[index]
    val lv = ZrLevel(index, name, emoji, theme)
    var x = 0f
    x = lv.flat(x, 3)
    x = when (index) {
        0 -> { var e = lv.stairs(x); e = lv.flat(e, 4, true); lv.stairs(e) }
        1 -> { var e = lv.hops(x, 3); e = lv.stones(e); e = lv.flat(e, 2); lv.hops(e, 2, 100f) }
        2 -> { var e = lv.mushroom(x); e = lv.flat(e, 2); e = lv.mushroom(e); lv.flat(e, 2, true) }
        3 -> { var e = lv.rafts(x, toddler); e = lv.flat(e, 2); e = lv.rafts(e, toddler); lv.flat(e, 2, true) }
        4 -> { var e = lv.bridge(x); e = lv.flat(e, 2); e = lv.bridge(e); lv.flat(e, 2, true) }
        5 -> { var e = lv.lift(x); e = lv.flat(e, 2); e = lv.lift(e); lv.flat(e, 2, true) }
        6 -> { var e = lv.stones(x); e = lv.bridge(e); e = lv.rafts(e, toddler); lv.flat(e, 2, true) }
        else -> {
            var e = lv.mushroom(x); e = lv.stairs(e); e = lv.lift(e); e = lv.bridge(e)
            e = lv.rafts(e, toddler); e = lv.hops(e, 2); lv.flat(e, 3, true)
        }
    }
    x = lv.flat(x, 2, false).let { it } // выравнивающий участок перед ракетой
    lv.len = x + 40f
    while (lv.stars < 3) { lv.star(x - 200f - lv.stars * 150f, 140f) }
    return lv
}

// ───────────────────────────── Мир ─────────────────────────────

private class ZrWorld(val toddler: Boolean) {
    lateinit var lv: ZrLevel
    var vw = 700f
    var vh = 360f
    var time = 0f
    var animClock = 0f
    var camX = 0f

    var x = 80f
    var y = 0f
    var vy = 0f
    var onGround = true
    var standing: ZrPlat? = null
    var targetX = 80f
    var facing = 1f
    var moving = false
    var running = false
    var waiting = false
    var jumpAnim = -1f
    var giggleT = 0f
    private var combo = 0
    private var lastTapTime = -10f
    private var safeX = 80f
    private var safeY = 0f
    var fallT = -1f


    var got = 0
    var gotStars = 0
    var need = 1
    /** 0 — идём, 1 — взлёт, 2 — готово. */
    var phase = 0
    var shipT = 0f
    var shipWiggle = 0f
    private var celebrateT = 0f
    var rocketX = 0f
    val sparks = ArrayList<ZrSpark>()

    var onCollect: (Boolean) -> Unit = {}
    var onJump: () -> Unit = {}
    var onGiggle: () -> Unit = {}
    var onSplash: () -> Unit = {}
    var onSpring: () -> Unit = {}
    var onButton: () -> Unit = {}
    var onLaunch: () -> Unit = {}
    var onWin: (Int) -> Unit = {}
    var onNeedMore: () -> Unit = {}
    private var needMoreCd = 0f

    val spriteH get() = 92f
    val spriteW get() = spriteH * 250f / 340f
    val groundY get() = vh * 0.80f

    fun load(level: ZrLevel) {
        lv = level
        x = 80f; y = 0f; vy = 0f; onGround = true; standing = null
        targetX = x; facing = 1f; moving = false; running = false; waiting = false
        jumpAnim = -1f; giggleT = 0f; combo = 0
        safeX = x; safeY = 0f; fallT = -1f
        got = 0; gotStars = 0
        need = if (toddler) max(1, (level.crystals * 0.6f).toInt()) else level.crystals
        phase = 0; shipT = 0f; shipWiggle = 0f; celebrateT = 0f
        rocketX = level.len - 130f
        sparks.clear()
        camX = 0f
        for (p in level.plats) { p.curTop = p.top; p.prevTop = p.top; p.dx = 0f; if (p.kind == ZR_K_BRIDGE) p.ext = 0f }
        level.buttons.forEach { it.pressed = false }
        level.items.forEach { it.taken = false }
        level.springs.forEach { it.squash = 0f }
        time = 0f
    }

    private fun spark(px: Float, py: Float, color: Color, n: Int) {
        repeat(n) {
            val a = Random.nextFloat() * 6.28f
            val s = Random.nextFloat() * 200f + 60f
            sparks.add(ZrSpark(px, py, cos(a) * s, sin(a) * s + 120f, if (Random.nextBoolean()) color else Color.White, 1f, Random.nextFloat() * 3f + 2.5f))
        }
    }

    private val airSpeed get() = if (toddler) 200f else 172f

    private fun jump(power: Float = 560f) {
        if (!onGround) return
        vy = power; onGround = false; standing = null
        jumpAnim = 0f
        onJump()
    }

    fun tap(wx: Float, wy: Float) {
        if (phase != 0) return
        lastTapTime = time
        val onAlien = abs(wx - x) < spriteW * 0.5f && wy > y + 0f && wy < y + spriteH
        // wy — высота над землёй
        if (onAlien) {
            if (time - lastTapTime < 1.2f) combo++ else combo = 1
            lastTapTime = time
            if (combo >= 3) {
                combo = 0; giggleT = 1.4f; onGiggle()
            } else jump(if (onGround) 570f else 0f)
            return
        }
        if (abs(wx - rocketX) < 50f && wy < 190f) {
            shipWiggle = 0.7f
            onNeedMore()
            return
        }
        targetX = wx.coerceIn(20f, lv.len - 20f)
    }

    private fun floorTopAt(px: Float, maxTop: Float): ZrPlat? {
        var best: ZrPlat? = null
        for (p in lv.plats) {
            if (p.kind == ZR_K_BRIDGE && p.ext < 0.02f) continue
            if (px >= p.left() - 4f && px <= p.right() + 4f && p.curTop <= maxTop) {
                if (best == null || p.curTop > best.curTop) best = p
            }
        }
        return best
    }

    private fun updatePlatforms(dt: Float) {
        for (p in lv.plats) {
            p.prevTop = p.curTop
            val oldDx = p.dx
            when (p.kind) {
                ZR_K_RAFT -> p.dx = sin(time * p.speed + p.phase) * p.amp
                ZR_K_LIFT -> {
                    val k = (sin(time * p.speed) + 1f) * 0.5f
                    val s = k * k * (3f - 2f * k)
                    p.curTop = p.low + (p.high - p.low) * s
                }
                ZR_K_BRIDGE -> {
                    val pressed = lv.buttons.any { it.id == p.btn && it.pressed }
                    p.ext = if (pressed) min(1f, p.ext + dt * 0.9f) else p.ext
                }
            }
            if (p === standing && onGround) {
                x += p.dx - oldDx
            }
        }
    }

    fun step(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0.001f, 0.04f)
        time += dt
        animClock += dt
        shipWiggle = max(0f, shipWiggle - dt)
        needMoreCd = max(0f, needMoreCd - dt)
        updatePlatforms(dt)
        stepSparks(dt)
        for (s in lv.springs) s.squash = max(0f, s.squash - dt * 4f)

        if (phase == 1) {
            shipT += dt
            celebrateT -= dt
            if (celebrateT <= 0f && onGround) { celebrateT = 0.9f; jump(520f) }
            applyVertical(dt)
            if (shipT > 2.8f) { phase = 2; onWin(gotStars) }
            return
        }
        if (phase == 2) return

        if (fallT >= 0f) {
            fallT += dt
            if (fallT > 0.8f) respawn()
            return
        }

        // — горизонталь
        if (giggleT > 0f) {
            giggleT -= dt
            moving = false
        } else if (onGround) {
            horizontalGround(dt)
        } else {
            val d = targetX - x
            if (abs(d) > 4f) {
                val dir = if (d > 0f) 1f else -1f
                facing = dir
                x += dir * min(airSpeed * dt, abs(d))
                moving = true
            } else moving = false
        }

        applyVertical(dt)

        // — безопасная точка
        val st = standing
        if (onGround && st != null && (st.kind == ZR_K_GROUND || st.kind == ZR_K_LEDGE || (st.kind == ZR_K_BRIDGE && st.ext >= 1f))) {
            safeX = x.coerceIn(st.left() + 24f, st.right() - 24f)
            safeY = st.curTop
        }
        if (y < -110f) {
            fallT = 0f
            onSplash()
            spark(x, -10f, Color(0xFF80D8FF), 22)
            moving = false
            return
        }

        // — пружины
        for (s in lv.springs) {
            if (abs(x - s.x) < 26f && y <= s.top + 8f && vy <= 0f && (onGround || y > s.top - 4f)) {
                vy = if (toddler) 780f else 800f
                onGround = false; standing = null; s.squash = 1f
                jumpAnim = 0f
                onSpring()
                spark(s.x, s.top + 10f, Color(0xFFFF8A80), 10)
            }
        }
        // — кнопки
        for (b in lv.buttons) {
            if (!b.pressed && onGround && abs(x - b.x) < 26f && y < 6f) {
                b.pressed = true
                onButton()
                spark(b.x, 10f, Color(0xFFFFD54F), 14)
            }
        }
        // — кристаллы и звёзды
        for (it in lv.items) {
            if (it.taken) continue
            if (hypot(x - it.x, (y + 46f) - it.y) < 54f) {
                it.taken = true
                if (it.star) gotStars++ else got++
                spark(it.x, it.y, if (it.star) Color(0xFFFFEE58) else CRYSTAL_COLORS[lv.index % CRYSTAL_COLORS.size], 16)
                onCollect(it.star)
            }
        }
        // — ракета
        if (onGround && abs(x - rocketX) < 62f && y < 8f) {
            if (got >= need) {
                phase = 1; shipT = 0f; celebrateT = 0.2f
                targetX = x
                onLaunch()
            } else if (needMoreCd <= 0f) {
                needMoreCd = 3f
                shipWiggle = 0.7f
                onNeedMore()
            }
        }

        if (jumpAnim >= 0f) { jumpAnim += dt / 0.75f; if (jumpAnim >= 1f) jumpAnim = -1f }

        // — камера
        val want = (x - vw * 0.38f).coerceIn(0f, max(0f, lv.len - vw))
        camX += (want - camX) * min(1f, dt * 6f)
    }

    private fun horizontalGround(dt: Float) {
        waiting = false
        val d = targetX - x
        if (abs(d) <= 5f) { moving = false; running = false; return }
        val dir = if (d > 0f) 1f else -1f
        facing = dir
        val aheadX = x + dir * 28f
        // что впереди?
        val walk = floorTopAt(aheadX, y + 1.5f)
        if (walk == null) {
            val higher = floorTopAt(aheadX, y + 90f)
            if (higher != null) {
                // ступенька вверх — прыгаем
                val need = higher.curTop - y
                val vyJ = sqrt(2f * ZR_G * (need + 28f)).coerceIn(420f, 620f)
                jump(vyJ)
                moving = true
                return
            }
            // пропасть, стена или ничего: смотрим, куда можно прыгнуть
            val range = if (toddler) 150f else 125f
            var landing = false
            var dist = 34f
            while (dist <= range) {
                val lp = floorTopAt(x + dir * dist, y + 85f)
                if (lp != null && lp.curTop >= y - 130f) { landing = true; break }
                dist += 12f
            }
            // есть ли вообще дно впереди (спуск вниз)?
            val below = floorTopAt(aheadX, y - 0.5f)
            if (below != null) {
                // спуск — идём вниз сами
            } else if (landing) {
                jump(540f)
                moving = true
                return
            } else {
                // стоим у края и ждём (плот/лифт/мост)
                val tall = floorTopAt(aheadX, y + 400f)
                if (tall != null && tall.kind != ZR_K_LIFT) { targetX = x; moving = false; running = false; return }
                moving = false; running = false; waiting = true
                return
            }
        }
        running = abs(d) > vw * 0.28f
        val speed = if (running) 230f else 95f
        val stepLen = speed * dt
        x += if (stepLen >= abs(d)) d else dir * stepLen
        moving = true
    }

    private fun applyVertical(dt: Float) {
        val st = standing
        if (onGround && st != null) {
            y = st.curTop
            // всё ещё стоим на платформе?
            if (x < st.left() - 8f || x > st.right() + 8f) { onGround = false; standing = null; vy = 0f }
            return
        }
        if (onGround && st == null) { onGround = false }
        val prevY = y
        vy -= ZR_G * dt
        y += vy * dt
        if (vy <= 0f) {
            var land: ZrPlat? = null
            for (p in lv.plats) {
                if (p.kind == ZR_K_BRIDGE && p.ext < 0.02f) continue
                if (x >= p.left() - 8f && x <= p.right() + 8f) {
                    if (prevY >= p.prevTop - 3f && y <= p.curTop + 0.5f) {
                        if (land == null || p.curTop > land.curTop) land = p
                    }
                }
            }
            if (land != null) {
                y = land.curTop; vy = 0f; onGround = true; standing = land
                if (jumpAnim >= 0.5f) jumpAnim = -1f
            }
        }
    }

    private fun respawn() {
        x = safeX; y = safeY; vy = 0f; onGround = false; standing = null
        targetX = x; fallT = -1f; moving = false
        spark(x, y + 40f, Color.White, 14)
        // найдём платформу под ногами
        val p = floorTopAt(x, y + 2f)
        if (p != null) { y = p.curTop; onGround = true; standing = p }
    }

    private fun stepSparks(dt: Float) {
        val it = sparks.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.vy -= 480f * dt
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.life -= dt
            if (s.life <= 0f) it.remove()
        }
    }

    fun shipLift(): Float = if (phase == 1) shipT * shipT * 62f else if (phase == 2) 500f else 0f
}

// ───────────────────────────── Экран ─────────────────────────────

private fun zrPrefs(ctx: Context) = ctx.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE)
private fun zrStars(ctx: Context, i: Int): Int = try { zrPrefs(ctx).getInt("zr_stars_$i", -1) } catch (e: Exception) { -1 }

@Composable
fun AlienRoomScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val ageProfile = LocalAgeProfile.current
    val dp = LocalDensity.current.density
    val frames = rememberAlienFrames()
    val fireB = rememberFireB()

    val toddler = ageProfile.isToddler2
    val world = remember(toddler) { ZrWorld(toddler) }
    var level by remember { mutableIntStateOf(-1) }       // -1 — карта
    var tick by remember { mutableIntStateOf(0) }
    var gotShown by remember { mutableIntStateOf(0) }
    var starsShown by remember { mutableIntStateOf(0) }
    var needShown by remember { mutableIntStateOf(0) }
    var totalShown by remember { mutableIntStateOf(0) }
    var hint by remember { mutableStateOf("") }
    var winStars by remember { mutableIntStateOf(-1) }
    var mapRefresh by remember { mutableIntStateOf(0) }
    var tappedOnce by remember { mutableIntStateOf(0) }
    var starPulse by remember { mutableFloatStateOf(0f) }

    world.onCollect = { star ->
        gotShown = world.got; starsShown = world.gotStars
        soundManager.play(if (star) "sparkle" else "star_collect", volume = 0.9f)
        soundPlayer.vibrateDragTick()
    }
    world.onJump = { soundManager.play("boing", volume = 0.6f) }
    world.onGiggle = { soundManager.play("giggle"); soundPlayer.vibrateSquishDeform() }
    world.onSplash = { soundManager.play("splash") }
    world.onSpring = { soundManager.play("boing", volume = 0.9f, rate = 1.5f) }
    world.onButton = { soundManager.play("bell_ding", volume = 0.8f); soundPlayer.vibrateMedium() }
    world.onNeedMore = { hint = "Нужно ещё кристаллов 💎: ${world.got}/${world.need}" }
    world.onLaunch = {
        soundManager.play("whoosh")
        soundPlayer.playFanfare()
        soundPlayer.vibrateMedium()
    }
    world.onWin = { s ->
        val prev = zrStars(context, level)
        if (s > prev) try { zrPrefs(context).edit().putInt("zr_stars_$level", s).apply() } catch (e: Exception) { }
        winStars = s
        mapRefresh++
    }

    fun openLevel(i: Int) {
        val lv = buildLevel(i, toddler)
        world.load(lv)
        gotShown = 0; starsShown = 0; needShown = world.need; totalShown = lv.crystals
        winStars = -1; hint = ""
        level = i
    }

    val bgStars = remember {
        val r = Random(7)
        List(70) { Triple(r.nextFloat(), r.nextFloat() * 0.62f, r.nextFloat() * 6.28f) }
    }

    Box(Modifier.fillMaxSize().testTag("alien_room_root")) {
        if (level < 0) {
            // ───── Карта приключений ─────
            val rev = mapRefresh
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF1A1446), Color(0xFF3B2A7A), Color(0xFF6A4BA8)))
                )
            ) {
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 8.dp, start = 12.dp, end = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Приключения Зелика",
                        fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White,
                        modifier = Modifier.padding(start = 76.dp, bottom = 2.dp)
                    )
                    Text(
                        "Собери кристаллы 💎, заправь ракету и лети дальше! Прячутся три звезды ⭐",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE1BEE7), textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(LEVEL_DEFS.size) { i ->
                            val st = zrStars(context, i)
                            val unlocked = i == 0 || zrStars(context, i - 1) >= 0
                            @Suppress("UNUSED_VARIABLE") val r2 = rev
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (unlocked) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.35f),
                                shadowElevation = 4.dp,
                                modifier = Modifier.height(112.dp).clickable(enabled = unlocked) {
                                    soundManager.play("pop")
                                    openLevel(i)
                                }
                            ) {
                                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(if (unlocked) LEVEL_DEFS[i].second else "🔒", fontSize = 32.sp)
                                    Text("${i + 1}. ${LEVEL_DEFS[i].first}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF3B2A7A), textAlign = TextAlign.Center, maxLines = 2)
                                    Text(
                                        if (st < 0) "☆☆☆" else "⭐".repeat(st) + "☆".repeat(3 - st),
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ───── Уровень ─────
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { pos ->
                            tappedOnce++
                            val wx = world.camX + pos.x / dp
                            val wy = world.groundY - pos.y / dp
                            world.tap(wx, wy)
                        }
                    }
            ) {
                world.vw = size.width / dp
                world.vh = size.height / dp
                @Suppress("UNUSED_VARIABLE") val redraw = tick
                zrDrawLevel(world, dp, frames, fireB)
            }

            // Верхняя панель
            Row(
                modifier = Modifier.statusBarsPadding().padding(top = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(88.dp))
                Surface(shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.88f), shadowElevation = 4.dp) {
                    Text(
                        "💎 $gotShown/$totalShown   ⭐ $starsShown/3",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3B2A7A)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.88f), shadowElevation = 4.dp,
                    modifier = Modifier.clickable { level = -1; soundManager.play("pop") }
                ) {
                    Text("🗺️ Карта", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3B2A7A))
                }
            }

            LaunchedEffect(hint) {
                if (hint.isNotEmpty()) { kotlinx.coroutines.delay(2600); hint = "" }
            }
            val tip = when {
                hint.isNotEmpty() -> hint
                tappedOnce == 0 -> "Нажми, куда идти Зелику 👆 Нажми на Зелика — прыжок!"
                else -> ""
            }
            if (tip.isNotEmpty() && winStars < 0) {
                Surface(
                    shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp)
                ) {
                    Text(tip, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3B2A7A))
                }
            }

            if (winStars >= 0) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    KitWindow(width = 330.dp, title = "Уровень пройден!") {
                        Text("⭐".repeat(winStars) + "☆".repeat(3 - winStars), fontSize = 30.sp)
                        Text("Спрятанных звёзд: $winStars из 3", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6A1B4D))
                        Spacer(Modifier.height(6.dp))
                        if (level + 1 < LEVEL_DEFS.size) {
                            KitLongButton(R.drawable.ui_btn_long_green, "Дальше ▶", 190.dp, 15.sp) { openLevel(level + 1) }
                        }
                        KitLongButton(R.drawable.ui_btn_long_blue, "Карта", 150.dp, 14.sp) { level = -1 }
                    }
                }
            }
        }
    }

    LaunchedEffect(world) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L && level >= 0) world.step((now - last) / 1_000_000_000f)
                last = now
                tick++
            }
        }
    }
}

// ───────────────────────────── Рисование ─────────────────────────────

private class ZrTheme(val skyTop: Color, val skyBottom: Color, val hill: Color, val hill2: Color, val grass: Color, val dirt: Color, val water: Color, val stars: Boolean)

private val ZR_THEMES = listOf(
    ZrTheme(Color(0xFF4FC3F7), Color(0xFFE1F5FE), Color(0xFF81C784), Color(0xFF66BB6A), Color(0xFF7CB342), Color(0xFF8D6E63), Color(0xFF29B6F6), false),
    ZrTheme(Color(0xFF26C6DA), Color(0xFFB2EBF2), Color(0xFF4DB6AC), Color(0xFF26A69A), Color(0xFF66BB6A), Color(0xFF8D6E63), Color(0xFF0288D1), false),
    ZrTheme(Color(0xFF2E7D32), Color(0xFFC5E1A5), Color(0xFF388E3C), Color(0xFF1B5E20), Color(0xFF558B2F), Color(0xFF5D4037), Color(0xFF00838F), false),
    ZrTheme(Color(0xFF311B92), Color(0xFF7E57C2), Color(0xFF4527A0), Color(0xFF311B92), Color(0xFF7E57C2), Color(0xFF4A148C), Color(0xFF6A1B9A), true),
    ZrTheme(Color(0xFF81D4FA), Color(0xFFF1F8FF), Color(0xFFB3E5FC), Color(0xFF90CAF9), Color(0xFFE3F2FD), Color(0xFF90A4AE), Color(0xFF4FC3F7), false),
    ZrTheme(Color(0xFF1A1446), Color(0xFFB07CC6), Color(0xFF4A3A8C), Color(0xFF35286B), Color(0xFF5FD3B3), Color(0xFF1E6F6A), Color(0xFF3949AB), true)
)

private fun DrawScope.zrDrawLevel(w: ZrWorld, dp: Float, frames: AlienFrames, fireB: FireSheet) {
    val th = ZR_THEMES[w.lv.theme.coerceIn(0, ZR_THEMES.size - 1)]
    val vw = w.vw
    val vh = w.vh
    val gy = w.groundY
    val cam = w.camX
    val t = w.time

    withTransform({ scale(dp, dp, Offset.Zero) }) {
        // небо
        drawRect(Brush.verticalGradient(listOf(th.skyTop, th.skyBottom), startY = 0f, endY = gy), Offset.Zero, Size(vw, vh))
        if (th.stars) {
            val r = Random(5)
            repeat(50) {
                val sx = r.nextFloat() * vw
                val sy = r.nextFloat() * gy * 0.7f
                val ph = r.nextFloat() * 6.28f
                drawCircle(Color.White.copy(alpha = 0.4f + 0.4f * sin(t * 1.6f + ph)), 1.2f + (ph % 1.5f), Offset(sx, sy))
            }
        } else {
            drawCircle(Color(0xFFFFF59D), 22f, Offset(vw * 0.8f, 46f))
            for (k in 0 until 3) {
                val cx = ((k * 330f - cam * 0.08f - t * 6f) % (vw + 200f) + (vw + 200f)) % (vw + 200f) - 100f
                drawOval(Color.White.copy(alpha = 0.7f), Offset(cx - 50f, 40f + k * 22f), Size(110f, 30f))
                drawOval(Color.White.copy(alpha = 0.7f), Offset(cx - 20f, 28f + k * 22f), Size(70f, 34f))
            }
        }
        // дальние холмы / горы (параллакс)
        for (layer in 0 until 2) {
            val sp = if (layer == 0) 0.2f else 0.4f
            val hp = Path()
            hp.moveTo(0f, vh)
            var xx = 0f
            while (xx <= vw + 40f) {
                val wx = xx + cam * sp
                val hh = (if (layer == 0) 90f else 54f) + sin(wx * 0.006f + layer) * 26f + sin(wx * 0.017f) * 12f
                hp.lineTo(xx, gy - hh)
                xx += 30f
            }
            hp.lineTo(vw, vh); hp.close()
            drawPath(hp, if (layer == 0) th.hill2 else th.hill)
        }

        // вода: всё ниже уровня земли, где нет суши
        drawRect(Brush.verticalGradient(listOf(th.water.copy(alpha = 0.95f), th.water.copy(alpha = 0.7f)), startY = gy, endY = vh), Offset(0f, gy + 8f), Size(vw, vh - gy))
        for (k in 0..(vw / 40f).toInt() + 1) {
            val wx = k * 40f - (cam % 40f)
            drawLine(Color.White.copy(alpha = 0.35f), Offset(wx, gy + 12f + sin(t * 2f + k) * 2f), Offset(wx + 18f, gy + 12f + sin(t * 2f + k + 1f) * 2f), 2.5f)
        }

        // платформы
        for (p in w.lv.plats) {
            val l = p.left() - cam
            val r = p.right() - cam
            if (r < -40f || l > vw + 40f) continue
            val top = gy - p.curTop
            when (p.kind) {
                ZR_K_GROUND -> {
                    drawRect(th.dirt, Offset(l, top), Size(r - l, vh - top))
                    drawRect(th.grass, Offset(l, top), Size(r - l, 12f))
                    var gx = l + 6f
                    while (gx < r - 4f) { drawLine(th.grass, Offset(gx, top), Offset(gx - 2f, top - 5f), 2.5f); gx += 14f }
                    var sx2 = l + 18f
                    while (sx2 < r) { drawCircle(Color.Black.copy(alpha = 0.12f), 5f, Offset(sx2, top + 36f + (sx2 % 30f))); sx2 += 44f }
                }
                ZR_K_LEDGE -> {
                    val thick = if (p.top > 4f) 24f else 18f
                    drawRoundRect(th.dirt, Offset(l, top), Size(r - l, thick), CornerRadius(8f))
                    drawRoundRect(th.grass, Offset(l, top - 2f), Size(r - l, 10f), CornerRadius(6f))
                    if (p.top > 4f) drawRect(Brush.verticalGradient(listOf(th.dirt.copy(alpha = 0.45f), Color.Transparent), startY = top, endY = top + 60f), Offset(l + 4f, top + thick), Size(r - l - 8f, 40f))
                }
                ZR_K_RAFT -> {
                    drawRoundRect(Color(0xFF8D6E63), Offset(l, top), Size(r - l, 16f), CornerRadius(6f))
                    var lx = l + 10f
                    while (lx < r - 6f) { drawLine(Color(0xFF5D4037), Offset(lx, top), Offset(lx, top + 16f), 2f); lx += 18f }
                    drawRoundRect(Color(0xFFBCAAA4), Offset(l, top - 3f), Size(r - l, 5f), CornerRadius(3f))
                }
                ZR_K_LIFT -> {
                    drawLine(Color(0xFF90A4AE), Offset((l + r) / 2f, gy - p.high - 20f), Offset((l + r) / 2f, top), 4f)
                    drawRoundRect(Color(0xFF607D8B), Offset(l, top), Size(r - l, 16f), CornerRadius(5f))
                    drawRoundRect(Color(0xFFFFC107), Offset(l + 6f, top + 4f), Size(r - l - 12f, 5f), CornerRadius(3f))
                    drawCircle(Color(0xFFFFEB3B).copy(alpha = 0.5f + 0.4f * sin(t * 6f)), 4f, Offset(l + 8f, top - 4f))
                }
                ZR_K_BRIDGE -> {
                    if (p.ext > 0.02f) {
                        drawRoundRect(Color(0xFFFFB74D), Offset(l, top), Size(r - l, 14f), CornerRadius(5f))
                        var bx = l + 8f
                        while (bx < r - 4f) { drawLine(Color(0xFFE65100), Offset(bx, top), Offset(bx, top + 14f), 2.5f); bx += 16f }
                        drawRoundRect(Color(0xFFFFE0B2), Offset(l, top - 2f), Size(r - l, 5f), CornerRadius(3f))
                    }
                }
            }
        }
        // кнопки
        for (b in w.lv.buttons) {
            val bx = b.x - cam
            if (bx < -40f || bx > vw + 40f) continue
            drawRoundRect(Color(0xFF455A64), Offset(bx - 24f, gy - 6f), Size(48f, 10f), CornerRadius(5f))
            drawRoundRect(if (b.pressed) Color(0xFF69F0AE) else Color(0xFFFF5252), Offset(bx - 18f, gy - (if (b.pressed) 8f else 14f)), Size(36f, if (b.pressed) 6f else 10f), CornerRadius(5f))
        }
        // пружины-грибы
        for (s in w.lv.springs) {
            val sx = s.x - cam
            if (sx < -50f || sx > vw + 50f) continue
            val sq = s.squash
            val capH = 26f - 10f * sq
            drawRoundRect(Color(0xFFFFF8E1), Offset(sx - 9f, gy - s.top - 18f), Size(18f, 20f), CornerRadius(6f))
            drawOval(Color(0xFFE53935), Offset(sx - 32f, gy - s.top - 18f - capH), Size(64f, capH + 10f))
            drawCircle(Color.White, 5f, Offset(sx - 12f, gy - s.top - 26f - (capH - 16f)))
            drawCircle(Color.White, 4f, Offset(sx + 10f, gy - s.top - 24f - (capH - 16f)))
        }

        // кристаллы и звёзды
        for (it in w.lv.items) {
            if (it.taken) continue
            val ix = it.x - cam
            if (ix < -50f || ix > vw + 50f) continue
            val iy = gy - it.y + sin(t * 2.4f + it.ph) * 4f
            val pulse = 1f + 0.16f * sin(t * 5f + it.ph)
            if (it.star) {
                drawCircle(Color(0xFFFFEE58).copy(alpha = 0.3f), 28f * pulse, Offset(ix, iy))
                val sp = Path()
                for (k in 0 until 10) {
                    val rr = if (k % 2 == 0) 20f else 9f
                    val a = -PI.toFloat() / 2f + k * PI.toFloat() / 5f
                    val px = ix + cos(a) * rr
                    val py = iy + sin(a) * rr
                    if (k == 0) sp.moveTo(px, py) else sp.lineTo(px, py)
                }
                sp.close()
                drawPath(sp, Color(0xFFFFD600)); drawPath(sp, Color.White, style = Stroke(2f))
            } else {
                val col = CRYSTAL_COLORS[w.lv.index % CRYSTAL_COLORS.size]
                drawCircle(col.copy(alpha = 0.28f), 30f * pulse, Offset(ix, iy))
                drawCircle(Color.White.copy(alpha = 0.5f), 30f * pulse, Offset(ix, iy), style = Stroke(1.6f))
                val d = Path().apply { moveTo(ix, iy - 23f); lineTo(ix + 15f, iy); lineTo(ix, iy + 23f); lineTo(ix - 15f, iy); close() }
                drawPath(d, col); drawPath(d, Color.White.copy(alpha = 0.8f), style = Stroke(1.8f))
                drawLine(Color.White.copy(alpha = 0.7f), Offset(ix - 3f, iy - 8f), Offset(ix - 6f, iy - 1f), 2.5f)
            }
        }

        // ракета
        zrDrawRocket(w, cam, gy, t, w.lv.index)
    }

    // — Зелик (в пикселях)
    val sw = w.spriteW * dp
    val sh = w.spriteH * dp
    val visible = !(w.phase == 2) && !(w.fallT >= 0f && w.fallT > 0.15f)
    val ax = (w.x - cam) * dp
    val footY = (gy - w.y) * dp
    if (visible) {
        // тень
        if (w.onGround || w.y < 30f) {
            val sTop = w.standing?.curTop ?: 0f
            drawOval(Color.Black.copy(alpha = 0.2f), Offset(ax - sw * 0.3f, (gy - sTop) * dp - 5f * dp), Size(sw * 0.6f, 12f * dp))
        }
        val anim = when {
            w.giggleT > 0f -> frames.hurt[(w.animClock * 8f).toInt() % 4]
            !w.onGround -> {
                val j = if (w.vy > 250f) 0 else if (w.vy > 0f) 1 else if (w.vy > -250f) 2 else 3
                frames.jump[j]
            }
            w.moving && w.running -> frames.run[(w.animClock * 13f).toInt() % 6]
            w.moving -> frames.walk[(w.animClock * 9f).toInt() % 6]
            else -> frames.idle[if ((w.time % 3.4f) > 3.2f) 1 else 0]
        }
        val shiver = if (w.giggleT > 0f) sin(w.time * 45f) * 3f * dp else 0f
        withTransform({ if (w.facing < 0f) scale(-1f, 1f, Offset(ax, 0f)) }) {
            drawImage(
                anim,
                dstOffset = IntOffset((ax - sw / 2f + shiver).toInt(), (footY - sh).toInt()),
                dstSize = IntSize(sw.toInt(), sh.toInt())
            )
        }
        if (w.giggleT > 0f) {
            for (i in 0 until 3) {
                val hy = footY - sh - 10f * dp - ((w.animClock * 60f + i * 26f) % 80f) * dp * 0.8f
                drawCircle(Color(0xFFFF4081).copy(alpha = 0.8f), 6f * dp, Offset(ax + (i - 1) * 28f * dp, hy))
            }
        }
        if (w.waiting && w.phase == 0) {
            drawCircle(Color.White.copy(alpha = 0.85f), 4f * dp, Offset(ax - 8f * dp, footY - sh - 12f * dp))
            drawCircle(Color.White.copy(alpha = 0.85f), 4f * dp, Offset(ax + 4f * dp, footY - sh - 12f * dp))
            drawCircle(Color.White.copy(alpha = 0.85f), 4f * dp, Offset(ax + 16f * dp, footY - sh - 12f * dp))
        }
    }
    // огонь ракеты
    if (w.phase == 1 && w.shipT > 0.15f) {
        val rx = (w.rocketX - cam) * dp
        val base = (gy - w.shipLift() - 2f) * dp
        val fl = (54f + 14f * sin(w.time * 40f)) * dp
        drawFire(fireB, (w.time * 24f).toInt(), rx, base + fl * 0.35f, fl * 1.9f, -90f)
    }

    withTransform({ scale(dp, dp, Offset.Zero) }) {
        for (s in w.sparks) {
            drawCircle(s.color.copy(alpha = s.life.coerceIn(0f, 1f)), s.size, Offset(s.x - cam, gy - s.y))
        }
        // всплеск воды при падении
        if (w.fallT >= 0f) drawRect(Color(0xFF80D8FF).copy(alpha = (0.45f - w.fallT * 0.4f).coerceIn(0f, 0.45f)), Offset.Zero, Size(vw, vh))
    }
}

private fun DrawScope.zrDrawRocket(w: ZrWorld, cam: Float, gy: Float, t: Float, levelIndex: Int) {
    val sx = w.rocketX - cam
    if (sx < -100f || sx > w.vw + 100f) return
    val lift = w.shipLift()
    val base = gy - 2f - lift
    val shipW = 56f
    val shipH = 150f
    val color = CRYSTAL_COLORS[levelIndex % CRYSTAL_COLORS.size]
    val ready = w.got >= w.need
    val shake = (if (w.phase == 0 && ready) sin(t * 30f) * 1.5f else 0f) + sin(t * 42f) * 4f * w.shipWiggle
    withTransform({ translate(shake, 0f) }) {
        val finL = Path().apply { moveTo(sx - shipW / 2f, base - 30f); lineTo(sx - shipW / 2f - 20f, base + 4f); lineTo(sx - shipW / 2f, base - 4f); close() }
        val finR = Path().apply { moveTo(sx + shipW / 2f, base - 30f); lineTo(sx + shipW / 2f + 20f, base + 4f); lineTo(sx + shipW / 2f, base - 4f); close() }
        drawPath(finL, color); drawPath(finR, color)
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0xFFECEFF1), Color.White, Color(0xFFB0BEC5)), startX = sx - shipW / 2f, endX = sx + shipW / 2f),
            Offset(sx - shipW / 2f, base - shipH), Size(shipW, shipH), CornerRadius(20f)
        )
        val nose = Path().apply {
            moveTo(sx - shipW / 2f, base - shipH + 16f)
            quadraticTo(sx, base - shipH - 40f, sx + shipW / 2f, base - shipH + 16f)
            close()
        }
        drawPath(nose, color)
        drawCircle(Color(0xFF263238), 12f, Offset(sx, base - shipH * 0.62f))
        drawCircle(Color(0xFF80DEEA), 9f, Offset(sx, base - shipH * 0.62f))
        // шкала заправки
        val ratio = (w.got.toFloat() / w.need.coerceAtLeast(1)).coerceIn(0f, 1f)
        drawRoundRect(Color(0xFF455A64).copy(alpha = 0.6f), Offset(sx - 10f, base - shipH * 0.40f), Size(20f, shipH * 0.30f), CornerRadius(5f))
        if (ratio > 0f) {
            val fh = shipH * 0.30f * ratio
            drawRoundRect(color, Offset(sx - 10f, base - shipH * 0.10f - fh), Size(20f, fh), CornerRadius(5f))
        }
    }
}
