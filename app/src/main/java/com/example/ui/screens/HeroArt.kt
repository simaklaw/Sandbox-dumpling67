package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.R
import kotlin.math.max

// Герои из модульного набора (9 штук): у каждого отдельно голова, тело и две ножки (ch_h/ch_b/ch_lf/ch_rf 0..8).
// Раньше жили в комнате «Гардероб героев»; теперь это враги в «Зелике» и в «Беге монстриков».
// Порядок героев: 0 Лучник, 1 Пещерный, 2 Клоун, 3 Монах, 4 Ниндзя, 5 Пират, 6 Каска, 7 Богатырь, 8 Волшебник.

/** Смещения относительно центра тела (пиксели картинок): голова x,y; левая нога x,y; правая нога x,y. */
internal val HERO_OFF = arrayOf(
    intArrayOf(0, -63, -7, 38, 36, 38),
    intArrayOf(1, -67, -19, 37, 24, 36),
    intArrayOf(-2, -51, -17, 35, 25, 34),
    intArrayOf(1, -42, -18, 36, 25, 36),
    intArrayOf(1, -43, -18, 37, 25, 37),
    intArrayOf(-3, -59, -19, 34, 28, 37),
    intArrayOf(2, -60, -18, 37, 26, 37),
    intArrayOf(0, -46, -17, 33, 25, 33),
    intArrayOf(3, -53, -17, 34, 24, 34)
)

internal const val HERO_COUNT = 9

/** Картинки героев и размеры «от центра тела»: верх головы и низ ступней в пикселях картинки. */
internal class HeroSet(
    val heads: List<ImageBitmap>,
    val bodies: List<ImageBitmap>,
    val lfs: List<ImageBitmap>,
    val rfs: List<ImageBitmap>
) {
    /** Расстояние от центра тела до макушки (пиксели картинки). */
    val topPx = FloatArray(HERO_COUNT) { i -> -HERO_OFF[i][1] + heads[i].height / 2f }

    /** Расстояние от центра тела до подошв (пиксели картинки). */
    val botPx = FloatArray(HERO_COUNT) { i ->
        max(HERO_OFF[i][3] + lfs[i].height / 2f, HERO_OFF[i][5] + rfs[i].height / 2f)
    }
}

@Composable
internal fun rememberHeroSet(): HeroSet {
    val heads = listOf(R.drawable.ch_h0, R.drawable.ch_h1, R.drawable.ch_h2, R.drawable.ch_h3, R.drawable.ch_h4, R.drawable.ch_h5, R.drawable.ch_h6, R.drawable.ch_h7, R.drawable.ch_h8).map { ImageBitmap.imageResource(it) }
    val bodies = listOf(R.drawable.ch_b0, R.drawable.ch_b1, R.drawable.ch_b2, R.drawable.ch_b3, R.drawable.ch_b4, R.drawable.ch_b5, R.drawable.ch_b6, R.drawable.ch_b7, R.drawable.ch_b8).map { ImageBitmap.imageResource(it) }
    val lfs = listOf(R.drawable.ch_lf0, R.drawable.ch_lf1, R.drawable.ch_lf2, R.drawable.ch_lf3, R.drawable.ch_lf4, R.drawable.ch_lf5, R.drawable.ch_lf6, R.drawable.ch_lf7, R.drawable.ch_lf8).map { ImageBitmap.imageResource(it) }
    val rfs = listOf(R.drawable.ch_rf0, R.drawable.ch_rf1, R.drawable.ch_rf2, R.drawable.ch_rf3, R.drawable.ch_rf4, R.drawable.ch_rf5, R.drawable.ch_rf6, R.drawable.ch_rf7, R.drawable.ch_rf8).map { ImageBitmap.imageResource(it) }
    return remember { HeroSet(heads, bodies, lfs, rfs) }
}

/** Рисует картинку по центру (cx, cy) с масштабом sc; общий помощник для многих комнат. */
internal fun DrawScope.drImg(img: ImageBitmap, cx: Float, cy: Float, sc: Float, alpha: Float = 1f, tint: ColorFilter? = null) {
    val w = img.width * sc; val h = img.height * sc
    drawImage(
        img, IntOffset.Zero, IntSize(img.width, img.height),
        IntOffset(Math.round(cx - w / 2f), Math.round(cy - h / 2f)), IntSize(max(1, Math.round(w)), max(1, Math.round(h))),
        alpha, colorFilter = tint, filterQuality = FilterQuality.Medium
    )
}
