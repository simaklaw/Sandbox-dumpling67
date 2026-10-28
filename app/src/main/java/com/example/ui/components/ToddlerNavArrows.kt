package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.example.shared.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import kotlin.math.roundToInt

/**
 * Big floating, pulsating candy arrow buttons on the left and right edges of the screen,
 * specifically designed for toddlers (2-3 y.o.) who find swipe gestures difficult.
 */
@Composable
fun BoxScope.ToddlerNavArrows(
    currentRoomIndex: Int,
    totalRooms: Int = 9,
    soundPlayer: SoundEffectPlayer,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arrow_bounce")

    // Pulsating bounce animation for left arrow
    val leftBounce by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "left_bounce"
    )

    // Pulsating bounce animation for right arrow
    val rightBounce by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "right_bounce"
    )

    // Left Arrow (Previous Room)
    AnimatedVisibility(
        visible = currentRoomIndex > 0,
        enter = fadeIn(tween(180)) + scaleIn(),
        exit = fadeOut(tween(150)) + scaleOut(),
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 8.dp, bottom = 8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(2.5.dp, Color(0xFFFF8F00)),
            shadowElevation = 4.dp,
            modifier = Modifier
                .offset { IntOffset(leftBounce.roundToInt(), 0) }
                .size(46.dp)
                .toddlerBounceClick(pressedScale = 0.85f) {
                    soundPlayer.triggerLightTapPulse()
                    soundPlayer.playBubblePop()
                    onPrevious()
                }
                .testTag("toddler_nav_prev_arrow")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFFFFFDE7),
                                Color(0xFFFFEE58),
                                Color(0xFFFFCA28)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "◀️",
                    fontSize = 20.sp
                )
            }
        }
    }

    // Right Arrow (Next Room)
    AnimatedVisibility(
        visible = currentRoomIndex < totalRooms - 1,
        enter = fadeIn(tween(180)) + scaleIn(),
        exit = fadeOut(tween(150)) + scaleOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 8.dp, bottom = 8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(2.5.dp, Color(0xFFFF8F00)),
            shadowElevation = 4.dp,
            modifier = Modifier
                .offset { IntOffset(rightBounce.roundToInt(), 0) }
                .size(46.dp)
                .toddlerBounceClick(pressedScale = 0.85f) {
                    soundPlayer.triggerLightTapPulse()
                    soundPlayer.playBubblePop()
                    onNext()
                }
                .testTag("toddler_nav_next_arrow")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFFFFFDE7),
                                Color(0xFFFFEE58),
                                Color(0xFFFFCA28)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▶️",
                    fontSize = 20.sp
                )
            }
        }
    }
}
