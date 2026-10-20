package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.ui.theme.PlaygroundEnvironmentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sin
import kotlin.random.Random

data class FloatingCloud(
    var baseX: Float,
    val y: Float,
    val speed: Float,
    val scale: Float,
    val alpha: Float,
    val parallaxFactor: Float
)

data class AmbientScenerySparkle(
    var baseX: Float,
    var baseY: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    var alpha: Float,
    val color: Color,
    val parallaxFactor: Float
)

/**
 * Living Playground Background Scenery with Multi-Layer Parallax Depth during room swipe transitions.
 */
@Composable
fun PlaygroundBackgroundScenery(
    environmentType: PlaygroundEnvironmentType,
    scrollOffset: Float = 0f,
    modifier: Modifier = Modifier
) {
    val effectiveTheme = environmentType.resolveEffectiveTheme()

    // Ambient breathing and pulsation animations
    val infiniteTransition = rememberInfiniteTransition(label = "scenery_anim")
    val sunPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sun_pulse"
    )
    val starGlow by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "star_glow"
    )

    // Dynamic clouds with individual parallax depths
    val clouds = remember {
        mutableStateListOf(
            FloatingCloud(baseX = 60f, y = 85f, speed = 0.35f, scale = 1.15f, alpha = 0.85f, parallaxFactor = 90f),
            FloatingCloud(baseX = 360f, y = 150f, speed = 0.22f, scale = 0.85f, alpha = 0.70f, parallaxFactor = 55f),
            FloatingCloud(baseX = 650f, y = 75f, speed = 0.45f, scale = 1.35f, alpha = 0.90f, parallaxFactor = 130f),
            FloatingCloud(baseX = 940f, y = 130f, speed = 0.28f, scale = 0.95f, alpha = 0.75f, parallaxFactor = 75f)
        )
    }

    val ambientSparkles = remember { mutableStateListOf<AmbientScenerySparkle>() }

    // Init ambient sparkles / fireflies with variable depth
    LaunchedEffect(effectiveTheme) {
        ambientSparkles.clear()
        val count = if (effectiveTheme.isNight) 30 else 15
        repeat(count) {
            val depth = Random.nextFloat() * 120f + 40f
            ambientSparkles.add(
                AmbientScenerySparkle(
                    baseX = Random.nextFloat() * 1200f,
                    baseY = Random.nextFloat() * 1400f,
                    vx = (Random.nextFloat() - 0.5f) * 0.9f,
                    vy = (Random.nextFloat() - 0.5f) * 0.9f,
                    size = if (effectiveTheme.isNight) Random.nextFloat() * 4.5f + 2f else Random.nextFloat() * 5f + 3f,
                    alpha = Random.nextFloat() * 0.7f + 0.3f,
                    color = if (effectiveTheme == PlaygroundEnvironmentType.COSMIC_SPACE) {
                        listOf(Color(0xFFE040FB), Color(0xFF00E5FF), Color(0xFFFFD54F), Color(0xFF76FF03)).random()
                    } else if (effectiveTheme.isNight) {
                        Color(0xFFFFF59D)
                    } else {
                        Color.White
                    },
                    parallaxFactor = depth
                )
            )
        }
    }

    // Animation ticker for ambient cloud drifting & particle floating
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(24)
            clouds.forEach { c ->
                c.baseX += c.speed
                if (c.baseX > 1400f) {
                    c.baseX = -300f
                }
            }
            ambientSparkles.forEach { s ->
                s.baseX += s.vx
                s.baseY += s.vy
                if (s.baseX < 0f) s.baseX = 1200f
                if (s.baseX > 1200f) s.baseX = 0f
                if (s.baseY < 0f) s.baseY = 1400f
                if (s.baseY > 1400f) s.baseY = 0f
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = effectiveTheme.skyColors))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // ==========================================
            // LAYER 1: Deep Sky & Celestial Body (Far Parallax: -25f per page)
            // ==========================================
            val farParallaxX = -scrollOffset * 35f

            if (!effectiveTheme.isNight) {
                // Sun in far background
                val baseSunX = width * 0.82f + farParallaxX
                val sunCenter = Offset(baseSunX, height * 0.12f)
                val sunRadius = 45f * sunPulse

                drawCircle(
                    color = Color(0xFFFFEB3B).copy(alpha = 0.22f),
                    radius = sunRadius * 1.8f,
                    center = sunCenter
                )
                drawCircle(
                    color = Color(0xFFFFD54F).copy(alpha = 0.45f),
                    radius = sunRadius * 1.35f,
                    center = sunCenter
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFB300)),
                        center = sunCenter,
                        radius = sunRadius
                    ),
                    radius = sunRadius,
                    center = sunCenter
                )
            } else {
                // Moon in far background
                val baseMoonX = width * 0.82f + farParallaxX
                val moonCenter = Offset(baseMoonX, height * 0.13f)
                val moonRadius = 42f

                drawCircle(
                    color = Color(0xFFFFF9C4).copy(alpha = 0.18f * starGlow),
                    radius = moonRadius * 1.9f,
                    center = moonCenter
                )
                drawCircle(
                    color = Color(0xFFFFF9C4),
                    radius = moonRadius,
                    center = moonCenter
                )
                drawCircle(
                    color = effectiveTheme.skyColors[0],
                    radius = moonRadius * 0.82f,
                    center = Offset(moonCenter.x - 14f, moonCenter.y - 8f)
                )
            }

            // ==========================================
            // LAYER 2: Distant Hills & Mid Stars (Mid Parallax: -70f per page)
            // ==========================================
            val midParallaxX = -scrollOffset * 70f
            val distantGroundY = height * 0.84f

            val distantHillPath = Path().apply {
                val startX = -200f + midParallaxX
                val endX = width + 400f + midParallaxX
                moveTo(startX, distantGroundY + 20f)
                cubicTo(
                    startX + width * 0.35f, distantGroundY - 35f,
                    startX + width * 0.75f, distantGroundY + 15f,
                    endX, distantGroundY - 20f
                )
                lineTo(endX, height)
                lineTo(startX, height)
                close()
            }
            drawPath(
                path = distantHillPath,
                color = effectiveTheme.groundColor.copy(alpha = 0.22f)
            )

            // Distant Stars / Cosmic nebulae
            if (effectiveTheme.isNight) {
                ambientSparkles.forEachIndexed { i, s ->
                    val starX = (s.baseX - scrollOffset * s.parallaxFactor).mod(width + 200f) - 100f
                    val twinkle = if (i % 2 == 0) starGlow else (1f - starGlow * 0.6f)
                    drawCircle(
                        color = s.color.copy(alpha = (s.alpha * twinkle).coerceIn(0f, 1f)),
                        radius = s.size,
                        center = Offset(starX, s.baseY)
                    )
                }
            }

            // ==========================================
            // LAYER 3: Drifting Clouds / Daytime Particles (Mid-Near Parallax)
            // ==========================================
            if (!effectiveTheme.isNight) {
                clouds.forEach { cloud ->
                    val cloudScreenX = (cloud.baseX - scrollOffset * cloud.parallaxFactor).mod(width + 400f) - 200f
                    drawFluffyCloud(
                        centerX = cloudScreenX,
                        centerY = cloud.y,
                        scale = cloud.scale,
                        alpha = cloud.alpha,
                        color = if (effectiveTheme == PlaygroundEnvironmentType.GOLDEN_SUNSET) Color(0xFFFFE0B2) else Color.White
                    )
                }
            }

            // ==========================================
            // LAYER 4: Foreground Rolling Meadow (Fast Parallax: -160f per page)
            // ==========================================
            val fgParallaxX = -scrollOffset * 160f
            val groundY = height * 0.88f

            val hillPath = Path().apply {
                val startX = -300f + fgParallaxX
                val endX = width + 600f + fgParallaxX
                moveTo(startX, groundY)
                cubicTo(
                    startX + width * 0.28f, groundY - 30f,
                    startX + width * 0.72f, groundY + 30f,
                    endX, groundY - 15f
                )
                lineTo(endX, height)
                lineTo(startX, height)
                close()
            }
            drawPath(
                path = hillPath,
                color = effectiveTheme.groundColor.copy(alpha = 0.40f)
            )

            // Foreground decorative little grass / sparkles
            val grassCount = 10
            for (g in 0 until grassCount) {
                val gx = (g * 140f - scrollOffset * 190f).mod(width + 100f) - 50f
                val gy = groundY + sin(g.toDouble() * 1.5).toFloat() * 15f + 10f
                drawCircle(
                    color = Color.White.copy(alpha = 0.45f),
                    radius = 3.5f,
                    center = Offset(gx, gy)
                )
            }
        }
    }
}

private fun DrawScope.drawFluffyCloud(
    centerX: Float,
    centerY: Float,
    scale: Float,
    alpha: Float,
    color: Color
) {
    val cloudColor = color.copy(alpha = alpha.coerceIn(0f, 1f))
    val r = 26f * scale

    drawCircle(color = cloudColor, radius = r * 1.3f, center = Offset(centerX, centerY))
    drawCircle(color = cloudColor, radius = r, center = Offset(centerX - r * 1.1f, centerY + r * 0.2f))
    drawCircle(color = cloudColor, radius = r * 1.1f, center = Offset(centerX + r * 1.1f, centerY + r * 0.15f))
    drawCircle(color = cloudColor, radius = r * 0.8f, center = Offset(centerX + r * 1.9f, centerY + r * 0.35f))
    drawCircle(color = cloudColor, radius = r * 0.8f, center = Offset(centerX - r * 1.8f, centerY + r * 0.35f))
}
