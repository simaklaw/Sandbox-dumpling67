package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.shared.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.screens.Boxes.BoxSurprise
import com.example.screens.Boxes.BoxSurpriseCatalog
import com.example.screens.Boxes.GiftBoxItem
import com.example.shared.ColorInk
import com.example.shared.Haptics
import com.example.shared.LocalAgeProfile
import com.example.shared.SoundManager
import com.example.shared.rememberHaptics
import com.example.shared.scene.SceneBackground
import com.example.shared.scene.SceneTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

// ============================================================================
// 📦 КТО В КОРОБКЕ? (Шаг 14)
// Тап по подарочной коробке → она трясётся → крышка слетает → выпрыгивает
// сюрприз со своим звуком. Тап по открытой коробке — закрывает её,
// внутри уже кто-то другой. Нет проигрыша; 2 года: 3 крупные коробки.
// ============================================================================

private val VOICED_SURPRISES = setOf("duck", "dog", "cat", "frog")

private enum class BoxPhase { CLOSED, SHAKING, OPEN }

private val BOX_PALETTE = listOf(
    Color(0xFFFF8FB1) to Color(0xFFFFD35C),
    Color(0xFF7FC8FF) to Color(0xFFFF6F61),
    Color(0xFF8EDC9D) to Color(0xFFB79CFF),
    Color(0xFFFFB36B) to Color(0xFF4F7BFF)
)

@Composable
fun BoxSurpriseScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val age = LocalAgeProfile.current
    val haptics = rememberHaptics()
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }

    val boxCount = if (age.isToddler2) 3 else 4
    val boxes = remember(boxCount) {
        List(boxCount) { i ->
            GiftBoxItem(id = i, baseColor = BOX_PALETTE[i % BOX_PALETTE.size].first, ribbonColor = BOX_PALETTE[i % BOX_PALETTE.size].second)
        }
    }

    // Какой сюрприз лежит в каждой коробке; при закрытии коробки выбираем нового,
    // которого сейчас нет ни в одной другой коробке — так игра не повторяется.
    val surprises = remember(boxCount) {
        mutableStateListOf<BoxSurprise>().apply {
            addAll(BoxSurpriseCatalog.ALL_SURPRISES.shuffled().take(boxCount))
        }
    }
    var interacted by remember { mutableStateOf(false) }
    var activity by remember { mutableIntStateOf(0) }   // любое реальное касание сбрасывает ожидание
    var demoToken by remember { mutableIntStateOf(0) }  // авто-демо: «тап» по очередной коробке

    // Авто-демо (2 года): если 10 с тишины — очередная коробка сама открывается
    LaunchedEffect(activity, age.id) {
        val secs = age.autoDemoIdleDelaySeconds
        if (secs > 0) {
            while (true) {
                delay(secs * 1000L)
                demoToken++
            }
        }
    }

    // Альбом открытых сюрпризов хранится на устройстве (SharedPreferences) — ребёнок видит прогресс 🐾/❔
    val prefs = remember { context.getSharedPreferences("playground_prefs", android.content.Context.MODE_PRIVATE) }
    var discovered by remember {
        mutableStateOf(
            try { prefs.getStringSet("box_discovered", emptySet())?.toSet() ?: emptySet() } catch (e: Exception) { emptySet<String>() }
        )
    }

    fun reveal(item: BoxSurprise) {
        if (item.id in discovered) return
        discovered = discovered + item.id
        try { prefs.edit().putStringSet("box_discovered", discovered).apply() } catch (_: Exception) {}
        if (discovered.size == BoxSurpriseCatalog.ALL_SURPRISES.size) {
            soundManager.play("rainbow_chime") // собрал всех!
            haptics.vibratePop()
        }
    }

    fun nextSurpriseFor(index: Int) {
        val taken = surprises.map { it.id }.toSet()
        val pool = BoxSurpriseCatalog.ALL_SURPRISES.filter { it.id !in taken }
        surprises[index] = (if (pool.isNotEmpty()) pool else BoxSurpriseCatalog.ALL_SURPRISES).random()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SceneBackground(theme = SceneTheme.ROOM)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 88.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (age.isToddler2) {
                // 3 больших коробки столбиком
                for (i in 0 until boxCount) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        GiftBoxView(
                            item = boxes[i],
                            surprise = surprises[i],
                            soundManager = soundManager,
                            haptics = haptics,
                            hintWiggle = !interacted && i == 0,
                            onOpened = { interacted = true; activity++ },
                            onRevealed = ::reveal,
                            demoSignal = if (demoToken > 0 && (demoToken - 1) % boxCount == i) demoToken else 0,
                            onClosed = { nextSurpriseFor(i); activity++ }
                        )
                    }
                }
            } else {
                // 2 x 2
                for (row in 0 until 2) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (col in 0 until 2) {
                            val i = row * 2 + col
                            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                GiftBoxView(
                                    item = boxes[i],
                                    surprise = surprises[i],
                                    soundManager = soundManager,
                                    haptics = haptics,
                                    hintWiggle = !interacted && i == 0,
                                    onOpened = { interacted = true; activity++ },
                                    onRevealed = ::reveal,
                                    demoSignal = if (demoToken > 0 && (demoToken - 1) % boxCount == i) demoToken else 0,
                                    onClosed = { nextSurpriseFor(i); activity++ }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 📖 Альбом: открытые сюрпризы показываем, остальные — «❔»
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 10.dp)
                .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                .border(BorderStroke(2.dp, ColorInk), RoundedCornerShape(16.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            BoxSurpriseCatalog.ALL_SURPRISES.forEach { sp ->
                Text(text = if (sp.id in discovered) sp.emoji else "❔", fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun GiftBoxView(
    item: GiftBoxItem,
    surprise: BoxSurprise,
    soundManager: SoundManager,
    haptics: Haptics,
    hintWiggle: Boolean,
    onOpened: () -> Unit,
    onRevealed: (BoxSurprise) -> Unit,
    demoSignal: Int,
    onClosed: () -> Unit
) {
    var phase by remember { mutableStateOf(BoxPhase.CLOSED) }
    val shake = remember { Animatable(0f) }      // угол, градусы
    val lid = remember { Animatable(0f) }        // 0 закрыта … 1 слетела
    val jump = remember { Animatable(0f) }       // 0 внутри … 1 над коробкой
    val squash = remember { Animatable(1f) }     // «пружинка» при нажатии

    // Подсказка: первая коробка слегка покачивается, пока ребёнок ничего не тронул
    val idle = rememberInfiniteTransition(label = "box_idle")
    val wiggle by idle.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "wiggle"
    )

    var tapToken by remember { mutableIntStateOf(0) }
    var demoFlag by remember { mutableStateOf(false) } // тап вызван демо, а не ребёнком

    LaunchedEffect(demoSignal) {
        if (demoSignal > 0 && phase == BoxPhase.CLOSED) {
            demoFlag = true
            tapToken++
        }
    }

    LaunchedEffect(tapToken) {
        if (tapToken == 0) return@LaunchedEffect
        when (phase) {
            BoxPhase.CLOSED -> {
                phase = BoxPhase.SHAKING
                if (!demoFlag) onOpened()
                demoFlag = false
                soundManager.play("box_shake")
                haptics.vibrateShort()
                // три быстрых покачивания
                repeat(3) {
                    shake.animateTo(9f, tween(70))
                    shake.animateTo(-9f, tween(140))
                    shake.animateTo(0f, tween(70))
                }
                // открываем
                soundManager.play("box_pop")
                haptics.vibratePop()
                phase = BoxPhase.OPEN
                lid.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
                soundManager.play(surprise.soundKey)
                onRevealed(surprise)
                // Голос называет зверя (записи есть только для некоторых)
                if (surprise.id in VOICED_SURPRISES) {
                    launch {
                        delay(900)
                        soundManager.play("voice_name_${surprise.id}")
                    }
                }
                jump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            }
            BoxPhase.OPEN -> {
                // закрываем крышку, сюрприз прячется
                jump.animateTo(0f, tween(180))
                lid.animateTo(0f, tween(220))
                soundManager.play("tap")
                phase = BoxPhase.CLOSED
                onClosed()
            }
            BoxPhase.SHAKING -> Unit // игнорируем тапы во время тряски
        }
    }

    // Лёгкая «пружинка» на нажатие
    LaunchedEffect(tapToken) {
        if (tapToken == 0) return@LaunchedEffect
        squash.snapTo(0.93f)
        squash.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { if (phase != BoxPhase.SHAKING) tapToken++ },
        contentAlignment = Alignment.BottomCenter
    ) {
        val side = min(maxWidth.value, maxHeight.value * 0.78f)
        val emojiSize = (side * 0.40f).sp

        // Сюрприз (под коробкой по слою, поэтому рисуем раньше)
        if (phase == BoxPhase.OPEN || jump.value > 0.01f) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        val j = jump.value
                        translationY = -j * side * 0.66f * density
                        val s = 0.4f + 0.6f * min(1f, j * 1.2f)
                        scaleX = s
                        scaleY = s
                        alpha = min(1f, j * 4f)
                    }
            ) {
                if (phase == BoxPhase.OPEN) {
                    Text(
                        text = surprise.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = ColorInk,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                            .border(BorderStroke(2.dp, ColorInk), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
                Text(text = surprise.emoji, fontSize = emojiSize)
            }
        }

        // Сама коробка
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(side.dp, (side * 0.8f).dp)
                .graphicsLayer {
                    val angle = shake.value + if (hintWiggle && phase == BoxPhase.CLOSED) wiggle else 0f
                    rotationZ = angle
                    scaleX = squash.value
                    scaleY = squash.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                }
        ) {
            val w = size.width
            val h = size.height
            val ink = Stroke(width = 4.dp.toPx())
            val bodyTop = h * 0.30f
            val bodyH = h - bodyTop
            val corner = CornerRadius(14.dp.toPx())

            // корпус
            drawRoundRect(item.baseColor, Offset(w * 0.06f, bodyTop), Size(w * 0.88f, bodyH), corner)
            drawRoundRect(ColorInk, Offset(w * 0.06f, bodyTop), Size(w * 0.88f, bodyH), corner, style = ink)
            // лента вертикальная
            drawRect(item.ribbonColor, Offset(w * 0.44f, bodyTop), Size(w * 0.12f, bodyH))
            drawLine(ColorInk, Offset(w * 0.44f, bodyTop), Offset(w * 0.44f, h), strokeWidth = 3.dp.toPx())
            drawLine(ColorInk, Offset(w * 0.56f, bodyTop), Offset(w * 0.56f, h), strokeWidth = 3.dp.toPx())

            // тёмная «пасть», когда открыто
            if (lid.value > 0.05f) {
                drawRoundRect(
                    Color(0xFF3B2A2F).copy(alpha = min(1f, lid.value * 2f)),
                    Offset(w * 0.12f, bodyTop + 3.dp.toPx()), Size(w * 0.76f, h * 0.10f),
                    CornerRadius(8.dp.toPx())
                )
            }

            // крышка: слетает вверх и в сторону, чуть вращаясь
            val l = lid.value
            val lidW = w * 0.96f
            val lidH = h * 0.20f
            val lidCx = w / 2f + l * w * 0.34f
            val lidCy = bodyTop - lidH * 0.1f - l * h * 0.38f
            rotate(degrees = l * 28f, pivot = Offset(lidCx, lidCy)) {
                drawRoundRect(item.baseColor, Offset(lidCx - lidW / 2f, lidCy - lidH / 2f), Size(lidW, lidH), corner)
                drawRoundRect(ColorInk, Offset(lidCx - lidW / 2f, lidCy - lidH / 2f), Size(lidW, lidH), corner, style = ink)
                drawRect(item.ribbonColor, Offset(lidCx - lidW * 0.06f, lidCy - lidH / 2f), Size(lidW * 0.12f, lidH))
                // бант
                val bowR = lidH * 0.55f
                drawCircle(item.ribbonColor, bowR, Offset(lidCx - bowR, lidCy - lidH / 2f - bowR * 0.3f))
                drawCircle(item.ribbonColor, bowR, Offset(lidCx + bowR, lidCy - lidH / 2f - bowR * 0.3f))
                drawCircle(ColorInk, bowR, Offset(lidCx - bowR, lidCy - lidH / 2f - bowR * 0.3f), style = Stroke(3.dp.toPx()))
                drawCircle(ColorInk, bowR, Offset(lidCx + bowR, lidCy - lidH / 2f - bowR * 0.3f), style = Stroke(3.dp.toPx()))
                drawCircle(item.ribbonColor, bowR * 0.55f, Offset(lidCx, lidCy - lidH / 2f - bowR * 0.2f))
                drawCircle(ColorInk, bowR * 0.55f, Offset(lidCx, lidCy - lidH / 2f - bowR * 0.2f), style = Stroke(3.dp.toPx()))
            }
        }
    }
}
