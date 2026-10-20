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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

data class WaterBead(
    val id: Int,
    var x: Float,
    var y: Float,
    var targetX: Float,
    var targetY: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterBeadBallScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var squeezeCount by remember { mutableStateOf(0) }
    var themeIndex by remember { mutableStateOf(0) }

    val themes = listOf(
        listOf(Color(0xFFFF3366), Color(0xFFFFCC00), Color(0xFF33CCFF), Color(0xFF33FF99), Color(0xFFCC33FF), Color(0xFFFF6699)), // Rainbow Orbeez
        listOf(Color(0xFFFF9AA2), Color(0xFFFFB7B2), Color(0xFFFFDAC1), Color(0xFFE2F0CB), Color(0xFFB5EAD7), Color(0xFFC7CEEA)), // Pastel Pearls
        listOf(Color(0xFF00FFFF), Color(0xFFFF00FF), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFFFF3300), Color(0xFF9900FF)), // Neon Galaxy
        listOf(Color(0xFFFFD700), Color(0xFFFFA500), Color(0xFFFF8C00), Color(0xFFFF4500), Color(0xFFDC143C), Color(0xFF8B0000))  // Sunset Gold
    )

    val currentThemeColors = themes[themeIndex]

    // Ball deformation
    var scaleX by remember { mutableStateOf(1f) }
    var scaleY by remember { mutableStateOf(1f) }

    val animScaleX = animateFloatAsState(
        targetValue = scaleX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "meshScaleX"
    )
    val animScaleY = animateFloatAsState(
        targetValue = scaleY,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "meshScaleY"
    )

    // Initialize 45 water beads inside the ball radius (~110f)
    var beads by remember {
        mutableStateOf(
            List(45) { id ->
                val angle = Random.nextFloat() * 2 * PI.toFloat()
                val dist = Random.nextFloat() * 90f
                WaterBead(
                    id = id,
                    x = cos(angle) * dist,
                    y = sin(angle) * dist,
                    targetX = cos(angle) * dist,
                    targetY = sin(angle) * dist,
                    vx = 0f,
                    vy = 0f,
                    radius = Random.nextFloat() * 6f + 10f, // 10dp to 16dp
                    color = currentThemeColors[id % currentThemeColors.size]
                )
            }
        )
    }

    // Update bead colors when theme changes
    LaunchedEffect(themeIndex) {
        beads = beads.map { bead ->
            bead.copy(color = currentThemeColors[bead.id % currentThemeColors.size])
        }
    }

    // Physics loop for bead wobbling and returning to center
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            beads = beads.map { bead ->
                // Spring force toward targetX/targetY
                val dx = bead.targetX - bead.x
                val dy = bead.targetY - bead.y
                bead.vx = bead.vx * 0.82f + dx * 0.15f
                bead.vy = bead.vy * 0.82f + dy * 0.15f
                var newX = bead.x + bead.vx
                var newY = bead.y + bead.vy

                // Boundary constraint inside ball radius (approx 105)
                val currentDist = sqrt(newX * newX + newY * newY)
                if (currentDist > 105f) {
                    val angle = atan2(newY, newX)
                    newX = cos(angle) * 105f
                    newY = sin(angle) * 105f
                    bead.vx = -bead.vx * 0.4f
                    bead.vy = -bead.vy * 0.4f
                }

                bead.copy(x = newX, y = newY)
            }
        }
    }

    fun triggerSquish(touchX: Float, touchY: Float, centerX: Float, centerY: Float) {
        squeezeCount++
        soundPlayer.playBubblePop()
        soundPlayer.playSquishElastic()

        // Push beads away from touch point or bulge them towards sides
        val relX = touchX - centerX
        val relY = touchY - centerY

        beads = beads.map { bead ->
            val angleToBead = atan2(bead.y - relY, bead.x - relX)
            val pushForce = Random.nextFloat() * 35f + 25f
            bead.copy(
                targetX = (bead.x + cos(angleToBead) * pushForce).coerceIn(-95f, 95f),
                targetY = (bead.y + sin(angleToBead) * pushForce).coerceIn(-95f, 95f),
                vx = cos(angleToBead) * 8f,
                vy = sin(angleToBead) * 8f
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFE0F7FA), Color(0xFFE1F5FE), Color(0xFFFCE4EC))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
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
                        .border(2.dp, Color(0xFF00ACC1), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color(0xFF00838F))
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    shadowElevation = 4.dp,
                    border = BorderStroke(2.dp, Color(0xFF00ACC1))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🫧", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Сжатия: $squeezeCount",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF006064)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        themeIndex = (themeIndex + 1) % themes.size
                        soundPlayer.playSparkleChime()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White, CircleShape)
                        .border(2.dp, Color(0xFF00ACC1), CircleShape)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = "Change Theme", tint = Color(0xFF00838F))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Водяные шарики антистресс! 💧",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF00838F)
            )
            Text(
                text = "Жмите и тяните сетку, чтобы шарики выдавливались!",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF006064).copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Main Interactive Mesh Ball Area
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                scaleX = 1.28f
                                scaleY = 0.75f
                                val centerX = size.width / 2f
                                val centerY = size.height / 2f
                                triggerSquish(offset.x, offset.y, centerX, centerY)
                            },
                            onDragEnd = {
                                scaleX = 1f
                                scaleY = 1f
                                scope.launch {
                                    beads = beads.map { it.copy(targetX = (it.id * 13f) % 80f - 40f, targetY = (it.id * 17f) % 80f - 40f) }
                                }
                            },
                            onDragCancel = {
                                scaleX = 1f
                                scaleY = 1f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val dx = dragAmount.x
                                val dy = dragAmount.y
                                scaleX = (1f + abs(dx) * 0.015f).coerceIn(0.7f, 1.5f)
                                scaleY = (1f + abs(dy) * 0.015f).coerceIn(0.7f, 1.5f)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                scaleX = 0.8f
                                scaleY = 1.25f
                                val centerX = size.width / 2f
                                val centerY = size.height / 2f
                                triggerSquish(offset.x, offset.y, centerX, centerY)
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
                // Drop shadow
                Box(
                    modifier = Modifier
                        .size(260.dp, 44.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color(0x22006064), CircleShape)
                )

                // Translucent Ball & Water Beads Canvas Inside Mesh
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .scale(animScaleX.value, animScaleY.value)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.85f),
                                    Color(0xFFB2EBF2).copy(alpha = 0.5f),
                                    Color(0xFF80DEEA).copy(alpha = 0.3f)
                                ),
                                center = Offset(90f, 90f),
                                radius = 320f
                            )
                        )
                        .border(BorderStroke(4.dp, Color.White), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f

                        // Draw water beads
                        beads.forEach { bead ->
                            val drawX = centerX + bead.x
                            val drawY = centerY + bead.y

                            drawCircle(
                                color = bead.color,
                                radius = bead.radius,
                                center = Offset(drawX, drawY)
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.7f),
                                radius = bead.radius * 0.35f,
                                center = Offset(drawX - bead.radius * 0.3f, drawY - bead.radius * 0.3f)
                            )
                        }

                        // Draw silicone mesh overlay
                        val meshColor = Color.White.copy(alpha = 0.75f)
                        val strokeW = 3f
                        for (i in -3..3) {
                            val offsetPos = i * 35f
                            drawLine(
                                color = meshColor,
                                start = Offset(centerX + offsetPos, centerY - 120f),
                                end = Offset(centerX + offsetPos * 0.7f, centerY + 120f),
                                strokeWidth = strokeW
                            )
                            drawLine(
                                color = meshColor,
                                start = Offset(centerX - 120f, centerY + offsetPos),
                                end = Offset(centerX + 120f, centerY + offsetPos * 0.7f),
                                strokeWidth = strokeW
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "💡 Нажмите на шарик или потяните в разные стороны!",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF006064)
                    )
                }
            }
        }
    }
}
