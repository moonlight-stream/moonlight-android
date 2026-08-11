package com.limelight.gamemenu

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.limelight.R
import com.limelight.preferences.GlPreferences

/**
 * Rendering choices for the in-stream menu overlay.
 *
 * A translucent dialog above a continuously updating SurfaceView can force old
 * SurfaceFlinger/HWC implementations down the GPU composition path every frame.
 * Keep the decorative path on capable devices, but use an opaque, solid overlay
 * on legacy or known low-end devices.
 */
internal data class GameMenuRenderingProfile(val isLowEnd: Boolean) {
    val useFabricTexture: Boolean
        get() = !isLowEnd

    val windowAlpha: Float
        get() = if (isLowEnd) 1.0f else DEFAULT_WINDOW_ALPHA

    val dialogAnimationStyle: Int
        get() = if (isLowEnd) R.style.GameMenuDialogAnimationLowEnd
        else R.style.GameMenuDialogAnimation

    companion object {
        private const val DEFAULT_WINDOW_ALPHA = 0.9f
        private const val LOW_MEMORY_CLASS_MB = 128

        private val lowEndRendererMarkers = listOf(
            "adreno (tm) 2",
            "adreno (tm) 3",
            "adreno 2",
            "adreno 3",
            "mali-t",
            "mali-4",
            "mali-3",
            "powervr sgx",
            "powervr rogue",
            "vivante",
            "swiftshader",
            "llvmpipe"
        )

        fun from(context: Context): GameMenuRenderingProfile {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val renderer = GlPreferences.readPreferences(context).glRenderer
            return GameMenuRenderingProfile(
                isLowEnd(
                    sdkInt = Build.VERSION.SDK_INT,
                    isLowRamDevice = activityManager?.isLowRamDevice == true,
                    memoryClassMb = activityManager?.memoryClass ?: Int.MAX_VALUE,
                    glRenderer = renderer
                )
            )
        }

        /**
         * Kept independent from Android services so the classification remains
         * deterministic and easy to cover with unit tests.
         */
        internal fun isLowEnd(
            sdkInt: Int,
            isLowRamDevice: Boolean,
            memoryClassMb: Int,
            glRenderer: String
        ): Boolean {
            if (sdkInt <= Build.VERSION_CODES.M) {
                // Android 6-era SurfaceFlinger/HWC combinations are especially
                // sensitive to a translucent window over a video SurfaceView.
                return true
            }
            if (isLowRamDevice || memoryClassMb in 1..LOW_MEMORY_CLASS_MB) {
                return true
            }

            val normalizedRenderer = glRenderer.lowercase()
            return lowEndRendererMarkers.any(normalizedRenderer::contains)
        }
    }
}
