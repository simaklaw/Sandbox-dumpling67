package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.LocalAgeProfile
import com.example.shared.SoundManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * «Шарики» — игра на физике.
 *
 * Шарики плывут вверх (подъёмная сила + сопротивление воздуха → у каждого своя
 * скорость подъёма), покачиваются от ветра, сталкиваются друг с другом и отскакивают
 * от стен. Тап по шарику нужного цвета — он лопается (конфетти + звёздочка),
 * тап по «не тому» — шарик отскакивает и качается. Собрали все звёздочки — праздник
 * и новый цвет. Для самых маленьких: цель не нужна, лопается любой шарик.
 */

private class BalloonColor(val name: String, val base: Color)

private val BALLOON_COLORS = listOf(
    BalloonColor("красные", Color(0xFFEF5350)),
    BalloonColor("жёлтые", Color(0xFFFFCA28)),
    BalloonColor("синие", Color(0xFF42A5F5)),
    BalloonColor("зелёные", Color(0xFF66BB6A)),
    BalloonColor("розовые", Color(0xFFF06292)),
    BalloonColor("оранжевые", Color(0xFFFFA726)),
    BalloonColor("фиолетовые", Color(0xFFAB47BC))
)

private class Balloon(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val r: Float,
    var colorIdx: Int,
    val phase: Float,
    val terminal: Float
) {
    var alive = true
    var respawnIn = 0f
    var wobble = 0f
}

private class Confetti(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    var life: Float,
    val size: Float
)

private class BalloonWorld(
    private val dp: Float,
    private val count: Int,
    val freeMode: Boolean
) {
    var w = 0f
    var h = 0f
    var time = 0f
    var targetIdx = 0
    val balloons = ArrayList<Balloon>()
    val confetti = ArrayList<Confetti>()

    var onPop: (Boolean) -> Unit = {}
    var onBounce: () -> Unit = {}

    private fun newBalloon(spawnAtBottom: Boolean): Balloon {
        val r = (Random.nextFloat() * 34f + (if (freeMode) 52f else 44f)) * dp
        val x = r + Random.nextFloat() * (w - 2 * r).coerceAtLeast(1f)
        val y = if (spawnAtBottom) h + r * 1.4f else Random.nextFloat() * h
        // Мелкие шарики поднимаются быстрее, крупные — медленнее (сопротивление ~ площади).
        val terminal = (150f - r / dp * 0.9f).coerceIn(60f, 120f) * dp
        return Balloon(
            x = x, y = y, vx = 0f, vy = -terminal * 0.4f, r = r,
            colorIdx = pickColor(), phase = Random.nextFloat() * 6.28f, terminal = terminal
        )
    }

    private fun pickColor(): Int =
        if (!freeMode && Random.nextFloat() < 0.38f) targetIdx else Random.nextInt(BALLOON_COLORS.size)

    fun init(width: Float, height: Float) {
        w = width
        h = height
        balloons.clear()
        repeat(count) { balloons.add(newBalloon(spawnAtBottom = false)) }
        ensureTargets()
    }

    /** Гарантируем минимум 3 шарика нужного цвета на экране. */
    fun ensureTargets() {
        if (freeMode) return
        var have = balloons.count { it.alive && it.colorIdx == targetIdx }
        for (b in balloons) {
            if (have >= 3) break
            if (b.alive && b.colorIdx != targetIdx) {
                b.colorIdx = targetIdx
                b.wobble = 1f
                have++
            }
        }
    }

    fun nextTarget() {
        var n = Random.nextInt(BALLOON_COLORS.size)
        while (n == targetIdx) n = Random.nextInt(BALLOON_COLORS.size)
        targetIdx = n
        ensureTargets()
    }

    fun burst(x: Float, y: Float, color: Color, n: Int) {
        repeat(n) {
            val a = Random.nextFloat() * 6.28f
            val s = (Random.nextFloat() * 260f + 90f) * dp
            confetti.add(
                Confetti(
                    x, y, cos(a) * s, sin(a) * s - 120f * dp,
                    if (Random.nextBoolean()) color else Color.White,
                    1f, (Random.nextFloat() * 4f + 3f) * dp
                )
            )
        }
    }

    fun tap(px: Float, py: Float) {
        // Верхний (последний нарисованный) шарик под пальцем.
        val hit = balloons.lastOrNull {
            if (!it.alive) return@lastOrNull false
            val dx = (px - it.x) / (it.r * 0.95f)
            val dy = (py - it.y) / (it.r * 1.1f)
            dx * dx + dy * dy <= 1.15f
        } ?: return
        val correct = freeMode || hit.colorIdx == targetIdx
        if (correct) {
            hit.alive = false
            hit.respawnIn = 0.6f + Random.nextFloat() * 0.9f
            burst(hit.x, hit.y, BALLOON_COLORS[hit.colorIdx].base, 20)
            onPop(true)
        } else {
            // Не тот цвет: мягко отталкиваем от точки касания, шарик качается.
            var dx = hit.x - px
            var dy = hit.y - py
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            dx /= len
            dy /= len
            hit.vx += dx * 320f * dp
            hit.vy += dy * 320f * dp - 60f * dp
            hit.wobble = 1f
            onBounce()
        }
    }

    fun step(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0.001f, 0.033f)
        time += dt

        for (b in balloons) {
            if (!b.alive) {
                b.respawnIn -= dt
                if (b.respawnIn <= 0f) {
                    val n = newBalloon(spawnAtBottom = true)
                    b.alive = true
                    b.x = n.x
                    b.y = n.y
                    b.vx = 0f
                    b.vy = n.vy
                    b.colorIdx = pickColor()
                    b.wobble = 0f
                }
                continue
            }
            // Подъёмная сила с сопротивлением воздуха: скорость тянется к «терминальной».
            b.vy += (-b.terminal - b.vy) * 1.1f * dt
            // Ветер: плавное покачивание вправо-влево.
            val wind = sin(time * 0.9f + b.phase) * 26f * dp
            b.vx += (wind - b.vx) * 0.9f * dt
            b.x += b.vx * dt
            b.y += b.vy * dt
            b.wobble = (b.wobble - dt * 1.4f).coerceAtLeast(0f)

            // Стены.
            if (b.x < b.r) { b.x = b.r; b.vx = abs(b.vx) * 0.6f }
            if (b.x > w - b.r) { b.x = w - b.r; b.vx = -abs(b.vx) * 0.6f }
            // Улетел за верх — возвращается снизу (не лопнул, просто новый заход).
            if (b.y < -b.r * 2.8f) {
                val n = newBalloon(spawnAtBottom = true)
                b.x = n.x
                b.y = n.y
                b.vx = 0f
                b.vy = n.vy
                b.colorIdx = pickColor()
            }
        }

        // Упругие столкновения шариков (масса ~ площади).
        for (i in 0 until balloons.size) {
            val a = balloons[i]
            if (!a.alive) continue
            for (j in i + 1 until balloons.size) {
                val c = balloons[j]
                if (!c.alive) continue
                val dx = c.x - a.x
                val dy = c.y - a.y
                val minD = (a.r + c.r) * 0.92f
                val d2 = dx * dx + dy * dy
                if (d2 >= minD * minD || d2 < 0.0001f) continue
                val d = sqrt(d2)
                val nx = dx / d
                val ny = dy / d
                val ma = a.r * a.r
                val mc = c.r * c.r
                val overlap = minD - d
                a.x -= nx * overlap * (mc / (ma + mc))
                a.y -= ny * overlap * (mc / (ma + mc))
                c.x += nx * overlap * (ma / (ma + mc))
                c.y += ny * overlap * (ma / (ma + mc))
                val rel = (c.vx - a.vx) * nx + (c.vy - a.vy) * ny
                if (rel < 0f) {
                    val j2 = -(1f + 0.7f) * rel / (1f / ma + 1f / mc)
                    a.vx -= j2 * nx / ma
                    a.vy -= j2 * ny / ma
                    c.vx += j2 * nx / mc
                    c.vy += j2 * ny / mc
                    a.wobble = maxOf(a.wobble, 0.5f)
                    c.wobble = maxOf(c.wobble, 0.5f)
                }
            }
        }

        // Конфетти падает под действием гравитации.
        val it = confetti.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.vy += 520f * dp * dt
            p.vx *= (1f - 0.8f * dt)
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life -= dt * 0.9f
            if (p.life <= 0f || p.y > h + 40f) it.remove()
        }
    }
}

@Composable
fun BalloonPopScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val ageProfile = LocalAgeProfile.current
    val dp = LocalDensity.current.density

    val goal = if (ageProfile.isToddler2) 10 else 6
    val world = remember(ageProfile.isToddler2) {
        BalloonWorld(dp = dp, count = if (ageProfile.isToddler2) 9 else 13, freeMode = ageProfile.isToddler2)
    }
    var score by remember { mutableIntStateOf(0) }
    var targetIdx by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }

    world.onPop = { _ ->
        soundManager.play("balloon_pop")
        soundPlayer.vibrateSquish()
        score += 1
        if (score >= goal) {
            soundPlayer.playFanfare()
            for (k in 0 until 6) {
                world.burst(
                    world.w * (0.1f + 0.16f * k), world.h * 0.35f,
                    BALLOON_COLORS[Random.nextInt(BALLOON_COLORS.size)].base, 24
                )
            }
            score = 0
            if (!world.freeMode) {
                world.nextTarget()
                targetIdx = world.targetIdx
            }
        }
    }
    world.onBounce = {
        soundManager.play("pop", rate = 1.3f)
        soundPlayer.vibrateDragTick()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF81D4FA), Color(0xFFE1F5FE), Color(0xFFFFF9C4)))
            )
            .testTag("balloon_pop_root")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        world.tap(first.position.x, first.position.y)
                        do {
                            val event = awaitPointerEvent()
                            for (c in event.changes) {
                                if (c.changedToDown()) world.tap(c.position.x, c.position.y)
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            if (!ready || world.w != size.width || world.h != size.height) {
                // Первый кадр: инициализация мира под размер экрана.
                if (world.w == 0f) world.init(size.width, size.height)
                else { world.w = size.width; world.h = size.height }
            }
            @Suppress("UNUSED_VARIABLE") val redraw = tick

            // Облака на заднем плане.
            for (i in 0 until 4) {
                val cx = ((i * 0.27f + 0.1f + world.time * 0.004f * (i + 1)) % 1.2f - 0.1f) * size.width
                val cy = size.height * (0.12f + i * 0.2f)
                drawOval(Color.White.copy(alpha = 0.55f), Offset(cx - 70f * dp, cy), Size(140f * dp, 46f * dp))
                drawOval(Color.White.copy(alpha = 0.55f), Offset(cx - 40f * dp, cy - 22f * dp), Size(90f * dp, 50f * dp))
            }

            for (b in world.balloons) {
                if (!b.alive) continue
                val base = BALLOON_COLORS[b.colorIdx].base
                val wob = 1f + 0.07f * b.wobble * sin(world.time * 16f)
                val rx = b.r * 0.92f * wob
                val ry = b.r * 1.08f / wob
                val tilt = (b.vx / (40f * dp)).coerceIn(-1f, 1f) * 10f

                rotate(degrees = tilt, pivot = Offset(b.x, b.y)) {
                    // Ниточка.
                    val sway = sin(world.time * 2.2f + b.phase) * b.r * 0.25f
                    val sy = b.y + ry
                    val string = Path().apply {
                        moveTo(b.x, sy)
                        quadraticTo(b.x + sway, sy + b.r * 0.6f, b.x - sway * 0.4f, sy + b.r * 1.3f)
                    }
                    drawPath(string, Color(0x88455A64), style = Stroke(width = 2f * dp))
                    // Узелок.
                    val knot = Path().apply {
                        moveTo(b.x, sy - 2f * dp)
                        lineTo(b.x - 6f * dp, sy + 7f * dp)
                        lineTo(b.x + 6f * dp, sy + 7f * dp)
                        close()
                    }
                    drawPath(knot, base)
                    // Тело.
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.9f),
                                base,
                                base.copy(red = base.red * 0.75f, green = base.green * 0.75f, blue = base.blue * 0.75f)
                            ),
                            center = Offset(b.x - rx * 0.35f, b.y - ry * 0.4f),
                            radius = b.r * 1.5f
                        ),
                        topLeft = Offset(b.x - rx, b.y - ry),
                        size = Size(rx * 2f, ry * 2f)
                    )
                    // Блик.
                    drawOval(
                        Color.White.copy(alpha = 0.55f),
                        Offset(b.x - rx * 0.55f, b.y - ry * 0.65f),
                        Size(rx * 0.35f, ry * 0.28f)
                    )
                }
            }

            for (p in world.confetti) {
                drawCircle(p.color.copy(alpha = p.life.coerceIn(0f, 1f)), p.size, Offset(p.x, p.y))
            }
        }

        // Шапка: цель и прогресс.
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(88.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.85f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!world.freeMode) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(BALLOON_COLORS[targetIdx].base)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Лопни ${BALLOON_COLORS[targetIdx].name}!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF37474F)
                        )
                        Spacer(Modifier.width(12.dp))
                    } else {
                        Text(
                            "Лопай шарики!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF37474F)
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        "★".repeat(score) + "☆".repeat((goal - score).coerceAtLeast(0)),
                        fontSize = 18.sp,
                        color = Color(0xFFFFA000)
                    )
                }
            }
        }
    }

    // Игровой цикл: физика на каждом кадре.
    LaunchedEffect(world) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (world.w > 0f) {
                    if (!ready) {
                        ready = true
                        targetIdx = world.targetIdx
                    }
                    if (last != 0L) world.step((now - last) / 1_000_000_000f)
                }
                last = now
                tick++
            }
        }
    }
}

@Composable
fun BalloonZone(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    BalloonPopScreen(repository = repository, soundPlayer = soundPlayer)
}
