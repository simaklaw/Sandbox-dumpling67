package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.R
import kotlin.math.roundToInt

/** Спрайт-лист анимации огненного шара: голова справа, хвост слева. */
class FireSheet(val img: ImageBitmap, val cols: Int, val frames: Int, val fw: Int, val fh: Int)

@Composable
fun rememberFireA(): FireSheet {
    val img = ImageBitmap.imageResource(R.drawable.fx_fire_a)
    return remember(img) { FireSheet(img, 7, 28, 160, 92) }
}

@Composable
fun rememberFireB(): FireSheet {
    val img = ImageBitmap.imageResource(R.drawable.fx_fire_b)
    return remember(img) { FireSheet(img, 5, 25, 120, 98) }
}

/**
 * Рисует кадр огненного шара с центром в (x, y); length — длина спрайта в пикселях,
 * angleDeg — поворот (0 — летит вправо, 90 — вниз, 180 — влево, -90 — вверх).
 */
fun DrawScope.drawFire(sheet: FireSheet, frame: Int, x: Float, y: Float, length: Float, angleDeg: Float, alpha: Float = 1f) {
    val f = ((frame % sheet.frames) + sheet.frames) % sheet.frames
    val h = length * sheet.fh / sheet.fw
    rotate(angleDeg, Offset(x, y)) {
        drawImage(
            sheet.img,
            srcOffset = IntOffset((f % sheet.cols) * sheet.fw, (f / sheet.cols) * sheet.fh),
            srcSize = IntSize(sheet.fw, sheet.fh),
            dstOffset = IntOffset((x - length / 2f).roundToInt(), (y - h / 2f).roundToInt()),
            dstSize = IntSize(length.roundToInt(), h.roundToInt()),
            alpha = alpha
        )
    }
}

@Composable
fun rememberWingsFx(): FireSheet {
    val img = ImageBitmap.imageResource(R.drawable.fx_wings)
    return remember(img) { FireSheet(img, 5, 5, 150, 60) }
}

@Composable
fun rememberWaterFx(): FireSheet {
    val img = ImageBitmap.imageResource(R.drawable.fx_water)
    return remember(img) { FireSheet(img, 5, 25, 120, 120) }
}
