package com.example

import com.example.ui.screens.BF_LIVES
import com.example.ui.screens.BF_MODE_CALM
import com.example.ui.screens.BF_MODE_DEADLY
import com.example.ui.screens.BF_MODE_HEARTS
import com.example.ui.screens.BfCrash
import com.example.ui.screens.bfBeatsRecord
import com.example.ui.screens.bfCrashOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Правила режимов «Птички-летуньи»: что считается ударом и когда игра проиграна. */
class BirdRulesTest {

    private fun hit(mode: Int, floor: Boolean = false, demo: Boolean = false, inv: Boolean = false, lives: Int = BF_LIVES) =
        bfCrashOutcome(mode, demo, floor, inv, lives)

    @Test
    fun calmModeNeverKills() {
        assertEquals(BfCrash.BUMP, hit(BF_MODE_CALM))
        assertEquals(BfCrash.BUMP, hit(BF_MODE_CALM, inv = true))
        // земля в спокойном режиме только мягко подбрасывает, без «ойка»
        assertEquals(BfCrash.NONE, hit(BF_MODE_CALM, floor = true))
    }

    @Test
    fun deadlyModeKillsOnPipeAndOnFloor() {
        assertEquals(BfCrash.DIE, hit(BF_MODE_DEADLY))
        assertEquals(BfCrash.DIE, hit(BF_MODE_DEADLY, floor = true))
        // даже сразу после «призрачности» — смертельный режим не прощает
        assertEquals(BfCrash.DIE, hit(BF_MODE_DEADLY, inv = true))
    }

    @Test
    fun demoBotNeverLosesInAnyMode() {
        listOf(BF_MODE_CALM, BF_MODE_HEARTS, BF_MODE_DEADLY).forEach { mode ->
            assertEquals(BfCrash.BUMP, hit(mode, demo = true))
            assertEquals(BfCrash.NONE, hit(mode, floor = true, demo = true))
        }
    }

    @Test
    fun heartsModeLosesOneHeartPerHitAndDiesOnTheLast() {
        assertEquals(BfCrash.LOSE_LIFE, hit(BF_MODE_HEARTS, lives = 3))
        assertEquals(BfCrash.LOSE_LIFE, hit(BF_MODE_HEARTS, floor = true, lives = 2))
        assertEquals(BfCrash.DIE, hit(BF_MODE_HEARTS, lives = 1))
        assertEquals(BfCrash.DIE, hit(BF_MODE_HEARTS, floor = true, lives = 1))
    }

    @Test
    fun heartsModeGivesGraceAfterAHit() {
        assertEquals(BfCrash.NONE, hit(BF_MODE_HEARTS, inv = true, lives = 3))
        assertEquals(BfCrash.NONE, hit(BF_MODE_HEARTS, inv = true, lives = 1))
    }

    @Test
    fun threeHeartsMeansThreeHitsToLose() {
        var lives = BF_LIVES
        var hits = 0
        while (true) {
            hits++
            when (hit(BF_MODE_HEARTS, lives = lives)) {
                BfCrash.LOSE_LIFE -> lives--
                BfCrash.DIE -> break
                else -> error("неожиданный исход")
            }
        }
        assertEquals(BF_LIVES, hits)
    }

    @Test
    fun recordNeedsAPositiveScoreThatBeatsTheOldOne() {
        assertTrue(bfBeatsRecord(score = 6, recordAtStart = 5))
        assertTrue(bfBeatsRecord(score = 1, recordAtStart = 0))
        assertFalse(bfBeatsRecord(score = 5, recordAtStart = 5))
        assertFalse(bfBeatsRecord(score = 0, recordAtStart = 0))
        assertFalse(bfBeatsRecord(score = 3, recordAtStart = 9))
    }
}
