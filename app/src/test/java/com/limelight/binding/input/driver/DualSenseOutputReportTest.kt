package com.limelight.binding.input.driver

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DualSenseOutputReportTest {
    @Test
    fun adaptiveTriggersMapsProtocolOrderToUsbReport() {
        val left = ByteArray(DualSenseOutputReport.EFFECT_PAYLOAD_SIZE) { (0x20 + it).toByte() }
        val right = ByteArray(DualSenseOutputReport.EFFECT_PAYLOAD_SIZE) { (0x40 + it).toByte() }

        val report = DualSenseOutputReport.adaptiveTriggers(
            DualSenseOutputReport.BOTH_TRIGGER_FLAGS.toByte(),
            0x11,
            0x22,
            left,
            right
        )

        assertEquals(48, report.size)
        assertEquals(0x02, report[0].toInt() and 0xFF)
        assertEquals(0x0C, report[1].toInt() and 0xFF)
        assertEquals(0x22, report[11].toInt() and 0xFF)
        assertArrayEquals(right, report.copyOfRange(12, 22))
        assertEquals(0x11, report[22].toInt() and 0xFF)
        assertArrayEquals(left, report.copyOfRange(23, 33))
    }

    @Test
    fun clearAdaptiveTriggersSendsOffOpcodeWithZeroedPayloads() {
        val report = DualSenseOutputReport.clearAdaptiveTriggers()

        assertEquals(0x0C, report[1].toInt() and 0xFF)
        assertEquals(0, report[3].toInt())
        assertEquals(0, report[4].toInt())
        // 0x05 is the DualSense "effect off" opcode; 0x00 is not a recognized mode.
        assertEquals(0x05, report[11].toInt() and 0xFF)
        assertEquals(0x05, report[22].toInt() and 0xFF)
        assertArrayEquals(ByteArray(10), report.copyOfRange(12, 22))
        assertArrayEquals(ByteArray(10), report.copyOfRange(23, 33))
    }

    @Test
    fun rumbleDoesNotMarkAdaptiveTriggerFieldsValid() {
        val report = DualSenseOutputReport.rumble(0x5500, 0x3300)

        assertEquals(0x03, report[1].toInt() and 0xFF)
        assertEquals(0x33, report[3].toInt() and 0xFF)
        assertEquals(0x55, report[4].toInt() and 0xFF)
    }
}
