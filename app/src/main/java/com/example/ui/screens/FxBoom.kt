package com.example.ui.screens

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.R

/** Покадровый взрыв (7 кадров, 160×160) — общий эффект для раннеров. */
internal const val BOOM_TIME = 0.5f

internal fun loadBoomFrames(context: Context): List<ImageBitmap> =
    listOf(
        R.drawable.fx_boom_0, R.drawable.fx_boom_1, R.drawable.fx_boom_2, R.drawable.fx_boom_3,
        R.drawable.fx_boom_4, R.drawable.fx_boom_5, R.drawable.fx_boom_6
    ).mapNotNull { id ->
        try { android.graphics.BitmapFactory.decodeResource(context.resources, id)?.asImageBitmap() } catch (e: Throwable) { null }
    }

/** [age] — секунды с момента взрыва, [size] — диаметр в текущих единицах канваса. */
internal fun DrawScope.drawBoom(frames: List<ImageBitmap>, cx: Float, cy: Float, size: Float, age: Float) {
    if (frames.isEmpty() || age < 0f || age >= BOOM_TIME) return
    val i = (age / BOOM_TIME * frames.size).toInt().coerceIn(0, frames.size - 1)
    val k = size / 160f
    withTransform({ translate(cx - size / 2f, cy - size / 2f); scale(k, k, Offset.Zero) }) {
        drawImage(frames[i], Offset.Zero)
    }
}
