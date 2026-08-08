package com.limelight.binding.input

import com.limelight.nvstream.input.ControllerPacket
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerButtonChordStateTest {
    @Test
    fun snapshotTriggersOnceUntilARequiredButtonIsReleased() {
        val state = ControllerButtonChordState(COMBO_FLAGS)

        assertFalse(state.updateSnapshot(SELECT or LB or RB))
        assertTrue(state.updateSnapshot(COMBO_FLAGS))
        assertFalse(state.updateSnapshot(COMBO_FLAGS or ControllerPacket.A_FLAG))
        assertFalse(state.updateSnapshot(SELECT or LB or RB))
        assertTrue(state.updateSnapshot(COMBO_FLAGS))
    }

    @Test
    fun individualButtonUpdatesUseTheSameLatchBehavior() {
        val state = ControllerButtonChordState(COMBO_FLAGS)

        assertFalse(state.updateButton(SELECT, true))
        assertFalse(state.updateButton(LB, true))
        assertFalse(state.updateButton(RB, true))
        assertTrue(state.updateButton(X, true))
        assertFalse(state.updateButton(X, true))
        assertFalse(state.updateButton(X, false))
        assertTrue(state.updateButton(X, true))
    }

    companion object {
        private const val SELECT = ControllerPacket.BACK_FLAG
        private const val LB = ControllerPacket.LB_FLAG
        private const val RB = ControllerPacket.RB_FLAG
        private const val X = ControllerPacket.X_FLAG
        private const val COMBO_FLAGS = SELECT or LB or RB or X
    }
}
