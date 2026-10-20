package com.example.shared

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==========================================================
// 🎨 "SOFT PLASTICINE" COLORS
// ==========================================================
val ColorBg = Color(0xFFFFF6E5)
val ColorBgSoft = Color(0xFFFFEFD1)
val ColorInk = Color(0xFF5A3E36)
val ColorPink = Color(0xFFFF8FB1)
val ColorYellow = Color(0xFFFFD35C)
val ColorBlue = Color(0xFF7FC8FF)
val ColorGreen = Color(0xFF8EDC9D)
val ColorOrange = Color(0xFFFFB36B)
val ColorPurple = Color(0xFFB79CFF)
val ColorWhite = Color(0xFFFFFFFF)
val ColorSoftGrey = Color(0xFFC9B8A8)

// ==========================================================
// 📐 CORNER SHAPES
// ==========================================================
val ShapeCard = RoundedCornerShape(32.dp)
val ShapeButton = RoundedCornerShape(28.dp)
val ShapeSmall = RoundedCornerShape(20.dp)

// ==========================================================
// ✍️ TYPOGRAPHY
// ==========================================================
val PlayTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = ColorInk
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        color = ColorInk
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = ColorInk
    )
)

// ==========================================================
// ✨ PLASTICINE PRESS ANIMATION MODIFIER
// ==========================================================
fun Modifier.plasticineClickable(
    enabled: Boolean = true,
    haptics: Haptics = Haptics(),
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Haptics and sound play on press
    LaunchedEffect(isPressed) {
        if (isPressed) {
            haptics.vibrateShort()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    val translationY by animateFloatAsState(
        targetValue = if (isPressed) 6f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "translationY"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.translationY = translationY
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}
