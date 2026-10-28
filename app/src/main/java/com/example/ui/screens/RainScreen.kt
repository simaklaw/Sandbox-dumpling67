package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.example.shared.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.screens.Rain.GrowingFlower
import com.example.screens.Rain.MeadowAnimal
import com.example.screens.Rain.RainDrop
import com.example.screens.Rain.RainPuddle
import com.example.screens.Rain.RainSoundHelper
import com.example.screens.Rain.RainSplashParticle
import com.example.shared.HintHand
import com.example.shared.Haptics
import com.example.shared.LocalAgeProfile
import com.example.shared.SoundManager
import com.example.shared.rememberHaptics
import com.example.shared.scene.SceneBackground
import com.example.shared.scene.SceneTheme
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ============================================================================
// 🌧️ ДОЖДИК (Шаг 14): тапай / води пальцем по небу — тучка поливает лужайку.
// Лужицы наполняются → появляются лягушка и утёнок, на траве вырастают цветы,
// а когда расцветёт достаточно цветов — выходит радуга.
// Нет проигрыша, нет таймеров — подходит для возраста 2+.
// ============================================================================

private val FLOWER_COLORS = listOf(
    Color(0xFFFF6F61), Color(0xFFFFD23F), Color(0xFFFF7AB6), Color(0xFFA66BFF), Color(0xFF4F7BFF)
)
private val FLOWER_EMOJIS = listOf("🌷", "🌼", "🌸", "🌻")
private val RAINBOW_COLORS = listOf(
    Color(0xFFFF5252), Color(0xFFFFA53F), Color(0xFFFFE23F), Color(0xFF5BDB82),
    Color(0xFF4FC3F7), Color(0xFF5C6BFF), Color(0xFFB36BFF)
)

private const val RAINBOW_SECONDS = 6f
private const val NIGHT_SECONDS = 9f
private const val MAX_FLOWERS = 14

/** Весь изменяемый мир игры. Живёт только в главном потоке (кадровый цикл + жесты). */
private class RainWorld {
    val drops = ArrayList<RainDrop>()
    val puddles = ArrayList<RainPuddle>()
    val flowers = ArrayList<GrowingFlower>()
    val animals = ArrayList<MeadowAnimal>()
    val splashes = ArrayList<RainSplashParticle>()

    var cloudX = 0f
    var cloudY = 90f
    var targetX = 0f
    var touching = false
    var spawnTimer = 0f
    var nextId = 1L
    var rainbowTimer = 0f
    var nightTimer = 0f
    var idleSec = 0f      // сколько секунд никто не трогал экран
    var demoTimer = 0f    // авто-демо: тучка сама поливает
    var bloomedSinceRainbow = 0
    var ready = false

    fun spawnDrop() {
        drops.add(
            RainDrop(
                id = nextId++,
                x = cloudX + Random.nextFloat() * 70f - 35f,
                y = cloudY + 34f,
                vy = 650f + Random.nextFloat() * 120f
            )
        )
    }

    fun spawnSplash(x: Float, y: Float, color: Color, count: Int, cap: Int) {
        repeat(count) {
            if (splashes.size >= cap) return
            splashes.add(
                RainSplashParticle(
                    id = nextId++,
                    x = x,
                    y = y,
                    vx = Random.nextFloat() * 120f - 60f,
                    vy = -(Random.nextFloat() * 160f + 60f),
                    color = color,
                    size = 5f + Random.nextFloat() * 4f
                )
            )
        }
    }

    /** Тап по лягушке/утёнку: прыжок + звук. true — если попали. */
    fun tapAnimal(x: Float, y: Float, sounds: RainSoundHelper, haptics: Haptics): Boolean {
        val hit = animals.firstOrNull { hypot(x - it.xDp, y - (it.yDp - 20f)) < 60f } ?: return false
        if (!hit.isJumping) {
            hit.isJumping = true
            hit.jumpProgress = 0f
        }
        if (hit.soundKey == "frog") sounds.playFrog() else sounds.playDuck()
        haptics.vibrateShort()
        return true
    }

    fun step(
        dt: Float,
        w: Float,
        h: Float,
        speedMul: Float,
        maxParticles: Int,
        toddler: Boolean,
        sounds: RainSoundHelper,
        demoDelaySec: Int = 0
    ) {
        val groundY = h * 0.84f
        cloudY = h * 0.13f
        cloudX += (targetX - cloudX) * min(1f, dt * 7f)

        // Авто-демо (2 года): если 10 с ничего не трогали — тучка сама покачивается и поливает 3 с
        if (demoDelaySec > 0 && !touching) {
            if (demoTimer > 0f) {
                demoTimer -= dt
                targetX = w * (0.5f + 0.3f * sin(demoTimer * 2.2f))
            } else {
                idleSec += dt
                if (idleSec >= demoDelaySec) {
                    idleSec = 0f
                    demoTimer = 3f
                }
            }
        }

        // Непрерывный дождь, пока палец на экране (или идёт демо)
        if (touching || demoTimer > 0f) {
            spawnTimer -= dt
            if (spawnTimer <= 0f) {
                spawnTimer = if (toddler) 0.12f else 0.08f
                spawnDrop()
                sounds.playDropPlip()
            }
        }

        // Капли
        val dropIt = drops.iterator()
        while (dropIt.hasNext()) {
            val d = dropIt.next()
            d.y += d.vy * speedMul * dt
            if (d.y >= groundY) {
                land(d, groundY, h, maxParticles, toddler, sounds)
                dropIt.remove()
            }
        }

        // Лужи: затухание ряби
        for (p in puddles) {
            if (p.ripplePhase > 0f) p.ripplePhase = (p.ripplePhase - dt * 2.2f).coerceAtLeast(0f)
        }

        // Цветы растут
        for (f in flowers) {
            if (!f.isFullyGrown) {
                f.progress = (f.progress + dt / 1.6f).coerceAtMost(1f)
                if (f.progress >= 1f) {
                    f.isFullyGrown = true
                    bloomedSinceRainbow++
                    sounds.playButterfly()
                    val need = if (toddler) 3 else 5
                    if (bloomedSinceRainbow >= need && rainbowTimer <= 0f) {
                        rainbowTimer = RAINBOW_SECONDS
                        bloomedSinceRainbow = 0
                        sounds.playRainbowChime()
                        sounds.playSun()
                    }
                }
            }
        }

        // Радуга: по окончании цветы «уносятся» — лужайка готова к новому циклу
        if (rainbowTimer > 0f) {
            rainbowTimer -= dt
            if (rainbowTimer <= 0f) {
                rainbowTimer = 0f
                flowers.removeAll { it.isFullyGrown }
                // После радуги наступает тихая ночь: звёзды и светлячки
                nightTimer = NIGHT_SECONDS
                sounds.playNight()
            }
        }

        if (nightTimer > 0f) nightTimer = (nightTimer - dt).coerceAtLeast(0f)

        // Прыжки зверушек
        for (a in animals) {
            if (a.isJumping) {
                a.jumpProgress += dt * 2.2f
                if (a.jumpProgress >= 1f) {
                    a.jumpProgress = 0f
                    a.isJumping = false
                    a.rotation = 0f
                } else {
                    a.rotation = sin(a.jumpProgress * 2f * PI.toFloat()) * 14f
                }
            }
        }

        // Брызги
        val sp = splashes.iterator()
        while (sp.hasNext()) {
            val s = sp.next()
            s.ageSec += dt
            s.vy += 900f * dt
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.alpha = (1f - s.ageSec / s.lifeSec).coerceIn(0f, 1f)
            if (s.ageSec >= s.lifeSec) sp.remove()
        }
    }

    private fun land(
        d: RainDrop,
        groundY: Float,
        h: Float,
        maxParticles: Int,
        toddler: Boolean,
        sounds: RainSoundHelper
    ) {
        val puddle = puddles.firstOrNull { abs(it.centerXDp - d.x) < it.widthDp / 2f }
        val cap = maxParticles * 2
        if (puddle != null) {
            puddle.dropCount++
            if (puddle.ripplePhase < 0.4f) sounds.playSplashSoft() // не заваливаем звуком
            puddle.ripplePhase = 1f
            spawnSplash(d.x, groundY, Color(0xFF90CAF9), if (toddler) 3 else 5, cap)

            if (puddle.dropCount >= 6 && !puddle.hasFrog) {
                puddle.hasFrog = true
                animals.add(
                    MeadowAnimal(
                        id = nextId++, emoji = "🐸", soundKey = "frog",
                        xDp = puddle.centerXDp - puddle.widthDp * 0.25f, yDp = groundY
                    )
                )
                sounds.playFrog()
            } else if (puddle.dropCount >= 14 && !puddle.hasDuck) {
                puddle.hasDuck = true
                animals.add(
                    MeadowAnimal(
                        id = nextId++, emoji = "🦆", soundKey = "duck",
                        xDp = puddle.centerXDp + puddle.widthDp * 0.25f, yDp = groundY
                    )
                )
                sounds.playDuck()
            }
        } else {
            spawnSplash(d.x, groundY, Color(0xFF7BD88F), 2, cap)
            // На траве вырастает цветок
            if (Random.nextFloat() < (if (toddler) 0.55f else 0.35f)) {
                if (flowers.size >= MAX_FLOWERS) {
                    flowers.firstOrNull { it.isFullyGrown }?.let { flowers.remove(it) }
                }
                if (flowers.size < MAX_FLOWERS) {
                    flowers.add(
                        GrowingFlower(
                            id = nextId++,
                            xDp = d.x,
                            yDp = h * 0.80f + Random.nextFloat() * h * 0.10f,
                            color = FLOWER_COLORS.random(),
                            iconEmoji = FLOWER_EMOJIS.random()
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun RainScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val dens = LocalDensity.current.density
    val age = LocalAgeProfile.current
    val haptics = rememberHaptics()
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val sounds = remember(soundPlayer) { RainSoundHelper(context, soundPlayer, soundManager) }
    val world = remember { RainWorld() }

    var sizeDp by remember { mutableStateOf(Size.Zero) }
    var frame by remember { mutableLongStateOf(0L) }
    var interacted by remember { mutableStateOf(false) }

    // Кадровый цикл симуляции (в dp-координатах)
    LaunchedEffect(sizeDp, age.id) {
        val w = sizeDp.width
        val h = sizeDp.height
        if (w <= 0f || h <= 0f) return@LaunchedEffect

        if (!world.ready) {
            world.ready = true
            world.cloudX = w / 2f
            world.targetX = w / 2f
            listOf(0.2f, 0.5f, 0.8f).forEachIndexed { i, fx ->
                world.puddles.add(RainPuddle(id = i, centerXDp = w * fx, widthDp = w * 0.24f))
            }
        }

        var last = 0L
        while (isActive) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                world.step(
                    dt = dt, w = w, h = h,
                    speedMul = age.movingSpeedMultiplier.coerceAtLeast(0.6f),
                    maxParticles = age.maxParticles,
                    toddler = age.isToddler2,
                    sounds = sounds,
                    demoDelaySec = age.autoDemoIdleDelaySeconds
                )
                frame++
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { sizeDp = Size(it.width / dens, it.height / dens) }
    ) {
        SceneBackground(theme = SceneTheme.MEADOW)

        // Слой рисования: радуга, лужи, капли, брызги, тучка
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(dens) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val x = down.position.x / dens
                        val y = down.position.y / dens
                        interacted = true
                        world.idleSec = 0f
                        world.demoTimer = 0f
                        if (!world.tapAnimal(x, y, sounds, haptics)) {
                            world.targetX = x
                            world.touching = true
                            world.spawnTimer = 0.12f
                            // сразу капельки — даже при очень быстром тапе
                            repeat(3) { world.spawnDrop() }
                            sounds.playDropPlip()
                            haptics.vibrateWater()
                        }
                        do {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull()
                            if (c != null && c.pressed && world.touching) {
                                world.targetX = c.position.x / dens
                            }
                        } while (event.changes.any { it.pressed })
                        world.touching = false
                    }
                }
        ) {
            @Suppress("UNUSED_VARIABLE")
            val tick = frame // подписка на кадр
            val d = density
            val w = size.width
            val h = size.height

            // 🌙 Ночь: плавно темнеет небо, мерцают звёзды, летают светлячки
            if (world.nightTimer > 0f) {
                val fade = min(1f, min(world.nightTimer, NIGHT_SECONDS - world.nightTimer)).coerceIn(0f, 1f)
                val maxDark = if (age.isToddler2) 0.35f else 0.5f // для малышей — мягче
                drawRect(Color(0xFF1E2A6B).copy(alpha = fade * maxDark), size = Size(w, h))
                for (i in 0 until 16) {
                    val sx = ((i * 7919) % 1000) / 1000f * w
                    val sy = ((i * 3571) % 1000) / 1000f * h * 0.5f
                    val tw = 0.5f + 0.5f * sin(frame * 0.06f + i)
                    drawCircle(Color.White.copy(alpha = fade * tw), radius = (2f + (i % 3)) * d, center = Offset(sx, sy))
                }
                for (i in 0 until 8) {
                    val fx = w * (0.1f + 0.1f * i) + sin(frame * 0.02f + i * 1.7f) * 30f * d
                    val fy = h * (0.62f + 0.04f * (i % 4)) + sin(frame * 0.03f + i) * 20f * d
                    drawCircle(Color(0xFFFFF176).copy(alpha = fade * 0.25f), radius = 14f * d, center = Offset(fx, fy))
                    drawCircle(Color(0xFFFFF176).copy(alpha = fade), radius = 4f * d, center = Offset(fx, fy))
                }
            }

            // 🌈 Радуга
            if (world.rainbowTimer > 0f) {
                val a = (min(world.rainbowTimer, RAINBOW_SECONDS - world.rainbowTimer) * 2f).coerceIn(0f, 1f) * 0.9f
                val cx = w / 2f
                val cy = h * 0.58f
                RAINBOW_COLORS.forEachIndexed { i, col ->
                    val r = w * 0.46f - i * 13f * d
                    drawArc(
                        color = col.copy(alpha = a),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(cx - r, cy - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(width = 13f * d, cap = StrokeCap.Butt)
                    )
                }
            }

            // 💧 Лужи
            for (p in world.puddles) {
                val cx = p.centerXDp * d
                val cy = h * 0.865f
                val pw = p.widthDp * d
                val ph = p.heightDp * d * (1f + 0.25f * (p.dropCount.coerceAtMost(14) / 14f))
                drawOval(Color(0xFF4FA8E8), Offset(cx - pw / 2f, cy - ph / 2f), Size(pw, ph))
                drawOval(
                    Color(0xFFBFE6FF), Offset(cx - pw / 2f + 6f * d, cy - ph / 2f + 3f * d),
                    Size(pw * 0.45f, ph * 0.3f)
                )
                if (p.ripplePhase > 0f) {
                    val rr = (1f - p.ripplePhase) * pw * 0.5f
                    drawOval(
                        Color.White.copy(alpha = p.ripplePhase * 0.8f),
                        Offset(cx - rr, cy - rr * 0.35f), Size(rr * 2f, rr * 0.7f),
                        style = Stroke(width = 2.5f * d)
                    )
                }
            }

            // 💦 Капли
            for (dr in world.drops) {
                val cx = dr.x * d
                val cy = dr.y * d
                drawLine(
                    Color(0xFF5AB4FF), Offset(cx, cy - 16f * d), Offset(cx, cy),
                    strokeWidth = 5f * d, cap = StrokeCap.Round
                )
                drawCircle(Color(0xFF3D9BF0), radius = dr.size * 0.5f * d, center = Offset(cx, cy))
            }

            // ✨ Брызги
            for (s in world.splashes) {
                drawCircle(
                    s.color.copy(alpha = s.alpha), radius = s.size * d * 0.5f,
                    center = Offset(s.x * d, s.y * d)
                )
            }

            // ☁️ Тучка
            val ccx = world.cloudX * d
            val ccy = world.cloudY * d
            val cloudCol = Color(0xFF9BB0C8)
            val bob = sin(frame * 0.05f) * 3f * d
            drawCircle(cloudCol, 34f * d, Offset(ccx - 42f * d, ccy + 10f * d + bob))
            drawCircle(cloudCol, 44f * d, Offset(ccx, ccy - 6f * d + bob))
            drawCircle(cloudCol, 36f * d, Offset(ccx + 44f * d, ccy + 8f * d + bob))
            drawRoundRectCloudBase(ccx, ccy + 22f * d + bob, 130f * d, 32f * d, cloudCol)
            // мордочка
            val eyeCol = Color(0xFF3F4A5C)
            drawCircle(eyeCol, 4.5f * d, Offset(ccx - 14f * d, ccy + 6f * d + bob))
            drawCircle(eyeCol, 4.5f * d, Offset(ccx + 14f * d, ccy + 6f * d + bob))
            drawArc(
                eyeCol, 20f, 140f, false,
                Offset(ccx - 10f * d, ccy + 8f * d + bob), Size(20f * d, 12f * d),
                style = Stroke(width = 3f * d, cap = StrokeCap.Round)
            )
        }

        // 🌸 Цветы и 🐸🦆 зверушки. Список читаем в композиции, а кадр `frame`
        // перекомпонует блок каждый кадр — значения прогресса всегда свежие.
        @Suppress("UNUSED_VARIABLE")
        val composeTick = frame
        for (f in world.flowers) {
            val growth = 0.2f + 0.8f * f.progress
            Text(
                text = f.iconEmoji,
                fontSize = 40.sp,
                modifier = Modifier
                    .offset { IntOffset(((f.xDp - 22f) * dens).toInt(), ((f.yDp - 44f * f.progress) * dens).toInt()) }
                    .graphicsLayer {
                        scaleX = growth
                        scaleY = growth
                    }
            )
        }
        for (a in world.animals) {
            val jump = if (a.isJumping) sin(a.jumpProgress * PI.toFloat()) * 50f else 0f
            val tilt = a.rotation
            Text(
                text = a.emoji,
                fontSize = 46.sp,
                modifier = Modifier
                    .offset { IntOffset(((a.xDp - 26f) * dens).toInt(), ((a.yDp - 52f - jump) * dens).toInt()) }
                    .graphicsLayer { rotationZ = tilt }
            )
        }

        // 👆 Подсказка до первого касания
        if (!interacted && sizeDp.width > 0f) {
            HintHand(
                from = Offset(sizeDp.width / 2f - 40f, sizeDp.height * 0.25f),
                mode = "tap",
                delayMs = age.hintIdleDelaySeconds * 1000L
            )
        }
    }
}

/** Плоское основание тучки, чтобы низ не был «дырявым» между кругами. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundRectCloudBase(
    cx: Float, cy: Float, width: Float, height: Float, color: Color
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(cx - width / 2f, cy - height / 2f),
        size = Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(height / 2f, height / 2f)
    )
}
