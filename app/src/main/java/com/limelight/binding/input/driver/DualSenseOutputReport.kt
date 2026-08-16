package com.limelight.binding.input.driver

internal object DualSenseOutputReport {
    const val EFFECT_PAYLOAD_SIZE = 10
    // Per the DualSense protocol, 0x05 is the "effect off" opcode; 0x00 is not a
    // recognized mode and leaves a previously set effect engaged.
    const val EFFECT_TYPE_OFF: Byte = 0x05
    const val RIGHT_TRIGGER_FLAG = 0x04
    const val LEFT_TRIGGER_FLAG = 0x08
    const val BOTH_TRIGGER_FLAGS = RIGHT_TRIGGER_FLAG or LEFT_TRIGGER_FLAG

    private const val REPORT_SIZE = 48
    private const val REPORT_ID = 0x02
    private const val RIGHT_TRIGGER_TYPE_OFFSET = 11
    private const val RIGHT_TRIGGER_PAYLOAD_OFFSET = 12
    private const val LEFT_TRIGGER_TYPE_OFFSET = 22
    private const val LEFT_TRIGGER_PAYLOAD_OFFSET = 23

    fun rumble(lowFreqMotor: Short, highFreqMotor: Short): ByteArray =
        ByteArray(REPORT_SIZE).apply {
            this[0] = REPORT_ID.toByte()
            this[1] = 0x03 // Enable compatible vibration motors only.
            this[3] = (highFreqMotor.toInt() ushr 8).toByte()
            this[4] = (lowFreqMotor.toInt() ushr 8).toByte()
        }

    fun adaptiveTriggers(
        eventFlags: Byte,
        typeLeft: Byte,
        typeRight: Byte,
        left: ByteArray,
        right: ByteArray
    ): ByteArray {
        require(left.size == EFFECT_PAYLOAD_SIZE) { "Invalid left adaptive trigger payload size" }
        require(right.size == EFFECT_PAYLOAD_SIZE) { "Invalid right adaptive trigger payload size" }

        return ByteArray(REPORT_SIZE).apply {
            this[0] = REPORT_ID.toByte()
            this[1] = (eventFlags.toInt() and BOTH_TRIGGER_FLAGS).toByte()
            this[RIGHT_TRIGGER_TYPE_OFFSET] = typeRight
            right.copyInto(this, RIGHT_TRIGGER_PAYLOAD_OFFSET)
            this[LEFT_TRIGGER_TYPE_OFFSET] = typeLeft
            left.copyInto(this, LEFT_TRIGGER_PAYLOAD_OFFSET)
        }
    }

    fun clearAdaptiveTriggers(): ByteArray = adaptiveTriggers(
        BOTH_TRIGGER_FLAGS.toByte(),
        EFFECT_TYPE_OFF,
        EFFECT_TYPE_OFF,
        ByteArray(EFFECT_PAYLOAD_SIZE),
        ByteArray(EFFECT_PAYLOAD_SIZE)
    )
}
