package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

data class PinataCandy(
    val id: Long,
    val emoji: String,
    var x: Float, // 0.1 to 0.9
    var y: Float, // 0.1 to 0.95
    val speed: Float
)

@Composable
fun PinataScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var candyScore by remember { mutableStateOf(0) }
    var hitCount by remember { mutableStateOf(0) }
    var candies by remember { mutableStateOf(listOf<PinataCandy>()) }
    var mascotMessage by remember { mutableStateOf("🪅 Ударь палочкой по пиньяте!") }
    var isHitAnimating by remember { mutableStateOf(false) }

    // Piñata swing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pinata_swing")
    val swingAngle by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "swing"
    )

    // Hit reaction scale
    val scaleAnim = remember { Animatable(1f) }

    // Game loop for candies falling / resting on floor
    LaunchedEffect(Unit) {
        var nextId = 0L
        while (true) {
            delay(100)
            candies = candies.mapNotNull { candy ->
                if (candy.y < 0.85f) {
                    candy.copy(y = candy.y + candy.speed)
                } else {
                    candy
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2C1810)) // Warm wood tone
            .testTag("pinata_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF3E2723),
                    border = BorderStroke(width = 2.dp, color = Color(0xFFFFB74D)),
                    modifier = Modifier.padding(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🪅", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Пиньята с Конфетами",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF4E342E),
                    border = BorderStroke(width = 2.dp, color = Color(0xFFFFD54F))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🍬", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "$candyScore",
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mascot banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                color = Color(0xFF3E2723).copy(alpha = 0.9f),
                border = BorderStroke(width = 1.5.dp, color = Color(0xFFFFB74D).copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🧸", fontSize = 38.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = mascotMessage,
                            color = Color(0xFFFFCC80),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ударов палочкой: $hitCount",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Game Area Box (Piñata hanging and falling candies)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1B1310))
                    .border(width = 2.dp, color = Color(0xFF5D4037), shape = RoundedCornerShape(24.dp))
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val wPx = constraints.maxWidth
                    val hPx = constraints.maxHeight

                    // Hanging rope
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(60.dp)
                                .background(Color(0xFF8D6E63))
                        )
                    }

                    // Swinging Piñata (Star shape)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .rotate(swingAngle)
                                .scale(scaleAnim.value)
                                .size(130.dp)
                                .clip(CircleShape)
                                .background(
                                    if (hitCount % 2 == 0) Color(0xFFAB47BC) else Color(0xFFEC407A)
                                )
                                .border(width = 4.dp, color = Color(0xFFFFD54F), shape = CircleShape)
                                .clickable {
                                    hitCount++
                                    soundPlayer.playBubblePop()
                                    mascotMessage = listOf(
                                        "⭐ Бабах! Ещё удар!",
                                        "🍬 Конфетки сыпятся!",
                                        "🪅 Здорово колотишь!",
                                        "🎉 Ура! Сладости!"
                                    ).random()

                                    scope.launch {
                                        scaleAnim.snapTo(1.2f)
                                        scaleAnim.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                    }

                                    // Drop 3 to 5 candies
                                    val newCandies = (1..Random.nextInt(3, 6)).map {
                                        PinataCandy(
                                            id = System.nanoTime() + it,
                                            emoji = listOf("🍬", "🍭", "🍫", "🍪", "⭐", "🍡").random(),
                                            x = Random.nextFloat() * 0.7f + 0.15f,
                                            y = 0.25f,
                                            speed = Random.nextFloat() * 0.015f + 0.008f
                                        )
                                    }
                                    candies = candies + newCandies
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "⭐", fontSize = 64.sp)
                        }
                    }

                    // Render falling & resting candies
                    candies.forEach { candy ->
                        val cX = (candy.x * wPx).toInt()
                        val cY = (candy.y * hPx).toInt()

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(cX, cY) }
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4E342E).copy(alpha = 0.6f))
                                .border(width = 1.5.dp, color = Color(0xFFFFD54F), shape = CircleShape)
                                .clickable {
                                    candyScore++
                                    soundPlayer.playSparkleChime()
                                    mascotMessage = "😋 Вкусная конфета!"
                                    candies = candies.filter { it.id != candy.id }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = candy.emoji, fontSize = 24.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer instructions
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF3E2723)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("💡 Нажимай на пиньяту, чтобы разбить её и собрать конфеты!", color = Color(0xFFFFCC80), fontSize = 14.sp)
                }
            }
        }
    }
}
