package com.limelight;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.binding.PlatformBinding;
import com.limelight.binding.audio.AndroidAudioRenderer;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.video.CrashListener;
import com.limelight.binding.video.MediaCodecDecoderRenderer;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.binding.video.PerfOverlayListener;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.ogl.TextureSurfaceRenderer;
import com.limelight.ogl.VideoTextureRenderer;
import com.limelight.preferences.GlPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.GameGestures;
import com.limelight.utils.Dialog;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Locale;

/**
 * Optional side-by-side phone-VR streaming activity.
 *
 * This keeps Moonlight's current stream negotiation and MediaCodec decoder, but sends decoder output
 * into an OpenGL external texture. The SBS renderer then duplicates the mono stream into left/right
 * eye views and applies the legacy SBS distortion shader from the old moonlight-SBS fork.
 */
public class GameVr extends Activity implements TextureView.SurfaceTextureListener,
        TextureSurfaceRenderer.OnGlReadyListener, NvConnectionListener, PerfOverlayListener,
        View.OnGenericMotionListener, View.OnKeyListener, GameGestures {

    public static final String EXTRA_HOST = Game.EXTRA_HOST;
    public static final String EXTRA_PORT = Game.EXTRA_PORT;
    public static final String EXTRA_HTTPS_PORT = Game.EXTRA_HTTPS_PORT;
    public static final String EXTRA_APP_NAME = Game.EXTRA_APP_NAME;
    public static final String EXTRA_APP_ID = Game.EXTRA_APP_ID;
    public static final String EXTRA_UNIQUEID = Game.EXTRA_UNIQUEID;
    public static final String EXTRA_PC_UUID = Game.EXTRA_PC_UUID;
    public static final String EXTRA_PC_NAME = Game.EXTRA_PC_NAME;
    public static final String EXTRA_APP_HDR = Game.EXTRA_APP_HDR;
    public static final String EXTRA_SERVER_CERT = Game.EXTRA_SERVER_CERT;

    public static final String PREF_ENABLE_SBS_VR = "checkbox_enable_sbs_vr";
    public static final String PREF_SBS_ZOOM = "seekbar_sbs_vr_zoom";
    public static final String PREF_SBS_DISTORTION = "seekbar_sbs_vr_distortion";
    public static final String PREF_SBS_WRAP = "checkbox_sbs_vr_wrap";
    public static final String PREF_SBS_SINGLE_VIEW = "checkbox_sbs_vr_single_view";

    private PreferenceConfiguration prefConfig;
    private SharedPreferences tombstonePrefs;
    private NvConnection conn;
    private MediaCodecDecoderRenderer decoderRenderer;
    private ControllerHandler controllerHandler;
    private SpinnerDialog spinner;
    private TextureView textureView;
    private TextView performanceOverlayView;
    private VideoTextureRenderer renderer;
    private WifiManager.WifiLock highPerfWifiLock;
    private WifiManager.WifiLock lowLatencyWifiLock;

    private boolean displayedFailureDialog;
    private boolean connecting;
    private boolean connected;
    private boolean attemptedConnection;
    private boolean reportedCrash;
    private String appName;
    private String pcName;
    private NvApp app;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UiHelper.setLocale(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        setContentView(R.layout.activity_gamevr);

        spinner = SpinnerDialog.displayDialog(this,
                getResources().getString(R.string.conn_establishing_title),
                getResources().getString(R.string.conn_establishing_msg), true);

        prefConfig = PreferenceConfiguration.readPreferences(this);
        tombstonePrefs = getSharedPreferences("DecoderTombstone", 0);

        textureView = findViewById(R.id.sbsSurface);
        textureView.setSurfaceTextureListener(this);
        textureView.setOnGenericMotionListener(this);
        textureView.setOnKeyListener(this);
        textureView.setFocusable(true);
        textureView.setFocusableInTouchMode(true);
        textureView.requestFocus();
        textureView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (renderer != null) {
                    renderer.setZoomedIn(!renderer.isZoomedIn());
                }
            }
        });

        performanceOverlayView = findViewById(R.id.performanceOverlay);
        if (prefConfig.enablePerfOverlay) {
            performanceOverlayView.setVisibility(View.VISIBLE);
        }

        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr.isActiveNetworkMetered()) {
            displayTransientMessage(getResources().getString(R.string.conn_metered));
        }

        WifiManager wifiMgr = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        try {
            highPerfWifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Moonlight SBS High Perf Lock");
            highPerfWifiLock.setReferenceCounted(false);
            highPerfWifiLock.acquire();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                lowLatencyWifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "Moonlight SBS Low Latency Lock");
                lowLatencyWifiLock.setReferenceCounted(false);
                lowLatencyWifiLock.acquire();
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        appName = getIntent().getStringExtra(EXTRA_APP_NAME);
        pcName = getIntent().getStringExtra(EXTRA_PC_NAME);
        String host = getIntent().getStringExtra(EXTRA_HOST);
        int port = getIntent().getIntExtra(EXTRA_PORT, NvHTTP.DEFAULT_HTTP_PORT);
        int httpsPort = getIntent().getIntExtra(EXTRA_HTTPS_PORT, 0);
        int appId = getIntent().getIntExtra(EXTRA_APP_ID, StreamConfiguration.INVALID_APP_ID);
        String uniqueId = getIntent().getStringExtra(EXTRA_UNIQUEID);
        boolean appSupportsHdr = getIntent().getBooleanExtra(EXTRA_APP_HDR, false);
        byte[] derCertData = getIntent().getByteArrayExtra(EXTRA_SERVER_CERT);

        app = new NvApp(appName != null ? appName : "app", appId, appSupportsHdr);

        X509Certificate serverCert = null;
        try {
            if (derCertData != null) {
                serverCert = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(derCertData));
            }
        } catch (CertificateException e) {
            e.printStackTrace();
        }

        if (appId == StreamConfiguration.INVALID_APP_ID) {
            finish();
            return;
        }

        GlPreferences glPrefs = GlPreferences.readPreferences(this);
        MediaCodecHelper.initialize(this, glPrefs.glRenderer);

        // The SBS shader path is SDR-only for now. Avoid negotiating HDR until EGL colorspace/HDR
        // output handling is implemented and validated for the extra GL pass.
        boolean willStreamHdr = false;
        if (prefConfig.enableHdr) {
            Toast.makeText(this, "SBS VR mode currently uses SDR. HDR is disabled for this stream.", Toast.LENGTH_LONG).show();
        }

        decoderRenderer = new MediaCodecDecoderRenderer(
                this,
                prefConfig,
                new CrashListener() {
                    @Override
                    public void notifyCrash(Exception e) {
                        tombstonePrefs.edit().putInt("CrashCount", tombstonePrefs.getInt("CrashCount", 0) + 1).commit();
                        reportedCrash = true;
                    }
                },
                tombstonePrefs.getInt("CrashCount", 0),
                connMgr.isActiveNetworkMetered(),
                willStreamHdr,
                glPrefs.glRenderer,
                this);

        if (!decoderRenderer.isAvcSupported()) {
            if (spinner != null) {
                spinner.dismiss();
                spinner = null;
            }
            Dialog.displayDialog(this, getResources().getString(R.string.conn_error_title),
                    "This device or ROM doesn't support hardware accelerated H.264 playback.", true);
            return;
        }

        int supportedVideoFormats = MoonBridge.VIDEO_FORMAT_H264;
        if (decoderRenderer.isHevcSupported()) {
            supportedVideoFormats |= MoonBridge.VIDEO_FORMAT_H265;
        }
        if (decoderRenderer.isAv1Supported()) {
            supportedVideoFormats |= MoonBridge.VIDEO_FORMAT_AV1_MAIN8;
        }

        float displayRefreshRate = getWindowManager().getDefaultDisplay().getRefreshRate();
        int gamepadMask = ControllerHandler.getAttachedControllerMask(this);
        if (!prefConfig.multiController) {
            gamepadMask = 1;
        }
        if (prefConfig.onscreenController) {
            gamepadMask |= 1;
        }

        StreamConfiguration config = new StreamConfiguration.Builder()
                .setResolution(prefConfig.width, prefConfig.height)
                .setLaunchRefreshRate(prefConfig.fps)
                .setRefreshRate(prefConfig.fps)
                .setApp(app)
                .setBitrate(prefConfig.bitrate)
                .setEnableSops(prefConfig.enableSops)
                .enableLocalAudioPlayback(prefConfig.playHostAudio)
                .setMaxPacketSize(1392)
                .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_AUTO)
                .setSupportedVideoFormats(supportedVideoFormats)
                .setAttachedGamepadMask(gamepadMask)
                .setClientRefreshRateX100((int) (displayRefreshRate * 100))
                .setAudioConfiguration(prefConfig.audioConfiguration)
                .setColorSpace(decoderRenderer.getPreferredColorSpace())
                .setColorRange(decoderRenderer.getPreferredColorRange())
                .setPersistGamepadsAfterDisconnect(!prefConfig.multiController)
                .build();

        conn = new NvConnection(getApplicationContext(),
                new ComputerDetails.AddressTuple(host, port),
                httpsPort, uniqueId, config,
                PlatformBinding.getCryptoProvider(this), serverCert);

        controllerHandler = new ControllerHandler(this, conn, this, prefConfig);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (!attemptedConnection) {
            attemptedConnection = true;

            renderer = new VideoTextureRenderer(this, surface, width, height, this);
            renderer.setVideoSize(prefConfig.width, prefConfig.height);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            renderer.setZoomFactor(prefs.getInt(PREF_SBS_ZOOM, 50));
            renderer.setDistortionFactor(prefs.getInt(PREF_SBS_DISTORTION, 81));
            renderer.setWrapEnabled(prefs.getBoolean(PREF_SBS_WRAP, true));
            renderer.setSingleView(prefs.getBoolean(PREF_SBS_SINGLE_VIEW, false));
        }
    }

    @Override
    public void onGlReady() {
        Surface decoderSurface = new Surface(renderer.getVideoTexture());
        decoderRenderer.setRenderTarget(new SurfaceBackedHolder(decoderSurface));
        connecting = true;
        conn.start(new AndroidAudioRenderer(this, prefConfig.enableAudioFx), decoderRenderer, this);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // The renderer viewport will be recalculated on the next stream frame.
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (renderer != null) {
            renderer.onPause();
            renderer = null;
        }
        if (connected || connecting) {
            decoderRenderer.prepareForStop();
            stopConnection();
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // No-op.
    }

    @Override
    protected void onStop() {
        super.onStop();

        SpinnerDialog.closeDialogs(this);
        Dialog.closeDialogs();

        if (controllerHandler != null) {
            controllerHandler.destroy();
        }
        if (renderer != null) {
            renderer.onPause();
            renderer = null;
        }
        if (conn != null) {
            displayedFailureDialog = true;
            stopConnection();
        }
        if (lowLatencyWifiLock != null) {
            lowLatencyWifiLock.release();
        }
        if (highPerfWifiLock != null) {
            highPerfWifiLock.release();
        }
    }

    private void stopConnection() {
        if (connecting || connected) {
            connecting = false;
            connected = false;
            conn.stop();
        }
    }

    @SuppressLint("InlinedApi")
    private final Runnable hideSystemUi = new Runnable() {
        @Override
        public void run() {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    };

    private void hideSystemUi(int delay) {
        Handler h = getWindow().getDecorView().getHandler();
        if (h != null) {
            h.removeCallbacks(hideSystemUi);
            h.postDelayed(hideSystemUi, delay);
        }
    }

    @Override
    public void stageStarting(final String stage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (spinner != null) {
                    spinner.setMessage(getResources().getString(R.string.conn_starting) + " " + stage);
                }
            }
        });
    }

    @Override
    public void stageComplete(String stage) {
        // No-op.
    }

    @Override
    public void stageFailed(final String stage, int portFlags, final int errorCode) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (spinner != null) {
                    spinner.dismiss();
                    spinner = null;
                }
                if (!displayedFailureDialog) {
                    displayedFailureDialog = true;
                    stopConnection();
                    Dialog.displayDialog(GameVr.this, getResources().getString(R.string.conn_error_title),
                            getResources().getString(R.string.conn_error_msg) + " " + stage +
                                    String.format((Locale) null, " (Error code: %d)", errorCode), true);
                }
            }
        });
    }

    @Override
    public void connectionStarted() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (spinner != null) {
                    spinner.dismiss();
                    spinner = null;
                }
                connecting = false;
                connected = true;
                UiHelper.notifyStreamConnected(GameVr.this);
                hideSystemUi(1000);
            }
        });
    }

    @Override
    public void connectionTerminated(final int errorCode) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!displayedFailureDialog) {
                    displayedFailureDialog = true;
                    stopConnection();
                    Dialog.displayDialog(GameVr.this, getResources().getString(R.string.conn_terminated_title),
                            getResources().getString(R.string.conn_terminated_msg) +
                                    String.format((Locale) null, " (Error code: %d)", errorCode), true);
                }
            }
        });
    }

    @Override
    public void connectionStatusUpdate(int connectionStatus) {
        // No-op. The normal Game activity handles notification overlays; keep SBS v1 simple.
    }

    @Override
    public void displayMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(GameVr.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void displayTransientMessage(final String message) {
        if (!prefConfig.disableWarnings) {
            displayMessage(message);
        }
    }

    @Override
    public void rumble(short controllerNumber, short lowFreqMotor, short highFreqMotor) {
        if (controllerHandler != null) {
            controllerHandler.handleRumble(controllerNumber, lowFreqMotor, highFreqMotor);
        }
    }

    @Override
    public void rumbleTriggers(short controllerNumber, short leftTrigger, short rightTrigger) {
        if (controllerHandler != null) {
            controllerHandler.handleRumbleTriggers(controllerNumber, leftTrigger, rightTrigger);
        }
    }

    @Override
    public void setHdrMode(boolean enabled, byte[] hdrMetadata) {
        // SBS mode intentionally runs SDR for now.
    }

    @Override
    public void setMotionEventState(short controllerNumber, byte motionType, short reportRateHz) {
        if (controllerHandler != null) {
            controllerHandler.handleSetMotionEventState(controllerNumber, motionType, reportRateHz);
        }
    }

    @Override
    public void setControllerLED(short controllerNumber, byte r, byte g, byte b) {
        if (controllerHandler != null) {
            controllerHandler.handleSetControllerLED(controllerNumber, r, g, b);
        }
    }

    @Override
    public void onPerfUpdate(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (performanceOverlayView != null) {
                    performanceOverlayView.setText(text);
                }
            }
        });
    }

    @Override
    public boolean onGenericMotion(View view, MotionEvent event) {
        return controllerHandler != null && controllerHandler.handleMotionEvent(event);
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent event) {
        if (controllerHandler != null && ControllerHandler.isGameControllerDevice(event.getDevice())) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                return controllerHandler.handleButtonDown(event);
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                return controllerHandler.handleButtonUp(event);
            }
        }
        return false;
    }

    @Override
    public void toggleKeyboard() {
        // The SBS v1 activity focuses on controller input. Keyboard overlay can be added later.
    }

    private static class SurfaceBackedHolder implements SurfaceHolder {
        private final Surface surface;
        private final Rect frame = new Rect();

        SurfaceBackedHolder(Surface surface) {
            this.surface = surface;
        }

        @Override
        public void addCallback(Callback callback) { }

        @Override
        public void removeCallback(Callback callback) { }

        @Override
        public boolean isCreating() {
            return false;
        }

        @Override
        public void setType(int type) { }

        @Override
        public void setFixedSize(int width, int height) {
            frame.set(0, 0, width, height);
        }

        @Override
        public void setSizeFromLayout() { }

        @Override
        public void setFormat(int format) { }

        @Override
        public void setKeepScreenOn(boolean screenOn) { }

        @Override
        public Canvas lockCanvas() {
            return null;
        }

        @Override
        public Canvas lockCanvas(Rect dirty) {
            return null;
        }

        @Override
        public void unlockCanvasAndPost(Canvas canvas) { }

        @Override
        public Rect getSurfaceFrame() {
            return frame;
        }

        @Override
        public Surface getSurface() {
            return surface;
        }
    }
}
