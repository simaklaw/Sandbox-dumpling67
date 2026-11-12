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
import com.example.shared.i18n.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlinx.coroutines.delay
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
 *
 * Лопание: под пальцем берётся ближайший по центру шарик; хлопок отталкивает соседей
 * (ударная волна) и рисует кольцо. Звук — файлы bp_pop1..7: один и тот же мягкий «поп» +
 * нота своего цвета (до-ре-ми-соль-ля-до-ре), поэтому очередь лопнувших шариков звучит
 * как мелодия. Играются «сырым» путём [SoundManager.playFxRaw]: если файл не успел
 * загрузиться, будет тишина, а не запасной процедурный «поп» поверх.
 */

private class BalloonColor(val name: String, val base: Color)

/** Одну и ту же ноту не повторяем чаще, чем раз в столько мс: двойной тап не удваивает громкость. */
private const val POP_GATE_MS = 70L

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
    /** Угол «качелей» шарика на ниточке (рад) и его скорость: ниточка и корпус запаздывают за движением. */
    var sw = 0f
    var swV = 0f
}

/** Обрывок лопнувшей резины: крутится и падает, гаснет. */
private class BalloonShard(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rot: Float,
    val vrot: Float,
    val color: Color,
    var life: Float,
    val len: Float
)

private class Confetti(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    var life: Float,
    val size: Float
)

/** Круг-«хлопок»: расходится от лопнувшего шарика и гаснет (life 1 → 0). */
private class Ring(
    val x: Float,
    val y: Float,
    val r0: Float,
    val r1: Float,
    val color: Color,
    var life: Float
)

private class BalloonWorld(
    private val dp: Float,
    private val count: Int,
    val freeMode: Boolean,
    val toddler: Boolean = false
) {
    var w = 0f
    var h = 0f
    var time = 0f
    var targetIdx = 0
    val balloons = ArrayList<Balloon>()
    val confetti = ArrayList<Confetti>()
    val rings = ArrayList<Ring>()
    val shards = ArrayList<BalloonShard>()

    var onPop: (Int) -> Unit = {}
    var onBounce: () -> Unit = {}

    private fun newBalloon(spawnAtBottom: Boolean): Balloon {
        val r = (Random.nextFloat() * 14f + (if (toddler) 40f else 36f)) * dp
        val x = r + Random.nextFloat() * (w - 2 * r).coerceAtLeast(1f)
        val y = if (spawnAtBottom) h + r * 1.4f else Random.nextFloat() * h
        // Мелкие шарики поднимаются быстрее, крупные — медленнее (сопротивление ~ площади).
        val terminal = (140f - r / dp * 0.9f).coerceIn(60f, 100f) * dp
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
        // Шарики входят в кадр не разом, а по очереди снизу: на экране всегда просторно.
        repeat(count) { i ->
            val b = newBalloon(spawnAtBottom = false)
            b.y = h * (0.15f + 0.8f * i / count.coerceAtLeast(1)) + Random.nextFloat() * b.r
            balloons.add(b)
        }
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

    /** Обрывки шарика: 7–9 кусочков резины своего цвета разлетаются и кружатся. */
    fun shatter(b: Balloon) {
        val c = BALLOON_COLORS[b.colorIdx].base
        repeat(8) {
            val a = Random.nextFloat() * 6.28f
            val s = (Random.nextFloat() * 220f + 120f) * dp
            shards.add(
                BalloonShard(
                    b.x + cos(a) * b.r * 0.5f, b.y + sin(a) * b.r * 0.6f,
                    cos(a) * s, sin(a) * s - 90f * dp,
                    Random.nextFloat() * 6.28f, (Random.nextFloat() - 0.5f) * 14f,
                    c, 1f, (Random.nextFloat() * 8f + 8f) * dp
                )
            )
        }
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

    /** Лопнувший шарик отталкивает соседей: хлопок «ощущается», а шарики не слипаются в гроздь. */
    private fun shockwave(src: Balloon) {
        val reach = src.r * 3.4f
        rings.add(Ring(src.x, src.y, src.r, reach, BALLOON_COLORS[src.colorIdx].base, 1f))
        for (b in balloons) {
            if (b === src || !b.alive) continue
            val dx = b.x - src.x
            val dy = b.y - src.y
            val d = sqrt(dx * dx + dy * dy)
            if (d >= reach || d < 0.001f) continue
            val k = 1f - d / reach
            // Лёгкие (мелкие) шарики улетают дальше тяжёлых.
            val push = 170f * dp * k * k * (src.r / b.r).coerceIn(0.7f, 1.4f)
            b.vx += dx / d * push
            b.vy += dy / d * push
            b.wobble = maxOf(b.wobble, 0.2f + 0.6f * k)
        }
    }

    fun tap(px: Float, py: Float) {
        // Под пальцем может быть несколько шариков: берём тот, чей центр ближе к касанию
        // (при равенстве — верхний, нарисованный позже).
        var best = Float.MAX_VALUE
        var found: Balloon? = null
        for (b in balloons) {
            if (!b.alive) continue
            val dx = (px - b.x) / (b.r * 0.95f)
            val dy = (py - b.y) / (b.r * 1.1f)
            val d = dx * dx + dy * dy
            if (d <= 1.15f && d <= best) {
                best = d
                found = b
            }
        }
        val hit = found ?: return
        val correct = freeMode || hit.colorIdx == targetIdx
        if (correct) {
            hit.alive = false
            hit.respawnIn = 1.4f + Random.nextFloat() * 1.6f
            burst(hit.x, hit.y, BALLOON_COLORS[hit.colorIdx].base, 9)
            shatter(hit)
            shockwave(hit)
            onPop(hit.colorIdx)
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
            // Качели на ниточке: пружина тянет к «против движения», с затуханием.
            val swTarget = (-b.vx / (70f * dp)).coerceIn(-1f, 1f) * 0.55f
            b.swV += ((swTarget - b.sw) * 38f - b.swV * 4.5f) * dt
            b.sw += b.swV * dt

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

        val shIt = shards.iterator()
        while (shIt.hasNext()) {
            val p = shIt.next()
            p.vy += 640f * dp * dt
            p.vx *= (1f - 1.2f * dt)
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rot += p.vrot * dt
            p.life -= dt * 0.8f
            if (p.life <= 0f || p.y > h + 40f) shIt.remove()
        }

        val ringIt = rings.iterator()
        while (ringIt.hasNext()) {
            val ring = ringIt.next()
            ring.life -= dt * 3.2f
            if (ring.life <= 0f) ringIt.remove()
        }
    }
}

// ───────────────────────── рисование ─────────────────────────

private fun lighten(c: Color, k: Float) = Color(
    (c.red + (1f - c.red) * k).coerceIn(0f, 1f),
    (c.green + (1f - c.green) * k).coerceIn(0f, 1f),
    (c.blue + (1f - c.blue) * k).coerceIn(0f, 1f),
    1f
)

private fun darken(c: Color, k: Float) = Color(c.red * (1f - k), c.green * (1f - k), c.blue * (1f - k), 1f)

/** Пушистое облачко из трёх кругов и «донышка». */
private fun DrawScope.drawCloud(cx: Float, cy: Float, s: Float, alpha: Float) {
    val c = Color.White.copy(alpha = alpha)
    drawCircle(c, 22f * s, Offset(cx - 34f * s, cy + 6f * s))
    drawCircle(c, 30f * s, Offset(cx - 6f * s, cy - 6f * s))
    drawCircle(c, 24f * s, Offset(cx + 26f * s, cy + 2f * s))
    drawRoundRect(c, Offset(cx - 56f * s, cy + 4f * s), Size(112f * s, 24f * s), CornerRadius(12f * s))
}

/** Два слоя мягких зелёных холмов внизу: шарики «вылетают» из-за них. */
private fun DrawScope.drawHills(w: Float, h: Float) {
    val far = Path().apply {
        moveTo(0f, h)
        lineTo(0f, h * 0.9f)
        cubicTo(w * 0.2f, h * 0.84f, w * 0.5f, h * 0.93f, w * 0.75f, h * 0.87f)
        cubicTo(w * 0.88f, h * 0.84f, w * 0.96f, h * 0.88f, w, h * 0.86f)
        lineTo(w, h)
        close()
    }
    drawPath(far, Color(0xFFAED581))
    val near = Path().apply {
        moveTo(0f, h)
        lineTo(0f, h * 0.95f)
        cubicTo(w * 0.3f, h * 0.9f, w * 0.55f, h * 0.99f, w * 0.8f, h * 0.93f)
        cubicTo(w * 0.9f, h * 0.91f, w * 0.97f, h * 0.94f, w, h * 0.93f)
        lineTo(w, h)
        close()
    }
    drawPath(
        near,
        Brush.verticalGradient(listOf(Color(0xFF8BC34A), Color(0xFF689F38)), startY = h * 0.9f, endY = h)
    )
}

/** Контур воздушного шарика: яйцо, сужающееся книзу к узелку. (cx, cy) — центр, rx/ry — полуоси. */
private fun balloonPath(cx: Float, cy: Float, rx: Float, ry: Float): Path = Path().apply {
    val top = cy - ry
    val bottom = cy + ry
    moveTo(cx, bottom)
    cubicTo(cx - rx * 0.18f, bottom - ry * 0.10f, cx - rx, cy + ry * 0.55f, cx - rx, cy - ry * 0.10f)
    cubicTo(cx - rx, cy - ry * 0.78f, cx - rx * 0.55f, top, cx, top)
    cubicTo(cx + rx * 0.55f, top, cx + rx, cy - ry * 0.78f, cx + rx, cy - ry * 0.10f)
    cubicTo(cx + rx, cy + ry * 0.55f, cx + rx * 0.18f, bottom - ry * 0.10f, cx, bottom)
    close()
}

/**
 * Шарик: объёмная заливка (свет сверху-слева, тёмный край), мягкий блик «окошком», отражённый
 * свет снизу, узелок и ниточка, которая запаздывает за движением (угол [sw] — от физики).
 */
private fun DrawScope.drawBalloon(
    x: Float, y: Float, r: Float, base: Color,
    sw: Float, phase: Float, wobble: Float, time: Float, dp: Float
) {
    val wob = 1f + 0.06f * wobble * sin(time * 18f + phase)
    val rx = r * 0.9f * wob
    val ry = r * 1.1f / wob
    val dark = darken(base, 0.30f)
    val light = lighten(base, 0.55f)

    rotate(degrees = sw * 20f, pivot = Offset(x, y)) {
        val kx = x
        val ky = y + ry
        // Ниточка: плавная кривая с лёгким «змейкой» колебанием.
        val len = r * 1.9f
        val curl = sin(time * 2.4f + phase) * r * 0.22f
        val string = Path().apply {
            moveTo(kx, ky + 3f * dp)
            cubicTo(kx + curl, ky + len * 0.35f, kx - curl, ky + len * 0.7f, kx + curl * 0.3f, ky + len)
        }
        drawPath(string, Color(0x99455A64), style = Stroke(width = 1.8f * dp))

        // Тело.
        val body = balloonPath(x, y, rx, ry)
        drawPath(
            body,
            Brush.radialGradient(
                colors = listOf(light, base, dark),
                center = Offset(x - rx * 0.3f, y - ry * 0.35f),
                radius = ry * 1.55f
            )
        )
        drawPath(body, dark.copy(alpha = 0.35f), style = Stroke(width = 1.4f * dp))

        // Узелок.
        val knot = Path().apply {
            moveTo(kx, ky - 1f * dp)
            lineTo(kx - 5.5f * dp, ky + 6f * dp)
            lineTo(kx + 5.5f * dp, ky + 6f * dp)
            close()
        }
        drawPath(knot, dark)

        // Отражённый свет снизу-справа.
        drawCircle(Color.White.copy(alpha = 0.16f), rx * 0.2f, Offset(x + rx * 0.5f, y + ry * 0.5f))
        // Главный блик «окошком» и маленькая точка рядом.
        val hx = x - rx * 0.42f
        val hy = y - ry * 0.52f
        rotate(degrees = -28f, pivot = Offset(hx, hy)) {
            drawOval(
                Color.White.copy(alpha = 0.62f),
                Offset(hx - rx * 0.17f, hy - ry * 0.3f),
                Size(rx * 0.34f, ry * 0.6f)
            )
        }
        drawCircle(Color.White.copy(alpha = 0.55f), rx * 0.06f, Offset(x - rx * 0.12f, y - ry * 0.78f))
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
        BalloonWorld(dp = dp, count = if (ageProfile.isToddler2) 6 else 8, freeMode = true, toddler = ageProfile.isToddler2)
    }
    var score by remember { mutableIntStateOf(0) }
    var targetIdx by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var cheer by remember { mutableIntStateOf(0) }
    val lastPopMs = remember { LongArray(BALLOON_COLORS.size) }

    LaunchedEffect(Unit) {
        soundManager.preloadRaw((1..BALLOON_COLORS.size).map { "bp_pop$it" })
    }
    // Фанфара звучит чуть позже последнего «попа», чтобы нота лопания не тонула в ней.
    LaunchedEffect(cheer) {
        if (cheer > 0) {
            delay(170)
            soundPlayer.playFanfare()
        }
    }
    world.onPop = { ci ->
        // Нота своего цвета (bp_pop1..7). Высоту не «дрожим» — только на ±1,5 %, иначе нота
        // выпадает из лада. Сырой путь: без каталога и без запасного процедурного «попа».
        val now = System.currentTimeMillis()
        if (now - lastPopMs[ci] >= POP_GATE_MS) {
            lastPopMs[ci] = now
            soundManager.playFxRaw(
                "bp_pop${ci + 1}",
                volume = 0.9f,
                rate = 1f + (Random.nextFloat() - 0.5f) * 0.03f
            )
        }
        soundPlayer.vibrateSquish()
        score += 1
        if (score >= goal) {
            cheer += 1
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

            // Небо: плывущие пушистые облака (ближние крупнее и быстрее) и холмы внизу.
            for (i in 0 until 5) {
                val speed = 0.003f * (1f + i * 0.6f)
                val cx = ((i * 0.23f + 0.08f + world.time * speed) % 1.3f - 0.15f) * size.width
                val cy = size.height * (0.10f + (i * 37 % 5) * 0.14f)
                drawCloud(cx, cy, dp * (0.7f + 0.18f * (i % 3)), 0.5f + 0.1f * (i % 3))
            }
            drawHills(size.width, size.height)

            for (b in world.balloons) {
                if (!b.alive) continue
                drawBalloon(b.x, b.y, b.r, BALLOON_COLORS[b.colorIdx].base, b.sw, b.phase, b.wobble, world.time, dp)
            }

            for (ring in world.rings) {
                val l = ring.life.coerceIn(0f, 1f)
                val p = 1f - l
                val eased = 1f - (1f - p) * (1f - p)
                val rad = ring.r0 + (ring.r1 - ring.r0) * eased
                drawCircle(ring.color.copy(alpha = 0.16f * l), rad, Offset(ring.x, ring.y))
                drawCircle(
                    color = Color.White.copy(alpha = 0.7f * l),
                    radius = rad,
                    center = Offset(ring.x, ring.y),
                    style = Stroke(width = (1.5f + 3f * l) * dp)
                )
            }

            for (p in world.shards) {
                val l = p.life.coerceIn(0f, 1f)
                rotate(degrees = p.rot * 57.3f, pivot = Offset(p.x, p.y)) {
                    drawRoundRect(
                        color = p.color.copy(alpha = l),
                        topLeft = Offset(p.x - p.len / 2f, p.y - p.len * 0.2f),
                        size = Size(p.len, p.len * 0.4f),
                        cornerRadius = CornerRadius(p.len * 0.2f)
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
