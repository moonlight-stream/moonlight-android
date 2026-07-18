package com.limelight

import android.os.Build
import android.view.Window

/** Applies a display mode selection to the Window rendered on that display. */
internal object DisplayModeWindowApplier {
    fun apply(window: Window, selection: DisplayModeManager.DisplayModeSelection) {
        val layoutParams = window.attributes

        if (selection.preferredModeId >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            layoutParams.preferredDisplayModeId = selection.preferredModeId
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            layoutParams.preferredRefreshRate = selection.refreshRate
        } else {
            return
        }

        window.attributes = layoutParams
    }
}
