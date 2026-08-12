package com.limelight

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneResolutionModeTest {
    private val presets = setOf("1280x720", "1920x1080", "2560x1440")

    @Test
    fun legacyNativeResolutionIsDerivedFromDimensions() {
        val mode = resolveSceneResolutionMode(JSONObject(), 2340, 1080, presets)

        assertTrue(mode.isNative)
        assertFalse(mode.isCustom)
    }

    @Test
    fun legacyPresetResolutionIsNotNativeOrCustom() {
        val mode = resolveSceneResolutionMode(JSONObject(), 1920, 1080, presets)

        assertFalse(mode.isNative)
        assertFalse(mode.isCustom)
    }

    @Test
    fun explicitCustomResolutionModeIsRestored() {
        val mode = resolveSceneResolutionMode(
            JSONObject()
                .put("isNativeResolution", false)
                .put("isCustomResolution", true),
            1170,
            540,
            presets
        )

        assertFalse(mode.isNative)
        assertTrue(mode.isCustom)
    }
}
