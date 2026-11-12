package com.example.shared

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Единый универсальный профиль приложения «Детская площадка».
 * Возрастных режимов («2 года» / «3-5 лет») больше нет: все игры работают одинаково, с крупными
 * кнопками и мягкой «прощающей» зоной касания, подходящими малышам от 2 лет.
 */
data class AgeProfile(
    val id: String,
    val title: String,
    val minMainButtonDp: Dp,
    val minSecondaryButtonDp: Dp,
    val hitSlopMultiplier: Float,
    val tapDuplicateDrag: Boolean,
    val movingSpeedMultiplier: Float,
    val maxParticles: Int,
    val allowScreenShakeAndFlash: Boolean,
    val hintIdleDelaySeconds: Int,
    val hintRepeatCount: Int,
    val autoDemoIdleDelaySeconds: Int,
    val allowRefusal: Boolean,
    val hasFoldersOnHome: Boolean
) {
    /**
     * Устарело: режима «2 года» больше нет, всегда false.
     * Оставлено, чтобы ветки в играх не правились вслепую без компиляции; их можно вычищать по одной.
     */
    val isToddler2: Boolean get() = false
}

val UNIVERSAL_PROFILE = AgeProfile(
    id = "universal",
    title = "Для всех",
    minMainButtonDp = 112.dp,
    minSecondaryButtonDp = 80.dp,
    hitSlopMultiplier = 1.20f,
    tapDuplicateDrag = false,
    movingSpeedMultiplier = 0.85f,
    maxParticles = 20,
    allowScreenShakeAndFlash = false,
    hintIdleDelaySeconds = 5,
    hintRepeatCount = 2,
    autoDemoIdleDelaySeconds = 0,
    allowRefusal = true,
    hasFoldersOnHome = true
)

val LocalAgeProfile = staticCompositionLocalOf { UNIVERSAL_PROFILE }
