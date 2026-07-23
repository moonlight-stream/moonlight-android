@file:Suppress("DEPRECATION")
package com.limelight.binding.input

import android.annotation.TargetApi
import android.hardware.BatteryState
import android.hardware.Sensor
import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.hardware.lights.LightsRequest
import android.media.AudioAttributes
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

import com.limelight.LimeLog
import com.limelight.binding.input.driver.AbstractController
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.jni.MoonBridge

import org.cgutman.shieldcontrollerextensions.SceChargingState
import org.cgutman.shieldcontrollerextensions.SceConnectionType
import java.util.concurrent.atomic.AtomicReference

/**
 * 振动、LED 与电池状态管理器
 */
class ControllerRumbleManager(private val handler: ControllerHandler) {

    private data class BaseRumble(val lowFrequency: Short, val highFrequency: Short)
    private data class TriggerRumble(val left: Short, val right: Short)

    private inner class UsbRumbleOutput(val device: AbstractController) {
        private val pendingBase = AtomicReference<BaseRumble?>()
        private val pendingTriggers = AtomicReference<TriggerRumble?>()
        @Volatile
        private var closed = false
        private val runnable = Runnable {
            if (closed) return@Runnable
            pendingBase.getAndSet(null)?.let { rumble ->
                try {
                    device.rumble(rumble.lowFrequency, rumble.highFrequency)
                } catch (e: Exception) {
                    LimeLog.warning("Controller rumble failed: ${e.message}")
                }
            }
            pendingTriggers.getAndSet(null)?.let { rumble ->
                try {
                    device.rumbleTriggers(rumble.left, rumble.right)
                } catch (e: Exception) {
                    LimeLog.warning("Controller trigger rumble failed: ${e.message}")
                }
            }
        }

        fun submitBase(rumble: BaseRumble) {
            if (closed) return
            pendingBase.set(rumble)
            schedule()
        }

        fun submitTriggers(rumble: TriggerRumble) {
            if (closed) return
            pendingTriggers.set(rumble)
            schedule()
        }

        private fun schedule() {
            if (closed) return
            // A stable runnable lets the worker keep only this device's latest state.
            handler.backgroundThreadHandler.removeCallbacks(runnable)
            if (!handler.backgroundThreadHandler.post(runnable)) {
                pendingBase.set(null)
                pendingTriggers.set(null)
            }
        }

        fun close() {
            closed = true
            pendingBase.set(null)
            pendingTriggers.set(null)
            handler.backgroundThreadHandler.removeCallbacks(runnable)
        }
    }

    @Volatile
    private var deviceFallbackControllerNumber = NO_DEVICE_FALLBACK_CONTROLLER
    private val usbRumbleOutputsLock = Any()
    private val usbRumbleOutputs = mutableMapOf<Int, UsbRumbleOutput>()

    @TargetApi(31)
    fun hasDualAmplitudeControlledRumbleVibrators(vm: VibratorManager): Boolean {
        val vibratorIds = vm.vibratorIds

        // There must be exactly 2 vibrators on this device
        if (vibratorIds.size != 2) {
            return false
        }

        // Both vibrators must have amplitude control
        for (vid in vibratorIds) {
            if (!vm.getVibrator(vid).hasAmplitudeControl()) {
                return false
            }
        }

        return true
    }

    // This must only be called if hasDualAmplitudeControlledRumbleVibrators() is true!
    @TargetApi(31)
    private fun rumbleDualVibrators(vm: VibratorManager, lowFreqMotor: Short, highFreqMotor: Short) {
        // Normalize motor values to 0-255 amplitudes for VibrationManager
        val highAmp = (highFreqMotor.toInt() shr 8) and 0xFF
        val lowAmp = (lowFreqMotor.toInt() shr 8) and 0xFF

        // If they're both zero, we can just call cancel().
        if (lowAmp == 0 && highAmp == 0) {
            vm.cancel()
            return
        }

        // There's no documentation that states that vibrators for FF_RUMBLE input devices will
        // always be enumerated in this order, but it seems consistent between Xbox Series X (USB),
        // PS3 (USB), and PS4 (USB+BT) controllers on Android 12 Beta 3.
        val vibratorIds = vm.vibratorIds
        val vibratorAmplitudes = intArrayOf(highAmp, lowAmp)

        val combo = CombinedVibration.startParallel()

        for (i in vibratorIds.indices) {
            // It's illegal to create a VibrationEffect with an amplitude of 0.
            // Simply excluding that vibrator from our ParallelCombination will turn it off.
            if (vibratorAmplitudes[i] != 0) {
                combo.addVibrator(vibratorIds[i], VibrationEffect.createOneShot(60000, vibratorAmplitudes[i]))
            }
        }

        val vibrationAttributes = VibrationAttributes.Builder()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrationAttributes.setUsage(VibrationAttributes.USAGE_MEDIA)
        }

        vm.vibrate(combo.combine(), vibrationAttributes.build())
    }

    @TargetApi(31)
    fun hasQuadAmplitudeControlledRumbleVibrators(vm: VibratorManager): Boolean {
        val vibratorIds = vm.vibratorIds

        // There must be exactly 4 vibrators on this device
        if (vibratorIds.size != 4) {
            return false
        }

        // All vibrators must have amplitude control
        for (vid in vibratorIds) {
            if (!vm.getVibrator(vid).hasAmplitudeControl()) {
                return false
            }
        }

        return true
    }

    // This must only be called if hasQuadAmplitudeControlledRumbleVibrators() is true!
    @TargetApi(31)
    private fun rumbleQuadVibrators(vm: VibratorManager, lowFreqMotor: Short, highFreqMotor: Short, leftTrigger: Short, rightTrigger: Short) {
        // Normalize motor values to 0-255 amplitudes for VibrationManager
        val highAmp = (highFreqMotor.toInt() shr 8) and 0xFF
        val lowAmp = (lowFreqMotor.toInt() shr 8) and 0xFF
        val ltAmp = (leftTrigger.toInt() shr 8) and 0xFF
        val rtAmp = (rightTrigger.toInt() shr 8) and 0xFF

        // If they're all zero, we can just call cancel().
        if (lowAmp == 0 && highAmp == 0 && ltAmp == 0 && rtAmp == 0) {
            vm.cancel()
            return
        }

        // This is a guess based upon the behavior of FF_RUMBLE, but untested due to lack of Linux
        // support for trigger rumble!
        val vibratorIds = vm.vibratorIds
        val vibratorAmplitudes = intArrayOf(highAmp, lowAmp, ltAmp, rtAmp)

        val combo = CombinedVibration.startParallel()

        for (i in vibratorIds.indices) {
            // It's illegal to create a VibrationEffect with an amplitude of 0.
            // Simply excluding that vibrator from our ParallelCombination will turn it off.
            if (vibratorAmplitudes[i] != 0) {
                combo.addVibrator(vibratorIds[i], VibrationEffect.createOneShot(60000, vibratorAmplitudes[i]))
            }
        }

        val vibrationAttributes = VibrationAttributes.Builder()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrationAttributes.setUsage(VibrationAttributes.USAGE_MEDIA)
        }

        vm.vibrate(combo.combine(), vibrationAttributes.build())
    }

    fun rumbleSingleVibrator(vibrator: Vibrator, lowFreqMotor: Short, highFreqMotor: Short) {
        // Since we can only use a single amplitude value, compute the desired amplitude
        // by taking 80% of the big motor and 33% of the small motor, then capping to 255.
        // NB: This value is now 0-255 as required by VibrationEffect.
        val simulatedAmplitude = simulatedAmplitude(lowFreqMotor, highFreqMotor)

        if (simulatedAmplitude == 0) {
            // This case is easy - just cancel the current effect and get out.
            // NB: We cannot simply check lowFreqMotor == highFreqMotor == 0
            // because our simulatedAmplitude could be 0 even though our inputs
            // are not (ex: lowFreqMotor == 0 && highFreqMotor == 1).
            vibrator.cancel()
            return
        }

        // Attempt to use amplitude-based control if we're on Oreo and the device
        // supports amplitude-based vibration control.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (vibrator.hasAmplitudeControl()) {
                val effect = VibrationEffect.createOneShot(60000, simulatedAmplitude)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val vibrationAttributes = VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_MEDIA)
                        .build()
                    vibrator.vibrate(effect, vibrationAttributes)
                } else {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .build()
                    vibrator.vibrate(effect, audioAttributes)
                }
                return
            }
        }

        // If we reach this point, we don't have amplitude controls available, so
        // we must emulate it by PWMing the vibration. Ick.
        val pwmPeriod: Long = 20
        val onTime = ((simulatedAmplitude / 255.0) * pwmPeriod).toLong()
        val offTime = pwmPeriod - onTime
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val vibrationAttributes = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_MEDIA)
                .build()
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, onTime, offTime), 0), vibrationAttributes)
        } else {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .build()
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, onTime, offTime), 0, audioAttributes)
        }
    }

    /** Releases fallback ownership after another phone-haptics backend has produced output. */
    fun releaseDeviceFallbackOwnership() {
        deviceFallbackControllerNumber = NO_DEVICE_FALLBACK_CONTROLLER
    }

    /** Cancels only phone vibration that was started by this controller's fallback path. */
    fun clearDeviceFallback(controllerNumber: Short) {
        if (deviceFallbackControllerNumber != controllerNumber.toInt()) return
        deviceFallbackControllerNumber = NO_DEVICE_FALLBACK_CONTROLLER
        handler.deviceVibrator.cancel()
    }

    private fun rumbleDeviceFallback(
        controllerNumber: Short,
        lowFreqMotor: Short,
        highFreqMotor: Short
    ) {
        if (simulatedAmplitude(lowFreqMotor, highFreqMotor) == 0) {
            clearDeviceFallback(controllerNumber)
            return
        }

        deviceFallbackControllerNumber = controllerNumber.toInt()
        rumbleSingleVibrator(handler.deviceVibrator, lowFreqMotor, highFreqMotor)
    }

    private fun simulatedAmplitude(lowFreqMotor: Short, highFreqMotor: Short): Int {
        val lowFreqMotorMSB = (lowFreqMotor.toInt() shr 8) and 0xFF
        val highFreqMotorMSB = (highFreqMotor.toInt() shr 8) and 0xFF
        return Math.min(255, ((lowFreqMotorMSB * 0.80) + (highFreqMotorMSB * 0.33)).toInt())
    }

    private fun usbRumbleOutput(device: AbstractController): UsbRumbleOutput =
        synchronized(usbRumbleOutputsLock) {
            val controllerId = device.getControllerId()
            val existing = usbRumbleOutputs[controllerId]
            if (existing?.device === device) {
                existing
            } else {
                existing?.close()
                UsbRumbleOutput(device).also { usbRumbleOutputs[controllerId] = it }
            }
        }

    internal fun forgetUsbDevice(device: AbstractController) {
        val output = synchronized(usbRumbleOutputsLock) {
            val controllerId = device.getControllerId()
            usbRumbleOutputs[controllerId]
                ?.takeIf { it.device === device }
                ?.also { usbRumbleOutputs.remove(controllerId) }
        }
        output?.close()
    }

    fun handleRumble(
        controllerNumber: Short,
        lowFreqMotor: Short,
        highFreqMotor: Short,
        allowDeviceFallback: Boolean = true
    ) {
        var foundMatchingDevice = false
        var vibrated = false

        if (handler.stopped) {
            return
        }

        for (i in 0 until handler.inputDeviceContexts.size()) {
            val deviceContext = handler.inputDeviceContexts.valueAt(i)

            if (handler.prefConfig.multiController && !deviceContext.assignedControllerNumber) {
                continue
            }

            if (deviceContext.controllerNumber == controllerNumber) {
                foundMatchingDevice = true

                deviceContext.lowFreqMotor = lowFreqMotor
                deviceContext.highFreqMotor = highFreqMotor

                // Prefer the documented Android 12 rumble API which can handle dual vibrators on PS/Xbox controllers
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && deviceContext.vibratorManager != null) {
                    vibrated = true
                    if (deviceContext.quadVibrators) {
                        rumbleQuadVibrators(
                            deviceContext.vibratorManager!!,
                            deviceContext.lowFreqMotor, deviceContext.highFreqMotor,
                            deviceContext.leftTriggerMotor, deviceContext.rightTriggerMotor
                        )
                    } else {
                        rumbleDualVibrators(
                            deviceContext.vibratorManager!!,
                            deviceContext.lowFreqMotor, deviceContext.highFreqMotor
                        )
                    }
                }
                // On Shield devices, we can use their special API to rumble Shield controllers
                else if (handler.sceManager.rumble(deviceContext.inputDevice, deviceContext.lowFreqMotor.toInt(), deviceContext.highFreqMotor.toInt())) {
                    vibrated = true
                }
                // If all else fails, we have to try the old Vibrator API
                else if (deviceContext.vibrator != null) {
                    vibrated = true
                    rumbleSingleVibrator(deviceContext.vibrator!!, deviceContext.lowFreqMotor, deviceContext.highFreqMotor)
                }
            }
        }

        for (i in 0 until handler.usbDeviceContexts.size()) {
            val deviceContext = handler.usbDeviceContexts.valueAt(i)

            if (handler.prefConfig.multiController && !deviceContext.assignedControllerNumber) {
                continue
            }

            if (deviceContext.controllerNumber == controllerNumber) {
                foundMatchingDevice = true
                val device = deviceContext.device ?: continue
                val capabilities = device.capabilities.toInt()
                if (capabilities and MoonBridge.LI_CCAP_RUMBLE.toInt() != 0) {
                    vibrated = true
                    usbRumbleOutput(device).submitBase(BaseRumble(lowFreqMotor, highFreqMotor))
                }
            }
        }

        // We may decide to rumble the device for player 1
        if (controllerNumber.toInt() == 0) {
            // If we didn't find a matching device, it must be the on-screen
            // controls that triggered the rumble. Vibrate the device if
            // the user has requested that behavior.
            if (
                !foundMatchingDevice &&
                allowDeviceFallback &&
                handler.prefConfig.onscreenController &&
                !handler.prefConfig.onlyL3R3 &&
                handler.prefConfig.vibrateOsc
            ) {
                rumbleDeviceFallback(controllerNumber, lowFreqMotor, highFreqMotor)
            } else if (
                foundMatchingDevice &&
                !vibrated &&
                allowDeviceFallback &&
                handler.prefConfig.vibrateFallbackToDevice
            ) {
                // We found a device to vibrate but it didn't have rumble support. The user
                // has requested us to vibrate the device in this case.

                // We cast the unsigned short value to a signed int before multiplying by
                // the preferred strength. The resulting value is capped at 65534 before
                // we cast it back to a short so it doesn't go above 100%.
                val lowFreqMotorAdjusted = Math.min(
                    ((lowFreqMotor.toInt() and 0xffff) * handler.prefConfig.vibrateFallbackToDeviceStrength) / 100,
                    Short.MAX_VALUE * 2
                ).toShort()
                val highFreqMotorAdjusted = Math.min(
                    ((highFreqMotor.toInt() and 0xffff) * handler.prefConfig.vibrateFallbackToDeviceStrength) / 100,
                    Short.MAX_VALUE * 2
                ).toShort()

                rumbleDeviceFallback(
                    controllerNumber,
                    lowFreqMotorAdjusted,
                    highFreqMotorAdjusted
                )
            }
        }
    }

    fun handleRumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short) {
        if (handler.stopped) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (i in 0 until handler.inputDeviceContexts.size()) {
                val deviceContext = handler.inputDeviceContexts.valueAt(i)

                if (handler.prefConfig.multiController && !deviceContext.assignedControllerNumber) {
                    continue
                }

                if (deviceContext.controllerNumber == controllerNumber) {
                    deviceContext.leftTriggerMotor = leftTrigger
                    deviceContext.rightTriggerMotor = rightTrigger

                    if (deviceContext.quadVibrators) {
                        rumbleQuadVibrators(
                            deviceContext.vibratorManager!!,
                            deviceContext.lowFreqMotor, deviceContext.highFreqMotor,
                            deviceContext.leftTriggerMotor, deviceContext.rightTriggerMotor
                        )
                    }
                }
            }
        }

        for (i in 0 until handler.usbDeviceContexts.size()) {
            val deviceContext = handler.usbDeviceContexts.valueAt(i)

            if (handler.prefConfig.multiController && !deviceContext.assignedControllerNumber) {
                continue
            }

            if (deviceContext.controllerNumber == controllerNumber) {
                val device = deviceContext.device ?: continue
                val capabilities = device.capabilities.toInt()
                if (capabilities and MoonBridge.LI_CCAP_TRIGGER_RUMBLE.toInt() != 0) {
                    usbRumbleOutput(device).submitTriggers(
                        TriggerRumble(leftTrigger, rightTrigger)
                    )
                }
            }
        }
    }

    @TargetApi(31)
    fun handleSetControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) {
        if (handler.stopped) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (i in 0 until handler.inputDeviceContexts.size()) {
                val deviceContext = handler.inputDeviceContexts.valueAt(i)

                // Ignore input devices without an RGB LED
                if (deviceContext.controllerNumber == controllerNumber && deviceContext.hasRgbLed) {
                    // Create a new light session if one doesn't already exist
                    if (deviceContext.lightsSession == null) {
                        deviceContext.lightsSession = deviceContext.inputDevice!!.lightsManager.openSession()
                    }

                    // Convert the RGB components into the integer value that LightState uses
                    val argbValue = 0xFF000000.toInt() or
                            ((r.toInt() shl 16) and 0xFF0000) or
                            ((g.toInt() shl 8) and 0xFF00) or
                            (b.toInt() and 0xFF)
                    val lightState = LightState.Builder().setColor(argbValue).build()

                    // Set the RGB value for each RGB-controllable LED on the device
                    val lightsRequestBuilder = LightsRequest.Builder()
                    for (light in deviceContext.inputDevice!!.lightsManager.lights) {
                        if (light.hasRgbControl()) {
                            lightsRequestBuilder.addLight(light, lightState)
                        }
                    }

                    // Apply the LED changes
                    deviceContext.lightsSession!!.requestLights(lightsRequestBuilder.build())
                }
            }
        }
    }

    // This must not be called on the main thread due to risk of ANRs!
    @TargetApi(31)
    fun sendControllerBatteryPacket(context: InputDeviceContext) {
        val currentBatteryStatus: Int
        val currentBatteryCapacity: Float

        // Use the BatteryState object introduced in Android S, if it's available and present.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context.inputDevice!!.batteryState.isPresent) {
            currentBatteryStatus = context.inputDevice!!.batteryState.status
            currentBatteryCapacity = context.inputDevice!!.batteryState.capacity
        } else if (handler.sceManager.isRecognizedDevice(context.inputDevice)) {
            // On the SHIELD Android TV, we can use a proprietary API to access battery/charge state.
            // We will convert it to the same form used by BatteryState to share code.
            val batteryPercentage = handler.sceManager.getBatteryPercentage(context.inputDevice)
            currentBatteryCapacity = if (batteryPercentage < 0) {
                Float.NaN
            } else {
                batteryPercentage / 100f
            }

            val connectionType = handler.sceManager.getConnectionType(context.inputDevice)
            val chargingState = handler.sceManager.getChargingState(context.inputDevice)

            // We can make some assumptions about charge state based on the connection type
            currentBatteryStatus = if (connectionType == SceConnectionType.WIRED || connectionType == SceConnectionType.BOTH) {
                if (batteryPercentage == 100) {
                    BatteryState.STATUS_FULL
                } else if (chargingState == SceChargingState.NOT_CHARGING) {
                    BatteryState.STATUS_NOT_CHARGING
                } else {
                    BatteryState.STATUS_CHARGING
                }
            } else if (connectionType == SceConnectionType.WIRELESS) {
                if (chargingState == SceChargingState.CHARGING) {
                    BatteryState.STATUS_CHARGING
                } else {
                    BatteryState.STATUS_DISCHARGING
                }
            } else {
                // If connection type is unknown, just use the charge state
                if (batteryPercentage == 100) {
                    BatteryState.STATUS_FULL
                } else if (chargingState == SceChargingState.NOT_CHARGING) {
                    BatteryState.STATUS_DISCHARGING
                } else if (chargingState == SceChargingState.CHARGING) {
                    BatteryState.STATUS_CHARGING
                } else {
                    BatteryState.STATUS_UNKNOWN
                }
            }
        } else {
            return
        }

        if (currentBatteryStatus != context.lastReportedBatteryStatus ||
            !areBatteryCapacitiesEqual(currentBatteryCapacity, context.lastReportedBatteryCapacity)
        ) {
            val state: Byte = when (currentBatteryStatus) {
                BatteryState.STATUS_UNKNOWN -> MoonBridge.LI_BATTERY_STATE_UNKNOWN
                BatteryState.STATUS_CHARGING -> MoonBridge.LI_BATTERY_STATE_CHARGING
                BatteryState.STATUS_DISCHARGING -> MoonBridge.LI_BATTERY_STATE_DISCHARGING
                BatteryState.STATUS_NOT_CHARGING -> MoonBridge.LI_BATTERY_STATE_NOT_CHARGING
                BatteryState.STATUS_FULL -> MoonBridge.LI_BATTERY_STATE_FULL
                else -> return
            }

            val percentage: Byte = if (currentBatteryCapacity.isNaN()) {
                MoonBridge.LI_BATTERY_PERCENTAGE_UNKNOWN
            } else {
                (currentBatteryCapacity * 100).toInt().toByte()
            }

            handler.conn.sendControllerBatteryEvent(context.controllerNumber.toByte(), state, percentage)

            context.lastReportedBatteryStatus = currentBatteryStatus
            context.lastReportedBatteryCapacity = currentBatteryCapacity
        }
    }

    companion object {
        private const val NO_DEVICE_FALLBACK_CONTROLLER = -1

        fun areBatteryCapacitiesEqual(first: Float, second: Float): Boolean {
            // With no NaNs involved, it is a simple equality comparison.
            if (!first.isNaN() && !second.isNaN()) {
                return first == second
            } else {
                // If we have a NaN in one or both positions, compare NaN-ness instead.
                // Equality comparisons will always return false for NaN.
                return first.isNaN() == second.isNaN()
            }
        }
    }
}
