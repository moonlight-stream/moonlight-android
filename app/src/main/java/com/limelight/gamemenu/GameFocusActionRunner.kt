package com.limelight.gamemenu

internal class GameFocusActionRunner(
    private val canRun: () -> Boolean,
    private val hasGameFocus: () -> Boolean,
    private val scheduleRetry: (Runnable) -> Unit
) {
    fun run(action: Runnable) {
        if (!canRun()) return

        if (!hasGameFocus()) {
            scheduleRetry(Runnable { run(action) })
            return
        }

        action.run()
    }

    fun dismissThenRun(dismiss: Runnable, action: Runnable) {
        dismiss.run()
        run(action)
    }
}
