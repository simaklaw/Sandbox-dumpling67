package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import com.example.shared.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shared.*
import kotlinx.coroutines.delay

@Composable
fun RestTimerOverlay(
    visible: Boolean,
    soundManager: SoundManager,
    onParentUnlock: () -> Unit
) {
    val haptics = rememberHaptics()
    val progress = remember { Animatable(0f) }
    var isSunPressed by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            soundManager.play("lullaby_short", volume = 0.5f)
        }
    }

    LaunchedEffect(isSunPressed) {
        if (isSunPressed) {
            progress.animateTo(1f, animationSpec = tween(3000, easing = LinearEasing))
            if (isSunPressed && progress.value >= 0.99f) {
                haptics.vibrate(50)
                soundManager.play("chime")
                onParentUnlock()
            }
        } else {
            progress.snapTo(0f)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(1500)),
        exit = fadeOut(animationSpec = tween(600))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1C2E).copy(alpha = 0.96f))
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                // Moon and Stars
                Text(
                    text = "🌙 ✨",
                    fontSize = 48.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Sleeping Hero
                Text(
                    text = "🥟💤",
                    fontSize = 80.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "Время отдохнуть!",
                    style = PlayTypography.titleLarge,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Глазки отдыхают.\nУдерживайте ☀️ 3 секунды, чтобы продолжить.",
                    style = PlayTypography.bodyLarge,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(36.dp))

                // ☀️ 3-second hold Sun button for parent
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(ColorYellow, CircleShape)
                        .border(3.dp, ColorWhite, CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isSunPressed = true
                                    tryAwaitRelease()
                                    isSunPressed = false
                                }
                            )
                        }
                        .testTag("rest_timer_unlock_sun")
                ) {
                    Text(
                        text = "☀️",
                        fontSize = 42.sp
                    )

                    if (isSunPressed) {
                        CircularProgressIndicator(
                            progress = { progress.value },
                            color = ColorInk,
                            strokeWidth = 5.dp,
                            modifier = Modifier.size(84.dp)
                        )
                    }
                }
            }
        }
    }
}
