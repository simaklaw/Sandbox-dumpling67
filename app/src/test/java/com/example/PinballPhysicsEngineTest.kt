package com.example

import com.example.screens.Pinball.PinballPhysicsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
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

    private fun newEngine() = PinballPhysicsEngine({}, {}, {}, {}, {})

    /** Шарик, лежащий на опущенной левой лапке, после удара должен взлететь вверх. */
    @Test
    fun flipperLaunchesBallUp() {
        val e = newEngine()
        e.autoPawsEnabled = false
        e.clearAllAndReset()
        val b = e.getBalls().first()
        b.px = 3.2f; b.py = 13.3f; b.vx = 0f; b.vy = 0f
        var t = 100_000_000L
        e.step(t)
        // дать шарику лечь на лапку
        repeat(30) { t += 16_666_666L; e.step(t) }
        e.holdLeft(true)
        var minVy = 0f
        repeat(20) { t += 16_666_666L; e.step(t); minVy = minOf(minVy, b.vy) }
        println("minVy after flip = $minVy")
        assertTrue("flipper must throw ball up, minVy=$minVy", minVy < -4f)
    }

    /** Шарик не должен надолго замирать в верхней части поля (симуляция в виртуальном времени). */
    @Test
    fun ballNeverStaysStuckUpTop() {
        val e = newEngine()
        var t = 100_000_000L
        e.step(t)
        var stillTop = 0f
        var maxStill = 0f
        var lx = 0f; var ly = 0f
        var drained = 0
        val total = 60 * 60 * 10
        for (i in 0 until total) {
            t += 16_666_666L
            e.step(t)
            val b = e.getBalls().first()
            if (hypot(b.px - lx, b.py - ly) < 0.05f && b.py < 12.5f) stillTop += 1f / 60f else stillTop = 0f
            lx = b.px; ly = b.py
            if (stillTop > maxStill) maxStill = stillTop
        }
        println("max still time on top = $maxStill s; nudges=${e.unstuckLowSpeedCount + e.unstuckNoDisplacementCount}")
        assertTrue("ball stuck on top for $maxStill s", maxStill < 1.5f)
    }
}
