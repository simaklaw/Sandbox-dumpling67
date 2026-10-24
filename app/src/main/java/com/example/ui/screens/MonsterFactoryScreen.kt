package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * «Фабрика монстриков» — собери своего монстрика из запчастей (Kenney Monster Builder, CC0).
 *
 * • Тап по части монстрика — она меняется: глаза, рот, нос, рожки/ушки, руки, ноги, туловище.
 * • Кнопки сбоку: 🎨 цвет, 👀 число глаз, 🎲 случайный, 🎵 танец, ⭐ сохранить в дом, 🏠 дом монстриков.
 * • Угощения снизу перетаскиваются монстрику в рот: хрум-хрум!
 * • В «Доме» живут все сохранённые монстрики, их можно позвать обратно или устроить вечеринку.
 */

private const val MON_BODIES = 6
private const val MON_ARMS = 5
private const val MON_LEGS = 5
private const val MON_DETAIL = 8   // 0 — нет, 1..7 — виды
private const val MON_NOSES = 5    // 0 — нет, 1..4 — виды
private const val MON_MAX_HOUSE = 8

internal data class MonSpec(
    val body: Int = 0, val color: Int = 2, val arm: Int = 1, val leg: Int = 1,
    val eyes: Int = 1, val eyeCount: Int = 1, val mouth: Int = 0, val nose: Int = 0, val detail: Int = 0
) {
    fun encode() = listOf(body, color, arm, leg, eyes, eyeCount, mouth, nose, detail).joinToString(",")

    companion object {
        fun decode(s: String): MonSpec? = try {
            val p = s.split(",").map { it.toInt() }
            MonSpec(
                p[0].mod(MON_BODIES), p[1].mod(6), p[2].mod(MON_ARMS), p[3].mod(MON_LEGS),
                p[4].mod(MonsterArt.EYES.size), p[5].coerceIn(1, 3), p[6].mod(MonsterArt.MOUTHS.size),
                p[7].mod(MON_NOSES), p[8].mod(MON_DETAIL)
            )
        } catch (e: Exception) { null }

        fun random() = MonSpec(
            Random.nextInt(MON_BODIES), Random.nextInt(6), Random.nextInt(MON_ARMS), Random.nextInt(MON_LEGS),
            Random.nextInt(MonsterArt.EYES.size), 1 + Random.nextInt(3), Random.nextInt(MonsterArt.MOUTHS.size),
            Random.nextInt(MON_NOSES), Random.nextInt(MON_DETAIL)
        )
    }
}

internal fun loadHouse(ctx: Context): List<MonSpec> {
    val s = try { ctx.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE).getString("mb_house", "") ?: "" } catch (e: Exception) { "" }
    return s.split(";").mapNotNull { if (it.isBlank()) null else MonSpec.decode(it) }.take(MON_MAX_HOUSE)
}

internal fun saveHouse(ctx: Context, list: List<MonSpec>) {
    try {
        ctx.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE).edit()
            .putString("mb_house", list.joinToString(";") { it.encode() }).apply()
    } catch (e: Exception) { /* не критично */ }
}

internal class MonImgs(
    val body: ImageBitmap, val arm: ImageBitmap, val leg: ImageBitmap, val eyes: ImageBitmap,
    val mouth: ImageBitmap, val nose: ImageBitmap?, val detail: ImageBitmap?
)

@Composable
internal fun rememberMonImgs(s: MonSpec): MonImgs {
    val body = ImageBitmap.imageResource(MonsterArt.BODY[s.color][s.body])
    val arm = ImageBitmap.imageResource(MonsterArt.ARM[s.color][s.arm])
    val leg = ImageBitmap.imageResource(MonsterArt.LEG[s.color][s.leg])
    val eyes = ImageBitmap.imageResource(MonsterArt.EYES[s.eyes])
    val mouth = ImageBitmap.imageResource(MonsterArt.MOUTHS[s.mouth])
    val nose = if (s.nose > 0) ImageBitmap.imageResource(MonsterArt.NOSES[s.nose - 1]) else null
    val detail = if (s.detail > 0) ImageBitmap.imageResource(MonsterArt.DETAIL[s.detail - 1][s.color]) else null
    return MonImgs(body, arm, leg, eyes, mouth, nose, detail)
}

private enum class MonZone { EYES, MOUTH, NOSE, TOP, ARMS, LEGS, BODY }

internal class MonAnim {
    val pop = FloatArray(7)
    var hop = 0f          // dp
    var hopV = 0f         // dp/s
    var squash = 0f
    var chomp = 0f
    var danceUntil = 0f
    var nextBeat = 0f
    val phase = Random.nextFloat() * 6.28f

    fun jump(power: Float = 520f) { if (hop <= 0.5f) { hopV = power } }

    fun step(dt: Float, time: Float) {
        for (i in pop.indices) pop[i] = max(0f, pop[i] - dt * 3.2f)
        squash = max(0f, squash - dt * 3f)
        chomp = max(0f, chomp - dt * 2.4f)
        if (hopV != 0f || hop > 0f) {
            hop += hopV * dt
            hopV -= 2600f * dt
            if (hop <= 0f) { hop = 0f; hopV = 0f }
        }
        if (time < danceUntil && hop <= 0.5f && time >= nextBeat) {
            hopV = 300f
            nextBeat = time + 0.46f
        }
    }
}

internal class MonLayout(
    val k: Float, val cx: Float, val ground: Float,
    val bw: Float, val bh: Float, val bodyTop: Float, val bodyBottom: Float,
    val legTop: Float, val lw: Float, val lh: Float, val aw: Float, val ah: Float,
    val detailW: Float, val detailH: Float
) {
    val body get() = Rect(cx - bw / 2f, bodyTop, cx + bw / 2f, bodyBottom)
    val legs get() = Rect(cx - (bw * 0.24f + lw / 2f), legTop, cx + (bw * 0.24f + lw / 2f), ground)
    val armsY get() = bodyTop + 0.40f * bh
    val armsRect get() = Rect(cx - bw / 2f - aw * 0.8f, armsY, cx + bw / 2f + aw * 0.8f, armsY + ah)
    val eyesRect get() = Rect(cx - bw * 0.36f, bodyTop + 0.16f * bh, cx + bw * 0.36f, bodyTop + 0.46f * bh)
    val noseRect get() = Rect(cx - bw * 0.12f, bodyTop + 0.46f * bh, cx + bw * 0.12f, bodyTop + 0.60f * bh)
    val mouthRect get() = Rect(cx - bw * 0.26f, bodyTop + 0.60f * bh, cx + bw * 0.26f, bodyTop + 0.82f * bh)
    val topRect get() = Rect(cx - bw * 0.45f, bodyTop - max(detailH, 40f) * 0.9f, cx + bw * 0.45f, bodyTop + 0.14f * bh)
}

/** Подбирает масштаб, чтобы монстрик целиком влез в (availW × availH), и считает раскладку. */
internal fun monLayout(im: MonImgs, cx: Float, ground: Float, availW: Float, availH: Float): MonLayout {
    val bw0 = im.body.width.toFloat(); val bh0 = im.body.height.toFloat()
    val lh0 = im.leg.height.toFloat()
    val dh0 = im.detail?.height?.toFloat() ?: 0f
    val totalH = lh0 * 0.80f + bh0 + dh0 * 0.65f
    val totalW = bw0 + im.arm.width * 1.9f
    val k = min(availH / totalH, availW / totalW)
    val bodyBottom = ground - lh0 * 0.80f * k
    return MonLayout(
        k, cx, ground, bw0 * k, bh0 * k, bodyBottom - bh0 * k, bodyBottom,
        ground - lh0 * k, im.leg.width * k, lh0 * k, im.arm.width * k, im.arm.height * k,
        (im.detail?.width ?: 0) * k, dh0 * k
    )
}

private fun DrawScope.putImg(im: ImageBitmap, left: Float, top: Float, w: Float, h: Float, mirror: Boolean = false) {
    if (mirror) {
        scale(-1f, 1f, Offset(left + w / 2f, top + h / 2f)) {
            drawImage(im, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = IntSize(max(1, w.roundToInt()), max(1, h.roundToInt())))
        }
    } else {
        drawImage(im, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = IntSize(max(1, w.roundToInt()), max(1, h.roundToInt())))
    }
}

internal fun DrawScope.drawMonster(im: MonImgs, spec: MonSpec, lay: MonLayout, a: MonAnim, time: Float, d: Float, lookX: Float) {
    val k = lay.k
    val cx = lay.cx
    val dancing = time < a.danceUntil
    val bob = 1f + 0.014f * sin(time * 2.4f + a.phase)
    val sx = (1f + 0.10f * a.squash) * bob
    val sy = (1f - 0.12f * a.squash) / bob
    val tilt = if (dancing) sin(time * 7f) * 5f else 0f
    val swing = if (dancing) sin(time * 8f) * 35f else sin(time * 1.6f + a.phase) * 3f
    val pivot = Offset(cx, lay.ground)

    withTransform({
        translate(0f, -a.hop * d)
        rotate(tilt, pivot)
        scale(sx, sy, pivot)
    }) {
        fun pop(zone: MonZone, r: Rect, block: DrawScope.() -> Unit) {
            val p = a.pop[zone.ordinal]
            if (p > 0.01f) scale(1f + 0.32f * p, 1f + 0.32f * p, r.center) { block() } else block()
        }
        // ноги
        pop(MonZone.LEGS, lay.legs) {
            for (sgn in intArrayOf(-1, 1)) {
                val lift = if (dancing) max(0f, sin(time * 8f + (if (sgn < 0) 0f else 3.14f))) * 12f * d else 0f
                val x = cx + sgn * lay.bw * 0.24f - lay.lw / 2f
                putImg(im.leg, x, lay.legTop - lift, lay.lw, lay.lh, mirror = sgn < 0)
            }
        }
        // руки (за туловищем)
        pop(MonZone.ARMS, lay.armsRect) {
            for (sgn in intArrayOf(-1, 1)) {
                val x = cx + sgn * (lay.bw * 0.5f - 0.05f * lay.bw) - lay.aw / 2f
                val y = lay.armsY
                val ang = if (sgn < 0) swing else -swing
                rotate(ang, Offset(x + lay.aw / 2f, y + lay.aw * 0.4f)) {
                    putImg(im.arm, x, y, lay.aw, lay.ah, mirror = sgn < 0)
                }
            }
        }
        // рожки / ушки / антенны
        val det = im.detail
        if (det != null) {
            pop(MonZone.TOP, lay.topRect) {
                val dw = det.width * k
                val dh = det.height * k
                if (spec.detail == 7) {
                    putImg(det, cx - dw / 2f, lay.bodyTop - dh * 0.7f, dw, dh)
                } else {
                    for (sgn in intArrayOf(-1, 1)) {
                        val x = cx + sgn * lay.bw * 0.30f - dw / 2f
                        val y = lay.bodyTop - dh * 0.62f
                        rotate(-sgn * 14f + (if (dancing) sin(time * 9f) * 6f else 0f), Offset(x + dw / 2f, y + dh)) {
                            putImg(det, x, y, dw, dh, mirror = sgn < 0)
                        }
                    }
                }
            }
        }
        // туловище
        pop(MonZone.BODY, lay.body) {
            putImg(im.body, cx - lay.bw / 2f, lay.bodyTop, lay.bw, lay.bh)
        }
        // лицо
        val lx = lookX * lay.bw * 0.03f
        pop(MonZone.EYES, lay.eyesRect) {
            val e = im.eyes
            fun eye(wf: Float, x: Float, y: Float) {
                val w = lay.bw * wf
                val h = e.height * w / e.width
                putImg(e, cx + x * lay.bw - w / 2f + lx, lay.bodyTop + y * lay.bh - h / 2f, w, h)
            }
            when (spec.eyeCount) {
                1 -> eye(0.46f, 0f, 0.30f)
                2 -> { eye(0.30f, -0.20f, 0.32f); eye(0.30f, 0.20f, 0.32f) }
                else -> { eye(0.27f, -0.23f, 0.38f); eye(0.27f, 0f, 0.20f); eye(0.27f, 0.23f, 0.38f) }
            }
        }
        val nose = im.nose
        if (nose != null) pop(MonZone.NOSE, lay.noseRect) {
            val w = lay.bw * 0.16f
            val h = nose.height * w / nose.width
            putImg(nose, cx - w / 2f + lx, lay.bodyTop + 0.52f * lay.bh - h / 2f, w, h)
        }
        pop(MonZone.MOUTH, lay.mouthRect) {
            val m = im.mouth
            val w = lay.bw * 0.40f
            val h = m.height * w / m.width
            val ch = 1f + 0.35f * sin(a.chomp * 18f).let { abs(it) } * a.chomp
            scale(1f, ch, Offset(cx, lay.bodyTop + 0.70f * lay.bh)) {
                putImg(m, cx - w / 2f + lx, lay.bodyTop + 0.70f * lay.bh - h / 2f, w, h)
            }
        }
    }
}

private fun hitZone(lay: MonLayout, im: MonImgs, x: Float, y: Float): MonZone? {
    val p = Offset(x, y)
    if (lay.eyesRect.contains(p)) return MonZone.EYES
    if (lay.mouthRect.contains(p)) return MonZone.MOUTH
    if (im.nose != null && lay.noseRect.contains(p)) return MonZone.NOSE
    if (lay.noseRect.contains(p)) return MonZone.NOSE
    if (lay.topRect.contains(p) && !lay.body.contains(p)) return MonZone.TOP
    if (lay.topRect.contains(p) && p.y < lay.bodyTop + 0.12f * lay.bh) return MonZone.TOP
    if (lay.armsRect.contains(p) && !lay.body.contains(p)) return MonZone.ARMS
    if (lay.legs.contains(p) && p.y > lay.bodyBottom - 4f) return MonZone.LEGS
    if (lay.body.contains(p)) return MonZone.BODY
    return null
}

private val MON_FOODS = listOf("🍎", "🍌", "🥕", "🍕", "🍪", "🍓", "🍉", "🧀")

@Composable
private fun MonBtn(res: Int, emoji: String, size: Int = 54, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(size.dp)
            .scale(if (pressed) 0.9f else 1f)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(res), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Text(emoji, fontSize = (size * 0.44f).sp)
    }
}

@Composable
fun MonsterFactoryScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val density = LocalDensity.current.density

    var spec by remember { mutableStateOf(MonSpec()) }
    val imgs = rememberMonImgs(spec)
    val anim = remember { MonAnim() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var tick by remember { mutableIntStateOf(0) }
    var clock by remember { mutableFloatStateOf(0f) }
    var showHouse by remember { mutableStateOf(false) }
    var showParty by remember { mutableStateOf(false) }
    var party by remember { mutableStateOf(false) }
    var house by remember { mutableStateOf(loadHouse(context)) }
    var feedCount by remember { mutableIntStateOf(0) }
    val foods = remember { mutableStateOf(MON_FOODS.shuffled().take(3)) }
    var dragFood by remember { mutableIntStateOf(-1) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var lookX by remember { mutableFloatStateOf(0f) }
    var toast by remember { mutableStateOf("") }
    val houseAnims = remember { List(MON_MAX_HOUSE) { MonAnim() } }
    val sparks = remember { mutableListOf<FloatArray>() } // x,y,vx,vy,life,colorIdx

    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val cx = w / 2f
    val ground = h * 0.70f
    val layout = if (w > 0f) monLayout(imgs, cx, ground, w * 0.78f, h * 0.50f) else null
    fun foodPos(i: Int) = Offset(w / 4f * (i + 1), h * 0.89f)

    fun confetti(x: Float, y: Float, n: Int) {
        repeat(n) {
            val a = Random.nextFloat() * 6.28f
            val s = (120f + Random.nextFloat() * 300f) * density
            sparks.add(floatArrayOf(x, y, cos(a) * s, sin(a) * s - 160f * density, 1f, Random.nextInt(5).toFloat()))
        }
    }

    fun changePart(zone: MonZone) {
        spec = when (zone) {
            MonZone.EYES -> spec.copy(eyes = (spec.eyes + 1) % MonsterArt.EYES.size)
            MonZone.MOUTH -> spec.copy(mouth = (spec.mouth + 1) % MonsterArt.MOUTHS.size)
            MonZone.NOSE -> spec.copy(nose = (spec.nose + 1) % MON_NOSES)
            MonZone.TOP -> spec.copy(detail = (spec.detail + 1) % MON_DETAIL)
            MonZone.ARMS -> spec.copy(arm = (spec.arm + 1) % MON_ARMS)
            MonZone.LEGS -> spec.copy(leg = (spec.leg + 1) % MON_LEGS)
            MonZone.BODY -> spec.copy(body = (spec.body + 1) % MON_BODIES)
        }
        anim.pop[zone.ordinal] = 1f
        anim.squash = 0.6f
        when (zone) {
            MonZone.EYES -> soundManager.play("giggle", volume = 0.7f)
            MonZone.MOUTH -> soundPlayer.playCartoonYelp(0.8f + (spec.mouth % 5) * 0.12f)
            MonZone.NOSE -> soundPlayer.playPop(1.6f)
            MonZone.TOP -> soundPlayer.playChime(1.0f + (spec.detail % 4) * 0.2f)
            MonZone.ARMS -> soundPlayer.playPop(1.0f)
            MonZone.LEGS -> soundPlayer.playPop(0.7f)
            MonZone.BODY -> soundManager.play("boing", volume = 0.7f)
        }
        soundPlayer.vibrateSquishDeform()
    }

    fun feed() {
        anim.chomp = 1f
        anim.squash = 0.5f
        anim.jump(260f)
        soundPlayer.playPop(0.8f)
        soundPlayer.playCartoonYelp(0.6f)
        feedCount++
        if (feedCount % 5 == 0) {
            anim.danceUntil = clock + 4f
            soundPlayer.playFanfare()
            confetti(cx, ground - h * 0.25f, 30)
            toast = "💖"
        }
        foods.value = MON_FOODS.shuffled().take(3)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFB3E5FC), Color(0xFFE1F5FE), Color(0xFFC5E1A5))))
            .onSizeChanged { size = it }
            .testTag("monster_factory_root")
            .pointerInput(showHouse, imgs, spec) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val p0 = down.position
                    if (showHouse) {
                        // тап по ячейке дома
                        var up = false
                        do { val e = awaitPointerEvent(); if (e.changes.none { it.pressed }) up = true } while (!up)
                        val cols = 2
                        val rows = 4
                        val cellW = w / cols
                        val cellH = h * 0.80f / rows
                        val top = h * 0.14f
                        val ci = ((p0.x / cellW).toInt()).coerceIn(0, cols - 1)
                        val ri = (((p0.y - top) / cellH).toInt())
                        if (p0.y >= top && ri in 0 until rows) {
                            val idx = ri * cols + ci
                            if (idx < house.size) {
                                val cellCx = cellW * (ci + 0.5f)
                                val cellTop = top + cellH * ri
                                if (hypot(p0.x - (cellCx + cellW * 0.38f), p0.y - (cellTop + 22f * density)) < 26f * density) {
                                    house = house.toMutableList().also { it.removeAt(idx) }
                                    saveHouse(context, house)
                                    soundPlayer.playPop(0.7f)
                                } else {
                                    spec = house[idx]
                                    showHouse = false
                                    party = false
                                    anim.jump()
                                    soundPlayer.playChime(1.3f)
                                }
                            }
                        }
                        return@awaitEachGesture
                    }
                    // еда?
                    var fi = -1
                    for (i in 0 until 3) {
                        if (hypot(p0.x - foodPos(i).x, p0.y - foodPos(i).y) < 60f * density) fi = i
                    }
                    if (fi >= 0) {
                        dragFood = fi; dragX = p0.x; dragY = p0.y
                        do {
                            val e = awaitPointerEvent()
                            val c = e.changes.firstOrNull { it.id == down.id } ?: break
                            dragX = c.position.x; dragY = c.position.y
                            c.consume()
                        } while (c.pressed)
                        val lay = layout
                        val onMonster = lay != null && (lay.body.inflate(30f * density).contains(Offset(dragX, dragY)) ||
                            lay.mouthRect.inflate(40f * density).contains(Offset(dragX, dragY)))
                        dragFood = -1
                        if (onMonster) feed()
                        return@awaitEachGesture
                    }
                    // тап (или движение — глазки следят)
                    var moved = false
                    do {
                        val e = awaitPointerEvent()
                        val c = e.changes.firstOrNull { it.id == down.id } ?: break
                        if (hypot(c.position.x - p0.x, c.position.y - p0.y) > 18f * density) moved = true
                        lookX = ((c.position.x - cx) / (w / 2f)).coerceIn(-1f, 1f)
                    } while (c.pressed)
                    lookX = 0f
                    if (!moved) {
                        val lay = layout
                        val z = if (lay != null) hitZone(lay, imgs, p0.x, p0.y) else null
                        if (z != null) changePart(z) else {
                            anim.jump(); anim.squash = 0.4f
                            soundManager.play("giggle", volume = 0.6f)
                        }
                    }
                }
            }
    ) {
        if (!showHouse) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val redraw = tick
                // холм-подиум
                drawOval(Color(0xFF9CCC65), Offset(-w * 0.2f, ground - h * 0.06f), Size(w * 1.4f, h * 0.5f))
                drawOval(Color(0xFF7CB342).copy(alpha = 0.35f), Offset(cx - w * 0.30f, ground - h * 0.02f), Size(w * 0.6f, h * 0.05f))
                val lay = layout
                if (lay != null) {
                    drawMonster(imgs, spec, lay, anim, clock, density, lookX)
                    for (s in sparks) {
                        val col = listOf(Color(0xFFFFD54F), Color(0xFFFF8A65), Color(0xFF4FC3F7), Color(0xFFAED581), Color(0xFFF06292))[s[5].toInt() % 5]
                        drawCircle(col.copy(alpha = s[4].coerceIn(0f, 1f)), 4f * density, Offset(s[0], s[1]))
                    }
                }
            }
            if (size != IntSize.Zero) {
                // тарелка с угощениями
                Box(
                    Modifier
                        .padding(top = 0.dp)
                        .fillMaxSize()
                ) {
                    for (i in 0 until 3) {
                        val dragging = dragFood == i
                        val pos = if (dragging) Offset(dragX, dragY) else foodPos(i)
                        Text(
                            foods.value[i],
                            fontSize = if (dragging) 66.sp else 52.sp,
                            modifier = Modifier.padding(
                                start = ((pos.x - 36f * density) / density).coerceAtLeast(0f).dp,
                                top = ((pos.y - 44f * density) / density).coerceAtLeast(0f).dp
                            )
                        )
                    }
                }
            }
            // кнопки сбоку
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MonBtn(R.drawable.ui_btn_sq_blue, "🎨") {
                    spec = spec.copy(color = (spec.color + 1) % 6)
                    anim.squash = 0.5f; anim.jump(300f)
                    soundPlayer.playSparkleChime()
                }
                MonBtn(R.drawable.ui_btn_sq_green, "👀") {
                    spec = spec.copy(eyeCount = spec.eyeCount % 3 + 1)
                    anim.pop[MonZone.EYES.ordinal] = 1f
                    soundManager.play("giggle", volume = 0.7f)
                }
                MonBtn(R.drawable.ui_btn_sq_red, "🎲") {
                    spec = MonSpec.random()
                    anim.squash = 0.8f; anim.jump(560f)
                    soundPlayer.playMagicSparkle()
                }
                MonBtn(R.drawable.ui_btn_sq_blue, "🎵") {
                    anim.danceUntil = clock + 6f
                    anim.nextBeat = clock
                    soundPlayer.playFanfare()
                }
                MonBtn(R.drawable.ui_btn_sq_green, "⭐") {
                    if (house.size < MON_MAX_HOUSE && spec !in house) {
                        house = house + spec
                        saveHouse(context, house)
                        confetti(cx, ground - h * 0.2f, 24)
                        soundPlayer.playChime(1.6f)
                        toast = "🏠 ✔"
                    } else if (house.size >= MON_MAX_HOUSE) {
                        soundPlayer.playPop(0.6f)
                        toast = "🏠 полон"
                    } else {
                        soundPlayer.playPop(0.9f)
                    }
                }
                MonBtn(R.drawable.ui_btn_sq_green, "🎪") {
                    showParty = true
                    soundPlayer.playFanfare()
                }
                MonBtn(R.drawable.ui_btn_sq_red, "🏠") {
                    showHouse = true
                    party = false
                    soundPlayer.playPop(1.2f)
                }
            }
            if (toast.isNotEmpty()) {
                Text(
                    toast, fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color(0xFF6A1B9A),
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 20.dp)
                )
            }
        } else {
            // ---------- Дом монстриков ----------
            Image(painterResource(R.drawable.ui_bg_pink), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            val houseImgs = house.map { rememberMonImgs(it) }
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val redraw = tick
                val cols = 2
                val rows = 4
                val cellW = size.width / cols
                val cellH = size.height * 0.80f / rows
                val top = size.height * 0.14f
                for ((i, hs) in house.withIndex()) {
                    val ci = i % cols
                    val ri = i / cols
                    val ccx = cellW * (ci + 0.5f)
                    val cellTop = top + cellH * ri
                    // полочка
                    drawRoundRect(
                        Color.White.copy(alpha = 0.45f), Offset(cellW * ci + 8f * density, cellTop + 4f * density),
                        Size(cellW - 16f * density, cellH - 8f * density), androidx.compose.ui.geometry.CornerRadius(18f * density)
                    )
                    val im = houseImgs[i]
                    val lay = monLayout(im, ccx, cellTop + cellH - 14f * density, cellW * 0.74f, cellH * 0.74f)
                    drawMonster(im, hs, lay, houseAnims[i], clock, density, 0f)
                    // крестик удаления
                    val xc = Offset(ccx + cellW * 0.38f, cellTop + 22f * density)
                    drawCircle(Color(0xFFE53935), 13f * density, xc)
                    drawLine(Color.White, Offset(xc.x - 5f * density, xc.y - 5f * density), Offset(xc.x + 5f * density, xc.y + 5f * density), 2.5f * density)
                    drawLine(Color.White, Offset(xc.x + 5f * density, xc.y - 5f * density), Offset(xc.x - 5f * density, xc.y + 5f * density), 2.5f * density)
                }
                for (s in sparks) {
                    val col = listOf(Color(0xFFFFD54F), Color(0xFFFF8A65), Color(0xFF4FC3F7), Color(0xFFAED581), Color(0xFFF06292))[s[5].toInt() % 5]
                    drawCircle(col.copy(alpha = s[4].coerceIn(0f, 1f)), 4f * density, Offset(s[0], s[1]))
                }
            }
            if (house.isEmpty()) {
                Text(
                    "Пока пусто\nНажми ⭐ у монстрика",
                    modifier = Modifier.align(Alignment.Center), fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF6A1B4D), textAlign = TextAlign.Center
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MonBtn(R.drawable.ui_btn_sq_green, "🎉") {
                    party = !party
                    if (party) {
                        for (i in house.indices) { houseAnims[i].danceUntil = clock + 8f; houseAnims[i].nextBeat = clock + i * 0.1f }
                        soundPlayer.playFanfare()
                        confetti(w / 2f, h * 0.3f, 40)
                    }
                }
                MonBtn(R.drawable.ui_btn_sq_red, "✖") {
                    showHouse = false
                    soundPlayer.playPop(1.0f)
                }
            }
        }
    }

    if (showParty) {
        MonsterPartyView(
            start = (listOf(spec) + house.filter { it != spec }).take(2).let { if (it.size < 2) it + MonSpec.random() else it },
            soundPlayer = soundPlayer, soundManager = soundManager, onExit = { showParty = false }
        )
    }

    LaunchedEffect(Unit) {
        var last = 0L
        var beat = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                clock += dt
                anim.step(dt, clock)
                for (a in houseAnims) a.step(dt, clock)
                // музыкальные «удары» во время танца
                val dancing = clock < anim.danceUntil || (showHouse && party)
                if (dancing && clock >= beat) {
                    beat = clock + 0.46f
                    val notes = floatArrayOf(1.0f, 1.12f, 1.26f, 1.5f, 1.68f, 1.5f, 1.26f, 1.12f)
                    soundPlayer.playChime(notes[((clock / 0.46f).toInt()) % notes.size])
                }
                val it = sparks.iterator()
                while (it.hasNext()) {
                    val s = it.next()
                    s[0] += s[2] * dt; s[1] += s[3] * dt
                    s[3] += 700f * density * dt
                    s[4] -= dt * 1.1f
                    if (s[4] <= 0f) it.remove()
                }
                if (toast.isNotEmpty() && (clock * 10f).toInt() % 20 == 0 && clock > 0.5f) { /* подпись исчезает ниже */ }
                tick++
            }
        }
    }
    LaunchedEffect(toast) {
        if (toast.isNotEmpty()) {
            kotlinx.coroutines.delay(1400)
            toast = ""
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Вечеринка у монстриков: гости голодные — корми их тем, что они просят. Чем больше накормил,
// тем веселее вечеринка: приходят новые гости, включается дискотека.
// ---------------------------------------------------------------------------------------------

private val PARTY_FOODS = listOf("🍎", "🍌", "🍕", "🍪")
private const val PARTY_MAX_GUESTS = 6

@Composable
private fun MonsterPartyView(
    start: List<MonSpec>,
    soundPlayer: SoundEffectPlayer,
    soundManager: SoundManager,
    onExit: () -> Unit
) {
    val density = LocalDensity.current.density
    val ctx = LocalContext.current
    val guests = remember { androidx.compose.runtime.mutableStateListOf<MonSpec>().also { it.addAll(start.take(2)) } }
    val wants = remember { androidx.compose.runtime.mutableStateListOf<Int>().also { l -> repeat(guests.size) { l.add(Random.nextInt(4)) } } }
    val anims = remember { List(PARTY_MAX_GUESTS) { MonAnim() } }
    val happyUntil = remember { FloatArray(PARTY_MAX_GUESTS) }
    val sparks = remember { mutableListOf<FloatArray>() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var tick by remember { mutableIntStateOf(0) }
    var clock by remember { mutableFloatStateOf(0f) }
    var fed by remember { mutableIntStateOf(0) }
    var level by remember { mutableIntStateOf(1) }
    var banner by remember { mutableStateOf("") }
    var dragFood by remember { mutableIntStateOf(-1) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val imgs = guests.map { rememberMonImgs(it) }

    val w = size.width.toFloat()
    val h = size.height.toFloat()
    fun foodPos(i: Int) = Offset(w / 5f * (i + 1), h * 0.88f)
    fun need() = 2 + guests.size

    fun cell(i: Int): Rect {
        val n = guests.size
        val cols = if (n <= 3) n else if (n == 4) 2 else 3
        val rows = (n + cols - 1) / cols
        val cw = w / cols
        val ch = h * 0.58f / rows
        return Rect(cw * (i % cols), h * 0.12f + ch * (i / cols), cw * (i % cols + 1), h * 0.12f + ch * (i / cols + 1))
    }

    fun confetti(x: Float, y: Float, n: Int) {
        repeat(n) {
            val a = Random.nextFloat() * 6.28f
            val s = (120f + Random.nextFloat() * 300f) * density
            sparks.add(floatArrayOf(x, y, cos(a) * s, sin(a) * s - 160f * density, 1f, Random.nextInt(5).toFloat()))
        }
    }

    fun levelUp() {
        level++
        fed = 0
        if (guests.size < PARTY_MAX_GUESTS) {
            val house = loadHouse(ctx)
            val newcomer = house.firstOrNull { it !in guests } ?: MonSpec.random()
            guests.add(newcomer)
            wants.add(Random.nextInt(4))
        }
        for (a in anims) { a.danceUntil = clock + 5f; a.nextBeat = clock; a.jump(400f) }
        soundPlayer.playLegendaryFanfare()
        confetti(w / 2f, h * 0.3f, 60)
        banner = "🎉 Уровень $level!"
    }

    fun feedGuest(i: Int, food: Int) {
        if (wants[i] == food) {
            wants[i] = -1
            happyUntil[i] = clock + 2.2f
            anims[i].danceUntil = clock + 3f
            anims[i].nextBeat = clock
            anims[i].chomp = 1f
            anims[i].jump(380f)
            soundPlayer.playPop(1.0f)
            soundPlayer.playCartoonYelp(0.7f)
            val c = cell(i)
            confetti(c.center.x, c.top + c.height * 0.4f, 18)
            fed++
            if (fed >= need()) levelUp()
        } else {
            anims[i].squash = 0.7f
            soundPlayer.playPop(0.5f)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF311B92), Color(0xFF6A1B9A), Color(0xFFAD1457))))
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val p0 = down.position
                    var fi = -1
                    for (i in PARTY_FOODS.indices) if (hypot(p0.x - foodPos(i).x, p0.y - foodPos(i).y) < 60f * density) fi = i
                    if (fi >= 0) {
                        dragFood = fi; dragX = p0.x; dragY = p0.y
                        do {
                            val e = awaitPointerEvent()
                            val c = e.changes.firstOrNull { it.id == down.id } ?: break
                            dragX = c.position.x; dragY = c.position.y
                            c.consume()
                        } while (c.pressed)
                        val gi = (0 until guests.size).firstOrNull { cell(it).contains(Offset(dragX, dragY)) }
                        dragFood = -1
                        if (gi != null) feedGuest(gi, fi)
                    } else {
                        do {
                            val e = awaitPointerEvent()
                        } while (e.changes.any { it.pressed })
                        val gi = (0 until guests.size).firstOrNull { cell(it).contains(p0) }
                        if (gi != null) {
                            anims[gi].jump(320f); anims[gi].squash = 0.5f
                            soundManager.play("giggle", volume = 0.7f)
                        }
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val redraw = tick
            // дискотечные лучи
            val beamPalette = listOf(Color(0xFFFFEB3B), Color(0xFF4FC3F7), Color(0xFFF06292), Color(0xFF81C784), Color(0xFFFFB74D))
            val a0 = 0.07f + 0.03f * min(level, 6)
            val cw = this.size.width
            val ch = this.size.height
            for (k in 0 until 5) {
                val x0 = cw * (k + 0.5f) / 5f
                val sway = sin(clock * 0.8f + k * 1.3f) * cw * 0.25f
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(x0, 0f)
                    lineTo(x0 + sway - cw * 0.12f, ch * 0.75f)
                    lineTo(x0 + sway + cw * 0.12f, ch * 0.75f)
                    close()
                }
                drawPath(path, beamPalette[k].copy(alpha = a0))
            }
            // пол
            drawRect(Color(0xFF4A148C), Offset(0f, h * 0.70f), Size(cw, ch * 0.30f))
            for (k in 0 until 8) {
                val on = ((clock * 2f).toInt() + k) % 2 == 0
                drawRect(beamPalette[k % 5].copy(alpha = if (on) 0.35f else 0.12f), Offset(cw / 8f * k, h * 0.70f), Size(cw / 8f, 10f * density))
            }
            for (i in 0 until guests.size) {
                val c = cell(i)
                val lay = monLayout(imgs[i], c.center.x, c.bottom - 4f * density, c.width * 0.8f, c.height * 0.60f)
                drawMonster(imgs[i], guests[i], lay, anims[i], clock, density, 0f)
            }
            for (s in sparks) {
                drawCircle(beamPalette[s[5].toInt() % 5].copy(alpha = s[4].coerceIn(0f, 1f)), 4f * density, Offset(s[0], s[1]))
            }
        }
        if (size != IntSize.Zero) {
            // пожелания гостей
            for (i in 0 until guests.size) {
                val c = cell(i)
                val wf = wants.getOrNull(i) ?: continue
                Box(
                    Modifier
                        .offset { IntOffset((c.center.x - 26f * density).roundToInt(), (c.top + 2f * density).roundToInt()) }
                        .size(52.dp)
                        .background(Color.White.copy(alpha = 0.92f), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text(if (wf < 0) "❤️" else PARTY_FOODS[wf], fontSize = 28.sp) }
            }
            // угощения
            for (i in PARTY_FOODS.indices) {
                val dragging = dragFood == i
                val pos = if (dragging) Offset(dragX, dragY) else foodPos(i)
                Text(
                    PARTY_FOODS[i], fontSize = if (dragging) 66.sp else 52.sp,
                    modifier = Modifier.offset { IntOffset((pos.x - 36f * density).roundToInt(), (pos.y - 44f * density).roundToInt()) }
                )
            }
        }
        // шкала веселья
        Row(
            Modifier.statusBarsPadding().padding(top = 14.dp, start = 88.dp, end = 70.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎪${level}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .fillMaxWidth()
                    .size(width = 200.dp, height = 14.dp)
                    .background(Color.White.copy(alpha = 0.3f), androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .fillMaxWidth(fed / need().toFloat())
                        .background(Color(0xFFFFD54F), androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
                )
            }
        }
        if (banner.isNotEmpty()) {
            Text(
                banner, fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFEB3B),
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 8.dp)) {
            MonBtn(R.drawable.ui_btn_sq_red, "✖") { onExit() }
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        var beat = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                clock += dt
                var dancing = false
                for (i in anims.indices) {
                    anims[i].step(dt, clock)
                    if (i < guests.size) {
                        if (clock < anims[i].danceUntil) dancing = true
                        if (wants.getOrNull(i) == -1 && clock > happyUntil[i]) wants[i] = Random.nextInt(4)
                    }
                }
                if (dancing && clock >= beat) {
                    beat = clock + 0.46f
                    val notes = floatArrayOf(1.0f, 1.26f, 1.5f, 1.68f, 1.5f, 1.26f)
                    soundPlayer.playChime(notes[((clock / 0.46f).toInt()) % notes.size])
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
