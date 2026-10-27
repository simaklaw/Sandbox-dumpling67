package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.R
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * Локации для платформера и раннера: 0 — Поля, 1 — Лес, 2 — Замок, 3 — Вулкан (рисуется кодом, без картинок).
 * Пиксель-арт: Trix (stringstar fields, kinda cute forest — «можно использовать везде, кредит приветствуется»)
 * и Medieval pixel art tileset (free). См. SOUND_CREDITS.md.
 */
internal const val BIOME_COUNT = 4
internal val BIOME_NAMES = listOf("🌾 Поля", "🌲 Лес", "🏰 Замок", "🌋 Вулкан")

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
        3 -> drawVolcanoBackdrop(camX, z, baseY, t)
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
        3 -> {
            drawRect(Color(0xFF1E0F12), Offset(x0, y), Size(w, h))
            drawRect(Color(0xFF4A2424), Offset(x0, y), Size(w, 10f))
            drawRect(Color(0xFF2E1618), Offset(x0, y + 10f), Size(w, 8f))
            var xx = floor(x0 / 96f) * 96f
            while (xx < x1) {
                val k = (xx / 96f).toInt()
                val gl = 0.6f + 0.4f * sin(k * 1.7f)
                drawRoundRect(Color(0xFFFF6F00).copy(alpha = gl), Offset(xx + 14f, y + 24f + (k % 3) * 14f), Size(34f + (k % 4) * 8f, 6f), CornerRadius(3f))
                drawRoundRect(Color(0xFFFFD54F).copy(alpha = gl * 0.8f), Offset(xx + 20f, y + 25f + (k % 3) * 14f), Size(16f, 3f), CornerRadius(2f))
                xx += 96f
            }
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
        3 -> {
            drawRoundRect(Color(0xFF2A1A22), Offset(x, y), Size(w, h), CornerRadius(6f))
            drawRoundRect(Color(0xFFFF7043), Offset(x, y), Size(w, 9f), CornerRadius(4f))
            drawRoundRect(Color(0xFFFFCA28), Offset(x + 4f, y + 1f), Size(w - 8f, 3f), CornerRadius(2f))
        }
        else -> {
            drawRoundRect(Color(0xFF2A1F3E), Offset(x, y), Size(w, h), CornerRadius(6f))
            drawRoundRect(Color(0xFFD46A6A), Offset(x, y), Size(w, 9f), CornerRadius(4f))
        }
    }
}


/** Вулкан: закат цвета лавы, дальние вулканы с кратерами, ближние холмы и летящие угольки. */
private fun DrawScope.drawVolcanoBackdrop(camX: Float, z: Float, baseY: Float, t: Int) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0508), Color(0xFF5A1308), Color(0xFFC0410F)), startY = 0f, endY = baseY), size = size)
    // дальние вулканы
    fun mountains(spacing: Float, par: Float, ph: Float, col: Color, crater: Boolean) {
        val scroll = camX * z * par
        val first = floor(scroll / spacing).toInt() - 1
        val n = (w / spacing).toInt() + 3
        for (i in first until first + n) {
            val cx = i * spacing - scroll + spacing * 0.5f
            val mh = ph * (0.75f + 0.25f * ((i * 7 % 5) / 4f))
            val path = Path().apply {
                moveTo(cx - spacing * 0.7f, baseY); lineTo(cx - spacing * 0.12f, baseY - mh)
                lineTo(cx + spacing * 0.12f, baseY - mh); lineTo(cx + spacing * 0.7f, baseY); close()
            }
            drawPath(path, col)
            if (crater) {
                val a = 0.55f + 0.25f * sin(t * 0.08f + i)
                drawCircle(Color(0xFFFF6F00).copy(alpha = a * 0.5f), spacing * 0.2f, Offset(cx, baseY - mh))
                drawRoundRect(Color(0xFFFFB300).copy(alpha = a), Offset(cx - spacing * 0.12f, baseY - mh - 4f * z), Size(spacing * 0.24f, 9f * z), CornerRadius(4f * z))
                // лавовый ручей по склону
                drawRect(Color(0xFFFF7043).copy(alpha = a * 0.7f), Offset(cx - 3f * z, baseY - mh), Size(6f * z, mh * 0.55f))
            }
        }
    }
    mountains(520f * z, 0.08f, h * 0.46f, Color(0xFF3A100C), true)
    mountains(380f * z, 0.2f, h * 0.30f, Color(0xFF260C0E), false)
    // лавовое зарево у горизонта
    drawRect(Brush.verticalGradient(listOf(Color(0x00FF6F00), Color(0x66FF6F00)), startY = baseY - h * 0.18f, endY = baseY), Offset(0f, baseY - h * 0.18f), Size(w, h * 0.18f))
    // угольки
    for (i in 0 until 26) {
        val sp = 0.3f + (i % 5) * 0.12f
        val x = (((i * 137.5f) * z - camX * z * 0.35f) % w + w) % w
        val y = baseY - (((t * (0.8f + sp * 1.6f) * z) + i * 97f * z) % (baseY * 0.95f))
        val a = (0.5f + 0.5f * sin(t * 0.1f + i)).coerceIn(0.2f, 1f)
        drawCircle(Color(0xFFFFB74D).copy(alpha = a), (1.4f + (i % 3)) * z, Offset(x + sin(t * 0.03f + i) * 8f * z, y))
    }
}
