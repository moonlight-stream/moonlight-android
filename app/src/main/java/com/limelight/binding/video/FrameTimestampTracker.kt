package com.limelight.binding.video

/**
 * Fixed-capacity timestamp metadata used between the decoder input and output threads.
 * The codec has a small bounded number of in-flight buffers, so a primitive ring avoids
 * per-frame map nodes and boxed Long values while retaining timestamp-based lookup.
 */
internal class FrameTimestampTracker(private val capacity: Int = DEFAULT_CAPACITY) {
    private val lock = Any()
    private val timestamps = LongArray(capacity)
    private val enqueueTimesMs = LongArray(capacity)
    private val hostPresentationTimesUs = LongArray(capacity)
    private val flags = ByteArray(capacity)
    private var nextSlot = 0

    init {
        require(capacity > 0)
    }

    fun recordFrame(
        timestampUs: Long,
        enqueueTimeMs: Long,
        hostPresentationTimeUs: Long,
        hasHostPresentationTime: Boolean,
    ) {
        synchronized(lock) {
            val slot = allocateSlotLocked()
            timestamps[slot] = timestampUs
            enqueueTimesMs[slot] = enqueueTimeMs
            var frameFlags = FLAG_ENQUEUE_TIME
            if (hasHostPresentationTime) {
                hostPresentationTimesUs[slot] = hostPresentationTimeUs
                frameFlags = frameFlags or FLAG_HOST_PRESENTATION_TIME
            }
            flags[slot] = frameFlags.toByte()
        }
    }

    fun takeEnqueueTime(timestampUs: Long, defaultValue: Long): Long = synchronized(lock) {
        takeValueLocked(timestampUs, FLAG_ENQUEUE_TIME, enqueueTimesMs, defaultValue)
    }

    fun takeHostPresentationTime(timestampUs: Long, defaultValue: Long): Long = synchronized(lock) {
        takeValueLocked(timestampUs, FLAG_HOST_PRESENTATION_TIME, hostPresentationTimesUs, defaultValue)
    }

    fun clear() {
        synchronized(lock) {
            flags.fill(0)
            nextSlot = 0
        }
    }

    private fun takeValueLocked(
        timestampUs: Long,
        flag: Int,
        values: LongArray,
        defaultValue: Long,
    ): Long {
        val slot = findSlotLocked(timestampUs)
        if (slot < 0 || (flags[slot].toInt() and flag) == 0) return defaultValue

        val value = values[slot]
        flags[slot] = (flags[slot].toInt() and flag.inv()).toByte()
        return value
    }

    private fun allocateSlotLocked(): Int {
        for (offset in flags.indices) {
            val slot = (nextSlot + offset) % capacity
            if (flags[slot].toInt() == 0) {
                nextSlot = (slot + 1) % capacity
                return slot
            }
        }

        val slot = nextSlot
        nextSlot = (slot + 1) % capacity
        return slot
    }

    private fun findSlotLocked(timestampUs: Long): Int {
        for (offset in 1..capacity) {
            val slot = (nextSlot - offset + capacity) % capacity
            if (flags[slot].toInt() != 0 && timestamps[slot] == timestampUs) return slot
        }
        return -1
    }

    private companion object {
        private const val DEFAULT_CAPACITY = 256
        private const val FLAG_ENQUEUE_TIME = 1
        private const val FLAG_HOST_PRESENTATION_TIME = 1 shl 1
    }
}
