@file:Suppress("DEPRECATION")
package com.limelight

import android.graphics.Point
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.annotation.RequiresApi
import com.limelight.binding.input.touch.AbsoluteTouchContext
import com.limelight.binding.input.touch.NativeTouchContext
import com.limelight.binding.input.touch.RelativeTouchContext
import com.limelight.binding.input.touch.TouchContext
import com.limelight.binding.input.touchpad.NonRootTouchpadHandler
import com.limelight.binding.input.virtual_controller.VirtualController
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.ui.StreamView
import kotlin.math.*

internal inline fun containsStylusTool(pointerCount: Int, toolTypeAt: (Int) -> Int): Boolean {
    for (i in 0 until pointerCount) {
        when (toolTypeAt(i)) {
            MotionEvent.TOOL_TYPE_STYLUS,
            MotionEvent.TOOL_TYPE_ERASER -> return true
        }
    }
    return false
}

internal const val CURRENT_MOTION_SAMPLE = -1

internal inline fun visitMotionEventSamples(
    historySize: Int,
    pointerCount: Int,
    visitor: (pointerIndex: Int, historyPosition: Int) -> Boolean
): Boolean {
    for (historyPosition in 0 until historySize) {
        for (pointerIndex in 0 until pointerCount) {
            if (!visitor(pointerIndex, historyPosition)) return false
        }
    }
    for (pointerIndex in 0 until pointerCount) {
        if (!visitor(pointerIndex, CURRENT_MOTION_SAMPLE)) return false
    }
    return true
}

/**
 * 处理所有触控/鼠标/触控笔 MotionEvent 逻辑。
 * 从 Game.java 提取，保持行为完全一致。
 */
class TouchInputHandler(private val game: Game) {

    // ---- 触控上下文 (Game 初始化后赋值) ----
    var touchContextMap = arrayOfNulls<TouchContext>(TOUCH_CONTEXT_LENGTH)
    val absoluteTouchContextMap = arrayOfNulls<TouchContext>(TOUCH_CONTEXT_LENGTH)
    val relativeTouchContextMap = arrayOfNulls<TouchContext>(TOUCH_CONTEXT_LENGTH)

    // ---- 触控私有状态 ----
    // Mouse-only baseline. Pen packets carry their full button state independently.
    private var lastButtonState = 0
    private var multiFingerDownTime = 0L

    // 双指右键检测
    private var twoFingerDownTime = 0L
    private var firstFingerUpTime = 0L
    private var twoFingerTapPending = false
    private var twoFingerMoved = false
    private var twoFingerStartX = 0f
    private var twoFingerStartY = 0f

    private var lastAbsTouchUpTime = 0L
    private var lastAbsTouchDownTime = 0L
    private var lastAbsTouchUpX = 0f
    private var lastAbsTouchUpY = 0f
    private var lastAbsTouchDownX = 0f
    private var lastAbsTouchDownY = 0f

    val nativeTouchPointerMap = HashMap<Int, NativeTouchContext.Pointer>()

    // 华为鼠标滚轮/中键模拟
    private var fakeScrollInitialY = -1f
    private var scrollTotal = 0f
    var lastMouseHoverTime = 0L          // 键盘处理也会读
    private var waitRelease = false
    private var detectScrolling = false
    var detectMouseMiddle = false         // 键盘处理也会读写
    var detectMouseMiddleDown = false     // 键盘处理也会读写
    private val nonRootTouchpadHandler = NonRootTouchpadHandler()
    private val penPointerCoords = MotionEvent.PointerCoords()

    // ---- 公共入口 ----

    @RequiresApi(Build.VERSION_CODES.O)
    fun handleMotionEvent(view: View?, event: MotionEvent): Boolean {
        if (!game.grabbedInput) return false

        val eventSource = event.source
        val hasStylusTool = eventHasStylusTool(event)

        if (view != null && hasStylusTool && trySendPenEvent(view, event)) {
            return true
        }

        if (!BuildConfig.ROOT_BUILD && game.prefConfig.optimizeHardwareTouchpad &&
            !hasStylusTool &&
            NonRootTouchpadHandler.isHardwareTouchpadEvent(event)) {
            if (game.inputCaptureProvider.isCapturingActive()) {
                nonRootTouchpadHandler.handleMotionEvent(event, game.conn)
            } else {
                nonRootTouchpadHandler.cancelAll(game.conn)
            }
            return true
        }

        // 华为平板原生鼠标下的滚动逻辑
        if (game.prefConfig.fixMouseWheel && game.cursorVisible &&
            eventSource == InputDevice.SOURCE_MOUSE &&
            (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE ||
                event.actionMasked == MotionEvent.ACTION_MOVE ||
                event.actionMasked == MotionEvent.ACTION_DOWN ||
                event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS)
        ) {
            lastMouseHoverTime = android.os.SystemClock.uptimeMillis()
            detectScrolling = true
        } else if (detectScrolling) {
            if (eventSource == InputDevice.SOURCE_TOUCHSCREEN && event.pointerCount == 1) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_CANCEL -> waitRelease = true
                    MotionEvent.ACTION_DOWN -> {
                        val timeDiff = android.os.SystemClock.uptimeMillis() - lastMouseHoverTime
                        if (timeDiff <= 40 || waitRelease) {
                            fakeScrollInitialY = event.y
                            game.conn?.sendMousePosition(
                                event.x.toInt().toShort(),
                                event.y.toInt().toShort(),
                                game.streamView.width.toShort(),
                                game.streamView.height.toShort()
                            )
                            return true
                        } else {
                            detectScrolling = false
                            waitRelease = false
                            scrollTotal = 0f
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = event.y - fakeScrollInitialY
                        fakeScrollInitialY = event.y
                        scrollTotal += deltaY
                        if (scrollTotal > 127.99f) {
                            scrollTotal -= 128f
                            game.conn?.sendMouseHighResScroll(120)
                        } else if (scrollTotal < -127.99f) {
                            scrollTotal += 128f
                            game.conn?.sendMouseHighResScroll(-120)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        while (scrollTotal > 127.99f || scrollTotal < -127.99f) {
                            if (scrollTotal > 127.99f) {
                                scrollTotal -= 128f
                                game.conn?.sendMouseHighResScroll(120)
                            } else {
                                scrollTotal += 128f
                                game.conn?.sendMouseHighResScroll(-120)
                            }
                        }
                        if (!waitRelease) detectScrolling = false
                        fakeScrollInitialY = -1f
                        scrollTotal = 0f
                        return true
                    }
                    else -> {
                        detectScrolling = false
                        waitRelease = false
                        scrollTotal = 0f
                    }
                }
            } else if (waitRelease && eventSource == InputDevice.SOURCE_MOUSE &&
                event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE
            ) {
                waitRelease = false
            } else if (!waitRelease) {
                detectScrolling = false
                scrollTotal = 0f
            }
        }

        // 华为鼠标中键
        if (game.prefConfig.fixMouseMiddle) {
            if (game.cursorVisible) {
                if (eventSource == InputDevice.SOURCE_MOUSE &&
                    event.actionMasked == MotionEvent.ACTION_HOVER_MOVE
                ) {
                    lastMouseHoverTime = android.os.SystemClock.uptimeMillis()
                    detectMouseMiddle = true
                }
            } else if (eventSource == InputDevice.SOURCE_MOUSE_RELATIVE &&
                event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE
            ) {
                lastMouseHoverTime = android.os.SystemClock.uptimeMillis()
                detectMouseMiddle = true
            }
        }

        val deviceSources = event.device?.sources ?: 0

        // 本地鼠标指针模式
        if (game.prefConfig.enableNativeMousePointer && (eventSource and InputDevice.SOURCE_CLASS_POINTER) != 0) {
            val isActualMouse = eventSource == InputDevice.SOURCE_MOUSE ||
                eventSource == InputDevice.SOURCE_MOUSE_RELATIVE ||
                (event.pointerCount >= 1 && event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) ||
                eventSource == 12290

            if (isActualMouse) {
                LimeLog.info(
                    "Native mouse event (processing): ${event.actionMasked}" +
                        ", source: $eventSource, x: ${event.x}, y: ${event.y}" +
                        ", buttons: ${event.buttonState}"
                )
                updateMousePosition(view, event)

                val buttonState = event.buttonState
                val changedButtons = buttonState xor lastButtonState

                if (changedButtons and MotionEvent.BUTTON_PRIMARY != 0) {
                    if (buttonState and MotionEvent.BUTTON_PRIMARY != 0) {
                        game.conn?.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
                    } else {
                        game.conn?.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
                    }
                }
                if (changedButtons and MotionEvent.BUTTON_SECONDARY != 0) {
                    if (buttonState and MotionEvent.BUTTON_SECONDARY != 0) {
                        game.conn?.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
                    } else {
                        game.conn?.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
                    }
                }
                if (changedButtons and MotionEvent.BUTTON_TERTIARY != 0) {
                    if (buttonState and MotionEvent.BUTTON_TERTIARY != 0) {
                        game.conn?.sendMouseButtonDown(MouseButtonPacket.BUTTON_MIDDLE)
                    } else {
                        game.conn?.sendMouseButtonUp(MouseButtonPacket.BUTTON_MIDDLE)
                    }
                }

                if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
                    game.conn?.sendMouseHighResScroll((event.getAxisValue(MotionEvent.AXIS_VSCROLL) * 120).toInt().toShort())
                    game.conn?.sendMouseHighResHScroll((event.getAxisValue(MotionEvent.AXIS_HSCROLL) * 120).toInt().toShort())
                }

                lastButtonState = buttonState
                return true
            }
        }

        if (eventSource and InputDevice.SOURCE_CLASS_JOYSTICK != 0) {
            return game.controllerHandler.handleMotionEvent(event)
        } else if (deviceSources and InputDevice.SOURCE_CLASS_JOYSTICK != 0 && game.controllerHandler.tryHandleTouchpadEvent(event)
        ) {
            return true
        } else if ((eventSource and InputDevice.SOURCE_CLASS_POINTER != 0) ||
            (eventSource and InputDevice.SOURCE_CLASS_POSITION != 0) ||
            eventSource == InputDevice.SOURCE_MOUSE_RELATIVE
        ) {
            if (eventSource == InputDevice.SOURCE_MOUSE ||
                (eventSource and InputDevice.SOURCE_CLASS_POSITION) != 0 ||
                eventSource == InputDevice.SOURCE_MOUSE_RELATIVE ||
                (event.pointerCount >= 1 &&
                    (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE ||
                        event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                        event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER)) ||
                eventSource == 12290
            ) {
                var buttonState = event.buttonState
                var changedButtons = buttonState xor lastButtonState

                if (eventSource == 12290) {
                    buttonState = when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> buttonState or MotionEvent.BUTTON_PRIMARY
                        MotionEvent.ACTION_UP -> buttonState and MotionEvent.BUTTON_PRIMARY.inv()
                        else -> buttonState or (lastButtonState and MotionEvent.BUTTON_PRIMARY)
                    }
                    changedButtons = buttonState xor lastButtonState
                }

                if (!game.inputCaptureProvider.isCapturingActive()) {
                    return true
                }

                val eventHasRelativeMouseAxes = game.inputCaptureProvider.eventHasRelativeMouseAxes(event)
                if (eventHasRelativeMouseAxes) {
                    val deltaX = game.inputCaptureProvider.getRelativeAxisX(event).toInt().toShort()
                    val deltaY = game.inputCaptureProvider.getRelativeAxisY(event).toInt().toShort()
                    if (deltaX.toInt() != 0 || deltaY.toInt() != 0) {
                        if (game.prefConfig.absoluteMouseMode) {
                            val activeStreamView = game.activeStreamView!!
                            game.conn?.sendMouseMoveAsMousePosition(
                                deltaX, deltaY,
                                activeStreamView.width.toShort(), activeStreamView.height.toShort()
                            )
                        } else {
                            game.conn?.sendMouseMove(deltaX, deltaY)
                        }
                    }
                } else if ((eventSource and InputDevice.SOURCE_CLASS_POSITION) != 0) {
                    val device = event.device
                    if (device != null) {
                        val xRange = device.getMotionRange(MotionEvent.AXIS_X, eventSource)
                        val yRange = device.getMotionRange(MotionEvent.AXIS_Y, eventSource)
                        if (xRange != null && yRange != null && xRange.min == 0f && yRange.min == 0f) {
                            val xMax = xRange.max.toInt()
                            val yMax = yRange.max.toInt()
                            if (xMax <= Short.MAX_VALUE && yMax <= Short.MAX_VALUE) {
                                game.conn?.sendMousePosition(
                                    event.x.toInt().toShort(), event.y.toInt().toShort(),
                                    xMax.toShort(), yMax.toShort()
                                )
                            }
                        }
                    }
                } else if (view != null) {
                    updateMousePosition(view, event)
                }

                if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
                    game.conn?.sendMouseHighResScroll((event.getAxisValue(MotionEvent.AXIS_VSCROLL) * 120).toInt().toShort())
                    game.conn?.sendMouseHighResHScroll((event.getAxisValue(MotionEvent.AXIS_HSCROLL) * 120).toInt().toShort())
                }

                if (changedButtons and MotionEvent.BUTTON_PRIMARY != 0) {
                    if (buttonState and MotionEvent.BUTTON_PRIMARY != 0) {
                        game.conn?.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
                    } else {
                        game.conn?.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
                    }
                }

                if (changedButtons and (MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_STYLUS_PRIMARY) != 0) {
                    if (buttonState and (MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_STYLUS_PRIMARY) != 0) {
                        game.conn?.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
                    } else {
                        game.conn?.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
                    }
                }

                if (changedButtons and (MotionEvent.BUTTON_TERTIARY or MotionEvent.BUTTON_STYLUS_SECONDARY) != 0) {
                    if (buttonState and (MotionEvent.BUTTON_TERTIARY or MotionEvent.BUTTON_STYLUS_SECONDARY) != 0) {
                        game.conn?.sendMouseButtonDown(MouseButtonPacket.BUTTON_MIDDLE)
                    } else {
                        game.conn?.sendMouseButtonUp(MouseButtonPacket.BUTTON_MIDDLE)
                    }
                }

                if (game.prefConfig.mouseNavButtons) {
                    if (changedButtons and MotionEvent.BUTTON_BACK != 0) {
                        if (buttonState and MotionEvent.BUTTON_BACK != 0) {
                            game.conn?.sendMouseButtonDown(MouseButtonPacket.BUTTON_X1)
                        } else {
                            game.conn?.sendMouseButtonUp(MouseButtonPacket.BUTTON_X1)
                        }
                    }
                    if (changedButtons and MotionEvent.BUTTON_FORWARD != 0) {
                        if (buttonState and MotionEvent.BUTTON_FORWARD != 0) {
                            game.conn?.sendMouseButtonDown(MouseButtonPacket.BUTTON_X2)
                        } else {
                            game.conn?.sendMouseButtonUp(MouseButtonPacket.BUTTON_X2)
                        }
                    }
                }

                // 触控笔按下/抬起
                if (event.pointerCount == 1 && event.actionIndex == 0) {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            when (event.getToolType(0)) {
                                MotionEvent.TOOL_TYPE_STYLUS -> {
                                    lastAbsTouchDownTime = event.eventTime
                                    lastAbsTouchDownX = event.getX(0)
                                    lastAbsTouchDownY = event.getY(0)
                                    game.conn?.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
                                }
                                MotionEvent.TOOL_TYPE_ERASER -> {
                                    lastAbsTouchDownTime = event.eventTime
                                    lastAbsTouchDownX = event.getX(0)
                                    lastAbsTouchDownY = event.getY(0)
                                    game.conn?.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
                                }

                                MotionEvent.TOOL_TYPE_FINGER -> {
                                    logIgnoredStylusBranchToolType(event, 0, "down")
                                }

                                MotionEvent.TOOL_TYPE_MOUSE -> {
                                    logIgnoredStylusBranchToolType(event, 0, "down")
                                }

                                MotionEvent.TOOL_TYPE_UNKNOWN -> {
                                    logIgnoredStylusBranchToolType(event, 0, "down")
                                }
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            when (event.getToolType(0)) {
                                MotionEvent.TOOL_TYPE_STYLUS -> {
                                    lastAbsTouchUpTime = event.eventTime
                                    lastAbsTouchUpX = event.getX(0)
                                    lastAbsTouchUpY = event.getY(0)
                                    game.conn?.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
                                }
                                MotionEvent.TOOL_TYPE_ERASER -> {
                                    lastAbsTouchUpTime = event.eventTime
                                    lastAbsTouchUpX = event.getX(0)
                                    lastAbsTouchUpY = event.getY(0)
                                    game.conn?.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
                                }

                                MotionEvent.TOOL_TYPE_FINGER -> {
                                    logIgnoredStylusBranchToolType(event, 0, "up")
                                }

                                MotionEvent.TOOL_TYPE_MOUSE -> {
                                    logIgnoredStylusBranchToolType(event, 0, "up")
                                }

                                MotionEvent.TOOL_TYPE_UNKNOWN -> {
                                    logIgnoredStylusBranchToolType(event, 0, "up")
                                }
                            }
                        }
                    }
                }

                lastButtonState = buttonState
            } else {
                // This case is for fingers
                if (game.getisTouchOverrideEnabled()) {
                    game.panZoomHandler.handleTouchEvent(event)
                    return true
                }

                if (!game.prefConfig.touchscreenTrackpad && game.prefConfig.enableEnhancedTouch && trySendTouchEvent(view, event)) {
                    return true
                }

                if (game.virtualController != null &&
                    (game.virtualController?.controllerMode == VirtualController.ControllerMode.MoveButtons ||
                        game.virtualController?.controllerMode == VirtualController.ControllerMode.ResizeButtons)
                ) {
                    return true
                }

                val actionIndex = event.actionIndex

                // 三指手势特殊处理
                if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN && event.pointerCount == 3) {
                    multiFingerDownTime = event.eventTime
                    for (ctx in touchContextMap) ctx?.cancelTouch()
                    return true
                }

                val context = getTouchContext(actionIndex) ?: return false

                when (event.actionMasked) {
                    MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                        if (event.actionMasked == MotionEvent.ACTION_DOWN) multiFingerDownTime = 0
                        val coords = getNormalizedCoordinates(game.streamView, event.getX(actionIndex), event.getY(actionIndex))
                        for (tc in touchContextMap) tc?.setPointerCount(event.pointerCount)

                        if (event.pointerCount == 2 && game.prefConfig.touchscreenTrackpad) {
                            twoFingerDownTime = event.eventTime
                            twoFingerStartX = event.getX(0)
                            twoFingerStartY = event.getY(0)
                            twoFingerMoved = false
                            twoFingerTapPending = false
                        }
                        context.touchDownEvent(coords[0].toInt(), coords[1].toInt(), event.eventTime, true)
                    }
                    MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                        val coords = getNormalizedCoordinates(game.streamView, event.getX(actionIndex), event.getY(actionIndex))

                        if (multiFingerDownTime == 0L && event.pointerCount == 2 && !twoFingerMoved && game.prefConfig.touchscreenTrackpad) {
                            if (event.eventTime - twoFingerDownTime < TWO_FINGER_TAP_THRESHOLD) {
                                if (!game.isMouseMoveOnlyEnabled) {
                                    game.conn?.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
                                    game.conn?.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
                                }
                                twoFingerTapPending = false
                                twoFingerMoved = true
                                context.cancelTouch()
                                for (tc in touchContextMap) tc?.setPointerCount(event.pointerCount - 1)
                                return true
                            } else {
                                firstFingerUpTime = event.eventTime
                                twoFingerTapPending = true
                            }
                        }

                        if (event.pointerCount == 1 &&
                            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                (event.flags and MotionEvent.FLAG_CANCELED) == 0)
                        ) {
                            if (twoFingerTapPending && !twoFingerMoved && game.prefConfig.touchscreenTrackpad) {
                                if (event.eventTime - firstFingerUpTime < TWO_FINGER_TAP_THRESHOLD) {
                                    if (!game.isMouseMoveOnlyEnabled) {
                                        game.conn?.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
                                        game.conn?.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
                                    }
                                    twoFingerTapPending = false
                                    for (tc in touchContextMap) {
                                        tc?.cancelTouch()
                                        tc?.setPointerCount(0)
                                    }
                                    return true
                                }
                            }
                            twoFingerTapPending = false

                            if (event.eventTime - multiFingerDownTime < MULTI_FINGER_TAP_THRESHOLD) {
                                game.toggleKeyboard()
                                return true
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            (event.flags and MotionEvent.FLAG_CANCELED) != 0
                        ) {
                            context.cancelTouch()
                        } else {
                            context.touchUpEvent(coords[0].toInt(), coords[1].toInt(), event.eventTime)
                        }

                        for (tc in touchContextMap) tc?.setPointerCount(event.pointerCount - 1)
                        if (actionIndex == 0 && event.pointerCount > 1 && !context.isCancelled()) {
                            val secondCoords = getNormalizedCoordinates(game.streamView, event.getX(1), event.getY(1))
                            context.touchDownEvent(secondCoords[0].toInt(), secondCoords[1].toInt(), event.eventTime, false)
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (event.pointerCount == 2 && !twoFingerMoved && game.prefConfig.touchscreenTrackpad) {
                            val dx = event.getX(0) - twoFingerStartX
                            val dy = event.getY(0) - twoFingerStartY
                            if (sqrt(dx * dx + dy * dy) > TWO_FINGER_MOVE_THRESHOLD) {
                                twoFingerMoved = true
                            }
                        }
                        for (i in 0 until event.historySize) {
                            for (tc in touchContextMap) {
                                if (tc != null && tc.getActionIndex() < event.pointerCount) {
                                    val hc = getNormalizedCoordinates(game.streamView, event.getHistoricalX(tc.getActionIndex(), i), event.getHistoricalY(tc.getActionIndex(), i))
                                    tc.touchMoveEvent(hc[0].toInt(), hc[1].toInt(), event.getHistoricalEventTime(i))
                                }
                            }
                        }
                        for (tc in touchContextMap) {
                            if (tc != null && tc.getActionIndex() < event.pointerCount) {
                                val cc = getNormalizedCoordinates(game.streamView, event.getX(tc.getActionIndex()), event.getY(tc.getActionIndex()))
                                tc.touchMoveEvent(cc[0].toInt(), cc[1].toInt(), event.eventTime)
                            }
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        for (tc in touchContextMap) {
                            tc?.cancelTouch()
                            tc?.setPointerCount(0)
                        }
                    }
                    else -> return false
                }
            }
            return true
        }
        return false
    }

    private fun handleTouchInput(
        event: MotionEvent,
        inputContextMap: Array<TouchContext?>,
        isTouchScreen: Boolean,
        invertAxis: Boolean = false,
        eventAction: Int = event.actionMasked,
        actionIndex: Int = event.actionIndex,
        pointerCount: Int = event.pointerCount
    ): Boolean {
        val context = if (actionIndex < inputContextMap.size) inputContextMap[actionIndex] else null
        if (context == null) {
            return false
        }

        val actualPointerCount = event.pointerCount
        val shouldDuplicateMovement = actualPointerCount < pointerCount

        if (eventAction == MotionEvent.ACTION_MOVE) {
            for (i in 0 until event.historySize) {
                for (touchContext in inputContextMap) {
                    if (touchContext != null && touchContext.getActionIndex() < pointerCount) {
                        val actualActionIndex = if (shouldDuplicateMovement) 0 else touchContext.getActionIndex()
                        if (actualActionIndex >= actualPointerCount) continue

                        var historicalX = event.getHistoricalX(actualActionIndex, i).toInt()
                        var historicalY = event.getHistoricalY(actualActionIndex, i).toInt()
                        if (isTouchScreen) {
                            val normalizedCoords = getNormalizedCoordinates(game.streamView, historicalX.toFloat(), historicalY.toFloat())
                            historicalX = normalizedCoords[0].toInt()
                            historicalY = normalizedCoords[1].toInt()
                        }

                        if (invertAxis) {
                            touchContext.touchMoveEvent(historicalY, historicalX, event.getHistoricalEventTime(i))
                        } else {
                            touchContext.touchMoveEvent(historicalX, historicalY, event.getHistoricalEventTime(i))
                        }
                    }
                }
            }

            for (touchContext in inputContextMap) {
                if (touchContext != null && touchContext.getActionIndex() < pointerCount) {
                    val actualActionIndex = if (shouldDuplicateMovement) 0 else touchContext.getActionIndex()
                    if (actualActionIndex >= actualPointerCount) continue

                    var currentX = event.getX(actualActionIndex).toInt()
                    var currentY = event.getY(actualActionIndex).toInt()
                    if (isTouchScreen) {
                        val normalizedCoords = getNormalizedCoordinates(game.streamView, currentX.toFloat(), currentY.toFloat())
                        currentX = normalizedCoords[0].toInt()
                        currentY = normalizedCoords[1].toInt()
                    }

                    if (invertAxis) {
                        touchContext.touchMoveEvent(currentY, currentX, event.eventTime)
                    } else {
                        touchContext.touchMoveEvent(currentX, currentY, event.eventTime)
                    }
                }
            }

            return true
        }

        val actualActionIndex = event.actionIndex.coerceAtMost((actualPointerCount - 1).coerceAtLeast(0))
        var eventX = event.getX(actualActionIndex).toInt()
        var eventY = event.getY(actualActionIndex).toInt()

        if (isTouchScreen) {
            val normalizedCoords = getNormalizedCoordinates(game.streamView, eventX.toFloat(), eventY.toFloat())
            eventX = normalizedCoords[0].toInt()
            eventY = normalizedCoords[1].toInt()
        }

        when (eventAction) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                for (touchContext in inputContextMap) touchContext?.setPointerCount(pointerCount)
                context.touchDownEvent(eventX, eventY, event.eventTime, true)
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    (event.flags and MotionEvent.FLAG_CANCELED) != 0
                ) {
                    context.cancelTouch()
                } else {
                    context.touchUpEvent(eventX, eventY, event.eventTime)
                }

                for (touchContext in inputContextMap) touchContext?.setPointerCount(pointerCount - 1)
                if (actionIndex == 0 && pointerCount > 1 && !context.isCancelled()) {
                    val secondaryIndex = if (actualPointerCount > 1) 1 else 0
                    var secondX = event.getX(secondaryIndex).toInt()
                    var secondY = event.getY(secondaryIndex).toInt()
                    if (isTouchScreen) {
                        val normalizedCoords = getNormalizedCoordinates(game.streamView, secondX.toFloat(), secondY.toFloat())
                        secondX = normalizedCoords[0].toInt()
                        secondY = normalizedCoords[1].toInt()
                    }
                    context.touchDownEvent(secondX, secondY, event.eventTime, false)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                for (touchContext in inputContextMap) {
                    touchContext?.cancelTouch()
                    touchContext?.setPointerCount(0)
                }
            }
            else -> return false
        }

        return true
    }

    // ---- updateMousePosition ----

    private fun updateMousePosition(touchedView: View?, event: MotionEvent) {
        val activeStreamView = game.activeStreamView ?: return

        var eventX: Float
        var eventY: Float

        if (touchedView == activeStreamView) {
            eventX = event.getX(0)
            eventY = event.getY(0)
        } else if (game.externalDisplayManager != null && game.externalDisplayManager?.isUsingExternalDisplay() == true) {
            eventX = event.getX(0)
            eventY = event.getY(0)
        } else {
            eventX = event.getX(0) - activeStreamView.x
            eventY = event.getY(0) - activeStreamView.y
        }

        if (event.pointerCount == 1 && event.actionIndex == 0 &&
            (event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER ||
                event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS)
        ) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_HOVER_ENTER,
                MotionEvent.ACTION_HOVER_EXIT, MotionEvent.ACTION_HOVER_MOVE -> {
                    if (event.eventTime - lastAbsTouchUpTime <= STYLUS_UP_DEAD_ZONE_DELAY &&
                        sqrt((eventX - lastAbsTouchUpX).pow(2) + (eventY - lastAbsTouchUpY).pow(2)) <= STYLUS_UP_DEAD_ZONE_RADIUS
                    ) return
                }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    if (event.eventTime - lastAbsTouchDownTime <= STYLUS_DOWN_DEAD_ZONE_DELAY &&
                        sqrt((eventX - lastAbsTouchDownX).pow(2) + (eventY - lastAbsTouchDownY).pow(2)) <= STYLUS_DOWN_DEAD_ZONE_RADIUS
                    ) return
                }
            }
        }

        if (game.externalDisplayManager != null && game.externalDisplayManager?.isUsingExternalDisplay() == true) {
            val streamViewWidth = activeStreamView.width
            val streamViewHeight = activeStreamView.height
            val size = Point()
            game.windowManager.defaultDisplay.getRealSize(size)
            val scaleX = streamViewWidth.toFloat() / size.x
            val scaleY = streamViewHeight.toFloat() / size.y
            eventX = (eventX * scaleX).coerceIn(0f, streamViewWidth.toFloat())
            eventY = (eventY * scaleY).coerceIn(0f, streamViewHeight.toFloat())
        } else {
            eventX = eventX.coerceIn(0f, activeStreamView.width.toFloat())
            eventY = eventY.coerceIn(0f, activeStreamView.height.toFloat())
        }

        game.conn?.sendMousePosition(eventX.toInt().toShort(), eventY.toInt().toShort(), activeStreamView.width.toShort(), activeStreamView.height.toShort())
    }

    // ---- Touch/Pen 事件发送 ----

    private fun getLiTouchTypeFromEvent(event: MotionEvent): Byte = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> MoonBridge.LI_TOUCH_EVENT_DOWN
        MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
            if (event.flags and MotionEvent.FLAG_CANCELED != 0) MoonBridge.LI_TOUCH_EVENT_CANCEL
            else MoonBridge.LI_TOUCH_EVENT_UP
        MotionEvent.ACTION_MOVE -> MoonBridge.LI_TOUCH_EVENT_MOVE
        MotionEvent.ACTION_CANCEL -> MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL
        MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> MoonBridge.LI_TOUCH_EVENT_HOVER
        MotionEvent.ACTION_HOVER_EXIT -> MoonBridge.LI_TOUCH_EVENT_HOVER_LEAVE
        MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> MoonBridge.LI_TOUCH_EVENT_BUTTON_ONLY
        else -> -1
    }

    private fun getStreamViewRelativeNormalizedXY(view: View?, event: MotionEvent, pointerIndex: Int): FloatArray {
        val rawX = event.getX(pointerIndex)
        val rawY = event.getY(pointerIndex)
        return getStreamViewRelativeNormalizedXY(view, rawX, rawY)
    }

    private fun getStreamViewRelativeNormalizedXY(view: View?, rawX: Float, rawY: Float): FloatArray {
        val activeStreamView = game.activeStreamView ?: return floatArrayOf(0f, 0f)

        if (game.externalDisplayManager != null && game.externalDisplayManager?.isUsingExternalDisplay() == true) {
            val touchWidth: Float
            val touchHeight: Float
            if (view != null && view.width > 0 && view.height > 0) {
                touchWidth = view.width.toFloat()
                touchHeight = view.height.toFloat()
            } else {
                val size = Point()
                game.windowManager.defaultDisplay.getRealSize(size)
                touchWidth = size.x.toFloat()
                touchHeight = size.y.toFloat()
            }
            return floatArrayOf(
                (rawX / touchWidth).coerceIn(0f, 1f),
                (rawY / touchHeight).coerceIn(0f, 1f)
            )
        }

        val absoluteX: Float
        val absoluteY: Float
        if (view == activeStreamView) {
            absoluteX = rawX
            absoluteY = rawY
        } else {
            val scaleX = activeStreamView.scaleX
            val scaleY = activeStreamView.scaleY
            if (scaleX == 0f || scaleY == 0f) return floatArrayOf(0f, 0f)
            absoluteX = (rawX - activeStreamView.x) / scaleX
            absoluteY = (rawY - activeStreamView.y) / scaleY
        }
        val streamWidth = activeStreamView.width
        val streamHeight = activeStreamView.height
        if (streamWidth == 0 || streamHeight == 0) return floatArrayOf(0f, 0f)

        return floatArrayOf(
            (absoluteX / streamWidth).coerceIn(0f, 1f),
            (absoluteY / streamHeight).coerceIn(0f, 1f)
        )
    }

    private fun getStreamViewNormalizedContactArea(event: MotionEvent, pointerIndex: Int): FloatArray {
        val orientation = if (event.device == null || event.device.getMotionRange(MotionEvent.AXIS_ORIENTATION, event.source) == null) {
            (Math.PI / 4).toFloat()
        } else {
            event.getOrientation(pointerIndex)
        }

        val contactAreaMajor: Float
        val contactAreaMinor: Float
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_EXIT -> {
                contactAreaMajor = event.getToolMajor(pointerIndex)
                contactAreaMinor = event.getToolMinor(pointerIndex)
            }
            else -> {
                contactAreaMajor = event.getTouchMajor(pointerIndex)
                contactAreaMinor = event.getTouchMinor(pointerIndex)
            }
        }

        return getStreamViewNormalizedContactArea(contactAreaMajor, contactAreaMinor, orientation)
    }

    private fun getStreamViewNormalizedContactArea(event: MotionEvent, pointerCoords: MotionEvent.PointerCoords): FloatArray {
        val orientation = if (event.device == null || event.device.getMotionRange(MotionEvent.AXIS_ORIENTATION, event.source) == null) {
            (Math.PI / 4).toFloat()
        } else {
            pointerCoords.orientation
        }
        val isHover = event.actionMasked == MotionEvent.ACTION_HOVER_ENTER ||
            event.actionMasked == MotionEvent.ACTION_HOVER_MOVE ||
            event.actionMasked == MotionEvent.ACTION_HOVER_EXIT
        val contactAreaMajor = if (isHover) pointerCoords.toolMajor else pointerCoords.touchMajor
        val contactAreaMinor = if (isHover) pointerCoords.toolMinor else pointerCoords.touchMinor
        return getStreamViewNormalizedContactArea(contactAreaMajor, contactAreaMinor, orientation)
    }

    private fun getStreamViewNormalizedContactArea(contactAreaMajor: Float, contactAreaMinor: Float, orientation: Float): FloatArray {
        val majorCart = polarToCartesian(contactAreaMajor, orientation)
        val minorCart = polarToCartesian(contactAreaMinor, (orientation + (Math.PI / 2).toFloat()))

        val refView = game.activeStreamView
        val refWidth = if (refView != null && refView.width > 0) refView.width else game.streamView.width.coerceAtLeast(1)
        val refHeight = if (refView != null && refView.height > 0) refView.height else game.streamView.height.coerceAtLeast(1)

        majorCart[0] = abs(majorCart[0]).coerceAtMost(refWidth.toFloat()) / refWidth
        minorCart[0] = abs(minorCart[0]).coerceAtMost(refWidth.toFloat()) / refWidth
        majorCart[1] = abs(majorCart[1]).coerceAtMost(refHeight.toFloat()) / refHeight
        minorCart[1] = abs(minorCart[1]).coerceAtMost(refHeight.toFloat()) / refHeight

        return floatArrayOf(cartesianToR(majorCart), cartesianToR(minorCart))
    }

    private fun getPressureOrDistance(event: MotionEvent, pointerCoords: MotionEvent.PointerCoords): Float {
        return when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_EXIT -> {
                val distanceRange = event.device?.getMotionRange(MotionEvent.AXIS_DISTANCE, event.source)
                if (distanceRange != null) {
                    normalizeValueInRange(pointerCoords.getAxisValue(MotionEvent.AXIS_DISTANCE), distanceRange)
                } else {
                    0f
                }
            }
            else -> pointerCoords.pressure
        }
    }

    private fun getRotationDegrees(event: MotionEvent, pointerCoords: MotionEvent.PointerCoords): Short {
        if (event.device?.getMotionRange(MotionEvent.AXIS_ORIENTATION, event.source) != null) {
            var rotationDegrees = Math.toDegrees(pointerCoords.orientation.toDouble()).toInt().toShort()
            if (rotationDegrees < 0) rotationDegrees = (rotationDegrees + 360).toShort()
            return rotationDegrees
        }
        return MoonBridge.LI_ROT_UNKNOWN
    }

    private fun sendPenEventForPointer(
        view: View,
        event: MotionEvent,
        eventType: Byte,
        toolType: Byte,
        pointerIndex: Int,
        historyPosition: Int = CURRENT_MOTION_SAMPLE
    ): Boolean {
        val pointerCoords = penPointerCoords
        if (historyPosition == CURRENT_MOTION_SAMPLE) {
            event.getPointerCoords(pointerIndex, pointerCoords)
        } else {
            event.getHistoricalPointerCoords(pointerIndex, historyPosition, pointerCoords)
        }

        var penButtons: Byte = 0
        if (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0) {
            penButtons = (penButtons.toInt() or MoonBridge.LI_PEN_BUTTON_PRIMARY.toInt()).toByte()
        }
        if (event.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY != 0) {
            penButtons = (penButtons.toInt() or MoonBridge.LI_PEN_BUTTON_SECONDARY.toInt()).toByte()
        }

        var tiltDegrees = MoonBridge.LI_TILT_UNKNOWN
        val dev = event.device
        if (dev?.getMotionRange(MotionEvent.AXIS_TILT, event.source) != null) {
            tiltDegrees = Math.toDegrees(pointerCoords.getAxisValue(MotionEvent.AXIS_TILT).toDouble()).toInt().toByte()
        }

        val normalizedCoords = getStreamViewRelativeNormalizedXY(view, pointerCoords.x, pointerCoords.y)
        val normalizedContactArea = getStreamViewNormalizedContactArea(event, pointerCoords)
        return game.conn?.sendPenEvent(
            eventType, toolType, penButtons,
            normalizedCoords[0], normalizedCoords[1],
            getPressureOrDistance(event, pointerCoords),
            normalizedContactArea[0], normalizedContactArea[1],
            getRotationDegrees(event, pointerCoords), tiltDegrees
        ) != MoonBridge.LI_ERR_UNSUPPORTED
    }

    private fun trySendPenEvent(view: View, event: MotionEvent): Boolean {
        val eventType = getLiTouchTypeFromEvent(event)
        if (eventType < 0) return false

        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            var handledStylusEvent = false
            val sentAllSamples = visitMotionEventSamples(event.historySize, event.pointerCount) { pointerIndex, historyPosition ->
                val toolType = convertToolTypeToStylusToolType(event, pointerIndex)
                if (toolType == MoonBridge.LI_TOOL_TYPE_UNKNOWN) {
                    true
                } else {
                    handledStylusEvent = true
                    if (historyPosition == CURRENT_MOTION_SAMPLE && game.prefConfig.enableEnhancedTouch) {
                        nativeTouchPointerMap[event.getPointerId(pointerIndex)]?.updatePointerCoords(event, pointerIndex)
                    }
                    sendPenEventForPointer(view, event, eventType, toolType, pointerIndex, historyPosition)
                }
            }
            return sentAllSamples && handledStylusEvent
        } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            return game.conn?.sendPenEvent(
                MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL, MoonBridge.LI_TOOL_TYPE_UNKNOWN, 0,
                0f, 0f, 0f, 0f, 0f,
                MoonBridge.LI_ROT_UNKNOWN, MoonBridge.LI_TILT_UNKNOWN
            ) != MoonBridge.LI_ERR_UNSUPPORTED
        } else {
            val toolType = convertToolTypeToStylusToolType(event, event.actionIndex)
            if (toolType == MoonBridge.LI_TOOL_TYPE_UNKNOWN) return false

            if (game.prefConfig.enableEnhancedTouch) {
                val actionIndex = event.actionIndex
                when (event.actionMasked) {
                    MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN, MotionEvent.ACTION_HOVER_ENTER -> {
                        val pointer = NativeTouchContext.Pointer(event)
                        nativeTouchPointerMap[pointer.pointerId] = pointer
                    }
                    MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_HOVER_EXIT -> {
                        nativeTouchPointerMap.remove(event.getPointerId(actionIndex))
                    }
                    MotionEvent.ACTION_HOVER_MOVE -> {
                        nativeTouchPointerMap[event.getPointerId(actionIndex)]?.updatePointerCoords(event, actionIndex)
                    }
                }
            }
            return sendPenEventForPointer(view, event, eventType, toolType, event.actionIndex)
        }
    }

    private fun sendTouchEventForPointer(view: View?, event: MotionEvent, eventType: Byte, pointerIndex: Int): Boolean {
        val normalizedCoords = getStreamViewRelativeNormalizedXY(view, event, pointerIndex)
        val normalizedContactArea = getStreamViewNormalizedContactArea(event, pointerIndex)
        return game.conn?.sendTouchEvent(
            eventType, event.getPointerId(pointerIndex),
            normalizedCoords[0], normalizedCoords[1],
            getPressureOrDistance(event, pointerIndex),
            normalizedContactArea[0], normalizedContactArea[1],
            getRotationDegrees(event, pointerIndex)
        ) != MoonBridge.LI_ERR_UNSUPPORTED
    }

    private fun trySendTouchEvent(view: View?, event: MotionEvent): Boolean {
        val eventType = getLiTouchTypeFromEvent(event)
        if (eventType < 0) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    if (game.prefConfig.enableEnhancedTouch) {
                        nativeTouchPointerMap[event.getPointerId(i)]?.updatePointerCoords(event, i)
                    }
                    if (!sendTouchEventForPointer(view, event, eventType, i)) return false
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                return game.conn?.sendTouchEvent(
                    MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL, 0,
                    0f, 0f, 0f, 0f, 0f,
                    MoonBridge.LI_ROT_UNKNOWN
                ) != MoonBridge.LI_ERR_UNSUPPORTED
            }
            else -> {
                val actionIndex = event.actionIndex
                when (event.actionMasked) {
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        multiFingerTapChecker(event)
                        if (game.prefConfig.enableEnhancedTouch) {
                            val pointer = NativeTouchContext.Pointer(event)
                            nativeTouchPointerMap[pointer.pointerId] = pointer
                        }
                    }

                    MotionEvent.ACTION_DOWN -> {
                        if (game.prefConfig.enableEnhancedTouch) {
                            val pointer = NativeTouchContext.Pointer(event)
                            nativeTouchPointerMap[pointer.pointerId] = pointer
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        if (event.eventTime - multiFingerDownTime < MULTI_FINGER_TAP_THRESHOLD) {
                            game.toggleKeyboard()
                        }
                    }

                    MotionEvent.ACTION_POINTER_UP -> {
                        if (game.prefConfig.enableEnhancedTouch) {
                            nativeTouchPointerMap.remove(event.getPointerId(actionIndex))
                        }
                    }
                }
                return sendTouchEventForPointer(view, event, eventType, actionIndex)
            }
        }
    }

    private fun multiFingerTapChecker(event: MotionEvent) {
        if (event.pointerCount == game.prefConfig.nativeTouchFingersToToggleKeyboard) {
            multiFingerDownTime = event.eventTime
        }
    }

    // ---- 坐标归一化 ----

    private fun getNormalizedCoordinates(streamView: View?, rawX: Float, rawY: Float): FloatArray {
        if (streamView == null) return floatArrayOf(rawX, rawY)

        if (game.externalDisplayManager != null && game.externalDisplayManager?.isUsingExternalDisplay() == true) {
            val active = game.activeStreamView
            if (active != null && active.width > 0 && active.height > 0) {
                val size = Point()
                game.windowManager.defaultDisplay.getRealSize(size)
                val scaleX = active.width.toFloat() / size.x
                val scaleY = active.height.toFloat() / size.y
                return floatArrayOf(rawX * scaleX, rawY * scaleY)
            }
            return floatArrayOf(rawX, rawY)
        }

        val scaleX = streamView.scaleX
        val scaleY = streamView.scaleY
        if (scaleX == 0f || scaleY == 0f) return floatArrayOf(rawX, rawY)

        return floatArrayOf(
            (rawX - streamView.x) / scaleX,
            (rawY - streamView.y) / scaleY
        )
    }

    // ---- 工具方法 ----

    private fun getTouchContext(actionIndex: Int): TouchContext? =
        if (actionIndex < touchContextMap.size) touchContextMap[actionIndex] else null

    private fun logIgnoredStylusBranchToolType(event: MotionEvent, pointerIndex: Int, phase: String) {
        LimeLog.warning(
            "Ignoring non-stylus tool type in stylus branch: phase=$phase" +
                ", toolType=${event.getToolType(pointerIndex)}" +
                ", source=${event.source}" +
                ", action=${event.actionMasked}"
        )
    }

    private fun eventHasStylusTool(event: MotionEvent): Boolean =
        containsStylusTool(event.pointerCount) { event.getToolType(it) }

    fun getRelativeTouchContextMap(): Array<RelativeTouchContext?> =
        Array(relativeTouchContextMap.size) { i ->
            relativeTouchContextMap[i] as? RelativeTouchContext
        }

    fun setTouchMode(enableRelativeTouch: Boolean) {
        for (i in touchContextMap.indices) {
            if (enableRelativeTouch) {
                game.prefConfig.touchscreenTrackpad = true
                game.prefConfig.enableNativeMousePointer = false
                touchContextMap = relativeTouchContextMap
                game.cursorServiceManager.refreshCursorMode()
            } else {
                game.prefConfig.touchscreenTrackpad = false
                touchContextMap = absoluteTouchContextMap
                game.cursorServiceManager.refreshCursorMode()
            }
        }
    }

    fun setEnhancedTouch(enableRelativeTouch: Boolean) {
        game.prefConfig.enableEnhancedTouch = enableRelativeTouch
        if (game.prefConfig.enableEnhancedTouch) {
            game.prefConfig.enableNativeMousePointer = false
        }
    }

    fun cancelNonRootTouchpad() {
        nonRootTouchpadHandler.cancelAll(game.conn)
    }

    /**
     * 初始化触控上下文（由 Game 在 onCreate / prepareConnection 中调用）
     */
    fun initTouchContexts(conn: NvConnection, streamView: StreamView, prefConfig: PreferenceConfiguration) {
        for (i in 0 until TOUCH_CONTEXT_LENGTH) {
            absoluteTouchContextMap[i] = AbsoluteTouchContext(conn, i, streamView)
            relativeTouchContextMap[i] = RelativeTouchContext(conn, i, streamView, prefConfig)
        }
        touchContextMap = if (!prefConfig.touchscreenTrackpad) absoluteTouchContextMap else relativeTouchContextMap
    }

    companion object {
        const val TOUCH_CONTEXT_LENGTH = 2
        private const val TWO_FINGER_TAP_THRESHOLD = 100L
        private const val TWO_FINGER_MOVE_THRESHOLD = 40f
        private const val STYLUS_DOWN_DEAD_ZONE_DELAY = 100L
        private const val STYLUS_DOWN_DEAD_ZONE_RADIUS = 20f
        private const val STYLUS_UP_DEAD_ZONE_DELAY = 150L
        private const val STYLUS_UP_DEAD_ZONE_RADIUS = 50f
        private const val MULTI_FINGER_TAP_THRESHOLD = 300L

        private fun normalizeValueInRange(value: Float, range: InputDevice.MotionRange): Float =
            (value - range.min) / range.range

        private fun getPressureOrDistance(event: MotionEvent, pointerIndex: Int): Float {
            return when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_EXIT -> {
                    val dev = event.device
                    if (dev != null) {
                        val distanceRange = dev.getMotionRange(MotionEvent.AXIS_DISTANCE, event.source)
                        if (distanceRange != null) {
                            return normalizeValueInRange(event.getAxisValue(MotionEvent.AXIS_DISTANCE, pointerIndex), distanceRange)
                        }
                    }
                    0f
                }
                else -> event.getPressure(pointerIndex)
            }
        }

        private fun getRotationDegrees(event: MotionEvent, pointerIndex: Int): Short {
            val dev = event.device
            if (dev?.getMotionRange(MotionEvent.AXIS_ORIENTATION, event.source) != null) {
                var rotationDegrees = Math.toDegrees(event.getOrientation(pointerIndex).toDouble()).toInt().toShort()
                if (rotationDegrees < 0) rotationDegrees = (rotationDegrees + 360).toShort()
                return rotationDegrees
            }
            return MoonBridge.LI_ROT_UNKNOWN
        }

        private fun polarToCartesian(r: Float, theta: Float): FloatArray =
            floatArrayOf((r * cos(theta)), (r * sin(theta)))

        private fun cartesianToR(point: FloatArray): Float =
            sqrt(point[0].pow(2) + point[1].pow(2))

        private fun convertToolTypeToStylusToolType(event: MotionEvent, pointerIndex: Int): Byte =
            when (event.getToolType(pointerIndex)) {
                MotionEvent.TOOL_TYPE_ERASER -> MoonBridge.LI_TOOL_TYPE_ERASER
                MotionEvent.TOOL_TYPE_STYLUS -> MoonBridge.LI_TOOL_TYPE_PEN
                else -> MoonBridge.LI_TOOL_TYPE_UNKNOWN
            }
    }
}
