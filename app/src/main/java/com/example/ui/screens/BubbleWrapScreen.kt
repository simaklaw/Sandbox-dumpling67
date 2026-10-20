package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class BubbleItem(
    val id: Int,
    val isPopped: Boolean = false,
    val colorVariant: Int = 0
)

data class BubbleConfettiParticle(
    val id: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val size: Float,
    var alpha: Float,
    var rotation: Float,
    val vRot: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BubbleWrapScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    onBackClick: () -> Unit = {}
) {
    val haptics = rememberHaptics()
    val coroutineScope = rememberCoroutineScope()

    var currentThemeIndex by remember { mutableStateOf(0) }

    val themes = listOf(
        Triple("Классика", Color(0xFFE0F7FA), Color(0xFF80DEEA)),
        Triple("Неон", Color(0xFF1A1A2E), Color(0xFFE94560)),
        Triple("Розовый зефир", Color(0xFFFFEEEE), Color(0xFFFF80AB)),
        Triple("Киберпанк", Color(0xFF0F0E17), Color(0xFFFF8906))
    )

    val activeTheme = themes[currentThemeIndex]

    val bubbleList = remember {
        mutableStateListOf<BubbleItem>().apply {
            for (i in 0 until 48) {
                add(BubbleItem(id = i, colorVariant = i % 4))
            }
        }
    }

    var poppedCount by remember { mutableStateOf(0) }

    val resetSheet = {
        try {
            soundPlayer.playSparkleChime()
        } catch (_: Exception) {}
        haptics.vibrateSquish()
        for (i in bubbleList.indices) {
            bubbleList[i] = bubbleList[i].copy(isPopped = false)
        }
        poppedCount = 0
    }

    val popAll = {
        try {
            soundPlayer.playSparkleChime()
        } catch (_: Exception) {}
        haptics.vibrateSquish()
        for (i in bubbleList.indices) {
            if (!bubbleList[i].isPopped) {
                bubbleList[i] = bubbleList[i].copy(isPopped = true)
            }
        }
        poppedCount = bubbleList.size
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Пузырчатая пленка", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Лопнуто: $poppedCount / ${bubbleList.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                // Общая кнопка «Домой» (80 dp) рисуется поверх верхнего левого угла — оставляем под неё место
                navigationIcon = { androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(88.dp)) },
                actions = {
                    IconButton(
                        onClick = {
                            currentThemeIndex = (currentThemeIndex + 1) % themes.size
                            try {
                                soundPlayer.playSparkleChime()
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.testTag("theme_button")
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = "Сменить тему")
                    }
                    IconButton(
                        onClick = resetSheet,
                        modifier = Modifier.testTag("reset_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Новый лист")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = activeTheme.second.copy(alpha = 0.2f)
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = resetSheet,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f).padding(end = 8.dp).testTag("reset_sheet_btn")
                    ) {
                        Text("🔄 Надуть заново")
                    }
                    Button(
                        onClick = popAll,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f).padding(start = 8.dp).testTag("pop_all_btn")
                    ) {
                        Text("💥 Лопнуть всё")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(activeTheme.second, activeTheme.third)
                    )
                )
                .padding(innerPadding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Тема: ${activeTheme.first}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(bubbleList) { index, bubble ->
                            BubbleCell(
                                bubble = bubble,
                                themeIndex = currentThemeIndex,
                                onClick = {
                                    if (!bubble.isPopped) {
                                        try {
                                            soundPlayer.playBubblePop()
                                        } catch (_: Exception) {}
                                        haptics.vibratePop()
                                        bubbleList[index] = bubble.copy(isPopped = true)
                                        poppedCount++
                                    } else {
                                        try {
                                            soundPlayer.playSparkleChime()
                                        } catch (_: Exception) {}
                                        bubbleList[index] = bubble.copy(isPopped = false)
                                        poppedCount = (poppedCount - 1).coerceAtLeast(0)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BubbleCell(
    bubble: BubbleItem,
    themeIndex: Int,
    onClick: () -> Unit
) {
    val particles = remember { mutableStateListOf<BubbleConfettiParticle>() }

    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.8f else if (bubble.isPopped) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pressAndPopScale"
    )

    // Particle animation ticker
    LaunchedEffect(particles.size) {
        if (particles.isNotEmpty()) {
            while (particles.isNotEmpty()) {
                withFrameNanos { _ ->
                    val iterator = particles.iterator()
                    while (iterator.hasNext()) {
                        val p = iterator.next()
                        p.x += p.vx
                        p.y += p.vy
                        p.vy += 0.35f // gravity
                        p.alpha -= 0.03f
                        p.rotation += p.vRot
                        if (p.alpha <= 0f) {
                            iterator.remove()
                        }
                    }
                }
            }
        }
    }

    val bubbleColors = when (themeIndex) {
        1 -> listOf(Color(0xFFE94560), Color(0xFF0F3460))
        2 -> listOf(Color(0xFFFF80AB), Color(0xFFFF4081))
        3 -> listOf(Color(0xFFFF8906), Color(0xFFE53170))
        else -> listOf(Color(0xFF80DEEA), Color(0xFF00ACC1))
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(pressScale)
            .clip(CircleShape)
            .background(
                brush = if (!bubble.isPopped) {
                    Brush.radialGradient(bubbleColors)
                } else {
                    Brush.radialGradient(listOf(Color.LightGray.copy(alpha = 0.4f), Color.DarkGray.copy(alpha = 0.2f)))
                }
            )
            .border(
                width = 2.dp,
                color = if (!bubble.isPopped) Color.White.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.4f),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                    },
                    onTap = {
                        if (!bubble.isPopped) {
                            val colors = listOf(Color.Yellow, Color.Cyan, Color.Magenta, Color.Green, Color.White, Color(0xFFFF6B6B))
                            for (i in 0 until 12) {
                                val angle = Random.nextFloat() * 2f * kotlin.math.PI.toFloat()
                                val speed = Random.nextFloat() * 9f + 3f
                                particles.add(
                                    BubbleConfettiParticle(
                                        id = Random.nextInt(),
                                        x = 0f,
                                        y = 0f,
                                        vx = cos(angle) * speed,
                                        vy = sin(angle) * speed - 2.5f,
                                        color = colors.random(),
                                        size = Random.nextFloat() * 7f + 4f,
                                        alpha = 1f,
                                        rotation = Random.nextFloat() * 360f,
                                        vRot = Random.nextFloat() * 18f - 9f
                                    )
                                )
                            }
                        }
                        onClick()
                    }
                )
            }
            .drawWithCache {
                val highlightColor = Color.White.copy(alpha = 0.7f)
                val depressedColor = Color.Black.copy(alpha = 0.15f)

                onDrawBehind {
                    if (!bubble.isPopped) {
                        drawCircle(
                            color = highlightColor,
                            radius = size.minDimension / 5f,
                            center = Offset(size.width * 0.35f, size.height * 0.35f)
                        )
                    } else {
                        drawCircle(
                            color = depressedColor,
                            radius = size.minDimension * 0.35f,
                            center = Offset(size.width * 0.5f, size.height * 0.5f)
                        )
                    }

                    for (p in particles) {
                        val centerX = size.width / 2f + p.x
                        val centerY = size.height / 2f + p.y
                        rotate(p.rotation, pivot = Offset(centerX, centerY)) {
                            drawRect(
                                color = p.color.copy(alpha = p.alpha.coerceIn(0f, 1f)),
                                topLeft = Offset(centerX - p.size / 2f, centerY - p.size / 2f),
                                size = androidx.compose.ui.geometry.Size(p.size, p.size * 1.5f)
                            )
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Rendered efficiently via drawWithCache
    }
}
