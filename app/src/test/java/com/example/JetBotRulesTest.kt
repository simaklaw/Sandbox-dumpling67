package com.example

import com.example.ui.screens.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class JetBotRulesTest {
    private fun run(w: JetWorld, seconds: Float, touch: (Float) -> Boolean) {
        var t = 0f
        while (t < seconds && w.state == JET_PLAY) { w.step(1f / 60f, touch(t)); t += 1f / 60f }
    }

    @Test fun playerAlwaysStaysInsideCave() {
        val w = JetWorld(780f, Random(7), god = true)
        var t = 0f
        while (t < 60f && w.state == JET_PLAY) {
            w.step(1f / 60f, (t * 1.7f).toInt() % 2 == 0)
            val (c, f) = w.caveAt(w.playerX + JetCfg.PLAYER / 2f)
            assertTrue("выше потолка при t=$t", w.playerY >= c)
            assertTrue("ниже пола при t=$t", w.playerY + JetCfg.PLAYER <= f)
            t += 1f / 60f
        }
    }

    @Test fun caveCoversScreenAndKeepsPassableGap() {
        val w = JetWorld(780f, Random(3), god = true)
        run(w, 40f) { it % 1f < 0.5f }
        assertTrue(w.cave.last().x >= w.lw)
        w.cave.forEach { assertTrue("пещера слишком узкая", it.floor - it.ceil >= 300f) }
    }

    @Test fun godModeReachesFlagAndWins() {
        val w = JetWorld(780f, Random(11), god = true)
        var t = 0f
        while (t < 240f && w.state == JET_PLAY) {
            // «умный» палец: держим робота у середины пещеры
            val (c, f) = w.caveAt(w.playerX)
            w.step(1f / 60f, w.playerY + JetCfg.PLAYER / 2f > (c + f) / 2f)
            t += 1f / 60f
        }
        assertEquals(JET_WON, w.state)
        assertTrue(w.events.any { it.type == JetEvent.WIN })
    }

    @Test fun hitCostsHeartThenGivesGhostTime() {
        val w = JetWorld(780f, Random(1))
        w.obs.add(JetObs(JetKind.GEAR, w.playerX, w.playerY, 34f, 34f, w.playerY, 0f, 0f, 0f, false))
        w.step(1f / 60f, false)
        assertEquals(JetCfg.LIVES - 1, w.lives)
        assertTrue(w.invuln > 1f)
        // второй удар во время «призрака» ничего не отнимает
        w.obs.add(JetObs(JetKind.GEAR, w.playerX, w.playerY, 34f, 34f, w.playerY, 0f, 0f, 0f, false))
        w.step(1f / 60f, false)
        assertEquals(JetCfg.LIVES - 1, w.lives)
    }

    @Test fun shieldAbsorbsHitWithoutLosingHeart() {
        val w = JetWorld(780f, Random(1)); w.shield = true
        w.obs.add(JetObs(JetKind.GEAR, w.playerX, w.playerY, 34f, 34f, w.playerY, 0f, 0f, 0f, false))
        w.step(1f / 60f, false)
        assertEquals(JetCfg.LIVES, w.lives); assertFalse(w.shield)
    }

    @Test fun threeHitsEndRunSoftly() {
        val w = JetWorld(780f, Random(1))
        repeat(3) {
            w.invuln = 0f
            w.obs.add(JetObs(JetKind.GEAR, w.playerX, w.playerY, 34f, 34f, w.playerY, 0f, 0f, 0f, false))
            w.step(1f / 60f, false)
        }
        assertEquals(JET_LOST, w.state)
    }

    @Test fun powerUpsWork() {
        val w = JetWorld(780f, Random(1))
        w.pows.add(JetPow(JetPower.BOOST, w.playerX, w.playerY, 44f))
        w.step(1f / 60f, false)
        assertTrue(w.boosting)
        w.pows.add(JetPow(JetPower.SHIELD, w.playerX, w.playerY, 44f))
        w.step(1f / 60f, false)
        assertTrue(w.shield)
    }

    @Test fun holdRaisesRobotReleaseLowersIt() {
        val up = JetWorld(780f, Random(1)); val y0 = up.playerY
        run(up, 0.5f) { true }
        assertTrue(up.playerY < y0)
        val down = JetWorld(780f, Random(1)); val y1 = down.playerY
        run(down, 0.5f) { false }
        assertTrue(down.playerY > y1)
    }
}
