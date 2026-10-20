package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.shared.*
import kotlinx.coroutines.launch

@Composable
fun FirstLaunchAgeDialog(
    onSelectAge: (String) -> Unit
) {
    val haptics = rememberHaptics()

    Dialog(onDismissRequest = { onSelectAge("3-5") }) {
        Surface(
            shape = ShapeCard,
            color = ColorBg,
            border = BorderStroke(4.dp, ColorInk),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "👶 Кто будет играть?",
                    style = PlayTypography.titleLarge,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = ColorInk,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Удерживайте карточку 2 секунды для выбора возраста",
                    style = PlayTypography.bodyLarge,
                    fontSize = 14.sp,
                    color = ColorInk.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Card 1: 👶 2 года
                    HoldAgeCard(
                        title = "2 года",
                        subtitle = "Крупные кнопки,\nбез отказа в еде,\nпростые игры",
                        icon = "👶",
                        bgColor = ColorYellow,
                        modifier = Modifier.weight(1f),
                        onTrigger = {
                            haptics.vibrate(40)
                            onSelectAge("2")
                        }
                    )

                    // Card 2: 🧸 3-5 лет
                    HoldAgeCard(
                        title = "3–5 лет",
                        subtitle = "Все игры,\nпапки, лук,\nпинбол и чай",
                        icon = "🧸",
                        bgColor = ColorPink,
                        modifier = Modifier.weight(1f),
                        onTrigger = {
                            haptics.vibrate(40)
                            onSelectAge("3-5")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HoldAgeCard(
    title: String,
    subtitle: String,
    icon: String,
    bgColor: Color,
    modifier: Modifier = Modifier,
    onTrigger: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            progress.animateTo(1f, animationSpec = tween(2000, easing = LinearEasing))
            if (isPressed && progress.value >= 0.99f) {
                onTrigger()
            }
        } else {
            progress.snapTo(0f)
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = bgColor,
        border = BorderStroke(3.dp, ColorInk),
        modifier = modifier
            .height(210.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = icon,
                    fontSize = 44.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Text(
                    text = title,
                    style = PlayTypography.titleLarge,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = ColorInk
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = ColorInk.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }

            // Circular hold progress indicator
            if (isPressed) {
                CircularProgressIndicator(
                    progress = { progress.value },
                    color = ColorInk,
                    strokeWidth = 4.dp,
                    modifier = Modifier
                        .size(72.dp)
                        .align(Alignment.Center)
                )
            }
        }
    }
}
