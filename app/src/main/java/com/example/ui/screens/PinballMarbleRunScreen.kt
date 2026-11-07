package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.screens.Pinball.PinballPhysicsEngine
import com.example.screens.Pinball.SpeedMode
import com.example.shared.*
import com.example.shared.i18n.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.*
import kotlin.random.Random

// ───────────────────────── палитра стола ─────────────────────────
private val PbNavy = Color(0xFF0B0630)
private val PbDeepA = Color(0xFF140A52)
private val PbDeepB = Color(0xFF2A1580)
private val PbNeonCyan = Color(0xFF4DE8FF)
private val PbNeonPink = Color(0xFFFF4FA3)
private val PbNeonGold = Color(0xFFFFD54A)
private val PbNeonLime = Color(0xFF7CFF8A)
private val PbChrome1 = Color(0xFFF1EEFF)
private val PbChrome2 = Color(0xFF9C8CD8)
private val PbChrome3 = Color(0xFF51408F)

private val PbMilestones = intArrayOf(800, 2500, 6000)
private const val PbStartLives = 3

class PinFlash(val id: Int, val start: Long)
class PinSpark(val x: Float, val y: Float, val vx: Float, val vy: Float, val color: Color)
class PinPopup(val x: Float, val y: Float, val text: String, val color: Color, val start: Long)

@Composable
fun PinballMarbleRunScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val toddler = LocalAgeProfile.current.isToddler2
    val prefs = remember { context.getSharedPreferences("pinball_prefs", Context.MODE_PRIVATE) }

    var score by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(prefs.getInt("best", 0)) }
    var lives by remember { mutableIntStateOf(PbStartLives) }
    var stars by remember { mutableIntStateOf(0) }
    var newRecord by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var multiplierUi by remember { mutableIntStateOf(1) }
    var speedMode by remember { mutableStateOf(SpeedMode.BUNNY) }
    var autoPaws by remember { mutableStateOf(true) }
    var ballColorIndex by remember { mutableIntStateOf(prefs.getInt("ball_color", 0)) }
    var banner by remember { mutableStateOf<String?>(null) }
    var firstHint by remember { mutableStateOf(prefs.getBoolean("hint_v2", true)) }
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }
    var launchPower by remember { mutableFloatStateOf(0f) }
    var launcherReady by remember { mutableStateOf(false) }

    val flashes = remember { mutableStateListOf<PinFlash>() }
    val sparks = remember { mutableStateListOf<PinSpark>() }
    val popups = remember { mutableStateListOf<PinPopup>() }
    val trail = remember { mutableStateListOf<Offset>() }
    val heldSince = remember { longArrayOf(0L) }
    val engineHolder = remember { arrayOfNulls<PinballPhysicsEngine>(1) }

    val ballColors = remember {
        listOf(Color(0xFFFF2A55), Color(0xFFFF8800), Color(0xFFFFD200), Color(0xFF00E676), Color(0xFF2979FF), Color(0xFFD500F9))
    }
    val ballSoundKeys = remember { listOf("rb_red", "rb_orange", "rb_yellow", "rb_green", "rb_blue", "rb_purple") }
    val ballColor = ballColors[ballColorIndex.coerceIn(0, ballColors.lastIndex)]
    val burstColors = remember { listOf(PbNeonPink, PbNeonGold, PbNeonCyan, PbNeonLime) }

    fun burst(x: Float, y: Float, n: Int) {
        repeat(n) {
            sparks.add(PinSpark(x, y, (Random.nextFloat() - 0.5f) * 9f, (Random.nextFloat() - 0.6f) * 9f, burstColors.random()))
        }
    }

    fun popup(x: Float, y: Float, base: Int, col: Color) {
        if (popups.size < 14) popups.add(PinPopup(x, y, "+" + base * multiplierUi, col, System.currentTimeMillis()))
    }

    fun saveBest() { if (score > prefs.getInt("best", 0)) prefs.edit().putInt("best", score).apply() }

    fun addScoreUi(delta: Int) {
        score += delta
        if (score > best) { if (best > 0) newRecord = true; best = score }
        while (stars < PbMilestones.size && score >= PbMilestones[stars]) {
            stars++
            soundManager.play("fanfare_short")
            burst(4.5f, 8f, 26)
            if (!toddler) { lives++; banner = "Звезда! Бонус: лишний шарик" } else banner = "Звезда!"
        }
    }

    val engine = remember {
        PinballPhysicsEngine(
            onScoreAdded = { delta -> addScoreUi(delta) },
            onSoundTriggered = { sfx ->
                soundManager.play(sfx)
                if (sfx == "bumper" || sfx == "sling") haptics.vibrateShort()
            },
            onBumperHit = { id ->
                flashes.add(PinFlash(id, System.currentTimeMillis()))
                engineHolder[0]?.bumperPositions?.getOrNull(id)?.let { popup(it.x, it.y - 0.9f, 10, PbNeonGold) }
            },
            onStarHit = { id ->
                haptics.vibrate(20)
                engineHolder[0]?.starPositions?.getOrNull(id)?.let { popup(it.x, it.y + 0.8f, 20, PbNeonCyan) }
            },
            onAllStarsLit = { burst(4.5f, 3f, 18); banner = "Ролловеры собраны!" }
        )
    }
    engineHolder[0] = engine
    engine.holdOnSpawn = true
    engine.onMultiplier = { m -> multiplierUi = m; soundManager.play("chime"); banner = "Множитель x$m" }
    engine.onGemHit = { i -> engine.gemPositions.getOrNull(i)?.let { popup(if (it.x < 4.5f) it.x + 0.9f else it.x - 0.9f, it.y, 15, PbNeonLime) } }
    engine.onAllGemsLit = { burst(4.5f, 8f, 22); banner = "Все кристаллы! Мультибол" }
    engine.onBallLost = {
        if (!toddler) {
            lives--
            if (lives <= 0) { lives = 0; gameOver = true; saveBest() }
        }
    }

    LaunchedEffect(speedMode) { engine.currentSpeedMode = speedMode }
    LaunchedEffect(autoPaws) { engine.autoPawsEnabled = autoPaws }
    LaunchedEffect(banner) { if (banner != null) { delay(1800); banner = null } }
    DisposableEffect(Unit) { onDispose { saveBest() } }

    fun doLaunch() {
        if (!gameOver && !menuOpen && engine.hasHeldBall()) {
            engine.launchBall(launchPower)
            soundPlayer.playPinballPlungerRelease()
            haptics.vibrateShort()
            heldSince[0] = 0L
        }
    }

    fun newGame() {
        saveBest()
        score = 0; lives = PbStartLives; stars = 0; multiplierUi = 1
        gameOver = false; newRecord = false; banner = null
        trail.clear(); popups.clear(); sparks.clear()
        engine.clearAllAndReset()
    }

    // игровой цикл: физика + пушка
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { ns ->
                if (!menuOpen && !gameOver) engine.step(ns)
                val nowMs = System.currentTimeMillis()
                val held = engine.hasHeldBall() && !gameOver && !menuOpen
                launcherReady = held
                if (held) {
                    if (heldSince[0] == 0L) heldSince[0] = nowMs
                    val el = (nowMs - heldSince[0]) / 1000f
                    launchPower = 0.5f + 0.5f * sin(el * 3.4f - 1.5708f)
                    if (nowMs - heldSince[0] > (if (toddler) 1300L else 6000L)) doLaunch()
                } else heldSince[0] = 0L
                val balls = engine.getBalls().filter { !it.held }
                if (balls.isNotEmpty()) {
                    balls.forEach { trail.add(Offset(it.px, it.py)) }
                    while (trail.size > 22 * balls.size) trail.removeAt(0)
                } else if (trail.isNotEmpty()) trail.clear()
            }
        }
    }

    // частицы, вспышки, всплывающие очки
    LaunchedEffect(flashes.size, sparks.size, popups.size) {
        while (isActive && (flashes.isNotEmpty() || sparks.isNotEmpty() || popups.isNotEmpty())) {
            delay(30)
            val now = System.currentTimeMillis()
            flashes.removeAll { now - it.start > 280L }
            popups.removeAll { now - it.start > 950L }
            for (i in sparks.indices.reversed()) {
                val p = sparks.getOrNull(i) ?: continue
                val nx = p.x + p.vx * 0.03f
                val ny = p.y + p.vy * 0.03f
                if (nx < 0f || nx > 9f || ny < 0f || ny > 17f) sparks.removeAt(i)
                else sparks[i] = PinSpark(nx, ny, p.vx * 0.985f, p.vy * 0.985f + 0.15f, p.color)
            }
        }
    }

    fun pressPad(left: Boolean, down: Boolean) {
        if (down) {
            if (firstHint) { firstHint = false; prefs.edit().putBoolean("hint_v2", false).apply() }
            doLaunch()
        }
        if (left) { leftPressed = down; engine.holdLeft(down) } else { rightPressed = down; engine.holdRight(down) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF05021A), Color(0xFF120A45), Color(0xFF05021A))))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PinHud(
                score = score, best = best, lives = lives, toddler = toddler,
                stars = stars, multiplier = multiplierUi, ballColor = ballColor,
                onMenu = { menuOpen = true; soundManager.play("tap") }
            )
            Spacer(Modifier.height(6.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PinballCanvas(
                    engine = engine, flashes = flashes, sparks = sparks, popups = popups,
                    leftActive = leftPressed, rightActive = rightPressed,
                    ballColor = ballColor, trail = trail,
                    launcherReady = launcherReady, launchPower = launchPower,
                    modifier = Modifier.fillMaxSize()
                )

                // зоны лапок: левая и правая половина поля, мультитач
                Row(Modifier.fillMaxSize()) {
                    for (isLeft in listOf(true, false)) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pointerInput(isLeft) {
                                    detectTapGestures(onPress = {
                                        pressPad(isLeft, true)
                                        tryAwaitRelease()
                                        pressPad(isLeft, false)
                                    })
                                }
                        )
                    }
                }

                if (firstHint) {
                    Text(
                        text = "Касайся левой и правой половины поля",
                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 90.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
                banner?.let {
                    Text(
                        text = it, color = PbNavy, fontSize = 17.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Brush.horizontalGradient(listOf(PbNeonGold, Color(0xFFFFF59D), PbNeonGold)), RoundedCornerShape(18.dp))
                            .border(2.dp, PbNavy, RoundedCornerShape(18.dp))
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            // нижние кнопки: левая лапка, пуск, правая лапка
            Row(
                modifier = Modifier.fillMaxWidth().height(86.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PinFlipperPad(left = true, pressed = leftPressed, modifier = Modifier.weight(1f).fillMaxHeight()) { pressPad(true, it) }
                PinLaunchButton(ready = launcherReady, power = launchPower) { doLaunch() }
                PinFlipperPad(left = false, pressed = rightPressed, modifier = Modifier.weight(1f).fillMaxHeight()) { pressPad(false, it) }
            }
        }

        if (menuOpen) {
            PinOverlay {
                Text("Меню", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(14.dp))
                PinSectionTitle("Скорость")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PinChip("Черепаха", speedMode == SpeedMode.TURTLE) { speedMode = SpeedMode.TURTLE; soundManager.play("tap") }
                    PinChip("Заяц", speedMode == SpeedMode.BUNNY) { speedMode = SpeedMode.BUNNY; soundManager.play("tap") }
                    PinChip("Ракета", speedMode == SpeedMode.ROCKET) { speedMode = SpeedMode.ROCKET; soundManager.play("tap") }
                }
                Spacer(Modifier.height(14.dp))
                PinSectionTitle("Помощь лапок")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PinChip("Авто", autoPaws) { autoPaws = true; soundManager.play("tap") }
                    PinChip("Сам", !autoPaws) { autoPaws = false; soundManager.play("tap") }
                }
                Spacer(Modifier.height(14.dp))
                PinSectionTitle("Цвет шарика")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ballColors.forEachIndexed { i, c ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(c, CircleShape)
                                .border(if (i == ballColorIndex) 4.dp else 1.5.dp, if (i == ballColorIndex) Color.White else Color.White.copy(alpha = 0.4f), CircleShape)
                                .pointerInput(i) {
                                    detectTapGestures(onTap = {
                                        ballColorIndex = i
                                        prefs.edit().putInt("ball_color", i).apply()
                                        if (!soundManager.play(ballSoundKeys[i])) soundManager.play("pop")
                                        haptics.vibrateShort()
                                    })
                                }
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                PinBigButton("Продолжить", PbNeonLime) { menuOpen = false; soundManager.play("tap") }
                Spacer(Modifier.height(10.dp))
                PinBigButton("Новая игра", PbNeonPink) { menuOpen = false; newGame(); soundManager.play("whoosh") }
            }
        }

        if (gameOver) {
            PinOverlay {
                Text("Игра окончена", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0 until 3) PinIcon(2, if (i < stars) PbNeonGold else Color.White.copy(alpha = 0.22f), Modifier.size(52.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text("%06d".format(score), color = PbNeonCyan, fontSize = 44.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black,
                    style = TextStyle(shadow = Shadow(PbNeonCyan, Offset.Zero, 24f)))
                if (newRecord) {
                    Spacer(Modifier.height(6.dp))
                    Text("Новый рекорд!", color = PbNeonGold, fontSize = 20.sp, fontWeight = FontWeight.Black)
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text("Рекорд: $best", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
                }
                Spacer(Modifier.height(22.dp))
                PinBigButton("Играть ещё", PbNeonLime) { newGame(); soundManager.play("whoosh") }
            }
        }
    }
}

// ───────────────────────── интерфейс ─────────────────────────

@Composable
private fun PinHud(
    score: Int, best: Int, lives: Int, toddler: Boolean, stars: Int, multiplier: Int,
    ballColor: Color, onMenu: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier.fillMaxWidth().height(86.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(58.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF3A2A8C), Color(0xFF1B1150))), RoundedCornerShape(18.dp))
                .border(2.dp, PbNeonCyan.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = { onMenu() }) }
                .testTag("pinball_menu_btn"),
            contentAlignment = Alignment.Center
        ) { PinIcon(0, PbNeonCyan, Modifier.size(28.dp)) }

        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(Color(0xFF0A0535), Color(0xFF1A0E58))), shape)
                .border(2.dp, Brush.horizontalGradient(listOf(PbNeonPink, PbNeonCyan)), shape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("ОЧКИ", color = PbNeonCyan.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(
                        "%06d".format(score), color = PbNeonCyan, fontSize = 30.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black,
                        style = TextStyle(shadow = Shadow(PbNeonCyan, Offset.Zero, 22f))
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (toddler) {
                            Text("∞", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            PinIcon(1, ballColor, Modifier.size(16.dp))
                        } else {
                            for (i in 0 until min(lives, 5)) PinIcon(1, ballColor, Modifier.size(16.dp))
                            if (lives > 5) Text("+${lives - 5}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("РЕКОРД $best", color = PbNeonGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (multiplier > 1) Text("x$multiplier", color = PbNeonPink, fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
            }
            // прогресс до следующей звезды
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val prev = if (stars == 0) 0 else PbMilestones[stars - 1]
                val next = if (stars >= PbMilestones.size) PbMilestones.last() else PbMilestones[stars]
                val frac = if (stars >= PbMilestones.size) 1f else ((score - prev).toFloat() / (next - prev)).coerceIn(0f, 1f)
                Box(
                    Modifier.weight(1f).height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Brush.horizontalGradient(listOf(PbNeonPink, PbNeonGold)), CircleShape))
                }
                for (i in 0 until 3) PinIcon(2, if (i < stars) PbNeonGold else Color.White.copy(alpha = 0.22f), Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PinFlipperPad(left: Boolean, pressed: Boolean, modifier: Modifier, onPress: (Boolean) -> Unit) {
    val base = if (left) PbNeonPink else PbNeonCyan
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    if (pressed) listOf(Color.White, base) else listOf(base.copy(alpha = 0.55f), base.copy(alpha = 0.18f))
                ), shape
            )
            .border(3.dp, base, shape)
            .pointerInput(left) {
                detectTapGestures(onPress = { onPress(true); tryAwaitRelease(); onPress(false) })
            }
            .testTag(if (left) "pinball_left_flipper_btn" else "pinball_right_flipper_btn"),
        contentAlignment = Alignment.Center
    ) {
        PinIcon(if (left) 3 else 4, if (pressed) PbNavy else Color.White, Modifier.size(46.dp))
    }
}

@Composable
private fun PinLaunchButton(ready: Boolean, power: Float, onLaunch: () -> Unit) {
    Box(
        Modifier
            .size(82.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { onLaunch() }) }
            .testTag("pinball_launch_btn"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f - 6f
            drawCircle(Color.White.copy(alpha = 0.08f), r, c)
            drawCircle(if (ready) PbNeonGold.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.2f), r, c, style = Stroke(4f))
            if (ready) {
                drawArc(
                    Brush.sweepGradient(listOf(PbNeonLime, PbNeonGold, PbNeonPink, PbNeonLime), c), -90f, 360f * power, false,
                    Offset(c.x - r + 9f, c.y - r + 9f), Size((r - 9f) * 2f, (r - 9f) * 2f), style = Stroke(9f, cap = StrokeCap.Round)
                )
            }
        }
        PinIcon(5, if (ready) PbNeonGold else Color.White.copy(alpha = 0.3f), Modifier.size(34.dp))
    }
}

@Composable
private fun PinOverlay(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF241470), Color(0xFF0E0838))), RoundedCornerShape(28.dp))
                .border(3.dp, Brush.horizontalGradient(listOf(PbNeonPink, PbNeonCyan)), RoundedCornerShape(28.dp))
                .padding(horizontal = 22.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun PinSectionTitle(t: String) {
    Text(t, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun PinChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .background(if (selected) PbNeonGold else Color.White.copy(alpha = 0.10f), shape)
            .border(2.dp, if (selected) Color.White else Color.White.copy(alpha = 0.3f), shape)
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) PbNavy else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PinBigButton(label: String, col: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(Brush.verticalGradient(listOf(Color.White, col)), shape)
            .border(3.dp, PbNavy, shape)
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = PbNavy, fontSize = 19.sp, fontWeight = FontWeight.Black)
    }
}

private fun pinStar(c: Offset, r: Float): Path = Path().apply {
    for (k in 0 until 10) {
        val a = -PI.toFloat() / 2f + k * PI.toFloat() / 5f
        val rr = if (k % 2 == 0) r else r * 0.45f
        val x = c.x + cos(a) * rr
        val y = c.y + sin(a) * rr
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Векторные значки: 0 меню, 1 шарик, 2 звезда, 3/4 стрелки лапок, 5 пуск (стрелка вниз). */
@Composable
private fun PinIcon(kind: Int, tint: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val c = Offset(w / 2f, h / 2f)
        when (kind) {
            0 -> for (i in 0 until 3) {
                val y = h * (0.22f + i * 0.28f)
                drawLine(tint, Offset(w * 0.12f, y), Offset(w * 0.88f, y), h * 0.12f, StrokeCap.Round)
            }
            1 -> {
                drawCircle(Brush.radialGradient(listOf(Color.White, tint, tint.copy(alpha = 0.55f)), Offset(c.x - w * 0.15f, c.y - h * 0.18f), w * 0.75f), w * 0.46f, c)
                drawCircle(PbNavy, w * 0.46f, c, style = Stroke(w * 0.07f))
            }
            2 -> {
                val p = pinStar(c, w * 0.5f)
                drawPath(p, tint)
                drawPath(p, Color.White.copy(alpha = 0.55f), style = Stroke(w * 0.05f, join = StrokeJoin.Round))
            }
            3, 4 -> {
                val d = if (kind == 3) -1f else 1f
                val p = Path().apply {
                    moveTo(c.x - d * w * 0.1f, h * 0.18f)
                    lineTo(c.x + d * w * 0.3f, c.y)
                    lineTo(c.x - d * w * 0.1f, h * 0.82f)
                }
                drawPath(p, tint, style = Stroke(w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                val p2 = Path().apply {
                    moveTo(c.x - d * w * 0.42f, h * 0.18f)
                    lineTo(c.x - d * w * 0.02f, c.y)
                    lineTo(c.x - d * w * 0.42f, h * 0.82f)
                }
                drawPath(p2, tint.copy(alpha = 0.5f), style = Stroke(w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            else -> {
                drawLine(tint, Offset(c.x, h * 0.12f), Offset(c.x, h * 0.78f), w * 0.14f, StrokeCap.Round)
                val p = Path().apply {
                    moveTo(w * 0.2f, h * 0.55f); lineTo(c.x, h * 0.88f); lineTo(w * 0.8f, h * 0.55f)
                }
                drawPath(p, tint, style = Stroke(w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

// ───────────────────────── поле ─────────────────────────

@Composable
fun PinballCanvas(
    engine: PinballPhysicsEngine,
    flashes: List<PinFlash>,
    sparks: List<PinSpark>,
    popups: List<PinPopup>,
    leftActive: Boolean,
    rightActive: Boolean,
    ballColor: Color,
    trail: List<Offset>,
    launcherReady: Boolean,
    launchPower: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val worldW = engine.worldWidth
        val worldH = engine.worldHeight
        val scale = min(size.width / (worldW + 0.9f), size.height / (worldH + 0.7f))
        val offsetX = (size.width - worldW * scale) / 2f
        val offsetY = (size.height - worldH * scale) / 2f
        fun px(x: Float) = offsetX + x * scale
        fun py(y: Float) = offsetY + y * scale
        fun pd(d: Float) = d * scale
        fun pt(x: Float, y: Float) = Offset(px(x), py(y))
        val now = System.currentTimeMillis()
        val tSec = (now % 100000L) / 1000f
        fun hash(k: Int): Float { val v = sin(k * 12.9898f) * 43758.547f; return v - floor(v) }

        // ── корпус ──
        val cabTL = Offset(px(0f) - pd(0.62f), py(0f) - pd(0.5f))
        val cabSize = Size(pd(worldW + 1.24f), pd(worldH + 0.75f))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF6A4FD0), Color(0xFF2B1A78), Color(0xFF150C48)), cabTL.y, cabTL.y + cabSize.height), cabTL, cabSize, CornerRadius(pd(2.3f)))
        drawRoundRect(Color.White.copy(alpha = 0.45f), cabTL, cabSize, CornerRadius(pd(2.3f)), style = Stroke(pd(0.05f)))
        drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(cabTL.x + pd(0.2f), cabTL.y + pd(0.2f)), Size(cabSize.width - pd(0.4f), cabSize.height - pd(0.4f)), CornerRadius(pd(2.1f)), style = Stroke(pd(0.07f)))
        for (sx in listOf(cabTL.x + pd(0.32f), cabTL.x + cabSize.width - pd(0.32f))) for (sy in listOf(cabTL.y + pd(1.2f), cabTL.y + cabSize.height * 0.5f)) {
            drawCircle(PbChrome2, pd(0.07f), Offset(sx, sy)); drawCircle(Color.White.copy(alpha = 0.7f), pd(0.025f), Offset(sx - pd(0.02f), sy - pd(0.02f)))
        }

        val fieldPath = Path().apply {
            moveTo(px(0f), py(13f)); lineTo(px(0f), py(4.5f))
            arcTo(androidx.compose.ui.geometry.Rect(px(0f), py(0f), px(9f), py(9f)), 180f, 180f, false)
            lineTo(px(9f), py(13f)); lineTo(px(engine.rightPivot.x), py(engine.rightPivot.y))
            lineTo(px(engine.rightPivot.x), py(18f)); lineTo(px(engine.leftPivot.x), py(18f))
            lineTo(px(engine.leftPivot.x), py(engine.leftPivot.y)); close()
        }
        drawPath(fieldPath, Brush.verticalGradient(listOf(Color(0xFF0C0640), Color(0xFF211070), Color(0xFF3A1B94)), py(0f), py(18f)))
        clipPath(fieldPath) {
            // планета с кольцом за воронкой
            val pc = pt(4.5f, 9.0f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF7E57C2).copy(alpha = 0.55f), Color(0xFF311B92).copy(alpha = 0.15f)), Offset(pc.x - pd(0.5f), pc.y - pd(0.5f)), pd(2.4f)), pd(1.9f), pc)
            drawOval(Color(0xFFFFD54F).copy(alpha = 0.22f), Offset(pc.x - pd(3.0f), pc.y - pd(0.6f)), Size(pd(6f), pd(1.2f)), style = Stroke(pd(0.12f)))
            // кольца света
            for ((i, rr) in listOf(3.6f, 2.6f, 1.6f).withIndex()) {
                drawCircle(listOf(PbNeonPink, PbNeonCyan, PbNeonGold)[i].copy(alpha = 0.10f), pd(rr), pt(4.5f, 6.4f), style = Stroke(pd(0.08f)))
            }
            // мерцающие звёзды
            for (k in 0 until 90) {
                val x = hash(k * 3 + 1) * worldW; val y = hash(k * 3 + 2) * 17f
                val tw = 0.5f + 0.5f * sin(tSec * 2f + k)
                drawCircle(Color.White.copy(alpha = 0.08f + 0.4f * tw * hash(k)), pd(0.02f + hash(k * 5) * 0.035f), pt(x, y))
            }
            // лампочки по арке (бегущий огонь)
            val nLamp = 15
            val lit = (tSec * 7f).toInt() % nLamp
            for (i in 0 until nLamp) {
                val a = PI.toFloat() + (i + 0.5f) * PI.toFloat() / nLamp
                val lc = pt(4.5f + 4.15f * cos(a), 4.5f + 4.15f * sin(a))
                val d = (i - lit + nLamp) % nLamp
                val glow = if (d == 0) 1f else if (d == 1) 0.5f else if (d == 2) 0.25f else 0.10f
                val col = if (i % 2 == 0) PbNeonGold else PbNeonPink
                if (glow > 0.2f) drawCircle(col.copy(alpha = glow * 0.35f), pd(0.26f), lc)
                drawCircle(col.copy(alpha = glow), pd(0.085f), lc)
            }
            // лампы множителя x2..x5
            for (m in 2..5) {
                val on = engine.multiplier >= m
                val cx = 3.0f + (m - 2) * 1.0f
                val rr = androidx.compose.ui.geometry.Rect(px(cx - 0.36f), py(11.9f), px(cx + 0.36f), py(12.35f))
                val col = if (on) PbNeonLime else Color(0xFF3B2C86)
                if (on) drawRoundRect(PbNeonLime.copy(alpha = 0.35f), Offset(rr.left - 5f, rr.top - 5f), Size(rr.width + 10f, rr.height + 10f), CornerRadius(pd(0.3f)))
                drawRoundRect(col, rr.topLeft, rr.size, CornerRadius(pd(0.2f)))
                drawRoundRect(Color.White.copy(alpha = if (on) 0.7f else 0.18f), rr.topLeft, rr.size, CornerRadius(pd(0.2f)), style = Stroke(pd(0.03f)))
            }
            // стрелки-лыжи к лапкам
            for (side in 0..1) for (k in 0 until 4) {
                val ax = if (side == 0) 0.55f + k * 0.22f else 8.45f - k * 0.22f
                val ay = 13.0f + k * 0.62f
                val on = ((tSec * 3f).toInt() % 4 == k)
                drawCircle(PbNeonGold.copy(alpha = if (on) 1f else 0.2f), pd(0.09f), pt(ax, ay))
            }
            drawRect(Color.Black.copy(alpha = 0.6f), pt(engine.leftPivot.x, 16.9f), Size(pd(engine.rightPivot.x - engine.leftPivot.x), pd(1.2f)))
        }

        // ── хромированные стенки ──
        fun rail(path: Path) {
            drawPath(path, PbNavy, style = Stroke(pd(0.52f), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, Brush.linearGradient(listOf(PbChrome1, PbChrome2, PbChrome3, PbChrome2), Offset(0f, offsetY), Offset(size.width, offsetY + pd(worldH))), style = Stroke(pd(0.36f), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, Color.White.copy(alpha = 0.6f), style = Stroke(pd(0.07f), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        rail(Path().apply {
            moveTo(px(-0.2f), py(13f)); lineTo(px(-0.2f), py(4.5f))
            arcTo(androidx.compose.ui.geometry.Rect(px(-0.2f), py(-0.2f), px(9.2f), py(9.2f)), 180f, 180f, false)
            lineTo(px(9.2f), py(13f))
        })
        rail(Path().apply { moveTo(px(0f), py(13f)); lineTo(px(engine.leftPivot.x), py(engine.leftPivot.y)) })
        rail(Path().apply { moveTo(px(9f), py(13f)); lineTo(px(engine.rightPivot.x), py(engine.rightPivot.y)) })

        // ── пушка-стартер ──
        run {
            val lp = engine.launcherPos
            val c = pt(lp.x, lp.y)
            val rr = pd(0.62f)
            drawCircle(Color.Black.copy(alpha = 0.45f), rr * 1.25f, c)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF2B1A78), PbNavy), c, rr), rr, c)
            drawCircle(if (launcherReady) PbNeonGold else PbChrome2, rr, c, style = Stroke(pd(0.09f)))
            if (launcherReady) {
                drawArc(PbNeonLime, -90f, 360f * launchPower, false, Offset(c.x - rr * 1.3f, c.y - rr * 1.3f), Size(rr * 2.6f, rr * 2.6f), style = Stroke(pd(0.11f), cap = StrokeCap.Round))
            }
        }

        // ── ролловеры-звёзды ──
        engine.starPositions.forEachIndexed { idx, p ->
            val lit = engine.starLitStates[idx]
            val c = pt(p.x, p.y)
            val rr = pd(0.42f)
            if (lit) drawCircle(PbNeonGold.copy(alpha = 0.35f), rr * 2f, c)
            val star = pinStar(c, rr)
            drawPath(star, if (lit) PbNeonGold else Color(0xFF3B2C86))
            drawPath(star, if (lit) Color(0xFFFF8F00) else PbChrome2, style = Stroke(pd(0.05f), join = StrokeJoin.Round))
        }

        // ── спиннер ──
        run {
            val c = pt(engine.spinnerPos.x, engine.spinnerPos.y)
            drawCircle(Color.Black.copy(alpha = 0.4f), pd(0.55f), c)
            drawCircle(PbNeonCyan.copy(alpha = 0.6f), pd(0.55f), c, style = Stroke(pd(0.05f)))
            val a = engine.spinAngle
            val half = pd(0.46f)
            drawLine(Color(0xFFFF7043), Offset(c.x - half * cos(a), c.y - half * 0.45f * sin(a)), Offset(c.x + half * cos(a), c.y + half * 0.45f * sin(a)), pd(0.16f), StrokeCap.Round)
            drawCircle(Color.White, pd(0.08f), c)
        }

        // ── воронка ──
        run {
            val c = pt(engine.saucerPos.x, engine.saucerPos.y)
            val rr = pd(engine.saucerR + 0.18f)
            val fl = engine.saucerFlash
            drawCircle(Color(0xFF7C4DFF).copy(alpha = 0.3f + 0.4f * fl), rr * 1.6f, c)
            drawCircle(Brush.radialGradient(listOf(Color.Black, PbNavy), c, rr), rr, c)
            for (k in 0 until 3) {
                val a0 = tSec * 4f + k * 2.094f
                drawArc(Color(0xFFB388FF).copy(alpha = 0.85f), Math.toDegrees(a0.toDouble()).toFloat(), 80f, false, Offset(c.x - rr * 0.8f, c.y - rr * 0.8f), Size(rr * 1.6f, rr * 1.6f), style = Stroke(pd(0.06f), cap = StrokeCap.Round))
                drawArc(PbNeonCyan.copy(alpha = 0.7f), Math.toDegrees((a0 * 1.6f).toDouble()).toFloat() + 40f, 60f, false, Offset(c.x - rr * 0.5f, c.y - rr * 0.5f), Size(rr, rr), style = Stroke(pd(0.05f), cap = StrokeCap.Round))
            }
            drawCircle(PbChrome1, rr, c, style = Stroke(pd(0.09f)))
        }

        // ── кристаллы ──
        engine.gemPositions.forEachIndexed { idx, p ->
            val c = pt(p.x, p.y)
            val lit = engine.gemLit[idx]
            val fl = engine.gemFlash[idx]
            val r = pd(engine.gemR) * (1f + 0.25f * fl)
            if (lit) drawCircle(PbNeonGold.copy(alpha = 0.35f + 0.3f * fl), r * 1.9f, c)
            val gem = Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r * 0.9f, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r * 0.9f, c.y); close() }
            drawPath(gem, if (lit) Brush.linearGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFB300)), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r))
                else Brush.linearGradient(listOf(Color(0xFF80DEEA), Color(0xFF0097A7)), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)))
            drawPath(gem, Color.White.copy(alpha = 0.85f), style = Stroke(pd(0.04f), join = StrokeJoin.Round))
            drawLine(Color.White.copy(alpha = 0.7f), Offset(c.x - r * 0.4f, c.y - r * 0.25f), Offset(c.x, c.y - r * 0.7f), pd(0.04f), StrokeCap.Round)
        }

        // ── рогатки ──
        for (side in 0..1) {
            val mx = { x: Float -> if (side == 0) x else 9f - x }
            val tri = Path().apply { moveTo(px(mx(1.15f)), py(10.6f)); lineTo(px(mx(2.25f)), py(13.35f)); lineTo(px(mx(1.15f)), py(12.6f)); close() }
            val fl = engine.slingFlash[side]
            drawPath(tri, Brush.linearGradient(listOf(Color(0xFFFFE082), PbNeonPink), pt(mx(1.15f), 10.6f), pt(mx(2.25f), 13.35f)))
            drawPath(tri, PbNavy, style = Stroke(pd(0.12f), join = StrokeJoin.Round))
            drawLine(Color.White.copy(alpha = 0.4f + 0.6f * fl), pt(mx(1.3f), 10.9f), pt(mx(2.15f), 13.0f), pd(0.09f + 0.07f * fl), StrokeCap.Round)
            if (fl > 0f) drawLine(Color(0xFFFFFF8D).copy(alpha = fl), pt(mx(1.15f), 10.6f), pt(mx(2.25f), 13.35f), pd(0.18f), StrokeCap.Round)
        }

        // ── бамперы ──
        val bumperCols = listOf(PbNeonPink, PbNeonGold, PbNeonCyan, PbNeonLime)
        engine.bumperPositions.forEachIndexed { idx, p ->
            val c = pt(p.x, p.y)
            val fl = engine.bumperFlash[idx]
            val bR = pd(engine.bumperR) * (1f + 0.1f * fl)
            val col = bumperCols[idx % bumperCols.size]
            drawCircle(Color.Black.copy(alpha = 0.45f), bR * 1.15f, Offset(c.x + pd(0.08f), c.y + pd(0.14f)))
            drawCircle(col.copy(alpha = 0.18f + 0.5f * fl), bR * 1.55f, c)
            drawCircle(PbNavy, bR * 1.14f, c)
            for (k in 0 until 10) {
                val a = k * 2f * PI.toFloat() / 10f
                val on = ((tSec * 6f).toInt() + k) % 3 == 0 || fl > 0.2f
                drawCircle(if (on) Color.White else col.copy(alpha = 0.35f), bR * 0.06f, Offset(c.x + cos(a) * bR * 1.0f, c.y + sin(a) * bR * 1.0f))
            }
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), col, Color(col.red * 0.5f, col.green * 0.5f, col.blue * 0.5f)), Offset(c.x - bR * 0.3f, c.y - bR * 0.35f), bR * 1.2f), bR * 0.82f, c)
            drawCircle(Color.White.copy(alpha = 0.55f), bR * 0.82f, c, style = Stroke(pd(0.04f)))
            drawPath(pinStar(c, bR * 0.45f), PbNavy.copy(alpha = 0.55f + 0.4f * fl))
            drawOval(Color.White.copy(alpha = 0.45f), Offset(c.x - bR * 0.55f, c.y - bR * 0.72f), Size(bR * 0.6f, bR * 0.26f))
            if (fl > 0f) drawCircle(Color.White.copy(alpha = fl), bR * 1.35f, c, style = Stroke(pd(0.08f) * fl))
        }

        // ── лапки ──
        val (leftAngle, rightAngle) = engine.getFlipperAngles()
        val flipLen = pd(engine.flipLen)
        val r1 = pd(engine.flipHalf)
        val r0 = r1 * 1.4f
        for (left in listOf(true, false)) {
            val pv = if (left) pt(engine.leftPivot.x, engine.leftPivot.y) else pt(engine.rightPivot.x, engine.rightPivot.y)
            val tip = if (left) Offset(pv.x + flipLen * cos(leftAngle), pv.y + flipLen * sin(leftAngle))
                      else Offset(pv.x - flipLen * cos(rightAngle), pv.y - flipLen * sin(rightAngle))
            val active = if (left) leftActive else rightActive
            val dx = (tip.x - pv.x) / flipLen; val dy = (tip.y - pv.y) / flipLen
            val nx = -dy; val ny = dx
            val body = Path().apply {
                moveTo(pv.x + nx * r0, pv.y + ny * r0); lineTo(tip.x + nx * r1, tip.y + ny * r1)
                lineTo(tip.x - nx * r1, tip.y - ny * r1); lineTo(pv.x - nx * r0, pv.y - ny * r0); close()
            }
            val main = if (left) PbNeonPink else PbNeonCyan
            // тень
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(pv.x + 4f, pv.y + 7f), Offset(tip.x + 4f, tip.y + 7f), r1 * 2.1f, StrokeCap.Round)
            // контур и тело
            drawLine(PbNavy, pv, tip, r0 * 2.25f, StrokeCap.Round)
            drawCircle(PbNavy, r0 * 1.12f, pv); drawCircle(PbNavy, r1 * 1.12f, tip)
            val fill = Brush.linearGradient(
                if (active) listOf(Color.White, PbNeonGold) else listOf(Color.White.copy(alpha = 0.9f), main, Color(main.red * 0.55f, main.green * 0.55f, main.blue * 0.55f)),
                Offset(pv.x + nx * r0, pv.y + ny * r0), Offset(pv.x - nx * r0, pv.y - ny * r0)
            )
            drawPath(body, fill)
            drawCircle(fill, r0, pv); drawCircle(fill, r1, tip)
            drawLine(Color.White.copy(alpha = 0.65f), Offset(pv.x + nx * r0 * 0.45f + dx * r0, pv.y + ny * r0 * 0.45f + dy * r0), Offset(tip.x + nx * r1 * 0.45f, tip.y + ny * r1 * 0.45f), r1 * 0.22f, StrokeCap.Round)
            drawCircle(PbChrome1, r0 * 0.5f, pv); drawCircle(PbChrome3, r0 * 0.5f, pv, style = Stroke(r0 * 0.12f)); drawCircle(PbChrome3, r0 * 0.15f, pv)
        }

        // ── след и шарики ──
        trail.forEachIndexed { i, p ->
            val k = (i + 1f) / trail.size
            drawCircle(ballColor.copy(alpha = (k * 0.5f).coerceIn(0.04f, 0.5f)), (pd(engine.ballR) * 0.75f * k).coerceAtLeast(2f), pt(p.x, p.y))
        }
        val ballPx = pd(engine.ballR)
        engine.getBalls().forEach { b ->
            val c = pt(b.px, b.py)
            val br = ballPx * (if (b.captured > 0f) 0.55f else 1f)
            drawOval(Color.Black.copy(alpha = 0.45f), Offset(c.x - br * 0.9f + 3f, c.y + br * 0.55f + 3f), Size(br * 1.8f, br * 0.9f))
            if (!b.held) drawCircle(ballColor.copy(alpha = 0.22f), br * 1.7f, c)
            drawCircle(Brush.radialGradient(
                listOf(Color.White, Color(ballColor.red * 0.4f + 0.6f, ballColor.green * 0.4f + 0.6f, ballColor.blue * 0.4f + 0.6f), ballColor, Color(ballColor.red * 0.35f, ballColor.green * 0.35f, ballColor.blue * 0.35f)),
                Offset(c.x - br * 0.35f, c.y - br * 0.4f), br * 1.5f), br, c)
            drawCircle(PbNavy, br, c, style = Stroke(2.5f))
            val ra = b.roll
            drawCircle(Color.White.copy(alpha = 0.8f), br * 0.14f, Offset(c.x - br * 0.38f * cos(ra * 0.4f), c.y - br * 0.42f))
            drawArc(Color.White.copy(alpha = 0.5f), 200f, 60f, false, Offset(c.x - br * 0.75f, c.y - br * 0.75f), Size(br * 1.5f, br * 1.5f), style = Stroke(br * 0.12f, cap = StrokeCap.Round))
        }

        // ── вспышки, искры, очки ──
        flashes.forEach { f ->
            val cp = engine.bumperPositions.getOrNull(f.id) ?: return@forEach
            val prog = ((now - f.start) / 280f).coerceIn(0f, 1f)
            drawCircle(PbNeonGold.copy(alpha = 1f - prog), pd(engine.bumperR) + prog * pd(0.9f), pt(cp.x, cp.y), style = Stroke(pd(0.08f)))
        }
        sparks.forEach { p ->
            drawCircle(p.color.copy(alpha = 0.35f), pd(0.28f), pt(p.x, p.y))
            drawCircle(p.color, pd(0.13f), pt(p.x, p.y))
            drawCircle(Color.White, pd(0.05f), pt(p.x, p.y))
        }
        popups.forEach { p ->
            val prog = ((now - p.start) / 950f).coerceIn(0f, 1f)
            val a = 1f - prog * prog
            val c = pt(p.x, p.y - prog * 0.9f)
            // «цифры» рисуем линиями-кружками: простой индикатор очков
            val digits = p.text
            val w = pd(0.26f)
            val startX = c.x - w * digits.length / 2f
            digits.forEachIndexed { i, ch -> drawPopupChar(ch, Offset(startX + i * w + w / 2f, c.y), w * 0.95f, p.color.copy(alpha = a)) }
        }

        // ── мягкая виньетка по краям ──
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)), Offset(size.width / 2f, size.height / 2f), size.maxDimension * 0.75f))
    }
}

/** Крошечный «семисегментный» шрифт для всплывающих очков: цифры и плюс. */
private fun DrawScope.drawPopupChar(ch: Char, c: Offset, s: Float, col: Color) {
    val w = s * 0.34f
    val h = s * 0.5f
    val sw = s * 0.12f
    fun seg(a: Offset, b: Offset) = drawLine(col, a, b, sw, StrokeCap.Round)
    if (ch == '+') {
        seg(Offset(c.x - w, c.y), Offset(c.x + w, c.y)); seg(Offset(c.x, c.y - w), Offset(c.x, c.y + w)); return
    }
    val d = ch - '0'
    if (d !in 0..9) return
    // сегменты: 0 верх, 1 лев-верх, 2 прав-верх, 3 середина, 4 лев-низ, 5 прав-низ, 6 низ
    val on = arrayOf(
        intArrayOf(0, 1, 2, 4, 5, 6), intArrayOf(2, 5), intArrayOf(0, 2, 3, 4, 6), intArrayOf(0, 2, 3, 5, 6), intArrayOf(1, 2, 3, 5),
        intArrayOf(0, 1, 3, 5, 6), intArrayOf(0, 1, 3, 4, 5, 6), intArrayOf(0, 2, 5), intArrayOf(0, 1, 2, 3, 4, 5, 6), intArrayOf(0, 1, 2, 3, 5, 6)
    )[d]
    val tl = Offset(c.x - w, c.y - h); val tr = Offset(c.x + w, c.y - h)
    val ml = Offset(c.x - w, c.y); val mr = Offset(c.x + w, c.y)
    val bl = Offset(c.x - w, c.y + h); val br = Offset(c.x + w, c.y + h)
    for (sgm in on) when (sgm) {
        0 -> seg(tl, tr); 1 -> seg(tl, ml); 2 -> seg(tr, mr); 3 -> seg(ml, mr)
        4 -> seg(ml, bl); 5 -> seg(mr, br); else -> seg(bl, br)
    }
}
