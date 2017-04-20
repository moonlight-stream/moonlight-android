package com.limelight


import com.google.vr.ndk.base.GvrLayout
import com.google.vr.sdk.base.AndroidCompat
import com.limelight.binding.PlatformBinding
import com.limelight.binding.video.EnhancedDecoderRenderer
import com.limelight.binding.video.MediaCodecHelper
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.NvConnectionListener
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.av.video.VideoDecoderRenderer
import com.limelight.nvstream.http.NvApp
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.utils.Dialog

import android.app.Activity
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.input.InputManager
import android.net.wifi.WifiManager
import android.opengl.GLSurfaceView
import android.os.*
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import com.limelight.binding.input.ControllerHandler
import com.limelight.binding.input.KeyboardTranslator
import com.limelight.binding.input.TouchContext
import com.limelight.binding.input.capture.InputCaptureManager
import com.limelight.binding.input.capture.InputCaptureProvider
import com.limelight.binding.input.driver.UsbDriverService
import com.limelight.binding.input.evdev.EvdevListener
import com.limelight.binding.input.virtual_controller.VirtualController
import com.limelight.nvstream.input.KeyboardPacket
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.ui.GameGestures
import com.limelight.vr.VideoSceneRenderer
import com.limelight.vr.MediaCodecDecoderRendererVR
import kotlin.experimental.and
import kotlin.experimental.or

class VirtualRealityGame : Activity(), NvConnectionListener, EvdevListener,
        View.OnGenericMotionListener, GameGestures {

    private var lastMouseX = Integer.MIN_VALUE
    private var lastMouseY = Integer.MIN_VALUE
    private var lastButtonState = 0

    private val REFERENCE_HORIZ_RES = 1280
    private val REFERENCE_VERT_RES = 720

    private var controllerHandler: ControllerHandler? = null
    private var keybTranslator: KeyboardTranslator? = null
    private var inputCaptureProvider: InputCaptureProvider? = null
    private var modifierFlags = 0
    private var grabbedInput = true
    private var grabComboDown = false
    private var connectedToUsbDriverService = false
    private val usbDriverServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            val binder = iBinder as UsbDriverService.UsbDriverBinder
            binder.setListener(controllerHandler)
            connectedToUsbDriverService = true
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            connectedToUsbDriverService = false
        }
    }

    private var gvrLayout: GvrLayout? = null
    private var surfaceView: GLSurfaceView? = null
    private var renderer: VideoSceneRenderer? = null
    private var hasFirstFrame: Boolean = false

    // Transform a quad that fills the clip box at Z=0 to a 16:9 screen at Z=-98. Note that the matrix
    // is column-major, so the translation is on the last line in this representation.
    private val videoTransform = floatArrayOf(67.2f, 0.0f, 0.0f, 0.0f, 0.0f, 37.8f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, -98f, 1.0f)

    // Runnable to refresh the viewer profile when gvrLayout is resumed.
    // This is done on the GL thread because refreshViewerProfile isn't thread-safe.
    private val refreshViewerProfileRunnable = Runnable { gvrLayout!!.gvrApi.refreshViewerProfile() }


    private var prefConfig: PreferenceConfiguration? = null
    private var conn: NvConnection? = null
    private var displayedFailureDialog = false
    private var connecting = false
    private var connected = false

    private var decoderRenderer: EnhancedDecoderRenderer? = null

    private var wifiLock: WifiManager.WifiLock? = null

    private val drFlags = 0

    private var host: String? = null
    private var uniqueId: String? = null
    private var streamConfig: StreamConfiguration? = null

    private fun setImmersiveSticky() {
        window
                .decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Setting up Moonlight-Android's stream

        // Make sure Wi-Fi is fully powered up
        val wifiMgr = getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Limelight")
        wifiLock!!.setReferenceCounted(false)
        wifiLock!!.acquire()

        host = this@VirtualRealityGame.intent.getStringExtra(EXTRA_HOST)
        val appName = this@VirtualRealityGame.intent.getStringExtra(EXTRA_APP_NAME)
        val appId = this@VirtualRealityGame.intent.getIntExtra(EXTRA_APP_ID, StreamConfiguration.INVALID_APP_ID)
        uniqueId = this@VirtualRealityGame.intent.getStringExtra(EXTRA_UNIQUEID)
        val remote = this@VirtualRealityGame.intent.getBooleanExtra(EXTRA_STREAMING_REMOTE, false)
        val uuid = this@VirtualRealityGame.intent.getStringExtra(EXTRA_PC_UUID)
        val pcName = this@VirtualRealityGame.intent.getStringExtra(EXTRA_PC_NAME)

        if (appId == StreamConfiguration.INVALID_APP_ID) {
            finish()
            return
        }

        // Initialize the MediaCodec helper before creating the decoder
        MediaCodecHelper.initializeWithContext(this)

        // Read the stream preferences
        prefConfig = PreferenceConfiguration.readPreferences(this)

        decoderRenderer = MediaCodecDecoderRendererVR(prefConfig!!.videoFormat)

        streamConfig = StreamConfiguration.Builder()
                .setResolution(prefConfig!!.width, prefConfig!!.height)
                .setRefreshRate(prefConfig!!.fps)
                .setApp(NvApp(appName, appId))
                .setBitrate(prefConfig!!.bitrate * 1000)
                .setEnableSops(prefConfig!!.enableSops)
                .enableAdaptiveResolution(decoderRenderer!!.capabilities and VideoDecoderRenderer.CAPABILITY_ADAPTIVE_RESOLUTION != 0)
                .enableLocalAudioPlayback(prefConfig!!.playHostAudio)
                .setMaxPacketSize(if (remote) 1024 else 1292)
                .setRemote(remote)
                .setHevcSupported(decoderRenderer!!.isHevcSupported)
                .setAudioConfiguration(if (prefConfig!!.enable51Surround)
                    StreamConfiguration.AUDIO_CONFIGURATION_5_1
                else
                    StreamConfiguration.AUDIO_CONFIGURATION_STEREO)
                .build()


        //Start of VR code
        setImmersiveSticky()
        window
                .decorView
                .setOnSystemUiVisibilityChangeListener { visibility ->
                    if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                        setImmersiveSticky()
                    }
                }

        AndroidCompat.setSustainedPerformanceMode(this, true)
        AndroidCompat.setVrModeEnabled(this, true)

        gvrLayout = GvrLayout(this)
        surfaceView = GLSurfaceView(this)
        surfaceView!!.setEGLContextClientVersion(2)
        surfaceView!!.setEGLConfigChooser(5, 6, 5, 0, 0, 0)

        //for controller input
        surfaceView!!.setOnGenericMotionListener(this)

        gvrLayout!!.setPresentationView(surfaceView)
        gvrLayout!!.keepScreenOn = true
        renderer = VideoSceneRenderer(this, gvrLayout!!.gvrApi)

        //Initialize the ExternalSurfaceListener to receive video Surface callbacks.
        hasFirstFrame = false
        val videoSurfaceListener = object : GvrLayout.ExternalSurfaceListener {
            override fun onSurfaceAvailable(surface: Surface) {
                //Set the surface for Moonlight's stream to output video frames to. Video
                //playback is started when the Surface is set.
                if (!connected && !connecting) {
                    connecting = true

                    conn!!.start(PlatformBinding.getDeviceName(), surface, drFlags,
                            PlatformBinding.getAudioRenderer(), decoderRenderer)
                }
            }

            override fun onFrameAvailable() {
                //If this is the first frame, signal to remove the loading splash screen,
                //and draw alpha 0 in the color buffer where the video will be drawn by the
                //GvrApi.
                if (!hasFirstFrame) {
                    surfaceView!!.queueEvent { renderer!!.setHasVideoPlaybackStarted(true) }

                    hasFirstFrame = true
                }
            }
        }

        // Initialize the connection
        conn = NvConnection(host, uniqueId, this@VirtualRealityGame, streamConfig, PlatformBinding.getCryptoProvider(this@VirtualRealityGame))
        keybTranslator = KeyboardTranslator(conn)
        controllerHandler = ControllerHandler(this@VirtualRealityGame, conn, this@VirtualRealityGame, prefConfig!!.multiController, prefConfig!!.deadzonePercentage)

        val inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(controllerHandler, null)
        inputCaptureProvider = InputCaptureManager.getInputCaptureProvider(this@VirtualRealityGame, this@VirtualRealityGame)

        if (prefConfig!!.usbDriver) {
            // Start the USB driver
            applicationContext.bindService(Intent(this@VirtualRealityGame, UsbDriverService::class.java),
                    usbDriverServiceConnection, Service.BIND_AUTO_CREATE)
        }

        //Note that the video Surface must be enabled before enabling Async Reprojection.
        //Async Reprojection must be enabled for the app to be able to use the video Surface.
        val isSurfaceEnabled = gvrLayout!!.enableAsyncReprojectionVideoSurface(
                videoSurfaceListener,
                Handler(Looper.getMainLooper()),
                false
        )
        val isAsyncReprojectionEnabled = gvrLayout!!.setAsyncReprojectionEnabled(true)

        if (!isSurfaceEnabled || !isAsyncReprojectionEnabled) {
            //The device doesn't support this API, video won't play.
            Log.e(
                    TAG,
                    "UnsupportedException: "
                            + (if (!isAsyncReprojectionEnabled) "Async Reprojection not supported. " else "")
                            + if (!isSurfaceEnabled) "Async Reprojection Video Surface not enabled." else "")
        } else {
            //The default value puts the viewport behind the eye, so it's invisible. Set the transform
            //now to ensure the video is visible when rendering starts.
            renderer!!.setVideoTransform(videoTransform)

            //The ExternalSurface buffer the GvrApi should reference when drawing the video buffer. This
            //must be called after enabling the Async Reprojection video surface.
            renderer!!.setVideoSurfaceId(gvrLayout!!.asyncReprojectionVideoSurfaceId)
        }

        //Set the renderer and start the app's GL thread.
        surfaceView!!.setRenderer(renderer)

        setContentView(gvrLayout)
    }

    private fun stopConnection() {
        if (connecting || connected) {
            connected = false
            connecting = connected
            conn!!.stop()
        }
        // Enable cursor visibility again
        inputCaptureProvider!!.disableCapture()

        // Destroy the capture provider
        inputCaptureProvider!!.destroy()
    }

    //NvConnectionListener callbacks
    override fun displayMessage(message: String) {
        runOnUiThread {
            //Toast.makeText(Game.this, message, Toast.LENGTH_LONG).show();
        }
    }

    override fun displayTransientMessage(message: String) {
        if (!prefConfig!!.disableWarnings) {
            runOnUiThread {
                //Toast.makeText(Game.this, message, Toast.LENGTH_LONG).show();
            }
        }
    }

    override fun stageStarting(stage: NvConnectionListener.Stage) {
        //spinner.setMessage(getResources().getString(R.string.conn_starting)+" "+stage.getName());
    }

    override fun stageComplete(stage: NvConnectionListener.Stage) {}
    override fun stageFailed(stage: NvConnectionListener.Stage) {

        if (!displayedFailureDialog) {
            displayedFailureDialog = true
            stopConnection()
            Dialog.displayDialog(this, resources.getString(R.string.conn_error_title),
                    resources.getString(R.string.conn_error_msg) + " " + stage.getName(), true)
        }
    }

    override fun connectionTerminated(e: Exception) {
        if (!displayedFailureDialog) {
            displayedFailureDialog = true
            e.printStackTrace()

            stopConnection()
        }
    }

    override fun connectionStarted() {
        connecting = false
        connected = true

        inputCaptureProvider!!.enableCapture()
    }
    //End NvConnectionListener callbacks.

    override fun onStart() {
        super.onStart()
        hasFirstFrame = false
        surfaceView!!.queueEvent { renderer!!.setHasVideoPlaybackStarted(false) }

        //Resume the gvrLayout here. This will start the render thread and trigger a
        //new async reprojection video Surface to become available.
        gvrLayout!!.onResume()
        //Refresh the viewer profile in case the viewer params were changed.
        surfaceView!!.queueEvent(refreshViewerProfileRunnable)
    }

    override fun onStop() {

        if (controllerHandler != null) {
            val inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
            inputManager.unregisterInputDeviceListener(controllerHandler)
        }

        wifiLock!!.release()

        if (connectedToUsbDriverService) {
            // Unbind from the discovery service
            //TODO:: figure out if code is necessary, for some reason this crashes in VR.
            //unbindService(usbDriverServiceConnection)
        }

        if (conn != null) {
            val videoFormat = conn!!.activeVideoFormat

            displayedFailureDialog = true
            stopConnection()

            val averageEndToEndLat = decoderRenderer!!.averageEndToEndLatency
            val averageDecoderLat = decoderRenderer!!.averageDecoderLatency
            var message: String? = null
            if (averageEndToEndLat > 0) {
                message = resources.getString(R.string.conn_client_latency) + " " + averageEndToEndLat + " ms"
                if (averageDecoderLat > 0) {
                    message += " (" + resources.getString(R.string.conn_client_latency_hw) + " " + averageDecoderLat + " ms)"
                }
            } else if (averageDecoderLat > 0) {
                message = resources.getString(R.string.conn_hardware_latency) + " " + averageDecoderLat + " ms"
            }

            // Add the video codec to the post-stream toast
            if (message != null && videoFormat != VideoDecoderRenderer.VideoFormat.Unknown) {
                if (videoFormat == VideoDecoderRenderer.VideoFormat.H265) {
                    message += " [H.265]"
                } else {
                    message += " [H.264]"
                }
            }

            if (message != null) {
                //log stats
            }
        }

        // Pause the gvrLayout here. The video Surface is guaranteed to be detached and not
        // available after gvrLayout.onPause(). We pause from onStop() to avoid needing to wait
        // for an available video Surface following brief onPause()/onResume() events. Wait for the
        // new onSurfaceAvailable() callback with a valid Surface before resuming the video player.
        gvrLayout!!.onPause()
        super.onStop()
    }

    override fun onDestroy() {
        gvrLayout!!.shutdown()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setImmersiveSticky()
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return handleMotionEvent(event) || super.onGenericMotionEvent(event)

    }

    override fun onGenericMotion(v: View, event: MotionEvent): Boolean {
        return handleMotionEvent(event)
    }

    override fun mouseMove(deltaX: Int, deltaY: Int) {
        conn!!.sendMouseMove(deltaX.toShort(), deltaY.toShort())
    }

    override fun mouseButtonEvent(buttonId: Int, down: Boolean) {
        val buttonIndex: Byte

        when (buttonId) {
            EvdevListener.BUTTON_LEFT -> buttonIndex = MouseButtonPacket.BUTTON_LEFT
            EvdevListener.BUTTON_MIDDLE -> buttonIndex = MouseButtonPacket.BUTTON_MIDDLE
            EvdevListener.BUTTON_RIGHT -> buttonIndex = MouseButtonPacket.BUTTON_RIGHT
            else -> {
                LimeLog.warning("Unhandled button: " + buttonId)
                return
            }
        }

        if (down) {
            conn!!.sendMouseButtonDown(buttonIndex)
        } else {
            conn!!.sendMouseButtonUp(buttonIndex)
        }
    }

    override fun mouseScroll(amount: Byte) {
        conn!!.sendMouseScroll(amount)
    }

    override fun keyboardEvent(buttonDown: Boolean, keyCode: Short) {
        val keyMap = keybTranslator!!.translate(keyCode.toInt())
        if (keyMap.toInt() != 0) {
            if (handleSpecialKeys(keyMap, buttonDown)) {
                return
            }

            if (buttonDown) {
                keybTranslator!!.sendKeyDown(keyMap, getModifierState())
            } else {
                keybTranslator!!.sendKeyUp(keyMap, getModifierState())
            }
        }
    }

    // Returns true if the key stroke was consumed
    private fun handleSpecialKeys(translatedKey: Short, down: Boolean): Boolean {
        var translatedKey = translatedKey
        var modifierMask = 0

        // Mask off the high byte
        translatedKey = translatedKey and 0xff

        if (translatedKey.toInt() == KeyboardTranslator.VK_CONTROL) {
            modifierMask = KeyboardPacket.MODIFIER_CTRL.toInt()
        } else if (translatedKey.toInt() == KeyboardTranslator.VK_SHIFT) {
            modifierMask = KeyboardPacket.MODIFIER_SHIFT.toInt()
        } else if (translatedKey.toInt() == KeyboardTranslator.VK_ALT) {
            modifierMask = KeyboardPacket.MODIFIER_ALT.toInt()
        }

        if (down) {
            this.modifierFlags = this.modifierFlags or modifierMask
        } else {
            this.modifierFlags = this.modifierFlags and modifierMask.inv()
        }

        // Check if Ctrl+Shift+Z is pressed
        if (translatedKey.toInt() == KeyboardTranslator.VK_Z && (getModifierState() and (KeyboardPacket.MODIFIER_CTRL or KeyboardPacket.MODIFIER_SHIFT)) == KeyboardPacket.MODIFIER_CTRL or KeyboardPacket.MODIFIER_SHIFT) {
            if (down) {
                // Now that we've pressed the magic combo
                // we'll wait for one of the keys to come up
                grabComboDown = true
            } else {
                // Toggle the grab if Z comes up
                val h = window.decorView.handler
                h?.postDelayed(toggleGrab, 250)

                grabComboDown = false
            }

            return true
        } else if (grabComboDown) {
            val h = window.decorView.handler
            h?.postDelayed(toggleGrab, 250)

            grabComboDown = false
            return true
        }// Toggle the grab if control or shift comes up

        // Not a special combo
        return false
    }

    private fun getModifierState(event: KeyEvent): Byte {
        var modifier: Byte = 0
        if (event.isShiftPressed) {
            modifier = modifier or KeyboardPacket.MODIFIER_SHIFT
        }
        if (event.isCtrlPressed) {
            modifier = modifier or KeyboardPacket.MODIFIER_CTRL
        }
        if (event.isAltPressed) {
            modifier = modifier or KeyboardPacket.MODIFIER_ALT
        }
        return modifier
    }

    private fun getModifierState(): Byte {
        return modifierFlags.toByte()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Pass-through virtual navigation keys
        if (event.flags and KeyEvent.FLAG_VIRTUAL_HARD_KEY != 0) {
            return super.onKeyDown(keyCode, event)
        }

        // Try the controller handler first
        val handled = controllerHandler!!.handleButtonDown(event)
        if (!handled) {
            // Try the keyboard handler
            val translated = keybTranslator!!.translate(event.keyCode)
            if (translated.toInt() == 0) {
                return super.onKeyDown(keyCode, event)
            }

            // Let this method take duplicate key down events
            if (handleSpecialKeys(translated, true)) {
                return true
            }

            // Pass through keyboard input if we're not grabbing
            if (!grabbedInput) {
                return super.onKeyDown(keyCode, event)
            }

            keybTranslator!!.sendKeyDown(translated,
                    getModifierState(event))
        }

        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // Pass-through virtual navigation keys
        if (event.flags and KeyEvent.FLAG_VIRTUAL_HARD_KEY != 0) {
            return super.onKeyUp(keyCode, event)
        }

        // Try the controller handler first
        val handled = controllerHandler!!.handleButtonUp(event)
        if (!handled) {
            // Try the keyboard handler
            val translated = keybTranslator!!.translate(event.keyCode)
            if (translated.toInt() == 0) {
                return super.onKeyUp(keyCode, event)
            }

            if (handleSpecialKeys(translated, false)) {
                return true
            }

            // Pass through keyboard input if we're not grabbing
            if (!grabbedInput) {
                return super.onKeyUp(keyCode, event)
            }

            keybTranslator!!.sendKeyUp(translated,
                    getModifierState(event))
        }

        return true
    }

    private val toggleGrab = Runnable {
        if (grabbedInput) {
            inputCaptureProvider!!.disableCapture()
        } else {
            inputCaptureProvider!!.enableCapture()
        }

        grabbedInput = !grabbedInput
    }

    // Returns true if the event was consumed
    private fun handleMotionEvent(event: MotionEvent): Boolean {
        // Pass through keyboard input if we're not grabbing
        if (!grabbedInput) {
            return false
        }

        if (event.source and InputDevice.SOURCE_CLASS_JOYSTICK != 0) {
            if (controllerHandler!!.handleMotionEvent(event)) {
                return true
            }
        } else if (event.source and InputDevice.SOURCE_CLASS_POINTER != 0) {
            // This case is for mice
            if (event.source == InputDevice.SOURCE_MOUSE || event.pointerCount >= 1 && event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) {
                val changedButtons = event.buttonState xor lastButtonState

                if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
                    // Send the vertical scroll packet
                    val vScrollClicks = event.getAxisValue(MotionEvent.AXIS_VSCROLL).toByte()
                    conn!!.sendMouseScroll(vScrollClicks)
                }

                if (changedButtons and MotionEvent.BUTTON_PRIMARY != 0) {
                    if (event.buttonState and MotionEvent.BUTTON_PRIMARY != 0) {
                        conn!!.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
                    } else {
                        conn!!.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
                    }
                }

                if (changedButtons and MotionEvent.BUTTON_SECONDARY != 0) {
                    if (event.buttonState and MotionEvent.BUTTON_SECONDARY != 0) {
                        conn!!.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
                    } else {
                        conn!!.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
                    }
                }

                if (changedButtons and MotionEvent.BUTTON_TERTIARY != 0) {
                    if (event.buttonState and MotionEvent.BUTTON_TERTIARY != 0) {
                        conn!!.sendMouseButtonDown(MouseButtonPacket.BUTTON_MIDDLE)
                    } else {
                        conn!!.sendMouseButtonUp(MouseButtonPacket.BUTTON_MIDDLE)
                    }
                }

                // Get relative axis values if we can
                if (inputCaptureProvider!!.eventHasRelativeMouseAxes(event)) {
                    // Send the deltas straight from the motion event
                    conn!!.sendMouseMove(inputCaptureProvider!!.getRelativeAxisX(event).toShort(),
                            inputCaptureProvider!!.getRelativeAxisY(event).toShort())

                    // We have to also update the position Android thinks the cursor is at
                    // in order to avoid jumping when we stop moving or click.
                    lastMouseX = event.x.toInt()
                    lastMouseY = event.y.toInt()
                } else {
                    // First process the history
                    for (i in 0..event.historySize - 1) {
                        updateMousePosition(event.getHistoricalX(i).toInt(), event.getHistoricalY(i).toInt())
                    }

                    // Now process the current values
                    updateMousePosition(event.x.toInt(), event.y.toInt())
                }

                lastButtonState = event.buttonState
            }
            // Handled a known source
            return true
        }

        // Unknown class
        return false
    }

    private fun updateMousePosition(eventX: Int, eventY: Int) {
        // Send a mouse move if we already have a mouse location
        // and the mouse coordinates change
        if (lastMouseX != Integer.MIN_VALUE &&
                lastMouseY != Integer.MIN_VALUE &&
                !(lastMouseX == eventX && lastMouseY == eventY)) {
            var deltaX = eventX - lastMouseX
            var deltaY = eventY - lastMouseY

            // Scale the deltas if the device resolution is different
            // than the stream resolution
            deltaX = Math.round(deltaX.toDouble() * (REFERENCE_HORIZ_RES / surfaceView!!.getWidth().toDouble())).toInt()
            deltaY = Math.round(deltaY.toDouble() * (REFERENCE_VERT_RES / surfaceView!!.getHeight().toDouble())).toInt()

            conn!!.sendMouseMove(deltaX.toShort(), deltaY.toShort())
        }

        // Update pointer location for delta calculation next time
        lastMouseX = eventX
        lastMouseY = eventY
    }

    override fun showKeyboard() {
        LimeLog.info("Showing keyboard overlay")
        val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
    }

    companion object {
        internal val TAG = "moonlightVR"

        val EXTRA_HOST = "Host"
        val EXTRA_APP_NAME = "AppName"
        val EXTRA_APP_ID = "AppId"
        val EXTRA_UNIQUEID = "UniqueId"
        val EXTRA_STREAMING_REMOTE = "Remote"
        val EXTRA_PC_UUID = "UUID"
        val EXTRA_PC_NAME = "PcName"
    }
}