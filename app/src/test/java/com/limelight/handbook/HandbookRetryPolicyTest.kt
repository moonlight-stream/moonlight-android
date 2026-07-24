package com.limelight.handbook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

class HandbookRetryPolicyTest {
    @Test
    fun retriesOneTransientFailureWhenBudgetRemains() {
        assertTrue(
            HandbookRetryPolicy.shouldRetry(
                IOException("stale pooled connection"),
                completedAttempts = 1,
                remainingNanos = TimeUnit.SECONDS.toNanos(2L)
            )
        )
    }

    @Test
    fun neverExceedsTwoAttempts() {
        assertFalse(
            HandbookRetryPolicy.shouldRetry(
                IOException("connection reset"),
                completedAttempts = HandbookRetryPolicy.MAX_ATTEMPTS,
                remainingNanos = TimeUnit.SECONDS.toNanos(2L)
            )
        )
    }

    @Test
    fun skipsRetryWhenOriginBudgetIsNearlyExhausted() {
        assertFalse(
            HandbookRetryPolicy.shouldRetry(
                IOException("late failure"),
                completedAttempts = 1,
                remainingNanos = TimeUnit.MILLISECONDS.toNanos(100L)
            )
        )
    }

    @Test
    fun doesNotRetryPermanentContentOrTlsFailures() {
        assertFalse(
            HandbookRetryPolicy.shouldRetry(
                PermanentHandbookException("invalid content"),
                completedAttempts = 1,
                remainingNanos = TimeUnit.SECONDS.toNanos(2L)
            )
        )
        assertFalse(
            HandbookRetryPolicy.shouldRetry(
                SSLHandshakeException("certificate rejected"),
                completedAttempts = 1,
                remainingNanos = TimeUnit.SECONDS.toNanos(2L)
            )
        )
    }
}
