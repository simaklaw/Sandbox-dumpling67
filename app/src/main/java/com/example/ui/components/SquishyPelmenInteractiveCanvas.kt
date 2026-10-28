package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import com.example.model.GameState
import com.example.model.PelmenAccessoryCatalog
import com.example.model.SquishyMaterialType
import com.example.physics.SquishyContact
import com.example.physics.SquishyPhysicsEngine
import com.example.ui.util.ToddlerPalmRejection
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Particle emitted during squishing, pinching, poking or releasing the squishy dough.
 */
data class SquishySparkle(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    val emoji: String? = null,
    val color: Color = Color(0xFFFFD54F),
    val isDroplet: Boolean = false,
    var alpha: Float = 1.0f
)

/**
 * Interactive Soft-Body Squishy Pelmen-Six Canvas Component with Real-Time 2D Mass-Spring Physics,
 * Incompressible Fluid/Dough Hydrostatic Bulge, Multi-Touch Pinch/Stretch, and 3D Canvas Rendering.
 */
@Composable
fun SquishyPelmenInteractiveCanvas(
    modifier: Modifier = Modifier,
    gameState: GameState,
    materialType: SquishyMaterialType,
    soundPlayer: SoundEffectPlayer,
    onSquishRecorded: () -> Unit,
    onIntensityChanged: (Float) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current
    val physicsEngine = remember {
        SquishyPhysicsEngine(outerNodeCount = 32, innerNodeCount = 16, baseRadius = 135f)
    }

    // Update material physical parameters when user selects a different material
    LaunchedEffect(materialType) {
        physicsEngine.applyMaterialPhysics(
            stiffness = materialType.springStiffness,
            dampingVal = materialType.damping,
            pressure = materialType.internalPressure,
            bounce = materialType.bounciness
        )
    }

    // Active touch contacts tracking with previous coordinates for velocity & drag
    val activeContacts = remember { mutableStateListOf<SquishyContact>() }
    val sparkles = remember { mutableStateListOf<SquishySparkle>() }

    var currentIntensity by remember { mutableFloatStateOf(0f) }
    var previousIntensity by remember { mutableFloatStateOf(0f) }

    // Idle gentle breathing floating motion
    val infiniteTransition = rememberInfiniteTransition(label = "pelmen_idle_breathing")
    val idleBreathing by infiniteTransition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_breathing_offset"
    )

    // Main real-time physics integration loop (60-120 FPS frame synchronization)
    LaunchedEffect(Unit) {
        var lastNanos = System.nanoTime()
        var lastSquishSoundTimeMs = 0L
        var reportedIntensity = 0f

        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                val dt = ((frameTimeNanos - lastNanos) / 1_000_000_000f).coerceIn(0.004f, 0.033f)
                lastNanos = frameTimeNanos

                // Step soft-body physics engine
                physicsEngine.step(
                    dt = dt,
                    contacts = activeContacts.toList(),
                    containerCenter = Offset.Zero
                )

                currentIntensity = physicsEngine.squishIntensity
                if (kotlin.math.abs(currentIntensity - reportedIntensity) >= 0.03f || (currentIntensity < 0.01f && reportedIntensity != 0f)) {
                    reportedIntensity = if (currentIntensity < 0.01f) 0f else currentIntensity
                    onIntensityChanged(reportedIntensity)
                }

                // Sound & Haptic triggers with debouncing & hysteresis to prevent 60fps audio saturation
                val now = System.currentTimeMillis()
                if (currentIntensity > 0.25f && previousIntensity <= 0.20f && (now - lastSquishSoundTimeMs) > 130L) {
                    lastSquishSoundTimeMs = now
                    soundPlayer.performSquishHaptic(currentIntensity, view)
                    soundPlayer.playSquishElastic(Random.nextFloat() * 0.25f + 0.95f)
                    onSquishRecorded()
                } else if (currentIntensity > 0.70f && previousIntensity <= 0.58f && (now - lastSquishSoundTimeMs) > 160L) {
                    lastSquishSoundTimeMs = now
                    soundPlayer.performSquishHaptic(1.0f, view)
                    soundPlayer.playSqueak()
                    onSquishRecorded()
                }
                previousIntensity = currentIntensity

                // Update sparkle and droplet particles smoothly on the same frame clock
                if (sparkles.isNotEmpty()) {
                    for (i in sparkles.indices.reversed()) {
                        val p = sparkles[i]
                        p.x += p.vx
                        p.y += p.vy
                        p.vx *= 0.98f
                        p.vy += 0.32f // gentle gravity
                        p.size -= 0.32f
                        p.alpha = (p.size / 18f).coerceIn(0f, 1f)

                        if (p.size <= 0.5f || p.alpha <= 0.02f) {
                            sparkles.removeAt(i)
                        }
                    }
                }
            }
        }
    }

    // Equipped accessories from GameState
    val equippedHat = PelmenAccessoryCatalog.getById(gameState.equippedHatId)
    val equippedGlasses = PelmenAccessoryCatalog.getById(gameState.equippedGlassesId)
    val equippedBody = PelmenAccessoryCatalog.getById(gameState.equippedBodyId)

    val density = LocalDensity.current
    val edgeMarginPx = remember(gameState.palmRejectionMarginDp, density) {
        with(density) { gameState.palmRejectionMarginDp.dp.toPx() }
    }

    Box(
        modifier = modifier
            .pointerInput(gameState.palmRejectionEnabled, edgeMarginPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    val downPos = down.position
                    val containerSize = Size(size.width.toFloat(), size.height.toFloat())

                    val isEdgeAccidental = gameState.palmRejectionEnabled &&
                            ToddlerPalmRejection.isNearEdge(downPos, containerSize, edgeMarginPx)

                    val centerOffset = Offset(size.width / 2f, size.height / 2f)
                    val localDown = downPos - centerOffset

                    if (!isEdgeAccidental) {
                        // Add primary touch contact
                        activeContacts.clear()
                        activeContacts.add(
                            SquishyContact(
                                id = pointerId.value,
                                x = localDown.x,
                                y = localDown.y,
                                previousX = localDown.x,
                                previousY = localDown.y,
                                pressure = 1.0f,
                                radius = 110f
                            )
                        )

                        soundPlayer.performSquishHaptic(0.6f, view)
                        soundPlayer.playSquish(Random.nextFloat() * 0.3f + 1.0f)
                        onSquishRecorded()

                        // Spawn initial touch sparkles & broth micro-droplets
                        repeat(6) { idx ->
                            sparkles.add(
                                SquishySparkle(
                                    id = System.nanoTime() + idx,
                                    x = localDown.x + (Random.nextFloat() - 0.5f) * 45f,
                                    y = localDown.y + (Random.nextFloat() - 0.5f) * 45f,
                                    vx = (Random.nextFloat() - 0.5f) * 8f,
                                    vy = (Random.nextFloat() - 0.9f) * 8f,
                                    size = Random.nextFloat() * 8f + 14f,
                                    emoji = materialType.particleEmojis.random(),
                                    color = materialType.rimColor
                                )
                            )
                        }
                    }

                    var previousPointers = listOf(down)

                    // Multi-touch drag, pinch & move loop with palm rejection
                    while (true) {
                        val event = awaitPointerEvent()
                        val filteredPointers = ToddlerPalmRejection.filterIntentionalChanges(
                            changes = event.changes,
                            containerSize = containerSize,
                            edgeMarginPx = edgeMarginPx,
                            enabled = gameState.palmRejectionEnabled
                        )
                        val currentPointers = filteredPointers.filter { it.pressed }

                        if (currentPointers.isEmpty()) {
                            // Touch released! Trigger release boing & wobble
                            activeContacts.clear()
                            soundPlayer.vibrateBoingRelease()
                            soundPlayer.playBoing(Random.nextFloat() * 0.3f + 1.0f)
                            physicsEngine.triggerImpulse(intensity = 0.85f)

                            // Slingshot fling detection when pulled far and released
                            val pullDist = hypot(physicsEngine.centerX, physicsEngine.centerY)
                            if (pullDist > 75f) {
                                soundPlayer.playSpeedDashWhoosh()
                                soundPlayer.playBoing(1.4f)
                                onSquishRecorded()
                                repeat(16) { idx ->
                                    sparkles.add(
                                        SquishySparkle(
                                            id = System.nanoTime() + idx,
                                            x = (Random.nextFloat() - 0.5f) * 200f,
                                            y = (Random.nextFloat() - 0.5f) * 200f,
                                            vx = (Random.nextFloat() - 0.5f) * 16f,
                                            vy = (Random.nextFloat() - 1.4f) * 14f,
                                            size = Random.nextFloat() * 12f + 20f,
                                            emoji = listOf("⭐", "✨", "💫", "🥟", "💖", "🪙").random(),
                                            color = materialType.highlightColor
                                        )
                                    )
                                }
                            } else {
                                // Normal release burst particles
                                repeat(10) { idx ->
                                    sparkles.add(
                                        SquishySparkle(
                                            id = System.nanoTime() + idx,
                                            x = (Random.nextFloat() - 0.5f) * 170f,
                                            y = (Random.nextFloat() - 0.5f) * 170f,
                                            vx = (Random.nextFloat() - 0.5f) * 10f,
                                            vy = (Random.nextFloat() - 1.2f) * 9f,
                                            size = Random.nextFloat() * 10f + 16f,
                                            emoji = materialType.particleEmojis.random(),
                                            color = materialType.highlightColor
                                        )
                                    )
                                }
                            }
                            break
                        }

                        // Map all active pointers with previous coordinates for velocity transfer
                        val newContacts = mutableListOf<SquishyContact>()
                        for (change in currentPointers) {
                            val localPos = change.position - centerOffset
                            val prevChange = previousPointers.find { it.id == change.id }
                            val prevLocalPos = if (prevChange != null) {
                                prevChange.position - centerOffset
                            } else localPos

                            newContacts.add(
                                SquishyContact(
                                    id = change.id.value,
                                    x = localPos.x,
                                    y = localPos.y,
                                    previousX = prevLocalPos.x,
                                    previousY = prevLocalPos.y,
                                    pressure = 1.25f,
                                    radius = 120f
                                )
                            )
                            change.consume()
                        }

                        activeContacts.clear()
                        activeContacts.addAll(newContacts)
                        previousPointers = currentPointers
                    }
                }
            }
            .testTag("squishy_physics_pelmen_canvas"),
        contentAlignment = Alignment.Center
    ) {
        val textMeasurer = rememberTextMeasurer()

        // 1. Main Physical Dumpling Canvas with 3D Shader Rendering
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f + idleBreathing)
            val scale = (size.width / 320f).coerceIn(0.85f, 1.25f)

            // A. Dynamic Multi-Layer Floor Contact Shadow with Deformed Silhouette
            val shadowCenter = Offset(
                center.x + physicsEngine.centerX * scale * 0.85f,
                center.y + 130f * scale + physicsEngine.centerY * scale * 0.22f
            )
            val shadowWidth = (245f + currentIntensity * 75f) * scale
            val shadowHeight = (46f - currentIntensity * 18f).coerceAtLeast(18f) * scale

            // Outer soft ambient shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x38000000), Color(0x12000000), Color(0x00000000)),
                    center = shadowCenter,
                    radius = shadowWidth / 2f
                ),
                topLeft = Offset(shadowCenter.x - shadowWidth / 2f, shadowCenter.y - shadowHeight / 2f),
                size = Size(shadowWidth, shadowHeight)
            )

            // Inner dense contact shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x5A000000), Color(0x00000000)),
                    center = shadowCenter,
                    radius = shadowWidth * 0.36f
                ),
                topLeft = Offset(shadowCenter.x - shadowWidth * 0.36f, shadowCenter.y - shadowHeight * 0.36f),
                size = Size(shadowWidth * 0.72f, shadowHeight * 0.72f)
            )

            // B. Deformed Soft-Body Contour Path from Mass-Spring Lattice
            val bodyPath = physicsEngine.createCurvedPath(center = center, scale = scale)

            // 3D-Lit Shader Fill Gradient (Light source from top-left)
            val lightSourceCenter = Offset(
                center.x + physicsEngine.centerX * scale - 20f * scale,
                center.y + physicsEngine.centerY * scale - 35f * scale
            )
            drawPath(
                path = bodyPath,
                brush = Brush.radialGradient(
                    colors = materialType.bodyColors,
                    center = lightSourceCenter,
                    radius = 180f * scale
                )
            )

            // Outer outline stroke
            drawPath(
                path = bodyPath,
                color = materialType.rimColor,
                style = Stroke(width = 6.5f * scale)
            )

            // Dynamic Touch Pressure Ripples under fingers
            for (contact in activeContacts) {
                val contactCenter = Offset(center.x + contact.x * scale, center.y + contact.y * scale)
                val rippleRadius = (45f + contact.pressure * 15f) * scale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            materialType.highlightColor.copy(alpha = 0.50f),
                            materialType.blushColor.copy(alpha = 0.20f),
                            Color.Transparent
                        ),
                        center = contactCenter,
                        radius = rippleRadius
                    ),
                    radius = rippleRadius,
                    center = contactCenter
                )
                drawCircle(
                    color = materialType.rimColor.copy(alpha = 0.35f),
                    radius = rippleRadius * 0.65f,
                    center = contactCenter,
                    style = Stroke(width = 2.5f * scale)
                )
            }

            // C. 3D Specular Sheen (Curved Highlights that deform on top curve)
            val highlightPath = Path().apply {
                val topNode = physicsEngine.allNodes[24 % physicsEngine.outerNodeCount]
                val leftNode = physicsEngine.allNodes[29 % physicsEngine.outerNodeCount]
                val rightNode = physicsEngine.allNodes[19 % physicsEngine.outerNodeCount]

                val pLeft = Offset(
                    center.x + (physicsEngine.centerX + leftNode.currentX * 0.76f) * scale,
                    center.y + (physicsEngine.centerY + leftNode.currentY * 0.76f) * scale
                )
                val pTop = Offset(
                    center.x + (physicsEngine.centerX + topNode.currentX * 0.80f) * scale,
                    center.y + (physicsEngine.centerY + topNode.currentY * 0.80f) * scale
                )
                val pRight = Offset(
                    center.x + (physicsEngine.centerX + rightNode.currentX * 0.76f) * scale,
                    center.y + (physicsEngine.centerY + rightNode.currentY * 0.76f) * scale
                )

                moveTo(pLeft.x, pLeft.y)
                quadraticBezierTo(pTop.x, pTop.y, pRight.x, pRight.y)
                quadraticBezierTo(pTop.x, pTop.y + 14f * scale, pLeft.x, pLeft.y)
                close()
            }
            drawPath(
                path = highlightPath,
                color = materialType.highlightColor
            )

            // D. Fluted Top Crest Fold Lines (dumpling pleats/folds that flex with physics)
            val crestFolds = physicsEngine.getCrestFoldLines(center = center, scale = scale)
            for (fold in crestFolds) {
                drawPath(
                    path = fold,
                    color = materialType.rimColor.copy(alpha = 0.65f),
                    style = Stroke(width = 3.8f * scale)
                )
            }

            // E. Signature "67" Belly Emblem Badge
            val emblemPos = physicsEngine.getEmblemAnchor(center = center, scale = scale)
            val emblemRadius = (35f - currentIntensity * 6f).coerceAtLeast(18f) * scale

            drawCircle(
                brush = Brush.radialGradient(
                    colors = materialType.emblemBgColors,
                    center = emblemPos,
                    radius = emblemRadius
                ),
                radius = emblemRadius,
                center = emblemPos
            )
            drawCircle(
                color = materialType.emblemTextColor.copy(alpha = 0.85f),
                radius = emblemRadius,
                center = emblemPos,
                style = Stroke(width = 3.5f * scale)
            )

            val layoutResult = textMeasurer.measure(
                text = "67",
                style = TextStyle(
                    fontSize = (22f * scale).sp,
                    fontWeight = FontWeight.Black,
                    color = materialType.emblemTextColor
                )
            )
            drawText(
                textLayoutResult = layoutResult,
                topLeft = Offset(
                    emblemPos.x - layoutResult.size.width / 2f,
                    emblemPos.y - layoutResult.size.height / 2f
                )
            )

            // F. Rosy Blushing Cheeks (inflate and glow brighter under squish!)
            val (leftCheek, rightCheek) = physicsEngine.getCheekAnchors(center = center, scale = scale)
            val cheekRadius = (22f + currentIntensity * 14f) * scale
            val cheekAlpha = (0.65f + currentIntensity * 0.35f).coerceIn(0f, 1f)

            drawCircle(
                color = materialType.blushColor.copy(alpha = cheekAlpha),
                radius = cheekRadius,
                center = leftCheek
            )
            drawCircle(
                color = materialType.blushColor.copy(alpha = cheekAlpha),
                radius = cheekRadius,
                center = rightCheek
            )

            // Cheek Sparkle accents
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = 4.5f * scale,
                center = Offset(leftCheek.x - 5f * scale, leftCheek.y - 5f * scale)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = 4.5f * scale,
                center = Offset(rightCheek.x + 5f * scale, rightCheek.y - 5f * scale)
            )

            // G. Interactive Kawaii Eyes with Pupil Tracking & Reactive Expressions
            val (leftEyeAnchor, rightEyeAnchor) = physicsEngine.getEyeAnchors(center = center, scale = scale)
            val gaze = Offset(physicsEngine.pupilGazeX * scale, physicsEngine.pupilGazeY * scale)

            drawKawaiiFacialFeatures(
                center = center,
                scale = scale,
                leftEyePos = leftEyeAnchor,
                rightEyePos = rightEyeAnchor,
                gazeOffset = gaze,
                squishIntensity = currentIntensity,
                rimColor = materialType.rimColor
            )

            // H. Interactive Sparkle Particles & Micro-Droplets
            for (p in sparkles) {
                if (p.emoji != null) {
                    val pLayout = textMeasurer.measure(
                        text = p.emoji,
                        style = TextStyle(fontSize = (p.size * 1.4f * scale).sp)
                    )
                    drawText(
                        textLayoutResult = pLayout,
                        topLeft = Offset(center.x + p.x * scale, center.y + p.y * scale),
                        alpha = p.alpha
                    )
                } else {
                    drawCircle(
                        color = p.color.copy(alpha = p.alpha),
                        radius = p.size * 0.6f * scale,
                        center = Offset(center.x + p.x * scale, center.y + p.y * scale)
                    )
                }
            }
        }

        // 3. Equipped Accessories Overlays (Hat, Glasses, Body) positioned on deformed anchor points
        val center = Offset(0f, idleBreathing)
        val scale = 1.0f

        // Body accessory (bowtie, cape, chain, scarf)
        if (equippedBody != null && equippedBody.id != "body_none") {
            val emblemPos = physicsEngine.getEmblemAnchor(center = center, scale = scale)
            Text(
                text = equippedBody.emoji,
                fontSize = 54.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset {
                        IntOffset(
                            (emblemPos.x).roundToInt(),
                            (emblemPos.y - 58f).roundToInt()
                        )
                    }
            )
        }

        // Glasses accessory (cool sunglasses, 3D, heart shades, nerd)
        if (equippedGlasses != null && equippedGlasses.id != "glasses_none") {
            val (leftEye, rightEye) = physicsEngine.getEyeAnchors(center = center, scale = scale)
            val midEyes = Offset((leftEye.x + rightEye.x) / 2f, (leftEye.y + rightEye.y) / 2f)
            Text(
                text = equippedGlasses.emoji,
                fontSize = 66.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset {
                        IntOffset(
                            midEyes.x.roundToInt(),
                            (midEyes.y - 12f).roundToInt()
                        )
                    }
            )
        }

        // Hat accessory (crown, chef, tophat, party, beanie)
        if (equippedHat != null && equippedHat.id != "hat_none") {
            val hatPos = physicsEngine.getHatAnchor(center = center, scale = scale)
            Text(
                text = equippedHat.emoji,
                fontSize = 72.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset {
                        IntOffset(
                            hatPos.x.roundToInt(),
                            hatPos.y.roundToInt()
                        )
                    }
            )
        }
    }
}

/**
 * Draws expressive kawaii facial features that smoothly react to squish intensity and gaze tracking.
 */
private fun DrawScope.drawKawaiiFacialFeatures(
    center: Offset,
    scale: Float,
    leftEyePos: Offset,
    rightEyePos: Offset,
    gazeOffset: Offset,
    squishIntensity: Float,
    rimColor: Color
) {
    val eyeRadius = 16f * scale
    val pupilRadius = 7f * scale
    val mouthY = (leftEyePos.y + rightEyePos.y) / 2f + 26f * scale
    val mouthCenter = Offset((leftEyePos.x + rightEyePos.x) / 2f, mouthY)

    when {
        // ULTRA-SQUISH (> 75%): Star Eyes & Huge Joyous Laughing Mouth!
        squishIntensity > 0.75f -> {
            drawSparkleStarEye(leftEyePos, eyeRadius * 1.25f, Color(0xFFFFD600))
            drawSparkleStarEye(rightEyePos, eyeRadius * 1.25f, Color(0xFFFFD600))

            // Wide open happy laughing mouth with tongue
            val wideMouth = Path().apply {
                moveTo(mouthCenter.x - 26f * scale, mouthCenter.y)
                quadraticBezierTo(
                    mouthCenter.x, mouthCenter.y + 36f * scale,
                    mouthCenter.x + 26f * scale, mouthCenter.y
                )
                close()
            }
            drawPath(wideMouth, color = Color(0xFF3E2723))
            drawCircle(
                color = Color(0xFFFF5252),
                radius = 12f * scale,
                center = Offset(mouthCenter.x, mouthCenter.y + 18f * scale)
            )
        }

        // MEDIUM SQUISH (40% - 75%): Silly Cute Squint `( > ∇ < )` with tongue out!
        squishIntensity > 0.40f -> {
            // Left Squint Arch `>`
            val leftSquint = Path().apply {
                moveTo(leftEyePos.x - 14f * scale, leftEyePos.y - 10f * scale)
                lineTo(leftEyePos.x + 10f * scale, leftEyePos.y)
                lineTo(leftEyePos.x - 14f * scale, leftEyePos.y + 10f * scale)
            }
            drawPath(leftSquint, color = Color(0xFF212121), style = Stroke(width = 6.5f * scale))

            // Right Squint Arch `<`
            val rightSquint = Path().apply {
                moveTo(rightEyePos.x + 14f * scale, rightEyePos.y - 10f * scale)
                lineTo(rightEyePos.x - 10f * scale, rightEyePos.y)
                lineTo(rightEyePos.x + 14f * scale, rightEyePos.y + 10f * scale)
            }
            drawPath(rightSquint, color = Color(0xFF212121), style = Stroke(width = 6.5f * scale))

            // Happy tongue mouth
            val happyMouth = Path().apply {
                moveTo(mouthCenter.x - 18f * scale, mouthCenter.y)
                quadraticBezierTo(
                    mouthCenter.x, mouthCenter.y + 26f * scale,
                    mouthCenter.x + 18f * scale, mouthCenter.y
                )
            }
            drawPath(happyMouth, color = Color(0xFF3E2723), style = Stroke(width = 5.5f * scale))
            drawCircle(
                color = Color(0xFFFF5252),
                radius = 9f * scale,
                center = Offset(mouthCenter.x + 4f * scale, mouthCenter.y + 14f * scale)
            )
        }

        // GENTLE / REST (< 40%): Big Sparkly Anime Kawaii Eyes with Live Touch Tracking!
        else -> {
            // Left Eye Sclera & Pupil
            drawCircle(color = Color(0xFF212121), radius = eyeRadius, center = leftEyePos)
            val leftPupilCenter = Offset(leftEyePos.x + gazeOffset.x * 0.7f, leftEyePos.y + gazeOffset.y * 0.7f)
            drawCircle(color = Color.White, radius = pupilRadius, center = Offset(leftPupilCenter.x - 4f * scale, leftPupilCenter.y - 4f * scale))
            drawCircle(color = Color.White, radius = pupilRadius * 0.45f, center = Offset(leftPupilCenter.x + 4f * scale, leftPupilCenter.y + 4f * scale))

            // Right Eye Sclera & Pupil
            drawCircle(color = Color(0xFF212121), radius = eyeRadius, center = rightEyePos)
            val rightPupilCenter = Offset(rightEyePos.x + gazeOffset.x * 0.7f, rightEyePos.y + gazeOffset.y * 0.7f)
            drawCircle(color = Color.White, radius = pupilRadius, center = Offset(rightPupilCenter.x - 4f * scale, rightPupilCenter.y - 4f * scale))
            drawCircle(color = Color.White, radius = pupilRadius * 0.45f, center = Offset(rightPupilCenter.x + 4f * scale, rightPupilCenter.y + 4f * scale))

            // Sweet gentle smile
            val gentleSmile = Path().apply {
                moveTo(mouthCenter.x - 16f * scale, mouthCenter.y - 2f * scale)
                quadraticBezierTo(
                    mouthCenter.x, mouthCenter.y + 18f * scale,
                    mouthCenter.x + 16f * scale, mouthCenter.y - 2f * scale
                )
            }
            drawPath(gentleSmile, color = Color(0xFF3E2723), style = Stroke(width = 5.5f * scale))
        }
    }
}

/**
 * Draws a 4-point sparkle star for ecstatic super-squish expression.
 */
private fun DrawScope.drawSparkleStarEye(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - size)
        quadraticBezierTo(center.x, center.y, center.x + size, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y + size)
        quadraticBezierTo(center.x, center.y, center.x - size, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y - size)
        close()
    }
    drawPath(path, color = color)
    drawPath(path, color = Color(0xFF3E2723), style = Stroke(width = 3.5f))
    drawCircle(color = Color.White, radius = size * 0.28f, center = Offset(center.x - size * 0.2f, center.y - size * 0.2f))
}
