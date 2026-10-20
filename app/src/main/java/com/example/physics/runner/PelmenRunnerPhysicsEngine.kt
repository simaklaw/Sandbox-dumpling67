package com.example.physics.runner

import org.jbox2d.callbacks.ContactImpulse
import org.jbox2d.callbacks.ContactListener
import org.jbox2d.collision.Manifold
import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.Fixture
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import org.jbox2d.dynamics.contacts.Contact
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 🌍 Distinct biomes for the progressive endless track.
 */
enum class RunnerBiome(val displayName: String, val startDistanceMeters: Int) {
    KITCHEN("Кухня 67", 0),
    ICE_FREEZER("Ледяная камера", 160),
    STEAM_CLOUDS("Облачная Чайная", 340),
    CLOUD_KITCHEN("Облачная кухня", 500);

    companion object {
        val ICE_CHAMBER get() = ICE_FREEZER
    }
}

/**
 * 🏃‍♂️ Entity classifications for the Pelmen-Six JBox2D physics runner.
 */
enum class RunnerEntityType {
    HERO,
    GROUND,
    PLATFORM,
    PLATFORM_ICE,        // Slippery Ice platform (friction = 0.01f)
    TRAMPOLINE,          // Bouncy mushroom spring pad (super high vertical rebound)
    BUMPER,              // Dumpling pot lid / pinball bounce bumper
    BUMPER_JELLY,        // Bouncy translucent marmalade jelly (restitution = 1.95f)
    STEAM_VENT,          // Steamy geyser upward wind lift
    OBSTACLE_BUTTER,     // Rolling or stationary butter/cheese hurdle
    OBSTACLE_HAZARD,     // Pepper / fork obstacle
    COLLECTIBLE_COIN,    // Star / Coin (+10 points)
    COLLECTIBLE_DUMPLING,// Golden Pelmen (+50 points)
    POWERUP_SHIELD,      // Bubble Shield (absorbs obstacle impact)
    POWERUP_MAGNET,      // Golden Coin Magnet (attracts coins)
    BOOSTER_GATE         // Speed 67 Turbo Gate
}

data class RunnerUserData(
    val type: RunnerEntityType,
    val id: Long = 0,
    var isCollected: Boolean = false,
    val width: Float = 1f,
    val height: Float = 1f,
    val extra: Any? = null
)

data class RunnerObstacleSnapshot(
    val id: Long,
    val type: RunnerEntityType,
    val position: Vec2,
    val width: Float,
    val height: Float,
    val angle: Float,
    val isCollected: Boolean = false
)

data class PhysicsDebugContact(
    val point: Vec2,
    val normal: Vec2,
    val isTouching: Boolean
)

/**
 * High-performance, toddler-friendly 2D physics simulation of "Пельмень-Сикс" in runner mode.
 * Uses JBox2D for rigid-body kinematics, collision response, elastic bounces, and sensory triggers.
 */
class PelmenRunnerPhysicsEngine(
    val onGroundContactChanged: (Boolean) -> Unit = {},
    val onBounce: (RunnerEntityType, Vec2, Float) -> Unit = { _, _, _ -> },
    val onCollect: (RunnerEntityType, Long, Int) -> Unit = { _, _, _ -> },
    val onSpeedBoost: () -> Unit = {},
    val onObstacleHit: (RunnerEntityType, Float) -> Unit = { _, _ -> },
    val onShieldAbsorb: () -> Unit = {},
    val onBiomeChanged: (RunnerBiome) -> Unit = {},
    val onSteamVent: () -> Unit = {},
    val onJellyBounce: () -> Unit = {},
    val onIceSlide: () -> Unit = {}
) : ContactListener {

    val gravity = Vec2(0f, 25f) // Downward gravity in screen-space
    val world: World = World(gravity)

    // Base runner parameters
    val groundY = 11.0f            // Meters (standard ground baseline)
    var baseRunSpeed: Float = 7.5f // m/s forward speed
    var boostSpeed: Float = 13.5f  // m/s turbo speed

    // Hero Body
    val heroRadius = 0.58f
    lateinit var heroBody: Body
        private set

    var groundContactCount: Int = 0
    var isGrounded: Boolean = false
        private set

    var jumpsRemaining: Int = 2
    var isBoosting: Boolean = false
        private set
    var boostTimer: Float = 0f
    var comboCount: Int = 0

    // PowerUp Active States
    var isShieldActive: Boolean = false
        private set
    var isMagnetActive: Boolean = false
        private set
    var shieldTimer: Float = 0f
        private set
    var magnetTimer: Float = 0f
        private set

    fun activateShield(duration: Float = 12f) {
        isShieldActive = true
        shieldTimer = duration
    }

    fun deactivateShield() {
        isShieldActive = false
        shieldTimer = 0f
    }

    fun activateMagnet(duration: Float = 10f) {
        isMagnetActive = true
        magnetTimer = duration
    }

    fun deactivateMagnet() {
        isMagnetActive = false
        magnetTimer = 0f
    }

    // 🧊 Ice Chamber ("Ледяная камера") JBox2D Friction & Sliding Parameters
    val normalGroundFriction: Float = 0.35f
    val normalHeroFriction: Float = 0.20f
    val iceFrictionCoefficient: Float = 0.003f // Ultra-low friction for ice in JBox2D
    val iceHeroFriction: Float = 0.01f         // Hero contact friction when sliding on ice
    val iceSlideGlideMultiplier: Float = 1.38f // Forward skating glide speed boost

    var isSlidingOnIce: Boolean = false
        private set
    var isInsideSteamVent: Boolean = false
        private set
    var iceSlideTimer: Float = 0f
        private set
    var totalDistanceSlidOnIce: Float = 0f
        private set

    val isIceChamberActive: Boolean
        get() = currentBiome == RunnerBiome.ICE_FREEZER

    var currentBiome: RunnerBiome = RunnerBiome.KITCHEN
        private set

    fun getBiomeForPosition(x: Float): RunnerBiome {
        return when {
            x < 160f -> RunnerBiome.KITCHEN
            x < 340f -> RunnerBiome.ICE_FREEZER
            x < 500f -> RunnerBiome.STEAM_CLOUDS
            else -> RunnerBiome.CLOUD_KITCHEN
        }
    }

    // Procedural generation tracking
    private var nextSpawnX: Float = 0f
    private val activeBodies = mutableListOf<Body>()
    private val pendingDestroyBodies = mutableSetOf<Body>()
    private val random = Random(42)
    private var idCounter: Long = 100L

    init {
        world.setContactListener(this)
        createHero()
        // Generate initial track
        buildGroundSegment(startX = -10f, endX = 25f, height = groundY)
        nextSpawnX = 25f
        generateTrackAhead(targetX = 60f)
    }

    private fun createHero() {
        val bDef = BodyDef().apply {
            type = BodyType.DYNAMIC
            position.set(3f, groundY - heroRadius - 0.2f)
            fixedRotation = false
            linearDamping = 0.05f
            angularDamping = 1.2f
        }
        heroBody = world.createBody(bDef)

        val circleShape = CircleShape().apply {
            m_radius = heroRadius
        }

        val fDef = FixtureDef().apply {
            shape = circleShape
            density = 1.25f
            friction = normalHeroFriction
            restitution = 0.55f // Elastic dumpling bounce
            userData = RunnerUserData(RunnerEntityType.HERO, id = 1L)
        }
        heroBody.createFixture(fDef)
        heroBody.linearVelocity = Vec2(baseRunSpeed, 0f)
    }

    /**
     * Executes single physics tick and manages procedural generation & cleanup.
     */
    fun step(dt: Float) {
        // Boost cooldown
        if (boostTimer > 0f) {
            boostTimer -= dt
            if (boostTimer <= 0f) {
                isBoosting = false
            }
        }

        // 🛡️ Shield Power-Up cooldown
        if (shieldTimer > 0f) {
            shieldTimer -= dt
            if (shieldTimer <= 0f) {
                isShieldActive = false
            }
        }

        // 🧲 Magnet Power-Up cooldown
        if (magnetTimer > 0f) {
            magnetTimer -= dt
            if (magnetTimer <= 0f) {
                isMagnetActive = false
            }
        }

        // 🧊 Ice Chamber ("Ледяная камера") sliding effect update
        if (isSlidingOnIce && isGrounded) {
            iceSlideTimer += dt
            totalDistanceSlidOnIce += heroBody.linearVelocity.x * dt
        } else {
            iceSlideTimer = 0f
        }

        // Forward running propulsion with low-friction ice slide
        val targetVx = when {
            isBoosting -> boostSpeed
            isSlidingOnIce -> baseRunSpeed * iceSlideGlideMultiplier
            else -> baseRunSpeed
        }
        val currentVx = heroBody.linearVelocity.x
        if (currentVx < targetVx) {
            val accelRate = if (isSlidingOnIce) 12f else 8f
            val accel = (targetVx - currentVx) * accelRate * dt
            heroBody.linearVelocity = Vec2(currentVx + accel, heroBody.linearVelocity.y)
        }

        // Limit falling speed to prevent tunneling
        if (heroBody.linearVelocity.y > 32f) {
            heroBody.linearVelocity = Vec2(heroBody.linearVelocity.x, 32f)
        }

        // 💨 Continuous upward acceleration for Steam Vent zone
        if (isInsideSteamVent) {
            heroBody.applyForceToCenter(Vec2(0f, -450f))
        }

        // Advance JBox2D physics
        world.step(dt, 8, 3)

        // 🧲 Active Magnet Attraction Field for Coins & Golden Dumplings
        if (isMagnetActive) {
            val heroPos = heroBody.position
            val magnetRadius = 8.5f
            val magnetRadiusSq = magnetRadius * magnetRadius
            for (b in activeBodies) {
                val data = b.fixtureList?.userData as? RunnerUserData
                if (data != null && !data.isCollected &&
                    (data.type == RunnerEntityType.COLLECTIBLE_COIN || data.type == RunnerEntityType.COLLECTIBLE_DUMPLING)
                ) {
                    val pos = b.position
                    val dx = heroPos.x - pos.x
                    val dy = heroPos.y - pos.y
                    val distSq = dx * dx + dy * dy
                    if (distSq < magnetRadiusSq) {
                        val dist = kotlin.math.sqrt(distSq).coerceAtLeast(0.08f)
                        // Accelerate smoothly towards hero
                        val pullSpeed = 16f * dt
                        val moveX = (dx / dist) * pullSpeed
                        val moveY = (dy / dist) * pullSpeed
                        b.setTransform(Vec2(pos.x + moveX, pos.y + moveY), 0f)

                        // If close enough to hero body (< 0.85m), collect immediately!
                        if (dist < 0.85f) {
                            data.isCollected = true
                            pendingDestroyBodies.add(b)
                            val points = if (data.type == RunnerEntityType.COLLECTIBLE_DUMPLING) 50 else 10
                            onCollect(data.type, data.id, points)
                        }
                    }
                }
            }
        }

        // Destroy queued items safely outside step
        if (pendingDestroyBodies.isNotEmpty()) {
            pendingDestroyBodies.forEach { body ->
                activeBodies.remove(body)
                try {
                    world.destroyBody(body)
                } catch (_: Exception) {}
            }
            pendingDestroyBodies.clear()
        }

        // Generate ahead of hero
        val heroX = heroBody.position.x
        val newBiome = getBiomeForPosition(heroX)
        if (newBiome != currentBiome) {
            currentBiome = newBiome
            onBiomeChanged(newBiome)
        }

        if (nextSpawnX < heroX + 45f) {
            generateTrackAhead(heroX + 55f)
        }

        // Recycle bodies far behind hero
        val cleanupThreshold = heroX - 25f
        val iterator = activeBodies.iterator()
        while (iterator.hasNext()) {
            val b = iterator.next()
            if (b.position.x < cleanupThreshold) {
                iterator.remove()
                try {
                    world.destroyBody(b)
                } catch (_: Exception) {}
            }
        }

        // Failsafe unstuck: if hero falls through ground somehow
        if (heroBody.position.y > groundY + 5f) {
            heroBody.setTransform(Vec2(heroX, groundY - heroRadius - 1f), 0f)
            heroBody.linearVelocity = Vec2(baseRunSpeed, -8f)
        }
    }

    /**
     * Triggers player jump or double-jump.
     * Returns true if double jump was used (for somersault animations).
     */
    fun jump(): Boolean {
        if (jumpsRemaining <= 0) return false
        val isDouble = jumpsRemaining == 1
        jumpsRemaining--

        val jumpPower = if (isDouble) -15.5f else -14.2f
        heroBody.linearVelocity = Vec2(heroBody.linearVelocity.x * 1.05f, jumpPower)
        if (isDouble) {
            heroBody.angularVelocity = -12f // Flip spin!
        }
        if (isSlidingOnIce) {
            isSlidingOnIce = false
            heroBody.fixtureList?.setFriction(normalHeroFriction)
            heroBody.linearDamping = 0.05f
        }
        isGrounded = false
        onGroundContactChanged(false)
        return isDouble
    }

    /**
     * Downward dive/slam onto trampolines or obstacles.
     */
    fun slamDown() {
        if (!isGrounded) {
            heroBody.linearVelocity = Vec2(heroBody.linearVelocity.x, 24f)
        }
    }

    /**
     * Activates 67 Turbo Mode.
     */
    fun triggerBoost(durationSeconds: Float = 3.5f) {
        isBoosting = true
        boostTimer = durationSeconds
        heroBody.linearVelocity = Vec2(boostSpeed, heroBody.linearVelocity.y)
        onSpeedBoost()
    }

    // ==========================================
    // TRACK GENERATION
    // ==========================================

    private fun generateTrackAhead(targetX: Float) {
        while (nextSpawnX < targetX) {
            val segmentLength = random.nextFloat() * 12f + 16f
            val endX = nextSpawnX + segmentLength
            val biome = getBiomeForPosition(nextSpawnX)
            val pattern = random.nextInt(6)

            when (biome) {
                RunnerBiome.KITCHEN -> {
                    when (pattern) {
                        0 -> {
                            buildGroundSegment(nextSpawnX, endX, groundY)
                            spawnTrampoline(nextSpawnX + segmentLength * 0.45f, groundY - 0.4f)
                            spawnCoinArc(nextSpawnX + segmentLength * 0.45f, groundY - 4.5f, 5)
                            if (random.nextFloat() < 0.35f) {
                                spawnMagnetPowerUp(nextSpawnX + segmentLength * 0.15f, groundY - 1.2f)
                            }
                        }
                        1 -> {
                            val platHeight = groundY - 3.0f
                            val platLen = segmentLength * 0.65f
                            buildGroundSegment(nextSpawnX, nextSpawnX + segmentLength * 0.25f, groundY)
                            buildPlatform(nextSpawnX + segmentLength * 0.35f, platLen, platHeight)
                            buildGroundSegment(nextSpawnX + segmentLength * 0.75f, endX, groundY)
                            spawnBumper(nextSpawnX + segmentLength * 0.55f, platHeight - 2.8f)
                            spawnGoldenDumpling(nextSpawnX + segmentLength * 0.55f, platHeight - 1.2f)
                            if (random.nextFloat() < 0.4f) {
                                spawnShieldPowerUp(nextSpawnX + segmentLength * 0.35f, platHeight - 1.2f)
                            }
                        }
                        2 -> {
                            buildGroundSegment(nextSpawnX, endX, groundY)
                            spawnButterObstacle(nextSpawnX + 5f, groundY - 0.6f)
                            spawnTrampoline(nextSpawnX + 9.5f, groundY - 0.4f)
                            spawnSpeedBoosterGate(nextSpawnX + 14f, groundY - 1.6f)
                        }
                        3 -> {
                            buildGroundSegment(nextSpawnX, endX, groundY)
                            spawnHazardObstacle(nextSpawnX + 6f, groundY - 0.65f)
                            spawnCoinArc(nextSpawnX + 6f, groundY - 2.5f, 4)
                            spawnButterObstacle(nextSpawnX + 12f, groundY - 0.6f)
                            if (random.nextFloat() < 0.35f) {
                                spawnShieldPowerUp(nextSpawnX + 2f, groundY - 1.2f)
                            }
                        }
                        4 -> {
                            buildGroundSegment(nextSpawnX, endX, groundY)
                            spawnGoldenDumpling(nextSpawnX + segmentLength * 0.5f, groundY - 2.8f)
                            spawnCoinArc(nextSpawnX + 3f, groundY - 1.5f, 6)
                        }
                        else -> {
                            buildGroundSegment(nextSpawnX, endX, groundY)
                            spawnMagnetPowerUp(nextSpawnX + 3f, groundY - 1.4f)
                            spawnCoinArc(nextSpawnX + 8f, groundY - 3.0f, 5)
                            spawnCoinArc(nextSpawnX + 14f, groundY - 4.2f, 5)
                        }
                    }
                }
                RunnerBiome.ICE_FREEZER -> {
                    // ❄️ Glacial freezer biome ("Ледяная камера") with slippery ice platforms & jelly bounce cubes
                    when (pattern) {
                        0 -> {
                            buildGroundSegment(nextSpawnX, nextSpawnX + 4f, groundY, isIceChamberGround = true)
                            buildIcePlatform(nextSpawnX + 4.5f, segmentLength * 0.55f, groundY - 0.1f)
                            buildGroundSegment(nextSpawnX + 4.5f + segmentLength * 0.55f, endX, groundY, isIceChamberGround = true)
                            spawnCoinArc(nextSpawnX + segmentLength * 0.45f, groundY - 2.5f, 6)
                            if (random.nextFloat() < 0.4f) {
                                spawnShieldPowerUp(nextSpawnX + segmentLength * 0.2f, groundY - 1.2f)
                            }
                        }
                        1 -> {
                            val iceHeight = groundY - 3.2f
                            buildGroundSegment(nextSpawnX, nextSpawnX + segmentLength * 0.3f, groundY, isIceChamberGround = true)
                            buildIcePlatform(nextSpawnX + segmentLength * 0.35f, segmentLength * 0.5f, iceHeight)
                            buildGroundSegment(nextSpawnX + segmentLength * 0.8f, endX, groundY, isIceChamberGround = true)
                            spawnJellyBumper(nextSpawnX + segmentLength * 0.55f, iceHeight - 1.5f)
                            spawnGoldenDumpling(nextSpawnX + segmentLength * 0.55f, iceHeight - 3.8f)
                        }
                        2 -> {
                            buildGroundSegment(nextSpawnX, endX, groundY, isIceChamberGround = true)
                            spawnJellyBumper(nextSpawnX + 5f, groundY - 0.8f)
                            spawnJellyBumper(nextSpawnX + 11f, groundY - 1.8f)
                            spawnCoinArc(nextSpawnX + 8f, groundY - 4.5f, 5)
                        }
                        3 -> {
                            buildGroundSegment(nextSpawnX, nextSpawnX + 3f, groundY, isIceChamberGround = true)
                            buildIcePlatform(nextSpawnX + 3.5f, segmentLength * 0.6f, groundY - 0.1f)
                            buildGroundSegment(nextSpawnX + 3.5f + segmentLength * 0.6f, endX, groundY, isIceChamberGround = true)
                            spawnButterObstacle(nextSpawnX + 8f, groundY - 0.7f)
                            spawnSpeedBoosterGate(nextSpawnX + 14f, groundY - 1.6f)
                        }
                        4 -> {
                            buildGroundSegment(nextSpawnX, endX, groundY, isIceChamberGround = true)
                            buildIcePlatform(nextSpawnX + 4f, 10f, groundY - 0.1f)
                            spawnMagnetPowerUp(nextSpawnX + 5f, groundY - 1.4f)
                            spawnCoinArc(nextSpawnX + 9f, groundY - 2.8f, 5)
                        }
                        else -> {
                            buildGroundSegment(nextSpawnX, endX, groundY, isIceChamberGround = true)
                            spawnShieldPowerUp(nextSpawnX + 3.5f, groundY - 1.4f)
                            spawnJellyBumper(nextSpawnX + 8.5f, groundY - 0.8f)
                            spawnGoldenDumpling(nextSpawnX + 13f, groundY - 2.5f)
                        }
                    }
                }
                RunnerBiome.STEAM_CLOUDS -> {
                    // ☁️ Steamy tea mountains with geysers & floating clouds
                    when (pattern) {
                        0 -> {
                            buildGroundSegment(nextSpawnX, endX, groundY)
                            spawnSteamVent(nextSpawnX + 5.5f, groundY - 2.2f)
                            spawnCoinArc(nextSpawnX + 6.5f, groundY - 6.5f, 6)
                            spawnGoldenDumpling(nextSpawnX + 12f, groundY - 5.5f)
                        }
                        1 -> {
                            buildGroundSegment(nextSpawnX, nextSpawnX + 4f, groundY)
                            buildPlatform(nextSpawnX + 5f, 5f, groundY - 2.8f)
                            buildPlatform(nextSpawnX + 11f, 5f, groundY - 5.2f)
                            buildGroundSegment(nextSpawnX + segmentLength * 0.8f, endX, groundY)
                            spawnCoinArc(nextSpawnX + 13f, groundY - 7.5f, 5)
                            spawnSpeedBoosterGate(nextSpawnX + 14f, groundY - 6.5f)
                        }
                        2 -> {
                            buildGroundSegment(nextSpawnX, endX, groundY)
                            spawnSteamVent(nextSpawnX + 4f, groundY - 2.2f)
                            spawnJellyBumper(nextSpawnX + 10f, groundY - 4.5f)
                            spawnCoinArc(nextSpawnX + 10f, groundY - 7.5f, 4)
                        }
                        3 -> {
                            buildGroundSegment(nextSpawnX, endX, groundY)
                            spawnSteamVent(nextSpawnX + 4.5f, groundY - 2.2f)
                            spawnSteamVent(nextSpawnX + 11.5f, groundY - 2.2f)
                            spawnCoinArc(nextSpawnX + 8f, groundY - 6.0f, 5)
                            spawnShieldPowerUp(nextSpawnX + 12f, groundY - 6.0f)
                        }
                        4 -> {
                            val platHeight = groundY - 3.8f
                            buildGroundSegment(nextSpawnX, nextSpawnX + 3f, groundY)
                            buildPlatform(nextSpawnX + 4f, segmentLength * 0.65f, platHeight)
                            buildGroundSegment(nextSpawnX + 4f + segmentLength * 0.65f, endX, groundY)
                            spawnMagnetPowerUp(nextSpawnX + 5.5f, platHeight - 1.4f)
                            spawnCoinArc(nextSpawnX + 11f, platHeight - 2.5f, 6)
                        }
                        else -> {
                            buildGroundSegment(nextSpawnX, endX, groundY)
                            spawnSteamVent(nextSpawnX + 5f, groundY - 2.2f)
                            spawnSpeedBoosterGate(nextSpawnX + 11f, groundY - 4.5f)
                            spawnGoldenDumpling(nextSpawnX + 15f, groundY - 3.0f)
                        }
                    }
                }

                RunnerBiome.CLOUD_KITCHEN -> {
                    // 🍳 Cloud kitchen with floating platforms and jelly trampolines
                    when (pattern) {
                        0 -> {
                            buildGroundSegment(nextSpawnX, endX, groundY)
                            spawnJellyBumper(nextSpawnX + 5f, groundY - 0.8f)
                            spawnTrampoline(nextSpawnX + 10f, groundY - 0.4f)
                            spawnCoinArc(nextSpawnX + 8f, groundY - 3.5f, 5)
                        }
                        1 -> {
                            val platHeight = groundY - 4f
                            buildGroundSegment(nextSpawnX, nextSpawnX + 3f, groundY)
                            buildPlatform(nextSpawnX + 4f, segmentLength * 0.5f, platHeight)
                            buildGroundSegment(nextSpawnX + 4f + segmentLength * 0.5f, endX, groundY)
                            spawnJellyBumper(nextSpawnX + 5f, platHeight - 1f)
                            spawnGoldenDumpling(nextSpawnX + 8f, platHeight - 2.5f)
                        }
                        else -> {
                            buildGroundSegment(nextSpawnX, endX, groundY)
                            spawnSteamVent(nextSpawnX + 4f, groundY - 2.2f)
                            spawnJellyBumper(nextSpawnX + 10f, groundY - 0.8f)
                        }
                    }
                }

            }

            nextSpawnX = endX
        }
    }

    fun buildGroundSegment(startX: Float, endX: Float, height: Float, isIceChamberGround: Boolean = false) {
        val len = endX - startX
        val halfW = len / 2f
        val halfH = 0.5f
        val centerX = startX + halfW
        val centerY = height + halfH

        val bDef = BodyDef().apply {
            type = BodyType.STATIC
            position.set(centerX, centerY)
        }
        val body = world.createBody(bDef)

        val poly = PolygonShape().apply {
            setAsBox(halfW, halfH)
        }
        val fDef = FixtureDef().apply {
            shape = poly
            friction = if (isIceChamberGround) iceFrictionCoefficient else normalGroundFriction
            restitution = if (isIceChamberGround) 0.12f else 0.15f
            userData = RunnerUserData(
                type = if (isIceChamberGround) RunnerEntityType.PLATFORM_ICE else RunnerEntityType.GROUND,
                id = if (isIceChamberGround) ++idCounter else 0L,
                width = len,
                height = halfH * 2
            )
        }
        body.createFixture(fDef)
        activeBodies.add(body)
    }

    fun buildPlatform(startX: Float, len: Float, height: Float) {
        val halfW = len / 2f
        val halfH = 0.3f
        val centerX = startX + halfW
        val centerY = height + halfH

        val bDef = BodyDef().apply {
            type = BodyType.STATIC
            position.set(centerX, centerY)
        }
        val body = world.createBody(bDef)

        val poly = PolygonShape().apply {
            setAsBox(halfW, halfH)
        }
        val fDef = FixtureDef().apply {
            shape = poly
            friction = 0.25f
            restitution = 0.4f
            userData = RunnerUserData(RunnerEntityType.PLATFORM, width = len, height = halfH * 2)
        }
        body.createFixture(fDef)
        activeBodies.add(body)
    }

    fun buildIcePlatform(startX: Float, len: Float, height: Float) {
        val halfW = len / 2f
        val halfH = 0.32f
        val centerX = startX + halfW
        val centerY = height + halfH

        val bDef = BodyDef().apply {
            type = BodyType.STATIC
            position.set(centerX, centerY)
        }
        val body = world.createBody(bDef)

        val poly = PolygonShape().apply {
            setAsBox(halfW, halfH)
        }
        val fDef = FixtureDef().apply {
            shape = poly
            friction = iceFrictionCoefficient // Slippery near-zero friction ice in JBox2D!
            restitution = 0.20f
            userData = RunnerUserData(RunnerEntityType.PLATFORM_ICE, id = ++idCounter, width = len, height = halfH * 2)
        }
        body.createFixture(fDef)
        activeBodies.add(body)
    }

    private fun spawnJellyBumper(x: Float, y: Float) {
        val bDef = BodyDef().apply {
            type = BodyType.STATIC
            position.set(x, y)
        }
        val body = world.createBody(bDef)
        val poly = PolygonShape().apply {
            setAsBox(0.85f, 0.55f)
        }
        val fDef = FixtureDef().apply {
            shape = poly
            restitution = 1.95f // Super squishy jelly bounce!
            friction = 0.1f
            userData = RunnerUserData(RunnerEntityType.BUMPER_JELLY, id = ++idCounter, width = 1.7f, height = 1.1f)
        }
        body.createFixture(fDef)
        activeBodies.add(body)
    }

    private fun spawnSteamVent(x: Float, y: Float) {
        val bDef = BodyDef().apply {
            type = BodyType.STATIC
            position.set(x, y)
        }
        val body = world.createBody(bDef)
        val poly = PolygonShape().apply {
            setAsBox(0.8f, 1.6f)
        }
        val fDef = FixtureDef().apply {
            shape = poly
            isSensor = true
            userData = RunnerUserData(RunnerEntityType.STEAM_VENT, id = ++idCounter, width = 1.6f, height = 3.2f)
        }
        body.createFixture(fDef)
        activeBodies.add(body)
    }

    private fun spawnTrampoline(x: Float, y: Float) {
        val bDef = BodyDef().apply {
            type = BodyType.STATIC
            position.set(x, y)
        }
        val body = world.createBody(bDef)
        val poly = PolygonShape().apply {
            setAsBox(0.9f, 0.35f)
        }
        val fDef = FixtureDef().apply {
            shape = poly
            restitution = 1.85f // Super bounce
            friction = 0.1f
            userData = RunnerUserData(RunnerEntityType.TRAMPOLINE, id = ++idCounter, width = 1.8f, height = 0.7f)
        }
        body.createFixture(fDef)
        activeBodies.add(body)
    }

    private fun spawnBumper(x: Float, y: Float) {
        val bDef = BodyDef().apply {
            type = BodyType.STATIC
            position.set(x, y)
        }
        val body = world.createBody(bDef)
        val circle = CircleShape().apply {
            m_radius = 0.72f
        }
        val fDef = FixtureDef().apply {
            shape = circle
            restitution = 1.45f
            friction = 0.05f
            userData = RunnerUserData(RunnerEntityType.BUMPER, id = ++idCounter, width = 1.44f, height = 1.44f)
        }
        body.createFixture(fDef)
        activeBodies.add(body)
    }

    private fun spawnButterObstacle(x: Float, y: Float) {
        val bDef = BodyDef().apply {
            type = BodyType.STATIC
            position.set(x, y)
        }
        val body = world.createBody(bDef)
        val poly = PolygonShape().apply {
            setAsBox(0.6f, 0.45f)
        }
        val fDef = FixtureDef().apply {
            shape = poly
            friction = 0.05f
            restitution = 0.85f
            userData = RunnerUserData(RunnerEntityType.OBSTACLE_BUTTER, id = ++idCounter, width = 1.2f, height = 0.9f)
        }
        body.createFixture(fDef)
        activeBodies.add(body)
    }

    private fun spawnHazardObstacle(x: Float, y: Float) {
        val bDef = BodyDef().apply {
            type = BodyType.STATIC
            position.set(x, y)
        }
        val body = world.createBody(bDef)
        val poly = PolygonShape().apply {
            setAsBox(0.5f, 0.55f)
        }
        val fDef = FixtureDef().apply {
            shape = poly
            friction = 0.3f
            restitution = 0.95f
            userData = RunnerUserData(RunnerEntityType.OBSTACLE_HAZARD, id = ++idCounter, width = 1.0f, height = 1.1f)
        }
        body.createFixture(fDef)
        activeBodies.add(body)
    }

    private fun spawnCoinArc(centerX: Float, peakY: Float, count: Int) {
        val span = 4.0f
        val step = span / (count - 1)
        for (i in 0 until count) {
            val ox = (i - (count - 1) / 2f) * step
            val oy = (ox * ox) * 0.12f // Parabolic arc
            spawnSensorItem(centerX + ox, peakY + oy, RunnerEntityType.COLLECTIBLE_COIN, radius = 0.35f)
        }
    }

    private fun spawnGoldenDumpling(x: Float, y: Float) {
        spawnSensorItem(x, y, RunnerEntityType.COLLECTIBLE_DUMPLING, radius = 0.55f)
    }

    private fun spawnShieldPowerUp(x: Float, y: Float) {
        spawnSensorItem(x, y, RunnerEntityType.POWERUP_SHIELD, radius = 0.55f)
    }

    private fun spawnMagnetPowerUp(x: Float, y: Float) {
        spawnSensorItem(x, y, RunnerEntityType.POWERUP_MAGNET, radius = 0.55f)
    }

    private fun spawnSpeedBoosterGate(x: Float, y: Float) {
        spawnSensorItem(x, y, RunnerEntityType.BOOSTER_GATE, radius = 0.85f)
    }

    private fun spawnSensorItem(x: Float, y: Float, type: RunnerEntityType, radius: Float) {
        val bDef = BodyDef().apply {
            this.type = BodyType.STATIC
            position.set(x, y)
        }
        val body = world.createBody(bDef)
        val circle = CircleShape().apply {
            m_radius = radius
        }
        val fDef = FixtureDef().apply {
            shape = circle
            isSensor = true
            userData = RunnerUserData(type, id = ++idCounter, width = radius * 2, height = radius * 2)
        }
        body.createFixture(fDef)
        activeBodies.add(body)
    }

    // ==========================================
    // JBOX2D CONTACT LISTENER
    // ==========================================

    override fun beginContact(contact: Contact) {
        val fixA = contact.fixtureA
        val fixB = contact.fixtureB
        val dataA = fixA.userData as? RunnerUserData
        val dataB = fixB.userData as? RunnerUserData

        val isHeroA = dataA?.type == RunnerEntityType.HERO
        val isHeroB = dataB?.type == RunnerEntityType.HERO
        if (!isHeroA && !isHeroB) return

        val otherFix = if (isHeroA) fixB else fixA
        val otherData = otherFix.userData as? RunnerUserData ?: return
        val otherBody = otherFix.body

        when (otherData.type) {
            RunnerEntityType.GROUND, RunnerEntityType.PLATFORM, RunnerEntityType.PLATFORM_ICE -> {
                // Ground contact check (hero center must be above platform center or moving down)
                val heroY = heroBody.position.y
                val platY = otherBody.position.y
                if (heroY < platY && heroBody.linearVelocity.y >= -1.0f) {
                    groundContactCount++
                    isGrounded = true
                    jumpsRemaining = 2
                    comboCount = 0
                    onGroundContactChanged(true)

                    val isIce = otherData.type == RunnerEntityType.PLATFORM_ICE ||
                        (otherData.type == RunnerEntityType.GROUND && currentBiome == RunnerBiome.ICE_FREEZER)

                    if (isIce) {
                        // 🧊 Slippery Ice Chamber: configure JBox2D fixture friction on hero for ice sliding
                        isSlidingOnIce = true
                        heroBody.fixtureList?.setFriction(iceHeroFriction)
                        heroBody.linearDamping = 0.008f // Low air/glide resistance

                        // Boost hero velocity forward with smooth skate glide
                        val currentVx = heroBody.linearVelocity.x
                        val slideSpeed = if (isBoosting) boostSpeed * 1.15f else baseRunSpeed * iceSlideGlideMultiplier
                        heroBody.linearVelocity = Vec2(max(currentVx, slideSpeed), heroBody.linearVelocity.y)
                        onIceSlide()
                    }
                }
            }
            RunnerEntityType.TRAMPOLINE -> {
                // Trampoline super-launch
                heroBody.linearVelocity = Vec2(
                    heroBody.linearVelocity.x * 1.15f + 1.2f,
                    -21.5f
                )
                jumpsRemaining = 2
                comboCount++
                val normal = Vec2(0f, -1f)
                onBounce(RunnerEntityType.TRAMPOLINE, normal, 21.5f)
            }
            RunnerEntityType.BUMPER_JELLY -> {
                // 🍮 Super squishy marmalade jelly trampoline bounce!
                heroBody.linearVelocity = Vec2(
                    heroBody.linearVelocity.x * 1.15f + 1.4f,
                    -23.5f // Higher spring rebound!
                )
                jumpsRemaining = 2
                comboCount++
                val normal = Vec2(0f, -1f)
                onBounce(RunnerEntityType.BUMPER_JELLY, normal, 23.5f)
                onJellyBounce()
            }
            RunnerEntityType.STEAM_VENT -> {
                // ☁️ Start steam vent zone
                isInsideSteamVent = true
                onSteamVent()
            }
            RunnerEntityType.BUMPER -> {
                // Pinball pot lid elastic bounce
                val heroPos = heroBody.position
                val bumperPos = otherBody.position
                val dx = heroPos.x - bumperPos.x
                val dy = heroPos.y - bumperPos.y
                val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                val normal = Vec2(dx / dist, dy / dist)

                // Impulse deflection
                val impulse = Vec2(normal.x * 16f, min(-14f, normal.y * 18f))
                heroBody.linearVelocity = impulse
                jumpsRemaining = 2
                comboCount++
                onBounce(RunnerEntityType.BUMPER, normal, 18f)
            }
            RunnerEntityType.OBSTACLE_BUTTER, RunnerEntityType.OBSTACLE_HAZARD -> {
                // If hero stomps down onto obstacle from above
                val isStomp = heroBody.position.y < otherBody.position.y - 0.2f && heroBody.linearVelocity.y > 2.0f
                if (isStomp) {
                    // Stomp bounce!
                    heroBody.linearVelocity = Vec2(heroBody.linearVelocity.x + 2f, -16f)
                    jumpsRemaining = 2
                    comboCount++
                    onBounce(otherData.type, Vec2(0f, -1f), 16f)
                    pendingDestroyBodies.add(otherBody)
                } else if (isShieldActive) {
                    // 🛡️ Shield absorbs the obstacle collision without hero knockback!
                    pendingDestroyBodies.add(otherBody)
                    deactivateShield() // Shield absorbs 1 impact and pops
                    onShieldAbsorb()
                } else {
                    // Bump backwards with funny comic recoil
                    heroBody.linearVelocity = Vec2(baseRunSpeed * 0.4f, -7.5f)
                    onObstacleHit(otherData.type, 1.0f)
                }
            }
            RunnerEntityType.COLLECTIBLE_COIN -> {
                if (!otherData.isCollected) {
                    otherData.isCollected = true
                    pendingDestroyBodies.add(otherBody)
                    onCollect(RunnerEntityType.COLLECTIBLE_COIN, otherData.id, 10)
                }
            }
            RunnerEntityType.COLLECTIBLE_DUMPLING -> {
                if (!otherData.isCollected) {
                    otherData.isCollected = true
                    pendingDestroyBodies.add(otherBody)
                    onCollect(RunnerEntityType.COLLECTIBLE_DUMPLING, otherData.id, 50)
                }
            }
            RunnerEntityType.POWERUP_SHIELD -> {
                if (!otherData.isCollected) {
                    otherData.isCollected = true
                    pendingDestroyBodies.add(otherBody)
                    activateShield(12f)
                    onCollect(RunnerEntityType.POWERUP_SHIELD, otherData.id, 25)
                }
            }
            RunnerEntityType.POWERUP_MAGNET -> {
                if (!otherData.isCollected) {
                    otherData.isCollected = true
                    pendingDestroyBodies.add(otherBody)
                    activateMagnet(10f)
                    onCollect(RunnerEntityType.POWERUP_MAGNET, otherData.id, 25)
                }
            }
            RunnerEntityType.BOOSTER_GATE -> {
                triggerBoost()
                onSpeedBoost()
            }
            else -> {}
        }
    }

    override fun endContact(contact: Contact) {
        val dataA = contact.fixtureA.userData as? RunnerUserData
        val dataB = contact.fixtureB.userData as? RunnerUserData
        val isHeroA = dataA?.type == RunnerEntityType.HERO
        val isHeroB = dataB?.type == RunnerEntityType.HERO
        if (!isHeroA && !isHeroB) return

        val otherData = if (isHeroA) dataB else dataA
        val isIce = otherData?.type == RunnerEntityType.PLATFORM_ICE ||
            (otherData?.type == RunnerEntityType.GROUND && currentBiome == RunnerBiome.ICE_FREEZER)

        if (isIce) {
            if (!isTouchingIce()) {
                isSlidingOnIce = false
                heroBody.fixtureList?.setFriction(normalHeroFriction)
                heroBody.linearDamping = 0.05f
            }
        }

        if (otherData?.type == RunnerEntityType.STEAM_VENT) {
            isInsideSteamVent = false
        }

        if (otherData?.type == RunnerEntityType.GROUND || otherData?.type == RunnerEntityType.PLATFORM || otherData?.type == RunnerEntityType.PLATFORM_ICE) {
            groundContactCount = max(0, groundContactCount - 1)
            if (groundContactCount == 0) {
                isGrounded = false
                onGroundContactChanged(false)
            }
        }
    }

    /**
     * Checks if Pelmen-Six currently maintains physical contact with any icy surface.
     */
    fun isTouchingIce(): Boolean {
        var edge = heroBody.contactList
        while (edge != null) {
            val contact = edge.contact
            if (contact != null && contact.isTouching) {
                val fixA = contact.fixtureA
                val fixB = contact.fixtureB
                val dataA = fixA?.userData as? RunnerUserData
                val dataB = fixB?.userData as? RunnerUserData
                val isIceA = dataA?.type == RunnerEntityType.PLATFORM_ICE ||
                    (dataA?.type == RunnerEntityType.GROUND && currentBiome == RunnerBiome.ICE_FREEZER)
                val isIceB = dataB?.type == RunnerEntityType.PLATFORM_ICE ||
                    (dataB?.type == RunnerEntityType.GROUND && currentBiome == RunnerBiome.ICE_FREEZER)
                if (isIceA || isIceB) {
                    return true
                }
            }
            edge = edge.next
        }
        return false
    }

    override fun preSolve(contact: Contact, oldManifold: Manifold) {
        val fixA = contact.fixtureA
        val fixB = contact.fixtureB
        val dataA = fixA?.userData as? RunnerUserData
        val dataB = fixB?.userData as? RunnerUserData
        val isHeroA = dataA?.type == RunnerEntityType.HERO
        val isHeroB = dataB?.type == RunnerEntityType.HERO
        if (!isHeroA && !isHeroB) return

        val otherData = if (isHeroA) dataB else dataA
        val isIce = otherData?.type == RunnerEntityType.PLATFORM_ICE ||
            (otherData?.type == RunnerEntityType.GROUND && currentBiome == RunnerBiome.ICE_FREEZER)

        if (isIce) {
            // 🧊 JBox2D Contact Friction Configuration for Ice Chamber:
            // Explicitly set contact friction to near-zero and impart tangential skating slide velocity
            contact.setFriction(iceFrictionCoefficient)
            val targetSlideSpeed = if (isBoosting) boostSpeed * 1.15f else baseRunSpeed * iceSlideGlideMultiplier
            contact.setTangentSpeed(targetSlideSpeed)
        }
    }

    override fun postSolve(contact: Contact, impulse: ContactImpulse) {}

    /**
     * Extracts active obstacle snapshots for Canvas rendering.
     */
    fun getVisibleObstacles(viewLeft: Float, viewRight: Float): List<RunnerObstacleSnapshot> {
        val result = mutableListOf<RunnerObstacleSnapshot>()
        for (body in activeBodies) {
            val pos = body.position
            if (pos.x >= viewLeft - 3f && pos.x <= viewRight + 3f) {
                val data = body.fixtureList?.userData as? RunnerUserData ?: continue
                result.add(
                    RunnerObstacleSnapshot(
                        id = data.id,
                        type = data.type,
                        position = Vec2(pos.x, pos.y),
                        width = data.width,
                        height = data.height,
                        angle = body.angle,
                        isCollected = data.isCollected
                    )
                )
            }
        }
        return result
    }

    /**
     * Returns all bodies currently in the JBox2D World for debug visualization.
     */
    fun getAllBodies(): List<Body> {
        val list = mutableListOf<Body>()
        var b = world.bodyList
        while (b != null) {
            list.add(b)
            b = b.next
        }
        return list
    }

    /**
     * Extracts active contact points for troubleshooting character-platform collisions.
     */
    fun getActiveContacts(): List<PhysicsDebugContact> {
        val list = mutableListOf<PhysicsDebugContact>()
        var contact = world.contactList
        while (contact != null) {
            if (contact.isTouching) {
                val manifold = contact.manifold
                if (manifold != null && manifold.pointCount > 0) {
                    val posA = contact.fixtureA.body.position
                    val posB = contact.fixtureB.body.position
                    val midPoint = Vec2((posA.x + posB.x) * 0.5f, (posA.y + posB.y) * 0.5f)
                    val norm = manifold.localNormal ?: Vec2(0f, -1f)
                    list.add(PhysicsDebugContact(midPoint, norm, true))
                }
            }
            contact = contact.next
        }
        return list
    }

    /**
     * Resets the game to starting line.
     */
    fun reset() {
        for (b in activeBodies) {
            try { world.destroyBody(b) } catch (_: Exception) {}
        }
        activeBodies.clear()
        pendingDestroyBodies.clear()

        buildGroundSegment(startX = -10f, endX = 25f, height = groundY)
        nextSpawnX = 25f
        generateTrackAhead(targetX = 60f)

        heroBody.setTransform(Vec2(3f, groundY - heroRadius - 0.2f), 0f)
        heroBody.linearVelocity = Vec2(baseRunSpeed, 0f)
        heroBody.angularVelocity = 0f
        isGrounded = true
        groundContactCount = 1
        jumpsRemaining = 2
        isBoosting = false
        boostTimer = 0f
        comboCount = 0
        deactivateShield()
        deactivateMagnet()
        isSlidingOnIce = false
        totalDistanceSlidOnIce = 0f
        iceSlideTimer = 0f
        heroBody.fixtureList?.setFriction(normalHeroFriction)
        heroBody.linearDamping = 0.05f
        currentBiome = RunnerBiome.KITCHEN
    }
}
