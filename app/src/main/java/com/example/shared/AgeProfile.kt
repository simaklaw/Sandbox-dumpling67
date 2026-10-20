package com.example.shared

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Age Profile settings for "Детская площадка" (Step 10).
 * Dynamically customizes interaction tolerances, speeds, simplifications and UI layouts.
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
    val isToddler2: Boolean get() = id == "2"
}

val TODDLER_2 = AgeProfile(
    id = "2",
    title = "👶 2 года",
    minMainButtonDp = 120.dp,
    minSecondaryButtonDp = 88.dp,
    hitSlopMultiplier = 1.30f,
    tapDuplicateDrag = true,
    movingSpeedMultiplier = 0.6f,
    maxParticles = 10,
    allowScreenShakeAndFlash = false,
    hintIdleDelaySeconds = 4,
    hintRepeatCount = 3,
    autoDemoIdleDelaySeconds = 10,
    allowRefusal = false,
    hasFoldersOnHome = false
)

val KIDS_3_5 = AgeProfile(
    id = "3-5",
    title = "🧸 3-5 лет",
    minMainButtonDp = 96.dp,
    minSecondaryButtonDp = 72.dp,
    hitSlopMultiplier = 1.10f,
    tapDuplicateDrag = false,
    movingSpeedMultiplier = 1.0f,
    maxParticles = 25,
    allowScreenShakeAndFlash = true,
    hintIdleDelaySeconds = 6,
    hintRepeatCount = 2,
    autoDemoIdleDelaySeconds = 0, // Disabled for 3-5 years
    allowRefusal = true,
    hasFoldersOnHome = true
)

val LocalAgeProfile = staticCompositionLocalOf { KIDS_3_5 }

fun getAgeProfileById(id: String?): AgeProfile {
    return if (id == "2") TODDLER_2 else KIDS_3_5
}
