package com.limelight

import org.json.JSONObject

internal data class SceneResolutionMode(
    val isNative: Boolean,
    val isCustom: Boolean
)

internal fun resolveSceneResolutionMode(
    json: JSONObject,
    width: Int,
    height: Int,
    presetResolutions: Collection<String>
): SceneResolutionMode {
    if (json.has("isNativeResolution") && json.has("isCustomResolution")) {
        return SceneResolutionMode(
            isNative = json.optBoolean("isNativeResolution"),
            isCustom = json.optBoolean("isCustomResolution")
        )
    }

    val matchesPreset = presetResolutions.contains("${width}x$height")
    return SceneResolutionMode(isNative = !matchesPreset, isCustom = false)
}
