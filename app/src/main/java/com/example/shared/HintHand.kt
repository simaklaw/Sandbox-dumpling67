package com.example.shared

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import com.example.shared.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun HintHand(
    modifier: Modifier = Modifier,
    from: Offset,
    to: Offset = Offset.Zero,
    mode: String = "tap", // "tap" | "drag"
    loop: Int = 2,
    delayMs: Long = 2000
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delayMs)
        visible = true
    }

    if (!visible) return

    val infiniteTransition = rememberInfiniteTransition(label = "hintHand")

    // Gesture loop animations
    if (mode == "tap") {
        // Pulse scale & tap animation
        val scale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "tapScale"
        )

        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 0.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseAlpha"
        )

        val pulseRadius by infiniteTransition.animateFloat(
            initialValue = 10f,
            targetValue = 60f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = EaseOutQuad),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseRadius"
        )

        Box(
            modifier = modifier
                .offset(x = from.x.dp, y = from.y.dp)
                .size(80.dp)
        ) {
            // Pulse circle under finger
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color(0xFFFFB300).copy(alpha = pulseAlpha),
                    radius = pulseRadius,
                    center = Offset(size.width / 2, size.height / 2)
                )
            }

            // Finger pointer
            Text(
                text = "👇",
                fontSize = 42.sp,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
    } else {
        // Drag animation from point A to point B
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = EaseInOutQuad),
                repeatMode = RepeatMode.Restart
            ),
            label = "dragProgress"
        )

        val currentX = from.x + (to.x - from.x) * progress
        val currentY = from.y + (to.y - from.y) * progress

        // Ring pulse at drag start
        val startPulseAlpha = if (progress < 0.3f) (1f - (progress / 0.3f)) * 0.5f else 0f

        Box(
            modifier = modifier
                .offset(x = currentX.dp, y = currentY.dp)
                .size(80.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color(0xFFFFB300).copy(alpha = startPulseAlpha),
                    radius = 40f,
                    center = Offset(size.width / 2, size.height / 2)
                )
            }

            Text(
                text = "👉",
                fontSize = 42.sp,
                modifier = Modifier.graphicsLayer {
                    rotationZ = -30f
                }
            )
        }
    }
}
