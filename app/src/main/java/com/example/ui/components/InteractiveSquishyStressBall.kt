package com.example.ui.components

import android.view.HapticFeedbackConstants
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.model.GameState
import com.example.model.PelmenAccessoryCatalog
import com.example.model.SquishyMaterialType
import com.example.physics.SquishyContact
import com.example.physics.SquishyPhysicsEngine
import com.example.ui.util.ToddlerPalmRejection
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

enum class StressBallShape(val title: String, val emoji: String) {
    DUMPLING("Пельмешек", "🥟"),
    STRESS_BALL("Мячик Антистресс", "🔴"),
    CAT_PAW("Кошачья Лапка", "🐾"),
    MAGIC_STAR("Звёздочка", "⭐"),
    SWEET_HEART("Сердечко", "💖"),
    JELLY_SLIME("Слайм-Пузырь", "🫧")
}

/**
 * 🥟 Reusable Interactive Squishy Stress Ball ('Сквиш-Мялка') Component.
 *
 * Implements real-time 2D mass-spring soft-body deformation physics:
 * - 32 outer perimeter springs + 16 inner radial dampeners
 * - Hydrostatic incompressible volume bulge (Pascal's law / shoelace polygon conservation)
 * - Multi-touch poke, pinch, stretch, slingshot fling, and bounce release
 * - Dynamic tactile haptics & pitch-modulated squish acoustics
 * - Expressive facial gaze tracking and squinting under pressure
 * - Integrated toddler palm rejection for edge holding stability
 */
@Composable
fun InteractiveSquishyStressBall(
    modifier: Modifier = Modifier,
    gameState: GameState,
    materialType: SquishyMaterialType = SquishyMaterialType.GOLDEN_DUMPLING,
    shapeType: StressBallShape = StressBallShape.DUMPLING,
    soundPlayer: SoundEffectPlayer,
    baseRadius: Float = 140f,
    showEmblem: Boolean = true,
    showAccessories: Boolean = true,
    onSquishRecorded: (() -> Unit)? = null,
    onIntensityChanged: ((Float) -> Unit)? = null
) {
    val view = LocalView.current
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val edgeMarginPx = remember(gameState.palmRejectionMarginDp, density) {
        with(density) { gameState.palmRejectionMarginDp.dp.toPx() }
    }

    val physicsEngine = remember(baseRadius) {
        SquishyPhysicsEngine(outerNodeCount = 32, innerNodeCount = 16, baseRadius = baseRadius)
    }

    // Material physics parameter synchronization
    LaunchedEffect(materialType) {
        physicsEngine.applyMaterialPhysics(
            stiffness = materialType.springStiffness,
            dampingVal = materialType.damping,
            pressure = materialType.internalPressure,
            bounce = materialType.bounciness
        )
    }

    val activeContacts = remember { mutableStateListOf<SquishyContact>() }
    val sparkles = remember { mutableStateListOf<SquishySparkle>() }

    var currentIntensity by remember { mutableFloatStateOf(0f) }

    // Gentle idle floating motion
    val infiniteTransition = rememberInfiniteTransition(label = "stress_ball_breathing")
    val idleBreathing by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_breathing_offset"
    )

    // Physics 60-120 FPS frame integration loop
    LaunchedEffect(Unit) {
        var lastNanos = System.nanoTime()
        var lastSquishSoundTimeMs = 0L
        var reportedIntensity = 0f

        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                val dt = ((frameTimeNanos - lastNanos) / 1_000_000_000f).coerceIn(0.004f, 0.033f)
                lastNanos = frameTimeNanos

                physicsEngine.step(
                    dt = dt,
                    contacts = activeContacts.toList(),
                    containerCenter = Offset.Zero
                )
                currentIntensity = physicsEngine.squishIntensity

                // Throttle callback to parent to avoid unnecessary recomposition churn
                if (kotlin.math.abs(currentIntensity - reportedIntensity) > 0.03f || (currentIntensity < 0.01f && reportedIntensity > 0f)) {
                    reportedIntensity = currentIntensity
                    onIntensityChanged?.invoke(currentIntensity)
                }

                // Dynamic haptics & audio modulation during continuous squishing
                if (activeContacts.isNotEmpty() && currentIntensity > 0.15f) {
                    val now = System.currentTimeMillis()
                    val interval = (280 - (currentIntensity * 160).toInt()).coerceAtLeast(90)
                    if (now - lastSquishSoundTimeMs > interval) {
                        lastSquishSoundTimeMs = now
                        soundPlayer.performSquishHaptic(currentIntensity, view)
                        soundPlayer.playSquish(0.85f + currentIntensity * 0.45f)
                    }
                }

                // Update particle sparkles physics
                for (i in sparkles.indices.reversed()) {
                    val sp = sparkles[i]
                    sp.x += sp.vx
                    sp.y += sp.vy
                    sp.vy += 0.35f // gravity
                    sp.size = (sp.size - 0.28f).coerceAtLeast(0f)
                    sp.alpha = (sp.alpha - 0.022f).coerceAtLeast(0f)
                    if (sp.size <= 0f || sp.alpha <= 0f) {
                        sparkles.removeAt(i)
                    }
                }
            }
        }
    }

    val equippedHat = if (showAccessories) PelmenAccessoryCatalog.getById(gameState.equippedHatId) else null
    val equippedGlasses = if (showAccessories) PelmenAccessoryCatalog.getById(gameState.equippedGlassesId) else null
    val equippedBody = if (showAccessories) PelmenAccessoryCatalog.getById(gameState.equippedBodyId) else null

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
                        activeContacts.clear()
                        activeContacts.add(
                            SquishyContact(
                                id = pointerId.value,
                                x = localDown.x,
                                y = localDown.y,
                                previousX = localDown.x,
                                previousY = localDown.y,
                                pressure = 1.0f,
                                radius = 115f
                            )
                        )

                        soundPlayer.performSquishHaptic(0.65f, view)
                        soundPlayer.playSquish(Random.nextFloat() * 0.3f + 1.0f)
                        onSquishRecorded?.invoke()

                        // Initial touch sparkle burst
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

                    // Multi-touch drag, pinch & deformation loop with palm rejection
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
                            // Touch released! Trigger release boing, wobble & particle burst
                            activeContacts.clear()
                            soundPlayer.vibrateBoingRelease()
                            soundPlayer.playBoing(Random.nextFloat() * 0.3f + 1.0f)
                            physicsEngine.triggerImpulse(intensity = 0.85f)

                            val pullDist = hypot(physicsEngine.centerX, physicsEngine.centerY)
                            if (pullDist > 75f) {
                                soundPlayer.playSpeedDashWhoosh()
                                soundPlayer.playBoing(1.4f)
                                onSquishRecorded?.invoke()
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

                        // Map active pointers
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
            .testTag("interactive_squishy_stress_ball")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f + idleBreathing)

            // 1. Soft Dynamic Drop Shadow under deformed soft body
            val shadowPath = Path()
            val shadowScale = (1.0f + currentIntensity * 0.35f).coerceIn(0.8f, 1.45f)
            val shadowW = baseRadius * 1.55f * shadowScale
            val shadowH = baseRadius * 0.45f / (1f + currentIntensity * 0.4f)
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.28f),
                        Color.Black.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(center.x + physicsEngine.centerX * 0.5f, center.y + baseRadius * 1.05f + physicsEngine.centerY * 0.3f),
                    radius = shadowW
                ),
                topLeft = Offset(center.x - shadowW + physicsEngine.centerX * 0.5f, center.y + baseRadius * 0.85f - shadowH),
                size = Size(shadowW * 2f, shadowH * 2f)
            )

            // 2. Render Deformed Soft-Body Pelmen Mesh Contour
            val bodyPath = Path()
            val nodes = physicsEngine.allNodes.filter { it.isPerimeter }
            if (nodes.isNotEmpty()) {
                val p0 = Offset(nodes[0].currentX + center.x, nodes[0].currentY + center.y)
                bodyPath.moveTo(p0.x, p0.y)

                for (i in nodes.indices) {
                    val nCurr = nodes[i]
                    val nNext = nodes[(i + 1) % nodes.size]
                    val nPrev = nodes[(i - 1 + nodes.size) % nodes.size]
                    val nAfterNext = nodes[(i + 2) % nodes.size]

                    val pCurr = Offset(nCurr.currentX + center.x, nCurr.currentY + center.y)
                    val pNext = Offset(nNext.currentX + center.x, nNext.currentY + center.y)
                    val pPrev = Offset(nPrev.currentX + center.x, nPrev.currentY + center.y)
                    val pAfter = Offset(nAfterNext.currentX + center.x, nAfterNext.currentY + center.y)

                    val cp1 = Offset(pCurr.x + (pNext.x - pPrev.x) / 6f, pCurr.y + (pNext.y - pPrev.y) / 6f)
                    val cp2 = Offset(pNext.x - (pAfter.x - pCurr.x) / 6f, pNext.y - (pAfter.y - pCurr.y) / 6f)

                    bodyPath.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, pNext.x, pNext.y)
                }
                bodyPath.close()

                // Fill Body with 3D Spherical Radial Gradient
                val lightSource = Offset(
                    center.x + physicsEngine.centerX * 0.6f - baseRadius * 0.45f,
                    center.y + physicsEngine.centerY * 0.6f - baseRadius * 0.45f
                )
                drawPath(
                    path = bodyPath,
                    brush = Brush.radialGradient(
                        colors = materialType.bodyColors,
                        center = lightSource,
                        radius = baseRadius * 1.8f
                    )
                )

                // Outer Soft Dough Rim Stroke
                drawPath(
                    path = bodyPath,
                    color = materialType.rimColor.copy(alpha = 0.85f),
                    style = Stroke(width = 4.5f)
                )

                // Top Glossy Highlight Sheen (Specular Arc)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            materialType.highlightColor,
                            materialType.highlightColor.copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        center = lightSource,
                        radius = baseRadius * 0.7f
                    ),
                    radius = baseRadius * 0.42f,
                    center = lightSource
                )
            }

            // 3. Render Deformed Features (Eyes, Rosy Cheeks, Emblem)
            val faceCenter = Offset(
                center.x + physicsEngine.centerX + physicsEngine.pupilGazeX * 8f,
                center.y + physicsEngine.centerY + physicsEngine.pupilGazeY * 8f
            )

            // Rosy Cheeks
            val blushRad = 16f * (1f + currentIntensity * 0.25f)
            drawCircle(
                color = materialType.blushColor.copy(alpha = 0.55f),
                radius = blushRad,
                center = Offset(faceCenter.x - baseRadius * 0.48f, faceCenter.y + 16f)
            )
            drawCircle(
                color = materialType.blushColor.copy(alpha = 0.55f),
                radius = blushRad,
                center = Offset(faceCenter.x + baseRadius * 0.48f, faceCenter.y + 16f)
            )

            // Kawaii Expressive Eyes
            val eyeSpacing = baseRadius * 0.34f
            val isSquinting = currentIntensity > 0.45f

            if (isSquinting) {
                // Happy Squinting Arch Eyes ( ^ ^ ) under pressure
                val eyeWidth = 24f
                val eyeLeftCenter = Offset(faceCenter.x - eyeSpacing, faceCenter.y - 8f)
                val eyeRightCenter = Offset(faceCenter.x + eyeSpacing, faceCenter.y - 8f)

                val leftArc = Path().apply {
                    moveTo(eyeLeftCenter.x - eyeWidth / 2, eyeLeftCenter.y + 4f)
                    quadraticBezierTo(eyeLeftCenter.x, eyeLeftCenter.y - 12f, eyeLeftCenter.x + eyeWidth / 2, eyeLeftCenter.y + 4f)
                }
                val rightArc = Path().apply {
                    moveTo(eyeRightCenter.x - eyeWidth / 2, eyeRightCenter.y + 4f)
                    quadraticBezierTo(eyeRightCenter.x, eyeRightCenter.y - 12f, eyeRightCenter.x + eyeWidth / 2, eyeRightCenter.y + 4f)
                }
                drawPath(leftArc, color = Color(0xFF2E1C0C), style = Stroke(width = 5.5f))
                drawPath(rightArc, color = Color(0xFF2E1C0C), style = Stroke(width = 5.5f))
            } else {
                // Big Anime Eyes with Gaze Tracking
                val eyeRadius = 14.5f
                val gazeOffX = physicsEngine.pupilGazeX * 4.5f
                val gazeOffY = physicsEngine.pupilGazeY * 4.5f

                listOf(
                    Offset(faceCenter.x - eyeSpacing, faceCenter.y - 8f),
                    Offset(faceCenter.x + eyeSpacing, faceCenter.y - 8f)
                ).forEach { eyeBase ->
                    drawCircle(color = Color(0xFF261408), radius = eyeRadius, center = eyeBase)
                    // Pupil highlight glint
                    drawCircle(
                        color = Color.White,
                        radius = eyeRadius * 0.42f,
                        center = Offset(eyeBase.x + gazeOffX - 3.5f, eyeBase.y + gazeOffY - 4f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = eyeRadius * 0.20f,
                        center = Offset(eyeBase.x + gazeOffX + 3.5f, eyeBase.y + gazeOffY + 3.5f)
                    )
                }
            }

            // Happy Mouth
            val mouthPath = Path()
            val mouthCenter = Offset(faceCenter.x, faceCenter.y + 24f)
            if (currentIntensity > 0.5f) {
                // Open laughing mouth :D
                mouthPath.moveTo(mouthCenter.x - 14f, mouthCenter.y - 4f)
                mouthPath.quadraticBezierTo(mouthCenter.x, mouthCenter.y + 18f, mouthCenter.x + 14f, mouthCenter.y - 4f)
                mouthPath.close()
                drawPath(mouthPath, color = Color(0xFFC2185B))
            } else {
                // Cute cat mouth :3
                mouthPath.moveTo(mouthCenter.x - 12f, mouthCenter.y)
                mouthPath.quadraticBezierTo(mouthCenter.x - 6f, mouthCenter.y + 8f, mouthCenter.x, mouthCenter.y + 2f)
                mouthPath.quadraticBezierTo(mouthCenter.x + 6f, mouthCenter.y + 8f, mouthCenter.x + 12f, mouthCenter.y)
                drawPath(mouthPath, color = Color(0xFF3E2723), style = Stroke(width = 4f))
            }

            // 4. Shape specific decorations (Cat paw pads, rubber stress ball grips, gel bubbles, stars, etc.)
            when (shapeType) {
                StressBallShape.CAT_PAW -> {
                    // Soft pink cat paw pad center + 3 toe beans
                    val padCenter = Offset(faceCenter.x, faceCenter.y + baseRadius * 0.35f)
                    val mainPadRad = 22f * (1f - currentIntensity * 0.15f)
                    drawCircle(
                        color = Color(0xFFFF80AB).copy(alpha = 0.85f),
                        radius = mainPadRad,
                        center = padCenter
                    )
                    // 3 toe beans
                    listOf(
                        Offset(padCenter.x - 24f, padCenter.y - 20f),
                        Offset(padCenter.x, padCenter.y - 28f),
                        Offset(padCenter.x + 24f, padCenter.y - 20f)
                    ).forEach { toePos ->
                        drawCircle(
                            color = Color(0xFFFF4081).copy(alpha = 0.85f),
                            radius = 9f,
                            center = toePos
                        )
                    }
                }
                StressBallShape.STRESS_BALL -> {
                    // Tactile rubber grip dots and anti-stress ridges
                    val gripCenter = Offset(faceCenter.x, faceCenter.y + baseRadius * 0.42f)
                    repeat(6) { idx ->
                        val angle = (idx / 6f) * 2f * PI.toFloat()
                        val dist = baseRadius * 0.35f
                        val dotPos = Offset(gripCenter.x + cos(angle) * dist, gripCenter.y + sin(angle) * dist)
                        drawCircle(
                            color = materialType.rimColor.copy(alpha = 0.6f),
                            radius = 6.5f,
                            center = dotPos
                        )
                    }
                }
                StressBallShape.JELLY_SLIME -> {
                    // Translucent floating gelatin bubbles
                    listOf(
                        Offset(faceCenter.x - baseRadius * 0.3f, faceCenter.y - 20f) to 12f,
                        Offset(faceCenter.x + baseRadius * 0.35f, faceCenter.y + 25f) to 16f,
                        Offset(faceCenter.x - 10f, faceCenter.y + baseRadius * 0.45f) to 14f
                    ).forEach { (pos, rad) ->
                        drawCircle(
                            color = Color.White.copy(alpha = 0.45f),
                            radius = rad * (1f + currentIntensity * 0.2f),
                            center = pos
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.85f),
                            radius = rad * 0.3f,
                            center = Offset(pos.x - rad * 0.3f, pos.y - rad * 0.3f)
                        )
                    }
                }
                StressBallShape.MAGIC_STAR -> {
                    val starLayout = textMeasurer.measure(
                        text = "⭐",
                        style = TextStyle(fontSize = 32.sp)
                    )
                    drawText(
                        textLayoutResult = starLayout,
                        topLeft = Offset(faceCenter.x - starLayout.size.width / 2f, faceCenter.y + baseRadius * 0.32f)
                    )
                }
                StressBallShape.SWEET_HEART -> {
                    val heartLayout = textMeasurer.measure(
                        text = "💖",
                        style = TextStyle(fontSize = 32.sp)
                    )
                    drawText(
                        textLayoutResult = heartLayout,
                        topLeft = Offset(faceCenter.x - heartLayout.size.width / 2f, faceCenter.y + baseRadius * 0.32f)
                    )
                }
                StressBallShape.DUMPLING -> {
                    if (showEmblem) {
                        val emblemCenter = Offset(faceCenter.x, faceCenter.y + baseRadius * 0.44f)
                        val emblemRad = 26f * (1f - currentIntensity * 0.2f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = materialType.emblemBgColors,
                                center = emblemCenter,
                                radius = emblemRad
                            ),
                            radius = emblemRad,
                            center = emblemCenter
                        )
                        drawCircle(
                            color = materialType.rimColor.copy(alpha = 0.7f),
                            radius = emblemRad,
                            center = emblemCenter,
                            style = Stroke(width = 2.5f)
                        )

                        // 67 Emblem Text
                        val textLayout = textMeasurer.measure(
                            text = "67",
                            style = TextStyle(
                                fontSize = (15 * (1f - currentIntensity * 0.15f)).sp,
                                fontWeight = FontWeight.Black,
                                color = materialType.emblemTextColor
                            )
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(emblemCenter.x - textLayout.size.width / 2f, emblemCenter.y - textLayout.size.height / 2f)
                        )
                    }
                }
            }

            // 5. Equipped Accessories Render (Hat, Glasses, Bow)
            if (equippedHat != null && equippedHat.emoji.isNotEmpty()) {
                val hatLayout = textMeasurer.measure(
                    text = equippedHat.emoji,
                    style = TextStyle(fontSize = 38.sp)
                )
                drawText(
                    textLayoutResult = hatLayout,
                    topLeft = Offset(
                        faceCenter.x - hatLayout.size.width / 2f,
                        faceCenter.y - baseRadius * 1.02f - hatLayout.size.height * 0.6f
                    )
                )
            }

            if (equippedGlasses != null && equippedGlasses.emoji.isNotEmpty()) {
                val glassesLayout = textMeasurer.measure(
                    text = equippedGlasses.emoji,
                    style = TextStyle(fontSize = 32.sp)
                )
                drawText(
                    textLayoutResult = glassesLayout,
                    topLeft = Offset(
                        faceCenter.x - glassesLayout.size.width / 2f,
                        faceCenter.y - 24f
                    )
                )
            }

            if (equippedBody != null && equippedBody.emoji.isNotEmpty()) {
                val bodyLayout = textMeasurer.measure(
                    text = equippedBody.emoji,
                    style = TextStyle(fontSize = 28.sp)
                )
                drawText(
                    textLayoutResult = bodyLayout,
                    topLeft = Offset(
                        faceCenter.x - bodyLayout.size.width / 2f,
                        faceCenter.y + baseRadius * 0.72f
                    )
                )
            }

            // 6. Bursting Antistress Sparkles & Emojis
            sparkles.forEach { sp ->
                if (sp.emoji != null) {
                    val emojiLayout = textMeasurer.measure(
                        text = sp.emoji,
                        style = TextStyle(fontSize = sp.size.sp)
                    )
                    drawText(
                        textLayoutResult = emojiLayout,
                        topLeft = Offset(center.x + sp.x - emojiLayout.size.width / 2f, center.y + sp.y - emojiLayout.size.height / 2f),
                        alpha = sp.alpha
                    )
                } else {
                    drawCircle(
                        color = sp.color.copy(alpha = sp.alpha),
                        radius = sp.size / 2f,
                        center = Offset(center.x + sp.x, center.y + sp.y)
                    )
                }
            }
        }
    }
}
