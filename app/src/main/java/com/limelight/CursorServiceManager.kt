package com.limelight

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.Gravity
import android.view.PointerIcon
import android.widget.FrameLayout
import com.limelight.binding.input.touch.RelativeTouchContext
import com.limelight.binding.input.touch.TouchContext
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.ui.CursorView
import com.limelight.ui.StreamView
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 光标服务管理器
 * 负责光标网络服务（接收服务端光标变化）、动画播放、本地光标渲染器管理。
 * 从 Game 类提取，减少 Game 的职责范围。
 */
class CursorServiceManager(
    private val streamView: StreamView,
    private val cursorOverlay: CursorView?,
    private val prefConfig: PreferenceConfiguration,
    private val relativeTouchContextMap: Array<TouchContext?>,
    private val uiCallback: UiCallback
) {

    /**
     * UI 回调接口，用于与 Activity 交互
     */
    interface UiCallback {
        fun runOnUi(runnable: Runnable)
        fun isActivityAlive(): Boolean
    }

    private var cursorNetworkThread: Thread? = null
    @Volatile
    private var isCursorNetworking = false
    private var cursorSocket: Socket? = null
    private var computerIpAddress: String? = null

    private var currentAnimationTask: Runnable? = null
    private val animationHandler = Handler(Looper.getMainLooper())

    private val cursorCache = LruCache<Int, Bitmap>(100)

    // ========== 本地光标渲染器管理 ==========

    fun initializeLocalCursorRenderers(width: Int, height: Int) {
        if (cursorOverlay == null) return

        for (context in relativeTouchContextMap) {
            if (context != null && context is RelativeTouchContext) {
                context.initializeLocalCursorRenderer(cursorOverlay, width, height)
                val shouldShow = prefConfig.enableLocalCursorRendering &&
                        prefConfig.touchscreenTrackpad &&
                        !prefConfig.enableNativeMousePointer
                context.setEnableLocalCursorRendering(shouldShow)
            }
        }
    }

    fun destroyLocalCursorRenderers() {
        for (context in relativeTouchContextMap) {
            if (context != null && context is RelativeTouchContext) {
                context.destroyLocalCursorRenderer()
            }
        }
    }

    fun refreshLocalCursorState(enabled: Boolean) {
        val shouldRender = enabled && !prefConfig.enableNativeMousePointer

        for (context in relativeTouchContextMap) {
            if (context != null && context is RelativeTouchContext) {
                context.setEnableLocalCursorRendering(shouldRender)
            }
        }
        updateServiceState(enabled)
    }

    // ========== 光标与视频流同步 ==========

    fun syncCursorWithStream() {
        if (cursorOverlay == null) return

        val x = streamView.x
        val y = streamView.y
        val w = streamView.width
        val h = streamView.height

        if (w == 0 || h == 0) return

        val params = cursorOverlay.layoutParams

        if (params is FrameLayout.LayoutParams) {
            params.gravity = Gravity.TOP or Gravity.LEFT
        }

        var needLayout = false
        if (params.width != w || params.height != h) {
            params.width = w
            params.height = h
            needLayout = true
        }

        if (needLayout) {
            cursorOverlay.layoutParams = params
        }

        cursorOverlay.pivotX = streamView.pivotX
        cursorOverlay.pivotY = streamView.pivotY
        cursorOverlay.scaleX = streamView.scaleX
        cursorOverlay.scaleY = streamView.scaleY
        cursorOverlay.x = x
        cursorOverlay.y = y

        initializeLocalCursorRenderers(w, h)

        LimeLog.info("CursorFix: Sync executed: W=$w H=$h X=$x")
    }

    // ========== 光标网络服务 ==========

    fun startService() {
        if (isCursorNetworking) return
        isCursorNetworking = true

        cursorNetworkThread = Thread {
            while (isCursorNetworking) {
                try {
                    val socket = Socket()
                    cursorSocket = socket
                    socket.connect(InetSocketAddress(computerIpAddress, CURSOR_PORT), 3000)
                    socket.tcpNoDelay = true
                    val dis = DataInputStream(socket.getInputStream())

                    cursorCache.evictAll()

                    while (isCursorNetworking) {
                        val lenBytes = ByteArray(4)
                        dis.readFully(lenBytes)
                        val packetLen = (lenBytes[0].toInt() and 0xFF) or
                                ((lenBytes[1].toInt() and 0xFF) shl 8) or
                                ((lenBytes[2].toInt() and 0xFF) shl 16) or
                                ((lenBytes[3].toInt() and 0xFF) shl 24)

                        val bodyData = ByteArray(packetLen)
                        dis.readFully(bodyData)

                        val wrapped = ByteBuffer.wrap(bodyData)
                        wrapped.order(ByteOrder.LITTLE_ENDIAN)

                        val cursorHash = wrapped.int
                        val hotX = wrapped.int
                        val hotY = wrapped.int
                        val frameCount = wrapped.int
                        val frameDelay = wrapped.int

                        val headerSize = 20
                        val pngSize = packetLen - headerSize

                        var targetBitmap: Bitmap?

                        if (pngSize > 0) {
                            targetBitmap = BitmapFactory.decodeByteArray(bodyData, headerSize, pngSize)
                            if (targetBitmap != null) {
                                cursorCache.put(cursorHash, targetBitmap)
                            }
                        } else {
                            targetBitmap = cursorCache.get(cursorHash)
                            if (targetBitmap == null) {
                                LimeLog.warning("CursorNet: 缓存未命中! Hash: $cursorHash")
                                continue
                            }
                        }

                        if (targetBitmap != null) {
                            val finalBmp = targetBitmap
                            uiCallback.runOnUi { handleCursorUpdate(finalBmp, hotX, hotY, frameCount, frameDelay) }
                        }
                    }
                } catch (e: Exception) {
                    LimeLog.warning("CursorNet: Connection disconnected or failed: ${e.message}")
                } finally {
                    try { cursorSocket?.close() } catch (_: Exception) {}
                    cursorSocket = null

                    if (isCursorNetworking) {
                        stopCurrentAnimation()
                        restoreDefaultCursor()

                        LimeLog.info("CursorNet: 2秒后重试连接...")
                        try { Thread.sleep(2000) } catch (_: InterruptedException) { break }
                    }
                }
            }
            LimeLog.info("CursorNet: 服务线程已退出")
        }.also { it.start() }
    }

    fun stopService() {
        isCursorNetworking = false

        cursorNetworkThread?.interrupt()

        try { cursorSocket?.close() } catch (_: Exception) {}
        cursorSocket = null

        uiCallback.runOnUi {
            if (!uiCallback.isActivityAlive()) return@runOnUi
            restoreDefaultCursorOnUiThread()
        }

        LimeLog.info("CursorNet: 服务已停止")
    }

    fun updateServiceState(shouldRun: Boolean) {
        updateServiceState(shouldRun, null)
    }

    fun updateServiceState(shouldRun: Boolean, hostAddress: String?) {
        if (hostAddress != null) {
            computerIpAddress = hostAddress
        }

        if (shouldRun) {
            if (!isCursorNetworking && computerIpAddress != null) {
                LimeLog.info("CursorNet: Enabling cursor service during stream with host: $computerIpAddress")
                startService()
            }
        } else {
            if (isCursorNetworking) {
                LimeLog.info("CursorNet: Disabling cursor service during stream")
                stopService()
            }
        }
    }

    fun isServiceRunning(): Boolean = isCursorNetworking

    // ========== 内部方法 ==========

    private fun stopCurrentAnimation() {
        animationHandler.removeCallbacksAndMessages(null)
        currentAnimationTask = null
    }

    private fun handleCursorUpdate(spriteSheet: Bitmap, hotX: Int, hotY: Int, frameCount: Int, frameDelay: Int) {
        stopCurrentAnimation()

        if (frameCount <= 1) {
            setSystemOrOverlayCursor(spriteSheet, hotX, hotY)
            return
        }

        try {
            val singleFrameW = spriteSheet.width
            val singleFrameH = spriteSheet.height / frameCount

            val frames = Array(frameCount) { i ->
                Bitmap.createBitmap(spriteSheet, 0, i * singleFrameH, singleFrameW, singleFrameH)
            }

            currentAnimationTask = object : Runnable {
                var index = 0
                override fun run() {
                    if (!isCursorNetworking) return
                    setSystemOrOverlayCursor(frames[index], hotX, hotY)
                    index = (index + 1) % frameCount
                    animationHandler.postDelayed(this, if (frameDelay > 0) frameDelay.toLong() else 33L)
                }
            }.also { it.run() }
        } catch (e: Exception) {
            LimeLog.warning("CursorNet: 动画处理失败: ${e.message}")
            setSystemOrOverlayCursor(spriteSheet, hotX, hotY)
        }
    }

    private fun setSystemOrOverlayCursor(bitmap: Bitmap, hotX: Int, hotY: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && prefConfig.enableNativeMousePointer) {
            try {
                val pointerIcon = PointerIcon.create(bitmap, hotX.toFloat(), hotY.toFloat())
                streamView.pointerIcon = pointerIcon
            } catch (_: Exception) {}
        } else {
            cursorOverlay?.setCursorBitmap(bitmap, hotX, hotY)
        }
    }

    private fun restoreDefaultCursor() {
        uiCallback.runOnUi { restoreDefaultCursorOnUiThread() }
    }

    private fun restoreDefaultCursorOnUiThread() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && prefConfig.enableNativeMousePointer) {
            try {
                streamView.pointerIcon = PointerIcon.getSystemIcon(
                    streamView.context, PointerIcon.TYPE_ARROW
                )
            } catch (_: Exception) {}
        } else {
            cursorOverlay?.resetToDefault()
        }
    }

    companion object {
        private const val CURSOR_PORT = 5005
    }
}
