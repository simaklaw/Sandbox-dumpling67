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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
private class RunOrb(var x: Float, val y: Float)
private class RunFire(var x: Float, val y: Float, var t: Float = 0f)
private class RunFx(val x: Float, val y: Float, val n: Int)
/** Чудик: kind 0 — колобок, 1 — прыгун, 2 — здоровяк (2 попадания). */
private class RunFoe(var x: Float, val kind: Int) {
    val w = if (kind == 2) 78f else 58f
    val h = if (kind == 2) 88f else 62f
    var hp = if (kind == 2) 2 else 1
    var y = 0f
    var vy = 0f
    var hopT = 0.6f
    var flash = 0f
    var dead = false
}

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
    var time = 0f
    var nextX = 700f
    var loc = 0
    var squash = 0f
    var wasAir = false
    var kills = 0
    var fireCd = 0f
    var muzzle = 0f
    var hxw = 0f
    var vw = 800f
    var bossState = 0       // 0 нет, 1 бой, 2 побеждён
    var bossHp = 0
    var bossMax = 1
    var bossT = 0f
    var bossFlash = 0f
    var bossAtkT = 2f
    var bossLap = -1
    val orbs = ArrayList<RunOrb>()
    val foes = ArrayList<RunFoe>()
    val fires = ArrayList<RunFire>()
    val fx = ArrayList<RunFx>()
    val obs = ArrayList<RunObs>()
    val items = ArrayList<RunCoin>()
    var onEvent: (String) -> Unit = {}

    fun start(startLoc: Int, visibleW: Float) {
        dist = startLoc * RUN_LOC_LEN
        loc = startLoc
        hy = 0f; vy = 0f; airJumps = 0
        lives = 3; coins = 0; invuln = 0f; slow = 0f; over = false; time = 0f
        obs.clear(); items.clear(); foes.clear(); fires.clear(); fx.clear(); orbs.clear()
        bossState = 0; bossLap = -1; bossFlash = 0f
        kills = 0; fireCd = 0f; muzzle = 0f
        nextX = dist + visibleW + 300f
    }

    fun jump() {
        if (over) return
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
            if (r < 0.40f) {
                obs.add(RunObs(nextX, kind, w, h))
                for (k in 0..4) {
                    val a = k / 4f * PI.toFloat()
                    items.add(RunCoin(nextX + w / 2f - 120f + k * 60f, h + 40f + sin(a) * 80f))
                }
            } else if (r < 0.52f && !toddler) {
                obs.add(RunObs(nextX, kind, w, h))
                obs.add(RunObs(nextX + w + 190f, (kind + 1) % 4, w, h))
                for (k in 0..6) {
                    val a = k / 6f * PI.toFloat()
                    items.add(RunCoin(nextX + w / 2f - 40f + k * 55f, h + 40f + sin(a) * 95f))
                }
                nextX += w + 190f
            } else if (r < 0.84f) {
                val fk = if (toddler) Random.nextInt(2) else Random.nextInt(3)
                foes.add(RunFoe(nextX, fk))
                if (!toddler && Random.nextFloat() < 0.35f) foes.add(RunFoe(nextX + 170f, Random.nextInt(2)))
            } else {
                for (k in 0..5) items.add(RunCoin(nextX + k * 62f, 36f + (if (k % 2 == 0) 0f else 20f)))
            }
            nextX += (sp * (if (toddler) 1.5f else 1.05f) + 260f) * (0.9f + Random.nextFloat() * 0.6f)
        }
    }

    private fun hurt() {
        invuln = 1.4f
        slow = 0.7f
        vy = 380f
        if (hy <= 0f) hy = 1f
        if (!toddler) {
            lives--
            if (lives <= 0) { over = true; onEvent("over") }
        }
        onEvent("hit")
    }

    fun fire() {
        if (over || fireCd > 0f) return
        fireCd = if (toddler) 0.6f else 0.42f
        muzzle = 0.18f
        fires.add(RunFire(hxw + 50f, hy + 64f))
        onEvent("fire")
    }

    fun bossSx(): Float = vw - 170f
    fun bossY(): Float = 150f + sin(time * 2.2f) * 16f

    private fun updateBoss(dt: Float, visibleW: Float, hx: Float) {
        bossFlash = max(0f, bossFlash - dt)
        val cycle = BIOME_COUNT * RUN_LOC_LEN
        val lap = (dist / cycle).toInt()
        // старт: конец локации «Вулкан» (последняя в круге)
        if (bossState == 0 && lap != bossLap && dist - lap * cycle >= cycle - RUN_LOC_LEN * 0.30f) {
            bossLap = lap
            bossState = 1
            bossMax = if (toddler) 8 else 14
            bossHp = bossMax
            bossAtkT = 2.2f
            nextX = 1e9f
            foes.clear()
            obs.removeAll { it.x > dist + visibleW * 0.6f }
            onEvent("bossStart")
        }
        if (bossState == 0) return
        if (bossState == 1) {
            bossAtkT -= dt
            if (bossAtkT <= 0f) {
                bossAtkT = (if (toddler) 3.2f else 2.0f) + Random.nextFloat() * 0.8f
                val high = !toddler && Random.nextBoolean()
                orbs.add(RunOrb(dist + bossSx() - 40f, if (high) 150f else 26f))
                onEvent("bossAtk")
            }
            for (p in fires) {
                if (abs(p.x - (dist + bossSx())) < 90f && abs(p.y - bossY()) < 120f) {
                    p.x = 1e9f
                    bossHp--
                    bossFlash = 0.15f
                    fx.add(RunFx(dist + bossSx() - 40f, p.y, 6))
                    if (bossHp <= 0) {
                        bossState = 2; bossT = 0f; orbs.clear()
                    } else onEvent("bossHit")
                    break
                }
            }
        } else {
            bossT += dt
            if (((bossT * 6f).toInt()) != (((bossT - dt) * 6f).toInt())) {
                fx.add(RunFx(dist + bossSx() + (Random.nextFloat() - 0.5f) * 140f, bossY() + (Random.nextFloat() - 0.5f) * 140f, 16))
            }
            if (bossT >= 2f) {
                bossState = 0
                kills += 5
                coins += 25
                for (k in 0..11) items.add(RunCoin(dist + visibleW * 0.55f + k * 52f, 40f + (k % 3) * 40f))
                nextX = dist + visibleW + 500f
                onEvent("bossWin")
            }
        }
        for (o in orbs) {
            o.x -= 360f * dt
            if (invuln <= 0f && abs(o.x - hx) < 36f && o.y + 26f > hy && o.y - 26f < hy + 100f) { hurt(); o.x = -1e9f }
        }
        for (p in fires) for (o in orbs) if (abs(p.x - o.x) < 34f && abs(p.y - o.y) < 40f) { p.x = 1e9f; fx.add(RunFx(o.x, o.y, 6)); o.x = -1e9f }
        orbs.removeAll { it.x < dist - 200f }
    }

    private fun killFoe(f: RunFoe) {
        f.dead = true
        kills++
        coins += 2
        for (k in 0..2) items.add(RunCoin(f.x - 40f + k * 40f, 50f + f.y + (if (k == 1) 30f else 0f)))
        fx.add(RunFx(f.x, f.y + f.h / 2f, 16))
        onEvent("foeDie")
    }

    private fun updateFoes(dt: Float, visibleW: Float, hx: Float) {
        hxw = hx
        fireCd = max(0f, fireCd - dt)
        muzzle = max(0f, muzzle - dt)
        if (toddler && fireCd <= 0f && (bossState == 1 || foes.any { !it.dead && it.x > hx + 100f && it.x < hx + visibleW * 0.7f })) fire()
        val walk = if (toddler) 30f else 60f
        for (f in foes) {
            if (f.dead) continue
            f.flash = max(0f, f.flash - dt)
            f.x -= walk * dt
            if (f.kind == 1) {
                f.hopT -= dt
                if (f.hopT <= 0f && f.y <= 0f) { f.vy = 560f; f.hopT = 1.1f + Random.nextFloat() * 0.5f }
                if (f.y > 0f || f.vy > 0f) {
                    f.y += f.vy * dt; f.vy -= 1900f * dt
                    if (f.y <= 0f) { f.y = 0f; f.vy = 0f }
                }
            }
            val near = abs(f.x - hx) < f.w / 2f + 14f
            if (invuln <= 0f && near && hy < f.y + f.h - 16f && hy + 100f > f.y) { hurt(); break }
        }
        for (p in fires) {
            p.x += 780f * dt
            p.t += dt
            for (f in foes) {
                if (f.dead) continue
                if (abs(p.x - f.x) < f.w / 2f + 14f && p.y > f.y - 12f && p.y < f.y + f.h + 12f) {
                    p.x = 1e9f
                    f.hp--
                    f.flash = 0.18f
                    fx.add(RunFx(f.x, p.y, 6))
                    if (f.hp <= 0) killFoe(f) else onEvent("foeHit")
                    break
                }
            }
        }
        fires.removeAll { it.x > dist + visibleW + 120f }
        foes.removeAll { it.dead || it.x < dist - 300f }
    }

    fun step(dt: Float, visibleW: Float, heroX: Float) {
        if (over) return
        time += dt
        slow = max(0f, slow - dt)
        invuln = max(0f, invuln - dt)
        speed = baseSpeed() * (if (slow > 0f) 0.55f else 1f)
        if (bossState != 0) {
            val capD = ((dist / (BIOME_COUNT * RUN_LOC_LEN)).toInt() + 1) * BIOME_COUNT * RUN_LOC_LEN - 2f
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

private enum class RunScreen { MENU, PLAY, OVER }

private fun runPrefs(ctx: Context) = ctx.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE)

@Composable
private fun RunChip(text: String) {
    Box(
        Modifier
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
}

@Composable
fun MonsterRunnerScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
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
    val heroSpec = houseList.getOrNull(heroIdx) ?: MonSpec()
    val heroImgs = rememberMonImgs(heroSpec)
    val heroAnim = remember { MonAnim().also { it.danceUntil = 1e9f } }
    val boomFrames = remember { loadBoomFrames(context) }
    val booms = remember { ArrayList<FloatArray>() }
    val genieFrames = remember {
        listOf(R.drawable.genie_f0, R.drawable.genie_f1, R.drawable.genie_f2, R.drawable.genie_f3, R.drawable.genie_f4, R.drawable.genie_f5, R.drawable.genie_f6).mapNotNull { id ->
            try { android.graphics.BitmapFactory.decodeResource(context.resources, id)?.asImageBitmap() } catch (e: Throwable) { null }
        }
    }
    var hudBoss by remember { mutableFloatStateOf(-1f) }
    val biomeGfx = rememberZBiomeGfx()

    var screen by remember { mutableStateOf(RunScreen.MENU) }
    var startLoc by remember { mutableIntStateOf(0) }
    val world = remember(toddler) { RunWorld(toddler) }
    var tick by remember { mutableIntStateOf(0) }
    var clock by remember { mutableFloatStateOf(0f) }
    var banner by remember { mutableStateOf("") }
    var best by remember { mutableIntStateOf(try { runPrefs(context).getInt("mr_best", 0) } catch (e: Exception) { 0 }) }
    var newBest by remember { mutableStateOf(false) }
    var resultDist by remember { mutableIntStateOf(0) }
    var resultCoins by remember { mutableIntStateOf(0) }
    var hudCoins by remember { mutableIntStateOf(0) }
    var hudLives by remember { mutableIntStateOf(3) }
    var hudLoc by remember { mutableIntStateOf(0) }
    var hudDist by remember { mutableIntStateOf(0) }
    var hudKills by remember { mutableIntStateOf(0) }
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

    fun confetti(x: Float, y: Float, n: Int) {
        repeat(n) {
            val a = Random.nextFloat() * 6.28f
            val s = (120f + Random.nextFloat() * 320f) * density
            sparks.add(floatArrayOf(x, y, cos(a) * s, sin(a) * s - 180f * density, 1f, Random.nextInt(5).toFloat()))
        }
    }

    LaunchedEffect(Unit) {
        soundManager.preloadRaw(listOf("zb_jump", "zb_jump2", "zb_land", "zb_coin", "zb_ouch", "zb_hurt", "zb_rocket", "zb_down", "sw_boom", "sw_dash", "sw_rage"))
    }
    world.onEvent = { ev ->
        when (ev) {
            "jump" -> soundManager.play("zb_jump", volume = 0.7f)
            "jump2" -> soundManager.play("zb_jump2", volume = 0.7f)
            "land" -> soundManager.play("zb_land", volume = 0.5f)
            "coin" -> soundManager.play("zb_coin", volume = 0.7f, rate = 0.9f + (world.coins % 8) * 0.05f)
            "hit" -> { soundManager.play("zb_ouch", volume = 0.9f); soundPlayer.vibrateSquishDeform() }
            "loc" -> {
                soundPlayer.playFanfare()
                banner = BIOME_NAMES[world.loc] + "!"
                confetti(sizeW / 2f, sizeH * 0.35f, 40)
                lottieTick++
            }
            "bossStart" -> {
                soundManager.play("sw_rage", volume = 1f, rate = 0.8f)
                banner = "⚠️ ДЖИНН-БОСС!"
            }
            "bossAtk" -> soundManager.play("sw_warn", volume = 0.7f, rate = 0.9f)
            "bossHit" -> soundManager.play("zb_hurt", volume = 0.7f, rate = 0.7f)
            "bossWin" -> {
                soundPlayer.playFanfare()
                soundManager.play("sw_boom", volume = 1f, rate = 0.7f)
                banner = "ПОБЕДА!"
                confetti(sizeW / 2f, sizeH * 0.35f, 60)
                lottieTick++
            }
            "fire" -> soundManager.play("zb_rocket", volume = 0.6f, rate = 1.25f)
            "foeHit" -> soundManager.play("zb_hurt", volume = 0.8f, rate = 1.2f)
            "foeDie" -> {
                soundManager.play("sw_boom", volume = 0.55f, rate = 1.3f)
                soundManager.play("zb_down", volume = 0.5f, rate = 1.5f)
                soundManager.play("zb_coin", volume = 0.6f, rate = 1.3f)
            }
            "over" -> {
                bank()
                resultDist = (world.dist / 10f).toInt()
                resultCoins = world.coins
                newBest = resultDist > best
                if (newBest) {
                    best = resultDist
                    try { runPrefs(context).edit().putInt("mr_best", best).apply() } catch (e: Exception) { }
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
        screen = RunScreen.PLAY
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
                    for (f in world.foes) {
                        if (f.dead || f.x > camX + wpx / z + 100f || f.x < camX - 100f) continue
                        val bob = sin(clock * 9f + f.x * 0.05f) * 3f
                        val base = gy - f.y
                        val col = when (f.kind) { 0 -> Color(0xFF7CB342); 1 -> Color(0xFFAB47BC); else -> Color(0xFFE53935) }
                        val body = if (f.flash > 0f) Color.White else col
                        drawOval(Color.Black.copy(alpha = 0.22f), Offset(f.x - f.w * 0.45f, gy - 6f), Size(f.w * 0.9f, 12f))
                        // ножки
                        val st = sin(clock * 12f + f.x) * 5f
                        drawOval(Color(0xFF37474F), Offset(f.x - f.w * 0.32f + st, base - 10f), Size(18f, 12f))
                        drawOval(Color(0xFF37474F), Offset(f.x + f.w * 0.32f - 18f - st, base - 10f), Size(18f, 12f))
                        drawOval(body, Offset(f.x - f.w / 2f, base - f.h + bob), Size(f.w, f.h - 4f - bob))
                        if (f.kind == 2) {
                            drawCircle(Color(0xFFFFF59D), 7f, Offset(f.x - f.w * 0.3f, base - f.h + 6f + bob))
                            drawCircle(Color(0xFFFFF59D), 7f, Offset(f.x + f.w * 0.3f, base - f.h + 6f + bob))
                        }
                        if (f.kind == 1) drawCircle(Color(0xFFCE93D8), 8f, Offset(f.x, base - f.h + 2f + bob))
                        val ey = base - f.h * 0.62f + bob
                        val ex = f.w * 0.19f
                        drawCircle(Color.White, 9f, Offset(f.x - ex, ey))
                        drawCircle(Color.White, 9f, Offset(f.x + ex, ey))
                        drawCircle(Color(0xFF212121), 4.5f, Offset(f.x - ex - 2f, ey + 1f))
                        drawCircle(Color(0xFF212121), 4.5f, Offset(f.x + ex - 2f, ey + 1f))
                        drawRoundRect(Color(0xFF3E2723), Offset(f.x - f.w * 0.2f, ey + 15f), Size(f.w * 0.4f, 8f), androidx.compose.ui.geometry.CornerRadius(4f, 4f))
                        drawRect(Color.White, Offset(f.x - 5f, ey + 15f), Size(4f, 4f))
                        drawRect(Color.White, Offset(f.x + 4f, ey + 15f), Size(4f, 4f))
                        if (f.hp > 1) {
                            drawRoundRect(Color.Black.copy(alpha = 0.4f), Offset(f.x - 20f, base - f.h - 14f), Size(40f, 7f), androidx.compose.ui.geometry.CornerRadius(3f, 3f))
                            drawRoundRect(Color(0xFFFF7043), Offset(f.x - 20f, base - f.h - 14f), Size(40f * f.hp / 2f, 7f), androidx.compose.ui.geometry.CornerRadius(3f, 3f))
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
            val pal = listOf(Color(0xFFFFD54F), Color(0xFFFF8A65), Color(0xFF4FC3F7), Color(0xFFAED581), Color(0xFFF06292))
            if (running && world.bossState != 0 && genieFrames.isNotEmpty()) {
                val dying = world.bossState == 2
                val shrink = if (dying) (1f - world.bossT / 2f).coerceIn(0.05f, 1f) else 1f
                val bxPx = (world.bossSx()) * z
                val byPx = (gy - world.bossY()) * z
                val h = 300f * z * shrink
                val img = genieFrames[((clock * 9f).toInt()) % genieFrames.size]
                val k2 = h / img.height
                // аура
                drawCircle(Color(0xFFE040FB).copy(alpha = 0.18f + 0.08f * sin(clock * 6f)), h * 0.55f, Offset(bxPx, byPx))
                val tint = if (world.bossFlash > 0f) androidx.compose.ui.graphics.ColorFilter.tint(Color(0xAAFFFFFF), androidx.compose.ui.graphics.BlendMode.SrcAtop) else null
                withTransform({
                    rotate(if (dying) world.bossT * 240f else 0f, Offset(bxPx, byPx))
                    translate(bxPx - img.width * k2 / 2f, byPx - h / 2f); scale(k2, k2, Offset.Zero)
                }) { drawImage(img, Offset.Zero, colorFilter = tint) }
                for (o in world.orbs) {
                    val ox = (o.x - world.dist) * z
                    val oy = (gy - o.y) * z
                    drawCircle(Color(0xFFE040FB).copy(alpha = 0.35f), 42f * z, Offset(ox, oy))
                    drawCircle(Color(0xFF7C4DFF), 26f * z, Offset(ox, oy))
                    drawCircle(Color(0xFF80DEEA), 15f * z, Offset(ox + sin(clock * 12f) * 3f * z, oy))
                    drawCircle(Color.White.copy(alpha = 0.8f), 6f * z, Offset(ox - 8f * z, oy - 8f * z))
                }
            }
            booms.removeAll { clock - it[2] >= BOOM_TIME }
            for (b in booms) drawBoom(boomFrames, b[0], b[1], 150f * z, clock - b[2])
            for (s in sparks) drawCircle(pal[s[5].toInt() % 5].copy(alpha = s[4].coerceIn(0f, 1f)), 4f * z, Offset(s[0], s[1]))
        }

        if (screen == RunScreen.PLAY) {
            Row(
                Modifier.statusBarsPadding().padding(top = 8.dp, start = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RunChip("🪙 $hudCoins")
                RunChip("📏 $hudDist м")
                RunChip("👾 $hudKills")
                if (!toddler) RunChip("❤️".repeat(max(0, hudLives)))
                RunChip(BIOME_NAMES[hudLoc])
            }
            Box(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 20.dp)
                    .size(96.dp)
                    .background(Color(0xFFFF6F00).copy(alpha = 0.85f), androidx.compose.foundation.shape.CircleShape)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val d = awaitFirstDown()
                            d.consume()
                            if (screen == RunScreen.PLAY) world.fire()
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Text("🔥", fontSize = 44.sp) }
            if (hudBoss >= 0f) {
                Row(
                    Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 52.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🧞", fontSize = 22.sp)
                    Box(
                        Modifier.padding(start = 6.dp).size(width = 170.dp, height = 14.dp)
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
                    ) {
                        Box(Modifier.size(width = (170f * hudBoss.coerceIn(0f, 1f)).dp, height = 14.dp).background(Color(0xFFE040FB), RoundedCornerShape(7.dp)))
                    }
                }
            }
            if (banner.isNotEmpty()) {
                Text(
                    banner, fontSize = 38.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFEB3B),
                    modifier = Modifier.align(Alignment.Center).padding(bottom = 120.dp)
                )
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
                        if (toddler) "Тапай — монстрик прыгает и сам плюётся огнём!\nЖги чудиков, собирай монетки." else "Тап — прыжок (в воздухе — двойной).\n🔥 — огненный шар из рук: жги чудиков!\nВ конце Вулкана ждёт джинн-босс!",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6A1B4D), textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
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
                KitLongButton(
                    R.drawable.ui_btn_long_blue,
                    if (houseList.isEmpty()) "Нужен монстрик 👾" else "Другой монстрик (${heroIdx + 1}/${houseList.size})",
                    230.dp, 12.sp
                ) {
                    if (houseList.isNotEmpty()) {
                        heroIdx = (heroIdx + 1) % houseList.size
                        heroAnim.squash = 0.5f
                        soundPlayer.playPop(1.3f)
                        try { runPrefs(context).edit().putInt("mr_hero", heroIdx).apply() } catch (e: Exception) { }
                    }
                }
                Spacer(Modifier.height(6.dp))
                KitLongButton(R.drawable.ui_btn_long_green, "БЕЖАТЬ ▶", 210.dp, 22.sp) { begin() }
                RunChip("🪙 в кошельке: $walletNow")
                if (best > 0 && !toddler) {
                    Spacer(Modifier.height(4.dp))
                    RunChip("🏆 рекорд: $best м")
                }
            }
        }

        if (screen == RunScreen.OVER) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                KitWindow(width = 330.dp, title = if (newBest) "Рекорд!" else "Конец забега") {
                    Text("📏 $resultDist м   🪙 $resultCoins", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF6A1B4D))
                    Spacer(Modifier.height(8.dp))
                    KitLongButton(R.drawable.ui_btn_long_green, "Ещё раз ↻", 170.dp, 15.sp) { begin() }
                    KitLongButton(R.drawable.ui_btn_long_blue, "В меню", 170.dp, 15.sp) { screen = RunScreen.MENU }
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
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.033f)
                last = now
                clock += dt
                if (screen == RunScreen.PLAY && sizeW > 0f) {
                    world.step(dt, sizeW / density, sizeW / density * 0.24f)
                    hudCoins = world.coins; hudLives = world.lives; hudLoc = world.loc; hudDist = (world.dist / 10f).toInt()
                    hudKills = world.kills
                    hudBoss = if (world.bossState == 1) world.bossHp.toFloat() / world.bossMax else -1f
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
