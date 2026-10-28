package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.shared.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.screens.Boxes.*
import com.example.screens.Catapult.CatapultParticle
import com.example.screens.Squish.SquishCatalog
import com.example.shared.*
import com.example.shared.scene.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

/**
 * 🎁 Игра 2: «Кто в коробке?» (ШАГ 14)
 * - 4 подарочные коробки на ковре (3 в режиме «2 года»).
 * - Касание: тряска (0.8 с, box_shake), крышка подпрыгивает (box_pop), выпрыгивает сюрприз с танцем и звуком.
 * - Кнопка «Альбом» (72 dp, правый верх): открытые сюрпризы цветные, неоткрытые — силуэты '?'.
 * - При открытии всех 10 сюрпризов — салют конфетти и подарок для шкафа «Мялки».
 */
@Composable
fun BlindBoxScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val ageProfile = LocalAgeProfile.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // SharedPreferences for unlocked surprises album
    val prefs = remember { context.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE) }
    val unlockedSurpriseIds = remember {
        val saved = prefs.getStringSet("boxes_unlocked_surprises", emptySet()) ?: emptySet()
        mutableStateListOf<String>().apply { addAll(saved) }
    }

    var isAlbumOpen by remember { mutableStateOf(false) }

    // Box configurations
    val boxCount = if (ageProfile.isToddler2) 3 else 4
    val boxColors = listOf(ColorPink, ColorYellow, ColorBlue, ColorGreen)

    val boxes = remember {
        mutableStateListOf<GiftBoxItem>().apply {
            for (i in 0 until boxCount) {
                add(
                    GiftBoxItem(
                        id = i,
                        baseColor = boxColors[i % boxColors.size],
                        ribbonColor = if (i % 2 == 0) ColorYellow else ColorPink
                    )
                )
            }
        }
    }

    var lastSurpriseId by remember { mutableStateOf<String?>(null) }

    // Floating particles
    val particles = remember { mutableStateListOf<CatapultParticle>() }

    // Decor for Room theme
    val roomDecors = remember {
        listOf(
            DecorSpec(DecorKind.GUEST, 0.12f, 0.25f, 76f),
            DecorSpec(DecorKind.BALLOON, 0.88f, 0.22f, 64f),
            DecorSpec(DecorKind.BULB, 0.50f, 0.06f, 48f)
        )
    }

    fun openBox(box: GiftBoxItem) {
        val now = System.currentTimeMillis()
        if (now - box.lastActionTime < 1000L) return
        box.lastActionTime = now

        coroutineScope.launch {
            // 1. Shake anticipation (0.8s)
            box.isShaking = true
            soundManager.play("box_shake")
            haptics.vibrate(20)

            val shakeAnim = Animatable(0f)
            repeat(3) {
                shakeAnim.animateTo(8f, tween(100))
                shakeAnim.animateTo(-8f, tween(100))
            }
            shakeAnim.animateTo(0f, tween(80))
            box.isShaking = false

            // 2. Pop open lid
            box.isLidOpen = true
            soundManager.play("box_pop")
            haptics.vibrate(25)

            // 3. Choose Surprise (Favor non-unlocked 60%)
            val maxSurprises = if (ageProfile.isToddler2) 6 else 10
            val catalog = BoxSurpriseCatalog.ALL_SURPRISES.take(maxSurprises)
            val locked = catalog.filter { it.id !in unlockedSurpriseIds && it.id != lastSurpriseId }

            val chosen = if (locked.isNotEmpty() && Random.nextFloat() < 0.60f) {
                locked.random()
            } else {
                val pool = catalog.filter { it.id != lastSurpriseId }
                if (pool.isNotEmpty()) pool.random() else catalog.random()
            }
            lastSurpriseId = chosen.id
            box.currentSurprise = chosen

            // Check if newly discovered
            if (chosen.id !in unlockedSurpriseIds) {
                unlockedSurpriseIds.add(chosen.id)
                prefs.edit().putStringSet("boxes_unlocked_surprises", unlockedSurpriseIds.toSet()).apply()
                soundManager.play("chime")

                // Check all unlocked -> Grand Reward!
                if (unlockedSurpriseIds.size >= maxSurprises) {
                    soundManager.play("fanfare_short")
                    // Unlock a wardrobe item in Мялка
                    val squishUnlocked = prefs.getStringSet("squish_unlocked_items", emptySet()) ?: emptySet()
                    val lockedWardrobe = SquishCatalog.ALL_ITEMS.filter { it.id !in squishUnlocked }
                    if (lockedWardrobe.isNotEmpty()) {
                        val newItem = lockedWardrobe.random()
                        prefs.edit().putStringSet("squish_unlocked_items", squishUnlocked + newItem.id).apply()
                    }
                }
            }

            // Play surprise sound
            soundManager.play(chosen.soundKey)

            // Jump animation
            box.surpriseProgress = 1.0f
            delay(2000L) // Dance for 2 sec

            // 4. Hide back & close door
            box.surpriseProgress = 0f
            box.currentSurprise = null
            box.isLidOpen = false
            soundManager.play("door_close")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Vector Room Background
        SceneBackground(theme = SceneTheme.ROOM)

        // 2. Harmless background decor
        DecorLayer(items = roomDecors)

        // 3. Interactive Boxes Grid (2x2 or 1x3)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 90.dp, start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val rows = if (ageProfile.isToddler2) 1 else 2
            val cols = if (ageProfile.isToddler2) 3 else 2
            val boxSizeDp = if (ageProfile.isToddler2) 110.dp else 140.dp

            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (c in 0 until cols) {
                        val idx = r * cols + c
                        if (idx < boxes.size) {
                            val box = boxes[idx]
                            InteractiveGiftBoxCard(
                                box = box,
                                sizeDp = boxSizeDp,
                                haptics = haptics,
                                onClick = { openBox(box) }
                            )
                        }
                    }
                }
            }
        }

        // 4. Top Right: "Альбом" Button (72 dp)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(72.dp)
                .border(BorderStroke(3.dp, ColorInk), CircleShape)
                .background(ColorYellow, CircleShape)
                .clip(CircleShape)
                .plasticineClickable(haptics = haptics) {
                    soundManager.play("tap")
                    isAlbumOpen = true
                }
                .testTag("boxes_album_btn")
        ) {
            Text(text = "📖", fontSize = 32.sp)
        }

        // 5. Album Dialog with Stickers & Sounds
        if (isAlbumOpen) {
            Dialog(onDismissRequest = { isAlbumOpen = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .background(ColorWhite, ShapeCard)
                        .border(BorderStroke(3.5.dp, ColorInk), ShapeCard)
                        .padding(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "📖 ⭐", fontSize = 32.sp)

                            // Close Button (64 dp)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(ColorBgSoft, CircleShape)
                                    .border(BorderStroke(2.5.dp, ColorInk), CircleShape)
                                    .plasticineClickable(haptics = haptics) {
                                        soundManager.play("tap")
                                        isAlbumOpen = false
                                    }
                            ) {
                                Text(text = "✖", fontSize = 24.sp, color = ColorInk)
                            }
                        }

                        // Grid of 10 (or 6) surprise stickers
                        val maxSurprises = if (ageProfile.isToddler2) 6 else 10
                        val catalog = BoxSurpriseCatalog.ALL_SURPRISES.take(maxSurprises)

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.heightIn(max = 340.dp)
                        ) {
                            items(catalog) { surprise ->
                                val isUnlocked = surprise.id in unlockedSurpriseIds
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(76.dp)
                                        .border(
                                            BorderStroke(if (isUnlocked) 3.dp else 1.5.dp, ColorInk),
                                            RoundedCornerShape(16.dp)
                                        )
                                        .background(
                                            if (isUnlocked) Color(0xFFFFF9C4) else Color(0xFFE0E0E0),
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clip(RoundedCornerShape(16.dp))
                                        .plasticineClickable(haptics = haptics) {
                                            if (isUnlocked) {
                                                soundManager.play(surprise.soundKey)
                                                haptics.vibrateShort()
                                            }
                                        }
                                ) {
                                    if (isUnlocked) {
                                        Text(text = surprise.emoji, fontSize = 36.sp)
                                    } else {
                                        Text(text = "❓", fontSize = 28.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated Box Component with Lid and Surprise popping out.
 */
@Composable
private fun InteractiveGiftBoxCard(
    box: GiftBoxItem,
    sizeDp: androidx.compose.ui.unit.Dp,
    haptics: Haptics,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "boxIdle_${box.id}")
    val idleWobble by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400 + box.id * 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "boxWobble"
    )

    val currentShake = if (box.isShaking) 8f else idleWobble

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(sizeDp)
            .graphicsLayer { rotationZ = currentShake }
            .plasticineClickable(haptics = haptics) {
                onClick()
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val inkStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

            // 1. Box Bottom Body
            val bodyH = h * 0.70f
            drawRoundRect(
                color = box.baseColor,
                topLeft = Offset(w * 0.08f, h - bodyH),
                size = Size(w * 0.84f, bodyH),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
            )
            drawRoundRect(
                color = ColorInk,
                topLeft = Offset(w * 0.08f, h - bodyH),
                size = Size(w * 0.84f, bodyH),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                style = inkStroke
            )

            // Ribbon vertical
            val ribW = w * 0.20f
            drawRect(color = box.ribbonColor, topLeft = Offset(w / 2f - ribW / 2f, h - bodyH), size = Size(ribW, bodyH))

            // 2. Surprise Graphic popping out if open
            box.currentSurprise?.let { surprise ->
                val popY = (h - bodyH) - (bodyH * 0.5f)
                drawCircle(color = ColorWhite, radius = 28.dp.toPx(), center = Offset(w / 2f, popY))
                drawCircle(color = ColorInk, radius = 28.dp.toPx(), center = Offset(w / 2f, popY), style = Stroke(width = 2.5.dp.toPx()))
            }

            // 3. Box Lid
            val lidH = h * 0.22f
            val lidY = if (box.isLidOpen) (h - bodyH) - lidH * 1.5f else (h - bodyH - lidH * 0.4f)
            val lidRot = if (box.isLidOpen) -25f else 0f

            rotate(lidRot, pivot = Offset(w * 0.05f, lidY)) {
                drawRoundRect(
                    color = box.baseColor,
                    topLeft = Offset(w * 0.04f, lidY),
                    size = Size(w * 0.92f, lidH),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                )
                drawRoundRect(
                    color = ColorInk,
                    topLeft = Offset(w * 0.04f, lidY),
                    size = Size(w * 0.92f, lidH),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                    style = inkStroke
                )

                // Bow on lid
                drawCircle(color = box.ribbonColor, radius = 12.dp.toPx(), center = Offset(w / 2f, lidY))
                drawCircle(color = ColorInk, radius = 12.dp.toPx(), center = Offset(w / 2f, lidY), style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}
