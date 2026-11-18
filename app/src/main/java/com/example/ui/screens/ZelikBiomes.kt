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
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * Локации для платформера и раннера: 0 — Поля, 1 — Лес, 2 — Замок, 3 — Вулкан (рисуется кодом, без картинок).
 * Пиксель-арт: Trix (stringstar fields, kinda cute forest — «можно использовать везде, кредит приветствуется»)
 * и Medieval pixel art tileset (free). См. SOUND_CREDITS.md.
 */
internal const val BIOME_COUNT = 4
internal val BIOME_NAMES = listOf("🌾 Поля", "🌲 Лес", "🏰 Замок", "🌋 Вулкан")

/** В «Зелике» пять карт: к четырём общим добавлена 4 — «Ледяная пещера» (в раннере монстриков её нет). */
internal const val ZBIOME_COUNT = 5
internal val ZBIOME_NAMES = BIOME_NAMES + "❄️ Лёд"

internal fun biomeOfLevel(level: Int): Int = ((level - 1) % ZBIOME_COUNT + ZBIOME_COUNT) % ZBIOME_COUNT

internal class ZBiomeGfx(
    val fb0: ImageBitmap, val fb1: ImageBitmap, val fb2: ImageBitmap, val fGround: ImageBitmap,
    val fTreeBig: ImageBitmap, val fTreeSmall: ImageBitmap,
    val forBg: ImageBitmap, val forDeco: ImageBitmap,
    val casGround: ImageBitmap, val arch1: ImageBitmap, val arch3: ImageBitmap, val torch: ImageBitmap,
    val shield: ImageBitmap, val crystal: ImageBitmap,
    val barrel: ImageBitmap, val crate: ImageBitmap, val vase: ImageBitmap, val chest: ImageBitmap,
    val pillar: ImageBitmap,
    val nat: ZNatureGfx
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
    ImageBitmap.imageResource(R.drawable.cas_pillar1),
    rememberZNatureGfx()
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
    drawBiomeBackdropBase(b, biome, camX, camY, z, groundY, t)
    drawBiomeAtmosphere(biome, camX, z, (groundY - camY) * z, t)
}

private fun DrawScope.drawBiomeBackdropBase(b: ZBiomeGfx, biome: Int, camX: Float, camY: Float, z: Float, groundY: Float, t: Int) {
    val sh = size.height
    val baseY = (groundY - camY) * z
    when (biome) {
        0 -> {
            val s = sh / 180f
            val w = 288f * s
            tileX(b.fb0, camX * z * 0.03f, 0f, w, sh)
            tileX(b.fb1, camX * z * 0.10f, 0f, w, sh)
            tileX(b.fb2, camX * z * 0.22f, 0f, w, sh)
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
        4 -> drawIceBackdrop(camX, z, baseY, t)
        else -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0E0C1E), Color(0xFF2A2350), Color(0xFF3A2F66))), size = size)
            val sc = 3.4f * z
            parallaxItems(330f * z, camX * z * 0.28f, { i -> if (i % 2 == 0) b.arch1 else b.arch3 }, sc, baseY + 2f * z)
            parallaxItems(330f * z, camX * z * 0.28f - 165f * z, { i -> if (i % 3 == 0) b.crystal else b.torch }, sc * 0.9f, baseY - 60f * z)
        }
    }
    drawNatureBackdrop(b.nat, biome, camX, z, baseY, t)
}

/** Земля (платформа №0). Координаты — мировые (внутри translate/scale). */
internal fun DrawScope.drawBiomeGround(b: ZBiomeGfx, biome: Int, x0: Float, x1: Float, y: Float, h: Float, t: Int = 0) {
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
        4 -> {
            drawRect(Color(0xFF0B2A3F), Offset(x0, y), Size(w, h))
            drawRect(Color(0xFF1B5E7B), Offset(x0, y), Size(w, 16f))
            drawRect(Color(0xFFB3E5FC), Offset(x0, y), Size(w, 9f))
            drawRect(Color(0xFFE1F5FE), Offset(x0, y), Size(w, 3f))
            var xx = floor(x0 / 64f) * 64f
            while (xx < x1) {
                val k = (xx / 64f).toInt()
                // снежные шапки и ледяные прожилки
                drawRoundRect(Color(0xFFFFFFFF), Offset(xx + 6f, y - 5f), Size(30f + (k % 3) * 8f, 8f), CornerRadius(4f))
                drawRect(Color(0xFF4FC3F7).copy(alpha = 0.55f), Offset(xx + 10f, y + 22f + (k % 4) * 12f), Size(3f, 14f + (k % 3) * 8f))
                drawRect(Color(0xFF0E3A52), Offset(xx, y + 16f), Size(2f, h))
                xx += 64f
            }
        }
        else -> {
            drawRect(Color(0xFF14122A), Offset(x0, y), Size(w, h))
            val tw = 192f * 2.2f
            var xx = floor(x0 / tw) * tw
            while (xx < x1) { pxImg(b.casGround, xx, y, tw + 1f, 48f * 2.2f); xx += tw }
        }
    }
    drawGroundDetail(biome, x0, x1, y, t)
    drawNatureGround(b.nat, biome, x0, x1, y, t)
    // мягкая тень под кромкой земли и светлая линия сверху: земля «лежит» объёмнее
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent), startY = y, endY = y + 46f), Offset(x0, y), Size(w, 46f))
    drawRect(Color.White.copy(alpha = 0.18f), Offset(x0, y), Size(w, 2f))
}

/** Парящие платформы. */
internal fun DrawScope.drawBiomePlatform(biome: Int, x: Float, y: Float, w: Float, h: Float) {
    drawBiomePlatformBase(biome, x, y, w, h)
    drawPlatformDetail(biome, x, y, w, h)
}

private fun DrawScope.drawBiomePlatformBase(biome: Int, x: Float, y: Float, w: Float, h: Float) {
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
        4 -> {
            drawRoundRect(Color(0xFF1B5E7B), Offset(x, y), Size(w, h), CornerRadius(7f))
            drawRoundRect(Color(0xFFB3E5FC), Offset(x, y), Size(w, 10f), CornerRadius(5f))
            drawRoundRect(Color(0xFFFFFFFF), Offset(x + 6f, y - 3f), Size(min(w - 12f, 46f), 6f), CornerRadius(3f))
            drawRect(Color(0xFFE1F5FE).copy(alpha = 0.5f), Offset(x + 10f, y + 12f), Size(3f, max(2f, h - 14f)))
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


/** Ледяная пещера: сумрачный свод, северное сияние, сосульки сверху, далёкие кристаллы и падающий снег. */
private fun DrawScope.drawIceBackdrop(camX: Float, z: Float, baseY: Float, t: Int) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF050B1E), Color(0xFF0D2B4A), Color(0xFF1F5F7E)), startY = 0f, endY = baseY), size = size)
    // северное сияние: две медленные ленты
    for (band in 0 until 2) {
        val col = if (band == 0) Color(0xFF69F0AE) else Color(0xFF80D8FF)
        val steps = 24
        for (i in 0 until steps) {
            val x = w * i / steps
            val y = h * (0.16f + 0.10f * band) + sin(i * 0.5f + t * 0.015f + band * 2f) * h * 0.045f
            drawRect(
                Brush.verticalGradient(listOf(col.copy(alpha = 0.0f), col.copy(alpha = 0.16f), col.copy(alpha = 0f)), startY = y - h * 0.10f, endY = y + h * 0.12f),
                Offset(x, y - h * 0.10f), Size(w / steps + 1f, h * 0.22f)
            )
        }
    }
    // дальние ледяные горы
    fun peaks(spacing: Float, par: Float, ph: Float, col: Color, snow: Color) {
        val scroll = camX * z * par
        val first = floor(scroll / spacing).toInt() - 1
        val n = (w / spacing).toInt() + 3
        for (i in first until first + n) {
            val cx = i * spacing - scroll + spacing * 0.5f
            val mh = ph * (0.7f + 0.3f * ((i * 5 % 7) / 6f))
            drawPath(Path().apply {
                moveTo(cx - spacing * 0.65f, baseY); lineTo(cx, baseY - mh); lineTo(cx + spacing * 0.65f, baseY); close()
            }, col)
            drawPath(Path().apply {
                moveTo(cx - spacing * 0.17f, baseY - mh * 0.74f); lineTo(cx, baseY - mh); lineTo(cx + spacing * 0.17f, baseY - mh * 0.74f)
                lineTo(cx + spacing * 0.06f, baseY - mh * 0.68f); lineTo(cx - spacing * 0.03f, baseY - mh * 0.76f); close()
            }, snow)
        }
    }
    peaks(560f * z, 0.06f, h * 0.42f, Color(0xFF123A5C), Color(0xFFCFEFFF))
    peaks(400f * z, 0.16f, h * 0.30f, Color(0xFF0C2B45), Color(0xFFB3E5FC))
    // сосульки на своде (параллакс)
    val sp = 130f * z
    val scroll2 = camX * z * 0.35f
    val f2 = floor(scroll2 / sp).toInt() - 1
    for (i in f2 until f2 + (w / sp).toInt() + 3) {
        val x = i * sp - scroll2
        val len = (36f + ((i * 37) % 7) * 14f) * z
        drawPath(Path().apply { moveTo(x - 16f * z, 0f); lineTo(x, len); lineTo(x + 16f * z, 0f); close() }, Color(0xFF9FD8F0).copy(alpha = 0.55f))
        drawPath(Path().apply { moveTo(x - 6f * z, 0f); lineTo(x, len * 0.8f); lineTo(x + 3f * z, 0f); close() }, Color.White.copy(alpha = 0.45f))
    }
    // кристаллы у земли
    val sp3 = 300f * z
    val scroll3 = camX * z * 0.5f
    val f3 = floor(scroll3 / sp3).toInt() - 1
    for (i in f3 until f3 + (w / sp3).toInt() + 3) {
        val x = i * sp3 - scroll3 + (i * 53 % 90) * z
        val ch = (38f + (i * 29 % 5) * 12f) * z
        val a = 0.55f + 0.2f * sin(t * 0.05f + i)
        drawPath(Path().apply { moveTo(x - 12f * z, baseY); lineTo(x - 4f * z, baseY - ch); lineTo(x + 8f * z, baseY - ch * 0.8f); lineTo(x + 14f * z, baseY); close() }, Color(0xFF4DD0E1).copy(alpha = a))
        drawPath(Path().apply { moveTo(x - 4f * z, baseY); lineTo(x - 4f * z, baseY - ch); lineTo(x + 2f * z, baseY - ch * 0.5f); close() }, Color.White.copy(alpha = 0.35f))
    }
    // снег
    for (i in 0 until 40) {
        val sp4 = 0.4f + (i % 5) * 0.15f
        val x = (((i * 97.3f) * z - camX * z * 0.25f + sin(t * 0.02f + i) * 14f * z) % w + w) % w
        val y = ((t * (0.9f + sp4) * z + i * 61f * z) % baseY)
        drawCircle(Color.White.copy(alpha = 0.35f + 0.4f * sp4), (1.2f + (i % 3) * 0.7f) * z, Offset(x, y))
    }
}


// ======================= детали земли и платформ =======================
private fun hsh(i: Int): Float = (((i * 1103515245 + 12345) ushr 8) and 0xFFFF) / 65535f

private fun DrawScope.blade(x: Float, y: Float, h: Float, lean: Float, col: Color) {
    drawPath(Path().apply { moveTo(x - 3f, y); lineTo(x + lean, y - h); lineTo(x + 3.5f, y); close() }, col)
}

/** Фактура земли: травинки, цветы, камни, трещины лавы и мороза. Координаты мировые. */
private fun DrawScope.drawGroundDetail(biome: Int, x0: Float, x1: Float, y: Float, t: Int) {
    val step = 22f
    var k = floor(x0 / step).toInt() - 1
    while (k * step < x1 + step) {
        val r = hsh(k); val r2 = hsh(k + 977)
        val x = k * step + r * 14f
        when (biome) {
            0 -> {
                blade(x, y + 3f, 8f + r2 * 9f, (r - 0.5f) * 8f, Color(0xFF3F8F34))
                blade(x + 7f, y + 3f, 6f + r * 7f, (r2 - 0.5f) * 8f, Color(0xFF7BC553))
                if (k % 5 == 0) {
                    val fc = when ((k / 5).mod(4)) { 0 -> Color(0xFFFF80AB); 1 -> Color(0xFFFFEB3B); 2 -> Color.White; else -> Color(0xFF80D8FF) }
                    val fh = 13f + r2 * 8f
                    drawLine(Color(0xFF2E7D32), Offset(x, y + 2f), Offset(x + 1f, y - fh), 2f)
                    drawCircle(fc, 4.2f, Offset(x + 1f, y - fh))
                    drawCircle(Color(0xFFFFB300), 1.7f, Offset(x + 1f, y - fh))
                }
            }
            1 -> {
                blade(x, y + 5f, 9f + r2 * 10f, (r - 0.5f) * 10f, Color(0xFF9BB62E))
                blade(x + 8f, y + 5f, 7f + r * 8f, (r2 - 0.5f) * 8f, Color(0xFFD4E04A))
                if (k % 6 == 0) { // грибок
                    drawRect(Color(0xFFF5E6C8), Offset(x - 2f, y - 7f), Size(4f, 8f))
                    drawArc(Color(0xFFE53935), 180f, 180f, true, Offset(x - 8f, y - 15f), Size(16f, 14f))
                    drawCircle(Color.White, 1.8f, Offset(x - 3f, y - 10f)); drawCircle(Color.White, 1.4f, Offset(x + 3f, y - 11f))
                }
                if (k % 3 == 1) { // камешки и корни в земле
                    drawOval(Color(0xFF4A3550), Offset(x, y + 28f + r2 * 40f), Size(9f + r * 9f, 6f))
                    drawLine(Color(0xFF3A2540), Offset(x, y + 18f), Offset(x + 10f + r * 12f, y + 38f + r2 * 14f), 2.5f)
                }
            }
            2 -> {
                if (k % 4 == 0) { // кладка и мелкий мусор
                    drawRect(Color.Black.copy(alpha = 0.22f), Offset(x, y + 22f + (k % 3) * 22f), Size(3f, 20f))
                }
                if (k % 7 == 2) {
                    drawPath(Path().apply { moveTo(x, y); lineTo(x + 5f, y - 7f); lineTo(x + 11f, y - 2f); lineTo(x + 12f, y); close() }, Color(0xFF6B6B88))
                }
                if (k % 9 == 4) blade(x, y + 1f, 6f, 1f, Color(0xFF3E7C4A)) // мох
            }
            3 -> {
                if (k % 4 == 0) {
                    val a = (0.55f + 0.35f * sin(t * 0.06f + k)).coerceIn(0.2f, 1f)
                    val px = x; var py = y + 6f
                    val path = Path().apply { moveTo(px, py); for (q in 1..4) { py += 10f + hsh(k + q) * 6f; lineTo(px + (hsh(k * 3 + q) - 0.5f) * 26f * q * 0.5f, py) } }
                    drawPath(path, Color(0xFF1A0A0C), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                    drawPath(path, Color(0xFFFF7A18).copy(alpha = a), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                    drawPath(path, Color(0xFFFFE082).copy(alpha = a * 0.8f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f))
                }
                if (k % 5 == 2) drawOval(Color(0xFF2B1618), Offset(x, y - 5f), Size(12f + r * 10f, 9f))
            }
            4 -> {
                if (k % 3 == 0) {
                    val path = Path().apply { moveTo(x, y + 5f); lineTo(x + 8f, y + 16f); lineTo(x + 3f, y + 26f); lineTo(x + 12f, y + 40f + r * 10f) }
                    drawPath(path, Color(0xFFB3E5FC).copy(alpha = 0.55f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                }
                if (k % 4 == 1) { // искры мороза
                    val tw = (0.5f + 0.5f * sin(t * 0.08f + k * 1.7f))
                    drawCircle(Color.White.copy(alpha = 0.3f + 0.6f * tw), 1.5f + 1.5f * tw, Offset(x + 6f, y - 3f - r2 * 4f))
                }
                if (k % 6 == 3) { // ледяной зубец на кромке
                    drawPath(Path().apply { moveTo(x, y); lineTo(x + 6f, y - 14f - r * 8f); lineTo(x + 13f, y); close() }, Color(0xFFCFEFFF))
                }
            }
        }
        k++
    }
}

private fun DrawScope.drawPlatformDetail(biome: Int, x: Float, y: Float, w: Float, h: Float) {
    // тень под платформой: объём
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent), startY = y + h, endY = y + h + 16f), Offset(x + 3f, y + h), Size(w - 6f, 16f))
    var xx = x + 6f
    var i = (x / 20f).toInt()
    while (xx < x + w - 6f) {
        val r = hsh(i++)
        when (biome) {
            0 -> blade(xx, y + 3f, 5f + r * 5f, (r - 0.5f) * 6f, Color(0xFF8DD06B))
            1 -> if (r > 0.45f) { // свисающий мох
                drawRect(Color(0xFF7A9A2A), Offset(xx, y + h - 2f), Size(3f, 6f + r * 9f))
                drawCircle(Color(0xFF9BB62E), 3f, Offset(xx + 1.5f, y + h + 5f + r * 9f))
            }
            2 -> drawRect(Color.Black.copy(alpha = 0.2f), Offset(xx, y + 10f), Size(2f, max(2f, h - 12f)))
            3 -> if (r > 0.5f) drawRect(Color(0xFFFF8F00).copy(alpha = 0.6f), Offset(xx, y + 10f), Size(3f, max(2f, min(h - 12f, 8f + r * 10f))))
            4 -> if (r > 0.4f) { // сосульки
                drawPath(Path().apply { moveTo(xx - 4f, y + h - 1f); lineTo(xx, y + h + 8f + r * 14f); lineTo(xx + 4f, y + h - 1f); close() }, Color(0xFFCFEFFF).copy(alpha = 0.9f))
            }
        }
        xx += 20f
    }
}

// ======================= атмосфера: свет, пыльца, светлячки, пепел, снег =======================
/** Эффекты на заднем плане (экранные координаты). */
private fun DrawScope.drawBiomeAtmosphere(biome: Int, camX: Float, z: Float, baseY: Float, t: Int) {
    val w = size.width
    val h = size.height
    when (biome) {
        0 -> {
            // солнечное свечение и плывущие облачка
            drawCircle(Brush.radialGradient(listOf(Color(0x66FFF59D), Color(0x00FFF59D)), Offset(w * 0.85f, h * 0.08f), w * 0.55f), w * 0.55f, Offset(w * 0.85f, h * 0.08f))
            for (i in 0 until 4) {
                val cx = ((i * 0.37f + t * 0.00018f - camX * z * 0.00004f) % 1.3f + 1.3f) % 1.3f * w - w * 0.15f
                val cy = h * (0.10f + (i % 3) * 0.07f)
                val c = Color.White.copy(alpha = 0.5f)
                drawOval(c, Offset(cx - 60f * z, cy), Size(120f * z, 26f * z))
                drawOval(c, Offset(cx - 28f * z, cy - 14f * z), Size(70f * z, 30f * z))
            }
            // лепестки и пыльца
            for (i in 0 until 22) {
                val sp = 0.4f + (i % 4) * 0.15f
                val x = (((i * 83.1f) * z + t * 0.7f * sp * z - camX * z * 0.3f) % w + w) % w
                val y = (((i * 57.7f) * z + t * 0.5f * sp * z + sin(t * 0.03f + i) * 20f * z) % baseY + baseY) % baseY
                val c = if (i % 3 == 0) Color(0xCCFFC1D9) else if (i % 3 == 1) Color(0xBBFFFFFF) else Color(0xAAFFF59D)
                drawOval(c, Offset(x, y), Size((4f + i % 3) * z, (2.4f + i % 2) * z))
            }
        }
        1 -> {
            // лучи света сквозь листву
            for (i in 0 until 5) {
                val x = ((i * 0.23f + 0.05f) * w - camX * z * 0.05f % w + w) % w
                val sway = sin(t * 0.012f + i * 1.7f) * 0.04f * w
                val a = 0.07f + 0.04f * sin(t * 0.02f + i)
                val path = Path().apply {
                    moveTo(x + sway, 0f); lineTo(x + sway + 0.07f * w, 0f)
                    lineTo(x + sway - 0.12f * w, baseY); lineTo(x + sway - 0.22f * w, baseY); close()
                }
                drawPath(path, Color(0xFFF6FFB0).copy(alpha = a))
            }
            // светлячки
            for (i in 0 until 18) {
                val x = (((i * 137.3f) * z - camX * z * 0.4f + sin(t * 0.02f + i * 2.1f) * 30f * z) % w + w) % w
                val y = baseY * (0.35f + 0.6f * hsh(i * 7)) + cos(t * 0.025f + i) * 14f * z
                val blink = (0.5f + 0.5f * sin(t * 0.05f + i * 1.3f)).coerceIn(0f, 1f)
                drawCircle(Brush.radialGradient(listOf(Color(0xCCEAFF6A).copy(alpha = 0.75f * blink), Color(0x00EAFF6A)), Offset(x, y), 14f * z), 14f * z, Offset(x, y))
                drawCircle(Color(0xFFF9FFD0).copy(alpha = blink), 2.2f * z, Offset(x, y))
            }
            // падающие листья
            for (i in 0 until 10) {
                val sp = 0.5f + (i % 3) * 0.2f
                val x = (((i * 151f) * z + t * 0.4f * z - camX * z * 0.35f + sin(t * 0.04f + i) * 26f * z) % w + w) % w
                val y = ((t * sp * 0.9f * z + i * 88f * z) % baseY)
                drawOval(if (i % 2 == 0) Color(0xCC8BC34A) else Color(0xCCFF9800), Offset(x, y), Size(7f * z, 4f * z))
            }
        }
        2 -> {
            // тёплый свет факелов: мерцающие пятна в тех же местах, что и факелы фона
            val sp = 330f * z
            val scroll = camX * z * 0.28f - 165f * z
            val f = floor(scroll / sp).toInt() - 1
            for (i in f until f + (w / sp).toInt() + 3) {
                if (i % 3 == 0) continue
                val x = i * sp - scroll
                val fl = 0.75f + 0.25f * sin(t * 0.3f + i * 2f) * sin(t * 0.17f + i)
                val cy = baseY - 150f * z
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB74D).copy(alpha = 0.34f * fl), Color(0x00FF9800)), Offset(x, cy), 150f * z), 150f * z, Offset(x, cy))
            }
            // пылинки в свете
            for (i in 0 until 26) {
                val x = (((i * 101.7f) * z - camX * z * 0.2f + sin(t * 0.01f + i) * 24f * z) % w + w) % w
                val y = (((i * 63.3f) * z - t * 0.18f * z * (0.5f + (i % 3) * 0.3f)) % baseY + baseY) % baseY
                drawCircle(Color(0xFFFFE0B2).copy(alpha = 0.25f + 0.25f * sin(t * 0.04f + i)), (1f + i % 2) * z, Offset(x, y))
            }
        }
        3 -> {
            // пепел и марево
            for (i in 0 until 24) {
                val sp = 0.5f + (i % 4) * 0.18f
                val x = (((i * 91.7f) * z + t * 0.45f * z - camX * z * 0.3f + sin(t * 0.03f + i) * 18f * z) % w + w) % w
                val y = ((t * sp * 0.8f * z + i * 71f * z) % baseY)
                drawOval(Color(0xFF9E9E9E).copy(alpha = 0.35f), Offset(x, y), Size((3f + i % 3) * z, (2f + i % 2) * z))
            }
            for (i in 0 until 4) { // колышущееся марево у горизонта
                val y = baseY - (10f + i * 18f) * z
                val sh = sin(t * 0.05f + i * 1.9f) * 14f * z
                drawRect(Color(0xFFFF8F00).copy(alpha = 0.06f), Offset(sh, y), Size(w, 7f * z))
            }
        }
        4 -> {
            // морозная дымка у земли
            for (i in 0 until 3) {
                val cx = ((i * 0.5f + t * 0.0004f) % 1.6f - 0.3f) * w
                drawOval(Color(0xFFB3E5FC).copy(alpha = 0.10f), Offset(cx - 200f * z, baseY - 34f * z), Size(400f * z, 60f * z))
            }
        }
    }
}

/** Эффекты поверх мира (экранные координаты): виньетка цвета карты, дымка у земли, крупные близкие частицы. */
internal fun DrawScope.drawBiomeForeground(biome: Int, camX: Float, z: Float, t: Int) {
    val w = size.width
    val h = size.height
    val tint = when (biome) {
        0 -> Color(0x2E1B5E20); 1 -> Color(0x5A0A2A12); 2 -> Color(0x66120A30); 3 -> Color(0x66400A00); else -> Color(0x4A06223A)
    }
    drawRect(Brush.radialGradient(listOf(Color.Transparent, tint), center = Offset(w / 2f, h * 0.55f), radius = max(w, h) * 0.78f), size = size)
    // близкие частицы, пролетающие быстрее мира
    val n = 7
    for (i in 0 until n) {
        val sp = 1.6f + (i % 3) * 0.5f
        val x = (((i * 211f) * z - camX * z * 0.9f + t * 0.6f * sp * z) % (w + 60f * z) + w + 60f * z) % (w + 60f * z) - 30f * z
        val y = ((i * 0.151f + t * 0.0011f * sp) % 1f) * h
        when (biome) {
            0 -> drawOval(Color.White.copy(alpha = 0.55f), Offset(x, y), Size(10f * z, 5f * z))
            1 -> drawOval(Color(0xAA7CB342), Offset(x, y), Size(13f * z, 7f * z))
            2 -> drawCircle(Color(0xFFFFB74D).copy(alpha = 0.35f), 3f * z, Offset(x, h - y * 0.5f))
            3 -> drawCircle(Brush.radialGradient(listOf(Color(0xFFFFCA28), Color(0x00FF6F00)), Offset(x, h - y * 0.7f), 9f * z), 9f * z, Offset(x, h - y * 0.7f))
            else -> drawCircle(Color.White.copy(alpha = 0.8f), 3.2f * z, Offset(x, y))
        }
    }
    // низкая дымка поверх земли
    val mist = when (biome) { 0 -> Color(0x14FFFFFF); 1 -> Color(0x22CFE8C0); 2 -> Color(0x1A9FA8DA); 3 -> Color(0x22FF7043); else -> Color(0x26CFEFFF) }
    for (i in 0 until 2) {
        val cx = ((i * 0.6f + t * 0.0006f) % 1.5f - 0.25f) * w
        drawOval(mist, Offset(cx - 260f * z, h * 0.86f), Size(520f * z, 70f * z))
    }
}
