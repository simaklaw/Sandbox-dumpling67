package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.*
import com.example.shared.scene.*
import com.example.ui.util.ToddlerPalmRejection
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class BalloonShape {
    OVAL,
    HEART,
    STAR,
    SOAP_BUBBLE,
    KITTY_FACE
}

data class ActiveBalloon(
    val id: Long,
    var x: Float,
    var y: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val shape: BalloonShape,
    val wobbleSpeed: Float,
    var wobbleOffset: Float = 0f,
    var isWinking: Boolean = false
)

data class ConfettiParticle(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    val color: Color,
    val shapeType: Int // 0: circle, 1: star, 2: rect
)

@Composable
fun BallonZone(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager(context, soundPlayer) }
    val haptics = rememberHaptics()
    val ageProfile = LocalAgeProfile.current
    val gameState by repository.gameState.collectAsState()
    val density = LocalDensity.current
    val edgeMarginPx = remember(gameState.palmRejectionMarginDp, density) {
        with(density) { gameState.palmRejectionMarginDp.dp.toPx() }
    }

    // Preload balloon sounds
    DisposableEffect(Unit) {
        soundManager.preload("balloons")
        onDispose {
            soundManager.release("balloons")
        }
    }

    val skyDecors = remember {
        listOf(
            DecorSpec(DecorKind.RAINBOW, 0.50f, 0.22f, 240f),
            DecorSpec(DecorKind.SUN, 0.88f, 0.10f, 76f),
            DecorSpec(DecorKind.CLOUD, 0.22f, 0.14f, 84f),
            DecorSpec(DecorKind.CLOUD, 0.68f, 0.16f, 76f),
            DecorSpec(DecorKind.HOT_AIR_BALLOON, 0.12f, 0.32f, 96f),
            DecorSpec(DecorKind.KITE, 0.84f, 0.35f, 80f),
            DecorSpec(DecorKind.BIRD, 0.18f, 0.48f, 60f),
            DecorSpec(DecorKind.BIRD, 0.80f, 0.48f, 60f)
        )
    }

    var popScore by remember { mutableIntStateOf(0) }
    var isBubbleMachineActive by remember { mutableStateOf(!ageProfile.isToddler2) }

    val balloons = remember { mutableStateListOf<ActiveBalloon>() }
    val confettiList = remember { mutableStateListOf<ConfettiParticle>() }

    var lastTouchTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Screen dimension bounds for balloon spawning
    var canvasWidth by remember { mutableFloatStateOf(1000f) }
    var canvasHeight by remember { mutableFloatStateOf(1800f) }

    val balloonPalette = listOf(
        Color(0xFFFF8FB1), // Soft Pink
        Color(0xFFFFB36B), // Soft Orange
        Color(0xFFFFD35C), // Soft Yellow
        Color(0xFF8EDC9D), // Soft Green
        Color(0xFF7FC8FF), // Soft Blue
        Color(0xFFB79CFF)  // Soft Purple
    )

    // Function to pop a specific balloon and explode confetti
    fun popBalloonAt(balloon: ActiveBalloon) {
        lastTouchTime = System.currentTimeMillis()
        soundManager.play("balloon_pop")
        haptics.vibrate(20)

        popScore++
        repository.addCoins(1)

        // Celebrate milestones: every 10th in 2yo mode, every 20th in 3-5yo mode
        val milestone = if (ageProfile.isToddler2) 10 else 20
        if (popScore % milestone == 0) {
            soundManager.play("fanfare_short")
            soundManager.play("praise")
        }

        // Spawn confetti burst (6-8 particles in 2yo mode, 16 in 3-5yo mode)
        val particleCount = if (ageProfile.isToddler2) 8 else 16
        repeat(particleCount) {
            val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
            val speed = Random.nextFloat() * 7f + 3f
            confettiList.add(
                ConfettiParticle(
                    id = System.nanoTime() + it,
                    x = balloon.x,
                    y = balloon.y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - 2.5f,
                    size = Random.nextFloat() * 10f + 8f,
                    color = listOf(balloon.color, ColorYellow, ColorPink, ColorBlue).random(),
                    shapeType = Random.nextInt(3)
                )
            )
        }
    }

    // Spawn 3-4 colored balloons from rainbow buttons
    fun spawnColoredBalloons(chosenColor: Color) {
        val maxBalloons = if (ageProfile.isToddler2) 5 else 8
        val spawnCount = if (ageProfile.isToddler2) 3 else 4

        // Trim oldest balloons if over limit
        while (balloons.size + spawnCount > maxBalloons && balloons.isNotEmpty()) {
            balloons.removeAt(0)
        }

        repeat(spawnCount) { idx ->
            val balloonSize = if (ageProfile.isToddler2) {
                Random.nextFloat() * 40f + 120f
            } else {
                Random.nextFloat() * 40f + 90f
            }
            val speed = if (ageProfile.isToddler2) {
                Random.nextFloat() * 0.8f + 1.1f
            } else {
                Random.nextFloat() * 3.5f + 2.2f
            }
            val xSpacing = canvasWidth / (spawnCount + 1f)
            val spawnX = (idx + 1) * xSpacing + (Random.nextFloat() - 0.5f) * 40f

            balloons.add(
                ActiveBalloon(
                    id = System.nanoTime() + Random.nextLong(10000),
                    x = spawnX.coerceIn(balloonSize, canvasWidth - balloonSize),
                    y = canvasHeight + 80f + idx * 45f,
                    speed = speed,
                    size = balloonSize,
                    color = chosenColor,
                    shape = if (ageProfile.isToddler2) BalloonShape.OVAL else BalloonShape.values().random(),
                    wobbleSpeed = Random.nextFloat() * 0.04f + 0.02f
                )
            )
        }
    }

    // Auto-demo in 2yo mode: after 10s idle, winks/wiggles a balloon
    LaunchedEffect(lastTouchTime, ageProfile.isToddler2) {
        if (ageProfile.isToddler2 && ageProfile.autoDemoIdleDelaySeconds > 0) {
            delay(ageProfile.autoDemoIdleDelaySeconds * 1000L)
            val b = balloons.firstOrNull()
            if (b != null) {
                b.isWinking = true
                soundManager.play("pop")
                delay(1200L)
                b.isWinking = false
            }
        }
    }

    // Balloon & Particle Physics Game Loop
    LaunchedEffect(ageProfile.isToddler2) {
        var frame = 0L
        while (isActive) {
            delay(16)
            frame++

            val maxBalloons = if (ageProfile.isToddler2) 5 else 8
            val spawnRate = if (ageProfile.isToddler2) 45L else (if (isBubbleMachineActive) 22L else 32L)

            // 1. Spawn new balloons periodically
            if (balloons.size < maxBalloons && canvasWidth > 100f) {
                if (frame % spawnRate == 0L) {
                    val isBubble = !ageProfile.isToddler2 && isBubbleMachineActive && Random.nextInt(100) < 45
                    val shape = if (isBubble) {
                        BalloonShape.SOAP_BUBBLE
                    } else if (ageProfile.isToddler2) {
                        listOf(BalloonShape.OVAL, BalloonShape.HEART, BalloonShape.KITTY_FACE).random()
                    } else {
                        BalloonShape.values().random()
                    }

                    val balloonSize = if (ageProfile.isToddler2) {
                        Random.nextFloat() * 40f + 120f // 120-160 dp for 2yo
                    } else if (shape == BalloonShape.SOAP_BUBBLE) {
                        Random.nextFloat() * 30f + 60f
                    } else {
                        Random.nextFloat() * 40f + 90f  // 90-130 dp for 3-5yo
                    }

                    val speed = if (ageProfile.isToddler2) {
                        Random.nextFloat() * 0.8f + 1.1f // ~60 dp/sec
                    } else {
                        Random.nextFloat() * 3.5f + 2.2f
                    }

                    balloons.add(
                        ActiveBalloon(
                            id = System.nanoTime() + Random.nextLong(10000),
                            x = Random.nextFloat() * (canvasWidth - balloonSize * 1.5f) + balloonSize * 0.75f,
                            y = canvasHeight + 100f,
                            speed = speed,
                            size = balloonSize,
                            color = balloonPalette.random(),
                            shape = shape,
                            wobbleSpeed = Random.nextFloat() * 0.04f + 0.02f
                        )
                    )
                }
            }

            // 2. Move active balloons upwards
            for (i in balloons.indices.reversed()) {
                val b = balloons[i]
                b.y -= b.speed
                b.wobbleOffset += b.wobbleSpeed
                b.x += sin(b.wobbleOffset) * 1.2f

                // Remove when reached top of screen
                if (b.y < -200f) {
                    balloons.removeAt(i)
                }
            }

            // 3. Move Confetti particles
            for (i in confettiList.indices.reversed()) {
                val p = confettiList[i]
                p.x += p.vx
                p.y += p.vy
                p.vy += 0.30f // Gravity
                p.size -= 0.16f

                if (p.size <= 0f || p.y > canvasHeight + 100f) {
                    confettiList.removeAt(i)
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("ballon_zone_screen")
    ) {
        // 1. Vector 3-Layer Scene Background: SKY
        SceneBackground(theme = SceneTheme.SKY)

        // 2. Harmless background decor layer (behind balloon touches)
        DecorLayer(items = skyDecors)

        canvasWidth = constraints.maxWidth.toFloat()
        canvasHeight = constraints.maxHeight.toFloat()

        val infiniteTransition = rememberInfiniteTransition(label = "clouds")
        val cloudFloat by infiniteTransition.animateFloat(
            initialValue = -15f,
            targetValue = 15f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cloud_float"
        )

        val hitSlopFactor = ageProfile.hitSlopMultiplier * ageProfile.hitSlopMultiplier

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(gameState.palmRejectionEnabled, edgeMarginPx, ageProfile.id) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val canvasSize = Size(size.width.toFloat(), size.height.toFloat())
                            val filteredPointers = ToddlerPalmRejection.filterIntentionalChanges(
                                changes = event.changes,
                                containerSize = canvasSize,
                                edgeMarginPx = edgeMarginPx,
                                enabled = gameState.palmRejectionEnabled
                            )
                            val activePointers = filteredPointers.filter { it.pressed }

                            for (pointer in activePointers) {
                                val touchPos = pointer.position

                                // Find balloon touching finger with hit slop tolerance
                                val hitBalloon = balloons.findLast { b ->
                                    val distSq = (touchPos.x - b.x) * (touchPos.x - b.x) + (touchPos.y - b.y) * (touchPos.y - b.y)
                                    distSq < (b.size * b.size * hitSlopFactor)
                                }

                                if (hitBalloon != null) {
                                    balloons.remove(hitBalloon)
                                    popBalloonAt(hitBalloon)
                                }
                            }
                        }
                    }
                }
        ) {
            // Draw clouds
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = 70f,
                center = Offset(size.width * 0.2f + cloudFloat, 160f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = 90f,
                center = Offset(size.width * 0.35f + cloudFloat, 150f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = 70f,
                center = Offset(size.width * 0.5f + cloudFloat, 160f)
            )

            // Draw green grass band at bottom
            val grassHeight = 50.dp.toPx()
            drawRect(
                color = Color(0xFF8EDC9D),
                topLeft = Offset(0f, size.height - grassHeight),
                size = Size(size.width, grassHeight)
            )
            drawLine(
                color = ColorInk,
                start = Offset(0f, size.height - grassHeight),
                end = Offset(size.width, size.height - grassHeight),
                strokeWidth = 3.5f
            )

            // Draw balloons
            balloons.forEach { b ->
                drawSingleBalloon(b)
            }

            // Draw confetti particles
            confettiList.forEach { p ->
                when (p.shapeType) {
                    0 -> drawCircle(color = p.color, radius = p.size, center = Offset(p.x, p.y))
                    1 -> drawStar(Offset(p.x, p.y), p.size * 1.4f, p.color)
                    else -> drawRect(
                        color = p.color,
                        topLeft = Offset(p.x - p.size, p.y - p.size),
                        size = Size(p.size * 2f, p.size * 1.2f)
                    )
                }
            }
        }

        // TOP & BOTTOM HUD: Only shown for 3-5yo mode (Icon-only buttons, 72/88 dp)
        if (!ageProfile.isToddler2) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // TOP RIGHT BAR: Bubble/Balloon switch (72 dp target)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .border(BorderStroke(3.dp, ColorInk), ShapeButton)
                            .background(if (isBubbleMachineActive) Color(0xFF00E5FF) else ColorWhite, ShapeButton)
                            .clip(ShapeButton)
                            .plasticineClickable(haptics = haptics) {
                                isBubbleMachineActive = !isBubbleMachineActive
                                soundManager.play("pop")
                            }
                    ) {
                        Text(
                            text = if (isBubbleMachineActive) "🫧" else "🎈",
                            fontSize = 32.sp
                        )
                    }
                }

                // BOTTOM CONTROLS: Rainbow Salute Button (88 dp, NO text) + Rainbow Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(88.dp)
                            .border(BorderStroke(3.5.dp, ColorInk), CircleShape)
                            .background(ColorPink, CircleShape)
                            .clip(CircleShape)
                            .plasticineClickable(haptics = haptics) {
                                soundManager.play("fanfare_short")
                                soundManager.play("praise")
                                balloons.forEach { b -> popBalloonAt(b) }
                                balloons.clear()
                            }
                            .testTag("mega_confetti_button")
                    ) {
                        Text("🎆", fontSize = 42.sp)
                    }

                    // Rainbow Color Buttons (spawns flurry of balloons of tapped color)
                    RainbowButtons(
                        onColor = { chosenColor ->
                            spawnColoredBalloons(chosenColor)
                        },
                        soundManager = soundManager,
                        haptics = haptics
                    )
                }
            }
        } else {
            // For 2yo mode: Direct bottom RainbowButtons (4 large 88dp buttons)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                RainbowButtons(
                    onColor = { chosenColor ->
                        spawnColoredBalloons(chosenColor)
                    },
                    soundManager = soundManager,
                    haptics = haptics
                )
            }
        }
    }
}

private fun DrawScope.drawSingleBalloon(b: ActiveBalloon) {
    val radius = b.size / 2f

    // 1. Thread line
    drawLine(
        color = ColorInk.copy(alpha = 0.4f),
        start = Offset(b.x, b.y + radius),
        end = Offset(b.x + sin(b.wobbleOffset) * 10f, b.y + radius + 40f),
        strokeWidth = 2.5f
    )

    // 2. Balloon Body
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                b.color.copy(alpha = 0.95f),
                b.color,
                ColorInk.copy(alpha = 0.15f)
            ),
            center = Offset(b.x - radius * 0.3f, b.y - radius * 0.3f),
            radius = radius * 1.2f
        ),
        radius = radius,
        center = Offset(b.x, b.y)
    )

    // 3. Highlight gloss
    drawCircle(
        color = Color.White.copy(alpha = 0.55f),
        radius = radius * 0.25f,
        center = Offset(b.x - radius * 0.4f, b.y - radius * 0.4f)
    )

    // 4. Always Happy Cute Face
    val eyeY = b.y - radius * 0.1f
    val eyeRadius = radius * 0.08f

    if (b.isWinking) {
        // One eye open, one eye winking arc
        drawCircle(color = ColorInk, radius = eyeRadius, center = Offset(b.x - radius * 0.25f, eyeY))
        drawArc(
            color = ColorInk,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(b.x + radius * 0.15f, eyeY - eyeRadius),
            size = Size(eyeRadius * 2.5f, eyeRadius * 2f),
            style = Stroke(width = 3f)
        )
    } else {
        // Two smiling dot eyes
        drawCircle(color = ColorInk, radius = eyeRadius, center = Offset(b.x - radius * 0.25f, eyeY))
        drawCircle(color = ColorInk, radius = eyeRadius, center = Offset(b.x + radius * 0.25f, eyeY))
    }

    // Cute smiling mouth curve
    val mouthPath = Path().apply {
        moveTo(b.x - radius * 0.2f, b.y + radius * 0.15f)
        quadraticBezierTo(
            b.x, b.y + radius * 0.35f,
            b.x + radius * 0.2f, b.y + radius * 0.15f
        )
    }
    drawPath(mouthPath, color = ColorInk, style = Stroke(width = 3.5f))
}

private fun DrawScope.drawStar(center: Offset, size: Float, color: Color) {
    val path = Path()
    val points = 5
    val outerRadius = size
    val innerRadius = size * 0.45f
    for (i in 0 until points * 2) {
        val angle = i * Math.PI.toFloat() / points - Math.PI.toFloat() / 2f
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val px = center.x + cos(angle) * r
        val py = center.y + sin(angle) * r
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, color)
}

@Composable
fun BalloonZone(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    BallonZone(repository = repository, soundPlayer = soundPlayer)
}

@Composable
fun BalloonPopScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    BallonZone(repository = repository, soundPlayer = soundPlayer)
}
