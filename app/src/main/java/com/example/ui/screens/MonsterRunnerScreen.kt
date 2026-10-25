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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
private class RunProj(var x: Float, val y: Float, val fly: Boolean)

private val RUN_BOSSES = listOf(
    MonSpec(body = 2, color = 2, arm = 2, leg = 3, eyes = 3, eyeCount = 1, mouth = 7, nose = 0, detail = 5),
    MonSpec(body = 0, color = 1, arm = 4, leg = 0, eyes = 6, eyeCount = 3, mouth = 9, nose = 2, detail = 4),
    MonSpec(body = 4, color = 3, arm = 1, leg = 4, eyes = 2, eyeCount = 2, mouth = 11, nose = 1, detail = 2)
)

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
    var bossState = 0      // 0 — нет, 1 — бой, 2 — закончен
    var bossSeg = -1
    var bossT = 0f
    var bossHp = 0
    val bossMax = if (toddler) 12 else 24
    var projT = 0f
    var coinT = 0f
    val projs = ArrayList<RunProj>()
    val obs = ArrayList<RunObs>()
    val items = ArrayList<RunCoin>()
    var onEvent: (String) -> Unit = {}

    fun start(startLoc: Int, visibleW: Float) {
        dist = startLoc * RUN_LOC_LEN
        loc = startLoc
        hy = 0f; vy = 0f; airJumps = 0
        lives = 3; coins = 0; invuln = 0f; slow = 0f; over = false; time = 0f
        obs.clear(); items.clear(); projs.clear()
        bossState = 0; bossSeg = -1; bossT = 0f
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
            if (r < 0.62f) {
                obs.add(RunObs(nextX, kind, w, h))
                for (k in 0..4) {
                    val a = k / 4f * PI.toFloat()
                    items.add(RunCoin(nextX + w / 2f - 80f + k * 40f, h + 26f + sin(a) * 70f))
                }
            } else if (r < 0.78f && !toddler) {
                obs.add(RunObs(nextX, kind, w, h))
                obs.add(RunObs(nextX + w + 150f, (kind + 1) % 4, w, h))
                for (k in 0..6) {
                    val a = k / 6f * PI.toFloat()
                    items.add(RunCoin(nextX + w / 2f - 20f + k * 38f, h + 30f + sin(a) * 85f))
                }
                nextX += w + 150f
            } else {
                for (k in 0..5) items.add(RunCoin(nextX + k * 42f, 26f + (if (k % 2 == 0) 0f else 14f)))
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

    private fun updateBoss(dt: Float, visibleW: Float, hx: Float) {
        val seg = (dist / RUN_LOC_LEN).toInt()
        if (bossState != 1 && bossSeg != seg && dist - seg * RUN_LOC_LEN > RUN_LOC_LEN * 0.8f) {
            bossSeg = seg; bossState = 1; bossT = 0f; bossHp = bossMax; projT = 2.2f; coinT = 0.8f
            onEvent("bossStart")
        }
        if (bossState != 1) return
        bossT += dt
        projT -= dt
        coinT -= dt
        if (projT <= 0f) {
            projT = if (toddler) 3.4f else 2.1f
            val fly = !toddler && Random.nextBoolean()
            projs.add(RunProj(dist + visibleW + 80f, if (fly) 95f else 0f, fly))
            // в ярости босс стреляет двойными залпами
            if (!toddler && bossHp <= bossMax / 2) {
                projs.add(RunProj(dist + visibleW + 260f, if (!fly) 95f else 0f, !fly))
                projT *= 0.8f
            }
            onEvent("bossShoot")
        }
        if (coinT <= 0f) {
            coinT = 1.7f
            for (k in 0..5) {
                val a = k / 5f * PI.toFloat()
                items.add(RunCoin(dist + visibleW + 120f + k * 40f, 60f + sin(a) * 90f))
            }
        }
        for (p in projs) {
            p.x -= 170f * dt
            val near = hx + 12f > p.x - 14f && hx - 12f < p.x + 14f
            val hit = if (p.fly) (hy > 15f && hy < 111f) else (hy < 34f)
            if (invuln <= 0f && near && hit) { hurt(); break }
        }
        projs.removeAll { it.x < dist - 300f }
        if (bossT > 20f) {
            bossState = 2; projs.clear(); nextX = dist + visibleW + 300f
            onEvent("bossLeave")
        }
    }

    fun step(dt: Float, visibleW: Float, heroX: Float) {
        if (over) return
        time += dt
        slow = max(0f, slow - dt)
        invuln = max(0f, invuln - dt)
        speed = baseSpeed() * (if (slow > 0f) 0.55f else 1f)
        dist += speed * dt
        if (hy > 0f || vy > 0f) {
            hy += vy * dt
            vy -= RUN_GRAVITY * dt
            if (hy <= 0f) { hy = 0f; vy = 0f; squash = 0.45f; if (wasAir) onEvent("land") }
            wasAir = true
        } else wasAir = false
        squash *= (1f - min(1f, dt * 9f))
        if (bossState != 1) spawn(visibleW)
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
        updateBoss(dt, visibleW, hx)
        val shower = ArrayList<RunCoin>()
        for (c in items) {
            if (c.taken) continue
            if (abs(c.x - hx) < 30f && abs(c.y - (hy + 34f)) < 46f) {
                c.taken = true; coins++; onEvent("coin")
                if (bossState == 1) {
                    bossHp--
                    if (bossHp <= 0) { bossState = 2; projs.clear(); coins += 40; nextX = dist + visibleW + 300f; onEvent("bossDown")
                        for (k in 0..17) {
                            val a = k / 17f * PI.toFloat()
                            shower.add(RunCoin(dist + 160f + k * 34f, 40f + sin(a) * 110f))
                        }
                    } else onEvent("bossHit")
                }
            }
        }
        items.addAll(shower)
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
    val biomeGfx = rememberZBiomeGfx()
    var hudLocForBoss by remember { mutableIntStateOf(0) }
    val bossSpec = RUN_BOSSES[hudLocForBoss]
    val bossImgs = rememberMonImgs(bossSpec)

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
    var hudBoss by remember { mutableIntStateOf(0) }
    var hudBossHp by remember { mutableIntStateOf(0) }
    val bossAnim = remember { MonAnim().also { it.danceUntil = 1e9f } }
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
        soundManager.preloadRaw(listOf("zb_jump", "zb_jump2", "zb_land", "zb_coin", "zb_ouch", "zb_roar", "zb_hurt", "zb_rocket", "zb_down", "zb_victory", "zb_coinrain"))
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
            "bossStart" -> { hudLocForBoss = world.loc; soundManager.play("zb_roar", volume = 0.9f); banner = "⚠️ БОСС! Лови монетки!" }
            "bossHit" -> { bossAnim.squash = 0.6f; soundManager.play("zb_hurt", volume = 0.8f) }
            "bossShoot" -> soundManager.play("zb_rocket", volume = 0.6f)
            "bossDown" -> {
                soundManager.play("zb_down", volume = 1f); soundManager.play("zb_victory", volume = 0.9f); soundManager.play("zb_coinrain", volume = 1f); banner = "🎉 Босс побеждён! +40 🪙"
                confetti(sizeW / 2f, sizeH * 0.4f, 60); lottieTick++
            }
            "bossLeave" -> { banner = "Босс убежал!" }
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
                    awaitFirstDown(requireUnconsumed = false)
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
            drawBiomeBackdrop(biomeGfx, bi, camX, 0f, z, gy, 0)
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
                        drawOval(Color(0xFFFFC107), Offset(c.x - 9f * sq - 2f, cy - 11f), Size(18f * sq + 4f, 22f))
                        drawOval(Color(0xFFFFF176), Offset(c.x - 4f * sq, cy - 7f), Size(7f * sq + 1f, 12f))
                    }
                    for (p in world.projs) {
                        if (p.fly) {
                            val cy = gy - p.y
                            drawCircle(Color(0xFFFF7043).copy(alpha = 0.35f), 26f, Offset(p.x + 14f, cy))
                            drawCircle(Color(0xFFFF7043), 16f, Offset(p.x, cy))
                            drawCircle(Color(0xFFFFEB3B), 8f, Offset(p.x - 2f, cy))
                        } else {
                            drawCircle(Color(0xFF607D8B), 16f, Offset(p.x, gy - 16f))
                            drawCircle(Color(0xFF90A4AE), 7f, Offset(p.x - 5f, gy - 21f))
                        }
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
            if (running && world.bossState == 1) {
                val inK = min(1f, world.bossT)
                val bx = wpx * (1.25f - 0.38f * inK)
                bossAnim.squash *= 0.9f
                val lay = monLayout(bossImgs, bx, gy * z + 2f, 230f * z, 270f * z)
                drawMonster(bossImgs, bossSpec, lay, bossAnim, clock * 1.1f, z, -0.7f)
            }
            val pal = listOf(Color(0xFFFFD54F), Color(0xFFFF8A65), Color(0xFF4FC3F7), Color(0xFFAED581), Color(0xFFF06292))
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
                if (!toddler) RunChip("❤️".repeat(max(0, hudLives)))
                RunChip(BIOME_NAMES[hudLoc])
            }
            if (hudBoss == 1) {
                Row(
                    Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 52.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("👹", fontSize = 22.sp)
                    Box(
                        Modifier.padding(start = 6.dp).size(width = 160.dp, height = 14.dp)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
                    ) {
                        Box(
                            Modifier.fillMaxSize().fillMaxWidth(hudBossHp / world.bossMax.toFloat())
                                .background(Color(0xFFE53935), RoundedCornerShape(7.dp))
                        )
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
                        if (toddler) "Тапай — монстрик прыгает!\nСобирай монетки." else "Тап — прыжок, ещё тап в воздухе — двойной.\nПерепрыгивай бочки и ящики!",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6A1B4D), textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (i in 0 until BIOME_COUNT) {
                            KitLongButton(
                                if (i == 0) R.drawable.ui_btn_long_green else if (i == 1) R.drawable.ui_btn_long_blue else R.drawable.ui_btn_long_red,
                                BIOME_NAMES[i].substringAfter(' '), 82.dp, 11.sp, selected = startLoc == i
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
                    hudBoss = world.bossState; hudBossHp = world.bossHp
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
