package com.limelight;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.limelight.binding.PlatformBinding;
import com.limelight.binding.video.EnhancedDecoderRenderer;
import com.limelight.binding.video.MediaCodecDecoderRenderer;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.http.NvApp;
import com.limelight.ogl.TextureSurfaceRenderer;
import com.limelight.ogl.VideoTextureRenderer;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.StreamView;
import com.limelight.utils.Dialog;
import com.limelight.utils.SpinnerDialog;

import java.util.Locale;

public class GameVr extends Activity implements TextureView.SurfaceTextureListener,
    NvConnectionListener, OnSystemUiVisibilityChangeListener, TextureSurfaceRenderer.OnGlReadyListener
{
    private PreferenceConfiguration prefConfig;
    private NvConnection conn;
    private SpinnerDialog spinner;
    private boolean displayedFailureDialog = false;
    private boolean connecting = false;
    private boolean connected = false;
    private TextureView textureView;
    private EnhancedDecoderRenderer decoderRenderer;
    private VideoTextureRenderer renderer;
    private WifiManager.WifiLock wifiLock;
    private int drFlags = 0;

    private int surfaceWidth;
    private int surfaceHeight;

    public static final String EXTRA_HOST = "Host";
    public static final String EXTRA_APP_NAME = "AppName";
    public static final String EXTRA_APP_ID = "AppId";
    public static final String EXTRA_UNIQUEID = "UniqueId";
    public static final String EXTRA_STREAMING_REMOTE = "Remote";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String locale = PreferenceConfiguration.readPreferences(this).language;
        if (!locale.equals(PreferenceConfiguration.DEFAULT_LANGUAGE)) {
            Configuration config = new Configuration(getResources().getConfiguration());
            config.locale = new Locale(locale);
            getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(   WindowManager.LayoutParams.FLAG_FULLSCREEN |
                                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);

        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(this);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        setContentView(R.layout.activity_gamevr);

        spinner = SpinnerDialog.displayDialog(this, getResources().getString(R.string.conn_establishing_title),
                getResources().getString(R.string.conn_establishing_msg), true);

        prefConfig = PreferenceConfiguration.readPreferences(this);

        if (prefConfig.stretchVideo) {
            drFlags |= VideoDecoderRenderer.FLAG_FILL_SCREEN;
        }

        checkDataConnection();
        WifiManager wifiMgr = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Limelight");
        wifiLock.setReferenceCounted(false);
        wifiLock.acquire();

        String host = GameVr.this.getIntent().getStringExtra(EXTRA_HOST);
        String appName = GameVr.this.getIntent().getStringExtra(EXTRA_APP_NAME);
        int appId = GameVr.this.getIntent().getIntExtra(EXTRA_APP_ID, StreamConfiguration.INVALID_APP_ID);
        String uniqueId = GameVr.this.getIntent().getStringExtra(EXTRA_UNIQUEID);
        boolean remote = GameVr.this.getIntent().getBooleanExtra(EXTRA_STREAMING_REMOTE, false);

        if (appId == StreamConfiguration.INVALID_APP_ID) {
            finish();
            return;
        }

        MediaCodecHelper.initializeWithContext(this);
        decoderRenderer = new MediaCodecDecoderRenderer(prefConfig.videoFormat);

        if (prefConfig.videoFormat == PreferenceConfiguration.FORCE_H265_ON && !decoderRenderer.isHevcSupported()) {
            Toast.makeText(this, "No H.265 decoder found.\nFalling back to H.264.", Toast.LENGTH_LONG).show();
        }

        if (!decoderRenderer.isAvcSupported()) {
            if (spinner != null) {
                spinner.dismiss();
                spinner = null;
            }

            Dialog.displayDialog(this, getResources().getString(R.string.conn_error_title),
                    "This device or ROM doesn't support hardware accelerated H.264 playback.", true);
            return;
        }

        StreamConfiguration config = new StreamConfiguration.Builder()
                .setResolution(prefConfig.width, prefConfig.height)
                .setRefreshRate(prefConfig.fps)
                .setApp(new NvApp(appName, appId))
                .setBitrate(prefConfig.bitrate * 1000)
                .setEnableSops(prefConfig.enableSops)
                .enableAdaptiveResolution((decoderRenderer.getCapabilities() &
                        VideoDecoderRenderer.CAPABILITY_ADAPTIVE_RESOLUTION) != 0)
                .enableLocalAudioPlayback(prefConfig.playHostAudio)
                .setMaxPacketSize(remote ? 1024 : 1292)
                .setRemote(remote)
                .setHevcSupported(decoderRenderer.isHevcSupported())
                .setAudioConfiguration(prefConfig.enable51Surround ?
                        StreamConfiguration.AUDIO_CONFIGURATION_5_1 :
                        StreamConfiguration.AUDIO_CONFIGURATION_STEREO)
                .build();

        // Initialize the connection
        conn = new NvConnection(host, uniqueId, GameVr.this, config, PlatformBinding.getCryptoProvider(this));
        prepareDisplayForRendering();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            getWindow().setSustainedPerformanceMode(true);
        }

        // Listen for events on the game surface
        textureView = (TextureView) findViewById(R.id.surface);
        textureView.setSurfaceTextureListener(this);
    }

    private void prepareDisplayForRendering() {
        Display display = getWindowManager().getDefaultDisplay();
        WindowManager.LayoutParams windowLayoutParams = getWindow().getAttributes();

        // On M, we can explicitly set the optimal display mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode bestMode = display.getMode();
            for (Display.Mode candidate : display.getSupportedModes()) {
                boolean refreshRateOk = candidate.getRefreshRate() >= bestMode.getRefreshRate() &&
                        candidate.getRefreshRate() < 63;
                boolean resolutionOk = candidate.getPhysicalWidth() >= bestMode.getPhysicalWidth() &&
                        candidate.getPhysicalHeight() >= bestMode.getPhysicalHeight() &&
                        candidate.getPhysicalWidth() <= 4096;

                // On non-4K streams, we force the resolution to never change
                if (prefConfig.width < 3840) {
                    if (display.getMode().getPhysicalWidth() != candidate.getPhysicalWidth() ||
                            display.getMode().getPhysicalHeight() != candidate.getPhysicalHeight()) {
                        continue;
                    }
                }

                // Make sure the refresh rate doesn't regress
                if (!refreshRateOk) {
                    continue;
                }

                // Make sure the resolution doesn't regress
                if (!resolutionOk) {
                    continue;
                }

                bestMode = candidate;
            }
            LimeLog.info("Selected display mode: "+bestMode.getPhysicalWidth()+"x"+
                    bestMode.getPhysicalHeight()+"x"+bestMode.getRefreshRate());
            windowLayoutParams.preferredDisplayModeId = bestMode.getModeId();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            float bestRefreshRate = display.getRefreshRate();
            for (float candidate : display.getSupportedRefreshRates()) {
                if (candidate > bestRefreshRate && candidate < 63) {
                    LimeLog.info("Examining refresh rate: "+candidate);
                    bestRefreshRate = candidate;
                }
            }
            LimeLog.info("Selected refresh rate: "+bestRefreshRate);
            windowLayoutParams.preferredRefreshRate = bestRefreshRate;
        }

        // Apply the display mode change
        getWindow().setAttributes(windowLayoutParams);
    }

    private void checkDataConnection() {
        ConnectivityManager mgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (mgr.isActiveNetworkMetered()) {
            displayTransientMessage(getResources().getString(R.string.conn_metered));
        }
    }

    @SuppressLint("InlinedApi")
    private final Runnable hideSystemUi = new Runnable() {
            @Override
            public void run() {
                GameVr.this.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
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
    protected void onStop() {
        super.onStop();

        SpinnerDialog.closeDialogs(this);
        Dialog.closeDialogs();

        wifiLock.release();

        if (conn != null) {
            displayedFailureDialog = true;
            stopConnection();

            int averageEndToEndLat = decoderRenderer.getAverageEndToEndLatency();
            int averageDecoderLat = decoderRenderer.getAverageDecoderLatency();
            String message = "";
            if (averageEndToEndLat > 0) {
                message += getResources().getString(R.string.conn_client_latency)+" "+averageEndToEndLat+" ms ";
            }

            if (averageDecoderLat > 0) {
                message += getResources().getString(R.string.conn_hardware_latency)+" "+averageDecoderLat+" ms";
            }

            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }

        finish();
    }

    @Override
    public void stageStarting(Stage stage) {
        if (spinner != null) {
            spinner.setMessage(getResources().getString(R.string.conn_starting)+" "+stage.getName());
        }
    }

    @Override
    public void stageComplete(Stage stage) {}

    private void stopConnection() {
        if (connecting || connected) {
            connecting = connected = false;
            conn.stop();
        }
    }

    @Override
    public void stageFailed(Stage stage) {
        if (spinner != null) {
            spinner.dismiss();
            spinner = null;
        }

        if (!displayedFailureDialog) {
            displayedFailureDialog = true;
            stopConnection();
            Dialog.displayDialog(this, getResources().getString(R.string.conn_error_title),
                    getResources().getString(R.string.conn_error_msg)+" "+stage.getName(), true);
        }
    }

    @Override
    public void connectionTerminated(Exception e) {
        if (!displayedFailureDialog) {
            displayedFailureDialog = true;
            e.printStackTrace();

            stopConnection();
            Dialog.displayDialog(this, getResources().getString(R.string.conn_terminated_title),
                    getResources().getString(R.string.conn_terminated_msg), true);
        }
    }

    @Override
    public void connectionStarted() {
        if (spinner != null) {
            spinner.dismiss();
            spinner = null;
        }

        connecting = false;
        connected = true;

        hideSystemUi(1000);
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
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(GameVr.this, message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height)
    {
        surfaceWidth = width;
        surfaceHeight = height;

        if (!connected && !connecting) {
            connecting = true;

            renderer = new VideoTextureRenderer(this, textureView.getSurfaceTexture(), surfaceWidth, surfaceHeight, this);
            renderer.setVideoSize(prefConfig.width, prefConfig.height);
        }
    }

    @Override
    public void onGlReady() {
        conn.start(PlatformBinding.getDeviceName(), new Surface(renderer.getVideoTexture()), drFlags,
                PlatformBinding.getAudioRenderer(), decoderRenderer);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface)
    {
        if (connected) {
            decoderRenderer.stop();
            stopConnection();
        }

        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        if (!connected) {
            return;
        }

        // This flag is set for all devices
        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0 || ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) &&
                 (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) || (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT &&
                 (visibility & View.SYSTEM_UI_FLAG_LOW_PROFILE) == 0)) {
            hideSystemUi(2000);
        }
    }
}
