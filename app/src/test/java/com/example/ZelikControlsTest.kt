package com.example

import com.example.ui.screens.ZelikControls
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZelikControlsTest {
    @Test
    fun thumbDriftingDownWhileRunningDoesNotCrouch() {
        // Раньше stickY > 0.55 уже приседал и запрещал прыжок.
        assertFalse(ZelikControls.crouch(0.8f, 0.6f, onGround = true))
        assertFalse(ZelikControls.crouch(0.9f, 0.8f, onGround = true))
        assertFalse(ZelikControls.crouch(0f, 0.5f, onGround = true))
    }

    @Test
    fun deliberatePullDownCrouchesOnlyOnGround() {
        assertTrue(ZelikControls.crouch(0.1f, 0.95f, onGround = true))
        assertFalse(ZelikControls.crouch(0.1f, 0.95f, onGround = false))
    }

    @Test
    fun stickUpJumpsButDiagonalRunDoesNot() {
        assertTrue(ZelikControls.stickJump(0f, -0.9f))
        assertTrue(ZelikControls.stickJump(0.3f, -0.85f))
        assertFalse(ZelikControls.stickJump(0.8f, -0.8f))
        assertFalse(ZelikControls.stickJump(0.5f, -0.4f))
        assertFalse(ZelikControls.stickJump(0f, 0.9f))
    }

    @Test
    fun jumpBufferIsShortButNotZero() {
        assertTrue(ZelikControls.JUMP_BUFFER in 4..10)
    }
}
