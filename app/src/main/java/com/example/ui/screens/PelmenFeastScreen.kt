package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.LocalAgeProfile
import com.example.shared.SoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * «Пир у Пельменя» — накорми друга по заказу.
 *
 * Над головой персонажа «облачко» с тем, что он хочет. На столе несколько угощений —
 * перетащи нужное ему в рот. Правильно — ням-ням, сердечко и радость; не то — друг
 * мотает головой («не хочу»), угощение возвращается на стол (без штрафов).
 * Накормил всех по заказу — праздник и новый гость за столом.
 */

private val FEAST_FOODS = listOf("🍎", "🍌", "🥕", "🍕", "🍰", "🥟", "🐟", "🍪", "🍇", "🍓", "🍉", "🧀")

private class FeastGuest(val emoji: String, val name: String, val sound: String? = null)

private val FEAST_GUESTS = listOf(
    FeastGuest("🥟", "Пельмень"),
    FeastGuest("🐱", "Котик", "animal_cat_happy"),
    FeastGuest("🐴", "Лошадка", "animal_horse"),
    FeastGuest("🐻", "Мишка"),
    FeastGuest("🐶", "Собачка", "animal_dog_happy")
)

@Composable
fun PelmenFeastScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val ageProfile = LocalAgeProfile.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()

    val optionCount = if (ageProfile.isToddler2) 3 else 5
    val goal = if (ageProfile.isToddler2) 3 else 5

    var size by remember { mutableStateOf(IntSize.Zero) }
    var guestIdx by remember { mutableIntStateOf(0) }
    var fed by remember { mutableIntStateOf(0) }
    var wanted by remember { mutableStateOf(FEAST_FOODS.first()) }
    val options = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf(false) } // идёт анимация — ввод блокируем
    var bubbleText by remember { mutableStateOf("") }

    var dragIdx by remember { mutableIntStateOf(-1) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    val guestScale = remember { Animatable(1f) }
    val guestShake = remember { Animatable(0f) }
    val guestJump = remember { Animatable(0f) }

    fun newRound() {
        val w = FEAST_FOODS.random()
        wanted = w
        options.clear()
        options.add(w)
        while (options.size < optionCount) {
            val f = FEAST_FOODS.random()
            if (f !in options) options.add(f)
        }
        options.shuffle()
        bubbleText = "Хочу!"
    }

    LaunchedEffect(Unit) { newRound() }

    // Геометрия
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val guestX = w / 2f
    val guestY = h * 0.36f
    val mouthRadius = 120f * density
    fun slotPos(i: Int): Pair<Float, Float> {
        val n = options.size.coerceAtLeast(1)
        val gap = w / (n + 1)
        return Pair(gap * (i + 1), h * 0.8f)
    }

    fun inMouth(x: Float, y: Float): Boolean {
        val dx = x - guestX
        val dy = y - guestY
        return sqrt(dx * dx + dy * dy) < mouthRadius
    }

    fun drop(idx: Int, x: Float, y: Float) {
        if (idx !in options.indices) return
        val food = options[idx]
        if (!inMouth(x, y)) return
        if (food == wanted) {
            busy = true
            soundManager.play("giggle")
            soundPlayer.playChime(1.2f)
            soundPlayer.vibrateSquishDeform()
            scope.launch {
                // Ням-ням: жуёт (3 раза сжимается).
                repeat(3) {
                    guestScale.animateTo(0.88f, tween(110))
                    guestScale.animateTo(1.08f, tween(110))
                }
                guestScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                fed += 1
                FEAST_GUESTS[guestIdx].sound?.let { soundManager.play(it) }
                bubbleText = "❤️ Вкусно!"
                if (fed >= goal) {
                    soundPlayer.playFanfare()
                    repeat(2) {
                        guestJump.animateTo(-70f * density, tween(220))
                        guestJump.animateTo(0f, tween(220))
                    }
                    bubbleText = "🎉 Ура!"
                    delay(700)
                    fed = 0
                    guestIdx = (guestIdx + 1) % FEAST_GUESTS.size
                } else {
                    delay(450)
                }
                newRound()
                busy = false
            }
        } else {
            busy = true
            soundPlayer.playPop(0.6f)
            soundPlayer.vibrateDragTick()
            bubbleText = "Не хочу!"
            scope.launch {
                repeat(2) {
                    guestShake.animateTo(-18f * density, tween(70))
                    guestShake.animateTo(18f * density, tween(70))
                }
                guestShake.animateTo(0f, tween(70))
                bubbleText = "Хочу!"
                busy = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2))))
            .onSizeChanged { size = it }
            .testTag("pelmen_feast_root")
            .pointerInput(size, options.size) {
                detectDragGestures(
                    onDragStart = { pos ->
                        if (busy) return@detectDragGestures
                        var best = -1
                        var bestD = 70f * density
                        for (i in options.indices) {
                            val (sx, sy) = slotPos(i)
                            val d = sqrt((pos.x - sx) * (pos.x - sx) + (pos.y - sy) * (pos.y - sy))
                            if (d < bestD) { bestD = d; best = i }
                        }
                        dragIdx = best
                        dragX = pos.x
                        dragY = pos.y
                    },
                    onDrag = { change, _ ->
                        if (dragIdx >= 0) {
                            dragX = change.position.x
                            dragY = change.position.y
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        val i = dragIdx
                        dragIdx = -1
                        if (i >= 0) drop(i, dragX, dragY)
                    },
                    onDragCancel = { dragIdx = -1 }
                )
            }
    ) {
        if (size != IntSize.Zero) {
            // Стол.
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, (h * 0.7f).roundToInt()) }
                    .fillMaxSize()
                    .background(Color(0xFFA1887F))
            )

            // Персонаж.
            val guest = FEAST_GUESTS[guestIdx]
            Text(
                guest.emoji,
                fontSize = 150.sp,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (guestX + guestShake.value - 90f * density).roundToInt(),
                            (guestY + guestJump.value - 100f * density).roundToInt()
                        )
                    }
                    .graphicsLayer(scaleX = guestScale.value, scaleY = 2f - guestScale.value)
            )

            // Облачко заказа.
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier.offset {
                    IntOffset(
                        (guestX + 70f * density).roundToInt(),
                        (guestY - 170f * density + guestJump.value).roundToInt()
                    )
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(wanted, fontSize = 40.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(bubbleText, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5D4037))
                }
            }

            // Угощения на столе.
            for (i in options.indices) {
                val (sx, sy) = slotPos(i)
                val dragging = dragIdx == i
                val x = if (dragging) dragX else sx
                val y = if (dragging) dragY else sy
                Text(
                    options[i],
                    fontSize = if (dragging) 76.sp else 60.sp,
                    modifier = Modifier.offset {
                        IntOffset((x - 40f * density).roundToInt(), (y - 48f * density).roundToInt())
                    }
                )
            }
        }

        // Шапка с прогрессом.
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(88.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.85f),
                shadowElevation = 4.dp
            ) {
                Text(
                    "Накорми: ${FEAST_GUESTS[guestIdx].name}  " + "❤️".repeat(fed) + "🤍".repeat((goal - fed).coerceAtLeast(0)),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5D4037)
                )
            }
        }
    }
}
