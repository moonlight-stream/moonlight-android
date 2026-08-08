package com.limelight.binding.input

import com.limelight.nvstream.input.ControllerPacket

/**
 * Tracks local shortcuts for controllers handled by the V+ USB driver.
 *
 * The USB driver reports complete button snapshots instead of Android KeyEvents, so shortcut
 * handling must also own button-release gating. This keeps the B press used to open the menu and
 * all menu navigation input away from the streamed host until every local button is released.
 */
internal class UsbControllerShortcutStateMachine(
    private val longPressDurationMs: Long = ControllerHandler.START_DOWN_TIME_MOUSE_MODE_MS.toLong()
) {
    enum class Action {
        SCHEDULE_LONG_PRESS,
        CANCEL_LONG_PRESS,
        SHOW_HINT,
        HIDE_HINT,
        TOGGLE_MOUSE_EMULATION,
        OPEN_GAME_MENU,
        EXIT_STREAM
    }

    data class ButtonChange(val buttonFlag: Int, val pressed: Boolean)

    data class Update(
        val actions: List<Action> = emptyList(),
        val menuButtonChanges: List<ButtonChange> = emptyList(),
        val consumeAllInput: Boolean = false,
        val sendNeutralState: Boolean = false,
        val menuOpenRequestId: Long? = null
    )

    private var lastButtonFlags = 0
    private var startDownTimeMs = 0L
    private var startGestureResolved = false
    private var hintVisible = false
    private var menuPending = false
    private var menuActive = false
    private var waitForRelease = false
    private var exitPending = false
    private var ignoreMenuTriggerBUntilRelease = false
    private var pendingMenuPressedFlags = 0
    private var nextMenuOpenRequestId = 0L
    private var pendingMenuOpenRequestId: Long? = null
    private var hostNeutralStateSent = false

    @Synchronized
    fun onButtonSnapshot(buttonFlags: Int, eventTimeMs: Long, startActionEnabled: Boolean): Update {
        val previousButtonFlags = lastButtonFlags
        val changedButtonFlags = previousButtonFlags xor buttonFlags
        lastButtonFlags = buttonFlags

        if (exitPending) {
            if (buttonFlags == 0) {
                exitPending = false
                return Update(
                    actions = listOf(Action.EXIT_STREAM),
                    consumeAllInput = true
                )
            }
            return Update(consumeAllInput = true)
        }

        if (buttonFlags == EXIT_COMBO_FLAGS) {
            val actions = buildList {
                add(Action.CANCEL_LONG_PRESS)
                if (hintVisible) add(Action.HIDE_HINT)
            }
            hintVisible = false
            startGestureResolved = true
            menuPending = false
            menuActive = false
            waitForRelease = false
            exitPending = true
            ignoreMenuTriggerBUntilRelease = false
            pendingMenuPressedFlags = 0
            pendingMenuOpenRequestId = null
            return Update(
                actions = actions,
                consumeAllInput = true,
                sendNeutralState = markHostNeutralStateRequired()
            )
        }

        if (waitForRelease) {
            if (buttonFlags == 0) {
                waitForRelease = false
                hostNeutralStateSent = false
                return Update()
            }
            return Update(consumeAllInput = true)
        }

        if (menuPending || menuActive) {
            val menuChanges = if (menuActive) {
                val changes = buildMenuButtonChanges(changedButtonFlags, buttonFlags)
                if (ignoreMenuTriggerBUntilRelease &&
                    buttonFlags and ControllerPacket.B_FLAG == 0
                ) {
                    ignoreMenuTriggerBUntilRelease = false
                }
                changes
            } else {
                if (ignoreMenuTriggerBUntilRelease &&
                    buttonFlags and ControllerPacket.B_FLAG == 0
                ) {
                    ignoreMenuTriggerBUntilRelease = false
                }
                pendingMenuPressedFlags = buttonFlags and MENU_BUTTON_MASK
                if (ignoreMenuTriggerBUntilRelease) {
                    pendingMenuPressedFlags =
                        pendingMenuPressedFlags and ControllerPacket.B_FLAG.inv()
                }
                emptyList()
            }
            return Update(
                menuButtonChanges = menuChanges,
                consumeAllInput = true
            )
        }

        val actions = mutableListOf<Action>()
        val startPressed = buttonFlags and ControllerPacket.PLAY_FLAG != 0
        val startWasPressed = previousButtonFlags and ControllerPacket.PLAY_FLAG != 0

        if (startPressed && !startWasPressed) {
            startDownTimeMs = eventTimeMs
            startGestureResolved = false
            if (startActionEnabled) {
                actions += Action.SCHEDULE_LONG_PRESS
            }
        }

        val bPressed = buttonFlags and ControllerPacket.B_FLAG != 0
        val bWasPressed = previousButtonFlags and ControllerPacket.B_FLAG != 0
        if (hintVisible && bPressed && !bWasPressed) {
            hintVisible = false
            startGestureResolved = true
            menuPending = true
            ignoreMenuTriggerBUntilRelease = true
            pendingMenuPressedFlags =
                buttonFlags and MENU_BUTTON_MASK and ControllerPacket.B_FLAG.inv()
            val menuOpenRequestId = ++nextMenuOpenRequestId
            pendingMenuOpenRequestId = menuOpenRequestId
            actions += Action.HIDE_HINT
            actions += Action.OPEN_GAME_MENU
            return Update(
                actions = actions,
                consumeAllInput = true,
                sendNeutralState = markHostNeutralStateRequired(),
                menuOpenRequestId = menuOpenRequestId
            )
        }

        if (!startPressed && startWasPressed) {
            actions += Action.CANCEL_LONG_PRESS
            if (hintVisible) {
                hintVisible = false
                actions += Action.HIDE_HINT
            }
            if (startActionEnabled && !startGestureResolved &&
                startDownTimeMs > 0 && eventTimeMs - startDownTimeMs > longPressDurationMs
            ) {
                actions += Action.TOGGLE_MOUSE_EMULATION
            }
            startDownTimeMs = 0
            startGestureResolved = false
        }

        return Update(actions = actions)
    }

    @Synchronized
    fun onLongPressTimeout(eventTimeMs: Long, startActionEnabled: Boolean): Update {
        val startPressed = lastButtonFlags and ControllerPacket.PLAY_FLAG != 0
        if (!startActionEnabled || !startPressed || startGestureResolved || startDownTimeMs == 0L ||
            eventTimeMs - startDownTimeMs < longPressDurationMs
        ) {
            return Update()
        }
        hintVisible = true
        return Update(actions = listOf(Action.SHOW_HINT))
    }

    @Synchronized
    fun onGameMenuOpenResult(requestId: Long, opened: Boolean): Update {
        if (!menuPending || pendingMenuOpenRequestId != requestId) {
            return Update(consumeAllInput = isLocalInputCaptureActive())
        }
        pendingMenuOpenRequestId = null
        menuPending = false
        menuActive = opened
        if (!opened) {
            waitForRelease = lastButtonFlags != 0
            if (!waitForRelease) hostNeutralStateSent = false
            pendingMenuPressedFlags = 0
            return Update(consumeAllInput = waitForRelease)
        }
        val replayChanges = buildMenuButtonChanges(
            pendingMenuPressedFlags,
            pendingMenuPressedFlags
        )
        pendingMenuPressedFlags = 0
        return Update(
            menuButtonChanges = replayChanges,
            consumeAllInput = true
        )
    }

    @Synchronized
    fun onGameMenuUnavailable() {
        menuPending = false
        menuActive = false
        waitForRelease = lastButtonFlags != 0
        pendingMenuPressedFlags = 0
        pendingMenuOpenRequestId = null
        if (!waitForRelease) hostNeutralStateSent = false
    }

    @Synchronized
    fun isMenuOpenRequestPending(requestId: Long): Boolean =
        menuPending && pendingMenuOpenRequestId == requestId

    @Synchronized
    fun reset(): Update {
        val actions = buildList {
            add(Action.CANCEL_LONG_PRESS)
            if (hintVisible) add(Action.HIDE_HINT)
        }
        lastButtonFlags = 0
        startDownTimeMs = 0
        startGestureResolved = false
        hintVisible = false
        menuPending = false
        menuActive = false
        waitForRelease = false
        exitPending = false
        ignoreMenuTriggerBUntilRelease = false
        pendingMenuPressedFlags = 0
        pendingMenuOpenRequestId = null
        hostNeutralStateSent = false
        return Update(actions = actions)
    }

    @Synchronized
    fun isStartPressed(): Boolean = lastButtonFlags and ControllerPacket.PLAY_FLAG != 0

    @Synchronized
    fun isHintVisible(): Boolean = hintVisible

    /** Returns whether every input from this controller currently belongs to a local action. */
    @Synchronized
    fun isLocalInputCaptureActive(): Boolean =
        menuPending || menuActive || waitForRelease || exitPending

    private fun markHostNeutralStateRequired(): Boolean {
        if (hostNeutralStateSent) return false
        hostNeutralStateSent = true
        return true
    }

    private fun buildMenuButtonChanges(changedFlags: Int, currentFlags: Int): List<ButtonChange> {
        if (changedFlags == 0) return emptyList()
        return buildList {
            for (flag in MENU_BUTTON_FLAGS) {
                if (changedFlags and flag == 0) continue
                if (flag == ControllerPacket.B_FLAG && ignoreMenuTriggerBUntilRelease) continue
                add(ButtonChange(flag, currentFlags and flag != 0))
            }
        }
    }

    companion object {
        val EXIT_COMBO_FLAGS: Int = ControllerPacket.PLAY_FLAG or ControllerPacket.BACK_FLAG or
            ControllerPacket.LB_FLAG or ControllerPacket.RB_FLAG

        private val MENU_BUTTON_FLAGS = intArrayOf(
            ControllerPacket.UP_FLAG,
            ControllerPacket.DOWN_FLAG,
            ControllerPacket.LEFT_FLAG,
            ControllerPacket.RIGHT_FLAG,
            ControllerPacket.A_FLAG,
            ControllerPacket.B_FLAG
        )
        private val MENU_BUTTON_MASK = MENU_BUTTON_FLAGS.fold(0) { mask, flag -> mask or flag }
    }
}
