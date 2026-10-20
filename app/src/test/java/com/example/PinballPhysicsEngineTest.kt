package com.example

import com.example.screens.Pinball.PinballPhysicsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PinballPhysicsEngineTest {

    @Test
    fun testFiveMinuteSimulationStability() {
        var score = 0
        var bumperHits = 0
        var starHits = 0
        var allStarsCount = 0

        val engine = PinballPhysicsEngine(
            onScoreAdded = { score += it },
            onSoundTriggered = { /* no op */ },
            onBumperHit = { bumperHits++ },
            onStarHit = { starHits++ },
            onAllStarsLit = { allStarsCount++ }
        )

        // 5 minutes at 60 Hz = 18,000 steps
        val totalSteps = 5 * 60 * 60
        val stepDtNanos = 16_666_666L

        var simulatedTimeNanos = 100_000_000L

        for (step in 0 until totalSteps) {
            simulatedTimeNanos += stepDtNanos

            // Simulate random flipper activation every ~0.5s
            if (step % 30 == 0) {
                engine.setLeftFlipperEngaged(Random.nextBoolean())
                engine.setRightFlipperEngaged(Random.nextBoolean())
            }

            engine.step(simulatedTimeNanos)
        }

        println("=== 5-MIN SIMULATION TEST RESULTS ===")
        println("Score accumulated: $score")
        println("Bumper hits: $bumperHits")
        println("Star hits: $starHits")
        println("All stars lit celebrations: $allStarsCount")
        println("Max speed recorded: ${engine.maxRecordedSpeed} m/s")
        println("Unstuck Low Speed triggers: ${engine.unstuckLowSpeedCount}")
        println("Unstuck No Displacement triggers: ${engine.unstuckNoDisplacementCount}")
        println("Unstuck On Flipper triggers: ${engine.unstuckOnFlipperCount}")
        println("Unstuck Out Of Bounds triggers: ${engine.unstuckOutOfBoundsCount}")
        println("Unstuck NaN speed/pos triggers: ${engine.unstuckNaNCount}")

        // Assert strictly: Zero out-of-bounds, Zero NaN!
        assertEquals("Out of bounds must be 0", 0, engine.unstuckOutOfBoundsCount)
        assertEquals("NaN occurrences must be 0", 0, engine.unstuckNaNCount)
        assertTrue("Max speed must be bounded <= 16.5", engine.maxRecordedSpeed <= 16.5f)
    }
}
