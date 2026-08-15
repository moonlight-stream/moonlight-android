package com.limelight.gamemenu

import org.junit.Assert.assertEquals
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
}
