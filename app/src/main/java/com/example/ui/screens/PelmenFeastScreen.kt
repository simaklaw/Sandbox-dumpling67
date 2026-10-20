package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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

data class FeastFoodItem(
    val id: Int,
    val emoji: String,
    val name: String,
    val color: Color
)

val FEAST_FOODS = listOf(
    FeastFoodItem(1, "🥟", "Пельмешек", Color(0xFFFFE082)),
    FeastFoodItem(2, "🍓", "Ягодка", Color(0xFFFF8A80)),
    FeastFoodItem(3, "🍌", "Бананчик", Color(0xFFFFEE58)),
    FeastFoodItem(4, "🍪", "Печенька", Color(0xFFD7CCC8)),
    FeastFoodItem(5, "⭐", "Звёздочка", Color(0xFFFFD54F))
)

data class FeastParticle(
    val id: Long,
    val emoji: String,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val scale: Float
)

@Composable
fun PelmenFeastScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    // Characters available to feed
    val characters = listOf(
        Triple("pelmen", "🥟", "Пельмень-Сикс"),
        Triple("cat", "🐱", "Котик Барсик"),
        Triple("bunny", "🐰", "Зайка Пушистик"),
        Triple("bear", "🐻", "Мишка Топтыжка")
    )

    var activeCharIndex by remember { mutableIntStateOf(0) }
    val currentChar = characters[activeCharIndex]

    // Satiety / happy hearts count
    var happyCount by remember { mutableIntStateOf(0) }
    var isChewing by remember { mutableStateOf(false) }
    var isJumping by remember { mutableStateOf(false) }

    // Jumping animation for happy dance
    val jumpAnim = remember { Animatable(0f) }

    // Particles for celebration
    val particles = remember { mutableStateListOf<FeastParticle>() }

    // Particle emission helper
    fun emitParticles(emoji: String) {
        repeat(12) {
            val angle = Random.nextFloat() * 2 * Math.PI.toFloat()
            val speed = Random.nextFloat() * 15f + 5f
            particles.add(
                FeastParticle(
                    id = System.nanoTime() + Random.nextLong(),
                    emoji = emoji,
                    x = 0f,
                    y = -50f,
                    vx = kotlin.math.cos(angle) * speed,
                    vy = kotlin.math.sin(angle) * speed - 5f,
                    scale = Random.nextFloat() * 0.6f + 0.8f
                )
            )
        }
    }

    // Particle tick loop
    LaunchedEffect(Unit) {
        while (true) {
            delay(16L)
            for (i in particles.size - 1 downTo 0) {
                val p = particles[i]
                particles[i] = p.copy(
                    x = p.x + p.vx,
                    y = p.y + p.vy,
                    vy = p.vy + 0.5f // gravity
                )
                if (p.y > 600f) {
                    particles.removeAt(i)
                }
            }
        }
    }

    // Feed action handler
    fun feedCurrentCharacter(food: FeastFoodItem) {
        soundManager.play("chime")
        haptics.vibrateShort()
        happyCount++
        isChewing = true
        isJumping = true

        emitParticles(food.emoji)

        scope.launch {
            jumpAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            jumpAnim.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            isJumping = false
        }

        scope.launch {
            delay(800L)
            isChewing = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE0F7FA))
            .testTag("pelmen_feast_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Background decorations (clouds & stars)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Draw joyful background bubbles
            drawCircle(Color.White.copy(alpha = 0.5f), radius = 120f, center = Offset(w * 0.2f, h * 0.15f))
            drawCircle(Color.White.copy(alpha = 0.4f), radius = 160f, center = Offset(w * 0.85f, h * 0.25f))
            drawCircle(Color.White.copy(alpha = 0.6f), radius = 100f, center = Offset(w * 0.5f, h * 0.08f))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Title & Happy Score
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "🥟", fontSize = 28.sp)
                        Text(
                            text = "Пир у Пельменя",
                            style = PlayTypography.titleLarge,
                            fontSize = 22.sp,
                            color = ColorInk,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Happy Hearts Counter
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFFFFEBEE),
                    shadowElevation = 4.dp,
                    modifier = Modifier.clickable {
                        // Switch character on tapping score card
                        activeCharIndex = (activeCharIndex + 1) % characters.size
                        soundManager.play("tap")
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "💖", fontSize = 24.sp)
                        Text(
                            text = "$happyCount",
                            style = PlayTypography.titleMedium,
                            fontSize = 20.sp,
                            color = Color(0xFFD32F2F),
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // Center Character Area with Jumping & Chewing Animation
            val charScale by animateFloatAsState(
                targetValue = if (isChewing) 1.2f else if (isJumping) 1.1f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "char_scale"
            )

            Box(
                modifier = Modifier
                    .size(240.dp)
                    .scale(charScale)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(6.dp, Color(0xFF80DEEA), CircleShape)
                    .clickable {
                        // Tap character for happy sound & reaction
                        soundManager.play("chime")
                        haptics.vibrateShort()
                        isJumping = true
                        emitParticles("✨")
                        scope.launch {
                            delay(600L)
                            isJumping = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isChewing) "😋" else currentChar.second,
                        fontSize = 90.sp,
                        modifier = Modifier.graphicsLayer {
                            translationY = -jumpAnim.value * 40f
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentChar.third,
                        style = PlayTypography.titleMedium,
                        fontSize = 20.sp,
                        color = ColorInk,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isChewing) "Ням-ням!" else "Хочу вкусняшку!",
                        style = PlayTypography.bodyMedium,
                        fontSize = 14.sp,
                        color = Color(0xFF00838F),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Floating Particles over Character
                Box(modifier = Modifier.fillMaxSize()) {
                    particles.forEach { p ->
                        Text(
                            text = p.emoji,
                            fontSize = 28.sp,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(x = p.x.dp, y = p.y.dp)
                                .scale(p.scale)
                        )
                    }
                }
            }

            // Bottom Tray: Giant Toddler-Friendly Food Buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Нажми на угощение, чтобы покормить!",
                    style = PlayTypography.bodyMedium,
                    fontSize = 16.sp,
                    color = Color(0xFF006064),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FEAST_FOODS.forEach { food ->
                        Surface(
                            shape = CircleShape,
                            color = food.color,
                            shadowElevation = 8.dp,
                            border = BorderStroke(3.dp, Color.White),
                            modifier = Modifier
                                .size(72.dp)
                                .testTag("food_${food.id}")
                                .clickable {
                                    feedCurrentCharacter(food)
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = food.emoji,
                                    fontSize = 36.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
