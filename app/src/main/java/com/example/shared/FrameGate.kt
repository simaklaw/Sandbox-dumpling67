package com.example.shared

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Экономия батареи без изменения скорости игры.
 *
 * Игровые циклы `withFrameNanos` гоняют 60 кадров в секунду, даже когда ребёнок давно ничего не трогает.
 * [withFrameNanosEco] при простое (нет касания дольше [ECO_AFTER_MS]) просыпается реже: ≈30, потом ≈20 раз в секунду.
 * Чтобы скорость и физика не менялись, лямбда кадра вызывается столько раз, сколько кадров «пропущено»
 * (с метками времени по 16,7 мс), поэтому и dt-интеграторы, и счётчики «на кадр» идут в прежнем темпе;
 * реже происходит только перерисовка (рекомпозиция и Canvas), а она и дороже всего. Первое касание
 * (или зажатый палец) мгновенно возвращает полную частоту.
 *
 * ПРАВИЛО: использовать только там, где ВЕСЬ шаг симуляции находится внутри лямбды, а код после неё — лишь счётчик
 * перерисовки (`frame++`). Если физика считается снаружи (`withFrameNanos { }` и дальше тело цикла, как в
 * BubbleWrap и PopIt), применять нельзя: оставляем обычный `withFrameNanos`.
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
const val FRAME_NS = 16_666_667L

/** Дополнительная пауза перед кадром при простое: 0 мс (полная частота), 20 мс (≈30 к/с), 40 мс (≈20 к/с). */
fun ecoDelayMs(idleMs: Long): Long = when {
    idleMs < ECO_AFTER_MS -> 0L
    idleMs < ECO_DEEP_AFTER_MS -> 20L
    else -> 40L
}

/** Сколько «обычных» кадров прошло за [waitedNs] с момента входа в ожидание (от 1 до 4). */
fun ecoSteps(waitedNs: Long): Int = (waitedNs.toFloat() / FRAME_NS).roundToInt().coerceIn(1, 4)

suspend fun <R> withFrameNanosEco(onFrame: (Long) -> R): R {
    val d = ecoDelayMs(IdleTracker.idleMs())
    if (d <= 0L) return withFrameNanos(onFrame)
    val start = System.nanoTime()
    delay(d)
    return withFrameNanos { frameNs ->
        val steps = ecoSteps(System.nanoTime() - start)
        for (k in steps - 1 downTo 1) onFrame(frameNs - k * FRAME_NS)
        onFrame(frameNs)
    }
}
