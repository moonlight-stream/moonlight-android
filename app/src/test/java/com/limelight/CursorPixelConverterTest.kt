package com.limelight

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CursorPixelConverterTest {
    @Test
    fun convertsBgraPixelsToArgbColors() {
        val pixels = byteArrayOf(
            0x33, 0x22, 0x11, 0x44,
            0xFF.toByte(), 0x80.toByte(), 0x00, 0xFF.toByte()
        )

        assertArrayEquals(
            intArrayOf(0x44112233, 0xFF0080FF.toInt()),
            CursorPixelConverter.bgraToArgb(pixels)
        )
    }

    @Test
    fun rejectsTruncatedPixelPayload() {
        assertThrows(IllegalArgumentException::class.java) {
            CursorPixelConverter.bgraToArgb(byteArrayOf(1, 2, 3))
        }
    }
}
