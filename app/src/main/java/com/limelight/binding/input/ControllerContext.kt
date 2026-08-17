package com.limelight.binding.input

import android.annotation.TargetApi
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.input.InputManager
import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.hardware.lights.LightsManager
import android.hardware.lights.LightsRequest
import android.os.Build
import android.os.VibratorManager
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.Toast

import com.limelight.gamemenu.GameMenu
import com.limelight.R
import com.limelight.binding.input.driver.AbstractController
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import java.util.concurrent.ConcurrentHashMap

// =================================================================================
// GenericControllerContext
// =================================================================================

open class GenericControllerContext(
    internal val handler: ControllerHandler
) : GameInputDevice {
    var id: Int = 0
    var external: Boolean = false

    var vendorId: Int = 0
    var productId: Int = 0

    var leftStickDeadzoneRadius: Float = 0f
    var rightStickDeadzoneRadius: Float = 0f
    var triggerDeadzone: Float = 0f

    var assignedControllerNumber: Boolean = false
    var reservedControllerNumber: Boolean = false
    internal val controllerArrival = ControllerArrivalTracker()
    var controllerNumber: Short = 0

    var inputMap: Int = 0
    internal val performanceOverlayShortcutState = ControllerButtonChordState(
        ControllerHandler.PERFORMANCE_OVERLAY_COMBO_FLAGS
    )
    var leftTrigger: Byte = 0x00
    var rightTrigger: Byte = 0x00
    var rightStickX: Short = 0x0000
    var rightStickY: Short = 0x0000
    var physRightStickX: Short = 0x0000
    var physRightStickY: Short = 0x0000
    var gyroRightStickX: Short = 0x0000
    var gyroRightStickY: Short = 0x0000
    var leftStickX: Short = 0x0000
    var leftStickY: Short = 0x0000

    var gyroHoldActive: Boolean = false

    var startDownTime: Long = 0

    var mouseEmulationActive: Boolean = false
    var mouseEmulationLastInputMap: Int = 0
    val mouseEmulationReportPeriod: Int = 50

    val mouseEmulationRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!mouseEmulationActive) {
                return
            }

            // Send mouse events from analog sticks
            if (handler.prefConfig.analogStickForScrolling == PreferenceConfiguration.AnalogStickForScrolling.RIGHT) {
                handler.sendEmulatedMouseMove(leftStickX, leftStickY)
                handler.sendEmulatedMouseScroll(rightStickX, rightStickY)
            } else if (handler.prefConfig.analogStickForScrolling == PreferenceConfiguration.AnalogStickForScrolling.LEFT) {
                handler.sendEmulatedMouseMove(rightStickX, rightStickY)
                handler.sendEmulatedMouseScroll(leftStickX, leftStickY)
            } else {
                handler.sendEmulatedMouseMove(leftStickX, leftStickY)
                handler.sendEmulatedMouseMove(rightStickX, rightStickY)
            }

            // Requeue the callback
            handler.mainThreadHandler.postDelayed(this, mouseEmulationReportPeriod.toLong())
        }
    }

    override fun getGameMenuQuickOptions(): List<GameMenu.MenuOption> {
        return listOf(
            GameMenu.MenuOption(
                label = handler.activityContext.getString(R.string.game_menu_controller_mouse),
                isWithGameFocus = false,
                runnable = null,
                iconKey = "game_menu_mouse_emulation",
                isShowIcon = true,
                isKeepDialog = true,
                inlineControl = GameMenu.InlineControl.Toggle(
                    checked = mouseEmulationActive,
                    toggleAction = Runnable(::toggleMouseEmulation)
                )
            )
        )
    }

    fun toggleMouseEmulation() {
        handler.mainThreadHandler.removeCallbacks(mouseEmulationRunnable)
        mouseEmulationActive = !mouseEmulationActive

        val messageResId = if (mouseEmulationActive)
            R.string.game_menu_toggle_mouse_on else R.string.game_menu_toggle_mouse_off
        Toast.makeText(handler.activityContext, messageResId, Toast.LENGTH_SHORT).show()

        if (mouseEmulationActive) {
            handler.mainThreadHandler.postDelayed(mouseEmulationRunnable, mouseEmulationReportPeriod.toLong())
        }
    }

    open fun destroy() {
        mouseEmulationActive = false
        handler.mainThreadHandler.removeCallbacks(mouseEmulationRunnable)
    }

    open fun sendControllerArrival(): Int = 0
}

// =================================================================================
// InputDeviceContext
// =================================================================================

class InputDeviceContext(handler: ControllerHandler) : GenericControllerContext(handler) {
    var name: String? = null
    var vibratorManager: VibratorManager? = null
    var vibrator: android.os.Vibrator? = null
    var quadVibrators: Boolean = false
    var lowFreqMotor: Short = 0
    var highFreqMotor: Short = 0
    var leftTriggerMotor: Short = 0
    var rightTriggerMotor: Short = 0

    var sensorManager: SensorManager? = null
    var gyroListener: SensorEventListener? = null
    var gyroReportRateHz: Short = 0
    var accelListener: SensorEventListener? = null
    var accelReportRateHz: Short = 0

    var inputDevice: InputDevice? = null

    var hasRgbLed: Boolean = false
    var lightsSession: LightsManager.LightsSession? = null

    // These are BatteryState values, not Moonlight values
    var lastReportedBatteryStatus: Int = 0
    var lastReportedBatteryCapacity: Float = 0f

    var leftStickXAxis: Int = -1
    var leftStickYAxis: Int = -1

    var rightStickXAxis: Int = -1
    var rightStickYAxis: Int = -1

    var leftTriggerAxis: Int = -1
    var rightTriggerAxis: Int = -1
    var triggersIdleNegative: Boolean = false
    var leftTriggerAxisUsed: Boolean = false
    var rightTriggerAxisUsed: Boolean = false

    var hatXAxis: Int = -1
    var hatYAxis: Int = -1
    var hatXAxisUsed: Boolean = false
    var hatYAxisUsed: Boolean = false

    var touchpadXRange: InputDevice.MotionRange? = null
    var touchpadYRange: InputDevice.MotionRange? = null
    var touchpadPressureRange: InputDevice.MotionRange? = null

    var isNonStandardDualShock4: Boolean = false
    var usesLinuxGamepadStandardFaceButtons: Boolean = false
    var isNonStandardXboxBtController: Boolean = false
    var isServal: Boolean = false
    var backIsStart: Boolean = false
    var modeIsSelect: Boolean = false
    var searchIsMode: Boolean = false
    var ignoreBack: Boolean = false
    var hasJoystickAxes: Boolean = false
    var pendingExit: Boolean = false
    var isDualShockStandaloneTouchpad: Boolean = false

    var emulatingButtonFlags: Int = 0
    var hasSelect: Boolean = false
    var hasMode: Boolean = false
    var hasPaddles: Boolean = false
    var hasShare: Boolean = false
    var needsClickpadEmulation: Boolean = false

    // Used for OUYA bumper state tracking since they force all buttons
    // up when the OUYA button goes down. We watch the last time we get
    // a bumper up and compare that to our maximum delay when we receive
    // a Start button press to see if we should activate one of our
    // emulated button combos.
    var lastLbUpTime: Long = 0
    var lastRbUpTime: Long = 0

    // Note: startDownTime is inherited from GenericControllerContext

    val batteryStateUpdateRunnable: Runnable = object : Runnable {
        override fun run() {
            handler.rumbleManager.sendControllerBatteryPacket(this@InputDeviceContext)

            // Requeue the callback
            handler.backgroundThreadHandler.postDelayed(this, ControllerHandler.BATTERY_RECHECK_INTERVAL_MS.toLong())
        }
    }

    val enableSensorRunnable: Runnable = Runnable {
        // Turn back on any sensors that should be reporting but are currently unregistered
        if (accelReportRateHz.toInt() != 0 && accelListener == null) {
            handler.handleSetMotionEventState(controllerNumber, MoonBridge.LI_MOTION_TYPE_ACCEL, accelReportRateHz)
        }
        if (gyroReportRateHz.toInt() != 0 && gyroListener == null) {
            handler.handleSetMotionEventState(controllerNumber, MoonBridge.LI_MOTION_TYPE_GYRO, gyroReportRateHz)
        }
    }

    override fun destroy() {
        super.destroy()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibratorManager != null) {
            vibratorManager!!.cancel()
        } else if (vibrator != null) {
            vibrator!!.cancel()
        }

        handler.backgroundThreadHandler.removeCallbacks(enableSensorRunnable)

        if (gyroListener != null) {
            sensorManager?.unregisterListener(gyroListener)
        }
        if (accelListener != null) {
            sensorManager?.unregisterListener(accelListener)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lightsSession?.close()
        }

        handler.backgroundThreadHandler.removeCallbacks(batteryStateUpdateRunnable)
    }

    @TargetApi(31)
    override fun sendControllerArrival(): Int {
        val inputDev = inputDevice ?: return -1
        val type: Byte = when (inputDev.vendorId) {
            0x045e -> MoonBridge.LI_CTYPE_XBOX // Microsoft
            0x054c -> MoonBridge.LI_CTYPE_PS   // Sony
            0x057e -> MoonBridge.LI_CTYPE_NINTENDO // Nintendo
            else -> MoonBridge.guessControllerType(inputDev.vendorId, inputDev.productId)
        }

        var supportedButtonFlags = 0
        for ((key, value) in ControllerHandler.ANDROID_TO_LI_BUTTON_MAP) {
            if (inputDev.hasKeys(key)[0]) {
                supportedButtonFlags = supportedButtonFlags or value
            }
        }

        // Add non-standard button flags that may not be mapped in the Android kl file
        if (hasPaddles) {
            supportedButtonFlags = supportedButtonFlags or
                    ControllerPacket.PADDLE1_FLAG or
                    ControllerPacket.PADDLE2_FLAG or
                    ControllerPacket.PADDLE3_FLAG or
                    ControllerPacket.PADDLE4_FLAG
        }
        if (hasShare) {
            supportedButtonFlags = supportedButtonFlags or ControllerPacket.MISC_FLAG
        }

        if (ControllerHandler.getMotionRangeForJoystickAxis(inputDev, MotionEvent.AXIS_HAT_X) != null) {
            supportedButtonFlags = supportedButtonFlags or ControllerPacket.LEFT_FLAG or ControllerPacket.RIGHT_FLAG
        }
        if (ControllerHandler.getMotionRangeForJoystickAxis(inputDev, MotionEvent.AXIS_HAT_Y) != null) {
            supportedButtonFlags = supportedButtonFlags or ControllerPacket.UP_FLAG or ControllerPacket.DOWN_FLAG
        }

        var capabilities: Short = 0

        // Most of the advanced InputDevice capabilities came in Android S
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (quadVibrators) {
                capabilities = (capabilities.toInt() or MoonBridge.LI_CCAP_RUMBLE.toInt() or MoonBridge.LI_CCAP_TRIGGER_RUMBLE.toInt()).toShort()
            } else if (vibratorManager != null || vibrator != null) {
                capabilities = (capabilities.toInt() or MoonBridge.LI_CCAP_RUMBLE.toInt()).toShort()
            }

            // Calling InputDevice.getBatteryState() to see if a battery is present
            // performs a Binder transaction that can cause ANRs on some devices.
            // To avoid this, we will just claim we can report battery state for all
            // external gamepad devices on Android S. If it turns out that no battery
            // is actually present, we'll just report unknown battery state to the host.
            if (external) {
                capabilities = (capabilities.toInt() or MoonBridge.LI_CCAP_BATTERY_STATE.toInt()).toShort()
            }

            // Light.hasRgbControl() was totally broken prior to Android 14.
            // It always returned true because LIGHT_CAPABILITY_RGB was defined as 0,
            // so we will just guess RGB is supported if it's a PlayStation controller.
            if (hasRgbLed && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE || type == MoonBridge.LI_CTYPE_PS)) {
                capabilities = (capabilities.toInt() or MoonBridge.LI_CCAP_RGB_LED.toInt()).toShort()
            }
        }

        // Report analog triggers if we have at least one trigger axis
        if (leftTriggerAxis != -1 || rightTriggerAxis != -1) {
            capabilities = (capabilities.toInt() or MoonBridge.LI_CCAP_ANALOG_TRIGGERS.toInt()).toShort()
        }

        // Report sensors if the input device has them or we're using built-in sensors for a built-in controller
        if (sensorManager != null && sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            capabilities = (capabilities.toInt() or MoonBridge.LI_CCAP_ACCEL.toInt()).toShort()
        }
        if (sensorManager != null && sensorManager!!.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
            capabilities = (capabilities.toInt() or MoonBridge.LI_CCAP_GYRO.toInt()).toShort()
        }

        val emulatingMotionSensors = type != MoonBridge.LI_CTYPE_PS && sensorManager != null
        val reportedType: Byte
        if (emulatingMotionSensors) {
            // Override the detected controller type if we're emulating motion sensors on an Xbox controller
            reportedType = MoonBridge.LI_CTYPE_UNKNOWN

            // Remember that we should enable the clickpad emulation combo (Select+LB) for this device
            needsClickpadEmulation = true
        } else {
            // Report the true type to the host PC if we're not emulating motion sensors
            reportedType = type
        }

        // We can perform basic rumble with any vibrator
        if (vibrator != null) {
            capabilities = (capabilities.toInt() or MoonBridge.LI_CCAP_RUMBLE.toInt()).toShort()
        }

        // Shield controllers use special APIs for rumble and battery state
        if (handler.sceManager.isRecognizedDevice(inputDev)) {
            capabilities = (capabilities.toInt() or MoonBridge.LI_CCAP_RUMBLE.toInt() or MoonBridge.LI_CCAP_BATTERY_STATE.toInt()).toShort()
        }

        if ((inputDev.sources and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD) {
            capabilities = (capabilities.toInt() or MoonBridge.LI_CCAP_TOUCHPAD.toInt()).toShort()

            // Use the platform API or internal heuristics to determine if this has a clickpad
            if (ControllerHandler.hasButtonUnderTouchpad(inputDev, type)) {
                supportedButtonFlags = supportedButtonFlags or ControllerPacket.TOUCHPAD_FLAG
            }
        }

        val result = handler.sendControllerArrivalEvent(
            controllerNumber.toByte(), reportedType, supportedButtonFlags, capabilities
        )
        if (result != 0) {
            return result
        }

        if (emulatingMotionSensors) {
            Toast.makeText(
                handler.activityContext,
                handler.activityContext.resources.getText(R.string.toast_controller_type_changed),
                Toast.LENGTH_LONG
            ).show()
        }

        // After reporting arrival to the host, send initial battery state and begin monitoring
        handler.backgroundThreadHandler.post(batteryStateUpdateRunnable)
        return result
    }

    fun migrateContext(oldContext: InputDeviceContext) {
        // Take ownership of the sensor and light sessions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            this.lightsSession = oldContext.lightsSession
            oldContext.lightsSession = null
        }
        this.gyroReportRateHz = oldContext.gyroReportRateHz
        this.accelReportRateHz = oldContext.accelReportRateHz
        this.lowFreqMotor = oldContext.lowFreqMotor
        this.highFreqMotor = oldContext.highFreqMotor
        this.leftTriggerMotor = oldContext.leftTriggerMotor
        this.rightTriggerMotor = oldContext.rightTriggerMotor

        // Don't release the controller number, because we will carry it over if it is present.
        // We also want to make sure the change is invisible to the host PC to avoid an add/remove
        // cycle for the gamepad which may break some games.
        oldContext.destroy()

        // Copy over existing controller number state
        this.assignedControllerNumber = oldContext.assignedControllerNumber
        this.reservedControllerNumber = oldContext.reservedControllerNumber
        if (oldContext.controllerArrival.isReported) {
            this.controllerArrival.markReported()
        }
        this.controllerNumber = oldContext.controllerNumber

        // We may have set this device to use the built-in sensor manager. If so, do that again.
        if (oldContext.sensorManager === handler.deviceSensorManager) {
            this.sensorManager = handler.deviceSensorManager
        }

        // Copy state initialized in reportControllerArrival()
        this.needsClickpadEmulation = oldContext.needsClickpadEmulation

        // Re-enable sensors on the new context
        enableSensors()

        // Resume battery polling only if the host already knows this controller.
        // A pending arrival starts polling after its first successful retry.
        if (controllerArrival.isReported) {
            handler.backgroundThreadHandler.post(batteryStateUpdateRunnable)
        }
    }

    fun disableSensors() {
        // Stop any pending enablement
        handler.backgroundThreadHandler.removeCallbacks(enableSensorRunnable)

        // Unregister all sensor listeners
        if (gyroListener != null) {
            sensorManager?.unregisterListener(gyroListener)
            gyroListener = null

            // Send a gyro event to ensure the virtual controller is stationary
            handler.conn.sendControllerMotionEvent(controllerNumber.toByte(), MoonBridge.LI_MOTION_TYPE_GYRO, 0f, 0f, 0f)
        }
        if (accelListener != null) {
            sensorManager?.unregisterListener(accelListener)
            accelListener = null

            // We leave the acceleration as-is to preserve the attitude of the controller
        }
    }

    fun enableSensors() {
        // We allow 1 second for the input device to settle before re-enabling sensors.
        // Pointer capture can cause the input device to change, which can cause
        // InputDeviceSensorManager to crash due to missing null checks on the InputDevice.
        handler.backgroundThreadHandler.postDelayed(enableSensorRunnable, 1000)
    }
}

// =================================================================================
// UsbDeviceContext
// =================================================================================

class UsbDeviceContext(handler: ControllerHandler) : GenericControllerContext(handler) {
    var device: AbstractController? = null
    internal val menuKeyDownTimes = ConcurrentHashMap<Int, Long>()
    internal val shortcutState = UsbControllerShortcutStateMachine()
    internal val shortcutLongPressRunnable = Runnable {
        handler.onUsbShortcutLongPress(this)
    }

    override fun destroy() {
        menuKeyDownTimes.clear()
        handler.releaseUsbShortcutState(this)
        super.destroy()
    }

    override fun onGameMenuDismissed() {
        menuKeyDownTimes.clear()
        shortcutState.onGameMenuUnavailable()
    }

    override fun sendControllerArrival(): Int {
        val dev = device ?: return -1
        return handler.sendControllerArrivalEvent(
            controllerNumber.toByte(), dev.type, dev.supportedButtonFlags, dev.capabilities
        )
    }
}
