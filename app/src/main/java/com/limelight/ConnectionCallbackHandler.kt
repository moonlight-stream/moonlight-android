package com.limelight

import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.limelight.binding.audio.MicrophoneManager
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.utils.Dialog
import com.limelight.utils.ServerHelper
import com.limelight.utils.ShortcutHelper
import com.limelight.utils.UiHelper

/**
 * NvConnectionListener 回调实现和连接停止逻辑。
 * 从 Game.java 提取，处理连接阶段通知、连接成功/失败/断开、网络质量更新。
 */
class ConnectionCallbackHandler(private val game: Game) {

    fun stageStarting(stage: String) {
        game.runOnUiThread {
            game.progressOverlay?.setMessage(
                game.resources.getString(R.string.conn_starting) + " " + stage
            )
        }
    }

    fun stageComplete(stage: String) {
        // no-op
    }

    fun stageFailed(stage: String, portFlags: Int, errorCode: Int) {
        // Perform a connection test if the failure could be due to a blocked port
        // This does network I/O, so don't do it on the main thread.
        val portTestResult = MoonBridge.testClientConnectivity(
            ServerHelper.CONNECTION_TEST_SERVER, 443, portFlags
        )

        game.runOnUiThread {
            game.progressOverlay?.dismiss()
            game.progressOverlay = null

            if (!game.displayedFailureDialog) {
                game.displayedFailureDialog = true
                LimeLog.severe("$stage failed: $errorCode")

                // If video initialization failed and the surface is still valid, display extra information
                if (stage.contains("video") && game.streamView?.holder?.surface?.isValid == true) {
                    Toast.makeText(
                        game, game.resources.getText(R.string.video_decoder_init_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }

                var dialogText = game.resources.getString(R.string.conn_error_msg) +
                        " " + stage + " (error " + errorCode + ")"

                if (portFlags != 0) {
                    dialogText += "\n\n" + game.resources.getString(R.string.check_ports_msg) + "\n" +
                            MoonBridge.stringifyPortFlags(portFlags, "\n")
                }

                if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0) {
                    dialogText += "\n\n" + game.resources.getString(R.string.nettest_text_blocked)
                }

                Dialog.displayDialog(
                    game, game.resources.getString(R.string.conn_error_title),
                    dialogText, true
                )
            }
        }
    }

    fun connectionTerminated(errorCode: Int) {
        // Perform a connection test if the failure could be due to a blocked port
        // This does network I/O, so don't do it on the main thread.
        val portFlags = MoonBridge.getPortFlagsFromTerminationErrorCode(errorCode)
        val portTestResult = MoonBridge.testClientConnectivity(
            ServerHelper.CONNECTION_TEST_SERVER, 443, portFlags
        )

        game.runOnUiThread {
            // Let the display go to sleep now
            game.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Stop processing controller input
            game.controllerHandler?.stop()

            game.microphoneManager?.stopMicrophoneStream()

            // Ungrab input
            game.setInputGrabState(false)

            if (!game.displayedFailureDialog) {
                game.displayedFailureDialog = true
                LimeLog.severe("Connection terminated: $errorCode")
                stopConnection()

                // Display the error dialog if it was an unexpected termination.
                // Otherwise, just finish the activity immediately.
                if (errorCode != MoonBridge.ML_ERROR_GRACEFUL_TERMINATION) {
                    val message: String = if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0) {
                        game.resources.getString(R.string.nettest_text_blocked)
                    } else {
                        when (errorCode) {
                            MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC ->
                                game.resources.getString(R.string.no_video_received_error)
                            MoonBridge.ML_ERROR_NO_VIDEO_FRAME ->
                                game.resources.getString(R.string.no_frame_received_error)
                            MoonBridge.ML_ERROR_UNEXPECTED_EARLY_TERMINATION,
                            MoonBridge.ML_ERROR_PROTECTED_CONTENT ->
                                game.resources.getString(R.string.early_termination_error)
                            MoonBridge.ML_ERROR_FRAME_CONVERSION ->
                                game.resources.getString(R.string.frame_conversion_error)
                            else -> {
                                val errorCodeString = if (Math.abs(errorCode) > 1000) {
                                    Integer.toHexString(errorCode)
                                } else {
                                    Integer.toString(errorCode)
                                }
                                game.resources.getString(R.string.conn_terminated_msg) + "\n\n" +
                                        game.resources.getString(R.string.error_code_prefix) + " " + errorCodeString
                            }
                        }
                    }

                    var finalMessage = message
                    if (portFlags != 0) {
                        finalMessage += "\n\n" + game.resources.getString(R.string.check_ports_msg) + "\n" +
                                MoonBridge.stringifyPortFlags(portFlags, "\n")
                    }

                    Dialog.displayDialog(
                        game, game.resources.getString(R.string.conn_terminated_title),
                        finalMessage, true
                    )
                } else {
                    game.finish()
                }
            }
        }
    }

    fun connectionStatusUpdate(connectionStatus: Int) {
        game.runOnUiThread {
            if (game.prefConfig.disableWarnings) {
                return@runOnUiThread
            }

            if (connectionStatus == MoonBridge.CONN_STATUS_POOR) {
                val message = if (game.prefConfig.bitrate > 5000) {
                    game.resources.getString(R.string.slow_connection_msg)
                } else {
                    game.resources.getString(R.string.poor_connection_msg)
                }
                game.notificationOverlayManager.update(connectionStatus, message)
                game.notificationOverlayManager.setRequestedVisible(true)
            } else if (connectionStatus == MoonBridge.CONN_STATUS_OKAY) {
                game.notificationOverlayManager.setRequestedVisible(false)
            }

            game.notificationOverlayManager.applyVisibility()
        }
    }

    fun connectionStarted() {
        game.runOnUiThread {
            // 不在此处 dismiss progressOverlay：connectionStarted 是连接级回调，
            // 视频首帧通常还没解码出来。dismiss 已交由 decoderRenderer.firstFrameCallback
            // 在首帧到达瞬间触发，避免 "loading 消失 → 黑屏 → 闪出画面" 的割裂感。
            // 这里只做 5 秒 fallback，防止首帧因异常迟迟不来导致 loading 卡死。
            val overlay = game.progressOverlay
            if (overlay != null) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (game.progressOverlay === overlay) {
                        overlay.dismiss()
                        game.progressOverlay = null
                    }
                }, 5000)
            }

            game.connected = true
            game.orientationManager.connected = true
            game.connecting = false
            game.updatePipAutoEnter()

            // Hide the mouse cursor now after a short delay.
            val h = Handler(Looper.getMainLooper())
            h.postDelayed({
                if (game.prefConfig.enableNativeMousePointer) {
                    game.enableNativeMousePointer(true)
                } else {
                    game.setInputGrabState(true)
                }
            }, 500)

            // Keep the display on
            game.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Update GameManager state to indicate we're in game
            UiHelper.notifyStreamConnected(game)

            game.hideSystemUi(1000)

            game.ds5TouchpadFeedbackView?.showActivatedHint()

            // 连接一开始就启动保活服务
            val prefs = PreferenceManager.getDefaultSharedPreferences(game)
            val isResumeEnabled = prefs.getBoolean("checkbox_resume_stream", false)
            if (isResumeEnabled) game.showKeepAliveNotification()

            // Cursor negotiation also updates Android views and main-thread timeout state.
            game.cursorServiceManager.onConnectionStarted()
        }

        // Report this shortcut being used (off the main thread to prevent ANRs)
        val computer = ComputerDetails()
        computer.name = game.pcName
        computer.uuid = game.intent.getStringExtra(Game.EXTRA_PC_UUID)
        val shortcutHelper = ShortcutHelper(game)
        shortcutHelper.reportComputerShortcutUsed(computer)
        if (game.appName != null) {
            shortcutHelper.reportGameLaunched(computer, game.app!!)
        }

        // Prepare the output pipeline when HDR is expected. This is intentionally separate from
        // the host setHdrMode callback so diagnostics don't claim HDR before the stream activates.
        val appSupportsHdr = game.intent.getBooleanExtra(Game.EXTRA_APP_HDR, false)
        if (appSupportsHdr && game.prefConfig.enableHdr && game.isNegotiatedHdrEnabled()) {
            game.prepareInitialHdrOutput()
        }

        // 初始化麦克风管理器
        game.microphoneManager = MicrophoneManager(game, game.conn, game.prefConfig.enableMic)
        game.microphoneManager?.setStateListener(object : MicrophoneManager.MicrophoneStateListener {
            override fun onMicrophoneStateChanged(isActive: Boolean) {
                LimeLog.info("麦克风状态改变: " + if (isActive) "激活" else "暂停")
            }

            override fun onPermissionRequested() {
                LimeLog.info("麦克风权限请求已发送")
            }
        })

        // 初始化麦克风流
        if (game.prefConfig.enableMic) {
            game.runOnUiThread {
                if (game.microphoneManager?.initializeMicrophoneStream() != true) {
                    LimeLog.warning("Failed to start microphone stream")
                } else {
                    LimeLog.info("Microphone stream initialized successfully")
                }

                // 更新麦克风按钮状态
                if (game.micButton != null) {
                    game.microphoneManager?.setMicrophoneButton(game.micButton)
                    game.microphoneManager?.setDefaultStateOff()
                }
            }
        }

        // 初始化串流时长统计
        game.streamStartTime = System.currentTimeMillis()
        game.accumulatedStreamTime = 0
        game.lastActiveTime = game.streamStartTime
        game.isStreamingActive = true

        // 记录游戏流媒体开始事件
        if (game.analyticsManager != null && game.pcName != null) {
            game.analyticsManager?.logGameStreamStart(game.pcName!!, game.appName)
        }

        // 1. 获取并保存 IP (存到全局变量)
        game.currentHostAddress = game.intent.getStringExtra(Game.EXTRA_HOST)

        // 2. 启动智能码率（如设置已开启）
        game.startAdaptiveBitrateIfEnabled()
    }

    /**
     * 停止连接并清理相关资源。
     */
    fun stopConnection() {
        // 重置尝试连接标志。
        game.attemptedConnection = false

        game.cancelKeepAliveNotification()

        if (game.connecting || game.connected) {
            game.connecting = false
            game.connected = false
            game.orientationManager.connected = false
            game.updatePipAutoEnter()

            // 停止智能码率
            game.stopAdaptiveBitrate()

            // 停止智能码率
            game.stopAdaptiveBitrate()

            game.controllerHandler?.stop()

            // 停止并释放 USB 控制器接管
            game.usbDriverServiceManager?.stopAndUnbind()

            // 停止麦克风流
            game.microphoneManager?.stopMicrophoneStream()

            // Update GameManager state to indicate we're no longer in game
            UiHelper.notifyStreamEnded(game)

            // Save current settings for this app before stopping connection
            val uuid = game.computerUuid
            if (uuid != null) {
                game.appSettingsManager?.saveAppLastSettings(uuid, game.app, game.prefConfig)
            }

            // Restore host-composited cursor mode before native control-stream teardown.
            game.cursorServiceManager.stopService()

            // Stop may take a few hundred ms to do some network I/O to tell
            // the server we're going away and clean up. Let it run in a separate
            // thread to keep things smooth for the UI.
            Thread { game.conn?.stop() }.start()
        }
    }
}
