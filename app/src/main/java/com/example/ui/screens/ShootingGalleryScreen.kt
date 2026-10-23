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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.example.shared.LocalAgeProfile
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
 * «Тир» — весёлый тир из набора Kenney Shooting Gallery (CC0).
 *
 * Три ряда плывут навстречу друг другу: утята и мишени на палочках. Тап — «выстрел»: попал — игрушка
 * переворачивается, утёнок крякает, идут очки. Каждые 10 попаданий — праздник и ряды едут быстрее.
 * Без проигрыша и патронов: стреляй сколько хочешь.
 */

private class GalKind(val front: Int, val back: Int, val isDuck: Boolean)

private class GalObj(var kind: Int, var x: Float) {
    var hitT = -1f   // <0 — не сбита
    val phase = Random.nextFloat() * 6.28f
}

private class GalShot(val x: Float, val y: Float, val hit: Boolean) { var t = 0f }
private class GalPlus(val x: Float, val y: Float) { var t = 0f }
private class GalSpark(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Color, val r: Float) { var life = 1f }

private class GalleryWorld(val density: Float, val toddler: Boolean) {
    var w = 0f
    var h = 0f
    var time = 0f
    var score = 0
    var level = 0
    var banner = 0f      // сколько секунд ещё показывать «GO!»
    var bannerReady = 1.1f
    val lanes = Array(3) { ArrayList<GalObj>() }
    val shots = ArrayList<GalShot>()
    val holes = ArrayList<GalShot>()
    val pluses = ArrayList<GalPlus>()
    val sparks = ArrayList<GalSpark>()
    var onEvent: (String, Int) -> Unit = { _, _ -> }
    val kinds = listOf(
        GalKind(0, 0, true), GalKind(1, 0, true), GalKind(2, 0, true), GalKind(3, 0, true), GalKind(4, 0, true),
        GalKind(5, 1, false), GalKind(6, 1, false), GalKind(7, 1, false), GalKind(8, 1, false)
    )
    private val laneKinds = arrayOf(intArrayOf(0, 1, 2, 3), intArrayOf(3, 4, 5, 6, 0), intArrayOf(5, 6, 7, 8, 4))
    private val laneSpeed = floatArrayOf(55f, 75f, 100f)
    private val laneDir = floatArrayOf(1f, -1f, 1f)

    val objW get() = 80f * density * (if (toddler) 1.15f else 1f)
    fun laneTop(i: Int) = h * (0.36f + 0.145f * i)
    private val perLane get() = if (toddler) 2 else 3
    private fun speedMul() = (if (toddler) 0.7f else 1f) * (1f + 0.18f * min(level, 5))

    fun init(width: Float, height: Float) {
        if (w == width && h == height) return
        val first = lanes[0].isEmpty()
        w = width; h = height
        if (first) {
            for (i in 0 until 3) {
                val n = perLane
                val span = w + 2f * objW
                for (j in 0 until n) {
                    val k = laneKinds[i][Random.nextInt(laneKinds[i].size)]
                    val x = if (laneDir[i] > 0f) -objW + span / n * j else w + objW - span / n * j
                    lanes[i].add(GalObj(k, x))
                }
            }
        }
    }

    private fun respawn(o: GalObj, lane: Int) {
        o.kind = laneKinds[lane][Random.nextInt(laneKinds[lane].size)]
        o.hitT = -1f
        o.x = if (laneDir[lane] > 0f) -objW - Random.nextFloat() * objW else w + objW + Random.nextFloat() * objW
    }

    fun bodyCenter(o: GalObj, lane: Int): Offset {
        val bob = sin(time * 3f + o.phase) * 3f * density
        var y = laneTop(lane) - objW * 0.62f - 14f * density + bob
        if (o.hitT > 0.9f) y += (o.hitT - 0.9f) * 520f * density
        return Offset(o.x, y)
    }

    private fun confetti(x: Float, y: Float, n: Int) {
        val cols = listOf(Color(0xFFFFD54F), Color(0xFFFF8A65), Color(0xFF4FC3F7), Color(0xFFAED581), Color(0xFFF06292))
        repeat(n) {
            val a = Random.nextFloat() * 6.28f
            val s = (80f + Random.nextFloat() * 260f) * density
            sparks.add(GalSpark(x, y, cos(a) * s, sin(a) * s - 120f * density, cols.random(), (3f + Random.nextFloat() * 4f) * density))
        }
    }

    fun tap(x: Float, y: Float) {
        val rad = objW * 0.66f * (if (toddler) 1.2f else 1f)
        var best: GalObj? = null
        var bestLane = 0
        var bestD = Float.MAX_VALUE
        for (li in 2 downTo 0) {
            for (o in lanes[li]) {
                if (o.hitT >= 0f) continue
                val c = bodyCenter(o, li)
                val d = hypot(c.x - x, c.y - y)
                if (d < rad && d < bestD) { bestD = d; best = o; bestLane = li }
            }
        }
        if (best != null) {
            best.hitT = 0f
            score++
            shots.add(GalShot(x, y, true))
            pluses.add(GalPlus(x, y))
            val k = kinds[best.kind]
            confetti(x, y, 8)
            onEvent(if (k.isDuck) "hitDuck" else "hitTarget", score)
            if (score % 10 == 0) {
                level++
                banner = 1.3f
                val c = bodyCenter(best, bestLane)
                confetti(w / 2f, h * 0.3f, 36)
                onEvent("level", score)
                @Suppress("UNUSED_VARIABLE") val unused = c
            }
        } else {
            shots.add(GalShot(x, y, false))
            if (y < laneTop(0) - 20f * density) holes.add(GalShot(x, y, false))
            if (holes.size > 14) holes.removeAt(0)
            onEvent("miss", score)
        }
    }

    fun step(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0f, 0.05f)
        time += dt
        if (bannerReady > 0f) {
            bannerReady -= dt
            if (bannerReady <= 0f) banner = 1.0f
        }
        if (banner > 0f && bannerReady <= 0f) banner -= dt
        for (li in 0 until 3) {
            for (o in lanes[li]) {
                if (o.hitT >= 0f) {
                    o.hitT += dt
                    o.x += laneDir[li] * laneSpeed[li] * speedMul() * density * dt * 0.3f
                    if (o.hitT > 1.4f) respawn(o, li)
                } else {
                    o.x += laneDir[li] * laneSpeed[li] * speedMul() * density * dt
                    if (laneDir[li] > 0f && o.x > w + objW) respawn(o, li)
                    if (laneDir[li] < 0f && o.x < -objW) respawn(o, li)
                }
            }
        }
        val si = shots.iterator()
        while (si.hasNext()) { val s = si.next(); s.t += dt; if (s.t > 0.45f) si.remove() }
        val hi = holes.iterator()
        while (hi.hasNext()) { val s = hi.next(); s.t += dt; if (s.t > 5f) hi.remove() }
        val pi = pluses.iterator()
        while (pi.hasNext()) { val s = pi.next(); s.t += dt; if (s.t > 0.9f) pi.remove() }
        val ki = sparks.iterator()
        while (ki.hasNext()) {
            val p = ki.next()
            p.x += p.vx * dt; p.y += p.vy * dt
            p.vy += 700f * density * dt
            p.life -= dt * 1.1f
            if (p.life <= 0f) ki.remove()
        }
    }
}

@Composable
fun ShootingGalleryScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val ageProfile = LocalAgeProfile.current
    val density = LocalDensity.current.density

    val front = listOf(
        ImageBitmap.imageResource(R.drawable.sg_duck_yellow),
        ImageBitmap.imageResource(R.drawable.sg_duck_white),
        ImageBitmap.imageResource(R.drawable.sg_duck_brown),
        ImageBitmap.imageResource(R.drawable.sg_duck_target_yellow),
        ImageBitmap.imageResource(R.drawable.sg_duck_target_brown),
        ImageBitmap.imageResource(R.drawable.sg_target_red1),
        ImageBitmap.imageResource(R.drawable.sg_target_red2),
        ImageBitmap.imageResource(R.drawable.sg_target_colored),
        ImageBitmap.imageResource(R.drawable.sg_target_white)
    )
    val backs = listOf(
        ImageBitmap.imageResource(R.drawable.sg_duck_back),
        ImageBitmap.imageResource(R.drawable.sg_target_back)
    )
    val stick = ImageBitmap.imageResource(R.drawable.sg_stick)
    val crossWhite = ImageBitmap.imageResource(R.drawable.sg_cross_white)
    val crossRed = ImageBitmap.imageResource(R.drawable.sg_cross_red)
    val hole = ImageBitmap.imageResource(R.drawable.sg_hole)
    val ready = ImageBitmap.imageResource(R.drawable.sg_ready)
    val go = ImageBitmap.imageResource(R.drawable.sg_go)
    val iconDuck = ImageBitmap.imageResource(R.drawable.sg_icon_duck)
    val plus = ImageBitmap.imageResource(R.drawable.sg_plus)
    val digits = listOf(
        ImageBitmap.imageResource(R.drawable.sg_d0), ImageBitmap.imageResource(R.drawable.sg_d1),
        ImageBitmap.imageResource(R.drawable.sg_d2), ImageBitmap.imageResource(R.drawable.sg_d3),
        ImageBitmap.imageResource(R.drawable.sg_d4), ImageBitmap.imageResource(R.drawable.sg_d5),
        ImageBitmap.imageResource(R.drawable.sg_d6), ImageBitmap.imageResource(R.drawable.sg_d7),
        ImageBitmap.imageResource(R.drawable.sg_d8), ImageBitmap.imageResource(R.drawable.sg_d9)
    )
    val straps = GalleryArt(
        water = listOf(ImageBitmap.imageResource(R.drawable.sg_water1), ImageBitmap.imageResource(R.drawable.sg_water2)),
        grass = listOf(ImageBitmap.imageResource(R.drawable.sg_grass1), ImageBitmap.imageResource(R.drawable.sg_grass2)),
        wood = ImageBitmap.imageResource(R.drawable.sg_bg_wood),
        oak = ImageBitmap.imageResource(R.drawable.sg_tree_oak),
        pine = ImageBitmap.imageResource(R.drawable.sg_tree_pine),
        clouds = listOf(ImageBitmap.imageResource(R.drawable.sg_cloud1), ImageBitmap.imageResource(R.drawable.sg_cloud2)),
        curtain = ImageBitmap.imageResource(R.drawable.sg_curtain),
        valance = ImageBitmap.imageResource(R.drawable.sg_valance)
    )

    val world = remember { GalleryWorld(density, ageProfile.isToddler2) }
    var tick by remember { mutableIntStateOf(0) }

    world.onEvent = { ev, score ->
        when (ev) {
            "hitDuck" -> {
                soundManager.play(if (Random.nextBoolean()) "animal_duck_1" else "animal_duck_2")
                soundPlayer.playPop(1.3f)
                soundPlayer.vibrateSquishDeform()
            }
            "hitTarget" -> {
                soundPlayer.playChime(1.0f + (score % 5) * 0.12f)
                soundPlayer.playPop(1.4f)
                soundPlayer.vibrateSquishDeform()
            }
            "miss" -> soundPlayer.playPop(0.55f)
            "level" -> soundPlayer.playFanfare()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF9B6B43))
            .testTag("shooting_gallery_root")
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    world.tap(down.position.x, down.position.y)
                    do {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                    } while (e.changes.any { it.pressed })
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val redraw = tick
            world.init(size.width, size.height)
            drawGallery(world, front, backs, stick, straps, crossWhite, crossRed, hole, ready, go, iconDuck, plus, digits, density)
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

private class GalleryArt(
    val water: List<ImageBitmap>,
    val grass: List<ImageBitmap>,
    val wood: ImageBitmap,
    val oak: ImageBitmap,
    val pine: ImageBitmap,
    val clouds: List<ImageBitmap>,
    val curtain: ImageBitmap,
    val valance: ImageBitmap
)

private fun DrawScope.img(
    im: ImageBitmap, x: Float, y: Float, wPx: Float, hPx: Float, alpha: Float = 1f
) {
    drawImage(
        im,
        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
        dstSize = IntSize(max(1, wPx.roundToInt()), max(1, hPx.roundToInt())),
        alpha = alpha
    )
}

private fun DrawScope.imgC(im: ImageBitmap, cx: Float, cy: Float, wPx: Float, alpha: Float = 1f) {
    val hh = wPx * im.height / im.width
    img(im, cx - wPx / 2f, cy - hh / 2f, wPx, hh, alpha)
}

private fun DrawScope.drawGallery(
    w: GalleryWorld,
    front: List<ImageBitmap>,
    backs: List<ImageBitmap>,
    stick: ImageBitmap,
    art: GalleryArt,
    crossWhite: ImageBitmap,
    crossRed: ImageBitmap,
    hole: ImageBitmap,
    ready: ImageBitmap,
    go: ImageBitmap,
    iconDuck: ImageBitmap,
    plus: ImageBitmap,
    digits: List<ImageBitmap>,
    d: Float
) {
    val sw = size.width
    val sh = size.height
    val t = w.time

    // деревянная стена
    val tile = 128f * d
    var yy = 0f
    while (yy < sh) {
        var xx = 0f
        while (xx < sw) {
            img(art.wood, xx, yy, tile + 1f, tile + 1f)
            xx += tile
        }
        yy += tile
    }
    // облака и деревья на стене
    for ((i, c) in art.clouds.withIndex()) {
        val span = sw + 200f * d
        val cx = ((t * (10f + 6f * i) * d + i * span * 0.55f) % span) - 100f * d
        imgC(c, cx, sh * (0.2f + 0.05f * i), 110f * d)
    }
    imgC(art.oak, sw * 0.14f, w.laneTop(0) - 70f * d, 90f * d)
    imgC(art.pine, sw * 0.88f, w.laneTop(0) - 80f * d, 84f * d)

    // отверстия от «выстрелов» в стене
    for (hs in w.holes) imgC(hole, hs.x, hs.y, 22f * d, (1f - hs.t / 5f).coerceIn(0f, 1f))

    // ряды: сначала игрушки, потом волна поверх них
    for (li in 0 until 3) {
        val top = w.laneTop(li)
        for (o in w.lanes[li]) {
            val c = w.bodyCenter(o, li)
            val k = w.kinds[o.kind]
            val ow = w.objW
            // палочка
            imgC(stick, c.x, c.y + ow * 0.55f, ow * 0.26f)
            // тело
            var sx = 1f
            var im = front[k.front]
            if (o.hitT >= 0f) {
                val f = (o.hitT / 0.3f).coerceIn(0f, 1f)
                sx = cos(f * PI.toFloat())
                if (sx < 0f) im = backs[k.back]
            }
            scale(scaleX = if (abs(sx) < 0.05f) 0.05f else sx, scaleY = 1f, pivot = c) {
                imgC(im, c.x, c.y, ow)
            }
        }
        // волна / трава ряда
        val strip = when (li) { 0 -> art.grass[1]; 1 -> art.water[0]; else -> art.water[1] }
        val stripH = sh - top + 10f * d
        val sc = (sh * 0.5f) / strip.height
        val tw = strip.width * sc
        val shift = if (li == 0) 0f else (t * 14f * d * (if (li == 1) -1f else 1f)) % tw
        var x = -tw + shift
        val bob = if (li == 0) 0f else sin(t * 1.6f + li) * 3f * d
        while (x < sw + tw) {
            img(strip, x, top + bob, tw + 1f, stripH)
            x += tw
        }
    }

    // шкурки: «+1» и перекрестия
    for (p in w.pluses) {
        val k = p.t / 0.9f
        val a = (1f - k).coerceIn(0f, 1f)
        val yp = p.y - 30f * d - k * 60f * d
        imgC(plus, p.x - 18f * d, yp, 28f * d, a)
        imgC(digits[1], p.x + 6f * d, yp, 24f * d, a)
    }
    for (s in w.shots) {
        val k = s.t / 0.45f
        val sz = (46f + 30f * (1f - k)) * d
        imgC(if (s.hit) crossRed else crossWhite, s.x, s.y, sz, (1f - k).coerceIn(0f, 1f))
    }
    for (p in w.sparks) {
        drawCircle(p.color.copy(alpha = p.life.coerceIn(0f, 1f)), p.r, Offset(p.x, p.y))
    }

    // занавес: сверху и по бокам
    run {
        var x = 0f
        val vh = 56f * d
        val vw = art.valance.width * vh / art.valance.height
        while (x < sw) { img(art.valance, x, -4f * d, vw + 1f, vh); x += vw }
    }
    val cw = 62f * d
    val ch = cw * art.curtain.height / art.curtain.width
    img(art.curtain, 0f, 0f, cw, ch)
    scale(scaleX = -1f, scaleY = 1f, pivot = Offset(sw / 2f, 0f)) { img(art.curtain, 0f, 0f, cw, ch) }

    // счёт: утёнок + цифры по центру сверху
    val digs = w.score.toString().map { it - '0' }
    val dh = 46f * d
    val dw = dh * digits[0].width / digits[0].height
    val totalW = 44f * d + digs.size * dw * 0.9f
    var cx = sw / 2f - totalW / 2f
    imgC(iconDuck, cx + 20f * d, 70f * d, 42f * d)
    cx += 44f * d
    for (dg in digs) {
        img(digits[dg], cx, 70f * d - dh / 2f, dw, dh)
        cx += dw * 0.9f
    }

    // «READY?» → «GO!»
    if (w.bannerReady > 0f) {
        val k = 1f + 0.05f * sin(t * 10f)
        imgC(ready, sw / 2f, sh * 0.2f, 220f * d * k)
    } else if (w.banner > 0f) {
        val k = 1f + 0.12f * sin(t * 14f)
        imgC(go, sw / 2f, sh * 0.2f, 170f * d * k, min(1f, w.banner * 2f))
    }
}
