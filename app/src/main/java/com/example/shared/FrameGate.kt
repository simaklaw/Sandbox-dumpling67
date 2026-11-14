package com.example.shared

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay

/**
 * Экономия батареи: игровые циклы `withFrameNanos` гоняют кадры 60 раз в секунду, даже когда ребёнок давно ничего
 * не трогает (телефон лежит на столе с открытой игрой). [withFrameNanosEco] ведёт себя как обычный `withFrameNanos`,
 * пока идёт касание или с последнего касания прошло меньше [ECO_AFTER_MS]; потом кадры редеют (≈30, затем ≈15 в секунду),
 * а первое же касание мгновенно возвращает полную частоту. Сами игры не меняются: dt в них и так ограничен сверху.
 */
object IdleTracker {
    @Volatile var lastTouchMs: Long = SystemClock.uptimeMillis()
    @Volatile var touching: Boolean = false

    /** Вызывается из `Activity.dispatchTouchEvent` на каждое событие. */
    fun onTouch(ev: MotionEvent) {
        touching = ev.actionMasked != MotionEvent.ACTION_UP && ev.actionMasked != MotionEvent.ACTION_CANCEL
        lastTouchMs = SystemClock.uptimeMillis()
    }

    fun idleMs(): Long = if (touching) 0L else SystemClock.uptimeMillis() - lastTouchMs
}

const val ECO_AFTER_MS = 15_000L
const val ECO_DEEP_AFTER_MS = 60_000L

/** Дополнительная пауза перед кадром при простое: 0 мс (полная частота), 20 мс (≈30 к/с), 50 мс (≈15 к/с). */
fun ecoDelayMs(idleMs: Long): Long = when {
    idleMs < ECO_AFTER_MS -> 0L
    idleMs < ECO_DEEP_AFTER_MS -> 20L
    else -> 50L
}

suspend fun <R> withFrameNanosEco(onFrame: (Long) -> R): R {
    val d = ecoDelayMs(IdleTracker.idleMs())
    if (d > 0L) delay(d)
    return withFrameNanos(onFrame)
}
