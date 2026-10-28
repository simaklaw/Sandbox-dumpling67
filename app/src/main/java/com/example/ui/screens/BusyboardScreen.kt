package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.screens.Busyboard.*
import com.example.shared.*
import com.example.shared.scene.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

private const val LAST_PAGE = 4

@Composable
fun BusyboardScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val soundHelper = remember(context, soundPlayer, soundManager) {
        BusyboardSoundHelper(context, soundPlayer, soundManager)
    }
    val haptics = rememberHaptics()
    val coroutineScope = rememberCoroutineScope()
    val ageProfile = LocalAgeProfile.current
    val topInset = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    LaunchedEffect(Unit) {
        soundManager.preloadRaw(listOf("sm_note1", "sm_pop", "sm_thud", "sm_tada", "sm_honk", "sm_party", "sm_hic", "sm_bubble",
            "animal_dog_1", "animal_dog_2", "animal_cat_1", "animal_cat_2", "animal_cow_1", "animal_cow_2", "animal_frog_1", "animal_frog_2"))
    }

    // SharedPreferences for First Launch
    val prefs = remember { context.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE) }
    var isFirstLaunch by remember { mutableStateOf(prefs.getBoolean("first_launch_busyboard", true)) }

    // Active Board Page (0: Buttons/Levers, 1: Lights/Knob, 2: Peekaboo)
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var pageStayStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showNextArrowPulse by remember { mutableStateOf(false) }

    // Hidden Action Counter for "Молодец" Balloon (every 6 actions in 2yo, 10 in 3-5yo)
    var actionCount by remember { mutableIntStateOf(0) }
    var activeBalloonY by remember { mutableFloatStateOf(-1f) }
    var isBalloonPopped by remember { mutableStateOf(false) }

    fun registerAction() {
        actionCount++
        if (isFirstLaunch) {
            isFirstLaunch = false
            prefs.edit().putBoolean("first_launch_busyboard", false).apply()
        }
        val threshold = if (ageProfile.isToddler2) 6 else 10
        if (actionCount >= threshold && activeBalloonY < 0f) {
            actionCount = 0
            activeBalloonY = 1600f
            isBalloonPopped = false
            soundHelper.playPraise()
        }
    }

    // Global Particles (Confetti, Stars, Sparks)
    val particles = remember { mutableStateListOf<BusyParticle>() }

    // Inactivity on single board monitor (15s -> arrow pulse)
    LaunchedEffect(currentPageIndex, pageStayStartTime) {
        showNextArrowPulse = false
        delay(15000)
        showNextArrowPulse = true
    }

    // Main Simulation Loop (Particles & Rising Balloon)
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (isActive) {
            withFrameNanos { nowNanos ->
                if (lastNanos == 0L) {
                    lastNanos = nowNanos
                    return@withFrameNanos
                }
                val dt = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
                lastNanos = nowNanos

                // 1. Update Particles
                for (i in particles.indices.reversed()) {
                    val p = particles[i]
                    p.ageSec += dt
                    p.vy += 450f * dt
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.alpha = (1.0f - p.ageSec / p.lifeSec).coerceIn(0f, 1f)
                    if (p.ageSec >= p.lifeSec) {
                        particles.removeAt(i)
                    }
                }

                // 2. Update Rising "Молодец" Balloon
                if (activeBalloonY >= 0f && !isBalloonPopped) {
                    activeBalloonY -= 140f * dt
                    if (activeBalloonY < -120f) {
                        activeBalloonY = -1f
                    }
                }
            }
        }
    }

    val decorController = remember { DecorController() }
    val busyDecors = remember {
        listOf(
            DecorSpec(DecorKind.GEAR, 0.10f, 0.08f, 52f, id = "gear_corner"),
            DecorSpec(DecorKind.BULB, 0.90f, 0.08f, 48f, id = "bulb_corner"),
            DecorSpec(DecorKind.MOUSE, 0.92f, 0.88f, 52f, id = "corner_mouse")
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    if (abs(dragAmount.x) > 40f && abs(dragAmount.y) < 30f) {
                        if (dragAmount.x < 0 && currentPageIndex < LAST_PAGE) {
                            soundHelper.playWhooshSoft()
                            currentPageIndex++
                            pageStayStartTime = System.currentTimeMillis()
                        } else if (dragAmount.x > 0 && currentPageIndex > 0) {
                            soundHelper.playWhooshSoft()
                            currentPageIndex--
                            pageStayStartTime = System.currentTimeMillis()
                        }
                    }
                }
            }
    ) {
        SceneBackground(theme = SceneTheme.ROOM)
        DecorLayer(items = busyDecors, controller = decorController)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Spacer for HomeButton (76 dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Spacer(modifier = Modifier.size(76.dp))
            }

            // Main Active Board (Animated Sliding Transition 250 ms)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = currentPageIndex,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(animationSpec = tween(250)) { it } + fadeIn(animationSpec = tween(250)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(250)) { -it } + fadeOut(animationSpec = tween(150)))
                        } else {
                            (slideInHorizontally(animationSpec = tween(250)) { -it } + fadeIn(animationSpec = tween(250)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(250)) { it } + fadeOut(animationSpec = tween(150)))
                        }
                    },
                    label = "busyboardPages"
                ) { pageIndex ->
                    when (pageIndex) {
                        0 -> BoardButtonsAndLevers(
                            soundHelper = soundHelper,
                            haptics = haptics,
                            particles = particles,
                            onAction = { registerAction() }
                        )
                        1 -> BoardLightsAndMechanics(
                            soundHelper = soundHelper,
                            haptics = haptics,
                            onAction = { registerAction() }
                        )
                        2 -> BoardPeekabooAndBell(
                            soundHelper = soundHelper,
                            haptics = haptics,
                            particles = particles,
                            onAction = { registerAction() }
                        )
                        3 -> BoardMusic(
                            soundManager = soundManager,
                            haptics = haptics,
                            particles = particles,
                            topInset = topInset,
                            onAction = { registerAction() }
                        )
                        4 -> BoardToys(
                            soundManager = soundManager,
                            soundHelper = soundHelper,
                            haptics = haptics,
                            particles = particles,
                            topInset = topInset,
                            onAction = { registerAction() }
                        )
                    }
                }
            }

            // Bottom Navigation Row: Arrow [◀], 3 Dots [● ○ ○], Arrow [▶] (88 dp buttons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Arrow Button [◀] (88 dp)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .border(BorderStroke(3.dp, ColorInk), ShapeButton)
                        .background(if (currentPageIndex > 0) ColorOrange else ColorBgSoft, ShapeButton)
                        .clip(ShapeButton)
                        .plasticineClickable(haptics = haptics) {
                            if (currentPageIndex > 0) {
                                soundHelper.playWhooshSoft()
                                currentPageIndex--
                                pageStayStartTime = System.currentTimeMillis()
                            }
                        }
                        .testTag("busyboard_prev_btn")
                ) {
                    Text(
                        text = "◀",
                        fontSize = 32.sp,
                        color = if (currentPageIndex > 0) ColorInk else ColorInk.copy(alpha = 0.3f),
                        fontWeight = FontWeight.Bold
                    )
                }

                // 3 Page Dots with Icons (🔘, 💡, 🏠)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(ColorWhite, ShapeCard)
                        .border(BorderStroke(2.5.dp, ColorInk), ShapeCard)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    BusyboardPage.values().forEachIndexed { idx, page ->
                        val isSelected = currentPageIndex == idx
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(if (isSelected) 36.dp else 24.dp)
                                .border(
                                    BorderStroke(if (isSelected) 3.dp else 1.5.dp, ColorInk),
                                    CircleShape
                                )
                                .background(if (isSelected) ColorYellow else ColorBgSoft, CircleShape)
                        ) {
                            if (isSelected) {
                                Text(text = page.iconEmoji, fontSize = 18.sp)
                            }
                        }
                    }
                }

                // Right Arrow Button [▶] (88 dp) with Pulse if inactive
                val nextPulseScale by animateFloatAsState(
                    targetValue = if (showNextArrowPulse && currentPageIndex < LAST_PAGE) 1.15f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                    label = "arrowPulse"
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .scale(nextPulseScale)
                        .size(80.dp)
                        .border(BorderStroke(3.dp, ColorInk), ShapeButton)
                        .background(if (currentPageIndex < LAST_PAGE) ColorOrange else ColorBgSoft, ShapeButton)
                        .clip(ShapeButton)
                        .plasticineClickable(haptics = haptics) {
                            if (currentPageIndex < LAST_PAGE) {
                                soundHelper.playWhooshSoft()
                                currentPageIndex++
                                pageStayStartTime = System.currentTimeMillis()
                            }
                        }
                        .testTag("busyboard_next_btn")
                ) {
                    Text(
                        text = "▶",
                        fontSize = 32.sp,
                        color = if (currentPageIndex < LAST_PAGE) ColorInk else ColorInk.copy(alpha = 0.3f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Слой частиц и шарика. ВАЖНО: на нём НЕТ pointerInput — перекрывающийся «брат» с обработчиком
        // касаний блокирует всё, что лежит под ним (из-за этого доска не реагировала на нажатия).
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            // Draw Particles
            particles.forEach { p ->
                if (p.isStar) {
                    drawStarGraphic(center = Offset(p.x, p.y), radius = p.size, color = p.color, alpha = p.alpha)
                } else {
                    drawCircle(
                        color = p.color.copy(alpha = p.alpha),
                        radius = p.size / 2f,
                        center = Offset(p.x, p.y)
                    )
                }
            }

            // Draw Rising "Молодец!" Balloon
            if (activeBalloonY >= 0f && !isBalloonPopped) {
                val bCenter = Offset(size.width / 2f, activeBalloonY)
                val bR = 42.dp.toPx()

                // Balloon String
                drawLine(
                    color = ColorInk,
                    start = Offset(bCenter.x, bCenter.y + bR),
                    end = Offset(bCenter.x + sin(activeBalloonY * 0.05f) * 12f, bCenter.y + bR + 45.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )

                // Balloon Body
                drawCircle(color = ColorPink, radius = bR, center = bCenter)
                drawCircle(color = ColorInk, radius = bR, center = bCenter, style = Stroke(width = 3.dp.toPx()))

                // Happy Smile on Balloon
                drawCircle(color = ColorInk, radius = 4.dp.toPx(), center = Offset(bCenter.x - 12.dp.toPx(), bCenter.y - 6.dp.toPx()))
                drawCircle(color = ColorInk, radius = 4.dp.toPx(), center = Offset(bCenter.x + 12.dp.toPx(), bCenter.y - 6.dp.toPx()))
                val smilePath = Path().apply {
                    moveTo(bCenter.x - 12.dp.toPx(), bCenter.y + 8.dp.toPx())
                    quadraticBezierTo(bCenter.x, bCenter.y + 20.dp.toPx(), bCenter.x + 12.dp.toPx(), bCenter.y + 8.dp.toPx())
                }
                drawPath(smilePath, color = ColorInk, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }

        // Шарик «Молодец!»: нажимаемая область только вокруг самого шарика
        if (activeBalloonY >= 0f && !isBalloonPopped) {
            val density = LocalDensity.current
            val bR = with(density) { 60.dp.toPx() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val cxPx = with(density) { maxWidth.toPx() } / 2f
                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(density) { (cxPx - bR).toDp() },
                                y = with(density) { (activeBalloonY - bR).toDp() }
                            )
                            .size(120.dp)
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    isBalloonPopped = true
                                    soundHelper.playPop()
                                    haptics.vibrate(25)
                                    repeat(20) {
                                        val ang = Random.nextFloat() * 2f * PI.toFloat()
                                        val spd = Random.nextFloat() * 250f + 80f
                                        particles.add(
                                            BusyParticle(
                                                x = cxPx, y = activeBalloonY,
                                                vx = cos(ang) * spd, vy = sin(ang) * spd,
                                                color = listOf(ColorPink, ColorYellow, ColorGreen, ColorOrange, ColorBlue).random(),
                                                isStar = true, size = 14f
                                            )
                                        )
                                    }
                                    activeBalloonY = -1f
                                }
                            }
                    )
                }
            }
        }

        // Hint Hand Gesture
        if (isFirstLaunch) {
            HintHand(
                from = Offset(60f, 180f),
                to = Offset(60f, 290f),
                mode = "drag",
                delayMs = 1200L
            )
        }
    }
}

/**
 * 🕹️ Board 1: Buttons & Levers.
 * 2 Vertical Levers + 4 Big Depressible Buttons (Star, Duck, Frog, Fireworks).
 */
@Composable
private fun BoardButtonsAndLevers(
    soundHelper: BusyboardSoundHelper,
    haptics: Haptics,
    particles: MutableList<BusyParticle>,
    onAction: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // Levers Pull Distance (0..160 dp)
    var leftLeverPull by remember { mutableFloatStateOf(0f) }
    var rightLeverPull by remember { mutableFloatStateOf(0f) }

    // Train Animation (Right Lever)
    var isTrainRunning by remember { mutableStateOf(false) }
    val trainPosX = remember { Animatable(-300f) }

    val board1Decors = remember {
        listOf(
            DecorSpec(DecorKind.GEAR, 0.10f, 0.08f, 58f),
            DecorSpec(DecorKind.GEAR, 0.90f, 0.08f, 62f),
            DecorSpec(DecorKind.GEAR, 0.92f, 0.50f, 56f),
            DecorSpec(DecorKind.CLOUD, 0.16f, 0.40f, 60f),
            DecorSpec(DecorKind.CLOUD, 0.84f, 0.40f, 60f)
        )
    }

    Surface(
        shape = ShapeCard,
        color = Color(0xFF6EC6FF),
        border = BorderStroke(3.5.dp, ColorInk),
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            DecorLayer(items = board1Decors)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // Top Row: 2 Heavy Mechanical Levers (Left: ⚡ Confetti, Right: 🚂 Train)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Lever (⚡ Confetti Rain)
                MechanicalLever(
                    iconEmoji = "⚡",
                    pullDistance = leftLeverPull,
                    onPull = { delta ->
                        leftLeverPull = (leftLeverPull + delta).coerceIn(0f, 150f)
                        soundHelper.playLeverHum()
                    },
                    onRelease = {
                        if (leftLeverPull > 110f) {
                            // Activated!
                            soundHelper.playLeverClack()
                            soundHelper.playConfettiRain()
                            haptics.vibrate(25)
                            onAction()

                            // Spawn 25 Confetti Particles
                            repeat(25) {
                                val ang = Random.nextFloat() * 2f * PI.toFloat()
                                val spd = Random.nextFloat() * 200f + 60f
                                particles.add(
                                    BusyParticle(
                                        x = Random.nextFloat() * 800f + 100f,
                                        y = 300f,
                                        vx = cos(ang) * spd,
                                        vy = -Random.nextFloat() * 250f - 50f,
                                        color = listOf(ColorPink, ColorYellow, ColorGreen, ColorOrange, ColorBlue).random(),
                                        isStar = true,
                                        size = 14f,
                                        lifeSec = 1.5f
                                    )
                                )
                            }
                        }
                        leftLeverPull = 0f
                    }
                )

                // Right Lever (🚂 Cartoon Steam Train)
                MechanicalLever(
                    iconEmoji = "🚂",
                    pullDistance = rightLeverPull,
                    onPull = { delta ->
                        rightLeverPull = (rightLeverPull + delta).coerceIn(0f, 150f)
                        soundHelper.playLeverHum()
                    },
                    onRelease = {
                        if (rightLeverPull > 110f && !isTrainRunning) {
                            soundHelper.playLeverClack()
                            soundHelper.playTrainToot()
                            haptics.vibrate(25)
                            onAction()

                            isTrainRunning = true
                            coroutineScope.launch {
                                trainPosX.snapTo(-200f)
                                trainPosX.animateTo(1200f, tween(2000, easing = LinearEasing))
                                isTrainRunning = false
                            }
                        }
                        rightLeverPull = 0f
                    }
                )
            }

            // Animated Train Crossing Banner
            if (isTrainRunning) {
                Box(
                    modifier = Modifier
                        .offset(x = trainPosX.value.dp, y = (-20).dp)
                        .size(140.dp, 60.dp)
                ) {
                    Text(text = "🚂💨💨", fontSize = 42.sp)
                }
            }

            // Bottom 2x2 Grid of 4 Round Depressible Buttons (96 dp each)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Row 1: ⭐ Звезда & 🦆 Утка
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DepressibleButton(
                        emoji = "⭐",
                        color = ColorYellow,
                        tag = "busy_btn_star",
                        haptics = haptics
                    ) {
                        soundHelper.playStarBurst()
                        haptics.vibrateShort()
                        onAction()
                        repeat(22) {
                            val ang = Random.nextFloat() * 2f * PI.toFloat()
                            val spd = Random.nextFloat() * 220f + 70f
                            particles.add(
                                BusyParticle(
                                    x = 260f,
                                    y = 900f,
                                    vx = cos(ang) * spd,
                                    vy = sin(ang) * spd,
                                    color = ColorYellow,
                                    isStar = true,
                                    size = 16f,
                                    lifeSec = 1.2f
                                )
                            )
                        }
                    }

                    DepressibleButton(
                        emoji = "🦆",
                        color = ColorPink,
                        tag = "busy_btn_duck",
                        haptics = haptics
                    ) {
                        soundHelper.playQuack()
                        haptics.vibrateShort()
                        onAction()
                        repeat(8) {
                            particles.add(
                                BusyParticle(
                                    x = 650f,
                                    y = 900f,
                                    vx = -Random.nextFloat() * 200f - 50f,
                                    vy = -Random.nextFloat() * 300f - 100f,
                                    color = ColorYellow,
                                    size = 18f,
                                    lifeSec = 1.4f
                                )
                            )
                        }
                    }
                }

                // Row 2: 🐸 Лягушка & 🎆 Салют
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DepressibleButton(
                        emoji = "🐸",
                        color = ColorGreen,
                        tag = "busy_btn_frog",
                        haptics = haptics
                    ) {
                        soundHelper.playFrogBoing()
                        haptics.vibrateShort()
                        onAction()
                        repeat(15) {
                            val ang = Random.nextFloat() * 2f * PI.toFloat()
                            val spd = Random.nextFloat() * 180f + 60f
                            particles.add(
                                BusyParticle(
                                    x = 260f,
                                    y = 1200f,
                                    vx = cos(ang) * spd,
                                    vy = sin(ang) * spd - 60f,
                                    color = ColorGreen,
                                    size = 14f,
                                    lifeSec = 1.3f
                                )
                            )
                        }
                    }

                    DepressibleButton(
                        emoji = "🎆",
                        color = ColorOrange,
                        tag = "busy_btn_firework",
                        haptics = haptics
                    ) {
                        soundHelper.playFirework()
                        haptics.vibrate(25)
                        onAction()
                        repeat(25) {
                            val ang = Random.nextFloat() * 2f * PI.toFloat()
                            val spd = Random.nextFloat() * 260f + 90f
                            particles.add(
                                BusyParticle(
                                    x = 650f,
                                    y = 1200f,
                                    vx = cos(ang) * spd,
                                    vy = sin(ang) * spd - 50f,
                                    color = listOf(ColorPink, ColorYellow, ColorBlue, ColorOrange).random(),
                                    isStar = true,
                                    size = 15f,
                                    lifeSec = 1.8f
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
}

/**
 * 💡 Board 2: Lights & Mechanics.
 * 4 Toggle Switches with 4 Bulbs + 1 Large Rotary Gear/Knob.
 */
@Composable
private fun BoardLightsAndMechanics(
    soundHelper: BusyboardSoundHelper,
    haptics: Haptics,
    onAction: () -> Unit
) {
    // 4 Toggle Switches States (Red, Yellow, Green, Blue)
    var switchRed by remember { mutableStateOf(false) }
    var switchYellow by remember { mutableStateOf(false) }
    var switchGreen by remember { mutableStateOf(false) }
    var switchBlue by remember { mutableStateOf(false) }

    val allLightsOn = switchRed && switchYellow && switchGreen && switchBlue

    // Chaser Light Index when all 4 are on (smooth cycling at max 2 changes/sec)
    var chaserIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(allLightsOn) {
        if (allLightsOn) {
            soundHelper.playJingleLights()
            while (true) {
                delay(500)
                chaserIndex = (chaserIndex + 1) % 4
            }
        }
    }

    // Rotary Gear Rotation Angle (changes background hue)
    var knobAngleDeg by remember { mutableFloatStateOf(0f) }
    var lastKnobClickAngle by remember { mutableFloatStateOf(0f) }

    val board2Decors = remember {
        listOf(
            DecorSpec(DecorKind.SUN, 0.86f, 0.10f, 60f, reaction = DecorReaction.SMILE),
            DecorSpec(DecorKind.STAR, 0.14f, 0.10f, 52f),
            DecorSpec(DecorKind.STAR, 0.28f, 0.15f, 44f),
            DecorSpec(DecorKind.STAR, 0.72f, 0.15f, 44f),
            DecorSpec(DecorKind.BULB, 0.50f, 0.08f, 48f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
            .clip(ShapeCard)
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF1E2A6B), Color(0xFF4F5DB8))))
            .border(BorderStroke(3.5.dp, ColorInk), ShapeCard)
    ) {
        DecorLayer(items = board2Decors)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 4 Bulbs and 4 Toggle Switches Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Red Bulb & Switch
                BulbAndSwitchColumn(
                    color = Color(0xFFFF6B6B),
                    isOn = switchRed,
                    isChasing = allLightsOn && chaserIndex == 0,
                    haptics = haptics,
                    onToggle = {
                        switchRed = !switchRed
                        if (switchRed) soundHelper.playSwitchOn() else soundHelper.playSwitchOff()
                        onAction()
                    }
                )

                // 2. Yellow Bulb & Switch
                BulbAndSwitchColumn(
                    color = Color(0xFFFFD93D),
                    isOn = switchYellow,
                    isChasing = allLightsOn && chaserIndex == 1,
                    haptics = haptics,
                    onToggle = {
                        switchYellow = !switchYellow
                        if (switchYellow) soundHelper.playSwitchOn() else soundHelper.playSwitchOff()
                        onAction()
                    }
                )

                // 3. Green Bulb & Switch
                BulbAndSwitchColumn(
                    color = Color(0xFF5CCB6E),
                    isOn = switchGreen,
                    isChasing = allLightsOn && chaserIndex == 2,
                    haptics = haptics,
                    onToggle = {
                        switchGreen = !switchGreen
                        if (switchGreen) soundHelper.playSwitchOn() else soundHelper.playSwitchOff()
                        onAction()
                    }
                )

                // 4. Blue Bulb & Switch
                BulbAndSwitchColumn(
                    color = Color(0xFF4A9BFF),
                    isOn = switchBlue,
                    isChasing = allLightsOn && chaserIndex == 3,
                    haptics = haptics,
                    onToggle = {
                        switchBlue = !switchBlue
                        if (switchBlue) soundHelper.playSwitchOn() else soundHelper.playSwitchOff()
                        onAction()
                    }
                )
            }

            // Large Rotary Gear / Knob (130 dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .padding(bottom = 20.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaAngle = dragAmount.x * 0.8f - dragAmount.y * 0.8f
                            knobAngleDeg += deltaAngle

                            if (abs(knobAngleDeg - lastKnobClickAngle) >= 30f) {
                                lastKnobClickAngle = knobAngleDeg
                                soundHelper.playKnobTick()
                                haptics.vibrate(8)
                                onAction()
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = 65.dp.toPx()
                    val inkStroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

                    rotate(degrees = knobAngleDeg, pivot = c) {
                        // Gear Teeth (8 teeth around circle)
                        for (i in 0 until 8) {
                            val toothAngle = i * 45f
                            rotate(degrees = toothAngle, pivot = c) {
                                drawRoundRect(
                                    color = Color(0xFFFFB74D),
                                    topLeft = Offset(c.x - 14.dp.toPx(), c.y - r - 12.dp.toPx()),
                                    size = Size(28.dp.toPx(), 24.dp.toPx()),
                                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                )
                                drawRoundRect(
                                    color = ColorInk,
                                    topLeft = Offset(c.x - 14.dp.toPx(), c.y - r - 12.dp.toPx()),
                                    size = Size(28.dp.toPx(), 24.dp.toPx()),
                                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                    style = inkStroke
                                )
                            }
                        }

                        // Gear Central Body
                        drawCircle(color = Color(0xFFFFD54F), radius = r, center = c)
                        drawCircle(color = ColorInk, radius = r, center = c, style = inkStroke)

                        // Center Knob Grip
                        drawCircle(color = Color(0xFFFF8F00), radius = r * 0.45f, center = c)
                        drawCircle(color = ColorInk, radius = r * 0.45f, center = c, style = Stroke(width = 2.5.dp.toPx()))

                        // Indicator Notch
                        drawLine(
                            color = ColorWhite,
                            start = c,
                            end = Offset(c.x, c.y - r * 0.75f),
                            strokeWidth = 6.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = ColorInk,
                            start = c,
                            end = Offset(c.x, c.y - r * 0.75f),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

/**
 * 🏠 Board 3: Peekaboo & Bell.
 * 3 Houses with Swinging Doors and Animals + 1 Large Pendulum Bell.
 */
@Composable
private fun BoardPeekabooAndBell(
    soundHelper: BusyboardSoundHelper,
    haptics: Haptics,
    particles: MutableList<BusyParticle>,
    onAction: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // 3 Houses States
    var house1Animal by remember { mutableStateOf<PeekabooAnimal?>(null) }
    var house2Animal by remember { mutableStateOf<PeekabooAnimal?>(null) }
    var house3Animal by remember { mutableStateOf<PeekabooAnimal?>(null) }

    val house1DoorAngle = remember { Animatable(0f) }
    val house2DoorAngle = remember { Animatable(0f) }
    val house3DoorAngle = remember { Animatable(0f) }

    // Bell Pendulum Swing Angle
    val bellSwing = remember { Animatable(0f) }

    val board3Decors = remember {
        listOf(
            DecorSpec(DecorKind.CLOUD, 0.16f, 0.08f, 68f),
            DecorSpec(DecorKind.CLOUD, 0.82f, 0.08f, 68f),
            DecorSpec(DecorKind.BIRD, 0.88f, 0.32f, 54f),
            DecorSpec(DecorKind.TREE, 0.08f, 0.62f, 72f),
            DecorSpec(DecorKind.FLOWER, 0.90f, 0.68f, 54f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
            .clip(ShapeCard)
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF9FDCFF), Color(0xFF8EDC9D))))
            .border(BorderStroke(3.5.dp, ColorInk), ShapeCard)
    ) {
        DecorLayer(items = board3Decors)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 3 Peekaboo Houses in a Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // House 1 (Red)
                PeekabooHouse(
                    roofColor = Color(0xFFFF6B6B),
                    wallColor = Color(0xFFFFCDD2),
                    doorAngle = house1DoorAngle.value,
                    animal = house1Animal,
                    onClick = {
                        if (house1DoorAngle.value > 10f) {
                            // Close early
                            coroutineScope.launch {
                                house1DoorAngle.animateTo(0f, tween(180))
                                house1Animal = null
                                soundHelper.playDoorClose()
                            }
                        } else {
                            // Open door!
                            val animal = BusyboardCatalog.PEEKABOO_ANIMALS.random()
                            house1Animal = animal
                            soundHelper.playDoorCreak()
                            soundHelper.playPeekabooVoice(animal.soundAnimal)
                            haptics.vibrate(20)
                            onAction()

                            coroutineScope.launch {
                                house1DoorAngle.animateTo(90f, tween(250))
                                delay(2500)
                                if (house1Animal == animal) {
                                    house1DoorAngle.animateTo(0f, tween(250))
                                    soundHelper.playDoorClose()
                                    house1Animal = null
                                }
                            }
                        }
                    }
                )

                // House 2 (Yellow)
                PeekabooHouse(
                    roofColor = Color(0xFFFFD93D),
                    wallColor = Color(0xFFFFF9C4),
                    doorAngle = house2DoorAngle.value,
                    animal = house2Animal,
                    onClick = {
                        if (house2DoorAngle.value > 10f) {
                            coroutineScope.launch {
                                house2DoorAngle.animateTo(0f, tween(180))
                                house2Animal = null
                                soundHelper.playDoorClose()
                            }
                        } else {
                            val animal = BusyboardCatalog.PEEKABOO_ANIMALS.random()
                            house2Animal = animal
                            soundHelper.playDoorCreak()
                            soundHelper.playPeekabooVoice(animal.soundAnimal)
                            haptics.vibrate(20)
                            onAction()

                            coroutineScope.launch {
                                house2DoorAngle.animateTo(90f, tween(250))
                                delay(2500)
                                if (house2Animal == animal) {
                                    house2DoorAngle.animateTo(0f, tween(250))
                                    soundHelper.playDoorClose()
                                    house2Animal = null
                                }
                            }
                        }
                    }
                )

                // House 3 (Green)
                PeekabooHouse(
                    roofColor = Color(0xFF5CCB6E),
                    wallColor = Color(0xFFC8E6C9),
                    doorAngle = house3DoorAngle.value,
                    animal = house3Animal,
                    onClick = {
                        if (house3DoorAngle.value > 10f) {
                            coroutineScope.launch {
                                house3DoorAngle.animateTo(0f, tween(180))
                                house3Animal = null
                                soundHelper.playDoorClose()
                            }
                        } else {
                            val animal = BusyboardCatalog.PEEKABOO_ANIMALS.random()
                            house3Animal = animal
                            soundHelper.playDoorCreak()
                            soundHelper.playPeekabooVoice(animal.soundAnimal)
                            haptics.vibrate(20)
                            onAction()

                            coroutineScope.launch {
                                house3DoorAngle.animateTo(90f, tween(250))
                                delay(2500)
                                if (house3Animal == animal) {
                                    house3DoorAngle.animateTo(0f, tween(250))
                                    soundHelper.playDoorClose()
                                    house3Animal = null
                                }
                            }
                        }
                    }
                )
            }

            // Large Hanging Bell (Swinging pendulum with pure ding)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .padding(bottom = 20.dp)
                    .plasticineClickable(haptics = haptics) {
                        soundHelper.playBellDing()
                        haptics.vibrate(15)
                        onAction()

                        // Golden Sparkle particles
                        repeat(8) {
                            val ang = Random.nextFloat() * 2f * PI.toFloat()
                            val spd = Random.nextFloat() * 140f + 40f
                            particles.add(
                                BusyParticle(
                                    x = 540f,
                                    y = 1200f,
                                    vx = cos(ang) * spd,
                                    vy = sin(ang) * spd,
                                    color = ColorYellow,
                                    isStar = true,
                                    size = 14f,
                                    lifeSec = 0.9f
                                )
                            )
                        }

                        // Swing Bell
                        coroutineScope.launch {
                            bellSwing.animateTo(25f, tween(120))
                            bellSwing.animateTo(-22f, tween(160))
                            bellSwing.animateTo(14f, tween(180))
                            bellSwing.animateTo(-8f, tween(200))
                            bellSwing.animateTo(0f, tween(220))
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, 20.dp.toPx())
                    val bellR = 48.dp.toPx()
                    val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

                    rotate(degrees = bellSwing.value, pivot = c) {
                        // Hanging Cord
                        drawLine(
                            color = ColorInk,
                            start = c,
                            end = Offset(c.x, c.y + 35.dp.toPx()),
                            strokeWidth = 3.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Golden Bell Dome
                        val bellCenter = Offset(c.x, c.y + 65.dp.toPx())
                        val bellPath = Path().apply {
                            moveTo(bellCenter.x - bellR * 0.7f, bellCenter.y + bellR * 0.6f)
                            quadraticBezierTo(bellCenter.x - bellR * 0.6f, bellCenter.y - bellR * 0.6f, bellCenter.x, bellCenter.y - bellR * 0.7f)
                            quadraticBezierTo(bellCenter.x + bellR * 0.6f, bellCenter.y - bellR * 0.6f, bellCenter.x + bellR * 0.7f, bellCenter.y + bellR * 0.6f)
                            close()
                        }
                        drawPath(bellPath, color = Color(0xFFFFD54F))
                        drawPath(bellPath, color = ColorInk, style = inkStroke)

                        // Bell Rim Clapper
                        drawCircle(color = Color(0xFFFF8F00), radius = 10.dp.toPx(), center = Offset(bellCenter.x, bellCenter.y + bellR * 0.65f))
                        drawCircle(color = ColorInk, radius = 10.dp.toPx(), center = Offset(bellCenter.x, bellCenter.y + bellR * 0.65f), style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }
        }
    }
}

/**
 * 🕹️ Pullable Mechanical Lever.
 */
@Composable
private fun MechanicalLever(
    iconEmoji: String,
    pullDistance: Float,
    onPull: (Float) -> Unit,
    onRelease: () -> Unit
) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .size(96.dp, 170.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onPull(dragAmount.y)
                    },
                    onDragEnd = { onRelease() },
                    onDragCancel = { onRelease() }
                )
            }
    ) {
        // Vertical Slot Background
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFD7CCC8),
            border = BorderStroke(3.dp, ColorInk),
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight()
        ) {
            Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                Text(text = iconEmoji, fontSize = 20.sp)
            }
        }

        // Pullable Knob (96 dp round handle)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset(y = pullDistance.dp)
                .size(76.dp)
                .border(BorderStroke(3.5.dp, ColorInk), CircleShape)
                .background(ColorOrange, CircleShape)
                .clip(CircleShape)
        ) {
            Text(text = iconEmoji, fontSize = 34.sp)
        }
    }
}

/**
 * 🔘 Depressible Button that physically shifts down by 6 dp.
 */
@Composable
private fun DepressibleButton(
    emoji: String,
    color: Color,
    tag: String,
    haptics: Haptics,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val pressOffsetY by animateFloatAsState(
        targetValue = if (isPressed) 6f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "btnPress"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset(y = pressOffsetY.dp)
            .size(96.dp)
            .border(BorderStroke(3.5.dp, ColorInk), CircleShape)
            .background(color, CircleShape)
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = {
                        onClick()
                    }
                )
            }
            .testTag(tag)
    ) {
        Text(text = emoji, fontSize = 44.sp)
    }
}

/**
 * 💡 Bulb + Toggle Switch Column.
 */
@Composable
private fun BulbAndSwitchColumn(
    color: Color,
    isOn: Boolean,
    isChasing: Boolean,
    haptics: Haptics,
    onToggle: () -> Unit
) {
    val lit = isOn || isChasing

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Bulb
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .border(BorderStroke(3.dp, ColorInk), CircleShape)
                .background(if (lit) color else Color(0xFFB0BEC5), CircleShape)
                .clip(CircleShape)
        ) {
            if (lit) {
                Text(text = "✨", fontSize = 24.sp)
            }
        }

        // Toggle Switch (Rocker lever)
        Box(
            contentAlignment = if (isOn) Alignment.TopCenter else Alignment.BottomCenter,
            modifier = Modifier
                .size(48.dp, 80.dp)
                .border(BorderStroke(2.5.dp, ColorInk), RoundedCornerShape(12.dp))
                .background(ColorBgSoft, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .plasticineClickable(haptics = haptics) {
                    onToggle()
                }
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp, 36.dp)
                    .border(BorderStroke(2.5.dp, ColorInk), RoundedCornerShape(8.dp))
                    .background(if (isOn) ColorWhite else Color(0xFF78909C), RoundedCornerShape(8.dp))
            )
        }
    }
}

/**
 * 🏠 Peekaboo House with swinging door.
 */
@Composable
private fun PeekabooHouse(
    roofColor: Color,
    wallColor: Color,
    doorAngle: Float,
    animal: PeekabooAnimal?,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .size(105.dp, 210.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        // House Wall and Peeking Animal
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = wallColor,
            border = BorderStroke(3.dp, ColorInk),
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (animal != null) {
                    Text(text = animal.emoji, fontSize = 52.sp)
                }
            }
        }

        // Roof
        Canvas(modifier = Modifier.fillMaxWidth().height(65.dp).align(Alignment.TopCenter)) {
            val w = size.width
            val h = size.height
            val roofPath = Path().apply {
                moveTo(w / 2f, 0f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(roofPath, color = roofColor)
            drawPath(roofPath, color = ColorInk, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Swinging Wooden Door
        if (doorAngle < 85f) {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxWidth(1f - (doorAngle / 90f) * 0.7f)
                    .height(130.dp)
                    .border(BorderStroke(2.5.dp, ColorInk), RoundedCornerShape(8.dp))
                    .background(Color(0xFF8D6E63), RoundedCornerShape(8.dp))
                    .padding(end = 8.dp)
            ) {
                // Door Knob
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .border(BorderStroke(1.5.dp, ColorInk), CircleShape)
                        .background(ColorYellow, CircleShape)
                )
            }
        }
    }
}

/**
 * ⭐ Star Particle Graphic.
 */
private fun DrawScope.drawStarGraphic(center: Offset, radius: Float, color: Color, alpha: Float) {
    val starPath = Path()
    val points = 5
    val innerR = radius * 0.48f

    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius else innerR
        val angle = i * PI.toFloat() / points - PI.toFloat() / 2f
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
    }
    starPath.close()

    drawPath(starPath, color = color.copy(alpha = alpha))
    drawPath(starPath, color = ColorInk.copy(alpha = alpha), style = Stroke(width = 1.5.dp.toPx()))
}

// =====================================================================================
// 🎹 Страница 4: Музыка — ксилофон, барабаны, горн и звери
// 🎡 Страница 5: Игрушки — колесо фортуны, поп-ит, ползунок-радуга, шестерёнки
// =====================================================================================

private fun burstAt(particles: MutableList<BusyParticle>, c: Offset, n: Int = 10) {
    if (particles.size > 140) return
    repeat(n) {
        val a = Random.nextFloat() * 2f * PI.toFloat()
        val s = Random.nextFloat() * 260f + 90f
        particles.add(
            BusyParticle(
                c.x, c.y, cos(a) * s, sin(a) * s - 120f,
                listOf(ColorPink, ColorYellow, ColorGreen, ColorOrange, ColorBlue).random(),
                isStar = it % 2 == 0, size = 12f
            )
        )
    }
}

private val BUSY_RAINBOW = listOf(
    Color(0xFFEF5350), Color(0xFFFFA726), Color(0xFFFFEE58), Color(0xFF66BB6A),
    Color(0xFF26C6DA), Color(0xFF42A5F5), Color(0xFF7E57C2), Color(0xFFEC407A)
)
private val BUSY_SCALE = listOf(1.0f, 1.122f, 1.26f, 1.335f, 1.498f, 1.682f, 1.888f, 2.0f)

/** Большая нажимаемая плитка: реагирует сразу при касании, с пружинкой и брызгами. */
@Composable
private fun BusyPad(
    modifier: Modifier,
    color: Color,
    haptics: Haptics,
    particles: MutableList<BusyParticle>,
    topInset: Float,
    emoji: String = "",
    fontSize: Int = 34,
    shape: androidx.compose.ui.graphics.Shape = ShapeButton,
    onPress: () -> Unit
) {
    var center by remember { mutableStateOf(Offset.Zero) }
    var pressed by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
        label = "busyPad"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .onGloballyPositioned {
                val p = it.positionInWindow()
                center = Offset(p.x + it.size.width / 2f, p.y + it.size.height / 2f - topInset)
            }
            .scale(sc)
            .border(BorderStroke(3.dp, ColorInk), shape)
            .background(if (pressed) color.copy(alpha = 0.8f) else color, shape)
            .clip(shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        haptics.vibrateShort()
                        burstAt(particles, center, 6)
                        onPress()
                        tryAwaitRelease()
                        pressed = false
                    }
                )
            }
    ) {
        if (emoji.isNotEmpty()) Text(text = emoji, fontSize = fontSize.sp)
    }
}

@Composable
private fun BoardMusic(
    soundManager: SoundManager,
    haptics: Haptics,
    particles: MutableList<BusyParticle>,
    topInset: Float,
    onAction: () -> Unit
) {
    Surface(
        shape = ShapeCard,
        color = Color(0xFFFFE082),
        border = BorderStroke(3.5.dp, ColorInk),
        modifier = Modifier.fillMaxSize().padding(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Ксилофон: 8 цветных пластин, каждая — своя нота
            Row(
                modifier = Modifier.weight(1.4f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                BUSY_RAINBOW.forEachIndexed { i, c ->
                    BusyPad(
                        modifier = Modifier.weight(1f).fillMaxHeight(1f - i * 0.05f),
                        color = c, haptics = haptics, particles = particles, topInset = topInset,
                        emoji = if (i % 2 == 0) "♪" else "♫", fontSize = 26
                    ) {
                        soundManager.play("sm_note1", volume = 0.9f, rate = BUSY_SCALE[i])
                        onAction()
                    }
                }
            }
            // Барабаны и горн
            Row(
                modifier = Modifier.weight(0.9f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BusyPad(Modifier.weight(1f).fillMaxHeight(), Color(0xFFEF5350), haptics, particles, topInset, "🥁") {
                    soundManager.play("sm_thud", rate = 0.7f); onAction()
                }
                BusyPad(Modifier.weight(1f).fillMaxHeight(), Color(0xFF42A5F5), haptics, particles, topInset, "🪘") {
                    soundManager.play("sm_pop", rate = 0.6f); onAction()
                }
                BusyPad(Modifier.weight(1f).fillMaxHeight(), Color(0xFFFFEE58), haptics, particles, topInset, "🎺") {
                    soundManager.play("sm_honk", rate = 0.9f + Random.nextFloat() * 0.5f); onAction()
                }
                BusyPad(Modifier.weight(1f).fillMaxHeight(), Color(0xFFAB47BC), haptics, particles, topInset, "🎉") {
                    soundManager.play("sm_party"); onAction()
                }
            }
            // Звери
            Row(
                modifier = Modifier.weight(0.9f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BusyPad(Modifier.weight(1f).fillMaxHeight(), Color(0xFFFFB74D), haptics, particles, topInset, "🐶") {
                    soundManager.play("animal_dog"); onAction()
                }
                BusyPad(Modifier.weight(1f).fillMaxHeight(), Color(0xFFFF8A80), haptics, particles, topInset, "🐱") {
                    soundManager.play("animal_cat"); onAction()
                }
                BusyPad(Modifier.weight(1f).fillMaxHeight(), Color(0xFFCE93D8), haptics, particles, topInset, "🐮") {
                    soundManager.play("animal_cow"); onAction()
                }
                BusyPad(Modifier.weight(1f).fillMaxHeight(), Color(0xFFAED581), haptics, particles, topInset, "🐸") {
                    soundManager.play("animal_frog"); onAction()
                }
            }
        }
    }
}

private val WHEEL_EMOJI = listOf("🐶", "🍎", "⭐", "🚗", "🌈", "🐱")
private val WHEEL_COLORS = listOf(
    Color(0xFFFF8A65), Color(0xFFEF5350), Color(0xFFFFEE58), Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFCE93D8)
)

@Composable
private fun BoardToys(
    soundManager: SoundManager,
    soundHelper: BusyboardSoundHelper,
    haptics: Haptics,
    particles: MutableList<BusyParticle>,
    topInset: Float,
    onAction: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // ---- колесо фортуны ----
    var rot by remember { mutableFloatStateOf(0f) }
    var spinning by remember { mutableStateOf(false) }
    var wheelCenter by remember { mutableStateOf(Offset.Zero) }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER } }

    // ---- поп-ит ----
    val popped = remember { mutableStateListOf(*Array(12) { false }) }

    // ---- ползунок ----
    var slider by remember { mutableFloatStateOf(0f) }
    var lastStep by remember { mutableIntStateOf(-1) }

    // ---- шестерёнка ----
    var gearAngle by remember { mutableFloatStateOf(0f) }
    var gearHeld by remember { mutableStateOf(false) }
    LaunchedEffect(gearHeld) {
        var frames = 0
        while (gearHeld) {
            withFrameNanos { _ ->
                gearAngle += 7f
                frames++
                if (frames % 10 == 0) soundHelper.playKnobTick()
            }
        }
    }

    Surface(
        shape = ShapeCard,
        color = Color(0xFFB2EBF2),
        border = BorderStroke(3.5.dp, ColorInk),
        modifier = Modifier.fillMaxSize().padding(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.weight(1.5f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Колесо
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .onGloballyPositioned {
                                val p = it.positionInWindow()
                                wheelCenter = Offset(p.x + it.size.width / 2f, p.y + it.size.height / 2f - topInset)
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        haptics.vibrateShort()
                                        if (!spinning) {
                                            spinning = true
                                            onAction()
                                            scope.launch {
                                                var omega = 720f + Random.nextFloat() * 520f
                                                var lastSector = floor(rot / 60f).toInt()
                                                var last = 0L
                                                while (omega > 18f) {
                                                    withFrameNanos { now ->
                                                        val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                                                        last = now
                                                        rot += omega * dt
                                                        omega -= omega * 0.85f * dt
                                                        val sec = floor(rot / 60f).toInt()
                                                        if (sec != lastSector) {
                                                            lastSector = sec
                                                            soundHelper.playKnobTick()
                                                        }
                                                    }
                                                }
                                                spinning = false
                                                val idx = floor(((360f - (rot % 360f)) % 360f) / 60f).toInt().coerceIn(0, 5)
                                                burstAt(particles, wheelCenter, 14)
                                                when (idx) {
                                                    0 -> soundManager.play("animal_dog")
                                                    1 -> soundManager.play("sm_pop")
                                                    2 -> soundManager.play("sm_tada")
                                                    3 -> soundManager.play("sm_honk")
                                                    4 -> soundManager.play("sm_party")
                                                    else -> soundManager.play("animal_cat")
                                                }
                                            }
                                        }
                                        tryAwaitRelease()
                                    }
                                )
                            }
                    ) {
                        val r = size.minDimension / 2f * 0.9f
                        val c = Offset(size.width / 2f, size.height / 2f)
                        rotate(rot, c) {
                            for (i in 0 until 6) {
                                drawArc(
                                    WHEEL_COLORS[i], -90f + i * 60f, 60f, true,
                                    Offset(c.x - r, c.y - r), Size(r * 2f, r * 2f)
                                )
                                val a = (-90f + i * 60f + 30f) * PI.toFloat() / 180f
                                paint.textSize = r * 0.38f
                                drawContext.canvas.nativeCanvas.drawText(
                                    WHEEL_EMOJI[i], c.x + cos(a) * r * 0.62f, c.y + sin(a) * r * 0.62f + r * 0.13f, paint
                                )
                            }
                        }
                        drawCircle(ColorInk, r, c, style = Stroke(width = 5.dp.toPx()))
                        drawCircle(Color.White, r * 0.12f, c)
                        drawCircle(ColorInk, r * 0.12f, c, style = Stroke(width = 3.dp.toPx()))
                        // стрелка сверху
                        val tri = Path().apply {
                            moveTo(c.x, c.y - r * 0.88f)
                            lineTo(c.x - r * 0.1f, c.y - r * 1.12f)
                            lineTo(c.x + r * 0.1f, c.y - r * 1.12f)
                            close()
                        }
                        drawPath(tri, Color(0xFFE53935))
                        drawPath(tri, ColorInk, style = Stroke(width = 3.dp.toPx()))
                    }
                }

                // Поп-ит 3×4
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (row in 0 until 4) {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (col in 0 until 3) {
                                val idx = row * 3 + col
                                val on = popped[idx]
                                BusyPad(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    color = if (on) Color(0xFFBDBDBD) else BUSY_RAINBOW[(idx * 3) % 8],
                                    haptics = haptics, particles = particles, topInset = topInset,
                                    shape = CircleShape
                                ) {
                                    popped[idx] = !popped[idx]
                                    soundManager.play("sm_pop", volume = 0.8f, rate = if (popped[idx]) 0.7f + Random.nextFloat() * 0.3f else 1.2f + Random.nextFloat() * 0.3f)
                                    onAction()
                                    if (popped.all { it }) {
                                        soundManager.play("sm_tada")
                                        scope.launch {
                                            delay(500)
                                            for (k in popped.indices) popped[k] = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Ползунок-радуга и шестерёнка
            Row(
                modifier = Modifier.weight(0.7f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(2.2f)
                        .fillMaxHeight()
                        .border(BorderStroke(3.dp, ColorInk), ShapeButton)
                        .background(Color.White, ShapeButton)
                        .clip(ShapeButton)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                fun update(x: Float) {
                                    val v = (x / size.width).coerceIn(0f, 1f)
                                    slider = v
                                    val step = (v * 7.99f).toInt()
                                    if (step != lastStep) {
                                        lastStep = step
                                        soundManager.play("sm_note1", volume = 0.8f, rate = BUSY_SCALE[step])
                                        haptics.vibrateShort()
                                        onAction()
                                    }
                                }
                                update(down.position.x)
                                down.consume()
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull() ?: break
                                    if (!ch.pressed) break
                                    update(ch.position.x)
                                    ch.consume()
                                }
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(maxWidth * slider)
                            .background(Brush.horizontalGradient(BUSY_RAINBOW))
                    )
                    Text(
                        text = "🎚️",
                        fontSize = 30.sp,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = (maxWidth * slider - 20.dp).coerceAtLeast(0.dp))
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(BorderStroke(3.dp, ColorInk), ShapeButton)
                        .background(if (gearHeld) Color(0xFFFFD54F) else Color(0xFFFFFFFF), ShapeButton)
                        .clip(ShapeButton)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    gearHeld = true
                                    haptics.vibrateShort()
                                    onAction()
                                    tryAwaitRelease()
                                    gearHeld = false
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚙️",
                        fontSize = 40.sp,
                        modifier = Modifier.graphicsLayer { rotationZ = gearAngle }
                    )
                }
            }
        }
    }
}
