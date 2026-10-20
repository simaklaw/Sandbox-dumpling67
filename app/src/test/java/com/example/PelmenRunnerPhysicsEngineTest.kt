package com.example

import com.example.physics.runner.PelmenRunnerPhysicsEngine
import com.example.physics.runner.RunnerEntityType
import org.jbox2d.common.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PelmenRunnerPhysicsEngineTest {

    @Test
    fun testInitialHeroStateAndMovement() {
        val engine = PelmenRunnerPhysicsEngine()

        // Hero should start with forward velocity
        assertTrue("Hero should start with positive forward velocity", engine.heroBody.linearVelocity.x > 0f)
        assertEquals("Should have 2 jumps available", 2, engine.jumpsRemaining)

        val startX = engine.heroBody.position.x

        // Step simulation for 1 second (60 steps)
        for (i in 0 until 60) {
            engine.step(1f / 60f)
        }

        assertTrue("Hero should be grounded after stepping on ground", engine.isGrounded)
        val endX = engine.heroBody.position.x
        assertTrue("Hero must advance forward horizontally", endX > startX)
    }

    @Test
    fun testJumpAndDoubleJumpMechanics() {
        var groundChangedCount = 0
        val engine = PelmenRunnerPhysicsEngine(
            onGroundContactChanged = { groundChangedCount++ }
        )

        // Initial Jump
        val isDoubleFirst = engine.jump()
        assertFalse("First jump should not be double jump", isDoubleFirst)
        assertTrue("Hero upward velocity should be negative in screen-space", engine.heroBody.linearVelocity.y < -10f)
        assertEquals("Should have 1 jump remaining", 1, engine.jumpsRemaining)

        // Step a few ticks
        engine.step(1f / 60f)

        // Double Jump
        val isDoubleSecond = engine.jump()
        assertTrue("Second jump must be registered as double jump", isDoubleSecond)
        assertEquals("Should have 0 jumps remaining", 0, engine.jumpsRemaining)
        assertTrue("Double jump triggers somersault angular spin", engine.heroBody.angularVelocity != 0f)

        // Third jump attempt should fail
        val isDoubleThird = engine.jump()
        assertFalse("Third jump attempt must fail", isDoubleThird)
    }

    @Test
    fun testSlamDownAction() {
        val engine = PelmenRunnerPhysicsEngine()
        engine.jump()
        engine.step(1f / 60f)

        engine.slamDown()
        assertTrue("Slam down must produce fast downward velocity", engine.heroBody.linearVelocity.y >= 20f)
    }

    @Test
    fun testSpeedBoostMode() {
        var boostCalled = false
        val engine = PelmenRunnerPhysicsEngine(
            onSpeedBoost = { boostCalled = true }
        )

        engine.triggerBoost(2.0f)
        assertTrue("isBoosting should be true", engine.isBoosting)
        assertTrue("Speed boost callback should be triggered", boostCalled)
        assertTrue("Hero velocity should match boost speed", engine.heroBody.linearVelocity.x >= engine.boostSpeed - 0.1f)

        // Step past boost duration
        for (i in 0 until 130) {
            engine.step(1f / 60f)
        }

        assertFalse("isBoosting should expire", engine.isBoosting)
    }

    @Test
    fun testResetFunctionality() {
        val engine = PelmenRunnerPhysicsEngine()

        // Advance simulation
        for (i in 0 until 100) {
            engine.step(1f / 60f)
        }

        val progressedX = engine.heroBody.position.x
        assertTrue("Progressed X should be > 10m", progressedX > 10f)

        engine.reset()
        val resetX = engine.heroBody.position.x
        assertEquals("Hero X should reset back to initial 3.0m", 3.0f, resetX, 0.1f)
        assertEquals("Jumps remaining should be reset to 2", 2, engine.jumpsRemaining)
    }

    @Test
    fun testBiomeProgressionAndThresholds() {
        var changedBiome: com.example.physics.runner.RunnerBiome? = null
        val engine = PelmenRunnerPhysicsEngine(
            onBiomeChanged = { biome -> changedBiome = biome }
        )

        assertEquals(com.example.physics.runner.RunnerBiome.KITCHEN, engine.currentBiome)
        assertEquals(com.example.physics.runner.RunnerBiome.KITCHEN, engine.getBiomeForPosition(50f))
        assertEquals(com.example.physics.runner.RunnerBiome.ICE_FREEZER, engine.getBiomeForPosition(200f))
        assertEquals(com.example.physics.runner.RunnerBiome.STEAM_CLOUDS, engine.getBiomeForPosition(450f))
    }

    @Test
    fun testInteractiveCallbacksInvocation() {
        var iceSlideCalled = false
        var jellyBounceCalled = false
        var steamVentCalled = false

        val engine = PelmenRunnerPhysicsEngine(
            onIceSlide = { iceSlideCalled = true },
            onJellyBounce = { jellyBounceCalled = true },
            onSteamVent = { steamVentCalled = true }
        )

        // Directly call handlers or verify non-null wiring
        engine.onIceSlide?.invoke()
        engine.onJellyBounce?.invoke()
        engine.onSteamVent?.invoke()

        assertTrue(iceSlideCalled)
        assertTrue(jellyBounceCalled)
        assertTrue(steamVentCalled)
    }
}
