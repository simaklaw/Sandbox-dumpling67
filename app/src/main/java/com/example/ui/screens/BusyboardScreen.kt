package com.example.ui.screens

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.screens.Busyboard.BB_CONFETTI
import com.example.screens.Busyboard.BB_DOT
import com.example.screens.Busyboard.BB_ROOMS
import com.example.screens.Busyboard.BB_STAR
import com.example.screens.Busyboard.BB_STARS
import com.example.screens.Busyboard.BbAbacus
import com.example.screens.Busyboard.BbAudio
import com.example.screens.Busyboard.BbCard
import com.example.screens.Busyboard.BbCtx
import com.example.screens.Busyboard.BbElement
import com.example.screens.Busyboard.BbGears
import com.example.screens.Busyboard.BbKeyLock
import com.example.screens.Busyboard.BbLatch
import com.example.screens.Busyboard.BbPanelBoard
import com.example.screens.Busyboard.BbParticles
import com.example.screens.Busyboard.BbSound
import com.example.screens.Busyboard.BbSwitch
import com.example.screens.Busyboard.BbWheel
import com.example.screens.Busyboard.BbXylophone
import com.example.screens.Busyboard.BbZipper
import com.example.screens.Busyboard.bbAward
import com.example.screens.Busyboard.bbCount
import com.example.screens.Busyboard.bbHas
import com.example.screens.Busyboard.bbMood
import com.example.screens.Busyboard.bbNextHint
import com.example.screens.Busyboard.bbRoomCount
import com.example.screens.Busyboard.bbRoomSize
import com.example.screens.Busyboard.rememberBbTick
import com.example.shared.ColorBgSoft
import com.example.shared.ColorGreen
import com.example.shared.ColorInk
import com.example.shared.ColorOrange
import com.example.shared.ColorWhite
import com.example.shared.ColorYellow
import com.example.shared.LocalAgeProfile
import com.example.shared.ShapeButton
import com.example.shared.ShapeCard
import com.example.shared.SoundManager
import com.example.shared.i18n.Text
import com.example.shared.plasticineClickable
import com.example.shared.rememberHaptics
import com.example.shared.scene.SceneBackground
import com.example.shared.scene.SceneTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * «Бизиборд» (комната 10) — лучшее из трёх присланных вариантов (View-версия, HTML «Ultimate», Unity):
 *  • 3 комнаты, 9 деталей, 9 звёздочек → шкала сверху, «настроение комнаты» теплеет, в конце фанфара и салют;
 *  • комната 1 «Замочки»: задвижка, выключатель с лампой, молния с сюрпризом, замок и ключ;
 *  • комната 2 «Звуки»: шестерёнки с храповиком и кольцом прогресса, счёты, ксилофон, колесо с инерцией;
 *  • комната 3 «Щиток»: электрощиток — рубильник, автоматы, лампы-мордочки, перегрузка и «Фух!».
 * Логика деталей — в screens/Busyboard (BusyboardRules.kt покрыт юнит-тестами), звук — BusyboardAudio.kt.
 */

private class BbRoomInfo(val emoji: String, val label: String)

private val BB_ROOM_INFO = listOf(
    BbRoomInfo("🔐", "Замочки"),
    BbRoomInfo("🎵", "Звуки"),
    BbRoomInfo("⚡", "Щиток")
)

@Composable
fun BusyboardScreen(
    @Suppress("UNUSED_PARAMETER") repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val ageProfile = LocalAgeProfile.current
    val toddler = ageProfile.isToddler2
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()

    val audio = remember(soundManager) { BbAudio(soundManager) }
    LaunchedEffect(audio) { audio.preload() }
    // Ничто не должно доигрывать после ухода из комнаты или сворачивания приложения.
    DisposableEffect(audio) { onDispose { audio.silence() } }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, audio) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) audio.silence() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var room by rememberSaveable { mutableIntStateOf(0) }
    var mask by rememberSaveable { mutableIntStateOf(0) }
    var lastTouch by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var hintTarget by remember { mutableStateOf<BbElement?>(null) }
    var showDone by remember { mutableStateOf(false) }
    var epoch by remember { mutableIntStateOf(0) }
    var rootW by remember { mutableFloatStateOf(1f) }
    val pulse = remember { Animatable(0f) }
    val confetti = remember { BbParticles(220) }
    val confettiTick = rememberBbTick { dt -> confetti.update(dt, 170f * density) }

    fun spawnConfetti() {
        repeat(if (toddler) 60 else 120) {
            confetti.emit(
                Random.nextFloat() * rootW, -20f,
                (Random.nextFloat() - 0.5f) * 120f * density, (110f + Random.nextFloat() * 240f) * density,
                3.5f + Random.nextFloat() * 1.8f, (5f + Random.nextFloat() * 6f) * density,
                BB_CONFETTI.random(), if (Random.nextBoolean()) BB_STAR else BB_DOT
            )
        }
        confettiTick.bump()
    }

    val ctx = remember(audio, haptics, toddler) {
        BbCtx(
            audio = audio,
            haptics = haptics,
            toddler = toddler,
            onReward = { e ->
                if (!bbHas(mask, e)) {
                    mask = bbAward(mask, e)
                    val n = bbCount(mask)
                    scope.launch {
                        pulse.snapTo(1f)
                        pulse.animateTo(0f, tween(650))
                    }
                    if (n >= BB_STARS) {
                        audio.play(BbSound.FANFARE)
                        audio.praise()
                        showDone = true
                        spawnConfetti()
                    } else {
                        audio.play(BbSound.DING)
                        if (n % 3 == 0) audio.praise()
                    }
                }
            },
            onTouch = {
                lastTouch = SystemClock.uptimeMillis()
                hintTarget = null
            }
        )
    }

    // Если ребёнок затих, подсвечиваем следующую ещё не освоенную деталь в этой комнате.
    LaunchedEffect(room, mask) {
        while (isActive) {
            delay(500L)
            val idle = SystemClock.uptimeMillis() - lastTouch
            hintTarget = if (idle > ageProfile.hintIdleDelaySeconds * 1000L) bbNextHint(mask, room) else null
        }
    }
    LaunchedEffect(showDone) {
        if (showDone) {
            delay(9000L)
            showDone = false
        }
    }

    fun restart() {
        mask = 0
        room = 0
        epoch++
        showDone = false
        confetti.clear()
        audio.silence()
    }

    fun goTo(target: Int) {
        val t = target.coerceIn(0, BB_ROOMS - 1)
        if (t != room) {
            audio.silence()
            audio.play(BbSound.WHOOSH)
            room = t
            hintTarget = null
            lastTouch = SystemClock.uptimeMillis()
        }
    }

    val mood by animateFloatAsState(bbMood(mask), tween(900), label = "bbMood")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .onSizeChanged { rootW = it.width.toFloat() }
    ) {
        SceneBackground(theme = SceneTheme.ROOM)
        // «Настроение комнаты»: в начале приглушённый вечерний свет, по мере звёздочек — тёплое сияние.
        // mood читается при рисовании, а не при композиции: анимация перерисовывает только эти слои
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Color(0xFF283593).copy(alpha = (1f - mood) * 0.30f)) })
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Brush.radialGradient(listOf(Color(0xFFFFE9A8).copy(alpha = 0.55f * mood), Color.Transparent))) })

        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(76.dp)) // место под кнопку «Домой»
                BbStarBar(count = bbCount(mask), pulse = { pulse.value }, modifier = Modifier.weight(1f).height(46.dp))
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    // «играет» = любое движение пальца по доске (не только касание): подсказка не мигает посреди игры.
                    // Наблюдаем на первом проходе и ничего не потребляем, детали получают события как обычно.
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                lastTouch = SystemClock.uptimeMillis()
                                if (hintTarget != null) hintTarget = null
                            }
                        }
                    }
            ) {
                // Без перекрёстного затухания: старая комната сразу уходит из композиции и не доигрывает звуки.
                // epoch меняется на «Играть ещё» — все детали создаются заново.
                key(room, epoch) {
                    when (room) {
                        0 -> RoomLocks(ctx, mask, hintTarget)
                        1 -> RoomSounds(ctx, mask, hintTarget)
                        else -> RoomPanel(ctx, mask, hintTarget)
                    }
                }
            }

            val arrowSize = if (toddler) 72.dp else 56.dp
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BbArrow("◀", room > 0, arrowSize, "busyboard_prev_btn", haptics) { goTo(room - 1) }
                Row(
                    Modifier
                        .weight(1f)
                        .background(ColorWhite, ShapeCard)
                        .border(BorderStroke(2.5.dp, ColorInk), ShapeCard)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (i in 0 until BB_ROOMS) {
                        val selected = i == room
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(ShapeButton)
                                .background(if (selected) ColorYellow else ColorBgSoft, ShapeButton)
                                .plasticineClickable(haptics = haptics) { goTo(i) }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = BB_ROOM_INFO[i].emoji, fontSize = 20.sp)
                            Text(text = BB_ROOM_INFO[i].label, fontSize = 11.sp, color = ColorInk, fontWeight = FontWeight.Bold)
                            Text(text = "${bbRoomCount(mask, i)}/${bbRoomSize(i)}", fontSize = 11.sp, color = ColorInk)
                        }
                    }
                }
                BbArrow("▶", room < BB_ROOMS - 1, arrowSize, "busyboard_next_btn", haptics) { goTo(room + 1) }
            }
        }

        // салют — просто рисунок поверх, касания не перехватывает
        Canvas(Modifier.fillMaxSize()) {
            confettiTick.value
            confetti.draw(this)
        }

        if (showDone) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .background(ColorWhite, ShapeCard)
                    .border(BorderStroke(3.dp, ColorInk), ShapeCard)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🌟", fontSize = 64.sp)
                Text(text = "Умница!", fontSize = 28.sp, color = ColorInk, fontWeight = FontWeight.Bold)
                Text(text = "Все звёздочки собраны", fontSize = 16.sp, color = ColorInk)
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .clip(ShapeButton)
                        .background(ColorGreen, ShapeButton)
                        .border(BorderStroke(3.dp, ColorInk), ShapeButton)
                        .plasticineClickable(haptics = haptics) { restart() }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(text = "Играть ещё", fontSize = 20.sp, color = ColorInk, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BbArrow(
    label: String,
    enabled: Boolean,
    sizeDp: androidx.compose.ui.unit.Dp,
    tag: String,
    haptics: com.example.shared.Haptics,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(sizeDp)
            .border(BorderStroke(3.dp, ColorInk), ShapeButton)
            .background(if (enabled) ColorOrange else ColorBgSoft, ShapeButton)
            .clip(ShapeButton)
            .plasticineClickable(enabled = enabled, haptics = haptics, onClick = onClick)
            .testTag(tag)
    ) {
        Text(text = label, fontSize = 26.sp, color = if (enabled) ColorInk else ColorInk.copy(alpha = 0.3f), fontWeight = FontWeight.Bold)
    }
}

/** Шкала из девяти звёзд: загораются слева направо, последняя «подпрыгивает». */
@Composable
private fun BbStarBar(count: Int, pulse: () -> Float, modifier: Modifier) {
    Canvas(modifier) {
        val pl = pulse()
        val cell = size.width / BB_STARS
        val path = Path()
        for (i in 0 until BB_STARS) {
            val cx = cell * (i + 0.5f)
            val cy = size.height / 2f
            val earned = i < count
            val newest = earned && i == count - 1
            val r = min(cell * 0.46f, size.height * 0.46f) * (if (newest) 1f + 0.55f * pl else 1f)
            path.rewind()
            for (k in 0 until 10) {
                val rr = if (k % 2 == 0) r else r * 0.45f
                val a = (-PI / 2 + k * PI / 5).toFloat()
                val px = cx + cos(a) * rr
                val py = cy + sin(a) * rr
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            drawPath(path, if (earned) Color(0xFFFFC93C) else Color.White.copy(alpha = 0.55f))
            drawPath(path, ColorInk, style = Stroke(2.5f * density))
        }
    }
}

// ───────────────────────── комнаты ─────────────────────────

@Composable
private fun RoomLocks(ctx: BbCtx, mask: Int, hint: BbElement?) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BbCard("Задвижка", bbHas(mask, BbElement.LATCH), hint == BbElement.LATCH, Color(0xFFFFE0B2), Modifier.weight(1f).fillMaxHeight()) { BbLatch(ctx) }
            BbCard("Выключатель", bbHas(mask, BbElement.SWITCH), hint == BbElement.SWITCH, Color(0xFFFFF59D), Modifier.weight(1f).fillMaxHeight()) { BbSwitch(ctx) }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BbCard("Молния", bbHas(mask, BbElement.ZIPPER), hint == BbElement.ZIPPER, Color(0xFFBBDEFB), Modifier.weight(1f).fillMaxHeight()) { BbZipper(ctx) }
            BbCard("Замок", bbHas(mask, BbElement.KEYLOCK), hint == BbElement.KEYLOCK, Color(0xFFFFCCBC), Modifier.weight(1f).fillMaxHeight()) { BbKeyLock(ctx) }
        }
    }
}

@Composable
private fun RoomSounds(ctx: BbCtx, mask: Int, hint: BbElement?) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BbCard("Шестерёнки", bbHas(mask, BbElement.GEARS), hint == BbElement.GEARS, Color(0xFFC8E6C9), Modifier.weight(1f).fillMaxHeight()) { BbGears(ctx) }
            BbCard("Счёты", bbHas(mask, BbElement.ABACUS), hint == BbElement.ABACUS, Color(0xFFFFE0B2), Modifier.weight(1f).fillMaxHeight()) { BbAbacus(ctx) }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BbCard("Ксилофон", bbHas(mask, BbElement.XYLOPHONE), hint == BbElement.XYLOPHONE, Color(0xFFE1BEE7), Modifier.weight(1f).fillMaxHeight()) { BbXylophone(ctx) }
            BbCard("Колесо", bbHas(mask, BbElement.WHEEL), hint == BbElement.WHEEL, Color(0xFFB3E5FC), Modifier.weight(1f).fillMaxHeight()) { BbWheel(ctx) }
        }
    }
}

@Composable
private fun RoomPanel(ctx: BbCtx, mask: Int, hint: BbElement?) {
    BbCard("Электрощиток", bbHas(mask, BbElement.PANEL), hint == BbElement.PANEL, Color(0xFF263238), Modifier.fillMaxSize()) { BbPanelBoard(ctx) }
}
