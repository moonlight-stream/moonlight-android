@file:Suppress("DEPRECATION")
package com.limelight

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.ui.StreamView
import com.limelight.utils.UiHelper

/**
 * 外接显示器管理器
 * 负责管理外接显示器的检测、连接、断开和内容显示
 */
class ExternalDisplayManager(
    private val activity: Activity,
    private val prefConfig: PreferenceConfiguration,
    private val targetDisplayResolver: TargetDisplayResolver
) {
    private val displayManager =
        activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private var displayListener: DisplayManager.DisplayListener? = null
    private var externalPresentation: ExternalDisplayPresentation? = null
    private var displayModeSelection: DisplayModeManager.DisplayModeSelection? = null

    interface ExternalDisplayCallback {
        fun onExternalDisplayConnected(display: Display)
        fun onExternalDisplayDisconnected()
        fun onStreamViewReady(streamView: StreamView)
    }

    var callback: ExternalDisplayCallback? = null

    fun initialize(initialDisplayMode: DisplayModeManager.DisplayModeSelection? = null) {
        displayModeSelection = initialDisplayMode
        targetDisplayResolver.resolve(prefConfig.useExternalDisplay)

        setupDisplayListener()
        checkForExternalDisplay()

        if (isUsingExternalDisplay()) {
            val window = activity.window
            if (window != null) {
                val layoutParams = window.attributes
                layoutParams.screenBrightness = 0.3f
                window.attributes = layoutParams
            }
            startExternalDisplayPresentation()
        }
    }

    fun cleanup() {
        dismissExternalPresentation()

        displayListener?.let {
            displayManager.unregisterDisplayListener(it)
            displayListener = null
        }
    }

    fun getTargetDisplay(): Display {
        return targetDisplayResolver.currentDisplay()
    }

    fun isUsingExternalDisplay(): Boolean = targetDisplayResolver.isExternalDisplaySelected()

    /**
     * Stores the mode selected for a specific display and applies it to the Presentation when
     * that display is rendered. The display id prevents a stale mode id from being used after a
     * hotplug event changes the target display.
     */
    fun updateDisplayMode(selection: DisplayModeManager.DisplayModeSelection) {
        displayModeSelection = selection

        if (externalPresentation?.isForDisplay(selection.displayId) == true) {
            externalPresentation?.window?.let { DisplayModeWindowApplier.apply(it, selection) }
        }
    }

    private fun setupDisplayListener() {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                LimeLog.info("Display added: $displayId")
                if (prefConfig.useExternalDisplay && displayId != Display.DEFAULT_DISPLAY) {
                    checkForExternalDisplay()
                    if (isUsingExternalDisplay()) {
                        startExternalDisplayPresentation()
                    }
                }
            }

            override fun onDisplayRemoved(displayId: Int) {
                LimeLog.info("Display removed: $displayId")
                val wasTargetDisplay = displayId != Display.DEFAULT_DISPLAY &&
                    targetDisplayResolver.onDisplayRemoved(displayId)
                if (wasTargetDisplay) {
                    dismissExternalPresentation()

                    val surfaceView = activity.findViewById<View>(R.id.surfaceView)
                    surfaceView?.visibility = View.VISIBLE
                    Toast.makeText(activity, activity.getString(R.string.toast_external_display_disconnected), Toast.LENGTH_SHORT).show()

                    callback?.onExternalDisplayDisconnected()
                }
            }

            override fun onDisplayChanged(displayId: Int) {
                LimeLog.info("Display changed: $displayId")
            }
        }

        displayListener = listener
        displayManager.registerDisplayListener(listener, null)
    }

    private fun dismissExternalPresentation() {
        externalPresentation?.dismiss()
        externalPresentation = null
    }

    private fun checkForExternalDisplay() {
        if (!prefConfig.useExternalDisplay) {
            LimeLog.info("External display disabled by user preference")
            targetDisplayResolver.resolve(false)
            return
        }

        val display = targetDisplayResolver.resolve(true)
        if (display.displayId == Display.DEFAULT_DISPLAY) {
            LimeLog.info("No external display found, using default display")
            return
        }

        LimeLog.info("Found external display: ${display.name} (ID: ${display.displayId})")
        callback?.onExternalDisplayConnected(display)
    }

    private inner class ExternalDisplayPresentation(
        outerContext: Context,
        display: Display
    ) : Presentation(outerContext, display) {
        private val presentationDisplayId = display.displayId

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            @Suppress("DEPRECATION")
            window?.decorView?.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

            val selection = displayModeSelection
            if (selection != null && selection.displayId == presentationDisplayId) {
                window?.let { DisplayModeWindowApplier.apply(it, selection) }
            }

            setContentView(R.layout.activity_game)

            val externalStreamView = findViewById<StreamView>(R.id.surfaceView)
            if (externalStreamView != null) {
                callback?.onStreamViewReady(externalStreamView)
            }
        }

        override fun onDisplayRemoved() {
            super.onDisplayRemoved()
            activity.finish()
        }

        fun isForDisplay(displayId: Int): Boolean = presentationDisplayId == displayId
    }

    @SuppressLint("ResourceAsColor", "SetTextI18n")
    private fun startExternalDisplayPresentation() {
        if (!isUsingExternalDisplay() || externalPresentation != null) {
            return
        }

        externalPresentation = ExternalDisplayPresentation(activity, targetDisplayResolver.currentDisplay())
        externalPresentation?.show()

        val surfaceView = activity.findViewById<View>(R.id.surfaceView)
        surfaceView?.visibility = View.GONE

        if (prefConfig.enablePerfOverlay) {
            val batteryTextView = TextView(activity)
            batteryTextView.gravity = Gravity.CENTER
            batteryTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
            batteryTextView.setTextColor(androidx.core.content.ContextCompat.getColor(activity, R.color.scene_color_1))

            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            params.gravity = Gravity.CENTER
            batteryTextView.layoutParams = params

            val rootView = activity.findViewById<FrameLayout>(android.R.id.content)
            rootView?.addView(batteryTextView)

            val handler = Handler(Looper.getMainLooper())
            val gravityOptions = intArrayOf(
                Gravity.CENTER,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                Gravity.CENTER_VERTICAL or Gravity.LEFT,
                Gravity.CENTER_VERTICAL or Gravity.RIGHT,
                Gravity.TOP or Gravity.LEFT,
                Gravity.TOP or Gravity.RIGHT,
                Gravity.BOTTOM or Gravity.LEFT,
                Gravity.BOTTOM or Gravity.RIGHT
            )

            val updateBatteryTask = object : Runnable {
                override fun run() {
                    batteryTextView.text = String.format("🔋 %d%%", UiHelper.getBatteryLevel(activity))

                    val randomGravity = gravityOptions[(Math.random() * gravityOptions.size).toInt()]
                    val randomMarginLeft = (Math.random() * 401).toInt() - 200
                    val randomMarginTop = (Math.random() * 401).toInt() - 200
                    val randomMarginRight = (Math.random() * 401).toInt() - 200
                    val randomMarginBottom = (Math.random() * 401).toInt() - 200

                    val p = batteryTextView.layoutParams as FrameLayout.LayoutParams
                    p.gravity = randomGravity
                    p.setMargins(randomMarginLeft, randomMarginTop, randomMarginRight, randomMarginBottom)
                    batteryTextView.layoutParams = p

                    handler.postDelayed(this, 60000)
                }
            }
            updateBatteryTask.run()
        }

        Toast.makeText(activity, activity.getString(R.string.toast_switched_to_external_display), Toast.LENGTH_LONG).show()
    }

    companion object {
        fun hasExternalDisplay(context: Context): Boolean {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            if (displayManager != null) {
                for (display in displayManager.displays) {
                    if (display.displayId != Display.DEFAULT_DISPLAY) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
