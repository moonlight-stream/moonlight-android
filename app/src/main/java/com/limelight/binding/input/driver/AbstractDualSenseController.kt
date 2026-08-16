package com.limelight.binding.input.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.os.SystemClock
import android.util.Log

import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.jni.MoonBridge

import java.nio.ByteBuffer
import java.nio.ByteOrder

abstract class AbstractDualSenseController(
    protected val device: UsbDevice,
    protected val connection: UsbDeviceConnection,
    deviceId: Int,
    listener: UsbDriverListener
) : AbstractController(deviceId, listener, device.vendorId, device.productId) {

    private var inputThread: Thread? = null
    private var stopped = false

    // Serializes output reports with the teardown sequence so no queued effect can
    // be sent after the final clear or alongside connection.close().
    protected val outputLock = Any()

    @Volatile
    protected var outputClosed = false

    protected var inEndpt: UsbEndpoint? = null
    protected var outEndpt: UsbEndpoint? = null

    // IMU data fields
    protected var gyroX = 0f
    protected var gyroY = 0f
    protected var gyroZ = 0f
    protected var accelX = 0f
    protected var accelY = 0f
    protected var accelZ = 0f

    init {
        type = MoonBridge.LI_CTYPE_PS
        capabilities = (MoonBridge.LI_CCAP_GYRO.toInt() or MoonBridge.LI_CCAP_ACCEL.toInt() or MoonBridge.LI_CCAP_RUMBLE.toInt()).toShort()
        buttonFlags = ControllerPacket.A_FLAG or ControllerPacket.B_FLAG or ControllerPacket.X_FLAG or ControllerPacket.Y_FLAG or
                ControllerPacket.UP_FLAG or ControllerPacket.DOWN_FLAG or ControllerPacket.LEFT_FLAG or ControllerPacket.RIGHT_FLAG or
                ControllerPacket.LB_FLAG or ControllerPacket.RB_FLAG or
                ControllerPacket.LS_CLK_FLAG or ControllerPacket.RS_CLK_FLAG or
                ControllerPacket.BACK_FLAG or ControllerPacket.PLAY_FLAG or ControllerPacket.SPECIAL_BUTTON_FLAG
        supportedButtonFlags = buttonFlags
    }

    private fun createInputThread(): Thread {
        return Thread {
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                return@Thread
            }

            notifyDeviceAdded()

            while (!Thread.currentThread().isInterrupted && !stopped) {
                val buffer = ByteArray(64)
                var res = -1

                do {
                    if (stopped || Thread.currentThread().isInterrupted) {
                        res = -1
                        break
                    }

                    val lastMillis = SystemClock.uptimeMillis()
                    if (inEndpt == null) {
                        Log.w("DualSenseController", "Connection or endpoint is null")
                        res = -1
                        break
                    }
                    res = connection.bulkTransfer(inEndpt, buffer, buffer.size, 3000)

                    if (res == 0) {
                        res = -1
                    }

                    if (res == -1 && SystemClock.uptimeMillis() - lastMillis < 1000) {
                        Log.d("DualSenseController", "Detected device I/O error")
                        this@AbstractDualSenseController.stop()
                        break
                    }
                } while (res == -1 && !Thread.currentThread().isInterrupted && !stopped)

                if (res == -1 || stopped || Thread.currentThread().isInterrupted) {
                    break
                }

                if (res > 0 && handleRead(ByteBuffer.wrap(buffer, 0, res).order(ByteOrder.LITTLE_ENDIAN))) {
                    reportInput()
                    reportMotion()
                }
            }
        }
    }

    private val ifaces = mutableListOf<UsbInterface>()

    override fun start(): Boolean {
        ifaces.clear()
        Log.d("DualSenseController", "start")
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (!connection.claimInterface(iface, true)) {
                Log.d("DualSenseController", "Failed to claim interface: $i")
                return false
            } else {
                ifaces.add(iface)
            }
        }
        Log.d("DualSenseController", "getInterfaceCount:" + device.interfaceCount)

        val iface = findInterface(device) ?: run {
            Log.e("DualSenseController", "Failed to find interface")
            return false
        }

        for (i in 0 until iface.endpointCount) {
            val endpt = iface.getEndpoint(i)
            if (endpt.direction == UsbConstants.USB_DIR_OUT) {
                if (outEndpt != null) {
                    Log.d("DualSenseController", "Found duplicate OUT endpoint")
                    return false
                }
                outEndpt = endpt
            } else if (endpt.direction == UsbConstants.USB_DIR_IN) {
                if (inEndpt != null) {
                    Log.d("DualSenseController", "Found duplicate IN endpoint")
                    return false
                }
                inEndpt = endpt
            }
        }
        Log.d("DualSenseController", "inEndpt: $inEndpt")
        Log.d("DualSenseController", "outEndpt: $outEndpt")

        if (inEndpt == null || outEndpt == null) {
            Log.d("DualSenseController", "Missing required endpoint")
            return false
        }

        if (!doInit()) {
            return false
        }

        inputThread = createInputThread()
        inputThread!!.start()
        return true
    }

    override fun stop() {
        synchronized(this) {
            if (stopped) return
            stopped = true
        }

        // Hold the output lock across the final clear so a report already queued by
        // the rumble worker cannot re-engage an effect after this point.
        synchronized(outputLock) {
            try {
                rumble(0, 0)
                setAdaptiveTriggers(
                    DualSenseOutputReport.BOTH_TRIGGER_FLAGS.toByte(),
                    DualSenseOutputReport.EFFECT_TYPE_OFF,
                    DualSenseOutputReport.EFFECT_TYPE_OFF,
                    ByteArray(DualSenseOutputReport.EFFECT_PAYLOAD_SIZE),
                    ByteArray(DualSenseOutputReport.EFFECT_PAYLOAD_SIZE)
                )
            } catch (e: Exception) {
                Log.d("DualSenseController", "Failed to clear controller output during stop", e)
            } finally {
                outputClosed = true
            }
        }

        inputThread?.let {
            it.interrupt()
            try {
                it.join(1000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            inputThread = null
        }

        if (ifaces.isNotEmpty()) {
            synchronized(ifaces) {
                for (iface in ifaces) {
                    try {
                        connection.releaseInterface(iface)
                    } catch (e: Exception) {
                        Log.w("DualSenseController", "Failed to release interface", e)
                    }
                }
                ifaces.clear()
            }
        }

        synchronized(outputLock) {
            try {
                connection.close()
            } catch (e: Exception) {
                Log.w("DualSenseController", "Failed to close connection", e)
            }
        }

        notifyDeviceRemoved()
    }

    protected fun reportMotion() {
        notifyControllerMotion(MoonBridge.LI_MOTION_TYPE_GYRO, gyroX, gyroY, gyroZ)
        notifyControllerMotion(MoonBridge.LI_MOTION_TYPE_ACCEL, accelX, accelY, accelZ)
    }

    protected abstract fun handleRead(buffer: ByteBuffer): Boolean

    protected abstract fun doInit(): Boolean

    protected abstract fun sendCommand(data: ByteArray)

    companion object {
        private fun findInterface(device: UsbDevice): UsbInterface? {
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass == UsbConstants.USB_CLASS_HID && intf.endpointCount >= 2) {
                    Log.d("DualSenseController", "Found HID interface: $i")
                    return intf
                }
            }
            return null
        }
    }
}
