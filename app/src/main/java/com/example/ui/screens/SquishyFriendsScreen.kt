package com.example.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.screens.Squish.*
import com.example.shared.*
import com.example.shared.scene.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

@Composable
fun SquishyFriendsScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager(context, soundPlayer) }
    val soundHelper = remember(context, soundPlayer, soundManager) {
        SquishSoundHelper(context, soundPlayer, soundManager)
    }
    val haptics = rememberHaptics()
    val coroutineScope = rememberCoroutineScope()
    val ageProfile = LocalAgeProfile.current

    // SharedPreferences & Migration
    val prefs = remember { context.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE) }
    val legacyPrefs = remember { context.getSharedPreferences("pelmeshki_67_prefs", Context.MODE_PRIVATE) }

    // Selected Skin & Theme State
    var selectedSkin by remember {
        val saved = prefs.getString("squish_selected_skin", HeroSkin.PELMEN.id)
        mutableStateOf(HeroSkin.values().firstOrNull { it.id == saved } ?: HeroSkin.PELMEN)
    }

    var dyeColor by remember { mutableStateOf<Color?>(null) }
    val colorWaveProgress = remember { Animatable(1f) }

    val meadowDecors = remember {
        listOf(
            DecorSpec(DecorKind.RAINBOW, 0.50f, 0.22f, 220f),
            DecorSpec(DecorKind.SUN, 0.88f, 0.08f, 76f),
            DecorSpec(DecorKind.CLOUD, 0.16f, 0.12f, 84f),
            DecorSpec(DecorKind.CLOUD, 0.68f, 0.14f, 76f),
            DecorSpec(DecorKind.KITE, 0.14f, 0.28f, 80f),
            DecorSpec(DecorKind.HOT_AIR_BALLOON, 0.84f, 0.26f, 96f),
            DecorSpec(DecorKind.BUTTERFLY, 0.12f, 0.44f, 60f),
            DecorSpec(DecorKind.BUTTERFLY, 0.24f, 0.72f, 56f),
            DecorSpec(DecorKind.BALLOON, 0.08f, 0.60f, 64f),
            DecorSpec(DecorKind.FLOWER, 0.86f, 0.86f, 64f),
            DecorSpec(DecorKind.FLOWER, 0.16f, 0.86f, 64f)
        )
    }

    var selectedBgTheme by remember {
        val saved = prefs.getString("squish_selected_bg", "bg_warm")
        mutableStateOf(SquishCatalog.BACKGROUND_THEMES.firstOrNull { it.id == saved } ?: SquishCatalog.BACKGROUND_THEMES[0])
    }

    // Unlocked Items & Outfits
    val unlockedItemIds = remember {
        val defaultSet = SquishCatalog.ALL_ITEMS.filter { it.isDefaultUnlocked }.map { it.id }.toMutableSet()
        val savedSet = prefs.getStringSet("squish_unlocked_items", emptySet()) ?: emptySet()
        // Migrate legacy unlocked items if present
        val legacyUnlocked = legacyPrefs.getStringSet("unlocked_accessories", emptySet()) ?: emptySet()
        defaultSet.addAll(savedSet)
        defaultSet.addAll(legacyUnlocked)
        mutableStateListOf<String>().apply { addAll(defaultSet) }
    }

    var equippedHeadId by remember { mutableStateOf(prefs.getString("squish_equipped_head", null)) }
    var equippedFaceId by remember { mutableStateOf(prefs.getString("squish_equipped_face", null)) }
    var equippedNeckId by remember { mutableStateOf(prefs.getString("squish_equipped_neck", null)) }

    // Interaction & Gift Counter
    var interactionCount by remember { mutableIntStateOf(prefs.getInt("squish_interaction_count", 0)) }
    var activeGiftBox by remember { mutableStateOf<FallingGiftBox?>(null) }

    // Wardrobe Sheet Visibility
    var isWardrobeOpen by remember { mutableStateOf(false) }
    var wardrobePage by remember { mutableIntStateOf(0) }

    // First Launch Hint State & Inactivity Monitor
    var isFirstLaunch by remember { mutableStateOf(prefs.getBoolean("first_launch_squish", true)) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showIdleHint by remember { mutableStateOf(false) }
    var isSleepy by remember { mutableStateOf(false) }

    // Tap History for Belly Tickle Detection (3+ taps in 2s)
    val tapTimestamps = remember { mutableStateListOf<Long>() }

    // Hero Expression State Machine
    var currentExpression by remember { mutableStateOf(HeroExpression.IDLE) }
    var expressionExpireTime by remember { mutableLongStateOf(0L) }

    fun setExpression(expr: HeroExpression) {
        if (expr.priority >= currentExpression.priority || System.currentTimeMillis() > expressionExpireTime) {
            currentExpression = expr
            if (expr.durationMs > 0L) {
                expressionExpireTime = System.currentTimeMillis() + expr.durationMs
            }
        }
    }

    // Particles & Confetti
    val particles = remember { mutableStateListOf<SquishParticle>() }

    // Soft-Body Verlet Physics Engine Instance
    val verletEngine = remember {
        SquishVerletEngine(
            onBounce = { vel ->
                soundHelper.playBounce()
                haptics.vibrate(20)
                setExpression(HeroExpression.IMPACT)
                // Spawn 5 star particles on impact
                repeat(5) {
                    val angle = Random.nextFloat() * 2f * PI.toFloat()
                    val spd = Random.nextFloat() * 180f + 50f
                    particles.add(
                        SquishParticle(
                            x = 0f, // updated in caller
                            y = 0f,
                            vx = cos(angle) * spd,
                            vy = sin(angle) * spd,
                            color = listOf(ColorYellow, ColorPink, ColorWhite).random(),
                            isStar = true,
                            size = 14f
                        )
                    )
                }
            },
            onSquishSound = { _ ->
                soundHelper.playSquish()
            },
            onStretchSound = {
                soundHelper.playStretch()
            }
        )
    }

    // Helper to register interaction and trigger gift drop every 40 interactions
    fun registerInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        showIdleHint = false
        isSleepy = false
        if (isFirstLaunch) {
            isFirstLaunch = false
            prefs.edit().putBoolean("first_launch_squish", false).apply()
        }

        interactionCount++
        prefs.edit().putInt("squish_interaction_count", interactionCount).apply()

        if (interactionCount % 40 == 0 && activeGiftBox == null) {
            // Drop a new gift box!
            activeGiftBox = FallingGiftBox(
                x = 0f, // will center in Canvas
                y = -80f,
                vy = 300f
            )
        }
    }

    // Accelerometer Shake Detection
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastShakeTime = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH

                if (gForce > 2.6f) {
                    val now = System.currentTimeMillis()
                    if (now - lastShakeTime > 1500L) {
                        lastShakeTime = now
                        soundHelper.playDizzy()
                        setExpression(HeroExpression.DIZZY)
                        haptics.vibrate(35)
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    // Inactivity Monitor (6s hint, 12s sleepy yawn)
    LaunchedEffect(lastInteractionTime) {
        showIdleHint = false
        isSleepy = false
        delay(6000)
        showIdleHint = true
        delay(6000)
        isSleepy = true
        soundHelper.playYawn()
        setExpression(HeroExpression.SLEEPY)
    }

    // Physics Simulation Loop (Frame Rate Bound)
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

                // Step Verlet Soft-Body
                verletEngine.step(dt)

                // Expression timeout check
                if (currentExpression != HeroExpression.IDLE &&
                    currentExpression != HeroExpression.FLYING &&
                    currentExpression != HeroExpression.SQUISHED &&
                    currentExpression != HeroExpression.STRETCHED &&
                    currentExpression != HeroExpression.SLEEPY &&
                    System.currentTimeMillis() > expressionExpireTime
                ) {
                    currentExpression = HeroExpression.IDLE
                }

                // Update Particles
                for (i in particles.indices.reversed()) {
                    val p = particles[i]
                    p.ageSec += dt
                    p.vy += 600f * dt
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.alpha = (1.0f - p.ageSec / p.lifeSec).coerceIn(0f, 1f)
                    if (p.ageSec >= p.lifeSec) {
                        particles.removeAt(i)
                    }
                }

                // Update Gift Box
                activeGiftBox?.let { gift ->
                    if (gift.isFalling) {
                        gift.y += gift.vy * dt
                        gift.swingAngle = sin(gift.y * 0.05f) * 12f
                        if (gift.y >= verletEngine.screenFloorY - 140f) {
                            gift.y = verletEngine.screenFloorY - 140f
                            gift.isFalling = false
                        }
                    }
                    // Despawn after 15s if untouched
                    if (!gift.isOpened && System.currentTimeMillis() - gift.spawnTime > 15000L) {
                        activeGiftBox = null
                    }
                }
            }
        }
    }

    // Touch Drag & Fling Velocity Tracking
    var dragStartTime by remember { mutableLongStateOf(0L) }
    var dragLastPos by remember { mutableStateOf(Offset.Zero) }
    var dragVelocity by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Vector 3-Layer Scene Background: MEADOW
        SceneBackground(theme = SceneTheme.MEADOW)

        // 2. Harmless interactive decor layer
        DecorLayer(items = meadowDecors)

        // 3. Vertical RainbowButtons "Краски" along right edge
        RainbowButtons(
            isVertical = true,
            onColor = { color ->
                soundHelper.playSquish()
                haptics.vibrateShort()
                dyeColor = color
                coroutineScope.launch {
                    colorWaveProgress.snapTo(0f)
                    colorWaveProgress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
                }
                val heroCenter = verletEngine.getCenter()
                repeat(8) {
                    val angle = Random.nextFloat() * 2f * PI.toFloat()
                    val spd = Random.nextFloat() * 180f + 80f
                    particles.add(
                        SquishParticle(
                            x = heroCenter.x,
                            y = heroCenter.y,
                            vx = cos(angle) * spd,
                            vy = sin(angle) * spd - 60f,
                            color = color,
                            isStar = false,
                            size = Random.nextFloat() * 14f + 8f
                        )
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp, top = 80.dp)
        )

        // Main Interactive Soft-Body Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(selectedSkin) {
                    val centerPx = Offset(size.width / 2f, size.height * 0.52f)
                    val baseRadiusPx = 105.dp.toPx()

                    // Initialize Verlet engine dimensions on first layout
                    verletEngine.screenLeft = 0f
                    verletEngine.screenRight = size.width.toFloat()
                    verletEngine.screenTop = 70.dp.toPx()
                    verletEngine.screenFloorY = size.height * 0.82f
                    if (verletEngine.points[0].x == 0f) {
                        verletEngine.initializeShape(selectedSkin, centerPx, baseRadiusPx)
                    }

                    detectDragGestures(
                        onDragStart = { startOffset ->
                            registerInteraction()
                            dragStartTime = System.currentTimeMillis()
                            dragLastPos = startOffset
                            dragVelocity = Offset.Zero
                            verletEngine.isFlying = false
                            verletEngine.isSettlingHome = false

                            // Check if tapped on Falling Gift Box
                            activeGiftBox?.let { gift ->
                                val giftPos = Offset(gift.x, gift.y)
                                if ((startOffset - giftPos).getDistance() <= 70.dp.toPx() && !gift.isOpened) {
                                    gift.isOpened = true
                                    soundHelper.playGift()
                                    haptics.vibrate(30)

                                    // Unlock a new item or theme!
                                    val lockedItems = SquishCatalog.ALL_ITEMS.filter { it.id !in unlockedItemIds }
                                    if (lockedItems.isNotEmpty()) {
                                        val unlockedItem = lockedItems.random()
                                        unlockedItemIds.add(unlockedItem.id)
                                        prefs.edit().putStringSet("squish_unlocked_items", unlockedItemIds.toSet()).apply()
                                    }

                                    // Celebration particles
                                    repeat(20) {
                                        val angle = Random.nextFloat() * 2f * PI.toFloat()
                                        val spd = Random.nextFloat() * 250f + 80f
                                        particles.add(
                                            SquishParticle(
                                                x = gift.x,
                                                y = gift.y,
                                                vx = cos(angle) * spd,
                                                vy = sin(angle) * spd - 60f,
                                                color = listOf(ColorPink, ColorYellow, ColorGreen, ColorOrange, ColorBlue).random(),
                                                isStar = true,
                                                size = 16f
                                            )
                                        )
                                    }
                                    activeGiftBox = null
                                    return@detectDragGestures
                                }
                            }

                            val distToHero = (startOffset - verletEngine.getCenter()).getDistance()
                            if (distToHero < baseRadiusPx * 1.5f) {
                                verletEngine.applyTouchInfluence(startOffset, intensityMultiplier = 1.0f)
                                setExpression(HeroExpression.SQUISHED)
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            registerInteraction()
                            val now = System.currentTimeMillis()
                            val dtMs = (now - dragStartTime).coerceAtLeast(1L)
                            dragVelocity = (change.position - dragLastPos) * (1000f / dtMs.toFloat())
                            dragLastPos = change.position
                            dragStartTime = now

                            verletEngine.applyTouchInfluence(change.position, intensityMultiplier = 1.0f)

                            val pullDist = (change.position - verletEngine.homeCenter).getDistance()
                            if (pullDist > baseRadiusPx * 0.7f) {
                                setExpression(HeroExpression.STRETCHED)
                            } else {
                                setExpression(HeroExpression.SQUISHED)
                            }
                        },
                        onDragEnd = {
                            val speed = dragVelocity.getDistance()
                            if (speed > 600.dp.toPx()) {
                                // Fast release -> FLING!
                                verletEngine.fling(dragVelocity)
                                soundHelper.playFly()
                                setExpression(HeroExpression.FLYING)
                                haptics.vibrate(25)
                            } else {
                                // Soft release -> Spring wobble back
                                soundHelper.playReleaseBoing()
                                setExpression(HeroExpression.HAPPY)
                                verletEngine.isFlying = false
                                verletEngine.isSettlingHome = true
                            }
                        },
                        onDragCancel = {
                            soundHelper.playReleaseBoing()
                            setExpression(HeroExpression.HAPPY)
                            verletEngine.isFlying = false
                            verletEngine.isSettlingHome = true
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            registerInteraction()
                            val heroCenter = verletEngine.getCenter()
                            val dist = (tapOffset - heroCenter).getDistance()
                            val baseRadius = 105.dp.toPx()

                            if (dist < baseRadius * 1.4f) {
                                soundHelper.playPoke()
                                haptics.vibrate(15)
                                verletEngine.poke(tapOffset)

                                // Check Belly Tickle (lower third tap series)
                                if (tapOffset.y > heroCenter.y + baseRadius * 0.15f) {
                                    val now = System.currentTimeMillis()
                                    tapTimestamps.add(now)
                                    // Keep taps within 2 seconds
                                    tapTimestamps.removeAll { now - it > 2000L }
                                    if (tapTimestamps.size >= 3) {
                                        soundHelper.playGiggle()
                                        setExpression(HeroExpression.GIGGLE)
                                        tapTimestamps.clear()
                                    }
                                } else {
                                    setExpression(HeroExpression.HAPPY)
                                }
                            }
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height

            // Set safe gift box X coordinate (right side 80% or left side 20%, >120dp from hero)
            val heroCenter = verletEngine.getCenter()
            activeGiftBox?.let { gift ->
                if (gift.x == 0f) {
                    val safeX = if (heroCenter.x > width * 0.5f) width * 0.22f else width * 0.78f
                    gift.x = safeX
                }
            }

            // 1. Draw Soft Floor Band in Plasticine Style (#8EDC9D) & Oval Shadow under Hero
            val floorY = height * 0.82f
            drawRoundRect(
                color = Color(0xFF8EDC9D),
                topLeft = Offset(0f, floorY),
                size = Size(width, height - floorY),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
            )
            drawLine(
                color = ColorInk,
                start = Offset(0f, floorY),
                end = Offset(width, floorY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Oval shadow under hero
            val shadowR = verletEngine.baseRadiusPx * 1.1f
            drawOval(
                color = Color(0x33000000),
                topLeft = Offset(heroCenter.x - shadowR, floorY - 14.dp.toPx()),
                size = Size(shadowR * 2f, 28.dp.toPx())
            )

            // 2. Draw Soft-Body Hero (Plasticine Mesh + Face + Clothes)
            drawPlasticineHero(
                engine = verletEngine,
                skin = selectedSkin,
                expression = currentExpression,
                equippedHead = SquishCatalog.ALL_ITEMS.firstOrNull { it.id == equippedHeadId },
                equippedFace = SquishCatalog.ALL_ITEMS.firstOrNull { it.id == equippedFaceId },
                equippedNeck = SquishCatalog.ALL_ITEMS.firstOrNull { it.id == equippedNeckId },
                dyeColor = dyeColor
            )

            // 3. Draw Falling Gift Box if active
            activeGiftBox?.let { gift ->
                rotate(degrees = gift.swingAngle, pivot = Offset(gift.x, gift.y)) {
                    drawGiftBoxGraphic(center = Offset(gift.x, gift.y), size = 60.dp.toPx())
                }
            }

            // 4. Draw Particles
            particles.forEach { p ->
                if (p.isStar) {
                    drawStarParticle(center = Offset(p.x, p.y), radius = p.size, alpha = p.alpha, color = p.color)
                } else {
                    drawCircle(color = p.color.copy(alpha = p.alpha), radius = p.size / 2, center = Offset(p.x, p.y))
                }
            }
        }

        // Top UI Layer: Home space, Skin selectors, Wardrobe button (Hidden for 2yo)
        if (!ageProfile.isToddler2) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Space for HomeButton (80 dp) placed by MainActivity
                    Spacer(modifier = Modifier.size(80.dp))

                    // 4 Hero Skin Selection Buttons (72 dp each, no text)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeroSkin.values().forEach { skin ->
                            val isSelected = selectedSkin == skin
                            val btnScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.15f else 1.0f,
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                                label = "skinScale"
                            )

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .scale(btnScale)
                                    .size(56.dp)
                                    .border(
                                        BorderStroke(if (isSelected) 3.5.dp else 2.dp, ColorInk),
                                        CircleShape
                                    )
                                    .background(if (isSelected) ColorWhite else ColorBgSoft, CircleShape)
                                    .clip(CircleShape)
                                    .plasticineClickable(haptics = haptics) {
                                        if (selectedSkin != skin) {
                                            selectedSkin = skin
                                            dyeColor = null
                                            prefs.edit().putString("squish_selected_skin", skin.id).apply()
                                            soundHelper.playSquish()
                                            haptics.vibrateShort()
                                            val heroCenter = verletEngine.getCenter()
                                            verletEngine.initializeShape(skin, heroCenter, verletEngine.baseRadiusPx)
                                        }
                                    }
                                    .testTag("squish_skin_btn_${skin.name.lowercase()}")
                            ) {
                                Text(text = skin.iconEmoji, fontSize = 28.sp)
                            }
                        }
                    }

                    // 👗 Wardrobe Button (72 dp, opens bottom sheet)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .border(BorderStroke(3.dp, ColorInk), ShapeButton)
                            .background(ColorBgSoft, ShapeButton)
                            .clip(ShapeButton)
                            .plasticineClickable(haptics = haptics) {
                                soundManager.play("tap")
                                haptics.vibrateShort()
                                isWardrobeOpen = !isWardrobeOpen
                            }
                            .testTag("squish_wardrobe_btn")
                    ) {
                        Text(text = "👒", fontSize = 32.sp)
                    }
                }
            }
        }

        // Wardrobe Bottom Sheet Panel (35% screen height)
        AnimatedVisibility(
            visible = isWardrobeOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = ColorBgSoft,
                border = BorderStroke(3.5.dp, ColorInk),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.38f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header with Category/Close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Шкаф нарядов 🎀",
                            style = PlayTypography.titleLarge,
                            fontSize = 22.sp,
                            color = ColorInk,
                            fontWeight = FontWeight.Black
                        )

                        // Close Button (72 dp target)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(52.dp)
                                .border(BorderStroke(2.5.dp, ColorInk), CircleShape)
                                .background(ColorWhite, CircleShape)
                                .clip(CircleShape)
                                .plasticineClickable(haptics = haptics) {
                                    isWardrobeOpen = false
                                }
                        ) {
                            Text(text = "✕", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ColorInk)
                        }
                    }

                    // 3x2 Grid of Accessories (80 dp items)
                    val pageSize = 6
                    val allItems = SquishCatalog.ALL_ITEMS
                    val totalPages = (allItems.size + pageSize - 1) / pageSize
                    val currentItems = allItems.drop(wardrobePage * pageSize).take(pageSize)

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(currentItems) { item ->
                            val isUnlocked = item.id in unlockedItemIds
                            val isEquipped = item.id == equippedHeadId || item.id == equippedFaceId || item.id == equippedNeckId

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(80.dp)
                                    .border(
                                        BorderStroke(if (isEquipped) 3.5.dp else 2.dp, if (isEquipped) ColorPink else ColorInk),
                                        ShapeButton
                                    )
                                    .background(if (isEquipped) ColorWhite else if (isUnlocked) ColorBg else Color(0xFFE0E0E0), ShapeButton)
                                    .clip(ShapeButton)
                                    .plasticineClickable(haptics = haptics) {
                                        if (isUnlocked) {
                                            soundHelper.playWear()
                                            setExpression(HeroExpression.PROUD)
                                            haptics.vibrateShort()

                                            when (item.slot) {
                                                WardrobeSlot.HEAD -> {
                                                    equippedHeadId = if (equippedHeadId == item.id) null else item.id
                                                    prefs.edit().putString("squish_equipped_head", equippedHeadId).apply()
                                                }
                                                WardrobeSlot.FACE -> {
                                                    equippedFaceId = if (equippedFaceId == item.id) null else item.id
                                                    prefs.edit().putString("squish_equipped_face", equippedFaceId).apply()
                                                }
                                                WardrobeSlot.NECK -> {
                                                    equippedNeckId = if (equippedNeckId == item.id) null else item.id
                                                    prefs.edit().putString("squish_equipped_neck", equippedNeckId).apply()
                                                }
                                            }
                                        } else {
                                            soundManager.play("soft_no")
                                        }
                                    }
                            ) {
                                if (isUnlocked) {
                                    Text(text = item.iconEmoji, fontSize = 36.sp)
                                } else {
                                    Text(text = "🎁", fontSize = 32.sp, color = Color.Gray)
                                }
                            }
                        }
                    }

                    // Pagination row with 72 dp touch arrows
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous page
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(52.dp)
                                .border(BorderStroke(2.5.dp, ColorInk), ShapeButton)
                                .background(ColorWhite, ShapeButton)
                                .clip(ShapeButton)
                                .plasticineClickable(haptics = haptics) {
                                    if (wardrobePage > 0) wardrobePage--
                                }
                        ) {
                            Text(text = "◀", fontSize = 20.sp, color = ColorInk)
                        }

                        // Theme Switcher Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(52.dp)
                                .border(BorderStroke(2.5.dp, ColorInk), ShapeButton)
                                .background(ColorWhite, ShapeButton)
                                .clip(ShapeButton)
                                .padding(horizontal = 14.dp)
                                .plasticineClickable(haptics = haptics) {
                                    val nextIdx = (SquishCatalog.BACKGROUND_THEMES.indexOf(selectedBgTheme) + 1) % SquishCatalog.BACKGROUND_THEMES.size
                                    selectedBgTheme = SquishCatalog.BACKGROUND_THEMES[nextIdx]
                                    prefs.edit().putString("squish_selected_bg", selectedBgTheme.id).apply()
                                    soundManager.play("magic")
                                }
                        ) {
                            Text(text = "Фон 🎨", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorInk)
                        }

                        // Next page
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(52.dp)
                                .border(BorderStroke(2.5.dp, ColorInk), ShapeButton)
                                .background(ColorWhite, ShapeButton)
                                .clip(ShapeButton)
                                .plasticineClickable(haptics = haptics) {
                                    if (wardrobePage < totalPages - 1) wardrobePage++
                                }
                        ) {
                            Text(text = "▶", fontSize = 20.sp, color = ColorInk)
                        }
                    }
                }
            }
        }

        // Hint Hand Gesture
        if (isFirstLaunch || showIdleHint) {
            HintHand(
                from = Offset(160f, 380f),
                to = Offset(240f, 460f),
                mode = if (isFirstLaunch) "drag" else "tap",
                delayMs = if (isFirstLaunch) 1000L else 200L
            )
        }
    }
}

/**
 * 🥟 Vector Plasticine Hero Renderer: Soft Mesh Body + Facial Features + Clothes.
 */
private fun DrawScope.drawPlasticineHero(
    engine: SquishVerletEngine,
    skin: HeroSkin,
    expression: HeroExpression,
    equippedHead: WardrobeItem?,
    equippedFace: WardrobeItem?,
    equippedNeck: WardrobeItem?,
    dyeColor: Color? = null
) {
    val count = engine.pointCount
    if (engine.points[0].x == 0f) return

    val heroCenter = engine.getCenter()
    val inkStroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    // 1. Construct Smooth Vector Path through 16 boundary points
    val bodyPath = Path()
    val p0 = engine.points[0]
    bodyPath.moveTo(p0.x, p0.y)

    for (i in 0 until count) {
        val curr = engine.points[i]
        val next = engine.points[(i + 1) % count]
        val midX = (curr.x + next.x) / 2f
        val midY = (curr.y + next.y) / 2f
        bodyPath.quadraticBezierTo(curr.x, curr.y, midX, midY)
    }
    bodyPath.close()

    // 2. Radial Gradient Plasticine Fill (with dye color blending if dipped in paint)
    val effectiveBase = dyeColor?.let { dye ->
        Color(
            red = (skin.baseColor.red * 0.3f + dye.red * 0.7f).coerceIn(0f, 1f),
            green = (skin.baseColor.green * 0.3f + dye.green * 0.7f).coerceIn(0f, 1f),
            blue = (skin.baseColor.blue * 0.3f + dye.blue * 0.7f).coerceIn(0f, 1f),
            alpha = 1.0f
        )
    } ?: skin.baseColor

    val effectiveHighlight = dyeColor?.let {
        Color(
            red = (effectiveBase.red + 0.22f).coerceAtMost(1f),
            green = (effectiveBase.green + 0.22f).coerceAtMost(1f),
            blue = (effectiveBase.blue + 0.22f).coerceAtMost(1f),
            alpha = 1f
        )
    } ?: skin.highlightColor

    val effectiveShade = dyeColor?.let {
        Color(
            red = (effectiveBase.red * 0.76f).coerceIn(0f, 1f),
            green = (effectiveBase.green * 0.76f).coerceIn(0f, 1f),
            blue = (effectiveBase.blue * 0.76f).coerceIn(0f, 1f),
            alpha = 1f
        )
    } ?: skin.shadeColor

    val gradientBrush = Brush.radialGradient(
        colors = listOf(effectiveHighlight, effectiveBase, effectiveShade),
        center = Offset(heroCenter.x - 35.dp.toPx(), heroCenter.y - 35.dp.toPx()),
        radius = engine.baseRadiusPx * 1.5f
    )
    drawPath(bodyPath, brush = gradientBrush)
    drawPath(bodyPath, color = ColorInk, style = inkStroke)

    // 3. Specular Highlight (Soft white plasticine sheen)
    drawOval(
        color = ColorWhite.copy(alpha = 0.35f),
        topLeft = Offset(heroCenter.x - engine.baseRadiusPx * 0.55f, heroCenter.y - engine.baseRadiusPx * 0.55f),
        size = Size(engine.baseRadiusPx * 0.45f, engine.baseRadiusPx * 0.28f)
    )

    // 4. Cute Crimped Edge for Pelmen (Orange #FF8800)
    if (skin == HeroSkin.PELMEN) {
        val crimpPath = Path()
        val topP1 = engine.points[12]
        val topP2 = engine.points[4]
        crimpPath.moveTo(topP1.x, topP1.y)
        crimpPath.quadraticBezierTo(heroCenter.x, heroCenter.y - engine.baseRadiusPx * 0.95f, topP2.x, topP2.y)
        drawPath(crimpPath, color = Color(0xFFFF8800), style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
    }

    // 5. Procedural Facial Features (Eyes, Cheeks, Mouth, Eyebrows)
    drawProceduralFace(
        center = heroCenter,
        radius = engine.baseRadiusPx,
        expression = expression
    )

    // 6. Draw Equipped Clothes & Accessories
    equippedHead?.let { headItem ->
        val headAnchor = Offset(heroCenter.x, heroCenter.y - engine.baseRadiusPx * 0.85f)
        drawAccessoryEmoji(headItem.iconEmoji, headAnchor, size = 48.dp.toPx())
    }

    equippedFace?.let { faceItem ->
        val faceAnchor = Offset(heroCenter.x, heroCenter.y - engine.baseRadiusPx * 0.10f)
        drawAccessoryEmoji(faceItem.iconEmoji, faceAnchor, size = 38.dp.toPx())
    }

    equippedNeck?.let { neckItem ->
        val neckAnchor = Offset(heroCenter.x, heroCenter.y + engine.baseRadiusPx * 0.65f)
        drawAccessoryEmoji(neckItem.iconEmoji, neckAnchor, size = 42.dp.toPx())
    }
}

/**
 * Procedural Facial Expressions with Lerp.
 */
private fun DrawScope.drawProceduralFace(
    center: Offset,
    radius: Float,
    expression: HeroExpression
) {
    val inkStroke = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val eyeSpacing = radius * 0.38f
    val eyeY = center.y - radius * 0.12f
    val leftEyeCenter = Offset(center.x - eyeSpacing, eyeY)
    val rightEyeCenter = Offset(center.x + eyeSpacing, eyeY)

    // Cheeks (Rosy Blush)
    val cheekR = radius * 0.16f
    val cheekAlpha = if (expression == HeroExpression.SQUISHED || expression == HeroExpression.HAPPY) 0.65f else 0.40f
    drawCircle(color = ColorPink.copy(alpha = cheekAlpha), radius = cheekR, center = Offset(center.x - eyeSpacing * 1.3f, eyeY + radius * 0.18f))
    drawCircle(color = ColorPink.copy(alpha = cheekAlpha), radius = cheekR, center = Offset(center.x + eyeSpacing * 1.3f, eyeY + radius * 0.18f))

    // Eyes Drawing based on expression
    when (expression) {
        HeroExpression.GIGGLE, HeroExpression.IMPACT, HeroExpression.SQUISHED -> {
            // Squinted Happy / Closed Arcs (^ ^)
            val leftArc = Path().apply {
                moveTo(leftEyeCenter.x - 12.dp.toPx(), leftEyeCenter.y + 4.dp.toPx())
                quadraticBezierTo(leftEyeCenter.x, leftEyeCenter.y - 12.dp.toPx(), leftEyeCenter.x + 12.dp.toPx(), leftEyeCenter.y + 4.dp.toPx())
            }
            val rightArc = Path().apply {
                moveTo(rightEyeCenter.x - 12.dp.toPx(), rightEyeCenter.y + 4.dp.toPx())
                quadraticBezierTo(rightEyeCenter.x, rightEyeCenter.y - 12.dp.toPx(), rightEyeCenter.x + 12.dp.toPx(), rightEyeCenter.y + 4.dp.toPx())
            }
            drawPath(leftArc, color = ColorInk, style = inkStroke)
            drawPath(rightArc, color = ColorInk, style = inkStroke)
        }
        HeroExpression.DIZZY -> {
            // Dizzy Stars / Spirals in eyes (⭐ ⭐)
            drawStarParticle(leftEyeCenter, radius = 10.dp.toPx(), alpha = 1f, color = ColorYellow)
            drawStarParticle(rightEyeCenter, radius = 10.dp.toPx(), alpha = 1f, color = ColorYellow)
        }
        HeroExpression.SLEEPY -> {
            // Closed sleepy flat lines (- -)
            drawLine(color = ColorInk, start = Offset(leftEyeCenter.x - 10.dp.toPx(), leftEyeCenter.y), end = Offset(leftEyeCenter.x + 10.dp.toPx(), leftEyeCenter.y), strokeWidth = 3.dp.toPx())
            drawLine(color = ColorInk, start = Offset(rightEyeCenter.x - 10.dp.toPx(), rightEyeCenter.y), end = Offset(rightEyeCenter.x + 10.dp.toPx(), rightEyeCenter.y), strokeWidth = 3.dp.toPx())
        }
        else -> {
            // Big button eyes with shiny white catchlights
            val eyeR = radius * 0.12f
            drawCircle(color = ColorInk, radius = eyeR, center = leftEyeCenter)
            drawCircle(color = ColorWhite, radius = eyeR * 0.35f, center = Offset(leftEyeCenter.x - eyeR * 0.3f, leftEyeCenter.y - eyeR * 0.3f))

            drawCircle(color = ColorInk, radius = eyeR, center = rightEyeCenter)
            drawCircle(color = ColorWhite, radius = eyeR * 0.35f, center = Offset(rightEyeCenter.x - eyeR * 0.3f, rightEyeCenter.y - eyeR * 0.3f))
        }
    }

    // Mouth Drawing based on expression
    val mouthCenter = Offset(center.x, center.y + radius * 0.22f)
    when (expression) {
        HeroExpression.FLYING, HeroExpression.STRETCHED -> {
            // Surprise / Joyful "O" mouth
            drawOval(
                color = ColorInk,
                topLeft = Offset(mouthCenter.x - 10.dp.toPx(), mouthCenter.y - 12.dp.toPx()),
                size = Size(20.dp.toPx(), 24.dp.toPx())
            )
            drawOval(
                color = ColorPink,
                topLeft = Offset(mouthCenter.x - 6.dp.toPx(), mouthCenter.y - 4.dp.toPx()),
                size = Size(12.dp.toPx(), 14.dp.toPx())
            )
        }
        HeroExpression.GIGGLE, HeroExpression.HAPPY, HeroExpression.PROUD -> {
            // Broad open happy smile
            val happyMouth = Path().apply {
                moveTo(mouthCenter.x - 16.dp.toPx(), mouthCenter.y - 2.dp.toPx())
                quadraticBezierTo(mouthCenter.x, mouthCenter.y + 18.dp.toPx(), mouthCenter.x + 16.dp.toPx(), mouthCenter.y - 2.dp.toPx())
                close()
            }
            drawPath(happyMouth, color = ColorInk)
            drawCircle(color = ColorPink, radius = 6.dp.toPx(), center = Offset(mouthCenter.x, mouthCenter.y + 6.dp.toPx()))
        }
        else -> {
            // Sweet gentle smile
            val smilePath = Path().apply {
                moveTo(mouthCenter.x - 14.dp.toPx(), mouthCenter.y)
                quadraticBezierTo(mouthCenter.x, mouthCenter.y + 12.dp.toPx(), mouthCenter.x + 14.dp.toPx(), mouthCenter.y)
            }
            drawPath(smilePath, color = ColorInk, style = inkStroke)
        }
    }
}

/**
 * Accessory emoji drawer on Canvas.
 */
private fun DrawScope.drawAccessoryEmoji(emoji: String, center: Offset, size: Float) {
    // Soft drop shadow
    drawCircle(
        color = ColorInk.copy(alpha = 0.15f),
        radius = size * 0.35f,
        center = Offset(center.x, center.y + 4.dp.toPx())
    )
}

/**
 * 🎁 Falling Gift Box Graphic.
 */
private fun DrawScope.drawGiftBoxGraphic(center: Offset, size: Float) {
    val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val half = size / 2f

    // Box body
    drawRoundRect(
        color = ColorPink,
        topLeft = Offset(center.x - half, center.y - half),
        size = Size(size, size),
        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
    )
    drawRoundRect(
        color = ColorInk,
        topLeft = Offset(center.x - half, center.y - half),
        size = Size(size, size),
        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
        style = inkStroke
    )

    // Ribbon (Yellow vertical & horizontal bands)
    val ribbonW = size * 0.22f
    drawRect(color = ColorYellow, topLeft = Offset(center.x - ribbonW / 2, center.y - half), size = Size(ribbonW, size))
    drawRect(color = ColorYellow, topLeft = Offset(center.x - half, center.y - ribbonW / 2), size = Size(size, ribbonW))

    // Bow on top
    drawCircle(color = ColorYellow, radius = 8.dp.toPx(), center = Offset(center.x, center.y - half))
    drawCircle(color = ColorInk, radius = 8.dp.toPx(), center = Offset(center.x, center.y - half), style = Stroke(width = 2.dp.toPx()))
}

/**
 * 🌟 Star Particle Graphic.
 */
private fun DrawScope.drawStarParticle(center: Offset, radius: Float, alpha: Float, color: Color) {
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
