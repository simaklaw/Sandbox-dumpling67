package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.LocalAgeProfile
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
 * «Пиньята» — праздничная комната. Подвешенная на верёвке бумажная пиньята (звезда, шар или сердце)
 * раскачивается как настоящий маятник. Бей палкой: от ударов появляются трещины, из дырок
 * сыплются конфеты и летит бумажное конфетти. Последний удар — пиньята раскалывается, и вся
 * сладость сыплется на пол. Собирай конфеты пальцем в банку: когда банка полная — салют.
 */

private val PIN_PALETTES = listOf(
    intArrayOf(0xFFE91E63.toInt(), 0xFFFFC107.toInt(), 0xFF29B6F6.toInt(), 0xFF66BB6A.toInt()),
    intArrayOf(0xFFAB47BC.toInt(), 0xFFFF7043.toInt(), 0xFFFFEE58.toInt(), 0xFF26C6DA.toInt()),
    intArrayOf(0xFFEF5350.toInt(), 0xFFFFFFFF.toInt(), 0xFF42A5F5.toInt(), 0xFFFFCA28.toInt())
)
private val CANDY_COLORS = intArrayOf(
    0xFFFF4081.toInt(), 0xFFFFC400.toInt(), 0xFF40C4FF.toInt(), 0xFF69F0AE.toInt(),
    0xFFB388FF.toInt(), 0xFFFF6E40.toInt(), 0xFFFFFFFF.toInt(), 0xFF8D6E63.toInt()
)

private class PCandy(var x: Float, var y: Float, var vx: Float, var vy: Float, val kind: Int, val color: Int) {
    var rot = Random.nextFloat() * 6.28f
    var vr = (Random.nextFloat() - 0.5f) * 8f
    var collecting = false
    var t = 0f
    var sx = 0f
    var sy = 0f
    var age = 0f
}

private class PPiece(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Int, val w: Float, val h: Float) {
    var rot = Random.nextFloat() * 6.28f
    var vr = (Random.nextFloat() - 0.5f) * 14f
    var life = 1f
    val flutter = Random.nextFloat() * 6.28f
}

private class PHalf(val side: Int, var x: Float, var y: Float, var vx: Float, var vy: Float, var rot: Float, val vr: Float)
private class PSpark(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Int) { var life = 1f }

private class PinataWorld(val toddler: Boolean) {
    var w = 0f
    var h = 0f
    var time = 0f

    // маятник
    var theta = 0f
    var omega = 0f
    var squash = 0f
    var shape = 0
    var palette = PIN_PALETTES[0]
    var hp = 0
    var maxHp = if (toddler) 4 else 7
    var state = 2             // 0 висит, 1 разбита, 2 спускается
    var enterT = 0f
    var brokenT = 0f
    val cracks = ArrayList<List<Offset>>()
    val halves = ArrayList<PHalf>()

    // палка
    var swingT = 1.2f
    var pending = false
    var tapX = 0f
    var tapY = 0f
    var stickQx = 0f
    var stickQy = 0f
    var stickSide = 1f

    val candies = ArrayList<PCandy>()
    val pieces = ArrayList<PPiece>()
    val sparks = ArrayList<PSpark>()
    var score = 0
    var jar = 0
    var jars = 0
    var hits = 0
    var clinkCd = 0f
    var hint = 0
    var onEvent: (String) -> Unit = {}

    val anchorX get() = w / 2f
    val anchorY = 12f
    val ropeL get() = min(h * 0.34f, 210f)
    val radius get() = min(h * 0.19f, 92f) * (if (toddler) 1.1f else 1f)
    val floorY get() = h - 52f
    val jarX get() = 56f
    val jarTop get() = floorY - 100f

    fun center(): Offset {
        val enter = if (state == 2) -(1f - ease(enterT)) * (ropeL + radius + 80f) else 0f
        return Offset(anchorX + sin(theta) * ropeL, anchorY + cos(theta) * ropeL + enter)
    }

    private fun ease(t: Float): Float { val k = t.coerceIn(0f, 1f); return 1f - (1f - k) * (1f - k) * (1f - k) }

    fun newPinata() {
        shape = (shape + 1 + Random.nextInt(2)) % 3
        palette = PIN_PALETTES[Random.nextInt(PIN_PALETTES.size)]
        hp = maxHp
        cracks.clear(); halves.clear()
        state = 2; enterT = 0f; theta = 0f; omega = 0f; brokenT = 0f
    }

    fun init(width: Float, height: Float) {
        if (w == width && h == height) return
        val first = w == 0f
        w = width; h = height
        if (first) newPinata()
    }

    private fun spawnPaper(x: Float, y: Float, n: Int, speed: Float) {
        repeat(n) {
            val a = Random.nextFloat() * 6.28f
            val s = speed * (0.4f + Random.nextFloat())
            pieces.add(PPiece(x, y, cos(a) * s, sin(a) * s - speed * 0.4f, palette[Random.nextInt(palette.size)], 5f + Random.nextFloat() * 5f, 9f + Random.nextFloat() * 8f))
        }
    }

    private fun spawnCandy(x: Float, y: Float, vx: Float, vy: Float) {
        candies.add(PCandy(x, y, vx, vy, Random.nextInt(5), CANDY_COLORS[Random.nextInt(CANDY_COLORS.size)]))
        if (candies.size > 90) candies.removeAt(0)
    }

    private fun addCrack() {
        val a = Random.nextFloat() * 6.28f
        val pts = ArrayList<Offset>()
        var px = cos(a) * 0.95f
        var py = sin(a) * 0.95f
        pts.add(Offset(px, py))
        val steps = 3 + Random.nextInt(2)
        for (i in 1..steps) {
            val k = i / steps.toFloat()
            px = cos(a) * (0.95f - 0.8f * k) + (Random.nextFloat() - 0.5f) * 0.28f
            py = sin(a) * (0.95f - 0.8f * k) + (Random.nextFloat() - 0.5f) * 0.28f
            pts.add(Offset(px, py))
        }
        cracks.add(pts)
    }

    /** Тап: палка замахивается; удар приходит в момент касания. */
    fun tap(x: Float, y: Float) {
        tapX = x; tapY = y
        stickSide = if (x < anchorX) -1f else 1f
        stickQx = x + stickSide * 150f
        stickQy = y + 190f
        swingT = 0f
        pending = true
        onEvent("swing")
    }

    private fun resolveHit() {
        if (state != 0) return
        val c = center()
        val reach = radius * (if (toddler) 1.35f else 1.1f)
        if (hypot(tapX - c.x, tapY - c.y) > reach) return
        hits++
        hint = 1
        val dir = if (tapX >= c.x) -1f else 1f
        omega += dir * (2.4f + Random.nextFloat() * 1.2f)
        squash = 1f
        hp--
        if (hp > 0) {
            addCrack()
            spawnPaper(tapX, tapY, 12, 260f)
            val n = if (toddler) 2 else 1 + Random.nextInt(2)
            repeat(n) { spawnCandy(c.x + (Random.nextFloat() - 0.5f) * radius, c.y + radius * 0.3f, (Random.nextFloat() - 0.5f) * 160f, -60f - Random.nextFloat() * 120f) }
            onEvent("whack")
        } else {
            breakOpen(c)
        }
    }

    private fun breakOpen(c: Offset) {
        state = 1
        brokenT = 0f
        hint = 2
        halves.add(PHalf(-1, c.x, c.y, -170f, -260f, theta, -2.4f))
        halves.add(PHalf(1, c.x, c.y, 170f, -260f, theta, 2.4f))
        spawnPaper(c.x, c.y, 70, 430f)
        val n = if (toddler) 26 else 38
        repeat(n) {
            val a = Random.nextFloat() * 6.28f
            val s = 120f + Random.nextFloat() * 380f
            spawnCandy(c.x + cos(a) * radius * 0.4f, c.y + sin(a) * radius * 0.4f, cos(a) * s, sin(a) * s - 260f)
        }
        onEvent("break")
    }

    /** Палец ведёт по конфетам — они улетают в банку. */
    fun sweep(x: Float, y: Float) {
        val r = if (toddler) 46f else 34f
        for (c in candies) {
            if (c.collecting) continue
            if (hypot(c.x - x, c.y - y) < r) startCollect(c)
        }
    }

    private fun startCollect(c: PCandy) {
        c.collecting = true; c.t = 0f; c.sx = c.x; c.sy = c.y
    }

    fun step(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0f, 0.04f)
        time += dt
        clinkCd = max(0f, clinkCd - dt)
        // маятник
        omega += (-8.2f * sin(theta) - 0.5f * omega) * dt
        theta += omega * dt
        theta = theta.coerceIn(-1.1f, 1.1f)
        squash = max(0f, squash - dt * 4.2f)
        if (state == 2) {
            enterT += dt / 1.1f
            if (enterT >= 1f) { enterT = 1f; state = 0; omega = 0.8f }
        }
        // палка
        if (swingT < 1.2f) {
            swingT += dt / 0.3f
            if (pending && swingT >= 0.35f) { pending = false; resolveHit() }
        }
        // после разбития — новая пиньята, когда конфеты собраны (или через 9 секунд)
        if (state == 1) {
            brokenT += dt
            val left = candies.count { !it.collecting }
            if ((left <= 3 && brokenT > 2f) || brokenT > 12f) newPinata()
        }
        // половинки
        val hi = halves.iterator()
        while (hi.hasNext()) {
            val hv = hi.next()
            hv.vy += 1800f * dt; hv.x += hv.vx * dt; hv.y += hv.vy * dt; hv.rot += hv.vr * dt
            if (hv.y > h + 200f) hi.remove()
        }
        // конфеты
        val ci = candies.iterator()
        while (ci.hasNext()) {
            val c = ci.next()
            c.age += dt
            if (c.collecting) {
                c.t += dt / 0.5f
                val k = min(1f, c.t)
                val jx = jarX
                val jy = jarTop + 30f
                c.x = c.sx + (jx - c.sx) * k
                c.y = c.sy + (jy - c.sy) * k - sin(k * PI.toFloat()) * 90f
                c.rot += c.vr * dt
                if (c.t >= 1f) {
                    ci.remove()
                    score++
                    jar++
                    onEvent("collect")
                    for (i in 0 until 4) sparks.add(PSpark(jx, jy, (Random.nextFloat() - 0.5f) * 160f, -Random.nextFloat() * 140f, CANDY_COLORS[Random.nextInt(CANDY_COLORS.size)]))
                    if (jar >= 30) {
                        jar = 0; jars++
                        onEvent("jarFull")
                        spawnPaper(jx + 60f, jarTop, 50, 380f)
                    }
                }
                continue
            }
            c.vy += 1700f * dt
            c.x += c.vx * dt
            c.y += c.vy * dt
            c.rot += c.vr * dt
            if (c.x < 14f) { c.x = 14f; c.vx = -c.vx * 0.5f }
            if (c.x > w - 14f) { c.x = w - 14f; c.vx = -c.vx * 0.5f }
            if (c.y > floorY - 10f) {
                c.y = floorY - 10f
                if (abs(c.vy) > 220f && clinkCd <= 0f) { clinkCd = 0.07f; onEvent("clink") }
                c.vy = -c.vy * 0.42f
                c.vx *= 0.78f
                c.vr *= 0.7f
                if (abs(c.vy) < 60f) { c.vy = 0f }
            }
            if (toddler && c.age > 7f && state != 2) startCollect(c)
        }
        // бумажки
        val pi = pieces.iterator()
        while (pi.hasNext()) {
            val p = pi.next()
            p.vy += 520f * dt
            p.vx *= (1f - dt * 1.4f); p.vy *= (1f - dt * 0.9f)
            p.x += (p.vx + sin(time * 6f + p.flutter) * 30f) * dt
            p.y += p.vy * dt
            p.rot += p.vr * dt
            p.life -= dt * 0.4f
            if (p.y > floorY + 20f || p.life <= 0f) pi.remove()
        }
        val si = sparks.iterator()
        while (si.hasNext()) {
            val s = si.next()
            s.x += s.vx * dt; s.y += s.vy * dt; s.vy += 400f * dt; s.life -= dt * 1.8f
            if (s.life <= 0f) si.remove()
        }
    }
}

@Composable
fun PinataScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val density = LocalDensity.current.density
    val toddler = LocalAgeProfile.current.isToddler2
    val world = remember(toddler) { PinataWorld(toddler) }
    var tick by remember { mutableIntStateOf(0) }
    var hudScore by remember { mutableIntStateOf(0) }
    var hudHint by remember { mutableIntStateOf(0) }
    var hudJars by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        soundManager.preloadRaw(
            listOf(
                "pin_whoosh", "pin_whack1", "pin_whack2", "pin_whack3", "pin_break", "pin_candy1", "pin_candy2",
                "pin_candy3", "pin_pour", "pin_collect", "pin_horn", "pin_rustle"
            )
        )
    }
    world.onEvent = { ev ->
        when (ev) {
            "swing" -> soundManager.play("pin_whoosh", volume = 0.6f, rate = 0.9f + Random.nextFloat() * 0.25f)
            "whack" -> {
                soundManager.play("pin_whack${1 + Random.nextInt(3)}", volume = 0.95f, rate = 0.95f + Random.nextFloat() * 0.1f)
                soundManager.play("pin_rustle", volume = 0.6f)
                soundManager.play("pin_candy${1 + Random.nextInt(3)}", volume = 0.5f)
                soundPlayer.vibrateSquishDeform()
            }
            "break" -> {
                soundManager.play("pin_break", volume = 1f)
                soundManager.play("pin_pour", volume = 0.8f)
                soundManager.play("pin_horn", volume = 0.7f)
                soundPlayer.vibrateSquishDeform()
            }
            "clink" -> soundManager.play("pin_candy${1 + Random.nextInt(3)}", volume = 0.35f + Random.nextFloat() * 0.25f, rate = 0.9f + Random.nextFloat() * 0.35f)
            "collect" -> soundManager.play("pin_collect", volume = 0.6f, rate = 0.85f + (world.jar % 10) * 0.05f)
            "jarFull" -> {
                soundManager.play("pin_horn", volume = 0.8f)
                soundManager.play("pin_pour", volume = 0.6f)
                soundPlayer.playFanfare()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFFE0B2))
            .testTag("pinata_screen")
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    world.tap(down.position.x / density, down.position.y / density)
                    world.sweep(down.position.x / density, down.position.y / density)
                    do {
                        val e = awaitPointerEvent()
                        e.changes.firstOrNull()?.let { world.sweep(it.position.x / density, it.position.y / density) }
                        e.changes.forEach { it.consume() }
                    } while (e.changes.any { it.pressed })
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val redraw = tick
            world.init(size.width / density, size.height / density)
            withTransform({ scale(density, density, Offset.Zero) }) { drawPinataRoom(world) }
        }
        Box(
            Modifier
                .statusBarsPadding()
                .padding(start = 84.dp, top = 8.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text("🍬 $hudScore" + (if (hudJars > 0) "   🏆 $hudJars" else ""), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 56.dp), contentAlignment = Alignment.TopCenter) {
            val msg = when (hudHint) {
                0 -> "Бей палкой — тапай по пиньяте!"
                2 -> "Собирай конфеты пальцем!"
                else -> ""
            }
            if (msg.isNotEmpty()) {
                Text(
                    msg, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White,
                    modifier = Modifier
                        .background(Color(0xFFD81B60).copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                if (world.w > 0f) world.step(dt)
                hudScore = world.score; hudHint = world.hint; hudJars = world.jars
                tick++
            }
        }
    }
}

// ───────────────────────── Рисование ─────────────────────────

private fun darker(c: Int, k: Float): Color {
    val col = Color(c)
    return Color(col.red * k, col.green * k, col.blue * k, 1f)
}

private fun DrawScope.drawPinataRoom(w: PinataWorld) {
    val sw = w.w
    val sh = w.h
    val t = w.time
    // стена в полоску
    drawRect(Color(0xFFFFF3E0), Offset.Zero, Size(sw, sh))
    var x = 0f
    var i = 0
    while (x < sw) {
        if (i % 2 == 0) drawRect(Color(0xFFFFE0B2), Offset(x, 0f), Size(44f, w.floorY))
        x += 44f; i++
    }
    drawRect(Brush.verticalGradient(listOf(Color(0x00000000), Color(0x33FF8A65)), startY = w.floorY - 120f, endY = w.floorY), Offset(0f, w.floorY - 120f), Size(sw, 120f))
    // потолочная балка
    drawRect(Color(0xFF6D4C41), Offset(0f, 0f), Size(sw, 14f))
    drawRect(Color(0xFF8D6E63), Offset(0f, 12f), Size(sw, 4f))
    // гирлянда флажков
    val flagCols = intArrayOf(0xFFE91E63.toInt(), 0xFFFFC107.toInt(), 0xFF29B6F6.toInt(), 0xFF66BB6A.toInt(), 0xFFAB47BC.toInt())
    val n = 14
    for (k in 0 until n) {
        val u = k / (n - 1f)
        val fx = u * sw
        val fy = 18f + sin(u * PI.toFloat()) * 38f
        val sway = sin(t * 1.6f + k) * 3f
        val path = Path().apply { moveTo(fx - 13f, fy); lineTo(fx + 13f, fy); lineTo(fx + sway, fy + 30f); close() }
        drawPath(path, Color(flagCols[k % flagCols.size]))
    }
    run {
        val path = Path()
        for (k in 0..40) {
            val u = k / 40f
            val px = u * sw
            val py = 18f + sin(u * PI.toFloat()) * 38f
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, Color(0xFF5D4037), style = Stroke(width = 2.5f))
    }
    // воздушные шары по краям
    val ballCols = intArrayOf(0xFFEF5350.toInt(), 0xFF42A5F5.toInt(), 0xFFFFCA28.toInt(), 0xFF66BB6A.toInt(), 0xFFAB47BC.toInt())
    for (k in 0 until 6) {
        val left = k % 2 == 0
        val bx = if (left) 30f + (k / 2) * 34f else sw - 30f - (k / 2) * 34f
        val by = sh * 0.38f + sin(t * 0.9f + k * 1.3f) * 8f - (k / 2) * 26f
        drawLine(Color(0xFF8D6E63), Offset(bx, by + 30f), Offset(bx + sin(t + k) * 5f, w.floorY - 8f), 1.5f)
        drawOval(Color(ballCols[k % ballCols.size]), Offset(bx - 22f, by - 30f), Size(44f, 60f))
        drawOval(Color.White.copy(alpha = 0.35f), Offset(bx - 12f, by - 22f), Size(11f, 18f))
        val tri = Path().apply { moveTo(bx - 5f, by + 29f); lineTo(bx + 5f, by + 29f); lineTo(bx, by + 36f); close() }
        drawPath(tri, Color(ballCols[k % ballCols.size]))
    }
    // пол
    drawRect(Brush.verticalGradient(listOf(Color(0xFFA1704A), Color(0xFF6D4C41)), startY = w.floorY, endY = sh), Offset(0f, w.floorY), Size(sw, sh - w.floorY))
    var px = 0f
    while (px < sw) { drawRect(Color(0x33000000), Offset(px, w.floorY), Size(2f, sh - w.floorY)); px += 70f }
    drawRect(Color(0xFFD7A36E), Offset(0f, w.floorY), Size(sw, 4f))

    drawJar(w)

    // тени конфет
    for (c in w.candies) if (!c.collecting) drawOval(Color.Black.copy(alpha = 0.18f), Offset(c.x - 9f, w.floorY + 2f), Size(18f, 6f))

    // пиньята
    val c = w.center()
    val ax = w.anchorX
    val ay = w.anchorY
    if (w.state != 1) {
        val topX = c.x - sin(w.theta) * w.radius * 0.95f
        val topY = c.y - cos(w.theta) * w.radius * 0.95f
        drawLine(Color(0xFF8D6E63), Offset(ax, ay), Offset(topX, topY), 4f, StrokeCap.Round)
        drawLine(Color(0xFFBCAAA4), Offset(ax - 1f, ay), Offset(topX - 1f, topY), 1.5f)
        drawCircle(Color(0xFF3E2723), 5f, Offset(ax, ay))
        val sq = w.squash
        withTransform({
            rotate(w.theta * 180f / PI.toFloat(), pivot = c)
            scale(1f + 0.14f * sq, 1f - 0.14f * sq, pivot = c)
        }) { drawPinataBody(w, c, 0) }
        drawCircle(Color(0xFFFFD54F), 7f, Offset(topX, topY))
        drawCircle(Color(0xFF8D6E63), 7f, Offset(topX, topY), style = Stroke(2f))
    }
    for (hv in w.halves) {
        withTransform({ rotate(hv.rot * 180f / PI.toFloat(), pivot = Offset(hv.x, hv.y)) }) {
            drawPinataBody(w, Offset(hv.x, hv.y), hv.side)
        }
    }

    // конфеты
    for (cd in w.candies) drawCandy(cd, w.toddler)

    // бумажное конфетти
    for (p in w.pieces) {
        val a = p.life.coerceIn(0f, 1f)
        val wid = p.w * (0.35f + 0.65f * abs(cos(p.rot * 1.3f)))
        withTransform({ rotate(p.rot * 180f / PI.toFloat(), pivot = Offset(p.x, p.y)) }) {
            drawRect(Color(p.color).copy(alpha = a), Offset(p.x - wid / 2f, p.y - p.h / 2f), Size(wid, p.h))
        }
    }
    for (s in w.sparks) drawCircle(Color(s.color).copy(alpha = s.life.coerceIn(0f, 1f)), 3f, Offset(s.x, s.y))

    drawStick(w)
}

private fun shapePath(kind: Int, c: Offset, r: Float): Path {
    val p = Path()
    when (kind) {
        0 -> { // звезда
            val pts = 10
            for (i in 0 until pts) {
                val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
                val rr = if (i % 2 == 0) r * 1.08f else r * 0.55f
                val x = c.x + cos(a) * rr
                val y = c.y + sin(a) * rr
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
        }
        1 -> { // шар
            p.addOval(androidx.compose.ui.geometry.Rect(c.x - r * 0.95f, c.y - r * 0.95f, c.x + r * 0.95f, c.y + r * 0.95f))
        }
        else -> { // сердце
            p.moveTo(c.x, c.y + r * 0.95f)
            p.cubicTo(c.x - r * 1.4f, c.y + r * 0.1f, c.x - r * 0.95f, c.y - r * 1.0f, c.x, c.y - r * 0.35f)
            p.cubicTo(c.x + r * 0.95f, c.y - r * 1.0f, c.x + r * 1.4f, c.y + r * 0.1f, c.x, c.y + r * 0.95f)
            p.close()
        }
    }
    return p
}

/** half: 0 — целиком, -1 — левая половинка, 1 — правая. */
private fun DrawScope.drawPinataBody(w: PinataWorld, c: Offset, half: Int) {
    val r = w.radius
    val path = shapePath(w.shape, c, r)
    val pal = w.palette
    clipPath(path) {
        val drawAll = {
            drawRect(Color(pal[0]), Offset(c.x - r * 1.5f, c.y - r * 1.5f), Size(r * 3f, r * 3f))
            val rows = 8
            val band = 2f * r / rows
            for (row in 0 until rows) {
                val y0 = c.y - r * 1.05f + row * band
                val col = Color(pal[(row + 1) % pal.size])
                val tw = r * 0.24f
                drawRect(col, Offset(c.x - r * 1.5f, y0), Size(r * 3f, band * 0.5f))
                var xx = c.x - r * 1.5f
                while (xx < c.x + r * 1.5f) {
                    val tri = Path().apply { moveTo(xx, y0 + band * 0.4f); lineTo(xx + tw, y0 + band * 0.4f); lineTo(xx + tw / 2f, y0 + band * 1.25f); close() }
                    drawPath(tri, col)
                    xx += tw
                }
                drawRect(Color.Black.copy(alpha = 0.10f), Offset(c.x - r * 1.5f, y0 + band * 0.4f), Size(r * 3f, 2f))
            }
            drawOval(Color.White.copy(alpha = 0.22f), Offset(c.x - r * 0.62f, c.y - r * 0.72f), Size(r * 0.55f, r * 0.34f))
            // трещины и дырки
            for (cr in w.cracks) {
                val path2 = Path()
                for ((i, pt) in cr.withIndex()) {
                    val q = Offset(c.x + pt.x * r, c.y + pt.y * r)
                    if (i == 0) path2.moveTo(q.x, q.y) else path2.lineTo(q.x, q.y)
                }
                drawPath(path2, Color(0xFF3E2723), style = Stroke(width = 4f, join = StrokeJoin.Round, cap = StrokeCap.Round))
                drawPath(path2, Color(0xFFFFF3E0), style = Stroke(width = 1.6f, join = StrokeJoin.Round, cap = StrokeCap.Round))
                val end = cr.last()
                val hole = Path().apply {
                    val ex = c.x + end.x * r; val ey = c.y + end.y * r
                    for (k in 0 until 7) {
                        val a = k * 0.9f
                        val rr = r * (0.09f + 0.04f * (k % 2))
                        val xq = ex + cos(a) * rr; val yq = ey + sin(a) * rr
                        if (k == 0) moveTo(xq, yq) else lineTo(xq, yq)
                    }
                    close()
                }
                drawPath(hole, Color(0xFF5D4037))
            }
        }
        when (half) {
            -1 -> clipRect(c.x - r * 2f, c.y - r * 2f, c.x, c.y + r * 2f) { drawAll() }
            1 -> clipRect(c.x, c.y - r * 2f, c.x + r * 2f, c.y + r * 2f) { drawAll() }
            else -> drawAll()
        }
    }
    drawPath(path, darker(pal[0], 0.55f), style = Stroke(width = 3.5f, join = StrokeJoin.Round))
}

private fun DrawScope.drawCandy(c: PCandy, toddler: Boolean) {
    val k = if (toddler) 1.25f else 1f
    val col = Color(c.color)
    withTransform({ rotate(c.rot * 180f / PI.toFloat(), pivot = Offset(c.x, c.y)) }) {
        when (c.kind) {
            0 -> { // конфета в обёртке
                val l = Path().apply { moveTo(c.x - 8f * k, c.y); lineTo(c.x - 17f * k, c.y - 7f * k); lineTo(c.x - 17f * k, c.y + 7f * k); close() }
                val r = Path().apply { moveTo(c.x + 8f * k, c.y); lineTo(c.x + 17f * k, c.y - 7f * k); lineTo(c.x + 17f * k, c.y + 7f * k); close() }
                drawPath(l, col); drawPath(r, col)
                drawOval(col, Offset(c.x - 10f * k, c.y - 7f * k), Size(20f * k, 14f * k))
                drawOval(Color.White.copy(alpha = 0.5f), Offset(c.x - 6f * k, c.y - 5f * k), Size(8f * k, 4f * k))
                drawOval(darker(c.color, 0.6f), Offset(c.x - 10f * k, c.y - 7f * k), Size(20f * k, 14f * k), style = Stroke(1.5f))
            }
            1 -> { // леденец
                drawLine(Color(0xFFF5F5F5), Offset(c.x, c.y), Offset(c.x, c.y + 18f * k), 3f * k, StrokeCap.Round)
                drawCircle(col, 10f * k, Offset(c.x, c.y))
                drawCircle(Color.White, 6.5f * k, Offset(c.x, c.y), style = Stroke(2.2f * k))
                drawCircle(Color.White, 2.8f * k, Offset(c.x, c.y), style = Stroke(2.2f * k))
                drawCircle(darker(c.color, 0.6f), 10f * k, Offset(c.x, c.y), style = Stroke(1.5f))
            }
            2 -> { // мармеладка
                drawRoundRect(col, Offset(c.x - 9f * k, c.y - 8f * k), Size(18f * k, 16f * k), CornerRadius(8f * k))
                for (d in 0 until 5) drawCircle(Color.White.copy(alpha = 0.65f), 1.1f * k, Offset(c.x - 6f * k + (d % 3) * 6f * k, c.y - 4f * k + (d / 3) * 7f * k))
                drawRoundRect(darker(c.color, 0.6f), Offset(c.x - 9f * k, c.y - 8f * k), Size(18f * k, 16f * k), CornerRadius(8f * k), style = Stroke(1.5f))
            }
            3 -> { // звёздочка
                val p = Path()
                for (i in 0 until 10) {
                    val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
                    val rr = (if (i % 2 == 0) 11f else 5f) * k
                    val x = c.x + cos(a) * rr; val y = c.y + sin(a) * rr
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
                drawPath(p, col)
                drawPath(p, darker(c.color, 0.6f), style = Stroke(1.5f, join = StrokeJoin.Round))
            }
            else -> { // шоколадка
                drawRoundRect(Color(0xFF6D4C41), Offset(c.x - 12f * k, c.y - 7f * k), Size(24f * k, 14f * k), CornerRadius(3f * k))
                for (d in 0 until 3) drawRect(Color(0xFF8D6E63), Offset(c.x - 10f * k + d * 7f * k, c.y - 5f * k), Size(5f * k, 4f * k))
                drawRect(col, Offset(c.x - 12f * k, c.y + 1f * k), Size(24f * k, 4f * k))
            }
        }
    }
}

private fun DrawScope.drawJar(w: PinataWorld) {
    val jx = w.jarX
    val bottom = w.floorY + 6f
    val top = w.jarTop
    val jw = 64f
    drawOval(Color.Black.copy(alpha = 0.18f), Offset(jx - jw / 2f - 6f, bottom - 5f), Size(jw + 12f, 12f))
    // конфеты в банке
    val cnt = w.jar
    clipPath(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(jx - jw / 2f, top + 14f, jx + jw / 2f, bottom, CornerRadius(12f))) }) {
        for (i in 0 until cnt) {
            val col = i % 5
            val row = i / 5
            val cx = jx - jw / 2f + 9f + col * 11.5f + (row % 2) * 4f
            val cy = bottom - 8f - row * 11f
            drawCircle(Color(CANDY_COLORS[(i * 3) % CANDY_COLORS.size]), 6f, Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = 0.4f), 2f, Offset(cx - 2f, cy - 2f))
        }
    }
    drawRoundRect(Color(0x33B3E5FC), Offset(jx - jw / 2f, top + 14f), Size(jw, bottom - top - 14f), CornerRadius(12f))
    drawRoundRect(Color(0xCCFFFFFF), Offset(jx - jw / 2f, top + 14f), Size(jw, bottom - top - 14f), CornerRadius(12f), style = Stroke(3f))
    drawRoundRect(Color(0xFFE91E63), Offset(jx - jw / 2f - 3f, top + 4f), Size(jw + 6f, 12f), CornerRadius(5f))
    drawRect(Color.White.copy(alpha = 0.5f), Offset(jx - jw / 2f + 6f, top + 24f), Size(4f, bottom - top - 40f))
}

private fun DrawScope.drawStick(w: PinataWorld) {
    if (w.swingT >= 1.15f) return
    val tgt = Offset(w.tapX, w.tapY)
    val q = Offset(w.stickQx, w.stickQy)
    val aT = atan2(tgt.y - q.y, tgt.x - q.x)
    val t = w.swingT
    val wind = 1.25f * (-w.stickSide) * -1f
    val ang = when {
        t < 0.35f -> {
            val u = (t / 0.35f)
            aT + wind * (1f - u * u)
        }
        else -> aT - wind * 0.12f * ((t - 0.35f) / 0.65f)
    }
    val alpha = if (t < 0.8f) 1f else (1.15f - t) / 0.35f
    val len = 270f
    val dx = cos(ang)
    val dy = sin(ang)
    val segs = 9
    for (i in 0 until segs) {
        val a = Offset(q.x + dx * len * i / segs, q.y + dy * len * i / segs)
        val b = Offset(q.x + dx * len * (i + 1) / segs, q.y + dy * len * (i + 1) / segs)
        val wd = 9f + 8f * (i / segs.toFloat())
        drawLine(if (i % 2 == 0) Color(0xFFE53935).copy(alpha = alpha) else Color.White.copy(alpha = alpha), a, b, wd, StrokeCap.Butt)
    }
    drawCircle(Color(0xFFFFD54F).copy(alpha = alpha), 8f, q)
    // «вжух»-след при замахе
    if (t in 0.1f..0.45f) {
        drawArc(
            Color.White.copy(alpha = 0.35f * alpha), Math.toDegrees(aT.toDouble()).toFloat() - 40f * (-w.stickSide) , 40f * (-w.stickSide), false,
            Offset(q.x - len, q.y - len), Size(len * 2f, len * 2f), style = Stroke(10f, cap = StrokeCap.Round)
        )
    }
}
