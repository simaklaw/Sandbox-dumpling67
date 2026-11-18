package com.example.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.R
import com.example.shared.SoundManager
import com.example.shared.withFrameNanosEco
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Заставка при запуске (рендеры «Детской площадки»): колыбелька с пельмешком → золотой вихрь →
 * взрыв конфетти → сборка логотипа → финал с героями. Кадры — готовые картинки, поверх рисуются
 * вихрь, конфетти, звёзды и пузыри. Нажатие пропускает заставку. Все времена в секундах от старта.
 */
private const val INTRO_SPEED = 0.8f // 1.0 — исходные 9,6 с; меньше — быстрее

internal const val INTRO_SWIRL_START = 2.2f * INTRO_SPEED
internal const val INTRO_BURST = 3.4f * INTRO_SPEED
internal const val INTRO_ASSEMBLY = 4.1f * INTRO_SPEED
internal const val INTRO_FINAL = 6.4f * INTRO_SPEED
internal const val INTRO_TOTAL = 9.6f * INTRO_SPEED
private const val INTRO_FADE = 0.5f

private val CONFETTI = listOf(
    Color(0xFFFF4D5E), Color(0xFFFF9F1C), Color(0xFFFFD23F), Color(0xFF5BD75B), Color(0xFF3FA9F5), Color(0xFFFF5FA2)
)
private val GOLD = Color(0xFFFFD86B)
private val GOLD_LIGHT = Color(0xFFFFF3C4)
private val SKY_TOP = Color(0xFF7CC0F1)
private val SKY_BOTTOM = Color(0xFFCFE9FB)

/** Нота «колыбельной» в начале и взлёт нот во время сборки: имена файлов `rb_*` (радуга). */
private val INTRO_NOTES = listOf("rb_red", "rb_orange", "rb_yellow", "rb_green", "rb_blue", "rb_purple")

private class IntroParticles(seed: Int = 67) {
    private val r = Random(seed)
    // конфетти: угол, скорость, поворот, скорость поворота, ширина, высота, цвет, время жизни
    val cAng = FloatArray(150) { r.nextFloat() * 6.283f }
    val cSpd = FloatArray(150) { 140f + r.nextFloat() * 460f }
    val cRot = FloatArray(150) { r.nextFloat() * 6.283f }
    val cVr = FloatArray(150) { (r.nextFloat() - 0.5f) * 9f }
    val cW = FloatArray(150) { 6f + r.nextFloat() * 8f }
    val cH = FloatArray(150) { 9f + r.nextFloat() * 11f }
    val cLife = FloatArray(150) { 3.6f + r.nextFloat() * 1.6f }
    // звёзды взрыва
    val sAng = FloatArray(60) { r.nextFloat() * 6.283f }
    val sSpd = FloatArray(60) { 90f + r.nextFloat() * 380f }
    val sRot = FloatArray(60) { r.nextFloat() * 6.283f }
    val sVr = FloatArray(60) { (r.nextFloat() - 0.5f) * 6f }
    val sSize = FloatArray(60) { 8f + r.nextFloat() * 13f }
    val sLife = FloatArray(60) { 3f + r.nextFloat() * 1.5f }
    // пузыри: положение по ширине, скорость, радиус, фаза, оттенок
    val bX = FloatArray(16) { r.nextFloat() }
    val bSpd = FloatArray(16) { 26f + r.nextFloat() * 48f }
    val bR = FloatArray(16) { 10f + r.nextFloat() * 28f }
    val bPh = FloatArray(16) { r.nextFloat() * 6.283f }
    val bOff = FloatArray(16) { r.nextFloat() }
    val bHue = FloatArray(16) { r.nextFloat() * 360f }
    // искры финала: положение вокруг центра, фаза мерцания
    val kX = FloatArray(16) { (r.nextFloat() - 0.5f) * 0.9f }
    val kY = FloatArray(16) { (r.nextFloat() - 0.5f) * 0.8f }
    val kPh = FloatArray(16) { r.nextFloat() * 6.283f }
}

private fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v
private fun easeOutCubic(v: Float): Float { val k = 1f - clamp01(v); return 1f - k * k * k }
private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k

internal fun introFade(t: Float): Float =
    if (t > INTRO_TOTAL - INTRO_FADE) clamp01((INTRO_TOTAL - t) / INTRO_FADE) else 1f

@Composable
fun IntroScreen(soundManager: SoundManager, onFinished: () -> Unit) {
    val cradle = ImageBitmap.imageResource(R.drawable.intro_cradle)
    val assembly = ImageBitmap.imageResource(R.drawable.intro_assembly)
    val final = ImageBitmap.imageResource(R.drawable.intro_final)
    val particles = remember { IntroParticles() }
    val layerPaint = remember { Paint() }
    val star = remember { starPath() }

    var t by remember { mutableFloatStateOf(0f) }
    var fired by remember { mutableIntStateOf(0) } // какие звуки уже сыграны (по битам)
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanosEco { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                last = now
                t += dt
                // звуки: короткая «колыбельная» из трёх нот, вихрь, взрыв, взлёт нот при сборке, финальный сигнал
                fun once(bit: Int, at: Float, play: () -> Unit) {
                    if (t >= at && (fired and (1 shl bit)) == 0) {
                        fired = fired or (1 shl bit)
                        play()
                    }
                }
                once(0, 0.5f) { soundManager.play(INTRO_NOTES[0], 0.5f) }
                once(1, 1.0f) { soundManager.play(INTRO_NOTES[2], 0.5f) }
                once(2, 1.5f) { soundManager.play(INTRO_NOTES[4], 0.5f) }
                once(3, INTRO_SWIRL_START) { soundManager.play("whoosh_soft", 0.7f) }
                once(4, INTRO_BURST) { soundManager.play("confetti_rain", 0.8f) }
                for (i in 0 until 5) {
                    once(5 + i, INTRO_ASSEMBLY + 0.14f * i) { soundManager.play(INTRO_NOTES[i], 0.5f) }
                }
                once(10, INTRO_FINAL) { soundManager.play("fanfare_short", 0.8f) }
                if (t >= INTRO_TOTAL && !finished) {
                    finished = true
                    onFinished()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = introFade(t) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (t > 0.4f && !finished) { finished = true; onFinished() }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawIntro(t, cradle, assembly, final, particles, layerPaint, star)
        }
    }
}

private fun starPath(): Path = Path().apply {
    for (i in 0 until 10) {
        val rr = if (i % 2 == 1) 0.45f else 1f
        val a = i * PI.toFloat() / 5f - PI.toFloat() / 2f
        if (i == 0) moveTo(cos(a) * rr, sin(a) * rr) else lineTo(cos(a) * rr, sin(a) * rr)
    }
    close()
}

private fun DrawScope.glow(c: Offset, r: Float, color: Color, a: Float) {
    if (a <= 0f || r <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = a.coerceIn(0f, 1f)), Color.Transparent), center = c, radius = r),
        radius = r, center = c, blendMode = BlendMode.Plus
    )
}

private fun DrawScope.drawStar(star: Path, c: Offset, r: Float, rot: Float, color: Color, a: Float) {
    if (a <= 0f) return
    withTransform({
        translate(c.x, c.y)
        rotate(rot * 180f / PI.toFloat(), pivot = Offset.Zero)
        scale(r, r, pivot = Offset.Zero)
    }) { drawPath(star, color.copy(alpha = a.coerceIn(0f, 1f))) }
}

/** Картинка с мягкими краями: края растворяются в небе, поэтому кадры с разным фоном не дают швов. */
private fun DrawScope.drawFeathered(
    img: ImageBitmap, center: Offset, widthPx: Float, alpha: Float, scale: Float, dy: Float, paint: Paint
) {
    if (alpha <= 0.002f) return
    val heightPx = widthPx * img.height / img.width
    val left = center.x - widthPx / 2f
    val top = center.y - heightPx / 2f
    val feather = 0.12f
    paint.alpha = alpha.coerceIn(0f, 1f)
    withTransform({
        translate(0f, dy)
        scale(scale, scale, pivot = center)
    }) {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(left, top, left + widthPx, top + heightPx), paint)
            drawImage(
                image = img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(widthPx.roundToInt(), heightPx.roundToInt()),
                filterQuality = FilterQuality.High
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent, feather to Color.Black, 1f - feather to Color.Black, 1f to Color.Transparent,
                    startX = left, endX = left + widthPx
                ),
                topLeft = Offset(left, top), size = Size(widthPx, heightPx), blendMode = BlendMode.DstIn
            )
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent, feather to Color.Black, 1f - feather to Color.Black, 1f to Color.Transparent,
                    startY = top, endY = top + heightPx
                ),
                topLeft = Offset(left, top), size = Size(widthPx, heightPx), blendMode = BlendMode.DstIn
            )
            canvas.restore()
        }
    }
}

private fun DrawScope.drawIntro(
    t: Float, cradle: ImageBitmap, assembly: ImageBitmap, final: ImageBitmap,
    p: IntroParticles, paint: Paint, star: Path
) {
    val w = size.width
    val h = size.height
    val u = min(w, h) / 760f
    val center = Offset(w / 2f, h / 2f)
    val imgW = min(w * 1.04f, h * 0.95f)

    // небо: к финалу чуть светлеет
    val k = clamp01(t / INTRO_FINAL)
    drawRect(
        Brush.verticalGradient(
            listOf(
                Color(
                    lerp(SKY_TOP.red, 0.56f, k), lerp(SKY_TOP.green, 0.78f, k), lerp(SKY_TOP.blue, 0.95f, k)
                ),
                Color(
                    lerp(0.58f, SKY_BOTTOM.red, k), lerp(0.80f, SKY_BOTTOM.green, k), lerp(0.94f, SKY_BOTTOM.blue, k)
                )
            )
        )
    )

    // пузыри всплывают всё время
    val fadeIn = clamp01(t / 0.8f)
    for (i in p.bX.indices) {
        val r = p.bR[i] * u
        val travel = h + 2f * r + 60f * u
        val y = h + r + 30f * u - ((t * p.bSpd[i] * u + p.bOff[i] * travel) % travel)
        val x = p.bX[i] * w + sin(t * 0.9f + p.bPh[i]) * 16f * u
        val c = Offset(x, y)
        drawCircle(Color.White.copy(alpha = 0.10f * fadeIn), r, c)
        drawCircle(
            Color.hsv((p.bHue[i] + t * 40f) % 360f, 0.45f, 1f, 0.55f * fadeIn), r * 0.96f, c, style = Stroke(2f * u)
        )
        drawCircle(Color.White.copy(alpha = 0.8f * fadeIn), r * 0.16f, Offset(c.x - r * 0.35f, c.y - r * 0.35f))
    }

    // колыбелька: плавно появляется, мягко «дышит» и растворяется перед сборкой
    val zoom = 1f + (1f - easeOutCubic(t / INTRO_FINAL)) * 0.06f
    val cradleA = clamp01(t / 0.6f) * clamp01(1f - (t - INTRO_BURST) / (INTRO_ASSEMBLY - INTRO_BURST - 0.2f))
    if (cradleA > 0f) {
        val bob = sin(t * 1.4f) * 4f * u
        drawFeathered(cradle, center, imgW, cradleA, zoom, bob, paint)
        // золотое сияние над пельмешком, пульсирует и разгорается к вихрю
        val gc = Offset(center.x, center.y + (0.37f - 0.5f) * imgW + bob)
        val pulse = 0.55f + 0.25f * sin(t * 3f)
        val rise = 1f + clamp01((t - INTRO_SWIRL_START) / (INTRO_BURST - INTRO_SWIRL_START)) * 1.4f
        glow(gc, 170f * u * rise, GOLD, pulse * cradleA * 0.6f)
    }

    // вихрь
    if (t >= INTRO_SWIRL_START && t < INTRO_ASSEMBLY) {
        val prog = (t - INTRO_SWIRL_START) / (INTRO_BURST - INTRO_SWIRL_START)
        val big = min(w, h) * 0.42f * easeOutCubic(prog)
        val dissolve = clamp01(1f - (t - INTRO_BURST) / (INTRO_ASSEMBLY - INTRO_BURST))
        if (big > 4f && dissolve > 0f) {
            val n = 80
            for (i in 0 until n) {
                val q = i / n.toFloat()
                val an = q * PI.toFloat() * 4f + t * 2.4f
                val rr = big * (0.35f + 0.65f * q)
                val c = Offset(center.x + cos(an) * rr, center.y + sin(an) * rr * 0.6f)
                glow(c, (6f + 8f * q) * u * 2f, GOLD, 0.55f * (1f - q * 0.4f) * dissolve)
                if (i % 6 == 0) drawStar(star, c, (4f + 4f * q) * u, an, GOLD_LIGHT, dissolve)
            }
        }
    }

    // сборка логотипа
    val asmA = clamp01((t - INTRO_ASSEMBLY + 0.3f) / 0.9f) * clamp01(1f - (t - INTRO_FINAL) / 0.8f)
    if (asmA > 0f) drawFeathered(assembly, center, imgW, asmA, zoom, 0f, paint)

    // финал: логотип с героями слегка парит
    val finA = clamp01((t - INTRO_FINAL) / 0.9f)
    if (finA > 0f) {
        val bob = sin(t * 1.6f) * 4f * u
        drawFeathered(final, center, imgW, finA, 1f + (1f - finA) * 0.04f, bob, paint)
        for (i in p.kX.indices) {
            val tw = 0.5f + 0.5f * sin(t * 3.2f + p.kPh[i])
            val c = Offset(center.x + p.kX[i] * imgW, center.y + p.kY[i] * imgW + bob)
            glow(c, (10f + 14f * tw) * u, GOLD_LIGHT, 0.6f * tw * finA)
            drawStar(star, c, (3f + 5f * tw) * u, t * 2f + i, GOLD_LIGHT, 0.9f * tw * finA)
        }
    }

    // взрыв: конфетти и звёзды разлетаются из центра и падают
    if (t >= INTRO_BURST) {
        val kb = t - INTRO_BURST
        val g = 360f * u
        for (i in p.cAng.indices) {
            if (kb > p.cLife[i]) continue
            val vx = cos(p.cAng[i]) * p.cSpd[i] * u
            val vy = sin(p.cAng[i]) * p.cSpd[i] * u - 140f * u
            val x = center.x + vx * kb
            val y = center.y + vy * kb + 0.5f * g * kb * kb
            if (y > h + 90f) continue
            val a = 1f - kb / p.cLife[i]
            withTransform({
                translate(x, y)
                rotate((p.cRot[i] + p.cVr[i] * kb) * 180f / PI.toFloat(), pivot = Offset.Zero)
            }) {
                drawRect(
                    CONFETTI[i % CONFETTI.size].copy(alpha = a),
                    topLeft = Offset(-p.cW[i] * u / 2f, -p.cH[i] * u / 2f),
                    size = Size(p.cW[i] * u, p.cH[i] * u)
                )
            }
        }
        for (i in p.sAng.indices) {
            if (kb > p.sLife[i]) continue
            val x = center.x + cos(p.sAng[i]) * p.sSpd[i] * u * kb
            val y = center.y + (sin(p.sAng[i]) * p.sSpd[i] * u - 100f * u) * kb + 0.5f * g * 0.6f * kb * kb
            if (y > h + 90f) continue
            drawStar(star, Offset(x, y), p.sSize[i] * u, p.sRot[i] + p.sVr[i] * kb, if (i % 2 == 0) GOLD else GOLD_LIGHT, 1f - kb / p.sLife[i])
        }
        // короткая тёплая вспышка
        val flash = clamp01(1f - kb * 2.2f)
        if (flash > 0f) drawRect(GOLD_LIGHT.copy(alpha = flash * 0.7f))
    }
}
