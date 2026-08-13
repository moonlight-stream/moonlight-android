package com.limelight.binding.video

import java.nio.ByteBuffer

internal enum class Hdr10PlusMetadataObservation {
    ABSENT,
    FIRST,
    UNCHANGED,
    CHANGED,
}

internal data class Hdr10PlusMetadataSnapshot(
    val outputFramesQueried: Long,
    val metadataFrames: Long,
    val metadataChanges: Long,
    val lastMetadataSize: Int,
    val lastMetadataHash: Int,
    val lastPresentationTimeUs: Long,
    val queryFailures: Int,
)

/**
 * Allocation-free HDR10+ metadata statistics for sampled MediaCodec output buffers.
 *
 * The observer deliberately samples after the initial probe window, so these counters describe
 * queried output buffers rather than every decoded frame. MediaCodec owns the metadata buffer,
 * so only a hash and scalar diagnostics are retained.
 */
internal class Hdr10PlusMetadataTracker {
    var outputFramesQueried: Long = 0
        private set
    var metadataFrames: Long = 0
        private set
    var metadataChanges: Long = 0
        private set
    var lastMetadataSize: Int = 0
        private set
    var lastMetadataHash: Int = 0
        private set
    var lastPresentationTimeUs: Long = -1
        private set
    var queryFailures: Int = 0
        private set

    private var hasLastMetadata = false

    @Synchronized
    fun recordMetadata(
        metadata: ByteBuffer?,
        presentationTimeUs: Long,
    ): Hdr10PlusMetadataObservation {
        outputFramesQueried++
        if (metadata == null || !metadata.hasRemaining()) {
            return Hdr10PlusMetadataObservation.ABSENT
        }

        val start = metadata.position()
        val end = metadata.limit()
        val size = end - start
        var hash = FNV_OFFSET_BASIS
        for (index in start until end) {
            hash = hash xor (metadata.get(index).toInt() and 0xFF)
            hash *= FNV_PRIME
        }

        metadataFrames++
        val result = when {
            !hasLastMetadata -> Hdr10PlusMetadataObservation.FIRST
            size != lastMetadataSize || hash != lastMetadataHash -> {
                metadataChanges++
                Hdr10PlusMetadataObservation.CHANGED
            }
            else -> Hdr10PlusMetadataObservation.UNCHANGED
        }

        hasLastMetadata = true
        lastMetadataSize = size
        lastMetadataHash = hash
        lastPresentationTimeUs = presentationTimeUs
        return result
    }

    /** Returns true only for the first query failure, so callers can rate-limit logging. */
    @Synchronized
    fun recordQueryFailure(): Boolean {
        outputFramesQueried++
        queryFailures++
        return queryFailures == 1
    }

    @Synchronized
    fun reset() {
        outputFramesQueried = 0
        metadataFrames = 0
        metadataChanges = 0
        lastMetadataSize = 0
        lastMetadataHash = 0
        lastPresentationTimeUs = -1
        queryFailures = 0
        hasLastMetadata = false
    }

    /** Consistent, best-effort diagnostic snapshot for readers outside the codec output thread. */
    @Synchronized
    fun snapshot(): Hdr10PlusMetadataSnapshot = Hdr10PlusMetadataSnapshot(
        outputFramesQueried = outputFramesQueried,
        metadataFrames = metadataFrames,
        metadataChanges = metadataChanges,
        lastMetadataSize = lastMetadataSize,
        lastMetadataHash = lastMetadataHash,
        lastPresentationTimeUs = lastPresentationTimeUs,
        queryFailures = queryFailures,
    )

    private companion object {
        private const val FNV_OFFSET_BASIS = -0x7ee3623b
        private const val FNV_PRIME = 0x01000193
    }
}
