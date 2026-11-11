package com.example.ui.screens

import kotlin.math.abs

/**
 * Правила управления «Зеликом» без Android-зависимостей (проверяются в ZelikControlsTest).
 *
 * Раньше присед включался при stickY > 0.55: палец, лежащий на нижней половине стика, сразу
 * приседал, а в приседе нельзя было прыгать и скорость падала вдвое — Зелик «не мог запрыгнуть
 * наверх» и брать высокие кристаллы. Теперь присед — только осознанное движение вниз,
 * стик вверх прыгает, а кнопка прыжка «запоминается» на несколько кадров до приземления.
 */
internal object ZelikControls {
    /** Насколько сильно и насколько «строго вниз» нужно потянуть стик, чтобы присесть. */
    private const val CROUCH_Y = 0.75f
    private const val UP_Y = 0.7f
    private const val AXIS_DOMINANCE = 1.4f

    /** Кадров (из 60 в секунду), в течение которых нажатие прыжка ждёт приземления. */
    const val JUMP_BUFFER = 7

    fun crouch(stickX: Float, stickY: Float, onGround: Boolean): Boolean =
        onGround && stickY > CROUCH_Y && stickY > abs(stickX) * AXIS_DOMINANCE

    /** Стик уверенно вверх (не по диагонали при беге) — это прыжок. */
    fun stickJump(stickX: Float, stickY: Float): Boolean =
        stickY < -UP_Y && -stickY > abs(stickX) * AXIS_DOMINANCE
}
