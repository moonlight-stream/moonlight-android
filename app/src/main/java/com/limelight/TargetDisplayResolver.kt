package com.limelight

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

/**
 * Resolves the display used by a streaming session.
 *
 * Display selection is intentionally kept separate from Presentation creation. The stream
 * configuration is built before [ExternalDisplayManager] creates its Presentation, so using
 * the Presentation manager as the source of truth would make early Native/HDR/refresh-rate
 * decisions fall back to the default display.
 */
class TargetDisplayResolver(context: Context) {
    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private var targetDisplayId = Display.DEFAULT_DISPLAY

    fun resolve(useExternalDisplay: Boolean): Display {
        if (!useExternalDisplay) {
            targetDisplayId = Display.DEFAULT_DISPLAY
            return getDefaultDisplay()
        }

        val currentTarget = displayManager.getDisplay(targetDisplayId)
        if (currentTarget != null && currentTarget.displayId != Display.DEFAULT_DISPLAY) {
            return currentTarget
        }

        val externalDisplay = displayManager.displays.firstOrNull {
            it.displayId != Display.DEFAULT_DISPLAY
        }

        if (externalDisplay != null) {
            targetDisplayId = externalDisplay.displayId
            return externalDisplay
        }

        targetDisplayId = Display.DEFAULT_DISPLAY
        return getDefaultDisplay()
    }

    fun currentDisplay(): Display {
        return displayManager.getDisplay(targetDisplayId) ?: getDefaultDisplay()
    }

    fun isExternalDisplaySelected(): Boolean {
        return targetDisplayId != Display.DEFAULT_DISPLAY &&
            displayManager.getDisplay(targetDisplayId) != null
    }

    /** Clears the selected target when it is removed and reports whether it was selected. */
    fun onDisplayRemoved(displayId: Int): Boolean {
        if (targetDisplayId != displayId) {
            return false
        }

        targetDisplayId = Display.DEFAULT_DISPLAY
        return true
    }

    private fun getDefaultDisplay(): Display {
        return displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            ?: error("Default display is unavailable")
    }
}
