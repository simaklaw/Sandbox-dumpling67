package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.example.shared.SoundManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * «Зелик: Приключение» — платформер с оружием (по мотивам присланной игры Stickman Arsenal).
 *
 * Идём вправо через уровень: прыгаем по платформам, собираем монеты и сердечки, побеждаем
 * летающих кляксов и в конце — большого босса. Три вида оружия:
 *  • лучевой пистолет (быстрые выстрелы),
 *  • плазменная граната (отскакивает и взрывается),
 *  • звёздные ракеты с самонаведением (присесть + огонь; патроны выпадают из монстров).
 * У Зелика 10 единиц здоровья и 3 жизни; когда здоровье кончается, он красиво падает.
 */

// ---------- Константы (мировые единицы, шаг физики — 60 кадров в секунду) ----------
private const val GRAVITY = 0.504f
private const val MOVE_SPEED = 3.36f
private const val JUMP_POWER = -13.44f
private const val MAX_FALL = 17.6f
private const val GROUND_Y = 500f
private const val BOLT_RELOAD = 26
private const val GRENADE_RELOAD = 55
private const val INVULN_TIME = 70
private const val BOLT_SPEED = 7.5f
private const val BOLT_DAMAGE = 10
private const val GRENADE_DAMAGE = 15
private const val HOMING_DAMAGE = 30
private const val GRENADE_SPEED_X = MOVE_SPEED * 1.6f
private const val GRENADE_SPEED_Y = -8.5f
private const val GRENADE_RADIUS = 110f
private const val GRENADE_FUSE = 75
private const val GRENADE_GRAVITY = 0.4f
private const val HOMING_MAX_AMMO = 3
private const val HOMING_SPEED = MOVE_SPEED * 0.9f
private const val HOMING_FUSE = 300
private const val HOMING_RADIUS = GRENADE_RADIUS * 1.15f
private const val MORTAR_COOLDOWN = 90
private const val PLAYER_MAX_HP = 10
private const val START_LIVES = 3
private const val SPRITE_H = 84f

private val MOB_HP = intArrayOf(10, 20, 30)
private val MOB_DMG = intArrayOf(2, 3, 5)

// ---------- Модели ----------
private class ZPlat(val x: Float, val y: Float, val w: Float, val h: Float)

private class ZEnemy(
    var x: Float, var y: Float, val r: Float, val tier: Int, var hp: Int, val maxHp: Int,
    val vx: Float, var dir: Float, val baseX: Float, val range: Float
) {
    var hitFlash = 0
    val anim = Random.nextFloat() * 6.28f
}

private class ZCured(val x: Float, val bottom: Float, val h: Float) { var age = 0 }

private class ZCoin(val x: Float, val y: Float, val phase: Float) { var taken = false }
private class ZMed(val x: Float, val y: Float) { var taken = false }

private class ZBoss(
    var x: Float, var y: Float, val w: Float, val h: Float, var hp: Int, val maxHp: Int, val shootInterval: Int
) {
    var hitFlash = 0
    var active = false
    var dead = false
    var shootTimer = 90
    var kind = 0          // 0 — пурпурный, 1 — лесной, 2 — огненный
    var phase = 0         // 0..2 — по потере здоровья
    var state = 0         // 0 ходьба, 1 разгон (предупреждение), 2 рывок, 3 прыжок вверх, 4 падение
    var stateT = 0
    var skillCd = 120
    var lastSkill = -1
    var vx = 0f
    var vy = 0f
    var dashDir = 1f
    var deadT = 0
}

private class ZShock(var x: Float, val dir: Float) { var life = 150 }
private class ZRainCoin(var x: Float, var y: Float, var vx: Float, var vy: Float) { var life = 1100 }

private class ZBolt(var x: Float, var y: Float, val vx: Float) { var trail = 0 }

private class ZGrenade(var x: Float, var y: Float, var vx: Float, var vy: Float) {
    val r = 7f
    var rot = 0f
    var rotSpeed = 0.3f
    var fuse = GRENADE_FUSE
}

private class ZHoming(var x: Float, var y: Float, var vx: Float, var vy: Float) {
    val r = 8f
    var life = HOMING_FUSE
    var target: ZEnemy? = null
    var targetBoss = false
    var angle = 0f
    var trail = 0
}

private class ZBossRocket(var x: Float, var y: Float, var vx: Float, var vy: Float, val straight: Boolean = false) {
    val r = 8f
    var life = 300
    var up = true
    var homingTimer = 20
    var angle = -1.57f
    var trail = 0
}

private class ZPickup(var x: Float, var y: Float, var vx: Float, var vy: Float) { var phase = 0f }

private class ZPart(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val decay: Float, val size: Float, val color: Color
)

private class ZWave(val x: Float, val y: Float, var r: Float, val maxR: Float, var life: Float, val color: Color)

private class ZSmoke(
    var x: Float, var y: Float, var r: Float, val vr: Float, var life: Float,
    val decay: Float, val vx: Float, val vy: Float
)

private class ZInput {
    var stickX = 0f
    var stickY = 0f
    var jump = false
    var attack = false
}

private data class ZHud(
    val hp: Int = PLAYER_MAX_HP,
    val lives: Int = START_LIVES,
    val coins: Int = 0,
    val ammo: Int = HOMING_MAX_AMMO,
    val weapon: Int = 0,
    val bossHp: Int = 0,
    val bossPhase: Int = 0,
    val bossMax: Int = 1,
    val bossActive: Boolean = false,
    val mobsLeft: Int = 0,
    val level: Int = 1
)

private class ZHelperGfx(val imgs: MonImgs, val spec: MonSpec, val anim: MonAnim)

private enum class ZScreen { MENU, PLAY, PAUSED, LEVEL_DONE, GAME_OVER }

private class ZFrames(
    val idle: List<ImageBitmap>,
    val walk: List<ImageBitmap>,
    val run: List<ImageBitmap>,
    val jump: List<ImageBitmap>,
    val hurt: List<ImageBitmap>,
    val dead: List<ImageBitmap>,
    val fire: List<ImageBitmap>,   // 0 — пистолет в руке, 1 — выстрел со вспышкой
    val attack: List<ImageBitmap>, // 0 — «заряд в глазах», 1 — бросок рукой
    val sunWalk: List<ImageBitmap>,
    val sunFade: List<ImageBitmap>,
    val sunCured: ImageBitmap
)

@Composable
private fun rememberZFrames(): ZFrames {
    val idle = listOf(
        ImageBitmap.imageResource(R.drawable.alien_idle_1),
        ImageBitmap.imageResource(R.drawable.alien_idle_2)
    )
    val walk = listOf(
        ImageBitmap.imageResource(R.drawable.alien_walk_1), ImageBitmap.imageResource(R.drawable.alien_walk_2),
        ImageBitmap.imageResource(R.drawable.alien_walk_3), ImageBitmap.imageResource(R.drawable.alien_walk_4),
        ImageBitmap.imageResource(R.drawable.alien_walk_5), ImageBitmap.imageResource(R.drawable.alien_walk_6)
    )
    val run = listOf(
        ImageBitmap.imageResource(R.drawable.alien_run_1), ImageBitmap.imageResource(R.drawable.alien_run_2),
        ImageBitmap.imageResource(R.drawable.alien_run_3), ImageBitmap.imageResource(R.drawable.alien_run_4),
        ImageBitmap.imageResource(R.drawable.alien_run_5), ImageBitmap.imageResource(R.drawable.alien_run_6)
    )
    val jump = listOf(
        ImageBitmap.imageResource(R.drawable.alien_jump_1), ImageBitmap.imageResource(R.drawable.alien_jump_2),
        ImageBitmap.imageResource(R.drawable.alien_jump_3), ImageBitmap.imageResource(R.drawable.alien_jump_4)
    )
    val hurt = listOf(
        ImageBitmap.imageResource(R.drawable.alien_hurt_1), ImageBitmap.imageResource(R.drawable.alien_hurt_2),
        ImageBitmap.imageResource(R.drawable.alien_hurt_3), ImageBitmap.imageResource(R.drawable.alien_hurt_4)
    )
    val dead = listOf(
        ImageBitmap.imageResource(R.drawable.alien_dead_1), ImageBitmap.imageResource(R.drawable.alien_dead_2),
        ImageBitmap.imageResource(R.drawable.alien_dead_3), ImageBitmap.imageResource(R.drawable.alien_dead_4),
        ImageBitmap.imageResource(R.drawable.alien_dead_5)
    )
    val fire = listOf(
        ImageBitmap.imageResource(R.drawable.alien_fire_5), ImageBitmap.imageResource(R.drawable.alien_fire_6)
    )
    val attack = listOf(
        ImageBitmap.imageResource(R.drawable.alien_attack_1), ImageBitmap.imageResource(R.drawable.alien_attack_2)
    )
    val sunWalk = listOf(
        ImageBitmap.imageResource(R.drawable.sun_walk_1), ImageBitmap.imageResource(R.drawable.sun_walk_2),
        ImageBitmap.imageResource(R.drawable.sun_walk_3)
    )
    val sunFade = listOf(
        ImageBitmap.imageResource(R.drawable.sun_fade_1), ImageBitmap.imageResource(R.drawable.sun_fade_2),
        ImageBitmap.imageResource(R.drawable.sun_fade_3)
    )
    val sunCured = ImageBitmap.imageResource(R.drawable.sun_cured)
    return remember { ZFrames(idle, walk, run, jump, hurt, dead, fire, attack, sunWalk, sunFade, sunCured) }
}

// ---------- Игровой мир ----------
private class ZelikWorld(val diff: Int, val input: ZInput) {
    // diff: 0 — легко, 1 — средне, 2 — сложно
    var level = 1
    var lives = START_LIVES
    var coinCount = 0
    var weapon = 0 // 0 — лучевой пистолет, 1 — граната
    var levelLength = 0f
    var arenaLeft = 0f
    var arenaRight = 0f
    var vw = 800f
    var vh = 800f
    var animTime = 0
    var levelDone = false
    var mobsTotal = 0

    val platforms = ArrayList<ZPlat>()
    val enemies = ArrayList<ZEnemy>()
    val coins = ArrayList<ZCoin>()
    val cured = ArrayList<ZCured>()
    val meds = ArrayList<ZMed>()
    var boss = ZBoss(0f, 0f, 70f, 110f, 1, 1, 200)

    val bolts = ArrayList<ZBolt>()
    val grenades = ArrayList<ZGrenade>()
    val homings = ArrayList<ZHoming>()
    val bossRockets = ArrayList<ZBossRocket>()
    val shocks = ArrayList<ZShock>()
    val rainCoins = ArrayList<ZRainCoin>()
    val pickups = ArrayList<ZPickup>()
    val parts = ArrayList<ZPart>()
    val waves = ArrayList<ZWave>()
    val smokes = ArrayList<ZSmoke>()

    // игрок
    var px = 100f
    var py = GROUND_Y - 78f
    val pw = 30f
    val ph = 76f
    var pvx = 0f
    var pvy = 0f
    var onGround = false
    var facing = 1f
    var attackFacing = 1f
    var shootCooldown = 0
    var fireAnim = 0
    var fireKind = 0 // 1 — выстрел, 2 — бросок, 3 — ракета
    var hurtAnim = 0
    var invuln = 0
    var hp = PLAYER_MAX_HP
    var dead = false
    var deadTimer = 0
    var crouching = false
    var mortarCooldown = 0
    var airJumps = 1
    var jumpPrev = false
    var wingT = -1
    var healT = -1
    var ammo = HOMING_MAX_AMMO

    var camX = 0f
    var camY = 0f

    // монстрик-помощник (из «Фабрики монстриков»)
    var helperOn = false
    var hx = 60f
    var hy = GROUND_Y - 100f
    var helperCd = 90
    var helperHeal = 0
    var helperDir = 1f

    /** События для звука/интерфейса. */
    var onEvent: (String) -> Unit = {}

    private fun dmgScale(d: Int): Int = if (diff == 0) (d + 1) / 2 else d

    fun startLevel(n: Int) {
        level = n
        levelDone = false
        val rng = Random(n * 1337 + Random.nextInt(99999))
        levelLength = 6000f + n * 1200f
        arenaLeft = levelLength - 700f
        arenaRight = levelLength + 150f

        platforms.clear()
        platforms.add(ZPlat(-200f, GROUND_Y, levelLength + 500f, 300f))
        val platCount = 20 + n * 3
        val occupied = ArrayList<Pair<Float, Float>>()
        val heights = floatArrayOf(240f, 260f, 280f, 300f, 320f, 340f, 360f, 380f, 400f, 420f)
        repeat(platCount) {
            var x: Float
            var y: Float
            var w: Float
            var tries = 0
            do {
                x = 300f + rng.nextFloat() * (levelLength - 500f)
                y = heights[rng.nextInt(heights.size)]
                w = 120f + rng.nextInt(120)
                tries++
                if (tries > 30) break
            } while (
                occupied.any { abs(it.first - x) < 180f && abs(it.second - y) < 80f } ||
                (x + w > arenaLeft - 100f && x < arenaRight + 100f)
            )
            occupied.add(Pair(x, y))
            platforms.add(ZPlat(x, y, w, 18f))
        }
        platforms.add(ZPlat(arenaLeft + 30f, 380f, 140f, 18f))
        platforms.add(ZPlat(arenaRight - 160f, 380f, 140f, 18f))

        enemies.clear()
        val base = 18 + n * 3
        val total = if (diff == 0) (base * 0.6f).toInt() else base
        mobsTotal = total
        val tiers = MutableList(total) { 0 }
        if (diff == 1) {
            val c2 = (total * 0.4f).toInt()
            for (i in 0 until c2) tiers[i] = 1
        } else if (diff == 2) {
            val c1 = (total * 0.4f).toInt()
            val c2 = (total * 0.3f).toInt()
            for (i in c1 until c1 + c2) tiers[i] = 1
            for (i in c1 + c2 until total) tiers[i] = 2
        }
        tiers.shuffle(rng)
        for (i in 0 until total) {
            val r = (16f + rng.nextInt(10)) * 1.25f
            val ex: Float
            val ey: Float
            val onPlatform = rng.nextFloat() < 0.5f && platforms.size > 1
            val p = if (onPlatform) platforms[1 + rng.nextInt(platforms.size - 1)] else null
            if (p != null && !(p.x + p.w > arenaLeft - 100f && p.x < arenaRight + 100f)) {
                ex = p.x + 20f + rng.nextFloat() * (p.w - 40f)
                ey = p.y - r - 4f
            } else {
                ex = 400f + rng.nextFloat() * (arenaLeft - 800f)
                ey = GROUND_Y - r - 8f
            }
            val tier = tiers[i]
            enemies.add(
                ZEnemy(
                    ex, ey, r, tier, MOB_HP[tier], MOB_HP[tier],
                    0.7f + rng.nextFloat() * 0.7f,
                    if (rng.nextBoolean()) 1f else -1f,
                    ex, 60f + rng.nextFloat() * 80f
                )
            )
        }

        coins.clear()
        cured.clear()
        val coinTarget = 40 + n * 5
        for (i in 0 until coinTarget) {
            val cx = 250f + rng.nextFloat() * (levelLength - 500f)
            val cy: Float
            if (rng.nextFloat() < 0.5f) {
                cy = GROUND_Y - 30f - rng.nextFloat() * 40f
            } else {
                val near = platforms.firstOrNull { it.h == 18f && abs((it.x + it.w / 2f) - cx) < 120f }
                cy = if (near != null) near.y - 30f - rng.nextFloat() * 40f else 200f + rng.nextFloat() * 250f
            }
            coins.add(ZCoin(cx, cy, rng.nextFloat() * 6.28f))
        }

        meds.clear()
        meds.add(ZMed(levelLength * 0.25f, GROUND_Y - 40f))
        meds.add(ZMed(levelLength * 0.5f, GROUND_Y - 40f))
        meds.add(ZMed(levelLength * 0.75f, GROUND_Y - 40f))

        val bossHp = ((when (diff) { 0 -> 40; 1 -> 60; else -> 80 }) * 1.8f).toInt() + (n - 1) * 16
        val interval = max(110, 190 - n * 12) + (if (diff == 0) 50 else 0)
        boss = ZBoss(levelLength - 300f, GROUND_Y - 140f, 92f, 140f, bossHp, bossHp, interval)
        boss.kind = biomeOfLevel(n)
        shocks.clear(); rainCoins.clear()

        bolts.clear(); grenades.clear(); homings.clear(); bossRockets.clear()
        pickups.clear(); parts.clear(); waves.clear(); smokes.clear()

        px = 100f
        py = GROUND_Y - ph - 2f
        pvx = 0f; pvy = 0f
        onGround = false
        facing = 1f
        shootCooldown = 0; fireAnim = 0; hurtAnim = 0
        invuln = 60
        dead = false; deadTimer = 0
        crouching = false
        mortarCooldown = 0
        ammo = HOMING_MAX_AMMO
        hp = PLAYER_MAX_HP
        camX = px - vw / 2f
        camY = py - vh * 0.6f
    }

    fun restartLevel() {
        lives = START_LIVES
        startLevel(level)
    }

    fun toggleWeapon() {
        weapon = 1 - weapon
        onEvent("switch")
    }

    // ----- эффекты -----
    private val fxColors = listOf(Color(0xFFE040FB), Color(0xFFFFEB3B), Color(0xFFFFFFFF), Color(0xFF80DEEA))

    private fun spawnDeathFX(x: Float, y: Float) {
        repeat(14) {
            val a = Random.nextFloat() * 6.28f
            val s = Random.nextFloat() * 4f + 1.5f
            parts.add(ZPart(x, y, cos(a) * s, sin(a) * s - 1f, 1f, 0.03f, 3f + Random.nextFloat() * 3f, fxColors.random()))
        }
        waves.add(ZWave(x, y, 4f, 40f, 1f, Color(0xFFE040FB)))
    }

    private fun spawnBigExplosion(x: Float, y: Float, radius: Float) {
        waves.add(ZWave(x, y, 10f, radius, 1f, Color(0xFFFFB300)))
        waves.add(ZWave(x, y, 6f, radius * 0.7f, 1f, Color(0xFFE040FB)))
        repeat(26) {
            val a = Random.nextFloat() * 6.28f
            val s = Random.nextFloat() * 6f + 2f
            parts.add(ZPart(x, y, cos(a) * s, sin(a) * s - 2f, 1f, 0.025f, 3f + Random.nextFloat() * 4f, fxColors.random()))
        }
        repeat(8) {
            smokes.add(
                ZSmoke(x + (Random.nextFloat() - 0.5f) * 30f, y + (Random.nextFloat() - 0.5f) * 30f,
                    6f + Random.nextFloat() * 6f, 0.7f, 1f, 0.025f,
                    (Random.nextFloat() - 0.5f) * 1.2f, -Random.nextFloat() * 1.2f)
            )
        }
    }

    private fun spawnSmoke(x: Float, y: Float) {
        smokes.add(ZSmoke(x, y, 3f, 0.35f, 1f, 0.06f, (Random.nextFloat() - 0.5f) * 0.4f, (Random.nextFloat() - 0.5f) * 0.4f))
    }

    private fun spawnMuzzle(x: Float, y: Float, dir: Float) {
        repeat(8) {
            parts.add(
                ZPart(x, y, dir * (Random.nextFloat() * 4f + 1f), (Random.nextFloat() - 0.5f) * 3f,
                    1f, 0.08f, 2f + Random.nextFloat() * 2f, fxColors.random())
            )
        }
    }

    // ----- игрок -----
    private fun overlapsPlat(p: ZPlat): Boolean =
        px < p.x + p.w && px + pw > p.x && py < p.y + p.h && py + ph > p.y

    private fun hurtPlayer(dmgRaw: Int, fromX: Float, kick: Float, up: Float) {
        if (dead || invuln > 0) return
        val dmg = dmgScale(dmgRaw)
        hp -= dmg
        invuln = INVULN_TIME
        hurtAnim = 24
        val dir = if ((px + pw / 2f) - fromX >= 0f) 1f else -1f
        pvx += dir * kick
        pvy = up
        if (hp <= 0) killPlayer() else onEvent("hurt")
    }

    private fun killPlayer() {
        if (dead) return
        dead = true
        hp = 0
        deadTimer = 0
        pvy = -6f
        onEvent("fall")
    }

    private fun respawn() {
        dead = false
        hp = PLAYER_MAX_HP
        px = max(100f, px - 300f)
        if (boss.active && !boss.dead) px = min(px, arenaLeft + 100f)
        py = GROUND_Y - ph - 2f
        pvx = 0f; pvy = 0f
        invuln = 150
        fireAnim = 0
        hurtAnim = 0
    }

    private fun updatePlayer() {
        if (invuln > 0) invuln--
        if (shootCooldown > 0) shootCooldown--
        if (fireAnim > 0) fireAnim--
        if (hurtAnim > 0) hurtAnim--
        if (mortarCooldown > 0) mortarCooldown--

        var moveX = 0f
        if (dead) {
            deadTimer++
            if (deadTimer == 110) {
                lives--
                if (lives <= 0) onEvent("gameover") else respawn()
            }
            crouching = false
            pvx = 0f
        } else {
            if (abs(input.stickX) > 0.25f) moveX = input.stickX.coerceIn(-1f, 1f)
            crouching = input.stickY > 0.55f && onGround
            val speedMul = if (crouching) 0.5f else 1f
            pvx = moveX * MOVE_SPEED * speedMul

            if (onGround) airJumps = 1
            if (input.jump && onGround && !crouching) {
                pvy = JUMP_POWER
                onGround = false
                onEvent("jump")
            } else if (input.jump && !jumpPrev && !onGround && airJumps > 0 && !crouching) {
                // второй прыжок — на крыльях
                airJumps--
                pvy = JUMP_POWER * 0.92f
                wingT = 0
                onEvent("wings")
            }
            jumpPrev = input.jump

            if (input.attack) {
                if (crouching) {
                    if (mortarCooldown <= 0 && ammo > 0) {
                        mortarCooldown = MORTAR_COOLDOWN
                        ammo--
                        fireKind = 3; fireAnim = 14
                        launchHoming()
                        onEvent("mortar")
                    }
                } else if (shootCooldown <= 0) {
                    attackFacing = facing
                    if (weapon == 0) {
                        shootCooldown = BOLT_RELOAD
                        fireKind = 1; fireAnim = 14
                        fireBolt()
                        onEvent("shoot")
                    } else {
                        shootCooldown = GRENADE_RELOAD
                        fireKind = 2; fireAnim = 14
                        throwGrenade()
                        onEvent("throw")
                    }
                }
            }
        }

        pvy += GRAVITY
        if (pvy > MAX_FALL) pvy = MAX_FALL

        px += pvx
        if (px < 20f) px = 20f
        if (boss.active && !boss.dead && px + pw > arenaRight) px = arenaRight - pw
        for (p in platforms) {
            if (overlapsPlat(p)) {
                if (pvx > 0f) px = p.x - pw else if (pvx < 0f) px = p.x + p.w
            }
        }
        py += pvy
        onGround = false
        for (p in platforms) {
            if (overlapsPlat(p)) {
                if (pvy > 0f) { py = p.y - ph; pvy = 0f; onGround = true }
                else if (pvy < 0f) { py = p.y + p.h; pvy = 0f }
            }
        }

        if (moveX > 0.2f) facing = 1f
        if (moveX < -0.2f) facing = -1f
        if (dead) return

        val cx = px + pw / 2f
        val cy = py + ph / 2f
        for (c in coins) {
            if (c.taken) continue
            if (hypot(cx - c.x, cy - c.y) < 9f + 24f) {
                c.taken = true
                coinCount++
                onEvent("coin")
                repeat(6) {
                    val a = Random.nextFloat() * 6.28f
                    parts.add(ZPart(c.x, c.y, cos(a) * 2f, sin(a) * 2f - 1f, 1f, 0.05f, 2.5f, Color(0xFFFFD54F)))
                }
            }
        }
        for (m in meds) {
            if (m.taken) continue
            if (hypot(cx - m.x, cy - m.y) < 12f + 26f && hp < PLAYER_MAX_HP) {
                m.taken = true
                hp = min(PLAYER_MAX_HP, hp + 4)
                healT = 0
                onEvent("heal")
            }
        }

        if (!boss.active && !boss.dead && px > arenaLeft - 50f) {
            boss.active = true
            onEvent("bossRoar")
            boss.shootTimer = 90
            onEvent("roar")
        }
        if (py > GROUND_Y + 400f) killPlayer()
    }

    private fun fireBolt() {
        val f = attackFacing
        val mx = px + pw / 2f + f * 44f
        val my = py + ph / 2f - 6f
        spawnMuzzle(mx, my, f)
        bolts.add(ZBolt(mx, my, f * BOLT_SPEED))
    }

    private fun throwGrenade() {
        val f = attackFacing
        val g = ZGrenade(px + pw / 2f + f * 20f, py + ph / 2f - 14f, f * GRENADE_SPEED_X, GRENADE_SPEED_Y)
        g.rotSpeed = f * 0.35f
        grenades.add(g)
    }

    private fun nearestTarget(x: Float, y: Float): Pair<ZEnemy?, Boolean> {
        var best: ZEnemy? = null
        var bestD = Float.MAX_VALUE
        for (e in enemies) {
            val d = hypot(e.x - x, e.y - y)
            if (d < bestD) { bestD = d; best = e }
        }
        if (boss.active && !boss.dead) {
            val d = hypot(boss.x + boss.w / 2f - x, boss.y + boss.h / 2f - y)
            if (d < bestD) return Pair(null, true)
        }
        return Pair(best, false)
    }

    private fun launchHoming() {
        val f = facing
        val sx = px + pw / 2f
        val sy = py + ph - 24f
        val angle = -PI.toFloat() / 2f + f * 0.25f
        val h = ZHoming(sx, sy, cos(angle) * HOMING_SPEED * 0.6f, sin(angle) * HOMING_SPEED * 0.8f)
        val (t, b) = nearestTarget(sx, sy)
        h.target = t; h.targetBoss = b
        homings.add(h)
        repeat(8) { spawnSmoke(sx + (Random.nextFloat() - 0.5f) * 12f, sy + (Random.nextFloat() - 0.5f) * 8f) }
    }

    private fun maybeDrop(e: ZEnemy) {
        if (Random.nextFloat() < 0.18f) {
            pickups.add(ZPickup(e.x, e.y, (Random.nextFloat() - 0.5f) * 1.5f, -4f))
        }
    }

    private fun killEnemy(e: ZEnemy) {
        if (e.tier >= 1) {
            // Подсолнух «расколдован»: гаснет и остаётся добрым цветком.
            cured.add(ZCured(e.x, e.y + e.r + 2f, e.r * 3.6f))
            repeat(8) {
                val a = Random.nextFloat() * 6.28f
                parts.add(ZPart(e.x, e.y, cos(a) * 2.5f, sin(a) * 2.5f - 1.5f, 1f, 0.04f, 3f, Color(0xFFFFD54F)))
            }
            onEvent("heal")
        } else {
            spawnDeathFX(e.x, e.y)
            onEvent("boom_small")
        }
        maybeDrop(e)
        enemies.remove(e)
    }

    private fun damageBoss(amount: Int) {
        boss.hp -= amount
        boss.hitFlash = 12
        onEvent("bossHit")
        val frac = boss.hp.toFloat() / boss.maxHp
        val ph = if (frac < 0.34f) 2 else if (frac < 0.67f) 1 else 0
        if (ph > boss.phase && boss.hp > 0) {
            boss.phase = ph
            boss.state = 0; boss.stateT = 0; boss.skillCd = 50; boss.vx = 0f
            boss.y = GROUND_Y - boss.h
            spawnBigExplosion(boss.x + boss.w / 2f, boss.y + boss.h / 2f, 120f)
            onEvent("bossPhase")
        }
        if (boss.hp <= 0) {
            boss.dead = true
            boss.deadT = 0
            shocks.clear()
            enemies.clear()
            val bcx = boss.x + boss.w / 2f
            val bcy = boss.y + boss.h / 2f
            repeat(5) { spawnBigExplosion(bcx + (Random.nextFloat() - 0.5f) * 60f, bcy + (Random.nextFloat() - 0.5f) * 60f, 70f) }
            bossRockets.clear()
            onEvent("bossDead")
        }
    }

    private fun updateBolts() {
        val it = bolts.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.x += b.vx
            b.trail++
            var hit = false
            var j = enemies.size - 1
            while (j >= 0) {
                val e = enemies[j]
                if (hypot(b.x - e.x, b.y - e.y) < e.r + 8f) {
                    e.hp -= BOLT_DAMAGE
                    e.hitFlash = 10
                    if (e.hp <= 0) killEnemy(e) else onEvent("hit")
                    hit = true
                    break
                }
                j--
            }
            if (!hit && boss.active && !boss.dead) {
                val bcx = boss.x + boss.w / 2f
                val bcy = boss.y + boss.h / 2f
                if (hypot(b.x - bcx, b.y - bcy) < max(boss.w, boss.h) / 2f + 8f) {
                    spawnDeathFX(b.x, b.y)
                    damageBoss(BOLT_DAMAGE)
                    hit = true
                }
            }
            if (!hit) {
                for (p in platforms) {
                    if (b.x > p.x && b.x < p.x + p.w && b.y > p.y && b.y < p.y + p.h) {
                        spawnDeathFX(b.x, b.y)
                        hit = true
                        break
                    }
                }
            }
            if (hit || b.x < camX - 300f || b.x > camX + vw + 300f) it.remove()
        }
    }

    private fun explodeAt(x: Float, y: Float, radius: Float, dmg: Int, selfDmg: Int) {
        spawnBigExplosion(x, y, radius)
        onEvent("boom")
        var j = enemies.size - 1
        while (j >= 0) {
            val e = enemies[j]
            if (hypot(e.x - x, e.y - y) < radius + e.r) {
                e.hp -= dmg
                e.hitFlash = 12
                if (e.hp <= 0) killEnemy(e)
            }
            j--
        }
        if (boss.active && !boss.dead) {
            val bcx = boss.x + boss.w / 2f
            val bcy = boss.y + boss.h / 2f
            if (hypot(bcx - x, bcy - y) < radius + max(boss.w, boss.h) / 2f) damageBoss(dmg)
        }
        if (diff > 0 && !dead && invuln <= 0 && hypot(px + pw / 2f - x, py + ph / 2f - y) < radius + 10f) {
            hurtPlayer(selfDmg, x, 7f, -8f)
        }
    }

    private fun updateGrenades() {
        val it = grenades.iterator()
        while (it.hasNext()) {
            val g = it.next()
            g.vy += GRENADE_GRAVITY
            g.x += g.vx
            g.y += g.vy
            g.rot += g.rotSpeed
            g.fuse--
            for (p in platforms) {
                if (g.x + g.r > p.x && g.x - g.r < p.x + p.w && g.y + g.r > p.y && g.y - g.r < p.y + p.h) {
                    val oTop = (g.y + g.r) - p.y
                    val oBot = (p.y + p.h) - (g.y - g.r)
                    val oL = (g.x + g.r) - p.x
                    val oR = (p.x + p.w) - (g.x - g.r)
                    val m = min(min(oTop, oBot), min(oL, oR))
                    if (m == oTop && g.vy > 0f) { g.y = p.y - g.r; g.vy *= -0.4f; g.vx *= 0.75f }
                    else if (m == oBot && g.vy < 0f) { g.y = p.y + p.h + g.r; g.vy *= -0.3f }
                    else if (m == oL && g.vx > 0f) { g.x = p.x - g.r; g.vx *= -0.5f }
                    else if (m == oR && g.vx < 0f) { g.x = p.x + p.w + g.r; g.vx *= -0.5f }
                }
            }
            for (e in enemies) {
                val dx = g.x - e.x
                val dy = g.y - e.y
                val d = hypot(dx, dy)
                val minD = g.r + e.r
                if (d < minD && d > 0.001f) {
                    val nx = dx / d
                    val ny = dy / d
                    g.x = e.x + nx * minD
                    g.y = e.y + ny * minD
                    val dot = g.vx * nx + g.vy * ny
                    if (dot < 0f) {
                        g.vx -= 1.55f * dot * nx
                        g.vy -= 1.55f * dot * ny
                        g.vx *= 0.92f; g.vy *= 0.92f
                    }
                }
            }
            if (boss.active && !boss.dead) {
                val bcx = boss.x + boss.w / 2f
                val bcy = boss.y + boss.h / 2f
                val half = max(boss.w, boss.h) / 2f
                val dx = g.x - bcx
                val dy = g.y - bcy
                val d = hypot(dx, dy)
                if (d < g.r + half && d > 0.001f) {
                    val nx = dx / d
                    val ny = dy / d
                    g.x = bcx + nx * (g.r + half)
                    g.y = bcy + ny * (g.r + half)
                    val dot = g.vx * nx + g.vy * ny
                    if (dot < 0f) { g.vx -= 1.55f * dot * nx; g.vy -= 1.55f * dot * ny; g.vx *= 0.92f; g.vy *= 0.92f }
                }
            }
            if (g.fuse <= 0) {
                explodeAt(g.x, g.y, GRENADE_RADIUS, GRENADE_DAMAGE, 3)
                it.remove()
            }
        }
    }

    private fun updateHoming() {
        val it = homings.iterator()
        while (it.hasNext()) {
            val rk = it.next()
            rk.life--
            rk.trail++
            if (rk.trail % 2 == 0) spawnSmoke(rk.x, rk.y)

            val tAlive = (rk.target != null && enemies.contains(rk.target)) || (rk.targetBoss && boss.active && !boss.dead)
            if (!tAlive) {
                val (t, b) = nearestTarget(rk.x, rk.y)
                rk.target = t; rk.targetBoss = b
            }
            var tx = 0f
            var ty = 0f
            var has = false
            if (rk.targetBoss) { tx = boss.x + boss.w / 2f; ty = boss.y + boss.h / 2f; has = true }
            else rk.target?.let { tx = it.x; ty = it.y; has = true }

            if (has) {
                val dx = tx - rk.x
                val dy = ty - rk.y
                val len = hypot(dx, dy).coerceAtLeast(0.001f)
                rk.vx += (dx / len * HOMING_SPEED - rk.vx) * 0.06f
                rk.vy += (dy / len * HOMING_SPEED - rk.vy) * 0.06f
            } else {
                rk.vy -= 0.08f
                if (rk.vy < -HOMING_SPEED * 0.5f) rk.vy = -HOMING_SPEED * 0.5f
                rk.vx *= 0.99f
            }
            rk.x += rk.vx
            rk.y += rk.vy
            if (abs(rk.vx) > 0.01f || abs(rk.vy) > 0.01f) rk.angle = atan2(rk.vy, rk.vx)

            var hit = false
            if (has) {
                val rad = if (rk.targetBoss) max(boss.w, boss.h) / 2f else (rk.target?.r ?: 0f)
                if (hypot(rk.x - tx, rk.y - ty) < rad + rk.r) {
                    explodeAt(rk.x, rk.y, HOMING_RADIUS, HOMING_DAMAGE, 4)
                    hit = true
                }
            }
            if (!hit) {
                for (p in platforms) {
                    if (rk.x > p.x && rk.x < p.x + p.w && rk.y > p.y && rk.y < p.y + p.h) {
                        spawnDeathFX(rk.x, rk.y); onEvent("boom_small"); hit = true; break
                    }
                }
            }
            if (hit || rk.life <= 0) {
                if (rk.life <= 0 && !hit) spawnDeathFX(rk.x, rk.y)
                it.remove()
            }
        }
    }

    private fun updatePickups() {
        val cx = px + pw / 2f
        val cy = py + ph / 2f
        val it = pickups.iterator()
        while (it.hasNext()) {
            val rp = it.next()
            rp.vy += 0.22f
            rp.x += rp.vx
            rp.y += rp.vy
            rp.phase += 0.1f
            if (!dead && ammo < HOMING_MAX_AMMO && hypot(cx - rp.x, cy - rp.y) < 46f) {
                ammo++
                onEvent("pickup")
                it.remove()
                continue
            }
            for (p in platforms) {
                if (rp.x > p.x && rp.x < p.x + p.w && rp.y > p.y - 8f && rp.y < p.y + p.h + 8f && rp.vy > 0f) {
                    rp.y = p.y - 8f
                    rp.vy *= -0.4f
                    rp.vx *= 0.7f
                }
            }
            if (rp.y > GROUND_Y + 400f) it.remove()
        }
    }

    private fun updateBoss() {
        if (boss.dead) {
            boss.deadT++
            if (boss.deadT < 10) {
                val cx = boss.x + boss.w / 2f
                val cy = boss.y + boss.h / 2f
                repeat(5) {
                    rainCoins.add(ZRainCoin(cx, cy, (Random.nextFloat() - 0.5f) * 14f, -6f - Random.nextFloat() * 9f))
                }
            }
            if (boss.deadT == 4) onEvent("bossCoinRain")
            return
        }
        if (!boss.active) return
        if (boss.hitFlash > 0) boss.hitFlash--
        val pcx = px + pw / 2f
        val bcx = boss.x + boss.w / 2f
        val dx = pcx - bcx
        val dist = abs(dx)
        val dir = if (dx >= 0f) 1f else -1f
        val groundTop = GROUND_Y - boss.h
        val rage = 1f + boss.phase * 0.3f
        boss.stateT++
        when (boss.state) {
            0 -> {
                val speed = 1.4f * rage
                if (dist > 260f) boss.x += dir * speed * 1.6f
                else if (dist > 120f) boss.x += dir * speed
                else if (dist < 100f) boss.x -= dir * speed * 1.2f
                boss.y = groundTop
                boss.skillCd--
                if (boss.skillCd <= 0) chooseBossSkill(dir)
            }
            1 -> { // предупреждение перед рывком
                boss.x -= dir * 0.4f
                if (boss.stateT == 1) onEvent("bossCharge")
                if (boss.stateT >= 45) {
                    boss.state = 2; boss.stateT = 0
                    boss.dashDir = if (dx >= 0f) 1f else -1f
                    boss.vx = boss.dashDir * (8f + boss.phase * 1.5f)
                    onEvent("bossDash")
                }
            }
            2 -> { // рывок
                boss.x += boss.vx
                if (boss.stateT % 3 == 0) spawnSmoke(boss.x + boss.w / 2f - boss.dashDir * 30f, boss.y + boss.h - 10f)
                if (boss.stateT > 55 || boss.x <= arenaLeft + 12f || boss.x + boss.w >= arenaRight - 12f) {
                    boss.state = 0; boss.stateT = 0; boss.vx = 0f
                    boss.skillCd = (boss.shootInterval * 0.6f).toInt()
                }
            }
            3 -> { // прыжок вверх
                if (boss.stateT == 1) onEvent("bossRoar")
                boss.y -= 11f
                boss.x += dir * 2.6f
                if (boss.y < GROUND_Y - boss.h - 300f || boss.stateT > 32) { boss.state = 4; boss.stateT = 0 }
            }
            4 -> { // падение
                boss.y += 20f
                if (boss.y >= groundTop) {
                    boss.y = groundTop
                    boss.state = 0; boss.stateT = 0
                    boss.skillCd = (boss.shootInterval * 0.7f).toInt()
                    val cx = boss.x + boss.w / 2f
                    shocks.add(ZShock(cx - 20f, -1f)); shocks.add(ZShock(cx + 20f, 1f))
                    if (boss.phase >= 2) { shocks.add(ZShock(cx - 120f, -1f)); shocks.add(ZShock(cx + 120f, 1f)) }
                    spawnBigExplosion(cx, GROUND_Y - 10f, 90f)
                    onEvent("bossSlam")
                    if (!dead && invuln <= 0 && py + ph > GROUND_Y - 24f && abs(pcx - cx) < 70f) hurtPlayer(5, cx, 7f, -8f)
                }
            }
        }
        if (boss.x < arenaLeft + 10f) boss.x = arenaLeft + 10f
        if (boss.x + boss.w > arenaRight - 10f) boss.x = arenaRight - 10f - boss.w

        if (!dead && invuln <= 0) {
            if (px < boss.x + boss.w && px + pw > boss.x && py < boss.y + boss.h && py + ph > boss.y) {
                hurtPlayer(if (boss.state == 2) 6 else 5, boss.x + boss.w / 2f, 7f, -7f)
            }
        }
    }

    private fun chooseBossSkill(dirToPlayer: Float) {
        val options = ArrayList<Int>()
        options.add(0)                           // залп ракет
        options.add(2)                           // рывок
        options.add(3)                           // прыжок с ударной волной
        if (boss.phase >= 1) { options.add(1); options.add(4) }   // веер огня, призыв
        if (boss.phase >= 2) { options.add(1); options.add(2); options.add(3) }
        var pick = options.random()
        if (pick == boss.lastSkill && options.size > 1) pick = options.random()
        boss.lastSkill = pick
        val interval = boss.shootInterval
        when (pick) {
            0 -> { fireBossVolley(2 + boss.phase); boss.skillCd = (interval * 0.75f).toInt() }
            1 -> { fireBossFan(5 + boss.phase * 2); boss.skillCd = (interval * 0.8f).toInt() }
            2 -> { boss.state = 1; boss.stateT = 0 }
            3 -> { boss.state = 3; boss.stateT = 0 }
            4 -> {
                if (enemies.size < 3) {
                    for (sgn in floatArrayOf(-1f, 1f)) {
                        val ex = (boss.x + boss.w / 2f + sgn * 110f).coerceIn(arenaLeft + 40f, arenaRight - 40f)
                        val r = 20f
                        enemies.add(ZEnemy(ex, GROUND_Y - r - 8f, r, 0, MOB_HP[0], MOB_HP[0], 1.2f, sgn, ex, 120f))
                    }
                    onEvent("bossSummon")
                }
                boss.skillCd = (interval * 0.9f).toInt()
            }
        }
    }

    private fun fireBossVolley(count: Int = 2) {
        val cx = boss.x + boss.w / 2f
        val half = (count - 1) / 2f
        for (i in 0 until count) {
            val off = (i - half) * 22f
            bossRockets.add(ZBossRocket(cx + off, boss.y, off * 0.05f, -6f))
            repeat(5) { spawnSmoke(cx + off + (Random.nextFloat() - 0.5f) * 8f, boss.y + (Random.nextFloat() - 0.5f) * 8f) }
        }
        onEvent("bossShoot")
    }

    private fun fireBossFan(count: Int) {
        val cx = boss.x + boss.w / 2f
        val cy = boss.y + boss.h * 0.4f
        val toPlayer = atan2((py + ph / 2f) - cy, (px + pw / 2f) - cx)
        val spread = 1.5f
        for (i in 0 until count) {
            val a = toPlayer - spread / 2f + spread * i / (count - 1).coerceAtLeast(1)
            bossRockets.add(ZBossRocket(cx, cy, cos(a) * 4.6f, sin(a) * 4.6f, straight = true).also { it.up = false; it.angle = a })
        }
        onEvent("bossFire")
    }

    private fun updateShocks() {
        val it = shocks.iterator()
        while (it.hasNext()) {
            val sh = it.next()
            sh.x += sh.dir * 5.5f
            sh.life--
            if (sh.life % 4 == 0) spawnSmoke(sh.x, GROUND_Y - 8f)
            if (!dead && invuln <= 0 && abs((px + pw / 2f) - sh.x) < 26f && py + ph > GROUND_Y - 34f) {
                hurtPlayer(4, sh.x, 6f, -7f)
            }
            if (sh.life <= 0 || sh.x < arenaLeft - 40f || sh.x > arenaRight + 40f) it.remove()
        }
    }

    private fun updateRainCoins() {
        val cx = px + pw / 2f
        val cy = py + ph / 2f
        val it = rainCoins.iterator()
        while (it.hasNext()) {
            val c = it.next()
            c.vy += 0.38f
            c.x += c.vx
            c.y += c.vy
            c.vx *= 0.995f
            c.life--
            for (p in platforms) {
                if (c.x > p.x && c.x < p.x + p.w && c.y > p.y - 8f && c.y < p.y + 16f && c.vy > 0f) {
                    c.y = p.y - 8f; c.vy *= -0.45f; c.vx *= 0.8f
                }
            }
            if (!dead && hypot(cx - c.x, cy - c.y) < 44f) {
                coinCount++
                onEvent("coin")
                it.remove()
                continue
            }
            if (c.life <= 0 || c.y > GROUND_Y + 300f) it.remove()
        }
    }

    private fun updateBossRockets() {
        val speed = MOVE_SPEED * 0.9f
        val it = bossRockets.iterator()
        while (it.hasNext()) {
            val rk = it.next()
            rk.life--
            rk.trail++
            if (rk.trail % 2 == 0) spawnSmoke(rk.x, rk.y)
            if (rk.straight) {
                // летит по прямой
            } else if (rk.up) {
                rk.vy -= 0.15f
                if (rk.vy < -9f) rk.vy = -9f
                rk.homingTimer--
                if (rk.homingTimer <= 0) rk.up = false
            } else {
                val dx = px + pw / 2f - rk.x
                val dy = py + ph / 2f - rk.y
                val len = hypot(dx, dy).coerceAtLeast(0.001f)
                rk.vx += (dx / len * speed - rk.vx) * 0.08f
                rk.vy += (dy / len * speed - rk.vy) * 0.08f
            }
            rk.x += rk.vx
            rk.y += rk.vy
            if (abs(rk.vx) > 0.01f || abs(rk.vy) > 0.01f) rk.angle = atan2(rk.vy, rk.vx)

            var hit = false
            if (!dead && invuln <= 0 && hypot(rk.x - (px + pw / 2f), rk.y - (py + ph / 2f)) < 22f + rk.r) {
                spawnDeathFX(rk.x, rk.y)
                onEvent("boom_small")
                hurtPlayer(3, rk.x, 5f, -6.5f)
                hit = true
            }
            if (!hit) {
                for (p in platforms) {
                    if (rk.x > p.x && rk.x < p.x + p.w && rk.y > p.y && rk.y < p.y + p.h) {
                        spawnDeathFX(rk.x, rk.y); hit = true; break
                    }
                }
            }
            // выстрел игрока может сбить ракету босса
            if (!hit) {
                val bi = bolts.iterator()
                while (bi.hasNext()) {
                    val b = bi.next()
                    if (hypot(b.x - rk.x, b.y - rk.y) < 16f) {
                        spawnDeathFX(rk.x, rk.y); onEvent("boom_small"); bi.remove(); hit = true; break
                    }
                }
            }
            if (hit || rk.life <= 0) {
                if (rk.life <= 0 && !hit) spawnDeathFX(rk.x, rk.y)
                it.remove()
            }
        }
    }

    private fun updateEnemies() {
        for (e in enemies) {
            e.x += e.vx * e.dir
            if (e.x > e.baseX + e.range) e.dir = -1f
            if (e.x < e.baseX - e.range) e.dir = 1f
            if (e.hitFlash > 0) e.hitFlash--
            if (!dead && invuln <= 0) {
                val dx = px + pw / 2f - e.x
                val dy = py + ph / 2f - e.y
                if (hypot(dx, dy) < e.r + 22f) hurtPlayer(MOB_DMG[e.tier], e.x, 5f, -6.5f)
            }
        }
    }

    private fun updateFX() {
        for (c in cured) c.age++
        if (wingT >= 0) { wingT++; if (wingT > 26) wingT = -1 }
        if (healT >= 0) { healT++; if (healT > 48) healT = -1 }
        val pi = parts.iterator()
        while (pi.hasNext()) {
            val p = pi.next()
            p.x += p.vx; p.y += p.vy
            p.vy += 0.2f; p.vx *= 0.98f
            p.life -= p.decay
            if (p.life <= 0f) pi.remove()
        }
        val wi = waves.iterator()
        while (wi.hasNext()) {
            val w = wi.next()
            w.r += (w.maxR - w.r) * 0.15f
            w.life -= 0.04f
            if (w.life <= 0f) wi.remove()
        }
        val si = smokes.iterator()
        while (si.hasNext()) {
            val s = si.next()
            s.x += s.vx; s.y += s.vy
            s.r += s.vr
            s.life -= s.decay
            if (s.life <= 0f) si.remove()
        }
    }

    private fun updateCamera() {
        val look = facing * vw * 0.18f
        val tx = px + pw / 2f - vw * 0.5f + look * 1.2f
        val ty = py + ph / 2f - vh * 0.6f
        camX += (tx - camX) * 0.12f
        camY += (ty - camY) * 0.12f
    }

    private fun updateHelper() {
        if (!helperOn || dead) return
        val tx = px + pw / 2f - facing * 52f
        val ty = py + ph - 52f + sin(animTime * 0.09f) * 6f
        if (hypot(tx - hx, ty - hy) > 700f) { hx = tx; hy = ty }
        hx += (tx - hx) * 0.07f
        hy += (ty - hy) * 0.10f
        helperCd--
        if (helperCd <= 0) {
            var best: Float? = null
            var bx = 0f
            var by = 0f
            for (e in enemies) {
                val dx = e.x - hx
                if (abs(dx) < 560f && abs(e.y - hy) < 320f && (best == null || abs(dx) < best!!)) { best = abs(dx); bx = e.x; by = e.y }
            }
            if (boss.active && !boss.dead) {
                val dx = boss.x + boss.w / 2f - hx
                if (abs(dx) < 700f && (best == null || abs(dx) < best!!)) { best = abs(dx); bx = boss.x + boss.w / 2f; by = boss.y + boss.h / 2f }
            }
            if (best != null) {
                helperDir = if (bx >= hx) 1f else -1f
                bolts.add(ZBolt(hx + helperDir * 30f, hy, helperDir * BOLT_SPEED))
                helperCd = 70
                onEvent("helperShoot")
            } else helperCd = 15
        }
        helperHeal++
        if (helperHeal >= 1000) {
            helperHeal = 0
            if (hp < PLAYER_MAX_HP) { hp++; onEvent("heal") }
        }
    }

    fun stepFrame() {
        animTime++
        updatePlayer()
        updateHelper()
        updateBolts()
        updateHoming()
        updateGrenades()
        updatePickups()
        updateBoss()
        updateBossRockets()
        updateShocks()
        updateRainCoins()
        updateEnemies()
        updateFX()
        updateCamera()
        if (!levelDone && boss.dead && !dead && boss.deadT > 300) {
            levelDone = true
            onEvent("levelDone")
        }
    }

    fun hud(): ZHud = ZHud(
        hp = max(0, hp), lives = lives, coins = coinCount, ammo = ammo, weapon = weapon,
        bossHp = max(0, boss.hp), bossPhase = boss.phase, bossMax = boss.maxHp, bossActive = boss.active && !boss.dead,
        mobsLeft = enemies.size, level = level
    )
}

// ---------- Рисование ----------
private val TIER_COLORS = listOf(
    Color(0xFFFF8A65) to Color(0xFFD84315),
    Color(0xFFBA68C8) to Color(0xFF7B1FA2),
    Color(0xFF7986CB) to Color(0xFF303F9F)
)

private fun DrawScope.drawMob(e: ZEnemy, t: Int, tx: Float, ty: Float) {
    val bob = sin(t * 0.09f + e.anim) * 4f
    val cx = e.x
    val cy = e.y + bob
    val r = e.r
    val (light, dark) = TIER_COLORS[e.tier]
    // рожки / шипы
    if (e.tier >= 1) {
        for (side in floatArrayOf(-1f, 1f)) {
            val horn = Path().apply {
                moveTo(cx + side * r * 0.35f, cy - r * 0.8f)
                lineTo(cx + side * r * 0.75f, cy - r * 1.45f)
                lineTo(cx + side * r * 0.85f, cy - r * 0.55f)
                close()
            }
            drawPath(horn, dark)
        }
    }
    if (e.tier == 2) {
        for (i in -1..1) {
            val sp = Path().apply {
                moveTo(cx + i * r * 0.3f - r * 0.12f, cy - r * 0.95f)
                lineTo(cx + i * r * 0.3f, cy - r * 1.35f)
                lineTo(cx + i * r * 0.3f + r * 0.12f, cy - r * 0.95f)
                close()
            }
            drawPath(sp, Color(0xFFFFEB3B))
        }
    }
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), light, dark), center = Offset(cx - r * 0.3f, cy - r * 0.35f), radius = r * 1.5f),
        r, Offset(cx, cy)
    )
    // ножки
    val step = sin(t * 0.2f + e.anim) * 3f
    drawCircle(dark, r * 0.22f, Offset(cx - r * 0.4f + step, cy + r * 0.95f))
    drawCircle(dark, r * 0.22f, Offset(cx + r * 0.4f - step, cy + r * 0.95f))
    // глаза следят за Зеликом
    val dx = tx - cx
    val dy = ty - cy
    val len = hypot(dx, dy).coerceAtLeast(1f)
    val ox = dx / len * r * 0.1f
    val oy = dy / len * r * 0.1f
    for (side in floatArrayOf(-1f, 1f)) {
        val ex = cx + side * r * 0.38f
        val ey = cy - r * 0.12f
        drawCircle(Color.White, r * 0.3f, Offset(ex, ey))
        drawCircle(Color(0xFF212121), r * 0.15f, Offset(ex + ox, ey + oy))
        drawCircle(Color.White, r * 0.05f, Offset(ex + ox - r * 0.04f, ey + oy - r * 0.05f))
    }
    // брови и рот
    if (e.tier >= 1) {
        drawLine(Color(0xFF212121), Offset(cx - r * 0.6f, cy - r * 0.5f), Offset(cx - r * 0.15f, cy - r * 0.32f), r * 0.1f, StrokeCap.Round)
        drawLine(Color(0xFF212121), Offset(cx + r * 0.6f, cy - r * 0.5f), Offset(cx + r * 0.15f, cy - r * 0.32f), r * 0.1f, StrokeCap.Round)
    }
    drawArc(
        Color(0xFF212121), 200f, 140f, false,
        Offset(cx - r * 0.3f, cy + r * 0.2f), Size(r * 0.6f, r * 0.35f),
        style = Stroke(width = r * 0.09f, cap = StrokeCap.Round)
    )
    if (e.hitFlash > 0) drawCircle(Color.White.copy(alpha = 0.6f), r, Offset(cx, cy))
    if (e.hp < e.maxHp) {
        val bw = r * 1.6f
        drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(cx - bw / 2f, cy - r * 1.6f), Size(bw, 6f), CornerRadius(3f))
        drawRoundRect(Color(0xFF66BB6A), Offset(cx - bw / 2f, cy - r * 1.6f), Size(bw * e.hp / e.maxHp, 6f), CornerRadius(3f))
    }
}

private fun DrawScope.drawSunflower(e: ZEnemy, t: Int, f: ZFrames) {
    val seq = intArrayOf(0, 1, 2, 1)
    val img = f.sunWalk[seq[(t / 8) % 4]]
    val hh = e.r * 3.6f * (if (e.tier == 2) 1.15f else 1f)
    val ww = hh * img.width / img.height
    val bottom = e.y + e.r + 2f
    withTransform({ if (e.dir < 0f) scale(-1f, 1f, Offset(e.x, 0f)) }) {
        drawImage(img, dstOffset = IntOffset((e.x - ww / 2f).toInt(), (bottom - hh).toInt()),
            dstSize = IntSize(ww.toInt().coerceAtLeast(1), hh.toInt().coerceAtLeast(1)))
        if (e.hitFlash > 0) drawImage(img, dstOffset = IntOffset((e.x - ww / 2f).toInt(), (bottom - hh).toInt()),
            dstSize = IntSize(ww.toInt().coerceAtLeast(1), hh.toInt().coerceAtLeast(1)), alpha = 0.5f,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White, androidx.compose.ui.graphics.BlendMode.SrcAtop))
    }
    if (e.hp < e.maxHp) {
        val bw = e.r * 1.6f
        val by = bottom - hh - 8f
        drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(e.x - bw / 2f, by), Size(bw, 6f), CornerRadius(3f))
        drawRoundRect(Color(0xFF66BB6A), Offset(e.x - bw / 2f, by), Size(bw * e.hp / e.maxHp, 6f), CornerRadius(3f))
    }
}

private fun DrawScope.drawBoss(b: ZBoss, t: Int, tx: Float) {
    if (b.dead) return
    val cDark: Color; val cMid: Color; val cLight: Color
    when (b.kind) {
        1 -> { cDark = Color(0xFF1B5E20); cMid = Color(0xFF43A047); cLight = Color(0xFFC8E6C9) }
        2 -> { cDark = Color(0xFF7F0000); cMid = Color(0xFFE64A19); cLight = Color(0xFFFFCCBC) }
        else -> { cDark = Color(0xFF4A148C); cMid = Color(0xFF8E24AA); cLight = Color(0xFFE1BEE7) }
    }
    val rage = b.phase >= 2
    // предупреждение рывка/падения
    if (b.state == 1) {
        val a = 0.25f + 0.25f * sin(t * 0.5f)
        drawRect(Color.Red.copy(alpha = a.coerceIn(0f, 1f)), Offset(b.x - 900f, GROUND_Y - 60f), Size(1800f, 60f))
    }
    if (b.state == 3 || b.state == 4) {
        drawCircle(Color.Red.copy(alpha = 0.35f), 70f, Offset(b.x + b.w / 2f, GROUND_Y - 4f), style = Stroke(6f))
    }
    if (rage) drawCircle(Color(0xFFFF1744).copy(alpha = 0.18f + 0.1f * sin(t * 0.3f)), b.w * 1.1f, Offset(b.x + b.w / 2f, b.y + b.h / 2f))

    val cx = b.x + b.w / 2f
    val bob = sin(t * 0.07f) * 4f
    val cy = b.y + b.h / 2f + bob
    val rw = b.w * 0.95f
    val rh = b.h * 0.62f
    // антенны-пушки
    for (side in floatArrayOf(-1f, 1f)) {
        drawLine(cDark, Offset(cx + side * rw * 0.35f, cy - rh * 0.8f), Offset(cx + side * rw * 0.55f, cy - rh * 1.45f), 7f, StrokeCap.Round)
        drawCircle(Color(0xFFFF5252), 9f, Offset(cx + side * rw * 0.55f, cy - rh * 1.45f))
    }
    // рога
    for (side in floatArrayOf(-1f, 1f)) {
        val hp = Path().apply {
            moveTo(cx + side * rw * 0.22f, cy - rh * 0.95f)
            lineTo(cx + side * rw * 0.42f, cy - rh * (if (rage) 1.9f else 1.5f))
            lineTo(cx + side * rw * 0.1f, cy - rh * 1.0f)
            close()
        }
        drawPath(hp, if (rage) Color(0xFFFFEB3B) else Color(0xFFFFF8E1))
    }
    // ножки
    val st = sin(t * 0.15f) * 5f
    drawRoundRect(cDark, Offset(cx - rw * 0.45f + st, cy + rh * 0.7f), Size(18f, 26f), CornerRadius(8f))
    drawRoundRect(cDark, Offset(cx + rw * 0.45f - 18f - st, cy + rh * 0.7f), Size(18f, 26f), CornerRadius(8f))
    // тело
    drawOval(
        Brush.radialGradient(listOf(cLight, cMid, cDark), center = Offset(cx - rw * 0.2f, cy - rh * 0.3f), radius = rw * 1.1f),
        Offset(cx - rw / 2f, cy - rh), Size(rw, rh * 2f)
    )
    // глаза
    val dir = if (tx >= cx) 1f else -1f
    for (side in floatArrayOf(-1f, 1f)) {
        val ex = cx + side * rw * 0.2f
        val ey = cy - rh * 0.25f
        drawCircle(Color.White, 11f, Offset(ex, ey))
        drawCircle(Color(0xFFD50000), 6f, Offset(ex + dir * 3f, ey))
        drawLine(Color(0xFF212121), Offset(ex - side * 14f, ey - 16f - side * 0f), Offset(ex + side * 8f, ey - 8f), 4f, StrokeCap.Round)
    }
    // зубастый рот
    val mouth = Path().apply {
        moveTo(cx - rw * 0.28f, cy + rh * 0.25f)
        lineTo(cx - rw * 0.14f, cy + rh * 0.45f)
        lineTo(cx, cy + rh * 0.25f)
        lineTo(cx + rw * 0.14f, cy + rh * 0.45f)
        lineTo(cx + rw * 0.28f, cy + rh * 0.25f)
        lineTo(cx + rw * 0.28f, cy + rh * 0.55f)
        lineTo(cx - rw * 0.28f, cy + rh * 0.55f)
        close()
    }
    drawPath(mouth, Color(0xFF311B92))
    if (b.hitFlash > 0) drawOval(Color.White.copy(alpha = 0.55f), Offset(cx - rw / 2f, cy - rh), Size(rw, rh * 2f))
}

private fun DrawScope.drawWorld(w: ZelikWorld, f: ZFrames, z: Float, fa: FireSheet, fb: FireSheet, wings: FireSheet, water: FireSheet, helper: ZHelperGfx? = null, bio: ZBiomeGfx? = null) {
    val camX = w.camX
    val camY = w.camY
    val sw = size.width
    val sh = size.height
    val t = w.animTime

    if (bio != null) {
        drawBiomeBackdrop(bio, biomeOfLevel(w.level), camX, camY, z, GROUND_Y, t)
    } else {
    // небо
    drawRect(Brush.verticalGradient(listOf(Color(0xFF4FC3F7), Color(0xFFB3E5FC), Color(0xFFFFF9C4))), size = Size(sw, sh))
    // солнце
    drawCircle(Color(0xFFFFEB3B).copy(alpha = 0.9f), 46f * z, Offset(sw * 0.82f, sh * 0.14f))
    // облака (параллакс)
    for (i in 0 until 7) {
        val span = sw * 1.5f
        val cx = (((i * 0.31f * sw - camX * z * 0.12f) % span) + span) % span - sw * 0.25f
        val cy = sh * (0.08f + (i % 3) * 0.09f)
        drawOval(Color.White.copy(alpha = 0.85f), Offset(cx, cy), Size(110f * z, 34f * z))
        drawOval(Color.White.copy(alpha = 0.85f), Offset(cx + 24f * z, cy - 16f * z), Size(70f * z, 36f * z))
    }
    // дальние холмы
    for (layer in 0 until 2) {
        val factor = 0.25f + layer * 0.2f
        val baseY = (GROUND_Y - camY) * z - (110f - layer * 50f) * z
        val path = Path()
        path.moveTo(0f, sh)
        var x = 0f
        while (x <= sw + 40f) {
            val wx = (x + camX * z * factor) / z
            val y = baseY + sin(wx / (130f + layer * 60f)) * (28f + layer * 12f) * z
            path.lineTo(x, y)
            x += 40f
        }
        path.lineTo(sw, sh)
        path.close()
        drawPath(path, if (layer == 0) Color(0xFF81C784) else Color(0xFF4CAF50))
    }

    }

    withTransform({
        translate(-camX * z, -camY * z)
        scale(z, z, Offset.Zero)
    }) {
        val left = camX - 100f
        val right = camX + sw / z + 100f

        // платформы
        for ((idx, p) in w.platforms.withIndex()) {
            if (p.x > right || p.x + p.w < left) continue
            if (bio != null) {
                val bi = biomeOfLevel(w.level)
                if (idx == 0) drawBiomeGround(bio, bi, max(p.x, left), min(p.x + p.w, right), p.y, p.h)
                else drawBiomePlatform(bi, p.x, p.y, p.w, p.h)
                continue
            }
            if (idx == 0) {
                drawRect(Color(0xFF8D6E63), Offset(max(p.x, left), p.y), Size(min(p.x + p.w, right) - max(p.x, left), p.h))
                drawRect(Color(0xFF66BB6A), Offset(max(p.x, left), p.y), Size(min(p.x + p.w, right) - max(p.x, left), 22f))
                drawRect(Color(0xFF43A047), Offset(max(p.x, left), p.y + 22f), Size(min(p.x + p.w, right) - max(p.x, left), 6f))
            } else {
                drawRoundRect(Color(0xFF8D6E63), Offset(p.x, p.y), Size(p.w, p.h), CornerRadius(8f))
                drawRoundRect(Color(0xFF66BB6A), Offset(p.x, p.y), Size(p.w, 9f), CornerRadius(5f))
            }
        }
        // арена босса: флажки
        run {
            val ax = w.arenaLeft
            if (ax in left..right) {
                drawLine(Color(0xFF5D4037), Offset(ax, GROUND_Y), Offset(ax, GROUND_Y - 120f), 6f)
                val flag = Path().apply { moveTo(ax, GROUND_Y - 120f); lineTo(ax + 50f, GROUND_Y - 100f); lineTo(ax, GROUND_Y - 80f); close() }
                drawPath(flag, Color(0xFFE53935))
            }
        }
        // монеты
        for (c in w.coins) {
            if (c.taken || c.x < left || c.x > right) continue
            val squash = abs(cos(t * 0.06f + c.phase))
            val cy = c.y + sin(t * 0.05f + c.phase) * 3f
            drawOval(Color(0xFFFFC107), Offset(c.x - 10f * squash - 2f, cy - 11f), Size(20f * squash + 4f, 22f))
            drawOval(Color(0xFFFFF176), Offset(c.x - 5f * squash, cy - 7f), Size(8f * squash + 1f, 12f))
        }
        // аптечки-сердечки
        for (m in w.meds) {
            if (m.taken || m.x < left || m.x > right) continue
            val by = m.y + sin(t * 0.06f) * 3f
            drawRoundRect(Color.White, Offset(m.x - 18f, by - 18f), Size(36f, 36f), CornerRadius(8f))
            drawRoundRect(Color(0xFFE53935), Offset(m.x - 18f, by - 18f), Size(36f, 36f), CornerRadius(8f), style = Stroke(3f))
            drawCircle(Color(0xFFE53935), 7f, Offset(m.x - 6f, by - 4f))
            drawCircle(Color(0xFFE53935), 7f, Offset(m.x + 6f, by - 4f))
            val tri = Path().apply { moveTo(m.x - 13f, by); lineTo(m.x + 13f, by); lineTo(m.x, by + 14f); close() }
            drawPath(tri, Color(0xFFE53935))
        }
        // патроны-звёзды
        for (p in w.pickups) {
            val bobY = sin(p.phase) * 3f
            drawCircle(Color(0xFF29B6F6).copy(alpha = 0.35f), 22f, Offset(p.x, p.y + bobY))
            drawStar(Offset(p.x, p.y + bobY), 13f, Color(0xFFFFEB3B))
        }
        // расколдованные подсолнухи
        for (c in w.cured) {
            if (c.x < left - 80f || c.x > right + 80f) continue
            val img: ImageBitmap
            var alpha = 1f
            var hh = c.h
            if (c.age < 45) { img = f.sunFade[min(2, c.age / 15)]; alpha = 1f - (c.age % 15) / 40f }
            else { img = f.sunCured; val k = min(1f, (c.age - 45) / 25f); alpha = k; hh *= 0.9f + 0.1f * k }
            val ww = hh * img.width / img.height
            drawImage(img, dstOffset = IntOffset((c.x - ww / 2f).toInt(), (c.bottom - hh).toInt()),
                dstSize = IntSize(ww.toInt().coerceAtLeast(1), hh.toInt().coerceAtLeast(1)), alpha = alpha.coerceIn(0f, 1f))
        }
        // вода расколдовывает подсолнухи
        for (c in w.cured) {
            if (c.age < 50) drawFire(water, c.age / 2, c.x, c.bottom - c.h * 0.5f, c.h * 1.2f, 0f, 0.9f)
        }
        // мобы
        val tx = w.px + w.pw / 2f
        val ty = w.py + w.ph / 2f
        for (e in w.enemies) {
            if (e.x < left - 60f || e.x > right + 60f) continue
            if (e.tier >= 1) drawSunflower(e, t, f) else drawMob(e, t, tx, ty)
        }
        drawBoss(w.boss, t, tx)
        if (helper != null && w.helperOn && !w.dead) {
            val lay = monLayout(helper.imgs, w.hx, w.hy + 44f, 70f, 84f)
            drawMonster(helper.imgs, helper.spec, lay, helper.anim, t / 60f, 1f, w.helperDir * 0.6f)
        }

        // дым
        for (s in w.smokes) {
            drawCircle(Color.White.copy(alpha = (s.life * 0.5f).coerceIn(0f, 1f)), s.r, Offset(s.x, s.y))
        }
        // выстрелы — огненные шары
        for (b in w.bolts) {
            val left = b.vx < 0f
            drawCircle(Color(0xFFFFB300).copy(alpha = 0.25f), 20f, Offset(b.x, b.y))
            drawFire(fa, b.trail / 2, b.x - (if (left) -22f else 22f) + (if (left) 0f else 0f), b.y, 72f, if (left) 180f else 0f)
        }
        // гранаты
        for (g in w.grenades) {
            val blink = (g.fuse % 12) < 6
            drawCircle(Color(0xFFE040FB).copy(alpha = 0.3f), g.r * 2.2f, Offset(g.x, g.y))
            drawCircle(if (blink) Color(0xFFCE93D8) else Color(0xFF8E24AA), g.r, Offset(g.x, g.y))
            rotate(g.rot * 57.3f, Offset(g.x, g.y)) {
                drawLine(Color.White, Offset(g.x - g.r, g.y), Offset(g.x + g.r, g.y), 2.5f)
            }
        }
        // ракеты-звёзды
        for (h in w.homings) {
            h.target?.let { drawTargetMark(Offset(it.x, it.y), it.r + 10f, t) }
            if (h.targetBoss) drawTargetMark(Offset(w.boss.x + w.boss.w / 2f, w.boss.y + w.boss.h / 2f), 64f, t)
            drawCircle(Color(0xFFFFEB3B).copy(alpha = 0.3f), 18f, Offset(h.x, h.y))
            rotate(t * 6f, Offset(h.x, h.y)) { drawStar(Offset(h.x, h.y), 12f, Color(0xFFFFEB3B)) }
        }
        // ударные волны босса
        for (sh in w.shocks) {
            val h = 46f + 10f * sin(sh.life * 0.4f)
            val path = Path().apply {
                moveTo(sh.x - 22f, GROUND_Y)
                lineTo(sh.x, GROUND_Y - h)
                lineTo(sh.x + 22f, GROUND_Y)
                close()
            }
            drawPath(path, Color(0xFFFF7043))
            drawPath(path, Color(0xFFFFEB3B), style = Stroke(4f))
        }
        // монеты, сыплющиеся из побеждённого босса
        for (c in w.rainCoins) {
            val sq = abs(cos((c.life + c.x) * 0.15f))
            drawOval(Color(0xFFFFC107), Offset(c.x - 11f * sq, c.y - 11f), Size(22f * sq, 22f))
            drawOval(Color(0xFFFFF59D), Offset(c.x - 6f * sq, c.y - 7f), Size(8f * sq, 12f))
        }
        // ракеты босса — огненные шары
        for (r in w.bossRockets) {
            drawFire(fb, r.trail / 2, r.x - cos(r.angle) * 22f, r.y - sin(r.angle) * 22f, 64f, r.angle * 57.2958f)
        }

        // крылья за спиной при втором прыжке
        if (w.wingT >= 0) {
            val fr = min(4, w.wingT / 3)
            val a = if (w.wingT > 18) (26 - w.wingT) / 8f else 1f
            drawFire(wings, fr, w.px + w.pw / 2f, w.py + w.ph * 0.38f, 150f, 0f, a.coerceIn(0f, 1f))
        }
        drawZelik(w, f)
        // водное исцеление
        if (w.healT >= 0) drawFire(water, w.healT / 2, w.px + w.pw / 2f, w.py + w.ph * 0.5f, 120f, 0f, 0.9f)

        // волны и частицы
        for (wv in w.waves) {
            drawCircle(wv.color.copy(alpha = (wv.life * 0.7f).coerceIn(0f, 1f)), wv.r, Offset(wv.x, wv.y), style = Stroke(width = 5f))
        }
        for (p in w.parts) {
            drawCircle(p.color.copy(alpha = p.life.coerceIn(0f, 1f)), p.size, Offset(p.x, p.y))
        }
    }
}

private fun DrawScope.drawStar(c: Offset, r: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val a = i * PI.toFloat() / 5f - PI.toFloat() / 2f
        val rr = if (i % 2 == 0) r else r * 0.45f
        val x = c.x + cos(a) * rr
        val y = c.y + sin(a) * rr
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawTargetMark(c: Offset, r: Float, t: Int) {
    val pulse = 1f + sin(t * 0.2f) * 0.08f
    drawCircle(Color(0xFFFF5252).copy(alpha = 0.8f), r * pulse, c, style = Stroke(width = 3f))
    drawLine(Color(0xFFFF5252).copy(alpha = 0.8f), Offset(c.x - r * 1.3f, c.y), Offset(c.x - r * 0.7f, c.y), 3f)
    drawLine(Color(0xFFFF5252).copy(alpha = 0.8f), Offset(c.x + r * 0.7f, c.y), Offset(c.x + r * 1.3f, c.y), 3f)
}

private fun DrawScope.drawZelik(w: ZelikWorld, f: ZFrames) {
    val frame: ImageBitmap = when {
        w.dead -> f.dead[min(4, w.deadTimer / 10)]
        w.hurtAnim > 0 -> f.hurt[(w.animTime / 5) % 4]
        w.fireAnim > 0 -> when (w.fireKind) {
            1 -> if (w.fireAnim > 9) f.fire[1] else f.fire[0]
            2 -> f.attack[1]
            else -> f.attack[0]
        }
        !w.onGround -> f.jump[
            when {
                w.pvy < -8f -> 0
                w.pvy < -1f -> 1
                w.pvy < 4f -> 2
                else -> 3
            }
        ]
        abs(w.pvx) > 0.1f -> if (abs(w.pvx) > MOVE_SPEED * 0.75f) f.run[(w.animTime / 5) % 6] else f.walk[(w.animTime / 7) % 6]
        else -> f.idle[if ((w.animTime % 200) > 190) 1 else 0]
    }
    // мигаем после удара
    if (!w.dead && w.invuln > 0 && (w.invuln / 4) % 2 == 0) return

    val k = SPRITE_H / 340f
    val dw = frame.width * k
    var dh = SPRITE_H
    val cx = w.px + w.pw / 2f
    val bottom = w.py + w.ph + 1f
    if (w.crouching && !w.dead) dh *= 0.84f
    val top = bottom - dh
    val dwAdj = if (w.crouching && !w.dead) dw else dw
    withTransform({
        if (w.facing < 0f) scale(-1f, 1f, Offset(cx, 0f))
    }) {
        drawImage(
            frame,
            dstOffset = IntOffset((cx - dwAdj / 2f).toInt(), top.toInt()),
            dstSize = IntSize(dwAdj.toInt().coerceAtLeast(1), dh.toInt().coerceAtLeast(1))
        )
    }
}

// ---------- Управление ----------
private fun Modifier.holdInput(onChange: (Boolean) -> Unit): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        onChange(true)
        do {
            val event = awaitPointerEvent()
        } while (event.changes.any { it.pressed })
        onChange(false)
    }
}

@Composable
private fun ZStick(input: ZInput, modifier: Modifier = Modifier) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density
    Box(
        modifier = modifier
            .size(160.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val r = 56f * density
                    fun apply(pos: Offset) {
                        var v = (pos - center) / r
                        val len = v.getDistance()
                        if (len > 1f) v = v / len
                        input.stickX = v.x
                        input.stickY = v.y
                        knob = v * r
                    }
                    apply(down.position)
                    do {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                        apply(c.position)
                        c.consume()
                    } while (c.pressed)
                    input.stickX = 0f
                    input.stickY = 0f
                    knob = Offset.Zero
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color.Black.copy(alpha = 0.18f), 56f * density, c)
            drawCircle(Color.White.copy(alpha = 0.55f), 56f * density, c, style = Stroke(width = 3f * density))
            drawCircle(Color.White.copy(alpha = 0.85f), 24f * density, c + knob)
        }
    }
}

@Composable
private fun ZRoundButton(res: Int, label: String, size: Int, onChange: (Boolean) -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(size.dp)
            .scale(if (pressed) 0.92f else 1f)
            .holdInput { pressed = it; onChange(it) },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(res), null, Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
        if (label.isNotEmpty()) Text(label, fontSize = (size * 0.42f).sp)
    }
}

// ---------- Экран ----------
@Composable
fun ZelikAdventureScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val density = LocalDensity.current.density
    val frames = rememberZFrames()
    val fireA = rememberFireA()
    val fireB = rememberFireB()
    val wingsFx = rememberWingsFx()
    val waterFx = rememberWaterFx()
    val input = remember { ZInput() }
    val houseList = remember { loadHouse(context) }
    var helperIdx by remember {
        mutableIntStateOf(
            try { context.getSharedPreferences("playground_prefs", android.content.Context.MODE_PRIVATE).getInt("mb_helper", -1) } catch (e: Exception) { -1 }
        )
    }
    if (helperIdx >= houseList.size) helperIdx = -1
    val helperSpec = houseList.getOrNull(helperIdx) ?: MonSpec()
    val helperImgs = rememberMonImgs(helperSpec)
    val helperAnim = remember { MonAnim() }
    val biomeGfx = rememberZBiomeGfx()
    val helperGfx = remember(helperImgs, helperSpec) { ZHelperGfx(helperImgs, helperSpec, helperAnim) }

    var screen by remember { mutableStateOf(ZScreen.MENU) }
    var diff by remember { mutableIntStateOf(0) }
    var startBiome by remember { mutableIntStateOf(0) }
    var world by remember { mutableStateOf<ZelikWorld?>(null) }
    var hud by remember { mutableStateOf(ZHud()) }
    var tick by remember { mutableIntStateOf(0) }
    var confettiTrig by remember { mutableIntStateOf(0) }

    fun playEvent(ev: String, w: ZelikWorld) {
        when (ev) {
            "jump" -> soundManager.play("boing", volume = 0.6f)
            "shoot" -> soundPlayer.playBowShot()
            "throw" -> soundPlayer.playCatapultLaunch()
            "mortar" -> soundPlayer.playMagicSparkle()
            "boom" -> soundPlayer.playCatapultCrashLand()
            "boom_small" -> soundManager.play("zb_boom", volume = 0.7f)
            "hit" -> soundPlayer.playPop(1.3f)
            "coin" -> soundManager.play("zb_coin", volume = 0.7f, rate = 0.9f + Random.nextFloat() * 0.3f)
            "heal" -> soundPlayer.playSparkleChime()
            "hurt" -> { soundManager.play("zb_ouch", volume = 0.9f); soundPlayer.vibrateSquishDeform() }
            "fall" -> soundPlayer.playComicalFallWobble()
            "roar", "bossRoar" -> soundManager.play("zb_roar", volume = 0.9f)
            "bossHit" -> soundManager.play("zb_hurt", volume = 0.8f)
            "bossShoot" -> soundManager.play("zb_rocket", volume = 0.8f)
            "bossFire" -> soundManager.play("zb_fire", volume = 0.8f)
            "bossCharge" -> soundManager.play("zb_charge", volume = 0.9f)
            "bossDash" -> soundManager.play("zb_dash", volume = 0.9f)
            "bossSlam" -> { soundManager.play("zb_slam", volume = 1f); soundManager.play("zb_shock", volume = 0.7f) }
            "bossSummon" -> soundManager.play("zb_summon", volume = 0.9f)
            "bossPhase" -> soundManager.play("zb_phase", volume = 1f)
            "bossCoinRain" -> soundManager.play("zb_coinrain", volume = 1f)
            "bossDead" -> { soundManager.play("zb_down", volume = 1f); soundManager.play("zb_victory", volume = 0.9f); confettiTrig++ }
            "pickup" -> soundPlayer.playChime(1.4f)
            "helperShoot" -> { soundPlayer.playPop(1.7f); helperAnim.jump(220f); helperAnim.squash = 0.4f }
            "wings" -> soundPlayer.playMagicSparkle()
            "switch" -> soundPlayer.playArcadeButton()
            "levelDone" -> {
                soundPlayer.playFanfare()
                hud = w.hud()
                screen = ZScreen.LEVEL_DONE
            }
            "gameover" -> {
                hud = w.hud()
                screen = ZScreen.GAME_OVER
            }
        }
    }

    LaunchedEffect(Unit) {
        for (n in listOf("zb_roar", "zb_hurt", "zb_rocket", "zb_fire", "zb_charge", "zb_dash", "zb_slam", "zb_shock",
            "zb_summon", "zb_phase", "zb_coin", "zb_coinrain", "zb_victory", "zb_down", "zb_ouch", "zb_boom")) {
            try { soundManager.play(n, volume = 0f) } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        var clk = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                clk += dt
                helperAnim.step(dt, clk)
            }
        }
    }

    fun beginGame(d: Int) {
        diff = d
        val w = ZelikWorld(d, input)
        w.onEvent = { ev -> playEvent(ev, w) }
        w.helperOn = helperIdx >= 0
        w.startLevel(1 + startBiome)
        world = w
        screen = ZScreen.PLAY
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF4FC3F7))
            .testTag("zelik_adventure_root")
    ) {
        val w = world
        if (w != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val redraw = tick
                w.vw = size.width / density
                w.vh = size.height / density
                drawWorld(w, frames, density, fireA, fireB, wingsFx, waterFx, helperGfx, biomeGfx)
            }
        }

        if (w != null && screen == ZScreen.PLAY) {
            // Шапка: здоровье, жизни, монеты, патроны.
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 6.dp, end = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(88.dp))
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.85f), shadowElevation = 3.dp) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("❤️", fontSize = 15.sp)
                            Spacer(Modifier.width(4.dp))
                            Box(
                                Modifier
                                    .width(92.dp)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFCFD8DC))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .fillMaxWidth(hud.hp / PLAYER_MAX_HP.toFloat())
                                        .background(if (hud.hp <= 3) Color(0xFFE53935) else Color(0xFF66BB6A))
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("👽×${hud.lives}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF37474F))
                            Spacer(Modifier.width(8.dp))
                            Text("🪙${hud.coins}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF37474F))
                            Spacer(Modifier.width(8.dp))
                            Text("⭐×${hud.ammo}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF37474F))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row {
                    Spacer(Modifier.width(88.dp))
                    Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.75f)) {
                        Text(
                            "Уровень ${hud.level} ${BIOME_NAMES[biomeOfLevel(hud.level)]} · монстров: ${hud.mobsLeft}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF455A64)
                        )
                    }
                }
            }
            if (hud.bossActive) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 74.dp)
                        .width(220.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (hud.bossPhase >= 2) "БОСС 🔥 ЯРОСТЬ!" else if (hud.bossPhase == 1) "БОСС 😡 ${hud.bossHp}/${hud.bossMax}" else "БОСС ${hud.bossHp}/${hud.bossMax}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF4A148C))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFCFD8DC))
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .fillMaxWidth(hud.bossHp / hud.bossMax.toFloat())
                                .background(if (hud.bossPhase >= 2) Color(0xFFE53935) else if (hud.bossPhase == 1) Color(0xFFFB8C00) else Color(0xFF8E24AA))
                        )
                    }
                }
            }

            // Управление.
            ZStick(
                input,
                Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 8.dp, bottom = 8.dp)
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 14.dp, bottom = 18.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ZRoundButton(R.drawable.ui_btn_up_green, "", 82) { input.jump = it }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ZRoundButton(R.drawable.ui_btn_sq_blue, if (hud.weapon == 0) "🔫" else "💣", 58) { down ->
                        if (down) world?.toggleWeapon()
                    }
                    ZRoundButton(R.drawable.ui_btn_sq_red, "💥", 94) { input.attack = it }
                }
            }
            // Пауза.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 6.dp, end = 8.dp)
            ) {
                ZRoundButton(R.drawable.ui_btn_pause, "", 46) { down ->
                    if (down) { input.stickX = 0f; input.stickY = 0f; input.jump = false; input.attack = false; screen = ZScreen.PAUSED }
                }
            }
        }

        if (screen == ZScreen.MENU) {
            Box(Modifier.fillMaxSize()) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(R.drawable.ui_bg_pink), null, Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Column(
                    modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    KitWindow(width = 330.dp, title = "Зелик") {
                        val opts = listOf(
                            Triple("Легко", R.drawable.ui_btn_long_green, 0),
                            Triple("Средне", R.drawable.ui_btn_long_blue, 1),
                            Triple("Сложно", R.drawable.ui_btn_long_red, 2)
                        )
                        for ((name, res, i) in opts) {
                            KitLongButton(res, (if (diff == i) "★ " else "") + name, 170.dp, 15.sp, selected = diff == i) { diff = i }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Беги вправо, прыгай (два раза — на крыльях), собирай монеты и победи босса!\nПрисядь и жми 💥 — звёздная ракета.",
                        fontSize = 12.sp, color = Color(0xFF6A1B4D), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (i in 0 until BIOME_COUNT) {
                            KitLongButton(
                                if (i == 0) R.drawable.ui_btn_long_green else if (i == 1) R.drawable.ui_btn_long_blue else R.drawable.ui_btn_long_red,
                                BIOME_NAMES[i], 100.dp, 11.sp, selected = startBiome == i
                            ) { startBiome = i; soundPlayer.playPop(1.2f) }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(56.dp)) {
                            if (helperIdx >= 0) {
                                val lay = monLayout(helperImgs, size.width / 2f, size.height - 2f, size.width, size.height - 2f)
                                drawMonster(helperImgs, helperSpec, lay, helperAnim, 0f, density, 0f)
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        KitLongButton(
                            R.drawable.ui_btn_long_blue,
                            if (houseList.isEmpty()) "Нужен монстрик 👾" else if (helperIdx < 0) "Помощник: нет" else "Помощник №${helperIdx + 1}",
                            190.dp, 12.sp, selected = helperIdx >= 0
                        ) {
                            if (houseList.isNotEmpty()) {
                                helperIdx = if (helperIdx + 1 >= houseList.size) -1 else helperIdx + 1
                                helperAnim.jump(300f); helperAnim.squash = 0.5f
                                soundPlayer.playPop(1.3f)
                                try { context.getSharedPreferences("playground_prefs", android.content.Context.MODE_PRIVATE).edit().putInt("mb_helper", helperIdx).apply() } catch (e: Exception) { }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    KitLongButton(R.drawable.ui_btn_long_green, "СТАРТ ▶", 210.dp, 22.sp) { beginGame(diff) }
                }
            }
        }

        LottieConfetti(confettiTrig * 2 + (if (screen == ZScreen.LEVEL_DONE) 1 else 0), Modifier.fillMaxSize())

        if (screen == ZScreen.PAUSED) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                KitWindow(width = 330.dp, title = "Пауза") {
                    KitLongButton(R.drawable.ui_btn_long_green, "Играть", 170.dp, 15.sp) { screen = ZScreen.PLAY }
                    KitLongButton(R.drawable.ui_btn_long_blue, "Заново", 170.dp, 15.sp) {
                        world?.restartLevel(); screen = ZScreen.PLAY
                    }
                    KitLongButton(R.drawable.ui_btn_long_red, "В меню", 170.dp, 15.sp) { screen = ZScreen.MENU }
                }
            }
        }

        if (screen == ZScreen.LEVEL_DONE || screen == ZScreen.GAME_OVER) {
            val won = screen == ZScreen.LEVEL_DONE
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                KitWindow(width = 350.dp, title = if (won) "Победа!" else "Ой!") {
                    if (won) {
                        val st = hud.lives.coerceIn(1, 3)
                        Text("⭐".repeat(st) + "☆".repeat(3 - st), fontSize = 26.sp, color = Color(0xFFFF8F00))
                    } else {
                        Text("Зелик устал 😴", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6D4C41))
                    }
                    Text(
                        "🪙 ${hud.coins}" + if (won) "   ·   монстров: ${(world?.mobsTotal ?: 0) - hud.mobsLeft}/${world?.mobsTotal ?: 0}" else "",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6D4C41)
                    )
                    Spacer(Modifier.height(4.dp))
                    KitLongButton(R.drawable.ui_btn_long_green, if (won) "Дальше ▶" else "Ещё раз ↻", 160.dp, 14.sp) {
                        val cur = world
                        if (cur != null) {
                            if (won) cur.startLevel(cur.level + 1) else cur.restartLevel()
                            screen = ZScreen.PLAY
                        }
                    }
                    KitLongButton(R.drawable.ui_btn_long_blue, "В меню", 160.dp, 14.sp) { screen = ZScreen.MENU }
                }
            }
        }
    }

    // Игровой цикл: физика с фиксированным шагом 60 Гц.
    LaunchedEffect(Unit) {
        var last = 0L
        var acc = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                last = now
                val w = world
                if (w != null && screen == ZScreen.PLAY) {
                    acc += dt
                    var steps = 0
                    while (acc >= 1f / 60f && steps < 4) {
                        w.stepFrame()
                        acc -= 1f / 60f
                        steps++
                        if (screen != ZScreen.PLAY) break
                    }
                    if (steps == 4) acc = 0f
                    val h = w.hud()
                    if (h != hud) hud = h
                } else {
                    acc = 0f
                }
                tick++
            }
        }
    }
}
