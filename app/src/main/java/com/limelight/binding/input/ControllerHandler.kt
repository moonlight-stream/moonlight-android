@file:Suppress("DEPRECATION")
package com.limelight.binding.input

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.input.InputManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.os.Vibrator
import android.os.VibratorManager
import android.util.SparseArray
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent

import com.limelight.LimeLog
import com.limelight.binding.input.driver.AbstractController
import com.limelight.binding.input.driver.UsbDriverListener
import com.limelight.binding.input.driver.UsbDriverService
import com.limelight.binding.input.haptics.ControllerHapticsCoordinator
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.ui.GameGestures
import com.limelight.utils.Vector2d

import org.cgutman.shieldcontrollerextensions.SceManager

import java.lang.reflect.InvocationTargetException

class ControllerHandler(
    internal val activityContext: Activity,
    internal val conn: NvConnection,
    private val gestures: GameGestures,
    internal val prefConfig: PreferenceConfiguration,
    private val onTogglePerformanceOverlay: () -> Unit = {}
) : InputManager.InputDeviceListener, UsbDriverListener {

    companion object {
        private const val MAXIMUM_BUMPER_UP_DELAY_MS = 100

        const val START_DOWN_TIME_MOUSE_MODE_MS = 750

        const val MINIMUM_BUTTON_DOWN_TIME_MS = 25

        const val PERFORMANCE_OVERLAY_COMBO_FLAGS: Int = ControllerPacket.BACK_FLAG or
            ControllerPacket.LB_FLAG or ControllerPacket.RB_FLAG or ControllerPacket.X_FLAG

        private const val EMULATING_SPECIAL = 0x1
        private const val EMULATING_SELECT = 0x2
        private const val EMULATING_TOUCHPAD = 0x4

        internal const val MAX_GAMEPADS: Short = 16 // Limited by bits in activeGamepadMask

        const val BATTERY_RECHECK_INTERVAL_MS = 120 * 1000

        const val GYRO_ACTIVATION_ALWAYS = ControllerGyroManager.GYRO_ACTIVATION_ALWAYS

        val ANDROID_TO_LI_BUTTON_MAP: Map<Int, Int> = mapOf(
            KeyEvent.KEYCODE_BUTTON_A to ControllerPacket.A_FLAG,
            KeyEvent.KEYCODE_BUTTON_B to ControllerPacket.B_FLAG,
            KeyEvent.KEYCODE_BUTTON_X to ControllerPacket.X_FLAG,
            KeyEvent.KEYCODE_BUTTON_Y to ControllerPacket.Y_FLAG,
            KeyEvent.KEYCODE_DPAD_UP to ControllerPacket.UP_FLAG,
            KeyEvent.KEYCODE_DPAD_DOWN to ControllerPacket.DOWN_FLAG,
            KeyEvent.KEYCODE_DPAD_LEFT to ControllerPacket.LEFT_FLAG,
            KeyEvent.KEYCODE_DPAD_RIGHT to ControllerPacket.RIGHT_FLAG,
            KeyEvent.KEYCODE_DPAD_UP_LEFT to (ControllerPacket.UP_FLAG or ControllerPacket.LEFT_FLAG),
            KeyEvent.KEYCODE_DPAD_UP_RIGHT to (ControllerPacket.UP_FLAG or ControllerPacket.RIGHT_FLAG),
            KeyEvent.KEYCODE_DPAD_DOWN_LEFT to (ControllerPacket.DOWN_FLAG or ControllerPacket.LEFT_FLAG),
            KeyEvent.KEYCODE_DPAD_DOWN_RIGHT to (ControllerPacket.DOWN_FLAG or ControllerPacket.RIGHT_FLAG),
            KeyEvent.KEYCODE_BUTTON_L1 to ControllerPacket.LB_FLAG,
            KeyEvent.KEYCODE_BUTTON_R1 to ControllerPacket.RB_FLAG,
            KeyEvent.KEYCODE_BUTTON_THUMBL to ControllerPacket.LS_CLK_FLAG,
            KeyEvent.KEYCODE_BUTTON_THUMBR to ControllerPacket.RS_CLK_FLAG,
            KeyEvent.KEYCODE_BUTTON_START to ControllerPacket.PLAY_FLAG,
            KeyEvent.KEYCODE_MENU to ControllerPacket.PLAY_FLAG,
            KeyEvent.KEYCODE_BUTTON_SELECT to ControllerPacket.BACK_FLAG,
            KeyEvent.KEYCODE_BACK to ControllerPacket.BACK_FLAG,
            KeyEvent.KEYCODE_BUTTON_MODE to ControllerPacket.SPECIAL_BUTTON_FLAG,

            // This is the Xbox Series X Share button
            KeyEvent.KEYCODE_MEDIA_RECORD to ControllerPacket.MISC_FLAG,

            // This is a weird one, but it's what Android does prior to 4.10 kernels
            // where DualShock/DualSense touchpads weren't mapped as separate devices.
            // https://android.googlesource.com/platform/frameworks/base/+/master/data/keyboards/Vendor_054c_Product_0ce6_fallback.kl
            // https://android.googlesource.com/platform/frameworks/base/+/master/data/keyboards/Vendor_054c_Product_09cc.kl
            KeyEvent.KEYCODE_BUTTON_1 to ControllerPacket.TOUCHPAD_FLAG

            // FIXME: Paddles?
        )

        @JvmStatic
        fun clampFloat(v: Float, min: Float, max: Float): Float {
            return if (v < min) min else if (v > max) max else v
        }

        @JvmStatic
        fun clampShortToStickRange(v: Int): Short {
            if (v > 0x7FFE) return 0x7FFE.toShort()
            if (v < -0x7FFE) return (-0x7FFE).toShort()
            return v.toShort()
        }

        // Treat very small physical stick values as 0 to avoid jitter when summing
        private const val PHYS_EPSILON: Short = 512 // ~1.6% of full scale

        @JvmStatic
        fun denoisePhys(v: Short): Short {
            return if (Math.abs(v.toInt()) <= PHYS_EPSILON) 0 else v
        }

        @JvmStatic
        fun getMotionRangeForJoystickAxis(dev: InputDevice, axis: Int): InputDevice.MotionRange? {
            // First get the axis for SOURCE_JOYSTICK
            var range = dev.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK)
            if (range == null) {
                // Now try the axis for SOURCE_GAMEPAD
                range = dev.getMotionRange(axis, InputDevice.SOURCE_GAMEPAD)
            }
            return range
        }

        @JvmStatic
        fun hasJoystickAxes(device: InputDevice): Boolean {
            return (device.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
                    getMotionRangeForJoystickAxis(device, MotionEvent.AXIS_X) != null &&
                    getMotionRangeForJoystickAxis(device, MotionEvent.AXIS_Y) != null
        }

        @JvmStatic
        fun hasGamepadButtons(device: InputDevice): Boolean {
            return (device.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        }

        @JvmStatic
        fun isGameControllerDevice(device: InputDevice?): Boolean {
            if (device == null) {
                return true
            }

            if (hasJoystickAxes(device) || hasGamepadButtons(device)) {
                // Has real joystick axes or gamepad buttons
                return true
            }

            // HACK for https://issuetracker.google.com/issues/163120692
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                if (device.id == -1) {
                    // This "virtual" device could be input from any of the attached devices.
                    // Look to see if any gamepads are connected.
                    val ids = InputDevice.getDeviceIds()
                    for (id in ids) {
                        val dev = InputDevice.getDevice(id) ?: continue

                        // If there are any gamepad devices connected, we'll
                        // report that this virtual device is a gamepad.
                        if (hasJoystickAxes(dev) || hasGamepadButtons(dev)) {
                            return true
                        }
                    }
                }
            }

            // Otherwise, we'll try anything that claims to be a non-alphabetic keyboard
            return device.keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC
        }

        @JvmStatic
        fun getAttachedControllerMask(context: Context): Short {
            var count = 0
            var mask: Short = 0

            // Count all input devices that are gamepads
            val im = context.getSystemService(Context.INPUT_SERVICE) as InputManager
            for (id in im.inputDeviceIds) {
                val dev = im.getInputDevice(id) ?: continue

                if (hasJoystickAxes(dev)) {
                    LimeLog.info("Counting InputDevice: " + dev.name)
                    mask = (mask.toInt() or (1 shl count++)).toShort()
                }
            }

            // Count all USB devices that match our drivers
            if (PreferenceConfiguration.readPreferences(context).usbDriver) {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
                if (usbManager != null) {
                    for (dev in usbManager.deviceList.values) {
                        // We explicitly check not to claim devices that appear as InputDevices
                        // otherwise we will double count them.
                        if (UsbDriverService.shouldClaimDevice(dev, false) &&
                            !UsbDriverService.isRecognizedInputDevice(dev)
                        ) {
                            LimeLog.info("Counting UsbDevice: " + dev.deviceName)
                            mask = (mask.toInt() or (1 shl count++)).toShort()
                        }
                    }
                }
            }

            if (PreferenceConfiguration.readPreferences(context).onscreenController) {
                LimeLog.info("Counting OSC gamepad")
                mask = (mask.toInt() or 1).toShort()
            }

            LimeLog.info("Enumerated $count gamepads")
            return mask
        }

        @JvmStatic
        fun hasButtonUnderTouchpad(dev: InputDevice, type: Byte): Boolean {
            // It has to have a touchpad to have a button under it
            if ((dev.sources and InputDevice.SOURCE_TOUCHPAD) != InputDevice.SOURCE_TOUCHPAD) {
                return false
            }

            // Landroid/view/InputDevice;->hasButtonUnderPad()Z is blocked after O
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
                try {
                    return dev.javaClass.getMethod("hasButtonUnderPad").invoke(dev) as Boolean
                } catch (e: NoSuchMethodException) {
                    e.printStackTrace()
                } catch (e: IllegalAccessException) {
                    e.printStackTrace()
                } catch (e: InvocationTargetException) {
                    e.printStackTrace()
                } catch (e: ClassCastException) {
                    e.printStackTrace()
                }
            }

            // We can't use the platform API, so we'll have to just guess based on the gamepad type.
            // If this is a PlayStation controller with a touchpad, we know it has a clickpad.
            return type == MoonBridge.LI_CTYPE_PS
        }

        @JvmStatic
        fun isExternal(dev: InputDevice): Boolean {
            // The ASUS Tinker Board inaccurately reports Bluetooth gamepads as internal,
            // causing shouldIgnoreBack() to believe it should pass through back as a
            // navigation event for any attached gamepads.
            if (Build.MODEL == "Tinker Board") {
                return true
            }

            val deviceName = dev.name
            if (deviceName.contains("gpio") || // This is the back button on Shield portable consoles
                deviceName.contains("joy_key") || // These are the gamepad buttons on the Archos Gamepad 2
                deviceName.contains("keypad") || // These are gamepad buttons on the XPERIA Play
                deviceName.equals("NVIDIA Corporation NVIDIA Controller v01.01", ignoreCase = true) || // Gamepad on Shield Portable
                deviceName.equals("NVIDIA Corporation NVIDIA Controller v01.02", ignoreCase = true) || // Gamepad on Shield Portable (?)
                deviceName.equals("GR0006", ignoreCase = true) // Gamepad on Logitech G Cloud
            ) {
                LimeLog.info(dev.name + " is internal by hardcoded mapping")
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Landroid/view/InputDevice;->isExternal()Z is officially public on Android Q
                return dev.isExternal
            } else {
                try {
                    // Landroid/view/InputDevice;->isExternal()Z is on the light graylist in Android P
                    return dev.javaClass.getMethod("isExternal").invoke(dev) as Boolean
                } catch (e: NoSuchMethodException) {
                    e.printStackTrace()
                } catch (e: IllegalAccessException) {
                    e.printStackTrace()
                } catch (e: InvocationTargetException) {
                    e.printStackTrace()
                } catch (e: ClassCastException) {
                    e.printStackTrace()
                }
            }

            // Answer true if we don't know
            return true
        }
    } // companion object

    // ========== Instance Fields ==========

    private val inputVector = Vector2d()

    internal val inputDeviceContexts = SparseArray<InputDeviceContext>()
    internal val usbDeviceContexts = SparseArray<UsbDeviceContext>()

    internal val deviceVibrator: Vibrator
    private val deviceVibratorManager: VibratorManager?
    internal val deviceSensorManager: SensorManager?
    private val inputManager: InputManager
    internal val sceManager: SceManager
    internal val mainThreadHandler: Handler
    private val backgroundHandlerThread: HandlerThread
    internal val backgroundThreadHandler: Handler
    private var hasGameController = false
    internal var stopped = false

    private var currentControllers: Short = 0
    private var initialControllers: Short = 0
    private var usbShortcutHintOwner: UsbDeviceContext? = null

    private val stickDeadzone: Double

    internal val defaultContext: InputDeviceContext

    // --- Managers ---
    internal val gyroManager = ControllerGyroManager(this)
    internal val rumbleManager = ControllerRumbleManager(this)
    private val hapticsCoordinator = ControllerHapticsCoordinator(this)

    private val REMAP_IGNORE = -1
    private val REMAP_CONSUME = -2

    // ========== Constructor ==========

    init {
        deviceVibrator = activityContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        deviceSensorManager = activityContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        inputManager = activityContext.getSystemService(Context.INPUT_SERVICE) as InputManager
        mainThreadHandler = Handler(Looper.getMainLooper())

        // Create a HandlerThread to process battery state updates. These can be slow enough
        // that they lead to ANRs if we do them on the main thread.
        backgroundHandlerThread = HandlerThread("ControllerHandler")
        backgroundHandlerThread.start()
        backgroundThreadHandler = Handler(backgroundHandlerThread.looper)

        deviceVibratorManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activityContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        } else {
            null
        }

        sceManager = SceManager(activityContext)
        sceManager.start()

        var deadzonePercentage = prefConfig.deadzonePercentage

        val ids = InputDevice.getDeviceIds()
        for (id in ids) {
            val dev = InputDevice.getDevice(id) ?: continue
            if ((dev.sources and InputDevice.SOURCE_JOYSTICK) != 0 ||
                (dev.sources and InputDevice.SOURCE_GAMEPAD) != 0
            ) {
                // This looks like a gamepad, but we'll check X and Y to be sure
                if (getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_X) != null &&
                    getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_Y) != null
                ) {
                    // This is a gamepad
                    hasGameController = true
                }
            }
        }

        // 1% is the lowest possible deadzone we support
        if (deadzonePercentage <= 0) {
            deadzonePercentage = 1
        }

        stickDeadzone = deadzonePercentage.toDouble() / 100.0

        // Initialize the default context for events with no device
        defaultContext = InputDeviceContext(this)
        defaultContext.leftStickXAxis = MotionEvent.AXIS_X
        defaultContext.leftStickYAxis = MotionEvent.AXIS_Y
        defaultContext.leftStickDeadzoneRadius = stickDeadzone.toFloat()
        defaultContext.rightStickXAxis = MotionEvent.AXIS_Z
        defaultContext.rightStickYAxis = MotionEvent.AXIS_RZ
        defaultContext.rightStickDeadzoneRadius = stickDeadzone.toFloat()
        defaultContext.leftTriggerAxis = MotionEvent.AXIS_BRAKE
        defaultContext.rightTriggerAxis = MotionEvent.AXIS_GAS
        defaultContext.hatXAxis = MotionEvent.AXIS_HAT_X
        defaultContext.hatYAxis = MotionEvent.AXIS_HAT_Y
        defaultContext.controllerNumber = 0
        defaultContext.assignedControllerNumber = true
        defaultContext.controllerArrival.markReported()
        defaultContext.external = false

        // Some devices (GPD XD) have a back button which sends input events
        // with device ID == 0. This hits the default context which would normally
        // consume these. Instead, let's ignore them since that's probably the
        // most likely case.
        defaultContext.ignoreBack = true

        // Get the initially attached set of gamepads. As each gamepad receives
        // its initial InputEvent, we will move these from this set onto the
        // currentControllers set which will allow them to properly unplug
        // if they are removed.
        initialControllers = getAttachedControllerMask(activityContext)

        // Register ourselves for input device notifications
        inputManager.registerInputDeviceListener(this, null)
        for (deviceId in InputDevice.getDeviceIds()) {
            registerRumbleContextIfNeeded(deviceId)
        }
        hapticsCoordinator.refreshPrimaryController()
    }

    // ========== InputDeviceListener callbacks ==========

    override fun onInputDeviceAdded(deviceId: Int) {
        registerRumbleContextIfNeeded(deviceId)
        hapticsCoordinator.refreshPrimaryController()
    }

    private fun registerRumbleContextIfNeeded(deviceId: Int) {
        if (inputDeviceContexts.get(deviceId) != null) return
        val device = InputDevice.getDevice(deviceId) ?: return
        if (!isExternal(device)) return
        if ((device.sources and InputDevice.SOURCE_GAMEPAD) == 0 &&
            (device.sources and InputDevice.SOURCE_JOYSTICK) == 0
        ) {
            return
        }
        if (getMotionRangeForJoystickAxis(device, MotionEvent.AXIS_X) == null ||
            getMotionRangeForJoystickAxis(device, MotionEvent.AXIS_Y) == null
        ) {
            return
        }

        val context = createInputDeviceContextForDevice(device)
        if (hapticsCoordinator.hasRumbleCapability(context)) {
            inputDeviceContexts.put(deviceId, context)
            hapticsCoordinator.onSinkChanged(context.controllerNumber)
        }
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        val context = inputDeviceContexts.get(deviceId)
        if (context != null) {
            LimeLog.info("Removed controller: " + context.name + " (" + deviceId + ")")
            releaseControllerNumber(context)
            context.destroy()
            inputDeviceContexts.remove(deviceId)
            hapticsCoordinator.refreshPrimaryController()
            hapticsCoordinator.clearControllerIfUnavailable(context.controllerNumber)

            // 如果陀螺仪鼠标模式开着，手柄断开后重新在 defaultContext 上注册手机传感器
            if (prefConfig.gyroToMouse) {
                gyroManager.registerDeviceGyroForDefaultContext(true)
            }
        }
    }

    // This can happen when gaining/losing input focus with some devices.
    // Input devices that have a trackpad may gain/lose AXIS_RELATIVE_X/Y.
    override fun onInputDeviceChanged(deviceId: Int) {
        val device = InputDevice.getDevice(deviceId) ?: return

        // If we don't have a context for this device, we don't need to update anything
        val existingContext = inputDeviceContexts.get(deviceId) ?: return

        LimeLog.info("Device changed: " + existingContext.name + " (" + deviceId + ")")

        // Migrate the existing context into this new one by moving any stateful elements
        val newContext = createInputDeviceContextForDevice(device)
        newContext.migrateContext(existingContext)
        inputDeviceContexts.put(deviceId, newContext)
        hapticsCoordinator.refreshPrimaryController()
        hapticsCoordinator.onSinkChanged(newContext.controllerNumber)
        hapticsCoordinator.clearControllerIfUnavailable(existingContext.controllerNumber)
    }

    // ========== Lifecycle ==========

    fun stop() {
        if (stopped) {
            return
        }

        // Flush rumble while device contexts and HOST fallback paths are still available.
        hapticsCoordinator.stop()

        // Stop new device contexts from being created or used
        stopped = true

        // Unregister our input device callbacks
        inputManager.unregisterInputDeviceListener(this)

        for (i in 0 until inputDeviceContexts.size()) {
            inputDeviceContexts.valueAt(i).destroy()
        }

        for (i in 0 until usbDeviceContexts.size()) {
            usbDeviceContexts.valueAt(i).destroy()
        }

        // 清理 defaultContext 上可能注册的手机陀螺仪传感器
        gyroManager.registerDeviceGyroForDefaultContext(false)
        defaultContext.destroy()

        deviceVibrator.cancel()
    }

    fun destroy() {
        if (!stopped) {
            stop()
        }

        sceManager.stop()
        backgroundHandlerThread.quitSafely()
    }

    internal fun onUsbShortcutLongPress(context: UsbDeviceContext) {
        if (stopped || usbShortcutHintOwner?.let { it !== context } == true) {
            return
        }
        handleUsbShortcutUpdate(
            context,
            context.shortcutState.onLongPressTimeout(
                android.os.SystemClock.uptimeMillis(),
                prefConfig.enableStartKeyMenu
            )
        )
    }

    internal fun releaseUsbShortcutState(context: UsbDeviceContext) {
        mainThreadHandler.removeCallbacks(context.shortcutLongPressRunnable)
        val update = context.shortcutState.reset()
        handleUsbShortcutUpdate(context, update)
    }

    fun disableSensors() {
        for (i in 0 until inputDeviceContexts.size()) {
            inputDeviceContexts.valueAt(i).disableSensors()
        }
    }

    fun enableSensors() {
        if (stopped) {
            return
        }

        for (i in 0 until inputDeviceContexts.size()) {
            inputDeviceContexts.valueAt(i).enableSensors()
        }

        // 如果陀螺仪模拟右摇杆功能已启用，需要重新激活
        gyroManager.onSensorsReenabled()
    }

    // ========== Controller Number Management ==========

    private fun releaseControllerNumber(context: GenericControllerContext) {
        // If we reserved a controller number, remove that reservation
        if (context.reservedControllerNumber) {
            LimeLog.info("Controller number " + context.controllerNumber + " is now available")
            currentControllers = (currentControllers.toInt() and (1 shl context.controllerNumber.toInt()).inv()).toShort()
        }

        // If this device was reported to the host, zero the values before removing.
        // We must do this after clearing the currentControllers entry so this
        // causes the device to be removed on the server PC.
        //
        // An assigned controller whose arrival is still pending has never sent a
        // normal input packet. Sending a removal packet for it can create a legacy
        // Xbox controller on hosts that keep controller 0 active.
        if (context.controllerArrival.isReported) {
            conn.sendControllerInput(
                context.controllerNumber, getActiveControllerMask(),
                0,
                0.toByte(), 0.toByte(),
                0.toShort(), 0.toShort(),
                0.toShort(), 0.toShort()
            )
        }
    }

    private fun isAssociatedJoystick(originalDevice: InputDevice?, possibleAssociatedJoystick: InputDevice?): Boolean {
        if (possibleAssociatedJoystick == null) {
            return false
        }

        // This can't be an associated joystick if it's not a joystick
        if ((possibleAssociatedJoystick.sources and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) {
            return false
        }

        // Make sure the device names *don't* match in order to prevent us from accidentally matching
        // on another of the exact same device.
        if (possibleAssociatedJoystick.name == originalDevice?.name) {
            return false
        }

        // Make sure the descriptor matches. This can match in cases where two of the exact same
        // input device are connected, so we perform the name check to exclude that case.
        if (possibleAssociatedJoystick.descriptor != originalDevice?.descriptor) {
            return false
        }

        return true
    }

    // Called before sending input but after we've determined that this
    // is definitely a controller (not a keyboard, mouse, or something else)
    private fun assignControllerNumberIfNeeded(context: GenericControllerContext): Boolean {
        if (context.assignedControllerNumber) {
            return false
        }

        if (context is InputDeviceContext) {
            LimeLog.info(context.name + " (" + context.id + ") needs a controller number assigned")
            if (!context.external) {
                LimeLog.info("Built-in buttons hardcoded as controller 0")
                context.controllerNumber = 0
            } else if (prefConfig.multiController && context.hasJoystickAxes) {
                context.controllerNumber = 0

                LimeLog.info("Reserving the next available controller number")
                for (i in 0 until MAX_GAMEPADS) {
                    if ((currentControllers.toInt() and (1 shl i)) == 0) {
                        // Found an unused controller value
                        currentControllers = (currentControllers.toInt() or (1 shl i)).toShort()

                        // Take this value out of the initial gamepad set
                        initialControllers = (initialControllers.toInt() and (1 shl i).inv()).toShort()

                        context.controllerNumber = i.toShort()
                        context.reservedControllerNumber = true
                        break
                    }
                }
            } else if (!context.hasJoystickAxes) {
                // If this device doesn't have joystick axes, it may be an input device associated
                // with another joystick (like a PS4 touchpad). We'll propagate that joystick's
                // controller number to this associated device.

                context.controllerNumber = 0

                // For the DS4 case, the associated joystick is the next device after the touchpad.
                // We'll try the opposite case too, just to be a little future-proof.
                var associatedDevice = InputDevice.getDevice(context.id + 1)
                if (!isAssociatedJoystick(context.inputDevice, associatedDevice)) {
                    associatedDevice = InputDevice.getDevice(context.id - 1)
                    if (!isAssociatedJoystick(context.inputDevice, associatedDevice)) {
                        LimeLog.info("No associated joystick device found")
                        associatedDevice = null
                    }
                }

                if (associatedDevice != null) {
                    var associatedDeviceContext = inputDeviceContexts.get(associatedDevice.id)

                    // Create a new context for the associated device if one doesn't exist
                    if (associatedDeviceContext == null) {
                        associatedDeviceContext = createInputDeviceContextForDevice(associatedDevice)
                        inputDeviceContexts.put(associatedDevice.id, associatedDeviceContext)
                    }

                    // Assign a controller number for the associated device if one isn't assigned
                    if (!associatedDeviceContext.assignedControllerNumber) {
                        assignControllerNumberIfNeeded(associatedDeviceContext)
                    }

                    // Propagate the associated controller number
                    context.controllerNumber = associatedDeviceContext.controllerNumber

                    LimeLog.info("Propagated controller number from " + associatedDeviceContext.name)
                }
            } else {
                LimeLog.info("Not reserving a controller number")
                context.controllerNumber = 0
            }

            // If the gamepad doesn't have motion sensors, use the on-device sensors as a fallback for player 1
            if (prefConfig.gamepadMotionSensorsFallbackToDevice && context.controllerNumber.toInt() == 0 && context.sensorManager == null) {
                context.sensorManager = deviceSensorManager
            }
        } else {
            if (prefConfig.multiController) {
                context.controllerNumber = 0

                LimeLog.info("Reserving the next available controller number")
                for (i in 0 until MAX_GAMEPADS) {
                    if ((currentControllers.toInt() and (1 shl i)) == 0) {
                        // Found an unused controller value
                        currentControllers = (currentControllers.toInt() or (1 shl i)).toShort()

                        // Take this value out of the initial gamepad set
                        initialControllers = (initialControllers.toInt() and (1 shl i).inv()).toShort()

                        context.controllerNumber = i.toShort()
                        context.reservedControllerNumber = true
                        break
                    }
                }
            } else {
                LimeLog.info("Not reserving a controller number")
                context.controllerNumber = 0
            }
        }

        LimeLog.info("Assigned as controller " + context.controllerNumber)
        context.assignedControllerNumber = true
        hapticsCoordinator.refreshPrimaryController()
        hapticsCoordinator.onSinkChanged(context.controllerNumber)

        // Report attributes of this new controller to the host
        reportControllerArrival(context)
        return true
    }

    private fun reportControllerArrival(context: GenericControllerContext): Boolean =
        synchronized(context.controllerArrival) {
            if (context.controllerArrival.isReported) {
                return@synchronized true
            }

            context.controllerArrival.recordAttempt(context.sendControllerArrival())
        }

    internal fun retryPendingControllerArrivals() {
        mainThreadHandler.post {
            if (stopped) {
                return@post
            }

            for (i in 0 until inputDeviceContexts.size()) {
                val context = inputDeviceContexts.valueAt(i)
                if (context.assignedControllerNumber &&
                    !context.controllerArrival.isReported &&
                    reportControllerArrival(context)
                ) {
                    sendControllerInputPacket(context)
                }
            }

            // USB contexts are owned by their driver threads and continuously
            // report state, so they retry through sendControllerInputPacket().
            // Don't iterate their SparseArray from the main thread here.
        }
    }

    // ========== Device Context Creation ==========

    private fun createUsbDeviceContextForDevice(device: AbstractController): UsbDeviceContext {
        val context = UsbDeviceContext(this)

        context.id = device.getControllerId()
        context.device = device
        context.external = true

        context.vendorId = device.getVendorId()
        context.productId = device.getProductId()

        context.leftStickDeadzoneRadius = stickDeadzone.toFloat()
        context.rightStickDeadzoneRadius = stickDeadzone.toFloat()
        context.triggerDeadzone = 0.13f

        return context
    }

    private fun shouldIgnoreBack(dev: InputDevice): Boolean {
        val devName = dev.name

        // The Serval has a Select button but the framework doesn't
        // know about that because it uses a non-standard scancode.
        if (devName.contains("Razer Serval")) {
            return true
        }

        // Classify this device as a remote by name if it has no joystick axes
        if (!hasJoystickAxes(dev) && devName.lowercase().contains("remote")) {
            return true
        }

        // Otherwise, dynamically try to determine whether we should allow this
        // back button to function for navigation.
        //
        // First, check if this is an internal device we're being called on.
        if (!isExternal(dev)) {
            val im = activityContext.getSystemService(Context.INPUT_SERVICE) as InputManager

            var foundInternalGamepad = false
            var foundInternalSelect = false
            for (id in im.inputDeviceIds) {
                val currentDev = im.getInputDevice(id)

                // Ignore external devices
                if (currentDev == null || isExternal(currentDev)) {
                    continue
                }

                // Note that we are explicitly NOT excluding the current device we're examining here,
                // since the other gamepad buttons may be on our current device and that's fine.
                if (currentDev.hasKeys(KeyEvent.KEYCODE_BUTTON_SELECT)[0]) {
                    foundInternalSelect = true
                }

                // We don't check KEYCODE_BUTTON_A here, since the Shield Android TV has a
                // virtual mouse device that claims to have KEYCODE_BUTTON_A. Instead, we rely
                // on the SOURCE_GAMEPAD flag to be set on gamepad devices.
                if (hasGamepadButtons(currentDev)) {
                    foundInternalGamepad = true
                }
            }

            // Allow the back button to function for navigation if we either:
            // a) have no internal gamepad (most phones)
            // b) have an internal gamepad but also have an internal select button (GPD XD)
            // but not:
            // c) have an internal gamepad but no internal select button (NVIDIA SHIELD Portable)
            return !foundInternalGamepad || foundInternalSelect
        } else {
            // For external devices, we want to pass through the back button if the device
            // has no gamepad axes or gamepad buttons.
            return !hasJoystickAxes(dev) && !hasGamepadButtons(dev)
        }
    }

    private fun createInputDeviceContextForDevice(dev: InputDevice): InputDeviceContext {
        val context = InputDeviceContext(this)
        val devName = dev.name

        LimeLog.info("Creating controller context for device: $devName")
        LimeLog.info("Vendor ID: " + dev.vendorId)
        LimeLog.info("Product ID: " + dev.productId)
        LimeLog.info(dev.toString())

        context.inputDevice = dev
        context.name = devName
        context.id = dev.id
        context.external = isExternal(dev)

        context.vendorId = dev.vendorId
        context.productId = dev.productId

        // These aren't always present in the Android key layout files, so they won't show up
        // in our normal InputDevice.hasKeys() probing.
        context.hasPaddles = MoonBridge.guessControllerHasPaddles(context.vendorId, context.productId)
        context.hasShare = MoonBridge.guessControllerHasShareButton(context.vendorId, context.productId)

        // Try to use the InputDevice's associated vibrators first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && rumbleManager.hasQuadAmplitudeControlledRumbleVibrators(dev.vibratorManager)) {
            context.vibratorManager = dev.vibratorManager
            context.quadVibrators = true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && rumbleManager.hasDualAmplitudeControlledRumbleVibrators(dev.vibratorManager)) {
            context.vibratorManager = dev.vibratorManager
            context.quadVibrators = false
        } else if (dev.vibrator.hasVibrator()) {
            context.vibrator = dev.vibrator
        } else if (!context.external) {
            // If this is an internal controller, try to use the device's vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && deviceVibratorManager != null &&
                rumbleManager.hasQuadAmplitudeControlledRumbleVibrators(deviceVibratorManager)
            ) {
                context.vibratorManager = deviceVibratorManager
                context.quadVibrators = true
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && deviceVibratorManager != null &&
                rumbleManager.hasDualAmplitudeControlledRumbleVibrators(deviceVibratorManager)
            ) {
                context.vibratorManager = deviceVibratorManager
                context.quadVibrators = false
            } else if (deviceVibrator.hasVibrator()) {
                context.vibrator = deviceVibrator
            }
        }

        // On Android 12, we can try to use the InputDevice's sensors. This may not work if the
        // Linux kernel version doesn't have motion sensor support, which is common for third-party
        // gamepads.
        //
        // Android 12 has a bug that causes InputDeviceSensorManager to cause a NPE on a background
        // thread due to bad error checking in InputListener callbacks. InputDeviceSensorManager is
        // created upon the first call to InputDevice.getSensorManager(), so we avoid calling this
        // on Android 12 unless we have a gamepad that could plausibly have motion sensors.
        // https://cs.android.com/android/_/android/platform/frameworks/base/+/8970010a5e9f3dc5c069f56b4147552accfcbbeb
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
                    (Build.VERSION.SDK_INT == Build.VERSION_CODES.S &&
                            (context.vendorId == 0x054c || context.vendorId == 0x057e))) && // Sony or Nintendo
            prefConfig.gamepadMotionSensors
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (dev.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null ||
                    dev.sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
                ) {
                    context.sensorManager = dev.sensorManager
                }
            }
        }

        // Check if this device has a usable RGB LED and cache that result
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (light in dev.lightsManager.lights) {
                if (light.hasRgbControl()) {
                    context.hasRgbLed = true
                    break
                }
            }
        }

        // Detect if the gamepad has Mode and Select buttons according to the Android key layouts.
        // We do this first because other codepaths below may override these defaults.
        val buttons = dev.hasKeys(KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK, 0)
        context.hasMode = buttons[0]
        context.hasSelect = buttons[1] || buttons[2]

        context.touchpadXRange = dev.getMotionRange(MotionEvent.AXIS_X, InputDevice.SOURCE_TOUCHPAD)
        context.touchpadYRange = dev.getMotionRange(MotionEvent.AXIS_Y, InputDevice.SOURCE_TOUCHPAD)
        context.touchpadPressureRange = dev.getMotionRange(MotionEvent.AXIS_PRESSURE, InputDevice.SOURCE_TOUCHPAD)

        context.leftStickXAxis = MotionEvent.AXIS_X
        context.leftStickYAxis = MotionEvent.AXIS_Y
        if (getMotionRangeForJoystickAxis(dev, context.leftStickXAxis) != null &&
            getMotionRangeForJoystickAxis(dev, context.leftStickYAxis) != null
        ) {
            // This is a gamepad
            hasGameController = true
            context.hasJoystickAxes = true
        }

        // This is hack to deal with the Nvidia Shield's modifications that causes the DS4 clickpad
        // to work as a duplicate Select button instead of a unique button we can handle separately.
        context.isDualShockStandaloneTouchpad =
            context.vendorId == 0x054c && // Sony
                    devName.endsWith(" Touchpad") &&
                    dev.sources == (InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_MOUSE)

        val leftTriggerRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_LTRIGGER)
        val rightTriggerRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RTRIGGER)
        val brakeRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_BRAKE)
        val gasRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_GAS)
        val throttleRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_THROTTLE)
        if (leftTriggerRange != null && rightTriggerRange != null) {
            // Some controllers use LTRIGGER and RTRIGGER (like Ouya)
            context.leftTriggerAxis = MotionEvent.AXIS_LTRIGGER
            context.rightTriggerAxis = MotionEvent.AXIS_RTRIGGER
        } else if (brakeRange != null && gasRange != null) {
            // Others use GAS and BRAKE (like Moga)
            context.leftTriggerAxis = MotionEvent.AXIS_BRAKE
            context.rightTriggerAxis = MotionEvent.AXIS_GAS
        } else if (brakeRange != null && throttleRange != null) {
            // Others use THROTTLE and BRAKE (like Xiaomi)
            context.leftTriggerAxis = MotionEvent.AXIS_BRAKE
            context.rightTriggerAxis = MotionEvent.AXIS_THROTTLE
        } else {
            val rxRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RX)
            val ryRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RY)
            if (rxRange != null && ryRange != null && devName != null) {
                if (dev.vendorId == 0x054c) { // Sony
                    if (dev.hasKeys(KeyEvent.KEYCODE_BUTTON_C)[0]) {
                        LimeLog.info("Detected non-standard DualShock 4 mapping")
                        context.isNonStandardDualShock4 = true
                    } else {
                        LimeLog.info("Detected DualShock 4 (Linux standard mapping)")
                        context.usesLinuxGamepadStandardFaceButtons = true
                    }
                }

                if (context.isNonStandardDualShock4) {
                    // The old DS4 driver uses RX and RY for triggers
                    context.leftTriggerAxis = MotionEvent.AXIS_RX
                    context.rightTriggerAxis = MotionEvent.AXIS_RY

                    // DS4 has Select and Mode buttons (possibly mapped non-standard)
                    context.hasSelect = true
                    context.hasMode = true
                } else {
                    // If it's not a non-standard DS4 controller, it's probably an Xbox controller or
                    // other sane controller that uses RX and RY for right stick and Z and RZ for triggers.
                    context.rightStickXAxis = MotionEvent.AXIS_RX
                    context.rightStickYAxis = MotionEvent.AXIS_RY

                    // While it's likely that Z and RZ are triggers, we may have digital trigger buttons
                    // instead. We must check that we actually have Z and RZ axes before assigning them.
                    if (getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_Z) != null &&
                        getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RZ) != null
                    ) {
                        context.leftTriggerAxis = MotionEvent.AXIS_Z
                        context.rightTriggerAxis = MotionEvent.AXIS_RZ
                    }
                }

                // Triggers always idle negative on axes that are centered at zero
                context.triggersIdleNegative = true
            }
        }

        if (context.rightStickXAxis == -1 && context.rightStickYAxis == -1) {
            val zRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_Z)
            val rzRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RZ)

            // Most other controllers use Z and RZ for the right stick
            if (zRange != null && rzRange != null) {
                context.rightStickXAxis = MotionEvent.AXIS_Z
                context.rightStickYAxis = MotionEvent.AXIS_RZ
            } else {
                val rxRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RX)
                val ryRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RY)

                // Try RX and RY now
                if (rxRange != null && ryRange != null) {
                    context.rightStickXAxis = MotionEvent.AXIS_RX
                    context.rightStickYAxis = MotionEvent.AXIS_RY
                }
            }
        }

        // Some devices have "hats" for d-pads
        val hatXRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_HAT_X)
        val hatYRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_HAT_Y)
        if (hatXRange != null && hatYRange != null) {
            context.hatXAxis = MotionEvent.AXIS_HAT_X
            context.hatYAxis = MotionEvent.AXIS_HAT_Y
        }

        if (context.leftStickXAxis != -1 && context.leftStickYAxis != -1) {
            context.leftStickDeadzoneRadius = stickDeadzone.toFloat()
        }

        if (context.rightStickXAxis != -1 && context.rightStickYAxis != -1) {
            context.rightStickDeadzoneRadius = stickDeadzone.toFloat()
        }

        if (context.leftTriggerAxis != -1 && context.rightTriggerAxis != -1) {
            val ltRange = getMotionRangeForJoystickAxis(dev, context.leftTriggerAxis)
            val rtRange = getMotionRangeForJoystickAxis(dev, context.rightTriggerAxis)

            // It's important to have a valid deadzone so controller packet batching works properly
            context.triggerDeadzone = Math.max(Math.abs(ltRange!!.flat), Math.abs(rtRange!!.flat))

            // For triggers without (valid) deadzones, we'll use 13% (around XInput's default)
            if (context.triggerDeadzone < 0.13f || context.triggerDeadzone > 0.30f) {
                context.triggerDeadzone = 0.13f
            }
        }

        // The ADT-1 controller needs a similar fixup to the ASUS Gamepad
        if (dev.vendorId == 0x18d1 && dev.productId == 0x2c40) {
            context.backIsStart = true
            context.modeIsSelect = true
            context.triggerDeadzone = 0.30f
            context.hasSelect = true
            context.hasMode = false
        }

        context.ignoreBack = shouldIgnoreBack(dev)

        if (devName != null) {
            // For the Nexus Player (and probably other ATV devices), we should
            // use the back button as start since it doesn't have a start/menu button
            // on the controller
            if (devName.contains("ASUS Gamepad")) {
                val hasStartKey = dev.hasKeys(KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU, 0)
                if (!hasStartKey[0] && !hasStartKey[1]) {
                    context.backIsStart = true
                    context.modeIsSelect = true
                    context.hasSelect = true
                    context.hasMode = false
                }

                // The ASUS Gamepad has triggers that sit far forward and are prone to false presses
                // so we increase the deadzone on them to minimize this
                context.triggerDeadzone = 0.30f
            }
            // SHIELD controllers will use small stick deadzones
            else if (devName.contains("SHIELD") || devName.contains("NVIDIA Controller")) {
                // The big Nvidia button on the Shield controllers acts like a Search button. It
                // summons the Google Assistant on the Shield TV. On my Pixel 4, it seems to do
                // nothing, so we can hijack it to act like a mode button.
                if (devName.contains("NVIDIA Controller v01.03") || devName.contains("NVIDIA Controller v01.04")) {
                    context.searchIsMode = true
                    context.hasMode = true
                }
            }
            // The Serval has a couple of unknown buttons that are start and select. It also has
            // a back button which we want to ignore since there's already a select button.
            else if (devName.contains("Razer Serval")) {
                context.isServal = true

                // Serval has Select and Mode buttons (possibly mapped non-standard)
                context.hasMode = true
                context.hasSelect = true
            }
            // The Xbox One S Bluetooth controller has some mappings that need fixing up.
            // However, Microsoft released a firmware update with no change to VID/PID
            // or device name that fixed the mappings for Android. Since there's
            // no good way to detect this, we'll use the presence of GAS/BRAKE axes
            // that were added in the latest firmware. If those are present, the only
            // required fixup is ignoring the select button.
            else if (devName == "Xbox Wireless Controller") {
                if (gasRange == null) {
                    context.isNonStandardXboxBtController = true

                    // Xbox One S has Select and Mode buttons (possibly mapped non-standard)
                    context.hasMode = true
                    context.hasSelect = true
                }
            }
        }

        // Thrustmaster Score A gamepad home button reports directly to android as
        // KEY_HOMEPAGE event on another event channel
        if (dev.vendorId == 0x044f && dev.productId == 0xb328) {
            context.hasMode = false
        }

        LimeLog.info("Analog stick deadzone: " + context.leftStickDeadzoneRadius + " " + context.rightStickDeadzoneRadius)
        LimeLog.info("Trigger deadzone: " + context.triggerDeadzone)

        return context
    }

    // ========== Event Context Resolution ==========

    private fun getContextForEvent(event: InputEvent): InputDeviceContext? {
        // Don't return a context if we're stopped
        if (stopped) {
            return null
        } else if (event.deviceId == 0) {
            // Unknown devices use the default context
            return defaultContext
        } else if (event.device == null) {
            // During device removal, sometimes we can get events after the
            // input device has been destroyed. In this case we'll see a
            // != 0 device ID but no device attached.
            return null
        }

        // HACK for https://issuetracker.google.com/issues/163120692
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            if (event.deviceId == -1) {
                return defaultContext
            }
        }

        // Return the existing context if it exists
        var context = inputDeviceContexts.get(event.deviceId)
        if (context != null) {
            return context
        }

        // Otherwise create a new context
        context = createInputDeviceContextForDevice(event.device)
        inputDeviceContexts.put(event.deviceId, context)

        // 如果陀螺仪鼠标模式开着，且新手柄会占用 controllerNumber=0，
        // 需要清理 defaultContext 上的手机传感器，避免双重输入。
        // Only unregister if this device is likely to become controller 0.
        // Internal devices and the first external controller will get controllerNumber=0.
        val likelyController0 = !context.external || (prefConfig.multiController && currentControllers.toInt() == 0)
        if (prefConfig.gyroToMouse && defaultContext.gyroListener != null && likelyController0) {
            gyroManager.registerDeviceGyroForDefaultContext(false)
            LimeLog.info("Physical controller connected, released defaultContext gyro")
        }

        return context
    }

    // ========== Input Helpers ==========

    private fun maxByMagnitude(a: Byte, b: Byte): Byte {
        val absA = Math.abs(a.toInt())
        val absB = Math.abs(b.toInt())
        return if (absA > absB) a else b
    }

    private fun maxByMagnitude(a: Short, b: Short): Short {
        val absA = Math.abs(a.toInt())
        val absB = Math.abs(b.toInt())
        return if (absA > absB) a else b
    }

    internal fun getActiveControllerMask(): Short {
        return if (prefConfig.multiController) {
            (currentControllers.toInt() or initialControllers.toInt() or (if (prefConfig.onscreenController or prefConfig.enableCrownFeatures) 1 else 0)).toShort()
        } else {
            // Only Player 1 is active with multi-controller disabled
            1
        }
    }

    internal fun sendControllerInputPacket(originalContext: GenericControllerContext) {
        val newlyAssigned = assignControllerNumberIfNeeded(originalContext)
        if (!originalContext.controllerArrival.isReported) {
            // assignControllerNumberIfNeeded() already made the first attempt. If
            // the input stream wasn't ready yet, keep the input state but don't let
            // a legacy controller packet create an Xbox device before our metadata.
            if (newlyAssigned || !reportControllerArrival(originalContext)) {
                return
            }
        }
        hapticsCoordinator.noteControllerInput(originalContext)

        // Take the context's controller number and fuse all inputs with the same number
        val controllerNumber = originalContext.controllerNumber
        var inputMap = 0
        var leftTrigger: Byte = 0
        var rightTrigger: Byte = 0
        var leftStickX: Short = 0
        var leftStickY: Short = 0
        var rightStickX: Short = 0
        var rightStickY: Short = 0

        // In order to properly handle controllers that are split into multiple devices,
        // we must aggregate all controllers with the same controller number into a single
        // device before we send it.
        for (i in 0 until inputDeviceContexts.size()) {
            val context: GenericControllerContext = inputDeviceContexts.valueAt(i)
            if (context.assignedControllerNumber &&
                context.controllerNumber == controllerNumber &&
                context.mouseEmulationActive == originalContext.mouseEmulationActive
            ) {
                inputMap = inputMap or context.inputMap
                leftTrigger = maxByMagnitude(leftTrigger, context.leftTrigger)
                rightTrigger = maxByMagnitude(rightTrigger, context.rightTrigger)
                leftStickX = maxByMagnitude(leftStickX, context.leftStickX)
                leftStickY = maxByMagnitude(leftStickY, context.leftStickY)
                rightStickX = maxByMagnitude(rightStickX, context.rightStickX)
                rightStickY = maxByMagnitude(rightStickY, context.rightStickY)
            }
        }
        for (i in 0 until usbDeviceContexts.size()) {
            val context: GenericControllerContext = usbDeviceContexts.valueAt(i)
            if (context.assignedControllerNumber &&
                context.controllerNumber == controllerNumber &&
                context.mouseEmulationActive == originalContext.mouseEmulationActive
            ) {
                inputMap = inputMap or context.inputMap
                leftTrigger = maxByMagnitude(leftTrigger, context.leftTrigger)
                rightTrigger = maxByMagnitude(rightTrigger, context.rightTrigger)
                leftStickX = maxByMagnitude(leftStickX, context.leftStickX)
                leftStickY = maxByMagnitude(leftStickY, context.leftStickY)
                rightStickX = maxByMagnitude(rightStickX, context.rightStickX)
                rightStickY = maxByMagnitude(rightStickY, context.rightStickY)
            }
        }
        if (defaultContext.controllerNumber == controllerNumber) {
            inputMap = inputMap or defaultContext.inputMap
            leftTrigger = maxByMagnitude(leftTrigger, defaultContext.leftTrigger)
            rightTrigger = maxByMagnitude(rightTrigger, defaultContext.rightTrigger)
            leftStickX = maxByMagnitude(leftStickX, defaultContext.leftStickX)
            leftStickY = maxByMagnitude(leftStickY, defaultContext.leftStickY)
            rightStickX = maxByMagnitude(rightStickX, defaultContext.rightStickX)
            rightStickY = maxByMagnitude(rightStickY, defaultContext.rightStickY)
        }

        if (originalContext.mouseEmulationActive) {
            val changedMask = inputMap xor originalContext.mouseEmulationLastInputMap

            val aDown = (inputMap and ControllerPacket.A_FLAG) != 0
            val bDown = (inputMap and ControllerPacket.B_FLAG) != 0

            originalContext.mouseEmulationLastInputMap = inputMap

            if ((changedMask and ControllerPacket.A_FLAG) != 0) {
                if (aDown) {
                    conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
                } else {
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
                }
            }
            if ((changedMask and ControllerPacket.B_FLAG) != 0) {
                if (bDown) {
                    conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
                } else {
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
                }
            }
            if ((changedMask and ControllerPacket.UP_FLAG) != 0) {
                if ((inputMap and ControllerPacket.UP_FLAG) != 0) {
                    conn.sendMouseScroll(1.toByte())
                }
            }
            if ((changedMask and ControllerPacket.DOWN_FLAG) != 0) {
                if ((inputMap and ControllerPacket.DOWN_FLAG) != 0) {
                    conn.sendMouseScroll((-1).toByte())
                }
            }
            if ((changedMask and ControllerPacket.RIGHT_FLAG) != 0) {
                if ((inputMap and ControllerPacket.RIGHT_FLAG) != 0) {
                    conn.sendMouseHScroll(1.toByte())
                }
            }
            if ((changedMask and ControllerPacket.LEFT_FLAG) != 0) {
                if ((inputMap and ControllerPacket.LEFT_FLAG) != 0) {
                    conn.sendMouseHScroll((-1).toByte())
                }
            }

            conn.sendControllerInput(
                controllerNumber, getActiveControllerMask(),
                0, 0.toByte(), 0.toByte(),
                0.toShort(), 0.toShort(), 0.toShort(), 0.toShort()
            )
        } else {
            conn.sendControllerInput(
                controllerNumber, getActiveControllerMask(),
                inputMap,
                leftTrigger, rightTrigger,
                leftStickX, leftStickY,
                rightStickX, rightStickY
            )
        }
    }

    // ========== Remapping ==========

    // Return a valid keycode, -2 to consume, or -1 to not consume the event
    // Device MAY BE NULL
    private fun handleRemapping(context: InputDeviceContext, event: KeyEvent): Int {
        // Don't capture the back button if configured
        if (context.ignoreBack) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                return REMAP_IGNORE
            }
        }

        // If we know this gamepad has a share button and receive an unmapped
        // KEY_RECORD event, report that as a share button press.
        if (context.hasShare) {
            if (event.keyCode == KeyEvent.KEYCODE_UNKNOWN && event.scanCode == 167) {
                return KeyEvent.KEYCODE_MEDIA_RECORD
            }
        }

        // The Shield's key layout files map the DualShock 4 clickpad button to
        // BUTTON_SELECT instead of something sane like BUTTON_1 as the standard AOSP
        // mapping does. If we get a button from a Sony device reported as BUTTON_SELECT
        // that matches the keycode used by hid-sony for the clickpad or it's from the
        // separate touchpad input device, remap it to BUTTON_1 to match the current AOSP
        // layout and trigger our touchpad button logic.
        if (context.vendorId == 0x054c &&
            event.keyCode == KeyEvent.KEYCODE_BUTTON_SELECT &&
            (event.scanCode == 317 || context.isDualShockStandaloneTouchpad)
        ) {
            return KeyEvent.KEYCODE_BUTTON_1
        }

        // Override mode button for 8BitDo controllers
        if (context.vendorId == 0x2dc8 && event.scanCode == 306) {
            return KeyEvent.KEYCODE_BUTTON_MODE
        }

        // This mapping was adding in Android 10, then changed based on
        // kernel changes (adding hid-nintendo) in Android 11. If we're
        // on anything newer than Pie, just use the built-in mapping.
        if ((context.vendorId == 0x057e && context.productId == 0x2009 && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) || // Switch Pro controller
            (context.vendorId == 0x0f0d && context.productId == 0x00c1) // HORIPAD for Switch
        ) {
            when (event.scanCode) {
                0x130 -> return KeyEvent.KEYCODE_BUTTON_A
                0x131 -> return KeyEvent.KEYCODE_BUTTON_B
                0x132 -> return KeyEvent.KEYCODE_BUTTON_X
                0x133 -> return KeyEvent.KEYCODE_BUTTON_Y
                0x134 -> return KeyEvent.KEYCODE_BUTTON_L1
                0x135 -> return KeyEvent.KEYCODE_BUTTON_R1
                0x136 -> return KeyEvent.KEYCODE_BUTTON_L2
                0x137 -> return KeyEvent.KEYCODE_BUTTON_R2
                0x138 -> return KeyEvent.KEYCODE_BUTTON_SELECT
                0x139 -> return KeyEvent.KEYCODE_BUTTON_START
                0x13A -> return KeyEvent.KEYCODE_BUTTON_THUMBL
                0x13B -> return KeyEvent.KEYCODE_BUTTON_THUMBR
                0x13D -> return KeyEvent.KEYCODE_BUTTON_MODE
            }
        }

        if (context.usesLinuxGamepadStandardFaceButtons) {
            // Android's Generic.kl swaps BTN_NORTH and BTN_WEST
            when (event.scanCode) {
                304 -> return KeyEvent.KEYCODE_BUTTON_A
                305 -> return KeyEvent.KEYCODE_BUTTON_B
                307 -> return KeyEvent.KEYCODE_BUTTON_Y
                308 -> return KeyEvent.KEYCODE_BUTTON_X
            }
        }

        if (context.isNonStandardDualShock4) {
            when (event.scanCode) {
                304 -> return KeyEvent.KEYCODE_BUTTON_X
                305 -> return KeyEvent.KEYCODE_BUTTON_A
                306 -> return KeyEvent.KEYCODE_BUTTON_B
                307 -> return KeyEvent.KEYCODE_BUTTON_Y
                308 -> return KeyEvent.KEYCODE_BUTTON_L1
                309 -> return KeyEvent.KEYCODE_BUTTON_R1
                /*
                **** Using analog triggers instead ****
                310 -> return KeyEvent.KEYCODE_BUTTON_L2
                311 -> return KeyEvent.KEYCODE_BUTTON_R2
                */
                312 -> return KeyEvent.KEYCODE_BUTTON_SELECT
                313 -> return KeyEvent.KEYCODE_BUTTON_START
                314 -> return KeyEvent.KEYCODE_BUTTON_THUMBL
                315 -> return KeyEvent.KEYCODE_BUTTON_THUMBR
                316 -> return KeyEvent.KEYCODE_BUTTON_MODE
                else -> return REMAP_CONSUME
            }
        }
        // If this is a Serval controller sending an unknown key code, it's probably
        // the start and select buttons
        else if (context.isServal && event.keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            when (event.scanCode) {
                314 -> return KeyEvent.KEYCODE_BUTTON_SELECT
                315 -> return KeyEvent.KEYCODE_BUTTON_START
            }
        } else if (context.isNonStandardXboxBtController) {
            when (event.scanCode) {
                306 -> return KeyEvent.KEYCODE_BUTTON_X
                307 -> return KeyEvent.KEYCODE_BUTTON_Y
                308 -> return KeyEvent.KEYCODE_BUTTON_L1
                309 -> return KeyEvent.KEYCODE_BUTTON_R1
                310 -> return KeyEvent.KEYCODE_BUTTON_SELECT
                311 -> return KeyEvent.KEYCODE_BUTTON_START
                312 -> return KeyEvent.KEYCODE_BUTTON_THUMBL
                313 -> return KeyEvent.KEYCODE_BUTTON_THUMBR
                139 -> return KeyEvent.KEYCODE_BUTTON_MODE
                // Other buttons are mapped correctly
            }

            // The Xbox button is sent as MENU
            if (event.keyCode == KeyEvent.KEYCODE_MENU) {
                return KeyEvent.KEYCODE_BUTTON_MODE
            }
        } else if (context.vendorId == 0x0b05 && // ASUS
            (context.productId == 0x7900 || // Kunai - USB
                    context.productId == 0x7902) // Kunai - Bluetooth
        ) {
            // ROG Kunai has special M1-M4 buttons that are accessible via the
            // joycon-style detachable controllers that we should map to Start
            // and Select.
            when (event.scanCode) {
                264, 266 -> return KeyEvent.KEYCODE_BUTTON_START
                265, 267 -> return KeyEvent.KEYCODE_BUTTON_SELECT
            }
        }

        if (context.hatXAxis == -1 &&
            context.hatYAxis == -1 &&
            /* FIXME: There's no good way to know for sure if xpad is bound
               to this device, so we won't use the name to validate if these
               scancodes should be mapped to DPAD

               context.isXboxController &&
             */
            event.keyCode == KeyEvent.KEYCODE_UNKNOWN
        ) {
            // If there's not a proper Xbox controller mapping, we'll translate the raw d-pad
            // scan codes into proper key codes
            when (event.scanCode) {
                704 -> return KeyEvent.KEYCODE_DPAD_LEFT
                705 -> return KeyEvent.KEYCODE_DPAD_RIGHT
                706 -> return KeyEvent.KEYCODE_DPAD_UP
                707 -> return KeyEvent.KEYCODE_DPAD_DOWN
            }
        }

        // Past here we can fixup the keycode and potentially trigger
        // another special case so we need to remember what keycode we're using
        var keyCode = event.keyCode

        // This is a hack for (at least) the "Tablet Remote" app
        // which sends BACK with META_ALT_ON instead of KEYCODE_BUTTON_B
        if (keyCode == KeyEvent.KEYCODE_BACK &&
            !event.hasNoModifiers() &&
            (event.flags and KeyEvent.FLAG_SOFT_KEYBOARD) != 0
        ) {
            keyCode = KeyEvent.KEYCODE_BUTTON_B
        }

        if (keyCode == KeyEvent.KEYCODE_BUTTON_START ||
            keyCode == KeyEvent.KEYCODE_MENU
        ) {
            // Ensure that we never use back as start if we have a real start
            context.backIsStart = false
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_SELECT) {
            // Don't use mode as select if we have a select
            context.modeIsSelect = false
        } else if (context.backIsStart && keyCode == KeyEvent.KEYCODE_BACK) {
            // Emulate the start button with back
            return KeyEvent.KEYCODE_BUTTON_START
        } else if (context.modeIsSelect && keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            // Emulate the select button with mode
            return KeyEvent.KEYCODE_BUTTON_SELECT
        } else if (context.searchIsMode && keyCode == KeyEvent.KEYCODE_SEARCH) {
            // Emulate the mode button with search
            return KeyEvent.KEYCODE_BUTTON_MODE
        }

        return keyCode
    }

    private fun handleFlipFaceButtons(keyCode: Int): Int {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
            KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
            KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
            else -> keyCode
        }
    }

    // ========== Deadzone & Axis Handling ==========

    private fun populateCachedVector(x: Float, y: Float): Vector2d {
        // Reinitialize our cached Vector2d object
        inputVector.initialize(x, y)
        return inputVector
    }

    private fun handleDeadZone(stickVector: Vector2d, deadzoneRadius: Float) {
        if (stickVector.magnitude <= deadzoneRadius) {
            // Deadzone
            stickVector.initialize(0f, 0f)
        }

        // We're not normalizing here because we let the computer handle the deadzones.
        // Normalizing can make the deadzones larger than they should be after the computer also
        // evaluates the deadzone.
    }

    private fun handleAxisSet(
        context: InputDeviceContext, lsX: Float, lsY: Float, rsX: Float,
        rsY: Float, lt: Float, rt: Float, hatX: Float, hatY: Float
    ) {
        @Suppress("NAME_SHADOWING")
        var lt = lt
        @Suppress("NAME_SHADOWING")
        var rt = rt

        if (context.leftStickXAxis != -1 && context.leftStickYAxis != -1) {
            val leftStickVector = populateCachedVector(lsX, lsY)

            handleDeadZone(leftStickVector, context.leftStickDeadzoneRadius)

            context.leftStickX = (leftStickVector.x * 0x7FFE).toInt().toShort()
            context.leftStickY = (-leftStickVector.y * 0x7FFE).toInt().toShort()
        }

        // Handle physical right stick separately and then apply gyro fusion if needed
        var physX: Short = 0
        var physY: Short = 0
        if (context.rightStickXAxis != -1 && context.rightStickYAxis != -1) {
            val rightStickVector = populateCachedVector(rsX, rsY)

            handleDeadZone(rightStickVector, context.rightStickDeadzoneRadius)

            physX = (rightStickVector.x * 0x7FFE).toInt().toShort()
            physY = (-rightStickVector.y * 0x7FFE).toInt().toShort()
            // cache physical right stick and apply EPS denoising
            context.physRightStickX = denoisePhys(physX)
            context.physRightStickY = denoisePhys(physY)
        }

        if (context.leftTriggerAxis != -1 && context.rightTriggerAxis != -1) {
            // Android sends an initial 0 value for trigger axes even if the trigger
            // should be negative when idle. After the first touch, the axes will go back
            // to normal behavior, so ignore triggersIdleNegative for each trigger until
            // first touch.
            if (lt != 0f) {
                context.leftTriggerAxisUsed = true
            }
            if (rt != 0f) {
                context.rightTriggerAxisUsed = true
            }
            if (context.triggersIdleNegative) {
                if (context.leftTriggerAxisUsed) {
                    lt = (lt + 1) / 2
                }
                if (context.rightTriggerAxisUsed) {
                    rt = (rt + 1) / 2
                }
            }

            if (lt <= context.triggerDeadzone) {
                lt = 0f
            }
            if (rt <= context.triggerDeadzone) {
                rt = 0f
            }

            context.leftTrigger = (lt * 0xFF).toInt().toByte()
            context.rightTrigger = (rt * 0xFF).toInt().toByte()
        }

        // Handle gyro hold activation edge detection for analog triggers
        val wasHold = context.gyroHoldActive
        if (prefConfig.gyroToRightStick || prefConfig.gyroToMouse) {
            context.gyroHoldActive = gyroManager.computeAnalogActivation(lt, rt)
            if (wasHold && !context.gyroHoldActive) {
                gyroManager.onGyroHoldDeactivatedInput(context)
            }
        }

        // Apply gyro fusion to right stick if needed
        if (prefConfig.gyroToRightStick && context.gyroHoldActive) {
            // 融合策略：按轴叠加并限幅
            val gx = context.gyroRightStickX
            val gy = context.gyroRightStickY
            context.rightStickX = clampShortToStickRange(physX + gx)
            context.rightStickY = clampShortToStickRange(physY + gy)
        } else {
            context.rightStickX = physX
            context.rightStickY = physY
        }

        if (context.hatXAxis != -1 && context.hatYAxis != -1) {
            context.inputMap = context.inputMap and (ControllerPacket.LEFT_FLAG or ControllerPacket.RIGHT_FLAG).inv()
            if (hatX < -0.5) {
                context.inputMap = context.inputMap or ControllerPacket.LEFT_FLAG
                context.hatXAxisUsed = true
            } else if (hatX > 0.5) {
                context.inputMap = context.inputMap or ControllerPacket.RIGHT_FLAG
                context.hatXAxisUsed = true
            }

            context.inputMap = context.inputMap and (ControllerPacket.UP_FLAG or ControllerPacket.DOWN_FLAG).inv()
            if (hatY < -0.5) {
                context.inputMap = context.inputMap or ControllerPacket.UP_FLAG
                context.hatYAxisUsed = true
            } else if (hatY > 0.5) {
                context.inputMap = context.inputMap or ControllerPacket.DOWN_FLAG
                context.hatYAxisUsed = true
            }
        }

        sendControllerInputPacket(context)
    }

    // Normalize the given raw float value into a 0.0-1.0f range
    private fun normalizeRawValueWithRange(value: Float, range: InputDevice.MotionRange): Float {
        @Suppress("NAME_SHADOWING")
        var value = value
        value = Math.max(value, range.min)
        value = Math.min(value, range.max)

        value -= range.min

        return value / range.range
    }

    // ========== Touchpad Handling ==========

    private fun sendTouchpadEventForPointer(context: InputDeviceContext, event: MotionEvent, touchType: Byte, pointerIndex: Int): Boolean {
        val normalizedX = normalizeRawValueWithRange(event.getX(pointerIndex), context.touchpadXRange!!)
        val normalizedY = normalizeRawValueWithRange(event.getY(pointerIndex), context.touchpadYRange!!)
        val normalizedPressure = if (context.touchpadPressureRange != null)
            normalizeRawValueWithRange(event.getPressure(pointerIndex), context.touchpadPressureRange!!)
        else 0f

        return conn.sendControllerTouchEvent(
            context.controllerNumber.toByte(), touchType,
            event.getPointerId(pointerIndex),
            normalizedX, normalizedY, normalizedPressure
        ) != MoonBridge.LI_ERR_UNSUPPORTED
    }

    fun tryHandleTouchpadEvent(event: MotionEvent): Boolean {
        // Bail if this is not a touchpad or mouse event
        if (event.source != InputDevice.SOURCE_TOUCHPAD &&
            event.source != InputDevice.SOURCE_MOUSE
        ) {
            return false
        }

        // Only get a context if one already exists. We want to ensure we don't report non-gamepads.
        val context = inputDeviceContexts.get(event.deviceId) ?: return false

        // When we're working with a mouse source instead of a touchpad, we're quite limited in
        // what useful input we can provide via the controller API. The ABS_X/ABS_Y values are
        // screen coordinates rather than touchpad coordinates. For now, we will just support
        // the clickpad button and nothing else.
        if (event.source == InputDevice.SOURCE_MOUSE) {
            // Unlike the touchpad where down and up refer to individual touches on the touchpad,
            // down and up on a mouse indicates the state of the left mouse button.
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    context.inputMap = context.inputMap or ControllerPacket.TOUCHPAD_FLAG
                    sendControllerInputPacket(context)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    context.inputMap = context.inputMap and ControllerPacket.TOUCHPAD_FLAG.inv()
                    sendControllerInputPacket(context)
                }
            }

            return !prefConfig.gamepadTouchpadAsMouse
        }

        val touchType: Byte
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                touchType = MoonBridge.LI_TOUCH_EVENT_DOWN

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                touchType = if ((event.flags and MotionEvent.FLAG_CANCELED) != 0) {
                    MoonBridge.LI_TOUCH_EVENT_CANCEL
                } else {
                    MoonBridge.LI_TOUCH_EVENT_UP
                }

            MotionEvent.ACTION_MOVE ->
                touchType = MoonBridge.LI_TOUCH_EVENT_MOVE

            MotionEvent.ACTION_CANCEL ->
                // ACTION_CANCEL applies to *all* pointers in the gesture, so it maps to CANCEL_ALL
                // rather than CANCEL. For a single pointer cancellation, that's indicated via
                // FLAG_CANCELED on a ACTION_POINTER_UP.
                // https://developer.android.com/develop/ui/views/touch-and-input/gestures/multi
                touchType = MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL

            MotionEvent.ACTION_BUTTON_PRESS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && event.actionButton == MotionEvent.BUTTON_PRIMARY) {
                    context.inputMap = context.inputMap or ControllerPacket.TOUCHPAD_FLAG
                    sendControllerInputPacket(context)
                    return !prefConfig.gamepadTouchpadAsMouse // Report as unhandled event to trigger mouse handling
                }
                return false
            }

            MotionEvent.ACTION_BUTTON_RELEASE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && event.actionButton == MotionEvent.BUTTON_PRIMARY) {
                    context.inputMap = context.inputMap and ControllerPacket.TOUCHPAD_FLAG.inv()
                    sendControllerInputPacket(context)
                    return !prefConfig.gamepadTouchpadAsMouse // Report as unhandled event to trigger mouse handling
                }
                return false
            }

            else -> return false
        }

        // Bail if the user wants gamepad touchpads to control the mouse
        //
        // NB: We do this after processing ACTION_BUTTON_PRESS and ACTION_BUTTON_RELEASE
        // because we want to still send the touchpad button via the gamepad even when
        // configured to use the touchpad for mouse control.
        if (prefConfig.gamepadTouchpadAsMouse) {
            return false
        }

        // If we don't have X and Y ranges, we can't process this event
        if (context.touchpadXRange == null || context.touchpadYRange == null) {
            return false
        }

        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            // Move events may impact all active pointers
            for (i in 0 until event.pointerCount) {
                if (!sendTouchpadEventForPointer(context, event, touchType, i)) {
                    // Controller touch events are not supported by the host
                    return false
                }
            }
            return true
        } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            // Cancel impacts all active pointers
            return conn.sendControllerTouchEvent(
                context.controllerNumber.toByte(), MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL,
                0, 0f, 0f, 0f
            ) != MoonBridge.LI_ERR_UNSUPPORTED
        } else {
            // Down and Up events impact the action index pointer
            return sendTouchpadEventForPointer(context, event, touchType, event.actionIndex)
        }
    }

    // ========== Motion Event Handling ==========

    fun handleMotionEvent(event: MotionEvent): Boolean {
        val context = getContextForEvent(event) ?: return true

        var lsX = 0f; var lsY = 0f; var rsX = 0f; var rsY = 0f
        var rt = 0f; var lt = 0f; var hatX = 0f; var hatY = 0f

        // We purposefully ignore the historical values in the motion event as it makes
        // the controller feel sluggish for some users.

        if (context.leftStickXAxis != -1 && context.leftStickYAxis != -1) {
            lsX = event.getAxisValue(context.leftStickXAxis)
            lsY = event.getAxisValue(context.leftStickYAxis)
        }

        if (context.rightStickXAxis != -1 && context.rightStickYAxis != -1) {
            rsX = event.getAxisValue(context.rightStickXAxis)
            rsY = event.getAxisValue(context.rightStickYAxis)
        }

        if (context.leftTriggerAxis != -1 && context.rightTriggerAxis != -1) {
            lt = event.getAxisValue(context.leftTriggerAxis)
            rt = event.getAxisValue(context.rightTriggerAxis)
        }

        if (context.hatXAxis != -1 && context.hatYAxis != -1) {
            hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        }

        handleAxisSet(context, lsX, lsY, rsX, rsY, lt, rt, hatX, hatY)

        return true
    }

    // ========== Mouse Emulation ==========

    private fun convertRawStickAxisToPixelMovement(stickX: Short, stickY: Short): Vector2d {
        val vector = Vector2d()
        vector.initialize(stickX.toFloat(), stickY.toFloat())
        vector.scalarMultiply((1 / 32766.0f).toDouble())
        vector.scalarMultiply(4.0)
        if (vector.magnitude > 0) {
            // Move faster as the stick is pressed further from center
            vector.scalarMultiply(Math.pow(vector.magnitude, 2.0))
        }
        return vector
    }

    internal fun sendEmulatedMouseMove(x: Short, y: Short) {
        val vector = convertRawStickAxisToPixelMovement(x, y)
        if (vector.magnitude >= 1) {
            conn.sendMouseMove(vector.x.toInt().toShort(), (-vector.y).toInt().toShort())
        }
    }

    internal fun sendEmulatedMouseScroll(x: Short, y: Short) {
        val vector = convertRawStickAxisToPixelMovement(x, y)
        if (vector.magnitude >= 1) {
            conn.sendMouseHighResScroll(vector.y.toInt().toShort())
            conn.sendMouseHighResHScroll(vector.x.toInt().toShort())
        }
    }

    // ========== Button Handling ==========

    fun handleButtonUp(event: KeyEvent): Boolean {
        val context = getContextForEvent(event) ?: return true

        updatePerformanceShortcut(context, event.keyCode, pressed = false)
        var keyCode = handleRemapping(context, event)
        if (keyCode < 0) {
            return (keyCode == REMAP_CONSUME)
        }

        if (prefConfig.flipFaceButtons) {
            keyCode = handleFlipFaceButtons(keyCode)
        }

        // If the button hasn't been down long enough, sleep for a bit before sending the up event
        // This allows "instant" button presses (like OUYA's virtual menu button) to work. This
        // path should not be triggered during normal usage.
        val buttonDownTime = (event.eventTime - event.downTime).toInt()
        if (buttonDownTime < MINIMUM_BUTTON_DOWN_TIME_MS) {
            // Since our sleep time is so short (<= 25 ms), it shouldn't cause a problem doing this
            // in the UI thread.
            try {
                Thread.sleep((MINIMUM_BUTTON_DOWN_TIME_MS - buttonDownTime).toLong())
            } catch (e: InterruptedException) {
                e.printStackTrace()

                // InterruptedException clears the thread's interrupt status. Since we can't
                // handle that here, we will re-interrupt the thread to set the interrupt
                // status back to true.
                Thread.currentThread().interrupt()
            }
        }

        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_MODE ->
                context.inputMap = context.inputMap and ControllerPacket.SPECIAL_BUTTON_FLAG.inv()
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> {
                // Sometimes we'll get a spurious key up event on controller disconnect.
                // Make sure it's real by checking that the key is actually down before taking
                // any action.
                if ((context.inputMap and ControllerPacket.PLAY_FLAG) != 0 &&
                    event.eventTime - context.startDownTime > START_DOWN_TIME_MOUSE_MODE_MS &&
                    prefConfig.enableStartKeyMenu
                ) {
                    gestures.showGameMenu(context)
                }
                context.inputMap = context.inputMap and ControllerPacket.PLAY_FLAG.inv()
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_SELECT ->
                context.inputMap = context.inputMap and ControllerPacket.BACK_FLAG.inv()
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (context.hatXAxisUsed) return true
                context.inputMap = context.inputMap and ControllerPacket.LEFT_FLAG.inv()
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (context.hatXAxisUsed) return true
                context.inputMap = context.inputMap and ControllerPacket.RIGHT_FLAG.inv()
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (context.hatYAxisUsed) return true
                context.inputMap = context.inputMap and ControllerPacket.UP_FLAG.inv()
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (context.hatYAxisUsed) return true
                context.inputMap = context.inputMap and ControllerPacket.DOWN_FLAG.inv()
            }
            KeyEvent.KEYCODE_DPAD_UP_LEFT -> {
                if (context.hatXAxisUsed && context.hatYAxisUsed) return true
                context.inputMap = context.inputMap and (ControllerPacket.UP_FLAG or ControllerPacket.LEFT_FLAG).inv()
            }
            KeyEvent.KEYCODE_DPAD_UP_RIGHT -> {
                if (context.hatXAxisUsed && context.hatYAxisUsed) return true
                context.inputMap = context.inputMap and (ControllerPacket.UP_FLAG or ControllerPacket.RIGHT_FLAG).inv()
            }
            KeyEvent.KEYCODE_DPAD_DOWN_LEFT -> {
                if (context.hatXAxisUsed && context.hatYAxisUsed) return true
                context.inputMap = context.inputMap and (ControllerPacket.DOWN_FLAG or ControllerPacket.LEFT_FLAG).inv()
            }
            KeyEvent.KEYCODE_DPAD_DOWN_RIGHT -> {
                if (context.hatXAxisUsed && context.hatYAxisUsed) return true
                context.inputMap = context.inputMap and (ControllerPacket.DOWN_FLAG or ControllerPacket.RIGHT_FLAG).inv()
            }
            KeyEvent.KEYCODE_BUTTON_B ->
                context.inputMap = context.inputMap and ControllerPacket.B_FLAG.inv()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A ->
                context.inputMap = context.inputMap and ControllerPacket.A_FLAG.inv()
            KeyEvent.KEYCODE_BUTTON_X ->
                context.inputMap = context.inputMap and ControllerPacket.X_FLAG.inv()
            KeyEvent.KEYCODE_BUTTON_Y ->
                context.inputMap = context.inputMap and ControllerPacket.Y_FLAG.inv()
            KeyEvent.KEYCODE_BUTTON_L1 -> {
                context.inputMap = context.inputMap and ControllerPacket.LB_FLAG.inv()
                context.lastLbUpTime = event.eventTime
            }
            KeyEvent.KEYCODE_BUTTON_R1 -> {
                context.inputMap = context.inputMap and ControllerPacket.RB_FLAG.inv()
                context.lastRbUpTime = event.eventTime
            }
            KeyEvent.KEYCODE_BUTTON_THUMBL ->
                context.inputMap = context.inputMap and ControllerPacket.LS_CLK_FLAG.inv()
            KeyEvent.KEYCODE_BUTTON_THUMBR ->
                context.inputMap = context.inputMap and ControllerPacket.RS_CLK_FLAG.inv()
            KeyEvent.KEYCODE_MEDIA_RECORD -> // Xbox Series X Share button
                context.inputMap = context.inputMap and ControllerPacket.MISC_FLAG.inv()
            KeyEvent.KEYCODE_BUTTON_1 -> // PS4/PS5 touchpad button (prior to 4.10)
                context.inputMap = context.inputMap and ControllerPacket.TOUCHPAD_FLAG.inv()
            KeyEvent.KEYCODE_BUTTON_L2 -> {
                if (context.leftTriggerAxisUsed) return true
                context.leftTrigger = 0
                gyroManager.updateGyroHoldFromDigital(context, KeyEvent.KEYCODE_BUTTON_L2, false)
            }
            KeyEvent.KEYCODE_BUTTON_R2 -> {
                if (context.rightTriggerAxisUsed) return true
                context.rightTrigger = 0
                gyroManager.updateGyroHoldFromDigital(context, KeyEvent.KEYCODE_BUTTON_R2, false)
            }
            KeyEvent.KEYCODE_UNKNOWN -> {
                // Paddles aren't mapped in any of the Android key layout files,
                // so we need to handle the evdev key codes directly.
                if (context.hasPaddles) {
                    when (event.scanCode) {
                        0x2c4 -> context.inputMap = context.inputMap and ControllerPacket.PADDLE1_FLAG.inv() // BTN_TRIGGER_HAPPY5
                        0x2c5 -> context.inputMap = context.inputMap and ControllerPacket.PADDLE2_FLAG.inv() // BTN_TRIGGER_HAPPY6
                        0x2c6 -> context.inputMap = context.inputMap and ControllerPacket.PADDLE3_FLAG.inv() // BTN_TRIGGER_HAPPY7
                        0x2c7 -> context.inputMap = context.inputMap and ControllerPacket.PADDLE4_FLAG.inv() // BTN_TRIGGER_HAPPY8
                        else -> return false
                    }
                } else {
                    return false
                }
            }
            else -> return false
        }

        // Check if we're emulating the select button
        if ((context.emulatingButtonFlags and EMULATING_SELECT) != 0) {
            // If either start or LB is up, select comes up too
            if ((context.inputMap and ControllerPacket.PLAY_FLAG) == 0 ||
                (context.inputMap and ControllerPacket.LB_FLAG) == 0
            ) {
                context.inputMap = context.inputMap and ControllerPacket.BACK_FLAG.inv()
                context.emulatingButtonFlags = context.emulatingButtonFlags and EMULATING_SELECT.inv()
            }
        }

        // Check if we're emulating the special button
        if ((context.emulatingButtonFlags and EMULATING_SPECIAL) != 0) {
            // If either start or select and RB is up, the special button comes up too
            if ((context.inputMap and ControllerPacket.PLAY_FLAG) == 0 ||
                ((context.inputMap and ControllerPacket.BACK_FLAG) == 0 &&
                        (context.inputMap and ControllerPacket.RB_FLAG) == 0)
            ) {
                context.inputMap = context.inputMap and ControllerPacket.SPECIAL_BUTTON_FLAG.inv()
                context.emulatingButtonFlags = context.emulatingButtonFlags and EMULATING_SPECIAL.inv()
            }
        }

        // Check if we're emulating the touchpad button
        if ((context.emulatingButtonFlags and EMULATING_TOUCHPAD) != 0) {
            // If either select or LB is up, touchpad comes up too
            if ((context.inputMap and ControllerPacket.BACK_FLAG) == 0 ||
                (context.inputMap and ControllerPacket.LB_FLAG) == 0
            ) {
                context.inputMap = context.inputMap and ControllerPacket.TOUCHPAD_FLAG.inv()
                context.emulatingButtonFlags = context.emulatingButtonFlags and EMULATING_TOUCHPAD.inv()
            }
        }

        sendControllerInputPacket(context)

        if (context.pendingExit && context.inputMap == 0) {
            // All buttons from the quit combo are lifted. Finish the activity now.
            activityContext.finish()
        }

        return true
    }

    fun handleButtonDown(event: KeyEvent): Boolean {
        val context = getContextForEvent(event) ?: return true

        updatePerformanceShortcut(context, event.keyCode, pressed = true)
        var keyCode = handleRemapping(context, event)
        if (keyCode < 0) {
            return (keyCode == REMAP_CONSUME)
        }

        if (prefConfig.flipFaceButtons) {
            keyCode = handleFlipFaceButtons(keyCode)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_MODE -> {
                context.hasMode = true
                context.inputMap = context.inputMap or ControllerPacket.SPECIAL_BUTTON_FLAG
            }
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> {
                if (event.repeatCount == 0) {
                    context.startDownTime = event.eventTime
                }
                context.inputMap = context.inputMap or ControllerPacket.PLAY_FLAG
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_SELECT -> {
                context.hasSelect = true
                context.inputMap = context.inputMap or ControllerPacket.BACK_FLAG
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (context.hatXAxisUsed) return true
                context.inputMap = context.inputMap or ControllerPacket.LEFT_FLAG
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (context.hatXAxisUsed) return true
                context.inputMap = context.inputMap or ControllerPacket.RIGHT_FLAG
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (context.hatYAxisUsed) return true
                context.inputMap = context.inputMap or ControllerPacket.UP_FLAG
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (context.hatYAxisUsed) return true
                context.inputMap = context.inputMap or ControllerPacket.DOWN_FLAG
            }
            KeyEvent.KEYCODE_DPAD_UP_LEFT -> {
                if (context.hatXAxisUsed && context.hatYAxisUsed) return true
                context.inputMap = context.inputMap or ControllerPacket.UP_FLAG or ControllerPacket.LEFT_FLAG
            }
            KeyEvent.KEYCODE_DPAD_UP_RIGHT -> {
                if (context.hatXAxisUsed && context.hatYAxisUsed) return true
                context.inputMap = context.inputMap or ControllerPacket.UP_FLAG or ControllerPacket.RIGHT_FLAG
            }
            KeyEvent.KEYCODE_DPAD_DOWN_LEFT -> {
                if (context.hatXAxisUsed && context.hatYAxisUsed) return true
                context.inputMap = context.inputMap or ControllerPacket.DOWN_FLAG or ControllerPacket.LEFT_FLAG
            }
            KeyEvent.KEYCODE_DPAD_DOWN_RIGHT -> {
                if (context.hatXAxisUsed && context.hatYAxisUsed) return true
                context.inputMap = context.inputMap or ControllerPacket.DOWN_FLAG or ControllerPacket.RIGHT_FLAG
            }
            KeyEvent.KEYCODE_BUTTON_B ->
                context.inputMap = context.inputMap or ControllerPacket.B_FLAG
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A ->
                context.inputMap = context.inputMap or ControllerPacket.A_FLAG
            KeyEvent.KEYCODE_BUTTON_X ->
                context.inputMap = context.inputMap or ControllerPacket.X_FLAG
            KeyEvent.KEYCODE_BUTTON_Y ->
                context.inputMap = context.inputMap or ControllerPacket.Y_FLAG
            KeyEvent.KEYCODE_BUTTON_L1 ->
                context.inputMap = context.inputMap or ControllerPacket.LB_FLAG
            KeyEvent.KEYCODE_BUTTON_R1 ->
                context.inputMap = context.inputMap or ControllerPacket.RB_FLAG
            KeyEvent.KEYCODE_BUTTON_THUMBL ->
                context.inputMap = context.inputMap or ControllerPacket.LS_CLK_FLAG
            KeyEvent.KEYCODE_BUTTON_THUMBR ->
                context.inputMap = context.inputMap or ControllerPacket.RS_CLK_FLAG
            KeyEvent.KEYCODE_MEDIA_RECORD -> // Xbox Series X Share button
                context.inputMap = context.inputMap or ControllerPacket.MISC_FLAG
            KeyEvent.KEYCODE_BUTTON_1 -> // PS4/PS5 touchpad button (prior to 4.10)
                context.inputMap = context.inputMap or ControllerPacket.TOUCHPAD_FLAG
            KeyEvent.KEYCODE_BUTTON_L2 -> {
                if (context.leftTriggerAxisUsed) return true
                context.leftTrigger = 0xFF.toByte()
                gyroManager.updateGyroHoldFromDigital(context, KeyEvent.KEYCODE_BUTTON_L2, true)
            }
            KeyEvent.KEYCODE_BUTTON_R2 -> {
                if (context.rightTriggerAxisUsed) return true
                context.rightTrigger = 0xFF.toByte()
                gyroManager.updateGyroHoldFromDigital(context, KeyEvent.KEYCODE_BUTTON_R2, true)
            }
            KeyEvent.KEYCODE_UNKNOWN -> {
                // Paddles aren't mapped in any of the Android key layout files,
                // so we need to handle the evdev key codes directly.
                if (context.hasPaddles) {
                    when (event.scanCode) {
                        0x2c4 -> context.inputMap = context.inputMap or ControllerPacket.PADDLE1_FLAG // BTN_TRIGGER_HAPPY5
                        0x2c5 -> context.inputMap = context.inputMap or ControllerPacket.PADDLE2_FLAG // BTN_TRIGGER_HAPPY6
                        0x2c6 -> context.inputMap = context.inputMap or ControllerPacket.PADDLE3_FLAG // BTN_TRIGGER_HAPPY7
                        0x2c7 -> context.inputMap = context.inputMap or ControllerPacket.PADDLE4_FLAG // BTN_TRIGGER_HAPPY8
                        else -> return false
                    }
                } else {
                    return false
                }
            }
            else -> return false
        }

        // Start+Back+LB+RB is the quit combo
        if (context.inputMap == (ControllerPacket.BACK_FLAG or ControllerPacket.PLAY_FLAG or
                    ControllerPacket.LB_FLAG or ControllerPacket.RB_FLAG)
        ) {
            // Wait for the combo to lift and then finish the activity
            context.pendingExit = true
        }

        // Start+LB acts like select for controllers with one button
        if (!context.hasSelect) {
            if (context.inputMap == (ControllerPacket.PLAY_FLAG or ControllerPacket.LB_FLAG) ||
                (context.inputMap == ControllerPacket.PLAY_FLAG &&
                        event.eventTime - context.lastLbUpTime <= MAXIMUM_BUMPER_UP_DELAY_MS)
            ) {
                context.inputMap = context.inputMap and (ControllerPacket.PLAY_FLAG or ControllerPacket.LB_FLAG).inv()
                context.inputMap = context.inputMap or ControllerPacket.BACK_FLAG

                context.emulatingButtonFlags = context.emulatingButtonFlags or EMULATING_SELECT
            }
        } else if (context.needsClickpadEmulation) {
            // Select+LB acts like the clickpad when we're faking a PS4 controller for motion support
            if (context.inputMap == (ControllerPacket.BACK_FLAG or ControllerPacket.LB_FLAG) ||
                (context.inputMap == ControllerPacket.BACK_FLAG &&
                        event.eventTime - context.lastLbUpTime <= MAXIMUM_BUMPER_UP_DELAY_MS)
            ) {
                context.inputMap = context.inputMap and (ControllerPacket.BACK_FLAG or ControllerPacket.LB_FLAG).inv()
                context.inputMap = context.inputMap or ControllerPacket.TOUCHPAD_FLAG

                context.emulatingButtonFlags = context.emulatingButtonFlags or EMULATING_TOUCHPAD
            }
        }

        // If there is a physical select button, we'll use Start+Select as the special button combo
        // otherwise we'll use Start+RB.
        if (!context.hasMode) {
            if (context.hasSelect) {
                if (context.inputMap == (ControllerPacket.PLAY_FLAG or ControllerPacket.BACK_FLAG)) {
                    context.inputMap = context.inputMap and (ControllerPacket.PLAY_FLAG or ControllerPacket.BACK_FLAG).inv()
                    context.inputMap = context.inputMap or ControllerPacket.SPECIAL_BUTTON_FLAG

                    context.emulatingButtonFlags = context.emulatingButtonFlags or EMULATING_SPECIAL
                }
            } else {
                if (context.inputMap == (ControllerPacket.PLAY_FLAG or ControllerPacket.RB_FLAG) ||
                    (context.inputMap == ControllerPacket.PLAY_FLAG &&
                            event.eventTime - context.lastRbUpTime <= MAXIMUM_BUMPER_UP_DELAY_MS)
                ) {
                    context.inputMap = context.inputMap and (ControllerPacket.PLAY_FLAG or ControllerPacket.RB_FLAG).inv()
                    context.inputMap = context.inputMap or ControllerPacket.SPECIAL_BUTTON_FLAG

                    context.emulatingButtonFlags = context.emulatingButtonFlags or EMULATING_SPECIAL
                }
            }
        }

        // We don't need to send repeat key down events, but the platform
        // sends us events that claim to be repeats but they're from different
        // devices, so we just send them all and deal with some duplicates.
        sendControllerInputPacket(context)
        return true
    }

    // ========== OSC / Virtual Controller ==========

    fun reportOscState(
        buttonFlags: Int,
        leftStickX: Short, leftStickY: Short,
        rightStickX: Short, rightStickY: Short,
        leftTrigger: Byte, rightTrigger: Byte
    ) {
        defaultContext.leftStickX = leftStickX
        defaultContext.leftStickY = leftStickY

        // 更新物理右摇杆值（用于陀螺仪融合）
        defaultContext.physRightStickX = rightStickX
        defaultContext.physRightStickY = rightStickY

        defaultContext.leftTrigger = leftTrigger
        defaultContext.rightTrigger = rightTrigger

        // 更新陀螺仪激活状态
        val wasHold = defaultContext.gyroHoldActive
        val leftTriggerFloat = (leftTrigger.toInt() and 0xFF) / 255.0f
        val rightTriggerFloat = (rightTrigger.toInt() and 0xFF) / 255.0f
        if (prefConfig.gyroToRightStick || prefConfig.gyroToMouse) {
            defaultContext.gyroHoldActive = gyroManager.computeAnalogActivation(leftTriggerFloat, rightTriggerFloat)
        }

        if (wasHold && !defaultContext.gyroHoldActive) {
            gyroManager.onGyroHoldDeactivatedInput(defaultContext)
        }

        if (!prefConfig.gyroToRightStick || !defaultContext.gyroHoldActive) {
            defaultContext.rightStickX = rightStickX
            defaultContext.rightStickY = rightStickY
        }

        defaultContext.inputMap = buttonFlags

        sendControllerInputPacket(defaultContext)
    }

    // ========== USB Driver Callbacks ==========

    private fun handleUsbShortcutUpdate(
        context: UsbDeviceContext,
        update: UsbControllerShortcutStateMachine.Update
    ) {
        for (action in update.actions) {
            when (action) {
                UsbControllerShortcutStateMachine.Action.SCHEDULE_LONG_PRESS -> {
                    mainThreadHandler.removeCallbacks(context.shortcutLongPressRunnable)
                    mainThreadHandler.postDelayed(
                        context.shortcutLongPressRunnable,
                        START_DOWN_TIME_MOUSE_MODE_MS.toLong()
                    )
                }
                UsbControllerShortcutStateMachine.Action.CANCEL_LONG_PRESS ->
                    mainThreadHandler.removeCallbacks(context.shortcutLongPressRunnable)
                UsbControllerShortcutStateMachine.Action.SHOW_HINT -> {
                    mainThreadHandler.post {
                        if (!stopped &&
                            (usbShortcutHintOwner == null || usbShortcutHintOwner === context)
                        ) {
                            usbShortcutHintOwner = context
                            gestures.showUsbControllerShortcutHint()
                        }
                    }
                }
                UsbControllerShortcutStateMachine.Action.HIDE_HINT -> {
                    mainThreadHandler.post {
                        if (usbShortcutHintOwner === context) {
                            usbShortcutHintOwner = null
                            gestures.hideUsbControllerShortcutHint()
                        }
                    }
                }
                UsbControllerShortcutStateMachine.Action.TOGGLE_MOUSE_EMULATION ->
                    mainThreadHandler.post {
                        if (!stopped) context.toggleMouseEmulation()
                    }
                UsbControllerShortcutStateMachine.Action.OPEN_GAME_MENU ->
                    mainThreadHandler.post openMenu@{
                        val requestId = update.menuOpenRequestId ?: return@openMenu
                        if (stopped ||
                            !context.shortcutState.isMenuOpenRequestPending(requestId)
                        ) {
                            context.shortcutState.onGameMenuOpenResult(requestId, false)
                            return@openMenu
                        }
                        handleUsbShortcutUpdate(
                            context,
                            context.shortcutState.onGameMenuOpenResult(
                                requestId,
                                gestures.showGameMenuFromUsb(context)
                            )
                        )
                    }
                UsbControllerShortcutStateMachine.Action.EXIT_STREAM ->
                    mainThreadHandler.post {
                        if (!activityContext.isFinishing) activityContext.finish()
                    }
            }
        }

        for (buttonChange in update.menuButtonChanges) {
            val keyCode = usbMenuKeyCode(buttonChange.buttonFlag) ?: continue
            mainThreadHandler.post {
                if (stopped) return@post
                val keyAction = if (buttonChange.pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
                val eventTime = SystemClock.uptimeMillis()
                val downTime = if (buttonChange.pressed) {
                    context.menuKeyDownTimes[buttonChange.buttonFlag] = eventTime
                    eventTime
                } else {
                    context.menuKeyDownTimes.remove(buttonChange.buttonFlag) ?: eventTime
                }
                val event = KeyEvent(downTime, eventTime, keyAction, keyCode, 0)
                if (!gestures.dispatchUsbControllerMenuKey(event)) {
                    context.menuKeyDownTimes.clear()
                    context.shortcutState.onGameMenuUnavailable()
                }
            }
        }
    }

    private fun usbMenuKeyCode(buttonFlag: Int): Int? {
        return when (buttonFlag) {
            ControllerPacket.UP_FLAG -> KeyEvent.KEYCODE_DPAD_UP
            ControllerPacket.DOWN_FLAG -> KeyEvent.KEYCODE_DPAD_DOWN
            ControllerPacket.LEFT_FLAG -> KeyEvent.KEYCODE_DPAD_LEFT
            ControllerPacket.RIGHT_FLAG -> KeyEvent.KEYCODE_DPAD_RIGHT
            ControllerPacket.A_FLAG -> KeyEvent.KEYCODE_DPAD_CENTER
            ControllerPacket.B_FLAG -> KeyEvent.KEYCODE_BACK
            else -> null
        }
    }

    private fun sendNeutralUsbControllerState(context: UsbDeviceContext) {
        context.inputMap = 0
        context.leftStickX = 0
        context.leftStickY = 0
        context.rightStickX = 0
        context.rightStickY = 0
        context.physRightStickX = 0
        context.physRightStickY = 0
        context.leftTrigger = 0
        context.rightTrigger = 0
        if (context.gyroHoldActive) {
            context.gyroHoldActive = false
            gyroManager.onGyroHoldDeactivated(context)
        }
        sendControllerInputPacket(context)
    }

    override fun reportControllerState(
        controllerId: Int, buttonFlags: Int,
        leftStickX: Float, leftStickY: Float,
        rightStickX: Float, rightStickY: Float,
        leftTrigger: Float, rightTrigger: Float
    ) {
        @Suppress("NAME_SHADOWING")
        var leftTrigger = leftTrigger
        @Suppress("NAME_SHADOWING")
        var rightTrigger = rightTrigger

        val context = usbDeviceContexts.get(controllerId) ?: return
        updatePerformanceShortcut(context, buttonFlags)
        val shortcutUpdate = context.shortcutState.onButtonSnapshot(
            buttonFlags,
            android.os.SystemClock.uptimeMillis(),
            prefConfig.enableStartKeyMenu
        )
        handleUsbShortcutUpdate(context, shortcutUpdate)
        if (shortcutUpdate.consumeAllInput) {
            if (shortcutUpdate.sendNeutralState) {
                sendNeutralUsbControllerState(context)
            }
            return
        }

        // Gyro hold activation via analog LT/RT thresholds when mapped to L2/R2
        val wasHold = context.gyroHoldActive
        context.gyroHoldActive = prefConfig.gyroToRightStick && gyroManager.computeAnalogActivation(leftTrigger, rightTrigger)
        if (wasHold && !context.gyroHoldActive) {
            // Ensure we immediately stop any residual gyro influence
            gyroManager.onGyroHoldDeactivated(context)
        }

        val leftStickVector = populateCachedVector(leftStickX, leftStickY)

        handleDeadZone(leftStickVector, context.leftStickDeadzoneRadius)

        context.leftStickX = (leftStickVector.x * 0x7FFE).toInt().toShort()
        context.leftStickY = (-leftStickVector.y * 0x7FFE).toInt().toShort()

        // Fuse physical right stick with gyro input to avoid jitter.
        // Strategy: if gyro-hold is active, take the component with larger magnitude per-axis.
        // Otherwise, use physical stick only.
        val physX: Short
        val physY: Short
        run {
            val rsv = populateCachedVector(rightStickX, rightStickY)
            handleDeadZone(rsv, context.rightStickDeadzoneRadius)
            physX = (rsv.x * 0x7FFE).toInt().toShort()
            physY = (-rsv.y * 0x7FFE).toInt().toShort()
            // cache physical right stick and apply EPS denoising
            context.physRightStickX = denoisePhys(physX)
            context.physRightStickY = denoisePhys(physY)
        }

        if (prefConfig.gyroToRightStick && context.gyroHoldActive) {
            // 融合策略：按轴叠加并限幅
            val gx = context.gyroRightStickX
            val gy = context.gyroRightStickY
            context.rightStickX = clampShortToStickRange(physX + gx)
            context.rightStickY = clampShortToStickRange(physY + gy)
        } else {
            context.rightStickX = physX
            context.rightStickY = physY
        }

        if (leftTrigger <= context.triggerDeadzone) {
            leftTrigger = 0f
        }
        if (rightTrigger <= context.triggerDeadzone) {
            rightTrigger = 0f
        }

        context.leftTrigger = (leftTrigger * 0xFF).toInt().toByte()
        context.rightTrigger = (rightTrigger * 0xFF).toInt().toByte()

        context.inputMap = buttonFlags

        sendControllerInputPacket(context)
    }

    private fun updatePerformanceShortcut(
        context: GenericControllerContext,
        keyCode: Int,
        pressed: Boolean
    ) {
        val flag = when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_BUTTON_SELECT -> ControllerPacket.BACK_FLAG
            KeyEvent.KEYCODE_BUTTON_L1 -> ControllerPacket.LB_FLAG
            KeyEvent.KEYCODE_BUTTON_R1 -> ControllerPacket.RB_FLAG
            KeyEvent.KEYCODE_BUTTON_X -> ControllerPacket.X_FLAG
            else -> return
        }
        dispatchPerformanceShortcutIfTriggered(
            context.performanceOverlayShortcutState.updateButton(flag, pressed)
        )
    }

    private fun updatePerformanceShortcut(context: GenericControllerContext, buttonFlags: Int) {
        dispatchPerformanceShortcutIfTriggered(
            context.performanceOverlayShortcutState.updateSnapshot(buttonFlags)
        )
    }

    private fun dispatchPerformanceShortcutIfTriggered(triggered: Boolean) {
        if (triggered) {
            mainThreadHandler.post {
                if (!stopped) onTogglePerformanceOverlay()
            }
        }
    }

    override fun deviceRemoved(controller: AbstractController) {
        val context = usbDeviceContexts.get(controller.getControllerId())
        if (context != null) {
            LimeLog.info("Removed controller: " + controller.getControllerId())
            rumbleManager.forgetUsbDevice(controller)
            releaseControllerNumber(context)
            context.destroy()
            usbDeviceContexts.remove(controller.getControllerId())
            hapticsCoordinator.refreshPrimaryController()
            hapticsCoordinator.clearControllerIfUnavailable(context.controllerNumber)
        }
    }

    override fun deviceAdded(controller: AbstractController) {
        if (stopped) {
            return
        }

        val context = createUsbDeviceContextForDevice(controller)
        usbDeviceContexts.put(controller.getControllerId(), context)
        hapticsCoordinator.refreshPrimaryController()
        hapticsCoordinator.onSinkChanged(context.controllerNumber)
    }

    override fun reportControllerMotion(controllerId: Int, motionType: Byte, x: Float, y: Float, z: Float) {
        val context = usbDeviceContexts.get(controllerId) ?: return
        if (context.shortcutState.isLocalInputCaptureActive()) return

        // 当启用"陀螺仪模拟右摇杆"或"陀螺仪模拟鼠标"时，拦截陀螺仪数据
        if (motionType == MoonBridge.LI_MOTION_TYPE_GYRO) {
            if (prefConfig.gyroToMouse && context.gyroHoldActive) {
                // x=pitch(deg/s), y=roll, z=yaw → 横屏下 z→mouseX, x→mouseY，转回 rad/s
                gyroManager.applyGyroToMouse(z / 57.2957795f, x / 57.2957795f, System.nanoTime())
                return
            }
            if (prefConfig.gyroToRightStick && context.gyroHoldActive) {
                // x=pitch, y=roll, z=yaw — pass yaw as X and pitch as Y to match
                // the same axis convention used in the device sensor listener (gz, gx)
                gyroManager.applyGyroToRightStick(context.controllerNumber, z, x)
                return
            }
        }

        // 否则照常上报IMU数据到主机
        conn.sendControllerMotionEvent(context.controllerNumber.toByte(), motionType, x, y, z)
    }

    // ========== Sensor Management ==========

    fun handleSetMotionEventState(controllerNumber: Short, motionType: Byte, reportRateHz: Short) {
        if (stopped) {
            return
        }

        @Suppress("NAME_SHADOWING")
        // Report rate is restricted to <= 200 Hz without the HIGH_SAMPLING_RATE_SENSORS permission
        val reportRateHz = Math.min(200, reportRateHz.toInt()).toShort()

        for (i in 0 until inputDeviceContexts.size()) {
            val deviceContext = inputDeviceContexts.valueAt(i)

            if (deviceContext.controllerNumber == controllerNumber) {
                // Store the desired report rate even if we don't have sensors. In some cases,
                // input devices can be reconfigured at runtime which results in a change where
                // sensors disappear and reappear. By storing the desired report rate, we can
                // reapply the desired motion sensor configuration after they reappear.
                when (motionType) {
                    MoonBridge.LI_MOTION_TYPE_ACCEL -> deviceContext.accelReportRateHz = reportRateHz
                    MoonBridge.LI_MOTION_TYPE_GYRO -> deviceContext.gyroReportRateHz = reportRateHz
                }

                backgroundThreadHandler.removeCallbacks(deviceContext.enableSensorRunnable)

                val sm = deviceContext.sensorManager ?: continue

                when (motionType) {
                    MoonBridge.LI_MOTION_TYPE_ACCEL -> {
                        if (deviceContext.accelListener != null) {
                            sm.unregisterListener(deviceContext.accelListener)
                            deviceContext.accelListener = null
                        }

                        // Enable the accelerometer if requested
                        val accelSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                        if (reportRateHz.toInt() != 0 && accelSensor != null) {
                            deviceContext.accelListener = gyroManager.createSensorListener(controllerNumber, motionType, sm === deviceSensorManager)
                            sm.registerListener(deviceContext.accelListener, accelSensor, 1000000 / reportRateHz)
                        }
                    }
                    MoonBridge.LI_MOTION_TYPE_GYRO -> {
                        if (deviceContext.gyroListener != null) {
                            sm.unregisterListener(deviceContext.gyroListener)
                            deviceContext.gyroListener = null
                        }

                        // Enable the gyroscope if requested
                        val gyroSensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
                        if (reportRateHz.toInt() != 0 && gyroSensor != null) {
                            deviceContext.gyroListener = gyroManager.createSensorListener(controllerNumber, motionType, sm === deviceSensorManager)
                            sm.registerListener(deviceContext.gyroListener, gyroSensor, 1000000 / reportRateHz)
                        }
                    }
                }
                break
            }
        }
    }

    // ========== Delegation to Managers ==========

    fun setVirtualControllerGyroCallbacks(suspend: Runnable?, resume: Runnable?) =
        gyroManager.setVirtualControllerGyroCallbacks(suspend, resume)

    fun setGyroToRightStickEnabled(enabled: Boolean) =
        gyroManager.setGyroToRightStickEnabled(enabled)

    fun setGyroToMouseEnabled(enabled: Boolean) =
        gyroManager.setGyroToMouseEnabled(enabled)

    fun onSensorsReenabled() =
        gyroManager.onSensorsReenabled()

    fun reportVirtualControllerGyro(gx: Float, gy: Float, gz: Float) =
        gyroManager.reportVirtualControllerGyro(gx, gy, gz)

    fun hasAnyController(): Boolean =
        gyroManager.hasAnyController()

    fun hasRumbleCapableController(): Boolean =
        hapticsCoordinator.hasRumbleCapableController()

    fun handleRumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short) =
        hapticsCoordinator.submitHost(controllerNumber, lowFreqMotor, highFreqMotor)

    fun handleTestRumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short) =
        hapticsCoordinator.submitTest(controllerNumber, lowFreqMotor, highFreqMotor)

    fun submitAudioHaptics(
        continuousLow: Float,
        continuousHigh: Float,
        transientLow: Float,
        transientHigh: Float,
        transientDurationMs: Int,
        hasTransient: Boolean,
        continuousChanged: Boolean
    ) = hapticsCoordinator.submitAudio(
        continuousLow,
        continuousHigh,
        transientLow,
        transientHigh,
        transientDurationMs,
        hasTransient,
        continuousChanged
    )

    fun submitAudioRumble(
        lowFrequency: Float,
        highFrequency: Float,
        durationMs: Int,
        continuous: Boolean
    ) = hapticsCoordinator.submitLegacyAudio(
        lowFrequency,
        highFrequency,
        durationMs,
        continuous
    )

    fun stopAudioRumble() =
        hapticsCoordinator.stopAudio()

    fun claimDeviceVibratorForAudio() =
        rumbleManager.releaseDeviceFallbackOwnership()

    fun refreshAudioRumbleWatchdog() =
        hapticsCoordinator.refreshAudioWatchdog()

    fun handleRumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short) =
        hapticsCoordinator.submitHostTriggers(controllerNumber, leftTrigger, rightTrigger)

    fun handleSetAdaptiveTriggers(
        controllerNumber: Short,
        eventFlags: Byte,
        typeLeft: Byte,
        typeRight: Byte,
        left: ByteArray,
        right: ByteArray
    ) = hapticsCoordinator.submitHostAdaptiveTriggers(
        controllerNumber,
        eventFlags,
        typeLeft,
        typeRight,
        left,
        right
    )

    @TargetApi(31)
    fun handleSetControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) =
        rumbleManager.handleSetControllerLED(controllerNumber, r, g, b)
}
