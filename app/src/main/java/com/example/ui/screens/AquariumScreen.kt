package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.R
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.SoundManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * «Аквариум» — комната для самых маленьких (рыбки из набора Kenney, CC0).
 *
 * • Короткий тап по рыбке — она пугается и стремглав уплывает (пузырьки!), потом возвращается.
 * • Тап по пузырьку — он лопается. Тап по пустой воде — взлетает стайка пузырьков.
 * • Если держать палец на воде, рыбки любопытно подплывают и водят хоровод вокруг пальца.
 * • Иглобрюх при тапе надувается шариком.
 * • Каждые несколько испугов в аквариуме появляется новая рыбка.
 * Никаких очков, проигрышей и текста.
 */

private class AqKind(val res: Int, val widthDp: Float, val speedDp: Float, val puffer: Boolean = false)

private class AqFish(val kind: Int, var x: Float, var y: Float) {
    var vx = 0f
    var vy = 0f
    var tx = x
    var ty = y
    var retarget = 0f
    var scared = 0f
    var puff = 0f
    var face = 1f
    val phase0 = Random.nextFloat() * 6.28f
    var phase = phase0
    val slot = Random.nextFloat() * 6.28f
    val ringR = 0.7f + Random.nextFloat() * 0.9f
}

private class AqBubble(var x: Float, var y: Float, val r: Float, val vy: Float, val wob: Float) { var t = 0f }
private class AqPop(val x: Float, val y: Float, val r: Float) { var t = 0f }

private class AquariumWorld(val density: Float) {
    var w = 0f
    var h = 0f
    var time = 0f
    val fish = ArrayList<AqFish>()
    val bubbles = ArrayList<AqBubble>()
    val pops = ArrayList<AqPop>()
    var calling = false
    var touchX = 0f
    var touchY = 0f
    var scares = 0
    var bubbleTimer = 0f
    var onEvent: (String) -> Unit = {}

    val kinds = listOf(
        AqKind(0, 120f, 70f),            // зелёная
        AqKind(0, 90f, 95f),             // розовая
        AqKind(0, 120f, 60f),            // синяя
        AqKind(0, 112f, 55f),            // красная
        AqKind(0, 104f, 80f),            // оранжевая
        AqKind(0, 140f, 50f),            // серая
        AqKind(0, 110f, 40f, puffer = true),
        AqKind(0, 190f, 45f)             // угорь
    )

    private val waterTop get() = h * 0.10f
    private val waterBottom get() = h - 150f * density

    fun init(width: Float, height: Float) {
        if (w == width && h == height) return
        val first = fish.isEmpty()
        w = width; h = height
        if (first) {
            for (k in intArrayOf(0, 1, 2, 4, 3, 6)) addFish(k, spawnInside = true)
        }
    }

    fun addFish(kind: Int, spawnInside: Boolean) {
        val x = if (spawnInside) w * (0.15f + Random.nextFloat() * 0.7f) else -60f * density
        val y = waterTop + (waterBottom - waterTop) * (0.1f + Random.nextFloat() * 0.8f)
        val f = AqFish(kind, x, y)
        f.face = if (Random.nextBoolean()) 1f else -1f
        fish.add(f)
    }

    private fun halfSize(f: AqFish): Float = kinds[f.kind].widthDp * density * 0.5f * (1f + 0.7f * f.puff)

    private fun burst(x: Float, y: Float, n: Int) {
        repeat(n) {
            bubbles.add(
                AqBubble(
                    x + (Random.nextFloat() - 0.5f) * 40f * density, y + (Random.nextFloat() - 0.5f) * 30f * density,
                    (6f + Random.nextFloat() * 12f) * density, (60f + Random.nextFloat() * 90f) * density,
                    Random.nextFloat() * 6.28f
                )
            )
        }
    }

    /** Короткий тап. */
    fun tap(x: Float, y: Float) {
        // 1) рыбка
        var hit: AqFish? = null
        var best = Float.MAX_VALUE
        for (f in fish) {
            val d = hypot(f.x - x, f.y - y)
            if (d < halfSize(f) + 24f * density && d < best) { best = d; hit = f }
        }
        if (hit != null) {
            val k = kinds[hit.kind]
            if (k.puffer) {
                hit.puff = 1f
                onEvent("puff")
            } else {
                val dx = hit.x - x
                val dy = hit.y - y
                val len = hypot(dx, dy).coerceAtLeast(1f)
                val sp = (k.speedDp * 9f + 260f) * density
                hit.vx = dx / len * sp
                hit.vy = dy / len * sp * 0.6f - 60f * density
                hit.scared = 1.2f
                onEvent("scare")
            }
            burst(hit.x, hit.y, 6)
            scares++
            if (scares % 8 == 0 && fish.size < 12) {
                addFish(Random.nextInt(kinds.size), spawnInside = false)
                onEvent("newfish")
            }
            return
        }
        // 2) пузырёк
        for (b in bubbles) {
            if (hypot(b.x - x, b.y - y) < b.r + 26f * density) {
                bubbles.remove(b)
                pops.add(AqPop(b.x, b.y, b.r))
                onEvent("pop")
                return
            }
        }
        // 3) пустая вода — стайка пузырьков
        burst(x, y, 5)
        onEvent("bubbles")
    }

    fun step(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0f, 0.05f)
        time += dt
        val top = waterTop
        val bottom = waterBottom
        for (f in fish) {
            val k = kinds[f.kind]
            f.phase += dt * (5f + k.speedDp * 0.05f)
            if (f.puff > 0f) f.puff = max(0f, f.puff - dt * 0.55f)
            if (f.scared > 0f) {
                f.scared -= dt
                val damp = 1f - min(1f, dt * 1.6f)
                f.vx *= damp; f.vy *= damp
            } else {
                var mult = 1f
                if (calling && hypot(touchX - f.x, touchY - f.y) < max(w, h) * 0.9f) {
                    val r = (70f + 40f * f.ringR) * density
                    val a = f.slot + time * 1.2f * (if (f.slot > 3.14f) 1f else -1f)
                    f.tx = touchX + cos(a) * r
                    f.ty = touchY + sin(a) * r * 0.7f
                    mult = 2.4f
                } else {
                    f.retarget -= dt
                    if (f.retarget <= 0f || hypot(f.tx - f.x, f.ty - f.y) < 30f * density) {
                        f.tx = w * (0.08f + Random.nextFloat() * 0.84f)
                        f.ty = top + (bottom - top) * Random.nextFloat()
                        f.retarget = 3f + Random.nextFloat() * 4f
                    }
                }
                if (f.puff > 0.2f) mult = 0.15f
                val dx = f.tx - f.x
                val dy = f.ty - f.y
                val len = hypot(dx, dy).coerceAtLeast(1f)
                val sp = k.speedDp * density * mult * (if (len < 60f * density) len / (60f * density) + 0.2f else 1f)
                val dvx = dx / len * sp
                val dvy = dy / len * sp
                val a = min(1f, dt * 2.5f)
                f.vx += (dvx - f.vx) * a
                f.vy += (dvy - f.vy) * a
            }
            f.x += f.vx * dt
            f.y += f.vy * dt
            val m = 40f * density
            if (f.scared > 0f) {
                if (f.x < m) { f.x = m; f.vx = abs(f.vx) }
                if (f.x > w - m) { f.x = w - m; f.vx = -abs(f.vx) }
            } else if (f.x < -200f * density || f.x > w + 200f * density) {
                // новая рыбка плывёт со стороны экрана к цели
            }
            if (f.y < top) { f.y = top; f.vy = abs(f.vy) }
            if (f.y > bottom) { f.y = bottom; f.vy = -abs(f.vy) }
            if (abs(f.vx) > 12f * density) {
                val s = if (f.vx > 0f) 1f else -1f
                f.face += (s - f.face) * min(1f, dt * 8f)
            }
        }
        // пузырьки
        bubbleTimer -= dt
        if (bubbleTimer <= 0f) {
            bubbleTimer = 0.5f + Random.nextFloat() * 0.8f
            bubbles.add(
                AqBubble(
                    w * (0.1f + Random.nextFloat() * 0.8f), h - 100f * density,
                    (5f + Random.nextFloat() * 11f) * density, (40f + Random.nextFloat() * 50f) * density,
                    Random.nextFloat() * 6.28f
                )
            )
        }
        val bi = bubbles.iterator()
        while (bi.hasNext()) {
            val b = bi.next()
            b.t += dt
            b.y -= b.vy * dt
            b.x += sin(b.t * 2.2f + b.wob) * 18f * density * dt
            if (b.y < -20f * density) bi.remove()
        }
        val pi = pops.iterator()
        while (pi.hasNext()) {
            val p = pi.next()
            p.t += dt
            if (p.t > 0.35f) pi.remove()
        }
    }
}

@Composable
fun AquariumScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val density = LocalDensity.current.density

    val fishImgs = listOf(
        ImageBitmap.imageResource(R.drawable.aq_fish_green),
        ImageBitmap.imageResource(R.drawable.aq_fish_pink),
        ImageBitmap.imageResource(R.drawable.aq_fish_blue),
        ImageBitmap.imageResource(R.drawable.aq_fish_red),
        ImageBitmap.imageResource(R.drawable.aq_fish_orange),
        ImageBitmap.imageResource(R.drawable.aq_fish_grey),
        ImageBitmap.imageResource(R.drawable.aq_fish_puffer),
        ImageBitmap.imageResource(R.drawable.aq_fish_eel)
    )
    val bubbleA = ImageBitmap.imageResource(R.drawable.aq_bubble_a)
    val bubbleC = ImageBitmap.imageResource(R.drawable.aq_bubble_c)
    val plants = listOf(
        ImageBitmap.imageResource(R.drawable.aq_plant_pink),
        ImageBitmap.imageResource(R.drawable.aq_plant_green),
        ImageBitmap.imageResource(R.drawable.aq_plant_green2),
        ImageBitmap.imageResource(R.drawable.aq_plant_orange)
    )
    val rocks = listOf(
        ImageBitmap.imageResource(R.drawable.aq_rock_a),
        ImageBitmap.imageResource(R.drawable.aq_rock_b)
    )
    val sandTop = ImageBitmap.imageResource(R.drawable.aq_sand_top)
    val sand = ImageBitmap.imageResource(R.drawable.aq_sand)

    val world = remember { AquariumWorld(density) }
    var tick by remember { mutableIntStateOf(0) }
    var lastCall = remember { longArrayOf(0L) }

    world.onEvent = { ev ->
        when (ev) {
            "scare" -> {
                soundPlayer.playPop(0.9f + Random.nextFloat() * 0.5f)
                soundPlayer.vibrateDragTick()
            }
            "puff" -> {
                soundManager.play("boing", volume = 0.7f)
                soundPlayer.vibrateSquishDeform()
            }
            "pop" -> soundPlayer.playPop(1.5f + Random.nextFloat() * 0.5f)
            "bubbles" -> soundPlayer.playPop(1.1f)
            "newfish" -> soundPlayer.playSparkleChime()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF3498DB))
            .testTag("aquarium_root")
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val t0 = down.uptimeMillis
                    world.touchX = down.position.x
                    world.touchY = down.position.y
                    var longPress = false
                    do {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                        world.touchX = c.position.x
                        world.touchY = c.position.y
                        val moved = hypot(c.position.x - down.position.x, c.position.y - down.position.y) > 24f * density
                        if (!longPress && c.pressed && (c.uptimeMillis - t0 > 260L || moved)) {
                            longPress = true
                            world.calling = true
                            val now = System.currentTimeMillis()
                            if (now - lastCall[0] > 1500L) { lastCall[0] = now; soundPlayer.playChime(1.6f) }
                        }
                        c.consume()
                    } while (c.pressed)
                    if (longPress) world.calling = false else world.tap(down.position.x, down.position.y)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val redraw = tick
            world.init(size.width, size.height)
            drawAquarium(world, fishImgs, bubbleA, bubbleC, plants, rocks, sandTop, sand, density)
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                world.step(dt)
                tick++
            }
        }
    }
}

private fun DrawScope.drawImg(img: ImageBitmap, cx: Float, bottomOrCy: Float, widthPx: Float, centered: Boolean, alpha: Float = 1f) {
    val hh = widthPx * img.height / img.width
    val top = if (centered) bottomOrCy - hh / 2f else bottomOrCy - hh
    drawImage(
        img,
        dstOffset = IntOffset((cx - widthPx / 2f).roundToInt(), top.roundToInt()),
        dstSize = IntSize(widthPx.roundToInt(), hh.roundToInt()),
        alpha = alpha
    )
}

private fun DrawScope.drawAquarium(
    w: AquariumWorld,
    fishImgs: List<ImageBitmap>,
    bubbleA: ImageBitmap,
    bubbleC: ImageBitmap,
    plants: List<ImageBitmap>,
    rocks: List<ImageBitmap>,
    sandTop: ImageBitmap,
    sand: ImageBitmap,
    density: Float
) {
    val sw = size.width
    val sh = size.height
    val t = w.time
    // вода
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF6EC6F5), Color(0xFF3498DB), Color(0xFF1B5E9E)), startY = 0f, endY = sh),
        size = Size(sw, sh)
    )
    // лучи света
    for (i in 0 until 4) {
        val x0 = sw * (0.1f + i * 0.26f) + sin(t * 0.3f + i) * 20f * density
        val path = Path().apply {
            moveTo(x0, 0f); lineTo(x0 + 50f * density, 0f)
            lineTo(x0 + 190f * density, sh * 0.85f); lineTo(x0 + 60f * density, sh * 0.85f); close()
        }
        drawPath(path, Color.White.copy(alpha = 0.06f + 0.02f * sin(t * 0.7f + i)))
    }

    val tile = 56f * density
    val sandY = sh - 2f * tile
    // задние водоросли и камни
    val backPlants = floatArrayOf(0.06f, 0.30f, 0.58f, 0.86f)
    for ((i, px) in backPlants.withIndex()) {
        val img = plants[i % 3]
        val pw = (70f + 18f * (i % 2)) * density
        val bx = sw * px
        rotate(sin(t * 0.9f + i * 1.7f) * 3f, Offset(bx, sandY + 6f * density)) {
            drawImg(img, bx, sandY + 6f * density, pw, false, 0.8f)
        }
    }
    drawImg(rocks[0], sw * 0.78f, sandY + 8f * density, 110f * density, false)
    drawImg(rocks[1], sw * 0.17f, sandY + 8f * density, 100f * density, false)

    // рыбки
    for (f in w.fish) {
        val k = w.kinds[f.kind]
        val img = fishImgs[f.kind]
        val wpx = k.widthDp * density * (1f + 0.7f * f.puff)
        val wag = sin(f.phase) * (if (f.scared > 0f) 12f else 5f)
        val shake = if (f.puff > 0.3f) sin(t * 40f) * 2f * density else 0f
        rotate(wag, Offset(f.x, f.y)) {
            scale(f.face, 1f, Offset(f.x, f.y)) {
                drawImg(img, f.x + shake, f.y, wpx, true)
            }
        }
        if (f.scared > 0.8f) {
            // «испуг»: маленькие капельки у рыбки
            drawCircle(Color.White.copy(alpha = 0.7f), 3f * density, Offset(f.x - f.face * wpx * 0.2f, f.y - wpx * 0.25f))
            drawCircle(Color.White.copy(alpha = 0.7f), 2.2f * density, Offset(f.x - f.face * wpx * 0.3f, f.y - wpx * 0.35f))
        }
    }

    // пузырьки
    for (b in w.bubbles) {
        val img = if (b.r > 9f * density) bubbleC else bubbleA
        drawImg(img, b.x, b.y, b.r * 2.4f, true, 0.85f)
    }
    for (p in w.pops) {
        val k = p.t / 0.35f
        drawCircle(Color.White.copy(alpha = 0.8f * (1f - k)), p.r * (1.4f + 1.8f * k), Offset(p.x, p.y), style = Stroke(width = 2.5f * density))
    }

    // песок
    val cols = (sw / tile).toInt() + 2
    for (c in 0 until cols) {
        val x = c * tile
        drawImage(
            sandTop, dstOffset = IntOffset(x.roundToInt(), sandY.roundToInt()),
            dstSize = IntSize(tile.roundToInt() + 1, tile.roundToInt() + 1)
        )
        drawImage(
            sand, dstOffset = IntOffset(x.roundToInt(), (sandY + tile).roundToInt()),
            dstSize = IntSize(tile.roundToInt() + 1, tile.roundToInt() + 2)
        )
    }
    // передние водоросли
    val front = floatArrayOf(0.02f, 0.44f, 0.93f)
    for ((i, px) in front.withIndex()) {
        val img = plants[(i + 1) % 4]
        val bx = sw * px
        rotate(sin(t * 1.1f + i * 2.3f) * 4f, Offset(bx, sandY + 10f * density)) {
            drawImg(img, bx, sandY + 10f * density, 64f * density, false)
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = PI
}
