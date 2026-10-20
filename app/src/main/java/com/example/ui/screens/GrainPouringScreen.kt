package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.screens.Rain.*
import com.example.shared.*
import com.example.shared.scene.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

/**
 * 🌧️ Игра 1: «Дождик» (ШАГ 14)
 * - Ведёшь тучку пальцем (lerp 0.2), идёт дождь (12/8 кап/с).
 * - Падающие капли (1100 dp/с²) брызгают в лужи и на луг.
 * - Из луж выпрыгивают лягушки и утёнок при наборе 20 капель.
 * - На земле вырастают цветы (scale 0->1). При 8 цветах (5 в 2yo): небо проясняется, выходит солнце, радуга, бабочки.
 * - Через 8 секунд праздник сбрасывается для нового цикла.
 */
@Composable
fun GrainPouringScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager(context, soundPlayer) }
    val soundHelper = remember(context, soundPlayer, soundManager) {
        RainSoundHelper(context, soundPlayer, soundManager)
    }
    val haptics = rememberHaptics()
    val ageProfile = LocalAgeProfile.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // Configuration according to age mode
    val maxDropsLimit = if (ageProfile.isToddler2) 40 else 60
    val dropSpawnIntervalMs = if (ageProfile.isToddler2) 125L else 83L // 8 vs 12 drops/sec
    val flowersRequiredForRainbow = if (ageProfile.isToddler2) 5 else 8
    val cloudWidthDp = if (ageProfile.isToddler2) 180f else 140f
    val cloudSpeedMultiplier = if (ageProfile.isToddler2) 0.14f else 0.20f

    // Cloud Position & Touch State
    var isTouchingScreen by remember { mutableStateOf(false) }
    var touchPosDp by remember { mutableStateOf(Offset.Zero) }
    var cloudCenterXDp by remember { mutableFloatStateOf(180f) }
    var cloudCenterYDp by remember { mutableFloatStateOf(100f) }

    // Drops and Particles
    val drops = remember { mutableStateListOf<RainDrop>() }
    val splashParticles = remember { mutableStateListOf<RainSplashParticle>() }

    // Ground Puddles (3 puddles)
    val puddles = remember {
        mutableStateListOf(
            RainPuddle(id = 1, centerXDp = 80f, widthDp = 100f),
            RainPuddle(id = 2, centerXDp = 190f, widthDp = 120f),
            RainPuddle(id = 3, centerXDp = 300f, widthDp = 90f)
        )
    }

    // Growing Flowers on Meadow
    val flowers = remember { mutableStateListOf<GrowingFlower>() }

    // Meadow Residents (Frogs, Duck)
    val animals = remember { mutableStateListOf<MeadowAnimal>() }

    // Rainbow celebration state
    var isRainbowActive by remember { mutableStateOf(false) }
    var rainbowTimerSec by remember { mutableFloatStateOf(0f) }

    // Sky Color transition
    val skyTopColor by animateColorAsState(
        targetValue = if (isRainbowActive) Color(0xFF8FD8FF) else Color(0xFF9EB7D9),
        animationSpec = tween(3000),
        label = "skyTop"
    )
    val skyBottomColor by animateColorAsState(
        targetValue = if (isRainbowActive) Color(0xFFD9F4FF) else Color(0xFFCFE3F5),
        animationSpec = tween(3000),
        label = "skyBottom"
    )

    // Rain loop handle
    var rainLoopHandle by remember { mutableStateOf<LoopHandle?>(null) }

    // Clean up sounds on exit
    DisposableEffect(Unit) {
        onDispose {
            rainLoopHandle?.let { soundManager.stopLoop(it) }
        }
    }

    // Rain loop volume management
    LaunchedEffect(drops.size, isTouchingScreen) {
        if (drops.isNotEmpty() && isTouchingScreen) {
            if (rainLoopHandle == null) {
                rainLoopHandle = soundManager.startLoop("rain_loop")
            }
            val targetVolume = (drops.size.toFloat() / maxDropsLimit).coerceIn(0.2f, 0.9f)
            rainLoopHandle?.let { soundManager.setLoopVolume(it, targetVolume) }
        } else {
            rainLoopHandle?.let {
                soundManager.stopLoop(it, fadeMs = 800)
                rainLoopHandle = null
            }
        }
    }

    // Drop spawner ticker (while touching)
    LaunchedEffect(isTouchingScreen, isRainbowActive) {
        if (isTouchingScreen && !isRainbowActive) {
            while (isActive && isTouchingScreen && !isRainbowActive) {
                if (drops.size < maxDropsLimit) {
                    val spawnOffsetXDp = (Random.nextFloat() - 0.5f) * (cloudWidthDp * 0.7f)
                    drops.add(
                        RainDrop(
                            id = Random.nextLong(),
                            x = cloudCenterXDp + spawnOffsetXDp,
                            y = cloudCenterYDp + 30f
                        )
                    )
                }
                delay(dropSpawnIntervalMs)
            }
        }
    }

    // Main Simulation Loop (120 Hz)
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        val fixedDt = 1f / 120f

        while (isActive) {
            withFrameNanos { nowNanos ->
                if (lastNanos == 0L) {
                    lastNanos = nowNanos
                    return@withFrameNanos
                }
                val frameDt = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
                lastNanos = nowNanos

                // 1. Smooth Cloud movement towards touch (or center when idle)
                val targetCloudX = if (isTouchingScreen) touchPosDp.x else 180f
                cloudCenterXDp += (targetCloudX - cloudCenterXDp) * cloudSpeedMultiplier

                // 2. Rainbow Celebration Timer
                if (isRainbowActive) {
                    rainbowTimerSec += frameDt
                    if (rainbowTimerSec >= 8f) {
                        // Reset for next game cycle
                        isRainbowActive = false
                        rainbowTimerSec = 0f
                        flowers.clear()
                        puddles.forEach {
                            it.dropCount = 0
                            it.hasFrog = false
                            it.hasDuck = false
                        }
                        animals.clear()
                    }
                }

                // 3. Update Raindrops
                val groundLevelDp = 520f // approx ground Y
                for (i in drops.indices.reversed()) {
                    val drop = drops[i]
                    drop.y += drop.vy * frameDt

                    if (drop.y >= groundLevelDp) {
                        // Hit ground!
                        soundHelper.playDropPlip()

                        // Check puddle collision
                        for (puddle in puddles) {
                            if (abs(drop.x - puddle.centerXDp) <= puddle.widthDp / 2f) {
                                puddle.dropCount++
                                puddle.ripplePhase += 0.4f

                                // Check Frog Spawn at 20 drops
                                if (puddle.dropCount >= 20 && !puddle.hasFrog && animals.count { it.soundKey == "animal_frog" } < 2) {
                                    puddle.hasFrog = true
                                    soundHelper.playFrog()
                                    haptics.vibrate(25)
                                    animals.add(
                                        MeadowAnimal(
                                            id = Random.nextLong(),
                                            emoji = "🐸",
                                            soundKey = "animal_frog",
                                            xDp = puddle.centerXDp,
                                            yDp = groundLevelDp - 15f
                                        )
                                    )
                                }
                            }
                        }

                        // Check Duck Spawn when all 3 puddles have 20 drops
                        if (puddles.all { it.dropCount >= 20 } && animals.none { it.soundKey == "animal_duck" }) {
                            soundHelper.playDuck()
                            haptics.vibrate(30)
                            animals.add(
                                MeadowAnimal(
                                    id = Random.nextLong(),
                                    emoji = "🦆",
                                    soundKey = "animal_duck",
                                    xDp = puddles[1].centerXDp,
                                    yDp = groundLevelDp - 18f
                                )
                            )
                        }

                        // Spawn Flower if area not overcrowded
                        if (!isRainbowActive && flowers.size < 12) {
                            val nearFlower = flowers.any { abs(it.xDp - drop.x) < 32f }
                            if (!nearFlower && Random.nextFloat() < 0.25f) {
                                flowers.add(
                                    GrowingFlower(
                                        id = Random.nextLong(),
                                        xDp = drop.x.coerceIn(30f, 330f),
                                        yDp = groundLevelDp + (Random.nextFloat() - 0.5f) * 20f,
                                        color = listOf(ColorPink, ColorYellow, ColorPurple, ColorOrange, ColorBlue).random(),
                                        iconEmoji = listOf("🌸", "🌻", "🌷", "🌺", "🌼").random()
                                    )
                                )
                            }
                        }

                        // Splash particles
                        repeat(3) {
                            val ang = Random.nextFloat() * PI.toFloat()
                            splashParticles.add(
                                RainSplashParticle(
                                    id = Random.nextLong(),
                                    x = drop.x,
                                    y = groundLevelDp,
                                    vx = cos(ang) * (Random.nextFloat() * 80f + 20f),
                                    vy = -sin(ang) * (Random.nextFloat() * 120f + 40f)
                                )
                            )
                        }

                        drops.removeAt(i)
                    }
                }

                // 4. Update Growing Flowers (grow over 2 sec)
                var fullyGrownCount = 0
                for (flower in flowers) {
                    if (flower.progress < 1.0f) {
                        flower.progress += frameDt * 0.5f // 2 seconds to full bloom
                    } else {
                        flower.isFullyGrown = true
                        fullyGrownCount++
                    }
                }

                // Check Rainbow Trigger
                if (!isRainbowActive && fullyGrownCount >= flowersRequiredForRainbow) {
                    isRainbowActive = true
                    rainbowTimerSec = 0f
                    soundHelper.playRainbowChime()
                    soundHelper.playSun()
                    soundHelper.playButterfly()
                    haptics.vibrate(35)
                }

                // 5. Update Splash Particles
                for (i in splashParticles.indices.reversed()) {
                    val p = splashParticles[i]
                    p.ageSec += frameDt
                    p.x += p.vx * frameDt
                    p.y += p.vy * frameDt
                    p.vy += 450f * frameDt
                    p.alpha = (1.0f - p.ageSec / p.lifeSec).coerceIn(0f, 1f)
                    if (p.ageSec >= p.lifeSec) {
                        splashParticles.removeAt(i)
                    }
                }

                // 6. Update Animals Jumping
                for (animal in animals) {
                    if (animal.isJumping) {
                        animal.jumpProgress += frameDt * 2f
                        if (animal.jumpProgress >= 1f) {
                            animal.jumpProgress = 0f
                            animal.isJumping = false
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val screenWidthDp = maxWidth.value
        val screenHeightDp = maxHeight.value

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tapPos ->
                            // Check tap on animals (Frog, Duck)
                            val tapPosDp = Offset(tapPos.x / density.density, tapPos.y / density.density)
                            for (animal in animals) {
                                if (abs(tapPosDp.x - animal.xDp) < 40f && abs(tapPosDp.y - animal.yDp) < 40f) {
                                    animal.isJumping = true
                                    animal.jumpProgress = 0f
                                    if (animal.soundKey == "animal_frog") soundHelper.playFrog() else soundHelper.playDuck()
                                    haptics.vibrateShort()
                                    return@detectTapGestures
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { startPos ->
                            isTouchingScreen = true
                            touchPosDp = Offset(startPos.x / density.density, startPos.y / density.density)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            isTouchingScreen = true
                            touchPosDp = Offset(change.position.x / density.density, change.position.y / density.density)
                        },
                        onDragEnd = {
                            isTouchingScreen = false
                        },
                        onDragCancel = {
                            isTouchingScreen = false
                        }
                    )
                }
        ) {
            val groundYPx = size.height * 0.78f

            // A. SKY BACKGROUND GRADIENT
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(skyTopColor, skyBottomColor),
                    startY = 0f,
                    endY = size.height
                )
            )

            // B. SUN & RAINBOW (If Rainbow Active)
            if (isRainbowActive) {
                // Happy Big Smiling Sun
                val sunCenter = Offset(size.width * 0.82f, size.height * 0.16f)
                drawCircle(color = ColorYellow, radius = 52.dp.toPx(), center = sunCenter)
                drawCircle(color = ColorInk, radius = 52.dp.toPx(), center = sunCenter, style = Stroke(width = 3.dp.toPx()))

                // Giant Rainbow Arc
                val rainbowColors = listOf(ColorPink, ColorOrange, ColorYellow, ColorGreen, ColorBlue, ColorPurple)
                rainbowColors.forEachIndexed { idx, color ->
                    val r = (size.width * 0.70f) + idx * 8.dp.toPx()
                    drawCircle(
                        color = color.copy(alpha = 0.75f),
                        radius = r,
                        center = Offset(size.width / 2f, size.height * 1.05f),
                        style = Stroke(width = 8.dp.toPx())
                    )
                }
            }

            // C. GREEN GROUND MEADOW
            val groundPath = Path().apply {
                moveTo(0f, groundYPx)
                quadraticBezierTo(size.width * 0.5f, groundYPx - 18.dp.toPx(), size.width, groundYPx)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(groundPath, color = Color(0xFF5BDB82))
            drawPath(groundPath, color = ColorInk, style = Stroke(width = 3.dp.toPx()))

            // D. WATER PUDDLES
            for (puddle in puddles) {
                val pX = (puddle.centerXDp / 360f) * size.width
                val pY = groundYPx + 14.dp.toPx()
                val pW = puddle.widthDp.dp.toPx()
                val pH = puddle.heightDp.dp.toPx()

                // Puddle oval
                drawOval(
                    color = Color(0xFF81D4FA),
                    topLeft = Offset(pX - pW / 2f, pY - pH / 2f),
                    size = Size(pW, pH)
                )
                drawOval(
                    color = ColorInk,
                    topLeft = Offset(pX - pW / 2f, pY - pH / 2f),
                    size = Size(pW, pH),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // E. RAINDROPS
            for (drop in drops) {
                val dX = (drop.x / 360f) * size.width
                val dY = (drop.y / 640f) * size.height
                val dropR = 5.dp.toPx()

                val dropPath = Path().apply {
                    moveTo(dX, dY - dropR * 2.2f)
                    lineTo(dX + dropR, dY)
                    quadraticBezierTo(dX, dY + dropR, dX - dropR, dY)
                    close()
                }
                drawPath(dropPath, color = Color(0xFF42A5F5))
            }

            // F. SPLASH PARTICLES
            for (p in splashParticles) {
                val pX = (p.x / 360f) * size.width
                val pY = (p.y / 640f) * size.height
                drawCircle(color = p.color.copy(alpha = p.alpha), radius = p.size.dp.toPx() / 2f, center = Offset(pX, pY))
            }

            // G. GROWING FLOWERS
            for (flower in flowers) {
                val fX = (flower.xDp / 360f) * size.width
                val fY = (flower.yDp / 640f) * size.height
                val fScale = flower.progress

                rotate(fScale * 12f, pivot = Offset(fX, fY)) {
                    val fRadius = 16.dp.toPx() * fScale
                    // Stem
                    drawLine(color = ColorGreen, start = Offset(fX, fY + fRadius), end = Offset(fX, fY + fRadius * 2.2f), strokeWidth = 3.dp.toPx())
                    // Bloom
                    drawCircle(color = flower.color, radius = fRadius, center = Offset(fX, fY))
                    drawCircle(color = ColorInk, radius = fRadius, center = Offset(fX, fY), style = Stroke(width = 2.dp.toPx()))
                    drawCircle(color = ColorYellow, radius = fRadius * 0.45f, center = Offset(fX, fY))
                }
            }

            // H. MEADOW ANIMALS (Frogs & Duck)
            for (animal in animals) {
                val aX = (animal.xDp / 360f) * size.width
                val jumpOffsetY = if (animal.isJumping) -sin(animal.jumpProgress * PI.toFloat()) * 40.dp.toPx() else 0f
                val aY = (animal.yDp / 640f) * size.height + jumpOffsetY

                drawCircle(color = ColorWhite, radius = 22.dp.toPx(), center = Offset(aX, aY))
                drawCircle(color = ColorInk, radius = 22.dp.toPx(), center = Offset(aX, aY), style = Stroke(width = 2.5.dp.toPx()))
            }

            // I. BIG HAPPY RAIN CLOUD
            val cloudXPx = (cloudCenterXDp / 360f) * size.width
            val cloudYPx = cloudCenterYDp.dp.toPx()
            val cloudWPx = cloudWidthDp.dp.toPx()

            drawRainCloud(Offset(cloudXPx, cloudYPx), cloudWPx, isTouchingScreen)
        }
    }
}

/**
 * Draws soft volumetric 3D smiling rain cloud.
 */
private fun DrawScope.drawRainCloud(center: Offset, width: Float, isRaining: Boolean) {
    val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val r = width * 0.28f

    val cloudBrush = Brush.radialGradient(
        colors = if (isRaining) listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB), Color(0xFF90CAF9)) else listOf(Color.White, Color(0xFFECEFF1)),
        center = Offset(center.x - r * 0.3f, center.y - r * 0.3f),
        radius = width * 0.6f
    )

    // Three puffy circles
    drawCircle(brush = cloudBrush, radius = r, center = Offset(center.x - r * 0.7f, center.y + r * 0.15f))
    drawCircle(color = ColorInk, radius = r, center = Offset(center.x - r * 0.7f, center.y + r * 0.15f), style = inkStroke)

    drawCircle(brush = cloudBrush, radius = r, center = Offset(center.x + r * 0.7f, center.y + r * 0.15f))
    drawCircle(color = ColorInk, radius = r, center = Offset(center.x + r * 0.7f, center.y + r * 0.15f), style = inkStroke)

    drawCircle(brush = cloudBrush, radius = r * 1.25f, center = Offset(center.x, center.y - r * 0.25f))
    drawCircle(color = ColorInk, radius = r * 1.25f, center = Offset(center.x, center.y - r * 0.25f), style = inkStroke)

    // Eyes
    val eyeSpacing = r * 0.55f
    val eyeY = center.y - r * 0.1f
    drawCircle(color = ColorInk, radius = 5.dp.toPx(), center = Offset(center.x - eyeSpacing, eyeY))
    drawCircle(color = ColorInk, radius = 5.dp.toPx(), center = Offset(center.x + eyeSpacing, eyeY))

    // Cheeks
    drawCircle(color = ColorPink.copy(alpha = 0.6f), radius = 7.dp.toPx(), center = Offset(center.x - eyeSpacing * 1.3f, eyeY + 8.dp.toPx()))
    drawCircle(color = ColorPink.copy(alpha = 0.6f), radius = 7.dp.toPx(), center = Offset(center.x + eyeSpacing * 1.3f, eyeY + 8.dp.toPx()))

    // Smile
    val smilePath = Path().apply {
        moveTo(center.x - 12.dp.toPx(), eyeY + 12.dp.toPx())
        quadraticBezierTo(center.x, eyeY + 22.dp.toPx(), center.x + 12.dp.toPx(), eyeY + 12.dp.toPx())
    }
    drawPath(smilePath, color = ColorInk, style = inkStroke)
}
