package com.example.shared.scene

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import com.example.shared.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shared.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 🦋 Interactive DecorLayer:
 * Renders harmless, adorable background decor elements that react to child touch.
 * Features automatic collision avoidance (>= 16 dp from working buttons).
 */
@Composable
fun DecorLayer(
    items: List<DecorSpec>,
    avoid: List<Rect> = emptyList(),
    controller: DecorController? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }
    val haptics = rememberHaptics()
    val ageProfile = LocalAgeProfile.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Filter budget by age profile (max 6 for 2yo, max 12 for 3-5yo)
    val maxDecors = if (ageProfile.isToddler2) 6 else 12
    val budgetedItems = remember(items, maxDecors) { items.take(maxDecors) }

    // Rate limiter for global decor sounds (< 3 per sec)
    var lastSoundTime by remember { mutableLongStateOf(0L) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val avoidMarginPx = with(density) { 16.dp.toPx() }

        budgetedItems.forEachIndexed { index, spec ->
            // Compute base center coordinates
            var posX = spec.xFraction * widthPx
            var posY = spec.yFraction * heightPx
            val itemSizePx = with(density) { (if (ageProfile.isToddler2) spec.sizeDp.coerceAtLeast(80f) else spec.sizeDp).dp.toPx() }
            val itemRect = Rect(
                left = posX - itemSizePx / 2f,
                top = posY - itemSizePx / 2f,
                right = posX + itemSizePx / 2f,
                bottom = posY + itemSizePx / 2f
            )

            // Collision avoidance shift if overlapping working buttons
            var isColliding = false
            for (avoidRect in avoid) {
                val expandedAvoid = avoidRect.inflate(avoidMarginPx)
                if (itemRect.overlaps(expandedAvoid)) {
                    isColliding = true
                    // Shift vertically or horizontally
                    posY = (avoidRect.bottom + avoidMarginPx + itemSizePx / 2f).coerceAtMost(heightPx - itemSizePx)
                    break
                }
            }

            // Animation & reaction states
            var lastTouchTime by remember { mutableLongStateOf(0L) }
            val reactionAnim = remember { Animatable(1.0f) }
            val rotAnim = remember { Animatable(0f) }
            var isDisappeared by remember { mutableStateOf(false) }

            // Idle floating animation
            val floatPhase = index * 0.8f
            val infiniteTransition = rememberInfiniteTransition(label = "decor_float_$index")
            val idleOffsetY by infiniteTransition.animateFloat(
                initialValue = -6f,
                targetValue = 6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(if (ageProfile.isToddler2) 3600 else 2200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "idleY"
            )

            val idleGearRot by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(14000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "idleGear"
            )

            val effectiveReaction = spec.reaction ?: when (spec.kind) {
                DecorKind.SUN -> DecorReaction.SMILE
                DecorKind.CLOUD -> DecorReaction.JUMP
                DecorKind.BUTTERFLY, DecorKind.BIRD -> DecorReaction.FLY_AWAY_AND_RETURN
                DecorKind.STAR -> DecorReaction.SPIN
                DecorKind.BALLOON -> DecorReaction.GROW_POP
                DecorKind.BALL -> DecorReaction.BOUNCE_THREE_TIMES
                DecorKind.YARN -> DecorReaction.ROLL_UNWIND
                DecorKind.KITE, DecorKind.HOT_AIR_BALLOON -> DecorReaction.FLY_UP_RETURN
                DecorKind.RAINBOW -> DecorReaction.SHIMMER_SPARKLE
                DecorKind.PLANT -> DecorReaction.PLANT_FLOWER
                DecorKind.PUMPKIN -> DecorReaction.PUMPKIN_JUMP
                DecorKind.SUNFLOWER -> DecorReaction.SUNFLOWER_NOD
                DecorKind.GEAR -> DecorReaction.GEAR_SPEED_UP
                DecorKind.GUEST -> DecorReaction.CLAP_HANDS
                DecorKind.TEAPOT -> DecorReaction.TEAPOT_STEAM
                DecorKind.CAKE -> DecorReaction.CANDLE_DANCE
                DecorKind.MOUSE -> DecorReaction.MOUSE_HIDE
                DecorKind.BULB -> DecorReaction.BULB_FLASH
                DecorKind.UFO -> DecorReaction.SHIMMER_SPARKLE
                DecorKind.MOON_FACE -> DecorReaction.SMILE
                DecorKind.PLANET -> DecorReaction.SPIN
                else -> DecorReaction.WIGGLE
            }

            fun triggerReaction(customReaction: DecorReaction? = null) {
                val now = System.currentTimeMillis()
                if (now - lastTouchTime < 350L) return // Rate limit
                lastTouchTime = now

                // Play decor sound if < 3 per sec
                if (now - lastSoundTime > 320L) {
                    lastSoundTime = now
                    val soundKey = spec.soundName ?: spec.kind.defaultSound
                    val played = soundManager.play(soundKey)
                    if (!played) soundManager.play("pop")
                    haptics.vibrateShort()
                }

                val activeReaction = customReaction ?: effectiveReaction
                scope.launch {
                    when (activeReaction) {
                        DecorReaction.WIGGLE -> {
                            rotAnim.animateTo(18f, tween(80))
                            rotAnim.animateTo(-18f, tween(100))
                            rotAnim.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                        }
                        DecorReaction.JUMP -> {
                            reactionAnim.animateTo(1.30f, spring(Spring.DampingRatioMediumBouncy))
                            reactionAnim.animateTo(1.0f, spring(Spring.DampingRatioHighBouncy))
                        }
                        DecorReaction.SMILE -> {
                            reactionAnim.animateTo(1.22f, tween(120))
                            reactionAnim.animateTo(1.0f, spring(Spring.DampingRatioHighBouncy))
                        }
                        DecorReaction.SPIN -> {
                            rotAnim.animateTo(360f, tween(500, easing = LinearOutSlowInEasing))
                            rotAnim.snapTo(0f)
                        }
                        DecorReaction.GROW_POP -> {
                            reactionAnim.animateTo(1.4f, tween(120))
                            isDisappeared = true
                            soundManager.play("pop")
                            delay(1200)
                            isDisappeared = false
                            reactionAnim.snapTo(1.0f)
                        }
                        DecorReaction.FLY_AWAY_AND_RETURN -> {
                            reactionAnim.animateTo(0f, tween(300))
                            isDisappeared = true
                            delay(2500)
                            isDisappeared = false
                            reactionAnim.animateTo(1.0f, spring(Spring.DampingRatioMediumBouncy))
                        }
                        DecorReaction.BOUNCE_THREE_TIMES -> {
                            reactionAnim.animateTo(1.35f, tween(100))
                            reactionAnim.animateTo(0.95f, tween(100))
                            reactionAnim.animateTo(1.20f, tween(90))
                            reactionAnim.animateTo(0.98f, tween(90))
                            reactionAnim.animateTo(1.08f, tween(80))
                            reactionAnim.animateTo(1.0f, spring(Spring.DampingRatioMediumBouncy))
                        }
                        DecorReaction.ROLL_UNWIND -> {
                            rotAnim.animateTo(360f, tween(600, easing = FastOutSlowInEasing))
                            rotAnim.snapTo(0f)
                        }
                        DecorReaction.FLY_UP_RETURN -> {
                            reactionAnim.animateTo(1.25f, tween(250, easing = FastOutSlowInEasing))
                            rotAnim.animateTo(12f, tween(200))
                            delay(400)
                            rotAnim.animateTo(0f, tween(250))
                            reactionAnim.animateTo(1.0f, spring(Spring.DampingRatioMediumBouncy))
                        }
                        DecorReaction.SHIMMER_SPARKLE -> {
                            reactionAnim.animateTo(1.18f, tween(200))
                            rotAnim.animateTo(6f, tween(150))
                            rotAnim.animateTo(-6f, tween(150))
                            rotAnim.animateTo(0f, tween(150))
                            reactionAnim.animateTo(1.0f, spring(Spring.DampingRatioHighBouncy))
                        }
                        DecorReaction.PLANT_FLOWER -> {
                            reactionAnim.animateTo(1.30f, spring(Spring.DampingRatioMediumBouncy))
                            soundManager.play("chime")
                            delay(200)
                            reactionAnim.animateTo(1.0f, spring(Spring.DampingRatioHighBouncy))
                        }
                        DecorReaction.PUMPKIN_JUMP -> {
                            reactionAnim.animateTo(1.35f, spring(Spring.DampingRatioMediumBouncy))
                            rotAnim.animateTo(12f, tween(80))
                            rotAnim.animateTo(-12f, tween(80))
                            rotAnim.animateTo(0f, spring(Spring.DampingRatioHighBouncy))
                            reactionAnim.animateTo(1.0f, spring(Spring.DampingRatioHighBouncy))
                        }
                        DecorReaction.SUNFLOWER_NOD -> {
                            rotAnim.animateTo(18f, tween(150))
                            reactionAnim.animateTo(1.15f, tween(150))
                            rotAnim.animateTo(-10f, tween(150))
                            rotAnim.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                            reactionAnim.animateTo(1.0f, spring(Spring.DampingRatioMediumBouncy))
                        }
                        DecorReaction.GEAR_SPEED_UP -> {
                            rotAnim.animateTo(rotAnim.targetValue + 720f, tween(1000, easing = LinearOutSlowInEasing))
                        }
                        DecorReaction.CLAP_HANDS -> {
                            repeat(3) {
                                reactionAnim.animateTo(1.25f, tween(120))
                                reactionAnim.animateTo(0.95f, tween(120))
                            }
                            reactionAnim.animateTo(1.0f, spring(Spring.DampingRatioMediumBouncy))
                        }
                        DecorReaction.TEAPOT_STEAM -> {
                            reactionAnim.animateTo(1.22f, tween(200))
                            rotAnim.animateTo(8f, tween(100))
                            rotAnim.animateTo(-8f, tween(100))
                            rotAnim.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                            reactionAnim.animateTo(1.0f, spring(Spring.DampingRatioMediumBouncy))
                        }
                        DecorReaction.CANDLE_DANCE -> {
                            reactionAnim.animateTo(1.20f, tween(150))
                            rotAnim.animateTo(14f, tween(120))
                            rotAnim.animateTo(-14f, tween(120))
                            rotAnim.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                            reactionAnim.animateTo(1.0f, spring(Spring.DampingRatioMediumBouncy))
                        }
                        DecorReaction.MOUSE_HIDE -> {
                            reactionAnim.animateTo(0.2f, tween(200))
                            isDisappeared = true
                            delay(3000L)
                            isDisappeared = false
                            reactionAnim.animateTo(1.0f, spring(Spring.DampingRatioMediumBouncy))
                        }
                        DecorReaction.BULB_FLASH -> {
                            reactionAnim.animateTo(1.35f, tween(120))
                            delay(200)
                            reactionAnim.animateTo(1.0f, spring(Spring.DampingRatioMediumBouncy))
                        }
                    }
                }
            }

            // Register controller listener if item has id
            DisposableEffect(controller, spec.id) {
                if (controller != null && spec.id != null) {
                    val listener: (String, DecorReaction) -> Unit = { targetId, customReaction ->
                        if (targetId == spec.id) {
                            triggerReaction(customReaction)
                        }
                    }
                    controller.addListener(listener)
                    onDispose { controller.removeListener(listener) }
                } else {
                    onDispose {}
                }
            }

            if (!isDisappeared) {
                val displaySizeDp = if (ageProfile.isToddler2) spec.sizeDp.coerceAtLeast(80f) else spec.sizeDp
                val baseRot = if (spec.kind == DecorKind.GEAR) idleGearRot else 0f

                val tapModifier = if (spec.interactive) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            triggerReaction()
                        }
                    }
                } else {
                    Modifier
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(
                            x = with(density) { (posX - itemSizePx / 2f).toDp() },
                            y = with(density) { (posY - itemSizePx / 2f + idleOffsetY).toDp() }
                        )
                        .size(displaySizeDp.dp)
                        .scale(reactionAnim.value)
                        .rotate(baseRot + rotAnim.value)
                        .then(tapModifier)
                        .testTag("decor_item_${spec.kind.name.lowercase()}")
                ) {
                    Text(
                        text = spec.kind.defaultEmoji,
                        fontSize = (displaySizeDp * 0.72f).sp
                    )
                }
            }
        }
    }
}
