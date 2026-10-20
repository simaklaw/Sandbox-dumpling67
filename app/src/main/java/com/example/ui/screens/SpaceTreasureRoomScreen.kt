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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class SpaceItem(
    val id: Long,
    val emoji: String,
    val name: String,
    val color: Color,
    var x: Float, // 0.1 to 0.9 ratio
    var y: Float, // 0.0 to 1.0
    val speed: Float,
    val size: Float
)

enum class ParticleStyle {
    EMOJI, SPARK
}

data class SpaceParticle(
    val id: Long,
    val style: ParticleStyle,
    val text: String,
    val color: Color,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val scale: Float,
    val alpha: Float,
    val radius: Float
)

val SPACE_COLLECTION = listOf(
    SpaceItem(0, "⭐", "Звезда", Color(0xFFFFD54F), 0f, 0f, 0.003f, 48f),
    SpaceItem(0, "💎", "Кристалл", Color(0xFF80D8FF), 0f, 0f, 0.0035f, 44f),
    SpaceItem(0, "🪐", "Планета", Color(0xFFFF8A80), 0f, 0f, 0.0025f, 52f),
    SpaceItem(0, "☄️", "Комета", Color(0xFFEA80FC), 0f, 0f, 0.004f, 46f),
    SpaceItem(0, "🚀", "Ракета", Color(0xFF69F0AE), 0f, 0f, 0.0045f, 50f)
)

@Composable
fun SpaceTreasureRoomScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var score by remember { mutableStateOf(0) }
    var items by remember { mutableStateOf(listOf<SpaceItem>()) }
    var particles by remember { mutableStateOf(listOf<SpaceParticle>()) }
    var mascotReaction by remember { mutableStateOf("🧑‍🚀 Вау! Космические сокровища!") }
    var showDiscoParty by remember { mutableStateOf(false) }

    // Background twinkling stars animation
    val infiniteTransition = rememberInfiniteTransition(label = "space_stars")
    val starTwinkle by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkle"
    )

    // Game loop for falling items & particle animation
    LaunchedEffect(Unit) {
        var nextId = 0L
        while (true) {
            delay(500)
            if (items.size < 8) {
                val template = SPACE_COLLECTION.random(Random(System.currentTimeMillis()))
                val newItem = SpaceItem(
                    id = nextId++,
                    emoji = template.emoji,
                    name = template.name,
                    color = template.color,
                    x = Random.nextFloat() * 0.75f + 0.12f,
                    y = -0.1f,
                    speed = Random.nextFloat() * 0.002f + 0.0025f,
                    size = template.size
                )
                items = items + newItem
            }

            // Update positions
            items = items.mapNotNull { item ->
                item.y += item.speed
                if (item.y > 1.1f) null else item
            }

            // Update particles with smooth alpha fade out and scale reduction
            particles = particles.mapNotNull { p ->
                val nx = p.x + p.vx
                val ny = p.y + p.vy
                val nAlpha = p.alpha - 0.03f
                val nScale = p.scale * 0.94f
                if (nAlpha <= 0f || nScale < 0.05f) null else p.copy(x = nx, y = ny, alpha = nAlpha, scale = nScale)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .testTag("space_treasure_screen")
    ) {
        // Starry background canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val rand = Random(42)
            for (i in 0..60) {
                val sx = rand.nextFloat() * w
                val sy = rand.nextFloat() * h
                val alpha = (rand.nextFloat() * 0.7f + 0.3f) * starTwinkle
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = rand.nextFloat() * 3f + 1f,
                    center = Offset(sx, sy)
                )
            }
        }

        // Main layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Header bar: Title & Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(width = 2.dp, color = Color(0xFF6366F1)),
                    modifier = Modifier.padding(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🚀", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Космические Сокровища",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF312E81),
                    border = BorderStroke(width = 2.dp, color = Color(0xFF818CF8))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⭐", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "$score",
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Astronaut Mascot banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                color = Color(0xFF1E1B4B).copy(alpha = 0.85f),
                border = BorderStroke(width = 1.5.dp, color = Color(0xFF818CF8).copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showDiscoParty) "👽🕺" else "🧑‍🚀",
                        fontSize = 38.sp,
                        modifier = Modifier
                            .scale(if (showDiscoParty) 1.2f else 1.0f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = mascotReaction,
                        color = Color(0xFFE2E8F0),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Game Area Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.8f))
                    .border(width = 2.dp, color = Color(0xFF334155), shape = RoundedCornerShape(24.dp))
            ) {
                // Render falling items & particle explosions
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val wPx = constraints.maxWidth
                    val hPx = constraints.maxHeight

                    items.forEach { item ->
                        val px = (item.x * wPx).toInt()
                        val py = (item.y * hPx).toInt()

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(px, py) }
                                .size(item.size.dp)
                                .clip(CircleShape)
                                .background(item.color.copy(alpha = 0.3f))
                                .border(width = 2.dp, color = item.color, shape = CircleShape)
                                .clickable {
                                    // Collect item
                                    score += 1
                                    soundPlayer.playSparkleChime()
                                    mascotReaction = listOf(
                                        "🌟 Отлично! Поймал!",
                                        "🚀 Ух ты, какое сокровище!",
                                        "💎 Супер! Ещё одно!",
                                        "⭐ Звёздный полёт!"
                                    ).random()

                                    if (score % 10 == 0) {
                                        showDiscoParty = true
                                        soundPlayer.playFanfare()
                                        scope.launch {
                                            delay(2500)
                                            showDiscoParty = false
                                        }
                                    }

                                    // Spawn rich particle explosion (stardust & colorful glowing sparks)
                                    val colors = listOf(
                                        Color(0xFFFFD54F), // Gold
                                        Color(0xFF80D8FF), // Cyan
                                        Color(0xFFFF8A80), // Coral Pink
                                        Color(0xFFEA80FC), // Purple/Magenta
                                        Color(0xFF69F0AE), // Mint Green
                                        Color.White
                                    )
                                    val newParticles = mutableListOf<SpaceParticle>()
                                    val baseNano = System.nanoTime()

                                    // 14 glowing spark dots radiating outwards in all directions with fade out
                                    for (i in 1..14) {
                                        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                                        val speed = Random.nextFloat() * 0.015f + 0.003f
                                        newParticles.add(
                                            SpaceParticle(
                                                id = baseNano + i,
                                                style = ParticleStyle.SPARK,
                                                text = "",
                                                color = colors.random(),
                                                x = item.x,
                                                y = item.y,
                                                vx = cos(angle) * speed,
                                                vy = sin(angle) * speed,
                                                scale = Random.nextFloat() * 1.0f + 0.5f,
                                                alpha = 1f,
                                                radius = Random.nextFloat() * 5f + 2.5f
                                            )
                                        )
                                    }

                                    // 6 stardust emoji sparks
                                    for (i in 1..6) {
                                        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                                        val speed = Random.nextFloat() * 0.01f + 0.002f
                                        newParticles.add(
                                            SpaceParticle(
                                                id = baseNano + 30 + i,
                                                style = ParticleStyle.EMOJI,
                                                text = listOf("✨", "💫", "🌟", "⭐", "☄️").random(),
                                                color = Color.White,
                                                x = item.x,
                                                y = item.y,
                                                vx = cos(angle) * speed,
                                                vy = sin(angle) * speed,
                                                scale = Random.nextFloat() * 0.6f + 1.0f,
                                                alpha = 1f,
                                                radius = 0f
                                            )
                                        )
                                    }
                                    particles = particles + newParticles

                                    // Remove collected item
                                    items = items.filter { it.id != item.id }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.emoji,
                                fontSize = (item.size * 0.55f).sp
                            )
                        }
                    }

                    // Render particles (glowing sparks and stardust emojis with fade out)
                    particles.forEach { p ->
                        val pX = (p.x * wPx).toInt()
                        val pY = (p.y * hPx).toInt()
                        if (p.style == ParticleStyle.SPARK) {
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(pX, pY) }
                                    .size((p.radius * 2 * p.scale).dp)
                                    .graphicsLayer(alpha = p.alpha)
                                    .clip(CircleShape)
                                    .background(p.color)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(pX, pY) }
                                    .graphicsLayer(
                                        scaleX = p.scale,
                                        scaleY = p.scale,
                                        alpha = p.alpha
                                    )
                            ) {
                                Text(text = p.text, fontSize = 20.sp)
                            }
                        }
                    }
                }

                if (showDiscoParty) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF6366F1).copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF1E1B4B),
                            border = BorderStroke(width = 3.dp, color = Color(0xFFFFD54F))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🎉 КОСМИЧЕСКАЯ ДИСКОТЕКА! 🚀", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Ты собрал много сокровищ!", fontSize = 16.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer instructions for toddlers
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("💡 Нажимай на падающие звёздочки и кристаллы!", color = Color(0xFF94A3B8), fontSize = 14.sp)
                }
            }
        }
    }
}
