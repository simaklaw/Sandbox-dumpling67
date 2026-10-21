package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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

data class AnimalSoundCardItem(
    val soundKey: String,
    val name: String,
    val voiceNameKey: String,
    val emoji: String,
    val bgColor: Color,
    val badgeColor: Color
)

val FULL_ANIMAL_SOUND_LIST = listOf(
    AnimalSoundCardItem("animal_cow", "Коровка", "voice_name_cow", "🐮", Color(0xFFE8F5E9), Color(0xFF81C784)),
    AnimalSoundCardItem("animal_horse", "Лошадка", "voice_name_horse", "🐴", Color(0xFFEFEBE9), Color(0xFFA1887F)),
    AnimalSoundCardItem("animal_dog", "Собачка", "voice_name_dog", "🐶", Color(0xFFFFF8E1), Color(0xFFFFB300)),
    AnimalSoundCardItem("animal_cat", "Кошечка", "voice_name_cat", "🐱", Color(0xFFFBE9E7), Color(0xFFFF8A65)),
    AnimalSoundCardItem("animal_pig", "Свинка", "voice_name_pig", "🐷", Color(0xFFFCE4EC), Color(0xFFF48FB1)),
    AnimalSoundCardItem("animal_sheep", "Барашек", "voice_name_sheep", "🐑", Color(0xFFE0F2F1), Color(0xFF80CBC4)),
    AnimalSoundCardItem("animal_goose", "Гусь", "voice_name_goose", "🪿", Color(0xFFE1F5FE), Color(0xFF4FC3F7)),
    AnimalSoundCardItem("animal_rooster", "Петушок", "voice_name_rooster", "🐓", Color(0xFFFFF3E0), Color(0xFFFF7043)),
    AnimalSoundCardItem("animal_frog", "Лягушонок", "voice_name_frog", "🐸", Color(0xFFE8F8F5), Color(0xFF66BB6A)),
    AnimalSoundCardItem("animal_crow", "Ворона", "voice_name_crow", "🐦‍⬛", Color(0xFFECEFF1), Color(0xFF90A4AE)),
    AnimalSoundCardItem("animal_duck", "Уточка", "voice_name_duck", "🦆", Color(0xFFFFFDE7), Color(0xFFFFD54F)),
    AnimalSoundCardItem("animal_bunny", "Зайка", "voice_name_bunny", "🐰", Color(0xFFF3E5F5), Color(0xFFCE93D8))
)

val TODDLER_ANIMAL_SOUND_LIST = listOf(
    AnimalSoundCardItem("animal_cow", "Коровка", "voice_name_cow", "🐮", Color(0xFFE8F5E9), Color(0xFF81C784)),
    AnimalSoundCardItem("animal_dog", "Собачка", "voice_name_dog", "🐶", Color(0xFFFFF8E1), Color(0xFFFFB300)),
    AnimalSoundCardItem("animal_cat", "Кошечка", "voice_name_cat", "🐱", Color(0xFFFBE9E7), Color(0xFFFF8A65)),
    AnimalSoundCardItem("animal_duck", "Уточка", "voice_name_duck", "🦆", Color(0xFFFFFDE7), Color(0xFFFFD54F)),
    AnimalSoundCardItem("animal_pig", "Свинка", "voice_name_pig", "🐷", Color(0xFFFCE4EC), Color(0xFFF48FB1)),
    AnimalSoundCardItem("animal_frog", "Лягушонок", "voice_name_frog", "🐸", Color(0xFFE8F8F5), Color(0xFF66BB6A))
)

@Composable
fun AnimalRoomScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val ageProfile = LocalAgeProfile.current

    val animalList = if (ageProfile.isToddler2) TODDLER_ANIMAL_SOUND_LIST else FULL_ANIMAL_SOUND_LIST

    // Preload animal sounds group
    DisposableEffect(Unit) {
        soundManager.preload("animals")
        onDispose {
            soundManager.release("animals")
        }
    }

    // Active animation states for each card
    val jumpAnimatables = remember(animalList.size) {
        animalList.map { Animatable(1.0f) }
    }
    val pulseAnimatables = remember(animalList.size) {
        animalList.map { Animatable(1.0f) }
    }

    // Choir state
    var isChoirPlaying by remember { mutableStateOf(false) }
    var highlightedIndex by remember { mutableStateOf<Int?>(null) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun triggerAnimal(index: Int, isAutoDemo: Boolean = false) {
        if (index !in animalList.indices) return
        val item = animalList[index]
        if (!isAutoDemo) {
            lastInteractionTime = System.currentTimeMillis()
            haptics.vibrate(20)
        }
        soundManager.play(item.soundKey)

        scope.launch {
            // Jump scale & bounce
            launch {
                jumpAnimatables[index].animateTo(
                    targetValue = 1.25f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                )
                jumpAnimatables[index].animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioHighBouncy))
            }
            // Card pulse
            launch {
                pulseAnimatables[index].animateTo(1.06f, tween(100))
                pulseAnimatables[index].animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }

            // In 2yo mode: after 0.8s, voice speaks animal name (voice_name_<kind>)
            if (ageProfile.isToddler2) {
                delay(800L)
                soundManager.play(item.voiceNameKey)
            }
        }
    }

    // Auto-demonstration for 2yo mode after 10s idle
    LaunchedEffect(lastInteractionTime, ageProfile.isToddler2) {
        if (ageProfile.isToddler2 && ageProfile.autoDemoIdleDelaySeconds > 0) {
            delay(ageProfile.autoDemoIdleDelaySeconds * 1000L)
            val randomIdx = Random.nextInt(animalList.size)
            triggerAnimal(randomIdx, isAutoDemo = true)
        }
    }

    fun startChoir() {
        if (isChoirPlaying) return
        isChoirPlaying = true
        haptics.vibrate(40)

        scope.launch {
            val indices = animalList.indices.shuffled().take(4)
            for (idx in indices) {
                highlightedIndex = idx
                triggerAnimal(idx)
                delay(600L)
            }
            highlightedIndex = null
            isChoirPlaying = false
        }
    }

    var currentPage by remember { mutableIntStateOf(0) }
    val pageSize = 6
    val totalPages = (animalList.size + pageSize - 1) / pageSize
    val pageItems = animalList.drop(currentPage * pageSize).take(pageSize)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectTapGestures {
                    lastInteractionTime = System.currentTimeMillis()
                }
            }
            .pointerInput(totalPages) {
                var dragSum = 0f
                detectDragGestures(
                    onDragStart = { dragSum = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragSum += dragAmount.x
                    },
                    onDragEnd = {
                        if (dragSum < -80f && currentPage < totalPages - 1) {
                            currentPage++
                            soundManager.play("transition")
                            haptics.vibrateShort()
                        } else if (dragSum > 80f && currentPage > 0) {
                            currentPage--
                            soundManager.play("transition")
                            haptics.vibrateShort()
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Spacer for HomeButton (80 dp) + 12 dp gap + 🐮 Icon (No text title)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(80.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "🐮",
                    fontSize = 38.sp
                )
            }

            // 2 x 3 Grid of 6 Cards for Current Page (No vertical scrolling)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                for (row in 0 until 3) {
                    val idx0 = row * 2
                    val idx1 = row * 2 + 1
                    if (idx0 < pageItems.size) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val globalIdx0 = currentPage * pageSize + idx0
                            val item0 = pageItems[idx0]
                            val isHigh0 = highlightedIndex == globalIdx0
                            val scale0 = pulseAnimatables[globalIdx0].value * (if (isHigh0) 1.08f else 1.0f)
                            val jump0 = jumpAnimatables[globalIdx0].value

                            Surface(
                                shape = ShapeCard,
                                color = item0.bgColor,
                                border = BorderStroke(
                                    width = if (isHigh0) 5.dp else 3.5.dp,
                                    color = if (isHigh0) ColorPurple else ColorInk
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .scale(scale0)
                                    .shadow(if (isHigh0) 12.dp else 4.dp, ShapeCard)
                                    .plasticineClickable(haptics = haptics) {
                                        triggerAnimal(globalIdx0)
                                    }
                                    .testTag("animal_card_${item0.soundKey}")
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(90.dp)
                                            .clip(CircleShape)
                                            .background(item0.badgeColor.copy(alpha = 0.25f))
                                    )
                                    Text(
                                        text = item0.emoji,
                                        fontSize = 64.sp,
                                        modifier = Modifier.scale(jump0)
                                    )
                                }
                            }

                            if (idx1 < pageItems.size) {
                                val globalIdx1 = currentPage * pageSize + idx1
                                val item1 = pageItems[idx1]
                                val isHigh1 = highlightedIndex == globalIdx1
                                val scale1 = pulseAnimatables[globalIdx1].value * (if (isHigh1) 1.08f else 1.0f)
                                val jump1 = jumpAnimatables[globalIdx1].value

                                Surface(
                                    shape = ShapeCard,
                                    color = item1.bgColor,
                                    border = BorderStroke(
                                        width = if (isHigh1) 5.dp else 3.5.dp,
                                        color = if (isHigh1) ColorPurple else ColorInk
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .scale(scale1)
                                        .shadow(if (isHigh1) 12.dp else 4.dp, ShapeCard)
                                        .plasticineClickable(haptics = haptics) {
                                            triggerAnimal(globalIdx1)
                                        }
                                        .testTag("animal_card_${item1.soundKey}")
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(90.dp)
                                                .clip(CircleShape)
                                                .background(item1.badgeColor.copy(alpha = 0.25f))
                                        )
                                        Text(
                                            text = item1.emoji,
                                            fontSize = 64.sp,
                                            modifier = Modifier.scale(jump1)
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp)) // Space for bottom choir button
        }

        // Left Navigation Arrow (88 dp)
        if (currentPage > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .size(88.dp)
                    .border(3.5.dp, ColorInk, CircleShape)
                    .background(ColorYellow, CircleShape)
                    .clip(CircleShape)
                    .plasticineClickable(haptics = haptics) {
                        currentPage--
                        soundManager.play("transition")
                    }
            ) {
                Text("◀️", fontSize = 42.sp)
            }
        }

        // Right Navigation Arrow (88 dp)
        if (currentPage < totalPages - 1) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .size(88.dp)
                    .border(3.5.dp, ColorInk, CircleShape)
                    .background(ColorYellow, CircleShape)
                    .clip(CircleShape)
                    .plasticineClickable(haptics = haptics) {
                        currentPage++
                        soundManager.play("transition")
                    }
            ) {
                Text("▶️", fontSize = 42.sp)
            }
        }

        // 🎵 88dp Choir Button ("Хор") - Fixed in Bottom Center
        if (!ageProfile.isToddler2) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .size(88.dp)
                    .border(4.dp, ColorInk, CircleShape)
                    .background(ColorPurple, CircleShape)
                    .clip(CircleShape)
                    .plasticineClickable(haptics = haptics) {
                        startChoir()
                    }
                    .testTag("choir_button")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("🎵", fontSize = 28.sp)
                    Text(
                        text = "Хор",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }
        }
    }
}
