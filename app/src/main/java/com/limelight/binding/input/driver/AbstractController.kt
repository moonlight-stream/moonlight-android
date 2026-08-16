package com.limelight.binding.input.driver

abstract class AbstractController(
    private val deviceId: Int,
    private val listener: UsbDriverListener,
    private val vendorId: Int,
    private val productId: Int
) {
    protected var buttonFlags = 0
    var supportedButtonFlags = 0
        protected set
    protected var leftTrigger = 0f
    protected var rightTrigger = 0f
    protected var rightStickX = 0f
    protected var rightStickY = 0f
    protected var leftStickX = 0f
    protected var leftStickY = 0f
    var capabilities: Short = 0
        protected set
    var type: Byte = 0
        protected set

    fun getControllerId(): Int = deviceId

    fun getVendorId(): Int = vendorId

    fun getProductId(): Int = productId

    protected fun setButtonFlag(buttonFlag: Int, data: Int) {
        if (data != 0) {
            buttonFlags = buttonFlags or buttonFlag
        } else {
            buttonFlags = buttonFlags and buttonFlag.inv()
        }
    }

    protected fun reportInput() {
        listener.reportControllerState(
            deviceId, buttonFlags, leftStickX, leftStickY,
            rightStickX, rightStickY, leftTrigger, rightTrigger
        )
    }

    abstract fun start(): Boolean
    abstract fun stop()

    abstract fun rumble(lowFreqMotor: Short, highFreqMotor: Short)

    abstract fun rumbleTriggers(leftTrigger: Short, rightTrigger: Short)

    open val supportsAdaptiveTriggers: Boolean = false

    open fun setAdaptiveTriggers(
        eventFlags: Byte,
        typeLeft: Byte,
        typeRight: Byte,
        left: ByteArray,
        right: ByteArray
    ) = Unit

    protected fun notifyDeviceRemoved() {
        listener.deviceRemoved(this)
    }

    protected fun notifyDeviceAdded() {
        listener.deviceAdded(this)
    }

    protected fun notifyControllerMotion(motionType: Byte, x: Float, y: Float, z: Float) {
        listener.reportControllerMotion(deviceId, motionType, x, y, z)
    }
}
