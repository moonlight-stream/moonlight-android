package com.limelight.preferences

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.limelight.R
import com.limelight.binding.input.ControllerHandler
import com.limelight.binding.input.UsbControllerShortcutStateMachine
import com.limelight.binding.input.driver.AbstractController
import com.limelight.binding.input.driver.UsbDriverListener
import com.limelight.binding.input.driver.UsbDriverService
import com.limelight.gamemenu.GameMenuCardShape
import com.limelight.gamemenu.GameMenuDimens
import com.limelight.gamemenu.gamepadFocusOutline
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.utils.UiHelper
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ControllerDiagnosticActivity : ComponentActivity(), UsbDriverListener,
    UsbDriverService.UsbDriverStateListener {
    private var snapshot by mutableStateOf(ControllerDiagnostics.Snapshot(emptyList()))
    private val simulatorHandler = Handler(Looper.getMainLooper())
    private val simulatorStateMachine = UsbControllerShortcutStateMachine()
    private var simulatorPressedFlags = 0
    private var simulatorInputSource: String? = null
    private var simulatorUiState by mutableStateOf(ShortcutSimulatorUiState())
    private var shortcutTestActive by mutableStateOf(false)
    private var shortcutTestPhase by mutableStateOf(ShortcutTestPhase.IDLE)
    private var shortcutTestSession by mutableStateOf(ShortcutTestSession.NONE)
    private var activeShortcutTarget by mutableStateOf<ShortcutTestTarget?>(null)
    private var shortcutAttemptState by mutableStateOf(ShortcutAttemptState.INACTIVE)
    private var shortcutAttemptRemainingSeconds by mutableIntStateOf(0)
    private var selectedDiagnosticTab by mutableStateOf(ControllerDiagnosticTab.BUTTONS)
    private var controllerInfoVisible by mutableStateOf(false)
    private var controllerInfoScrollSequence by mutableIntStateOf(0)
    private var controllerInfoScrollDirection by mutableIntStateOf(0)
    private var controllerInfoHatDirection = 0
    private var controllerInfoLastScrollMs = 0L
    private var selectedTestDurationSeconds by mutableIntStateOf(DEFAULT_TEST_DURATION_SECONDS)
    private var remainingTestSeconds by mutableIntStateOf(0)
    private var testDeadlineMs = 0L
    private var shortcutAttemptDeadlineMs = 0L
    private var startKeyActionEnabled by mutableStateOf(true)
    private var systemStartDownTimeMs = 0L
    private var systemExitPending = false
    private var usbDriverBinder: UsbDriverService.UsbDriverBinder? = null
    private var usbDriverBound = false
    private val usbPermissionPromptCount = AtomicInteger(0)
    private val usbDriverExecutor = Executors.newSingleThreadExecutor()
    private val usbControllerNames = mutableMapOf<Int, String>()
    private var usbNavigationRawFlags = 0
    private var usbNavigationInjectedFlags = 0
    private val usbNavigationKeyDownTimes = mutableMapOf<Int, Long>()
    private val simulatorLongPressRunnable = Runnable {
        applySimulatorUpdate(
            simulatorStateMachine.onLongPressTimeout(
                SystemClock.uptimeMillis(),
                startKeyActionEnabled
            )
        )
    }
    private val testCountdownRunnable = object : Runnable {
        override fun run() {
            if (!shortcutTestActive) return
            val remainingMs = testDeadlineMs - SystemClock.elapsedRealtime()
            if (remainingMs <= 0L) {
                stopShortcutTest()
                return
            }
            remainingTestSeconds = ((remainingMs + 999L) / 1000L).toInt()
            simulatorHandler.postDelayed(this, COUNTDOWN_TICK_MS)
        }
    }
    private val shortcutAttemptCountdownRunnable = object : Runnable {
        override fun run() {
            if (!shortcutTestActive || shortcutAttemptState != ShortcutAttemptState.WAITING) return
            val remainingMs = shortcutAttemptDeadlineMs - SystemClock.elapsedRealtime()
            if (remainingMs <= 0L) {
                activeShortcutTarget = null
                shortcutAttemptRemainingSeconds = 0
                shortcutAttemptState = ShortcutAttemptState.TIMED_OUT
                simulatorHandler.postDelayed(finishShortcutAttemptRunnable, SHORTCUT_RESULT_DISPLAY_MS)
                return
            }
            shortcutAttemptRemainingSeconds = ((remainingMs + 999L) / 1000L).toInt()
            simulatorHandler.postDelayed(this, COUNTDOWN_TICK_MS)
        }
    }
    private val finishShortcutAttemptRunnable = Runnable { finishShortcutAttempt() }
    private val usbServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (!shortcutTestActive || usbDriverExecutor.isShutdown) return
            val binder = service as? UsbDriverService.UsbDriverBinder ?: run {
                markShortcutTestReady()
                return
            }
            usbDriverBinder = binder
            val listenerAttached = runCatching {
                binder.setListener(this@ControllerDiagnosticActivity)
                binder.setStateListener(this@ControllerDiagnosticActivity)
            }.isSuccess
            if (!listenerAttached) {
                markShortcutTestReady()
                return
            }
            usbPermissionPromptCount.set(0)
            val taskSubmitted = runCatching {
                usbDriverExecutor.execute {
                    val controllerStarting = runCatching { binder.startForDiagnostics() }
                        .getOrDefault(false)
                    simulatorHandler.post {
                        if (!shortcutTestActive || usbDriverBinder !== binder) return@post
                        if (!controllerStarting) usbControllerNames.clear()
                        maybeMarkShortcutTestReady()
                    }
                }
            }.isSuccess
            if (!taskSubmitted) {
                markShortcutTestReady()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            usbDriverBinder = null
            usbControllerNames.clear()
            maybeMarkShortcutTestReady()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val systemBarColor = ContextCompat.getColor(this, R.color.game_menu_dialog_background)
        val window: Window = window
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = systemBarColor
        window.navigationBarColor = systemBarColor
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.systemBars())
            val lightSystemBars = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
            isAppearanceLightStatusBars = lightSystemBars
            isAppearanceLightNavigationBars = lightSystemBars
        }

        UiHelper.setLocale(this)
        refreshControllers()

        setContent {
            ControllerDiagnosticScreen(
                snapshot = snapshot,
                simulatorState = simulatorUiState,
                shortcutTestPhase = shortcutTestPhase,
                activeShortcutTarget = activeShortcutTarget,
                shortcutAttemptState = shortcutAttemptState,
                shortcutAttemptRemainingSeconds = shortcutAttemptRemainingSeconds,
                selectedTab = selectedDiagnosticTab,
                controllerInfoVisible = controllerInfoVisible,
                controllerInfoScrollSequence = controllerInfoScrollSequence,
                controllerInfoScrollDirection = controllerInfoScrollDirection,
                selectedTestDurationSeconds = selectedTestDurationSeconds,
                remainingTestSeconds = remainingTestSeconds,
                startKeyActionEnabled = startKeyActionEnabled,
                onBack = ::exitDiagnosticScreen,
                onRefresh = ::refreshControllers,
                onShowControllerInfo = {
                    controllerInfoHatDirection = 0
                    controllerInfoVisible = true
                },
                onDismissControllerInfo = {
                    controllerInfoHatDirection = 0
                    controllerInfoVisible = false
                },
                onToggleShortcutTest = ::toggleShortcutTest,
                onToggleShortcutTarget = ::toggleShortcutTarget,
                shortcutTabEnabled = shortcutTestSession != ShortcutTestSession.GENERAL,
                onTabSelected = ::selectDiagnosticTab,
                onSelectTestDuration = ::selectTestDuration
            )
        }

        UiHelper.notifyNewRootView(this)
    }

    override fun onResume() {
        super.onResume()
        if (!shortcutTestActive) {
            snapshot = ControllerDiagnostics.scan(this)
        }
    }

    private fun refreshControllers() {
        if (shortcutTestPhase != ShortcutTestPhase.IDLE) {
            stopShortcutTest(refreshAfterStop = true)
            return
        }
        resetShortcutTest()
        snapshot = ControllerDiagnostics.scan(this)
        startKeyActionEnabled = PreferenceConfiguration.readPreferences(this).enableStartKeyMenu
    }

    private fun exitDiagnosticScreen() {
        if (shortcutTestPhase != ShortcutTestPhase.IDLE) {
            stopShortcutTest(refreshAfterStop = false)
        }
        finish()
    }

    private fun resetShortcutTest() {
        simulatorHandler.removeCallbacks(simulatorLongPressRunnable)
        simulatorStateMachine.reset()
        simulatorPressedFlags = 0
        simulatorInputSource = null
        systemStartDownTimeMs = 0L
        systemExitPending = false
        simulatorUiState = ShortcutSimulatorUiState()
    }

    // Intercept before focused Compose content so controller input cannot escape the active test.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (controllerInfoVisible) {
            if (event.action == KeyEvent.ACTION_UP &&
                (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_BUTTON_B)
            ) {
                controllerInfoVisible = false
                return true
            }
            if (isControllerEvent(event.device, event.source)) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> requestControllerInfoScroll(-1)
                        KeyEvent.KEYCODE_DPAD_DOWN -> requestControllerInfoScroll(1)
                    }
                }
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        if (shortcutTestActive && isControllerEvent(event.device, event.source)) {
            if (isShortcutNavigationMode()) return super.dispatchKeyEvent(event)
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) handleSimulatorKeyEvent(event, true)
                }
                KeyEvent.ACTION_UP -> handleSimulatorKeyEvent(event, false)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (controllerInfoVisible && isControllerEvent(event.device, event.source)) {
            handleControllerInfoMotion(event)
            return true
        }
        if (shortcutTestActive && isControllerEvent(event.device, event.source)) {
            if (isShortcutNavigationMode()) return super.dispatchGenericMotionEvent(event)
            handleSimulatorMotionEvent(event)
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun requestControllerInfoScroll(direction: Int) {
        controllerInfoScrollDirection = direction
        controllerInfoScrollSequence++
    }

    private fun handleControllerInfoMotion(event: MotionEvent) {
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val stickY = event.getAxisValue(MotionEvent.AXIS_Y)
        val direction = when {
            hatY < -DPAD_AXIS_THRESHOLD || stickY < -INFO_SCROLL_AXIS_THRESHOLD -> -1
            hatY > DPAD_AXIS_THRESHOLD || stickY > INFO_SCROLL_AXIS_THRESHOLD -> 1
            else -> 0
        }
        val now = SystemClock.uptimeMillis()
        if (direction != 0 && (direction != controllerInfoHatDirection ||
                now - controllerInfoLastScrollMs >= INFO_SCROLL_REPEAT_MS)
        ) {
            requestControllerInfoScroll(direction)
            controllerInfoLastScrollMs = now
        }
        controllerInfoHatDirection = direction
    }

    private fun isShortcutNavigationMode(): Boolean {
        return selectedDiagnosticTab == ControllerDiagnosticTab.SHORTCUTS &&
            activeShortcutTarget == null
    }

    private fun handleUsbNavigationState(
        buttonFlags: Int,
        leftStickX: Float,
        leftStickY: Float
    ): Boolean {
        var navigationFlags = buttonFlags and USB_NAVIGATION_FLAGS
        if (leftStickX <= -USB_NAVIGATION_AXIS_THRESHOLD) {
            navigationFlags = navigationFlags or ControllerPacket.LEFT_FLAG
        } else if (leftStickX >= USB_NAVIGATION_AXIS_THRESHOLD) {
            navigationFlags = navigationFlags or ControllerPacket.RIGHT_FLAG
        }
        if (leftStickY <= -USB_NAVIGATION_AXIS_THRESHOLD) {
            navigationFlags = navigationFlags or ControllerPacket.UP_FLAG
        } else if (leftStickY >= USB_NAVIGATION_AXIS_THRESHOLD) {
            navigationFlags = navigationFlags or ControllerPacket.DOWN_FLAG
        }

        val navigationMode = isShortcutNavigationMode()
        val changedFlags = navigationFlags xor usbNavigationRawFlags
        for (flag in USB_NAVIGATION_BUTTONS) {
            if (changedFlags and flag == 0) continue
            val pressed = navigationFlags and flag != 0
            if (pressed && navigationMode) {
                dispatchUsbNavigationKey(flag, pressed = true)
                usbNavigationInjectedFlags = usbNavigationInjectedFlags or flag
            } else if (!pressed && usbNavigationInjectedFlags and flag != 0) {
                dispatchUsbNavigationKey(flag, pressed = false)
                usbNavigationInjectedFlags = usbNavigationInjectedFlags and flag.inv()
            }
        }
        usbNavigationRawFlags = navigationFlags
        return navigationMode
    }

    private fun releaseUsbNavigationKeys(resetRawState: Boolean) {
        for (flag in USB_NAVIGATION_BUTTONS) {
            if (usbNavigationInjectedFlags and flag != 0) {
                dispatchUsbNavigationKey(flag, pressed = false)
            }
        }
        usbNavigationInjectedFlags = 0
        usbNavigationKeyDownTimes.clear()
        if (resetRawState) usbNavigationRawFlags = 0
    }

    private fun dispatchUsbNavigationKey(buttonFlag: Int, pressed: Boolean) {
        val keyCode = when (buttonFlag) {
            ControllerPacket.UP_FLAG -> KeyEvent.KEYCODE_DPAD_UP
            ControllerPacket.DOWN_FLAG -> KeyEvent.KEYCODE_DPAD_DOWN
            ControllerPacket.LEFT_FLAG -> KeyEvent.KEYCODE_DPAD_LEFT
            ControllerPacket.RIGHT_FLAG -> KeyEvent.KEYCODE_DPAD_RIGHT
            ControllerPacket.A_FLAG -> KeyEvent.KEYCODE_DPAD_CENTER
            ControllerPacket.B_FLAG -> KeyEvent.KEYCODE_BACK
            else -> return
        }
        val eventTime = SystemClock.uptimeMillis()
        val downTime = if (pressed) {
            usbNavigationKeyDownTimes[buttonFlag] = eventTime
            eventTime
        } else {
            usbNavigationKeyDownTimes.remove(buttonFlag) ?: eventTime
        }
        val event = KeyEvent(
            downTime,
            eventTime,
            if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP,
            keyCode,
            0
        )
        if (!window.superDispatchKeyEvent(event)) {
            event.dispatch(this, window.decorView.keyDispatcherState, this)
        }
    }

    private fun handleSimulatorKeyEvent(event: KeyEvent, pressed: Boolean) {
        val controllerName = event.device?.name?.takeIf { it.isNotBlank() }
            ?: getString(R.string.controller_diag_unknown_device)
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L2 -> {
                updateSimulatorButtonSnapshot(
                    source = "android:${event.deviceId}",
                    controllerName = controllerName,
                    inputPath = ControllerDiagnostics.InputPath.SYSTEM,
                    pressedFlags = simulatorPressedFlags,
                    eventTimeMs = event.eventTime,
                    leftTrigger = if (pressed) 1f else 0f
                )
                return
            }
            KeyEvent.KEYCODE_BUTTON_R2 -> {
                updateSimulatorButtonSnapshot(
                    source = "android:${event.deviceId}",
                    controllerName = controllerName,
                    inputPath = ControllerDiagnostics.InputPath.SYSTEM,
                    pressedFlags = simulatorPressedFlags,
                    eventTimeMs = event.eventTime,
                    rightTrigger = if (pressed) 1f else 0f
                )
                return
            }
        }

        val buttonFlag = simulatorButtonFlag(event) ?: return
        val pressedFlags = if (pressed) {
            simulatorPressedFlags or buttonFlag
        } else {
            simulatorPressedFlags and buttonFlag.inv()
        }
        updateSimulatorButtonSnapshot(
            source = "android:${event.deviceId}",
            controllerName = controllerName,
            inputPath = ControllerDiagnostics.InputPath.SYSTEM,
            pressedFlags = pressedFlags,
            eventTimeMs = event.eventTime
        )
    }

    override fun onDestroy() {
        stopShortcutTest(refreshAfterStop = false)
        usbDriverExecutor.shutdown()
        super.onDestroy()
    }

    override fun onStop() {
        controllerInfoVisible = false
        controllerInfoHatDirection = 0
        if (shortcutTestActive) {
            stopShortcutTest(refreshAfterStop = false)
        }
        super.onStop()
    }

    private fun toggleShortcutTest() {
        if (selectedDiagnosticTab == ControllerDiagnosticTab.SHORTCUTS &&
            shortcutTestPhase == ShortcutTestPhase.IDLE
        ) {
            return
        }
        when (shortcutTestPhase) {
            ShortcutTestPhase.IDLE -> startShortcutTest(
                target = null,
                session = ShortcutTestSession.GENERAL
            )
            ShortcutTestPhase.STARTING,
            ShortcutTestPhase.ACTIVE -> stopShortcutTest()
            ShortcutTestPhase.STOPPING -> Unit
        }
    }

    private fun selectDiagnosticTab(tab: ControllerDiagnosticTab) {
        if (tab == ControllerDiagnosticTab.SHORTCUTS &&
            shortcutTestSession == ShortcutTestSession.GENERAL
        ) {
            return
        }
        if (selectedDiagnosticTab == ControllerDiagnosticTab.SHORTCUTS &&
            tab != ControllerDiagnosticTab.SHORTCUTS &&
            shortcutTestSession == ShortcutTestSession.SHORTCUTS &&
            shortcutTestPhase != ShortcutTestPhase.IDLE
        ) {
            stopShortcutTest(refreshAfterStop = true)
        }
        selectedDiagnosticTab = tab
    }

    private fun toggleShortcutTarget(target: ShortcutTestTarget) {
        when (shortcutTestPhase) {
            ShortcutTestPhase.IDLE -> startShortcutTest(
                target = target,
                session = ShortcutTestSession.SHORTCUTS
            )
            ShortcutTestPhase.STOPPING -> Unit
            ShortcutTestPhase.STARTING -> {
                if (activeShortcutTarget == target) {
                    finishShortcutAttempt()
                } else {
                    releaseUsbNavigationKeys(resetRawState = false)
                    activeShortcutTarget = target
                    resetShortcutTest()
                    shortcutAttemptState = ShortcutAttemptState.PREPARING
                    shortcutAttemptRemainingSeconds = SHORTCUT_ATTEMPT_SECONDS
                }
            }
            ShortcutTestPhase.ACTIVE -> {
                if (activeShortcutTarget == target) {
                    finishShortcutAttempt()
                } else {
                    releaseUsbNavigationKeys(resetRawState = false)
                    activeShortcutTarget = target
                    resetShortcutTest()
                    startShortcutAttemptCountdown()
                }
            }
        }
    }

    private fun startShortcutTest(
        target: ShortcutTestTarget?,
        session: ShortcutTestSession
    ) {
        releaseUsbNavigationKeys(resetRawState = true)
        resetShortcutTest()
        resetShortcutAttempt()
        shortcutTestSession = session
        activeShortcutTarget = target
        shortcutAttemptState = if (target == null) {
            ShortcutAttemptState.INACTIVE
        } else {
            ShortcutAttemptState.PREPARING
        }
        shortcutAttemptRemainingSeconds = if (target == null) 0 else SHORTCUT_ATTEMPT_SECONDS
        startKeyActionEnabled = PreferenceConfiguration.readPreferences(this).enableStartKeyMenu
        shortcutTestActive = true
        shortcutTestPhase = ShortcutTestPhase.STARTING
        remainingTestSeconds = 0
        usbDriverBound = runCatching {
            bindService(
                Intent(this, UsbDriverService::class.java),
                usbServiceConnection,
                Context.BIND_AUTO_CREATE
            )
        }.getOrDefault(false)
        if (!usbDriverBound) markShortcutTestReady()
    }

    private fun stopShortcutTest(refreshAfterStop: Boolean = true) {
        if (shortcutTestPhase == ShortcutTestPhase.IDLE ||
            shortcutTestPhase == ShortcutTestPhase.STOPPING
        ) {
            return
        }
        shortcutTestActive = false
        shortcutTestPhase = ShortcutTestPhase.STOPPING
        simulatorHandler.removeCallbacks(testCountdownRunnable)
        remainingTestSeconds = 0
        testDeadlineMs = 0L
        usbPermissionPromptCount.set(0)
        releaseUsbNavigationKeys(resetRawState = true)
        val binder = usbDriverBinder
        runCatching { binder?.setListener(null) }
        runCatching { binder?.setStateListener(null) }
        usbDriverBinder = null
        resetShortcutTest()
        resetShortcutAttempt()

        if (binder == null) {
            completeShortcutTestStop(refreshAfterStop)
        } else {
            val taskSubmitted = runCatching {
                usbDriverExecutor.execute {
                    runCatching { binder.stop() }
                    simulatorHandler.post {
                        completeShortcutTestStop(refreshAfterStop)
                    }
                }
            }.isSuccess
            if (!taskSubmitted) {
                completeShortcutTestStop(refreshAfterStop)
            }
        }
    }

    private fun completeShortcutTestStop(refreshAfterStop: Boolean) {
        if (usbDriverBound) {
            runCatching { unbindService(usbServiceConnection) }
            usbDriverBound = false
        }
        usbControllerNames.clear()
        activeShortcutTarget = null
        shortcutTestSession = ShortcutTestSession.NONE
        shortcutTestPhase = ShortcutTestPhase.IDLE
        if (refreshAfterStop && !isFinishing && !isDestroyed) {
            simulatorHandler.postDelayed({
                snapshot = ControllerDiagnostics.scan(this)
            }, USB_RELEASE_REFRESH_DELAY_MS)
        }
    }

    private fun markShortcutTestReady() {
        if (!shortcutTestActive || shortcutTestPhase == ShortcutTestPhase.ACTIVE) return
        restartTestCountdown()
        if (activeShortcutTarget != null) startShortcutAttemptCountdown()
    }

    private fun maybeMarkShortcutTestReady() {
        if (!shortcutTestActive || shortcutTestPhase != ShortcutTestPhase.STARTING ||
            usbPermissionPromptCount.get() != 0
        ) {
            return
        }
        val controllerStarting = runCatching {
            usbDriverBinder?.hasActiveControllers() == true
        }.getOrDefault(false)
        if (usbControllerNames.isNotEmpty() || !controllerStarting) {
            markShortcutTestReady()
        }
    }

    private fun restartTestCountdown() {
        if (!shortcutTestActive) return
        shortcutTestPhase = ShortcutTestPhase.ACTIVE
        remainingTestSeconds = selectedTestDurationSeconds
        testDeadlineMs = SystemClock.elapsedRealtime() + selectedTestDurationSeconds * 1000L
        simulatorHandler.removeCallbacks(testCountdownRunnable)
        simulatorHandler.postDelayed(testCountdownRunnable, COUNTDOWN_TICK_MS)
    }

    private fun startShortcutAttemptCountdown() {
        if (!shortcutTestActive || activeShortcutTarget == null) return
        simulatorHandler.removeCallbacks(shortcutAttemptCountdownRunnable)
        simulatorHandler.removeCallbacks(finishShortcutAttemptRunnable)
        shortcutAttemptState = ShortcutAttemptState.WAITING
        shortcutAttemptRemainingSeconds = SHORTCUT_ATTEMPT_SECONDS
        shortcutAttemptDeadlineMs = SystemClock.elapsedRealtime() + SHORTCUT_ATTEMPT_SECONDS * 1000L
        simulatorHandler.postDelayed(shortcutAttemptCountdownRunnable, COUNTDOWN_TICK_MS)
    }

    private fun resetShortcutAttempt() {
        simulatorHandler.removeCallbacks(shortcutAttemptCountdownRunnable)
        simulatorHandler.removeCallbacks(finishShortcutAttemptRunnable)
        shortcutAttemptState = ShortcutAttemptState.INACTIVE
        shortcutAttemptRemainingSeconds = 0
        shortcutAttemptDeadlineMs = 0L
    }

    private fun finishShortcutAttempt() {
        if (!shortcutTestActive) return
        activeShortcutTarget = null
        resetShortcutTest()
        resetShortcutAttempt()
    }

    private fun selectTestDuration(durationSeconds: Int) {
        if (!shortcutTestActive && durationSeconds in SUPPORTED_TEST_DURATIONS_SECONDS) {
            selectedTestDurationSeconds = durationSeconds
        }
    }

    private fun isControllerEvent(inputDevice: InputDevice?, fallbackSource: Int): Boolean {
        if (inputDevice == null || inputDevice.isVirtual) return false
        val sources = inputDevice.sources.takeIf { it != 0 } ?: fallbackSource
        return sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }

    private fun handleSimulatorMotionEvent(event: MotionEvent) {
        val inputDevice = event.device ?: return
        val rightStickUsesZRz = inputDevice.getMotionRange(
            MotionEvent.AXIS_Z,
            InputDevice.SOURCE_JOYSTICK
        ) != null && inputDevice.getMotionRange(
            MotionEvent.AXIS_RZ,
            InputDevice.SOURCE_JOYSTICK
        ) != null
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        var pressedFlags = simulatorPressedFlags and DPAD_FLAGS.inv()
        if (hatX < -DPAD_AXIS_THRESHOLD) pressedFlags = pressedFlags or ControllerPacket.LEFT_FLAG
        if (hatX > DPAD_AXIS_THRESHOLD) pressedFlags = pressedFlags or ControllerPacket.RIGHT_FLAG
        if (hatY < -DPAD_AXIS_THRESHOLD) pressedFlags = pressedFlags or ControllerPacket.UP_FLAG
        if (hatY > DPAD_AXIS_THRESHOLD) pressedFlags = pressedFlags or ControllerPacket.DOWN_FLAG

        updateSimulatorButtonSnapshot(
            source = "android:${event.deviceId}",
            controllerName = inputDevice.name.takeIf { it.isNotBlank() }
                ?: getString(R.string.controller_diag_unknown_device),
            inputPath = ControllerDiagnostics.InputPath.SYSTEM,
            pressedFlags = pressedFlags,
            eventTimeMs = event.eventTime,
            leftStickX = event.getAxisValue(MotionEvent.AXIS_X).coerceIn(-1f, 1f),
            leftStickY = event.getAxisValue(MotionEvent.AXIS_Y).coerceIn(-1f, 1f),
            rightStickX = event.getAxisValue(
                if (rightStickUsesZRz) MotionEvent.AXIS_Z else MotionEvent.AXIS_RX
            ).coerceIn(-1f, 1f),
            rightStickY = event.getAxisValue(
                if (rightStickUsesZRz) MotionEvent.AXIS_RZ else MotionEvent.AXIS_RY
            ).coerceIn(-1f, 1f),
            leftTrigger = maxOf(
                event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_BRAKE)
            ).coerceIn(0f, 1f),
            rightTrigger = maxOf(
                event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_GAS)
            ).coerceIn(0f, 1f)
        )
    }

    private fun updateSimulatorButtonSnapshot(
        source: String,
        controllerName: String,
        inputPath: ControllerDiagnostics.InputPath,
        pressedFlags: Int,
        eventTimeMs: Long,
        leftStickX: Float? = null,
        leftStickY: Float? = null,
        rightStickX: Float? = null,
        rightStickY: Float? = null,
        leftTrigger: Float? = null,
        rightTrigger: Float? = null
    ) {
        if (!shortcutTestActive) return
        if (simulatorInputSource != source) {
            simulatorHandler.removeCallbacks(simulatorLongPressRunnable)
            simulatorStateMachine.reset()
            simulatorPressedFlags = 0
            simulatorInputSource = source
            systemStartDownTimeMs = 0L
            systemExitPending = false
            simulatorUiState = ShortcutSimulatorUiState(
                controllerName = controllerName,
                inputPath = inputPath
            )
        }
        simulatorPressedFlags = pressedFlags
        if (inputPath == ControllerDiagnostics.InputPath.USB_TAKEOVER) {
            applySimulatorUpdate(
                simulatorStateMachine.onButtonSnapshot(
                    simulatorPressedFlags,
                    eventTimeMs,
                    startKeyActionEnabled
                )
            )
        } else {
            applySystemSimulatorUpdate(eventTimeMs)
        }
        simulatorUiState = simulatorUiState.copy(
            result = if (
                pressedFlags and ControllerHandler.PERFORMANCE_OVERLAY_COMBO_FLAGS ==
                    ControllerHandler.PERFORMANCE_OVERLAY_COMBO_FLAGS
            ) {
                ShortcutSimulatorResult.PERFORMANCE_OVERLAY
            } else {
                simulatorUiState.result
            },
            leftStickX = leftStickX ?: simulatorUiState.leftStickX,
            leftStickY = leftStickY ?: simulatorUiState.leftStickY,
            rightStickX = rightStickX ?: simulatorUiState.rightStickX,
            rightStickY = rightStickY ?: simulatorUiState.rightStickY,
            leftTrigger = leftTrigger ?: simulatorUiState.leftTrigger,
            rightTrigger = rightTrigger ?: simulatorUiState.rightTrigger
        )
        evaluateShortcutAttempt()
    }

    private fun applySystemSimulatorUpdate(eventTimeMs: Long) {
        val previousFlags = simulatorUiState.pressedFlags
        var result = simulatorUiState.result

        if (systemExitPending) {
            if (simulatorPressedFlags == 0) {
                systemExitPending = false
                result = ShortcutSimulatorResult.EXIT_STREAM
            }
        } else if (simulatorPressedFlags == UsbControllerShortcutStateMachine.EXIT_COMBO_FLAGS) {
            systemExitPending = true
            result = ShortcutSimulatorResult.IDLE
        } else {
            val startPressed = simulatorPressedFlags and ControllerPacket.PLAY_FLAG != 0
            val startWasPressed = previousFlags and ControllerPacket.PLAY_FLAG != 0
            if (startPressed && !startWasPressed) {
                systemStartDownTimeMs = eventTimeMs
                result = ShortcutSimulatorResult.IDLE
            } else if (!startPressed && startWasPressed) {
                if (startKeyActionEnabled && systemStartDownTimeMs > 0 &&
                    eventTimeMs - systemStartDownTimeMs >
                    ControllerHandler.START_DOWN_TIME_MOUSE_MODE_MS
                ) {
                    result = ShortcutSimulatorResult.GAME_MENU
                }
                systemStartDownTimeMs = 0L
            }
        }

        simulatorUiState = simulatorUiState.copy(
            result = result,
            hintVisible = false,
            pressedFlags = simulatorPressedFlags
        )
    }

    private fun applySimulatorUpdate(update: UsbControllerShortcutStateMachine.Update) {
        var result = simulatorUiState.result
        for (action in update.actions) {
            when (action) {
                UsbControllerShortcutStateMachine.Action.SCHEDULE_LONG_PRESS -> {
                    result = ShortcutSimulatorResult.IDLE
                    simulatorHandler.removeCallbacks(simulatorLongPressRunnable)
                    simulatorHandler.postDelayed(
                        simulatorLongPressRunnable,
                        ControllerHandler.START_DOWN_TIME_MOUSE_MODE_MS.toLong()
                    )
                }
                UsbControllerShortcutStateMachine.Action.CANCEL_LONG_PRESS ->
                    simulatorHandler.removeCallbacks(simulatorLongPressRunnable)
                UsbControllerShortcutStateMachine.Action.SHOW_HINT ->
                    result = ShortcutSimulatorResult.HINT
                UsbControllerShortcutStateMachine.Action.HIDE_HINT ->
                    if (result == ShortcutSimulatorResult.HINT) result = ShortcutSimulatorResult.IDLE
                UsbControllerShortcutStateMachine.Action.TOGGLE_MOUSE_EMULATION ->
                    result = ShortcutSimulatorResult.MOUSE_EMULATION
                UsbControllerShortcutStateMachine.Action.OPEN_GAME_MENU -> {
                    result = ShortcutSimulatorResult.GAME_MENU
                    update.menuOpenRequestId?.let { requestId ->
                        simulatorStateMachine.onGameMenuOpenResult(requestId, false)
                    }
                }
                UsbControllerShortcutStateMachine.Action.EXIT_STREAM ->
                    result = ShortcutSimulatorResult.EXIT_STREAM
            }
        }
        simulatorUiState = ShortcutSimulatorUiState(
            result = result,
            hintVisible = simulatorStateMachine.isHintVisible(),
            pressedFlags = simulatorPressedFlags,
            controllerName = simulatorUiState.controllerName,
            inputPath = simulatorUiState.inputPath
        )
        evaluateShortcutAttempt()
    }

    private fun evaluateShortcutAttempt() {
        if (shortcutAttemptState != ShortcutAttemptState.WAITING) return
        val recognized = when (activeShortcutTarget) {
            ShortcutTestTarget.SYSTEM_GAME_MENU ->
                simulatorUiState.inputPath == ControllerDiagnostics.InputPath.SYSTEM &&
                    simulatorUiState.result == ShortcutSimulatorResult.GAME_MENU
            ShortcutTestTarget.USB_GAME_MENU ->
                simulatorUiState.inputPath == ControllerDiagnostics.InputPath.USB_TAKEOVER &&
                    simulatorUiState.result == ShortcutSimulatorResult.GAME_MENU
            ShortcutTestTarget.USB_MOUSE_MODE ->
                simulatorUiState.inputPath == ControllerDiagnostics.InputPath.USB_TAKEOVER &&
                    simulatorUiState.result == ShortcutSimulatorResult.MOUSE_EMULATION
            ShortcutTestTarget.PERFORMANCE_OVERLAY ->
                simulatorUiState.result == ShortcutSimulatorResult.PERFORMANCE_OVERLAY
            ShortcutTestTarget.EXIT_STREAM ->
                simulatorUiState.result == ShortcutSimulatorResult.EXIT_STREAM
            null -> false
        }
        if (!recognized) return

        simulatorHandler.removeCallbacks(shortcutAttemptCountdownRunnable)
        activeShortcutTarget = null
        shortcutAttemptRemainingSeconds = 0
        shortcutAttemptState = ShortcutAttemptState.SUCCEEDED
        simulatorHandler.postDelayed(finishShortcutAttemptRunnable, SHORTCUT_RESULT_DISPLAY_MS)
    }

    override fun reportControllerState(
        controllerId: Int,
        buttonFlags: Int,
        leftStickX: Float,
        leftStickY: Float,
        rightStickX: Float,
        rightStickY: Float,
        leftTrigger: Float,
        rightTrigger: Float
    ) {
        simulatorHandler.post {
            if (!shortcutTestActive) return@post
            if (handleUsbNavigationState(buttonFlags, leftStickX, leftStickY)) {
                return@post
            }
            updateSimulatorButtonSnapshot(
                source = "usb:$controllerId",
                controllerName = usbControllerNames[controllerId]
                    ?: getString(R.string.controller_diag_unknown_device),
                inputPath = ControllerDiagnostics.InputPath.USB_TAKEOVER,
                pressedFlags = buttonFlags,
                eventTimeMs = SystemClock.uptimeMillis(),
                leftStickX = leftStickX,
                leftStickY = leftStickY,
                rightStickX = rightStickX,
                rightStickY = rightStickY,
                leftTrigger = leftTrigger,
                rightTrigger = rightTrigger
            )
        }
    }

    override fun deviceAdded(controller: AbstractController) {
        simulatorHandler.post {
            val controllerName = snapshot.devices.firstOrNull {
                it.vendorId == controller.getVendorId() && it.productId == controller.getProductId()
            }?.name ?: getString(R.string.controller_diag_unknown_device)
            usbControllerNames[controller.getControllerId()] = controllerName
            maybeMarkShortcutTestReady()
        }
    }

    override fun deviceRemoved(controller: AbstractController) {
        simulatorHandler.post {
            usbControllerNames.remove(controller.getControllerId())
            if (simulatorInputSource == "usb:${controller.getControllerId()}") {
                resetShortcutTest()
            }
            maybeMarkShortcutTestReady()
        }
    }

    override fun reportControllerMotion(
        controllerId: Int,
        motionType: Byte,
        x: Float,
        y: Float,
        z: Float
    ) = Unit

    override fun onUsbPermissionPromptStarting() {
        usbPermissionPromptCount.incrementAndGet()
    }

    override fun onUsbPermissionPromptCompleted() {
        usbPermissionPromptCount.updateAndGet { count -> (count - 1).coerceAtLeast(0) }
        simulatorHandler.post {
            maybeMarkShortcutTestReady()
        }
    }

    private fun simulatorButtonFlag(event: KeyEvent): Int? {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> ControllerPacket.PLAY_FLAG
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A -> ControllerPacket.A_FLAG
            KeyEvent.KEYCODE_BUTTON_B -> ControllerPacket.B_FLAG
            KeyEvent.KEYCODE_BUTTON_X -> ControllerPacket.X_FLAG
            KeyEvent.KEYCODE_BUTTON_Y -> ControllerPacket.Y_FLAG
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK -> ControllerPacket.BACK_FLAG
            KeyEvent.KEYCODE_DPAD_UP -> ControllerPacket.UP_FLAG
            KeyEvent.KEYCODE_DPAD_DOWN -> ControllerPacket.DOWN_FLAG
            KeyEvent.KEYCODE_DPAD_LEFT -> ControllerPacket.LEFT_FLAG
            KeyEvent.KEYCODE_DPAD_RIGHT -> ControllerPacket.RIGHT_FLAG
            KeyEvent.KEYCODE_BUTTON_L1 -> ControllerPacket.LB_FLAG
            KeyEvent.KEYCODE_BUTTON_R1 -> ControllerPacket.RB_FLAG
            KeyEvent.KEYCODE_BUTTON_THUMBL -> ControllerPacket.LS_CLK_FLAG
            KeyEvent.KEYCODE_BUTTON_THUMBR -> ControllerPacket.RS_CLK_FLAG
            KeyEvent.KEYCODE_BUTTON_MODE -> ControllerPacket.SPECIAL_BUTTON_FLAG
            KeyEvent.KEYCODE_BUTTON_1 -> ControllerPacket.TOUCHPAD_FLAG
            KeyEvent.KEYCODE_MEDIA_RECORD -> ControllerPacket.MISC_FLAG
            KeyEvent.KEYCODE_UNKNOWN -> when (event.scanCode) {
                0x2c4 -> ControllerPacket.PADDLE1_FLAG
                0x2c5 -> ControllerPacket.PADDLE2_FLAG
                0x2c6 -> ControllerPacket.PADDLE3_FLAG
                0x2c7 -> ControllerPacket.PADDLE4_FLAG
                else -> null
            }
            else -> null
        }
    }

    companion object {
        private const val USB_RELEASE_REFRESH_DELAY_MS = 500L
        private const val DEFAULT_TEST_DURATION_SECONDS = 30
        private const val SHORTCUT_ATTEMPT_SECONDS = 5
        private const val SHORTCUT_RESULT_DISPLAY_MS = 900L
        private const val COUNTDOWN_TICK_MS = 250L
        private const val DPAD_AXIS_THRESHOLD = 0.5f
        private const val INFO_SCROLL_AXIS_THRESHOLD = 0.65f
        private const val INFO_SCROLL_REPEAT_MS = 120L
        private const val USB_NAVIGATION_AXIS_THRESHOLD = 0.65f
        private val USB_NAVIGATION_BUTTONS = intArrayOf(
            ControllerPacket.UP_FLAG,
            ControllerPacket.DOWN_FLAG,
            ControllerPacket.LEFT_FLAG,
            ControllerPacket.RIGHT_FLAG,
            ControllerPacket.A_FLAG,
            ControllerPacket.B_FLAG
        )
        private const val USB_NAVIGATION_FLAGS = ControllerPacket.UP_FLAG or
            ControllerPacket.DOWN_FLAG or ControllerPacket.LEFT_FLAG or
            ControllerPacket.RIGHT_FLAG or ControllerPacket.A_FLAG or ControllerPacket.B_FLAG
        private val SUPPORTED_TEST_DURATIONS_SECONDS = setOf(30, 60, 180)
        private const val DPAD_FLAGS = ControllerPacket.UP_FLAG or ControllerPacket.DOWN_FLAG or
            ControllerPacket.LEFT_FLAG or ControllerPacket.RIGHT_FLAG
    }
}

private const val CONTROLLER_SCROLL_REPEAT_MS = 90L
private const val MEDIUM_LANDSCAPE_MIN_WIDTH_DP = 600
private const val WIDE_LANDSCAPE_MIN_WIDTH_DP = 720
private const val COMPACT_LANDSCAPE_MAX_HEIGHT_DP = 480

private enum class ShortcutSimulatorResult {
    IDLE,
    HINT,
    MOUSE_EMULATION,
    GAME_MENU,
    PERFORMANCE_OVERLAY,
    EXIT_STREAM
}

private enum class ShortcutTestPhase {
    IDLE,
    STARTING,
    ACTIVE,
    STOPPING
}

private enum class ShortcutTestSession {
    NONE,
    GENERAL,
    SHORTCUTS
}

private enum class ShortcutAttemptState {
    INACTIVE,
    PREPARING,
    WAITING,
    SUCCEEDED,
    TIMED_OUT
}

private enum class ShortcutTestTarget {
    SYSTEM_GAME_MENU,
    USB_GAME_MENU,
    USB_MOUSE_MODE,
    PERFORMANCE_OVERLAY,
    EXIT_STREAM
}

private enum class ControllerDiagnosticTab {
    BUTTONS,
    VISUAL,
    SHORTCUTS
}

private data class ShortcutSimulatorUiState(
    val result: ShortcutSimulatorResult = ShortcutSimulatorResult.IDLE,
    val hintVisible: Boolean = false,
    val pressedFlags: Int = 0,
    val controllerName: String? = null,
    val inputPath: ControllerDiagnostics.InputPath? = null,
    val leftStickX: Float = 0f,
    val leftStickY: Float = 0f,
    val rightStickX: Float = 0f,
    val rightStickY: Float = 0f,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f
)

private object ControllerDiagnostics {
    enum class ConnectionType {
        USB,
        BLUETOOTH_OR_WIRELESS,
        BUILT_IN
    }

    enum class InputPath {
        SYSTEM,
        USB_TAKEOVER,
        UNAVAILABLE
    }

    enum class ShortcutSupport {
        SYSTEM_ALL,
        SYSTEM_EXIT_ONLY,
        USB_ALL,
        USB_EXIT_ONLY,
        UNAVAILABLE
    }

    enum class Note {
        USB_TAKEOVER_READY,
        USB_PERMISSION_REQUIRED,
        SYSTEM_USB,
        SYSTEM_WIRELESS,
        SYSTEM_BUILT_IN,
        USB_DRIVER_DISABLED_OR_UNSUPPORTED
    }

    data class Device(
        val name: String,
        val connectionType: ConnectionType,
        val inputPath: InputPath,
        val shortcutSupport: ShortcutSupport,
        val vendorId: Int,
        val productId: Int,
        val note: Note
    )

    data class Snapshot(val devices: List<Device>)

    fun scan(context: Context): Snapshot {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        val prefs = PreferenceConfiguration.readPreferences(context)
        val systemDevices = connectedSystemGamepads().toMutableList()
        val devices = mutableListOf<Device>()

        val usbDevices = usbManager?.deviceList?.values.orEmpty()
            .sortedWith(compareBy({ it.vendorId }, { it.productId }, { it.deviceId }))

        for (usbDevice in usbDevices) {
            val matchedIndex = systemDevices.indexOfFirst { inputDevice ->
                samePhysicalIds(inputDevice.vendorId, inputDevice.productId, usbDevice.vendorId, usbDevice.productId)
            }
            val matchedSystemDevice = if (matchedIndex >= 0) systemDevices.removeAt(matchedIndex) else null
            val supportedByUsbDriver = runCatching {
                UsbDriverService.shouldClaimDevice(usbDevice, true)
            }.getOrDefault(false)

            if (matchedSystemDevice == null && !supportedByUsbDriver) {
                continue
            }

            val willUseUsbTakeover = prefs.usbDriver && supportedByUsbDriver && runCatching {
                UsbDriverService.shouldClaimDevice(usbDevice, prefs.bindAllUsb)
            }.getOrDefault(false)

            val inputPath = when {
                willUseUsbTakeover -> InputPath.USB_TAKEOVER
                matchedSystemDevice != null -> InputPath.SYSTEM
                else -> InputPath.UNAVAILABLE
            }
            val note = when (inputPath) {
                InputPath.USB_TAKEOVER -> {
                    if (usbManager?.hasPermission(usbDevice) == true) Note.USB_TAKEOVER_READY
                    else Note.USB_PERMISSION_REQUIRED
                }
                InputPath.SYSTEM -> Note.SYSTEM_USB
                InputPath.UNAVAILABLE -> Note.USB_DRIVER_DISABLED_OR_UNSUPPORTED
            }

            devices += Device(
                name = usbDisplayName(usbDevice, matchedSystemDevice, context),
                connectionType = ConnectionType.USB,
                inputPath = inputPath,
                shortcutSupport = shortcutSupport(inputPath, prefs.enableStartKeyMenu),
                vendorId = usbDevice.vendorId,
                productId = usbDevice.productId,
                note = note
            )
        }

        for (inputDevice in systemDevices) {
            val connectionType = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !inputDevice.isExternal -> ConnectionType.BUILT_IN
                else -> ConnectionType.BLUETOOTH_OR_WIRELESS
            }
            devices += Device(
                name = inputDevice.name.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.controller_diag_unknown_device),
                connectionType = connectionType,
                inputPath = InputPath.SYSTEM,
                shortcutSupport = shortcutSupport(InputPath.SYSTEM, prefs.enableStartKeyMenu),
                vendorId = inputDevice.vendorId,
                productId = inputDevice.productId,
                note = when (connectionType) {
                    ConnectionType.BUILT_IN -> Note.SYSTEM_BUILT_IN
                    else -> Note.SYSTEM_WIRELESS
                }
            )
        }

        return Snapshot(
            devices.sortedWith(
                compareBy<Device>({ it.connectionType.ordinal }, { it.name.lowercase(Locale.ROOT) })
            )
        )
    }

    private fun shortcutSupport(inputPath: InputPath, startKeyActionEnabled: Boolean): ShortcutSupport {
        return when (inputPath) {
            InputPath.SYSTEM -> {
                if (startKeyActionEnabled) ShortcutSupport.SYSTEM_ALL
                else ShortcutSupport.SYSTEM_EXIT_ONLY
            }
            InputPath.USB_TAKEOVER -> {
                if (startKeyActionEnabled) ShortcutSupport.USB_ALL
                else ShortcutSupport.USB_EXIT_ONLY
            }
            InputPath.UNAVAILABLE -> ShortcutSupport.UNAVAILABLE
        }
    }

    private fun connectedSystemGamepads(): List<InputDevice> {
        return InputDevice.getDeviceIds()
            .map { deviceId -> InputDevice.getDevice(deviceId) }
            .filterNotNull()
            .filter(::isGamepad)
            .distinctBy { inputDevice ->
                inputDevice.descriptor.takeIf { it.isNotBlank() }
                    ?: "${inputDevice.vendorId}:${inputDevice.productId}:${inputDevice.name}"
            }
    }

    private fun isGamepad(inputDevice: InputDevice): Boolean {
        if (inputDevice.isVirtual) return false
        val sources = inputDevice.sources
        return sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }

    private fun samePhysicalIds(firstVendorId: Int, firstProductId: Int, secondVendorId: Int, secondProductId: Int): Boolean {
        if (firstVendorId == 0 && firstProductId == 0) return false
        if (secondVendorId == 0 && secondProductId == 0) return false
        return firstVendorId == secondVendorId && firstProductId == secondProductId
    }

    private fun usbDisplayName(usbDevice: UsbDevice, systemDevice: InputDevice?, context: Context): String {
        val usbName = runCatching { usbDevice.productName }.getOrNull()
        return usbName?.takeIf { it.isNotBlank() }
            ?: systemDevice?.name?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.controller_diag_unknown_device)
    }
}

@Composable
private fun ControllerDiagnosticScreen(
    snapshot: ControllerDiagnostics.Snapshot,
    simulatorState: ShortcutSimulatorUiState,
    shortcutTestPhase: ShortcutTestPhase,
    activeShortcutTarget: ShortcutTestTarget?,
    shortcutAttemptState: ShortcutAttemptState,
    shortcutAttemptRemainingSeconds: Int,
    selectedTab: ControllerDiagnosticTab,
    controllerInfoVisible: Boolean,
    controllerInfoScrollSequence: Int,
    controllerInfoScrollDirection: Int,
    selectedTestDurationSeconds: Int,
    remainingTestSeconds: Int,
    startKeyActionEnabled: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onShowControllerInfo: () -> Unit,
    onDismissControllerInfo: () -> Unit,
    onToggleShortcutTest: () -> Unit,
    onToggleShortcutTarget: (ShortcutTestTarget) -> Unit,
    shortcutTabEnabled: Boolean,
    onTabSelected: (ControllerDiagnosticTab) -> Unit,
    onSelectTestDuration: (Int) -> Unit
) {
    val background = colorResource(R.color.game_menu_dialog_background)
    val primary = colorResource(R.color.game_menu_text_primary)
    val secondary = colorResource(R.color.game_menu_text_secondary)
    val accent = colorResource(R.color.game_menu_accent)
    val baseColorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    val listState = rememberLazyListState()
    val controllerScrollStep = with(LocalDensity.current) { 64.dp.toPx() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val layoutDirection = LocalLayoutDirection.current
    val safeArea = WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .asPaddingValues()
    val safeRight = if (isLandscape) safeArea.calculateRightPadding(layoutDirection) else 0.dp
    val safeBottom = safeArea.calculateBottomPadding()

    LaunchedEffect(shortcutTestPhase, simulatorState.pressedFlags) {
        if (shortcutTestPhase == ShortcutTestPhase.ACTIVE) {
            val scrollDirection = when {
                isPressed(simulatorState, ControllerPacket.UP_FLAG) &&
                    !isPressed(simulatorState, ControllerPacket.DOWN_FLAG) -> -1
                isPressed(simulatorState, ControllerPacket.DOWN_FLAG) &&
                    !isPressed(simulatorState, ControllerPacket.UP_FLAG) -> 1
                else -> 0
            }
            while (scrollDirection != 0) {
                listState.scrollBy(controllerScrollStep * scrollDirection)
                delay(CONTROLLER_SCROLL_REPEAT_MS)
            }
        }
    }

    MaterialTheme(
        colorScheme = baseColorScheme.copy(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.12f),
            surface = colorResource(R.color.game_menu_card_background),
            onSurface = primary,
            surfaceVariant = colorResource(R.color.game_menu_card_background),
            onSurfaceVariant = secondary,
            outline = colorResource(R.color.game_menu_dialog_border)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = safeRight, bottom = safeBottom)
            ) {
                ControllerDiagnosticTopBar(
                    primary = primary,
                    accent = accent,
                    showTestControls = isLandscape,
                    testPhase = shortcutTestPhase,
                    testToggleEnabled = selectedTab != ControllerDiagnosticTab.SHORTCUTS ||
                        shortcutTestPhase != ShortcutTestPhase.IDLE,
                    selectedDurationSeconds = selectedTestDurationSeconds,
                    remainingSeconds = remainingTestSeconds,
                    onBack = onBack,
                    onRefresh = onRefresh,
                    onShowInfo = onShowControllerInfo,
                    onToggleTest = onToggleShortcutTest,
                    onSelectDuration = onSelectTestDuration
                )

                if (!isLandscape) {
                    Surface(
                        color = colorResource(R.color.game_menu_card_background),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ControllerTestControls(
                            testPhase = shortcutTestPhase,
                            testToggleEnabled = selectedTab != ControllerDiagnosticTab.SHORTCUTS ||
                                shortcutTestPhase != ShortcutTestPhase.IDLE,
                            selectedDurationSeconds = selectedTestDurationSeconds,
                            remainingSeconds = remainingTestSeconds,
                            onToggleTest = onToggleShortcutTest,
                            onSelectDuration = onSelectTestDuration,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 0.dp,
                        bottom = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        ShortcutSimulatorCard(
                            state = simulatorState,
                            testPhase = shortcutTestPhase,
                            activeShortcutTarget = activeShortcutTarget,
                            selectedTab = selectedTab,
                            predictedInputPath = snapshot.devices.firstOrNull()?.inputPath,
                            isLandscape = isLandscape,
                            startKeyActionEnabled = startKeyActionEnabled,
                            shortcutTabEnabled = shortcutTabEnabled,
                            onTabSelected = onTabSelected,
                            onToggleShortcutTarget = onToggleShortcutTarget
                        )
                    }
                }
            }

            if (shortcutAttemptState != ShortcutAttemptState.INACTIVE &&
                shortcutAttemptState != ShortcutAttemptState.PREPARING
            ) {
                ShortcutAttemptPopup(
                    state = shortcutAttemptState,
                    remainingSeconds = shortcutAttemptRemainingSeconds,
                    compactLandscape = isLandscape,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            if (controllerInfoVisible) {
                ControllerInfoOverlay(
                    snapshot = snapshot,
                    simulatorState = simulatorState,
                    testPhase = shortcutTestPhase,
                    scrollSequence = controllerInfoScrollSequence,
                    scrollDirection = controllerInfoScrollDirection,
                    safeRight = safeRight,
                    safeBottom = safeBottom,
                    onDismiss = onDismissControllerInfo
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortcutSimulatorCard(
    state: ShortcutSimulatorUiState,
    testPhase: ShortcutTestPhase,
    activeShortcutTarget: ShortcutTestTarget?,
    selectedTab: ControllerDiagnosticTab,
    predictedInputPath: ControllerDiagnostics.InputPath?,
    isLandscape: Boolean,
    startKeyActionEnabled: Boolean,
    shortcutTabEnabled: Boolean,
    onTabSelected: (ControllerDiagnosticTab) -> Unit,
    onToggleShortcutTarget: (ShortcutTestTarget) -> Unit
) {
    val configuration = LocalConfiguration.current
    val useWideLandscapeLayout = isLandscape &&
        configuration.screenWidthDp >= WIDE_LANDSCAPE_MIN_WIDTH_DP &&
        configuration.screenHeightDp >= COMPACT_LANDSCAPE_MAX_HEIGHT_DP
    Surface(
        color = colorResource(R.color.game_menu_card_background),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(GameMenuDimens.section),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (useWideLandscapeLayout) {
                ControllerLandscapeContent(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    shortcutTabEnabled = shortcutTabEnabled,
                    state = state,
                    predictedInputPath = predictedInputPath,
                    startKeyActionEnabled = startKeyActionEnabled,
                    testPhase = testPhase,
                    activeShortcutTarget = activeShortcutTarget,
                    onToggleShortcutTarget = onToggleShortcutTarget,
                    isLandscape = true,
                    sidebarWidth = 120.dp
                )
            } else if (isLandscape) {
                ControllerLandscapeContent(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    shortcutTabEnabled = shortcutTabEnabled,
                    state = state,
                    predictedInputPath = predictedInputPath,
                    startKeyActionEnabled = startKeyActionEnabled,
                    testPhase = testPhase,
                    activeShortcutTarget = activeShortcutTarget,
                    onToggleShortcutTarget = onToggleShortcutTarget,
                    isLandscape = true,
                    sidebarWidth = 96.dp
                )
            } else {
                Text(
                    text = stringResource(
                        when (testPhase) {
                            ShortcutTestPhase.IDLE ->
                                R.string.controller_diag_simulator_inactive_summary
                            ShortcutTestPhase.STARTING ->
                                R.string.controller_diag_simulator_starting_summary
                            ShortcutTestPhase.ACTIVE ->
                                R.string.controller_diag_simulator_active_summary
                            ShortcutTestPhase.STOPPING ->
                                R.string.controller_diag_simulator_stopping_summary
                        }
                    ),
                    color = colorResource(R.color.game_menu_text_secondary),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                ControllerTestTabs(
                    selectedTab = selectedTab,
                    shortcutTabEnabled = shortcutTabEnabled,
                    onTabSelected = onTabSelected
                )
                ControllerSelectedTabContent(
                    selectedTab = selectedTab,
                    state = state,
                    predictedInputPath = predictedInputPath,
                    startKeyActionEnabled = startKeyActionEnabled,
                    testPhase = testPhase,
                    activeShortcutTarget = activeShortcutTarget,
                    onToggleShortcutTarget = onToggleShortcutTarget,
                    isLandscape = false
                )
            }
        }
    }
}

@Composable
private fun ControllerLandscapeContent(
    selectedTab: ControllerDiagnosticTab,
    onTabSelected: (ControllerDiagnosticTab) -> Unit,
    shortcutTabEnabled: Boolean,
    state: ShortcutSimulatorUiState,
    predictedInputPath: ControllerDiagnostics.InputPath?,
    startKeyActionEnabled: Boolean,
    testPhase: ShortcutTestPhase,
    activeShortcutTarget: ShortcutTestTarget?,
    onToggleShortcutTarget: (ShortcutTestTarget) -> Unit,
    isLandscape: Boolean,
    sidebarWidth: androidx.compose.ui.unit.Dp
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Top
    ) {
        ControllerTestTabs(
            selectedTab = selectedTab,
            shortcutTabEnabled = shortcutTabEnabled,
            onTabSelected = onTabSelected,
            vertical = true,
            modifier = Modifier.width(sidebarWidth)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ControllerSelectedTabContent(
                selectedTab = selectedTab,
                state = state,
                predictedInputPath = predictedInputPath,
                startKeyActionEnabled = startKeyActionEnabled,
                testPhase = testPhase,
                activeShortcutTarget = activeShortcutTarget,
                onToggleShortcutTarget = onToggleShortcutTarget,
                isLandscape = isLandscape
            )
        }
    }
}

@Composable
private fun ControllerSelectedTabContent(
    selectedTab: ControllerDiagnosticTab,
    state: ShortcutSimulatorUiState,
    predictedInputPath: ControllerDiagnostics.InputPath?,
    startKeyActionEnabled: Boolean,
    testPhase: ShortcutTestPhase,
    activeShortcutTarget: ShortcutTestTarget?,
    onToggleShortcutTarget: (ShortcutTestTarget) -> Unit,
    isLandscape: Boolean
) {
    when (selectedTab) {
        ControllerDiagnosticTab.BUTTONS -> ControllerButtonsPanel(
            state = state,
            isLandscape = isLandscape
        )
        ControllerDiagnosticTab.VISUAL -> GamepadSilhouetteVisualization(state)
        ControllerDiagnosticTab.SHORTCUTS -> ControllerShortcutGuide(
            state = state,
            inputPath = state.inputPath ?: predictedInputPath,
            startKeyActionEnabled = startKeyActionEnabled,
            testPhase = testPhase,
            activeShortcutTarget = activeShortcutTarget,
            onToggleShortcutTarget = onToggleShortcutTarget,
            isLandscape = isLandscape
        )
    }
}

@Composable
private fun ControllerTestControls(
    testPhase: ShortcutTestPhase,
    testToggleEnabled: Boolean,
    selectedDurationSeconds: Int,
    remainingSeconds: Int,
    onToggleTest: () -> Unit,
    onSelectDuration: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    toggleWidth: androidx.compose.ui.unit.Dp = 92.dp
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showLabel) {
            Text(
                text = stringResource(R.string.controller_diag_test_label),
                color = colorResource(R.color.game_menu_text_primary),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        ShortcutTestToggle(
            testPhase = testPhase,
            enabled = testToggleEnabled,
            onClick = onToggleTest,
            modifier = Modifier.width(toggleWidth)
        )
        if (testPhase == ShortcutTestPhase.IDLE) {
            TestDurationSelector(
                selectedDurationSeconds = selectedDurationSeconds,
                onSelectDuration = onSelectDuration,
                modifier = Modifier.weight(1f)
            )
        } else {
            TestPhaseTimeTag(
                testPhase = testPhase,
                remainingSeconds = remainingSeconds
            )
        }
    }
}

@Composable
private fun ControllerTestStatus(
    state: ShortcutSimulatorUiState,
    testPhase: ShortcutTestPhase,
    modifier: Modifier = Modifier
) {
    val accent = colorResource(R.color.game_menu_accent)
    Surface(
        color = if (state.hintVisible) {
            accent.copy(alpha = 0.14f)
        } else {
            colorResource(R.color.game_menu_dialog_background)
        },
        shape = GameMenuCardShape,
        border = BorderStroke(
            GameMenuDimens.surfaceStroke,
            if (state.hintVisible) accent.copy(alpha = 0.42f)
            else colorResource(R.color.game_menu_button_border)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = GameMenuDimens.section, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        when (testPhase) {
                            ShortcutTestPhase.ACTIVE -> accent
                            ShortcutTestPhase.STARTING,
                            ShortcutTestPhase.STOPPING -> Color(0xFFFFB36B)
                            ShortcutTestPhase.IDLE ->
                                colorResource(R.color.game_menu_button_border)
                        },
                        CircleShape
                    )
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (testPhase) {
                        ShortcutTestPhase.ACTIVE -> simulatorResultLabel(state.result)
                        ShortcutTestPhase.STARTING ->
                            stringResource(R.string.controller_diag_simulator_starting)
                        ShortcutTestPhase.STOPPING ->
                            stringResource(R.string.controller_diag_simulator_stopping)
                        ShortcutTestPhase.IDLE ->
                            stringResource(R.string.controller_diag_simulator_inactive)
                    },
                    color = if (state.hintVisible) accent
                    else colorResource(R.color.game_menu_text_primary),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Text(
                    text = state.controllerName?.let {
                        stringResource(R.string.controller_diag_simulator_controller, it)
                    } ?: stringResource(R.string.controller_diag_simulator_waiting_input),
                    color = colorResource(R.color.game_menu_text_secondary),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            state.inputPath?.let { inputPath ->
                Text(
                    text = inputPathLabel(inputPath),
                    color = inputPathColor(inputPath),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ShortcutAttemptPopup(
    state: ShortcutAttemptState,
    remainingSeconds: Int,
    compactLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = colorResource(R.color.game_menu_accent)
    val statusColor = when (state) {
        ShortcutAttemptState.SUCCEEDED -> accent
        ShortcutAttemptState.TIMED_OUT -> Color(0xFFE34F63)
        ShortcutAttemptState.PREPARING -> Color(0xFFE89A3D)
        ShortcutAttemptState.WAITING -> accent
        ShortcutAttemptState.INACTIVE -> Color.Transparent
    }
    val message = when (state) {
        ShortcutAttemptState.PREPARING ->
            stringResource(R.string.controller_diag_shortcut_prompt_preparing)
        ShortcutAttemptState.WAITING -> stringResource(
            R.string.controller_diag_shortcut_prompt_waiting,
            remainingSeconds.coerceAtLeast(1)
        )
        ShortcutAttemptState.SUCCEEDED ->
            stringResource(R.string.controller_diag_shortcut_prompt_success)
        ShortcutAttemptState.TIMED_OUT ->
            stringResource(R.string.controller_diag_shortcut_prompt_timeout)
        ShortcutAttemptState.INACTIVE -> ""
    }

    Surface(
        color = colorResource(R.color.game_menu_card_background),
        shape = GameMenuCardShape,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.65f)),
        shadowElevation = 10.dp,
        modifier = modifier
            .statusBarsPadding()
            .padding(
                top = if (compactLandscape) 58.dp else 116.dp,
                start = 16.dp,
                end = 16.dp
            )
            .fillMaxWidth(0.86f)
            .widthIn(max = 380.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state == ShortcutAttemptState.PREPARING) {
                CircularProgressIndicator(
                    color = statusColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(statusColor, CircleShape)
                )
            }
            Text(
                text = message,
                color = colorResource(R.color.game_menu_text_primary),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 2
            )
        }
    }
}

@Composable
private fun ShortcutTestToggle(
    testPhase: ShortcutTestPhase,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = colorResource(R.color.game_menu_accent)
    val actionEnabled = enabled && testPhase != ShortcutTestPhase.STOPPING
    var focused by remember { mutableStateOf(false) }
    val actionColor = when (testPhase) {
        ShortcutTestPhase.IDLE -> accent
        ShortcutTestPhase.STARTING -> Color(0xFFE89A3D)
        ShortcutTestPhase.ACTIVE -> Color(0xFFE34F63)
        ShortcutTestPhase.STOPPING -> accent.copy(alpha = 0.12f)
    }
    val contentColor = when {
        !actionEnabled -> colorResource(R.color.game_menu_text_secondary).copy(alpha = 0.55f)
        focused -> actionColor
        else -> Color.White
    }
    Surface(
        color = when {
            !actionEnabled -> colorResource(R.color.game_menu_dialog_background)
            focused -> colorResource(R.color.game_menu_card_background)
            else -> actionColor
        },
        shape = GameMenuCardShape,
        border = BorderStroke(
            if (focused) 2.dp else GameMenuDimens.surfaceStroke,
            if (actionEnabled) actionColor
            else colorResource(R.color.game_menu_button_border).copy(alpha = 0.55f)
        ),
        modifier = modifier
            .heightIn(min = 44.dp)
            .gamepadFocusOutline(GameMenuCardShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = actionEnabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(
                    if (testPhase == ShortcutTestPhase.IDLE) R.drawable.ic_play
                    else R.drawable.ic_stop_test
                ),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = when (testPhase) {
                    ShortcutTestPhase.IDLE -> stringResource(R.string.controller_diag_start_test)
                    ShortcutTestPhase.STARTING -> stringResource(R.string.controller_diag_cancel_start)
                    ShortcutTestPhase.ACTIVE -> stringResource(R.string.controller_diag_stop_test)
                    ShortcutTestPhase.STOPPING ->
                        stringResource(R.string.controller_diag_stopping_test)
                },
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TestPhaseTimeTag(
    testPhase: ShortcutTestPhase,
    remainingSeconds: Int
) {
    val accent = colorResource(R.color.game_menu_accent)
    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (testPhase == ShortcutTestPhase.STARTING) {
            CircularProgressIndicator(
                color = accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = when (testPhase) {
                ShortcutTestPhase.ACTIVE -> stringResource(
                    R.string.controller_diag_remaining_time,
                    formatCountdown(remainingSeconds)
                )
                ShortcutTestPhase.STARTING ->
                    stringResource(R.string.controller_diag_simulator_starting)
                ShortcutTestPhase.STOPPING ->
                    stringResource(R.string.controller_diag_simulator_stopping)
                ShortcutTestPhase.IDLE -> ""
            },
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun TestDurationSelector(
    selectedDurationSeconds: Int,
    onSelectDuration: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(GameMenuCardShape)
            .border(
                GameMenuDimens.surfaceStroke,
                colorResource(R.color.game_menu_button_border),
                GameMenuCardShape
            )
    ) {
        TestDurationSegment(
            label = stringResource(R.string.controller_diag_duration_thirty_seconds),
            selected = selectedDurationSeconds == 30,
            onClick = { onSelectDuration(30) },
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(GameMenuDimens.surfaceStroke)
                .fillMaxSize()
                .background(colorResource(R.color.game_menu_button_border))
        )
        TestDurationSegment(
            label = stringResource(R.string.controller_diag_duration_one_minute),
            selected = selectedDurationSeconds == 60,
            onClick = { onSelectDuration(60) },
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(GameMenuDimens.surfaceStroke)
                .fillMaxSize()
                .background(colorResource(R.color.game_menu_button_border))
        )
        TestDurationSegment(
            label = stringResource(R.string.controller_diag_duration_three_minutes),
            selected = selectedDurationSeconds == 180,
            onClick = { onSelectDuration(180) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TestDurationSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = colorResource(R.color.game_menu_accent)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent)
            .gamepadFocusOutline(GameMenuCardShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) accent else colorResource(R.color.game_menu_text_secondary),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun ControllerTestTabs(
    selectedTab: ControllerDiagnosticTab,
    shortcutTabEnabled: Boolean,
    onTabSelected: (ControllerDiagnosticTab) -> Unit,
    vertical: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (vertical) {
        Column(modifier = modifier.fillMaxWidth()) {
            ControllerSidebarTab(
                label = stringResource(R.string.controller_diag_tab_buttons),
                selected = selectedTab == ControllerDiagnosticTab.BUTTONS,
                onClick = { onTabSelected(ControllerDiagnosticTab.BUTTONS) }
            )
            ControllerSidebarTab(
                label = stringResource(R.string.controller_diag_tab_visual),
                selected = selectedTab == ControllerDiagnosticTab.VISUAL,
                onClick = { onTabSelected(ControllerDiagnosticTab.VISUAL) }
            )
            ControllerSidebarTab(
                label = stringResource(R.string.controller_diag_tab_shortcuts),
                selected = selectedTab == ControllerDiagnosticTab.SHORTCUTS,
                enabled = shortcutTabEnabled,
                onClick = { onTabSelected(ControllerDiagnosticTab.SHORTCUTS) }
            )
        }
        return
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ControllerTestTab(
            label = stringResource(R.string.controller_diag_tab_buttons),
            selected = selectedTab == ControllerDiagnosticTab.BUTTONS,
            onClick = { onTabSelected(ControllerDiagnosticTab.BUTTONS) },
            modifier = Modifier.weight(1f)
        )
        ControllerTestTab(
            label = stringResource(R.string.controller_diag_tab_visual),
            selected = selectedTab == ControllerDiagnosticTab.VISUAL,
            onClick = { onTabSelected(ControllerDiagnosticTab.VISUAL) },
            modifier = Modifier.weight(1f)
        )
        ControllerTestTab(
            label = stringResource(R.string.controller_diag_tab_shortcuts),
            selected = selectedTab == ControllerDiagnosticTab.SHORTCUTS,
            enabled = shortcutTabEnabled,
            onClick = { onTabSelected(ControllerDiagnosticTab.SHORTCUTS) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ControllerTestTab(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = colorResource(R.color.game_menu_accent)
    Column(
        modifier = modifier
            .gamepadFocusOutline(GameMenuCardShape)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = when {
                    selected -> accent
                    enabled -> colorResource(R.color.game_menu_text_secondary)
                    else -> colorResource(R.color.game_menu_text_secondary).copy(alpha = 0.38f)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) accent else Color.Transparent)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControllerButtonsPanel(
    state: ShortcutSimulatorUiState,
    isLandscape: Boolean
) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val columnCount = when {
        !isLandscape -> 2
        widthDp >= WIDE_LANDSCAPE_MIN_WIDTH_DP -> 4
        widthDp >= MEDIUM_LANDSCAPE_MIN_WIDTH_DP -> 3
        else -> 2
    }
    val cardSpacing = if (isLandscape) 4.dp else 10.dp
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (columnCount == 4) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(cardSpacing)
            ) {
                ControllerDirectionSection(state, Modifier.weight(1f).fillMaxHeight())
                ControllerSticksSection(state, Modifier.weight(1f).fillMaxHeight())
                ControllerFaceSection(state, Modifier.weight(1f).fillMaxHeight())
                ControllerShoulderSection(state, Modifier.weight(1f).fillMaxHeight())
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(cardSpacing)
            ) {
                ControllerFunctionSection(state, Modifier.weight(1f).fillMaxHeight())
                ControllerExtraSection(state, Modifier.weight(1f).fillMaxHeight())
            }
        } else if (columnCount == 3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(cardSpacing)
            ) {
                ControllerDirectionSection(state, Modifier.weight(1f).fillMaxHeight())
                ControllerSticksSection(state, Modifier.weight(1f).fillMaxHeight())
                ControllerFaceSection(state, Modifier.weight(1f).fillMaxHeight())
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(cardSpacing)
            ) {
                ControllerShoulderSection(state, Modifier.weight(1f).fillMaxHeight())
                ControllerFunctionSection(state, Modifier.weight(1f).fillMaxHeight())
                ControllerExtraSection(state, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(cardSpacing)
            ) {
                ControllerDirectionSection(state, Modifier.weight(1f).fillMaxHeight())
                ControllerSticksSection(state, Modifier.weight(1f).fillMaxHeight())
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(cardSpacing)
            ) {
                ControllerFaceSection(state, Modifier.weight(1f).fillMaxHeight())
                ControllerShoulderSection(state, Modifier.weight(1f).fillMaxHeight())
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(cardSpacing)
            ) {
                ControllerFunctionSection(state, Modifier.weight(1f).fillMaxHeight())
                ControllerExtraSection(state, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun ControllerSidebarTab(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val accent = colorResource(R.color.game_menu_accent)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(if (selected) accent.copy(alpha = 0.10f) else Color.Transparent)
            .gamepadFocusOutline(RectangleShape)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .background(if (selected) accent else Color.Transparent)
        )
        Text(
            text = label,
            color = when {
                selected -> accent
                enabled -> colorResource(R.color.game_menu_text_secondary)
                else -> colorResource(R.color.game_menu_text_secondary).copy(alpha = 0.38f)
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
    }
}

@Composable
private fun ControllerDirectionSection(state: ShortcutSimulatorUiState, modifier: Modifier) {
    ControllerButtonSection(
        title = stringResource(R.string.controller_diag_group_direction),
        modifier = modifier
    ) {
        ControllerDpadLayout(state)
    }
}

@Composable
private fun ControllerSticksSection(state: ShortcutSimulatorUiState, modifier: Modifier) {
    ControllerButtonSection(
        title = stringResource(R.string.controller_diag_group_sticks),
        modifier = modifier
    ) {
        ControllerAxisIndicator(
            label = stringResource(R.string.controller_diag_left_stick),
            x = state.leftStickX,
            y = state.leftStickY,
            pressed = isPressed(state, ControllerPacket.LS_CLK_FLAG)
        )
        ControllerAxisIndicator(
            label = stringResource(R.string.controller_diag_right_stick),
            x = state.rightStickX,
            y = state.rightStickY,
            pressed = isPressed(state, ControllerPacket.RS_CLK_FLAG)
        )
    }
}

@Composable
private fun ControllerFaceSection(state: ShortcutSimulatorUiState, modifier: Modifier) {
    ControllerButtonSection(
        title = stringResource(R.string.controller_diag_group_face_buttons),
        modifier = modifier
    ) {
        ControllerFaceButtonLayout(state)
    }
}

@Composable
private fun ControllerShoulderSection(state: ShortcutSimulatorUiState, modifier: Modifier) {
    ControllerButtonSection(
        title = stringResource(R.string.controller_diag_group_shoulders),
        modifier = modifier
    ) {
        ControllerShoulderButtonLayout(state)
    }
}

@Composable
private fun ControllerFunctionSection(state: ShortcutSimulatorUiState, modifier: Modifier) {
    ControllerButtonSection(
        title = stringResource(R.string.controller_diag_group_function),
        modifier = modifier
    ) {
        ControllerFunctionButtonLayout(state)
    }
}

@Composable
private fun ControllerExtraSection(state: ShortcutSimulatorUiState, modifier: Modifier) {
    ControllerButtonSection(
        title = stringResource(R.string.controller_diag_group_extra),
        modifier = modifier
    ) {
        ControllerPaddleButtonLayout(state)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControllerButtonSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val compact = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val shape = if (compact) RectangleShape else GameMenuCardShape
    Surface(
        color = colorResource(R.color.game_menu_dialog_background),
        shape = shape,
        border = BorderStroke(
            GameMenuDimens.surfaceStroke,
            colorResource(R.color.game_menu_button_border)
        ),
        modifier = modifier
            .heightIn(min = if (compact) 88.dp else 116.dp)
            .gamepadFocusOutline(shape)
            .focusable()
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 6.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
        ) {
            Text(
                text = title,
                color = colorResource(R.color.game_menu_text_secondary),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
            content()
        }
    }
}

@Composable
private fun ControllerDpadLayout(state: ShortcutSimulatorUiState) {
    ControllerCrossLayout(
        topLabel = "↑",
        topPressed = isPressed(state, ControllerPacket.UP_FLAG),
        leftLabel = "←",
        leftPressed = isPressed(state, ControllerPacket.LEFT_FLAG),
        rightLabel = "→",
        rightPressed = isPressed(state, ControllerPacket.RIGHT_FLAG),
        bottomLabel = "↓",
        bottomPressed = isPressed(state, ControllerPacket.DOWN_FLAG)
    )
}

@Composable
private fun ControllerFaceButtonLayout(state: ShortcutSimulatorUiState) {
    ControllerCrossLayout(
        topLabel = "Y",
        topPressed = isPressed(state, ControllerPacket.Y_FLAG),
        leftLabel = "X",
        leftPressed = isPressed(state, ControllerPacket.X_FLAG),
        rightLabel = "B",
        rightPressed = isPressed(state, ControllerPacket.B_FLAG),
        bottomLabel = "A",
        bottomPressed = isPressed(state, ControllerPacket.A_FLAG)
    )
}

@Composable
private fun ControllerCrossLayout(
    topLabel: String,
    topPressed: Boolean,
    leftLabel: String,
    leftPressed: Boolean,
    rightLabel: String,
    rightPressed: Boolean,
    bottomLabel: String,
    bottomPressed: Boolean
) {
    val compact = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val keySize = if (compact) 32.dp else 42.dp
    val centerGap = if (compact) 18.dp else 26.dp
    val crossOffset = (keySize + centerGap) / 2
    val clusterSize = keySize * 2 + centerGap
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(clusterSize),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(modifier = Modifier.size(clusterSize)) {
            ControllerDiagramKey(
                topLabel,
                topPressed,
                Modifier.offset(x = crossOffset)
            )
            ControllerDiagramKey(
                leftLabel,
                leftPressed,
                Modifier.offset(y = crossOffset)
            )
            ControllerDiagramKey(
                rightLabel,
                rightPressed,
                Modifier.offset(x = keySize + centerGap, y = crossOffset)
            )
            ControllerDiagramKey(
                bottomLabel,
                bottomPressed,
                Modifier.offset(x = crossOffset, y = keySize + centerGap)
            )
        }
    }
}

@Composable
private fun ControllerShoulderButtonLayout(state: ShortcutSimulatorUiState) {
    val spacing = if (
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    ) 4.dp else 6.dp
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        ControllerPairedKeys(
            leftLabel = "LT",
            leftPressed = state.leftTrigger > ANALOG_ACTIVE_THRESHOLD,
            rightLabel = "RT",
            rightPressed = state.rightTrigger > ANALOG_ACTIVE_THRESHOLD
        )
        ControllerPairedKeys(
            leftLabel = "LB",
            leftPressed = isPressed(state, ControllerPacket.LB_FLAG),
            rightLabel = "RB",
            rightPressed = isPressed(state, ControllerPacket.RB_FLAG)
        )
        ControllerPairedKeys(
            leftLabel = "L3",
            leftPressed = isPressed(state, ControllerPacket.LS_CLK_FLAG),
            rightLabel = "R3",
            rightPressed = isPressed(state, ControllerPacket.RS_CLK_FLAG)
        )
    }
}

@Composable
private fun ControllerPairedKeys(
    leftLabel: String,
    leftPressed: Boolean,
    rightLabel: String,
    rightPressed: Boolean
) {
    val spacing = if (
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    ) 4.dp else 6.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ControllerCompactKey(leftLabel, leftPressed, Modifier.weight(1f))
        Spacer(modifier = Modifier.width(spacing))
        ControllerCompactKey(rightLabel, rightPressed, Modifier.weight(1f))
    }
}

@Composable
private fun ControllerFunctionButtonLayout(state: ShortcutSimulatorUiState) {
    val spacing = if (
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    ) 4.dp else 6.dp
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        ControllerPairedKeys(
            leftLabel = "Select",
            leftPressed = isPressed(state, ControllerPacket.BACK_FLAG),
            rightLabel = "Start",
            rightPressed = isPressed(state, ControllerPacket.PLAY_FLAG)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ControllerCompactKey(
                "Share",
                isPressed(state, ControllerPacket.MISC_FLAG),
                Modifier.weight(1f),
                fontSize = 8.sp
            )
            ControllerCompactKey(
                "Home",
                isPressed(state, ControllerPacket.SPECIAL_BUTTON_FLAG),
                Modifier.weight(1f),
                fontSize = 8.sp
            )
            ControllerCompactKey(
                "Touch",
                isPressed(state, ControllerPacket.TOUCHPAD_FLAG),
                Modifier.weight(1f),
                fontSize = 8.sp
            )
        }
    }
}

@Composable
private fun ControllerPaddleButtonLayout(state: ShortcutSimulatorUiState) {
    val spacing = if (
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    ) 4.dp else 6.dp
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        ControllerPairedKeys(
            leftLabel = "P1",
            leftPressed = isPressed(state, ControllerPacket.PADDLE1_FLAG),
            rightLabel = "P4",
            rightPressed = isPressed(state, ControllerPacket.PADDLE4_FLAG)
        )
        ControllerPairedKeys(
            leftLabel = "P2",
            leftPressed = isPressed(state, ControllerPacket.PADDLE2_FLAG),
            rightLabel = "P3",
            rightPressed = isPressed(state, ControllerPacket.PADDLE3_FLAG)
        )
    }
}

@Composable
private fun ControllerDiagramKey(
    label: String,
    pressed: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = colorResource(R.color.game_menu_accent)
    val compact = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Box(
        modifier = modifier
            .size(if (compact) 32.dp else 42.dp)
            .background(
                if (pressed) accent else colorResource(R.color.game_menu_card_background),
                CircleShape
            )
            .border(
                GameMenuDimens.surfaceStroke,
                if (pressed) accent else colorResource(R.color.game_menu_button_border),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (pressed) Color.White else colorResource(R.color.game_menu_text_secondary),
            fontSize = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ControllerCompactKey(
    label: String,
    pressed: Boolean,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 10.sp
) {
    val accent = colorResource(R.color.game_menu_accent)
    val compact = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Box(
        modifier = modifier
            .height(if (compact) 28.dp else 34.dp)
            .background(
                if (pressed) accent else colorResource(R.color.game_menu_card_background),
                CircleShape
            )
            .border(
                GameMenuDimens.surfaceStroke,
                if (pressed) accent else colorResource(R.color.game_menu_button_border),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (pressed) Color.White else colorResource(R.color.game_menu_text_secondary),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun ControllerAxisIndicator(
    label: String,
    x: Float,
    y: Float,
    pressed: Boolean
) {
    val accent = colorResource(R.color.game_menu_accent)
    val compact = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val active = pressed || kotlin.math.abs(x) > ANALOG_ACTIVE_THRESHOLD ||
        kotlin.math.abs(y) > ANALOG_ACTIVE_THRESHOLD
    Surface(
        color = accent.copy(alpha = if (active) 0.16f else 0.03f),
        shape = CircleShape,
        border = BorderStroke(
            GameMenuDimens.surfaceStroke,
            accent.copy(alpha = if (active) 0.55f else 0.12f)
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 10.dp,
                vertical = if (compact) 4.dp else 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (active) accent else colorResource(R.color.game_menu_button_border),
                        CircleShape
                    )
            )
            Text(
                text = "$label  ${String.format(Locale.US, "%.2f, %.2f", x, y)}",
                color = if (active) accent else colorResource(R.color.game_menu_text_secondary),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControllerShortcutGuide(
    state: ShortcutSimulatorUiState,
    inputPath: ControllerDiagnostics.InputPath?,
    startKeyActionEnabled: Boolean,
    testPhase: ShortcutTestPhase,
    activeShortcutTarget: ShortcutTestTarget?,
    onToggleShortcutTarget: (ShortcutTestTarget) -> Unit,
    isLandscape: Boolean
) {
    val systemPathLabel = inputPathLabel(ControllerDiagnostics.InputPath.SYSTEM)
    val usbPathLabel = inputPathLabel(ControllerDiagnostics.InputPath.USB_TAKEOVER)
    val performancePressedCount = listOf(
        ControllerPacket.BACK_FLAG,
        ControllerPacket.LB_FLAG,
        ControllerPacket.RB_FLAG,
        ControllerPacket.X_FLAG
    ).count { flag -> isPressed(state, flag) }
    fun isUnderTest(target: ShortcutTestTarget): Boolean {
        return testPhase != ShortcutTestPhase.IDLE &&
            activeShortcutTarget == target
    }
    fun keyActive(target: ShortcutTestTarget, flag: Int): Boolean {
        return isUnderTest(target) && isPressed(state, flag)
    }
    val pressedExitKeyCount = listOf(
        ControllerPacket.PLAY_FLAG,
        ControllerPacket.BACK_FLAG,
        ControllerPacket.LB_FLAG,
        ControllerPacket.RB_FLAG
    ).count { flag -> isPressed(state, flag) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!startKeyActionEnabled) {
            Text(
                text = stringResource(R.string.controller_diag_start_shortcut_disabled),
                color = colorResource(R.color.game_menu_text_secondary),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val horizontalGap = if (isLandscape) 4.dp else 8.dp
            val columnCount = when {
                !isLandscape -> 1
                maxWidth >= 330.dp -> 3
                maxWidth >= 220.dp -> 2
                else -> 1
            }
            val cardWidth = (maxWidth - horizontalGap * (columnCount - 1)) / columnCount
            val cardModifier = Modifier.width(cardWidth)

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = columnCount,
                horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (startKeyActionEnabled) {
                    ControllerShortcutCard(
                        title = stringResource(R.string.controller_diag_shortcut_menu_title),
                        pathLabel = systemPathLabel,
                        summary = stringResource(R.string.controller_diag_shortcut_menu_system),
                        keys = listOf(
                            "Start" to keyActive(
                                ShortcutTestTarget.SYSTEM_GAME_MENU,
                                ControllerPacket.PLAY_FLAG
                            )
                        ),
                        recognized = isUnderTest(ShortcutTestTarget.SYSTEM_GAME_MENU) &&
                            inputPath == ControllerDiagnostics.InputPath.SYSTEM &&
                            state.result == ShortcutSimulatorResult.GAME_MENU,
                        inProgress = isUnderTest(ShortcutTestTarget.SYSTEM_GAME_MENU) &&
                            inputPath == ControllerDiagnostics.InputPath.SYSTEM &&
                            isPressed(state, ControllerPacket.PLAY_FLAG),
                        testRunning = isUnderTest(ShortcutTestTarget.SYSTEM_GAME_MENU),
                        testSelected = activeShortcutTarget == ShortcutTestTarget.SYSTEM_GAME_MENU,
                        onToggleTest = {
                            onToggleShortcutTarget(ShortcutTestTarget.SYSTEM_GAME_MENU)
                        },
                        modifier = cardModifier
                    )
                }
                if (startKeyActionEnabled) {
                    ControllerShortcutCard(
                        title = stringResource(R.string.controller_diag_shortcut_menu_title),
                        pathLabel = usbPathLabel,
                        summary = stringResource(R.string.controller_diag_shortcut_menu_usb),
                        keys = listOf(
                            "Start" to keyActive(
                                ShortcutTestTarget.USB_GAME_MENU,
                                ControllerPacket.PLAY_FLAG
                            ),
                            "B" to keyActive(
                                ShortcutTestTarget.USB_GAME_MENU,
                                ControllerPacket.B_FLAG
                            )
                        ),
                        recognized = isUnderTest(ShortcutTestTarget.USB_GAME_MENU) &&
                            inputPath == ControllerDiagnostics.InputPath.USB_TAKEOVER &&
                            state.result == ShortcutSimulatorResult.GAME_MENU,
                        inProgress = isUnderTest(ShortcutTestTarget.USB_GAME_MENU) &&
                            inputPath == ControllerDiagnostics.InputPath.USB_TAKEOVER &&
                            (state.hintVisible || isPressed(state, ControllerPacket.PLAY_FLAG)),
                        testRunning = isUnderTest(ShortcutTestTarget.USB_GAME_MENU),
                        testSelected = activeShortcutTarget == ShortcutTestTarget.USB_GAME_MENU,
                        onToggleTest = {
                            onToggleShortcutTarget(ShortcutTestTarget.USB_GAME_MENU)
                        },
                        modifier = cardModifier
                    )
                }
                if (startKeyActionEnabled) {
                    ControllerShortcutCard(
                        title = stringResource(R.string.controller_diag_shortcut_mouse_title),
                        pathLabel = usbPathLabel,
                        summary = stringResource(R.string.controller_diag_shortcut_mouse_usb),
                        keys = listOf(
                            "Start" to keyActive(
                                ShortcutTestTarget.USB_MOUSE_MODE,
                                ControllerPacket.PLAY_FLAG
                            )
                        ),
                        recognized = isUnderTest(ShortcutTestTarget.USB_MOUSE_MODE) &&
                            inputPath == ControllerDiagnostics.InputPath.USB_TAKEOVER &&
                            state.result == ShortcutSimulatorResult.MOUSE_EMULATION,
                        inProgress = isUnderTest(ShortcutTestTarget.USB_MOUSE_MODE) &&
                            inputPath == ControllerDiagnostics.InputPath.USB_TAKEOVER &&
                            (state.hintVisible || isPressed(state, ControllerPacket.PLAY_FLAG)),
                        testRunning = isUnderTest(ShortcutTestTarget.USB_MOUSE_MODE),
                        testSelected = activeShortcutTarget == ShortcutTestTarget.USB_MOUSE_MODE,
                        onToggleTest = {
                            onToggleShortcutTarget(ShortcutTestTarget.USB_MOUSE_MODE)
                        },
                        modifier = cardModifier
                    )
                }

                ControllerShortcutCard(
                    title = stringResource(R.string.controller_diag_shortcut_performance_title),
                    summary = stringResource(R.string.controller_diag_shortcut_performance_summary),
                    keys = listOf(
                            "Select" to keyActive(
                                ShortcutTestTarget.PERFORMANCE_OVERLAY,
                                ControllerPacket.BACK_FLAG
                            ),
                            "L1/LB" to keyActive(
                                ShortcutTestTarget.PERFORMANCE_OVERLAY,
                                ControllerPacket.LB_FLAG
                            ),
                            "R1/RB" to keyActive(
                                ShortcutTestTarget.PERFORMANCE_OVERLAY,
                                ControllerPacket.RB_FLAG
                            ),
                            "X" to keyActive(
                                ShortcutTestTarget.PERFORMANCE_OVERLAY,
                                ControllerPacket.X_FLAG
                            )
                    ),
                    recognized = isUnderTest(ShortcutTestTarget.PERFORMANCE_OVERLAY) &&
                        state.result == ShortcutSimulatorResult.PERFORMANCE_OVERLAY,
                    inProgress = isUnderTest(ShortcutTestTarget.PERFORMANCE_OVERLAY) &&
                        performancePressedCount >= 2,
                    testRunning = isUnderTest(ShortcutTestTarget.PERFORMANCE_OVERLAY),
                    testSelected = activeShortcutTarget == ShortcutTestTarget.PERFORMANCE_OVERLAY,
                    onToggleTest = {
                        onToggleShortcutTarget(ShortcutTestTarget.PERFORMANCE_OVERLAY)
                    },
                    modifier = cardModifier
                )

                ControllerShortcutCard(
                    title = stringResource(R.string.controller_diag_shortcut_exit_title),
                    summary = stringResource(R.string.controller_diag_shortcut_exit_summary),
                    keys = listOf(
                            "Start" to keyActive(
                                ShortcutTestTarget.EXIT_STREAM,
                                ControllerPacket.PLAY_FLAG
                            ),
                            "Select" to keyActive(
                                ShortcutTestTarget.EXIT_STREAM,
                                ControllerPacket.BACK_FLAG
                            ),
                            "LB" to keyActive(
                                ShortcutTestTarget.EXIT_STREAM,
                                ControllerPacket.LB_FLAG
                            ),
                            "RB" to keyActive(
                                ShortcutTestTarget.EXIT_STREAM,
                                ControllerPacket.RB_FLAG
                            )
                    ),
                    recognized = isUnderTest(ShortcutTestTarget.EXIT_STREAM) &&
                        state.result == ShortcutSimulatorResult.EXIT_STREAM,
                    inProgress = isUnderTest(ShortcutTestTarget.EXIT_STREAM) &&
                        pressedExitKeyCount >= 2,
                    testRunning = isUnderTest(ShortcutTestTarget.EXIT_STREAM),
                    testSelected = activeShortcutTarget == ShortcutTestTarget.EXIT_STREAM,
                    onToggleTest = { onToggleShortcutTarget(ShortcutTestTarget.EXIT_STREAM) },
                    modifier = cardModifier
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControllerShortcutCard(
    title: String,
    pathLabel: String? = null,
    summary: String,
    keys: List<Pair<String, Boolean>>,
    recognized: Boolean,
    inProgress: Boolean,
    testRunning: Boolean,
    testSelected: Boolean,
    onToggleTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = colorResource(R.color.game_menu_accent)
    val compact = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val shape = if (compact) RectangleShape else GameMenuCardShape
    val statusColor = when {
        recognized -> accent
        inProgress -> Color(0xFFFFB36B)
        else -> colorResource(R.color.game_menu_text_secondary)
    }
    Surface(
        color = if (recognized) accent.copy(alpha = 0.10f)
        else colorResource(R.color.game_menu_dialog_background),
        shape = shape,
        border = BorderStroke(
            GameMenuDimens.surfaceStroke,
            if (recognized) accent.copy(alpha = 0.48f)
            else colorResource(R.color.game_menu_button_border)
        ),
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 7.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
            ) {
                Text(
                    text = if (pathLabel == null) title else "$title · $pathLabel",
                    color = colorResource(R.color.game_menu_text_primary),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                ShortcutCardTestButton(
                    selected = testSelected,
                    onClick = onToggleTest,
                    onFocused = {
                        scope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                )
            }
            if (testRunning) {
                Text(
                    text = stringResource(
                        when {
                            recognized -> R.string.controller_diag_shortcut_recognized
                            inProgress -> R.string.controller_diag_shortcut_in_progress
                            else -> R.string.controller_diag_shortcut_waiting
                        }
                    ),
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
            ) {
                keys.forEach { (label, active) ->
                    ShortcutKeyChip(label = label, active = active)
                }
            }
            Text(
                text = summary,
                color = colorResource(R.color.game_menu_text_secondary),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun ShortcutKeyChip(label: String, active: Boolean) {
    val accent = colorResource(R.color.game_menu_accent)
    Text(
        text = label,
        color = if (active) Color.White else colorResource(R.color.game_menu_text_secondary),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(
                if (active) accent else colorResource(R.color.game_menu_card_background),
                CircleShape
            )
            .border(
                GameMenuDimens.surfaceStroke,
                if (active) accent else colorResource(R.color.game_menu_button_border),
                CircleShape
            )
            .padding(horizontal = 9.dp, vertical = 5.dp)
    )
}

@Composable
private fun GamepadSilhouetteVisualization(state: ShortcutSimulatorUiState) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val accent = colorResource(R.color.game_menu_accent)
    val bodyColor = colorResource(R.color.game_menu_card_background)
    val outline = colorResource(R.color.game_menu_button_border)
    val idleControl = colorResource(R.color.game_menu_dialog_background)
    val textColor = colorResource(R.color.game_menu_text_secondary)
    val shape = if (isLandscape) RectangleShape else GameMenuCardShape
    val visualizationModifier = if (isLandscape) {
        Modifier
            .fillMaxWidth()
            .height((configuration.screenHeightDp.dp - 120.dp).coerceIn(180.dp, 360.dp))
    } else {
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.67f)
    }

    Surface(
        color = colorResource(R.color.game_menu_dialog_background),
        shape = shape,
        border = BorderStroke(GameMenuDimens.surfaceStroke, outline),
        modifier = visualizationModifier
            .gamepadFocusOutline(shape)
            .focusable()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            val designWidth = 1000f
            val designHeight = 600f
            val scale = minOf(size.width / designWidth, size.height / designHeight) * 1.13f
            val originX = (size.width - designWidth * scale) / 2f
            val originY = (size.height - designHeight * scale) / 2f
            fun px(x: Float) = originX + x * scale
            fun py(y: Float) = originY + y * scale
            fun point(x: Float, y: Float) = Offset(px(x), py(y))
            fun scaledSize(width: Float, height: Float) = Size(width * scale, height * scale)
            fun activeColor(active: Boolean) = if (active) accent else idleControl
            fun activeStroke(active: Boolean) = if (active) accent else outline

            val bodyPath = Path().apply {
                moveTo(px(500f), py(128f))
                cubicTo(px(420f), py(112f), px(315f), py(116f), px(250f), py(145f))
                cubicTo(px(178f), py(177f), px(145f), py(260f), px(105f), py(382f))
                cubicTo(px(72f), py(482f), px(78f), py(548f), px(133f), py(556f))
                cubicTo(px(196f), py(565f), px(225f), py(438f), px(315f), py(418f))
                cubicTo(px(382f), py(403f), px(430f), py(445f), px(500f), py(445f))
                cubicTo(px(570f), py(445f), px(618f), py(403f), px(685f), py(418f))
                cubicTo(px(775f), py(438f), px(804f), py(565f), px(867f), py(556f))
                cubicTo(px(922f), py(548f), px(928f), py(482f), px(895f), py(382f))
                cubicTo(px(855f), py(260f), px(822f), py(177f), px(750f), py(145f))
                cubicTo(px(685f), py(116f), px(580f), py(112f), px(500f), py(128f))
                close()
            }
            drawPath(bodyPath, bodyColor)
            drawPath(
                bodyPath,
                outline,
                style = Stroke(width = 4f * scale, cap = StrokeCap.Round)
            )

            fun drawShoulder(
                x: Float,
                y: Float,
                width: Float,
                height: Float,
                active: Boolean,
                label: String,
                analogValue: Float? = null
            ) {
                val corner = CornerRadius(16f * scale, 16f * scale)
                val topLeft = point(x, y)
                drawRoundRect(
                    if (analogValue == null) activeColor(active) else idleControl,
                    topLeft,
                    scaledSize(width, height),
                    corner
                )
                analogValue?.coerceIn(0f, 1f)?.takeIf { it > 0f }?.let { value ->
                    drawRoundRect(
                        accent.copy(alpha = 0.82f),
                        topLeft,
                        scaledSize(width * value, height),
                        corner
                    )
                }
                drawRoundRect(
                    activeStroke(active),
                    topLeft,
                    scaledSize(width, height),
                    corner,
                    style = Stroke(width = 3f * scale)
                )
                drawControllerLabel(
                    label,
                    point(x + width / 2f, y + height / 2f),
                    if (active && analogValue == null) Color.White else textColor,
                    scale
                )
            }
            drawShoulder(
                185f, 66f, 150f, 48f,
                state.leftTrigger > ANALOG_ACTIVE_THRESHOLD, "LT", state.leftTrigger
            )
            drawShoulder(
                665f, 66f, 150f, 48f,
                state.rightTrigger > ANALOG_ACTIVE_THRESHOLD, "RT", state.rightTrigger
            )
            drawShoulder(205f, 122f, 135f, 42f,
                isPressed(state, ControllerPacket.LB_FLAG), "LB")
            drawShoulder(660f, 122f, 135f, 42f,
                isPressed(state, ControllerPacket.RB_FLAG), "RB")

            fun drawStick(
                centerX: Float,
                centerY: Float,
                axisX: Float,
                axisY: Float,
                pressed: Boolean,
                label: String
            ) {
                val center = point(centerX, centerY)
                drawCircle(idleControl, 66f * scale, center)
                drawCircle(
                    activeStroke(pressed),
                    66f * scale,
                    center,
                    style = Stroke(width = 4f * scale)
                )
                val active = pressed || kotlin.math.abs(axisX) > ANALOG_ACTIVE_THRESHOLD ||
                    kotlin.math.abs(axisY) > ANALOG_ACTIVE_THRESHOLD
                val knob = point(
                    centerX + axisX.coerceIn(-1f, 1f) * 30f,
                    centerY + axisY.coerceIn(-1f, 1f) * 30f
                )
                drawCircle(if (active) accent else outline, 36f * scale, knob)
                drawControllerLabel(label, point(centerX, centerY + 91f), textColor, scale, 22f)
            }
            drawStick(
                360f, 235f, state.leftStickX, state.leftStickY,
                isPressed(state, ControllerPacket.LS_CLK_FLAG), "L3"
            )
            drawStick(
                630f, 350f, state.rightStickX, state.rightStickY,
                isPressed(state, ControllerPacket.RS_CLK_FLAG), "R3"
            )

            val dpadCenter = point(278f, 350f)
            drawCircle(idleControl, 74f * scale, dpadCenter)
            drawCircle(outline, 74f * scale, dpadCenter, style = Stroke(width = 3f * scale))
            fun drawDpadPart(x: Float, y: Float, width: Float, height: Float, flag: Int) {
                val pressed = isPressed(state, flag)
                val topLeft = point(x, y)
                val partSize = scaledSize(width, height)
                val corner = CornerRadius(8f * scale, 8f * scale)
                drawRoundRect(activeColor(pressed), topLeft, partSize, corner)
                drawRoundRect(
                    activeStroke(pressed),
                    topLeft,
                    partSize,
                    corner,
                    style = Stroke(width = 2f * scale)
                )
            }
            drawDpadPart(265f, 277f, 26f, 58f, ControllerPacket.UP_FLAG)
            drawDpadPart(265f, 365f, 26f, 58f, ControllerPacket.DOWN_FLAG)
            drawDpadPart(205f, 337f, 58f, 26f, ControllerPacket.LEFT_FLAG)
            drawDpadPart(291f, 337f, 58f, 26f, ControllerPacket.RIGHT_FLAG)

            fun drawFaceButton(x: Float, y: Float, label: String, flag: Int) {
                val pressed = isPressed(state, flag)
                drawCircle(activeColor(pressed), 34f * scale, point(x, y))
                drawCircle(
                    activeStroke(pressed),
                    34f * scale,
                    point(x, y),
                    style = Stroke(width = 3f * scale)
                )
                drawControllerLabel(
                    label,
                    point(x, y),
                    if (pressed) Color.White else textColor,
                    scale
                )
            }
            drawFaceButton(748f, 220f, "Y", ControllerPacket.Y_FLAG)
            drawFaceButton(802f, 274f, "B", ControllerPacket.B_FLAG)
            drawFaceButton(748f, 328f, "A", ControllerPacket.A_FLAG)
            drawFaceButton(694f, 274f, "X", ControllerPacket.X_FLAG)

            fun drawMetaButton(
                x: Float,
                y: Float,
                width: Float,
                label: String,
                flag: Int,
                labelSize: Float = 20f
            ) {
                val pressed = isPressed(state, flag)
                val topLeft = point(x, y)
                val metaSize = scaledSize(width, 36f)
                val corner = CornerRadius(18f * scale, 18f * scale)
                drawRoundRect(activeColor(pressed), topLeft, metaSize, corner)
                drawRoundRect(
                    activeStroke(pressed),
                    topLeft,
                    metaSize,
                    corner,
                    style = Stroke(width = 2f * scale)
                )
                drawControllerLabel(
                    label,
                    point(x + width / 2f, y + 18f),
                    textColor,
                    scale,
                    labelSize
                )
            }
            drawMetaButton(410f, 235f, 82f, "Select", ControllerPacket.BACK_FLAG, 17f)
            drawMetaButton(508f, 235f, 82f, "Start", ControllerPacket.PLAY_FLAG, 17f)
            drawMetaButton(405f, 292f, 80f, "Share", ControllerPacket.MISC_FLAG, 16f)
            drawMetaButton(460f, 342f, 80f, "Home", ControllerPacket.SPECIAL_BUTTON_FLAG, 16f)
            drawMetaButton(515f, 292f, 80f, "Touch", ControllerPacket.TOUCHPAD_FLAG, 16f)

            listOf(
                Triple(175f to 435f, "P1", ControllerPacket.PADDLE1_FLAG),
                Triple(230f to 485f, "P2", ControllerPacket.PADDLE2_FLAG),
                Triple(715f to 485f, "P3", ControllerPacket.PADDLE3_FLAG),
                Triple(770f to 435f, "P4", ControllerPacket.PADDLE4_FLAG)
            ).forEach { (position, label, flag) ->
                drawMetaButton(position.first, position.second, 54f, label, flag)
            }

            drawLine(
                outline.copy(alpha = 0.55f),
                point(155f, 390f),
                point(245f, 475f),
                strokeWidth = 3f * scale
            )
            drawLine(
                outline.copy(alpha = 0.55f),
                point(845f, 390f),
                point(755f, 475f),
                strokeWidth = 3f * scale
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawControllerLabel(
    label: String,
    center: Offset,
    color: Color,
    scale: Float,
    designTextSize: Float = 26f
) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        textSize = designTextSize * scale
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val baseline = center.y - (paint.ascent() + paint.descent()) / 2f
    drawContext.canvas.nativeCanvas.drawText(label, center.x, baseline, paint)
}

@Composable
private fun ControllerKeyIndicator(label: String, pressed: Boolean) {
    val accent = colorResource(R.color.game_menu_accent)
    Surface(
        color = accent.copy(alpha = if (pressed) 0.16f else 0.03f),
        shape = CircleShape,
        border = BorderStroke(
            GameMenuDimens.surfaceStroke,
            accent.copy(alpha = if (pressed) 0.55f else 0.12f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (pressed) accent else colorResource(R.color.game_menu_button_border),
                        CircleShape
                    )
            )
            Text(
                text = label,
                color = if (pressed) accent else colorResource(R.color.game_menu_text_secondary),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun simulatorResultLabel(result: ShortcutSimulatorResult): String {
    return stringResource(
        when (result) {
            ShortcutSimulatorResult.IDLE -> R.string.controller_diag_simulator_idle
            ShortcutSimulatorResult.HINT -> R.string.usb_shortcut_hold_hint
            ShortcutSimulatorResult.MOUSE_EMULATION -> R.string.controller_diag_simulator_mouse_result
            ShortcutSimulatorResult.GAME_MENU -> R.string.controller_diag_simulator_menu_result
            ShortcutSimulatorResult.PERFORMANCE_OVERLAY ->
                R.string.controller_diag_simulator_performance_result
            ShortcutSimulatorResult.EXIT_STREAM -> R.string.controller_diag_simulator_exit_result
        }
    )
}

@Composable
private fun ControllerDiagnosticTopBar(
    primary: Color,
    accent: Color,
    showTestControls: Boolean,
    testPhase: ShortcutTestPhase,
    testToggleEnabled: Boolean,
    selectedDurationSeconds: Int,
    remainingSeconds: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onShowInfo: () -> Unit,
    onToggleTest: () -> Unit,
    onSelectDuration: (Int) -> Unit
) {
    Surface(
        color = colorResource(R.color.game_menu_card_background),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .gamepadFocusOutline(RectangleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back_24),
                    contentDescription = stringResource(R.string.controller_diag_back),
                    tint = primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = stringResource(R.string.controller_diag_title),
                color = primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (showTestControls) {
                ControllerTestControls(
                    testPhase = testPhase,
                    testToggleEnabled = testToggleEnabled,
                    selectedDurationSeconds = selectedDurationSeconds,
                    remainingSeconds = remainingSeconds,
                    onToggleTest = onToggleTest,
                    onSelectDuration = onSelectDuration,
                    modifier = Modifier
                        .widthIn(min = 210.dp, max = 250.dp),
                    showLabel = false,
                    toggleWidth = 78.dp
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .gamepadFocusOutline(RectangleShape)
                    .clickable(onClick = onShowInfo),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = stringResource(R.string.controller_diag_info),
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .gamepadFocusOutline(RectangleShape)
                    .clickable(onClick = onRefresh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.phc_action_reset),
                    contentDescription = stringResource(R.string.controller_diag_refresh),
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ShortcutCardTestButton(
    selected: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit
) {
    val accent = colorResource(R.color.game_menu_accent)
    Surface(
        color = if (selected) Color(0xFFE34F63) else accent.copy(alpha = 0.12f),
        shape = RectangleShape,
        border = BorderStroke(GameMenuDimens.surfaceStroke, accent.copy(alpha = 0.38f)),
        modifier = Modifier
            .heightIn(min = 34.dp)
            .gamepadFocusOutline(RectangleShape)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(
                    if (selected) R.drawable.ic_stop_test else R.drawable.ic_play
                ),
                contentDescription = null,
                tint = if (selected) Color.White else accent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(
                    if (selected) R.string.controller_diag_stop_test
                    else R.string.controller_diag_test_label
                ),
                color = if (selected) Color.White else accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ControllerInfoOverlay(
    snapshot: ControllerDiagnostics.Snapshot,
    simulatorState: ShortcutSimulatorUiState,
    testPhase: ShortcutTestPhase,
    scrollSequence: Int,
    scrollDirection: Int,
    safeRight: androidx.compose.ui.unit.Dp,
    safeBottom: androidx.compose.ui.unit.Dp,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrollStep = with(LocalDensity.current) { 88.dp.toPx() }

    BackHandler(onBack = onDismiss)
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(scrollSequence) {
        if (scrollSequence > 0 && scrollDirection != 0) {
            listState.scrollBy(scrollStep * scrollDirection)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss
            )
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                when (event.key) {
                    Key.DirectionUp,
                    Key.PageUp -> scope.launch { listState.scrollBy(-scrollStep) }
                    Key.DirectionDown,
                    Key.PageDown -> scope.launch { listState.scrollBy(scrollStep) }
                }
                true
            }
            .focusable()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    top = 58.dp,
                    end = safeRight + 12.dp,
                    bottom = safeBottom + 12.dp
                )
        ) {
            val landscape = maxWidth > maxHeight
            val cardWidth = minOf(maxWidth * if (landscape) 0.46f else 0.9f, 480.dp)
            val cardHeight = minOf(maxHeight, 560.dp)
            Surface(
                color = colorResource(R.color.game_menu_card_background),
                shape = GameMenuCardShape,
                border = BorderStroke(
                    GameMenuDimens.surfaceStroke,
                    colorResource(R.color.game_menu_dialog_border)
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(cardWidth)
                    .height(cardHeight)
                    .shadow(12.dp, GameMenuCardShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_info),
                            contentDescription = null,
                            tint = colorResource(R.color.game_menu_accent),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.controller_diag_info),
                            color = colorResource(R.color.game_menu_text_primary),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .gamepadFocusOutline(CircleShape)
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close_stylish),
                                contentDescription = stringResource(R.string.controller_diag_close),
                                tint = colorResource(R.color.game_menu_text_primary),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            item {
                                ControllerTestStatus(
                                    state = simulatorState,
                                    testPhase = testPhase
                                )
                            }
                            if (snapshot.devices.isEmpty()) {
                                item { EmptyControllerCard() }
                            } else {
                                items(snapshot.devices) { device -> ControllerCard(device) }
                            }
                        }
                        ControllerInfoScrollbar(
                            listState = listState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ControllerInfoScrollbar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    if ((!listState.canScrollBackward && !listState.canScrollForward) || visibleItems.isEmpty()) return

    val averageItemSize = visibleItems.map { it.size }.average().toFloat().coerceAtLeast(1f)
    val viewportSize = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
        .toFloat()
        .coerceAtLeast(1f)
    val estimatedContentSize = averageItemSize * layoutInfo.totalItemsCount
    val estimatedScrollRange = (estimatedContentSize - viewportSize).coerceAtLeast(1f)
    val firstVisibleItem = visibleItems.first()
    val estimatedScrollOffset = listState.firstVisibleItemIndex * averageItemSize -
        firstVisibleItem.offset + layoutInfo.viewportStartOffset
    val progress = (estimatedScrollOffset / estimatedScrollRange).coerceIn(0f, 1f)
    val visibleFraction = (viewportSize / estimatedContentSize)
        .coerceIn(0.18f, 0.72f)
    val trackColor = colorResource(R.color.game_menu_button_border)
    val thumbColor = colorResource(R.color.game_menu_accent)

    Canvas(
        modifier = modifier
            .width(4.dp)
            .fillMaxHeight()
    ) {
        drawRoundRect(
            color = trackColor.copy(alpha = 0.35f),
            cornerRadius = CornerRadius(size.width / 2f, size.width / 2f)
        )
        val thumbHeight = size.height * visibleFraction
        val thumbTop = (size.height - thumbHeight) * progress
        drawRoundRect(
            color = thumbColor.copy(alpha = 0.82f),
            topLeft = Offset(0f, thumbTop),
            size = Size(size.width, thumbHeight),
            cornerRadius = CornerRadius(size.width / 2f, size.width / 2f)
        )
    }
}

@Composable
private fun DiagnosticCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(GameMenuDimens.section),
    content: @Composable () -> Unit
) {
    Surface(
        color = colorResource(R.color.game_menu_card_background),
        shape = GameMenuCardShape,
        border = BorderStroke(
            GameMenuDimens.surfaceStroke,
            colorResource(R.color.game_menu_button_border)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
private fun EmptyControllerCard() {
    DiagnosticCard(
        modifier = Modifier
            .gamepadFocusOutline(GameMenuCardShape)
            .focusable(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GameMenuDimens.section)
        ) {
            Icon(
                painter = painterResource(R.drawable.phc_gamepad),
                contentDescription = null,
                tint = colorResource(R.color.game_menu_text_secondary),
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = stringResource(R.string.controller_diag_empty_title),
                color = colorResource(R.color.game_menu_text_primary),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.controller_diag_empty_summary),
                color = colorResource(R.color.game_menu_text_secondary),
                fontSize = 12.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControllerCard(device: ControllerDiagnostics.Device) {
    val accent = colorResource(R.color.game_menu_accent)
    DiagnosticCard(
        modifier = Modifier
            .gamepadFocusOutline(GameMenuCardShape)
            .focusable()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(GameMenuDimens.compact)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.phc_gamepad),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = device.name,
                    color = colorResource(R.color.game_menu_text_primary),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DiagnosticTag(
                    text = connectionLabel(device.connectionType),
                    color = accent
                )
                DiagnosticTag(
                    text = inputPathLabel(device.inputPath),
                    color = inputPathColor(device.inputPath)
                )
            }

            KeyValueRow(
                key = stringResource(R.string.controller_diag_connection),
                value = connectionLabel(device.connectionType)
            )
            KeyValueRow(
                key = stringResource(R.string.controller_diag_stream_path),
                value = inputPathLabel(device.inputPath)
            )
            KeyValueRow(
                key = stringResource(R.string.controller_diag_vid_pid),
                value = formatVidPid(device.vendorId, device.productId)
            )

            Text(
                text = noteLabel(device.note),
                color = colorResource(R.color.game_menu_text_secondary),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun DiagnosticTag(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), GameMenuCardShape)
            .border(GameMenuDimens.surfaceStroke, color.copy(alpha = 0.22f), GameMenuCardShape)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

@Composable
private fun KeyValueRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = key,
            color = colorResource(R.color.game_menu_text_secondary),
            fontSize = 13.sp,
            modifier = Modifier.width(104.dp)
        )
        Text(
            text = value,
            color = colorResource(R.color.game_menu_text_primary),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun connectionLabel(connectionType: ControllerDiagnostics.ConnectionType): String {
    return stringResource(
        when (connectionType) {
            ControllerDiagnostics.ConnectionType.USB -> R.string.controller_diag_connection_usb
            ControllerDiagnostics.ConnectionType.BLUETOOTH_OR_WIRELESS -> R.string.controller_diag_connection_wireless
            ControllerDiagnostics.ConnectionType.BUILT_IN -> R.string.controller_diag_connection_builtin
        }
    )
}

@Composable
private fun inputPathLabel(inputPath: ControllerDiagnostics.InputPath): String {
    return stringResource(
        when (inputPath) {
            ControllerDiagnostics.InputPath.SYSTEM -> R.string.controller_diag_path_system
            ControllerDiagnostics.InputPath.USB_TAKEOVER -> R.string.controller_diag_path_usb_takeover
            ControllerDiagnostics.InputPath.UNAVAILABLE -> R.string.controller_diag_path_unavailable
        }
    )
}

@Composable
private fun inputPathColor(inputPath: ControllerDiagnostics.InputPath): Color {
    return when (inputPath) {
        ControllerDiagnostics.InputPath.SYSTEM -> Color(0xFF7CCBFF)
        ControllerDiagnostics.InputPath.USB_TAKEOVER -> Color(0xFF8CE99A)
        ControllerDiagnostics.InputPath.UNAVAILABLE -> Color(0xFFFFB36B)
    }
}

@Composable
private fun noteLabel(note: ControllerDiagnostics.Note): String {
    return stringResource(
        when (note) {
            ControllerDiagnostics.Note.USB_TAKEOVER_READY -> R.string.controller_diag_note_usb_ready
            ControllerDiagnostics.Note.USB_PERMISSION_REQUIRED -> R.string.controller_diag_note_usb_permission
            ControllerDiagnostics.Note.SYSTEM_USB -> R.string.controller_diag_note_system_usb
            ControllerDiagnostics.Note.SYSTEM_WIRELESS -> R.string.controller_diag_note_system_wireless
            ControllerDiagnostics.Note.SYSTEM_BUILT_IN -> R.string.controller_diag_note_system_builtin
            ControllerDiagnostics.Note.USB_DRIVER_DISABLED_OR_UNSUPPORTED -> R.string.controller_diag_note_unavailable
        }
    )
}

private fun formatVidPid(vendorId: Int, productId: Int): String {
    if (vendorId == 0 && productId == 0) return "—"
    return String.format(Locale.US, "%04X:%04X", vendorId, productId)
}

private fun isPressed(state: ShortcutSimulatorUiState, flag: Int): Boolean {
    return state.pressedFlags and flag != 0
}

private fun formatCountdown(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", safeSeconds / 60, safeSeconds % 60)
}

private const val ANALOG_ACTIVE_THRESHOLD = 0.08f
