package com.example.screens.Pinball

import android.util.Log
import org.jbox2d.callbacks.ContactImpulse
import org.jbox2d.callbacks.ContactListener
import org.jbox2d.collision.Manifold
import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.*
import org.jbox2d.dynamics.contacts.Contact
import org.jbox2d.dynamics.joints.RevoluteJoint
import org.jbox2d.dynamics.joints.RevoluteJointDef
import kotlin.math.*
import kotlin.random.Random

enum class SpeedMode(val gravity: Float, val flipperSpeedMultiplier: Float) {
    TURTLE(6f, 0.8f),
    BUNNY(10f, 1.0f),
    ROCKET(14f, 1.2f)
}

enum class EntityType {
    WALL,
    BUMPER,
    SLINGSHOT,
    STAR,
    DRAIN,
    FLIPPER_LEFT,
    FLIPPER_RIGHT,
    BALL
}

data class UserDataPayload(
    val type: EntityType,
    val id: Int = 0,
    val extra: Any? = null
)

class PinballPhysicsEngine(
    val onScoreAdded: (Int) -> Unit,
    val onSoundTriggered: (String) -> Unit,
    val onBumperHit: (Int) -> Unit,
    val onStarHit: (Int) -> Unit,
    val onAllStarsLit: () -> Unit
) : ContactListener {

    val worldWidth = 9.0f
    val worldHeight = 16.0f

    private val gravityVec = Vec2(0f, 10f)
    var world: World = World(gravityVec)

    private val balls = mutableListOf<Body>()
    private val ballCreationTimes = mutableMapOf<Body, Long>()
    private val ballLastMoveTimes = mutableMapOf<Body, Long>()
    private val ballLastPositions = mutableMapOf<Body, Vec2>()

    // Flippers
    var leftFlipperJoint: RevoluteJoint? = null
    var rightFlipperJoint: RevoluteJoint? = null
    private var leftFlipperBody: Body? = null
    private var rightFlipperBody: Body? = null

    // Speed mode
    var currentSpeedMode: SpeedMode = SpeedMode.BUNNY
        set(value) {
            field = value
            world.gravity = Vec2(0f, value.gravity)
            updateFlipperMotors()
        }

    var autoPawsEnabled: Boolean = true

    // Stars lit state (3 stars)
    val starLitStates = booleanArrayOf(false, false, false)

    private val pendingDrainBalls = mutableSetOf<Body>()

    // Unstuck debug metrics
    var unstuckLowSpeedCount = 0
    var unstuckNoDisplacementCount = 0
    var unstuckOnFlipperCount = 0
    var unstuckOutOfBoundsCount = 0
    var unstuckNaNCount = 0
    var maxRecordedSpeed = 0f

    init {
        world.setContactListener(this)
        buildFieldGeometry()
        spawnBall()
    }

    private fun buildFieldGeometry() {
        val groundBodyDef = BodyDef().apply {
            type = BodyType.STATIC
            position.set(0f, 0f)
        }
        val groundBody = world.createBody(groundBodyDef)

        // 1. Smooth outer walls: Left wall, Arch, Right wall, and bottom chutes
        // We use thick PolygonShapes and smooth chains with >= 12 segments for the arch
        buildSmoothWalls(groundBody)

        // 2. Bumpers (4 round bumpers: (3, 5.5), (6, 5.5), (4.5, 7.5), (4.5, 4), radius 0.7)
        val bumperPositions = listOf(
            Vec2(3.0f, 5.5f),
            Vec2(6.0f, 5.5f),
            Vec2(4.5f, 7.5f),
            Vec2(4.5f, 4.0f)
        )
        bumperPositions.forEachIndexed { index, pos ->
            val bDef = BodyDef().apply {
                type = BodyType.STATIC
                position.set(pos.x, pos.y)
            }
            val bBody = world.createBody(bDef)
            val shape = CircleShape().apply { m_radius = 0.7f }
            val fDef = FixtureDef().apply {
                this.shape = shape
                restitution = 0.8f
                friction = 0.1f
                userData = UserDataPayload(EntityType.BUMPER, id = index)
            }
            bBody.createFixture(fDef)
        }

        // 3. Slingshots (2 soft triangles above flippers: left (1.6, 11), right (7.4, 11))
        buildSlingshots()

        // 4. Star targets (3 sensors in upper arch: (3.0, 2.0), (4.5, 1.2), (6.0, 2.0))
        val starPositions = listOf(
            Vec2(3.0f, 2.0f),
            Vec2(4.5f, 1.2f),
            Vec2(6.0f, 2.0f)
        )
        starPositions.forEachIndexed { index, pos ->
            val sDef = BodyDef().apply {
                type = BodyType.STATIC
                position.set(pos.x, pos.y)
            }
            val sBody = world.createBody(sDef)
            val shape = CircleShape().apply { m_radius = 0.45f }
            val fDef = FixtureDef().apply {
                this.shape = shape
                isSensor = true // Trigger only, no solid bounce
                userData = UserDataPayload(EntityType.STAR, id = index)
            }
            sBody.createFixture(fDef)
        }

        // 5. Drain sensor (band below flippers at y = 15.5)
        val dDef = BodyDef().apply {
            type = BodyType.STATIC
            position.set(4.5f, 15.5f)
        }
        val dBody = world.createBody(dDef)
        val dShape = PolygonShape().apply {
            setAsBox(4.5f, 0.4f)
        }
        val dFixture = FixtureDef().apply {
            shape = dShape
            isSensor = true
            userData = UserDataPayload(EntityType.DRAIN)
        }
        dBody.createFixture(dFixture)

        // 6. Flippers
        buildFlippers(groundBody)
    }

    private fun buildSmoothWalls(groundBody: Body) {
        val wallThickness = 0.35f

        // Left straight wall (x=0, y=3 to 12)
        createThickWall(groundBody, Vec2(-wallThickness / 2, 7.5f), wallThickness / 2, 4.5f)

        // Right straight wall (x=9, y=3 to 12)
        createThickWall(groundBody, Vec2(9f + wallThickness / 2, 7.5f), wallThickness / 2, 4.5f)

        // Top Arch: Semicircle with radius 4.5 centered at (4.5, 3.0)
        // 16 segments for butter-smooth curve
        val numArchSegments = 16
        val archCenter = Vec2(4.5f, 3.0f)
        val archRadius = 4.5f
        for (i in 0 until numArchSegments) {
            val a1 = Math.PI - (i * Math.PI / numArchSegments)
            val a2 = Math.PI - ((i + 1) * Math.PI / numArchSegments)

            val p1 = Vec2(
                (archCenter.x + archRadius * cos(a1)).toFloat(),
                (archCenter.y - archRadius * sin(a1)).toFloat()
            )
            val p2 = Vec2(
                (archCenter.x + archRadius * cos(a2)).toFloat(),
                (archCenter.y - archRadius * sin(a2)).toFloat()
            )

            val mid = Vec2((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
            val len = sqrt((p2.x - p1.x).pow(2) + (p2.y - p1.y).pow(2))
            val angle = atan2(p2.y - p1.y, p2.x - p1.x)

            val shape = PolygonShape().apply {
                setAsBox(len / 2f, wallThickness / 2f, Vec2(0f, 0f), angle)
            }
            val fDef = FixtureDef().apply {
                this.shape = shape
                friction = 0.1f
                restitution = 0.4f
                userData = UserDataPayload(EntityType.WALL)
            }
            val b = world.createBody(BodyDef().apply {
                type = BodyType.STATIC
                position.set(mid.x, mid.y)
            })
            b.createFixture(fDef)
        }

        // Left chute: from (0, 12) to left flipper pivot (2.2, 14)
        createSlantedGuide(Vec2(0f, 12f), Vec2(2.2f, 14f), wallThickness)

        // Right chute: from (9, 12) to right flipper pivot (6.8, 14)
        createSlantedGuide(Vec2(9f, 12f), Vec2(6.8f, 14f), wallThickness)
    }

    private fun createThickWall(groundBody: Body, pos: Vec2, halfWidth: Float, halfHeight: Float) {
        val bDef = BodyDef().apply {
            type = BodyType.STATIC
            position.set(pos.x, pos.y)
        }
        val b = world.createBody(bDef)
        val shape = PolygonShape().apply {
            setAsBox(halfWidth, halfHeight)
        }
        val fDef = FixtureDef().apply {
            this.shape = shape
            friction = 0.1f
            restitution = 0.3f
            userData = UserDataPayload(EntityType.WALL)
        }
        b.createFixture(fDef)
    }

    private fun createSlantedGuide(p1: Vec2, p2: Vec2, thickness: Float) {
        val mid = Vec2((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
        val len = sqrt((p2.x - p1.x).pow(2) + (p2.y - p1.y).pow(2))
        val angle = atan2(p2.y - p1.y, p2.x - p1.x)

        val shape = PolygonShape().apply {
            setAsBox(len / 2f, thickness / 2f, Vec2(0f, 0f), angle)
        }
        val b = world.createBody(BodyDef().apply {
            type = BodyType.STATIC
            position.set(mid.x, mid.y)
        })
        b.createFixture(FixtureDef().apply {
            this.shape = shape
            friction = 0.1f
            restitution = 0.35f
            userData = UserDataPayload(EntityType.WALL)
        })
    }

    private fun buildSlingshots() {
        // Left slingshot triangle
        val leftTri = arrayOf(
            Vec2(1.2f, 10.0f),
            Vec2(2.2f, 11.8f),
            Vec2(1.2f, 12.0f)
        )
        val lShape = PolygonShape().apply { set(leftTri, 3) }
        val lBody = world.createBody(BodyDef().apply { type = BodyType.STATIC })
        lBody.createFixture(FixtureDef().apply {
            shape = lShape
            restitution = 1.0f
            friction = 0.05f
            userData = UserDataPayload(EntityType.SLINGSHOT, id = 0)
        })

        // Right slingshot triangle
        val rightTri = arrayOf(
            Vec2(7.8f, 10.0f),
            Vec2(7.8f, 12.0f),
            Vec2(6.8f, 11.8f)
        )
        val rShape = PolygonShape().apply { set(rightTri, 3) }
        val rBody = world.createBody(BodyDef().apply { type = BodyType.STATIC })
        rBody.createFixture(FixtureDef().apply {
            shape = rShape
            restitution = 1.0f
            friction = 0.05f
            userData = UserDataPayload(EntityType.SLINGSHOT, id = 1)
        })
    }

    private fun buildFlippers(groundBody: Body) {
        val flipperLength = 1.9f
        val flipperThickness = 0.22f

        // Left flipper pivot (2.2, 14.0)
        val leftPivot = Vec2(2.2f, 14.0f)
        val lBodyDef = BodyDef().apply {
            type = BodyType.DYNAMIC
            position.set(leftPivot.x, leftPivot.y)
            bullet = true
        }
        leftFlipperBody = world.createBody(lBodyDef)
        val lShape = PolygonShape().apply {
            // Offset box so pivot is at the base
            setAsBox(flipperLength / 2f, flipperThickness, Vec2(flipperLength / 2f, 0f), 0f)
        }
        val lFixtureDef = FixtureDef().apply {
            shape = lShape
            density = 5.0f
            friction = 0.2f
            restitution = 0.6f
            userData = UserDataPayload(EntityType.FLIPPER_LEFT)
        }
        leftFlipperBody?.createFixture(lFixtureDef)

        val lJointDef = RevoluteJointDef().apply {
            initialize(groundBody, leftFlipperBody, leftPivot)
            lowerAngle = (-28f * Math.PI / 180f).toFloat() // Rest angle
            upperAngle = (28f * Math.PI / 180f).toFloat()  // Raised angle
            enableLimit = true
            maxMotorTorque = 1200f
            motorSpeed = -12f
            enableMotor = true
        }
        leftFlipperJoint = world.createJoint(lJointDef) as RevoluteJoint

        // Right flipper pivot (6.8, 14.0)
        val rightPivot = Vec2(6.8f, 14.0f)
        val rBodyDef = BodyDef().apply {
            type = BodyType.DYNAMIC
            position.set(rightPivot.x, rightPivot.y)
            bullet = true
        }
        rightFlipperBody = world.createBody(rBodyDef)
        val rShape = PolygonShape().apply {
            // Points to the left from pivot
            setAsBox(flipperLength / 2f, flipperThickness, Vec2(-flipperLength / 2f, 0f), 0f)
        }
        val rFixtureDef = FixtureDef().apply {
            shape = rShape
            density = 5.0f
            friction = 0.2f
            restitution = 0.6f
            userData = UserDataPayload(EntityType.FLIPPER_RIGHT)
        }
        rightFlipperBody?.createFixture(rFixtureDef)

        val rJointDef = RevoluteJointDef().apply {
            initialize(groundBody, rightFlipperBody, rightPivot)
            lowerAngle = (-28f * Math.PI / 180f).toFloat()
            upperAngle = (28f * Math.PI / 180f).toFloat()
            enableLimit = true
            maxMotorTorque = 1200f
            motorSpeed = 12f
            enableMotor = true
        }
        rightFlipperJoint = world.createJoint(rJointDef) as RevoluteJoint

        updateFlipperMotors()
    }

    private fun updateFlipperMotors() {
        val mult = currentSpeedMode.flipperSpeedMultiplier
        // Left down speed = -12 * mult, right down speed = +12 * mult
        if (leftFlipperJoint?.motorSpeed ?: 0f < 0f) {
            leftFlipperJoint?.motorSpeed = -12f * mult
        }
        if (rightFlipperJoint?.motorSpeed ?: 0f > 0f) {
            rightFlipperJoint?.motorSpeed = 12f * mult
        }
    }

    // Ручное удержание кнопок: автолапки не должны отпускать то, что держит палец.
    @Volatile private var manualLeft = false
    @Volatile private var manualRight = false
    private var leftOn = false
    private var rightOn = false

    /** Нажатие/отпускание левой кнопки пальцем. */
    fun holdLeft(pressed: Boolean) {
        manualLeft = pressed
        setLeftFlipperEngaged(pressed || false)
    }

    /** Нажатие/отпускание правой кнопки пальцем. */
    fun holdRight(pressed: Boolean) {
        manualRight = pressed
        setRightFlipperEngaged(pressed || false)
    }

    fun setLeftFlipperEngaged(engaged: Boolean) {
        val mult = currentSpeedMode.flipperSpeedMultiplier
        if (!engaged && manualLeft) return
        if (engaged) {
            leftFlipperJoint?.motorSpeed = 18f * mult
            if (!leftOn) onSoundTriggered("flip")
        } else {
            leftFlipperJoint?.motorSpeed = -12f * mult
        }
        leftOn = engaged
    }

    fun setRightFlipperEngaged(engaged: Boolean) {
        val mult = currentSpeedMode.flipperSpeedMultiplier
        if (!engaged && manualRight) return
        if (engaged) {
            rightFlipperJoint?.motorSpeed = -18f * mult
            if (!rightOn) onSoundTriggered("flip")
        } else {
            rightFlipperJoint?.motorSpeed = 12f * mult
        }
        rightOn = engaged
    }

    fun spawnBall(initialPos: Vec2 = Vec2(4.5f, 3.0f)) {
        if (balls.size >= 3) {
            // Remove oldest
            val oldest = balls.removeAt(0)
            ballCreationTimes.remove(oldest)
            ballLastMoveTimes.remove(oldest)
            ballLastPositions.remove(oldest)
            world.destroyBody(oldest)
        }

        val bDef = BodyDef().apply {
            type = BodyType.DYNAMIC
            position.set(initialPos.x, initialPos.y)
            bullet = true // Continuous collision detection prevents tunneling
            linearDamping = 0.05f
        }
        val ballBody = world.createBody(bDef)
        val shape = CircleShape().apply { m_radius = 0.4f }
        val fDef = FixtureDef().apply {
            this.shape = shape
            density = 1.0f
            friction = 0.2f
            restitution = 0.55f
            userData = UserDataPayload(EntityType.BALL)
        }
        ballBody.createFixture(fDef)

        // Random subtle initial velocity
        val randX = (Random.nextFloat() - 0.5f) * 2.0f
        val randY = Random.nextFloat() * 1.5f + 0.5f
        ballBody.linearVelocity = Vec2(randX, randY)

        balls.add(ballBody)
        val now = System.currentTimeMillis()
        ballCreationTimes[ballBody] = now
        ballLastMoveTimes[ballBody] = now
        ballLastPositions[ballBody] = Vec2(initialPos.x, initialPos.y)

        onSoundTriggered("spawn")
    }

    fun clearAllAndReset() {
        val toRemove = balls.toList()
        toRemove.forEach {
            world.destroyBody(it)
        }
        balls.clear()
        ballCreationTimes.clear()
        ballLastMoveTimes.clear()
        ballLastPositions.clear()
        starLitStates.fill(false)
        spawnBall()
    }

    fun getBalls(): List<Body> = balls.toList()

    fun getFlipperAngles(): Pair<Float, Float> {
        val l = leftFlipperBody?.angle ?: 0f
        val r = rightFlipperBody?.angle ?: 0f
        return Pair(l, r)
    }

    // Fixed timestep execution loop: 1/60s
    private val fixedDt = 1f / 60f
    private var accumulator = 0f
    private var lastTimeNanos = 0L

    fun step(currentTimeNanos: Long) {
        if (lastTimeNanos == 0L) {
            lastTimeNanos = currentTimeNanos
            return
        }

        val frameTimeSec = (currentTimeNanos - lastTimeNanos) / 1_000_000_000.0f
        lastTimeNanos = currentTimeNanos
        accumulator += min(frameTimeSec, 0.1f) // Clamp frame spike

        var steps = 0
        while (accumulator >= fixedDt && steps < 5) {
            world.step(fixedDt, 8, 3)

            // Process any balls that fell into drain
            if (pendingDrainBalls.isNotEmpty()) {
                pendingDrainBalls.forEach { ball ->
                    ball.setTransform(Vec2(4.5f, 3.0f), 0f)
                    ball.linearVelocity = Vec2((Random.nextFloat() - 0.5f) * 2.0f, 1.2f)
                }
                pendingDrainBalls.clear()
            }

            // Auto-paws trigger check
            if (autoPawsEnabled) {
                checkAutoPaws()
            }

            // Post-step ball speed clamping & anti-stuck checks
            checkBallsPhysics()

            accumulator -= fixedDt
            steps++
        }
    }

    private fun checkAutoPaws() {
        balls.forEach { ball ->
            val pos = ball.position
            val vel = ball.linearVelocity

            if (vel.y > 0.5f && pos.y in 12.0f..14.5f) {
                // If ball heading towards left flipper
                if (pos.x in 1.2f..4.0f) {
                    setLeftFlipperEngaged(true)
                }
                // If ball heading towards right flipper
                if (pos.x in 5.0f..7.8f) {
                    setRightFlipperEngaged(true)
                }
            } else if (pos.y < 11.5f || pos.y > 15.0f) {
                // Auto release if ball moved away
                setLeftFlipperEngaged(false)
                setRightFlipperEngaged(false)
            }
        }
    }

    private var lastUnstuckCheckTime = 0L

    private fun checkBallsPhysics() {
        val maxSpeed = 16.0f
        val now = System.currentTimeMillis()
        val doUnstuckCheck = (now - lastUnstuckCheckTime >= 100L)
        if (doUnstuckCheck) {
            lastUnstuckCheckTime = now
        }

        val ballsCopy = balls.toList()
        for (ball in ballsCopy) {
            val vel = ball.linearVelocity
            val pos = ball.position

            // NaN or infinite speed protection
            if (vel.x.isNaN() || vel.y.isNaN() || pos.x.isNaN() || pos.y.isNaN()) {
                unstuckNaNCount++
                Log.d("PinballUnstuck", "NaN detected in ball coords. Resetting ball to center.")
                ball.setTransform(Vec2(4.5f, 3.0f), 0f)
                ball.linearVelocity = Vec2(0f, 1f)
                continue
            }

            // Speed clamping
            var speed = vel.length()
            if (speed > maxSpeed) {
                ball.linearVelocity = vel.mul(maxSpeed / speed)
                speed = maxSpeed
            }
            if (speed > maxRecordedSpeed) {
                maxRecordedSpeed = speed
            }

            // Unstuck rules check
            if (doUnstuckCheck) {
                // Rule 4: Out of bounds (margin 1.0)
                if (pos.x < -1.0f || pos.x > worldWidth + 1.0f || pos.y < -1.0f || pos.y > worldHeight + 1.5f) {
                    unstuckOutOfBoundsCount++
                    Log.d("PinballUnstuck", "Ball out of bounds at ($pos.x, $pos.y). Resetting to top.")
                    ball.setTransform(Vec2(4.5f, 3.0f), 0f)
                    ball.linearVelocity = Vec2(0f, 1f)
                    continue
                }

                // Rule 1: Low speed (< 0.3 for > 1.5s not on flippers)
                val lastPos = ballLastPositions[ball] ?: pos
                val displacement = (pos.sub(lastPos)).length()
                if (displacement > 0.2f) {
                    ballLastMoveTimes[ball] = now
                    ballLastPositions[ball] = Vec2(pos.x, pos.y)
                }

                val timeSinceMove = now - (ballLastMoveTimes[ball] ?: now)

                if (pos.y < 13.5f) { // Above flippers
                    if (speed < 0.3f && timeSinceMove > 1500L) {
                        unstuckLowSpeedCount++
                        val impulseX = if (Random.nextBoolean()) 1.5f else -1.5f
                        Log.d("PinballUnstuck", "Low speed detected above flippers. Nudging ball.")
                        ball.applyLinearImpulse(Vec2(impulseX, -1.8f), ball.worldCenter)
                        ballLastMoveTimes[ball] = now
                    } else if (timeSinceMove > 2000L) {
                        unstuckNoDisplacementCount++
                        Log.d("PinballUnstuck", "No displacement for > 2s. Nudging ball.")
                        ball.applyLinearImpulse(Vec2(1.2f, -2.0f), ball.worldCenter)
                        ballLastMoveTimes[ball] = now
                    }
                } else if (pos.y in 13.5f..14.5f) {
                    // Rule 3: Resting on flippers for > 1.2s -> flip automatically!
                    if (speed < 0.3f && timeSinceMove > 1200L) {
                        unstuckOnFlipperCount++
                        Log.d("PinballUnstuck", "Ball resting on flippers for > 1.2s. Flipping auto!")
                        if (pos.x < 4.5f) {
                            setLeftFlipperEngaged(true)
                        } else {
                            setRightFlipperEngaged(true)
                        }
                        ballLastMoveTimes[ball] = now
                    }
                }
            }
        }
    }

    // ========================================================
    // 💥 CONTACT LISTENER
    // ========================================================
    override fun beginContact(contact: Contact) {
        val fA = contact.fixtureA
        val fB = contact.fixtureB
        val uA = fA.userData as? UserDataPayload
        val uB = fB.userData as? UserDataPayload

        handleContactPair(uA, uB, fA, fB, contact)
        handleContactPair(uB, uA, fB, fA, contact)
    }

    private fun handleContactPair(
        uA: UserDataPayload?,
        uB: UserDataPayload?,
        fA: Fixture,
        fB: Fixture,
        contact: Contact
    ) {
        if (uA == null || uB == null) return

        if (uA.type == EntityType.BALL) {
            val ballBody = fA.body

            when (uB.type) {
                EntityType.BUMPER -> {
                    // Impulse 3.5 along normal from bumper center, +10 points
                    val bumperCenter = fB.body.position
                    val dir = ballBody.position.sub(bumperCenter)
                    val len = dir.length()
                    val norm = if (len > 0.001f) dir.mul(1f / len) else Vec2(0f, -1f)
                    ballBody.applyLinearImpulse(norm.mul(3.5f), ballBody.worldCenter)

                    onScoreAdded(10)
                    onSoundTriggered("bumper")
                    onBumperHit(uB.id)
                }
                EntityType.SLINGSHOT -> {
                    // Impulse 2.5, +5 points
                    val dir = if (uB.id == 0) Vec2(1.5f, -2.0f) else Vec2(-1.5f, -2.0f)
                    ballBody.applyLinearImpulse(dir, ballBody.worldCenter)

                    onScoreAdded(5)
                    onSoundTriggered("sling")
                }
                EntityType.STAR -> {
                    // Sensor touched: lights up yellow, sound chime, +20 points
                    val starIndex = uB.id
                    if (!starLitStates[starIndex]) {
                        starLitStates[starIndex] = true
                        onScoreAdded(20)
                        onSoundTriggered("chime")
                        onStarHit(starIndex)

                        if (starLitStates.all { it }) {
                            onSoundTriggered("fanfare_short")
                            onAllStarsLit()
                            starLitStates.fill(false) // reset after salute
                        }
                    }
                }
                EntityType.DRAIN -> {
                    // Drain sensor touched: sound blub, respawn at (4.5, 3.0) after step
                    onSoundTriggered("blub")
                    pendingDrainBalls.add(ballBody)
                }
                EntityType.WALL -> {
                    onSoundTriggered("wall")
                }
                else -> { /* No op */ }
            }
        }
    }

    override fun endContact(contact: Contact) {}
    override fun preSolve(contact: Contact, oldManifold: Manifold) {}
    override fun postSolve(contact: Contact, impulse: ContactImpulse) {}
}
