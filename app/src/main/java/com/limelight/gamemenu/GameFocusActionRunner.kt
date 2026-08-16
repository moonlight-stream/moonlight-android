package com.limelight.gamemenu

internal class GameFocusActionRunner(
    private val canRun: () -> Boolean,
    private val hasGameFocus: () -> Boolean,
    private val scheduleRetry: (Runnable) -> Unit,
    private val maxAttempts: Int = 100
) {
    fun run(action: Runnable) = run(action, attempt = 0)

    private fun run(action: Runnable, attempt: Int) {
        if (!canRun()) return

        if (!hasGameFocus()) {
            if (attempt >= maxAttempts) return
            scheduleRetry(Runnable { run(action, attempt + 1) })
            return
        }

        action.run()
    }

    fun dismissThenRun(dismiss: Runnable, action: Runnable) {
        dismiss.run()
        run(action)
    }
}
