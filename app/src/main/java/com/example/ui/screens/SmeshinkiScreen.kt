package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.HintHand
import com.example.shared.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class SmeshinkiSkin(val title: String, val bodyColor: Color, val secondaryColor: Color, val blushColor: Color) {
    PELMEN("Пельмешек", Color(0xFFFFFDF5), Color(0xFFF3EED9), Color(0xFFFF8595)),
    KOLOBOK("Колобок", Color(0xFFFFD54F), Color(0xFFFFA000), Color(0xFFFF6E40)),
    ZEFIRKA("Зефирка", Color(0xFFFFE4EC), Color(0xFFFFB2C9), Color(0xFFFF4081))
}

enum class SmeshinkiReaction {
    IDLE,
    SNEEZE,       // Нос: мощный чих + звёздочки
    BELLY_PUFF,   // Живот: надувается -> сдувается с хлопком
    TONGUE_FACE,  // Щека: вываливает язык + косые глаза
    HEAD_SPIN,    // Голова: крутится, глаза-спирали + звёздочки
    BUTT_FALL,    // Низ: падение на попу + смех
    HICCUP,       // Двойной тычок: серия иков
    BONUS_LAUGH   // Бонус за 10-12 тыков подряд
}

data class SmeshinkiParticle(
    val id: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val size: Float,
    var alpha: Float,
    val isStar: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmeshinkiScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    onBackClick: () -> Unit = {}
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    var skin by remember { mutableStateOf(SmeshinkiSkin.PELMEN) }
    var reaction by remember { mutableStateOf(SmeshinkiReaction.IDLE) }
    var tapCount by remember { mutableStateOf(0) }
    var consecutiveTaps by remember { mutableStateOf(0) }
    var showIdleHint by remember { mutableStateOf(false) }

    val particles = remember { mutableStateListOf<SmeshinkiParticle>() }

    // Last tap timestamp for double-tap detection
    var lastTapTimestamp by remember { mutableStateOf(0L) }

    // Idle hint timer
    LaunchedEffect(tapCount) {
        if (tapCount == 0) {
            delay(2000L)
            if (tapCount == 0 && reaction == SmeshinkiReaction.IDLE) {
                showIdleHint = true
            }
        } else {
            showIdleHint = false
        }
    }

    // Gentle breathing idle sway
    val infiniteTransition = rememberInfiniteTransition(label = "idle_sway")
    val idleBreathe by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    // Reaction Animation Drivers
    val reactionProgress = remember { Animatable(0f) }
    val headAngle = remember { Animatable(0f) }
    val bellyScale = remember { Animatable(1f) }
    val bodyOffsetY = remember { Animatable(0f) }
    val tongueOut = remember { Animatable(0f) }
    val eyeWobble = remember { Animatable(0f) }

    // Helper to spawn soft burst particles (stars, hearts, droplets)
    fun spawnParticles(centerX: Float, centerY: Float, count: Int = 12, colors: List<Color> = listOf(Color(0xFFFFD54F), Color(0xFFFF80AB), Color(0xFF80DEEA), Color.White)) {
        for (i in 0 until count) {
            val ang = Random.nextFloat() * 2f * PI.toFloat()
            val spd = Random.nextFloat() * 260f + 120f
            particles.add(
                SmeshinkiParticle(
                    id = Random.nextInt(),
                    x = centerX,
                    y = centerY,
                    vx = cos(ang) * spd,
                    vy = sin(ang) * spd - 60f,
                    color = colors.random(),
                    size = Random.nextFloat() * 12f + 8f,
                    alpha = 1f,
                    isStar = i % 2 == 0
                )
            )
        }
    }

    // Particle update ticker loop
    LaunchedEffect(particles.size) {
        if (particles.isNotEmpty()) {
            while (particles.isNotEmpty()) {
                withFrameNanos { _ ->
                    val it = particles.iterator()
                    while (it.hasNext()) {
                        val p = it.next()
                        p.x += p.vx * 0.016f
                        p.y += p.vy * 0.016f
                        p.vy += 350f * 0.016f // gravity
                        p.alpha -= 0.025f
                        if (p.alpha <= 0f) {
                            it.remove()
                        }
                    }
                }
            }
        }
    }

    // Trigger specific cartoon reaction
    fun triggerReaction(type: SmeshinkiReaction, touchX: Float = 0f, touchY: Float = 0f) {
        showIdleHint = false
        tapCount++
        consecutiveTaps++

        // Check for bonus celebration after 10-12 taps
        val actualReaction = if (consecutiveTaps >= 11) {
            consecutiveTaps = 0
            SmeshinkiReaction.BONUS_LAUGH
        } else {
            type
        }

        reaction = actualReaction

        scope.launch {
            when (actualReaction) {
                SmeshinkiReaction.SNEEZE -> {
                    // 1.1s Sneeze: head back -> squint -> ACHOO! -> stars spray
                    soundPlayer.playSneezeApchhi()
                    haptics.vibrateSquish()
                    // Buildup
                    launch { headAngle.animateTo(-16f, tween(320, easing = EaseOutQuad)) }
                    launch { eyeWobble.animateTo(1f, tween(300)) }
                    delay(340)

                    // Explosive sneeze forward
                    haptics.vibratePop()
                    launch { headAngle.animateTo(22f, tween(160, easing = EaseOutBounce)) }
                    launch { bodyOffsetY.animateTo(-25f, tween(160)) }
                    spawnParticles(touchX, touchY, count = 16)
                    delay(260)

                    // Head shake recovery
                    launch { headAngle.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)) }
                    launch { bodyOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                    launch { eyeWobble.animateTo(0f, tween(300)) }
                    delay(300)
                }

                SmeshinkiReaction.BELLY_PUFF -> {
                    // 1.3s Belly: Inflates 1.0 -> 1.45, holds 0.3s -> pops/deflates with laugh
                    soundPlayer.playBellyInhalePop()
                    haptics.vibrate(40)
                    bellyScale.animateTo(1.45f, tween(550, easing = EaseOutBack))
                    delay(220)
                    // Sharp pop
                    haptics.vibratePop()
                    spawnParticles(touchX, touchY, count = 14)
                    launch { bellyScale.animateTo(0.85f, tween(140, easing = EaseInQuad)) }
                    launch { bodyOffsetY.animateTo(-35f, tween(140, easing = EaseOutQuad)) }
                    delay(160)
                    // Recovery bounce
                    soundPlayer.playIdleChuckle()
                    launch { bellyScale.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)) }
                    launch { bodyOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                    delay(300)
                }

                SmeshinkiReaction.TONGUE_FACE -> {
                    // 1.0s Cheek: indents, tongue shoots sideways, eyes go derpy
                    soundPlayer.playTongueBleee()
                    haptics.vibrateSquish()
                    launch { tongueOut.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioHighBouncy)) }
                    launch { eyeWobble.animateTo(1f, tween(200)) }
                    launch { headAngle.animateTo(12f, tween(200)) }
                    delay(550)
                    // Return tongue
                    launch { tongueOut.animateTo(0f, tween(250, easing = EaseInOutQuad)) }
                    launch { eyeWobble.animateTo(0f, tween(250)) }
                    launch { headAngle.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                    delay(250)
                }

                SmeshinkiReaction.HEAD_SPIN -> {
                    // 1.4s Head: spins 2-3 times, dizzy wobble with stars
                    soundPlayer.playHeadDizzySpin()
                    haptics.vibrate(30)
                    launch { eyeWobble.animateTo(2f, tween(200)) } // 2f = spirals
                    headAngle.animateTo(720f, tween(750, easing = EaseInOutCubic))
                    headAngle.snapTo(0f)
                    spawnParticles(touchX, touchY - 40f, count = 12)
                    // Dizzy wobble
                    launch { headAngle.animateTo(14f, tween(150)) }
                    delay(150)
                    launch { headAngle.animateTo(-14f, tween(150)) }
                    delay(150)
                    launch { headAngle.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                    launch { eyeWobble.animateTo(0f, tween(300)) }
                    delay(250)
                }

                SmeshinkiReaction.BUTT_FALL -> {
                    // 1.5s Butt Fall: loses balance -> falls down with thud -> giggles -> stands up
                    soundPlayer.playButtFallLaugh()
                    haptics.vibrateSquish()
                    // Fall down
                    launch { bodyOffsetY.animateTo(110f, tween(300, easing = EaseInQuad)) }
                    launch { bellyScale.animateTo(1.25f, tween(300)) }
                    launch { headAngle.animateTo(-18f, tween(300)) }
                    delay(320)
                    spawnParticles(touchX, touchY + 60f, count = 14)

                    // Giggle shake on bottom
                    repeat(3) {
                        launch { headAngle.animateTo(12f, tween(100)) }
                        delay(100)
                        launch { headAngle.animateTo(-12f, tween(100)) }
                        delay(100)
                    }

                    // Jump back up to feet
                    launch { bodyOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)) }
                    launch { bellyScale.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
                    launch { headAngle.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                    delay(300)
                }

                SmeshinkiReaction.HICCUP -> {
                    // 1.6s Hiccup series: 4 rapid hiccup jumps
                    soundPlayer.playHiccupSeries()
                    repeat(4) {
                        haptics.vibratePop()
                        launch { bodyOffsetY.animateTo(-24f, tween(90, easing = EaseOutQuad)) }
                        launch { bellyScale.animateTo(0.9f, tween(90)) }
                        delay(100)
                        launch { bodyOffsetY.animateTo(0f, tween(110, easing = EaseInQuad)) }
                        launch { bellyScale.animateTo(1f, tween(110)) }
                        delay(120)
                    }
                    soundPlayer.playIdleChuckle()
                    delay(200)
                }

                SmeshinkiReaction.BONUS_LAUGH -> {
                    // Bonus celebration: jumps with fireworks & cheerful laughter
                    soundPlayer.playCuteCharacterSound(1.3f)
                    soundPlayer.playSparkleChime()
                    haptics.vibrate(50)
                    spawnParticles(touchX, touchY, count = 24, colors = listOf(Color(0xFFFFD54F), Color(0xFFFF4081), Color(0xFF00E676), Color(0xFF2979FF), Color.White))
                    repeat(3) {
                        launch { bodyOffsetY.animateTo(-50f, tween(160, easing = EaseOutQuad)) }
                        launch { headAngle.animateTo(15f, tween(160)) }
                        delay(180)
                        launch { bodyOffsetY.animateTo(0f, tween(160, easing = EaseInQuad)) }
                        launch { headAngle.animateTo(-15f, tween(160)) }
                        delay(180)
                    }
                    headAngle.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                    soundPlayer.playBabyGiggle()
                    delay(300)
                }

                SmeshinkiReaction.IDLE -> {}
            }

            // Return to Idle
            reaction = SmeshinkiReaction.IDLE
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Смешинки 😜", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Тыков: $tapCount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                // Общая кнопка «Домой» (80 dp) рисуется поверх верхнего левого угла — оставляем под неё место
                navigationIcon = { androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(88.dp)) },
                actions = {
                    // Switch Character Skin: Pelmen, Kolobok, Zefirka
                    FilledTonalButton(
                        onClick = {
                            val nextIdx = (skin.ordinal + 1) % SmeshinkiSkin.values().size
                            skin = SmeshinkiSkin.values()[nextIdx]
                            soundPlayer.playBubblePop()
                            haptics.vibratePop()
                        },
                        modifier = Modifier.padding(end = 8.dp).testTag("skin_switcher_btn")
                    ) {
                        Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(skin.title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = skin.bodyColor.copy(alpha = 0.5f)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(skin.secondaryColor.copy(alpha = 0.25f), skin.bodyColor)
                    )
                )
                .padding(innerPadding)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val now = System.currentTimeMillis()
                        val isDoubleTap = (now - lastTapTimestamp) < 320L
                        lastTapTimestamp = now

                        if (isDoubleTap) {
                            triggerReaction(SmeshinkiReaction.HICCUP, offset.x, offset.y)
                            return@detectTapGestures
                        }

                        // Map touch coordinate relative to screen height
                        val normY = offset.y / size.height.toFloat()
                        val normX = offset.x / size.width.toFloat()

                        when {
                            // Head (Top 25%)
                            normY < 0.28f -> triggerReaction(SmeshinkiReaction.HEAD_SPIN, offset.x, offset.y)
                            // Nose (Middle center, normY between 0.28 and 0.44, normX between 0.38 and 0.62)
                            normY in 0.28f..0.44f && normX in 0.38f..0.62f -> triggerReaction(SmeshinkiReaction.SNEEZE, offset.x, offset.y)
                            // Cheek (Middle sides, normY between 0.28 and 0.46, outer X)
                            normY in 0.28f..0.46f -> triggerReaction(SmeshinkiReaction.TONGUE_FACE, offset.x, offset.y)
                            // Belly (Center body, normY between 0.46 and 0.72)
                            normY in 0.46f..0.72f -> triggerReaction(SmeshinkiReaction.BELLY_PUFF, offset.x, offset.y)
                            // Legs / Bottom (Below 0.72)
                            else -> triggerReaction(SmeshinkiReaction.BUTT_FALL, offset.x, offset.y)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Main Character Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("smeshinki_character_canvas")
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f + bodyOffsetY.value

                val baseRadius = size.minDimension * 0.34f
                val effectiveBreathe = if (reaction == SmeshinkiReaction.IDLE) idleBreathe else 1f
                val rX = baseRadius * bellyScale.value * effectiveBreathe
                val rY = baseRadius * (2f - bellyScale.value * 0.7f).coerceIn(0.6f, 1.4f) * effectiveBreathe

                // 1. Soft Shadow on Floor
                drawOval(
                    color = Color.Black.copy(alpha = 0.12f),
                    topLeft = Offset(cx - rX * 0.85f, cy + rY * 0.85f),
                    size = Size(rX * 1.7f, 32.dp.toPx())
                )

                rotate(degrees = headAngle.value, pivot = Offset(cx, cy)) {
                    // 2. Main Soft Squishy Body (Claymorphism style)
                    val bodyBrush = Brush.radialGradient(
                        colors = listOf(Color.White, skin.bodyColor, skin.secondaryColor),
                        center = Offset(cx - rX * 0.25f, cy - rY * 0.25f),
                        radius = rX * 1.3f
                    )

                    drawOval(
                        brush = bodyBrush,
                        topLeft = Offset(cx - rX, cy - rY),
                        size = Size(rX * 2f, rY * 2f)
                    )

                    // Dough folds for Pelmen skin
                    if (skin == SmeshinkiSkin.PELMEN) {
                        for (i in -3..3) {
                            val foldAngle = (i * 18f) * PI.toFloat() / 180f
                            val fx = cx + sin(foldAngle) * (rX * 0.88f)
                            val fy = cy - cos(foldAngle) * (rY * 0.88f)
                            drawCircle(
                                color = skin.secondaryColor.copy(alpha = 0.75f),
                                radius = 14.dp.toPx(),
                                center = Offset(fx, fy),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }

                    // Soft Outline for clay/rubber feel
                    drawOval(
                        color = Color.Black.copy(alpha = 0.15f),
                        topLeft = Offset(cx - rX, cy - rY),
                        size = Size(rX * 2f, rY * 2f),
                        style = Stroke(width = 3.5.dp.toPx())
                    )

                    // 3. Cute Rosy Cheeks
                    val cheekRadius = 22.dp.toPx()
                    drawCircle(
                        color = skin.blushColor.copy(alpha = 0.55f),
                        radius = cheekRadius,
                        center = Offset(cx - rX * 0.55f, cy + 10.dp.toPx())
                    )
                    drawCircle(
                        color = skin.blushColor.copy(alpha = 0.55f),
                        radius = cheekRadius,
                        center = Offset(cx + rX * 0.55f, cy + 10.dp.toPx())
                    )

                    // 4. Animated Eyes
                    val eyeOffsetX = rX * 0.32f
                    val eyeOffsetY = cy - rY * 0.18f

                    when {
                        // Spirals when dizzy
                        eyeWobble.value >= 1.8f -> {
                            drawSpiralEye(Offset(cx - eyeOffsetX, eyeOffsetY), 18.dp.toPx())
                            drawSpiralEye(Offset(cx + eyeOffsetX, eyeOffsetY), 18.dp.toPx())
                        }
                        // Squinted tightly during sneeze
                        reaction == SmeshinkiReaction.SNEEZE && eyeWobble.value > 0.5f -> {
                            // > < squinting eyes
                            drawLine(
                                color = Color.Black,
                                start = Offset(cx - eyeOffsetX - 15f, eyeOffsetY - 10f),
                                end = Offset(cx - eyeOffsetX + 15f, eyeOffsetY + 10f),
                                strokeWidth = 5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = Color.Black,
                                start = Offset(cx - eyeOffsetX - 15f, eyeOffsetY + 10f),
                                end = Offset(cx - eyeOffsetX + 15f, eyeOffsetY - 10f),
                                strokeWidth = 5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = Color.Black,
                                start = Offset(cx + eyeOffsetX - 15f, eyeOffsetY - 10f),
                                end = Offset(cx + eyeOffsetX + 15f, eyeOffsetY + 10f),
                                strokeWidth = 5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = Color.Black,
                                start = Offset(cx + eyeOffsetX - 15f, eyeOffsetY + 10f),
                                end = Offset(cx + eyeOffsetX + 15f, eyeOffsetY - 10f),
                                strokeWidth = 5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                        // Derpy googly eyes when tongue is out
                        tongueOut.value > 0.2f -> {
                            // Left eye looks left-up, Right eye looks crossed
                            drawGooglyEye(Offset(cx - eyeOffsetX, eyeOffsetY), 22.dp.toPx(), pupilOffset = Offset(-6f, -5f))
                            drawGooglyEye(Offset(cx + eyeOffsetX, eyeOffsetY), 22.dp.toPx(), pupilOffset = Offset(-8f, 6f))
                        }
                        // Normal big cartoon eyes with cute highlights
                        else -> {
                            drawNormalEye(Offset(cx - eyeOffsetX, eyeOffsetY), 22.dp.toPx())
                            drawNormalEye(Offset(cx + eyeOffsetX, eyeOffsetY), 22.dp.toPx())
                        }
                    }

                    // 5. Cute Nose (Touch target)
                    drawCircle(
                        color = skin.blushColor,
                        radius = 11.dp.toPx(),
                        center = Offset(cx, cy - 8.dp.toPx())
                    )

                    // 6. Mouth / Tongue
                    if (tongueOut.value > 0.05f) {
                        // Wide funny open mouth
                        drawArc(
                            color = Color(0xFF6B1D2F),
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = Offset(cx - 32.dp.toPx(), cy + 6.dp.toPx()),
                            size = Size(64.dp.toPx(), 44.dp.toPx())
                        )
                        // Springy pink tongue protruding sideways
                        val tongueLen = 50.dp.toPx() * tongueOut.value
                        drawRoundRect(
                            color = Color(0xFFFF5252),
                            topLeft = Offset(cx - 8.dp.toPx(), cy + 22.dp.toPx()),
                            size = Size(36.dp.toPx(), tongueLen),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx(), 18.dp.toPx())
                        )
                    } else if (reaction == SmeshinkiReaction.BONUS_LAUGH || reaction == SmeshinkiReaction.BUTT_FALL) {
                        // Big open laughing mouth
                        drawArc(
                            color = Color(0xFF8B263E),
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = Offset(cx - 36.dp.toPx(), cy + 6.dp.toPx()),
                            size = Size(72.dp.toPx(), 48.dp.toPx())
                        )
                        // Tongue inside
                        drawCircle(
                            color = Color(0xFFFF5252),
                            radius = 16.dp.toPx(),
                            center = Offset(cx, cy + 34.dp.toPx())
                        )
                    } else {
                        // Happy sweet smile curve
                        val smilePath = Path().apply {
                            moveTo(cx - 28.dp.toPx(), cy + 18.dp.toPx())
                            quadraticBezierTo(
                                cx, cy + 38.dp.toPx(),
                                cx + 28.dp.toPx(), cy + 18.dp.toPx()
                            )
                        }
                        drawPath(
                            path = smilePath,
                            color = Color.Black.copy(alpha = 0.85f),
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // 7. Belly Button (Tummy tickle spot)
                    drawCircle(
                        color = skin.secondaryColor,
                        radius = 7.dp.toPx(),
                        center = Offset(cx, cy + rY * 0.45f)
                    )
                }

                // 8. Draw Active Particles (Stars, Hearts, Droplets)
                for (p in particles) {
                    if (p.isStar) {
                        drawStar(Offset(p.x, p.y), p.size, p.color.copy(alpha = p.alpha.coerceIn(0f, 1f)))
                    } else {
                        drawCircle(
                            color = p.color.copy(alpha = p.alpha.coerceIn(0f, 1f)),
                            radius = p.size / 2f,
                            center = Offset(p.x, p.y)
                        )
                    }
                }
            }

            // Interactive Guidance Hand for 2-year-olds (First 2 seconds idle)
            if (showIdleHint) {
                Box(modifier = Modifier.align(Alignment.Center)) {
                    HintHand(
                        from = Offset(0f, 80f),
                        mode = "tap",
                        delayMs = 200
                    )
                }
            }
        }
    }
}

// Drawing Helpers for Cute Character Eyes and Particles
private fun DrawScope.drawNormalEye(center: Offset, radius: Float) {
    // Sclera
    drawCircle(color = Color.White, radius = radius, center = center)
    drawCircle(color = Color.Black.copy(alpha = 0.2f), radius = radius, center = center, style = Stroke(width = 2.5f))
    // Pupil
    drawCircle(color = Color(0xFF212121), radius = radius * 0.65f, center = center)
    // Cute Highlights
    drawCircle(color = Color.White, radius = radius * 0.26f, center = Offset(center.x - radius * 0.2f, center.y - radius * 0.25f))
    drawCircle(color = Color.White, radius = radius * 0.14f, center = Offset(center.x + radius * 0.25f, center.y + radius * 0.2f))
}

private fun DrawScope.drawGooglyEye(center: Offset, radius: Float, pupilOffset: Offset) {
    drawCircle(color = Color.White, radius = radius, center = center)
    drawCircle(color = Color.Black.copy(alpha = 0.2f), radius = radius, center = center, style = Stroke(width = 2.5f))
    // Off-center pupil
    drawCircle(color = Color(0xFF212121), radius = radius * 0.62f, center = center + pupilOffset)
    drawCircle(color = Color.White, radius = radius * 0.22f, center = center + pupilOffset - Offset(3f, 3f))
}

private fun DrawScope.drawSpiralEye(center: Offset, radius: Float) {
    drawCircle(color = Color.White, radius = radius, center = center)
    val spiralPath = Path()
    val steps = 40
    for (i in 0 until steps) {
        val angle = i * 0.35f
        val r = (i.toFloat() / steps) * radius * 0.85f
        val x = center.x + cos(angle) * r
        val y = center.y + sin(angle) * r
        if (i == 0) spiralPath.moveTo(x, y) else spiralPath.lineTo(x, y)
    }
    drawPath(path = spiralPath, color = Color(0xFF212121), style = Stroke(width = 3.5f, cap = StrokeCap.Round))
}

private fun DrawScope.drawStar(center: Offset, size: Float, color: Color) {
    val path = Path()
    val points = 5
    val outerR = size
    val innerR = size * 0.45f
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outerR else innerR
        val angle = (i * PI / points - PI / 2).toFloat()
        val x = center.x + cos(angle) * r
        val y = center.y + sin(angle) * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = color)
}
