package com.example

import com.example.ui.screens.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class CookRulesTest {
    private fun carry(f: CFam, d: CDone, chopped: Boolean = true) = CCarry(f, chopped, true, d)

    @Test fun lovedNeedsWantedPerfectAndChopped() {
        assertEquals(CReact.LOVED, cookJudge(listOf(carry(CFam.VEG, CDone.PERFECT)), CGuest.BUNNY))
        assertEquals(CReact.LIKED, cookJudge(listOf(carry(CFam.VEG, CDone.WARM)), CGuest.BUNNY))
        assertEquals(CReact.LIKED, cookJudge(listOf(carry(CFam.VEG, CDone.PERFECT, chopped = false)), CGuest.BUNNY))
    }

    @Test fun wrongOrBurntDishNeverBreaksTheGame() {
        assertEquals(CReact.MEH, cookJudge(listOf(carry(CFam.CHEESE, CDone.PERFECT)), CGuest.BUNNY))
        assertEquals(CReact.SAD, cookJudge(listOf(carry(CFam.VEG, CDone.BURNT)), CGuest.BUNNY))
        assertEquals(CReact.SAD, cookJudge(emptyList(), CGuest.BUNNY))
    }

    @Test fun pandaWantsTwoPerfectParts() {
        assertEquals(CReact.LIKED, cookJudge(listOf(carry(CFam.VEG, CDone.PERFECT)), CGuest.PANDA))
        assertEquals(CReact.LOVED, cookJudge(listOf(carry(CFam.VEG, CDone.PERFECT), carry(CFam.FRUIT, CDone.PERFECT)), CGuest.PANDA))
    }

    @Test fun donenessBands() {
        val w = 2f
        assertEquals(CDone.RAW, cookDoneAt(0.5f, w)); assertEquals(CDone.WARM, cookDoneAt(2.5f, w))
        assertEquals(CDone.PERFECT, cookDoneAt(4f, w)); assertEquals(CDone.BURNT, cookDoneAt(6f, w))
    }

    @Test fun fullChainServesGuestAndOpensRecipe() {
        val sim = CookSim(CProfile(), Random(5))
        sim.step(1.0f) // первый гость садится (зайка)
        sim.step(0.1f)
        val seat = sim.seats.indexOfFirst { it.guest == CGuest.BUNNY }
        assertTrue("зайка должен сесть", seat >= 0)
        sim.belt.add(CBelt(999, CFam.VEG, 100f))
        assertTrue(sim.tapBelt(999))
        assertTrue(sim.tapBoard())                       // положить
        // дождаться золотой зоны маркера
        var guard = 0
        while (kotlin.math.abs(sim.chopPos(sim.board!!) - 0.5f) > 0.05f && guard++ < 1000) sim.step(0.01f)
        assertTrue(sim.tapBoard())                       // режь
        assertTrue(sim.board!!.chopped)
        assertTrue(sim.tapBoard())                       // взять
        assertTrue(sim.tapPan(0))                        // на сковороду
        sim.pans[0].food!!.t = CookCfg.PERFECT_START + 0.3f
        assertTrue(sim.tapPan(0))                        // снять в золотой момент
        assertEquals(CDone.PERFECT, sim.hand!!.done)
        assertTrue(sim.tapPlate())
        assertTrue(sim.tapGuest(seat))
        assertEquals(3, sim.starsEarned)
        assertTrue(sim.profile.recipes.contains("veg"))
        assertTrue(sim.events.any { it.type == CEv.RECIPE })
    }

    @Test fun sadServeKeepsGuestWaiting() {
        val sim = CookSim(CProfile(), Random(5)); sim.step(1.0f)
        val seat = sim.seats.indexOfFirst { it.guest != null }
        sim.hand = carry(CFam.VEG, CDone.BURNT)
        assertTrue(sim.tapGuest(seat))
        sim.step(2.0f)
        assertNotNull("гость остаётся после невкусного блюда", sim.seats[seat].guest)
        assertEquals(0, sim.starsEarned)
    }

    @Test fun cannotPickTwoThingsOrServeRaw() {
        val sim = CookSim(CProfile(), Random(5)); sim.step(1.0f)
        sim.belt.add(CBelt(1, CFam.VEG, 50f)); sim.belt.add(CBelt(2, CFam.MUSH, 80f))
        assertTrue(sim.tapBelt(1)); assertFalse(sim.tapBelt(2))
        val seat = sim.seats.indexOfFirst { it.guest != null }
        assertFalse(sim.tapGuest(seat))
        assertTrue(sim.tapTrash()); assertNull(sim.hand)
    }

    @Test fun shiftAlwaysEndsWithAtLeastOneStarAndProgresses() {
        val sim = CookSim(CProfile(), Random(2))
        var t = 0f
        while (!sim.cleared && t < 2000f) { sim.step(0.1f); t += 0.1f }   // никто не играет: все гости уходят
        assertTrue(sim.cleared)
        assertTrue(sim.rating >= 1)
        assertEquals(2, sim.profile.night)
    }

    @Test fun giftsAreDistinctAndRespectCaps() {
        val p = CProfile(); p.mods[CMod.STOVE] = 1
        val sim = CookSim(p, Random(1))
        val g = sim.offerGifts()
        assertEquals(3, g.size); assertEquals(3, g.toSet().size); assertFalse(g.contains(CMod.STOVE))
        sim.pickGift(CMod.GOLD); sim.pickGift(CMod.GOLD); sim.pickGift(CMod.GOLD); sim.pickGift(CMod.GOLD)
        assertEquals(3, p.level(CMod.GOLD))
        assertEquals(3, CookSim(p).pans.size)
    }

    @Test fun profileSurvivesSaveAndLoad() {
        val p = CProfile(night = 4, stars = 17)
        p.mods[CMod.GOLD] = 2; p.mods[CMod.KNIFE] = 1; p.recipes.add("veg"); p.recipes.add("mush+veg")
        val q = CProfile.decode(p.encode())
        assertEquals(4, q.night); assertEquals(17, q.stars)
        assertEquals(2, q.level(CMod.GOLD)); assertEquals(setOf("veg", "mush+veg"), q.recipes)
        assertEquals(1, CProfile.decode("мусор|||").night)
        assertEquals(1, CProfile.decode(null).night)
    }

    @Test fun hintLeadsThroughTheChain() {
        val sim = CookSim(CProfile(), Random(5)); sim.step(1.0f)
        sim.belt.add(CBelt(7, CFam.VEG, 100f))
        assertEquals(CHint.BELT, sim.hint()!!.kind)
        sim.tapBelt(7)
        assertEquals(CHint.BOARD, sim.hint()!!.kind)
    }
}
