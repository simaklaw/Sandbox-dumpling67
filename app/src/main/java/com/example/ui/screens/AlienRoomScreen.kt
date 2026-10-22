package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.R
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.LocalAgeProfile
import com.example.shared.SoundManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * «Комната Зелика» — космический друг.
 *
 * Зелик (зелёный пришелец) живёт на маленькой планете. Нажми, куда ему идти — пойдёт
 * шагом, а если далеко — побежит. Рядом лежат энергетические кристаллы: собери их и
 * заправь его корабль. Корабль полон — взлетает с ракетным огоньком, а Зелик машет и
 * прыгает от радости; потом прилетает новый корабль и начинается следующий полёт.
 * Нажми на самого Зелика — подпрыгнет, быстро нажми несколько раз — захихикает.
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

private class Crystal(var x: Float, val phase: Float)

private class AlienSpark(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    val color: Color, var life: Float, val size: Float
)

private class AlienWorld(
    private val dp: Float,
    val goal: Int,
    private val crystalsAtOnce: Int
) {
    var w = 0f
    var h = 0f
    var time = 0f
    var animClock = 0f

    var alienX = 0f
    var targetX = 0f
    var facing = 1f
    var moving = false
    var running = false
    var jumpT = -1f
    var giggleT = 0f
    private var combo = 0
    private var lastTapTime = -10f

    var fuel = 0
    var round = 0
    /** 0 — корабль стоит, 1 — взлетает, 2 — прилетает новый. */
    var shipPhase = 0
    var shipT = 0f
    private var celebrateJumpTimer = 0f

    val crystals = ArrayList<Crystal>()
    val sparks = ArrayList<AlienSpark>()

    var onCollect: () -> Unit = {}
    var onJump: () -> Unit = {}
    var onGiggle: () -> Unit = {}
    var onLaunch: () -> Unit = {}
    var onRound: () -> Unit = {}

    val groundY get() = h * 0.80f
    val spriteH get() = (h * 0.34f).coerceIn(150f * dp, 300f * dp)
    val spriteW get() = spriteH * 250f / 340f
    val shipX get() = w * 0.86f

    fun init(width: Float, height: Float) {
        w = width
        h = height
        alienX = w * 0.3f
        targetX = alienX
        crystals.clear()
        refillCrystals()
    }

    private fun refillCrystals() {
        var guard = 0
        while (crystals.size < crystalsAtOnce && guard++ < 60) {
            val minX = spriteW * 0.6f
            val maxX = shipX - 90f * dp
            val x = minX + Random.nextFloat() * (maxX - minX).coerceAtLeast(1f)
            if (abs(x - alienX) < spriteW * 0.9f) continue
            if (crystals.any { abs(it.x - x) < 80f * dp }) continue
            crystals.add(Crystal(x, Random.nextFloat() * 6.28f))
        }
    }

    fun spark(x: Float, y: Float, color: Color, n: Int) {
        repeat(n) {
            val a = Random.nextFloat() * 6.28f
            val s = (Random.nextFloat() * 220f + 70f) * dp
            sparks.add(
                AlienSpark(x, y, cos(a) * s, sin(a) * s - 120f * dp,
                    if (Random.nextBoolean()) color else Color.White, 1f,
                    (Random.nextFloat() * 3.5f + 2.5f) * dp)
            )
        }
    }

    private fun startJump() {
        if (jumpT < 0f) {
            jumpT = 0f
            onJump()
        }
    }

    fun jumpOffset(): Float =
        if (jumpT < 0f) 0f else sin(PI.toFloat() * jumpT) * spriteH * 0.35f

    fun tap(x: Float, y: Float) {
        val left = alienX - spriteW / 2f
        val top = groundY - spriteH - jumpOffset()
        val onAlien = x in left..(left + spriteW) && y in top..(top + spriteH)
        if (onAlien) {
            if (time - lastTapTime < 1.2f) combo++ else combo = 1
            lastTapTime = time
            if (combo >= 3) {
                combo = 0
                giggleT = 1.4f
                onGiggle()
            } else {
                startJump()
            }
        } else {
            targetX = x.coerceIn(spriteW * 0.35f, w - spriteW * 0.35f)
        }
    }

    fun step(dtRaw: Float) {
        if (w <= 0f) return
        val dt = dtRaw.coerceIn(0.001f, 0.05f)
        time += dt
        animClock += dt

        if (giggleT > 0f) {
            giggleT -= dt
            moving = false
        } else {
            val dist = targetX - alienX
            if (abs(dist) > 5f * dp) {
                val dir = if (dist > 0f) 1f else -1f
                facing = dir
                running = abs(dist) > w * 0.28f
                val speed = (if (running) 230f else 95f) * dp
                val stepLen = speed * dt
                alienX += if (stepLen >= abs(dist)) dist else dir * stepLen
                moving = true
            } else {
                moving = false
                running = false
            }
        }

        if (jumpT >= 0f) {
            jumpT += dt / 0.75f
            if (jumpT >= 1f) jumpT = -1f
        }

        // Сбор кристаллов.
        if (shipPhase == 0 && giggleT <= 0f) {
            val it = crystals.iterator()
            var collected = false
            while (it.hasNext()) {
                val c = it.next()
                if (abs(c.x - alienX) < spriteW * 0.4f) {
                    it.remove()
                    fuel++
                    collected = true
                    spark(c.x, groundY - 30f * dp, CRYSTAL_COLORS[round % CRYSTAL_COLORS.size], 16)
                }
            }
            if (collected) {
                onCollect()
                startJump()
                if (fuel >= goal) {
                    shipPhase = 1
                    shipT = 0f
                    crystals.clear()
                    onLaunch()
                } else {
                    refillCrystals()
                }
            }
        }

        // Корабль.
        when (shipPhase) {
            1 -> {
                shipT += dt
                celebrateJumpTimer -= dt
                if (celebrateJumpTimer <= 0f) {
                    celebrateJumpTimer = 0.9f
                    startJump()
                }
                if (shipT > 2.8f) {
                    shipPhase = 2
                    shipT = 0f
                    fuel = 0
                    round++
                    onRound()
                }
            }
            2 -> {
                shipT += dt
                if (shipT > 1.2f) {
                    shipPhase = 0
                    shipT = 0f
                    refillCrystals()
                }
            }
        }

        val si = sparks.iterator()
        while (si.hasNext()) {
            val s = si.next()
            s.vy += 480f * dp * dt
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.life -= dt * 1.0f
            if (s.life <= 0f || s.y > h + 40f) si.remove()
        }
    }

    /** Подъём корабля над землёй (в пикселях). */
    fun shipLift(): Float = if (shipPhase == 1) shipT * shipT * h * 0.28f else 0f
}

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

    val world = remember(ageProfile.isToddler2) {
        AlienWorld(dp = dp, goal = if (ageProfile.isToddler2) 3 else 5, crystalsAtOnce = if (ageProfile.isToddler2) 2 else 3)
    }
    var fuelShown by remember { mutableIntStateOf(0) }
    var rounds by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var tappedOnce by remember { mutableIntStateOf(0) }

    world.onCollect = {
        fuelShown = world.fuel
        soundManager.play("star_collect")
        soundPlayer.vibrateDragTick()
    }
    world.onJump = { soundManager.play("boing", volume = 0.7f) }
    world.onGiggle = {
        soundManager.play("giggle")
        soundPlayer.vibrateSquishDeform()
    }
    world.onLaunch = {
        fuelShown = world.goal
        soundManager.play("whoosh")
        soundPlayer.playFanfare()
        soundPlayer.vibrateMedium()
    }
    world.onRound = {
        fuelShown = 0
        rounds = world.round
    }

    // Статичное оформление неба/земли (создаётся один раз).
    val stars = remember {
        val r = Random(7)
        List(70) { Triple(r.nextFloat(), r.nextFloat() * 0.62f, r.nextFloat() * 6.28f) }
    }
    val flora = remember {
        val r = Random(21)
        List(9) { Triple(r.nextFloat(), 16f + r.nextFloat() * 30f, r.nextInt(3)) }
    }
    val craters = remember {
        val r = Random(33)
        List(6) { Pair(r.nextFloat(), r.nextFloat()) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("alien_room_root")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        tappedOnce++
                        world.tap(pos.x, pos.y)
                    }
                }
        ) {
            if (world.w != size.width || world.h != size.height) world.init(size.width, size.height)
            @Suppress("UNUSED_VARIABLE") val redraw = tick
            val w = size.width
            val h = size.height
            val t = world.time
            val groundY = world.groundY

            // Небо.
            drawRect(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A1446), Color(0xFF3B2A7A), Color(0xFF6A4BA8), Color(0xFFB07CC6)),
                    startY = 0f, endY = groundY
                ),
                size = Size(w, groundY)
            )
            for ((fx, fy, ph) in stars) {
                val a = 0.45f + 0.45f * sin(t * 1.6f + ph)
                drawCircle(Color.White.copy(alpha = a), (1.2f + (ph % 1.6f)) * dp, Offset(fx * w, fy * h))
            }
            // Планета с кольцом.
            val pc = Offset(w * 0.24f, h * 0.17f)
            val pr = minOf(w, h) * 0.085f
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFFFB74D), Color(0xFFE64A19)), center = Offset(pc.x - pr * 0.3f, pc.y - pr * 0.3f), radius = pr * 1.4f),
                pr, pc
            )
            drawOval(Color(0xFFFFE0B2).copy(alpha = 0.8f), Offset(pc.x - pr * 1.7f, pc.y - pr * 0.28f), Size(pr * 3.4f, pr * 0.56f), style = Stroke(width = 3f * dp))
            // Луна.
            drawCircle(Color(0xFFE8EAF6), pr * 0.45f, Offset(w * 0.72f, h * 0.12f))
            drawCircle(Color(0xFFC5CAE9), pr * 0.12f, Offset(w * 0.70f, h * 0.115f))
            drawCircle(Color(0xFFC5CAE9), pr * 0.09f, Offset(w * 0.74f, h * 0.135f))
            // Падающая звезда.
            val sp = (t * 0.22f) % 1f
            if (sp < 0.18f) {
                val k = sp / 0.18f
                val sx = w * (1.0f - k * 0.7f)
                val sy = h * (0.06f + k * 0.25f)
                drawLine(Color.White.copy(alpha = 1f - k), Offset(sx, sy), Offset(sx + 70f * dp, sy - 26f * dp), 3f * dp)
            }

            // Дальние горы.
            val mount = Path().apply {
                moveTo(0f, groundY)
                lineTo(0f, groundY - h * 0.10f)
                lineTo(w * 0.15f, groundY - h * 0.20f)
                lineTo(w * 0.30f, groundY - h * 0.09f)
                lineTo(w * 0.48f, groundY - h * 0.23f)
                lineTo(w * 0.66f, groundY - h * 0.10f)
                lineTo(w * 0.82f, groundY - h * 0.19f)
                lineTo(w, groundY - h * 0.08f)
                lineTo(w, groundY)
                close()
            }
            drawPath(mount, Color(0xFF4A3A8C))

            // Земля.
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFF5FD3B3), Color(0xFF2A9D8F), Color(0xFF1E6F6A)), startY = groundY - 14f * dp, endY = h),
                Offset(0f, groundY - 14f * dp), Size(w, h - groundY + 14f * dp)
            )
            for ((cx, cy) in craters) {
                val ox = cx * w
                val oy = groundY + 18f * dp + cy * (h - groundY - 40f * dp).coerceAtLeast(10f)
                drawOval(Color(0xFF1E6F6A).copy(alpha = 0.55f), Offset(ox - 34f * dp, oy), Size(68f * dp, 16f * dp))
            }
            // Инопланетные растения.
            for ((fx, fh, kind) in flora) {
                val bx = fx * w
                val by = groundY - 6f * dp
                val sway = sin(t * 1.4f + fx * 9f) * 3f * dp
                val col = when (kind) { 0 -> Color(0xFFFF8A80); 1 -> Color(0xFFFFEE58); else -> Color(0xFF80D8FF) }
                drawLine(Color(0xFF1B5E20), Offset(bx, by), Offset(bx + sway, by - fh * dp), 3f * dp)
                drawCircle(col, 7f * dp, Offset(bx + sway, by - fh * dp))
                drawCircle(Color.White.copy(alpha = 0.6f), 2.5f * dp, Offset(bx + sway - 2f * dp, by - fh * dp - 2f * dp))
            }

            // Корабль.
            val shipAlpha = if (world.shipPhase == 2) (world.shipT / 1.2f).coerceIn(0f, 1f) else 1f
            val lift = world.shipLift()
            val sx = world.shipX
            val shipBase = groundY - 2f * dp - lift
            val shipW = 54f * dp
            val shipH = 128f * dp
            val shake = if (world.shipPhase == 0 && world.fuel >= world.goal - 1 && world.goal > 1) sin(t * 30f) * 1.5f * dp else 0f
            val shipColor = CRYSTAL_COLORS[world.round % CRYSTAL_COLORS.size]
            withTransform({ translate(shake, 0f) }) {
                if (world.shipPhase == 1 && world.shipT > 0.15f) {
                    val flame = Path().apply {
                        moveTo(sx - 14f * dp, shipBase)
                        lineTo(sx, shipBase + (46f + 16f * sin(t * 40f)) * dp)
                        lineTo(sx + 14f * dp, shipBase)
                        close()
                    }
                    drawPath(flame, Color(0xFFFFB300))
                    val flame2 = Path().apply {
                        moveTo(sx - 7f * dp, shipBase)
                        lineTo(sx, shipBase + (28f + 10f * sin(t * 47f)) * dp)
                        lineTo(sx + 7f * dp, shipBase)
                        close()
                    }
                    drawPath(flame2, Color(0xFFFFF59D))
                }
                // Плавники.
                val finL = Path().apply {
                    moveTo(sx - shipW / 2f, shipBase - 34f * dp); lineTo(sx - shipW / 2f - 22f * dp, shipBase + 4f * dp); lineTo(sx - shipW / 2f, shipBase - 4f * dp); close()
                }
                val finR = Path().apply {
                    moveTo(sx + shipW / 2f, shipBase - 34f * dp); lineTo(sx + shipW / 2f + 22f * dp, shipBase + 4f * dp); lineTo(sx + shipW / 2f, shipBase - 4f * dp); close()
                }
                drawPath(finL, shipColor.copy(alpha = shipAlpha))
                drawPath(finR, shipColor.copy(alpha = shipAlpha))
                // Корпус.
                drawRoundRect(
                    Brush.horizontalGradient(
                        listOf(Color(0xFFECEFF1), Color.White, Color(0xFFB0BEC5)),
                        startX = sx - shipW / 2f, endX = sx + shipW / 2f
                    ),
                    Offset(sx - shipW / 2f, shipBase - shipH), Size(shipW, shipH),
                    androidx.compose.ui.geometry.CornerRadius(22f * dp), alpha = shipAlpha
                )
                // Нос.
                val nose = Path().apply {
                    moveTo(sx - shipW / 2f, shipBase - shipH + 18f * dp)
                    quadraticTo(sx, shipBase - shipH - 46f * dp, sx + shipW / 2f, shipBase - shipH + 18f * dp)
                    close()
                }
                drawPath(nose, shipColor.copy(alpha = shipAlpha))
                // Иллюминатор.
                drawCircle(Color(0xFF263238).copy(alpha = shipAlpha), 13f * dp, Offset(sx, shipBase - shipH * 0.62f))
                drawCircle(Color(0xFF80DEEA).copy(alpha = shipAlpha), 10f * dp, Offset(sx, shipBase - shipH * 0.62f))
                // Полоса уровня топлива на корпусе.
                val ratio = world.fuel.toFloat() / world.goal
                drawRoundRect(Color(0xFF455A64).copy(alpha = 0.6f * shipAlpha), Offset(sx - 11f * dp, shipBase - shipH * 0.40f), Size(22f * dp, shipH * 0.30f), androidx.compose.ui.geometry.CornerRadius(6f * dp))
                if (ratio > 0f) {
                    val fh = shipH * 0.30f * ratio
                    drawRoundRect(shipColor.copy(alpha = shipAlpha), Offset(sx - 11f * dp, shipBase - shipH * 0.10f - fh), Size(22f * dp, fh), androidx.compose.ui.geometry.CornerRadius(6f * dp))
                }
            }

            // Кристаллы.
            for (c in world.crystals) {
                val col = CRYSTAL_COLORS[world.round % CRYSTAL_COLORS.size]
                val bob = sin(t * 2.4f + c.phase) * 4f * dp
                val cy = groundY - 34f * dp + bob
                drawCircle(col.copy(alpha = 0.22f), 30f * dp, Offset(c.x, cy))
                val d = Path().apply {
                    moveTo(c.x, cy - 24f * dp); lineTo(c.x + 16f * dp, cy); lineTo(c.x, cy + 24f * dp); lineTo(c.x - 16f * dp, cy); close()
                }
                drawPath(d, col)
                drawPath(d, Color.White.copy(alpha = 0.8f), style = Stroke(width = 2f * dp))
                drawLine(Color.White.copy(alpha = 0.7f), Offset(c.x - 4f * dp, cy - 10f * dp), Offset(c.x - 9f * dp, cy - 1f * dp), 3f * dp)
            }

            // Тень Зелика.
            val sw = world.spriteW
            val sh = world.spriteH
            val air = world.jumpOffset() / (sh * 0.35f)
            drawOval(
                Color.Black.copy(alpha = 0.22f - 0.1f * air),
                Offset(world.alienX - sw * (0.32f - 0.08f * air), groundY - 6f * dp),
                Size(sw * (0.64f - 0.16f * air), 14f * dp)
            )

            // Сам Зелик.
            val anim = when {
                world.giggleT > 0f -> frames.hurt[(world.animClock * 8f).toInt() % 4]
                world.jumpT >= 0f -> {
                    val j = world.jumpT
                    frames.jump[if (j < 0.15f) 0 else if (j < 0.42f) 1 else if (j < 0.68f) 2 else 3]
                }
                world.moving && world.running -> frames.run[(world.animClock * 13f).toInt() % 6]
                world.moving -> frames.walk[(world.animClock * 9f).toInt() % 6]
                else -> frames.idle[if ((world.time % 3.4f) > 3.2f) 1 else 0]
            }
            val ax = world.alienX
            val ay = groundY - sh - world.jumpOffset()
            val shiver = if (world.giggleT > 0f) sin(t * 45f) * 3f * dp else 0f
            withTransform({
                if (world.facing < 0f) scale(-1f, 1f, Offset(ax, 0f))
            }) {
                drawImage(
                    anim,
                    dstOffset = IntOffset((ax - sw / 2f + shiver).toInt(), ay.toInt()),
                    dstSize = IntSize(sw.toInt(), sh.toInt())
                )
            }
            // Сердечки при хихиканье.
            if (world.giggleT > 0f) {
                for (i in 0 until 3) {
                    val hy = ay - 10f * dp - ((world.animClock * 60f + i * 26f) % 80f) * dp * 0.8f
                    drawCircle(Color(0xFFFF4081).copy(alpha = 0.8f), 6f * dp, Offset(ax + (i - 1) * 28f * dp, hy))
                }
            }

            for (s in world.sparks) {
                drawCircle(s.color.copy(alpha = s.life.coerceIn(0f, 1f)), s.size, Offset(s.x, s.y))
            }
        }

        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(88.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.88f),
                shadowElevation = 4.dp
            ) {
                Text(
                    "Заправь корабль Зелика!  " + "💎".repeat(fuelShown) + "▫️".repeat((world.goal - fuelShown).coerceAtLeast(0)) +
                        if (rounds > 0) "  🚀 $rounds" else "",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3B2A7A)
                )
            }
        }

        if (tappedOnce == 0) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    "Нажми, куда идти Зелику 👆",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3B2A7A)
                )
            }
        }
    }

    LaunchedEffect(world) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) world.step((now - last) / 1_000_000_000f)
                last = now
                tick++
            }
        }
    }
}
