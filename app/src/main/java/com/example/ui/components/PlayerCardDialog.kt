package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.audio.SoundEffectPlayer
import com.example.model.GameState
import com.example.model.PelmenAccessoryCatalog
import kotlinx.coroutines.launch

/**
 * Formats total play time in seconds into a human-readable string.
 */
fun formatPlayTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> "${hours} ч ${minutes} мин"
        minutes > 0 -> "${minutes} мин ${seconds} сек"
        else -> "${seconds} сек"
    }
}

/**
 * Player Profile Card Modal Dialog showing:
 * 1. Live preview of Pelmen-Six with currently equipped accessories
 * 2. Current equipped accessories names & emojis
 * 3. Total play time in game
 * 4. Total opened blind boxes count
 * 5. Player coins & squish stats
 */
@Composable
fun PlayerCardDialog(
    gameState: GameState,
    soundPlayer: SoundEffectPlayer,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var expressionIndex by remember { mutableIntStateOf(0) }

    val equippedHat = PelmenAccessoryCatalog.getById(gameState.equippedHatId)
    val equippedGlasses = PelmenAccessoryCatalog.getById(gameState.equippedGlassesId)
    val equippedBody = PelmenAccessoryCatalog.getById(gameState.equippedBodyId)

    // Interactive squish physics for avatar preview
    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .testTag("player_card_dialog"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)),
            border = BorderStroke(3.dp, Color(0xFFFFB300)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // HEADER WITH CLOSE BUTTON
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFFD54F),
                            border = BorderStroke(1.5.dp, Color(0xFFFF8F00))
                        ) {
                            Text("🎴", fontSize = 22.sp, modifier = Modifier.padding(6.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Карточка Игрока",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF3E2723)
                            )
                            Text(
                                text = "Вселенная Пельмень-Сикса 67",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            soundPlayer.playSqueak()
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("close_player_card")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = Color(0xFF5D4037)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // TOP HERO DISPLAY: LIVE PREVIEW OF PELMEN-SIX
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    border = BorderStroke(2.dp, Color(0xFFFFE082)),
                    shadowElevation = 3.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Pelmen-Six Body Canvas + Equipped Items
                        Box(
                            modifier = Modifier
                                .scale(scaleX.value, scaleY.value)
                                .size(140.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            soundPlayer.playSqueak()
                                            soundPlayer.triggerLightTapPulse()
                                            expressionIndex = (expressionIndex + 1) % 4
                                            coroutineScope.launch {
                                                scaleX.animateTo(1.25f, spring(Spring.DampingRatioMediumBouncy))
                                                scaleY.animateTo(0.75f, spring(Spring.DampingRatioMediumBouncy))
                                                scaleX.animateTo(1f, spring(Spring.DampingRatioHighBouncy))
                                                scaleY.animateTo(1f, spring(Spring.DampingRatioHighBouncy))
                                            }
                                        }
                                    )
                                }
                                .testTag("player_card_pelmen_avatar"),
                            contentAlignment = Alignment.Center
                        ) {
                            val textMeasurer = rememberTextMeasurer()

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawWardrobePelmenBody(
                                    expressionIndex = expressionIndex,
                                    textMeasurer = textMeasurer
                                )
                            }

                            // Equipped items overlay
                            if (equippedBody != null && equippedBody.id != "body_none") {
                                Text(
                                    text = equippedBody.emoji,
                                    fontSize = 32.sp,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .offset(y = (-16).dp)
                                )
                            }

                            if (equippedGlasses != null && equippedGlasses.id != "glasses_none") {
                                Text(
                                    text = equippedGlasses.emoji,
                                    fontSize = 40.sp,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .offset(y = (-6).dp)
                                )
                            }

                            if (equippedHat != null && equippedHat.id != "hat_none") {
                                Text(
                                    text = equippedHat.emoji,
                                    fontSize = 44.sp,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .offset(y = (-18).dp)
                                )
                            }
                        }

                        // Badge on avatar bottom right
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFD54F),
                            border = BorderStroke(1.dp, Color(0xFFFF8F00))
                        ) {
                            Text(
                                text = "✨ Нажми на героя!",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF3E2723),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SECTION 1: EQUIPPED ACCESSORIES ("Текущие аксессуары")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFFFF8E1),
                    border = BorderStroke(1.5.dp, Color(0xFFFFD54F))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "👑 ТЕКУЩИЙ НАРАД ПЕЛЬМЕНЯ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFE65100),
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Hat
                            AccessoryPillItem(
                                categoryName = "Шапка",
                                emoji = equippedHat?.emoji ?: "👒",
                                itemName = equippedHat?.name ?: "Без шапки"
                            )
                            // Glasses
                            AccessoryPillItem(
                                categoryName = "Очки",
                                emoji = equippedGlasses?.emoji ?: "👓",
                                itemName = equippedGlasses?.name ?: "Без очков"
                            )
                            // Body
                            AccessoryPillItem(
                                categoryName = "Наряд",
                                emoji = equippedBody?.emoji ?: "👔",
                                itemName = equippedBody?.name ?: "Без наряда"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SECTION 2: GAME STATS GRID ("Общее время & Блайнд-боксы")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Stat Card 1: Opened Blind Boxes
                    CardStatItem(
                        modifier = Modifier.weight(1f),
                        emoji = "🎁",
                        title = "Открыто боксов",
                        value = "${gameState.missionOpenBoxesCount} шт.",
                        accentColor = Color(0xFF8E24AA),
                        bgColor = Color(0xFFF3E5F5)
                    )

                    // Stat Card 2: Total Play Time
                    CardStatItem(
                        modifier = Modifier.weight(1f),
                        emoji = "⏱️",
                        title = "Время в игре",
                        value = formatPlayTime(gameState.totalPlayTimeSeconds),
                        accentColor = Color(0xFF1E88E5),
                        bgColor = Color(0xFFE3F2FD)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Stat Card 3: Coins
                    CardStatItem(
                        modifier = Modifier.weight(1f),
                        emoji = "🪙",
                        title = "Монеты",
                        value = "${gameState.coins}",
                        accentColor = Color(0xFFE65100),
                        bgColor = Color(0xFFFFF3E0)
                    )

                    // Stat Card 4: Total Squishes
                    CardStatItem(
                        modifier = Modifier.weight(1f),
                        emoji = "🖐️",
                        title = "Всего сквишей",
                        value = "${gameState.totalSquishCount} раз",
                        accentColor = Color(0xFF2E7D32),
                        bgColor = Color(0xFFE8F5E9)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // OK BUTTON
                Button(
                    onClick = {
                        soundPlayer.playSqueak()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("player_card_ok_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFB300),
                        contentColor = Color(0xFF3E2723)
                    ),
                    border = BorderStroke(2.dp, Color(0xFFFF8F00))
                ) {
                    Text(
                        text = "Отлично! 🎉",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessoryPillItem(
    categoryName: String,
    emoji: String,
    itemName: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFFFE082))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(categoryName, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8D6E63))
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = itemName,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF3E2723)
                )
            }
        }
    }
}

@Composable
private fun CardStatItem(
    modifier: Modifier = Modifier,
    emoji: String,
    title: String,
    value: String,
    accentColor: Color,
    bgColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 22.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5D4037)
                )
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = accentColor
                )
            }
        }
    }
}
