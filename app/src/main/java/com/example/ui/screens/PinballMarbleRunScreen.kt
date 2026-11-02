package com.example.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.example.shared.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.screens.Pinball.PinballPhysicsEngine
import com.example.screens.Pinball.SpeedMode
import com.example.shared.*
import com.example.shared.scene.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

data class BumperFlash(
    val id: Int,
    val center: Offset,
    val startTime: Long
)

data class SparkleParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color
)

@Composable
fun PinballMarbleRunScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val coroutineScope = rememberCoroutineScope()

    var score by remember { mutableIntStateOf(0) }
    var speedMode by remember { mutableStateOf(SpeedMode.BUNNY) }
    var autoPaws by remember { mutableStateOf(true) }
    var multiplierUi by remember { mutableIntStateOf(1) }

    // Visual animation states
    val bumperFlashes = remember { mutableStateListOf<BumperFlash>() }
    val particles = remember { mutableStateListOf<SparkleParticle>() }

    // Flipper touch states
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }

    // Ball Colors & Trail
    val ballColors = remember {
        listOf(
            Color(0xFFFF2A55),
            Color(0xFFFF8800),
            Color(0xFFFFD200),
            Color(0xFF00E676),
            Color(0xFF2979FF),
            Color(0xFFD500F9)
        )
    }
    val ballSoundKeys = remember {
        listOf("rb_red", "rb_orange", "rb_yellow", "rb_green", "rb_blue", "rb_purple")
    }
    var ballColorIndex by remember { mutableIntStateOf(0) }
    val currentBallColor = ballColors[ballColorIndex]
    val trailPoints = remember { mutableStateListOf<Offset>() }

    // Decor controller for game-triggered decor reactions
    val decorController = remember { DecorController() }

    val pinballDecors = remember {
        listOf(
            DecorSpec(DecorKind.STAR, 0.16f, 0.22f, 52f, interactive = false, id = "star_bump"),
            DecorSpec(DecorKind.STAR, 0.84f, 0.22f, 52f, interactive = false, id = "stars_all"),
            DecorSpec(DecorKind.SUN, 0.50f, 0.15f, 64f, interactive = false, id = "moon_slingshot"),
            DecorSpec(DecorKind.STAR, 0.14f, 0.42f, 48f, interactive = false, id = "stars_all"),
            DecorSpec(DecorKind.STAR, 0.86f, 0.42f, 48f, interactive = false, id = "star_bump")
        )
    }

    // First time hint preference
    val prefs = remember { context.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE) }
    var isFirstLaunch by remember { mutableStateOf(prefs.getBoolean("first_launch_pinball", true)) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var showIdleHint by remember { mutableStateOf(false) }

    // Physics Engine instance
    val engine = remember {
        PinballPhysicsEngine(
            onScoreAdded = { delta ->
                score += delta
            },
            onSoundTriggered = { sfx ->
                soundManager.play(sfx)
                haptics.vibrateShort()
            },
            onBumperHit = { id ->
                // Visual ring flash
                bumperFlashes.add(BumperFlash(id, Offset.Zero, System.currentTimeMillis()))
                decorController.trigger("star_bump", DecorReaction.BULB_FLASH)
            },
            onStarHit = { _ ->
                haptics.vibrate(20)
                decorController.trigger("moon_slingshot", DecorReaction.SMILE)
            },
            onAllStarsLit = {
                decorController.trigger("stars_all", DecorReaction.SHIMMER_SPARKLE)
                // Salute particles
                repeat(15) {
                    particles.add(
                        SparkleParticle(
                            x = 4.5f,
                            y = 3.0f,
                            vx = (Random.nextFloat() - 0.5f) * 8f,
                            vy = (Random.nextFloat() - 0.5f) * 8f,
                            color = listOf(ColorPink, ColorYellow, ColorBlue, ColorGreen).random()
                        )
                    )
                }
            }
        )
    }

    engine.onMultiplier = { m -> multiplierUi = m; soundManager.play("chime") }
    engine.onAllGemsLit = {
        repeat(20) {
            particles.add(SparkleParticle(4.5f, 8f, (Random.nextFloat() - 0.5f) * 10f, (Random.nextFloat() - 0.7f) * 10f, listOf(ColorPink, ColorYellow, ColorBlue, ColorGreen).random()))
        }
    }
    // Sync state to engine
    LaunchedEffect(speedMode) {
        engine.currentSpeedMode = speedMode
    }
    LaunchedEffect(autoPaws) {
        engine.autoPawsEnabled = autoPaws
    }

    // Idle hint monitor (if auto-paws is OFF and 6s of inactivity)
    LaunchedEffect(lastInteractionTime, autoPaws) {
        showIdleHint = false
        if (!autoPaws) {
            delay(6000)
            showIdleHint = true
        }
    }

    // Physics simulation loop bound to frames
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                engine.step(frameTimeNanos)
                val balls = engine.getBalls()
                if (balls.isNotEmpty()) {
                    balls.forEach { b ->
                        trailPoints.add(Offset(b.position.x, b.position.y))
                    }
                    while (trailPoints.size > 25 * balls.size) {
                        trailPoints.removeAt(0)
                    }
                } else if (trailPoints.isNotEmpty()) {
                    trailPoints.clear()
                }
            }
        }
    }

    // Particle decay & Bumper flashes cleaner
    LaunchedEffect(bumperFlashes.size, particles.size) {
        while (isActive && (bumperFlashes.isNotEmpty() || particles.isNotEmpty())) {
            delay(30)
            val now = System.currentTimeMillis()
            bumperFlashes.removeAll { now - it.startTime > 250L }

            for (i in particles.indices.reversed()) {
                val p = particles.getOrNull(i) ?: continue
                val nx = p.x + p.vx * 0.03f
                val ny = p.y + p.vy * 0.03f
                if (nx < 0f || nx > 9f || ny < 0f || ny > 16f) {
                    particles.removeAt(i)
                } else {
                    particles[i] = p.copy(x = nx, y = ny)
                }
            }
        }
    }

    // 5-minute accelerated debug simulation test
    val runDebugSimulation: () -> Unit = {
        coroutineScope.launch {
            Log.d("PinballUnstuck", "=== STARTING 5-MIN PINBALL SIMULATION TEST ===")
            soundManager.play("chime")
            val startTime = System.currentTimeMillis()
            val totalSimSteps = 5 * 60 * 60 // 5 minutes at 60Hz = 18,000 steps

            for (step in 0 until totalSimSteps) {
                // Random flipper actions
                if (step % 40 == 0) {
                    engine.setLeftFlipperEngaged(Random.nextBoolean())
                    engine.setRightFlipperEngaged(Random.nextBoolean())
                }
                engine.step(step * 16_666_666L)
            }

            Log.d("PinballUnstuck", "=== 5-MIN SIMULATION COMPLETED ===")
            Log.d("PinballUnstuck", "Unstuck Low Speed triggers: ${engine.unstuckLowSpeedCount}")
            Log.d("PinballUnstuck", "Unstuck No Displacement triggers: ${engine.unstuckNoDisplacementCount}")
            Log.d("PinballUnstuck", "Unstuck On Flipper triggers: ${engine.unstuckOnFlipperCount}")
            Log.d("PinballUnstuck", "Unstuck Out Of Bounds triggers: ${engine.unstuckOutOfBoundsCount}")
            Log.d("PinballUnstuck", "Unstuck NaN speed/pos triggers: ${engine.unstuckNaNCount}")
            Log.d("PinballUnstuck", "Max Ball Speed Recorded: ${engine.maxRecordedSpeed}")
            soundManager.play("fanfare_short")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Чистый фон без наложений (раньше поверх поля просвечивала комната с декором)
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF311B92), Color(0xFF512DA8), Color(0xFF7E57C2))))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row: Score & 3-Star Sign at Top-Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 3 Cartoon Stars Sign
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(ColorPurple.copy(alpha = 0.25f), CircleShape)
                        .border(BorderStroke(2.dp, ColorInk), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = "⭐", fontSize = 20.sp)
                    Text(text = "🌟", fontSize = 24.sp)
                    Text(text = "⭐", fontSize = 20.sp)
                    if (multiplierUi > 1) Text(text = "×$multiplierUi", fontSize = 22.sp, fontWeight = FontWeight.Black, color = ColorInk)
                }

                // Score text ★ 130 (40sp bold, without words)
                // Long press 3s triggers hidden stability test
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    runDebugSimulation()
                                }
                            )
                        }
                        .background(ColorWhite, ShapeButton)
                        .border(BorderStroke(3.dp, ColorInk), ShapeButton)
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    Text(text = "⭐", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$score",
                        style = PlayTypography.titleLarge,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = ColorInk
                    )
                }
            }

            // Pinball Playfield Canvas (Scales 9x16 world, centered)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                PinballCanvas(
                    engine = engine,
                    bumperFlashes = bumperFlashes,
                    particles = particles,
                    leftFlipperActive = leftPressed,
                    rightFlipperActive = rightPressed,
                    ballColor = currentBallColor,
                    trailPoints = trailPoints,
                    modifier = Modifier.fillMaxSize()
                )

                // Multi-touch Flipper Interaction Zones (left/right bottom 40% of field)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.40f)
                        .align(Alignment.BottomCenter)
                ) {
                    // Left Flipper Tap Zone
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        lastInteractionTime = System.currentTimeMillis()
                                        if (isFirstLaunch) {
                                            isFirstLaunch = false
                                            prefs.edit().putBoolean("first_launch_pinball", false).apply()
                                        }
                                        leftPressed = true
                                        engine.holdLeft(true)
                                        tryAwaitRelease()
                                        leftPressed = false
                                        engine.holdLeft(false)
                                    }
                                )
                            }
                    )
                    // Right Flipper Tap Zone
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        lastInteractionTime = System.currentTimeMillis()
                                        if (isFirstLaunch) {
                                            isFirstLaunch = false
                                            prefs.edit().putBoolean("first_launch_pinball", false).apply()
                                        }
                                        rightPressed = true
                                        engine.holdRight(true)
                                        tryAwaitRelease()
                                        rightPressed = false
                                        engine.holdRight(false)
                                    }
                                )
                            }
                    )
                }
            }

            // Две большие кнопки-лапки: левая и правая лопатки (работают одновременно, мультитач)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                for (isLeft in listOf(true, false)) {
                    val pressed = if (isLeft) leftPressed else rightPressed
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(BorderStroke(4.dp, ColorInk), RoundedCornerShape(28.dp))
                            .background(
                                if (pressed) Color(0xFFFFD54F) else if (isLeft) Color(0xFFFF8A65) else Color(0xFF4DD0E1),
                                RoundedCornerShape(28.dp)
                            )
                            .clip(RoundedCornerShape(28.dp))
                            .pointerInput(isLeft) {
                                detectTapGestures(
                                    onPress = {
                                        lastInteractionTime = System.currentTimeMillis()
                                        if (isFirstLaunch) {
                                            isFirstLaunch = false
                                            prefs.edit().putBoolean("first_launch_pinball", false).apply()
                                        }
                                        if (isLeft) { leftPressed = true; engine.holdLeft(true) }
                                        else { rightPressed = true; engine.holdRight(true) }
                                        haptics.vibrateShort()
                                        tryAwaitRelease()
                                        if (isLeft) { leftPressed = false; engine.holdLeft(false) }
                                        else { rightPressed = false; engine.holdRight(false) }
                                    }
                                )
                            }
                            .testTag(if (isLeft) "pinball_left_flipper_btn" else "pinball_right_flipper_btn")
                    ) {
                        Text(text = if (isLeft) "⬆️ 🐾" else "🐾 ⬆️", fontSize = 28.sp)
                    }
                }
            }

            // Bottom Action Control Panel (Outside field, 72-96dp buttons)
            Surface(
                shape = ShapeCard,
                color = ColorBgSoft,
                border = BorderStroke(3.dp, ColorInk),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. "New Ball" Button (🎾+)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .border(BorderStroke(3.dp, ColorInk), ShapeButton)
                            .background(ColorYellow, ShapeButton)
                            .clip(ShapeButton)
                            .plasticineClickable(haptics = haptics) {
                                soundManager.play("tap")
                                engine.spawnBall()
                            }
                            .testTag("pinball_new_ball_btn")
                    ) {
                        Text(text = "⚪➕", fontSize = 28.sp)
                    }

                    // 2. Ball Color Toggle Button (72 dp, circle of current ball color)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .border(BorderStroke(3.dp, ColorInk), CircleShape)
                            .background(currentBallColor, CircleShape)
                            .clip(CircleShape)
                            .plasticineClickable(haptics = haptics) {
                                ballColorIndex = (ballColorIndex + 1) % ballColors.size
                                val key = ballSoundKeys[ballColorIndex]
                                val played = soundManager.play(key)
                                if (!played) soundManager.play("pop")
                                haptics.vibrateShort()
                            }
                            .testTag("pinball_ball_color_btn")
                    ) {
                        Text(text = "🎨", fontSize = 28.sp)
                    }

                    // 3. "Clear" / Reset Button (🧹 broom)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .border(BorderStroke(3.dp, ColorInk), ShapeButton)
                            .background(ColorPink, ShapeButton)
                            .clip(ShapeButton)
                            .plasticineClickable(haptics = haptics) {
                                soundManager.play("whoosh")
                                score = 0
                                multiplierUi = 1
                                engine.clearAllAndReset()
                            }
                            .testTag("pinball_clear_btn")
                    ) {
                        Text(text = "🧹", fontSize = 30.sp)
                    }

                    // 4. Speed Mode Selector [🐢, 🐇, 🚀]
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpeedModeButton("🐢", speedMode == SpeedMode.TURTLE, haptics) {
                            speedMode = SpeedMode.TURTLE
                            soundManager.play("tap")
                        }
                        SpeedModeButton("🐇", speedMode == SpeedMode.BUNNY, haptics) {
                            speedMode = SpeedMode.BUNNY
                            soundManager.play("tap")
                        }
                        SpeedModeButton("🚀", speedMode == SpeedMode.ROCKET, haptics) {
                            speedMode = SpeedMode.ROCKET
                            soundManager.play("tap")
                        }
                    }

                    // 5. "Auto-paws" Mode Toggle [🐾]
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .border(
                                BorderStroke(
                                    width = if (autoPaws) 3.5.dp else 2.5.dp,
                                    color = if (autoPaws) ColorOrange else ColorInk.copy(alpha = 0.4f)
                                ),
                                ShapeButton
                            )
                            .background(if (autoPaws) ColorWhite else ColorBgSoft, ShapeButton)
                            .clip(ShapeButton)
                            .plasticineClickable(haptics = haptics) {
                                autoPaws = !autoPaws
                                soundManager.play("pop")
                            }
                            .testTag("pinball_autopaws_btn")
                    ) {
                        Text(text = "🐾", fontSize = 30.sp)
                    }
                }
            }
        }

        // Animated Guide Hand Hint for Flipper zones
        if (isFirstLaunch || showIdleHint) {
            HintHand(
                from = Offset(60f, 480f),
                mode = "tap",
                delayMs = 200
            )
        }
    }
}

@Composable
fun SpeedModeButton(
    emoji: String,
    isSelected: Boolean,
    haptics: Haptics,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .border(
                BorderStroke(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) ColorInk else ColorInk.copy(alpha = 0.3f)
                ),
                ShapeSmall
            )
            .background(if (isSelected) ColorYellow else ColorWhite, ShapeSmall)
            .clip(ShapeSmall)
            .plasticineClickable(haptics = haptics, onClick = onClick)
    ) {
        Text(text = emoji, fontSize = 20.sp)
    }
}

@Composable
fun PinballCanvas(
    engine: PinballPhysicsEngine,
    bumperFlashes: List<BumperFlash>,
    particles: List<SparkleParticle>,
    leftFlipperActive: Boolean,
    rightFlipperActive: Boolean,
    ballColor: Color,
    trailPoints: List<Offset>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val worldW = engine.worldWidth
        val worldH = engine.worldHeight
        val scale = min(size.width / (worldW + 0.8f), size.height / (worldH + 0.6f))
        val offsetX = (size.width - worldW * scale) / 2f
        val offsetY = (size.height - worldH * scale) / 2f
        fun px(x: Float) = offsetX + x * scale
        fun py(y: Float) = offsetY + y * scale
        fun pd(d: Float) = d * scale
        fun pt(x: Float, y: Float) = Offset(px(x), py(y))
        val now = System.currentTimeMillis()
        val tSec = (now % 100000L) / 1000f
        fun hash(k: Int): Float { val v = sin(k * 12.9898f) * 43758.547f; return v - floor(v) }

        // ── корпус стола: тёмная рама и хромированная кромка ──
        val fieldPath = Path().apply {
            moveTo(px(0f), py(13f)); lineTo(px(0f), py(4.5f))
            arcTo(androidx.compose.ui.geometry.Rect(px(0f), py(0f), px(9f), py(9f)), 180f, 180f, false)
            lineTo(px(9f), py(13f)); lineTo(px(engine.rightPivot.x), py(engine.rightPivot.y))
            lineTo(px(engine.rightPivot.x), py(18f)); lineTo(px(engine.leftPivot.x), py(18f))
            lineTo(px(engine.leftPivot.x), py(engine.leftPivot.y)); close()
        }
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF8E24AA), Color(0xFF4A148C), Color(0xFF311B92))),
            Offset(px(0f) - pd(0.55f), py(0f) - pd(0.45f)), Size(pd(worldW + 1.1f), pd(worldH + 0.7f)),
            androidx.compose.ui.geometry.CornerRadius(pd(2.2f))
        )
        drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(px(0f) - pd(0.55f), py(0f) - pd(0.45f)), Size(pd(worldW + 1.1f), pd(worldH + 0.7f)),
            androidx.compose.ui.geometry.CornerRadius(pd(2.2f)), style = Stroke(pd(0.05f)))

        // ── игровое поле: ночное небо с огоньками ──
        drawPath(fieldPath, Brush.verticalGradient(listOf(Color(0xFF1A1055), Color(0xFF3A1D86), Color(0xFF5E2EA6)), py(0f), py(18f)))
        clipPath(fieldPath) {
            // мягкие цветные круги-узоры
            drawCircle(Color(0xFFFF4081).copy(alpha = 0.10f), pd(3.4f), pt(4.5f, 6.4f))
            drawCircle(Color(0xFF40C4FF).copy(alpha = 0.09f), pd(2.3f), pt(4.5f, 6.4f))
            drawCircle(Color(0xFFFFD740).copy(alpha = 0.08f), pd(1.2f), pt(4.5f, 6.4f))
            drawCircle(Color.White.copy(alpha = 0.10f), pd(3.4f), pt(4.5f, 6.4f), style = Stroke(pd(0.06f)))
            drawCircle(Color.White.copy(alpha = 0.10f), pd(2.3f), pt(4.5f, 6.4f), style = Stroke(pd(0.06f)))
            // ромбики-текстура
            for (k in 0 until 70) {
                val x = hash(k * 3 + 1) * worldW; val y = hash(k * 3 + 2) * 17f
                val tw = 0.5f + 0.5f * sin(tSec * 2f + k)
                drawCircle(Color.White.copy(alpha = 0.10f + 0.35f * tw * hash(k)), pd(0.025f + hash(k * 5) * 0.035f), pt(x, y))
            }
            // тёмный слив под лапками
            drawRect(Color.Black.copy(alpha = 0.55f), pt(engine.leftPivot.x, 16.9f), Size(pd(engine.rightPivot.x - engine.leftPivot.x), pd(1.2f)))
            // световые «лыжи»-стрелки к лапкам
            for (side in 0..1) {
                for (k in 0 until 4) {
                    val ax = if (side == 0) 0.6f + k * 0.22f else 8.4f - k * 0.22f
                    val ay = 13.0f + k * 0.62f
                    val blink = ((tSec * 3f).toInt() % 4 == k)
                    drawCircle((if (blink) Color(0xFFFFEB3B) else Color(0xFFFFEB3B).copy(alpha = 0.22f)), pd(0.09f), pt(ax, ay))
                }
            }
        }

        // ── стенки: хромированные трубы ──
        fun rail(path: Path) {
            drawPath(path, Color(0xFF1A0B3D), style = Stroke(pd(0.50f), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, Brush.linearGradient(listOf(Color(0xFFE1BEE7), Color(0xFF9575CD), Color(0xFFB39DDB)), Offset(0f, offsetY), Offset(size.width, offsetY + pd(worldH))),
                style = Stroke(pd(0.34f), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, Color.White.copy(alpha = 0.55f), style = Stroke(pd(0.07f), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val outline = Path().apply {
            moveTo(px(-0.2f), py(13f)); lineTo(px(-0.2f), py(4.5f))
            arcTo(androidx.compose.ui.geometry.Rect(px(-0.2f), py(-0.2f), px(9.2f), py(9.2f)), 180f, 180f, false)
            lineTo(px(9.2f), py(13f))
        }
        rail(outline)
        rail(Path().apply { moveTo(px(0f), py(13f)); lineTo(px(engine.leftPivot.x), py(engine.leftPivot.y)) })
        rail(Path().apply { moveTo(px(9f), py(13f)); lineTo(px(engine.rightPivot.x), py(engine.rightPivot.y)) })

        // ── ролловеры-звёзды в арке ──
        engine.starPositions.forEachIndexed { idx, p ->
            val lit = engine.starLitStates[idx]
            val c = pt(p.x, p.y)
            val rr = pd(0.42f)
            if (lit) drawCircle(Color(0xFFFFEB3B).copy(alpha = 0.35f), rr * 1.9f, c)
            val star = Path().apply {
                for (k in 0 until 10) {
                    val a = -PI.toFloat() / 2f + k * PI.toFloat() / 5f
                    val r = if (k % 2 == 0) rr else rr * 0.45f
                    val x = c.x + cos(a) * r; val y = c.y + sin(a) * r
                    if (k == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(star, if (lit) Color(0xFFFFEB3B) else Color(0xFF4527A0))
            drawPath(star, if (lit) Color(0xFFFF8F00) else Color(0xFF9575CD), style = Stroke(pd(0.05f), join = StrokeJoin.Round))
        }

        // ── спиннер (правая дорожка) ──
        run {
            val c = pt(engine.spinnerPos.x, engine.spinnerPos.y)
            drawCircle(Color.Black.copy(alpha = 0.35f), pd(0.55f), c)
            drawCircle(Color(0xFF80DEEA).copy(alpha = 0.5f), pd(0.55f), c, style = Stroke(pd(0.05f)))
            val a = engine.spinAngle
            val ca = cos(a); val sa = sin(a)
            val half = pd(0.46f)
            drawLine(Color(0xFFFF7043), Offset(c.x - half * ca, c.y - half * 0.45f * sa), Offset(c.x + half * ca, c.y + half * 0.45f * sa), pd(0.16f), StrokeCap.Round)
            drawCircle(Color.White, pd(0.08f), c)
        }

        // ── воронка («чёрная дыра») ──
        run {
            val c = pt(engine.saucerPos.x, engine.saucerPos.y)
            val rr = pd(engine.saucerR + 0.18f)
            val fl = engine.saucerFlash
            drawCircle(Color(0xFF7C4DFF).copy(alpha = 0.30f + 0.4f * fl), rr * 1.5f, c)
            drawCircle(Brush.radialGradient(listOf(Color.Black, Color(0xFF1A0B3D)), c, rr), rr, c)
            for (k in 0 until 3) {
                val a0 = tSec * 4f + k * 2.094f
                drawArc(Color(0xFFB388FF).copy(alpha = 0.8f), Math.toDegrees(a0.toDouble()).toFloat(), 80f, false,
                    Offset(c.x - rr * 0.8f, c.y - rr * 0.8f), Size(rr * 1.6f, rr * 1.6f), style = Stroke(pd(0.06f), cap = StrokeCap.Round))
                drawArc(Color(0xFF80D8FF).copy(alpha = 0.7f), Math.toDegrees((a0 * 1.6f).toDouble()).toFloat() + 40f, 60f, false,
                    Offset(c.x - rr * 0.5f, c.y - rr * 0.5f), Size(rr, rr), style = Stroke(pd(0.05f), cap = StrokeCap.Round))
            }
            drawCircle(Color(0xFFD1C4E9), rr, c, style = Stroke(pd(0.09f)))
        }

        // ── кристаллы-мишени на стенках ──
        engine.gemPositions.forEachIndexed { idx, p ->
            val c = pt(p.x, p.y)
            val lit = engine.gemLit[idx]
            val fl = engine.gemFlash[idx]
            val r = pd(engine.gemR) * (1f + 0.25f * fl)
            if (lit) drawCircle(Color(0xFFFFEB3B).copy(alpha = 0.35f + 0.3f * fl), r * 1.8f, c)
            val gem = Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r * 0.9f, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r * 0.9f, c.y); close() }
            drawPath(gem, if (lit) Brush.linearGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFB300)), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r))
                else Brush.linearGradient(listOf(Color(0xFF80DEEA), Color(0xFF0097A7)), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)))
            drawPath(gem, Color.White.copy(alpha = 0.85f), style = Stroke(pd(0.04f), join = StrokeJoin.Round))
            drawLine(Color.White.copy(alpha = 0.7f), Offset(c.x - r * 0.4f, c.y - r * 0.25f), Offset(c.x, c.y - r * 0.7f), pd(0.04f), StrokeCap.Round)
        }

        // ── рогатки: резиновые треугольники ──
        for (side in 0..1) {
            val mx = { x: Float -> if (side == 0) x else 9f - x }
            val tri = Path().apply {
                moveTo(px(mx(1.15f)), py(10.6f)); lineTo(px(mx(2.25f)), py(13.35f)); lineTo(px(mx(1.15f)), py(12.6f)); close()
            }
            val fl = engine.slingFlash[side]
            drawPath(tri, Brush.linearGradient(listOf(Color(0xFFFFE082).copy(alpha = 1f), Color(0xFFFF7043)), pt(mx(1.15f), 10.6f), pt(mx(2.25f), 13.35f)))
            drawPath(tri, Color(0xFF1A0B3D), style = Stroke(pd(0.12f), join = StrokeJoin.Round))
            drawLine(Color.White.copy(alpha = 0.4f + 0.6f * fl), pt(mx(1.3f), 10.9f), pt(mx(2.15f), 13.0f), pd(0.09f + 0.07f * fl), StrokeCap.Round)
            if (fl > 0f) drawLine(Color(0xFFFFFF8D).copy(alpha = fl), pt(mx(1.15f), 10.6f), pt(mx(2.25f), 13.35f), pd(0.18f), StrokeCap.Round)
        }

        // ── бамперы с мордочками ──
        val bumperColors = listOf(ColorPink, ColorYellow, ColorPurple, ColorGreen)
        engine.bumperPositions.forEachIndexed { idx, p ->
            val center = pt(p.x, p.y)
            val fl = engine.bumperFlash[idx]
            val bR = pd(engine.bumperR) * (1f + 0.12f * fl)
            val col = bumperColors[idx % bumperColors.size]
            drawCircle(Color.Black.copy(alpha = 0.40f), bR * 1.1f, Offset(center.x + pd(0.08f), center.y + pd(0.14f)))
            drawCircle(Color(0xFF1A0B3D), bR * 1.12f, center)
            drawCircle(Brush.linearGradient(listOf(Color(0xFFE1BEE7), Color(0xFF7E57C2)), Offset(center.x - bR, center.y - bR), Offset(center.x + bR, center.y + bR)), bR * 1.04f, center)
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 1f).let { Color(it.red * 0.35f + 0.65f, it.green * 0.35f + 0.65f, it.blue * 0.35f + 0.65f) }, col, Color(col.red * 0.55f, col.green * 0.55f, col.blue * 0.55f)),
                Offset(center.x - bR * 0.3f, center.y - bR * 0.35f), bR * 1.3f), bR * 0.88f, center)
            drawCircle(Color.White.copy(alpha = 0.45f), bR * 0.88f, center, style = Stroke(pd(0.04f)))
            drawOval(Color.White.copy(alpha = 0.5f), Offset(center.x - bR * 0.6f, center.y - bR * 0.78f), Size(bR * 0.7f, bR * 0.32f))
            val eyeDist = bR * 0.30f; val eyeY = center.y - bR * 0.10f; val eyeR = bR * 0.12f
            if (fl > 0.05f) {
                drawLine(ColorInk, Offset(center.x - eyeDist - eyeR, eyeY), Offset(center.x - eyeDist + eyeR, eyeY), 3f, StrokeCap.Round)
                drawLine(ColorInk, Offset(center.x + eyeDist - eyeR, eyeY), Offset(center.x + eyeDist + eyeR, eyeY), 3f, StrokeCap.Round)
                drawCircle(ColorInk, bR * 0.17f, Offset(center.x, center.y + bR * 0.28f))
                drawCircle(Color.White.copy(alpha = fl), bR * 1.4f, center, style = Stroke(pd(0.08f) * fl))
            } else {
                drawCircle(ColorInk, eyeR, Offset(center.x - eyeDist, eyeY)); drawCircle(ColorInk, eyeR, Offset(center.x + eyeDist, eyeY))
                drawCircle(Color.White, eyeR * 0.4f, Offset(center.x - eyeDist - eyeR * 0.3f, eyeY - eyeR * 0.3f))
                drawCircle(Color.White, eyeR * 0.4f, Offset(center.x + eyeDist - eyeR * 0.3f, eyeY - eyeR * 0.3f))
                drawArc(ColorInk, 0f, 180f, false, Offset(center.x - bR * 0.22f, center.y + bR * 0.08f), Size(bR * 0.44f, bR * 0.32f), style = Stroke(3f, cap = StrokeCap.Round))
            }
        }

        // ── лапки ──
        val (leftAngle, rightAngle) = engine.getFlipperAngles()
        val flipLength = pd(engine.flipLen)
        val flipThick = pd(engine.flipHalf * 2f)
        for (left in listOf(true, false)) {
            val pv = if (left) pt(engine.leftPivot.x, engine.leftPivot.y) else pt(engine.rightPivot.x, engine.rightPivot.y)
            val tip = if (left) Offset(pv.x + flipLength * cos(leftAngle), pv.y + flipLength * sin(leftAngle))
                      else Offset(pv.x - flipLength * cos(rightAngle), pv.y - flipLength * sin(rightAngle))
            val active = if (left) leftFlipperActive else rightFlipperActive
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(pv.x + 4f, pv.y + 7f), Offset(tip.x + 4f, tip.y + 7f), flipThick * 1.3f, StrokeCap.Round)
            drawLine(Color(0xFF1A0B3D), pv, tip, flipThick * 1.32f, StrokeCap.Round)
            drawLine(if (active) Color(0xFFFFD54F) else Color(0xFFFF8A65), pv, tip, flipThick, StrokeCap.Round)
            // блик вдоль лапки
            val nx = -(tip.y - pv.y) / flipLength; val ny = (tip.x - pv.x) / flipLength
            drawLine(Color.White.copy(alpha = 0.55f), Offset(pv.x + nx * flipThick * 0.2f * -1f, pv.y + ny * flipThick * 0.2f * -1f), Offset(tip.x + nx * flipThick * 0.2f * -1f, tip.y + ny * flipThick * 0.2f * -1f), flipThick * 0.16f, StrokeCap.Round)
            drawCircle(Color(0xFFE0E0E0), flipThick * 0.42f, pv)
            drawCircle(Color(0xFF616161), flipThick * 0.42f, pv, style = Stroke(flipThick * 0.1f))
            drawCircle(Color(0xFF616161), flipThick * 0.12f, pv)
        }

        // ── след и шарики ──
        trailPoints.forEachIndexed { tIdx, p ->
            val k = (tIdx + 1).toFloat() / trailPoints.size.toFloat()
            drawCircle(ballColor.copy(alpha = (k * 0.55f).coerceIn(0.05f, 0.55f)), (pd(engine.ballR) * 0.7f * k).coerceAtLeast(2f), pt(p.x, p.y))
        }
        val ballRpx = pd(engine.ballR)
        engine.getBalls().forEach { b ->
            val c = pt(b.px, b.py)
            val vis = if (b.captured > 0f) 0.55f else 1f
            val br = ballRpx * vis
            drawOval(Color.Black.copy(alpha = 0.40f), Offset(c.x - br * 0.9f + 3f, c.y + br * 0.55f + 3f), Size(br * 1.8f, br * 0.9f))
            drawCircle(Brush.radialGradient(
                listOf(Color.White, Color(ballColor.red * 0.4f + 0.6f, ballColor.green * 0.4f + 0.6f, ballColor.blue * 0.4f + 0.6f), ballColor, Color(ballColor.red * 0.4f, ballColor.green * 0.4f, ballColor.blue * 0.4f)),
                Offset(c.x - br * 0.35f, c.y - br * 0.4f), br * 1.5f), br, c)
            drawCircle(Color(0xFF1A0B3D), br, c, style = Stroke(2.5f))
            // «окно» отражения вращается вместе с шариком
            val ra = b.roll
            drawCircle(Color.White.copy(alpha = 0.75f), br * 0.14f, Offset(c.x - br * 0.38f * cos(ra * 0.4f), c.y - br * 0.42f))
            drawArc(Color.White.copy(alpha = 0.5f), 200f, 60f, false, Offset(c.x - br * 0.75f, c.y - br * 0.75f), Size(br * 1.5f, br * 1.5f), style = Stroke(br * 0.12f, cap = StrokeCap.Round))
        }

        // ── вспышки и салют ──
        bumperFlashes.forEach { flash ->
            val centerP = engine.bumperPositions.getOrNull(flash.id) ?: return@forEach
            val prog = ((now - flash.startTime) / 250f).coerceIn(0f, 1f)
            drawCircle(ColorYellow.copy(alpha = 1f - prog), pd(engine.bumperR) + prog * pd(0.8f), pt(centerP.x, centerP.y), style = Stroke(pd(0.08f)))
        }
        particles.forEach { p ->
            drawCircle(p.color.copy(alpha = 0.35f), 12.dp.toPx(), pt(p.x, p.y))
            drawCircle(p.color, 6.dp.toPx(), pt(p.x, p.y))
            drawCircle(Color.White, 2.dp.toPx(), pt(p.x, p.y))
        }
    }
}
