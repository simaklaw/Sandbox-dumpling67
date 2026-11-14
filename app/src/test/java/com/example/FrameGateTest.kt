package com.example

import com.example.shared.ECO_AFTER_MS
import com.example.shared.ECO_DEEP_AFTER_MS
import com.example.shared.FRAME_NS
import com.example.shared.ecoDelayMs
import com.example.shared.ecoSteps
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameGateTest {
    @Test fun fullRateWhileChildPlays() {
        assertEquals(0L, ecoDelayMs(0L))
        assertEquals(0L, ecoDelayMs(ECO_AFTER_MS - 1))
    }

    @Test fun framesThinOutWhenIdle() {
        assertEquals(20L, ecoDelayMs(ECO_AFTER_MS))
        assertEquals(20L, ecoDelayMs(ECO_DEEP_AFTER_MS - 1))
        assertEquals(40L, ecoDelayMs(ECO_DEEP_AFTER_MS))
    }

    @Test fun skippedFramesAreReplayedSoSpeedStaysTheSame() {
        assertEquals(1, ecoSteps(FRAME_NS))
        assertEquals(1, ecoSteps(0L))
        assertEquals(2, ecoSteps(FRAME_NS * 2))
        assertEquals(2, ecoSteps(28_000_000L))   // 20 мс паузы + ожидание vsync
        assertEquals(3, ecoSteps(50_000_000L))
        assertEquals(4, ecoSteps(500_000_000L))  // потолок: после долгой паузы не взрываем симуляцию
    }
}
