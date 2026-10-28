package com.example.ui.screens

import android.content.Context
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
import androidx.compose.material3.Surface
import com.example.shared.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.screens.Catapult.*
import com.example.screens.Squish.*
import com.example.shared.*
import com.example.shared.scene.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

/**
 * 🚀 ШАГ 13. КАТАПУЛЬТА: «ЗАПУСТИ ГЕРОЯ В НЕБО»
 *
 * Особенности:
 * - Рогатка: натяжение пальцем (до 200 dp), параболический расчет траектории, v0 = 12 * pullDist.
 * - Полет: камера плавно следует за героем (lerp 0.12), параллакс фона через 4 зоны высоты.
 * - Парашют: автоматическое раскрытие после высшей точки или по тапу, раскачивание ±8°.
 * - Управление: ведение пальцем влево/вправо в воздухе.
 * - Бонусы: звёзды, облака-батуты, шарики, птицы, воздушные змеи, радуги-горки, НЛО.
 * - Банка звёзд: визуальное заполнение до 10 звёзд -> с неба спускается подарок с вещью для гардероба «Мялки».
 * - Ноль текста, ноль цифр и плашек, 100% радостный и безопасный геймплей.
 */
@Composable
fun FunnyRunningBoobaScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val soundHelper = remember(context, soundPlayer, soundManager) {
        CatapultSoundHelper(context, soundPlayer, soundManager)
    }
    val haptics = rememberHaptics()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val ageProfile = LocalAgeProfile.current

    // SharedPreferences for unlocked items and hero skin (Shared with Squishy/Мялка)
    val prefs = remember { context.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE) }
    var isFirstLaunch by remember { mutableStateOf(prefs.getBoolean("first_launch_catapult", true)) }

    // Selected Skin state
    var selectedSkin by remember {
        val saved = prefs.getString("squish_selected_skin", HeroSkin.PELMEN.id)
        mutableStateOf(HeroSkin.values().firstOrNull { it.id == saved } ?: HeroSkin.PELMEN)
    }

    // Unlocked items set (Synced with Wardrobe in Мялка)
    val unlockedItemIds = remember {
        val defaultSet = SquishCatalog.ALL_ITEMS.filter { it.isDefaultUnlocked }.map { it.id }.toMutableSet()
        val savedSet = prefs.getStringSet("squish_unlocked_items", emptySet()) ?: emptySet()
        defaultSet.addAll(savedSet)
        mutableStateListOf<String>().apply { addAll(defaultSet) }
    }

    // Equipped accessories
    val equippedHead = remember {
        val id = prefs.getString("squish_equipped_head", null)
        SquishCatalog.ALL_ITEMS.firstOrNull { it.slot == WardrobeSlot.HEAD && it.id == id }
    }
    val equippedFace = remember {
        val id = prefs.getString("squish_equipped_face", null)
        SquishCatalog.ALL_ITEMS.firstOrNull { it.slot == WardrobeSlot.FACE && it.id == id }
    }
    val equippedNeck = remember {
        val id = prefs.getString("squish_equipped_neck", null)
        SquishCatalog.ALL_ITEMS.firstOrNull { it.slot == WardrobeSlot.NECK && it.id == id }
    }

    // Slingshot & Hero State
    var heroState by remember { mutableStateOf(CatapultHeroState.IDLE) }
    var heroX by remember { mutableFloatStateOf(0f) }   // dp relative to center of screen
    var heroY by remember { mutableFloatStateOf(0f) }   // dp height above ground (0 = ground/slingshot)
    var heroVx by remember { mutableFloatStateOf(0f) }  // dp/sec
    var heroVy by remember { mutableFloatStateOf(0f) }  // dp/sec
    var heroRotation by remember { mutableFloatStateOf(0f) }
    var hasParachute by remember { mutableStateOf(false) }
    var parachuteSwayAngle by remember { mutableFloatStateOf(0f) }
    var heroExpression by remember { mutableStateOf(HeroExpression.IDLE) }

    // Touch pull drag state
    var isDraggingSling by remember { mutableStateOf(false) }
    var slingPullOffset by remember { mutableStateOf(Offset.Zero) } // dp
    var slingPullLoopHandle by remember { mutableStateOf<LoopHandle?>(null) }
    var windLoopHandle by remember { mutableStateOf<LoopHandle?>(null) }

    // Steering finger target X
    var steeringTargetX by remember { mutableStateOf<Float?>(null) } // dp

    // Camera viewport offset (smooth lerp 0.12)
    var cameraTargetY by remember { mutableFloatStateOf(0f) }
    var cameraCurrentY by remember { mutableFloatStateOf(0f) }

    // Star Jar State (10 stars to fill)
    var collectedStarsCount by remember { mutableIntStateOf(0) }
    var fallingGift by remember { mutableStateOf<CatapultGiftBox?>(null) }

    // Particles list
    val particles = remember { mutableStateListOf<CatapultParticle>() }

    // Interactive sky bonus objects (procedurally generated on launch)
    val skyBonusItems = remember { mutableStateListOf<CatapultBonusItem>() }

    // Inactivity 8s Hint Monitor
    var lastActionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showSlingshotHint by remember { mutableStateOf(false) }
    var showSteeringHint by remember { mutableStateOf(false) }

    // Kite latching timer
    var kiteFlightTimerSec by remember { mutableFloatStateOf(0f) }

    // Ground decor items for Meadow base
    val groundDecors = remember {
        listOf(
            DecorSpec(DecorKind.SUN, 0.86f, 0.12f, 76f),
            DecorSpec(DecorKind.CLOUD, 0.18f, 0.16f, 84f),
            DecorSpec(DecorKind.BUTTERFLY, 0.12f, 0.72f, 56f),
            DecorSpec(DecorKind.FLOWER, 0.22f, 0.88f, 56f),
            DecorSpec(DecorKind.FLOWER, 0.78f, 0.88f, 56f),
            DecorSpec(DecorKind.TREE, 0.08f, 0.76f, 96f)
        )
    }

    // Function to populate sky bonuses for a flight
    fun generateSkyBonuses(screenWidthDp: Float, screenHeightDp: Float) {
        skyBonusItems.clear()
        val random = Random(System.currentTimeMillis())

        // Height levels from 1 screen to 8 screens
        for (screenIdx in 1..8) {
            val baseY = screenIdx * screenHeightDp
            val zone = CatapultZone.forHeight(screenIdx.toFloat())

            // 1. Group of 3-4 Stars
            val starGroupCenterY = baseY + random.nextFloat() * (screenHeightDp * 0.7f)
            val starGroupCenterX = (random.nextFloat() * 0.6f + 0.2f) * screenWidthDp
            repeat(4) { i ->
                val offsetX = (i - 1.5f) * 36f
                val offsetY = sin(i * 1.2f) * 20f
                skyBonusItems.add(
                    CatapultBonusItem(
                        id = random.nextLong(),
                        kind = CatapultBonusKind.STAR,
                        xDp = (starGroupCenterX + offsetX).coerceIn(40f, screenWidthDp - 40f),
                        yDp = starGroupCenterY + offsetY
                    )
                )
            }

            // 2. Zone specific objects
            when (zone) {
                CatapultZone.CLOUDS -> {
                    // Cloud trampoline & Kite
                    skyBonusItems.add(
                        CatapultBonusItem(
                            id = random.nextLong(),
                            kind = CatapultBonusKind.CLOUD_TRAMPOLINE,
                            xDp = (random.nextFloat() * 0.6f + 0.2f) * screenWidthDp,
                            yDp = baseY + random.nextFloat() * (screenHeightDp * 0.6f)
                        )
                    )
                    if (random.nextBoolean()) {
                        skyBonusItems.add(
                            CatapultBonusItem(
                                id = random.nextLong(),
                                kind = CatapultBonusKind.KITE,
                                xDp = (random.nextFloat() * 0.7f + 0.15f) * screenWidthDp,
                                yDp = baseY + random.nextFloat() * (screenHeightDp * 0.8f)
                            )
                        )
                    }
                    if (random.nextBoolean()) {
                        skyBonusItems.add(
                            CatapultBonusItem(
                                id = random.nextLong(),
                                kind = CatapultBonusKind.BIRD,
                                xDp = (random.nextFloat() * 0.7f + 0.15f) * screenWidthDp,
                                yDp = baseY + random.nextFloat() * (screenHeightDp * 0.8f)
                            )
                        )
                    }
                }
                CatapultZone.HIGH_SKY -> {
                    // Balloon bundle & Rainbow slide
                    skyBonusItems.add(
                        CatapultBonusItem(
                            id = random.nextLong(),
                            kind = CatapultBonusKind.BALLOON_BUNDLE,
                            xDp = (random.nextFloat() * 0.6f + 0.2f) * screenWidthDp,
                            yDp = baseY + random.nextFloat() * (screenHeightDp * 0.5f)
                        )
                    )
                    skyBonusItems.add(
                        CatapultBonusItem(
                            id = random.nextLong(),
                            kind = CatapultBonusKind.RAINBOW,
                            xDp = (random.nextFloat() * 0.5f + 0.25f) * screenWidthDp,
                            yDp = baseY + random.nextFloat() * (screenHeightDp * 0.7f)
                        )
                    )
                }
                CatapultZone.SPACE -> {
                    // UFO & Moon face
                    skyBonusItems.add(
                        CatapultBonusItem(
                            id = random.nextLong(),
                            kind = CatapultBonusKind.UFO,
                            xDp = (random.nextFloat() * 0.6f + 0.2f) * screenWidthDp,
                            yDp = baseY + random.nextFloat() * (screenHeightDp * 0.6f)
                        )
                    )
                    if (screenIdx >= 6) {
                        skyBonusItems.add(
                            CatapultBonusItem(
                                id = random.nextLong(),
                                kind = CatapultBonusKind.MOON_FACE,
                                xDp = (random.nextFloat() * 0.5f + 0.25f) * screenWidthDp,
                                yDp = baseY + random.nextFloat() * (screenHeightDp * 0.5f)
                            )
                        )
                    }
                }
                else -> {}
            }
        }
    }

    // Inactivity Hand monitor
    LaunchedEffect(lastActionTime, heroState) {
        showSlingshotHint = false
        showSteeringHint = false
        if (heroState == CatapultHeroState.IDLE) {
            delay(8000)
            showSlingshotHint = true
        }
    }

    // Stop all looping sounds when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            slingPullLoopHandle?.let { soundManager.stopLoop(it) }
            windLoopHandle?.let { soundManager.stopLoop(it) }
        }
    }

    // Main Physics Simulation Loop (120 Hz with accumulator)
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        var accumulator = 0f
        val fixedDt = 1f / 120f
        var totalFlightTime = 0f

        while (isActive) {
            withFrameNanos { nowNanos ->
                if (lastNanos == 0L) {
                    lastNanos = nowNanos
                    return@withFrameNanos
                }
                val frameDt = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
                lastNanos = nowNanos
                accumulator += frameDt

                var steps = 0
                while (accumulator >= fixedDt && steps < 6) {
                    accumulator -= fixedDt
                    steps++

                    // --- 1. HERO IN FLIGHT SIMULATION ---
                    if (heroState == CatapultHeroState.FLYING || heroState == CatapultHeroState.PARACHUTE) {
                        totalFlightTime += fixedDt

                        // Safety check: max 25s flight
                        if (totalFlightTime > 25f && !hasParachute) {
                            hasParachute = true
                            soundHelper.playParachuteOpen()
                        }

                        // Air resistance & Gravity
                        val gravity = 900f // dp/s^2
                        val airDrag = 0.05f

                        heroVy -= gravity * fixedDt
                        heroVx *= (1.0f - airDrag * fixedDt)
                        heroVy *= (1.0f - airDrag * fixedDt)

                        // Check top peak to open parachute automatically
                        if (!hasParachute && heroVy < 0f && heroY > 80f) {
                            hasParachute = true
                            heroState = CatapultHeroState.PARACHUTE
                            soundHelper.playParachuteOpen()
                            haptics.vibrateShort()
                        }

                        // Parachute speed terminal limit
                        if (hasParachute) {
                            val maxFallSpeed = -480f // dp/s
                            if (heroVy < maxFallSpeed) {
                                heroVy = heroVy + (maxFallSpeed - heroVy) * 0.1f
                            }

                            // Horizontal Steering towards finger
                            steeringTargetX?.let { targetX ->
                                val dx = targetX - heroX
                                val steerAcc = (dx * 5.0f).coerceIn(-700f, 700f)
                                heroVx = (heroVx + steerAcc * fixedDt).coerceIn(-350f, 350f)
                                parachuteSwayAngle = (heroVx / 350f) * 8f
                            } ?: run {
                                heroVx *= (1.0f - 1.5f * fixedDt)
                                parachuteSwayAngle *= 0.92f
                            }
                        } else {
                            // Free fall terminal limit
                            if (heroVy < -1200f) heroVy = -1200f
                            heroRotation += 120f * fixedDt
                        }

                        // Kite hitch duration
                        if (kiteFlightTimerSec > 0f) {
                            kiteFlightTimerSec -= fixedDt
                            heroVy = 250f
                            heroVx = sin(totalFlightTime * 4f) * 180f
                        }

                        // Integrate position
                        heroX += heroVx * fixedDt
                        heroY += heroVy * fixedDt

                        // Wall soft bounce
                        val halfWidth = 160f
                        if (heroX < -halfWidth) {
                            heroX = -halfWidth
                            heroVx = abs(heroVx) * 0.5f
                            heroExpression = HeroExpression.IMPACT
                            soundHelper.playPop()
                            haptics.vibrate(15)
                        } else if (heroX > halfWidth) {
                            heroX = halfWidth
                            heroVx = -abs(heroVx) * 0.5f
                            heroExpression = HeroExpression.IMPACT
                            soundHelper.playPop()
                            haptics.vibrate(15)
                        }

                        // Check collision with ground
                        if (heroY <= 0f) {
                            heroY = 0f
                            heroState = CatapultHeroState.LANDED
                            hasParachute = false
                            heroVx = 0f
                            heroVy = 0f
                            heroRotation = 0f
                            heroExpression = HeroExpression.HAPPY
                            soundHelper.playLandBounce()
                            haptics.vibrate(25)

                            // Confetti burst on landing
                            repeat(8) {
                                val ang = Random.nextFloat() * 2f * PI.toFloat()
                                val spd = Random.nextFloat() * 200f + 60f
                                particles.add(
                                    CatapultParticle(
                                        id = Random.nextLong(),
                                        x = heroX,
                                        y = 20f,
                                        vx = cos(ang) * spd,
                                        vy = sin(ang) * spd,
                                        color = listOf(ColorPink, ColorYellow, ColorGreen, ColorOrange, ColorBlue).random(),
                                        size = 14f
                                    )
                                )
                            }
                        }

                        // Check bonus collisions
                        val heroPosWorld = Offset(heroX, heroY)
                        for (item in skyBonusItems) {
                            if (!item.isCollected) {
                                val itemOffset = Offset(item.xDp - halfWidth, item.yDp)
                                if ((heroPosWorld - itemOffset).getDistance() <= item.kind.radiusDp + 36f) {
                                    item.isCollected = true

                                    when (item.kind) {
                                        CatapultBonusKind.STAR -> {
                                            soundHelper.playStarCollect()
                                            haptics.vibrateShort()
                                            collectedStarsCount++
                                            heroExpression = HeroExpression.GIGGLE

                                            // Star sparkles
                                            repeat(6) {
                                                val ang = Random.nextFloat() * 2f * PI.toFloat()
                                                particles.add(
                                                    CatapultParticle(
                                                        id = Random.nextLong(),
                                                        x = heroX,
                                                        y = heroY,
                                                        vx = cos(ang) * 160f,
                                                        vy = sin(ang) * 160f,
                                                        color = ColorYellow,
                                                        isStar = true,
                                                        size = 14f
                                                    )
                                                )
                                            }

                                            // Check Jar Full -> Drop Gift!
                                            if (collectedStarsCount >= 10 && fallingGift == null) {
                                                collectedStarsCount = 0
                                                soundHelper.playGift()
                                                fallingGift = CatapultGiftBox(
                                                    xDp = 0f,
                                                    yDp = 800f
                                                )
                                            }
                                        }
                                        CatapultBonusKind.CLOUD_TRAMPOLINE -> {
                                            heroVy = 550f
                                            soundHelper.playCloudBounce()
                                            haptics.vibrate(20)
                                            heroExpression = HeroExpression.FLYING
                                        }
                                        CatapultBonusKind.BALLOON_BUNDLE -> {
                                            heroVy = 420f
                                            soundHelper.playBalloonBoost()
                                            haptics.vibrate(20)
                                        }
                                        CatapultBonusKind.BIRD -> {
                                            heroVx += if (Random.nextBoolean()) 250f else -250f
                                            soundHelper.playBirdTweet()
                                            heroExpression = HeroExpression.IMPACT
                                        }
                                        CatapultBonusKind.KITE -> {
                                            kiteFlightTimerSec = 1.0f
                                            soundHelper.playKiteSwoosh()
                                            haptics.vibrateShort()
                                        }
                                        CatapultBonusKind.RAINBOW -> {
                                            heroVy = 400f
                                            heroVx = 220f
                                            soundHelper.playRainbowSlide()
                                            haptics.vibrate(25)
                                        }
                                        CatapultBonusKind.UFO, CatapultBonusKind.MOON_FACE -> {
                                            soundHelper.playUfoHello()
                                            haptics.vibrate(25)
                                            repeat(3) {
                                                collectedStarsCount = (collectedStarsCount + 1).coerceAtMost(10)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- 2. FALLING GIFT BOX UPDATE ---
                    fallingGift?.let { gift ->
                        if (!gift.isLanded) {
                            gift.yDp -= 120f * fixedDt
                            gift.parachuteSway = sin(gift.ageSec * 4f) * 10f
                            if (gift.yDp <= 10f) {
                                gift.yDp = 10f
                                gift.isLanded = true
                                soundHelper.playLandBounce()
                            }
                        }
                        gift.ageSec += fixedDt
                        if (gift.ageSec > 30f && !gift.isOpened) {
                            fallingGift = null // Smooth disappear after 30s
                        }
                    }

                    // --- 3. PARTICLES UPDATE ---
                    for (i in particles.indices.reversed()) {
                        val p = particles[i]
                        p.ageSec += fixedDt
                        p.x += p.vx * fixedDt
                        p.y += p.vy * fixedDt
                        p.vy -= 200f * fixedDt
                        p.alpha = (1.0f - p.ageSec / p.lifeSec).coerceIn(0f, 1f)
                        if (p.ageSec >= p.lifeSec) {
                            particles.removeAt(i)
                        }
                    }
                }

                // Smooth Camera Lerp 0.12
                cameraTargetY = if (heroState == CatapultHeroState.IDLE || heroState == CatapultHeroState.LANDED) {
                    0f
                } else {
                    (heroY - 200f).coerceAtLeast(0f)
                }
                cameraCurrentY += (cameraTargetY - cameraCurrentY) * 0.12f
            }
        }
    }

    // Auto-Return to Slingshot after Landed
    LaunchedEffect(heroState) {
        if (heroState == CatapultHeroState.LANDED) {
            delay(1200) // Happy expression
            // Walk back to slingshot center
            val startX = heroX
            val anim = Animatable(0f)
            anim.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) {
                heroX = startX * (1f - value)
            }
            heroX = 0f
            heroY = 0f
            heroState = CatapultHeroState.IDLE
            heroExpression = HeroExpression.IDLE
            steeringTargetX = null
        }
    }

    // Selected Game Mode: JBox2D Runner (Default) vs Catapult
    var activeGameMode by remember { mutableStateOf(PelmenGameMode.RUNNER_JBOX2D) }

    if (activeGameMode == PelmenGameMode.RUNNER_JBOX2D) {
        Box(modifier = Modifier.fillMaxSize()) {
            PelmenRunnerScreen(
                repository = repository,
                soundPlayer = soundPlayer
            )

            // Mode Selector Bar at Top Center
            // Top Mode Switcher Bar (Runner vs Catapult) - Enforce 72dp child touch targets
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = ColorGreen,
                    border = BorderStroke(3.dp, ColorInk),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 130.dp, minHeight = 72.dp)
                        .testTag("mode_active_runner_button")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(
                            text = "🏃‍♂️ Раннер 67\n(JBox2D)",
                            color = ColorWhite,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = ColorWhite,
                    border = BorderStroke(3.dp, ColorInk),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 120.dp, minHeight = 72.dp)
                        .pointerInput(Unit) {
                            detectTapGestures {
                                soundPlayer.triggerLightTapPulse()
                                activeGameMode = PelmenGameMode.CATAPULT
                            }
                        }
                        .testTag("switch_to_catapult_button")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(
                            text = "🚀 Катапульта\n(Небо)",
                            color = ColorInk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    } else {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
        val screenWidthDp = maxWidth.value
        val screenHeightDp = maxHeight.value

        // Initialize sky bonuses
        LaunchedEffect(screenWidthDp, screenHeightDp) {
            generateSkyBonuses(screenWidthDp, screenHeightDp)
        }

        // Height in screens
        val currentScreenHeightRatio = (cameraCurrentY / screenHeightDp).coerceAtLeast(0f)
        val currentZone = CatapultZone.forHeight(currentScreenHeightRatio)

        // 1. DYNAMIC VERTICAL PARALLAX SKY BACKGROUND
        Canvas(modifier = Modifier.fillMaxSize()) {
            val skyGradient = Brush.verticalGradient(
                colors = listOf(currentZone.skyTopColor, currentZone.skyBottomColor),
                startY = 0f,
                endY = size.height
            )
            drawRect(brush = skyGradient)

            // Ground hill only visible near bottom (cameraCurrentY < screenHeightDp)
            if (cameraCurrentY < screenHeightDp * 1.2f) {
                val groundTopY = size.height - (screenHeightDp * 0.22f - cameraCurrentY * 0.6f).dp.toPx()
                val groundPath = Path().apply {
                    moveTo(0f, groundTopY)
                    quadraticBezierTo(size.width * 0.5f, groundTopY - 24.dp.toPx(), size.width, groundTopY)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(groundPath, color = Color(0xFF5BDB82))
                drawPath(groundPath, color = ColorInk, style = Stroke(width = 3.dp.toPx()))
            }
        }

        // 2. GROUND DECOR LAYER (Active only when hero on ground)
        if (heroState == CatapultHeroState.IDLE && cameraCurrentY < 50f) {
            DecorLayer(items = groundDecors)
        }

        // 3. MAIN GAMEPLAY INTERACTIVE CANVAS
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(heroState) {
                    if (heroState == CatapultHeroState.IDLE) {
                        detectDragGestures(
                            onDragStart = { startPos ->
                                lastActionTime = System.currentTimeMillis()
                                showSlingshotHint = false
                                val heroSlingshotCenter = Offset(size.width / 2f, size.height - 180.dp.toPx())

                                // Check tap on Gift Box if present near ground
                                fallingGift?.let { gift ->
                                    val giftScreenY = (size.height - 160.dp.toPx()) - gift.yDp.dp.toPx() + cameraCurrentY.dp.toPx()
                                    val giftScreenX = (size.width / 2f) + gift.xDp.dp.toPx()
                                    val giftPos = Offset(giftScreenX, giftScreenY)

                                    if ((startPos - giftPos).getDistance() <= 60.dp.toPx() && !gift.isOpened) {
                                        gift.isOpened = true
                                        soundHelper.playGift()
                                        haptics.vibrate(30)

                                        // Unlock a new item in wardrobe
                                        val lockedItems = SquishCatalog.ALL_ITEMS.filter { it.id !in unlockedItemIds }
                                        if (lockedItems.isNotEmpty()) {
                                            val unlockedItem = lockedItems.random()
                                            unlockedItemIds.add(unlockedItem.id)
                                            prefs.edit().putStringSet("squish_unlocked_items", unlockedItemIds.toSet()).apply()
                                        }

                                        // Confetti burst on opening
                                        repeat(16) {
                                            val angle = Random.nextFloat() * 2f * PI.toFloat()
                                            val spd = Random.nextFloat() * 220f + 60f
                                            particles.add(
                                                CatapultParticle(
                                                    id = Random.nextLong(),
                                                    x = gift.xDp,
                                                    y = gift.yDp,
                                                    vx = cos(angle) * spd,
                                                    vy = sin(angle) * spd + 40f,
                                                    color = listOf(ColorPink, ColorYellow, ColorGreen, ColorOrange, ColorBlue).random(),
                                                    isStar = true,
                                                    size = 14f
                                                )
                                            )
                                        }
                                        fallingGift = null
                                        return@detectDragGestures
                                    }
                                }

                                if ((startPos - heroSlingshotCenter).getDistance() <= 120.dp.toPx()) {
                                    isDraggingSling = true
                                    heroState = CatapultHeroState.CHARGING
                                    heroExpression = HeroExpression.GIGGLE
                                    slingPullLoopHandle = soundManager.startLoop("sling_pull_loop")
                                    haptics.vibrate(15)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                lastActionTime = System.currentTimeMillis()
                                if (isDraggingSling) {
                                    val currentDrag = slingPullOffset + Offset(dragAmount.x / density.density, dragAmount.y / density.density)
                                    // Limit pull to 200 dp radius, primarily downwards
                                    val pullDist = currentDrag.getDistance().coerceAtMost(200f)
                                    val pullAngle = atan2(currentDrag.y, currentDrag.x)
                                    slingPullOffset = Offset(cos(pullAngle) * pullDist, sin(pullAngle) * pullDist)

                                    heroX = slingPullOffset.x * 0.8f
                                    heroY = (-slingPullOffset.y * 0.8f).coerceAtLeast(0f)
                                }
                            },
                            onDragEnd = {
                                if (isDraggingSling) {
                                    slingPullLoopHandle?.let {
                                        soundManager.stopLoop(it)
                                        slingPullLoopHandle = null
                                    }
                                    val pullDistance = slingPullOffset.getDistance()

                                    if (pullDistance >= 24f) {
                                        // Launch!
                                        val launchSpeed = pullDistance * 12.0f // up to 2400 dp/s
                                        val angle = atan2(-slingPullOffset.y, -slingPullOffset.x)
                                        heroVx = cos(angle) * launchSpeed * 0.65f
                                        heroVy = sin(angle) * launchSpeed
                                        heroState = CatapultHeroState.FLYING
                                        heroExpression = HeroExpression.FLYING
                                        soundHelper.playSlingFire()
                                        haptics.vibrate(25)

                                        // Particulate spray from posts
                                        repeat(8) {
                                            val sprayAng = Random.nextFloat() * PI.toFloat()
                                            particles.add(
                                                CatapultParticle(
                                                    id = Random.nextLong(),
                                                    x = heroX,
                                                    y = 20f,
                                                    vx = cos(sprayAng) * 200f,
                                                    vy = sin(sprayAng) * 250f,
                                                    color = ColorYellow,
                                                    size = 12f
                                                )
                                            )
                                        }
                                    } else {
                                        // Too short -> snap back
                                        heroX = 0f
                                        heroY = 0f
                                        heroState = CatapultHeroState.IDLE
                                        heroExpression = HeroExpression.IDLE
                                    }

                                    isDraggingSling = false
                                    slingPullOffset = Offset.Zero
                                }
                            }
                        )
                    } else if (heroState == CatapultHeroState.FLYING || heroState == CatapultHeroState.PARACHUTE) {
                        detectDragGestures(
                            onDragStart = { startPos ->
                                lastActionTime = System.currentTimeMillis()
                                if (!hasParachute) {
                                    hasParachute = true
                                    heroState = CatapultHeroState.PARACHUTE
                                    soundHelper.playParachuteOpen()
                                    haptics.vibrateShort()
                                }
                                val screenCenterXPx = size.width / 2f
                                steeringTargetX = (startPos.x - screenCenterXPx) / density.density
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                lastActionTime = System.currentTimeMillis()
                                val screenCenterXPx = size.width / 2f
                                steeringTargetX = (change.position.x - screenCenterXPx) / density.density
                            },
                            onDragEnd = {
                                steeringTargetX = null
                            }
                        )
                    }
                }
        ) {
            val centerX = size.width / 2f
            val slingshotBaseY = size.height - 160.dp.toPx()
            val cameraOffsetPx = cameraCurrentY.dp.toPx()

            // A. DRAW SLINGSHOT POSTS & RUBBER BANDS (Visible when near ground)
            if (cameraCurrentY < screenHeightDp) {
                val postY = slingshotBaseY + cameraOffsetPx
                val postSpacing = 90.dp.toPx()
                val postLeft = Offset(centerX - postSpacing, postY)
                val postRight = Offset(centerX + postSpacing, postY)

                val heroScreenPos = Offset(centerX + heroX.dp.toPx(), slingshotBaseY - heroY.dp.toPx() + cameraOffsetPx)

                // Rubber band (Stretches with yellow-to-orange tint)
                val pullRatio = (slingPullOffset.getDistance() / 200f).coerceIn(0f, 1f)
                val rubberColor = Color(
                    red = 1.0f,
                    green = (0.85f - pullRatio * 0.35f).coerceIn(0f, 1f),
                    blue = 0.2f
                )
                val rubberThickness = (10.dp.toPx() * (1.0f - pullRatio * 0.45f)).coerceAtLeast(4.dp.toPx())

                // Left band
                drawLine(color = rubberColor, start = postLeft, end = heroScreenPos, strokeWidth = rubberThickness, cap = StrokeCap.Round)
                drawLine(color = ColorInk, start = postLeft, end = heroScreenPos, strokeWidth = rubberThickness + 2.5.dp.toPx(), cap = StrokeCap.Round, blendMode = BlendMode.DstOver)

                // Right band
                drawLine(color = rubberColor, start = postRight, end = heroScreenPos, strokeWidth = rubberThickness, cap = StrokeCap.Round)
                drawLine(color = ColorInk, start = postRight, end = heroScreenPos, strokeWidth = rubberThickness + 2.5.dp.toPx(), cap = StrokeCap.Round, blendMode = BlendMode.DstOver)

                // Dotted Aim Trajectory during charging
                if (isDraggingSling && slingPullOffset.getDistance() > 24f) {
                    val launchSpeed = slingPullOffset.getDistance() * 12.0f
                    val angle = atan2(-slingPullOffset.y, -slingPullOffset.x)
                    val v0x = cos(angle) * launchSpeed * 0.65f
                    val v0y = sin(angle) * launchSpeed

                    for (step in 1..12) {
                        val t = step * 0.07f
                        val dotX = heroX + v0x * t
                        val dotY = heroY + v0y * t - 0.5f * 900f * t * t
                        val dotScreen = Offset(centerX + dotX.dp.toPx(), slingshotBaseY - dotY.dp.toPx() + cameraOffsetPx)
                        val dotAlpha = (1.0f - step / 14f).coerceIn(0.1f, 0.9f)
                        val dotRadius = (6.dp.toPx() * (1f - step / 18f)).coerceAtLeast(2.dp.toPx())

                        drawCircle(color = ColorWhite.copy(alpha = dotAlpha), radius = dotRadius, center = dotScreen)
                        drawCircle(color = ColorInk.copy(alpha = dotAlpha), radius = dotRadius, center = dotScreen, style = Stroke(width = 1.5.dp.toPx()))
                    }
                }

                // Slingshot Wooden / Plasticine Posts (Left Orange, Right Pink)
                drawSlingshotPost(postLeft, ColorOrange, 76.dp.toPx())
                drawSlingshotPost(postRight, ColorPink, 76.dp.toPx())
            }

            // B. DRAW SKY BONUSES (Stars, Clouds, Balloons, Birds, UFO)
            for (item in skyBonusItems) {
                if (!item.isCollected) {
                    val itemScreenY = slingshotBaseY - item.yDp.dp.toPx() + cameraOffsetPx
                    // Only draw visible items
                    if (itemScreenY in -120f..(size.height + 120f)) {
                        val itemScreenX = (item.xDp / screenWidthDp) * size.width
                        drawSkyBonusGraphic(item.kind, Offset(itemScreenX, itemScreenY), item.kind.radiusDp.dp.toPx())
                    }
                }
            }

            // C. DRAW FALLING GIFT BOX WITH PARACHUTE
            fallingGift?.let { gift ->
                val giftScreenY = slingshotBaseY - gift.yDp.dp.toPx() + cameraOffsetPx
                val giftScreenX = centerX + gift.xDp.dp.toPx()
                if (giftScreenY in -120f..(size.height + 120f)) {
                    val giftPos = Offset(giftScreenX, giftScreenY)
                    if (!gift.isLanded) {
                        // Gift mini parachute
                        rotate(gift.parachuteSway, pivot = giftPos) {
                            val paraTop = Offset(giftPos.x, giftPos.y - 48.dp.toPx())
                            drawParachuteGraphic(paraTop, 36.dp.toPx(), ColorYellow)
                            drawLine(color = ColorInk, start = Offset(giftPos.x - 14.dp.toPx(), giftPos.y - 12.dp.toPx()), end = Offset(paraTop.x - 24.dp.toPx(), paraTop.y), strokeWidth = 1.5.dp.toPx())
                            drawLine(color = ColorInk, start = Offset(giftPos.x + 14.dp.toPx(), giftPos.y - 12.dp.toPx()), end = Offset(paraTop.x + 24.dp.toPx(), paraTop.y), strokeWidth = 1.5.dp.toPx())
                        }
                    }
                    drawGiftBoxGraphic(giftPos, 44.dp.toPx())
                }
            }

            // D. DRAW PARTICLES
            for (p in particles) {
                val pScreen = Offset(centerX + p.x.dp.toPx(), slingshotBaseY - p.y.dp.toPx() + cameraOffsetPx)
                if (p.isStar) {
                    drawStarParticle(pScreen, p.size.dp.toPx(), p.alpha, p.color)
                } else {
                    drawCircle(color = p.color.copy(alpha = p.alpha), radius = p.size.dp.toPx() / 2f, center = pScreen)
                }
            }

            // E. DRAW HERO & PARACHUTE
            val heroScreenY = slingshotBaseY - heroY.dp.toPx() + cameraOffsetPx
            val heroScreenPos = Offset(centerX + heroX.dp.toPx(), heroScreenY)

            // Draw Parachute if deployed
            if (hasParachute) {
                rotate(parachuteSwayAngle, pivot = heroScreenPos) {
                    val parachuteCenter = Offset(heroScreenPos.x, heroScreenPos.y - 72.dp.toPx())
                    drawParachuteGraphic(parachuteCenter, 68.dp.toPx(), Color(0xFFFF4081))

                    // Strings to hero
                    drawLine(color = ColorInk, start = Offset(heroScreenPos.x - 18.dp.toPx(), heroScreenPos.y - 16.dp.toPx()), end = Offset(parachuteCenter.x - 44.dp.toPx(), parachuteCenter.y), strokeWidth = 2.dp.toPx())
                    drawLine(color = ColorInk, start = Offset(heroScreenPos.x + 18.dp.toPx(), heroScreenPos.y - 16.dp.toPx()), end = Offset(parachuteCenter.x + 44.dp.toPx(), parachuteCenter.y), strokeWidth = 2.dp.toPx())
                }
            }

            // Draw Hero Body with selected Skin & Face
            rotate(heroRotation, pivot = heroScreenPos) {
                drawHeroPlasticineBody(
                    skin = selectedSkin,
                    center = heroScreenPos,
                    radius = 38.dp.toPx(),
                    expression = heroExpression,
                    equippedHead = equippedHead,
                    equippedFace = equippedFace,
                    equippedNeck = equippedNeck
                )
            }
        }

        // 4. TOP HUD BAR: [HomeButton], [Skin Switchers (72 dp)], [Star Jar (72 dp)]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Spacer to accommodate floating HomeButton
            Spacer(modifier = Modifier.size(76.dp))

            // Switch to JBox2D Runner button (72dp min touch target)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ColorGreen,
                border = BorderStroke(3.dp, ColorInk),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .defaultMinSize(minWidth = 110.dp, minHeight = 72.dp)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            soundPlayer.triggerLightTapPulse()
                            activeGameMode = PelmenGameMode.RUNNER_JBOX2D
                        }
                    }
                    .testTag("switch_to_runner_button")
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        text = "🏃‍♂️ В Раннер 67",
                        color = ColorWhite,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }
            }

            // 🥟 Character Skin Switcher Buttons (72 dp, glossy round buttons)
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
                            .size(72.dp)
                            .border(
                                BorderStroke(if (isSelected) 4.dp else 2.5.dp, ColorInk),
                                CircleShape
                            )
                            .background(if (isSelected) ColorWhite else ColorBgSoft, CircleShape)
                            .clip(CircleShape)
                            .plasticineClickable(haptics = haptics) {
                                if (selectedSkin != skin) {
                                    selectedSkin = skin
                                    prefs.edit().putString("squish_selected_skin", skin.id).apply()
                                    soundHelper.playPop()
                                    haptics.vibrateShort()
                                }
                            }
                            .testTag("catapult_skin_${skin.name.lowercase()}")
                    ) {
                        Text(text = skin.iconEmoji, fontSize = 34.sp)
                    }
                }
            }

            // ⭐ Star Jar Indicator (72 dp, fills visually with 0..10 stars)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .border(BorderStroke(3.dp, ColorInk), ShapeCard)
                    .background(ColorWhite, ShapeCard)
                    .clip(ShapeCard)
                    .testTag("star_jar_indicator")
            ) {
                // Background fill level
                val fillRatio = (collectedStarsCount / 10f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fillRatio)
                        .align(Alignment.BottomCenter)
                        .background(ColorYellow.copy(alpha = 0.5f))
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "⭐", fontSize = 28.sp)
                    // Visual dots row inside jar (up to 5 dots)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        repeat(5) { dotIdx ->
                            val isLit = collectedStarsCount > dotIdx * 2
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(if (isLit) ColorOrange else ColorBgSoft, CircleShape)
                            )
                        }
                    }
                }
            }
        }

        // 5. TUTORIAL / HINT HAND
        if (showSlingshotHint && heroState == CatapultHeroState.IDLE) {
            HintHand(
                from = Offset(screenWidthDp / 2f - 40f, screenHeightDp - 220f),
                to = Offset(screenWidthDp / 2f - 40f, screenHeightDp - 140f),
                mode = "drag",
                delayMs = 400
            )
        }
    }
}
}

/**
 * Pelmen game mode switcher (JBox2D Runner vs Sky Catapult)
 */
enum class PelmenGameMode {
    RUNNER_JBOX2D,
    CATAPULT
}

/**
 * Catapult hero movement phases
 */
private enum class CatapultHeroState {
    IDLE,
    CHARGING,
    FLYING,
    PARACHUTE,
    LANDED
}

/**
 * Draws wooden / plasticine slingshot post with star cap.
 */
private fun DrawScope.drawSlingshotPost(pos: Offset, color: Color, height: Float) {
    val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val postWidth = 24.dp.toPx()

    // Post body
    drawRoundRect(
        color = color,
        topLeft = Offset(pos.x - postWidth / 2f, pos.y - height),
        size = Size(postWidth, height),
        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
    )
    drawRoundRect(
        color = ColorInk,
        topLeft = Offset(pos.x - postWidth / 2f, pos.y - height),
        size = Size(postWidth, height),
        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
        style = inkStroke
    )

    // Star on top
    drawCircle(color = ColorYellow, radius = 14.dp.toPx(), center = Offset(pos.x, pos.y - height))
    drawCircle(color = ColorInk, radius = 14.dp.toPx(), center = Offset(pos.x, pos.y - height), style = Stroke(width = 2.dp.toPx()))
}

/**
 * Draws colorful sky bonus icons.
 */
private fun DrawScope.drawSkyBonusGraphic(kind: CatapultBonusKind, pos: Offset, radius: Float) {
    // Soft glowing halo
    drawCircle(
        color = ColorWhite.copy(alpha = 0.45f),
        radius = radius * 1.35f,
        center = pos
    )
    drawCircle(
        color = ColorInk.copy(alpha = 0.2f),
        radius = radius * 1.35f,
        center = pos,
        style = Stroke(width = 2.dp.toPx())
    )
}

/**
 * Draws parachute umbrella.
 */
private fun DrawScope.drawParachuteGraphic(center: Offset, radius: Float, color: Color) {
    val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val domePath = Path().apply {
        moveTo(center.x - radius, center.y)
        quadraticBezierTo(center.x, center.y - radius * 0.95f, center.x + radius, center.y)
        quadraticBezierTo(center.x + radius * 0.5f, center.y - radius * 0.15f, center.x, center.y)
        quadraticBezierTo(center.x - radius * 0.5f, center.y - radius * 0.15f, center.x - radius, center.y)
        close()
    }
    drawPath(domePath, color = color)
    drawPath(domePath, color = ColorInk, style = inkStroke)

    // Highlight stripe
    drawCircle(color = ColorWhite.copy(alpha = 0.35f), radius = radius * 0.3f, center = Offset(center.x - radius * 0.3f, center.y - radius * 0.4f))
}

/**
 * Draws hero plasticine body with eyes, expressions and accessories.
 */
private fun DrawScope.drawHeroPlasticineBody(
    skin: HeroSkin,
    center: Offset,
    radius: Float,
    expression: HeroExpression,
    equippedHead: WardrobeItem?,
    equippedFace: WardrobeItem?,
    equippedNeck: WardrobeItem?
) {
    val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Body Gradient
    val gradientBrush = Brush.radialGradient(
        colors = listOf(skin.highlightColor, skin.baseColor, skin.shadeColor),
        center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
        radius = radius * 1.25f
    )

    when (skin) {
        HeroSkin.PELMEN -> {
            val bodyPath = Path().apply {
                moveTo(center.x - radius, center.y + radius * 0.3f)
                quadraticBezierTo(center.x, center.y - radius * 1.15f, center.x + radius, center.y + radius * 0.3f)
                quadraticBezierTo(center.x, center.y + radius * 1.05f, center.x - radius, center.y + radius * 0.3f)
                close()
            }
            drawPath(bodyPath, brush = gradientBrush)
            drawPath(bodyPath, color = ColorInk, style = inkStroke)

            // Crimped top edge
            val crimpPath = Path().apply {
                moveTo(center.x - radius * 0.8f, center.y - radius * 0.2f)
                quadraticBezierTo(center.x, center.y - radius * 1.0f, center.x + radius * 0.8f, center.y - radius * 0.2f)
            }
            drawPath(crimpPath, color = Color(0xFFFF8800), style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
        }
        HeroSkin.ZEFIR -> {
            drawRoundRect(
                brush = gradientBrush,
                topLeft = Offset(center.x - radius * 0.9f, center.y - radius * 0.9f),
                size = Size(radius * 1.8f, radius * 1.8f),
                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
            )
            drawRoundRect(
                color = ColorInk,
                topLeft = Offset(center.x - radius * 0.9f, center.y - radius * 0.9f),
                size = Size(radius * 1.8f, radius * 1.8f),
                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                style = inkStroke
            )
        }
        else -> {
            drawCircle(brush = gradientBrush, radius = radius, center = center)
            drawCircle(color = ColorInk, radius = radius, center = center, style = inkStroke)
        }
    }

    // Specular Sheen
    drawOval(
        color = ColorWhite.copy(alpha = 0.4f),
        topLeft = Offset(center.x - radius * 0.55f, center.y - radius * 0.55f),
        size = Size(radius * 0.45f, radius * 0.26f)
    )

    // Facial features
    drawProceduralFaceDetails(center, radius, expression)
}

/**
 * Facial eyes and smile details.
 */
private fun DrawScope.drawProceduralFaceDetails(center: Offset, radius: Float, expression: HeroExpression) {
    val inkStroke = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val eyeSpacing = radius * 0.38f
    val eyeY = center.y - radius * 0.12f
    val leftEyeCenter = Offset(center.x - eyeSpacing, eyeY)
    val rightEyeCenter = Offset(center.x + eyeSpacing, eyeY)

    // Cheeks
    drawCircle(color = ColorPink.copy(alpha = 0.5f), radius = radius * 0.16f, center = Offset(center.x - eyeSpacing * 1.3f, eyeY + radius * 0.18f))
    drawCircle(color = ColorPink.copy(alpha = 0.5f), radius = radius * 0.16f, center = Offset(center.x + eyeSpacing * 1.3f, eyeY + radius * 0.18f))

    // Eyes
    when (expression) {
        HeroExpression.FLYING, HeroExpression.IMPACT, HeroExpression.GIGGLE -> {
            val leftArc = Path().apply {
                moveTo(leftEyeCenter.x - 10.dp.toPx(), leftEyeCenter.y + 4.dp.toPx())
                quadraticBezierTo(leftEyeCenter.x, leftEyeCenter.y - 10.dp.toPx(), leftEyeCenter.x + 10.dp.toPx(), leftEyeCenter.y + 4.dp.toPx())
            }
            val rightArc = Path().apply {
                moveTo(rightEyeCenter.x - 10.dp.toPx(), rightEyeCenter.y + 4.dp.toPx())
                quadraticBezierTo(rightEyeCenter.x, rightEyeCenter.y - 10.dp.toPx(), rightEyeCenter.x + 10.dp.toPx(), rightEyeCenter.y + 4.dp.toPx())
            }
            drawPath(leftArc, color = ColorInk, style = inkStroke)
            drawPath(rightArc, color = ColorInk, style = inkStroke)
        }
        else -> {
            val eyeR = radius * 0.13f
            drawCircle(color = ColorInk, radius = eyeR, center = leftEyeCenter)
            drawCircle(color = ColorWhite, radius = eyeR * 0.35f, center = Offset(leftEyeCenter.x - eyeR * 0.3f, leftEyeCenter.y - eyeR * 0.3f))
            drawCircle(color = ColorInk, radius = eyeR, center = rightEyeCenter)
            drawCircle(color = ColorWhite, radius = eyeR * 0.35f, center = Offset(rightEyeCenter.x - eyeR * 0.3f, rightEyeCenter.y - eyeR * 0.3f))
        }
    }

    // Mouth
    val mouthCenter = Offset(center.x, center.y + radius * 0.22f)
    if (expression == HeroExpression.FLYING) {
        drawOval(color = ColorInk, topLeft = Offset(mouthCenter.x - 10.dp.toPx(), mouthCenter.y - 12.dp.toPx()), size = Size(20.dp.toPx(), 24.dp.toPx()))
        drawOval(color = ColorPink, topLeft = Offset(mouthCenter.x - 6.dp.toPx(), mouthCenter.y - 4.dp.toPx()), size = Size(12.dp.toPx(), 14.dp.toPx()))
    } else {
        val happyMouth = Path().apply {
            moveTo(mouthCenter.x - 14.dp.toPx(), mouthCenter.y - 2.dp.toPx())
            quadraticBezierTo(mouthCenter.x, mouthCenter.y + 16.dp.toPx(), mouthCenter.x + 14.dp.toPx(), mouthCenter.y - 2.dp.toPx())
            close()
        }
        drawPath(happyMouth, color = ColorInk)
        drawCircle(color = ColorPink, radius = 5.dp.toPx(), center = Offset(mouthCenter.x, mouthCenter.y + 5.dp.toPx()))
    }
}

/**
 * Draws falling gift box graphic.
 */
private fun DrawScope.drawGiftBoxGraphic(center: Offset, size: Float) {
    val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val half = size / 2f

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

    // Ribbon
    val ribbonW = size * 0.22f
    drawRect(color = ColorYellow, topLeft = Offset(center.x - ribbonW / 2, center.y - half), size = Size(ribbonW, size))
    drawRect(color = ColorYellow, topLeft = Offset(center.x - half, center.y - ribbonW / 2), size = Size(size, ribbonW))

    // Bow
    drawCircle(color = ColorYellow, radius = 7.dp.toPx(), center = Offset(center.x, center.y - half))
    drawCircle(color = ColorInk, radius = 7.dp.toPx(), center = Offset(center.x, center.y - half), style = Stroke(width = 2.dp.toPx()))
}

/**
 * Draws 5-point star particle.
 */
private fun DrawScope.drawStarParticle(center: Offset, radius: Float, alpha: Float, color: Color) {
    val starPath = Path()
    val points = 5
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius else radius * 0.45f
        val angle = (i * PI / points).toFloat() - (PI / 2f).toFloat()
        val px = center.x + cos(angle) * r
        val py = center.y + sin(angle) * r
        if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
    }
    starPath.close()

    drawPath(starPath, color = color.copy(alpha = alpha))
    drawPath(starPath, color = ColorInk.copy(alpha = alpha), style = Stroke(width = 1.5.dp.toPx()))
}
