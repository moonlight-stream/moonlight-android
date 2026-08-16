package com.limelight.binding.input.driver

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.util.Log

import com.limelight.nvstream.input.ControllerPacket

import java.nio.ByteBuffer

class DualSenseController(
    device: UsbDevice,
    connection: UsbDeviceConnection,
    deviceId: Int,
    listener: UsbDriverListener
) : AbstractDualSenseController(device, connection, deviceId, listener) {

    override val supportsAdaptiveTriggers: Boolean = true

    private fun normalizeThumbStickAxis(value: Int): Float {
        return (2.0f * value / 255.0f) - 1.0f
    }

    private fun normalizeTriggerAxis(value: Int): Float {
        return value / 255.0f
    }

    override fun handleRead(buffer: ByteBuffer): Boolean {
        if (buffer.remaining() < 64) {
            Log.d("DualSenseController", "No DualSense input: ${buffer.remaining()}")
            return false
        }

        val reportId = buffer.get(0).toInt() and 0xFF
        if (reportId != 0x01) {
            Log.d("DualSenseController", "Unexpected report ID: 0x${Integer.toHexString(reportId)}")
        }

        buffer.get()

        val capacity = buffer.capacity()
        if (capacity < 11) {
            Log.w("DualSenseController", "Buffer too small for button data")
            return false
        }

        // Process D-pad
        val dpad = buffer.get(8).toInt() and 0x0F
        setButtonFlag(ControllerPacket.UP_FLAG, if (dpad == 0 || dpad == 1 || dpad == 7) 0x01 else 0)
        setButtonFlag(ControllerPacket.DOWN_FLAG, if (dpad == 3 || dpad == 4 || dpad == 5) 0x02 else 0)
        setButtonFlag(ControllerPacket.LEFT_FLAG, if (dpad == 5 || dpad == 6 || dpad == 7) 0x04 else 0)
        setButtonFlag(ControllerPacket.RIGHT_FLAG, if (dpad == 1 || dpad == 2 || dpad == 3) 0x08 else 0)

        // ABXY
        val b8 = buffer.get(8).toInt()
        setButtonFlag(ControllerPacket.A_FLAG, b8 and 0x20)
        setButtonFlag(ControllerPacket.B_FLAG, b8 and 0x40)
        setButtonFlag(ControllerPacket.X_FLAG, b8 and 0x10)
        setButtonFlag(ControllerPacket.Y_FLAG, b8 and 0x80)

        // LB/RB
        val b9 = buffer.get(9).toInt()
        setButtonFlag(ControllerPacket.LB_FLAG, b9 and 0x01)
        setButtonFlag(ControllerPacket.RB_FLAG, b9 and 0x02)
        setButtonFlag(ControllerPacket.BACK_FLAG, b9 and 0x10)
        setButtonFlag(ControllerPacket.PLAY_FLAG, b9 and 0x20)
        setButtonFlag(ControllerPacket.LS_CLK_FLAG, b9 and 0x40)
        setButtonFlag(ControllerPacket.RS_CLK_FLAG, b9 and 0x80)

        // PS button
        val b10 = buffer.get(10).toInt()
        setButtonFlag(ControllerPacket.SPECIAL_BUTTON_FLAG, b10 and 0x01)
        setButtonFlag(ControllerPacket.MISC_FLAG, b10 and 0x04)
        setButtonFlag(ControllerPacket.TOUCHPAD_FLAG, b10 and 0x02)

        // Process analog sticks
        val axes0 = if (capacity > 1) buffer.get(1).toInt() and 0xFF else 0x80
        val axes1 = if (capacity > 2) buffer.get(2).toInt() and 0xFF else 0x80
        val axes2 = if (capacity > 3) buffer.get(3).toInt() and 0xFF else 0x80
        val axes3 = if (capacity > 4) buffer.get(4).toInt() and 0xFF else 0x80
        val axes4 = if (capacity > 5) buffer.get(5).toInt() and 0xFF else 0
        val axes5 = if (capacity > 6) buffer.get(6).toInt() and 0xFF else 0

        leftStickX = normalizeThumbStickAxis(axes0)
        leftStickY = normalizeThumbStickAxis(axes1)
        rightStickX = normalizeThumbStickAxis(axes2)
        rightStickY = normalizeThumbStickAxis(axes3)
        leftTrigger = normalizeTriggerAxis(axes4)
        rightTrigger = normalizeTriggerAxis(axes5)

        // IMU data
        val GYRO_SCALE = 2000.0f / 32768.0f
        val ACCEL_SCALE = 4.0f / 32768.0f
        val G_TO_MS2 = 9.81f

        if (capacity < 27) {
            Log.w("DualSenseController", "Buffer too small for IMU data: $capacity")
            return false
        }

        try {
            val gyrox = buffer.getShort(16).toInt()
            val gyroy = buffer.getShort(18).toInt()
            val gyroz = buffer.getShort(20).toInt()

            val accelx = buffer.getShort(22).toInt()
            val accely = buffer.getShort(24).toInt()
            val accelz = buffer.getShort(26).toInt()

            gyroX = gyrox * GYRO_SCALE
            gyroY = gyroy * GYRO_SCALE
            gyroZ = gyroz * GYRO_SCALE

            accelX = accelx * ACCEL_SCALE * G_TO_MS2
            accelY = accely * ACCEL_SCALE * G_TO_MS2
            accelZ = accelz * ACCEL_SCALE * G_TO_MS2
        } catch (e: IndexOutOfBoundsException) {
            Log.w("DualSenseController", "Failed to read IMU data", e)
            gyroX = 0f; gyroY = 0f; gyroZ = 0f
            accelX = 0f; accelY = 0f; accelZ = 0f
        }

        return true
    }

    override fun doInit(): Boolean {
        Log.d("DualSenseController", "doInit")
        sendCommand(getDualSenseInit())
        return true
    }

    override fun rumble(lowFreqMotor: Short, highFreqMotor: Short) {
        sendCommand(DualSenseOutputReport.rumble(lowFreqMotor, highFreqMotor))
    }

    override fun rumbleTriggers(leftTrigger: Short, rightTrigger: Short) {
        // DS5 supports trigger rumble but implementation is complex
    }

    override fun setAdaptiveTriggers(
        eventFlags: Byte,
        typeLeft: Byte,
        typeRight: Byte,
        left: ByteArray,
        right: ByteArray
    ) {
        if (left.size != DualSenseOutputReport.EFFECT_PAYLOAD_SIZE ||
            right.size != DualSenseOutputReport.EFFECT_PAYLOAD_SIZE
        ) {
            Log.w("DualSenseController", "Ignoring malformed adaptive trigger payload")
            return
        }

        sendCommand(
            DualSenseOutputReport.adaptiveTriggers(
                eventFlags,
                typeLeft,
                typeRight,
                left,
                right
            )
        )
    }

    override fun sendCommand(data: ByteArray) {
        if (outEndpt == null) {
            Log.w("DualSenseController", "Cannot send command: invalid parameters")
            return
        }
        synchronized(outputLock) {
            // Re-check under the lock: stop() sets this after sending its final
            // clear, so no stale report may follow it.
            if (outputClosed) {
                return
            }
            Log.d("DualSenseController", "sendCommand")
            val res = connection.bulkTransfer(outEndpt, data, data.size, 1000)
            if (res != data.size) {
                Log.w("DualSenseController", "Command transfer failed: expected ${data.size}, got $res")
            }
        }
    }

    private fun getDualSenseInit(): ByteArray {
        return byteArrayOf(
            0x02, // Report ID
            (0x10 or 0x20 or 0x40 or 0x80).toByte(), // valid_flag0
            0xf7.toByte(), // valid_flag1
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, // mute_button_led
            0x10, // power_save_control
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // R2 trigger effect
            0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // L2 trigger effect
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x02, 0x00, 0x02, 0x00,
            0x00, // player leds
            0x78, 0x78, 0xEF.toByte() // RGB values
        )
    }

    companion object {
        private val SUPPORTED_VENDORS = intArrayOf(0x054C, 0x1532)
        private val SUPPORTED_PRODUCTS = intArrayOf(0x0CE6, 0x0DF2, 0x100b, 0x100c)

        @JvmStatic
        fun canClaimDevice(device: UsbDevice?): Boolean {
            if (device == null) return false
            for (vid in SUPPORTED_VENDORS) {
                for (pid in SUPPORTED_PRODUCTS) {
                    if (device.vendorId == vid && device.productId == pid && device.interfaceCount >= 1) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
