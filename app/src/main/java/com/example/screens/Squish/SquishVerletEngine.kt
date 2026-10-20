package com.example.screens.Squish

import androidx.compose.ui.geometry.Offset
import kotlin.math.*

class VerletPoint(
    var x: Float,
    var y: Float,
    var oldX: Float = x,
    var oldY: Float = y
) {
    fun velocityX(dt: Float): Float = (x - oldX) / dt
    fun velocityY(dt: Float): Float = (y - oldY) / dt
}

class VerletSpring(
    val p1: Int,
    val p2: Int,
    var restLength: Float,
    val stiffness: Float
)

class SquishVerletEngine(
    val onBounce: (Float) -> Unit,
    val onSquishSound: (Float) -> Unit,
    val onStretchSound: () -> Unit
) {
    val pointCount = 16
    val centerIndex = 16
    val points = Array(17) { VerletPoint(0f, 0f) }
    private val restOffsets = Array(17) { Offset.Zero }
    private val springs = mutableListOf<VerletSpring>()

    var baseRadiusPx: Float = 105f * 2.5f
    var restArea: Float = 1f
    var currentSkin: HeroSkin = HeroSkin.PELMEN

    // World & screen boundaries
    var screenLeft: Float = 0f
    var screenRight: Float = 1000f
    var screenTop: Float = 100f
    var screenFloorY: Float = 1800f
    var homeCenter: Offset = Offset(500f, 900f)

    // Motion states
    var isFlying: Boolean = false
    var bounceCount: Int = 0
    var isSettlingHome: Boolean = false

    private val damping: Float = 0.985f
    private var lastStretchSoundTime: Long = 0L

    fun initializeShape(skin: HeroSkin, center: Offset, radiusPx: Float) {
        currentSkin = skin
        homeCenter = center
        baseRadiusPx = radiusPx
        springs.clear()

        // Generate rest shape points based on selected skin
        for (i in 0 until pointCount) {
            val angle = i * 2f * PI.toFloat() / pointCount
            val offset = when (skin) {
                HeroSkin.PELMEN -> {
                    // Semi-circular dumpling with crimped wavy top/edge
                    val normY = sin(angle)
                    val r = if (normY < 0f) {
                        radiusPx * (0.85f + 0.12f * sin(angle * 8f)) // crimped edge
                    } else {
                        radiusPx * 1.05f
                    }
                    Offset(cos(angle) * r, sin(angle) * r * 0.82f)
                }
                HeroSkin.ZEFIR -> {
                    // Chubby rounded marshmallow rectangle
                    val cx = cos(angle).coerceIn(-0.75f, 0.75f) * radiusPx * 1.15f
                    val cy = sin(angle).coerceIn(-0.75f, 0.75f) * radiusPx * 0.95f
                    Offset(cx, cy)
                }
                HeroSkin.KOLOBOK, HeroSkin.FACE -> {
                    // Perfect soft circle
                    Offset(cos(angle) * radiusPx, sin(angle) * radiusPx)
                }
            }
            restOffsets[i] = offset
            points[i].x = center.x + offset.x
            points[i].y = center.y + offset.y
            points[i].oldX = points[i].x
            points[i].oldY = points[i].y
        }

        // Center point
        restOffsets[centerIndex] = Offset.Zero
        points[centerIndex].x = center.x
        points[centerIndex].y = center.y
        points[centerIndex].oldX = center.x
        points[centerIndex].oldY = center.y

        // Calculate rest area
        restArea = calculatePolygonArea()

        // 1. Perimeter springs
        for (i in 0 until pointCount) {
            val next = (i + 1) % pointCount
            val len = (restOffsets[i] - restOffsets[next]).getDistance()
            springs.add(VerletSpring(i, next, len, 0.55f))
        }

        // 2. Center radial springs
        for (i in 0 until pointCount) {
            val len = restOffsets[i].getDistance()
            springs.add(VerletSpring(i, centerIndex, len, 0.35f))
        }

        // 3. Cross-diameter springs (maintain shape width)
        for (i in 0 until pointCount / 2) {
            val opposite = (i + pointCount / 2) % pointCount
            val len = (restOffsets[i] - restOffsets[opposite]).getDistance()
            springs.add(VerletSpring(i, opposite, len, 0.30f))
        }
    }

    fun getCenter(): Offset = Offset(points[centerIndex].x, points[centerIndex].y)

    fun getAverageVelocity(): Offset {
        val dt = 1f / 120f
        return Offset(points[centerIndex].velocityX(dt), points[centerIndex].velocityY(dt))
    }

    fun step(dtSec: Float) {
        val fixedDt = 1f / 120f
        var accumulator = dtSec.coerceAtMost(0.05f)
        var steps = 0

        while (accumulator >= fixedDt && steps < 6) {
            subStep(fixedDt)
            accumulator -= fixedDt
            steps++
        }
    }

    private fun subStep(dt: Float) {
        // 1. Integrate Verlet on all points
        val gravity = if (isFlying) 1800f * 2.2f else 0f

        for (i in 0..centerIndex) {
            val p = points[i]
            val vx = (p.x - p.oldX) * damping
            val vy = (p.y - p.oldY) * damping + gravity * dt * dt

            p.oldX = p.x
            p.oldY = p.y
            p.x += vx
            p.y += vy
        }

        // 2. Solve Springs & Constraints (3 iterations for robust stiffness)
        repeat(3) {
            for (s in springs) {
                val p1 = points[s.p1]
                val p2 = points[s.p2]
                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                val diff = (dist - s.restLength) / dist * s.stiffness * 0.5f

                val offsetX = dx * diff
                val offsetY = dy * diff

                if (s.p1 != centerIndex) {
                    p1.x += offsetX
                    p1.y += offsetY
                }
                if (s.p2 != centerIndex) {
                    p2.x -= offsetX
                    p2.y -= offsetY
                }
            }

            // Volume conservation pressure model
            val currentArea = calculatePolygonArea()
            val areaDiff = (restArea - currentArea) * 0.6f / restArea.coerceAtLeast(1f)

            for (i in 0 until pointCount) {
                val next = (i + 1) % pointCount
                val p1 = points[i]
                val p2 = points[next]
                val segDx = p2.x - p1.x
                val segDy = p2.y - p1.y
                val segLen = sqrt(segDx * segDx + segDy * segDy).coerceAtLeast(0.001f)

                // Outward normal
                val nx = -segDy / segLen
                val ny = segDx / segLen

                val push = areaDiff * segLen * 0.25f
                p1.x += nx * push
                p1.y += ny * push
            }
        }

        // 3. Screen Bounds Collisions with Wall Bounce
        var hitWall = false
        var maxImpactVel = 0f

        for (i in 0..centerIndex) {
            val p = points[i]
            val margin = 20f

            if (p.x < screenLeft + margin) {
                val impact = abs(p.x - p.oldX)
                if (impact > maxImpactVel) maxImpactVel = impact
                p.x = screenLeft + margin
                p.oldX = p.x + (p.x - p.oldX) * 0.6f
                hitWall = true
            } else if (p.x > screenRight - margin) {
                val impact = abs(p.x - p.oldX)
                if (impact > maxImpactVel) maxImpactVel = impact
                p.x = screenRight - margin
                p.oldX = p.x + (p.x - p.oldX) * 0.6f
                hitWall = true
            }

            if (p.y < screenTop + margin) {
                val impact = abs(p.y - p.oldY)
                if (impact > maxImpactVel) maxImpactVel = impact
                p.y = screenTop + margin
                p.oldY = p.y + (p.y - p.oldY) * 0.6f
                hitWall = true
            } else if (p.y > screenFloorY - margin) {
                val impact = abs(p.y - p.oldY)
                if (impact > maxImpactVel) maxImpactVel = impact
                p.y = screenFloorY - margin
                p.oldY = p.y + (p.y - p.oldY) * 0.6f
                hitWall = true
            }
        }

        if (hitWall && isFlying && maxImpactVel > 4f) {
            bounceCount++
            onBounce(maxImpactVel)
            if (bounceCount >= 3) {
                isFlying = false
                isSettlingHome = true
            }
        }

        // 4. Smooth Return / Settling Home after flight
        if (isSettlingHome || (!isFlying && points[centerIndex].y > screenFloorY - 100f)) {
            val center = points[centerIndex]
            val target = homeCenter
            val dist = (target - Offset(center.x, center.y)).getDistance()
            if (dist > 5f) {
                val glide = (target - Offset(center.x, center.y)) * 0.04f
                for (i in 0..centerIndex) {
                    points[i].x += glide.x
                    points[i].y += glide.y
                    points[i].oldX += glide.x
                    points[i].oldY += glide.y
                }
            } else {
                isSettlingHome = false
            }
        }

        // 5. Anti-Explosion & NaN Guard
        for (i in 0..centerIndex) {
            val p = points[i]
            if (p.x.isNaN() || p.y.isNaN() || p.oldX.isNaN() || p.oldY.isNaN() ||
                abs(p.x - p.oldX) > 300f || abs(p.y - p.oldY) > 300f
            ) {
                resetToRestPosition(homeCenter)
                break
            }
        }
    }

    /**
     * Apply touch deformation (pulling or squishing).
     */
    fun applyTouchInfluence(touchPos: Offset, intensityMultiplier: Float = 1.0f) {
        val radiusInfluence = 130f * 2.5f
        var totalDistMoved = 0f

        for (i in 0..centerIndex) {
            val p = points[i]
            val dx = touchPos.x - p.x
            val dy = touchPos.y - p.y
            val dist = sqrt(dx * dx + dy * dy)

            if (dist < radiusInfluence) {
                val weight = (1f - dist / radiusInfluence) * (1f - dist / radiusInfluence) * intensityMultiplier
                val pullX = dx * weight * 0.42f
                val pullY = dy * weight * 0.42f
                p.x += pullX
                p.y += pullY
                totalDistMoved += abs(pullX) + abs(pullY)
            }
        }

        // Sound cues for squishing/stretching
        if (totalDistMoved > 18f) {
            val now = System.currentTimeMillis()
            if (now - lastStretchSoundTime > 300L) {
                lastStretchSoundTime = now
                onStretchSound()
            }
        }
    }

    /**
     * Launch/Fling hero into flight.
     */
    fun fling(velocity: Offset) {
        isFlying = true
        isSettlingHome = false
        bounceCount = 0
        val clampedVel = Offset(
            velocity.x.coerceIn(-2500f, 2500f),
            velocity.y.coerceIn(-2500f, 2500f)
        )
        val dt = 1f / 120f

        for (i in 0..centerIndex) {
            points[i].oldX = points[i].x - clampedVel.x * dt
            points[i].oldY = points[i].y - clampedVel.y * dt
        }
    }

    /**
     * Poke / Tap dent in body.
     */
    fun poke(pos: Offset) {
        val radiusInf = 80f * 2.5f
        for (i in 0..centerIndex) {
            val p = points[i]
            val dx = pos.x - p.x
            val dy = pos.y - p.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < radiusInf) {
                val w = (1f - dist / radiusInf)
                p.x -= dx * w * 0.35f
                p.y -= dy * w * 0.35f
            }
        }
        // Small upward hop
        for (i in 0..centerIndex) {
            points[i].oldY = points[i].y + 16f
        }
    }

    fun resetToRestPosition(center: Offset) {
        for (i in 0 until pointCount) {
            points[i].x = center.x + restOffsets[i].x
            points[i].y = center.y + restOffsets[i].y
            points[i].oldX = points[i].x
            points[i].oldY = points[i].y
        }
        points[centerIndex].x = center.x
        points[centerIndex].y = center.y
        points[centerIndex].oldX = center.x
        points[centerIndex].oldY = center.y
        isFlying = false
        isSettlingHome = false
        bounceCount = 0
    }

    private fun calculatePolygonArea(): Float {
        var area = 0f
        for (i in 0 until pointCount) {
            val next = (i + 1) % pointCount
            area += points[i].x * points[next].y - points[next].x * points[i].y
        }
        return abs(area) * 0.5f
    }
}
