package com.limelight

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.Gravity
import android.view.PointerIcon
import android.widget.FrameLayout
import com.limelight.binding.input.touch.RelativeTouchContext
import com.limelight.binding.input.touch.TouchContext
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.ui.CursorView
import com.limelight.ui.StreamView
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * Owns cursor mode negotiation and presentation for an active stream.
 *
 * Cursor transport and packet reassembly live in moonlight-common-c. This class
 * deliberately only handles Android policy, decoding, caching, and rendering.
 */
class CursorServiceManager(
    private val streamView: StreamView,
    private val cursorOverlay: CursorView?,
    private val prefConfig: PreferenceConfiguration,
    private val relativeTouchContextMap: Array<TouchContext?>,
    private val uiCallback: UiCallback
) {

    interface UiCallback {
        fun runOnUi(runnable: Runnable)
        fun isActivityAlive(): Boolean
        fun onLocalCursorFallback()
    }

    private data class CursorShape(
        val bitmap: Bitmap,
        val hotspotX: Int,
        val hotspotY: Int
    )

    private data class RenderKey(
        val shapeId: Int,
        val viewWidth: Int,
        val viewHeight: Int,
        val streamWidth: Int,
        val streamHeight: Int
    )

    private val decodeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "CursorShapeDecoder").apply { isDaemon = true }
    }
    private val sessionGeneration = AtomicInteger()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cursorCache = object : LruCache<Int, CursorShape>(SOURCE_CURSOR_CACHE_KB) {
        override fun sizeOf(key: Int, value: CursorShape): Int = bitmapSizeKb(value.bitmap)
    }
    private val renderedCursorCache = object : LruCache<RenderKey, CursorShape>(RENDERED_CURSOR_CACHE_KB) {
        override fun sizeOf(key: RenderKey, value: CursorShape): Int = bitmapSizeKb(value.bitmap)
    }

    @Volatile
    private var connected = false
    @Volatile
    private var localModeActive = false
    @Volatile
    private var destroyed = false

    private var hostCursorVisible = false
    private var currentShapeId: Int? = null
    private var streamWidth = prefConfig.width.coerceAtLeast(1)
    private var streamHeight = prefConfig.height.coerceAtLeast(1)
    private var loggedUnsupportedHost = false
    private var cursorUpdateTimeout: Runnable? = null

    fun initializeLocalCursorRenderers(width: Int, height: Int) {
        if (cursorOverlay == null) return

        for (context in relativeTouchContextMap) {
            if (context is RelativeTouchContext) {
                context.initializeLocalCursorRenderer(cursorOverlay, width, height)
                context.setEnableLocalCursorRendering(shouldRenderOverlay())
            }
        }
    }

    fun destroyLocalCursorRenderers() {
        for (context in relativeTouchContextMap) {
            if (context is RelativeTouchContext) {
                context.destroyLocalCursorRenderer()
            }
        }
    }

    fun refreshCursorMode() {
        reconcileCursorMode()
        updateRelativeCursorRenderers()
        if (localModeActive) {
            postApplyCurrentCursor()
        }
    }

    fun onConnectionStarted() {
        connected = true
        loggedUnsupportedHost = false
        sessionGeneration.incrementAndGet()
        clearCursorState()
        reconcileCursorMode()
    }

    fun onStreamResolutionChanged(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        streamWidth = width
        streamHeight = height
        renderedCursorCache.evictAll()
        postApplyCurrentCursor()
    }

    fun onCursorUpdate(
        flags: Int,
        shapeId: Int,
        width: Int,
        height: Int,
        hotspotX: Int,
        hotspotY: Int,
        bgraPixels: ByteArray?
    ) {
        if (!connected || !localModeActive || destroyed) return

        val generation = sessionGeneration.get()
        val visible = flags and MoonBridge.LI_CURSOR_UPDATE_FLAG_VISIBLE != 0
        val hasShape = flags and MoonBridge.LI_CURSOR_UPDATE_FLAG_SHAPE != 0

        try {
            decodeExecutor.execute {
                if (destroyed || generation != sessionGeneration.get()) return@execute

                val decodedShape = if (hasShape) {
                    decodeShape(width, height, hotspotX, hotspotY, bgraPixels)
                } else {
                    null
                }

                uiCallback.runOnUi {
                    if (!uiCallback.isActivityAlive() || destroyed ||
                        generation != sessionGeneration.get() || !localModeActive) {
                        return@runOnUi
                    }

                    if (decodedShape != null) {
                        cursorCache.put(shapeId, decodedShape)
                        renderedCursorCache.evictAll()
                    } else if (hasShape) {
                        LimeLog.warning("Cursor: rejected malformed shape id=$shapeId ${width}x$height")
                        return@runOnUi
                    }

                    cancelCursorUpdateTimeout()
                    currentShapeId = shapeId
                    hostCursorVisible = visible
                    applyCurrentCursorOnUiThread()
                }
            }
        } catch (_: RejectedExecutionException) {
            LimeLog.info("Cursor: ignored update after decoder shutdown")
        }
    }

    fun syncCursorWithStream() {
        if (cursorOverlay == null) return

        val width = streamView.width
        val height = streamView.height
        if (width == 0 || height == 0) return

        val params = cursorOverlay.layoutParams
        if (params is FrameLayout.LayoutParams) {
            params.gravity = Gravity.TOP or Gravity.LEFT
        }

        if (params.width != width || params.height != height) {
            params.width = width
            params.height = height
            cursorOverlay.layoutParams = params
            renderedCursorCache.evictAll()
        }

        cursorOverlay.pivotX = streamView.pivotX
        cursorOverlay.pivotY = streamView.pivotY
        cursorOverlay.scaleX = streamView.scaleX
        cursorOverlay.scaleY = streamView.scaleY
        cursorOverlay.x = streamView.x
        cursorOverlay.y = streamView.y

        initializeLocalCursorRenderers(width, height)
        postApplyCurrentCursor()
    }

    /** Kept for existing lifecycle call sites while the old socket service is removed. */
    fun stopService() {
        val wasLocal = localModeActive
        if (connected && wasLocal) {
            MoonBridge.setCursorMode(MoonBridge.LI_CURSOR_MODE_VIDEO)
        }

        connected = false
        localModeActive = false
        sessionGeneration.incrementAndGet()
        cancelCursorUpdateTimeout()
        clearCursorState()
        updateRelativeCursorRenderers()
        restoreDefaultCursor()
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        stopService()
        decodeExecutor.shutdownNow()
        destroyLocalCursorRenderers()
    }

    fun isServiceRunning(): Boolean = localModeActive

    private fun shouldUseLocalMode(): Boolean {
        return CursorModePolicy.shouldUseLocalMode(
            nativePointerSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N,
            nativePointerEnabled = prefConfig.enableNativeMousePointer,
            touchpadEnabled = prefConfig.touchscreenTrackpad,
            localCursorEnabled = prefConfig.enableLocalCursorRendering,
            hasCursorOverlay = cursorOverlay != null
        )
    }

    private fun shouldRenderOverlay(): Boolean {
        return CursorModePolicy.shouldRenderTouchpadOverlay(
            localModeActive = localModeActive,
            nativePointerSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N,
            touchpadEnabled = prefConfig.touchscreenTrackpad,
            localCursorEnabled = prefConfig.enableLocalCursorRendering,
            nativePointerEnabled = prefConfig.enableNativeMousePointer
        )
    }

    private fun reconcileCursorMode() {
        if (!connected || destroyed) return

        val shouldUseLocal = shouldUseLocalMode()
        if (shouldUseLocal == localModeActive) return

        if (!shouldUseLocal) {
            cancelCursorUpdateTimeout()
            if (localModeActive) {
                MoonBridge.setCursorMode(MoonBridge.LI_CURSOR_MODE_VIDEO)
            }
            localModeActive = false
            hostCursorVisible = false
            updateRelativeCursorRenderers()
            restoreDefaultCursor()
            return
        }

        val featureFlags = MoonBridge.getHostFeatureFlags()
        if (featureFlags and MoonBridge.LI_FF_CURSOR_SHAPE == 0) {
            logUnsupportedHostOnce("feature flag unavailable")
            localModeActive = false
            updateRelativeCursorRenderers()
            restoreDefaultCursor()
            return
        }

        val result = MoonBridge.setCursorMode(MoonBridge.LI_CURSOR_MODE_LOCAL)
        if (result == MoonBridge.LI_CURSOR_MODE_OK) {
            localModeActive = true
            hostCursorVisible = false
            currentShapeId = null
            hideLocalPresentation()
            updateRelativeCursorRenderers()
            scheduleCursorUpdateTimeout()
            LimeLog.info("Cursor: local rendering mode enabled")
        } else {
            localModeActive = false
            logUnsupportedHostOnce("mode negotiation failed: $result")
            updateRelativeCursorRenderers()
            restoreDefaultCursor()
        }
    }

    private fun logUnsupportedHostOnce(reason: String) {
        if (loggedUnsupportedHost) return
        loggedUnsupportedHost = true
        LimeLog.warning("Cursor: using host-composited cursor ($reason)")
        uiCallback.runOnUi {
            if (uiCallback.isActivityAlive()) {
                uiCallback.onLocalCursorFallback()
            }
        }
    }

    private fun scheduleCursorUpdateTimeout() {
        cancelCursorUpdateTimeout()
        val generation = sessionGeneration.get()
        cursorUpdateTimeout = Runnable {
            if (!connected || !localModeActive || generation != sessionGeneration.get()) return@Runnable
            LimeLog.warning("Cursor: no update received after enabling local mode")
            MoonBridge.setCursorMode(MoonBridge.LI_CURSOR_MODE_VIDEO)
            localModeActive = false
            hostCursorVisible = false
            updateRelativeCursorRenderers()
            restoreDefaultCursor()
            logUnsupportedHostOnce("cursor update timeout")
        }.also { mainHandler.postDelayed(it, CURSOR_UPDATE_TIMEOUT_MS) }
    }

    private fun cancelCursorUpdateTimeout() {
        cursorUpdateTimeout?.let(mainHandler::removeCallbacks)
        cursorUpdateTimeout = null
    }

    private fun updateRelativeCursorRenderers() {
        val enabled = shouldRenderOverlay()
        for (context in relativeTouchContextMap) {
            if (context is RelativeTouchContext) {
                context.setEnableLocalCursorRendering(enabled)
            }
        }
    }

    private fun decodeShape(
        width: Int,
        height: Int,
        hotspotX: Int,
        hotspotY: Int,
        pixels: ByteArray?
    ): CursorShape? {
        if (width !in 1..MAX_CURSOR_DIMENSION || height !in 1..MAX_CURSOR_DIMENSION ||
            hotspotX !in 0 until width || hotspotY !in 0 until height ||
            pixels == null || pixels.size != width * height * 4) {
            return null
        }

        val colors = CursorPixelConverter.bgraToArgb(pixels)
        val bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
        return CursorShape(bitmap, hotspotX, hotspotY)
    }

    private fun postApplyCurrentCursor() {
        uiCallback.runOnUi {
            if (uiCallback.isActivityAlive() && !destroyed) {
                applyCurrentCursorOnUiThread()
            }
        }
    }

    private fun applyCurrentCursorOnUiThread() {
        if (!localModeActive || !hostCursorVisible) {
            hideLocalPresentation()
            return
        }

        val shapeId = currentShapeId ?: run {
            hideLocalPresentation()
            return
        }
        val source = cursorCache.get(shapeId) ?: run {
            hideLocalPresentation()
            return
        }
        val shape = scaleShapeForView(shapeId, source)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && prefConfig.enableNativeMousePointer) {
            try {
                streamView.pointerIcon = PointerIcon.create(
                    shape.bitmap,
                    shape.hotspotX.toFloat(),
                    shape.hotspotY.toFloat()
                )
            } catch (e: Exception) {
                LimeLog.warning("Cursor: failed to apply native pointer: ${e.message}")
            }
        } else if (shouldRenderOverlay()) {
            cursorOverlay?.setCursorBitmap(shape.bitmap, shape.hotspotX, shape.hotspotY)
            cursorOverlay?.setHostCursorVisible(true)
        }
    }

    private fun scaleShapeForView(shapeId: Int, source: CursorShape): CursorShape {
        val viewWidth = streamView.width.coerceAtLeast(1)
        val viewHeight = streamView.height.coerceAtLeast(1)
        val key = RenderKey(shapeId, viewWidth, viewHeight, streamWidth, streamHeight)
        renderedCursorCache.get(key)?.let { return it }

        val scaleX = viewWidth.toFloat() / streamWidth.toFloat()
        val scaleY = viewHeight.toFloat() / streamHeight.toFloat()
        val targetWidth = (source.bitmap.width * scaleX).roundToInt().coerceAtLeast(1)
        val targetHeight = (source.bitmap.height * scaleY).roundToInt().coerceAtLeast(1)
        if (targetWidth == source.bitmap.width && targetHeight == source.bitmap.height) {
            return source
        }
        val scaled = Bitmap.createScaledBitmap(source.bitmap, targetWidth, targetHeight, true)
        val result = CursorShape(
            scaled,
            (source.hotspotX * scaleX).roundToInt().coerceIn(0, targetWidth - 1),
            (source.hotspotY * scaleY).roundToInt().coerceIn(0, targetHeight - 1)
        )
        renderedCursorCache.put(key, result)
        return result
    }

    private fun bitmapSizeKb(bitmap: Bitmap): Int {
        return ((bitmap.allocationByteCount.toLong() + 1023L) / 1024L)
            .coerceIn(1L, Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun hideLocalPresentation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && prefConfig.enableNativeMousePointer) {
            try {
                streamView.pointerIcon = PointerIcon.getSystemIcon(
                    streamView.context,
                    PointerIcon.TYPE_NULL
                )
            } catch (_: Exception) {
            }
        }
        cursorOverlay?.setHostCursorVisible(false)
    }

    private fun restoreDefaultCursor() {
        uiCallback.runOnUi {
            if (!uiCallback.isActivityAlive()) return@runOnUi
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && prefConfig.enableNativeMousePointer) {
                try {
                    streamView.pointerIcon = PointerIcon.getSystemIcon(
                        streamView.context,
                        PointerIcon.TYPE_ARROW
                    )
                } catch (_: Exception) {
                }
            }
            cursorOverlay?.setHostCursorVisible(false)
            cursorOverlay?.resetToDefault()
        }
    }

    private fun clearCursorState() {
        hostCursorVisible = false
        currentShapeId = null
        cursorCache.evictAll()
        renderedCursorCache.evictAll()
    }

    companion object {
        private const val MAX_CURSOR_DIMENSION = 256
        private const val CURSOR_UPDATE_TIMEOUT_MS = 1500L
        private const val SOURCE_CURSOR_CACHE_KB = 8 * 1024
        private const val RENDERED_CURSOR_CACHE_KB = 8 * 1024
    }
}

internal object CursorModePolicy {
    fun shouldUseLocalMode(
        nativePointerSupported: Boolean,
        nativePointerEnabled: Boolean,
        touchpadEnabled: Boolean,
        localCursorEnabled: Boolean,
        hasCursorOverlay: Boolean
    ): Boolean {
        val nativePointer = nativePointerSupported && nativePointerEnabled
        val touchpadOverlay = touchpadEnabled && localCursorEnabled && hasCursorOverlay
        return nativePointer || touchpadOverlay
    }

    fun shouldRenderTouchpadOverlay(
        localModeActive: Boolean,
        nativePointerSupported: Boolean,
        touchpadEnabled: Boolean,
        localCursorEnabled: Boolean,
        nativePointerEnabled: Boolean
    ): Boolean {
        return localModeActive && touchpadEnabled && localCursorEnabled &&
                !(nativePointerSupported && nativePointerEnabled)
    }
}

internal object CursorPixelConverter {
    fun bgraToArgb(pixels: ByteArray): IntArray {
        require(pixels.size % 4 == 0) { "BGRA payload length must be divisible by 4" }
        return IntArray(pixels.size / 4) { index ->
            val offset = index * 4
            val b = pixels[offset].toInt() and 0xFF
            val g = pixels[offset + 1].toInt() and 0xFF
            val r = pixels[offset + 2].toInt() and 0xFF
            val a = pixels[offset + 3].toInt() and 0xFF
            (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
