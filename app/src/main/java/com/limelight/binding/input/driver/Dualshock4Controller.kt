package com.limelight.binding.input.driver

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.util.Log

import com.limelight.LimeLog
import com.limelight.nvstream.input.ControllerPacket

import java.nio.ByteBuffer

class Dualshock4Controller(
    device: UsbDevice,
    connection: UsbDeviceConnection,
    deviceId: Int,
    listener: UsbDriverListener
) : AbstractDualSenseController(device, connection, deviceId, listener) {

    private fun normalizeThumbStickAxis(value: Int): Float {
        return (2.0f * value / 255.0f) - 1.0f
    }

    private fun normalizeTriggerAxis(value: Int): Float {
        return value / 255.0f
    }

    override fun handleRead(buffer: ByteBuffer): Boolean {
        if (buffer.remaining() < 64) {
            Log.d("Dualshock4Controller", "No Dualshock4Controller input: ${buffer.remaining()}")
            return false
        }

        val reportId = buffer.get(0).toInt() and 0xFF
        if (reportId != 0x01 && reportId != 0x11) {
            Log.d("Dualshock4Controller", "Unexpected report ID: 0x${Integer.toHexString(reportId)}")
        }

        buffer.get()

        if (buffer.remaining() < 9) {
            Log.w("Dualshock4Controller", "Buffer too small for button data")
            return false
        }

        val capacity = buffer.capacity()

        // Process D-pad
        val dpad = if (capacity > 5) buffer.get(5).toInt() and 0x0F else 0
        setButtonFlag(ControllerPacket.UP_FLAG, if (dpad == 0 || dpad == 1 || dpad == 7) 0x01 else 0)
        setButtonFlag(ControllerPacket.DOWN_FLAG, if (dpad == 3 || dpad == 4 || dpad == 5) 0x02 else 0)
        setButtonFlag(ControllerPacket.LEFT_FLAG, if (dpad == 5 || dpad == 6 || dpad == 7) 0x04 else 0)
        setButtonFlag(ControllerPacket.RIGHT_FLAG, if (dpad == 1 || dpad == 2 || dpad == 3) 0x08 else 0)

        // ABXY
        if (capacity > 5) {
            val b5 = buffer.get(5).toInt()
            setButtonFlag(ControllerPacket.A_FLAG, b5 and 0x20)
            setButtonFlag(ControllerPacket.B_FLAG, b5 and 0x40)
            setButtonFlag(ControllerPacket.X_FLAG, b5 and 0x10)
            setButtonFlag(ControllerPacket.Y_FLAG, b5 and 0x80)
        }

        // LB/RB
        if (capacity > 6) {
            val b6 = buffer.get(6).toInt()
            setButtonFlag(ControllerPacket.LB_FLAG, b6 and 0x01)
            setButtonFlag(ControllerPacket.RB_FLAG, b6 and 0x02)
            setButtonFlag(ControllerPacket.BACK_FLAG, b6 and 0x10)
            setButtonFlag(ControllerPacket.PLAY_FLAG, b6 and 0x20)
            setButtonFlag(ControllerPacket.LS_CLK_FLAG, b6 and 0x40)
            setButtonFlag(ControllerPacket.RS_CLK_FLAG, b6 and 0x80)
        }

        // PS button
        if (capacity > 7) {
            val b7 = buffer.get(7).toInt()
            setButtonFlag(ControllerPacket.SPECIAL_BUTTON_FLAG, b7 and 0x01)
            setButtonFlag(ControllerPacket.TOUCHPAD_FLAG, b7 and 0x02)
        }

        // Process analog sticks
        val axes0 = if (capacity > 1) buffer.get(1).toInt() and 0xFF else 0x80
        val axes1 = if (capacity > 2) buffer.get(2).toInt() and 0xFF else 0x80
        val axes2 = if (capacity > 3) buffer.get(3).toInt() and 0xFF else 0x80
        val axes3 = if (capacity > 4) buffer.get(4).toInt() and 0xFF else 0x80
        val axes4 = if (capacity > 8) buffer.get(8).toInt() and 0xFF else 0
        val axes5 = if (capacity > 9) buffer.get(9).toInt() and 0xFF else 0

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

        if (capacity < 24) {
            Log.w("Dualshock4Controller", "Buffer too small for IMU data: $capacity")
            return false
        }

        try {
            val gyrox = buffer.getShort(13).toInt()
            val gyroy = buffer.getShort(15).toInt()
            val gyroz = buffer.getShort(17).toInt()

            val accelx = buffer.getShort(19).toInt()
            val accely = buffer.getShort(21).toInt()
            val accelz = buffer.getShort(23).toInt()

            gyroX = gyrox * GYRO_SCALE
            gyroY = gyroy * GYRO_SCALE
            gyroZ = gyroz * GYRO_SCALE

            accelX = accelx * ACCEL_SCALE * G_TO_MS2
            accelY = accely * ACCEL_SCALE * G_TO_MS2
            accelZ = accelz * ACCEL_SCALE * G_TO_MS2
        } catch (e: IndexOutOfBoundsException) {
            Log.w("Dualshock4Controller", "Failed to read IMU data", e)
            gyroX = 0f; gyroY = 0f; gyroZ = 0f
            accelX = 0f; accelY = 0f; accelZ = 0f
        }

        return true
    }

    override fun doInit(): Boolean {
        Log.d("Dualshock4Controller", "doInit")
        sendCommand(getInitData())
        return true
    }

    override fun rumble(lowFreqMotor: Short, highFreqMotor: Short) {
        val report = ByteArray(32)
        report[0] = 0x05
        report[1] = 0x01
        report[2] = 0x04
        report[4] = (highFreqMotor.toInt() ushr 8).toByte()
        report[5] = (lowFreqMotor.toInt() ushr 8).toByte()
        report[6] = 0x78
        report[7] = 0x78
        report[8] = 0xEF.toByte()
        sendCommand(report)
    }

    override fun rumbleTriggers(leftTrigger: Short, rightTrigger: Short) {
        // DS4 doesn't support trigger rumble
    }

    override fun sendCommand(data: ByteArray) {
        if (outEndpt == null) {
            Log.w("Dualshock4Controller", "Cannot send command: invalid parameters")
            return
        }
        synchronized(outputLock) {
            // Re-check under the lock: stop() sets this after sending its final
            // clear, so no stale report may follow it.
            if (outputClosed) {
                return
            }
            Log.d("Dualshock4Controller", "sendCommand")
            val res = connection.bulkTransfer(outEndpt, data, data.size, 1000)
            if (res != data.size) {
                Log.w("Dualshock4Controller", "Command transfer failed: expected ${data.size}, got $res")
            }
        }
    }

    private fun getInitData(): ByteArray {
        val report = ByteArray(32)
        report[0] = 0x05
        report[1] = 0x02
        report[2] = 0x04
        report[4] = 0x00
        report[5] = 0x00
        report[6] = 0x78
        report[7] = 0x78
        report[8] = 0xEF.toByte()
        return report
    }

    companion object {
        private val SUPPORTED_VENDORS = intArrayOf(0x054C)
        private val SUPPORTED_PRODUCTS = intArrayOf(0x05c4, 0x09cc)

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
