package com.limelight.binding.input

internal class ControllerArrivalTracker(initiallyReported: Boolean = false) {
    private companion object {
        const val SUCCESS = 0
    }

    @Volatile
    var isReported: Boolean = initiallyReported
        private set

    fun recordAttempt(result: Int): Boolean {
        if (result == SUCCESS) {
            markReported()
        }
        return isReported
    }

    fun markReported() {
        isReported = true
    }
}
