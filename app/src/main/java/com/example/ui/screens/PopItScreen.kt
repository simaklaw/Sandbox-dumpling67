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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.*

data class PopBubble(
    val id: Int,
    var isPopped: Boolean,
    val row: Int,
    val col: Int
)

@Composable
fun PopItScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current

    // Generate a circular-like Pop-it layout (rows with varying items: 3, 5, 7, 5, 3)
    val rowsConfig = listOf(3, 5, 7, 5, 3)
    var bubbles by remember {
        mutableStateOf(
            mutableListOf<PopBubble>().apply {
                var idCounter = 0
                rowsConfig.forEachIndexed { rIndex, count ->
                    for (c in 0 until count) {
                        add(PopBubble(id = idCounter++, isPopped = false, row = rIndex, col = c))
                    }
                }
            }
        )
    }

    var mascotMessage by remember { mutableStateOf("🟣 Нажимай на пузырики! Хрусь-хлоп!") }
    val poppedCount = bubbles.count { it.isPopped }
    val totalCount = bubbles.size

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1B4B))
            .testTag("pop_it_screen"),
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
                    color = Color(0xFF312E81),
                    border = BorderStroke(width = 2.dp, color = Color(0xFF818CF8)),
                    modifier = Modifier.padding(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🟣", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Поп-ит Антистресс",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }

                // Reset / Flip Button
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF4338CA),
                    border = BorderStroke(width = 2.dp, color = Color(0xFF818CF8)),
                    modifier = Modifier.clickable {
                        bubbles = bubbles.map { it.copy(isPopped = false) }.toMutableList()
                        soundPlayer.playPop(1.5f)
                        mascotMessage = "🔄 Перевернули! Снова лопаем!"
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔄", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Сброс", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

// Mascot banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                color = Color(0xFF312E81).copy(alpha = 0.85f),
                border = BorderStroke(width = 1.5.dp, color = Color(0xFF818CF8).copy(alpha = 0.5f))
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
                            color = Color(0xFFE2E8F0),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Лопай пузырики: $poppedCount / $totalCount",
                            color = Color(0xFFFFD54F),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Pop-it Silicone Board (Circular style)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF9333EA),
                                Color(0xFF7E22CE),
                                Color(0xFF581C87)
                            )
                        )
                    )
                    .border(width = 8.dp, color = Color(0xFFC084FC), shape = CircleShape)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    rowsConfig.forEachIndexed { rIndex, count ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val rowBubbles = bubbles.filter { it.row == rIndex }
                            rowBubbles.forEach { bubble ->
                                PopBubbleView(
                                    bubble = bubble,
                                    onClick = {
                                        val newState = !bubble.isPopped
                                        bubbles = bubbles.map {
                                            if (it.id == bubble.id) it.copy(isPopped = newState) else it
                                        }.toMutableList()

                                        if (newState) {
                                            soundPlayer.playBubblePop()
                                            val newPoppedCount = bubbles.count { it.isPopped }
                                            if (newPoppedCount == totalCount) {
                                                soundPlayer.playFanfare()
                                                mascotMessage = "🎉 Ура! Все пузырики лопнули! Ты супер!"
                                            } else {
                                                mascotMessage = listOf(
                                                    "✨ Хлоп! Отлично!",
                                                    "🎵 Здорово звучит!",
                                                    "⭐ Ещё один!",
                                                    "🎈 Весело!"
                                                ).random()
                                            }
                                        } else {
                                            soundPlayer.playPop(0.8f)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer instructions
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF312E81)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("💡 Нажимай на каждый пузырик, чтобы услышать антистресс-звук!", color = Color(0xFFCBD5E1), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun PopBubbleView(
    bubble: PopBubble,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (bubble.isPopped) 0.85f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "bubble_scale"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (bubble.isPopped) {
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF3B0764),
                            Color(0xFF2E1065)
                        )
                    )
                } else {
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE879F9),
                            Color(0xFFD946EF),
                            Color(0xFFC084FC)
                        )
                    )
                }
            )
            .border(
                width = if (bubble.isPopped) 2.dp else 3.dp,
                color = if (bubble.isPopped) Color(0xFF581C87) else Color(0xFFF0ABFC),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (!bubble.isPopped) {
            // Shiny highlight for 3D silicone dome effect
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .offset(x = (-6).dp, y = (-6).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.5f))
            )
        }
    }
}
