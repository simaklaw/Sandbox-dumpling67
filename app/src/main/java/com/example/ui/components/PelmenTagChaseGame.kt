package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.example.shared.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.model.GameState
import com.example.model.PelmenAccessoryCatalog
import com.example.model.SquishyMaterialType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Visual particle bursting when Pelmen is caught in tag mode.
 */
data class TagSparkleParticle(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    var rotation: Float,
    var vRot: Float,
    val color: Color,
    val emoji: String? = null,
    var alpha: Float = 1.0f
)

/**
 * Expanding comic shockwave ring on tag impact.
 */
data class TagShockwave(
    val id: Long,
    val x: Float,
    val y: Float,
    var radius: Float = 12f,
    val maxRadius: Float = 95f,
    val color: Color,
    var alpha: Float = 1.0f
)

/**
 * Floating score or comic text popup (e.g. "+25 ⭐", "КОМБО х3!").
 */
data class TagFloatingText(
    val id: Long,
    var x: Float,
    var y: Float,
    val text: String,
    val color: Color,
    var size: Float = 26f,
    var alpha: Float = 1.0f
)

/**
 * Floating power-up bonus bubble floating across the playground.
 */
data class TagBonusBubble(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val type: BonusBubbleType,
    val radius: Float = 36f,
    var wobble: Float = 0f
)

enum class BonusBubbleType(
    val emoji: String,
    val title: String,
    val color: Color
) {
    FREEZE("❄️", "Заморозка!", Color(0xFF00E5FF)),
    MAGNET("🧲", "Супер-Магнит!", Color(0xFFFF1744)),
    STAR_BONUS("⭐", "+100 Очков!", Color(0xFFFFD600)),
    MINI_CLONES("🥟", "Клоны-Пельмешки!", Color(0xFFFF9100)),
    COIN_BURST("🪙", "+5 Монеток!", Color(0xFFFFAB00))
}

/**
 * Mini running helper dumpling clone.
 */
data class MiniPelmenClone(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var lifeFrames: Int = 360,
    var squishX: Float = 1f,
    var squishY: Float = 1f
)

/**
 * Speed difficulty presets for toddler play.
 */
enum class TagDifficulty(
    val title: String,
    val emoji: String,
    val baseSpeed: Float,
    val changeDirInterval: Int,
    val scoreMultiplier: Int
) {
    TODDLER("Малыш", "🐢", 3.2f, 90, 1),
    FUN_RUN("Шустрик", "🏃‍♂️", 6.5f, 50, 2),
    TURBO("Турбо", "🚀", 10.5f, 30, 3)
}

/**
 * Interactive "Догонялки с Пельмень-Сикс" (Tag / Catch Me If You Can) Mini-Game.
 *
 * Pelmen-Six chaotically runs, hops, somersaults and zooms across the screen.
 * Toddler taps on him to catch him, trigger funny squish deformations, score points,
 * combos, coin rewards, particle fireworks, and capture floating bonus bubbles!
 */
@Composable
fun PelmenTagChaseGame(
    modifier: Modifier = Modifier,
    gameState: GameState,
    materialType: SquishyMaterialType,
    soundPlayer: SoundEffectPlayer,
    repository: GameRepository,
    onSwitchToAntistress: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    val view = LocalView.current

    // Game Mode & Difficulty
    var difficulty by remember { mutableStateOf(TagDifficulty.FUN_RUN) }
    var score by remember { mutableIntStateOf(0) }
    var highScore by remember { mutableIntStateOf(0) }
    var catchCount by remember { mutableIntStateOf(0) }
    var comboCount by remember { mutableIntStateOf(0) }
    var comboMultiplier by remember { mutableIntStateOf(1) }
    var lastCatchTimeMs by remember { mutableStateOf(System.currentTimeMillis()) }

    // Physical Position & State of Pelmen-Six
    var pelmenX by remember { mutableFloatStateOf(300f) }
    var pelmenY by remember { mutableFloatStateOf(400f) }
    var pelmenVx by remember { mutableFloatStateOf(3.5f) }
    var pelmenVy by remember { mutableFloatStateOf(2.5f) }
    var pelmenHeadingAngle by remember { mutableFloatStateOf(0f) }
    var isFacingRight by remember { mutableStateOf(true) }

    // States & Powerups
    var isDizzyCaught by remember { mutableStateOf(false) }
    var dizzyFramesLeft by remember { mutableIntStateOf(0) }

    var isFrozen by remember { mutableStateOf(false) }
    var freezeFramesLeft by remember { mutableIntStateOf(0) }

    var isMagnetActive by remember { mutableStateOf(false) }
    var magnetFramesLeft by remember { mutableIntStateOf(0) }

    var isTurboBoost by remember { mutableStateOf(false) }
    var turboFramesLeft by remember { mutableIntStateOf(0) }

    var comicSpeechText by remember { mutableStateOf("Догони меня! 😜") }
    var speechTimerFrames by remember { mutableIntStateOf(120) }

    // Celebration milestone banner
    var showMilestoneBanner by remember { mutableStateOf(false) }
    var milestoneText by remember { mutableStateOf("") }

    // Visual lists
    val sparkles = remember { mutableStateListOf<TagSparkleParticle>() }
    val shockwaves = remember { mutableStateListOf<TagShockwave>() }
    val floatingTexts = remember { mutableStateListOf<TagFloatingText>() }
    val bonusBubbles = remember { mutableStateListOf<TagBonusBubble>() }
    val miniClones = remember { mutableStateListOf<MiniPelmenClone>() }

    // Physical Deformation Animators
    val bodySquishX = remember { Animatable(1f) }
    val bodySquishY = remember { Animatable(1f) }
    val bodyTiltAngle = remember { Animatable(0f) }

    // Continuous running leg & bounce animations
    val infiniteTransition = rememberInfiniteTransition(label = "tag_pelmen_cycle")
    val runStepCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(280, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "run_step_cycle"
    )

    val dizzyStarRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dizzy_rotation"
    )

    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_pulse"
    )

    // Sound loop: footsteps when running
    LaunchedEffect(isDizzyCaught, isFrozen) {
        if (!isDizzyCaught && !isFrozen) {
            var left = true
            while (!isDizzyCaught && !isFrozen) {
                val delayMs = when (difficulty) {
                    TagDifficulty.TODDLER -> 340L
                    TagDifficulty.FUN_RUN -> 210L
                    TagDifficulty.TURBO -> 130L
                }
                delay(delayMs)
                if (!isDizzyCaught && !isFrozen) {
                    soundPlayer.playPatterStep(left)
                    left = !left
                }
            }
        }
    }

    // Comical speech periodic chatter
    LaunchedEffect(Unit) {
        val sillyRunningPhrases = listOf(
            "Не поймаешь! 🏃‍♂️💨",
            "Я быстрый пельмеш! 🥟✨",
            "ХА-ХА! Опять убежал! 😜",
            "Тут я! Лови скорее! 🎈",
            "Ой, куда я бегу?! 🤪",
            "Уииии! 🚀",
            "Щекотно в животике! 💖"
        )
        while (true) {
            delay(2800L)
            if (!isDizzyCaught && !isFrozen) {
                comicSpeechText = sillyRunningPhrases.random()
                if (Random.nextFloat() < 0.4f) {
                    soundPlayer.playCuteCharacterSound(Random.nextFloat() * 0.4f + 0.9f)
                }
            }
        }
    }

    // Spawn bonus bubbles periodically
    LaunchedEffect(Unit) {
        while (true) {
            delay(5500L)
            if (bonusBubbles.size < 4) {
                val type = BonusBubbleType.values().random()
                val bubble = TagBonusBubble(
                    id = System.nanoTime(),
                    x = Random.nextFloat() * 500f + 100f,
                    y = 900f,
                    vx = (Random.nextFloat() - 0.5f) * 1.8f,
                    vy = -(Random.nextFloat() * 1.8f + 1.6f),
                    type = type
                )
                bonusBubbles.add(bubble)
            }
        }
    }

    // Combo timer decay check
    LaunchedEffect(comboCount, lastCatchTimeMs) {
        while (comboCount > 0) {
            delay(500)
            if (System.currentTimeMillis() - lastCatchTimeMs > 4200L) {
                comboCount = 0
                comboMultiplier = 1
            }
        }
    }

    // Main 60 FPS Physics Simulation Loop
    LaunchedEffect(difficulty) {
        var frameCounter = 0
        var dirChangeFrames = 0
        var lastHopTimeMs = 0L
        val targetChangeInterval = difficulty.changeDirInterval

        while (isActive) {
            withFrameNanos {
                frameCounter++

                // 1. Pelmen-Six Movement Logic
                if (isFrozen) {
                    freezeFramesLeft--
                    if (freezeFramesLeft <= 0) {
                        isFrozen = false
                        comicSpeechText = "ОТТАЯЛ! ПОБЕЖАЛИ! 🏃‍♂️"
                        soundPlayer.playBoing(1.3f)
                    }
                } else if (isDizzyCaught) {
                    dizzyFramesLeft--
                    if (dizzyFramesLeft <= 0) {
                        isDizzyCaught = false
                        comicSpeechText = "ХА-ХА! ОПЯТЬ УБЕГАЮ! 💨"
                        soundPlayer.playPelmenGiggleRun()
                        // Burst of speed after escaping dizzy
                        val angle = Random.nextFloat() * 2f * PI.toFloat()
                        val spd = difficulty.baseSpeed * 1.4f
                        pelmenVx = cos(angle) * spd
                        pelmenVy = sin(angle) * spd
                    }
                } else {
                    // Normal chaotic running
                    dirChangeFrames++
                    if (dirChangeFrames >= targetChangeInterval || Random.nextFloat() < 0.035f) {
                        dirChangeFrames = 0
                        // Chaotic direction alteration
                        val baseSpeed = if (isTurboBoost) difficulty.baseSpeed * 1.8f else difficulty.baseSpeed
                        val currentAngle = atan2(pelmenVy, pelmenVx)
                        // Zig-zag deflection or full comic reversal
                        val deltaAngle = (Random.nextFloat() - 0.5f) * (PI.toFloat() * 1.3f)
                        val newAngle = currentAngle + deltaAngle
                        val speedMag = baseSpeed * (Random.nextFloat() * 0.6f + 0.75f)

                        pelmenVx = cos(newAngle) * speedMag
                        pelmenVy = sin(newAngle) * speedMag

                        // Chance to do a high bounce hop with debouncing
                        val now = System.currentTimeMillis()
                        if (Random.nextFloat() < 0.25f && (now - lastHopTimeMs) > 1200L) {
                            lastHopTimeMs = now
                            soundPlayer.playBoing(Random.nextFloat() * 0.3f + 1.2f)
                            coroutineScope.launch {
                                bodySquishX.animateTo(0.75f, spring(Spring.DampingRatioHighBouncy))
                                bodySquishX.animateTo(1.0f, spring(Spring.DampingRatioMediumBouncy))
                            }
                            coroutineScope.launch {
                                bodySquishY.animateTo(1.35f, spring(Spring.DampingRatioHighBouncy))
                                bodySquishY.animateTo(1.0f, spring(Spring.DampingRatioMediumBouncy))
                            }
                        }
                    }

                    // Update Position
                    pelmenX += pelmenVx
                    pelmenY += pelmenVy

                    // Facing direction
                    if (pelmenVx > 0.4f) isFacingRight = true
                    if (pelmenVx < -0.4f) isFacingRight = false

                    // Heading tilt
                    pelmenHeadingAngle = (pelmenVx * 2.2f).coerceIn(-25f, 25f)

                    // Spawn dust puff trails behind feet
                    if (frameCounter % 6 == 0) {
                        sparkles.add(
                            TagSparkleParticle(
                                id = System.nanoTime(),
                                x = pelmenX + (if (isFacingRight) -30f else 30f),
                                y = pelmenY + 45f,
                                vx = (Random.nextFloat() - 0.5f) * 1.5f,
                                vy = -Random.nextFloat() * 1.2f,
                                size = Random.nextFloat() * 7f + 5f,
                                rotation = Random.nextFloat() * 360f,
                                vRot = (Random.nextFloat() - 0.5f) * 6f,
                                color = Color.White.copy(alpha = 0.6f),
                                emoji = if (Random.nextBoolean()) "✨" else null,
                                alpha = 0.7f
                            )
                        )
                    }
                }

                // Turbo / Magnet Timers
                if (isTurboBoost) {
                    turboFramesLeft--
                    if (turboFramesLeft <= 0) isTurboBoost = false
                }
                if (isMagnetActive) {
                    magnetFramesLeft--
                    if (magnetFramesLeft <= 0) isMagnetActive = false
                }

                // 2. Bonus Bubbles Movement
                for (i in bonusBubbles.indices.reversed()) {
                    val b = bonusBubbles[i]
                    b.x += b.vx
                    b.y += b.vy
                    b.wobble += 0.08f
                    if (b.y < -80f || b.x < -60f || b.x > 1200f) {
                        bonusBubbles.removeAt(i)
                    }
                }

                // 3. Mini Clones Simulation
                for (i in miniClones.indices.reversed()) {
                    val c = miniClones[i]
                    c.lifeFrames--
                    c.x += c.vx
                    c.y += c.vy
                    if (Random.nextFloat() < 0.05f) {
                        c.vx = (Random.nextFloat() - 0.5f) * 8f
                        c.vy = (Random.nextFloat() - 0.5f) * 8f
                    }
                    if (c.lifeFrames <= 0) {
                        miniClones.removeAt(i)
                    }
                }

                // 4. Particles & Floating Texts
                for (i in sparkles.indices.reversed()) {
                    val p = sparkles[i]
                    p.x += p.vx
                    p.y += p.vy
                    p.vy += 0.18f
                    p.vx *= 0.96f
                    p.rotation += p.vRot
                    p.alpha -= 0.024f
                    if (p.alpha <= 0f) sparkles.removeAt(i)
                }

                for (i in shockwaves.indices.reversed()) {
                    val sw = shockwaves[i]
                    sw.radius += 4.5f
                    sw.alpha = (1f - (sw.radius / sw.maxRadius)).coerceIn(0f, 1f)
                    if (sw.radius >= sw.maxRadius) shockwaves.removeAt(i)
                }

                for (i in floatingTexts.indices.reversed()) {
                    val ft = floatingTexts[i]
                    ft.y -= 2.2f
                    ft.size += 0.3f
                    ft.alpha -= 0.028f
                    if (ft.alpha <= 0f) floatingTexts.removeAt(i)
                }
            }
        }
    }

    // Tap Handling: Catching Pelmen-Six or Bonus Bubbles
    fun onCatchPelmen(tapPos: Offset, arenaWidth: Float, arenaHeight: Float) {
        // 1. Check Bonus Bubble hits first
        for (i in bonusBubbles.indices.reversed()) {
            val b = bonusBubbles[i]
            val dx = tapPos.x - b.x
            val dy = tapPos.y - b.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist <= b.radius * 1.5f + 25f) {
                // Popped bonus bubble!
                soundPlayer.playBubblePop()
                soundPlayer.performItemCollectHaptic(view)
                val popType = b.type
                bonusBubbles.removeAt(i)

                // Trigger Power-Up Effects via SoundPool & Haptics
                when (popType) {
                    BonusBubbleType.FREEZE -> {
                        isFrozen = true
                        freezeFramesLeft = 210 // 3.5 seconds
                        soundPlayer.playPelmeshCollectItem("FREEZE", 0.9f)
                        soundPlayer.playFreezeIceCrystal()
                        comicSpeechText = "БРРР! Я ЗАМЁРЗ! ❄️🥶"
                    }
                    BonusBubbleType.MAGNET -> {
                        isMagnetActive = true
                        magnetFramesLeft = 360 // 6 seconds
                        soundPlayer.playPelmeshCollectItem("MAGNET", 1.2f)
                        soundPlayer.playShieldChime()
                        comicSpeechText = "МАГНИТНЫЙ ПЕЛЬМЕШ! 🧲✨"
                    }
                    BonusBubbleType.STAR_BONUS -> {
                        val bonusPts = 100 * difficulty.scoreMultiplier
                        score += bonusPts
                        if (score > highScore) highScore = score
                        soundPlayer.playPelmeshCollectItem("STAR", 1.4f)
                        soundPlayer.playFanfare()
                        floatingTexts.add(
                            TagFloatingText(
                                id = System.nanoTime(),
                                x = b.x,
                                y = b.y - 20f,
                                text = "+$bonusPts ⭐ СУПЕР ЗВЕЗДА!",
                                color = Color(0xFFFFD600),
                                size = 32f
                            )
                        )
                    }
                    BonusBubbleType.MINI_CLONES -> {
                        soundPlayer.playPelmeshCollectItem("CLONES", 1.1f)
                        soundPlayer.playBabyGiggle()
                        repeat(4) { idx ->
                            miniClones.add(
                                MiniPelmenClone(
                                    id = System.nanoTime() + idx,
                                    x = b.x + (Random.nextFloat() - 0.5f) * 60f,
                                    y = b.y + (Random.nextFloat() - 0.5f) * 60f,
                                    vx = (Random.nextFloat() - 0.5f) * 9f,
                                    vy = (Random.nextFloat() - 0.5f) * 9f
                                )
                            )
                        }
                        comicSpeechText = "ОГО! СКОЛЬКО НАС! 🥟🥟🥟"
                    }
                    BonusBubbleType.COIN_BURST -> {
                        soundPlayer.playPelmeshCollectItem("COIN", 1.3f)
                        soundPlayer.playShieldChime()
                        repository.addCoins(5)
                        floatingTexts.add(
                            TagFloatingText(
                                id = System.nanoTime(),
                                x = b.x,
                                y = b.y - 20f,
                                text = "+5 🪙 МОНЕТКИ!",
                                color = Color(0xFFFFAB00),
                                size = 30f
                            )
                        )
                    }
                }

                // Sparkles for bubble
                repeat(12) {
                    val angle = Random.nextFloat() * 2f * PI.toFloat()
                    val spd = Random.nextFloat() * 8f + 3f
                    sparkles.add(
                        TagSparkleParticle(
                            id = System.nanoTime() + it,
                            x = b.x,
                            y = b.y,
                            vx = cos(angle) * spd,
                            vy = sin(angle) * spd,
                            size = Random.nextFloat() * 8f + 6f,
                            rotation = Random.nextFloat() * 360f,
                            vRot = (Random.nextFloat() - 0.5f) * 16f,
                            color = popType.color,
                            emoji = popType.emoji
                        )
                    )
                }
                return
            }
        }

        // 2. Check Mini Clones hits
        for (i in miniClones.indices.reversed()) {
            val c = miniClones[i]
            val dx = tapPos.x - c.x
            val dy = tapPos.y - c.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist <= 65f) {
                soundPlayer.playPelmeshCollectItem("MINI_PELMEN", 1.5f)
                soundPlayer.performItemCollectHaptic(view)
                miniClones.removeAt(i)
                val clonePoints = 20 * difficulty.scoreMultiplier
                score += clonePoints
                if (score > highScore) highScore = score
                repository.addCoins(1)

                floatingTexts.add(
                    TagFloatingText(
                        id = System.nanoTime(),
                        x = c.x,
                        y = c.y - 15f,
                        text = "+$clonePoints 🥟",
                        color = Color(0xFFFF9100),
                        size = 24f
                    )
                )
                return
            }
        }

        // 3. Check Pelmen-Six Catch hit!
        val hitRadius = if (isMagnetActive) 130f else 85f
        val dx = tapPos.x - pelmenX
        val dy = tapPos.y - pelmenY
        val dist = sqrt(dx * dx + dy * dy)

        if (dist <= hitRadius) {
            // CAUGHT PELMEN-SIX!
            catchCount++
            comboCount++
            lastCatchTimeMs = System.currentTimeMillis()

            comboMultiplier = when {
                comboCount >= 8 -> 5
                comboCount >= 5 -> 3
                comboCount >= 3 -> 2
                else -> 1
            }

            val gainedScore = 15 * comboMultiplier * difficulty.scoreMultiplier
            score += gainedScore
            if (score > highScore) highScore = score

            val coinsReward = if (comboMultiplier > 2) 2 else 1
            repository.addCoins(coinsReward)

            isDizzyCaught = true
            dizzyFramesLeft = 60 // 1 second dizzy stun

            // Sound & Haptics via SoundPool + HapticFeedbackConstants
            soundPlayer.performSquishHaptic(1.0f, view)
            soundPlayer.playPelmenTagCaught(Random.nextFloat() * 0.3f + 0.95f)
            if (comboCount % 3 == 0) {
                soundPlayer.playBabyGiggle()
            }

            // Comic text responses
            val sillyCaughtPhrases = listOf(
                "ОЙ, ПОЙМАЛ! 🥟",
                "ЩЕКОТНО! 😆",
                "АЙ-АЙ, ПОПАЛСЯ! 💖",
                "ХА-ХА, ТЫ БЫСТРЫЙ! 🚀",
                "БОНЬК! 🔨",
                "ОЙ! ГОЛОВА КРУГОМ! 💫",
                "ВОТ ЭТО ЛОВКОСТЬ! ⭐"
            )
            comicSpeechText = sillyCaughtPhrases.random()

            // Floating score popup
            val comboPrefix = if (comboMultiplier > 1) "🔥 x$comboMultiplier! " else ""
            floatingTexts.add(
                TagFloatingText(
                    id = System.nanoTime(),
                    x = pelmenX,
                    y = pelmenY - 45f,
                    text = "$comboPrefix+$gainedScore ⭐",
                    color = when (comboMultiplier) {
                        5 -> Color(0xFFFF1744)
                        3 -> Color(0xFFFF9100)
                        2 -> Color(0xFFFFD600)
                        else -> Color(0xFF00E676)
                    },
                    size = if (comboMultiplier > 1) 32f else 26f
                )
            )

            // Expanding Shockwave
            shockwaves.add(
                TagShockwave(
                    id = System.nanoTime(),
                    x = pelmenX,
                    y = pelmenY,
                    color = materialType.rimColor
                )
            )

            // Star, Dumpling and Heart Particle Burst
            val palette = listOf(
                Color(0xFFFFD600), Color(0xFFFF9100), Color(0xFFFF1744),
                Color(0xFF00E5FF), Color(0xFF76FF03), Color(0xFFE040FB), Color.White
            )
            val burstEmojis = listOf("✨", "🥟", "⭐", "💖", "🎉", "🌟")

            repeat(16) { i ->
                val angle = Random.nextFloat() * 2f * PI.toFloat()
                val spd = Random.nextFloat() * 11f + 4f
                sparkles.add(
                    TagSparkleParticle(
                        id = System.nanoTime() + i,
                        x = pelmenX,
                        y = pelmenY,
                        vx = cos(angle) * spd,
                        vy = sin(angle) * spd - 2.5f,
                        size = Random.nextFloat() * 9f + 7f,
                        rotation = Random.nextFloat() * 360f,
                        vRot = (Random.nextFloat() - 0.5f) * 20f,
                        color = palette.random(),
                        emoji = burstEmojis.random()
                    )
                )
            }

            // Squish & bounce animations
            coroutineScope.launch {
                launch {
                    bodySquishX.snapTo(1.45f)
                    bodySquishX.animateTo(1.0f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium))
                }
                launch {
                    bodySquishY.snapTo(0.55f)
                    bodySquishY.animateTo(1.0f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium))
                }
                launch {
                    val tilt = if (Random.nextBoolean()) 26f else -26f
                    bodyTiltAngle.snapTo(tilt)
                    bodyTiltAngle.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                }
            }

            // Milestone Celebration every 10 catches
            if (catchCount % 10 == 0) {
                soundPlayer.playJackpotFanfare()
                repository.addCoins(5)
                showMilestoneBanner = true
                milestoneText = "🎉 СУПЕР ЛОВЕЦ! $catchCount ПОЙМАНО! 🏆"
                coroutineScope.launch {
                    delay(2600L)
                    showMilestoneBanner = false
                }
            }
        }
    }

    // Composable Layout
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("pelmen_tag_chase_game")
    ) {
        val arenaWidth = constraints.maxWidth.toFloat()
        val arenaHeight = constraints.maxHeight.toFloat()

        // Screen Boundaries Clamping for Pelmen-Six
        val margin = 80f
        val minX = margin
        val maxX = (arenaWidth - margin).coerceAtLeast(minX + 50f)
        val minY = 130f
        val maxY = (arenaHeight - 160f).coerceAtLeast(minY + 50f)

        if (pelmenX < minX) {
            pelmenX = minX
            pelmenVx = kotlin.math.abs(pelmenVx) * (Random.nextFloat() * 0.4f + 0.8f)
            soundPlayer.playPelmeshHitObstacle(0.7f)
        } else if (pelmenX > maxX) {
            pelmenX = maxX
            pelmenVx = -kotlin.math.abs(pelmenVx) * (Random.nextFloat() * 0.4f + 0.8f)
            soundPlayer.playPelmeshHitObstacle(0.7f)
        }

        if (pelmenY < minY) {
            pelmenY = minY
            pelmenVy = kotlin.math.abs(pelmenVy) * (Random.nextFloat() * 0.4f + 0.8f)
            soundPlayer.playPelmeshHitObstacle(0.7f)
        } else if (pelmenY > maxY) {
            pelmenY = maxY
            pelmenVy = -kotlin.math.abs(pelmenVy) * (Random.nextFloat() * 0.4f + 0.8f)
            soundPlayer.playPelmeshHitObstacle(0.7f)
        }

        // MAIN GAME ARENA CANVAS & TOUCH LISTENER
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(arenaWidth, arenaHeight) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            onCatchPelmen(tapOffset, arenaWidth, arenaHeight)
                        }
                    )
                }
        ) {
            val scale = size.width / 360f

            // 1. Draw Magnet Aura Ring if active
            if (isMagnetActive) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF1744).copy(alpha = 0.25f),
                            Color(0xFFFF5252).copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(pelmenX, pelmenY),
                        radius = 140f * auraPulse
                    ),
                    radius = 140f * auraPulse,
                    center = Offset(pelmenX, pelmenY)
                )
                drawCircle(
                    color = Color(0xFFFF1744).copy(alpha = 0.6f),
                    radius = 120f,
                    center = Offset(pelmenX, pelmenY),
                    style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f))
                )
            }

            // 2. Draw Shockwaves
            for (sw in shockwaves) {
                drawCircle(
                    color = sw.color.copy(alpha = sw.alpha * 0.8f),
                    radius = sw.radius,
                    center = Offset(sw.x, sw.y),
                    style = Stroke(width = 5f * sw.alpha + 2f)
                )
            }

            // 3. Draw Particles (Stars, Dumplings, Dust)
            for (p in sparkles) {
                if (p.emoji != null) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = p.emoji,
                        topLeft = Offset(p.x - p.size, p.y - p.size),
                        style = TextStyle(fontSize = (p.size * 1.5f).sp)
                    )
                } else {
                    drawCircle(
                        color = p.color.copy(alpha = p.alpha),
                        radius = p.size * p.alpha,
                        center = Offset(p.x, p.y)
                    )
                }
            }

            // 4. Draw Mini Clones
            for (c in miniClones) {
                drawMiniClone(
                    clone = c,
                    materialType = materialType
                )
            }

            // 5. Draw Bonus Bubbles
            for (b in bonusBubbles) {
                drawBonusBubble(
                    bubble = b,
                    textMeasurer = textMeasurer
                )
            }

            // 6. Draw PELMEN-SIX Character
            drawPelmenCharacter(
                x = pelmenX,
                y = pelmenY,
                squishX = bodySquishX.value,
                squishY = bodySquishY.value,
                tiltAngle = bodyTiltAngle.value + pelmenHeadingAngle,
                isFacingRight = isFacingRight,
                isDizzy = isDizzyCaught,
                isFrozen = isFrozen,
                materialType = materialType,
                runCycle = runStepCycle,
                dizzyStarRot = dizzyStarRotation,
                textMeasurer = textMeasurer,
                gameState = gameState
            )

            // 7. Draw Floating Texts (+25 ⭐, Combo)
            for (ft in floatingTexts) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = ft.text,
                    topLeft = Offset(ft.x - 70f, ft.y),
                    style = TextStyle(
                        color = ft.color.copy(alpha = ft.alpha),
                        fontSize = ft.size.sp,
                        fontWeight = FontWeight.Black
                    )
                )
            }
        }

        // TOP CONTROLS & HUD OVERLAY
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .align(Alignment.TopCenter)
        ) {
            // TOP HUD CARD
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.94f),
                shadowElevation = 4.dp,
                border = BorderStroke(1.5.dp, materialType.rimColor.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Switch back to antistress mode button
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFFF3E0),
                        border = BorderStroke(1.2.dp, Color(0xFFFFB74D)),
                        modifier = Modifier
                            .clickable {
                                soundPlayer.playBoing()
                                onSwitchToAntistress()
                            }
                            .testTag("switch_to_antistress_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🥟", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Мялка",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                        }
                    }

                    // Score & High Score
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Current Score
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFE8F5E9),
                            border = BorderStroke(1.2.dp, Color(0xFF81C784))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⭐", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "$score",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }

                        // Combo Badge if active
                        AnimatedVisibility(
                            visible = comboMultiplier > 1,
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFFFEBEE),
                                border = BorderStroke(1.5.dp, Color(0xFFFF1744))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🔥", fontSize = 13.sp)
                                    Text(
                                        text = "x$comboMultiplier",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFFFF1744)
                                    )
                                }
                            }
                        }

                        // Coins
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFFFF8E1),
                            border = BorderStroke(1.dp, Color(0xFFFFD54F))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🪙", fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "${gameState.coins}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFF57F17)
                                )
                            }
                        }
                    }

                    // Reset / New Round
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFEDE7F6),
                        border = BorderStroke(1.dp, Color(0xFFB39DDB)),
                        modifier = Modifier
                            .size(34.dp)
                            .clickable {
                                soundPlayer.playBoing(1.2f)
                                score = 0
                                catchCount = 0
                                comboCount = 0
                                comboMultiplier = 1
                            }
                            .testTag("reset_tag_score_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Сброс",
                                tint = Color(0xFF673AB7),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // SPEED & DIFFICULTY SELECTOR BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Difficulty Chips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TagDifficulty.values().forEach { diff ->
                        val isSelected = difficulty == diff
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) materialType.rimColor else Color.White.copy(alpha = 0.85f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) materialType.rimColor else Color.Gray.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .clickable {
                                    soundPlayer.playSqueak()
                                    difficulty = diff
                                }
                                .testTag("difficulty_${diff.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(diff.emoji, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = diff.title,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color(0xFF424242)
                                )
                            }
                        }
                    }
                }

                // Catches Count Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎯", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Поймано: $catchCount",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00838F)
                        )
                    }
                }
            }
        }

        // COMIC SPEECH BUBBLE ABOVE PELMEN-SIX
        val bubbleOffsetX = (pelmenX - 80f).coerceIn(16f, (arenaWidth - 180f).coerceAtLeast(16f))
        val bubbleOffsetY = (pelmenY - 110f).coerceIn(90f, arenaHeight - 120f)

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(2.dp, materialType.rimColor),
            shadowElevation = 4.dp,
            modifier = Modifier
                .offset { IntOffset(bubbleOffsetX.roundToInt(), bubbleOffsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectTapGestures {
                        onCatchPelmen(Offset(pelmenX, pelmenY), arenaWidth, arenaHeight)
                    }
                }
        ) {
            Text(
                text = comicSpeechText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = materialType.rimColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        // MILESTONE CELEBRATION POPUP BANNER
        AnimatedVisibility(
            visible = showMilestoneBanner,
            enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color.White,
                border = BorderStroke(3.dp, Color(0xFFFFD600)),
                shadowElevation = 12.dp,
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🌟🌟🌟", fontSize = 28.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = milestoneText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFE65100)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "+5 🪙 Бонусных Монеток!",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }
    }
}

/**
 * Draws Pelmen-Six with running legs, eyes, dumpling folds, blushing cheeks,
 * squish scale and shadow.
 */
private fun DrawScope.drawPelmenCharacter(
    x: Float,
    y: Float,
    squishX: Float,
    squishY: Float,
    tiltAngle: Float,
    isFacingRight: Boolean,
    isDizzy: Boolean,
    isFrozen: Boolean,
    materialType: SquishyMaterialType,
    runCycle: Float,
    dizzyStarRot: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    gameState: GameState
) {
    val radiusX = 64f * squishX
    val radiusY = 52f * squishY

    // 1. Dynamic Floor Shadow
    drawOval(
        color = Color.Black.copy(alpha = 0.18f),
        topLeft = Offset(x - radiusX * 0.95f, y + radiusY * 0.7f),
        size = Size(radiusX * 1.9f, 22f)
    )

    // 2. Animated Running Legs
    val leftLegOffset = sin(runCycle) * 14f
    val rightLegOffset = sin(runCycle + PI.toFloat()) * 14f

    val legColor = if (isFrozen) Color(0xFF80DEEA) else materialType.rimColor
    // Left foot
    drawRoundRect(
        color = legColor,
        topLeft = Offset(x - 24f, y + radiusY * 0.72f + leftLegOffset),
        size = Size(14f, 18f),
        cornerRadius = CornerRadius(7f, 7f)
    )
    // Right foot
    drawRoundRect(
        color = legColor,
        topLeft = Offset(x + 10f, y + radiusY * 0.72f + rightLegOffset),
        size = Size(14f, 18f),
        cornerRadius = CornerRadius(7f, 7f)
    )

    // 3. Main Dumpling Body with Tilt
    rotate(degrees = tiltAngle, pivot = Offset(x, y)) {
        // Dumpling Body Gradient
        val bodyColors = if (isFrozen) {
            listOf(Color(0xFFE0F7FA), Color(0xFF80DEEA), Color(0xFF00ACC1))
        } else {
            materialType.bodyColors
        }

        val bodyBrush = Brush.radialGradient(
            colors = bodyColors,
            center = Offset(x - radiusX * 0.2f, y - radiusY * 0.25f),
            radius = radiusX * 1.3f
        )

        // Dumpling base oval
        drawOval(
            brush = bodyBrush,
            topLeft = Offset(x - radiusX, y - radiusY),
            size = Size(radiusX * 2f, radiusY * 2f)
        )

        // Dumpling Rim / Border
        drawOval(
            color = if (isFrozen) Color(0xFF00ACC1) else materialType.rimColor,
            topLeft = Offset(x - radiusX, y - radiusY),
            size = Size(radiusX * 2f, radiusY * 2f),
            style = Stroke(width = 3.5f)
        )

        // Dumpling Top Crimp / Folds
        val foldColor = if (isFrozen) Color(0xFF4DD0E1) else materialType.rimColor.copy(alpha = 0.5f)
        for (i in -2..2) {
            val fx = x + i * (radiusX * 0.35f)
            val fy = y - radiusY + 2f
            drawArc(
                color = foldColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(fx - 12f, fy - 10f),
                size = Size(24f, 18f),
                style = Stroke(width = 2.5f)
            )
        }

        // Rosy Blushing Cheeks
        val blushColor = if (isFrozen) Color(0x6600E5FF) else materialType.blushColor.copy(alpha = 0.65f)
        drawCircle(
            color = blushColor,
            radius = 11f * squishX,
            center = Offset(x - radiusX * 0.48f, y + 8f)
        )
        drawCircle(
            color = blushColor,
            radius = 11f * squishX,
            center = Offset(x + radiusX * 0.48f, y + 8f)
        )

        // Face & Eyes
        val eyeDir = if (isFacingRight) 4f else -4f
        val leftEyeCenter = Offset(x - 20f + eyeDir, y - 6f)
        val rightEyeCenter = Offset(x + 20f + eyeDir, y - 6f)

        if (isDizzy) {
            // Spiral dizzy eyes!
            drawDizzyEye(leftEyeCenter, 10f, dizzyStarRot)
            drawDizzyEye(rightEyeCenter, 10f, -dizzyStarRot)

            // Dizzy wobbly open mouth
            drawArc(
                color = Color(0xFF3E2723),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = true,
                topLeft = Offset(x - 9f, y + 12f),
                size = Size(18f, 14f)
            )
        } else if (isFrozen) {
            // Frozen shivering eyes and mouth
            drawLine(
                color = Color(0xFF006064),
                start = Offset(leftEyeCenter.x - 7f, leftEyeCenter.y),
                end = Offset(leftEyeCenter.x + 7f, leftEyeCenter.y),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF006064),
                start = Offset(rightEyeCenter.x - 7f, rightEyeCenter.y),
                end = Offset(rightEyeCenter.x + 7f, rightEyeCenter.y),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            // Shivering teeth
            drawRect(
                color = Color.White,
                topLeft = Offset(x - 10f, y + 10f),
                size = Size(20f, 8f)
            )
            drawRect(
                color = Color(0xFF006064),
                topLeft = Offset(x - 10f, y + 10f),
                size = Size(20f, 8f),
                style = Stroke(width = 1.5f)
            )
        } else {
            // Cheerful wide open eyes with highlights
            drawCircle(color = Color(0xFF212121), radius = 8f, center = leftEyeCenter)
            drawCircle(color = Color.White, radius = 3f, center = Offset(leftEyeCenter.x - 2f, leftEyeCenter.y - 2f))

            drawCircle(color = Color(0xFF212121), radius = 8f, center = rightEyeCenter)
            drawCircle(color = Color.White, radius = 3f, center = Offset(rightEyeCenter.x - 2f, rightEyeCenter.y - 2f))

            // Cheerful Smile with pink tongue
            val mouthPath = Path().apply {
                moveTo(x - 12f, y + 10f)
                quadraticBezierTo(x, y + 24f, x + 12f, y + 10f)
            }
            drawPath(path = mouthPath, color = Color(0xFF3E2723), style = Stroke(width = 3f, cap = StrokeCap.Round))
            drawCircle(color = Color(0xFFFF5252), radius = 4.5f, center = Offset(x, y + 15f))
        }

        // 4. Dizzy Stars Orbiting Head if caught
        if (isDizzy) {
            for (i in 0 until 3) {
                val starAngle = dizzyStarRot + i * 120f
                val rad = (starAngle * PI / 180f).toFloat()
                val sx = x + cos(rad) * 48f
                val sy = y - radiusY - 14f + sin(rad) * 12f
                drawText(
                    textMeasurer = textMeasurer,
                    text = "💫",
                    topLeft = Offset(sx - 10f, sy - 10f),
                    style = TextStyle(fontSize = 16.sp)
                )
            }
        }

        // 5. Ice Crystal Spikes if Frozen
        if (isFrozen) {
            drawText(
                textMeasurer = textMeasurer,
                text = "❄️",
                topLeft = Offset(x - 14f, y - radiusY - 18f),
                style = TextStyle(fontSize = 22.sp)
            )
        }
    }
}

/**
 * Draws spiral dizzy cartoon eye.
 */
private fun DrawScope.drawDizzyEye(center: Offset, radius: Float, rotationAngle: Float) {
    rotate(degrees = rotationAngle, pivot = center) {
        drawCircle(color = Color.White, radius = radius, center = center)
        drawCircle(color = Color(0xFF212121), radius = radius, center = center, style = Stroke(width = 2f))
        // Spiral arc
        drawArc(
            color = Color(0xFF212121),
            startAngle = 0f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.6f, center.y - radius * 0.6f),
            size = Size(radius * 1.2f, radius * 1.2f),
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Draws floating bonus power-up bubble with gloss highlight and emoji.
 */
private fun DrawScope.drawBonusBubble(
    bubble: TagBonusBubble,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val r = bubble.radius
    val center = Offset(bubble.x, bubble.y)

    // Bubble glow aura
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                bubble.type.color.copy(alpha = 0.65f),
                bubble.type.color.copy(alpha = 0.25f),
                Color.Transparent
            ),
            center = center,
            radius = r * 1.2f
        ),
        radius = r * 1.2f,
        center = center
    )

    // Bubble sphere
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, bubble.type.color.copy(alpha = 0.4f)),
            center = Offset(center.x - r * 0.35f, center.y - r * 0.35f),
            radius = r * 1.1f
        ),
        radius = r,
        center = center
    )
    drawCircle(
        color = bubble.type.color,
        radius = r,
        center = center,
        style = Stroke(width = 2.5f)
    )

    // Bubble gloss reflection
    drawOval(
        color = Color.White.copy(alpha = 0.75f),
        topLeft = Offset(center.x - r * 0.6f, center.y - r * 0.65f),
        size = Size(r * 0.65f, r * 0.4f)
    )

    // Icon Emoji
    drawText(
        textMeasurer = textMeasurer,
        text = bubble.type.emoji,
        topLeft = Offset(center.x - 14f, center.y - 14f),
        style = TextStyle(fontSize = 20.sp)
    )
}

/**
 * Draws tiny mini running clone dumpling.
 */
private fun DrawScope.drawMiniClone(
    clone: MiniPelmenClone,
    materialType: SquishyMaterialType
) {
    val r = 24f
    val center = Offset(clone.x, clone.y)

    drawOval(
        brush = Brush.radialGradient(
            colors = materialType.bodyColors,
            center = center,
            radius = r * 1.2f
        ),
        topLeft = Offset(center.x - r, center.y - r * 0.8f),
        size = Size(r * 2f, r * 1.6f)
    )
    drawOval(
        color = materialType.rimColor,
        topLeft = Offset(center.x - r, center.y - r * 0.8f),
        size = Size(r * 2f, r * 1.6f),
        style = Stroke(width = 2f)
    )
    // Tiny eyes
    drawCircle(color = Color.Black, radius = 3f, center = Offset(center.x - 6f, center.y - 2f))
    drawCircle(color = Color.Black, radius = 3f, center = Offset(center.x + 6f, center.y - 2f))
}
