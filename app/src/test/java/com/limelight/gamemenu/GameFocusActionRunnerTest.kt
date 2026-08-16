package com.limelight.gamemenu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class GameFocusActionRunnerTest {
    @Test
    fun dismissesBeforeWaitingForFocusAndRunningAction() {
        val events = mutableListOf<String>()
        var hasGameFocus = false
        var pendingRetry: Runnable? = null
        val runner = GameFocusActionRunner(
            canRun = { true },
            hasGameFocus = { hasGameFocus },
            scheduleRetry = { pendingRetry = it }
        )

        runner.dismissThenRun(
            dismiss = Runnable { events += "dismiss" },
            action = Runnable { events += "run" }
        )

        assertEquals(listOf("dismiss"), events)
        assertNotNull(pendingRetry)

        hasGameFocus = true
        requireNotNull(pendingRetry).run()

        assertEquals(listOf("dismiss", "run"), events)
    }

    @Test
    fun doesNotRunOrRetryWhenOwnerCannotRun() {
        var actionRan = false
        var retryScheduled = false
        val runner = GameFocusActionRunner(
            canRun = { false },
            hasGameFocus = { false },
            scheduleRetry = { retryScheduled = true }
        )

        runner.run(Runnable { actionRan = true })

        assertFalse(actionRan)
        assertFalse(retryScheduled)
    }

    @Test
    fun stopsRetryingAfterMaximumAttempts() {
        var actionRan = false
        val retries = ArrayDeque<Runnable>()
        val runner = GameFocusActionRunner(
            canRun = { true },
            hasGameFocus = { false },
            scheduleRetry = retries::addLast,
            maxAttempts = 2
        )

        runner.run(Runnable { actionRan = true })
        assertEquals(1, retries.size)
        retries.removeFirst().run()
        assertEquals(1, retries.size)
        retries.removeFirst().run()

        assertFalse(actionRan)
        assertEquals(0, retries.size)
    }
}
