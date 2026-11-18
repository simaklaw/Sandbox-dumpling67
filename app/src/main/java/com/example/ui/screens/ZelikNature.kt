package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Природа для карт «Зелика» и забега: деревья, кусты, камни, цветы, грибы и пни.
 * Модели Midnight Misfit Studio («Cozy Nature», бесплатный набор), отрисованные в спрайты tools/zelik_nature.
 * Сами 3D-файлы в приложение и в репозиторий не входят, только готовые картинки.
 */
internal class ZNatureGfx(private val im: Map<String, ImageBitmap>) {
    operator fun get(k: String): ImageBitmap? = im[k]
}

private val ZN_RES = mapOf(
    "oak" to R.drawable.zn_oak, "birch" to R.drawable.zn_birch, "spruce" to R.drawable.zn_spruce, "cherry" to R.drawable.zn_cherry,
    "bush" to R.drawable.zn_bush, "berries" to R.drawable.zn_berries, "fern" to R.drawable.zn_fern,
    "rock_s" to R.drawable.zn_rock_s, "rock_l" to R.drawable.zn_rock_l, "rock_t" to R.drawable.zn_rock_t,
    "rock_arch" to R.drawable.zn_rock_arch, "rock_moss" to R.drawable.zn_rock_moss,
    "daisy" to R.drawable.zn_daisy, "tulip" to R.drawable.zn_tulip, "lavender" to R.drawable.zn_lavender, "sunflower" to R.drawable.zn_sunflower,
    "tuft" to R.drawable.zn_tuft, "tallgrass" to R.drawable.zn_tallgrass,
    "agaric" to R.drawable.zn_agaric, "porcini" to R.drawable.zn_porcini, "mushgroup" to R.drawable.zn_mushgroup,
    "stump" to R.drawable.zn_stump, "log" to R.drawable.zn_log, "logstack" to R.drawable.zn_logstack, "roots" to R.drawable.zn_roots
)

@Composable
internal fun rememberZNatureGfx(): ZNatureGfx {
    val ctx = LocalContext.current
    return remember {
        val m = HashMap<String, ImageBitmap>()
        for ((k, id) in ZN_RES) {
            val bmp = try { BitmapFactory.decodeResource(ctx.resources, id) } catch (e: Exception) { null }
            if (bmp != null) m[k] = bmp.asImageBitmap()
        }
        ZNatureGfx(m)
    }
}

private class ZnItem(val key: String, val h: Float, val sway: Boolean = false)

private fun znH(i: Int, s: Int = 0): Float {
    var x = i * 374761393 + s * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFF) / 65535f
}

private fun haze(c: Long) = ColorFilter.tint(Color(c), BlendMode.SrcAtop)

/** Рисует картинку по нижнему центру: baseY — «земля», h — высота на экране. */
internal fun DrawScope.znSprite(
    g: ZNatureGfx, key: String, cx: Float, baseY: Float, h: Float,
    tint: ColorFilter? = null, alpha: Float = 1f, rotDeg: Float = 0f, flip: Boolean = false
) {
    val im = g[key] ?: return
    val w = h * im.width / im.height
    withTransform({
        if (rotDeg != 0f) rotate(rotDeg, Offset(cx, baseY))
        if (flip) scale(-1f, 1f, Offset(cx, baseY))
    }) {
        drawImage(
            im, dstOffset = IntOffset((cx - w / 2f).roundToInt(), (baseY - h).roundToInt()),
            dstSize = IntSize(max(1, w.roundToInt()), max(1, h.roundToInt())),
            alpha = alpha, colorFilter = tint, filterQuality = FilterQuality.Medium
        )
    }
}

// ───────── что растёт на земле каждой карты (высоты в мировых пикселях, игрок ≈ 84) ─────────

private val ZN_GROUND: Array<List<ZnItem>> = arrayOf(
    listOf( // поля
        ZnItem("daisy", 36f, true), ZnItem("tulip", 36f, true), ZnItem("sunflower", 48f, true), ZnItem("lavender", 34f, true),
        ZnItem("tuft", 26f, true), ZnItem("tallgrass", 38f, true), ZnItem("bush", 38f), ZnItem("rock_s", 18f),
        ZnItem("rock_moss", 26f), ZnItem("daisy", 30f, true), ZnItem("tuft", 22f, true)
    ),
    listOf( // лес
        ZnItem("fern", 24f, true), ZnItem("agaric", 26f), ZnItem("porcini", 24f), ZnItem("mushgroup", 28f), ZnItem("stump", 36f),
        ZnItem("log", 30f), ZnItem("berries", 40f), ZnItem("tuft", 26f, true), ZnItem("tallgrass", 40f, true), ZnItem("roots", 44f),
        ZnItem("logstack", 46f), ZnItem("fern", 30f, true)
    ),
    listOf( // замок
        ZnItem("rock_s", 18f), ZnItem("rock_moss", 28f), ZnItem("rock_t", 58f), ZnItem("fern", 26f, true), ZnItem("mushgroup", 26f),
        ZnItem("tuft", 24f, true), ZnItem("rock_s", 22f), ZnItem("agaric", 22f)
    ),
    listOf( // вулкан
        ZnItem("rock_s", 20f), ZnItem("rock_l", 38f), ZnItem("rock_t", 56f), ZnItem("tallgrass", 34f, true), ZnItem("rock_s", 24f),
        ZnItem("tuft", 22f, true)
    ),
    listOf( // лёд
        ZnItem("rock_s", 20f), ZnItem("rock_moss", 28f), ZnItem("tuft", 24f, true), ZnItem("spruce", 78f), ZnItem("rock_l", 36f),
        ZnItem("tallgrass", 34f, true), ZnItem("spruce", 62f)
    )
)

private val ZN_GROUND_TINT: Array<ColorFilter?> = arrayOf(
    null, null, haze(0x66241A55), haze(0x66401408), haze(0x66C8EAFF)
)

/** Растения, которые Зелик «оживляет»: по карте разные. */
internal fun znBloomKeys(biome: Int): List<String> = when (biome) {
    0 -> listOf("daisy", "tulip", "sunflower", "lavender")
    1 -> listOf("agaric", "porcini", "mushgroup", "fern")
    2 -> listOf("mushgroup", "lavender", "porcini", "fern")
    3 -> listOf("sunflower", "lavender", "tulip")
    else -> listOf("daisy", "tulip", "lavender")
}

internal fun znBloomHeight(key: String): Float = when (key) {
    "sunflower" -> 52f; "agaric", "porcini" -> 30f; "mushgroup" -> 34f; "fern" -> 30f; else -> 44f
}

/** Растительность на земле. Координаты мировые (внутри translate/scale). */
internal fun DrawScope.drawNatureGround(g: ZNatureGfx, biome: Int, x0: Float, x1: Float, y: Float, t: Int) {
    val list = ZN_GROUND[biome.coerceIn(0, ZN_GROUND.size - 1)]
    val tint = ZN_GROUND_TINT[biome.coerceIn(0, ZN_GROUND.size - 1)]
    val step = 58f
    var k = floor(x0 / step).toInt() - 2
    while (k * step < x1 + step * 2f) {
        val r = znH(k, biome)
        if (r >= 0.3f) {
            val it = list[(znH(k, biome + 50) * list.size).toInt().coerceIn(0, list.size - 1)]
            val x = k * step + znH(k, 3) * step * 0.8f
            val sway = if (it.sway) sin(t * 0.045f + k) * 3.5f else 0f
            val hs = it.h * (0.85f + znH(k, 9) * 0.35f)
            znSprite(g, it.key, x, y + 5f, hs, tint, 1f, sway, r > 0.65f)
        }
        k++
    }
}

private fun DrawScope.znLayer(
    g: ZNatureGfx, spacing: Float, scroll: Float, seed: Int, baseY: Float, scale: Float,
    tint: ColorFilter?, alpha: Float, pick: (Int, Float) -> ZnItem?
) {
    val first = floor(scroll / spacing).toInt() - 1
    val n = (size.width / spacing).toInt() + 4
    for (i in first until first + n) {
        val r = znH(i, seed)
        val it = pick(i, r) ?: continue
        val x = i * spacing + (znH(i, seed + 7) - 0.5f) * spacing * 0.6f - scroll
        znSprite(g, it.key, x, baseY, it.h * scale * (0.85f + 0.3f * znH(i, seed + 3)), tint, alpha, 0f, r > 0.5f)
    }
}

/** Дальний и средний планы с деревьями и камнями; параллакс, экранные координаты. */
internal fun DrawScope.drawNatureBackdrop(g: ZNatureGfx, biome: Int, camX: Float, z: Float, baseY: Float, t: Int) {
    val by = baseY + 6f * z
    when (biome) {
        0 -> {
            val far = haze(0x44DDEEFF)
            znLayer(g, 540f * z, camX * z * 0.40f, 11, by, z, far, 1f) { i, r ->
                if (r < 0.12f) null else ZnItem(if (i % 3 == 0) "oak" else if (i % 3 == 1) "birch" else "cherry", if (i % 3 == 2) 230f else 270f)
            }
            znLayer(g, 760f * z, camX * z * 0.62f, 23, by + 2f * z, z, null, 1f) { _, r ->
                if (r < 0.35f) null else ZnItem(if (r < 0.6f) "bush" else if (r < 0.8f) "rock_moss" else "berries", 56f)
            }
        }
        1 -> {
            znLayer(g, 430f * z, camX * z * 0.30f, 31, by, z, haze(0x55103018), 1f) { i, r ->
                if (r < 0.1f) null else ZnItem(if (i % 3 == 0) "spruce" else if (i % 3 == 1) "birch" else "oak", 300f)
            }
            znLayer(g, 640f * z, camX * z * 0.55f, 41, by + 2f * z, z, haze(0x22082010), 1f) { i, r ->
                if (r < 0.3f) null else ZnItem(if (i % 2 == 0) "spruce" else "oak", 360f)
            }
        }
        2 -> {
            val dim = haze(0x77201848)
            znLayer(g, 600f * z, camX * z * 0.42f, 51, by, z, dim, 1f) { i, r ->
                if (r < 0.2f) null else ZnItem(if (i % 3 == 0) "rock_arch" else if (i % 3 == 1) "rock_t" else "rock_l", if (i % 3 == 0) 210f else 170f)
            }
            znLayer(g, 520f * z, camX * z * 0.66f, 53, by + 2f * z, z, haze(0x55201848), 1f) { _, r ->
                if (r < 0.45f) null else ZnItem(if (r < 0.7f) "fern" else "mushgroup", 44f)
            }
        }
        3 -> {
            znLayer(g, 560f * z, camX * z * 0.36f, 61, by, z, haze(0x88401408), 1f) { i, r ->
                if (r < 0.15f) null else ZnItem(if (i % 3 == 0) "rock_t" else if (i % 3 == 1) "rock_l" else "rock_arch", if (i % 3 == 0) 230f else 170f)
            }
            znLayer(g, 700f * z, camX * z * 0.6f, 63, by + 2f * z, z, haze(0x99200808), 1f) { _, r ->
                if (r < 0.4f) null else ZnItem(if (r < 0.7f) "rock_l" else "rock_t", 110f)
            }
        }
        else -> {
            znLayer(g, 470f * z, camX * z * 0.33f, 71, by, z, haze(0x88BFE8FF), 1f) { _, r ->
                if (r < 0.1f) null else ZnItem("spruce", 300f)
            }
            znLayer(g, 640f * z, camX * z * 0.56f, 73, by + 2f * z, z, haze(0x55A0D8F0), 1f) { _, r ->
                if (r < 0.35f) null else ZnItem(if (r < 0.75f) "spruce" else "rock_l", if (r < 0.75f) 360f else 120f)
            }
        }
    }
}

private val ZN_SLEEP = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.06f) })

/** Растение-«сонька»: серое и поникшее, пока Зелик не разбудит; потом вспыхивает красками и подпрыгивает. */
internal fun DrawScope.znBloom(g: ZNatureGfx, key: String, x: Float, baseY: Float, h: Float, awake: Boolean, age: Int, t: Int) {
    if (!awake) {
        val pulse = 0.5f + 0.5f * sin(t * 0.07f + x * 0.01f)
        drawCircle(Color.White.copy(alpha = 0.10f + 0.14f * pulse), 26f, Offset(x, baseY - h * 0.5f))
        znSprite(g, key, x, baseY, h * 0.82f, ZN_SLEEP, 0.85f, 9f)
    } else {
        val k = min(1f, age / 24f)
        val pop = 1f + 0.3f * sin(PI.toFloat() * k) * (1f - k * 0.2f)
        drawCircle(Color(0xFFFFF59D).copy(alpha = 0.22f * (1f - k * 0.5f)), 30f, Offset(x, baseY - h * 0.5f))
        znSprite(g, key, x, baseY, h * pop, null, 1f, sin(t * 0.05f + x) * 2.5f)
        for (i in 0..1) {
            val a = t * 0.06f + i * PI.toFloat() + x
            drawCircle(Color(0xFFFFF59D), 2.4f, Offset(x + cos(a) * 20f, baseY - h * 0.6f + sin(a) * 12f))
        }
    }
}
