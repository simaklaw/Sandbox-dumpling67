package com.example.physics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A vertex node in the 2D soft-body mass-spring lattice.
 */
data class SquishyNode(
    val index: Int,
    val isPerimeter: Boolean,
    val baseAngle: Float,
    val baseRadius: Float,
    var currentX: Float,
    var currentY: Float,
    var velocityX: Float = 0f,
    var velocityY: Float = 0f,
    var forceX: Float = 0f,
    var forceY: Float = 0f
)

/**
 * Structural / shear spring connecting two nodes with rest length and elasticity.
 */
data class SoftBodySpring(
    val nodeA: Int,
    val nodeB: Int,
    val isOuterToOuter: Boolean,
    val isOuterToInner: Boolean,
    val restLength: Float,
    val stiffnessMultiplier: Float = 1.0f
)

/**
 * Represents an active touch contact point on the squishy soft body.
 */
data class SquishyContact(
    val id: Long,
    val x: Float,
    val y: Float,
    val previousX: Float = x,
    val previousY: Float = y,
    val pressure: Float = 1.0f,
    val radius: Float = 100f
)

/**
 * High-performance 2D Soft-Body Mass-Spring & Hydrostatic Pressure Physics Engine
 * for Pelmen-Six "Сквиш-Мялка" in Jetpack Compose.
 *
 * Implements:
 * 1. Triangulated Mass-Spring Lattice (32 outer perimeter nodes + 16 inner ring nodes + center node).
 * 2. Hooke's Law with structural, shear, and central radial springs.
 * 3. Gauss / Shoelace Polygon Area Hydrostatic Pressure (true incompressible fluid/dough bulge).
 * 4. Multi-touch poking, dragging, pinching, stretching, and flick inertia.
 * 5. Material-specific physical constants (stiffness, damping, pressure, bounciness).
 * 6. Smooth Catmull-Rom cubic Bezier spline contour reconstruction.
 * 7. Facial rigging anchors (eyes, cheeks, emblem) deformed with the soft body.
 * 8. Surface normal vector generation for dynamic 3D lighting.
 */
class SquishyPhysicsEngine(
    val outerNodeCount: Int = 32,
    val innerNodeCount: Int = 16,
    val baseRadius: Float = 140f
) {
    // Total nodes = outer (32) + inner (16) + center (1) = 49
    val allNodes = ArrayList<SquishyNode>(outerNodeCount + innerNodeCount + 1)
    val springs = ArrayList<SoftBodySpring>()

    // Rest polygon area for hydrostatic pressure
    private var restPolygonArea: Float = 0f

    // Center of mass translation
    var centerX: Float = 0f
    var centerY: Float = 0f
    var centerVelX: Float = 0f
    var centerVelY: Float = 0f

    // Dynamic metrics for UI / audio / haptics
    var squishIntensity: Float = 0f
        private set
    var squishAngle: Float = 0f
        private set
    var isBeingTouched: Boolean = false
        private set
    var touchContactCount: Int = 0
        private set
    var currentAreaRatio: Float = 1.0f
        private set

    // Gaze tracking for kawaii eyes
    var pupilGazeX: Float = 0f
        private set
    var pupilGazeY: Float = 0f
        private set

    // Physical material parameters
    var springStiffness: Float = 150f
    var damping: Float = 9.5f
    var internalPressure: Float = 0.80f
    var bounciness: Float = 1.0f
    var mass: Float = 1.0f

    init {
        buildLatticeMesh()
    }

    /**
     * Applies physical tuning from the selected material.
     */
    fun applyMaterialPhysics(
        stiffness: Float,
        dampingVal: Float,
        pressure: Float,
        bounce: Float
    ) {
        this.springStiffness = stiffness
        this.damping = dampingVal
        this.internalPressure = pressure
        this.bounciness = bounce
    }

    /**
     * Constructs the 2D mass-spring lattice mesh.
     */
    fun buildLatticeMesh() {
        allNodes.clear()
        springs.clear()

        // 1. Create 32 Outer Perimeter Nodes (Dumpling contour)
        for (i in 0 until outerNodeCount) {
            val angle = (i.toFloat() / outerNodeCount) * 2f * PI.toFloat()

            // Contour: plump bottom belly + 5 fluted crest folds along top
            val r: Float = if (angle in 0f..PI.toFloat()) {
                val sinVal = sin(angle)
                baseRadius * (1.06f + 0.18f * sinVal)
            } else {
                val crestSin = sin(5f * angle)
                val archFactor = 1.0f - 0.20f * sin(angle - PI.toFloat())
                baseRadius * archFactor * (0.92f + 0.08f * crestSin)
            }

            val bx = r * cos(angle)
            val by = r * sin(angle)

            allNodes.add(
                SquishyNode(
                    index = i,
                    isPerimeter = true,
                    baseAngle = angle,
                    baseRadius = r,
                    currentX = bx,
                    currentY = by
                )
            )
        }

        // 2. Create 16 Inner Ring Nodes (at ~50% radius)
        val innerStartIndex = outerNodeCount
        for (j in 0 until innerNodeCount) {
            val angle = (j.toFloat() / innerNodeCount) * 2f * PI.toFloat()
            val innerRadius = baseRadius * 0.52f
            val bx = innerRadius * cos(angle)
            val by = innerRadius * sin(angle)

            allNodes.add(
                SquishyNode(
                    index = innerStartIndex + j,
                    isPerimeter = false,
                    baseAngle = angle,
                    baseRadius = innerRadius,
                    currentX = bx,
                    currentY = by
                )
            )
        }

        // 3. Create Center Core Node (index = outerNodeCount + innerNodeCount)
        val centerIndex = outerNodeCount + innerNodeCount
        allNodes.add(
            SquishyNode(
                index = centerIndex,
                isPerimeter = false,
                baseAngle = 0f,
                baseRadius = 0f,
                currentX = 0f,
                currentY = 0f
            )
        )

        // 4. Build Springs
        // A. Perimeter springs (Outer-to-Outer loop)
        for (i in 0 until outerNodeCount) {
            val nextI = (i + 1) % outerNodeCount
            val nA = allNodes[i]
            val nB = allNodes[nextI]
            val restDist = hypot(nA.currentX - nB.currentX, nA.currentY - nB.currentY)
            springs.add(
                SoftBodySpring(
                    nodeA = i,
                    nodeB = nextI,
                    isOuterToOuter = true,
                    isOuterToInner = false,
                    restLength = restDist,
                    stiffnessMultiplier = 1.35f
                )
            )
        }

        // B. Outer-to-Inner Radial & Diagonal Shear Springs
        for (i in 0 until outerNodeCount) {
            val innerIdx = innerStartIndex + (i / 2) % innerNodeCount
            val nextInnerIdx = innerStartIndex + ((i / 2) + 1) % innerNodeCount

            val nOuter = allNodes[i]
            val nInner = allNodes[innerIdx]
            val nInnerNext = allNodes[nextInnerIdx]

            // Radial spring
            val radDist = hypot(nOuter.currentX - nInner.currentX, nOuter.currentY - nInner.currentY)
            springs.add(
                SoftBodySpring(
                    nodeA = i,
                    nodeB = innerIdx,
                    isOuterToOuter = false,
                    isOuterToInner = true,
                    restLength = radDist,
                    stiffnessMultiplier = 1.0f
                )
            )

            // Diagonal cross-brace spring
            val diagDist = hypot(nOuter.currentX - nInnerNext.currentX, nOuter.currentY - nInnerNext.currentY)
            springs.add(
                SoftBodySpring(
                    nodeA = i,
                    nodeB = nextInnerIdx,
                    isOuterToOuter = false,
                    isOuterToInner = true,
                    restLength = diagDist,
                    stiffnessMultiplier = 0.75f
                )
            )
        }

        // C. Inner-to-Inner Perimeter Springs
        for (j in 0 until innerNodeCount) {
            val nextJ = (j + 1) % innerNodeCount
            val nA = allNodes[innerStartIndex + j]
            val nB = allNodes[innerStartIndex + nextJ]
            val dist = hypot(nA.currentX - nB.currentX, nA.currentY - nB.currentY)
            springs.add(
                SoftBodySpring(
                    nodeA = innerStartIndex + j,
                    nodeB = innerStartIndex + nextJ,
                    isOuterToOuter = false,
                    isOuterToInner = false,
                    restLength = dist,
                    stiffnessMultiplier = 1.1f
                )
            )
        }

        // D. Inner-to-Center Core Radial Spokes
        for (j in 0 until innerNodeCount) {
            val nA = allNodes[innerStartIndex + j]
            val dist = hypot(nA.currentX, nA.currentY)
            springs.add(
                SoftBodySpring(
                    nodeA = innerStartIndex + j,
                    nodeB = centerIndex,
                    isOuterToOuter = false,
                    isOuterToInner = false,
                    restLength = dist,
                    stiffnessMultiplier = 1.15f
                )
            )
        }

        // Calculate initial rest polygon area via Shoelace formula
        restPolygonArea = calculatePerimeterArea()
    }

    /**
     * Calculates the signed polygon area of the outer perimeter nodes.
     */
    private fun calculatePerimeterArea(): Float {
        var area = 0f
        for (i in 0 until outerNodeCount) {
            val nextI = (i + 1) % outerNodeCount
            val nA = allNodes[i]
            val nB = allNodes[nextI]
            area += (nA.currentX * nB.currentY - nB.currentX * nA.currentY)
        }
        return kotlin.math.abs(area) * 0.5f
    }

    /**
     * Resets the soft-body mesh to rest shape.
     */
    fun resetToRest() {
        for (node in allNodes) {
            val bx = node.baseRadius * cos(node.baseAngle)
            val by = node.baseRadius * sin(node.baseAngle)
            node.currentX = bx
            node.currentY = by
            node.velocityX = 0f
            node.velocityY = 0f
            node.forceX = 0f
            node.forceY = 0f
        }
        centerX = 0f
        centerY = 0f
        centerVelX = 0f
        centerVelY = 0f
        squishIntensity = 0f
        pupilGazeX = 0f
        pupilGazeY = 0f
    }

    /**
     * Injects a quick physical jiggle/wobble wave across all nodes (e.g. on tickle or bounce).
     */
    fun triggerImpulse(intensity: Float = 1.0f, directionX: Float = 0f, directionY: Float = 1f) {
        val mult = intensity * bounciness
        for (i in 0 until outerNodeCount) {
            val node = allNodes[i]
            val wave = sin(node.baseAngle * 3f + PI.toFloat() / 3f) * 75f * mult
            node.velocityX += wave * cos(node.baseAngle) + directionX * 55f * mult
            node.velocityY += wave * sin(node.baseAngle) + directionY * 55f * mult
        }
        for (j in 0 until innerNodeCount) {
            val node = allNodes[outerNodeCount + j]
            val wave = sin(node.baseAngle * 2f) * 45f * mult
            node.velocityX += wave * cos(node.baseAngle) + directionX * 35f * mult
            node.velocityY += wave * sin(node.baseAngle) + directionY * 35f * mult
        }
        centerVelX += directionX * 90f * mult
        centerVelY += directionY * 90f * mult
    }

    /**
     * Main physical simulation step.
     * Integrates Hooke spring forces, internal hydrostatic pressure, touch interactions,
     * damping, and Verlet/Euler time integration.
     */
    fun step(
        dt: Float,
        contacts: List<SquishyContact>,
        containerCenter: Offset = Offset.Zero
    ) {
        val clampedDt = dt.coerceIn(0.002f, 0.025f)
        isBeingTouched = contacts.isNotEmpty()
        touchContactCount = contacts.size

        // 1. Calculate touch gaze vector for kawaii eyes
        if (contacts.isNotEmpty()) {
            val primary = contacts.first()
            val dx = primary.x - (containerCenter.x + centerX)
            val dy = primary.y - (containerCenter.y + centerY)
            val dist = hypot(dx, dy)
            if (dist > 1f) {
                val maxGaze = 14f
                pupilGazeX = (dx / dist) * (dist / 120f).coerceIn(0f, 1f) * maxGaze
                pupilGazeY = (dy / dist) * (dist / 120f).coerceIn(0f, 1f) * maxGaze
            }
        } else {
            pupilGazeX += (0f - pupilGazeX) * 0.15f
            pupilGazeY += (0f - pupilGazeY) * 0.15f
        }

        // 2. Clear forces on all nodes
        for (node in allNodes) {
            node.forceX = 0f
            node.forceY = 0f
        }

        // 3. Restoring Anchor Springs to Rest Base Shape
        for (node in allNodes) {
            val baseX = node.baseRadius * cos(node.baseAngle)
            val baseY = node.baseRadius * sin(node.baseAngle)
            val dispX = node.currentX - baseX
            val dispY = node.currentY - baseY

            val anchorK = springStiffness * (if (node.isPerimeter) 0.65f else 0.85f)
            node.forceX -= anchorK * dispX
            node.forceY -= anchorK * dispY

            // Velocity Damping
            node.forceX -= damping * node.velocityX
            node.forceY -= damping * node.velocityY
        }

        // 4. Hooke's Law for all Structural & Shear Springs
        for (spring in springs) {
            val nodeA = allNodes[spring.nodeA]
            val nodeB = allNodes[spring.nodeB]

            val deltaX = nodeB.currentX - nodeA.currentX
            val deltaY = nodeB.currentY - nodeA.currentY
            val currentDist = hypot(deltaX, deltaY).coerceAtLeast(0.001f)

            val diff = currentDist - spring.restLength
            val k = springStiffness * spring.stiffnessMultiplier
            val forceMag = k * diff

            val fx = (deltaX / currentDist) * forceMag
            val fy = (deltaY / currentDist) * forceMag

            nodeA.forceX += fx
            nodeA.forceY += fy
            nodeB.forceX -= fx
            nodeB.forceY -= fy
        }

        // 5. Hydrostatic Internal Fluid / Gas Pressure Model
        // Computes current polygon area and applies normal pressure forces outward
        val currentArea = calculatePerimeterArea()
        currentAreaRatio = (currentArea / restPolygonArea.coerceAtLeast(1f)).coerceIn(0.4f, 1.8f)
        val areaDiff = restPolygonArea - currentArea // > 0 when compressed

        if (areaDiff > 0f) {
            val pressureFactor = (areaDiff / restPolygonArea) * internalPressure * 450f
            for (i in 0 until outerNodeCount) {
                val prevI = (i - 1 + outerNodeCount) % outerNodeCount
                val nextI = (i + 1) % outerNodeCount
                val nPrev = allNodes[prevI]
                val nNext = allNodes[nextI]

                // Normal vector perpendicular to chord between prev and next
                val chordX = nNext.currentX - nPrev.currentX
                val chordY = nNext.currentY - nPrev.currentY
                val chordLen = hypot(chordX, chordY).coerceAtLeast(0.001f)

                // Outward normal in 2D (counter-clockwise winding: (dy, -dx))
                val normalX = chordY / chordLen
                val normalY = -chordX / chordLen

                val node = allNodes[i]
                node.forceX += normalX * pressureFactor
                node.forceY += normalY * pressureFactor
            }
        }

        // 6. Touch Contacts: Local Indentation, Drag, Pinch & Stretch
        var totalIndentDistance = 0f
        var maxIndentDist = 0f
        var dominantTouchAngle = 0f

        for (contact in contacts) {
            val localTouchX = contact.x - (containerCenter.x + centerX)
            val localTouchY = contact.y - (containerCenter.y + centerY)
            val touchAngle = atan2(localTouchY, localTouchX)

            // Finger velocity transfer (dynamic drag force)
            val touchVelX = (contact.x - contact.previousX) / clampedDt
            val touchVelY = (contact.y - contact.previousY) / clampedDt
            centerVelX += touchVelX * 0.04f
            centerVelY += touchVelY * 0.04f

            for (node in allNodes) {
                val distToTouch = hypot(node.currentX - localTouchX, node.currentY - localTouchY)
                val touchRadius = contact.radius * contact.pressure

                if (distToTouch < touchRadius) {
                    val factor = 1.0f - (distToTouch / touchRadius)
                    val smoothFactor = factor * factor * (3f - 2f * factor) // Smooth Hermite falloff

                    // Indentation push
                    val pushDist = smoothFactor * (touchRadius * 0.75f)
                    val pullDirX = localTouchX - node.currentX
                    val pullDirY = localTouchY - node.currentY

                    val touchForce = 520f * contact.pressure * smoothFactor
                    node.forceX += pullDirX * (touchForce / 20f)
                    node.forceY += pullDirY * (touchForce / 20f)

                    // Inject finger drag velocity into nearby nodes
                    node.velocityX += touchVelX * smoothFactor * 0.15f
                    node.velocityY += touchVelY * smoothFactor * 0.15f

                    if (node.isPerimeter) {
                        totalIndentDistance += pushDist
                        if (pushDist > maxIndentDist) {
                            maxIndentDist = pushDist
                            dominantTouchAngle = touchAngle
                        }
                    }
                }
            }
        }

        // Multi-touch Pinch, Stretch & Twist Interaction
        if (contacts.size >= 2) {
            val c1 = contacts[0]
            val c2 = contacts[1]
            val touchDist = hypot(c1.x - c2.x, c1.y - c2.y)
            val prevTouchDist = hypot(c1.previousX - c2.previousX, c1.previousY - c2.previousY)
            val distDelta = touchDist - prevTouchDist

            val pinchAngle = atan2(c2.y - c1.y, c2.x - c1.x)
            val prevPinchAngle = atan2(c2.previousY - c1.previousY, c2.previousX - c1.previousX)
            val twistDelta = (pinchAngle - prevPinchAngle)

            // Squeeze pinch or pull elongation with Poisson orthogonal bulge
            if (kotlin.math.abs(distDelta) > 0.3f) {
                val stretchFactor = distDelta * 3.2f
                for (i in 0 until outerNodeCount) {
                    val node = allNodes[i]
                    val angleDiff = kotlin.math.abs(node.baseAngle - pinchAngle)
                    val normDiff = if (angleDiff > PI.toFloat()) (2f * PI.toFloat() - angleDiff) else angleDiff
                    val alignFactor = cos(normDiff) // +1 aligned with stretch axis, -1 orthogonal axis

                    // Longitudinal stretch/compression + Transverse Poisson bulging
                    val radialForce = (alignFactor * stretchFactor * 35f) - (cos(2f * normDiff) * stretchFactor * 18f)
                    node.forceX += cos(node.baseAngle) * radialForce
                    node.forceY += sin(node.baseAngle) * radialForce
                }
            }

            // Angular twist deformation
            if (kotlin.math.abs(twistDelta) > 0.001f) {
                val twistTorque = twistDelta * 450f
                for (i in 0 until outerNodeCount) {
                    val node = allNodes[i]
                    // Tangential force
                    val tanX = -sin(node.baseAngle) * twistTorque
                    val tanY = cos(node.baseAngle) * twistTorque
                    node.forceX += tanX
                    node.forceY += tanY
                }
            }
        }

        // 7. Numerical Time Integration (Symplectic Euler with high stability)
        var totalCurrentDisp = 0f
        for (node in allNodes) {
            val accelX = node.forceX / mass
            val accelY = node.forceY / mass

            node.velocityX += accelX * clampedDt
            node.velocityY += accelY * clampedDt

            // Velocity damping curve
            node.velocityX *= (1.0f - damping * 0.012f).coerceIn(0.85f, 0.999f)
            node.velocityY *= (1.0f - damping * 0.012f).coerceIn(0.85f, 0.999f)

            // Velocity ceiling to prevent tunneling
            node.velocityX = node.velocityX.coerceIn(-1200f, 1200f)
            node.velocityY = node.velocityY.coerceIn(-1200f, 1200f)

            node.currentX += node.velocityX * clampedDt
            node.currentY += node.velocityY * clampedDt

            if (node.isPerimeter) {
                val baseX = node.baseRadius * cos(node.baseAngle)
                val baseY = node.baseRadius * sin(node.baseAngle)
                totalCurrentDisp += hypot(node.currentX - baseX, node.currentY - baseY)
            }
        }

        // 8. Update Squish Intensity (0.0 to 1.0)
        val rawSquish = (totalCurrentDisp / (outerNodeCount * baseRadius * 0.26f)).coerceIn(0f, 1f)
        squishIntensity = squishIntensity + (rawSquish - squishIntensity) * 0.28f
        squishAngle = dominantTouchAngle

        // 9. Center of Mass Restoring Elastic Spring
        val centerK = 110f
        val centerDamp = 8.5f
        val centerFx = -centerK * centerX - centerDamp * centerVelX
        val centerFy = -centerK * centerY - centerDamp * centerVelY

        centerVelX += centerFx * clampedDt
        centerVelY += centerFy * clampedDt
        centerX += centerVelX * clampedDt
        centerY += centerVelY * clampedDt
    }

    /**
     * Builds a smooth, organic curved Path along the perimeter of the deformed nodes.
     * Uses cubic Catmull-Rom splines for zero-crease, silky smooth silicone/dough deformation.
     */
    fun createCurvedPath(center: Offset, scale: Float = 1.0f): Path {
        val path = Path()
        if (allNodes.isEmpty()) return path

        val n = outerNodeCount
        val pts = Array(n) { i ->
            Offset(
                center.x + (centerX + allNodes[i].currentX) * scale,
                center.y + (centerY + allNodes[i].currentY) * scale
            )
        }

        path.moveTo(pts[0].x, pts[0].y)

        // Cubic Catmull-Rom splines through all 32 perimeter nodes
        for (i in 0 until n) {
            val p0 = pts[(i - 1 + n) % n]
            val p1 = pts[i]
            val p2 = pts[(i + 1) % n]
            val p3 = pts[(i + 2) % n]

            val tension = 0.5f
            val cp1x = p1.x + (p2.x - p0.x) * (tension / 3f)
            val cp1y = p1.y + (p2.y - p0.y) * (tension / 3f)
            val cp2x = p2.x - (p3.x - p1.x) * (tension / 3f)
            val cp2y = p2.y - (p3.y - p1.y) * (tension / 3f)

            path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
        }

        path.close()
        return path
    }

    /**
     * Returns the 5 fluted decorative top fold paths that deform with the top nodes.
     */
    fun getCrestFoldLines(center: Offset, scale: Float = 1.0f): List<Path> {
        val paths = mutableListOf<Path>()
        val crestIndices = listOf(19, 22, 25, 28, 30)

        for (idx in crestIndices) {
            val rimNode = allNodes[idx % outerNodeCount]
            val rimPt = Offset(
                center.x + (centerX + rimNode.currentX) * scale,
                center.y + (centerY + rimNode.currentY) * scale
            )
            val innerPt = Offset(
                center.x + (centerX + rimNode.currentX * 0.65f) * scale,
                center.y + (centerY + rimNode.currentY * 0.65f) * scale
            )

            val foldPath = Path().apply {
                moveTo(rimPt.x, rimPt.y)
                quadraticBezierTo(
                    (rimPt.x + innerPt.x) / 2f + 5f * scale,
                    (rimPt.y + innerPt.y) / 2f,
                    innerPt.x, innerPt.y
                )
            }
            paths.add(foldPath)
        }
        return paths
    }

    /**
     * Returns calculated eye anchor positions based on physical node deformation.
     */
    fun getEyeAnchors(center: Offset, scale: Float = 1.0f): Pair<Offset, Offset> {
        val eyeLeftBase = Offset(-baseRadius * 0.32f, -baseRadius * 0.12f)
        val eyeRightBase = Offset(baseRadius * 0.32f, -baseRadius * 0.12f)

        val leftNode = allNodes[27 % outerNodeCount]
        val rightNode = allNodes[21 % outerNodeCount]

        val leftDispX = (leftNode.currentX - leftNode.baseRadius * cos(leftNode.baseAngle)) * 0.45f
        val leftDispY = (leftNode.currentY - leftNode.baseRadius * sin(leftNode.baseAngle)) * 0.45f

        val rightDispX = (rightNode.currentX - rightNode.baseRadius * cos(rightNode.baseAngle)) * 0.45f
        val rightDispY = (rightNode.currentY - rightNode.baseRadius * sin(rightNode.baseAngle)) * 0.45f

        val leftEye = Offset(
            center.x + (centerX + eyeLeftBase.x + leftDispX) * scale,
            center.y + (centerY + eyeLeftBase.y + leftDispY) * scale
        )
        val rightEye = Offset(
            center.x + (centerX + eyeRightBase.x + rightDispX) * scale,
            center.y + (centerY + eyeRightBase.y + rightDispY) * scale
        )

        return Pair(leftEye, rightEye)
    }

    /**
     * Calculates cheek blush anchors that inflate and move during squishing.
     */
    fun getCheekAnchors(center: Offset, scale: Float = 1.0f): Pair<Offset, Offset> {
        val cheekLeftBase = Offset(-baseRadius * 0.52f, baseRadius * 0.15f)
        val cheekRightBase = Offset(baseRadius * 0.52f, baseRadius * 0.15f)

        val leftNode = allNodes[30 % outerNodeCount]
        val rightNode = allNodes[18 % outerNodeCount]

        val leftDispX = (leftNode.currentX - leftNode.baseRadius * cos(leftNode.baseAngle)) * 0.55f
        val leftDispY = (leftNode.currentY - leftNode.baseRadius * sin(leftNode.baseAngle)) * 0.55f

        val rightDispX = (rightNode.currentX - rightNode.baseRadius * cos(rightNode.baseAngle)) * 0.55f
        val rightDispY = (rightNode.currentY - rightNode.baseRadius * sin(rightNode.baseAngle)) * 0.55f

        val leftCheek = Offset(
            center.x + (centerX + cheekLeftBase.x + leftDispX) * scale,
            center.y + (centerY + cheekLeftBase.y + leftDispY) * scale
        )
        val rightCheek = Offset(
            center.x + (centerX + cheekRightBase.x + rightDispX) * scale,
            center.y + (centerY + cheekRightBase.y + rightDispY) * scale
        )

        return Pair(leftCheek, rightCheek)
    }

    /**
     * Returns the "67" emblem position on the belly.
     */
    fun getEmblemAnchor(center: Offset, scale: Float = 1.0f): Offset {
        val bottomNode = allNodes[8 % outerNodeCount]
        val dispX = (bottomNode.currentX - bottomNode.baseRadius * cos(bottomNode.baseAngle)) * 0.35f
        val dispY = (bottomNode.currentY - bottomNode.baseRadius * sin(bottomNode.baseAngle)) * 0.35f

        return Offset(
            center.x + (centerX + dispX) * scale,
            center.y + (centerY + baseRadius * 0.28f + dispY) * scale
        )
    }

    /**
     * Returns top crest anchor for hat accessories.
     */
    fun getHatAnchor(center: Offset, scale: Float = 1.0f): Offset {
        val topNode = allNodes[24 % outerNodeCount]
        return Offset(
            center.x + (centerX + topNode.currentX * 0.9f) * scale,
            center.y + (centerY + topNode.currentY * 0.9f - 24f) * scale
        )
    }

    /**
     * Returns the list of outer perimeter node offsets for Canvas rendering and normals.
     */
    fun getPerimeterPoints(center: Offset, scale: Float = 1.0f): List<Offset> {
        return List(outerNodeCount) { i ->
            Offset(
                center.x + (centerX + allNodes[i].currentX) * scale,
                center.y + (centerY + allNodes[i].currentY) * scale
            )
        }
    }
}
