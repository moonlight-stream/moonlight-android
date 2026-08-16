package com.limelight

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorModePolicyTest {
    @Test
    fun `touchpad uses host cursor when local cursor is disabled`() {
        assertFalse(
            CursorModePolicy.shouldUseLocalMode(
                nativePointerSupported = true,
                nativePointerEnabled = false,
                touchpadEnabled = true,
                localCursorEnabled = false,
                hasCursorOverlay = true
            )
        )
        assertFalse(
            CursorModePolicy.shouldRenderTouchpadOverlay(
                localModeActive = true,
                nativePointerSupported = true,
                touchpadEnabled = true,
                localCursorEnabled = false,
                nativePointerEnabled = false
            )
        )
    }

    @Test
    fun `touchpad can opt in to local cursor when an overlay is available`() {
        assertTrue(
            CursorModePolicy.shouldUseLocalMode(
                nativePointerSupported = true,
                nativePointerEnabled = false,
                touchpadEnabled = true,
                localCursorEnabled = true,
                hasCursorOverlay = true
            )
        )
        assertTrue(
            CursorModePolicy.shouldRenderTouchpadOverlay(
                localModeActive = true,
                nativePointerSupported = true,
                touchpadEnabled = true,
                localCursorEnabled = true,
                nativePointerEnabled = false
            )
        )
    }

    @Test
    fun `local cursor is not enabled without an overlay`() {
        assertFalse(
            CursorModePolicy.shouldUseLocalMode(
                nativePointerSupported = true,
                nativePointerEnabled = false,
                touchpadEnabled = true,
                localCursorEnabled = true,
                hasCursorOverlay = false
            )
        )
    }

    @Test
    fun `native pointer remains independent from touchpad local cursor setting`() {
        assertTrue(
            CursorModePolicy.shouldUseLocalMode(
                nativePointerSupported = true,
                nativePointerEnabled = true,
                touchpadEnabled = false,
                localCursorEnabled = false,
                hasCursorOverlay = false
            )
        )
        assertFalse(
            CursorModePolicy.shouldRenderTouchpadOverlay(
                localModeActive = true,
                nativePointerSupported = true,
                touchpadEnabled = true,
                localCursorEnabled = true,
                nativePointerEnabled = true
            )
        )
    }

    @Test
    fun `native pointer requires platform support`() {
        assertFalse(
            CursorModePolicy.shouldUseLocalMode(
                nativePointerSupported = false,
                nativePointerEnabled = true,
                touchpadEnabled = false,
                localCursorEnabled = false,
                hasCursorOverlay = true
            )
        )
    }

    @Test
    fun `unsupported native pointer preference does not suppress touchpad overlay`() {
        assertTrue(
            CursorModePolicy.shouldUseLocalMode(
                nativePointerSupported = false,
                nativePointerEnabled = true,
                touchpadEnabled = true,
                localCursorEnabled = true,
                hasCursorOverlay = true
            )
        )
        assertTrue(
            CursorModePolicy.shouldRenderTouchpadOverlay(
                localModeActive = true,
                nativePointerSupported = false,
                touchpadEnabled = true,
                localCursorEnabled = true,
                nativePointerEnabled = true
            )
        )
    }
}
