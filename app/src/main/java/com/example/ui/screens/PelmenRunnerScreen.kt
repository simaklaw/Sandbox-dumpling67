package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random
import com.example.audio.SoundEffectPlayer
import com.example.audio.rememberRunnerSoundEffectsManager
import com.example.audio.RunnerSoundEffectsManager
import com.example.data.GameRepository
import com.example.physics.runner.PelmenRunnerPhysicsEngine
import com.example.physics.runner.PhysicsDebugContact
import com.example.physics.runner.RunnerEntityType
import com.example.physics.runner.RunnerObstacleSnapshot
import com.example.physics.runner.RunnerUserData
import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.Fixture
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.StickerEntity
import com.example.physics.runner.RunnerBiome
import com.example.shared.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*


data class RunnerParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var color: Color,
    var size: Float,
    var alpha: Float = 1.0f,
    var life: Float = 1.0f,
    var maxLife: Float = 1.0f,
    var isStar: Boolean = false
)

data class FloatingScorePopup(
    val id: Long,
    val text: String,
    var x: Float,
    var y: Float,
    val color: Color,
    var alpha: Float = 1.0f,
    var age: Float = 0f
)

/**
 * 🏃‍♂️ «Раннер Пельмень-Сикс» — Real-time JBox2D physics simulation runner game.
 * Features realistic elastic collisions, double jumps, trampoline launches, bumper bounces,
 * dynamic squish deformation, particle effects, and toddler-friendly controls.
 */
@Composable
fun PelmenRunnerScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    modifier: Modifier = Modifier,
    customSoundManager: RunnerSoundEffectsManager? = null
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val runnerSoundManager = customSoundManager ?: rememberRunnerSoundEffectsManager(context, soundPlayer, soundManager)
    val haptics = rememberHaptics()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Session stats
    var distanceMeters by remember { mutableIntStateOf(0) }
    var coinsCollected by remember { mutableIntStateOf(0) }
    var dumplingsCollected by remember { mutableIntStateOf(0) }
    var enemiesSmashed by remember { mutableIntStateOf(0) }
    var maxDistanceMeters by remember { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var showPhysicsDebugOverlay by remember { mutableStateOf(false) }
    var isSoundMuted by remember { mutableStateOf(!runnerSoundManager.isEnabled) }
    var gameTimeSeconds by remember { mutableFloatStateOf(0f) }

    // Particles and Popups
    val particles = remember { mutableStateListOf<RunnerParticle>() }
    val popups = remember { mutableStateListOf<FloatingScorePopup>() }
    var popupIdGen by remember { mutableLongStateOf(0L) }

    // Visual deformation states
    var heroSquashX by remember { mutableFloatStateOf(1.0f) }
    var heroSquashY by remember { mutableFloatStateOf(1.0f) }
    var bounceFlashColor by remember { mutableStateOf<Color?>(null) }
    var doubleJumpSpinAngle by remember { mutableFloatStateOf(0f) }
    var isSpinning by remember { mutableStateOf(false) }

    // High Score from GameState
    val gameState by repository.gameState.collectAsState()
    val currentHighScore = gameState.runnerHighScore

    // 📖 Interactive Sticker Book & Biome States
    var showStickerBookDialog by remember { mutableStateOf(false) }
    var newlyUnlockedSticker by remember { mutableStateOf<StickerEntity?>(null) }
    val allStickers by repository.allStickers.collectAsStateWithLifecycle(initialValue = emptyList())
    val unlockedStickerCount by repository.unlockedStickerCount.collectAsStateWithLifecycle(initialValue = 0)
    var currentBiomeState by remember { mutableStateOf(RunnerBiome.KITCHEN) }

    // Particle and Popup helpers
    fun spawnShieldPopParticles(x: Float, y: Float, count: Int, isShieldPickup: Boolean = false) {
        val rand = Random(System.currentTimeMillis())
        val bubbleColors = if (isShieldPickup) {
            listOf(Color(0xFF00E5FF), Color(0xFF80DEEA), Color(0xFFE040FB), Color(0xFFFFFFFF))
        } else {
            listOf(Color(0xFF00E5FF), Color(0xFFFF4081), Color(0xFFFFEA00), Color(0xFF76FF03), Color(0xFFFFFFFF))
        }
        repeat(count) {
            val angle = rand.nextFloat() * 2f * PI.toFloat()
            val speed = rand.nextFloat() * 11f + 3f
            particles.add(
                RunnerParticle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = bubbleColors.random(),
                    size = rand.nextFloat() * 9f + 4f,
                    life = 1f,
                    maxLife = rand.nextFloat() * 0.45f + 0.35f,
                    isStar = rand.nextBoolean()
                )
            )
        }
    }

    fun spawnMagnetParticles(x: Float, y: Float, count: Int) {
        val rand = Random(System.currentTimeMillis())
        val magnetColors = listOf(Color(0xFFFFD700), Color(0xFFFFEA00), Color(0xFFFF9100), Color(0xFFFFFFFF))
        repeat(count) {
            val angle = rand.nextFloat() * 2f * PI.toFloat()
            val speed = rand.nextFloat() * 10f + 3f
            particles.add(
                RunnerParticle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = magnetColors.random(),
                    size = rand.nextFloat() * 8f + 4f,
                    life = 1f,
                    maxLife = rand.nextFloat() * 0.4f + 0.3f,
                    isStar = true
                )
            )
        }
    }

    fun spawnIceParticles(x: Float, y: Float, count: Int) {
        val rand = Random(System.currentTimeMillis())
        val iceColors = listOf(Color(0xFFE0F7FA), Color(0xFF80DEEA), Color(0xFFB2EBF2), Color(0xFFFFFFFF))
        repeat(count) {
            val angle = rand.nextFloat() * PI.toFloat() + PI.toFloat()
            val speed = rand.nextFloat() * 9f + 3f
            particles.add(
                RunnerParticle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = iceColors.random(),
                    size = rand.nextFloat() * 7f + 4f,
                    life = 1f,
                    maxLife = rand.nextFloat() * 0.45f + 0.3f,
                    isStar = true
                )
            )
        }
    }

    fun spawnJellyParticles(x: Float, y: Float, count: Int) {
        val rand = Random(System.currentTimeMillis())
        val jellyColors = listOf(Color(0xFFFF4081), Color(0xFFFF80AB), Color(0xFFF50057), Color(0xFFFFEB3B))
        repeat(count) {
            val angle = rand.nextFloat() * PI.toFloat() + PI.toFloat()
            val speed = rand.nextFloat() * 12f + 4f
            particles.add(
                RunnerParticle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = jellyColors.random(),
                    size = rand.nextFloat() * 8f + 5f,
                    life = 1f,
                    maxLife = rand.nextFloat() * 0.42f + 0.32f,
                    isStar = rand.nextBoolean()
                )
            )
        }
    }

    fun spawnSteamParticles(x: Float, y: Float, count: Int) {
        val rand = Random(System.currentTimeMillis())
        val steamColors = listOf(Color(0xFFEDE7F6), Color(0xFFD1C4E9), Color(0xFFB388FF), Color(0xFFFFFFFF))
        repeat(count) {
            val angle = rand.nextFloat() * 0.8f * PI.toFloat() + 1.1f * PI.toFloat()
            val speed = rand.nextFloat() * 10f + 3f
            particles.add(
                RunnerParticle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = steamColors.random(),
                    size = rand.nextFloat() * 10f + 6f,
                    life = 1f,
                    maxLife = rand.nextFloat() * 0.5f + 0.35f,
                    isStar = false
                )
            )
        }
    }

    fun spawnBounceParticles(x: Float, y: Float, count: Int, isTrampoline: Boolean = false) {
        val rand = Random(System.currentTimeMillis())
        repeat(count) {
            val angle = rand.nextFloat() * PI.toFloat() + PI.toFloat() // Upward spray
            val speed = rand.nextFloat() * 12f + 4f
            particles.add(
                RunnerParticle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = if (isTrampoline) Color(0xFFFF5252) else Color(0xFFFFD700),
                    size = rand.nextFloat() * 8f + 5f,
                    life = 1f,
                    maxLife = rand.nextFloat() * 0.4f + 0.3f,
                    isStar = rand.nextBoolean()
                )
            )
        }
    }

    fun addPopup(text: String, x: Float, y: Float, color: Color) {
        popups.add(
            FloatingScorePopup(
                id = ++popupIdGen,
                text = text,
                x = x,
                y = y,
                color = color
            )
        )
    }

    // Initialize JBox2D Physics Engine with RunnerSoundEffectsManager
    val physicsEngine = remember {
        lateinit var engine: PelmenRunnerPhysicsEngine
        engine = PelmenRunnerPhysicsEngine(
            onGroundContactChanged = { grounded ->
                if (grounded) {
                    heroSquashX = 1.35f
                    heroSquashY = 0.72f
                    runnerSoundManager.playLand(1.0f)
                }
            },
            onBounce = { type, normal, impactSpeed ->
                heroSquashX = 1.45f
                heroSquashY = 0.65f
                bounceFlashColor = Color(0xFFFFD54F)
                runnerSoundManager.playBounce(type, impactSpeed)
            },
            onCollect = { type, id, points ->
                when (type) {
                    RunnerEntityType.COLLECTIBLE_COIN -> {
                        coinsCollected++
                        repository.addCoins(1)
                        runnerSoundManager.playCollectCoin(comboStreak = coinsCollected)
                    }
                    RunnerEntityType.COLLECTIBLE_DUMPLING -> {
                        dumplingsCollected++
                        coinsCollected += 5
                        repository.addCoins(5)
                        runnerSoundManager.playCollectDumpling(comboStreak = dumplingsCollected)
                    }
                    RunnerEntityType.POWERUP_SHIELD -> {
                        runnerSoundManager.playPowerUpShield()
                        val heroPos = engine.heroBody.position
                        spawnShieldPopParticles(heroPos.x, heroPos.y, count = 18, isShieldPickup = true)
                        addPopup("СУПЕР-ЩИТ! 🛡️✨", heroPos.x, heroPos.y - 1.5f, Color(0xFF00E5FF))
                    }
                    RunnerEntityType.POWERUP_MAGNET -> {
                        runnerSoundManager.playPowerUpMagnet()
                        val heroPos = engine.heroBody.position
                        spawnMagnetParticles(heroPos.x, heroPos.y, count = 18)
                        addPopup("МАГНИТ МОНЕТ! 🧲⭐", heroPos.x, heroPos.y - 1.5f, Color(0xFFFFD700))
                    }
                    else -> {
                        runnerSoundManager.playCollectItem(type, comboStreak = coinsCollected)
                    }
                }
            },
            onSpeedBoost = {
                runnerSoundManager.playSpeedBoost()
            },
            onObstacleHit = { type, intensity ->
                heroSquashX = 1.4f
                heroSquashY = 0.75f
                runnerSoundManager.playObstacleHit(type, intensity)
            },
            onShieldAbsorb = {
                heroSquashX = 1.35f
                heroSquashY = 0.8f
                bounceFlashColor = Color(0xFF00E5FF)
                runnerSoundManager.playShieldBreak()
                val heroPos = engine.heroBody.position
                spawnShieldPopParticles(heroPos.x, heroPos.y, count = 22, isShieldPickup = false)
                addPopup("ЩИТ СПАС! 🛡️💥", heroPos.x, heroPos.y - 1.4f, Color(0xFF00E5FF))
            },
            onBiomeChanged = { newBiome ->
                currentBiomeState = newBiome
                val (biomeName, bannerColor) = when (newBiome) {
                    RunnerBiome.ICE_FREEZER -> "ЛЕДЯНАЯ КАМЕРА! 🧊❄️" to Color(0xFF00E5FF)
                    RunnerBiome.STEAM_CLOUDS -> "ОБЛАЧНАЯ ЧАЙНАЯ! ☁️✨" to Color(0xFFE040FB)
                    RunnerBiome.CLOUD_KITCHEN -> "ОБЛАЧНАЯ КУХНЯ! 🍳☁️" to Color(0xFFFFCCBC)
                    else -> "УЮТНАЯ КУХНЯ! 🍳" to Color(0xFFFFD54F)
                }
                runnerSoundManager.playMilestone(distanceMeters)
                addPopup(biomeName, engine.heroBody.position.x, engine.heroBody.position.y - 2.2f, bannerColor)
            },
            onIceSlide = {
                runnerSoundManager.playIceSlide()
                val heroPos = engine.heroBody.position
                spawnIceParticles(heroPos.x, heroPos.y, count = 12)
                addPopup("СКОЛЬЖЕНИЕ ПО ЛЬДУ! ⛸️❄️", heroPos.x, heroPos.y - 1.2f, Color(0xFF80DEEA))
            },
            onJellyBounce = {
                heroSquashX = 1.55f
                heroSquashY = 0.55f
                bounceFlashColor = Color(0xFFFF4081)
                runnerSoundManager.playJellyBounce()
                val heroPos = engine.heroBody.position
                spawnJellyParticles(heroPos.x, heroPos.y, count = 16)
                addPopup("ЖЕЛЕ-ОТСКОК! 🍮🎉", heroPos.x, heroPos.y - 1.5f, Color(0xFFFF4081))
            },
            onSteamVent = {
                runnerSoundManager.playSteamWhoosh()
                val heroPos = engine.heroBody.position
                spawnSteamParticles(heroPos.x, heroPos.y, count = 14)
                addPopup("ПАРОВОЙ ГЕЙЗЕР! ☁️💨", heroPos.x, heroPos.y - 1.6f, Color(0xFFB388FF))
            }
        )
        engine
    }

    // ==========================================
    // 60-120 FPS GAME LOOP WITH withFrameNanos
    // ==========================================
    LaunchedEffect(isPaused) {
        var lastNanos = 0L
        var accumulator = 0f
        val fixedDt = 1f / 60f
        var runningFeetTimer = 0f

        while (isActive) {
            withFrameNanos { nowNanos ->
                if (lastNanos == 0L) {
                    lastNanos = nowNanos
                    return@withFrameNanos
                }
                val frameDt = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastNanos = nowNanos

                if (!isPaused) {
                    gameTimeSeconds += frameDt
                    accumulator += frameDt
                    var steps = 0
                    while (accumulator >= fixedDt && steps < 4) {
                        accumulator -= fixedDt
                        steps++
                        physicsEngine.step(fixedDt)

                        // ⛸️ Continuous ice trail particles
                        if (physicsEngine.isSlidingOnIce && Random.nextFloat() < 0.25f) {
                            val heroPos = physicsEngine.heroBody.position
                            spawnIceParticles(heroPos.x, heroPos.y + 0.4f, count = 3)
                        }

                        // Update distance (in meters)
                        val currDist = physicsEngine.heroBody.position.x.toInt().coerceAtLeast(0)
                        if (currDist > distanceMeters) {
                            val prevHundreds = distanceMeters / 100
                            val newHundreds = currDist / 100
                            if (newHundreds > prevHundreds && newHundreds > 0) {
                                runnerSoundManager.playMilestone(currDist)
                                addPopup("УРА! ${newHundreds * 100} МЕТРОВ! 🏆", physicsEngine.heroBody.position.x, physicsEngine.heroBody.position.y - 1.5f, Color(0xFFFFD54F))
                            }
                            distanceMeters = currDist
                            if (currDist > maxDistanceMeters) {
                                maxDistanceMeters = currDist
                            }
                            if (currDist > currentHighScore) {
                                repository.updateRunnerHighScore(currDist)
                            }

                            // 🌟 Check interactive sticker unlocks for toddler achievements
                            coroutineScope.launch {
                                val newlyUnlocked = repository.checkAndUnlockDistanceStickers(currDist)
                                if (newlyUnlocked.isNotEmpty()) {
                                    val newest = newlyUnlocked.first()
                                    newlyUnlockedSticker = newest
                                    runnerSoundManager.playStickerUnlockCelebration()
                                    addPopup("НАКЛЕЙКА: ${newest.emoji} ${newest.title}!", physicsEngine.heroBody.position.x, physicsEngine.heroBody.position.y - 2.2f, Color(0xFFFFD54F))
                                }
                            }
                        }
                    }

                    // Smooth squash spring recovery
                    heroSquashX += (1.0f - heroSquashX) * 0.18f
                    heroSquashY += (1.0f - heroSquashY) * 0.18f

                    // Somersault spin
                    if (isSpinning) {
                        doubleJumpSpinAngle += 900f * frameDt
                        if (doubleJumpSpinAngle >= 360f) {
                            doubleJumpSpinAngle = 0f
                            isSpinning = false
                        }
                    }

                    // Footstep audio/haptics while running on ground
                    if (physicsEngine.isGrounded) {
                        runningFeetTimer += frameDt
                        if (runningFeetTimer > 0.22f) {
                            runningFeetTimer = 0f
                            runnerSoundManager.playFootstep(Random.nextBoolean())
                        }
                    }

                    // Speed trail particles
                    val heroPos = physicsEngine.heroBody.position
                    if (physicsEngine.isBoosting) {
                        particles.add(
                            RunnerParticle(
                                x = heroPos.x - 0.4f,
                                y = heroPos.y + (Random.nextFloat() - 0.5f) * 0.6f,
                                vx = -Random.nextFloat() * 6f - 2f,
                                vy = (Random.nextFloat() - 0.5f) * 3f,
                                color = listOf(Color(0xFFFF1744), Color(0xFFFFEA00), Color(0xFF00E676), Color(0xFF2979FF)).random(),
                                size = Random.nextFloat() * 7f + 5f,
                                life = 1f,
                                maxLife = 0.35f,
                                isStar = true
                            )
                        )
                    }

                    // 🧲 Magnet attraction spark trail
                    if (physicsEngine.isMagnetActive && Random.nextFloat() < 0.35f) {
                        particles.add(
                            RunnerParticle(
                                x = heroPos.x + (Random.nextFloat() - 0.5f) * 1.6f,
                                y = heroPos.y + (Random.nextFloat() - 0.5f) * 1.6f,
                                vx = (Random.nextFloat() - 0.5f) * 2.5f,
                                vy = -Random.nextFloat() * 3.5f - 1f,
                                color = Color(0xFFFFD700),
                                size = Random.nextFloat() * 6f + 3f,
                                life = 1f,
                                maxLife = 0.38f,
                                isStar = true
                            )
                        )
                    }

                    // 🛡️ Shield shimmering micro-bubbles
                    if (physicsEngine.isShieldActive && Random.nextFloat() < 0.25f) {
                        particles.add(
                            RunnerParticle(
                                x = heroPos.x + (Random.nextFloat() - 0.5f) * 1.8f,
                                y = heroPos.y + (Random.nextFloat() - 0.5f) * 1.8f,
                                vx = (Random.nextFloat() - 0.5f) * 1.5f,
                                vy = -Random.nextFloat() * 2f - 0.5f,
                                color = listOf(Color(0xFF00E5FF), Color(0xFF80DEEA), Color(0xFFE040FB)).random(),
                                size = Random.nextFloat() * 5f + 3f,
                                life = 1f,
                                maxLife = 0.42f,
                                isStar = false
                            )
                        )
                    }

                    // ❄️ Ice Freezer biome gentle falling snowflakes
                    if (physicsEngine.currentBiome == RunnerBiome.ICE_FREEZER && Random.nextFloat() < 0.22f) {
                        particles.add(
                            RunnerParticle(
                                x = heroPos.x + (Random.nextFloat() - 0.2f) * 18f,
                                y = Random.nextFloat() * 6f + 2f,
                                vx = -Random.nextFloat() * 3f - 1.5f,
                                vy = Random.nextFloat() * 2f + 1.2f,
                                color = Color(0xFFE0F7FA),
                                size = Random.nextFloat() * 5f + 3f,
                                life = 1f,
                                maxLife = 1.0f,
                                isStar = true
                            )
                        )
                    }

                    // ⛸️ Active Ice Chamber ("Ледяная камера") sliding particles
                    if (physicsEngine.isSlidingOnIce && Random.nextFloat() < 0.65f) {
                        particles.add(
                            RunnerParticle(
                                x = heroPos.x - 0.35f + (Random.nextFloat() - 0.5f) * 0.4f,
                                y = heroPos.y + physicsEngine.heroRadius * 0.95f,
                                vx = -Random.nextFloat() * 4.5f - 2f,
                                vy = -Random.nextFloat() * 2.5f - 0.5f,
                                color = listOf(Color(0xFFE0F7FA), Color(0xFF80DEEA), Color(0xFF00E5FF), Color.White).random(),
                                size = Random.nextFloat() * 6f + 3f,
                                life = 1f,
                                maxLife = 0.28f,
                                isStar = true
                            )
                        )
                    }

                    // ☁️ Steam Clouds biome gentle upward steam puffs
                    if (physicsEngine.currentBiome == RunnerBiome.STEAM_CLOUDS && Random.nextFloat() < 0.22f) {
                        particles.add(
                            RunnerParticle(
                                x = heroPos.x + (Random.nextFloat() - 0.2f) * 18f,
                                y = physicsEngine.groundY - Random.nextFloat() * 2f,
                                vx = (Random.nextFloat() - 0.5f) * 1.5f,
                                vy = -Random.nextFloat() * 3f - 1.5f,
                                color = Color(0xFFF3E5F5).copy(alpha = 0.55f),
                                size = Random.nextFloat() * 9f + 5f,
                                life = 1f,
                                maxLife = 0.85f,
                                isStar = false
                            )
                        )
                    }

                    // Update particles
                    val pIterator = particles.iterator()
                    while (pIterator.hasNext()) {
                        val p = pIterator.next()
                        p.x += p.vx * frameDt
                        p.y += p.vy * frameDt
                        p.vy += 15f * frameDt // particle gravity
                        p.life -= frameDt / p.maxLife
                        p.alpha = p.life.coerceIn(0f, 1f)
                        if (p.life <= 0f) {
                            pIterator.remove()
                        }
                    }

                    // Update popups
                    val popupIterator = popups.iterator()
                    while (popupIterator.hasNext()) {
                        val pop = popupIterator.next()
                        pop.age += frameDt
                        pop.y -= 1.8f * frameDt // Float upward
                        pop.alpha = (1.0f - pop.age / 0.8f).coerceIn(0f, 1f)
                        if (pop.age >= 0.8f) {
                            popupIterator.remove()
                        }
                    }
                }
            }
        }
    }

    // Save final stats on exit or milestone
    DisposableEffect(Unit) {
        onDispose {
            if (distanceMeters > 0) {
                repository.saveRunnerScoreResult(
                    distance = distanceMeters,
                    coins = coinsCollected,
                    dumplings = dumplingsCollected,
                    enemiesSmashed = enemiesSmashed,
                    locationName = "Кухня 67"
                )
            }
        }
    }

    // Jump action handler
    fun performJump() {
        if (isPaused) return
        val isDouble = physicsEngine.jump()
        if (isDouble) {
            isSpinning = true
            doubleJumpSpinAngle = 0f
            runnerSoundManager.playDoubleJumpSpin()
            addPopup("СИКС-КУЛЬБИТ! 🌀", physicsEngine.heroBody.position.x, physicsEngine.heroBody.position.y - 1.2f, Color(0xFFFF4081))
        } else {
            runnerSoundManager.playJump(isDoubleJump = false)
            val heroPos = physicsEngine.heroBody.position
            spawnBounceParticles(heroPos.x, heroPos.y + 0.5f, 6)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF81D4FA))
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        performJump()
                    }
                )
            }
    ) {
        // ==========================================
        // MAIN JBOX2D CANVAS RENDERER
        // ==========================================
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("pelmen_runner_canvas")
        ) {
            val canvasW = size.width
            val canvasH = size.height
            val meterToPx = canvasH / 14.0f // 14 meters visible vertically

            val heroPos = physicsEngine.heroBody.position
            val heroVel = physicsEngine.heroBody.linearVelocity
            val cameraX = heroPos.x
            val cameraOffsetPx = canvasW * 0.28f // Position hero at 28% from left

            fun worldToScreen(wx: Float, wy: Float): Offset {
                val sx = (wx - cameraX) * meterToPx + cameraOffsetPx
                val sy = wy * meterToPx
                return Offset(sx, sy)
            }

            // --- 1. MULTI-LAYERED PARALLAX BACKGROUND (0.03x to 1.0x with scrolling floor) ---
            drawParallaxMultiLayerBackground(
                canvasW = canvasW,
                canvasH = canvasH,
                meterToPx = meterToPx,
                cameraX = cameraX,
                groundY = physicsEngine.groundY,
                gameTime = gameTimeSeconds,
                biome = currentBiomeState
            )

            // --- 2. JBOX2D OBSTACLES & PLATFORMS RENDERING ---
            val viewLeftMeters = cameraX - (cameraOffsetPx / meterToPx) - 2f
            val viewRightMeters = cameraX + ((canvasW - cameraOffsetPx) / meterToPx) + 2f
            val visibleObstacles = physicsEngine.getVisibleObstacles(viewLeftMeters, viewRightMeters)

            for (obs in visibleObstacles) {
                val screenPos = worldToScreen(obs.position.x, obs.position.y)
                val wPx = obs.width * meterToPx
                val hPx = obs.height * meterToPx

                when (obs.type) {
                    RunnerEntityType.GROUND -> {
                        // Warm wooden countertop ground
                        drawRoundRect(
                            color = Color(0xFFFFB74D),
                            topLeft = Offset(screenPos.x - wPx / 2f, screenPos.y - hPx / 2f),
                            size = Size(wPx, hPx + 200.dp.toPx()),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                        // Top rim
                        drawRect(
                            color = Color(0xFFFFA726),
                            topLeft = Offset(screenPos.x - wPx / 2f, screenPos.y - hPx / 2f),
                            size = Size(wPx, 8.dp.toPx())
                        )
                        // Dark outline
                        drawRoundRect(
                            color = ColorInk,
                            topLeft = Offset(screenPos.x - wPx / 2f, screenPos.y - hPx / 2f),
                            size = Size(wPx, hPx + 200.dp.toPx()),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                    RunnerEntityType.PLATFORM -> {
                        // Toast / cutting board platform
                        drawRoundRect(
                            color = Color(0xFFFFE082),
                            topLeft = Offset(screenPos.x - wPx / 2f, screenPos.y - hPx / 2f),
                            size = Size(wPx, hPx),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                        drawRoundRect(
                            color = ColorInk,
                            topLeft = Offset(screenPos.x - wPx / 2f, screenPos.y - hPx / 2f),
                            size = Size(wPx, hPx),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                    RunnerEntityType.PLATFORM_ICE -> {
                        // 🧊 Slippery Ice Platform
                        drawIcePlatformGraphic(screenPos, wPx, hPx, gameTimeSeconds)
                    }
                    RunnerEntityType.TRAMPOLINE -> {
                        // Springy Mushroom Trampoline
                        drawTrampolineGraphic(screenPos, wPx, hPx)
                    }
                    RunnerEntityType.BUMPER_JELLY -> {
                        // 🍮 Bouncy Marmalade Jelly Trampoline
                        drawJellyBumperGraphic(screenPos, wPx, hPx, gameTimeSeconds)
                    }
                    RunnerEntityType.STEAM_VENT -> {
                        // ☁️ Steamy Geyser Upward Vent
                        drawSteamVentGraphic(screenPos, wPx, hPx, gameTimeSeconds)
                    }
                    RunnerEntityType.BUMPER -> {
                        // Dumpling Pot Lid Bumper
                        drawBumperGraphic(screenPos, wPx / 2f)
                    }
                    RunnerEntityType.OBSTACLE_BUTTER -> {
                        // Golden Butter Block
                        drawButterObstacleGraphic(screenPos, wPx, hPx)
                    }
                    RunnerEntityType.OBSTACLE_HAZARD -> {
                        // Spiky Pepper
                        drawChiliPepperGraphic(screenPos, wPx, hPx)
                    }
                    RunnerEntityType.COLLECTIBLE_COIN -> {
                        // Glowing 5-point Star Coin
                        drawStarCoinGraphic(screenPos, wPx / 2f)
                    }
                    RunnerEntityType.COLLECTIBLE_DUMPLING -> {
                        // Legendary Golden Dumpling
                        drawGoldenDumplingGraphic(screenPos, wPx / 2f)
                    }
                    RunnerEntityType.POWERUP_SHIELD -> {
                        // Rainbow Bubble Shield Power-Up
                        drawShieldPowerUpGraphic(screenPos, wPx / 2f, gameTimeSeconds)
                    }
                    RunnerEntityType.POWERUP_MAGNET -> {
                        // Golden Horseshoe Magnet Power-Up
                        drawMagnetPowerUpGraphic(screenPos, wPx / 2f, gameTimeSeconds)
                    }
                    RunnerEntityType.BOOSTER_GATE -> {
                        // Speed 67 Turbo Gate
                        drawBoosterGateGraphic(screenPos, wPx, hPx)
                    }
                    else -> {}
                }
            }

            // --- 3. PARTICLES RENDERING ---
            for (p in particles) {
                val pPos = worldToScreen(p.x, p.y)
                if (p.isStar) {
                    drawStarGraphic(pPos, p.size * density.density, p.color.copy(alpha = p.alpha))
                } else {
                    drawCircle(
                        color = p.color.copy(alpha = p.alpha),
                        radius = p.size * density.density,
                        center = pPos
                    )
                }
            }

            // --- 4. PELMEN-SIX CHARACTER RENDERING ---
            val heroScreenPos = worldToScreen(heroPos.x, heroPos.y)
            val heroRadiusPx = physicsEngine.heroRadius * meterToPx

            // Dynamic velocity squash/stretch
            val velocityStretchY = (1.0f - (heroVel.y / 40f).coerceIn(-0.35f, 0.45f))
            val velocityStretchX = (1.0f / velocityStretchY)
            val finalScaleX = heroSquashX * velocityStretchX
            val finalScaleY = heroSquashY * velocityStretchY

            // Leaning rotation
            val baseRotation = if (isSpinning) doubleJumpSpinAngle else (heroVel.x / 10f) * 6f

            rotate(degrees = baseRotation, pivot = heroScreenPos) {
                scale(scaleX = finalScaleX, scaleY = finalScaleY, pivot = heroScreenPos) {
                    drawPelmenSixHeroGraphic(
                        center = heroScreenPos,
                        radius = heroRadiusPx,
                        isGrounded = physicsEngine.isGrounded,
                        isBoosting = physicsEngine.isBoosting,
                        verticalVelocity = heroVel.y
                    )
                }
            }

            // 🧲 Active Magnetic Field Attraction Waves
            if (physicsEngine.isMagnetActive) {
                drawMagneticFieldArcs(heroScreenPos, heroRadiusPx * 1.55f, gameTimeSeconds)
            }

            // 🛡️ Active Bubble Shield
            if (physicsEngine.isShieldActive) {
                drawBubbleShieldGraphic(heroScreenPos, heroRadiusPx * 1.5f, gameTimeSeconds)
            }

            // --- 5. VISUAL JBOX2D PHYSICS DEBUG OVERLAY ---
            if (showPhysicsDebugOverlay) {
                drawPhysicsDebugOverlay(
                    canvasW = canvasW,
                    canvasH = canvasH,
                    meterToPx = meterToPx,
                    worldToScreen = ::worldToScreen,
                    physicsEngine = physicsEngine,
                    viewLeftMeters = viewLeftMeters,
                    viewRightMeters = viewRightMeters
                )
            }
        }

        // ==========================================
        // TOP HUD: SCORE, DISTANCE, HIGH SCORE, DEBUG, PAUSE
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stats Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Biome Chip
                    val (biomeEmoji, biomeLabel, biomeBg) = when (currentBiomeState) {
                        RunnerBiome.ICE_FREEZER -> Triple("🧊", "Лед", Color(0xFFE0F7FA))
                        RunnerBiome.STEAM_CLOUDS -> Triple("☁️", "Чайная Гора", Color(0xFFF3E5F5))
                        else -> Triple("🍳", "Кухня 67", Color(0xFFFFF9C4))
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = biomeBg,
                        border = BorderStroke(2.dp, ColorInk),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = biomeEmoji, fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = biomeLabel,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = ColorInk
                            )
                        }
                    }

                    // Distance badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ColorWhite,
                        border = BorderStroke(2.dp, ColorInk),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "📏", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${distanceMeters}м",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = ColorInk
                            )
                        }
                    }

                    // Coins badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ColorWhite,
                        border = BorderStroke(2.dp, ColorInk),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🪙", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "$coinsCollected",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = ColorInk
                            )
                        }
                    }

                    // High score badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ColorYellow,
                        border = BorderStroke(2.dp, ColorInk),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🏆", fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "$currentHighScore",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = ColorInk
                            )
                        }
                    }
                }

                // Controls (Sticker Book, Debug Toggle, Sound, Pause, Reset) - 72dp child-friendly touch targets
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 📖 Toddler-Friendly Sticker Album Button (72dp)
                    Surface(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        showStickerBookDialog = true
                                        runnerSoundManager.playUiTap()
                                    }
                                )
                            }
                            .testTag("runner_sticker_book_button"),
                        shape = CircleShape,
                        color = Color(0xFFCE93D8),
                        border = BorderStroke(3.dp, ColorInk),
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = "📖", fontSize = 24.sp)
                                Text(
                                    text = "$unlockedStickerCount/${allStickers.size.coerceAtLeast(12)}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ColorInk
                                )
                            }
                        }
                    }
                    // 🔬 JBox2D Physics Debug Overlay Toggle (72dp)
                    Surface(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        showPhysicsDebugOverlay = !showPhysicsDebugOverlay
                                        runnerSoundManager.playUiTap()
                                    }
                                )
                            }
                            .testTag("runner_debug_toggle"),
                        shape = CircleShape,
                        color = if (showPhysicsDebugOverlay) Color(0xFF00E676) else ColorWhite,
                        border = BorderStroke(3.dp, if (showPhysicsDebugOverlay) Color(0xFF00C853) else ColorInk),
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = "🔬", fontSize = 24.sp)
                                Text(
                                    text = if (showPhysicsDebugOverlay) "СЕТКА" else "ТЕСТ",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (showPhysicsDebugOverlay) Color(0xFF1B5E20) else ColorInk
                                )
                            }
                        }
                    }

                    // 🔊 Toddler-Friendly Sound Toggle (72dp)
                    val soundButtonScale by animateFloatAsState(
                        targetValue = if (isSoundMuted) 0.94f else 1.0f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                        label = "soundButtonScale"
                    )
                    Surface(
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer {
                                scaleX = soundButtonScale
                                scaleY = soundButtonScale
                            }
                            .clip(CircleShape)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        val enabled = runnerSoundManager.toggleSound()
                                        isSoundMuted = !enabled
                                        haptics.click()
                                    }
                                )
                            }
                            .testTag("runner_sound_toggle"),
                        shape = CircleShape,
                        color = if (!isSoundMuted) Color(0xFFFFD54F) else Color(0xFFECEFF1),
                        border = BorderStroke(3.dp, if (!isSoundMuted) ColorInk else Color(0xFF90A4AE)),
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (!isSoundMuted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = if (!isSoundMuted) "Выключить звук" else "Включить звук",
                                    tint = if (!isSoundMuted) ColorInk else Color(0xFF78909C),
                                    modifier = Modifier.size(30.dp)
                                )
                                Text(
                                    text = if (!isSoundMuted) "ЗВУК" else "ТИХО",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (!isSoundMuted) ColorInk else Color(0xFF78909C)
                                )
                            }
                        }
                    }

                    // Pause button (72dp)
                    Surface(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        isPaused = !isPaused
                                        runnerSoundManager.playPauseToggle(isPaused)
                                        if (isPaused) runnerSoundManager.pause() else runnerSoundManager.resume()
                                    }
                                )
                            }
                            .testTag("runner_pause_button"),
                        shape = CircleShape,
                        color = if (isPaused) ColorOrange else ColorWhite,
                        border = BorderStroke(3.dp, ColorInk),
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isPaused) "Resume" else "Pause",
                                tint = ColorInk,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Reset button (72dp)
                    Surface(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        runnerSoundManager.playReset()
                                        physicsEngine.reset()
                                        distanceMeters = 0
                                        coinsCollected = 0
                                        dumplingsCollected = 0
                                        particles.clear()
                                        popups.clear()
                                    }
                                )
                            }
                            .testTag("runner_reset_button"),
                        shape = CircleShape,
                        color = ColorWhite,
                        border = BorderStroke(3.dp, ColorInk),
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Restart",
                                tint = ColorInk,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }
            }

            // 🛡️ & 🧲 Active Power-Up Badges HUD
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (physicsEngine.isShieldActive) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFE0F7FA),
                        border = BorderStroke(2.5.dp, Color(0xFF00B8D4)),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🛡️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ЩИТ ${ceil(physicsEngine.shieldTimer).toInt()}с",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = Color(0xFF006064)
                            )
                        }
                    }
                }

                if (physicsEngine.isMagnetActive) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFFFF9C4),
                        border = BorderStroke(2.5.dp, Color(0xFFFFB300)),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🧲", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "МАГНИТ ${ceil(physicsEngine.magnetTimer).toInt()}с",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }

                if (physicsEngine.isBoosting) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFFFEBEE),
                        border = BorderStroke(2.5.dp, Color(0xFFFF1744)),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "⚡", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ТУРБО 67",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = Color(0xFFC2185B)
                            )
                        }
                    }
                }
            }

            // Realtime Physics Telemetry Floating Card
            if (showPhysicsDebugOverlay) {
                PhysicsDebugHud(
                    physicsEngine = physicsEngine,
                    onClose = { showPhysicsDebugOverlay = false }
                )
            }
        }

        // ==========================================
        // FLOATING SCORE POPUPS OVERLAY
        // ==========================================
        for (popup in popups) {
            Text(
                text = popup.text,
                color = popup.color.copy(alpha = popup.alpha),
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .offset(x = 120.dp, y = (popup.y * 30f).dp)
            )
        }

        // ==========================================
        // TURBO 67 BANNER
        // ==========================================
        AnimatedVisibility(
            visible = physicsEngine.isBoosting,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 70.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ColorPink,
                border = BorderStroke(3.dp, ColorInk),
                shadowElevation = 8.dp
            ) {
                Text(
                    text = "⚡ ТУРБО СИКС-СЕВЕН 67! ⚡",
                    color = ColorWhite,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }

        // ==========================================
        // BIG TODDLER JUMP CONTROLS (BOTTOM RIGHT & BOTTOM LEFT)
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Dive / Slam button (⬇️)
                Surface(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    runnerSoundManager.playDive()
                                    physicsEngine.slamDown()
                                }
                            )
                        }
                        .testTag("runner_dive_button"),
                    shape = CircleShape,
                    color = ColorOrange,
                    border = BorderStroke(3.dp, ColorInk),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = "⬇️", fontSize = 28.sp)
                    }
                }

                // Primary Jump button (🦘 ПРЫЖОК / 88dp)
                Surface(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    performJump()
                                }
                            )
                        }
                        .testTag("runner_jump_button"),
                    shape = CircleShape,
                    color = ColorGreen,
                    border = BorderStroke(4.dp, ColorInk),
                    shadowElevation = 10.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = "🦘", fontSize = 34.sp)
                    }
                }
            }
        }

        // ==========================================
        // 🌟 STICKER UNLOCK CELEBRATION MODAL
        // ==========================================
        if (newlyUnlockedSticker != null) {
            val sticker = newlyUnlockedSticker!!
            Dialog(
                onDismissRequest = { newlyUnlockedSticker = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .pointerInput(Unit) { detectTapGestures { newlyUnlockedSticker = null } },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 380.dp)
                            .padding(24.dp)
                            .pointerInput(Unit) { detectTapGestures { /* consume tap */ } },
                        shape = RoundedCornerShape(28.dp),
                        color = ColorWhite,
                        border = BorderStroke(4.dp, ColorInk),
                        shadowElevation = 16.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "🎉 НОВАЯ НАКЛЕЙКА! 🎉",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFE91E63)
                            )

                            // Sticker Card with golden shine
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = Color(0xFFFFF9C4),
                                border = BorderStroke(3.dp, Color(0xFFFFB300)),
                                shadowElevation = 8.dp,
                                modifier = Modifier.size(130.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = sticker.emoji,
                                        fontSize = 68.sp
                                    )
                                }
                            }

                            Text(
                                text = sticker.title,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = ColorInk
                            )

                            Text(
                                text = sticker.description,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF546E7A)
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onTap = {
                                                    newlyUnlockedSticker = null
                                                    showStickerBookDialog = true
                                                    runnerSoundManager.playUiTap()
                                                }
                                            )
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFCE93D8),
                                    border = BorderStroke(2.5.dp, ColorInk),
                                    shadowElevation = 4.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "В Альбом 📖",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = ColorInk
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onTap = {
                                                    newlyUnlockedSticker = null
                                                    runnerSoundManager.playUiTap()
                                                }
                                            )
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    color = ColorGreen,
                                    border = BorderStroke(2.5.dp, ColorInk),
                                    shadowElevation = 4.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Ура! 🚀",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 17.sp,
                                            color = ColorInk
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 📖 INTERACTIVE STICKER BOOK MODAL
        // ==========================================
        if (showStickerBookDialog) {
            StickerBookDialog(
                allStickers = allStickers,
                unlockedCount = unlockedStickerCount,
                runnerSoundManager = runnerSoundManager,
                onDismiss = { showStickerBookDialog = false }
            )
        }
    }
}

@Composable
fun StickerBookDialog(
    allStickers: List<StickerEntity>,
    unlockedCount: Int,
    runnerSoundManager: RunnerSoundEffectsManager,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Все") }
    val categories = listOf("Все", "Кухня 67", "Ледяная камера", "Чайная Гора")

    val filteredStickers = remember(selectedCategory, allStickers) {
        if (selectedCategory == "Все") allStickers
        else allStickers.filter { it.biome == selectedCategory }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxHeight(0.9f)
                    .padding(16.dp)
                    .pointerInput(Unit) { detectTapGestures { /* consume tap */ } },
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFFFFFDE7),
                border = BorderStroke(4.dp, ColorInk),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "📖 Альбом Наклеек",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = ColorInk
                            )
                            Text(
                                text = "Собрано: $unlockedCount из ${allStickers.size} ✨",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7B1FA2)
                            )
                        }

                        // Close 48dp button
                        Surface(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            runnerSoundManager.playUiTap()
                                            onDismiss()
                                        }
                                    )
                                },
                            shape = CircleShape,
                            color = Color(0xFFFF5252),
                            border = BorderStroke(2.5.dp, ColorInk)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = "✕", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ColorWhite)
                            }
                        }
                    }

                    // Biome Category Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (cat in categories) {
                            val isSelected = selectedCategory == cat
                            val label = when (cat) {
                                "Все" -> "🌟 Все"
                                "Кухня 67" -> "🍳 Кухня"
                                "Ледяная камера" -> "🧊 Лед"
                                "Чайная Гора" -> "☁️ Пар"
                                else -> cat
                            }
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                selectedCategory = cat
                                                runnerSoundManager.playUiTap()
                                            }
                                        )
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFFFFD54F) else ColorWhite,
                                border = BorderStroke(2.dp, if (isSelected) ColorInk else Color(0xFFB0BEC5))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                        color = ColorInk
                                    )
                                }
                            }
                        }
                    }

                    // Stickers Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredStickers, key = { it.id }) { stk ->
                            StickerItemCard(
                                sticker = stk,
                                onStickerTapped = {
                                    if (stk.isUnlocked) {
                                        runnerSoundManager.playBubblyItemCollect(comboStreak = 3)
                                    } else {
                                        runnerSoundManager.playUiTap()
                                    }
                                }
                            )
                        }
                    }

                    // Bottom Big Play Button (minimum 56dp)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        runnerSoundManager.playUiTap()
                                        onDismiss()
                                    }
                                )
                            },
                        shape = RoundedCornerShape(18.dp),
                        color = ColorGreen,
                        border = BorderStroke(3.dp, ColorInk),
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Продолжить Забег! 🏃‍♂️💨",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = ColorInk
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerItemCard(
    sticker: StickerEntity,
    onStickerTapped: () -> Unit
) {
    var isTappedPop by remember { mutableStateOf(false) }
    val popScale by animateFloatAsState(
        targetValue = if (isTappedPop) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        finishedListener = { isTappedPop = false },
        label = "stickerPop"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .graphicsLayer {
                scaleX = popScale
                scaleY = popScale
            }
            .clip(RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        isTappedPop = true
                        onStickerTapped()
                    }
                )
            },
        shape = RoundedCornerShape(18.dp),
        color = if (sticker.isUnlocked) ColorWhite else Color(0xFFECEFF1),
        border = BorderStroke(
            width = if (sticker.isUnlocked) 2.5.dp else 1.5.dp,
            color = if (sticker.isUnlocked) Color(0xFFFFB300) else Color(0xFFB0BEC5)
        ),
        shadowElevation = if (sticker.isUnlocked) 4.dp else 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp),
                contentAlignment = Alignment.Center
            ) {
                if (sticker.isUnlocked) {
                    Text(
                        text = sticker.emoji,
                        fontSize = 38.sp
                    )
                } else {
                    Text(
                        text = "🔒",
                        fontSize = 28.sp
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (sticker.isUnlocked) sticker.title else "???",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = if (sticker.isUnlocked) ColorInk else Color(0xFF90A4AE),
                    maxLines = 1
                )
                Text(
                    text = if (sticker.isUnlocked) "${sticker.requiredDistance}м" else "На ${sticker.requiredDistance}м",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (sticker.isUnlocked) Color(0xFF388E3C) else Color(0xFFB0BEC5)
                )
            }
        }
    }
}

// ==========================================
// GRAPHIC DRAWING HELPERS (CANVAS RENDERING)
// ==========================================

private fun DrawScope.drawPelmenSixHeroGraphic(
    center: Offset,
    radius: Float,
    isGrounded: Boolean,
    isBoosting: Boolean,
    verticalVelocity: Float
) {
    val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Shadow on floor if grounded
    if (isGrounded) {
        drawOval(
            color = ColorInk.copy(alpha = 0.2f),
            topLeft = Offset(center.x - radius * 0.85f, center.y + radius * 0.75f),
            size = Size(radius * 1.7f, radius * 0.45f)
        )
    }

    // Cute Little Running Legs
    val legY = center.y + radius * 0.75f
    val legR = radius * 0.22f
    val legOffset = if (isGrounded) sin(System.currentTimeMillis() * 0.018f) * 12.dp.toPx() else 0f

    // Left Foot
    drawCircle(color = Color(0xFFFFCC80), radius = legR, center = Offset(center.x - radius * 0.45f + legOffset, legY))
    drawCircle(color = ColorInk, radius = legR, center = Offset(center.x - radius * 0.45f + legOffset, legY), style = inkStroke)

    // Right Foot
    drawCircle(color = Color(0xFFFFCC80), radius = legR, center = Offset(center.x + radius * 0.45f - legOffset, legY))
    drawCircle(color = ColorInk, radius = legR, center = Offset(center.x + radius * 0.45f - legOffset, legY), style = inkStroke)

    // Main Dumpling Body (Soft Pill / Oval Shape)
    val bodyPath = Path().apply {
        val left = center.x - radius
        val right = center.x + radius
        val top = center.y - radius * 0.9f
        val bottom = center.y + radius * 0.85f

        moveTo(left + radius * 0.3f, top)
        // Fluted Dumpling Folds on top
        val crestCount = 4
        val step = (radius * 1.4f) / crestCount
        for (i in 0 until crestCount) {
            val cx = left + radius * 0.3f + i * step + step / 2f
            val cy = top - 10.dp.toPx()
            val ex = left + radius * 0.3f + (i + 1) * step
            quadraticBezierTo(cx, cy, ex, top)
        }
        // Round bottom
        cubicTo(right + 6.dp.toPx(), center.y, right, bottom, center.x, bottom + 4.dp.toPx())
        cubicTo(left, bottom, left - 6.dp.toPx(), center.y, left + radius * 0.3f, top)
        close()
    }

    // Body Fill (Warm Dumpling Cream)
    drawPath(
        path = bodyPath,
        brush = Brush.radialGradient(
            colors = listOf(
                if (isBoosting) Color(0xFFFFEB3B) else Color.White,
                if (isBoosting) Color(0xFFFF9800) else Color(0xFFFFF9C4),
                Color(0xFFFFE082)
            ),
            center = Offset(center.x - radius * 0.2f, center.y - radius * 0.2f),
            radius = radius * 1.2f
        )
    )
    drawPath(path = bodyPath, color = ColorInk, style = inkStroke)

    // Rosy Pink Cheeks
    drawCircle(color = Color(0xFFFF80AB).copy(alpha = 0.65f), radius = radius * 0.18f, center = Offset(center.x - radius * 0.48f, center.y + radius * 0.18f))
    drawCircle(color = Color(0xFFFF80AB).copy(alpha = 0.65f), radius = radius * 0.18f, center = Offset(center.x + radius * 0.48f, center.y + radius * 0.18f))

    // Expressive Eyes
    val eyeR = radius * 0.14f
    val leftEyeCenter = Offset(center.x - radius * 0.28f, center.y - radius * 0.05f)
    val rightEyeCenter = Offset(center.x + radius * 0.28f, center.y - radius * 0.05f)

    if (verticalVelocity < -6f) {
        // Jumping up: Excited Big Eyes
        drawCircle(color = ColorInk, radius = eyeR * 1.15f, center = leftEyeCenter)
        drawCircle(color = ColorWhite, radius = eyeR * 0.5f, center = Offset(leftEyeCenter.x - 3.dp.toPx(), leftEyeCenter.y - 3.dp.toPx()))
        drawCircle(color = ColorInk, radius = eyeR * 1.15f, center = rightEyeCenter)
        drawCircle(color = ColorWhite, radius = eyeR * 0.5f, center = Offset(rightEyeCenter.x - 3.dp.toPx(), rightEyeCenter.y - 3.dp.toPx()))
    } else {
        // Happy Running Eyes
        drawCircle(color = ColorInk, radius = eyeR, center = leftEyeCenter)
        drawCircle(color = ColorWhite, radius = eyeR * 0.4f, center = Offset(leftEyeCenter.x - 2.dp.toPx(), leftEyeCenter.y - 2.dp.toPx()))
        drawCircle(color = ColorInk, radius = eyeR, center = rightEyeCenter)
        drawCircle(color = ColorWhite, radius = eyeR * 0.4f, center = Offset(rightEyeCenter.x - 2.dp.toPx(), rightEyeCenter.y - 2.dp.toPx()))
    }

    // Happy Mouth
    val mouthPath = Path().apply {
        val mx = center.x
        val my = center.y + radius * 0.32f
        moveTo(mx - 10.dp.toPx(), my)
        quadraticBezierTo(mx, my + 14.dp.toPx(), mx + 10.dp.toPx(), my)
        close()
    }
    drawPath(mouthPath, color = Color(0xFFD81B60))
    drawPath(mouthPath, color = ColorInk, style = Stroke(width = 2.dp.toPx()))

    // Red Headband with "67"
    val bandY = center.y - radius * 0.35f
    drawRoundRect(
        color = Color(0xFFFF1744),
        topLeft = Offset(center.x - radius * 0.72f, bandY - 6.dp.toPx()),
        size = Size(radius * 1.44f, 13.dp.toPx()),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    )
    drawRoundRect(
        color = ColorInk,
        topLeft = Offset(center.x - radius * 0.72f, bandY - 6.dp.toPx()),
        size = Size(radius * 1.44f, 13.dp.toPx()),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        style = Stroke(width = 2.dp.toPx())
    )
}

private fun DrawScope.drawTrampolineGraphic(center: Offset, width: Float, height: Float) {
    val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Metal Spring base
    val springPath = Path().apply {
        moveTo(center.x, center.y + height / 2f)
        lineTo(center.x - 12.dp.toPx(), center.y + height * 0.2f)
        lineTo(center.x + 12.dp.toPx(), center.y - height * 0.1f)
        lineTo(center.x, center.y - height * 0.3f)
    }
    drawPath(springPath, color = Color(0xFF78909C), style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))

    // Red Polka Dot Mushroom Cap
    val capPath = Path().apply {
        val left = center.x - width / 2f
        val right = center.x + width / 2f
        val top = center.y - height / 2f
        val bottom = center.y + height * 0.15f
        moveTo(left, bottom)
        quadraticBezierTo(center.x, top - 12.dp.toPx(), right, bottom)
        close()
    }
    drawPath(capPath, color = Color(0xFFFF1744))
    drawPath(capPath, color = ColorInk, style = inkStroke)

    // White polka dots
    drawCircle(color = ColorWhite, radius = 6.dp.toPx(), center = Offset(center.x, center.y - 6.dp.toPx()))
    drawCircle(color = ColorWhite, radius = 5.dp.toPx(), center = Offset(center.x - width * 0.26f, center.y - 2.dp.toPx()))
    drawCircle(color = ColorWhite, radius = 5.dp.toPx(), center = Offset(center.x + width * 0.26f, center.y - 2.dp.toPx()))
}

private fun DrawScope.drawBumperGraphic(center: Offset, radius: Float) {
    val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)

    // Outer Glow / Ring
    drawCircle(color = Color(0xFFFFD54F).copy(alpha = 0.4f), radius = radius * 1.25f, center = center)

    // Pot Lid Metallic Circle
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFCA28), Color(0xFFFFA000)),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
    drawCircle(color = ColorInk, radius = radius, center = center, style = inkStroke)

    // Brass Knob in Center
    drawCircle(color = Color(0xFFFF6F00), radius = radius * 0.35f, center = center)
    drawCircle(color = ColorInk, radius = radius * 0.35f, center = center, style = Stroke(width = 2.dp.toPx()))

    // Inner Concentric Ring
    drawCircle(color = Color(0xFFFFF8E1), radius = radius * 0.65f, center = center, style = Stroke(width = 2.5.dp.toPx()))
}

private fun DrawScope.drawButterObstacleGraphic(center: Offset, width: Float, height: Float) {
    val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    drawRoundRect(
        color = Color(0xFFFFEB3B),
        topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
    )
    drawRoundRect(
        color = ColorInk,
        topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        style = inkStroke
    )

    // Melting butter drops
    drawCircle(color = Color(0xFFFDD835), radius = 5.dp.toPx(), center = Offset(center.x - width * 0.25f, center.y - height * 0.15f))
    drawCircle(color = Color(0xFFFDD835), radius = 4.dp.toPx(), center = Offset(center.x + width * 0.2f, center.y + height * 0.1f))
}

private fun DrawScope.drawChiliPepperGraphic(center: Offset, width: Float, height: Float) {
    val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    val pepperPath = Path().apply {
        val top = center.y - height / 2f
        val bot = center.y + height / 2f
        moveTo(center.x - width * 0.35f, top + 8.dp.toPx())
        cubicTo(center.x - width * 0.5f, center.y, center.x - 4.dp.toPx(), bot, center.x, bot)
        cubicTo(center.x + width * 0.45f, center.y, center.x + width * 0.35f, top + 8.dp.toPx(), center.x, top + 6.dp.toPx())
        close()
    }
    drawPath(pepperPath, color = Color(0xFFD50000))
    drawPath(pepperPath, color = ColorInk, style = inkStroke)

    // Green stem
    drawRoundRect(
        color = Color(0xFF2E7D32),
        topLeft = Offset(center.x - 4.dp.toPx(), center.y - height / 2f - 4.dp.toPx()),
        size = Size(8.dp.toPx(), 10.dp.toPx()),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )
}

private fun DrawScope.drawStarCoinGraphic(center: Offset, radius: Float) {
    drawCircle(color = Color(0xFFFFD700).copy(alpha = 0.3f), radius = radius * 1.35f, center = center)
    drawCircle(color = Color(0xFFFFD700), radius = radius, center = center)
    drawCircle(color = ColorInk, radius = radius, center = center, style = Stroke(width = 2.dp.toPx()))

    // Inner Star
    drawStarGraphic(center, radius * 0.7f, ColorWhite)
}

private fun DrawScope.drawGoldenDumplingGraphic(center: Offset, radius: Float) {
    drawCircle(color = Color(0xFFFFEB3B).copy(alpha = 0.4f), radius = radius * 1.5f, center = center)

    // Golden dumpling shape
    val dPath = Path().apply {
        moveTo(center.x - radius, center.y)
        quadraticBezierTo(center.x, center.y - radius * 1.2f, center.x + radius, center.y)
        quadraticBezierTo(center.x, center.y + radius * 0.9f, center.x - radius, center.y)
        close()
    }
    drawPath(
        path = dPath,
        brush = Brush.radialGradient(
            colors = listOf(ColorWhite, Color(0xFFFFD700), Color(0xFFFF8F00)),
            center = center,
            radius = radius * 1.2f
        )
    )
    drawPath(path = dPath, color = ColorInk, style = Stroke(width = 2.5.dp.toPx()))
}

private fun DrawScope.drawBoosterGateGraphic(center: Offset, width: Float, height: Float) {
    val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)

    // Rainbow Gate Arch
    val archPath = Path().apply {
        val left = center.x - width / 2f
        val right = center.x + width / 2f
        val top = center.y - height / 2f
        val bot = center.y + height / 2f
        moveTo(left, bot)
        lineTo(left, top + 20.dp.toPx())
        quadraticBezierTo(center.x, top - 15.dp.toPx(), right, top + 20.dp.toPx())
        lineTo(right, bot)
    }
    drawPath(
        path = archPath,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFFF1744), Color(0xFFFFEA00), Color(0xFF00E676), Color(0xFF2979FF))
        ),
        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
    )
    drawPath(path = archPath, color = ColorInk, style = inkStroke)

    // Rotating 67 icon badge at peak
    drawCircle(color = Color(0xFFFF4081), radius = 14.dp.toPx(), center = Offset(center.x, center.y - height / 2f))
    drawCircle(color = ColorInk, radius = 14.dp.toPx(), center = Offset(center.x, center.y - height / 2f), style = Stroke(width = 2.dp.toPx()))
}

private fun DrawScope.drawIcePlatformGraphic(center: Offset, width: Float, height: Float, time: Float) {
    val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val left = center.x - width / 2f
    val top = center.y - height / 2f

    // Frozen ice crystal body
    val iceBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFE0F7FA), // frosted white-cyan rim
            Color(0xFF80DEEA), // sparkling crystal blue
            Color(0xFF00ACC1), // deep glacier turquoise
            Color(0xFF00838F)
        ),
        startY = top,
        endY = top + height
    )
    drawRoundRect(
        brush = iceBrush,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
    )
    // Dark ink border
    drawRoundRect(
        color = ColorInk,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
        style = inkStroke
    )

    // Top glossy slippery glint line
    drawLine(
        color = Color.White.copy(alpha = 0.9f),
        start = Offset(left + 6.dp.toPx(), top + 3.dp.toPx()),
        end = Offset(left + width - 6.dp.toPx(), top + 3.dp.toPx()),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Icicles hanging underneath
    val icicleCount = (width / 24.dp.toPx()).toInt().coerceIn(2, 6)
    val step = width / icicleCount
    for (i in 0 until icicleCount) {
        val ix = left + (i + 0.5f) * step
        val iLen = ((i % 2) * 5 + 8).dp.toPx()
        val iciclePath = Path().apply {
            moveTo(ix - 4.dp.toPx(), top + height)
            lineTo(ix + 4.dp.toPx(), top + height)
            lineTo(ix, top + height + iLen)
            close()
        }
        drawPath(iciclePath, color = Color(0xFFB2EBF2))
        drawPath(iciclePath, color = ColorInk, style = Stroke(width = 1.5.dp.toPx()))
    }

    // Sparkle star on ice
    val sparklePulse = (sin(time * 4f) * 0.5f + 0.5f)
    drawStarGraphic(Offset(left + width * 0.3f, top + height * 0.45f), 4.dp.toPx() * sparklePulse, Color.White)
}

private fun DrawScope.drawJellyBumperGraphic(center: Offset, width: Float, height: Float, time: Float) {
    val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val wobbleX = sin(time * 7f) * 3.dp.toPx()
    val wobbleY = cos(time * 7f) * 2.dp.toPx()

    val left = center.x - width / 2f
    val right = center.x + width / 2f
    val top = center.y - height / 2f + wobbleY
    val bot = center.y + height / 2f

    // Soft bouncy jelly dome path
    val jellyPath = Path().apply {
        moveTo(left, bot)
        cubicTo(
            left - 4.dp.toPx() + wobbleX, top + height * 0.3f,
            center.x - width * 0.3f, top - 6.dp.toPx(),
            center.x, top - 6.dp.toPx()
        )
        cubicTo(
            center.x + width * 0.3f, top - 6.dp.toPx(),
            right + 4.dp.toPx() - wobbleX, top + height * 0.3f,
            right, bot
        )
        close()
    }

    // Rich translucent marmalade gradient
    val jellyBrush = Brush.radialGradient(
        colors = listOf(
            Color(0xFFFF80AB),
            Color(0xFFFF4081),
            Color(0xFFE91E63),
            Color(0xFFC2185B)
        ),
        center = Offset(center.x, top + height * 0.3f),
        radius = width * 0.75f
    )
    drawPath(jellyPath, brush = jellyBrush)
    drawPath(jellyPath, color = ColorInk, style = inkStroke)

    // Glossy highlights (white shine arc)
    val shinePath = Path().apply {
        moveTo(center.x - width * 0.25f, top + 2.dp.toPx())
        quadraticBezierTo(center.x, top - 2.dp.toPx(), center.x + width * 0.15f, top + 3.dp.toPx())
    }
    drawPath(shinePath, color = Color.White.copy(alpha = 0.85f), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))

    // Sugar crystal dots
    drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(center.x - width * 0.2f, top + height * 0.45f))
    drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(center.x + width * 0.22f, top + height * 0.4f))
    drawCircle(color = Color(0xFFFFEB3B), radius = 2.5.dp.toPx(), center = Offset(center.x + 2.dp.toPx(), top + height * 0.55f))
}

private fun DrawScope.drawSteamVentGraphic(center: Offset, width: Float, height: Float, time: Float) {
    val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
    val botY = center.y + height / 2f
    val ventW = width * 0.85f
    val ventH = 14.dp.toPx()

    // Brass Tea Kettle Spout Nozzle at bottom
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color(0xFFFFB300), Color(0xFFFFD54F), Color(0xFFFF8F00))
        ),
        topLeft = Offset(center.x - ventW / 2f, botY - ventH),
        size = Size(ventW, ventH),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    )
    drawRoundRect(
        color = ColorInk,
        topLeft = Offset(center.x - ventW / 2f, botY - ventH),
        size = Size(ventW, ventH),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        style = inkStroke
    )

    // Billowing rising steam puffs (animated upwards with time)
    val steamPuffCount = 4
    for (i in 0 until steamPuffCount) {
        val progress = ((time * 1.2f + i * 0.25f) % 1.0f)
        val puffY = botY - ventH - progress * (height * 0.85f)
        val puffWobble = sin(progress * 8f + i) * 10.dp.toPx()
        val puffX = center.x + puffWobble
        val puffRadius = (8.dp.toPx() + progress * 16.dp.toPx())
        val alpha = (1.0f - progress).coerceIn(0.15f, 0.75f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = alpha),
                    Color(0xFFE1BEE7).copy(alpha = alpha * 0.75f),
                    Color(0xFFB388FF).copy(alpha = 0f)
                ),
                center = Offset(puffX, puffY),
                radius = puffRadius
            ),
            radius = puffRadius,
            center = Offset(puffX, puffY)
        )
    }

    // Gentle upward motion arrows in center
    val arrowProgress = ((time * 2f) % 1.0f)
    val arrowY = botY - ventH - arrowProgress * (height * 0.5f)
    val arrowPath = Path().apply {
        moveTo(center.x - 8.dp.toPx(), arrowY)
        lineTo(center.x, arrowY - 8.dp.toPx())
        lineTo(center.x + 8.dp.toPx(), arrowY)
    }
    drawPath(arrowPath, color = Color(0xFFBA68C8).copy(alpha = (1f - arrowProgress) * 0.8f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawStarGraphic(center: Offset, radius: Float, color: Color) {
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
    drawPath(starPath, color = color)
}

private fun DrawScope.drawCloudGraphic(center: Offset, sizePx: Float) {
    val r = sizePx / 3f
    drawCircle(color = ColorWhite.copy(alpha = 0.5f), radius = r, center = center)
    drawCircle(color = ColorWhite.copy(alpha = 0.5f), radius = r * 0.8f, center = Offset(center.x - r * 0.7f, center.y + r * 0.1f))
    drawCircle(color = ColorWhite.copy(alpha = 0.5f), radius = r * 0.85f, center = Offset(center.x + r * 0.7f, center.y + r * 0.1f))
}

private fun DrawScope.drawShieldPowerUpGraphic(center: Offset, radius: Float, time: Float) {
    val hoverY = sin(time * 3.5f) * 4.dp.toPx()
    val pos = Offset(center.x, center.y + hoverY)
    val inkStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Outer cyan glow
    drawCircle(
        color = Color(0xFF00E5FF).copy(alpha = 0.25f + 0.15f * sin(time * 4f)),
        radius = radius * 1.4f,
        center = pos
    )

    // Soap bubble body
    val bubbleBrush = Brush.radialGradient(
        colors = listOf(
            Color(0xEEFFFFFF),
            Color(0xCCB2EBF2),
            Color(0xBB00E5FF),
            Color(0x990091EA)
        ),
        center = Offset(pos.x - radius * 0.3f, pos.y - radius * 0.3f),
        radius = radius * 1.2f
    )
    drawCircle(brush = bubbleBrush, radius = radius, center = pos)
    drawCircle(color = ColorInk, radius = radius, center = pos, style = inkStroke)

    // Inside Shield Silhouette / Crest
    val shieldPath = Path().apply {
        val sw = radius * 0.7f
        val sh = radius * 0.85f
        moveTo(pos.x, pos.y - sh * 0.5f)
        lineTo(pos.x + sw * 0.5f, pos.y - sh * 0.3f)
        quadraticBezierTo(pos.x + sw * 0.45f, pos.y + sh * 0.35f, pos.x, pos.y + sh * 0.55f)
        quadraticBezierTo(pos.x - sw * 0.45f, pos.y + sh * 0.35f, pos.x - sw * 0.5f, pos.y - sh * 0.3f)
        close()
    }
    drawPath(shieldPath, color = Color(0xFF00B0FF))
    drawPath(shieldPath, color = ColorInk, style = Stroke(width = 2.dp.toPx()))

    // Inner bright star on shield
    drawStarGraphic(Offset(pos.x, pos.y), radius * 0.28f, ColorWhite)

    // Specular bubble glint
    drawCircle(
        color = ColorWhite.copy(alpha = 0.9f),
        radius = radius * 0.22f,
        center = Offset(pos.x - radius * 0.45f, pos.y - radius * 0.45f)
    )
}

private fun DrawScope.drawMagnetPowerUpGraphic(center: Offset, radius: Float, time: Float) {
    val hoverY = sin(time * 3.5f + 1.2f) * 4.dp.toPx()
    val pos = Offset(center.x, center.y + hoverY)
    val inkStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Outer warm gold aura
    drawCircle(
        color = Color(0xFFFFD54F).copy(alpha = 0.3f + 0.15f * sin(time * 4f)),
        radius = radius * 1.4f,
        center = pos
    )

    // Base background circle
    drawCircle(color = Color(0xFFFFF9C4), radius = radius, center = pos)
    drawCircle(color = ColorInk, radius = radius, center = pos, style = inkStroke)

    // Horseshoe Magnet
    val uWidth = radius * 0.95f
    val uHeight = radius * 1.05f
    val magnetPath = Path().apply {
        val left = pos.x - uWidth * 0.45f
        val right = pos.x + uWidth * 0.45f
        val top = pos.y - uHeight * 0.4f
        val bot = pos.y + uHeight * 0.15f
        moveTo(left, top)
        lineTo(left, bot)
        quadraticBezierTo(pos.x, pos.y + uHeight * 0.5f, right, bot)
        lineTo(right, top)
    }
    drawPath(magnetPath, color = ColorInk, style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Butt))
    drawPath(magnetPath, color = Color(0xFFFF1744), style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Butt))

    // Silver poles on magnet ends
    val poleW = 7.dp.toPx()
    val poleH = 5.dp.toPx()
    drawRoundRect(
        color = Color(0xFFCFD8DC),
        topLeft = Offset(pos.x - uWidth * 0.45f - poleW / 2f, pos.y - uHeight * 0.4f - poleH / 2f),
        size = Size(poleW, poleH),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )
    drawRoundRect(
        color = ColorInk,
        topLeft = Offset(pos.x - uWidth * 0.45f - poleW / 2f, pos.y - uHeight * 0.4f - poleH / 2f),
        size = Size(poleW, poleH),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFFCFD8DC),
        topLeft = Offset(pos.x + uWidth * 0.45f - poleW / 2f, pos.y - uHeight * 0.4f - poleH / 2f),
        size = Size(poleW, poleH),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )
    drawRoundRect(
        color = ColorInk,
        topLeft = Offset(pos.x + uWidth * 0.45f - poleW / 2f, pos.y - uHeight * 0.4f - poleH / 2f),
        size = Size(poleW, poleH),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx())
    )

    // Sparkle star above poles
    drawStarGraphic(Offset(pos.x, pos.y - uHeight * 0.35f), radius * 0.22f, Color(0xFFFFD700))
}

private fun DrawScope.drawBubbleShieldGraphic(center: Offset, radius: Float, time: Float) {
    val pulse = sin(time * 5f) * 3.dp.toPx()
    val curRadius = radius + pulse

    // Soft shimmering iridescent gradient
    val rainbowGlow = Brush.radialGradient(
        colors = listOf(
            Color(0x11FFFFFF),
            Color(0x3300E5FF),
            Color(0x33E040FB),
            Color(0x44FFD700),
            Color(0x5500E5FF)
        ),
        center = Offset(center.x - curRadius * 0.25f, center.y - curRadius * 0.25f),
        radius = curRadius * 1.15f
    )
    drawCircle(brush = rainbowGlow, radius = curRadius, center = center)

    // Bubble rim outline
    drawCircle(
        color = Color(0xBB00E5FF),
        radius = curRadius,
        center = center,
        style = Stroke(width = 3.dp.toPx())
    )
    drawCircle(
        color = ColorWhite.copy(alpha = 0.5f),
        radius = curRadius - 2.dp.toPx(),
        center = center,
        style = Stroke(width = 1.5.dp.toPx())
    )

    // Specular highlight crescent at top-left
    val sheenRadius = curRadius * 0.28f
    drawCircle(
        color = ColorWhite.copy(alpha = 0.85f),
        radius = sheenRadius,
        center = Offset(center.x - curRadius * 0.55f, center.y - curRadius * 0.55f)
    )
    drawCircle(
        color = ColorWhite.copy(alpha = 0.6f),
        radius = sheenRadius * 0.45f,
        center = Offset(center.x - curRadius * 0.32f, center.y - curRadius * 0.68f)
    )

    // Floating micro-bubbles
    val bAngle1 = time * 2f
    val bAngle2 = time * 2.5f + 2f
    val bx1 = center.x + cos(bAngle1) * (curRadius * 0.95f)
    val by1 = center.y + sin(bAngle1) * (curRadius * 0.95f)
    drawCircle(color = Color(0xAA80DEEA), radius = 4.dp.toPx(), center = Offset(bx1, by1))
    val bx2 = center.x + cos(bAngle2) * (curRadius * 0.95f)
    val by2 = center.y + sin(bAngle2) * (curRadius * 0.95f)
    drawCircle(color = Color(0xAAFFD54F), radius = 3.dp.toPx(), center = Offset(bx2, by2))
}

private fun DrawScope.drawMagneticFieldArcs(center: Offset, radius: Float, time: Float) {
    // 3 concentric magnetic flux arcs rippling in front of the character
    for (i in 1..3) {
        val waveOffset = ((time * 3f + i * 0.6f) % 2.0f) / 2.0f
        val arcRadius = radius * (1.1f + waveOffset * 0.6f)
        val alpha = (1.0f - waveOffset).coerceIn(0f, 1f) * 0.75f
        val arcPath = Path().apply {
            val sweep = 110f
            val startAngle = -55f
            arcTo(
                rect = Rect(
                    center.x - arcRadius,
                    center.y - arcRadius,
                    center.x + arcRadius,
                    center.y + arcRadius
                ),
                startAngleDegrees = startAngle,
                sweepAngleDegrees = sweep,
                forceMoveTo = false
            )
        }
        val arcColor = if (i % 2 == 0) Color(0xFFFFD700) else Color(0xFF00E5FF)
        drawPath(
            path = arcPath,
            color = arcColor.copy(alpha = alpha),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

// ==========================================
// MULTI-LAYERED PARALLAX BACKGROUND
// ==========================================

private fun DrawScope.drawParallaxMultiLayerBackground(
    canvasW: Float,
    canvasH: Float,
    meterToPx: Float,
    cameraX: Float,
    groundY: Float,
    gameTime: Float,
    biome: RunnerBiome = RunnerBiome.KITCHEN
) {
    // ----------------------------------------------------
    // LAYER 0: Sky Gradient & Radiant Celestial Body (0.03x Parallax)
    // ----------------------------------------------------
    val skyColors = when (biome) {
        RunnerBiome.ICE_FREEZER -> listOf(
            Color(0xFF006064), // Deep arctic cyan
            Color(0xFF00838F),
            Color(0xFF4DD0E1),
            Color(0xFFE0F7FA)  // Frosty icy horizon
        )
        RunnerBiome.STEAM_CLOUDS -> listOf(
            Color(0xFF311B92), // Twilight purple
            Color(0xFF6A1B9A),
            Color(0xFFBA68C8),
            Color(0xFFFFD180)  // Golden peach sunset horizon
        )
        RunnerBiome.KITCHEN -> listOf(
            Color(0xFF81D4FA), // Deep sky blue
            Color(0xFFB3E5FC),
            Color(0xFFE0F7FA),
            Color(0xFFFFF3E0)  // Warm peach horizon
        )
        RunnerBiome.CLOUD_KITCHEN -> listOf(
            Color(0xFF81D4FA), // Sky blue
            Color(0xFFB3E5FC),
            Color(0xFFFFF9C4), // Light yellow
            Color(0xFFFFCCBC)  // Peach sunset
        )
    }
    drawRect(
        brush = Brush.verticalGradient(colors = skyColors),
        topLeft = Offset.Zero,
        size = Size(canvasW, canvasH)
    )

    // Radiant Celestial Body (0.03x Parallax)
    val sunWrapW = canvasW + 240.dp.toPx()
    val sunScroll = (cameraX * meterToPx * 0.03f) % sunWrapW
    val sunX = (canvasW * 0.82f - sunScroll + sunWrapW) % sunWrapW
    val sunY = 65.dp.toPx()
    val sunR = 26.dp.toPx()

    when (biome) {
        RunnerBiome.ICE_FREEZER -> {
            // Frosty Moon with glowing halo
            drawCircle(color = Color(0xFFE0F7FA).copy(alpha = 0.35f), radius = sunR * 1.4f, center = Offset(sunX, sunY))
            drawCircle(color = Color(0xFFB2EBF2), radius = sunR, center = Offset(sunX, sunY))
            drawCircle(color = ColorInk, radius = sunR, center = Offset(sunX, sunY), style = Stroke(width = 2.5.dp.toPx()))
            // Sleepy winter face
            drawCircle(color = ColorInk, radius = 2.5.dp.toPx(), center = Offset(sunX - 7.dp.toPx(), sunY - 3.dp.toPx()))
            drawCircle(color = ColorInk, radius = 2.5.dp.toPx(), center = Offset(sunX + 7.dp.toPx(), sunY - 3.dp.toPx()))
            drawArc(
                color = ColorInk,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(sunX - 6.dp.toPx(), sunY - 2.dp.toPx()),
                size = Size(12.dp.toPx(), 8.dp.toPx()),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        RunnerBiome.STEAM_CLOUDS -> {
            // Radiant Golden Sunset Sun in Clouds
            drawCircle(color = Color(0xFFFFD54F).copy(alpha = 0.45f), radius = sunR * 1.5f, center = Offset(sunX, sunY))
            drawCircle(color = Color(0xFFFFCA28), radius = sunR, center = Offset(sunX, sunY))
            drawCircle(color = ColorInk, radius = sunR, center = Offset(sunX, sunY), style = Stroke(width = 2.5.dp.toPx()))
            // Cheerful face
            drawCircle(color = ColorInk, radius = 2.5.dp.toPx(), center = Offset(sunX - 7.dp.toPx(), sunY - 3.dp.toPx()))
            drawCircle(color = ColorInk, radius = 2.5.dp.toPx(), center = Offset(sunX + 7.dp.toPx(), sunY - 3.dp.toPx()))
            drawArc(
                color = ColorInk,
                startAngle = 10f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(sunX - 7.dp.toPx(), sunY - 2.dp.toPx()),
                size = Size(14.dp.toPx(), 10.dp.toPx()),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        RunnerBiome.KITCHEN -> {
            // Rotating Sun Rays & Sun Disc
            val rayCount = 10
            val rayAngleOffset = (gameTime * 20f) % 360f
            for (i in 0 until rayCount) {
                val angleRad = Math.toRadians((i * (360.0 / rayCount) + rayAngleOffset))
                val rInner = sunR + 5.dp.toPx()
                val rOuter = sunR + 14.dp.toPx()
                val p1 = Offset(sunX + (cos(angleRad) * rInner).toFloat(), sunY + (sin(angleRad) * rInner).toFloat())
                val p2 = Offset(sunX + (cos(angleRad) * rOuter).toFloat(), sunY + (sin(angleRad) * rOuter).toFloat())
                drawLine(
                    color = Color(0xFFFFD54F),
                    start = p1,
                    end = p2,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            drawCircle(color = Color(0xFFFFE082), radius = sunR, center = Offset(sunX, sunY))
            drawCircle(color = Color(0xFFFFB300), radius = sunR, center = Offset(sunX, sunY), style = Stroke(width = 2.5.dp.toPx()))
            drawCircle(color = ColorInk, radius = 2.5.dp.toPx(), center = Offset(sunX - 7.dp.toPx(), sunY - 3.dp.toPx()))
            drawCircle(color = ColorInk, radius = 2.5.dp.toPx(), center = Offset(sunX + 7.dp.toPx(), sunY - 3.dp.toPx()))
            drawArc(
                color = ColorInk,
                startAngle = 10f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(sunX - 7.dp.toPx(), sunY - 2.dp.toPx()),
                size = Size(14.dp.toPx(), 10.dp.toPx()),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        RunnerBiome.CLOUD_KITCHEN -> {
            // Cloud Kitchen Sun
            drawCircle(color = Color(0xFFFFD54F).copy(alpha = 0.45f), radius = sunR * 1.5f, center = Offset(sunX, sunY))
        }

    }

    // ----------------------------------------------------
    // LAYER 1: Distant Silhouette Hills (0.08x Parallax)
    // ----------------------------------------------------
    val hillRepeatW = 420.dp.toPx()
    val hillOffset = -(cameraX * meterToPx * 0.08f) % hillRepeatW
    val hillBaseY = (groundY - 3.2f) * meterToPx
    val hillPath = Path()
    hillPath.moveTo(0f, canvasH)
    for (i in -1..(canvasW / hillRepeatW).toInt() + 2) {
        val startX = i * hillRepeatW + hillOffset
        if (i == -1) hillPath.lineTo(startX, hillBaseY)
        hillPath.cubicTo(
            startX + hillRepeatW * 0.25f, hillBaseY - 38.dp.toPx(),
            startX + hillRepeatW * 0.75f, hillBaseY + 28.dp.toPx(),
            startX + hillRepeatW, hillBaseY
        )
    }
    hillPath.lineTo(canvasW, canvasH)
    hillPath.close()
    drawPath(path = hillPath, color = Color(0xFFB2DFDB).copy(alpha = 0.42f))

    // ----------------------------------------------------
    // LAYER 2: Distant Floating Fluffy Clouds (0.18x Parallax)
    // ----------------------------------------------------
    val cloudWrap = canvasW + 360.dp.toPx()
    val cOffset = -(cameraX * meterToPx * 0.18f) % cloudWrap
    drawCloudGraphic(Offset((cOffset + 80.dp.toPx() + cloudWrap) % cloudWrap, 60.dp.toPx()), 85.dp.toPx())
    drawCloudGraphic(Offset((cOffset + canvasW * 0.52f + cloudWrap) % cloudWrap, 115.dp.toPx()), 115.dp.toPx())
    drawCloudGraphic(Offset((cOffset + canvasW * 0.88f + cloudWrap) % cloudWrap, 80.dp.toPx()), 75.dp.toPx())

    // ----------------------------------------------------
    // LAYER 3: Kitchen Wall Wallpaper / Tile Grid (0.35x Parallax)
    // ----------------------------------------------------
    val tileSpacingPx = 76.dp.toPx()
    val tileOffsetX = -(cameraX * meterToPx * 0.35f) % tileSpacingPx
    val tileLimitY = (groundY - 1.6f) * meterToPx

    for (tx in -1..(canvasW / tileSpacingPx).toInt() + 2) {
        val x = tx * tileSpacingPx + tileOffsetX
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(x, 0f),
            end = Offset(x, tileLimitY),
            strokeWidth = 2.dp.toPx()
        )
    }
    val tileRowH = 50.dp.toPx()
    var ty = 30.dp.toPx()
    while (ty < tileLimitY) {
        drawLine(
            color = Color.White.copy(alpha = 0.22f),
            start = Offset(0f, ty),
            end = Offset(canvasW, ty),
            strokeWidth = 1.5.dp.toPx()
        )
        for (tx in -1..(canvasW / tileSpacingPx).toInt() + 2) {
            val x = tx * tileSpacingPx + tileOffsetX
            drawCircle(
                color = Color(0xFFFFECB3).copy(alpha = 0.45f),
                radius = 3.dp.toPx(),
                center = Offset(x, ty)
            )
        }
        ty += tileRowH
    }

    // ----------------------------------------------------
    // LAYER 4: Mid-Ground Floating Kitchen Shelves & Decor (0.55x Parallax)
    // ----------------------------------------------------
    val shelfY = 4.7f * meterToPx
    val shelfH = 14.dp.toPx()
    val shelfRepeatW = 320.dp.toPx()
    val shelfOffsetX = -(cameraX * meterToPx * 0.55f) % shelfRepeatW

    // Wooden Shelf Plank
    drawRect(
        color = Color(0xFFBCAAA4),
        topLeft = Offset(0f, shelfY),
        size = Size(canvasW, shelfH)
    )
    drawRect(
        color = Color(0xFF8D6E63),
        topLeft = Offset(0f, shelfY + shelfH - 2.dp.toPx()),
        size = Size(canvasW, 2.dp.toPx())
    )
    drawLine(
        color = ColorInk.copy(alpha = 0.3f),
        start = Offset(0f, shelfY),
        end = Offset(canvasW, shelfY),
        strokeWidth = 2.dp.toPx()
    )

    // Jars and Decor along the shelf
    for (si in -1..(canvasW / shelfRepeatW).toInt() + 2) {
        val sx = si * shelfRepeatW + shelfOffsetX

        // Shelf Bracket
        drawPath(
            path = Path().apply {
                moveTo(sx + 30.dp.toPx(), shelfY + shelfH)
                lineTo(sx + 30.dp.toPx(), shelfY + shelfH + 20.dp.toPx())
                lineTo(sx + 44.dp.toPx(), shelfY + shelfH)
                close()
            },
            color = Color(0xFF6D4C41)
        )

        // Glass Mason Jar with jam
        val jarW = 20.dp.toPx()
        val jarH = 26.dp.toPx()
        val jarX = sx + 50.dp.toPx()
        val jarY = shelfY - jarH
        drawRoundRect(
            color = Color(0xFFEF5350).copy(alpha = 0.75f),
            topLeft = Offset(jarX, jarY + 4.dp.toPx()),
            size = Size(jarW, jarH - 4.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        // Checkered Lid
        drawRoundRect(
            color = Color(0xFFFFCDD2),
            topLeft = Offset(jarX - 2.dp.toPx(), jarY),
            size = Size(jarW + 4.dp.toPx(), 6.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
        drawRect(
            color = ColorInk,
            topLeft = Offset(jarX - 2.dp.toPx(), jarY),
            size = Size(jarW + 4.dp.toPx(), 6.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Spice Shaker
        val spiceW = 14.dp.toPx()
        val spiceH = 20.dp.toPx()
        val spiceX = sx + 80.dp.toPx()
        val spiceY = shelfY - spiceH
        drawRoundRect(
            color = Color(0xFFFFF9C4),
            topLeft = Offset(spiceX, spiceY),
            size = Size(spiceW, spiceH),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )

        // Cute Mini Potted Herb
        val potW = 22.dp.toPx()
        val potH = 18.dp.toPx()
        val potX = sx + 150.dp.toPx()
        val potY = shelfY - potH
        drawRoundRect(
            color = Color(0xFFFF8A65),
            topLeft = Offset(potX, potY),
            size = Size(potW, potH),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
        drawCircle(color = Color(0xFF66BB6A), radius = 7.dp.toPx(), center = Offset(potX + 6.dp.toPx(), potY - 4.dp.toPx()))
        drawCircle(color = Color(0xFF4CAF50), radius = 8.dp.toPx(), center = Offset(potX + 16.dp.toPx(), potY - 5.dp.toPx()))
    }

    // ----------------------------------------------------
    // LAYER 5: Near-Ground Backsplash Mosaic Border (0.75x Parallax)
    // ----------------------------------------------------
    val splashTopY = (groundY - 1.6f) * meterToPx
    val splashBottomY = groundY * meterToPx
    val splashH = splashBottomY - splashTopY
    val subwayW = 54.dp.toPx()
    val subwayH = 18.dp.toPx()
    val splashOffset = -(cameraX * meterToPx * 0.75f) % subwayW

    // Backsplash Base
    drawRect(
        color = Color(0xFFF5F5F5),
        topLeft = Offset(0f, splashTopY),
        size = Size(canvasW, splashH)
    )

    // Staggered subway tile bricks
    var brickRow = 0
    var bY = splashTopY
    while (bY < splashBottomY - 12.dp.toPx()) {
        val rowStagger = if (brickRow % 2 == 0) 0f else subwayW * 0.5f
        for (bx in -1..(canvasW / subwayW).toInt() + 2) {
            val bX = bx * subwayW + splashOffset + rowStagger
            drawRect(
                color = Color(0xFFEEEEEE),
                topLeft = Offset(bX, bY),
                size = Size(subwayW - 2.dp.toPx(), subwayH - 2.dp.toPx())
            )
            drawRect(
                color = Color(0xFFE0E0E0),
                topLeft = Offset(bX, bY),
                size = Size(subwayW - 2.dp.toPx(), subwayH - 2.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
        }
        bY += subwayH
        brickRow++
    }

    // Colorful Mosaic Border Band
    val mosaicH = 10.dp.toPx()
    val mosaicY = splashBottomY - mosaicH
    drawRect(
        color = Color(0xFF26A69A),
        topLeft = Offset(0f, mosaicY),
        size = Size(canvasW, mosaicH)
    )
    val tileMiniW = 12.dp.toPx()
    val miniOffset = -(cameraX * meterToPx * 0.75f) % (tileMiniW * 2)
    for (mi in -1..(canvasW / (tileMiniW * 2)).toInt() + 2) {
        val mx = mi * (tileMiniW * 2) + miniOffset
        drawRect(
            color = Color(0xFFFF7043),
            topLeft = Offset(mx, mosaicY),
            size = Size(tileMiniW, mosaicH)
        )
    }
    drawLine(
        color = ColorInk,
        start = Offset(0f, mosaicY),
        end = Offset(canvasW, mosaicY),
        strokeWidth = 2.dp.toPx()
    )

    // ----------------------------------------------------
    // LAYER 6: Scrolling Floor Countertop (1.0x Parallax - Exact Match with Physics Ground)
    // ----------------------------------------------------
    val groundScreenY = groundY * meterToPx
    val floorHeight = canvasH - groundScreenY + 20.dp.toPx()
    val plankW = 110.dp.toPx()
    val floorOffset = -(cameraX * meterToPx) % plankW

    val floorBaseColor = when (biome) {
        RunnerBiome.ICE_FREEZER -> Color(0xFF80DEEA)
        RunnerBiome.STEAM_CLOUDS -> Color(0xFFEDE7F6)
        RunnerBiome.KITCHEN -> Color(0xFFFFB74D)
        RunnerBiome.CLOUD_KITCHEN -> Color(0xFFFFCCBC)
    }
    val floorAltColor = when (biome) {
        RunnerBiome.ICE_FREEZER -> Color(0xFF4DD0E1).copy(alpha = 0.45f)
        RunnerBiome.STEAM_CLOUDS -> Color(0xFFD1C4E9).copy(alpha = 0.45f)
        RunnerBiome.KITCHEN -> Color(0xFFFFA726).copy(alpha = 0.35f)
        RunnerBiome.CLOUD_KITCHEN -> Color(0xFFFFAB91).copy(alpha = 0.45f)
    }
    val floorSeamColor = when (biome) {
        RunnerBiome.ICE_FREEZER -> Color(0xFF00ACC1)
        RunnerBiome.STEAM_CLOUDS -> Color(0xFFBA68C8)
        RunnerBiome.KITCHEN -> Color(0xFFD87C1E)
        RunnerBiome.CLOUD_KITCHEN -> Color(0xFFD84315)
    }
    val floorTopRimColor = when (biome) {
        RunnerBiome.ICE_FREEZER -> Color(0xFFE0F7FA)
        RunnerBiome.STEAM_CLOUDS -> Color(0xFFFFD54F)
        RunnerBiome.KITCHEN -> Color(0xFFFFF176)
        RunnerBiome.CLOUD_KITCHEN -> Color(0xFFFFF176)
    }

    // Solid Countertop / Ice / Cloud Base
    drawRect(
        color = floorBaseColor,
        topLeft = Offset(0f, groundScreenY),
        size = Size(canvasW, floorHeight)
    )

    // Multi-plank Boards (1.0x Synchronous Scrolling)
    for (pi in -1..(canvasW / plankW).toInt() + 2) {
        val px = pi * plankW + floorOffset
        val plankIndex = ((cameraX * meterToPx / plankW).toInt() + pi)
        val isAlt = Math.floorMod(plankIndex, 2) == 0
        if (isAlt) {
            drawRect(
                color = floorAltColor,
                topLeft = Offset(px, groundScreenY),
                size = Size(plankW, floorHeight)
            )
        }

        // Vertical plank seam groove
        drawLine(
            color = floorSeamColor,
            start = Offset(px, groundScreenY),
            end = Offset(px, canvasH),
            strokeWidth = 3.dp.toPx()
        )
        drawLine(
            color = ColorInk.copy(alpha = 0.25f),
            start = Offset(px + 1.dp.toPx(), groundScreenY),
            end = Offset(px + 1.dp.toPx(), canvasH),
            strokeWidth = 1.dp.toPx()
        )

        if (biome == RunnerBiome.KITCHEN) {
            // Horizontal wavy wood grain stripes
            val grainY1 = groundScreenY + 22.dp.toPx()
            val grainY2 = groundScreenY + 55.dp.toPx()
            val grainY3 = groundScreenY + 95.dp.toPx()
            drawLine(
                color = Color(0xFFF57C00).copy(alpha = 0.25f),
                start = Offset(px + 4.dp.toPx(), grainY1),
                end = Offset(px + plankW - 4.dp.toPx(), grainY1 + 4.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFF57C00).copy(alpha = 0.2f),
                start = Offset(px + 8.dp.toPx(), grainY2),
                end = Offset(px + plankW - 8.dp.toPx(), grainY2 - 3.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFF57C00).copy(alpha = 0.22f),
                start = Offset(px + 6.dp.toPx(), grainY3),
                end = Offset(px + plankW - 6.dp.toPx(), grainY3 + 2.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Wood Knot Graphic on select planks
            if (Math.floorMod(plankIndex, 3) == 0) {
                val knotCenter = Offset(px + plankW * 0.45f, groundScreenY + 40.dp.toPx())
                drawOval(
                    color = Color(0xFFE65100).copy(alpha = 0.3f),
                    topLeft = Offset(knotCenter.x - 12.dp.toPx(), knotCenter.y - 7.dp.toPx()),
                    size = Size(24.dp.toPx(), 14.dp.toPx())
                )
                drawOval(
                    color = Color(0xFFD87C1E).copy(alpha = 0.45f),
                    topLeft = Offset(knotCenter.x - 6.dp.toPx(), knotCenter.y - 4.dp.toPx()),
                    size = Size(12.dp.toPx(), 8.dp.toPx())
                )
            }
        } else if (biome == RunnerBiome.ICE_FREEZER) {
            // Ice crystal glints on floor
            if (Math.floorMod(plankIndex, 2) == 0) {
                drawStarGraphic(Offset(px + plankW * 0.5f, groundScreenY + 25.dp.toPx()), 4.dp.toPx(), Color.White.copy(alpha = 0.8f))
            }
        } else {
            // Steam clouds fluff circles on floor
            if (Math.floorMod(plankIndex, 2) == 0) {
                drawCircle(color = Color(0xFFBA68C8).copy(alpha = 0.25f), radius = 6.dp.toPx(), center = Offset(px + plankW * 0.5f, groundScreenY + 22.dp.toPx()))
            }
        }
    }

    // Top Rim Beveled Highlight and Drop Shadow
    drawRect(
        color = floorTopRimColor,
        topLeft = Offset(0f, groundScreenY),
        size = Size(canvasW, 4.dp.toPx())
    )
    drawRect(
        color = floorSeamColor.copy(alpha = 0.45f),
        topLeft = Offset(0f, groundScreenY + 4.dp.toPx()),
        size = Size(canvasW, 4.dp.toPx())
    )
    drawLine(
        color = ColorInk,
        start = Offset(0f, groundScreenY),
        end = Offset(canvasW, groundScreenY),
        strokeWidth = 3.5.dp.toPx()
    )
}

// ==========================================
// JBOX2D PHYSICS DEBUG OVERLAY
// ==========================================

private fun DrawScope.drawPhysicsDebugOverlay(
    canvasW: Float,
    canvasH: Float,
    meterToPx: Float,
    worldToScreen: (Float, Float) -> Offset,
    physicsEngine: PelmenRunnerPhysicsEngine,
    viewLeftMeters: Float,
    viewRightMeters: Float
) {
    // 1. Semi-transparent dark overlay for high contrast
    drawRect(
        color = Color(0x33000000),
        topLeft = Offset.Zero,
        size = Size(canvasW, canvasH)
    )

    // 2. Iterate and draw outlines for all JBox2D bodies in the world
    val allBodies = physicsEngine.getAllBodies()
    for (body in allBodies) {
        val bPos = body.position
        if (bPos.x < viewLeftMeters - 6f || bPos.x > viewRightMeters + 6f) continue

        val isHero = (body == physicsEngine.heroBody)
        var fixture: Fixture? = body.fixtureList

        while (fixture != null) {
            val isSensor = fixture.isSensor
            val shape = fixture.shape
            val userData = fixture.userData as? RunnerUserData ?: body.userData as? RunnerUserData
            val entityType = userData?.type ?: if (isHero) RunnerEntityType.HERO else RunnerEntityType.GROUND

            val debugColor = when {
                isHero -> Color(0xFF00E676)
                entityType == RunnerEntityType.GROUND -> Color(0xFF00E5FF)
                entityType == RunnerEntityType.PLATFORM -> Color(0xFF00B0FF)
                entityType == RunnerEntityType.TRAMPOLINE -> Color(0xFFFF007F)
                entityType == RunnerEntityType.BUMPER -> Color(0xFFFF6D00)
                entityType == RunnerEntityType.OBSTACLE_BUTTER ||
                entityType == RunnerEntityType.OBSTACLE_HAZARD -> Color(0xFFFF1744)
                isSensor || entityType == RunnerEntityType.COLLECTIBLE_COIN ||
                entityType == RunnerEntityType.COLLECTIBLE_DUMPLING ||
                entityType == RunnerEntityType.BOOSTER_GATE -> Color(0xFFFFD600)
                else -> Color(0xFFEEEEEE)
            }

            if (shape is CircleShape) {
                val radiusPx = shape.m_radius * meterToPx
                val worldCenter = body.getWorldPoint(shape.m_p)
                val centerScreen = worldToScreen(worldCenter.x, worldCenter.y)

                drawCircle(
                    color = debugColor.copy(alpha = if (isHero) 0.25f else 0.20f),
                    radius = radiusPx,
                    center = centerScreen
                )
                drawCircle(
                    color = debugColor,
                    radius = radiusPx,
                    center = centerScreen,
                    style = Stroke(width = if (isHero) 4.dp.toPx() else 2.5.dp.toPx())
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.5.dp.toPx(),
                    center = centerScreen
                )
                val angle = body.angle
                val edgePoint = Offset(
                    centerScreen.x + cos(angle) * radiusPx,
                    centerScreen.y + sin(angle) * radiusPx
                )
                drawLine(
                    color = Color.White,
                    start = centerScreen,
                    end = edgePoint,
                    strokeWidth = 2.dp.toPx()
                )

                if (isHero) {
                    val vel = body.linearVelocity
                    val velScale = meterToPx * 0.12f
                    val arrowEnd = Offset(centerScreen.x + vel.x * velScale, centerScreen.y + vel.y * velScale)
                    drawLine(
                        color = Color(0xFFFFEA00),
                        start = centerScreen,
                        end = arrowEnd,
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = Color(0xFFFFEA00),
                        radius = 5.dp.toPx(),
                        center = arrowEnd
                    )

                    val bottomPoint = Offset(centerScreen.x, centerScreen.y + radiusPx)
                    val rayEnd = Offset(centerScreen.x, centerScreen.y + radiusPx + 14.dp.toPx())
                    drawLine(
                        color = if (physicsEngine.isGrounded) Color(0xFF00E676) else Color(0xFFFF5252),
                        start = bottomPoint,
                        end = rayEnd,
                        strokeWidth = 3.dp.toPx()
                    )
                    drawCircle(
                        color = if (physicsEngine.isGrounded) Color(0xFF00E676) else Color(0xFFFF5252),
                        radius = 4.dp.toPx(),
                        center = rayEnd
                    )
                }
            } else if (shape is PolygonShape) {
                val vertexCount = shape.vertexCount
                if (vertexCount >= 3) {
                    val polyPath = Path()
                    val screenVerts = mutableListOf<Offset>()
                    for (i in 0 until vertexCount) {
                        val wv = body.getWorldPoint(shape.getVertex(i))
                        val sv = worldToScreen(wv.x, wv.y)
                        screenVerts.add(sv)
                        if (i == 0) polyPath.moveTo(sv.x, sv.y) else polyPath.lineTo(sv.x, sv.y)
                    }
                    polyPath.close()

                    drawPath(
                        path = polyPath,
                        color = debugColor.copy(alpha = if (isSensor) 0.15f else 0.22f)
                    )
                    drawPath(
                        path = polyPath,
                        color = debugColor,
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            pathEffect = if (isSensor) PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f) else null
                        )
                    )
                    for (sv in screenVerts) {
                        drawCircle(
                            color = Color.White,
                            radius = 3.5.dp.toPx(),
                            center = sv
                        )
                    }
                }
            }

            fixture = fixture.next
        }
    }

    // 3. Draw Active Contact Points & Collision Normals
    val activeContacts = physicsEngine.getActiveContacts()
    for (c in activeContacts) {
        val cs = worldToScreen(c.point.x, c.point.y)
        val crossR = 7.dp.toPx()
        drawLine(
            color = Color(0xFFFF3D00),
            start = Offset(cs.x - crossR, cs.y),
            end = Offset(cs.x + crossR, cs.y),
            strokeWidth = 3.dp.toPx()
        )
        drawLine(
            color = Color(0xFFFF3D00),
            start = Offset(cs.x, cs.y - crossR),
            end = Offset(cs.x, cs.y + crossR),
            strokeWidth = 3.dp.toPx()
        )
        val normArrow = Offset(cs.x + c.normal.x * 20.dp.toPx(), cs.y + c.normal.y * 20.dp.toPx())
        drawLine(
            color = Color(0xFFFFD600),
            start = cs,
            end = normArrow,
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

// ==========================================
// REAL-TIME PHYSICS TELEMETRY HUD COMPOSABLES
// ==========================================

@Composable
private fun PhysicsDebugHud(
    physicsEngine: PelmenRunnerPhysicsEngine,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val heroPos = physicsEngine.heroBody.position
    val heroVel = physicsEngine.heroBody.linearVelocity
    val bodiesCount = remember(physicsEngine) { physicsEngine.getAllBodies().size }
    val contactsCount = physicsEngine.getActiveContacts().size

    Surface(
        modifier = modifier
            .widthIn(max = 350.dp)
            .testTag("physics_debug_hud"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xEE161B22),
        border = BorderStroke(2.dp, Color(0xFF00E676)),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔬 JBox2D PHYSICS ENGINE",
                    color = Color(0xFF00E676),
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
                Text(
                    text = "60 FPS / 60 Hz",
                    color = ColorWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "📍 Позиция: X=${"%.2f".format(heroPos.x)}м | Y=${"%.2f".format(heroPos.y)}м",
                color = ColorWhite,
                fontSize = 11.sp
            )
            Text(
                text = "⚡ Скорость: Vx=${"%.2f".format(heroVel.x)} м/с | Vy=${"%.2f".format(heroVel.y)} м/с",
                color = Color(0xFFFFD54F),
                fontSize = 11.sp
            )
            Text(
                text = "🎯 Контакт: ${if (physicsEngine.isGrounded) "ЗЕМЛЯ ✅" else "В ПОЛЕТЕ 🦘"} (Касаний: ${physicsEngine.groundContactCount})",
                color = if (physicsEngine.isGrounded) Color(0xFF69F0AE) else Color(0xFFFF8A80),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "🔁 Прыжки: ${physicsEngine.jumpsRemaining}/2 | Турбо: ${if (physicsEngine.isBoosting) "ДА 🚀" else "НЕТ"}",
                color = ColorWhite,
                fontSize = 11.sp
            )
            Text(
                text = "📦 Тела в мире: $bodiesCount | Активных контактов: $contactsCount",
                color = Color(0xFFB0BEC5),
                fontSize = 10.sp
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DebugColorBadge(color = Color(0xFF00E676), label = "Герой")
                DebugColorBadge(color = Color(0xFF00E5FF), label = "Платформа")
                DebugColorBadge(color = Color(0xFFFF007F), label = "Батут")
                DebugColorBadge(color = Color(0xFFFF6D00), label = "Бампер")
                DebugColorBadge(color = Color(0xFFFFD600), label = "Сенсор")
            }
        }
    }
}

@Composable
private fun DebugColorBadge(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = label, color = Color(0xFFCFD8DC), fontSize = 9.sp)
    }
}

/**
 * RunnerGame composable alias for JBox2D runner mode.
 */
@Composable
fun RunnerGame(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    modifier: Modifier = Modifier,
    soundEffectsManager: RunnerSoundEffectsManager? = null
) {
    PelmenRunnerScreen(
        repository = repository,
        soundPlayer = soundPlayer,
        modifier = modifier,
        customSoundManager = soundEffectsManager
    )
}

/**
 * RunnerGame composable alias supporting direct RunnerSoundEffectsManager.
 */
@Composable
fun RunnerGame(
    repository: GameRepository,
    soundEffectsManager: RunnerSoundEffectsManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val soundPlayer = remember { SoundEffectPlayer(context) }
    PelmenRunnerScreen(
        repository = repository,
        soundPlayer = soundPlayer,
        modifier = modifier,
        customSoundManager = soundEffectsManager
    )
}

/**
 * RunnerGameScreen composable alias for JBox2D runner mode.
 */
@Composable
fun RunnerGameScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    modifier: Modifier = Modifier,
    soundEffectsManager: RunnerSoundEffectsManager? = null
) {
    PelmenRunnerScreen(
        repository = repository,
        soundPlayer = soundPlayer,
        modifier = modifier,
        customSoundManager = soundEffectsManager
    )
}
