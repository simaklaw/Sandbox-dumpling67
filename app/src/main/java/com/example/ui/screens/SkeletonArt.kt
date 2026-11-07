package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.imageResource
import com.example.R
import kotlin.math.abs
import kotlin.math.sin

// Скелетики (набор BluBlu Games «2D Animated Skeletons», слои PSB склеены в картинки, глаза перекрашены в тёплый цвет).
// 0 — обычный, 1 — с мечом, 2 — позеленевший, 3 — позеленевший с мечом. Картинки смотрят вправо.

/** Загружает четыре картинки скелетиков. */
@Composable
internal fun rememberSkeletons(): List<ImageBitmap> {
    val a = ImageBitmap.imageResource(R.drawable.sk_0)
    val b = ImageBitmap.imageResource(R.drawable.sk_1)
    val c = ImageBitmap.imageResource(R.drawable.sk_2)
    val d = ImageBitmap.imageResource(R.drawable.sk_3)
    return remember(a, b, c, d) { listOf(a, b, c, d) }
}

/**
 * Рисует скелетика, стоящего ногами на линии [bottom]; [height] — высота, [cx] — центр тела.
 * [phase] — фаза шага (радианы), [lean] — наклон в градусах (+ вперёд), [squash] — сжатие по вертикали.
 */
internal fun DrawScope.drawSkeleton(
    img: ImageBitmap, cx: Float, bottom: Float, height: Float, faceRight: Boolean,
    phase: Float, lean: Float = 0f, squash: Float = 1f, flash: Boolean = false, alpha: Float = 1f
) {
    val sc = height / img.height
    val w = img.width * sc
    val bodyW = height * 0.56f
    val dir = if (faceRight) 1f else -1f
    val bob = abs(sin(phase)) * height * 0.035f
    val tilt = sin(phase) * 4f + lean * dir
    val sq = squash * (1f + 0.03f * sin(phase * 2f))
    val center = Offset(cx + (w - bodyW) / 2f, bottom - height / 2f - bob)
    val tint = if (flash) ColorFilter.tint(Color(0xAAFFFFFF), BlendMode.SrcAtop) else null
    withTransform({
        rotate(tilt, Offset(cx, bottom))
        scale(dir, sq, Offset(cx, bottom))
    }) {
        drImg(img, center.x, center.y, sc, alpha, tint)
    }
}
