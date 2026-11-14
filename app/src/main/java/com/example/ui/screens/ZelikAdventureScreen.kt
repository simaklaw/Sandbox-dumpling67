package com.example.ui.screens

import com.example.shared.withFrameNanosEco
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.example.shared.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
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
import com.example.BuildConfig
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
 *
 * Враги: скелеты с оружием (ходят, замахиваются, стреляют из лука) и герои из модульного набора ch_*.
 * Босс каждой локации — гигантский скелет с огромным мечом. Один раз за игру, в одной случайной локации,
 * вместо него выходит злой дух: бросает порывы воздуха, а на Зелика нападают 10 героев.
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
private const val BOSS_H = 190f          // рост гигантского скелета-босса

private val MOB_HP = intArrayOf(10, 20, 30)
private val MOB_DMG = intArrayOf(2, 3, 5)

// ---------- Модели ----------
/** oneWay — тонкая площадка: можно запрыгнуть снизу, встать сверху и спрыгнуть вниз (присесть + прыжок). Остальные — сплошные блоки (ступени). */
private class ZPlat(val x: Float, val y: Float, val w: Float, val h: Float, val oneWay: Boolean = false)

/** Пружина-батут: подбрасывает героя на высокие площадки. */
private class ZSpring(val x: Float, val y: Float) { var comp = 0 }

private class ZEnemy(
    var x: Float, var y: Float, val r: Float, val tier: Int, var hp: Int, val maxHp: Int,
    val vx: Float, var dir: Float, val baseX: Float, val range: Float
) {
    var hitFlash = 0
    val anim = Random.nextFloat() * 6.28f
    var fireCd = 90 + Random.nextInt(120)   // подсолнухи плюются огнём
    var windup = 0

    // Бойцы (tier 0): скелеты с оружием и герои. У подсолнухов (tier 1–2) эти поля не используются.
    var look = 0                 // 0..7 — скелет, 100..108 — герой из набора, 109 — золотой богатырь
    var weapon = 0
    var dmg = 2
    var hh = 64f                 // рост рисунка
    var floorY = 0f              // линия земли под ногами
    var minX = 0f
    var maxX = 0f
    var aggro = false            // нападает, где бы ни стоял игрок (герои финала)
    var gold = false
    var style: SkelStyle? = null
    var walk = 0f
    var stride = 0f
    var atkT = 0
    var atkCd = 40
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
    var kind = 0          // 0..3 — гигантский скелет по локации, 4 — злой дух
    var phase = 0         // 0..2 — по потере здоровья
    var state = 0         // 0 ходьба, 1 разгон (предупреждение), 2 рывок, 3 прыжок вверх, 4 падение, 5 замах мечом, 6 удар мечом
    var stateT = 0
    var skillCd = 120
    var lastSkill = -1
    var vx = 0f
    var vy = 0f
    var dashDir = 1f
    var deadT = 0
    var walk = 0f
    var stride = 0f
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

/** Снаряд врага: kind 0 — стрела, 1 — звезда волшебника, 2 — порыв воздуха злого духа. */
private class ZEShot(var x: Float, var y: Float, var vx: Float, var vy: Float, val kind: Int, val dmg: Int) {
    var life = 260
    var t = 0
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
    val level: Int = 1,
    val bossKind: Int = 0,
    val heroesLeft: Int = 0
)

private class ZHelperGfx(val imgs: MonImgs, val spec: MonSpec, val anim: MonAnim)

/** Барашек-декорация: пасётся, иногда неспешно прогуливается; ни с чем не взаимодействует. */
private class ZSheep(val homeX: Float, var dir: Float, val seed: Int) {
    var x = homeX
    var walking = false
    var timer = 60 + (seed * 37) % 200
    var clock = seed * 13
}

private const val SHEEP_W = 62
private const val SHEEP_H = 39

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
    val sunCured: ImageBitmap,
    val sheep: ImageBitmap // атлас барашков: 4 ряда (пасётся →, ←; идёт →, ←) × 4 кадра
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
    val sheep = ImageBitmap.imageResource(R.drawable.zelik_sheep)
    return remember { ZFrames(idle, walk, run, jump, hurt, dead, fire, attack, sunWalk, sunFade, sunCured, sheep) }
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

    // «Злой дух и 10 героев»: один раз за игру, в одной случайной локации
    private val spiritBiome = Random.nextInt(BIOME_COUNT)
    private var spiritDone = false
    var forceSpirit = false          // отладка: финал на первом же уровне
    var spiritLevel = false
    private val roster = ArrayList<Int>()   // герои, которые ещё не вышли
    private var heroCd = 0

    val platforms = ArrayList<ZPlat>()
    val springs = ArrayList<ZSpring>()
    var coyote = 0
    var dropT = 0
    val enemies = ArrayList<ZEnemy>()
    val coins = ArrayList<ZCoin>()
    val cured = ArrayList<ZCured>()
    val sheep = ArrayList<ZSheep>()
    val meds = ArrayList<ZMed>()
    var boss = ZBoss(0f, 0f, 70f, 110f, 1, 1, 200)

    val bolts = ArrayList<ZBolt>()
    val grenades = ArrayList<ZGrenade>()
    val homings = ArrayList<ZHoming>()
    val bossRockets = ArrayList<ZBossRocket>()
    val eShots = ArrayList<ZEShot>()
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
    var jumpBuf = 0
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
        sheep.clear()
        if (biomeOfLevel(n) <= 1) { // барашки только на полях и в лесу
            var gx = 500f + rng.nextFloat() * 300f
            while (gx < arenaLeft - 400f) {
                repeat(1 + rng.nextInt(3)) { k ->
                    sheep.add(ZSheep(gx + k * (90f + rng.nextFloat() * 50f), if (rng.nextBoolean()) 1f else -1f, sheep.size + 1))
                }
                gx += 900f + rng.nextFloat() * 700f
            }
        }
        springs.clear()
        val structCoins = ArrayList<ZCoin>()
        fun sc(x: Float, y: Float) { structCoins.add(ZCoin(x, y, rng.nextFloat() * 6.28f)) }
        var gx = 450f
        val endX = arenaLeft - 350f
        while (gx < endX) {
            var pattern = rng.nextInt(4)
            if (pattern == 0 && gx + 800f > arenaLeft - 120f) pattern = 3 // лестнице не хватает места до арены
            when (pattern) {
                0 -> { // лестница вверх и вниз: герой поднимается пешком
                    val steps = 3 + rng.nextInt(3)
                    val sw = 64f; val sh = 26f
                    for (k in 1..steps) platforms.add(ZPlat(gx + (k - 1) * sw, GROUND_Y - k * sh, sw, k * sh))
                    val px0 = gx + steps * sw
                    val topY = GROUND_Y - steps * sh
                    platforms.add(ZPlat(px0, topY, 120f, steps * sh))
                    for (j in 1 until steps) platforms.add(ZPlat(px0 + 120f + (j - 1) * sw, GROUND_Y - (steps - j) * sh, sw, (steps - j) * sh))
                    for (k in 1..steps) sc(gx + (k - 0.5f) * sw, GROUND_Y - k * sh - 32f)
                    for (q in 0..2) sc(px0 + 20f + q * 40f, topY - 34f)
                    gx = px0 + 120f + (steps - 1) * sw + 260f
                }
                1 -> { // цепочка парящих площадок — запрыгиваем снизу
                    val cnt = 3 + rng.nextInt(3)
                    var cx = gx
                    var cy = GROUND_Y - 85f - rng.nextFloat() * 20f
                    for (i in 0 until cnt) {
                        val w = 140f + rng.nextInt(50)
                        platforms.add(ZPlat(cx, cy, w, 18f, true))
                        for (q in 0..2) sc(cx + w * (0.25f + 0.25f * q), cy - 34f)
                        cx += w + 60f + rng.nextFloat() * 40f
                        val dy = 70f + rng.nextFloat() * 25f
                        cy = if (i < cnt / 2) max(190f, cy - dy) else min(GROUND_Y - 80f, cy + dy)
                    }
                    gx = cx + 240f
                }
                2 -> { // пружина к высокой площадке
                    springs.add(ZSpring(gx + 40f, GROUND_Y))
                    val ty = GROUND_Y - 290f - rng.nextFloat() * 25f
                    platforms.add(ZPlat(gx + 110f, ty, 180f, 18f, true))
                    for (q in 0..5) sc(gx + 40f, GROUND_Y - 80f - q * 45f)
                    for (q in 0..3) sc(gx + 140f + q * 40f, ty - 34f)
                    gx += 480f
                }
                else -> { // дуга монет — собираем в прыжке
                    for (i in 0..6) sc(gx + i * 38f, GROUND_Y - 34f - sin(i / 6f * Math.PI.toFloat()) * 120f)
                    gx += 420f
                }
            }
        }
        // арена босса чистая: ни ступеней, ни пружин от обычных участков
        val ground = platforms[0]
        platforms.removeAll { it !== ground && it.x + it.w > arenaLeft - 60f }
        structCoins.removeAll { it.x > arenaLeft - 60f }
        springs.removeAll { it.x > arenaLeft - 60f }
        platforms.add(ZPlat(arenaLeft + 30f, 380f, 140f, 18f, true))
        platforms.add(ZPlat(arenaRight - 160f, 380f, 140f, 18f, true))
        platforms.add(ZPlat(arenaLeft + 260f, 300f, 150f, 18f, true))

        enemies.clear()
        eShots.clear()
        val biome = biomeOfLevel(n)
        spiritLevel = !spiritDone && (forceSpirit || biome == spiritBiome)
        val base = 18 + n * 3
        val total = if (diff == 0) (base * 0.6f).toInt() else base
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
        val solids = platforms.filter { it !== ground && !it.oneWay }
        val flo = platforms.filter { it.oneWay && !(it.x + it.w > arenaLeft - 100f && it.x < arenaRight + 100f) }
        for (i in 0 until total) {
            val tier = tiers[i]
            val onPlat = rng.nextFloat() < 0.5f && flo.isNotEmpty()
            val patrol = 60f + rng.nextFloat() * 80f
            val dir = if (rng.nextBoolean()) 1f else -1f
            if (tier == 0) {
                // боец: скелет с оружием или герой из набора
                val look = if (rng.nextFloat() < 0.55f) rng.nextInt(SKEL_WEAPONS.size) else 100 + rng.nextInt(HERO_COUNT)
                if (onPlat) {
                    val p = flo[rng.nextInt(flo.size)]
                    val range = min(patrol, p.w / 2f - 24f)
                    val ex = p.x + p.w / 2f + (rng.nextFloat() * 2f - 1f) * range
                    enemies.add(makeFighter(ex, p.y, look, dir, p.x + 20f, p.x + p.w - 20f, range, rng))
                } else {
                    var ex = 400f + rng.nextFloat() * (arenaLeft - 800f)
                    var tries = 0
                    while (tries < 12 && solids.any { ex + patrol + 30f > it.x && ex - patrol - 30f < it.x + it.w }) {
                        ex = 400f + rng.nextFloat() * (arenaLeft - 800f)
                        tries++
                    }
                    if (tries >= 12) continue // рядом слишком много ступеней: этот враг не появляется
                    var lo = 60f
                    var hi = arenaLeft - 40f
                    for (s in solids) {
                        if (s.x + s.w <= ex) lo = max(lo, s.x + s.w + 20f) else if (s.x >= ex) hi = min(hi, s.x - 20f)
                    }
                    enemies.add(makeFighter(ex, GROUND_Y, look, dir, lo, hi, patrol, rng))
                }
            } else {
                // подсолнух
                val r = (16f + rng.nextInt(10)) * 1.25f
                val ex: Float
                val ey: Float
                val baseX: Float
                var range = patrol
                if (onPlat) {
                    val p = flo[rng.nextInt(flo.size)]
                    range = min(patrol, p.w / 2f - 24f)
                    baseX = p.x + p.w / 2f
                    ex = baseX + (rng.nextFloat() * 2f - 1f) * range
                    ey = p.y - r - 4f
                } else {
                    ex = 400f + rng.nextFloat() * (arenaLeft - 800f)
                    baseX = ex
                    ey = GROUND_Y - r - 8f
                }
                enemies.add(ZEnemy(ex, ey, r, tier, MOB_HP[tier], MOB_HP[tier], 0.7f + rng.nextFloat() * 0.7f, dir, baseX, range))
            }
        }
        mobsTotal = enemies.size

        coins.clear()
        cured.clear()
        coins.addAll(structCoins)
        val coinTarget = 20 + n * 3
        for (i in 0 until coinTarget) { // россыпь на земле — всегда достаётся
            val cx = 250f + rng.nextFloat() * (arenaLeft - 450f)
            coins.add(ZCoin(cx, GROUND_Y - 30f - rng.nextFloat() * 30f, rng.nextFloat() * 6.28f))
        }

        meds.clear()
        meds.add(ZMed(levelLength * 0.25f, GROUND_Y - 40f))
        meds.add(ZMed(levelLength * 0.5f, GROUND_Y - 40f))
        meds.add(ZMed(levelLength * 0.75f, GROUND_Y - 40f))
        if (spiritLevel) { // в финале на арене лежат три аптечки
            meds.add(ZMed(arenaLeft + 120f, GROUND_Y - 40f))
            meds.add(ZMed((arenaLeft + arenaRight) / 2f, GROUND_Y - 40f))
            meds.add(ZMed(arenaRight - 120f, GROUND_Y - 40f))
        }

        val bossHp = ((when (diff) { 0 -> 40; 1 -> 60; else -> 80 }) * 1.8f).toInt() + (n - 1) * 16
        val interval = max(110, 190 - n * 12) + (if (diff == 0) 50 else 0)
        roster.clear()
        if (spiritLevel) {
            val hp = (bossHp * 1.25f).toInt()
            boss = ZBoss(levelLength - 300f, GROUND_Y - 165f, 92f, 130f, hp, hp, (interval * 0.7f).toInt())
            boss.kind = 4
            // девять разных героев из набора и десятый — золотой богатырь
            roster.addAll((0 until HERO_COUNT).shuffled(rng).map { 100 + it })
            roster.add(109)
            heroCd = 70
        } else {
            boss = ZBoss(levelLength - 300f, GROUND_Y - BOSS_H, 96f, BOSS_H, bossHp, bossHp, interval)
            boss.kind = biome
        }
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
            crouching = ZelikControls.crouch(input.stickX, input.stickY, onGround)
            val speedMul = if (crouching) 0.5f else 1f
            pvx = moveX * MOVE_SPEED * speedMul

            if (onGround) { airJumps = 1; coyote = 7 } else if (coyote > 0) coyote--
            val standingOnOneWay = onGround && platforms.any { it.oneWay && px < it.x + it.w && px + pw > it.x && abs(py + ph - it.y) < 1.5f }
            // Прыжок: кнопка или уверенный стик вверх; нажатие чуть раньше приземления не пропадает.
            val jumpHeld = input.jump || ZelikControls.stickJump(input.stickX, input.stickY)
            val jumpPress = jumpHeld && !jumpPrev
            if (jumpPress) jumpBuf = ZelikControls.JUMP_BUFFER else if (jumpBuf > 0) jumpBuf--
            if (jumpPress && crouching && standingOnOneWay) {
                dropT = 14; py += 3f; onGround = false; coyote = 0; jumpBuf = 0 // спрыгнуть вниз сквозь площадку
            } else if ((jumpHeld || jumpBuf > 0) && (onGround || coyote > 0)) {
                pvy = JUMP_POWER
                onGround = false
                coyote = 0
                jumpBuf = 0
                onEvent("jump")
            } else if (jumpPress && !onGround && airJumps > 0) {
                // второй прыжок — на крыльях
                airJumps--
                pvy = JUMP_POWER * 0.92f
                wingT = 0
                jumpBuf = 0
                onEvent("wings")
            }
            jumpPrev = jumpHeld

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
            if (p.oneWay) continue
            if (overlapsPlat(p)) {
                val rise = py + ph - p.y
                if (rise in 0f..34f && pvy >= 0f) py = p.y - ph // ступенька: поднимаемся пешком
                else if (pvx > 0f) px = p.x - pw else if (pvx < 0f) px = p.x + p.w
            }
        }
        val prevBottom = py + ph
        py += pvy
        onGround = false
        for (p in platforms) {
            if (p.oneWay) {
                if (dropT <= 0 && pvy >= 0f && px < p.x + p.w && px + pw > p.x && prevBottom <= p.y + 1f && py + ph >= p.y) {
                    py = p.y - ph; pvy = 0f; onGround = true
                }
            } else if (overlapsPlat(p)) {
                if (pvy > 0f) { py = p.y - ph; pvy = 0f; onGround = true }
                else if (pvy < 0f) { py = p.y + p.h; pvy = 0f }
            }
        }
        if (dropT > 0) dropT--
        if (!dead) {
            for (sp in springs) {
                val feet = py + ph
                if (pvy >= 0f && feet >= sp.y - 30f && feet <= sp.y + 6f && abs(px + pw / 2f - sp.x) < 30f) {
                    pvy = -19.5f; onGround = false; airJumps = 1; sp.comp = 12
                    onEvent("jump")
                    repeat(8) { val a = Random.nextFloat() * 6.28f; parts.add(ZPart(sp.x, sp.y - 10f, cos(a) * 2.5f, -abs(sin(a)) * 3f, 1f, 0.05f, 2.5f, Color(0xFFFF8A80))) }
                }
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
            spawnBones(e.x, e.y, e.look >= 100)
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
            if (boss.kind != 4) enemies.clear() // герои духа продолжают бой, пока не погибнут
            eShots.clear()
            roster.clear()
            if (boss.kind == 4) spiritDone = true
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
        if (boss.kind == 4) { updateSpirit(); return }
        val pcx = px + pw / 2f
        val bcx = boss.x + boss.w / 2f
        val dx = pcx - bcx
        val dist = abs(dx)
        val dir = if (dx >= 0f) 1f else -1f
        val groundTop = GROUND_Y - boss.h
        val rage = 1f + boss.phase * 0.3f
        boss.stateT++
        val x0 = boss.x
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
                    boss.state = 6; boss.stateT = 6 // меч врезается в землю, затем возврат
                    boss.skillCd = (boss.shootInterval * 0.7f).toInt()
                    val cx = boss.x + boss.w / 2f
                    shocks.add(ZShock(cx - 20f, -1f)); shocks.add(ZShock(cx + 20f, 1f))
                    if (boss.phase >= 2) { shocks.add(ZShock(cx - 120f, -1f)); shocks.add(ZShock(cx + 120f, 1f)) }
                    spawnBigExplosion(cx, GROUND_Y - 10f, 90f)
                    onEvent("bossSlam")
                    if (!dead && invuln <= 0 && py + ph > GROUND_Y - 24f && abs(pcx - cx) < 70f) hurtPlayer(5, cx, 7f, -8f)
                }
            }
            5 -> { // замах сокрушающего удара: меч поднят над головой, на земле горит зона удара
                boss.y = groundTop
                boss.x -= dir * 0.3f
                if (boss.stateT == 1) onEvent("bossCharge")
                if (boss.stateT >= 38) { crushStrike(dir); boss.state = 6; boss.stateT = 0 }
            }
            6 -> { // удар мечом и возврат в стойку
                boss.y = groundTop
                if (boss.stateT >= 26) { boss.state = 0; boss.stateT = 0 }
            }
        }
        if (boss.x < arenaLeft + 10f) boss.x = arenaLeft + 10f
        if (boss.x + boss.w > arenaRight - 10f) boss.x = arenaRight - 10f - boss.w
        val moved = abs(boss.x - x0)
        val walking = (boss.state == 0 || boss.state == 2) && moved > 0.05f
        if (walking) boss.walk += moved * (6.2832f / (0.85f * boss.h))
        boss.stride += ((if (walking) 1f else 0f) - boss.stride) * 0.15f

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
        options.add(5)                           // сокрушающий удар мечом
        if (boss.phase >= 1) { options.add(1); options.add(4); options.add(5) }   // веер огня, призыв
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
            5 -> { boss.state = 5; boss.stateT = 0 }
            4 -> {
                if (enemies.size < 3) {
                    for (sgn in floatArrayOf(-1f, 1f)) {
                        val ex = (boss.x + boss.w / 2f + sgn * 110f).coerceIn(arenaLeft + 40f, arenaRight - 40f)
                        val look = if (Random.nextBoolean()) Random.nextInt(SKEL_WEAPONS.size) else 100 + Random.nextInt(HERO_COUNT)
                        val e = makeFighter(ex, GROUND_Y, look, -sgn, arenaLeft + 10f, arenaRight - 10f, 120f)
                        e.aggro = true
                        enemies.add(e)
                    }
                    onEvent("bossSummon")
                }
                boss.skillCd = (interval * 0.9f).toInt()
            }
        }
    }

    /** Сокрушающий удар гигантского меча: волна по земле вперёд, урон тому, кто стоит под клинком. */
    private fun crushStrike(dir: Float) {
        val cx = boss.x + boss.w / 2f
        val hitX = cx + dir * 150f
        spawnBigExplosion(hitX, GROUND_Y - 10f, 100f)
        shocks.add(ZShock(hitX, dir))
        if (boss.phase >= 1) shocks.add(ZShock(hitX, -dir))
        boss.skillCd = (boss.shootInterval * 0.65f).toInt()
        onEvent("bossSlam")
        val front = (px + pw / 2f - cx) * dir
        if (!dead && invuln <= 0 && front > 40f && front < 270f && py + ph > GROUND_Y - 80f) hurtPlayer(5, hitX, 7f, -8f)
    }

    // ----- злой дух и герои -----
    private fun aliveHeroes(): Int {
        var n = 0
        for (e in enemies) if (e.look >= 100) n++
        return n
    }

    /** Следующий герой выходит с дальнего от Зелика края арены. */
    private fun spawnFinaleHero() {
        if (roster.isEmpty()) return
        val look = roster.removeAt(0)
        val fromRight = px + pw / 2f < (arenaLeft + arenaRight) / 2f
        val x = if (fromRight) arenaRight - 60f - Random.nextFloat() * 80f else arenaLeft + 60f + Random.nextFloat() * 80f
        val e = makeFighter(x, GROUND_Y, look, if (fromRight) -1f else 1f, arenaLeft + 10f, arenaRight - 10f, 0f)
        e.aggro = true
        e.atkCd = 45
        enemies.add(e)
        spawnBigExplosion(e.x, GROUND_Y - e.hh * 0.5f, 50f)
        onEvent("bossSummon")
    }

    private fun updateSpirit() {
        val pcx = px + pw / 2f
        val bcx = boss.x + boss.w / 2f
        val dx = pcx - bcx
        val dist = abs(dx)
        val dir = if (dx >= 0f) 1f else -1f
        val rage = 1f + boss.phase * 0.3f
        boss.stateT++
        // парит невысоко над землёй, чтобы Зелик мог достать
        boss.y = GROUND_Y - 165f + sin(animTime * 0.05f) * 22f
        if (dist > 470f) boss.x += dir * 1.8f * rage else if (dist < 240f) boss.x -= dir * 1.4f * rage
        boss.x += sin(animTime * 0.021f) * 0.9f
        boss.x = boss.x.coerceIn(arenaLeft + 10f, arenaRight - 10f - boss.w)
        // герои выходят по одному, пока не выйдут все десять
        if (roster.isNotEmpty() && --heroCd <= 0 && aliveHeroes() < 6) {
            spawnFinaleHero()
            heroCd = 75 - boss.phase * 12
        }
        if (boss.state == 0) {
            if (--boss.skillCd <= 0) {
                boss.state = 1; boss.stateT = 0
                val opts = ArrayList<Int>()
                opts.add(0); opts.add(0)
                if (boss.phase >= 1) opts.add(1)
                if (boss.phase >= 2) opts.add(2)
                boss.lastSkill = opts.random()
            }
        } else if (boss.stateT >= 28) { // копил порыв 28 тиков — бросает
            spiritCast(boss.lastSkill)
            boss.state = 0; boss.stateT = 0
            boss.skillCd = (boss.shootInterval * 0.8f).toInt()
        }
        if (!dead && invuln <= 0 && px < boss.x + boss.w && px + pw > boss.x && py < boss.y + boss.h && py + ph > boss.y) {
            hurtPlayer(2, bcx, 7f, -7f)
        }
    }

    /** Бросок воздуха: 0 — веер, 1 — кольцо во все стороны, 2 — один большой быстрый порыв. */
    private fun spiritCast(skill: Int) {
        val cx = boss.x + boss.w / 2f
        val cy = boss.y + boss.h * 0.4f
        val toPlayer = atan2((py + ph / 2f) - cy, (px + pw / 2f) - cx)
        when (skill) {
            0 -> {
                val n = 3 + boss.phase
                for (i in 0 until n) {
                    val a = toPlayer + (i - (n - 1) / 2f) * 0.38f
                    eShots.add(ZEShot(cx, cy, cos(a) * 3.6f, sin(a) * 3.6f, 2, 1))
                }
            }
            1 -> for (i in 0 until 10) {
                val a = i * 6.2832f / 10f + animTime * 0.01f
                eShots.add(ZEShot(cx, cy, cos(a) * 2.7f, sin(a) * 2.7f, 2, 1))
            }
            else -> eShots.add(ZEShot(cx, cy, cos(toPlayer) * 5.2f, sin(toPlayer) * 5.2f, 2, 2).also { it.life = 200 })
        }
        onEvent("spiritAir")
    }

    // ----- бойцы: скелеты с оружием и герои -----
    private fun makeFighter(x: Float, floorY: Float, look: Int, dir: Float, minX: Float, maxX: Float, patrol: Float, rng: Random = Random): ZEnemy {
        val hero = look >= 100
        val h = if (look == 109) 7 else look - 100
        val hh = if (hero) HERO_HEIGHT[h] * (if (look == 109) 1.12f else 1f) else 58f + rng.nextInt(10)
        val hp = if (!hero) MOB_HP[0] else if (look == 109) 60 else HERO_HP[h]
        val speed = (0.7f + rng.nextFloat() * 0.6f) * (if (hero) HERO_SPEED[h] else 1f)
        val cx = x.coerceIn(minX, maxX)
        val e = ZEnemy(cx, floorY - hh * 0.5f, hh * 0.36f, 0, hp, hp, speed, dir, cx, patrol)
        e.look = look
        e.hh = hh
        e.floorY = floorY
        e.minX = minX
        e.maxX = maxX
        if (hero) {
            e.weapon = HERO_WEAPON[h]
            e.dmg = if (look == 109) 3 else HERO_DMG[h]
            e.gold = look == 109
        } else {
            e.weapon = SKEL_WEAPONS[look.coerceIn(0, SKEL_WEAPONS.size - 1)]
            e.dmg = MOB_DMG[0]
            val shield = (e.weapon == 0 || e.weapon == 2 || e.weapon == 3) && rng.nextInt(3) != 0
            e.style = skelStyle(e.weapon, rng.nextInt(7), biomeOfLevel(level), shield)
        }
        return e
    }

    private fun updateFighter(e: ZEnemy) {
        val ranged = weaponRanged(e.weapon)
        val windup = weaponWindup(e.weapon)
        val pcx = px + pw / 2f
        val dx = pcx - e.x
        val dist = abs(dx)
        val dyFeet = (py + ph) - e.floorY // меньше нуля — Зелик выше врага
        val sight = if (ranged) 520f else 330f
        val near = !dead && (e.aggro || (dist < sight && dyFeet > -130f && dyFeet < 70f))
        if (e.atkCd > 0) e.atkCd--
        var moved = 0f
        if (e.atkT > 0) {
            e.atkT++
            if (e.atkT == windup) foeStrike(e, dx)
            if (e.atkT >= windup + 18) { e.atkT = 0; e.atkCd = (if (ranged) 110 else 55) + Random.nextInt(40) }
        } else {
            var step = 0f
            if (near) {
                val want = if (dx >= 0f) 1f else -1f
                e.dir = want
                val reach = weaponReach(e.weapon)
                if (ranged) {
                    if (dist < 170f) step = -want * e.vx * 1.1f else if (dist > 380f) step = want * e.vx * 1.2f
                    if (e.atkCd <= 0 && dist > 110f && dist < 560f && dyFeet > -320f) e.atkT = 1
                } else {
                    if (dist > reach * 0.75f) step = want * e.vx * 1.5f
                    if (e.atkCd <= 0 && dist <= reach + 6f) e.atkT = 1
                }
            } else {
                step = e.dir * e.vx
                if (e.x > e.baseX + e.range) e.dir = -1f
                if (e.x < e.baseX - e.range) e.dir = 1f
            }
            if (step != 0f) {
                var nx = e.x + step
                if (nx < e.minX || nx > e.maxX) {
                    nx = nx.coerceIn(e.minX, e.maxX)
                    if (!near) e.dir = -e.dir
                }
                // герои финала не слипаются: идущий сзади ждёт того, кто впереди
                if (e.aggro && enemies.any { o -> o !== e && o.tier == 0 && abs(o.x - nx) < 22f && abs(o.x - e.x) > abs(o.x - nx) }) nx = e.x
                moved = abs(nx - e.x)
                e.x = nx
            }
        }
        e.walk += moved * (6.2832f / (0.85f * e.hh))
        e.stride += ((if (moved > 0.01f) 1f else 0f) - e.stride) * 0.2f
        // толчок телом — лёгкий ушиб, чтобы нельзя было пробегать сквозь врага
        if (!dead && invuln <= 0 && e.atkT == 0 && hypot(pcx - e.x, (py + ph / 2f) - e.y) < e.r + 14f) {
            hurtPlayer(1, e.x, 4f, -5.5f)
        }
    }

    /** Момент удара или выстрела. */
    private fun foeStrike(e: ZEnemy, dx: Float) {
        val pcx = px + pw / 2f
        if (weaponRanged(e.weapon)) {
            val sx = e.x + e.dir * 24f
            val sy = e.floorY - e.hh * 0.55f
            val a = atan2((py + ph * 0.45f) - sy, pcx - sx)
            val magic = e.weapon == 11
            val sp = if (magic) 4.4f else 6f
            eShots.add(ZEShot(sx, sy, cos(a) * sp, sin(a) * sp, if (magic) 1 else 0, e.dmg))
            if (abs(dx) < 700f) onEvent("foeShoot")
            return
        }
        if (abs(dx) < 700f) onEvent("foeSwing")
        val reach = weaponReach(e.weapon)
        val front = (pcx - e.x) * e.dir
        val low = (py + ph) > e.floorY - 70f && py < e.floorY // перепрыгнувшему удар не страшен
        if (!dead && front > -14f && front < reach + 22f && low) hurtPlayer(e.dmg, e.x, 6f, -6.5f)
        val tx = e.x + e.dir * reach
        repeat(5) {
            parts.add(ZPart(tx, e.floorY - 8f, e.dir * Random.nextFloat() * 2.5f, -Random.nextFloat() * 2f, 1f, 0.07f, 2f + Random.nextFloat() * 2f, fxColors.random()))
        }
    }

    private fun updateEShots() {
        val iter = eShots.iterator()
        while (iter.hasNext()) {
            val s = iter.next()
            s.t++
            s.life--
            if (s.kind == 0) s.vy += 0.06f
            s.x += s.vx
            s.y += s.vy
            var hit = false
            val pad = if (s.kind == 2) (if (s.dmg >= 2) 20f else 12f) else 6f
            if (!dead && invuln <= 0 && s.x > px - pad && s.x < px + pw + pad && s.y > py - pad && s.y < py + ph + pad) {
                hurtPlayer(s.dmg, s.x - s.vx * 3f, 4f, -5f)
                hit = true
            }
            if (!hit && s.kind != 2) { // стрелы и звёзды застревают в площадках
                for (p in platforms) {
                    if (s.x > p.x && s.x < p.x + p.w && s.y > p.y && s.y < p.y + p.h) { hit = true; break }
                }
            }
            if (!hit) { // выстрел Зелика сбивает снаряд
                val bi = bolts.iterator()
                while (bi.hasNext()) {
                    val b = bi.next()
                    if (hypot(b.x - s.x, b.y - s.y) < 18f) { bi.remove(); hit = true; break }
                }
            }
            if (hit) {
                repeat(4) {
                    parts.add(ZPart(s.x, s.y, (Random.nextFloat() - 0.5f) * 3f, (Random.nextFloat() - 0.5f) * 3f - 1f, 1f, 0.06f, 2.5f, if (s.kind == 2) Color(0xFFB3E5FC) else Color(0xFFFFE082)))
                }
            }
            if (hit || s.life <= 0 || s.y > GROUND_Y + 40f) iter.remove()
        }
    }

    private fun spawnBones(x: Float, y: Float, hero: Boolean) {
        val col = if (hero) Color(0xFFFFEB3B) else Color(0xFFF1EBDD)
        repeat(12) {
            val a = Random.nextFloat() * 6.28f
            val s = Random.nextFloat() * 3.5f + 1f
            parts.add(ZPart(x, y, cos(a) * s, sin(a) * s - 2.5f, 1f, 0.025f, 3f + Random.nextFloat() * 3f, col))
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
            if (e.tier == 0) {
                if (e.hitFlash > 0) e.hitFlash--
                updateFighter(e)
                continue
            }
            if (e.tier >= 1 && !dead) {
                val dxp = px + pw / 2f - e.x
                val dyp = py + ph / 2f - e.y
                if (e.windup > 0) {
                    e.windup--
                    e.dir = if (dxp >= 0f) 1f else -1f
                    if (e.windup == 0) {
                        val sx = e.x + e.dir * e.r
                        val sy = e.y - e.r * 0.9f
                        val a = atan2(py + ph / 2f - sy, px + pw / 2f - sx)
                        val sp = if (e.tier == 2) 5.2f else 4.4f
                        bossRockets.add(ZBossRocket(sx, sy, cos(a) * sp, sin(a) * sp, straight = true).also { it.angle = a; it.up = false; it.life = 200 })
                        e.fireCd = if (e.tier == 2) 150 else 200
                        onEvent("sunFire")
                    }
                } else {
                    e.fireCd--
                    if (e.fireCd <= 0) {
                        if (abs(dxp) < 560f && abs(dyp) < 280f) e.windup = 34 else e.fireCd = 30
                    }
                }
            }
            if (e.windup <= 0) e.x += e.vx * e.dir
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

    private fun updateSheep() {
        for (sh in sheep) {
            sh.clock++
            if (--sh.timer <= 0) {
                sh.walking = !sh.walking
                sh.timer = if (sh.walking) 90 + (sh.clock * 7) % 120 else 240 + (sh.clock * 11) % 360
                if (sh.walking && Random.nextInt(3) == 0) sh.dir = -sh.dir
            }
            if (sh.walking) {
                sh.x += sh.dir * 0.35f
                if (sh.x > sh.homeX + 70f) sh.dir = -1f
                if (sh.x < sh.homeX - 70f) sh.dir = 1f
            }
        }
    }

    fun stepFrame() {
        animTime++
        for (sp in springs) if (sp.comp > 0) sp.comp--
        updateSheep()
        updatePlayer()
        updateHelper()
        updateBolts()
        updateHoming()
        updateGrenades()
        updatePickups()
        updateBoss()
        updateBossRockets()
        updateEShots()
        updateShocks()
        updateRainCoins()
        updateEnemies()
        updateFX()
        updateCamera()
        if (!levelDone && boss.dead && !dead && boss.deadT > 300 && (boss.kind != 4 || aliveHeroes() == 0)) {
            levelDone = true
            onEvent("levelDone")
        }
    }

    fun hud(): ZHud = ZHud(
        hp = max(0, hp), lives = lives, coins = coinCount, ammo = ammo, weapon = weapon,
        bossHp = max(0, boss.hp), bossPhase = boss.phase, bossMax = boss.maxHp, bossActive = boss.active && !boss.dead,
        mobsLeft = enemies.size, level = level,
        bossKind = boss.kind, heroesLeft = if (spiritLevel) aliveHeroes() + roster.size else 0
    )
}

// ---------- Рисование ----------
/** Картинки героев (враги из модульного набора); заполняются из композиции. */
private var zHeroes: HeroSet? = null

private val HIT_TINT = ColorFilter.tint(Color.White.copy(alpha = 0.75f), BlendMode.SrcAtop)
private val GOLD_TINT = ColorFilter.tint(Color(0xFFFFD54F).copy(alpha = 0.45f), BlendMode.SrcAtop)

/** Боец: скелет с оружием или герой. Ноги стоят на линии floorY, шаг идёт по фазе walk. */
private fun DrawScope.drawFighter(e: ZEnemy) {
    val face = if (e.dir >= 0f) 1f else -1f
    val windup = weaponWindup(e.weapon)
    val arm = atkPose(e.atkT, windup)
    val k = if (e.atkT in 1 until windup) e.atkT / windup.toFloat() else 0f
    val bowPull = if (e.weapon == 4) k else 0f
    val charge = if (e.weapon == 11) k else 0f
    val flash = e.hitFlash > 0
    drawOval(Color.Black.copy(alpha = 0.22f), Offset(e.x - e.hh * 0.3f, e.floorY - 4f), Size(e.hh * 0.6f, 8f))
    val st = e.style
    val set = zHeroes
    if (e.look >= 100) {
        if (set == null) return
        val hero = if (e.look == 109) 7 else e.look - 100
        val tint = if (flash) HIT_TINT else if (e.gold) GOLD_TINT else null
        drawHeroFighter(set, hero, e.x, e.floorY, e.hh, face, e.walk, e.stride, arm, tint, bowPull, charge)
    } else if (st != null) {
        drawBoneFighter(e.x, e.floorY, e.hh, face, st, e.walk, e.stride, arm, flash = flash, bowPull = bowPull, charge = charge)
    }
    if (e.hp < e.maxHp) {
        val bw = e.hh * 0.6f
        val by = e.floorY - e.hh - 12f
        drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(e.x - bw / 2f, by), Size(bw, 6f), CornerRadius(3f))
        drawRoundRect(Color(0xFF66BB6A), Offset(e.x - bw / 2f, by), Size(bw * e.hp / e.maxHp, 6f), CornerRadius(3f))
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
    if (e.windup > 0) {
        val k = 1f - e.windup / 34f
        val cx = e.x + e.dir * e.r * 0.9f
        val cy = e.y - e.r * 0.9f
        drawCircle(Color(0xFFFF6F00).copy(alpha = 0.35f + 0.3f * k), e.r * (0.5f + 0.7f * k), Offset(cx, cy))
        drawCircle(Color(0xFFFFEB3B).copy(alpha = 0.8f), e.r * 0.35f * k + 3f, Offset(cx, cy))
        drawCircle(Color(0xFFFF1744).copy(alpha = 0.25f * k), e.r * 1.4f, Offset(e.x, e.y), style = Stroke(3f))
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
    val sx = b.x + b.w / 2f
    val face = if (tx >= sx) 1f else -1f
    val rage = b.phase >= 2
    if (rage) drawCircle(Color(0xFFFF1744).copy(alpha = 0.16f + 0.08f * sin(t * 0.3f)), b.w * 1.3f, Offset(sx, b.y + b.h / 2f))
    if (b.kind == 4) { // злой дух парит над землёй, под ним лёгкая тень
        val charge = if (b.state == 1) min(1f, b.stateT / 28f) else 0f
        val lift = ((GROUND_Y - (b.y + b.h)) / 220f).coerceIn(0f, 1f)
        drawOval(Color.Black.copy(alpha = 0.2f * (1f - lift * 0.6f)), Offset(sx - b.w * 0.45f, GROUND_Y - 7f), Size(b.w * 0.9f * (1f - lift * 0.3f), 13f))
        drawSpirit(sx, b.y + b.h * 0.5f, b.w, b.h, t, b.hitFlash > 0, charge)
        return
    }
    // предупреждения: рывок, прыжок и сокрушающий удар мечом
    if (b.state == 1) {
        val a = 0.25f + 0.25f * sin(t * 0.5f)
        drawRect(Color.Red.copy(alpha = a.coerceIn(0f, 1f)), Offset(b.x - 900f, GROUND_Y - 60f), Size(1800f, 60f))
    }
    if (b.state == 3 || b.state == 4) {
        drawCircle(Color.Red.copy(alpha = 0.35f), 70f, Offset(sx, GROUND_Y - 4f), style = Stroke(6f))
    }
    if (b.state == 5) {
        val k = (b.stateT / 38f).coerceIn(0f, 1f)
        val zx = sx + face * 150f
        drawRect(Color.Red.copy(alpha = 0.12f + 0.3f * k), Offset(zx - 95f, GROUND_Y - 16f), Size(190f, 16f))
        drawCircle(Color.Red.copy(alpha = 0.2f + 0.3f * k), 40f + 50f * k, Offset(zx, GROUND_Y - 4f), style = Stroke(5f))
    }
    val st = BOSS_STYLES[b.kind.coerceIn(0, BOSS_STYLES.size - 1)]
    val arm = when (b.state) {
        1 -> 0.55f + 0.1f * sin(t * 0.7f)
        2 -> -0.5f
        3 -> 1f
        4 -> -1f
        5 -> atkPose(b.stateT, 38)
        6 -> atkPose(38 + b.stateT, 38)
        else -> 0f
    }
    val lean = when (b.state) {
        1 -> -0.25f + sin(t * 0.8f) * 0.05f
        2 -> 0.3f
        3 -> -0.1f
        4 -> 0.2f
        5 -> -0.2f * (b.stateT / 38f).coerceIn(0f, 1f)
        6 -> 0.22f * (1f - b.stateT / 26f).coerceIn(0f, 1f)
        else -> 0f
    }
    val tuck = if (b.state == 3 || b.state == 4) 1f else 0f
    val jaw = if (b.state == 1 || b.state == 5 || b.state == 3) 0.9f else 0f
    drawOval(Color.Black.copy(alpha = 0.25f), Offset(sx - b.w * 0.55f, GROUND_Y - 9f), Size(b.w * 1.1f, 18f))
    drawBoneFighter(sx, b.y + b.h, b.h, face, st, b.walk, b.stride, arm, lean = lean, tuck = tuck, flash = b.hitFlash > 0, jaw = jaw)
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
        // пружины-батуты
        for (sp in w.springs) {
            if (sp.x < left - 60f || sp.x > right + 60f) continue
            val k = sp.comp / 12f
            val hgt = 30f * (1f - 0.6f * k)
            for (i in 0..3) drawLine(Color(0xFFB0BEC5), Offset(sp.x - 18f, sp.y - 8f - hgt * i / 4f), Offset(sp.x + 18f, sp.y - 8f - hgt * (i + 0.5f) / 4f), 4f)
            drawRoundRect(Color(0xFF455A64), Offset(sp.x - 26f, sp.y - 8f), Size(52f, 8f), CornerRadius(4f))
            drawRoundRect(Color(0xFFFF5252), Offset(sp.x - 30f, sp.y - 8f - hgt - 9f), Size(60f, 10f), CornerRadius(5f))
        }
        // барашки-декорация (позади монет, монстров и героя)
        for (sh in w.sheep) {
            if (sh.x < left - 80f || sh.x > right + 80f) continue
            val right1 = sh.dir > 0f
            val row = if (sh.walking) (if (right1) 2 else 3) else (if (right1) 0 else 1)
            val fr = if (sh.walking) (sh.clock / 8) % 4
                else intArrayOf(0, 1, 2, 3, 3, 3, 2, 1)[(sh.clock / 18) % 8]
            val dw = (SHEEP_W * 1.35f).toInt(); val dh = (SHEEP_H * 1.35f).toInt()
            drawImage(f.sheep, srcOffset = IntOffset(fr * SHEEP_W, row * SHEEP_H), srcSize = IntSize(SHEEP_W, SHEEP_H),
                dstOffset = IntOffset((sh.x - dw / 2f).toInt(), GROUND_Y.toInt() + 6 - dh), dstSize = IntSize(dw, dh),
                filterQuality = FilterQuality.None)
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
        for (e in w.enemies) {
            if (e.x < left - 60f || e.x > right + 60f) continue
            if (e.tier >= 1) drawSunflower(e, t, f) else drawFighter(e)
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
        // стрелы, звёзды и порывы воздуха врагов
        for (s in w.eShots) drawFoeShot(s.kind, s.x, s.y, atan2(s.vy, s.vx), s.t, s.dmg >= 2)

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
    zHeroes = rememberHeroSet()
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
            "sunFire" -> soundManager.play("zb_rocket", volume = 0.5f, rate = 1.3f)
            "bossFire" -> soundManager.play("zb_fire", volume = 0.8f)
            "bossCharge" -> soundManager.play("zb_charge", volume = 0.9f)
            "bossDash" -> soundManager.play("zb_dash", volume = 0.9f)
            "bossSlam" -> { soundManager.play("zb_slam", volume = 1f); soundManager.play("zb_shock", volume = 0.7f) }
            "bossSummon" -> soundManager.play("zb_summon", volume = 0.9f)
            "spiritAir" -> { soundManager.play("zb_dash", volume = 0.8f, rate = 1.5f); soundPlayer.playMagicSparkle() }
            "foeShoot" -> soundPlayer.playBowShot()
            "foeSwing" -> soundManager.play("zb_dash", volume = 0.3f, rate = 1.7f)
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
        soundManager.preloadRaw(listOf("zb_roar", "zb_hurt", "zb_rocket", "zb_fire", "zb_charge", "zb_dash", "zb_slam", "zb_shock",
            "zb_summon", "zb_phase", "zb_coin", "zb_coinrain", "zb_victory", "zb_down", "zb_ouch", "zb_boom"))
    }

    LaunchedEffect(Unit) {
        var last = 0L
        var clk = 0f
        while (true) {
            withFrameNanosEco { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                clk += dt
                helperAnim.step(dt, clk)
            }
        }
    }

    fun beginGame(d: Int, spirit: Boolean = false) {
        diff = d
        val w = ZelikWorld(d, input)
        w.onEvent = { ev -> playEvent(ev, w) }
        w.helperOn = helperIdx >= 0
        w.forceSpirit = spirit
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
                    val bossName = if (hud.bossKind == 4) "ЗЛОЙ ДУХ" else "БОСС"
                    Text(if (hud.bossPhase >= 2) "$bossName 🔥 ЯРОСТЬ!" else if (hud.bossPhase == 1) "$bossName 😡 ${hud.bossHp}/${hud.bossMax}" else "$bossName ${hud.bossHp}/${hud.bossMax}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF4A148C))

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

            if (hud.heroesLeft > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = if (hud.bossActive) 110.dp else 74.dp),
                    shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.8f)
                ) {
                    Text(
                        "Героев осталось: ${hud.heroesLeft}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6A1B9A)
                    )
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
                        "Беги вправо, прыгай (два раза — на крыльях), собирай монеты и победи босса!\nПрисядь и жми 💥 — звёздная ракета.\nСкелеты и герои с оружием — берегись!",
                        fontSize = 12.sp, color = Color(0xFF6A1B4D), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (i in 0 until BIOME_COUNT) {
                            KitLongButton(
                                if (i == 0) R.drawable.ui_btn_long_green else if (i == 1) R.drawable.ui_btn_long_blue else if (i == 2) R.drawable.ui_btn_long_red else R.drawable.ui_btn_long_red,
                                BIOME_NAMES[i], 76.dp, 10.sp, selected = startBiome == i
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
                    if (BuildConfig.DEBUG) {
                        Spacer(Modifier.height(4.dp))
                        KitLongButton(R.drawable.ui_btn_long_red, "ФИНАЛ: дух + 10 героев (отладка)", 250.dp, 11.sp) { beginGame(diff, spirit = true) }
                    }
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
            withFrameNanosEco { now ->
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
