package com.example

import com.example.screens.Busyboard.BB_ROOMS
import com.example.screens.Busyboard.BB_STARS
import com.example.screens.Busyboard.BbElement
import com.example.screens.Busyboard.BbGate
import com.example.screens.Busyboard.BbGearTrain
import com.example.screens.Busyboard.BbPanel
import com.example.screens.Busyboard.BbPanelEvent
import com.example.screens.Busyboard.BbSpring
import com.example.screens.Busyboard.bbAbacusDrag
import com.example.screens.Busyboard.bbAbacusRowRight
import com.example.screens.Busyboard.bbAward
import com.example.screens.Busyboard.bbComplete
import com.example.screens.Busyboard.bbCount
import com.example.screens.Busyboard.bbCrossings
import com.example.screens.Busyboard.bbHas
import com.example.screens.Busyboard.bbMood
import com.example.screens.Busyboard.bbNextHint
import com.example.screens.Busyboard.bbRoomCount
import com.example.screens.Busyboard.bbRoomSize
import com.example.screens.Busyboard.bbWheelDecay
import com.example.screens.Busyboard.bbWheelIndex
import com.example.screens.Busyboard.bbXyloDone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Правила «Бизиборда»: звёзды, ограничитель звука, шестерёнки, счёты, колесо и электрощиток. */
class BusyboardRulesTest {

    // ───── звёзды ─────

    @Test
    fun nineStarsAcrossThreeRooms() {
        assertEquals(9, BB_STARS)
        assertEquals(3, BB_ROOMS)
        assertEquals(9, (0 until BB_ROOMS).sumOf { bbRoomSize(it) })
        // в каждой комнате есть хотя бы одна деталь
        for (r in 0 until BB_ROOMS) assertTrue(bbRoomSize(r) > 0)
    }

    @Test
    fun starAwardIsIdempotentAndCounted() {
        var m = 0
        m = bbAward(m, BbElement.GEARS)
        m = bbAward(m, BbElement.GEARS)
        assertEquals(1, bbCount(m))
        assertTrue(bbHas(m, BbElement.GEARS))
        assertFalse(bbHas(m, BbElement.PANEL))
        assertEquals(1, bbRoomCount(m, 1))
        assertEquals(0, bbRoomCount(m, 0))
    }

    @Test
    fun completeOnlyWhenEveryElementEarned() {
        var m = 0
        for (e in BbElement.values()) {
            assertFalse(bbComplete(m))
            m = bbAward(m, e)
        }
        assertTrue(bbComplete(m))
        assertEquals(1f, bbMood(m), 0.0001f)
        assertEquals(0f, bbMood(0), 0.0001f)
    }

    @Test
    fun hintPointsAtFirstUnearnedElementOfTheRoom() {
        assertEquals(BbElement.LATCH, bbNextHint(0, 0))
        val m = bbAward(0, BbElement.LATCH)
        assertEquals(BbElement.SWITCH, bbNextHint(m, 0))
        var all = 0
        for (e in BbElement.values().filter { it.room == 2 }) all = bbAward(all, e)
        assertNull(bbNextHint(all, 2))
    }

    @Test
    fun xylophoneNeedsFiveDifferentBars() {
        assertFalse(bbXyloDone(0b0000_1111))
        assertFalse(bbXyloDone(0b0000_0001))
        assertTrue(bbXyloDone(0b0001_1111))
        assertTrue(bbXyloDone(0b1010_1011 or 0b0000_0100))
    }

    // ───── ограничитель звука ─────

    @Test
    fun gateEnforcesPerSoundGap() {
        val g = BbGate(longArrayOf(100L, 0L))
        assertTrue(g.allow(0, 1000))
        assertFalse(g.allow(0, 1050))
        assertTrue(g.allow(0, 1100))
        assertFalse(g.allow(5, 1200)) // нет такого звука
    }

    @Test
    fun gateCapsBurstsAcrossAllSounds() {
        val g = BbGate(longArrayOf(0L), globalLimit = 4, globalWindowMs = 100L)
        var played = 0
        for (i in 0 until 50) if (g.allow(0, 1000L + i)) played++ // 50 вызовов за 50 мс
        assertEquals(4, played)
        // через окно можно снова
        assertTrue(g.allow(0, 1200))
    }

    // ───── пружина ─────

    @Test
    fun springSettlesOnTarget() {
        val s = BbSpring(x = 0f, target = 1f)
        repeat(240) { s.step(1f / 60f) }
        assertEquals(1f, s.x, 0.01f)
        assertTrue(s.settled)
    }

    @Test
    fun crossingsCountEachNotchOnce() {
        assertEquals(0, bbCrossings(0.1f, 0.2f, 0.5f))
        assertEquals(1, bbCrossings(0.4f, 0.6f, 0.5f))
        assertEquals(3, bbCrossings(0.1f, 1.7f, 0.5f))
        assertEquals(3, bbCrossings(1.7f, 0.1f, 0.5f)) // назад — тоже щелчки
        assertEquals(0, bbCrossings(0f, 5f, 0f))
    }

    // ───── шестерёнки ─────

    @Test
    fun drivenGearsTurnTheOtherWayWithGearRatio() {
        val t = BbGearTrain(intArrayOf(12, 8, 6))
        t.advance(90f)
        // ведомая 8 зубьев крутится в 12/8 раза быстрее и в обратную сторону (с поправкой на ползубца)
        assertEquals(-90f * 12 / 8 + 180f / 8, t.angleOf(1), 0.001f)
        assertEquals(-90f * 12 / 6 + 180f / 6, t.angleOf(2), 0.001f)
        assertEquals(90f, t.angleOf(0), 0.001f)
    }

    @Test
    fun ratchetClicksOncePerToothAndCountsTurns() {
        val t = BbGearTrain(intArrayOf(12, 8, 6))
        val clicks = t.advance(360f) // 12 зубьев → 12 щелчков за оборот
        assertEquals(12, clicks)
        assertEquals(360f, t.turned, 0.001f)
        t.advance(-180f)
        assertEquals(540f, t.turned, 0.001f) // назад тоже считается «прокрученным»
        assertEquals(1f, t.progress(1f), 0.0001f)
        assertEquals(0.5f, BbGearTrain(intArrayOf(12, 8)).also { it.advance(540f) }.progress(3f), 0.0001f)
    }

    @Test
    fun gearInertiaFadesToStop() {
        val t = BbGearTrain(intArrayOf(12, 8, 6))
        t.omega = 900f
        var frames = 0
        while (t.omega != 0f && frames < 60 * 30) {
            t.coast(1f / 60f)
            frames++
        }
        assertEquals(0f, t.omega, 0f)
        assertTrue("шестерёнка должна остановиться за разумное время", frames < 60 * 10)
    }

    // ───── колесо ─────

    @Test
    fun wheelAlwaysStopsAndNeverReverses() {
        var omega = 1500f
        var t = 0f
        while (omega != 0f && t < 30f) {
            val next = bbWheelDecay(omega, 1f / 60f)
            assertTrue(next >= 0f)
            omega = next
            t += 1f / 60f
        }
        assertEquals(0f, omega, 0f)
        assertTrue("колесо крутится дольше 15 с", t < 15f)
        assertEquals(0f, bbWheelDecay(-3f, 0.016f), 0f)
        assertTrue(bbWheelDecay(-900f, 0.016f) < 0f)
    }

    @Test
    fun wheelIndexFollowsRotationAndStaysInRange() {
        assertEquals(0, bbWheelIndex(0f, 6))
        // повернули на 60° по часовой — под стрелкой оказался предыдущий сектор (последний)
        assertEquals(5, bbWheelIndex(60f, 6))
        assertEquals(1, bbWheelIndex(-60f, 6))
        for (a in -720..720 step 7) assertTrue(bbWheelIndex(a.toFloat(), 6) in 0..5)
    }

    // ───── счёты ─────

    @Test
    fun abacusBeadPushesNeighboursAndKeepsOrder() {
        val pos = floatArrayOf(0f, 0.1f, 0.2f, 0.3f, 0.4f)
        val pushed = bbAbacusDrag(pos, 1, 0.5f, 0.1f)
        assertEquals(0.5f, pos[1], 0.0001f)
        assertEquals(3, pushed) // три правые косточки сдвинуты
        for (j in 1 until pos.size) assertTrue("косточки не должны наезжать", pos[j] - pos[j - 1] >= 0.1f - 0.0001f)
        assertTrue(pos.last() <= 1f + 0.0001f)
    }

    @Test
    fun abacusBeadStaysInsideTheRod() {
        val pos = floatArrayOf(0f, 0.25f, 0.5f, 0.75f)
        bbAbacusDrag(pos, 0, -5f, 0.25f)
        assertEquals(0f, pos[0], 0.0001f)
        bbAbacusDrag(pos, 3, 9f, 0.25f)
        assertEquals(1f, pos[3], 0.0001f)
        bbAbacusDrag(pos, 0, 9f, 0.25f) // тянем левую до упора — она упрётся в соседей
        assertEquals(0.25f, pos[0], 0.0001f)
    }

    @Test
    fun abacusRowIsDoneWhenAllBeadsAreRight() {
        val pos = floatArrayOf(0f, 0.25f, 0.5f, 0.75f)
        assertFalse(bbAbacusRowRight(pos, 0.25f))
        bbAbacusDrag(pos, 3, 1f, 0.25f)
        bbAbacusDrag(pos, 0, 1f, 0.25f) // толкает всех вправо
        assertTrue(bbAbacusRowRight(pos, 0.25f))
    }

    // ───── электрощиток ─────

    @Test
    fun panelLampsLightOnlyWhenPowerIsOn() {
        val p = BbPanel()
        assertTrue(p.toggleBreaker(0).isEmpty()) // тока нет — лампа не горит
        assertFalse(p.lampOn(0))
        val ev = p.setPower(true)
        assertEquals(listOf(BbPanelEvent.LAMP_ON), ev.map { it.event })
        assertTrue(p.lampOn(0))
    }

    @Test
    fun threeLampsAtOnceOverloadAndLeverRestoresOrder() {
        val p = BbPanel()
        p.toggleBreaker(0); p.toggleBreaker(1); p.toggleBreaker(2)
        val on = p.setPower(true).map { it.event }
        assertTrue(BbPanelEvent.OVERLOAD in on)
        assertTrue(p.overloaded)
        val off = p.setPower(false).map { it.event }
        assertTrue(BbPanelEvent.RESTORED in off)
        assertFalse(p.overloaded)
    }

    @Test
    fun overloadCanBeCalmedWithABreaker() {
        val p = BbPanel()
        p.setPower(true)
        p.toggleBreaker(0); p.toggleBreaker(1)
        val third = p.toggleBreaker(2).map { it.event }
        assertEquals(listOf(BbPanelEvent.LAMP_ON, BbPanelEvent.OVERLOAD), third)
        val calm = p.toggleBreaker(1).map { it.event }
        assertEquals(listOf(BbPanelEvent.LAMP_OFF, BbPanelEvent.RESTORED), calm)
        assertTrue(p.power) // рубильник остался внизу, ток идёт нормально
    }

    @Test
    fun twoLampsNeverOverload() {
        val p = BbPanel()
        p.setPower(true)
        p.toggleBreaker(0); p.toggleBreaker(2)
        assertFalse(p.overloaded)
        assertEquals(2, p.lit)
    }

    @Test
    fun chaosEndsByItselfAfterAutoTrip() {
        val p = BbPanel(autoTripSec = 3f)
        p.toggleBreaker(0); p.toggleBreaker(1); p.toggleBreaker(2)
        p.setPower(true)
        var tripped = false
        var restored = false
        var t = 0f
        while (t < 10f && !restored) {
            for (n in p.tick(0.1f)) {
                if (n.event == BbPanelEvent.AUTO_TRIP) tripped = true
                if (n.event == BbPanelEvent.RESTORED) restored = true
            }
            t += 0.1f
        }
        assertTrue(tripped && restored)
        assertTrue("хаос не должен длиться дольше автовыбивания", t < 3.5f)
        assertFalse(p.power)
        assertTrue(p.tick(1f).isEmpty())
    }
}
