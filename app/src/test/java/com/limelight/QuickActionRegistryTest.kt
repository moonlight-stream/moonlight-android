package com.limelight

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickActionRegistryTest {
    @Test
    fun hotkeyActionsUseTextBadgeInsteadOfPlaceholderDrawables() {
        listOf("send_tab", "send_alt_tab", "send_alt_f4").forEach { id ->
            val action = requireNotNull(QuickActionRegistry.getBuiltin(id))
            assertEquals(0, action.iconRes)
            assertEquals("HK", action.iconText)
        }
    }

    @Test
    fun baseDefaultsFavorFrequentReversibleActions() {
        assertEquals(
            listOf(
                "send_esc",
                "send_win",
                "send_alt_tab",
                "toggle_keyboard",
                "toggle_perf",
                "quit"
            ),
            QuickActionRegistry.defaultIdsFor(
                enableHdr = false,
                enableMic = false,
                enableVirtualController = false
            )
        )
    }

    @Test
    fun optionalDefaultsFollowEnabledFeatures() {
        assertEquals(
            listOf(
                "send_esc",
                "send_win",
                "send_alt_tab",
                "toggle_keyboard",
                "toggle_perf",
                "toggle_hdr",
                "toggle_mic",
                "toggle_controller",
                "quit"
            ),
            QuickActionRegistry.defaultIdsFor(
                enableHdr = true,
                enableMic = true,
                enableVirtualController = true
            )
        )
    }
}
