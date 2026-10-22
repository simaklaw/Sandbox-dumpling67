package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.LocalAgeProfile
import com.example.shared.SoundManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * «Луг питомцев» — милые зверята на лугу (и на острове).
 *
 * Тап по зверьку — он подпрыгивает и подаёт голос. Время от времени кто-то из них «хочет» угощение
 * (облачко над головой): перетащи нужное из корзинки ему в рот — получишь монетку-сердечко.
 * Не тому — зверёк мотает головой, без штрафов. За каждые 5 угощений — новая локация.
 */

private class MeadowPet(
    val key: String,
    val food: String,
    val tapSound: String?,
    val happySound: String?,
    val pitch: Float
) {
    var squash = 0f   // 0..1, затухает
    var hop = 0f      // высота прыжка в долях роста
    var hopV = 0f
    var shake = 0f
    var phase = Random.nextFloat() * 6.28f
}

private class FlyingCoin(var x: Float, var y: Float, var vy: Float, var life: Float)

private val MEADOW_PET_DEFS = listOf(
    Triple("cat", "🐟", "animal_cat_1") to ("animal_cat_happy" to 1.0f),
    Triple("dog", "🦴", "animal_dog_1") to ("animal_dog_happy" to 1.0f),
    Triple("hamster1", "🌰", null) to (null to 2.0f),
    Triple("croc", "🍌", null) to (null to 0.55f),
    Triple("rat", "🧀", null) to (null to 1.8f),
    Triple("hamster2", "🌻", null) to (null to 2.2f)
)

private val OTHER_FOODS = listOf("🍎", "🥕", "🍕", "🍪", "🍓", "🥟")

@Composable
fun PetMeadowScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val ageProfile = LocalAgeProfile.current
    val density = LocalDensity.current.density

    val images = mapOf(
        "cat" to ImageBitmap.imageResource(R.drawable.pet_cat),
        "dog" to ImageBitmap.imageResource(R.drawable.pet_dog),
        "rat" to ImageBitmap.imageResource(R.drawable.pet_rat),
        "croc" to ImageBitmap.imageResource(R.drawable.pet_croc),
        "hamster1" to ImageBitmap.imageResource(R.drawable.pet_hamster1),
        "hamster2" to ImageBitmap.imageResource(R.drawable.pet_hamster2)
    )
    val coinImg = ImageBitmap.imageResource(R.drawable.pet_coin)
    val bgMeadow = ImageBitmap.imageResource(R.drawable.pet_bg_meadow)
    val bgIsland = ImageBitmap.imageResource(R.drawable.pet_bg_island)

    val petCount = if (ageProfile.isToddler2) 4 else 6
    val pets = remember {
        MEADOW_PET_DEFS.take(petCount).map { (a, b) ->
            MeadowPet(a.first, a.second, a.third, b.first, b.second)
        }
    }
    val coinsFly = remember { mutableListOf<FlyingCoin>() }

    var size by remember { mutableStateOf(IntSize.Zero) }
    var tick by remember { mutableIntStateOf(0) }
    var scene by remember { mutableIntStateOf(0) }
    var wantIdx by remember { mutableIntStateOf(0) }
    var fed by remember { mutableIntStateOf(0) }
    var coins by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var busyUntil by remember { mutableFloatStateOf(0f) }
    var clock by remember { mutableFloatStateOf(0f) }
    val options = remember { mutableStateListOf<String>() }

    var dragIdx by remember { mutableIntStateOf(-1) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    fun newRound() {
        wantIdx = Random.nextInt(pets.size)
        val want = pets[wantIdx].food
        options.clear()
        options.add(want)
        val pool = (pets.map { it.food } + OTHER_FOODS).filter { it != want }.shuffled()
        options.addAll(pool.take(2))
        options.shuffle()
    }
    LaunchedEffect(Unit) { newRound() }

    // Геометрия
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val cols = if (pets.size <= 4) 2 else 3
    val rows = (pets.size + cols - 1) / cols
    val petH = if (w > 0f) minOf(w / cols * 1.05f, h * 0.60f / rows) else 0f
    val petW = petH * 0.875f
    fun petCenter(i: Int): Pair<Float, Float> {
        val c = i % cols
        val r = i / cols
        val x = w / cols * (c + 0.5f) + (if (r % 2 == 1) w * 0.04f else -w * 0.02f)
        val top = h * 0.2f
        val rowH = (h * 0.62f) / rows
        val y = top + rowH * (r + 1) - petH * 0.5f
        return Pair(x, y)
    }
    fun slotPos(i: Int): Pair<Float, Float> = Pair(w / (options.size + 1) * (i + 1), h * 0.9f)

    fun petAt(x: Float, y: Float): Int {
        for (i in pets.indices.reversed()) {
            val (cx, cy) = petCenter(i)
            if (abs(x - cx) < petW / 2f && abs(y - cy) < petH / 2f) return i
        }
        return -1
    }

    fun bounce(p: MeadowPet) {
        p.squash = 1f
        p.hopV = 0.045f
    }

    fun tapPet(i: Int) {
        val p = pets[i]
        bounce(p)
        if (p.tapSound != null) soundManager.play(p.tapSound) else {
            if (p.pitch < 1f) soundPlayer.playCartoonYelp(p.pitch) else soundPlayer.playPop(p.pitch)
        }
        soundPlayer.vibrateSquishDeform()
    }

    fun dropFood(idx: Int, x: Float, y: Float) {
        if (idx !in options.indices) return
        val i = petAt(x, y)
        if (i < 0) return
        val p = pets[i]
        if (options[idx] == pets[wantIdx].food && i == wantIdx) {
            bounce(p)
            p.hopV = 0.07f
            if (p.happySound != null) soundManager.play(p.happySound) else soundPlayer.playPop(p.pitch)
            soundPlayer.playChime(1.3f)
            val (cx, cy) = petCenter(i)
            repeat(3) { k -> coinsFly.add(FlyingCoin(cx + (k - 1) * 40f * density, cy - petH * 0.3f, -3f - k, 1f)) }
            coins++
            fed++
            message = "❤️ Вкусно!"
            if (fed % 5 == 0) {
                soundPlayer.playFanfare()
                scene = 1 - scene
                message = "🎉 Новое место!"
            }
            busyUntil = clock + 0.6f
            newRound()
        } else {
            p.shake = 1f
            soundPlayer.playPop(0.6f)
            message = "Не хочу!"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF8BC34A))
            .onSizeChanged { size = it }
            .testTag("pet_meadow_root")
            .pointerInput(size, options.size) {
                detectDragGestures(
                    onDragStart = { pos ->
                        var best = -1
                        var bestD = 70f * density
                        for (i in options.indices) {
                            val (sx, sy) = slotPos(i)
                            val d = sqrt((pos.x - sx) * (pos.x - sx) + (pos.y - sy) * (pos.y - sy))
                            if (d < bestD) { bestD = d; best = i }
                        }
                        dragIdx = best
                        dragX = pos.x; dragY = pos.y
                        if (best < 0) {
                            val pi = petAt(pos.x, pos.y)
                            if (pi >= 0) tapPet(pi)
                        }
                    },
                    onDrag = { change, _ ->
                        if (dragIdx >= 0) {
                            dragX = change.position.x; dragY = change.position.y
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        val i = dragIdx
                        dragIdx = -1
                        if (i >= 0) dropFood(i, dragX, dragY)
                    },
                    onDragCancel = { dragIdx = -1 }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val redraw = tick
            drawCover(if (scene == 0) bgMeadow else bgIsland, if (scene == 0) 0.45f else 0.5f)
            // Зверьки: задние ряды рисуем первыми.
            for (i in pets.indices) {
                val p = pets[i]
                val img = images[p.key] ?: continue
                val (cx, cy0) = petCenter(i)
                val breathe = 1f + sin(clock * 2f + p.phase) * 0.012f
                val sq = p.squash
                val sx = (1f + 0.12f * sq) * breathe
                val sy = (1f - 0.14f * sq) / breathe
                val cy = cy0 - p.hop * petH
                val bottomX = cx + p.shake * sin(clock * 40f) * 10f * density
                val bottomY = cy + petH / 2f
                // тень
                drawOval(
                    Color.Black.copy(alpha = 0.18f),
                    androidx.compose.ui.geometry.Offset(cx - petW * 0.4f, cy0 + petH * 0.46f),
                    androidx.compose.ui.geometry.Size(petW * 0.8f, petH * 0.1f)
                )
                withTransform({
                    scale(sx, sy, androidx.compose.ui.geometry.Offset(bottomX, bottomY))
                }) {
                    drawImage(
                        img,
                        dstOffset = IntOffset((bottomX - petW / 2f).roundToInt(), (bottomY - petH).roundToInt()),
                        dstSize = IntSize(petW.roundToInt(), petH.roundToInt())
                    )
                }
            }
            for (c in coinsFly) {
                val s = 44f * density
                drawImage(
                    coinImg,
                    dstOffset = IntOffset((c.x - s / 2f).roundToInt(), c.y.roundToInt()),
                    dstSize = IntSize(s.roundToInt(), s.roundToInt()),
                    alpha = c.life.coerceIn(0f, 1f)
                )
            }
        }

        if (size != IntSize.Zero) {
            // Облачко над желающим.
            val (wx, wy) = petCenter(wantIdx)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier.offset {
                    IntOffset((wx - 34f * density).roundToInt(), (wy - petH * 0.5f - 52f * density).roundToInt())
                }
            ) {
                Text(
                    pets[wantIdx].food + "?",
                    fontSize = 30.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            // Корзинка с угощениями.
            Box(
                Modifier
                    .offset { IntOffset(0, (h * 0.8f).roundToInt()) }
                    .fillMaxSize()
                    .background(Color(0xFFA1887F).copy(alpha = 0.55f))
            )
            for (i in options.indices) {
                val (sx, sy) = slotPos(i)
                val dragging = dragIdx == i
                val x = if (dragging) dragX else sx
                val y = if (dragging) dragY else sy
                Text(
                    options[i],
                    fontSize = if (dragging) 70.sp else 54.sp,
                    modifier = Modifier.offset {
                        IntOffset((x - 38f * density).roundToInt(), (y - 46f * density).roundToInt())
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(88.dp))
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.88f), shadowElevation = 4.dp) {
                Text(
                    "🪙 $coins   " + message,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5D4037)
                )
            }
        }
    }

    // Анимации зверьков и монеток.
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                clock += dt
                for (p in pets) {
                    p.squash = max(0f, p.squash - dt * 3.5f)
                    p.shake = max(0f, p.shake - dt * 2.5f)
                    if (p.hopV != 0f || p.hop > 0f) {
                        p.hop += p.hopV * dt * 60f
                        p.hopV -= 0.0035f * dt * 60f
                        if (p.hop <= 0f) { p.hop = 0f; p.hopV = 0f }
                    }
                }
                val it = coinsFly.iterator()
                while (it.hasNext()) {
                    val c = it.next()
                    c.y += c.vy * dt * 60f * density * 0.6f
                    c.life -= dt * 0.9f
                    if (c.life <= 0f) it.remove()
                }
                tick++
            }
        }
    }
}

/** Рисует картинку «по размеру экрана» с обрезкой по краям (cover); focus — по какой доле ширины центрируем. */
private fun DrawScope.drawCover(img: ImageBitmap, focus: Float) {
    val s = max(size.width / img.width, size.height / img.height)
    val dw = img.width * s
    val dh = img.height * s
    val ox = -(dw - size.width) * focus
    val oy = -(dh - size.height) * 0.5f
    drawImage(
        img,
        dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
        dstSize = IntSize(dw.roundToInt() + 1, dh.roundToInt() + 1)
    )
}
