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
import android.content.Context
import android.net.wifi.WifiManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.View
import com.limelight.vr.VideoSceneRenderer
import com.limelight.vr.MediaCodecDecoderRendererVR

class VirtualRealityGame : Activity(), NvConnectionListener {

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
        gvrLayout!!.setPresentationView(surfaceView)
        gvrLayout!!.keepScreenOn = true
        renderer = VideoSceneRenderer(this, gvrLayout!!.gvrApi)

        //Initialize the ExternalSurfaceListener to receive video Surface callbacks.
        hasFirstFrame = false
        val videoSurfaceListener = object : GvrLayout.ExternalSurfaceListener {
            override fun onSurfaceAvailable(surface: Surface) {
                //Set the surface for Moonlight's stream to output video frames to. Video
                //playback is started when the Surface is set.
                // Initialize the connection
                conn = NvConnection(host, uniqueId, this@VirtualRealityGame, streamConfig, PlatformBinding.getCryptoProvider(this@VirtualRealityGame))
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
        wifiLock!!.release()
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