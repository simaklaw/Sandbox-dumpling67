package com.example.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class RainbowColorItem(
    val id: String,
    val soundKey: String,
    val color: Color
)

val RAINBOW_COLOR_ITEMS_6 = listOf(
    RainbowColorItem("red", "rb_red", Color(0xFFFF2A55)),
    RainbowColorItem("orange", "rb_orange", Color(0xFFFF8800)),
    RainbowColorItem("yellow", "rb_yellow", Color(0xFFFFD200)),
    RainbowColorItem("green", "rb_green", Color(0xFF00E676)),
    RainbowColorItem("blue", "rb_blue", Color(0xFF2979FF)),
    RainbowColorItem("purple", "rb_purple", Color(0xFFD500F9))
)

val RAINBOW_COLOR_ITEMS_4 = listOf(
    RainbowColorItem("red", "rb_red", Color(0xFFFF2A55)),
    RainbowColorItem("yellow", "rb_yellow", Color(0xFFFFD200)),
    RainbowColorItem("green", "rb_green", Color(0xFF00E676)),
    RainbowColorItem("blue", "rb_blue", Color(0xFF2979FF))
)

/**
 * 🌈 RainbowButtons:
 * Row (or column) of glossy, satisfying color buttons for teaching colors
 * and unleashing delightful toy reactions without breaking gameplay.
 */
@Composable
fun RainbowButtons(
    onColor: (Color) -> Unit,
    modifier: Modifier = Modifier,
    isVertical: Boolean = false,
    soundManager: SoundManager? = null,
    haptics: Haptics = rememberHaptics()
) {
    val context = LocalContext.current
    val sound = soundManager ?: remember { SoundManager(context) }
    val ageProfile = LocalAgeProfile.current
    val items = if (ageProfile.isToddler2) RAINBOW_COLOR_ITEMS_4 else RAINBOW_COLOR_ITEMS_6

    val visualSize = 64.dp
    val touchSize = if (ageProfile.isToddler2) 88.dp else 72.dp
    val spacing = if (ageProfile.isToddler2) 8.dp else 6.dp

    if (isVertical) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items.forEach { item ->
                RainbowColorButton(
                    item = item,
                    visualSize = visualSize,
                    touchSize = touchSize,
                    onClick = {
                        sound.play(item.soundKey)
                        sound.play("pop")
                        haptics.vibrateShort()
                        onColor(item.color)
                    }
                )
            }
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                RainbowColorButton(
                    item = item,
                    visualSize = visualSize,
                    touchSize = touchSize,
                    onClick = {
                        sound.play(item.soundKey)
                        sound.play("pop")
                        haptics.vibrateShort()
                        onColor(item.color)
                    }
                )
            }
        }
    }
}

@Composable
private fun RainbowColorButton(
    item: RainbowColorItem,
    visualSize: Dp,
    touchSize: Dp,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1.0f) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(touchSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                scope.launch {
                    scaleAnim.animateTo(0.85f, spring(stiffness = Spring.StiffnessHigh))
                    scaleAnim.animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                }
                onClick()
            }
            .testTag("rainbow_btn_${item.id}")
    ) {
        // Glossy circular plasticine button
        Box(
            modifier = Modifier
                .size(visualSize)
                .scale(scaleAnim.value)
                .border(BorderStroke(3.5.dp, ColorInk), CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            item.color.copy(alpha = 0.95f),
                            item.color,
                            item.color.darker()
                        ),
                        center = Offset(24f, 24f),
                        radius = 80f
                    )
                )
        ) {
            // Specular highlight gloss
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                drawCircle(
                    color = Color.White.copy(alpha = 0.55f),
                    radius = radius * 0.32f,
                    center = Offset(radius * 0.65f, radius * 0.62f)
                )
            }
        }
    }
}

private fun Color.darker(): Color {
    return Color(
        red = (red * 0.78f).coerceIn(0f, 1f),
        green = (green * 0.78f).coerceIn(0f, 1f),
        blue = (blue * 0.78f).coerceIn(0f, 1f),
        alpha = alpha
    )
}
