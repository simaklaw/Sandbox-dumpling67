package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

data class SqueezyParticle(
    val id: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val symbol: String,
    var alpha: Float = 1f,
    var scale: Float = 1f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SqueezyBallScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var squeezeCount by remember { mutableStateOf(0) }
    
    // Ball deformation and scale states
    var scaleX by remember { mutableStateOf(1f) }
    var scaleY by remember { mutableStateOf(1f) }
    var ballColorIndex by remember { mutableStateOf(0) }
    var faceIndex by remember { mutableStateOf(0) }
    
    val ballColors = listOf(
        Color(0xFFFF6B6B), // Coral Red
        Color(0xFFFFD93D), // Sunny Yellow
        Color(0xFF6BCB77), // Mint Green
        Color(0xFF4D96FF), // Sky Blue
        Color(0xFFFF78C4), // Hot Pink
        Color(0xFF9B51E0)  // Purple
    )
    
    val faces = listOf("🤪", "😆", "😎", "🤩", "😜", "😺", "🤭", "🤠")
    
    // Floating particles (stars, hearts, sparkles) when squeezed
    var particles by remember { mutableStateOf(listOf<SqueezyParticle>()) }
    
    // Animation for spring back
    val animScaleX = animateFloatAsState(targetValue = scaleX, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "scaleX")
    val animScaleY = animateFloatAsState(targetValue = scaleY, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "scaleY")

    // Particle tick
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            if (particles.isNotEmpty()) {
                particles = particles.mapNotNull { p ->
                    p.x += p.vx
                    p.y += p.vy
                    p.vy += 0.2f // gravity
                    p.alpha -= 0.03f
                    if (p.alpha <= 0f) null else p
                }
            }
        }
    }

    fun triggerSqueeze(centerX: Float, centerY: Float) {
        squeezeCount++
        soundPlayer.playBubblePop()
        faceIndex = Random.nextInt(faces.size)
        if (squeezeCount % 5 == 0) {
            ballColorIndex = (ballColorIndex + 1) % ballColors.size
        }
        
        // Spawn particles
        val symbols = listOf("✨", "⭐", "💖", "💫", "🫧", "🌟")
        val newParticles = mutableListOf<SqueezyParticle>()
        for (i in 0 until 8) {
            val angle = Random.nextFloat() * 2 * Math.PI.toFloat()
            val speed = Random.nextFloat() * 8f + 3f
            newParticles.add(
                SqueezyParticle(
                    id = Random.nextInt(),
                    x = centerX,
                    y = centerY,
                    vx = kotlin.math.cos(angle) * speed,
                    vy = kotlin.math.sin(angle) * speed - 2f,
                    symbol = symbols[Random.nextInt(symbols.size)]
                )
            )
        }
        particles = particles + newParticles
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFF0F5), Color(0xFFFFE4E1), Color(0xFFF3E5F5))
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White, CircleShape)
                        .border(2.dp, Color(0xFFFFB6C1), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color(0xFFD81B60))
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    shadowElevation = 4.dp,
                    border = BorderStroke(2.dp, Color(0xFFFFB6C1))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🤪", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Сжатия: $squeezeCount",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF880E4F)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        ballColorIndex = (ballColorIndex + 1) % ballColors.size
                        soundPlayer.playPop()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White, CircleShape)
                        .border(2.dp, Color(0xFFFFB6C1), CircleShape)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = "Change Color", tint = Color(0xFFD81B60))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Потискай смешной смайлик! 👇",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFAD1457)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Interactive Squeezy Ball Area
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                scaleX = 1.3f
                                scaleY = 0.7f
                                triggerSqueeze(size.width / 2f, size.height / 2f)
                            },
                            onDragEnd = {
                                scaleX = 1f
                                scaleY = 1f
                            },
                            onDragCancel = {
                                scaleX = 1f
                                scaleY = 1f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val dx = dragAmount.x
                                val dy = dragAmount.y
                                scaleX = (1f + kotlin.math.abs(dx) * 0.01f).coerceIn(0.7f, 1.6f)
                                scaleY = (1f + kotlin.math.abs(dy) * 0.01f).coerceIn(0.7f, 1.6f)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                scaleX = 0.75f
                                scaleY = 1.25f
                                triggerSqueeze(size.width / 2f, size.height / 2f)
                                scope.launch {
                                    delay(150)
                                    scaleX = 1f
                                    scaleY = 1f
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Ball Shadow
                Box(
                    modifier = Modifier
                        .size(240.dp, 40.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color(0x33000000), CircleShape)
                )

                // The Ball itself
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .scale(animScaleX.value, animScaleY.value)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.6f),
                                    ballColors[ballColorIndex],
                                    ballColors[ballColorIndex].copy(alpha = 0.8f)
                                ),
                                center = Offset(70f, 70f),
                                radius = 300f
                            )
                        )
                        .border(4.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = faces[faceIndex],
                            fontSize = 80.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Сквиш!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom controls / info
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.9f),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color(0xFFD81B60))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Тапай или тяни мячик в разные стороны!",
                        fontSize = 14.sp,
                        color = Color(0xFF880E4F),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Particle Overlay Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                particles.forEach { p ->
                    val paint = android.graphics.Paint().apply {
                        textSize = 48f
                        alpha = (p.alpha * 255).coerceIn(0f, 255f).toInt()
                    }
                    nativeCanvas.drawText(p.symbol, p.x, p.y, paint)
                }
            }
        }
    }
}
