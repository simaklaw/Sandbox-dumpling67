package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

data class RoutineItem(
    val id: Int,
    val title: String,
    val emoji: String,
    val description: String,
    val color: Color
)

data class RoutinePhase(
    val name: String,
    val timeLabel: String,
    val phaseEmoji: String,
    val items: List<RoutineItem>,
    val headerColor: Color
)

@Composable
fun ToddlerRoutineScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    val phases = listOf(
        RoutinePhase(
            name = "Утро",
            timeLabel = "Утро • Просыпаемся",
            phaseEmoji = "🌅",
            headerColor = Color(0xFFFFE082),
            items = listOf(
                RoutineItem(1, "Просыпаемся", "☀️", "Доброе утро, солнышко!", Color(0xFFFFD54F)),
                RoutineItem(2, "Зарядка", "🤸", "Потянемся высоко-высоко!", Color(0xFF81D4FA)),
                RoutineItem(3, "Чистим зубки", "🪥", "Щеткой весело блестим!", Color(0xFFA5D6A7)),
                RoutineItem(4, "Вкусный завтрак", "🥣", "Каша и любимый чай!", Color(0xFFFFCC80))
            )
        ),
        RoutinePhase(
            name = "День",
            timeLabel = "День • Игры и Прогулка",
            phaseEmoji = "☀️",
            headerColor = Color(0xFF81D4FA),
            items = listOf(
                RoutineItem(5, "Собираем игрушки", "🧸", "Каждая игрушка на своем месте", Color(0xFFCE93D8)),
                RoutineItem(6, "Прогулка", "🌳", "Гуляем в парке с друзьями", Color(0xFFA5D6A7)),
                RoutineItem(7, "Горка и качели", "🛝", "Катаемся и смеемся", Color(0xFFFFAB91)),
                RoutineItem(8, "Полезный обед", "🍲", "Супчик для силы и роста", Color(0xFFFFE082))
            )
        ),
        RoutinePhase(
            name = "Вечер",
            timeLabel = "Вечер • Подготовка ко сну",
            phaseEmoji = "🌙",
            headerColor = Color(0xFFCE93D8),
            items = listOf(
                RoutineItem(9, "Теплая ванна", "🛁", "Пенные пузыри и уточки", Color(0xFF90CAF9)),
                RoutineItem(10, "Пижамка", "👕", "Мягкая и уютная одежда", Color(0xFFF48FB1)),
                RoutineItem(11, "Сказка на ночь", "📖", "Волшебная история перед сном", Color(0xFFFFCC80)),
                RoutineItem(12, "Сладких снов", "😴", "Закрываем глазки, бай-бай", Color(0xFFB39DDB))
            )
        )
    )

    var selectedPhaseIndex by remember { mutableIntStateOf(0) }
    val currentPhase = phases[selectedPhaseIndex]

    // Completed items map
    val completedItems = remember { mutableStateMapOf<Int, Boolean>() }

    // Celebration state
    var showCelebration by remember { mutableStateOf(false) }

    // Bounce animation for active items
    val infiniteTransition = rememberInfiniteTransition(label = "routine_anim")
    val bounceAnim by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header: Title & Phase Selector Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🍼 День Малыша",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorInk
                )

                // Progress Indicator
                val totalItems = currentPhase.items.size
                val completedCount = currentPhase.items.count { completedItems[it.id] == true }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = currentPhase.headerColor.copy(alpha = 0.4f),
                    border = BorderStroke(2.dp, currentPhase.headerColor)
                ) {
                    Text(
                        text = "$completedCount / $totalItems ${currentPhase.phaseEmoji}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        color = ColorInk
                    )
                }
            }

            // Phase selector tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                phases.forEachIndexed { index, phase ->
                    val isSelected = selectedPhaseIndex == index
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clickable {
                                soundManager.play("tap")
                                haptics.vibrateShort()
                                selectedPhaseIndex = index
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) phase.headerColor else Color.White.copy(alpha = 0.8f),
                        border = BorderStroke(3.dp, if (isSelected) Color.White else phase.headerColor)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = phase.phaseEmoji, fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = phase.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorInk
                            )
                        }
                    }
                }
            }

            // Main routine items list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(currentPhase.items, key = { it.id }) { item ->
                    val isDone = completedItems[item.id] == true

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .scale(if (!isDone) bounceAnim else 1f)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable {
                                soundManager.play("success")
                                haptics.vibrateShort()
                                completedItems[item.id] = true

                                // Check if all items in current phase are done
                                if (currentPhase.items.all { completedItems[it.id] == true }) {
                                    showCelebration = true
                                    soundManager.play("success")
                                }
                            },
                        shape = RoundedCornerShape(24.dp),
                        color = if (isDone) item.color.copy(alpha = 0.4f) else Color.White,
                        border = BorderStroke(4.dp, if (isDone) item.color else item.color.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = item.color.copy(alpha = 0.3f),
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = item.emoji, fontSize = 34.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.width(18.dp))

                                Column {
                                    Text(
                                        text = item.title,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorInk
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.description,
                                        fontSize = 15.sp,
                                        color = ColorInk.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            // Status check or tap cue
                            Surface(
                                shape = CircleShape,
                                color = if (isDone) Color(0xFF4CAF50) else item.color.copy(alpha = 0.2f),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (isDone) "✓" else "👉",
                                        fontSize = 22.sp,
                                        color = if (isDone) Color.White else ColorInk
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Celebration Dialog when phase is completed
        if (showCelebration) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showCelebration = false },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .wrapContentHeight(),
                    border = BorderStroke(4.dp, ColorPink)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "🎉 Ура! Молодчина! 🎉", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ColorInk)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(text = "🐾 Зверюшки танцуют и радуются!", fontSize = 18.sp, color = ColorInk.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(text = "🐱", fontSize = 42.sp)
                            Text(text = "🐰", fontSize = 42.sp)
                            Text(text = "🐻", fontSize = 42.sp)
                            Text(text = "🥟", fontSize = 42.sp)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = ColorPink,
                            modifier = Modifier
                                .height(56.dp)
                                .fillMaxWidth()
                                .clickable {
                                    soundManager.play("tap")
                                    showCelebration = false
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = "Продолжить играться! ✨", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
