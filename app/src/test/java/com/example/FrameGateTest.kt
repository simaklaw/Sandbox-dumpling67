package com.example

import com.example.shared.ECO_AFTER_MS
import com.example.shared.ECO_DEEP_AFTER_MS
import com.example.shared.ecoDelayMs
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
        assertEquals(50L, ecoDelayMs(ECO_DEEP_AFTER_MS))
    }
}
