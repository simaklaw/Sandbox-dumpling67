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
                    Text(text = "⭐", fontSize = 24.sp)
                    Text(text = "🌟", fontSize = 28.sp)
                    Text(text = "⭐", fontSize = 24.sp)
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
                    .height(84.dp)
                    .padding(top = 6.dp),
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
                        Text(text = if (isLeft) "⬆️ 🐾" else "🐾 ⬆️", fontSize = 34.sp)
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
                    .height(76.dp)
                    .padding(top = 6.dp)
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
                            .size(72.dp)
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
                            .size(72.dp)
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
                            .size(72.dp)
                            .border(BorderStroke(3.dp, ColorInk), ShapeButton)
                            .background(ColorPink, ShapeButton)
                            .clip(ShapeButton)
                            .plasticineClickable(haptics = haptics) {
                                soundManager.play("whoosh")
                                score = 0
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
                            .size(72.dp)
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
    val fieldBgBrush = remember {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFCFEAFF), Color(0xFFFFD6F0))
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "bulbCycle")
    val bulbIndexFloat by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bulbStep"
    )
    val activeBulbIndex = bulbIndexFloat.toInt() % 12

    Canvas(modifier = modifier) {
        // Compute coordinate transform from 9x16 world to Canvas
        val worldW = engine.worldWidth
        val worldH = engine.worldHeight
        val scale = min(size.width / worldW, size.height / worldH)
        val offsetX = (size.width - worldW * scale) / 2f
        val offsetY = (size.height - worldH * scale) / 2f

        fun toPxX(x: Float): Float = offsetX + x * scale
        fun toPxY(y: Float): Float = offsetY + y * scale
        fun toPxDist(d: Float): Float = d * scale

        // 1. Draw Field Background with Caramel Gradient & Purple Cabinet Frame
        val fieldRectTop = toPxY(0f)
        val fieldRectLeft = toPxX(0f)
        val fieldRectWidth = toPxDist(worldW)
        val fieldRectHeight = toPxDist(worldH)

        // Purple Cabinet Shadow & Frame
        drawRoundRect(
            color = Color(0xFF7B1FA2),
            topLeft = Offset(fieldRectLeft - 8.dp.toPx(), fieldRectTop - 8.dp.toPx()),
            size = Size(fieldRectWidth + 16.dp.toPx(), fieldRectHeight + 16.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(36.dp.toPx(), 36.dp.toPx())
        )

        // Caramel Machine Playfield
        drawRoundRect(
            brush = fieldBgBrush,
            topLeft = Offset(fieldRectLeft, fieldRectTop),
            size = Size(fieldRectWidth, fieldRectHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(32.dp.toPx(), 32.dp.toPx())
        )

        // 12 Gentle Perimeter Bulbs along cabinet border (step <= 1Hz)
        val bulbPositions = listOf(
            Offset(fieldRectLeft, fieldRectTop + fieldRectHeight * 0.15f),
            Offset(fieldRectLeft, fieldRectTop + fieldRectHeight * 0.35f),
            Offset(fieldRectLeft, fieldRectTop + fieldRectHeight * 0.55f),
            Offset(fieldRectLeft, fieldRectTop + fieldRectHeight * 0.75f),
            Offset(fieldRectLeft + fieldRectWidth * 0.25f, fieldRectTop),
            Offset(fieldRectLeft + fieldRectWidth * 0.50f, fieldRectTop - 4.dp.toPx()),
            Offset(fieldRectLeft + fieldRectWidth * 0.75f, fieldRectTop),
            Offset(fieldRectLeft + fieldRectWidth, fieldRectTop + fieldRectHeight * 0.15f),
            Offset(fieldRectLeft + fieldRectWidth, fieldRectTop + fieldRectHeight * 0.35f),
            Offset(fieldRectLeft + fieldRectWidth, fieldRectTop + fieldRectHeight * 0.55f),
            Offset(fieldRectLeft + fieldRectWidth, fieldRectTop + fieldRectHeight * 0.75f),
            Offset(fieldRectLeft + fieldRectWidth * 0.50f, fieldRectTop + fieldRectHeight)
        )
        bulbPositions.forEachIndexed { bIdx, pos ->
            val isActive = bIdx == activeBulbIndex
            val bulbColor = if (isActive) Color(0xFFFFEB3B) else Color(0xFFFFCC80)
            drawCircle(color = bulbColor, radius = 5.dp.toPx(), center = pos)
            drawCircle(color = ColorInk, radius = 5.dp.toPx(), center = pos, style = Stroke(width = 1.5.dp.toPx()))
            if (isActive) {
                drawCircle(color = ColorYellow.copy(alpha = 0.4f), radius = 9.dp.toPx(), center = pos)
            }
        }

        // 2. Draw Top Arch & Side Walls with thick ColorInk
        val wallStroke = 6.dp.toPx()
        val wallColor = ColorInk

        val archPath = Path().apply {
            // Left wall (x=0, y=12 up to y=3)
            moveTo(toPxX(0f), toPxY(12f))
            lineTo(toPxX(0f), toPxY(3f))
            // Arch semicircle: from (0,3) to (9,3) via center (4.5, 3)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    left = toPxX(0f),
                    top = toPxY(-1.5f),
                    right = toPxX(9f),
                    bottom = toPxY(7.5f)
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            // Right wall (x=9, y=3 down to y=12)
            lineTo(toPxX(9f), toPxY(12f))
        }

        drawPath(
            path = archPath,
            color = wallColor,
            style = Stroke(width = wallStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 3. Draw Bottom Slanted Chutes to Flippers
        // Left chute: (0, 12) -> (2.2, 14)
        drawLine(
            color = wallColor,
            start = Offset(toPxX(0f), toPxY(12f)),
            end = Offset(toPxX(2.2f), toPxY(14f)),
            strokeWidth = wallStroke,
            cap = StrokeCap.Round
        )

        // Right chute: (9, 12) -> (6.8, 14)
        drawLine(
            color = wallColor,
            start = Offset(toPxX(9f), toPxY(12f)),
            end = Offset(toPxX(6.8f), toPxY(14f)),
            strokeWidth = wallStroke,
            cap = StrokeCap.Round
        )

        // 4. Draw Slingshots (Triangles above flippers)
        val leftSlingshot = Path().apply {
            moveTo(toPxX(1.2f), toPxY(10.0f))
            lineTo(toPxX(2.2f), toPxY(11.8f))
            lineTo(toPxX(1.2f), toPxY(12.0f))
            close()
        }
        drawPath(leftSlingshot, color = Color(0xFFFFCC80))
        drawPath(leftSlingshot, color = wallColor, style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))

        val rightSlingshot = Path().apply {
            moveTo(toPxX(7.8f), toPxY(10.0f))
            lineTo(toPxX(7.8f), toPxY(12.0f))
            lineTo(toPxX(6.8f), toPxY(11.8f))
            close()
        }
        drawPath(rightSlingshot, color = Color(0xFFFFCC80))
        drawPath(rightSlingshot, color = wallColor, style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))

        // 5. Draw Star Targets (3 sensors in top arch)
        val starPos = listOf(
            Offset(toPxX(3.0f), toPxY(2.0f)),
            Offset(toPxX(4.5f), toPxY(1.2f)),
            Offset(toPxX(6.0f), toPxY(2.0f))
        )
        starPos.forEachIndexed { idx, center ->
            val isLit = engine.starLitStates[idx]
            drawCircle(
                color = if (isLit) ColorYellow else ColorWhite,
                radius = toPxDist(0.45f),
                center = center
            )
            drawCircle(
                color = wallColor,
                radius = toPxDist(0.45f),
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )
            // Inner decorative star ring
            drawCircle(
                color = if (isLit) ColorOrange else Color.LightGray,
                radius = toPxDist(0.25f),
                center = center
            )
        }

        // 6. Draw Bumpers with Friendly Faces (4 round bumpers: (3, 5.5), (6, 5.5), (4.5, 7.5), (4.5, 4))
        val bumperColors = listOf(ColorPink, ColorYellow, ColorPurple, ColorGreen)
        val bumperCenters = listOf(
            Offset(toPxX(3.0f), toPxY(5.5f)),
            Offset(toPxX(6.0f), toPxY(5.5f)),
            Offset(toPxX(4.5f), toPxY(7.5f)),
            Offset(toPxX(4.5f), toPxY(4.0f))
        )
        val bumperR = toPxDist(0.7f)

        bumperCenters.forEachIndexed { idx, center ->
            // Outer body
            drawCircle(
                color = bumperColors[idx % bumperColors.size],
                radius = bumperR,
                center = center
            )
            drawCircle(
                color = wallColor,
                radius = bumperR,
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )
            // Inner plasticine ring highlight
            drawCircle(
                color = ColorWhite.copy(alpha = 0.85f),
                radius = bumperR * 0.45f,
                center = center
            )

            // Bumper Face (cute eyes and smiling mouth; reacts to hit)
            val isBumperFlashed = bumperFlashes.any { it.id == idx }
            val eyeDist = bumperR * 0.32f
            val eyeY = center.y - bumperR * 0.12f
            val eyeR = bumperR * 0.12f
            if (isBumperFlashed) {
                // Happy squinting lines
                drawLine(ColorInk, Offset(center.x - eyeDist - eyeR, eyeY), Offset(center.x - eyeDist + eyeR, eyeY), strokeWidth = 3f)
                drawLine(ColorInk, Offset(center.x + eyeDist - eyeR, eyeY), Offset(center.x + eyeDist + eyeR, eyeY), strokeWidth = 3f)
                // Surprised open mouth 'O'
                drawCircle(ColorInk, radius = bumperR * 0.18f, center = Offset(center.x, center.y + bumperR * 0.28f))
            } else {
                // Two dot eyes
                drawCircle(ColorInk, radius = eyeR, center = Offset(center.x - eyeDist, eyeY))
                drawCircle(ColorInk, radius = eyeR, center = Offset(center.x + eyeDist, eyeY))
                // Cute smiling mouth arc
                drawArc(
                    color = ColorInk,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(center.x - bumperR * 0.22f, center.y + bumperR * 0.10f),
                    size = Size(bumperR * 0.44f, bumperR * 0.32f),
                    style = Stroke(width = 3f, cap = StrokeCap.Round)
                )
            }
        }

        // Flash rings on bumpers
        bumperFlashes.forEach { flash ->
            val center = bumperCenters.getOrNull(flash.id) ?: Offset.Zero
            val elapsed = System.currentTimeMillis() - flash.startTime
            val prog = (elapsed / 250f).coerceIn(0f, 1f)
            val ringRadius = bumperR + prog * toPxDist(0.8f)
            drawCircle(
                color = ColorYellow.copy(alpha = 1f - prog),
                radius = ringRadius,
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )
        }

        // 7. Draw Flippers with Pivots
        val (leftAngle, rightAngle) = engine.getFlipperAngles()
        val flipLength = toPxDist(1.9f)
        val flipThick = toPxDist(0.44f)

        // Left flipper
        val lPivot = Offset(toPxX(2.2f), toPxY(14.0f))
        val lTip = Offset(
            lPivot.x + flipLength * cos(leftAngle),
            lPivot.y + flipLength * sin(leftAngle)
        )
        drawLine(
            color = if (leftFlipperActive) Color(0xFFFFD54F) else ColorOrange,
            start = lPivot,
            end = lTip,
            strokeWidth = flipThick,
            cap = StrokeCap.Round
        )
        drawLine(
            color = wallColor,
            start = lPivot,
            end = lTip,
            strokeWidth = flipThick,
            cap = StrokeCap.Round
        )
        // Left pivot circle
        drawCircle(color = wallColor, radius = flipThick * 0.5f, center = lPivot)

        // Right flipper
        val rPivot = Offset(toPxX(6.8f), toPxY(14.0f))
        val rTip = Offset(
            rPivot.x - flipLength * cos(rightAngle),
            rPivot.y - flipLength * sin(rightAngle)
        )
        drawLine(
            color = if (rightFlipperActive) Color(0xFFFFD54F) else ColorOrange,
            start = rPivot,
            end = rTip,
            strokeWidth = flipThick,
            cap = StrokeCap.Round
        )
        drawLine(
            color = wallColor,
            start = rPivot,
            end = rTip,
            strokeWidth = flipThick,
            cap = StrokeCap.Round
        )
        // Right pivot circle
        drawCircle(color = wallColor, radius = flipThick * 0.5f, center = rPivot)

        // 8. Draw Ball Trailing Rainbow Dots
        trailPoints.forEachIndexed { tIdx, pt ->
            val trailAlpha = ((tIdx + 1).toFloat() / trailPoints.size.toFloat() * 0.75f).coerceIn(0.1f, 0.75f)
            val trailR = toPxDist(0.18f) * ((tIdx + 1).toFloat() / trailPoints.size.toFloat())
            drawCircle(
                color = ballColor.copy(alpha = trailAlpha),
                radius = trailR.coerceAtLeast(2f),
                center = Offset(toPxX(pt.x), toPxY(pt.y))
            )
        }

        // 9. Draw Balls (filled with ballColor, gloss highlight and ink outline)
        val balls = engine.getBalls()
        val ballR = toPxDist(0.4f)
        balls.forEach { b ->
            val bPos = b.position
            val cx = toPxX(bPos.x)
            val cy = toPxY(bPos.y)

            // Ball colored body
            drawCircle(
                color = ballColor,
                radius = ballR,
                center = Offset(cx, cy)
            )
            // Ball thick outline
            drawCircle(
                color = wallColor,
                radius = ballR,
                center = Offset(cx, cy),
                style = Stroke(width = 3.dp.toPx())
            )
            // Gloss highlight dot
            drawCircle(
                color = ColorWhite.copy(alpha = 0.9f),
                radius = ballR * 0.35f,
                center = Offset(cx - ballR * 0.3f, cy - ballR * 0.3f)
            )
        }

        // 10. Draw Sparkle Celebration Particles
        particles.forEach { p ->
            val px = toPxX(p.x)
            val py = toPxY(p.y)
            drawCircle(
                color = p.color,
                radius = 7.dp.toPx(),
                center = Offset(px, py)
            )
        }
    }
}
