package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.screens.Tea.*
import com.example.shared.*
import com.example.shared.scene.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

@Composable
fun MagicTeaKitchenScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager(context, soundPlayer) }
    val soundHelper = remember(context, soundPlayer, soundManager) {
        TeaSoundHelper(context, soundPlayer, soundManager)
    }
    val haptics = rememberHaptics()
    val coroutineScope = rememberCoroutineScope()

    // SharedPreferences for First Launch Hint
    val prefs = remember { context.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE) }
    var isFirstLaunch by remember { mutableStateOf(prefs.getBoolean("first_launch_tea", true)) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showIdleHint by remember { mutableStateOf(false) }

    // Game State
    var brewState by remember { mutableStateOf(TeaBrewState.ADDING_INGREDIENTS) }
    val cupIngredients = remember { mutableStateListOf<CupIngredientItem>() }

    // Decor controller for guest reactions
    val decorController = remember { DecorController() }
    val teaDecors = remember {
        listOf(
            DecorSpec(DecorKind.TEAPOT, 0.20f, 0.12f, 76f, id = "shelf_teapot"),
            DecorSpec(DecorKind.CAKE, 0.80f, 0.12f, 76f, id = "shelf_cake"),
            DecorSpec(DecorKind.SUN, 0.50f, 0.08f, 64f),
            DecorSpec(DecorKind.GUEST, 0.12f, 0.40f, 96f, id = "guest_bear", soundName = "decor_guest"),
            DecorSpec(DecorKind.GUEST, 0.88f, 0.40f, 96f, id = "guest_bunny", soundName = "decor_guest"),
            DecorSpec(DecorKind.FLOWER, 0.08f, 0.62f, 56f),
            DecorSpec(DecorKind.PLANT, 0.92f, 0.62f, 56f),
            DecorSpec(DecorKind.BULB, 0.50f, 0.04f, 44f)
        )
    }

    // Liquid Levels & Colors
    var liquidLevel by remember { mutableFloatStateOf(0.85f) } // 0..1
    var targetLiquidColor by remember { mutableStateOf(Color(0xFFCFEAFF)) }
    var isLiquidBrewed by remember { mutableStateOf(false) }

    // Animated Smooth Liquid Color Transition
    val animatedLiquidColor by animateColorAsState(
        targetValue = targetLiquidColor,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "teaColor"
    )

    // Animated Liquid Fill Level (for drinking and refilling)
    val animatedLiquidLevel by animateFloatAsState(
        targetValue = liquidLevel,
        animationSpec = tween(if (brewState == TeaBrewState.DRINKING) 1600 else 600, easing = LinearOutSlowInEasing),
        label = "liquidLevel"
    )

    // Cup Breathing & Sway Animation
    val infiniteTransition = rememberInfiniteTransition(label = "teaAnimations")
    val cupBreathScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cupBreath"
    )
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // Cup Smile State & Jiggle
    val cupJiggleAngle = remember { Animatable(0f) }
    val cupEyeSquint = remember { Animatable(0f) }
    val cupMouthOpen = remember { Animatable(0f) }

    // Stirring Spoon Animation
    var isStirring by remember { mutableStateOf(false) }
    val spoonAngle = remember { Animatable(0f) }
    val spoonOffsetX = remember { Animatable(0f) }

    // Particles (Steam, Bubbles, Stars)
    val steamParticles = remember { mutableStateListOf<SteamParticle>() }
    val bubbleParticles = remember { mutableStateListOf<TeaBubbleParticle>() }

    // Drag-and-Drop Floating Ingredient under finger
    var draggingIngredientType by remember { mutableStateOf<TeaIngredientType?>(null) }
    var dragTouchPos by remember { mutableStateOf(Offset.Zero) }

    // Layout References (Stored in Float state)
    var cupCenterX by remember { mutableFloatStateOf(0f) }
    var cupCenterY by remember { mutableFloatStateOf(0f) }
    var cupWidthPx by remember { mutableFloatStateOf(0f) }
    var cupHeightPx by remember { mutableFloatStateOf(0f) }

    fun recalculateLiquidColor() {
        if (cupIngredients.isEmpty()) {
            targetLiquidColor = Color(0xFFCFEAFF)
            return
        }
        var rSum = 0f
        var gSum = 0f
        var bSum = 0f
        cupIngredients.forEach { item ->
            rSum += item.type.color.red
            gSum += item.type.color.green
            bSum += item.type.color.blue
        }
        val count = cupIngredients.size.toFloat()
        var r = rSum / count
        var g = gSum / count
        var b = bSum / count

        if (isLiquidBrewed) {
            // Boost saturation by +25%
            r = (r * 1.25f).coerceIn(0f, 1f)
            g = (g * 1.25f).coerceIn(0f, 1f)
            b = (b * 1.25f).coerceIn(0f, 1f)
        }
        targetLiquidColor = Color(r, g, b)
    }

    fun addIngredientToCup(type: TeaIngredientType) {
        lastInteractionTime = System.currentTimeMillis()
        showIdleHint = false

        if (cupIngredients.size >= 3) {
            // Reached limit: gentle rejection wobbly jiggle
            soundHelper.playPop()
            haptics.vibrate(15)
            coroutineScope.launch {
                cupJiggleAngle.animateTo(6f, tween(100))
                cupJiggleAngle.animateTo(-6f, tween(120))
                cupJiggleAngle.animateTo(0f, tween(120))
            }
            return
        }

        soundHelper.playSplashSmall()
        haptics.vibrate(20)

        val spawnX = cupCenterX + (Random.nextFloat() - 0.5f) * (cupWidthPx * 0.45f)
        val spawnY = (cupCenterY - cupHeightPx / 2f) + cupHeightPx * 0.35f
        val targetY = spawnY + Random.nextFloat() * (cupHeightPx * 0.30f) + 15f

        cupIngredients.add(
            CupIngredientItem(
                id = System.currentTimeMillis() + Random.nextLong(1000),
                type = type,
                x = spawnX,
                y = spawnY,
                targetY = targetY,
                rotation = (Random.nextFloat() - 0.5f) * 35f
            )
        )

        // Spawn splash bubbles
        repeat(5) {
            bubbleParticles.add(
                TeaBubbleParticle(
                    x = spawnX + (Random.nextFloat() - 0.5f) * 20f,
                    y = spawnY,
                    vy = -Random.nextFloat() * 80f - 30f,
                    radius = Random.nextFloat() * 6f + 3f,
                    color = type.color
                )
            )
        }

        recalculateLiquidColor()
        decorController.trigger("guest_bear", DecorReaction.SMILE)
        decorController.trigger("guest_bunny", DecorReaction.SMILE)
    }

    // Inactivity 6s Hint Monitor
    LaunchedEffect(lastInteractionTime, brewState, cupIngredients.size) {
        showIdleHint = false
        delay(6000)
        showIdleHint = true
    }

    // Main Simulation Loop (Steam & Bubbles)
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

                // 1. Update Floating / Sinking Ingredients
                cupIngredients.forEach { item ->
                    if (item.y < item.targetY) {
                        item.y += 18f * dt
                    }
                    item.rotation += sin(item.y * 0.08f) * 0.3f
                }

                // 2. Spawn Steam if Brewed or Ready
                if (brewState == TeaBrewState.BREWING || brewState == TeaBrewState.READY_TO_DRINK) {
                    if (steamParticles.size < 12 && Random.nextFloat() < 0.18f && cupCenterX != 0f) {
                        steamParticles.add(
                            SteamParticle(
                                x = cupCenterX + (Random.nextFloat() - 0.5f) * 140f,
                                y = cupCenterY - cupHeightPx / 2f + 20f,
                                vx = (Random.nextFloat() - 0.5f) * 25f,
                                vy = -Random.nextFloat() * 70f - 40f,
                                scale = Random.nextFloat() * 0.6f + 0.7f
                            )
                        )
                    }
                }

                // Update Steam
                for (i in steamParticles.indices.reversed()) {
                    val s = steamParticles[i]
                    s.ageSec += dt
                    s.x += s.vx * dt
                    s.y += s.vy * dt
                    s.scale += dt * 0.4f
                    s.alpha = (1.0f - s.ageSec / s.lifeSec).coerceIn(0f, 1f)
                    if (s.ageSec >= s.lifeSec) {
                        steamParticles.removeAt(i)
                    }
                }

                // Update Bubbles
                for (i in bubbleParticles.indices.reversed()) {
                    val b = bubbleParticles[i]
                    b.ageSec += dt
                    b.y += b.vy * dt
                    b.alpha = (1.0f - b.ageSec / b.lifeSec).coerceIn(0f, 1f)
                    if (b.ageSec >= b.lifeSec) {
                        bubbleParticles.removeAt(i)
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        lastInteractionTime = System.currentTimeMillis()
                        showIdleHint = false
                        if (isFirstLaunch) {
                            isFirstLaunch = false
                            prefs.edit().putBoolean("first_launch_tea", false).apply()
                        }
                    }
                )
            }
    ) {
        // 1. Vector 3-Layer Scene Background: KITCHEN
        SceneBackground(theme = SceneTheme.KITCHEN)

        // 2. Harmless background decor with guest bear & bunny
        DecorLayer(items = teaDecors, controller = decorController)
        // Main Cup Canvas Layer
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            lastInteractionTime = System.currentTimeMillis()
                            showIdleHint = false

                            // Check Tap on Spoon
                            val spoonX = cupCenterX + cupWidthPx * 0.58f
                            val spoonY = cupCenterY - 80f
                            if ((tapOffset - Offset(spoonX, spoonY)).getDistance() <= 120f) {
                                soundHelper.playClink()
                                haptics.vibrate(15)
                                coroutineScope.launch {
                                    cupJiggleAngle.animateTo(4f, tween(80))
                                    cupJiggleAngle.animateTo(-4f, tween(100))
                                    cupJiggleAngle.animateTo(0f, tween(100))
                                }
                                return@detectTapGestures
                            }

                            // Check Tap on Cup Face
                            val dx = tapOffset.x - cupCenterX
                            val dy = tapOffset.y - cupCenterY
                            if (abs(dx) <= cupWidthPx / 2f && abs(dy) <= cupHeightPx / 2f) {
                                soundHelper.playGiggle()
                                haptics.vibrateShort()
                                coroutineScope.launch {
                                    cupEyeSquint.animateTo(1f, tween(120))
                                    cupMouthOpen.animateTo(1f, tween(120))
                                    delay(400)
                                    cupEyeSquint.animateTo(0f, tween(180))
                                    cupMouthOpen.animateTo(0f, tween(180))
                                }
                            }
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height

            // Calculate Cup Position
            val cupW = width * 0.58f
            val cupH = height * 0.30f
            val cCenter = Offset(width / 2f, height * 0.38f)

            cupCenterX = cCenter.x
            cupCenterY = cCenter.y
            cupWidthPx = cupW
            cupHeightPx = cupH

            // 1. Draw Wooden Counter Table & Checkered Tablecloth at bottom
            val tableY = height * 0.74f
            drawRect(
                color = Color(0xFFD7CCC8),
                topLeft = Offset(0f, tableY),
                size = Size(width, height - tableY)
            )
            // Red and white checkered cloth
            val checkSize = 22.dp.toPx()
            var xCheck = 0f
            while (xCheck < width) {
                var yCheck = tableY
                while (yCheck < height) {
                    val isAlt = (((xCheck / checkSize).toInt() + (yCheck / checkSize).toInt()) % 2 == 0)
                    if (isAlt) {
                        drawRect(
                            color = Color(0xFFFF5252).copy(alpha = 0.35f),
                            topLeft = Offset(xCheck, yCheck),
                            size = Size(checkSize, checkSize)
                        )
                    }
                    yCheck += checkSize
                }
                xCheck += checkSize
            }
            drawLine(
                color = ColorInk,
                start = Offset(0f, tableY),
                end = Offset(width, tableY),
                strokeWidth = 3.5.dp.toPx()
            )

            // 2. Draw Steam Clouds
            steamParticles.forEach { s ->
                drawCircle(
                    color = ColorWhite.copy(alpha = s.alpha * 0.6f),
                    radius = 18.dp.toPx() * s.scale,
                    center = Offset(s.x, s.y)
                )
            }

            // 3. Draw Cup (Glass Body, Liquid, Sine Waves, Floating Items, Smiling Face)
            rotate(degrees = cupJiggleAngle.value, pivot = cCenter) {
                scale(scale = cupBreathScale, pivot = cCenter) {
                    drawPlasticineTeaCup(
                        center = cCenter,
                        width = cupW,
                        height = cupH,
                        liquidLevel = animatedLiquidLevel,
                        liquidColor = animatedLiquidColor,
                        wavePhase = wavePhase,
                        ingredients = cupIngredients,
                        eyeSquint = cupEyeSquint.value,
                        mouthOpen = cupMouthOpen.value,
                        isBrewed = isLiquidBrewed
                    )

                    // 4. Draw Spoon alongside cup
                    drawDecorativeSpoon(
                        cupCenter = cCenter,
                        cupW = cupW,
                        cupH = cupH,
                        isStirring = isStirring,
                        stirAngle = spoonAngle.value,
                        stirOffset = spoonOffsetX.value
                    )
                }
            }

            // 5. Draw Bubbles
            bubbleParticles.forEach { b ->
                drawCircle(
                    color = b.color.copy(alpha = b.alpha),
                    radius = b.radius,
                    center = Offset(b.x, b.y)
                )
                drawCircle(
                    color = ColorInk.copy(alpha = b.alpha * 0.6f),
                    radius = b.radius,
                    center = Offset(b.x, b.y),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 6. Draw Dragging Ingredient under finger
            draggingIngredientType?.let { dragType ->
                drawCircle(
                    color = ColorWhite,
                    radius = 36.dp.toPx(),
                    center = dragTouchPos
                )
                drawCircle(
                    color = ColorInk,
                    radius = 36.dp.toPx(),
                    center = dragTouchPos,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        // ONE Single Big Action Button (96 dp, changes according to state)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = (brewState == TeaBrewState.ADDING_INGREDIENTS && cupIngredients.isNotEmpty()) ||
                        brewState == TeaBrewState.READY_TO_DRINK,
                enter = scaleIn(animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)),
                exit = scaleOut(animationSpec = tween(150))
            ) {
                if (brewState == TeaBrewState.ADDING_INGREDIENTS) {
                    // «Заварить» Button (96 dp, Orange, Teapot 🫖)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(96.dp)
                            .border(BorderStroke(3.5.dp, ColorInk), CircleShape)
                            .background(ColorOrange, CircleShape)
                            .clip(CircleShape)
                            .plasticineClickable(haptics = haptics) {
                                soundHelper.playStir()
                                soundHelper.playSteam()
                                haptics.vibrate(25)
                                brewState = TeaBrewState.BREWING
                                isStirring = true

                                // Guests inhale steam and ears perk up, teapot steams
                                decorController.trigger("guest_bear", DecorReaction.JUMP)
                                decorController.trigger("guest_bunny", DecorReaction.JUMP)
                                decorController.trigger("shelf_teapot", DecorReaction.TEAPOT_STEAM)

                                coroutineScope.launch {
                                    // 2.5 seconds stirring animation
                                    launch {
                                        spoonAngle.animateTo(25f, tween(300))
                                        repeat(6) {
                                            spoonOffsetX.animateTo(18f, tween(200))
                                            spoonOffsetX.animateTo(-18f, tween(200))
                                        }
                                        spoonOffsetX.animateTo(0f, tween(200))
                                        spoonAngle.animateTo(0f, tween(200))
                                    }

                                    // Cup ecstatic smile
                                    cupMouthOpen.animateTo(1f, tween(300))

                                    delay(2500)
                                    isStirring = false
                                    isLiquidBrewed = true
                                    recalculateLiquidColor()
                                    soundHelper.playChime()
                                    haptics.vibrateShort()

                                    // Special combinations fizz particles
                                    checkAndTriggerSpecialCombos(cupIngredients, bubbleParticles, Offset(cupCenterX, cupCenterY))

                                    brewState = TeaBrewState.READY_TO_DRINK
                                    cupMouthOpen.animateTo(0f, tween(200))
                                }
                            }
                            .testTag("tea_brew_btn")
                    ) {
                        Text(text = "🫖", fontSize = 44.sp)
                    }
                } else if (brewState == TeaBrewState.READY_TO_DRINK) {
                    // «Выпить» Button (96 dp, Pink, Laughing Mouth 😋)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(96.dp)
                            .border(BorderStroke(3.5.dp, ColorInk), CircleShape)
                            .background(ColorPink, CircleShape)
                            .clip(CircleShape)
                            .plasticineClickable(haptics = haptics) {
                                soundHelper.playGlug()
                                brewState = TeaBrewState.DRINKING

                                // Guests clap hands for 2s, cake sparkles
                                decorController.trigger("guest_bear", DecorReaction.CLAP_HANDS)
                                decorController.trigger("guest_bunny", DecorReaction.CLAP_HANDS)
                                decorController.trigger("shelf_cake", DecorReaction.CANDLE_DANCE)

                                coroutineScope.launch {
                                    // Periodic haptic gulps
                                    launch {
                                        repeat(5) {
                                            delay(300)
                                            haptics.vibrate(10)
                                        }
                                    }

                                    // Lower liquid level
                                    liquidLevel = 0.05f
                                    cupMouthOpen.animateTo(1f, tween(400))

                                    delay(1600)

                                    // Finished drinking: stars and yum!
                                    soundHelper.playYum()
                                    haptics.vibrate(25)
                                    cupMouthOpen.animateTo(0f, tween(200))

                                    // Burst stars around cup
                                    repeat(12) {
                                        val ang = Random.nextFloat() * 2f * PI.toFloat()
                                        val spd = Random.nextFloat() * 150f + 50f
                                        bubbleParticles.add(
                                            TeaBubbleParticle(
                                                x = cupCenterX,
                                                y = cupCenterY,
                                                vy = sin(ang) * spd,
                                                radius = Random.nextFloat() * 6f + 4f,
                                                color = ColorYellow,
                                                isSparkle = true
                                            )
                                        )
                                    }

                                    // Refill fresh blue water
                                    delay(400)
                                    cupIngredients.clear()
                                    isLiquidBrewed = false
                                    targetLiquidColor = Color(0xFFCFEAFF)
                                    liquidLevel = 0.85f

                                    brewState = TeaBrewState.ADDING_INGREDIENTS
                                }
                            }
                            .testTag("tea_drink_btn")
                    ) {
                        Text(text = "😋", fontSize = 44.sp)
                    }
                }
            }
        }

        // Bottom Shelf with 4 Ingredients (88 dp buttons, always present)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TeaIngredientType.values().forEach { ingredient ->
                val isBeingDragged = draggingIngredientType == ingredient

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .border(BorderStroke(3.dp, ColorInk), CircleShape)
                        .background(
                            if (isBeingDragged) ColorWhite.copy(alpha = 0.4f) else ColorWhite,
                            CircleShape
                        )
                        .clip(CircleShape)
                        .pointerInput(ingredient) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    lastInteractionTime = System.currentTimeMillis()
                                    showIdleHint = false
                                    draggingIngredientType = ingredient
                                    dragTouchPos = offset
                                    soundHelper.playPickup()
                                    haptics.vibrate(15)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val rawPos = dragTouchPos + dragAmount
                                    val distToCup = kotlin.math.hypot(rawPos.x - cupCenterX, rawPos.y - cupCenterY)
                                    val magneticRadius = 180f
                                    val snapPos = if (distToCup < magneticRadius) {
                                        val pullFactor = (1f - (distToCup / magneticRadius)).coerceIn(0f, 1f)
                                        val springPull = pullFactor * pullFactor * 0.5f
                                        Offset(
                                            rawPos.x + (cupCenterX - rawPos.x) * springPull,
                                            rawPos.y + (cupCenterY - rawPos.y) * springPull
                                        )
                                    } else {
                                        rawPos
                                    }
                                    dragTouchPos = snapPos
                                },
                                onDragEnd = {
                                    val left = cupCenterX - cupWidthPx / 2f - 80f
                                    val top = cupCenterY - cupHeightPx / 2f - 80f
                                    val right = cupCenterX + cupWidthPx / 2f + 80f
                                    val bottom = cupCenterY + cupHeightPx / 2f + 80f

                                    if (dragTouchPos.x in left..right && dragTouchPos.y in top..bottom) {
                                        addIngredientToCup(ingredient)
                                    }
                                    draggingIngredientType = null
                                },
                                onDragCancel = {
                                    draggingIngredientType = null
                                }
                            )
                        }
                        .pointerInput(ingredient) {
                            detectTapGestures(
                                onTap = {
                                    // Simple tap: fly into cup!
                                    soundHelper.playPickup()
                                    haptics.vibrate(15)
                                    addIngredientToCup(ingredient)
                                }
                            )
                        }
                        .testTag("tea_ingredient_${ingredient.id}")
                ) {
                    Text(text = ingredient.emoji, fontSize = 38.sp)
                }
            }
        }

        // Hint Hand Gesture
        if (isFirstLaunch || showIdleHint) {
            val hintFrom = when {
                cupIngredients.isEmpty() -> Offset(160f, 620f)
                brewState == TeaBrewState.ADDING_INGREDIENTS -> Offset(160f, 490f)
                brewState == TeaBrewState.READY_TO_DRINK -> Offset(160f, 490f)
                else -> Offset(160f, 620f)
            }
            val hintTo = when {
                cupIngredients.isEmpty() -> Offset(160f, 320f)
                else -> Offset.Zero
            }
            HintHand(
                from = hintFrom,
                to = hintTo,
                mode = if (cupIngredients.isEmpty()) "drag" else "tap",
                delayMs = if (isFirstLaunch) 1200L else 200L
            )
        }
    }
}

/**
 * 🍵 Transparent Glass Tea Cup with Animated Sine Waves, Liquid, Smiling Face, and Ingredients.
 */
private fun DrawScope.drawPlasticineTeaCup(
    center: Offset,
    width: Float,
    height: Float,
    liquidLevel: Float,
    liquidColor: Color,
    wavePhase: Float,
    ingredients: List<CupIngredientItem>,
    eyeSquint: Float,
    mouthOpen: Float,
    isBrewed: Boolean
) {
    val inkStroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val halfW = width / 2f
    val halfH = height / 2f
    val topY = center.y - halfH
    val bottomY = center.y + halfH

    // 1. Cup Outline Path
    val cupPath = Path().apply {
        moveTo(center.x - halfW, topY)
        quadraticBezierTo(center.x - halfW, bottomY, center.x - halfW * 0.65f, bottomY)
        lineTo(center.x + halfW * 0.65f, bottomY)
        quadraticBezierTo(center.x + halfW, bottomY, center.x + halfW, topY)
        close()
    }

    // 2. Liquid Fill with Animated Sine Waves
    val currentLiquidY = bottomY - (height * 0.85f * liquidLevel)
    val liquidPath = Path().apply {
        moveTo(center.x - halfW, currentLiquidY)
        // Sine wave top surface
        val steps = 20
        val stepX = width / steps
        for (i in 0..steps) {
            val px = (center.x - halfW) + i * stepX
            val py = currentLiquidY + sin(wavePhase + i * 0.4f) * 4.dp.toPx()
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        // Follow cup bottom
        quadraticBezierTo(center.x + halfW, bottomY, center.x + halfW * 0.65f, bottomY)
        lineTo(center.x - halfW * 0.65f, bottomY)
        quadraticBezierTo(center.x - halfW, bottomY, center.x - halfW, currentLiquidY)
        close()
    }

    // Draw Liquid
    drawPath(liquidPath, color = liquidColor.copy(alpha = 0.80f))

    // 3. Draw Ingredients Floating inside liquid
    ingredients.forEach { item ->
        drawCircle(
            color = ColorWhite,
            radius = 16.dp.toPx(),
            center = Offset(item.x, item.y)
        )
        drawCircle(
            color = ColorInk,
            radius = 16.dp.toPx(),
            center = Offset(item.x, item.y),
            style = Stroke(width = 2.dp.toPx())
        )
    }

    // 4. Glass Cup Transparent Body & Plasticine Contour
    drawPath(cupPath, color = Color(0xFFE1F5FE).copy(alpha = 0.28f))
    drawPath(cupPath, color = ColorInk, style = inkStroke)

    // Glass Reflection Highlight (White glossy curved sheen on left wall)
    val sheenPath = Path().apply {
        moveTo(center.x - halfW + 8.dp.toPx(), topY + 12.dp.toPx())
        quadraticBezierTo(
            center.x - halfW + 14.dp.toPx(),
            center.y,
            center.x - halfW * 0.60f,
            bottomY - 12.dp.toPx()
        )
    }
    drawPath(sheenPath, color = ColorWhite.copy(alpha = 0.55f), style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))

    // 5. Cup Handle (Right ear)
    val handlePath = Path().apply {
        moveTo(center.x + halfW - 2.dp.toPx(), center.y - halfH * 0.45f)
        cubicTo(
            center.x + halfW + 42.dp.toPx(),
            center.y - halfH * 0.35f,
            center.x + halfW + 42.dp.toPx(),
            center.y + halfH * 0.35f,
            center.x + halfW - 2.dp.toPx(),
            center.y + halfH * 0.45f
        )
    }
    drawPath(handlePath, color = ColorInk, style = inkStroke)

    // 6. Live Smiling Face on the front wall
    val faceCenter = Offset(center.x, center.y + 12.dp.toPx())
    val eyeSpacing = 28.dp.toPx()
    val eyeY = faceCenter.y - 14.dp.toPx()

    // Cheeks
    drawCircle(color = ColorPink.copy(alpha = 0.5f), radius = 9.dp.toPx(), center = Offset(faceCenter.x - eyeSpacing - 8.dp.toPx(), eyeY + 14.dp.toPx()))
    drawCircle(color = ColorPink.copy(alpha = 0.5f), radius = 9.dp.toPx(), center = Offset(faceCenter.x + eyeSpacing + 8.dp.toPx(), eyeY + 14.dp.toPx()))

    // Eyes
    if (eyeSquint > 0.5f) {
        // Happy squinted arcs (^ ^)
        val leftEye = Path().apply {
            moveTo(faceCenter.x - eyeSpacing - 8.dp.toPx(), eyeY + 2.dp.toPx())
            quadraticBezierTo(faceCenter.x - eyeSpacing, eyeY - 8.dp.toPx(), faceCenter.x - eyeSpacing + 8.dp.toPx(), eyeY + 2.dp.toPx())
        }
        val rightEye = Path().apply {
            moveTo(faceCenter.x + eyeSpacing - 8.dp.toPx(), eyeY + 2.dp.toPx())
            quadraticBezierTo(faceCenter.x + eyeSpacing, eyeY - 8.dp.toPx(), faceCenter.x + eyeSpacing + 8.dp.toPx(), eyeY + 2.dp.toPx())
        }
        drawPath(leftEye, color = ColorInk, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        drawPath(rightEye, color = ColorInk, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    } else {
        // Round button eyes with shiny catchlights
        val eyeR = 6.5.dp.toPx()
        drawCircle(color = ColorInk, radius = eyeR, center = Offset(faceCenter.x - eyeSpacing, eyeY))
        drawCircle(color = ColorWhite, radius = eyeR * 0.35f, center = Offset(faceCenter.x - eyeSpacing - 2.dp.toPx(), eyeY - 2.dp.toPx()))

        drawCircle(color = ColorInk, radius = eyeR, center = Offset(faceCenter.x + eyeSpacing, eyeY))
        drawCircle(color = ColorWhite, radius = eyeR * 0.35f, center = Offset(faceCenter.x + eyeSpacing - 2.dp.toPx(), eyeY - 2.dp.toPx()))
    }

    // Mouth
    val mouthCenter = Offset(faceCenter.x, faceCenter.y + 12.dp.toPx())
    if (mouthOpen > 0.5f) {
        val openMouth = Path().apply {
            moveTo(mouthCenter.x - 14.dp.toPx(), mouthCenter.y - 2.dp.toPx())
            quadraticBezierTo(mouthCenter.x, mouthCenter.y + 16.dp.toPx(), mouthCenter.x + 14.dp.toPx(), mouthCenter.y - 2.dp.toPx())
            close()
        }
        drawPath(openMouth, color = ColorInk)
        drawCircle(color = ColorPink, radius = 5.dp.toPx(), center = Offset(mouthCenter.x, mouthCenter.y + 5.dp.toPx()))
    } else {
        val smile = Path().apply {
            moveTo(mouthCenter.x - 12.dp.toPx(), mouthCenter.y)
            quadraticBezierTo(mouthCenter.x, mouthCenter.y + 10.dp.toPx(), mouthCenter.x + 12.dp.toPx(), mouthCenter.y)
        }
        drawPath(smile, color = ColorInk, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * 🥄 Decorative Wooden/Metal Spoon standing alongside cup.
 */
private fun DrawScope.drawDecorativeSpoon(
    cupCenter: Offset,
    cupW: Float,
    cupH: Float,
    isStirring: Boolean,
    stirAngle: Float,
    stirOffset: Float
) {
    val inkStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val spoonX = cupCenter.x + cupW * 0.58f + stirOffset
    val spoonY = cupCenter.y - 30.dp.toPx()

    rotate(degrees = if (isStirring) stirAngle else 14f, pivot = Offset(spoonX, spoonY)) {
        // Spoon Handle
        drawLine(
            color = Color(0xFFFFD54F),
            start = Offset(spoonX, spoonY - 45.dp.toPx()),
            end = Offset(spoonX, spoonY + 45.dp.toPx()),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = ColorInk,
            start = Offset(spoonX, spoonY - 45.dp.toPx()),
            end = Offset(spoonX, spoonY + 45.dp.toPx()),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Spoon Head
        drawOval(
            color = Color(0xFFFFE082),
            topLeft = Offset(spoonX - 9.dp.toPx(), spoonY + 30.dp.toPx()),
            size = Size(18.dp.toPx(), 26.dp.toPx())
        )
        drawOval(
            color = ColorInk,
            topLeft = Offset(spoonX - 9.dp.toPx(), spoonY + 30.dp.toPx()),
            size = Size(18.dp.toPx(), 26.dp.toPx()),
            style = inkStroke
        )
    }
}

/**
 * Special combinations fizz & sparkle generator.
 */
private fun checkAndTriggerSpecialCombos(
    ingredients: List<CupIngredientItem>,
    bubbles: MutableList<TeaBubbleParticle>,
    center: Offset
) {
    val types = ingredients.map { it.type }.toSet()
    if (TeaIngredientType.STRAWBERRY in types && TeaIngredientType.LEMON in types) {
        // Pink fizzing lemonade bubbles
        repeat(15) {
            bubbles.add(
                TeaBubbleParticle(
                    x = center.x + (Random.nextFloat() - 0.5f) * 70f,
                    y = center.y,
                    vy = -Random.nextFloat() * 120f - 40f,
                    radius = Random.nextFloat() * 7f + 4f,
                    color = Color(0xFFFF4081)
                )
            )
        }
    }
    if (TeaIngredientType.MINT in types && TeaIngredientType.LEMON in types) {
        // Green fresh fizzing bubbles
        repeat(15) {
            bubbles.add(
                TeaBubbleParticle(
                    x = center.x + (Random.nextFloat() - 0.5f) * 70f,
                    y = center.y,
                    vy = -Random.nextFloat() * 120f - 40f,
                    radius = Random.nextFloat() * 7f + 4f,
                    color = Color(0xFF00E676)
                )
            )
        }
    }
    if (TeaIngredientType.BLUEBERRY in types && TeaIngredientType.STRAWBERRY in types) {
        // Purple sparkles
        repeat(15) {
            bubbles.add(
                TeaBubbleParticle(
                    x = center.x + (Random.nextFloat() - 0.5f) * 70f,
                    y = center.y,
                    vy = -Random.nextFloat() * 120f - 40f,
                    radius = Random.nextFloat() * 6f + 3f,
                    color = Color(0xFF7C4DFF),
                    isSparkle = true
                )
            )
        }
    }
}
