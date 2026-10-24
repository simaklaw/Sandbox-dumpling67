package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.R
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Локации для платформера и раннера: 0 — Поля, 1 — Лес, 2 — Замок.
 * Пиксель-арт: Trix (stringstar fields, kinda cute forest — «можно использовать везде, кредит приветствуется»)
 * и Medieval pixel art tileset (free). См. SOUND_CREDITS.md.
 */
internal const val BIOME_COUNT = 3
internal val BIOME_NAMES = listOf("🌾 Поля", "🌲 Лес", "🏰 Замок")

internal fun biomeOfLevel(level: Int): Int = ((level - 1) % BIOME_COUNT + BIOME_COUNT) % BIOME_COUNT

internal class ZBiomeGfx(
    val fb0: ImageBitmap, val fb1: ImageBitmap, val fb2: ImageBitmap, val fGround: ImageBitmap,
    val fTreeBig: ImageBitmap, val fTreeSmall: ImageBitmap,
    val forBg: ImageBitmap, val forDeco: ImageBitmap,
    val casGround: ImageBitmap, val arch1: ImageBitmap, val arch3: ImageBitmap, val torch: ImageBitmap,
    val shield: ImageBitmap, val crystal: ImageBitmap,
    val barrel: ImageBitmap, val crate: ImageBitmap, val vase: ImageBitmap, val chest: ImageBitmap,
    val pillar: ImageBitmap
)

@Composable
internal fun rememberZBiomeGfx(): ZBiomeGfx = ZBiomeGfx(
    ImageBitmap.imageResource(R.drawable.fld_bg0), ImageBitmap.imageResource(R.drawable.fld_bg1),
    ImageBitmap.imageResource(R.drawable.fld_bg2), ImageBitmap.imageResource(R.drawable.fld_ground),
    ImageBitmap.imageResource(R.drawable.fld_tree_big), ImageBitmap.imageResource(R.drawable.fld_tree_small),
    ImageBitmap.imageResource(R.drawable.for_bg), ImageBitmap.imageResource(R.drawable.for_deco),
    ImageBitmap.imageResource(R.drawable.cas_ground), ImageBitmap.imageResource(R.drawable.cas_arch1),
    ImageBitmap.imageResource(R.drawable.cas_arch3), ImageBitmap.imageResource(R.drawable.cas_torch),
    ImageBitmap.imageResource(R.drawable.cas_shield), ImageBitmap.imageResource(R.drawable.cas_crystal),
    ImageBitmap.imageResource(R.drawable.cas_barrel), ImageBitmap.imageResource(R.drawable.cas_crate),
    ImageBitmap.imageResource(R.drawable.cas_vase), ImageBitmap.imageResource(R.drawable.cas_chest),
    ImageBitmap.imageResource(R.drawable.cas_pillar1)
)

internal fun DrawScope.pxImg(im: ImageBitmap, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f) {
    drawImage(
        im, dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
        dstSize = IntSize(max(1, w.roundToInt()), max(1, h.roundToInt())),
        alpha = alpha, filterQuality = FilterQuality.None
    )
}

/** Повторяет картинку по горизонтали на весь экран, сдвигая на scroll пикселей. */
private fun DrawScope.tileX(im: ImageBitmap, scroll: Float, y: Float, w: Float, h: Float, alpha: Float = 1f) {
    val wi = max(1, w.roundToInt())
    val start = -(((scroll.roundToInt() % wi) + wi) % wi)
    var x = start
    while (x < size.width) {
        pxImg(im, x.toFloat(), y, wi.toFloat() + 1f, h, alpha)
        x += wi
    }
}

/** Декор по параллаксу: каждый spacing пикселей экрана — один предмет, вариант выбирается по номеру. */
private fun DrawScope.parallaxItems(
    spacing: Float, scroll: Float, pick: (Int) -> ImageBitmap?, scale: Float, bottomY: Float, alpha: Float = 1f,
    yOff: (Int) -> Float = { 0f }
) {
    val first = floor(scroll / spacing).toInt() - 1
    val n = (size.width / spacing).toInt() + 3
    for (i in first until first + n) {
        val im = pick(i) ?: continue
        val x = i * spacing - scroll
        val w = im.width * scale
        val h = im.height * scale
        pxImg(im, x - w / 2f, bottomY - h + yOff(i), w, h, alpha)
    }
}

internal fun DrawScope.drawBiomeBackdrop(b: ZBiomeGfx, biome: Int, camX: Float, camY: Float, z: Float, groundY: Float, t: Int) {
    val sh = size.height
    val baseY = (groundY - camY) * z
    when (biome) {
        0 -> {
            val s = sh / 180f
            val w = 288f * s
            tileX(b.fb0, camX * z * 0.03f, 0f, w, sh)
            tileX(b.fb1, camX * z * 0.10f, 0f, w, sh)
            tileX(b.fb2, camX * z * 0.22f, 0f, w, sh)
            val sc = 2.0f * z
            parallaxItems(760f * z, camX * z * 0.5f, { i -> if (i % 3 == 0) b.fTreeBig else if (i % 3 == 1) b.fTreeSmall else null }, sc, baseY + 6f * z)
        }
        1 -> {
            val s = sh / 144f
            tileX(b.forBg, camX * z * 0.03f, 0f, 256f * s, sh)
            parallaxItems(260f * z, camX * z * 0.22f, { i -> if (i % 4 == 3) null else b.forDeco }, 3.2f * z, baseY + 4f * z, 0.45f,
                { i -> if (i % 2 == 0) 0f else -10f * z })
            parallaxItems(190f * z, camX * z * 0.45f, { i -> if (i % 3 == 0) null else b.forDeco }, 4.6f * z, baseY + 6f * z, 0.8f,
                { i -> if (i % 2 == 0) 0f else 8f * z })
        }
        else -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0E0C1E), Color(0xFF2A2350), Color(0xFF3A2F66))), size = size)
            val sc = 3.4f * z
            parallaxItems(330f * z, camX * z * 0.28f, { i -> if (i % 2 == 0) b.arch1 else b.arch3 }, sc, baseY + 2f * z)
            parallaxItems(330f * z, camX * z * 0.28f - 165f * z, { i -> if (i % 3 == 0) b.crystal else b.torch }, sc * 0.9f, baseY - 60f * z)
        }
    }
}

/** Земля (платформа №0). Координаты — мировые (внутри translate/scale). */
internal fun DrawScope.drawBiomeGround(b: ZBiomeGfx, biome: Int, x0: Float, x1: Float, y: Float, h: Float) {
    val w = x1 - x0
    when (biome) {
        0 -> {
            drawRect(Color(0xFF061E26), Offset(x0, y + 30f), Size(w, h))
            val tw = 288f * 3f
            var xx = floor(x0 / tw) * tw
            while (xx < x1) { pxImg(b.fGround, xx, y - 6f, tw + 1f, 18f * 3f); xx += tw }
            drawRect(Color(0xFF061E26), Offset(x0, y + 48f), Size(w, h))
        }
        1 -> {
            drawRect(Color(0xFF2B1A2E), Offset(x0, y), Size(w, h))
            drawRect(Color(0xFFC2CC3A), Offset(x0, y), Size(w, 10f))
            drawRect(Color(0xFF6E8B2C), Offset(x0, y + 10f), Size(w, 7f))
            var xx = floor(x0 / 36f) * 36f
            while (xx < x1) { drawRect(Color(0xFF6E8B2C), Offset(xx, y + 17f), Size(10f, 5f)); xx += 36f }
        }
        else -> {
            drawRect(Color(0xFF14122A), Offset(x0, y), Size(w, h))
            val tw = 192f * 2.2f
            var xx = floor(x0 / tw) * tw
            while (xx < x1) { pxImg(b.casGround, xx, y, tw + 1f, 48f * 2.2f); xx += tw }
        }
    }
}

/** Парящие платформы. */
internal fun DrawScope.drawBiomePlatform(biome: Int, x: Float, y: Float, w: Float, h: Float) {
    when (biome) {
        0 -> {
            drawRoundRect(Color(0xFF123A44), Offset(x, y), Size(w, h), CornerRadius(8f))
            drawRoundRect(Color(0xFF6B9B5C), Offset(x, y), Size(w, 9f), CornerRadius(5f))
        }
        1 -> {
            drawRoundRect(Color(0xFF4A2C40), Offset(x, y), Size(w, h), CornerRadius(8f))
            drawRoundRect(Color(0xFFD6906A), Offset(x, y), Size(w, 10f), CornerRadius(5f))
        }
        else -> {
            drawRoundRect(Color(0xFF2A1F3E), Offset(x, y), Size(w, h), CornerRadius(6f))
            drawRoundRect(Color(0xFFD46A6A), Offset(x, y), Size(w, 9f), CornerRadius(4f))
        }
    }
}
