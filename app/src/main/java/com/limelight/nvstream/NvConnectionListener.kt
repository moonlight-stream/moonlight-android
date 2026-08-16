package com.limelight.nvstream

interface NvConnectionListener {
    fun stageStarting(stage: String)
    fun stageComplete(stage: String)
    fun stageFailed(stage: String, portFlags: Int, errorCode: Int)

    fun connectionStarted()
    fun connectionTerminated(errorCode: Int)
    fun connectionStatusUpdate(connectionStatus: Int)

    fun displayMessage(message: String)
    fun displayTransientMessage(message: String)

    fun rumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short)
    fun rumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short)
    fun setAdaptiveTriggers(
        controllerNumber: Short,
        eventFlags: Byte,
        typeLeft: Byte,
        typeRight: Byte,
        left: ByteArray,
        right: ByteArray
    )

    fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?)

    fun setMotionEventState(controllerNumber: Short, motionType: Byte, reportRateHz: Short)

    fun setControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte)

    fun onResolutionChanged(width: Int, height: Int)

    fun onCursorUpdate(
        flags: Int,
        shapeId: Int,
        width: Int,
        height: Int,
        hotspotX: Int,
        hotspotY: Int,
        bgraPixels: ByteArray?
    )
}
