package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.window.Dialog
import com.example.shared.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch

/**
 * 🍬 Toddler Tactile Press Bounce Modifier.
 * Shrinks button to 0.88f on physical press and springs back on release,
 * triggering subtle HapticFeedbackConstants vibration on press and click.
 */
@Composable
fun Modifier.toddlerBounceClick(
    pressedScale: Float = 0.88f,
    onClick: () -> Unit
): Modifier {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            try {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } catch (_: Exception) {}
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "toddler_press_scale"
    )
    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                try {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                } catch (_: Exception) {}
                onClick()
            }
        )
}

/**
 * Toddler Exit Protection Dialog ("Детский щит от случайного выхода").
 * Prevents 2-3 year old toddlers from accidentally exiting the app when they swipe screen edges
 * or press the Android back navigation.
 */
@Composable
fun ToddlerExitProtectionDialog(
    onContinuePlaying: () -> Unit,
    onParentUnlock: () -> Unit
) {
    Dialog(onDismissRequest = onContinuePlaying) {
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)),
            border = BorderStroke(3.5.dp, Color(0xFFFFB300)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🎈", fontSize = 56.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ты в игре! Играем дальше?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF3E2723),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE8F5E9),
                    border = BorderStroke(1.5.dp, Color(0xFF81C784))
                ) {
                    Text(
                        text = "🔒 Детский щит: защита от случайного выхода активна",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Giant Chunky Continue Playing Button for the toddler with tactile bounce!
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF4CAF50),
                    border = BorderStroke(2.5.dp, Color(0xFF2E7D32)),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .toddlerBounceClick(pressedScale = 0.88f) {
                            onContinuePlaying()
                        }
                        .testTag("exit_shield_continue_button")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF66BB6A), Color(0xFF43A047), Color(0xFF2E7D32))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("🎮", fontSize = 26.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ИГРАТЬ ДАЛЬШЕ! 🌈",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Parental unlock with Hold-to-Exit (3 seconds hold) or math gate
                HoldToExitParentSection(
                    onParentUnlock = onParentUnlock
                )
            }
        }
    }
}

/**
 * Childproof Hold-to-Exit mechanism for parents.
 * Requires holding the button for 3 seconds continuously, which 2-3 y.o. children cannot perform.
 */
@Composable
fun HoldToExitParentSection(
    onParentUnlock: () -> Unit
) {
    var isHolding by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF5F5F5),
        border = BorderStroke(1.5.dp, if (isHolding) Color(0xFFFF9800) else Color(0xFFE0E0E0)),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        val job = coroutineScope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 2600, easing = LinearEasing)
                            )
                            // Held for required time!
                            onParentUnlock()
                        }
                        tryAwaitRelease()
                        isHolding = false
                        job.cancel()
                        progress.snapTo(0f)
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(if (isHolding) "⏳" else "🔒", fontSize = 15.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isHolding) "Держите ещё немного..." else "Родителям: Удерживайте 3 сек для выхода",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHolding) Color(0xFFE65100) else Color(0xFF616161)
                )
            }
            if (isHolding || progress.value > 0f) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFFFF9800),
                    trackColor = Color(0xFFE0E0E0)
                )
            }
        }
    }
}
