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
            onAllStarsLit = { allStarsCount++ },
            rng = Random(12345)
        )

        // 5 minutes at 60 Hz = 18,000 steps
        val totalSteps = 5 * 60 * 60
        val stepDtNanos = 16_666_666L

        var simulatedTimeNanos = 100_000_000L
        val inputRng = Random(999)

        for (step in 0 until totalSteps) {
            simulatedTimeNanos += stepDtNanos

            // Simulate random flipper activation every ~0.5s
            if (step % 30 == 0) {
                engine.setLeftFlipperEngaged(inputRng.nextBoolean())
                engine.setRightFlipperEngaged(inputRng.nextBoolean())
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

    private fun newEngine(seed: Int = 7) = PinballPhysicsEngine({}, {}, {}, {}, {}, Random(seed))

    /** Шарик, лежащий на опущенной левой лапке, после удара должен взлететь вверх. */
    @Test
    fun flipperLaunchesBallUp() {
        val e = newEngine()
        e.autoPawsEnabled = false
        e.clearAllAndReset()
        val b = e.getBalls().first()
        b.px = 3.4f; b.py = 15.4f; b.vx = 0f; b.vy = 0f
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

    /**
     * Шарик не должен надолго замирать в верхней части поля. Симуляция детерминирована (зёрна заданы),
     * проверяем много разных зёрен; «замер» = шарик не ушёл дальше 0.25 от опорной точки.
     */
    @Test
    fun ballNeverStaysStuckUpTop() {
        for (seed in 1..8) {
            val e = newEngine(seed)
            var t = 100_000_000L
            e.step(t)
            var ax = 0f; var ay = 0f; var anchorT = 0f
            var maxStill = 0f
            var time = 0f
            val total = 60 * 60 * 3
            for (i in 0 until total) {
                t += 16_666_666L
                time += 1f / 60f
                e.step(t)
                for (b in e.getBalls()) {
                    if (b.captured > 0f || b.py >= 13.5f) { ax = b.px; ay = b.py; anchorT = time; continue }
                    if (hypot(b.px - ax, b.py - ay) > 0.25f) { ax = b.px; ay = b.py; anchorT = time }
                    maxStill = maxOf(maxStill, time - anchorT)
                }
            }
            println("seed=$seed max still on top = $maxStill s")
            assertTrue("seed=$seed: ball stuck on top for $maxStill s", maxStill < 1.5f)
        }
    }

    /** Шарик, положенный в бывший «клин» между стенкой и кристаллом, скатывается сам, без страховки. */
    @Test
    fun noWedgeBetweenWallAndGem() {
        for (side in 0..1) {
            val e = newEngine()
            e.autoPawsEnabled = false
            e.clearAllAndReset()
            val b = e.getBalls().first()
            val gem = e.gemPositions[if (side == 0) 1 else 4]
            b.px = if (side == 0) 0.36f else 8.64f; b.py = gem.y - 0.62f; b.vx = 0f; b.vy = 0f
            var t = 100_000_000L
            e.step(t)
            repeat(60) { t += 16_666_666L; e.step(t) }
            assertTrue("ball must leave the gem top, y=${b.py}", b.py > gem.y + 0.5f || kotlin.math.abs(b.px - (if (side == 0) 0.36f else 8.64f)) > 0.5f)
            assertEquals("страховка не должна понадобиться", 0, e.unstuckLowSpeedCount + e.unstuckNoDisplacementCount)
        }
    }
}

class PinballFieldTest {
    private fun newEngine(seed: Int = 7) = PinballPhysicsEngine({}, {}, {}, {}, {}, Random(seed))

    /** Шарик в воронке не пропадает и вылетает вверх. */
    @Test
    fun saucerCapturesAndEjects() {
        val e = newEngine()
        e.autoPawsEnabled = false
        e.clearAllAndReset()
        val b = e.getBalls().first()
        b.px = e.saucerPos.x; b.py = e.saucerPos.y - 0.2f; b.vx = 0f; b.vy = 1f
        var t = 100_000_000L
        e.step(t)
        var captured = false; var ejectedUp = false
        repeat(200) {
            t += 16_666_666L; e.step(t)
            if (b.captured > 0f) captured = true
            if (captured && b.captured <= 0f && b.vy < -5f) ejectedUp = true
        }
        assertTrue("saucer must capture", captured)
        assertTrue("saucer must eject upward", ejectedUp)
    }

    /** Шарик, брошенный в зону лапок без нажатий и автолапок, не теряется (остаётся в поле). */
    @Test
    fun ballStaysInsideFieldWithoutInput() {
        val e = newEngine()
        e.autoPawsEnabled = false
        var t = 100_000_000L
        e.step(t)
        repeat(60 * 120) {
            t += 16_666_666L; e.step(t)
            for (b in e.getBalls()) {
                assertTrue("ball escaped: ${b.px},${b.py}", b.px in -0.5f..9.5f && b.py in -1f..18.2f)
            }
        }
    }
}

class PinballLauncherTest {
    private fun newEngine() = PinballPhysicsEngine({}, {}, {}, {}, {}, Random(3)).also { it.holdOnSpawn = true; it.clearAllAndReset() }

    /** Шарик ждёт в пушке неподвижно и вылетает только после запуска. */
    @Test
    fun ballWaitsInLauncherUntilLaunched() {
        val e = newEngine()
        val b = e.getBalls().first()
        var t = 100_000_000L
        e.step(t)
        repeat(120) { t += 16_666_666L; e.step(t) }
        assertTrue(e.hasHeldBall())
        assertEquals(e.launcherPos.y, b.py, 1e-4f)
        assertTrue(e.launchBall(0.5f))
        repeat(30) { t += 16_666_666L; e.step(t) }
        assertTrue("ball must move after launch", hypot(b.px - e.launcherPos.x, b.py - e.launcherPos.y) > 0.5f)
        assertTrue(!e.hasHeldBall())
    }

    /** Слив последнего шарика сообщает о потере жизни и снова ставит шарик в пушку. */
    @Test
    fun lastBallDrainReportsLossAndReloads() {
        var lost = 0
        val e = PinballPhysicsEngine({}, {}, {}, {}, {}, Random(5))
        e.holdOnSpawn = true
        e.onBallLost = { lost++ }
        e.autoPawsEnabled = false
        e.clearAllAndReset()
        val b = e.getBalls().first()
        e.launchBall(0.5f)
        b.px = 4.5f; b.py = 17.5f; b.vx = 0f; b.vy = 6f
        var t = 100_000_000L
        e.step(t)
        repeat(30) { t += 16_666_666L; e.step(t) }
        assertEquals(1, lost)
        assertTrue(e.hasHeldBall())
    }
}
