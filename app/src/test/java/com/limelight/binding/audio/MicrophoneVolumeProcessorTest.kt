package com.limelight.binding.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.abs

class MicrophoneVolumeProcessorTest {

    @Test
    fun disabledProcessingLeavesPcmUnchanged() {
        val pcm = pcmOf(-32768, -1000, 0, 1000, 32767)
        val original = pcm.copyOf()
        val processor = MicrophoneVolumeProcessor()

        processor.configure(
            enabled = false,
            gainEnabled = true,
            gainDb = 20,
            balanceEnabled = false,
            balanceTargetPercent = 50,
            voiceEnhancementEnabled = true
        )
        processor.processFrame(pcm, 0, pcm.size)

        assertArrayEquals(original, pcm)
    }

    @Test
    fun fixedGainAppliesConfiguredDecibels() {
        val pcm = pcmOf(-1000, 0, 1000)
        val processor = MicrophoneVolumeProcessor()

        processor.configure(
            enabled = true,
            gainEnabled = true,
            gainDb = 6,
            balanceEnabled = false,
            balanceTargetPercent = 50,
            voiceEnhancementEnabled = false
        )
        processor.processFrame(pcm, 0, pcm.size)

        assertEquals(-1995, sampleAt(pcm, 0))
        assertEquals(0, sampleAt(pcm, 1))
        assertEquals(1995, sampleAt(pcm, 2))
    }

    @Test
    fun fixedGainOnlyTouchesRequestedRange() {
        val pcm = pcmOf(123, -1000, 1000, 456)
        val processor = MicrophoneVolumeProcessor()

        processor.configure(
            enabled = true,
            gainEnabled = true,
            gainDb = 6,
            balanceEnabled = false,
            balanceTargetPercent = 50,
            voiceEnhancementEnabled = false
        )
        processor.processFrame(pcm, 2, 4)

        assertEquals(123, sampleAt(pcm, 0))
        assertEquals(-1995, sampleAt(pcm, 1))
        assertEquals(1995, sampleAt(pcm, 2))
        assertEquals(456, sampleAt(pcm, 3))
    }

    @Test
    fun balanceLimiterKeepsStartupTransientBelowMinusOneDbfs() {
        val pcm = pcmOf(*IntArray(MicrophoneConfig.SAMPLES_PER_FRAME) { Short.MAX_VALUE.toInt() })
        val processor = MicrophoneVolumeProcessor()

        processor.configure(
            enabled = true,
            gainEnabled = false,
            gainDb = 0,
            balanceEnabled = true,
            balanceTargetPercent = 100,
            voiceEnhancementEnabled = false
        )
        processor.processFrame(pcm, 0, pcm.size)

        val peak = (0 until MicrophoneConfig.SAMPLES_PER_FRAME)
            .maxOf { abs(sampleAt(pcm, it)) }
        assertTrue("peak=$peak", peak <= 29_204)
    }

    @Test
    fun balanceLimiterHandlesEveryPartialBlockSize() {
        for (sampleCount in 1 until 32) {
            val pcm = pcmOf(*IntArray(sampleCount) {
                if (it % 2 == 0) Short.MAX_VALUE.toInt() else Short.MIN_VALUE.toInt()
            })
            val processor = MicrophoneVolumeProcessor()

            processor.configure(
                enabled = true,
                gainEnabled = false,
                gainDb = 0,
                balanceEnabled = true,
                balanceTargetPercent = 100,
                voiceEnhancementEnabled = false
            )
            processor.processFrame(pcm, 0, pcm.size)

            val peak = (0 until sampleCount).maxOf { abs(sampleAt(pcm, it)) }
            assertTrue("sampleCount=$sampleCount peak=$peak", peak <= 29_204)
        }
    }

    @Test
    fun balanceCompressorSettlesWithoutDuplicateLimiterReduction() {
        var pcm = ByteArray(0)
        val processor = MicrophoneVolumeProcessor()

        processor.configure(
            enabled = true,
            gainEnabled = false,
            gainDb = 0,
            balanceEnabled = true,
            balanceTargetPercent = 100,
            voiceEnhancementEnabled = false
        )
        repeat(50) {
            pcm = pcmOf(*IntArray(MicrophoneConfig.SAMPLES_PER_FRAME) { Short.MAX_VALUE.toInt() })
            processor.processFrame(pcm, 0, pcm.size)
        }

        val settledSample = sampleAt(pcm, MicrophoneConfig.SAMPLES_PER_FRAME - 1)
        assertTrue("settledSample=$settledSample", settledSample in 20_000..21_500)
    }

    @Test
    fun voiceEnhancementChangesBothEnabledModes() {
        for (gainEnabled in listOf(true, false)) {
            val source = pcmOf(*IntArray(64) { if (it % 2 == 0) 2000 else -2000 })
            val withoutEnhancement = source.copyOf()
            val withEnhancement = source.copyOf()

            configuredProcessor(gainEnabled, voiceEnhancementEnabled = false)
                .processFrame(withoutEnhancement, 0, withoutEnhancement.size)
            configuredProcessor(gainEnabled, voiceEnhancementEnabled = true)
                .processFrame(withEnhancement, 0, withEnhancement.size)

            assertFalse(
                "voice enhancement did not affect ${if (gainEnabled) "gain" else "balance"} mode",
                withoutEnhancement.contentEquals(withEnhancement)
            )
        }
    }

    @Test
    fun processFrameValidatesPcmRangeBeforeIndexing() {
        val processor = configuredProcessor(gainEnabled = true, voiceEnhancementEnabled = false)
        val pcm = ByteArray(4)

        processor.processFrame(pcm, pcm.size, 0)
        assertThrows(IllegalArgumentException::class.java) { processor.processFrame(pcm, -1, 2) }
        assertThrows(IllegalArgumentException::class.java) { processor.processFrame(pcm, 0, -2) }
        assertThrows(IllegalArgumentException::class.java) { processor.processFrame(pcm, 0, 1) }
        assertThrows(IllegalArgumentException::class.java) { processor.processFrame(pcm, 2, 4) }
        assertThrows(IllegalArgumentException::class.java) {
            processor.processFrame(pcm, Int.MAX_VALUE, 2)
        }
    }

    private fun configuredProcessor(
        gainEnabled: Boolean,
        voiceEnhancementEnabled: Boolean
    ): MicrophoneVolumeProcessor {
        return MicrophoneVolumeProcessor().also { processor ->
            processor.configure(
                enabled = true,
                gainEnabled = gainEnabled,
                gainDb = 0,
                balanceEnabled = !gainEnabled,
                balanceTargetPercent = 50,
                voiceEnhancementEnabled = voiceEnhancementEnabled
            )
        }
    }

    private fun pcmOf(vararg samples: Int): ByteArray {
        return ByteArray(samples.size * 2).also { data ->
            samples.forEachIndexed { index, sample ->
                data[index * 2] = (sample and 0xFF).toByte()
                data[index * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
            }
        }
    }

    private fun sampleAt(data: ByteArray, sampleIndex: Int): Int {
        val byteIndex = sampleIndex * 2
        return (data[byteIndex].toInt() and 0xFF) or (data[byteIndex + 1].toInt() shl 8)
    }
}
