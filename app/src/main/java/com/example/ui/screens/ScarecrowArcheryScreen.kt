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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.screens.Archery.*
import com.example.shared.*
import com.example.shared.scene.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

@Composable
fun ScarecrowArcheryScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager(context, soundPlayer) }
    val soundHelper = remember(context, soundPlayer, soundManager) {
        ArcherySoundHelper(context, soundPlayer, soundManager)
    }
    val haptics = rememberHaptics()
    val coroutineScope = rememberCoroutineScope()

    // Preferences for first-launch hint
    val prefs = remember { context.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE) }
    var isFirstLaunch by remember { mutableStateOf(prefs.getBoolean("first_launch_archery", true)) }

    // Selected Arrow State
    var selectedArrowType by remember { mutableStateOf(ArrowType.NORMAL) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showIdleHint by remember { mutableStateOf(false) }

    // Big Pop-out Badge Animation (150 dp in center on arrow selection)
    var bigBadgeArrow by remember { mutableStateOf<ArrowType?>(null) }
    val badgeScale = remember { Animatable(0f) }
    val badgeAlpha = remember { Animatable(0f) }

    // Hit dots counter (0 to 7)
    var hitDotsCount by remember { mutableIntStateOf(0) }

    // Scarecrow Animations
    val scarecrowIdleTransition = rememberInfiniteTransition(label = "scarecrowIdle")
    val idleSwayDeg by scarecrowIdleTransition.animateFloat(
        initialValue = -2.2f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleSway"
    )

    val scarecrowWobbleAngle = remember { Animatable(0f) }
    val scarecrowHatOffsetY = remember { Animatable(0f) }
    val scarecrowEyesScale = remember { Animatable(1.0f) }
    val scarecrowLeftArmWave = remember { Animatable(0f) }
    val scarecrowRightArmWave = remember { Animatable(0f) }
    val scarecrowSpinAngle = remember { Animatable(0f) }

    // Bow Drag / Slingshot State
    var bowCenterPx by remember { mutableStateOf(Offset.Zero) }
    var bowWidthPx by remember { mutableFloatStateOf(0f) }
    var isDraggingBow by remember { mutableStateOf(false) }
    var bowDragOffsetPx by remember { mutableStateOf(Offset.Zero) } // Relative to bow center

    // Arrow Physics State
    var activeFlyingArrow by remember { mutableStateOf<FlyingArrow?>(null) }
    val stuckArrows = remember { mutableStateListOf<StuckArrow>() }
    val groundArrows = remember { mutableStateListOf<StuckArrow>() }

    // Particle Systems (Max 20 particles pool)
    val particles = remember { mutableStateListOf<ArcheryParticle>() }
    val confettiList = remember { mutableStateListOf<ArcheryConfetti>() }

    // Scarecrow Visual Center and Bounds
    var scarecrowCenterPx by remember { mutableStateOf(Offset.Zero) }
    var scarecrowScale by remember { mutableFloatStateOf(1f) }

    // Inactivity 6s hint monitor after changing arrow
    LaunchedEffect(lastInteractionTime) {
        showIdleHint = false
        delay(6000)
        showIdleHint = true
    }

    // Physics Simulation Loop (Frame Rate Sync)
    LaunchedEffect(Unit) {
        var lastTimeNanos = 0L
        while (isActive) {
            withFrameNanos { nowNanos ->
                if (lastTimeNanos == 0L) {
                    lastTimeNanos = nowNanos
                    return@withFrameNanos
                }
                val dt = ((nowNanos - lastTimeNanos) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
                lastTimeNanos = nowNanos

                // 1. Update flying arrow
                activeFlyingArrow?.let { arrow ->
                    arrow.ageSec += dt

                    // Soft Guidance towards scarecrow center if trajectory passes near
                    if (scarecrowCenterPx != Offset.Zero) {
                        val toTarget = scarecrowCenterPx - arrow.position
                        val distToCenter = toTarget.getDistance()
                        if (distToCenter < 180f && !arrow.isAssisted) {
                            arrow.isAssisted = true
                            val correction = toTarget * 0.35f * dt * 4f
                            arrow.velocity = Offset(arrow.velocity.x + correction.x, arrow.velocity.y + correction.y)
                        }
                    }

                    // Parabolic Gravity (1400 dp/s²)
                    val gravityPx = 1400f * 2.2f // scaled to screen density
                    arrow.velocity = Offset(
                        arrow.velocity.x,
                        arrow.velocity.y + gravityPx * dt
                    )
                    arrow.position = Offset(
                        arrow.position.x + arrow.velocity.x * dt,
                        arrow.position.y + arrow.velocity.y * dt
                    )
                    arrow.angleDeg = (atan2(arrow.velocity.y, arrow.velocity.x) * 180f / PI.toFloat())

                    // Collision Check with Scarecrow
                    if (scarecrowCenterPx != Offset.Zero) {
                        val dx = arrow.position.x - scarecrowCenterPx.x
                        val dy = arrow.position.y - scarecrowCenterPx.y

                        // Generous 120% Hitbox zones
                        val headRadius = 75f * scarecrowScale
                        val bodyHalfW = 65f * scarecrowScale
                        val bodyHalfH = 95f * scarecrowScale
                        val armLength = 115f * scarecrowScale
                        val armThickness = 45f * scarecrowScale

                        var hitZone: HitZone? = null

                        if (sqrt(dx * dx + (dy + 90f * scarecrowScale) * (dy + 90f * scarecrowScale)) <= headRadius) {
                            hitZone = HitZone.HEAD
                        } else if (abs(dx) <= bodyHalfW && dy in (-20f * scarecrowScale)..(130f * scarecrowScale)) {
                            hitZone = HitZone.BODY
                        } else if (dx in (-bodyHalfW - armLength)..(-bodyHalfW) && abs(dy - 10f * scarecrowScale) <= armThickness) {
                            hitZone = HitZone.LEFT_ARM
                        } else if (dx in (bodyHalfW)..(bodyHalfW + armLength) && abs(dy - 10f * scarecrowScale) <= armThickness) {
                            hitZone = HitZone.RIGHT_ARM
                        }

                        if (hitZone != null) {
                            // Hit registered!
                            soundHelper.playHit(arrow.type)
                            haptics.vibrate(25)

                            // Hit reaction animation
                            coroutineScope.launch {
                                when (hitZone) {
                                    HitZone.HEAD -> {
                                        soundHelper.playScarecrowGiggle()
                                        launch {
                                            scarecrowHatOffsetY.animateTo(-45f, tween(150, easing = FastOutSlowInEasing))
                                            scarecrowHatOffsetY.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
                                        }
                                        launch {
                                            scarecrowEyesScale.animateTo(1.4f, tween(150))
                                            scarecrowEyesScale.animateTo(1.0f, tween(250))
                                        }
                                    }
                                    HitZone.BODY -> {
                                        soundHelper.playWobble()
                                        scarecrowWobbleAngle.animateTo(7f, tween(120))
                                        scarecrowWobbleAngle.animateTo(-7f, tween(150))
                                        scarecrowWobbleAngle.animateTo(4f, tween(150))
                                        scarecrowWobbleAngle.animateTo(0f, tween(180))
                                    }
                                    HitZone.LEFT_ARM -> {
                                        soundHelper.playWobble()
                                        scarecrowLeftArmWave.animateTo(18f, tween(120))
                                        scarecrowLeftArmWave.animateTo(-12f, tween(150))
                                        scarecrowLeftArmWave.animateTo(0f, tween(180))
                                    }
                                    HitZone.RIGHT_ARM -> {
                                        soundHelper.playWobble()
                                        scarecrowRightArmWave.animateTo(-18f, tween(120))
                                        scarecrowRightArmWave.animateTo(12f, tween(150))
                                        scarecrowRightArmWave.animateTo(0f, tween(180))
                                    }
                                }
                            }

                            // Spawn 6 soft particles of arrow color
                            repeat(7) {
                                val pAngle = Random.nextFloat() * 2f * PI.toFloat()
                                val pSpeed = Random.nextFloat() * 220f + 60f
                                particles.add(
                                    ArcheryParticle(
                                        x = arrow.position.x,
                                        y = arrow.position.y,
                                        vx = cos(pAngle) * pSpeed,
                                        vy = sin(pAngle) * pSpeed - 60f,
                                        color = arrow.type.particleColor,
                                        radius = Random.nextFloat() * 6f + 5f
                                    )
                                )
                            }

                            // Keep pool size under 20
                            while (particles.size > 20) {
                                particles.removeAt(0)
                            }

                            // Store stuck item in scarecrow local space
                            val currentAngle = (scarecrowWobbleAngle.value + idleSwayDeg + scarecrowSpinAngle.value) * PI.toFloat() / 180f
                            val relX = dx * cos(-currentAngle) - dy * sin(-currentAngle)
                            val relY = dx * sin(-currentAngle) + dy * cos(-currentAngle)

                            // Cap at 10 stuck items: oldest item starts fading
                            if (stuckArrows.size >= 10) {
                                stuckArrows.removeAt(0)
                            }

                            stuckArrows.add(
                                StuckArrow(
                                    id = arrow.id,
                                    type = arrow.type,
                                    localOffset = Offset(relX, relY),
                                    angleDeg = arrow.angleDeg - (scarecrowWobbleAngle.value + idleSwayDeg + scarecrowSpinAngle.value),
                                    hitZone = hitZone
                                )
                            )

                            // Advance Hit Dots counter
                            val nextHits = hitDotsCount + 1
                            if (nextHits >= 7) {
                                // 7-Hit Auto-Clear Celebration!
                                hitDotsCount = 0
                                soundHelper.playFanfare()

                                // 360 degree spin
                                coroutineScope.launch {
                                    scarecrowSpinAngle.animateTo(360f, tween(800, easing = FastOutSlowInEasing))
                                    scarecrowSpinAngle.snapTo(0f)
                                }

                                // Arrows blow away in centrifugal directions
                                stuckArrows.forEach { stuck ->
                                    stuck.isBlowingAway = true
                                    stuck.blowAwayVx = (Random.nextFloat() - 0.5f) * 600f
                                    stuck.blowAwayVy = -Random.nextFloat() * 400f - 150f
                                }

                                // 1-second burst of 20 soft confetti pieces
                                repeat(20) {
                                    confettiList.add(
                                        ArcheryConfetti(
                                            x = scarecrowCenterPx.x + (Random.nextFloat() - 0.5f) * 120f,
                                            y = scarecrowCenterPx.y + (Random.nextFloat() - 0.5f) * 120f,
                                            vx = (Random.nextFloat() - 0.5f) * 450f,
                                            vy = -Random.nextFloat() * 500f - 100f,
                                            rotation = Random.nextFloat() * 360f,
                                            vRot = (Random.nextFloat() - 0.5f) * 500f,
                                            color = listOf(ColorPink, ColorYellow, ColorGreen, ColorOrange, ColorBlue).random(),
                                            size = Random.nextFloat() * 10f + 8f
                                        )
                                    )
                                }
                            } else {
                                hitDotsCount = nextHits
                            }

                            activeFlyingArrow = null
                        }
                    }

                    // Ground hit or out-of-bounds miss
                    if (activeFlyingArrow != null) {
                        val groundY = bowCenterPx.y + 40f
                        if (arrow.position.y >= groundY && arrow.velocity.y > 0) {
                            // Stuck in ground for 3 seconds, then fades softly
                            soundHelper.playThud()
                            groundArrows.add(
                                StuckArrow(
                                    id = arrow.id,
                                    type = arrow.type,
                                    localOffset = Offset(arrow.position.x, groundY),
                                    angleDeg = arrow.angleDeg.coerceIn(30f, 85f),
                                    hitZone = null
                                )
                            )
                            activeFlyingArrow = null
                        } else if (arrow.position.x < -100f || arrow.position.x > 1500f || arrow.position.y > 2500f) {
                            activeFlyingArrow = null
                        }
                    }
                }

                // 2. Update Particles & Confetti
                for (i in particles.indices.reversed()) {
                    val p = particles[i]
                    p.ageSec += dt
                    p.vy += 800f * dt
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.alpha = (1.0f - p.ageSec / p.lifeSec).coerceIn(0f, 1f)
                    if (p.ageSec >= p.lifeSec) {
                        particles.removeAt(i)
                    }
                }

                for (i in confettiList.indices.reversed()) {
                    val c = confettiList[i]
                    c.ageSec += dt
                    c.vy += 600f * dt
                    c.x += c.vx * dt
                    c.y += c.vy * dt
                    c.rotation += c.vRot * dt
                    if (c.ageSec >= 1.0f) {
                        c.alpha = (1.0f - (c.ageSec - 1.0f) / 0.4f).coerceIn(0f, 1f)
                    }
                    if (c.ageSec >= 1.4f) {
                        confettiList.removeAt(i)
                    }
                }

                // 3. Update Stuck Arrows blowing away
                for (i in stuckArrows.indices.reversed()) {
                    val st = stuckArrows[i]
                    if (st.isBlowingAway) {
                        st.localOffset = Offset(st.localOffset.x + st.blowAwayVx * dt, st.localOffset.y + st.blowAwayVy * dt)
                        st.blowAwayVy += 600f * dt
                        st.alpha -= dt * 1.8f
                        if (st.alpha <= 0f) {
                            stuckArrows.removeAt(i)
                        }
                    }
                }

                // 4. Update Ground Arrows (fade after 3s)
                val nowMs = System.currentTimeMillis()
                for (i in groundArrows.indices.reversed()) {
                    val ga = groundArrows[i]
                    val elapsed = nowMs - ga.timestamp
                    if (elapsed > 3000L) {
                        ga.alpha -= dt * 2f
                        if (ga.alpha <= 0f) {
                            groundArrows.removeAt(i)
                        }
                    }
                }
            }
        }
    }

    val archeryDecors = remember {
        listOf(
            DecorSpec(DecorKind.SUN, 0.88f, 0.08f, 76f),
            DecorSpec(DecorKind.CLOUD, 0.16f, 0.10f, 84f),
            DecorSpec(DecorKind.CLOUD, 0.58f, 0.12f, 76f),
            DecorSpec(DecorKind.PUMPKIN, 0.88f, 0.72f, 80f),
            DecorSpec(DecorKind.PUMPKIN, 0.12f, 0.78f, 76f),
            DecorSpec(DecorKind.SUNFLOWER, 0.86f, 0.52f, 96f),
            DecorSpec(DecorKind.BUTTERFLY, 0.18f, 0.28f, 60f),
            DecorSpec(DecorKind.BIRD, 0.82f, 0.28f, 60f),
            DecorSpec(DecorKind.FLOWER, 0.08f, 0.54f, 64f),
            DecorSpec(DecorKind.TREE, 0.08f, 0.38f, 84f)
        )
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
                            prefs.edit().putBoolean("first_launch_archery", false).apply()
                        }
                    }
                )
            }
    ) {
        // 1. Vector 3-Layer Scene Background: FARM
        SceneBackground(theme = SceneTheme.FARM)

        // 2. Harmless background decor outside the shooting zone
        DecorLayer(items = archeryDecors)
        // Main Interactive Garden Canvas (Scarecrow, Bow, Trajectory, Arrows, Confetti)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(selectedArrowType) {
                    val maxPullDistPx = 160.dp.toPx()
                    val minPullDistPx = 24.dp.toPx()
                    val touchRadiusPx = 160.dp.toPx()

                    detectDragGestures(
                        onDragStart = { startOffset ->
                            lastInteractionTime = System.currentTimeMillis()
                            showIdleHint = false
                            if (isFirstLaunch) {
                                isFirstLaunch = false
                                prefs.edit().putBoolean("first_launch_archery", false).apply()
                            }

                            // Check if drag starts near the bow center
                            val distToBow = (startOffset - bowCenterPx).getDistance()
                            if (distToBow <= touchRadiusPx) {
                                isDraggingBow = true
                                bowDragOffsetPx = Offset.Zero
                                soundHelper.playBowPull()
                                haptics.vibrate(15)
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (isDraggingBow) {
                                lastInteractionTime = System.currentTimeMillis()
                                val nextOffset = bowDragOffsetPx + dragAmount
                                // Clamp to downward-pull cone and max pull distance
                                val clampedDist = nextOffset.getDistance().coerceAtMost(maxPullDistPx)
                                val angle = atan2(nextOffset.y, nextOffset.x)
                                bowDragOffsetPx = Offset(
                                    cos(angle) * clampedDist,
                                    sin(angle) * clampedDist
                                )
                            }
                        },
                        onDragEnd = {
                            if (isDraggingBow) {
                                val pullDistance = bowDragOffsetPx.getDistance()
                                val pullDirection = if (pullDistance > 0f) bowDragOffsetPx / pullDistance else Offset(0f, 1f)

                                if (pullDistance >= minPullDistPx) {
                                    // Valid Shot! Arrow flies opposite to the pull vector
                                    val launchSpeedPx = pullDistance * 6.5f * 2.2f
                                    val launchVelocity = -pullDirection * launchSpeedPx

                                    activeFlyingArrow = FlyingArrow(
                                        id = System.currentTimeMillis(),
                                        type = selectedArrowType,
                                        position = bowCenterPx - pullDirection * 20f,
                                        velocity = launchVelocity,
                                        angleDeg = (atan2(launchVelocity.y, launchVelocity.x) * 180f / PI.toFloat())
                                    )

                                    soundHelper.playShoot()
                                    haptics.vibrate(20)
                                } else {
                                    // Too small pull: cancel softly
                                    soundManager.play("soft_no")
                                }

                                isDraggingBow = false
                                bowDragOffsetPx = Offset.Zero
                            }
                        },
                        onDragCancel = {
                            isDraggingBow = false
                            bowDragOffsetPx = Offset.Zero
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height

            // Calculate dynamic key positions
            val sCenter = Offset(width / 2f, height * 0.32f)
            scarecrowCenterPx = sCenter
            scarecrowScale = (width / 420f).coerceIn(0.85f, 1.25f)

            val bWidth = width * 0.36f
            bowWidthPx = bWidth
            val bCenter = Offset(bWidth * 0.5f + 8.dp.toPx(), height * 0.65f)
            bowCenterPx = bCenter

            // 1. Draw Decorative Garden Background in Plasticine Style
            drawGardenBackground(width, height)

            // 2. Draw Scarecrow with all active animations & stuck arrows
            val totalScarecrowAngle = scarecrowWobbleAngle.value + idleSwayDeg + scarecrowSpinAngle.value
            rotate(degrees = totalScarecrowAngle, pivot = sCenter) {
                drawPlasticineScarecrow(
                    center = sCenter,
                    scale = scarecrowScale,
                    hatOffsetY = scarecrowHatOffsetY.value,
                    eyesScale = scarecrowEyesScale.value,
                    leftArmAngle = scarecrowLeftArmWave.value,
                    rightArmAngle = scarecrowRightArmWave.value,
                    stuckArrows = stuckArrows
                )
            }

            // 3. Draw Stuck Ground Arrows
            groundArrows.forEach { ga ->
                drawStuckArrowItem(
                    pos = ga.localOffset,
                    angleDeg = ga.angleDeg,
                    type = ga.type,
                    alpha = ga.alpha
                )
            }

            // 4. Draw Flying Arrow
            activeFlyingArrow?.let { fa ->
                drawFlyingArrowItem(
                    pos = fa.position,
                    angleDeg = fa.angleDeg,
                    type = fa.type
                )
            }

            // 5. Draw Dotted Aiming Trajectory while pulling
            if (isDraggingBow && bowDragOffsetPx.getDistance() >= 20.dp.toPx()) {
                val pullDistance = bowDragOffsetPx.getDistance()
                val pullDirection = bowDragOffsetPx / pullDistance
                val launchSpeedPx = pullDistance * 6.5f * 2.2f
                val launchVelocity = -pullDirection * launchSpeedPx

                drawDottedTrajectory(
                    startPos = bCenter - pullDirection * 20f,
                    initialVelocity = launchVelocity,
                    gravity = 1400f * 2.2f
                )
            }

            // 6. Draw Bow (Wood, Limbs, Bent String, and Loaded Arrow)
            drawPlasticineBow(
                center = bCenter,
                width = bWidth,
                dragOffset = bowDragOffsetPx,
                loadedArrowType = selectedArrowType,
                isDragging = isDraggingBow
            )

            // 7. Draw Soft Impact Particles
            particles.forEach { p ->
                drawCircle(
                    color = p.color.copy(alpha = p.alpha),
                    radius = p.radius,
                    center = Offset(p.x, p.y)
                )
                // Plasticine ink contour
                drawCircle(
                    color = ColorInk.copy(alpha = p.alpha * 0.7f),
                    radius = p.radius,
                    center = Offset(p.x, p.y),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 8. Draw Confetti Celebrations
            confettiList.forEach { c ->
                rotate(degrees = c.rotation, pivot = Offset(c.x, c.y)) {
                    drawRoundRect(
                        color = c.color.copy(alpha = c.alpha),
                        topLeft = Offset(c.x - c.size / 2, c.y - c.size / 4),
                        size = Size(c.size, c.size / 2),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
        }

        // Top UI Layer: 7-Hit Dots Indicator & Broom Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Space for HomeButton (which is placed by MainActivity at TopStart)
            Spacer(modifier = Modifier.size(76.dp))

            // 7 Round Dots Progress Indicator (Non-textual, toddler-friendly)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(ColorWhite.copy(alpha = 0.85f), ShapeCard)
                    .border(BorderStroke(2.5.dp, ColorInk), ShapeCard)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                for (i in 0 until 7) {
                    val isFilled = i < hitDotsCount
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .border(BorderStroke(2.dp, ColorInk), CircleShape)
                            .background(if (isFilled) ColorYellow else ColorBgSoft, CircleShape)
                    )
                }
            }

            // 🧹 Broom / Clear Button (72 dp, ALWAYS visible, clean with one tap)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .border(BorderStroke(3.dp, ColorInk), ShapeButton)
                    .background(ColorYellow, ShapeButton)
                    .clip(ShapeButton)
                    .plasticineClickable(haptics = haptics) {
                        soundHelper.playWhoosh()
                        haptics.vibrateShort()

                        // Blow away all stuck arrows with wind animation
                        stuckArrows.forEach { stuck ->
                            stuck.isBlowingAway = true
                            stuck.blowAwayVx = Random.nextFloat() * 450f + 350f
                            stuck.blowAwayVy = -Random.nextFloat() * 150f - 50f
                        }
                        groundArrows.clear()
                        hitDotsCount = 0

                        // Scarecrow sways happily
                        coroutineScope.launch {
                            scarecrowWobbleAngle.animateTo(10f, tween(150))
                            scarecrowWobbleAngle.animateTo(-10f, tween(180))
                            scarecrowWobbleAngle.animateTo(0f, tween(200))
                        }
                    }
                    .testTag("archery_clean_broom_btn")
            ) {
                Text(
                    text = "🧹",
                    fontSize = 32.sp
                )
            }
        }

        // Arrow Selector Row (3 round buttons, 88 dp each, at the bottom)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArrowType.values().forEach { arrowType ->
                val isSelected = selectedArrowType == arrowType
                val buttonScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
                    label = "arrowBtnScale"
                )

                val btnCircleColor = when (arrowType) {
                    ArrowType.NORMAL -> Color(0xFFFFE08A)
                    ArrowType.CARROT -> Color(0xFFFFB347)
                    ArrowType.STAR -> Color(0xFFC9B3FF)
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .scale(buttonScale)
                        .size(88.dp)
                        .border(
                            BorderStroke(if (isSelected) 4.5.dp else 2.5.dp, ColorInk),
                            CircleShape
                        )
                        .background(btnCircleColor, CircleShape)
                        .clip(CircleShape)
                        .plasticineClickable(haptics = haptics) {
                            if (selectedArrowType != arrowType) {
                                selectedArrowType = arrowType
                                soundHelper.playPop()
                                soundHelper.playArrowVoice(arrowType)
                                haptics.vibrate(15)

                                // Trigger big pop-out badge in center of screen
                                bigBadgeArrow = arrowType
                                coroutineScope.launch {
                                    badgeScale.snapTo(0f)
                                    badgeAlpha.snapTo(1f)
                                    badgeScale.animateTo(1.2f, tween(180, easing = FastOutSlowInEasing))
                                    badgeScale.animateTo(1.0f, tween(140))
                                    delay(480)
                                    badgeAlpha.animateTo(0f, tween(200))
                                    bigBadgeArrow = null
                                }
                            }
                        }
                        .testTag("archery_arrow_btn_${arrowType.name.lowercase()}")
                ) {
                    Text(
                        text = arrowType.iconEmoji,
                        fontSize = 38.sp
                    )
                }
            }
        }

        // Center Pop-out Badge (150 dp, scale 0 -> 1.2 -> 1.0, fade out over 800 ms)
        bigBadgeArrow?.let { badgeType ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .scale(badgeScale.value)
                    .size(150.dp)
                    .border(BorderStroke(4.dp, ColorInk), CircleShape)
                    .background(ColorWhite.copy(alpha = badgeAlpha.value), CircleShape)
                    .clip(CircleShape)
            ) {
                Text(
                    text = badgeType.iconEmoji,
                    fontSize = 76.sp,
                    modifier = Modifier.graphicsLayer { alpha = badgeAlpha.value }
                )
            }
        }

        // Hint Hand Gesture: Pull down bowstring & release
        if (isFirstLaunch || showIdleHint) {
            HintHand(
                from = Offset(160f, 480f),
                to = Offset(160f, 560f),
                mode = "drag",
                delayMs = if (isFirstLaunch) 1200L else 200L
            )
        }
    }
}

/**
 * 🎨 Plasticine garden background (sky, rolling soft hills, fence, soft grass).
 */
private fun DrawScope.drawGardenBackground(width: Float, height: Float) {
    // Sky gradient
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFE1F5FE), Color(0xFFFFF9C4), Color(0xFFE8F5E9)),
            startY = 0f,
            endY = height * 0.7f
        ),
        size = Size(width, height)
    )

    // Soft rolling green hill
    val hillPath = Path().apply {
        moveTo(0f, height * 0.52f)
        quadraticBezierTo(width * 0.3f, height * 0.46f, width * 0.65f, height * 0.50f)
        quadraticBezierTo(width * 0.85f, height * 0.53f, width, height * 0.49f)
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }
    drawPath(hillPath, color = Color(0xFFA5D6A7))
    drawPath(hillPath, color = ColorInk, style = Stroke(width = 3.dp.toPx()))

    // Decorative plasticine fence posts
    val fenceY = height * 0.53f
    val fencePostW = 16.dp.toPx()
    val fencePostH = 45.dp.toPx()
    val stepX = width / 7f

    for (i in 0..7) {
        val px = i * stepX + 6.dp.toPx()
        drawRoundRect(
            color = Color(0xFFD7CCC8),
            topLeft = Offset(px, fenceY - fencePostH),
            size = Size(fencePostW, fencePostH),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )
        drawRoundRect(
            color = ColorInk,
            topLeft = Offset(px, fenceY - fencePostH),
            size = Size(fencePostW, fencePostH),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            style = Stroke(width = 2.5.dp.toPx())
        )
    }

    // Horizontal fence rail
    drawRoundRect(
        color = Color(0xFFBCAAA4),
        topLeft = Offset(0f, fenceY - fencePostH * 0.55f),
        size = Size(width, 12.dp.toPx()),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    )
    drawRoundRect(
        color = ColorInk,
        topLeft = Offset(0f, fenceY - fencePostH * 0.55f),
        size = Size(width, 12.dp.toPx()),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        style = Stroke(width = 2.5.dp.toPx())
    )

    // Foreground lush garden grass
    val grassPath = Path().apply {
        moveTo(0f, height * 0.64f)
        quadraticBezierTo(width * 0.4f, height * 0.61f, width, height * 0.65f)
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }
    drawPath(grassPath, color = Color(0xFF81C784))
    drawPath(grassPath, color = ColorInk, style = Stroke(width = 3.dp.toPx()))
}

/**
 * 🌾 Detailed Plasticine Scarecrow Drawing with 4 Hit Zones.
 */
private fun DrawScope.drawPlasticineScarecrow(
    center: Offset,
    scale: Float,
    hatOffsetY: Float,
    eyesScale: Float,
    leftArmAngle: Float,
    rightArmAngle: Float,
    stuckArrows: List<StuckArrow>
) {
    val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    // 1. Wooden Cross Pole (Support Post)
    val poleW = 18.dp.toPx() * scale
    val poleH = 240.dp.toPx() * scale
    drawRoundRect(
        color = Color(0xFF8D6E63),
        topLeft = Offset(center.x - poleW / 2, center.y - 40.dp.toPx() * scale),
        size = Size(poleW, poleH),
        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
    )
    drawRoundRect(
        color = ColorInk,
        topLeft = Offset(center.x - poleW / 2, center.y - 40.dp.toPx() * scale),
        size = Size(poleW, poleH),
        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
        style = inkStroke
    )

    // 2. Arms (Left & Right with wave angles)
    val armBarW = 85.dp.toPx() * scale
    val armBarH = 26.dp.toPx() * scale

    // Left Arm (Shirt sleeve + Straw tuft)
    rotate(degrees = leftArmAngle, pivot = Offset(center.x - 40.dp.toPx() * scale, center.y + 10.dp.toPx() * scale)) {
        val leftX = center.x - 40.dp.toPx() * scale - armBarW
        val leftY = center.y + 10.dp.toPx() * scale
        // Sleeve
        drawRoundRect(
            color = Color(0xFFFFD54F),
            topLeft = Offset(leftX, leftY),
            size = Size(armBarW, armBarH),
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
        )
        drawRoundRect(
            color = ColorInk,
            topLeft = Offset(leftX, leftY),
            size = Size(armBarW, armBarH),
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
            style = inkStroke
        )
        // Straw hand tuft
        drawCircle(
            color = Color(0xFFFFE082),
            radius = 14.dp.toPx() * scale,
            center = Offset(leftX, leftY + armBarH / 2)
        )
        drawCircle(
            color = ColorInk,
            radius = 14.dp.toPx() * scale,
            center = Offset(leftX, leftY + armBarH / 2),
            style = inkStroke
        )
    }

    // Right Arm (Shirt sleeve + Straw tuft)
    rotate(degrees = rightArmAngle, pivot = Offset(center.x + 40.dp.toPx() * scale, center.y + 10.dp.toPx() * scale)) {
        val rightX = center.x + 40.dp.toPx() * scale
        val rightY = center.y + 10.dp.toPx() * scale
        // Sleeve
        drawRoundRect(
            color = Color(0xFFFFD54F),
            topLeft = Offset(rightX, rightY),
            size = Size(armBarW, armBarH),
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
        )
        drawRoundRect(
            color = ColorInk,
            topLeft = Offset(rightX, rightY),
            size = Size(armBarW, armBarH),
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
            style = inkStroke
        )
        // Straw hand tuft
        drawCircle(
            color = Color(0xFFFFE082),
            radius = 14.dp.toPx() * scale,
            center = Offset(rightX + armBarW, rightY + armBarH / 2)
        )
        drawCircle(
            color = ColorInk,
            radius = 14.dp.toPx() * scale,
            center = Offset(rightX + armBarW, rightY + armBarH / 2),
            style = inkStroke
        )
    }

    // 3. Body (Straw Burlap Shirt with cute patches)
    val bodyW = 90.dp.toPx() * scale
    val bodyH = 110.dp.toPx() * scale
    val bodyTopLeft = Offset(center.x - bodyW / 2, center.y)

    drawRoundRect(
        color = Color(0xFFFFCA28),
        topLeft = bodyTopLeft,
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
    )
    drawRoundRect(
        color = ColorInk,
        topLeft = bodyTopLeft,
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
        style = inkStroke
    )

    // Cute pink plasticine patch with stitches
    val patchW = 24.dp.toPx() * scale
    val patchTopLeft = Offset(center.x - 26.dp.toPx() * scale, center.y + 36.dp.toPx() * scale)
    drawRoundRect(
        color = ColorPink,
        topLeft = patchTopLeft,
        size = Size(patchW, patchW),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    )
    drawRoundRect(
        color = ColorInk,
        topLeft = patchTopLeft,
        size = Size(patchW, patchW),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        style = Stroke(width = 2.dp.toPx())
    )

    // Rope Belt
    drawLine(
        color = Color(0xFF6D4C41),
        start = Offset(bodyTopLeft.x, bodyTopLeft.y + bodyH * 0.72f),
        end = Offset(bodyTopLeft.x + bodyW, bodyTopLeft.y + bodyH * 0.72f),
        strokeWidth = 6.dp.toPx() * scale,
        cap = StrokeCap.Round
    )

    // 4. Head (Burlap sphere, button eyes, embroidered smile, rosy cheeks)
    val headRadius = 40.dp.toPx() * scale
    val headCenter = Offset(center.x, center.y - 40.dp.toPx() * scale)

    drawCircle(
        color = Color(0xFFFFF9C4),
        radius = headRadius,
        center = headCenter
    )
    drawCircle(
        color = ColorInk,
        radius = headRadius,
        center = headCenter,
        style = inkStroke
    )

    // Rosy pink cheeks
    drawCircle(
        color = ColorPink.copy(alpha = 0.5f),
        radius = 8.dp.toPx() * scale,
        center = Offset(headCenter.x - 22.dp.toPx() * scale, headCenter.y + 8.dp.toPx() * scale)
    )
    drawCircle(
        color = ColorPink.copy(alpha = 0.5f),
        radius = 8.dp.toPx() * scale,
        center = Offset(headCenter.x + 22.dp.toPx() * scale, headCenter.y + 8.dp.toPx() * scale)
    )

    // Button Eyes (Brown with stitched cross)
    val eyeR = 7.dp.toPx() * scale * eyesScale
    val leftEyeCenter = Offset(headCenter.x - 14.dp.toPx() * scale, headCenter.y - 6.dp.toPx() * scale)
    val rightEyeCenter = Offset(headCenter.x + 14.dp.toPx() * scale, headCenter.y - 6.dp.toPx() * scale)

    // Left Eye
    drawCircle(color = Color(0xFF4E342E), radius = eyeR, center = leftEyeCenter)
    drawCircle(color = ColorInk, radius = eyeR, center = leftEyeCenter, style = Stroke(width = 1.5.dp.toPx()))
    drawLine(color = ColorWhite, start = Offset(leftEyeCenter.x - eyeR * 0.5f, leftEyeCenter.y), end = Offset(leftEyeCenter.x + eyeR * 0.5f, leftEyeCenter.y), strokeWidth = 1.5.dp.toPx())

    // Right Eye
    drawCircle(color = Color(0xFF4E342E), radius = eyeR, center = rightEyeCenter)
    drawCircle(color = ColorInk, radius = eyeR, center = rightEyeCenter, style = Stroke(width = 1.5.dp.toPx()))
    drawLine(color = ColorWhite, start = Offset(rightEyeCenter.x - eyeR * 0.5f, rightEyeCenter.y), end = Offset(rightEyeCenter.x + eyeR * 0.5f, rightEyeCenter.y), strokeWidth = 1.5.dp.toPx())

    // Happy embroidered smile
    val smilePath = Path().apply {
        moveTo(headCenter.x - 14.dp.toPx() * scale, headCenter.y + 14.dp.toPx() * scale)
        quadraticBezierTo(headCenter.x, headCenter.y + 26.dp.toPx() * scale, headCenter.x + 14.dp.toPx() * scale, headCenter.y + 14.dp.toPx() * scale)
    }
    drawPath(smilePath, color = ColorInk, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

    // 5. Straw Hat (Bounces on head hit)
    val hatCenter = Offset(headCenter.x, headCenter.y - 32.dp.toPx() * scale + hatOffsetY)
    val brimW = 110.dp.toPx() * scale
    val brimH = 22.dp.toPx() * scale
    val crownW = 55.dp.toPx() * scale
    val crownH = 35.dp.toPx() * scale

    // Hat Crown
    drawRoundRect(
        color = Color(0xFFFFB74D),
        topLeft = Offset(hatCenter.x - crownW / 2, hatCenter.y - crownH),
        size = Size(crownW, crownH),
        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
    )
    drawRoundRect(
        color = ColorInk,
        topLeft = Offset(hatCenter.x - crownW / 2, hatCenter.y - crownH),
        size = Size(crownW, crownH),
        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
        style = inkStroke
    )

    // Colored Ribbon Band
    drawRoundRect(
        color = ColorPink,
        topLeft = Offset(hatCenter.x - crownW / 2, hatCenter.y - 12.dp.toPx() * scale),
        size = Size(crownW, 10.dp.toPx() * scale),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )
    drawRoundRect(
        color = ColorInk,
        topLeft = Offset(hatCenter.x - crownW / 2, hatCenter.y - 12.dp.toPx() * scale),
        size = Size(crownW, 10.dp.toPx() * scale),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        style = Stroke(width = 2.dp.toPx())
    )

    // Hat Brim
    drawOval(
        color = Color(0xFFFFCC80),
        topLeft = Offset(hatCenter.x - brimW / 2, hatCenter.y - brimH / 2),
        size = Size(brimW, brimH)
    )
    drawOval(
        color = ColorInk,
        topLeft = Offset(hatCenter.x - brimW / 2, hatCenter.y - brimH / 2),
        size = Size(brimW, brimH),
        style = inkStroke
    )

    // 6. Stuck Arrows & Star Stickers (Rendered in Scarecrow's local frame)
    stuckArrows.forEach { stuck ->
        val stuckPos = center + stuck.localOffset
        drawStuckArrowItem(
            pos = stuckPos,
            angleDeg = stuck.angleDeg,
            type = stuck.type,
            alpha = stuck.alpha
        )
    }
}

/**
 * 🏹 Wooden Plasticine Bow with string tension and loaded arrow.
 */
private fun DrawScope.drawPlasticineBow(
    center: Offset,
    width: Float,
    dragOffset: Offset,
    loadedArrowType: ArrowType,
    isDragging: Boolean
) {
    val inkStroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val halfW = width / 2f
    val bowHeight = 48.dp.toPx()

    // Bow limb tip positions
    val leftTip = Offset(center.x - halfW, center.y - 10.dp.toPx())
    val rightTip = Offset(center.x + halfW, center.y - 10.dp.toPx())

    // Drag displacement (string touch point)
    val stringCenter = center + dragOffset

    // 1. Draw Bow Wood Arch (flexes slightly when string is pulled)
    val flexY = dragOffset.y * 0.12f
    val bowArch = Path().apply {
        moveTo(leftTip.x, leftTip.y)
        quadraticBezierTo(
            center.x,
            center.y - bowHeight + flexY,
            rightTip.x,
            rightTip.y
        )
    }

    // Wooden limb body
    drawPath(bowArch, color = Color(0xFFA1887F), style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round))
    drawPath(bowArch, color = Color(0xFFD7CCC8), style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
    drawPath(bowArch, color = ColorInk, style = inkStroke)

    // Bow Limb End Caps
    drawCircle(color = Color(0xFF5D4037), radius = 6.dp.toPx(), center = leftTip)
    drawCircle(color = ColorInk, radius = 6.dp.toPx(), center = leftTip, style = Stroke(width = 2.dp.toPx()))
    drawCircle(color = Color(0xFF5D4037), radius = 6.dp.toPx(), center = rightTip)
    drawCircle(color = ColorInk, radius = 6.dp.toPx(), center = rightTip, style = Stroke(width = 2.dp.toPx()))

    // 2. Draw Bow String (Taut V-shape when pulled)
    val stringPath = Path().apply {
        moveTo(leftTip.x, leftTip.y)
        lineTo(stringCenter.x, stringCenter.y)
        lineTo(rightTip.x, rightTip.y)
    }
    drawLine(color = Color(0xFFE0E0E0), start = leftTip, end = stringCenter, strokeWidth = 3.dp.toPx())
    drawLine(color = Color(0xFFE0E0E0), start = stringCenter, end = rightTip, strokeWidth = 3.dp.toPx())
    drawPath(stringPath, color = ColorInk, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

    // 3. Draw Loaded Arrow resting on the bow
    val pullDist = dragOffset.getDistance()
    val arrowAngleDeg = if (pullDist > 10f) {
        val dir = -dragOffset / pullDist
        (atan2(dir.y, dir.x) * 180f / PI.toFloat())
    } else {
        -90f // pointing straight up
    }

    val arrowNock = stringCenter
    val arrowLength = 70.dp.toPx()
    val arrowDir = Offset(cos(arrowAngleDeg * PI.toFloat() / 180f), sin(arrowAngleDeg * PI.toFloat() / 180f))
    val arrowTip = arrowNock + arrowDir * arrowLength

    drawArrowGraphic(
        startNock = arrowNock,
        endTip = arrowTip,
        type = loadedArrowType,
        angleDeg = arrowAngleDeg,
        alpha = 1.0f
    )
}

/**
 * 🎯 Dotted parabolic trajectory guide (8-10 points, fading opacity).
 */
private fun DrawScope.drawDottedTrajectory(
    startPos: Offset,
    initialVelocity: Offset,
    gravity: Float
) {
    val totalPoints = 9
    val dtStep = 0.055f

    for (i in 1..totalPoints) {
        val t = i * dtStep
        val px = startPos.x + initialVelocity.x * t
        val py = startPos.y + initialVelocity.y * t + 0.5f * gravity * t * t

        val alpha = (1.0f - (i.toFloat() / (totalPoints + 1))).coerceIn(0.15f, 0.95f)
        val dotRadius = (5.5.dp.toPx() * (1f - (i * 0.04f))).coerceAtLeast(2.5.dp.toPx())

        drawCircle(
            color = ColorYellow.copy(alpha = alpha),
            radius = dotRadius,
            center = Offset(px, py)
        )
        drawCircle(
            color = ColorInk.copy(alpha = alpha),
            radius = dotRadius,
            center = Offset(px, py),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

/**
 * 🏹 Flying arrow in motion.
 */
private fun DrawScope.drawFlyingArrowItem(
    pos: Offset,
    angleDeg: Float,
    type: ArrowType
) {
    val arrowLength = 65.dp.toPx()
    val dir = Offset(cos(angleDeg * PI.toFloat() / 180f), sin(angleDeg * PI.toFloat() / 180f))
    val nock = pos - dir * (arrowLength * 0.7f)
    val tip = pos + dir * (arrowLength * 0.3f)

    drawArrowGraphic(
        startNock = nock,
        endTip = tip,
        type = type,
        angleDeg = angleDeg,
        alpha = 1.0f
    )
}

/**
 * 📍 Arrow or Star Sticker stuck in target.
 */
private fun DrawScope.drawStuckArrowItem(
    pos: Offset,
    angleDeg: Float,
    type: ArrowType,
    alpha: Float
) {
    when (type) {
        ArrowType.NORMAL, ArrowType.CARROT -> {
            val arrowLength = 55.dp.toPx()
            val dir = Offset(cos(angleDeg * PI.toFloat() / 180f), sin(angleDeg * PI.toFloat() / 180f))
            val nock = pos - dir * arrowLength
            val tip = pos // Tip is embedded in scarecrow!

            drawArrowGraphic(
                startNock = nock,
                endTip = tip,
                type = type,
                angleDeg = angleDeg,
                alpha = alpha
            )
        }
        ArrowType.STAR -> {
            // Star arrow leaves a shiny star sticker (~28 dp)
            rotate(degrees = angleDeg, pivot = pos) {
                drawStarSticker(center = pos, radius = 16.dp.toPx(), alpha = alpha)
            }
        }
    }
}

/**
 * Common graphic painter for arrows (Normal wooden arrow, Carrot, or Star-on-stick).
 */
private fun DrawScope.drawArrowGraphic(
    startNock: Offset,
    endTip: Offset,
    type: ArrowType,
    angleDeg: Float,
    alpha: Float
) {
    val inkStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (type) {
        ArrowType.NORMAL -> {
            // Straight Wooden Arrow Shaft
            drawLine(
                color = Color(0xFF8D6E63).copy(alpha = alpha),
                start = startNock,
                end = endTip,
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = ColorInk.copy(alpha = alpha),
                start = startNock,
                end = endTip,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Arrow Tip (Arrowhead)
            rotate(degrees = angleDeg, pivot = endTip) {
                val headPath = Path().apply {
                    moveTo(endTip.x, endTip.y)
                    lineTo(endTip.x - 14.dp.toPx(), endTip.y - 7.dp.toPx())
                    lineTo(endTip.x - 10.dp.toPx(), endTip.y)
                    lineTo(endTip.x - 14.dp.toPx(), endTip.y + 7.dp.toPx())
                    close()
                }
                drawPath(headPath, color = Color(0xFFCFD8DC).copy(alpha = alpha))
                drawPath(headPath, color = ColorInk.copy(alpha = alpha), style = inkStroke)
            }

            // Fletching Feathers (Nock)
            rotate(degrees = angleDeg, pivot = startNock) {
                val fletchW = 12.dp.toPx()
                val fletchH = 6.dp.toPx()
                // Top feather
                drawRoundRect(
                    color = ColorPink.copy(alpha = alpha),
                    topLeft = Offset(startNock.x, startNock.y - fletchH - 1.dp.toPx()),
                    size = Size(fletchW, fletchH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
                // Bottom feather
                drawRoundRect(
                    color = ColorPink.copy(alpha = alpha),
                    topLeft = Offset(startNock.x, startNock.y + 1.dp.toPx()),
                    size = Size(fletchW, fletchH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }
        ArrowType.CARROT -> {
            // Funny Carrot Body
            rotate(degrees = angleDeg, pivot = endTip) {
                val carrotLength = (endTip - startNock).getDistance()
                val carrotPath = Path().apply {
                    moveTo(endTip.x, endTip.y)
                    quadraticBezierTo(endTip.x - carrotLength * 0.5f, endTip.y - 10.dp.toPx(), endTip.x - carrotLength, endTip.y - 8.dp.toPx())
                    lineTo(endTip.x - carrotLength, endTip.y + 8.dp.toPx())
                    quadraticBezierTo(endTip.x - carrotLength * 0.5f, endTip.y + 10.dp.toPx(), endTip.x, endTip.y)
                    close()
                }
                drawPath(carrotPath, color = ColorOrange.copy(alpha = alpha))
                drawPath(carrotPath, color = ColorInk.copy(alpha = alpha), style = inkStroke)

                // Carrot stripes
                drawLine(
                    color = Color(0xFFE65100).copy(alpha = alpha),
                    start = Offset(endTip.x - carrotLength * 0.4f, endTip.y - 5.dp.toPx()),
                    end = Offset(endTip.x - carrotLength * 0.4f, endTip.y + 4.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = Color(0xFFE65100).copy(alpha = alpha),
                    start = Offset(endTip.x - carrotLength * 0.7f, endTip.y - 6.dp.toPx()),
                    end = Offset(endTip.x - carrotLength * 0.7f, endTip.y + 5.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )

                // Green Leafy Tail (Botva)
                drawCircle(color = ColorGreen.copy(alpha = alpha), radius = 6.dp.toPx(), center = Offset(endTip.x - carrotLength - 4.dp.toPx(), endTip.y - 4.dp.toPx()))
                drawCircle(color = ColorGreen.copy(alpha = alpha), radius = 6.dp.toPx(), center = Offset(endTip.x - carrotLength - 4.dp.toPx(), endTip.y + 4.dp.toPx()))
                drawCircle(color = ColorGreen.copy(alpha = alpha), radius = 7.dp.toPx(), center = Offset(endTip.x - carrotLength - 8.dp.toPx(), endTip.y))
            }
        }
        ArrowType.STAR -> {
            // Golden Star on a Stick
            drawLine(
                color = Color(0xFFFFD54F).copy(alpha = alpha),
                start = startNock,
                end = endTip,
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = ColorInk.copy(alpha = alpha),
                start = startNock,
                end = endTip,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Star head
            rotate(degrees = angleDeg, pivot = endTip) {
                drawStarSticker(center = endTip, radius = 15.dp.toPx(), alpha = alpha)
            }
        }
    }
}

/**
 * 🌟 Cute Plasticine 5-Pointed Star Sticker with ink outline.
 */
private fun DrawScope.drawStarSticker(
    center: Offset,
    radius: Float,
    alpha: Float
) {
    val inkStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val starPath = Path()
    val points = 5
    val innerRadius = radius * 0.48f

    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius else innerRadius
        val angle = i * PI.toFloat() / points - PI.toFloat() / 2f
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
    }
    starPath.close()

    drawPath(starPath, color = ColorYellow.copy(alpha = alpha))
    drawPath(starPath, color = ColorInk.copy(alpha = alpha), style = inkStroke)

    // Soft shine spark highlight
    drawCircle(
        color = ColorWhite.copy(alpha = alpha * 0.8f),
        radius = radius * 0.22f,
        center = Offset(center.x - radius * 0.2f, center.y - radius * 0.2f)
    )
}
